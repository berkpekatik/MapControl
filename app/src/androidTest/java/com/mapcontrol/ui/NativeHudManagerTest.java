package com.mapcontrol.ui;

import android.app.Instrumentation;
import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.mapcontrol.R;
import com.mapcontrol.launcher.HomeScreenPrefs;
import com.mapcontrol.trip.AppBackup;
import com.mapcontrol.nav.HudNavigationManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

/** Araç servisi bulunmayan emülatörde gerçek bildirim çözümleme ve native bağlantı durumları. */
@RunWith(AndroidJUnit4.class)
public class NativeHudManagerTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private final String[] keys = {"hudNavEnabled", "hudNavProvider", "nativeHudOwned"};
    private final Map<String, Object> previous = new HashMap<>();
    private HudNavigationManager hud;
    private SharedPreferences prefs;
    private TestListener listener;
    private SharedPreferences profilePrefs;
    private boolean hadModel;
    private int previousModel;

    private static class TestListener extends NotificationListenerService {
        TestListener(Context context) { attachBaseContext(context); }
    }

    @Before public void setUp() {
        assumeFalse("This test requires an emulator without the vehicle service",
                CarInfoProxy.getInstance().isServiceConnnected());
        profilePrefs = context.getSharedPreferences(AppBackup.FILE_LAUNCHER, Context.MODE_PRIVATE);
        hadModel = profilePrefs.contains("car_model");
        previousModel = HomeScreenPrefs.carModelIndex(context);
        prefs = context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
        for (String key : keys) if (prefs.contains(key)) previous.put(key, prefs.getAll().get(key));
        instrumentation.runOnMainSync(() -> {
            HomeScreenPrefs.setCarModelIndex(context, 2);
            hud = HudNavigationManager.get(context);
            hud.stop();
            prefs.edit().remove("hudNavEnabled").remove("hudNavProvider").remove("nativeHudOwned").commit();
            listener = new TestListener(context);
            hud.start();
            hud.onListenerConnected(listener);
            hud.setEnabled(true);
        });
        awaitStatus(R.string.hud_nav_waiting);
    }

    @After public void tearDown() {
        if (hud == null) return;
        instrumentation.runOnMainSync(() -> {
            hud.stop();
            if (hadModel) profilePrefs.edit().putInt("car_model", previousModel).commit();
            else profilePrefs.edit().remove("car_model").commit();
            SharedPreferences.Editor edit = prefs.edit();
            for (String key : keys) {
                edit.remove(key);
                Object value = previous.get(key);
                if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                if (value instanceof String) edit.putString(key, (String) value);
            }
            edit.commit();
        });
    }

    private void awaitStatus(int resource) {
        CountDownLatch ready = new CountDownLatch(1);
        Runnable watcher = () -> {
            if (context.getString(resource).equals(hud.status())) ready.countDown();
        };
        instrumentation.runOnMainSync(() -> hud.addWatcher(watcher));
        try {
            assertTrue("Expected " + context.getString(resource) + ", got " + hud.status(),
                    ready.await(8, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } finally {
            instrumentation.runOnMainSync(() -> hud.removeWatcher(watcher));
        }
    }

    private StatusBarNotification googleRoute() {
        Notification notification = new Notification.Builder(context, "hud-test")
                .setSmallIcon(R.mipmap.ic_launcher).setCategory(Notification.CATEGORY_NAVIGATION)
                .setOngoing(true).setContentTitle("300 m")
                .setContentText("Sağa dönün - Atatürk Cd.")
                .setSubText("20 dk. · 18 km · TVS: 14:52").build();
        return new StatusBarNotification("com.google.android.apps.maps", "com.google.android.apps.maps",
                123, "native-hud-route", Process.myUid(), Process.myPid(), 0, notification,
                Process.myUserHandle(), System.currentTimeMillis());
    }

    @Test public void notificationWaitsForVehicleThenRemovalClearsGuidance() {
        StatusBarNotification notification = googleRoute();
        instrumentation.runOnMainSync(() -> hud.onNotificationPosted(listener, notification));
        awaitStatus(R.string.hud_nav_need_vehicle);
        assertFalse(prefs.getBoolean("nativeHudOwned", false));
        instrumentation.runOnMainSync(() -> hud.onNotificationRemoved(listener, notification));
        awaitStatus(R.string.hud_nav_waiting);
    }

    @Test public void otherModelSelectionDisablesHudEvenIfItsToggleWasSavedOn() {
        instrumentation.runOnMainSync(() -> HomeScreenPrefs.setCarModelIndex(context, 1));
        awaitStatus(R.string.hud_nav_off);
        instrumentation.runOnMainSync(() -> {
            hud.showTest();
            hud.onNotificationPosted(listener, googleRoute());
        });
        assertFalse(hud.isEnabled());
        assertFalse(prefs.getBoolean("nativeHudOwned", false));
    }

    @Test public void projectionCardFollowsModelChangesWithoutRecreatingSettings() {
        instrumentation.runOnMainSync(() -> {
            HomeScreenPrefs.setCarModelIndex(context, 1);
            com.mapcontrol.ui.builder.ProjectionTabBuilder.ProjectionCallback callback =
                    (com.mapcontrol.ui.builder.ProjectionTabBuilder.ProjectionCallback)
                    java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                            new Class<?>[]{com.mapcontrol.ui.builder.ProjectionTabBuilder.ProjectionCallback.class},
                            (proxy, method, args) -> method.getReturnType() == boolean.class ? false
                                    : method.getReturnType() == String.class ? "" : null);
            com.mapcontrol.ui.builder.ProjectionTabBuilder builder =
                    new com.mapcontrol.ui.builder.ProjectionTabBuilder(context, prefs, callback);
            try {
                java.lang.reflect.Field field = builder.getClass().getDeclaredField("hudNavigationCard");
                field.setAccessible(true);
                assertNull(field.get(builder));
                HomeScreenPrefs.setCarModelIndex(context, 2);
                builder.refreshProjectionStatusUi();
                android.view.View card = (android.view.View) field.get(builder);
                assertNotNull(card);
                assertEquals(android.view.View.VISIBLE, card.getVisibility());
                HomeScreenPrefs.setCarModelIndex(context, 1);
                builder.refreshProjectionStatusUi();
                assertEquals(android.view.View.GONE, card.getVisibility());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test public void disconnectClearsQueuedNativeRoute() {
        instrumentation.runOnMainSync(() -> hud.onNotificationPosted(listener, googleRoute()));
        awaitStatus(R.string.hud_nav_need_vehicle);
        instrumentation.runOnMainSync(() -> hud.onListenerDisconnected());
        awaitStatus(R.string.hud_nav_need_notifications);
    }

    @Test public void disableCancelsTestAndConnectionRetries() {
        instrumentation.runOnMainSync(() -> hud.showTest());
        awaitStatus(R.string.hud_nav_need_vehicle);
        instrumentation.runOnMainSync(() -> hud.setEnabled(false));
        awaitStatus(R.string.hud_nav_off);
        assertFalse(prefs.getBoolean("nativeHudOwned", false));
    }
}
