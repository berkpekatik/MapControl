package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirButtonsPolicyTest {
    @Test fun tiggo8AlwaysShowsAirButtons() {
        assertTrue(VehicleProfile.TIGGO8.showsAirButtons(false))
        assertTrue(VehicleProfile.TIGGO8.showsAirButtons(true))
        assertFalse(VehicleProfile.TIGGO8.airButtonsOptional)
    }

    @Test fun tiggo7ShowsOnlyWhenEnabled() {
        assertFalse(VehicleProfile.TIGGO7.showsAirButtons(false))
        assertTrue(VehicleProfile.TIGGO7.showsAirButtons(true))
        assertTrue(VehicleProfile.TIGGO7.airButtonsOptional)
    }

    @Test fun portraitShowsOnlyWhenEnabled() {
        assertFalse(VehicleProfile.PORTRAIT.showsAirButtons(false))
        assertTrue(VehicleProfile.PORTRAIT.showsAirButtons(true))
        assertTrue(VehicleProfile.PORTRAIT.airButtonsOptional)
    }

    @Test fun portraitWithAirButtonsUsesCompactBar() {
        assertEquals(ClimateBarSize.COMPACT, ClimateBarSize.forBar(VehicleProfile.PORTRAIT, true))
        assertEquals(ClimateBarSize.FULL, ClimateBarSize.forBar(VehicleProfile.PORTRAIT, false))
        assertEquals(ClimateBarSize.FULL, ClimateBarSize.forBar(VehicleProfile.TIGGO8, true))
        assertEquals(ClimateBarSize.FULL, ClimateBarSize.forBar(VehicleProfile.TIGGO7, true))
    }

    /** Dikey ekran ~1275 dp: sabit parçalar ve hava düğmeleri sonrası her sıcaklık hücresine en az 150 dp kalmalı. */
    @Test fun compactBarLeavesRoomForTemperatures() {
        val s = ClimateBarSize.COMPACT
        val fixed = 16 + (3 * 88 + 2 * 6) + 4 * 21 + s.fanDp + 4 * s.airButtonDp + (2 * 88 + 6)
        assertTrue((1275 - fixed) / 2 >= 150)
    }
}
