package com.mapcontrol.manager;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.HapticFeedbackConstants;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.mapcontrol.R;
import com.mapcontrol.service.GlobalBackService;
import com.mapcontrol.service.MapControlService;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.AppLaunchHelper;

import java.util.ArrayList;

/**
 * Yüzen geri tuşu + uzun basışla açılan yan menü (yansıtma + hızlı işlemler).
 */
public class FloatingBackButtonManager {
    private static final String PREFS_NAME = "MapControlPrefs";
    private static final String KEY_FLOATING_BACK_BUTTON_ENABLED = "floatingBackButtonEnabled";
    private static final String KEY_FLOATING_BACK_POS_SAVED = "floatingBackPosSaved";
    private static final String KEY_FLOATING_BACK_POS_X = "floatingBackPosX";
    private static final String KEY_FLOATING_BACK_POS_Y = "floatingBackPosY";

    private static volatile FloatingBackButtonManager sInstance;
    private static final int PENDING_LOG_MAX = 32;
    private static final ArrayList<String> sPendingForUi = new ArrayList<>();

    private static final int LONG_PRESS_MS = 550;
    private static final int DRAG_SLOP_PX = 12;
    private static final int CLICK_SLOP_PX = 12;

    private Context context;
    private WindowManager windowManager;
    /** Kök overlay görünümü ({@link FrameLayout}). */
    private View floatingButton;
    private WindowManager.LayoutParams params;
    private boolean isShowing = false;

    private LinearLayout cardInner;
    private AppCompatImageButton btnBack;
    private LinearLayout menuContainer;
    private AppCompatImageButton btnProjChange;
    private AppCompatImageButton btnProjOpen;
    private AppCompatImageButton btnProjClose;
    private AppCompatImageButton btnQaToggle;
    private LinearLayout qaActionsRow;
    private AppCompatImageButton btnQaDisplay0;
    private AppCompatImageButton btnQaDisplayCluster;
    private AppCompatImageButton btnQaWifi;

    private boolean menuOpen;
    private boolean qaExpanded;
    private boolean dragging;
    /** Bu dokunuşta uzun basış menü aç/kapa işlediyse UP'te kısa dokunuş BACK sayılmasın. */
    private boolean longPressHandledThisGesture;

    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;
    private float longPressDownRawX;
    private float longPressDownRawY;
    private Runnable longPressRunnable;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Kök dinleyicide basılı tutulan hub hücresi (ripple + bırakınca animasyon). */
    private View pressedHubButton;

    private int barWidthPx;
    private int barHeightPx;

    private long lastDragLogUptimeMs;
    private static final int DRAG_LOG_MIN_INTERVAL_MS = 120;

    private android.content.ComponentCallbacks2 mThemeConfigCallback;

    public interface LogCallback {
        void log(String message);
    }

    private LogCallback logCallback;

    private FloatingBackButtonManager(Context appContext) {
        this.context = appContext.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    public static FloatingBackButtonManager getInstance(Context anyContext) {
        if (sInstance == null) {
            synchronized (FloatingBackButtonManager.class) {
                if (sInstance == null) {
                    sInstance = new FloatingBackButtonManager(anyContext);
                    sInstance.log("[INFO] tekil FloatingBackButtonManager oluşturuldu");
                }
            }
        }
        return sInstance;
    }

    public void setLogCallback(LogCallback callback) {
        this.logCallback = callback;
        if (callback != null) {
            synchronized (FloatingBackButtonManager.class) {
                for (int i = 0; i < sPendingForUi.size(); i++) {
                    callback.log(sPendingForUi.get(i));
                }
                sPendingForUi.clear();
            }
        }
    }

    private void log(String message) {
        if (logCallback != null) {
            logCallback.log(message);
        } else {
            synchronized (FloatingBackButtonManager.class) {
                if (sPendingForUi.size() < PENDING_LOG_MAX) {
                    sPendingForUi.add(message);
                }
            }
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

    private void saveButtonPosition() {
        if (params == null) {
            return;
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_FLOATING_BACK_POS_SAVED, true)
                .putInt(KEY_FLOATING_BACK_POS_X, params.x)
                .putInt(KEY_FLOATING_BACK_POS_Y, params.y)
                .commit();
    }

    private void applyInitialPosition(int screenWidth, int screenHeight, float density) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_FLOATING_BACK_POS_SAVED, false)) {
            int x = prefs.getInt(KEY_FLOATING_BACK_POS_X, 0);
            int y = prefs.getInt(KEY_FLOATING_BACK_POS_Y, 0);
            int[] c = clampBarPosition(x, y, screenWidth, screenHeight);
            params.x = c[0];
            params.y = c[1];
        } else {
            final int cell = FloatingOverlayBarSpec.uniformCellSidePx(context);
            params.x = screenWidth - cell - (int) (16 * density);
            params.y = screenHeight - cell - (int) (100 * density);
        }
    }

    private static LinearLayout.LayoutParams newCellLp(int cellSide, int gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cellSide, cellSide);
        lp.gravity = Gravity.CENTER_VERTICAL;
        lp.setMargins(gap / 2, 0, gap / 2, 0);
        return lp;
    }

    private static boolean rawPointInsideView(float rawX, float rawY, View v) {
        if (v == null || v.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return rawX >= loc[0] && rawX < loc[0] + v.getWidth()
                && rawY >= loc[1] && rawY < loc[1] + v.getHeight();
    }

    private View findHubTargetAt(float rawX, float rawY) {
        if (rawPointInsideView(rawX, rawY, btnBack)) {
            return btnBack;
        }
        if (!menuOpen) {
            return null;
        }
        if (rawPointInsideView(rawX, rawY, btnProjChange)) {
            return btnProjChange;
        }
        if (rawPointInsideView(rawX, rawY, btnProjOpen)) {
            return btnProjOpen;
        }
        if (rawPointInsideView(rawX, rawY, btnProjClose)) {
            return btnProjClose;
        }
        if (rawPointInsideView(rawX, rawY, btnQaToggle)) {
            return btnQaToggle;
        }
        if (qaExpanded) {
            if (rawPointInsideView(rawX, rawY, btnQaDisplay0)) {
                return btnQaDisplay0;
            }
            if (rawPointInsideView(rawX, rawY, btnQaDisplayCluster)) {
                return btnQaDisplayCluster;
            }
            if (rawPointInsideView(rawX, rawY, btnQaWifi)) {
                return btnQaWifi;
            }
        }
        return null;
    }

    private void setRippleHotspot(View v, float rawX, float rawY) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || v == null) {
            return;
        }
        Drawable bg = v.getBackground();
        if (bg instanceof RippleDrawable) {
            int[] loc = new int[2];
            v.getLocationOnScreen(loc);
            bg.setHotspot(rawX - loc[0], rawY - loc[1]);
        }
    }

    private void updateHubPress(float rawX, float rawY) {
        View target = findHubTargetAt(rawX, rawY);
        if (pressedHubButton == target) {
            if (target != null) {
                setRippleHotspot(target, rawX, rawY);
            }
            return;
        }
        clearHubPress();
        pressedHubButton = target;
        if (pressedHubButton != null) {
            setRippleHotspot(pressedHubButton, rawX, rawY);
            pressedHubButton.setPressed(true);
        }
    }

    private void clearHubPress() {
        if (pressedHubButton != null) {
            pressedHubButton.setPressed(false);
            pressedHubButton = null;
        }
    }

    private void performHubTap(View target, float rawX, float rawY) {
        if (target == null) {
            return;
        }
        setRippleHotspot(target, rawX, rawY);
        target.setPressed(false);
        pressedHubButton = null;
        target.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        animateButtonClick(target);
    }

    private void handleHubTap(float rawX, float rawY, boolean suppressBackTap) {
        View target = findHubTargetAt(rawX, rawY);
        if (target == null) {
            return;
        }
        if (target == btnBack) {
            if (!suppressBackTap) {
                performHubTap(btnBack, rawX, rawY);
                simulateBackButton();
            }
            return;
        }
        if (!menuOpen) {
            return;
        }
        performHubTap(target, rawX, rawY);
        if (target == btnProjChange) {
            openTargetAppPickerFromOverlay();
        } else if (target == btnProjOpen) {
            startProjectionServiceAction(MapControlService.ACTION_BENCH_OPEN_CLUSTER);
        } else if (target == btnProjClose) {
            startProjectionServiceAction(
                    MapControlService.ACTION_BENCH_CLOSE_CLUSTER,
                    false);
        } else if (target == btnQaToggle) {
            qaExpanded = !qaExpanded;
            applyQaExpandUi();
        } else if (qaExpanded) {
            if (target == btnQaDisplay0) {
                openRunningAppsOnDisplay0();
            } else if (target == btnQaDisplayCluster) {
                openRunningAppsOnCluster();
            } else if (target == btnQaWifi) {
                startUserWifiStabilize();
            }
        }
    }

    private void cancelLongPress() {
        if (longPressRunnable != null) {
            handler.removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    private void scheduleLongPress() {
        cancelLongPress();
        longPressRunnable = () -> {
            if (dragging) {
                return;
            }
            float sx = longPressDownRawX;
            float sy = longPressDownRawY;
            if (!menuOpen) {
                if (rawPointInsideView(sx, sy, btnBack)) {
                    openMenuAnimated();
                    longPressHandledThisGesture = true;
                    log("[INFO] Yüzen menü: uzun basış ile açıldı");
                }
            } else {
                // Menü açıkken yalnızca ◀ üzerinde uzun basınca kapat (menü içi uzun basış kapatmasın).
                if (rawPointInsideView(sx, sy, btnBack)) {
                    closeMenuAnimated();
                    longPressHandledThisGesture = true;
                    log("[INFO] Yüzen menü: geri üzerinde uzun basış ile kapatıldı");
                }
            }
        };
        handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
    }

    private void remeasureAndUpdateWindow() {
        if (floatingButton == null || params == null || windowManager == null) {
            return;
        }
        int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        floatingButton.measure(widthSpec, heightSpec);
        barWidthPx = floatingButton.getMeasuredWidth();
        barHeightPx = floatingButton.getMeasuredHeight();
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        try {
            windowManager.updateViewLayout(floatingButton, params);
        } catch (Exception e) {
            log("[ERROR] Hub remeasure updateViewLayout: " + e.getMessage());
        }
    }

    /** İkonlar ImageView ölçeklemesi ile gerçek ortada; arka plan ripple ile dokunma geri bildirimi. */
    private void applyHubImageChrome(AppCompatImageButton b) {
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        int pad = FloatingOverlayBarSpec.rowInnerPadPx(context);
        b.setPadding(pad, pad, pad, pad);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setAdjustViewBounds(false);
        FloatingOverlayBarSpec.applyHubCellRippleBackground(context, b);
    }

    private void applyHubImageIcon(AppCompatImageButton b, int iconResId) {
        b.setImageResource(iconResId);
        ImageViewCompat.setImageTintList(b,
                ColorStateList.valueOf(UiStyles.color(context, R.color.textPrimary)));
    }

    private void applyHubChromeAndIcon(AppCompatImageButton b, int iconResId) {
        applyHubImageChrome(b);
        applyHubImageIcon(b, iconResId);
    }

    private void applyQaToggleUi() {
        if (btnQaToggle == null) {
            return;
        }
        applyHubImageChrome(btnQaToggle);
        int icon = qaExpanded ? R.drawable.ic_mdi_chevron_double_left : R.drawable.ic_mdi_lightning_bolt;
        applyHubImageIcon(btnQaToggle, icon);
    }

    private void applyQaExpandUi() {
        if (qaActionsRow == null) {
            return;
        }
        qaActionsRow.setVisibility(qaExpanded ? View.VISIBLE : View.GONE);
        applyQaToggleUi();
        if (floatingButton != null) {
            floatingButton.post(this::remeasureAndUpdateWindow);
        }
    }

    private void openMenuAnimated() {
        if (menuOpen || menuContainer == null) {
            return;
        }
        menuOpen = true;
        menuContainer.setVisibility(View.VISIBLE);
        menuContainer.setAlpha(0f);
        menuContainer.setScaleX(0.82f);
        menuContainer.setPivotX(0f);
        floatingButton.post(() -> {
            menuContainer.setPivotY(menuContainer.getHeight() / 2f);
            menuContainer.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .setDuration(320)
                    .setInterpolator(new OvershootInterpolator(1.08f))
                    .withEndAction(this::remeasureAndUpdateWindow)
                    .start();
        });
    }

    private void closeMenuAnimated() {
        if (!menuOpen || menuContainer == null) {
            return;
        }
        menuContainer.setPivotX(0f);
        menuContainer.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .setDuration(240)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    menuContainer.setVisibility(View.GONE);
                    menuContainer.setAlpha(1f);
                    menuContainer.setScaleX(1f);
                    menuOpen = false;
                    qaExpanded = false;
                    if (qaActionsRow != null) {
                        qaActionsRow.setVisibility(View.GONE);
                    }
                    applyQaToggleUi();
                    remeasureAndUpdateWindow();
                })
                .start();
    }

    private void openTargetAppPickerFromOverlay() {
        try {
            TargetAppPickerOverlay.show(context, msg -> log(msg));
        } catch (Exception e) {
            log("[ERROR] Hedef uygulama overlay: " + e.getMessage());
            Toast.makeText(context, "Seçici açılamadı: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startProjectionServiceAction(String action) {
        startProjectionServiceAction(action, null);
    }

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

    private void openRunningAppsOnDisplay0() {
        try {
            DisplayRunningAppsOverlay.show(
                    context,
                    Display.DEFAULT_DISPLAY,
                    context.getString(R.string.floating_qa_title_display1),
                    this::log);
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
                    this::log);
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

    public synchronized void show() {
        log("[INFO] Floating Back hub show() çağrıldı");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                log("[WARN] Floating Back: overlay izni yok");
                handler.post(() -> {
                    try {
                        android.content.Intent intent =
                                new android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                        Toast.makeText(context,
                                "Lütfen 'Diğer uygulamaların üzerinde görüntüleme' iznini açın",
                                Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(context, "İzin ayarlarına gidilemedi: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
                return;
            }
        }

        if (isShowing && floatingButton != null) {
            try {
                if (floatingButton.getParent() != null) {
                    log("[DEBUG] Hub zaten gösteriliyor; servis/ayar tekrar çağrısı yoksayıldı");
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        cleanupExistingView();

        float density = context.getResources().getDisplayMetrics().density;
        final int cellSide = FloatingOverlayBarSpec.uniformCellSidePx(context);
        int padH = FloatingOverlayBarSpec.dpToPx(FloatingOverlayBarSpec.BAR_CARD_PAD_H_DP, density);
        int padV = FloatingOverlayBarSpec.dpToPx(FloatingOverlayBarSpec.BAR_CARD_PAD_V_DP, density);
        int gap = FloatingOverlayBarSpec.dpToPx(FloatingOverlayBarSpec.BAR_COLUMN_GAP_DP, density);

        /** Çocuk Button’lar dokunmayı yutmasın; vuruş testi kök dinleyicide (FloatingQuickActions ile aynı model). */
        FrameLayout root = new FrameLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return true;
            }
        };
        cardInner = new LinearLayout(context);
        cardInner.setOrientation(LinearLayout.HORIZONTAL);
        cardInner.setPadding(padH, padV, padH, padV);
        UiStyles.applySolidRoundedBackgroundDp(cardInner,
                UiStyles.color(context, R.color.surfaceCard), FloatingOverlayBarSpec.BAR_CORNER_DP);
        cardInner.setAlpha(0.96f);

        btnBack = new AppCompatImageButton(context);
        applyHubChromeAndIcon(btnBack, R.drawable.ic_hub_arrow_left);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(cellSide, cellSide);
        backLp.gravity = Gravity.CENTER_VERTICAL;
        cardInner.addView(btnBack, backLp);

        menuContainer = new LinearLayout(context);
        menuContainer.setOrientation(LinearLayout.HORIZONTAL);
        menuContainer.setVisibility(View.GONE);

        btnProjChange = new AppCompatImageButton(context);
        btnProjOpen = new AppCompatImageButton(context);
        btnProjClose = new AppCompatImageButton(context);
        applyHubChromeAndIcon(btnProjChange, R.drawable.ic_mdi_package_variant);
        applyHubChromeAndIcon(btnProjOpen, R.drawable.ic_mdi_map);
        applyHubChromeAndIcon(btnProjClose, R.drawable.ic_mdi_close);

        menuContainer.addView(btnProjChange, newCellLp(cellSide, gap));
        menuContainer.addView(btnProjOpen, newCellLp(cellSide, gap));
        menuContainer.addView(btnProjClose, newCellLp(cellSide, gap));

        btnQaToggle = new AppCompatImageButton(context);
        applyQaToggleUi();

        menuContainer.addView(btnQaToggle, newCellLp(cellSide, gap));

        qaActionsRow = new LinearLayout(context);
        qaActionsRow.setOrientation(LinearLayout.HORIZONTAL);
        qaActionsRow.setVisibility(View.GONE);

        btnQaDisplay0 = new AppCompatImageButton(context);
        btnQaDisplayCluster = new AppCompatImageButton(context);
        btnQaWifi = new AppCompatImageButton(context);
        applyHubChromeAndIcon(btnQaDisplay0, R.drawable.ic_mdi_cellphone);
        applyHubChromeAndIcon(btnQaDisplayCluster, R.drawable.ic_hub_monitor);
        applyHubChromeAndIcon(btnQaWifi, R.drawable.ic_mdi_refresh);

        qaActionsRow.addView(btnQaDisplay0, newCellLp(cellSide, gap));
        qaActionsRow.addView(btnQaDisplayCluster, newCellLp(cellSide, gap));
        qaActionsRow.addView(btnQaWifi, newCellLp(cellSide, gap));

        menuContainer.addView(qaActionsRow);

        LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        menuLp.gravity = Gravity.CENTER_VERTICAL;
        cardInner.addView(menuContainer, menuLp);

        root.addView(cardInner, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        floatingButton = root;

        btnBack.setClickable(false);
        btnBack.setFocusable(false);

        btnProjChange.setClickable(false);
        btnProjOpen.setClickable(false);
        btnProjClose.setClickable(false);
        btnQaToggle.setClickable(false);
        btnQaDisplay0.setClickable(false);
        btnQaDisplayCluster.setClickable(false);
        btnQaWifi.setClickable(false);
        btnProjChange.setFocusable(false);
        btnProjOpen.setFocusable(false);
        btnProjClose.setFocusable(false);
        btnQaToggle.setFocusable(false);
        btnQaDisplay0.setFocusable(false);
        btnQaDisplayCluster.setFocusable(false);
        btnQaWifi.setFocusable(false);

        menuOpen = false;
        qaExpanded = false;

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        root.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    longPressDownRawX = event.getRawX();
                    longPressDownRawY = event.getRawY();
                    dragging = false;
                    longPressHandledThisGesture = false;
                    cancelLongPress();
                    scheduleLongPress();
                    updateHubPress(event.getRawX(), event.getRawY());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int deltaX = (int) (event.getRawX() - initialTouchX);
                    int deltaY = (int) (event.getRawY() - initialTouchY);
                    if (!dragging && (Math.abs(deltaX) > DRAG_SLOP_PX || Math.abs(deltaY) > DRAG_SLOP_PX)) {
                        dragging = true;
                        cancelLongPress();
                        clearHubPress();
                    }
                    if (dragging) {
                        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
                        windowManager.getDefaultDisplay().getMetrics(dm);
                        int newX = initialX + deltaX;
                        int newY = initialY + deltaY;
                        int[] c = clampBarPosition(newX, newY, dm.widthPixels, dm.heightPixels);
                        params.x = c[0];
                        params.y = c[1];
                        try {
                            windowManager.updateViewLayout(floatingButton, params);
                        } catch (Exception e) {
                            log("[ERROR] Hub updateViewLayout: " + e.getMessage());
                        }
                        long now = SystemClock.uptimeMillis();
                        if (now - lastDragLogUptimeMs >= DRAG_LOG_MIN_INTERVAL_MS) {
                            lastDragLogUptimeMs = now;
                            log(String.format(java.util.Locale.US,
                                    "[DEBUG] Hub sürükle (x=%d, y=%d)", params.x, params.y));
                        }
                    }
                    if (!dragging) {
                        updateHubPress(event.getRawX(), event.getRawY());
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    cancelLongPress();
                    boolean suppressBackTap = longPressHandledThisGesture;
                    longPressHandledThisGesture = false;
                    float moveDx = Math.abs(event.getRawX() - initialTouchX);
                    float moveDy = Math.abs(event.getRawY() - initialTouchY);
                    if (!dragging && moveDx < CLICK_SLOP_PX && moveDy < CLICK_SLOP_PX) {
                        handleHubTap(event.getRawX(), event.getRawY(), suppressBackTap);
                    }
                    clearHubPress();
                    saveButtonPosition();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelLongPress();
                    longPressHandledThisGesture = false;
                    clearHubPress();
                    saveButtonPosition();
                    return true;
                default:
                    return false;
            }
        });

        android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        floatingButton.measure(widthSpec, heightSpec);
        barWidthPx = floatingButton.getMeasuredWidth();
        barHeightPx = floatingButton.getMeasuredHeight();

        applyInitialPosition(displayMetrics.widthPixels, displayMetrics.heightPixels, density);

        try {
            if (windowManager != null && floatingButton != null && params != null) {
                windowManager.addView(floatingButton, params);
                isShowing = true;
                registerThemeConfigCallback();
                log("[SUCCESS] Floating Back hub gösterildi");
            }
        } catch (Exception e) {
            log("[ERROR] Floating Back hub gösterilemedi: " + e.getMessage());
            floatingButton = null;
            isShowing = false;
            handler.post(() ->
                    Toast.makeText(context, "Yüzen buton gösterilemedi: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show());
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
                new Handler(Looper.getMainLooper()).post(() -> reapplyHubTheme());
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

    private void reapplyHubTheme() {
        if (floatingButton == null || !isShowing || cardInner == null) {
            return;
        }
        int cardC = UiStyles.color(context, R.color.surfaceCard);
        UiStyles.applySolidRoundedBackgroundDp(cardInner, cardC, FloatingOverlayBarSpec.BAR_CORNER_DP);
        applyHubChromeAndIcon(btnBack, R.drawable.ic_hub_arrow_left);
        applyHubChromeAndIcon(btnProjChange, R.drawable.ic_mdi_package_variant);
        applyHubChromeAndIcon(btnProjOpen, R.drawable.ic_mdi_map);
        applyHubChromeAndIcon(btnProjClose, R.drawable.ic_mdi_close);
        applyQaToggleUi();
        applyHubChromeAndIcon(btnQaDisplay0, R.drawable.ic_mdi_cellphone);
        applyHubChromeAndIcon(btnQaDisplayCluster, R.drawable.ic_hub_monitor);
        applyHubChromeAndIcon(btnQaWifi, R.drawable.ic_mdi_refresh);
    }

    private void cleanupExistingView() {
        cancelLongPress();
        clearHubPress();
        unregisterThemeConfigCallback();
        menuContainer = null;
        cardInner = null;
        btnBack = null;
        btnProjChange = null;
        btnProjOpen = null;
        btnProjClose = null;
        btnQaToggle = null;
        qaActionsRow = null;
        btnQaDisplay0 = null;
        btnQaDisplayCluster = null;
        btnQaWifi = null;
        menuOpen = false;
        qaExpanded = false;
        if (floatingButton != null) {
            try {
                if (windowManager != null) {
                    windowManager.removeView(floatingButton);
                }
            } catch (Exception ignored) {
            }
            floatingButton = null;
        }
        isShowing = false;
    }

    public synchronized void hide() {
        DisplayRunningAppsOverlay.dismissIfShowing();
        TargetAppPickerOverlay.dismissIfShowing();
        ProjectionVDBusTargetPickerManager.dismissIfShowing();
        if (params != null) {
            saveButtonPosition();
        }
        cleanupExistingView();
        log("[INFO] Floating Back hub gizlendi");
    }

    public boolean isShowing() {
        return isShowing;
    }

    private void animateButtonClick(View button) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(button, "scaleX", 1.0f, 0.85f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(button, "scaleY", 1.0f, 0.85f, 1.0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(button, "alpha", 0.96f, 0.65f, 0.96f);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY, alpha);
        animatorSet.setDuration(200);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.start();
    }

    private void simulateBackButton() {
        String foregroundPackage = getForegroundPackage();

        if (foregroundPackage != null && foregroundPackage.equals("com.mapcontrol")) {
            log("[DEBUG] Floating Back: MapControl aktifken BACK tuşu gönderilmedi");
            return;
        }

        if (GlobalBackService.isServiceEnabled()) {
            // ok
        } else if (GlobalBackService.isRegisteredInSystemAccessibilitySettings(context)) {
            log("[INFO] Floating Back: erişilebilirlik açık, servis bağlantısı kısa sürede deneniyor");
            handler.postDelayed(() -> {
                if (GlobalBackService.isServiceEnabled()) {
                    if (GlobalBackService.performBackAction()) {
                        log("[SUCCESS] Floating Back: BACK (gecikmeli bağlantı sonrası)");
                    }
                } else {
                    Toast.makeText(
                            context,
                            "Erişilebilirlik servisi henüz yanıt vermiyor; birkaç sn sonra tekrar deneyin",
                            Toast.LENGTH_LONG).show();
                }
            }, 400);
            return;
        } else {
            log("[WARN] Floating Back: GlobalBackService ayarlarda kapalı, yönlendiriliyor");
            handler.post(() -> {
                try {
                    android.content.Intent intent = new android.content.Intent(
                            android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    Toast.makeText(
                            context,
                            "Lütfen 'Global Back Service' (MapControl) erişilebilirlik servisini açın",
                            Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    log("[ERROR] Floating Back: Accessibility ayarlarına gidilemedi - " + e.getMessage());
                }
            });
            return;
        }

        boolean success = GlobalBackService.performBackAction();
        if (success) {
            log("[SUCCESS] Floating Back: BACK tuşu gönderildi (AccessibilityService)");
        } else {
            log("[ERROR] Floating Back: BACK tuşu gönderilemedi");
        }
    }

    private String getForegroundPackage() {
        try {
            try {
                java.lang.Process process = Runtime.getRuntime().exec("dumpsys activity activities");
                java.io.BufferedReader reader =
                        new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("mResumedActivity") || line.contains("mFocusedActivity")) {
                        int startIndex = line.indexOf("com.");
                        if (startIndex != -1) {
                            int endIndex = line.indexOf("/", startIndex);
                            if (endIndex == -1) {
                                endIndex = line.indexOf(" ", startIndex);
                            }
                            if (endIndex == -1) {
                                endIndex = line.length();
                            }
                            String packageName = line.substring(startIndex, endIndex).trim();
                            reader.close();
                            return packageName;
                        }
                    }
                }
                reader.close();
            } catch (Exception e) {
                log("[DEBUG] Floating Back: dumpsys hatası - " + e.getMessage());
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                android.app.ActivityManager am =
                        (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    java.util.List<android.app.ActivityManager.RunningAppProcessInfo> runningProcesses =
                            am.getRunningAppProcesses();
                    if (runningProcesses != null) {
                        for (android.app.ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                            if (processInfo.importance
                                    == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                                if (processInfo.pkgList != null && processInfo.pkgList.length > 0) {
                                    return processInfo.pkgList[0];
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static void saveEnabledState(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_FLOATING_BACK_BUTTON_ENABLED, enabled).apply();
    }

    public static boolean loadEnabledState(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_FLOATING_BACK_BUTTON_ENABLED, true);
    }
}
