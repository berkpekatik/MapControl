package com.mapcontrol.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.mapcontrol.ui.builder.TopBarBuilder;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class HeaderStatusSinkTest {

    @Test
    public void testHeaderStatusSinkUpdates() {
        AtomicReference<TopBarBuilder.WifiState> wifiRef = new AtomicReference<>(TopBarBuilder.WifiState.UNKNOWN);
        AtomicBoolean driveConnectedRef = new AtomicBoolean(false);
        AtomicInteger driveModeRef = new AtomicInteger(Integer.MIN_VALUE);
        AtomicBoolean serverRunningRef = new AtomicBoolean(false);
        AtomicBoolean projectionActiveRef = new AtomicBoolean(false);
        AtomicBoolean soundBoostActiveRef = new AtomicBoolean(false);
        AtomicInteger soundGainRef = new AtomicInteger(0);

        HeaderStatusSink sink = new HeaderStatusSink() {
            @Override
            public void setWifiState(TopBarBuilder.WifiState state) {
                wifiRef.set(state);
            }

            @Override
            public void setDriveMode(boolean connected, int mode) {
                driveConnectedRef.set(connected);
                driveModeRef.set(mode);
            }

            @Override
            public void setServerRunning(boolean running) {
                serverRunningRef.set(running);
            }

            @Override
            public void setProjectionActive(boolean active) {
                projectionActiveRef.set(active);
            }

            @Override
            public void setSoundBoost(boolean active, int gainMb) {
                soundBoostActiveRef.set(active);
                soundGainRef.set(gainMb);
            }
        };

        sink.setWifiState(TopBarBuilder.WifiState.CONNECTED);
        sink.setDriveMode(true, 2);
        sink.setServerRunning(true);
        sink.setProjectionActive(true);
        sink.setSoundBoost(true, 600);

        assertEquals(TopBarBuilder.WifiState.CONNECTED, wifiRef.get());
        assertTrue(driveConnectedRef.get());
        assertEquals(2, driveModeRef.get());
        assertTrue(serverRunningRef.get());
        assertTrue(projectionActiveRef.get());
        assertTrue(soundBoostActiveRef.get());
        assertEquals(600, soundGainRef.get());
    }
}
