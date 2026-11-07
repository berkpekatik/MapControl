package com.mapcontrol.launcher

import java.text.Collator
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/*
 * Telefon arka ucunun saf mantığı (Android çağrısı yok; JVM testli: PhoneLogicTest).
 * GİZLİLİK: buradaki hiçbir işlev isim/numara günlüğe yazmaz.
 */

/**
 * VDBus VDBTDialInfo'nun test edilebilir özeti (Parcel gerektirmez). [state] = VDValueBT.DialStatus. [uuid]: OEM
 * servisinin arama kimliği (kişisel veri değil); VDBus ile kapatma/reddetme bunu ister.
 */
internal data class DialInfoLite(val state: Int, val outGoing: Boolean = false, val uuid: String? = null)

/**
 * VDBus DIAL (262545) için hangi aramanın kimliğinin (uuid) gönderileceği. OEM servisi kapatmayı (dialCtrl 3)
 * terminateCall(addr, uuid) ile yapar ve uuid boşsa hiçbir şey yapmaz; ayrı bir
 * "reddet" komutu yok, OEM arayüzü de gelen aramayı aynı yolla reddeder.
 */
internal object VdCallPick {
    /** Reddedilecek: gelen ya da bekleyen arama. Yoksa null (arka uç başarısız sayılır, sıradaki yol denenir). */
    fun forReject(list: List<DialInfoLite>): String? =
        list.firstOrNull { (it.state == PhoneStateMap.INCOMING || it.state == PhoneStateMap.WAITING) && !it.uuid.isNullOrBlank() }?.uuid

    /** Kapatılacak: konuşma/aranıyor/çalıyor; yoksa ekranda gösterilen arama ([PhoneStateMap.relevantIndex]). */
    fun forHangUp(list: List<DialInfoLite>): String? {
        val live = list.firstOrNull {
            (it.state == PhoneStateMap.ACTIVE || it.state == PhoneStateMap.DIALING || it.state == PhoneStateMap.ALERTING) &&
                !it.uuid.isNullOrBlank()
        }
        return live?.uuid ?: list.getOrNull(PhoneStateMap.relevantIndex(list))?.uuid?.takeIf { it.isNotBlank() }
    }

    /**
     * Cevaplama türü (VDValueBT.AcceptCallType). Araç HFP yığını tek gelen aramada (durum 4) bayrak 0 değilse hiçbir şey
     * yapmaz (araç HFP yığını); bekleyen aramada (5) 0 ve 1 ikisi de "kabul et, ötekini
     * beklet" demektir. Bu yüzden her zaman NONE (0); VDBTDial'ın varsayılanı 1 tek gelen aramada yok sayılıyordu.
     */
    const val ACCEPT_TYPE = 0
}

/**
 * Projeksiyon (CarPlay/Android Auto/HiCar/CarLife/CarLink) bağlantısı: VDBus PHONELINK PHONE_STATE (851970)
 * yükündeki "status" ve "type" (CommonLinkService PhoneLinkVDDispatcher). Bağlıyken direksiyon ses tuşunu OEM
 * projeksiyon uygulaması telefona iletir (DesaySVAndroidAuto/CarPlay KeyEventImpl, tuş 6 → projeksiyon VR tuşu).
 */
internal object ProjectionLink {
    /** PhoneLinkProto.PhoneStatus.CONNECTED. */
    const val STATUS_CONNECTED = 2
    /** LinkDeviceProto.DeviceType: CARPLAY 1, APPLE_CARLIFE 2, ANDROID_CARLIFE 3, ANDROID_AUTO 4, HICAR 7, CARLINK 8. */
    private val TYPES = setOf(1, 2, 3, 4, 7, 8)

    fun active(status: Int, type: Int): Boolean = status == STATUS_CONNECTED && type in TYPES
}

/** Arama durumu eşlemeleri (VDBus, HFP istemci, arama geçmişi). */
internal object PhoneStateMap {
    // VDValueBT.DialStatus ile BluetoothHeadsetClientCall.CALL_STATE_* aynı sayılardır.
    const val ACTIVE = 0
    const val HELD = 1
    const val DIALING = 2
    const val ALERTING = 3
    const val INCOMING = 4
    const val WAITING = 5
    const val HELD_BY_RESPONSE_AND_HOLD = 6
    const val TERMINATED = 7
    const val MISSED = 8

    private fun category(state: Int): CallKind? = when (state) {
        INCOMING, WAITING -> CallKind.INCOMING
        ACTIVE, HELD, HELD_BY_RESPONSE_AND_HOLD -> CallKind.ACTIVE
        DIALING, ALERTING -> CallKind.DIALING
        TERMINATED, MISSED -> CallKind.ENDED
        else -> null
    }

    private val PRIORITY = listOf(CallKind.INCOMING, CallKind.ACTIVE, CallKind.DIALING, CallKind.ENDED)

    /** Birden çok arama varsa öncelik: gelen/bekleyen > aktif/beklemede > aranıyor > bitti; boş liste NONE. */
    fun fromVdDialInfos(list: List<DialInfoLite>): CallKind {
        val kinds = list.mapNotNull { category(it.state) }.toSet()
        return PRIORITY.firstOrNull { it in kinds } ?: CallKind.NONE
    }

    /** [fromVdDialInfos] sonucunu üreten ilk kaydın sırası (isim/numara bundan alınır); yoksa -1. */
    fun relevantIndex(list: List<DialInfoLite>): Int {
        val kind = fromVdDialInfos(list)
        if (kind == CallKind.NONE) return -1
        return list.indexOfFirst { category(it.state) == kind }
    }

    /** BluetoothHeadsetClientCall.CALL_STATE_* tek arama eşlemesi. */
    fun fromHfpClientCallState(state: Int): CallKind = when (state) {
        ACTIVE, HELD, HELD_BY_RESPONSE_AND_HOLD -> CallKind.ACTIVE
        DIALING, ALERTING -> CallKind.DIALING
        INCOMING, WAITING -> CallKind.INCOMING
        TERMINATED -> CallKind.ENDED
        else -> CallKind.NONE
    }

    /** CallLog.Calls.TYPE: 1 gelen, 2 giden, 3 cevapsız, 5 reddedilen (cevapsız sayılır); sesli mesaj vb. null. */
    fun recentType(callLogType: Int): RecentType? = when (callLogType) {
        1 -> RecentType.INCOMING
        2 -> RecentType.OUTGOING
        3, 5 -> RecentType.MISSED
        else -> null
    }

    /** VDValueBT.RecordType: 1 cevapsız, 2 gelen (kabul), 3 giden. */
    fun recentTypeFromVd(recordType: Int): RecentType? = when (recordType) {
        1 -> RecentType.MISSED
        2 -> RecentType.INCOMING
        3 -> RecentType.OUTGOING
        else -> null
    }

    /**
     * Arka uçtan gelen ham türü ekranda gösterilecek türe çevirir: görüşmeden doğrudan boşa (liste boşaldı) geçişte
     * kısa "bitti" gösterilir; boştayken gelen eski "bitti" kayıtları yok sayılır (bitti yeniden açılmaz).
     */
    fun effectiveKind(prev: CallKind, raw: CallKind): CallKind = when {
        raw == CallKind.NONE && (prev == CallKind.INCOMING || prev == CallKind.DIALING || prev == CallKind.ACTIVE) -> CallKind.ENDED
        raw == CallKind.NONE && prev == CallKind.ENDED -> CallKind.ENDED
        raw == CallKind.ENDED && prev == CallKind.NONE -> CallKind.NONE
        else -> raw
    }

    /**
     * Yeni arama durumu: ACTIVE'e ilk girişte sayaç başlar ([nowElapsed]), ACTIVE sürerken korunur. Konuşma sırasında
     * bekleyen arama gelirse (ACTIVE → INCOMING) sayaç başlangıcı saklanır; bekleyen reddedilip konuşmaya dönülünce
     * sayaç 0:00'dan başlamaz. Diğer durumlarda 0. Sessiz yalnızca görüşme sürerken korunur.
     *
     * İsim/numara yalnızca AYNI görüşmede eksik gelirse öncekinden alınır: önceki durum gelen/aranıyor/konuşma ve
     * görüşme ileri gidiyor (gelen/aranıyor → konuşma, herhangi biri → bitti, aynı durum) ve yeni numara yok ya da
     * öncekiyle aynı. "Bitti" sonrası gelen yeni arama ve konuşma sırasında bekleyen arama yeni görüşmedir: gizli
     * numaralı arayan önceki kişinin adıyla gösterilmez. [oemStartElapsed]: OEM başlangıcının elapsedRealtime karşılığı
     * (yoksa null). [photoKey]: bu durumda bilinen fotoğraf anahtarı; null ise aynı görüşmede öncekinden alınır.
     */
    fun nextCall(
        prev: CallState, kind: CallKind, name: String?, number: String?, nowElapsed: Long,
        oemStartElapsed: Long? = null, photoKey: String? = null,
    ): CallState {
        val forward = when (prev.kind) {
            CallKind.INCOMING -> kind == CallKind.INCOMING || kind == CallKind.ACTIVE || kind == CallKind.ENDED
            CallKind.DIALING -> kind == CallKind.DIALING || kind == CallKind.ACTIVE || kind == CallKind.ENDED
            CallKind.ACTIVE -> kind == CallKind.ACTIVE || kind == CallKind.ENDED
            else -> false
        }
        val sameNumber = number == null || prev.number == null ||
            FavoritesRanker.normalizeNumber(number) == FavoritesRanker.normalizeNumber(prev.number)
        val sameCall = forward && sameNumber
        val started = when {
            kind == CallKind.ACTIVE && (prev.kind == CallKind.ACTIVE || prev.kind == CallKind.INCOMING) && prev.startedAtMs != 0L -> prev.startedAtMs
            // İlk ACTIVE: OEM'in arama başlangıcı (VDBTDialInfo.time → [CallClock]) varsa o; biz konuşmanın ortasında
            // açılsak da OEM ekranıyla aynı süre görünür.
            kind == CallKind.ACTIVE -> oemStartElapsed ?: nowElapsed
            kind == CallKind.INCOMING && prev.kind == CallKind.ACTIVE -> prev.startedAtMs
            kind == CallKind.INCOMING && prev.kind == CallKind.INCOMING -> prev.startedAtMs
            else -> 0L
        }
        val inCall = kind == CallKind.INCOMING || kind == CallKind.DIALING || kind == CallKind.ACTIVE
        return CallState(
            kind = kind,
            name = name ?: if (sameCall) prev.name else null,
            number = number ?: if (sameCall) prev.number else null,
            startedAtMs = started,
            muted = inCall && prev.muted,
            // Ses yönü de yalnızca görüşme sürerken korunur (sonraki aramaya taşınmaz).
            audioOnPhone = inCall && prev.audioOnPhone,
            photoKey = photoKey ?: if (sameCall) prev.photoKey else null,
        )
    }

    private val VD_FORMATS = listOf("yyyyMMdd'T'HHmmss", "yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
    /** Yalnız rakamdan oluşan sıkışık tarihler (Desay/PBAP): 14 hane tarih-saat, 8 hane yalnız tarih. */
    private val COMPACT_FORMATS = mapOf(14 to "yyyyMMddHHmmss", 8 to "yyyyMMdd")

    /**
     * VDBTRecord tarih metni → duvar saati ms. Biçim araçta doğrulanmadı: 14/8 haneli sıkışık tarih ("20261105143000",
     * "20261105"), 9–13 haneli sayıysa ms (ya da sn), değilse PBAP "20261105T143000" ve yaygın tarih biçimleri
     * denenir. Çözülemezse null.
     */
    fun parseVdDateTime(raw: String?, zone: TimeZone = TimeZone.getDefault()): Long? {
        val s = raw?.trim()?.removeSuffix("Z").orEmpty()
        if (s.isEmpty()) return null
        val digits = s.all { it.isDigit() }
        if (digits) COMPACT_FORMATS[s.length]?.let { f -> return parseWith(f, s, zone) }
        if (digits && s.length in 9..13) {
            val v = s.toLongOrNull() ?: return null
            return if (s.length >= 12) v else v * 1000L
        }
        for (f in VD_FORMATS) {
            val fmt = SimpleDateFormat(f, Locale.US).apply { timeZone = zone; isLenient = false }
            try {
                return fmt.parse(s)?.time ?: continue
            } catch (e: ParseException) {
                // sıradaki biçim
            }
        }
        return null
    }

    private fun parseWith(format: String, s: String, zone: TimeZone): Long? = try {
        SimpleDateFormat(format, Locale.US).apply { timeZone = zone; isLenient = false }.parse(s)?.time
    } catch (e: ParseException) {
        null
    }
}

/** Favori sıralaması: sabitlenenler → yıldızlılar (Türkçe alfabe) → az ise son 30 günün en sık aranan numaraları. */
internal object FavoritesRanker {
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val WINDOW_MS = 30 * DAY_MS
    private const val FILL_BELOW = 4

    /** Rakamlar tutulur; ülke/alan öneki farkı ("+90", "00", "0") için son 10 rakam alınır. */
    fun normalizeNumber(n: String): String {
        val d = n.filter { it in '0'..'9' }
        return if (d.length > 10) d.takeLast(10) else d
    }

    fun rank(
        contacts: List<PhoneContact>,
        recents: List<PhoneRecent>,
        pinnedIds: List<String>,
        max: Int = 6,
        nowMs: Long,
    ): List<PhoneContact> {
        val out = ArrayList<PhoneContact>()
        val usedIds = HashSet<String>()
        val usedNumbers = HashSet<String>()
        val byNumber = HashMap<String, PhoneContact>()
        for (c in contacts) {
            val k = normalizeNumber(c.number)
            if (k.isNotEmpty() && k !in byNumber) byNumber[k] = c
        }

        fun add(c: PhoneContact): Boolean {
            if (out.size >= max) return false
            val k = normalizeNumber(c.number)
            if (c.id in usedIds || (k.isNotEmpty() && k in usedNumbers)) return false
            out.add(c)
            usedIds.add(c.id)
            if (k.isNotEmpty()) usedNumbers.add(k)
            return true
        }

        fun fromRecent(r: PhoneRecent): PhoneContact {
            val k = normalizeNumber(r.number)
            return byNumber[k] ?: PhoneContact(
                id = "n:$k",
                name = r.name?.takeIf { it.isNotBlank() } ?: r.number.trim(),
                number = r.number,
                starred = false,
            )
        }

        // 1) Sabitlenenler, sabitlenme sırasıyla (kaynakta artık yoksa atlanır).
        for (id in pinnedIds) {
            val c = contacts.firstOrNull { it.id == id }
                ?: if (id.startsWith("n:")) recents.firstOrNull { "n:" + normalizeNumber(it.number) == id }?.let { fromRecent(it) } else null
            if (c != null) add(c)
        }
        // 2) Yıldızlılar, Türkçe alfabe sırası.
        val collator = Collator.getInstance(Locale("tr", "TR"))
        contacts.filter { it.starred }.sortedWith { a, b -> collator.compare(a.name, b.name) }.forEach { add(it) }
        // 3) Az ise sıklık: giden/gelen 1, cevapsız 0,5; eşitlikte en yeni önce. 30 günden eskiler sayılmaz.
        if (out.size < FILL_BELOW) {
            class Score(val recent: PhoneRecent, var score: Double, var last: Long)
            val scores = LinkedHashMap<String, Score>()
            for (r in recents) {
                if (nowMs - r.timeMs > WINDOW_MS) continue
                val k = normalizeNumber(r.number)
                if (k.isEmpty()) continue
                val w = if (r.type == RecentType.MISSED) 0.5 else 1.0
                val s = scores[k]
                if (s == null) {
                    scores[k] = Score(r, w, r.timeMs)
                } else {
                    s.score += w
                    if (r.timeMs > s.last) {
                        s.last = r.timeMs
                    }
                }
            }
            scores.values.sortedWith(compareByDescending<Score> { it.score }.thenByDescending { it.last })
                .forEach { add(fromRecent(it.recent)) }
        }
        return out
    }
}

/** Son aramalar listesi: en yeni önce; 10 dk içinde art arda aynı numara+tür tek satır olur. */
internal object RecentsShaper {
    private const val COLLAPSE_MS = 10L * 60 * 1000

    fun shape(rows: List<PhoneRecent>, limit: Int = 5): List<PhoneRecent> {
        val out = ArrayList<PhoneRecent>()
        for (r in rows.sortedByDescending { it.timeMs }) {
            val last = out.lastOrNull()
            if (last != null && last.type == r.type &&
                FavoritesRanker.normalizeNumber(last.number) == FavoritesRanker.normalizeNumber(r.number) &&
                last.timeMs - r.timeMs <= COLLAPSE_MS
            ) {
                // Aynı kişiye art arda (ör. tekrar tekrar cevapsız): en yeni satır kalır, isim yoksa eskisinden alınır.
                if (last.name == null && r.name != null) out[out.size - 1] = last.copy(name = r.name)
                continue
            }
            if (out.size >= limit) break
            out.add(r)
        }
        return out
    }
}

/** Telefon eylemleri (yedek sırası için). */
internal enum class PhoneAction { DIAL, ANSWER, REJECT, HANG_UP }

/** Yeteneklere göre arka uç seçimi (sıra: demo → VDBus → Telecom → HFP istemci). */
internal object PhoneBackendChooser {
    /**
     * [hfpClientProxy]: HFP istemci vekili açık, gizli API muafiyeti alındı ve bağlı cihaz var (yansıma çağrıları
     * gerçekten yapılabilir). [telecomCallControl]: ANSWER_PHONE_CALLS verildi (Telecom ile cevap/kapat).
     */
    data class Caps(
        val demo: Boolean = false,
        val vdbusBt: Boolean = false,
        val telecomHfpAccount: Boolean = false,
        val hfpClientProxy: Boolean = false,
        val contactsPermission: Boolean = false,
        val vdbusData: Boolean = false,
        val telecomCallControl: Boolean = false,
    )

    /**
     * Eylemin deneneceği arka uçlar, sırayla (ilki başarısızsa bir sonraki bir kez denenir). Arama: VDBus → Telecom
     * (hfpc hesabı) → HFP. Cevap/ret/kapat: VDBus → HFP → Telecom (yalnızca ANSWER_PHONE_CALLS verilmişse; araç HFP
     * yığını her aramayı Telecom'a da koyar, HfpClientDeviceBlock).
     */
    fun fallbackOrder(c: Caps, action: PhoneAction): List<String> {
        if (c.demo) return listOf(PhoneBackendName.DEMO)
        val out = ArrayList<String>(3)
        if (c.vdbusBt) out.add(PhoneBackendName.VDBUS)
        if (action == PhoneAction.DIAL && c.telecomHfpAccount) out.add(PhoneBackendName.TELECOM)
        if (c.hfpClientProxy) out.add(PhoneBackendName.HFP_CLIENT)
        if (action != PhoneAction.DIAL && c.telecomCallControl) out.add(PhoneBackendName.TELECOM)
        return out
    }

    fun callBackend(c: Caps): String = fallbackOrder(c, PhoneAction.DIAL).firstOrNull() ?: PhoneBackendName.NONE

    fun answerBackend(c: Caps): String = fallbackOrder(c, PhoneAction.ANSWER).firstOrNull() ?: PhoneBackendName.NONE

    fun dataBackend(c: Caps): String = when {
        c.demo -> PhoneBackendName.DEMO
        c.contactsPermission -> PhoneBackendName.CONTACTS_PROVIDER
        c.vdbusData -> PhoneBackendName.VDBUS
        else -> PhoneBackendName.NONE
    }

    /**
     * Birincil veri yolu boş döndüğünde (0 kişi ve 0 geçmiş) bir kez denenecek ikinci yol; yoksa null. Araç Bluetooth
     * servisi her açılışta pbapsink hesaplarını ve tüm CallLog'u siler (PbapClientService.removeUncleanAccounts) ve
     * sağlayıcı yalnızca OEM servisi indirme başlatınca dolar; bu arada OEM önbelleği (VDBus) dolu olabilir.
     */
    fun dataFallback(c: Caps, primary: String): String? =
        if (!c.demo && primary == PhoneBackendName.CONTACTS_PROVIDER && c.vdbusData) PhoneBackendName.VDBUS else null

    fun assistantBackend(c: Caps): String = when {
        c.demo -> PhoneBackendName.DEMO
        c.hfpClientProxy -> PhoneBackendName.HFP_CLIENT
        else -> PhoneBackendName.NONE
    }

    /** Arama durumunu dinleyen kaynak: VDBus varsa o, yoksa HFP istemci yayını. */
    fun stateBackend(c: Caps): String = when {
        c.demo -> PhoneBackendName.DEMO
        c.vdbusBt -> PhoneBackendName.VDBUS
        c.hfpClientProxy -> PhoneBackendName.HFP_CLIENT
        else -> PhoneBackendName.NONE
    }

    fun permissionNeeded(c: Caps): Boolean = !c.demo && !c.contactsPermission && !c.vdbusData
}

/**
 * DTMF tuşu → VDBus DIAL (ctrl 4) number alanı. OEM servisi `Byte.parseByte(number)` ile ONDALIK okur ve o baytı
 * HFP sendDTMF'e verir; OEM arayüzü tuşun ASCII baytını gönderir.
 * Bu yüzden '5' → "53", '*' → "42", '#' → "35". Geçersizse null.
 */
internal object DtmfCode {
    fun valid(c: Char): Boolean = c in '0'..'9' || c == '*' || c == '#'

    fun vdNumber(c: Char): String? = if (valid(c)) c.code.toString() else null
}

/** DIAL_AUDIO_MODE durum değeri (SCO 0 kopuk → 2, 1 bağlanıyor → 3, 2 bağlı → 1). */
internal object AudioModeMap {
    const val CAR = 1
    const val PHONE = 2
    const val CONNECTING = 3

    /** true = ses telefonda, false = araçta, null = bağlanıyor/bilinmiyor (önceki değer korunur). */
    fun onPhone(status: Int): Boolean? = when (status) {
        CAR -> false
        PHONE -> true
        else -> null
    }
}

/**
 * Konuşma sayacının başlangıcı. VDBTDialInfo.time = OEM'in arama başlangıcı, **SystemClock.elapsedRealtime** (duvar
 * saati değil): OEM BT servisi (T1E24) değeri elapsedRealtime ile koyar; ilk değer
 * BluetoothHeadsetClientCall.getCreationElapsedMilli. elapsedRealtime cihaz
 * genelinde tek saat olduğundan doğrudan bizim [android.os.SystemClock.elapsedRealtime] ile karşılaştırılır; çevirme
 * yok. Değer makul değilse (0, gelecekte ya da 12 saatten eski) null: çağıran [PhoneStateMap.nextCall]'ın kendi
 * başlangıcını kullanır.
 */
internal object CallClock {
    private const val MAX_AGE_MS = 12 * 60 * 60 * 1000L
    private const val FUTURE_SLACK_MS = 5_000L

    fun startElapsed(oemStartElapsedMs: Long, nowElapsedMs: Long): Long? {
        if (oemStartElapsedMs <= 0L) return null
        val age = nowElapsedMs - oemStartElapsedMs
        if (age < -FUTURE_SLACK_MS || age > MAX_AGE_MS) return null
        return oemStartElapsedMs.coerceAtMost(nowElapsedMs)
    }
}

/**
 * HFP sesli tanıma (BVRA, Siri/Google) başlatma planı (saf). Araç HFP yığını:
 *  - AT+BVRA=1 yalnızca kendi durumu 0 iken gönderilir, değilse sessizce düşer ama binder yine true döner;
 *    AT+BVRA=0 yalnızca durum 1 iken gönderilir, durum 0'da zararsız bir hiçliktir.
 *  - Bizim isteğimizin OK'i durumu SESSİZCE 1/0 yapar, yayın yok; telefondan gelen +BVRA yalnızca değer
 *    değişirse AG_EVENT olarak yayınlanır. HF'nin başlattığı oturumda AG_EVENT 1 hiç gelmeyebilir: tek
 *    sağlam doğrulama SCO'nun bağlanması (AUDIO_STATE 1 bağlanıyor / 2 bağlı).
 *  - Durum yalnızca +BVRA:0, kendi durdurmamızın OK'i ya da durum makinesinin yeniden kurulması ile 0 olur.
 * Bu yüzden yığının 1'de kalmış olabileceğini bildiğimiz sürece ([stackMaybeActive]; süreç başında bilinmez = true)
 * başlatmadan önce durdurulur ve kısa beklenir. 6 Kasım araç: UNCONFIRMED sonrası doğrudan başlatma hep düşüyordu.
 */
internal object VoicePlan {
    enum class Step { STOP_SESSION, BUSY, RESET_THEN_START, START }

    /**
     * [sessionOpen]: açık oturum biliniyor; [scoUp]: SCO bağlı; [stackMaybeActive]: yığın 1'de olabilir;
     * [callBusy]: sürmekte olan arama var.
     */
    fun next(sessionOpen: Boolean, scoUp: Boolean, stackMaybeActive: Boolean, callBusy: Boolean): Step = when {
        sessionOpen && scoUp -> Step.STOP_SESSION
        callBusy -> Step.BUSY
        stackMaybeActive -> Step.RESET_THEN_START
        else -> Step.START
    }

    /** AUDIO_STATE_CHANGED: 1 (bağlanıyor) ya da 2 (bağlı) telefonun sesli tanımayı açtığını doğrular. */
    fun confirmsStart(audioState: Int): Boolean = audioState == 1 || audioState == 2
}

/**
 * Arama durumu yoklama yedeği: canlı DIAL_INFO_LIST yayını kaçarsa (6 Kasım araç: arama bitti, şerit ACTIVE kaldı)
 * YALNIZCA arama sürerken [INTERVAL_MS]'de bir getOnce. Arama yokken hiç yoklama yok.
 */
internal object CallPoll {
    const val INTERVAL_MS = 2_000L

    fun shouldPoll(kind: CallKind, ownersOrLingering: Boolean): Boolean =
        ownersOrLingering && (kind == CallKind.INCOMING || kind == CallKind.DIALING || kind == CallKind.ACTIVE)
}

/**
 * Rehber/geçmiş yenileme kararı (saf). Bellek içi depo süreç boyunca tutulur; boş bir okuma dolu depoyu SİLMEZ
 * (yalnızca farklı bir telefon bağlanınca depo sıfırlanır).
 *
 * OEM gerçeği (bizim araç T1E24): _CACHE haritası yalnızca o PBAP oturumunda bir
 * indirme bitince dolar ve PBAP kopunca silinir. _DB yolu indirme
 * sonrası hiç yanıt vermez, kullanılmaz. Boş harita tek {total 0} yayını verir
 * (6 Kasım araç: BT yığını yeniden başladı, PBAP koptu, indirme bitmeden istendi).
 * Çözüm: önbellek boşsa bağlantı başına EN ÇOK BİR KEZ PHONE_BOOK_SYNC (262346) ctrl 1 tür 50 (rehber+geçmiş
 * indirmesi), indirme parçaları PHONE_BOOK_CONTACTS_LIST/RECORD_LIST (262347/262348) yayınlarından
 * toplanır; PHONE_BOOK_SYNC durum yayını FINISHED (1) gelince önbellek yeniden istenir.
 */
internal object PhonebookRefresh {
    enum class Step { NONE, CACHE, DOWNLOAD }

    /** VDValueBT.SyncStatus: START 0, FINISHED 1, IDLE 20, WAITING 21. */
    fun downloading(syncStatus: Int?): Boolean = syncStatus == 0 || syncStatus == 21

    /**
     * Rehber (tür 7) ve geçmiş (tür 6) görevlerinin durumları tek karara indirgenir: biri iniyorsa "iniyor" (yeni
     * indirme istenmez; bitince FINISHED yayını önbelleği yeniden okutur). Değilse rehberinki, yoksa geçmişinki.
     */
    fun combine(contactStatus: Int?, callLogStatus: Int?): Int? = when {
        downloading(contactStatus) -> contactStatus
        downloading(callLogStatus) -> callLogStatus
        else -> contactStatus ?: callLogStatus
    }

    /**
     * [storeHasData]: bellekteki depo dolu. [cacheTotal]: son önbellek isteğinin toplamı (null = henüz sorulmadı).
     * [syncStatus]: PHONE_BOOK_SYNC getOnce durumu (null = bilinmiyor). [downloadRequested]: bu bağlantıda indirme
     * zaten istendi.
     */
    fun next(storeHasData: Boolean, cacheTotal: Int?, syncStatus: Int?, downloadRequested: Boolean): Step = when {
        cacheTotal == null -> Step.CACHE
        cacheTotal > 0 -> Step.NONE
        storeHasData -> Step.NONE
        downloadRequested || downloading(syncStatus) -> Step.NONE
        else -> Step.DOWNLOAD
    }
}

/**
 * Arka uçtan gelen ham arama durumu (herhangi bir iş parçacığı → io). [uuid]: OEM arama kimliği; [startElapsedMs]: OEM
 * arama başlangıcı (VDBTDialInfo.time, elapsedRealtime; yoksa 0); [portrait]: arayanın fotoğrafı (VDBTDialInfo.portrait;
 * yalnızca bellekte, [PhonePhotos]'a çözülür). toString isim/numara/fotoğraf içermez.
 */
internal class RawCall(
    val kind: CallKind,
    val name: String? = null,
    val number: String? = null,
    val uuid: String? = null,
    val startElapsedMs: Long = 0L,
    val portrait: ByteArray? = null,
) {
    override fun toString(): String = "RawCall(kind=$kind, start=${startElapsedMs != 0L}, photo=${portrait?.isNotEmpty() == true})"

    companion object {
        val NONE = RawCall(CallKind.NONE)
    }
}

/**
 * Sağlam kapatma/ret kararı (saf). 6 Kasım araç: arama bittikten sonra şerit ACTIVE kaldı, Kapat 'callList.size=0' aldı.
 * OEM'de canlı arama yoksa yalnızca bizim durum temizlenir (hata yok); varsa istek gönderilir; son yol da başarısız
 * olduğunda hata yalnızca arama hâlâ canlıysa gösterilir.
 */
internal object HangUpPlan {
    enum class Decision { CLEAR_LOCAL, SEND, FAIL }

    /** İstekten önce. [liveOnOem]: taze okuma (null = bilinmiyor). [ourInCall]: ekranda arama var. */
    fun decide(liveOnOem: Boolean?, ourInCall: Boolean): Decision = when {
        liveOnOem == false -> Decision.CLEAR_LOCAL
        liveOnOem == true -> Decision.SEND
        // Bilinmiyor: ekranda arama varsa yine de denenir; yoksa kapatılacak bir şey yok.
        ourInCall -> Decision.SEND
        else -> Decision.FAIL
    }

    /** Onay süresi doldu (arama durumu değişmedi): canlı değilse temizle, değilse sıradaki yol (SEND). */
    fun onTimeout(liveOnOem: Boolean?): Decision = if (liveOnOem == false) Decision.CLEAR_LOCAL else Decision.SEND

    /** Bütün yollar denendi: canlı değilse temizle, değilse (ya da bilinmiyorsa) hata. */
    fun onExhausted(liveOnOem: Boolean?): Decision = if (liveOnOem == false) Decision.CLEAR_LOCAL else Decision.FAIL
}

/**
 * Bellekteki rehber/geçmiş deposu (saf; süreç ömrü, cihaz başına). Diske ya da günlüğe asla yazılmaz.
 *
 * Kurallar: boş ya da null bir okuma dolu listeyi SİLMEZ (OEM önbelleği PBAP kopunca boşalır, yeniden kurulumdan
 * sonra total=0 döner); yalnızca dolu ve farklı bir liste yerine geçer. Farklı bir cihaz bağlanınca ([onDevice]) depo
 * sıfırlanır. Kişiler Türkçe alfabe sırasında (kimliğe göre tekil), geçmiş en yeni önce ve en çok [MAX_RECENTS].
 * Geçmiş satırlarına numara eşleşmesiyle isim, [PhoneRecent.contactId] ve [PhoneRecent.photoKey] eklenir. Her
 * değişiklikte [version] artar.
 */
internal class DirectoryStore {
    var deviceKey: Int? = null
        private set
    var contacts: List<PhoneContact> = emptyList()
        private set
    var recents: List<PhoneRecent> = emptyList()
        private set
    var version: Int = 0
        private set
    /** Eşleştirilmemiş geçmiş: kişiler sonradan gelince yeniden eşleştirmek için. */
    private var rawRecents: List<PhoneRecent> = emptyList()

    /**
     * Disk önbelleğine yazılacak geçmiş: telefonun gönderdiği hâli (rehberden eşlenmiş ad/kimlik/fotoğraf yok).
     * [recents] yazılsaydı eşleşen kişi adı geri yüklemede telefondan gelmiş gibi kalır, kişi silinse/yeniden
     * adlandırılsa da eski ad diskte yaşardı.
     */
    internal val rawRecentsForSave: List<PhoneRecent> get() = rawRecents

    val hasData: Boolean get() = contacts.isNotEmpty() || recents.isNotEmpty()

    /**
     * Bağlı cihaz anahtarı (adres özeti; günlüğe yazılmaz). Önceki bilinen cihazdan farklıysa depo sıfırlanır ve true
     * döner (çağıran fotoğrafları da siler). İlk bilinen cihazda (öncesinde okunan veri o cihazındır) false. null = bilinmiyor.
     */
    fun onDevice(key: Int?): Boolean {
        if (key == null || key == deviceKey) return false
        val first = deviceKey == null
        deviceKey = key
        if (first) return false
        val wasData = hasData
        contacts = emptyList()
        recents = emptyList()
        rawRecents = emptyList()
        if (wasData) version++
        return true
    }

    /** Yeni okuma; değiştiyse true. null/boş liste mevcut listeyi korur. */
    fun merge(newContacts: List<PhoneContact>?, newRecents: List<PhoneRecent>?): Boolean {
        var changed = false
        if (!newContacts.isNullOrEmpty()) {
            val sorted = sortContacts(newContacts)
            if (sorted != contacts) {
                contacts = sorted
                changed = true
            }
        }
        if (!newRecents.isNullOrEmpty()) {
            val sorted = newRecents.sortedByDescending { it.timeMs }.take(MAX_RECENTS)
            if (sorted != rawRecents) {
                rawRecents = sorted
                changed = true
            }
        }
        if (changed) {
            recents = annotate(rawRecents, contacts)
            version++
        }
        return changed
    }

    /** Normalize numara → kişi (arayan eşleşmesi için). */
    fun byNumber(): Map<String, PhoneContact> = numberMap(contacts)

    companion object {
        const val MAX_RECENTS = 100

        fun sortContacts(list: List<PhoneContact>): List<PhoneContact> {
            val collator = Collator.getInstance(Locale("tr", "TR"))
            val seen = HashSet<String>()
            return list.filter { seen.add(it.id) }.sortedWith { a, b -> collator.compare(a.name, b.name) }
        }

        fun numberMap(contacts: List<PhoneContact>): Map<String, PhoneContact> {
            val m = HashMap<String, PhoneContact>()
            for (c in contacts) {
                val k = FavoritesRanker.normalizeNumber(c.number)
                if (k.isNotEmpty()) m.putIfAbsent(k, c)
            }
            return m
        }

        fun annotate(recents: List<PhoneRecent>, contacts: List<PhoneContact>): List<PhoneRecent> {
            if (contacts.isEmpty()) return recents
            val m = numberMap(contacts)
            return recents.map { r ->
                val c = m[FavoritesRanker.normalizeNumber(r.number)] ?: return@map r
                r.copy(name = r.name ?: c.name, contactId = c.id, photoKey = c.photoKey)
            }
        }
    }
}
