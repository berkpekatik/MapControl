package com.mapcontrol.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Method;

/**
 * Wi-Fi radyo durumu, erişim noktası ve aktif ağın internet taşıyıp taşımadığı.
 */
public final class NetworkWifiHelper {

    private static final String PREFS_NAME = "MapControlPrefs";
    /** Kapalıyken Wi-Fi radyosu değişmeden önce araç erişim noktası düşer. */
    public static final String KEY_KEEP_WIRELESS_PROJECTION = "force_keep_wireless_projection";

    private static final String AP_STATUS_ACTION = "com.desay.systemui.set_ap_status";
    private static final String AP_STATUS_EXTRA = "ap_status";
    /** {@code WifiManager.WIFI_AP_STATE_DISABLED}. */
    private static final int WIFI_AP_STATE_DISABLED = 11;
    private static final long AP_WAIT_MS = 3000L;
    private static final long AP_POLL_MS = 300L;

    @Nullable
    private static volatile Method wifiApStateMethod;
    private static volatile boolean wifiApStateResolved;

    private NetworkWifiHelper() {
    }

    public static boolean keepWirelessProjection(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_KEEP_WIRELESS_PROJECTION, false);
    }

    public static void setKeepWirelessProjection(@NonNull Context context, boolean keep) {
        prefs(context).edit().putBoolean(KEY_KEEP_WIRELESS_PROJECTION, keep).apply();
    }

    /**
     * Araç erişim noktasını kapatır ve durum kapalıya düşünceye kadar bekler.
     * Radyo değişikliği bu dönüşten sonra yapılmalı. Doğrulanamazsa yine false döner;
     * çağıran Wi-Fi isteğini buna rağmen gönderebilir.
     */
    public static boolean releaseAccessPoint(@NonNull Context context) {
        Context app = context.getApplicationContext();
        try {
            app.sendBroadcast(new Intent(AP_STATUS_ACTION).putExtra(AP_STATUS_EXTRA, false));
        } catch (RuntimeException e) {
            return false;
        }
        WifiManager wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
        Method method = wifiApStateMethod();
        if (wm == null || method == null) {
            return false;
        }
        long deadline = SystemClock.uptimeMillis() + AP_WAIT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            Integer state = readApState(wm, method);
            if (state == null) {
                return false;
            }
            if (state == WIFI_AP_STATE_DISABLED) {
                return true;
            }
            try {
                Thread.sleep(AP_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Radyonun erişim noktası ile istemci Wi-Fi’yi aynı anda taşıyıp taşımadığı.
     * API 30 altı veya çağrı hata verirse null.
     */
    @Nullable
    public static Boolean staApConcurrencyOrNull(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null;
        }
        WifiManager wm = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wm == null) {
            return null;
        }
        try {
            return wm.isStaApConcurrencySupported();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Nullable
    private static Method wifiApStateMethod() {
        if (wifiApStateResolved) {
            return wifiApStateMethod;
        }
        synchronized (NetworkWifiHelper.class) {
            if (!wifiApStateResolved) {
                try {
                    Method method = WifiManager.class.getDeclaredMethod("getWifiApState");
                    method.setAccessible(true);
                    wifiApStateMethod = method;
                } catch (Throwable ignored) {
                    wifiApStateMethod = null;
                }
                wifiApStateResolved = true;
            }
            return wifiApStateMethod;
        }
    }

    @Nullable
    private static Integer readApState(WifiManager wm, Method method) {
        try {
            Object value = method.invoke(wm);
            return value instanceof Integer ? (Integer) value : null;
        } catch (Throwable ignored) {
            return null;
        }
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
