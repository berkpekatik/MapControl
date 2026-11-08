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
    enum class Decision { NO_TRANSITION, SWITCH, DISABLED, UNKNOWN_SOURCE, ALREADY_BT, IN_CALL, OWN_PLAY, USER_PICK, DEBOUNCE }

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

    /** [enabled] ve [inCall] yalnızca geçişte sorulur (BT oturumu ~500 ms'de bir durum yollar). */
    fun onBtState(playing: Boolean, now: Long, oemSource: Int?, enabled: () -> Boolean, inCall: () -> Boolean): Decision {
        val prev = btPlaying
        btPlaying = playing
        btPresent = true
        if (!playing || prev != false) return Decision.NO_TRANSITION
        return when {
            !enabled() -> Decision.DISABLED
            oemSource == null -> Decision.UNKNOWN_SOURCE
            oemSource == OemAudioSource.BT_MUSIC -> Decision.ALREADY_BT
            inCall() -> Decision.IN_CALL
            now - lastOwn < ownMs -> Decision.OWN_PLAY
            now - maxOf(lastUserPick, lastOemAway) < userPickMs -> Decision.USER_PICK
            now - lastSwitch < debounceMs -> Decision.DEBOUNCE
            else -> {
                lastSwitch = now
                Decision.SWITCH
            }
        }
    }

    companion object {
        const val DEBOUNCE_MS = 5_000L
        const val USER_PICK_MS = 10_000L
        const val OWN_MS = 5_000L
        const val LOST_GRACE_MS = 5_000L
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

    /** Satır işaretli mi: OEM kaynağı bu tür. */
    fun isCurrent(entry: Int, current: Int?): Boolean = current == entry
}
