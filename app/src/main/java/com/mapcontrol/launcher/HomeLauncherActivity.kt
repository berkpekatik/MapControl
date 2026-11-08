package com.mapcontrol.launcher

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.mapcontrol.R
import com.mapcontrol.vehicle.VehicleMetricsRepository
import com.mapcontrol.vehicle.VehicleMetricsSnapshot

/**
 * Yeni ana ekran (HOME). MapControl'ün geri kalanına "Ayarlar" düğmesinden girilir.
 *
 * Yerleşim (yukarıdan aşağı): üst şerit 72dp (düzenleme modunda düzenleme çubuğu) · Sahne orta alanı ([SahneScreen]:
 * iki panel, ortada araç, altta şerit) · launcher'ın kendi alt çubuğu ([OurBottomBar]; OEM çubuğu olan araçta "Orijinal"
 * seçiliyken yok). Tüm widget'lar yalnızca ekran görünürken çalışır.
 */
class HomeLauncherActivity : Activity() {

    private val metrics by lazy { VehicleMetricsRepository.getInstance(applicationContext) }
    private val settings by lazy { LauncherSettings(this, onEditHome = ::enterEditMode) }
    private val metricsListener = VehicleMetricsRepository.Listener { snap ->
        // Sürüş başlarsa (>5 km/s) düzenleme modu kaydedilip kapanır.
        if (snap.connected && snap.hasDriveMode()) observedDriveMode = snap.driveMode
        // Hareket: gösterge hızı > 0 (okunamıyorsa bilinmiyor; sayaçlar sıfırlanır).
        observedPowerOn = WelcomeGate.powerOn(snap)
        observedMoving = if (snap.connected && (VehicleMetricsSnapshot.isValid(snap.displaySpeed) || VehicleMetricsSnapshot.isValid(snap.actualSpeed))) snap.preferredSpeed() > 0 else null
        // Vites P mi (ReadOnly 26: P=1); okunamıyorsa bilinmiyor.
        observedInPark = if (snap.connected && VehicleMetricsSnapshot.isValid(snap.gearSignal)) snap.gearSignal == 1 else null
        window.decorView.post {
            if (editing && LauncherSafeArea.isMoving(snap, EDIT_EXIT_KMH)) exitEditMode()
        }
    }

    /** Aracın bildirdiği son sürüş modu (veri yoksa null); tema vurgusu bundan [DRIVE_STABLE_MS] sonra uyarlanır. */
    @Volatile private var observedDriveMode: Int? = null
    @Volatile private var observedMoving: Boolean? = null
    @Volatile private var observedInPark: Boolean? = null
    /** Güç modu açık mı (kontak; bilinmiyorsa null): karşılama (§7.4) bundan karar verir. */
    @Volatile private var observedPowerOn: Boolean? = null
    /** Sahne karşılaması; etkinlik başına bir kez kurulur. */
    private var welcome: SahneWelcome? = null
    /** Soğuk açılışta karşılama için ekran siyah tutuldu; bu ana kadar güç sinyali gelmezse kısa solmayla açılır. */
    private var welcomeArmUntil = 0L

    /** Park/Sürüş düzeni: etkin düzen, geçiş kararı ve başlıktaki düzenleme başlığı. */
    private var activeMode = LayoutMode.PARK
    private var wifiPanel: WifiPanel? = null
    private var bluetoothPanel: BluetoothPanel? = null
    private val layoutPolicy = DriveLayoutPolicy()
    /** Kartların "sürüşte" durumu ([DriveMotion]): düzen seçeneği kapalıyken de ve düzenlerken de işler. */
    private val motionPolicy = DriveLayoutPolicy()
    /** Tercihler her turda SharedPreferences'tan okunmasın diye alanlarda tutulur; onCreate/onStart/ayar değişiminde tazelenir. */
    private var driveAccentOn = true
    private var autoLayoutOn = DriveLayoutPrefs.DEFAULT_ON
    /** Son onStop zamanı (0: görünür); dönüşte bayat sürüş durumunu temizlemek için. */
    private var stoppedAt = 0L
    private lateinit var editHelper: android.widget.TextView
    private val driveModePolicy = DriveModePolicy(DRIVE_STABLE_MS)
    private val driveHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val driveTick = object : Runnable {
        override fun run() {
            if (started) { driveModeTick(); layoutTick(); welcomeTick() }
            driveHandler.postDelayed(this, DRIVE_TICK_MS)
        }
    }

    private lateinit var header: HeaderBar
    private lateinit var editBar: View
    /** Orta alan: Sahne (iki panel, ortada araç, altta şerit). */
    private lateinit var sahne: SahneScreen
    /** Düzenleme modu açık mı. */
    private val editing: Boolean get() = ::sahne.isInitialized && sahne.editMode
    private val oemBar by lazy { LauncherSafeArea.hasOemBar(this) }
    private lateinit var fixedWidgets: List<LauncherWidget>
    /** Tema değişiminde yeniden kurulan sabit parçalar (bkz. [applyThemeInPlace]). */
    private var ourBar: OurBottomBar? = null
    /** Orta alan ile [ourBar] arasındaki boşluk; çubukla birlikte gizlenir. */
    private var ourBarGap: View? = null
    /**
     * Sistem OEM çubuğunu zorla gösteriyor (FrostNav bölünmüş ekranı/serbest pencere açık): bizim çubuğumuz gizlenir, alt kenar
     * OEM çubuğunundur; bölme bitince geri gelir. Bkz. [BottomBarMode.oemBarForced].
     */
    private var oemBarForced = false
    private val applyOemBarForced = Runnable { setOemBarForced(true) }
    /** Gelen/aktif arama şeridi (üst orta, tüm içeriğin üstünde); sabit widget'larla aynı ömür. */
    private lateinit var callBanner: CallBanner
    /** Tam ekran arama sayfası (içerik FrameLayout'una eklenen örtü; karar §15). */
    private lateinit var callScreen: CallScreen
    /** Etkinlik ömrü boyunca arama durumunu izler: arama başlayınca launcher'ı öne getirir ("Arama ekranı: Launcher"). */
    private val callWatcher = object : PhoneListener {
        override fun onPhoneChanged(snapshot: PhoneSnapshot) = onCallWatch(snapshot.call.kind)
    }
    private var callWatching = false
    private var watchKind = CallKind.NONE
    /** Bu aramada öne getirmenin yapıldığı geçişler (aynı geçiş tekrarlanmaz). */
    private val broughtFor = HashSet<CallKind>()
    /** Kullanıcı bu aramada arama sayfasını küçülttü: kendiliğinden yeniden açılmaz (şerit kalır). */
    private var callMinimized = false
    /** Varsayılan ana ekran biz miyiz (PackageManager Binder'ı arka planda, onStart'ta tazelenir). */
    @Volatile private var defaultHome = false
    private val bringTask = Runnable { bringToFrontForCall() }
    private var rootLayout: View? = null
    /** Sürüş ekranının arkasındaki düz zemin ([DriveScreen]); açılırken belirir, ana ekran öne gelince kalkar. */
    private var driveBackdrop: View? = null
    /** Zeminin harita yarısındaki "Haritayı aç" ipucu: harita penceresinin arkasında kalır, harita kapatılınca görünür. */
    private var driveMapHint: View? = null
    private val showDriveMapHint = Runnable { driveMapHint?.animate()?.alpha(1f)?.setDuration(DRIVE_FADE_MS)?.start() }
    private val onDriveBackdrop: (Boolean) -> Unit = { show -> showDriveBackdrop(show) }

    private var started = false
    private var resumed = false
    /** Kurulumda/tema uygulanırken geçerli olan tercih özeti ([HomeScreenPrefs.signature]). */
    private var appliedSignature = ""
    /** Öne gelirken tercih değiştiği için yeniden kurulma bekleniyor; bu kopya düzenleme moduna girmez. */
    private var recreating = false
    private var sheet: android.app.Dialog? = null
    /** `--ez launcher_data_check true` ile açılan Veri kontrolü paneli (yalnızca debug). */
    private var dataCheckExtra: DataCheckSheet? = null

    override fun attachBaseContext(newBase: android.content.Context) {
        // Farklı ekranlarda (ör. Tiggo 8) yerleşim 1920x720 tasarımına yakın bir dp genişliğine getirilir.
        super.attachBaseContext(LauncherScale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Çökme döngüsü bekçisi: 60 sn içinde iki ya da 10 dk içinde üç çökmede uygulama HOME'u aracın launcher'ına geri verir.
        LauncherCrashGuard.onLauncherStart(this)
        if (LauncherCrashGuard.isDebuggable(this) && intent?.getBooleanExtra(LauncherCrashGuard.EXTRA_DEBUG_CRASH, false) == true) {
            throw RuntimeException("launcher_debug_crash (yalnızca debug)")
        }
        window.decorView.postDelayed({ LauncherCrashGuard.onHealthy(this) }, LauncherCrashGuard.HEALTHY_MS)
        LauncherEdition.init(applicationContext)
        LauncherInfo.logOnce(this)
        LauncherDemo.applyFrom(this, intent)
        // Orta alan kurulmadan önce: düzen deposu ve alt çubuk biçimi etkin araç profiline göre.
        VehicleProfile.resolve(this)
        LauncherDemo.applyPreview(intent, restored = savedInstanceState != null)
        LauncherDemo.applyMoving(intent)
        // Debug: telefon panelinde asistan düğmesi (yeniden kurulumda eski istek ayarın üstüne yazmasın).
        if (savedInstanceState == null) PhonePrefs.applyShowAssistantExtra(this, intent)
        dataCheckExtra = DataCheckSheet.applyFrom(this, intent)
        LauncherSafeArea.applyFrom(this, intent)
        LauncherBottomBar.applyFrom(this, intent)
        LauncherTheme.applyFrom(resources.configuration, ThemeMode.read(this))
        // Sürüş modu vurgusu: seçenek kapalıysa varsayılan vurgu; demo isteği ilk kurulumda hemen uygulanır.
        refreshDrivePrefs()
        if (!driveAccentOn) LauncherTheme.driveMode = DriveAccent.NORMAL
        else LauncherDemo.applyDriveMode(intent)?.let { LauncherTheme.driveMode = it }
        driveModePolicy.reset(LauncherTheme.driveMode)
        // Tema geçişiyle yeniden kurulunca etkin düzen korunur (statik); otomatik seçenek kapalıysa Park.
        // Seçenek kapalıyken hatırlanan düzen de Park'a çekilir: yeniden açılınca bir an Sürüş düzeni görünmesin.
        if (!autoLayoutOn) rememberedMode = LayoutMode.PARK
        activeMode = rememberedMode
        LauncherMotion.driveLayout = activeMode == LayoutMode.DRIVE
        layoutPolicy.reset(activeMode)
        // Yeniden kurulumda (tema) sürüş durumu korunur: kart 5 sn yolculuğa dönüp geri gelmesin.
        motionPolicy.reset(if (DriveMotion.driving) LayoutMode.DRIVE else LayoutMode.PARK)
        LauncherInfo.mode = activeMode
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) { consumeAppearanceExtra(intent); consumeEditExtra(intent) }
        window.decorView.setBackgroundColor(LauncherTheme.GROUND)
        hideSystemBars()

        header = HeaderBar(this, onSettings = ::openSettings, onEditHome = ::enterEditMode, onProfile = ::openProfiles, onWifi = ::toggleWifiPanel, onBluetooth = ::toggleBluetoothPanel)
        // "Alt çubuk: Bizimki" seçiliyse OEM çubuğu gizlenir ve yerine OurBottomBar gelir; OEM çubuğu yokken "Orijinal" de
        // bizim çubuğa düşer. "Gizli"de çubuk yok.
        ourBar = if (BottomBarMode.drawsOurBar(LauncherBottomBar.mode(this), oemBar)) OurBottomBar(this, onPhoneTap = ::onPhoneButton) else null
        val ourBar = this.ourBar
        // Kompakt arama hapı başlıktaki saatin sağına yerleşir (saati örtmez); konumları başlıktan okur.
        callBanner = CallBanner(this, onIncoming = ::onIncomingCall, onOpen = { openCallScreen(fromUser = true) }, header = header)
        fixedWidgets = listOfNotNull(header, ourBar, callBanner)
        editBar = buildEditBar()
        sahne = newSahneScreen()

        val root = LinearLayout(this).apply {
            rootLayout = this
            orientation = LinearLayout.VERTICAL
            // Taslak ölçüleri (başlık 12dp'den, orta alan 108dp'den başlar); cam gölgeleri taşabilsin diye kırpma yok.
            background = SahneGround()
            clipChildren = false
            clipToPadding = false
            setPadding(dp(24), dp(12), dp(24), dp(20))

            addView(FrameLayout(context).apply {
                clipChildren = false
                addView(header.view)
                addView(editBar)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(LauncherTheme.HEADER_HEIGHT_DP)))
            // Sahne ritmi: başlık / orta alan / şerit / alt çubuk arası hep 16dp.
            addView(gap(context, 16, vertical = true))
            addView(sahne, weightLp(1f, vertical = true))
            if (ourBar != null) {
                addView(gap(context, 16, vertical = true).also { ourBarGap = it })
                addView(ourBar.view, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(OurBottomBar.heightDp(this@HomeLauncherActivity))))
            }

            // OEM alt çubuğu üstüne çizilir; alt çubuk ve orta alan onun üstünde kalsın. Bizimki'nde en altta
            // dar bir boşluk kalır: alttan kaydırma hareketi (OEM çubuğunu geçici çağırır) çubuğumuza takılmaz.
            // Sistem OEM çubuğunu zorla açınca (bölünmüş ekran) bizimki gizlenir ve alt pay OEM çubuğunun inset'i olur.
            val baseBottom = { dp(if (ourBar != null && !oemBarForced) OurBottomBar.BOTTOM_GAP_DP else 20) }
            setPadding(paddingLeft, paddingTop, paddingRight, maxOf(baseBottom(), LauncherSafeArea.oemInsetPx(context)))
            setOnApplyWindowInsetsListener { v, insets ->
                onOemBarForced(LauncherSafeArea.oemBarForced(context, insets))
                val bottom = maxOf(baseBottom(), LauncherSafeArea.bottomInsetPx(context, insets, oemBarForced))
                if (v.paddingBottom != bottom) v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bottom)
                insets
            }
        }
        // İçerik her zaman bir FrameLayout: kök düzen, sürüş ekranının zemini ve en üstte arama şeridi.
        // Kök düzene bağlı her şey (rootLayout, tema geçişi, karşılama, iç boşluk dinleyicisi) `root`'u kullanır, içerik görünümünü değil.
        setContentView(FrameLayout(this).apply {
            clipChildren = false
            addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            // Sürüş ekranı açıkken yarıların arkasında yalnız zemin görünür (kartlar durum çubuğu şeridinden ve kenarlardan sızmaz).
            // Harita başlıktaki X ile kapatılırsa yarısı boş kalmasın: orada "Haritayı aç" görünür, dokununca sürüş ekranı yeniden açılır.
            addView(FrameLayout(this@HomeLauncherActivity).apply {
                background = SahneGround()
                visibility = View.GONE
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                driveBackdrop = this
                addView(LinearLayout(this@HomeLauncherActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    alpha = 0f
                    addView(android.widget.ImageView(this@HomeLauncherActivity).apply {
                        setImageResource(R.drawable.ic_sahne_plus)
                        setTint(Sahne.INK_3)
                    }, LinearLayout.LayoutParams(dp(56), dp(56)))
                    addView(sahneText(this@HomeLauncherActivity, 28f, Sahne.INK_2, 500).apply {
                        setText(R.string.launcher_drive_reopen_map)
                    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(12)
                    })
                    driveMapHint = this
                }, FrameLayout.LayoutParams(0, 0))
                setOnTouchListener { v, e ->
                    if (e.actionMasked == android.view.MotionEvent.ACTION_UP && inDriveMapSlot(v, e.rawX, e.rawY)) {
                        DriveScreen.reopenFromBackdrop(this@HomeLauncherActivity)
                    }
                    true
                }
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(callBanner.view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = dp(CALL_BANNER_TOP_DP) })
            // Arama sayfası açılınca en üste eklenir; açıkken şerit gizlenir (süre iki yerde birden sayılmaz).
            callScreen = CallScreen(this@HomeLauncherActivity, this, ::onCallScreenChanged)
        })
        DriveScreen.attachLauncher(onDriveBackdrop)
        syncCallWatcher()
        if (savedInstanceState == null) consumeShowCall(intent)
        loadForMode()
        setupWelcome(root)
        LauncherStartup.begin(window.decorView)
        ThemeTransition.playIn(this)
        LauncherBottomBar.logResult(this)
        appliedSignature = HomeScreenPrefs.signature(this)
        if (pendingEdit) openPendingEdit()
        // Favori tuşu: etkinlik ömrü boyunca dinlenir (HOME etkinliği başka uygulama öndeyken de yaşar, yalnızca durur).
        FavoriteKey.sync(this)
        // Klima tuşlarına kilit: süreç boyunca olay güdümlü dinler (önde başka uygulama varken de).
        ClimatePanelGuard.start(this)
        // Ses (VR) tuşu uzun basış → bağlı telefonun asistanı: ayara göre, aynı etkinlik ömrüyle.
        PhoneSource.syncVoiceKey(this)
        DrawTrace.applyFrom(this, intent)
    }

    /** Çalışırken gelen yeni istek (singleTask): demo sürüş modu değiştirilebilir (`--ei launcher_demo_drive_mode N`). */
    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (LauncherCrashGuard.isDebuggable(this) && intent?.getBooleanExtra(LauncherCrashGuard.EXTRA_DEBUG_CRASH, false) == true) {
            throw RuntimeException("launcher_debug_crash (yalnızca debug)")
        }
        consumeAppearanceExtra(intent)
        consumeEditExtra(intent)
        consumeShowCall(intent)
        PhonePrefs.applyShowAssistantExtra(this, intent)
        LauncherDemo.applyDriveMode(intent)
        LauncherDemo.applyMoving(intent)
        LauncherDemo.applyPreview(intent)
        // Demo: çalışırken araç profili değişirse (satır sayısı değişir) yeniden kurulur.
        if (LauncherDemo.enabled && intent?.hasExtra(VehicleProfile.EXTRA_PROFILE) == true) {
            VehicleProfile.demoOverride = VehicleProfile.parse(intent.getStringExtra(VehicleProfile.EXTRA_PROFILE))
            if (VehicleProfile.compute(this) != VehicleProfile.active) ThemeTransition.recreate(this)
        }
    }

    /** FrostNav Ayarlar'daki "Ana ekran görünümü" bağlantısı: [EXTRA_OPEN_APPEARANCE] ile gelince Görünüm panelini açar. */
    private fun consumeAppearanceExtra(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_APPEARANCE, false) != true) return
        intent.removeExtra(EXTRA_OPEN_APPEARANCE)
        window.decorView.post { if (!isFinishing && !isDestroyed) settings.show(appearanceOnly = true) }
    }

    /**
     * FrostNav "Ana ekran" sekmesindeki "Ana ekranı düzenle": [HomeScreenPrefs.EXTRA_OPEN_EDIT] ile gelince düzenleme moduna girer.
     * İstek [pendingEdit] ile tutulur: öne gelirken tercih değişikliği yüzünden etkinlik yeniden kurulursa yeni kopya girer.
     */
    private fun consumeEditExtra(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(HomeScreenPrefs.EXTRA_OPEN_EDIT, false) != true) return
        intent.removeExtra(HomeScreenPrefs.EXTRA_OPEN_EDIT)
        pendingEdit = true
        openPendingEdit()
    }

    private fun openPendingEdit() {
        window.decorView.post {
            if (isFinishing || isDestroyed || !::sahne.isInitialized || recreating || !pendingEdit) return@post
            pendingEdit = false
            enterEditMode()
        }
    }

    /** Bir sonraki yerinde tema yenilemesi düzenleme modunu korusun mu ([rethemeKeepingEdit]). */
    private var keepEditOnRetheme = false

    /** Görünüm panelinden tema seçimi: düzenleme açıksa açık kalır ve panel yeni temayla yeniden gösterilir. */
    internal fun rethemeKeepingEdit() {
        keepEditOnRetheme = editing
        ThemeTransition.retheme(this)
    }

    /**
     * Tema değişimini etkinliği yeniden yaratmadan uygular ([ThemeTransition.retheme] üstteki anlık görüntü örtüsünün
     * altında çağırır). Jetonlar güncellenir; başlık yerinde yenilenir, alt çubuk ve Sahne orta alanı yeniden kurulur
     * (çalışan işleri durdurulup yenisi başlatılır). Açık sayfa kapanır, düzenleme modundan çıkılır.
     */
    internal fun applyThemeInPlace() {
        if (isFinishing || isDestroyed || !::sahne.isInitialized) return
        // Görünüm panelinden tema değişimi düzenleme modunu bozmaz (gece/gündüz karşılaştırılabilsin); panel yeniden açılır.
        val keepEdit = keepEditOnRetheme && editing
        keepEditOnRetheme = false
        sheet?.dismiss()
        sheet = null
        wifiPanel?.dismiss()
        bluetoothPanel?.dismiss()
        if (!keepEdit) exitEditMode()
        // Karşılama eski görünümlere bağlı: önce son haline çekilir (kök zemin geri verilir), sahne yenilenince yeni
        // parçalarla yeniden kurulur (siyah tutma yeniden kurulmaz).
        welcome?.finish()
        welcomeArmUntil = 0L
        refreshDrivePrefs()
        if (!driveAccentOn) LauncherTheme.driveMode = DriveAccent.NORMAL
        LauncherTheme.applyFrom(resources.configuration, ThemeMode.read(this))
        syncAutoNight()
        window.decorView.setBackgroundColor(LauncherTheme.GROUND)
        rootLayout?.setBackgroundColor(LauncherTheme.GROUND)

        // Sabit parçalar: başlık yerinde yenilenir.
        header.applyTheme()
        val newEditBar = buildEditBar().also { it.visibility = editBar.visibility }
        swapView(editBar, newEditBar)
        editBar = newEditBar
        refreshEditLayoutUi()
        // Alt çubuk cam ve düğme renklerini kurulumda aldığı için yenisiyle değiştirilir.
        ourBar?.let { old ->
            val fresh = OurBottomBar(this, onPhoneTap = ::onPhoneButton)
            swapView(old.view, fresh.view)
            if (started) old.onHidden()
            old.release()
            ourBar = fresh
            if (oemBarForced) fresh.view.visibility = View.GONE
            if (started) showWidget(fresh)
        }
        callBanner.applyTheme()
        callScreen.applyTheme()
        fixedWidgets = listOfNotNull(header, ourBar, callBanner)

        // Orta alan yeni jetonlarla baştan kurulur (örtünün altında); düzen ve mod aynı kalır.
        rootLayout?.background = SahneGround()
        val old = sahne
        val fresh = newSahneScreen()
        if (started) old.onHidden()
        old.release()
        swapView(old, fresh)
        sahne = fresh
        fresh.setMode(activeMode)
        rootLayout?.let { welcome = buildWelcome(it) }
        if (keepEdit) {
            fresh.editMode = true
            window.decorView.post { if (!isFinishing && !isDestroyed && editing) settings.show(appearanceOnly = true) }
        }
        if (started) fresh.onVisible()
        window.decorView.invalidate()
        appliedSignature = HomeScreenPrefs.signature(this)
    }

    /** Tema geçişi örtüsü açıkken yeni kurulan widget'ların onVisible'ı bekletilir (yoklama/metrik patlamaları ilk karede çıkmasın). */
    private var themeDefer = false
    private val deferredVisible = ArrayList<LauncherWidget>()

    internal fun beginThemeDefer() { themeDefer = true }

    /** Örtü söndükten sonra: bekleyen ve hâlâ yaşayan widget'ların onVisible'ı çalışır ([ThemeTransition.retheme]). */
    internal fun endThemeDefer() {
        themeDefer = false
        if (deferredVisible.isEmpty()) return
        val pending = ArrayList(deferredVisible)
        deferredVisible.clear()
        if (!started || isFinishing || isDestroyed) return
        pending.forEach { w -> if (w in fixedWidgets) w.onVisible() }
    }

    private fun showWidget(w: LauncherWidget) {
        if (!started) return
        if (themeDefer) deferredVisible += w else w.onVisible()
    }

    /** Sahne orta alanı; seçici açılınca [sheet] olur, düzenleme yalnızca araç dururken. */
    private fun newSahneScreen(): SahneScreen = SahneScreen(this, onSheet = { sheet = it }, canEdit = { !editLocked() })

    /** [old]'u ana kadar bulunduğu yerde (sıra ve ölçüleriyle) [new] ile değiştirir. */
    private fun swapView(old: View, new: View) {
        val parent = old.parent as? ViewGroup ?: return
        val i = parent.indexOfChild(old)
        val lp = old.layoutParams
        parent.removeViewAt(i)
        parent.addView(new, i, lp)
    }

    /**
     * Sürüş modu değişimini uygular: gözlenen mod ~2 sn kararlı kalınca vurgu güncellenir ve etkinlik
     * ThemeTransition ile yeniden kurulur (renkler kurulumda okunur). Düzenleme/sayfa açıkken ertelenir.
     */
    private fun driveModeTick() {
        if (!driveAccentOn) return
        val observed = if (LauncherDemo.enabled) LauncherDemo.driveMode else observedDriveMode
        val to = driveModePolicy.update(System.currentTimeMillis(), observed, !editing && sheet == null)
        if (to != null && to != LauncherTheme.driveMode) {
            LauncherTheme.driveMode = to
            ThemeTransition.retheme(this)
        }
    }

    /**
     * Sahne karşılamasını kurar. Süreçte ilk kez (araç ünitesinde ya da demo isteğiyle) ekran baştan siyah tutulur: güç
     * sinyali [WELCOME_ARM_MS] içinde gelirse karşılama karanlıktan başlar, gelmezse kısa solmayla açılır.
     */
    private fun setupWelcome(root: View) {
        val w = buildWelcome(root)
        welcome = w
        val demo = LauncherDemo.enabled && LauncherDemo.welcomeRequested
        if (!WelcomeCycle.gate.played && !WelcomeCycle.armedOnce && (demo || LauncherSafeArea.isDesayUnit(this))) {
            WelcomeCycle.armedOnce = true
            w.prepare()
            welcomeArmUntil = android.os.SystemClock.uptimeMillis() + WELCOME_ARM_MS
        }
    }

    private fun buildWelcome(root: View): SahneWelcome {
        val (stage, parts) = sahne.welcomeParts()
        val others = listOfNotNull(header.view, ourBar?.view) + parts
        return SahneWelcome(root, stage, others)
    }

    /**
     * Karşılama (§7.4): kontak açılınca (güç modu 1/2) ya da kontak açıkken ilk öne gelişte bir kez.
     * Demo `launcher_demo_welcome true` beklemeden yeniden oynatır. Düzenlemeye girilirse hemen biter.
     */
    private fun welcomeTick() {
        val w = welcome ?: return
        if ((w.running || w.prepared) && (editing || sheet != null)) {
            w.finish()
            welcomeArmUntil = 0L
            return
        }
        val ready = resumed && !editing && sheet == null && wifiPanel == null && bluetoothPanel?.isShowing != true
        if (LauncherDemo.enabled && LauncherDemo.welcomeRequested && ready) {
            LauncherDemo.welcomeRequested = false
            WelcomeCycle.gate.replay()
            welcomeArmUntil = 0L
            if (WelcomeCycle.gate.update(true, ready = true, sportActive = false)) w.play() else w.finish()
            return
        }
        if (w.running) return
        if (WelcomeCycle.gate.update(observedPowerOn, ready, sportActive = false)) {
            welcomeArmUntil = 0L
            w.play()
        } else if (w.prepared && (WelcomeCycle.gate.played || android.os.SystemClock.uptimeMillis() > welcomeArmUntil)) {
            welcomeArmUntil = 0L
            w.reveal()
        }
    }

    /**
     * Park/Sürüş geçişi: hız ve vites sinyallerinden [DriveLayoutPolicy] karar verir; düzenleme ya da sayfa açıkken ertelenir.
     * Seçenek kapalıysa sürüş düzeni gösteriliyorsa Park'a dönülür. Demo `launcher_demo_moving false` beklemeden Park'a döner.
     * Kartların sürüş durumu ([DriveMotion]) aynı kurallarla ama seçenekten ve düzenlemeden bağımsız ayrı bir politikayla işler.
     */
    private fun layoutTick() {
        val idle = !editing && sheet == null
        val moving = if (LauncherDemo.enabled) LauncherDemo.moving else observedMoving
        // Vites/el freni yalnızca Sürüş'te durulduğunda okunur (salt okuma); Park'ta ve giderken IPC yapılmaz.
        val wantsPark = motionPolicy.wantsParkSignal(moving) || (autoLayoutOn && layoutPolicy.wantsParkSignal(moving))
        val parked = if (wantsPark) DriveSignals.parked() else { DriveSignals.invalidate(); false }
        val now = System.currentTimeMillis()
        val immediatePark = LauncherDemo.enabled && moving == false
        // Kartların sürüş durumu her zaman işler (seçenek kapalıyken de; düzenleme beklemez).
        motionPolicy.update(now, moving, parked, canSwitch = true, immediatePark = immediatePark)
        DriveMotion.set(motionPolicy.mode == LayoutMode.DRIVE)
        if (!autoLayoutOn) {
            if (activeMode == LayoutMode.DRIVE && idle) { layoutPolicy.reset(LayoutMode.PARK); switchMode(LayoutMode.PARK, true, automatic = true) }
            return
        }
        val to = layoutPolicy.update(now, moving, parked, idle, immediatePark = immediatePark)
        if (to != null) switchMode(to, true, automatic = true)
    }

    /** Düzeni değiştirir: orta alan kısa süre kararır, yeni düzen kurulur, geri açılır (etkinlik yeniden yaratılmaz). */
    private fun switchMode(to: LayoutMode, animate: Boolean, automatic: Boolean = false) {
        if (to == activeMode) return
        val notice = layoutNoticeFor(activeMode, to, automatic, started)
        activeMode = to
        LauncherMotion.driveLayout = to == LayoutMode.DRIVE
        rememberedMode = to
        LauncherInfo.mode = to
        notice?.let { showLayoutNotice(it) }
        val apply = { loadForMode() }
        if (!animate || !started) { apply(); return }
        val m = sahne
        m.animate().cancel()
        m.animate().alpha(0f).setDuration(FADE_OUT_MS).withEndAction {
            apply()
            m.animate().alpha(1f).setDuration(FADE_IN_MS).start()
        }.start()
    }

    /** Otomatik düzen geçişinde kısa, sessiz bilgi: başlıkta tarihin yerinde çapraz geçişle gösterilir. */
    private fun showLayoutNotice(mode: LayoutMode) {
        header.showNotice(if (mode == LayoutMode.DRIVE) R.string.launcher_layout_drive_edit else R.string.launcher_layout_park_edit,
            NOTICE_SHOW_MS, NOTICE_FADE_MS)
    }

    /** Düzenleme çubuğundaki Park | Sürüş seçici: düzenlenen/etkin düzeni elle değiştirir. */
    private fun selectEditLayout(to: LayoutMode) {
        if (!editing || !autoLayoutOn || to == activeMode) return
        val m = sahne
        m.animate().cancel()
        m.animate().alpha(0f).setDuration(EDIT_SWAP_OUT_MS).withEndAction {
            switchMode(to, animate = false)
            refreshEditLayoutUi()
            m.animate().alpha(1f).setDuration(EDIT_SWAP_IN_MS).start()
        }.start()
        // Seçici hemen yanıt verir; orta alan kısa geçişle ardından değişir.
    }

    private fun refreshEditLayoutUi() {
        editHelper.setText(when {
            activeMode == LayoutMode.DRIVE -> R.string.launcher_sahne_helper_drive
            else -> R.string.launcher_sahne_helper_park
        })
        // Tek düzen ("Sürüşte ayrı düzen" kapalı): Park | Sürüş seçici gizlenir, yalnızca tek düzen düzenlenir.
        editLayoutTabs?.visibility = if (autoLayoutOn) View.VISIBLE else View.GONE
        editSegments.forEach { (mode, seg) ->
            val on = mode == activeMode
            seg.view.background = if (on) rounded(this, DriveAccent.buttonFill(LauncherTheme.ACCENT), 999f) else null
            val c = if (on) LauncherTheme.ON_ACCENT else LauncherTheme.TEXT_SECONDARY
            seg.label.setTextColor(c)
            seg.icon.imageTintList = android.content.res.ColorStateList.valueOf(c)
            seg.view.isSelected = on
        }
    }

    private class EditSegment(val view: View, val label: android.widget.TextView, val icon: android.widget.ImageView)

    private val editSegments = LinkedHashMap<LayoutMode, EditSegment>()
    private var editLayoutTabs: View? = null

    private fun layoutSegmented(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(context, LauncherTheme.CARD, 999f, LauncherTheme.CARD_BORDER)
        setPadding(dp(5), dp(5), dp(5), dp(5))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(LauncherTheme.TOUCH_MIN_DP))
        editSegments.clear()
        listOf(
            Triple(LayoutMode.PARK, R.string.launcher_layout_park, R.drawable.ic_mdi_car),
            Triple(LayoutMode.DRIVE, R.string.launcher_layout_drive, R.drawable.ic_mdi_steering),
        ).forEach { (mode, label, iconRes) ->
            val ic = icon(context, iconRes, 32, LauncherTheme.TEXT_SECONDARY)
            val tv = text(context, LauncherTheme.BODY_SP + 4f, LauncherTheme.TEXT_SECONDARY, medium = true).apply { setText(label) }
            val seg = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(26), 0, dp(30), 0)
                contentDescription = getString(if (mode == LayoutMode.DRIVE) R.string.launcher_layout_drive_edit else R.string.launcher_layout_park_edit)
                isClickable = true
                setOnClickListener { selectEditLayout(mode) }
                pressFeedback()
                addView(ic)
                addView(gap(context, 10))
                addView(tv)
            }
            editSegments[mode] = EditSegment(seg, tv, ic)
            addView(seg, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
        }
    }

    /** Sürüş vurgusu ve otomatik düzen seçeneklerini bir kez okur (ayarlar bunları değiştirince çağrılır). */
    internal fun refreshDrivePrefs() {
        driveAccentOn = DriveAccent.enabled(this)
        autoLayoutOn = DriveLayoutPrefs.get(this)
    }

    override fun onStart() {
        super.onStart()
        started = true
        deferredVisible.clear() // aşağıda zaten hepsine onVisible gider
        refreshDrivePrefs()
        // Uzun süre arkadayken (tam ekran navigasyon) sürüş durumu bayatlar: duruş yedeği kadar geçtiyse Park'tan başlanır.
        if (stoppedAt != 0L && System.currentTimeMillis() - stoppedAt >= DriveLayoutPolicy.STILL_FALLBACK_MS) {
            motionPolicy.reset(LayoutMode.PARK)
            DriveMotion.set(false)
        }
        stoppedAt = 0L
        driveHandler.removeCallbacks(driveTick)
        driveHandler.postDelayed(driveTick, DRIVE_TICK_MS)
        metrics.addListener(metricsListener)
        fixedWidgets.forEach { it.onVisible() }
        sahne.onVisible()
        // Ana ekrana dönüş: araç uykusunda düşmüş olabilecek favori tuşu aboneliği tazelenir.
        FavoriteKey.refresh()
        ClimatePanelGuard.refresh()
        PhoneSource.refreshVoiceKey()
        refreshDefaultHome()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        LauncherEdition.setHomeForeground(true)
        // Açılışta (MapControlService) ana ekran istendi: göründüğü onaylanır, tekrar denenmez.
        LauncherEdition.onLauncherShown(this)
        resumed = true
        DriveScreen.onLauncherResumed(this)
        syncAutoNight()
        // FrostNav "Ana ekran" sekmesinde tema/alt çubuk/düzen tercihleri değiştiyse (renkler ve çubuklar kurulumda okunur) yeniden kur.
        val sig = HomeScreenPrefs.signature(this)
        if (appliedSignature.isNotEmpty() && sig != appliedSignature) {
            appliedSignature = sig
            recreating = true
            if (editing) pendingEdit = true
            ThemeTransition.recreate(this)
        }
    }

    /**
     * Otomatik temada araç lambası/ışık sensörü izlenir; düzenlerken ya da bir sayfa açıkken geçiş beklenir.
     * Yalnızca AUTO iken dinleyici ve döngü kurulur; tema modu değişince ([applyThemeInPlace]) yeniden eşitlenir.
     */
    private fun syncAutoNight() {
        if (!resumed) return
        LauncherTheme.startAutoNight(this, { !editing && sheet == null }) { ThemeTransition.retheme(this) }
    }

    override fun onPause() {
        resumed = false
        welcome?.finish()
        welcomeArmUntil = 0L
        LauncherTheme.stopAutoNight()
        LauncherEdition.setHomeForeground(false)
        super.onPause()
    }

    override fun onStop() {
        started = false
        stoppedAt = System.currentTimeMillis()
        themeDefer = false
        deferredVisible.clear()
        driveHandler.removeCallbacks(driveTick)
        sheet?.dismiss()
        sheet = null
        wifiPanel?.dismiss()
        bluetoothPanel?.dismiss()
        exitEditMode()
        // Görünmezken arama sayfası çizilmez/sayılmaz; arama sürerken öne gelince şerit ya da sayfa yeniden açılır.
        callScreen.close()
        metrics.removeListener(metricsListener)
        fixedWidgets.forEach { it.onHidden() }
        sahne.onHidden()
        super.onStop()
    }

    /**
     * Sürüş ekranı: ana ekran yarılara yerini bırakınca açık sayılır, yeniden öne gelince biter ([DriveScreen]). Ana ekran tuşu:
     * tuşa basıldığında ana ekranımız önde miydi ([HomeKey]; öndeyse aracın kendi ana ekranına çıkılır).
     */
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        HomeKey.onLauncherTop(this, isTopResumedActivity)
        DriveScreen.onLauncherTop(this, isTopResumedActivity)
    }

    private fun showDriveBackdrop(show: Boolean) {
        val v = driveBackdrop ?: return
        v.animate().cancel()
        v.removeCallbacks(showDriveMapHint)
        if (show) {
            // Hemen tam görünür: belirme animasyonu ana ekran öndeki yerini yarılara bırakınca yarıda kalabiliyor ve durum
            // çubuğu şeridinde kartlar görünüyordu. Yarılar zaten kendi açılış animasyonuyla gelir.
            v.alpha = 1f
            if (v.visibility != View.VISIBLE) {
                v.visibility = View.VISIBLE
                // İpucu açılışta harita yarısında yanıp sönmesin: harita penceresi gelene kadar gizli, sonra (arkasında) belirir.
                driveMapHint?.let { hint ->
                    hint.animate().cancel()
                    hint.alpha = 0f
                    placeDriveMapHint(v, hint)
                }
            }
            v.postDelayed(showDriveMapHint, DRIVE_HINT_DELAY_MS)
        } else if (v.visibility == View.VISIBLE) {
            v.animate().alpha(0f).setDuration(DRIVE_FADE_MS).withEndAction { v.visibility = View.GONE }.start()
        }
    }

    /** İpucunu sürüş ekranının harita yarısına (ekran koordinatı, [FrostNavFeatures.driveMapSlot]) yerleştirir. */
    private fun placeDriveMapHint(backdrop: View, hint: View) {
        val slot = LauncherEdition.frostNav(this).driveMapSlot()
        if (slot == null) {
            hint.visibility = View.GONE
            return
        }
        hint.visibility = View.VISIBLE
        backdrop.post {
            val at = IntArray(2).also { backdrop.getLocationOnScreen(it) }
            hint.layoutParams = (hint.layoutParams as FrameLayout.LayoutParams).apply {
                width = slot.width()
                height = slot.height()
                leftMargin = slot.left - at[0]
                topMargin = slot.top - at[1]
            }
        }
    }

    /** Dokunuş (ekran koordinatı) sürüş ekranının harita yarısında mı. */
    private fun inDriveMapSlot(v: View, rawX: Float, rawY: Float): Boolean {
        if (v.visibility != View.VISIBLE) return false
        val slot = LauncherEdition.frostNav(this).driveMapSlot() ?: return false
        return slot.contains(rawX.toInt(), rawY.toInt())
    }

    override fun onDestroy() {
        DriveScreen.detachLauncher(onDriveBackdrop)
        if (::fixedWidgets.isInitialized) fixedWidgets.forEach { it.release() }
        if (::sahne.isInitialized) sahne.release()
        // Ortak örnek: release() çağrılmaz (güneş çatısı vb. dinleyicileri sönerdi), yalnızca kendi dinleyicimiz çıkar.
        metrics.removeListener(metricsListener)
        settings.release()
        dataCheckExtra?.release()
        dataCheckExtra = null
        FavoriteKey.stop(this)
        PhoneSource.stopVoiceKey(this)
        if (::callScreen.isInitialized) callScreen.release()
        stopCallWatcher()
        super.onDestroy()
    }

    /** İzin sonuçları: telefon izinleri, Wi-Fi paneli konum izni, fotoğraf widget'ı depolama izni. */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Telefon izinleri (rehber, geçmiş, arama): kısmi verilse de sonuç yeniden okunur.
        if (requestCode == PhoneSource.REQ_PERMISSIONS) {
            PhoneSource.onPermissionsResult(this)
            return
        }
        // Wi-Fi paneli reddi de bilmeli (kalıcı retten sonra satırdaki düğme "Ayarları aç"a döner); yarıda kesilen pencere ret sayılmaz.
        if (requestCode == WifiPanel.REQ_LOCATION) { wifiPanel?.onPermissionResult(WifiAccess.onPermissionResult(grantResults)); return }
        val granted = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted && requestCode == PhotoTile.REQ_STORAGE) PhotoTile.pickerRefresh?.invoke()
    }

    /** Ana ekrandayken geri tuşu yalnızca düzenleme modundan çıkar. */
    @Deprecated("Activity.onBackPressed, API 33 öncesi araçta geçerli yol")
    override fun onBackPressed() {
        if (::callScreen.isInitialized && callScreen.isOpen) {
            minimizeCallScreen()
            return
        }
        if (editing) exitEditMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Etkin düzeni (Park/Sürüş) Sahne'ye uygular. */
    private fun loadForMode() = sahne.setMode(activeMode)

    // --- Düzenleme modu ---

    /** Düzenleme yalnızca araç dururken açılır; sürüş sırasında ekranla uğraşılmasın. */
    private fun enterEditMode() {
        if (editing) return
        if (editLocked()) return
        sahne.editMode = true
        refreshEditLayoutUi()
        header.view.visibility = View.INVISIBLE
        editBar.visibility = View.VISIBLE
    }

    /** Araç hareket halindeyse (veya hız henüz okunmadıysa) kısa bir mesaj gösterip true döner. */
    private fun editLocked(): Boolean {
        if (!LauncherSafeArea.isMoving(metrics.currentSnapshot())) return false
        Toast.makeText(this, R.string.launcher_edit_locked_driving, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun exitEditMode() {
        if (!editing) return
        sheet?.dismiss()
        sheet = null
        sahne.editMode = false
        editBar.visibility = View.GONE
        header.view.visibility = View.VISIBLE
        // Önizleme/düzenleme bitince otomatik düzene dönülür (Sürüş'ü Park'tayken düzenlediyse).
        val auto = if (autoLayoutOn) layoutPolicy.mode else LayoutMode.PARK
        if (activeMode != auto) switchMode(auto, animate = false)
    }

    private fun buildEditBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE

        // Boşluk seçicinin kenar payı: seçici gizlenince yardım metni sola yaslanır.
        addView(layoutSegmented().also { tabs ->
            editLayoutTabs = tabs
            (tabs.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(20)
        })
        addView(text(context, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY, maxLines = 2).apply {
            editHelper = this
            gravity = Gravity.CENTER_VERTICAL
            setText(R.string.launcher_sahne_helper_park)
        }, weightLp(1f))
        addView(gap(context, 16))
        // Yuvalar sabit: ekleme yuvanın kendi seçicisinden yapılır.
        addView(barButton(R.drawable.ic_mdi_theme_light_dark, R.string.launcher_appearance, LauncherTheme.CARD) { settings.show(appearanceOnly = true) })
        addView(gap(context, 16))
        addView(barButton(R.drawable.ic_mdi_restart, R.string.launcher_edit_reset, LauncherTheme.CARD) { confirmReset() })
        addView(gap(context, 16))
        addView(barButton(R.drawable.ic_mdi_check, R.string.launcher_edit_done, DriveAccent.buttonFill(LauncherTheme.ACCENT), onFill = true) { exitEditMode() })
    }

    private fun barButton(iconRes: Int, labelRes: Int, background: Int, onFill: Boolean = false, onClick: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        this.background = rounded(context, background)
        setPadding(dp(24), 0, dp(24), 0)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(LauncherTheme.TOUCH_MIN_DP))
        // Birincil düğme: renkli dolgu üzerinde beyaz ikon ve yazı (karşıtlık >= 4,5:1, bkz. DriveAccent.buttonFill).
        addView(if (onFill) icon(context, iconRes, 28, LauncherTheme.ON_ACCENT) else icon(context, iconRes, 28))
        addView(gap(context, 12))
        addView(text(context, LauncherTheme.BODY_SP, if (onFill) LauncherTheme.ON_ACCENT else LauncherTheme.TEXT, medium = true).apply { setText(labelRes) })
        contentDescription = getString(labelRes)
        isClickable = true
        setOnClickListener { onClick() }
        pressFeedback()
    }

    private fun confirmReset() {
        if (isFinishing || isDestroyed) return
        sheet?.dismiss()
        sheet = showLauncherSheet(this, R.string.launcher_edit_reset, RESET_SHEET_WIDTH_DP) { d ->
            addView(text(context, LauncherTheme.BODY_SP, LauncherTheme.TEXT_SECONDARY, maxLines = 3).apply {
                setText(when {
                    !autoLayoutOn -> R.string.launcher_edit_reset_confirm_home
                    activeMode == LayoutMode.DRIVE -> R.string.launcher_edit_reset_confirm_drive
                    else -> R.string.launcher_edit_reset_confirm_park
                })
            }, sheetFullWidth())
            addView(gap(context, 32, vertical = true))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(sheetButton(R.string.launcher_nav_dialog_cancel, primary = false) { d.dismiss() }, weightLp(1f))
                addView(gap(context, 12))
                addView(sheetButton(R.string.launcher_edit_reset, primary = true) {
                    d.dismiss()
                    sahne.resetToDefaults()
                }, weightLp(1f))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(LauncherTheme.TOUCH_MIN_DP)))
        }
    }

    private fun sheetButton(labelRes: Int, primary: Boolean, onClick: () -> Unit): View =
        text(this, LauncherTheme.BODY_SP, if (primary) LauncherTheme.ON_ACCENT else LauncherTheme.TEXT, medium = true).apply {
            setText(labelRes)
            gravity = Gravity.CENTER
            background = rounded(context, if (primary) LauncherTheme.ACCENT else LauncherTheme.CONTROL, 16f)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            pressFeedback()
        }

    // --- Diğer ---

    /**
     * Alt çubuk telefon düğmesi ([PhoneUiLogic.phoneButtonAction]): telefon paneli ekrandaysa bir şey yapılmaz; bağlı
     * telefon var ama arama yolu yoksa OEM telefon uygulaması; yoksa telefon sayfası (bağlı değilse Bluetooth düğmesiyle).
     */
    private fun onPhoneButton() {
        if (editing || isFinishing || isDestroyed) return
        val s = PhoneSource.snapshot()
        val panelOnScreen = ::sahne.isInitialized && sahne.hasPanel(WidgetType.PHONE)
        when (PhoneUiLogic.phoneButtonAction(panelOnScreen, s.connected, s.canDial || LauncherDemo.enabled)) {
            // Panel zaten ekranda: dokunuş boşa gitmiş gibi görünmesin (titreşim + panel kısa bir nabız atar).
            PhoneUiLogic.PhoneButtonAction.NONE -> {
                window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                sahne.flashPanel(WidgetType.PHONE)
            }
            PhoneUiLogic.PhoneButtonAction.OEM_APP -> openOemPhoneApp(this)
            PhoneUiLogic.PhoneButtonAction.SHEET -> openPhoneSheet()
        }
    }

    /** Gelen arama şeridi göründü: üstteki sayfa ve Wi-Fi/Bluetooth panelleri kapanır (Cevapla/Reddet örtülmesin). */
    private fun onIncomingCall() {
        sheet?.dismiss()
        sheet = null
        wifiPanel?.dismiss()
        bluetoothPanel?.dismiss()
    }

    // --- Arama sayfası ve öne getirme (karar §15) ---

    /** Arama sayfasını açar: üstteki sayfa/paneller kapanır (diyalog pencereleri örtünün üstünde kalırdı). */
    private fun openCallScreen(fromUser: Boolean) {
        if (isFinishing || isDestroyed || !::callScreen.isInitialized) return
        if (fromUser) callMinimized = false
        if (editing) exitEditMode()
        sheet?.dismiss()
        sheet = null
        wifiPanel?.dismiss()
        bluetoothPanel?.dismiss()
        callScreen.open()
    }

    private fun minimizeCallScreen() = callScreen.close()

    /** Arama sayfası açıldı/kapandı: şerit gizlenir/gelir. Ön plandayken arama sürerken kapandıysa kullanıcı küçülttü. */
    private fun onCallScreenChanged(open: Boolean) {
        callBanner.setCovered(open)
        if (!open && resumed && PhoneSource.snapshot().call.inCall) callMinimized = true
    }

    /** [EXTRA_SHOW_CALL] ile gelindi (öne getirme): arama sürüyorsa ve kullanıcı küçültmediyse sayfa açılır. */
    private fun consumeShowCall(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SHOW_CALL, false) != true) return
        intent.removeExtra(EXTRA_SHOW_CALL)
        window.decorView.post { if (!callMinimized && PhoneSource.snapshot().call.inCall) openCallScreen(fromUser = false) }
    }

    /**
     * "Arama ekranı: Launcher" iken izleyici etkinlik ömrü boyunca açık (yalnız arama durumu; rehber okunmaz, arama yokken
     * yoklama yok). "Fabrika"da kapalı. Ayar değişince [LauncherSettings] çağırır.
     */
    internal fun syncCallWatcher() {
        val want = CallScreenPrefs.launcher(this)
        if (want == callWatching) return
        if (!want) {
            stopCallWatcher()
            return
        }
        callWatching = true
        watchKind = PhoneSource.snapshot().call.kind
        refreshDefaultHome()
        PhoneSource.start(this, callWatcher, wantsData = false)
        PhoneSource.addListener(callWatcher)
    }

    private fun stopCallWatcher() {
        window.decorView.removeCallbacks(bringTask)
        if (!callWatching) return
        callWatching = false
        PhoneSource.removeListener(callWatcher)
        PhoneSource.stop(callWatcher)
    }

    /** Arama durumu geçişi: gelen arama / aranıyor / konuşmaya ilk geçişte 700 ms sonra launcher öne getirilir. */
    private fun onCallWatch(next: CallKind) {
        val prev = watchKind
        watchKind = next
        val prevIn = prev == CallKind.INCOMING || prev == CallKind.DIALING || prev == CallKind.ACTIVE
        val nextIn = next == CallKind.INCOMING || next == CallKind.DIALING || next == CallKind.ACTIVE
        if (!nextIn) {
            if (next == CallKind.NONE) window.decorView.removeCallbacks(bringTask)
            return
        }
        // Yeni arama: geçmiş geçişler ve küçültme sıfırlanır.
        if (!prevIn) {
            broughtFor.clear()
            callMinimized = false
        }
        if (!PhoneUiLogic.shouldBringToFront(prev, next, isHomeForCalls(), next in broughtFor)) return
        broughtFor += next
        window.decorView.removeCallbacks(bringTask)
        window.decorView.postDelayed(bringTask, BRING_TO_FRONT_DELAY_MS)
    }

    /**
     * OEM arama ekranı (com.desaysv.bluetooth.phone) açıldıktan sonra launcher öne alınır ve arama sayfası açılır. Ana
     * ekran süreci Android 10/11'de arka plan etkinlik başlatma kısıtından muaf (araçta doğrulanacak).
     */
    private fun bringToFrontForCall() {
        if (isFinishing || isDestroyed || !callWatching || !isHomeForCalls()) return
        val call = PhoneSource.snapshot().call
        if (!call.inCall) return
        if (resumed) {
            if (!callMinimized) openCallScreen(fromUser = false)
            return
        }
        try {
            startActivity(android.content.Intent(this, javaClass)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(EXTRA_SHOW_CALL, true))
            android.util.Log.i(CALL_TAG, "arama: launcher öne getirildi (${call.kind})")
        } catch (t: Throwable) {
            android.util.Log.w(CALL_TAG, "arama: öne getirilemedi ${t.javaClass.simpleName}")
        }
    }

    /**
     * Arama sayfası için "ana ekran biz miyiz": sistem varsayılanı biz ya da FrostNav launcher modu açık (araçta varsayılan HOME
     * OEM launcher kalıyor, Ana ekran tuşunu FrostNav yakalıyor; araç testinde OEM arama ekranı önde kaldı).
     */
    private fun isHomeForCalls(): Boolean = defaultHome || LauncherEdition.launcherModeOn(this)

    /** Varsayılan HOME biz miyiz: PackageManager sorgusu arka planda (Binder ana iş parçacığında değil). */
    private fun refreshDefaultHome() {
        val app = applicationContext
        LauncherIo.serial().execute {
            defaultHome = try {
                app.packageManager.resolveActivity(
                    android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME),
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
                )?.activityInfo?.packageName == app.packageName
            } catch (t: Throwable) {
                false
            }
        }
    }

    /** "Tümü" sayfası (Son aramalar / Kişiler): açık sayfanın yerini alır; gelen aramada kapanır. */
    internal fun openPhoneAll() {
        if (editing || isFinishing || isDestroyed) return
        sheet?.dismiss()
        lateinit var d: android.app.Dialog
        d = PhoneAllSheet.show(this) { if (sheet === d) sheet = null }
        sheet = d
    }

    /** Telefon sayfası: Sahne paneliyle aynı görünüm; arama başlayınca kapanır, kapanınca panel bırakılır. */
    private fun openPhoneSheet() {
        sheet?.dismiss()
        var panel: PhonePanel? = null
        val d = showLauncherSheet(this, R.string.launcher_phone_title, PHONE_SHEET_WIDTH_DP) { dialog ->
            val p = PhonePanel(this@HomeLauncherActivity, sheet = true, onDialed = { dialog.dismiss() },
                onOpenAll = { dialog.dismiss(); openPhoneAll() })
            panel = p
            addView(p.view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(PHONE_SHEET_HEIGHT_DP)))
            p.onVisible()
        }
        d.setOnDismissListener {
            panel?.release()
            panel = null
            if (sheet === d) sheet = null
        }
        sheet = d
    }

    /** Wi-Fi simgesine dokunma: simgenin altında açılır panel (ikinci dokunuş kapatır). */
    private fun toggleWifiPanel(anchor: View) {
        if (editing) return
        val panel = wifiPanel ?: WifiPanel(
            this, LauncherEdition.wifiBackend(this),
            driving = { activeMode == LayoutMode.DRIVE || LauncherSafeArea.isMoving(metrics.currentSnapshot()) },
            onSettings = { LauncherEdition.openWifi(this) },
            features = LauncherEdition.frostNav(this),
            onWebState = { header.setWebRunning(it) },
        ).also { wifiPanel = it }
        panel.show(anchor)
    }

    /** Bluetooth simgesine dokunma: launcher'dan çıkmadan simgenin altında Bluetooth paneli (ikinci dokunuş kapatır). */
    private fun toggleBluetoothPanel(anchor: View) {
        if (editing) return
        val panel = bluetoothPanel ?: BluetoothPanel(this, AndroidBluetoothBackend(this), onSettings = { openOemBluetooth(this) })
            .also { bluetoothPanel = it }
        panel.show(anchor)
    }

    private fun openProfiles() {
        // Düzenleme sırasında profil değişmez (başlık zaten gizli); sürüşte serbest, tek dokunuş.
        if (editing) return
        showProfileSheet(this, isMoving = { LauncherSafeArea.isMoving(metrics.currentSnapshot()) }) { ThemeTransition.recreate(this) }
    }

    private fun openSettings() {
        // Doğrudan MapControl ayarları; ayar ekranı olmayan derlemede launcher paneli.
        if (LauncherEdition.HAS_SETTINGS) LauncherEdition.openSettings(this) else settings.show()
    }

    /**
     * Inset dinleyicisinden: sistem OEM çubuğunu zorla gösteriyor mu. Gizleme kısa bir bekleme sonra uygulanır (açılışta gizleme
     * isteği işlenene dek çubuk bir an görünür bildirilebilir, çubuğumuz titremesin); geri gelme hemen.
     */
    private fun onOemBarForced(forced: Boolean) {
        val root = rootLayout ?: return
        root.removeCallbacks(applyOemBarForced)
        if (forced == oemBarForced) return
        if (forced) root.postDelayed(applyOemBarForced, OEM_BAR_SETTLE_MS) else setOemBarForced(false)
    }

    private fun setOemBarForced(forced: Boolean) {
        if (oemBarForced == forced) return
        oemBarForced = forced
        val visibility = if (forced) View.GONE else View.VISIBLE
        ourBar?.view?.visibility = visibility
        ourBarGap?.visibility = visibility
        Log.i(TAG, "OEM çubuğu sistemce zorlandı=$forced (bizim çubuk ${if (forced) "gizli" else "görünür"})")
        // Alt pay yeni duruma göre yeniden hesaplansın.
        rootLayout?.requestApplyInsets()
    }

    private fun hideSystemBars() {
        // minSdk 29: WindowInsetsController API 30'da geldi; Android 10'da (ya da denetleyici yoksa) eski bayrak yolu.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && hideSystemBarsR()) return
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            if (LauncherTheme.night) 0 else View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.R)
    private fun hideSystemBarsR(): Boolean {
        val it = window.insetsController ?: return false
        it.hide(WindowInsets.Type.statusBars())
        if (LauncherBottomBar.hidesOemBar(this)) it.hide(WindowInsets.Type.navigationBars())
        // Gündüz görünümünde gezinme çubuğu simgeleri açık zeminde görünür kalsın.
        it.setSystemBarsAppearance(
            if (LauncherTheme.night) 0 else WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
        it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        return true
    }

    private companion object {
        const val EDIT_EXIT_KMH = 5
        /** FrostNav Ayarlar'dan gelir (bkz. SettingsTabBuilder): açılışta Görünüm panelini gösterir. */
        const val EXTRA_OPEN_APPEARANCE = "launcher_open_appearance"
        const val FADE_OUT_MS = 120L
        const val FADE_IN_MS = 200L
        /** Tema geçişiyle yeniden kurulmada etkin düzeni korur. */
        @Volatile var rememberedMode = LayoutMode.PARK
        private const val NOTICE_SHOW_MS = 1800L
        private const val NOTICE_FADE_MS = 250L
        private const val EDIT_SWAP_OUT_MS = 80L
        private const val EDIT_SWAP_IN_MS = 120L
        /** FrostNav'dan gelen "düzenleme modunda aç" isteği; yeniden kurulma arasında kaybolmasın diye statik. */
        @Volatile var pendingEdit = false
        const val DRIVE_STABLE_MS = 2_000L
        const val DRIVE_TICK_MS = 500L
        /** Soğuk açılışta karşılama için güç sinyalinin en çok beklendiği süre (ekran bu sırada siyah). */
        const val WELCOME_ARM_MS = 1_500L
        /** Sürüş ekranı zemininin kalkma süresi (ana ekrana dönüşte kartlar yumuşak belirir). */
        const val DRIVE_FADE_MS = 180L
        /** Zemindeki "Haritayı aç" ipucu açılıştan bu kadar sonra belirir (o ana dek harita penceresi üstünü örter). */
        const val DRIVE_HINT_DELAY_MS = 2_500L
        const val RESET_SHEET_WIDTH_DP = 640
        const val PHONE_SHEET_WIDTH_DP = 760
        const val PHONE_SHEET_HEIGHT_DP = 440
        /** Arama şeridinin üst kenardan payı (başlığın üstünde durur). */
        const val CALL_BANNER_TOP_DP = 12
        /** Öne getirmede arama sayfasını açtıran ekstra (karar §15). */
        const val EXTRA_SHOW_CALL = "launcher_show_call"
        /** OEM arama ekranı açıldıktan sonra launcher'ın öne alınma gecikmesi. */
        const val BRING_TO_FRONT_DELAY_MS = 700L
        const val CALL_TAG = "PhoneUi"
        /** Sistem OEM çubuğunu zorla açtı bilgisinin kalıcı sayılması için bekleme (açılıştaki geçici durumu eler). */
        const val OEM_BAR_SETTLE_MS = 300L
        const val TAG = "HomeLauncher"
    }
}
