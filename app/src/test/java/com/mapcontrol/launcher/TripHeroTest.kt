package com.mapcontrol.launcher

import com.mapcontrol.launcher.TripHero.Content
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TripHeroTest {

    @Test fun cardShowsTripWhenConnectedWithKm() {
        assertEquals(Content.TRIP, TripHero.content(connected = true, tripKm = 53.8))
        assertEquals(Content.TRIP, TripHero.content(connected = true, tripKm = 0.0))
    }

    @Test fun noDataWins() {
        assertEquals(Content.NO_DATA, TripHero.content(connected = false, tripKm = 53.8))
        assertEquals(Content.NO_DATA, TripHero.content(connected = false, tripKm = null))
        assertEquals(Content.NO_DATA, TripHero.content(connected = true, tripKm = null))
    }

    /** 7 Kasım kararı: içerik sürüş durumundan bağımsızdır; anlık tüketim kartta yok (ayrı tüketim kutucuğu). */
    @Test fun noLiveConsumptionContentExists() {
        assertEquals(listOf("NO_DATA", "TRIP"), Content.values().map { it.name })
    }

    @Test fun driveMotionNotifiesOnlyOnChange() {
        DriveMotion.set(false)
        val seen = ArrayList<Boolean>()
        val l: (Boolean) -> Unit = { seen += it }
        DriveMotion.add(l)
        DriveMotion.set(false); DriveMotion.set(true); DriveMotion.set(true); DriveMotion.set(false)
        DriveMotion.remove(l)
        DriveMotion.set(true)
        DriveMotion.set(false)
        assertEquals(listOf(true, false), seen)
    }

    @Test fun separateDriveLayoutIsOffByDefault() {
        assertFalse(DriveLayoutPrefs.DEFAULT_ON)
    }
}
