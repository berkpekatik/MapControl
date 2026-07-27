package com.mapcontrol.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.mapcontrol.vehicle.material.MaterialVehicleResources;

/**
 * OEM FrameAnimationView'in sadeleştirilmiş hali — materialvehicle PNG dizilerini oynatır.
 * İsimlendirme: {prefix}_{index} (ör. tailgate_0 … tailgate_48)
 */
public final class OemFrameAnimationView extends View {

    private static final long FRAME_DELAY_MS = 40L;

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MaterialVehicleResources resources;
    private String prefix;
    private int frameIndex;
    private int endIndex = -1;
    private boolean reverse;
    private boolean playing;
    private Bitmap currentFrame;

    private final Runnable frameRunnable = new Runnable() {
        @Override
        public void run() {
            if (!playing || resources == null || prefix == null) {
                return;
            }
            currentFrame = resources.loadFrame(prefix, frameIndex);
            invalidate();

            if (endIndex >= 0) {
                if (!reverse && frameIndex >= endIndex) {
                    stop();
                    return;
                }
                if (reverse && frameIndex <= endIndex) {
                    stop();
                    return;
                }
            }

            frameIndex += reverse ? -1 : 1;
            handler.postDelayed(this, FRAME_DELAY_MS);
        }
    };

    public OemFrameAnimationView(Context context) {
        super(context);
    }

    public OemFrameAnimationView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setResources(MaterialVehicleResources resources) {
        this.resources = resources;
    }

    public void play(String prefix, int startIndex, int endIndex, boolean reverse) {
        stop();
        this.prefix = prefix;
        this.frameIndex = startIndex;
        this.endIndex = endIndex;
        this.reverse = reverse;
        this.playing = true;
        setVisibility(VISIBLE);
        handler.post(frameRunnable);
    }

    public void showFrame(String prefix, int index) {
        stop();
        this.prefix = prefix;
        this.frameIndex = index;
        if (resources != null) {
            currentFrame = resources.loadFrame(prefix, index);
            invalidate();
        }
        setVisibility(VISIBLE);
    }

    public void stop() {
        playing = false;
        handler.removeCallbacks(frameRunnable);
    }

    public void hide() {
        stop();
        currentFrame = null;
        setVisibility(GONE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (currentFrame == null || currentFrame.isRecycled()) {
            return;
        }
        float scale = Math.min(
                (float) getWidth() / currentFrame.getWidth(),
                (float) getHeight() / currentFrame.getHeight());
        float dx = (getWidth() - currentFrame.getWidth() * scale) / 2f;
        float dy = (getHeight() - currentFrame.getHeight() * scale) / 2f;
        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);
        canvas.drawBitmap(currentFrame, 0f, 0f, paint);
        canvas.restore();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
