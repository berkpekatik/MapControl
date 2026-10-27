package com.mapcontrol.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.ColorInt;

import com.mapcontrol.util.SplitScreenLauncher;

/**
 * İki uygulamanın ekrandaki payını gösteren küçük önizleme. Bölme yönü gerçek bölmeyle aynı kuralı
 * kullanır: yatay ekranda sol/sağ, dikeyde üst/alt. Görünüm ekranın en-boy oranında ölçülür.
 */
public class SplitRatioView extends View {

    private final Paint firstPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint secondPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float firstShare = 0.5f;
    private final boolean sideBySide;
    private final float aspect;

    public SplitRatioView(Context context) {
        super(context);
        sideBySide = SplitScreenLauncher.splitsSideBySide(context);
        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        aspect = dm.heightPixels > 0 ? (float) dm.widthPixels / dm.heightPixels : 1.6f;
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
        dividerPaint.setStyle(Paint.Style.FILL);
    }

    public void setShare(float firstShare) {
        this.firstShare = firstShare;
        invalidate();
    }

    /** İlk alan vurgu, ikinci alan nötr renkte çizilir. */
    public void setColors(@ColorInt int first, @ColorInt int second, @ColorInt int divider,
            @ColorInt int label) {
        firstPaint.setColor(first);
        secondPaint.setColor(second);
        dividerPaint.setColor(divider);
        labelPaint.setColor(label);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = Math.round(w / Math.max(0.5f, aspect));
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float radius = Math.min(w, h) * 0.08f;
        float gap = Math.max(2f, Math.min(w, h) * 0.02f);
        labelPaint.setTextSize(Math.min(w, h) * 0.22f);

        if (sideBySide) {
            float edge = w * firstShare;
            rect.set(0, 0, edge - gap, h);
            canvas.drawRoundRect(rect, radius, radius, firstPaint);
            drawLabel(canvas, "1", rect);
            rect.set(edge + gap, 0, w, h);
            canvas.drawRoundRect(rect, radius, radius, secondPaint);
            drawLabel(canvas, "2", rect);
        } else {
            float edge = h * firstShare;
            rect.set(0, 0, w, edge - gap);
            canvas.drawRoundRect(rect, radius, radius, firstPaint);
            drawLabel(canvas, "1", rect);
            rect.set(0, edge + gap, w, h);
            canvas.drawRoundRect(rect, radius, radius, secondPaint);
            drawLabel(canvas, "2", rect);
        }
    }

    private void drawLabel(Canvas canvas, String text, RectF area) {
        float baseline = area.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f;
        canvas.drawText(text, area.centerX(), baseline, labelPaint);
    }
}
