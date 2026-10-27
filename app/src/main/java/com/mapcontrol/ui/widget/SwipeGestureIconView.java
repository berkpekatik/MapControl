package com.mapcontrol.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.ColorInt;

import com.mapcontrol.util.SplitScreenGesture;

/**
 * Kaydırma hareketi çizimi: araç ekranını temsil eden çerçeve, ekrana değen parmak sayısı kadar
 * nokta ve kaydırma yönünü gösteren ok. Renk dışarıdan verilir; tema değişince sekme yeniden
 * kurulduğunda güncellenir.
 */
public class SwipeGestureIconView extends View {

    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fingerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame = new RectF();

    private int fingers = 2;
    private int direction = SplitScreenGesture.UP;
    @ColorInt
    private int tint = 0xFF888888;

    public SwipeGestureIconView(Context context) {
        super(context);
        framePaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        fingerPaint.setStyle(Paint.Style.FILL);
    }

    public void setGesture(int fingers, int direction) {
        this.fingers = Math.max(1, Math.min(4, fingers));
        this.direction = direction;
        invalidate();
    }

    public void setTint(@ColorInt int tint) {
        this.tint = tint;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float unit = Math.min(w, h);
        float stroke = Math.max(2f, unit * 0.035f);

        framePaint.setColor(tint);
        framePaint.setAlpha(150);
        framePaint.setStrokeWidth(stroke);
        float inset = stroke;
        frame.set(inset, inset, w - inset, h - inset);
        canvas.drawRoundRect(frame, unit * 0.12f, unit * 0.12f, framePaint);

        float dx = 0f;
        float dy = 0f;
        switch (direction) {
            case SplitScreenGesture.LEFT:
                dx = -1f;
                break;
            case SplitScreenGesture.RIGHT:
                dx = 1f;
                break;
            case SplitScreenGesture.DOWN:
                dy = 1f;
                break;
            case SplitScreenGesture.UP:
            default:
                dy = -1f;
                break;
        }
        // Parmaklar kaydırma yönüne dik sıralanır.
        float px = -dy;
        float py = dx;

        float cx = w / 2f;
        float cy = h / 2f;
        float reach = unit * 0.30f;
        float startX = cx - dx * reach;
        float startY = cy - dy * reach;
        float endX = cx + dx * reach;
        float endY = cy + dy * reach;

        arrowPaint.setColor(tint);
        arrowPaint.setStrokeWidth(stroke * 1.3f);
        canvas.drawLine(startX, startY, endX, endY, arrowPaint);
        float head = unit * 0.13f;
        canvas.drawLine(endX, endY,
                endX - dx * head + px * head, endY - dy * head + py * head, arrowPaint);
        canvas.drawLine(endX, endY,
                endX - dx * head - px * head, endY - dy * head - py * head, arrowPaint);

        fingerPaint.setColor(tint);
        float spacing = unit * 0.15f;
        float radius = unit * 0.055f;
        for (int i = 0; i < fingers; i++) {
            float offset = (i - (fingers - 1) / 2f) * spacing;
            canvas.drawCircle(startX + px * offset, startY + py * offset, radius, fingerPaint);
        }
    }
}
