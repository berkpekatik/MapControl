package com.mapcontrol.ui.activity;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.mapcontrol.ui.builder.DriveModeTabBuilder;
import com.mapcontrol.ui.builder.LauncherTabBuilder;
import com.mapcontrol.ui.builder.TopBarBuilder;
import com.mapcontrol.util.TargetPackageStore;
import com.mapcontrol.vehicle.DriveModeStyle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DriveModeSelectionWidgetsTest {
    private Object field(MainActivity activity, String name) {
        try {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(activity);
        } catch (Exception failure) { throw new AssertionError(failure); }
    }
    private void call(MainActivity activity, String name, Class<?> type, Object value) {
        try {
            Method method = MainActivity.class.getDeclaredMethod(name, type);
            method.setAccessible(true);
            method.invoke(activity, value);
        } catch (Exception failure) { throw new AssertionError(failure); }
    }
    private void assertBoth(MainActivity activity, String expected) {
        View rail = ((TopBarBuilder) field(activity, "topBarBuilder")).getStatusWidgets();
        View launcher = ((LauncherTabBuilder) field(activity, "launcherTabBuilder")).getRootView();
        assertEquals("Rail must reflect the app selection", expected,
                ((TextView) rail.findViewWithTag("header_mode_value")).getText().toString());
        assertEquals("Fullscreen must reflect the same app selection", expected,
                ((TextView) launcher.findViewWithTag("launcher_status_mode_value")).getText().toString());
    }
    private MainActivity launch(Instrumentation instrumentation, Context context) throws Exception {
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long deadline = System.currentTimeMillis() + 15000;
        boolean[] ready = {false};
        while (!ready[0] && System.currentTimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> ready[0] = field(activity, "headerStatusMonitor") != null
                    && field(activity, "launcherTabBuilder") != null);
            Thread.sleep(30);
        }
        assertTrue("MainActivity must finish constructing both widgets", ready[0]);
        instrumentation.waitForIdleSync();
        return activity;
    }
    @SuppressWarnings("unchecked")
    private void restore(SharedPreferences prefs, Map<String, ?> before) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Map.Entry<String, ?> entry : before.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
        }
        editor.commit();
    }
    @Test public void realModeCardClicksUpdateBothWidgetsAndSurviveMissingOemReplies() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        SharedPreferences prefs = TargetPackageStore.prefs(context);
        Map<String, ?> before = prefs.getAll();
        Field singleton = CarInfoProxy.class.getDeclaredField("mInstance");
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        List<Integer> commands = new ArrayList<>();
        CarInfoProxy commandReceiver = new CarInfoProxy() {
            @Override public void init(Context ignored) {}
            @Override public boolean isServiceConnnected() { return true; }
            @Override public void regCallBack(int module, IVDBusNotify.Stub listener, int[] ids) {}
            @Override public void unRegCallBack(int module, IVDBusNotify.Stub listener) {}
            @Override public int[] getItemValues(int module, int id) { return new int[]{0}; }
            @Override public void sendItemValue(int module, int id, int value) {
                if (module == VDEventCarInfo.MODULE_NEW_ENERGY && id == 4) commands.add(value);
            }
        };
        MainActivity activity = null;
        try {
            singleton.set(null, commandReceiver);
            prefs.edit().putBoolean("disclaimerAccepted", true).putBoolean("appManagementDisclaimerAccepted", true)
                    .putBoolean("onboardingCompleted", true).putBoolean("launcherModeEnabled", false)
                    .putBoolean("floatingBackButtonEnabled", false).putBoolean("bootAutoStart", false)
                    .putBoolean("driveMode4x4Expanded", true)
                    .remove("driveModeSetting").commit();
            activity = launch(instrumentation, context);
            MainActivity first = activity;
            instrumentation.runOnMainSync(() -> {
                call(first, "switchTab", int.class, 6);
                DriveModeTabBuilder builder = (DriveModeTabBuilder) field(first, "driveModeTabBuilder");
                int commandCount = commands.size();
                for (int mode : new int[]{0, 1, 2, 3, 4, 5, 7}) {
                    View card = builder.getScrollView().findViewWithTag("drive_mode_card_" + mode);
                    assertNotNull(card);
                    assertTrue(card.performClick());
                    assertEquals(mode, prefs.getInt("driveModeSetting", -1));
                    assertBoth(first, DriveModeStyle.shortLabel(mode));
                    assertEquals(++commandCount, commands.size());
                    assertEquals(mode, (int) commands.get(commands.size() - 1));
                }
                builder.getScrollView().findViewWithTag("drive_mode_clear").performClick();
                assertBoth(first, "—");
                assertEquals(-1, prefs.getInt("driveModeSetting", 99));
                assertEquals("Clearing a UI choice must not send a new command", commandCount, commands.size());
                builder.getScrollView().findViewWithTag("drive_mode_card_2").performClick();
                assertBoth(first, "SPORT");
                call(first, "applyLauncherChrome", boolean.class, true);
                call(first, "switchTab", int.class, (Integer) field(first, "TAB_LAUNCHER"));
                assertBoth(first, "SPORT");
            });
            // A null OEM reply from the existing two-second timer must not erase the app choice.
            Thread.sleep(2300);
            instrumentation.runOnMainSync(() -> assertBoth(first, "SPORT"));
            instrumentation.runOnMainSync(first::finish);
            instrumentation.waitForIdleSync();
            activity = launch(instrumentation, context);
            MainActivity restarted = activity;
            instrumentation.runOnMainSync(() -> assertBoth(restarted, "SPORT"));
        } finally {
            if (activity != null) {
                MainActivity last = activity;
                instrumentation.runOnMainSync(last::finish);
                instrumentation.waitForIdleSync();
            }
            singleton.set(null, previous);
            restore(prefs, before);
        }
    }
}
