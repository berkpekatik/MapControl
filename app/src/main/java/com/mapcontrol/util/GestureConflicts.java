package com.mapcontrol.util;

import android.content.Context;

import androidx.annotation.StringRes;

import com.mapcontrol.R;
import com.mapcontrol.manager.CarSystemGestureController;
import com.mapcontrol.vehicle.SunroofGestureOverlayManager.GestureAction;
import com.mapcontrol.vehicle.SunroofGestureStore;

/**
 * Aynı kaydırma hareketinin birden fazla özelliğe bağlanmasını önler.
 *
 * <p>Hareket sahipleri: harita kaydırma (iki parmak sola/sağa), bölünmüş ekran ve cam tavan ile
 * perde işlemleri. Yeni bir hareket seçilirken {@link #findOwnerLabel} başka bir sahip döndürürse
 * seçim reddedilir ve kullanıcı uyarılır.
 */
public final class GestureConflicts {

    public enum Slot {
        MAP_SWIPE,
        SPLIT_SCREEN,
        SUNROOF_OPEN,
        SUNROOF_CLOSE,
        SHADE_OPEN,
        SHADE_CLOSE
    }

    private GestureConflicts() {
    }

    public static Slot slotOf(GestureAction action) {
        switch (action) {
            case SWIPE_RIGHT:
                return Slot.SUNROOF_OPEN;
            case SWIPE_LEFT:
                return Slot.SUNROOF_CLOSE;
            case SWIPE_UP:
                return Slot.SHADE_CLOSE;
            case SWIPE_DOWN:
            default:
                return Slot.SHADE_OPEN;
        }
    }

    @StringRes
    public static int labelOf(Slot slot) {
        switch (slot) {
            case MAP_SWIPE:
                return R.string.gesture_owner_map_swipe;
            case SPLIT_SCREEN:
                return R.string.gesture_owner_split_screen;
            case SUNROOF_OPEN:
                return R.string.gesture_owner_sunroof_open;
            case SUNROOF_CLOSE:
                return R.string.gesture_owner_sunroof_close;
            case SHADE_OPEN:
                return R.string.gesture_owner_shade_open;
            case SHADE_CLOSE:
            default:
                return R.string.gesture_owner_shade_close;
        }
    }

    /**
     * {@code code} hareketini {@code requester} dışında kullanan özelliğin ad kaynağı; kimse
     * kullanmıyorsa 0.
     */
    @StringRes
    public static int findOwnerLabel(Context context, int code, Slot requester) {
        if (!SplitScreenGesture.isValid(code)) {
            return 0;
        }
        if (requester != Slot.MAP_SWIPE
                && SplitScreenGesture.isReservedByMapSwipe(code)
                && CarSystemGestureController.isEnabled(context)) {
            return labelOf(Slot.MAP_SWIPE);
        }
        if (requester != Slot.SPLIT_SCREEN
                && SplitScreenStore.getGestureCode(context) == code) {
            return labelOf(Slot.SPLIT_SCREEN);
        }
        for (GestureAction action : GestureAction.values()) {
            Slot slot = slotOf(action);
            if (slot != requester && SunroofGestureStore.getCode(context, action) == code) {
                return labelOf(slot);
            }
        }
        return 0;
    }

    /**
     * Harita kaydırma açılırken iki parmak sola/sağa kodlarını başka bir özellik kullanıyor mu?
     * Kullanıyorsa o özelliğin ad kaynağı, yoksa 0.
     */
    @StringRes
    public static int findMapSwipeConflict(Context context) {
        int owner = findOwnerLabel(context, SplitScreenGesture.CODE_TWO_LEFT, Slot.MAP_SWIPE);
        if (owner != 0) {
            return owner;
        }
        return findOwnerLabel(context, SplitScreenGesture.CODE_TWO_RIGHT, Slot.MAP_SWIPE);
    }
}
