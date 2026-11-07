package com.mapcontrol.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.graphics.drawable.Drawable
import android.view.View
import com.mapcontrol.R

/**
 * Markasız, kuşbakışı SUV şeması (burun yukarıda, bagaj aşağıda; sürücü solda). Araç görseli yerine: kapı, bagaj, cam tavan ve lastik durumunu
 * doğrudan yerinde gösterir. Logo, ızgara ya da rozet yoktur.
 *
 * Çizim yaklaşık 100×230 birimlik dikey sabit bir uzayda yapılır; yollar yalnızca bir kez kurulur, boyut değişince yalnızca
 * tuval ölçeklenir. Görünüm yüksekliğe göre en-boy oranını kendisi belirler (bkz. [onMeasure]). Parça durumu değişince kısa (≤300 ms) bir hareketle geçilir. Renkler yalnızca [LauncherTheme]
 * belirteçlerinden okunur (gece/gündüz kendiliğinden).
 */
class CarSchematicView(context: Context) : View(context) {

    /** Çizilecek gövde durumu. Bilinmeyen (sinyal yok) kısımlar kapalı sayılır. */
    data class State(
        val doorLf: Boolean = false,
        val doorRf: Boolean = false,
        val doorLr: Boolean = false,
        val doorRr: Boolean = false,
        val trunk: Boolean = false,
        val sunroof: Boolean = false,
        val hood: Boolean = false,
        val tireWarning: Boolean = false,
        /** true kilitli, false açık, null bilinmiyor (kilit simgesi çizilmez). */
        val locked: Boolean? = null,
    )

    private var state = State()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /**
     * Açık/kapalı geçişleri kısa bir hareketle gösterilir (kapı dışa sallanır, bagaj/kaput kalkar, cam tavan kayar).
     * [prog] parça başına 0 (kapalı) .. 1 (açık) ilerlemedir; sıra: 4 kapı, bagaj, cam tavan, kaput.
     * İlk durum ve görünür olmayan görünüm hareketsiz atlar. Sistem animasyon ölçeği 0 ise süre sıfırdır.
     */
    private val prog = FloatArray(PARTS)
    private var animFrom = FloatArray(PARTS)
    private var animTo = FloatArray(PARTS)
    private var hasState = false
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = MOTION_MS
        interpolator = DecelerateInterpolator(1.6f)
        addUpdateListener { a ->
            val t = a.animatedValue as Float
            for (i in 0 until PARTS) prog[i] = animFrom[i] + (animTo[i] - animFrom[i]) * t
            invalidate()
        }
    }

    fun setState(next: State) {
        if (next == state && hasState) return
        state = next
        val target = floatArrayOf(
            b(next.doorLf), b(next.doorRf), b(next.doorLr), b(next.doorRr), b(next.trunk), b(next.sunroof), b(next.hood),
        )
        val animate = hasState && isAttachedToWindow && width > 0
        hasState = true
        animator.cancel()
        if (!animate || target.contentEquals(prog)) {
            target.copyInto(prog)
            invalidate()
            return
        }
        prog.copyInto(animFrom)
        animTo = target
        animator.start()
    }

    private fun b(v: Boolean) = if (v) 1f else 0f

    override fun onDetachedFromWindow() {
        // Yarım kalan hareket bitmiş say: yeniden eklenince doğru durumla başlasın.
        if (animator.isRunning) { animator.cancel(); animTo.copyInto(prog) }
        super.onDetachedFromWindow()
    }

    /** Tema (gece/gündüz, sürüş modu) değişince renkleri yeniden okutmak için. */
    fun refreshTheme() = invalidate()

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Yollar birim uzayında bir kez kurulur.
    private val body = Path()
    private val windshield = Path()
    private val rearGlass = Path()
    private val roof = Path()
    private val roofRails = Path()
    private val sunroofRect = RectF(-SUN_HALF_W, SUN_T, SUN_HALF_W, SUN_B)
    private val wheelRects = arrayOf(
        wheel(-1, FRONT_WHEEL_Y), wheel(1, FRONT_WHEEL_Y), wheel(-1, REAR_WHEEL_Y), wheel(1, REAR_WHEEL_Y),
    )
    private val mirrors = arrayOf(
        RectF(-MIRROR_OUT, MIRROR_Y - 6f, -BODY_HALF_W + 1f, MIRROR_Y + 6f),
        RectF(BODY_HALF_W - 1f, MIRROR_Y - 6f, MIRROR_OUT, MIRROR_Y + 6f),
    )
    private val doorSeams = Path()
    private val trunkSeam = Path()
    private val hood = Path()
    private val trunkPanel = Path()
    // Kapı şeritleri (gövde kenarı ile tavan arası): 0 sol ön, 1 sağ ön, 2 sol arka, 3 sağ arka.
    private val doorStrips = arrayOf(
        RectF(-BODY_HALF_W, A_PILLAR, -ROOF_HALF_W, B_PILLAR),
        RectF(ROOF_HALF_W, A_PILLAR, BODY_HALF_W, B_PILLAR),
        RectF(-BODY_HALF_W, B_PILLAR, -ROOF_HALF_W, C_PILLAR),
        RectF(ROOF_HALF_W, B_PILLAR, BODY_HALF_W, C_PILLAR),
    )

    private val lockClosed: Drawable? = context.getDrawable(R.drawable.ic_mdi_lock)?.mutate()
    private val lockOpen: Drawable? = context.getDrawable(R.drawable.ic_mdi_lock_open_variant)?.mutate()

    private val swing = Path()
    private var scale = 1f
    private var offX = 0f
    private var offY = 0f

    init {
        buildPaths()
    }

    private fun wheel(side: Int, cy: Float): RectF {
        val cx = side * (BODY_HALF_W - 1f)
        return RectF(cx - WHEEL_T / 2, cy - WHEEL_L / 2, cx + WHEEL_T / 2, cy + WHEEL_L / 2)
    }

    private fun buildPaths() {
        val w = BODY_HALF_W
        // Gövde: yumuşak daralan burun (üstte), hafif omuz kavisi, daha dolgun ama yuvarlak kuyruk (altta).
        body.apply {
            moveTo(-14f, -NOSE_Y)
            lineTo(14f, -NOSE_Y)
            cubicTo(34f, -NOSE_Y, w, -92f, w, -58f)
            cubicTo(w + 1.5f, -30f, w + 1.5f, 40f, w, 72f)
            cubicTo(w, 98f, w - 7f, TAIL_Y, w - 22f, TAIL_Y)
            lineTo(-(w - 22f), TAIL_Y)
            cubicTo(-(w - 7f), TAIL_Y, -w, 98f, -w, 72f)
            cubicTo(-(w + 1.5f), 40f, -(w + 1.5f), -30f, -w, -58f)
            cubicTo(-w, -92f, -34f, -NOSE_Y, -14f, -NOSE_Y)
            close()
        }
        // Ön cam: tabanı (burna yakın) geniş, tavana doğru hafif daralır.
        windshield.apply {
            moveTo(-37f, WINDSHIELD_BASE); lineTo(37f, WINDSHIELD_BASE)
            lineTo(ROOF_HALF_W + 1f, A_PILLAR - 2f); lineTo(-ROOF_HALF_W - 1f, A_PILLAR - 2f); close()
        }
        rearGlass.apply {
            moveTo(-ROOF_HALF_W, C_PILLAR + 2f); lineTo(ROOF_HALF_W, C_PILLAR + 2f)
            lineTo(30f, REAR_GLASS_BASE); lineTo(-30f, REAR_GLASS_BASE); close()
        }
        roof.addRoundRect(RectF(-ROOF_HALF_W, A_PILLAR, ROOF_HALF_W, C_PILLAR), 10f, 10f, Path.Direction.CW)
        // Tavan rayları: SUV izlenimi, çok ince.
        for (s in intArrayOf(-1, 1)) {
            roofRails.moveTo(s * (ROOF_HALF_W - 4f), A_PILLAR + 7f); roofRails.lineTo(s * (ROOF_HALF_W - 4f), C_PILLAR - 7f)
        }
        for (y in floatArrayOf(A_PILLAR, B_PILLAR, C_PILLAR)) {
            doorSeams.moveTo(-BODY_HALF_W + 1f, y); doorSeams.lineTo(-ROOF_HALF_W, y)
            doorSeams.moveTo(BODY_HALF_W - 1f, y); doorSeams.lineTo(ROOF_HALF_W, y)
        }
        // Kaput: iki hafif omuz çizgisi (ızgara/rozet yok).
        hood.apply {
            moveTo(-33f, WINDSHIELD_BASE - 4f); quadTo(-30f, -90f, -16f, -104f)
            moveTo(33f, WINDSHIELD_BASE - 4f); quadTo(30f, -90f, 16f, -104f)
        }
        // Bagaj kapağı dikişi: arka camın hemen arkasında hafif bombeli bir çizgi.
        trunkSeam.apply { moveTo(-33f, REAR_GLASS_BASE + 2f); quadTo(0f, REAR_GLASS_BASE + 5f, 33f, REAR_GLASS_BASE + 2f) }
        trunkPanel.apply {
            moveTo(-60f, TAIL_Y + 8f); lineTo(-60f, REAR_GLASS_BASE + 2f); lineTo(-33f, REAR_GLASS_BASE + 2f)
            quadTo(0f, REAR_GLASS_BASE + 5f, 33f, REAR_GLASS_BASE + 2f); lineTo(60f, REAR_GLASS_BASE + 2f)
            lineTo(60f, TAIL_Y + 8f); close()
        }
    }

    /**
     * Genişlik, verilen yüksekliğin en-boy oranıyla türetilir (görünüm tüm yüksekliği kullanır, sağa yaslanır).
     * Yükseklik belirsizse ya da genişlik daha dar verilmişse genişliğe uyacak şekilde küçülür.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        var h = if (hMode == MeasureSpec.UNSPECIFIED) (resources.displayMetrics.density * DEFAULT_H_DP).toInt() else hSize
        var w = (h * ASPECT).toInt()
        if (wMode != MeasureSpec.UNSPECIFIED && w > wSize) {
            w = wSize
            h = if (hMode == MeasureSpec.EXACTLY) hSize else (w / ASPECT).toInt()
        }
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val spanX = 2f * EXT_X
        val spanY = EXT_B - EXT_T
        scale = minOf(w * FIT / spanX, h * FIT / spanY)
        offX = w / 2f
        offY = h / 2f - (EXT_T + EXT_B) / 2f * scale
    }

    override fun onDraw(canvas: Canvas) {
        val st = state
        val outline = LauncherTheme.TEXT_SECONDARY
        val alert = LauncherTheme.ALERT
        val dp = resources.displayMetrics.density / scale // 1 dp, birim uzayında
        canvas.save()
        canvas.translate(offX, offY)
        canvas.scale(scale, scale)

        // Tekerlekler gövdenin altında; yalnızca dışa taşan kısımları görünür.
        for (r in wheelRects) {
            val warn = st.tireWarning
            fill.color = if (warn) alert else withAlpha(outline, 150)
            canvas.drawRoundRect(r, 4f, 4f, fill)
        }

        // Gövde: dolgu CONTROL, çizgi ikincil metin rengi (hafif).
        fill.color = LauncherTheme.CONTROL
        canvas.drawPath(body, fill)
        // Kapalı bagaj bölgesi sadece çizgiyle; açıkken ALERT dolgu gövde içinde kırpılır.
        if (prog[P_TRUNK] > 0.01f) {
            canvas.save()
            canvas.clipPath(body)
            fill.color = withAlpha(alert, (255 * prog[P_TRUNK]).toInt())
            canvas.drawPath(trunkPanel, fill)
            canvas.restore()
        }
        if (prog[P_HOOD] > 0.01f) {
            val p = prog[P_HOOD]
            canvas.save()
            canvas.clipPath(body)
            fill.color = withAlpha(alert, (150 * p).toInt())
            // Kalkan kaput yukarıdan bakınca menteşeye (ön cam tabanı) doğru kısalır.
            canvas.drawRect(-60f, hoodEdge(p), 60f, WINDSHIELD_BASE - 4f, fill)
            canvas.restore()
        }
        for (i in doorStrips.indices) {
            val p = prog[i]
            if (p < 0.01f) continue
            canvas.save()
            canvas.clipPath(body)
            fill.color = withAlpha(alert, (255 * p).toInt())
            canvas.drawRect(doorStrips[i], fill)
            canvas.restore()
        }

        // Camlar: ikincil rengin düşük opaklığı; roof CARD'a yakın bir ton açar.
        fill.color = withAlpha(outline, 45)
        canvas.drawPath(windshield, fill)
        canvas.drawPath(rearGlass, fill)
        fill.color = withAlpha(LauncherTheme.CARD, 150)
        canvas.drawPath(roof, fill)

        stroke.strokeWidth = 1.5f * dp
        stroke.color = outline
        canvas.drawPath(body, stroke)
        stroke.strokeWidth = 1.1f * dp
        stroke.color = withAlpha(outline, 200)
        canvas.drawPath(windshield, stroke)
        canvas.drawPath(rearGlass, stroke)
        stroke.color = withAlpha(outline, 130)
        canvas.drawPath(roof, stroke)
        canvas.drawPath(doorSeams, stroke)
        canvas.drawPath(trunkSeam, stroke)
        stroke.color = withAlpha(outline, 90)
        canvas.drawPath(roofRails, stroke)
        canvas.drawPath(hood, stroke)

        // Cam tavan: açılırken cam panel arkaya kayar, açıklık ALERT ile belirir.
        val sp = prog[P_SUNROOF]
        if (sp > 0.01f) {
            fill.color = withAlpha(alert, (255 * sp).toInt())
            canvas.drawRoundRect(sunroofRect, 5f, 5f, fill)
        }
        canvas.save()
        canvas.clipPath(roof)
        val slide = (SUN_B - SUN_T) * SUN_SLIDE * sp
        sunPanel.set(sunroofRect.left, sunroofRect.top + slide, sunroofRect.right, sunroofRect.bottom + slide)
        fill.color = withAlpha(outline, 55)
        canvas.drawRoundRect(sunPanel, 5f, 5f, fill)
        stroke.color = withAlpha(outline, 150)
        canvas.drawRoundRect(sunPanel, 5f, 5f, stroke)
        canvas.restore()
        if (sp > 0.01f) {
            stroke.color = withAlpha(alert, (255 * sp).toInt())
            canvas.drawRoundRect(sunroofRect, 5f, 5f, stroke)
        }

        // Aynalar.
        for (m in mirrors) {
            fill.color = LauncherTheme.CONTROL
            canvas.drawRoundRect(m, 3f, 3f, fill)
            stroke.color = withAlpha(outline, 200)
            canvas.drawRoundRect(m, 3f, 3f, stroke)
        }

        // Açık kapılar dışa doğru sallanmış kalın çizgi (menteşe ön kenarda); açıya göre kapalıdan açığa döner.
        stroke.color = alert
        stroke.strokeWidth = 6f * dp
        for (i in doorStrips.indices) {
            if (prog[i] > 0.01f) drawDoorSwing(canvas, doorStrips[i], i % 2 == 0, prog[i])
        }
        // Açık bagaj: kapak arkaya doğru kalkar (iki yan çizgi + arka kenar).
        val tp = prog[P_TRUNK]
        if (tp > 0.01f) {
            stroke.strokeWidth = 6f * dp
            val lift = 16f * tp
            swing.reset()
            swing.moveTo(-28f, TAIL_Y); swing.lineTo(-28f + 6f * tp, TAIL_Y + lift)
            swing.lineTo(28f - 6f * tp, TAIL_Y + lift); swing.lineTo(28f, TAIL_Y)
            canvas.drawPath(swing, stroke)
        }
        // Açık kaput: ön kenar menteşeye doğru çekilen kalın çizgi.
        val hp = prog[P_HOOD]
        if (hp > 0.01f) {
            stroke.strokeWidth = 6f * dp
            val y = hoodEdge(hp)
            swing.reset()
            swing.moveTo(-26f + 4f * hp, y); swing.lineTo(26f - 4f * hp, y)
            canvas.drawPath(swing, stroke)
        }
        // Lastik uyarısında tekerlek çerçevesi.
        if (st.tireWarning) {
            stroke.strokeWidth = 1.4f * dp
            stroke.color = alert
            for (r in wheelRects) canvas.drawRoundRect(r, 4f, 4f, stroke)
        }
        canvas.restore()

        // Kilit simgesi: dik durur, tavanın ön yarısında; piksel boyutu ölçekle gelir.
        val locked = st.locked
        if (locked != null) {
            val d = if (locked) lockClosed else lockOpen
            if (d != null) {
                val cy = offY + LOCK_Y * scale
                val half = LOCK_SIZE / 2f * scale
                d.setTint(outline)
                d.alpha = 220
                d.setBounds((offX - half).toInt(), (cy - half).toInt(), (offX + half).toInt(), (cy + half).toInt())
                d.draw(canvas)
            }
        }
    }

    private val sunPanel = RectF()

    /** Kaputun ön kenarı: kapalıyken burunda, açıkken menteşeye [HOOD_LIFT] kadar yaklaşmış. */
    private fun hoodEdge(p: Float) = (-NOSE_Y + 12f) + ((WINDSHIELD_BASE - 4f - HOOD_LIFT) - (-NOSE_Y + 12f)) * p

    /** Kapı ön kenarındaki menteşeden dışa doğru açılır; [left] true ise sol (sürücü) taraf. */
    private fun drawDoorSwing(canvas: Canvas, strip: RectF, left: Boolean, p: Float) {
        val side = if (left) -1f else 1f
        val hingeX = side * BODY_HALF_W
        val hingeY = strip.top
        val len = strip.height()
        val a = Math.toRadians((SWING_DEG * p).toDouble())
        val ex = hingeX + side * len * Math.sin(a).toFloat()
        val ey = hingeY + len * Math.cos(a).toFloat()
        swing.reset()
        swing.moveTo(hingeX, hingeY)
        swing.lineTo(ex, ey)
        canvas.drawPath(swing, stroke)
    }

    private fun withAlpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private companion object {
        // Dikey birim uzayı: x sağa, y aşağı; burun üstte (y<0), kuyruk altta.
        const val BODY_HALF_W = 50f
        const val ROOF_HALF_W = 33f
        const val NOSE_Y = 114f
        const val TAIL_Y = 112f
        const val WINDSHIELD_BASE = -62f
        const val A_PILLAR = -36f
        const val B_PILLAR = 12f
        const val C_PILLAR = 68f
        const val REAR_GLASS_BASE = 90f
        const val FRONT_WHEEL_Y = -72f
        const val REAR_WHEEL_Y = 70f
        const val WHEEL_L = 34f
        const val WHEEL_T = 17f
        const val MIRROR_Y = -42f
        const val MIRROR_OUT = 59f
        const val SUN_HALF_W = 19f
        const val SUN_T = 24f
        const val SUN_B = 60f
        const val LOCK_Y = -9f
        const val LOCK_SIZE = 20f
        const val SWING_DEG = 26f
        const val PARTS = 7
        const val P_TRUNK = 4
        const val P_SUNROOF = 5
        const val P_HOOD = 6
        /** Açılma/kapanma hareketi süresi (ms); kısa ve sakin. */
        const val MOTION_MS = 280L
        /** Cam tavan paneli en çok açıklığın bu kadarı kadar arkaya kayar. */
        const val SUN_SLIDE = 0.64f
        const val HOOD_LIFT = 26f
        // Birim uzayındaki en uç değerler (sallanan kapı ve kalkık bagaj dahil).
        const val EXT_X = 77f
        const val EXT_T = -NOSE_Y - 2f
        const val EXT_B = TAIL_Y + 19f
        const val FIT = 0.98f
        /** Genişlik / yükseklik. */
        const val ASPECT = (2f * EXT_X) / (EXT_B - EXT_T)
        const val DEFAULT_H_DP = 160
    }
}
