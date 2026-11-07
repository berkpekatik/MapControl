package com.mapcontrol.launcher

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.mapcontrol.R

/**
 * Başlıktaki Wi-Fi simgesine dokununca açılan, simgenin altına bağlı Wi-Fi paneli (macOS menü çubuğu gibi):
 * aç/kapa, bağlı ağ ("Bağlantıyı kes"), yakındaki ağlar, yeni şifreli ağ için şifre penceresi, "Web paneli" satırı (FrostNav web
 * sunucusu; açıkken QR ve adres) ve "Wi-Fi ayarları…". Wi-Fi işleri [WifiBackend] üzerinden gider ve yalnızca açıkken tarama/ağ
 * yayınlarıyla yenilenir; web paneli durumu panel açıkken değişince ([FrostNavFeatures.watchWebPanel]) ve yedek olarak
 * [WEB_POLL_MS]'de bir arka planda okunur. "Web paneli" anahtarı sunucuyu yerinde açıp kapatır.
 *
 * Sürüşte ([driving] true) en çok [WifiListShaper.MAX_VISIBLE] ağ, kaydırma yok ve şifre girilemez; park halinde liste kayar.
 */
internal class WifiPanel(
    private val activity: Activity,
    private val backend: WifiBackend,
    private val driving: () -> Boolean,
    private val onSettings: () -> Unit,
    /** FrostNav web paneli arka ucu (yoksa satır gösterilmez) ve durum değişince başlıktaki noktayı güncelleyen geri çağrı. */
    private val features: FrostNavFeatures = FrostNavFeatures.NONE,
    private val onWebState: (Boolean) -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    private var popup: PopupWindow? = null
    private var root: LinearLayout? = null
    private var scanning = false
    private var toggling = false
    private var status: String? = null
    private var statusAlert = false
    private var maxRows = WifiListShaper.MAX_VISIBLE
    /** Panelin açıldığı yerden ekran altına kalan yükseklik (dp); ağ satırı sayısı her çizimde buna göre. */
    private var availDp = 0f
    private var scrollY = 0

    private val scanTimeout = Runnable { scanning = false; render() }

    /** Web paneli: son okunan durum, adresin QR'ı (adres değişmedikçe aynı örnek). */
    private var web: WebPanelState? = null
    private var webQr: QrMatrix? = null
    private var webQrUrl: String? = null
    private val webIo = LauncherIo.serial()
    private var webUnwatch: (() -> Unit)? = null
    private val webPoll = object : Runnable {
        override fun run() {
            readWeb()
            main.postDelayed(this, WEB_POLL_MS)
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) { scanning = false; main.removeCallbacks(scanTimeout) }
            render()
        }
    }

    val isShowing: Boolean get() = popup?.isShowing == true

    fun show(anchor: View) {
        if (isShowing) { dismiss(); return }
        // Simgeye ikinci dokunuş: önce dışarı dokunuşla kapandı, hemen yeniden açılmasın (aç/kapa gibi davranır).
        if (android.os.SystemClock.uptimeMillis() - dismissedAt < REOPEN_GUARD_MS) return
        val ctx = activity
        status = null
        availDp = availableDp(anchor)
        maxRows = rowsThatFit()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            // Kendi yüzeyi: alttaki kartlardan ayrışan ton ve belirgin 1dp kenar (gece #1E232B / #2A3038).
            background = rounded(ctx, if (LauncherTheme.night) 0xFF1E232B.toInt() else 0xFFFFFFFF.toInt(),
                Sahne.PANEL_RADIUS_DP, if (LauncherTheme.night) 0xFF2A3038.toInt() else 0x29181E28)
            val p = ctx.dp(PAD_DP)
            setPadding(p, p, p, p)
        }
        root = box
        // Hafif perde: panel alttaki içerikten ayrışır; dokununca kapanır.
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
        // Odak almayan pencere: sistem gezinme çubuğu geri gelmez, ekran kaymaz; dışarı dokunuş paneli kapatır.
        val pw = PopupWindow(box, ctx.dp(WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isTouchable = true
            isOutsideTouchable = true
            elevation = 0f
            setOnDismissListener { onDismissed() }
        }
        popup = pw
        render()
        box.alpha = 0f
        pw.showAsDropDown(anchor, 0, ctx.dp(8), Gravity.START)
        box.animate().alpha(1f).setDuration(FADE_MS).start()
        ctx.registerReceiver(receiver, IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        })
        scan()
        if (features.available) {
            main.post(webPoll)
            webUnwatch = features.watchWebPanel { readWeb() }
        }
    }

    fun dismiss() {
        popup?.dismiss()
    }

    /** İzin penceresi verilince ya da dışarıdan içeriği tazeler (ayarlara gidince onStop paneli kapatır; dönüşte yeniden açılır). */
    fun refresh() {
        if (isShowing) { render(); scan() }
    }

    /**
     * Konum izni penceresinin sonucu (onResume'dan önce gelir): verildiyse bir kez yeniden taranır; reddedildiyse satır kalır,
     * kısa bir not düşülür; yarıda kesildiyse yalnızca yeniden çizilir.
     */
    fun onPermissionResult(result: WifiAccess.PermissionResult) {
        if (!isShowing) return
        if (result == WifiAccess.PermissionResult.GRANTED) { status = null; refresh(); return }
        if (result == WifiAccess.PermissionResult.CANCELLED) { render(); return }
        status = activity.getString(R.string.launcher_wifi_permission_denied)
        statusAlert = true
        render()
    }

    private var dismissedAt = 0L

    private var scrim: PopupWindow? = null

    private fun onDismissed() {
        scrim?.dismiss()
        scrim = null
        dismissedAt = android.os.SystemClock.uptimeMillis()
        main.removeCallbacks(scanTimeout)
        main.removeCallbacks(webPoll)
        webUnwatch?.invoke()
        webUnwatch = null
        try { activity.unregisterReceiver(receiver) } catch (e: IllegalArgumentException) { }
        popup = null
        root = null
        scanning = false
    }

    /** Ağ listesinin önündeki engel: izin yok / sistem konumu kapalı / yok. */
    private fun gate(): WifiAccess.Gate = WifiAccess.gate(backend.hasLocationPermission(), backend.isLocationOn())

    private fun scan() {
        // İzinsiz ya da konum kapalıyken tarama istenmez: WifiService reddeder ve liste sessizce boş kalırdı.
        if (!backend.canList || !backend.isEnabled() || gate() != WifiAccess.Gate.LIST) { scanning = false; render(); return }
        scanning = backend.startScan()
        main.removeCallbacks(scanTimeout)
        if (scanning) main.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)
        render()
    }

    private fun availableDp(anchor: View): Float {
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val screenH = activity.resources.displayMetrics.heightPixels
        return (screenH - (loc[1] + anchor.height)) / activity.resources.displayMetrics.density - 24f
    }

    /**
     * Alt/üst sabit satırlardan (ve web paneli satırı ile açıksa QR bloğundan) artan yüksekliğe sığan ağ satırı sayısı
     * (2..6): sürüşte kaydırma olmadığından hepsi görünmeli.
     */
    private fun rowsThatFit(): Int {
        var fixedDp = PAD_DP * 2 + ROW_DP * 3 + SECTION_DP + 8
        if (features.available) fixedDp += ROW_DP + DIVIDER_DP
        if (web?.running == true) fixedDp += WEB_QR_DP + 16 - ROW_DP
        return ((availDp - fixedDp) / ROW_DP).toInt().coerceIn(2, WifiListShaper.MAX_VISIBLE)
    }

    // --- Çizim ---

    private fun render() {
        val box = root ?: return
        val ctx = activity
        // Kaydırma konumu yeniden çizimde korunur.
        (box.findViewWithTag<ScrollView>(TAG_SCROLL))?.let { scrollY = it.scrollY }
        box.removeAllViews()
        maxRows = rowsThatFit()
        val enabled = backend.isEnabled()
        box.addView(titleRow(enabled), sheetFullWidth())
        status?.let { msg ->
            box.addView(text(ctx, LauncherTheme.LABEL_SP, if (statusAlert) LauncherTheme.ALERT else LauncherTheme.TEXT_SECONDARY, medium = true, maxLines = 2).apply {
                text = msg
                setPadding(ctx.dp(12), ctx.dp(4), ctx.dp(12), ctx.dp(8))
            }, sheetFullWidth())
        }
        if (!enabled) {
            box.addView(messageRow(ctx.getString(R.string.launcher_wifi_off_state)), sheetFullWidth())
        } else {
            val cur = backend.current()
            if (cur != null) box.addView(currentRow(cur.first, cur.second), sheetFullWidth())
            if (backend.canList) renderNetworks(box, cur?.first) else box.addView(messageRow(ctx.getString(R.string.launcher_wifi_list_in_settings)), sheetFullWidth())
        }
        if (features.available) {
            box.addView(divider())
            renderWeb(box)
        }
        box.addView(divider())
        box.addView(settingsRow(), sheetFullWidth())
    }

    // --- Web paneli ---

    private fun readWeb() {
        webIo.runSafely {
            val s = try { features.readWebPanel() } catch (e: RuntimeException) { WebPanelState(false, null, false) }
            val qr = if (s.url != webQrUrl) s.url?.let { features.qr(it) } else webQr
            main.post {
                onWebState(s.running)
                if (s == web && qr === webQr) return@post
                web = s
                webQrUrl = s.url
                webQr = qr
                render()
            }
        }
    }

    /**
     * "Web paneli" satırı: ad ve sunucunun durumunu gösteren anahtar. Açıkken aynı satır büyür: adın altında adres ve not,
     * anahtarın solunda QR (tek blok; ağ listesine ve "Wi-Fi ayarları…" satırına ekranda yer kalsın).
     */
    private fun renderWeb(box: LinearLayout) {
        val ctx = activity
        val s = web
        val on = s?.running == true
        box.addView(baseRow().apply {
            if (on) setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(ctx, LauncherTheme.BODY_SP, medium = true).apply { setText(R.string.launcher_widget_web_panel) })
                if (!on) addView(text(ctx, LauncherTheme.LABEL_SP - 2f, LauncherTheme.TEXT_SECONDARY).apply { setText(R.string.launcher_wifi_web_sub) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(2) })
                if (on) {
                    addView(text(ctx, LauncherTheme.BODY_SP, LauncherTheme.TEXT).apply { text = s?.url?.removePrefix("http://") },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(6) })
                    addView(text(ctx, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY, maxLines = 2).apply {
                        setText(if (s?.reachable == false) R.string.launcher_fn_web_local else R.string.launcher_fn_web_scan)
                    })
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val qr = webQr
            if (on && qr != null) addView(QrView(ctx).apply {
                setMatrix(qr)
                contentDescription = ctx.getString(R.string.launcher_fn_qr_desc) + ", " + (s?.url ?: "")
            }, LinearLayout.LayoutParams(ctx.dp(WEB_QR_DP), ctx.dp(WEB_QR_DP)).apply { marginEnd = ctx.dp(8) })
            addView(switchView(on, ctx.getString(R.string.launcher_widget_web_panel)) { toggleWeb() })
        }, sheetFullWidth())
    }

    /**
     * Sunucuyu yerinde açar/kapatır; durum değişikliği izleyiciyle gelir. Başlatılamazsa (ör. port kullanımda) kısa bir
     * mesajla FrostNav'ın Aktar sekmesi açılır, ayrıntılı hata orada görünür.
     */
    private fun toggleWeb() {
        features.setWebPanel(web?.running != true) { msg ->
            readWeb()
            if (msg != null) {
                android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_LONG).show()
                dismiss()
                features.openTab(activity, FrostNavTab.WEB)
            }
        }
    }

    private fun renderNetworks(box: LinearLayout, connectedSsid: String?) {
        val ctx = activity
        box.addView(LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(4))
            addView(text(ctx, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY, medium = true).apply { setText(R.string.launcher_wifi_networks) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (scanning) addView(text(ctx, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY).apply { setText(R.string.launcher_wifi_scanning) })
        }, sheetFullWidth())
        when (gate()) {
            WifiAccess.Gate.NEED_PERMISSION -> { box.addView(permissionRow(), sheetFullWidth()); return }
            WifiAccess.Gate.LOCATION_OFF -> { box.addView(locationOffRow(), sheetFullWidth()); return }
            WifiAccess.Gate.LIST -> Unit
        }
        val drive = driving()
        val rows = WifiListShaper.shape(backend.scanResults() + LauncherDemo.wifiScans(), connectedSsid, backend::isSaved, drive)
        if (rows.isEmpty()) {
            if (!scanning) box.addView(messageRow(ctx.getString(R.string.launcher_wifi_none)), sheetFullWidth())
            return
        }
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        rows.take(if (drive) maxRows else rows.size).forEach { list.addView(networkRow(it), sheetFullWidth()) }
        if (WifiListShaper.scrollAllowed(drive)) {
            val scroll = object : ScrollView(ctx) {
                override fun onMeasure(w: Int, h: Int) {
                    super.onMeasure(w, MeasureSpec.makeMeasureSpec(ctx.dp(ROW_DP * maxRows), MeasureSpec.AT_MOST))
                }
            }.apply {
                tag = TAG_SCROLL
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(list)
                post { scrollTo(0, scrollY) }
            }
            box.addView(scroll, sheetFullWidth())
        } else {
            box.addView(list, sheetFullWidth())
        }
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

    private fun titleRow(enabled: Boolean): View = baseRow().apply {
        addView(text(activity, LauncherTheme.SECTION_SP, bold = true).apply { setText(R.string.launcher_wifi_title) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(switchView(enabled, activity.getString(R.string.launcher_wifi_title)) { toggle(!enabled) })
    }

    /** Açma/kapama anahtarı: 88x72 dokunma alanı, ortada 72x40 hap. */
    private fun switchView(on: Boolean, description: String, onClick: () -> Unit): View {
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
            contentDescription = description
            isSelected = on
            alpha = if (toggling) 0.5f else 1f
            addView(track, FrameLayout.LayoutParams(ctx.dp(72), ctx.dp(40), Gravity.CENTER))
            clickable(this) { onClick() }
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
            if (root != null) { render(); if (on) scan() }
        }
    }

    private fun currentRow(ssid: String?, level: Int): View = baseRow().apply {
        addView(icon(activity, R.drawable.ic_mdi_check, 32, LauncherTheme.ACCENT), LinearLayout.LayoutParams(activity.dp(40), activity.dp(32)))
        addView(text(activity, LauncherTheme.BODY_SP, medium = true).apply {
            text = ssid ?: activity.getString(R.string.launcher_wifi_connected)
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(glyph(level), LinearLayout.LayoutParams(activity.dp(36), activity.dp(36)).apply { marginStart = activity.dp(8); marginEnd = activity.dp(12) })
        addView(pill(R.string.launcher_wifi_disconnect) {
            Thread({
                backend.disconnect()
                main.postDelayed({ if (root != null) render() }, 1000)
            }, "wifi-disconnect").start()
        })
    }

    private fun networkRow(row: WifiRow): View = baseRow().apply {
        val scan = row.scan
        addView(View(activity), LinearLayout.LayoutParams(activity.dp(40), 1)) // onay işareti sütunuyla hizalı
        addView(text(activity, LauncherTheme.BODY_SP).apply {
            text = scan.ssid
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (scan.secured) addView(icon(activity, R.drawable.ic_mdi_lock, 24, LauncherTheme.TEXT_SECONDARY),
            LinearLayout.LayoutParams(activity.dp(24), activity.dp(24)).apply { marginEnd = activity.dp(12) })
        addView(glyph(scan.level), LinearLayout.LayoutParams(activity.dp(36), activity.dp(36)))
        contentDescription = scan.ssid
        clickable(this) { onNetworkTap(row) }
    }

    private fun glyph(level: Int): View = WifiGlyph(activity).apply { bind(WifiUi(WifiUi.Kind.CONNECTED, level.coerceIn(0, WifiStateMap.MAX_LEVEL))) }

    private fun pill(res: Int, onClick: () -> Unit): TextView =
        text(activity, LauncherTheme.LABEL_SP, LauncherTheme.TEXT, medium = true).apply {
            setText(res)
            gravity = Gravity.CENTER
            minimumHeight = activity.dp(56)
            background = rounded(activity, LauncherTheme.PANEL_PILL, 999f)
            setPadding(activity.dp(20), 0, activity.dp(20), 0)
            clickable(this, onClick)
        }

    private fun messageRow(msg: String): View = baseRow().apply {
        addView(text(activity, LauncherTheme.BODY_SP, LauncherTheme.TEXT_SECONDARY, maxLines = 2).apply { text = msg })
    }

    /**
     * İzin satırı: açıklama + "İzin ver" (çalışma anı izin penceresi). Daha önce reddedildiyse ve sistem artık sormuyorsa
     * düğme "Ayarları aç" olur ve uygulama ayrıntılarını açar (pencere hiç açılmayacağı için düğme boşa basılmasın).
     */
    private fun permissionRow(): View = baseRow().apply {
        val action = WifiAccess.permissionAction(WifiAccess.isDeniedBefore(),
            activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION))
        addView(text(activity, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY, maxLines = 3).apply { setText(R.string.launcher_wifi_need_location) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = activity.dp(12) })
        if (action == WifiAccess.PermissionAction.REQUEST) {
            addView(pill(R.string.launcher_wifi_grant) {
                activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
            })
        } else {
            addView(pill(R.string.launcher_wifi_open_settings) {
                openSettings(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", activity.packageName, null)))
            })
        }
    }

    /** Sistem konumu kapalı: açıklama + konum ayarlarını açan düğme; ayarlara gidince panel kapanır, yeniden açılınca güncel durum çizilir. */
    private fun locationOffRow(): View = baseRow().apply {
        addView(text(activity, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY, maxLines = 3).apply { setText(R.string.launcher_wifi_location_off) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = activity.dp(12) })
        addView(pill(R.string.launcher_wifi_location_settings) {
            openSettings(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        })
    }

    /** Ayar sayfasını açar; araçta o sayfa yoksa genel ayarlar. */
    private fun openSettings(intent: Intent) {
        try {
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: android.content.ActivityNotFoundException) {
            try { activity.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: RuntimeException) { }
        } catch (e: SecurityException) { }
    }

    /** Tam genişlikte 72dp satır: ad solda, sağda ok (bağlantı gibi mavi yazı değil). */
    private fun settingsRow(): View = baseRow().apply {
        minimumHeight = activity.dp(LauncherTheme.TOUCH_MIN_DP)
        addView(text(activity, LauncherTheme.BODY_SP, LauncherTheme.TEXT, medium = true).apply { setText(R.string.launcher_wifi_settings) },
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

    private fun onNetworkTap(row: WifiRow) {
        val scan = row.scan
        statusAlert = false
        if (!scan.secured || row.saved) {
            connect(scan, null)
        } else if (WifiListShaper.passwordAllowed(driving())) {
            dismiss()
            askPassword(scan)
        } else {
            status = activity.getString(R.string.launcher_wifi_park_for_password)
            statusAlert = true
            render()
        }
    }

    private fun connect(scan: WifiScan, password: String?) {
        status = activity.getString(R.string.launcher_wifi_connecting, scan.ssid)
        render()
        Thread({
            val ok = try { backend.connect(scan, password) } catch (e: RuntimeException) { false }
            main.post {
                if (!ok) {
                    // Kayıtlı sayılan ağ bağlanamadıysa (şifre değişmiş olabilir) park halinde şifre sorulur.
                    if (scan.secured && password == null && WifiListShaper.passwordAllowed(driving()) && root != null) {
                        dismiss(); askPassword(scan)
                    } else if (root != null) {
                        status = activity.getString(R.string.launcher_wifi_connect_failed)
                        statusAlert = true
                        render()
                    }
                }
            }
        }, "wifi-connect").start()
    }

    /** Yeni şifreli ağ: şifre + "Şifreyi göster" + "Bağlan" (yalnızca park halinde çağrılır). Şifre günlüğe yazılmaz, saklanmaz. */
    private fun askPassword(scan: WifiScan) {
        val ctx = activity
        showLauncherSheet(ctx, R.string.launcher_wifi_password_title, widthDp = 640) { dialog ->
            addView(text(ctx, LauncherTheme.BODY_SP, LauncherTheme.TEXT_SECONDARY, medium = true).apply { text = scan.ssid }, sheetFullWidth())
            addView(gap(ctx, 12, vertical = true))
            val input = EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setHint(R.string.launcher_wifi_password_hint)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, LauncherTheme.BODY_SP)
                setTextColor(LauncherTheme.TEXT)
                setHintTextColor(LauncherTheme.TEXT_SECONDARY)
                background = rounded(ctx, LauncherTheme.CONTROL, 16f)
                setPadding(ctx.dp(20), 0, ctx.dp(20), 0)
                isSingleLine = true
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            }
            val connectBtn = text(ctx, LauncherTheme.BODY_SP, LauncherTheme.ON_ACCENT, bold = true).apply {
                setText(R.string.launcher_wifi_connect)
                gravity = Gravity.CENTER
                background = rounded(ctx, DriveAccent.buttonFill(LauncherTheme.ACCENT), 16f)
                setPadding(ctx.dp(28), 0, ctx.dp(28), 0)
                clickable(this) {
                    val pw = input.text.toString()
                    if (pw.isEmpty()) return@clickable
                    dialog.dismiss()
                    Thread({ try { backend.connect(scan, pw) } catch (e: RuntimeException) { } }, "wifi-connect").start()
                }
            }
            // Giriş ve "Bağlan" aynı satırda: ekran klavyesi altta açılınca düğme örtülmesin.
            addView(LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(input, LinearLayout.LayoutParams(0, ctx.dp(LauncherTheme.TOUCH_MIN_DP), 1f).apply { marginEnd = ctx.dp(12) })
                addView(connectBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ctx.dp(LauncherTheme.TOUCH_MIN_DP)))
            }, sheetFullWidth())
            addView(gap(ctx, 12, vertical = true))
            var shown = false
            val showRow = baseRowFor(ctx)
            val showSwitchHolder = FrameLayout(ctx)
            fun drawSwitch() {
                showSwitchHolder.removeAllViews()
                val track = FrameLayout(ctx).apply {
                    background = rounded(ctx, if (shown) DriveAccent.buttonFill(LauncherTheme.ACCENT) else LauncherTheme.TRACK, 999f)
                    addView(View(ctx).apply { background = oval(if (shown) LauncherTheme.ON_ACCENT else LauncherTheme.TEXT_SECONDARY) },
                        FrameLayout.LayoutParams(ctx.dp(32), ctx.dp(32), (if (shown) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL)
                            .apply { marginStart = ctx.dp(4); marginEnd = ctx.dp(4) })
                }
                showSwitchHolder.addView(track, FrameLayout.LayoutParams(ctx.dp(72), ctx.dp(40), Gravity.CENTER))
            }
            drawSwitch()
            showRow.addView(text(ctx, LauncherTheme.BODY_SP).apply { setText(R.string.launcher_wifi_show_password) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            showRow.addView(showSwitchHolder, LinearLayout.LayoutParams(ctx.dp(88), ctx.dp(LauncherTheme.TOUCH_MIN_DP)))
            showRow.isClickable = true
            showRow.setOnClickListener {
                shown = !shown
                val sel = input.selectionEnd
                input.transformationMethod = if (shown) null else PasswordTransformationMethod.getInstance()
                input.setSelection(sel.coerceAtLeast(0))
                drawSwitch()
            }
            showRow.pressFeedback()
            addView(showRow, sheetFullWidth())
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
            input.requestFocus()
        }
    }

    private fun baseRowFor(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ctx.dp(LauncherTheme.TOUCH_MIN_DP)
    }

    companion object {
        /** HomeLauncherActivity izin kodları birbirinden ayrı olmalı: 4711 telefon izinleriydi, Wi-Fi sonucu oraya gidiyordu. */
        const val REQ_LOCATION = 4712
        private const val WIDTH_DP = 600
        private const val PAD_DP = 16
        private const val ROW_DP = LauncherTheme.TOUCH_MIN_DP
        private const val SECTION_DP = 40
        private const val FADE_MS = 120L
        private const val SCAN_TIMEOUT_MS = 6_000L
        private const val TAG_SCROLL = "wifi_scroll"
        private const val WEB_POLL_MS = 2_000L
        private const val REOPEN_GUARD_MS = 350L
        /** QR telefonla rahat okunur, ağ listesine yer bırakır. */
        private const val WEB_QR_DP = 124
        private const val DIVIDER_DP = 9
    }
}
