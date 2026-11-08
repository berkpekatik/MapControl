package com.mapcontrol.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimatePanelGestureTest {
    @Test fun upwardDragNeedsSlopAndVerticalDominance() {
        assertTrue(ClimatePanelGesture.isUpwardDrag(30f, 10f, 20f))
        assertFalse(ClimatePanelGesture.isUpwardDrag(10f, 0f, 20f))
        assertFalse(ClimatePanelGesture.isUpwardDrag(30f, 40f, 20f))
        assertFalse(ClimatePanelGesture.isUpwardDrag(-50f, 0f, 20f))
    }

    @Test fun opensOnlyAboveThresholdAndMostlyVertical() {
        assertTrue(ClimatePanelGesture.shouldOpen(60f, 10f, 1f))
        assertTrue(ClimatePanelGesture.shouldOpen(120f, 0f, 2f))
        assertFalse(ClimatePanelGesture.shouldOpen(59f, 0f, 1f))
        assertFalse(ClimatePanelGesture.shouldOpen(100f, 100f, 1f))
        assertFalse(ClimatePanelGesture.shouldOpen(-100f, 0f, 1f))
    }
}
