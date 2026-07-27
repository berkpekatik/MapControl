package com.mapcontrol.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Launcher orta kartı görünüm modu: 3D araç modeli veya Lite (yalnızca saat).
 */
public final class LauncherDisplayModeStore {

    public static final String MODE_3D = "3d";
    public static final String MODE_LITE = "lite";

    private static final String PREFS_NAME = "MapControlPrefs";
    private static final String KEY_DISPLAY_MODE = "launcher_display_mode";

    private LauncherDisplayModeStore() {
    }

    public static boolean isLite(@NonNull Context context) {
        return MODE_LITE.equals(getMode(context));
    }

    public static boolean is3d(@NonNull Context context) {
        return !isLite(context);
    }

    @NonNull
    public static String getMode(@NonNull Context context) {
        String stored = prefs(context).getString(KEY_DISPLAY_MODE, MODE_3D);
        return MODE_LITE.equals(stored) ? MODE_LITE : MODE_3D;
    }

    public static void setMode(@NonNull Context context, @NonNull String mode) {
        prefs(context).edit()
                .putString(KEY_DISPLAY_MODE, MODE_LITE.equals(mode) ? MODE_LITE : MODE_3D)
                .apply();
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
