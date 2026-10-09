package com.mapcontrol.ui.welcome;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.mapcontrol.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Dağ Yürüyüşü: gün batımı manzarası, saat halkası ve isteğe bağlı isim satırı.
 */
public final class MountainHikeWelcomeDesign implements WelcomeDesign {

    private static final int INK = 0xFFF4F7FB;
    private static final int INK_SOFT = 0xFFC9D0DA;
    private static final int RIDGE = 0xFFD5DCE4;

    @Override
    public String id() {
        return WelcomeDesignCatalog.ID_HIKE;
    }

    @Override
    public int titleRes() {
        return R.string.welcome_design_hike;
    }

    @Override
    public int helpRes() {
        return R.string.welcome_design_hike_help;
    }

    @Override
    public View createView(Context context) {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        Typeface display = outfit(context, 200);
        Typeface light = outfit(context, 300);
        Typeface regular = outfit(context, 400);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", locale);
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", locale);

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0xFF1A2744);
        root.addView(new MountainHikeScene(context), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        int side = dp(context, 48);
        column.setPadding(side, 0, side, 0);

        TextView clock = text(context, display, 58, INK, -0.03f);
        column.addView(clock);

        TextView day = text(context, regular, 13, INK_SOFT, 0.28f);
        day.setAllCaps(true);
        LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dayParams.topMargin = dp(context, 6);
        column.addView(day, dayParams);

        column.addView(divider(context), dividerParams(context));

        TextView name = text(context, light, 26, INK, 0.06f);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(context, 12);
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
                rise(clock, 120, animators);
                rise(day, 280, animators);
                rise(name, 460, animators);
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

        root.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        return root;
    }

    private static LinearLayout divider(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.addView(rule(context), new LinearLayout.LayoutParams(dp(context, 36), dp(context, 1)));
        LinearLayout.LayoutParams peak = new LinearLayout.LayoutParams(dp(context, 16), dp(context, 10));
        peak.leftMargin = dp(context, 8);
        peak.rightMargin = dp(context, 8);
        row.addView(new PeakGlyph(context, RIDGE), peak);
        row.addView(rule(context), new LinearLayout.LayoutParams(dp(context, 36), dp(context, 1)));
        return row;
    }

    private static LinearLayout.LayoutParams dividerParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, 16);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        return params;
    }

    private static View rule(Context context) {
        View line = new View(context);
        line.setBackgroundColor(0x99D5DCE6);
        return line;
    }

    private static TextView text(Context context, Typeface face, int sp, int color, float tracking) {
        TextView view = new TextView(context);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTypeface(face);
        view.setLetterSpacing(tracking);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        return view;
    }

    private static void rise(View view, long delay, List<Animator> animators) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        view.setAlpha(0f);
        view.setTranslationY(dp(view.getContext(), 16));
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, dp(view.getContext(), 16), 0f));
        set.setStartDelay(delay);
        set.setDuration(700);
        set.setInterpolator(new DecelerateInterpolator());
        animators.add(set);
        set.start();
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

    /** Saat altındaki küçük dağ işareti. */
    static final class PeakGlyph extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        PeakGlyph(Context context, int color) {
            super(context);
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            path.reset();
            path.moveTo(w * 0.18f, h);
            path.lineTo(w * 0.38f, h * 0.28f);
            path.lineTo(w * 0.52f, h * 0.62f);
            path.lineTo(w * 0.7f, 0f);
            path.lineTo(w, h);
            path.close();
            canvas.drawPath(path, paint);
        }
    }
}
