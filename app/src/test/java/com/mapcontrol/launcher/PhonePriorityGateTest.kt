package com.mapcontrol.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonePriorityGateTest {
    @Test fun runsOnlyWhenLauncherModeAndPrefAreOn() {
        assertTrue(PhonePriorityGate.allowed(launcherMode = true, prefOn = true))
        assertFalse(PhonePriorityGate.allowed(launcherMode = false, prefOn = true))
        assertFalse(PhonePriorityGate.allowed(launcherMode = true, prefOn = false))
        assertFalse(PhonePriorityGate.allowed(launcherMode = false, prefOn = false))
    }
}
