package com.mapcontrol.nav

import com.mapcontrol.launcher.NavDistance
import com.mapcontrol.launcher.NavManeuver
import com.mapcontrol.launcher.NavigationSource
import com.mapcontrol.launcher.NavigationState
import org.junit.Assert.*
import org.junit.Test

class HudLastKnownTest {
    private var now = 0L
    private val memory = HudLastKnown({ now }, holdMs = 10_000L)

    private fun full(pkg: String) = NavigationState(
        active = true, maneuver = NavManeuver.RIGHT,
        distance = NavDistance("300", "m", 300), street = "Atatürk Cd.",
        arrivalTime = "14:52", remainingDistance = NavDistance("18", "km", 18_000),
        remainingMinutes = 25, sourcePackage = pkg)

    private fun bothSources(check: (String) -> Unit) {
        check(NavigationSource.GOOGLE_MAPS)
        check(NavigationSource.YANDEX_MAPS)
    }

    @Test fun missingArrowAndDistanceKeepLastValuesForGoogleAndYandex() = bothSources { pkg ->
        val m = HudLastKnown({ now })
        m.merge(full(pkg))
        now += 1_000
        val partial = full(pkg).copy(maneuver = NavManeuver.UNKNOWN, distance = null, street = null)
        val merged = m.merge(partial)
        assertEquals(NavManeuver.RIGHT, merged.maneuver)
        assertEquals("300", merged.distance?.value)
        assertEquals("Atatürk Cd.", merged.street)
    }

    @Test fun realChangeReplacesOldValue() {
        memory.merge(full(NavigationSource.GOOGLE_MAPS))
        val next = full(NavigationSource.GOOGLE_MAPS).copy(
            maneuver = NavManeuver.LEFT, distance = NavDistance("100", "m", 100), street = "İnönü Cd.")
        val merged = memory.merge(next)
        assertEquals(NavManeuver.LEFT, merged.maneuver)
        assertEquals("100", merged.distance?.value)
        assertEquals("İnönü Cd.", merged.street)
    }

    @Test fun heldValuesExpireButArrowIsKept() {
        memory.merge(full(NavigationSource.GOOGLE_MAPS))
        now += 10_001
        val merged = memory.merge(full(NavigationSource.GOOGLE_MAPS).copy(maneuver = NavManeuver.UNKNOWN, distance = null))
        // JCarTools gibi: ok gelmezse önceki ok kalır (bilinmeyen ok HUD'u kapatır).
        assertEquals(NavManeuver.RIGHT, merged.maneuver)
        assertNull(merged.distance)
    }

    @Test fun expiryIsNotExtendedByRepeatedMissingFields() {
        memory.merge(full(NavigationSource.GOOGLE_MAPS))
        val partial = full(NavigationSource.GOOGLE_MAPS).copy(maneuver = NavManeuver.UNKNOWN, distance = null)
        now += 6_000; assertNotNull(memory.merge(partial).distance)
        now += 6_000; assertNull(memory.merge(partial).distance)
        assertEquals(NavManeuver.RIGHT, memory.merge(partial).maneuver)
    }

    @Test fun switchingSourceDoesNotLeakValues() {
        memory.merge(full(NavigationSource.GOOGLE_MAPS))
        val yandex = full(NavigationSource.YANDEX_MAPS).copy(maneuver = NavManeuver.UNKNOWN, distance = null)
        val merged = memory.merge(yandex)
        assertEquals(NavManeuver.UNKNOWN, merged.maneuver)
        assertNull(merged.distance)
    }

    @Test fun inactiveStateIsReturnedUnchangedAndMemoryResetClears() {
        memory.merge(full(NavigationSource.GOOGLE_MAPS))
        assertSame(NavigationState.NONE, memory.merge(NavigationState.NONE))
        memory.reset()
        val merged = memory.merge(full(NavigationSource.GOOGLE_MAPS).copy(maneuver = NavManeuver.UNKNOWN))
        assertEquals(NavManeuver.UNKNOWN, merged.maneuver)
    }

    @Test fun brieflyMissingNotificationStillProducesFrame() {
        // Dönüşte bildirim yalnız yol adını verirse HUD kare almaya devam eder.
        val pkg = NavigationSource.GOOGLE_MAPS
        memory.merge(full(pkg))
        val onlyStreet = NavigationState(active = true, street = "X Sk.", sourcePackage = pkg)
        assertNotNull(NativeHudProtocol.encode(memory.merge(onlyStreet)))
    }
}
