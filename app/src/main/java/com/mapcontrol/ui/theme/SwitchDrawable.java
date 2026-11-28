package com.mapcontrol.ui.theme;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/** Launcher tarzı anahtar: topu kayarak geçer, iz rengi birlikte değişir. CompoundButton düğmesi olarak kullanılır. */
final class SwitchDrawable extends Drawable {

    private static final int WIDTH_DP = 52;
    private static final int HEIGHT_DP = 30;
    private static final int KNOB_INSET_DP = 3;
    private static final int DISABLED_ALPHA = 102;
    private static final long DURATION_MS = 180L;

    private final float density;
    private final int widthDp;
    private final int heightDp;
    private final int knobInsetDp;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int onColor;
    private int offColor;
    private boolean checked;
    private boolean enabled = true;
    private int alpha = 255;
    private float position;
    @Nullable
    private ValueAnimator animator;

    SwitchDrawable(float density) {
        this(density, WIDTH_DP, HEIGHT_DP, KNOB_INSET_DP);
    }

    SwitchDrawable(float density, int widthDp, int heightDp, int knobInsetDp) {
        this.density = density;
        this.widthDp = widthDp;
        this.heightDp = heightDp;
        this.knobInsetDp = knobInsetDp;
    }

    void setColors(int on, int off, int knobColor) {
        onColor = on;
        offColor = off;
        knob.setColor(knobColor);
        invalidateSelf();
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(@NonNull int[] state) {
        boolean next = false;
        boolean nextEnabled = false;
        for (int s : state) {
            if (s == android.R.attr.state_checked) {
                next = true;
            } else if (s == android.R.attr.state_enabled) {
                nextEnabled = true;
            }
        }
        boolean enabledChanged = nextEnabled != enabled;
        enabled = nextEnabled;
        if (next == checked) {
            if (enabledChanged) {
                invalidateSelf();
            }
            return enabledChanged;
        }
        checked = next;
        cancelAnimator();
        ValueAnimator a = ValueAnimator.ofFloat(position, checked ? 1f : 0f);
        a.setDuration(DURATION_MS);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(v -> {
            position = (float) v.getAnimatedValue();
            invalidateSelf();
        });
        animator = a;
        a.start();
        return true;
    }

    /** İlk kurulumda ve görünüm bağlanırken animasyonsuz son konuma geçer. */
    @Override
    public void jumpToCurrentState() {
        cancelAnimator();
        position = checked ? 1f : 0f;
        invalidateSelf();
    }

    private void cancelAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        rect.set(getBounds());
        float radius = rect.height() / 2f;
        int drawAlpha = enabled ? alpha : alpha * DISABLED_ALPHA / 255;
        track.setColor(ColorUtils.blendARGB(offColor, onColor, position));
        track.setAlpha(Color.alpha(track.getColor()) * drawAlpha / 255);
        knob.setAlpha(drawAlpha);
        canvas.drawRoundRect(rect, radius, radius, track);
        float inset = knobInsetDp * density;
        float knobRadius = radius - inset;
        float startX = rect.left + inset + knobRadius;
        float endX = rect.right - inset - knobRadius;
        canvas.drawCircle(startX + (endX - startX) * position, rect.centerY(), knobRadius, knob);
    }

    @Override
    public int getIntrinsicWidth() {
        return Math.round(widthDp * density);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(heightDp * density);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        track.setColorFilter(colorFilter);
        knob.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
