package com.mapcontrol.vehicle;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.mapcontrol.util.SplitScreenGesture;
import com.mapcontrol.vehicle.SunroofGestureOverlayManager.GestureAction;

/**
 * Cam tavan ve perde işlemlerinin hangi kaydırma hareketine bağlı olduğunu tutar.
 *
 * <p>Hareket kodu {@link SplitScreenGesture} ile aynıdır: parmak sayısı * 100 + yön. Böylece
 * bölünmüş ekran ve harita kaydırma ile aynı kod uzayında çakışma denetlenebilir.
 */
public final class SunroofGestureStore {

    /** Hareket kapalı. */
    public static final int GESTURE_OFF = 0;

    private static final String KEY_PREFIX = "sunroof_gesture_code_";

    private SunroofGestureStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(SunroofAutomationEngine.PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String key(GestureAction action) {
        return KEY_PREFIX + action.name();
    }

    /** Daha önce sabit olan iki parmak hareketleri varsayılan kalır. */
    public static int defaultCode(GestureAction action) {
        switch (action) {
            case SWIPE_LEFT:
                return SplitScreenGesture.code(2, SplitScreenGesture.LEFT);
            case SWIPE_RIGHT:
                return SplitScreenGesture.code(2, SplitScreenGesture.RIGHT);
            case SWIPE_UP:
                return SplitScreenGesture.code(2, SplitScreenGesture.UP);
            case SWIPE_DOWN:
            default:
                return SplitScreenGesture.code(2, SplitScreenGesture.DOWN);
        }
    }

    public static int getCode(Context context, GestureAction action) {
        return prefs(context).getInt(key(action), defaultCode(action));
    }

    public static void setCode(Context context, GestureAction action, int code) {
        prefs(context).edit().putInt(key(action), code).apply();
    }

    /** Verilen kaydırmaya bağlı işlem; yoksa {@code null}. */
    @Nullable
    public static GestureAction actionFor(Context context, int code) {
        if (!SplitScreenGesture.isValid(code)) {
            return null;
        }
        for (GestureAction action : GestureAction.values()) {
            if (getCode(context, action) == code) {
                return action;
            }
        }
        return null;
    }
}
