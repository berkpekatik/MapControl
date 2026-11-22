package com.mapcontrol.manager;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;

import com.mapcontrol.R;
import com.mapcontrol.service.GlobalBackService;
import com.mapcontrol.service.MapControlService;
import com.mapcontrol.ui.theme.UiStyles;

import java.util.ArrayList;
import java.util.List;

/**
 * Yuvarlak yüzen geri tuşu.
 * <ul>
 *   <li>Kısa dokunuş: sistem geri (erişilebilirlik servisi; o yoksa MapControl öndeyse kendi içinde geri).</li>
 *   <li>Basılı tut: tuşun etrafında, ekranın ortasına bakan bir yay üzerinde etiketli kısayollar açılır.
 *       Parmağı kaldırmadan bir kısayolun üstüne kaydırıp bırakmak ya da açıldıktan sonra dokunmak çalıştırır.
 *       Ortadaki ✕ ya da boş alan menüyü kapatır.</li>
 *   <li>Sürükle: taşı (konum saklanır).</li>
 * </ul>
 * İki pencere: tuş kendi küçük penceresinde sabit durur; menü açılınca ayrı, saydam, tam ekran bir pencere
 * eklenir (dışarı dokununca kapanır, karartma yok) ve kapanınca kaldırılır. Tuş penceresi hiç taşınmaz/büyütülmez:
 * Android konumu değişen pencereyi eski yerinden kaydırarak taşıdığı için tek pencereli tasarımda kapanışta tuş
 * sol üst köşeden yerine kayıyordu.
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

    private static final int LONG_PRESS_MS = 450;
    private static final long A11Y_RETRY_MS = 400L;

    /** Yay üzerindeki komşu öğeler arası açı. */
    private static final double ITEM_STEP_RAD = Math.toRadians(50);
    private static final long OPEN_DURATION_MS = 340L;
    private static final long OPEN_STAGGER_MS = 40L;
    private static final long CLOSE_DURATION_MS = 180L;
    private static final long CLOSE_STAGGER_MS = 25L;

    private enum MenuState { COLLAPSED, OPEN, CLOSING }

    private static final class HubItem {
        final LinearLayout container;
        final ImageView circle;
        final TextView label;
        final int iconRes;
        final Runnable action;

        HubItem(LinearLayout container, ImageView circle, TextView label, int iconRes, Runnable action) {
            this.container = container;
            this.circle = circle;
            this.label = label;
            this.iconRes = iconRes;
            this.action = action;
        }
    }

    private final Context context;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Tuş penceresinin kökü (yalnızca geri tuşu). */
    private FrameLayout floatingButton;
    private WindowManager.LayoutParams params;
    private boolean isShowing = false;
    private ImageView btnBack;

    /** Menü penceresinin kökü (tam ekran, saydam); yalnızca menü açıkken eklidir. */
    private FrameLayout menuRoot;
    private WindowManager.LayoutParams menuParams;
    /** Menü penceresinde, tuşun tam üstünde duran ✕. */
    private ImageView centerClose;
    private final List<HubItem> items = new ArrayList<>();

    private MenuState state = MenuState.COLLAPSED;
    private int cellPx;
    private int shadowPadPx;
    private int itemWidthPx;
    private int dragSlopPx;

    /** Geri tuşunun ekran konumu (menü açılırken sabitlenir). */
    private int backScreenX;
    private int backScreenY;

    // Dokunma durumu
    private boolean gestureIgnored;
    private boolean dragging;
    private boolean openedThisGesture;
    private View downTarget;
    private View pressedTarget;
    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;
    private Runnable longPressRunnable;
    private final Runnable finishCloseRunnable = this::finishClose;

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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> log(message));
            return;
        }
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

    // ---------------------------------------------------------------- konum

    private int buttonWindowSidePx() {
        return cellPx + 2 * shadowPadPx;
    }

    private DisplayMetrics screenMetrics() {
        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(dm);
        return dm;
    }

    /** Tuş penceresini ekranda tutar; gölge boşluğu görsel kenar payı olur. */
    private int[] clampButton(int x, int y) {
        DisplayMetrics dm = screenMetrics();
        int side = buttonWindowSidePx();
        x = Math.max(0, Math.min(x, dm.widthPixels - side));
        y = Math.max(0, Math.min(y, dm.heightPixels - side));
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
                .apply();
    }

    private void applyInitialPosition() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int x;
        int y;
        if (prefs.getBoolean(KEY_FLOATING_BACK_POS_SAVED, false)) {
            x = prefs.getInt(KEY_FLOATING_BACK_POS_X, 0);
            y = prefs.getInt(KEY_FLOATING_BACK_POS_Y, 0);
        } else {
            float density = context.getResources().getDisplayMetrics().density;
            DisplayMetrics dm = screenMetrics();
            x = dm.widthPixels - buttonWindowSidePx() - (int) (12 * density);
            y = dm.heightPixels - buttonWindowSidePx() - (int) (96 * density);
        }
        int[] c = clampButton(x, y);
        params.x = c[0];
        params.y = c[1];
    }

    // ---------------------------------------------------------------- görünüm

    private void applyCircleTheme(ImageView v, int iconRes) {
        if (v == null) {
            return;
        }
        FloatingOverlayBarSpec.applyHubCircleBackground(context, v, UiStyles.color(context, R.color.surfaceCard));
        v.setImageResource(iconRes);
        ImageViewCompat.setImageTintList(v, ColorStateList.valueOf(UiStyles.color(context, R.color.textPrimary)));
    }

    private void applyItemTheme(HubItem item) {
        FloatingOverlayBarSpec.applyHubCircleBackground(context, item.circle,
                UiStyles.color(context, R.color.surfaceCard));
        item.circle.setImageResource(item.iconRes);
        ImageViewCompat.setImageTintList(item.circle,
                ColorStateList.valueOf(UiStyles.color(context, R.color.accent)));
        item.label.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.applySolidRoundedBackgroundDp(item.label,
                ColorUtils.setAlphaComponent(UiStyles.color(context, R.color.surfaceCard), 0xEE), 10f);
    }

    private ImageView newCircle(int padFraction100) {
        ImageView v = new ImageView(context);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = cellPx * padFraction100 / 100;
        v.setPadding(pad, pad, pad, pad);
        return v;
    }

    private void addItem(FrameLayout root, int iconRes, int labelRes, Runnable action) {
        float d = context.getResources().getDisplayMetrics().density;
        LinearLayout c = new LinearLayout(context);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setClipChildren(false);
        c.setClipToPadding(false);

        ImageView circle = newCircle(26);
        c.addView(circle, new LinearLayout.LayoutParams(cellPx, cellPx));

        TextView label = new TextView(context);
        label.setText(labelRes);
        label.setTextSize(13f);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        int padH = (int) (8 * d);
        int padV = (int) (3 * d);
        label.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = (int) (6 * d);
        c.addView(label, labelLp);

        c.setVisibility(View.INVISIBLE);
        root.addView(c, new FrameLayout.LayoutParams(itemWidthPx, FrameLayout.LayoutParams.WRAP_CONTENT));

        HubItem item = new HubItem(c, circle, label, iconRes, action);
        applyItemTheme(item);
        items.add(item);
    }

    /** Çocuklar dokunmayı yutmasın; vuruş testi kökte ham koordinatla yapılır (iki pencere için ortak). */
    private FrameLayout newTouchRoot() {
        FrameLayout root = new FrameLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return true;
            }
        };
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setOnTouchListener((v, event) -> onRootTouch(event));
        return root;
    }

    public synchronized void show() {
        log("[INFO] Floating Back hub show() çağrıldı");

        if (!Settings.canDrawOverlays(context)) {
            log("[WARN] Floating Back: overlay izni yok");
            handler.post(() -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    Toast.makeText(context, R.string.overlay_permission_toast, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(context, context.getString(R.string.floating_back_permission_settings, e.getMessage()),
                            Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        if (isShowing && floatingButton != null && floatingButton.getParent() != null) {
            log("[DEBUG] Hub zaten gösteriliyor; servis/ayar tekrar çağrısı yoksayıldı");
            return;
        }

        cleanupExistingView();

        float density = context.getResources().getDisplayMetrics().density;
        cellPx = FloatingOverlayBarSpec.uniformCellSidePx(context);
        shadowPadPx = FloatingOverlayBarSpec.dpToPx(FloatingOverlayBarSpec.HUB_CIRCLE_ELEVATION_DP * 2f, density);
        itemWidthPx = Math.max((int) (cellPx * 1.7f), FloatingOverlayBarSpec.dpToPx(100f, density));
        dragSlopPx = Math.max(ViewConfiguration.get(context).getScaledTouchSlop(), (int) (10 * density));

        // Tuş penceresi
        FrameLayout root = newTouchRoot();
        btnBack = newCircle(24);
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(cellPx, cellPx);
        backLp.setMargins(shadowPadPx, shadowPadPx, shadowPadPx, shadowPadPx);
        root.addView(btnBack, backLp);
        applyCircleTheme(btnBack, R.drawable.ic_hub_arrow_left);
        floatingButton = root;

        // Menü penceresi (henüz eklenmez)
        menuRoot = newTouchRoot();
        items.clear();
        addItem(menuRoot, R.drawable.ic_hub_swap, R.string.floating_hub_item_pick_app,
                this::openTargetAppPickerFromOverlay);
        addItem(menuRoot, R.drawable.ic_hub_cast, R.string.floating_hub_item_cast,
                () -> startProjectionServiceAction(MapControlService.ACTION_BENCH_OPEN_CLUSTER, null));
        addItem(menuRoot, R.drawable.ic_hub_cast_off, R.string.floating_hub_item_cast_off,
                () -> startProjectionServiceAction(MapControlService.ACTION_BENCH_CLOSE_CLUSTER, false));
        addItem(menuRoot, R.drawable.ic_mdi_view_grid, R.string.floating_hub_item_apps,
                this::openRunningApps);
        centerClose = newCircle(24);
        centerClose.setVisibility(View.INVISIBLE);
        menuRoot.addView(centerClose, new FrameLayout.LayoutParams(cellPx, cellPx));
        applyCircleTheme(centerClose, R.drawable.ic_mdi_close);

        state = MenuState.COLLAPSED;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        applyInitialPosition();

        menuParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        menuParams.gravity = Gravity.TOP | Gravity.START;

        try {
            windowManager.addView(floatingButton, params);
            isShowing = true;
            registerThemeConfigCallback();
            log("[SUCCESS] Floating Back hub gösterildi");
        } catch (Exception e) {
            log("[ERROR] Floating Back hub gösterilemedi: " + e.getMessage());
            floatingButton = null;
            isShowing = false;
            handler.post(() ->
                    Toast.makeText(context, context.getString(R.string.floating_back_show_failed, e.getMessage()),
                            Toast.LENGTH_SHORT).show());
        }
    }

    // ---------------------------------------------------------------- dokunma

    private static boolean rawPointInsideView(float rawX, float rawY, View v) {
        if (v == null || v.getVisibility() != View.VISIBLE || !v.isAttachedToWindow()) {
            return false;
        }
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return rawX >= loc[0] && rawX < loc[0] + v.getWidth()
                && rawY >= loc[1] && rawY < loc[1] + v.getHeight();
    }

    private View hitTest(float rawX, float rawY) {
        if (state == MenuState.OPEN) {
            for (HubItem item : items) {
                if (rawPointInsideView(rawX, rawY, item.circle) || rawPointInsideView(rawX, rawY, item.label)) {
                    return item.container;
                }
            }
            if (rawPointInsideView(rawX, rawY, centerClose)) {
                return centerClose;
            }
            return null;
        }
        if (rawPointInsideView(rawX, rawY, btnBack)) {
            return btnBack;
        }
        return null;
    }

    private HubItem itemFor(View v) {
        if (v == null) {
            return null;
        }
        for (HubItem item : items) {
            if (item.container == v) {
                return item;
            }
        }
        return null;
    }

    private void setPressedTarget(View v) {
        if (pressedTarget == v) {
            return;
        }
        if (pressedTarget != null) {
            pressVisual(pressedTarget, false);
        }
        pressedTarget = v;
        if (v != null) {
            pressVisual(v, true);
        }
    }

    private void pressVisual(View target, boolean on) {
        if (target == btnBack || target == centerClose) {
            target.setPressed(on);
            return;
        }
        HubItem item = itemFor(target);
        if (item == null) {
            return;
        }
        item.circle.setPressed(on);
        float s = on ? 1.12f : 1f;
        item.circle.animate().scaleX(s).scaleY(s).setDuration(120).start();
    }

    /**
     * İki pencerenin ortak dokunma işleyicisi. Basılı tutup açılan menüde parmak hâlâ tuş penceresindedir; bu
     * hareket o pencereye gelmeye devam eder, menü öğeleri ham koordinatla bulunur (kaydırıp bırak).
     */
    private boolean onRootTouch(MotionEvent event) {
        float rx = event.getRawX();
        float ry = event.getRawY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gestureIgnored = state == MenuState.CLOSING;
                if (gestureIgnored) {
                    return true;
                }
                initialX = params.x;
                initialY = params.y;
                initialTouchX = rx;
                initialTouchY = ry;
                dragging = false;
                openedThisGesture = false;
                downTarget = hitTest(rx, ry);
                cancelLongPress();
                if (state == MenuState.COLLAPSED && downTarget == btnBack) {
                    scheduleLongPress();
                }
                setPressedTarget(downTarget);
                return true;

            case MotionEvent.ACTION_MOVE: {
                if (gestureIgnored) {
                    return true;
                }
                int dx = (int) (rx - initialTouchX);
                int dy = (int) (ry - initialTouchY);
                if (state == MenuState.COLLAPSED) {
                    if (!dragging && (Math.abs(dx) > dragSlopPx || Math.abs(dy) > dragSlopPx)) {
                        dragging = true;
                        cancelLongPress();
                        setPressedTarget(null);
                    }
                    if (dragging) {
                        int[] c = clampButton(initialX + dx, initialY + dy);
                        params.x = c[0];
                        params.y = c[1];
                        try {
                            windowManager.updateViewLayout(floatingButton, params);
                        } catch (Exception e) {
                            log("[ERROR] Hub updateViewLayout: " + e.getMessage());
                        }
                    }
                } else if (state == MenuState.OPEN) {
                    View t = hitTest(rx, ry);
                    setPressedTarget(openedThisGesture || t == downTarget ? t : null);
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                if (gestureIgnored) {
                    return true;
                }
                cancelLongPress();
                View t = hitTest(rx, ry);
                setPressedTarget(null);
                if (dragging) {
                    dragging = false;
                    saveButtonPosition();
                    return true;
                }
                if (state == MenuState.COLLAPSED) {
                    if (t == btnBack && downTarget == btnBack) {
                        btnBack.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        animateTap(btnBack);
                        requestBack();
                    }
                } else if (state == MenuState.OPEN) {
                    HubItem item = itemFor(t);
                    if (item != null && (openedThisGesture || t == downTarget)) {
                        item.circle.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        closeMenu();
                        item.action.run();
                    } else if (item == null && !openedThisGesture && t == downTarget) {
                        // Ortadaki ✕ ya da boş alana dokunuş.
                        closeMenu();
                    }
                }
                openedThisGesture = false;
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                cancelLongPress();
                setPressedTarget(null);
                if (dragging) {
                    dragging = false;
                    saveButtonPosition();
                }
                openedThisGesture = false;
                return true;

            default:
                return false;
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
            longPressRunnable = null;
            if (dragging || state != MenuState.COLLAPSED || btnBack == null) {
                return;
            }
            openedThisGesture = true;
            btnBack.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            setPressedTarget(null);
            openMenu();
            log("[INFO] Yüzen menü: uzun basış ile açıldı");
        };
        handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
    }

    // ---------------------------------------------------------------- halka menü

    private void openMenu() {
        if (state != MenuState.COLLAPSED || menuRoot == null || btnBack == null) {
            return;
        }
        int[] loc = new int[2];
        btnBack.getLocationOnScreen(loc);
        backScreenX = loc[0];
        backScreenY = loc[1];
        for (HubItem item : items) {
            item.container.animate().cancel();
            item.container.setVisibility(View.INVISIBLE);
        }
        centerClose.setVisibility(View.INVISIBLE);
        try {
            if (menuRoot.getParent() == null) {
                windowManager.addView(menuRoot, menuParams);
            }
        } catch (Exception e) {
            log("[ERROR] Hub menü açma: " + e.getMessage());
            return;
        }
        state = MenuState.OPEN;
        final FrameLayout root = menuRoot;
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                if (state == MenuState.OPEN && root == menuRoot) {
                    layoutAndAnimateOpen();
                }
                return true;
            }
        });
    }

    /** Menü penceresi yerleştikten sonra (ilk çizimden önce): ✕'i tuşun üstüne koy, öğeleri yaya aç. */
    private void layoutAndAnimateOpen() {
        FrameLayout root = menuRoot;
        int[] r = new int[2];
        root.getLocationOnScreen(r);
        float bx = backScreenX - r[0];
        float by = backScreenY - r[1];
        centerClose.setTranslationX(bx);
        centerClose.setTranslationY(by);
        centerClose.setVisibility(View.VISIBLE);
        centerClose.setRotation(-90f);
        centerClose.animate().rotation(0f).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();

        int w = root.getWidth();
        int h = root.getHeight();
        float bcx = bx + cellPx / 2f;
        float bcy = by + cellPx / 2f;

        // Yay ekranın ortasına bakar; kenar/köşedeki tuşta öğeler ekranın içine açılır.
        double toCenterX = w / 2f - bcx;
        double toCenterY = h / 2f - bcy;
        double base = Math.hypot(toCenterX, toCenterY) < cellPx
                ? -Math.PI / 2 : Math.atan2(toCenterY, toCenterX);
        float density = context.getResources().getDisplayMetrics().density;
        double chord = itemWidthPx + 4 * density;
        float radius = (float) Math.max(cellPx * 1.9f, chord / (2 * Math.sin(ITEM_STEP_RAD / 2)));
        int margin = (int) (8 * density);
        int n = items.size();

        for (int i = 0; i < n; i++) {
            View c = items.get(i).container;
            c.measure(View.MeasureSpec.makeMeasureSpec(itemWidthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int ih = c.getMeasuredHeight();
            double a = base + (i - (n - 1) / 2.0) * ITEM_STEP_RAD;
            float cx = bcx + (float) (radius * Math.cos(a));
            float cy = bcy + (float) (radius * Math.sin(a));
            float tx = clamp(cx - itemWidthPx / 2f, margin, w - itemWidthPx - margin);
            float ty = clamp(cy - cellPx / 2f, margin, h - ih - margin);

            c.animate().cancel();
            c.setPivotX(itemWidthPx / 2f);
            c.setPivotY(cellPx / 2f);
            c.setTranslationX(bcx - itemWidthPx / 2f);
            c.setTranslationY(bcy - cellPx / 2f);
            c.setScaleX(0.3f);
            c.setScaleY(0.3f);
            c.setAlpha(0f);
            c.setVisibility(View.VISIBLE);
            c.animate()
                    .translationX(tx)
                    .translationY(ty)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setStartDelay(i * OPEN_STAGGER_MS)
                    .setDuration(OPEN_DURATION_MS)
                    .setInterpolator(new OvershootInterpolator(1.3f))
                    .start();
        }
    }

    private static float clamp(float v, float min, float max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(v, max));
    }

    private void closeMenu() {
        if (state != MenuState.OPEN) {
            return;
        }
        state = MenuState.CLOSING;
        float bcx = centerClose.getTranslationX() + cellPx / 2f;
        float bcy = centerClose.getTranslationY() + cellPx / 2f;
        int n = items.size();
        for (int i = 0; i < n; i++) {
            View c = items.get(i).container;
            c.animate().cancel();
            c.animate()
                    .translationX(bcx - itemWidthPx / 2f)
                    .translationY(bcy - cellPx / 2f)
                    .scaleX(0.3f)
                    .scaleY(0.3f)
                    .alpha(0f)
                    .setStartDelay((n - 1 - i) * CLOSE_STAGGER_MS)
                    .setDuration(CLOSE_DURATION_MS)
                    .setInterpolator(new AccelerateInterpolator())
                    .start();
        }
        // ✕ dönerek kaybolur; altındaki gerçek tuş (◀) görünür kalır.
        centerClose.animate().cancel();
        centerClose.animate().rotation(90f).alpha(0f).setDuration(CLOSE_DURATION_MS).start();

        handler.removeCallbacks(finishCloseRunnable);
        handler.postDelayed(finishCloseRunnable, CLOSE_DURATION_MS + (n - 1) * CLOSE_STAGGER_MS + 30);
    }

    /** Menü penceresini kaldırır; tuş penceresine dokunulmaz. */
    private void finishClose() {
        handler.removeCallbacks(finishCloseRunnable);
        state = MenuState.COLLAPSED;
        for (HubItem item : items) {
            item.container.animate().cancel();
            item.container.setVisibility(View.INVISIBLE);
            item.circle.setPressed(false);
            item.circle.setScaleX(1f);
            item.circle.setScaleY(1f);
        }
        if (centerClose != null) {
            centerClose.animate().cancel();
            centerClose.setVisibility(View.INVISIBLE);
            centerClose.setAlpha(1f);
            centerClose.setRotation(0f);
            centerClose.setPressed(false);
        }
        if (menuRoot != null && menuRoot.getParent() != null) {
            try {
                windowManager.removeViewImmediate(menuRoot);
            } catch (Exception e) {
                log("[ERROR] Hub menü kapatma: " + e.getMessage());
            }
        }
    }

    private void animateTap(View v) {
        v.animate().cancel();
        v.setScaleX(0.86f);
        v.setScaleY(0.86f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                .setInterpolator(new OvershootInterpolator(2f)).start();
    }

    // ---------------------------------------------------------------- eylemler

    private void openTargetAppPickerFromOverlay() {
        try {
            TargetAppPickerOverlay.show(context, this::log);
        } catch (Exception e) {
            log("[ERROR] Hedef uygulama overlay: " + e.getMessage());
            Toast.makeText(context, context.getString(R.string.target_picker_picker_failed, e.getMessage()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void startProjectionServiceAction(String action, Boolean clusterCloseSendBackground) {
        try {
            Intent i = new Intent(context, MapControlService.class).setAction(action);
            if (clusterCloseSendBackground != null
                    && MapControlService.ACTION_BENCH_CLOSE_CLUSTER.equals(action)) {
                i.putExtra(MapControlService.EXTRA_CLUSTER_CLOSE_SEND_BACKGROUND, clusterCloseSendBackground);
            }
            context.startForegroundService(i);
            log("[INFO] Yüzen yansıtma: servis " + action);
        } catch (Exception e) {
            log("[ERROR] Yüzen yansıtma servis: " + e.getMessage());
            Toast.makeText(context, context.getString(R.string.target_picker_service_failed, e.getMessage()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openRunningApps() {
        try {
            DisplayRunningAppsOverlay.show(context, this::log);
        } catch (Exception e) {
            log("[ERROR] Açık uygulamalar: " + e.getMessage());
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Sistem geri. Öndeki uygulamayı dumpsys ile bulmaya gerek yok: erişilebilirlik BACK'i odaktaki pencereye
     * (MapControl dahil) gider. Servis bağlı değilse ve MapControl öndeyse kendi içinde geri yapılır.
     */
    private void requestBack() {
        if (GlobalBackService.performBackAction()) {
            log("[SUCCESS] Floating Back: BACK gönderildi (AccessibilityService)");
            return;
        }
        if (isOwnUiInForeground() && com.mapcontrol.ui.activity.MainActivity.dispatchOverlayBack()) {
            log("[SUCCESS] Floating Back: uygulama içinde geri");
            return;
        }
        if (GlobalBackService.isRegisteredInSystemAccessibilitySettings(context)) {
            log("[INFO] Floating Back: erişilebilirlik açık, servis bağlantısı kısa sürede deneniyor");
            handler.postDelayed(() -> {
                if (GlobalBackService.performBackAction()) {
                    log("[SUCCESS] Floating Back: BACK (gecikmeli bağlantı sonrası)");
                } else {
                    Toast.makeText(context, R.string.floating_back_a11y_not_ready, Toast.LENGTH_LONG).show();
                }
            }, A11Y_RETRY_MS);
            return;
        }
        log("[WARN] Floating Back: GlobalBackService ayarlarda kapalı, yönlendiriliyor");
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Toast.makeText(context, R.string.floating_back_a11y_required, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            log("[ERROR] Floating Back: erişilebilirlik ayarlarına gidilemedi - " + e.getMessage());
        }
    }

    /** Kendi sürecimizin önemi: yalnızca bir MapControl ekranı üstteyken FOREGROUND olur (overlay/servis değil). */
    private static boolean isOwnUiInForeground() {
        ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(info);
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
    }

    // ---------------------------------------------------------------- tema / yaşam döngüsü

    private void unregisterThemeConfigCallback() {
        if (mThemeConfigCallback != null) {
            try {
                context.unregisterComponentCallbacks(mThemeConfigCallback);
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
                handler.post(FloatingBackButtonManager.this::onConfigChanged);
            }

            @Override
            public void onLowMemory() {
            }

            @Override
            public void onTrimMemory(int level) {
            }
        };
        context.registerComponentCallbacks(mThemeConfigCallback);
    }

    /** Tema (gece/gündüz) ve ekran boyutu değişimi: renkleri yenile, menüyü kapat, konumu ekrana sığdır. */
    private void onConfigChanged() {
        if (floatingButton == null || !isShowing) {
            return;
        }
        finishClose();
        applyCircleTheme(btnBack, R.drawable.ic_hub_arrow_left);
        applyCircleTheme(centerClose, R.drawable.ic_mdi_close);
        for (HubItem item : items) {
            applyItemTheme(item);
        }
        int[] c = clampButton(params.x, params.y);
        if (c[0] != params.x || c[1] != params.y) {
            params.x = c[0];
            params.y = c[1];
            try {
                windowManager.updateViewLayout(floatingButton, params);
            } catch (Exception e) {
                log("[ERROR] Hub konum düzeltme: " + e.getMessage());
            }
            saveButtonPosition();
        }
    }

    private void cleanupExistingView() {
        cancelLongPress();
        finishClose();
        unregisterThemeConfigCallback();
        pressedTarget = null;
        downTarget = null;
        dragging = false;
        if (floatingButton != null) {
            try {
                windowManager.removeView(floatingButton);
            } catch (Exception ignored) {
            }
            floatingButton = null;
        }
        items.clear();
        menuRoot = null;
        centerClose = null;
        btnBack = null;
        state = MenuState.COLLAPSED;
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

    public static void saveEnabledState(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_FLOATING_BACK_BUTTON_ENABLED, enabled).apply();
    }

    public static boolean loadEnabledState(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_FLOATING_BACK_BUTTON_ENABLED, true);
    }
}
