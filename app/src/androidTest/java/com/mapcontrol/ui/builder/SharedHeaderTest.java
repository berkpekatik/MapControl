package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import android.widget.LinearLayout;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.DriveModeStyle;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SharedHeaderTest {
    private Context context(String language) {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(new Locale(language));
        return new ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_MapControl);
    }
    private TopBarBuilder builder(Context context, AtomicInteger selected) {
        return new TopBarBuilder(context, new TopBarBuilder.TopBarCallback() {
            public void onLogTabToggle(boolean show) {}
            public void log(String message) {}
            public void onStatusSelected(int tab) { selected.set(tab); }
        });
    }
    private View headerWithWidgets(Context context, TopBarBuilder builder) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        View widgets = builder.getStatusWidgets();
        if (widgets.getParent() instanceof ViewGroup) {
            ((ViewGroup) widgets.getParent()).removeView(widgets);
        }
        root.addView(widgets, new LinearLayout.LayoutParams(-1, -2));
        root.addView(builder.build(), new LinearLayout.LayoutParams(-1, -2));
        return root;
    }

    private void layout(View view, Context context, int widthDp) {
        int width = Math.round(widthDp * context.getResources().getDisplayMetrics().density);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, width, view.getMeasuredHeight());
    }
    private void assertTextFits(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            assertNotNull(text.getLayout());
            for (int line = 0; line < text.getLayout().getLineCount(); line++) {
                assertTrue(text.getText() + " tag=" + text.getTag() + " line=" + line
                                + " measured=" + text.getLayout().getLineMax(line)
                                + " available=" + (text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight())
                                + " fontScale=" + text.getResources().getConfiguration().fontScale,
                        text.getLayout().getLineMax(line) <= text.getWidth()
                                - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight() + 1);
                assertEquals("Status or branding text must not be truncated", 0,
                        text.getLayout().getEllipsisCount(line));
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (group.getChildAt(i).getVisibility() != View.GONE) assertTextFits(group.getChildAt(i));
            }
        }
    }

    @Test public void titlesAndLiveStatesKeepTheSameHeaderHeightWithoutClipping() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (String language : new String[]{"tr", "en"}) {
                Context context = context(language);
                TopBarBuilder builder = builder(context, new AtomicInteger());
                View header = headerWithWidgets(context, builder);
                layout(header, context, 1400);
                int height = header.getHeight();
                for (int title : new int[]{R.string.side_rail_wifi, R.string.side_rail_apps,
                        R.string.side_rail_web, R.string.side_rail_drive_mode, R.string.side_rail_vehicle_info,
                        R.string.side_rail_projection, R.string.side_rail_settings, R.string.side_rail_welcome_sound,
                        R.string.side_rail_equalizer, R.string.topbar_vehicle_home}) {
                    builder.getTitleView().setText(title);
                    builder.setWifiState(TopBarBuilder.WifiState.DISCONNECTED);
                    builder.setDriveMode(true, DriveModeStyle.NORMAL);
                    builder.setServerRunning(true);
                    builder.setProjectionActive(true);
                    layout(header, context, 1400);
                    assertEquals(height, header.getHeight());
                    assertTextFits(header);
                }
                for (int width : new int[]{720, 400, 1400}) {
                    layout(header, context, width);
                    assertTextFits(header);
                    assertTrue(header.findViewWithTag("header_projection").getWidth() > 0);
                }
            }
        });
    }

    @Test public void widgetsStayOutsideHeaderAndAboveScrollingRailInBothOrientations() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (String language : new String[]{"tr", "en", "ar"}) {
                for (float fontScale : new float[]{1f, 1.3f}) {
                    Context base = context(language);
                    Configuration config = new Configuration(base.getResources().getConfiguration());
                    config.fontScale = fontScale;
                    Context themed = new ContextThemeWrapper(base.createConfigurationContext(config),
                            R.style.Theme_MapControl);
                    TopBarBuilder builder = builder(themed, new AtomicInteger());
                    View header = builder.build();
                    View widgets = builder.getStatusWidgets();
                    assertNull(header.findViewWithTag("header_statuses"));
                    SideRailBuilder railBuilder = new SideRailBuilder(themed,
                            themed.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE),
                            new SideRailBuilder.SideRailCallback() {
                                public void onTabSelected(int tab, String title) {}
                                public void onAppManagementRequested() {}
                                public void log(String message) {}
                            });
                    railBuilder.setStatusWidgets(widgets);
                    builder.setWifiState(TopBarBuilder.WifiState.DISCONNECTED);
                    builder.setSoundBoost(true, 250);
                    for (boolean portrait : new boolean[]{false, true, false}) {
                        ViewGroup rail = portrait ? railBuilder.buildPortrait() : railBuilder.build();
                        assertSame(widgets, rail.getChildAt(0));
                        assertSame(rail, widgets.getParent());
                        for (int width : new int[]{520, 300, 720}) {
                            layout(rail, themed, width);
                            assertTrue(widgets.getHeight() > 0);
                            assertEquals(0, widgets.getTop());
                            ViewGroup row = (ViewGroup) widgets;
                            int commonTop = row.getChildAt(0).getTop();
                            for (int index = 0; index < row.getChildCount(); index++) {
                                View chip = row.getChildAt(index);
                                if (chip.getVisibility() == View.GONE) continue;
                                assertEquals("Panel widgets must share one row", commonTop, chip.getTop());
                                assertTrue(chip.getRight() <= widgets.getWidth() - widgets.getPaddingRight());
                            }
                            assertTextFits(widgets);
                            assertEquals(themed.getString(R.string.header_disconnected),
                                    ((TextView) widgets.findViewWithTag("header_wifi_value")).getText().toString());
                        }
                    }
                    builder.release();
                }
            }
        });
    }

    @Test public void launcherStatusCardsKeepTwoColumnsAndNavigationAtDifferentWidths() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (String language : new String[]{"tr", "en", "ar"}) {
                for (float scale : new float[]{1f, 1.3f}) {
                    Context base = context(language);
                    Configuration config = new Configuration(base.getResources().getConfiguration());
                    config.fontScale = scale;
                    Context themed = new ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_MapControl);
                    AtomicInteger selected = new AtomicInteger(-1);
                    com.mapcontrol.ui.widget.StatusWidgetsView widgets =
                            new com.mapcontrol.ui.widget.StatusWidgetsView(themed, "launcher_status", selected::set);
                    widgets.setColumnCount(2);
                    widgets.setWifiState(TopBarBuilder.WifiState.DISCONNECTED);
                    widgets.setDriveMode(true, DriveModeStyle.NORMAL);
                    widgets.setServerRunning(true);
                    widgets.setProjectionActive(true);
                    for (boolean sound : new boolean[]{false, true, false}) {
                        widgets.setSoundBoost(sound, 250);
                        for (int width : new int[]{400, 300, 1000, 400}) {
                            layout(widgets, themed, width);
                            View wifi = widgets.getChildAt(0), mode = widgets.getChildAt(1);
                            View phone = widgets.getChildAt(2), projection = widgets.getChildAt(3);
                            assertEquals(wifi.getTop(), mode.getTop());
                            assertEquals(phone.getTop(), projection.getTop());
                            assertTrue(phone.getTop() > wifi.getBottom());
                            assertTrue(mode.getLeft() > wifi.getRight());
                            assertTextFits(widgets);
                            for (int i = 0; i < widgets.getChildCount(); i++) {
                                View chip = widgets.getChildAt(i);
                                if (chip.getVisibility() == View.GONE) continue;
                                assertTrue(chip.getRight() <= widgets.getWidth());
                                assertTrue(chip.getHeight() >= Math.round(64 * themed.getResources().getDisplayMetrics().density));
                                chip.performClick();
                                assertEquals(new int[]{0, 6, 1, 3, 11}[i], selected.get());
                            }
                        }
                    }
                }
            }
        });
    }

    @Test public void disconnectClearsActualModeAndRebuildPreservesOtherStatusValues() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = context("tr");
            android.content.SharedPreferences prefs = context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
            boolean hadMode = prefs.contains("driveModeSetting");
            int rememberedMode = prefs.getInt("driveModeSetting", -1);
            AtomicInteger selected = new AtomicInteger(-1);
            TopBarBuilder builder = builder(context, selected);
            try {
                prefs.edit().putInt("driveModeSetting", DriveModeStyle.SPORT).commit();
                View header = headerWithWidgets(context, builder);
                TextView mode = header.findViewWithTag("header_mode_value");
                builder.reapplyTheme();
                assertEquals("—", mode.getText().toString());
                builder.setDriveMode(true, DriveModeStyle.ECO);
                assertEquals("ECO", mode.getText().toString());
                builder.setDriveMode(false, DriveModeStyle.ECO);
                assertEquals("—", mode.getText().toString());
                assertEquals(UiStyles.color(context, R.color.textSecondary), mode.getCurrentTextColor());
                builder.reapplyTheme();
                assertEquals("—", mode.getText().toString());
                builder.setDriveMode(true, 6);
                assertEquals("—", mode.getText().toString());
                builder.setDriveMode(true, DriveModeStyle.SNOW);
                builder.setServerRunning(true);
                builder.setProjectionActive(true);
                builder.setWifiState(TopBarBuilder.WifiState.CONNECTED);
                header = headerWithWidgets(context, builder);
                assertEquals("SNOW", ((TextView) header.findViewWithTag("header_mode_value")).getText().toString());
                assertEquals(context.getString(R.string.header_running),
                        ((TextView) header.findViewWithTag("header_phone_value")).getText().toString());
                assertEquals(context.getString(R.string.header_active),
                        ((TextView) header.findViewWithTag("header_projection_value")).getText().toString());
                Configuration night = new Configuration(context.getResources().getConfiguration());
                night.uiMode = (night.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
                try {
                    UiStyles.setUiModeOverride(night);
                    builder.reapplyTheme();
                    TextView value = header.findViewWithTag("header_mode_value");
                    assertEquals("SNOW", value.getText().toString());
                    assertEquals(DriveModeStyle.accentColor(context, DriveModeStyle.SNOW), value.getCurrentTextColor());
                } finally { UiStyles.setUiModeOverride(null); }
                assertEquals(DriveModeStyle.SPORT, prefs.getInt("driveModeSetting", -1));
                assertEquals("Status rendering must not navigate or select a drive mode", -1, selected.get());
            } finally {
                builder.release();
                if (hadMode) prefs.edit().putInt("driveModeSetting", rememberedMode).commit();
                else prefs.edit().remove("driveModeSetting").commit();
            }
        });
    }

    @Test public void statusMonitorDisplaysAppSelectionWithoutClaimingVehicleConnectivity() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = context("tr");
            android.content.SharedPreferences prefs = context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
            boolean hadMode = prefs.contains("driveModeSetting");
            int rememberedMode = prefs.getInt("driveModeSetting", -1);
            AtomicInteger lastMode = new AtomicInteger(Integer.MAX_VALUE);
            AtomicInteger updates = new AtomicInteger();
            com.mapcontrol.ui.HeaderStatusSink sink = new com.mapcontrol.ui.HeaderStatusSink() {
                public void setWifiState(TopBarBuilder.WifiState state) {}
                public void setDriveMode(boolean connected, int mode) { lastMode.set(mode); updates.incrementAndGet(); }
                public void setServerRunning(boolean running) {}
                public void setProjectionActive(boolean active) {}
                public void setSoundBoost(boolean active, int gainMb) {}
            };
            com.mapcontrol.ui.HeaderStatusMonitor monitor =
                    new com.mapcontrol.ui.HeaderStatusMonitor(context, null, () -> false);
            try {
                prefs.edit().putInt("driveModeSetting", DriveModeStyle.SPORT).commit();
                int liveMode = Integer.MIN_VALUE;
                assertFalse(DriveModeStyle.isThemed(liveMode));
                monitor.addSink(sink);
                assertEquals(DriveModeStyle.SPORT, lastMode.get());
                monitor.refresh();
                assertEquals(DriveModeStyle.SPORT, lastMode.get());
                assertEquals(2, updates.get());
                assertEquals(DriveModeStyle.SPORT, prefs.getInt("driveModeSetting", -1));
            } finally {
                monitor.stop();
                if (hadMode) prefs.edit().putInt("driveModeSetting", rememberedMode).commit();
                else prefs.edit().remove("driveModeSetting").commit();
            }
        });
    }

    @Test public void statusIndicatorsNavigateWithoutChangingTheirState() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = context("tr");
            AtomicInteger selected = new AtomicInteger(-1);
            TopBarBuilder builder = builder(context, selected);
            View header = headerWithWidgets(context, builder);
            String[] tags = {"header_wifi", "header_mode", "header_phone", "header_projection"};
            int[] tabs = {0, 6, 1, 3};
            for (int i = 0; i < tags.length; i++) {
                header.findViewWithTag(tags[i]).performClick();
                assertEquals(tabs[i], selected.get());
            }
            assertEquals(context.getString(R.string.header_stopped),
                    ((TextView) header.findViewWithTag("header_phone_value")).getText().toString());
        });
    }
    @Test public void titleAndOneStatusLeaveOtherIndicatorsUntouched() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = context("tr");
            TopBarBuilder builder = builder(context, new AtomicInteger());
            View header = headerWithWidgets(context, builder);
            View wifi = header.findViewWithTag("header_wifi");
            View mode = header.findViewWithTag("header_mode");
            View phone = header.findViewWithTag("header_phone");
            View projection = header.findViewWithTag("header_projection");
            Object wifiBackground = wifi.getBackground(), modeBackground = mode.getBackground();
            Object phoneBackground = phone.getBackground(), projectionBackground = projection.getBackground();
            builder.setTitle(R.string.side_rail_vehicle_info);
            assertSame(wifiBackground, wifi.getBackground());
            assertSame(modeBackground, mode.getBackground());
            assertSame(phoneBackground, phone.getBackground());
            assertSame(projectionBackground, projection.getBackground());
            builder.setDriveMode(true, DriveModeStyle.SPORT);
            assertNotSame(modeBackground, mode.getBackground());
            assertSame(wifiBackground, wifi.getBackground());
            assertSame(phoneBackground, phone.getBackground());
            assertSame(projectionBackground, projection.getBackground());
            modeBackground = mode.getBackground();
            builder.setDriveMode(true, DriveModeStyle.SPORT);
            assertSame(modeBackground, mode.getBackground());
            builder.setWifiState(TopBarBuilder.WifiState.CONNECTED);
            assertSame(modeBackground, mode.getBackground());
            assertSame(phoneBackground, phone.getBackground());
            assertSame(projectionBackground, projection.getBackground());
        });
    }

}
