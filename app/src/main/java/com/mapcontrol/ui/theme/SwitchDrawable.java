package com.mapcontrol.ui.theme;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
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
    private static final long DURATION_MS = 180L;

    private final float density;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int onColor;
    private int offColor;
    private boolean checked;
    private float position;
    @Nullable
    private ValueAnimator animator;

    SwitchDrawable(float density) {
        this.density = density;
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
        for (int s : state) {
            if (s == android.R.attr.state_checked) {
                next = true;
                break;
            }
        }
        if (next == checked) {
            return false;
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
        track.setColor(ColorUtils.blendARGB(offColor, onColor, position));
        canvas.drawRoundRect(rect, radius, radius, track);
        float inset = KNOB_INSET_DP * density;
        float knobRadius = radius - inset;
        float startX = rect.left + inset + knobRadius;
        float endX = rect.right - inset - knobRadius;
        canvas.drawCircle(startX + (endX - startX) * position, rect.centerY(), knobRadius, knob);
    }

    @Override
    public int getIntrinsicWidth() {
        return Math.round(WIDTH_DP * density);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(HEIGHT_DP * density);
    }

    @Override
    public void setAlpha(int alpha) {
        track.setAlpha(alpha);
        knob.setAlpha(alpha);
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
