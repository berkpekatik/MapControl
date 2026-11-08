package com.mapcontrol.vehicle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    @Test
    public void missingReplyLeavesLockUnknown() {
        // Cevap yok / sinyal yok: "kilitli" değil, bilinmiyor.
        assertNull(VehicleQuickControls.lockedFromReply(null));
        assertNull(VehicleQuickControls.lockedFromReply(new int[0]));
        assertEquals(Boolean.TRUE, VehicleQuickControls.lockedFromReply(new int[]{0}));
        assertEquals(Boolean.FALSE, VehicleQuickControls.lockedFromReply(new int[]{1}));
    }
}
