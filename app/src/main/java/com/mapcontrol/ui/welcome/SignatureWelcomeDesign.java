package com.mapcontrol.ui.welcome;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Saat ve isteğe bağlı karşılama satırı. Yazı boşsa satır görünmez.
 */
public final class SignatureWelcomeDesign implements WelcomeDesign {

    @Override
    public String id() {
        return WelcomeDesignCatalog.ID_SIGNATURE;
    }

    @Override
    public int titleRes() {
        return R.string.welcome_design_signature;
    }

    @Override
    public int helpRes() {
        return R.string.welcome_design_signature_help;
    }

    @Override
    public View createView(Context context) {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        Typeface display = outfit(context, 200);
        Typeface light = outfit(context, 300);
        Typeface regular = outfit(context, 400);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", locale);
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", locale);

        android.widget.FrameLayout root = new android.widget.FrameLayout(context);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        UiStyles.color(context, R.color.root_gradient_start),
                        UiStyles.color(context, R.color.boot_splash_grad_b),
                        UiStyles.color(context, R.color.root_gradient_center),
                        UiStyles.color(context, R.color.root_gradient_end)
                });
        root.setBackground(background);

        int accent = UiStyles.color(context, R.color.boot_splash_grad_accent);
        GradientDrawable halo = new GradientDrawable();
        halo.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        halo.setGradientCenter(0.5f, 0.42f);
        halo.setGradientRadius(dp(context, 420));
        halo.setColors(new int[]{
                Color.argb(110, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT
        });
        View haloView = new View(context);
        haloView.setBackground(halo);
        root.addView(haloView, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        int ringColor = UiStyles.color(context, R.color.oemAccent);
        View[] rings = new View[3];
        int ringSize = dp(context, 220);
        for (int i = 0; i < rings.length; i++) {
            rings[i] = ringView(context, ringColor);
            root.addView(rings[i], new android.widget.FrameLayout.LayoutParams(
                    ringSize, ringSize, Gravity.CENTER));
        }

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        int side = dp(context, 48);
        column.setPadding(side, 0, side, 0);

        TextView clock = new TextView(context);
        clock.setTextColor(UiStyles.color(context, R.color.textPrimary));
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 92);
        clock.setTypeface(display);
        clock.setLetterSpacing(-0.03f);
        clock.setIncludeFontPadding(false);
        clock.setGravity(Gravity.CENTER);
        column.addView(clock);

        TextView day = new TextView(context);
        day.setTextColor(UiStyles.color(context, R.color.textSecondary));
        day.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        day.setTypeface(regular);
        day.setLetterSpacing(0.22f);
        day.setAllCaps(true);
        day.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dayParams.topMargin = dp(context, 8);
        column.addView(day, dayParams);

        View rule = new View(context);
        GradientDrawable ruleBg = new GradientDrawable();
        ruleBg.setColor(UiStyles.color(context, R.color.oemAccent));
        ruleBg.setCornerRadius(dp(context, 1));
        rule.setBackground(ruleBg);
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(dp(context, 56), dp(context, 2));
        ruleParams.topMargin = dp(context, 28);
        column.addView(rule, ruleParams);

        TextView name = new TextView(context);
        name.setTextColor(UiStyles.color(context, R.color.textPrimary));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        name.setTypeface(light);
        name.setLetterSpacing(0.08f);
        name.setGravity(Gravity.CENTER);
        name.setIncludeFontPadding(false);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(context, 28);
        column.addView(name, nameParams);

        String line = customLine(context);
        if (TextUtils.isEmpty(line)) {
            name.setVisibility(View.GONE);
        } else {
            name.setText(line);
        }

        Runnable refresh = () -> {
            Date now = new Date();
            clock.setText(timeFormat.format(now));
            day.setText(dayFormat.format(now));
        };
        refresh.run();

        Handler handler = new Handler(Looper.getMainLooper());
        List<Animator> animators = new ArrayList<>();
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                refresh.run();
                long wait = 1000L - (System.currentTimeMillis() % 1000L);
                handler.postDelayed(this, wait);
            }
        };
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                handler.removeCallbacks(tick);
                long wait = 1000L - (System.currentTimeMillis() % 1000L);
                handler.postDelayed(tick, wait);
                startMotion(root, haloView, rings, clock, day, rule, name, animators);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                handler.removeCallbacks(tick);
                for (Animator animator : animators) {
                    animator.cancel();
                }
                animators.clear();
            }
        });

        root.addView(column, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        return root;
    }

    private static void startMotion(View root, View halo, View[] rings,
            View clock, View day, View rule, View name, List<Animator> animators) {
        halo.setScaleX(0.86f);
        halo.setScaleY(0.86f);
        AnimatorSet haloIn = new AnimatorSet();
        haloIn.playTogether(
                ObjectAnimator.ofFloat(halo, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(halo, View.SCALE_X, 0.86f, 1f),
                ObjectAnimator.ofFloat(halo, View.SCALE_Y, 0.86f, 1f));
        haloIn.setDuration(900);
        haloIn.setInterpolator(new DecelerateInterpolator());
        keep(animators, haloIn);

        ObjectAnimator pulseX = ObjectAnimator.ofFloat(halo, View.SCALE_X, 1f, 1.08f);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(halo, View.SCALE_Y, 1f, 1.08f);
        ObjectAnimator pulseA = ObjectAnimator.ofFloat(halo, View.ALPHA, 0.72f, 1f);
        for (ObjectAnimator part : new ObjectAnimator[]{pulseX, pulseY, pulseA}) {
            part.setStartDelay(900);
            part.setDuration(2400);
            part.setRepeatCount(ValueAnimator.INFINITE);
            part.setRepeatMode(ValueAnimator.REVERSE);
            part.setInterpolator(new AccelerateDecelerateInterpolator());
            keep(animators, part);
        }

        for (int i = 0; i < rings.length; i++) {
            addRingWave(rings[i], i * 850L, animators);
        }

        rise(clock, 80, animators);
        rise(day, 260, animators);
        drawRule(rule, 420, animators);
        if (name.getVisibility() == View.VISIBLE) {
            rise(name, 560, animators);
        }
    }

    private static void addRingWave(View ring, long delay, List<Animator> animators) {
        ring.setScaleX(0.42f);
        ring.setScaleY(0.42f);
        ring.setAlpha(0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(ring, View.SCALE_X, 0.42f, 1.85f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(ring, View.SCALE_Y, 0.42f, 1.85f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(ring, View.ALPHA, 0.7f, 0f);
        for (ObjectAnimator part : new ObjectAnimator[]{scaleX, scaleY, alpha}) {
            part.setStartDelay(delay);
            part.setDuration(2800);
            part.setRepeatCount(ValueAnimator.INFINITE);
            part.setInterpolator(new DecelerateInterpolator());
            keep(animators, part);
        }
    }

    private static void rise(View view, long delay, List<Animator> animators) {
        view.setAlpha(0f);
        view.setTranslationY(dp(view.getContext(), 22));
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, dp(view.getContext(), 22), 0f));
        set.setStartDelay(delay);
        set.setDuration(720);
        set.setInterpolator(new DecelerateInterpolator());
        keep(animators, set);
    }

    private static void drawRule(View rule, long delay, List<Animator> animators) {
        rule.setAlpha(0f);
        rule.setScaleX(0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(rule, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(rule, View.SCALE_X, 0f, 1f));
        set.setStartDelay(delay);
        set.setDuration(640);
        set.setInterpolator(new DecelerateInterpolator());
        keep(animators, set);
    }

    private static void keep(List<Animator> animators, Animator animator) {
        animators.add(animator);
        animator.start();
    }

    private static View ringView(Context context, int color) {
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(Color.TRANSPARENT);
        oval.setStroke(Math.max(1, dp(context, 1)),
                Color.argb(170, Color.red(color), Color.green(color), Color.blue(color)));
        View ring = new View(context);
        ring.setBackground(oval);
        return ring;
    }

    private static String customLine(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(
                WelcomeDesignCatalog.PREFS_NAME, Context.MODE_PRIVATE);
        String line = prefs.getString(WelcomeDesignCatalog.PREF_LINE, "");
        return line == null ? "" : line.trim();
    }

    private static Typeface outfit(Context context, int weight) {
        Typeface base = ResourcesCompat.getFont(context, R.font.outfit);
        if (base == null) {
            return Typeface.create("sans-serif-light", Typeface.NORMAL);
        }
        return Typeface.create(base, weight, false);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
