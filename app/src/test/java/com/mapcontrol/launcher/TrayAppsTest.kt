package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class TrayAppsTest {

    private val self: (String) -> String = { it }

    @Test fun parseDropsEmptySlotsAndDuplicates() {
        assertEquals(listOf("com.a", "com.b"), TrayApps.parse("com.a||com.b|com.a"))
        assertEquals(emptyList<String>(), TrayApps.parse(null))
        assertEquals(emptyList<String>(), TrayApps.parse(""))
    }

    @Test fun encodeRoundTrips() {
        assertEquals(listOf("com.a", "com.b"), TrayApps.parse(TrayApps.encode(listOf("com.a", "com.b"))))
    }

    @Test fun addIgnoresDuplicatesAndRespectsBackupLimit() {
        assertEquals(listOf("com.a"), TrayApps.add(listOf("com.a"), "com.a"))
        val full = (1..TrayApps.MAX_ADDED).map { "com.p$it" }
        assertEquals(full, TrayApps.add(full, "com.new"))
    }

    @Test fun removeDropsOnlyThatPackage() {
        assertEquals(listOf("com.b"), TrayApps.remove(listOf("com.a", "com.b"), "com.a"))
    }

    @Test fun trayShowsUserAppsAndOnlyAddedSystemApps() {
        val tray = TrayApps.visible(listOf("com.user"), listOf("com.maps", "com.oem"), listOf("com.maps"), self)
        assertEquals(listOf("com.user", "com.maps"), tray)
    }

    @Test fun removedFromDeviceAddedAppIsNotShown() {
        assertEquals(emptyList<String>(), TrayApps.visible(emptyList(), emptyList(), listOf("com.gone"), self))
    }

    @Test fun addListHidesAddedAppsAndClosesAtLimit() {
        assertEquals(listOf("com.oem"), TrayApps.addable(listOf("com.maps", "com.oem"), listOf("com.maps"), self))
        val full = (1..TrayApps.MAX_ADDED).map { "com.p$it" }
        assertEquals(emptyList<String>(), TrayApps.addable(listOf("com.oem"), full, self))
    }
}
