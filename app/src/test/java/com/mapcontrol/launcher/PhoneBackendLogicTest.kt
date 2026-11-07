package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PHONE-BACKEND saf mantığı: depo birleştirme, OEM sayaç başlangıcı, sağlam kapatma, edilgen parça toplama. */
class PhoneBackendLogicTest {

    private fun c(id: String, name: String, n: Int, photo: String? = null) =
        PhoneContact(id, name, "0555 000 00 %02d".format(n), photoKey = photo)

    private fun r(type: RecentType, n: Int, t: Long, name: String? = null) =
        PhoneRecent(type, name, "0555 000 00 %02d".format(n), t)

    // ---- DirectoryStore ----

    @Test fun emptyReadKeepsStore() {
        val s = DirectoryStore()
        assertTrue(s.merge(listOf(c("v:1", "Zeynep", 1), c("v:2", "Ayşe", 2)), listOf(r(RecentType.OUTGOING, 1, 10))))
        val v = s.version
        assertFalse(s.merge(emptyList(), emptyList()))
        assertFalse(s.merge(null, null))
        assertEquals(2, s.contacts.size)
        assertEquals(1, s.recents.size)
        assertEquals(v, s.version)
        // Türkçe sıralama.
        assertEquals(listOf("Ayşe", "Zeynep"), s.contacts.map { it.name })
    }

    @Test fun sameReadDoesNotBumpVersion() {
        val s = DirectoryStore()
        s.merge(listOf(c("v:1", "A", 1)), null)
        val v = s.version
        assertFalse(s.merge(listOf(c("v:1", "A", 1)), null))
        assertEquals(v, s.version)
        assertTrue(s.merge(null, listOf(r(RecentType.MISSED, 1, 5))))
        assertEquals(v + 1, s.version)
    }

    @Test fun newDeviceResetsStore() {
        val s = DirectoryStore()
        assertFalse(s.onDevice(111)) // ilk cihaz: önceden okunan veri onundur
        s.merge(listOf(c("v:1", "A", 1)), listOf(r(RecentType.INCOMING, 1, 1)))
        assertFalse(s.onDevice(111))
        assertFalse(s.onDevice(null))
        assertTrue(s.hasData)
        val v = s.version
        assertTrue(s.onDevice(222))
        assertFalse(s.hasData)
        assertTrue(s.version > v)
    }

    @Test fun recentsSortedCappedAndAnnotated() {
        val s = DirectoryStore()
        val rows = (1..150).map { r(RecentType.OUTGOING, it % 50, it.toLong()) }
        s.merge(listOf(c("v:7", "Yedi", 7, photo = "v:7")), rows)
        assertEquals(DirectoryStore.MAX_RECENTS, s.recents.size)
        assertEquals(150L, s.recents.first().timeMs)
        val seven = s.recents.first { it.number.endsWith("07") }
        assertEquals("Yedi", seven.name)
        assertEquals("v:7", seven.contactId)
        assertEquals("v:7", seven.photoKey)
        // Kişiler sonradan gelirse eşleşme yenilenir.
        val s2 = DirectoryStore()
        s2.merge(null, listOf(r(RecentType.MISSED, 3, 1)))
        assertNull(s2.recents.first().name)
        s2.merge(listOf(c("v:3", "Üç", 3)), null)
        assertEquals("Üç", s2.recents.first().name)
    }

    @Test fun duplicateIdsCollapse() {
        val s = DirectoryStore()
        s.merge(listOf(c("v:1", "A", 1), c("v:1", "A", 2)), null)
        assertEquals(1, s.contacts.size)
    }

    // ---- nextCall: OEM başlangıcı, ses yönü ve sessiz sıfırlama, fotoğraf ----

    @Test fun nextCallUsesOemStartOnFirstActive() {
        val active = PhoneStateMap.nextCall(CallState.IDLE, CallKind.ACTIVE, null, "1", 100_000, oemStartElapsed = 40_000)
        assertEquals(40_000L, active.startedAtMs)
        // Sürerken korunur, yeni OEM değeri ezmez.
        assertEquals(40_000L, PhoneStateMap.nextCall(active, CallKind.ACTIVE, null, null, 102_000, oemStartElapsed = 41_000).startedAtMs)
        // OEM yoksa şimdiki zaman.
        assertEquals(5_000L, PhoneStateMap.nextCall(CallState.IDLE, CallKind.ACTIVE, null, null, 5_000).startedAtMs)
        // Gelen → konuşma: OEM başlangıcı var.
        val inc = PhoneStateMap.nextCall(CallState.IDLE, CallKind.INCOMING, null, "1", 1_000)
        assertEquals(900L, PhoneStateMap.nextCall(inc, CallKind.ACTIVE, null, null, 2_000, oemStartElapsed = 900).startedAtMs)
    }

    @Test fun audioAndMuteResetAcrossCalls() {
        val live = CallState(CallKind.ACTIVE, null, "1", 1L, muted = true, audioOnPhone = true)
        val still = PhoneStateMap.nextCall(live, CallKind.ACTIVE, null, null, 2)
        assertTrue(still.audioOnPhone)
        assertTrue(still.muted)
        val ended = PhoneStateMap.nextCall(still, CallKind.ENDED, null, null, 3)
        assertFalse(ended.audioOnPhone)
        assertFalse(ended.muted)
        val nextCall = PhoneStateMap.nextCall(ended, CallKind.INCOMING, null, "2", 4)
        assertFalse(nextCall.audioOnPhone)
        assertFalse(nextCall.muted)
    }

    @Test fun photoKeyKeptInSameCallOnly() {
        val inc = PhoneStateMap.nextCall(CallState.IDLE, CallKind.INCOMING, "A", "1", 0, photoKey = "call:u1")
        assertEquals("call:u1", inc.photoKey)
        val act = PhoneStateMap.nextCall(inc, CallKind.ACTIVE, null, null, 1)
        assertEquals("call:u1", act.photoKey)
        val ended = PhoneStateMap.nextCall(act, CallKind.ENDED, null, null, 2)
        assertEquals("call:u1", ended.photoKey)
        assertNull(PhoneStateMap.nextCall(ended, CallKind.INCOMING, null, "2", 3).photoKey)
        assertFalse(inc.toString().contains("A"))
    }

    // ---- HangUpPlan ----

    @Test fun hangUpPlan() {
        assertEquals(HangUpPlan.Decision.CLEAR_LOCAL, HangUpPlan.decide(false, true))
        assertEquals(HangUpPlan.Decision.CLEAR_LOCAL, HangUpPlan.decide(false, false))
        assertEquals(HangUpPlan.Decision.SEND, HangUpPlan.decide(true, true))
        assertEquals(HangUpPlan.Decision.SEND, HangUpPlan.decide(true, false))
        assertEquals(HangUpPlan.Decision.SEND, HangUpPlan.decide(null, true))
        assertEquals(HangUpPlan.Decision.FAIL, HangUpPlan.decide(null, false))
        assertEquals(HangUpPlan.Decision.CLEAR_LOCAL, HangUpPlan.onTimeout(false))
        assertEquals(HangUpPlan.Decision.SEND, HangUpPlan.onTimeout(true))
        assertEquals(HangUpPlan.Decision.SEND, HangUpPlan.onTimeout(null))
        assertEquals(HangUpPlan.Decision.CLEAR_LOCAL, HangUpPlan.onExhausted(false))
        assertEquals(HangUpPlan.Decision.FAIL, HangUpPlan.onExhausted(true))
        assertEquals(HangUpPlan.Decision.FAIL, HangUpPlan.onExhausted(null))
    }

    @Test fun endedListMeansNoneAndClearsCall() {
        // Boş liste (OEM total 0) → NONE; görüşmeden NONE'a geçiş kısa ENDED gösterir.
        assertEquals(CallKind.NONE, PhoneStateMap.fromVdDialInfos(emptyList()))
        assertEquals(CallKind.ENDED, PhoneStateMap.effectiveKind(CallKind.ACTIVE, CallKind.NONE))
        assertEquals("RawCall(kind=NONE, start=false, photo=false)", RawCall.NONE.toString())
    }

    // ---- Yoklama, DTMF, edilgen parça (key null) ----

    @Test fun pollPredicate() {
        for (k in listOf(CallKind.INCOMING, CallKind.DIALING, CallKind.ACTIVE)) assertTrue(CallPoll.shouldPoll(k, true))
        for (k in listOf(CallKind.NONE, CallKind.ENDED)) assertFalse(CallPoll.shouldPoll(k, true))
        assertFalse(CallPoll.shouldPoll(CallKind.ACTIVE, false))
        assertEquals(2_000L, CallPoll.INTERVAL_MS)
    }

    @Test fun dtmf() {
        assertEquals("49", DtmfCode.vdNumber('1'))
        assertEquals("35", DtmfCode.vdNumber('#'))
        assertNull(DtmfCode.vdNumber('x'))
    }

    @Test fun passiveCollectorKeyNull() {
        val acc = VdListCollector<Int>(null)
        // Bizim etiketli önbellek yanıtı edilgen toplayıcıya karışmaz.
        assertEquals(VdListCollector.Offer.OTHER_KEY, acc.offer("ml-1", 9, 20, (0..9).toList()))
        assertEquals(VdListCollector.Offer.ACCEPTED, acc.offer(null, 9, 20, (0..9).toList()))
        assertFalse(acc.finished)
        assertEquals(VdListCollector.Offer.ACCEPTED, acc.offer(null, 19, 20, (10..19).toList()))
        assertTrue(acc.finished)
        assertEquals(20, acc.result()!!.size)
        assertEquals(VdListCollector.Offer.AFTER_FINISH, acc.offer(null, 19, 20, listOf(1)))
        // Hiç parça yoksa null (boş rehber sayılmaz).
        assertNull(VdListCollector<Int>(null).result())
    }
}
