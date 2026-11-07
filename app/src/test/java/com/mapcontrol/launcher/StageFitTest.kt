package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** Sahne aracının yerleşimi (§10/§11): tam ortada, omuzlarla çakışmaz (köşe payı dışında), şerit payını aşmaz. */
class StageFitTest {

    private val aspect = CarAnimSpec.BASE_H / CarAnimSpec.BASE_W
    private val gap = 10f
    private val inset = 0.02f

    /** 960 genişlikte sahne: sol omuzda menzil satırı, sağ omuzda kilit hapı (emülatörde ölçülen ölçüler). */
    private fun shoulders() = listOf(
        StageFit.Box(24f, 22f, 230f, 78f),
        StageFit.Box(756f, 12f, 936f, 84f),
    )

    /** Sahne yüksekliği [h]; alt pay şerit (64) + kenarlar. */
    private fun fit(h: Float) = StageFit.centered(480f, 6f, h - 82f, 640f, 420f, aspect, gap, shoulders(), inset)

    private fun overlapsInner(c: StageFit.Box, o: StageFit.Box): Boolean {
        val i = c.width * inset
        return c.left + i < o.right && c.right - i > o.left && c.top < o.bottom && c.bottom > o.top
    }

    @Test
    fun alwaysExactlyCentered() {
        listOf(396f, 423f, 508f, 600f).forEach { h -> assertEquals(480f, (fit(h).left + fit(h).right) / 2f, 0.01f) }
    }

    @Test
    fun neverOverlapsShouldersOrStripReserve() {
        listOf(396f, 423f, 508f, 600f).forEach { h ->
            val c = fit(h)
            shoulders().forEach { o -> assertTrue("$h $c $o", !overlapsInner(c, o)) }
            assertTrue(c.bottom <= h - 82f + 0.01f)
            assertEquals(aspect, c.height / c.width, 0.001f)
        }
    }

    @Test
    fun ourBarCarStaysLarge() {
        // Bizim alt çubuk (en alçak sahne): araç omuzların arasında, en az 500 (eski 510 boyuna yakın).
        assertTrue("${fit(396f).width}", fit(396f).width >= 500f)
        // Daha yüksek sahnede küçülmez.
        assertTrue(fit(508f).width >= fit(396f).width)
    }

    @Test
    fun noObstaclesMeansMaxCarCenteredVertically() {
        val c = StageFit.centered(480f, 0f, 400f, 612f, 510f, aspect, gap, emptyList())
        assertEquals(612f, c.width, 0.01f)
        assertEquals(200f, (c.top + c.bottom) / 2f, 0.01f)
    }

    @Test
    fun impossibleSpaceFallsBackToMinimumSize() {
        val c = StageFit.centered(480f, 0f, 100f, 612f, 510f, aspect, gap, emptyList())
        assertEquals(510f, c.width, 0.01f)
        assertEquals(480f, (c.left + c.right) / 2f, 0.01f)
    }

    // --- Uzun sahne (Tiggo 8) ---

    @Test
    fun onlyTheTallStageCountsAsTall() {
        assertFalse(StageFit.isTall(960, 394)) // Tiggo 7 bandı
        assertTrue(StageFit.isTall(879, 658)) // Tiggo 8 bandı
        assertFalse(StageFit.isTall(0, 500))
    }

    @Test
    fun tallCarGrowsButKeepsTheOpenTrunkInsideTheStage() {
        val frac = 79f / 766f
        val w = StageFit.tallMaxWidth(879f, 586f, 16f, frac)
        assertTrue(w > 586f * 1.3f)
        assertTrue(w <= 586f * StageFit.TALL_GROWTH)
        assertTrue(w * (1 + frac) <= 879f - 32f + 0.01f)
        // Geniş sahnede büyüme sınırı 1,4 kat.
        assertEquals(586f * StageFit.TALL_GROWTH, StageFit.tallMaxWidth(3000f, 586f, 16f, frac), 0.01f)
    }

    @Test
    fun tallTrunkShiftMovesJustEnoughAndNeverPastTheRightEdge() {
        val frac = 79f / 766f
        val carW = StageFit.tallMaxWidth(879f, 586f, 16f, frac)
        val left = (879f - carW) / 2f
        val car = StageFit.Box(left, 100f, left + carW, 100f + carW * 0.46f)
        val shift = StageFit.tallTrunkShift(879f, car, 16f, frac)
        assertEquals(16f, car.left + shift - carW * frac, 0.5f) // kapak ucu sol payda
        assertTrue(car.right + shift <= 879f - 16f + 0.5f)
        // Kapak zaten içerdeyse kayma yok.
        assertEquals(0f, StageFit.tallTrunkShift(879f, StageFit.Box(300f, 0f, 500f, 90f), 16f, frac), 0f)
    }
}
