package com.mapcontrol.launcher

/**
 * Yolculuk kartının içerik kararı (saf, JVM'de sınanır). Öncü sayı hep bugünkü yolculuk içeriğidir ("Sıfırlamadan beri"
 * km); sürüşte de değişmez. Anlık tüketim bu kartta gösterilmez (7 Kasım kararı): isteyen ayrı tüketim kutucuğunu ekler.
 */
internal object TripHero {
    enum class Content { NO_DATA, TRIP }

    /** Gösterilecek içerik: araç verisi yoksa ya da yolculuk km'si okunamıyorsa NO_DATA; değilse TRIP. */
    fun content(connected: Boolean, tripKm: Double?): Content =
        if (!connected || tripKm == null) Content.NO_DATA else Content.TRIP
}
