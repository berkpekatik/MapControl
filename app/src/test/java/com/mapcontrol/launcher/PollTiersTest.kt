package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class PollTiersTest {
    private val tripPeriods = longArrayOf(1_000L, 5_000L, 60_000L)

    @Test fun firstCallReadsEverything() {
        val t = PollTiers(tripPeriods)
        assertEquals(0b111, t.due(0))
    }

    @Test fun tiersFireAtTheirOwnRate() {
        val t = PollTiers(tripPeriods)
        t.due(0)
        var live = 0; var trip = 0; var slow = 0
        // 1 sn'lik turlar, 120 sn: canlı her tur, yolculuk 5 sn'de bir, yavaş 60 sn'de bir.
        for (sec in 1..120) {
            val m = t.due(sec * 1000L)
            if (m and 1 != 0) live++
            if (m and 2 != 0) trip++
            if (m and 4 != 0) slow++
        }
        assertEquals(120, live)
        assertEquals(24, trip)
        assertEquals(2, slow)
    }

    @Test fun idleLiveTierIsThinned() {
        val t = PollTiers(tripPeriods)
        t.due(0, 3_000L)
        var live = 0
        for (sec in 1..30) if (t.due(sec * 1000L, 3_000L) and 1 != 0) live++
        assertEquals(10, live)
    }

    @Test fun liveIntervalOnlyThinsWhenEngineOffAndStopped() {
        assertEquals(3_000L, TripDecode.liveIntervalMs(0, 0))
        assertEquals(1_000L, TripDecode.liveIntervalMs(0, 5))
        assertEquals(1_000L, TripDecode.liveIntervalMs(800, 0))
        assertEquals(1_000L, TripDecode.liveIntervalMs(null, 0))
        assertEquals(1_000L, TripDecode.liveIntervalMs(0, null))
    }

    @Test fun resetAndMarkAll() {
        val t = PollTiers(tripPeriods)
        t.due(0)
        assertEquals(0, t.due(500))
        t.reset()
        assertEquals(0b111, t.due(600))
        t.markAll(10_000)
        assertEquals(0, t.due(10_500))
        assertEquals(0b001, t.due(11_000))
    }

    @Test fun extraSourceTiers() {
        val t = PollTiers(longArrayOf(4_000L, 20_000L))
        assertEquals(0b11, t.due(0))
        var fast = 0; var slow = 0
        var now = 0L
        // Gerçek tur aralığı en az 4 sn (scheduleWithFixedDelay).
        repeat(30) { now += 4_050; val m = t.due(now); if (m and 1 != 0) fast++; if (m and 2 != 0) slow++ }
        assertEquals(30, fast)
        assertEquals(6, slow)
    }
}
