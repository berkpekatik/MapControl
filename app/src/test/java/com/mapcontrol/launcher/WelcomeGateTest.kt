package com.mapcontrol.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeGateTest {

    @Test fun playsOncePerIgnitionCycle() {
        val g = WelcomeGate()
        assertTrue(g.update(powerOn = true, ready = true, sportActive = false))
        assertFalse(g.update(powerOn = true, ready = true, sportActive = false))
        // Kontak kapanıp açılınca yeni döngü.
        assertFalse(g.update(powerOn = false, ready = true, sportActive = false))
        assertTrue(g.update(powerOn = true, ready = true, sportActive = false))
    }

    @Test fun unknownPowerNeverPlays() {
        val g = WelcomeGate()
        repeat(3) { assertFalse(g.update(powerOn = null, ready = true, sportActive = false)) }
        assertTrue(g.update(powerOn = true, ready = true, sportActive = false))
    }

    @Test fun waitsUntilReadyThenPlaysOnFirstResume() {
        val g = WelcomeGate()
        assertFalse(g.update(powerOn = true, ready = false, sportActive = false))
        assertFalse(g.played)
        assertTrue(g.update(powerOn = true, ready = true, sportActive = false))
    }

    @Test fun sportSkipsThisCycle() {
        val g = WelcomeGate()
        assertFalse(g.update(powerOn = true, ready = true, sportActive = true))
        assertTrue(g.played)
        assertFalse(g.update(powerOn = true, ready = true, sportActive = false))
    }

    @Test fun replayAllowsDemoAgain() {
        val g = WelcomeGate()
        assertTrue(g.update(true, true, false))
        g.replay()
        assertTrue(g.update(true, true, false))
    }
}
