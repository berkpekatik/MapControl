package com.mapcontrol.launcher

import com.mapcontrol.vehicle.VehicleMetricsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelHelpersTest {

    private fun snap(fuel: Int, connected: Boolean = true) = VehicleMetricsSnapshot(
        connected, 0, 0, 0, fuel, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    )

    @Test fun rawIsAlwaysTenths() {
        assertEquals(694, snap(694).fuelTenthsOrNull())
        assertEquals(60, snap(60).fuelTenthsOrNull())
        assertEquals(100, snap(100).fuelTenthsOrNull())
        assertEquals(0, snap(0).fuelTenthsOrNull())
        assertEquals(101, snap(101).fuelTenthsOrNull())
    }

    @Test fun clampedTo1000() {
        assertEquals(1000, snap(1200).fuelTenthsOrNull())
        assertEquals(1000, snap(1500).fuelTenthsOrNull())
    }

    @Test fun invalidOrDisconnectedIsNull() {
        assertNull(snap(Int.MIN_VALUE).fuelTenthsOrNull())
        assertNull(snap(-5).fuelTenthsOrNull())
        assertNull(snap(50, connected = false).fuelTenthsOrNull())
        assertNull(snap(50, connected = false).fuelPercentOrNull())
    }

    @Test fun percentRounds() {
        assertEquals(69, snap(694).fuelPercentOrNull())
        assertEquals(70, snap(695).fuelPercentOrNull())
        assertEquals(7, snap(69).fuelPercentOrNull())
        assertEquals(6, snap(60).fuelPercentOrNull())
        assertEquals(10, snap(100).fuelPercentOrNull())
        assertEquals(100, snap(1200).fuelPercentOrNull())
        assertEquals(10, snap(101).fuelPercentOrNull())
    }
}
