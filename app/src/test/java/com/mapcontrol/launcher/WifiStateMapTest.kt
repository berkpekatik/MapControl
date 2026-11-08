package com.mapcontrol.launcher

import com.mapcontrol.launcher.WifiUi.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiStateMapTest {
    @Test fun connectedShowsLevelAndShortName() {
        assertEquals(WifiUi(Kind.CONNECTED, 3, "Ev"), WifiStateMap.map(true, false, true, 3, "\"Ev\""))
    }

    @Test fun longOrUnknownNameIsHidden() {
        assertNull(WifiStateMap.map(true, false, true, 2, "\"BirCokUzunAgAdi123\"").ssid)
        assertNull(WifiStateMap.map(true, false, true, 2, "<unknown ssid>").ssid)
        assertNull(WifiStateMap.map(true, false, true, 2, null).ssid)
        assertEquals("123456789012", WifiStateMap.shortSsid("123456789012"))
    }

    @Test fun levelIsClamped() {
        assertEquals(4, WifiStateMap.map(true, false, true, 9, null).level)
        assertEquals(0, WifiStateMap.map(true, false, true, -1, null).level)
    }

    @Test fun notConnectedOrDisabledIsOff() {
        assertEquals(Kind.OFF, WifiStateMap.map(true, false, false, 4, "x").kind)
        assertEquals(Kind.OFF, WifiStateMap.map(false, false, true, 4, "x").kind)
    }

    @Test fun hotspotWins() {
        assertEquals(Kind.HOTSPOT, WifiStateMap.map(true, true, true, 4, "x").kind)
        assertEquals(Kind.HOTSPOT, WifiStateMap.map(false, true, false, 0, null).kind)
    }
}
