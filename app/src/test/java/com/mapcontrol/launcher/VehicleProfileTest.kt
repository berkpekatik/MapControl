package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleProfileTest {

    @Test fun detectsFromDisplay() {
        assertEquals(VehicleProfile.TIGGO7, VehicleProfile.detect(1920, 720))
        assertEquals(VehicleProfile.TIGGO8, VehicleProfile.detect(2560, 1440))
        assertEquals(VehicleProfile.TIGGO8, VehicleProfile.detect(1440, 2560))
        assertEquals(VehicleProfile.TIGGO8, VehicleProfile.detect(1920, 1080)) // 16:9
        assertEquals(VehicleProfile.TIGGO7, VehicleProfile.detect(0, 0))
    }

    @Test fun overrideWins() {
        assertEquals(VehicleProfile.TIGGO7, VehicleProfile.choose(1, VehicleProfile.TIGGO8))
        assertEquals(VehicleProfile.TIGGO8, VehicleProfile.choose(2, VehicleProfile.TIGGO7))
        assertEquals(VehicleProfile.TIGGO8, VehicleProfile.choose(0, VehicleProfile.TIGGO8))
        assertEquals(VehicleProfile.TIGGO8, VehicleProfile.parse(" Tiggo8 "))
    }

    @Test fun barHeight() {
        assertEquals(104, VehicleProfile.TIGGO7.barHeightDp(960f))
        assertEquals(85, VehicleProfile.TIGGO8.barHeightDp(960f))
    }
}
