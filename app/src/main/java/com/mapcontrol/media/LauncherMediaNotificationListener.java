package com.mapcontrol.media;

import android.service.notification.NotificationListenerService;

/**
 * Aktif medya oturumlarını okumak için bildirim dinleyici.
 * Ayarlar &gt; Bildirim erişimi üzerinden etkinleştirilmesi gerekebilir.
 */
public final class LauncherMediaNotificationListener extends NotificationListenerService {

    private static volatile Listener listener;

    interface Listener {
        void onListenerConnected();
    }

    static void setListener(Listener value) {
        listener = value;
    }

    @Override
    public void onListenerConnected() {
        Listener current = listener;
        if (current != null) {
            current.onListenerConnected();
        }
    }
}
