package com.mapcontrol.launcher

import com.mapcontrol.launcher.PhonePriority.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kilit açılınca gelen anlık seslerin radyoyu kesmemesi: parça bilgisi filtresi ve kısa çalmada geri dönüş. */
class PhonePriorityTrackInfoTest {

    private val fm = 9

    private fun primed() = PhonePriority().also {
        it.onBtState(false, 0L, fm, { true }, { false })
    }

    @Test fun noTrackInfoDoesNotSwitch() {
        val p = primed()
        assertEquals(Decision.NO_TRACK_INFO, p.onBtState(true, 100_000L, fm, { true }, { false }, hasTrackInfo = { false }))
    }

    @Test fun lateTrackInfoSwitchesOnRetry() {
        val p = primed()
        p.onBtState(true, 100_000L, fm, { true }, { false }, hasTrackInfo = { false })
        assertEquals(Decision.NO_TRACK_INFO, p.retry(100_300L, fm, { true }, { false }, { false }))
        assertEquals(Decision.SWITCH, p.retry(100_700L, fm, { true }, { false }, { true }))
        // Bir kez geçti: tekrar denenmez.
        assertEquals(Decision.NO_TRANSITION, p.retry(100_800L, fm, { true }, { false }, { true }))
    }

    @Test fun retryGivesUpAfterWaitOrPause() {
        val late = primed()
        late.onBtState(true, 100_000L, fm, { true }, { false }, hasTrackInfo = { false })
        assertEquals(Decision.NO_TRANSITION, late.retry(101_000L, fm, { true }, { false }, { true }))

        val paused = primed()
        paused.onBtState(true, 100_000L, fm, { true }, { false }, hasTrackInfo = { false })
        paused.onBtState(false, 100_200L, fm, { true }, { false })
        assertEquals(Decision.NO_TRANSITION, paused.retry(100_300L, fm, { true }, { false }, { true }))
    }

    @Test fun otherGuardsWinOverTrackInfo() {
        // Ayar kapalıyken bilgi beklenmez.
        assertEquals(Decision.DISABLED, primed().onBtState(true, 100_000L, fm, { false }, { false }, hasTrackInfo = { false }))
    }

    @Test fun retryWithoutPendingIsNoop() {
        assertEquals(Decision.NO_TRANSITION, primed().retry(100_000L, fm, { true }, { false }, { true }))
    }

    @Test fun placeholderTitlesAreNotTrackInfo() {
        assertFalse(TrackInfo.present(null, null))
        assertFalse(TrackInfo.present(" ", "Not Provided"))
        assertFalse(TrackInfo.present("<unknown>", "unknown"))
        assertTrue(TrackInfo.present("Bir Şarkı", null))
        assertTrue(TrackInfo.present(null, "Sanatçı"))
    }
}

class PriorityRevertTest {

    private val fm = 9
    private val bt = OemAudioSource.BT_MUSIC

    @Test fun shortBlipRevertsToRadio() {
        val r = PriorityRevert()
        r.onSwitched(fm, 0L)
        assertFalse(r.onBtState(true, 500L))
        assertTrue(r.onBtState(false, 2_000L)) // telefon sesi bitti
        assertNull(r.due(3_000L, false, bt)) // henüz oturmadı
        assertEquals(fm, r.due(3_600L, false, bt))
        assertFalse(r.pending)
    }

    @Test fun resumedPlaybackKeepsBt() {
        val r = PriorityRevert()
        r.onSwitched(fm, 0L)
        r.onBtState(false, 2_000L)
        r.onBtState(true, 2_500L) // parça arası kısa durma
        assertNull(r.due(3_600L, true, bt))
        assertTrue(r.pending)
    }

    @Test fun longListeningIsNotReverted() {
        val r = PriorityRevert()
        r.onSwitched(fm, 0L)
        r.onBtState(true, 5_000L)
        assertFalse(r.onBtState(false, PriorityRevert.WINDOW_MS + 1_000L))
        assertFalse(r.pending)
    }

    @Test fun userChoiceCancels() {
        val r = PriorityRevert()
        r.onSwitched(fm, 0L)
        r.onBtState(false, 1_000L)
        r.cancel()
        assertNull(r.due(5_000L, false, bt))
    }

    @Test fun noRevertWhenSourceAlreadyMovedOrSwitchFailed() {
        val r = PriorityRevert()
        r.onSwitched(fm, 0L)
        r.onBtState(false, 1_000L)
        assertNull(r.due(3_000L, false, fm))
        assertFalse(r.pending)
    }

    @Test fun onlyOemOwnSourcesAreRestored() {
        val r = PriorityRevert()
        r.onSwitched(99, 0L) // bilinmeyen / üçüncü taraf kaynak
        assertFalse(r.onBtState(false, 1_000L))
        assertNull(r.due(5_000L, false, bt))
    }
}
