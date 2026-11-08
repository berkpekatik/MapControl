package com.mapcontrol.launcher

import com.desaysv.ivi.vdb.event.id.bt.VDEventBT
import com.desaysv.ivi.vdb.event.id.media.VDEventMedia
import com.mapcontrol.launcher.MediaSourcePick.Cand
import com.mapcontrol.launcher.MediaSourcePick.Path
import com.mapcontrol.launcher.MediaSourcePick.SkipPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSourcePickTest {

    private val radio = Cand(MediaSourcePick.OEM_AUDIO_PKG, playing = false, hasTitle = true)   // boş "am" yer tutucusu
    private val radioOld = Cand(MediaSourcePick.OEM_AUDIO_PKG_OLD, playing = false, hasTitle = true)
    private val bt = Cand(MediaSourcePick.BT_PKG, playing = false, hasTitle = false)
    private val btPlaying = Cand(MediaSourcePick.BT_PKG, playing = true, hasTitle = true)
    private val radioPlaying = Cand(MediaSourcePick.OEM_AUDIO_PKG, playing = true, hasTitle = true)
    private val spotify = Cand("com.spotify.music", playing = false, hasTitle = true)

    @Test fun emptyListPicksNothing() = assertEquals(-1, MediaSourcePick.pick(emptyList(), -1))

    @Test fun ourCarOemAudioPackage() {
        // Bu araç: dumpsys media_session package=com.desaysv.svaudioapp; bazı yazılım sürümlerinde com.desaysv.audioapp.
        assertEquals("com.desaysv.svaudioapp", MediaSourcePick.OEM_AUDIO_PKG)
        assertTrue(MediaSourcePick.isOemAudio("com.desaysv.svaudioapp"))
        assertTrue(MediaSourcePick.isOemAudio("com.desaysv.audioapp"))
        assertFalse(MediaSourcePick.isOemAudio("com.spotify.music"))
        assertFalse(MediaSourcePick.isOemAudio(null))
    }

    @Test fun playingSessionAlwaysWins() {
        assertEquals(1, MediaSourcePick.pick(listOf(radio, btPlaying), 0))
        assertEquals(0, MediaSourcePick.pick(listOf(radioPlaying, bt), 1))
    }

    @Test fun radioPlaceholderNotShownWhenBluetoothSessionExists() {
        // 6 Kasım: radyo ("am") seçili, BT oturumu var, hiçbiri çalmıyor → kart BT'yi göstermeli.
        assertEquals(1, MediaSourcePick.pick(listOf(radio, bt), 0))
        assertEquals(1, MediaSourcePick.pick(listOf(radio, bt), -1))
        assertEquals(1, MediaSourcePick.pick(listOf(radioOld, bt), 0))   // eski yazılım sürümü
    }

    @Test fun pausedSelectionKeptOtherwise() {
        assertEquals(0, MediaSourcePick.pick(listOf(spotify, bt), 0))   // duraklatılan müzik atlamaz
        assertEquals(1, MediaSourcePick.pick(listOf(radio, bt), 1))     // BT seçiliyse korunur
        assertEquals(0, MediaSourcePick.pick(listOf(radio), 0))         // BT yoksa radyo gösterilir
        assertEquals(0, MediaSourcePick.pick(listOf(spotify, bt), -1))  // başlıklı üçüncü taraf önce
    }

    @Test fun switchOnlyWhenOemSourceIsNotBluetooth() {
        assertFalse(MediaSourcePick.needsBtSwitch(OemAudioSource.BT_MUSIC))
        assertTrue(MediaSourcePick.needsBtSwitch(10))   // AM
        assertTrue(MediaSourcePick.needsBtSwitch(9))    // FM
        assertTrue(MediaSourcePick.needsBtSwitch(null)) // okunamadı: OEM gibi geçir (zararsız)
    }

    @Test fun pathPrefersBtServiceThenMediaServiceThenPlain() {
        // Kaynak 13 ama odak başkasında olabilir: BT servisi varsa yine MUSIC_PLAY.
        assertEquals(Path.BT_PLAY, MediaSourcePick.path(13, btBus = true, mediaBus = true))
        assertEquals(Path.ALREADY_BT, MediaSourcePick.path(13, btBus = false, mediaBus = true))
        assertEquals(Path.ALREADY_BT, MediaSourcePick.path(13, btBus = false, mediaBus = false))
        assertEquals(Path.BT_PLAY, MediaSourcePick.path(9, btBus = true, mediaBus = true))
        assertEquals(Path.BT_PLAY, MediaSourcePick.path(null, btBus = true, mediaBus = false))
        assertEquals(Path.OPEN_SOURCE, MediaSourcePick.path(10, btBus = false, mediaBus = true))
        assertEquals(Path.PLAIN, MediaSourcePick.path(null, btBus = false, mediaBus = false))
    }

    @Test fun switchPayloadUsesReasonCards() {
        // OEM başlatıcı kartının yolu (reason=8); OEM ses uygulaması reason 13'ü yok sayar.
        val p = OemAudioSource.switchPayload()
        assertEquals(13, p.mediaType)
        assertEquals(6, p.playAction)
        assertEquals(8, p.mediaPlayActionInfo.reason)
        assertEquals(2, p.mediaPlayActionInfo.disPlayView)
        assertEquals(1, p.mediaPlayActionInfo.intent)
    }

    @Test fun skipPathBySessionAndOemSource() {
        val sv = MediaSourcePick.OEM_AUDIO_PKG
        val bt = MediaSourcePick.BT_PKG
        // BT oturumu: OEM BT servisi bağlıysa her zaman MUSIC_PLAY{5|4} (radyo odak tutsa da).
        assertEquals(SkipPath.BT_CTRL, MediaSourcePick.skipPath(bt, 9, btBus = true, mediaBus = false))
        assertEquals(SkipPath.BT_CTRL, MediaSourcePick.skipPath(bt, 13, btBus = true, mediaBus = false))
        assertEquals(SkipPath.SESSION, MediaSourcePick.skipPath(bt, 9, btBus = false, mediaBus = true))
        // Radyo/USB oturumu ya da oturum yok: OEM kartı gibi MEDIA_SOURCE_PLAY_STATUS{kaynak, 5|4}.
        assertEquals(SkipPath.MEDIA_CTRL, MediaSourcePick.skipPath(sv, 9, btBus = false, mediaBus = true))
        assertEquals(SkipPath.MEDIA_CTRL, MediaSourcePick.skipPath(sv, 10, btBus = false, mediaBus = true))
        assertEquals(SkipPath.MEDIA_CTRL, MediaSourcePick.skipPath(sv, 24, btBus = false, mediaBus = true))
        assertEquals(SkipPath.MEDIA_CTRL, MediaSourcePick.skipPath(sv, 3, btBus = false, mediaBus = true))
        assertEquals(SkipPath.MEDIA_CTRL, MediaSourcePick.skipPath(null, 13, btBus = false, mediaBus = true))
        // Radyo oturumu ama kaynak 13: MEDIA_CTRL{13} BT parçasını değiştirirdi (kart radyoyu gösterirken) → oturum.
        assertEquals(SkipPath.SESSION, MediaSourcePick.skipPath(sv, 13, btBus = false, mediaBus = true))
        assertEquals(SkipPath.SESSION, MediaSourcePick.skipPath(sv, 13, btBus = true, mediaBus = true))
        assertEquals(SkipPath.MEDIA_CTRL, MediaSourcePick.skipPath(MediaSourcePick.OEM_AUDIO_PKG_OLD, 8, false, true))
        // Kaynak bilinmiyor, medya servisinde denetçisi yok (1, 31) ya da OEM kartının tıklamayla göndermediği (18, 20): oturum.
        assertEquals(SkipPath.SESSION, MediaSourcePick.skipPath(sv, null, btBus = false, mediaBus = true))
        assertEquals(SkipPath.SESSION, MediaSourcePick.skipPath(sv, 1, btBus = false, mediaBus = true))
        assertEquals(SkipPath.SESSION, MediaSourcePick.skipPath(sv, 31, btBus = false, mediaBus = true))
        assertEquals(SkipPath.SESSION, MediaSourcePick.skipPath(null, 18, btBus = false, mediaBus = true))
        assertEquals(SkipPath.SESSION, MediaSourcePick.skipPath(null, 20, btBus = false, mediaBus = true))
        assertEquals(SkipPath.SESSION, MediaSourcePick.skipPath(sv, 9, btBus = false, mediaBus = false))
        // Üçüncü taraf uygulama: OEM kaynağına dokunulmaz.
        assertEquals(SkipPath.SESSION, MediaSourcePick.skipPath("com.spotify.music", 9, btBus = true, mediaBus = true))
    }

    @Test fun skipPayloadsMatchOem() {
        // OEM başlatıcı kartının yükü: {mediaType=geçerli, playAction 5 ileri / 4 geri}, bilgi yok.
        val n = OemAudioSource.skipPayload(9, next = true)
        assertEquals(9, n.mediaType)
        assertEquals(5, n.playAction)
        assertNull(n.mediaPlayActionInfo)
        assertEquals(4, OemAudioSource.skipPayload(10, next = false).playAction)
        // OEM ses uygulaması ve OEM BT servisi: playCtrl 5 ileri, 4 geri, playType 0.
        val b = OemAudioSource.btCtrlPayload(OemAudioSource.CTRL_NEXT)
        assertEquals(5, b.playCtrl)
        assertEquals(0, b.playType)
        assertEquals(4, OemAudioSource.btCtrlPayload(OemAudioSource.CTRL_PREV).playCtrl)
        assertEquals(393220, VDEventMedia.MEDIA_SOURCE_PLAY_STATUS)
        assertEquals(393217, VDEventMedia.CURRENT_SOURCE_TYPE)
    }

    @Test fun followOnlyBluetoothOrLiveSwitchToOemSource() {
        assertEquals(13, MediaSourcePick.follow(13, switchedLive = false))
        assertEquals(13, MediaSourcePick.follow(13, switchedLive = true))
        assertNull(MediaSourcePick.follow(9, switchedLive = false))   // 6 Kasım: BT yeniden bağlandı, kaynak radyoda kaldı
        assertEquals(9, MediaSourcePick.follow(9, switchedLive = true))
        assertNull(MediaSourcePick.follow(31, switchedLive = true))
        assertNull(MediaSourcePick.follow(18, switchedLive = true))
        assertNull(MediaSourcePick.follow(null, switchedLive = true))
    }

    @Test fun liveSwitchForgottenWhenBluetoothSessionAppears() {
        val f = SourceFollow()
        assertTrue(f.onSource(13))                 // ilk okuma: geçiş değil
        assertEquals(13, f.follow(hasBt = true))
        assertTrue(f.onSource(9))                  // telefon koptu, svaudioapp radyoya düştü (canlı)
        assertEquals(9, f.follow(hasBt = false))
        // Telefon yeniden bağlandı, kaynak 9'da kaldı: yeni BT oturumu canlı geçişi siler, kart BT'yi seçer.
        val follow = f.follow(hasBt = true)
        assertNull(follow)
        assertEquals(1, MediaSourcePick.pick(listOf(radio, bt), 0, follow = follow))
        assertNull(f.follow(hasBt = true))
        // BT varken kullanıcı radyoya geçerse izlenir; BT oturumu sürdükçe izlenmeye devam eder.
        assertFalse(f.onSource(9))
        assertTrue(f.onSource(10))
        assertEquals(10, f.follow(hasBt = true))
        assertEquals(10, f.follow(hasBt = true))
    }

    @Test fun firstSourceReadIsNotALiveSwitch() {
        val f = SourceFollow()
        f.onSource(9)
        assertNull(f.follow(hasBt = true))         // 6 Kasım
        assertNull(f.follow(hasBt = false))
    }

    @Test fun cardFollowsOemSourceSwitch() {
        // Kaynak BT'ye geçti: radyo seçiliyken bile BT oturumu (BT henüz çalmıyor).
        assertEquals(1, MediaSourcePick.pick(listOf(radio, bt), 0, follow = 13))
        // Kaynak canlı olarak radyoya geçti: BT seçili ve BT oturumu varken bile radyo oturumu.
        assertEquals(0, MediaSourcePick.pick(listOf(radio, bt), 1, follow = 9))
        assertEquals(0, MediaSourcePick.pick(listOf(radioOld, bt), -1, follow = 10))
        // İzlenecek kaynak yok: eski kural (BT varken radyo yer tutucusu gösterilmez).
        assertEquals(1, MediaSourcePick.pick(listOf(radio, bt), 0, follow = null))
        // Çalan oturum yine önce.
        assertEquals(1, MediaSourcePick.pick(listOf(radio, btPlaying), 0, follow = 9))
        assertEquals(0, MediaSourcePick.pick(listOf(radioPlaying, bt), 1, follow = 13))
        // Seçili üçüncü taraf uygulama OEM kaynağı yüzünden bırakılmaz.
        assertEquals(0, MediaSourcePick.pick(listOf(spotify, bt, radio), 0, follow = 13))
        assertEquals(0, MediaSourcePick.pick(listOf(spotify, bt, radio), 0, follow = 9))
        // İzlenen kaynağın oturumu yoksa eski kurallar.
        assertEquals(0, MediaSourcePick.pick(listOf(spotify), -1, follow = 13))
        assertEquals(0, MediaSourcePick.pick(listOf(bt), 0, follow = 9))
    }

    @Test fun btPlayPayloadMatchesSvAudioApp() {
        // OEM ses uygulamasının yolu: MUSIC_PLAY{playCtrl=1, playType=0}.
        val p = OemAudioSource.btPlayPayload()
        assertEquals(1, p.playCtrl)
        assertEquals(0, p.playType)
        assertEquals(262948, VDEventBT.MUSIC_PLAY)
    }
}
