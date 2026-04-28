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
    private static final List<android.animation.Animator> bootSplashAnimators = new ArrayList<>();

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
        root.setBackgroundColor(ContextCompat.getColor(ctx, R.color.preparing_overlay_root));

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
        spinner.setColorFilter(ContextCompat.getColor(ctx, R.color.accentColor));
        int spinnerSize = dp(ctx, 40);
        FrameLayout.LayoutParams spLp = new FrameLayout.LayoutParams(spinnerSize, spinnerSize);
        spLp.gravity = Gravity.CENTER;
        spinnerWrap.addView(spinner, spLp);

        card.addView(spinnerWrap);

        TextView title = new TextView(ctx);
        title.setId(R.id.preparing_title);
        title.setText("Uygulama Hazırlanıyor");
        title.setTextColor(ContextCompat.getColor(ctx, R.color.textPrimary));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(null, Typeface.BOLD);
        title.setLetterSpacing(0.02f);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(ctx, 22);
        title.setLayoutParams(titleLp);
        card.addView(title);

        TextView sub = new TextView(ctx);
        sub.setText("Lütfen bekleyin");
        sub.setTextColor(ContextCompat.getColor(ctx, R.color.textSecondaryCool));
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
        int accent = ContextCompat.getColor(ctx, R.color.accentColor);
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
        preparingAnimators.clear();

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
        try {
            cancelPreparingAnimations();
            if (preparingMessageView != null && preparingWindowManager != null) {
                try {
                    preparingMessageView.animate()
                            .alpha(0f)
                            .setDuration(180)
                            .withEndAction(() -> {
                                try {
                                    if (preparingWindowManager != null && preparingMessageView != null) {
                                        preparingWindowManager.removeView(preparingMessageView);
                                    }
                                } catch (Exception ignored) {
                                } finally {
                                    preparingMessageView = null;
                                    preparingWindowManager = null;
                                    preparingParams = null;
                                    displayContext = null;
                                }
                            })
                            .start();
                    return;
                } catch (Exception ignored) {
                }

                preparingWindowManager.removeView(preparingMessageView);
                preparingMessageView = null;
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

    /**
     * Cluster (secondary) ekrana boot/welcome overlay'i bastırır. Kullanıcı uygulamadan ekran
     * yansıtana kadar oradaki siyahlığı doldurur. {@link #hideBootSplash()} çağrılana kadar kalır.
     */
    public static void showBootSplashOnDisplay(Context context, int displayId) {
        try {
            if (bootSplashView != null) {
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

            bootSplashView = overlay;
            bootSplashWindowManager.addView(bootSplashView, bootSplashParams);

            startBootSplashAnimations(overlay);
        } catch (Exception ignored) {
        }
    }

    public static void hideBootSplash() {
        try {
            cancelBootSplashAnimations();
            if (bootSplashView != null && bootSplashWindowManager != null) {
                try {
                    bootSplashView.animate()
                            .alpha(0f)
                            .setDuration(260)
                            .withEndAction(() -> {
                                try {
                                    if (bootSplashWindowManager != null && bootSplashView != null) {
                                        bootSplashWindowManager.removeView(bootSplashView);
                                    }
                                } catch (Exception ignored) {
                                } finally {
                                    bootSplashView = null;
                                    bootSplashWindowManager = null;
                                    bootSplashParams = null;
                                    bootSplashDisplayContext = null;
                                    bootSplashDisplayId = Display.INVALID_DISPLAY;
                                }
                            })
                            .start();
                    return;
                } catch (Exception ignored) {
                }
                bootSplashWindowManager.removeView(bootSplashView);
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
            startBootSplashAnimations(overlay);
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

    private static View buildBootSplashOverlay(Context ctx) {
        FrameLayout root = new FrameLayout(ctx);

        // Katman 1: derin gradient arka plan
        View bgGradient = new View(ctx);
        android.graphics.drawable.GradientDrawable bgDr = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{
                        ContextCompat.getColor(ctx, R.color.boot_splash_gradient_edge),
                        ContextCompat.getColor(ctx, R.color.boot_splash_gradient_mid),
                        ContextCompat.getColor(ctx, R.color.boot_splash_gradient_edge)});
        bgGradient.setBackground(bgDr);
        root.addView(bgGradient, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Katman 2: hareketli harita grid'i
        MapGridView grid = new MapGridView(ctx);
        grid.setId(R.id.boot_splash_grid);
        root.addView(grid, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Katman 3: merkezde parlayan halo (radial)
        View halo = new View(ctx);
        halo.setId(R.id.boot_splash_halo);
        android.content.res.Resources res = ctx.getResources();
        halo.setBackground(new RadialHaloDrawable(
                ContextCompat.getColor(ctx, R.color.accentColor),
                ContextCompat.getColor(ctx, R.color.transparent),
                res.getInteger(R.integer.boot_splash_halo_center_alpha),
                res.getInteger(R.integer.boot_splash_halo_mid_alpha)));
        int haloSize = dp(ctx, 480);
        FrameLayout.LayoutParams haloLp = new FrameLayout.LayoutParams(haloSize, haloSize);
        haloLp.gravity = Gravity.CENTER;
        root.addView(halo, haloLp);

        // Katman 4: merkez içerik
        LinearLayout center = new LinearLayout(ctx);
        center.setId(R.id.boot_splash_center);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);

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
        int logoSize = dp(ctx, 96);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(logoSize, logoSize);
        logoLp.gravity = Gravity.CENTER_HORIZONTAL;
        logo.setLayoutParams(logoLp);
        center.addView(logo);

        TextView wordmark = new TextView(ctx);
        wordmark.setId(R.id.boot_splash_wordmark);
        wordmark.setText("Map Control");
        wordmark.setTextColor(ContextCompat.getColor(ctx, R.color.textPrimary));
        wordmark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        wordmark.setTypeface(null, Typeface.BOLD);
        wordmark.setLetterSpacing(0.18f);
        wordmark.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams wmLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wmLp.topMargin = dp(ctx, 22);
        wordmark.setLayoutParams(wmLp);
        center.addView(wordmark);

        TextView tagline = new TextView(ctx);
        tagline.setId(R.id.boot_splash_tagline);
        tagline.setText("Harita bekleniyor");
        tagline.setTextColor(ContextCompat.getColor(ctx, R.color.textSecondaryCool));
        tagline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tagline.setLetterSpacing(0.12f);
        tagline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tagLp.topMargin = dp(ctx, 10);
        tagline.setLayoutParams(tagLp);
        center.addView(tagline);

        // Konum pini + radar dalgaları
        RadarPinView radar = new RadarPinView(ctx,
                ContextCompat.getColor(ctx, R.color.accentColor));
        radar.setId(R.id.boot_splash_bar);
        int radarW = dp(ctx, 96);
        int radarH = dp(ctx, 96);
        LinearLayout.LayoutParams radarLp = new LinearLayout.LayoutParams(radarW, radarH);
        radarLp.gravity = Gravity.CENTER_HORIZONTAL;
        radarLp.topMargin = dp(ctx, 24);
        radar.setLayoutParams(radarLp);
        center.addView(radar);

        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        centerLp.gravity = Gravity.CENTER;
        center.setLayoutParams(centerLp);
        root.addView(center);

        return root;
    }

    private static void startBootSplashAnimations(View root) {
        bootSplashAnimators.clear();

        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(360).start();

        View grid = root.findViewById(R.id.boot_splash_grid);
        View halo = root.findViewById(R.id.boot_splash_halo);
        View center = root.findViewById(R.id.boot_splash_center);
        View bar = root.findViewById(R.id.boot_splash_bar);
        View logo = root.findViewById(R.id.boot_splash_logo);
        View tagline = root.findViewById(R.id.boot_splash_tagline);

        if (grid instanceof MapGridView) {
            ValueAnimator scroll = ValueAnimator.ofFloat(0f, 1f);
            scroll.setDuration(8000);
            scroll.setRepeatCount(ValueAnimator.INFINITE);
            scroll.setInterpolator(new LinearInterpolator());
            final MapGridView g = (MapGridView) grid;
            scroll.addUpdateListener(a -> g.setProgress((float) a.getAnimatedValue()));
            scroll.start();
            bootSplashAnimators.add(scroll);
        }

        if (halo != null) {
            halo.setAlpha(0.0f);
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(halo, View.ALPHA, 0f, 0.85f);
            fadeIn.setDuration(900);
            fadeIn.setInterpolator(new AccelerateDecelerateInterpolator());
            fadeIn.start();
            bootSplashAnimators.add(fadeIn);

            ObjectAnimator pulseX = ObjectAnimator.ofFloat(halo, View.SCALE_X, 0.95f, 1.08f);
            ObjectAnimator pulseY = ObjectAnimator.ofFloat(halo, View.SCALE_Y, 0.95f, 1.08f);
            ObjectAnimator pulseA = ObjectAnimator.ofFloat(halo, View.ALPHA, 0.65f, 0.95f);
            for (ObjectAnimator a : new ObjectAnimator[]{pulseX, pulseY, pulseA}) {
                a.setDuration(2200);
                a.setStartDelay(900);
                a.setInterpolator(new AccelerateDecelerateInterpolator());
                a.setRepeatCount(ValueAnimator.INFINITE);
                a.setRepeatMode(ValueAnimator.REVERSE);
            }
            AnimatorSet pulse = new AnimatorSet();
            pulse.playTogether(pulseX, pulseY, pulseA);
            pulse.start();
            bootSplashAnimators.add(pulse);
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
            ObjectAnimator floatY = ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y,
                    0f, -dp(root.getContext(), 4), 0f);
            floatY.setDuration(3200);
            floatY.setRepeatCount(ValueAnimator.INFINITE);
            floatY.setInterpolator(new AccelerateDecelerateInterpolator());
            floatY.start();
            bootSplashAnimators.add(floatY);
        }

        if (tagline != null) {
            ObjectAnimator blink = ObjectAnimator.ofFloat(tagline, View.ALPHA, 0.55f, 1f);
            blink.setDuration(1400);
            blink.setRepeatCount(ValueAnimator.INFINITE);
            blink.setRepeatMode(ValueAnimator.REVERSE);
            blink.setInterpolator(new AccelerateDecelerateInterpolator());
            blink.start();
            bootSplashAnimators.add(blink);
        }

        if (bar instanceof RadarPinView) {
            final RadarPinView radar = (RadarPinView) bar;
            ValueAnimator pulse = ValueAnimator.ofFloat(0f, 1f);
            pulse.setDuration(2200);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setInterpolator(new LinearInterpolator());
            pulse.addUpdateListener(a -> radar.setProgress((float) a.getAnimatedValue()));
            pulse.start();
            bootSplashAnimators.add(pulse);

            ObjectAnimator pinBounce = ObjectAnimator.ofFloat(radar, View.TRANSLATION_Y,
                    0f, -dp(root.getContext(), 3), 0f);
            pinBounce.setDuration(1800);
            pinBounce.setRepeatCount(ValueAnimator.INFINITE);
            pinBounce.setInterpolator(new AccelerateDecelerateInterpolator());
            pinBounce.start();
            bootSplashAnimators.add(pinBounce);
        }
    }

    private static void cancelBootSplashAnimations() {
        for (android.animation.Animator a : bootSplashAnimators) {
            try {
                a.cancel();
            } catch (Exception ignored) {
            }
        }
        bootSplashAnimators.clear();
    }

    /** Hareketli izometrik harita grid'i — boot splash arkaplanı için. */
    private static final class MapGridView extends View {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint vignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress = 0f;
        private final int spacingDp = 56;
        private final int vignetteTransparent;
        private final int vignetteEdge;
        private final int glowPulseAlphaMax;

        MapGridView(Context ctx) {
            super(ctx);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(dp(ctx, 1));
            linePaint.setColor(ContextCompat.getColor(ctx, R.color.map_grid_line_accent));

            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setStrokeWidth(dp(ctx, 1));
            glowPaint.setColor(ContextCompat.getColor(ctx, R.color.map_grid_glow_accent));

            vignettePaint.setStyle(Paint.Style.FILL);
            vignetteTransparent = ContextCompat.getColor(ctx, R.color.transparent);
            vignetteEdge = ContextCompat.getColor(ctx, R.color.map_grid_vignette_edge);
            glowPulseAlphaMax = ctx.getResources().getInteger(R.integer.map_grid_glow_pulse_alpha_max);
        }

        void setProgress(float p) {
            this.progress = p;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            // Kenarlardan merkeze koyu vignette
            RadialGradient vg = new RadialGradient(
                    w / 2f, h / 2f,
                    Math.max(w, h) * 0.7f,
                    new int[]{vignetteTransparent, vignetteEdge},
                    new float[]{0.55f, 1f},
                    Shader.TileMode.CLAMP);
            vignettePaint.setShader(vg);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            int spacing = dp(getContext(), spacingDp);
            float offset = progress * spacing;

            // Diyagonal grid (izometrik his)
            for (float x = -h + offset; x < w + h; x += spacing) {
                canvas.drawLine(x, 0, x + h, h, linePaint);
            }
            for (float x = -h - offset; x < w + h; x += spacing) {
                canvas.drawLine(x, h, x + h, 0, linePaint);
            }

            // Pulse eden ışıklı çizgi (rotation effekti)
            float t = (progress * 2f) % 1f;
            float gx = -h + t * (w + h);
            glowPaint.setAlpha((int) (glowPulseAlphaMax * (1f - Math.abs(0.5f - t) * 2f)));
            canvas.drawLine(gx, 0, gx + h, h, glowPaint);

            // Vignette
            canvas.drawRect(0, 0, w, h, vignettePaint);
        }
    }

    /** Merkezi parlayan radial halo. */
    private static final class RadialHaloDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;
        private final int transparentOuter;
        private final int centerAlpha;
        private final int midAlpha;

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
            paint.setShader(new RadialGradient(cx, cy, r,
                    new int[]{center, mid, transparentOuter},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** Konum pin'i + dışa doğru genişleyen radar dalgaları. */
    private static final class RadarPinView extends View {
        private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pinFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pinDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pinShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;
        private final int radarWaveAlphaMax;
        private float progress = 0f;

        RadarPinView(Context ctx, int accent) {
            super(ctx);
            this.accent = accent;
            this.radarWaveAlphaMax = ctx.getResources().getInteger(R.integer.boot_splash_radar_wave_alpha_max);
            wavePaint.setStyle(Paint.Style.STROKE);
            wavePaint.setStrokeWidth(dp(ctx, 2));
            wavePaint.setColor(accent);

            pinFillPaint.setStyle(Paint.Style.FILL);
            pinFillPaint.setColor(accent);

            pinDotPaint.setStyle(Paint.Style.FILL);
            pinDotPaint.setColor(ContextCompat.getColor(ctx, R.color.boot_splash_pin_dot));

            pinShadowPaint.setStyle(Paint.Style.FILL);
            pinShadowPaint.setColor(ContextCompat.getColor(ctx, R.color.boot_splash_pin_shadow));
        }

        void setProgress(float p) {
            this.progress = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h * 0.62f;
            float maxR = Math.min(w, h) * 0.46f;

            // Üç kademeli radar dalgası
            for (int i = 0; i < 3; i++) {
                float t = (progress + i / 3f) % 1f;
                float r = maxR * t;
                int alpha = (int) (radarWaveAlphaMax * (1f - t));
                if (alpha <= 0) continue;
                wavePaint.setAlpha(alpha);
                canvas.drawCircle(cx, cy, r, wavePaint);
            }

            // Yer gölgesi (pin'in altına oval)
            float shadowSquish = 0.25f + 0.08f * (float) Math.sin(progress * Math.PI * 2);
            float shadowR = maxR * 0.22f;
            canvas.save();
            canvas.scale(1f, shadowSquish, cx, cy + maxR * 0.25f);
            canvas.drawCircle(cx, cy + maxR * 0.25f, shadowR, pinShadowPaint);
            canvas.restore();

            // Klasik konum pini (damla şekli)
            float pinR = dp(getContext(), 13);
            float pinTipY = cy + pinR * 1.55f;
            float pinTopY = cy - pinR;
            android.graphics.Path pin = new android.graphics.Path();
            pin.addCircle(cx, cy, pinR, android.graphics.Path.Direction.CW);
            pin.moveTo(cx - pinR * 0.78f, cy + pinR * 0.55f);
            pin.lineTo(cx, pinTipY);
            pin.lineTo(cx + pinR * 0.78f, cy + pinR * 0.55f);
            pin.close();
            canvas.drawPath(pin, pinFillPaint);

            // İç boşluk (delik)
            canvas.drawCircle(cx, cy - pinR * 0.05f, pinR * 0.42f, pinDotPaint);

            // Suppress unused warning
            if (pinTopY < 0) { /* no-op */ }
        }
    }
}
