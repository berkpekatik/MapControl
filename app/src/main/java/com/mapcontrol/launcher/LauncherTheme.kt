package com.mapcontrol.launcher

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import com.mapcontrol.vehicle.VehicleLightSensorManager

/**
 * Etkinlik yeniden kurulmadan tema değişimini (gündüz/gece, sürüş vurgusu) uygulayabilen widget/görünüm.
 * [applyTheme] LauncherTheme değerleri güncellendikten sonra çağrılır; renkleri ve arka planları yeniden atar.
 * Bunu uygulamayan widget'lar tema değişince tek tek yeniden kurulur (bkz. HomeLauncherActivity.applyThemeInPlace).
 */
interface Themeable {
    fun applyTheme()
}

/**
 * Temaya bağlı atamaların tek kaydı: [bind] bloğu hemen çalıştırır ve saklar, [reapply] hepsini LauncherTheme'in
 * o anki değerleriyle yeniden çalıştırır. Kurucu ile tema değişimi aynı kodu kullandığı için ikisi birbirinden sapmaz.
 * Bloklar görünüm kurulumunda sabit kalan atamalar içindir (arka plan, metin/ikon rengi, boya, yükselti);
 * veriye bağlı renkler widget'ın kendi bağlama (bind) işlevinden yeniden atanır.
 */
class ThemeBindings {
    private val blocks = ArrayList<() -> Unit>()

    fun bind(block: () -> Unit) {
        blocks += block
        block()
    }

    fun reapply() {
        for (b in blocks) b()
    }
}

/**
 * Yeni launcher'ın tasarım değerleri (gündüz ve gece görünümü).
 * Tek vurgu rengi var: [ACCENT] yalnızca birincil/aktif öğede, [ALERT] yalnızca uyarıda (her zaman ikonla).
 */
object LauncherTheme {
    /** Gündüz/gece seçimi; [applyFrom] ile ayarlanır. Okunmadan önce etkinlik başında çağrılmalı. */
    @JvmStatic
    var night: Boolean = true

    /**
     * Uygulanan sürüş modu ([DriveAccent]); yalnızca vurgu rengini etkiler. Etkinlik yeniden kurulsa da
     * korunur (süreç boyunca); kullanıcı seçeneği kapalıysa etkinlik başında Normal'e çekilir.
     */
    @JvmStatic
    @Volatile
    var driveMode: Int = DriveAccent.NORMAL

    private const val NIGHT_ALERT = 0xFFEF4444.toInt()
    private const val DAY_ALERT = 0xFFDC2626.toInt()
    private const val NIGHT_ALERT_SURFACE = 0xFF3A1F22.toInt()
    private const val DAY_ALERT_SURFACE = 0xFFFDE8E8.toInt()

    // Jetonlar Sahne paletinden ([Sahne]) gelir; başlık, alt çubuk ve panellerdeki widget'lar ayrı kod olmadan Sahne dilindedir.
    val GROUND: Int get() = Sahne.GROUND
    val CARD: Int get() = if (night) 0xFF13171E.toInt() else 0xFFF9F8F7.toInt()
    /** Açılır panellerde (Wi-Fi, Bluetooth) hap düğmesi zemini: panel zemininden belirgin açık (gece CONTROL panelle neredeyse aynıydı). */
    val PANEL_PILL: Int get() = if (night) 0xFF2E3542.toInt() else CONTROL
    val CONTROL: Int get() = if (night) 0xFF1D2128.toInt() else 0xFFEDECEA.toInt()
    /** Kart kenar çizgisi rengi; gecede 0 (çizgi yok). */
    val CARD_BORDER: Int get() = if (night) 0 else 0x17181E28
    val TRACK: Int get() = if (night) 0xFF2C2E33.toInt() else 0xFFD3D3D2.toInt()
    val ACCENT: Int get() = DriveAccent.accentFor(driveMode, night) ?: if (night) 0xFF4C82D0.toInt() else 0xFF1D5FC4.toInt()
    val ALERT: Int get() = if (night) NIGHT_ALERT else DAY_ALERT
    /** Isıtma anlamı: sürüş moduna bağlı değil, yalnızca gündüz/gece değişir. */
    val HEAT: Int get() = Sahne.AMBER
    /** Soğutma/havalandırma anlamı (bkz. [HEAT]). */
    val COOL: Int get() = Sahne.COOL
    val TEXT: Int get() = if (night) 0xFFEEF2F7.toInt() else 0xFF111419.toInt()
    /** Gecede ikincil yazı [Sahne.INK_3] ile aynı çelik mavisi (aracın alt çubuğuyla uyumlu). */
    val TEXT_SECONDARY: Int get() = if (night) Sahne.INK_3 else 0xFF4D4F53.toInt()

    /** Alt çubuk üstündeki ikincil yazı: gecede eski ikincil tonda (%72 açık mürekkep); çelik mavisi yalnızca başlık ve şeritte. */
    val DOCK_TEXT_SECONDARY: Int get() = if (night) 0xB8E4ECF6.toInt() else Sahne.INK_2
    /** Basış örtüsü: gece %12 beyaz, gündüz %8 koyu. */
    val DOCK_PRESS: Int get() = if (night) 0x1FFFFFFF else (DAY_PRESS_INK and 0x00FFFFFF) or 0x14000000
    /** Klima sürükleme tonları: serin mavi, sıcak turuncu, fan mavisi; gündüz açık yüzeyde biraz daha doygun. */
    val DOCK_TINT_COOL: Int get() = if (night) 0x403B82F6 else 0x382563EB
    val DOCK_TINT_WARM: Int get() = if (night) 0x3DFB923C else 0x40F97316
    val DOCK_TINT_FAN: Int get() = if (night) 0x333B82F6 else 0x2E2563EB
    private const val DAY_PRESS_INK = 0xFF111827.toInt()

    /** Vurgu rengi üstündeki yazı/ikon; iki görünümde de beyaz. */
    val ON_ACCENT: Int get() = 0xFFFFFFFF.toInt()

    /** Uyarı durumundaki kart zemini (ör. lastik uyarısı). */
    val ALERT_SURFACE: Int get() = if (night) NIGHT_ALERT_SURFACE else DAY_ALERT_SURFACE

    /**
     * Görünümü seçer. [ThemeMode.AUTO] sistemin gündüz/gece durumunu izler; bilinmeyen durum gece sayılır
     * (araçta güvenli varsayılan). Diğer iki seçenek sistemi yok sayar.
     */
    fun applyFrom(config: Configuration, mode: ThemeMode = ThemeMode.AUTO) {
        night = when (mode) {
            ThemeMode.DAY -> false
            ThemeMode.NIGHT -> true
            // Araç verisinden son uygulanan otomatik durum varsa o; yoksa (emülatör, ilk açılış) sistem.
            ThemeMode.AUTO -> lastAutoNight
                ?: ((config.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_NO)
        }
    }

    /** Otomatik modda son uygulanan gündüz/gece (araç verisi); yeniden kurulumda korunur. */
    @Volatile
    private var lastAutoNight: Boolean? = null

    private const val AUTO_STABLE_MS = 10_000L
    private const val AUTO_TICK_MS = 2_000L
    /** Gerçek bir ışık sensörü bu değerin altını okuduysa karanlık (kestirim en az 800 lx verir). */
    private const val DARK_LUX = 200f

    private var autoRunner: Runnable? = null
    private var autoListener: VehicleLightSensorManager.Listener? = null
    private var autoManager: VehicleLightSensorManager? = null
    private val autoHandler = Handler(Looper.getMainLooper())
    private val autoPolicy = AutoNightPolicy(AUTO_STABLE_MS)

    /**
     * Otomatik temayı aracın farlarına/ışık sensörüne bağlar. onResume'da çağrılır, onPause'da [stopAutoNight].
     * [canSwitch] false iken (ör. düzenleme modu, ayarlar açık) geçiş ertelenir; [onSwitch] tema değişince
     * çağrılır (etkinlik `ThemeTransition.recreate` yapmalı). Yalnızca ThemeMode.AUTO iken kurulur; mod değişince yeniden çağrılmalıdır (bkz. HomeLauncherActivity.syncAutoNight).
     */
    @JvmStatic
    fun startAutoNight(context: Context, canSwitch: () -> Boolean, onSwitch: () -> Unit) {
        stopAutoNight()
        val app = context.applicationContext
        // Gündüz/Gece sabitken sensör dinleyicisi ve 2 sn'lik döngü hiç kurulmaz; mod değişince çağıran yeniden başlatır.
        if (ThemeMode.read(app) != ThemeMode.AUTO) return
        val manager = try { VehicleLightSensorManager.getInstance(app) } catch (t: Throwable) { null }
        val listener = VehicleLightSensorManager.Listener { _, _, _ -> }
        manager?.addListener(listener)
        autoManager = manager
        autoListener = listener
        autoPolicy.reset(night)
        val runner = object : Runnable {
            override fun run() {
                if (ThemeMode.read(app) == ThemeMode.AUTO && manager != null && manager.hasVehicleLampData()) {
                    val dark = manager.isParkingLampActive ||
                        manager.effectiveLux < DARK_LUX
                    val switchTo = autoPolicy.update(System.currentTimeMillis(), dark, canSwitch())
                    if (switchTo != null) {
                        night = switchTo
                        lastAutoNight = switchTo
                        onSwitch()
                    }
                }
                // Geçiş sırasında yeniden başlatıldıysa (syncAutoNight) bu eski döngü kendini yeniden kurmaz.
                if (autoRunner === this) autoHandler.postDelayed(this, AUTO_TICK_MS)
            }
        }
        autoRunner = runner
        autoHandler.postDelayed(runner, AUTO_TICK_MS)
    }

    @JvmStatic
    fun stopAutoNight() {
        autoRunner?.let { autoHandler.removeCallbacks(it) }
        autoRunner = null
        autoListener?.let { autoManager?.removeListener(it) }
        autoListener = null
        autoManager = null
    }

    const val CARD_RADIUS_DP = 20f
    /** Boşluk ölçeği: bitişik öğeler S, grup arası M, bölüm arası L. */
    const val GAP_S_DP = 8
    const val GAP_SM_DP = 12
    const val GAP_M_DP = 16
    const val GAP_L_DP = 24

    /** İkon ölçeği: satır içi / başlık / büyük. */
    const val ICON_S_DP = 24
    const val ICON_M_DP = 32
    const val ICON_L_DP = 56

    /** Kart iç boşluğu: büyük kartlar 32, kutucuklar 24, sıkışık/şerit 16. */
    const val PADDING_DP = 32
    const val CARD_PAD_DP = 24
    const val CARD_PAD_COMPACT_DP = 16
    const val BORDER_DP = 1

    /** Sürüşte kullanılan dokunma hedefleri en az bu boyutta. */
    const val TOUCH_MIN_DP = 72
    const val MEDIA_BUTTON_DP = 88
    const val PLAY_BUTTON_DP = 104

    const val HEADER_HEIGHT_DP = 72
    const val STATUS_HEIGHT_DP = 40

    /** Saat, sıcaklık gibi rakamlar sp cinsinden; etiketler küçük, rakamlar büyük. */
    const val TIME_SP = 56f
    const val TITLE_SP = 52f
    const val TEMP_SP = 44f
    const val SECTION_SP = 36f
    const val BODY_SP = 24f
    /** Sürüşte okunur alt sınır; etiketler bunun altına inmez. */
    const val LABEL_SP = 22f
    /** "Araç verisi bekleniyor" notu: tüm küçük kartlarda aynı boyut (bkz. CenteredTile, TripTiles, AwdTile). */
    const val WAITING_SP = LABEL_SP

    const val PRESS_SCALE = 0.98f
    const val PRESS_DURATION_MS = 120L
    const val CROSSFADE_MS = 150L
}

/**
 * Otomatik gece/gündüz için saf histerezis: aday durum [stableMs] boyunca değişmeden kalırsa ve geçişe izin
 * varsa yeni değeri döndürür (yoksa null). Aday değişirse ya da geçerli duruma dönerse sayaç sıfırlanır.
 */
class AutoNightPolicy(private val stableMs: Long) {
    private var applied = true
    private var candidate: Boolean? = null
    private var since = 0L

    fun reset(current: Boolean) {
        applied = current
        candidate = null
    }

    fun update(now: Long, desired: Boolean, canSwitch: Boolean): Boolean? {
        if (desired == applied) {
            candidate = null
            return null
        }
        if (candidate != desired) {
            candidate = desired
            since = now
        }
        if (now - since < stableMs || !canSwitch) return null
        applied = desired
        candidate = null
        return desired
    }
}
