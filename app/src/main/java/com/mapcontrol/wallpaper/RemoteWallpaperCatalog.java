package com.mapcontrol.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Sunucudaki sabit duvar kağıdı katalogunu ({@link #CATALOG_URL}) ayrıştırma,
 * doğrulama ve yerel {@code SVWallpaper} dosya eşleme yardımcısı.
 */
public final class RemoteWallpaperCatalog {

    // Sunucuya catalog.json yuklendiginde buraya tam HTTPS adresi yazilir (orn. "https://vnoisy.dev/wallpaper/catalog.json").
    public static final String CATALOG_URL = "";
    public static final String CACHE_FILENAME = "wallpaper_catalog_cache.json";
    public static final String REMOTE_FILE_PREFIX = "remote_";

    public static final class RemoteItem {
        @NonNull
        public final String id;
        @NonNull
        public final String title;
        @NonNull
        public final String subtitle;
        @NonNull
        public final String resolution;
        public final long sizeBytes;
        @Nullable
        public final String thumbUrl;
        @NonNull
        public final String imageUrl;

        public RemoteItem(
                @NonNull String id,
                @NonNull String title,
                @NonNull String subtitle,
                @NonNull String resolution,
                long sizeBytes,
                @Nullable String thumbUrl,
                @NonNull String imageUrl) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.resolution = resolution;
            this.sizeBytes = sizeBytes;
            this.thumbUrl = thumbUrl;
            this.imageUrl = imageUrl;
        }

        @NonNull
        public String formatSize() {
            if (sizeBytes >= 1024L * 1024L) {
                return String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
            }
            if (sizeBytes >= 1024L) {
                return Math.max(1L, sizeBytes / 1024L) + " KB";
            }
            return sizeBytes > 0 ? (sizeBytes + " B") : "4K";
        }
    }

    public static final class CatalogParseResult {
        public final int version;
        @NonNull
        public final String updatedAt;
        @NonNull
        public final List<RemoteItem> items;

        public CatalogParseResult(int version, @NonNull String updatedAt, @NonNull List<RemoteItem> items) {
            this.version = version;
            this.updatedAt = updatedAt;
            this.items = items;
        }
    }

    private RemoteWallpaperCatalog() {
    }

    @NonNull
    public static CatalogParseResult parseCatalogJson(@Nullable String rawJson) throws JSONException {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return new CatalogParseResult(1, "", Collections.emptyList());
        }
        JSONObject root = new JSONObject(rawJson);
        int version = root.optInt("version", 1);
        String updatedAt = root.optString("updatedAt", "").trim();
        JSONArray array = root.optJSONArray("items");
        if (array == null) {
            return new CatalogParseResult(version, updatedAt, Collections.emptyList());
        }

        List<RemoteItem> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            String safeId = sanitizeId(obj.optString("id", ""));
            String imageUrl = obj.optString("imageUrl", "").trim();
            if (safeId.isEmpty() || !isValidHttpUrl(imageUrl)) {
                continue;
            }
            String title = obj.optString("title", safeId).trim();
            if (title.isEmpty()) {
                title = safeId;
            }
            String subtitle = obj.optString("subtitle", "").trim();
            String resolution = obj.optString("resolution", "4K").trim();
            if (resolution.isEmpty()) {
                resolution = "4K";
            }
            long sizeBytes = Math.max(0L, obj.optLong("sizeBytes", 0L));
            String thumbRaw = obj.optString("thumbUrl", "").trim();
            String thumbUrl = isValidHttpUrl(thumbRaw) ? thumbRaw : null;

            list.add(new RemoteItem(safeId, title, subtitle, resolution, sizeBytes, thumbUrl, imageUrl));
        }
        return new CatalogParseResult(version, updatedAt, Collections.unmodifiableList(list));
    }

    public static boolean isValidHttpUrl(@Nullable String url) {
        if (url == null) {
            return false;
        }
        String lower = url.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
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
    public static String localBaseNameForRemoteId(@NonNull String id) {
        String safe = sanitizeId(id);
        return REMOTE_FILE_PREFIX + (safe.isEmpty() ? "item" : safe);
    }

    @Nullable
    public static File findDownloadedFile(@NonNull File svWallpaperDir, @NonNull String id) {
        String base = localBaseNameForRemoteId(id);
        String[] exts = {".jpg", ".png", ".webp", ".jpeg"};
        for (String ext : exts) {
            File candidate = new File(svWallpaperDir, base + ext);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }
}
