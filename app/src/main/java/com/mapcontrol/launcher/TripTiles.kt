package com.mapcontrol.launcher

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import android.util.Log
import com.desaysv.ivi.extra.project.carinfo.HvacID
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.extra.project.carinfo.proxy.Constants
import com.desaysv.ivi.vdb.client.VDBus
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.R
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Tek sinyali okur (VDBus.getOnce). Cevap yoksa null; CarInfoProxy.getItemValues bunu {0} ile gizler. */
internal object CarSignals {
    fun read(moduleId: Int, id: Int): IntArray? = try {
        val bundle = Bundle().apply { putInt(Constants.CMD_ID, id) }
        VDBus.getDefault().getOnce(VDEvent(moduleId, bundle))?.payload?.getIntArray(Constants.VALUE)
    } catch (e: RuntimeException) {
        null
    }

    /** Çok elemanlı cevaplar VehicleMetricsCache gibi bayt bayt birleştirilir; tek eleman olduğu gibi. */
    fun readInt(moduleId: Int, id: Int): Int? {
        val v = read(moduleId, id) ?: return null
        if (v.isEmpty()) return null
        return if (v.size == 1) v[0] else v.fold(0) { acc, b -> (acc shl 8) or (b and 0xFF) }
    }
}

/**
 * Yolculuk sinyallerinin ham değerden çözümü (saf işlevler, JVM'de sınanır). Tiggo 7'de 2026-10-29'da gösterge
 * TripB sayfasıyla karşılaştırıldı: mesafe, ortalama tüketim ve ortalama hız onda bir birimdir.
 */
internal object TripDecode {
    /** Motor kapalı (devir 0) ve hız 0 iken canlı katman 3 sn'ye seyreltilir; devir okunamazsa seyreltilmez. */
    fun liveIntervalMs(rpmRaw: Int?, speedKmh: Int?): Long =
        if (rpmRaw == 0 && speedKmh == 0) 3_000L else 1_000L

    /**
     * Dış sıcaklık (HVAC 19 `ID_AC_TEMPERATURE_CELSIUS_OUTSIDE`): °C = ham / 2 − 55 (0,5 °C adım). Araçta iki noktayla
     * doğrulandı (2026-11-06, Tiggo 7): ham 144 = gösterge 17 °C, ham 140 = 15 °C. 255 (0xFF) ve −40..80 dışı geçersiz.
     */
    fun outsideC(raw: Int?): Double? = raw?.takeIf { it in 0..254 }?.let { it / 2.0 - 55.0 }?.takeIf { it in -40.0..80.0 }

    /** Anlık tüketim (sinyal 70): ham/10 L/100 km. 0xFFFF (araç duruyor) ve aralık dışı değer geçersizdir. */
    fun instantL100(raw: Int?): Double? = raw?.takeIf { it in 0..999 }?.let { it / 10.0 }

    /** Gösterge hızı (sinyal 119): km/sa doğrudan. Aralık dışı değer geçersizdir. */
    fun speedKmh(raw: Int?): Int? = raw?.takeIf { it in 0..300 }

    /**
     * Gösterge hızı (119) varsa o, yoksa eski hız sinyali (0) kullanılır; ilki 0/geçersizse ikincisi denenir.
     * Tiggo 7'de sinyal 0 hareket ederken de 0/geçersiz kalıyordu.
     */
    fun speed(gauge: Int?, legacy: Int?): Int? {
        val g = speedKmh(gauge)
        if (g != null && g > 0) return g
        val l = speedKmh(legacy)
        if (l != null && l > 0) return l
        return g ?: l
    }

    /** Hareket kararı: hız > 0 ya da anlık tüketim geçerli. Hiçbiri okunamadıysa null (bilinmiyor). */
    fun moving(speedKmh: Int?, instantL100: Double?): Boolean? = when {
        speedKmh != null && speedKmh > 0 -> true
        instantL100 != null -> true
        speedKmh != null -> false
        else -> null
    }

    /** Sıfırlamadan beri mesafe (sinyal 40): ham/10 km. */
    fun tripKm(raw: Int?): Double? = raw?.takeIf { it >= 0 }?.let { it / 10.0 }

    /** Sıfırlamadan beri ortalama tüketim (sinyal 37): ham/10 L/100 km. */
    fun avgL100(raw: Int?): Double? = raw?.takeIf { it in 1..999 }?.let { it / 10.0 }

    /**
     * Toplam km (ReadOnly 150 `ID_TOTAL_MILEAGE`, yoksa 71 `ID_GRAND_TOTAL_KM`): tam km. 71 araçta gösterge ile eşleşti (4 bayt
     * [0,0,43,24] = 11032 km); 150'nin aynı birimde olduğu varsayıldı (yeni yazılımın ID'si, OEM uygulamalarında kullanımı yok). Doğrulanmadı.
     */
    fun totalKm(raw: Int?): Int? = raw?.takeIf { it in 1..9_999_999 }

    /**
     * Toplam km önce 71'den (`ID_GRAND_TOTAL_KM`, araçta [0,0,43,24] = 11032 km, göstergeyle eşleşti), geçersizse 150'den.
     * 150 (`ID_TOTAL_MILEAGE`) OEM `ReadOnlyID` tanımlarında var ama hiçbir OEM uygulaması 150/153/71'i okumuyor: birimi ve anlamı doğrulanmadı;
     * makul görünen yanlış bir değer (km×10, başka sayaç) doğrulanmış 71'in önüne geçmesin diye yalnızca yedek.
     * Her biri ayrı çözülür: `?:` yalnızca null'da düştüğünden geçersiz (0) birincil km satırını kaybettiriyordu.
     */
    fun totalKm(verifiedRaw: Int?, fallbackRaw: Int?): Int? = totalKm(verifiedRaw) ?: totalKm(fallbackRaw)

    /** Toplam yakıt (ReadOnly 153 `ID_TOTAL_FUEL`): birim OEM kodunda yok; ham/10 litre varsayıldı (SUM_FUEL gibi). Doğrulanmadı. */
    fun totalFuelL(raw: Int?): Double? = raw?.takeIf { it in 1..9_999_999 }?.let { it / 10.0 }

    /**
     * Sürüş süresi (151 çalışmadan beri / 152 sıfırlamadan beri): `ID_DRIVE_TIME` (29) gibi 4 bayt [saat üst, saat alt, dk, sn]
     * ise onun çözümü; 1-2 elemanlı ise doğrudan dakika varsayılır. Doğrulanmadı.
     */
    fun drivingMinutes(raw: IntArray?): Int? = when (raw?.size) {
        4 -> ReadOnlyDecode.driveMinutes(raw)
        1, 2 -> raw.fold(0) { acc, b -> (acc shl 8) or (b and 0xFF) }.takeIf { it in 0..99_999 }
        else -> null
    }

    /** Sıfırlamadan beri ortalama hız (sinyal 41): ham/10, gösterge gibi tam sayıya yuvarlanmadan kırpılır (456 -> 45). */
    fun avgSpeedKmh(raw: Int?): Int? = raw?.takeIf { it in 1..3000 }?.let { it / 10 }
}

/**
 * Yolculuk bilgisayarı verisi. Çevrimler Tiggo 7'de doğrulandı (2026-10-29, gösterge TripB ile eşleşti);
 * [driveAvgL100] hâlâ doğrulanmadı (okunan [0,0] = 0). [instantL100] duruyorken 0xFFFF (geçersiz) gelir.
 */
internal data class TripData(
    val connected: Boolean = false,
    val moving: Boolean? = null,
    /** Gösterge hızı (km/sa); anlık tüketimin düşük hızda gösterilmemesi için. */
    val speedKmh: Int? = null,
    val instantL100: Double? = null,
    val driveAvgL100: Double? = null,
    val tripKm: Double? = null,
    val avgL100: Double? = null,
    val avgSpeedKmh: Int? = null,
    val batteryV: Double? = null,
    /** Aşırı gerilim 30 sn sürdü ([OverVoltageGate]); yalnızca o zaman "Akü yüksek" uyarısı çıkar. */
    val batteryHighHeld: Boolean = false,
    /** Motor suyu durum kodu (ReadOnly 100, ham; derece değil, bkz. [CoolantStatus]). */
    val coolantCode: Int? = null,
    val lowFuel: Boolean? = null,
    /** Servise kalan km / gün (ICMT). Araçta 2026-10-29'da okundu, gösterge menüsüyle henüz karşılaştırılmadı. */
    val serviceKm: Int? = null,
    val serviceDays: Int? = null,
    /** Kabin içi / dışı PM2.5 (µg/m³). */
    val pm25In: Int? = null,
    val pm25Out: Int? = null,
    /** Dış sıcaklık °C ([TripDecode.outsideC]); 5 sn katmanında okunur. */
    val outsideC: Double? = null,
    /** Toplam km (150, yoksa 71), toplam yakıt L (153), sürüş süresi dk: çalışmadan beri (151) ve sıfırlamadan beri (152). */
    val totalKm: Int? = null,
    val totalFuelL: Double? = null,
    val drivingRunMin: Int? = null,
    val drivingResetMin: Int? = null,
    /** ID_DRIVE_TIME (29) / GRAND_TIME_AFTER_RUNNING (50) dakikası; 151/152 sıfır dönerse yedek. */
    val drivingAltMin: Int? = null,
    /** Kendi biriktirdiğimiz motor-açık süresi (dk): hiçbir sinyal sıfırdan büyük değilse son yedek. */
    val engineOnMin: Int? = null,
    /** Far, sinyaller ve geri vites (canlı katmanda; Sahne dijital ikizi için, bkz. [CarLightSignals]). */
    val lights: CarLights = CarLights.OFF,
)

/**
 * Yolculuk sinyallerini yalnızca dinleyen bir widget varken okur (ana ekran gizlenince durur). Tek okuyucu: birden çok
 * widget aynı turu paylaşır. Yalnızca okur. Her sinyal kendi hızında okunur ([PollTiers]):
 *  - canlı (hız, anlık tüketim, far / sinyal / geri vites): 1 sn; motor kapalı ve hız 0 iken 3 sn,
 *  - yolculuk (mesafe, ortalamalar, akü, soğutma suyu, düşük yakıt, motor devri): 5 sn,
 *  - bakım, PM2.5 ve toplamlar (toplam km, toplam yakıt, sürüş süreleri): 60 sn.
 */
internal object TripSource {

    fun interface Listener {
        fun onTrip(data: TripData)
    }

    const val POLL_MS = 1_000L
    const val TRIP_MS = 5_000L
    const val SLOW_MS = 60_000L
    private const val T_LIVE = 1
    private const val T_TRIP = 2
    private const val T_SLOW = 4
    private val RO = VDEventCarInfo.MODULE_READONLY_INFO
    private val HVAC = VDEventCarInfo.MODULE_HVAC

    internal fun liveIntervalMs(rpmRaw: Int?, speedKmh: Int?): Long = TripDecode.liveIntervalMs(rpmRaw, speedKmh)

    private val listeners = CopyOnWriteArraySet<Listener>()
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "TripSource").apply { isDaemon = true } }
    private var task: ScheduledFuture<*>? = null
    private var appContext: Context? = null
    private val tiers = PollTiers(longArrayOf(POLL_MS, TRIP_MS, SLOW_MS))
    @Volatile private var rpmRaw: Int? = null
    private var engineOnMs = 0L
    private val overVoltage = OverVoltageGate()
    private var lastLiveAt = 0L

    @Volatile
    var last: TripData = TripData()
        private set

    fun add(context: Context, listener: Listener) {
        appContext = context.applicationContext
        listeners += listener
        listener.onTrip(if (LauncherDemo.enabled) LauncherDemo.trip() else last)
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
            val data = if (LauncherDemo.enabled) LauncherDemo.trip() else readDue() ?: return
            if (data == last && !LauncherDemo.enabled) return
            last = data
            main.post { listeners.forEach { it.onTrip(data) } }
        } catch (t: Throwable) {
            Log.w("TripSource", "poll başarısız", t)
        }
    }

    /** Süresi dolan katmanları okur, gerisi son turdan kalır. Okunacak bir şey yoksa null. */
    private fun readDue(): TripData? {
        val prev = last
        val now = SystemClock.elapsedRealtime()
        val liveMs = liveIntervalMs(rpmRaw, prev.speedKmh)
        val proxy = CarInfoProxy.getInstance()
        if (!proxy.isServiceConnnected) {
            appContext?.let { proxy.init(it) }
            tiers.reset()
            return TripData()
        }
        // Bağlı değilken gelindiyse (ilk bağlantı) hepsi birlikte okunur.
        val mask = if (prev.connected) tiers.due(now, liveMs) else tiers.all().also { tiers.markAll(now, liveMs) }
        if (mask == 0) return null
        fun ro(id: Int) = CarSignals.readInt(RO, id)
        fun hvac(id: Int) = CarSignals.readInt(HVAC, id)
        var d = prev.copy(connected = true)
        if (mask and T_LIVE != 0) {
            // Anlık tüketim duruyorken 0xFFFF döner; geçerli geldiğinde araç hareket ediyordur.
            val instant = TripDecode.instantL100(ro(ReadOnlyID.ID_INSTANTANEOUS_CONSUMPTION))
            // Hız: gösterge hızı (119); eski sinyal (0) bu araçta hareket ederken 0/geçersiz kalıyordu ("Araç duruyor" hatası).
            val speed = TripDecode.speed(ro(ReadOnlyID.ID_SPEED_GAUGE_DISPLAY), ro(ReadOnlyID.ID_CAR_SPEED))
            // Yedek sayaç: devir ya da hız varken geçen süre (yalnızca widget görünürken çalışır; alt sınır).
            engineOnMs = TripTotals.accumulateMs(engineOnMs, if (lastLiveAt == 0L) 0L else now - lastLiveAt, (rpmRaw ?: 0) > 0 || (speed ?: 0) > 0)
            lastLiveAt = now
            d = d.copy(moving = TripDecode.moving(speed, instant), speedKmh = speed, instantL100 = instant, engineOnMin = (engineOnMs / 60_000L).toInt(),
                lights = CarLightSignals.read(::ro))
        }
        if (mask and T_TRIP != 0) {
            rpmRaw = ro(ReadOnlyID.ID_ENGINE_RPM)
            d = d.copy(
                driveAvgL100 = ro(ReadOnlyID.ID_AVERAGE_FUEL_CONS_AFTER_RUNNING)?.takeIf { it in 1..999 }?.let { it / 10.0 },
                tripKm = TripDecode.tripKm(ro(ReadOnlyID.ID_GRAND_TOTAL_KM_AFTER_CLEAR)),
                avgL100 = TripDecode.avgL100(ro(ReadOnlyID.ID_AVERAGE_OIL_CONSUMPTION_AFTER_CLEAR)),
                avgSpeedKmh = TripDecode.avgSpeedKmh(ro(ReadOnlyID.ID_AVERAGE_SPEED_AFTER_CLEAR)),
                batteryV = ReadOnlyDecode.batteryVolts(ro(ReadOnlyID.ID_BATTERY_LEVEL)),
                coolantCode = ro(ReadOnlyID.ID_WATER_TEMPERATURE),
                lowFuel = ro(ReadOnlyID.ID_LOW_FUEL_WARNING)?.let { it == 1 },
                outsideC = TripDecode.outsideC(hvac(HvacID.ID_AC_TEMPERATURE_CELSIUS_OUTSIDE)),
            )
            d = d.copy(batteryHighHeld = overVoltage.update(d.batteryV, now))
        }
        if (mask and T_SLOW != 0) {
            d = d.copy(
                serviceKm = ro(ReadOnlyID.ID_ICMT_DISTANCE)?.takeIf { it in 0..99_999 },
                serviceDays = ro(ReadOnlyID.ID_ICMT_DAY)?.takeIf { it in 0..9_999 },
                pm25In = hvac(HvacID.ID_AC_PM25_INCAR)?.takeIf { it in 0..999 },
                pm25Out = hvac(HvacID.ID_AC_PM25_OUTCAR)?.takeIf { it in 0..999 },
                totalKm = TripDecode.totalKm(ro(ReadOnlyID.ID_GRAND_TOTAL_KM), ro(ReadOnlyIds.TOTAL_MILEAGE)),
                totalFuelL = TripDecode.totalFuelL(ro(ReadOnlyIds.TOTAL_FUEL)),
                drivingRunMin = TripDecode.drivingMinutes(CarSignals.read(RO, ReadOnlyIds.DRIVING_TIME_AFTER_RUNNING)),
                drivingResetMin = TripDecode.drivingMinutes(CarSignals.read(RO, ReadOnlyIds.DRIVING_TIME_AFTER_RESET)),
                drivingAltMin = TripDecode.drivingMinutes(CarSignals.read(RO, ReadOnlyID.ID_DRIVE_TIME))?.takeIf { it > 0 }
                    ?: TripDecode.drivingMinutes(CarSignals.read(RO, ReadOnlyID.ID_GRAND_TIME_AFTER_RUNNING)),
            )
        }
        return d
    }
}

/**
 * Yolculuk ailesinin (tüketim, yolculuk, sağlık) ortak kartı. İçerik her boyutta kartın ortasındadır:
 * başlık satırı (küçük ikon + etiket), büyük değer, isteğe bağlı küçük alt başlık. 3 sütun ve üzerinde
 * sağda ince bir ayraçla ayrılmış, dikey ortalanmış bir değer bloğu daha vardır; kart iki yarıya bölünüp
 * dolu görünür. Yalnızca okur; yeni veri uydurmaz.
 */
internal abstract class TripCard(
    protected val context: Context,
    @DrawableRes iconRes: Int,
    @StringRes private val labelRes: Int,
) : SizedWidget, Themeable, StripSource {

    // --- Şerit (Sahne) özeti ---
    override var onStripChanged: (() -> Unit)? = null
    private var stripIcon = iconRes
    private var stripValue = ""
    private var stripTone = StripTone.MUTED
    private var stripDetail: String? = null

    private var stripRows: List<Pair<String, String>> = emptyList()
    private var stripAlertRows: Set<Int> = emptySet()
    private var stripHistory: List<Float> = emptyList()

    override fun stripState(): StripState =
        StripState(context.getString(labelRes), stripValue, stripIcon, stripTone, detail = stripDetail, rows = stripRows,
            alertRows = stripAlertRows, history = stripHistory)

    /** Panel biçimindeki çubuk geçmişi (eskiden yeniye); kutucukta çizilmez. */
    protected fun setHistory(values: List<Float>) {
        if (values == stripHistory) return
        stripHistory = values
        notifyStrip()
    }

    override fun stripTap(part: Int) { root.performClick() }

    private fun notifyStrip() { onStripChanged?.invoke() }

    /** Tema bağlı atamalar (kurucuda bir kez, [applyTheme]'de yeniden); bkz. [ThemeBindings]. */
    private val theme = ThemeBindings()

    protected var data = TripData()
        private set

    /** Sağ blok gösterilen boyut (>= 3 sütun). */
    protected var wide = false
        private set

    /** Büyük boyut (>= 4 sütun ve >= 4 satır): sağ blokta ek bölüm ve fazladan satırlar için yer var. */
    protected var large = false
        private set

    private val listener = TripSource.Listener {
        data = it
        bind()
    }

    protected val headerIcon: ImageView = icon(context, iconRes, TITLE_ICON_DP).also { v -> theme.bind { v.setTint(LauncherTheme.TEXT_SECONDARY) } }
    private val titleText = text(context, TITLE_SP).apply {
        setText(labelRes)
        theme.bind { setTextColor(LauncherTheme.TEXT_SECONDARY) }
    }
    private val valueText = text(context, VALUE_SP, medium = true).apply {
        gravity = Gravity.CENTER
        theme.bind { setTextColor(LauncherTheme.TEXT) }
    }
    private val subtitleText = text(context, TITLE_SP, maxLines = 2).apply {
        gravity = Gravity.CENTER
        visibility = View.GONE
        theme.bind { setTextColor(LauncherTheme.TEXT_SECONDARY) }
    }

    private val mainColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(headerIcon)
            addView(gap(context, 8))
            addView(titleText)
        })
        addView(valueText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(subtitleText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private val divider = View(context).apply {
        theme.bind { setBackgroundColor(LauncherTheme.TRACK) }
        visibility = View.GONE
    }

    /** Sağ blok: dikey ortalanmış, başa hizalı etiketli satırlar. */
    private val rightBlock = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
    }

    protected val root: LinearLayout = card(context, LinearLayout.HORIZONTAL, paddingDp = PADDING_DP).apply {
        minimumHeight = context.dp(LauncherTheme.TOUCH_MIN_DP)
        gravity = Gravity.CENTER_VERTICAL
        theme.bind { background = cardSurface(context) }
        addView(mainColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(divider, LinearLayout.LayoutParams(context.dp(1), context.dp(DIVIDER_DP)).apply {
            marginStart = context.dp(8)
            marginEnd = context.dp(16)
        })
        addView(rightBlock, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, RIGHT_WEIGHT))
    }

    override val view: View get() = root

    override fun onVisible() = TripSource.add(context, listener)

    override fun onHidden() = TripSource.remove(listener)

    override fun release() = TripSource.remove(listener)

    override fun applySpan(span: Span) {
        wide = span.w >= 3
        large = span.w >= 4 && span.h >= 4
        bind()
    }

    /** Tema değişti: sabit renkler varsayılana döner, ardından değer/ikon/alt başlık/sağ satırlar [bind] ile yeniden atanır. */
    override fun applyTheme() {
        theme.reapply()
        bind()
    }

    protected abstract fun bind()

    protected fun one(value: Double): String = String.format(java.util.Locale.getDefault(), "%,.1f", value)

    protected fun setIcon(@DrawableRes res: Int, tint: Int) {
        headerIcon.setImageResource(res)
        headerIcon.imageTintList = ColorStateList.valueOf(tint)
        stripIcon = res
    }

    protected fun setValue(value: CharSequence, color: Int, sizeSp: Float = VALUE_SP) {
        valueText.textSize = sizeSp
        valueText.setTextColor(color)
        // Tüketim, yolculuk ve sağlık değerleri sürüşte sürekli akar: geçişsiz.
        valueText.setTextSmooth(unitStyled(value), animate = false)
        stripValue = value.toString()
        stripTone = stripToneFor(color)
        notifyStrip()
    }

    protected fun setSubtitle(subtitle: CharSequence?, color: Int = LauncherTheme.TEXT_SECONDARY) {
        subtitleText.setTextColor(color)
        subtitleText.textSize = TITLE_SP
        subtitleText.text = subtitle
        subtitleText.visibility = if (subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
        stripDetail = subtitle?.toString()
        notifyStrip()
    }

    /**
     * Sağ blok satırları (etiket null ise yalnız değer). [alert] uyarı rengiyle gösterilecek satır numaraları.
     * [section] varsa satırların altında başlıklı, tek satırlık (etiket solda, değer sağda) ek bir bölüm çizilir
     * (yolculukta "Toplam"). Dar boyutta ya da satır yoksa blok ve ayraç gizlenir ve sol sütun tüm kartı alır.
     */
    protected fun showRows(rows: List<Pair<String?, String>>, alert: Set<Int> = emptySet(), section: Section? = null) {
        stripRows = rows.map { (it.first ?: "") to it.second } + (section?.rows ?: emptyList())
        stripAlertRows = alert
        notifyStrip()
        rightBlock.removeAllViews()
        val show = wide && (rows.isNotEmpty() || section != null)
        rows.forEachIndexed { i, (label, value) ->
            rightBlock.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                if (label != null) addView(text(context, TITLE_SP, LauncherTheme.TEXT_SECONDARY).apply { text = label })
                addView(unitRow(value, if (i in alert) LauncherTheme.ALERT else null))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) topMargin = context.dp(6)
            })
        }
        if (show && section != null && section.rows.isNotEmpty()) {
            rightBlock.addView(View(context).apply { theme.bind { setBackgroundColor(LauncherTheme.TRACK) } },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(1)).apply {
                    topMargin = context.dp(8)
                    bottomMargin = context.dp(6)
                })
            rightBlock.addView(text(context, TITLE_SP, LauncherTheme.TEXT_SECONDARY, bold = true).apply { text = section.title })
            section.rows.forEach { (label, value) ->
                rightBlock.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(text(context, TITLE_SP, LauncherTheme.TEXT_SECONDARY).apply { text = label }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(text(context, SECTION_VALUE_SP, medium = true).apply { text = value; gravity = Gravity.END })
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(2) })
            }
        }
        rightBlock.visibility = if (show) View.VISIBLE else View.GONE
        divider.visibility = if (show) View.VISIBLE else View.GONE
    }

    /** Sağ bloktaki başlıklı ek bölüm: [rows] (etiket, değer). */
    protected class Section(val title: String, val rows: List<Pair<String, String>>)

    /** "7,9 L/100 km" -> sayı büyük, birim küçük ve ikincil tonda. */
    private fun unitRow(value: String, color: Int? = null) = text(context, ROW_VALUE_SP, medium = true).apply {
        if (color != null) setTextColor(color)
        val i = value.indexOf(' ')
        text = if (i < 0) value else android.text.SpannableStringBuilder(value).apply {
            setSpan(android.text.style.RelativeSizeSpan(0.75f), i, length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(android.text.style.ForegroundColorSpan(color ?: LauncherTheme.TEXT_SECONDARY), i, length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    protected fun describe(vararg parts: String?) {
        root.contentDescription = parts.filterNotNull().joinToString(", ")
    }

    protected fun showMissing() {
        setValue(context.getString(R.string.launcher_unavailable), LauncherTheme.TEXT_SECONDARY)
        setSubtitle(context.getString(R.string.launcher_waiting_data))
        subtitleText.textSize = LauncherTheme.WAITING_SP
        showRows(emptyList())
        describe(context.getString(R.string.launcher_waiting_data))
    }

    protected companion object {
        /** Okunurluk alt sınırı: 2×2 kartta değer en az 28 sp, etiketler en az 18 sp. */
        const val VALUE_SP = 44f
        const val TITLE_SP = 18f
        const val ROW_VALUE_SP = 28f
        /** Başlıklı ek bölümün değerleri; ikincil bilgi olduğu için satırlardan küçük ama en az 24 sp. */
        const val SECTION_VALUE_SP = 24f
        const val TITLE_ICON_DP = 24
        const val PADDING_DP = 12
        const val DIVIDER_DP = 64
        const val RIGHT_WEIGHT = 1f
    }
}

/**
 * Anlık tüketimi gösterime çevirir (saf mantık, JVM'de sınanır). Sinyal 70 yaklaşık 2,2 sn'de bir güncellenir ve
 * kalkışta/düşük hızda anlamsız büyük değerler verir (13 km/sa'te 95 L/100 km); bu yüzden:
 * - 20 km/sa altında L/100 km gösterilmez ("Kalkış"); 22'ye çıkana dek geri dönülmez (eşikte titremesin).
 *   L/h türetilmez: tüketim ve hız farklı zamanlarda örneklendiği için ivmelenirken çarpım güvenilmez.
 * - Üstünde son [WINDOW] geçerli örneğin ortalaması 0,1'e yuvarlanır; yeni değer gösterilenden en fazla
 *   [DEADBAND] farklıysa metin değişmez. 30,0 üstü "30+".
 * - Araç durunca (hareket yok ya da hız 0 ve sinyal geçersiz) ortalama sıfırlanır.
 * - Aynı okuma iki kez sayılmaz: sinyal ~2,2 sn'de bir değişir, canlı yoklama 1 sn'de bir; örnek yalnızca değer
 *   değiştiyse ya da son örnekten bu yana [SAMPLE_GAP_MS] geçtiyse eklenir.
 * Kanıt: gerçek sürüş kaydı 2026-10-30 (7-10 km/sa 26-39, 20-25 km/sa 17-18, boşta 2,7-5 L/100 km): /10 ölçeği
 * makul; gösterge paneliyle karşılaştırma bekliyor.
 * Tüketim kutucuğu ve Spor "Anlık" kadranı aynı kuralları bu sınıfla kullanır (her tüketici kendi örneğiyle).
 */
internal class ConsumptionSmoother {
    enum class Mode { NONE, IDLE, TAKEOFF, VALUE, CAP }

    data class Display(val mode: Mode, val l100: Double? = null)

    private val samples = ArrayDeque<Double>()
    private var shown: Double? = null
    private var takeoff = false

    private var lastValue: Double? = null
    private var lastAddAt = 0L

    fun update(instantL100: Double?, speedKmh: Int?, moving: Boolean?, nowMs: Long = System.currentTimeMillis()): Display {
        val stopped = moving == false || (speedKmh == 0 && instantL100 == null)
        if (stopped) {
            reset()
            return Display(Mode.IDLE)
        }
        val low = speedKmh != null && speedKmh < (if (takeoff) EXIT_KMH else ENTER_KMH)
        if (low) {
            reset()
            takeoff = true
            return Display(Mode.TAKEOFF)
        }
        takeoff = false
        if (instantL100 != null) {
            if (samples.isEmpty() || instantL100 != lastValue || nowMs - lastAddAt >= SAMPLE_GAP_MS) {
                samples.addLast(instantL100)
                lastValue = instantL100
                lastAddAt = nowMs
            }
            while (samples.size > WINDOW) samples.removeFirst()
            val candidate = Math.round(samples.average() * 10) / 10.0
            val cur = shown
            if (cur == null || Math.abs(candidate - cur) > DEADBAND + 1e-9) shown = candidate
        }
        val v = shown ?: return Display(Mode.NONE)
        return if (v > CAP) Display(Mode.CAP, CAP) else Display(Mode.VALUE, v)
    }

    private fun reset() {
        samples.clear()
        shown = null
        takeoff = false
        lastValue = null
    }

    companion object {
        const val WINDOW = 5
        const val SAMPLE_GAP_MS = 2000L
        const val DEADBAND = 0.3
        const val CAP = 30.0
        const val ENTER_KMH = 20
        const val EXIT_KMH = 22
    }
}

/**
 * Anlık tüketim: yalnızca büyük, okunur bir rakam. Araç duruyorsa "Araç duruyor". Renk değişmez:
 * sürücüyü uyarmaz, bilgi verir. Panel biçiminde (Sahne) altında son ölçümlerin küçük çubuk geçmişi ([ConsumptionHistory])
 * ve sıfırlamadan beri ortalama tüketim satırı vardır; ikisi de bu kutucuğun zaten okuduğu veriden gelir.
 */
internal class ConsumptionTile(context: Context) :
    TripCard(context, R.drawable.ic_mdi_leaf, R.string.launcher_widget_consumption) {

    private val smoother = ConsumptionSmoother()
    private var display = ConsumptionSmoother.Display(ConsumptionSmoother.Mode.NONE)
    private var fed: TripData? = null
    private val history = ConsumptionHistory().apply { if (LauncherDemo.enabled) seed(LauncherDemo.CONSUMPTION_HISTORY) }

    override fun bind() {
        val d = data
        if (!d.connected) {
            fed = null
            showMissing()
            return
        }
        // bind boyut değişince de çağrılır; aynı veri iki kez ortalamaya girmesin.
        if (d !== fed) {
            fed = d
            display = smoother.update(d.instantL100, d.speedKmh, d.moving)
            history.add(display)
        }
        setHistory(history.values())
        val title = context.getString(R.string.launcher_widget_consumption)
        when (display.mode) {
            ConsumptionSmoother.Mode.IDLE -> {
                val idle = context.getString(R.string.launcher_consumption_idle)
                setValue(idle, LauncherTheme.TEXT_SECONDARY, IDLE_SP)
                setSubtitle(null)
                describe(title, idle)
            }
            ConsumptionSmoother.Mode.TAKEOFF -> {
                val takeoff = context.getString(R.string.launcher_consumption_takeoff)
                val low = context.getString(R.string.launcher_consumption_low_speed)
                setValue(takeoff, LauncherTheme.TEXT, IDLE_SP)
                setSubtitle(low)
                describe(title, takeoff, low)
            }
            ConsumptionSmoother.Mode.CAP -> {
                val cap = context.getString(R.string.launcher_consumption_over_cap)
                val unit = context.getString(R.string.launcher_l100_unit)
                setValue(cap, LauncherTheme.TEXT)
                setSubtitle(unit)
                describe(title, cap, unit)
            }
            ConsumptionSmoother.Mode.VALUE -> {
                val value = context.getString(R.string.launcher_l100_value, one(display.l100!!))
                setValue(value, LauncherTheme.TEXT)
                setSubtitle(null)
                describe(title, value)
            }
            ConsumptionSmoother.Mode.NONE -> showMissing()
        }
        showRows(listOfNotNull(d.avgL100?.let {
            context.getString(R.string.launcher_trip_avg_cons_label) to context.getString(R.string.launcher_l100_value, one(it))
        }))
    }

    private companion object {
        const val IDLE_SP = 32f
    }
}

/**
 * Anlık tüketimin kısa geçmişi (saf, JVM'de sınanır): yalnızca gösterilen değerler (Değer ya da 30+) eklenir; ardışık aynı
 * değer tekrar eklenmez ki duraklarda çubuklar düzleşmesin. En çok [MAX] değer, eskiden yeniye.
 */
internal class ConsumptionHistory {
    private val values = ArrayDeque<Float>()
    private var last: Double? = null

    fun add(display: ConsumptionSmoother.Display) {
        val v = display.l100 ?: return
        if (display.mode != ConsumptionSmoother.Mode.VALUE && display.mode != ConsumptionSmoother.Mode.CAP) return
        if (v == last) return
        last = v
        values.addLast(v.toFloat())
        while (values.size > MAX) values.removeFirst()
    }

    fun seed(list: List<Float>) {
        list.takeLast(MAX).forEach { values.addLast(it) }
    }

    fun values(): List<Float> = values.toList()

    companion object {
        const val MAX = 24
    }
}

/**
 * Yolculuk: gösterge TripB sayfası gibi "Sıfırlamadan beri". 2×2'de başlık, büyük mesafe ve alt başlık;
 * 3×2'de mesafe solda, sağda ortalama tüketim ve ortalama hız; 4×4'te ayrıca "Toplam" bölümü
 * (ömür boyu km, toplam yakıt, sürüş süresi). Ana değer her boyutta sıfırlamadan beri km'dir.
 * Toplam sinyalleri (150-153) eski yazılımlı araçta olmayabilir: eksik satırlar düşer, hiç yoksa bölüm gizlenir.
 */
internal class TripComputerTile(context: Context) :
    TripCard(context, R.drawable.ic_mdi_road_variant, R.string.launcher_widget_trip) {

    override fun bind() {
        val km = data.tripKm
        if (!data.connected || km == null) {
            showMissing()
            return
        }
        val distance = context.getString(R.string.launcher_km_value, one(km))
        setValue(distance, LauncherTheme.TEXT)
        setSubtitle(context.getString(R.string.launcher_trip_since_reset))
        val entries = listOfNotNull(
            data.avgL100?.let { context.getString(R.string.launcher_trip_avg_cons_label) to context.getString(R.string.launcher_l100_value, one(it)) },
            data.avgSpeedKmh?.let { context.getString(R.string.launcher_trip_avg_speed_label) to context.getString(R.string.launcher_kmh_value, it) },
        )
        val totals = if (large) totalsRows() else emptyList()
        showRows(entries, section = if (totals.isEmpty()) null else Section(context.getString(R.string.launcher_widget_trip_totals), totals))
        val spoken = (entries.map { "${it.first} ${it.second}" } + totals.map { "${it.first} ${it.second}" }).toTypedArray()
        describe(context.getString(R.string.launcher_trip_since_reset), distance, *spoken)
    }

    /** "Toplam" bölümünün satırları: ömür boyu km, toplam yakıt, sürüş süresi (okunabilenler). */
    private fun totalsRows(): List<Pair<String, String>> {
        val d = data
        val time = TripTotals.drivingMinutes(d.drivingRunMin, d.drivingResetMin, d.drivingAltMin, d.engineOnMin)
        return TripTotals.rows(d.totalKm, d.totalFuelL, time).map { (kind, value) ->
            when (kind) {
                TripTotals.Kind.ODOMETER -> context.getString(R.string.launcher_totals_odometer) to
                    context.getString(R.string.launcher_km_value, String.format(java.util.Locale.getDefault(), "%,d", value as Int))
                TripTotals.Kind.FUEL -> context.getString(R.string.launcher_totals_fuel_label) to
                    context.getString(R.string.launcher_liter_value, one(value as Double))
                TripTotals.Kind.TIME -> context.getString(R.string.launcher_totals_time_label) to duration(value as Int)
            }
        }
    }

    private fun duration(min: Int): String =
        if (min >= 60) context.getString(R.string.launcher_break_hm, min / 60, min % 60) else context.getString(R.string.launcher_break_m, min)
}

/**
 * Araç sağlığı (eski Akü + Bakım + sağlık kutucukları tek yerde): akü gerilimi, motor suyu, yakıt uyarısı ve bakım aralığı
 * ([HealthMath]). Her şey yolundaysa sakin "Her şey normal" ve bakıma kalan mesafe; bir sorun varsa en önemlisi
 * uyarı renginde adıyla görünür. Yalnızca eldeki veriden türetilir, araca bir şey göndermez.
 * 2×2: özet; 4×4: sağda akü, motor ve bakım satırları (sorunlu satır kırmızı).
 */
internal class HealthTile(context: Context) :
    TripCard(context, R.drawable.ic_mdi_lightning_bolt, R.string.launcher_widget_health) {

    private fun number(n: Int) = java.text.NumberFormat.getIntegerInstance(java.util.Locale.getDefault()).format(n)

    private fun label(issue: HealthMath.Issue) = context.getString(when (issue) {
        HealthMath.Issue.ENGINE_HOT -> R.string.launcher_health_hot
        HealthMath.Issue.BATTERY_LOW -> R.string.launcher_health_battery_low
        HealthMath.Issue.BATTERY_HIGH -> R.string.launcher_health_battery_high
        HealthMath.Issue.SERVICE_OVERDUE -> R.string.launcher_health_service_overdue
        HealthMath.Issue.LOW_FUEL -> R.string.launcher_health_low_fuel
        HealthMath.Issue.SERVICE_SOON -> R.string.launcher_health_service_soon
    })

    /** Sorunun değeri (ölçüm), yoksa null: akü "11,4 V", bakım kalan mesafe/gün. */
    private fun detail(issue: HealthMath.Issue): String? = when (issue) {
        HealthMath.Issue.BATTERY_LOW, HealthMath.Issue.BATTERY_HIGH ->
            data.batteryV?.let { context.getString(R.string.launcher_health_volt_only, one(it)) }
        HealthMath.Issue.ENGINE_HOT -> null
        HealthMath.Issue.SERVICE_OVERDUE, HealthMath.Issue.SERVICE_SOON -> serviceText()
        HealthMath.Issue.LOW_FUEL -> null
    }

    /** "3.978 km" ya da yalnız gün biliniyorsa "155 gün". */
    private fun serviceText(): String? = data.serviceKm?.let { context.getString(R.string.launcher_km_value, number(it)) }
        ?: data.serviceDays?.let { context.getString(R.string.launcher_days_value, it) }

    override fun bind() {
        val d = data
        if (!d.connected || !HealthMath.hasData(d.batteryV, d.coolantCode, d.lowFuel, d.serviceKm, d.serviceDays)) {
            setIcon(R.drawable.ic_mdi_lightning_bolt, LauncherTheme.TEXT_SECONDARY)
            showMissing()
            return
        }
        val issues = HealthMath.issues(d.batteryV, d.lowFuel, d.serviceKm, d.serviceDays, d.batteryHighHeld)
        val bad = issues.isNotEmpty()
        setIcon(if (bad) R.drawable.ic_mdi_alert else R.drawable.ic_mdi_lightning_bolt,
            if (bad) LauncherTheme.ALERT else LauncherTheme.TEXT_SECONDARY)
        val headline = if (bad) label(issues.first()) else context.getString(R.string.launcher_health_all_ok)
        setValue(headline, if (bad) LauncherTheme.ALERT else LauncherTheme.TEXT, if (headline.length > LONG_TEXT) STATUS_LONG_SP else STATUS_SP)
        // Sorunluyken ölçüm (+ başka sorun sayısı); sorunsuzken bakıma kalan mesafe.
        val sub = if (bad) listOfNotNull(detail(issues.first()), issues.size.takeIf { it > 1 }?.let { "+${it - 1}" }).joinToString(" · ")
        else serviceText()?.let { context.getString(R.string.launcher_health_service_in, it) }
        setSubtitle(sub?.ifEmpty { null }, if (bad) LauncherTheme.ALERT else LauncherTheme.TEXT_SECONDARY)

        val rows = mutableListOf<Pair<String?, String>>()
        val alert = mutableSetOf<Int>()
        fun add(label: String, value: String?, isAlert: Boolean) {
            if (value == null) return
            if (isAlert) alert += rows.size
            rows += label to value
        }
        add(context.getString(R.string.launcher_health_row_battery),
            d.batteryV?.let { context.getString(R.string.launcher_health_volt_only, one(it)) },
            HealthMath.Issue.BATTERY_LOW in issues || HealthMath.Issue.BATTERY_HIGH in issues)
        add(context.getString(R.string.launcher_health_row_engine),
            if (CoolantStatus.isNormal(d.coolantCode)) context.getString(R.string.launcher_coolant_normal) else null, false)
        val serviceBad = HealthMath.Issue.SERVICE_OVERDUE in issues || HealthMath.Issue.SERVICE_SOON in issues
        add(context.getString(R.string.launcher_health_row_service),
            d.serviceKm?.let { context.getString(R.string.launcher_km_value, number(it)) }, serviceBad)
        if (large) add(context.getString(R.string.launcher_health_row_service_days),
            d.serviceDays?.let { context.getString(R.string.launcher_days_value, it) }, serviceBad)
        showRows(rows, alert)
        describe(context.getString(R.string.launcher_widget_health), headline, sub, *rows.map { "${it.first} ${it.second}" }.toTypedArray())
    }

    private companion object {
        const val STATUS_SP = 32f
        /** Uzun başlıklar ("Bakım yaklaşıyor") 2×2'de bu boyda tek satıra sığar. */
        const val STATUS_LONG_SP = 28f
        const val LONG_TEXT = 14
    }
}

/**
 * ReadOnly kimlikleri: FrostNav'ın `ReadOnlyID` jar'ında bulunmayan, yeni Desay yazılımının eklediği sinyaller. Eski yazılımlı araçta okunamaz: bütün çağıranlar null'u kaldırır.
 */
internal object ReadOnlyIds {
    const val TIRE_PRESSURE = 140
    const val TOTAL_MILEAGE = 150
    const val DRIVING_TIME_AFTER_RUNNING = 151
    const val DRIVING_TIME_AFTER_RESET = 152
    const val TOTAL_FUEL = 153
}
