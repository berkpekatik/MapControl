package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** VDBus VEHICLE_HAL 519/773 okuması, devir kaynağı seçimi ve spor demosunun tutarlılığı (saf mantık). */
class SportVhalTest {

    @Test fun vhalIdsAreLow16BitsOfVhalProperties() {
        // PERF_VEHICLE_SPEED 0x11600207 (OEM 519 ile aynı), ENGINE_RPM 0x11600305 (VehiclePropertyIds 291504901).
        assertEquals(CarSpeedReader.PERF_VEHICLE_SPEED and 0xFFFF, SportVhalMath.SPEED_ID)
        assertEquals(291_504_901 and 0xFFFF, SportVhalMath.RPM_ID)
    }

    @Test fun speedPayloadIsMetresPerSecond() {
        assertEquals(36f, SportVhalMath.speedKmh(doubleArrayOf(10.0))!!, 0.001f)
        assertEquals(0f, SportVhalMath.speedKmh(doubleArrayOf(0.0))!!, 0f)
        // Geri giderken eksi gelebilir: kadran büyüklüğü gösterir.
        assertEquals(7.2f, SportVhalMath.speedKmh(doubleArrayOf(-2.0))!!, 0.001f)
        assertNull(SportVhalMath.speedKmh(null))
        assertNull(SportVhalMath.speedKmh(doubleArrayOf()))
        assertNull(SportVhalMath.speedKmh(doubleArrayOf(Double.NaN)))
        assertNull(SportVhalMath.speedKmh(doubleArrayOf(200.0)))
    }

    @Test fun rpmPayloadIsBoundedAndRounded() {
        assertEquals(752, SportVhalMath.rpm(doubleArrayOf(751.6)))
        assertEquals(0, SportVhalMath.rpm(doubleArrayOf(0.0)))
        assertNull(SportVhalMath.rpm(doubleArrayOf(-1.0)))
        assertNull(SportVhalMath.rpm(doubleArrayOf(12_000.0)))
        assertNull(SportVhalMath.rpm(doubleArrayOf(Double.POSITIVE_INFINITY)))
        assertNull(SportVhalMath.rpm(null))
    }

    @Test fun vhalRpmMustAgreeWithReadOnly62() {
        assertTrue(SportVhalMath.agrees(760, 750))
        assertTrue(SportVhalMath.agrees(1_000, 750))
        assertTrue(SportVhalMath.agrees(3_900, 3_400))
        // Ölçek farkı (ör. onda bir ya da rpm/4) güvenilmez.
        assertFalse(SportVhalMath.agrees(75, 750))
        assertFalse(SportVhalMath.agrees(3_000, 750))
    }

    @Test fun rpmSourcePrefersFreshTrustedVhal() {
        val now = 10_000L
        val fresh = SportVhalMath.chooseRpm(now, 2_480, now - 100, trusted = true, readOnly = 2_000)
        assertEquals(2_480, fresh.rpm)
        assertEquals(SportVhalMath.RpmSource.VHAL, fresh.source)
        assertEquals(now - 100, fresh.arrivedAt)
        // Doğrulanmadıysa 62.
        val untrusted = SportVhalMath.chooseRpm(now, 2_480, now - 100, trusted = false, readOnly = 2_000)
        assertEquals(2_000, untrusted.rpm)
        assertEquals(SportVhalMath.RpmSource.READ_ONLY, untrusted.source)
        assertNull(untrusted.arrivedAt)
        // Bayatsa 62.
        val stale = SportVhalMath.chooseRpm(now, 2_480, now - SportVhalMath.FRESH_MS, trusted = true, readOnly = 2_000)
        assertEquals(SportVhalMath.RpmSource.READ_ONLY, stale.source)
        // Hiçbiri yok.
        val none = SportVhalMath.chooseRpm(now, null, 0L, trusted = true, readOnly = null)
        assertNull(none.rpm)
        assertEquals(SportVhalMath.RpmSource.NONE, none.source)
    }

    @Test fun streamingRpmStaysVisibleWhileArriving() {
        val r = RpmReadout(3_000)
        assertEquals(2_400, r.next(0, 2_400, arrivedAt = 0))
        // Aynı değer akmaya devam ediyor (sabit seyir): gizlenmez.
        assertEquals(2_400, r.next(5_000, 2_400, arrivedAt = 4_900))
        // Akış durdu: 3 sn sonra gizli.
        assertNull(r.next(8_000, 2_400, arrivedAt = 4_900))
        // Yoklanan kaynak (varış yok) eskisi gibi değişime bakar.
        val p = RpmReadout(3_000)
        assertEquals(800, p.next(0, 752))
        assertNull(p.next(3_100, 752))
    }

    @Test fun sportDemoShowsOneConsistentMoment() {
        // D4'te 3400 dev/dk ile demo hızı uyuşur (4. vites ~25–35 km/sa / 1000 dev/dk).
        val perThousand = SportDemo.kmh(idle = false) * 1000.0 / SportDemo.RPM
        assertTrue(perThousand in 25.0..35.0)
        assertEquals("D4", SportDemo.gearText(idle = false))
        // Tepe işareti gösterilen hızın altında kalmaz.
        assertTrue(SportDemo.MAX_KMH >= SportDemo.kmh(idle = false))
        assertTrue(SportDemo.kmh(idle = false) <= SportMath.SPEED_MAX)
        // Rölanti taklidi: P'de ve duruyor.
        assertEquals(0, SportDemo.kmh(idle = true))
        assertEquals("P", SportDemo.gearText(idle = true))
    }
}
