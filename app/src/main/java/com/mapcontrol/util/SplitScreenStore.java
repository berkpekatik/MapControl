package com.mapcontrol.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * Bölünmüş ekran seçimleri: iki uygulama, oran ve hızlı başlatma hareketi.
 * Ayrı dosya kullanır; mevcut tercihlere dokunmaz.
 */
public final class SplitScreenStore {

    public static final String PREFS_NAME = "mapcontrol_split_screen";

    private static final String KEY_PKG1 = "split_pkg1";
    private static final String KEY_PKG2 = "split_pkg2";
    private static final String KEY_RATIO = "split_ratio";
    private static final String KEY_GESTURE = "split_gesture_code";

    /** Hareket kapalı. */
    public static final int GESTURE_OFF = 0;

    private SplitScreenStore() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Nullable
    public static String getPkg1(Context context) {
        return emptyToNull(prefs(context).getString(KEY_PKG1, null));
    }

    @Nullable
    public static String getPkg2(Context context) {
        return emptyToNull(prefs(context).getString(KEY_PKG2, null));
    }

    public static void setPkg1(Context context, @Nullable String pkg) {
        prefs(context).edit().putString(KEY_PKG1, pkg == null ? "" : pkg).apply();
    }

    public static void setPkg2(Context context, @Nullable String pkg) {
        prefs(context).edit().putString(KEY_PKG2, pkg == null ? "" : pkg).apply();
    }

    public static SplitScreenLauncher.Ratio getRatio(Context context) {
        return SplitScreenLauncher.Ratio.fromId(prefs(context).getString(KEY_RATIO, null));
    }

    public static void setRatio(Context context, SplitScreenLauncher.Ratio ratio) {
        prefs(context).edit().putString(KEY_RATIO, ratio.id).apply();
    }

    public static int getGestureCode(Context context) {
        return prefs(context).getInt(KEY_GESTURE, GESTURE_OFF);
    }

    public static void setGestureCode(Context context, int code) {
        prefs(context).edit().putInt(KEY_GESTURE, code).apply();
    }

    public static boolean hasPair(Context context) {
        String a = getPkg1(context);
        String b = getPkg2(context);
        return a != null && b != null && !a.equals(b);
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
