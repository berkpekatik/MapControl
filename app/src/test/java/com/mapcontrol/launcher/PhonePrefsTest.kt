package com.mapcontrol.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonePrefsTest {
    @Test fun assistantButtonDefaultsOff() {
        assertFalse(PhonePrefs.SHOW_ASSISTANT_DEFAULT)
        assertFalse(PhonePrefs.resolveShowAssistant(null))
    }

    @Test fun explicitlyEnabledStaysOn() = assertTrue(PhonePrefs.resolveShowAssistant(true))

    @Test fun explicitlyDisabledStaysOff() = assertFalse(PhonePrefs.resolveShowAssistant(false))
}
