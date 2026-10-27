package com.mapcontrol.media;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.mapcontrol.nav.GoogleMapsNavNotificationCoordinator;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Aktif medya oturumlarını okumak ve Google Maps navigasyon bildirimini izlemek için dinleyici.
 * Ayarlar &gt; Bildirim erişimi üzerinden etkinleştirilmesi gerekebilir.
 */
public final class LauncherMediaNotificationListener extends NotificationListenerService {

    private static volatile Listener listener;
    private static final CopyOnWriteArraySet<Runnable> accessReadyWatchers = new CopyOnWriteArraySet<>();
    private static final CopyOnWriteArraySet<Runnable> accessLostWatchers = new CopyOnWriteArraySet<>();

    interface Listener {
        void onListenerConnected();
    }

    static void setListener(Listener value) {
        listener = value;
    }

    static void watchAccess(Runnable onReady, Runnable onLost) {
        if (onReady != null) {
            accessReadyWatchers.add(onReady);
        }
        if (onLost != null) {
            accessLostWatchers.add(onLost);
        }
    }

    @Override
    public void onListenerConnected() {
        GoogleMapsNavNotificationCoordinator.onListenerConnected(this);
        GoogleMapsNavNotificationCoordinator.resyncActiveNotifications(this);
        Listener current = listener;
        if (current != null) {
            current.onListenerConnected();
        }
        for (Runnable watcher : accessReadyWatchers) {
            watcher.run();
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

    @Override
    public void onListenerDisconnected() {
        GoogleMapsNavNotificationCoordinator.onListenerDisconnected();
        for (Runnable watcher : accessLostWatchers) {
            watcher.run();
        }
    }
}
