package com.mapcontrol.launcher

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

/**
 * Sürüş ekranı: Navigasyon kartına (ya da "Haritayı aç", Ev / İş) dokununca ekran ikiye bölünür, bir yarıda Mini gösterge, diğerinde
 * seçili harita (Tiggo 7 sol/sağ, Tiggo 8 üst/alt; bölme MapControl'ın kendi başlatıcısıyla, [MapControlFeatures.startDriveScreen]).
 * Harita seçilmemişse harita yarısında "+ Harita ekle" seçicisi açılır.
 *
 * Tek ekran gibi görünsün diye: yarıların arkasında kalan ana ekranımız, sürüş ekranı açıkken kartlarını gizleyip yalnız Sahne zeminini
 * gösterir ([attachLauncher]; durum çubuğu şeridinde ve yarıların kenarında kartlar görünmez). Ana ekran yeniden öne gelince (Mini
 * göstergenin "Tam ekran"ı, Ana ekran tuşu, boşluğa dokunuş) sürüş ekranı biter: zemin kalkar, kendi yarılarımızın görevleri kapatılır.
 *
 * Mini göstergedeki harita düğmesi haritayı tam ekran yapar ([mapFullScreen]). Sürüş ekranı sürerken (tam ekran harita dahil) Ana ekran
 * tuşu bizim ana ekranımıza döner ([active] -> [HomePolicy.decide] `driveActive`; sürüş ekranı dışında üçüncü taraf uygulamalardaki tuşa
 * dokunulmaz). Ana ekran öne gelince oturum biter; sistemin varsayılan HOME ayarına dokunulmaz.
 *
 * Durum süreç içindedir (ana ekran, Mini gösterge ve seçici aynı süreçte); süreç ölürse sürüş ekranı kapalı sayılır.
 */
internal object DriveScreen {

    private const val TAG = "DriveScreen"

    /** Kendi etkinliklerimize (Mini gösterge, harita seçici): sürüş ekranının yarısı olarak açıldı. */
    const val EXTRA_DRIVE = "com.mapcontrol.launcher.extra.DRIVE"

    private const val WINDOWING_MODE_FULLSCREEN = 1

    private val main = Handler(Looper.getMainLooper())

    @Volatile private var phase = DrivePhase.IDLE
    /** Harita yarısındaki paket (seçici açıksa ve henüz seçilmediyse null). */
    @Volatile var mapPackage: String? = null
        private set
    /** Harita bu oturumda tam ekran yapıldı ([mapFullScreen]); değilse oturum bitince harita yarıda serbest pencere kalır. */
    @Volatile private var mapFull = false
    /** Oturum bitince (ana ekran öne gelince) sürüş ekranı yeniden açılsın: zemindeki harita yarısına dokunuldu ([reopenFromBackdrop]). */
    @Volatile private var reopenPending = false
    private var appContext: Context? = null

    private var backdrop: ((Boolean) -> Unit)? = null
    private val openTimeout = Runnable { onEvent(DriveEvent.TIMEOUT, null) }

    /** Ana ekran sürüş ekranının arkasında zemin gösteriyor mu. */
    val backdropShown: Boolean get() = DrivePolicy.backdrop(phase)

    /** Sürüş ekranı açık ya da açılıyor mu. */
    val active: Boolean get() = phase != DrivePhase.IDLE

    /** Ana ekran (onCreate) zemini göstermek / gizlemek için bağlanır; [show] hemen o anki durumla çağrılır. */
    fun attachLauncher(show: (Boolean) -> Unit) {
        backdrop = show
        show(backdropShown)
    }

    fun detachLauncher(show: (Boolean) -> Unit) {
        if (backdrop === show) backdrop = null
    }

    /**
     * Navigasyon kartından sürüş ekranını açar. MapControl yoksa ([MapControlFeatures.NONE]) ya da bölme başlatılamazsa [fallback] (eski davranış:
     * harita tam ekran; Ev / İş'te rota intent'i). [route]: Ev / İş rotası (yalnız [mapPkg] verilmişse; harita yarısı onunla açılır).
     * Bölme başarısızsa ve harita varsa ([DrivePolicy.fallbackOnFailure]) yine [fallback]: dokunuş boşa gitmez; yarıda açılmış Mini
     * gösterge oturumla birlikte kapanır ([DrivePolicy.next]).
     */
    fun open(context: Context, mapPkg: String?, route: Intent?, fallback: () -> Unit) {
        val features = LauncherEdition.mapcontrol(context)
        if (!features.available) {
            fallback()
            return
        }
        if (phase == DrivePhase.OPENING) return
        appContext = context.applicationContext
        mapPackage = mapPkg
        mapFull = false
        // Harita yeniden yarısına yerleşir; önceki oturumdan kalan "yarıda kaldı" işareti geçersiz.
        DriveMapPrefs.clearLeftover(context)
        // Oturum işareti: süreç oturum açıkken ölürse (uygulama güncellemesi) açılışta harita görevi geri alınır ([recoverStale]).
        if (!mapPkg.isNullOrBlank()) DriveMapPrefs.markSession(context, mapPkg)
        onEvent(DriveEvent.TAP, context)
        Log.i(TAG, "open map=${mapPkg ?: "picker"} route=${route != null}")
        features.startDriveScreen(mapPkg, route) { error ->
            if (error == null) return@startDriveScreen
            Log.w(TAG, "open failed")
            onEvent(DriveEvent.START_FAILED, context)
            // Oturum bitişinden (yarıda kalan harita işareti, Mini yarısının kapanması) sonra: aynı kuyrukta sırayla.
            if (DrivePolicy.fallbackOnFailure(mapPkg)) main.post(fallback)
            else Toast.makeText(context.applicationContext, error, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Zeminin harita yarısına dokunuldu (harita başlıktaki X ile kapatılmış, yarı boş): sürüş ekranı yeniden açılır (harita yoksa
     * seçiciyle). Dokunuş ana ekranı öne getirdiği için oturum önce biter; açık oturumdaysa bitişte açılır ([onEvent]).
     */
    fun reopenFromBackdrop(context: Context) {
        if (phase == DrivePhase.OPEN) {
            reopenPending = true
            return
        }
        if (phase == DrivePhase.IDLE) openDefault(context)
    }

    /** Navigasyon kartının harita seçimiyle sürüş ekranının harita yarısı ([DrivePolicy.mapSlot]); null = seçici. */
    fun defaultMap(context: Context): String? {
        val installed = { pkg: String ->
            try { context.packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }
        }
        return DrivePolicy.mapSlot(MapAppPrefs.current(context)?.packageName, DriveMapPrefs.other(context), installed)
    }

    /**
     * Sürüş ekranını kartın harita seçimiyle açar ([defaultMap]); bölme olmazsa harita tam ekran. Zemine dokunuş ve "Bölünmüş ekran"
     * kutusu (şerit / panel; Mini gösterge + harita çifti) bunu kullanır: tek bölünmüş ekran deneyimi.
     */
    fun openDefault(context: Context) {
        val pkg = defaultMap(context)
        open(context, pkg, null) {
            if (pkg != null) openFullScreen(context, pkg)
        }
    }

    /**
     * Ana ekran kısayolları (Haritalar karosu, alt çubuk, Navigasyon kartı yedeği): [pkg] sürüş ekranından yarıda serbest pencere
     * kaldıysa tam ekrana taşınarak açılır ve true döner; değilse false (çağıran her zamanki gibi açar). Yoksa Android açık görevi
     * ana ekranın üstünde yarım pencere olarak öne getirirdi.
     */
    fun openLeftover(context: Context, pkg: String): Boolean {
        if (!DriveMapPrefs.takeLeftover(context, pkg)) return false
        val features = LauncherEdition.mapcontrol(context)
        if (!features.available) return false
        Log.i(TAG, "leftover map -> full")
        features.openFullScreen(pkg)
        return true
    }

    /**
     * Süreç başlangıcında (arka plan iş parçacığı, korumalı): önceki süreç sürüş ekranı açıkken öldüyse (ör. uygulama güncellemesi)
     * harita görevi yarım serbest pencere olarak ana ekranın üstünde kalır; oturum durumu kayboldu, kimse toplamaz. İşaret önce silinir
     * (döngü yok), sonra görev tüm kullanılabilir alana büyütülür; uygulama kapatılmaz, görev yoksa hiçbir şey yapılmaz.
     */
    @JvmName("recoverStale")
    fun recoverStale(context: Context) {
        val app = context.applicationContext
        val pkg = DrivePolicy.staleToRestore(DriveMapPrefs.sessionPkg(app) ?: DriveMapPrefs.placedPkg(app)) ?: return
        DriveMapPrefs.clearSession(app)
        DriveMapPrefs.clearPlaced(app)
        Thread {
            try {
                Log.i(TAG, "stale drive session -> restore map")
                LauncherEdition.mapcontrol(app).restoreStaleMap(pkg)
            } catch (t: Throwable) {
                Log.w(TAG, "stale restore failed: ${t.javaClass.simpleName}")
            }
        }.start()
    }

    /**
     * Ana ekran görünür oldu (`onResume`): sürüş oturumu yokken ([DrivePhase.IDLE]) yarıya yerleştirdiğimiz harita görevi (oturum
     * ya da yerleştirme işareti; önceki süreçten de kalır) hâlâ ana ekranın üstünde yarım pencere kalmış sayılır ve tüm alana
     * büyütülür ([recoverStale] ile aynı yol). Kısa gecikmeyle (normal bitiş önce işareti siler), en çok bir kez (işaret silinir);
     * uygulama başlatılmaz / kapatılmaz.
     */
    fun onLauncherResumed(context: Context) {
        val app = context.applicationContext
        main.removeCallbacks(resumeCheck)
        resumeCheckContext = app
        main.postDelayed(resumeCheck, DrivePolicy.RESUME_DEBOUNCE_MS)
    }

    private var resumeCheckContext: Context? = null
    private val resumeCheck = Runnable {
        val app = resumeCheckContext ?: return@Runnable
        val pkg = DrivePolicy.restoreOnLauncherResume(phase, DriveMapPrefs.sessionPkg(app), DriveMapPrefs.placedPkg(app)) ?: return@Runnable
        DriveMapPrefs.clearSession(app)
        DriveMapPrefs.clearPlaced(app)
        Thread {
            try {
                Log.i(TAG, "launcher resumed with half map -> restore")
                LauncherEdition.mapcontrol(app).restoreStaleMap(pkg)
            } catch (t: Throwable) {
                Log.w(TAG, "resume restore failed: ${t.javaClass.simpleName}")
            }
        }.start()
    }

    /** Harita tam ekran: yarıda kalmışsa taşınır, değilse başlatıcıyla açılır (Navigasyon kartı "Haritayı aç" da bunu kullanır). */
    internal fun openFullScreen(context: Context, pkg: String) {
        if (openLeftover(context, pkg)) return
        LauncherEdition.mapcontrol(context).openFullScreen(pkg)
    }

    /** Ana ekran en üstteki etkin pencere oldu / olmaktan çıktı (`onTopResumedActivityChanged`). */
    fun onLauncherTop(context: Context, top: Boolean) {
        onEvent(if (top) DriveEvent.LAUNCHER_TOP_GAINED else DriveEvent.LAUNCHER_TOP_LOST, context)
    }

    /** Harita seçiciden seçim yapıldı: Mini göstergenin harita düğmesi bu paketi tam ekran yapar. */
    fun onMapChosen(pkg: String) {
        mapPackage = pkg
    }

    /**
     * Mini göstergenin harita düğmesi: harita tam ekran olur, sonra [done] (ör. Mini gösterge kendini kapatır). Sürüş ekranı açık kalır:
     * Ana ekran tuşu yine bizim ana ekranımıza döner.
     */
    fun mapFullScreen(context: Context, done: (Boolean) -> Unit) {
        val pkg = mapPackage ?: return done(false)
        LauncherEdition.mapcontrol(context).mapFullScreen(pkg) { ok ->
            Log.i(TAG, "map full -> $ok")
            if (ok) { mapFull = true; appContext?.let { DriveMapPrefs.clearSession(it); DriveMapPrefs.clearPlaced(it) } }
            done(ok)
        }
    }

    /**
     * Bizim ana ekranımız tam ekran (pencere modu 1) öne gelir; HOME rolü kapalıyken de açıkça adlandırılır. Ana ekran tuşu
     * yönlendirmesi ve açılış da bu yolu kullanır ([HomeKey]).
     */
    fun launchHome(context: Context) {
        try {
            val intent = Intent(context, HomeLauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            val opts = ActivityOptions.makeBasic().toBundle() ?: Bundle()
            // ActivityOptions.setLaunchWindowingMode (gizli API) ile aynı anahtarlar; 1 = WINDOWING_MODE_FULLSCREEN.
            opts.putInt("android.activity.windowingMode", WINDOWING_MODE_FULLSCREEN)
            opts.putInt("android:windowingMode", WINDOWING_MODE_FULLSCREEN)
            context.startActivity(intent, opts)
        } catch (e: RuntimeException) {
            Log.w(TAG, "launcher could not be opened: ${e.javaClass.simpleName}")
        }
    }

    private fun onEvent(event: DriveEvent, context: Context?) {
        main.post {
            val before = phase
            val after = DrivePolicy.next(before, event)
            if (after == before) return@post
            phase = after
            Log.i(TAG, "$before --$event--> $after")
            when (after) {
                DrivePhase.OPENING -> {
                    main.removeCallbacks(openTimeout)
                    main.postDelayed(openTimeout, DrivePolicy.OPEN_TIMEOUT_MS)
                }
                DrivePhase.OPEN -> main.removeCallbacks(openTimeout)
                DrivePhase.IDLE -> {
                    main.removeCallbacks(openTimeout)
                    val ctx = context ?: appContext
                    // Harita tam ekran yapılmadıysa görevi yarıda serbest pencere kaldı: sonraki kısayol açılışı onu tam ekrana taşır.
                    val map = mapPackage
                    if (ctx != null && map != null && !mapFull) DriveMapPrefs.markLeftover(ctx, map)
                    // Oturum normal bitti: süreç ölümü işareti silinir.
                    if (ctx != null) { DriveMapPrefs.clearSession(ctx); DriveMapPrefs.clearPlaced(ctx) }
                    mapPackage = null
                    // Bitiş: kendi yarılarımız (Mini gösterge, seçici) kapanır; harita görevi tam ekran ana ekranın arkasında kalır.
                    // Açılış yarıda kaldıysa da (Mini açıldı, harita açılamadı) tek başına Mini yarısı kalmasın.
                    if (ctx != null) LauncherEdition.mapcontrol(ctx).endDriveScreen()
                    if (reopenPending && ctx != null) {
                        reopenPending = false
                        openDefault(ctx)
                    }
                }
            }
            backdrop?.invoke(DrivePolicy.backdrop(after))
        }
    }
}

internal enum class DrivePhase { IDLE, OPENING, OPEN }

internal enum class DriveEvent { TAP, START_FAILED, TIMEOUT, LAUNCHER_TOP_LOST, LAUNCHER_TOP_GAINED }

/** Sürüş ekranının saf kuralları (Android'e bağlı değil; JVM'de sınanır). */
internal object DrivePolicy {

    /** Dokunuştan sonra ana ekran bu sürede öndeki yerini yarılara bırakmadıysa açılış başarısız sayılır (zemin kalkar). */
    const val OPEN_TIMEOUT_MS = 8_000L

    /**
     * Durum geçişleri. Dokunuş açılışı başlatır; ana ekran öndeki yerini kaybedince (yarılar açıldı) sürüş ekranı açık sayılır; açıkken
     * ana ekran yeniden öne gelince biter. Açılırken ana ekranın öne gelmesi (dokunuşun kendisi) açılışı bozmaz.
     */
    fun next(phase: DrivePhase, event: DriveEvent): DrivePhase = when (event) {
        DriveEvent.TAP -> if (phase == DrivePhase.IDLE) DrivePhase.OPENING else phase
        // Açılış başarısızsa açık sayılmış oturum da biter (Mini yarısı açılıp harita açılamadıysa): kendi yarımız kapanır.
        DriveEvent.START_FAILED -> DrivePhase.IDLE
        DriveEvent.TIMEOUT -> if (phase == DrivePhase.OPENING) DrivePhase.IDLE else phase
        DriveEvent.LAUNCHER_TOP_LOST -> if (phase == DrivePhase.OPENING) DrivePhase.OPEN else phase
        DriveEvent.LAUNCHER_TOP_GAINED -> if (phase == DrivePhase.OPEN) DrivePhase.IDLE else phase
    }

    /** Açılışta geri alınacak harita paketi: oturum işareti doluysa o (boş / boşluk = yok). */
    fun staleToRestore(sessionPkg: String?): String? = sessionPkg?.trim()?.takeIf { it.isNotEmpty() }

    /** Ana ekran görünür olduktan sonra kontrol bu kadar bekler (normal bitişin işareti silmesi, çift onResume için). */
    const val RESUME_DEBOUNCE_MS = 1_500L

    /**
     * Ana ekran göründüğünde geri alınacak harita paketi: yalnız sürüş oturumu yokken ([DrivePhase.IDLE]); oturum işareti, yoksa
     * yerleştirme işareti (boş / boşluk = yok). Açılırken / açıkken hiçbir şey yapılmaz.
     */
    fun restoreOnLauncherResume(phase: DrivePhase, sessionPkg: String?, placedPkg: String?): String? {
        if (phase != DrivePhase.IDLE) return null
        return staleToRestore(sessionPkg) ?: staleToRestore(placedPkg)
    }

    /** Bölme başlatılamadı: harita varsa yedek (tam ekran harita / rota) açılır; yoksa (seçici) yalnız neden gösterilir. */
    fun fallbackOnFailure(mapPkg: String?): Boolean = !mapPkg.isNullOrBlank()

    /** Ana ekran kartlarını gizleyip zemin gösterir mi (açılırken ve açıkken). */
    fun backdrop(phase: DrivePhase): Boolean = phase != DrivePhase.IDLE

    /**
     * Harita yarısı: kartın paketi kuruluysa o; değilse seçicide seçilmiş başka navigasyon uygulaması kuruluysa o; ikisi de yoksa null
     * ("+ Harita ekle" seçicisi).
     */
    fun mapSlot(cardPkg: String?, otherPkg: String?, installed: (String) -> Boolean): String? {
        cardPkg?.trim()?.takeIf { it.isNotEmpty() && installed(it) }?.let { return it }
        return otherPkg?.trim()?.takeIf { it.isNotEmpty() && installed(it) }
    }

    /**
     * Seçicideki uygulamalar: kurulu harita seçenekleri ([MapApp.ORDER] sırasıyla), ardından `geo:` açabilen diğer uygulamalar (adına
     * göre; kendimiz ve yinelenenler hariç).
     */
    fun pickerChoices(mapApps: List<String>, geoApps: List<Pair<String, String>>, selfPkg: String): List<String> {
        val out = LinkedHashSet<String>()
        mapApps.filter { it != selfPkg }.forEach { out.add(it) }
        geoApps.filter { it.first != selfPkg && it.first !in out }.sortedBy { it.second.lowercase() }.forEach { out.add(it.first) }
        return out.toList()
    }
}

/** Seçicide seçilen, [MapApp] dışındaki navigasyon uygulaması (profil bağımsız, Ev / İş ile aynı tercih dosyası). */
internal object DriveMapPrefs {
    private const val PREFS = "launcher_nav"
    private const val KEY_OTHER = "drive_map_other"

    private const val KEY_LEFTOVER = "drive_map_leftover"
    private const val KEY_SESSION = "drive_session_map"

    /** Sürüş ekranı açıldı: harita paketi kalıcı işaretlenir (süreç ölse de kalır). */
    fun markSession(context: Context, pkg: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SESSION, pkg).apply()
    }

    fun clearSession(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_SESSION).apply()
    }

    fun sessionPkg(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SESSION, null)

    /** Sürüş ekranının yarısına yerleştirdiğimiz harita ([com.mapcontrol.util.SplitScreenLauncher] yazar, aynı dosya / anahtar). */
    private const val KEY_PLACED = "drive_placed_map"

    fun placedPkg(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PLACED, null)

    fun clearPlaced(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_PLACED).apply()
    }

    fun other(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_OTHER, null)

    /** Sürüş ekranı bitti, [pkg] yarıda serbest pencere kaldı (süreç ölse de görev kalır; kalıcı). */
    fun markLeftover(context: Context, pkg: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LEFTOVER, pkg).apply()
    }

    fun clearLeftover(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LEFTOVER).apply()
    }

    /** [pkg] yarıda kalmış harita mı; öyleyse işaret silinir (bir kez tam ekrana taşınır). */
    fun takeLeftover(context: Context, pkg: String): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LEFTOVER, null) != pkg) return false
        prefs.edit().remove(KEY_LEFTOVER).apply()
        return true
    }

    /** [pkg] bir [MapApp] ise kartın seçimi olur; değilse "diğer" olarak saklanır. */
    fun choose(context: Context, pkg: String) {
        val app = MapApp.fromPackage(pkg)
        val e = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (app != null) {
            MapAppPrefs.set(context, app)
            e.remove(KEY_OTHER)
        } else {
            e.putString(KEY_OTHER, pkg)
        }
        e.apply()
    }

    /** `geo:` açabilen uygulamalar (paket, ad); seçiciye "diğer navigasyon" adayları. */
    fun geoApps(context: Context): List<Pair<String, String>> = try {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=0,0")), 0)
            .mapNotNull { ri -> ri.activityInfo?.let { it.packageName to ri.loadLabel(pm).toString() } }
            .distinctBy { it.first }
    } catch (e: RuntimeException) {
        emptyList()
    }
}
