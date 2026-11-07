package com.mapcontrol.launcher

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import com.mapcontrol.R
import java.util.concurrent.Executor

/**
 * Konfor kutucuklarının ortak gövdesi: 3 sn'de bir araçtan okur, dokunuşta hedefi hemen gösterir ("iyimser"),
 * komut yalnızca [ComfortWriter] kapısı açıksa gider. Kapı kapalıyken (araç testi öncesi) ekran ~1,5 sn sonra aracın
 * gerçek durumuna döner ve bilgi mesajı gösterilir; klima kapsülüyle aynı davranış.
 */
internal abstract class ComfortTile(protected val context: Context) : SizedWidget, Themeable, StripSource {

    // --- Şerit (Sahne): alt sınıflar [render] sonunda [stripNow]'ı doldurup [notifyStrip] çağırır ---
    override var onStripChanged: (() -> Unit)? = null
    protected var stripNow: StripState? = null
    override fun stripState(): StripState? = stripNow
    override fun stripTap(part: Int) { view.performClick() }
    protected fun notifyStrip(state: StripState) {
        stripNow = state
        onStripChanged?.invoke()
    }

    /** Tema bağlı atamalar (kurucuda bir kez, [applyTheme]'de yeniden); bkz. [ThemeBindings]. */
    protected val theme = ThemeBindings()

    protected val main = Handler(Looper.getMainLooper())
    protected val io: Executor = LauncherIo.serial()
    protected var reading = ComfortReading()
        private set

    private var startup: LauncherStartup.Deferred? = null
    private var toast: Toast? = null

    private val poll = object : Runnable {
        override fun run() {
            refreshNow()
            main.postDelayed(this, pollIntervalMs())
        }
    }

    private val revert = Runnable { renderIfIdle() }

    /** Sonraki okumaya kadar bekleme; bagaj hareket halindeyken kısalır. */
    protected open fun pollIntervalMs(): Long = POLL_MS

    /** Arka plan thread'inde okuma; bagaj kutucuğu kendi sinyallerini de okutur. */
    protected open fun readNow(): ComfortReading = ComfortReader.read()

    /** Okumayı ve bekleyen (iyimser) hedefi ekrana yansıtır. */
    protected abstract fun render()

    /** Kullanıcı bir şey sürüklüyorsa çizimi atlar. */
    protected open fun busy(): Boolean = false

    /** Bekleyen iyimser hedefleri temizler (revert/okuma sonrası). */
    protected abstract fun clearPending()

    /**
     * Tema değişti: sabit renkler ([theme]) yeniden atanır, ardından [render] durum rengi taşıyan her şeyi (ikon, yazı,
     * düğme zemini, çizim) yeni renklerle yeniden kurar; bekleyen iyimser hedef ve okuma korunur.
     */
    override fun applyTheme() {
        theme.reapply()
        render()
    }

    override fun onVisible() {
        render()
        startup?.cancel()
        startup = LauncherStartup.later { main.post(poll) }
    }

    override fun onHidden() {
        startup?.cancel()
        main.removeCallbacks(poll)
        main.removeCallbacks(revert)
        toast?.cancel()
        onStopped()
    }

    override fun release() = Unit

    protected open fun onStopped() = Unit

    private fun refreshNow() {
        io.runSafely {
            val read = readNow()
            main.post {
                if (read == reading) return@post
                reading = read
                // Araçtan yeni değer geldi: iyimser hedef artık gereksiz.
                if (!busy()) { clearPending(); render() }
            }
        }
    }

    private fun renderIfIdle() {
        clearPending()
        if (!busy()) render()
    }

    /**
     * Bir değişiklik işlendi. Yazma açıksa aracın yeni değeri okunana kadar ([CONFIRM_MS]) hedef gösterilir;
     * kapalıysa ~[REVERT_MS] sonra araçtakine dönülür ve neden söylenir.
     */
    protected fun afterCommit(writeEnabled: Boolean) {
        render()
        main.removeCallbacks(revert)
        if (writeEnabled) {
            main.postDelayed(revert, CONFIRM_MS)
            return
        }
        main.postDelayed(revert, REVERT_MS)
        showToast(if (LauncherEdition.READ_ONLY) R.string.launcher_read_only_comfort else R.string.launcher_comfort_pending)
    }

    protected fun showToast(msg: Int) {
        toast?.cancel()
        toast = Toast.makeText(context, msg, Toast.LENGTH_LONG).also { it.show() }
    }

    protected fun onFill() = DriveAccent.buttonFill(LauncherTheme.ACCENT)

    companion object {
        const val POLL_MS = 3_000L
        const val REVERT_MS = 1_500L
        const val CONFIRM_MS = 2_500L
    }
}

// ---------------------------------------------------------------------------------------------
// Bagaj
// ---------------------------------------------------------------------------------------------

/**
 * Bagaj kapağı: yandan bakışla araç siluetinde kapağın açılma açısı ve yüzde. Dokun = kapalıysa aç; açıksa "Bagaj kapansın mı?"
 * onayıyla kapat; hareket halindeyse durdur (yalnızca durdurma değeri doğrulandıysa). Okuma 3 sn'de bir, hareket halinde 500 ms.
 * Yazma [ComfortWriter.TRUNK_WRITE_VERIFIED] true iken gider (sürüş kilidi yok; araç reddederse geri okuma gösterir); o zamana dek
 * kutucuk salt-okurdur ve dokunuş "henüz doğrulanmadı" ipucu gösterir.
 */
internal class TrunkTile(context: Context) : ComfortTile(context) {

    private val car = TrunkView(context)
    private val title = text(context, LABEL_SP).apply { theme.bind { setTextColor(LauncherTheme.TEXT_SECONDARY) } }
    private val stateText = text(context, VALUE_SP, medium = true).apply { theme.bind { setTextColor(LauncherTheme.TEXT) } }
    private val iconView: ImageView = icon(context, R.drawable.ic_mdi_car_trunk, ICON_DP).also { v -> theme.bind { v.setTint(LauncherTheme.TEXT_SECONDARY) } }

    /** İyimser hareket (true açılıyor, false kapanıyor); null = araçtaki gösterilir. */
    private var pendingOpening: Boolean? = null
    private var dialog: AlertDialog? = null

    private val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    private val bottomGap = gap(context, 8, vertical = true)

    private val root: LinearLayout = card(context, LinearLayout.VERTICAL, PADDING_DP).apply {
        theme.bind { background = cardSurface(context) }
        addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(gap(context, 8, vertical = true))
        addView(car, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(bottomGap)
        isClickable = true
        isFocusable = true
        setOnClickListener { onTap() }
        pressFeedback()
    }

    init {
        title.setText(R.string.launcher_widget_trunk)
        // Çizim renkleri çizimde okunur; tema değişince yalnızca yeniden çizilir.
        theme.bind { car.invalidate() }
    }

    override val view: View get() = root

    override fun readNow(): ComfortReading = ComfortReader.read(withTrunk = true)

    private fun ownState(): TrunkState = TrunkMath.decode(reading.trunk).takeIf { reading.connected } ?: TrunkState.UNKNOWN

    override fun pollIntervalMs(): Long = if (state().moving || pendingOpening != null) FAST_POLL_MS else POLL_MS

    override fun applySpan(span: Span) {
        // Yüksek (2x4): üstte ikon + başlık, silüet, altta ortalı büyük durum. Alçak (2x2): ikon + durum tek satırda.
        val tall = span.h >= TALL_ROWS
        header.removeAllViews()
        (stateText.parent as? android.view.ViewGroup)?.removeView(stateText)
        header.addView(iconView)
        header.addView(gap(context, 10))
        if (tall) {
            header.addView(title)
            root.addView(stateText, root.indexOfChild(bottomGap) + 1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            stateText.gravity = Gravity.CENTER
        } else {
            header.addView(stateText)
        }
        bottomGap.visibility = if (tall) View.VISIBLE else View.GONE
        val pad = context.dp(if (tall) PADDING_DP else COMPACT_PADDING_DP)
        root.setPadding(pad, pad, pad, pad)
    }

    override fun clearPending() { pendingOpening = null }

    /** Sahne aracı ve uyarı satırıyla aynı kaynak: araç gövdesi bagajı açık diyorsa kutucuk da "Açık" der. */
    private val openingsListener: (Openings?) -> Unit = { render() }

    override fun onVisible() {
        OpeningsBus.add(openingsListener)
        super.onVisible()
    }

    override fun onStopped() {
        OpeningsBus.remove(openingsListener)
        car.cancelAnim()
        dialog?.dismiss()
        dialog = null
    }

    /** Konfor okuması kapalı/bilinmiyor derken gövde sinyali bagajı açık görüyorsa açık sayılır (tek doğruluk). */
    private fun state(): TrunkState {
        val own = ownState()
        val bodyOpen = OpeningsBus.current?.trunk == true
        return if (bodyOpen && (own == TrunkState.CLOSED || own == TrunkState.UNKNOWN)) TrunkState.OPEN else own
    }

    override fun render() {
        val state = state()
        val known = state != TrunkState.UNKNOWN
        val pending = pendingOpening
        val fraction = TrunkMath.openFraction(reading.trunk.takeIf { reading.connected })
        val percent = TrunkMath.percent(reading.trunk?.position)
        car.known = known || pending != null
        val target = when {
            pending != null -> if (pending) 1f else 0f
            else -> fraction
        }
        if (target != null) car.animateTo(target)
        val open = pending == true || (pending == null && state != TrunkState.CLOSED && known)
        val str = when {
            pending != null -> context.getString(if (pending) R.string.launcher_trunk_opening else R.string.launcher_trunk_closing)
            !known -> context.getString(R.string.launcher_unavailable)
            else -> when (state) {
                TrunkState.CLOSED -> context.getString(R.string.launcher_trunk_closed)
                TrunkState.OPENING -> context.getString(R.string.launcher_trunk_opening)
                TrunkState.CLOSING -> context.getString(R.string.launcher_trunk_closing)
                TrunkState.OPEN -> context.getString(R.string.launcher_trunk_open)
                else -> if (percent != null) context.getString(R.string.launcher_trunk_percent, percent) else context.getString(R.string.launcher_trunk_open)
            }
        }
        stateText.setTextSmooth(str)
        iconView.imageTintList = ColorStateList.valueOf(if (open) LauncherTheme.ACCENT else LauncherTheme.TEXT_SECONDARY)
        stateText.setTextColor(if (known || pending != null) (if (open) LauncherTheme.ACCENT else LauncherTheme.TEXT) else LauncherTheme.TEXT_SECONDARY)
        root.contentDescription = context.getString(R.string.launcher_widget_trunk) + ", " + str
        root.isEnabled = known || LauncherEdition.READ_ONLY
        notifyStrip(StripState(context.getString(R.string.launcher_widget_trunk), str, R.drawable.ic_mdi_car_trunk,
            // Açık bagaj bir uyarıdır (sahne satırıyla aynı turuncu); hareket hâlinde buz mavisi.
            when { !(known || pending != null) -> StripTone.MUTED; state == TrunkState.OPEN && pending == null -> StripTone.ALERT; open -> StripTone.ON; else -> StripTone.NORMAL },
            active = open))
    }

    override fun stripTap(part: Int) = onTap()

    // --- Dokunma ---

    private fun onTap() {
        val state = state()
        // Doğrulanana kadar salt-okur: hiçbir komut hazırlanmaz, yalnızca ipucu.
        if (!ComfortWriter.trunkEnabled) {
            showToast(if (LauncherEdition.READ_ONLY) R.string.launcher_read_only_comfort else R.string.launcher_trunk_unverified)
            return
        }
        when (TrunkMath.tapAction(state, ComfortWriter.trunkStopKnown)) {
            TrunkAction.OPEN -> write(open = true)
            TrunkAction.CLOSE_CONFIRM -> confirmClose()
            TrunkAction.STOP -> write(open = null)
            TrunkAction.STOP_UNKNOWN -> showToast(R.string.launcher_trunk_stop_unknown)
            TrunkAction.NONE -> Unit
        }
    }

    /** Kapatma, arkada biri varken tehlikeli olabilir: her seferinde onay istenir. */
    private fun confirmClose() {
        val host = context as? android.app.Activity
        if (host != null && (host.isFinishing || host.isDestroyed)) return
        dialog?.dismiss()
        dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.launcher_trunk_close_title)
            .setMessage(R.string.launcher_trunk_close_message)
            .setPositiveButton(R.string.launcher_trunk_close_confirm) { _, _ -> write(open = false) }
            .setNegativeButton(android.R.string.cancel, null)
            .create().also { it.hideOemBarIfNeeded(context); it.show() }
    }

    /** [open] true aç, false kapat, null durdur. Yazma arka planda; sonuca göre ekran güncellenir. */
    private fun write(open: Boolean?) {
        io.runSafely {
            val result = if (open == null) ComfortWriter.stopTrunk() else ComfortWriter.setTrunk(open)
            main.post {
                when (result) {
                    TrunkWriteResult.SENT -> {
                        if (open != null) pendingOpening = open
                        afterCommit(true)
                    }
                    TrunkWriteResult.DISABLED -> showToast(R.string.launcher_trunk_unverified)
                    TrunkWriteResult.STALE -> showToast(R.string.launcher_trunk_stale)
                    TrunkWriteResult.TOO_SOON -> Unit
                }
            }
        }
    }

    // --- Çizim ---

    /**
     * Arkadan stilize araç, araç kartındaki şema diliyle (CONTROL dolgu, ikincil metin çizgisi, yarı saydam cam):
     * gövde, cam bölmesi, stop lambaları, tekerlekler ve bagaj kapağı. Kapak açıldıkça tavanın üstüne doğru kalkar ve
     * arkasındaki bagaj boşluğu görünür. [openness] 0 = kapalı, 1 = tam açık.
     */
    private class TrunkView(context: Context) : View(context) {
        var openness = 0f
            private set
        var known = false
            set(v) { if (field != v) { field = v; invalidate() } }

        private var anim: ValueAnimator? = null
        private var animTarget = -1f
        private var firstSet = true
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val path = android.graphics.Path()

        fun cancelAnim() { anim?.cancel(); anim = null }

        private fun setOpenness(o: Float) {
            openness = o.coerceIn(0f, 1f)
            invalidate()
        }

        /** Yalnızca hedef değişince kısa bir geçiş; ilk gösterim anında oturur. */
        fun animateTo(target: Float) {
            if (firstSet || !isAttachedToWindow || width == 0) {
                firstSet = false
                cancelAnim()
                setOpenness(target)
                return
            }
            if (anim != null && target == animTarget) return
            if (anim == null && Math.abs(target - openness) < 0.005f) return
            cancelAnim()
            animTarget = target
            anim = ValueAnimator.ofFloat(openness, target).apply {
                duration = ANIM_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener { setOpenness(it.animatedValue as Float) }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            // 100 × 68 birimlik tasarım alanı (üstteki pay tam açık kapak için), orantılı sığdırılıp ortalanır.
            val u = minOf(w / DESIGN_W, h / DESIGN_H)
            val dp = resources.displayMetrics.density / u
            canvas.save()
            canvas.translate((w - DESIGN_W * u) / 2f, (h - DESIGN_H * u) / 2f)
            canvas.scale(u, u)
            canvas.translate(0f, TOP_PAD)
            val outline = LauncherTheme.TEXT_SECONDARY
            val accent = if (known) LauncherTheme.ACCENT else outline
            val t = openness
            val opened = t > 0.02f

            // Tekerlekler (gövdenin altından taşan kısım).
            paint.style = Paint.Style.FILL
            paint.color = ColorUtils.setAlphaComponent(outline, 150)
            rect.set(15f, 44f, 27f, 57f); canvas.drawRoundRect(rect, 3f, 3f, paint)
            rect.set(73f, 44f, 85f, 57f); canvas.drawRoundRect(rect, 3f, 3f, paint)

            // Cam bölmesi ve gövde (tek parça yol).
            path.reset()
            path.moveTo(21f, 26f)
            path.lineTo(28.5f, 9.5f); path.quadTo(30f, 7f, 33f, 7f)
            path.lineTo(67f, 7f); path.quadTo(70f, 7f, 71.5f, 9.5f)
            path.lineTo(79f, 26f)
            path.lineTo(84f, 26f); path.quadTo(90f, 26f, 90f, 32f)
            path.lineTo(90f, 45f); path.quadTo(90f, 50f, 85f, 50f)
            path.lineTo(15f, 50f); path.quadTo(10f, 50f, 10f, 45f)
            path.lineTo(10f, 32f); path.quadTo(10f, 26f, 16f, 26f)
            path.close()
            paint.color = LauncherTheme.CONTROL
            canvas.drawPath(path, paint)
            // Aynalar.
            rect.set(7f, 25.5f, 15f, 30f); canvas.drawRoundRect(rect, 2f, 2f, paint)
            rect.set(85f, 25.5f, 93f, 30f); canvas.drawRoundRect(rect, 2f, 2f, paint)

            // Kapak açıksa altındaki bagaj boşluğu (koyu) ve taban çizgisi.
            if (opened) {
                paint.color = ColorUtils.setAlphaComponent(outline, (60 + 70 * t).toInt())
                rect.set(GATE_L + 1.5f, GATE_TOP, GATE_R - 1.5f, GATE_BOTTOM - 1f)
                canvas.drawRoundRect(rect, 3f, 3f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f * dp
                paint.color = ColorUtils.setAlphaComponent(accent, 200)
                canvas.drawLine(GATE_L + 4f, GATE_BOTTOM - 4f, GATE_R - 4f, GATE_BOTTOM - 4f, paint)
                paint.style = Paint.Style.FILL
            }

            // Stop lambaları.
            paint.color = ColorUtils.setAlphaComponent(LauncherTheme.ALERT, 210)
            rect.set(12.5f, 30f, 25f, 34f); canvas.drawRoundRect(rect, 2f, 2f, paint)
            rect.set(75f, 30f, 87.5f, 34f); canvas.drawRoundRect(rect, 2f, 2f, paint)

            // Bagaj kapağı: alt kenarı açıldıkça yukarı, tavanın üstüne kalkar; üst kenar menteşede hafifçe yükselir.
            val top = lerp(GATE_TOP, -7f, t)
            val bottom = lerp(GATE_BOTTOM, -1f, t)
            val g1 = minOf(top, bottom)
            val g2 = maxOf(top, bottom)
            rect.set(GATE_L, g1, GATE_R, g2)
            paint.color = if (opened) ColorUtils.blendARGB(LauncherTheme.CONTROL, accent, 0.35f) else LauncherTheme.CONTROL
            canvas.drawRoundRect(rect, 3f, 3f, paint)
            // Kapaktaki arka cam (kapak kalktıkça incelir) ve kapalıyken plaka.
            val glassH = (g2 - g1) * 0.42f
            if (glassH > 1.5f) {
                paint.color = ColorUtils.setAlphaComponent(outline, 45)
                rect.set(GATE_L + 3f, g1 + 2.5f, GATE_R - 3f, g1 + 2.5f + glassH)
                canvas.drawRoundRect(rect, 2f, 2f, paint)
            }
            if (!opened) {
                paint.color = ColorUtils.setAlphaComponent(outline, 70)
                rect.set(43f, 35.5f, 57f, 40f)
                canvas.drawRoundRect(rect, 1.5f, 1.5f, paint)
            }

            // Çizgiler: gövde, tampon ve kapak kenarı.
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = 1.5f * dp
            paint.color = outline
            canvas.drawPath(path, paint)
            paint.strokeWidth = 1.1f * dp
            paint.color = ColorUtils.setAlphaComponent(outline, 90)
            canvas.drawLine(14f, 44.5f, 86f, 44.5f, paint)
            paint.strokeWidth = (if (opened) 1.6f else 1.1f) * dp
            paint.color = if (opened) accent else ColorUtils.setAlphaComponent(outline, 160)
            rect.set(GATE_L, g1, GATE_R, g2)
            canvas.drawRoundRect(rect, 3f, 3f, paint)
            paint.style = Paint.Style.FILL
            canvas.restore()
        }

        private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
    }

    private companion object {
        const val PADDING_DP = 20
        const val COMPACT_PADDING_DP = 14
        const val ICON_DP = 28
        const val LABEL_SP = 22f
        const val VALUE_SP = 28f
        const val TALL_ROWS = 4
        const val ANIM_MS = 450L
        const val FAST_POLL_MS = 500L
        const val DESIGN_W = 100f
        const val DESIGN_H = 68f
        /** Tam açık kapağın tavanın üstüne taştığı pay (tasarım biriminde). */
        const val TOP_PAD = 9f
        const val GATE_L = 28f
        const val GATE_R = 72f
        const val GATE_TOP = 8.5f
        const val GATE_BOTTOM = 42f
    }
}
