package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SportTorqueTest {
    private fun eq(expected: Float, actual: Float?) = assertEquals(expected, actual!!, 1e-4f)

    @Test fun idleIsEmpty() {
        eq(0f, SportTorque.fraction(2000))
        // Rölanti kıpırtısı (2000±5) halkayı oynatmaz.
        eq(0f, SportTorque.fraction(2005))
        eq(0f, SportTorque.fraction(1995))
    }

    @Test fun halfAndFullScale() {
        eq(0.5f, SportTorque.fraction(2150))
        eq(1f, SportTorque.fraction(2300))
        eq(1f, SportTorque.fraction(2509))
        eq(287f / 300f, SportTorque.fraction(2287))
    }

    @Test fun engineBrakingIsEmpty() {
        eq(0f, SportTorque.fraction(1960))
        eq(0f, SportTorque.fraction(1956))
    }

    @Test fun invalidIsNull() {
        assertNull(SportTorque.fraction(null))
        assertNull(SportTorque.fraction(0xFFFF))
        assertNull(SportTorque.fraction(0))
        assertNull(SportTorque.fraction(999))
        assertNull(SportTorque.fraction(3001))
    }

    @Test fun degreesQuantize() {
        assertEquals(0, SportTorque.degrees(0f))
        assertEquals(135, SportTorque.degrees(0.5f))
        assertEquals(270, SportTorque.degrees(1f))
        assertEquals(270, SportTorque.degrees(1.4f))
        assertEquals(0, SportTorque.degrees(-0.2f))
        // 1 Nm (0,9°) farkı çoğu zaman aynı dereceye düşer: çizim yenilenmez.
        assertEquals(SportTorque.degrees(SportTorque.fractionOfNm(100)), SportTorque.degrees(SportTorque.fractionOfNm(100) + 0.0005f))
    }

    @Test fun demoWaveStaysInRange() {
        for (t in 0L until SportTorque.DEMO_PERIOD_MS step 100) {
            val nm = SportTorque.demoNm(t, idle = false, still = false)
            assertTrue(nm in SportTorque.DEMO_LOW_NM..SportTorque.DEMO_HIGH_NM)
        }
        assertEquals(SportTorque.DEMO_LOW_NM, SportTorque.demoNm(0, idle = false, still = false))
        assertEquals(SportTorque.DEMO_HIGH_NM, SportTorque.demoNm(SportTorque.DEMO_PERIOD_MS / 2, idle = false, still = false))
        assertEquals(0, SportTorque.demoNm(1234, idle = true, still = false))
        assertEquals(SportTorque.DEMO_STILL_NM, SportTorque.demoNm(1234, idle = false, still = true))
    }

    // --- Kritik sönümlü yay ---

    private fun run(from: Float, to: Float, seconds: Float, dt: Float = 1f / 60f, onStep: (Float) -> Unit = {}): Pair<Float, Float> {
        var x = from
        var v = 0f
        var t = 0f
        while (t < seconds) {
            val nx = SportTorque.springStep(x, v, to, dt)
            v = SportTorque.springVelocity(x, v, to, dt)
            x = nx
            t += dt
            onStep(x)
        }
        return x to v
    }

    @Test fun springStepUpNeverOvershootsAndIsMonotonic() {
        var prev = 0f
        run(0f, 1f, 3f) { x ->
            assertTrue(x >= prev - 1e-6f)
            assertTrue(x <= 1f + 1e-6f)
            prev = x
        }
    }

    @Test fun springStepDownNeverUndershootsAndIsMonotonic() {
        var prev = 1f
        run(1f, 0f, 4f) { x ->
            assertTrue(x <= prev + 1e-6f)
            assertTrue(x >= -1e-6f)
            prev = x
        }
    }

    @Test fun springSettlesWithinExpectedTime() {
        val up = run(0f, 1f, 1.0f).first
        assertTrue("up $up", Math.abs(1f - up) < 0.03f)
        val down = run(1f, 0f, 2.0f).first
        assertTrue("down $down", Math.abs(down) < 0.03f)
        val (x, v) = run(0f, 1f, 2.5f)
        assertTrue(SportTorque.isSettled(x, v, 1f))
    }

    @Test fun springKeepsMovingBetweenSecondReadings() {
        // ~1 sn'lik okumalar arasında yay durmaz: 0,5 sn sonra hâlâ gözle görülür hareket var.
        var x = 0f
        var v = 0f
        repeat(30) { x = SportTorque.springStep(x, v, 1f, 1f / 60f).also { nx -> v = SportTorque.springVelocity(x, v, 1f, 1f / 60f); x = nx } }
        assertTrue(1f - x > 0.02f && x > 0.5f)
    }

    @Test fun springDtIsClamped() {
        val a = SportTorque.springStep(0f, 0f, 1f, 5f)
        val b = SportTorque.springStep(0f, 0f, 1f, SportTorque.MAX_DT)
        assertEquals(b, a, 1e-6f)
        assertEquals(0f, SportTorque.springStep(0.3f, 0f, 1f, -1f), 0.3f)
    }

    @Test fun attackIsFasterThanRelease() {
        val up = SportTorque.springStep(0f, 0f, 1f, 0.05f)
        val down = 1f - SportTorque.springStep(1f, 0f, 0f, 0.05f)
        assertTrue(up > down)
    }

    @Test fun springAtRestStaysPut() {
        assertEquals(0.4f, SportTorque.springStep(0.4f, 0f, 0.4f, 0.016f), 1e-6f)
        assertTrue(SportTorque.isSettled(0.4f, 0f, 0.4f))
    }
}
