package com.mapcontrol.nav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.provider.Settings;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.mapcontrol.util.AppLaunchHelper;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

/** Applies every cluster style on the secondary display and verifies the overlay attaches. */
@RunWith(AndroidJUnit4.class)
public class ClusterOverlaySmokeTest {

    private final String[] styles = {
            GoogleMapsClusterNavOverlay.STYLE_HUD,
            GoogleMapsClusterNavOverlay.STYLE_DUAL,
            GoogleMapsClusterNavOverlay.STYLE_RADIAL,
            GoogleMapsClusterNavOverlay.STYLE_TRAFFIC
    };

    @After
    public void tearDown() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        GoogleMapsClusterNavOverlay.setCardOpacity(context, false, 100);
        GoogleMapsClusterNavOverlay.setCardOpacity(context, true, 100);
        GoogleMapsClusterNavOverlay.getInstance(context).hide();
    }

    @Test
    public void allStylesAttachToClusterDisplay() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue("overlay permission is required", Settings.canDrawOverlays(context));
        Assume.assumeTrue("cluster display is required",
                AppLaunchHelper.getClusterDisplayId(context) != 0);

        GoogleMapsNavSnapshot snapshot = new GoogleMapsNavSnapshot(
                true,
                "Turn left",
                "350 m - Main St",
                "13 min · 8.5 km · 11:15",
                true);

        GoogleMapsClusterNavOverlay.setCardOpacity(context, false, 55);
        GoogleMapsClusterNavOverlay.setCardOpacity(context, true, 55);

        for (String style : styles) {
            GoogleMapsClusterNavOverlay.setStyle(context, style);
            GoogleMapsClusterNavOverlay overlay =
                    GoogleMapsClusterNavOverlay.getInstance(context);
            overlay.apply(snapshot);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            Thread.sleep(350);
            assertEquals(style, GoogleMapsClusterNavOverlay.getStyle(context));
            assertNotNull("overlay did not attach for style " + style, field(overlay, "overlayRoot"));
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
