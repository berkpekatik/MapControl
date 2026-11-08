package com.mapcontrol.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Başlıktaki Wi-Fi simgesi, kodla çizilir (tek çizim, dört dilim): nokta + üç yay. [ui] seviyesine göre dolu dilimler
 * [LauncherTheme.TEXT], kalanlar [LauncherTheme.TRACK]. Bağlı değilken tüm dilimler soluk ve üstü çizik; hotspot'ta
 * ortada nokta, iki yanda yaylar (yayın simgesi) vurgu renginde.
 */
internal class WifiGlyph(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var ui = WifiUi(WifiUi.Kind.OFF)

    /** Sağ üstte küçük nokta: FrostNav web paneli sunucusu çalışıyor (başlık kapsülü). */
    var webDot: Boolean = false
        set(v) { if (field != v) { field = v; invalidate() } }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** Spor modunda (karbon zemin) açık metal ton; null iken temanın tonu. */
    var sportInk: Int? = null
        set(v) { if (field != v) { field = v; invalidate() } }

    fun bind(state: WifiUi) {
        ui = state
        invalidate()
    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        if (!webDot) return
        // Simgenin sağ üst köşesi: yaylardan ayrık, kapsül zemininde okunur.
        val r = width * 0.13f
        dotPaint.color = Sahne.OK
        canvas.drawCircle(width - r, r, r, dotPaint)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = w * 0.085f
        paint.strokeWidth = stroke
        val on = sportInk ?: Sahne.HEADER_ICON
        val connectedNow = ui.kind == WifiUi.Kind.CONNECTED
        // Bağlıyken dolmayan dilimler iz renginde; bağlı değilken tüm simge soluk ama zeminden ayırt edilir.
        val off = sportInk?.let { (it and 0x00FFFFFF) or 0x59000000 }
            ?: if (connectedNow) LauncherTheme.TRACK else (LauncherTheme.TEXT_SECONDARY and 0x00FFFFFF) or 0x80000000.toInt()
        if (ui.kind == WifiUi.Kind.HOTSPOT) {
            drawHotspot(canvas, w, h, stroke)
            return
        }
        val connected = ui.kind == WifiUi.Kind.CONNECTED
        // Dilim i (0 nokta, 1..3 yaylar) için en az gereken seviye: 1, 2, 3, 4.
        fun lit(i: Int) = connected && ui.level >= i + 1
        val cx = w / 2f
        val cy = h * 0.80f
        for (i in 3 downTo 1) {
            val r = w * 0.17f * i + w * 0.04f
            paint.style = Paint.Style.STROKE
            paint.color = if (lit(i)) on else off
            canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 225f, 90f, false, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = if (lit(0)) on else off
        canvas.drawCircle(cx, cy, stroke * 0.95f, paint)
        if (!connected) {
            paint.style = Paint.Style.STROKE
            paint.color = LauncherTheme.TEXT_SECONDARY
            canvas.drawLine(w * 0.16f, h * 0.14f, w * 0.84f, h * 0.88f, paint)
        }
    }

    private fun drawHotspot(canvas: Canvas, w: Float, h: Float, stroke: Float) {
        val cx = w / 2f
        val cy = h / 2f
        paint.color = LauncherTheme.ACCENT
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, stroke * 1.4f, paint)
        paint.style = Paint.Style.STROKE
        for (i in 1..2) {
            val r = w * 0.17f * i + w * 0.08f
            val rect = RectF(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(rect, -40f, 80f, false, paint)
            canvas.drawArc(rect, 140f, 80f, false, paint)
        }
    }
}
