package com.mapcontrol.manager;

import com.desaysv.ivi.vdb.event.id.sms.VDValueAction;

/**
 * Direksiyon ses tuşu hareket filtresi (MapControl PR #48'den). Yalnızca kısa bas-bırak
 * ({@code ACTION_PRESS=0} → {@code ACTION_RELEASE=3}) tetikler; uzun basma ({@code 1}, {@code 2}, {@code 4})
 * Android Auto / aracın kendi asistanı için dokunulmadan bırakılır.
 */
final class VoiceKeyGesture {
    private boolean longPress;

    synchronized boolean accept(int action) {
        if (action == VDValueAction.ACTION_PRESS) {
            longPress = false;
            return false;
        }
        if (action == VDValueAction.ACTION_LONG_PRESS || action == VDValueAction.ACTION_SUPER_LONG_PRESS) {
            longPress = true;
            return false;
        }
        if (action == VDValueAction.ACTION_LONG_RELEASE) {
            longPress = false;
            return false;
        }
        if (action == VDValueAction.ACTION_RELEASE) {
            boolean trigger = !longPress;
            longPress = false;
            return trigger;
        }
        return false;
    }
}
