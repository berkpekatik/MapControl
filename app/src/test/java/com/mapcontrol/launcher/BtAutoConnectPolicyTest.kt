package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BtAutoConnectPolicyTest {
    private fun dev(addr: String, kind: BtKind = BtKind.PHONE, phone: Boolean = false, audio: Boolean = false) =
        BtDevice(addr, null, kind, phone, audio)

    @Test fun targetsLastDeviceIgnoringCase() {
        assertEquals("AA:BB", BtAutoConnectPolicy.target(listOf(dev("CC:DD"), dev("AA:BB")), "aa:bb"))
    }

    @Test fun fallsBackToOnlyPairedPhoneWhenLastUnknown() {
        assertEquals("AA", BtAutoConnectPolicy.target(listOf(dev("AA"), dev("S", BtKind.AUDIO)), null))
        assertEquals("AA", BtAutoConnectPolicy.target(listOf(dev("AA")), "silinmiş"))
    }

    @Test fun doesNotGuessBetweenSeveralPhones() {
        assertNull(BtAutoConnectPolicy.target(listOf(dev("AA"), dev("BB")), null))
    }

    @Test fun neverPicksNonPhoneWithoutHistory() {
        assertNull(BtAutoConnectPolicy.target(listOf(dev("S", BtKind.AUDIO)), null))
    }

    @Test fun waitsWhileAdapterOff() {
        assertEquals(BtAutoStep.Wait, BtAutoConnectPolicy.next(false, true, emptyList(), "AA"))
    }

    @Test fun stopsWithoutPermission() {
        assertEquals(BtAutoStep.Done, BtAutoConnectPolicy.next(true, false, emptyList(), "AA"))
    }

    @Test fun stopsWhenAnyDeviceConnectedSoAnotherPhoneIsNotKicked() {
        val devices = listOf(dev("AA"), dev("BB", phone = true))
        assertEquals(BtAutoStep.Done, BtAutoConnectPolicy.next(true, true, devices, "AA"))
    }

    @Test fun connectsToTargetWhenNothingConnected() {
        assertEquals(BtAutoStep.Connect("AA"), BtAutoConnectPolicy.next(true, true, listOf(dev("AA"), dev("BB")), "AA"))
    }

    @Test fun stopsWhenNoTarget() {
        assertEquals(BtAutoStep.Done, BtAutoConnectPolicy.next(true, true, listOf(dev("AA"), dev("BB")), null))
    }

    @Test fun attemptsAreBounded() {
        val last = BtAutoConnectPolicy.ATTEMPT_DELAYS_MS.lastIndex
        assertNotNull(BtAutoConnectPolicy.delayAfter(last - 1))
        assertNull(BtAutoConnectPolicy.delayAfter(last))
    }
}
