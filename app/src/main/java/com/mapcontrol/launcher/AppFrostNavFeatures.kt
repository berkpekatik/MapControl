package com.mapcontrol.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.mapcontrol.audio.AudioEqualizerController
import com.mapcontrol.manager.WebServerHost
import com.mapcontrol.service.DmsFaceRecognitionManager
import com.mapcontrol.service.MapControlService
import com.mapcontrol.ui.activity.MainActivity
import com.mapcontrol.util.ClusterNavigationState
import com.mapcontrol.util.SplitScreenLauncher
import com.mapcontrol.util.SplitScreenMessages
import com.mapcontrol.util.SplitScreenStore
import com.mapcontrol.util.TargetPackageStore
import com.mapcontrol.vehicle.ClimateAutomationEngine
import com.mapcontrol.vehicle.ClimateAutomationStore
import com.mapcontrol.vehicle.SunroofAutomationEngine

/**
 * [FrostNavFeatures]'ın gerçek sürümü: her çağrı FrostNav'ın kendi ayar sekmesinin kullandığı yoldan geçer. Yalnızca :app'te
 * derlenir (salt-okur test derlemesi bu dosyayı almaz, [FrostNavFeatures.NONE] kullanır).
 *
 * Web sunucusu: süreç genelindeki tek sahibi [WebServerHost]'tur; FrostNav'ın Aktar sekmesi de aynı örneği kullanır. Durum
 * ondan okunur ve dinlenir, adres FrostNav'ın kendi adres seçiminden ([WebServerHost.currentHost]) gelir; başlat/durdur
 * doğrudan yapılır. Yasal uyarı onaylanmadan (FrostNav hiç kurulmamışken) sunucu açılmaz, FrostNav'a yönlendirilir.
 */
internal class AppFrostNavFeatures(context: Context) : FrostNavFeatures {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    override val available = true

    // --- Web paneli ---

    private val web: WebServerHost get() = WebServerHost.get(app)

    override fun readWebPanel(): WebPanelState {
        if (!web.isRunning) return WebPanelState(running = false, url = null, reachable = false)
        // Adres her okumada güncel (hotspot sunucudan sonra açılmış olabilir); FrostNav'ın gösterdiği adresle aynı seçim.
        val host = try { web.currentHost() } catch (e: RuntimeException) { null }
        return WebPanelState(true, FrostNavWidgetMath.webUrl(host), !FrostNavWidgetMath.isLoopback(host))
    }

    override fun setWebPanel(on: Boolean, done: (String?) -> Unit) {
        if (!on) {
            web.stopServer()
            main.post { done(null) }
            return
        }
        if (!app.getSharedPreferences(PREFS_MAIN, Context.MODE_PRIVATE).getBoolean(KEY_DISCLAIMER_ACCEPTED, false)) {
            main.post { done(app.getString(com.mapcontrol.R.string.launcher_fn_web_needs_frostnav)) }
            return
        }
        web.startServer { error ->
            if (error != null) Log.w(TAG, "web start: $error")
            done(if (error == null) null else app.getString(com.mapcontrol.R.string.launcher_fn_web_start_failed))
        }
    }

    override fun watchWebPanel(onChange: () -> Unit): () -> Unit {
        val host = web
        val listener = WebServerHost.StateListener { onChange() }
        host.addStateListener(listener)
        return { host.removeStateListener(listener) }
    }

    override fun qr(text: String): QrMatrix? = try {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 0,
        )
        // 0×0 istenince zxing modül çözünürlüğünde (1 piksel = 1 modül) üretir; çizim launcher'da ölçeklenir.
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val size = minOf(m.width, m.height)
        QrMatrix(size, BooleanArray(size * size) { m.get(it % size, it / size) })
    } catch (e: WriterException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    // --- Ekolayzer (EqualizerTabBuilder.onPresetSelected ile aynı yol) ---

    override fun readEqualizer(): EqState {
        val s = AudioEqualizerController.read(app)
        return EqState(s.supported, s.enabled, s.preset, PRESETS)
    }

    override fun setEqualizer(enabled: Boolean, preset: String) {
        val s = AudioEqualizerController.read(app)
        val bands = if (preset == AudioEqualizerController.PRESET_CUSTOM) s.bands
        else AudioEqualizerController.bandsForPreset(preset, s.centerHz, s.minLevelMb, s.maxLevelMb)
        AudioEqualizerController.update(app, enabled, s.gainMb, bands, preset)
    }

    // --- Otomasyon (ClimateAutomationTabBuilder / SunroofAutomationTabBuilder ana anahtarlarıyla aynı yol) ---

    private fun climatePrefs() = app.getSharedPreferences(PREFS_MAIN, Context.MODE_PRIVATE)

    override fun readAutomations(): List<AutomationItem> = listOf(
        AutomationItem(AutomationId.CLIMATE, ClimateAutomationStore.isMasterEnabled(climatePrefs()), toggleable = true),
        AutomationItem(AutomationId.SUNROOF,
            SunroofAutomationEngine.getInstance(app).prefs.getBoolean(SunroofAutomationEngine.KEY_ENABLED, false), toggleable = true),
        // Yüz tanımayı açmak kamera izni ve kayıt akışı ister (DmsFaceRecognitionTabBuilder): yalnızca durumu gösterilir.
        AutomationItem(AutomationId.FACE, DmsFaceRecognitionManager.isEnabled(app), toggleable = false),
    )

    override fun setAutomation(id: AutomationId, on: Boolean) {
        when (id) {
            AutomationId.CLIMATE -> {
                ClimateAutomationStore.setMasterEnabled(climatePrefs(), on)
                val engine = ClimateAutomationEngine.getInstance(app)
                if (on) engine.notifyRulesChanged() else engine.disableAllRules()
            }
            AutomationId.SUNROOF -> SunroofAutomationEngine.getInstance(app).prefs.edit()
                .putBoolean(SunroofAutomationEngine.KEY_ENABLED, on).apply()
            AutomationId.FACE -> Unit
        }
        Log.i(TAG, "automation $id -> $on")
    }

    // --- Bölünmüş ekran (SplitScreenTabBuilder.onStartClicked ile aynı yol) ---

    override fun readSplitPair() = SplitPair(SplitScreenStore.getPkg1(app), SplitScreenStore.getPkg2(app))

    override fun startSplit(done: (String?) -> Unit) {
        val pair = readSplitPair()
        if (!FrostNavWidgetMath.splitReady(pair)) {
            main.post { done(app.getString(com.mapcontrol.R.string.split_select_both)) }
            return
        }
        SplitScreenLauncher.start(app, pair.first!!, pair.second!!, SplitScreenStore.getRatio(app)) { result ->
            Log.i(TAG, "split -> $result")
            done(if (result == SplitScreenLauncher.Result.STARTED) null else SplitScreenMessages.resultText(app, result))
        }
    }

    override fun startSplitWithMiniDash(done: (String?) -> Unit) {
        val other = FrostNavWidgetMath.miniDashSplitOther(SplitScreenLauncher.lastOtherApp(app)) { pkg ->
            try { app.packageManager.getApplicationInfo(pkg, 0); true } catch (e: Exception) { false }
        }
        SplitScreenLauncher.startWithMiniDash(app, other) { result ->
            Log.i(TAG, "split(miniDash, $other) -> $result")
            done(if (result == SplitScreenLauncher.Result.STARTED) null else SplitScreenMessages.resultText(app, result))
        }
    }

    // --- Sürüş ekranı (Navigasyon kartı; FrostNav'ın aynı bölme başlatıcısı) ---

    override fun startDriveScreen(mapPkg: String?, route: Intent?, done: (String?) -> Unit) {
        val slot = mapPkg ?: com.mapcontrol.util.SplitSlot.mapPicker(app.packageName)
        SplitScreenLauncher.startDriveScreen(app, slot, route) { result ->
            Log.i(TAG, "split(drive, ${if (mapPkg == null) "picker" else mapPkg}) -> $result")
            done(if (result == SplitScreenLauncher.Result.STARTED) null else SplitScreenMessages.resultText(app, result))
        }
    }

    override fun mapFullScreen(pkg: String, done: (Boolean) -> Unit) {
        SplitScreenLauncher.mapFullScreen(app, pkg) { result -> done(result == SplitScreenLauncher.Result.STARTED) }
    }

    override fun endDriveScreen() = SplitScreenLauncher.endDriveScreen(app)

    override fun openFullScreen(pkg: String) = SplitScreenLauncher.openFullScreenAsync(app, pkg)

    override fun restoreStaleMap(pkg: String) = SplitScreenLauncher.restoreStaleMap(app, pkg)

    override fun driveMapSlot(): android.graphics.Rect? = SplitScreenLauncher.driveMapSlot(app)

    // --- Göstergeye yansıt (MapControlService aç/kapa eylemleri → ClusterDisplayManager, Yansıtma sekmesiyle aynı) ---

    override fun readCluster() = ClusterState(ClusterNavigationState.getLastKnownOpen(), TargetPackageStore.read(app).ifEmpty { null })

    override fun setCluster(open: Boolean) {
        val intent = Intent(app, MapControlService::class.java)
            .setAction(if (open) MapControlService.ACTION_BENCH_OPEN_CLUSTER else MapControlService.ACTION_BENCH_CLOSE_CLUSTER)
            // Yansıtma sekmesindeki gibi true: hedef uygulama ana ekrana taşınınca HOME ile launcher yeniden öne gelir.
            .putExtra(MapControlService.EXTRA_CLUSTER_CLOSE_SEND_BACKGROUND, true)
        try {
            app.startService(intent)
        } catch (e: RuntimeException) {
            Log.w(TAG, "cluster $open: ${e.message}")
        }
    }

    override fun openTab(activity: Activity, tab: FrostNavTab) {
        activity.startActivity(
            Intent(activity, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, tabIndex(tab)),
        )
    }

    private fun tabIndex(tab: FrostNavTab): Int = when (tab) {
        // Web yönetimi ("Aktar"), Yansıtma ve Ses ayarları için MainActivity'de herkese açık sabit yok (tabTitle: 1, 3, 11).
        FrostNavTab.WEB -> TAB_WEB
        FrostNavTab.PROJECTION -> TAB_PROJECTION
        FrostNavTab.EQUALIZER -> TAB_EQUALIZER
        FrostNavTab.CLIMATE_AUTOMATION -> MainActivity.TAB_CLIMATE_AUTOMATION
        FrostNavTab.SUNROOF_AUTOMATION -> MainActivity.TAB_SUNROOF_AUTOMATION
        FrostNavTab.FACE_RECOGNITION -> MainActivity.TAB_FACE_RECOGNITION
        FrostNavTab.SPLIT_SCREEN -> MainActivity.TAB_SPLIT_SCREEN
    }

    private companion object {
        const val TAG = "FrostNavWidgets"
        const val PREFS_MAIN = "MapControlPrefs"
        /** MainActivity'nin yasal uyarı onayı; onaysız web sunucusu açılmaz. */
        const val KEY_DISCLAIMER_ACCEPTED = "disclaimerAccepted"
        const val TAB_WEB = 1
        const val TAB_PROJECTION = 3
        const val TAB_EQUALIZER = 11

        /** EqualizerTabBuilder'daki hazır ayar sırası. */
        val PRESETS = listOf(
            AudioEqualizerController.PRESET_FLAT,
            AudioEqualizerController.PRESET_BASS,
            AudioEqualizerController.PRESET_VOCAL,
            AudioEqualizerController.PRESET_TREBLE,
        )
    }
}
