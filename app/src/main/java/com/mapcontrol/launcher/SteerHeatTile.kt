package com.mapcontrol.launcher

import android.app.Activity
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import com.desaysv.ivi.extra.project.carinfo.CarSettingID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.R
import com.mapcontrol.comfort.ComfortPlan

/**
 * Direksiyon ısıtmanın saf kuralları (JVM'de sınanır). CS 160 okunur: 1 açık, diğerleri kapalı (araçta ölçüldü). Yazma bir
 * ANAHTARDIR: açarken de kapatırken de 160 ← 1 ([ComfortPlan.STEER_WHEEL_TOGGLE]); bu yüzden yalnız okunan durum istenenden
 * farklıysa gönderilir ve gönderimden sonra [RESEND_GAP_MS] boyunca yeni dokunuş yok sayılır (çift dokunuş iki kez çevirmesin;
 * ClimateAutomationEngine.TOGGLE_RESEND_GAP_MS ile aynı). HVAC 9 yazılmaz (OEM klima panelini açar).
 */
internal object SteerHeatLogic {

    enum class Action { SEND, SKIP, WAIT, REFUSE }

    const val RESEND_GAP_MS = 3_000L

    fun isOn(read: Int?): Boolean = read == 1

    /**
     * [current] taze okuma (null okunamadı), [wantOn] istenen durum, [lastSentMs] son gönderim (yoksa null). Sıra: gönderimden
     * beri boşluk dolmadıysa WAIT; okunamadıysa REFUSE; zaten istenen durumdaysa SKIP; değilse SEND.
     */
    fun decide(current: Int?, wantOn: Boolean, nowMs: Long, lastSentMs: Long?): Action {
        if (lastSentMs != null && nowMs - lastSentMs < RESEND_GAP_MS) return Action.WAIT
        return when (ComfortPlan.toggleAction(current, if (wantOn) 1 else 0)) {
            ComfortPlan.ToggleAction.REFUSE -> Action.REFUSE
            ComfortPlan.ToggleAction.SKIP -> Action.SKIP
            ComfortPlan.ToggleAction.SEND -> Action.SEND
        }
    }
}

/**
 * Direksiyon ısıtma (Tiggo 8; şerit öğesi). Görsel bir direksiyondur; açıkken simidi sıcak turuncu parlar ("parça yanar"),
 * rozet ya da halka yok. Dokunuş durumu çevirir ([SteerHeatLogic]); okuma 3 sn'de bir. Kutucuk yalnız CarConfig 120 izin
 * verdiğinde düzene gelir ([CarFeatures.steerHeat], [SahneForm.available]); yine de izin yoksa hiçbir şey gönderilmez.
 */
internal class SteerHeatTile(private val activity: Activity) : SizedWidget, Themeable, StripSource {

    override var onStripChanged: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val io = LauncherIo.serial()

    /** Son okuma (null okunamadı) ve iyimser hedef. */
    private var read: Int? = null
    private var pending: Boolean? = null
    @Volatile private var lastSentMs: Long? = null

    private val wheel = SteerWheelDrawable()
    /** Şerit öğesinin görseli (tek örnek; durum değişince kendini yeniden çizer). */
    private val stripWheel = SteerWheelDrawable()
    private val art = ImageView(activity).apply {
        setImageDrawable(wheel)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val label = text(activity, LauncherTheme.LABEL_SP, maxLines = 2).apply {
        setText(R.string.launcher_widget_steer_heat)
        gravity = Gravity.CENTER
    }
    private val state = text(activity, LauncherTheme.LABEL_SP).apply { gravity = Gravity.CENTER }

    private val root: LinearLayout = card(activity, LinearLayout.VERTICAL, paddingDp = 8).apply {
        gravity = Gravity.CENTER
        background = cardSurface(activity)
        isClickable = true
        isFocusable = true
        addView(art, LinearLayout.LayoutParams(activity.dp(ICON_DP), activity.dp(ICON_DP)))
        addView(gap(activity, 6, vertical = true))
        addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(state, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setOnClickListener { tap() }
        pressFeedback()
    }

    override val view: View = root

    private val poll = object : Runnable {
        override fun run() {
            refresh()
            main.postDelayed(this, POLL_MS)
        }
    }
    private val dropPending = Runnable { pending = null; render() }

    init {
        WidgetWriter.attach(activity)
        CarFeatures.init(activity)
        render()
    }

    override fun applySpan(span: Span) = Unit

    override fun applyTheme() {
        root.background = cardSurface(activity)
        render()
    }

    override fun onVisible() {
        main.removeCallbacks(poll)
        main.post(poll)
    }

    override fun onHidden() {
        main.removeCallbacks(poll)
        main.removeCallbacks(dropPending)
        pending = null
    }

    override fun stripState(): StripState {
        val on = shownOn()
        val known = on != null
        return StripState(
            title = activity.getString(R.string.launcher_widget_steer_heat),
            value = activity.getString(when (on) {
                null -> R.string.launcher_unavailable
                true -> R.string.launcher_toggle_on
                false -> R.string.launcher_toggle_off
            }),
            icon = R.drawable.ic_mdi_steering,
            tone = when (on) { null -> StripTone.MUTED; true -> StripTone.HEAT; false -> StripTone.NORMAL },
            iconDrawable = stripWheel.also { it.state = wheelState(on) },
            active = if (known) on else null,
        )
    }

    override fun stripTap(part: Int) = tap()

    private fun shownOn(): Boolean? = pending ?: if (LauncherDemo.enabled) false else read?.let { SteerHeatLogic.isOn(it) }

    private fun wheelState(on: Boolean?) = when (on) {
        null -> SteerWheelDrawable.State.UNKNOWN
        true -> SteerWheelDrawable.State.ON
        false -> SteerWheelDrawable.State.OFF
    }

    private fun refresh() {
        if (LauncherDemo.enabled) { render(); return }
        io.runSafely {
            val v = readNow()
            main.post {
                if (v == read) return@post
                read = v
                if (pending != null && v != null && SteerHeatLogic.isOn(v) == pending) { pending = null; main.removeCallbacks(dropPending) }
                render()
            }
        }
    }

    private fun readNow(): Int? {
        val proxy = CarInfoProxy.getInstance()
        if (!proxy.isServiceConnnected) return null
        return try {
            proxy.getItemValues(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_STEER_WHEEL_HEAT)?.firstOrNull()
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun tap() {
        if (LauncherDemo.enabled || CarFeatures.steerHeat != true) return
        val want = !(shownOn() ?: return)
        io.runSafely {
            val current = readNow()
            val now = SystemClock.elapsedRealtime()
            val action = SteerHeatLogic.decide(current, want, now, lastSentMs)
            // Yazma kapısı reddederse (direksiyon ısıtma araçta henüz doğrulanmadı) neden gösterilir, iyimser durum kurulmaz.
            val sent = action == SteerHeatLogic.Action.SEND &&
                LauncherEdition.sendItemValue(CarInfoProxy.getInstance(), VDEventCarInfo.MODULE_CAR_SETTING,
                    CarSettingID.ID_CAR_STEER_WHEEL_HEAT, ComfortPlan.STEER_WHEEL_TOGGLE)
            if (sent) lastSentMs = now
            main.post {
                read = current ?: read
                if (sent) {
                    pending = want
                    main.removeCallbacks(dropPending)
                    main.postDelayed(dropPending, CONFIRM_MS)
                }
                render()
            }
        }
    }

    private fun render() {
        val on = shownOn()
        wheel.state = wheelState(on)
        art.invalidate()
        label.setTextColor(LauncherTheme.TEXT_SECONDARY)
        state.text = activity.getString(when (on) {
            null -> R.string.launcher_unavailable
            true -> R.string.launcher_toggle_on
            false -> R.string.launcher_toggle_off
        })
        state.setTextColor(if (on == true) Sahne.AMBER else LauncherTheme.TEXT)
        root.contentDescription = activity.getString(R.string.launcher_widget_steer_heat) + ", " + state.text
        onStripChanged?.invoke()
    }

    private companion object {
        const val POLL_MS = 3_000L
        /** Araç yeni durumu okumazsa iyimser hedef bırakılır (gönderim boşluğundan biraz uzun). */
        const val CONFIRM_MS = 3_500L
        const val ICON_DP = 48
    }
}

/**
 * Çizgi direksiyon: simit, göbek ve üç kol. [State.ON]'da yalnız simit sıcak turuncu parlar (birkaç saydam iz ile yumuşak
 * hale); kapalıyken başlık ikonlarının çelik rengi, bilinmiyorken soluk. Renkler çizimde okunur (gece/gündüz).
 */
internal class SteerWheelDrawable : Drawable() {
    enum class State { ON, OFF, UNKNOWN }

    var state: State = State.OFF
        set(v) { if (field != v) { field = v; invalidateSelf() } }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val size = minOf(b.width(), b.height()).toFloat()
        if (size <= 0f) return
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val stroke = size * 0.085f
        val r = size / 2f - stroke * 1.6f
        val line = when (state) {
            State.UNKNOWN -> Sahne.HEADER_ICON_OFF
            else -> Sahne.HEADER_ICON
        }
        val rim = if (state == State.ON) Sahne.AMBER else line
        if (state == State.ON) {
            // Hale: simidin üstünde genişleyen, giderek saydamlaşan izler.
            paint.color = ColorUtils.setAlphaComponent(Sahne.AMBER_HI, 0x40)
            paint.strokeWidth = stroke * 2.6f
            canvas.drawCircle(cx, cy, r, paint)
            paint.color = ColorUtils.setAlphaComponent(Sahne.AMBER_HI, 0x70)
            paint.strokeWidth = stroke * 1.7f
            canvas.drawCircle(cx, cy, r, paint)
        }
        paint.color = rim
        paint.strokeWidth = stroke
        canvas.drawCircle(cx, cy, r, paint)
        // Göbek ve kollar (sol, sağ, alt).
        paint.color = line
        val hub = r * 0.26f
        canvas.drawCircle(cx, cy, hub, paint)
        canvas.drawLine(cx - r, cy, cx - hub, cy, paint)
        canvas.drawLine(cx + hub, cy, cx + r, cy, paint)
        canvas.drawLine(cx, cy + hub, cx, cy + r, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = -1
    override fun getIntrinsicHeight(): Int = -1
}
