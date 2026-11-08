package com.mapcontrol.launcher

import org.junit.Assert.assertSame
import org.junit.Test

class PhonePairLogicTest {

    @Test
    fun showsQrOnlyWhenPairingAddressExists() {
        assertSame(PhonePairUi.Ready, PhonePairLogic.state(running = true, pairingUri = "uri", starting = false))
    }

    @Test
    fun runningWithoutAddressMeansNoNetwork() {
        assertSame(PhonePairUi.NoNetwork, PhonePairLogic.state(running = true, pairingUri = null, starting = false))
    }

    @Test
    fun stoppedServerOffersStart() {
        assertSame(PhonePairUi.ServerOff, PhonePairLogic.state(running = false, pairingUri = null, starting = false))
        assertSame(PhonePairUi.Starting, PhonePairLogic.state(running = false, pairingUri = null, starting = true))
    }
}
