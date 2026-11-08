package com.mapcontrol.launcher

import android.app.Notification
import com.mapcontrol.nav.GoogleMapsNavNotificationParser
import com.mapcontrol.nav.GoogleMapsNavSnapshot

/**
 * Google Haritalar rehberlik bildirimini [NavigationState]'e çevirir (Android'e bağlı değil; JVM'de sınanır).
 *
 * Alan ayrıştırması FrostNav'ın [GoogleMapsNavNotificationParser]'ından gelir (cluster kartları da onu kullanır): alt metin
 * yoksa ya da hiçbir alanda mesafe / süre yoksa rota sayılmaz; ft / mi metreye çevrilir; "TVS:" / "ETA" etiketleri atılır.
 * Bilinen biçimler (araçta ve testlerde görülen):
 *  - başlık "500 ft - Head south-west", metin "Vega AVM - 12:26 ETA", alt metin "1 hr 34 min · 89 mi · 12:26 ETA"
 *  - başlık yok, metin "Salkım Söğüt Sk. yönüne doğru", alt metin "1 dk. · 260 m · TVS: 23:01"
 *  - başlık "200 m", metin "Turn left - Main", alt metin "5 min · 800 m"
 *
 * Manevra oku Google'da bitmap (büyük ikon) olarak gelir; okunmaz. Ok, talimat metnindeki sözcüklerden temkinli çıkarılır
 * (Türkçe / İngilizce); tanınmazsa [NavManeuver.UNKNOWN] (sönük düz ok, mesafe ve talimat yine gösterilir).
 */
object GoogleNavParse {

    /** Bildirim extras metinlerinden durum; rota yoksa null. */
    fun parse(title: String?, text: String?, subText: String?, nowMs: Long = 0L): NavigationState? =
        fromSnapshot(
            GoogleMapsNavNotificationParser.parseFields(clean(title), clean(text), clean(subText), false),
            nowMs,
        )

    fun fromSnapshot(snap: GoogleMapsNavSnapshot, nowMs: Long = 0L): NavigationState? {
        if (!snap.active) return null
        val distance = NavParse.parseDistanceOnly(snap.formatManeuverDistanceLine())
        val instruction = snap.formatManeuverDirectionLine()?.takeIf { it.any(Char::isLetter) && NavParse.parseDistanceOnly(it) == null }
        val minutes = NavParse.parseMinutes(snap.etaDurationLabel())
        val remaining = NavParse.findDistance(snap.etaDistanceLabel())
        val arrival = arrivalClock(snap.etaArrivalLabel())
        if (distance == null && instruction == null && minutes == null && remaining == null) return null
        val maneuver = maneuverFromInstruction(instruction) ?: NavManeuver.UNKNOWN
        val exit = if (maneuver == NavManeuver.ROUNDABOUT_ENTER) NavParse.parseExitNumber(instruction) else null
        return NavigationState(
            active = true,
            maneuver = maneuver,
            roundaboutExit = exit,
            distance = distance,
            street = instruction,
            arrivalTime = arrival,
            remainingDistance = remaining,
            remainingMinutes = minutes,
            sourcePackage = GoogleMapsNavNotificationParser.PACKAGE_GOOGLE_MAPS,
            updatedAtMs = nowMs,
        )
    }

    private fun clean(s: String?): String? = s?.trim()?.ifEmpty { null }

    /**
     * Rehberlik bildirimi mi: Google Haritalar aynı pakette trafik / işe gidiş gibi sayı içeren başka bildirimler de yayımlar;
     * yalnız navigasyon kategorisindeki ya da süren (ongoing / ön plan hizmeti) bildirim rota sayılır
     * (FrostNav'ın `GoogleMapsNavNotificationCoordinator.score()` ölçütleriyle aynı işaretler).
     */
    fun isGuidanceNotification(category: String?, flags: Int): Boolean =
        category == Notification.CATEGORY_NAVIGATION ||
            (flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE)) != 0

    private val CLOCK_12 = Regex("""(?iu)(?<!\d)(1[0-2]|0?[1-9])[:.]([0-5]\d)\s*([ap])\.?\s*m\b\.?""")

    /** Varış saati 24 saat "HH:mm": 12 saatlik "1:26 PM" → "13:26", "12:05 AM" → "00:05"; AM/PM yoksa [NavParse.findClock]. */
    fun arrivalClock(text: String?): String? {
        if (text == null) return null
        CLOCK_12.find(text)?.let { m ->
            val h12 = m.groupValues[1].toInt() % 12
            val h = if (m.groupValues[3].equals("p", ignoreCase = true)) h12 + 12 else h12
            return "${h.toString().padStart(2, '0')}:${m.groupValues[2]}"
        }
        return NavParse.findClock(text)
    }

    private val UTURN = Regex("""(?iu)u[\s-]?turn|u\s+dönüş|geri\s+dön""")
    private val ROUNDABOUT = Regex("""(?iu)roundabout|traffic\s+circle|rotary|döner\s+kavşa|dönel\s+kavşa|göbek""")
    private val FERRY = Regex("""(?iu)ferry|feribot|vapur""")
    private val ARRIVE = Regex("""(?iu)\barrive|destination|hedefiniz|varış\s+nokta|ulaştınız|varacaksınız""")
    private val HARD_LEFT = Regex("""(?iu)sharp\s+left|keskin\s+(bir\s+)?sola""")
    private val HARD_RIGHT = Regex("""(?iu)sharp\s+right|keskin\s+(bir\s+)?sağa""")
    private val SLIGHT_LEFT = Regex("""(?iu)slight\s+left|keep\s+left|bear\s+left|fork\s+left|hafif\s+sola|soldan\s+devam|sol\s+şeritte?\s+kal|sola\s+doğru\s+devam""")
    private val SLIGHT_RIGHT = Regex("""(?iu)slight\s+right|keep\s+right|bear\s+right|fork\s+right|hafif\s+sağa|sağdan\s+devam|sağ\s+şeritte?\s+kal|sağa\s+doğru\s+devam""")
    private val EXIT_LEFT = Regex("""(?iu)exit\s+(on\s+the\s+)?left|soldaki\s+çıkış|sol\s+çıkış""")
    private val EXIT_RIGHT = Regex("""(?iu)exit\s+(on\s+the\s+)?right|sağdaki\s+çıkış|sağ\s+çıkış""")
    private val LEFT = Regex("""(?iu)turn\s+left|sola\s+dön|sola\s+sap|sola\s+gir""")
    private val RIGHT = Regex("""(?iu)turn\s+right|sağa\s+dön|sağa\s+sap|sağa\s+gir""")
    private val STRAIGHT = Regex("""(?iu)\bhead\b|continue|go\s+straight|straight\s+ahead|düz\s+devam|devam\s+edin|ilerleyin|yönüne\s+doğru|doğru\s+ilerle""")

    /**
     * Talimat metninden manevra (sıra önemli: U dönüşü ve kavşak, "sola" sözcüğü içerse de önce gelir). Tanınmayan metin null.
     */
    fun maneuverFromInstruction(text: String?): NavManeuver? {
        if (text.isNullOrBlank()) return null
        return when {
            UTURN.containsMatchIn(text) -> NavManeuver.UTURN_LEFT
            ROUNDABOUT.containsMatchIn(text) -> NavManeuver.ROUNDABOUT_ENTER
            FERRY.containsMatchIn(text) -> NavManeuver.FERRY
            ARRIVE.containsMatchIn(text) -> NavManeuver.FINISH
            HARD_LEFT.containsMatchIn(text) -> NavManeuver.HARD_LEFT
            HARD_RIGHT.containsMatchIn(text) -> NavManeuver.HARD_RIGHT
            EXIT_LEFT.containsMatchIn(text) -> NavManeuver.EXIT_LEFT
            EXIT_RIGHT.containsMatchIn(text) -> NavManeuver.EXIT_RIGHT
            SLIGHT_LEFT.containsMatchIn(text) -> NavManeuver.SLIGHT_LEFT
            SLIGHT_RIGHT.containsMatchIn(text) -> NavManeuver.SLIGHT_RIGHT
            LEFT.containsMatchIn(text) -> NavManeuver.LEFT
            RIGHT.containsMatchIn(text) -> NavManeuver.RIGHT
            STRAIGHT.containsMatchIn(text) -> NavManeuver.STRAIGHT
            else -> null
        }
    }
}
