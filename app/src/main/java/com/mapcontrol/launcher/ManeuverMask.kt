package com.mapcontrol.launcher

/**
 * Ok resmini sabit boyutlu siyah-beyaz maskeye çevirir ve maskeleri karşılaştırır (Android'e bağlı değil; JVM'de sınanır).
 *
 * Google Haritalar manevra okunu bildirimde resim (büyük ikon) olarak verir. Resim, Haritalar'ın kendi `maneuver_*`
 * çizimlerinden üretilen maskelerle karşılaştırılır: mürekkep (ok) pikselleri bulunur, çevresindeki boşluk kırpılır,
 * kalan alan [SIZE]x[SIZE] ızgaraya ölçeklenir. Böylece renk, boyut ve kenar boşluğu farkı sonucu etkilemez.
 */
internal object ManeuverMask {

    const val SIZE = 32

    /** Kırpılan mürekkep alanı bundan küçükse (piksel) resim ok sayılmaz. */
    private const val MIN_INK_SIDE = 4

    /**
     * ARGB piksellerinden maske; mürekkep yoksa null. Köşe pikseli saydamsa mürekkep = opak piksel; köşe opaksa
     * (düz renk zemin) mürekkep = zeminden belirgin farklı renk.
     */
    fun fromArgb(px: IntArray, w: Int, h: Int): BooleanArray? {
        if (w <= 0 || h <= 0 || px.size < w * h) return null
        val corner = px[0]
        val transparentBg = (corner ushr 24) < 128
        val ink = BooleanArray(w * h) { i ->
            val c = px[i]
            if ((c ushr 24) < 128) false
            else if (transparentBg) true
            else colorDistance(c, corner) > 96
        }
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (ink[y * w + x]) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return null
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        if (bw < MIN_INK_SIDE && bh < MIN_INK_SIDE) return null
        // Kare çerçeve: ince oklar (düz) şişmesin; kısa kenar ortalanır.
        val side = maxOf(bw, bh)
        val ox = minX - (side - bw) / 2
        val oy = minY - (side - bh) / 2
        val out = BooleanArray(SIZE * SIZE)
        for (ty in 0 until SIZE) {
            val y0 = oy + ty * side / SIZE
            val y1 = maxOf(y0 + 1, oy + (ty + 1) * side / SIZE)
            for (tx in 0 until SIZE) {
                val x0 = ox + tx * side / SIZE
                val x1 = maxOf(x0 + 1, ox + (tx + 1) * side / SIZE)
                var on = 0
                var all = 0
                for (y in y0 until y1) for (x in x0 until x1) {
                    all++
                    if (x in 0 until w && y in 0 until h && ink[y * w + x]) on++
                }
                out[ty * SIZE + tx] = on * 2 >= all
            }
        }
        return out
    }

    private fun colorDistance(a: Int, b: Int): Int =
        Math.abs(((a shr 16) and 0xff) - ((b shr 16) and 0xff)) +
            Math.abs(((a shr 8) and 0xff) - ((b shr 8) and 0xff)) +
            Math.abs((a and 0xff) - (b and 0xff))

    /** Kesişim / birleşim (0..1). */
    fun similarity(a: BooleanArray, b: BooleanArray): Float {
        var inter = 0
        var union = 0
        for (i in a.indices) {
            if (a[i] && b[i]) inter++
            if (a[i] || b[i]) union++
        }
        return if (union == 0) 0f else inter.toFloat() / union
    }

    class Match(val name: String, val maneuver: NavManeuver, val score: Float, val runnerUp: Float)

    class Template(val name: String, val maneuver: NavManeuver, val mask: BooleanArray)

    /**
     * En benzer şablon; skor [minScore] altındaysa ya da farklı manevralı ikinci en iyi şablon [minMargin]'den yakınsa
     * null (emin değilse ok uydurulmaz; metne ya da "bilinmiyor"a düşülür).
     */
    fun match(sample: BooleanArray, templates: List<Template>, minScore: Float = 0.6f, minMargin: Float = 0.05f): Match? {
        var best: Template? = null
        var bestScore = -1f
        val scores = templates.map { it to similarity(sample, it.mask) }
        for ((t, s) in scores) if (s > bestScore) { best = t; bestScore = s }
        val b = best ?: return null
        val runnerUp = scores.filter { it.first.maneuver != b.maneuver }.maxOfOrNull { it.second } ?: 0f
        if (bestScore < minScore || bestScore - runnerUp < minMargin) return null
        return Match(b.name, b.maneuver, bestScore, runnerUp)
    }

    /** Google Haritalar çizim adından manevra (`maneuver_turn_normal_left` → LEFT); tanınmayan ad null. */
    fun maneuverFromGoogleName(name: String): NavManeuver? {
        val n = name.removePrefix("maneuver_")
        val left = n.endsWith("_left")
        val right = n.endsWith("_right")
        fun side(l: NavManeuver, r: NavManeuver): NavManeuver? = if (left) l else if (right) r else null
        return when {
            n.startsWith("destination") -> NavManeuver.FINISH
            // Haritalar'da üç birleşme çizimi birebir aynı resim: yönü resimden bilinemez, düz sayılır.
            n == "straight" || n == "name_change" || n == "depart" || n.startsWith("merge") -> NavManeuver.STRAIGHT
            n.startsWith("roundabout_exit") -> NavManeuver.ROUNDABOUT_EXIT
            n.startsWith("roundabout") -> NavManeuver.ROUNDABOUT_ENTER
            n.contains("u_turn") -> side(NavManeuver.UTURN_LEFT, NavManeuver.UTURN_RIGHT)
            n.contains("sharp") -> side(NavManeuver.HARD_LEFT, NavManeuver.HARD_RIGHT)
            // Hafif / şeritte kal çıkışları "hafif dön" ile aynı resim: aynı oka bağlanır, yoksa eşleşme kararsız kalır.
            n.contains("slight") || n.contains("keep") || n.startsWith("fork") ->
                side(NavManeuver.SLIGHT_LEFT, NavManeuver.SLIGHT_RIGHT)
            n.startsWith("off_ramp") -> side(NavManeuver.EXIT_LEFT, NavManeuver.EXIT_RIGHT)
            n.contains("normal") -> side(NavManeuver.LEFT, NavManeuver.RIGHT)
            else -> null
        }
    }
}
