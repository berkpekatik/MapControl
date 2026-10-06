package com.mapcontrol.nav;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;

import com.mapcontrol.media.LauncherMediaController;
import com.mapcontrol.service.MapControlService;
import com.mapcontrol.util.AppLaunchHelper;
import com.mapcontrol.util.ClusterNavigationState;
import com.mapcontrol.util.TargetPackageStore;

/**
 * Google Maps navigasyon bildirimini yansıtma açılışına, cluster kartlarına ve UI yayınına bağlar.
 * Bildirim kalkınca cluster yansıtması kapatılmaz; kartlar gizlenir.
 */
public final class GoogleMapsNavNotificationCoordinator {

    /**
     * Geçici: navigasyon bildirimi gelince cluster yansıtmasını otomatik açma.
     * Tekrar açmak için {@code false} yapın.
     */
    private static final boolean TEMPORARILY_DISABLE_AUTO_CLUSTER_OPEN = true;

    public static final String PREF_AUTO_PROJECTION = "google_maps_notification_auto_projection";
    public static final String ACTION_GOOGLE_MAPS_NAV_SNAPSHOT =
            "com.mapcontrol.action.GOOGLE_MAPS_NAV_SNAPSHOT";
    public static final String EXTRA_ACTIVE = "active";
    public static final String EXTRA_MANEUVER = "maneuverHint";
    public static final String EXTRA_DISTANCE = "distanceTitle";
    public static final String EXTRA_SUMMARY = "routeSummary";

    private static final long OPEN_DEBOUNCE_MS = 2500L;

    private static volatile GoogleMapsNavSnapshot lastSnapshot = GoogleMapsNavSnapshot.inactive();
    private static long lastOpenRequestUptimeMs;

    private GoogleMapsNavNotificationCoordinator() {
    }

    public static boolean isAutoProjectionEnabled(Context context) {
        if (TEMPORARILY_DISABLE_AUTO_CLUSTER_OPEN) {
            return false;
        }
        return TargetPackageStore.prefs(context).getBoolean(PREF_AUTO_PROJECTION, true);
    }

    public static void setAutoProjectionEnabled(Context context, boolean enabled) {
        TargetPackageStore.prefs(context).edit().putBoolean(PREF_AUTO_PROJECTION, enabled).apply();
    }

    public static GoogleMapsNavSnapshot getLastSnapshot() {
        return lastSnapshot;
    }

    public static void onNotificationPosted(Context context, @Nullable StatusBarNotification sbn) {
        if (!isGoogleMapsNotification(sbn)) {
            return;
        }
        GoogleMapsNavSnapshot snapshot = GoogleMapsNavNotificationParser.parse(sbn);
        if (snapshot.active) {
            publish(context, snapshot);
            requestOpenIfEligible(context.getApplicationContext());
            return;
        }
        if (lastSnapshot.active) {
            publish(context, GoogleMapsNavSnapshot.inactive());
        }
    }

    public static void onNotificationRemoved(Context context, @Nullable StatusBarNotification sbn) {
        if (!shouldClearAfterMapsNotificationRemoved(sbn)) {
            return;
        }
        publish(context, GoogleMapsNavSnapshot.inactive());
    }

    /**
     * Bildirim dinleyici bağlandığında veya ayar açıldığında mevcut Maps nav bildirimini yeniden okur.
     */
    public static void resyncActiveNotifications(@Nullable NotificationListenerService service) {
        if (service == null) {
            return;
        }
        try {
            StatusBarNotification[] active = service.getActiveNotifications();
            if (active == null) {
                return;
            }
            for (StatusBarNotification sbn : active) {
                if (!GoogleMapsNavNotificationParser.isNavigationNotification(sbn)) {
                    continue;
                }
                GoogleMapsNavSnapshot snapshot = GoogleMapsNavNotificationParser.parse(sbn);
                if (snapshot.active) {
                    publish(service, snapshot);
                    return;
                }
            }
            if (lastSnapshot.active) {
                publish(service, GoogleMapsNavSnapshot.inactive());
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isGoogleMapsNotification(@Nullable StatusBarNotification sbn) {
        return sbn != null
                && GoogleMapsNavNotificationParser.PACKAGE_GOOGLE_MAPS.equals(sbn.getPackageName());
    }

    /**
     * Bildirim silinirken extras boşalabilir; son aktif rehberlik veya nav kategorisine bakılır.
     */
    private static boolean shouldClearAfterMapsNotificationRemoved(@Nullable StatusBarNotification sbn) {
        if (!isGoogleMapsNotification(sbn)) {
            return false;
        }
        if (lastSnapshot.active) {
            return true;
        }
        if (GoogleMapsNavNotificationParser.isNavigationNotification(sbn)) {
            return true;
        }
        android.app.Notification notification = sbn.getNotification();
        return notification != null
                && android.app.Notification.CATEGORY_NAVIGATION.equals(notification.category);
    }

    private static void publish(Context context, GoogleMapsNavSnapshot snapshot) {
        lastSnapshot = snapshot;
        Context app = context.getApplicationContext();
        syncClusterPresentation(app, snapshot);
        Intent intent = new Intent(ACTION_GOOGLE_MAPS_NAV_SNAPSHOT);
        intent.setPackage(app.getPackageName());
        intent.putExtra(EXTRA_ACTIVE, snapshot.active);
        if (snapshot.maneuverHint != null) {
            intent.putExtra(EXTRA_MANEUVER, snapshot.maneuverHint);
        }
        if (snapshot.distanceTitle != null) {
            intent.putExtra(EXTRA_DISTANCE, snapshot.distanceTitle);
        }
        if (snapshot.routeSummary != null) {
            intent.putExtra(EXTRA_SUMMARY, snapshot.routeSummary);
        }
        app.sendBroadcast(intent);
    }

    private static void syncClusterPresentation(Context app, GoogleMapsNavSnapshot snapshot) {
        if (GoogleMapsClusterNavOverlay.isEnabled(app)) {
            GoogleMapsClusterNavOverlay.getInstance(app).apply(snapshot);
        } else {
            GoogleMapsClusterNavOverlay.getInstance(app).hide();
        }
        GoogleMapsClusterNavVDBusPublisher.publish(app, snapshot);
    }

    public static boolean isClusterOverlayEnabled(Context context) {
        return GoogleMapsClusterNavOverlay.isEnabled(context);
    }

    public static void setClusterOverlayEnabled(Context context, boolean enabled) {
        GoogleMapsClusterNavOverlay.setEnabled(context, enabled);
        if (enabled) {
            GoogleMapsClusterNavOverlay.getInstance(context).apply(getLastSnapshot());
        }
    }

    public static boolean isClusterVDBusEnabled(Context context) {
        return GoogleMapsClusterNavVDBusPublisher.isEnabled(context);
    }

    public static void setClusterVDBusEnabled(Context context, boolean enabled) {
        GoogleMapsClusterNavVDBusPublisher.setEnabled(context, enabled);
        if (enabled) {
            GoogleMapsClusterNavVDBusPublisher.publish(context, getLastSnapshot());
        }
    }

    private static void requestOpenIfEligible(Context app) {
        if (TEMPORARILY_DISABLE_AUTO_CLUSTER_OPEN) {
            return;
        }
        if (!isAutoProjectionEnabled(app)) {
            return;
        }
        if (!LauncherMediaController.isNotificationAccessEnabled(app)) {
            return;
        }
        if (!AppLaunchHelper.GOOGLE_MAPS_PACKAGE.equals(TargetPackageStore.read(app))) {
            return;
        }
        if (ClusterNavigationState.getLastKnownOpen()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastOpenRequestUptimeMs < OPEN_DEBOUNCE_MS) {
            return;
        }
        lastOpenRequestUptimeMs = now;
        try {
            Intent service = new Intent(app, MapControlService.class);
            service.setAction(MapControlService.ACTION_OPEN_CLUSTER_FROM_MAPS_NOTIFICATION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(service);
            } else {
                app.startService(service);
            }
        } catch (Exception ignored) {
        }
    }
}
