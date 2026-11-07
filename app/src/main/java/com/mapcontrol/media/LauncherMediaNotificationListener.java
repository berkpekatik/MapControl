package com.mapcontrol.media;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.mapcontrol.launcher.NavigationSource;
import com.mapcontrol.nav.GoogleMapsNavNotificationCoordinator;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Aktif medya oturumlarını okumak, Google Maps navigasyon bildirimini izlemek ve Yandex rehberlik bildirimini
 * Sahne Navigasyon paneline ({@link NavigationSource}) iletmek için dinleyici.
 * Ayarlar &gt; Bildirim erişimi üzerinden etkinleştirilmesi gerekebilir.
 */
public final class LauncherMediaNotificationListener extends NotificationListenerService {

    /** Her medya denetleyicisi (ana ekran kartı, spor, Mini gösterge) kendi dinleyicisini ekler; biri durunca ötekiler silinmez. */
    private static final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();
    private static final CopyOnWriteArraySet<Runnable> accessReadyWatchers = new CopyOnWriteArraySet<>();
    private static final CopyOnWriteArraySet<Runnable> accessLostWatchers = new CopyOnWriteArraySet<>();

    interface Listener {
        void onListenerConnected();
    }

    static void addListener(Listener value) {
        if (value != null) {
            listeners.add(value);
        }
    }

    static void removeListener(Listener value) {
        listeners.remove(value);
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
        NavigationSource.onListenerConnected(this);
        BtSessionWatcher.start(this);
        for (Listener current : listeners) {
            current.onListenerConnected();
        }
        for (Runnable watcher : accessReadyWatchers) {
            watcher.run();
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        GoogleMapsNavNotificationCoordinator.onNotificationPosted(this, sbn);
        NavigationSource.onNotificationPosted(this, sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        GoogleMapsNavNotificationCoordinator.onNotificationRemoved(this, sbn);
        NavigationSource.onNotificationRemoved(this, sbn);
    }

    @Override
    public void onListenerDisconnected() {
        GoogleMapsNavNotificationCoordinator.onListenerDisconnected();
        NavigationSource.onListenerDisconnected();
        BtSessionWatcher.stop();
        for (Runnable watcher : accessLostWatchers) {
            watcher.run();
        }
    }
}
