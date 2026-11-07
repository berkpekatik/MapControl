package com.mapcontrol.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.mapcontrol.R

/**
 * Başlıktaki Bluetooth simgesine dokununca açılan, simgenin altına bağlı Bluetooth paneli ([WifiPanel] ile aynı dil):
 * aç/kapa anahtarı (izin yoksa yalnızca durumu gösterir, altında not), bağlı cihaz (telefon/ses profilleri, "Bağlantıyı kes"),
 * eşleşmiş cihazlar ("Bağlan") ve "Bluetooth ayarları" (eski yol: OEM telefon uygulaması). Yeni cihaz arama/eşleştirme yok.
 *
 * Bağlan/kes gizli profil API'siyle denenir ([BluetoothBackend.connect]); olmazsa kısa bir not ve "Telefon uygulamasında aç"
 * yedeği görünür. Panel yalnızca açıkken [BluetoothStatus] yayınlarıyla ve profil vekilleri gelince yenilenir; zamanlayıcı yok
 * (yalnızca başlatılan bağlantı için bir zaman aşımı).
 */
internal class BluetoothPanel(
    private val activity: Activity,
    private val backend: BluetoothBackend,
    private val onSettings: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var popup: PopupWindow? = null
    private var scrim: PopupWindow? = null
    private var root: LinearLayout? = null
    private var toggling = false
    private var status: String? = null
    private var statusAlert = false
    /** Durum notunun yanında "Telefon uygulamasında aç" yedeği gösterilsin mi. */
    private var offerFallback = false
    /** Bağlanması/kesilmesi beklenen cihaz (adres) ve beklenen sonuç (true: bağlı). */
    private var pending: Pair<String, Boolean>? = null
    private var availDp = 0f
    private var scrollY = 0
    private var dismissedAt = 0L

    private val status0 = BluetoothStatus(activity) { render() }
    private val pendingTimeout = Runnable {
        if (pending != null) { pending = null; fail(R.string.launcher_bt_connect_failed) }
    }

    val isShowing: Boolean get() = popup?.isShowing == true

    fun show(anchor: View) {
        if (isShowing) { dismiss(); return }
        // Simgeye ikinci dokunuş: önce dışarı dokunuşla kapandı, hemen yeniden açılmasın (aç/kapa gibi davranır).
        if (android.os.SystemClock.uptimeMillis() - dismissedAt < REOPEN_GUARD_MS) return
        val ctx = activity
        status = null
        offerFallback = false
        pending = null
        availDp = availableDp(anchor)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(ctx, if (LauncherTheme.night) 0xFF1E232B.toInt() else 0xFFFFFFFF.toInt(),
                Sahne.PANEL_RADIUS_DP, if (LauncherTheme.night) 0xFF2A3038.toInt() else 0x29181E28)
            val p = ctx.dp(PAD_DP)
            setPadding(p, p, p, p)
        }
        root = box
        val decor = ctx.window.decorView
        scrim = PopupWindow(View(ctx).apply {
            setBackgroundColor(if (LauncherTheme.night) 0x66000000 else 0x26000000)
            isClickable = true
            setOnClickListener { dismiss() }
        }, decor.width, decor.height, false).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isTouchable = true
            isClippingEnabled = false
            showAtLocation(anchor, Gravity.NO_GRAVITY, 0, 0)
        }
        val pw = PopupWindow(box, ctx.dp(WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isTouchable = true
            isOutsideTouchable = true
            elevation = 0f
            setOnDismissListener { onDismissed() }
        }
        popup = pw
        backend.open { render() }
        status0.start()
        render()
        box.alpha = 0f
        pw.showAsDropDown(anchor, 0, ctx.dp(8), Gravity.START)
        box.animate().alpha(1f).setDuration(FADE_MS).start()
    }

    fun dismiss() {
        popup?.dismiss()
    }

    private fun onDismissed() {
        scrim?.dismiss()
        scrim = null
        dismissedAt = android.os.SystemClock.uptimeMillis()
        main.removeCallbacks(pendingTimeout)
        status0.stop()
        backend.close()
        popup = null
        root = null
        pending = null
    }

    private fun availableDp(anchor: View): Float {
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val screenH = activity.resources.displayMetrics.heightPixels
        return (screenH - (loc[1] + anchor.height)) / activity.resources.displayMetrics.density - 24f
    }

    /** Başlık, bağlı cihaz(lar), bölüm başlığı ve ayarlar satırından artan yüksekliğe sığan eşleşmiş cihaz satırı (2..5). */
    private fun rowsThatFit(connected: Int): Int {
        val fixedDp = PAD_DP * 2 + ROW_DP * (2 + connected) + SECTION_DP + DIVIDER_DP + (if (status != null) ROW_DP else 0)
        return ((availDp - fixedDp) / ROW_DP).toInt().coerceIn(2, BtListShaper.MAX_VISIBLE)
    }

    // --- Çizim ---

    private fun render() {
        val box = root ?: return
        val ctx = activity
        (box.findViewWithTag<ScrollView>(TAG_SCROLL))?.let { scrollY = it.scrollY }
        box.removeAllViews()
        val mode = BtListShaper.switchMode(backend.present, backend.canToggle())
        val real = if (backend.canRead()) backend.devices() else emptyList()
        val demo = real.isEmpty() && LauncherDemo.enabled
        val enabled = backend.isEnabled() || demo
        val (connected, paired) = BtListShaper.shape(if (demo) LauncherDemo.bluetoothDevices() else real)
        // Beklenen bağlantı oturduysa not kalkar.
        pending?.let { (addr, wantConnected) ->
            val now = connected.any { it.address.equals(addr, true) }
            if (now == wantConnected) { pending = null; status = null; main.removeCallbacks(pendingTimeout) }
        }

        box.addView(titleRow(enabled, mode), sheetFullWidth())
        if (mode == BtSwitch.READ_ONLY && !demo) box.addView(note(ctx.getString(R.string.launcher_bt_switch_locked)), sheetFullWidth())
        status?.let { box.addView(statusRow(it), sheetFullWidth()) }
        when {
            mode == BtSwitch.ABSENT && !demo -> box.addView(messageRow(ctx.getString(R.string.launcher_bt_unavailable)), sheetFullWidth())
            !enabled -> box.addView(messageRow(ctx.getString(R.string.launcher_bt_off_state)), sheetFullWidth())
            !backend.canRead() && !demo -> box.addView(messageRow(ctx.getString(R.string.launcher_bt_no_read)), sheetFullWidth())
            else -> {
                connected.forEach { box.addView(connectedRow(it), sheetFullWidth()) }
                renderPaired(box, paired, rowsThatFit(connected.size))
            }
        }
        box.addView(divider())
        box.addView(settingsRow(), sheetFullWidth())
    }

    private fun renderPaired(box: LinearLayout, paired: List<BtDevice>, maxRows: Int) {
        val ctx = activity
        box.addView(text(ctx, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY, medium = true).apply {
            setText(R.string.launcher_bt_paired)
            setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(4))
        }, sheetFullWidth())
        if (paired.isEmpty()) {
            box.addView(messageRow(ctx.getString(R.string.launcher_bt_none)), sheetFullWidth())
            return
        }
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        paired.forEach { list.addView(pairedRow(it), sheetFullWidth()) }
        if (paired.size <= maxRows) { box.addView(list, sheetFullWidth()); return }
        box.addView(object : ScrollView(ctx) {
            override fun onMeasure(w: Int, h: Int) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(ctx.dp(ROW_DP * maxRows), MeasureSpec.AT_MOST))
            }
        }.apply {
            tag = TAG_SCROLL
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(list)
            post { scrollTo(0, scrollY) }
        }, sheetFullWidth())
    }

    private fun baseRow(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = activity.dp(ROW_DP)
        setPadding(activity.dp(12), 0, activity.dp(12), 0)
    }

    private fun clickable(v: View, onClick: () -> Unit) {
        v.isClickable = true
        v.isFocusable = true
        v.setOnClickListener { onClick() }
        v.pressFeedback()
    }

    private fun titleRow(enabled: Boolean, mode: BtSwitch): View = baseRow().apply {
        addView(text(activity, LauncherTheme.SECTION_SP, bold = true).apply { setText(R.string.launcher_bt_title) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (mode != BtSwitch.ABSENT) addView(switchView(enabled, mode == BtSwitch.ACTIVE))
    }

    /** Açma/kapama anahtarı ([WifiPanel] ile aynı ölçü): izin yoksa soluk ve dokunulmaz, yalnızca durumu gösterir. */
    private fun switchView(on: Boolean, active: Boolean): View {
        val ctx = activity
        val track = FrameLayout(ctx).apply {
            background = rounded(ctx, if (on) DriveAccent.buttonFill(LauncherTheme.ACCENT) else LauncherTheme.TRACK, 999f)
        }
        val thumb = View(ctx).apply { background = oval(if (on) LauncherTheme.ON_ACCENT else LauncherTheme.TEXT_SECONDARY) }
        track.addView(thumb, FrameLayout.LayoutParams(ctx.dp(32), ctx.dp(32), (if (on) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL).apply {
            marginStart = ctx.dp(4); marginEnd = ctx.dp(4)
        })
        return FrameLayout(ctx).apply {
            minimumWidth = ctx.dp(88)
            contentDescription = ctx.getString(R.string.launcher_bt_title)
            isSelected = on
            alpha = if (!active) 0.4f else if (toggling) 0.5f else 1f
            addView(track, FrameLayout.LayoutParams(ctx.dp(72), ctx.dp(40), Gravity.CENTER))
            if (active) clickable(this) { toggle(!on) } else isEnabled = false
            layoutParams = LinearLayout.LayoutParams(ctx.dp(88), ctx.dp(ROW_DP))
        }
    }

    private fun toggle(on: Boolean) {
        if (toggling) return
        toggling = true
        status = null
        render()
        backend.setEnabled(on) {
            toggling = false
            render()
        }
    }

    private fun deviceIcon(kind: BtKind): Int = when (kind) {
        BtKind.PHONE -> R.drawable.ic_mdi_cellphone
        BtKind.AUDIO -> R.drawable.ic_mdi_music_note
        BtKind.OTHER -> R.drawable.ic_mdi_bluetooth
    }

    /** Bağlı cihaz: onay işareti, ad ve altında bağlı profiller (telefon/ses simgesi + kısa ad), sağda "Bağlantıyı kes". */
    private fun connectedRow(d: BtDevice): View = baseRow().apply {
        val ctx = activity
        setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8))
        addView(icon(ctx, R.drawable.ic_mdi_check, 32, LauncherTheme.ACCENT), LinearLayout.LayoutParams(ctx.dp(40), ctx.dp(32)))
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(ctx, LauncherTheme.BODY_SP, medium = true).apply {
                text = BtListShaper.label(d)
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                if (d.phone) profileTag(this, R.drawable.ic_sahne_phone, R.string.launcher_bt_profile_phone)
                if (d.audio) profileTag(this, R.drawable.ic_sahne_speaker, R.string.launcher_bt_profile_audio)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(4) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = ctx.dp(12) })
        addView(pill(R.string.launcher_bt_disconnect, busy = pending?.first == d.address) { request(d, connect = false) })
    }

    private fun profileTag(row: LinearLayout, res: Int, label: Int) {
        val ctx = activity
        row.addView(icon(ctx, res, 22, LauncherTheme.TEXT_SECONDARY), LinearLayout.LayoutParams(ctx.dp(22), ctx.dp(22)).apply { marginEnd = ctx.dp(6) })
        row.addView(text(ctx, LauncherTheme.LABEL_SP - 2f, LauncherTheme.TEXT_SECONDARY).apply { setText(label) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = ctx.dp(16) })
    }

    private fun pairedRow(d: BtDevice): View = baseRow().apply {
        val ctx = activity
        addView(icon(ctx, deviceIcon(d.kind), 28, LauncherTheme.TEXT_SECONDARY), LinearLayout.LayoutParams(ctx.dp(40), ctx.dp(28)))
        addView(text(ctx, LauncherTheme.BODY_SP).apply {
            text = BtListShaper.label(d)
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = ctx.dp(12) })
        addView(pill(R.string.launcher_bt_connect, busy = pending?.first == d.address) { request(d, connect = true) })
        contentDescription = BtListShaper.label(d)
    }

    private fun pill(res: Int, busy: Boolean = false, onClick: () -> Unit): TextView =
        text(activity, LauncherTheme.LABEL_SP, LauncherTheme.TEXT, medium = true).apply {
            setText(res)
            gravity = Gravity.CENTER
            minimumHeight = activity.dp(56)
            background = rounded(activity, LauncherTheme.PANEL_PILL, 999f)
            setPadding(activity.dp(20), 0, activity.dp(20), 0)
            alpha = if (busy) 0.5f else 1f
            clickable(this) { if (!busy) onClick() }
        }

    private fun note(msg: String): View = text(activity, LauncherTheme.LABEL_SP - 2f, LauncherTheme.TEXT_SECONDARY, maxLines = 2).apply {
        text = msg
        setPadding(activity.dp(12), 0, activity.dp(12), activity.dp(8))
    }

    /** Durum notu; bağlanma/kesme olmadıysa yanında "Telefon uygulamasında aç" yedeği. */
    private fun statusRow(msg: String): View = baseRow().apply {
        val ctx = activity
        addView(text(ctx, LauncherTheme.LABEL_SP, if (statusAlert) LauncherTheme.ALERT else LauncherTheme.TEXT_SECONDARY, medium = true, maxLines = 2).apply {
            text = msg
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = ctx.dp(12) })
        if (offerFallback) addView(pill(R.string.launcher_bt_open_phone_app) { dismiss(); openOemBluetooth(ctx) })
    }

    private fun messageRow(msg: String): View = baseRow().apply {
        addView(text(activity, LauncherTheme.BODY_SP, LauncherTheme.TEXT_SECONDARY, maxLines = 2).apply { text = msg })
    }

    private fun settingsRow(): View = baseRow().apply {
        minimumHeight = activity.dp(LauncherTheme.TOUCH_MIN_DP)
        addView(text(activity, LauncherTheme.BODY_SP, LauncherTheme.TEXT, medium = true).apply { setText(R.string.launcher_bt_settings) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(icon(activity, R.drawable.ic_sahne_chev_right, 28, LauncherTheme.TEXT_SECONDARY))
        clickable(this) { dismiss(); onSettings() }
    }

    private fun divider(): View = View(activity).apply {
        setBackgroundColor(LauncherTheme.TRACK)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(1)).apply {
            topMargin = activity.dp(4); bottomMargin = activity.dp(4)
        }
    }

    // --- Bağlanma ---

    private fun request(d: BtDevice, connect: Boolean) {
        if (pending != null) return
        pending = d.address to connect
        statusAlert = false
        offerFallback = false
        status = activity.getString(if (connect) R.string.launcher_bt_connecting else R.string.launcher_bt_disconnecting, BtListShaper.label(d))
        render()
        Thread({
            val result = try {
                if (connect) backend.connect(d.address) else backend.disconnect(d.address)
            } catch (e: RuntimeException) {
                BtOutcome.FAILED
            }
            main.post {
                if (root == null) return@post
                if (result == BtOutcome.STARTED) {
                    // Sonuç yayınla gelir; gelmezse zaman aşımında yedek önerilir.
                    main.removeCallbacks(pendingTimeout)
                    main.postDelayed(pendingTimeout, PENDING_TIMEOUT_MS)
                } else {
                    pending = null
                    fail(if (connect) R.string.launcher_bt_connect_failed else R.string.launcher_bt_disconnect_failed)
                }
            }
        }, "bt-connect").start()
    }

    private fun fail(res: Int) {
        status = activity.getString(res)
        statusAlert = true
        offerFallback = true
        render()
    }

    companion object {
        private const val WIDTH_DP = 600
        private const val PAD_DP = 16
        private const val ROW_DP = LauncherTheme.TOUCH_MIN_DP
        private const val SECTION_DP = 40
        private const val DIVIDER_DP = 9
        private const val FADE_MS = 120L
        private const val TAG_SCROLL = "bt_scroll"
        private const val REOPEN_GUARD_MS = 350L
        private const val PENDING_TIMEOUT_MS = 10_000L
    }
}
