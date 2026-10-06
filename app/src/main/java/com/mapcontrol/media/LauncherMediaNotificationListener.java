package com.mapcontrol.media;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.mapcontrol.nav.GoogleMapsNavNotificationCoordinator;

/**
 * Aktif medya oturumlarını okumak ve Google Maps navigasyon bildirimini izlemek için dinleyici.
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
        GoogleMapsNavNotificationCoordinator.resyncActiveNotifications(this);
        Listener current = listener;
        if (current != null) {
            current.onListenerConnected();
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        GoogleMapsNavNotificationCoordinator.onNotificationPosted(this, sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        GoogleMapsNavNotificationCoordinator.onNotificationRemoved(this, sbn);
    }
}
