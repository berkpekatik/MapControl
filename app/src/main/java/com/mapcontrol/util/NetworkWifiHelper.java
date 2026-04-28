package com.mapcontrol.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;

import androidx.annotation.NonNull;

/**
 * Wi-Fi üzerinden internet erişiminin mevcut olup olmadığını (aktif ağ) bildirir.
 */
public final class NetworkWifiHelper {

    private NetworkWifiHelper() {
    }

    /**
     * @return aktif ağ Wi-Fi ve {@link NetworkCapabilities#NET_CAPABILITY_INTERNET} varsa true
     */
    public static boolean isWifiConnectedWithInternet(@NonNull Context context) {
        Context app = context.getApplicationContext();
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) {
            return false;
        }
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return false;
        }
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /**
     * Wi-Fi radyo durumu (OEM/araç cihazlarında {@link #startWifiScan} / yeniden bağlanma için).
     */
    @SuppressWarnings("deprecation")
    public static boolean setWifiEnabled(@NonNull Context context, boolean enabled) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) {
            return false;
        }
        try {
            return wm.setWifiEnabled(enabled);
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean startWifiScan(@NonNull Context context) {
        Context app = context.getApplicationContext();
        WifiManager wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
        if (wm == null) {
            return false;
        }
        if (!wm.isWifiEnabled()) {
            return false;
        }
        try {
            return wm.startScan();
        } catch (Exception e) {
            return false;
        }
    }
}
