package com.mapcontrol.util;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.util.Collections;
import java.util.List;

/**
 * Wi-Fi tarama / bağlanma / bağlantıyı kesme / aç-kapa mantığı. FrostNav'ın Wi-Fi sekmesi ({@code WifiTabBuilder}) ve
 * launcher'ın Wi-Fi açılır paneli aynı kodu kullanır. Bağlanma WifiConfiguration + addNetwork/enableNetwork ile yapılır;
 * bu, araçta uygulama cihaz sahibi olduğu için çalışır (Android 10+ sıradan uygulamada ağ eklenemez).
 * Hiçbir yöntem arayüzle ilgilenmez; sonuçlar {@link Outcome} olarak döner. Ağ/IPC çağıran iş parçacığında yapılır.
 */
public class WifiController {

    public enum Result {
        /** Ağ etkinleştirildi, bağlantı kuruluyor. */
        STARTED,
        /** Ağ eklendi/bulundu ama etkinleştirilemedi. */
        ENABLE_FAILED,
        /** Ağ yapılandırması eklenemedi. */
        ADD_FAILED,
        /** Bağlantısı kesildi ({@link Outcome#ssid}). */
        DISCONNECTED,
        /** Kesilecek etkin bağlantı yok. */
        NO_ACTIVE,
        /** {@code disconnect()} başarısız. */
        DISCONNECT_FAILED,
        /** İstisna ({@link Outcome#error}). */
        ERROR
    }

    public static final class Outcome {
        public final Result result;
        public final String ssid;
        public final String error;

        Outcome(Result result, String ssid, String error) {
            this.result = result;
            this.ssid = ssid;
            this.error = error;
        }
    }

    public interface Log {
        void log(String message);
    }

    private final Context context;
    private final WifiManager wifiManager;

    /** Tarama ve sonuçlar için gereken çalışma anı izni (Android 9+ WifiService konum izni ister). */
    public static boolean hasLocationPermission(Context context) {
        return context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Sistem konumu açık mı: {@code Settings.Secure.LOCATION_MODE} 0 (kapalı) değilse açık. Okunamazsa açık sayılır
     * (yanlış "Konum kapalı" uyarısı göstermemek için).
     */
    @SuppressWarnings("deprecation")
    public static boolean isLocationOn(Context context) {
        try {
            return android.provider.Settings.Secure.getInt(context.getContentResolver(),
                    android.provider.Settings.Secure.LOCATION_MODE) != android.provider.Settings.Secure.LOCATION_MODE_OFF;
        } catch (android.provider.Settings.SettingNotFoundException | RuntimeException e) {
            return true;
        }
    }

    public WifiController(Context context) {
        this.context = context.getApplicationContext();
        this.wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
    }

    public boolean available() {
        return wifiManager != null;
    }

    /** Açık mı; hata olursa false. */
    public boolean isEnabled() {
        try {
            return wifiManager != null && wifiManager.isWifiEnabled();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Wi-Fi'yi açar/kapatır. Telefon oturumunu ve erişim noktasını (ya da "kablosuz projeksiyonu koru" tercihine göre
     * yalnızca günlük) önce ele alır. Bloklar; arka plan iş parçacığında çağrılır. {@code log} çağıran iş parçacığında çalışır.
     *
     * @return setWifiEnabled sonucu
     */
    public boolean setEnabledBlocking(boolean newState, Log log) {
        if (wifiManager == null) return false;
        if (NetworkWifiHelper.keepWirelessProjection(context)) {
            Boolean concurrent = NetworkWifiHelper.staApConcurrencyOrNull(context);
            log.log("Telefon bağlantısı korunuyor. STA+AP: " + concurrent);
        } else {
            PhoneLinkSession.Release phone = PhoneLinkSession.releaseWireless(context);
            boolean apDown = NetworkWifiHelper.releaseAccessPoint(context);
            if (phone == PhoneLinkSession.Release.INACTIVE) {
                log.log("Telefon oturumu kapandı");
            } else if (phone == PhoneLinkSession.Release.UNREACHABLE) {
                log.log("Telefon servisine ulaşılamadı");
            } else {
                log.log("Telefon oturumu kapanmadı");
            }
            log.log(apDown
                    ? "Erişim noktası kapandı"
                    : "Erişim noktası kapanışı doğrulanamadı; Wi-Fi yine de değiştiriliyor");
        }
        boolean result = wifiManager.setWifiEnabled(newState);
        log.log("setWifiEnabled(" + newState + ") sonucu: " + result);
        return result;
    }

    /** Taramayı başlatır; başlamadıysa false. */
    @SuppressWarnings("deprecation")
    public boolean startScan() {
        return wifiManager != null && wifiManager.startScan();
    }

    /** Son tarama sonuçları (konum izni yoksa boş olabilir). */
    public List<ScanResult> scanResults() {
        if (wifiManager == null) return Collections.emptyList();
        try {
            List<ScanResult> r = wifiManager.getScanResults();
            return r != null ? r : Collections.<ScanResult>emptyList();
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("deprecation")
    public WifiInfo connectionInfo() {
        if (wifiManager == null) return null;
        try {
            return wifiManager.getConnectionInfo();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean isSecure(String capabilities) {
        return capabilities != null && (capabilities.contains("WPA") || capabilities.contains("WEP"));
    }

    public static boolean isOpen(String capabilities) {
        return !isSecure(capabilities);
    }

    /** Kayıtlı ağın id'si ({@code ssid} tırnaksız); yoksa -1. Cihaz sahibi olmayan uygulamada liste boş gelir. */
    @SuppressWarnings("deprecation")
    public int savedNetworkId(String ssid) {
        if (wifiManager == null || ssid == null) return -1;
        try {
            List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
            if (configs == null) return -1;
            String quoted = "\"" + ssid + "\"";
            for (WifiConfiguration c : configs) {
                if (c.SSID != null && c.SSID.equals(quoted)) return c.networkId;
            }
        } catch (RuntimeException e) {
            // izin yok: kayıtlı sayılmaz
        }
        return -1;
    }

    /** Kayıtlı bir ağı etkinleştirir (şifre yeniden istenmez). */
    @SuppressWarnings("deprecation")
    public Outcome connectSaved(String ssid) {
        try {
            int id = savedNetworkId(ssid);
            if (id == -1) return new Outcome(Result.ADD_FAILED, ssid, null);
            return wifiManager.enableNetwork(id, true)
                    ? new Outcome(Result.STARTED, ssid, null) : new Outcome(Result.ENABLE_FAILED, ssid, null);
        } catch (RuntimeException e) {
            return new Outcome(Result.ERROR, ssid, e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    public Outcome connectOpen(String ssid) {
        try {
            WifiConfiguration config = new WifiConfiguration();
            config.SSID = "\"" + ssid + "\"";
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            config.allowedAuthAlgorithms.clear();
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);

            int networkId = wifiManager.addNetwork(config);
            if (networkId == -1) networkId = savedNetworkId(ssid);
            return enable(networkId, ssid);
        } catch (RuntimeException e) {
            return new Outcome(Result.ERROR, ssid, e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    public Outcome connectSecure(String ssid, String capabilities, String password) {
        try {
            WifiConfiguration config = new WifiConfiguration();
            config.SSID = "\"" + ssid + "\"";

            if (capabilities.contains("WPA3")) {
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.SAE);
                config.preSharedKey = "\"" + password + "\"";
            } else if (capabilities.contains("WPA2")) {
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                config.preSharedKey = "\"" + password + "\"";
            } else if (capabilities.contains("WPA")) {
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                config.preSharedKey = "\"" + password + "\"";
            } else if (capabilities.contains("WEP")) {
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.NONE);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
                config.wepKeys[0] = wepKey(password);
                config.wepTxKeyIndex = 0;
            }

            int networkId = wifiManager.addNetwork(config);
            if (networkId == -1) {
                // Zaten kayıtlı: parolayı güncelle.
                List<WifiConfiguration> existing = wifiManager.getConfiguredNetworks();
                if (existing != null) {
                    for (WifiConfiguration c : existing) {
                        if (c.SSID != null && c.SSID.equals("\"" + ssid + "\"")) {
                            networkId = c.networkId;
                            if (capabilities.contains("WEP")) {
                                c.wepKeys[0] = wepKey(password);
                            } else {
                                c.preSharedKey = "\"" + password + "\"";
                            }
                            wifiManager.updateNetwork(c);
                            break;
                        }
                    }
                }
            }
            return enable(networkId, ssid);
        } catch (RuntimeException e) {
            return new Outcome(Result.ERROR, ssid, e.getMessage());
        }
    }

    private static String wepKey(String password) {
        int n = password.length();
        return (n == 10 || n == 26 || n == 58) ? password : "\"" + password + "\"";
    }

    @SuppressWarnings("deprecation")
    private Outcome enable(int networkId, String ssid) {
        if (networkId == -1) return new Outcome(Result.ADD_FAILED, ssid, null);
        return wifiManager.enableNetwork(networkId, true)
                ? new Outcome(Result.STARTED, ssid, null) : new Outcome(Result.ENABLE_FAILED, ssid, null);
    }

    /** Etkin bağlantıyı keser. */
    @SuppressWarnings("deprecation")
    public Outcome disconnect() {
        if (wifiManager == null) return new Outcome(Result.ERROR, null, "WifiManager bulunamadı");
        try {
            WifiInfo info = wifiManager.getConnectionInfo();
            if (info == null || info.getNetworkId() == -1) return new Outcome(Result.NO_ACTIVE, null, null);
            String raw = info.getSSID();
            if (raw == null) return new Outcome(Result.NO_ACTIVE, null, null);
            String ssid = raw.replace("\"", "");
            return wifiManager.disconnect()
                    ? new Outcome(Result.DISCONNECTED, ssid, null) : new Outcome(Result.DISCONNECT_FAILED, ssid, null);
        } catch (RuntimeException e) {
            return new Outcome(Result.ERROR, null, e.getMessage());
        }
    }
}
