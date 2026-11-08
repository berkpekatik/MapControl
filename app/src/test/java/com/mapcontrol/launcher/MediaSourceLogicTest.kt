package com.mapcontrol.launcher

import com.mapcontrol.launcher.PhonePriority.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonePriorityTest {

    private val am = 10
    private val fm = 9

    /** BT oturumu duraklamış görülmüş (ilk gözlem) bir karar verici. */
    private fun primed(now: Long = 0L) = PhonePriority().also {
        assertEquals(Decision.NO_TRANSITION, it.onBtState(false, now, am, enabled = { true }, inCall = { false }))
    }

    @Test fun phonePlayOnRadioSwitches() {
        // 7 Kasım: kaynak AM (10), telefonda Oynat → BT oturumu PLAYING → BT'ye geç.
        val p = primed()
        assertEquals(Decision.SWITCH, p.onBtState(true, 100_000L, am, enabled = { true }, inCall = { false }))
    }

    @Test fun firstObservationIsNotATransition() {
        // Süreç başlarken telefon zaten çalıyorsa geçiş sayılmaz (biz başlatmadık, ne olduğunu bilmiyoruz).
        val p = PhonePriority()
        assertEquals(Decision.NO_TRANSITION, p.onBtState(true, 0L, am, enabled = { true }, inCall = { false }))
    }

    @Test fun repeatedPlayingStateIsNotATransition() {
        val p = primed()
        assertEquals(Decision.SWITCH, p.onBtState(true, 100_000L, am, { true }, { false }))
        // BT oturumu ~500 ms'de bir konum yollar: aynı "çalıyor" durumu yeniden geçiş değildir.
        assertEquals(Decision.NO_TRANSITION, p.onBtState(true, 100_500L, am, { true }, { false }))
    }

    @Test fun reappearingSessionAfterGoneCounts() {
        // Telefon koptu (oturum yok), yeniden bağlanıp çalmaya başladı: kaynak radyoya düşmüştü → BT'ye geç.
        val p = primed()
        p.onBtGone(50_000L)
        assertEquals(Decision.SWITCH, p.onBtState(true, 100_000L, fm, { true }, { false }))
    }

    @Test fun noSessionAtStartupCountsAsPaused() {
        // Süreç başında BT oturumu yok (OEM A2DP kopuşta oturumu pasif yapar). Telefon bağlanınca oturum zaten PLAYING
        // görünür (msgTrack ile yeniden etkinleşir): bu bir geçiştir, telefon öne alınır.
        val p = PhonePriority()
        p.onBtGone(0L)
        assertEquals(Decision.SWITCH, p.onBtState(true, 100_000L, fm, { true }, { false }))
    }

    @Test fun oemFallbackAfterLinkLossIsNotAUserPick() {
        // Telefon çalıyordu, bağlantı koptu: oturum gitti, sonra OEM 13 → FM'e düştü. Telefon 3 sn sonra yeniden bağlanıp
        // çalmaya devam etti: geçilmeli (asıl şikâyet "düşünce araba radyoya düşüyor").
        val p = primed()
        p.onBtState(true, 1_000L, OemAudioSource.BT_MUSIC, { true }, { false })
        p.onBtGone(100_000L)
        assertFalse(p.onOemSourceAway(100_200L))
        assertEquals(Decision.SWITCH, p.onBtState(true, 103_000L, fm, { true }, { false }))
    }

    @Test fun oemFallbackBeforeLossSignalIsUndone() {
        // Sıra tersse (önce kaynak 13 → FM, sonra oturum gitti): kopuş, az önceki kaynak değişimini seçim olmaktan çıkarır.
        val p = primed()
        p.onBtState(true, 1_000L, OemAudioSource.BT_MUSIC, { true }, { false })
        assertTrue(p.onOemSourceAway(100_000L))
        p.onBtGone(100_300L)
        assertEquals(Decision.SWITCH, p.onBtState(true, 104_000L, fm, { true }, { false }))
    }

    @Test fun oemPickWhileConnectedIsAUserPick() {
        // Telefon bağlı ve çalarken kullanıcı OEM uygulamasında FM'i seçti; OEM telefonu duraklattı. Telefon hemen yeniden
        // çalarsa (ör. bildirim) kaynak geri alınmaz.
        val p = primed()
        p.onBtState(true, 1_000L, OemAudioSource.BT_MUSIC, { true }, { false })
        assertTrue(p.onOemSourceAway(100_000L))
        p.onBtState(false, 101_000L, fm, { true }, { false })
        assertEquals(Decision.USER_PICK, p.onBtState(true, 105_000L, fm, { true }, { false }))
        p.onBtState(false, 106_000L, fm, { true }, { false })
        assertEquals(Decision.SWITCH, p.onBtState(true, 110_000L, fm, { true }, { false }))
    }

    @Test fun oemSourceChangeWithoutSessionIsNotAUserPick() {
        val p = PhonePriority()
        assertFalse(p.onOemSourceAway(100_000L))
        p.onBtGone(200_000L)
        assertFalse(p.onOemSourceAway(204_999L))
    }

    @Test fun transitionPeek() {
        val p = PhonePriority()
        assertFalse(p.isTransition(true))
        p.onBtGone(0L)
        assertTrue(p.isTransition(true))
        assertFalse(p.isTransition(false))
        p.onBtState(true, 100_000L, OemAudioSource.BT_MUSIC, { true }, { false })
        assertFalse(p.isTransition(true))
    }

    @Test fun btSessionStateMapping() {
        assertEquals(true, MediaSourcePick.btPlaying(3))
        assertEquals(false, MediaSourcePick.btPlaying(2))
        assertEquals(false, MediaSourcePick.btPlaying(null))
        assertNull(MediaSourcePick.btPlaying(7)) // ERROR: OEM A2DP kopuşu
    }

    @Test fun guards() {
        assertEquals(Decision.DISABLED, primed().onBtState(true, 100_000L, am, enabled = { false }, inCall = { false }))
        assertEquals(Decision.UNKNOWN_SOURCE, primed().onBtState(true, 100_000L, null, { true }, { false }))
        assertEquals(Decision.ALREADY_BT, primed().onBtState(true, 100_000L, OemAudioSource.BT_MUSIC, { true }, { false }))
        assertEquals(Decision.IN_CALL, primed().onBtState(true, 100_000L, am, { true }, inCall = { true }))
    }

    @Test fun ownPlayIsNotPhonePriority() {
        val p = primed()
        p.onOwnPlay(100_000L)
        assertEquals(Decision.OWN_PLAY, p.onBtState(true, 104_999L, am, { true }, { false }))
        p.onBtState(false, 105_000L, am, { true }, { false })
        assertEquals(Decision.SWITCH, p.onBtState(true, 105_000L, am, { true }, { false }))
    }

    @Test fun userJustPickedRadio() {
        val p = primed()
        p.onUserPick(100_000L)
        assertEquals(Decision.USER_PICK, p.onBtState(true, 109_999L, fm, { true }, { false }))
        p.onBtState(false, 110_000L, fm, { true }, { false })
        assertEquals(Decision.SWITCH, p.onBtState(true, 110_000L, fm, { true }, { false }))
    }

    @Test fun debounceOncePerFiveSeconds() {
        val p = primed()
        assertEquals(Decision.SWITCH, p.onBtState(true, 100_000L, am, { true }, { false }))
        p.onBtState(false, 101_000L, am, { true }, { false })
        assertEquals(Decision.DEBOUNCE, p.onBtState(true, 102_000L, am, { true }, { false }))
        p.onBtState(false, 104_000L, am, { true }, { false })
        assertEquals(Decision.SWITCH, p.onBtState(true, 105_000L, am, { true }, { false }))
    }

    @Test fun pauseIsNeverASwitch() {
        val p = primed()
        p.onBtState(true, 100_000L, am, { true }, { false })
        assertEquals(Decision.NO_TRANSITION, p.onBtState(false, 101_000L, am, { true }, { false }))
    }
}

class RadioDisplayTest {

    private val audio = MediaSourcePick.OEM_AUDIO_PKG

    @Test fun fmFrequencyFromRawKhz() {
        // svaudioapp: TITLE = String.valueOf(89700), ARTIST = "fm".
        val s = RadioDisplay.of(audio, "89700", "fm", 9)!!
        assertEquals(RadioDisplay.Band.FM, s.band)
        assertEquals("FM 89,7", s.title)
        assertNull(s.subtitle)
    }

    @Test fun fmFormatting() {
        assertEquals("FM 89,7", RadioDisplay.frequency(RadioDisplay.Band.FM, 89_700))
        assertEquals("FM 89,75", RadioDisplay.frequency(RadioDisplay.Band.FM, 89_750))
        assertEquals("FM 101,0", RadioDisplay.frequency(RadioDisplay.Band.FM, 101_000))
        assertEquals("FM 87,5", RadioDisplay.frequency(RadioDisplay.Band.FM, 87_500))
        assertEquals("FM 897", RadioDisplay.frequency(RadioDisplay.Band.FM, 897)) // birim tanınmazsa olduğu gibi
    }

    @Test fun amFrequencyInKhz() {
        // 7 Kasım araç: AM 522 kHz (hardware_radio "current fre: 522").
        val s = RadioDisplay.of(audio, "522", "am", 10)!!
        assertEquals(RadioDisplay.Band.AM, s.band)
        assertEquals("AM 522 kHz", s.title)
        assertNull(s.subtitle)
    }

    @Test fun rdsStationNameKeepsBandBelow() {
        // RDS istasyon adı varsa TITLE odur; frekans oturumda yok, bant alt satırda.
        val s = RadioDisplay.of(audio, "RADYO X", "fm", 9)!!
        assertEquals("RADYO X", s.title)
        assertEquals("FM", s.subtitle)
    }

    @Test fun dabUsesSourceTypeAndEnsemble() {
        val s = RadioDisplay.of(audio, "STATION", "ENSEMBLE", 24)!!
        assertEquals(RadioDisplay.Band.DAB, s.band)
        assertEquals("STATION", s.title)
        assertEquals("ENSEMBLE", s.subtitle)
    }

    @Test fun bandFromSourceWhenLabelMissing() {
        val s = RadioDisplay.of(audio, "98500", null, 9)!!
        assertEquals("FM 98,5", s.title)
    }

    @Test fun notRadio() {
        // Üçüncü taraf uygulama ve BT hiçbir zaman radyo değil.
        assertNull(RadioDisplay.of("com.spotify.music", "89700", "fm", 9))
        assertNull(RadioDisplay.of(MediaSourcePick.BT_PKG, "Song", "Artist", 9))
        // svaudioapp USB müziği: etiket yok, kaynak USB → radyo değil.
        assertNull(RadioDisplay.of(audio, "Song", "Artist", 3))
        assertNull(RadioDisplay.of(audio, "Song", "Artist", null))
    }

    @Test fun emptyTitleShowsBand() {
        assertEquals("AM", RadioDisplay.of(audio, "", "am", 10)!!.title)
    }
}

class SourceChoiceTest {

    @Test fun verifiedOemTypes() {
        // VDValueMedia.MediaType: FM 9, AM 10, LOCAL_MUSIC_USB0 3, BT_MUSIC 13.
        assertEquals(9, SourceChoice.FM)
        assertEquals(10, SourceChoice.AM)
        assertEquals(3, SourceChoice.USB0)
        assertEquals(13, OemAudioSource.BT_MUSIC)
    }

    @Test fun entriesUsbOnlyWhenConnected() {
        assertEquals(listOf(13, 9, 10), SourceChoice.entries(usbAvailable = false))
        assertEquals(listOf(13, 9, 10, 3), SourceChoice.entries(usbAvailable = true))
    }

    @Test fun usbConnectState() {
        // VDValueMedia.MediaConnectState: 1 DISCONNECTED, 2 CONNECTED_SEARCHING, 3 CONNECTED_SEARCHED.
        assertFalse(SourceChoice.usbConnected(null))
        assertFalse(SourceChoice.usbConnected(1))
        assertTrue(SourceChoice.usbConnected(2))
        assertTrue(SourceChoice.usbConnected(3))
    }

    @Test fun currentMark() {
        assertTrue(SourceChoice.isCurrent(9, 9))
        assertFalse(SourceChoice.isCurrent(9, 8)) // FM_AM (8) hiçbir satırı işaretlemez
        assertFalse(SourceChoice.isCurrent(13, null))
    }
}
