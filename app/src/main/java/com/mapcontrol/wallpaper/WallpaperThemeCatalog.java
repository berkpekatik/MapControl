package com.mapcontrol.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Statik duvar kağıdı tercih anahtarları ve {@code SVWallpaper} klasör öğe modeli.
 */
public final class WallpaperThemeCatalog {

    public static final String PREFS_NAME = "mapcontrol_wallpaper_prefs";
    public static final String PREF_SELECTED_PATH = "selected_wallpaper_path";
    public static final String PREF_APPLIED_PATH = "applied_wallpaper_path";


    public static final class WallpaperItem {
        @NonNull
        public final String filePath;
        @NonNull
        public final String fileName;
        public final int width;
        public final int height;
        public final long sizeBytes;
        public final long lastModifiedMs;

        public WallpaperItem(
                @NonNull String filePath,
                @NonNull String fileName,
                int width,
                int height,
                long sizeBytes,
                long lastModifiedMs) {
            this.filePath = filePath;
            this.fileName = fileName;
            this.width = width;
            this.height = height;
            this.sizeBytes = sizeBytes;
            this.lastModifiedMs = lastModifiedMs;
        }

        @NonNull
        public String formatResolution() {
            if (width <= 0 || height <= 0) {
                return "—";
            }
            return width + " × " + height;
        }

        @NonNull
        public String formatSize() {
            if (sizeBytes >= 1024L * 1024L) {
                return String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
            }
            if (sizeBytes >= 1024L) {
                return Math.max(1L, sizeBytes / 1024L) + " KB";
            }
            return Math.max(0L, sizeBytes) + " B";
        }
    }

    private WallpaperThemeCatalog() {
    }

    /**
     * Duvar kağıdı menüsü bu ekranda gösterilir mi: Tiggo 7 (1920×720, birleşik ekran) SVLauncher'ı sabit, opak bir
     * arka plan çizer ve dışarıdan görsel kabul etmez; menü yalnızca orada gizlenir. Elle profil seçimi değil, fiziksel
     * ekran ölçüsü belirleyicidir.
     */
    public static boolean isSupportedScreen(@NonNull Context context) {
        return com.mapcontrol.launcher.VehicleProfile.Companion.detectFromDisplay(context)
                != com.mapcontrol.launcher.VehicleProfile.TIGGO7;
    }

    @NonNull
    public static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
