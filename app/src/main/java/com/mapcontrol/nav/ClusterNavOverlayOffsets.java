package com.mapcontrol.nav;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;

import androidx.annotation.Nullable;

import com.mapcontrol.util.AppLaunchHelper;

/**
 * Cluster navigasyon kartı: konum (dp) ve boyut (%).
 * Konum aralığı cluster ekran boyutuna göre hesaplanır.
 */
public final class ClusterNavOverlayOffsets {

    public static final String PREFS = "MapControlPrefs";
        public static final int SCALE_MIN_PERCENT = 50;
    public static final int SCALE_MAX_PERCENT = 150;
    public static final int SCALE_DEFAULT_PERCENT = 100;

    /** @deprecated Eski ortak ofset; yeni kurulumda kullanılmaz. */
    private static final String LEGACY_GOOGLE_OFFSET_X_DP = "google_maps_cluster_nav_overlay_offset_x_dp";
    private static final String LEGACY_GOOGLE_OFFSET_Y_DP = "google_maps_cluster_nav_overlay_offset_y_dp";
    private static final String LEGACY_YANDEX_OFFSET_X_DP = "yandex_cluster_nav_overlay_offset_x_dp";
    private static final String LEGACY_YANDEX_OFFSET_Y_DP = "yandex_cluster_nav_overlay_offset_y_dp";

    private static final String GOOGLE_CARD_X = "google_cluster_nav_card_offset_x_dp";
    private static final String GOOGLE_CARD_Y = "google_cluster_nav_card_offset_y_dp";
    private static final String GOOGLE_CARD_SCALE = "google_cluster_nav_card_scale_pct";

    private static final String YANDEX_CARD_X = "yandex_cluster_nav_card_offset_x_dp";
    private static final String YANDEX_CARD_Y = "yandex_cluster_nav_card_offset_y_dp";
    private static final String YANDEX_CARD_SCALE = "yandex_cluster_nav_card_scale_pct";

    public enum Provider {
        GOOGLE,
        YANDEX
    }

    /** Kart görünümü: ayrıntılı kart veya ince çizgi. */
    public enum Style {
        DETAIL,
        SLIM
    }

    private static final String GOOGLE_STYLE = "google_cluster_nav_card_style";
    private static final String YANDEX_STYLE = "yandex_cluster_nav_card_style";

    public static Style getStyle(Context context, Provider provider) {
        String v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(provider == Provider.GOOGLE ? GOOGLE_STYLE : YANDEX_STYLE,
                        Style.DETAIL.name());
        try {
            return Style.valueOf(v);
        } catch (IllegalArgumentException e) {
            return Style.DETAIL;
        }
    }

    public static void setStyle(Context context, Provider provider, Style style) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(provider == Provider.GOOGLE ? GOOGLE_STYLE : YANDEX_STYLE, style.name())
                .apply();
    }

    /** Tek birleşik kart (manevra + ETA). */
    public enum Card {
        NAV
    }

    public static final class CardLayout {
        public int offsetXDp;
        public int offsetYDp;
        public int scalePercent;

        public CardLayout(int offsetXDp, int offsetYDp, int scalePercent) {
            this.offsetXDp = offsetXDp;
            this.offsetYDp = offsetYDp;
            this.scalePercent = scalePercent;
        }
    }

    private ClusterNavOverlayOffsets() {
    }

    public static CardLayout defaultCardLayout() {
        return new CardLayout(0, 0, SCALE_DEFAULT_PERCENT);
    }

    /** Kartın ekran içinde kalacağı X/Y kaydırma aralığı (dp, varsayılan konuma göre). */
    public static final class Bounds {
        public final int minX;
        public final int maxX;
        public final int minY;
        public final int maxY;

        Bounds(int minX, int maxX, int minY, int maxY) {
            this.minX = minX;
            this.maxX = Math.max(minX, maxX);
            this.minY = minY;
            this.maxY = Math.max(minY, maxY);
        }
    }

    /**
     * Kart yatayda ortalı, dikeyde üstten sabit boşlukla başlar; sol üst köşe etrafında ölçeklenir.
     * Sınırlar kartı cluster ekranının dışına taşmayacak şekilde hesaplanır.
     */
    public static Bounds getBounds(Context context, Provider provider, int scalePercent) {
        Style style = getStyle(context, provider);
        DisplayMetrics m = getClusterDisplayMetrics(context);
        float screenW = m.widthPixels / m.density;
        float screenH = m.heightPixels / m.density;
        float scale = clampScale(scalePercent) / 100f;
        float cardW = ClusterNavCardStyle.widthDp(style);
        float cardH = ClusterNavCardStyle.heightDp(style);
        float top = ClusterNavCardStyle.topMarginDp(provider);
        int minX = -Math.round((screenW - cardW) / 2f);
        int maxX = Math.round((screenW + cardW) / 2f - cardW * scale);
        int minY = -Math.round(top);
        int maxY = Math.round(screenH - top - cardH * scale);
        return new Bounds(minX, maxX, minY, maxY);
    }

    public static int clampOffset(int offsetDp, int min, int max) {
        return Math.max(min, Math.min(max, offsetDp));
    }

    public static int clampScale(int scalePercent) {
        return Math.max(SCALE_MIN_PERCENT, Math.min(SCALE_MAX_PERCENT, scalePercent));
    }

    public static int offsetSeekMax(int min, int max) {
        return max - min;
    }

    public static int toOffsetSeekProgress(int offsetDp, int min, int max) {
        return clampOffset(offsetDp, min, max) - min;
    }

    public static int fromOffsetSeekProgress(int progress, int min, int max) {
        return clampOffset(progress + min, min, max);
    }

    public static int scaleSeekMax() {
        return SCALE_MAX_PERCENT - SCALE_MIN_PERCENT;
    }

    public static int toScaleSeekProgress(int scalePercent) {
        return clampScale(scalePercent) - SCALE_MIN_PERCENT;
    }

    public static int fromScaleSeekProgress(int progress) {
        return clampScale(progress + SCALE_MIN_PERCENT);
    }

    public static CardLayout getCardLayout(Context context, Provider provider, Card card) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (provider == Provider.GOOGLE) {
            return readCardLayout(context, provider, prefs, GOOGLE_CARD_X, GOOGLE_CARD_Y,
                    GOOGLE_CARD_SCALE, LEGACY_GOOGLE_OFFSET_X_DP, LEGACY_GOOGLE_OFFSET_Y_DP);
        }
        return readCardLayout(context, provider, prefs, YANDEX_CARD_X, YANDEX_CARD_Y,
                YANDEX_CARD_SCALE, LEGACY_YANDEX_OFFSET_X_DP, LEGACY_YANDEX_OFFSET_Y_DP);
    }

    public static void setCardLayout(Context context, Provider provider, Card card, CardLayout layout) {
        if (layout == null) {
            return;
        }
        boolean google = provider == Provider.GOOGLE;
        int scale = clampScale(layout.scalePercent);
        Bounds b = getBounds(context, provider, scale);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(google ? GOOGLE_CARD_X : YANDEX_CARD_X,
                        clampOffset(layout.offsetXDp, b.minX, b.maxX))
                .putInt(google ? GOOGLE_CARD_Y : YANDEX_CARD_Y,
                        clampOffset(layout.offsetYDp, b.minY, b.maxY))
                .putInt(google ? GOOGLE_CARD_SCALE : YANDEX_CARD_SCALE, scale)
                .apply();
    }

    /** Kartı varsayılan konum ve %100 boyuta döndürür. */
    public static void resetProvider(Context context, Provider provider) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (provider == Provider.GOOGLE) {
            putDefaults(editor, GOOGLE_CARD_X, GOOGLE_CARD_Y, GOOGLE_CARD_SCALE);
            editor.remove(LEGACY_GOOGLE_OFFSET_X_DP);
            editor.remove(LEGACY_GOOGLE_OFFSET_Y_DP);
        } else {
            putDefaults(editor, YANDEX_CARD_X, YANDEX_CARD_Y, YANDEX_CARD_SCALE);
            editor.remove(LEGACY_YANDEX_OFFSET_X_DP);
            editor.remove(LEGACY_YANDEX_OFFSET_Y_DP);
        }
        editor.apply();
    }

    private static void putDefaults(SharedPreferences.Editor editor, String xKey, String yKey, String scaleKey) {
        editor.putInt(xKey, 0);
        editor.putInt(yKey, 0);
        editor.putInt(scaleKey, SCALE_DEFAULT_PERCENT);
    }

    public static void applyTranslationAndScale(View card, Context measureContext,
            int offsetXDp, int offsetYDp, int scalePercent) {
        if (card == null) {
            return;
        }
        float density = measureContext.getResources().getDisplayMetrics().density;
        float scale = clampScale(scalePercent) / 100f;
        card.setPivotX(0f);
        card.setPivotY(0f);
        card.setScaleX(scale);
        card.setScaleY(scale);
        card.setTranslationX(Math.round(offsetXDp * density));
        card.setTranslationY(Math.round(offsetYDp * density));
    }

    private static CardLayout readCardLayout(Context context, Provider provider,
            SharedPreferences prefs, String xKey, String yKey, String scaleKey,
            String legacyXKey, String legacyYKey) {
        int scale = clampScale(prefs.getInt(scaleKey, SCALE_DEFAULT_PERCENT));
        Bounds b = getBounds(context, provider, scale);
        int defaultX = 0;
        int defaultY = 0;
        if (!prefs.contains(xKey) && prefs.contains(legacyXKey)) {
            defaultX = prefs.getInt(legacyXKey, 0);
        }
        if (!prefs.contains(yKey) && prefs.contains(legacyYKey)) {
            defaultY = prefs.getInt(legacyYKey, 0);
        }
        int x = clampOffset(prefs.getInt(xKey, defaultX), b.minX, b.maxX);
        int y = clampOffset(prefs.getInt(yKey, defaultY), b.minY, b.maxY);
        return new CardLayout(x, y, scale);
    }

    private static DisplayMetrics getClusterDisplayMetrics(Context context) {
        Display cluster = findClusterDisplay(context);
        if (cluster != null) {
            // getMetrics: sistem çubuklarını (gezinme çubuğu vb.) düşer; overlay bu alana çizilir.
            DisplayMetrics metrics = new DisplayMetrics();
            cluster.getMetrics(metrics);
            return metrics;
        }
        return context.getResources().getDisplayMetrics();
    }

    @Nullable
    private static Display findClusterDisplay(Context context) {
        try {
            int displayId = AppLaunchHelper.getClusterDisplayId(context);
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (dm == null) {
                return null;
            }
            for (Display d : dm.getDisplays()) {
                if (d.getDisplayId() == displayId) {
                    return d;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
