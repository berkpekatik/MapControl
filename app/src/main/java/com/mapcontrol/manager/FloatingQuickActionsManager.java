package com.mapcontrol.manager;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.mapcontrol.R;
import com.mapcontrol.service.MapControlService;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.AppLaunchHelper;

/**
 * Yüzen hızlı işlemler: 1. ekran açık uygulamalar, 2. ekran açık uygulamalar, Wi‑Fi tazele.
 * Daraltılabilir: tek anchor ile açılır / kapanır. Diğer yüzen kontrollerle aynı prefs/izin modeli.
 */
public class FloatingQuickActionsManager {

    private static final String PREFS_NAME = "MapControlPrefs";
    private static final String KEY_ENABLED = "floatingQuickActionsEnabled";
    private static final String KEY_POS_SAVED = "floatingQuickBarPosSaved";
    private static final String KEY_POS_X = "floatingQuickBarPosX";
    private static final String KEY_POS_Y = "floatingQuickBarPosY";

    private static volatile FloatingQuickActionsManager sInstance;

    private Context context;
    private WindowManager windowManager;
    private View barRoot;
    private WindowManager.LayoutParams params;
    private boolean isShowing = false;
    private boolean isExpanded = false;
    private android.content.ComponentCallbacks2 mThemeConfigCallback;
    private LinearLayout quickInner;

    private LinearLayout actionsRow;
    private Button btnToggle;
    private Button btnDisplay0;
    private Button btnDisplayCluster;
    private Button btnWifi;
    private float density;

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

    private FloatingQuickActionsManager(Context appContext) {
        this.context = appContext.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    public static FloatingQuickActionsManager getInstance(Context anyContext) {
        if (sInstance == null) {
            synchronized (FloatingQuickActionsManager.class) {
                if (sInstance == null) {
                    sInstance = new FloatingQuickActionsManager(anyContext);
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

    private void applyInitialPosition(int screenWidth, int screenHeight, float d) {
        android.content.SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_POS_SAVED, false)) {
            int x = prefs.getInt(KEY_POS_X, 0);
            int y = prefs.getInt(KEY_POS_Y, 0);
            int[] c = clampBarPosition(x, y, screenWidth, screenHeight);
            params.x = c[0];
            params.y = c[1];
        } else {
            params.x = (int) (12 * d);
            params.y = screenHeight - barHeightPx - (int) (120 * d);
        }
    }

    private static boolean rawPointInsideView(float rawX, float rawY, View v) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return rawX >= loc[0] && rawX < loc[0] + v.getWidth()
                && rawY >= loc[1] && rawY < loc[1] + v.getHeight();
    }

    /**
     * Aç/kapa düğmesi: diğer hızlı işlem düğmeleriyle aynı tipografi, ikon 20dp üst, etiket altta.
     */
    private void applyToggleIconOnly(boolean expanded) {
        if (btnToggle == null) {
            return;
        }
        int res = expanded
                ? R.drawable.ic_mdi_chevron_double_left
                : R.drawable.ic_mdi_lightning_bolt;
        android.graphics.drawable.Drawable d = ContextCompat.getDrawable(context, res);
        if (d != null) {
            d = DrawableCompat.wrap(d).mutate();
            int iconPx = FloatingOverlayBarSpec.rowIconSizePx(context);
            d.setBounds(0, 0, iconPx, iconPx);
            DrawableCompat.setTint(d, UiStyles.color(context, R.color.textPrimary));
        }
        btnToggle.setAllCaps(false);
        btnToggle.setIncludeFontPadding(false);
        btnToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, FloatingOverlayBarSpec.ROW_TEXT_SIZE_SP);
        btnToggle.setTypeface(null, Typeface.BOLD);
        btnToggle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        btnToggle.setText(context.getString(R.string.floating_qa_label_toggle));
        btnToggle.setCompoundDrawablePadding(FloatingOverlayBarSpec.compoundDrawablePaddingPx(context));
        if (d != null) {
            btnToggle.setCompoundDrawables(null, d, null, null);
        } else {
            btnToggle.setCompoundDrawables(null, null, null, null);
        }
        int p = FloatingOverlayBarSpec.rowInnerPadPx(context);
        btnToggle.setPadding(p, p, p, p);
        btnToggle.setMaxLines(2);
        btnToggle.setGravity(Gravity.CENTER);
        UiStyles.styleOemButton(btnToggle, UiStyles.color(context, R.color.surfaceCardInner));
    }

    private void applyExpandUi() {
        if (actionsRow == null || btnToggle == null) {
            return;
        }
        if (isExpanded) {
            actionsRow.setVisibility(View.VISIBLE);
        } else {
            actionsRow.setVisibility(View.GONE);
        }
        applyToggleIconOnly(isExpanded);
        if (barRoot != null) {
            barRoot.requestLayout();
            barRoot.post(() -> {
                if (barRoot == null) {
                    return;
                }
                int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                barRoot.measure(widthSpec, heightSpec);
                barWidthPx = barRoot.getMeasuredWidth();
                barHeightPx = barRoot.getMeasuredHeight();
                if (params != null && windowManager != null) {
                    try {
                        windowManager.updateViewLayout(barRoot, params);
                    } catch (Exception ignored) {
                    }
                }
            });
        }
    }

    private void openRunningAppsOnDisplay0() {
        try {
            DisplayRunningAppsOverlay.show(
                    context,
                    Display.DEFAULT_DISPLAY,
                    context.getString(R.string.floating_qa_title_display1),
                    m -> log(m));
        } catch (Exception e) {
            log("[ERROR] 1. ekran açık uygulamalar: " + e.getMessage());
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openRunningAppsOnCluster() {
        try {
            int clusterId = AppLaunchHelper.getClusterDisplayId(context);
            DisplayRunningAppsOverlay.show(
                    context,
                    clusterId,
                    context.getString(R.string.floating_qa_title_display2),
                    m -> log(m));
        } catch (Exception e) {
            log("[ERROR] 2. ekran açık uygulamalar: " + e.getMessage());
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startUserWifiStabilize() {
        try {
            Intent i = new Intent(context, MapControlService.class)
                    .setAction(MapControlService.ACTION_USER_WIFI_STABILIZE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i);
            } else {
                context.startService(i);
            }
            log("[INFO] Yüzen hızlı işlem: Wi‑Fi stabilize");
        } catch (Exception e) {
            log("[ERROR] Wi‑Fi servis: " + e.getMessage());
            Toast.makeText(context, "Servis başlatılamadı: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                new Handler(Looper.getMainLooper()).post(() -> reapplyQuickBarTheme());
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

    private void reapplyQuickBarTheme() {
        if (!isShowing) {
            return;
        }
        if (quickInner != null) {
            UiStyles.applySolidRoundedBackgroundDp(quickInner,
                    UiStyles.color(context, R.color.surfaceCard), FloatingOverlayBarSpec.BAR_CORNER_DP);
            quickInner.setAlpha(0.95f);
        }
        if (btnToggle != null) {
            applyToggleIconOnly(isExpanded);
        }
        applyActionButtonThemed(btnDisplay0,
                R.string.floating_qa_label_display1, R.drawable.ic_mdi_cellphone);
        applyActionButtonThemed(btnDisplayCluster,
                R.string.floating_qa_label_display2, R.drawable.ic_mdi_inbox_outline);
        applyActionButtonThemed(btnWifi, R.string.floating_qa_label_wifi, R.drawable.ic_mdi_refresh);
    }

    private void applyActionButtonThemed(Button b, int labelRes, int iconRes) {
        if (b == null) {
            return;
        }
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(UiStyles.color(context, R.color.textPrimary));
        b.setIncludeFontPadding(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, FloatingOverlayBarSpec.ROW_TEXT_SIZE_SP);
        b.setText(context.getString(labelRes));
        int iconPx = FloatingOverlayBarSpec.rowIconSizePx(context);
        android.graphics.drawable.Drawable d = ContextCompat.getDrawable(context, iconRes);
        if (d != null) {
            d = DrawableCompat.wrap(d).mutate();
            d.setBounds(0, 0, iconPx, iconPx);
            DrawableCompat.setTint(d, UiStyles.color(context, R.color.textPrimary));
            b.setCompoundDrawables(null, d, null, null);
        }
        b.setCompoundDrawablePadding(FloatingOverlayBarSpec.compoundDrawablePaddingPx(context));
        int pad = FloatingOverlayBarSpec.rowInnerPadPx(context);
        b.setPadding(pad, pad, pad, pad);
        UiStyles.styleOemButton(b, UiStyles.color(context, R.color.surfaceCardInner));
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
        quickInner = null;
        actionsRow = null;
        btnToggle = null;
        btnDisplay0 = null;
        btnDisplayCluster = null;
        btnWifi = null;
        isShowing = false;
    }

    /**
     * Hızlı işlemler artık {@link FloatingBackButtonManager} yan menüsündedir; ayrı çubuk yok.
     */
    public synchronized void show() {
        log("[INFO] Yüzen hızlı işlemler (ayrı çubuk devre dışı — FloatingBackButtonManager kullanın)");
    }

    private Button makeActionButton(int labelRes, int iconRes) {
        Button b = new Button(context);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setText(context.getString(labelRes));
        b.setIncludeFontPadding(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, FloatingOverlayBarSpec.ROW_TEXT_SIZE_SP);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(UiStyles.color(context, R.color.textPrimary));
        b.setMaxLines(2);
        b.setCompoundDrawablePadding(FloatingOverlayBarSpec.compoundDrawablePaddingPx(context));
        android.graphics.drawable.Drawable d = ContextCompat.getDrawable(context, iconRes);
        if (d != null) {
            d = DrawableCompat.wrap(d).mutate();
            int iconPx = FloatingOverlayBarSpec.rowIconSizePx(context);
            d.setBounds(0, 0, iconPx, iconPx);
            DrawableCompat.setTint(d, UiStyles.color(context, R.color.textPrimary));
            b.setCompoundDrawables(null, d, null, null);
        }
        UiStyles.styleOemButton(b, UiStyles.color(context, R.color.surfaceCardInner));
        int p = FloatingOverlayBarSpec.rowInnerPadPx(context);
        b.setPadding(p, p, p, p);
        return b;
    }

    public synchronized void hide() {
        DisplayRunningAppsOverlay.dismissIfShowing();
        if (params != null) {
            saveBarPosition();
        }
        cleanupExistingView();
        log("[INFO] Yüzen hızlı işlemler gizlendi");
    }

    public boolean isShowing() {
        return isShowing;
    }

    public static void saveEnabledState(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** Varsayılan kapalı — yansıtma çubuğu ile aynı. */
    public static boolean loadEnabledState(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }
}
