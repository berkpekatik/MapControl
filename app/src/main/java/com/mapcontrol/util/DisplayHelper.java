package com.mapcontrol.util;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;

import java.util.ArrayList;
import java.util.List;
import com.mapcontrol.ui.theme.UiStyles;

public final class DisplayHelper {
    private static View preparingMessageView = null;
    private static android.view.WindowManager preparingWindowManager = null;
    private static android.view.WindowManager.LayoutParams preparingParams = null;
    private static Context displayContext = null;
    private static final List<android.animation.Animator> preparingAnimators = new ArrayList<>();

    /** Boot splash — açılışta cluster ekranını siyah bırakmamak için kalıcı, harita temalı overlay. */
    private static View bootSplashView = null;
    private static android.view.WindowManager bootSplashWindowManager = null;
    private static android.view.WindowManager.LayoutParams bootSplashParams = null;
    private static Context bootSplashDisplayContext = null;
    /** {@link Display#INVALID_DISPLAY} when splash kapalı. */
    private static int bootSplashDisplayId = Display.INVALID_DISPLAY;
    /** Cluster ekranındaki boot splash animasyonları. */
    private static final List<android.animation.Animator> bootSplashAnimators = new ArrayList<>();
    /**
     * Splash giriş animasyonunun oynayacağı süre; sonrasında tüm sonsuz animatörler iptal edilir ve
     * overlay son karede sabit kalır. Cluster splash'i projeksiyon açılana kadar (araçta 23 dk görüldü)
     * ekranda durabildiği için sürekli çizim RenderThread/GPU'yu boşuna meşgul ediyordu.
     */
    private static final long SPLASH_ANIMATION_MAX_MS = 4_000L;
    private static final int SPLASH_LOGO_RADIUS_DP = 60;
    private static final int SPLASH_LOGO_LIFT_DP = 44;
    private static final int SPLASH_TEXT_DROP_DP = 76;
    /** Uygulama içi açılış/onboarding splash animasyonları (cluster'dan ayrı tutulur). */
    private static final List<android.animation.Animator> appSplashAnimators = new ArrayList<>();

    private DisplayHelper() {}

    public static void showPreparingMessageOnDisplay(Context context, int displayId) {
        try {
            hidePreparingMessage();

            DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager == null) {
                return;
            }

            Display targetDisplay = null;
            Display[] displays = displayManager.getDisplays();
            for (Display display : displays) {
                if (display.getDisplayId() == displayId) {
                    targetDisplay = display;
                    break;
                }
            }

            if (targetDisplay == null) {
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                displayContext = context.createDisplayContext(targetDisplay);
            } else {
                displayContext = context;
            }

            preparingWindowManager = (android.view.WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);
            if (preparingWindowManager == null) {
                return;
            }

            View overlay = buildPreparingOverlay(displayContext);

            preparingParams = new android.view.WindowManager.LayoutParams(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT
            );

            preparingMessageView = overlay;
            preparingWindowManager.addView(preparingMessageView, preparingParams);

            startPreparingAnimations(overlay);
        } catch (Exception ignored) {
        }
    }

    private static View buildPreparingOverlay(Context ctx) {
        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(UiStyles.color(ctx, R.color.preparing_overlay_root));

        LinearLayout card = new LinearLayout(ctx);
        card.setId(R.id.preparing_card);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_card_glass));
        int padH = dp(ctx, 36);
        int padV = dp(ctx, 32);
        card.setPadding(padH, padV, padH, padV);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.CENTER;
        card.setLayoutParams(cardLp);

        FrameLayout spinnerWrap = new FrameLayout(ctx);
        int wrapSize = dp(ctx, 72);
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(wrapSize, wrapSize);
        spinnerLp.gravity = Gravity.CENTER_HORIZONTAL;
        spinnerWrap.setLayoutParams(spinnerLp);

        ImageView halo = new ImageView(ctx);
        halo.setId(R.id.preparing_halo);
        halo.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.bg_global_wifi_toast_halo_preparing));
        FrameLayout.LayoutParams haloLp = new FrameLayout.LayoutParams(wrapSize, wrapSize);
        haloLp.gravity = Gravity.CENTER;
        spinnerWrap.addView(halo, haloLp);

        ImageView spinner = new ImageView(ctx);
        spinner.setId(R.id.preparing_spinner);
        spinner.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.ic_mdi_loading));
        spinner.setColorFilter(UiStyles.color(ctx, R.color.accentColor));
        int spinnerSize = dp(ctx, 40);
        FrameLayout.LayoutParams spLp = new FrameLayout.LayoutParams(spinnerSize, spinnerSize);
        spLp.gravity = Gravity.CENTER;
        spinnerWrap.addView(spinner, spLp);

        card.addView(spinnerWrap);

        TextView title = new TextView(ctx);
        title.setId(R.id.preparing_title);
        title.setText(R.string.splash_preparing_title);
        title.setTextColor(UiStyles.color(ctx, R.color.textPrimary));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        UiStyles.setFontStyle(title, Typeface.BOLD);
        title.setLetterSpacing(0.02f);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(ctx, 22);
        title.setLayoutParams(titleLp);
        card.addView(title);

        TextView sub = new TextView(ctx);
        sub.setText(R.string.splash_preparing_sub);
        sub.setTextColor(UiStyles.color(ctx, R.color.textSecondaryCool));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(ctx, 6);
        sub.setLayoutParams(subLp);
        card.addView(sub);

        LinearLayout dots = new LinearLayout(ctx);
        dots.setId(R.id.preparing_dots);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dotsLp.topMargin = dp(ctx, 18);
        dots.setLayoutParams(dotsLp);

        int dotSize = dp(ctx, 7);
        int dotGap = dp(ctx, 6);
        int accent = UiStyles.color(ctx, R.color.accentColor);
        for (int i = 0; i < 3; i++) {
            View dot = new View(ctx);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            bg.setColor(accent);
            dot.setBackground(bg);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
            if (i > 0) dotLp.leftMargin = dotGap;
            dot.setLayoutParams(dotLp);
            dot.setAlpha(0.25f);
            dots.addView(dot);
        }
        card.addView(dots);

        root.addView(card);
        return root;
    }

    private static void startPreparingAnimations(View root) {
        // Önceki animasyonlar durdurulmadan liste temizlenirse sahipsiz kalıp sonsuza dek çalışırlar.
        cancelPreparingAnimations();

        View card = root.findViewById(R.id.preparing_card);
        View spinner = root.findViewById(R.id.preparing_spinner);
        View halo = root.findViewById(R.id.preparing_halo);
        View dotsGroup = root.findViewById(R.id.preparing_dots);

        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(220).start();

        if (card != null) {
            card.setAlpha(0f);
            card.setTranslationY(dp(root.getContext(), 12));
            card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(60)
                    .setDuration(320)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }

        if (spinner != null) {
            ObjectAnimator rotate = ObjectAnimator.ofFloat(spinner, View.ROTATION, 0f, 360f);
            rotate.setDuration(900);
            rotate.setInterpolator(new LinearInterpolator());
            rotate.setRepeatCount(ValueAnimator.INFINITE);
            rotate.start();
            preparingAnimators.add(rotate);
        }

        if (halo != null) {
            ObjectAnimator haloScaleX = ObjectAnimator.ofFloat(halo, View.SCALE_X, 1.0f, 1.08f);
            ObjectAnimator haloScaleY = ObjectAnimator.ofFloat(halo, View.SCALE_Y, 1.0f, 1.08f);
            ObjectAnimator haloAlpha = ObjectAnimator.ofFloat(halo, View.ALPHA, 0.85f, 1.0f);
            for (ObjectAnimator a : new ObjectAnimator[]{haloScaleX, haloScaleY, haloAlpha}) {
                a.setDuration(1100);
                a.setInterpolator(new AccelerateDecelerateInterpolator());
                a.setRepeatCount(ValueAnimator.INFINITE);
                a.setRepeatMode(ValueAnimator.REVERSE);
            }
            AnimatorSet haloSet = new AnimatorSet();
            haloSet.playTogether(haloScaleX, haloScaleY, haloAlpha);
            haloSet.start();
            preparingAnimators.add(haloSet);
        }

        if (dotsGroup instanceof LinearLayout) {
            LinearLayout dots = (LinearLayout) dotsGroup;
            for (int i = 0; i < dots.getChildCount(); i++) {
                View dot = dots.getChildAt(i);
                ObjectAnimator alpha = ObjectAnimator.ofFloat(dot, View.ALPHA, 0.25f, 1f);
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(dot, View.SCALE_X, 0.85f, 1.15f);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(dot, View.SCALE_Y, 0.85f, 1.15f);
                for (ObjectAnimator a : new ObjectAnimator[]{alpha, scaleX, scaleY}) {
                    a.setDuration(520);
                    a.setStartDelay(i * 160L);
                    a.setRepeatCount(ValueAnimator.INFINITE);
                    a.setRepeatMode(ValueAnimator.REVERSE);
                    a.setInterpolator(new AccelerateDecelerateInterpolator());
                }
                AnimatorSet set = new AnimatorSet();
                set.playTogether(alpha, scaleX, scaleY);
                set.start();
                preparingAnimators.add(set);
            }
        }
    }

    private static int dp(Context ctx, int value) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    public static void hidePreparingMessage() {
        // Eski sürümde finally alanları hemen null'luyordu; solma sonundaki removeView null görüp hiçbir şey
        // kaldırmıyordu: her "Göstergeye yansıt" display 2'de bir overlay sızdırıyordu. Görünüm yerel olarak
        // tutulup alanlar hemen boşaltılır; solma biten görünümü kendisi kaldırır.
        final View view = preparingMessageView;
        final android.view.WindowManager wm = preparingWindowManager;
        try {
            cancelPreparingAnimations();
            if (view != null && wm != null) {
                fadeAndRemove(view, wm, 180);
            }
        } catch (Exception ignored) {
        } finally {
            cancelPreparingAnimations();
            preparingMessageView = null;
            preparingWindowManager = null;
            preparingParams = null;
            displayContext = null;
        }
    }

    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    private static boolean onMainThread() {
        return android.os.Looper.myLooper() == android.os.Looper.getMainLooper();
    }

    /**
     * Görünümü soldurup kaldırır; solma sonu çalışmazsa (iptal, görünür değil) süre + 150 ms sonra yine kaldırılır.
     */
    private static void fadeAndRemove(final View view, final android.view.WindowManager wm, long ms) {
        final boolean[] done = {false};
        final Runnable remove = () -> {
            if (done[0]) {
                return;
            }
            try {
                wm.removeViewImmediate(view);
                done[0] = true;
            } catch (Exception ignored) {
                // Başarısızsa işaretleme: diğer yol (animasyon sonu / gecikmeli yedek) yeniden denesin.
            }
        };
        try {
            view.animate().alpha(0f).setDuration(ms).withEndAction(remove).start();
        } catch (Exception e) {
            remove.run();
            return;
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(remove, ms + 150);
    }

    private static void cancelPreparingAnimations() {
        for (android.animation.Animator a : preparingAnimators) {
            try {
                a.cancel();
            } catch (Exception ignored) {
            }
        }
        preparingAnimators.clear();
    }

    // --- Boot Splash ----------------------------------------------------------------------------

    private static volatile boolean bootSplashBlockedForInstall;

    /**
     * Uygulama kurulumu süreci öldürmeden önce gösterge çizimini solmadan kaldırır ve kaldırılana kadar bekler; çizim
     * {@link #allowBootSplashAfterInstall()} çağrılana kadar yeniden gösterilmez. Bloklar: ana thread'den çağırma.
     *
     * @return çizim açıktı ve kaldırıldıysa true
     */
    public static boolean removeBootSplashForInstall(long timeoutMs) throws InterruptedException {
        bootSplashBlockedForInstall = true;
        final boolean[] removed = {false};
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                cancelBootSplashAnimations();
                View view = bootSplashView;
                android.view.WindowManager wm = bootSplashWindowManager;
                bootSplashView = null;
                bootSplashWindowManager = null;
                bootSplashParams = null;
                bootSplashDisplayContext = null;
                bootSplashDisplayId = Display.INVALID_DISPLAY;
                if (view != null && wm != null) {
                    wm.removeViewImmediate(view);
                    removed[0] = true;
                }
            } catch (Exception ignored) {
            } finally {
                done.countDown();
            }
        });
        done.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        return removed[0];
    }

    public static void allowBootSplashAfterInstall() {
        bootSplashBlockedForInstall = false;
    }

    /**
     * Cluster (secondary) ekrana boot/welcome overlay'i bastırır. Kullanıcı uygulamadan ekran
     * yansıtana kadar oradaki siyahlığı doldurur. {@link #hideBootSplash()} çağrılana kadar kalır.
     */
    public static void showBootSplashOnDisplay(Context context, int displayId) {
        showBootSplashOnDisplay(context, displayId, false);
    }

    /**
     * @param evenIfProjectionOpen true yalnız göstergede ön planda uygulama olmadığı doğrulanınca ("Ana Ekrana Al"
     *                             sonrası boş gösterge); diğer yollar yansıtma açıkken splash basmaz.
     */
    public static void showBootSplashOnDisplay(Context context, int displayId, boolean evenIfProjectionOpen) {
        // Pencere hep ana thread'de eklenip kaldırılır; başka thread'de eklenen görünümü ana thread kaldıramaz
        // (removeViewImmediate hata verir, yakalanır) ve splash göstergede sahipsiz kalır.
        if (!onMainThread()) {
            MAIN.post(() -> showBootSplashOnDisplay(context, displayId, evenIfProjectionOpen));
            return;
        }
        // Yansıtma açıkken splash, göstergedeki uygulamanın (ör. Yandex) üstünü örter: harita arkada çalışır ama
        // görünmez, ana ekranda "Aç" da uygulamayı göstergede öne getirdiği için hiçbir şey olmaz gibi görünür.
        if (!evenIfProjectionOpen && ClusterNavigationState.getLastKnownOpen()) {
            return;
        }
        try {
            if (bootSplashView != null || bootSplashBlockedForInstall) {
                return;
            }
            DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager == null) {
                return;
            }
            Display targetDisplay = null;
            for (Display display : displayManager.getDisplays()) {
                if (display.getDisplayId() == displayId) {
                    targetDisplay = display;
                    break;
                }
            }
            if (targetDisplay == null) {
                return;
            }

            bootSplashDisplayId = displayId;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                Configuration cfg = new Configuration(
                        context.getApplicationContext().getResources().getConfiguration());
                bootSplashDisplayContext = context.getApplicationContext()
                        .createDisplayContext(targetDisplay)
                        .createConfigurationContext(cfg);
            } else {
                bootSplashDisplayContext = context;
            }

            bootSplashWindowManager = (android.view.WindowManager) bootSplashDisplayContext.getSystemService(Context.WINDOW_SERVICE);
            if (bootSplashWindowManager == null) {
                return;
            }

            View overlay = buildBootSplashOverlay(bootSplashDisplayContext);

            bootSplashParams = new android.view.WindowManager.LayoutParams(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT
            );

            bootSplashWindowManager.addView(overlay, bootSplashParams);
            bootSplashView = overlay;

            startBootSplashAnimations(overlay, bootSplashAnimators);
        } catch (Exception ignored) {
            // Eklenemediyse alan dolu kalmasın; yoksa sonraki gösterimler erken döner.
            bootSplashView = null;
            bootSplashWindowManager = null;
            bootSplashParams = null;
            bootSplashDisplayContext = null;
            bootSplashDisplayId = Display.INVALID_DISPLAY;
        }
    }

    public static void hideBootSplash() {
        if (!onMainThread()) {
            MAIN.post(DisplayHelper::hideBootSplash);
            return;
        }
        try {
            cancelBootSplashAnimations();
            if (bootSplashView != null && bootSplashWindowManager != null) {
                // Görünüm yerelde soluyup kaldırılır; alanlar hemen boşalır (solma sırasında yeniden gösterme
                // erken dönmesin, solma sonu yeni splash'i kaldırmasın).
                fadeAndRemove(bootSplashView, bootSplashWindowManager, 260);
                bootSplashView = null;
                bootSplashWindowManager = null;
                bootSplashParams = null;
                bootSplashDisplayContext = null;
                bootSplashDisplayId = Display.INVALID_DISPLAY;
            }
        } catch (Exception ignored) {
        } finally {
            cancelBootSplashAnimations();
        }
    }

    /**
     * Sistem gündüz/gece (veya diğer) yapılandırması değişince boot splash hâlâ gösteriliyorsa
     * güncel {@link Configuration} ile yeniden kurulur; renkler {@code values}/{@code values-night} ile uyumlanır.
     */
    public static void refreshBootSplashAfterConfigurationChange(Context applicationContext) {
        if (bootSplashView == null || bootSplashParams == null) {
            return;
        }
        if (ClusterNavigationState.getLastKnownOpen()) {
            // Yansıtma açıkken kalmış bir splash varsa yenilemek yerine kaldır.
            hideBootSplash();
            return;
        }
        if (bootSplashDisplayId == Display.INVALID_DISPLAY) {
            return;
        }
        try {
            Context app = applicationContext.getApplicationContext();
            DisplayManager displayManager = (DisplayManager) app.getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager == null) {
                return;
            }
            Display targetDisplay = null;
            for (Display display : displayManager.getDisplays()) {
                if (display.getDisplayId() == bootSplashDisplayId) {
                    targetDisplay = display;
                    break;
                }
            }
            if (targetDisplay == null) {
                return;
            }

            cancelBootSplashAnimations();
            try {
                bootSplashWindowManager.removeView(bootSplashView);
            } catch (Exception ignored) {
            }
            bootSplashView = null;
            bootSplashWindowManager = null;
            bootSplashDisplayContext = null;

            Configuration cfg = new Configuration(app.getResources().getConfiguration());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                bootSplashDisplayContext = app.createDisplayContext(targetDisplay).createConfigurationContext(cfg);
            } else {
                bootSplashDisplayContext = app;
            }
            bootSplashWindowManager = (android.view.WindowManager) bootSplashDisplayContext.getSystemService(
                    Context.WINDOW_SERVICE);
            if (bootSplashWindowManager == null) {
                bootSplashDisplayId = Display.INVALID_DISPLAY;
                bootSplashParams = null;
                return;
            }

            View overlay = buildBootSplashOverlay(bootSplashDisplayContext);
            bootSplashView = overlay;
            bootSplashWindowManager.addView(bootSplashView, bootSplashParams);
            startBootSplashAnimations(overlay, bootSplashAnimators);
        } catch (Exception ignored) {
            bootSplashDisplayId = Display.INVALID_DISPLAY;
            bootSplashParams = null;
            bootSplashDisplayContext = null;
            bootSplashWindowManager = null;
            bootSplashView = null;
        }
    }

    public static boolean isBootSplashShowing() {
        return bootSplashView != null;
    }

    /**
     * MainActivity cold start — cluster overlay'den bağımsız uygulama açılış splash'i.
     * Logo + harita grid + light/dark gradient (mevcut boot splash görseli).
     */
    public static View createAppLaunchSplashView(Context ctx) {
        View overlay = buildBootSplashOverlay(ctx);
        TextView tagline = overlay.findViewById(R.id.boot_splash_tagline);
        if (tagline != null) {
            tagline.setText(R.string.launch_splash_loading);
        }
        startBootSplashAnimations(overlay, appSplashAnimators);
        return overlay;
    }

    /** Uygulama splash animasyonlarını durdurur (cluster splash ayrı kalabilir). */
    public static void stopAppLaunchSplashAnimations() {
        cancelAll(appSplashAnimators);
    }

    private static View buildBootSplashOverlay(Context ctx) {
        FrameLayout root = new FrameLayout(ctx);
        UiStyles.setRootBackground(root);

        // Logonun arkasında çok hafif ışık (gece en parlak piksel içerik kalsın)
        View halo = new View(ctx);
        halo.setId(R.id.boot_splash_halo);
        android.content.res.Resources res = ctx.getResources();
        halo.setBackground(new RadialHaloDrawable(
                UiStyles.color(ctx, R.color.oemAccent),
                UiStyles.color(ctx, R.color.transparent),
                res.getInteger(R.integer.boot_splash_halo_center_alpha),
                res.getInteger(R.integer.boot_splash_halo_mid_alpha)));
        int haloSize = dp(ctx, 420);
        FrameLayout.LayoutParams haloLp = new FrameLayout.LayoutParams(haloSize, haloSize);
        haloLp.gravity = Gravity.CENTER;
        haloLp.bottomMargin = 2 * dp(ctx, SPLASH_LOGO_LIFT_DP);
        root.addView(halo, haloLp);

        FrameLayout center = new FrameLayout(ctx);
        center.setId(R.id.boot_splash_center);

        // Radar halkaları ve blip
        RadarBlipView radar = new RadarBlipView(ctx);
        radar.setId(R.id.boot_splash_bar);
        center.addView(radar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        ImageView logo = new ImageView(ctx);
        logo.setId(R.id.boot_splash_logo);
        Drawable launcher = null;
        try {
            String pkg = ctx.getPackageName();
            launcher = ctx.getPackageManager().getApplicationIcon(pkg);
        } catch (Exception ignored) {
        }
        if (launcher == null) {
            launcher = ContextCompat.getDrawable(ctx, R.mipmap.ic_launcher);
        }
        logo.setImageDrawable(launcher);
        logo.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        logo.setClipToOutline(true);
        int logoSize = 2 * dp(ctx, SPLASH_LOGO_RADIUS_DP);
        FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(logoSize, logoSize);
        logoLp.gravity = Gravity.CENTER;
        logoLp.bottomMargin = 2 * dp(ctx, SPLASH_LOGO_LIFT_DP);
        center.addView(logo, logoLp);

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView wordmark = new TextView(ctx);
        wordmark.setId(R.id.boot_splash_wordmark);
        wordmark.setText("MapControl");
        wordmark.setTextColor(UiStyles.color(ctx, R.color.textPrimary));
        wordmark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        wordmark.setTypeface(com.mapcontrol.launcher.SahneFonts.INSTANCE.get(ctx, 300));
        wordmark.setLetterSpacing(0.22f);
        wordmark.setGravity(Gravity.CENTER);
        texts.addView(wordmark, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tagline = new TextView(ctx);
        tagline.setId(R.id.boot_splash_tagline);
        tagline.setText(R.string.splash_map_waiting);
        tagline.setTextColor(UiStyles.color(ctx, R.color.textHint));
        tagline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tagline.setTypeface(com.mapcontrol.launcher.SahneFonts.INSTANCE.get(ctx, 500));
        tagline.setLetterSpacing(0.12f);
        tagline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tagLp.topMargin = dp(ctx, 6);
        texts.addView(tagline, tagLp);

        FrameLayout.LayoutParams textsLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        textsLp.gravity = Gravity.CENTER;
        textsLp.topMargin = 2 * dp(ctx, SPLASH_TEXT_DROP_DP);
        center.addView(texts, textsLp);

        root.addView(center, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        return root;
    }

    /**
     * Splash animasyonlarını başlatır ve [out] listesine ekler. Listede önceki animasyonlar varsa önce
     * durdurulur (eskiden yalnızca clear() yapılıyor, durdurulmamış animasyonlar sonsuza dek çalışıyordu).
     * Ayrıca görünüm pencereden ayrılınca kendi animasyonları otomatik durur.
     */
    private static void startBootSplashAnimations(View root, List<android.animation.Animator> out) {
        cancelAll(out);
        final List<android.animation.Animator> own = new ArrayList<>();

        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(360).start();

        View halo = root.findViewById(R.id.boot_splash_halo);
        View center = root.findViewById(R.id.boot_splash_center);
        View bar = root.findViewById(R.id.boot_splash_bar);
        View logo = root.findViewById(R.id.boot_splash_logo);
        View tagline = root.findViewById(R.id.boot_splash_tagline);

        if (halo != null) {
            halo.setAlpha(0.0f);
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(halo, View.ALPHA, 0f, 0.85f);
            fadeIn.setDuration(900);
            fadeIn.setInterpolator(new AccelerateDecelerateInterpolator());
            fadeIn.start();
            own.add(fadeIn);

        }

        if (center != null) {
            center.setAlpha(0f);
            center.setTranslationY(dp(root.getContext(), 18));
            center.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(220)
                    .setDuration(520)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }

        if (logo != null) {
            logo.setScaleX(0.92f);
            logo.setScaleY(0.92f);
            logo.animate().scaleX(1f).scaleY(1f).setStartDelay(220).setDuration(620)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }

        if (tagline != null) {
            ObjectAnimator blink = ObjectAnimator.ofFloat(tagline, View.ALPHA, 0.55f, 1f);
            blink.setDuration(1400);
            blink.setRepeatCount(ValueAnimator.INFINITE);
            blink.setRepeatMode(ValueAnimator.REVERSE);
            blink.setInterpolator(new AccelerateDecelerateInterpolator());
            blink.start();
            own.add(blink);
        }

        final RadarBlipView radar = bar instanceof RadarBlipView ? (RadarBlipView) bar : null;
        if (radar != null) {
            ValueAnimator pulse = ValueAnimator.ofFloat(0f, 1f);
            pulse.setDuration(2400);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setInterpolator(new LinearInterpolator());
            pulse.addUpdateListener(a -> radar.setProgress((float) a.getAnimatedValue()));
            pulse.start();
            own.add(pulse);
        }
        out.addAll(own);
        // Sonsuz animasyon sürekli çizim demek: cluster splash'i saatlerce ekranda kalabiliyor ve
        // RenderThread ~%55 CPU harcıyordu. Giriş animasyonu kısa süre oynar, sonra son karede donar.
        // View.postDelayed yerine ana Looper Handler'ı: görünümün pencereye bağlanma zamanından bağımsız
        // olarak süre dolunca kesin çalışır (görünümün run-queue'su bağlanma olmazsa hiç boşalmaz).
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            cancelAll(own);
            out.removeAll(own);
            if (radar != null) {
                radar.settle();
            }
        }, SPLASH_ANIMATION_MAX_MS);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                cancelAll(own);
                out.removeAll(own);
                v.removeOnAttachStateChangeListener(this);
            }
        });
    }

    private static void cancelBootSplashAnimations() {
        cancelAll(bootSplashAnimators);
    }

    private static void cancelAll(List<android.animation.Animator> animators) {
        for (android.animation.Animator a : new ArrayList<>(animators)) {
            try {
                a.cancel();
            } catch (Exception ignored) {
            }
        }
        animators.clear();
    }

    /** Merkezi parlayan radial halo. */
    private static final class RadialHaloDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;
        private final int transparentOuter;
        private final int centerAlpha;
        private final int midAlpha;
        private final android.graphics.Rect shaderBounds = new android.graphics.Rect();
        private Shader shader;

        RadialHaloDrawable(int accent, int transparentOuter, int centerAlpha, int midAlpha) {
            this.accent = accent;
            this.transparentOuter = transparentOuter;
            this.centerAlpha = centerAlpha;
            this.midAlpha = midAlpha;
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            float cx = b.exactCenterX();
            float cy = b.exactCenterY();
            float r = Math.min(b.width(), b.height()) / 2f;
            int center = Color.argb(centerAlpha,
                    Color.red(accent), Color.green(accent), Color.blue(accent));
            int mid = Color.argb(midAlpha,
                    Color.red(accent), Color.green(accent), Color.blue(accent));
            // Gölgelendirici yalnız sınırlar değişince kurulur (her karede yeni RadialGradient ayırmaz).
            if (shader == null || !b.equals(shaderBounds)) {
                shader = new RadialGradient(cx, cy, r,
                        new int[]{center, mid, transparentOuter},
                        new float[]{0f, 0.55f, 1f},
                        Shader.TileMode.CLAMP);
                shaderBounds.set(b);
                paint.setShader(shader);
            }
            canvas.drawCircle(cx, cy, r, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /**
     * Logodan yukarı doğru yayılan radar: sabit ince halkalar, dışa giden tarama halkası ve tarama değince parlayan blip.
     * Halkalar aşağı doğru söner; yazıların üstünden geçmez.
     */
    private static final class RadarBlipView extends View {
        private static final int RING_ALPHA = 66;
        private static final int PING_ALPHA = 170;
        private static final float BLIP_ANGLE_DEG = -38f;
        private static final float BLIP_RING = 1;

        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint blipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;
        private final float logoRadius;
        private final float[] rings;
        private final float maxRadius;
        private final float lift;
        private final float blipRadius;
        private final float blipHitT;
        private float progress;
        private boolean settled;

        RadarBlipView(Context ctx) {
            super(ctx);
            accent = UiStyles.color(ctx, R.color.oemAccent);
            logoRadius = dp(ctx, SPLASH_LOGO_RADIUS_DP);
            rings = new float[]{dp(ctx, 100), dp(ctx, 146), dp(ctx, 198)};
            maxRadius = dp(ctx, 250);
            lift = dp(ctx, SPLASH_LOGO_LIFT_DP);
            blipRadius = dp(ctx, 5);
            float strokeWidth = Math.max(1f, dp(ctx, 1));
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(strokeWidth);
            pingPaint.setStyle(Paint.Style.STROKE);
            pingPaint.setStrokeWidth(strokeWidth * 1.5f);
            blipPaint.setStyle(Paint.Style.FILL);
            blipPaint.setColor(UiStyles.color(ctx, R.color.accentColor));
            glowPaint.setStyle(Paint.Style.FILL);
            glowPaint.setColor(accent);
            // Tarama halkası blip'in halkasına eased ilerlemeyle hangi anda varır.
            float fraction = (rings[(int) BLIP_RING] - logoRadius) / (maxRadius - logoRadius);
            blipHitT = 1f - (float) Math.sqrt(1f - fraction);
        }

        void setProgress(float p) {
            progress = p - (float) Math.floor(p);
            invalidate();
        }

        /** Animasyon sınırı dolunca son poz: tarama yok, blip sabit yanar (sürekli çizim olmasın). */
        void settle() {
            settled = true;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            float cy = h / 2f - lift;
            int clear = accent & 0x00FFFFFF;
            Shader fade = new android.graphics.LinearGradient(0f, cy - maxRadius, 0f, cy + logoRadius,
                    new int[]{accent, accent, clear}, new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP);
            ringPaint.setShader(fade);
            pingPaint.setShader(fade);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f - lift;
            ringPaint.setAlpha(RING_ALPHA);
            for (float r : rings) {
                canvas.drawCircle(cx, cy, r, ringPaint);
            }
            float blipLevel = 1f;
            if (!settled) {
                float eased = 1f - (1f - progress) * (1f - progress);
                float r = logoRadius + (maxRadius - logoRadius) * eased;
                int alpha = (int) (PING_ALPHA * (1f - eased));
                if (alpha > 0) {
                    pingPaint.setAlpha(alpha);
                    canvas.drawCircle(cx, cy, r, pingPaint);
                }
                float sinceHit = (progress - blipHitT + 1f) % 1f;
                blipLevel = 0.3f + 0.7f * (float) Math.exp(-sinceHit * 4.5f);
            }
            double angle = Math.toRadians(BLIP_ANGLE_DEG);
            float radius = rings[(int) BLIP_RING];
            float bx = cx + (float) Math.cos(angle) * radius;
            float by = cy + (float) Math.sin(angle) * radius;
            glowPaint.setAlpha((int) (70 * blipLevel));
            canvas.drawCircle(bx, by, blipRadius * (2f + blipLevel), glowPaint);
            blipPaint.setAlpha((int) (255 * blipLevel));
            canvas.drawCircle(bx, by, blipRadius, blipPaint);
        }
    }
}
