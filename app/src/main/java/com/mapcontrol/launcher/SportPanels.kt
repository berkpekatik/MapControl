package com.mapcontrol.launcher

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.mapcontrol.R
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Spor küçük kadran seçiminin kaydı (tek seçim, Park ve Sürüş'te aynı). Demo'da `launcher_demo_sport_dials` verildiyse
 * kayıt yerine o kullanılır; demo açıkken seçicideki değişiklik yalnız bellekte tutulur (kayda yazılmaz). Değişince dinleyiciler (başlıktaki dijital saat)
 * ana iş parçacığında çağrılır.
 */
internal object SportDialPrefs {
    private const val FILE = "launcher_sport"
    private const val KEY = "dials"
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    @Volatile private var sensorG: Boolean? = null

    /** İvme sensörü var mı (G-metre sunulur mu); demo "sensör yok" diyebilir. */
    fun hasG(context: Context): Boolean {
        if (LauncherDemo.enabled && LauncherDemo.sportNoG) return false
        sensorG?.let { return it }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val v = sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null || sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        sensorG = v
        return v
    }

    fun load(context: Context): List<SportDial> {
        val demo = LauncherDemo.sportDials.takeIf { LauncherDemo.enabled }
        val raw = demo ?: prefs(context).getString(KEY, null)
        return SportDials.decode(raw, hasG(context))
    }

    fun save(context: Context, dials: List<SportDial>) {
        val s = SportDials.encode(dials)
        // Demo seçimi yalnız bellekte (geçici önizleme): kullanıcının kayıtlı seçimine sızmaz.
        if (LauncherDemo.enabled) LauncherDemo.sportDials = s
        else prefs(context).edit().putString(KEY, s).apply()
        listeners.forEach { it() }
    }

    /** Analog saat seçili mi (spor modunda başlıktaki dijital saat gizlenir). */
    fun clockChosen(context: Context): Boolean = SportDial.CLOCK in load(context)

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/**
 * Hız kadranında bu sürüşün en yüksek (gösterge) hızı: ince kırmızı-turuncu tepe işareti (bölüntü halkasının üstünde kısa çizgi).
 * Bir kez çizilir, donanım katmanında; yalnız döndürülür (tuval açısı, +x yönü 0°).
 */
internal class PeakView(context: Context) : View(context) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = (SportInk.RED_HOT and 0x00FFFFFF) or 0xE6000000.toInt() }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = (SportInk.RED and 0x00FFFFFF) or 0x33000000 }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = INVISIBLE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val rf = w / 2f
        line.strokeWidth = maxOf(1.5f, rf * 0.011f)
        halo.strokeWidth = line.strokeWidth * 3.2f
    }

    override fun onDraw(canvas: Canvas) {
        val rf = width / 2f
        val cy = height / 2f
        canvas.drawLine(rf + rf * 0.80f, cy, rf + rf * 0.965f, cy, halo)
        canvas.drawLine(rf + rf * 0.80f, cy, rf + rf * 0.965f, cy, line)
    }
}

/**
 * Analog saat ibresi (12 yönünü gösterir, döndürülerek kullanılır): saat ve dakika ince metal gövdeli, turuncu uçlu;
 * dakika ibresi göbeği de çizer. Saniye ibresi minik alt kadranda, ince kırmızı-turuncu. Bir kez çizilir, donanım katmanında.
 */
internal class SportClockHand(context: Context, private val kind: Kind) : View(context) {
    enum class Kind { HOUR, MINUTE, SECOND }

    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SportInk.RED_HOT }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = 0xCC050506.toInt() }
    private val hub = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPath = Path()
    private val tipPath = Path()

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val r = w / 2f
        val cx = r
        val cy = h / 2f
        val (len, base, end, tail) = when (kind) {
            Kind.HOUR -> floatArrayOf(r * 0.50f, r * 0.030f, r * 0.016f, r * 0.10f)
            Kind.MINUTE -> floatArrayOf(r * 0.76f, r * 0.022f, r * 0.011f, r * 0.12f)
            Kind.SECOND -> floatArrayOf(r * 0.86f, maxOf(0.9f, r * 0.04f), maxOf(0.6f, r * 0.025f), r * 0.22f)
        }.let { Quad(it[0], it[1], it[2], it[3]) }
        val split = cy - len * 0.78f
        bodyPath.reset()
        bodyPath.moveTo(cx - base, cy + tail)
        bodyPath.lineTo(cx - end, split)
        bodyPath.lineTo(cx + end, split)
        bodyPath.lineTo(cx + base, cy + tail)
        bodyPath.close()
        tipPath.reset()
        tipPath.moveTo(cx - end, split)
        tipPath.lineTo(cx - end * 0.55f, cy - len)
        tipPath.lineTo(cx + end * 0.55f, cy - len)
        tipPath.lineTo(cx + end, split)
        tipPath.close()
        if (kind == Kind.SECOND) {
            body.shader = null
            body.color = SportInk.RED
            hub.shader = null
            hub.color = SportInk.RED_HOT
        } else {
            body.shader = LinearGradient(cx - base, 0f, cx + base, 0f, intArrayOf(SportInk.METAL_LO, SportInk.METAL_HI, SportInk.METAL_MID), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            val hr = r * 0.07f
            hub.shader = RadialGradient(cx, cy - hr * 0.3f, hr * 1.2f, intArrayOf(SportInk.METAL_HI, SportInk.METAL_MID, SportInk.METAL_LO), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
    }

    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)

    override fun onDraw(canvas: Canvas) {
        val r = width / 2f
        val cy = height / 2f
        canvas.drawPath(bodyPath, body)
        if (kind != Kind.SECOND) canvas.drawPath(bodyPath, edge)
        canvas.drawPath(tipPath, tip)
        when (kind) {
            Kind.MINUTE -> {
                canvas.drawCircle(r, cy, r * 0.07f, hub)
                canvas.drawCircle(r, cy, r * 0.026f, tip)
            }
            Kind.SECOND -> canvas.drawCircle(r, cy, maxOf(1.5f, r * 0.09f), hub)
            Kind.HOUR -> Unit
        }
    }
}

/** Düzenleme modunda küçük kadranın çevresindeki ince işaret halkası (uzun basınca seçici açılır). */
internal class SportEditRing(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpF(2f)
        color = (SportInk.RED_HOT and 0x00FFFFFF) or 0xB3000000.toInt()
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(context.dpF(10f), context.dpF(6f)), 0f)
    }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        val c = width / 2f
        canvas.drawCircle(c, height / 2f, c - padPx(context) / 2f, paint)
    }

    companion object {
        fun padPx(context: Context): Float = context.dpF(8f)
    }
}

/**
 * Küçük kadran seçicisi (spor dili): ekranı hafifçe karartan örtü üstünde, dokunulan kadranın yakınında küçük metal
 * çerçeveli panel; beş sütunlu seçenekler (G-metre yalnız sensör varsa). Başka kadranda olan değer seçilirse iki kadran
 * yer değiştirir. Örtüye dokununca kapanır.
 */
internal class SportDialPicker private constructor(
    private val activity: Activity,
    private val onDismiss: () -> Unit,
) {
    private var scrim: FrameLayout? = null

    fun dismiss() {
        val s = scrim ?: return
        scrim = null
        s.animate().cancel()
        (s.parent as? ViewGroup)?.removeView(s)
        onDismiss()
    }

    companion object {
        private const val COLUMNS = 5
        private const val CHIP_W_DP = 172
        private const val CHIP_H_DP = 80
        private const val GAP_DP = 10

        fun show(
            activity: Activity,
            anchor: View,
            dial: SportGeometry.Dial,
            current: List<SportDial>,
            offered: List<SportDial>,
            onPick: (SportDial) -> Unit,
            onDismiss: () -> Unit,
        ): SportDialPicker? {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout ?: return null
            val picker = SportDialPicker(activity, onDismiss)
            val ctx: Context = activity
            val here = current.getOrNull(dial.slot)
            val panel = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = SportPanelDrawable(ctx, 24f, solid = true)
                setPadding(ctx.dp(22), ctx.dp(18), ctx.dp(22), ctx.dp(22))
                isClickable = true // panel içi dokunuş örtüyü kapatmaz
            }
            panel.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(sahneText(ctx, 18f, SportInk.INK_3, 500, 0.06f, dayBump = false).apply { setText(R.string.launcher_sport_pick_title) })
                addView(gap(ctx, 12))
                addView(sahneText(ctx, 20f, SportInk.INK, 500, dayBump = false).apply { text = here?.let { SportGeometry.pickerName(it) } ?: "" })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(14) })
            offered.chunked(COLUMNS).forEachIndexed { r, row ->
                panel.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    row.forEachIndexed { c, kind ->
                        addView(chip(ctx, kind, here == kind, kind in current && here != kind) {
                            picker.dismiss()
                            if (kind != here) onPick(kind)
                        }, LinearLayout.LayoutParams(ctx.dp(CHIP_W_DP), ctx.dp(CHIP_H_DP)).apply { if (c > 0) marginStart = ctx.dp(GAP_DP) })
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (r > 0) topMargin = ctx.dp(GAP_DP) })
            }
            val scrim = FrameLayout(ctx).apply {
                setBackgroundColor(0x8C000000.toInt())
                isClickable = true
                setOnClickListener { picker.dismiss() }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            scrim.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            content.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            picker.scrim = scrim
            // Konum: kadranın yatay ortasına yakın (ekrana kırpılır), dikeyde kadranın hizasında.
            val loc = IntArray(2)
            anchor.getLocationInWindow(loc)
            val contentLoc = IntArray(2)
            content.getLocationInWindow(contentLoc)
            val cw = content.width.takeIf { it > 0 } ?: anchor.rootView.width
            val ch = content.height.takeIf { it > 0 } ?: anchor.rootView.height
            panel.measure(View.MeasureSpec.makeMeasureSpec(cw, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(ch, View.MeasureSpec.AT_MOST))
            val m = ctx.dp(16)
            val dx = loc[0] - contentLoc[0] + dial.cx
            val dy = loc[1] - contentLoc[1] + dial.cy
            val left = (dx - panel.measuredWidth / 2f).toInt().coerceIn(m, maxOf(m, cw - m - panel.measuredWidth))
            val top = (dy - panel.measuredHeight / 2f).toInt().coerceIn(m, maxOf(m, ch - m - panel.measuredHeight))
            (panel.layoutParams as FrameLayout.LayoutParams).apply { leftMargin = left; topMargin = top; gravity = Gravity.TOP or Gravity.START }
            panel.alpha = 0f
            panel.scaleX = 0.96f
            panel.scaleY = 0.96f
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160L).setInterpolator(DecelerateInterpolator()).start()
            return picker
        }

        private fun chip(ctx: Context, kind: SportDial, selected: Boolean, elsewhere: Boolean, onClick: () -> Unit): View = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = SportPillDrawable(ctx, 18f, 1.5f)
            if (selected) foreground = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = ctx.dpF(18f)
                setStroke(ctx.dp(2), SportInk.RED_HOT)
            } else foreground = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF), null,
                android.graphics.drawable.GradientDrawable().apply { cornerRadius = ctx.dpF(18f); setColor(0xFFFFFFFF.toInt()) })
            setPadding(ctx.dp(10), 0, ctx.dp(10), 0)
            addView(sahneText(ctx, 20f, if (selected) SportInk.RED_HOT else SportInk.INK, 500, dayBump = false).apply {
                text = SportGeometry.pickerName(kind)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            if (elsewhere) addView(sahneText(ctx, 14f, SportInk.INK_3, 400, dayBump = false).apply {
                setText(R.string.launcher_sport_pick_swap)
                gravity = Gravity.CENTER
            })
            contentDescription = SportGeometry.pickerName(kind)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }
}

/**
 * Sürüş özeti kartı (spor modundan çıkınca ya da spor açıkken Park'a geçince): süre, en yüksek hız, en iyi 0–100,
 * ortalama tüketim (bu sürüş; yalnız okunan veri). Ekranın ortasında sakin bir metal çerçeveli kart; ~6 sn sonra kendi
 * söner, dokununca hemen kapanır. Hiç veri yoksa açılmaz.
 */
internal object SportSummaryCard {
    const val SHOW_MS = 6_000L
    private const val IN_MS = 260L
    private const val OUT_MS = 320L
    private var shown: WeakReference<View>? = null

    /** Bu sürüşün özeti: süre ve ortalama tüketim araç yolculuk verisinden, en yüksek hız ve 0–100 spor oturumundan. */
    fun current(context: Context): SportSummary {
        if (LauncherDemo.enabled) {
            val t = LauncherDemo.trip()
            return SportSummary(t.drivingRunMin, SportDemo.MAX_KMH, SportDemo.ZERO100_MS, t.driveAvgL100)
        }
        val t = TripSource.last
        val s = SportSessionStore.session
        val min = TripTotals.drivingMinutes(t.drivingRunMin, t.drivingResetMin, t.drivingAltMin, t.engineOnMin)
        return SportSummary(min, s.maxSpeedKmh, s.best0to100Ms, t.driveAvgL100)
    }

    fun show(activity: Activity, summary: SportSummary) {
        if (!summary.hasData || activity.isFinishing || activity.isDestroyed) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout ?: return
        shown?.get()?.let { (it.parent as? ViewGroup)?.removeView(it) }
        val ctx: Context = activity
        val dash = "—"
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = SportPanelDrawable(ctx, 26f, solid = true)
            setPadding(ctx.dp(40), ctx.dp(20), ctx.dp(40), ctx.dp(24))
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        card.addView(sahneText(ctx, 17f, SportInk.INK_3, 500, 0.08f, dayBump = false).apply {
            setText(R.string.launcher_sport_summary_title)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(10) })
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val cols = listOf(
            Triple(SportFormat.duration(summary.minutes) ?: dash, SportFormat.durationUnit(summary.minutes), ctx.getString(R.string.launcher_sport_summary_time)),
            Triple(summary.maxKmh?.toString() ?: dash, "km/sa", ctx.getString(R.string.launcher_sport_summary_max)),
            Triple(SportFormat.seconds(summary.best0to100Ms) ?: dash, "sn", "0–100"),
            Triple(SportFormat.l100(summary.avgL100) ?: dash, "L/100", ctx.getString(R.string.launcher_sport_summary_avg)),
        )
        cols.forEachIndexed { i, (value, unit, label) ->
            if (i > 0) row.addView(View(ctx).apply { setBackgroundColor(0x24FFFFFF) }, LinearLayout.LayoutParams(ctx.dp(1), ctx.dp(56)).apply {
                marginStart = ctx.dp(28); marginEnd = ctx.dp(28)
            })
            row.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.BOTTOM
                    isBaselineAligned = true
                    addView(sahneText(ctx, 44f, SportInk.INK, 300, -0.02f, dayBump = false).apply { text = value; includeFontPadding = false })
                    addView(sahneText(ctx, 16f, SportInk.INK_3, 500, dayBump = false).apply { text = unit }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = ctx.dp(6) })
                })
                addView(sahneText(ctx, 16f, SportInk.INK_2, 400, dayBump = false).apply { text = label; gravity = Gravity.CENTER })
            })
        }
        // WRAP_CONTENT: dikey LinearLayout'ta MATCH_PARENT çocuk kartın genişliğine katılmaz (kart başlık kadar kalırdı).
        card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.contentDescription = ctx.getString(R.string.launcher_sport_summary_title) + ": " + cols.joinToString(", ") { "${it.third} ${it.first} ${it.second}" }
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        content.addView(card, lp)
        shown = WeakReference(card)
        card.alpha = 0f
        card.translationY = ctx.dpF(14f)
        card.animate().alpha(1f).translationY(0f).setDuration(IN_MS).setInterpolator(DecelerateInterpolator()).start()
        val hide = Runnable { fadeOut(card) }
        card.setOnClickListener { card.removeCallbacks(hide); fadeOut(card, fast = true) }
        card.postDelayed(hide, SHOW_MS)
    }

    private fun fadeOut(card: View, fast: Boolean = false) {
        card.animate().cancel()
        card.animate().alpha(0f).translationY(card.context.dpF(8f)).setDuration(if (fast) OUT_MS / 2 else OUT_MS).withEndAction {
            (card.parent as? ViewGroup)?.removeView(card)
            if (shown?.get() === card) shown = null
        }.start()
    }
}
