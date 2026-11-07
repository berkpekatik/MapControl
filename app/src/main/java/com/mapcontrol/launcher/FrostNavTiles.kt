package com.mapcontrol.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mapcontrol.R

/**
 * FrostNav'ın var olan özelliklerini süren widget'ların ortak gövdesi. Sahne'de şerit öğesi ([StripSource]) ve panel (4 sütunlu büyük biçim; panel kutucuğun kart zeminini kaldırır) olarak
 * çalışır. Durum [FrostNavFeatures] üzerinden, görünürken [POLL_MS]'de bir arka planda okunur; yalnızca değişince çizilir.
 * Uzun basış (şerit) FrostNav'ın ilgili ayar sekmesini açar.
 */
internal abstract class FrostNavTile<S : Any>(
    protected val activity: Activity,
    @StringRes protected val labelRes: Int,
    @DrawableRes protected val iconRes: Int,
    private val settingsTab: FrostNavTab,
) : SizedWidget, Themeable, StripSource {

    protected val features: FrostNavFeatures = LauncherEdition.frostNav(activity)
    protected val main = Handler(Looper.getMainLooper())
    private val io = LauncherIo.serial()
    private var startup: LauncherStartup.Deferred? = null

    /** Son okunan durum; ilk okumaya kadar null. */
    protected var state: S? = null

    private var span = Span(2, 2)

    /** Büyük biçim: 4 sütunlu kutucuk ya da Sahne paneli. */
    protected val large: Boolean get() = span.w >= WIDE_MIN_COLS

    /** Büyük biçim yüksek mi (4×4 ya da panel); 4×2 alçak geniş kutucuktur ([buildWide]). */
    private val tall: Boolean get() = span.h >= TALL_MIN_ROWS

    /** Açık sayfa (varsa): durum değişince içeriği yeniden kurulur. */
    private var sheetBox: LinearLayout? = null
    private var sheet: Dialog? = null

    protected val root: FrameLayout = FrameLayout(activity).apply {
        background = cardSurface(activity)
        minimumHeight = activity.dp(LauncherTheme.TOUCH_MIN_DP)
    }

    override val view: View get() = root

    private val poll = object : Runnable {
        override fun run() {
            refresh()
            main.postDelayed(this, POLL_MS)
        }
    }

    /** Arka planda okuma (G/Ç yapabilir). */
    protected abstract fun load(): S

    /** Kutucuk/panel içeriği; [state] null ise okuma bekleniyor. */
    protected abstract fun buildCompact(): View
    protected abstract fun buildLarge(): View

    /** Alçak geniş kutucuk (4×2): varsayılan büyük biçim; düğmesi olan türler yan yana dizer. */
    protected open fun buildWide(): View = buildLarge()

    /** Kutucuğa (küçük biçim) dokunuş. Büyük biçimde dokunuşlar içerideki düğmelerdedir. */
    protected abstract fun onTap()

    /** Şerit özeti. */
    protected abstract fun strip(): StripState

    /** Sayfa içeriği ([openSheet] ile açılınca ve durum değişince). */
    protected open fun buildSheet(box: LinearLayout, dialog: Dialog) = Unit

    override fun onVisible() {
        redraw()
        startup?.cancel()
        startup = LauncherStartup.later { main.post(poll) }
    }

    override fun onHidden() {
        startup?.cancel()
        main.removeCallbacks(poll)
    }

    override fun release() {
        onHidden()
        sheet?.dismiss()
    }

    override fun applySpan(span: Span) {
        this.span = span
        redraw()
    }

    /** Tema değişti: yüzey (panelde kaldırılmışsa kaldırılmış kalır) ve içerik yeni renklerle yeniden kurulur. */
    override fun applyTheme() {
        if (root.background != null) root.background = cardSurface(activity)
        redraw()
    }

    /** Şimdi oku; değiştiyse çiz. */
    protected fun refresh() {
        if (!features.available) return
        io.runSafely {
            val s = load()
            main.post {
                if (s == state) return@post
                state = s
                redraw()
            }
        }
    }

    /** Arka planda [block], ardından yeniden okuma. */
    protected fun act(block: () -> Unit) {
        io.runSafely(block)
        refresh()
    }

    protected fun redraw() {
        root.removeAllViews()
        val content = when {
            !features.available -> unavailable()
            !large -> buildCompact()
            tall -> buildLarge()
            else -> buildWide()
        }
        root.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setTappable(features.available && !large)
        if (!features.available) root.setOnClickListener { notAvailableToast() }
        sheetBox?.let { box -> sheet?.let { d -> box.removeAllViews(); buildSheet(box, d) } }
        onStripChanged?.invoke()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTappable(on: Boolean) {
        root.isClickable = on
        root.isFocusable = on
        if (on) {
            root.setOnClickListener { onTap() }
            root.pressFeedback()
        } else {
            root.setOnClickListener(null)
            root.isClickable = false
            root.setOnTouchListener(null)
        }
    }

    /** FrostNav'sız derlemede (test sürümü) dokunuş sessiz kalmasın: kısa bir bilgi gösterilir. */
    private fun notAvailableToast() {
        android.widget.Toast.makeText(activity, R.string.launcher_fn_unavailable_toast, android.widget.Toast.LENGTH_SHORT).show()
    }

    protected fun openSettings() = openTab(settingsTab)

    /**
     * FrostNav'ın [tab] sekmesini açar. Açık sayfa önce kapanır: FrostNav'dan geri dönülünce ana ekranda eski sayfa
     * (ör. Otomasyon'da "Yüz tanıma" satırından gidilince) açık kalmasın.
     */
    protected fun openTab(tab: FrostNavTab) {
        sheet?.dismiss()
        features.openTab(activity, tab)
    }

    /** Kutucuğun ayrıntı sayfası: başlık + [buildSheet] içeriği; durum değiştikçe yenilenir. */
    protected fun openSheet(widthDp: Int = SHEET_DP) {
        if (sheet?.isShowing == true) return
        sheet = showLauncherSheet(activity, labelRes, widthDp) { dialog ->
            val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            addView(box, sheetFullWidth())
            sheetBox = box
            buildSheet(box, dialog)
            dialog.setOnDismissListener { sheetBox = null; sheet = null }
        }
    }

    // --- Şerit ---
    override var onStripChanged: (() -> Unit)? = null
    override fun stripState(): StripState? =
        if (!features.available) StripState(activity.getString(labelRes), activity.getString(R.string.launcher_fn_only), iconRes, StripTone.MUTED)
        else strip()

    override fun stripTap(part: Int) { if (features.available) onTap() else notAvailableToast() }

    override fun stripLongPress(): Boolean {
        if (!features.available) { notAvailableToast(); return true }
        openSettings()
        return true
    }

    // --- Ortak görünümler ---

    private fun unavailable(): View = compact(activity.getString(R.string.launcher_fn_only), LauncherTheme.TEXT_SECONDARY, null)

    /** Küçük biçim: üstte ikon + başlık, altında büyük değer ve küçük detay; [leading] solda (ör. QR). */
    protected fun compact(value: String, valueColor: Int, detail: String?, leading: View? = null): View {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(header(withIcon = leading == null))
            addView(text(activity, COMPACT_VALUE_SP, valueColor, medium = true).apply {
                text = value
                setAutoSizeTextTypeUniformWithConfiguration(18, COMPACT_VALUE_SP.toInt(), 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(COMPACT_VALUE_H_DP)).apply { topMargin = activity.dp(6) })
            if (!detail.isNullOrEmpty()) addView(text(activity, LauncherTheme.LABEL_SP - 2f, LauncherTheme.TEXT_SECONDARY).apply { text = detail },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = activity.dp(4) })
        }
        val p = activity.dp(PAD_DP)
        root.contentDescription = listOfNotNull(activity.getString(labelRes), value, detail).joinToString(", ")
        if (leading == null) return column.apply { setPadding(p, p, p, p) }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(p, p, p, p)
            // Kare öğe ([QrView]) genişliğini yüksekliğinden alır.
            addView(leading, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = activity.dp(14) })
        }
    }

    /** Başlık satırı: ikon + ad, [trailing] sağda. */
    protected fun header(withIcon: Boolean = true, trailing: View? = null): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        if (withIcon) addView(icon(activity, sahneIconFor(iconRes), HEADER_ICON_DP, LauncherTheme.TEXT_SECONDARY))
        addView(text(activity, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY).apply { setText(labelRes) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (withIcon) marginStart = activity.dp(10) })
        if (trailing != null) addView(trailing)
    }

    /** Büyük biçimin kabı: başlık + gövde, 24dp iç boşluk. */
    protected fun largeFrame(trailing: View?, body: LinearLayout.() -> Unit): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val p = activity.dp(LARGE_PAD_DP)
        setPadding(p, p, p, p)
        addView(header(trailing = trailing).apply { minimumHeight = activity.dp(40) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body()
    }

    /**
     * Büyük biçimde boş alanı dolduran illüstrasyon (`launcher_art_<key>`, [SahneArt]); yoksa null. Kapalıyken renksiz ve soluk;
     * gece görsel filtresi Sahne şeridiyle aynı.
     */
    protected fun heroArt(key: String, on: Boolean): View? {
        val d = SahneArt.drawable(activity, key) ?: return null
        return ImageView(activity).apply {
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.FIT_CENTER
            colorFilter = SahneArt.filter(!on)
            imageAlpha = if (on) 255 else SahneArt.DIM_ALPHA
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    /** Sağ üstte durum yazısı ("Açık" vurgu rengiyle). */
    protected fun stateLabel(on: Boolean): TextView =
        text(activity, LauncherTheme.LABEL_SP, if (on) LauncherTheme.ACCENT else LauncherTheme.TEXT_SECONDARY, medium = true).apply {
            setText(if (on) R.string.launcher_toggle_on else R.string.launcher_toggle_off)
        }

    /** Hap düğme (en az 72dp yüksek). [primary]: vurgu dolgulu. */
    protected fun pill(label: String, primary: Boolean = false, onClick: () -> Unit): TextView =
        text(activity, LauncherTheme.BODY_SP, if (primary) LauncherTheme.ON_ACCENT else LauncherTheme.TEXT, medium = true).apply {
            text = label
            gravity = Gravity.CENTER
            minimumHeight = activity.dp(LauncherTheme.TOUCH_MIN_DP)
            setPadding(activity.dp(28), 0, activity.dp(28), 0)
            background = rounded(activity, if (primary) DriveAccent.buttonFill(LauncherTheme.ACCENT) else LauncherTheme.CONTROL, 999f)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            pressFeedback()
        }

    /**
     * Büyük seçilebilir satır (en az [ROW_MIN_DP]): ad, isteğe bağlı sağ yazı ve aç/kapa anahtarı ya da seçim işareti.
     * Seçili satır vurgu tonunda.
     */
    protected fun row(
        label: String,
        selected: Boolean = false,
        trailingText: String? = null,
        switchOn: Boolean? = null,
        enabled: Boolean = true,
        /** Satır FrostNav'a götürür: sağda ok. */
        opens: Boolean = false,
        onClick: (() -> Unit)?,
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = activity.dp(ROW_MIN_DP)
        setPadding(activity.dp(20), 0, activity.dp(12), 0)
        background = rounded(activity, if (selected) DriveAccent.buttonFill(LauncherTheme.ACCENT) else LauncherTheme.CONTROL, 16f)
        val fg = if (selected) LauncherTheme.ON_ACCENT else LauncherTheme.TEXT
        addView(text(activity, LauncherTheme.BODY_SP, fg, medium = true).apply { text = label; ellipsize = TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (trailingText != null) addView(text(activity, LauncherTheme.LABEL_SP, if (selected) fg else LauncherTheme.TEXT_SECONDARY).apply { text = trailingText },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = activity.dp(10) })
        if (switchOn != null) addView(switchPill(switchOn), LinearLayout.LayoutParams(activity.dp(88), activity.dp(ROW_MIN_DP)))
        else if (selected) addView(icon(activity, R.drawable.ic_mdi_check, 30, fg), LinearLayout.LayoutParams(activity.dp(40), activity.dp(30)).apply { marginEnd = activity.dp(8) })
        else if (opens) addView(icon(activity, R.drawable.ic_mdi_chevron_right, 30, LauncherTheme.TEXT_SECONDARY), LinearLayout.LayoutParams(activity.dp(30), activity.dp(30)).apply { marginEnd = activity.dp(8) })
        alpha = if (enabled) 1f else DISABLED_ALPHA
        // Anahtar satırında durum da okunur (TalkBack ve otomatik test anahtarın açık mı kapalı mı olduğunu görsün).
        val switchText = switchOn?.let { activity.getString(if (it) R.string.launcher_toggle_on else R.string.launcher_toggle_off) }
        contentDescription = listOfNotNull(label, trailingText, switchText).joinToString(", ")
        isSelected = selected
        if (onClick != null && enabled) {
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            pressFeedback()
        }
    }

    /** Dokunulabilir aç/kapa anahtarı (88×56 dokunma alanı), ör. panel başlığının sağında. */
    protected fun switchButton(on: Boolean, description: String, onClick: () -> Unit): View = FrameLayout(activity).apply {
        addView(switchPill(on), FrameLayout.LayoutParams(activity.dp(88), activity.dp(56), Gravity.CENTER))
        contentDescription = description + ", " + activity.getString(if (on) R.string.launcher_toggle_on else R.string.launcher_toggle_off)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(activity.dp(88), activity.dp(56))
    }

    /**
     * Seçim kutuları ızgarası ([columns] sütun): panelde satır listesi sığmayınca büyük dokunma alanlı seçenekler. Her kutu
     * kabın yüksekliğini eşit paylaşır.
     */
    protected fun grid(cells: List<View>, columns: Int): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        cells.chunked(columns).forEachIndexed { r, line ->
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                line.forEachIndexed { c, v ->
                    addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { if (c > 0) marginStart = activity.dp(12) })
                }
                repeat(columns - line.size) { addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = activity.dp(12) }) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { if (r > 0) topMargin = activity.dp(12) })
        }
    }

    /** Aç/kapa anahtarı görünümü (Wi-Fi panelindekiyle aynı ölçü): 72×40 hap, 32dp düğme. Dokunuş satırdadır. */
    private fun switchPill(on: Boolean): View = FrameLayout(activity).apply {
        val track = FrameLayout(activity).apply {
            background = rounded(activity, if (on) DriveAccent.buttonFill(LauncherTheme.ACCENT) else LauncherTheme.TRACK, 999f)
            addView(View(activity).apply { background = oval(if (on) LauncherTheme.ON_ACCENT else LauncherTheme.TEXT_SECONDARY) },
                FrameLayout.LayoutParams(activity.dp(32), activity.dp(32), (if (on) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL)
                    .apply { marginStart = activity.dp(4); marginEnd = activity.dp(4) })
        }
        addView(track, FrameLayout.LayoutParams(activity.dp(72), activity.dp(40), Gravity.CENTER))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Satırları dikey dizer; [fill] true ise eşit paylı (panel), değilse sabit yükseklikte (sayfa). */
    protected fun rows(views: List<View>, fill: Boolean): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        views.forEachIndexed { i, v ->
            addView(v, if (fill) LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { if (i > 0) topMargin = activity.dp(10) }
            else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(ROW_MIN_DP)).apply { if (i > 0) topMargin = activity.dp(10) })
        }
    }

    /** 4×2: solda küçük biçim (başlık, değer, detay), sağda düğme. */
    protected fun wide(value: String, valueColor: Int, detail: String?, button: View): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(compact(value, valueColor, detail), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = activity.dp(PAD_DP) })
    }

    protected fun toast(msg: String) = Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()

    protected companion object {
        const val POLL_MS = 2_000L
        const val SHEET_DP = 640
        const val PAD_DP = 16
        const val LARGE_PAD_DP = 24
        const val HEADER_ICON_DP = 28
        const val COMPACT_VALUE_SP = 34f
        const val COMPACT_VALUE_H_DP = 44
        const val ROW_MIN_DP = 72
        const val DISABLED_ALPHA = 0.45f
        const val TALL_MIN_ROWS = 4
        /** Büyük biçimde illüstrasyon yüksekliği: panelin üçte biri, düğmeye ve yazıya yer kalır. */
        const val HERO_ART_DP = 100
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}

/**
 * QR çizimi: beyaz sessiz bölge (2 modül) üstünde koyu modüller, kutunun en küçük kenarına sığan kare, ortalı. Kart değildir;
 * beyaz zemin yalnızca okunabilirlik için QR'ın kendi kenar boşluğudur. Gece de beyaz kalır (telefon kameraları açık zemin ister).
 */
internal class QrView(context: Context) : View(context) {
    private var matrix: QrMatrix? = null
    private val dark = Paint().apply { color = 0xFF111419.toInt(); isAntiAlias = false }
    private val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val box = RectF()

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** Yalnızca yükseklik belliyse (yatay satırda) genişlik yüksekliğe eşitlenir: kare kalır. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        if (wMode != MeasureSpec.EXACTLY && hMode == MeasureSpec.EXACTLY) {
            val h = MeasureSpec.getSize(heightMeasureSpec)
            val w = if (wMode == MeasureSpec.AT_MOST) minOf(h, MeasureSpec.getSize(widthMeasureSpec)) else h
            setMeasuredDimension(w, h)
            return
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun setMatrix(m: QrMatrix?) {
        if (m === matrix) return
        matrix = m
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val m = matrix ?: return
        val side = minOf(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        if (side <= 0f) return
        val modules = m.size + QUIET * 2
        // Tam piksel modül: bulanık kenar olmasın.
        val cell = kotlin.math.floor(side / modules)
        if (cell < 1f) return
        val drawn = cell * modules
        val left = paddingLeft + ((width - paddingLeft - paddingRight) - drawn) / 2f
        val top = paddingTop + ((height - paddingTop - paddingBottom) - drawn) / 2f
        box.set(left, top, left + drawn, top + drawn)
        canvas.drawRoundRect(box, cell * 1.5f, cell * 1.5f, light)
        for (y in 0 until m.size) for (x in 0 until m.size) {
            if (!m[x, y]) continue
            val l = left + (x + QUIET) * cell
            val t = top + (y + QUIET) * cell
            canvas.drawRect(l, t, l + cell, t + cell, dark)
        }
    }

    private companion object {
        const val QUIET = 2
    }
}

// ------------------------------------------------------------------------------------------------------------------
// Web paneli

/** Web yükü: durum + adresin QR'ı (adres değişmedikçe aynı örnek, böylece eşitlik sabit kalır). */
internal data class WebLoad(val state: WebPanelState, val qr: QrMatrix?)

/**
 * "Web paneli": FrostNav web yönetim sunucusunun durumu; açıkken adresin QR'ı ve adres. Kapalıyken dokunuş (ve paneldeki
 * "Başlat") sunucuyu yerinde başlatır; başlatılamazsa kısa mesajla FrostNav'ın "Aktar" sekmesi açılır. Yönetim (durdurma,
 * eşleme) "FrostNav'da yönet" ile. Durum değişince izleyiciyle ([FrostNavFeatures.watchWebPanel]) hemen yenilenir.
 * Şerit: durum + küçük QR simgesi; açıkken dokunuş büyük QR sayfasını açar. Panel: büyük QR.
 */
internal class WebPanelTile(activity: Activity) :
    FrostNavTile<WebLoad>(activity, R.string.launcher_widget_web_panel, R.drawable.ic_mdi_web, FrostNavTab.WEB) {

    private var qrUrl: String? = null
    private var qrCache: QrMatrix? = null
    private var starting = false
    private var unwatch: (() -> Unit)? = null

    override fun onVisible() {
        super.onVisible()
        unwatch?.invoke()
        unwatch = if (features.available) features.watchWebPanel { refresh() } else null
    }

    override fun onHidden() {
        unwatch?.invoke()
        unwatch = null
        super.onHidden()
    }

    /** Sunucuyu yerinde başlatır; olmazsa nedeni söyleyip FrostNav'ın Aktar sekmesini açar. */
    private fun start() {
        if (starting) return
        starting = true
        redraw()
        features.setWebPanel(true) { msg ->
            starting = false
            redraw()
            refresh()
            if (msg != null) {
                toast(msg)
                openSettings()
            }
        }
    }

    private fun startLabel(): String =
        activity.getString(if (starting) R.string.launcher_fn_web_starting else R.string.launcher_fn_web_start)

    override fun load(): WebLoad {
        val s = features.readWebPanel()
        val url = s.url
        if (url != qrUrl) {
            qrUrl = url
            qrCache = url?.let { features.qr(it) }
        }
        return WebLoad(s, qrCache)
    }

    private val running: Boolean get() = state?.state?.running == true

    private fun qrView(): QrView = QrView(activity).apply {
        setMatrix(state?.qr)
        contentDescription = activity.getString(R.string.launcher_fn_qr_desc) + ", " + (state?.state?.url ?: "")
    }

    private fun note(s: WebPanelState): String =
        activity.getString(if (s.reachable) R.string.launcher_fn_web_scan else R.string.launcher_fn_web_local)

    override fun buildCompact(): View {
        val s = state?.state
        if (s == null || !s.running) return compact(activity.getString(R.string.launcher_toggle_off), LauncherTheme.TEXT_SECONDARY,
            startLabel())
        return compact(activity.getString(R.string.launcher_toggle_on), LauncherTheme.ACCENT, s.url?.removePrefix("http://"),
            leading = if (state?.qr != null) qrView() else null)
    }

    /** Panel başlığının sağı: durum ve (açıkken) FrostNav'da yönet düğmesi; QR'a en çok yer kalsın diye ayrı satır yok. */
    private fun largeTrailing(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(stateLabel(running))
        if (running) addView(iconButton(activity, R.drawable.ic_mdi_open_in_new, activity.getString(R.string.launcher_fn_web_manage), sizeDp = 56) {
            openSettings()
        }.apply { layoutParams = LinearLayout.LayoutParams(activity.dp(56), activity.dp(56)).apply { marginStart = activity.dp(14) } })
    }

    override fun buildLarge(): View = largeFrame(largeTrailing()) {
        val s = state?.state
        if (s == null || !s.running) {
            addView(View(activity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(heroArt("web_panel", on = false) ?: icon(activity, R.drawable.ic_sahne_qr, 96, LauncherTheme.TEXT_SECONDARY),
                LinearLayout.LayoutParams(MATCH, activity.dp(HERO_ART_DP)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(text(activity, LauncherTheme.BODY_SP, LauncherTheme.TEXT_SECONDARY).apply {
                setText(R.string.launcher_fn_web_off_detail)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = activity.dp(16) })
            addView(View(activity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(pill(startLabel(), primary = true) { start() },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
            return@largeFrame
        }
        addView(qrView(), LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = activity.dp(8) })
        addView(text(activity, LauncherTheme.BODY_SP, LauncherTheme.TEXT, medium = true).apply {
            text = s.url
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = activity.dp(10) })
        // Telefonun okuyamayacağı adres (yalnızca 127.x) uyarısı; normalde QR kendini anlatır.
        if (!s.reachable) addView(text(activity, LauncherTheme.LABEL_SP - 2f, LauncherTheme.TEXT_SECONDARY).apply {
            text = note(s)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = activity.dp(4) })
    }

    override fun onTap() {
        when {
            running && state?.qr != null -> openSheet()
            running -> openSettings()
            else -> start()
        }
    }

    override fun buildSheet(box: LinearLayout, dialog: Dialog) {
        val s = state?.state
        if (s == null || !s.running) {
            dialog.dismiss()
            return
        }
        box.addView(qrView(), LinearLayout.LayoutParams(activity.dp(SHEET_QR_DP), activity.dp(SHEET_QR_DP)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        box.addView(text(activity, LauncherTheme.SECTION_SP - 6f, LauncherTheme.TEXT, medium = true).apply {
            text = s.url
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = activity.dp(16) })
        box.addView(text(activity, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY).apply {
            text = note(s)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = activity.dp(6) })
        box.addView(pill(activity.getString(R.string.launcher_fn_web_manage)) { dialog.dismiss(); openSettings() },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = activity.dp(20) })
    }

    override fun strip(): StripState {
        val s = state?.state
        val on = s?.running == true
        return StripState(activity.getString(labelRes),
            activity.getString(if (on) R.string.launcher_toggle_on else R.string.launcher_toggle_off),
            iconRes, if (on) StripTone.ON else StripTone.NORMAL,
            detail = if (on) s?.url else startLabel(), active = on)
    }

    private companion object {
        /**
         * Sayfadaki QR kenarı. 320'de 720 px yüksek ekranda (Tiggo 7, 160 dpi) sayfa ekrana sığmıyor, "FrostNav'da yönet"
         * düğmesi alttan kesiliyordu; 240 başlık + QR + adres + not + düğmeyi sığdırır, telefon kamerası yine rahat okur.
         */
        const val SHEET_QR_DP = 240
    }
}

// ------------------------------------------------------------------------------------------------------------------
// Ekolayzer

/**
 * "Ekolayzer": FrostNav ekolayzerinin etkin hazır ayarı. Küçük biçim ve şerit dokununca hazır ayar sayfası açılır; panelde
 * hazır ayarlar büyük seçilebilir satırlardır. Seçim FrostNav'ın ekolayzer sekmesiyle aynı yoldan kaydedilir ve ekolayzeri açar.
 */
internal class EqualizerTile(activity: Activity) :
    FrostNavTile<EqState>(activity, R.string.launcher_widget_equalizer, R.drawable.ic_mdi_equalizer, FrostNavTab.EQUALIZER) {

    override fun load(): EqState = features.readEqualizer()

    private fun presetLabel(id: String): String = activity.getString(when (id) {
        "flat" -> R.string.equalizer_preset_flat
        "bass" -> R.string.equalizer_preset_bass
        "vocal" -> R.string.equalizer_preset_vocal
        "treble" -> R.string.equalizer_preset_treble
        else -> R.string.equalizer_preset_custom
    })

    private fun choose(preset: String) {
        val s = state ?: return
        state = s.copy(enabled = true, preset = preset)
        redraw()
        act { features.setEqualizer(true, preset) }
    }

    private fun setEnabled(on: Boolean) {
        val s = state ?: return
        state = s.copy(enabled = on)
        redraw()
        act { features.setEqualizer(on, s.preset) }
    }

    /** Aç/kapa satırı + hazır ayar satırları; desteklenmiyorsa açıklama ve ayarlar düğmesi. */
    private fun list(fill: Boolean): View {
        val s = state ?: return View(activity)
        if (!s.supported) return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(text(activity, LauncherTheme.BODY_SP, LauncherTheme.TEXT_SECONDARY, maxLines = 2).apply {
                setText(R.string.launcher_fn_eq_unsupported); gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(pill(activity.getString(R.string.launcher_fn_open_settings)) { openSettings() },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = activity.dp(16) })
        }
        val views = ArrayList<View>()
        views += row(activity.getString(labelRes), switchOn = s.enabled) { setEnabled(!s.enabled) }
        s.presets.forEach { p ->
            views += row(presetLabel(p), selected = s.enabled && p == s.preset) { choose(p) }
        }
        return rows(views, fill)
    }

    override fun buildCompact(): View {
        val s = state ?: return compact(activity.getString(R.string.launcher_unavailable), LauncherTheme.TEXT_SECONDARY, null)
        if (!s.supported) return compact(activity.getString(R.string.launcher_unavailable), LauncherTheme.TEXT_SECONDARY,
            activity.getString(R.string.launcher_fn_eq_unsupported))
        return compact(presetLabel(s.preset), if (s.enabled) LauncherTheme.ACCENT else LauncherTheme.TEXT_SECONDARY,
            activity.getString(if (s.enabled) R.string.launcher_toggle_on else R.string.launcher_toggle_off))
    }

    /** Panel: aç/kapa başlıkta, hazır ayarlar 2×2 büyük kutular (satır listesi panele sığmaz). */
    override fun buildLarge(): View {
        val s = state
        val trailing = if (s != null && s.supported) switchButton(s.enabled, activity.getString(labelRes)) { setEnabled(!s.enabled) } else null
        return largeFrame(trailing) {
            if (s == null || !s.supported) {
                addView(list(fill = true), LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = activity.dp(12) })
                return@largeFrame
            }
            val cells = s.presets.map { p ->
                text(activity, LauncherTheme.SECTION_SP - 8f, LauncherTheme.TEXT, medium = true).apply {
                    val selected = s.enabled && p == s.preset
                    text = presetLabel(p)
                    gravity = Gravity.CENTER
                    setTextColor(if (selected) LauncherTheme.ON_ACCENT else LauncherTheme.TEXT)
                    background = rounded(activity, if (selected) DriveAccent.buttonFill(LauncherTheme.ACCENT) else LauncherTheme.CONTROL, 18f)
                    contentDescription = presetLabel(p)
                    isSelected = selected
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { choose(p) }
                    pressFeedback()
                }
            }
            addView(grid(cells, 2), LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = activity.dp(12) })
        }
    }

    override fun onTap() {
        if (state?.supported == false) openSettings() else openSheet()
    }

    override fun buildSheet(box: LinearLayout, dialog: Dialog) {
        box.addView(list(fill = false), sheetFullWidth())
    }

    override fun strip(): StripState {
        val s = state
        val on = s?.enabled == true && s.supported
        val value = when {
            s == null -> activity.getString(R.string.launcher_unavailable)
            !s.supported -> activity.getString(R.string.launcher_unavailable)
            !s.enabled -> activity.getString(R.string.launcher_toggle_off)
            else -> presetLabel(s.preset)
        }
        return StripState(activity.getString(labelRes), value, iconRes, if (on) StripTone.ON else StripTone.NORMAL, active = on)
    }
}

// ------------------------------------------------------------------------------------------------------------------
// Otomasyon

/**
 * "Otomasyon": FrostNav'ın aç/kapa otomasyonları (klima otomasyonu, cam tavan otomasyonu; yüz tanıma yalnızca durum).
 * Küçük biçim ve şerit dokununca anahtar sayfası açılır; panelde her biri bir anahtar satırıdır. Anahtarlar FrostNav ayar
 * sekmelerinin ana anahtarlarıyla aynı yoldan yazılır.
 */
internal class AutomationTile(activity: Activity) :
    FrostNavTile<List<AutomationItem>>(activity, R.string.launcher_widget_automation, R.drawable.ic_mdi_restart, FrostNavTab.CLIMATE_AUTOMATION) {

    override fun load(): List<AutomationItem> = features.readAutomations()

    private fun name(id: AutomationId): String = activity.getString(when (id) {
        AutomationId.CLIMATE -> R.string.launcher_fn_auto_climate
        AutomationId.SUNROOF -> R.string.launcher_fn_auto_sunroof
        AutomationId.FACE -> R.string.launcher_fn_auto_face
    })

    private fun tabOf(id: AutomationId) = when (id) {
        AutomationId.CLIMATE -> FrostNavTab.CLIMATE_AUTOMATION
        AutomationId.SUNROOF -> FrostNavTab.SUNROOF_AUTOMATION
        AutomationId.FACE -> FrostNavTab.FACE_RECOGNITION
    }

    private fun toggle(item: AutomationItem) {
        if (!item.toggleable) {
            openTab(tabOf(item.id))
            return
        }
        val want = !item.on
        state = state?.map { if (it.id == item.id) it.copy(on = want) else it }
        redraw()
        act { features.setAutomation(item.id, want) }
    }

    private fun list(fill: Boolean): View = rows(state.orEmpty().map { item ->
        if (item.toggleable) row(name(item.id), switchOn = item.on) { toggle(item) }
        else row(name(item.id), trailingText = activity.getString(if (item.on) R.string.launcher_toggle_on else R.string.launcher_toggle_off), opens = true) { toggle(item) }
    }, fill)

    private fun summary(): Pair<String, String?> {
        val items = state.orEmpty()
        val (on, _) = FrostNavWidgetMath.automationCount(items)
        if (on == 0) return activity.getString(R.string.launcher_toggle_off) to null
        return activity.getString(R.string.launcher_fn_auto_on_count, on) to items.filter { it.on }.joinToString(" · ") { name(it.id) }
    }

    override fun buildCompact(): View {
        val (value, detail) = summary()
        val any = state.orEmpty().any { it.on }
        return compact(value, if (any) LauncherTheme.ACCENT else LauncherTheme.TEXT_SECONDARY, detail)
    }

    override fun buildLarge(): View = largeFrame(null) {
        addView(list(fill = true), LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = activity.dp(12) })
    }

    override fun onTap() = openSheet()

    override fun buildSheet(box: LinearLayout, dialog: Dialog) {
        box.addView(list(fill = false), sheetFullWidth())
    }

    override fun strip(): StripState {
        val (value, detail) = summary()
        val any = state.orEmpty().any { it.on }
        return StripState(activity.getString(labelRes), value, iconRes, if (any) StripTone.ON else StripTone.NORMAL, detail = detail, active = any)
    }
}

// ------------------------------------------------------------------------------------------------------------------
// Bölünmüş ekran

/**
 * Bölünmüş ekran kutusunun yazı kararları (saf, JVM testli). FrostNav'ın seçili çifti düz paket ("pkg") ya da bileşen
 * ("pkg/.Sinif") olabilir; bileşende `getApplicationInfo` başarısız olup alt başlıkta ham bileşen adı görünüyordu.
 */
internal object SplitTileText {
    /** Seçimden paket adı: bileşense '/' öncesi; boşsa null. */
    fun packageOf(entry: String?): String? {
        val e = entry?.trim().orEmpty()
        if (e.isEmpty()) return null
        val i = e.indexOf('/')
        return (if (i >= 0) e.substring(0, i) else e).trim().ifEmpty { null }
    }

    /** Bileşendeki tam sınıf adı (".Sinif" paketle tamamlanır); bileşen değilse null. (Test derlemesi util/'u görmez, burada.) */
    fun classOf(entry: String?): String? {
        val e = entry?.trim().orEmpty()
        val i = e.indexOf('/')
        if (i <= 0 || i >= e.length - 1) return null
        val cls = e.substring(i + 1)
        return if (cls.startsWith(".")) e.substring(0, i) + cls else cls
    }

    /** Ad çözülemezse gösterilecek yazı: ham bileşen değil, paket adı. */
    fun display(entry: String?, label: String?): String? = label?.takeIf { it.isNotBlank() } ?: packageOf(entry)

    /** Kendi Mini göstergemizin sınıfı (util/SplitSlot.MINI_DASH_CLASS ile aynı; test derlemesi util/'u görmez). */
    const val MINI_DASH_CLASS = "com.mapcontrol.launcher.MiniDashActivity"

    /** Seçim kendi paketimizin bir bileşeni mi (Mini gösterge ya da harita seçici). */
    fun isOwn(entry: String?, selfPkg: String): Boolean = classOf(entry) != null && packageOf(entry) == selfPkg

    /** Seçim kendi Mini göstergemiz mi. */
    fun isMiniDash(entry: String?, selfPkg: String): Boolean = isOwn(entry, selfPkg) && classOf(entry) == MINI_DASH_CLASS

    /**
     * Kutunun açacağı bölme (7 Kasım kararı: tek bölünmüş ekran deneyimi). FrostNav'da Mini gösterge İÇERMEYEN iki başka uygulama
     * seçiliyse o çift FrostNav'ın kendi bölmesiyle açılır ([SplitRoute.PAIR]); diğer her durumda (çift yok / eksik, Mini
     * gösterge + herhangi bir uygulama, kendi bileşenimiz) Navigasyon kartıyla aynı sürüş ekranı ([SplitRoute.DRIVE]; harita
     * kartın seçimi, [DriveScreen.openDefault]).
     */
    fun route(pair: SplitPair, selfPkg: String): SplitRoute {
        if (!FrostNavWidgetMath.splitReady(pair)) return SplitRoute.DRIVE
        return if (isOwn(pair.first, selfPkg) || isOwn(pair.second, selfPkg)) SplitRoute.DRIVE else SplitRoute.PAIR
    }
}

/** "Bölünmüş ekran" kutusunun açtığı bölme: sürüş ekranı (Mini gösterge + harita) ya da FrostNav'da seçili iki başka uygulama. */
internal enum class SplitRoute { DRIVE, PAIR }

/**
 * Seçili çiftin uygulama adları (paket yoksa null), açılacak bölme ve sürüş ekranının harita yarısı ([mapPkg] null = seçici;
 * [mapLabel] haritanın adı).
 */
internal data class SplitLoad(
    val pair: SplitPair,
    val firstLabel: String?,
    val secondLabel: String?,
    val route: SplitRoute = SplitRoute.DRIVE,
    val mapPkg: String? = null,
    val mapLabel: String? = null,
)

/**
 * "Bölünmüş ekran": tek dokunuşla Navigasyon kartıyla aynı sürüş ekranı (Mini gösterge + kartın haritası, [DriveScreen]; 7 Kasım
 * kararı, FrostNav'ın eski Mini gösterge bölmesi artık açılmaz). FrostNav'da Mini gösterge içermeyen iki başka uygulama seçildiyse
 * o çift FrostNav'ın kendi bölünmüş ekran başlatıcısı ve oranıyla açılır ([SplitTileText.route]). Ayarlar şeritte uzun basışta.
 * Başlatılamazsa FrostNav'ın kendi mesajı gösterilir.
 */
internal class SplitScreenTile(activity: Activity) :
    FrostNavTile<SplitLoad>(activity, R.string.launcher_widget_split_screen, R.drawable.ic_mdi_view_split, FrostNavTab.SPLIT_SCREEN) {

    private var starting = false
    private val icons = HashMap<String, Drawable?>()

    override fun load(): SplitLoad {
        val p = features.readSplitPair()
        val route = SplitTileText.route(p, activity.packageName)
        val map = if (route == SplitRoute.DRIVE) DriveScreen.defaultMap(activity) else null
        return SplitLoad(p, label(p.first), label(p.second), route, map, map?.let { label(it) })
    }

    /** Seçimin görünen adı: bileşense etkinliğin adı (yoksa uygulamanınki), düz paketse uygulama adı. */
    private fun label(entry: String?): String? {
        val pkg = SplitTileText.packageOf(entry) ?: return null
        val pm = activity.packageManager
        val cls = SplitTileText.classOf(entry)
        if (cls != null) {
            try {
                return pm.getActivityInfo(android.content.ComponentName(pkg, cls), 0).loadLabel(pm).toString()
            } catch (e: Exception) {
                // etkinlik bulunamadı: uygulama adına düşülür
            }
        }
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun appIcon(entry: String?): Drawable? {
        val pkg = SplitTileText.packageOf(entry) ?: return null
        return icons.getOrPut(pkg) { try { activity.packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null } }
    }

    /** Alt yazı: "Mini gösterge + Yandex Haritalar" (harita yoksa "+ harita") ya da FrostNav çifti "A + B". */
    private fun subtitle(): String? {
        val s = state ?: return null
        if (s.route == SplitRoute.PAIR) {
            return "${SplitTileText.display(s.pair.first, s.firstLabel)} + ${SplitTileText.display(s.pair.second, s.secondLabel)}"
        }
        val map = SplitTileText.display(s.mapPkg, s.mapLabel)
        return if (map == null) activity.getString(R.string.launcher_fn_split_drive_map)
            else activity.getString(R.string.launcher_fn_split_drive, map)
    }

    /** Dokunuş (kutucuk, şerit, panel düğmesi): sürüş ekranı ya da FrostNav çifti. */
    private fun start() {
        if (starting) return
        val s = state
        if (s == null || s.route == SplitRoute.DRIVE) {
            // Navigasyon kartıyla aynı yol: harita kartın seçimi, yoksa "+ Harita ekle" seçicisi; bölme olmazsa harita tam ekran.
            DriveScreen.openDefault(activity)
            return
        }
        starting = true
        redraw()
        features.startSplit { msg ->
            starting = false
            redraw()
            if (msg != null) toast(msg)
        }
    }

    private fun startLabel(): String =
        activity.getString(if (starting) R.string.launcher_fn_split_starting else R.string.launcher_fn_split_start)

    override fun buildCompact(): View = compact(startLabel(), LauncherTheme.ACCENT, subtitle())

    override fun buildLarge(): View = largeFrame(null) {
        val s = state
        addView(View(activity), LinearLayout.LayoutParams(1, 0, 1f))
        if (s != null) {
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                if (s.route == SplitRoute.PAIR) {
                    addView(app(s.pair.first, s.firstLabel), LinearLayout.LayoutParams(0, WRAP, 1f))
                    addView(icon(activity, R.drawable.ic_mdi_plus, 28, LauncherTheme.TEXT_SECONDARY))
                    addView(app(s.pair.second, s.secondLabel), LinearLayout.LayoutParams(0, WRAP, 1f))
                } else {
                    addView(app(activity.packageName, activity.getString(R.string.launcher_mini_title)), LinearLayout.LayoutParams(0, WRAP, 1f))
                    addView(icon(activity, R.drawable.ic_mdi_plus, 28, LauncherTheme.TEXT_SECONDARY))
                    addView(app(s.mapPkg, s.mapLabel ?: activity.getString(R.string.launcher_fn_split_drive_map)), LinearLayout.LayoutParams(0, WRAP, 1f))
                }
            }, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        addView(View(activity), LinearLayout.LayoutParams(1, 0, 1f))
        addView(pill(startLabel(), primary = true) { start() }, LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
    }

    override fun buildWide(): View = wide(
        subtitle() ?: activity.getString(labelRes),
        LauncherTheme.TEXT,
        null,
        pill(startLabel(), primary = true) { start() },
    )

    /** Uygulama ikonu + adı (üstte ikon). */
    private fun app(pkg: String?, label: String?): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(ImageView(activity).apply {
            setImageDrawable(appIcon(pkg))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(activity.dp(72), activity.dp(72)))
        addView(text(activity, LauncherTheme.LABEL_SP, LauncherTheme.TEXT).apply { text = SplitTileText.display(pkg, label); gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = activity.dp(8) })
    }

    override fun onTap() = start()

    override fun strip(): StripState = StripState(
        activity.getString(labelRes),
        if (starting) activity.getString(R.string.launcher_fn_split_starting)
        else subtitle() ?: activity.getString(R.string.launcher_fn_split_drive_map),
        iconRes,
        StripTone.NORMAL,
        detail = null,
        active = true,
    )
}

// ------------------------------------------------------------------------------------------------------------------
// Göstergeye yansıt

/** Yansıtma yükü: açık mı ve hedef uygulamanın adı. */
internal data class ClusterLoad(val open: Boolean, val targetLabel: String?)

/**
 * "Göstergeye yansıt": FrostNav'ın gösterge yansıtmasını (Yansıtma sekmesinde seçilen uygulama göstergede) başlatır/durdurur.
 * Yalnızca FrostNav servisinin aç/kapa giriş noktası çağrılır; gezinme, HUD ve gösterge çizimi koduna dokunulmaz.
 * Dokunuştan sonra istenen durum, servis bildirene dek ([FrostNavWidgetMath.PENDING_MS]) gösterilir.
 */
internal class ClusterMirrorTile(activity: Activity) :
    FrostNavTile<ClusterLoad>(activity, R.string.launcher_widget_cluster_mirror, R.drawable.ic_mdi_speedometer, FrostNavTab.PROJECTION) {

    private var pending: Boolean? = null
    private var pendingAt = 0L
    private val settle = Runnable { redraw() }

    override fun load(): ClusterLoad {
        val c = features.readCluster()
        val label = c.target?.let { pkg ->
            try { activity.packageManager.let { pm -> pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } } catch (e: Exception) { pkg }
        }
        return ClusterLoad(c.open, label)
    }

    private val open: Boolean
        get() = FrostNavWidgetMath.clusterShown(state?.open == true, pending, SystemClock.uptimeMillis() - pendingAt)

    private fun toggle() {
        val want = !open
        pending = want
        pendingAt = SystemClock.uptimeMillis()
        redraw()
        main.removeCallbacks(settle)
        main.postDelayed(settle, FrostNavWidgetMath.PENDING_MS)
        act { features.setCluster(want) }
    }

    private fun target(): String = state?.targetLabel ?: activity.getString(R.string.launcher_fn_cluster_no_target)

    override fun buildCompact(): View = compact(
        activity.getString(if (open) R.string.launcher_toggle_on else R.string.launcher_toggle_off),
        if (open) LauncherTheme.ACCENT else LauncherTheme.TEXT_SECONDARY, target())

    override fun buildLarge(): View = largeFrame(stateLabel(open)) {
        addView(View(activity), LinearLayout.LayoutParams(1, 0, 1f))
        heroArt("cluster_mirror", on = open)?.let { addView(it, LinearLayout.LayoutParams(MATCH, activity.dp(HERO_ART_DP))) }
        addView(text(activity, LauncherTheme.SECTION_SP - 4f, if (open) LauncherTheme.ACCENT else LauncherTheme.TEXT, medium = true).apply {
            text = target(); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = activity.dp(8) })
        addView(View(activity), LinearLayout.LayoutParams(1, 0, 1f))
        addView(pill(activity.getString(if (open) R.string.launcher_fn_cluster_stop else R.string.launcher_fn_cluster_start), primary = !open) { toggle() },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
    }

    override fun buildWide(): View = wide(
        activity.getString(if (open) R.string.launcher_toggle_on else R.string.launcher_toggle_off),
        if (open) LauncherTheme.ACCENT else LauncherTheme.TEXT_SECONDARY, target(),
        pill(activity.getString(if (open) R.string.launcher_fn_cluster_stop else R.string.launcher_fn_cluster_start), primary = !open) { toggle() },
    )

    override fun onTap() = toggle()

    override fun onHidden() {
        super.onHidden()
        main.removeCallbacks(settle)
    }

    override fun strip(): StripState = StripState(
        activity.getString(labelRes),
        activity.getString(if (open) R.string.launcher_toggle_on else R.string.launcher_toggle_off),
        iconRes, if (open) StripTone.ON else StripTone.NORMAL, detail = target(), active = open)
}
