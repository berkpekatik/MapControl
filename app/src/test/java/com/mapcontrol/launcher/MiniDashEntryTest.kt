package com.mapcontrol.launcher

import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniDashEntryTest {
    @Test fun iconFollowsLauncherMode() {
        assertEquals(COMPONENT_ENABLED_STATE_ENABLED, MiniDashEntry.wantedState(true))
        assertEquals(COMPONENT_ENABLED_STATE_DISABLED, MiniDashEntry.wantedState(false))
    }

    @Test fun manifestDefaultCountsAsHidden() {
        assertNull(MiniDashEntry.stateToWrite(COMPONENT_ENABLED_STATE_DEFAULT, false))
        assertEquals(COMPONENT_ENABLED_STATE_ENABLED, MiniDashEntry.stateToWrite(COMPONENT_ENABLED_STATE_DEFAULT, true))
    }

    @Test fun togglesOnlyWhenNeeded() {
        assertNull(MiniDashEntry.stateToWrite(COMPONENT_ENABLED_STATE_ENABLED, true))
        assertNull(MiniDashEntry.stateToWrite(COMPONENT_ENABLED_STATE_DISABLED, false))
        assertEquals(COMPONENT_ENABLED_STATE_DISABLED, MiniDashEntry.stateToWrite(COMPONENT_ENABLED_STATE_ENABLED, false))
        assertEquals(COMPONENT_ENABLED_STATE_ENABLED, MiniDashEntry.stateToWrite(COMPONENT_ENABLED_STATE_DISABLED, true))
    }
}
