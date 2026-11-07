package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiListShaperTest {
    private fun scan(ssid: String, level: Int, caps: String? = "[WPA2-PSK]") = WifiScan(ssid, level, caps)
    private val none: (String) -> Boolean = { false }

    @Test fun dedupeKeepsStrongest() {
        val rows = WifiListShaper.shape(listOf(scan("Ev", 1), scan("Ev", 3), scan("Ev", 2)), null, none, false)
        assertEquals(1, rows.size)
        assertEquals(3, rows[0].scan.level)
    }

    @Test fun hidesEmptyAndUnknownSsids() {
        val rows = WifiListShaper.shape(listOf(scan("", 4), scan("  ", 4), scan("<unknown ssid>", 4), scan("Ofis", 2)), null, none, false)
        assertEquals(listOf("Ofis"), rows.map { it.scan.ssid })
    }

    @Test fun sortsStrongestFirstThenName() {
        val rows = WifiListShaper.shape(listOf(scan("b", 2), scan("a", 2), scan("z", 4), scan("y", 1)), null, none, false)
        assertEquals(listOf("z", "a", "b", "y"), rows.map { it.scan.ssid })
    }

    @Test fun excludesConnectedNetwork() {
        val rows = WifiListShaper.shape(listOf(scan("Ev", 4), scan("Ofis", 2)), "Ev", none, false)
        assertEquals(listOf("Ofis"), rows.map { it.scan.ssid })
    }

    @Test fun drivingCapsAtSix() {
        val many = (1..10).map { scan("n$it", it % 5) }
        assertEquals(6, WifiListShaper.shape(many, null, none, true).size)
        assertEquals(10, WifiListShaper.shape(many, null, none, false).size)
    }

    @Test fun savedFlagAndSecurity() {
        val rows = WifiListShaper.shape(listOf(scan("Ev", 3), scan("Kafe", 2, "[ESS]")), null, { it == "Ev" }, false)
        assertTrue(rows.first { it.scan.ssid == "Ev" }.saved)
        assertTrue(rows.first { it.scan.ssid == "Ev" }.scan.secured)
        assertFalse(rows.first { it.scan.ssid == "Kafe" }.scan.secured)
    }

    @Test fun cleanSsidStripsQuotes() {
        assertEquals("Ev", WifiListShaper.cleanSsid("\"Ev\""))
        assertEquals(null, WifiListShaper.cleanSsid("<unknown ssid>"))
        assertEquals(null, WifiListShaper.cleanSsid(null))
    }

    @Test fun policy() {
        assertTrue(WifiListShaper.passwordAllowed(driving = false))
        assertFalse(WifiListShaper.passwordAllowed(driving = true))
        assertTrue(WifiListShaper.scrollAllowed(false))
        assertFalse(WifiListShaper.scrollAllowed(true))
    }
}
