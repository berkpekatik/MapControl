package com.mapcontrol.launcher

import com.mapcontrol.launcher.VoiceKeyPolicy.Companion.ACTION_LONG_PRESS
import com.mapcontrol.launcher.VoiceKeyPolicy.Companion.ACTION_LONG_RELEASE
import com.mapcontrol.launcher.VoiceKeyPolicy.Companion.ACTION_PRESS
import com.mapcontrol.launcher.VoiceKeyPolicy.Companion.ACTION_SHORT_RELEASE
import com.mapcontrol.launcher.VoiceKeyPolicy.Companion.KEY_TYPE_VR
import com.mapcontrol.launcher.VoiceKeyPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceKeyPolicyTest {

    @Test fun longPressStartsAssistant() =
        assertEquals(Decision.ASSISTANT, VoiceKeyPolicy().onKey(KEY_TYPE_VR, ACTION_LONG_PRESS, true, 1_000))

    @Test fun disabledIgnores() =
        assertEquals(Decision.IGNORE, VoiceKeyPolicy().onKey(KEY_TYPE_VR, ACTION_LONG_PRESS, false, 1_000))

    @Test fun shortPressAndReleasesIgnored() {
        val p = VoiceKeyPolicy()
        assertEquals(Decision.IGNORE, p.onKey(KEY_TYPE_VR, ACTION_PRESS, true, 1_000))
        assertEquals(Decision.IGNORE, p.onKey(KEY_TYPE_VR, ACTION_SHORT_RELEASE, true, 1_100))
        assertEquals(Decision.IGNORE, p.onKey(KEY_TYPE_VR, ACTION_LONG_RELEASE, true, 1_200))
    }

    @Test fun otherKeyTypeIgnored() =
        assertEquals(Decision.IGNORE, VoiceKeyPolicy().onKey(19, ACTION_LONG_PRESS, true, 1_000))

    @Test fun debounceThenAgain() {
        val p = VoiceKeyPolicy()
        assertEquals(Decision.ASSISTANT, p.onKey(KEY_TYPE_VR, ACTION_LONG_PRESS, true, 10_000))
        assertEquals(Decision.IGNORE, p.onKey(KEY_TYPE_VR, ACTION_LONG_PRESS, true, 10_500))
        assertEquals(Decision.IGNORE, p.onKey(KEY_TYPE_VR, ACTION_LONG_PRESS, true, 11_499))
        assertEquals(Decision.ASSISTANT, p.onKey(KEY_TYPE_VR, ACTION_LONG_PRESS, true, 11_500))
    }

    @Test fun ignoredPressDoesNotStartDebounce() {
        val p = VoiceKeyPolicy()
        assertEquals(Decision.IGNORE, p.onKey(KEY_TYPE_VR, ACTION_LONG_PRESS, false, 10_000))
        assertEquals(Decision.ASSISTANT, p.onKey(KEY_TYPE_VR, ACTION_LONG_PRESS, true, 10_100))
    }

    @Test fun projectionIgnoresEverything() {
        val p = VoiceKeyPolicy()
        assertEquals(Decision.IGNORE, p.onKey(KEY_TYPE_VR, ACTION_LONG_PRESS, true, 1_000, projection = true))
        assertEquals(Decision.IGNORE, p.onKey(KEY_TYPE_VR, ACTION_SHORT_RELEASE, true, 3_000, projection = true, assistantActive = true))
        // Projeksiyonda yok sayılan basış debounce başlatmaz.
        assertEquals(Decision.ASSISTANT, p.onKey(KEY_TYPE_VR, ACTION_LONG_PRESS, true, 1_100))
    }

    @Test fun shortPressStopsOurAssistantOnly() {
        val p = VoiceKeyPolicy()
        assertEquals(Decision.STOP_ASSISTANT, p.onKey(KEY_TYPE_VR, ACTION_SHORT_RELEASE, true, 1_000, assistantActive = true))
        assertEquals(Decision.IGNORE, p.onKey(KEY_TYPE_VR, ACTION_SHORT_RELEASE, true, 1_000, assistantActive = false))
        assertEquals(Decision.IGNORE, p.onKey(KEY_TYPE_VR, ACTION_PRESS, true, 1_000, assistantActive = true))
        assertEquals(Decision.IGNORE, p.onKey(KEY_TYPE_VR, ACTION_LONG_RELEASE, true, 1_000, assistantActive = true))
        assertEquals(Decision.IGNORE, p.onKey(KEY_TYPE_VR, ACTION_SHORT_RELEASE, false, 1_000, assistantActive = true))
    }

    @Test fun longPressWhileActiveStillToggles() =
        assertEquals(Decision.ASSISTANT, VoiceKeyPolicy().onKey(KEY_TYPE_VR, ACTION_LONG_PRESS, true, 1_000, assistantActive = true))
}
