package com.mapcontrol.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;

import com.mapcontrol.service.GlobalBackService;

/**
 * Erişilebilirlik hizmetimizi açık tutar.
 * Bazı araçlarda (Jaecoo, iflytek ses asistanı) sistem uygulaması
 * {@code enabled_accessibility_services} listesini yalnız kendisini yazarak ezer; hizmetimiz düşer,
 * otomatik INSTALL ve ana ekran tuşu çalışmaz. Kurulum aracı WRITE_SECURE_SETTINGS verdiyse
 * kendimizi listeye geri ekleriz (yalnız ekleme; diğer hizmetler korunur).
 */
public final class AccessibilityKeeper {

    private static final String TAG = "A11yKeeper";
    /** Başka bir uygulamayla yazma yarışına girmemek için iki yazma arası en kısa süre. */
    private static final long MIN_WRITE_GAP_MS = 5_000L;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private static boolean watching;
    private static long lastWriteMs;
    private static boolean retryQueued;

    private AccessibilityKeeper() {
    }

    /** Uygulama açılışında bir kez: hemen dener ve liste değişince yeniden dener. */
    public static void watch(Context context) {
        Context app = context.getApplicationContext();
        if (watching || !canWrite(app)) {
            return;
        }
        watching = true;
        ensure(app, "acilis");
        ContentObserver observer = new ContentObserver(HANDLER) {
            @Override
            public void onChange(boolean selfChange, @Nullable Uri uri) {
                ensure(app, "liste degisti");
            }
        };
        try {
            app.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer);
            app.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED), false, observer);
        } catch (RuntimeException e) {
            Log.w(TAG, "gozlemci kaydedilemedi", e);
        }
    }

    /**
     * Hizmetimiz listede değilse ekler. Yazdıysa true (sistem birkaç yüz ms içinde bağlar).
     * Ana iş parçacığından çağrılır.
     */
    public static boolean ensure(Context context, String reason) {
        Context app = context.getApplicationContext();
        if (!canWrite(app) || GlobalBackService.isRegisteredInSystemAccessibilitySettings(app)) {
            return false;
        }
        long now = SystemClock.uptimeMillis();
        long wait = lastWriteMs + MIN_WRITE_GAP_MS - now;
        if (lastWriteMs != 0L && wait > 0) {
            if (!retryQueued) {
                retryQueued = true;
                HANDLER.postDelayed(() -> {
                    retryQueued = false;
                    ensure(app, reason + " (ertelenmis)");
                }, wait);
            }
            return false;
        }
        lastWriteMs = now;
        try {
            String current = Settings.Secure.getString(app.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String updated = withService(current, GlobalBackService.ACCESSIBILITY_FLAT_COMPONENT);
            Settings.Secure.putString(app.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated);
            Settings.Secure.putInt(app.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            DiagJournal.event("erisilebilirlik", "hizmet listeden dusmus, geri eklendi (" + reason
                    + "); onceki liste: " + current);
            Log.i(TAG, "hizmet yeniden eklendi: " + reason);
            return true;
        } catch (RuntimeException e) {
            DiagJournal.event("erisilebilirlik", "hizmet geri eklenemedi: " + e);
            return false;
        }
    }

    private static boolean canWrite(Context context) {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** ':' ile ayrılmış listeye [component]'i ekler; zaten varsa listeyi aynen döner. */
    static String withService(@Nullable String current, String component) {
        if (current == null || current.trim().isEmpty() || "null".equals(current.trim())) {
            return component;
        }
        for (String raw : current.split(":")) {
            String id = raw.trim();
            if (id.equals(component) || id.endsWith("service.GlobalBackService")) {
                return current;
            }
        }
        return current + ":" + component;
    }
}
