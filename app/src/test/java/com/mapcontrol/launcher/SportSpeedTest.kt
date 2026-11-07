package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hız önce spor kadranının saf mantığı (6 Kasım kararı). */
class SportSpeedTest {

    @Test fun speedDialSweepsSameArcAsBefore() {
        assertEquals(135f, SportMath.speedDeg(0f), 0.01f)
        assertEquals(405f, SportMath.speedDeg(SportMath.SPEED_MAX.toFloat()), 0.01f)
        assertEquals(270f, SportMath.speedDeg(110f), 0.01f)
        // Aralık dışı kırpılır: ibre alt boşluğa (dijital hız, vites, devir) hiç girmez.
        assertEquals(405f, SportMath.speedDeg(300f), 0.01f)
        assertEquals(135f, SportMath.speedDeg(-3f), 0.01f)
        assertEquals(0, SportMath.SPEED_MAX % SportMath.SPEED_MAJOR)
    }

    @Test fun speedDeadbandKeepsNeedleStillOnTinyChanges() {
        val band = SportStageView.NEEDLE_DEADBAND_KMH
        assertFalse(SportMath.needleShouldMove(false, 50f, 50f, 50.3f, band))
        assertTrue(SportMath.needleShouldMove(false, 50f, 50f, 50.6f, band))
        assertFalse(SportMath.needleShouldMove(true, 60f, 55f, 60.2f, band))
        assertTrue(SportMath.needleShouldMove(true, 60f, 55f, 61f, band))
    }

    @Test fun gStepIgnoresJitterAndReturnsToCenter() {
        assertNull(SportMath.gStep(10f, 10f, 3f))
        assertNull(SportMath.gStep(10f, 12f, 3f))
        assertEquals(14f, SportMath.gStep(10f, 14f, 3f)!!, 0f)
        assertEquals(0f, SportMath.gStep(1f, 0f, 3f)!!, 0f)
        assertEquals(-4f, SportMath.gStep(0f, -4f, 3f)!!, 0f)
    }

    @Test fun rpmReadoutHidesWhenStale() {
        val r = RpmReadout(3_000)
        assertNull(r.next(0, null))
        assertEquals(800, r.next(0, 752))
        assertEquals(800, r.next(2_900, 752))
        // 3 sn'den uzun değişmedi: gizli.
        assertNull(r.next(3_100, 752))
        // Değişince yeniden görünür, 100'e yuvarlanır.
        assertEquals(2_400, r.next(3_200, 2_380))
        assertEquals(2_400, r.next(3_300, 2_420))
        assertNull(r.next(3_400, -1))
        // Bilinmiyorsa sıfırlanır: aynı değer gelince taze sayılır.
        assertEquals(2_400, r.next(3_500, 2_420))
    }

    @Test fun calibrationLearnsGaugeRatioOnlyWhenSteady() {
        val c = SpeedCalibration()
        assertEquals(1f, c.ratio, 0f)
        // Düşük hızda öğrenmez.
        repeat(5) { c.learn(30f, 32) }
        assertEquals(1f, c.ratio, 0f)
        // Hızlanırken (sabit değil) öğrenmez.
        c.learn(60f, 63)
        c.learn(70f, 74)
        assertEquals(1f, c.ratio, 0f)
        // Sabit 100 km/sa, gösterge 104: oran ~1,04'e yaklaşır.
        repeat(60) { c.learn(100f, 104) }
        assertEquals(1.04f, c.ratio, 0.005f)
        assertEquals(104f, c.display(100f), 0.5f)
        // Saçma oran kırpılır.
        val d = SpeedCalibration()
        repeat(80) { d.learn(100f, 150) }
        assertTrue(d.ratio <= SpeedCalibration.MAX_RATIO + 1e-4f)
        // Gösterge bilinmiyorsa değişmez.
        val e = SpeedCalibration()
        repeat(5) { e.learn(100f, null) }
        assertEquals(1f, e.ratio, 0f)
    }

    @Test fun postGateWakesMainOnlyOnRealChange() {
        val g = SpeedPostGate(0.5f)
        assertTrue(g.shouldPost(0f))
        // Dururken gelen 0 olayları ana iş parçacığını uyandırmaz.
        repeat(20) { assertFalse(g.shouldPost(0f)) }
        assertFalse(g.shouldPost(0.3f))
        assertTrue(g.shouldPost(0.6f))
        assertFalse(g.shouldPost(0.9f))
        assertTrue(g.shouldPost(1.2f))
        // Duruşa iniş her zaman iletilir.
        assertTrue(g.shouldPost(0f))
        g.reset()
        assertTrue(g.shouldPost(0f))
    }

    @Test fun tickLandsOnSecondBoundaryWhenClockShown() {
        assertEquals(500L, SportMath.tickDelay(10_000, 500))
        assertEquals(505L, SportMath.tickDelay(10_500, 600))
        assertEquals(105L, SportMath.tickDelay(10_900, 500))
        assertEquals(6L, SportMath.tickDelay(10_999, 500))
    }

    @Test fun gSensorIsSlowerThanUiRate() {
        // SENSOR_DELAY_UI ≈ 66 ms; biz 100 ms (10 Hz).
        assertTrue(GFilter.SAMPLING_US >= 66_666)
    }

    @Test fun summaryLineHasOnlyCounts() {
        val line = SportSpeedLog.summaryLine(10_000, "car", 100, 12, 9, 87, true, 420)
        assertEquals("özet 10000ms kaynak=car olay=100 (10.0/sn) anaİş=12 ibre=9 hız=87 devir=görünür cpu=4.2%", line)
        assertTrue(SportSpeedLog.summaryLine(5_000, "vdbus/izin-yok", 0, 0, 0, -1, false, 0).contains("hız=- devir=gizli cpu=0.0%"))
    }
}
