package com.mapcontrol.launcher

import org.json.JSONObject

/**
 * Telefonla paylaşılan Ev / İş konumu belgesi (`GET` / `PUT /nav/places`):
 *
 * ```
 * {"home":{"lat":41.012345,"lon":29.012345,"label":"Kadıköy"},"work":null}
 * ```
 *
 * `GET` iki yeri de verir (ayarsız yer `null`). `PUT`'ta belgede olan yer yazılır, `null` olan silinir, olmayana dokunulmaz.
 * Bilinmeyen anahtar (daha yeni telefonun yeri) yok sayılır; tanınan yerin değeri geçersizse hiçbiri yazılmaz.
 * `label` isteğe bağlıdır (kartın alt satırı); boşsa yazılmaz. Android'e bağlı değildir (JVM testi).
 */
object NavPlacesDoc {
    const val MAX_LABEL = 60
    val PLACES = listOf(NavPlaces.HOME, NavPlaces.WORK)

    data class Place(val lat: Double, val lon: Double, val label: String?)

    /** `PUT` gövdesi: yer → yazılacak konum (silmek için null). Geçersizse null ([errors] alan adlarını alır). */
    @JvmStatic
    fun parse(root: JSONObject, errors: MutableList<String>): Map<String, Place?>? {
        val out = LinkedHashMap<String, Place?>()
        for (name in PLACES) {
            if (!root.has(name)) continue
            val raw = root.opt(name)
            if (raw == null || raw == JSONObject.NULL) {
                out[name] = null
                continue
            }
            val place = (raw as? JSONObject)?.let { place(it) }
            if (place == null) errors += name else out[name] = place
        }
        return if (errors.isEmpty()) out else null
    }

    private fun place(o: JSONObject): Place? {
        val lat = number(o.opt("lat")) ?: return null
        val lon = number(o.opt("lon")) ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val label = when (val l = o.opt("label")) {
            null, JSONObject.NULL -> null
            is String -> l.trim().takeIf { it.length <= MAX_LABEL && it.none { c -> c < ' ' } } ?: return null
            else -> return null
        }
        return Place(lat, lon, label?.ifEmpty { null })
    }

    /** Yalnızca gerçek sayı; "41.0" yazısı, NaN ve sonsuz kabul edilmez. */
    private fun number(v: Any?): Double? =
        (v as? Number)?.toDouble()?.takeIf { it.isFinite() }

    @JvmStatic
    fun toJson(places: Map<String, Place?>): JSONObject = JSONObject().apply {
        for (name in PLACES) {
            val p = places[name]
            put(name, if (p == null) JSONObject.NULL else JSONObject().apply {
                put("lat", p.lat)
                put("lon", p.lon)
                if (p.label != null) put("label", p.label)
            })
        }
    }
}
