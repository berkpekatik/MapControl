package com.mapcontrol.launcher

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.desaysv.ivi.vdb.client.VDBus
import com.desaysv.ivi.vdb.client.bind.VDServiceDef
import com.desaysv.ivi.vdb.event.id.bt.VDEventBT
import com.desaysv.ivi.vdb.event.id.bt.bean.VDBTMusicPlay
import com.desaysv.ivi.vdb.event.id.media.VDEventMedia
import com.desaysv.ivi.vdb.event.id.media.bean.VDMediaExtInfo
import com.desaysv.ivi.vdb.event.id.media.bean.VDMediaPlay
import com.desaysv.ivi.vdb.event.id.media.bean.VDMediaPlayActionInfo
import com.desaysv.ivi.vdb.event.id.media.bean.VDMediaType
import com.desaysv.ivi.vdb.client.listener.VDBindListener
import com.desaysv.ivi.vdb.client.listener.VDNotifyListener
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/**
 * Müzik kartı / mini gösterge seçim kuralları (saf, JVM'de sınanır).
 *
 * 6 Kasım araç: BT yeniden bağlanınca OEM ses kaynağı radyoda kaldı (OEM ses uygulaması odak tutuyor), kart radyonun boş "am"
 * oturumunu gösterdi ve BT çalması 1-2 sn sonra durdu. Kurallar: hiçbir oturum çalmıyorsa ve bir Bluetooth oturumu varsa
 * OEM ses uygulamasının (radyo) oturumu karta seçilmez.
 */
internal object MediaSourcePick {

    /** A2DP sink oturumu (com.android.bluetooth). */
    const val BT_PKG = "com.android.bluetooth"

    /**
     * OEM ses merkezi (radyo/USB). Bizim araç (T1E24): `com.desaysv.svaudioapp`
     * (dumpsys media_session: RadioMediaSessionBrowserService / MediaPlaybackService).
     * Arkadaşın aracı (T18FL4): `com.desaysv.audioapp`.
     */
    const val OEM_AUDIO_PKG = "com.desaysv.svaudioapp"
    const val OEM_AUDIO_PKG_OLD = "com.desaysv.audioapp"

    class Cand(val pkg: String?, val playing: Boolean, val hasTitle: Boolean)

    fun isBluetooth(pkg: String?) = pkg == BT_PKG

    /**
     * Telefon önceliğine giden BT oturumu durumu ([PlaybackState] kodu; null: durum yok). PLAYING (3) → true; ERROR (7) →
     * null ("oturum yok": OEM A2DP servisi kopuşta durumu "bluetooth disconnected" hatasıyla ERROR'a çeker); gerisi
     * false.
     */
    fun btPlaying(state: Int?): Boolean? = when (state) {
        STATE_PLAYING -> true
        STATE_ERROR -> null
        else -> false
    }

    private const val STATE_PLAYING = 3
    private const val STATE_ERROR = 7

    fun isOemAudio(pkg: String?) = pkg == OEM_AUDIO_PKG || pkg == OEM_AUDIO_PKG_OLD

    /**
     * OEM ses uygulamasının kendi çaldığı OEM kaynak türleri (VDValueMedia.MediaType: LOCAL_MUSIC 2, USB0 3, USB1 4, FM_AM 8, FM 9,
     * AM 10, DAB 24). Hepsi OEM başlatıcı kartının izlediği listede ve medya servisinde
     * denetçisi var (tanımsız türde `PlayStatusEventHandle` NPE verir).
     */
    private val OEM_OWN_SOURCES = setOf(2, 3, 4, 8, 9, 10, 24)

    fun isOemOwnSource(type: Int?) = type != null && type in OEM_OWN_SOURCES

    /** Oturum üçüncü taraf bir uygulamanınki mi (BT ya da OEM ses uygulaması değil). */
    private fun isThirdParty(pkg: String?) = pkg != null && !isBluetooth(pkg) && !isOemAudio(pkg)

    /**
     * Seçilecek oturumun sırası; liste boşsa -1. [current] seçili oturumun sırası (yoksa -1). [follow]: kartın izleyeceği OEM
     * kaynağı ([OemAudioSource.followSource]; yoksa null).
     * 1) Çalan ilk oturum. 2) Seçili oturum üçüncü taraf değilse OEM kaynağı izlenir: 13 → BT oturumu; OEM ses uygulaması kaynağı
     * (radyo/USB, yalnızca canlı geçişten sonra) → OEM ses oturumu. 3) Seçili oturum korunur (duraklatılan müzik atlamaz) —
     * seçili oturum OEM radyosuysa ve BT oturumu varsa korunmaz. 4) Başlığı olan ilk oturum; o OEM radyosuysa ve BT varsa BT.
     * 5) BT. 6) İlk oturum.
     */
    @JvmOverloads
    fun pick(cands: List<Cand>, current: Int, follow: Int? = null): Int {
        if (cands.isEmpty()) return -1
        cands.indexOfFirst { it.playing }.let { if (it >= 0) return it }
        val bt = cands.indexOfFirst { isBluetooth(it.pkg) }
        if (follow != null && !(current in cands.indices && isThirdParty(cands[current].pkg))) {
            if (follow == OemAudioSource.BT_MUSIC && bt >= 0) return bt
            if (isOemOwnSource(follow)) cands.indexOfFirst { isOemAudio(it.pkg) }.let { if (it >= 0) return it }
        }
        if (current in cands.indices && !(bt >= 0 && isOemAudio(cands[current].pkg))) return current
        val titled = cands.indexOfFirst { it.hasTitle }
        if (titled >= 0 && !(bt >= 0 && isOemAudio(cands[titled].pkg))) return titled
        if (bt >= 0) return bt
        return if (titled >= 0) titled else 0
    }

    /** Oynat'tan önce OEM kaynağı Bluetooth müziğine geçirilmeli mi: kaynak okunamadıysa (null) da geçirilir (OEM gibi zararsız). */
    fun needsBtSwitch(currentSourceType: Int?): Boolean = currentSourceType != OemAudioSource.BT_MUSIC

    /**
     * Oynat'ta izlenecek yol. BT servisi bağlıysa kaynak 13 olsa bile `MUSIC_PLAY` gider: `CURRENT_SOURCE_TYPE` OEM'in son
     * seçtiği kaynaktır, odak 42'nin şu anki sahibi değil (navigasyon anonsu, başka uygulama ya da biten HFP araması sonrası
     * 13 kalır ama odak bizde değildir). Komut zararsızdır: OEM BT servisi odağı yalnızca tutmuyorsa ister.
     */
    enum class Path {
        /** OEM kaynağı zaten BT ve BT servisi yok: yalnızca BT oturumuna oynat (OPEN_SOURCE atlanır). */
        ALREADY_BT,
        /** VDBus BT `MUSIC_PLAY{playCtrl=1, playType=0}`: OEM BT servisi odak 42'yi alır ve çalar (OEM ses uygulamasının yolu). */
        BT_PLAY,
        /** VDBus MEDIA `OPEN_SOURCE` reason=8: medya servisi OEM ses uygulaması BTMusicService'i başlatır, o da BT_PLAY gönderir. */
        OPEN_SOURCE,
        /** VDBus yok: yalnızca BT oturumuna oynat (odak 42 değilse A2dpMediaBrowserService bunu yok sayar). */
        PLAIN,
    }

    fun path(currentSourceType: Int?, btBus: Boolean, mediaBus: Boolean): Path = when {
        btBus -> Path.BT_PLAY
        !needsBtSwitch(currentSourceType) -> Path.ALREADY_BT
        mediaBus -> Path.OPEN_SOURCE
        else -> Path.PLAIN
    }

    /** İleri/Geri'de izlenecek yol. */
    enum class SkipPath {
        /** `MediaController.skipToNext/Previous` (üçüncü taraf uygulama ya da VDBus yok). */
        SESSION,
        /** VDBus BT `MUSIC_PLAY{playCtrl=5|4, playType=0}`: OEM BT servisi odak 42'yi ister ve atlar (OEM ses uygulamasının yolu). */
        BT_CTRL,
        /** VDBus MEDIA `MEDIA_SOURCE_PLAY_STATUS{mediaType=geçerli kaynak, playAction=5|4}`: OEM başlatıcı kartının yolu. */
        MEDIA_CTRL,
    }

    /**
     * [activePkg]: kartın oturumu (yoksa null). [oemSource]: `CURRENT_SOURCE_TYPE` (bilinmiyorsa null).
     * BT oturumu → BT servisi bağlıysa BT_CTRL (`com.android.bluetooth` oturumuna giden next/prev odak 42 yokken no-op).
     * OEM ses oturumu, kaynak OEM ses uygulamasınınsa → MEDIA_CTRL; kaynak 13 ise SESSION
     * (MEDIA_CTRL{13} BT denetçisine gider, kart radyoyu gösterirken BT parçası
     * değişirdi). Oturum yok, kaynak OEM ses uygulamasının ya da 13 → MEDIA_CTRL. Gerisi SESSION (üçüncü taraf uygulamanın kendi
     * odağı var; OEM kaynağına dokunulmaz).
     */
    fun skipPath(activePkg: String?, oemSource: Int?, btBus: Boolean, mediaBus: Boolean): SkipPath = when {
        isBluetooth(activePkg) -> if (btBus) SkipPath.BT_CTRL else SkipPath.SESSION
        !mediaBus -> SkipPath.SESSION
        isOemAudio(activePkg) && isOemOwnSource(oemSource) -> SkipPath.MEDIA_CTRL
        activePkg == null && (isOemOwnSource(oemSource) || oemSource == OemAudioSource.BT_MUSIC) -> SkipPath.MEDIA_CTRL
        else -> SkipPath.SESSION
    }

    /** Kartın izleyeceği OEM kaynağı: 13 her zaman; OEM ses uygulaması kaynağı yalnızca canlı geçişle geldiyse (bkz. [pick]). */
    fun follow(source: Int?, switchedLive: Boolean): Int? = when {
        source == OemAudioSource.BT_MUSIC -> source
        isOemOwnSource(source) && switchedLive -> source
        else -> null
    }
}

/**
 * OEM kaynağı izleme durumu (saf, JVM'de sınanır; [OemAudioSource] kilit altında kullanır).
 *
 * Canlı geçiş (OEM ses uygulaması kaynağına) tek seferliktir: bir Bluetooth oturumu sonradan belirirse unutulur. Yoksa BT 13 → telefon
 * koptu, kaynak radyoya (9) düştü → telefon yeniden bağlandı, kaynak 9'da kaldı zinciri 6 Kasım hatasını geri getirir
 * (kart BT oturumu yerine boş radyo oturumunu seçer). Süreç uyku boyunca yaşarsa eski geçiş de böyle silinir.
 */
internal class SourceFollow {
    /** Son bilinen `CURRENT_SOURCE_TYPE` (bilinmiyorsa null). */
    var source: Int? = null
        private set
    private var switchedLive = false
    private var hadBt = false

    /** Yeni kaynak; değiştiyse true. İlk değer geçiş sayılmaz, sonraki her değişim geçiştir. */
    fun onSource(type: Int): Boolean {
        val old = source
        if (old == type) return false
        source = type
        switchedLive = old != null
        return true
    }

    /** [hasBt]: aday oturumlarda Bluetooth oturumu var mı. Yeni beliren BT oturumu canlı geçişi siler. */
    fun follow(hasBt: Boolean): Int? {
        if (hasBt && !hadBt) switchedLive = false
        hadBt = hasBt
        return MediaSourcePick.follow(source, switchedLive)
    }
}

/**
 * OEM ses kaynağını Bluetooth müziğine geçirme (bizim araç T1E24, 6 Kasım ölçümü):
 *
 * - Araçta ses kaynağını müzik odağının sahibi belirler. BT müziğin odağını (usage=1, content=2, legacyStreamType=42)
 * yalnızca OEM BT servisi `com.desaysv.ivi.vds.bt` ister: `MUSIC_PLAY` (262948).

 * - `com.android.bluetooth` oturumuna giden play/next/prev, odak 42 değilse yok sayılır.
 * Bu yüzden ilk yol OEM ses uygulamasının kendi yolu: `MUSIC_PLAY{1,0}` (yol BT_PLAY).
 * - Yedek: OEM başlatıcı kartının yolu `set(MEDIA_SOURCE_PLAY_STATUS, VDMediaPlay{13, 6,
 * info{reason=8}})`. Medya servisi bunu OEM ses uygulaması `BTMusicService`'e
 * iletir; o yalnızca reason 2/6/8'e tepki verir. Arkadaşın aracındaki reason=13
 * (REASON_SOURCE_LIST) bizim OEM ses uygulamasında yok sayılır. disPlayView=2 ile OEM ekranı açılmaz.
 * - Geçerli kaynak `CURRENT_SOURCE_TYPE` (393217), yük `media.bean.VDMediaType`.
 *
 * Kullanıcı Oynat'a ya da seçicide bir kaynağa bastığında, bir de telefonda Oynat'a basılınca ("Telefon önceliği",
 * [onBtPlayback]) çalışır; binder çağrıları arka plandadır. Günlük etiketi `MediaSource`
 * (yalnızca kaynak numarası, yol ve süre; başlık/ad/adres yok).
 */
internal object OemAudioSource {

    const val TAG = "MediaSource"

    const val BT_MUSIC = 13
    const val PLAY_ACTION_OPEN_SOURCE = 6
    /** VDValueMedia REASON_CARDS: OEM kart ve SystemUI yolu, iki araçta da var. */
    const val REASON_CARDS = 8
    const val DISPLAY_BACKGROUND = 2
    const val INTENT_DEFAULT_VIEW = 1

    /** VDBTMusicPlay playCtrl=1 (PLAY), playType=0 (OEM ses uygulamasıyla aynı). */
    const val BT_PLAY_CTRL_PLAY = 1
    const val BT_PLAY_TYPE_DEFAULT = 0

    /**
     * İleri/Geri: VDBTMusicPlay playCtrl 5/4 (OEM BT servisi 4 → skipToPrevious, 5 → skipToNext) ve VDMediaPlay
     * playAction 5/4 (OEM ana ekran kartıyla aynı; medya servisi 4 → önceki, 5 → sonraki).
     */
    const val CTRL_NEXT = 5
    const val CTRL_PREV = 4

    /** Komut gittikten sonra BT oturumuna yedek oynat göndermeden önce beklenen süre (odak el değiştirsin). */
    private const val SETTLE_MS = 800L
    private const val BIND_WAIT_MS = 1_500L

    private val io by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "oem-audio-source").apply { isDaemon = true } } }
    private val main by lazy { Handler(Looper.getMainLooper()) }

    @Volatile private var bus: VDBus? = null

    /** Yedek yol: OEM kartıyla aynı yük, ama arka planda (OEM medya ekranı öne gelmez). */
    fun switchPayload(type: Int = BT_MUSIC): VDMediaPlay = VDMediaPlay().apply {
        putMediaType(type)
        putPlayAction(PLAY_ACTION_OPEN_SOURCE)
        putMediaPlayActionInfo(VDMediaPlayActionInfo().apply {
            putReason(REASON_CARDS)
            putDisPlayView(DISPLAY_BACKGROUND)
            putIntent(INTENT_DEFAULT_VIEW)
        })
    }

    /** İlk yol: OEM ses uygulamasının BT oynat yüküyle aynı. */
    fun btPlayPayload(): VDBTMusicPlay = btCtrlPayload(BT_PLAY_CTRL_PLAY)

    /** OEM ses uygulamasının BT denetim yüküyle aynı (playType 0). */
    fun btCtrlPayload(ctrl: Int): VDBTMusicPlay = VDBTMusicPlay().apply {
        putPlayCtrl(ctrl)
        putPlayType(BT_PLAY_TYPE_DEFAULT)
    }

    /**
     * OEM başlatıcı kartının İleri/Geri yükü; kart tıklaması bilgi eklemez.
     *
     * Radyoda (9/10) OEM kartıyla aynı zincir, 7 Kasım günlüğüyle doğrulandı: medya servisi 5/4'ü `FM_AM_PLAY_STATUS`
     * NEXT_CHANNEL 7 / PREV_CHANNEL 6'ya çevirir, OEM radyo servisi OEM ses uygulamasına "next"/"pre"
     * yollar, OEM ses uygulaması çoklu kipte `MULTI_SEEK_FORWARD/BACKWARD` → istasyon LİSTESİNDE sonraki/önceki
     * (araçta `RadioControlTool dealMessage: radioAction = MULTI_SEEK_FORWARD` görüldü).
     * Liste boşsa (AM hiç taranmamış, frekans 522 kHz'de kaldı) OEM kartında da istasyon değişmez. Frekans adımı
     * (NEXT_FREQUENCY 9 / PREV_FREQUENCY 8) OEM radyo servisinde boş: başka bir yol yok.
     */
    fun skipPayload(type: Int, next: Boolean): VDMediaPlay = VDMediaPlay().apply {
        putMediaType(type)
        putPlayAction(if (next) CTRL_NEXT else CTRL_PREV)
    }

    /**
     * Kullanıcı Oynat'a bastı ve bir Bluetooth oturumu var: OEM BT servisine her zaman çal komutu gider (odak 42 alınır,
     * kaynak 13 olsa da; bkz. [MediaSourcePick.Path]); [play] yedek olarak ana iş parçacığında biraz sonra çalışır
     * (zararsız: oturum zaten çalıyorsa onPlay bir şey yapmaz). VDBus yoksa yalnızca [play].
     */
    fun playBluetooth(context: Context, play: Runnable) {
        markOwnPlay()
        ensureBus(context)
        io.execute {
            val t0 = SystemClock.elapsedRealtime()
            // İki servisin bağlanması aynı anda başlar: bekleme en çok BIND_WAIT_MS (art arda ~3 sn değil).
            startBind(VDServiceDef.ServiceType.MEDIA)
            startBind(VDServiceDef.ServiceType.BT)
            val media = connect(VDServiceDef.ServiceType.MEDIA)
            val current = media?.let { readSource(it) }
            val btBus = connect(VDServiceDef.ServiceType.BT)
            val path = MediaSourcePick.path(current, btBus != null, media != null)
            val sent = try {
                when (path) {
                    MediaSourcePick.Path.BT_PLAY ->
                        btBus?.set(VDBTMusicPlay.createEvent(VDEventBT.MUSIC_PLAY, btPlayPayload()))
                    MediaSourcePick.Path.OPEN_SOURCE ->
                        media?.set(VDMediaPlay.createEvent(VDEventMedia.MEDIA_SOURCE_PLAY_STATUS, switchPayload()))
                    else -> Unit
                }
                true
            } catch (t: Throwable) {
                Log.w(TAG, "play: path=$path failed ${t.javaClass.simpleName}")
                false
            }
            val ms = SystemClock.elapsedRealtime() - t0
            val commanded = sent && (path == MediaSourcePick.Path.BT_PLAY || path == MediaSourcePick.Path.OPEN_SOURCE)
            if (commanded) {
                Log.i(TAG, "play: oemSource=$current path=$path sent ${ms}ms, session play in ${SETTLE_MS}ms")
                main.postDelayed(play, SETTLE_MS)
            } else {
                Log.i(TAG, "play: oemSource=$current path=$path ${ms}ms, session play")
                main.post(play)
            }
        }
    }

    /**
     * Kart İleri/Geri ([next]); [activePkg] kartın oturumu. Yol [MediaSourcePick.skipPath]: BT oturumu → OEM BT servisine
     * `MUSIC_PLAY{5|4}`; OEM ses oturumu (radyo/USB) → OEM kartı gibi `MEDIA_SOURCE_PLAY_STATUS{kaynak, 5|4}`; gerisi
     * ya da komut gidemezse [session] (ana iş parçacığında). Yalnızca kullanıcı dokunuşuyla çağrılır.
     */
    fun skip(context: Context, next: Boolean, activePkg: String?, session: Runnable) {
        val bt = MediaSourcePick.isBluetooth(activePkg)
        if (!bt && activePkg != null && !MediaSourcePick.isOemAudio(activePkg)) {
            session.run() // üçüncü taraf uygulama: OEM kaynağına dokunulmaz, VDBus beklenmez
            return
        }
        if (ensureBus(context) == null) {
            session.run()
            return
        }
        if (bt) markOwnPlay() // duraklamış telefonda İleri çalmayı başlatabilir: telefon önceliği bunu kendi geçişi sayar
        io.execute {
            val t0 = SystemClock.elapsedRealtime()
            val type = if (bt) VDServiceDef.ServiceType.BT else VDServiceDef.ServiceType.MEDIA
            startBind(type)
            val b = connect(type)
            var source = currentSource
            if (!bt && b != null) source = readSource(b) ?: source
            val path = MediaSourcePick.skipPath(activePkg, source, bt && b != null, !bt && b != null)
            val ctrl = if (next) CTRL_NEXT else CTRL_PREV
            val sent = try {
                when (path) {
                    MediaSourcePick.SkipPath.BT_CTRL ->
                        b?.set(VDBTMusicPlay.createEvent(VDEventBT.MUSIC_PLAY, btCtrlPayload(ctrl)))
                    MediaSourcePick.SkipPath.MEDIA_CTRL ->
                        b?.set(VDMediaPlay.createEvent(VDEventMedia.MEDIA_SOURCE_PLAY_STATUS, skipPayload(source ?: 0, next)))
                    MediaSourcePick.SkipPath.SESSION -> Unit
                }
                path != MediaSourcePick.SkipPath.SESSION
            } catch (t: Throwable) {
                Log.w(TAG, "skip: path=$path failed ${t.javaClass.simpleName}")
                false
            }
            val ms = SystemClock.elapsedRealtime() - t0
            Log.i(TAG, "skip: ctrl=$ctrl oemSource=$source path=$path sent=$sent ${ms}ms")
            if (!sent) main.post(session)
        }
    }

    /** Kart, OEM kaynağı değişince yeniden seçim yapsın diye (ana iş parçacığında çağrılır). */
    fun interface SourceListener {
        fun onOemSourceChanged(type: Int)
    }

    private val sourceListeners = CopyOnWriteArraySet<SourceListener>()

    /** Kaynak ve canlı geçiş durumu ([SourceFollow]); `this` kilidi altında. */
    private val sourceFollow = SourceFollow()

    /** Son bilinen `CURRENT_SOURCE_TYPE` (abonelik ya da okuma; bilinmiyorsa null). */
    val currentSource: Int?
        @Synchronized get() = sourceFollow.source

    @Volatile private var watching = false

    /** Kartın izleyeceği kaynak ([SourceFollow.follow]); [hasBt]: aday oturumlarda Bluetooth oturumu var mı. */
    @Synchronized
    fun followSource(hasBt: Boolean): Int? = sourceFollow.follow(hasBt)

    private val notifyListener = VDNotifyListener { event, _ ->
        if (event == null || event.id != VDEventMedia.CURRENT_SOURCE_TYPE) return@VDNotifyListener
        val type = try {
            VDMediaType.getValue(event)?.mediaType
        } catch (t: Throwable) {
            Log.w(TAG, "source notify: ${t.javaClass.simpleName}")
            null
        }
        if (type != null) update(type, live = true)
    }

    private val bindListener = object : VDBindListener {
        override fun onVDConnected(serviceType: VDServiceDef.ServiceType?) {
            // Medya servisi (yeniden) bağlandı: abonelik VDConnector'da yenilenir, değer yeniden okunur.
            if (serviceType == VDServiceDef.ServiceType.MEDIA && watching) io.execute { bus?.let { readSource(it) } }
        }

        override fun onVDDisconnected(serviceType: VDServiceDef.ServiceType?) = Unit
    }

    /**
     * `CURRENT_SOURCE_TYPE` (393217) aboneliği, OEM başlatıcısı gibi.
     * Abonelik süreç boyunca tek; [listener] [unwatchSource] ile çıkarılır. Ana iş parçacığından çağrılır.
     */
    fun watchSource(context: Context, listener: SourceListener) {
        sourceListeners.add(listener)
        if (watching) return
        val b = ensureBus(context) ?: return
        watching = true
        io.execute {
            try {
                b.registerVDBindListener(bindListener)
                b.registerVDNotifyListener(notifyListener)
                b.addSubscribe(VDEventMedia.CURRENT_SOURCE_TYPE)
                b.subscribeCommit()
            } catch (t: Throwable) {
                Log.w(TAG, "subscribe: ${t.javaClass.simpleName}")
            }
            startBind(VDServiceDef.ServiceType.MEDIA)
            // BT servisi de önceden bağlanır: telefon önceliği OEM'in 1 sn'lik duraklatma süresi içinde gitmeli.
            startBind(VDServiceDef.ServiceType.BT)
            connect(VDServiceDef.ServiceType.MEDIA)?.let { readSource(it) }
        }
    }

    fun unwatchSource(listener: SourceListener) {
        sourceListeners.remove(listener)
    }

    /** io: `getOnce(CURRENT_SOURCE_TYPE)`; okunursa önbellek güncellenir. */
    private fun readSource(b: VDBus): Int? {
        val type = try {
            VDMediaType.getValue(b.getOnce(VDEventMedia.CURRENT_SOURCE_TYPE))?.mediaType
        } catch (t: Throwable) {
            Log.w(TAG, "source read: ${t.javaClass.simpleName}")
            null
        }
        if (type != null) update(type, live = false)
        return type
    }

    @Synchronized
    private fun update(type: Int, live: Boolean) {
        val old = sourceFollow.source
        // İlk değer geçiş sayılmaz: BT yeniden bağlanınca radyoda kalan kaynak kartı radyoya çekmesin (6 Kasım).
        // Sonraki her değişim (bildirim ya da aboneliğin kaçırdığını yakalayan okuma) geçiştir ([SourceFollow]).
        if (!sourceFollow.onSource(type)) return
        Log.i(TAG, "source: $old -> $type live=$live")
        // Kullanıcı OEM uygulamasında BT dışı kaynak seçti: telefon önceliği bir süre karışmaz. BT kopunca OEM'in kendi
        // radyoya düşüşü seçim sayılmaz ([PhonePriority.onOemSourceAway]).
        if (old != null && type != BT_MUSIC) {
            val pick = phonePriority.onOemSourceAway(SystemClock.elapsedRealtime())
            Log.i(TAG, "phone-priority: oemSourceAway=$type userPick=$pick")
        }
        main.post { sourceListeners.forEach { it.onOemSourceChanged(type) } }
    }

    // --- Telefon önceliği ---

    /** Kilit: `this` ([update] ile aynı). */
    private val phonePriority = PhonePriority()

    @Synchronized
    private fun markOwnPlay() = phonePriority.onOwnPlay(SystemClock.elapsedRealtime())

    /**
     * `com.android.bluetooth` A2DP oturumunun çalma durumu (süreç boyu yaşayan BtSessionWatcher her gözlemde çağırır;
     * [playing] null: BT oturumu yok ya da koptu). Duraklamış → çalıyor geçişinde [PhonePriority] "geç" derse kartın
     * Oynat'ıyla aynı BT geçişi gider ([sendBtSwitch]); telefon zaten çaldığı için oturuma ayrıca oynat gönderilmez.
     * Ana iş parçacığında çağrılır.
     */
    fun onBtPlayback(context: Context, playing: Boolean?) {
        val now = SystemClock.elapsedRealtime()
        // Ayar ve ses kipi kilit dışında okunur (update/readSource io'da aynı kilidi alır; ilk okuma diske gidebilir).
        // Yalnızca geçişte okunur (oturum çalarken ~500 ms'de bir durum yollar); karar yine kilit altında verilir.
        val transition = playing == true && synchronized(this) { phonePriority.isTransition(true) }
        val enabled = transition && MediaPrefs.phonePriority(context)
        val calling = transition && inCall(context)
        var source: Int? = null
        val decision = synchronized(this) {
            source = sourceFollow.source
            if (playing == null) {
                phonePriority.onBtGone(now)
                null
            } else {
                phonePriority.onBtState(playing, now, source, { enabled }, { calling })
            }
        } ?: return
        if (decision == PhonePriority.Decision.NO_TRANSITION) return
        if (decision != PhonePriority.Decision.SWITCH) {
            Log.i(TAG, "phone-priority: skip=$decision oemSource=$source")
            return
        }
        if (ensureBus(context) == null) {
            Log.i(TAG, "phone-priority: no VDBus oemSource=$source")
            return
        }
        val from = source
        io.execute { sendBtSwitch("phone-priority", from) }
    }

    /** Arama ya da sesli görüşme sürüyor mu (ses kipi normal değil). */
    private fun inCall(context: Context): Boolean = try {
        val mode = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.mode
        mode != null && mode != AudioManager.MODE_NORMAL
    } catch (t: Throwable) {
        false
    }

    /**
     * io: kartın Oynat'ındaki BT geçişi, oturum oynatı olmadan: BT servisi bağlıysa `MUSIC_PLAY{1,0}` (OEM BT servisi odak
     * 42'yi ister; telefon bu arada duraklatıldıysa yeniden çalar), yoksa `OPEN_SOURCE{13, reason 8}`.
     */
    private fun sendBtSwitch(tag: String, source: Int?) {
        val t0 = SystemClock.elapsedRealtime()
        startBind(VDServiceDef.ServiceType.BT)
        startBind(VDServiceDef.ServiceType.MEDIA)
        val btBus = connect(VDServiceDef.ServiceType.BT)
        val media = if (btBus == null) connect(VDServiceDef.ServiceType.MEDIA) else null
        val path = MediaSourcePick.path(source, btBus != null, media != null)
        val sent = try {
            when (path) {
                MediaSourcePick.Path.BT_PLAY -> btBus?.set(VDBTMusicPlay.createEvent(VDEventBT.MUSIC_PLAY, btPlayPayload()))
                MediaSourcePick.Path.OPEN_SOURCE ->
                    media?.set(VDMediaPlay.createEvent(VDEventMedia.MEDIA_SOURCE_PLAY_STATUS, switchPayload()))
                else -> Unit
            }
            path == MediaSourcePick.Path.BT_PLAY || path == MediaSourcePick.Path.OPEN_SOURCE
        } catch (t: Throwable) {
            Log.w(TAG, "$tag: path=$path failed ${t.javaClass.simpleName}")
            false
        }
        Log.i(TAG, "$tag: oemSource=$source path=$path sent=$sent ${SystemClock.elapsedRealtime() - t0}ms")
    }

    // --- Kaynak seçici ---

    /**
     * Kart seçicisinden BT dışı kaynak ([type]: FM 9, AM 10, USB0 3): OEM kartı / SystemUI gibi `MEDIA_SOURCE_PLAY_STATUS
     * {type, playAction 6, reason 8}`, ama arka planda
     * (disPlayView 2; intent görünümü yalnızca ön planda anlamlı). Telefon önceliği
     * bir süre karışmaz. Yalnızca kullanıcı dokunuşuyla çağrılır.
     */
    fun openSource(context: Context, type: Int) {
        synchronized(this) { phonePriority.onUserPick(SystemClock.elapsedRealtime()) }
        if (ensureBus(context) == null) {
            Log.i(TAG, "pick: target=$type no VDBus")
            return
        }
        io.execute {
            val t0 = SystemClock.elapsedRealtime()
            startBind(VDServiceDef.ServiceType.MEDIA)
            val media = connect(VDServiceDef.ServiceType.MEDIA)
            val sent = try {
                media?.set(VDMediaPlay.createEvent(VDEventMedia.MEDIA_SOURCE_PLAY_STATUS, switchPayload(type)))
                media != null
            } catch (t: Throwable) {
                Log.w(TAG, "pick: target=$type failed ${t.javaClass.simpleName}")
                false
            }
            Log.i(TAG, "pick: target=$type path=OPEN_SOURCE sent=$sent ${SystemClock.elapsedRealtime() - t0}ms")
        }
    }

    /** Seçicide Bluetooth ama oynatılacak BT oturumu yok: oturumsuz BT geçişi ([sendBtSwitch]). */
    fun openBluetoothSource(context: Context) {
        markOwnPlay()
        if (ensureBus(context) == null) return
        val from = currentSource
        io.execute { sendBtSwitch("pick", from) }
    }

    /**
     * USB0 müziği bağlı mı (salt okuma): OEM SystemUI gibi `getOnce(MEDIA_SOURCE_EXT_INFO{mediaType 3})`
     * → connectState ([SourceChoice.usbConnected]). Sonuç ana iş parçacığında [result]'a; okunamazsa false.
     */
    fun checkUsb(context: Context, result: (Boolean) -> Unit) {
        if (ensureBus(context) == null) {
            result(false)
            return
        }
        io.execute {
            startBind(VDServiceDef.ServiceType.MEDIA)
            val state = connect(VDServiceDef.ServiceType.MEDIA)?.let { b ->
                try {
                    val q = VDMediaExtInfo().apply { putMediaType(SourceChoice.USB0) }
                    VDMediaExtInfo.getValue(b.getOnce(VDMediaExtInfo.createEvent(VDEventMedia.MEDIA_SOURCE_EXT_INFO, q)))?.connectState
                } catch (t: Throwable) {
                    Log.w(TAG, "usb read: ${t.javaClass.simpleName}")
                    null
                }
            }
            val ok = SourceChoice.usbConnected(state)
            Log.i(TAG, "usb: connectState=$state available=$ok")
            main.post { result(ok) }
        }
    }

    /** VDBus init ana iş parçacığında (PhoneBackends/FavoriteKey gibi); bağlanma ve binder çağrıları arka planda. */
    private fun ensureBus(context: Context): VDBus? {
        bus?.let { return it }
        return try {
            VDBus.getDefault().also { it.init(context.applicationContext) }.also { bus = it }
        } catch (t: Throwable) {
            Log.w(TAG, "VDBus init: ${t.javaClass.simpleName}")
            null
        }
    }

    /** io: [type] servisi bağlı değilse bağlanmayı başlatır, beklemez. */
    private fun startBind(type: VDServiceDef.ServiceType) {
        val b = bus ?: return
        try {
            if (!b.isServiceConnected(type)) b.bindService(type)
        } catch (t: Throwable) {
            Log.w(TAG, "bind $type: ${t.javaClass.simpleName}")
        }
    }

    /** io: [startBind] sonrası [type] servisinin bağlanmasını bekler (en çok [BIND_WAIT_MS]); bağlanamazsa null. */
    private fun connect(type: VDServiceDef.ServiceType): VDBus? {
        val b = bus ?: return null
        return try {
            // Bağlanma [startBind] ile başladı; burada yalnızca beklenir (bindService iki kez çağrılmaz).
            val until = SystemClock.elapsedRealtime() + BIND_WAIT_MS
            while (!b.isServiceConnected(type) && SystemClock.elapsedRealtime() < until) {
                SystemClock.sleep(100)
            }
            if (b.isServiceConnected(type)) b else null
        } catch (t: Throwable) {
            Log.w(TAG, "bind $type: ${t.javaClass.simpleName}")
            null
        }
    }
}

/** Müzik kartı seçenekleri (profil başına). */
internal object MediaPrefs {
    private const val PREFS_NAME = "launcher_settings"
    private const val KEY_PHONE_PRIORITY = "media_phone_priority"

    /** "Telefon önceliği": telefondan çalınca OEM kaynağı Bluetooth'a geçer ([PhonePriority]); varsayılan AÇIK. */
    fun phonePriority(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(Profiles.key(context, KEY_PHONE_PRIORITY), true)

    fun setPhonePriority(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(Profiles.key(context, KEY_PHONE_PRIORITY), on).apply()
    }
}
