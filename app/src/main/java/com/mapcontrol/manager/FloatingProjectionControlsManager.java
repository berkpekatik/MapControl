package com.mapcontrol.manager;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.mapcontrol.R;
import com.mapcontrol.service.MapControlService;
import com.mapcontrol.ui.theme.UiStyles;

/**
 * Yüzen yansıtma kontrol çubuğu: Değiştir / Yansıt / Durdur.
 * {@link FloatingBackButtonManager} ile aynı overlay izni ve prefs dosyası; ayrı konum anahtarları.
 */
public class FloatingProjectionControlsManager {

    private static final String PREFS_NAME = "MapControlPrefs";
    private static final String KEY_ENABLED = "floatingProjectionControlsEnabled";
    private static final String KEY_POS_SAVED = "floatingProjectionBarPosSaved";
    private static final String KEY_POS_X = "floatingProjectionBarPosX";
    private static final String KEY_POS_Y = "floatingProjectionBarPosY";

    private static volatile FloatingProjectionControlsManager sInstance;

    private Context context;
    private WindowManager windowManager;
    private View barRoot;
    private WindowManager.LayoutParams params;
    private boolean isShowing = false;
    private android.content.ComponentCallbacks2 mThemeConfigCallback;

    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;
    private boolean dragging;
    private long lastDragLogUptimeMs;
    private static final int DRAG_LOG_MIN_INTERVAL_MS = 120;

    private int barWidthPx;
    private int barHeightPx;

    public interface LogCallback {
        void log(String message);
    }

    private LogCallback logCallback;

    private FloatingProjectionControlsManager(Context appContext) {
        this.context = appContext.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    public static FloatingProjectionControlsManager getInstance(Context anyContext) {
        if (sInstance == null) {
            synchronized (FloatingProjectionControlsManager.class) {
                if (sInstance == null) {
                    sInstance = new FloatingProjectionControlsManager(anyContext);
                }
            }
        }
        return sInstance;
    }

    public void setLogCallback(LogCallback callback) {
        this.logCallback = callback;
    }

    private void log(String message) {
        if (logCallback != null) {
            logCallback.log(message);
        }
    }

    private static int getEdgeMarginPx(Context ctx) {
        return (int) (8 * ctx.getResources().getDisplayMetrics().density);
    }

    private int[] clampBarPosition(int x, int y, int screenWidth, int screenHeight) {
        int m = getEdgeMarginPx(context);
        int w = Math.max(barWidthPx, 1);
        int h = Math.max(barHeightPx, 1);
        if (x < m) {
            x = m;
        } else if (x > screenWidth - w - m) {
            x = screenWidth - w - m;
        }
        if (y < m) {
            y = m;
        } else if (y > screenHeight - h - m) {
            y = screenHeight - h - m;
        }
        return new int[] {x, y};
    }

    private void saveBarPosition() {
        if (params == null) {
            return;
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_POS_SAVED, true)
                .putInt(KEY_POS_X, params.x)
                .putInt(KEY_POS_Y, params.y)
                .commit();
    }

    private void applyInitialPosition(int screenWidth, int screenHeight, float density) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_POS_SAVED, false)) {
            int x = prefs.getInt(KEY_POS_X, 0);
            int y = prefs.getInt(KEY_POS_Y, 0);
            int[] c = clampBarPosition(x, y, screenWidth, screenHeight);
            params.x = c[0];
            params.y = c[1];
        } else {
            params.x = (int) (12 * density);
            params.y = screenHeight - barHeightPx - (int) (160 * density);
        }
    }

    private static boolean rawPointInsideView(float rawX, float rawY, View v) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return rawX >= loc[0] && rawX < loc[0] + v.getWidth()
                && rawY >= loc[1] && rawY < loc[1] + v.getHeight();
    }

    private void startProjectionServiceAction(String action) {
        startProjectionServiceAction(action, null);
    }

    /**
     * @param clusterCloseSendBackground yalnızca {@link MapControlService#ACTION_BENCH_CLOSE_CLUSTER} için;
     *                                   {@code false} = HOME atma, uygulama önde kalsın
     */
    private void startProjectionServiceAction(String action, Boolean clusterCloseSendBackground) {
        try {
            Intent i = new Intent(context, MapControlService.class).setAction(action);
            if (clusterCloseSendBackground != null
                    && MapControlService.ACTION_BENCH_CLOSE_CLUSTER.equals(action)) {
                i.putExtra(MapControlService.EXTRA_CLUSTER_CLOSE_SEND_BACKGROUND, clusterCloseSendBackground);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i);
            } else {
                context.startService(i);
            }
            log("[INFO] Yüzen yansıtma: servis " + action);
        } catch (Exception e) {
            log("[ERROR] Yüzen yansıtma servis: " + e.getMessage());
            Toast.makeText(context, "Servis başlatılamadı: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openTargetAppPickerFromOverlay() {
        try {
            TargetAppPickerOverlay.show(context, logCallback);
        } catch (Exception e) {
            log("[ERROR] Hedef uygulama overlay: " + e.getMessage());
            Toast.makeText(context, "Seçici açılamadı: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void unregisterThemeConfigCallback() {
        if (mThemeConfigCallback != null) {
            try {
                context.getApplicationContext().unregisterComponentCallbacks(mThemeConfigCallback);
            } catch (Exception ignored) {
            }
            mThemeConfigCallback = null;
        }
    }

    private void registerThemeConfigCallback() {
        unregisterThemeConfigCallback();
        mThemeConfigCallback = new android.content.ComponentCallbacks2() {
            @Override
            public void onConfigurationChanged(@NonNull Configuration newConfig) {
                new Handler(Looper.getMainLooper()).post(() -> reapplyProjectionBarTheme());
            }

            @Override
            public void onLowMemory() {
            }

            @Override
            public void onTrimMemory(int level) {
            }
        };
        context.getApplicationContext().registerComponentCallbacks(mThemeConfigCallback);
    }

    private void reapplyProjectionBarTheme() {
        if (barRoot == null || !isShowing) {
            return;
        }
        if (!(barRoot instanceof LinearLayout)) {
            return;
        }
        LinearLayout root = (LinearLayout) barRoot;
        int textC = UiStyles.color(context, R.color.textPrimary);
        int cardC = UiStyles.color(context, R.color.surfaceCard);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setAlpha(1f);
        for (int i = 0; i < root.getChildCount(); i++) {
            View ch = root.getChildAt(i);
            if (ch instanceof Button) {
                Button b = (Button) ch;
                b.setTextColor(textC);
                for (android.graphics.drawable.Drawable dr2 : b.getCompoundDrawables()) {
                    if (dr2 != null) {
                        DrawableCompat.setTint(dr2, textC);
                    }
                }
                b.setCompoundDrawablePadding(FloatingOverlayBarSpec.compoundDrawablePaddingPx(context));
                FloatingOverlayBarSpec.applyLikeFloatingBackButton(b, cardC);
            }
        }
    }

    private void cleanupExistingView() {
        unregisterThemeConfigCallback();
        if (barRoot != null) {
            try {
                if (windowManager != null) {
                    windowManager.removeView(barRoot);
                }
            } catch (Exception ignored) {
            }
            barRoot = null;
        }
        isShowing = false;
    }

    /**
     * Yüzen yansıtma düğmeleri artık {@link FloatingBackButtonManager} yan menüsündedir; ayrı pencere yok.
     */
    public synchronized void show() {
        log("[INFO] Yüzen yansıtma (ayrı çubuk devre dışı — FloatingBackButtonManager kullanın)");
    }

    private Button makeBarButton(String label, int iconRes) {
        Button b = new Button(context);
        b.setText(label);
        b.setAllCaps(false);
        b.setIncludeFontPadding(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, FloatingOverlayBarSpec.ROW_TEXT_SIZE_SP);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(UiStyles.color(context, R.color.textPrimary));
        b.setMaxLines(2);
        b.setGravity(Gravity.CENTER);
        b.setCompoundDrawablePadding(FloatingOverlayBarSpec.compoundDrawablePaddingPx(context));
        android.graphics.drawable.Drawable dr = ContextCompat.getDrawable(context, iconRes);
        if (dr != null) {
            dr = DrawableCompat.wrap(dr).mutate();
            int iconPx = FloatingOverlayBarSpec.rowIconSizePx(context);
            dr.setBounds(0, 0, iconPx, iconPx);
            DrawableCompat.setTint(dr, UiStyles.color(context, R.color.textPrimary));
            b.setCompoundDrawables(null, dr, null, null);
        }
        int p = FloatingOverlayBarSpec.rowInnerPadPx(context);
        b.setPadding(p, p, p, p);
        FloatingOverlayBarSpec.applyLikeFloatingBackButton(
                b, UiStyles.color(context, R.color.surfaceCard));
        return b;
    }

    public synchronized void hide() {
        TargetAppPickerOverlay.dismissIfShowing();
        ProjectionVDBusTargetPickerManager.dismissIfShowing();
        if (params != null) {
            saveBarPosition();
        }
        cleanupExistingView();
        log("[INFO] Yüzen yansıtma kontrolleri gizlendi");
    }

    public boolean isShowing() {
        return isShowing;
    }

    public static void saveEnabledState(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** Varsayılan kapalı — kullanıcı ayarlardan açar. */
    public static boolean loadEnabledState(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }
}
