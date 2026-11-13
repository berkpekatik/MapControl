package com.mapcontrol.launcher

/**
 * Telefon önceliği kararı (saf, JVM'de sınanır; [OemAudioSource] kilit altında kullanır).
 *
 * Araç testi: kaynak AM (10) iken telefonda Oynat'a basıldı. `com.android.bluetooth` A2DP oturumu PLAYING
 * oldu (OEM BT servisi `BTSer-MediaCON onPlaybackStateChanged: playState=3`; aynı oturumu
 * MediaBrowser ile izliyor), ama OEM BT servisi odak 42'yi yalnızca `carAudioType` listesindeyse ister
 * (liste boş): istemedi, `checkAudioFocus` 1000 ms sonra (postDelayed
 * 1000) telefonu duraklattı (AVRCP PAUSE, keyCode 70). OEM telefonu öne almıyor; bu sınıf "geç" kararını verir.
 *
 * Yalnızca BT oturumunun duraklamış → çalıyor geçişinde (süreç başında oturum zaten varken ilk gözlem geçiş sayılmaz; oturum
 * yokken görülmesi "duraklamış" sayılır) ve şunların hiçbiri yoksa geçilir: ayar kapalı, kaynak bilinmiyor ya da zaten 13,
 * arama sürüyor, son [ownMs] içinde kendi Oynat'ımız (geçişi biz yaptık), son [userPickMs] içinde kullanıcı başka kaynak
 * seçti, son geçişten [debounceMs] geçmedi.
 *
 * Kullanıcı seçimi: bizim seçici her zaman; OEM'de `CURRENT_SOURCE_TYPE`'ın 13 dışına geçmesi yalnızca BT oturumu varken ve
 * bağlantı kopmamışken ([lostGraceMs] içinde, iki yönde de). OEM A2DP servisi kopuşta oturumu pasif yapar ve durumu
 * ERROR'a çeker: OEM'in kendi 13 → radyo düşüşü kullanıcı seçimi
 * sayılsaydı telefon yeniden bağlanıp çalınca geçilmez, OEM 1 sn sonra telefonu duraklatırdı (asıl şikâyet).
 */
internal class PhonePriority(
    private val debounceMs: Long = DEBOUNCE_MS,
    private val userPickMs: Long = USER_PICK_MS,
    private val ownMs: Long = OWN_MS,
    private val lostGraceMs: Long = LOST_GRACE_MS,
) {
    enum class Decision {
        NO_TRANSITION, SWITCH, DISABLED, UNKNOWN_SOURCE, ALREADY_BT, IN_CALL, OWN_PLAY, USER_PICK, DEBOUNCE,
        /** Telefon parça bilgisi göndermedi (kilit açılınca reels/bildirim sesi gibi): [retry] ile kısa süre yeniden sorulur. */
        NO_TRACK_INFO,
    }

    /** Son bilinen BT oturumu çalıyor mu (hiç gözlenmediyse null). */
    private var btPlaying: Boolean? = null
    /** BT oturumu şu an var mı (son gözlem bir durumdu, "yok" değil). */
    private var btPresent = false
    private var lastSwitch = Long.MIN_VALUE / 2
    private var lastUserPick = Long.MIN_VALUE / 2
    private var lastOemAway = Long.MIN_VALUE / 2
    private var lastBtLost = Long.MIN_VALUE / 2
    private var lastOwn = Long.MIN_VALUE / 2

    /** Kendi kartımız / seçicimiz BT'yi çaldırdı (Oynat, İleri/Geri, seçicide Bluetooth). */
    fun onOwnPlay(now: Long) {
        lastOwn = now
    }

    /** Kullanıcı bizim seçicide BT dışı bir kaynak seçti. */
    fun onUserPick(now: Long) {
        lastUserPick = now
    }

    /**
     * OEM kaynağı canlı olarak BT dışı bir kaynağa geçti. Kullanıcı seçimi sayılır mı (true: sayıldı): BT oturumu varken ve
     * son [lostGraceMs] içinde kopmadıysa. Kopuş biraz sonra gelirse [onBtGone] bunu geri alır.
     */
    fun onOemSourceAway(now: Long): Boolean {
        if (!btPresent || now - lastBtLost < lostGraceMs) return false
        lastOemAway = now
        return true
    }

    /**
     * BT oturumu yok (pasif / koptu): bir sonraki çalma geçiş sayılır. Süreç başında oturum yoksa da "duraklamış" sayılır:
     * telefon sonradan bağlanıp çalarken gelirse öncelik çalışır. Az önceki OEM kaynak değişimi kopuşun düşüşüdür.
     */
    fun onBtGone(now: Long) {
        btPlaying = false
        btPresent = false
        lastBtLost = now
        if (now - lastOemAway < lostGraceMs) lastOemAway = Long.MIN_VALUE / 2
    }

    /** [onBtState] bu durumla duraklamış → çalıyor geçişi görür mü (ayar / arama okumasını geçişe saklamak için). */
    fun isTransition(playing: Boolean): Boolean = playing && btPlaying == false

    /** [NO_TRACK_INFO] sonrası bilgi bekleniyor (geçiş anı; yoksa MIN). */
    private var infoPendingSince = Long.MIN_VALUE / 2
    private var infoPending = false

    /**
     * [enabled], [inCall] ve [hasTrackInfo] yalnızca geçişte sorulur (BT oturumu ~500 ms'de bir durum yollar). Parça
     * bilgisi en son sorulur: diğer engellerden biri varsa beklemeye gerek yoktur.
     */
    fun onBtState(
        playing: Boolean,
        now: Long,
        oemSource: Int?,
        enabled: () -> Boolean,
        inCall: () -> Boolean,
        hasTrackInfo: () -> Boolean = { true },
    ): Decision {
        val prev = btPlaying
        btPlaying = playing
        btPresent = true
        if (!playing) infoPending = false
        if (!playing || prev != false) return Decision.NO_TRANSITION
        infoPendingSince = now
        return decide(now, oemSource, enabled, inCall, hasTrackInfo)
    }

    /**
     * Geçişte parça bilgisi yoktu ([Decision.NO_TRACK_INFO]); bilgi sonradan gelmiş olabilir. Yalnızca BT hâlâ çalarken ve
     * geçişten [infoWaitMs] içinde sorulur; aksi halde [Decision.NO_TRANSITION]. Diğer engeller yeniden denetlenir.
     */
    fun retry(
        now: Long,
        oemSource: Int?,
        enabled: () -> Boolean,
        inCall: () -> Boolean,
        hasTrackInfo: () -> Boolean,
        infoWaitMs: Long = INFO_WAIT_MS,
    ): Decision {
        if (!infoPending || btPlaying != true || now - infoPendingSince > infoWaitMs) {
            infoPending = false
            return Decision.NO_TRANSITION
        }
        return decide(now, oemSource, enabled, inCall, hasTrackInfo)
    }

    private fun decide(
        now: Long,
        oemSource: Int?,
        enabled: () -> Boolean,
        inCall: () -> Boolean,
        hasTrackInfo: () -> Boolean,
    ): Decision {
        val d = when {
            !enabled() -> Decision.DISABLED
            oemSource == null -> Decision.UNKNOWN_SOURCE
            oemSource == OemAudioSource.BT_MUSIC -> Decision.ALREADY_BT
            inCall() -> Decision.IN_CALL
            now - lastOwn < ownMs -> Decision.OWN_PLAY
            now - maxOf(lastUserPick, lastOemAway) < userPickMs -> Decision.USER_PICK
            now - lastSwitch < debounceMs -> Decision.DEBOUNCE
            !hasTrackInfo() -> Decision.NO_TRACK_INFO
            else -> {
                lastSwitch = now
                Decision.SWITCH
            }
        }
        infoPending = d == Decision.NO_TRACK_INFO
        return d
    }

    companion object {
        const val DEBOUNCE_MS = 5_000L
        const val USER_PICK_MS = 10_000L
        const val OWN_MS = 5_000L
        const val LOST_GRACE_MS = 5_000L
        /** OEM BT servisi odak yoksa telefonu ~1000 ms sonra duraklatır: bilgi bundan önce gelmeli. */
        const val INFO_WAIT_MS = 900L
    }
}

/**
 * Telefonun gerçek bir parça mı çaldığı (saf). Kilit açılınca öndeki uygulamanın (reels, önizleme) ya da bildirimin kısa
 * sesi oturumu bir an PLAYING yapar ama çoğunlukla parça adı göndermez; telefon önceliği bunda geçmez. Bazı AVRCP
 * yığınları bilgi yokken yer tutucu ad yollar.
 */
internal object TrackInfo {
    private val PLACEHOLDERS = setOf("", "not provided", "unknown", "<unknown>", "no title", "bilinmiyor", "null")

    fun meaningful(title: String?): Boolean = title != null && title.trim().lowercase() !in PLACEHOLDERS

    fun present(title: String?, artist: String?): Boolean = meaningful(title) || meaningful(artist)
}

/**
 * Telefon önceliğinin "yalnızca odak" geçişi ([OemBtFocus]) tuttu mu (saf; [OemAudioSource] kilit altında kullanır).
 *
 * Odak istendikten sonra kaynak [OemAudioSource.BT_MUSIC]'e geçerse yol çalışıyor demektir. Denetim anında ([confirm])
 * geçmemişse bu araçta yol çalışmıyor sayılır (süreç boyunca eski `MUSIC_PLAY` yolu) ve BT hâlâ çalıyorsa bu geçiş de
 * eski yoldan yapılır. BT durmuşsa düşülmez: bildirim sesi gibi kısa bir çalmaydı, telefona oynat gönderilmemeli.
 */
internal class FocusOnlySwitch {
    /** null: henüz denenmedi; true: kaynak BT'ye geçti; false: geçmedi. */
    var works: Boolean? = null
        private set
    private var pending = false

    /** Bu araçta yalnızca odak yolu denenmeli mi (bir kez tutmadıysa hayır). */
    fun shouldTry(): Boolean = works != false

    fun onRequested() {
        pending = true
    }

    /** Kullanıcı kaynak seçti ya da kartın Oynat'ına bastı: denetim artık bir şey yapmaz. */
    fun cancel() {
        pending = false
    }

    /** OEM kaynak bildirimi. */
    fun onSource(type: Int) {
        if (pending && type == OemAudioSource.BT_MUSIC) {
            pending = false
            works = true
        }
    }

    /** Zamanlanmış denetim. true: eski yola düş (kaynak geçmedi, telefon hâlâ çalıyor). */
    fun confirm(source: Int?, btPlaying: Boolean?): Boolean {
        if (!pending) return false
        pending = false
        if (source == OemAudioSource.BT_MUSIC) {
            works = true
            return false
        }
        works = false
        return btPlaying == true
    }
}

/**
 * Telefon önceliğiyle yapılan geçişi kısa çalmada geri alma kararı (saf; [OemAudioSource] kilit altında kullanır).
 *
 * Telefon önceliği OEM'in kendi kaynağından (radyo/USB, [MediaSourcePick.isOemOwnSource]) BT'ye geçtikten sonra BT
 * [windowMs] içinde durur ve [settleMs] boyunca durmuş kalırsa önceki kaynak yeniden açılır: kilit açılınca gelen bir
 * anlık ses radyoyu kalıcı olarak kesmesin. Kullanıcı bu arada bir şey seçtiyse ([cancel]) ya da kaynak BT'den başka
 * yere geçtiyse geri alınmaz.
 */
internal class PriorityRevert(
    private val windowMs: Long = WINDOW_MS,
    private val settleMs: Long = SETTLE_MS,
) {
    private var from: Int? = null
    private var switchedAt = Long.MIN_VALUE / 2
    private var stoppedAt: Long? = null

    /** Telefon önceliği [from] kaynağından BT'ye geçti. Geri alınacak kaynak yalnızca OEM'in kendi kaynağıdır. */
    fun onSwitched(from: Int?, now: Long) {
        this.from = if (MediaSourcePick.isOemOwnSource(from)) from else null
        switchedAt = now
        stoppedAt = null
    }

    /** Kullanıcı kaynak seçti, kartın Oynat'ına bastı ya da kaynak BT dışına geçti. */
    fun cancel() {
        from = null
        stoppedAt = null
    }

    val pending: Boolean get() = from != null

    /** BT durumu. true: BT az önce durdu, [settleMs] sonra [due] sorulmalı. */
    fun onBtState(playing: Boolean?, now: Long): Boolean {
        if (from == null) return false
        if (now - switchedAt > windowMs && stoppedAt == null) {
            from = null // uzun çaldı: gerçek dinleme, geri alınmaz
            return false
        }
        if (playing == true) {
            stoppedAt = null
            return false
        }
        if (stoppedAt != null) return false
        stoppedAt = now
        return true
    }

    /** Zamanlanmış denetim: geri açılacak kaynak ya da null. [source] OEM'in şu anki kaynağı. */
    fun due(now: Long, playing: Boolean?, source: Int?): Int? {
        val target = from ?: return null
        val since = stoppedAt ?: return null
        if (playing == true) {
            stoppedAt = null
            return null
        }
        if (source != OemAudioSource.BT_MUSIC) {
            cancel() // geçiş tutmadı ya da kaynak zaten başka yerde
            return null
        }
        if (now - since < settleMs) return null
        cancel()
        return target
    }

    companion object {
        /** Geçişten sonra BT bu süre içinde durursa "anlık ses" sayılır. */
        const val WINDOW_MS = 8_000L
        /** Parça arası / tampon kısa durmaları geri dönüş tetiklemesin. */
        const val SETTLE_MS = 1_500L
    }
}

/**
 * Radyo oturumunun kartta gösterimi (saf). OEM ses uygulamasında FM/AM'de
 * TITLE = ham frekans (`String.valueOf(getRadioFrequency())`; RDS istasyon adı varsa o), ARTIST = "am" / "fm"
 * (`RadioMessage.getRadioMessageSource`, DAB'da "dab" değil topluluk etiketi). Ham frekans kHz'dir: FM'de
 * `getCalculateFrequency` 1000'e böler (89700 → 89.7 MHz), AM'de olduğu gibi (522 kHz).
 */
internal object RadioDisplay {

    enum class Band { FM, AM, DAB }

    class Shown(val band: Band, val title: String, val subtitle: String?)

    /** OEM kaynak türü → bant (VDValueMedia.MediaType FM 9, AM 10, DAB 24). */
    fun bandOfSource(type: Int?): Band? = when (type) {
        9 -> Band.FM
        10 -> Band.AM
        24 -> Band.DAB
        else -> null
    }

    private fun bandOfLabel(artist: String?): Band? = when (artist?.trim()?.lowercase()) {
        "fm" -> Band.FM
        "am" -> Band.AM
        "dab" -> Band.DAB
        else -> null
    }

    /**
     * [pkg] OEM ses uygulaması değilse null (radyo değil). Bant önce ARTIST etiketinden; yoksa (DAB ya da etiketsiz) OEM
     * kaynağından ([oemSource]) — ama USB müziği de aynı paketten çalar, bu yüzden kaynak yalnızca radyo türündeyse.
     */
    fun of(pkg: String?, title: String?, artist: String?, oemSource: Int?): Shown? {
        if (!MediaSourcePick.isOemAudio(pkg)) return null
        val label = bandOfLabel(artist)
        val band = label ?: bandOfSource(oemSource) ?: return null
        val t = title?.trim().orEmpty()
        val raw = t.toIntOrNull()
        // Etiket bandı söylediyse ARTIST alt satıra yazılmaz ("fm" bir kaynak adı, istasyon metni değil).
        val extra = if (label != null) null else artist?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            raw != null && band != Band.DAB -> Shown(band, frequency(band, raw), extra)
            t.isNotEmpty() -> Shown(band, t, if (label != null) bandName(band) else extra)
            else -> Shown(band, bandName(band), extra)
        }
    }

    fun bandName(band: Band) = band.name

    /** "FM 89,7" (kHz → MHz, en çok iki ondalık, en az bir), "AM 522 kHz". Aralık dışı FM değeri olduğu gibi yazılır. */
    fun frequency(band: Band, raw: Int): String = when (band) {
        Band.FM -> if (raw in 10_000..200_000) "FM " + mhz(raw) else "FM $raw"
        Band.AM -> "AM $raw kHz"
        Band.DAB -> "DAB $raw"
    }

    private fun mhz(khz: Int): String {
        val whole = khz / 1000
        val frac = khz % 1000
        val digits = String.format(java.util.Locale.ROOT, "%03d", frac).trimEnd('0').ifEmpty { "0" }.take(2)
        return "$whole,$digits"
    }
}

/**
 * Kartın kaynak seçicisi (saf). Türler OEM'de doğrulandı: OEM medya servisi 9 → FM
 * (bant 1), 10 → AM (bant 5), 3 → USB0 müziği, 13 → BT müziği; OPEN_SOURCE
 * reason 8 radyoda OEM radyo servisi ile FM_SOURCE / AM_SOURCE'u açar. USB yalnızca medya servisi bağlı
 * derse (`MEDIA_SOURCE_EXT_INFO` connectState 2/3, VDValueMedia.MediaConnectState; OEM SystemUI).
 */
internal object SourceChoice {

    const val FM = 9
    const val AM = 10
    const val USB0 = 3

    /** VDValueMedia.MediaConnectState: 1 bağlı değil, 2 bağlı (taranıyor), 3 bağlı (tarandı). */
    fun usbConnected(connectState: Int?): Boolean = connectState == 2 || connectState == 3

    /** Seçicide gösterilecek türler, sırayla: Bluetooth, FM, AM, (USB bağlıysa) USB. */
    fun entries(usbAvailable: Boolean): List<Int> =
        if (usbAvailable) listOf(OemAudioSource.BT_MUSIC, FM, AM, USB0) else listOf(OemAudioSource.BT_MUSIC, FM, AM)

    /** Satır işaretli mi: OEM kaynağı bu tür. Kart üçüncü taraf uygulamadaysa işaret onda olur (OEM kaynağı eski değerde kalabilir). */
    fun isCurrent(entry: Int, current: Int?, cardOnApp: Boolean = false): Boolean = !cardOnApp && current == entry
}
