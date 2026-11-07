package com.mapcontrol.api;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kontak kapanışındaki yüklemeler için ortak HTTP yardımcıları.
 *
 * <p>Ölçüm (güç geçiş tanısı): kontak 0 olduktan sonra internet ~2 sn kalır; kapı açılınca araç Wi-Fi'yi kapatır.
 * Bu sürede DNS + TCP + TLS kurmak pahalıdır. {@link HttpURLConnection} hesap sunucusuna açılan bağlantıyı havuzda
 * tutar (~5 dk); bağlantı {@link #finish} ile bırakılır, {@code disconnect()} ile kapatılmaz, sürüş sırasında da
 * {@link #prewarm} ile sıcak tutulur. Kapanıştaki yükleme böylece doğrudan isteği gönderir.
 */
public final class CloudHttp {
    private static final String TAG = "CloudHttp";
    /** Havuz boşta kalan bağlantıyı ~5 dk tutar; bundan sık tazelenir. */
    private static final long PREWARM_EVERY_MS = 4 * 60 * 1000L;
    private static final int PREWARM_TIMEOUT_MS = 5_000;
    private static final ExecutorService PREWARM = Executors.newSingleThreadExecutor();
    private static volatile long lastPrewarmAt;

    private CloudHttp() {
    }

    /** Yanıt gövdesini okuyup kapatır; bağlantı havuza döner. Okunamazsa bağlantı kapatılır. */
    public static void finish(HttpURLConnection conn, int code) {
        if (conn == null) {
            return;
        }
        try {
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in != null) {
                byte[] buf = new byte[1024];
                //noinspection StatementWithEmptyBody
                while (in.read(buf) >= 0) {
                }
                in.close();
            }
        } catch (IOException e) {
            conn.disconnect();
        }
    }

    /** Varsayılan ağ internete çıkabiliyor mu (Android doğruladı mı). Bilinemiyorsa true. */
    public static boolean hasInternet(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return true;
            }
            Network n = cm.getActiveNetwork();
            NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (RuntimeException e) {
            return true;
        }
    }

    /**
     * Sunucuya giden bağlantıyı sıcak tutar (en sık {@link #PREWARM_EVERY_MS}). Yolculuk sürerken çağrılır; hızlı döner,
     * yüklemeleri bekletmez. İnternet yoksa denemez.
     */
    public static void prewarm(Context context) {
        long now = SystemClock.elapsedRealtime();
        if (lastPrewarmAt != 0L && now - lastPrewarmAt < PREWARM_EVERY_MS) {
            return;
        }
        lastPrewarmAt = now;
        final Context app = context.getApplicationContext();
        PREWARM.execute(() -> {
            if (!hasInternet(app)) {
                lastPrewarmAt = 0L; // ağ gelince yeniden denensin
                return;
            }
            HttpURLConnection conn = null;
            int code = -1;
            try {
                conn = (HttpURLConnection) new URL(CloudApi.API_ROOT + "/").openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(PREWARM_TIMEOUT_MS);
                conn.setReadTimeout(PREWARM_TIMEOUT_MS);
                code = conn.getResponseCode();
            } catch (IOException | RuntimeException e) {
                Log.d(TAG, "bağlantı ısıtılamadı: " + e);
                lastPrewarmAt = 0L;
                if (conn != null) {
                    conn.disconnect();
                }
                return;
            }
            finish(conn, code);
        });
    }

    /** Kısa yükleme süresince CPU'yu uyanık tutar; {@code null} dönebilir. */
    public static PowerManager.WakeLock acquireWake(Context context, String tag, long timeoutMs) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm == null) {
                return null;
            }
            PowerManager.WakeLock wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag);
            wake.setReferenceCounted(false);
            wake.acquire(timeoutMs);
            return wake;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void release(PowerManager.WakeLock wake) {
        try {
            if (wake != null && wake.isHeld()) {
                wake.release();
            }
        } catch (RuntimeException ignored) {
        }
    }
}
