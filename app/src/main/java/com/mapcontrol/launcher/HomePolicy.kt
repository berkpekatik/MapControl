package com.mapcontrol.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Ana ekran tuşu ve açılış ekranı (FrostNav launcher modu açıkken; kapalıyken hiçbir şey değişmez). Kural bilerek dar tutuldu:
 *
 * 1. Araç / uygulama açılınca FrostNav bizim ana ekranımızda ([HomeLauncherActivity]) açılır, FrostNav ayarlarında değil.
 * 2. FrostNav'ın kendi ekranları öndeyken (ayarlar sekmeleri, harita seçici, kamera, Mini gösterge…; bizim ana ekranımız hariç)
 *    aracın Ana ekran tuşu bizim ana ekranımıza getirir.
 * 3. Sürüş ekranı ([DriveScreen]) sürerken yarılardan ya da tam ekran haritadan basılan Ana ekran tuşu da bizim ana ekranımıza getirir.
 * 4. Başka her durumda araç kendi bildiği gibi davranır: üçüncü taraf uygulamadan (Google Haritalar, müzik…) ya da bizim ana
 *    ekranımızdan basılan Ana ekran tuşu aracın kendi ana ekranına (OEM, `com.desaysv.launcher`) çıkar; biz geri çekmeyiz.
 *
 * Sistemin varsayılan HOME ayarına dokunulmaz: tuş her zaman OEM ana ekranını açar, erişilebilirlik hizmeti ([HomeKey.onWindowStateChanged])
 * OEM ana ekranının öne geldiğini görür ve ondan önce öndeki uygulamaya göre ([HomePolicy.decide]) bizim ana ekranımızı öne getirir ya
 * da bırakır. Önceki uygulama bilinmiyorsa (hizmet yeni bağlandı, ön plan okunamadı) hiçbir şey yapılmaz.
 * Kurallar saf koddur (JVM'de sınanır); [HomeKeyTracker] olay sırasını tutar, [HomeKey] Android'e bağlar.
 */
internal enum class HomeAction {
    /** Bu olayla ilgili bir şey yapılmaz. */
    NONE,

    /** Ana ekran tuşu FrostNav ekranında (ya da sürüş ekranında) basıldı: bizim ana ekranımız öne gelir. */
    REDIRECT_TO_LAUNCHER,

    /** Ana ekran tuşu bizim ana ekranımızdayken basıldı: aracın kendi ana ekranında kalınır (yalnız günlük için ayrı). */
    STAY_ON_OEM,
}

/** OEM ana ekranı öne gelmeden önce öndeki uygulamanın türü. */
internal enum class FrontApp {
    /** Bilinmiyor (hizmet yeni bağlandı ya da ön plan okunamadı): hiçbir şey yapılmaz. */
    UNKNOWN,

    /** Bizim ana ekranımız ([HomeLauncherActivity]). */
    OUR_LAUNCHER,

    /** FrostNav'ın kendi başka bir ekranı (ayarlar, harita seçici, kamera, Mini gösterge…). */
    OUR_SCREEN,

    /** Aracın kendi ana ekranı. */
    CAR_HOME,

    /** Başka bir uygulama (Google Haritalar, müzik, telefon…). */
    OTHER_APP,
}

/** Uygulama açılışında gösterilecek ekran. */
internal enum class StartScreen { LAUNCHER, SETTINGS }

/** Ana ekran tuşu ve açılış ekranının saf kuralları (Android'e bağlı değil). */
internal object HomePolicy {

    /** İki yönlendirme arası en kısa süre (OEM ana ekranı art arda olay üretirse tek açılış). */
    const val REDIRECT_DEBOUNCE_MS = 1_000L

    /**
     * Öndeki uygulama sayılmayan geçici pencereler: durum çubuğu / bildirim paneli, sistem iletişim kutuları ve OEM klima paneli
     * (`com.desaysv.svhvac`, servisin çizdiği katman; [ClimatePanel]). Bunlar açılıp kapanınca "son uygulama" değişmez.
     */
    val TRANSIENT_PACKAGES: Set<String> = setOf("com.android.systemui", "android", ClimatePanel.PACKAGE)

    /** Öne gelen paket aracın kendi (OEM) ana ekranı mı: sistem varsayılan HOME paketi ve biz değil. */
    fun isOemHome(pkg: String?, oemHomePkg: String?, selfPkg: String): Boolean =
        !pkg.isNullOrEmpty() && !oemHomePkg.isNullOrEmpty() && oemHomePkg != selfPkg && pkg == oemHomePkg

    /**
     * Pencere olayı "öndeki uygulama" değişimi sayılmaz mı: geçici sistem pencereleri, klavye (`...SoftInputWindow`; klavye kendi
     * paketinden olay gönderir, FrostNav ayarlarında yazarken Ana ekran tuşu yine bize dönmeli) ya da kendi paketimizin etkinlik
     * olmayan pencereleri (yüzen geri oku, gösterge katmanları; sınıf adları `...Activity` ile bitmez).
     */
    fun isTransient(pkg: String, className: String?, selfPkg: String): Boolean {
        if (pkg in TRANSIENT_PACKAGES) return true
        if (className != null && className.endsWith("SoftInputWindow")) return true
        return pkg == selfPkg && (className == null || !className.endsWith("Activity"))
    }

    /** Öne gelen (geçici olmayan) pencerenin türü. */
    fun classify(pkg: String, className: String?, selfPkg: String, launcherClass: String, oemHomePkg: String?): FrontApp = when {
        isOemHome(pkg, oemHomePkg, selfPkg) -> FrontApp.CAR_HOME
        pkg == selfPkg -> if (className == launcherClass) FrontApp.OUR_LAUNCHER else FrontApp.OUR_SCREEN
        else -> FrontApp.OTHER_APP
    }

    /**
     * OEM ana ekranı öne geldi: ne yapılır.
     *
     * @param launcherModeOn FrostNav launcher modu ([com.mapcontrol.util.LauncherModeManager.isEnabled]); kapalıysa hiç dokunulmaz.
     * @param before OEM ana ekranından hemen önce öndeki uygulama.
     * @param driveActive sürüş ekranı açık ya da açılıyor ([DriveScreen.active]): yarılardaki / tam ekran haritadaki tuş da bize döner.
     * @param sinceLastRedirectMs son yönlendirmeden bu yana geçen süre (yakınsa OEM'in art arda olayı: hiçbir şey).
     */
    fun decide(launcherModeOn: Boolean, before: FrontApp, driveActive: Boolean, sinceLastRedirectMs: Long): HomeAction {
        if (!launcherModeOn) return HomeAction.NONE
        if (sinceLastRedirectMs in 0 until REDIRECT_DEBOUNCE_MS) return HomeAction.NONE
        return when (before) {
            FrontApp.OUR_LAUNCHER -> HomeAction.STAY_ON_OEM
            FrontApp.OUR_SCREEN -> HomeAction.REDIRECT_TO_LAUNCHER
            FrontApp.OTHER_APP -> if (driveActive) HomeAction.REDIRECT_TO_LAUNCHER else HomeAction.NONE
            FrontApp.CAR_HOME, FrontApp.UNKNOWN -> HomeAction.NONE
        }
    }

    /** Araç açılışında (MapControlService) gösterilecek ekran: launcher modu açıksa bizim ana ekranımız. */
    fun bootScreen(launcherModeOn: Boolean): StartScreen = if (launcherModeOn) StartScreen.LAUNCHER else StartScreen.SETTINGS

    /**
     * FrostNav simgesinden açılış (MAIN + LAUNCHER, kendine özgü istek yok, yeniden kurulum değil): launcher modu açıksa bizim ana
     * ekranımız açılır. Ayarlar düğmesi, sekme / seçici istekleri ve kurulum sonuçları ayarlara gider.
     */
    fun appStartScreen(launcherModeOn: Boolean, iconLaunch: Boolean, hasOwnRequest: Boolean, restored: Boolean): StartScreen =
        if (launcherModeOn && iconLaunch && !hasOwnRequest && !restored) StartScreen.LAUNCHER else StartScreen.SETTINGS
}

/**
 * Ana ekran tuşu olay sırası (saf; JVM'de sınanır). Ana ekranımızın öne gelme / öndeki yerini bırakma anlarını
 * ([onLauncherTop], `onTopResumedActivityChanged`) ve erişilebilirlik pencere olaylarını ([onWindow]) birleştirip OEM ana ekranından
 * önce öndeki uygulamayı ([FrontApp]) tutar.
 */
internal class HomeKeyTracker(private val selfPkg: String, private val launcherClass: String) {

    private var launcherTop = false
    private var front = FrontApp.UNKNOWN
    private var lastRedirectAt = 0L

    /** Ana ekranımız en üstteki etkin pencere oldu / olmaktan çıktı. */
    fun onLauncherTop(top: Boolean) {
        launcherTop = top
        if (top) front = FrontApp.OUR_LAUNCHER
    }

    /**
     * Erişilebilirlik hizmeti bağlandı: öndeki uygulama varsayılmaz, gerçekten okunan pakettir ([pkg]; okunamadıysa null = bilinmiyor).
     * Kendi paketimizse ana ekranımızın şu an en üstte olup olmadığına bakılır (süreç içi bilgi).
     */
    fun onServiceConnected(pkg: String?, oemHomePkg: String?) {
        front = when {
            pkg.isNullOrEmpty() || pkg in HomePolicy.TRANSIENT_PACKAGES -> FrontApp.UNKNOWN
            pkg == selfPkg -> if (launcherTop) FrontApp.OUR_LAUNCHER else FrontApp.OUR_SCREEN
            else -> HomePolicy.classify(pkg, null, selfPkg, launcherClass, oemHomePkg)
        }
    }

    /** Erişilebilirlik hizmeti kapandı: aradaki olaylar kaçar, öndeki uygulama bilinmez olur. */
    fun onServiceDisconnected() {
        front = FrontApp.UNKNOWN
    }

    /**
     * Öne gelen pencere ([pkg], [className]); [oemHomePkg]: sistemin varsayılan ana ekranı. [appWindow] false: pencere listesinde
     * uygulama penceresi değil (klavye, ses çubuğu gibi başka paketlerin açılır pencereleri, sistem katmanları); öndeki uygulama
     * değişmez. OEM ana ekranının kendi olayı bu yüzden hiç atlanmaz.
     */
    fun onWindow(
        pkg: String?,
        className: String?,
        nowMs: Long,
        oemHomePkg: String?,
        launcherModeOn: Boolean,
        driveActive: Boolean = false,
        appWindow: Boolean = true,
    ): HomeAction {
        if (pkg.isNullOrEmpty() || HomePolicy.isTransient(pkg, className, selfPkg)) return HomeAction.NONE
        val kind = HomePolicy.classify(pkg, className, selfPkg, launcherClass, oemHomePkg)
        if (kind != FrontApp.CAR_HOME) {
            if (!appWindow) return HomeAction.NONE
            front = kind
            return HomeAction.NONE
        }
        // Tuşa basıldığında ana ekranımız hâlâ en üstte görünüyorsa tuş onda basılmıştır.
        val before = if (launcherTop) FrontApp.OUR_LAUNCHER else front
        val sinceRedirect = if (lastRedirectAt == 0L) Long.MAX_VALUE else nowMs - lastRedirectAt
        val action = HomePolicy.decide(launcherModeOn, before, driveActive, sinceRedirect)
        front = FrontApp.CAR_HOME
        if (action == HomeAction.REDIRECT_TO_LAUNCHER) lastRedirectAt = nowMs
        return action
    }
}

/**
 * Ana ekran tuşu kuralının Android tarafı (tek süreç: erişilebilirlik hizmeti ve etkinlikler aynı süreçte). Yönlendirme bizim ana
 * ekranımızı tam ekran açar ([DriveScreen.launchHome]); sürüş ekranı sürerken basılan Ana ekran tuşu da bu yoldan gelir.
 */
object HomeKey {

    private const val TAG = "HomeKey"

    /** Sistem varsayılan ana ekranı en çok bu kadar süre önbellekte tutulur (olaylar sık gelir; PackageManager sorgusu Binder). */
    private const val HOME_CACHE_MS = 60_000L

    private var tracker: HomeKeyTracker? = null
    @Volatile private var oemHome: String? = null
    @Volatile private var oemHomeAt = 0L

    private fun tracker(context: Context): HomeKeyTracker =
        tracker ?: HomeKeyTracker(context.packageName, HomeLauncherActivity::class.java.name).also { tracker = it }

    /** Ana ekranımız en üstteki etkin pencere oldu / olmaktan çıktı (`onTopResumedActivityChanged`, ana iş parçacığı). */
    @JvmStatic
    fun onLauncherTop(context: Context, top: Boolean) {
        tracker(context).onLauncherTop(top)
    }

    /**
     * Erişilebilirlik hizmetinden (ana iş parçacığı): `TYPE_WINDOW_STATE_CHANGED` olayının paketi, sınıfı ve pencere kimliği.
     * Başka paketin penceresi (bizim ve OEM ana ekranı dışında) yalnız gerçek uygulama penceresiyse öndeki uygulama sayılır
     * ([isAppWindow]); klavye ve başka açılır pencereler FrostNav ekranındaki Ana ekran tuşunu bozmaz.
     */
    @JvmStatic
    fun onWindowStateChanged(service: AccessibilityService, pkg: CharSequence?, className: CharSequence?, windowId: Int) {
        val name = pkg?.toString() ?: return
        val modeOn = LauncherEdition.launcherModeOn(service)
        val oem = oemHomePackage(service)
        val appWindow = name == service.packageName || name == oem || isAppWindow(service, windowId)
        val action = tracker(service).onWindow(
            name, className?.toString(), SystemClock.uptimeMillis(), oem, modeOn, DriveScreen.active, appWindow,
        )
        when (action) {
            HomeAction.REDIRECT_TO_LAUNCHER -> {
                Log.i(TAG, "home key -> launcher")
                DriveScreen.launchHome(service)
            }
            HomeAction.STAY_ON_OEM -> Log.i(TAG, "home key on launcher -> car home")
            HomeAction.NONE -> Unit
        }
    }

    /**
     * Erişilebilirlik hizmeti bağlanınca (ana iş parçacığı): öndeki uygulama varsayılmaz, hizmetin pencere listesinden okunur
     * ([foregroundPackage]). Araçta hizmet Google Haritalar öndeyken bağlanınca eski kayıt "ana ekranımız öndeydi" sanıyordu.
     */
    @JvmStatic
    fun onServiceConnected(service: AccessibilityService) {
        val pkg = foregroundPackage(service)
        Log.i(TAG, "service connected, front=$pkg")
        tracker(service).onServiceConnected(pkg, oemHomePackage(service))
    }

    /** Erişilebilirlik hizmeti kapanınca: öndeki uygulama bilinmez olur (aradaki olaylar kaçar). */
    @JvmStatic
    fun onServiceDisconnected(context: Context) {
        tracker(context).onServiceDisconnected()
    }

    /**
     * Pencere listesinde [windowId] uygulama penceresi mi. Bulunamazsa (liste okunamadı, pencere kapandı, kimlik yok) uygulama
     * penceresi sayılır: önceki davranış korunur.
     */
    private fun isAppWindow(service: AccessibilityService, windowId: Int): Boolean {
        if (windowId < 0) return true
        return try {
            val win = service.windows.firstOrNull { it.id == windowId } ?: return true
            win.type == AccessibilityWindowInfo.TYPE_APPLICATION
        } catch (e: RuntimeException) {
            true
        }
    }

    /**
     * Öndeki uygulamanın paketi: etkin / odaklı uygulama penceresi (`flagRetrieveInteractiveWindows` gerekir), yoksa etkin pencerenin
     * kökü. Okunamazsa null (bilinmiyor; kural hiçbir şey yapmaz).
     */
    private fun foregroundPackage(service: AccessibilityService): String? {
        try {
            val apps = service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            val win = apps.firstOrNull { it.isActive } ?: apps.firstOrNull { it.isFocused }
            win?.root?.packageName?.toString()?.let { return it }
        } catch (e: RuntimeException) {
            Log.w(TAG, "windows unavailable: ${e.javaClass.simpleName}")
        }
        return try {
            service.rootInActiveWindow?.packageName?.toString()
        } catch (e: RuntimeException) {
            null
        }
    }

    /**
     * FrostNav simgesinden açılış ([com.mapcontrol.ui.activity.MainActivity.onCreate]): kural ([HomePolicy.appStartScreen]) ana ekranı
     * gösteriyorsa onu açar ve true döner (çağıran kendini kapatır).
     */
    @JvmStatic
    fun openLauncherOnAppStart(context: Context, intent: Intent?, hasOwnRequest: Boolean, restored: Boolean): Boolean {
        val icon = intent != null && Intent.ACTION_MAIN == intent.action && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        val screen = HomePolicy.appStartScreen(LauncherEdition.launcherModeOn(context), icon, hasOwnRequest, restored)
        if (screen != StartScreen.LAUNCHER) return false
        Log.i(TAG, "app start -> launcher")
        DriveScreen.launchHome(context)
        return true
    }

    /** Araç açılışında açılacak ekran ana ekranımız mı ([HomePolicy.bootScreen]). */
    @JvmStatic
    fun bootOpensLauncher(context: Context): Boolean =
        HomePolicy.bootScreen(LauncherEdition.launcherModeOn(context)) == StartScreen.LAUNCHER

    /** Ana ekranımızı tam ekran öne getirir ([DriveScreen.launchHome]). */
    @JvmStatic
    fun openLauncher(context: Context) = DriveScreen.launchHome(context)

    /**
     * Aracın kendi ana ekranı: sistem varsayılan HOME paketi; çözülemezse (seçici "android" ya da biz) bizden başka ilk HOME adayı.
     */
    private fun oemHomePackage(context: Context): String? {
        val now = SystemClock.uptimeMillis()
        val cached = oemHome
        if (cached != null && now - oemHomeAt < HOME_CACHE_MS) return cached
        val self = context.packageName
        val pm = context.packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = try {
            pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        } catch (e: RuntimeException) {
            null
        }
        // Varsayılan HOME bizsek (cihaz sahibi sabitlemesi) tuş zaten bize gelir: yönlendirilecek OEM ana ekranı yok.
        val pkg = if (resolved == self) null else resolved?.takeIf { it != "android" } ?: try {
            pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { it.activityInfo?.packageName }
                .firstOrNull { it != self && it != "android" }
        } catch (e: RuntimeException) {
            null
        }
        oemHome = pkg
        oemHomeAt = now
        return pkg
    }
}
