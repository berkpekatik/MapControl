package com.mapcontrol.ui.activity;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.mapcontrol.R;
import com.mapcontrol.util.TargetPackageStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SharedHeaderNavigationTest {
    private Object field(MainActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }
    private void call(MainActivity activity, String name, Class<?> arg, Object value) {
        try {
            Method method = MainActivity.class.getDeclaredMethod(name, arg);
            method.setAccessible(true);
            method.invoke(activity, value);
        } catch (Exception failure) { throw new AssertionError(failure); }
    }
    private void screenshot(Instrumentation instrumentation, Context context, String name) throws Exception {
        instrumentation.waitForIdleSync();
        Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        File folder = new File(context.getFilesDir(), "ui-qa");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(folder, name))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally { screenshot.recycle(); }
    }

    @Test public void categoryTransitionCoversOnlyBodyAndKeepsHeaderInstance() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        SharedPreferences prefs = TargetPackageStore.prefs(context);
        Map<String, ?> before = prefs.getAll();
        prefs.edit().putBoolean("disclaimerAccepted", true)
                .putBoolean("appManagementDisclaimerAccepted", true).putBoolean("onboardingCompleted", true)
                .putBoolean("launcherModeEnabled", false).putBoolean("floatingBackButtonEnabled", false)
                .putBoolean("bootAutoStart", false).commit();
        MainActivity activity = null;
        try {
            activity = (MainActivity) instrumentation.startActivitySync(
                    new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            MainActivity host = activity;
            long deadline = System.currentTimeMillis() + 15000;
            View[] header = new View[1];
            while (header[0] == null && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync(() -> header[0] = host.getWindow().getDecorView().findViewWithTag("shared_header"));
                Thread.sleep(50);
            }
            assertNotNull(header[0]);
            instrumentation.waitForIdleSync();
            Object[] modeBackground = new Object[1];
            View[] pinnedWidgets = new View[1];
            instrumentation.runOnMainSync(() -> {
                modeBackground[0] = host.getWindow().getDecorView().findViewWithTag("header_mode").getBackground();
                call(host, "crossfadeContent", Runnable.class, (Runnable) () -> call(host, "switchTab", int.class, 6));
                try {
                    Map<?, ?> veils = (Map<?, ?>) field(host, "transitionVeils");
                    assertTrue(veils.containsKey(field(host, "tabContentArea")));
                    assertFalse(veils.containsKey(field(host, "mainContent")));
                    assertFalse(veils.containsKey(header[0]));
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
            Thread.sleep(600);
            instrumentation.runOnMainSync(() -> {
                assertSame(header[0], host.getWindow().getDecorView().findViewWithTag("shared_header"));
                assertEquals(host.getString(R.string.side_rail_drive_mode),
                        ((TextView) header[0].findViewWithTag("header_title")).getText().toString());
                assertSame(modeBackground[0], host.getWindow().getDecorView().findViewWithTag("header_mode").getBackground());
                call(host, "switchTab", int.class, 9);
                assertSame(header[0], host.getWindow().getDecorView().findViewWithTag("shared_header"));
                assertEquals(host.getString(R.string.side_rail_vehicle_info),
                        ((TextView) header[0].findViewWithTag("header_title")).getText().toString());
            });
            screenshot(instrumentation, context, "normal-header.png");
            instrumentation.runOnMainSync(() -> {
                assertNull(header[0].findViewWithTag("header_statuses"));
                try {
                    View rail = (View) field(host, "sideRail");
                    View widgets = rail.findViewWithTag("header_statuses");
                    assertNotNull(widgets);
                    pinnedWidgets[0] = widgets;
                    assertSame(rail, widgets.getParent());
                    Field launcherTab = MainActivity.class.getDeclaredField("TAB_LAUNCHER");
                    launcherTab.setAccessible(true);
                    call(host, "applyLauncherChrome", boolean.class, true);
                    call(host, "switchTab", int.class, launcherTab.getInt(null));
                    assertEquals(View.GONE, header[0].getVisibility());
                    View clockTitle = host.getWindow().getDecorView().findViewWithTag("launcher_clock_title");
                    View clockWidgets = host.getWindow().getDecorView().findViewWithTag("launcher_status_statuses");
                    assertNotNull(clockTitle);
                    assertNotNull(clockWidgets);
                    assertNotSame(clockTitle.getParent(), clockWidgets.getParent());
                    View quickScroll = host.getWindow().getDecorView().findViewWithTag("launcher_quick_access_scroll");
                    assertSame(clockWidgets, quickScroll.findViewWithTag("launcher_status_statuses"));
                    ViewGroup shortcuts = host.getWindow().getDecorView().findViewWithTag("launcher_shortcuts_grid");
                    assertEquals(7, shortcuts.getChildCount());
                    assertNotNull(shortcuts.findViewWithTag("launcher_shortcut_14"));
                    assertNotNull(shortcuts.findViewWithTag("launcher_shortcut_15"));
                    for (int tab : new int[]{0, 1, 3, 6}) {
                        assertNull(shortcuts.findViewWithTag("launcher_shortcut_" + tab));
                    }
                    assertEquals(View.VISIBLE, clockWidgets.getVisibility());
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                ViewGroup widgets = host.getWindow().getDecorView().findViewWithTag("launcher_status_statuses");
                View wifi = widgets.findViewWithTag("launcher_status_wifi");
                View mode = widgets.findViewWithTag("launcher_status_mode");
                View phone = widgets.findViewWithTag("launcher_status_phone");
                View projection = widgets.findViewWithTag("launcher_status_projection");
                assertTrue(wifi.getWidth() > 0);
                assertEquals(wifi.getTop(), mode.getTop());
                assertEquals(phone.getTop(), projection.getTop());
                assertTrue(phone.getTop() > wifi.getBottom());
                assertTrue(mode.getLeft() > wifi.getRight());
            });
            screenshot(instrumentation, context, "fullscreen-clock-widgets.png");
            instrumentation.runOnMainSync(() -> {
                try {
                    call(host, "switchTab", int.class, 9);
                    call(host, "applyLauncherChrome", boolean.class, false);
                    assertEquals(View.VISIBLE, header[0].getVisibility());
                    assertSame(pinnedWidgets[0], host.getWindow().getDecorView().findViewWithTag("header_statuses"));
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
        } finally {
            if (activity != null) {
                MainActivity host = activity;
                instrumentation.runOnMainSync(host::finish);
                instrumentation.waitForIdleSync();
            }
            SharedPreferences.Editor restore = prefs.edit();
            for (String key : new String[]{"disclaimerAccepted", "appManagementDisclaimerAccepted", "onboardingCompleted",
                    "launcherModeEnabled", "floatingBackButtonEnabled", "bootAutoStart"}) {
                if (before.containsKey(key)) restore.putBoolean(key, (Boolean) before.get(key));
                else restore.remove(key);
            }
            restore.commit();
        }
    }
}
