package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarAlertMathTest {

    @Test fun nothingOpenMeansNoAlertLine() {
        assertEquals(emptyList<AlertSeg>(), CarAlertMath.segments(tireAlert = false, windows = emptyList(), fuelCapOpen = false))
    }

    @Test fun segmentsKeepTyreWindowsFuelCapOrder() {
        val windows = CarAlertMath.windows(listOf(BodyPart.WINDOW_LR))
        assertEquals(
            listOf(AlertSeg.TIRE, AlertSeg.WINDOWS, AlertSeg.FUEL_CAP),
            CarAlertMath.segments(tireAlert = true, windows = windows, fuelCapOpen = true),
        )
        assertEquals(listOf(AlertSeg.FUEL_CAP), CarAlertMath.segments(false, emptyList(), true))
    }

    @Test fun doorsTrunkHoodAndSunroofStayOutOfTheLine() {
        val open = listOf(BodyPart.DOOR_LF, BodyPart.TRUNK, BodyPart.HOOD, BodyPart.WINDOW_RF, BodyPart.FUEL_CAP)
        assertEquals(listOf(BodyPart.WINDOW_RF), CarAlertMath.windows(open))
        assertTrue(CarAlertMath.segments(false, CarAlertMath.windows(open), false) == listOf(AlertSeg.WINDOWS))
    }
}
