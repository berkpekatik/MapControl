package com.mapcontrol.launcher

import com.mapcontrol.launcher.NavManeuver
import com.mapcontrol.nav.hudRoute
import com.mapcontrol.launcher.NavigationSource
import com.mapcontrol.launcher.NavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NavigationRoutesTest {
    private fun current(store: NavigationRoutes, provider: String): NavigationState = latestNavigation(store.byPackage(), when (provider) {
        "google" -> setOf(NavigationSource.GOOGLE_MAPS)
        "yandex" -> setOf(NavigationSource.YANDEX_MAPS, NavigationSource.YANDEX_NAVI)
        else -> setOf(NavigationSource.GOOGLE_MAPS, NavigationSource.YANDEX_MAPS, NavigationSource.YANDEX_NAVI)
    })

    private val google = NavigationState(true, NavManeuver.RIGHT,
        sourcePackage = NavigationSource.GOOGLE_MAPS, updatedAtMs = 100)
    private val yandex = NavigationState(true, NavManeuver.LEFT,
        sourcePackage = NavigationSource.YANDEX_MAPS, updatedAtMs = 200)

    @Test fun independentProvidersUseTheLatestGuidance() {
        val store = NavigationRoutes()
        store.update("google-route", google)
        store.update("yandex-route", yandex)
        assertEquals(yandex, current(store, "auto"))
        assertEquals(google, current(store, "google"))
        assertEquals(yandex, current(store, "yandex"))
    }

    @Test fun unrelatedNotificationRemovalKeepsGuidance() {
        val store = NavigationRoutes()
        store.update("route", google)
        store.update("traffic", null)
        store.retainKeys(setOf("route"))
        assertEquals(google, current(store, "auto"))
    }

    @Test fun EndedRouteFallsBackToOtherActiveProvider() {
        val store = NavigationRoutes()
        store.update("google-route", google)
        store.update("yandex-route", yandex)
        store.update("yandex-route", NavigationState.NONE)
        assertEquals(google, current(store, "auto"))
        assertFalse(current(store, "yandex").active)
    }

    @Test fun ProviderSwitchDoesNotReuseWrongApplicationRoute() {
        val store = NavigationRoutes()
        store.update("route", google)
        assertFalse(current(store, "yandex").active)
        assertEquals(google, current(store, "google"))
    }

    @Test fun NavigatorIsAcceptedAsYandex() {
        val store = NavigationRoutes()
        val navigator = yandex.copy(sourcePackage = NavigationSource.YANDEX_NAVI)
        store.update("navigator", navigator)
        assertEquals(navigator, current(store, "yandex"))
    }

    @Test fun samePackageKeepsItsOtherActiveNotificationWhenOneIsRemoved() {
        val store = NavigationRoutes()
        store.update("older", google)
        store.update("newer", google.copy(updatedAtMs = 300, street = "New"))
        assertEquals("New", current(store, "google").street)
        store.retainKeys(setOf("older"))
        assertEquals(google, current(store, "google"))
    }

    @Test fun DisconnectClearsAllGuidance() {
        val store = NavigationRoutes()
        store.update("google", google)
        store.update("yandex", yandex)
        store.clear()
        assertFalse(current(store, "auto").active)
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

    @Test fun YandexNotificationArrowAndForegroundMetricsReachHudTogether() {
        val screen = yandex.copy(maneuver = NavManeuver.UNKNOWN,
            distance = NavDistance("300", "m", 300), street = "Atatürk Cd.",
            remainingDistance = NavDistance("18", "km", 18_000))
        val result = hudRoute(yandex, screen, "yandex")
        assertEquals(NavManeuver.LEFT, result.maneuver)
        assertEquals(screen.distance, result.distance)
        assertEquals(screen.street, result.street)
        assertEquals(screen.remainingDistance, result.remainingDistance)
    }

    @Test fun ForegroundDoesNotOverwritePopulatedNotificationFields() {
        val notification = yandex.copy(distance = NavDistance("200", "m", 200), street = "Yeni yol")
        val screen = yandex.copy(distance = NavDistance("300", "m", 300), street = "Eski yol")
        assertEquals(notification, hudRoute(notification, screen, "auto"))
    }

    @Test fun YandexMapsScreenDoesNotFillAnotherNavigatorsRoute() {
        val navigator = yandex.copy(sourcePackage = NavigationSource.YANDEX_NAVI)
        val screen = yandex.copy(distance = NavDistance("300", "m", 300), street = "Maps yolu")
        assertEquals(navigator, hudRoute(navigator, screen, "yandex"))
    }
}
