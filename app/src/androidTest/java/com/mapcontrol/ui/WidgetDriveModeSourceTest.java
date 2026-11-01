package com.mapcontrol.ui;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.extra.project.carinfo.proxy.Constants;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.client.bind.VDRouter;
import com.desaysv.ivi.vdb.client.bind.VDThreadConfig;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.mapcontrol.R;
import com.mapcontrol.ui.widget.StatusWidgetsView;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WidgetDriveModeSourceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Context context() {
        return new ContextThemeWrapper(instrumentation.getTargetContext(), R.style.Theme_MapControl);
    }
    private android.content.SharedPreferences preferences;
    private boolean hadSelection;
    private int savedSelection;
    @Before public void isolateSelection() {
        preferences = context().getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
        hadSelection = preferences.contains("driveModeSetting");
        savedSelection = preferences.getInt("driveModeSetting", -1);
        preferences.edit().remove("driveModeSetting").commit();
    }
    @After public void restoreSelection() {
        if (hadSelection) preferences.edit().putInt("driveModeSetting", savedSelection).commit();
        else preferences.edit().remove("driveModeSetting").commit();
    }
    private static VDEvent reply(int mode) {
        Bundle data = new Bundle();
        data.putInt(Constants.CMD_ID, NewEnergyID.ID_DRIVE_MODE);
        data.putIntArray(Constants.VALUE, new int[]{mode});
        return new VDEvent(VDEventCarInfo.MODULE_NEW_ENERGY, data);
    }
    private void awaitLabel(StatusWidgetsView widget, String prefix, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        String[] actual = {null};
        while (System.nanoTime() < deadline) {
            instrumentation.runOnMainSync(() -> actual[0] =
                    ((TextView) widget.findViewWithTag(prefix + "_mode_value")).getText().toString());
            if (expected.equals(actual[0])) return;
            Thread.sleep(30);
        }
        assertEquals(expected, actual[0]);
    }
    @Test public void productionMonitorReadsRawRepliesWhenGeneralProxyIsDisconnected() throws Exception {
        // Keep the production monitor, source, request format and decoder; replace only the remote bus.
        AtomicReference<VDEvent> response = new AtomicReference<>();
        AtomicInteger requests = new AtomicInteger(), mainThreadReads = new AtomicInteger();
        VDRouter fakeRouter = new VDRouter() {
            @Override public void init(Context c, VDThreadConfig config, ArrayList<String> keep) {}
            @Override public VDEvent getOnce(VDEvent request) {
                if (Looper.myLooper() == Looper.getMainLooper()) mainThreadReads.incrementAndGet();
                assertEquals(VDEventCarInfo.MODULE_NEW_ENERGY, request.getId());
                assertEquals(NewEnergyID.ID_DRIVE_MODE, request.getPayload().getInt(Constants.CMD_ID));
                requests.incrementAndGet();
                return response.get();
            }
        };
        VDBus bus = VDBus.getDefault();
        Field router = VDBus.class.getDeclaredField("mVDRouter");
        router.setAccessible(true);
        Object previousRouter = router.get(bus);
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        Field connected = CarInfoProxy.class.getDeclaredField("mIsServiceConnnected");
        connected.setAccessible(true);
        boolean previousConnection = connected.getBoolean(proxy);
        HeaderStatusMonitor[] monitor = new HeaderStatusMonitor[1];
        StatusWidgetsView[] widgets = new StatusWidgetsView[2];
        VehicleMetricsRepository repository = new VehicleMetricsRepository(context());
        try {
            connected.setBoolean(proxy, false);
            router.set(bus, fakeRouter);
            assertFalse(repository.currentSnapshot().connected);
            assertEquals(Integer.MIN_VALUE, repository.currentSnapshot().driveMode);
            instrumentation.runOnMainSync(() -> {
                widgets[0] = new StatusWidgetsView(context(), "rail", tab -> {});
                widgets[1] = new StatusWidgetsView(context(), "launcher", tab -> {});
                monitor[0] = new HeaderStatusMonitor(context(), widgets[0], () -> false);
                monitor[0].addSink(widgets[1]);
                monitor[0].start();
            });
            // A late reply must replace the initial dash without an OEM callback or tab change.
            long firstReadDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (requests.get() == 0 && System.nanoTime() < firstReadDeadline) Thread.sleep(20);
            assertTrue("Production source must issue a raw mode query", requests.get() > 0);
            response.set(reply(2));
            awaitLabel(widgets[0], "rail", "SPORT");
            awaitLabel(widgets[1], "launcher", "SPORT");
            response.set(reply(0));
            awaitLabel(widgets[0], "rail", "ECO");
            awaitLabel(widgets[1], "launcher", "ECO");
            response.set(null);
            instrumentation.runOnMainSync(() -> monitor[0].refresh());
            awaitLabel(widgets[0], "rail", "—");
            awaitLabel(widgets[1], "launcher", "—");
            response.set(reply(3));
            instrumentation.runOnMainSync(() -> monitor[0].refresh());
            awaitLabel(widgets[0], "rail", "SNOW");
            awaitLabel(widgets[1], "launcher", "SNOW");
            assertFalse("UI reading must not alter shared proxy state", proxy.isServiceConnnected());
            assertEquals(0, mainThreadReads.get());
        } finally {
            instrumentation.runOnMainSync(() -> { if (monitor[0] != null) monitor[0].stop(); });
            router.set(bus, previousRouter);
            connected.setBoolean(proxy, previousConnection);
            repository.release();
        }
    }
    @Test public void failedUnrelatedMetricReadDoesNotBlockModeWidget() throws Exception {
        VDBus bus = VDBus.getDefault();
        Field router = VDBus.class.getDeclaredField("mVDRouter");
        router.setAccessible(true);
        Object previousRouter = router.get(bus);
        Field singleton = CarInfoProxy.class.getDeclaredField("mInstance");
        singleton.setAccessible(true);
        Object previousProxy = singleton.get(null);
        AtomicInteger unrelatedReads = new AtomicInteger();
        CarInfoProxy failingMetrics = new CarInfoProxy() {
            @Override public boolean isServiceConnnected() { return true; }
            @Override public void regCallBack(int module, com.desaysv.ivi.vdb.IVDBusNotify.Stub listener, int[] ids) {}
            @Override public void unRegCallBack(int module, com.desaysv.ivi.vdb.IVDBusNotify.Stub listener) {}
            @Override public int[] getItemValues(int module, int id) {
                unrelatedReads.incrementAndGet();
                throw new IllegalStateException("An unrelated metric cannot be read");
            }
        };
        VDRouter rawModeBus = new VDRouter() {
            @Override public void init(Context c, VDThreadConfig config, ArrayList<String> keep) {}
            @Override public VDEvent getOnce(VDEvent request) {
                assertEquals(NewEnergyID.ID_DRIVE_MODE, request.getPayload().getInt(Constants.CMD_ID));
                return reply(2);
            }
        };
        VehicleMetricsRepository repository = new VehicleMetricsRepository(context());
        CountDownLatch snapshotReady = new CountDownLatch(1);
        HeaderStatusMonitor[] monitor = new HeaderStatusMonitor[1];
        StatusWidgetsView[] widget = new StatusWidgetsView[1];
        try {
            router.set(bus, rawModeBus);
            singleton.set(null, failingMetrics);
            repository.addListener(value -> snapshotReady.countDown());
            assertTrue(snapshotReady.await(3, TimeUnit.SECONDS));
            assertTrue(unrelatedReads.get() > 0);
            assertTrue(repository.currentSnapshot().connected);
            assertEquals(Integer.MIN_VALUE, repository.currentSnapshot().driveMode);
            instrumentation.runOnMainSync(() -> {
                widget[0] = new StatusWidgetsView(context(), "raw", tab -> {});
                monitor[0] = new HeaderStatusMonitor(context(), widget[0], () -> false);
                monitor[0].start();
            });
            awaitLabel(widget[0], "raw", "SPORT");
        } finally {
            instrumentation.runOnMainSync(() -> { if (monitor[0] != null) monitor[0].stop(); });
            repository.release();
            singleton.set(null, previousProxy);
            router.set(bus, previousRouter);
        }
    }

    private HeaderStatusMonitor.ModeReading read(WidgetDriveModeSource source) throws Exception {
        BlockingQueue<HeaderStatusMonitor.ModeReading> values = new LinkedBlockingQueue<>();
        Consumer<HeaderStatusMonitor.ModeReading> listener = values::add;
        instrumentation.runOnMainSync(() -> {
            source.addListener(listener);
            values.clear();
            source.refresh();
        });
        try {
            HeaderStatusMonitor.ModeReading result = values.poll(3, TimeUnit.SECONDS);
            assertNotNull("Raw read must be delivered", result);
            return result;
        } finally { instrumentation.runOnMainSync(() -> source.removeListener(listener)); }
    }
    @Test public void missingRepliesNeverBecomeEcoButRealZeroRemainsEco() throws Exception {
        AtomicReference<VDEvent> reply = new AtomicReference<>();
        WidgetDriveModeSource source = new WidgetDriveModeSource(request -> reply.get());
        assertEquals(Integer.MIN_VALUE, read(source).driveMode);
        reply.set(new VDEvent(VDEventCarInfo.MODULE_NEW_ENERGY, new Bundle()));
        assertEquals(Integer.MIN_VALUE, read(source).driveMode);
        Bundle wrongCommand = new Bundle();
        wrongCommand.putInt(Constants.CMD_ID, 99);
        wrongCommand.putIntArray(Constants.VALUE, new int[]{2});
        reply.set(new VDEvent(VDEventCarInfo.MODULE_NEW_ENERGY, wrongCommand));
        assertEquals(Integer.MIN_VALUE, read(source).driveMode);
        reply.set(new VDEvent(VDEventCarInfo.MODULE_READONLY_INFO, reply(2).getPayload()));
        assertEquals(Integer.MIN_VALUE, read(source).driveMode);
        reply.set(reply(0));
        HeaderStatusMonitor.ModeReading eco = read(source);
        assertTrue(eco.connected);
        assertEquals(0, eco.driveMode);
        Bundle multiByte = new Bundle();
        multiByte.putIntArray(Constants.VALUE, new int[]{0, 0, 2});
        reply.set(new VDEvent(VDEventCarInfo.MODULE_NEW_ENERGY, multiByte));
        assertEquals(2, read(source).driveMode);
        multiByte.putIntArray(Constants.VALUE, new int[0]);
        assertEquals(Integer.MIN_VALUE, read(source).driveMode);
        WidgetDriveModeSource unavailable = new WidgetDriveModeSource(request -> { throw new IllegalStateException(); });
        assertEquals(Integer.MIN_VALUE, read(unavailable).driveMode);
    }
    @Test public void pendingReadIsCoalescedAndLateReplyAfterStopCannotReachWidgets() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger(), updates = new AtomicInteger();
        WidgetDriveModeSource source = new WidgetDriveModeSource(request -> {
            if (requests.incrementAndGet() == 1) {
                entered.countDown();
                try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                return reply(0);
            }
            return reply(2);
        });
        Consumer<HeaderStatusMonitor.ModeReading> listener = value -> updates.incrementAndGet();
        try {
            instrumentation.runOnMainSync(() -> {
                source.addListener(listener);
                source.refresh();
                source.refresh();
            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            instrumentation.runOnMainSync(() -> source.removeListener(listener));
            release.countDown();
            instrumentation.waitForIdleSync();
            assertEquals(1, requests.get());
            assertEquals("Only the initial unknown value may be delivered", 1, updates.get());
            assertEquals(2, read(source).driveMode);
        } finally {
            release.countDown();
            instrumentation.runOnMainSync(() -> source.removeListener(listener));
        }
    }
}
