package com.mapcontrol.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.desaysv.ivi.extra.project.carinfo.CarSettingID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.vehicle.VehicleMetricsRepository
import java.util.concurrent.Executors

/**
 * "Spor modunda ambiyans rengi" kararlarının SAF kuralları (JVM'de sınanır). Klasik iç ambiyans yolu (OEM ayarlar,
 * Tiggo 8 yazılımı): CarSetting 7 aç/kapa (okuma 1 = açık), 11 renk SIRASI (RGB değil, okuma 1..64 geçerli), 55 sürüş
 * moduna bağlı (1 = açık), 8 müzik ritmi (1 = açık). Spor başlarken geçerli renk hatırlanır ve OEM tablosundaki
 * kırmızı-turuncu ([SPORT_COLOR]) yazılır; spor bitince hatırlanan renk geri yazılır.
 */
internal object SportAmbientLogic {

    /** OEM renk tablosunda sıra 45 = (255, 66, 0), CSS orangered'e en yakın kırmızı-turuncu. */
    const val SPORT_COLOR = 45

    /** OEM okuma yolunun kabul ettiği renk sırası aralığı. */
    const val COLOR_MIN = 1
    const val COLOR_MAX = 64

    fun validColor(v: Int?): Boolean = v != null && v in COLOR_MIN..COLOR_MAX

    /** Kalıcı durum: [saved] spor öncesi renk, [applied] spor rengini biz yazdık (geri yazma bekliyor). */
    data class State(val saved: Int? = null, val applied: Boolean = false) {
        companion object {
            @JvmField val IDLE = State()
        }
    }

    /** Karar anında okunan CarSetting değerleri; okunamayan null. */
    data class Reading(val ambientOn: Int? = null, val color: Int? = null, val relateDrive: Int? = null, val rhythm: Int? = null)

    /**
     * [next] yeni durum, [write] yazılacak renk (null: hiçbir şey yazılmaz). [settled] false: okuma yetmedi, durum
     * değişmedi, daha sonra yeniden denenmeli (yalnızca geri yazmada; ör. servis henüz bağlanmadı).
     */
    data class Decision(val next: State, val write: Int? = null, val settled: Boolean = true)

    /**
     * [wantSport]: spor kararlı ve seçenek açık. Spor istenip henüz uygulanmadıysa: ambiyans açık (7 = 1), sürüş moduna
     * bağlı değil (55 != 1, o zaman rengi araç seçer), müzik ritmi kapalı (8 != 1) ve renk geçerliyse hatırlanır ve
     * [SPORT_COLOR] yazılır; renk zaten [SPORT_COLOR] ise geri yazacak bir şey yoktur, hiçbir şey yapılmaz. Spor bitmiş
     * (ya da seçenek kapanmış) ve uygulanmışsa: renk hâlâ [SPORT_COLOR] ise hatırlanan geri yazılır; kullanıcı sporda
     * başka renk seçtiyse ona dokunulmaz; renk okunamıyorsa bekler ([Decision.settled] false).
     */
    fun decide(state: State, wantSport: Boolean, r: Reading): Decision {
        if (wantSport == state.applied) return Decision(state)
        if (wantSport) {
            // Okunamadı (açılış/servis yeniden bağlanma ilk saniyeleri): vazgeçilmez, yeniden denenir. Bilinçli ret
            // (ambiyans kapalı, sürüş moduna bağlı, ritim) bu spor açıklığı için kesindir.
            if (r.ambientOn == null || r.color == null) return Decision(state, settled = false)
            if (r.ambientOn != 1 || r.relateDrive == 1 || r.rhythm == 1) return Decision(state)
            val color = r.color
            if (!validColor(color) || color == SPORT_COLOR) return Decision(state)
            return Decision(State(saved = color, applied = true), write = SPORT_COLOR)
        }
        val saved = state.saved
        if (!validColor(saved)) return Decision(State.IDLE)
        val now = r.color ?: return Decision(state, settled = false)
        if (now != SPORT_COLOR) return Decision(State.IDLE)
        return Decision(State.IDLE, write = saved)
    }

    /** Tercihte saklama: hatırlanan renk yoksa -1. */
    fun encodeSaved(saved: Int?): Int = if (validColor(saved)) saved!! else -1

    fun decodeSaved(raw: Int): Int? = raw.takeIf { validColor(it) }
}

/**
 * Spor ambiyansının araç tarafı. Seçenek açıkken ya da geri yazma beklerken sürüş modu [VehicleMetricsRepository]
 * olaylarından izlenir (yeni yoklama yok); mod [STABLE_MS] kararlı kalınca [SportAmbientLogic.decide] arka planda
 * uygulanır. Yazma diğer widget yazmalarıyla aynı kapıdan geçer ([enabled]): [AMBIENT_WRITE_VERIFIED] false iken gerçek
 * sürümde araca HİÇBİR ŞEY gitmez; test sürümünde yalnızca "widget'lardan yaz" anahtarı açıkken beyaz listeden
 * ([WidgetWritePolicy] "SportAmbient") gider. Demo modunda hiçbir şey okunmaz ya da yazılmaz. Kapı kapalıyken durum
 * değişmez: bekleyen geri yazma kapı açılınca yapılır.
 */
internal object SportAmbient {

    /** Ambiyans rengi yazması araçta DOĞRULANMADI (CS 11 kodlaması Tiggo 8 yazılımından). Tur 6'dan sonra true yapılır. */
    const val AMBIENT_WRITE_VERIFIED = false

    const val STABLE_MS = 2_000L
    /** Renk okunamadığı için ertelenen geri yazmanın yeniden deneme aralığı. */
    const val RETRY_MS = 5_000L

    private const val TAG = "SportAmbient"
    private const val PREFS = "launcher_settings"
    private const val KEY_ENABLED = "sport_ambient"
    private const val KEY_SAVED = "sport_ambient_saved"
    private const val KEY_APPLIED = "sport_ambient_applied"

    val enabled: Boolean
        get() = ComfortWriter.canWrite(ComfortWriter.COMFORT_WRITE_VERIFIED,
            WidgetWritePolicy.gate(AMBIENT_WRITE_VERIFIED, LauncherEdition.WRITES_ON), LauncherEdition.READ_ONLY)

    fun isOn(context: Context): Boolean =
        prefs(context).getBoolean(Profiles.key(context, KEY_ENABLED), false)

    fun setOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(Profiles.key(context, KEY_ENABLED), on).apply()
        sync(context)
    }

    // Tembel: JVM testlerinde nesneye dokunmak Android Looper'ı istemesin.
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val io by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "sport-ambient").apply { isDaemon = true } } }
    private var app: Context? = null
    private var listening = false
    private val gate = SportGate(STABLE_MS)
    private var lastMode: Int? = null
    /** Bu istek için karar verildi (yeniden denenmez); istek değişince sıfırlanır. */
    @Volatile private var doneFor: Boolean? = null
    @Volatile private var retryAt = 0L
    @Volatile private var busy = false

    private val listener = VehicleMetricsRepository.Listener { snap ->
        lastMode = if (snap.connected && snap.hasDriveMode()) snap.driveMode else null
        tick()
    }
    private val recheck = Runnable { tick() }

    /**
     * Etkinlik açılışında ve seçenek değişince: seçenek açıksa ya da geri yazma bekliyorsa izlemeye başlar (uygulama
     * açılışında bekleyen geri yazma buradan yapılır), değilse bırakır.
     */
    fun sync(context: Context) {
        val c = context.applicationContext
        app = c
        // Kapı kapalıysa (gerçek sürümde doğrulanana kadar hep) dinleyici bile kurulmaz; test sürümünde anahtar değişince
        // etkinlik yeniden kurulur ve buradan yeniden bakılır.
        val need = !LauncherDemo.enabled && enabled && (isOn(c) || state(c).applied)
        val repo = VehicleMetricsRepository.getInstance(c)
        if (need && !listening) {
            listening = true
            gate.reset(false)
            lastMode = null
            doneFor = null
            repo.addListener(listener)
        } else if (!need && listening) {
            listening = false
            main.removeCallbacks(recheck)
            repo.removeListener(listener)
        }
        if (listening) tick()
    }

    /** Ana thread: kararlı sporu süzer, gerekiyorsa arka planda karar verir. */
    private fun tick() {
        val c = app ?: return
        if (LauncherDemo.enabled) return
        val mode = lastMode
        gate.update(SystemClock.elapsedRealtime(), mode, true)
        // Mod okunamıyorsa ya da süzgeç hâlâ sayıyorsa karar yok; olay gelmese de süre dolunca yeniden bakılır.
        val settled = mode != null && (mode == DriveAccent.SPORT) == gate.active
        if (!settled) {
            main.removeCallbacks(recheck)
            if (mode != null) main.postDelayed(recheck, STABLE_MS + 100L)
            return
        }
        val want = gate.active && isOn(c)
        if (busy || !enabled) return
        if (doneFor == want) return
        if (want == state(c).applied) { doneFor = want; return }
        if (SystemClock.elapsedRealtime() < retryAt) return
        busy = true
        io.execute {
            try { reconcile(c, want) } catch (t: Throwable) { Log.w(TAG, "karar başarısız", t) } finally { busy = false }
        }
    }

    /** Arka plan: okur, karar verir, yazar ve durumu kaydeder. */
    private fun reconcile(c: Context, want: Boolean) {
        val proxy = CarInfoProxy.getInstance()
        if (!proxy.isServiceConnnected) { retryLater(); return }
        fun cs(id: Int) = CarSignals.readInt(VDEventCarInfo.MODULE_CAR_SETTING, id)
        val reading = if (want) {
            SportAmbientLogic.Reading(
                ambientOn = cs(CarSettingID.ID_CAR_AMBIENT_LIGHT),
                color = cs(CarSettingID.ID_CAR_AMBIENT_LIGHT_COLOR),
                relateDrive = cs(CarSettingID.ID_CAR_AMBIENT_LIGHT_RELATE_DRIVE_MODE),
                rhythm = cs(CarSettingID.ID_CAR_MUSIC_RHYTHM),
            )
        } else {
            SportAmbientLogic.Reading(color = cs(CarSettingID.ID_CAR_AMBIENT_LIGHT_COLOR))
        }
        val d = SportAmbientLogic.decide(state(c), want, reading)
        if (!d.settled) { retryLater(); return }
        // Kapı yazmadan hemen önce yeniden sorulur (test anahtarı bu arada kapanmış olabilir); kapalıysa durum değişmez.
        if (d.write != null) {
            if (!enabled || LauncherDemo.enabled) return
            // Spor rengi yazılmadan ÖNCE özgün renk ve "geri yazma bekliyor" bayrağı eşzamanlı (commit) kaydedilir; iki
            // adım arasında süreç ölse bile özgün renk kaybolmaz. Geri yazmada sıra tersi güvenlidir (ölürse renk 45
            // değildir, açılışta IDLE'a düşer).
            if (d.next.applied) save(c, d.next)
            LauncherEdition.sendItemValue(proxy, VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_AMBIENT_LIGHT_COLOR, d.write)
            Log.i(TAG, "want=$want read=$reading write=${d.write}")
        }
        save(c, d.next)
        doneFor = want
        main.post { if (!isOn(c) && !d.next.applied) sync(c) }
    }

    private fun retryLater() {
        retryAt = SystemClock.elapsedRealtime() + RETRY_MS
        main.postDelayed(recheck, RETRY_MS + 100L)
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Araç durumu (sürücü profiline bağlı değil). */
    private fun state(c: Context): SportAmbientLogic.State {
        val p = prefs(c)
        return SportAmbientLogic.State(SportAmbientLogic.decodeSaved(p.getInt(KEY_SAVED, -1)), p.getBoolean(KEY_APPLIED, false))
    }

    private fun save(c: Context, s: SportAmbientLogic.State) {
        prefs(c).edit().putInt(KEY_SAVED, SportAmbientLogic.encodeSaved(s.saved)).putBoolean(KEY_APPLIED, s.applied).commit()
    }
}
