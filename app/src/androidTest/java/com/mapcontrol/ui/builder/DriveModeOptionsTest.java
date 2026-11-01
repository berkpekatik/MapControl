package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.mapcontrol.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DriveModeOptionsTest {
    private Context context() {
        return new ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                R.style.Theme_MapControl);
    }

    private DriveModeTabBuilder builder(Context context, SharedPreferences prefs, AtomicInteger callbacks) {
        return new DriveModeTabBuilder(context, prefs, new DriveModeTabBuilder.DriveModeCallback() {
            @Override public void onModeSelected(int mode) { callbacks.incrementAndGet(); }
            @Override public void log(String message) {}
        });
    }

    private void assertVisibleModes(DriveModeTabBuilder builder, boolean expanded) {
        for (int mode : new int[]{0, 1, 2}) {
            assertEquals(View.VISIBLE, builder.getScrollView().findViewWithTag("drive_mode_card_" + mode).getVisibility());
        }
        for (int mode : new int[]{3, 4, 5, 7}) {
            assertEquals(expanded ? View.VISIBLE : View.GONE,
                    builder.getScrollView().findViewWithTag("drive_mode_card_" + mode).getVisibility());
        }
        assertEquals(View.GONE, builder.getScrollView().findViewWithTag("drive_mode_card_-1").getVisibility());
    }

    @Test public void defaultsToThreeModesAndRemembersExpansionWithoutSelectingMode() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = context();
            SharedPreferences prefs = context.getSharedPreferences("drive_mode_options_test", 0);
            prefs.edit().clear().commit();
            AtomicInteger callbacks = new AtomicInteger();
            DriveModeTabBuilder builder = builder(context, prefs, callbacks);
            try {
                assertVisibleModes(builder, false);
                CompoundButton toggle = builder.getScrollView().findViewWithTag("drive_mode_4x4");
                assertFalse(toggle.isChecked());
                toggle.performClick();
                assertVisibleModes(builder, true);
                assertTrue(prefs.getBoolean("driveMode4x4Expanded", false));
                assertEquals(-1, prefs.getInt("driveModeSetting", -1));
                assertEquals(0, callbacks.get());
                builder.release();
                builder = builder(context, prefs, callbacks);
                assertVisibleModes(builder, true);
                assertTrue(((CompoundButton) builder.getScrollView().findViewWithTag("drive_mode_4x4")).isChecked());
                assertEquals(0, callbacks.get());
            } finally {
                builder.release();
                prefs.edit().clear().commit();
            }
        });
    }

    @Test public void hidingFourWheelModesPreservesSelectionAndClearStillDisablesStartupPreference() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = context();
            SharedPreferences prefs = context.getSharedPreferences("drive_mode_options_test", 0);
            prefs.edit().clear().putInt("driveModeSetting", 3).putBoolean("driveMode4x4Expanded", true).commit();
            AtomicInteger callbacks = new AtomicInteger();
            DriveModeTabBuilder builder = builder(context, prefs, callbacks);
            try {
                CompoundButton toggle = builder.getScrollView().findViewWithTag("drive_mode_4x4");
                View snow = builder.getScrollView().findViewWithTag("drive_mode_card_3");
                assertTrue(snow.isSelected());
                toggle.performClick();
                assertVisibleModes(builder, false);
                assertEquals(3, prefs.getInt("driveModeSetting", -1));
                assertEquals(0, callbacks.get());
                toggle.performClick();
                assertVisibleModes(builder, true);
                assertTrue(snow.isSelected());
                assertEquals(0, callbacks.get());
                builder.getScrollView().findViewWithTag("drive_mode_clear").performClick();
                assertEquals(-1, prefs.getInt("driveModeSetting", 3));
                assertEquals(1, callbacks.get());
                assertFalse(snow.isSelected());
                assertEquals(View.GONE, builder.getScrollView().findViewWithTag("drive_mode_clear").getVisibility());
            } finally {
                builder.release();
                prefs.edit().clear().commit();
            }
        });
    }

    private CompoundButton firstChoice(View view) {
        if (view instanceof CompoundButton) return (CompoundButton) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                CompoundButton found = firstChoice(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

}
