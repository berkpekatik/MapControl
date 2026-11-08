package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [NavEndAction] başlık eşleme sınaması. */
class NavEndActionTest {
    @Test fun matchesKnownEndTitles() {
        listOf(
            "Çık", "ÇIK", "Navigasyondan çık", "Navigasyonu bitir", "Rehberliği durdur", "Bitir",
            "Exit", "Exit navigation", "End navigation", "Stop", "Stop navigation", "Finish",
            "Завершить", "Завершить маршрут", "Остановить", "Exit navigation.",
        ).forEach { assertTrue(it, NavEndAction.isEndTitle(it)) }
    }

    @Test fun rejectsOtherActions() {
        listOf(
            null, "", "Sessiz", "Mute", "Pause", "Duraklat", "Share trip progress", "Yolculuğu paylaş", "Aç", "Open",
            "Yeniden yönlendir",
        ).forEach { assertFalse(it.toString(), NavEndAction.isEndTitle(it)) }
    }

    @Test fun findsByTitleNotPosition() {
        assertEquals(2, NavEndAction.indexOf(listOf("Mute", "Share", "Exit", "Stop")))
        assertEquals(-1, NavEndAction.indexOf(listOf("Mute", "Share")))
        assertEquals(0, NavEndAction.indexOf(listOf("Çık")))
    }

    @Test fun demoRouteShowsEndPill() {
        assertTrue(NavigationState.DEMO.demoEnd)
        assertTrue(NavigationState.DEMO.hasEnd)
        assertFalse(NavigationState.NONE.hasEnd)
        assertFalse(NavigationState(active = true).hasEnd)
    }
}
