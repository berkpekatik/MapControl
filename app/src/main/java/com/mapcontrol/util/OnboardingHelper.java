package com.mapcontrol.util;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

/** One-time welcome screen shown after the legal disclaimer: animated backdrop + language choice. */
public final class OnboardingHelper {
    public static final String PREF_KEY_COMPLETED = "onboardingCompleted";

    private OnboardingHelper() {}

    public static View createView(Activity activity, Runnable onContinue) {
        Context ctx = activity;
        FrameLayout root = (FrameLayout) DisplayHelper.createAppLaunchSplashView(ctx);
        View splashCenter = root.findViewById(R.id.boot_splash_center);
        if (splashCenter != null) splashCenter.setVisibility(View.GONE);

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        contentLp.gravity = Gravity.CENTER;
        root.addView(content, contentLp);

        ImageView logo = new ImageView(ctx);
        Drawable icon = null;
        try {
            icon = ctx.getPackageManager().getApplicationIcon(ctx.getPackageName());
        } catch (Exception ignored) {
        }
        if (icon == null) icon = ContextCompat.getDrawable(ctx, R.mipmap.ic_launcher);
        logo.setImageDrawable(icon);
        content.addView(logo, new LinearLayout.LayoutParams(dp(ctx, 88), dp(ctx, 88)));

        TextView title = text(ctx, ctx.getString(R.string.onboarding_title), 30, R.color.textPrimary);
        title.setTypeface(null, Typeface.BOLD);
        title.setLetterSpacing(0.12f);
        content.addView(title, lp(dp(ctx, 20), 0));

        TextView subtitle = text(ctx, ctx.getString(R.string.onboarding_subtitle), 15, R.color.textSecondaryCool);
        content.addView(subtitle, lp(dp(ctx, 8), 0));

        TextView langLabel = text(ctx, ctx.getString(R.string.settings_language_section_title), 13, R.color.textSecondaryCool);
        langLabel.setLetterSpacing(0.1f);
        content.addView(langLabel, lp(dp(ctx, 32), 0));

        boolean english = AppLocaleManager.isEnglish(ctx);
        LinearLayout langRow = new LinearLayout(ctx);
        langRow.setOrientation(LinearLayout.HORIZONTAL);
        langRow.setGravity(Gravity.CENTER);
        langRow.addView(languageChip(activity, ctx.getString(R.string.settings_language_tr),
                AppLocaleManager.LOCALE_TR, !english), chipLp(ctx));
        langRow.addView(languageChip(activity, ctx.getString(R.string.settings_language_en),
                AppLocaleManager.LOCALE_EN, english), chipLp(ctx));
        content.addView(langRow, lp(dp(ctx, 12), 0));

        TextView next = text(ctx, ctx.getString(R.string.onboarding_continue), 17, R.color.textPrimary);
        next.setTextColor(Color.WHITE);
        next.setTypeface(null, Typeface.BOLD);
        next.setPadding(dp(ctx, 48), dp(ctx, 14), dp(ctx, 48), dp(ctx, 14));
        next.setBackground(pill(UiStyles.color(ctx, R.color.accentColor), 0, 0));
        next.setClickable(true);
        next.setOnClickListener(v -> onContinue.run());
        content.addView(next, lp(dp(ctx, 36), 0));

        View[] steps = {logo, title, subtitle, langLabel, langRow, next};
        for (int i = 0; i < steps.length; i++) {
            steps[i].setAlpha(0f);
            steps[i].setTranslationY(dp(ctx, 22));
            steps[i].animate().alpha(1f).translationY(0f)
                    .setStartDelay(250L + i * 130L).setDuration(520)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }

        ObjectAnimator floatY = ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, 0f, -dp(ctx, 5), 0f);
        floatY.setStartDelay(1200);
        floatY.setDuration(3200);
        floatY.setRepeatCount(ValueAnimator.INFINITE);
        floatY.setInterpolator(new AccelerateDecelerateInterpolator());
        floatY.start();
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) { floatY.cancel(); }
        });

        return root;
    }

    private static TextView languageChip(Activity activity, String label, String tag, boolean selected) {
        Context ctx = activity;
        TextView chip = text(ctx, label, 16, R.color.textPrimary);
        chip.setTypeface(null, Typeface.BOLD);
        chip.setPadding(dp(ctx, 28), dp(ctx, 12), dp(ctx, 28), dp(ctx, 12));
        int accent = UiStyles.color(ctx, R.color.accentColor);
        if (selected) {
            chip.setTextColor(Color.WHITE);
            chip.setBackground(pill(accent, 0, 0));
        } else {
            chip.setBackground(pill(UiStyles.color(ctx, R.color.surfaceCardInner), accent, dp(ctx, 1)));
        }
        chip.setClickable(true);
        chip.setOnClickListener(v -> {
            if (tag.equals(AppLocaleManager.getStoredLocaleTag(ctx))) return;
            AppLocaleManager.setLocale(ctx, tag);
            activity.recreate();
        });
        return chip;
    }

    private static GradientDrawable pill(int fill, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(1000f);
        if (strokeWidth > 0) d.setStroke(strokeWidth, stroke);
        return d;
    }

    private static TextView text(Context ctx, String value, int sp, int colorRes) {
        TextView tv = new TextView(ctx);
        tv.setText(value);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(UiStyles.color(ctx, colorRes));
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private static LinearLayout.LayoutParams lp(int topMargin, int bottomMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        lp.bottomMargin = bottomMargin;
        return lp;
    }

    private static LinearLayout.LayoutParams chipLp(Context ctx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(ctx, 8), 0, dp(ctx, 8), 0);
        return lp;
    }

    private static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
