package com.mapcontrol.nav;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;

import com.mapcontrol.media.LauncherMediaController;
import com.mapcontrol.service.MapControlService;
import com.mapcontrol.util.AppLaunchHelper;
import com.mapcontrol.util.ClusterNavigationState;
import com.mapcontrol.util.TargetPackageStore;

import java.lang.ref.WeakReference;

/**
 * Google Maps navigasyon bildirimini yansıtma açılışına, cluster kartlarına ve UI yayınına bağlar.
 * Bildirim kalkınca cluster yansıtması kapatılmaz; kartlar gizlenir.
 *
 * Yalnızca rehberlik bildirimi izlenir. Trafik, indirme veya arka plan bildirimleri kartı
 * kapatmaz. Bildirim anahtarı değişince kısa süre son iyi rota tutulur, sonra yeniden taranır.
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
    /** Aynı anahtarın silinip yeniden yazılması kartı söndürmesin. */
    private static final long INACTIVE_HOLD_MS = 800L;

    private static volatile GoogleMapsNavSnapshot lastSnapshot = GoogleMapsNavSnapshot.inactive();
    private static long lastOpenRequestUptimeMs;
    @Nullable
    private static volatile String activeNavKey;
    private static int activeScore = -1;
    @Nullable
    private static Context appContext;
    @Nullable
    private static WeakReference<NotificationListenerService> serviceRef;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final Runnable confirmInactiveRunnable =
            GoogleMapsNavNotificationCoordinator::confirmInactive;

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

    public static void onListenerConnected(@Nullable NotificationListenerService service) {
        remember(service);
    }

    public static void onListenerDisconnected() {
        serviceRef = null;
    }

    public static void onNotificationPosted(Context context, @Nullable StatusBarNotification sbn) {
        remember(context);
        if (!isGoogleMapsNotification(sbn)) {
            return;
        }
        GoogleMapsNavSnapshot snapshot = GoogleMapsNavNotificationParser.parse(sbn);
        if (snapshot.active) {
            int score = score(sbn, snapshot);
            if (!shouldAdopt(sbn.getKey(), score)) {
                return;
            }
            cancelInactiveCheck();
            activeNavKey = sbn.getKey();
            activeScore = score;
            publish(context, snapshot);
            requestOpenIfEligible(context.getApplicationContext());
            return;
        }
        if (activeNavKey != null && activeNavKey.equals(sbn.getKey())) {
            scheduleInactiveCheck(context);
        }
    }

    public static void onNotificationRemoved(Context context, @Nullable StatusBarNotification sbn) {
        remember(context);
        if (!isGoogleMapsNotification(sbn)) {
            return;
        }
        if (activeNavKey != null && !activeNavKey.equals(sbn.getKey())) {
            return;
        }
        if (activeNavKey == null && !lastSnapshot.active) {
            return;
        }
        activeNavKey = null;
        activeScore = -1;
        scheduleInactiveCheck(context);
    }

    /**
     * Bildirim dinleyici bağlandığında veya ayar açıldığında mevcut Maps nav bildirimini yeniden okur.
     */
    public static void resyncActiveNotifications(@Nullable NotificationListenerService service) {
        if (service == null) {
            return;
        }
        remember(service);
        try {
            StatusBarNotification[] active = service.getActiveNotifications();
            if (active == null) {
                return;
            }
            NavPick pick = pickBest(active);
            if (pick != null) {
                cancelInactiveCheck();
                activeNavKey = pick.key;
                activeScore = pick.score;
                publish(service, pick.snapshot);
                return;
            }
            if (lastSnapshot.active) {
                activeNavKey = null;
                activeScore = -1;
                publish(service, GoogleMapsNavSnapshot.inactive());
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isGoogleMapsNotification(@Nullable StatusBarNotification sbn) {
        return sbn != null
                && GoogleMapsNavNotificationParser.PACKAGE_GOOGLE_MAPS.equals(sbn.getPackageName());
    }

    private static void remember(@Nullable Context context) {
        if (context == null) {
            return;
        }
        if (appContext == null) {
            appContext = context.getApplicationContext();
        }
        if (context instanceof NotificationListenerService) {
            serviceRef = new WeakReference<>((NotificationListenerService) context);
        }
    }

    private static void scheduleInactiveCheck(Context context) {
        remember(context);
        mainHandler.removeCallbacks(confirmInactiveRunnable);
        mainHandler.postDelayed(confirmInactiveRunnable, INACTIVE_HOLD_MS);
    }

    private static void cancelInactiveCheck() {
        mainHandler.removeCallbacks(confirmInactiveRunnable);
    }

    /**
     * İzlenen bildirim silinince hemen kartı kapatma. Kısa aradan sonra hâlâ rehberlik
     * bildirimi varsa onu göster; yoksa kartı kapat.
     */
    private static void confirmInactive() {
        Context app = appContext;
        NotificationListenerService service = listenerService();
        if (app == null || service == null) {
            return;
        }
        try {
            NavPick pick = pickBest(service.getActiveNotifications());
            if (pick != null) {
                activeNavKey = pick.key;
                activeScore = pick.score;
                publish(app, pick.snapshot);
                return;
            }
        } catch (Exception ignored) {
            return;
        }
        activeNavKey = null;
        activeScore = -1;
        publish(app, GoogleMapsNavSnapshot.inactive());
    }

    @Nullable
    private static NotificationListenerService listenerService() {
        WeakReference<NotificationListenerService> ref = serviceRef;
        return ref == null ? null : ref.get();
    }

    private static boolean shouldAdopt(@Nullable String key, int score) {
        if (key == null) {
            return false;
        }
        if (activeNavKey == null || activeNavKey.equals(key)) {
            return true;
        }
        return score > activeScore;
    }

    private static int score(@Nullable StatusBarNotification sbn, @Nullable GoogleMapsNavSnapshot snapshot) {
        if (sbn == null || snapshot == null || !snapshot.active) {
            return -1;
        }
        int value = 1;
        Notification notification = sbn.getNotification();
        if (notification != null) {
            if (Notification.CATEGORY_NAVIGATION.equals(notification.category)) {
                value += 100;
            }
            if ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) {
                value += 40;
            }
        }
        if (hasDigit(snapshot.distanceTitle) || hasDigit(snapshot.maneuverHint)) {
            value += 30;
        }
        if (snapshot.routeSummary != null && snapshot.routeSummary.indexOf('·') >= 0) {
            value += 10;
        }
        return value;
    }

    private static boolean hasDigit(@Nullable String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static NavPick pickBest(@Nullable StatusBarNotification[] active) {
        if (active == null) {
            return null;
        }
        NavPick best = null;
        for (StatusBarNotification sbn : active) {
            if (!isGoogleMapsNotification(sbn)) {
                continue;
            }
            GoogleMapsNavSnapshot snapshot = GoogleMapsNavNotificationParser.parse(sbn);
            int value = score(sbn, snapshot);
            if (value < 0) {
                continue;
            }
            if (best == null || value > best.score) {
                best = new NavPick(sbn.getKey(), value, snapshot);
            }
        }
        return best;
    }

    private static final class NavPick {
        final String key;
        final int score;
        final GoogleMapsNavSnapshot snapshot;

        NavPick(String key, int score, GoogleMapsNavSnapshot snapshot) {
            this.key = key;
            this.score = score;
            this.snapshot = snapshot;
        }
    }

    private static void publish(Context context, GoogleMapsNavSnapshot snapshot) {
        boolean changed = !lastSnapshot.contentEquals(snapshot);
        lastSnapshot = snapshot;
        Context app = context.getApplicationContext();
        appContext = app;
        if (GoogleMapsClusterNavOverlay.isEnabled(app)) {
            GoogleMapsClusterNavOverlay.getInstance(app).apply(snapshot);
        } else {
            GoogleMapsClusterNavOverlay.getInstance(app).hide();
        }
        if (!changed) {
            return;
        }
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

    public static boolean isClusterOverlayEnabled(Context context) {
        return GoogleMapsClusterNavOverlay.isEnabled(context);
    }

    public static void setClusterOverlayEnabled(Context context, boolean enabled) {
        GoogleMapsClusterNavOverlay.setEnabled(context, enabled);
        if (enabled) {
            GoogleMapsClusterNavOverlay.getInstance(context).apply(getLastSnapshot());
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
