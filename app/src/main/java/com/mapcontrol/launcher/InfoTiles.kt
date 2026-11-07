package com.mapcontrol.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import com.mapcontrol.R
import com.mapcontrol.util.AppIconHelper
import com.mapcontrol.util.AppLaunchHelper
import com.mapcontrol.util.ProjectionTargetApps

/** Bu süreden eski hava verisi ikincil tonda gösterilir (hata değil). */
internal const val WEATHER_STALE_MS = 3L * 60L * 60L * 1000L

/**
 * Tek uygulamalık kısayol: 64dp ikon ve altında etiket. Dokunmak uygulamayı açar.
 * Uygulama yoksa ya da kurulu değilse artı ikonlu boş kutu gösterilir; ona dokunmak seçiciyi açar.
 * Uzun basış her durumda seçiciyi açar. Seçim "launcher_tile_apps" tercihlerinde [tileId] anahtarıyla tutulur
 * ve verilen [packageName] değerini geçersiz kılar.
 */
class AppShortcutTile(
    private val activity: Activity,
    private val packageName: String?,
    private val tileId: String = "app",
) : SizedWidget, Themeable, StripSource {

    // --- Şerit (Sahne): ikon + ad; dokunuş açar (boşsa seçici), uzun basış seçici ---
    override var onStripChanged: (() -> Unit)? = null
    private var stripNow: StripState? = null
    override fun stripState(): StripState? = stripNow
    override fun stripTap(part: Int) {
        when (val current = shown) {
            is Shown.App -> launch(current.pkg)
            Shown.Empty -> openPicker()
            Shown.Loading -> Unit
        }
    }
    override fun stripLongPress(): Boolean { openPicker(); return true }

    /** Ekranda şu an ne gösterildiği; tıklamanın ne yapacağını belirler. */
    private sealed class Shown {
        object Loading : Shown()
        object Empty : Shown()
        class App(val pkg: String) : Shown()
    }

    /** Uygulamanın etiketi ve ikonu. */
    private class AppEntry(val label: String, val icon: Drawable)

    private val context: Context = activity
    /** Arka plan sonuçları ana iş parçacığına bununla döner: kutucuk ekrana konmadan (Sahne şeridi) da çalışır. */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val prefs = context.getSharedPreferences(Profiles.key(PREFS_NAME, Profiles.current(context)), Context.MODE_PRIVATE)
    private val executor = LauncherIo.serial()
    private val iconRadius = context.dpF(ICON_RADIUS_DP)

    /** Her yenilemede artar; eski arka plan sonuçları bu değişmişse yok sayılır. */
    private var generation = 0
    private var shown: Shown = Shown.Loading

    private val iconView = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(context.dp(ICON_DP), context.dp(ICON_DP))
        scaleType = ImageView.ScaleType.FIT_CENTER
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, iconRadius)
            }
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val labelView = text(context, LauncherTheme.LABEL_SP).apply {
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    // 125dp kartta 64dp ikon + etiket sığsın diye 10dp kenar: etiketin alt çıkıntıları kesilmez.
    private var compact = false

    private val root = CardShell(context, paddingDp = 10).apply {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        addView(iconView)
        addView(gap(context, 8, vertical = true))
        addView(labelView)
        setOnClickListener {
            when (val current = shown) {
                is Shown.App -> launch(current.pkg)
                Shown.Empty -> openPicker()
                Shown.Loading -> Unit
            }
        }
        setOnLongClickListener {
            openPicker()
            true
        }
        pressFeedback()
    }

    override val view: View = root

    /**
     * Tema değişti: yüzeyi [CardShell] çizimde okur (yalnızca yeniden çizilir); etiket rengi ve boş kutunun
     * zemini/artısı mevcut duruma göre yeniden atanır. Uygulama ikonu temadan bağımsızdır.
     */
    override fun applyTheme() {
        if (shown is Shown.Empty) bindEmpty() else labelView.setTextColor(LauncherTheme.TEXT)
        root.invalidate()
    }

    /**
     * 1 hücre genişliğindeki ince kutucukta (≈143 px) ikon küçülür, etiket küçük puntoyla tek satıra sığar
     * (taşarsa "…"); 2 hücrede eski tam boy görünüm.
     */
    override fun applySpan(span: Span) {
        root.gravity = Gravity.CENTER
        compact = span.w < 2
        val iconPx = context.dp(if (compact) ICON_COMPACT_DP else ICON_DP)
        iconView.layoutParams = LinearLayout.LayoutParams(iconPx, iconPx)
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) LABEL_COMPACT_SP else LauncherTheme.LABEL_SP)
        // Dar kutucukta uzun ad ("YouTube Music") kesilmesin: iki satıra kadar sarılır.
        labelView.maxLines = if (compact) 2 else 1
        val p = context.dp(if (compact) 4 else 10)
        root.setPadding(p, p, p, p)
        if (shown is Shown.Empty) bindEmpty()
        applyEmptyVisibility()
    }

    /** Boş kutu sakin, boş bir yüzey kalır (seçici yuvanın kendisinden ya da uzun basışla açılır). */
    private fun applyEmptyVisibility() {
        val hide = shown is Shown.Empty
        root.surfaceVisible = !hide
        iconView.visibility = if (hide) View.INVISIBLE else View.VISIBLE
        labelView.visibility = if (hide) View.INVISIBLE else View.VISIBLE
    }

    /** Ekran görünür olunca seçimi okur ve ikon/etiketi (gerekirse arka planda) yükler. */
    override fun onVisible() = refresh()

    override fun onHidden() = Unit

    /** Paylaşılan [LauncherIo] havuzu kapatılmaz; ayrılacak bir kaynak yok. */
    override fun release() = Unit

    /** Kayıtlı seçim varsa o, yoksa kurulumdaki paket; paket yoksa ya da kurulu değilse boş kutu. */
    private fun refresh() {
        val token = ++generation
        val pkg = prefs.getString(tileId, null) ?: packageName
        if (pkg.isNullOrEmpty()) {
            bindEmpty()
            return
        }
        executor.runSafely {
            val entry = resolve(pkg)
            mainHandler.post {
                if (token != generation) return@post
                if (entry == null) bindEmpty() else bindApp(pkg, entry)
            }
        }
    }

    private fun bindApp(pkg: String, entry: AppEntry) {
        shown = Shown.App(pkg)
        iconView.background = null
        iconView.imageTintList = null
        iconView.setPadding(0, 0, 0, 0)
        iconView.setImageDrawable(entry.icon)
        labelView.text = entry.label
        labelView.setTextColor(LauncherTheme.TEXT)
        root.contentDescription = entry.label
        applyEmptyVisibility()
        stripNow = StripState("", entry.label, R.drawable.ic_mdi_plus, iconDrawable = entry.icon)
        onStripChanged?.invoke()
    }

    /** Boş kutu: CONTROL renkli yuvarlak kare içinde artı ikonu. */
    private fun bindEmpty() {
        shown = Shown.Empty
        val pad = context.dp(((if (compact) ICON_COMPACT_DP else ICON_DP) - PLUS_DP) / 2)
        iconView.background = rounded(context, LauncherTheme.CONTROL, ICON_RADIUS_DP)
        iconView.imageTintList = ColorStateList.valueOf(LauncherTheme.TEXT_SECONDARY)
        iconView.setPadding(pad, pad, pad, pad)
        iconView.setImageResource(R.drawable.ic_mdi_plus)
        val emptyLabel = context.getString(R.string.launcher_app_empty)
        labelView.text = emptyLabel
        labelView.setTextColor(LauncherTheme.TEXT_SECONDARY)
        root.contentDescription = emptyLabel
        applyEmptyVisibility()
        stripNow = StripState("", emptyLabel, R.drawable.ic_mdi_plus, StripTone.MUTED)
        onStripChanged?.invoke()
    }

    /** Uygulamayı varsayılan ekranda açar. Java yardımcısı senkron olduğu için arka planda çalışır. */
    private fun launch(pkg: String) {
        executor.runSafely {
            // Sürüş ekranından yarıda kalmış harita tam ekran açılır (yoksa ana ekranın üstünde yarım pencere gelirdi).
            if (!DriveScreen.openLeftover(context, pkg)) AppLaunchHelper.launchAppOnDisplay(context, pkg, Display.DEFAULT_DISPLAY, 0)
        }
    }

    /** Başlatılabilir uygulamaları arka planda listeler; seçim tercihlere yazılır. */
    private fun openPicker() {
        executor.runSafely {
            val rows = ProjectionTargetApps.loadAllLaunchableRows(context)
            mainHandler.post {
                if (rows.isEmpty()) return@post
                AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(R.string.launcher_app_pick_title)
                    .setItems(rows.map { it.label }.toTypedArray()) { _, which ->
                        prefs.edit().putString(tileId, rows[which].packageName).apply()
                        refresh()
                    }
                    .create().also { it.hideOemBarIfNeeded(context); it.show() }
            }
        }
    }

    /** Paketin etiketi ve ikonu; kurulu değilse null. Arka planda çağrılır. */
    private fun resolve(pkg: String): AppEntry? = try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(pkg, 0)
        val label = AppIdentity.label(context, pkg, pm.getApplicationLabel(info).toString())
        AppEntry(label, AppIdentity.overrideIcon(context, pkg, context.dp(ICON_DP)) ?: AppIconHelper.load(context, pkg))
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private companion object {
        const val PREFS_NAME = "launcher_tile_apps"
        const val ICON_DP = 64
        const val ICON_COMPACT_DP = 52
        const val LABEL_COMPACT_SP = 18f
        const val PLUS_DP = 28
        const val ICON_RADIUS_DP = 16f
    }
}
