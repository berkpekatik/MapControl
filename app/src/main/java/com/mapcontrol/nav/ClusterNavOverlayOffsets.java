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
 * Cluster navigasyon kartları: kart bazlı konum (dp) ve boyut (%).
 * Konum aralığı cluster ekran boyutuna göre hesaplanır.
 */
public final class ClusterNavOverlayOffsets {

    public static final String PREFS = "MapControlPrefs";
    private static final int OFFSET_RANGE_FALLBACK_DP = 80;
    public static final int SCALE_MIN_PERCENT = 50;
    public static final int SCALE_MAX_PERCENT = 150;
    public static final int SCALE_DEFAULT_PERCENT = 100;

    /** @deprecated Eski ortak ofset; yeni kurulumda kullanılmaz. */
    private static final String LEGACY_GOOGLE_OFFSET_X_DP = "google_maps_cluster_nav_overlay_offset_x_dp";
    private static final String LEGACY_GOOGLE_OFFSET_Y_DP = "google_maps_cluster_nav_overlay_offset_y_dp";
    private static final String LEGACY_YANDEX_OFFSET_X_DP = "yandex_cluster_nav_overlay_offset_x_dp";
    private static final String LEGACY_YANDEX_OFFSET_Y_DP = "yandex_cluster_nav_overlay_offset_y_dp";

    private static final String GOOGLE_MANEUVER_X = "google_cluster_nav_maneuver_offset_x_dp";
    private static final String GOOGLE_MANEUVER_Y = "google_cluster_nav_maneuver_offset_y_dp";
    private static final String GOOGLE_MANEUVER_SCALE = "google_cluster_nav_maneuver_scale_pct";
    private static final String GOOGLE_ETA_X = "google_cluster_nav_eta_offset_x_dp";
    private static final String GOOGLE_ETA_Y = "google_cluster_nav_eta_offset_y_dp";
    private static final String GOOGLE_ETA_SCALE = "google_cluster_nav_eta_scale_pct";

    private static final String YANDEX_MANEUVER_X = "yandex_cluster_nav_maneuver_offset_x_dp";
    private static final String YANDEX_MANEUVER_Y = "yandex_cluster_nav_maneuver_offset_y_dp";
    private static final String YANDEX_MANEUVER_SCALE = "yandex_cluster_nav_maneuver_scale_pct";
    private static final String YANDEX_ETA_X = "yandex_cluster_nav_eta_offset_x_dp";
    private static final String YANDEX_ETA_Y = "yandex_cluster_nav_eta_offset_y_dp";
    private static final String YANDEX_ETA_SCALE = "yandex_cluster_nav_eta_scale_pct";

    private static final String PETAL_MANEUVER_X = "petal_cluster_nav_maneuver_offset_x_dp";
    private static final String PETAL_MANEUVER_Y = "petal_cluster_nav_maneuver_offset_y_dp";
    private static final String PETAL_MANEUVER_SCALE = "petal_cluster_nav_maneuver_scale_pct";
    private static final String PETAL_ETA_X = "petal_cluster_nav_eta_offset_x_dp";
    private static final String PETAL_ETA_Y = "petal_cluster_nav_eta_offset_y_dp";
    private static final String PETAL_ETA_SCALE = "petal_cluster_nav_eta_scale_pct";

    public enum Provider {
        GOOGLE,
        YANDEX,
        PETAL
    }

    public enum Card {
        MANEUVER,
        ETA
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

    /** Yatay slider: ± (cluster genişliği / 2) dp. */
    public static int getOffsetRangeXDp(Context context) {
        DisplayMetrics metrics = getClusterDisplayMetrics(context);
        int widthDp = Math.round(metrics.widthPixels / metrics.density);
        return Math.max(widthDp / 2, OFFSET_RANGE_FALLBACK_DP);
    }

    /** Dikey slider: ± (cluster yüksekliği / 2) dp. */
    public static int getOffsetRangeYDp(Context context) {
        DisplayMetrics metrics = getClusterDisplayMetrics(context);
        int heightDp = Math.round(metrics.heightPixels / metrics.density);
        return Math.max(heightDp / 2, OFFSET_RANGE_FALLBACK_DP);
    }

    public static int clampOffsetX(Context context, int offsetDp) {
        return clampOffset(offsetDp, getOffsetRangeXDp(context));
    }

    public static int clampOffsetY(Context context, int offsetDp) {
        return clampOffset(offsetDp, getOffsetRangeYDp(context));
    }

    public static int clampOffset(int offsetDp, int rangeDp) {
        return Math.max(-rangeDp, Math.min(rangeDp, offsetDp));
    }

    public static int clampScale(int scalePercent) {
        return Math.max(SCALE_MIN_PERCENT, Math.min(SCALE_MAX_PERCENT, scalePercent));
    }

    public static int offsetSeekMax(int rangeDp) {
        return rangeDp * 2;
    }

    public static int toOffsetSeekProgress(int offsetDp, int rangeDp) {
        return clampOffset(offsetDp, rangeDp) + rangeDp;
    }

    public static int fromOffsetSeekProgress(int progress, int rangeDp) {
        return clampOffset(progress - rangeDp, rangeDp);
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
        switch (provider) {
            case GOOGLE:
                if (card == Card.MANEUVER) {
                    return readCardLayout(context, prefs, GOOGLE_MANEUVER_X, GOOGLE_MANEUVER_Y,
                            GOOGLE_MANEUVER_SCALE, LEGACY_GOOGLE_OFFSET_X_DP, LEGACY_GOOGLE_OFFSET_Y_DP);
                }
                return readCardLayout(context, prefs, GOOGLE_ETA_X, GOOGLE_ETA_Y, GOOGLE_ETA_SCALE,
                        LEGACY_GOOGLE_OFFSET_X_DP, LEGACY_GOOGLE_OFFSET_Y_DP);
            case PETAL:
                if (card == Card.MANEUVER) {
                    return readCardLayout(context, prefs, PETAL_MANEUVER_X, PETAL_MANEUVER_Y,
                            PETAL_MANEUVER_SCALE, PETAL_MANEUVER_X, PETAL_MANEUVER_Y);
                }
                return readCardLayout(context, prefs, PETAL_ETA_X, PETAL_ETA_Y, PETAL_ETA_SCALE,
                        PETAL_ETA_X, PETAL_ETA_Y);
            case YANDEX:
            default:
                if (card == Card.MANEUVER) {
                    return readCardLayout(context, prefs, YANDEX_MANEUVER_X, YANDEX_MANEUVER_Y,
                            YANDEX_MANEUVER_SCALE, LEGACY_YANDEX_OFFSET_X_DP, LEGACY_YANDEX_OFFSET_Y_DP);
                }
                return readCardLayout(context, prefs, YANDEX_ETA_X, YANDEX_ETA_Y, YANDEX_ETA_SCALE,
                        LEGACY_YANDEX_OFFSET_X_DP, LEGACY_YANDEX_OFFSET_Y_DP);
        }
    }

    public static void setCardLayout(Context context, Provider provider, Card card, CardLayout layout) {
        if (layout == null) {
            return;
        }
        String xKey;
        String yKey;
        String scaleKey;
        switch (provider) {
            case GOOGLE:
                if (card == Card.MANEUVER) {
                    xKey = GOOGLE_MANEUVER_X;
                    yKey = GOOGLE_MANEUVER_Y;
                    scaleKey = GOOGLE_MANEUVER_SCALE;
                } else {
                    xKey = GOOGLE_ETA_X;
                    yKey = GOOGLE_ETA_Y;
                    scaleKey = GOOGLE_ETA_SCALE;
                }
                break;
            case PETAL:
                if (card == Card.MANEUVER) {
                    xKey = PETAL_MANEUVER_X;
                    yKey = PETAL_MANEUVER_Y;
                    scaleKey = PETAL_MANEUVER_SCALE;
                } else {
                    xKey = PETAL_ETA_X;
                    yKey = PETAL_ETA_Y;
                    scaleKey = PETAL_ETA_SCALE;
                }
                break;
            case YANDEX:
            default:
                if (card == Card.MANEUVER) {
                    xKey = YANDEX_MANEUVER_X;
                    yKey = YANDEX_MANEUVER_Y;
                    scaleKey = YANDEX_MANEUVER_SCALE;
                } else {
                    xKey = YANDEX_ETA_X;
                    yKey = YANDEX_ETA_Y;
                    scaleKey = YANDEX_ETA_SCALE;
                }
                break;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(xKey, clampOffsetX(context, layout.offsetXDp))
                .putInt(yKey, clampOffsetY(context, layout.offsetYDp))
                .putInt(scaleKey, clampScale(layout.scalePercent))
                .apply();
    }

    /** Manevra + ETA kartlarını varsayılan konum ve %100 boyuta döndürür. */
    public static void resetProvider(Context context, Provider provider) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        switch (provider) {
            case GOOGLE:
                putDefaults(editor, GOOGLE_MANEUVER_X, GOOGLE_MANEUVER_Y, GOOGLE_MANEUVER_SCALE);
                putDefaults(editor, GOOGLE_ETA_X, GOOGLE_ETA_Y, GOOGLE_ETA_SCALE);
                editor.remove(LEGACY_GOOGLE_OFFSET_X_DP);
                editor.remove(LEGACY_GOOGLE_OFFSET_Y_DP);
                break;
            case PETAL:
                putDefaults(editor, PETAL_MANEUVER_X, PETAL_MANEUVER_Y, PETAL_MANEUVER_SCALE);
                putDefaults(editor, PETAL_ETA_X, PETAL_ETA_Y, PETAL_ETA_SCALE);
                break;
            case YANDEX:
            default:
                putDefaults(editor, YANDEX_MANEUVER_X, YANDEX_MANEUVER_Y, YANDEX_MANEUVER_SCALE);
                putDefaults(editor, YANDEX_ETA_X, YANDEX_ETA_Y, YANDEX_ETA_SCALE);
                editor.remove(LEGACY_YANDEX_OFFSET_X_DP);
                editor.remove(LEGACY_YANDEX_OFFSET_Y_DP);
                break;
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

    private static CardLayout readCardLayout(Context context, SharedPreferences prefs,
            String xKey, String yKey, String scaleKey,
            String legacyXKey, String legacyYKey) {
        int defaultX = 0;
        int defaultY = 0;
        if (!prefs.contains(xKey) && prefs.contains(legacyXKey)) {
            defaultX = clampOffsetX(context, prefs.getInt(legacyXKey, 0));
        }
        if (!prefs.contains(yKey) && prefs.contains(legacyYKey)) {
            defaultY = clampOffsetY(context, prefs.getInt(legacyYKey, 0));
        }
        int x = clampOffsetX(context, prefs.getInt(xKey, defaultX));
        int y = clampOffsetY(context, prefs.getInt(yKey, defaultY));
        int scale = clampScale(prefs.getInt(scaleKey, SCALE_DEFAULT_PERCENT));
        return new CardLayout(x, y, scale);
    }

    private static DisplayMetrics getClusterDisplayMetrics(Context context) {
        Display cluster = findClusterDisplay(context);
        if (cluster != null) {
            DisplayMetrics metrics = new DisplayMetrics();
            cluster.getRealMetrics(metrics);
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
