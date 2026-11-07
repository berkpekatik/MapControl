package com.mapcontrol.launcher

/**
 * Yandex rehberlik bildiriminden okunan ham parçalar (Android'e bağlı değil; JVM'de sınanır). [NavNotificationReader]
 * doldurur: extras metinleri, RemoteViews içindeki metinler ve çizim kaynak adları (görünüm kimliğinin adıyla birlikte,
 * ör. "titleView" → "300 м", "primaryIcon" → "notification_right_sdl").
 */
data class NavNotificationInput(
    val packageName: String,
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val bigText: String? = null,
    val infoText: String? = null,
    /** RemoteViews metinleri: (görünüm kimliği adı ya da null, metin), eylem sırasıyla. */
    val viewTexts: List<Pair<String?, String>> = emptyList(),
    /** RemoteViews çizimleri: (görünüm kimliği adı ya da null, çizim kaynak adı), eylem sırasıyla. */
    val images: List<Pair<String?, String>> = emptyList(),
    /** Bildirimin küçük ikonunun kaynak adı (Android Auto eki açıksa manevra çizimi olur). */
    val smallIconName: String? = null,
)

/** Mesafe: ekranda gösterilen sayı ("1,2"), birim ("m" / "km") ve metre karşılığı. */
data class NavDistance(val value: String, val unit: String, val meters: Int) {
    val text: String get() = "$value $unit"
}

/**
 * Yandex bildirim metinlerinin saf çözümleyicisi. Rusça, Türkçe ve İngilizce biçimleri tanır: "300 м", "300 m", "1,2 км",
 * "14:52", "18 dk", "1 ч 12 мин", "3. çıkış", "3-й съезд". Hiçbir metni günlüğe yazmaz.
 */
object NavParse {

    private const val NUM = """(\d+(?:[.,]\d+)?)"""
    private val DISTANCE = Regex("""(?iu)(?<![\d:])$NUM\s*(км|km|м|m)(?![\p{L}])""")
    private val DISTANCE_ONLY = Regex("""(?iu)^\s*$NUM\s*(км|km|м|m)\s*$""")
    private val CLOCK = Regex("""(?<!\d)([01]?\d|2[0-3])[:.]([0-5]\d)(?!\d)""")
    private val HOURS = Regex("""(?iu)(\d+)\s*(sa|saat|ч|час|часа|часов|h|hr|hrs|hour|hours)(?![\p{L}])""")
    private val MINUTES = Regex("""(?iu)(\d+)\s*(dk|dakika|мин|min|mins|minute|minutes)(?![\p{L}])""")
    private val EXIT_TR = Regex("""(?iu)(\d+)\s*\.\s*(çıkış|cikis|sapak)""")
    private val EXIT_RU = Regex("""(?iu)(\d+)\s*(?:-?\s*(?:й|я|е|ой|ий))?\s*(съезд|поворот)""")
    private val EXIT_EN_NUM = Regex("""(?iu)(?:exit\s+(\d+))|(?:(\d+)\s*(?:st|nd|rd|th)\s+exit)""")
    private val EXIT_EN_WORD = Regex("""(?iu)\b(first|second|third|fourth|fifth|sixth|seventh|eighth)\s+exit""")
    private val EN_ORDINALS = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth")
    private val SPLIT = Regex("""\s*[·•|\n]\s*|,\s+|\s+[—–-]\s+""")
    /** Rota yokken Yandex'in yer tutucu bildirimi ("Navigator is running" / "Navigator başlatıldı" / "Навигатор запущен"). */
    private val PLACEHOLDER = Regex("""(?iu)(navigator|навигатор|navigasyon).*(running|başlatıldı|запущен|çalışıyor|работает)""")
    /** Varış etiketi önekleri: "Varış 14:52", "Прибытие 14:52", "ETA 14:52", "Arrival 14:52". */
    private val ARRIVAL_WORDS = Regex("""(?iu)^(varış|varis|прибытие|eta|arrival|arrive|в\s+пути\s+до)\s*:?\s*""")

    /**
     * Yandex çizim kaynak adından manevra: bildirim adları (`notification_left_sdl`), balon adları (`context_ra_turn_left`).
     * Manevra çizimi olmayan (şerit, trafik ışığı, uygulama ikonu) ad null döner; `directions_lane_unknown_*` [NavManeuver.UNKNOWN].
     */
    fun maneuverFromResourceName(name: String?): NavManeuver? {
        if (name.isNullOrBlank()) return null
        val n = name.lowercase()
        if (n.startsWith("directions_lane_unknown")) return NavManeuver.UNKNOWN
        val core = when {
            n.startsWith("notification_") && n.endsWith("_sdl") -> n.removePrefix("notification_").removeSuffix("_sdl")
            n.startsWith("context_ra_") -> n.removePrefix("context_ra_")
            else -> return null
        }
        return when (core) {
            "straight", "forward" -> NavManeuver.STRAIGHT
            "slight_left", "fork_left", "take_left" -> NavManeuver.SLIGHT_LEFT
            "slight_right", "fork_right", "take_right" -> NavManeuver.SLIGHT_RIGHT
            "left", "turn_left" -> NavManeuver.LEFT
            "right", "turn_right" -> NavManeuver.RIGHT
            "hard_left", "hard_turn_left" -> NavManeuver.HARD_LEFT
            "hard_right", "hard_turn_right" -> NavManeuver.HARD_RIGHT
            "uturn_left", "turn_back_left" -> NavManeuver.UTURN_LEFT
            "uturn_right", "turn_back_right" -> NavManeuver.UTURN_RIGHT
            "enter_roundabout", "in_circular_movement" -> NavManeuver.ROUNDABOUT_ENTER
            "leave_roundabout", "out_circular_movement" -> NavManeuver.ROUNDABOUT_EXIT
            "exit_left" -> NavManeuver.EXIT_LEFT
            "exit_right" -> NavManeuver.EXIT_RIGHT
            "board_ferry", "leave_ferry", "boardferry" -> NavManeuver.FERRY
            "finish" -> NavManeuver.FINISH
            "via", "waypoint" -> NavManeuver.WAYPOINT
            else -> null
        }
    }

    /** Metnin tamamı bir mesafe mi ("300 м", "1,2 km"). */
    fun parseDistanceOnly(text: String?): NavDistance? {
        val m = DISTANCE_ONLY.find(text ?: return null) ?: return null
        return distance(m.groupValues[1], m.groupValues[2])
    }

    /** Metindeki ilk mesafe ("Осталось 18 км" → 18 km). */
    fun findDistance(text: String?): NavDistance? {
        val m = DISTANCE.find(text ?: return null) ?: return null
        return distance(m.groupValues[1], m.groupValues[2])
    }

    private fun distance(rawNum: String, rawUnit: String): NavDistance? {
        val km = rawUnit.lowercase().let { it == "км" || it == "km" }
        val number = rawNum.replace(',', '.').toDoubleOrNull() ?: return null
        // Ekranda Türkçe biçim: ondalık virgül, birim Latin harfli.
        return NavDistance(rawNum.replace('.', ','), if (km) "km" else "m", (if (km) number * 1000 else number).toInt())
    }

    /** "14:52" (ya da "14.52") → "14:52"; yoksa null. */
    fun findClock(text: String?): String? {
        val m = CLOCK.find(text ?: return null) ?: return null
        return "${m.groupValues[1].padStart(2, '0')}:${m.groupValues[2]}"
    }

    /** "18 dk", "1 sa 12 dk", "1 ч 12 мин", "45 min" → dakika; saat/dakika birimi yoksa null. */
    fun parseMinutes(text: String?): Int? {
        if (text == null) return null
        val h = HOURS.find(text)?.groupValues?.get(1)?.toIntOrNull()
        val m = MINUTES.find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (h == null && m == null) return null
        return (h ?: 0) * 60 + (m ?: 0)
    }

    /** Yuvarlak kavşak çıkış numarası: "3. çıkış", "3-й съезд", "3 съезд", "Exit 3", "3rd exit", "Third exit". */
    fun parseExitNumber(text: String?): Int? {
        if (text == null) return null
        EXIT_TR.find(text)?.let { return it.groupValues[1].toIntOrNull() }
        EXIT_RU.find(text)?.let { return it.groupValues[1].toIntOrNull() }
        EXIT_EN_NUM.find(text)?.let { m -> return (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull() }
        EXIT_EN_WORD.find(text)?.let { return EN_ORDINALS.indexOf(it.groupValues[1].lowercase()) + 1 }
        return null
    }

    fun isPlaceholder(text: String?): Boolean = text != null && PLACEHOLDER.containsMatchIn(text)

    /** Bir parça yalnızca zaman/mesafe/çıkış bilgisi mi (sokak adı değil). */
    private fun isMetric(piece: String): Boolean {
        val stripped = piece.replace(ARRIVAL_WORDS, "")
        if (DISTANCE_ONLY.matches(stripped)) return true
        if (CLOCK.matches(stripped.trim())) return true
        if (parseExitNumber(stripped) != null && stripped.length < 24) return true
        // "18 dk", "1 ч 12 мин": rakam ve süre birimlerinden başka bir şey yok.
        val noTime = stripped.replace(HOURS, "").replace(MINUTES, "").trim()
        if (noTime.isEmpty() && parseMinutes(stripped) != null) return true
        return false
    }

    /**
     * Bildirimi rota durumuna çevirir. Rota yoksa (yer tutucu, boş, manevra ve mesafe yok) null döner.
     * Kurallar: manevra çizimi tercihen "primaryIcon*" görünümünden, yoksa ilk tanınan çizimden, yoksa küçük ikondan;
     * mesafe başlıktan (tamamı mesafe olan ilk metin); sokak, metrik olmayan ilk metin (içerik metni önce); varış saati,
     * kalan süre ve kalan mesafe geri kalan metinlerden.
     */
    fun parse(input: NavNotificationInput, nowMs: Long = 0L): NavigationState? {
        val viewTexts = input.viewTexts.map { it.first?.lowercase() to it.second.trim() }.filter { it.second.isNotEmpty() }
        val extras = listOfNotNull(input.title, input.text, input.subText, input.bigText, input.infoText).map { it.trim() }.filter { it.isNotEmpty() }
        val all = extras + viewTexts.map { it.second }
        if (all.isEmpty() && input.images.isEmpty()) return null

        val primary = input.images.firstOrNull { (view, name) -> view?.lowercase()?.startsWith("primaryicon") == true && maneuverFromResourceName(name) != null }
        val maneuver = maneuverFromResourceName(primary?.second)
            ?: input.images.firstNotNullOfOrNull { maneuverFromResourceName(it.second) }
            ?: maneuverFromResourceName(input.smallIconName)

        // Mesafe: önce başlık görünümü / başlık, sonra herhangi bir tam-mesafe metni.
        val titleView = viewTexts.firstOrNull { it.first == "titleview" }?.second
        val distanceSource = listOfNotNull(titleView, input.title) + all
        val distance = distanceSource.firstNotNullOfOrNull { parseDistanceOnly(it) }

        if (maneuver == null && distance == null) return null
        if (maneuver == null && all.any { isPlaceholder(it) }) return null

        // Sokak: açıklama görünümü, içerik metni, sonra metrik olmayan ilk parça (yer tutucu ve paket adı hariç).
        val descView = viewTexts.firstOrNull { it.first == "descriptionview" }?.second
        val streetCandidates = listOfNotNull(descView, input.text, input.bigText) + viewTexts.map { it.second } + extras
        val street = streetCandidates.asSequence()
            .flatMap { it.split(SPLIT).asSequence() }
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !isMetric(it) && !isPlaceholder(it) && it.any { c -> c.isLetter() } }

        // Varış, kalan süre ve kalan mesafe: mesafe metni ve sokak dışındaki parçalardan.
        // Manevra mesafesinin kopyaları (başlık hem extras'ta hem görünümde) kalan mesafe sayılmaz.
        val pieces = all.flatMap { it.split(SPLIT) }.map { it.trim() }
            .filter { it.isNotEmpty() && it != street && (distance == null || parseDistanceOnly(it) != distance) }
        var arrival: String? = null
        var minutes: Int? = null
        var remaining: NavDistance? = null
        for (p in pieces) {
            if (arrival == null) arrival = findClock(p)
            if (minutes == null) minutes = parseMinutes(p)
            if (remaining == null) remaining = findDistance(p)
        }
        val exit = if (maneuver == NavManeuver.ROUNDABOUT_EXIT || maneuver == NavManeuver.ROUNDABOUT_ENTER) {
            all.firstNotNullOfOrNull { parseExitNumber(it) }
        } else null

        return NavigationState(
            active = true,
            maneuver = maneuver ?: NavManeuver.UNKNOWN,
            roundaboutExit = exit,
            distance = distance,
            street = street,
            arrivalTime = arrival,
            remainingDistance = remaining,
            remainingMinutes = minutes,
            sourcePackage = input.packageName,
            updatedAtMs = nowMs,
        )
    }

    /** Ev / iş konumu ayarı: "41.0082, 28.9784" (virgül, boşluk ya da noktalı virgülle) → (enlem, boylam); geçersizse null. */
    fun parseLatLon(raw: String?): Pair<Double, Double>? {
        val parts = raw?.trim()?.split(Regex("""[\s,;]+"""))?.filter { it.isNotEmpty() } ?: return null
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }
}
