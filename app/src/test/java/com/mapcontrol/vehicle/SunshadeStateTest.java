package com.mapcontrol.vehicle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Güneşlik durumu: araçta ölçüldü, 1 kapalı, 2 açılıyor, 4 kapanıyor, 5 tam açık, 6 arada durdu. */
public class SunshadeStateTest {

    private static VehicleMetricsSnapshot withSunshade(int sunshade) {
        int inv = VehicleMetricsCache.INVALID;
        return new VehicleMetricsSnapshot(
                true, inv, inv, inv, inv, inv, inv, inv, inv,
                inv, inv, inv, inv, inv,
                inv, inv, sunshade, inv, inv, inv,
                inv);
    }

    @Test
    public void closedIsNotOpen() {
        assertFalse(withSunshade(1).isSunshadeOpen());
    }

    @Test
    public void everyOtherMeasuredValueIsOpen() {
        for (int v : new int[] {2, 3, 4, 5, 6}) {
            assertTrue("deger " + v, withSunshade(v).isSunshadeOpen());
        }
    }

    @Test
    public void unknownIsNotOpen() {
        assertFalse(withSunshade(0).isSunshadeOpen());
        assertFalse(withSunshade(VehicleMetricsCache.INVALID).isSunshadeOpen());
        assertFalse(VehicleMetricsSnapshot.empty(true).isSunshadeOpen());
    }
}
