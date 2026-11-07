package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AwdMathTest {
    @Test fun engineOffset() {
        assertEquals(0, AwdMath.engineNm(2000))
        assertEquals(287, AwdMath.engineNm(2287))
        assertEquals(-36, AwdMath.engineNm(1964))
    }

    @Test fun engineInvalid() {
        assertNull(AwdMath.engineNm(null))
        assertNull(AwdMath.engineNm(0xFFFF))
        assertNull(AwdMath.engineNm(0))
        assertNull(AwdMath.engineNm(5000))
    }

    @Test fun couplingDecodeAndInvalid() {
        assertEquals(0, AwdMath.couplingNm(0))
        assertEquals(186, AwdMath.couplingNm(186))
        assertNull(AwdMath.couplingNm(1200))
        assertNull(AwdMath.couplingNm(0xFFFF))
        assertNull(AwdMath.couplingNm(-1))
        assertNull(AwdMath.couplingNm(null))
    }

    @Test fun shareBasics() {
        assertEquals(30, AwdMath.rearPercent(200, 60) )
        assertEquals(70, AwdMath.frontPercent(30))
        assertEquals(0, AwdMath.rearPercent(200, 0))
    }

    @Test fun shareNoiseAndZeroDivision() {
        assertEquals(0, AwdMath.rearPercent(0, 0))
        assertEquals(0, AwdMath.rearPercent(0, 10))
        assertEquals(0, AwdMath.rearPercent(-30, 5))
        assertEquals(0, AwdMath.rearPercent(null, 100))
        assertEquals(0, AwdMath.rearPercent(100, null))
    }

    @Test fun shareClamped() {
        assertEquals(100, AwdMath.rearPercent(100, 665))
        assertEquals(100, AwdMath.rearPercent(-20, 100))
        assertEquals(0, AwdMath.frontPercent(150))
        assertEquals(100, AwdMath.frontPercent(-5))
    }

    @Test fun noDataIsNotFrontWheelDrive() {
        assertNull(AwdMath.rearPercentOrNull(null, null))
        assertNull(AwdMath.rearPercentOrNull(200, null))
        assertNull(AwdMath.rearPercentOrNull(null, 60))
        assertEquals(0, AwdMath.rearPercentOrNull(200, 0))
        assertEquals(30, AwdMath.rearPercentOrNull(200, 60))
    }
}
