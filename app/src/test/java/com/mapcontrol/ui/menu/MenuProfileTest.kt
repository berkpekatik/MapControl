package com.mapcontrol.ui.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuProfileTest {

    private val allTabs = listOf(
        MenuTabs.WIFI, MenuTabs.FILE, MenuTabs.PROJECTION, MenuTabs.APPS, MenuTabs.DRIVE_MODE, MenuTabs.SETTINGS,
        MenuTabs.WELCOME_SOUND, MenuTabs.VEHICLE_INFO, MenuTabs.EQUALIZER, MenuTabs.CLIMATE_AUTOMATION,
        MenuTabs.FACE_RECOGNITION, MenuTabs.CAMERA_360, MenuTabs.SPLIT_SCREEN, MenuTabs.SUNROOF_AUTOMATION,
        MenuTabs.HOME_SCREEN,
    )

    @Test
    fun noSelectionShowsEverything() {
        allTabs.forEach { assertTrue("tab $it", MenuProfile.isVisible(it, null)) }
    }

    @Test
    fun emptySelectionKeepsOnlyAlwaysVisible() {
        val visible = allTabs.filter { MenuProfile.isVisible(it, emptySet()) }.toSet()
        assertEquals(setOf(MenuTabs.WIFI, MenuTabs.APPS, MenuTabs.FILE, MenuTabs.SETTINGS), visible)
    }

    @Test
    fun cardOpensItsMenus() {
        val cards = setOf(MenuCard.AUTOMATION)
        assertTrue(MenuProfile.isVisible(MenuTabs.CLIMATE_AUTOMATION, cards))
        assertTrue(MenuProfile.isVisible(MenuTabs.SUNROOF_AUTOMATION, cards))
        assertFalse(MenuProfile.isVisible(MenuTabs.DRIVE_MODE, cards))
        assertFalse(MenuProfile.isVisible(MenuTabs.VEHICLE_INFO, cards))
    }

    @Test
    fun memoryAndTechAreSeparateCards() {
        assertTrue(MenuProfile.isVisible(MenuTabs.DRIVE_MODE, setOf(MenuCard.MEMORY)))
        assertTrue(MenuProfile.isVisible(MenuTabs.VEHICLE_INFO, setOf(MenuCard.TECH)))
        assertFalse(MenuProfile.isVisible(MenuTabs.VEHICLE_INFO, setOf(MenuCard.MEMORY)))
    }

    @Test
    fun everyTabOutsideAlwaysBelongsToOneCard() {
        allTabs.filterNot { it in MenuProfile.ALWAYS }.forEach { tab ->
            assertEquals("tab $tab", 1, MenuCard.values().count { tab in it.tabs })
        }
    }

    @Test
    fun unknownTabStaysVisible() {
        assertTrue(MenuProfile.isVisible(4, emptySet()))
    }

    @Test
    fun allCardsNormalizeToEverything() {
        assertNull(MenuProfile.normalize(MenuCard.values().toSet()))
        assertEquals(setOf(MenuCard.SOUND), MenuProfile.normalize(setOf(MenuCard.SOUND)))
    }

    @Test
    fun encodeDecodeRoundTripIgnoresUnknown() {
        val cards = setOf(MenuCard.LAUNCHER, MenuCard.CAMERA)
        assertEquals(cards, MenuProfile.decode(MenuProfile.encode(cards)))
        assertEquals(setOf(MenuCard.SOUND), MenuProfile.decode(setOf("SOUND", "GONE_CARD")))
        assertNull(MenuProfile.decode(null))
    }
}
