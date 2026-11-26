package com.mapcontrol.nav

import com.mapcontrol.launcher.NavDistance
import com.mapcontrol.launcher.NavManeuver
import com.mapcontrol.launcher.NavigationState
import org.junit.Assert.*
import org.junit.Test

class NativeHudSessionTest {
    private var now = 1_000L
    private val route = NavigationState(true, NavManeuver.RIGHT,
        distance = NavDistance("300", "m", 300), street = "Atatürk Cd.",
        remainingDistance = NavDistance("18", "km", 18_000), sourcePackage = "google")
    private val frame = NativeHudProtocol.encode(route)!!
    private val changed = NativeHudProtocol.encode(route.copy(
        distance = NavDistance("400", "m", 400)))!!

    private fun newSession(
        initiallyOwned: Boolean = false,
        persistOwnership: (Boolean) -> Boolean = { true },
        finish: () -> Boolean = { true },
        send: (IntArray) -> Boolean,
    ) = NativeHudSession(
        initiallyOwned = initiallyOwned,
        blankDelayMs = 50L,
        cleanupDelayMs = 20_000L,
        now = { now },
        persistOwnership = persistOwnership,
        finish = finish,
        send = send,
    )

    private fun recordingSession(
        initiallyOwned: Boolean = false,
        persistOwnership: (Boolean) -> Boolean = { true },
        finish: () -> Boolean = { true },
        sender: (IntArray) -> Boolean = { true },
        sent: MutableList<List<Int>>,
    ) = newSession(initiallyOwned, persistOwnership, finish) {
        sent.add(it.toList())
        sender(it)
    }

    @Test fun startsOnceAndSkipsUnchangedPackets() {
        val sent = mutableListOf<List<Int>>()
        val session = recordingSession(sent = sent)
        assertTrue(session.apply(frame))
        assertEquals(listOf(listOf(52, 1, 0)) + frame.packets, sent)
        assertTrue(session.apply(frame.copy()))
        assertEquals((1 + frame.packets.size), sent.size)
    }

    @Test fun changedFrameSendsStartAndAllThreeDataPacketsWithoutLegacyClear() {
        val sent = mutableListOf<List<Int>>()
        val session = recordingSession(sent = sent)
        session.apply(frame)
        sent.clear()
        assertTrue(session.apply(changed))
        assertEquals(listOf(listOf(52, 1, 0)) + changed.packets, sent)
        assertFalse(sent.any { it.first() == 3 })
    }

    @Test fun repeatedNullDoesNotExtendTheEndDeadline() {
        val sent = mutableListOf<List<Int>>()
        val session = recordingSession(sent = sent)
        session.apply(frame)
        sent.clear()
        now = 1_000L
        assertTrue(session.apply(null))
        assertEquals(50L, session.nextActionDelayMs())
        now = 1_030L
        assertTrue(session.apply(null))
        assertEquals(20L, session.nextActionDelayMs())
        now = 1_049L
        assertTrue(session.tick())
        assertTrue(sent.isEmpty())
        now = 1_050L
        assertTrue(session.tick())
        assertEquals(NativeHudProtocol.blank().packets, sent)
    }

    @Test fun naturalEndBlanksAfter50msAndStopsAfter20s() {
        val sent = mutableListOf<List<Int>>()
        var finished = 0
        var owned = true
        val session = recordingSession(
            initiallyOwned = true,
            persistOwnership = { owned = it; true },
            finish = { finished++; true },
            sent = sent,
        )
        session.apply(frame)
        sent.clear()
        now = 1_000L
        session.apply(null)
        now = 1_049L
        assertTrue(session.tick())
        assertTrue(sent.isEmpty())
        assertEquals(0, finished)
        now = 1_050L
        assertTrue(session.tick())
        assertEquals(NativeHudProtocol.blank().packets, sent)
        assertFalse(sent.any { it.first() == 52 })
        assertFalse(sent.any { it.first() == 3 })
        assertEquals(0, finished)
        now = 20_999L
        assertTrue(session.tick())
        assertEquals(0, finished)
        now = 21_000L
        assertTrue(session.tick())
        assertEquals(1, finished)
        assertFalse(owned)
        assertFalse(session.hasWork)
    }

    @Test fun sameRouteBeforeBlankCancelsPendingEnd() {
        val sent = mutableListOf<List<Int>>()
        var finished = 0
        val session = recordingSession(finish = { finished++; true }, sent = sent)
        session.apply(frame)
        sent.clear()
        now = 1_000L
        session.apply(null)
        now = 1_030L
        assertTrue(session.apply(frame.copy()))
        assertTrue(sent.isEmpty())
        now = 21_000L
        assertTrue(session.tick())
        assertEquals(0, finished)
        assertTrue(sent.isEmpty())
        assertNull(session.nextActionDelayMs())
    }

    @Test fun sameRouteAfterBlankStartsAFreshFrame() {
        val sent = mutableListOf<List<Int>>()
        var finished = 0
        val session = recordingSession(finish = { finished++; true }, sent = sent)
        session.apply(frame)
        session.apply(null)
        now = 1_050L
        session.tick()
        sent.clear()
        now = 1_100L
        assertTrue(session.apply(frame))
        assertEquals(listOf(listOf(52, 1, 0)) + frame.packets, sent)
        now = 21_000L
        assertTrue(session.tick())
        assertEquals(0, finished)
    }

    @Test fun newRouteCancelsPendingEnd() {
        val sent = mutableListOf<List<Int>>()
        var finished = 0
        val session = recordingSession(finish = { finished++; true }, sent = sent)
        session.apply(frame)
        session.apply(null)
        now = 1_020L
        sent.clear()
        assertTrue(session.apply(changed))
        assertEquals(listOf(listOf(52, 1, 0)) + changed.packets, sent)
        now = 21_000L
        session.tick()
        assertEquals(0, finished)
    }

    @Test fun partialSendFailureRetriesFullFrameWithoutLegacyClear() {
        val sent = mutableListOf<List<Int>>()
        var fail = true
        val session = recordingSession(sender = { !(fail && it[0] == 49) }, sent = sent)
        assertFalse(session.apply(frame))
        assertEquals(listOf(52, 48, 49), sent.map { it[0] })
        assertTrue(session.hasWork)
        fail = false
        sent.clear()
        assertTrue(session.apply(frame))
        assertEquals(listOf(listOf(52, 1, 0)) + frame.packets, sent)
        assertFalse(sent.any { it.first() == 3 })
    }

    @Test fun connectionLossInvalidatesFrameAndRetriesFullStartAndData() {
        val sent = mutableListOf<List<Int>>()
        val session = recordingSession(sent = sent)
        session.apply(frame)
        sent.clear()
        session.connectionLost()
        assertTrue(session.apply(frame))
        assertEquals(listOf(listOf(52, 1, 0)) + frame.packets, sent)
        assertFalse(sent.any { it.first() == 3 })
    }

    @Test fun failedBlankStaysPendingAndRetriesWhenConnected() {
        val sent = mutableListOf<List<Int>>()
        var connected = true
        val session = recordingSession(sender = { connected }, sent = sent)
        session.apply(frame)
        session.apply(null)
        now = 1_050L
        connected = false
        assertFalse(session.tick())
        assertTrue(session.hasWork)
        assertEquals(0L, session.nextActionDelayMs())
        connected = true
        sent.clear()
        assertTrue(session.tick())
        assertEquals(NativeHudProtocol.blank().packets, sent)
        assertTrue(session.hasWork)
        // 20 s bitişi henüz gelmedi.
        assertEquals(19_950L, session.nextActionDelayMs())
    }

    @Test fun sameRouteIsRestoredAfterPartiallyFailedBlank() {
        val sent = mutableListOf<List<Int>>()
        var failBlank = false
        var finished = 0
        val session = recordingSession(
            sender = { !(failBlank && it[0] == 49) },
            finish = { finished++; true },
            sent = sent,
        )
        session.apply(frame)
        session.apply(null)
        now = 1_050L
        failBlank = true
        assertFalse(session.tick())
        sent.clear()
        failBlank = false
        assertTrue(session.apply(frame.copy()))
        assertEquals(listOf(NativeHudProtocol.start().toList()) + frame.packets, sent)
        now = 21_000L
        assertTrue(session.tick())
        assertEquals(0, finished)
    }

    @Test fun failedFinishStaysPendingAndPersistsOwnershipOnlyAfterSuccess() {
        val persisted = mutableListOf<Boolean>()
        var owned = true
        var finishOk = false
        val session = recordingSession(
            initiallyOwned = true,
            persistOwnership = { persisted.add(it); owned = it; true },
            finish = { finishOk },
            sent = mutableListOf(),
        )
        session.apply(frame)
        session.apply(null)
        now = 1_050L
        session.tick()
        now = 21_000L
        assertFalse(session.tick())
        assertTrue(session.hasWork)
        assertTrue(owned)
        assertFalse(persisted.contains(false))
        finishOk = true
        assertTrue(session.tick())
        assertFalse(owned)
        assertEquals(listOf(false), persisted)
        assertFalse(session.hasWork)
    }

    @Test fun explicitShutdownBlanksAndFinishesImmediately() {
        val sent = mutableListOf<List<Int>>()
        var finished = 0
        var owned = true
        val session = recordingSession(
            initiallyOwned = true,
            persistOwnership = { owned = it; true },
            finish = { finished++; true },
            sent = sent,
        )
        session.apply(frame)
        sent.clear()
        now = 1_010L
        assertTrue(session.shutdown())
        assertEquals(NativeHudProtocol.blank().packets, sent)
        assertEquals(1, finished)
        assertFalse(owned)
        assertFalse(session.hasWork)
    }

    @Test fun explicitShutdownDoesNotWaitForNaturalEndDeadline() {
        val sent = mutableListOf<List<Int>>()
        var finished = 0
        val session = recordingSession(finish = { finished++; true }, sent = sent)
        session.apply(frame)
        session.apply(null)
        now = 1_010L
        sent.clear()
        assertTrue(session.shutdown())
        assertEquals(NativeHudProtocol.blank().packets, sent)
        assertEquals(1, finished)
        assertFalse(session.hasWork)
    }

    @Test fun unownedInactiveSessionDoesNotSendAnything() {
        val sent = mutableListOf<List<Int>>()
        var finished = 0
        val session = recordingSession(finish = { finished++; true }, sent = sent)
        assertTrue(session.apply(null))
        assertTrue(sent.isEmpty())
        assertEquals(0, finished)
        assertNull(session.nextActionDelayMs())
        assertFalse(session.hasWork)
    }

    @Test fun processRestartWithOwnedHudCleansUpWithoutARoute() {
        val sent = mutableListOf<List<Int>>()
        var owned = true
        var finished = 0
        val session = recordingSession(
            initiallyOwned = true,
            persistOwnership = { owned = it; true },
            finish = { finished++; true },
            sent = sent,
        )
        assertTrue(session.apply(null))
        now = 1_050L
        session.tick()
        assertEquals(NativeHudProtocol.blank().packets, sent)
        now = 21_000L
        session.tick()
        assertEquals(1, finished)
        assertFalse(owned)
    }

    @Test fun ownershipIsPersistedTrueBeforeFirstStartAndNeverRepeated() {
        val events = mutableListOf<String>()
        var owned = false
        val session = newSession(persistOwnership = {
            owned = it
            events.add("owned=$it")
            true
        }) {
            events.add("cmd=${it[0]}")
            true
        }
        assertTrue(session.apply(frame))
        assertTrue(session.apply(frame.copy()))
        assertTrue(session.apply(changed))
        assertEquals(listOf("owned=true", "cmd=52"), events.take(2))
        assertEquals(1, events.count { it == "owned=true" })
        assertTrue(owned)
    }

    @Test fun failedOwnershipWritePreventsStarting() {
        val sent = mutableListOf<List<Int>>()
        val session = newSession(persistOwnership = { false }) {
            sent.add(it.toList())
            true
        }
        assertFalse(session.apply(frame))
        assertTrue(sent.isEmpty())
    }

    @Test fun failedOwnershipClearAfterStopKeepsCleanupPending() {
        val persisted = mutableListOf<Boolean>()
        var allowClear = false
        var finished = 0
        val session = recordingSession(
            initiallyOwned = true,
            persistOwnership = { value -> persisted.add(value); value || allowClear },
            finish = { finished++; true },
            sent = mutableListOf(),
        )
        session.apply(frame)
        session.apply(null)
        now = 1_050L
        session.tick()
        now = 21_000L
        assertFalse(session.tick())
        assertTrue(session.hasWork)
        assertEquals(1, finished)
        allowClear = true
        assertTrue(session.tick())
        assertFalse(session.hasWork)
    }

    /** Araçta 2026-11-24: bitiş yayınından 1 s sonra gönderilen kare HUD'da görünmedi. */
    private fun finishedSession(sent: MutableList<List<Int>>): NativeHudSession {
        val session = recordingSession(sent = sent)
        session.apply(frame)
        now = 1_000L
        session.apply(null)
        now = 1_050L
        session.tick()
        now = 21_000L
        assertTrue(session.tick())
        sent.clear()
        return session
    }

    @Test fun frameRightAfterFinishIsResentOnceWhenTheHudHasSettled() {
        val sent = mutableListOf<List<Int>>()
        val session = finishedSession(sent)
        now = 22_000L
        assertTrue(session.apply(frame))
        assertEquals(listOf(listOf(52, 1, 0)) + frame.packets, sent)
        assertEquals(2_000L, session.nextActionDelayMs())
        sent.clear()
        now = 23_999L
        assertTrue(session.apply(frame))
        assertTrue(session.tick())
        assertTrue(sent.isEmpty())
        now = 24_000L
        assertTrue(session.apply(frame))
        assertTrue(session.tick())
        assertEquals(listOf(listOf(52, 1, 0)) + frame.packets, sent)
        sent.clear()
        now = 30_000L
        assertTrue(session.apply(frame))
        assertTrue(session.tick())
        assertTrue(sent.isEmpty())
        assertNull(session.nextActionDelayMs())
    }

    @Test fun frameLongAfterFinishIsNotResent() {
        val sent = mutableListOf<List<Int>>()
        val session = finishedSession(sent)
        now = 24_000L
        assertTrue(session.apply(frame))
        assertNull(session.nextActionDelayMs())
        sent.clear()
        now = 30_000L
        assertTrue(session.tick())
        assertTrue(sent.isEmpty())
    }

    @Test fun routeEndingInsideTheSettleWindowDoesNotResend() {
        val sent = mutableListOf<List<Int>>()
        val session = finishedSession(sent)
        now = 22_000L
        session.apply(frame)
        now = 22_100L
        session.apply(null)
        now = 22_150L
        session.tick()
        sent.clear()
        now = 24_500L
        assertTrue(session.tick())
        assertTrue(sent.isEmpty())
    }
}
