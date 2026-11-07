package com.mapcontrol.launcher

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.desaysv.ivi.extra.project.carinfo.HvacID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.R
import com.mapcontrol.media.LauncherMediaController
import com.mapcontrol.media.LauncherMediaController.MediaState
import com.mapcontrol.util.AppIconHelper
import com.mapcontrol.util.AppLaunchHelper
import com.mapcontrol.util.ProjectionTargetApps

/** Klima okuması: sürücü ve yolcu sıcaklığı (°C) ile fan kademesi; okunamayan alan null. */
internal data class ClimateReadout(
    val connected: Boolean = false,
    val driverTemp: Double? = null,
    val passengerTemp: Double? = null,
    val fan: Int? = null,
)

/**
 * Launcher'ın kendi alt çubuğu ("Alt çubuk: Bizimki"): OEM gezinme çubuğu gizlenirken yerini alır.
 * Tek parça koyu "dock" şeridi, bölümler ince ayraçlarla: ana ekran · uygulamalar · araç (kare düğmeler), medya (kapak,
 * başlık, sanatçı, oynat/duraklat; oturum yokken "Müzik"), sürücü sıcaklığı · fan · yolcu sıcaklığı
 * ([ClimateControl]: sürükle/dokun/uzun bas), ayarlar · telefon (kare düğmeler). Klima yazması araçta doğrulanana
 * kadar kapalı ([ClimateWriter.CLIMATE_WRITE_VERIFIED]): değer kısa süre değişir, sonra araçtakine döner.
 * Okuma 3 sn'de bir, yalnızca ekran görünürken.

 */
class OurBottomBar(
    private val activity: Activity,
    /**
     * Telefon düğmesine dokunuş (ana ekran: telefon sayfası ya da paneli, bkz. HomeLauncherActivity.onPhoneButton); null ise
     * eski davranış (OEM telefon uygulaması). Uzun basış her durumda bağlı telefonun sesli asistanıdır.
     */
    private val onPhoneTap: (() -> Unit)? = null,
) : LauncherWidget {

    private val context: Context = activity
    private val io = LauncherIo.serial()
    private val main = Handler(Looper.getMainLooper())
    private var startup: LauncherStartup.Deferred? = null
    private var readout = ClimateReadout()

    // --- Klima ---
    private val climateControl = ClimateControl(context, io, main)

    /** Çubuğun düğmeleri ve ayraçları (spor görünümü bunların yüzeyini değiştirir); [view]'dan önce kurulmalı. */
    private val barButtons = ArrayList<BarButton>()
    private val barSeps = ArrayList<View>()

    override val view: View = sahneBar()

    /**
     * Çubuk (Sahne taslağı): cam şerit, solda ana ekran · uygulamalar · araç (88 × 80 düğmeler, etkin ana ekran gece beyaz,
     * gündüz grafit), ince çizgilerle ayrılmış sürücü sıcaklığı · fan · yolcu sıcaklığı, sağda ayarlar · telefon.
     * Dokunma davranışları aynı.
     */
    private fun sahneBar(): View = SwipeUpBar(context) { openClimatePanel() }.apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GlassDrawable(context, Sahne.PANEL_RADIUS_DP, control = true)
        setPadding(context.dp(8), 0, context.dp(8), 0)
        fun sep() = View(context).apply { setBackgroundColor(Sahne.CONTROL_BORDER) }.also { barSeps += it }
        fun sepLp() = LinearLayout.LayoutParams(context.dp(1), context.dp(52)).apply { marginStart = context.dp(10); marginEnd = context.dp(10) }
        fun btnLp(first: Boolean) = LinearLayout.LayoutParams(context.dp(88), context.dp(80)).apply { if (!first) marginStart = context.dp(6) }
        addView(sahneButton(R.drawable.ic_sahne_home_fill, R.string.launcher_bar_home, active = true) { leaveToSystemHome() }, btnLp(true))
        addView(sahneButton(R.drawable.ic_sahne_grid, R.string.launcher_bar_apps) { showApps() }, btnLp(false))
        addView(sahneButton(R.drawable.ic_sahne_car, R.string.launcher_vehicle_settings, iconDp = CAR_ICON_DP) { openPackage(VEHICLE_SETTINGS_PACKAGE) }, btnLp(false))
        addView(sep(), sepLp())
        addView(climateControl.driverView, LinearLayout.LayoutParams(0, context.dp(80), 1f))
        addView(sep(), sepLp())
        addView(climateControl.fanView, LinearLayout.LayoutParams(context.dp(330), context.dp(80)))
        addView(sep(), sepLp())
        addView(climateControl.passengerView, LinearLayout.LayoutParams(0, context.dp(80), 1f))
        addView(sep(), sepLp())
        addView(sahneButton(R.drawable.ic_sahne_gear, R.string.launcher_bar_settings) { openAndroidSettings() }, btnLp(true))
        addView(sahneButton(R.drawable.ic_sahne_phone, R.string.launcher_app_phone, onLongClick = ::startPhoneAssistant) {
            onPhoneTap?.invoke() ?: openPackage(PHONE_PACKAGE)
        }, btnLp(false))
    }

    /** Sahne düğmesi: 22dp köşeli; etkin olan birincil (CTA) gradyan zeminli ve gölgeli, diğerleri zeminsiz. */
    private fun sahneButton(
        iconRes: Int,
        labelRes: Int,
        active: Boolean = false,
        iconDp: Int = 30,
        /** Uzun basış (yalnız telefon düğmesinde: sesli asistan); dokunsal geri bildirimle. */
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit,
    ): View =
        FrameLayout(context).also { barButtons += BarButton(it, active) }.apply {
            val radius = context.dpF(Sahne.SQUARE_RADIUS_DP)
            if (active) {
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Sahne.HOME_TOP, Sahne.HOME_BOTTOM),
                ).apply { cornerRadius = radius }
                // Gece koyu dolgu gölgesiz (zeminden ayrışması için gölge gerekmez, parlak leke de olmaz).
                elevation = if (LauncherTheme.night) 0f else context.dpF(8f)
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, outline: Outline) = outline.setRoundRect(0, 0, v.width, v.height, radius)
                }
            }
            foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(LauncherTheme.DOCK_PRESS), null,
                android.graphics.drawable.GradientDrawable().apply { cornerRadius = radius; setColor(0xFFFFFFFF.toInt()) },
            )
            contentDescription = context.getString(labelRes)
            isClickable = true
            isFocusable = true
            addView(icon(context, iconRes, iconDp, if (active) Sahne.HOME_INK else Sahne.CONTROL_ICON),
                FrameLayout.LayoutParams(context.dp(iconDp), context.dp(iconDp), Gravity.CENTER))
            setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { v ->
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onLongClick()
                true
            }
        }

    /** Telefon düğmesine uzun basış: bağlı telefonun sesli asistanı (Siri / Google); yol yoksa kısa uyarı (OEM uygulaması açılmaz). */
    private fun startPhoneAssistant() {
        if (PhoneSource.startVoiceAssistant()) return
        // Neden (bağlı telefon yok vb.) dinleyen panel/şerit varsa onPhoneError ile zaten gösterilir; yoksa tek genel
        // uyarı. Hata bildirimi ana kuyruğa önce girdiği için sonrasında bakılır.
        view.post { if (!PhoneToast.shownWithin(ERROR_TOAST_WINDOW_MS)) PhoneToast.show(context, R.string.launcher_phone_assistant_failed) }
    }

    // --- Spor modu ---

    private class BarButton(val frame: FrameLayout, val active: Boolean) {
        val icon: ImageView get() = frame.getChildAt(0) as ImageView
        var bg: Drawable? = null
        var elevation = 0f
        var tint: android.content.res.ColorStateList? = null
    }

    private var sport = false
    private var normalBackground: Drawable? = null

    /**
     * Spor görünümü: tek karbon panel, fırçalanmış metal çerçeve, üst kenarda ince kırmızı dikiş; düğmeler talaşlı metal
     * çerçeve (tırtıl yalnız ana ekran düğmesinde), sıcaklıklar ince rakamlar, fan küçük yay göstergesi. Yerleşim, dokunma
     * alanları ve davranış aynı; geri dönüşte kurulumdaki çizimler aynen geri konur.
     */
    fun setSport(on: Boolean) {
        if (on == sport) return
        sport = on
        if (on) {
            normalBackground = view.background
            view.background = SportPanelDrawable(context, Sahne.PANEL_RADIUS_DP)
            barButtons.forEach { b ->
                b.bg = b.frame.background
                b.elevation = b.frame.elevation
                b.tint = b.icon.imageTintList
                b.frame.background = SportKnobDrawable(context, knurled = b.active, insetDp = 5f)
                b.frame.elevation = 0f
                b.icon.imageTintList = android.content.res.ColorStateList.valueOf(if (b.active) SportInk.ICON_ACTIVE else SportInk.ICON)
            }
            barSeps.forEach { it.setBackgroundColor(0x24FFFFFF) }
        } else {
            view.background = normalBackground
            normalBackground = null
            barButtons.forEach { b ->
                b.frame.background = b.bg
                b.frame.elevation = b.elevation
                b.icon.imageTintList = b.tint
                b.bg = null
            }
            barSeps.forEach { it.setBackgroundColor(Sahne.CONTROL_BORDER) }
        }
        climateControl.setSport(on)
    }

    override fun onVisible() {
        bindClimate(readout)
        startup?.cancel()
        startup = LauncherStartup.later { main.post(poll) }
    }

    override fun onHidden() {
        startup?.cancel()
        main.removeCallbacks(poll)
        climateControl.onHidden()
        appsDialog?.dismiss()
        appsDialog = null
    }

    /** Paylaşılan [LauncherIo] havuzu kapatılmaz. */
    override fun release() = onHidden()

    private val poll = object : Runnable {
        override fun run() {
            io.runSafely {
                val read = readClimate()
                main.post {
                    if (read == readout) return@post
                    readout = read
                    bindClimate(read)
                }
            }
            main.postDelayed(this, POLL_MS)
        }
    }

    // --- Yerleşim ---

    // --- Bağlama ---

    private fun bindClimate(r: ClimateReadout) = climateControl.bind(r)

    // --- Okuma ---

    /** Araçtan salt-okur: sürücü/yolcu sıcaklığı (HVAC 10/13) ve fan kademesi (HVAC 12). Yazma yok. */
    private fun readClimate(): ClimateReadout {
        if (LauncherDemo.enabled) return ClimateReadout(true, LauncherDemo.DRIVER_TEMP, LauncherDemo.PASSENGER_TEMP, LauncherDemo.FAN_LEVEL)
        val proxy = CarInfoProxy.getInstance()
        if (!proxy.isServiceConnnected) return ClimateReadout()
        return try {
            fun first(id: Int): Int? = proxy.getItemValues(VDEventCarInfo.MODULE_HVAC, id)?.firstOrNull()
            ClimateReadout(
                connected = true,
                driverTemp = first(HvacID.ID_AC_TEMPERATURE_DRIVER)?.let(::decodeTemp),
                passengerTemp = first(HvacID.ID_AC_TEMPERATURE_COPILOT)?.let(::decodeTemp),
                fan = first(HvacID.ID_AC_BLOW_SPEED_LEVEL)?.let(::decodeFan),
            )
        } catch (e: RuntimeException) {
            ClimateReadout()
        }
    }

    // --- Eylemler ---

    /** Uygulamayı varsayılan ekranda açar; kurulu değilse sessizce bir şey yapmaz (emülatör). */
    private fun leaveToSystemHome() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        if (target == null) { activity.moveTaskToBack(true); return }
        if (target.packageName == context.packageName) return
        try {
            context.startActivity(home.setClassName(target.packageName, target.name))
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }

    /** Çubukta yukarı kaydırma: OEM klima paneli (yalnız OEM paketi olan araçta; yoksa kısa uyarı). */
    private fun openClimatePanel() {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        ClimatePanel.open(context)
    }

    private fun openPackage(packageName: String) {
        io.runSafely {
            // Sürüş ekranından yarıda kalmış harita tam ekran açılır ([DriveScreen.openLeftover]).
            if (!DriveScreen.openLeftover(context, packageName)) AppLaunchHelper.launchAppOnDisplay(context, packageName, Display.DEFAULT_DISPLAY, 0)
        }
    }

    private fun openAndroidSettings() {
        try {
            activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // Ayarlar uygulaması yok: yapılacak bir şey yok.
        }
    }

    // --- Tüm uygulamalar ---

    private var appsDialog: Dialog? = null

    /** Başlatılabilir tüm uygulamalar bir panelde; ikonlar arka planda çözülür, dokununca uygulama açılır. */
    private fun showApps() {
        if (appsDialog?.isShowing == true) return
        io.runSafely {
            val rows = ProjectionTargetApps.loadAllLaunchableRows(context)
            val entries = rows.map { r ->
                AppCell(AppIdentity.label(context, r.packageName, r.label), r.packageName, loadIcon(r.packageName))
            }
            main.post {
                if (activity.isFinishing || activity.isDestroyed || entries.isEmpty()) return@post
                appsDialog = showLauncherSheet(activity, R.string.launcher_bar_apps, APPS_SHEET_WIDTH_DP) { d ->
                    addView(appsGrid(entries, d), LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, context.dp(APPS_HEIGHT_DP)))
                }.apply { setOnDismissListener { appsDialog = null } }
            }
        }
    }

    private class AppCell(val label: String, val packageName: String, val icon: Drawable?)

    private fun loadIcon(packageName: String): Drawable? = try {
        val plate = if (LauncherTheme.night) LauncherTheme.CONTROL else 0
        AppIdentity.overrideIcon(context, packageName, context.dp(APP_ICON_DP))
            ?: AppIconHelper.loadSized(context, packageName, context.dp(APP_ICON_DP), plate)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun appsGrid(entries: List<AppCell>, d: Dialog): View = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            entries.chunked(APPS_COLUMNS).forEach { chunk ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    chunk.forEach { app -> addView(appCell(app, d), LinearLayout.LayoutParams(0, context.dp(APP_CELL_DP), 1f)) }
                    // Son satırı doldur ki hücre genişlikleri eşit kalsın.
                    repeat(APPS_COLUMNS - chunk.size) { addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)) }
                })
            }
        })
    }

    private fun appCell(app: AppCell, d: Dialog): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = app.label
        addView(ImageView(context).apply {
            setImageDrawable(app.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(APP_ICON_DP), context.dp(APP_ICON_DP)))
        addView(gap(context, 6, vertical = true))
        addView(text(context, LauncherTheme.LABEL_SP).apply {
            text = app.label
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = context.dp(8)
            marginEnd = context.dp(8)
        })
        setOnClickListener {
            d.dismiss()
            openPackage(app.packageName)
        }
        pressFeedback()
    }

    companion object {
        /** Araç simgesi geniş ve alçak bir yan görünüş: diğer simgelerle aynı görsel ağırlıkta kalması için kutusu büyük (genişliğe sıkı görünüm kutusu). */
        private const val CAR_ICON_DP = 40

        /** Tiggo 7 çubuk yüksekliği; profile göre gerçek yükseklik için [heightDp]. */
        const val HEIGHT_DP = VehicleProfile.TIGGO7_BAR_DP

        /** Etkin profilin çubuk yüksekliği (dp): Tiggo 7 sabit, Tiggo 8 ekran yüksekliğinin kendi dock oranı. */
        fun heightDp(context: Context): Int {
            val dm = context.resources.displayMetrics
            return VehicleProfile.active.barHeightDp(dm.heightPixels / dm.density)
        }
        /** Çubuğun alt kenarında bırakılan boşluk: alttan kaydırma hareketi (OEM çubuğunu çağırır) çubuğa takılmasın. */
        const val BOTTOM_GAP_DP = 12

        private const val VEHICLE_SETTINGS_PACKAGE = "com.desaysv.vehiclesetting"
        private const val PHONE_PACKAGE = "com.desaysv.bluetooth.phone"
        private const val ERROR_TOAST_WINDOW_MS = 500L
        private const val POLL_MS = 3_000L
        private const val DASH = "—"

        private const val APPS_SHEET_WIDTH_DP = 1280
        private const val APPS_HEIGHT_DP = 440
        private const val APPS_COLUMNS = 7
        private const val APP_CELL_DP = 140
        private const val APP_ICON_DP = 64

        /** Sıcaklık: °C = 15,5 + ham/2 (araçta ekranla karşılaştırıldı: 21 → 26,0); ham 0 = LO (15,5), 30 = HI (30,5); aralık dışı "—". */
        fun decodeTemp(raw: Int): Double? = (ClimateMath.LO_TEMP + raw / 2.0).takeIf { raw in ClimateMath.RAW_LO..ClimateMath.RAW_HI }

        /** Fan kademesi doğrudan okunur; makul aralık (0..[ClimateMath.FAN_MAX]) dışı "—". Araçta en çok 10 okundu. */
        fun decodeFan(raw: Int): Int? = raw.takeIf { it in 0..ClimateMath.FAN_MAX }
    }
}

/**
 * Dock'un basış örtüsü: basılıyken %12 beyaz, bırakınca kısa solar. Bölümün üstünde (foreground); dış şekle
 * kırpma dock'un `clipToOutline`ından gelir.
 */
internal fun dockPressOverlay(context: Context): Drawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(LauncherTheme.DOCK_PRESS))
    addState(intArrayOf(), ColorDrawable(0))
    setEnterFadeDuration(0)
    setExitFadeDuration(150)
}

/**
 * Yatay çubuk kökü: dokunma alanları (sıcaklık/fan) kendi sürüklemesini sahiplenir ve üst görünümün yakalamasını
 * kapatır, bu yüzden yalnızca düğmeler ve boşluklar üstündeki yukarı kaydırma ([ClimatePanelGesture]) yakalanır.
 * Bırakınca eşik aşıldıysa [onSwipeUp] bir kez çağrılır.
 */
private class SwipeUpBar(context: Context, private val onSwipeUp: () -> Unit) : LinearLayout(context) {
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    override fun onInterceptTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; tracking = false }
            android.view.MotionEvent.ACTION_MOVE ->
                if (ClimatePanelGesture.isUpwardDrag(downY - e.rawY, e.rawX - downX, slop)) { tracking = true; return true }
        }
        return false
    }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        if (!tracking) return super.onTouchEvent(e)
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_UP -> {
                tracking = false
                if (ClimatePanelGesture.shouldOpen(downY - e.rawY, e.rawX - downX, resources.displayMetrics.density)) onSwipeUp()
            }
            android.view.MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return true
    }
}
