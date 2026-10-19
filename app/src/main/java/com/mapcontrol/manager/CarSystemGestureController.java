package com.mapcontrol.manager;

import android.app.ActivityOptions;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;

import com.mapcontrol.nav.YandexNavScraper;
import com.mapcontrol.util.AppLaunchHelper;
import com.mapcontrol.util.ClusterNavigationState;
import com.mapcontrol.util.TargetPackageStore;
import com.mapcontrol.util.UsageAccessHelper;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Araç kaydırması ({@code android.intent.action.car_system_gesture_mode}).
 * Yalnızca Google Maps ve Yandex Maps. Alıcı işi bekletmez; kullanım sorgusu arka plandadır.
 */
public final class CarSystemGestureController {

    public static final String PREF_ENABLED = "car_system_gesture_maps";

    private static final String TAG = "CarSystemGesture";
    private static final String ACTION = "android.intent.action.car_system_gesture_mode";
    /** İki parmak sola. */
    private static final int GESTURE_LEFT = 200;
    /** İki parmak sağa. */
    private static final int GESTURE_RIGHT = 201;
    private static final long DEBOUNCE_MS = 800L;
    /** Ön plan haritası için kısa pencere; saatlik tarama yok. */
    private static final long QUERY_WINDOW_MS = 15L * 60L * 1000L;

    public interface Host {
        void openCluster();

        void closeCluster();
    }

    private final Context appContext;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread workerThread;
    private final Handler worker;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            accept(intent);
        }
    };

    private volatile long lastAcceptMs;
    private volatile boolean registered;
    private volatile boolean released;

    public CarSystemGestureController(Context context, Host host) {
        this.appContext = context.getApplicationContext();
        this.host = host;
        workerThread = new HandlerThread("car-system-gesture");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
    }

    public static boolean isEnabled(Context context) {
        return TargetPackageStore.prefs(context).getBoolean(PREF_ENABLED, false);
    }

    public void syncRegistration() {
        main.post(this::syncRegistrationOnMain);
    }

    public void release() {
        released = true;
        main.post(() -> {
            unregisterQuietly();
            workerThread.quitSafely();
        });
    }

    private void accept(Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) {
            return;
        }
        if (!intent.hasExtra("gesture")) {
            return;
        }
        int gesture = intent.getIntExtra("gesture", -1);
        if (gesture != GESTURE_LEFT && gesture != GESTURE_RIGHT) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastAcceptMs < DEBOUNCE_MS) {
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        lastAcceptMs = now;
        worker.post(() -> {
            try {
                if (released || !isEnabled(appContext)) {
                    return;
                }
                if (gesture == GESTURE_RIGHT) {
                    handleRight();
                } else {
                    handleLeft();
                }
            } catch (Exception e) {
                Log.w(TAG, "gesture handling failed", e);
            } finally {
                main.postDelayed(() -> busy.set(false), DEBOUNCE_MS);
            }
        });
    }

    private void handleRight() {
        if (!ClusterNavigationState.getLastKnownOpen()) {
            return;
        }
        String target = TargetPackageStore.read(appContext);
        if (!isMapPackage(target)) {
            return;
        }
        Log.i(TAG, "right swipe: close cluster target=" + target);
        main.post(host::closeCluster);
    }

    private void handleLeft() {
        if (!UsageAccessHelper.hasPackageUsageAccess(appContext)) {
            Log.i(TAG, "left swipe ignored: usage access missing");
            return;
        }
        boolean clusterOpen = ClusterNavigationState.getLastKnownOpen();
        String clusterPkg = TargetPackageStore.read(appContext);
        String mainPkg = latestMapOnMainDisplay();
        if (!clusterOpen) {
            if (!isMapPackage(mainPkg)) {
                return;
            }
            final String openPkg = mainPkg;
            Log.i(TAG, "left swipe: open cluster for " + openPkg);
            main.post(() -> {
                TargetPackageStore.writeAndBroadcast(appContext, openPkg);
                host.openCluster();
            });
            return;
        }
        if (!isMapPackage(clusterPkg) || !isMapPackage(mainPkg) || clusterPkg.equals(mainPkg)) {
            return;
        }
        final String fromCluster = clusterPkg;
        final String fromMain = mainPkg;
        Log.i(TAG, "left swipe: swap cluster=" + fromCluster + " main=" + fromMain);
        main.post(() -> {
            int clusterId = AppLaunchHelper.getClusterDisplayId(appContext);
            AppLaunchHelper.launchAppOnDisplay(
                    appContext,
                    fromMain,
                    clusterId,
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            TargetPackageStore.writeAndBroadcast(appContext, fromMain);
            if (AppLaunchHelper.GOOGLE_MAPS_PACKAGE.equals(fromCluster)) {
                Log.i(TAG, "left swipe: Google Maps stays off the main screen; EmbeddedClusterActivity is replaced");
                return;
            }
            AppLaunchHelper.moveAppToDefaultDisplay(appContext, fromCluster);
            main.postDelayed(this::sendHomeOnMainDisplay, 450L);
        });
    }

    /** Sağ ekrana inen uygulamayı önde bırakmaz. Home yalnızca ana ekrana gider. */
    private void sendHomeOnMainDisplay() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ActivityOptions opts = ActivityOptions.makeBasic();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                opts.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
            }
            appContext.startActivity(home, opts.toBundle());
        } catch (Exception e) {
            Log.w(TAG, "home on main display failed", e);
        }
    }

    /**
     * Ana ekranda en son öne gelen harita. Ekran kimliği yoksa da en yeni kaydı kullanır;
     * cluster'daki diğer haritayı bunun yerine seçmez.
     */
    private String latestMapOnMainDisplay() {
        UsageStatsManager usm = (UsageStatsManager) appContext.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return null;
        }
        long end = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(end - QUERY_WINDOW_MS, end);
        if (events == null) {
            return null;
        }
        UsageEvents.Event event = new UsageEvents.Event();
        String onMain = null;
        long onMainAt = -1L;
        long googleUnknownAt = -1L;
        long yandexUnknownAt = -1L;
        long petalUnknownAt = -1L;
        while (events.hasNextEvent()) {
            if (!events.getNextEvent(event)) {
                break;
            }
            if (event.getEventType() != UsageEvents.Event.ACTIVITY_RESUMED) {
                continue;
            }
            String pkg = event.getPackageName();
            if (!isMapPackage(pkg)) {
                continue;
            }
            long at = event.getTimeStamp();
            int displayId = displayIdOf(event);
            if (displayId == Display.DEFAULT_DISPLAY) {
                if (at >= onMainAt) {
                    onMain = pkg;
                    onMainAt = at;
                }
            } else if (displayId < 0) {
                if (AppLaunchHelper.GOOGLE_MAPS_PACKAGE.equals(pkg) && at >= googleUnknownAt) {
                    googleUnknownAt = at;
                } else if (YandexNavScraper.PACKAGE_YANDEX_MAPS.equals(pkg) && at >= yandexUnknownAt) {
                    yandexUnknownAt = at;
                } else if (AppLaunchHelper.PETAL_MAPS_PACKAGE.equals(pkg) && at >= petalUnknownAt) {
                    petalUnknownAt = at;
                }
            }
        }
        if (onMain != null) {
            return onMain;
        }
        if (googleUnknownAt < 0 && yandexUnknownAt < 0 && petalUnknownAt < 0) {
            return null;
        }
        if (googleUnknownAt >= yandexUnknownAt && googleUnknownAt >= petalUnknownAt) {
            return AppLaunchHelper.GOOGLE_MAPS_PACKAGE;
        }
        if (yandexUnknownAt >= petalUnknownAt) {
            return YandexNavScraper.PACKAGE_YANDEX_MAPS;
        }
        return AppLaunchHelper.PETAL_MAPS_PACKAGE;
    }

    private static int displayIdOf(UsageEvents.Event event) {
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getDisplayId");
            Object value = method.invoke(event);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Field field = event.getClass().getDeclaredField("mDisplayId");
            field.setAccessible(true);
            return field.getInt(event);
        } catch (Throwable ignored) {
        }
        return -1;
    }

    static boolean isMapPackage(String packageName) {
        return AppLaunchHelper.GOOGLE_MAPS_PACKAGE.equals(packageName)
                || YandexNavScraper.PACKAGE_YANDEX_MAPS.equals(packageName)
                || AppLaunchHelper.PETAL_MAPS_PACKAGE.equals(packageName);
    }

    private void syncRegistrationOnMain() {
        if (released) {
            unregisterQuietly();
            return;
        }
        if (isEnabled(appContext)) {
            if (registered) {
                return;
            }
            IntentFilter filter = new IntentFilter(ACTION);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
                } else {
                    appContext.registerReceiver(receiver, filter);
                }
                registered = true;
                Log.i(TAG, "gesture receiver registered");
            } catch (Exception e) {
                Log.w(TAG, "gesture receiver register failed", e);
            }
            return;
        }
        unregisterQuietly();
    }

    private void unregisterQuietly() {
        if (!registered) {
            return;
        }
        try {
            appContext.unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
        registered = false;
        Log.i(TAG, "gesture receiver unregistered");
    }
}
