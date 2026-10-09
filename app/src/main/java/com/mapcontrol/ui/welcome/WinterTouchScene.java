package com.mapcontrol.ui.welcome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
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
 * Kış Dokunuşu zemini: mor gece, köşe çiçekleri, düşen yapraklar, halka ve kelebekler.
 */
public class WinterTouchScene extends View {

    private static final int PETALS = 18;
    private static final float[][] BOKEH = {
            {0.18f, 0.22f, 0.16f},
            {0.72f, 0.18f, 0.2f},
            {0.84f, 0.62f, 0.18f},
            {0.3f, 0.78f, 0.14f},
            {0.55f, 0.4f, 0.22f}
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final Petal[] petals = new Petal[PETALS];
    private LinearGradient background;
    private RadialGradient floorGlow;
    private int backgroundW;
    private int backgroundH;
    private boolean running;
    private long startMs;
    private long lastMs;

    public WinterTouchScene(Context context) {
        this(context, null);
    }

    public WinterTouchScene(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setAntiAlias(true);
        paint.setDither(true);
        for (int i = 0; i < petals.length; i++) {
            petals[i] = new Petal();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        startMs = SystemClock.uptimeMillis();
        lastMs = startMs;
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
        background = null;
        if (w <= 0 || h <= 0) {
            return;
        }
        for (int i = 0; i < petals.length; i++) {
            Petal petal = petals[i];
            petal.x = (i * 97 % w);
            petal.y = (i * 53 % h);
            petal.vy = dp(28) + (i % 5) * dp(8);
            petal.vx = dp(i % 2 == 0 ? 8 : -6);
            petal.phase = i * 0.7f;
            petal.size = dp(7 + (i % 4) * 3);
            petal.spin = (i % 2 == 0 ? 40f : -35f);
            petal.color = i % 3 == 0 ? 0xCCFFF4F8 : (i % 3 == 1 ? 0xD8F3C2D4 : 0xB8E7A8C0);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        float t = (now - startMs) / 1000f;
        float dt = Math.min(0.05f, (now - lastMs) / 1000f);
        lastMs = now;

        drawBackground(canvas, w, h);
        drawBokeh(canvas, w, h, t);
        drawFloor(canvas, w, h);
        drawBlossoms(canvas, w, h, t);
        drawPetals(canvas, w, h, t, dt);
        float radius = Math.min(w, h) * 0.34f;
        drawRing(canvas, w / 2f, h / 2f, radius, t);
        drawSparkHearts(canvas, w / 2f, h / 2f, radius, t);
        drawButterfly(canvas, w / 2f + radius * 1.05f, h / 2f - radius * 0.78f, t, 0f, radius * 0.16f);
        drawButterfly(canvas, w / 2f - radius * 1.12f, h / 2f + radius * 0.7f, t, 2.1f, radius * 0.13f);

        if (running) {
            postInvalidateOnAnimation();
        }
    }

    private void drawBackground(Canvas canvas, int w, int h) {
        if (background == null || backgroundW != w || backgroundH != h) {
        background = new LinearGradient(0, 0, 0, h,
                new int[]{0xFF161028, 0xFF2A1844, 0xFF4A2A58, 0xFF6E3A62},
                new float[]{0f, 0.42f, 0.78f, 1f},
                Shader.TileMode.CLAMP);
        floorGlow = new RadialGradient(w * 0.5f, h * 0.95f, Math.max(1, h * 0.55f),
                0x66E7A0C0, 0x00000000, Shader.TileMode.CLAMP);
            backgroundW = w;
            backgroundH = h;
        }
        paint.setShader(background);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, w, h, paint);
        if (floorGlow != null) {
            paint.setShader(floorGlow);
            canvas.drawRect(0, 0, w, h, paint);
        }
        paint.setShader(null);
    }

    private void drawBokeh(Canvas canvas, int w, int h, float t) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        for (int i = 0; i < BOKEH.length; i++) {
            float x = BOKEH[i][0] * w + (float) Math.sin(t * 0.35f + i) * dp(10);
            float y = BOKEH[i][1] * h + (float) Math.cos(t * 0.28f + i) * dp(8);
            float r = BOKEH[i][2] * Math.min(w, h);
            paint.setColor(0x18F6C6DC);
            canvas.drawCircle(x, y, r, paint);
            paint.setColor(0x28F8D0E2);
            canvas.drawCircle(x, y, r * 0.45f, paint);
        }
    }

    private void drawFloor(Canvas canvas, int w, int h) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x33E8B0C8);
        oval.set(w * 0.18f, h * 0.86f, w * 0.82f, h * 0.98f);
        canvas.drawOval(oval, paint);
        paint.setColor(0x22F3D0DE);
        oval.set(w * 0.28f, h * 0.9f, w * 0.72f, h * 0.97f);
        canvas.drawOval(oval, paint);
    }

    private void drawBlossoms(Canvas canvas, int w, int h, float t) {
        float sway = (float) Math.sin(t * 0.9f) * dp(5);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(0x66C98AA4);
        path.reset();
        path.moveTo(-dp(10), dp(30));
        path.quadTo(dp(70), dp(10) + sway, dp(160), -dp(8));
        canvas.drawPath(path, paint);
        path.reset();
        path.moveTo(w + dp(10), h - dp(24));
        path.quadTo(w - dp(80), h - dp(8) + sway, w - dp(170), h + dp(6));
        canvas.drawPath(path, paint);

        flower(canvas, dp(28) + sway, dp(22), dp(16), 0xD8F8D0DE, 0xE6FFF6FA);
        flower(canvas, dp(62), dp(8) + sway * 0.4f, dp(12), 0xC8F3C4D6, 0xDDFFF8FB);
        flower(canvas, dp(96) + sway * 0.3f, dp(26), dp(18), 0xE0FBE4EE, 0xF0FFF9FC);
        flower(canvas, dp(18), dp(58), dp(11), 0x99F0B8CE, 0xAAFFF4F8);
        flower(canvas, w - dp(30) - sway, h - dp(26), dp(17), 0xD8F8D0DE, 0xE6FFF6FA);
        flower(canvas, w - dp(68), h - dp(12) - sway * 0.4f, dp(13), 0xC8F3C4D6, 0xDDFFF8FB);
        flower(canvas, w - dp(104) - sway * 0.3f, h - dp(34), dp(19), 0xE0FBE4EE, 0xF0FFF9FC);
        flower(canvas, w - dp(22), h - dp(64), dp(10), 0x99F0B8CE, 0xAAFFF4F8);
    }

    private void flower(Canvas canvas, float x, float y, float r, int petal, int center) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        for (int i = 0; i < 5; i++) {
            canvas.save();
            canvas.rotate(i * 72f, x, y);
            paint.setColor(petal);
            oval.set(x - r * 0.38f, y - r * 1.15f, x + r * 0.38f, y - r * 0.12f);
            canvas.drawOval(oval, paint);
            canvas.restore();
        }
        paint.setColor(center);
        canvas.drawCircle(x, y, r * 0.22f, paint);
    }

    private void drawPetals(Canvas canvas, int w, int h, float t, float dt) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        for (Petal petal : petals) {
            petal.y += petal.vy * dt;
            petal.x += petal.vx * dt + (float) Math.sin(t + petal.phase) * dp(12) * dt;
            petal.rot += petal.spin * dt;
            if (petal.y > h + dp(30)) {
                petal.y = -dp(20);
                petal.x = (petal.x + w * 0.37f) % w;
            }
            if (petal.x < -dp(20)) {
                petal.x = w + dp(10);
            } else if (petal.x > w + dp(20)) {
                petal.x = -dp(10);
            }
            canvas.save();
            canvas.translate(petal.x, petal.y);
            canvas.rotate(petal.rot);
            paint.setColor(petal.color);
            oval.set(-petal.size * 0.45f, -petal.size, petal.size * 0.45f, petal.size * 0.15f);
            canvas.drawOval(oval, paint);
            canvas.restore();
        }
    }

    private void drawRing(Canvas canvas, float cx, float cy, float radius, float t) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(2));
        paint.setColor(0x66F4C9DA);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStrokeWidth(dp(3));
        paint.setColor(0xE6F8D7E6);
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(oval, -70f + t * 18f, 52f, false, paint);
    }

    private void drawSparkHearts(Canvas canvas, float cx, float cy, float radius, float t) {
        float[] angles = {-150f, -28f, 128f, 200f};
        for (int i = 0; i < angles.length; i++) {
            float bob = (float) Math.sin(t * 1.4f + i) * dp(4);
            double rad = Math.toRadians(angles[i]);
            float x = cx + (float) Math.cos(rad) * (radius + dp(18));
            float y = cy + (float) Math.sin(rad) * (radius + dp(18)) + bob;
            paint.setColor(i % 2 == 0 ? 0xCCF7D0E0 : 0x99F3C4D6);
            drawHeart(canvas, x, y, dp(i % 2 == 0 ? 7 : 5));
        }
    }

    private void drawButterfly(Canvas canvas, float anchorX, float anchorY, float t, float phase, float scale) {
        float hoverX = anchorX + (float) Math.sin(t * 0.7f + phase) * dp(16);
        float hoverY = anchorY + (float) Math.cos(t * 0.9f + phase) * dp(10);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        for (int i = 6; i >= 1; i--) {
            float trailT = t - i * 0.08f;
            float x = anchorX + (float) Math.sin(trailT * 0.7f + phase) * dp(16);
            float y = anchorY + (float) Math.cos(trailT * 0.9f + phase) * dp(10);
            paint.setColor(Color.argb(28 - i * 3, 248, 220, 232));
            canvas.drawCircle(x - i * dp(7), y + i * dp(2), dp(2), paint);
        }
        float flap = (float) Math.sin(t * 10f + phase);
        canvas.save();
        canvas.translate(hoverX, hoverY);
        canvas.scale(scale / dp(16), scale / dp(16));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f);
        paint.setColor(0xE8F8E4EE);
        wing(canvas, flap, 1f);
        wing(canvas, flap, -1f);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(0, -7f, 0, 9f, paint);
        canvas.drawLine(0, -7f, -4f, -12f, paint);
        canvas.drawLine(0, -7f, 4f, -12f, paint);
        canvas.restore();
    }

    private void wing(Canvas canvas, float flap, float side) {
        float open = 0.45f + 0.55f * Math.abs(flap);
        path.reset();
        path.moveTo(0, 0);
        path.cubicTo(side * -6f * open, -16f, side * -26f * open, -14f, side * -20f * open, 2f);
        path.cubicTo(side * -14f * open, 8f, side * -4f, 5f, 0, 0);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(path, paint);
        path.reset();
        path.moveTo(0, 2);
        path.cubicTo(side * -4f * open, 8f, side * -18f * open, 16f, side * -12f * open, 6f);
        paint.setColor(0x88F8D5E4);
        canvas.drawPath(path, paint);
        paint.setColor(0xE8F8E4EE);
    }

    private void drawHeart(Canvas canvas, float x, float y, float size) {
        canvas.save();
        canvas.translate(x, y);
        path.reset();
        path.moveTo(0, size * 0.3f);
        path.cubicTo(-size * 0.6f, -size * 0.35f, -size * 0.65f, size * 0.45f, 0, size * 0.85f);
        path.cubicTo(size * 0.65f, size * 0.45f, size * 0.6f, -size * 0.35f, 0, size * 0.3f);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Petal {
        float x;
        float y;
        float vx;
        float vy;
        float phase;
        float size;
        float spin;
        float rot;
        int color;
    }
}
