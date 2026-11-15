package com.mapcontrol.util;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Ana iş parçacığında, uygulamayı düşürmemesi gereken tek bir bilinen OEM hatasını yutar.
 *
 * Bazı Chery araçlarında (vdbus.jar sistemde) VDConnector sistem kopyasıdır; BT servisi ölünce kendi Handler'ında
 * yeniden bağlanırken startForegroundService "Not allowed to start service ... vds.bt" SecurityException atar.
 * Bu bizim kodumuzun dışında olduğu için yakalanamaz ve launcher'ı düşürür (GlitchTip #2308/#2309).
 * Kalkan Looper.loop()'u try/catch içinde yeniden çalıştırır: yalnızca [isVdBindDenied] eşleşmesi yutulur, gerisi
 * aynen fırlatılır (normal çökme yolu, LauncherCrashGuard ve GlitchTip değişmez).
 */
public final class MainLoopShield {

    private static final String TAG = "MainLoopShield";
    private static final String VD_CONNECTOR = "com.desaysv.ivi.vdb.client.bind.VDConnector";
    /** Bir dakikada en fazla bu kadar yutma; aşılırsa döngü gibi davranır ve normal çökme yoluna bırakılır. */
    static final int MAX_PER_MINUTE = 20;
    private static final long MINUTE_MS = 60_000L;

    private static volatile boolean installed;
    private static long windowStart;
    private static int windowCount;
    private static boolean reported;

    private MainLoopShield() {
    }

    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        new Handler(Looper.getMainLooper()).postAtFrontOfQueue(MainLoopShield::loopForever);
    }

    private static void loopForever() {
        while (true) {
            try {
                Looper.loop();
                return; // Looper.quit(): ana döngü bitti.
            } catch (Throwable error) {
                if (!isVdBindDenied(error) || !allow(SystemClock.elapsedRealtime())) {
                    throw error;
                }
                Log.w(TAG, "OEM VDConnector yeniden bağlanma reddedildi, yutuldu: " + error.getMessage());
                reportOnce(error);
            }
        }
    }

    /** Saf: VDConnector.bindService içinden gelen "Not allowed to start service" SecurityException'ı mı. */
    static boolean isVdBindDenied(Throwable error) {
        if (!(error instanceof SecurityException)) {
            return false;
        }
        String msg = error.getMessage();
        if (msg == null || !msg.contains("Not allowed to start service")) {
            return false;
        }
        for (StackTraceElement e : error.getStackTrace()) {
            if (VD_CONNECTOR.equals(e.getClassName()) && "bindService".equals(e.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    /** Dakikalık sınır (ana iş parçacığında çağrılır). */
    static synchronized boolean allow(long nowMs) {
        if (nowMs - windowStart > MINUTE_MS) {
            windowStart = nowMs;
            windowCount = 0;
        }
        windowCount++;
        return windowCount <= MAX_PER_MINUTE;
    }

    static synchronized void resetForTest() {
        windowStart = 0;
        windowCount = 0;
        reported = false;
    }

    /** Süreç başına bir uyarı: hangi araçlarda olduğu GlitchTip'te görünsün, olay seli olmasın. */
    private static void reportOnce(Throwable error) {
        synchronized (MainLoopShield.class) {
            if (reported) {
                return;
            }
            reported = true;
        }
        try {
            Map<String, String> extras = new HashMap<>();
            String msg = error.getMessage();
            extras.put("message", msg == null ? "" : (msg.length() > 200 ? msg.substring(0, 200) : msg));
            com.mapcontrol.GlitchTip.reportWarning("vdbus_bind_denied:swallowed", extras);
        } catch (Throwable ignored) {
        }
    }
}
