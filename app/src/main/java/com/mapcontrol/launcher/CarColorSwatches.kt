package com.mapcontrol.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * "Araç rengi" seçicisi: altı yuvarlak renk örneği ve altında adı; seçili olanın çevresinde vurgu halkası. Launcher'ın
 * Görünüm paneli ve FrostNav'ın "Ana ekran" sekmesi aynı satırı kullanır (renkleri çağıran verir). Seçim
 * [CarColorPrefs]'e yazılır; açık Sahne aracı tercih dinleyicisiyle hemen yeni renge geçer.
 */
object CarColorSwatches {

    /**
     * @param ring seçili örneğin halka rengi (vurgu), [label] ad rengi, [outline] örneklerin ince kenarı (açık renkler
     * açık zeminde kaybolmasın), [labelSp] ad yazı boyu, [swatchDp] örnek çapı.
     */
    @JvmStatic
    fun build(
        context: Context,
        ring: Int,
        label: Int,
        outline: Int,
        labelSp: Float,
        swatchDp: Int,
        onPick: Runnable?,
    ): LinearLayout {
        val current = CarColorPrefs.get(context)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        CarColor.values().forEach { color ->
            val selected = color == current
            val dot = View(context).apply {
                background = swatch(context, color.swatch, if (selected) ring else 0, outline)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val name = TextView(context).apply {
                setText(color.label)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSp)
                setTextColor(label)
                gravity = Gravity.CENTER
                maxLines = 1
                if (selected) setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            row.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val p = context.dp(6)
                setPadding(p, p, p, p)
                addView(dot, LinearLayout.LayoutParams(context.dp(swatchDp), context.dp(swatchDp)))
                addView(name, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = context.dp(8)
                })
                isClickable = true
                isFocusable = true
                isSelected = selected
                contentDescription = context.getString(com.mapcontrol.R.string.launcher_car_color) + ": " + context.getString(color.label)
                setOnClickListener {
                    if (color != CarColorPrefs.get(context)) CarColorPrefs.set(context, color)
                    onPick?.run()
                }
                pressFeedback()
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return row
    }

    /** Dolu daire; seçiliyse 3dp boşluklu vurgu halkası. */
    private fun swatch(context: Context, fill: Int, ring: Int, outline: Int): LayerDrawable {
        val ringD = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0)
            if (ring != 0) setStroke(context.dp(3), ring)
        }
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke(context.dp(1), outline)
        }
        return LayerDrawable(arrayOf(ringD, dot)).apply {
            val i = context.dp(7)
            setLayerInset(1, i, i, i, i)
        }
    }
}
