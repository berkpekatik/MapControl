package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmbientModeColorTest {
    private val cover = 0xFF123456.toInt()

    @Test
    fun coverColorWinsWhetherPlayingOrNot() {
        assertEquals(cover, AmbientModeColor.decide(cover, true, DriveModes.SPORT))
        assertEquals(cover, AmbientModeColor.decide(cover, false, DriveModes.SPORT))
    }

    @Test
    fun noCoverAndPlayingUsesModeColor() {
        assertEquals(AmbientModeColor.ECO_COLOR, AmbientModeColor.decide(null, true, DriveModes.ECO))
        assertEquals(AmbientModeColor.SPORT_COLOR, AmbientModeColor.decide(null, true, DriveModes.SPORT))
        assertEquals(AmbientModeColor.SAND_COLOR, AmbientModeColor.decide(null, true, DriveModes.SAND))
    }

    @Test
    fun noCoverAndNotPlayingIsOff() {
        assertNull(AmbientModeColor.decide(null, false, DriveModes.ECO))
        assertNull(AmbientModeColor.decide(null, false, 99))
    }

    @Test
    fun unknownModeFallsBackToNormalBlue() {
        assertEquals(AmbientModeColor.NORMAL_COLOR, AmbientModeColor.forMode(6))
        assertEquals(AmbientModeColor.NORMAL_COLOR, AmbientModeColor.forMode(-1))
        assertEquals(AmbientModeColor.NORMAL_COLOR, AmbientModeColor.decide(null, true, 6))
    }

    @Test
    fun everyKnownModeHasItsOwnOpaqueColor() {
        val colors = DriveMode.values().map { AmbientModeColor.forMode(it.value) }
        assertEquals(DriveMode.values().size, colors.toSet().size)
        colors.forEach { assertEquals(0xFF, it ushr 24) }
        assertNotEquals(AmbientModeColor.SNOW_COLOR, 0xFFFFFFFF.toInt())
    }
}
