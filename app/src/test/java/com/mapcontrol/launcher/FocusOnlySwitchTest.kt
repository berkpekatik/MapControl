package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telefon önceliğinin "yalnızca odak" geçişi: mesaj bildirimi sesi telefonda duraklatılmış şarkıyı başlatmasın. */
class FocusOnlySwitchTest {

    private val fm = 9
    private val bt = OemAudioSource.BT_MUSIC

    @Test fun triedUntilKnownToFail() {
        val s = FocusOnlySwitch()
        assertTrue(s.shouldTry())
        assertNull(s.works)
    }

    @Test fun sourceChangeMarksWorkingAndNoFallback() {
        val s = FocusOnlySwitch()
        s.onRequested()
        s.onSource(bt)
        assertEquals(true, s.works)
        assertFalse(s.confirm(bt, true))
        assertTrue(s.shouldTry())
    }

    @Test fun sourceAlreadyBtAtConfirmWorks() {
        val s = FocusOnlySwitch()
        s.onRequested()
        assertFalse(s.confirm(bt, true))
        assertEquals(true, s.works)
    }

    @Test fun noSourceChangeWhilePlayingFallsBackAndStopsTrying() {
        val s = FocusOnlySwitch()
        s.onRequested()
        assertTrue(s.confirm(fm, true))
        assertEquals(false, s.works)
        assertFalse(s.shouldTry())
    }

    /** Bildirim sesi bitti: eski yola (telefona oynat) düşülmez. */
    @Test fun noSourceChangeAfterShortSoundDoesNotFallBack() {
        val s = FocusOnlySwitch()
        s.onRequested()
        assertFalse(s.confirm(fm, false))
        assertFalse(s.confirm(fm, null))
    }

    @Test fun userActionCancelsConfirm() {
        val s = FocusOnlySwitch()
        s.onRequested()
        s.cancel()
        assertFalse(s.confirm(fm, true))
        assertNull(s.works)
    }

    @Test fun otherSourceDoesNotConfirm() {
        val s = FocusOnlySwitch()
        s.onRequested()
        s.onSource(fm)
        assertNull(s.works)
        assertTrue(s.confirm(fm, true))
    }

    @Test fun confirmOnlyOnce() {
        val s = FocusOnlySwitch()
        s.onRequested()
        assertTrue(s.confirm(fm, true))
        assertFalse(s.confirm(fm, true))
    }
}
