package com.mapcontrol.vehicle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VehicleQuickControlsTest {

    @Test
    public void lockCommandUsesTwoForLockAndOneForUnlock() {
        assertEquals(2, VehicleQuickControls.centralLockValue(true));
        assertEquals(1, VehicleQuickControls.centralLockValue(false));
    }

    @Test
    public void readbackZeroMeansLocked() {
        assertTrue(VehicleQuickControls.isLockedValue(0));
        assertFalse(VehicleQuickControls.isLockedValue(1));
        // Komut değeri 2 geri okumada "kilitli" sayılmaz.
        assertFalse(VehicleQuickControls.isLockedValue(2));
    }
}
