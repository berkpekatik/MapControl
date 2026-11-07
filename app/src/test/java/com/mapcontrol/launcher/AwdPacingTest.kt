package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AwdPacingTest {
    @Test fun pollIsFastRightAfterChangeThenSlows() {
        assertEquals(AwdPacing.FAST_POLL_MS, AwdPacing.pollDelayMs(0))
        assertEquals(AwdPacing.FAST_POLL_MS, AwdPacing.pollDelayMs(AwdPacing.FAST_WINDOW_POLLS - 1))
        assertEquals(AwdPacing.SLOW_POLL_MS, AwdPacing.pollDelayMs(AwdPacing.FAST_WINDOW_POLLS))
        assertEquals(1000L, AwdPacing.pollDelayMs(10_000))
    }

    @Test fun frameRateAtMostTenFps() {
        assertTrue(AwdPacing.FRAME_MS >= 100L)
    }

    @Test fun flowOnlyShortlyAfterChange() {
        val t0 = 10_000L
        assertTrue(AwdPacing.animateFlow(true, false, 0.3f, t0, t0))
        assertTrue(AwdPacing.animateFlow(true, false, 0.3f, t0 + AwdPacing.FLOW_HOLD_MS, t0))
        assertFalse(AwdPacing.animateFlow(true, false, 0.3f, t0 + AwdPacing.FLOW_HOLD_MS + 1, t0))
    }

    @Test fun noFlowWhenHiddenCompactOrFrontOnly() {
        val t0 = 10_000L
        assertFalse(AwdPacing.animateFlow(false, false, 0.3f, t0, t0))
        assertFalse(AwdPacing.animateFlow(true, true, 0.3f, t0, t0))
        assertFalse(AwdPacing.animateFlow(true, false, 0f, t0, t0))
    }

    @Test fun neverChangedMeansNoFlow() {
        assertFalse(AwdPacing.animateFlow(true, false, 0.3f, 10_000L, Long.MIN_VALUE / 2))
    }

    @Test fun disconnectedServiceDropsToSlowPoll() {
        // Servis bağlı değilken okuma null: sayaç artmalı, sonsuza dek 250 ms'de kalınmamalı.
        var n = 0
        repeat(AwdPacing.FAST_WINDOW_POLLS) {
            val s = AwdPacing.step(notBoundYet = true, read = false, changed = false, unchangedPolls = n)
            assertFalse(s.bind)
            n = s.unchangedPolls
        }
        assertEquals(AwdPacing.SLOW_POLL_MS, AwdPacing.pollDelayMs(n))
    }

    @Test fun firstReadOfSessionAlwaysBindsEvenIfSameValue() {
        // Gizle→göster: değer önceki oturumla aynı olsa da yeni oturumda bir kez yansımalı.
        assertTrue(AwdPacing.step(notBoundYet = true, read = true, changed = false, unchangedPolls = 7).bind)
        assertEquals(0, AwdPacing.step(true, true, false, 7).unchangedPolls)
    }

    @Test fun unchangedReadDoesNotRebind() {
        val s = AwdPacing.step(notBoundYet = false, read = true, changed = false, unchangedPolls = 2)
        assertFalse(s.bind)
        assertEquals(3, s.unchangedPolls)
        assertTrue(AwdPacing.step(false, true, true, 9).bind)
    }
}
