package com.mapcontrol.util;

import android.view.MotionEvent;

/**
 * 2-4 parmaklı kaydırmayı dokunuş akışından tanır (saf; JVM'de sınanır). Bir parmak seti için hareket yalnızca bir kez
 * değerlendirilir. Parmak sayısı değişirse ölçüm yeni parmak gelene dek durur.
 */
public final class MultiFingerSwipeTracker {

    public enum Result {
        /** Olay normal yolunda devam eder. */
        PASS,
        /** Hareket tanınmıştı; olay uygulamaya iletilmez. */
        CONSUME,
        /** Hareket şimdi tanındı; alt görünümlere iptal gönderilir ve olay iletilmez. */
        CANCEL_CHILDREN
    }

    public interface OnSwipe {
        /** @return hareket işlendiyse true (dokunuş iptal edilir); false ise dokunuşa karışılmaz */
        boolean onSwipe(int fingers, int direction);
    }

    private static final float THRESHOLD_X_DP = 35f;
    private static final float THRESHOLD_Y_DP = 22f;

    private final OnSwipe onSwipe;
    /** Hareketin başladığı andaki parmak ortası. */
    private float downMidX;
    private float downMidY;
    /** Ekrandaki geçerli parmak sayısı (2-4). Parmak kalkınca 0: yeni parmak gelene dek değerlendirme yok. */
    private int activeFingers;
    /** Bu parmak seti için eşik aşıldı ve hareket yönü değerlendirildi. */
    private boolean evaluated;
    /** Hareket tanındı; parmaklar kalkana dek olaylar uygulamaya iletilmez. */
    private boolean consumed;

    public MultiFingerSwipeTracker(OnSwipe onSwipe) {
        this.onSwipe = onSwipe;
    }

    /**
     * @param action     {@code MotionEvent.getActionMasked()}
     * @param midX       tüm parmakların x ortalaması
     * @param midY       tüm parmakların y ortalaması
     * @param density    piksel/dp oranı
     */
    public Result onEvent(int action, int pointerCount, float midX, float midY, float density) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                reset();
                return Result.PASS;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (consumed) {
                    return Result.CONSUME;
                }
                if (pointerCount >= 2 && pointerCount <= 4) {
                    activeFingers = pointerCount;
                    evaluated = false;
                    downMidX = midX;
                    downMidY = midY;
                } else {
                    activeFingers = 0;
                }
                return Result.PASS;

            case MotionEvent.ACTION_POINTER_UP:
                if (consumed) {
                    return Result.CONSUME;
                }
                // Kalan parmaklar eski ortaya göre ölçülmez; yeni parmak gelene dek hareket yok.
                activeFingers = 0;
                return Result.PASS;

            case MotionEvent.ACTION_MOVE:
                if (consumed) {
                    return Result.CONSUME;
                }
                if (activeFingers >= 2 && pointerCount == activeFingers && !evaluated
                        && trySwipe(pointerCount, midX, midY, density)) {
                    consumed = true;
                    return Result.CANCEL_CHILDREN;
                }
                return Result.PASS;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean wasConsumed = consumed;
                reset();
                return wasConsumed ? Result.CONSUME : Result.PASS;

            default:
                return Result.PASS;
        }
    }

    private boolean trySwipe(int fingers, float midX, float midY, float density) {
        float dx = midX - downMidX;
        float dy = midY - downMidY;
        float absDx = Math.abs(dx);
        float absDy = Math.abs(dy);
        if (absDx <= THRESHOLD_X_DP * density && absDy <= THRESHOLD_Y_DP * density) {
            return false;
        }
        evaluated = true;
        int direction;
        if (absDx >= absDy) {
            direction = dx > 0 ? SplitScreenGesture.RIGHT : SplitScreenGesture.LEFT;
        } else {
            direction = dy < 0 ? SplitScreenGesture.UP : SplitScreenGesture.DOWN;
        }
        return onSwipe.onSwipe(fingers, direction);
    }

    private void reset() {
        activeFingers = 0;
        evaluated = false;
        consumed = false;
    }
}
