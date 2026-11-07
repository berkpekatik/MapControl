package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapAppChoiceTest {
    private val google = MapApp.GOOGLE_MAPS.packageName
    private val ymaps = MapApp.YANDEX_MAPS.packageName
    private val ynavi = MapApp.YANDEX_NAVI.packageName
    private val all = setOf(google, ymaps, ynavi)

    @Test
    fun defaultIsGoogleWhenInstalledElseYandex() {
        assertEquals(MapApp.GOOGLE_MAPS, MapAppChoice.defaultFor(all))
        assertEquals(MapApp.GOOGLE_MAPS, MapAppChoice.defaultFor(setOf(google, ynavi)))
        assertEquals(MapApp.YANDEX_MAPS, MapAppChoice.defaultFor(setOf(ynavi, ymaps)))
        assertEquals(MapApp.YANDEX_NAVI, MapAppChoice.defaultFor(setOf(ynavi)))
        assertNull(MapAppChoice.defaultFor(emptySet()))
    }

    @Test
    fun savedChoiceWinsOnlyWhenInstalled() {
        assertEquals(MapApp.YANDEX_MAPS, MapAppChoice.resolve("yandex_maps", all))
        assertEquals(MapApp.GOOGLE_MAPS, MapAppChoice.resolve("yandex_maps", setOf(google, ynavi)))
        assertEquals(MapApp.GOOGLE_MAPS, MapAppChoice.resolve("bogus", all))
        assertEquals(MapApp.GOOGLE_MAPS, MapAppChoice.resolve(null, all))
        assertNull(MapAppChoice.resolve("google_maps", emptySet()))
    }

    @Test
    fun choicesListOnlyInstalledInOrder() {
        assertEquals(listOf(MapApp.GOOGLE_MAPS, MapApp.YANDEX_NAVI), MapAppChoice.choices(setOf(ynavi, google, "x")))
        assertEquals(emptyList<MapApp>(), MapAppChoice.choices(emptySet()))
    }

    @Test
    fun notificationPackagesFollowChoice() {
        assertEquals(setOf(google), MapAppChoice.notificationPackages(MapApp.GOOGLE_MAPS))
        assertEquals(setOf(ymaps, ynavi), MapAppChoice.notificationPackages(MapApp.YANDEX_MAPS))
        assertEquals(setOf(ymaps, ynavi), MapAppChoice.notificationPackages(MapApp.YANDEX_NAVI))
        // Hiçbiri kurulu değilse eski davranış (Yandex).
        assertEquals(setOf(ymaps, ynavi), MapAppChoice.notificationPackages(null))
    }

    @Test
    fun routeUriPerApp() {
        assertEquals("google.navigation:q=41.008200,28.978400", NavPlaces.routeUri(google, 41.0082, 28.9784))
        assertEquals("yandexmaps://build_route_on_map?lat_to=41.008200&lon_to=28.978400", NavPlaces.routeUri(ymaps, 41.0082, 28.9784))
        assertEquals("yandexnavi://build_route_on_map?lat_to=41.008200&lon_to=28.978400", NavPlaces.routeUri(ynavi, 41.0082, 28.9784))
    }

    @Test
    fun otherNavAppsGetGeoUris() {
        assertEquals("geo:41.008200,28.978400?q=41.008200,28.978400", NavPlaces.routeUri("com.waze", 41.0082, 28.9784))
        assertEquals("yandexmaps://build_route_on_map?lat_to=41.008200&lon_to=28.978400", NavPlaces.routeUri(ymaps, 41.0082, 28.9784))
    }
}
