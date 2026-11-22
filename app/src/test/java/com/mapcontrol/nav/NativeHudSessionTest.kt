package com.mapcontrol.nav

import com.mapcontrol.launcher.NavDistance
import com.mapcontrol.launcher.NavManeuver
import com.mapcontrol.launcher.NavigationState
import org.junit.Assert.*
import org.junit.Test

class NativeHudSessionTest {
    private val route = NavigationState(true, NavManeuver.RIGHT,
        distance = NavDistance("300", "m", 300), street = "Atatürk Cd.",
        remainingDistance = NavDistance("18", "km", 18_000), sourcePackage = "google")
    private val frame = NativeHudProtocol.encode(route)!!

    @Test fun startsOnceAndSkipsUnchangedPackets() {
        val sent = mutableListOf<List<Int>>()
        val session = NativeHudSession { sent.add(it.toList()); true }
        assertTrue(session.apply(frame))
        assertEquals(listOf(52, 1, 0), sent.first())
        assertEquals(frame.packets, sent.drop(1))
        val count = sent.size
        assertTrue(session.apply(frame.copy()))
        assertEquals(count, sent.size)
    }

    @Test fun endsAndRepeatsManufacturerClear() {
        val sent = mutableListOf<List<Int>>()
        val session = NativeHudSession { sent.add(it.toList()); true }
        session.apply(frame)
        sent.clear()
        assertTrue(session.apply(null))
        assertTrue(session.needsRepeatClear)
        assertTrue(session.repeatClear())
        assertEquals(listOf(listOf(3, 1, 1), listOf(3, 1, 1)), sent)
        assertFalse(session.hasWork)
    }

    @Test fun newRouteCancelsDelayedClear() {
        val sent = mutableListOf<List<Int>>()
        val session = NativeHudSession { sent.add(it.toList()); true }
        session.apply(frame)
        session.apply(null)
        session.apply(frame)
        val count = sent.size
        assertTrue(session.repeatClear())
        assertEquals(count, sent.size)
    }

    @Test fun partialFailureIsClearedBeforeFullRetry() {
        val sent = mutableListOf<List<Int>>()
        var fail = true
        val session = NativeHudSession {
            sent.add(it.toList())
            !(fail && it[0] == 49)
        }
        assertFalse(session.apply(frame))
        fail = false
        sent.clear()
        assertTrue(session.apply(frame))
        assertEquals(listOf(listOf(3, 1, 1), listOf(52, 1, 0)) + frame.packets, sent)
    }

    @Test fun failedCloseStaysPendingForReconnect() {
        val sent = mutableListOf<List<Int>>()
        var connected = true
        val session = NativeHudSession {
            sent.add(it.toList())
            connected
        }
        session.apply(frame)
        connected = false
        assertFalse(session.apply(null))
        assertTrue(session.hasWork)
        connected = true
        sent.clear()
        assertTrue(session.apply(null))
        assertEquals(listOf(listOf(3, 1, 1)), sent)
        assertTrue(session.needsRepeatClear)
    }

    @Test fun connectionLossDoesNotClearAnUnownedHud() {
        val sent = mutableListOf<List<Int>>()
        val session = NativeHudSession { sent.add(it.toList()); true }
        session.connectionLost()
        session.apply(null)
        assertTrue(sent.isEmpty())
    }

    @Test fun activeConnectionLossRebuildsTheNativeSession() {
        val sent = mutableListOf<List<Int>>()
        val session = NativeHudSession { sent.add(it.toList()); true }
        session.apply(frame)
        sent.clear()
        session.connectionLost()
        assertTrue(session.apply(frame))
        assertEquals(listOf(listOf(3, 1, 1), listOf(52, 1, 0)) + frame.packets, sent)
    }

    @Test fun missingDistanceFieldsClearPreviouslyDisplayedValues() {
        val sent = mutableListOf<List<Int>>()
        val session = NativeHudSession { sent.add(it.toList()); true }
        session.apply(frame)
        sent.clear()
        val remainingOnly = NativeHudProtocol.encode(route.copy(distance = null))!!
        session.apply(remainingOnly)
        assertEquals(listOf(3, 1, 1), sent[0])
        assertEquals(listOf(52, 1, 0), sent[1])
        assertEquals(remainingOnly.packets, sent.drop(2))
        assertFalse(sent.drop(2).any { it[0] == 49 || it[0] == 50 })
    }

    @Test fun providerChangeStartsFreshEvenIfPayloadIsEqual() {
        val sent = mutableListOf<List<Int>>()
        val session = NativeHudSession { sent.add(it.toList()); true }
        session.apply(frame)
        sent.clear()
        session.apply(frame.copy(sourcePackage = "yandex"))
        assertEquals(listOf(3, 1, 1), sent[0])
        assertEquals(listOf(52, 1, 0), sent[1])
    }

    @Test fun processRestartClearsPreviouslyOwnedGuidanceEvenWithoutARoute() {
        val sent = mutableListOf<List<Int>>()
        var owned = true
        val session = NativeHudSession(
            initiallyOwned = owned,
            persistOwnership = { owned = it; true },
        ) { sent.add(it.toList()); true }
        session.apply(null)
        assertTrue(owned)
        session.repeatClear()
        assertFalse(owned)
        assertEquals(listOf(listOf(3, 1, 1), listOf(3, 1, 1)), sent)
    }

    @Test fun ownershipIsSavedBeforeStartAndRetainedOnPartialFailure() {
        val events = mutableListOf<String>()
        var owned = false
        val session = NativeHudSession(persistOwnership = {
            owned = it
            events.add("owned=$it")
            true
        }) {
            events.add("cmd=${it[0]}")
            it[0] != 49
        }
        assertFalse(session.apply(frame))
        assertTrue(owned)
        assertEquals(listOf("owned=true", "cmd=52"), events.take(2))
        assertTrue(session.apply(null))
        assertTrue(owned)
        assertTrue(session.repeatClear())
        assertFalse(owned)
    }

    @Test fun restartingOwnedSessionDoesNotCommitTheSameOwnershipAgain() {
        val ownership = mutableListOf<Boolean>()
        val session = NativeHudSession(persistOwnership = { ownership.add(it); true }) { true }
        assertTrue(session.apply(frame))
        session.connectionLost()
        assertTrue(session.apply(frame))
        assertEquals(listOf(true), ownership)
    }

    @Test fun failedOwnershipWritePreventsStartingAnUnrecoverableSession() {
        val sent = mutableListOf<List<Int>>()
        val session = NativeHudSession(persistOwnership = { false }) { sent.add(it.toList()); true }
        assertFalse(session.apply(frame))
        assertTrue(sent.isEmpty())
    }
}
