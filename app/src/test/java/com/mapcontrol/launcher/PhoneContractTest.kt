package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Genişletilmiş telefon sözleşmesinin saf yardımcıları (DTMF, ses yönü, sayaç, yoklama, rehber yenileme). */
class PhoneContractTest {

    @Test fun dtmfIsAsciiDecimal() {
        assertEquals("48", DtmfCode.vdNumber('0'))
        assertEquals("53", DtmfCode.vdNumber('5'))
        assertEquals("57", DtmfCode.vdNumber('9'))
        assertEquals("42", DtmfCode.vdNumber('*'))
        assertEquals("35", DtmfCode.vdNumber('#'))
        assertNull(DtmfCode.vdNumber('a'))
        assertNull(DtmfCode.vdNumber('+'))
        // OEM servisi Byte.parseByte ile okur: hepsi bayta sığmalı.
        for (c in "0123456789*#") assertEquals(c.code, DtmfCode.vdNumber(c)!!.toByte().toInt())
    }

    @Test fun audioMode() {
        assertEquals(false, AudioModeMap.onPhone(1))
        assertEquals(true, AudioModeMap.onPhone(2))
        assertNull(AudioModeMap.onPhone(3))
        assertNull(AudioModeMap.onPhone(-1))
    }

    @Test fun callClock() {
        // VDBTDialInfo.time elapsedRealtime: olduğu gibi kullanılır (duvar saatine çevirme yok).
        assertEquals(1_000L, CallClock.startElapsed(oemStartElapsedMs = 1_000L, nowElapsedMs = 41_000L))
        assertNull(CallClock.startElapsed(0L, 41_000L))
        assertNull(CallClock.startElapsed(-5L, 41_000L))
        // Gelecekte (5 sn'den fazla) ya da 12 saatten eski: güvenilmez.
        assertNull(CallClock.startElapsed(50_000L, 41_000L))
        assertNull(CallClock.startElapsed(1L, 13L * 3600_000L))
        // Küçük kayma (gelecek) şimdiye çekilir.
        assertEquals(41_000L, CallClock.startElapsed(43_000L, 41_000L))
        // Eski yanlış yorum (duvar saati) burada null verirdi: 6 Kasım'da süre hep sıfırdan başlıyordu.
        assertEquals(3_600_000L, CallClock.startElapsed(3_600_000L, 3_700_000L))
    }

    @Test fun pollOnlyDuringCall() {
        assertTrue(CallPoll.shouldPoll(CallKind.ACTIVE, true))
        assertTrue(CallPoll.shouldPoll(CallKind.INCOMING, true))
        assertTrue(CallPoll.shouldPoll(CallKind.DIALING, true))
        assertFalse(CallPoll.shouldPoll(CallKind.ENDED, true))
        assertFalse(CallPoll.shouldPoll(CallKind.NONE, true))
        assertFalse(CallPoll.shouldPoll(CallKind.ACTIVE, false))
    }

    @Test fun phonebookRefresh() {
        assertEquals(PhonebookRefresh.Step.CACHE, PhonebookRefresh.next(false, null, null, false))
        assertEquals(PhonebookRefresh.Step.NONE, PhonebookRefresh.next(false, 12, null, false))
        assertEquals(PhonebookRefresh.Step.NONE, PhonebookRefresh.next(true, 0, null, false))
        assertEquals(PhonebookRefresh.Step.DOWNLOAD, PhonebookRefresh.next(false, 0, 20, false))
        assertEquals(PhonebookRefresh.Step.DOWNLOAD, PhonebookRefresh.next(false, 0, null, false))
        assertEquals(PhonebookRefresh.Step.NONE, PhonebookRefresh.next(false, 0, 0, false))
        assertEquals(PhonebookRefresh.Step.NONE, PhonebookRefresh.next(false, 0, 21, false))
        assertEquals(PhonebookRefresh.Step.NONE, PhonebookRefresh.next(false, 0, 1, true))
    }

    @Test fun phonebookStatusCombine() {
        // Rehber (7) ya da geçmiş (6) iniyorsa yeni indirme istenmez.
        assertEquals(0, PhonebookRefresh.combine(1, 0))
        assertEquals(21, PhonebookRefresh.combine(21, 1))
        assertEquals(0, PhonebookRefresh.combine(null, 0))
        // İkisi de inmiyorsa rehberinki, yoksa geçmişinki.
        assertEquals(20, PhonebookRefresh.combine(20, 1))
        assertEquals(1, PhonebookRefresh.combine(null, 1))
        assertNull(PhonebookRefresh.combine(null, null))
        // Geçmiş inerken boş önbellek DOWNLOAD'a götürmez.
        assertEquals(PhonebookRefresh.Step.NONE, PhonebookRefresh.next(false, 0, PhonebookRefresh.combine(20, 0), false))
    }

    @Test fun voicePlan() {
        // Açık oturum + SCO: ikinci basış kapatır (aramada bile).
        assertEquals(VoicePlan.Step.STOP_SESSION, VoicePlan.next(sessionOpen = true, scoUp = true, stackMaybeActive = true, callBusy = false))
        assertEquals(VoicePlan.Step.BUSY, VoicePlan.next(false, false, true, callBusy = true))
        // Süreç başı (bilinmiyor) ya da doğrulanmamış başlatma sonrası: önce durdur, sonra başlat.
        assertEquals(VoicePlan.Step.RESET_THEN_START, VoicePlan.next(false, false, stackMaybeActive = true, callBusy = false))
        // Bayat oturum (SCO yok): yine önce durdur.
        assertEquals(VoicePlan.Step.RESET_THEN_START, VoicePlan.next(true, false, true, false))
        // Yığın 0 olduğu biliniyor (+BVRA:0 ya da kendi durdurmamız): doğrudan başlat.
        assertEquals(VoicePlan.Step.START, VoicePlan.next(false, false, stackMaybeActive = false, callBusy = false))
        // SCO bağlanıyor (1) ya da bağlı (2) başlatmayı doğrular; kopuk (0) doğrulamaz.
        assertTrue(VoicePlan.confirmsStart(1))
        assertTrue(VoicePlan.confirmsStart(2))
        assertFalse(VoicePlan.confirmsStart(0))
        assertFalse(VoicePlan.confirmsStart(-1))
    }

    @Test fun snapshotDefaultsBackwardCompatible() {
        val s = PhoneSnapshot()
        assertFalse(s.canAudioTransfer)
        assertFalse(s.canDtmf)
        assertEquals(0, s.directoryVersion)
        val c = CallState(CallKind.ACTIVE, "x", "1", 5L, true)
        assertFalse(c.audioOnPhone)
        assertNull(c.photoKey)
        assertFalse(c.toString().contains("x"))
    }
}
