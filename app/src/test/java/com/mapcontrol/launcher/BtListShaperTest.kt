package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BtListShaperTest {
    private fun dev(addr: String, name: String?, phone: Boolean = false, audio: Boolean = false, kind: BtKind = BtKind.OTHER) =
        BtDevice(addr, name, kind, phone, audio)

    @Test fun splitsConnectedFromPaired() {
        val (on, off) = BtListShaper.shape(listOf(dev("A", "AirPods"), dev("B", "iPhone", phone = true), dev("C", "Galaxy")))
        assertEquals(listOf("iPhone"), on.map { it.name })
        assertEquals(listOf("AirPods", "Galaxy"), off.map { it.name })
    }

    @Test fun connectedPhoneFirstThenName() {
        val (on, _) = BtListShaper.shape(listOf(dev("A", "Aaa hoparlör", audio = true), dev("B", "Zeynep", phone = true)))
        assertEquals(listOf("Zeynep", "Aaa hoparlör"), on.map { it.name })
    }

    @Test fun pairedSortedByNameIgnoringCase() {
        val (_, off) = BtListShaper.shape(listOf(dev("1", "beta"), dev("2", "Alfa"), dev("3", "gamma")))
        assertEquals(listOf("Alfa", "beta", "gamma"), off.map { it.name })
    }

    @Test fun sameAddressMergedOnceWithProfiles() {
        val (on, off) = BtListShaper.shape(listOf(dev("aa:bb", "iPhone", phone = true), dev("AA:BB", null, audio = true, kind = BtKind.PHONE)))
        assertEquals(1, on.size)
        assertTrue(on[0].phone && on[0].audio)
        assertEquals(BtKind.PHONE, on[0].kind)
        assertEquals("iPhone", on[0].name)
        assertTrue(off.isEmpty())
    }

    @Test fun labelFallsBackToAddress() {
        assertEquals("11:22", BtListShaper.label(dev("11:22", null)))
        assertEquals("11:22", BtListShaper.label(dev("11:22", "   ")))
        assertEquals("Araba", BtListShaper.label(dev("11:22", " Araba ")))
    }

    @Test fun outcomeAnyOkStarts() {
        assertEquals(BtOutcome.STARTED, BtListShaper.outcome(listOf(BtCall.DENIED, BtCall.OK)))
    }

    @Test fun outcomeDeniedWithoutOkIsNotPermitted() {
        assertEquals(BtOutcome.NOT_PERMITTED, BtListShaper.outcome(listOf(BtCall.UNSUPPORTED, BtCall.DENIED, BtCall.FAILED)))
    }

    @Test fun outcomeNothingOrFailuresIsFailed() {
        assertEquals(BtOutcome.FAILED, BtListShaper.outcome(emptyList()))
        assertEquals(BtOutcome.FAILED, BtListShaper.outcome(listOf(BtCall.UNSUPPORTED, BtCall.FAILED)))
    }

    @Test fun switchMode() {
        assertEquals(BtSwitch.ABSENT, BtListShaper.switchMode(false, true))
        assertEquals(BtSwitch.ACTIVE, BtListShaper.switchMode(true, true))
        assertEquals(BtSwitch.READ_ONLY, BtListShaper.switchMode(true, false))
    }

    @Test fun kindFromMajorClass() {
        assertEquals(BtKind.PHONE, BtListShaper.kind(0x0200))
        assertEquals(BtKind.AUDIO, BtListShaper.kind(0x0400))
        assertEquals(BtKind.OTHER, BtListShaper.kind(0x0100))
    }

    @Test fun carProfilesTriedFirstAndMapped() {
        assertEquals(listOf(16, 11, 1, 2), BtListShaper.PROFILES)
        assertTrue(BtListShaper.isPhoneProfile(BtListShaper.HEADSET_CLIENT))
        assertTrue(BtListShaper.isAudioProfile(BtListShaper.A2DP_SINK))
        assertFalse(BtListShaper.isPhoneProfile(BtListShaper.A2DP_SINK))
        assertFalse(BtListShaper.isAudioProfile(BtListShaper.HEADSET))
    }
}
