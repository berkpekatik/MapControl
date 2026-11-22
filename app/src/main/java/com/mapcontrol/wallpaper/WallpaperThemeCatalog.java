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
 * Statik duvar kağıdı tercih anahtarları, APK içi hazır paket listesi ve {@code SVWallpaper} klasör öğe modeli.
 */
public final class WallpaperThemeCatalog {

    public static final String PREFS_NAME = "mapcontrol_wallpaper_prefs";
    public static final String PREF_SELECTED_PATH = "selected_wallpaper_path";
    public static final String PREF_APPLIED_PATH = "applied_wallpaper_path";
    public static final String PRESET_FILE_PREFIX = "preset_";

    public static final class BundledItem {
        @NonNull
        public final String id;
        @NonNull
        public final String title;
        @NonNull
        public final String assetPath;
        @NonNull
        public final String resolution;

        public BundledItem(
                @NonNull String id,
                @NonNull String title,
                @NonNull String assetPath,
                @NonNull String resolution) {
            this.id = id;
            this.title = title;
            this.assetPath = assetPath;
            this.resolution = resolution;
        }
    }

    private static final List<BundledItem> BUNDLED_ITEMS = Collections.unmodifiableList(Arrays.asList(
            new BundledItem("mockup_1", "Mockup 1", "wallpapers/mockup_1.jpg", "1920 × 1080"),
            new BundledItem("mockup_2", "Mockup 2", "wallpapers/mockup_2.jpg", "1920 × 1080"),
            new BundledItem("mockup_3", "Mockup 3", "wallpapers/mockup_3.jpg", "1920 × 1080"),
            new BundledItem("mockup_4", "Mockup 4", "wallpapers/mockup_4.jpg", "1920 × 1080"),
            new BundledItem("mockup_5", "Mockup 5", "wallpapers/mockup_5.jpg", "1920 × 1080")
    ));

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

    @NonNull
    public static List<BundledItem> bundledItems() {
        return BUNDLED_ITEMS;
    }

    @NonNull
    public static String sanitizeId(@Nullable String rawId) {
        if (rawId == null) {
            return "";
        }
        String trimmed = rawId.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if ((ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '_'
                    || ch == '-') {
                sb.append(ch);
            } else if (ch == ' ') {
                sb.append('_');
            }
            if (sb.length() >= 48) {
                break;
            }
        }
        String out = sb.toString();
        while (out.startsWith("_") || out.startsWith("-")) {
            out = out.substring(1);
        }
        return out;
    }

    @NonNull
    public static String localBaseNameForBundledId(@NonNull String id) {
        return PRESET_FILE_PREFIX + sanitizeId(id);
    }

    @Nullable
    public static File findInstalledBundledFile(@NonNull File svWallpaperDir, @NonNull String id) {
        String base = localBaseNameForBundledId(id);
        String[] exts = {".jpg", ".png", ".webp", ".jpeg"};
        for (String ext : exts) {
            File candidate = new File(svWallpaperDir, base + ext);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    @NonNull
    public static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
