package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDecodeTest {

    @Test fun outsideTemperatureIsHalfDegreesMinus55() {
        // Araçta iki nokta: ham 144 = gösterge 17 °C, ham 140 = 15 °C.
        assertEquals(17.0, TripDecode.outsideC(144)!!, 1e-9)
        assertEquals(15.0, TripDecode.outsideC(140)!!, 1e-9)
        assertEquals(15.5, TripDecode.outsideC(141)!!, 1e-9)
        assertEquals(-40.0, TripDecode.outsideC(30)!!, 1e-9)
        assertNull(TripDecode.outsideC(29))      // −40,5 °C: aralık dışı
        assertNull(TripDecode.outsideC(255))     // 0xFF
        assertNull(TripDecode.outsideC(0xFFFF))
        assertNull(TripDecode.outsideC(null))
    }

    @Test fun instantIsTenths() {
        assertEquals(12.1, TripDecode.instantL100(121)!!, 1e-9)
        assertEquals(13.1, TripDecode.instantL100(131)!!, 1e-9)
        assertEquals(0.1, TripDecode.instantL100(1)!!, 1e-9)
        assertEquals(0.0, TripDecode.instantL100(0)!!, 1e-9)
    }

    @Test fun instantInvalidWhenStopped() {
        assertNull(TripDecode.instantL100(0xFFFF))
        assertNull(TripDecode.instantL100(null))
        assertNull(TripDecode.instantL100(-1))
    }

    @Test fun speedIsKmhDirectly() {
        assertEquals(64, TripDecode.speedKmh(64))
        assertEquals(0, TripDecode.speedKmh(0))
        assertNull(TripDecode.speedKmh(0xFFFF))
    }

    @Test fun speedFallsBackToLegacySignal() {
        assertEquals(65, TripDecode.speed(65, 0))
        assertEquals(40, TripDecode.speed(0, 40))
        assertEquals(40, TripDecode.speed(null, 40))
        assertEquals(0, TripDecode.speed(0, 0))
        assertNull(TripDecode.speed(null, null))
    }

    @Test fun movingBySpeedOrValidInstant() {
        assertEquals(true, TripDecode.moving(64, null))
        assertEquals(true, TripDecode.moving(0, 12.1))
        assertEquals(true, TripDecode.moving(null, 12.1))
        assertEquals(false, TripDecode.moving(0, null))
        assertNull(TripDecode.moving(null, null))
        assertFalse(TripDecode.moving(0, null)!!)
        assertTrue(TripDecode.moving(20, null)!!)
    }

    @Test fun tripSinceReset() {
        assertEquals(53.8, TripDecode.tripKm(538)!!, 1e-9)
        assertEquals(13.8, TripDecode.avgL100(138)!!, 1e-9)
        assertEquals(45, TripDecode.avgSpeedKmh(456))
        assertNull(TripDecode.avgL100(0))
        assertNull(TripDecode.avgSpeedKmh(0))
        assertNull(TripDecode.tripKm(null))
    }

    /** 71 doğrulanmış kaynak (araçta [0,0,43,24] = 11032 km, göstergeyle eşleşti); 150 yalnızca 71 geçersizse kullanılır. */
    @Test fun totalKmPrefersVerifiedGrandTotal() {
        val grand = (43 shl 8) or 24
        assertEquals(11032, TripDecode.totalKm(grand, 110320))
        assertEquals(11032, TripDecode.totalKm(grand, 0))
        assertEquals(11032, TripDecode.totalKm(grand, null))
        assertEquals(12000, TripDecode.totalKm(0, 12000))
        assertEquals(12000, TripDecode.totalKm(-1, 12000))
        assertEquals(12000, TripDecode.totalKm(null, 12000))
        assertNull(TripDecode.totalKm(0, 0))
        assertNull(TripDecode.totalKm(null, null))
    }

    @Test fun totalsRowsDropMissing() {
        assertEquals(listOf(TripTotals.Kind.TIME), TripTotals.rows(null, null, 6).map { it.first })
        assertEquals(listOf(TripTotals.Kind.ODOMETER, TripTotals.Kind.TIME), TripTotals.rows(11032, null, 6).map { it.first })
        assertTrue(TripTotals.rows(null, null, null).isEmpty())
    }
}
