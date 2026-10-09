package com.mapcontrol.ui.welcome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Dağ Yürüyüşü zemini: gün batımı, katmanlı sırtlar, göl ve uçan kuşlar.
 */
public class MountainHikeScene extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path farRidge = new Path();
    private final Path midRidge = new Path();
    private final Path nearRidge = new Path();
    private final Path shrubs = new Path();
    private final RectF oval = new RectF();
    private LinearGradient sky;
    private RadialGradient sunGlow;
    private int laidOutW;
    private int laidOutH;
    private boolean running;
    private long startMs;

    public MountainHikeScene(Context context) {
        this(context, null);
    }

    public MountainHikeScene(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setDither(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        startMs = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        sky = null;
        sunGlow = null;
        if (w <= 0 || h <= 0) {
            return;
        }
        laidOutW = w;
        laidOutH = h;
        buildRidges(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float t = (SystemClock.uptimeMillis() - startMs) / 1000f;
        float horizon = h * 0.62f;
        float sunX = w * 0.84f;
        float sunY = horizon - dp(6);

        drawSky(canvas, w, h, sunX, sunY);
        drawClouds(canvas, w, h, t);
        drawSun(canvas, sunX, sunY, t);
        if (laidOutW != w || laidOutH != h) {
            buildRidges(w, h);
            laidOutW = w;
            laidOutH = h;
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF3E5878);
        canvas.drawPath(farRidge, paint);
        paint.setColor(0xFF24344C);
        canvas.drawPath(midRidge, paint);
        drawWater(canvas, w, h, horizon, sunX, t);
        paint.setColor(0xFF121A28);
        canvas.drawPath(nearRidge, paint);
        drawShoreLights(canvas, w, horizon, t);
        paint.setColor(0xFF0C121C);
        canvas.drawPath(shrubs, paint);
        drawBirds(canvas, w, h, t);

        float radius = Math.min(w, h) * 0.34f;
        drawRing(canvas, w / 2f, h / 2f, radius, t);

        if (running) {
            postInvalidateOnAnimation();
        }
    }

    private void drawSky(Canvas canvas, int w, int h, float sunX, float sunY) {
        if (sky == null) {
            sky = new LinearGradient(0, 0, w, h * 0.75f,
                    new int[]{0xFF1A2744, 0xFF243656, 0xFF6A4A58, 0xFFC46A3A},
                    new float[]{0f, 0.38f, 0.72f, 1f},
                    Shader.TileMode.CLAMP);
            sunGlow = new RadialGradient(sunX, sunY, Math.max(1, h * 0.42f),
                    0x88FFB060, 0x00FFB060, Shader.TileMode.CLAMP);
        }
        paint.setShader(sky);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, w, h, paint);
        if (sunGlow != null) {
            paint.setShader(sunGlow);
            canvas.drawRect(0, 0, w, h, paint);
        }
        paint.setShader(null);
    }

    private void drawClouds(Canvas canvas, int w, int h, float t) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x22101828);
        float drift = (t * dp(8)) % (w + dp(200));
        oval.set(drift - dp(180), h * 0.08f, drift + dp(40), h * 0.16f);
        canvas.drawOval(oval, paint);
        oval.set(w * 0.15f - drift * 0.3f, h * 0.16f, w * 0.42f - drift * 0.3f, h * 0.22f);
        canvas.drawOval(oval, paint);
        paint.setColor(0x18F0C8A0);
        oval.set(w * 0.62f, h * 0.34f, w * 0.92f, h * 0.42f);
        canvas.drawOval(oval, paint);
    }

    private void drawSun(Canvas canvas, float sunX, float sunY, float t) {
        float pulse = 1f + 0.04f * (float) Math.sin(t * 1.2f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x66FF9A48);
        canvas.drawCircle(sunX, sunY, dp(28) * pulse, paint);
        paint.setColor(0xFFFFE0B0);
        canvas.drawCircle(sunX, sunY, dp(11), paint);
        paint.setColor(0xFFFFF6E4);
        canvas.drawCircle(sunX, sunY, dp(6), paint);
    }

    private void drawWater(Canvas canvas, int w, int h, float horizon, float sunX, float t) {
        paint.setColor(0xFF1A2838);
        canvas.drawRect(0, horizon + dp(8), w, h, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        for (int i = 0; i < 7; i++) {
            float y = horizon + dp(28) + i * dp(14);
            float shimmer = (float) Math.sin(t * 1.6f + i) * dp(10);
            int alpha = 28 + (int) (18 * (0.5f + 0.5f * Math.sin(t * 2f + i)));
            paint.setColor((alpha << 24) | 0x8FB4C8);
            canvas.drawLine(w * 0.05f, y, w * 0.7f, y + shimmer * 0.1f, paint);
        }
        paint.setStrokeWidth(dp(2));
        float reflectTop = horizon + dp(18);
        for (int i = 0; i < 8; i++) {
            float y = reflectTop + i * dp(10);
            float half = dp(18) - i * dp(1.4f);
            float wobble = (float) Math.sin(t * 2.2f + i) * dp(4);
            int alpha = 90 - i * 8;
            paint.setColor((Math.max(alpha, 12) << 24) | 0xE8A060);
            canvas.drawLine(sunX - half + wobble, y, sunX + half + wobble, y, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawShoreLights(Canvas canvas, int w, float horizon, float t) {
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 9; i++) {
            float x = w * (0.06f + i * 0.035f);
            float y = horizon + dp(16) + (i % 3) * dp(3);
            int alpha = 140 + (int) (70 * Math.sin(t * 2.4f + i));
            paint.setColor((Math.max(40, Math.min(220, alpha)) << 24) | 0xF2D48A);
            canvas.drawCircle(x, y, dp(i % 2 == 0 ? 2 : 1), paint);
        }
    }

    private void drawBirds(Canvas canvas, int w, int h, float t) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(1.4f));
        paint.setColor(0xCC1A2433);
        float[] lanes = {0.18f, 0.24f, 0.15f};
        for (int i = 0; i < lanes.length; i++) {
            float travel = (t * dp(18 + i * 4) + i * w * 0.22f) % (w + dp(80));
            float x = travel - dp(40);
            float y = h * lanes[i] + (float) Math.sin(t * 1.3f + i) * dp(6);
            float flap = (float) Math.sin(t * 7f + i);
            float wing = dp(7 + i);
            float lift = wing * (0.35f + 0.45f * flap);
            canvas.drawLine(x - wing, y - lift, x, y, paint);
            canvas.drawLine(x, y, x + wing, y - lift, paint);
        }
    }

    private void drawRing(Canvas canvas, float cx, float cy, float radius, float t) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(1.6f));
        paint.setColor(0x99D5E2F0);
        canvas.drawCircle(cx, cy, radius, paint);
        canvas.drawCircle(cx, cy, radius - dp(8), paint);
        paint.setStrokeWidth(dp(3.2f));
        paint.setColor(0xE8F0A060);
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
        float sway = (float) Math.sin(t * 0.6f) * 6f;
        canvas.drawArc(oval, -78f + sway, 62f, false, paint);
    }

    private void buildRidges(int w, int h) {
        float horizon = h * 0.62f;
        farRidge.reset();
        farRidge.moveTo(0, horizon + dp(10));
        farRidge.lineTo(w * 0.08f, horizon - h * 0.04f);
        farRidge.lineTo(w * 0.18f, horizon - h * 0.1f);
        farRidge.lineTo(w * 0.28f, horizon - h * 0.03f);
        farRidge.lineTo(w * 0.4f, horizon - h * 0.12f);
        farRidge.lineTo(w * 0.52f, horizon - h * 0.02f);
        farRidge.lineTo(w * 0.66f, horizon - h * 0.08f);
        farRidge.lineTo(w * 0.78f, horizon);
        farRidge.lineTo(w * 0.9f, horizon - h * 0.05f);
        farRidge.lineTo(w, horizon + dp(6));
        farRidge.lineTo(w, h);
        farRidge.lineTo(0, h);
        farRidge.close();

        midRidge.reset();
        midRidge.moveTo(0, horizon + dp(28));
        midRidge.lineTo(w * 0.06f, horizon);
        midRidge.lineTo(w * 0.16f, horizon - h * 0.16f);
        midRidge.lineTo(w * 0.24f, horizon - h * 0.08f);
        midRidge.lineTo(w * 0.34f, horizon - h * 0.2f);
        midRidge.lineTo(w * 0.46f, horizon - h * 0.05f);
        midRidge.lineTo(w * 0.58f, horizon - h * 0.11f);
        midRidge.lineTo(w * 0.72f, horizon + dp(8));
        midRidge.lineTo(w * 0.86f, horizon - h * 0.04f);
        midRidge.lineTo(w, horizon + dp(20));
        midRidge.lineTo(w, h);
        midRidge.lineTo(0, h);
        midRidge.close();

        nearRidge.reset();
        nearRidge.moveTo(0, horizon + dp(46));
        nearRidge.lineTo(w * 0.1f, horizon + dp(18));
        nearRidge.lineTo(w * 0.22f, horizon - h * 0.06f);
        nearRidge.lineTo(w * 0.33f, horizon + dp(10));
        nearRidge.lineTo(w * 0.48f, horizon + dp(36));
        nearRidge.lineTo(w * 0.7f, horizon + dp(20));
        nearRidge.lineTo(w * 0.86f, horizon + dp(40));
        nearRidge.lineTo(w, horizon + dp(28));
        nearRidge.lineTo(w, h);
        nearRidge.lineTo(0, h);
        nearRidge.close();

        shrubs.reset();
        float base = h * 0.92f;
        shrubs.moveTo(w * 0.72f, h);
        shrubs.lineTo(w * 0.78f, base);
        shrubs.lineTo(w * 0.82f, h * 0.84f);
        shrubs.lineTo(w * 0.86f, base - dp(8));
        shrubs.lineTo(w * 0.9f, h * 0.78f);
        shrubs.lineTo(w * 0.94f, base);
        shrubs.lineTo(w, h * 0.8f);
        shrubs.lineTo(w, h);
        shrubs.close();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
