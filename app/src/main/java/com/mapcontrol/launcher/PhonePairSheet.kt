package com.mapcontrol.launcher

import android.app.Activity
import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.mapcontrol.R

/** Telefon eşleme sayfasının gösterdiği durum (saf, JVM testli). */
internal sealed class PhonePairUi {
    object ServerOff : PhonePairUi()
    object Starting : PhonePairUi()
    object NoNetwork : PhonePairUi()
    object Ready : PhonePairUi()
}

internal object PhonePairLogic {
    /** [pairingUri] yalnızca sunucu açık ve araç bir ağdayken doludur. */
    fun state(running: Boolean, pairingUri: String?, starting: Boolean): PhonePairUi = when {
        pairingUri != null -> PhonePairUi.Ready
        running -> PhonePairUi.NoNetwork
        starting -> PhonePairUi.Starting
        else -> PhonePairUi.ServerOff
    }
}

private const val SHEET_WIDTH_DP = 520
private const val QR_DP = 240

/**
 * Telefon uygulamasıyla eşleşme QR'ı (MapControl'ın Aktar sekmesindekiyle aynı içerik). QR eşleşme anahtarını taşıdığı için
 * yalnızca bu sayfa açıkken üretilir, günlüğe ya da erişilebilirlik metnine yazılmaz. Sunucu kendiliğinden açılmaz:
 * kapalıysa kullanıcı "Başlat" ile açar.
 */
internal fun showPhonePairSheet(activity: Activity, features: MapControlFeatures, onClosed: () -> Unit): Dialog {
    val main = Handler(Looper.getMainLooper())
    val io = LauncherIo.serial()
    var closed = false
    var starting = false

    val qr = QrView(activity).apply {
        contentDescription = activity.getString(R.string.launcher_phone_pair_title)
        visibility = View.GONE
    }
    val status = text(activity, LauncherTheme.BODY_SP, LauncherTheme.TEXT_SECONDARY).apply { gravity = Gravity.CENTER }
    val startButton = text(activity, LauncherTheme.BODY_SP, LauncherTheme.ON_ACCENT, medium = true).apply {
        gravity = Gravity.CENTER
        minimumHeight = activity.dp(LauncherTheme.TOUCH_MIN_DP)
        setPadding(activity.dp(28), 0, activity.dp(28), 0)
        background = rounded(activity, DriveAccent.buttonFill(LauncherTheme.ACCENT), 999f)
        isClickable = true
        isFocusable = true
        pressFeedback()
        visibility = View.GONE
    }

    fun render(ui: PhonePairUi, matrix: QrMatrix?) {
        val ready = ui is PhonePairUi.Ready && matrix != null
        qr.setMatrix(if (ready) matrix else null)
        qr.visibility = if (ready) View.VISIBLE else View.GONE
        status.setText(when (ui) {
            PhonePairUi.Ready -> R.string.phone_pair_scan
            PhonePairUi.NoNetwork -> R.string.launcher_fn_web_local
            PhonePairUi.ServerOff, PhonePairUi.Starting -> R.string.launcher_phone_pair_off
        })
        val canStart = ui is PhonePairUi.ServerOff || ui is PhonePairUi.Starting
        startButton.visibility = if (canStart) View.VISIBLE else View.GONE
        startButton.isEnabled = ui is PhonePairUi.ServerOff
        startButton.setText(if (ui is PhonePairUi.Starting) R.string.launcher_fn_web_starting else R.string.launcher_fn_web_start)
    }

    fun refresh() {
        io.runSafely {
            val running = features.readWebPanel().running
            val uri = features.readPairingUri()
            val matrix = uri?.let { features.qr(it) }
            main.post { if (!closed) render(PhonePairLogic.state(running, uri, starting), matrix) }
        }
    }

    startButton.setOnClickListener {
        if (starting) return@setOnClickListener
        starting = true
        refresh()
        features.setWebPanel(true) { message ->
            starting = false
            refresh()
            if (message != null) Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    val unwatch = features.watchWebPanel { refresh() }
    val dialog = showLauncherSheet(activity, R.string.launcher_phone_pair_title, SHEET_WIDTH_DP) { dialog ->
        addView(qr, LinearLayout.LayoutParams(activity.dp(QR_DP), activity.dp(QR_DP)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(14)
        })
        addView(startButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = activity.dp(18)
        })
        dialog.setOnDismissListener {
            closed = true
            unwatch()
            onClosed()
        }
    }
    refresh()
    return dialog
}
