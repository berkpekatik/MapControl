package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class BtStateMapTest {
    @Test fun noAdapterOrDisabledIsOff() {
        assertEquals(BtUi.OFF, BtStateMap.map(false, false, false))
        assertEquals(BtUi.OFF, BtStateMap.map(true, false, true))
    }

    @Test fun enabledWithoutPhoneIsOn() {
        assertEquals(BtUi.ON, BtStateMap.map(true, true, false))
    }

    @Test fun enabledWithPhoneIsConnected() {
        assertEquals(BtUi.CONNECTED, BtStateMap.map(true, true, true))
    }
}
