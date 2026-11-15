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

    @Test fun portraitFromNaturalSize() {
        // Jaecoo dikey ekran (hedef 1440×1920).
        assertEquals(VehicleProfile.PORTRAIT, VehicleProfile.detectNatural(1440, 1920))
        assertEquals(VehicleProfile.PORTRAIT, VehicleProfile.detectNatural(1080, 1920))
        // Dikey bildiren büyük Tiggo 8 ünitesi Tiggo 8 kalır; yatay ekranlar eskisi gibi.
        assertEquals(VehicleProfile.TIGGO8, VehicleProfile.detectNatural(1440, 2560))
        assertEquals(VehicleProfile.TIGGO7, VehicleProfile.detectNatural(1920, 720))
        assertEquals(VehicleProfile.TIGGO8, VehicleProfile.detectNatural(2560, 1440))
        assertEquals(VehicleProfile.TIGGO7, VehicleProfile.detectNatural(0, 0))
    }

    @Test fun portraitOverrideAndFlags() {
        assertEquals(VehicleProfile.PORTRAIT, VehicleProfile.choose(3, VehicleProfile.TIGGO7))
        assertEquals(VehicleProfile.PORTRAIT, VehicleProfile.parse("portrait"))
        assertTrue(VehicleProfile.PORTRAIT.quad)
        assertTrue(VehicleProfile.PORTRAIT.portrait)
        assertTrue(VehicleProfile.TIGGO8.quad)
        assertFalse(VehicleProfile.TIGGO8.portrait)
        assertFalse(VehicleProfile.TIGGO7.quad)
        assertEquals(VehicleProfile.PORTRAIT_BAR_DP, VehicleProfile.PORTRAIT.barHeightDp(1700f))
    }

    @Test fun barHeight() {
        assertEquals(104, VehicleProfile.TIGGO7.barHeightDp(960f))
        assertEquals(85, VehicleProfile.TIGGO8.barHeightDp(960f))
    }
}
