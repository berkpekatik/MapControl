package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneDemoTest {
    @Test fun parseCall() {
        assertEquals(CallKind.INCOMING, PhoneDemo.parseCall("incoming"))
        assertEquals(CallKind.ACTIVE, PhoneDemo.parseCall(" Active "))
        assertEquals(CallKind.NONE, PhoneDemo.parseCall("none"))
        assertEquals(CallKind.NONE, PhoneDemo.parseCall(null))
    }

    @Test fun demoDataRanksAndShapes() {
        val now = 1_800_000_000_000L
        val recents = PhoneDemo.recents(now)
        assertEquals(5, recents.size)
        val fav = FavoritesRanker.rank(PhoneDemo.contacts, recents, emptyList(), nowMs = now)
        assertEquals(listOf("Ayşe", "Ev", "Mehmet"), fav.take(3).map { it.name })
        assertTrue(fav.size in 3..6)
        assertEquals(5, RecentsShaper.shape(recents).size)
        assertEquals(RecentType.MISSED, RecentsShaper.shape(recents).first().type)
    }

    @Test fun demoNumbersAreFake() {
        assertTrue(PhoneDemo.contacts.all { it.number.startsWith("0555 000 00 ") })
    }
}
