package com.mapcontrol.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.mapcontrol.R

/**
 * "Ekranı karart" kutucuğu (1×2 ve 2×2): dokununca [ScreenOffActivity] ile tam siyah, en düşük parlaklıkta
 * bir ekran açar. Araca ya da sistem ayarlarına hiçbir şey yazılmaz.
 */
class ScreenOffTile(private val activity: Activity) : SizedWidget, Themeable, StripSource {

    // --- Şerit (Sahne): başlık "Ekran", değer "Karart"; dokunuş kutucuktakiyle aynı ---
    override var onStripChanged: (() -> Unit)? = null
    override fun stripState(): StripState = StripState(
        activity.getString(R.string.launcher_screen_off_title), activity.getString(R.string.launcher_screen_off_action),
        R.drawable.ic_mdi_weather_night,
    )
    override fun stripTap(part: Int) { root.performClick() }

    /** Tema bağlı atamalar (kurucuda bir kez, [applyTheme]'de yeniden); bkz. [ThemeBindings]. */
    private val theme = ThemeBindings()

    private val icon = icon(activity, R.drawable.ic_mdi_weather_night, ICON_DP).also { v -> theme.bind { v.setTint(LauncherTheme.TEXT) } }
    private val label = text(activity, LauncherTheme.LABEL_SP, maxLines = 2).apply {
        setText(R.string.launcher_screen_off_label)
        gravity = Gravity.CENTER
        theme.bind { setTextColor(LauncherTheme.TEXT_SECONDARY) }
    }

    private val root: LinearLayout = card(activity, LinearLayout.VERTICAL, paddingDp = 8).apply {
        gravity = Gravity.CENTER
        theme.bind { background = cardSurface(activity) }
        isClickable = true
        isFocusable = true
        contentDescription = activity.getString(R.string.launcher_screen_off_label)
        addView(icon)
        addView(gap(activity, 8, vertical = true))
        addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setOnClickListener { activity.startActivity(Intent(activity, ScreenOffActivity::class.java)) }
        pressFeedback()
    }

    override val view: View = root

    /** Tema değişti: yüzey, ikon ve etiket yeniden atanır. */
    override fun applyTheme() = theme.reapply()

    /** İnce (1 sütun) kutucukta yalnızca ikon görünür; 2×2'de ikon ve etiket. */
    override fun applySpan(span: Span) {
        label.visibility = if (span.w < 2) View.GONE else View.VISIBLE
        val px = activity.dp(if (span.w < 2) ICON_NARROW_DP else ICON_DP)
        icon.layoutParams = LinearLayout.LayoutParams(px, px)
    }

    override fun onVisible() = Unit

    override fun onHidden() = Unit

    private companion object {
        const val ICON_DP = 48
        const val ICON_NARROW_DP = 36
    }
}

/**
 * Tam ekran saf siyah ekran: sistem çubukları gizli, pencere parlaklığı çok düşük, ekran açık kalır.
 * Herhangi bir dokunuş ya da tuş ekranı kapatır; parlaklık pencereye ait olduğu için kapanınca kendiliğinden
 * eski haline döner. SYSTEM_ALERT_WINDOW ya da Settings.System yazımı gerekmez. Ekran arka plana giderse kapanır.
 */
class ScreenOffActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var shownAt = 0L
    private lateinit var hint: TextView

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_FULLSCREEN)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
            attributes = attributes.apply { screenBrightness = DIM_BRIGHTNESS }
        }
        hint = TextView(this).apply {
            setText(R.string.launcher_screen_off_hint)
            setTextColor(HINT_COLOR)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN) wake()
                true
            }
        })
        shownAt = SystemClock.uptimeMillis()
        handler.postDelayed({ hint.animate().alpha(0f).setDuration(HINT_FADE_MS).start() }, HINT_VISIBLE_MS)
    }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    /** Açılışı başlatan dokunuşun hemen kapatmaması için kısa bir koruma süresi vardır. */
    private fun wake() {
        if (SystemClock.uptimeMillis() - shownAt < GUARD_MS) return
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        wake()
        return true
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        super.onDestroy()
    }

    private companion object {
        const val DIM_BRIGHTNESS = 0.01f
        const val HINT_COLOR = 0x33FFFFFF
        const val HINT_VISIBLE_MS = 2000L
        const val HINT_FADE_MS = 600L
        const val GUARD_MS = 300L
    }
}
