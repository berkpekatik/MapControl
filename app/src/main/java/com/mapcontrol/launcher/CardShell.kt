package com.mapcontrol.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.LinearLayout

/**
 * Kart yüzeyini kendisi çizen kutucuk kabuğu: [LauncherTheme.CARD] dolgulu, kart yarıçapında yuvarlak, gündüz ince
 * kenarlıklı. [surfaceVisible] kapalıyken hiçbir şey çizmez (boş uygulama kutusu arka planla bütünleşir).
 */
open class CardShell(context: Context, paddingDp: Int) : LinearLayout(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpF(LauncherTheme.BORDER_DP.toFloat())
    }
    private val rect = RectF()
    private val radius = context.dpF(LauncherTheme.CARD_RADIUS_DP)

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        val p = context.dp(paddingDp)
        setPadding(p, p, p, p)
    }

    /** Kapalıyken kart hiçbir şey çizmez (yüzey, kenarlık yok). */
    var surfaceVisible = true
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        if (!surfaceVisible) return
        val w = width.toFloat()
        val h = height.toFloat()
        fill.color = LauncherTheme.CARD
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, fill)
        val border = LauncherTheme.CARD_BORDER
        if (border != 0) {
            stroke.color = border
            val inset = stroke.strokeWidth / 2
            rect.set(inset, inset, w - inset, h - inset)
            canvas.drawRoundRect(rect, radius, radius, stroke)
        }
    }
}
