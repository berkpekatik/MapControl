package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoNightPolicyTest {
    @Test fun switchesOnlyAfterStable() {
        val p = AutoNightPolicy(10_000)
        p.reset(false)
        assertNull(p.update(0, true, true))
        assertNull(p.update(9_999, true, true))
        assertEquals(true, p.update(10_000, true, true))
        assertNull(p.update(20_000, true, true))
    }

    @Test fun flickerResetsTimer() {
        val p = AutoNightPolicy(10_000)
        p.reset(false)
        p.update(0, true, true)
        assertNull(p.update(5_000, false, true))
        assertNull(p.update(12_000, true, true))
        assertEquals(true, p.update(22_000, true, true))
    }

    @Test fun blockedDefersUntilAllowed() {
        val p = AutoNightPolicy(10_000)
        p.reset(true)
        p.update(0, false, false)
        assertNull(p.update(30_000, false, false))
        assertEquals(false, p.update(31_000, false, true))
    }
}
