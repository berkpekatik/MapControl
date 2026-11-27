package com.mapcontrol.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YandexExitNumberTest {
    @Test fun readsTurkishEnglishAndWordForms() {
        assertEquals(3, YandexNavScraper.parseExitNumber("3. çıkış"))
        assertEquals(1, YandexNavScraper.parseExitNumber("1. çıkış"))
        assertEquals(2, YandexNavScraper.parseExitNumber("Exit 2"))
        assertEquals(3, YandexNavScraper.parseExitNumber("Third exit"))
        assertEquals(4, YandexNavScraper.parseExitNumber("Fourth exit"))
    }

    @Test fun rejectsMissingOrOutOfRange() {
        assertNull(YandexNavScraper.parseExitNumber(null))
        assertNull(YandexNavScraper.parseExitNumber("çıkış"))
        assertNull(YandexNavScraper.parseExitNumber("0. çıkış"))
        assertNull(YandexNavScraper.parseExitNumber("12. çıkış"))
    }

    @Test fun exitNumberIsNotHeldAcrossSnapshots() {
        val stability = YandexNavStability()
        val withExit = YandexNavSnapshot(true, "300", "m", "Cd.", "18 km", null, null, null, null, null, 3)
        assertEquals(3, stability.accept(withExit, 0L).exitNumber)
        val without = YandexNavSnapshot(true, "200", "m", "Cd.", "18 km", null, null, null, null, null, null)
        assertNull(stability.accept(without, 1_000L).exitNumber)
    }
}
