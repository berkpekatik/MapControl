package com.mapcontrol.launcher

import com.mapcontrol.launcher.LauncherCrashGuard.CRASH_LIMIT
import com.mapcontrol.launcher.LauncherCrashGuard.LONG_CRASH_LIMIT
import com.mapcontrol.launcher.LauncherCrashGuard.LONG_WINDOW_MS
import com.mapcontrol.launcher.LauncherCrashGuard.WINDOW_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherCrashGuardTest {

    private val t0 = 1_000_000_000L

    @Test
    fun twoCrashesSoonAfterStartTrip() {
        val first = LauncherCrashGuard.decide(t0 + 2_000, t0, emptyList(), emptyList())
        assertFalse(first.tripped)
        val second = LauncherCrashGuard.decide(t0 + 5_000, t0 + 3_000, first.fastTimes, first.longTimes)
        assertTrue(second.tripped)
        assertEquals(CRASH_LIMIT, 2)
    }

    @Test
    fun crashesSpacedBeyondHealthyWindowStillTrip() {
        // Her çökmeden önce launcher 10 sn'den uzun sağlıklı çalıştı: başlangıç kaydı silinmiş (0), hızlı sayaç boş.
        var fast = emptyList<Long>()
        var long = emptyList<Long>()
        val gap = 2 * 60_000L
        for (i in 1 until LONG_CRASH_LIMIT) {
            val d = LauncherCrashGuard.decide(t0 + i * gap, 0L, fast, long)
            assertFalse("çökme $i", d.tripped)
            fast = d.fastTimes
            long = d.longTimes
        }
        val last = LauncherCrashGuard.decide(t0 + LONG_CRASH_LIMIT * gap, 0L, fast, long)
        assertTrue(last.tripped)
        assertTrue(last.fastTimes.isEmpty())
        assertTrue(last.longTimes.isEmpty())
    }

    @Test
    fun oldCrashesOutsideLongWindowAreDropped() {
        val old = listOf(t0, t0 + 1_000)
        val now = t0 + LONG_WINDOW_MS + 5_000
        val d = LauncherCrashGuard.decide(now, 0L, emptyList(), old)
        assertFalse(d.tripped)
        assertEquals(listOf(now), d.longTimes)
    }

    @Test
    fun crashFarFromStartDoesNotFeedFastCounter() {
        val start = t0
        val now = t0 + WINDOW_MS + 1
        val d = LauncherCrashGuard.decide(now, start, listOf(now - 500), emptyList())
        assertFalse(d.tripped)
        assertTrue(d.fastTimes.isEmpty())
        assertEquals(listOf(now), d.longTimes)
    }
}
