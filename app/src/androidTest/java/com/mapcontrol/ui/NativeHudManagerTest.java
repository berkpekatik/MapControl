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
import com.desaysv.ivi.extra.project.carinfo.proxy.IServiceConnectListener;
import com.mapcontrol.R;
import com.mapcontrol.launcher.HomeScreenPrefs;
import com.mapcontrol.launcher.NavigationSource;
import com.mapcontrol.nav.HudNavigationManager;
import com.mapcontrol.nav.HudVehicle;
import com.mapcontrol.nav.YandexNavSnapshot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Gerçek Android bildirim hattı; araç taşıması kontrollü bir test nesnesidir. Fiziksel HUD testi değildir. */
@RunWith(AndroidJUnit4.class)
public class NativeHudManagerTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private final String[] keys = {"hudNavEnabled", "hudNavProvider", "nativeHudOwned"};
    private final Map<String, Object> previous = new HashMap<>();
    private HudNavigationManager hud;
    private SharedPreferences prefs;
    private TestListener listener;
    private FakeVehicle vehicle;

    private static class TestListener extends NotificationListenerService {
        volatile StatusBarNotification[] notifications = new StatusBarNotification[0];
        TestListener(Context context) { attachBaseContext(context); }
        @Override public StatusBarNotification[] getActiveNotifications() { return notifications; }
    }

    private static class FakeVehicle implements HudVehicle {
        volatile Boolean support = true;
        volatile boolean connected = true;
        volatile Integer power = 2;
        volatile IServiceConnectListener listener;
        volatile Runnable onSend;
        volatile int connects;
        volatile int[] lastTurn;
        final List<Integer> commands = Collections.synchronizedList(new ArrayList<>());
        @Override public Boolean hudSupport() { return support; }
        @Override public boolean connected() { return connected; }
        @Override public Integer powerMode() { return power; }
        @Override public void connect(Context context) { connects++; }
        @Override public void listen(IServiceConnectListener value) { listener = value; }
        @Override public void unlisten(IServiceConnectListener value) { if (listener == value) listener = null; }
        @Override public boolean send(int[] packet) {
            commands.add(packet[0]);
            if (packet[0] == 50) lastTurn = packet.clone();
            Runnable hook = onSend;
            if (hook != null) hook.run();
            return true;
        }
        int count(int command) {
            synchronized (commands) { return Collections.frequency(commands, command); }
        }
    }

    @Before public void setUp() {
        prefs = context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
        for (String key : keys) if (prefs.contains(key)) previous.put(key, prefs.getAll().get(key));
        vehicle = new FakeVehicle();
        instrumentation.runOnMainSync(() -> {
            prefs.edit().putBoolean("hudNavEnabled", true).putString("hudNavProvider", "auto")
                    .remove("nativeHudOwned").commit();
            listener = new TestListener(context);
            NavigationSource.onListenerConnected(listener);
            hud = new HudNavigationManager(context, vehicle);
            hud.start();
        });
        awaitStatus(R.string.hud_nav_waiting);
    }

    @After public void tearDown() {
        if (hud == null) return;
        vehicle.onSend = null;
        vehicle.support = true;
        vehicle.connected = true;
        vehicle.power = 2;
        instrumentation.runOnMainSync(() -> {
            hud.setEnabled(false);
            NavigationSource.onListenerDisconnected();
        });
        awaitStatus(R.string.hud_nav_off);
        // İkinci temizleme tamamlanmadan test tercihlerinin üstüne yazılmaz.
        waitFor(() -> !prefs.getBoolean("nativeHudOwned", false));
        instrumentation.runOnMainSync(() -> {
            hud.stop();
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

    private interface Condition { boolean ready(); }
    private void waitFor(Condition condition) {
        long deadline = android.os.SystemClock.elapsedRealtime() + 8000;
        while (!condition.ready() && android.os.SystemClock.elapsedRealtime() < deadline) {
            android.os.SystemClock.sleep(25);
        }
        assertTrue("Condition did not become true", condition.ready());
    }
    private void awaitStatus(int resource) {
        waitFor(() -> context.getString(resource).equals(hud.status()));
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
    private void postRoute() {
        StatusBarNotification route = googleRoute();
        listener.notifications = new StatusBarNotification[]{route};
        instrumentation.runOnMainSync(() -> NavigationSource.onNotificationPosted(listener, route));
    }

    @Test public void notificationAndRemovalUseTheSharedSource() {
        postRoute();
        awaitStatus(R.string.hud_nav_showing);
        assertEquals(1, vehicle.count(52));
        StatusBarNotification route = listener.notifications[0];
        listener.notifications = new StatusBarNotification[0];
        instrumentation.runOnMainSync(() -> NavigationSource.onNotificationRemoved(listener, route));
        awaitStatus(R.string.hud_nav_waiting);
        waitFor(() -> vehicle.count(3) == 2);
    }

    @Test public void unsupportedVehicleNeverSendsOrKeepsHudListeners() {
        vehicle.support = false;
        instrumentation.runOnMainSync(() -> hud.refresh());
        awaitStatus(R.string.hud_nav_not_supported);
        postRoute();
        instrumentation.runOnMainSync(() -> hud.showTest());
        assertFalse(hud.isEnabled());
        assertNull(vehicle.listener);
        assertTrue(vehicle.commands.isEmpty());
    }

    @Test public void unknownSupportIsNotReportedAsAnUnsupportedVehicle() {
        vehicle.support = null;
        instrumentation.runOnMainSync(() -> hud.refresh());
        awaitStatus(R.string.hud_nav_check_support);
        assertFalse(hud.isSupported());
        assertTrue(vehicle.commands.isEmpty());
    }

    @Test public void powerOffDefersStartAndCleanupUntilPowerReturns() {
        postRoute();
        awaitStatus(R.string.hud_nav_showing);
        vehicle.power = 0;
        instrumentation.runOnMainSync(() -> { hud.refresh(); hud.setEnabled(false); });
        awaitStatus(R.string.hud_nav_off);
        assertEquals(0, vehicle.count(3));
        assertTrue(prefs.getBoolean("nativeHudOwned", false));
        vehicle.power = 2;
        instrumentation.runOnMainSync(() -> hud.refresh());
        waitFor(() -> vehicle.count(3) == 2);
        waitFor(() -> !prefs.getBoolean("nativeHudOwned", false));
    }

    @Test public void reconnectNotificationOneDoesNotRestartAnActiveSession() {
        postRoute();
        awaitStatus(R.string.hud_nav_showing);
        vehicle.listener.onServiceConnectedChanged(1);
        instrumentation.waitForIdleSync();
        android.os.SystemClock.sleep(300);
        assertEquals(1, vehicle.count(52));
        assertEquals(0, vehicle.count(3));
    }

    @Test public void realDisconnectRebuildsTheSessionAfterReconnect() {
        postRoute();
        awaitStatus(R.string.hud_nav_showing);
        vehicle.connected = false;
        vehicle.listener.onServiceConnectedChanged(0);
        awaitStatus(R.string.hud_nav_need_vehicle);
        vehicle.connected = true;
        vehicle.listener.onServiceConnectedChanged(1);
        awaitStatus(R.string.hud_nav_showing);
        assertEquals(2, vehicle.count(52));
        assertEquals(1, vehicle.count(3));
    }

    @Test public void repeatedYandexSnapshotsDoNotInterruptAnInFlightFrame() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        vehicle.onSend = () -> {
            if (first.compareAndSet(true, false)) {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
            }
        };
        YandexNavSnapshot snapshot = new YandexNavSnapshot(true, "300", "m", "Test Cd.",
                "18 km", "14:52", "20 dk", null, null, null);
        instrumentation.runOnMainSync(() -> hud.onYandexSnapshot(snapshot));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        try {
            instrumentation.runOnMainSync(() -> {
                for (int i = 0; i < 100; i++) hud.onYandexSnapshot(snapshot);
            });
            instrumentation.waitForIdleSync();
        } finally { release.countDown(); }
        awaitStatus(R.string.hud_nav_showing);
        assertEquals(1, vehicle.count(52));
        assertEquals(0, vehicle.count(3));
        assertEquals(1, vehicle.count(50));
    }

    @Test public void changingContentIsCoalescedWithoutAbortingTheCurrentFrame() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        vehicle.onSend = () -> {
            if (first.compareAndSet(true, false)) {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
            }
        };
        instrumentation.runOnMainSync(() -> hud.onYandexSnapshot(new YandexNavSnapshot(true,
                "300", "m", "Test Cd.", "18 km", null, null, null, null, null)));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        try {
            instrumentation.runOnMainSync(() -> {
                for (int i = 301; i <= 400; i++) hud.onYandexSnapshot(new YandexNavSnapshot(true,
                        Integer.toString(i), "m", "Test Cd.", "18 km", null, null, null, null, null));
            });
            instrumentation.waitForIdleSync();
        } finally { release.countDown(); }
        awaitStatus(R.string.hud_nav_showing);
        assertEquals(1, vehicle.count(52));
        assertEquals(0, vehicle.count(3));
        assertEquals(2, vehicle.count(50));
        assertEquals(4000, (vehicle.lastTurn[4] & 255) | ((vehicle.lastTurn[5] & 255) << 8));
    }

    @Test public void powerLossDuringAFrameBlocksEveryRemainingPacketAndRecovers() {
        AtomicBoolean first = new AtomicBoolean(true);
        vehicle.onSend = () -> { if (first.compareAndSet(true, false)) vehicle.power = 0; };
        postRoute();
        awaitStatus(R.string.hud_nav_send_error);
        assertEquals(1, vehicle.commands.size());
        assertEquals(1, vehicle.count(52));
        assertTrue(prefs.getBoolean("nativeHudOwned", false));
        vehicle.power = 2;
        instrumentation.runOnMainSync(() -> hud.refresh());
        awaitStatus(R.string.hud_nav_showing);
        assertEquals(1, vehicle.count(3));
        assertEquals(2, vehicle.count(52));
    }

    @Test public void hudUsesGoogleWhileLauncherIsSetToYandex() {
        instrumentation.runOnMainSync(() -> NavigationSource.setMapApp(com.mapcontrol.launcher.MapApp.YANDEX_MAPS));
        postRoute();
        awaitStatus(R.string.hud_nav_showing);
        assertFalse(NavigationSource.getState().getActive());
        assertEquals(1, vehicle.count(52));
    }

    @Test public void unknownPowerPreventsEveryPacket() {
        vehicle.power = null;
        postRoute();
        awaitStatus(R.string.hud_nav_need_power);
        assertTrue(vehicle.commands.isEmpty());
        assertFalse(prefs.getBoolean("nativeHudOwned", false));
    }

    @Test public void portraitDefaultsToUprightAndRespectsExplicitOverride() {
        SharedPreferences settings = context.getSharedPreferences(com.mapcontrol.trip.AppBackup.FILE_LAUNCHER, Context.MODE_PRIVATE);
        Map<String, ?> saved = settings.getAll();
        try {
            settings.edit().putInt("vehicle_profile", 3).remove("car_vertical").commit();
            assertTrue(HomeScreenPrefs.carVertical(context));
            settings.edit().putBoolean("car_vertical", false).commit();
            assertFalse(HomeScreenPrefs.carVertical(context));
            settings.edit().putInt("vehicle_profile", 1).remove("car_vertical").commit();
            assertFalse(HomeScreenPrefs.carVertical(context));
        } finally {
            SharedPreferences.Editor edit = settings.edit();
            for (String key : new String[]{"vehicle_profile", "car_vertical"}) {
                edit.remove(key);
                Object value = saved.get(key);
                if (value instanceof Integer) edit.putInt(key, (Integer) value);
                if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            }
            edit.commit();
        }
    }

    @Test public void listenerDisconnectClearsNativeGuidance() {
        postRoute();
        awaitStatus(R.string.hud_nav_showing);
        instrumentation.runOnMainSync(NavigationSource::onListenerDisconnected);
        awaitStatus(R.string.hud_nav_need_notifications);
        waitFor(() -> vehicle.count(3) == 2);
    }
}
