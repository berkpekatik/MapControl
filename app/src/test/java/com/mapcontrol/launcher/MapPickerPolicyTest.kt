package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapPickerPolicyTest {

    @Test fun mapSlotPrefersCardThenOtherThenPicker() {
        val installed = setOf("com.google.android.apps.maps", "com.waze")
        assertEquals("com.google.android.apps.maps", MapPickerPolicy.mapSlot("com.google.android.apps.maps", "com.waze", installed::contains))
        assertEquals("com.waze", MapPickerPolicy.mapSlot(null, "com.waze", installed::contains))
        assertEquals("com.waze", MapPickerPolicy.mapSlot("ru.yandex.yandexmaps", " com.waze ", installed::contains))
        assertNull(MapPickerPolicy.mapSlot(null, "com.uninstalled", installed::contains))
        assertNull(MapPickerPolicy.mapSlot("", "", installed::contains))
    }

    @Test fun pickerListsMapAppsFirstThenOtherNavAppsByName() {
        val maps = listOf("com.google.android.apps.maps", "ru.yandex.yandexmaps")
        val geo = listOf(
            "ru.yandex.yandexmaps" to "Yandex Maps",
            "com.waze" to "Waze",
            "com.mapcontrol" to "MapControl",
            "app.organicmaps" to "Organic Maps",
        )
        assertEquals(
            listOf("com.google.android.apps.maps", "ru.yandex.yandexmaps", "app.organicmaps", "com.waze"),
            MapPickerPolicy.pickerChoices(maps, geo, "com.mapcontrol"),
        )
        assertEquals(emptyList<String>(), MapPickerPolicy.pickerChoices(emptyList(), emptyList(), "com.mapcontrol"))
    }

    @Test fun staleSessionMarkerRestoresOnlyWhenPresent() {
        assertNull(MapPickerPolicy.staleToRestore(null))
        assertNull(MapPickerPolicy.staleToRestore("  "))
        assertEquals("com.google.android.apps.maps", MapPickerPolicy.staleToRestore(" com.google.android.apps.maps "))
    }
}
