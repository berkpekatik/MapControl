package com.mapcontrol.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HudSupportDeciderTest {
    private val d = HudSupportDecider(giveUpMs = 60_000L)

    @Test fun configBitWins() {
        assertEquals(true, d.decide(true, null, null, true, 0))
        assertEquals(false, HudSupportDecider().decide(false, intArrayOf(0), intArrayOf(1), true, 0))
    }

    @Test fun valuesSeenOnHudEquippedJ7AreEvidence() {
        assertEquals(true, HudSupportDecider().decide(null, intArrayOf(0), intArrayOf(1), true, 0))
        assertEquals(true, HudSupportDecider().decide(null, intArrayOf(3), intArrayOf(0), true, 0))
        assertEquals(true, HudSupportDecider().decide(null, intArrayOf(2), intArrayOf(0), true, 0))
    }

    @Test fun defaultZeroReplyIsNotEvidence() {
        // HUD'suz araçta CarInfo varsayılan [0] dönerse HUD'lu sayılmamalı.
        assertNull(d.decide(null, intArrayOf(0), intArrayOf(0), true, 0))
        assertNull(d.decide(null, intArrayOf(0), null, true, 1_000))
    }

    @Test fun evidenceIsStickyThroughLaterSilence() {
        assertEquals(true, d.decide(null, intArrayOf(0), intArrayOf(1), true, 0))
        assertEquals(true, d.decide(null, null, null, true, 10_000))
        assertEquals(true, d.decide(null, intArrayOf(0), intArrayOf(0), true, 120_000))
        assertEquals(true, d.decide(null, null, null, false, 200_000))
    }

    @Test fun noEvidenceForGiveUpWindowMeansNoHud() {
        assertNull(d.decide(null, null, null, true, 0))
        assertNull(d.decide(null, intArrayOf(0), intArrayOf(0), true, 59_999))
        assertEquals(false, d.decide(null, null, null, true, 60_000))
    }

    @Test fun waitingRestartsWhileServiceIsDisconnected() {
        assertNull(d.decide(null, null, null, true, 0))
        assertNull(d.decide(null, null, null, false, 50_000))
        // Bağlantı gelince süre baştan sayılır (açılışta servis bağlanmadan yanıt yok).
        assertNull(d.decide(null, null, null, true, 70_000))
        assertNull(d.decide(null, null, null, true, 129_999))
        assertEquals(false, d.decide(null, null, null, true, 130_000))
    }

    @Test fun lateEvidenceBeforeGiveUpIsAccepted() {
        assertNull(d.decide(null, null, null, true, 0))
        assertEquals(true, d.decide(null, intArrayOf(0), intArrayOf(1), true, 30_000))
    }
}
