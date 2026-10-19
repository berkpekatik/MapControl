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
 * Petal Maps navigasyon bildirimini Google Maps akışından ayrı izler.
 * Cluster kartları ve yansıtma açılışı yalnız Petal paketi için çalışır.
 */
public final class PetalMapsNavNotificationCoordinator {

    private static final long OPEN_DEBOUNCE_MS = 2500L;

    @Nullable
    private static String activeKey;
    private static long lastOpenRequestUptimeMs;

    private PetalMapsNavNotificationCoordinator() {
    }

    public static boolean isClusterOverlayEnabled(Context context) {
        return context.getSharedPreferences(ClusterNavOverlayOffsets.PREFS, Context.MODE_PRIVATE)
                .getBoolean(GoogleMapsClusterNavOverlay.PREF_PETAL_ENABLED, true);
    }

    public static void setClusterOverlayEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(ClusterNavOverlayOffsets.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(GoogleMapsClusterNavOverlay.PREF_PETAL_ENABLED, enabled)
                .apply();
        GoogleMapsClusterNavOverlay overlay = GoogleMapsClusterNavOverlay.getPetalInstance(context);
        if (!enabled) {
            overlay.hide();
            return;
        }
        overlay.invalidateCache();
        if (context instanceof NotificationListenerService) {
            resyncActiveNotifications((NotificationListenerService) context);
        }
    }

    public static void onNotificationPosted(Context context, @Nullable StatusBarNotification sbn) {
        if (!isPetal(sbn)) {
            return;
        }
        GoogleMapsNavSnapshot snapshot = PetalMapsNavNotificationParser.parse(sbn);
        if (!snapshot.active) {
            if (activeKey != null && activeKey.equals(sbn.getKey())) {
                clear(context);
            }
            return;
        }
        activeKey = sbn.getKey();
        GoogleMapsClusterNavOverlay.getPetalInstance(context).apply(snapshot);
        requestOpenIfEligible(context.getApplicationContext());
    }

    public static void onNotificationRemoved(Context context, @Nullable StatusBarNotification sbn) {
        if (!isPetal(sbn)) {
            return;
        }
        if (activeKey != null && !activeKey.equals(sbn.getKey())) {
            return;
        }
        clear(context);
    }

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
                GoogleMapsNavSnapshot snapshot = PetalMapsNavNotificationParser.parse(sbn);
                if (snapshot.active) {
                    activeKey = sbn.getKey();
                    GoogleMapsClusterNavOverlay.getPetalInstance(service).apply(snapshot);
                    requestOpenIfEligible(service.getApplicationContext());
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        if (activeKey != null) {
            clear(service);
        }
    }

    private static void clear(Context context) {
        activeKey = null;
        GoogleMapsClusterNavOverlay.getPetalInstance(context).apply(GoogleMapsNavSnapshot.inactive());
    }

    private static boolean isPetal(@Nullable StatusBarNotification sbn) {
        return sbn != null
                && PetalMapsNavNotificationParser.PACKAGE_PETAL_MAPS.equals(sbn.getPackageName());
    }

    private static void requestOpenIfEligible(Context app) {
        if (!LauncherMediaController.isNotificationAccessEnabled(app)) {
            return;
        }
        if (!AppLaunchHelper.PETAL_MAPS_PACKAGE.equals(TargetPackageStore.read(app))) {
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
