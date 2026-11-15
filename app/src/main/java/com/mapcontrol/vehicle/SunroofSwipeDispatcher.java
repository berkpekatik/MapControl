package com.mapcontrol.vehicle;

import android.content.Context;
import android.view.MotionEvent;

import com.mapcontrol.util.GestureConflicts;
import com.mapcontrol.util.MultiFingerSwipeTracker;
import com.mapcontrol.util.SplitScreenGesture;

import java.util.function.BooleanSupplier;

/**
 * Etkinliğin {@code dispatchTouchEvent} girişinde çok parmaklı kaydırmayı cam tavan / perde işlemine çevirir. Görünümlerden
 * önce çalıştığı için dokunuşu yakalayan kartlar hareketi engellemez; hareket tanınınca alt görünümlere iptal gönderilir.
 */
public final class SunroofSwipeDispatcher {

    public interface Downstream {
        boolean dispatch(MotionEvent event);
    }

    private final Context context;
    private final BooleanSupplier allowed;
    private final MultiFingerSwipeTracker tracker = new MultiFingerSwipeTracker(this::onSwipe);

    /** @param allowed false iken (düzenleme modu, üstte sayfa) hareket yok sayılır ve dokunuş normal akar */
    public SunroofSwipeDispatcher(Context context, BooleanSupplier allowed) {
        this.context = context;
        this.allowed = allowed;
    }

    public boolean dispatch(MotionEvent event, Downstream downstream) {
        int count = event.getPointerCount();
        float sumX = 0f;
        float sumY = 0f;
        for (int i = 0; i < count; i++) {
            sumX += event.getX(i);
            sumY += event.getY(i);
        }
        float density = context.getResources().getDisplayMetrics().density;
        switch (tracker.onEvent(event.getActionMasked(), count, sumX / count, sumY / count, density)) {
            case CONSUME:
                return true;
            case CANCEL_CHILDREN:
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                downstream.dispatch(cancel);
                cancel.recycle();
                return true;
            default:
                return downstream.dispatch(event);
        }
    }

    /**
     * Harekete bağlı işlemi çalıştırır. Hareket seçilmemişse veya başka bir özellik (bölünmüş ekran, harita kaydırma) aynı
     * hareketi kullanıyorsa dokunuşa karışmaz.
     */
    private boolean onSwipe(int fingers, int direction) {
        if (!allowed.getAsBoolean()) {
            return false;
        }
        int code = SplitScreenGesture.code(fingers, direction);
        SunroofGestureOverlayManager.GestureAction gesture = SunroofGestureStore.actionFor(context, code);
        if (gesture == null
                || GestureConflicts.findOwnerLabel(context, code, GestureConflicts.slotOf(gesture)) != 0) {
            return false;
        }
        SunroofGestureOverlayManager.getInstance(context).handleGestureAction(gesture, "Ekran");
        return true;
    }
}
