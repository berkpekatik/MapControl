package com.mapcontrol.launcher

import com.mapcontrol.launcher.PhoneBackendChooser.Caps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Sahte veri: Ayşe/Mehmet/0555 000 00 xx (gerçek kişi yok). */
class PhoneLogicTest {

    private fun lite(vararg states: Int) = states.map { DialInfoLite(it) }

    // ---- VDBus durum eşlemesi ----

    @Test fun vdSingleStates() {
        assertEquals(CallKind.ACTIVE, PhoneStateMap.fromVdDialInfos(lite(0)))
        assertEquals(CallKind.ACTIVE, PhoneStateMap.fromVdDialInfos(lite(1)))
        assertEquals(CallKind.DIALING, PhoneStateMap.fromVdDialInfos(lite(2)))
        assertEquals(CallKind.DIALING, PhoneStateMap.fromVdDialInfos(lite(3)))
        assertEquals(CallKind.INCOMING, PhoneStateMap.fromVdDialInfos(lite(4)))
        assertEquals(CallKind.INCOMING, PhoneStateMap.fromVdDialInfos(lite(5)))
        assertEquals(CallKind.ACTIVE, PhoneStateMap.fromVdDialInfos(lite(6)))
        assertEquals(CallKind.ENDED, PhoneStateMap.fromVdDialInfos(lite(7)))
        assertEquals(CallKind.ENDED, PhoneStateMap.fromVdDialInfos(lite(8)))
        assertEquals(CallKind.NONE, PhoneStateMap.fromVdDialInfos(emptyList()))
        assertEquals(CallKind.NONE, PhoneStateMap.fromVdDialInfos(lite(99)))
    }

    @Test fun vdMultiCallPriority() {
        assertEquals(CallKind.INCOMING, PhoneStateMap.fromVdDialInfos(lite(0, 5)))
        assertEquals(CallKind.ACTIVE, PhoneStateMap.fromVdDialInfos(lite(7, 1)))
        assertEquals(CallKind.DIALING, PhoneStateMap.fromVdDialInfos(lite(7, 3)))
        assertEquals(CallKind.ACTIVE, PhoneStateMap.fromVdDialInfos(lite(2, 0)))
    }

    @Test fun relevantIndexPointsAtWinningEntry() {
        assertEquals(1, PhoneStateMap.relevantIndex(lite(0, 5)))
        assertEquals(0, PhoneStateMap.relevantIndex(lite(0, 7)))
        assertEquals(-1, PhoneStateMap.relevantIndex(emptyList()))
    }

    @Test fun hfpStates() {
        assertEquals(CallKind.ACTIVE, PhoneStateMap.fromHfpClientCallState(0))
        assertEquals(CallKind.ACTIVE, PhoneStateMap.fromHfpClientCallState(1))
        assertEquals(CallKind.DIALING, PhoneStateMap.fromHfpClientCallState(2))
        assertEquals(CallKind.DIALING, PhoneStateMap.fromHfpClientCallState(3))
        assertEquals(CallKind.INCOMING, PhoneStateMap.fromHfpClientCallState(4))
        assertEquals(CallKind.INCOMING, PhoneStateMap.fromHfpClientCallState(5))
        assertEquals(CallKind.ACTIVE, PhoneStateMap.fromHfpClientCallState(6))
        assertEquals(CallKind.ENDED, PhoneStateMap.fromHfpClientCallState(7))
        assertEquals(CallKind.NONE, PhoneStateMap.fromHfpClientCallState(42))
    }

    @Test fun callLogTypes() {
        assertEquals(RecentType.INCOMING, PhoneStateMap.recentType(1))
        assertEquals(RecentType.OUTGOING, PhoneStateMap.recentType(2))
        assertEquals(RecentType.MISSED, PhoneStateMap.recentType(3))
        assertNull(PhoneStateMap.recentType(4)) // sesli mesaj
        assertEquals(RecentType.MISSED, PhoneStateMap.recentType(5)) // reddedilen
        assertNull(PhoneStateMap.recentType(6))
    }

    @Test fun vdRecordTypes() {
        assertEquals(RecentType.MISSED, PhoneStateMap.recentTypeFromVd(1))
        assertEquals(RecentType.INCOMING, PhoneStateMap.recentTypeFromVd(2))
        assertEquals(RecentType.OUTGOING, PhoneStateMap.recentTypeFromVd(3))
        assertNull(PhoneStateMap.recentTypeFromVd(0))
    }

    @Test fun effectiveKindShowsEndedOnceAndNeverReopens() {
        assertEquals(CallKind.ENDED, PhoneStateMap.effectiveKind(CallKind.ACTIVE, CallKind.NONE))
        assertEquals(CallKind.ENDED, PhoneStateMap.effectiveKind(CallKind.INCOMING, CallKind.NONE))
        assertEquals(CallKind.ENDED, PhoneStateMap.effectiveKind(CallKind.ENDED, CallKind.NONE))
        assertEquals(CallKind.NONE, PhoneStateMap.effectiveKind(CallKind.NONE, CallKind.ENDED))
        assertEquals(CallKind.NONE, PhoneStateMap.effectiveKind(CallKind.NONE, CallKind.NONE))
        assertEquals(CallKind.INCOMING, PhoneStateMap.effectiveKind(CallKind.NONE, CallKind.INCOMING))
        assertEquals(CallKind.INCOMING, PhoneStateMap.effectiveKind(CallKind.ENDED, CallKind.INCOMING))
    }

    // ---- nextCall ----

    @Test fun nextCallTimerStartsKeepsAndResets() {
        val incoming = PhoneStateMap.nextCall(CallState.IDLE, CallKind.INCOMING, "Ayşe", "0555 000 00 01", 1_000)
        assertEquals(0L, incoming.startedAtMs)
        val active = PhoneStateMap.nextCall(incoming, CallKind.ACTIVE, null, null, 5_000)
        assertEquals(5_000L, active.startedAtMs)
        assertEquals("Ayşe", active.name)
        assertEquals("0555 000 00 01", active.number)
        val still = PhoneStateMap.nextCall(active, CallKind.ACTIVE, null, null, 9_000)
        assertEquals(5_000L, still.startedAtMs)
        val ended = PhoneStateMap.nextCall(still, CallKind.ENDED, null, null, 12_000)
        assertEquals(0L, ended.startedAtMs)
        assertEquals("Ayşe", ended.name)
        val none = PhoneStateMap.nextCall(ended, CallKind.NONE, null, null, 13_000)
        assertEquals(CallState.IDLE, none)
    }

    @Test fun nextCallMuteOnlyWhileInCall() {
        val muted = CallState(CallKind.ACTIVE, "Ayşe", "0555 000 00 01", 5_000, muted = true)
        assertTrue(PhoneStateMap.nextCall(muted, CallKind.ACTIVE, null, null, 6_000).muted)
        assertFalse(PhoneStateMap.nextCall(muted, CallKind.ENDED, null, null, 6_000).muted)
        assertFalse(PhoneStateMap.nextCall(muted, CallKind.NONE, null, null, 6_000).muted)
    }

    @Test fun nextCallNewNameReplacesOld() {
        val a = CallState(CallKind.INCOMING, "Ayşe", "0555 000 00 01")
        val b = PhoneStateMap.nextCall(a, CallKind.INCOMING, "Mehmet", "0555 000 00 02", 0)
        assertEquals("Mehmet", b.name)
        assertEquals("0555 000 00 02", b.number)
    }

    @Test fun nextCallAfterEndedUnknownCallerIsFresh() {
        val ended = CallState(CallKind.ENDED, "Ayşe", "0555 000 00 01")
        val hidden = PhoneStateMap.nextCall(ended, CallKind.INCOMING, null, null, 0)
        assertNull(hidden.name)
        assertNull(hidden.number)
        val other = PhoneStateMap.nextCall(ended, CallKind.INCOMING, null, "0555 000 00 09", 0)
        assertNull(other.name)
        assertEquals("0555 000 00 09", other.number)
    }

    @Test fun nextCallWaitingCallIsFreshAndKeepsTimer() {
        val active = CallState(CallKind.ACTIVE, "Ayşe", "0555 000 00 01", startedAtMs = 5_000)
        val waiting = PhoneStateMap.nextCall(active, CallKind.INCOMING, null, null, 20_000)
        assertNull(waiting.name)
        assertNull(waiting.number)
        // Bekleyen reddedildi: konuşmaya dönüş, sayaç sürer.
        val back = PhoneStateMap.nextCall(waiting, CallKind.ACTIVE, "Ayşe", "0555 000 00 01", 30_000)
        assertEquals(5_000L, back.startedAtMs)
        assertEquals("Ayşe", back.name)
    }

    @Test fun nextCallSameCallDifferentNumberIsNotMerged() {
        val incoming = CallState(CallKind.INCOMING, "Ayşe", "0555 000 00 01")
        val active = PhoneStateMap.nextCall(incoming, CallKind.ACTIVE, null, "+90 555 000 00 01", 1_000)
        assertEquals("Ayşe", active.name)
        val other = PhoneStateMap.nextCall(incoming, CallKind.ACTIVE, null, "0555 000 00 07", 1_000)
        assertNull(other.name)
    }

    // ---- numara ----

    @Test fun normalizeNumberVariantsMatch() {
        val a = FavoritesRanker.normalizeNumber("+90 555 000 00 01")
        assertEquals(a, FavoritesRanker.normalizeNumber("0555 000 00 01"))
        assertEquals(a, FavoritesRanker.normalizeNumber("905550000001"))
        assertEquals(a, FavoritesRanker.normalizeNumber("0090 (555) 000-00-01"))
        assertEquals("5550000001", a)
        assertEquals("112", FavoritesRanker.normalizeNumber("112"))
        assertEquals("", FavoritesRanker.normalizeNumber("gizli"))
    }

    @Test fun vdDateTimeFormats() {
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals(1_762_353_000_000L, PhoneStateMap.parseVdDateTime("20251105T143000", utc))
        assertEquals(1_762_353_000_000L, PhoneStateMap.parseVdDateTime("2025-11-05 14:30:00", utc))
        assertEquals(1_762_353_000_000L, PhoneStateMap.parseVdDateTime("1762353000000", utc))
        assertEquals(1_762_353_000_000L, PhoneStateMap.parseVdDateTime("1762353000", utc))
        assertEquals(1_762_353_000_000L, PhoneStateMap.parseVdDateTime("20251105143000", utc))
        assertEquals(1_762_300_800_000L, PhoneStateMap.parseVdDateTime("20251105", utc))
        assertNull(PhoneStateMap.parseVdDateTime("20251399999999", utc))
        assertNull(PhoneStateMap.parseVdDateTime("dün", utc))
        assertNull(PhoneStateMap.parseVdDateTime(null, utc))
    }

    // ---- favoriler ----

    private val now = 1_800_000_000_000L
    private val min = 60_000L
    private val day = 24 * 60 * min

    private fun c(id: String, name: String, n: Int, starred: Boolean = false) =
        PhoneContact(id, name, "0555 000 00 %02d".format(n), starred)

    private fun r(type: RecentType, n: Int, ago: Long, name: String? = null) =
        PhoneRecent(type, name, "0555 000 00 %02d".format(n), now - ago)

    @Test fun pinnedFirstInPinnedOrderThenStarredTurkishAlphabet() {
        val contacts = listOf(
            c("c:1", "Çağla", 1, starred = true),
            c("c:2", "Cem", 2, starred = true),
            c("c:3", "Ayşe", 3, starred = true),
            c("c:4", "Mehmet", 4),
            c("c:5", "Zeynep", 5),
        )
        val out = FavoritesRanker.rank(contacts, emptyList(), listOf("c:5", "c:4", "c:missing"), nowMs = now)
        assertEquals(listOf("Zeynep", "Mehmet", "Ayşe", "Cem", "Çağla"), out.map { it.name })
    }

    @Test fun frequencyFillOnlyWhenFewerThanFour() {
        val starred = (1..4).map { c("c:$it", "Kişi $it", it, starred = true) }
        val recents = listOf(r(RecentType.OUTGOING, 9, 5 * min), r(RecentType.OUTGOING, 9, 10 * min))
        val out = FavoritesRanker.rank(starred, recents, emptyList(), nowMs = now)
        assertEquals(4, out.size)
        assertTrue(out.none { it.id.startsWith("n:") })
    }

    @Test fun frequencyFillScoresAndIgnoresOldRecents() {
        val contacts = listOf(c("c:1", "Ayşe", 1, starred = true), c("c:2", "Mehmet", 2))
        val recents = listOf(
            r(RecentType.MISSED, 7, 1 * min), // 0,5
            r(RecentType.INCOMING, 8, 2 * min), // 1
            r(RecentType.OUTGOING, 2, 3 * min), // Mehmet 1
            r(RecentType.OUTGOING, 2, 4 * min), // Mehmet 2
            r(RecentType.OUTGOING, 9, 40 * day), // 30 günden eski
            r(RecentType.OUTGOING, 9, 41 * day),
            r(RecentType.OUTGOING, 1, 5 * min), // Ayşe zaten var (tekrar yok)
        )
        val out = FavoritesRanker.rank(contacts, recents, emptyList(), nowMs = now)
        assertEquals(listOf("c:1", "c:2", "n:5550000008", "n:5550000007"), out.map { it.id })
        // Rehberde olmayan numaranın adı numaranın kendisi.
        assertEquals("0555 000 00 08", out[2].name)
        assertFalse(out.any { it.id == "n:5550000009" })
    }

    @Test fun frequencyTieMostRecentFirst() {
        val recents = listOf(r(RecentType.OUTGOING, 1, 30 * min), r(RecentType.OUTGOING, 2, 5 * min))
        val out = FavoritesRanker.rank(emptyList(), recents, emptyList(), nowMs = now)
        assertEquals(listOf("n:5550000002", "n:5550000001"), out.map { it.id })
    }

    @Test fun dedupByNumberAndCapSix() {
        val contacts = (1..9).map { c("c:$it", "Kişi $it", it, starred = true) } +
            PhoneContact("c:99", "Kopya", "+90 555 000 00 01", starred = true)
        val out = FavoritesRanker.rank(contacts, emptyList(), listOf("c:1"), nowMs = now)
        assertEquals(6, out.size)
        assertEquals(1, out.count { FavoritesRanker.normalizeNumber(it.number) == "5550000001" })
    }

    @Test fun pinnedRecentOnlyNumber() {
        val recents = listOf(r(RecentType.INCOMING, 8, 2 * min, name = null))
        val out = FavoritesRanker.rank(emptyList(), recents, listOf("n:5550000008"), nowMs = now)
        assertEquals("n:5550000008", out.first().id)
    }

    // ---- son aramalar ----

    @Test fun recentsCollapseAndLimit() {
        val rows = listOf(
            r(RecentType.MISSED, 1, 1 * min, "Ayşe"),
            r(RecentType.MISSED, 1, 4 * min, "Ayşe"), // 10 dk içinde aynı → birleşir
            r(RecentType.MISSED, 1, 30 * min, "Ayşe"), // 10 dk'dan uzak → ayrı
            r(RecentType.OUTGOING, 2, 40 * min),
            r(RecentType.INCOMING, 3, 50 * min),
            r(RecentType.OUTGOING, 4, 60 * min),
            r(RecentType.OUTGOING, 5, 70 * min),
        ).shuffled(java.util.Random(7))
        val out = RecentsShaper.shape(rows, 5)
        assertEquals(5, out.size)
        assertEquals(now - 1 * min, out[0].timeMs)
        assertEquals(now - 30 * min, out[1].timeMs)
        assertTrue(out.zipWithNext().all { (a, b) -> a.timeMs >= b.timeMs })
    }

    @Test fun recentsDifferentTypeNotCollapsed() {
        val rows = listOf(r(RecentType.MISSED, 1, 1 * min), r(RecentType.OUTGOING, 1, 2 * min))
        assertEquals(2, RecentsShaper.shape(rows).size)
    }

    // ---- arka uç seçimi ----

    @Test fun callBackendOrder() {
        assertEquals(PhoneBackendName.DEMO, PhoneBackendChooser.callBackend(Caps(demo = true, vdbusBt = true)))
        assertEquals(PhoneBackendName.VDBUS, PhoneBackendChooser.callBackend(Caps(vdbusBt = true, telecomHfpAccount = true, hfpClientProxy = true)))
        assertEquals(PhoneBackendName.TELECOM, PhoneBackendChooser.callBackend(Caps(telecomHfpAccount = true, hfpClientProxy = true)))
        assertEquals(PhoneBackendName.HFP_CLIENT, PhoneBackendChooser.callBackend(Caps(hfpClientProxy = true)))
        assertEquals(PhoneBackendName.NONE, PhoneBackendChooser.callBackend(Caps(contactsPermission = true)))
    }

    @Test fun answerSkipsTelecom() {
        assertEquals(PhoneBackendName.HFP_CLIENT, PhoneBackendChooser.answerBackend(Caps(telecomHfpAccount = true, hfpClientProxy = true)))
        assertEquals(PhoneBackendName.NONE, PhoneBackendChooser.answerBackend(Caps(telecomHfpAccount = true)))
        assertEquals(PhoneBackendName.VDBUS, PhoneBackendChooser.answerBackend(Caps(vdbusBt = true, hfpClientProxy = true)))
        assertEquals(PhoneBackendName.DEMO, PhoneBackendChooser.answerBackend(Caps(demo = true)))
    }

    @Test fun fallbackOrders() {
        val all = Caps(vdbusBt = true, telecomHfpAccount = true, hfpClientProxy = true)
        val v = PhoneBackendName.VDBUS
        val t = PhoneBackendName.TELECOM
        val h = PhoneBackendName.HFP_CLIENT
        assertEquals(listOf(v, t, h), PhoneBackendChooser.fallbackOrder(all, PhoneAction.DIAL))
        assertEquals(listOf(v, h), PhoneBackendChooser.fallbackOrder(all, PhoneAction.ANSWER))
        assertEquals(listOf(v, h), PhoneBackendChooser.fallbackOrder(all, PhoneAction.REJECT))
        assertEquals(listOf(v, h), PhoneBackendChooser.fallbackOrder(all, PhoneAction.HANG_UP))
        assertEquals(listOf(PhoneBackendName.DEMO), PhoneBackendChooser.fallbackOrder(all.copy(demo = true), PhoneAction.HANG_UP))
        assertEquals(emptyList<String>(), PhoneBackendChooser.fallbackOrder(Caps(telecomHfpAccount = true), PhoneAction.HANG_UP))
    }

    @Test fun dataBackendOrder() {
        assertEquals(PhoneBackendName.DEMO, PhoneBackendChooser.dataBackend(Caps(demo = true, contactsPermission = true)))
        assertEquals(PhoneBackendName.CONTACTS_PROVIDER, PhoneBackendChooser.dataBackend(Caps(contactsPermission = true, vdbusData = true)))
        assertEquals(PhoneBackendName.VDBUS, PhoneBackendChooser.dataBackend(Caps(vdbusData = true)))
        assertEquals(PhoneBackendName.NONE, PhoneBackendChooser.dataBackend(Caps()))
    }

    @Test fun assistantAndStateBackends() {
        assertEquals(PhoneBackendName.DEMO, PhoneBackendChooser.assistantBackend(Caps(demo = true)))
        assertEquals(PhoneBackendName.HFP_CLIENT, PhoneBackendChooser.assistantBackend(Caps(vdbusBt = true, hfpClientProxy = true)))
        assertEquals(PhoneBackendName.NONE, PhoneBackendChooser.assistantBackend(Caps(vdbusBt = true)))
        assertEquals(PhoneBackendName.VDBUS, PhoneBackendChooser.stateBackend(Caps(vdbusBt = true, hfpClientProxy = true)))
        assertEquals(PhoneBackendName.HFP_CLIENT, PhoneBackendChooser.stateBackend(Caps(telecomHfpAccount = true, hfpClientProxy = true)))
        assertEquals(PhoneBackendName.NONE, PhoneBackendChooser.stateBackend(Caps(telecomHfpAccount = true)))
    }

    @Test fun permissionNeededTruthTable() {
        for (demo in listOf(false, true)) for (perm in listOf(false, true)) for (vd in listOf(false, true)) {
            val expected = !demo && !perm && !vd
            assertEquals("demo=$demo perm=$perm vd=$vd", expected,
                PhoneBackendChooser.permissionNeeded(Caps(demo = demo, contactsPermission = perm, vdbusData = vd)))
        }
    }

    // ---- Yazılım incelemesi: VDBus uuid seçimi, projeksiyon, yedek veri yolu ----

    @Test fun vdRejectPicksIncomingOrWaitingUuid() {
        val list = listOf(DialInfoLite(0, uuid = "a"), DialInfoLite(5, uuid = "w"))
        assertEquals("w", VdCallPick.forReject(list))
        assertEquals("i", VdCallPick.forReject(listOf(DialInfoLite(4, uuid = "i"))))
        assertNull(VdCallPick.forReject(listOf(DialInfoLite(0, uuid = "a"))))
        assertNull(VdCallPick.forReject(listOf(DialInfoLite(4, uuid = ""))))
        assertNull(VdCallPick.forReject(emptyList()))
    }

    @Test fun vdHangUpPicksLiveCallThenRelevant() {
        assertEquals("a", VdCallPick.forHangUp(listOf(DialInfoLite(5, uuid = "w"), DialInfoLite(0, uuid = "a"))))
        assertEquals("d", VdCallPick.forHangUp(listOf(DialInfoLite(2, uuid = "d"))))
        assertEquals("r", VdCallPick.forHangUp(listOf(DialInfoLite(3, uuid = "r"))))
        // Yalnız beklemedeki arama: ekranda gösterilen (relevantIndex) kapatılır.
        assertEquals("h", VdCallPick.forHangUp(listOf(DialInfoLite(1, uuid = "h"))))
        assertNull(VdCallPick.forHangUp(listOf(DialInfoLite(0, uuid = null))))
        assertNull(VdCallPick.forHangUp(emptyList()))
    }

    @Test fun acceptTypeIsNone() = assertEquals(0, VdCallPick.ACCEPT_TYPE)

    @Test fun projectionLinkActive() {
        assertTrue(ProjectionLink.active(2, 1))
        assertTrue(ProjectionLink.active(2, 4))
        assertTrue(ProjectionLink.active(2, 7))
        assertTrue(ProjectionLink.active(2, 8))
        assertFalse(ProjectionLink.active(1, 1))
        assertFalse(ProjectionLink.active(0, 4))
        assertFalse(ProjectionLink.active(2, 0))
        assertFalse(ProjectionLink.active(2, -1))
    }

    @Test fun telecomControlIsLastForCallControlOnly() {
        val c = Caps(vdbusBt = true, telecomHfpAccount = true, hfpClientProxy = true, telecomCallControl = true)
        val v = PhoneBackendName.VDBUS; val t = PhoneBackendName.TELECOM; val h = PhoneBackendName.HFP_CLIENT
        assertEquals(listOf(v, t, h), PhoneBackendChooser.fallbackOrder(c, PhoneAction.DIAL))
        assertEquals(listOf(v, h, t), PhoneBackendChooser.fallbackOrder(c, PhoneAction.ANSWER))
        assertEquals(listOf(v, h, t), PhoneBackendChooser.fallbackOrder(c, PhoneAction.HANG_UP))
        assertEquals(listOf(t), PhoneBackendChooser.fallbackOrder(Caps(telecomCallControl = true), PhoneAction.REJECT))
        assertEquals(emptyList<String>(), PhoneBackendChooser.fallbackOrder(Caps(telecomCallControl = true), PhoneAction.DIAL))
    }

    @Test fun dataFallbackToVdbusOnlyFromProvider() {
        assertEquals(PhoneBackendName.VDBUS,
            PhoneBackendChooser.dataFallback(Caps(contactsPermission = true, vdbusData = true), PhoneBackendName.CONTACTS_PROVIDER))
        assertNull(PhoneBackendChooser.dataFallback(Caps(contactsPermission = true), PhoneBackendName.CONTACTS_PROVIDER))
        assertNull(PhoneBackendChooser.dataFallback(Caps(vdbusData = true), PhoneBackendName.VDBUS))
        assertNull(PhoneBackendChooser.dataFallback(Caps(demo = true, vdbusData = true), PhoneBackendName.CONTACTS_PROVIDER))
    }

    /** VDBTRecord.dateTime = ham vCard X-IRMC-CALL-DATETIME; OEM arayüzü Z sonekini yok sayıp yerel saat sayar. */
    @Test fun vdRecordDateTimeFromVcard() {
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals(1_793_975_130_000L, PhoneStateMap.parseVdDateTime("20261106T142530", utc))
        assertEquals(1_793_975_130_000L, PhoneStateMap.parseVdDateTime("20261106T142530Z", utc))
    }
}
