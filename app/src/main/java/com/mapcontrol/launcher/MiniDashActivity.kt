package com.mapcontrol.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.mapcontrol.R
import com.mapcontrol.media.LauncherMediaController
import com.mapcontrol.media.LauncherMediaController.MediaState
import com.mapcontrol.vehicle.VehicleMetricsRepository
import com.mapcontrol.vehicle.VehicleMetricsSnapshot
import java.util.Date

/**
 * Mini gösterge: bölünmüş ekranın bir yarısında (navigasyonun yanında, serbest pencere) ya da tam ekran açılan ayrı etkinlik
 * (karar §5). Ana ekran (launcher) kendisi bölünemediği için bu etkinlik MapControl'ın bölme düğmesinden ya da uygulama
 * listesinden açılır.
 *
 * Yan panele göz atan sürücü için önem sırası: (1) saat, menzil ve yakıt büyük; (2) ana ekranın araç sahnesi ([SahneStage],
 * küçültülmüş: kapı / bagaj / ışıklar canlı, kilit hapı, uyarı şeridi, dokununca araç detayı); (3) ince medya şeridi (çalan bir
 * şey yokken yer tutmaz, üstte "Müzik aç" düğmesi kalır); (4) yan sütunda yolculuk (sürüşte canlı anlık tüketim, dururken
 * yolculuk mesafesi; [MiniTripPanel]) ve telefon (tek dokunuşla "Geri ara", favoriler; [MiniPhonePanel]); (5) "Tam ekran" ile
 * ana ekrana dönüş. Klima yok (fabrika çubuğunda), hız yok (gösterge panelinde), koltuklar ana ekranda. Yerleşim kuralları
 * [MiniLayout]'ta (bölünmüş yarı ya da geniş satır); serbest pencere başlığı gizlenemezse içerik onun altından başlar
 * ([FreeformCaption.overlapPx]).
 *
 * Sürüş ekranının yarısı olarak açılınca ([DriveScreen], Navigasyon kartı) başlık her zaman gizlenir, "Tam ekran" "Ana ekran" adını
 * alır (iki "tam ekran" karışmasın) ve yanında yazılı "Haritayı büyüt" düğmesi çıkar: diğer yarıdaki haritayı tam ekran yapar, bu yarı
 * kapanır. Yer açılsın diye "Müzik aç" o zaman yalnız simge kalır.
 *
 * Veri kaynakları ana ekranınkilerle aynıdır ve paylaşılır ([VehicleMetricsRepository], [TripSource], [LauncherMediaController],
 * [PhoneSource], sahnenin kendi kaynakları); araca hiçbir şey yazılmaz. Görünmezken (onStop) tüm
 * dinleyiciler ve yoklamalar durur.
 */
class MiniDashActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private val metrics by lazy { VehicleMetricsRepository.getInstance(applicationContext) }
    private lateinit var media: LauncherMediaController
    private var nav: MiniNavSource = MiniNavSource.NONE
    private var started = false

    // --- Durum (yalnızca değişince yeniden bağlanır) ---
    private var snapshot: VehicleMetricsSnapshot? = null
    private var trip = TripData()
    private var phoneView: MiniSide.PhoneView? = null
    private var tripShownNow: Boolean? = null
    private var navHint: NavHint? = null
    private var lastTop: Triple<Int?, Int?, Boolean>? = null
    private var lastMedia: MediaUi? = null
    private var layoutNow: MiniLayout? = null
    private var captionPx = 0
    /** Demo: `--ez launcher_mini_demo_idle true` çalan bir şey yokmuş gibi gösterir (daraltılmış medya). */
    private var demoIdle = false
    /** Sürüş ekranının yarısı olarak açıldı ([DriveScreen.EXTRA_DRIVE]): başlık her zaman gizlenir, harita düğmesi gösterilir. */
    private var drive = false

    private val metricsListener = VehicleMetricsRepository.Listener { s ->
        main.post { if (started && !LauncherDemo.enabled) { snapshot = s; bindTop() } }
    }
    private val tripListener = TripSource.Listener {
        trip = it
        bindTop()
        bindTrip()
    }
    private val mediaListener = LauncherMediaController.Listener { s -> if (!LauncherDemo.enabled) main.post { if (started) bindMedia(s) } }
    private val navListener = MiniNavSource.Listener { h -> navHint = h; bindNav() }
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = bindClock()
    }

    // --- Görünümler ---
    private lateinit var root: FrameLayout
    private lateinit var column: LinearLayout
    /** Bölünmüş: üst satır. Geniş: sol bilgi sütunu. */
    private lateinit var info: LinearLayout
    private lateinit var stats: LinearLayout
    private lateinit var pills: LinearLayout
    /** Bölünmüş: araç | yan sütun (yolculuk + telefon) satırı. Geniş: araç / medya sütunu. */
    private lateinit var body: LinearLayout
    /** Yan sütun: yolculuk + telefon. */
    private lateinit var side: LinearLayout
    private var arrangedWide: Boolean? = null
    private lateinit var clock: TextView
    /** Geniş biçimde saatin altında gün ve tarih (ana ekran başlığındaki biçim). */
    private lateinit var date: TextView
    private lateinit var divider: View
    private lateinit var rangeBlock: LinearLayout
    private lateinit var rangeValue: TextView
    private lateinit var rangeUnit: TextView
    private lateinit var fuelBlock: LinearLayout
    private lateinit var fuelValue: TextView
    private lateinit var gauge: SahneStage.FuelGauge
    private lateinit var fullPill: Pill
    private lateinit var musicPill: Pill
    /** Sürüş ekranı: diğer yarıdaki haritayı tam ekran yapar (yalnız simge). */
    private lateinit var mapPill: Pill
    private lateinit var stage: SahneStage
    private lateinit var mediaStrip: LinearLayout
    private lateinit var artwork: ImageView
    private lateinit var artworkBox: FrameLayout
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var prev: FrameLayout
    private lateinit var play: FrameLayout
    private lateinit var playIcon: ImageView
    private lateinit var next: FrameLayout
    private lateinit var navRow: LinearLayout
    private lateinit var navManeuver: TextView
    private lateinit var navDistance: TextView
    private lateinit var navEta: TextView
    private lateinit var tripPanel: MiniTripPanel
    private lateinit var phonePanel: MiniPhonePanel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherEdition.init(applicationContext)
        // Uygulama listesinden tek başına açılınca da ana ekranla aynı gündüz / gece.
        LauncherTheme.applyFrom(resources.configuration, ThemeMode.read(this))
        // Demo bayrakları LauncherDemo'nun kendi okuyucularıyla (yalnızca debug derlemede açılır).
        LauncherDemo.applyFrom(this, intent)
        LauncherDemo.applyPreview(intent)
        LauncherDemo.applyMoving(intent)
        readDemo(intent)
        nav = navSourceFactory(this, intent)
        media = LauncherMediaController(this)
        window.setBackgroundDrawable(ColorDrawable(Sahne.GROUND))
        setContentView(build())
        LauncherStartup.attach(window.decorView)
        // Serbest pencere başlığı görünür kaldıysa gizlenir; gizlenemezse içerik onun altından başlar.
        window.decorView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> v.post { onWindowLaidOut() } }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        LauncherDemo.applyFrom(this, intent)
        LauncherDemo.applyPreview(intent)
        LauncherDemo.applyMoving(intent)
        readDemo(intent)
        val nextNav = navSourceFactory(this, intent)
        if (nextNav.javaClass != nav.javaClass) {
            if (started) nav.stop()
            nav = nextNav
            if (started) nav.start(navListener)
        }
        if (started) bindMedia(currentMedia())
    }

    private fun readDemo(intent: Intent?) {
        demoIdle = LauncherDemo.enabled && intent?.getBooleanExtra(EXTRA_DEMO_IDLE, false) == true
        drive = intent?.getBooleanExtra(DriveScreen.EXTRA_DRIVE, false) == true
    }

    override fun onStart() {
        super.onStart()
        started = true
        registerReceiver(timeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        bindClock()
        snapshot = if (LauncherDemo.enabled) LauncherDemo.vehicle() else metrics.currentSnapshot()
        bindTop()
        if (!LauncherDemo.enabled) metrics.addListener(metricsListener)
        TripSource.add(this, tripListener)
        media.addListener(mediaListener)
        media.start()
        bindMedia(currentMedia())
        stage.onVisible()
        phonePanel.onVisible()
        bindTrip()
        nav.start(navListener)
    }

    override fun onResume() {
        super.onResume()
        bindMapPill()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        // Seçici yarısında harita seçildiyse düğme görünür olur (Mini göstergeye dönünce).
        if (isTopResumedActivity) bindMapPill()
    }

    /** Harita düğmesi yalnız sürüş ekranında ve harita yarısında bir harita varken; "Tam ekran" sürüş ekranında "Ana ekran". */
    private fun bindMapPill() {
        if (!::mapPill.isInitialized) return
        mapPill.visibility = if (drive && DriveScreen.active && DriveScreen.mapPackage != null) View.VISIBLE else View.GONE
        fullPill.setText(getString(if (drive) R.string.launcher_drive_home else R.string.launcher_mini_fullscreen))
        layoutNow?.let { applyPillText(it) }
    }

    /**
     * Yazılar dar yarıda gizli; harita düğmesi görünürken "Müzik aç" yalnız simge (üç yazılı hap başlık satırına sığmaz). Geniş
     * yerleşimde (Tiggo 8 üst yarı, bilgi sütunu ~372 dp) "Ana ekran" da yalnız ev simgesi kalır: öykünücüde 2560x1440'ta "An…" diye
     * kesiliyordu; yazılı kalan tek düğme "Haritayı büyüt".
     */
    private fun applyPillText(m: MiniLayout) {
        val mapShown = mapPill.visibility == View.VISIBLE
        fullPill.setTextShown(m.pillText && !(mapShown && m.wide))
        mapPill.setTextShown(m.pillText)
        musicPill.setTextShown(m.pillText && !mapShown)
    }

    /** Harita tam ekran olur, sonra bu yarı kapanır (Ana ekran tuşu yine bizim ana ekranımıza döner; [DriveScreen]). */
    private fun goMapFullScreen() {
        DriveScreen.mapFullScreen(this) { ok -> if (ok && !isFinishing) finishAndRemoveTask() }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        started = false
        try { unregisterReceiver(timeReceiver) } catch (e: IllegalArgumentException) { }
        metrics.removeListener(metricsListener)
        TripSource.remove(tripListener)
        media.removeListener(mediaListener)
        media.stop()
        stage.onHidden()
        phonePanel.onHidden()
        nav.stop()
        main.removeCallbacksAndMessages(null)
        super.onStop()
    }

    override fun onDestroy() {
        stage.release()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PhoneSource.REQ_PERMISSIONS) PhoneSource.onPermissionsResult(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Pencere boyutu değişti (serbest pencere): ölçüler yerleşim dinleyicisinde yeniden seçilir.
        root.requestLayout()
        // DecorCaptionView yapılandırma değişince başlığı yeniden gösterebilir.
        window.decorView.post { onWindowLaidOut() }
    }

    /** Başlığı gizlemeyi dener; kalan örtüşme değiştiyse yerleşim yeniden seçilir. */
    private fun onWindowLaidOut() {
        if (isFinishing || isDestroyed) return
        FreeformCaption.apply(this, force = drive)
        val px = FreeformCaption.overlapPx(this, root)
        if (px == captionPx) return
        captionPx = px
        relayout(root.width, root.height)
    }

    /**
     * "Tam ekran": bizim ana ekranımızı tam ekran (pencere modu 1) öne getirir ve bu görevi kapatır. Ana ekran her iki
     * sürümde de açıkça adlandırılır (HOME rolü kapalıyken de bizim launcher'ımıza döner).
     */
    private fun goFullScreen() {
        DriveScreen.launchHome(this)
        finishAndRemoveTask()
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Bağlama
    // ------------------------------------------------------------------------------------------------------------------

    private fun bindClock() {
        val now = Date()
        clock.text = DateFormat.getTimeFormat(this).format(now)
        date.text = java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.getDefault()).format(now)
    }

    private fun bindTop() {
        val s = snapshot
        val range = if (s != null && s.connected) com.mapcontrol.vehicle.VehicleRange.stableOrNull(this, s) else null
        val fuel = s?.fuelPercentOrNull()
        val low = trip.lowFuel == true
        val key = Triple(range, fuel, low)
        if (key == lastTop) return
        lastTop = key
        // Okunamayan değer tireyle yer tutmaz, gizlenir (veri yokluğunu sahnedeki tek "Araç verisi bekleniyor" satırı söyler).
        rangeBlock.visibility = if (range == null) View.GONE else View.VISIBLE
        fuelBlock.visibility = if (fuel == null) View.GONE else View.VISIBLE
        divider.visibility = if (range == null && fuel == null) View.INVISIBLE else View.VISIBLE
        rangeValue.text = range?.toString().orEmpty()
        fuelValue.text = fuel?.let { getString(R.string.launcher_percent, it) }.orEmpty()
        val tone = if (low) Sahne.AMBER else Sahne.NUMERAL
        rangeValue.setTextColor(tone)
        fuelValue.setTextColor(tone)
        gauge.set(fuel, low)
        if (range != null) rangeBlock.contentDescription = getString(R.string.launcher_mini_range) + ", " + getString(R.string.launcher_range_value, range)
        fuelBlock.contentDescription = getString(R.string.launcher_mini_fuel) + ", " + fuelValue.text
        layoutNow?.let { applyFuelMargin(it) }
    }

    /** Yolculuk paneli: veri yokken gizli (tire yok); görünürlüğü değişince yan sütun yeniden bölünür. */
    private fun bindTrip() {
        if (!::tripPanel.isInitialized) return
        val content = TripHero.content(trip.connected, trip.tripKm)
        val d = trip
        val minutes = TripTotals.drivingMinutes(d.drivingRunMin, d.drivingResetMin, d.drivingAltMin, d.engineOnMin)
        val shown = MiniSide.tripShown(content)
        if (shown) tripPanel.bind(content, d, minutes)
        if (shown != tripShownNow) {
            tripShownNow = shown
            applySide()
        }
    }

    /**
     * Yan sütun: yolculuk kalan yüksekliği alır, telefon doğal yüksekliğinde altta ([MiniSide.phoneDp]). Yolculuk gizliyken
     * telefon üstte kalır (boş kutu büyütülmez).
     */
    private fun applySide() {
        val m = layoutNow ?: return
        val v = phoneView ?: MiniSide.phone(PhoneSnapshot.EMPTY)
        val trip = tripShownNow == true
        tripPanel.view.visibility = if (trip) View.VISIBLE else View.GONE
        (phonePanel.view.layoutParams as? LinearLayout.LayoutParams)?.apply {
            height = dp(MiniSide.phoneDp(v, m))
            topMargin = if (trip) dp(m.gapDp) else 0
            phonePanel.view.layoutParams = this
        }
    }

    private fun bindNav() {
        val h = navHint
        if (layoutNow?.showNav != true || !MiniNav.shown(h) || h == null) {
            navRow.visibility = View.GONE
            return
        }
        navManeuver.text = h.maneuver.orEmpty()
        val d = MiniNav.distance(h.distanceM)
        navDistance.text = d?.let { (v, km) -> getString(if (km) R.string.launcher_mini_nav_km else R.string.launcher_mini_nav_m, v) }.orEmpty()
        navDistance.visibility = if (d == null) View.GONE else View.VISIBLE
        val eta = MiniNav.arrival(h.arrivalMinutesOfDay, DateFormat.is24HourFormat(this))
        navEta.text = eta?.let { getString(R.string.launcher_mini_nav_eta, it) }.orEmpty()
        navEta.visibility = if (eta == null) View.GONE else View.VISIBLE
        navRow.visibility = View.VISIBLE
    }

    private fun currentMedia(): MediaState = when {
        demoIdle -> MediaState(false, true, false, null, null, null, 0L, 0L, null)
        LauncherDemo.enabled -> LauncherDemo.media()
        else -> media.currentState()
    }

    private data class MediaUi(val mode: Int, val title: String, val artist: String, val playing: Boolean, val art: Bitmap?)

    /**
     * Oturum varken ince şerit (kapak, başlık / sanatçı, önceki / oynat / sonraki); yokken şerit kalkar, üstte "Müzik aç"
     * düğmesi görünür (dokununca son kaynak, yoksa müzik uygulaması seçimi; izin yoksa izin ayarı).
     */
    private fun bindMedia(state: MediaState) {
        val mode = when {
            !state.notificationAccessGranted -> MODE_NO_ACCESS
            !state.sessionAvailable -> MODE_IDLE
            else -> MODE_SESSION
        }
        val ui = if (mode == MODE_SESSION) MediaUi(mode, state.title.orEmpty().ifBlank { getString(R.string.launcher_media_title_neutral) },
            state.artist.orEmpty(), state.playing, state.artwork) else MediaUi(mode, "", "", false, null)
        if (ui == lastMedia) return
        lastMedia = ui
        val session = mode == MODE_SESSION
        mediaStrip.visibility = if (session) View.VISIBLE else View.GONE
        musicPill.visibility = if (session) View.GONE else View.VISIBLE
        musicPill.contentDescription = getString(if (mode == MODE_NO_ACCESS) R.string.launcher_media_access_hint else R.string.launcher_mini_open_music)
        title.text = ui.title
        artist.text = ui.artist
        artist.visibility = if (ui.artist.isEmpty()) View.GONE else View.VISIBLE
        playIcon.setImageResource(if (ui.playing) R.drawable.ic_sahne_pause else R.drawable.ic_sahne_play)
        play.contentDescription = getString(if (ui.playing) R.string.launcher_media_pause else R.string.launcher_media_play)
        if (ui.art != null) {
            artwork.setImageBitmap(ui.art)
            artwork.visibility = View.VISIBLE
        } else {
            artwork.setImageDrawable(null)
            artwork.visibility = View.GONE
        }
        // Sahnenin ortam ışığı kapak renginden (ana ekrandaki medya kartıyla aynı yol).
        if (session) SahneCover.publish(ui.art)
        mediaStrip.contentDescription = "${ui.title}, ${ui.artist}"
    }

    private fun onPlay() {
        when (lastMedia?.mode) {
            MODE_SESSION -> media.playPause()
            else -> openMusic()
        }
    }

    /** Son müzik kaynağı; hiç yoksa sistemin müzik uygulaması seçimi. İzin yoksa izin ayarı. */
    private fun openMusic() {
        if (lastMedia?.mode == MODE_NO_ACCESS) {
            media.openNotificationAccessSettings()
            return
        }
        if (media.launchLastSource()) return
        try {
            startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: RuntimeException) {
            Log.w(TAG, "müzik uygulaması açılamadı", e)
        }
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Kurulum
    // ------------------------------------------------------------------------------------------------------------------

    private fun build(): View {
        root = FrameLayout(this).apply { background = SahneGround() }
        column = group(LinearLayout.VERTICAL)
        root.addView(column, FrameLayout.LayoutParams(MATCH, MATCH))
        info = group(LinearLayout.HORIZONTAL)
        stats = group(LinearLayout.HORIZONTAL).apply { gravity = Gravity.CENTER_VERTICAL }
        pills = group(LinearLayout.HORIZONTAL).apply { gravity = Gravity.CENTER_VERTICAL }
        body = group(LinearLayout.HORIZONTAL)
        side = group(LinearLayout.VERTICAL)

        // Saat, menzil, yakıt: eşit genişlikli rakamlar, değer değişince satır kaymaz.
        clock = sahneText(this, 60f, Sahne.NUMERAL, 300, -0.02f).apply { includeFontPadding = false; fontFeatureSettings = "tnum" }
        date = sahneText(this, 20f, Sahne.HEADER_SUB, 500).apply { maxLines = 1 }
        divider = View(this).apply { setBackgroundColor(Sahne.HAIR) }
        rangeValue = sahneText(this, 38f, Sahne.NUMERAL, 300, -0.02f).apply { includeFontPadding = false; fontFeatureSettings = "tnum" }
        rangeUnit = sahneText(this, 19f, Sahne.INK_2, 500).apply { setText(R.string.launcher_sahne_km) }
        rangeBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = true
                addView(rangeValue, LinearLayout.LayoutParams(WRAP, WRAP))
                addView(rangeUnit, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(6) })
            }, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(label(R.string.launcher_mini_range), LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(4) })
        }
        fuelValue = sahneText(this, 38f, Sahne.NUMERAL, 300, -0.02f).apply { includeFontPadding = false; fontFeatureSettings = "tnum" }
        gauge = SahneStage.FuelGauge(this)
        fuelBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(fuelValue, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(gauge, LinearLayout.LayoutParams(dp(FUEL_BAR_DP), dp(6)).apply { marginEnd = dp(10) })
                addView(label(R.string.launcher_mini_fuel))
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(4) })
        }
        fullPill = Pill(this, R.drawable.ic_sahne_home, getString(R.string.launcher_mini_fullscreen)) { goFullScreen() }
        musicPill = Pill(this, R.drawable.ic_sahne_note, getString(R.string.launcher_mini_open_music)) { openMusic() }
        mapPill = Pill(this, R.drawable.ic_sahne_expand, getString(R.string.launcher_drive_map_big)) { goMapFullScreen() }
        mapPill.contentDescription = getString(R.string.launcher_drive_map_full)
        mapPill.visibility = View.GONE

        // Ana ekranın araç sahnesi: menzil omzu burada üst satırda olduğu için gizli; kilit hapı ve uyarı şeridi aynen.
        stage = SahneStage(this).apply { showRange = false; hideLockWhileWaiting = true }

        // Medya şeridi (yalnızca oturum varken).
        artwork = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        artworkBox = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF3A1D4F.toInt(), 0xFF7A2F57.toInt(), 0xFFD0654A.toInt())).apply { cornerRadius = dpF(COVER_RADIUS_DP) }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, outline: Outline) = outline.setRoundRect(0, 0, v.width, v.height, dpF(COVER_RADIUS_DP))
            }
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_sahne_note)
                setTint(0xD1FFFFFF.toInt())
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
            addView(artwork, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        title = sahneText(this, 22f, Sahne.INK, 600).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        artist = sahneText(this, 18f, Sahne.INK_2, 400).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        val meta = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(artist, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(2) })
            isClickable = true
            setOnClickListener { media.launchLastSource() }
        }
        prev = roundButton(R.drawable.ic_sahne_prev, R.string.launcher_media_prev, primary = false) { media.skipToPrevious() }
        play = roundButton(R.drawable.ic_sahne_play, R.string.launcher_media_play, primary = true) { onPlay() }
        playIcon = play.getChildAt(0) as ImageView
        next = roundButton(R.drawable.ic_sahne_next, R.string.launcher_media_next, primary = false) { media.skipToNext() }
        mediaStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GlassDrawable(this@MiniDashActivity, Sahne.PANEL_RADIUS_DP)
            addView(artworkBox, LinearLayout.LayoutParams(dp(80), dp(80)).apply { marginEnd = dp(16) })
            addView(meta, LinearLayout.LayoutParams(0, MATCH, 1f))
            addView(prev, LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginStart = dp(12) })
            addView(play, LinearLayout.LayoutParams(dp(80), dp(80)).apply { marginStart = dp(10); marginEnd = dp(10) })
            addView(next, LinearLayout.LayoutParams(dp(64), dp(64)))
            visibility = View.GONE
        }

        // Rota satırı (yalnızca tam ekranda, rota varken).
        navManeuver = sahneText(this, 20f, Sahne.INK, 500).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        navDistance = sahneText(this, 26f, Sahne.NUMERAL, 500)
        navEta = sahneText(this, 18f, Sahne.INK_2, 500)
        navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GlassDrawable(this@MiniDashActivity, Sahne.CONTROL_RADIUS_DP, shadow = 0f)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            minimumHeight = dp(MiniLayout.NAV_ROW_DP)
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_sahne_route)
                setTint(Sahne.ICE)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(14) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    isBaselineAligned = true
                    addView(navDistance)
                    addView(navEta, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(12) })
                })
                addView(navManeuver)
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            visibility = View.GONE
        }

        // Yan sütun: yolculuk ve telefon.
        tripPanel = MiniTripPanel(this)
        phonePanel = MiniPhonePanel(this) { v -> phoneView = v; applySide() }

        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) relayout(r - l, b - t)
        }
        val c = resources.configuration
        val d = resources.displayMetrics.density
        relayout((c.screenWidthDp * d).toInt(), (c.screenHeightDp * d).toInt())
        return root
    }

    /** Pencere ölçüsü (px) ve başlık örtüşmesinden yerleşimi seçer; değiştiyse uygular. */
    private fun relayout(wPx: Int, hPx: Int) {
        if (wPx <= 0 || hPx <= 0) return
        val d = resources.displayMetrics.density
        val next = MiniLayout.forSize((wPx / d).toInt(), (hPx / d).toInt(), (captionPx / d + 0.5f).toInt())
        if (next == layoutNow) return
        layoutNow = next
        root.post { applyLayout(next) }
    }

    /**
     * Bölünmüş: üst satır (saat | menzil, yakıt | düğmeler), araç | yan sütun (yolculuk + telefon), altta medya şeridi.
     * Geniş: bilgi sütunu (saat, menzil + yakıt, rota, düğmeler) | araç / medya | yan sütun.
     * Görünümler aynıdır; yalnızca ebeveyn ve yerleşim parametreleri değişir.
     */
    private fun arrange(wide: Boolean) {
        if (arrangedWide == wide) return
        arrangedWide = wide
        for (g in listOf(column, info, stats, pills, body, side)) g.removeAllViews()
        stats.orientation = if (wide) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        stats.gravity = if (wide) Gravity.START else Gravity.CENTER_VERTICAL
        (navRow.parent as? ViewGroup)?.removeView(navRow)
        // Boşluk "Müzik aç"ın sağında: o gizliyken "Tam ekran" hizadan kaymaz.
        pills.addView(musicPill, LinearLayout.LayoutParams(WRAP, dp(MiniLayout.MIN_TOUCH_DP)).apply { marginEnd = dp(12) })
        pills.addView(mapPill, LinearLayout.LayoutParams(WRAP, dp(MiniLayout.MIN_TOUCH_DP)).apply { marginEnd = dp(12) })
        pills.addView(fullPill, LinearLayout.LayoutParams(WRAP, dp(MiniLayout.MIN_TOUCH_DP)))
        side.addView(tripPanel.view, LinearLayout.LayoutParams(MATCH, 0, 1f))
        side.addView(phonePanel.view, LinearLayout.LayoutParams(MATCH, dp(MiniLayout.MIN_TOUCH_DP)))
        if (!wide) {
            column.orientation = LinearLayout.VERTICAL
            info.orientation = LinearLayout.HORIZONTAL
            info.gravity = Gravity.CENTER_VERTICAL
            info.addView(clock, LinearLayout.LayoutParams(WRAP, WRAP))
            info.addView(divider, LinearLayout.LayoutParams(dp(1), dp(44)))
            stats.addView(rangeBlock, LinearLayout.LayoutParams(WRAP, WRAP))
            stats.addView(fuelBlock, LinearLayout.LayoutParams(WRAP, WRAP))
            info.addView(stats, LinearLayout.LayoutParams(0, WRAP, 1f))
            info.addView(pills, LinearLayout.LayoutParams(WRAP, WRAP))
            column.addView(info, LinearLayout.LayoutParams(MATCH, 0))
            body.orientation = LinearLayout.HORIZONTAL
            body.addView(stage, LinearLayout.LayoutParams(0, MATCH, 1f))
            body.addView(side, LinearLayout.LayoutParams(0, MATCH))
            column.addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))
            column.addView(mediaStrip, LinearLayout.LayoutParams(MATCH, 0))
        } else {
            column.orientation = LinearLayout.HORIZONTAL
            info.orientation = LinearLayout.VERTICAL
            info.gravity = Gravity.START
            info.addView(clock, LinearLayout.LayoutParams(WRAP, WRAP))
            info.addView(date, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(8) })
            // Tarih yalnız büyük geniş pencerede (applyLayout: MiniLayout.showDate).
            stats.addView(rangeBlock, LinearLayout.LayoutParams(WRAP, WRAP))
            stats.addView(fuelBlock, LinearLayout.LayoutParams(WRAP, WRAP))
            info.addView(stats, LinearLayout.LayoutParams(WRAP, WRAP))
            info.addView(navRow, LinearLayout.LayoutParams(MATCH, WRAP))
            info.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
            info.addView(pills, LinearLayout.LayoutParams(WRAP, WRAP))
            column.addView(info, LinearLayout.LayoutParams(0, MATCH))
            body.orientation = LinearLayout.VERTICAL
            body.addView(stage, LinearLayout.LayoutParams(MATCH, 0, 1f))
            body.addView(mediaStrip, LinearLayout.LayoutParams(MATCH, 0))
            column.addView(body, LinearLayout.LayoutParams(0, MATCH, 1f))
            column.addView(side, LinearLayout.LayoutParams(0, MATCH))
        }
    }

    private fun applyLayout(m: MiniLayout) {
        arrange(m.wide)
        val p = dp(m.paddingDp)
        val g = dp(m.gapDp)
        column.setPadding(p, p + captionPx, p, p)
        clock.textSize = m.clockSp
        rangeValue.textSize = m.valueSp
        fuelValue.textSize = m.valueSp
        rangeUnit.textSize = m.valueSp / 2f
        applyPillText(m)
        lp(side).apply { width = dp(m.sideDp) }
        lp(mediaStrip).apply { height = dp(m.mediaDp) }
        mediaStrip.setPadding(dp(if (m.coverDp > 0) 12 else 20), 0, dp(12), 0)
        artworkBox.visibility = if (m.coverDp > 0) View.VISIBLE else View.GONE
        lp(artworkBox).apply { width = dp(m.coverDp); height = dp(m.coverDp) }
        lp(prev).apply { width = dp(m.skipDp); height = dp(m.skipDp) }
        lp(next).apply { width = dp(m.skipDp); height = dp(m.skipDp) }
        lp(play).apply { width = dp(m.playDp); height = dp(m.playDp) }
        tripPanel.setSizes(m)
        phonePanel.setSizes(m)
        applyFuelMargin(m)
        if (!m.wide) {
            lp(info).apply { height = dp(m.headerDp) }
            lp(divider).apply { marginStart = dp(if (m.pillText) 24 else 16); marginEnd = dp(if (m.pillText) 24 else 16) }
            lp(body).apply { topMargin = g }
            lp(side).apply { marginStart = g }
            lp(mediaStrip).apply { topMargin = g }
            lp(rangeBlock).apply { marginStart = 0 }
        } else {
            lp(info).apply { width = dp(m.infoDp) }
            // Ölçüler MiniLayout.infoNeedDp ile aynı: kısa geniş pencerede sütun taşmaz.
            date.visibility = if (m.showDate) View.VISIBLE else View.GONE
            lp(stats).apply { topMargin = dp(m.infoGapDp) }
            lp(navRow).apply { topMargin = dp(MiniLayout.NAV_GAP_DP) }
            lp(body).apply { marginStart = g }
            lp(side).apply { marginStart = g }
            lp(mediaStrip).apply { topMargin = g }
        }
        bindNav()
        applySide()
        bindTrip()
        column.requestLayout()
    }

    /** Bölünmüşte menzil ve yakıt yan yana; genişte üst üste. Menzil gizliyken yakıt satır başına geçer. */
    private fun applyFuelMargin(m: MiniLayout) {
        // Yerleşim henüz uygulanmadıysa (ilk onStart) blok ebeveynsiz: applyLayout zaten çağırır.
        if (fuelBlock.layoutParams !is ViewGroup.MarginLayoutParams) return
        val first = rangeBlock.visibility == View.GONE
        lp(fuelBlock).apply {
            marginStart = if (m.wide || first) 0 else dp(if (m.pillText) 28 else 20)
            topMargin = if (m.wide && !first) dp(m.infoGapDp - 6) else 0
        }
        fuelBlock.requestLayout()
    }

    private fun lp(v: View): ViewGroup.MarginLayoutParams = v.layoutParams as ViewGroup.MarginLayoutParams

    private fun group(orientation: Int) = LinearLayout(this).apply {
        this.orientation = orientation
        clipChildren = false
        clipToPadding = false
    }

    private fun label(res: Int) = sahneText(this, MiniLayout.INFO_LABEL_SP, Sahne.INK_3, 500).apply { setText(res) }

    private fun roundButton(icon: Int, label: Int, primary: Boolean, onClick: () -> Unit): FrameLayout = FrameLayout(this).apply {
        background = if (primary) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Sahne.CTA_TOP, Sahne.CTA_BOTTOM)).apply {
            shape = GradientDrawable.OVAL
            if (Sahne.CTA_RING != 0) setStroke(dp(1), Sahne.CTA_RING)
        } else GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Sahne.SOFT_FILL)
            setStroke(dp(1), Sahne.HAIR)
        }
        contentDescription = getString(label)
        isClickable = true
        isFocusable = true
        val iconDp = if (primary) 32 else 28
        addView(ImageView(context).apply {
            setImageResource(icon)
            setTint(if (primary) Sahne.CTA_INK else Sahne.INK)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER))
        setOnClickListener { onClick() }
        pressFeedback()
    }

    private fun dp(v: Int) = (this as Context).dp(v)
    private fun dpF(v: Float) = (this as Context).dpF(v)

    /** Cam hap düğme (simge + yazı; dar pencerede yalnızca simge, 64 dp kare). */
    private class Pill(context: Context, icon: Int, text: String, onTap: () -> Unit) : LinearLayout(context) {
        private val label = sahneText(context, 20f, Sahne.INK, 500).apply { this.text = text; maxLines = 1 }
        private val iconView = ImageView(context).apply {
            setImageResource(icon)
            setTint(Sahne.CONTROL_ICON)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            background = GlassDrawable(context, Sahne.PILL_RADIUS_DP, shadow = 0.6f, control = true)
            minimumWidth = context.dp(MiniLayout.MIN_TOUCH_DP)
            addView(iconView, LayoutParams(context.dp(26), context.dp(26)))
            addView(label, LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(10) })
            contentDescription = text
            isClickable = true
            isFocusable = true
            setOnClickListener { onTap() }
            pressFeedback()
        }

        fun setText(text: String) {
            if (label.text == text) return
            label.text = text
            contentDescription = text
        }

        fun setTextShown(shown: Boolean) {
            label.visibility = if (shown) VISIBLE else GONE
            if (shown) setPadding(context.dp(20), 0, context.dp(24), 0) else setPadding(0, 0, 0, 0)
        }
    }

    companion object {
        private const val TAG = "MiniDash"
        private const val MODE_NO_ACCESS = 0
        private const val MODE_IDLE = 1
        private const val MODE_SESSION = 2
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val FUEL_BAR_DP = 64
        private const val COVER_RADIUS_DP = 14f

        /** Demo navigasyon satırı: `--ez launcher_mini_demo_nav true` (yalnızca demo açıkken). */
        const val EXTRA_DEMO_NAV = "launcher_mini_demo_nav"
        /** Demo: çalan bir şey yok (daraltılmış medya): `--ez launcher_mini_demo_idle true` (yalnızca demo açıkken). */
        const val EXTRA_DEMO_IDLE = "launcher_mini_demo_idle"

        /**
         * Navigasyon kaynağı: Yandex rehberlik bildirimi ([NavigationMiniSource] → [NavigationSource]; rota yokken satır gizli).
         * Demo: `--ez launcher_mini_demo_nav true` sabit ipucu, `--ez launcher_demo_nav true` ana ekrandaki demo rotası.
         */
        internal var navSourceFactory: (Context, Intent?) -> MiniNavSource = { context, intent ->
            if (LauncherDemo.enabled && intent?.getBooleanExtra(EXTRA_DEMO_NAV, false) == true) MiniNavSource.DEMO else NavigationMiniSource(context)
        }
    }
}
