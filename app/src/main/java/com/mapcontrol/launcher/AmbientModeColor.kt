package com.mapcontrol.launcher

/**
 * Sahne ortam ışığı için sürüş moduna bağlı yedek renk ve karar mantığı (saf; JVM'de sınanır).
 *
 * Kapaktan renk çıkmıyorsa (kapak yok, gri / siyah-beyaz kapak, radyo) ve müzik çalıyorsa ışık aracın sürüş modunu
 * taşır. Renkler [AmbientColor] çıktısıyla aynı yumuşaklıkta seçildi (ton sabit, parlaklık ~0,9, doygunluk 0,25..0,8);
 * görünüm aynı alfa ile ([SahneStage] AMBIENT_ALPHA_*) çizdiği için kapak renkleriyle aynı yumuşaklıkta görünür.
 * Mod değerleri [DriveModes] tablosundan gelir.
 */
internal object AmbientModeColor {
    /** Eko: sakin yeşil. */
    const val ECO_COLOR = 0xFF50E69B.toInt()
    /** Normal: yumuşak mavi (aynı zamanda bilinmeyen mod için nötr renk). */
    const val NORMAL_COLOR = 0xFF5C95E6.toInt()
    /** Spor: kırmızı. */
    const val SPORT_COLOR = 0xFFE63D2E.toInt()
    /** Kar: soğuk buz; saf beyaz değil, hafif mavi tonlu (parlama yapmaz). */
    const val SNOW_COLOR = 0xFFB8DEF2.toInt()
    /** Çamur: toprak kahvesi (koyu kahve ışık vermediği için biraz açık). */
    const val MUD_COLOR = 0xFFB5764A.toInt()
    /** Arazi: kehribar / turuncu. */
    const val OFFROAD_COLOR = 0xFFF29A1F.toInt()
    /** Kum: sıcak kum sarısı. */
    const val SAND_COLOR = 0xFFE9C46A.toInt()

    /**
     * Moda özgü ışık rengi. Bilinmeyen / tanımsız değer (6, okunamayan) Normal'in mavisine düşer: müzik çalarken ışık
     * sönük kalmasın, ama yanlış bir mod (ör. kırmızı Spor) da ima edilmesin.
     */
    @JvmStatic
    fun forMode(mode: Int): Int = when (mode) {
        DriveModes.ECO -> ECO_COLOR
        DriveModes.SPORT -> SPORT_COLOR
        DriveModes.SNOW -> SNOW_COLOR
        DriveModes.MUD -> MUD_COLOR
        DriveModes.OFFROAD -> OFFROAD_COLOR
        DriveModes.SAND -> SAND_COLOR
        else -> NORMAL_COLOR
    }

    /**
     * Ortam ışığı rengi: kapak rengi varsa o kazanır (çalma durumundan bağımsız, eskisi gibi); yoksa yalnızca müzik
     * çalarken sürüş modu rengi; durmuş / duraklatılmışsa ve kapak rengi de yoksa null (ışık yok).
     */
    @JvmStatic
    fun decide(coverColor: Int?, playing: Boolean, driveMode: Int): Int? = when {
        coverColor != null -> coverColor
        playing -> forMode(driveMode)
        else -> null
    }
}
