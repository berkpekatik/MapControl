package com.mapcontrol.ui;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.ContextThemeWrapper;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.mapcontrol.R;
import com.mapcontrol.ui.widget.StatusWidgetsView;
import com.mapcontrol.vehicle.DriveModeStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HeaderStatusMonitorTest {
    private Context context() {
        return new ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                R.style.Theme_MapControl);
    }

    private SharedPreferences originalPreferences;
    private boolean hadSelection;
    private int originalSelection;
    @Before public void isolateSelection() {
        originalPreferences = context().getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
        hadSelection = originalPreferences.contains("driveModeSetting");
        originalSelection = originalPreferences.getInt("driveModeSetting", -1);
        originalPreferences.edit().remove("driveModeSetting").commit();
    }
    @After public void restoreSelection() {
        if (hadSelection) originalPreferences.edit().putInt("driveModeSetting", originalSelection).commit();
        else originalPreferences.edit().remove("driveModeSetting").commit();
    }

    private static HeaderStatusMonitor.ModeReading snapshot(boolean connected, int mode) {
        return new HeaderStatusMonitor.ModeReading(connected, mode);
    }

    private static final class Source implements HeaderStatusMonitor.ModeSource {
        HeaderStatusMonitor.ModeReading current = snapshot(true, DriveModeStyle.NORMAL);
        HeaderStatusMonitor.ModeReading next = current;
        int refreshes;
        Runnable onRefresh = () -> {};
        final List<Consumer<HeaderStatusMonitor.ModeReading>> listeners = new ArrayList<>();
        public HeaderStatusMonitor.ModeReading currentReading() { return current; }
        public void addListener(Consumer<HeaderStatusMonitor.ModeReading> listener) {
            listeners.add(listener);
            listener.accept(current);
        }
        public void removeListener(Consumer<HeaderStatusMonitor.ModeReading> listener) { listeners.remove(listener); }
        public void refresh() {
            refreshes++;
            current = next;
            for (Consumer<HeaderStatusMonitor.ModeReading> listener : new ArrayList<>(listeners)) {
                listener.accept(current);
            }
            onRefresh.run();
        }
    }

    private void assertMode(StatusWidgetsView widget, String prefix, String text) {
        assertEquals(text, ((TextView) widget.findViewWithTag(prefix + "_mode_value")).getText().toString());
    }

    @Test public void savedSelectionUpdatesEveryWidgetImmediatelyAndCannotBeErasedByLiveReplies() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = context();
        SharedPreferences prefs = originalPreferences;
        Source source = new Source();
        HeaderStatusMonitor[] monitor = new HeaderStatusMonitor[1];
        StatusWidgetsView[] widgets = new StatusWidgetsView[3];
        try {
            instrumentation.runOnMainSync(() -> {
                widgets[0] = new StatusWidgetsView(context, "rail", tab -> {});
                widgets[1] = new StatusWidgetsView(context, "launcher", tab -> {});
                monitor[0] = new HeaderStatusMonitor(context, widgets[0], source, () -> false);
                monitor[0].addSink(widgets[1]);
                monitor[0].start();
            });
            instrumentation.waitForIdleSync();
            int[] before = new int[1];
            instrumentation.runOnMainSync(() -> {
                before[0] = source.refreshes;
                prefs.edit().putInt("driveModeSetting", DriveModeStyle.SPORT).commit();
                monitor[0].refresh();
                assertMode(widgets[0], "rail", "SPORT");
                assertMode(widgets[1], "launcher", "SPORT");
                assertEquals(before[0], source.refreshes);
                widgets[2] = new StatusWidgetsView(context, "late", tab -> {});
                monitor[0].addSink(widgets[2]);
                assertMode(widgets[2], "late", "SPORT");
                for (HeaderStatusMonitor.ModeReading reading : new HeaderStatusMonitor.ModeReading[]{
                        snapshot(true, Integer.MIN_VALUE), snapshot(false, DriveModeStyle.SPORT),
                        snapshot(true, DriveModeStyle.ECO)}) {
                    source.current = reading;
                    for (Consumer<HeaderStatusMonitor.ModeReading> listener : new ArrayList<>(source.listeners)) {
                        listener.accept(reading);
                    }
                    assertMode(widgets[0], "rail", "SPORT");
                    assertMode(widgets[1], "launcher", "SPORT");
                    assertMode(widgets[2], "late", "SPORT");
                }
                assertEquals(DriveModeStyle.SPORT, prefs.getInt("driveModeSetting", -1));
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                assertEquals("A stored selection needs no OEM polling", before[0], source.refreshes);
                // A preference change without the activity callback must also update all widgets.
                prefs.edit().putInt("driveModeSetting", DriveModeStyle.SNOW).apply();
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                assertMode(widgets[0], "rail", "SNOW");
                assertMode(widgets[1], "launcher", "SNOW");
                prefs.edit().putInt("driveModeSetting", -1).apply();
                monitor[0].refresh();
                assertMode(widgets[0], "rail", "—");
                assertMode(widgets[1], "launcher", "—");
                assertEquals(-1, prefs.getInt("driveModeSetting", 99));
            });
        } finally {
            instrumentation.runOnMainSync(() -> { if (monitor[0] != null) monitor[0].stop(); });
        }
    }

    @Test public void periodicRefreshPicksUpExternalModeChangesWithoutNavigation() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Source source = new Source();
        HeaderStatusMonitor[] monitor = new HeaderStatusMonitor[1];
        StatusWidgetsView[] widget = new StatusWidgetsView[1];
        try {
            instrumentation.runOnMainSync(() -> {
                widget[0] = new StatusWidgetsView(context(), "live", tab -> {});
                monitor[0] = new HeaderStatusMonitor(context(), widget[0], source, () -> false);
                monitor[0].start();
            });
            instrumentation.waitForIdleSync();
            CountDownLatch changed = new CountDownLatch(1);
            instrumentation.runOnMainSync(() -> {
                source.next = snapshot(true, DriveModeStyle.ECO);
                source.onRefresh = () -> { if (source.current.driveMode == DriveModeStyle.ECO) changed.countDown(); };
            });
            assertTrue("Periodic UI refresh must request fresh vehicle state", changed.await(5, TimeUnit.SECONDS));
            instrumentation.runOnMainSync(() -> {
                assertMode(widget[0], "live", "ECO");
                assertTrue(source.refreshes >= 2);
            });
        } finally {
            instrumentation.runOnMainSync(() -> { if (monitor[0] != null) monitor[0].stop(); });
        }
    }

    @Test public void stoppingCancelsQueuedAndPeriodicReadsAndRestartRefreshesAgain() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Source source = new Source();
        HeaderStatusMonitor[] monitor = new HeaderStatusMonitor[1];
        int[] before = new int[1];
        CountDownLatch readAfterStop = new CountDownLatch(1);
        try {
            instrumentation.runOnMainSync(() -> {
                monitor[0] = new HeaderStatusMonitor(context(), null, source, () -> false);
                monitor[0].start();
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                before[0] = source.refreshes;
                source.onRefresh = readAfterStop::countDown;
                monitor[0].refresh();
                monitor[0].stop();
                assertTrue(source.listeners.isEmpty());
            });
            assertFalse("Hidden UI must not continue reading vehicle state", readAfterStop.await(2300, TimeUnit.MILLISECONDS));
            instrumentation.runOnMainSync(() -> {
                assertEquals(before[0], source.refreshes);
                source.onRefresh = () -> {};
                source.next = snapshot(true, DriveModeStyle.SPORT);
                monitor[0].start();
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                assertTrue(source.refreshes > before[0]);
                assertEquals(DriveModeStyle.SPORT, source.current.driveMode);
            });
        } finally {
            instrumentation.runOnMainSync(() -> { if (monitor[0] != null) monitor[0].stop(); });
        }
    }
}
