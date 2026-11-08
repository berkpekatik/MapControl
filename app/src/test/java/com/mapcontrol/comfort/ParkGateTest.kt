package com.mapcontrol.comfort

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkGateTest {

    @Test fun parkGate() {
        assertTrue(ParkGate.evaluate(0, null, 1).allowed)
        assertTrue(ParkGate.evaluate(0, 0, 1).allowed)
        assertFalse(ParkGate.evaluate(null, 0, 1).allowed)   // 119 okunamazsa izin yok
        assertFalse(ParkGate.evaluate(1, 0, 1).allowed)
        assertFalse(ParkGate.evaluate(0, 5, 1).allowed)      // eski hız hareketli
        assertFalse(ParkGate.evaluate(-1, 0, 1).allowed)
        assertFalse(ParkGate.evaluate(65535, 0, 1).allowed)
    }

    @Test fun gearMustBePark() {
        assertFalse(ParkGate.evaluate(0, 0, null).allowed)   // vites okunamazsa izin yok
        assertFalse(ParkGate.evaluate(0, 0, 0).allowed)
        assertFalse(ParkGate.evaluate(0, 0, 2).allowed)
        assertFalse(ParkGate.evaluate(0, 0, 3).allowed)
        assertFalse(ParkGate.evaluate(0, null, 4).allowed)
        assertFalse(ParkGate.evaluate(5, 0, 1).allowed)      // hız hâlâ geçerli kural
        assertTrue(ParkGate.evaluate(0, 0, ParkGate.GEAR_PARK).allowed)
    }
}
