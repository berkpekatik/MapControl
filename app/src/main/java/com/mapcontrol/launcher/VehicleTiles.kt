package com.mapcontrol.launcher

import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import com.mapcontrol.R
import com.mapcontrol.vehicle.VehicleQuickControls
import java.util.concurrent.Executor

/**
 * Merkezi kilit düğmesi (1×2, uygulama kutucuğuyla aynı ayak izi): yolcunun da kolayca uzanacağı net bir düğme.
 * Büyük, ortalanmış kilit ikonu; altında küçük durum etiketi ("Kilitli" / "Kilit açık"). Dokununca kilidi açar/kapatır.
 */
internal class LockTile(private val context: Context) : SizedWidget, Themeable, StripSource {

    override var onStripChanged: (() -> Unit)? = null
    private var stripNow: StripState? = null
    override fun stripState(): StripState? = stripNow
    /** Sahne kilit hapı ve şerit: kutucuktaki dokunuşla aynı yol ([toggle]). */
    override fun stripTap(part: Int) = toggle()

    /** Son bilinen kilit durumu (null: bilinmiyor); Sahne kilit hapı için. */
    val isLocked: Boolean? get() = locked

    private val appContext = context.applicationContext

    /** Tema bağlı atamalar (kurucuda bir kez, [applyTheme]'de yeniden); bkz. [ThemeBindings]. */
    private val theme = ThemeBindings()

    /**
     * Her seferinde yeniden alınır: MapControl'ün ana ekranı kapanırken bu paylaşılan nesneyi release() ediyor;
     * saklanan eski örnek kapalı executor'la çökme yaratırdı.
     */
    private fun controls(): VehicleQuickControls = VehicleQuickControls.getInstance(appContext)
    private val io: Executor = LauncherIo.serial()
    private val main = Handler(Looper.getMainLooper())

    /** null: bağlı değil ya da durum bilinmiyor. */
    private var locked: Boolean? = null

    private val listener = VehicleQuickControls.Listener { isLocked, connected ->
        if (LauncherDemo.enabled) return@Listener
        locked = if (connected) isLocked else null
        main.post { bind() }
    }

    private val lockIcon: ImageView = icon(context, R.drawable.ic_mdi_lock, ICON_DP, LauncherTheme.TEXT).apply {
        layoutParams = LinearLayout.LayoutParams(context.dp(ICON_DP), context.dp(ICON_DP))
    }
    private val stateText: TextView = text(context, STATE_SP).apply {
        gravity = android.view.Gravity.CENTER
        theme.bind { setTextColor(LauncherTheme.TEXT_SECONDARY) }
    }

    private val root: LinearLayout = card(context, LinearLayout.VERTICAL, paddingDp = PADDING_DP).apply {
        gravity = android.view.Gravity.CENTER
        theme.bind { background = cardSurface(context) }
        addView(lockIcon)
        addView(gap(context, 8, vertical = true))
        addView(stateText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    override val view: View get() = root

    /** Tek boyut var (1×2); yerleşim değişmez. */
    override fun applySpan(span: Span) = Unit

    /** Tema değişti: yüzey ve durum yazısı varsayılana döner, kilit ikonu/yazısı [bind] ile yeniden atanır. */
    override fun applyTheme() {
        theme.reapply()
        bind()
    }

    init {
        root.isClickable = true
        root.isFocusable = true
        root.setOnClickListener { toggle() }
        root.pressFeedback()
    }

    override fun onVisible() {
        // Durum yalnızca dinleyiciden gelir: ana thread'de bağlantı kurmaya çalışmayız ve
        // yükleme bitmeden yanlış "kilit açık" göstermeyiz.
        locked = if (LauncherDemo.enabled) LauncherDemo.LOCKED else null
        bind()
        controls().addListener(listener)
        OpeningsBus.add(openingsListener)
    }

    /** Demo: kapı açılıp kapanınca kilit de ona uyar (gerçek araçta durum dinleyiciden gelir). */
    private val openingsListener: (Openings?) -> Unit = {
        if (LauncherDemo.enabled && locked != LauncherDemo.LOCKED) {
            locked = LauncherDemo.LOCKED
            bind()
        }
    }

    override fun onHidden() {
        OpeningsBus.remove(openingsListener)
        controls().removeListener(listener)
        main.removeCallbacksAndMessages(null)
    }

    /** Paylaşılan [LauncherIo] havuzu kapatılmaz; ayrılacak bir kaynak yok. */
    override fun release() = Unit

    private fun bind() {
        when (locked) {
            true -> show(R.drawable.ic_mdi_lock, LauncherTheme.TEXT, context.getString(R.string.launcher_locked))
            false -> show(R.drawable.ic_mdi_lock_open_variant, LauncherTheme.TEXT, context.getString(R.string.launcher_unlocked))
            null -> show(R.drawable.ic_mdi_lock, LauncherTheme.TEXT_SECONDARY, context.getString(R.string.launcher_unavailable))
        }
        // Test sürümünde veri yokken de dokunuş açıklama göstermeli.
        root.isEnabled = locked != null || LauncherEdition.READ_ONLY
    }

    private fun show(@DrawableRes res: Int, tint: Int, label: String) {
        lockIcon.setImageResource(res)
        lockIcon.imageTintList = ColorStateList.valueOf(tint)
        stateText.setTextSmooth(label)
        root.contentDescription = context.getString(R.string.launcher_widget_lock) + ", " + label
        stripNow = StripState(context.getString(R.string.launcher_widget_lock), label, res,
            if (tint == LauncherTheme.TEXT_SECONDARY) StripTone.MUTED else StripTone.NORMAL)
        onStripChanged?.invoke()
    }

    /** Kilit komutu senkron binder çağrısı; ana thread'i bloklamasın diye arka planda gönderilir. */
    private fun toggle() {
        if (LauncherEdition.READ_ONLY) {
            Toast.makeText(appContext, R.string.launcher_read_only_lock, Toast.LENGTH_LONG).show()
            return
        }
        val current = locked ?: return
        io.runSafely {
            try {
                LauncherEdition.setCentralLock(appContext, !current)
            } catch (e: RuntimeException) {
                // Komut gitmediyse dinleyici gerçek durumu göstermeye devam eder.
            }
        }
    }

    private companion object {
        const val ICON_DP = 64
        const val STATE_SP = 20f
        const val PADDING_DP = 6
    }
}
