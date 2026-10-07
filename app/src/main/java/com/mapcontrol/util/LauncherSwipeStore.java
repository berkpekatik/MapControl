package com.mapcontrol.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Launcher orta saat kartındaki dört yönlü kaydırmanın açacağı paket adları.
 */
public final class LauncherSwipeStore {

    public static final int UP = 0;
    public static final int DOWN = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;

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
