package com.mapcontrol.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.desaysv.ivi.extra.project.carinfo.CarSettingID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.ui.activity.MainActivity
import com.mapcontrol.util.WifiController

/**
 * Launcher'ın araca komut gönderen ve MapControl'e bağlanan tek noktası.
 */
internal object LauncherEdition {
    /** MapControl launcher modu açık mı (Ana ekran tuşunu MapControl yakalıyor; sistem varsayılan HOME OEM olabilir). */
    fun launcherModeOn(context: Context): Boolean = com.mapcontrol.util.LauncherModeManager.isEnabled(context)


    /** true: araç verisi yalnızca okunur, dokunuşlar komut göndermez. */
    const val READ_ONLY = false

    /**
     * Widget yazmaları açık. Yine de yalnızca araçta doğrulanmış girdiler gider ([WidgetWritePolicy.gate]: doğrulanmış VE
     * açık); bagaj, kilit ve sürüş modu ayrıca park kapısından geçer. Her komut `WidgetWrite` etiketiyle günlüğe yazılır.
     */
    const val WRITES_ON = true

    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context) = Unit

    /** Launcher (ana ekran / Mini gösterge) öndeyken yüzen MapControl geri okunu gizler; başka uygulama önde iken eskisi gibi. */
    fun setHomeForeground(front: Boolean) = com.mapcontrol.manager.FloatingBackButtonManager.setSuppressed(front)

    /** Ana ekran göründü: açılışta (MapControlService) istenen arayüz açılmış sayılır ve yeniden başlatılmaz. */
    fun onLauncherShown(context: Context) = com.mapcontrol.util.BootSession.markUiLaunched(context)

    /** Ayarlar (MapControl ana ekranı) bu derlemede var mı. */
    const val HAS_SETTINGS = true

    /**
     * Koltuk soğutma yazması araçta doğrulandı (sürücü, CS 93: yazma seviye+1 = 2/3/4, kapalı = 1; okuma 1/2/3, kapalı 0).
     * Yolcu tarafı (CS 94) aynı kodlamayla gidiyor, ayrıca ölçülmedi.
     */
    const val SEAT_VENT_WRITE_VERIFIED = true

    /** Koltuk soğutma seviyesi (0..3). Kodlama ısıtmayla aynı (sürücüde araçta doğrulandı). */
    fun sendSeatVentLevel(proxy: CarInfoProxy, driver: Boolean, level: Int): Boolean {
        if (!SEAT_VENT_WRITE_VERIFIED) return false
        val id = if (driver) CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION else CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION
        // Kodlama ısıtmayla aynı (SeatHeatModel.encode: 0 kapalı → 1, 1..3 → 2..4), ClimateAutomationEngine.wireSeatLevel gibi.
        return WidgetWriter.send(proxy, VDEventCarInfo.MODULE_CAR_SETTING, id, SeatHeatModel.encode(level))
    }

    /** Beyaz liste + `WidgetWrite` günlüğü ([WidgetWriter]). */
    fun sendItemValue(proxy: CarInfoProxy, module: Int, id: Int, value: Int): Boolean =
        WidgetWriter.send(proxy, module, id, value)

    /** Ses alanı (arka hoparlör): [balance] sol-sağ, [fade] ön-arka, 0..20. Çağıran kapıyı ([ComfortWriter.rearSpeakerEnabled]) zaten denetledi. */
    fun sendSoundField(balance: Int, fade: Int): Boolean = WidgetWriter.sendSoundField(balance, fade)

    fun setCentralLock(context: Context, locked: Boolean): Boolean = WidgetWriter.sendLock(context, locked)

    /**
     * MapControl'ı doğrudan "Ana ekran" sekmesinde açar. Launcher kendi görev ilişkisinde (taskAffinity) çalışır;
     * CLEAR_TOP|SINGLE_TOP MapControl'ı açıksa öne getirip onNewIntent ile sekmeyi iletir.
     */
    fun openSettings(activity: Activity) {
        activity.startActivity(
            Intent(activity, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_HOME_SCREEN),
        )
    }

    /** MapControl'ı doğrudan "Wi-Fi" sekmesinde açar (yakındaki ağları tarar, listeler ve bağlanır). */
    fun openWifi(activity: Activity) {
        activity.startActivity(
            Intent(activity, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_WIFI),
        )
    }

    /** Wi-Fi paneli arka ucu: MapControl'ın Wi-Fi sekmesiyle paylaşılan [WifiController] (tara, bağlan, kes, aç/kapa). */
    fun wifiBackend(context: Context): WifiBackend = AppWifiBackend(context.applicationContext)

    /** MapControl widget'larının arka ucu: MapControl'ın kendi yöneticileri, tercihleri ve servisi ([AppMapControlFeatures]). */
    fun mapcontrol(context: Context): MapControlFeatures = AppMapControlFeatures(context.applicationContext)
}

private class AppWifiBackend(private val context: Context) : WifiBackend {
    private val controller = WifiController(context)
    private val main = Handler(Looper.getMainLooper())

    override val canList = true

    override fun isEnabled() = controller.isEnabled()

    override fun setEnabled(on: Boolean, done: () -> Unit) {
        Thread({
            try {
                controller.setEnabledBlocking(on) { Log.d(TAG, it) }
            } catch (e: RuntimeException) {
                Log.w(TAG, "setEnabled: ${e.message}")
            }
            main.postDelayed(done, SETTLE_MS)
        }, "wifi-radio").start()
    }

    override fun hasLocationPermission() =
        context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun isLocationOn() = WifiController.isLocationOn(context)

    override fun startScan() = try { controller.startScan() } catch (e: RuntimeException) { false }

    override fun scanResults(): List<WifiScan> = controller.scanResults().map {
        WifiScan(it.SSID.orEmpty(), WifiManager.calculateSignalLevel(it.level, WifiStateMap.MAX_LEVEL + 1), it.capabilities)
    }

    override fun current(): Pair<String?, Int>? {
        val info = controller.connectionInfo() ?: return null
        if (info.supplicantState != SupplicantState.COMPLETED) return null
        return WifiListShaper.cleanSsid(info.ssid) to WifiManager.calculateSignalLevel(info.rssi, WifiStateMap.MAX_LEVEL + 1)
    }

    override fun isSaved(ssid: String) = controller.savedNetworkId(ssid) != -1

    override fun connect(scan: WifiScan, password: String?): Boolean {
        val out = when {
            controller.savedNetworkId(scan.ssid) != -1 && password == null -> controller.connectSaved(scan.ssid)
            !scan.secured -> controller.connectOpen(scan.ssid)
            password != null -> controller.connectSecure(scan.ssid, scan.capabilities.orEmpty(), password)
            else -> return false
        }
        return out.result == WifiController.Result.STARTED
    }

    override fun disconnect() = controller.disconnect().result == WifiController.Result.DISCONNECTED

    private companion object {
        const val TAG = "WifiPanel"
        const val SETTLE_MS = 800L
    }
}
