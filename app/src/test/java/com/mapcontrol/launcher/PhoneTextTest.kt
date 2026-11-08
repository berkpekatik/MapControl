package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneTextTest {
    @Test fun initialsTwoWordsTurkish() = assertEquals("AY", PhoneText.initials("ayşe yılmaz"))
    @Test fun initialsDottedI() = assertEquals("İE", PhoneText.initials("ismail emre"))
    @Test fun initialsSingleWord() = assertEquals("E", PhoneText.initials("Ev"))
    @Test fun initialsAtMostTwo() = assertEquals("MA", PhoneText.initials("Mehmet Ali Kaya"))
    @Test fun initialsNumberOrBlank() {
        assertEquals("#", PhoneText.initials("+90 555 000 00 00"))
        assertEquals("#", PhoneText.initials(""))
        assertEquals("#", PhoneText.initials(null))
    }
    @Test fun initialsSkipsLeadingDigitWord() = assertEquals("A", PhoneText.initials("2 Anne"))

    @Test fun callStateInCall() {
        assertFalse(CallState.IDLE.inCall)
        assertTrue(CallState(CallKind.INCOMING).inCall)
        assertTrue(CallState(CallKind.ACTIVE).inCall)
        assertFalse(CallState(CallKind.ENDED).inCall)
    }

    @Test fun toStringHasNoPersonalData() {
        val c = PhoneContact("1", "Ayşe", "05550000000")
        val r = PhoneRecent(RecentType.MISSED, "Ayşe", "05550000000", 0L)
        val s = PhoneSnapshot(favorites = listOf(c), recents = listOf(r), call = CallState(CallKind.ACTIVE, "Ayşe", "05550000000"))
        listOf(c.toString(), r.toString(), s.toString()).forEach {
            assertFalse(it, it.contains("Ayşe"))
            assertFalse(it, it.contains("0555"))
        }
    }
}
