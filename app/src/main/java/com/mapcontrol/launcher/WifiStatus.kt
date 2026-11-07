package com.mapcontrol.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper

/** Başlıktaki Wi-Fi göstergesinin durumu: [kind], sinyal seviyesi 0..4 ve (sığıyorsa) kısa ağ adı. */
internal data class WifiUi(val kind: Kind, val level: Int = 0, val ssid: String? = null) {
    enum class Kind { OFF, CONNECTED, HOTSPOT }
}

/** Saf durum eşlemesi (JVM'de sınanır). */
internal object WifiStateMap {
    const val MAX_LEVEL = 4
    const val MAX_SSID = 12

    /**
     * Önce hotspot (araç ünitesi bağlantı paylaşıyorsa istemci tarafı anlamsız), sonra bağlı, aksi halde kapalı/bağlı değil.
     * [rssiLevel] 0..4 dışı değerler kırpılır; [ssid] tırnaklardan arındırılır, bilinmiyorsa ya da [MAX_SSID]'den uzunsa gösterilmez.
     */
    fun map(wifiEnabled: Boolean, hotspotOn: Boolean, connected: Boolean, rssiLevel: Int, ssid: String?): WifiUi = when {
        hotspotOn -> WifiUi(WifiUi.Kind.HOTSPOT)
        wifiEnabled && connected -> WifiUi(WifiUi.Kind.CONNECTED, rssiLevel.coerceIn(0, MAX_LEVEL), shortSsid(ssid))
        else -> WifiUi(WifiUi.Kind.OFF)
    }

    fun shortSsid(raw: String?): String? {
        val s = raw?.trim()?.removeSurrounding("\"")?.trim() ?: return null
        if (s.isEmpty() || s == "<unknown ssid>" || s == "0x") return null
        return s.takeIf { it.length <= MAX_SSID }
    }
}

/**
 * Wi-Fi durumunu olaylarla izler (zamanlayıcı yok): [ConnectivityManager.NetworkCallback] (bağlandı/koptu) ile sinyal,
 * Wi-Fi açık/kapalı ve hotspot yayınları. FrostNav'ın HeaderStatusMonitor'ü ile aynı mantık (Wi-Fi açık + Wi-Fi ağı var = bağlı);
 * o sınıf yalnızca uygulama sürümünde olduğu için launcher'ın test sürümünde de çalışan bu küçük kopya kullanılır.
 * [start]/[stop] eşleşmelidir; her ikisi de ana iş parçacığında çağrılır.
 */
internal class WifiStatus(context: Context, private val onChange: (WifiUi) -> Unit) {
    private val app = context.applicationContext
    private val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private var hotspot = false
    private val refreshTask = Runnable { if (started) onChange(read()) }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = post()
        override fun onLost(network: Network) = post()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = post()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AP_STATE_ACTION) hotspot = intent.getIntExtra(EXTRA_AP_STATE, 0) == AP_ENABLED
            post()
        }
    }

    private fun post() {
        main.removeCallbacks(refreshTask)
        main.post(refreshTask)
    }

    fun start() {
        if (started) return
        started = true
        hotspot = readHotspot()
        try {
            connectivity?.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback)
        } catch (e: RuntimeException) {
            // İzin/servis yok: yayınlar ve ilk okuma yine çalışır.
        }
        app.registerReceiver(receiver, IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(AP_STATE_ACTION)
        })
        onChange(read())
    }

    fun stop() {
        if (!started) return
        started = false
        main.removeCallbacks(refreshTask)
        try { connectivity?.unregisterNetworkCallback(callback) } catch (e: IllegalArgumentException) { }
        try { app.unregisterReceiver(receiver) } catch (e: IllegalArgumentException) { }
    }

    @Suppress("DEPRECATION")
    private fun read(): WifiUi {
        val w = wifi ?: return WifiUi(WifiUi.Kind.OFF)
        val enabled = try { w.isWifiEnabled } catch (e: RuntimeException) { false }
        val connected = enabled && wifiNetworkUp()
        val info = if (connected) try { w.connectionInfo } catch (e: RuntimeException) { null } else null
        val level = info?.let { WifiManager.calculateSignalLevel(it.rssi, WifiStateMap.MAX_LEVEL + 1) } ?: 0
        return WifiStateMap.map(enabled, hotspot, connected, level, info?.ssid)
    }

    @Suppress("DEPRECATION")
    private fun wifiNetworkUp(): Boolean = try {
        connectivity?.allNetworks?.any { n ->
            connectivity.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } == true
    } catch (e: RuntimeException) { false }

    /** Hotspot açık mı: gizli `isWifiApEnabled`; erişilemezse yayın gelene dek kapalı sayılır. */
    private fun readHotspot(): Boolean = try {
        WifiManager::class.java.getMethod("isWifiApEnabled").invoke(wifi) as? Boolean ?: false
    } catch (e: ReflectiveOperationException) { false } catch (e: RuntimeException) { false }

    private companion object {
        const val AP_STATE_ACTION = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        const val EXTRA_AP_STATE = "wifi_state"
        const val AP_ENABLED = 13
    }
}
