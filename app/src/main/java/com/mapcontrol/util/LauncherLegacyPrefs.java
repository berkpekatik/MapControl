package com.mapcontrol.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Kaldırılan launcher özelliklerinden kalan SharedPreferences anahtarlarını temizler.
 */
public final class LauncherLegacyPrefs {

    private static final String PREFS_NAME = "MapControlPrefs";
    /** Eski 3D/Lite seçici ({@code LauncherDisplayModeStore}); artık yalnızca saat paneli. */
    private static final String KEY_DISPLAY_MODE = "launcher_display_mode";

    private LauncherLegacyPrefs() {
    }

    public static void applyMigrations(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_DISPLAY_MODE)) {
            return;
        }
        prefs.edit().remove(KEY_DISPLAY_MODE).apply();
    }
}
