package com.mapcontrol.launcher

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Outline
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
import com.mapcontrol.R
import com.mapcontrol.util.AppIconHelper
import com.mapcontrol.util.AppLaunchHelper
import com.mapcontrol.util.ProjectionTargetApps

/**
 * Launcher'ın kendi alt çubuğu ("Alt çubuk: Bizimki"): OEM gezinme çubuğu gizlenirken yerini alır.
 * Tek parça "dock" şeridi: solda ana ekran · uygulamalar · araç, sağda ayarlar · telefon (kare düğmeler). Telefon düğmesi
 * aracın telefon uygulamasını açar. Ortadaki alan klima kontrolleri içindir (sonraki parçada).
 */
class OurBottomBar(
    private val activity: Activity,
) : LauncherWidget {

    private val context: Context = activity
    private val io = LauncherIo.serial()
    private val main = Handler(Looper.getMainLooper())

    override val view: View = sahneBar()

    /**
     * Çubuk (Sahne taslağı): cam şerit, solda ana ekran · uygulamalar · araç (88 × 80 düğmeler, etkin ana ekran gece beyaz,
     * gündüz grafit), sağda ayarlar · telefon.
     */
    private fun sahneBar(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GlassDrawable(context, Sahne.PANEL_RADIUS_DP, control = true)
        setPadding(context.dp(8), 0, context.dp(8), 0)
        fun btnLp(first: Boolean) = LinearLayout.LayoutParams(context.dp(88), context.dp(80)).apply { if (!first) marginStart = context.dp(6) }
        addView(sahneButton(R.drawable.ic_sahne_home_fill, R.string.launcher_bar_home, active = true) { leaveToSystemHome() }, btnLp(true))
        addView(sahneButton(R.drawable.ic_sahne_grid, R.string.launcher_bar_apps) { showApps() }, btnLp(false))
        addView(sahneButton(R.drawable.ic_sahne_car, R.string.launcher_vehicle_settings, iconDp = CAR_ICON_DP) { openPackage(VEHICLE_SETTINGS_PACKAGE) }, btnLp(false))
        addView(View(context), LinearLayout.LayoutParams(0, context.dp(80), 1f))
        addView(sahneButton(R.drawable.ic_sahne_gear, R.string.launcher_bar_settings) { openAndroidSettings() }, btnLp(true))
        addView(sahneButton(R.drawable.ic_sahne_phone, R.string.launcher_app_phone) { openPackage(PHONE_PACKAGE) }, btnLp(false))
    }

    /** Sahne düğmesi: 22dp köşeli; etkin olan birincil (CTA) gradyan zeminli ve gölgeli, diğerleri zeminsiz. */
    private fun sahneButton(
        iconRes: Int,
        labelRes: Int,
        active: Boolean = false,
        iconDp: Int = 30,
        onClick: () -> Unit,
    ): View =
        FrameLayout(context).apply {
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
        }

    override fun onVisible() = Unit

    override fun onHidden() {
        appsDialog?.dismiss()
        appsDialog = null
    }

    /** Paylaşılan [LauncherIo] havuzu kapatılmaz. */
    override fun release() = onHidden()

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

        private const val APPS_SHEET_WIDTH_DP = 1280
        private const val APPS_HEIGHT_DP = 440
        private const val APPS_COLUMNS = 7
        private const val APP_CELL_DP = 140
        private const val APP_ICON_DP = 64
    }
}
