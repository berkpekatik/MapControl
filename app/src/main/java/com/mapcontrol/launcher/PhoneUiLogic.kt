package com.mapcontrol.launcher

import androidx.annotation.StringRes
import com.mapcontrol.R
import java.util.Calendar
import java.util.TimeZone

/**
 * Telefon arayüzünün saf kararları (JVM testli, [PhoneUiLogicTest]): panelin hangi hâli göstereceği, alt çubuktaki telefon
 * düğmesinin ne yapacağı, arama şeridinin kipi, süre ve göreli zaman biçimi, favori sütun sayısı. İsim ve numara yalnızca
 * ekrana gider; burada da günlüğe yazılmaz.
 */
internal object PhoneUiLogic {

    /** Telefon panelinin gövdesi: bağlı telefon yok · izin gerekli · yükleniyor · boş · içerik (favoriler + son aramalar). */
    enum class PanelState { NO_PHONE, PERMISSION, LOADING, EMPTY, CONTENT }

    /** Alt çubuk telefon düğmesi: hiçbir şey (panel zaten ekranda) · telefon sayfası · OEM telefon uygulaması. */
    enum class PhoneButtonAction { NONE, SHEET, OEM_APP }

    /** Arama şeridi kipi. */
    enum class BannerMode { HIDDEN, INCOMING, DIALING, ACTIVE, ENDED }

    /** Göreli zaman (metin kaynağı UI'da seçilir): şimdi · n dk · n sa · dün · gün + ay (0 tabanlı). */
    sealed class RelTime {
        object Now : RelTime()
        data class Minutes(val n: Int) : RelTime()
        data class Hours(val n: Int) : RelTime()
        object Yesterday : RelTime()
        data class Date(val day: Int, val monthIndex: Int) : RelTime()
    }

    /** Son arama satırı: gösterilecek ad (yoksa numara), aranacak numara, cevapsız mı. */
    data class RecentRow(val type: RecentType, val label: String, val number: String, val timeMs: Long) {
        val missed: Boolean get() = type == RecentType.MISSED
        override fun toString(): String = "RecentRow(type=$type)"
    }

    /** Bir favori hücresinin en küçük genişliği (dp): 72dp kare avatar + yanlarda pay; dokunma alanı ≥ 76dp kalır. */
    const val FAVORITE_CELL_MIN_DP = 96
    const val FAVORITE_MAX = 6
    /** Dar panelde bile en az bu kadar sütun (440dp panelde 4 sütun sığar; daha darsa hücre küçülmez, sütun azalır). */
    const val FAVORITE_MIN = 3

    fun panelState(s: PhoneSnapshot): PanelState = when {
        !s.connected -> PanelState.NO_PHONE
        s.permissionNeeded -> PanelState.PERMISSION
        !s.loaded -> PanelState.LOADING
        s.favorites.isEmpty() && s.recents.isEmpty() -> PanelState.EMPTY
        else -> PanelState.CONTENT
    }

    /**
     * Alt çubuk telefon dokunuşu: telefon paneli ekrandaysa bir şey yapılmaz (aynı içerik zaten görünür); telefon bağlı
     * değilse sayfa ("Telefon bağlı değil" + Bluetooth düğmesi); bağlı ama arama yolu yoksa OEM telefon uygulaması; yoksa sayfa.
     */
    fun phoneButtonAction(panelOnScreen: Boolean, connected: Boolean, canDial: Boolean): PhoneButtonAction = when {
        panelOnScreen -> PhoneButtonAction.NONE
        !connected -> PhoneButtonAction.SHEET
        !canDial -> PhoneButtonAction.OEM_APP
        else -> PhoneButtonAction.SHEET
    }

    /**
     * OEM küçük arama hapı (OEM telefon uygulaması, 240×54, x 421 y 16, her uygulamanın üstünde)
     * süreyi/durumu kendisi gösteriyor mu: arama DesaySV telefon yığınından geliyorsa ([PhoneBackendName.VDBUS]) ve
     * aranıyor ya da konuşma sürüyorsa. O zaman bizim hap süre/durum yazmaz ve saniye sayacı durur (araçta her şey iki
     * kez görünüyordu); ad, sessiz işareti ve Kapat kalır. Gelen aramada ve "Arama bitti"de OEM hapı yok. Telecom/HFP
     * istemci/demo arka uçlarında (OEM yığını yok ya da emülatör) süre bizde kalır.
     *
     * Not: Android Auto yansıtmasında OEM hapının durumu bilinmiyor (launcher'da yansıtma sinyali yok); aramayı VDBus
     * bildirdiği sürece hap var sayılır.
     */
    fun oemPillShowsDuration(kind: CallKind, backend: String): Boolean =
        backend == PhoneBackendName.VDBUS && (kind == CallKind.DIALING || kind == CallKind.ACTIVE)

    /** Kompakt arama hapının yeri (piksel, şerit çerçevesine göre): sol kenar ve genişlik. */
    data class PillSlot(val leftPx: Int, val widthPx: Int)

    /**
     * Kompakt arama hapı başlıktaki saatin üstüne binmez: saatin sağ kenarı + [gapPx]'ten başlar, sağ grubun (tarih,
     * hava) solundan [gapPx] önce biter, en çok [maxWidthPx]. Bu boşluk [minWidthPx]'ten darsa sağ grubun üstüne
     * uzanır (yalnız bakılan bilgi; saat yine açık), [rightLimitPx]'e dek. Saat ölçülmemişse null: çağıran ortalar.
     */
    fun compactPillSlot(
        clockRightPx: Int?,
        rightGroupLeftPx: Int?,
        rightLimitPx: Int,
        gapPx: Int,
        maxWidthPx: Int,
        minWidthPx: Int,
    ): PillSlot? {
        if (clockRightPx == null || clockRightPx <= 0) return null
        val left = clockRightPx + gapPx
        val beforeGroup = (rightGroupLeftPx ?: rightLimitPx).coerceAtMost(rightLimitPx) - gapPx - left
        if (beforeGroup >= minWidthPx) return PillSlot(left, minOf(maxWidthPx, beforeGroup))
        val toEdge = rightLimitPx - left
        if (toEdge >= minWidthPx) return PillSlot(left, minOf(maxWidthPx, toEdge))
        return null
    }

    fun bannerMode(call: CallState): BannerMode = when (call.kind) {
        CallKind.NONE -> BannerMode.HIDDEN
        CallKind.INCOMING -> BannerMode.INCOMING
        CallKind.DIALING -> BannerMode.DIALING
        CallKind.ACTIVE -> BannerMode.ACTIVE
        CallKind.ENDED -> BannerMode.ENDED
    }

    /** Konuşma süresi: "0:05", "1:23", "12:00", "1:02:03" (eksi süre 0 sayılır). */
    fun formatDuration(elapsedMs: Long): String {
        val total = (elapsedMs.coerceAtLeast(0L) / 1000L)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.ROOT, "%d:%02d", m, s)
    }

    /**
     * Arama zamanının göreli hâli: 1 dk'dan yeni (ya da saat ileri kaymış) "şimdi"; 1 saatten yeni dakika; aynı takvim günü
     * saat; bir önceki gün "dün"; daha eskisi gün + ay.
     */
    fun relativeTime(nowMs: Long, timeMs: Long, zone: TimeZone = TimeZone.getDefault()): RelTime {
        val diff = nowMs - timeMs
        if (diff < 60_000L) return RelTime.Now
        if (diff < 3_600_000L) return RelTime.Minutes((diff / 60_000L).toInt())
        val now = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
        val then = Calendar.getInstance(zone).apply { timeInMillis = timeMs }
        if (sameDay(now, then)) return RelTime.Hours((diff / 3_600_000L).toInt())
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (sameDay(yesterday, then)) return RelTime.Yesterday
        return RelTime.Date(then.get(Calendar.DAY_OF_MONTH), then.get(Calendar.MONTH))
    }

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    /** Favori satırında kaç hücre: her hücre ≥ [FAVORITE_CELL_MIN_DP]; en az [FAVORITE_MIN], en çok [FAVORITE_MAX]. */
    fun favoriteColumns(widthDp: Int): Int = (widthDp / FAVORITE_CELL_MIN_DP).coerceIn(FAVORITE_MIN, FAVORITE_MAX)

    /** Son aramalar satırları (arka uç sıralı ve sıkıştırılmış verir): en çok [max]; ad yoksa ya da boşsa numara gösterilir. */
    fun recentRows(recents: List<PhoneRecent>, max: Int = 5): List<RecentRow> =
        recents.take(max).map { r -> RecentRow(r.type, r.name?.trim()?.takeIf { it.isNotEmpty() } ?: r.number, r.number, r.timeMs) }

    /**
     * Uzun basışla sabitlenebilir mi: yalnızca rehber kimlikleri. Yalnız geçmişten gelen kişilerin kimliği numarayı içerir
     * ("n:…"); sabitleme ayara yazıldığı için bunlar sabitlenmez (ayara numara yazılmaz).
     */
    fun canPin(id: String): Boolean = id.isNotBlank() && !id.startsWith("n:")

    /** Sabitlemeyi aç/kapat: listede varsa çıkar, yoksa sona ekle. */
    fun togglePin(pinned: List<String>, id: String): List<String> = if (id in pinned) pinned - id else pinned + id

    // --- Telefon paneli (4×4, 378dp): satır ölçüleri tek yerde; [PhoneUiLogicTest] toplamın sığdığını denetler. ---

    /** Panelin doğal yüksekliği (SahneScreen.NATURAL_PANEL_H_DP). */
    const val PANEL_H_DP = 378
    const val PANEL_PAD_TOP_DP = 18
    const val PANEL_PAD_BOTTOM_DP = 16
    const val PANEL_HEADER_DP = 28
    const val PANEL_LAST_CALL_DP = 96
    const val PANEL_FAVORITES_DP = 112
    const val PANEL_ASSIST_DP = 76
    const val PANEL_GAP_DP = 10
    /** Favori hücresi sayısı (yalnız isimli kişiler); dördüncü hücre "Tümü". */
    const val PANEL_FAVORITES = 3

    /** Paneldeki satırların toplamı (dp): üst/alt pay + başlık + son arama + favoriler + asistan + 3 boşluk. */
    fun panelContentDp(): Int = PANEL_PAD_TOP_DP + PANEL_HEADER_DP + PANEL_GAP_DP + PANEL_LAST_CALL_DP + PANEL_GAP_DP +
        PANEL_FAVORITES_DP + PANEL_GAP_DP + PANEL_ASSIST_DP + PANEL_PAD_BOTTOM_DP

    // --- Asistansız düzen ("Sesli asistan düğmesi: Kapalı"): son arama kartı büyür, favoriler 2×3 ızgara. ---

    /** Asistansız düzende ızgara sütunu. */
    const val COMPACT_COLUMNS = 3
    /** Asistansız düzende en çok favori (altıncı hücre hep "Tümü"). */
    const val COMPACT_FAVORITES = 5
    /** Favori hücresinde adın satırı ve avatarla arası (dp). */
    const val COMPACT_NAME_ROW_DP = 24
    const val COMPACT_NAME_GAP_DP = 4
    /** Son arama kartının iç payı (dp). */
    const val COMPACT_CARD_PAD_DP = 10
    /** Büyük kartın en küçük yüksekliği (dp): "Son arama" + ad + zaman satırı payla birlikte sığar. */
    const val COMPACT_LAST_MIN_DP = 100
    /** Dokunma hedefi alt sınırı (dp): sürüşte de dokunulur. */
    const val TOUCH_MIN_DP = 64

    /** Favori ızgarasının bir hücresi: kişi · "Tümü". */
    sealed class FavCell {
        data class Contact(val contact: PhoneContact) : FavCell()
        object All : FavCell()
    }

    /** Tek satırda en çok favori (kişi 3 ise "Tümü" ile dört hücre olur; boş ikinci satır açılmaz). */
    const val COMPACT_ONE_ROW_FAVORITES = 3
    /** Bir ızgara satırının en küçük yüksekliği (dp): en küçük avatar (48) + ad satırı + arası. */
    const val COMPACT_ROW_MIN_DP = 48 + COMPACT_NAME_ROW_DP + COMPACT_NAME_GAP_DP

    /**
     * Asistansız düzenin favori hücreleri satır satır; boş hücre yok. En çok [COMPACT_FAVORITES] isimli kişi, "Tümü" hep
     * son kişinin hemen ardından. 3 ya da daha az kişi tek satır (3 kişide KKKT); 4-5 kişi iki satır (KKK/KT, KKK/KKT;
     * kısa satır ortalanır, [compactSlots]). İki satır [bodyDp] gövdeye sığmıyorsa ([COMPACT_ROW_MIN_DP]) tek satıra
     * iner ve ilk [COMPACT_ONE_ROW_FAVORITES] kişi gösterilir.
     */
    fun compactGrid(favorites: List<PhoneContact>, bodyDp: Int, hasLast: Boolean, exclude: PhoneRecent? = null): List<List<FavCell>> {
        val named = favoriteCells(favorites, COMPACT_FAVORITES, exclude)
        val twoRows = named.size > COMPACT_ONE_ROW_FAVORITES &&
            compactSizes(bodyDp, 2, hasLast).rowDp >= COMPACT_ROW_MIN_DP
        val shown = if (twoRows) named else named.take(COMPACT_ONE_ROW_FAVORITES)
        val cells = shown.map<PhoneContact, FavCell> { FavCell.Contact(it) } + FavCell.All
        return if (twoRows) cells.chunked(COMPACT_COLUMNS) else listOf(cells)
    }

    /** Izgaranın sütun sayısı: hücre genişliği tüm satırlarda aynı, kısa satır ortalanır (en az [COMPACT_COLUMNS]). */
    fun compactSlots(grid: List<List<FavCell>>): Int = maxOf(COMPACT_COLUMNS, grid.maxOfOrNull { it.size } ?: 0)

    /** Asistansız düzenin ölçüleri (dp): son arama kartı ve avatarı, favori satırı ve avatarı. */
    data class CompactSizes(val lastDp: Int, val lastAvatarDp: Int, val rowDp: Int, val favAvatarDp: Int)

    /**
     * Asistansız gövdenin [bodyDp] yüksekliğini boşluksuz paylaştırır: son arama kartı (varsa) iki satırlı ızgarada
     * ~%36, tek satırda yarısı; kalan satırlara eşit. Avatarlar satıra sığacak kadar büyür (sınırlı).
     */
    fun compactSizes(bodyDp: Int, rows: Int, hasLast: Boolean, cellWidthDp: Int = Int.MAX_VALUE): CompactSizes {
        val r = rows.coerceAtLeast(1)
        val gaps = PANEL_GAP_DP * (if (hasLast) r else r - 1)
        val avail = (bodyDp - gaps).coerceAtLeast(0)
        val last = if (!hasLast) 0 else (avail * (if (r >= 2) 36 else 50) / 100).coerceIn(COMPACT_LAST_MIN_DP, 160)
        val row = (avail - last) / r
        val lastAvatar = (last - 2 * COMPACT_CARD_PAD_DP).coerceIn(56, 88)
        // Hücre genişliği de sınırlar (dört hücreli satırda avatar yanındakine değmesin).
        val favAvatar = (row - COMPACT_NAME_ROW_DP - COMPACT_NAME_GAP_DP).coerceAtMost(cellWidthDp - 16).coerceIn(48, 96)
        return CompactSizes(last, if (hasLast) lastAvatar else 0, row, favAvatar)
    }

    /** Gösterilebilir bir isim: boş değil ve en az bir harf içerir (yalın numara isim sayılmaz). */
    fun hasName(name: String?): Boolean = name?.trim()?.any { it.isLetter() } == true

    /** "Son arama" kartı: ismi olan ilk (en yeni) kayıt; isimsizler atlanır. Yoksa null (kart gizlenir). */
    fun lastNamedRecent(recents: List<PhoneRecent>): PhoneRecent? = recents.firstOrNull { hasName(it.name) }

    /**
     * Paneldeki favori hücreleri: yalnızca isimli kişiler, en çok [max]; çıplak numara asla. [exclude] ("Son arama"
     * kartındaki kayıt) favorilerde tekrar edilmez; boşalan yeri sıradaki favori doldurur ([isSamePerson]).
     */
    fun favoriteCells(favorites: List<PhoneContact>, max: Int = PANEL_FAVORITES, exclude: PhoneRecent? = null): List<PhoneContact> =
        favorites.filter { hasName(it.name) && (exclude == null || !isSamePerson(it, exclude)) }.take(max)

    /**
     * Favori kişi ile son arama kaydı aynı kişi mi: rehber kimliği eşitse; değilse numaraların son 9 hanesi (en az 6 hane)
     * eşitse; numara yoksa/eşleşmezse iki taraf da isimliyse ve isimler (büyük/küçük harf ve boşluk farkı yok) eşitse.
     */
    fun isSamePerson(c: PhoneContact, r: PhoneRecent): Boolean {
        if (r.contactId != null && r.contactId == c.id) return true
        val a = digitsOf(c.number).takeLast(9)
        val b = digitsOf(r.number).takeLast(9)
        if (a.length >= 6 && a == b) return true
        val rn = r.name
        return hasName(c.name) && hasName(rn) && c.name.trim().equals(rn!!.trim(), ignoreCase = true)
    }

    /** "Son arama" adı en çok bu kadar satıra kaydırılır; taşarsa ikinci satırın sonunda "…". */
    const val LAST_NAME_MAX_LINES = 2

    /**
     * "Son arama" kartında küçük "Son arama" başlığı görünsün mü: ad tek satırdaysa evet; iki satıra kaydıysa başlık
     * kalkar ki ad + zaman satırı kartın iç yüksekliğine sığsın ([lastCardTextDp], [PhoneUiLogicTest] denetler).
     */
    fun lastCardCaptionVisible(nameLines: Int): Boolean = nameLines <= 1

    /**
     * Kart metin sütununun yaklaşık yüksekliği (dp; üst sınır tahmini): satır ≈ 1,17 × yazı boyu, yazı payı açıksa
     * (includeFontPadding, başlık ve zaman yazısı) +0,16 ×. Ad yazısında yazı payı kapalı. Zaman satırı en az 20dp
     * (simge). Yazı boyları gündüz +1sp büyüyebilir ([dayBumpSp]).
     */
    fun lastCardTextDp(nameLines: Int, captionVisible: Boolean, nameSp: Float, captionSp: Float, dayBumpSp: Float = 1f): Int {
        fun lines(n: Int, sp: Float, pad: Boolean) = if (n <= 0) 0f else (sp + dayBumpSp) * (1.17f * n + if (pad) 0.16f else 0f)
        val caption = if (captionVisible) lines(1, captionSp, true) + LAST_CAPTION_GAP_DP else 0f
        val time = maxOf(20f, lines(1, captionSp, true)) + LAST_TIME_GAP_DP
        return kotlin.math.ceil(caption + lines(nameLines, nameSp, false) + time).toInt()
    }

    /** Asistanlı "Son arama" kartının iç payı (dp, her yan) ve yazı boyları; [PhonePanel] ile test aynı değeri kullanır. */
    const val FACE_CARD_PAD_DP = 10
    const val LAST_CAPTION_SP = 15f
    const val LAST_NAME_SP = 21f
    /** Asistansız büyük kartın yazı boyları. */
    const val C_LAST_CAPTION_SP = 16f
    const val C_LAST_NAME_SP = 22f

    const val LAST_CAPTION_GAP_DP = 1
    const val LAST_TIME_GAP_DP = 2

    /**
     * "Son arama" kartının fotoğraf anahtarı: kaydın kendi anahtarı, yoksa eşleşen kişinin (kimlikle, sonra numaranın
     * son 9 hanesiyle). Görüntü yine [PhonePhotos]'tan okunur; önbellekte yoksa UI baş harfe düşer.
     */
    fun recentPhotoKey(r: PhoneRecent, contacts: List<PhoneContact>): String? {
        r.photoKey?.let { return it }
        r.contactId?.let { id -> contacts.firstOrNull { it.id == id }?.photoKey }?.let { return it }
        val tail = digitsOf(r.number).takeLast(9)
        if (tail.length < 6) return null
        return contacts.firstOrNull { it.photoKey != null && digitsOf(it.number).endsWith(tail) }?.photoKey
    }

    private fun digitsOf(s: String): String = s.filter { it.isDigit() }

    // --- Arama ekranı ve şeridi ---

    /** Büyük satır isim (yoksa numara); numara satırı yalnız isim varken. */
    data class CallLabel(val title: String, val number: String?) {
        override fun toString(): String = "CallLabel(number=${number != null})"
    }

    fun callLabel(call: CallState): CallLabel {
        val name = call.name?.trim()?.takeIf { it.isNotEmpty() }
        val number = call.number?.trim()?.takeIf { it.isNotEmpty() }
        return CallLabel(name ?: number.orEmpty(), if (name != null) number else null)
    }

    /** Arama ekranının alt sıra denetimleri. */
    enum class CallControl { MUTE, AUDIO, KEYPAD, MINIMIZE, HANGUP, REJECT, ANSWER }

    /**
     * Arama ekranında görünen denetimler (soldan sağa). Gelen arama: Reddet + Cevapla. Aranıyor/konuşma: yolu olan
     * Sessiz, Telefona aktar, Tuş takımı; her zaman Küçült ve Kapat. Bitti ya da arama yok: yalnız Küçült.
     */
    fun callControls(kind: CallKind, canMute: Boolean, canAudio: Boolean, canDtmf: Boolean): List<CallControl> = when (kind) {
        CallKind.INCOMING -> listOf(CallControl.REJECT, CallControl.ANSWER)
        CallKind.DIALING, CallKind.ACTIVE -> ArrayList<CallControl>().apply {
            if (canMute) add(CallControl.MUTE)
            if (canAudio) add(CallControl.AUDIO)
            if (canDtmf) add(CallControl.KEYPAD)
            add(CallControl.MINIMIZE)
            add(CallControl.HANGUP)
        }
        CallKind.ENDED, CallKind.NONE -> listOf(CallControl.MINIMIZE)
    }

    /** Tuş takımı yalnızca konuşma sürerken çalışır (aranıyorken soluk). */
    fun keypadEnabled(kind: CallKind): Boolean = kind == CallKind.ACTIVE

    const val KEYPAD_MAX = 24

    /** Tuş takımı satırı (yalnız bellekte ve ekranda): en çok [max] karakter, taşınca baştan kırpılır; geçersiz tuş eklenmez. */
    fun keypadAppend(current: String, c: Char, max: Int = KEYPAD_MAX): String {
        if (c !in '0'..'9' && c != '*' && c != '#') return current
        val next = current + c
        return if (next.length > max) next.takeLast(max) else next
    }

    /** Arama ekranı durum NONE olunca kendiliğinden kapanır (ENDED sırasında "Arama bitti" görünür). */
    fun callScreenShouldClose(kind: CallKind): Boolean = kind == CallKind.NONE

    /**
     * Launcher öne getirilsin mi (karar §15): yalnızca varsayılan ana ekran bizsek, gelen arama / aranıyor / konuşmaya
     * GEÇİŞTE ve bu aramada bu geçiş için henüz yapılmadıysa ([alreadyForThisCall] false). Kullanıcı sonra OEM
     * uygulamasında kalırsa tekrar yapılmaz.
     */
    fun shouldBringToFront(prevKind: CallKind, nextKind: CallKind, isDefaultHome: Boolean, alreadyForThisCall: Boolean): Boolean {
        if (!isDefaultHome || alreadyForThisCall || prevKind == nextKind) return false
        return nextKind == CallKind.INCOMING || nextKind == CallKind.DIALING || nextKind == CallKind.ACTIVE
    }

    /** Hata metni. */
    @StringRes
    fun errorRes(error: PhoneError): Int = when (error) {
        PhoneError.NO_PHONE -> R.string.launcher_call_no_phone
        PhoneError.NOT_SUPPORTED -> R.string.launcher_call_not_supported
        PhoneError.FAILED -> R.string.launcher_call_failed
        PhoneError.ASSISTANT_UNAVAILABLE -> R.string.launcher_phone_assistant_failed
        PhoneError.PERMISSION -> R.string.launcher_phone_permission
    }
}
