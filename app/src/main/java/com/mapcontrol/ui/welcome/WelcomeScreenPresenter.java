package com.mapcontrol.ui.welcome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;

/**
 * Kayıtlı giriş tasarımını varsayılan ekranda tam ekran gösterir.
 * Dokununca veya süre dolunca kapanır. Küme ekranındaki açılış görseline dokunmaz.
 */
public final class WelcomeScreenPresenter {

    private static final String TAG = "WelcomeScreen";
    private static final long VISIBLE_MS = 8_000L;
    private static final long DISMISS_MS = 620L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static View overlayView;
    private static View curtainView;
    private static WindowManager windowManager;
    private static Runnable autoHide;
    private static boolean dismissing;

    private WelcomeScreenPresenter() {
    }

    /** Kayıtlı tasarımı açar. Kapalıysa bir şey göstermez. */
    public static void showSaved(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(
                WelcomeDesignCatalog.PREFS_NAME, Context.MODE_PRIVATE);
        String id = WelcomeDesignCatalog.savedId(prefs);
        MAIN.post(() -> showOnMain(app, id));
    }

    private static void showOnMain(Context app, String designId) {
        if (overlayView != null || dismissing) {
            return;
        }
        View content = WelcomeDesignCatalog.createView(app, designId);
        if (content == null) {
            return;
        }
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            return;
        }
        content.setClickable(true);
        content.setOnClickListener(v -> hide());

        FrameLayout host = new FrameLayout(app);
        host.setBackgroundColor(0xFF10141B);
        host.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        View curtain = new View(app);
        curtain.setBackgroundColor(0xFF10141B);
        curtain.setAlpha(0f);
        host.addView(curtain, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        host.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                PixelFormat.OPAQUE);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;
        params.alpha = 1f;
        applyFullDisplaySize(wm, params);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.setFitInsetsTypes(0);
            params.setFitInsetsSides(0);
            params.setFitInsetsIgnoringVisibility(true);
        }
        try {
            wm.addView(host, params);
        } catch (RuntimeException e) {
            Log.w(TAG, "Hoşgeldin ekranı açılamadı", e);
            return;
        }
        overlayView = host;
        curtainView = curtain;
        windowManager = wm;
        autoHide = WelcomeScreenPresenter::hide;
        MAIN.postDelayed(autoHide, VISIBLE_MS);
    }

    /** Sistem çubuğu payını bırakmadan fiziksel ekranın tamamını kaplar. */
    private static void applyFullDisplaySize(WindowManager wm, WindowManager.LayoutParams params) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = wm.getMaximumWindowMetrics().getBounds();
            params.width = bounds.width();
            params.height = bounds.height();
            return;
        }
        Point size = new Point();
        wm.getDefaultDisplay().getRealSize(size);
        params.width = size.x;
        params.height = size.y;
    }

    public static void hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(WelcomeScreenPresenter::hide);
            return;
        }
        if (autoHide != null) {
            MAIN.removeCallbacks(autoHide);
            autoHide = null;
        }
        if (overlayView == null || windowManager == null || dismissing) {
            return;
        }
        dismissing = true;
        View view = overlayView;
        View curtain = curtainView;
        WindowManager wm = windowManager;
        if (curtain == null) {
            finishHide(view, wm);
            return;
        }
        curtain.animate().cancel();
        curtain.animate()
                .alpha(1f)
                .setDuration(DISMISS_MS)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> finishHide(view, wm))
                .start();
    }

    private static void finishHide(View view, WindowManager wm) {
        dismissing = false;
        if (overlayView == view) {
            overlayView = null;
            curtainView = null;
            windowManager = null;
        }
        try {
            wm.removeView(view);
        } catch (RuntimeException ignored) {
        }
    }
}
