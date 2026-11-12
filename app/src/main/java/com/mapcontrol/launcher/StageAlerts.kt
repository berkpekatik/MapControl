package com.mapcontrol.launcher

/**
 * Sahne köşe notlarının görünürlük kuralları (saf, JVM'de sınanır). İlke (4 Kasım kararı): her şey yolundaysa sakin ekran;
 * araç sağlığı ve hava kalitesi köşeleri normalde gizli, yalnızca bir şey olunca çıkar.
 */
internal object StageCorners {

    /** Bakım "yaklaştı" sayılan kalan mesafe (km); geçmiş bakım (0 ve altı) her zaman sayılır. */
    const val SERVICE_NEAR_KM = HealthMath.SOON_KM

    /**
     * Kötü hava eşiği: kabin içi PM2.5 (µg/m³) bu değer ve üstündeyse hava köşesi çıkar. Hava kutucuğunun "Kötü" derecesiyle
     * aynı ([AirGrade.POOR]): uyarı renginde çıkan not "Orta" yazmasın.
     */
    const val AIR_BAD_PM25 = AirGrade.MODERATE_MAX + 1

    /** Bakım yakın: kalan <= [SERVICE_NEAR_KM] km ya da bakım zamanı geçmiş (km ya da gün 0'a inmiş). */
    fun serviceNear(serviceKm: Int?, serviceDays: Int?): Boolean =
        (serviceKm != null && serviceKm <= SERVICE_NEAR_KM) || HealthMath.serviceOverdue(serviceKm, serviceDays)

    /** Hava köşesi: yalnızca kabin içi PM2.5 >= [AIR_BAD_PM25] iken. */
    fun airShown(pm25In: Int?): Boolean = pm25In != null && pm25In >= AIR_BAD_PM25
}

/** Sahne aracının yeri ve boyu (saf, JVM'de sınanır); bkz. [centered]. */
internal object StageFit {

    /** Sahne pikseli dikdörtgen. */
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /**
     * Dikey araç: [upright] ekranda görünen dar ve uzun kutu. Araç görünümü yatay çizildiği için yatay gövde kutusu aynı
     * merkezde, boyu [upright] yüksekliği ve eni boy × [aspect] (yükseklik/genişlik) olur; sonra merkez etrafında döndürülür.
     */
    fun lyingBody(upright: Box, aspect: Float): Box {
        val cx = (upright.left + upright.right) / 2f
        val cy = (upright.top + upright.bottom) / 2f
        val len = upright.height
        val wid = len * aspect
        return Box(cx - len / 2f, cy - wid / 2f, cx + len / 2f, cy + wid / 2f)
    }

    /** [box] aynı merkezde [scale] katı (model ölçeği: tuval kutusu yerleşim çerçevesinden büyüktür). Ölçek 1 ise kutunun kendisi. */
    fun scaled(box: Box, scale: Float): Box {
        if (scale == 1f) return box
        val cx = (box.left + box.right) / 2f
        val cy = (box.top + box.bottom) / 2f
        val hw = box.width * scale / 2f
        val hh = box.height * scale / 2f
        return Box(cx - hw, cy - hh, cx + hw, cy + hh)
    }

    /**
     * Tuvalde [canvasFrac] konumundaki (tuval genişliğine oran; negatif = tuvalin solunda) bir noktanın, tuval aynı merkezde
     * [scale] katı büyütüldüğünde yerleşim çerçevesinin solundan taşması (çerçeve genişliğine oran). Ölçek 1 ise eski hesabın
     * kendisi (-[canvasFrac], kırpılmadan); büyütülmüş tuvalde taşmıyorsa 0.
     */
    fun frameOverflowLeft(canvasFrac: Float, scale: Float): Float =
        if (scale == 1f) -canvasFrac else (-(0.5f + (canvasFrac - 0.5f) * scale)).coerceAtLeast(0f)

    /**
     * Tuvalde [canvasFrac] konumundaki noktanın (burun), tuval [scale] katı büyütüldüğünde yerleşim çerçevesinin sağından taşması
     * (çerçeve genişliğine oran). Ölçek 1 ise 0: çerçeve tuvalin kendisidir, eski hesaba hiçbir şey eklenmez.
     */
    fun frameOverflowRight(canvasFrac: Float, scale: Float): Float =
        if (scale == 1f) 0f else ((canvasFrac - 0.5f) * scale - 0.5f).coerceAtLeast(0f)

    /**
     * Sahnenin tam ortasındaki araç (§10/§11): yatayda [centerX]'e sabit, en büyük genişlik [maxW]'den [minW]'ye aranır.
     * Üstteki engellerle (omuzlardaki menzil ve kilit) yatayda çakışırsa araç onların altına iner; çakışma hesabında aracın
     * yuvarlak köşeleri için her iki yandan genişliğin [cornerInset] oranı boş sayılır (omuz köşeye biraz girebilir). Dikeyde
     * kalan boşluğun ortasına oturur. Hiçbir genişlik sığmazsa [minW] en üstte döner.
     */
    fun centered(
        centerX: Float, minTop: Float, maxBottom: Float, maxW: Float, minW: Float, aspect: Float, gap: Float,
        above: List<Box>, cornerInset: Float = 0f, step: Float = 2f,
    ): Box {
        var w = maxW
        while (w >= minW) {
            val h = w * aspect
            val x = centerX - w / 2f
            val inset = w * cornerInset
            val top = above.filter { it.left < x + w - inset + gap && it.right > x + inset - gap }
                .maxOfOrNull { it.bottom + gap }?.coerceAtLeast(minTop) ?: minTop
            if (top + h <= maxBottom) {
                val y = top + (maxBottom - top - h) / 2f
                return Box(x, y, x + w, y + h)
            }
            w -= step
        }
        val x = centerX - minW / 2f
        return Box(x, minTop, x + minW, minTop + minW * aspect)
    }

    /**
     * Uzun sahne (Tiggo 8, 6 Kasım §13): sahne yüksekliği genişliğinin [TALL_RATIO] katından büyükse araç yatayda büyür ve
     * omuz satırı ile şerit arasında dikeyde ortalanır. Tiggo 7'nin bandı (~0,41) bunun çok altındadır, orada hiçbir şey değişmez.
     */
    const val TALL_RATIO = 0.6f

    fun isTall(stageW: Int, stageH: Int): Boolean = stageW > 0 && stageH > stageW * TALL_RATIO

    /**
     * Uzun sahnede aracın en büyük genişliği: normal üst sınırın [TALL_GROWTH] katı, ama açık bagaj kapağı dahil
     * (gövdenin solundan [trunkFrac] oranında taşar) aracın tamamı sahnenin iki yanında [margin] pay bırakarak sığmalı;
     * kapak açılınca araç bu kadar sağa kayabilir ([tallTrunkShift]).
     */
    const val TALL_GROWTH = 1.4f

    fun tallMaxWidth(stageW: Float, normalMaxW: Float, margin: Float, trunkFrac: Float): Float =
        minOf(normalMaxW * TALL_GROWTH, (stageW - 2 * margin) / (1f + trunkFrac)).coerceAtLeast(0f)

    /**
     * Uzun sahnede bagaj açıkken kayma: kapağın sol ucu sahnenin solunda en az [margin] kalacak kadar (sağa taşmadan).
     * [noseFrac]: burnun kutunun sağından taşması (genişliğe oran; yalnız ölçekli modelde, bkz. [frameOverflowRight]).
     */
    fun tallTrunkShift(stageW: Float, car: Box, margin: Float, trunkFrac: Float, noseFrac: Float = 0f): Float {
        val trunkLeft = car.left - car.width * trunkFrac
        val need = margin - trunkLeft
        val room = stageW - margin - car.right - car.width * noseFrac
        return need.coerceAtMost(room).coerceAtLeast(0f)
    }
}

/**
 * "+N" kuralı (saf, JVM'de sınanır): Sahne uyarı şeridi ve mini gösterge en çok [MAX_VISIBLE] maddeyi adlandırır, fazlası tek
 * bir "+N" olur. §10 (4 Kasım, son karar): araç çiziminde görünen şeyler (kapı, bagaj, kaput, cam, cam tavan) şeride yazılmaz;
 * şerit yalnızca arabada görünmeyenler içindir ([MiniAlerts]: düşük lastik, bakım, yakıt az, kötü hava).
 */
internal object StageNotices {

    /** Aynı anda adlandırılan en çok madde; fazlası tek bir "+N" olur. */
    const val MAX_VISIBLE = 2

    /**
     * Görünen maddeler ve "+N" sayısı. [fit]: genişliğe sığan en çok madde (çağıran ölçer). Hiç sığmazsa ilk madde yine
     * gösterilir (en önemli uyarı kaybolmaz).
     */
    fun <T> visible(all: List<T>, fit: Int = MAX_VISIBLE): Pair<List<T>, Int> {
        val n = maxOf(1, minOf(fit, MAX_VISIBLE))
        if (all.size <= n) return all to 0
        return all.take(n) to all.size - n
    }
}

/** En yakın benzin istasyonu araması: önce Yandex Haritalar, açılmazsa genel harita (geo:) araması. */
internal object NearestStation {
    /** Yandex arama terimi: "АЗС" (benzin istasyonu kategorisi; Türkçe arayüzde de tanınıyor). */
    const val YANDEX_QUERY = "АЗС"
    const val GEO_QUERY = "benzin istasyonu"

    private fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun yandexUri(): String = "yandexmaps://maps.yandex.ru/?text=" + encode(YANDEX_QUERY)

    fun geoUri(): String = "geo:0,0?q=" + encode(GEO_QUERY)
}
