package com.mapcontrol.nav

import com.mapcontrol.launcher.NavManeuver
import com.mapcontrol.launcher.NavigationSource
import com.mapcontrol.launcher.NavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HudRouteStoreTest {
    private val google = NavigationState(true, NavManeuver.RIGHT,
        sourcePackage = NavigationSource.GOOGLE_MAPS, updatedAtMs = 100)
    private val yandex = NavigationState(true, NavManeuver.LEFT,
        sourcePackage = NavigationSource.YANDEX_MAPS, updatedAtMs = 200)

    @Test fun independentProvidersUseTheLatestGuidance() {
        val store = HudRouteStore()
        store.update("google-route", google)
        store.update("yandex-route", yandex)
        assertEquals(yandex, store.current("auto"))
        assertEquals(google, store.current("google"))
        assertEquals(yandex, store.current("yandex"))
    }

    @Test fun unrelatedNotificationRemovalKeepsGuidance() {
        val store = HudRouteStore()
        store.update("route", google)
        store.update("traffic", null)
        store.remove("traffic")
        assertEquals(google, store.current("auto"))
    }

    @Test fun EndedRouteFallsBackToOtherActiveProvider() {
        val store = HudRouteStore()
        store.update("google-route", google)
        store.update("yandex-route", yandex)
        store.update("yandex-route", NavigationState.NONE)
        assertEquals(google, store.current("auto"))
        assertFalse(store.current("yandex").active)
    }

    @Test fun ProviderSwitchDoesNotReuseWrongApplicationRoute() {
        val store = HudRouteStore()
        store.update("route", google)
        assertFalse(store.current("yandex").active)
        assertEquals(google, store.current("google"))
    }

    @Test fun NavigatorIsAcceptedAsYandex() {
        val store = HudRouteStore()
        val navigator = yandex.copy(sourcePackage = NavigationSource.YANDEX_NAVI)
        store.update("navigator", navigator)
        assertEquals(navigator, store.current("yandex"))
    }

    @Test fun DisconnectClearsAllGuidance() {
        val store = HudRouteStore()
        store.update("google", google)
        store.update("yandex", yandex)
        store.clear()
        assertFalse(store.current("auto").active)
    }

    @Test fun YandexForegroundCannotReplaceGoogleWithUnknownManeuver() {
        val etaOnlyGoogle = google.copy(maneuver = NavManeuver.UNKNOWN, remainingMinutes = 5)
        assertEquals(etaOnlyGoogle, hudRoute(etaOnlyGoogle, yandex, "auto"))
    }

    @Test fun YandexForegroundCompletesMissingNotificationGuidance() {
        assertEquals(yandex, hudRoute(NavigationState.NONE, yandex, "auto"))
        assertEquals(yandex, hudRoute(yandex.copy(maneuver = NavManeuver.UNKNOWN), yandex, "yandex"))
        assertFalse(hudRoute(NavigationState.NONE, yandex, "google").active)
    }
}
