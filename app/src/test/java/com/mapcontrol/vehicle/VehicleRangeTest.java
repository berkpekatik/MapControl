package com.mapcontrol.vehicle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VehicleRangeTest {

    @Test
    public void stoppedCarSentinelIsNotARange() {
        assertFalse(VehicleRange.isPlausible(61440)); // 0xF000: kontak kapalı / araç duruyor
        assertFalse(VehicleRange.isPlausible(-1));
        assertTrue(VehicleRange.isPlausible(0));
        assertTrue(VehicleRange.isPlausible(395));
        assertTrue(VehicleRange.isPlausible(VehicleRange.MAX_PLAUSIBLE_KM));
    }

    @Test
    public void sentinelFallsBackToLastGoodRange() {
        assertEquals(395, VehicleRange.pick(61440, 395));
        assertEquals(410, VehicleRange.pick(410, 395));
        assertEquals(VehicleRange.NONE, VehicleRange.pick(61440, VehicleRange.NONE));
        assertEquals(VehicleRange.NONE, VehicleRange.pick(61440, 61440));
    }

    @Test
    public void onlyIgnitionOnReadingsAreRemembered() {
        assertTrue(VehicleRange.shouldRemember(395, VehicleRange.POWER_ON));
        assertFalse(VehicleRange.shouldRemember(395, 1));
        assertFalse(VehicleRange.shouldRemember(61440, VehicleRange.POWER_ON));
    }
}
