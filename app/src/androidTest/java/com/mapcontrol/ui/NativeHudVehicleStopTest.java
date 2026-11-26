package com.mapcontrol.ui;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.mapcontrol.nav.NativeHudVehicle;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Records the actual transport's intents without broadcasting to a vehicle or binding a service. */
@RunWith(AndroidJUnit4.class)
public class NativeHudVehicleStopTest {
    @Test public void stopMatchesOriginalJCarToolsEvenWhenAnotherComponentEstablishedTheConnection() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        List<Intent> sent = new ArrayList<>();
        Context recording = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public void sendBroadcast(Intent intent) { sent.add(new Intent(intent)); }
        };
        // The manager can find CarInfoProxy already connected and never call this vehicle's connect().
        NativeHudVehicle vehicle = new NativeHudVehicle(recording);
        assertTrue(vehicle.stopNavigation());
        assertEquals(2, sent.size());
        assertEquals("turbodog.navigation.system.message", sent.get(0).getAction());
        assertEquals(203, sent.get(0).getIntExtra("CODE", -1));
        assertEquals("AUTONAVI_STANDARD_BROADCAST_SEND", sent.get(1).getAction());
        assertEquals(10019, sent.get(1).getIntExtra("KEY_TYPE", -1));
        assertEquals(12, sent.get(1).getIntExtra("EXTRA_STATE", -1));
    }

    @Test public void rejectedStopBroadcastIsNotReportedAsSuccess() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context denied = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public void sendBroadcast(Intent intent) { throw new SecurityException("test denial"); }
        };
        assertFalse(new NativeHudVehicle(denied).stopNavigation());
    }
}
