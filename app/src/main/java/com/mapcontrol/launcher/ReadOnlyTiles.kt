package com.mapcontrol.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextClock
import androidx.annotation.StringRes
import com.desaysv.ivi.extra.project.carinfo.CarSettingID
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.R
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Yalnızca okuyan yeni kutucuklar: mola, hız limiti, akü, telefon, saat, gövde.
 * Araca hiçbir şey yazılmaz; hepsi [ExtraSource] turundan beslenir. Geçersiz ya da bilinmeyen değer
 * "—" olarak görünür, uydurma sayı gösterilmez.
 */

/**
 * Bu dosyadaki sinyallerin ham değerden çözümü (saf işlevler, JVM'de sınanır).
 *
 * Araçta (Tiggo 7, 2026-10-29/30 kayıtları) ölçülenler: DRIVE_TIME, el freni, kaput, kapı, cam (2 = kapalı).
 * Doğrulanmayanlar her işlevin belgesinde "varsayım" diye işaretlidir.
 */
internal object ReadOnlyDecode {

    /** Kesintisiz sürüş bu süreye (dakika) ulaşınca mola uyarısı çıkar. */
    const val BREAK_AFTER_MIN = 120

    /**
     * Sürüş süresi (ReadOnly 29): dört bayt [b0, b1, dk, sn]. 2026-10-30 kaydı: [0,0,29,45] -> [0,0,29,51] -> ... -> [0,0,30,3],
     * yani üçüncü bayt dakika, dördüncü saniye (60'ta devreder); OEM günlüğü de aynı dakikayı "mDriveMin" diye yazıyor.
     * VARSAYIM (doğrulanmadı): saat ilk iki baytta (b0*256+b1); 1 saati geçen bir sürüş henüz kaydedilmedi.
     * Dakika baytı 60'ı aşarsa (saat ayrı sayılmıyorsa) da toplam doğru çıksın diye dakika 255'e kadar kabul edilir.
     * Toplam dakika döner; boyut/aralık bozuksa null.
     */
    fun driveMinutes(raw: IntArray?): Int? {
        if (raw == null || raw.size != 4) return null
        if (raw.any { it !in 0..255 }) return null
        val hours = raw[0] * 256 + raw[1]
        val minutes = raw[2]
        val seconds = raw[3]
        if (seconds > 59 || hours > 99) return null
        if (raw.all { it == 255 }) return null
        return hours * 60 + minutes
    }

    fun breakDue(minutes: Int?): Boolean = minutes != null && minutes >= BREAK_AFTER_MIN

    /**
     * Hız limiti (CarSetting 86): km/sa doğrudan. 0 = limit yok; 5..200 dışı (0xFF, 0xFFFF) geçersiz.
     * Durum sinyali (85) araçta hep 0 geldi; anlamı bilinmediği için limit onsuz da gösterilir.
     */

    /** Akü gerilimi (ReadOnly 76, BATTERY_LEVEL): ham/10 V. Araçta motor çalışırken [0,142] -> 14,2 V. 8,0-16,5 V dışı geçersiz. */
    fun batteryVolts(raw: Int?): Double? = raw?.takeIf { it in 80..165 }?.let { it / 10.0 }

    /**
     * Akü şarjı (ReadOnly 85, IBS_SOC): 1..100 %. Araçta hep [0] okundu; 0 "desteklenmiyor" sayılır (boş aküyü
     * 12 V sistemde göstermek yanıltıcı olurdu), yani null döner.
     */
    fun batterySoc(raw: Int?): Int? = raw?.takeIf { it in 1..100 }

    const val PAD_EMPTY = 0
    const val PAD_CHARGING = 1
    const val PAD_FULL = 2
    const val PAD_ERROR = 3
    const val PAD_OFF = 4

    /**
     * Kablosuz şarj durumu (CarSetting 44), OEM durum çubuğu gibi:
     * 0 boşta, 1 şarj oluyor, 2 OEM'in ayrı tuttuğu durum (şarj tamamlandı sayılır; araçta görülmedi), 3 hata.
     * Başka değer bilinmiyor (null). 0 ve 1 araçta ölçüldü.
     */
    fun padState(raw: Int?): Int? = when (raw) {
        0 -> PAD_EMPTY
        1 -> PAD_CHARGING
        2 -> PAD_FULL
        3 -> PAD_ERROR
        else -> null
    }

    /**
     * Telefon pedde şarj oluyor mu (başlık göstergesi). Girdi [padStateWithSwitch] sonucudur: yalnız şarj oluyor ya da
     * tamamlandı durumunda true; boşta, hata ve anahtar kapalı ([PAD_OFF]) göstergeyi yakmaz.
     */
    fun phoneCharging(state: Int?): Boolean = state == PAD_CHARGING || state == PAD_FULL

    /**
     * Kablosuz şarj anahtarı (CarSetting 45) okuması: 1 açık (araçta hep 1 okundu). Başka bilinen
     * değer kapalıdır ve durum yerine [PAD_OFF] gösterilir. Anahtar okunamadıysa (null) [padState] olduğu gibi kalır.
     * Not: OEM bu anahtarı 2 = aç, 1 = kapat diye YAZAR; okunan ve yazılan değer farklıdır.
     */
    fun padStateWithSwitch(state: Int?, switchRaw: Int?): Int? = when (switchRaw) {
        null, 1 -> state
        else -> PAD_OFF
    }

    /**
     * Telefon unutuldu mu: gerçek bayrak PHONE_FORGOT_ON_WIRLESS_BOARD (46) = 1 ("pedde unutuldu").
     * PHONE_FORGOT (47) araçta pedde telefon yokken de hep 1 okundu; bu yüzden hatırlatma AÇIK ayarı sayılır:
     * 47 = 0 ise hatırlatma kapalıdır ve uyarı çıkmaz. Bayrak okunamadıysa null.
     */
    fun phoneForgotten(onBoard: Int?, reminderOn: Int?): Boolean? = when (onBoard) {
        1 -> reminderOn != 0
        0 -> false
        else -> null
    }

    /**
     * Cam (CarSetting 100..103): 2 = kapalı (araçta ölçüldü); 100 için 2 -> 6 -> 4 -> 2 hareket gözlendi.
     * Kapalı olmayan her geçerli değer (1..15) açık / hareket sayılır. 0 ve aralık dışı: bilinmiyor (null).
     */
    fun windowOpen(raw: Int?): Boolean? = when (raw) {
        2 -> false
        in 1..15 -> true
        else -> null
    }

    /** Kapı, bagaj, kaput (ReadOnly): 0 kapalı, 1 açık (kapı/bagaj/kaput araçta ölçüldü). Başka değer: null. */
    fun flagOpen(raw: Int?): Boolean? = when (raw) {
        0 -> false
        1 -> true
        else -> null
    }

    /** Yakıt kapağı (CarSetting 224): VARSAYIM 0 kapalı, 1 açık (araçta yalnızca 0 görüldü). */
    fun fuelCapOpen(raw: Int?): Boolean? = flagOpen(raw)

    /** El freni (ReadOnly 75): park halinde 1 okundu, yani 1 = çekili (varsayım: 0 = inik). */
    fun handbrakeOn(raw: Int?): Boolean? = flagOpen(raw)
}

/** Gövdede açık olabilen parçalar; [label] kullanıcıya gösterilen ad. */
internal enum class BodyPart(@StringRes val label: Int) {
    DOOR_LF(R.string.launcher_door_lf),
    DOOR_RF(R.string.launcher_door_rf),
    DOOR_LR(R.string.launcher_door_lr),
    DOOR_RR(R.string.launcher_door_rr),
    TRUNK(R.string.launcher_trunk),
    WINDOW_LF(R.string.launcher_body_win_lf),
    WINDOW_RF(R.string.launcher_body_win_rf),
    WINDOW_LR(R.string.launcher_body_win_lr),
    WINDOW_RR(R.string.launcher_body_win_rr),
    HOOD(R.string.launcher_body_hood),
    FUEL_CAP(R.string.launcher_body_fuel_cap),
}

/** Araç kartındaki kırmızı uyarı satırının bölümleri, gösterim sırasıyla (kapılar/bagaj/kaput ayrı satırda ve şemada kalır). */
internal enum class AlertSeg { TIRE, WINDOWS, FUEL_CAP }

/** Araç kartı uyarı satırının saf kuralları (JVM'de sınanır). */
internal object CarAlertMath {
    fun isWindow(part: BodyPart): Boolean = part.name.startsWith("WINDOW_")

    /** Açık camlar, [BodyPart] sırasıyla. */
    fun windows(open: List<BodyPart>): List<BodyPart> = open.filter(::isWindow)

    /** Satırda görünen bölümler. Hiçbiri yoksa boş döner ve satır gizlenir. */
    fun segments(tireAlert: Boolean, windows: List<BodyPart>, fuelCapOpen: Boolean): List<AlertSeg> = buildList {
        if (tireAlert) add(AlertSeg.TIRE)
        if (windows.isNotEmpty()) add(AlertSeg.WINDOWS)
        if (fuelCapOpen) add(AlertSeg.FUEL_CAP)
    }
}

/**
 * [ExtraSource] verisi. Her alan null ise "okunamadı / bilinmiyor". [parts] gövde parçalarının açık (true) /
 * kapalı (false) durumunu taşır; hiç okunamayanlar haritada yoktur.
 */
internal data class ExtraData(
    val connected: Boolean = false,
    val driveMinutes: Int? = null,
    val batteryV: Double? = null,
    val batterySoc: Int? = null,
    val padState: Int? = null,
    val phoneForgotten: Boolean? = null,
    val handbrakeOn: Boolean? = null,
    val parts: Map<BodyPart, Boolean> = emptyMap(),
    /** Lastik basıncı (ReadOnly 140, uyarı 95); eski yazılımda ya da çözülemezse null. */
    val tires: TireReading? = null,
) {
    /** Gövde hakkında en az bir parça okunabildi mi. */
    val bodyKnown: Boolean get() = parts.isNotEmpty()

    /** Açık parçalar, [BodyPart] sırasıyla. */
    fun openParts(): List<BodyPart> = BodyPart.values().filter { parts[it] == true }

    fun hoodOpen(): Boolean = parts[BodyPart.HOOD] == true
}

/**
 * Salt-okur sinyalleri yalnızca dinleyen bir widget varken okur (ana ekran gizlenince durur). Tek okuyucu: tüm yeni
 * kutucuklar ve araç kartı aynı turu paylaşır. Animasyon döngüsü yok. Hızlı katman 4 sn'de bir (kapılar, bagaj, kaput,
 * hız limiti, el freni); yavaş katman 20 sn'de bir (camlar, yakıt kapağı, kablosuz şarj, sürüş süresi, akü, SOC).
 */
internal object ExtraSource {

    fun interface Listener {
        fun onExtra(data: ExtraData)
    }

    const val POLL_MS = 4_000L
    const val SLOW_MS = 20_000L
    private const val T_FAST = 1
    private const val T_SLOW = 2
    private val RO = VDEventCarInfo.MODULE_READONLY_INFO
    private val CS = VDEventCarInfo.MODULE_CAR_SETTING
    private val tiers = PollTiers(longArrayOf(POLL_MS, SLOW_MS))
    /** Yavaş katmanın son okuduğu gövde parçaları (camlar, yakıt kapağı); hızlı turlarda korunur. */
    private var slowParts: Map<BodyPart, Boolean> = emptyMap()
    /** Hızlı katmanın son okuduğu gövde parçaları (kapılar, bagaj, kaput). */
    private var fastParts: Map<BodyPart, Boolean> = emptyMap()

    private val listeners = CopyOnWriteArraySet<Listener>()
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "ExtraSource").apply { isDaemon = true } }
    private var task: ScheduledFuture<*>? = null
    private var appContext: Context? = null

    @Volatile
    var last: ExtraData = ExtraData()
        private set

    fun add(context: Context, listener: Listener) {
        appContext = context.applicationContext
        listeners += listener
        listener.onExtra(if (LauncherDemo.enabled) ReadOnlyDemo.extra() else last)
        synchronized(this) {
            if (task == null) {
                tiers.reset()
                task = io.scheduleWithFixedDelay({ poll() }, 0, POLL_MS, TimeUnit.MILLISECONDS)
            }
        }
    }

    fun remove(listener: Listener) {
        listeners -= listener
        synchronized(this) {
            if (listeners.isEmpty()) {
                task?.cancel(false)
                task = null
            }
        }
    }

    private fun poll() {
        // Yakalanmayan hata scheduleWithFixedDelay'i sessizce iptal eder; döngü sürsün.
        try {
            val data = if (LauncherDemo.enabled) ReadOnlyDemo.extra() else readDue() ?: return
            if (data == last && !LauncherDemo.enabled) return
            last = data
            main.post { listeners.forEach { it.onExtra(data) } }
        } catch (t: Throwable) {
            Log.w("ExtraSource", "poll başarısız", t)
        }
    }

    private fun readDue(): ExtraData? {
        val proxy = CarInfoProxy.getInstance()
        if (!proxy.isServiceConnnected) {
            appContext?.let { proxy.init(it) }
            tiers.reset()
            slowParts = emptyMap(); fastParts = emptyMap()
            return ExtraData()
        }
        val prev = last
        val now = SystemClock.elapsedRealtime()
        // Yeni bağlanıldıysa hepsi birlikte okunur.
        val mask = if (prev.connected) tiers.due(now) else tiers.all().also { tiers.markAll(now) }
        if (mask == 0) return null
        fun ro(id: Int) = CarSignals.readInt(RO, id)
        fun cs(id: Int) = CarSignals.readInt(CS, id)
        var d = prev.copy(connected = true)
        if (mask and T_SLOW != 0) {
            slowParts = buildMap {
                fun set(part: BodyPart, open: Boolean?) { if (open != null) this[part] = open }
                set(BodyPart.WINDOW_LF, ReadOnlyDecode.windowOpen(cs(CarSettingID.ID_CAR_FL_WINDOW)))
                set(BodyPart.WINDOW_RF, ReadOnlyDecode.windowOpen(cs(CarSettingID.ID_CAR_FR_WINDOW)))
                set(BodyPart.WINDOW_LR, ReadOnlyDecode.windowOpen(cs(CarSettingID.ID_CAR_RL_WINDOW)))
                set(BodyPart.WINDOW_RR, ReadOnlyDecode.windowOpen(cs(CarSettingID.ID_CAR_RR_WINDOW)))
                set(BodyPart.FUEL_CAP, ReadOnlyDecode.fuelCapOpen(cs(CarSettingID.ID_FUEL_FILLER_CAP)))
            }
            d = d.copy(
                driveMinutes = ReadOnlyDecode.driveMinutes(CarSignals.read(RO, ReadOnlyID.ID_DRIVE_TIME)),
                batteryV = ReadOnlyDecode.batteryVolts(ro(ReadOnlyID.ID_BATTERY_LEVEL)),
                batterySoc = ReadOnlyDecode.batterySoc(ro(ReadOnlyID.ID_IBS_SOC)),
                padState = ReadOnlyDecode.padStateWithSwitch(
                    ReadOnlyDecode.padState(cs(CarSettingID.ID_CAR_WIRLESS_CHARGE_STATE)),
                    cs(CarSettingID.ID_CAR_WIRLESS_CAHRGE_SET),
                ),
                tires = TireDecode.parse(CarSignals.read(RO, ReadOnlyIds.TIRE_PRESSURE), ro(ReadOnlyID.ID_TIRE_PRESSURE_WARNING)),
                phoneForgotten = ReadOnlyDecode.phoneForgotten(
                    cs(CarSettingID.ID_CAR_PHONE_FORGOT_ON_WIRLESS_BOARD), cs(CarSettingID.ID_CAR_PHONE_FORGOT),
                ),
            )
        }
        if (mask and T_FAST != 0) {
            fastParts = buildMap {
                fun set(part: BodyPart, open: Boolean?) { if (open != null) this[part] = open }
                set(BodyPart.DOOR_LF, ReadOnlyDecode.flagOpen(ro(ReadOnlyID.ID_DRIVE_DOOR_STATE)))
                set(BodyPart.DOOR_RF, ReadOnlyDecode.flagOpen(ro(ReadOnlyID.ID_PASSENGER_DOOR_STATE)))
                set(BodyPart.DOOR_LR, ReadOnlyDecode.flagOpen(ro(ReadOnlyID.ID_LFFT_BEHIND_DOOR_STATE)))
                set(BodyPart.DOOR_RR, ReadOnlyDecode.flagOpen(ro(ReadOnlyID.ID_RIGHT_BEHIND_DOOR_STATE)))
                set(BodyPart.TRUNK, ReadOnlyDecode.flagOpen(ro(ReadOnlyID.ID_TRUNK_STATE)))
                set(BodyPart.HOOD, ReadOnlyDecode.flagOpen(ro(ReadOnlyID.ID_HOOD_STATE)))
            }
            d = d.copy(
                handbrakeOn = ReadOnlyDecode.handbrakeOn(ro(ReadOnlyID.ID_HANDBRAKE_STATE)),
            )
        }
        // Parçalar BodyPart sırasıyla birleşir (sıralı harita); yavaş parçalar son okunandan gelir.
        val merged = LinkedHashMap<BodyPart, Boolean>()
        BodyPart.values().forEach { part -> (fastParts[part] ?: slowParts[part])?.let { merged[part] = it } }
        return d.copy(parts = merged)
    }
}

/** Emülatör demosu için makul değerler (bkz. [LauncherDemo]): 1 sa 12 dk sürüş, %85 akü, 50 limiti, pedde şarj olan telefon. */
internal object ReadOnlyDemo {
    fun extra(): ExtraData = ExtraData(
        connected = true,
        driveMinutes = 72,
        batteryV = 14.1,
        batterySoc = 85,
        padState = if (LauncherDemo.charging) ReadOnlyDecode.PAD_CHARGING else ReadOnlyDecode.PAD_EMPTY,
        phoneForgotten = false,
        handbrakeOn = true,
        // Demo: `launcher_demo_open` içinde wlf/wrf/wlr/wrr (camlar) ve fuel (yakıt kapağı) açık sayılır.
        parts = BodyPart.values().associateWith { false } + mapOf(
            BodyPart.WINDOW_LF to ("wlf" in LauncherDemo.openParts),
            BodyPart.WINDOW_RF to ("wrf" in LauncherDemo.openParts),
            BodyPart.WINDOW_LR to ("wlr" in LauncherDemo.openParts),
            BodyPart.WINDOW_RR to ("wrr" in LauncherDemo.openParts),
            BodyPart.FUEL_CAP to ("fuel" in LauncherDemo.openParts),
        ),
        tires = TireReading(LauncherDemo.TIRES, warning = LauncherDemo.TIRE_WARNING),
    )
}

/** [CenteredTile] + [ExtraSource] dinleyicisi; alt sınıflar yalnızca [bind] yazar. */
internal abstract class ExtraTile(
    context: Context,
    iconRes: Int,
    labelRes: Int,
    /** 2x2'de uzun metin değerleri sığsın diye değer yazısı bu boyuta (sp) küçülür; null = varsayılan. */
    private val narrowValueSp: Float? = null,
) : CenteredTile(context, iconRes, labelRes) {

    protected var data = ExtraData()
        private set
    private var alertSurface = false
    private val listener = ExtraSource.Listener {
        data = it
        bind()
    }

    override fun applySpan(span: Span) {
        super.applySpan(span)
        narrowValueSp?.let { if (tier == 0) valueText.textSize = it }
    }

    override fun onVisible() = ExtraSource.add(context, listener)

    override fun onHidden() = ExtraSource.remove(listener)

    override fun release() = ExtraSource.remove(listener)

    override fun bindDetail() = bind()

    /** Tema değişti: [CenteredTile] kart yüzeyini normale çevirdi; uyarı zemini [bind] ile yeniden verilir. */
    override fun resetThemeState() {
        alertSurface = false
    }

    /** Sakin uyarı zemini: kart yüzeyi ALERT_SURFACE tonuna geçer; uyarı geçince normale döner. */
    protected fun setAlertSurface(on: Boolean) {
        if (on == alertSurface && view.background != null) return
        alertSurface = on
        view.background = if (on) cardSurface(context, LauncherTheme.ALERT_SURFACE) else cardSurface(context)
    }

    protected abstract fun bind()
}

/**
 * Mola hatırlatıcı: sürüş süresi (ReadOnly DRIVE_TIME) "1 sa 12 dk" + "sürüş". [ReadOnlyDecode.BREAK_AFTER_MIN]
 * dakikayı geçince sakin ALERT zeminine ve "Mola zamanı"na geçer. Süre OEM sayacıdır (kontak açılınca başlar);
 * gerçek mola algılanmaz. Birimler araçta doğrulandı (dk/sn), 1 saat üstü doğrulanmadı.
 */
internal class BreakTile(context: Context) :
    ExtraTile(context, R.drawable.ic_mdi_timer_sand, R.string.launcher_widget_break, narrowValueSp = 32f) {

    override fun bind() {
        val minutes = data.driveMinutes
        if (!data.connected || minutes == null) {
            setAlertSurface(false)
            showMissing()
            return
        }
        val duration = durationText(minutes)
        if (ReadOnlyDecode.breakDue(minutes)) {
            setAlertSurface(true)
            setIcon(R.drawable.ic_mdi_alert, LauncherTheme.ALERT)
            setValue(context.getString(R.string.launcher_break_now), LauncherTheme.ALERT)
            showDetail(context.getString(R.string.launcher_break_driving_for, duration), LauncherTheme.ALERT)
        } else {
            setAlertSurface(false)
            setIcon(R.drawable.ic_mdi_timer_sand, LauncherTheme.TEXT_SECONDARY)
            setValue(duration, LauncherTheme.TEXT, animate = false)
            showDetail(context.getString(R.string.launcher_break_driving))
        }
    }

    private fun durationText(minutes: Int): String =
        if (minutes >= 60) context.getString(R.string.launcher_break_hm, minutes / 60, minutes % 60)
        else context.getString(R.string.launcher_break_m, minutes)
}

/**
 * Telefon hatırlatıcı: kablosuz şarj padinin durumu ("Telefon şarjda" / "Şarj pedi boş"). Pedde unutulan telefon
 * bayrağı (PHONE_FORGOT_ON_WIRLESS_BOARD) kalkarsa sakin ALERT zeminiyle "Telefonunu al". Araçta yalnızca pedin
 * boş (0) ve telefon pedde (1) durumları araçta ölçüldü (CarSetting 44); 2/3 ve anahtar (CS 45) OEM kodundan.
 */
internal class PhoneTile(context: Context) :
    ExtraTile(context, R.drawable.ic_mdi_cellphone, R.string.launcher_widget_phone, narrowValueSp = 30f) {

    override fun bind() {
        val pad = data.padState
        val forgot = data.phoneForgotten == true
        if (!data.connected || (pad == null && data.phoneForgotten == null)) {
            setAlertSurface(false)
            showMissing()
            return
        }
        if (forgot) {
            setAlertSurface(true)
            setIcon(R.drawable.ic_mdi_alert, LauncherTheme.ALERT)
            setValue(context.getString(R.string.launcher_phone_take), LauncherTheme.ALERT)
            showDetail(context.getString(R.string.launcher_phone_on_pad), LauncherTheme.ALERT)
            return
        }
        setAlertSurface(false)
        setIcon(R.drawable.ic_mdi_cellphone, LauncherTheme.TEXT_SECONDARY)
        val state = when (pad) {
            ReadOnlyDecode.PAD_CHARGING -> R.string.launcher_phone_charging
            ReadOnlyDecode.PAD_FULL -> R.string.launcher_phone_charge_full
            ReadOnlyDecode.PAD_ERROR -> R.string.launcher_phone_charge_error
            ReadOnlyDecode.PAD_EMPTY -> R.string.launcher_phone_pad_empty
            ReadOnlyDecode.PAD_OFF -> R.string.launcher_phone_pad_off
            else -> null
        }
        if (state == null) {
            showMissing()
            return
        }
        val color = when (pad) {
            ReadOnlyDecode.PAD_EMPTY, ReadOnlyDecode.PAD_OFF -> LauncherTheme.TEXT_SECONDARY
            ReadOnlyDecode.PAD_ERROR -> LauncherTheme.ALERT
            else -> LauncherTheme.TEXT
        }
        setValue(context.getString(state), color)
        showDetail(null)
    }
}

/**
 * Büyük saat: ortalı saat ve tarih. Araç verisi yok; [TextClock] dakikada bir sistem yayınıyla güncellenir
 * (kendi zamanlayıcımız ya da animasyon yok). 2x2'de saat 56sp, 4x2'de 88sp.
 */
internal class ClockTile(private val context: Context) : SizedWidget, Themeable, StripSource {

    // --- Şerit (Sahne): saat dakikada bir, yalnızca şeritte gösterilirken ([onStripChanged] atanmışsa) ---
    override var onStripChanged: (() -> Unit)? = null
    private val stripHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val stripTick = object : Runnable {
        override fun run() {
            onStripChanged?.invoke()
            val now = System.currentTimeMillis()
            stripHandler.postDelayed(this, 60_000L - now % 60_000L + 50L)
        }
    }

    override fun stripState(): StripState {
        val now = java.util.Date()
        return StripState(
            android.text.format.DateFormat.format(datePattern, now).toString().replaceFirstChar { it.titlecase(Locale.getDefault()) },
            android.text.format.DateFormat.format("HH:mm", now).toString(),
            R.drawable.ic_mdi_clock_outline,
        )
    }

    override fun stripTap(part: Int) = Unit

    /** Tema bağlı atamalar (kurucuda bir kez, [applyTheme]'de yeniden); bkz. [ThemeBindings]. */
    private val theme = ThemeBindings()

    private val time = TextClock(context).apply {
        format12Hour = "HH:mm"
        format24Hour = "HH:mm"
        theme.bind { setTextColor(LauncherTheme.TEXT) }
        typeface = launcherTypeface(context, medium = true)
        fontFeatureSettings = "tnum"
        includeFontPadding = false
        gravity = Gravity.CENTER
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, LauncherTheme.TIME_SP)
    }
    private val datePattern: String =
        android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEE d MMMM")
    /** Geniş kutuda tarih iki satır: gün adı üstte, gün ve ay altta (yerel desenlerin ikisi ayrı alınır). */
    private val dateWidePattern: String =
        android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEE") + "'\n'" +
            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "d MMMM")
    private val date = TextClock(context).apply {
        format12Hour = datePattern
        format24Hour = datePattern
        theme.bind { setTextColor(LauncherTheme.TEXT_SECONDARY) }
        typeface = launcherTypeface(context)
        includeFontPadding = false
        gravity = Gravity.CENTER
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, LauncherTheme.LABEL_SP)
    }

    private val root: LinearLayout = card(context, LinearLayout.VERTICAL, paddingDp = PADDING_DP).apply {
        gravity = Gravity.CENTER
        minimumHeight = context.dp(LauncherTheme.TOUCH_MIN_DP)
        theme.bind { background = cardSurface(context) }
    }

    override val view: View get() = root

    /** Tema değişti: yüzey ve saat/tarih renkleri yeniden atanır (saat çalışmaya devam eder). */
    override fun applyTheme() = theme.reapply()

    /** 2x2: saat üstte, tarih altta (56sp). 4x2: saat solda 80sp, tarih sağda iki satır; kutu alçak olduğundan yan yana. */
    override fun applySpan(span: Span) {
        val wide = span.w >= WIDE_MIN_COLS
        root.removeAllViews()
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
        if (wide) {
            root.orientation = LinearLayout.HORIZONTAL
            time.gravity = Gravity.CENTER
            time.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, WIDE_TIME_SP)
            date.format12Hour = dateWidePattern
            date.format24Hour = dateWidePattern
            date.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            date.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, LauncherTheme.SECTION_SP * 0.75f)
            root.addView(time, LinearLayout.LayoutParams(wrap, wrap))
            root.addView(gap(context, 32))
            root.addView(date, LinearLayout.LayoutParams(wrap, wrap))
        } else {
            root.orientation = LinearLayout.VERTICAL
            time.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, LauncherTheme.TIME_SP)
            date.format12Hour = datePattern
            date.format24Hour = datePattern
            date.gravity = Gravity.CENTER
            date.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, LauncherTheme.LABEL_SP)
            root.addView(time, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, wrap))
            root.addView(date, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, wrap).apply { topMargin = context.dp(6) })
        }
    }

    override fun onVisible() {
        stripHandler.removeCallbacks(stripTick)
        if (onStripChanged != null) stripTick.run()
    }

    override fun onHidden() {
        stripHandler.removeCallbacks(stripTick)
    }

    private companion object {
        const val PADDING_DP = 16
        const val WIDE_TIME_SP = 80f
    }
}
