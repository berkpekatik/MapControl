package com.mapcontrol.launcher

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Display
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.mapcontrol.R
import com.mapcontrol.util.AppLaunchHelper
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sahne Navigasyon paneli. Üç hâl:
 *  - Rota var ([NavigationState.active]): büyük manevra oku (kendi vektör çizimimiz, [ManeuverArrowView]), büyük mesafe,
 *    sokak adı ve altta "Varış 14:52 · 18 km". Dokununca sürüş ekranı açılır ([DriveScreen]: Mini gösterge + seçili harita, bölünmüş;
 *    MapControl'sız derlemede harita tam ekran).
 *  - Rota yok: başlık, iki büyük kısayol "Ev" / "İş" (konum ayarlıysa seçili uygulamada rota kurar; uzun basınca konum
 *    ayarı) ve "Haritayı aç".
 * Başlığın sağındaki küçük "Google ▾" / "Yandex ▾" çipi harita uygulamasını seçer ([MapAppPrefs]; yalnız kurulu olanlar
 * listelenir). Seçim hem açılan uygulamayı hem kartın hangi bildirimi okuduğunu belirler.
 *  - Bildirim erişimi yok: kısa Türkçe ileti ve sistem bildirim erişimi ayarını açan düğme (izin kodla verilmez).
 * Veri [NavigationSource]'tan, olay güdümlü (yoklama yok).
 */
internal class NavigationPanel(private val context: Context) : SizedWidget {

    private enum class Mode { ROUTE, IDLE, NO_ACCESS }

    private var mode: Mode? = null
    private var visible = false
    private var current = NavigationState.NONE
    private val io = LauncherIo.serial()
    /** Sürüş ekranı (ve harita yokken seçicisi) bu derlemede var mı (MapControl). */
    private val drivePicker by lazy { LauncherEdition.mapcontrol(context).available }
    /** Etkin harita uygulaması (kurulu seçenek yoksa null); görünür olunca ve seçim değişince yenilenir. */
    private var mapApp: MapApp? = null
    private var mapChoices: List<MapApp> = emptyList()

    /** Başlıktaki seçim çipi: "Google ▾". Tek seçenek varsa ok yok, dokunulmaz. */
    private val appChip = sahneText(context, SahneType.SUB, Sahne.INK_3, 500).apply {
        maxLines = 1
        val p = context.dp(10)
        setPadding(p, context.dp(6), p, context.dp(6))
        background = rounded(context, Sahne.SOFT_FILL, Sahne.CONTROL_RADIUS_DP, Sahne.HAIR_2)
        setOnClickListener { chooseMapApp() }
    }

    private val listener: (NavigationState) -> Unit = { s ->
        current = s
        bind()
    }

    // --- Başlık ---
    private val head = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_sahne_nav)
            imageTintList = ColorStateList.valueOf(Sahne.INK_2)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(24), context.dp(24)))
        addView(sahneText(context, SahneType.HEAD, Sahne.INK_2, 500).apply { setText(R.string.launcher_widget_navigation) },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(10) })
        addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        addView(appChip, LinearLayout.LayoutParams(WRAP, WRAP))
    }

    // --- Rota ---
    private val arrow = ManeuverArrowView(context)
    private val distance = sahneText(context, SahneType.HERO, Sahne.NUMERAL, 300, -0.03f).apply { maxLines = 1 }
    private val street = sahneText(context, STREET_SP, Sahne.INK, 500).apply {
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    private val arrival = sahneText(context, SahneType.SUB, Sahne.INK_3, 400).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    /** "Bitir" hapı: bildirimin bitirme eylemi varsa görünür; dokunuşu PendingIntent'i gönderir (uygulama öldürülmez). */
    private val endButton = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        val p = context.dp(16)
        setPadding(p, 0, p, 0)
        minimumHeight = context.dp(END_DP)
        background = rounded(context, Sahne.SOFT_FILL, Sahne.CONTROL_RADIUS_DP, Sahne.HAIR_2)
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_sahne_flag)
            imageTintList = ColorStateList.valueOf(Sahne.INK_2)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(22), context.dp(22)))
        addView(sahneText(context, SahneType.SUB, Sahne.INK_2, 600).apply { setText(R.string.launcher_nav_end) },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(8) })
        visibility = View.GONE
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.launcher_nav_end)
        setOnClickListener { endNavigation() }
        pressFeedback()
    }
    private val arrivalRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(arrival, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(endButton, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(12) })
    }
    private val arrivalDivider = View(context).apply { setBackgroundColor(Sahne.HAIR_2) }
    private val routeBox = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(arrow, LinearLayout.LayoutParams(context.dp(ARROW_DP), context.dp(ARROW_DP)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(distance, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP))
                addView(street, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP).apply { topMargin = context.dp(2) })
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = context.dp(24) })
        })
        addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        addView(arrivalDivider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(1)))
        addView(arrivalRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP).apply { topMargin = context.dp(10) })
        isClickable = true
        setOnClickListener { openMap() }
        pressFeedback()
    }

    // --- Rota yok ---
    private val homeTile = PlaceTile(NavPlaces.HOME, R.drawable.ic_sahne_home, R.string.launcher_nav_home)
    private val workTile = PlaceTile(NavPlaces.WORK, R.drawable.ic_sahne_work, R.string.launcher_nav_work)
    private val mapButton = ctaButton(R.string.launcher_nav_open_map) { openMap() }
    private val idleBox = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(homeTile.view, LinearLayout.LayoutParams(0, context.dp(TILE_DP), 1f))
            addView(workTile.view, LinearLayout.LayoutParams(0, context.dp(TILE_DP), 1f).apply { marginStart = context.dp(14) })
        })
        addView(mapButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(BUTTON_DP)).apply { topMargin = context.dp(14) })
    }

    // --- Erişim yok ---
    private val accessBox = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        addView(sahneText(context, MESSAGE_SP, Sahne.INK_2, 400).apply {
            setText(R.string.launcher_nav_access_message)
            maxLines = 3
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP))
        addView(ctaButton(R.string.launcher_nav_access_button) { openAccessSettings() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(BUTTON_DP)).apply { topMargin = context.dp(18) })
        addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
    }

    private val body = FrameLayout(context).apply {
        addView(routeBox, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(idleBox, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(accessBox, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(30), context.dp(24), context.dp(30), context.dp(22))
        addView(head)
        addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = context.dp(12) })
    }

    init {
        refreshMapApp()
        bind()
    }

    override val view: View get() = root
    override fun applySpan(span: Span) = Unit

    /** Ev / İş telefondan değişti: kutular hemen yenilenir. */
    private val placesListener: () -> Unit = {
        homeTile.bind()
        workTile.bind()
    }

    override fun onVisible() {
        visible = true
        refreshMapApp()
        NavigationSource.addListener(listener)
        NavPlaces.addListener(placesListener)
        placesListener() // gizliyken değişmiş olabilir
    }

    override fun onHidden() {
        visible = false
        NavigationSource.removeListener(listener)
        NavPlaces.removeListener(placesListener)
    }

    override fun release() {
        NavigationSource.removeListener(listener)
        NavPlaces.removeListener(placesListener)
    }

    private fun bind() {
        val s = current
        val next = when {
            s.active -> Mode.ROUTE
            !NavigationSource.hasAccess(context) -> Mode.NO_ACCESS
            else -> Mode.IDLE
        }
        if (next != mode) {
            mode = next
            routeBox.visibility = if (next == Mode.ROUTE) View.VISIBLE else View.GONE
            idleBox.visibility = if (next == Mode.IDLE) View.VISIBLE else View.GONE
            accessBox.visibility = if (next == Mode.NO_ACCESS) View.VISIBLE else View.GONE
        }
        when (next) {
            Mode.ROUTE -> bindRoute(s)
            Mode.IDLE -> bindIdle()
            Mode.NO_ACCESS -> root.contentDescription = context.getString(R.string.launcher_nav_access_message)
        }
    }

    private fun bindRoute(s: NavigationState) {
        arrow.set(s.maneuver, s.roundaboutExit)
        val d = s.distance
        distance.visibility = if (d == null) View.GONE else View.VISIBLE
        if (d != null) distance.text = withUnit(context, d.text, SahneType.HERO_UNIT)
        val exit = s.roundaboutExit?.let { context.getString(R.string.launcher_nav_exit, it) }
        val streetLine = listOfNotNull(exit, s.street).joinToString(" · ")
        street.text = streetLine
        street.visibility = if (streetLine.isEmpty()) View.GONE else View.VISIBLE
        val bottom = arrivalLine(s)
        arrival.text = bottom
        arrival.visibility = if (bottom.isEmpty()) View.GONE else View.VISIBLE
        endButton.visibility = if (s.hasEnd) View.VISIBLE else View.GONE
        arrivalDivider.visibility = if (arrival.visibility == View.VISIBLE || s.hasEnd) View.VISIBLE else View.GONE
        root.contentDescription = listOfNotNull(d?.text, streetLine.ifEmpty { null }, bottom.ifEmpty { null }).joinToString(", ")
    }

    /**
     * Bildirimin bitirme eylemini gönderir; rota bildirimi kalkınca kart kendiliğinden boşa döner. Eylem yoksa/iptalse
     * sessizce geçer. Demo rotada yalnız demo rota kapatılır (kart gerçek duruma döner).
     */
    private fun endNavigation() {
        if (current.demoEnd) {
            NavigationSource.setDemo(false)
            return
        }
        val action = current.endAction ?: return
        try {
            action.send()
        } catch (e: android.app.PendingIntent.CanceledException) {
        }
    }

    /** "Varış 14:52 · 18 km · 25 dk" (eksik parçalar düşer). */
    private fun arrivalLine(s: NavigationState): String = listOfNotNull(
        s.arrivalTime?.let { context.getString(R.string.launcher_nav_arrival, it) },
        s.remainingDistance?.text,
        s.remainingMinutes?.let { m ->
            if (m >= 60) context.getString(R.string.launcher_break_hm, m / 60, m % 60) else context.getString(R.string.launcher_break_m, m)
        },
    ).joinToString(" · ")

    private fun bindIdle() {
        homeTile.bind()
        workTile.bind()
        val installed = driveMap() != null
        // Harita yoksa düğme "+ Harita ekle" seçicisini açar (MapControl varken).
        val canPick = !installed && drivePicker
        (mapButton.getChildAt(1) as TextView).setText(when {
            installed -> R.string.launcher_nav_open_map
            canPick -> R.string.launcher_drive_add_map
            else -> R.string.launcher_nav_not_installed
        })
        mapButton.alpha = if (installed || canPick) 1f else DISABLED_ALPHA
        root.contentDescription = context.getString(R.string.launcher_widget_navigation)
    }

    // --- Harita uygulamasına geçiş ---

    /** Seçimi (kurulu set + MapControl hedefi + kayıt) yeniden okur; çipi ve kartın bildirim kaynağını günceller. */
    private fun refreshMapApp() {
        val installed = MapAppPrefs.installedSet(context)
        mapChoices = MapAppChoice.choices(installed)
        mapApp = MapAppPrefs.current(context)
        NavigationSource.setMapApp(mapApp)
        bindChip()
    }

    private fun bindChip() {
        val app = mapApp
        appChip.visibility = if (app == null) View.GONE else View.VISIBLE
        if (app == null) return
        val many = mapChoices.size > 1
        appChip.text = if (many) app.label + " \u25BE" else app.label
        appChip.isClickable = many
        appChip.isFocusable = many
        // Tek seçenekte basma animasyonu da olmasın (dokunulmaz görünsün).
        if (many) appChip.pressFeedback() else appChip.setOnTouchListener(null)
        appChip.contentDescription = context.getString(R.string.launcher_nav_map_app_title) + ": " + app.label
    }

    /** Kurulu seçenekler arasında seçim (tek seçim penceresi). */
    private fun chooseMapApp() {
        val choices = mapChoices
        if (choices.size < 2) return
        val checked = choices.indexOf(mapApp)
        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.launcher_nav_map_app_choose)
            .setSingleChoiceItems(choices.map { it.fullName }.toTypedArray(), checked) { d, which ->
                choices.getOrNull(which)?.let { MapAppPrefs.set(context, it) }
                d.dismiss()
                refreshMapApp()
                bind()
            }
            .setNegativeButton(R.string.launcher_nav_place_cancel, null)
            .show()
    }

    /** Açılacak paket: rota sürüyorsa rotayı gönderen (seçili uygulamanınkidir), değilse seçili uygulama. */
    private fun mapPackage(): String? {
        val src = current.sourcePackage
        if (current.active && src != null && installed(src)) return src
        return mapApp?.packageName?.takeIf { installed(it) }
    }

    private fun installed(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    /** Sürüş ekranının harita yarısı: kartın paketi, yoksa seçicide seçilen diğer uygulama; ikisi de yoksa null (seçici). */
    private fun driveMap(): String? = DrivePolicy.mapSlot(mapPackage(), DriveMapPrefs.other(context), ::installed)

    /**
     * Kart / "Haritayı aç": harita her zaman bölmesiz, tam ekran açılır (her araçta aynı davranış; sürüş ekranı bölmesi
     * "Bölünmüş ekran" kutusunda). Harita seçili değilse "+ Harita ekle" seçicisi kendi tam ekranında açılır.
     */
    private fun openMap() {
        val pkg = driveMap()
        if (pkg == null) {
            if (drivePicker) openMapPicker()
            return
        }
        openMapFull(pkg)
    }

    /**
     * Tam ekran: MapControl varsa sürüş ekranından yarım kalmış harita penceresi de tam ekrana taşınır (yoksa Android eski yarım
     * pencereyi öne getirirdi); MapControl yoksa düz başlatma.
     */
    private fun openMapFull(pkg: String?) {
        pkg ?: return
        if (drivePicker) DriveScreen.openFullScreen(context, pkg)
        else io.runSafely { AppLaunchHelper.launchAppOnDisplay(context, pkg, Display.DEFAULT_DISPLAY, 0) }
    }

    /** "+ Harita ekle": seçici bölmesiz açılır; seçilen harita seçicinin (tam ekran) penceresinde başlar. */
    private fun openMapPicker() {
        try {
            context.startActivity(Intent().setClassName(context.packageName, com.mapcontrol.util.SplitSlot.MAP_PICKER_CLASS)
                .putExtra(com.mapcontrol.ui.activity.DriveMapPickerActivity.EXTRA_FULL_SCREEN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            android.util.Log.w("NavigationPanel", "map picker: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Ayarlı konuma rota (Yandex: yandexmaps:// / yandexnavi://, Google: google.navigation:, diğer: geo:); konum yoksa [label]
     * araması. Rota sürüş ekranının harita yarısında açılır; MapControl yoksa harita tam ekran.
     */
    private fun openPlace(place: String, label: String) {
        val pkg = driveMap()
        if (pkg == null) {
            openMap()
            return
        }
        val uri = NavPlaces.get(context, place)?.let { (lat, lon) -> NavPlaces.routeUri(pkg, lat, lon) } ?: NavPlaces.searchUri(pkg, label)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        DriveScreen.open(context, pkg, intent) {
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                openMapFull(pkg)
            } catch (e: SecurityException) {
                openMapFull(pkg)
            }
        }
    }

    /** Sistem bildirim erişimi ayarı; kullanıcı kendisi açar. */
    private fun openAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (ignored: Exception) {
            }
        }
    }

    /** Uzun basış: enlem, boylam girişi (yer tutucu ayar; adres araması yok). */
    private fun editPlace(place: String, label: String) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = context.getString(R.string.launcher_nav_place_hint)
            setText(NavPlaces.get(context, place)?.let { (lat, lon) -> NavPlaces.format(lat, lon) }.orEmpty())
            setSingleLine()
        }
        val box = FrameLayout(context).apply {
            val p = context.dp(24)
            setPadding(p, context.dp(8), p, 0)
            addView(input)
        }
        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(context.getString(R.string.launcher_nav_place_title, label))
            .setView(box)
            .setPositiveButton(R.string.launcher_nav_place_save) { _, _ ->
                val ll = NavParse.parseLatLon(input.text?.toString())
                if (ll == null) Toast.makeText(context, R.string.launcher_nav_place_invalid, Toast.LENGTH_SHORT).show()
                // Koordinat değişmediyse telefondan gelen yer adı kalır; elle yeni koordinatın adı yoktur.
                else NavPlaces.set(context, place, ll, NavPlaces.label(context, place).takeIf { NavPlaces.get(context, place) == ll })
                bind()
            }
            .setNeutralButton(R.string.launcher_nav_place_clear) { _, _ ->
                NavPlaces.set(context, place, null)
                bind()
            }
            .setNegativeButton(R.string.launcher_nav_place_cancel, null)
            .show()
    }

    // --- Görünüm parçaları ---

    /** Birincil düğme (medya panelindeki oynat düğmesiyle aynı koyu grafit): ikon + yazı, tam genişlik. */
    private fun ctaButton(label: Int, onClick: () -> Unit): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Sahne.CTA_TOP, Sahne.CTA_BOTTOM),
        ).apply {
            cornerRadius = context.dpF(Sahne.CONTROL_RADIUS_DP)
            if (Sahne.CTA_RING != 0) setStroke(context.dp(1), Sahne.CTA_RING)
        }
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_sahne_nav)
            imageTintList = ColorStateList.valueOf(Sahne.CTA_INK)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(24), context.dp(24)))
        addView(sahneText(context, BUTTON_SP, Sahne.CTA_INK, 600).apply { setText(label) },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(12) })
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        pressFeedback()
    }

    /**
     * "Ev" / "İş" kısayolu: ikon, ad ve alt satır (ayarlıysa "Rota başlat", değilse kısa "Ayarla": uzun ipucu Tiggo 7/8 kutusunda
     * kesiliyordu, 7 Kasım). Ayarsız kutuya dokunmak da konum düzenleyicisini açar (ipucuyla tutarlı); basılı tutmak hep düzenler.
     */
    private inner class PlaceTile(private val place: String, icon: Int, private val label: Int) {
        private val sub = sahneText(context, SahneType.SUB, Sahne.INK_3, 400).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(context, Sahne.SOFT_FILL, Sahne.CONTROL_RADIUS_DP, Sahne.HAIR_2)
            setPadding(context.dp(20), context.dp(14), context.dp(16), context.dp(14))
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(Sahne.INK_2)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(context.dp(30), context.dp(30)))
            addView(sahneText(context, PLACE_SP, Sahne.INK, 600).apply { setText(label) },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(8) })
            addView(sub, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (NavPlaces.get(context, place) == null) editPlace(place, context.getString(label))
                else openPlace(place, context.getString(label))
            }
            setOnLongClickListener { editPlace(place, context.getString(label)); true }
            pressFeedback()
        }

        fun bind() {
            val name = NavPlaces.label(context, place)
            when {
                NavPlaces.get(context, place) == null -> sub.setText(R.string.launcher_nav_set_hint)
                name != null -> sub.text = name // telefondan gelen yer adı
                else -> sub.setText(R.string.launcher_nav_route_hint)
            }
            view.contentDescription = context.getString(label) + ", " + sub.text
        }
    }

    private companion object {
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        const val ARROW_DP = 128
        const val STREET_SP = 26f
        const val MESSAGE_SP = 22f
        const val PLACE_SP = 26f
        const val BUTTON_SP = 22f
        const val TILE_DP = 136
        const val BUTTON_DP = 64
        /** "Bitir" hapının asgari yüksekliği (araç ekranı dokunma hedefi). */
        const val END_DP = 64
        const val DISABLED_ALPHA = 0.45f
    }
}

/**
 * Ev / iş konumları (profil bağımsız) ve harita derin bağlantıları (Yandex, Google). Konum araçtaki kutudan elle ya da
 * telefondan (`PUT /nav/places`, [NavPlacesDoc]) yazılır; telefon yanında yer adını da verir ([label], kartın alt satırı).
 * Araçta elle yazılan koordinatın adı yoktur: eski ad silinir. Değişiklik [addListener] ile ana iş parçacığında bildirilir.
 */
object NavPlaces {
    const val HOME = "home"
    const val WORK = "work"
    private const val PREFS = "launcher_nav"
    private const val LABEL_SUFFIX = "_label"
    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, place: String): Pair<Double, Double>? = NavParse.parseLatLon(prefs(context).getString(place, null))

    fun label(context: Context, place: String): String? = prefs(context).getString(place + LABEL_SUFFIX, null)?.ifEmpty { null }

    fun set(context: Context, place: String, latLon: Pair<Double, Double>?, label: String? = null) {
        val e = prefs(context).edit()
        if (latLon == null) e.remove(place) else e.putString(place, format(latLon.first, latLon.second))
        if (latLon == null || label.isNullOrEmpty()) e.remove(place + LABEL_SUFFIX) else e.putString(place + LABEL_SUFFIX, label)
        e.apply()
        main.post { listeners.forEach { it() } }
    }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    /** Telefona giden belge ([NavPlacesDoc.toJson] girdisi). */
    @JvmStatic
    fun readAll(context: Context): Map<String, NavPlacesDoc.Place?> = NavPlacesDoc.PLACES.associateWith { place ->
        get(context, place)?.let { (lat, lon) -> NavPlacesDoc.Place(lat, lon, label(context, place)) }
    }

    /** Telefondan doğrulanmış değişiklikler ([NavPlacesDoc.parse]); null olan yer silinir. */
    @JvmStatic
    fun writeAll(context: Context, changes: Map<String, NavPlacesDoc.Place?>) {
        for ((place, p) in changes) set(context, place, p?.let { it.lat to it.lon }, p?.label)
    }

    fun format(lat: Double, lon: Double): String = String.format(Locale.US, "%.6f, %.6f", lat, lon)

    /**
     * Yandex Haritalar: `yandexmaps://build_route_on_map?lat_to=..&lon_to=..`; Navigator: `yandexnavi://…` aynı parametrelerle;
     * Google Haritalar: `google.navigation:q=lat,lon` (doğrudan rehberlik).
     */
    fun routeUri(pkg: String, lat: Double, lon: Double): String {
        if (pkg == NavigationSource.GOOGLE_MAPS) return String.format(Locale.US, "google.navigation:q=%.6f,%.6f", lat, lon)
        if (MapApp.fromPackage(pkg) == null) return String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f", lat, lon, lat, lon)
        val scheme = if (pkg == NavigationSource.YANDEX_NAVI) "yandexnavi" else "yandexmaps"
        return String.format(Locale.US, "%s://build_route_on_map?lat_to=%.6f&lon_to=%.6f", scheme, lat, lon)
    }

    /** Arama: Haritalar `yandexmaps://maps.yandex.ru/?text=..`, Navigator `yandexnavi://map_search?text=..`, Google `geo:0,0?q=..`. */
    fun searchUri(pkg: String, text: String): String {
        val q = Uri.encode(text)
        if (pkg == NavigationSource.GOOGLE_MAPS || MapApp.fromPackage(pkg) == null) return "geo:0,0?q=$q"
        return if (pkg == NavigationSource.YANDEX_NAVI) "yandexnavi://map_search?text=$q" else "yandexmaps://maps.yandex.ru/?text=$q"
    }
}

/**
 * Manevra oku: kendi çizimimiz (Yandex çizimleri kopyalanmaz). 100 × 100 birimlik tuvalde kalın yuvarlak uçlu gövde ve dolu
 * ok başı; dönüş açısı türe göre. Yuvarlak kavşakta ince halka, saat yönünün tersine giden yay ve çıkış numarası (sağdan
 * trafik). Bilinmeyen manevrada sönük düz ok. Çizimde ayırma yok (yollar yalnızca tür değişince kurulur).
 */
internal class ManeuverArrowView(context: Context) : View(context) {
    private var maneuver = NavManeuver.UNKNOWN
    private var exit: Int? = null
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = STROKE
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ghost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = GHOST
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 17f
        typeface = SahneFonts.get(context, 700)
    }
    private val body = Path()
    private val head = Path()
    private val ghostPath = Path()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun set(m: NavManeuver, exitNumber: Int?) {
        if (m == maneuver && exitNumber == exit && !body.isEmpty) return
        maneuver = m
        exit = exitNumber
        build()
        invalidate()
    }

    private fun build() {
        body.reset(); head.reset(); ghostPath.reset()
        when (maneuver) {
            NavManeuver.STRAIGHT, NavManeuver.UNKNOWN -> turn(0f)
            NavManeuver.SLIGHT_LEFT -> turn(-45f)
            NavManeuver.SLIGHT_RIGHT -> turn(45f)
            NavManeuver.LEFT -> turn(-90f)
            NavManeuver.RIGHT -> turn(90f)
            NavManeuver.HARD_LEFT -> turn(-135f)
            NavManeuver.HARD_RIGHT -> turn(135f)
            NavManeuver.UTURN_LEFT -> uturn(left = true)
            NavManeuver.UTURN_RIGHT -> uturn(left = false)
            NavManeuver.EXIT_LEFT -> { ghostPath.moveTo(56f, 92f); ghostPath.lineTo(56f, 8f); turn(-40f, x0 = 56f, corner = 52f, len = 24f) }
            NavManeuver.EXIT_RIGHT -> { ghostPath.moveTo(44f, 92f); ghostPath.lineTo(44f, 8f); turn(40f, x0 = 44f, corner = 52f, len = 24f) }
            NavManeuver.ROUNDABOUT_ENTER, NavManeuver.ROUNDABOUT_EXIT -> roundabout()
            NavManeuver.FERRY -> { turn(0f, bottom = 76f); ghostPath.moveTo(18f, 90f); ghostPath.quadTo(34f, 82f, 50f, 90f); ghostPath.quadTo(66f, 98f, 82f, 90f) }
            NavManeuver.FINISH -> finish()
            NavManeuver.WAYPOINT -> waypoint()
        }
    }

    /**
     * Gövde aşağıdan [corner] yüksekliğine çıkar, yumuşak bir dirsekle [deg] yönüne döner (0 yukarı, + sağ) ve ok başıyla
     * biter. Gövde, ok başı ortalansın diye dönüşün tersine kaydırılır.
     */
    private fun turn(deg: Float, x0: Float = 50f - 12f * sin(Math.toRadians(deg.toDouble())).toFloat(), corner: Float = 44f, len: Float = 26f, bottom: Float = 92f) {
        val r = Math.toRadians(deg.toDouble())
        val dx = sin(r).toFloat()
        val dy = -cos(r).toFloat()
        body.moveTo(x0, bottom)
        if (deg == 0f) {
            body.lineTo(x0, 30f)
            arrowHead(x0, 30f, 0f, -1f)
            return
        }
        body.lineTo(x0, corner + 12f)
        val bendX = x0 + dx * 12f
        val bendY = corner + dy * 12f
        body.quadTo(x0, corner, bendX, bendY)
        val ex = bendX + dx * len
        val ey = bendY + dy * len
        body.lineTo(ex, ey)
        arrowHead(ex, ey, dx, dy)
    }

    /** U dönüşü: gövde yukarı, yarım daire, aşağı; ok başı aşağıya. */
    private fun uturn(left: Boolean) {
        val a = if (left) 64f else 36f
        val b = if (left) 34f else 66f
        body.moveTo(a, 92f)
        body.lineTo(a, 42f)
        val rect = RectF(minOf(a, b), 27f, maxOf(a, b), 57f)
        body.arcTo(rect, if (left) 0f else 180f, if (left) -180f else 180f)
        body.lineTo(b, 58f)
        arrowHead(b, 58f, 0f, 1f)
    }

    /** Yuvarlak kavşak: ince halka (merkez 50,48), aşağıdan giriş, saat yönünün tersine yay, çıkış kolu ve ok başı. */
    private fun roundabout() {
        val cx = 50f; val cy = RING_CY; val rr = 18f
        ghostPath.addCircle(cx, cy, rr, Path.Direction.CW)
        val n = (exit ?: 2).coerceIn(1, 4)
        // Android açıları: 0 sağ, 90 aşağı. Giriş altta (90°); 1. çıkış sağ, 2. yukarı, 3. sol, 4. sol-alt.
        val exitAngle = if (n == 4) -225f else 90f - 90f * n
        body.moveTo(cx, 94f)
        body.lineTo(cx, cy + rr)
        body.arcTo(RectF(cx - rr, cy - rr, cx + rr, cy + rr), 90f, exitAngle - 90f)
        val a = Math.toRadians(exitAngle.toDouble())
        val ux = cos(a).toFloat(); val uy = sin(a).toFloat()
                val ex = cx + ux * (rr + 10f); val ey = cy + uy * (rr + 10f)
        body.lineTo(ex, ey)
        arrowHead(ex, ey, ux, uy)
    }

    /** Varış bayrağı: direk ve flama. */
    private fun finish() {
        body.moveTo(34f, 90f)
        body.lineTo(34f, 14f)
        head.moveTo(34f, 14f)
        head.lineTo(76f, 26f)
        head.lineTo(34f, 46f)
        head.close()
        ghostPath.moveTo(22f, 90f)
        ghostPath.lineTo(50f, 90f)
    }

    /** Ara nokta: iğne (halka ve uç). */
    private fun waypoint() {
        body.addCircle(50f, 38f, 18f, Path.Direction.CW)
        body.moveTo(38f, 52f)
        body.lineTo(50f, 86f)
        body.lineTo(62f, 52f)
        head.addCircle(50f, 38f, 6f, Path.Direction.CW)
    }

    /** Dolu ok başı: uç (x, y) + yön × 13, taban yön dikmesi boyunca ± 12. */
    private fun arrowHead(x: Float, y: Float, dx: Float, dy: Float) {
        val px = -dy; val py = dx
        head.moveTo(x + dx * 14f, y + dy * 14f)
        head.lineTo(x + px * 13f - dx * 2f, y + py * 13f - dy * 2f)
        head.lineTo(x - px * 13f - dx * 2f, y - py * 13f - dy * 2f)
        head.close()
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val s = minOf(width, height) / 100f
        canvas.save()
        canvas.translate((width - 100f * s) / 2f, (height - 100f * s) / 2f)
        canvas.scale(s, s)
        val ink = if (maneuver == NavManeuver.UNKNOWN) Sahne.INK_3 else Sahne.NUMERAL
        ghost.color = Sahne.TRACK
        stroke.color = ink
        fill.color = ink
        if (!ghostPath.isEmpty) canvas.drawPath(ghostPath, ghost)
        canvas.drawPath(body, stroke)
        canvas.drawPath(head, fill)
        val n = exit
        if (n != null && (maneuver == NavManeuver.ROUNDABOUT_EXIT || maneuver == NavManeuver.ROUNDABOUT_ENTER)) {
            label.color = Sahne.INK_2
            canvas.drawText(n.toString(), 50f, RING_CY - (label.ascent() + label.descent()) / 2f, label)
        }
        canvas.restore()
    }

    private companion object {
        const val STROKE = 11f
        const val GHOST = 6f
        /** Kavşak halkasının merkezi: yukarı çıkışın ok başı tuvale sığsın. */
        const val RING_CY = 48f
    }
}
