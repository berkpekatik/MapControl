package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sahne köşe notları: normalde gizli, yalnızca bakım yakın / uyarı / kötü havada görünür. */
class StageCornersTest {

    @Test
    fun serviceNearThresholdIs1000Km() {
        assertEquals(1000, StageCorners.SERVICE_NEAR_KM)
        assertTrue(StageCorners.serviceNear(1000, 200))
        assertTrue(StageCorners.serviceNear(800, null))
        assertFalse(StageCorners.serviceNear(1001, 200))
        // Gün yaklaşması tek başına köşeyi açmaz (karar km); ama gün 0'a indiyse bakım geçmiştir.
        assertFalse(StageCorners.serviceNear(5000, 20))
        assertTrue(StageCorners.serviceNear(5000, 0))
        assertTrue(StageCorners.serviceNear(0, null))
        assertTrue(StageCorners.serviceNear(-50, null))
        assertFalse(StageCorners.serviceNear(null, null))
    }

    @Test
    fun airCornerOnlyWhenBad() {
        assertEquals(76, StageCorners.AIR_BAD_PM25)
        assertFalse(StageCorners.airShown(null))
        assertFalse(StageCorners.airShown(4))
        assertFalse(StageCorners.airShown(75)) // "Orta": not çıkmaz
        assertTrue(StageCorners.airShown(76))
        assertTrue(StageCorners.airShown(120))
    }
}
