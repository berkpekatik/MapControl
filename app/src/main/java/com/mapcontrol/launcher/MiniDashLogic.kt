package com.mapcontrol.launcher

import kotlin.math.roundToInt

/**
 * Mini göstergenin (bölünmüş ekranın bir yarısı, bkz. [MiniDashActivity]) saf kuralları; JVM'de sınanır.
 *
 * Uyarı satırı yalnızca bölünmüş ekranda BAŞKA YERDE görünmeyen şeyleri söyler (ana ekran o sırada gizli, fabrika çubuğu
 * yalnızca klimayı gösterir): motor suyu sıcak, akü zayıf / yüksek, düşük lastik (psi ile), yakıt kapağı açık, düşük yakıt,
 * yaklaşan / geçmiş bakım ve kötü kabin havası. Eşikler Sahne ile aynıdır ([HealthMath], [StageCorners], [TireDecode]); yeni
 * eşik yoktur.
 */
internal object MiniAlerts {

    /**
     * Uyarı türü; sıra önem sırasıdır (küçük = önce): motor suyu / akü > düşük lastik > yakıt kapağı > yakıt az > bakım > hava.
     * Bunların hiçbiri araç çiziminde görünmez (§10: çizimde görünen kapı, bagaj, kaput, cam yazılmaz).
     */
    enum class Kind { ENGINE_HOT, BATTERY_LOW, BATTERY_HIGH, TIRE, FUEL_CAP, FUEL_LOW, SERVICE_OVERDUE, SERVICE_NEAR, AIR_BAD }

    /**
     * Tek uyarı ve metni kuracak değerler ([tireLow] lastikte, [serviceKm] bakımda, [pm25] havada, [batteryV] aküde).
     */
    data class Item(
        val kind: Kind,
        val tireLow: TireLow? = null,
        val serviceKm: Int? = null,
        val pm25: Int? = null,
        val batteryV: Double? = null,
    )

    /** Satırda aynı anda adlandırılan en çok uyarı; fazlası "+N" olur. */
    const val MAX_NAMED = 2

    fun build(
        tires: TireReading?,
        lowFuel: Boolean?,
        serviceKm: Int?,
        serviceDays: Int?,
        pm25In: Int?,
        batteryV: Double? = null,
        fuelCapOpen: Boolean = false,
        batteryHighHeld: Boolean = false,
    ): List<Item> {
        val low = TireDecode.low(tires)
        return build(TireDecode.alertShown(tires?.warning, low), low, lowFuel, serviceKm, serviceDays, pm25In, batteryV, fuelCapOpen, batteryHighHeld)
    }

    /**
     * Sahne uyarı şeridi de aynı kuralları kullanır (§10): araç kartının özetinden gelen lastik uyarısı ([tireAlert],
     * [tireLow]) ile. Kapı, bagaj, kaput, cam ve cam tavan burada yoktur; onları araç çizimi gösterir. Akü [HealthMath]
     * eşikleriyle (okunamayan değer uyarı sayılmaz); motor suyu uyarısı derece sinyali doğrulanana dek yok ([CoolantStatus]);
     * yakıt kapağı çizimde yoktur, şeritte yazılır.
     */
    fun build(
        tireAlert: Boolean,
        tireLow: TireLow?,
        lowFuel: Boolean?,
        serviceKm: Int?,
        serviceDays: Int?,
        pm25In: Int?,
        batteryV: Double? = null,
        fuelCapOpen: Boolean = false,
        /** Aşırı gerilim 30 sn sürdü mü ([TripData.batteryHighHeld]); değilse "Akü yüksek" çıkmaz. */
        batteryHighHeld: Boolean = false,
    ): List<Item> = buildList {
        val health = HealthMath.issues(batteryV, null, null, null, batteryHighHeld)
        if (HealthMath.Issue.BATTERY_LOW in health) add(Item(Kind.BATTERY_LOW, batteryV = batteryV))
        if (HealthMath.Issue.BATTERY_HIGH in health) add(Item(Kind.BATTERY_HIGH, batteryV = batteryV))
        if (tireAlert) add(Item(Kind.TIRE, tireLow = tireLow))
        if (fuelCapOpen) add(Item(Kind.FUEL_CAP))
        if (lowFuel == true) add(Item(Kind.FUEL_LOW))
        when {
            HealthMath.serviceOverdue(serviceKm, serviceDays) -> add(Item(Kind.SERVICE_OVERDUE))
            StageCorners.serviceNear(serviceKm, serviceDays) -> add(Item(Kind.SERVICE_NEAR, serviceKm = serviceKm))
        }
        if (StageCorners.airShown(pm25In)) add(Item(Kind.AIR_BAD, pm25 = pm25In))
    }

    /** Adlandırılan uyarılar ve "+N" sayısı (en önemli uyarı hiç kaybolmaz). */
    fun visible(all: List<Item>): Pair<List<Item>, Int> = StageNotices.visible(all, MAX_NAMED)
}

/**
 * Navigasyon ipucu: sonraki dönüş ve varış. [maneuver] kısa yön metni ("Sağa dönün"), [distanceM] dönüşe kalan metre,
 * [arrivalMinutesOfDay] varış saati (gün içi dakika, 0..1439); bilinmeyen alan null.
 */
internal data class NavHint(
    val maneuver: String?,
    val distanceM: Int?,
    val arrivalMinutesOfDay: Int?,
)

/** Navigasyon satırının saf biçimlendirmesi. */
internal object MiniNav {

    /** Satır yalnızca gösterecek bir şey varken görünür. */
    fun shown(h: NavHint?): Boolean = h != null && (!h.maneuver.isNullOrBlank() || h.distanceM != null || h.arrivalMinutesOfDay != null)

    /** Mesafe: 1 km altı 10 m'ye yuvarlanır ("350 m"); üstü 0,1 km ("1,2 km", 10 km üstü tam sayı). Geçersizse null. */
    fun distance(m: Int?, decimalSep: Char = ','): Pair<String, Boolean>? {
        if (m == null || m < 0) return null
        val meters = (m + 5) / 10 * 10
        if (meters < 1_000) return meters.toString() to false
        val tenths = (m + 50) / 100
        if (tenths >= 100) return ((m + 500) / 1_000).toString() to true
        return "${tenths / 10}$decimalSep${tenths % 10}" to true
    }

    /**
     * [NavigationSource] durumundan ipucu (mini göstergenin gerçek kaynağı, [NavigationMiniSource]): rota yoksa null; yön
     * metni "manevra · çıkış · yol" ([label] manevranın kısa adı, bilinmeyende null), mesafe metre, varış "HH:mm" → gün içi
     * dakika (okunamazsa null).
     */
    fun hint(s: NavigationState, label: (NavManeuver) -> String?, exit: (Int) -> String): NavHint? {
        if (!s.active) return null
        val text = listOfNotNull(label(s.maneuver), s.roundaboutExit?.let(exit), s.street?.takeIf { it.isNotBlank() })
            .joinToString(" · ").ifEmpty { null }
        return NavHint(text, s.distance?.meters, minutesOfDay(s.arrivalTime))
    }

    /** "14:52" → 892; geçersizse null. */
    fun minutesOfDay(hhmm: String?): Int? {
        val m = Regex("""^\s*(\d{1,2})[:.](\d{2})\s*$""").find(hhmm ?: return null) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    /** Varış saati "HH:mm" (24 saat) ya da "h:mm" (12 saat, AM/PM çağıranda); geçersizse null. */
    fun arrival(minutesOfDay: Int?, is24: Boolean = true): String? {
        if (minutesOfDay == null || minutesOfDay !in 0 until 24 * 60) return null
        val h = minutesOfDay / 60
        val m = minutesOfDay % 60
        val hh = if (is24) h else (if (h % 12 == 0) 12 else h % 12)
        return (if (is24) "%02d:%02d" else "%d:%02d").format(hh, m)
    }
}

/**
 * Pencere boyutuna göre yerleşim (dp); saf kural, JVM'de sınanır. İki biçim vardır:
 *
 * - Bölünmüş yarı (Tiggo 7 sol yarı ~960 × 606 dp, serbest pencere başlığı altında): üstte saat + menzil + yakıt + düğmeler
 *   (tek satır), ortada araç sahnesi | yan sütun (yolculuk + telefon), altta tam genişlikte ince medya şeridi.
 * - Geniş ve kısa pencere ([wide]; tam ekran 1920 × 648, Tiggo 8 üst yarısı 1707 × ~470): solda bilgi sütunu (saat, menzil,
 *   yakıt, rota, düğmeler), ortada araç sahnesi ve altında medya şeridi, sağda yan sütun.
 *
 * Yan sütunda üstte yolculuk paneli (kalan yükseklik), altında telefon paneli (doğal yüksekliği, [MiniSide.phoneDp]).
 * Çalan bir şey yokken medya şeridi hiç yer tutmaz (yalnızca üstteki "Müzik aç" düğmesi kalır); araç ve yan sütun büyür.
 * Dokunma alanları sürüşte de [MIN_TOUCH_DP] altına inmez.
 */
internal data class MiniLayout(
    val wide: Boolean,
    val paddingDp: Int,
    /** Bloklar arası boşluk. */
    val gapDp: Int,
    val clockSp: Float,
    /** Menzil ve yakıt rakamları; birim ("km") bunun yarısı. */
    val valueSp: Float,
    /** Bölünmüş biçimde üst satırın yüksekliği (geniş biçimde 0). */
    val headerDp: Int,
    /** "Tam ekran" / "Müzik aç" düğmelerinde yazı (dar pencerede yalnızca simge, yine 64 dp kare). */
    val pillText: Boolean,
    /** Yan sütunun (yolculuk + telefon) genişliği. */
    val sideDp: Int,
    /** Geniş biçimde bilgi sütununun genişliği (bölünmüşte 0). */
    val infoDp: Int,
    /** Medya şeridi yüksekliği (çalan bir şey varken). */
    val mediaDp: Int,
    /** Kapak resmi kenarı; 0 = kapak yok (yer yetmiyorsa). */
    val coverDp: Int,
    val playDp: Int,
    val skipDp: Int,
    /** Medya şeridi varken yan sütunun beklenen yüksekliği (yerleşim ağırlıklarla aynı sonucu verir). */
    val sideHeightDp: Int,
    /** Yan sütundaki panellerin iç payı. */
    val panelPadDp: Int,
    /** Yolculuk panelinin öncü sayısı ve alt satırdaki değerler. */
    val tripHeroSp: Float,
    val tripStatSp: Float,
    /** Telefon: "Geri ara" satırı yüksekliği ve favori avatarı. */
    val callRowDp: Int,
    val favAvatarDp: Int,
    /** Rota satırı yalnızca tam ekranda: bölünmüş ekranda harita zaten yanda, aynı bilgiyi tekrarlamaz. */
    val showNav: Boolean,
    /** Yolculuk değerleri etiketli liste hâlinde mi (yalnız büyük geniş pencere); değilse tek satır. */
    val tripList: Boolean = false,
    /** Geniş biçimde saatin altında tarih (kısa geniş pencerede yer açmak için düşer). */
    val showDate: Boolean = false,
    /** Geniş biçimde bilgi sütununda saat → menzil arası; menzil → yakıt arası bunun 6 dp eksiği. */
    val infoGapDp: Int = 28,
) {
    companion object {
        const val MIN_TOUCH_DP = 64

        /** Geniş satır yerleşimi: pencere en az bu kadar geniş ve en-boy oranı en az bu kadar yatay. */
        const val WIDE_MIN_WIDTH_DP = 1200
        const val WIDE_MIN_ASPECT = 2.4f

        /** Yazı tipi payı açık metin satırı / yazı boyu (Manrope, tahmini üst sınır). */
        private const val PADDED_LINE = 1.6f
        /** Rota satırının en küçük yüksekliği ve üst payı (görünümle aynı). */
        const val NAV_ROW_DP = 68
        const val NAV_GAP_DP = 24
        const val INFO_LABEL_SP = 16f

        /**
         * Geniş biçimde bilgi sütununun (saat, tarih, menzil, yakıt, rota, düğmeler) gereken yüksekliği; rota satırı görünür
         * varsayılır. [MiniDashActivity] aynı ölçüleri kullanır.
         */
        fun infoNeedDp(m: MiniLayout): Int {
            val line = MiniSide.TRIP_LINE
            val clock = m.clockSp * line
            val date = if (m.showDate) 8 + 20f * PADDED_LINE else 0f
            val value = m.valueSp * line + 4 + INFO_LABEL_SP * PADDED_LINE
            val nav = if (m.showNav) (NAV_GAP_DP + NAV_ROW_DP).toFloat() else 0f
            return (clock + date + m.infoGapDp + value + (m.infoGapDp - 6) + value + nav + MIN_TOUCH_DP).roundToInt()
        }

        /** Saf kural (birim testlenir): 960 genişlikli yarılar (≈ 1,6) ve dar pencereler false. */
        fun isWide(widthDp: Int, heightDp: Int): Boolean =
            heightDp > 0 && widthDp >= WIDE_MIN_WIDTH_DP && widthDp.toFloat() / heightDp >= WIDE_MIN_ASPECT

        /**
         * [heightDp] pencerenin içerik yüksekliği; [captionDp] serbest pencere başlığının içeriğin üstünü örttüğü pay
         * ([FreeformCaption.overlap]); içerik bu payın altından başlar.
         */
        fun forSize(widthDp: Int, heightDp: Int, captionDp: Int = 0): MiniLayout {
            val usableH = heightDp - captionDp.coerceAtLeast(0)
            val wide = isWide(widthDp, usableH)
            val compact = widthDp < 760 || usableH < 520
            val tight = usableH < 440
            val padding = if (compact) 16 else 24
            val gap = if (compact) 12 else 16
            val header = if (wide) 0 else if (compact) 72 else 84
            var media = if (tight) 88 else if (compact) 96 else if (wide && usableH >= 560) 120 else 108
            val contentH = usableH - 2 * padding
            val big = wide && usableH >= 560
            // Yan sütun: bölünmüşte araçla aynı satırda (medya şeridi altta, tam genişlik), genişte tam yükseklik.
            fun side(mediaH: Int): Int = if (wide) contentH else contentH - header - gap - mediaH - gap
            val layout = MiniLayout(
                wide = wide,
                paddingDp = padding,
                gapDp = gap,
                // Kısa geniş pencerede (Tiggo 8 üst yarısı) bilgi sütunu sığsın: saat ve rakamlar küçülür, tarih düşer.
                clockSp = if (wide) (if (big) 80f else 52f) else if (compact) 48f else 60f,
                valueSp = if (wide) (if (big) 48f else 40f) else if (compact) 30f else 38f,
                headerDp = header,
                pillText = wide || widthDp >= 860,
                sideDp = if (wide) (widthDp * 0.29f).toInt().coerceIn(420, 560) else (widthDp * 0.38f).toInt().coerceIn(280, 380),
                // En az 372: "Müzik aç" + "Tam ekran" yazılı düğmeleri yan yana kesilmeden sığar (1707 genişlikte 341 yetmiyordu).
                infoDp = if (wide) (widthDp * 0.2f).toInt().coerceIn(372, 420) else 0,
                mediaDp = media,
                coverDp = if (media >= 96) media - 24 else 0,
                playDp = if (tight) 72 else 80,
                skipDp = MIN_TOUCH_DP,
                sideHeightDp = side(media),
                panelPadDp = if (big) 24 else 12,
                tripHeroSp = if (big) 64f else 38f,
                tripStatSp = if (big) 26f else 20f,
                callRowDp = if (big) 80 else MIN_TOUCH_DP,
                favAvatarDp = if (big) 72 else if (compact) 48 else 52,
                showNav = wide,
                tripList = big,
                showDate = big,
                infoGapDp = if (big) 28 else 16,
            )
            // Yolculuk değerleri (süre · km/sa · L/100 km) tek satırda yan sütun genişliğine sığsın: yazı gerekirse küçülür.
            val statSp = if (layout.tripList) layout.tripStatSp
                else minOf(layout.tripStatSp, MiniSide.statSpForWidth(layout.sideDp, layout.panelPadDp))
            val sized = layout.copy(tripStatSp = statSp)
            // Genişte yan sütun tam boy: medya ve başlık etkilemez.
            if (wide) return sized
            return fitSide(sized, contentH)
        }

        /** Yan sütunun iki paneli ve [SIDE_SPARE_DP] pay sığıyor mu (bölünmüş biçim). */
        private fun sideFits(m: MiniLayout): Boolean = m.sideHeightDp >= MiniSide.sideNeedDp(m) + SIDE_SPARE_DP

        /**
         * Bölünmüş biçimde yan sütun (yolculuk değer satırı dahil) sığana dek sırayla küçültür: (1) medya şeridi en ince (kapak
         * düşer), (2) üst satır 72 dp ve boşluklar 12 dp, (3) favori avatarı 44 dp, (4) yolculuğun öncü sayısı 34 sp. 7 Kasım
         * araç testi: sürüş ekranında Mini gösterge yarısı ~960 × 580 dp; eski kural 1 dp eksik kalıyordu ve yolculuk panelinde
         * yalnız "89,6 km" görünüyordu (süre / km/sa / L/100 km satırı düşüyordu). Dokunma alanları [MIN_TOUCH_DP] altına inmez.
         */
        private fun fitSide(start: MiniLayout, contentH: Int): MiniLayout {
            fun withSide(m: MiniLayout) = m.copy(sideHeightDp = contentH - m.headerDp - m.gapDp - m.mediaDp - m.gapDp)
            val steps = listOf<(MiniLayout) -> MiniLayout>(
                { it.copy(mediaDp = 88, coverDp = 0) },
                { it.copy(headerDp = minOf(it.headerDp, 72), gapDp = minOf(it.gapDp, 12)) },
                { it.copy(favAvatarDp = minOf(it.favAvatarDp, 44)) },
                { it.copy(tripHeroSp = minOf(it.tripHeroSp, 34f)) },
            )
            var m = start
            for (step in steps) {
                if (sideFits(m)) return m
                m = withSide(step(m))
            }
            return m
        }

        /** Yan sütunda gereken yüksekliğin üstüne bırakılan pay: gerçek satır yükseklikleri tahminden birkaç dp sapabilir. */
        const val SIDE_SPARE_DP = 4
    }
}

/**
 * Yan sütunun saf kararları (koltuk tuşları yerine; koltuklar ana ekranda kalır): yolculuk paneli ve telefon paneli.
 *
 * Yolculuk: araç sürüşteyken öncü sayı canlı anlık tüketimdir ([TripHero], ana ekrandaki yolculuk kartıyla aynı durumlar),
 * altında bu yolculuğun mesafesi, süresi ve ortalama hızı; dururken öncü sayı yolculuk mesafesi, altında süre, ortalama hız
 * ve ortalama tüketim. Araç verisi yokken panel hiç görünmez (sahnedeki tek "Araç verisi bekleniyor" satırı yeter; tire yok).
 *
 * Telefon: son isimli arama için tek dokunuşla "Geri ara" ve en çok [FAVORITES] favori. Bağlı telefon yokken kutu yok, tek
 * sessiz satır. Gelen / süren arama ana ekranın arama şeridinde; burada kopyalanmaz.
 */
internal object MiniSide {
    const val FAVORITES = 3
    /** Favori adının satırı ve avatarla arası (dp). */
    const val NAME_ROW_DP = 22
    const val NAME_GAP_DP = 4
    /** Telefon panelinde "Geri ara" satırı ile favoriler arası. */
    const val ROW_GAP_DP = 10
    /** Yolculuk panelinin başlık satırı ve satır araları. */
    const val TRIP_HEAD_DP = 22
    /** Başlık ile öncü satır, öncü satır ile değer satırı arası (görünüm de aynısını kullanır). */
    const val TRIP_GAP_DP = 6
    /** Manrope satır yüksekliği / yazı boyu (yazı tipi payı kapalıyken: çıkış + iniş = 1,366 em). */
    const val TRIP_LINE = 1.37f

    enum class Stat { DISTANCE, DURATION, AVG_SPEED, AVG_CONS }

    /** Geniş liste biçimi: satır yüksekliği, ince çizgi ve listenin üst payı (görünüm de aynısını kullanır). */
    const val LIST_ROW_DP = 52
    const val LIST_GAP_DP = 20

    /** Değer satırındaki ayırıcıların iki yanındaki boşluk (görünüm de aynısını kullanır: [MiniTripPanel]). */
    const val STAT_GAP_DP = 14

    /**
     * Üç değerin ("1 sa 12 dk", "45 km/sa", "6,8 L/100 km"; birimler 0,72 boyda) toplam genişliği, yazı boyu cinsinden (em);
     * Manrope rakam ve harf genişliğinden tahmini üst sınır.
     */
    const val STATS_EM = 13.5f

    /** Yolculuk değer satırı yazı boyunun alt sınırı. */
    const val MIN_STAT_SP = 16f

    /**
     * Değer satırının [sideDp] genişliğindeki yan sütuna tek satırda sığacağı en büyük yazı boyu (sp ≈ dp): panel iç payı
     * ([panelPadDp] + 6 her yanda), iki ayırıcı ve boşlukları düşülür. [MIN_STAT_SP] altına inmez.
     */
    fun statSpForWidth(sideDp: Int, panelPadDp: Int): Float {
        val inner = sideDp - 2 * (panelPadDp + 6) - 4 * STAT_GAP_DP - 2
        return (inner / STATS_EM).coerceAtLeast(MIN_STAT_SP)
    }

    /** Değer satırının tahmini genişliği (dp). */
    fun statsRowDp(m: MiniLayout): Int = (m.tripStatSp * STATS_EM).roundToInt() + 4 * STAT_GAP_DP + 2

    /** Yolculuk paneli görünür mü: yalnız veri yokken gizli. */
    fun tripShown(content: TripHero.Content): Boolean = content != TripHero.Content.NO_DATA

    /**
     * Öncü sayının (yolculuk mesafesi) altındaki değerler (sırasıyla; okunamayan düşer): süre, ortalama hız, ortalama
     * tüketim. Sürüşte de aynıdır; anlık tüketim bu panelde gösterilmez.
     */
    fun stats(content: TripHero.Content, km: Double?, minutes: Int?, avgSpeedKmh: Int?, avgL100: Double?): List<Stat> = when (content) {
        TripHero.Content.NO_DATA -> emptyList()
        TripHero.Content.TRIP -> listOfNotNull(
            Stat.DURATION.takeIf { minutes != null && minutes > 0 },
            Stat.AVG_SPEED.takeIf { avgSpeedKmh != null },
            Stat.AVG_CONS.takeIf { avgL100 != null },
        )
    }

    /** Telefon panelinin hâli: kutusuz sessiz satır (bağlı değil, yükleniyor, boş), izin satırı ya da içerik. */
    enum class Phone { OFF, LOADING, EMPTY, PERMISSION, CONTENT }

    /** Gösterilecek telefon içeriği. [last] "Geri ara" kaydı (isimli), [favorites] en çok [FAVORITES] isimli kişi. */
    data class PhoneView(val state: Phone, val last: PhoneRecent?, val favorites: List<PhoneContact>) {
        override fun toString(): String = "PhoneView(state=$state, last=${last != null}, fav=${favorites.size})"
    }

    /**
     * Telefon durumundan panel. Favoriler "Geri ara" kişisini tekrarlamaz (numara son 10 haneyle karşılaştırılır); ne son
     * arama ne favori varsa boş sayılır.
     */
    fun phone(s: PhoneSnapshot): PhoneView {
        val state = when (PhoneUiLogic.panelState(s)) {
            PhoneUiLogic.PanelState.NO_PHONE -> Phone.OFF
            PhoneUiLogic.PanelState.PERMISSION -> Phone.PERMISSION
            PhoneUiLogic.PanelState.LOADING -> Phone.LOADING
            PhoneUiLogic.PanelState.EMPTY -> Phone.EMPTY
            PhoneUiLogic.PanelState.CONTENT -> Phone.CONTENT
        }
        if (state != Phone.CONTENT) return PhoneView(state, null, emptyList())
        val last = PhoneUiLogic.lastNamedRecent(s.recents)
        val lastNumber = last?.let { FavoritesRanker.normalizeNumber(it.number) }
        val favs = s.favorites.filter { PhoneUiLogic.hasName(it.name) && (lastNumber == null || FavoritesRanker.normalizeNumber(it.number) != lastNumber) }
            .take(FAVORITES)
        if (last == null && favs.isEmpty()) return PhoneView(Phone.EMPTY, null, emptyList())
        return PhoneView(Phone.CONTENT, last, favs)
    }

    /** Favori satırının yüksekliği: avatar + ad. */
    fun favoriteRowDp(m: MiniLayout): Int = m.favAvatarDp + NAME_GAP_DP + NAME_ROW_DP

    /** Telefon panelinin doğal yüksekliği; içerik yoksa tek sessiz satır ([MiniLayout.MIN_TOUCH_DP]). */
    fun phoneDp(v: PhoneView, m: MiniLayout): Int {
        if (v.state != Phone.CONTENT) return MiniLayout.MIN_TOUCH_DP
        val rows = listOfNotNull(m.callRowDp.takeIf { v.last != null }, favoriteRowDp(m).takeIf { v.favorites.isNotEmpty() })
        return 2 * m.panelPadDp + rows.sum() + ROW_GAP_DP * (rows.size - 1).coerceAtLeast(0)
    }

    /** En dolu telefon paneli (Geri ara + favoriler). */
    fun phoneMaxDp(m: MiniLayout): Int = 2 * m.panelPadDp + m.callRowDp + ROW_GAP_DP + favoriteRowDp(m)

    /**
     * Yolculuk panelinin en küçük yüksekliği: başlık, öncü sayı ve değer satırı; ikisi de yazı tipi payı kapalı, satır
     * yüksekliği [TRIP_LINE] (görünümle aynı ölçü: [MiniTripPanel]). Liste biçiminde değer satırı yerine üç etiketli satır.
     */
    fun tripMinDp(m: MiniLayout): Int =
        2 * m.panelPadDp + TRIP_HEAD_DP + TRIP_GAP_DP + (m.tripHeroSp * TRIP_LINE).roundToInt() +
            if (m.tripList) LIST_GAP_DP + 3 * (LIST_ROW_DP + 1) else TRIP_GAP_DP + (m.tripStatSp * TRIP_LINE).roundToInt()

    /** Yan sütunun iki paneli için gereken en az yükseklik. */
    fun sideNeedDp(m: MiniLayout): Int = tripMinDp(m) + m.gapDp + phoneMaxDp(m)
}
