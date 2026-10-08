package com.mapcontrol.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Launcher orta saat kartındaki dört yönlü kaydırma hedefi.
 * Değer bir paket adı ya da {@code action:} ile başlayan yerleşik işlemdir.
 */
public final class LauncherSwipeStore {

    public static final int UP = 0;
    public static final int DOWN = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;

    public static final String ACTION_MAIN_MENU = "action:main_menu";
    public static final String ACTION_APP_TRAY = "action:app_tray";
    public static final String ACTION_CLUSTER_OPEN = "action:cluster_open";
    public static final String ACTION_CLUSTER_CLOSE = "action:cluster_close";
    public static final String ACTION_CLUSTER_TOGGLE = "action:cluster_toggle";
    private static final String ACTION_TAB_PREFIX = "action:tab:";

    private static final String PREFS_NAME = "MapControlPrefs";
    private static final String[] KEYS = {
            "launcher_swipe_up",
            "launcher_swipe_down",
            "launcher_swipe_left",
            "launcher_swipe_right"
    };

    private LauncherSwipeStore() {
    }

    @NonNull
    public static String getPackage(@NonNull Context context, int direction) {
        String key = keyFor(direction);
        if (key == null) {
            return "";
        }
        String value = prefs(context).getString(key, "");
        return value != null ? value.trim() : "";
    }

    public static void setPackage(@NonNull Context context, int direction, @Nullable String packageName) {
        String key = keyFor(direction);
        if (key == null) {
            return;
        }
        prefs(context).edit()
                .putString(key, packageName != null ? packageName.trim() : "")
                .apply();
    }

    public static void clear(@NonNull Context context, int direction) {
        setPackage(context, direction, "");
    }

    public static boolean isClusterAction(@Nullable String value) {
        return ACTION_CLUSTER_OPEN.equals(value)
                || ACTION_CLUSTER_CLOSE.equals(value)
                || ACTION_CLUSTER_TOGGLE.equals(value);
    }

    @NonNull
    public static String tabAction(int tabIndex) {
        return ACTION_TAB_PREFIX + tabIndex;
    }

    public static int tabIndex(@Nullable String value) {
        if (value == null || !value.startsWith(ACTION_TAB_PREFIX)) {
            return -1;
        }
        try {
            return Integer.parseInt(value.substring(ACTION_TAB_PREFIX.length()));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    @Nullable
    private static String keyFor(int direction) {
        if (direction < 0 || direction >= KEYS.length) {
            return null;
        }
        return KEYS[direction];
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
