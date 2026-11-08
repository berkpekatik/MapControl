package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivePolicyTest {

    private fun run(vararg events: DriveEvent): DrivePhase = events.fold(DrivePhase.IDLE) { p, e -> DrivePolicy.next(p, e) }

    @Test fun tapOpensThenLauncherLosingTopMeansSplitIsUp() {
        assertEquals(DrivePhase.OPENING, run(DriveEvent.TAP))
        assertEquals(DrivePhase.OPEN, run(DriveEvent.TAP, DriveEvent.LAUNCHER_TOP_LOST))
    }

    @Test fun launcherBackOnTopEndsTheDriveScreen() {
        assertEquals(DrivePhase.IDLE, run(DriveEvent.TAP, DriveEvent.LAUNCHER_TOP_LOST, DriveEvent.LAUNCHER_TOP_GAINED))
    }

    @Test fun launcherStillOnTopWhileOpeningDoesNotEnd() {
        // Dokunuş anında ana ekran hâlâ önde (aynı geçişte tekrar "öne geldi" de gelebilir): açılış sürer.
        assertEquals(DrivePhase.OPENING, run(DriveEvent.TAP, DriveEvent.LAUNCHER_TOP_GAINED))
    }

    @Test fun failureOrTimeoutWhileOpeningDropsTheBackdrop() {
        assertEquals(DrivePhase.IDLE, run(DriveEvent.TAP, DriveEvent.START_FAILED))
        assertEquals(DrivePhase.IDLE, run(DriveEvent.TAP, DriveEvent.TIMEOUT))
        // Açıldıktan sonra gelen gecikmiş zaman aşımı sürüş ekranını kapatmaz.
        assertEquals(DrivePhase.OPEN, run(DriveEvent.TAP, DriveEvent.LAUNCHER_TOP_LOST, DriveEvent.TIMEOUT))
    }

    @Test fun failureAfterTheMiniHalfOpenedEndsTheSession() {
        // Mini yarısı açıldı (ana ekran öndeki yerini kaybetti), harita yarısı açılamadı: tek başına Mini yarısı kalmasın.
        assertEquals(DrivePhase.IDLE, run(DriveEvent.TAP, DriveEvent.LAUNCHER_TOP_LOST, DriveEvent.START_FAILED))
    }

    @Test fun failedSplitFallsBackToTheMapOnlyWhenThereIsOne() {
        assertTrue(DrivePolicy.fallbackOnFailure("ru.yandex.yandexmaps"))
        assertFalse(DrivePolicy.fallbackOnFailure(null))
        assertFalse(DrivePolicy.fallbackOnFailure(" "))
    }

    @Test fun idleIgnoresEverythingButTap() {
        assertEquals(DrivePhase.IDLE, run(DriveEvent.LAUNCHER_TOP_LOST))
        assertEquals(DrivePhase.IDLE, run(DriveEvent.LAUNCHER_TOP_GAINED))
        assertEquals(DrivePhase.IDLE, run(DriveEvent.TIMEOUT))
        // İkinci dokunuş açılışı yeniden başlatmaz.
        assertEquals(DrivePhase.OPEN, run(DriveEvent.TAP, DriveEvent.LAUNCHER_TOP_LOST, DriveEvent.TAP))
    }

    @Test fun backdropWhileOpeningAndOpen() {
        assertFalse(DrivePolicy.backdrop(DrivePhase.IDLE))
        assertTrue(DrivePolicy.backdrop(DrivePhase.OPENING))
        assertTrue(DrivePolicy.backdrop(DrivePhase.OPEN))
    }

    @Test fun mapSlotPrefersCardThenOtherThenPicker() {
        val installed = setOf("com.google.android.apps.maps", "com.waze")
        assertEquals("com.google.android.apps.maps", DrivePolicy.mapSlot("com.google.android.apps.maps", "com.waze", installed::contains))
        assertEquals("com.waze", DrivePolicy.mapSlot(null, "com.waze", installed::contains))
        assertEquals("com.waze", DrivePolicy.mapSlot("ru.yandex.yandexmaps", " com.waze ", installed::contains))
        assertNull(DrivePolicy.mapSlot(null, "com.uninstalled", installed::contains))
        assertNull(DrivePolicy.mapSlot("", "", installed::contains))
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
            DrivePolicy.pickerChoices(maps, geo, "com.mapcontrol"),
        )
        assertEquals(emptyList<String>(), DrivePolicy.pickerChoices(emptyList(), emptyList(), "com.mapcontrol"))
    }

    @Test fun staleSessionMarkerRestoresOnlyWhenPresent() {
        assertNull(DrivePolicy.staleToRestore(null))
        assertNull(DrivePolicy.staleToRestore("  "))
        assertEquals("com.google.android.apps.maps", DrivePolicy.staleToRestore(" com.google.android.apps.maps "))
    }

    @Test fun launcherResumeRestoresOnlyWhenIdle() {
        val m = "com.google.android.apps.maps"
        assertEquals(m, DrivePolicy.restoreOnLauncherResume(DrivePhase.IDLE, null, m))
        assertEquals(m, DrivePolicy.restoreOnLauncherResume(DrivePhase.IDLE, m, null))
        assertEquals("a", DrivePolicy.restoreOnLauncherResume(DrivePhase.IDLE, "a", "b"))
        assertNull(DrivePolicy.restoreOnLauncherResume(DrivePhase.OPEN, m, m))
        assertNull(DrivePolicy.restoreOnLauncherResume(DrivePhase.OPENING, m, m))
        assertNull(DrivePolicy.restoreOnLauncherResume(DrivePhase.IDLE, " ", null))
    }
}
