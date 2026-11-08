package com.mapcontrol.launcher

import android.content.Context
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Her profilin iki ana ekran düzeni vardır: duran araç için [PARK], giderken için [DRIVE]. */
enum class LayoutMode { PARK, DRIVE }

/**
 * Park/Sürüş düzeni geçiş kararı (saf, JVM'de sınanır).
 *
 * Sürüşe geçiş: hız > 0 kesintisiz [enterMs] sürerse. Parka dönüş (yalnızca Sürüş'teyken), hangisi önce olursa:
 *  - vites P (ya da el freni çekili) kesintisiz [parkSignalMs] sürerse; bu sinyaller bilinmiyorsa hiç gelmez,
 *  - hız 0 kesintisiz [stillFallbackMs] sürerse (kırmızı ışık gibi kısa beklemeler Sürüş'te kalır).
 * Bilinmeyen (null) hız bekleme sayaçlarını sıfırlar. [canSwitch] false iken karar ertelenir, sayaçlar işlemeye devam eder.
 */
class DriveLayoutPolicy(
    private val enterMs: Long = ENTER_MS,
    private val parkSignalMs: Long = PARK_SIGNAL_MS,
    private val stillFallbackMs: Long = STILL_FALLBACK_MS,
) {
    private var applied = LayoutMode.PARK
    private var movingSince: Long? = null
    private var stillSince: Long? = null
    private var parkedSince: Long? = null

    val mode: LayoutMode get() = applied

    fun reset(mode: LayoutMode = LayoutMode.PARK) {
        applied = mode
        movingSince = null; stillSince = null; parkedSince = null
    }

    /**
     * Park sinyali (vites/el freni) bu turda karar için gerekiyor mu? Yalnızca Sürüş'teyken ve hız 0/bilinmiyorken:
     * Park'ta ve hareket halinde sinyal hiçbir sayacı etkilemez, okunmaz (IPC tasarrufu). Gerekmeyince
     * [DriveSignals.invalidate] çağrılmalı ki bayat değer yeniden gerektiğinde bir sayaç başlatmasın.
     */
    fun wantsParkSignal(moving: Boolean?): Boolean = applied == LayoutMode.DRIVE && moving != true

    /** Geçilecek yeni mod, yoksa null. [immediatePark] (demo): sürüşteyken beklemeden Park'a. */
    fun update(now: Long, moving: Boolean?, parked: Boolean, canSwitch: Boolean, immediatePark: Boolean = false): LayoutMode? {
        movingSince = if (moving == true) movingSince ?: now else null
        stillSince = if (moving == false) stillSince ?: now else null
        parkedSince = if (parked && moving != true) parkedSince ?: now else null
        if (!canSwitch) return null
        val to = when (applied) {
            LayoutMode.PARK -> if (movingSince?.let { now - it >= enterMs } == true) LayoutMode.DRIVE else null
            LayoutMode.DRIVE -> when {
                immediatePark -> LayoutMode.PARK
                parkedSince?.let { now - it >= parkSignalMs } == true -> LayoutMode.PARK
                stillSince?.let { now - it >= stillFallbackMs } == true -> LayoutMode.PARK
                else -> null
            }
        }
        if (to != null) reset(to)
        return to
    }

    companion object {
        const val ENTER_MS = 5_000L
        const val PARK_SIGNAL_MS = 2_000L
        const val STILL_FALLBACK_MS = 180_000L
    }
}

/**
 * Düzen geçişinde gösterilecek bilgi: yalnızca otomatik geçişte, gerçekten mod değiştiyse ve ekran kurulduktan
 * (ilk açılış değil) sonra; elle geçişte null. Gösterilecek yeni modu döndürür.
 */
fun layoutNoticeFor(from: LayoutMode, to: LayoutMode, automatic: Boolean, started: Boolean): LayoutMode? =
    if (automatic && started && from != to) to else null

/**
 * "Sürüşte ayrı düzen" seçeneği (profil başına, varsayılan kapalı: herkes tek düzenle, Park düzeniyle başlar). Açıkken
 * araç giderken ayrı Sürüş düzeni gösterilir. Daha önce elle yazılmış değer korunur; hiç yazılmadıysa varsayılan geçerlidir.
 */
object DriveLayoutPrefs {
    private const val PREFS = "launcher_drive_layout"
    private const val KEY = "auto_drive_layout"

    const val DEFAULT_ON = false

    fun key(profile: Int): String = Profiles.key(KEY, profile)
    fun get(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(Profiles.key(context, KEY), DEFAULT_ON)

    fun set(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(Profiles.key(context, KEY), on).apply()
    }
}

/**
 * Aracın "sürüşte" durumu, düzen seçeneğinden ve düzenleme modundan bağımsız: [DriveLayoutPolicy] ile aynı gecikmeler
 * (5 sn hareketle girilir; vites P/el freni 2 sn ya da 3 dk duruşla çıkılır, kırmızı ışıkta titremez). Ana ekran her
 * turda [set] eder; yolculuk kartı ([SahneTripPanel]) dinler. Yalnızca ana iş parçacığında.
 */
internal object DriveMotion {
    var driving = false
        private set
    private val listeners = LinkedHashSet<(Boolean) -> Unit>()

    fun set(on: Boolean) {
        if (on == driving) return
        driving = on
        listeners.toList().forEach { it(on) }
    }

    fun add(l: (Boolean) -> Unit) { listeners += l }
    fun remove(l: (Boolean) -> Unit) { listeners -= l }
}

/**
 * Parka dönüş sinyalleri. Değerler araçta henüz ölçülmedi: null iken sinyal okunmaz ve yalnızca
 * [DriveLayoutPolicy.STILL_FALLBACK_MS] yedeği çalışır.
 */
internal object DriveSignals {
    /** ReadOnly ID_GEARBOX_STATE (26) değeri: P=1, R=2, N=3, D=5 (araçta ölçüldü). */
    val PARK_GEAR_VALUE: Int? = 1

    /** ReadOnly ID_HANDBRAKE_STATE (75) değeri: 1 çekili, 2 bırakılıyor, 0 inik, 3 çekiliyor (araçta ölçüldü). */
    val HANDBRAKE_ON_VALUE: Int? = 1

    private val RO = VDEventCarInfo.MODULE_READONLY_INFO
    /** Arka plan (daemon) tek iş parçacığı; boşta kendiliğinden biter (hiç kapatılmayan nesne için). */
    private val io = ThreadPoolExecutor(1, 1, 20L, TimeUnit.SECONDS, LinkedBlockingQueue()) { r ->
        Thread(r, "drive-signals").apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }
    @Volatile private var cached = false
    @Volatile private var pending = false
    /** [invalidate] sayacı: uçuştaki bir okuma, arada geçersiz kılındıysa sonucunu atar. */
    @Volatile private var generation = 0

    /** Son okunan "park sinyali var" (arka planda tazelenir; ana iş parçacığını bloklamaz). Her çağrı bir okuma başlatır. */
    fun parked(): Boolean {
        if (PARK_GEAR_VALUE == null && HANDBRAKE_ON_VALUE == null) return false
        if (!pending) {
            pending = true
            val gen = generation
            io.execute {
                try {
                    val gear = PARK_GEAR_VALUE?.let { CarSignals.readInt(RO, ReadOnlyID.ID_GEARBOX_STATE) == it } ?: false
                    val brake = HANDBRAKE_ON_VALUE?.let { CarSignals.readInt(RO, ReadOnlyID.ID_HANDBRAKE_STATE) == it } ?: false
                    if (gen == generation) cached = gear || brake
                } finally { pending = false }
            }
        }
        return cached
    }

    /** Sinyal artık gerekmiyor: bayat değeri at (bkz. [DriveLayoutPolicy.wantsParkSignal]). */
    fun invalidate() {
        generation++
        cached = false
    }
}
