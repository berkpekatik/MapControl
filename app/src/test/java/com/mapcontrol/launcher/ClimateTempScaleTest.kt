package com.mapcontrol.launcher

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Değerler OEM klima uygulamasından (SVHvac): tam derece ölçeğinde ham 1..15 = 16..30 °C, 16 = HI. */
class ClimateTempScaleTest {
    @Before fun whole() { ClimateMath.scale = ClimateTempScale.WHOLE }
    @After fun reset() { ClimateMath.scale = ClimateTempScale.HALF }

    @Test fun topRawIsHiNotTwentyThreeAndAHalf() {
        assertTrue(ClimateMath.isHi(OurBottomBar.decodeTemp(16)!!))
        assertEquals(16, ClimateMath.encodeTemp(ClimateMath.HI_TEMP))
    }

    @Test fun middleValuesAreWholeDegrees() {
        assertTrue(ClimateMath.isLo(OurBottomBar.decodeTemp(0)!!))
        assertEquals(16.0, OurBottomBar.decodeTemp(1)!!, 0.0)
        assertEquals(24.0, OurBottomBar.decodeTemp(9)!!, 0.0)
        assertEquals(30.0, OurBottomBar.decodeTemp(15)!!, 0.0)
    }

    @Test fun roundTripsEveryRaw() {
        for (raw in 0..16) assertEquals(raw, ClimateMath.encodeTemp(OurBottomBar.decodeTemp(raw)!!))
    }

    @Test fun aboveTopIsInvalid() {
        assertNull(OurBottomBar.decodeTemp(17))
    }

    @Test fun oneStepIsOneDegreeAndEndsAreLoHi() {
        assertEquals(25.0, ClimateMath.targetTemp(24.0, 1), 0.0)
        assertEquals(16.0, ClimateMath.targetTemp(ClimateMath.LO_TEMP, 1), 0.0)
        assertEquals(ClimateMath.HI_TEMP, ClimateMath.targetTemp(30.0, 1), 0.0)
        assertEquals(ClimateMath.HI_TEMP, ClimateMath.targetTemp(ClimateMath.HI_TEMP, 5), 0.0)
        assertEquals(ClimateMath.LO_TEMP, ClimateMath.targetTemp(16.0, -3), 0.0)
    }

    @Test fun neverWritesAboveCarTop() {
        assertEquals(15, ClimateMath.encodeTemp(30.2))
        assertEquals(1, ClimateMath.encodeTemp(15.7))
    }
}
