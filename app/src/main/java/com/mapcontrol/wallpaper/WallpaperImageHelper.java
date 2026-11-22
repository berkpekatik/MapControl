package com.mapcontrol.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Pure image sizing, aspect-ratio center-crop, and filename helpers for multi-resolution
 * head-unit wallpaper adaptation (supports 4K+ sources and wide/standard head units).
 */
public final class WallpaperImageHelper {

    public static final int DEFAULT_SCREEN_WIDTH = 1920;
    public static final int DEFAULT_SCREEN_HEIGHT = 1080;
    public static final int MAX_SAFE_DECODE_DIMENSION = 4096;

    private WallpaperImageHelper() {
    }

    public static final class Size {
        public final int width;
        public final int height;

        public Size(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    public static final class CropRect {
        public final int left;
        public final int top;
        public final int width;
        public final int height;

        public CropRect(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }
    }

    @NonNull
    public static Size normalizeDisplaySize(int rawWidth, int rawHeight) {
        if (rawWidth <= 0 || rawHeight <= 0) {
            return new Size(DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_HEIGHT);
        }
        return new Size(rawWidth, rawHeight);
    }

    /**
     * Computes center-crop rectangle inside {@code srcWidth x srcHeight} matching the aspect ratio
     * of {@code targetWidth x targetHeight} with zero distortion.
     */
    @NonNull
    public static CropRect calculateCenterCropRect(
            int srcWidth, int srcHeight, int targetWidth, int targetHeight) {
        int safeSrcW = Math.max(1, srcWidth);
        int safeSrcH = Math.max(1, srcHeight);
        Size normTarget = normalizeDisplaySize(targetWidth, targetHeight);

        double srcAspect = (double) safeSrcW / safeSrcH;
        double targetAspect = (double) normTarget.width / normTarget.height;

        if (Math.abs(srcAspect - targetAspect) < 0.002d) {
            return new CropRect(0, 0, safeSrcW, safeSrcH);
        }

        if (srcAspect > targetAspect) {
            int cropW = Math.max(1, (int) Math.round(safeSrcH * targetAspect));
            cropW = Math.min(safeSrcW, cropW);
            int left = Math.max(0, (safeSrcW - cropW) / 2);
            return new CropRect(left, 0, cropW, safeSrcH);
        } else {
            int cropH = Math.max(1, (int) Math.round(safeSrcW / targetAspect));
            cropH = Math.min(safeSrcH, cropH);
            int top = Math.max(0, (safeSrcH - cropH) / 2);
            return new CropRect(0, top, safeSrcW, cropH);
        }
    }

    /**
     * Computes adapted output dimensions matching the vehicle display aspect ratio.
     * Downscales oversized images (e.g. 4K on a 1920x720 head unit) to exact display dimensions,
     * preserves 4K when the display itself is 4K, and avoids unnecessary upscaling for smaller inputs.
     */
    @NonNull
    public static Size calculateAdaptedOutputSize(
            int srcWidth, int srcHeight, int screenWidth, int screenHeight) {
        Size normScreen = normalizeDisplaySize(screenWidth, screenHeight);
        CropRect crop = calculateCenterCropRect(srcWidth, srcHeight, normScreen.width, normScreen.height);

        int maxOutW = Math.min(MAX_SAFE_DECODE_DIMENSION, normScreen.width);
        int maxOutH = Math.min(MAX_SAFE_DECODE_DIMENSION, normScreen.height);

        if (crop.width >= maxOutW && crop.height >= maxOutH) {
            return new Size(maxOutW, maxOutH);
        }

        double targetAspect = (double) normScreen.width / normScreen.height;
        int outW = Math.min(crop.width, maxOutW);
        int outH = Math.max(1, (int) Math.round(outW / targetAspect));
        if (outH > crop.height) {
            outH = Math.min(crop.height, maxOutH);
            outW = Math.max(1, (int) Math.round(outH * targetAspect));
        }
        return new Size(Math.max(1, outW), Math.max(1, outH));
    }

    /**
     * Power-of-two subsampling factor to safely decode 4K/8K bitmaps without exhausting heap.
     */
    public static int calculateInSampleSize(int srcWidth, int srcHeight, int reqWidth, int reqHeight) {
        int safeReqW = Math.max(1, reqWidth);
        int safeReqH = Math.max(1, reqHeight);
        int inSampleSize = 1;
        if (srcHeight > safeReqH || srcWidth > safeReqW) {
            int halfHeight = srcHeight / 2;
            int halfWidth = srcWidth / 2;
            while ((halfHeight / inSampleSize) >= safeReqH && (halfWidth / inSampleSize) >= safeReqW) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    public static boolean isSupportedImageFilename(@Nullable String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.startsWith(".")) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".tmp")) {
            return false;
        }
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp");
    }

    @NonNull
    public static String sanitizeImportFileName(
            @Nullable String rawName, long suffixStamp, boolean preferPng) {
        String base = rawName == null ? "" : rawName.trim();
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < base.length()) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < base.length(); i++) {
            char ch = base.charAt(i);
            if ((ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '_'
                    || ch == '-') {
                cleaned.append(ch);
            } else if (ch == ' ') {
                cleaned.append('_');
            }
            if (cleaned.length() >= 36) {
                break;
            }
        }
        String stem = cleaned.toString();
        while (stem.startsWith("_") || stem.startsWith("-")) {
            stem = stem.substring(1);
        }
        if (stem.isEmpty()) {
            stem = "wallpaper";
        }
        String ext = preferPng ? ".png" : ".jpg";
        return stem + "_" + Math.abs(suffixStamp % 100000L) + ext;
    }

    public static boolean isInsideDirectory(@NonNull File parentDir, @NonNull File candidate) {
        try {
            String parentPath = parentDir.getCanonicalPath();
            String childPath = candidate.getCanonicalPath();
            if (!parentPath.endsWith(File.separator)) {
                parentPath = parentPath + File.separator;
            }
            return childPath.startsWith(parentPath);
        } catch (IOException e) {
            return false;
        }
    }

    @NonNull
    public static String formatAspectLabel(int width, int height) {
        Size norm = normalizeDisplaySize(width, height);
        int g = gcd(norm.width, norm.height);
        int rw = norm.width / g;
        int rh = norm.height / g;
        if (rw > 32 || rh > 32) {
            double ratio = (double) norm.width / norm.height;
            return String.format(Locale.US, "%.2f:1", ratio);
        }
        return rw + ":" + rh;
    }

    private static int gcd(int a, int b) {
        int x = Math.abs(a);
        int y = Math.abs(b);
        while (y != 0) {
            int t = x % y;
            x = y;
            y = t;
        }
        return Math.max(1, x);
    }
}
