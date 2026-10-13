package com.mapcontrol.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

import com.mapcontrol.service.BootReceiver;

/**
 * Aynı açılışı tanır. Süreç yeniden doğunca karşılama ve ana ekran ikinci kez açılmaz.
 * Dakika kovasında on birimlik sapma aynı açılış sayılır.
 */
public final class BootSession {

    private static final String KEY_ELAPSED = "sonGorulduBoot";
    private static final String KEY_WELCOME = "welcomeShownBootId";
    private static final String KEY_UI = "uiLaunchedBootId";
    private static final String KEY_UI_PENDING = "bootUiPending";
    private static final long MINUTE_MS = 60_000L;
    private static final long BUCKET_TOLERANCE = 10L;

    private BootSession() {
    }

    /**
     * Servis ayağa kalkınca çağrılır. Uptime geriye sardıysa bu açılışın kayıtları silinir.
     */
    public static void noteServiceStart(Context context) {
        SharedPreferences prefs = prefs(context);
        long elapsed = SystemClock.elapsedRealtime();
        long stored = prefs.getLong(KEY_ELAPSED, 0L);
        SharedPreferences.Editor editor = prefs.edit().putLong(KEY_ELAPSED, elapsed);
        if (stored > elapsed) {
            editor.remove(KEY_WELCOME).remove(KEY_UI);
        }
        editor.apply();
    }

    public static boolean isWelcomeShown(Context context) {
        return sameBoot(prefs(context).getString(KEY_WELCOME, null), currentId(context));
    }

    public static void markWelcomeShown(Context context) {
        prefs(context).edit().putString(KEY_WELCOME, currentId(context)).apply();
    }

    public static void clearWelcomeShown(Context context) {
        prefs(context).edit().remove(KEY_WELCOME).apply();
    }

    public static void setUiPending(Context context, boolean pending) {
        prefs(context).edit().putBoolean(KEY_UI_PENDING, pending).commit();
    }

    public static boolean isUiPending(Context context) {
        return prefs(context).getBoolean(KEY_UI_PENDING, false);
    }

    public static boolean isUiLaunched(Context context) {
        return sameBoot(prefs(context).getString(KEY_UI, null), currentId(context));
    }

    public static void markUiLaunched(Context context) {
        prefs(context).edit().putString(KEY_UI, currentId(context)).apply();
    }

    static String currentId(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                int count = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
                if (count >= 0) {
                    return "s" + count;
                }
            } catch (Exception ignored) {
            }
        }
        long bucket = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / MINUTE_MS;
        return "t" + bucket;
    }

    static boolean sameBoot(String stored, String current) {
        if (stored == null || current == null) {
            return false;
        }
        if (!stored.startsWith("t") || !current.startsWith("t")) {
            return stored.equals(current);
        }
        try {
            long left = Long.parseLong(stored.substring(1));
            long right = Long.parseLong(current.substring(1));
            return Math.abs(left - right) <= BUCKET_TOLERANCE;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE);
    }
}
