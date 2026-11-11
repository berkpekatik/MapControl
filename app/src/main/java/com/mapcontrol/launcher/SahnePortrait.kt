package com.mapcontrol.launcher

import kotlin.math.roundToInt

/**
 * Dikey ekranda ([VehicleProfile.PORTRAIT], Jaecoo) Sahne orta alanının yerleşimi (saf; JVM'de sınanır).
 *
 * Yukarıdan aşağı: araç sahnesi tam genişlikte (bandın [STAGE_FRACTION]'ı, en az [STAGE_MIN_DP]) · 2×2 panel ızgarası
 * (dört panelli biçimin yuvaları: 0 sol üst, 1 sağ üst, 2 sol alt, 3 sağ alt) · altta şerit. Yatay biçimdeki gibi
 * şerit ile bant arasında bir dikey boşluk vardır.
 *
 * 1440×1920 ekran, [LauncherScale] sonrası yaklaşık 1273×1700 dp (ya da 160 dpi'da 1440×1920 dp): paneller ~600×400 dp
 * çıkar, yani Tiggo 7 panelinin (440×378) üstünde; içerik küçültülmez.
 */
internal object SahnePortrait {

    const val STAGE_FRACTION = 0.38f
    const val STAGE_MIN_DP = 320

    data class Geometry(val stageH: Int, val panelW: Int, val panelH: Int, val gapV: Int) {
        /** [i] panelinin sol kenarı ([w] orta alan genişliği): çift indeksler solda, tekler sağda. */
        fun panelLeft(i: Int, w: Int): Int = if (i % 2 == 0) 0 else w - panelW

        /** [i] panelinin üst kenarı: ilk satır sahnenin altında, ikinci satır bir boşluk aşağıda. */
        fun panelTop(i: Int): Int = stageH + gapV + (i / 2) * (panelH + gapV)
    }

    /**
     * [w]×[h] orta alanda (piksel) geometri. [stripH] şerit, [gapH]/[gapV] yatay/dikey boşluk, [stageMinPx] sahnenin
     * en küçük yüksekliği (piksel).
     */
    fun geometry(w: Int, h: Int, stripH: Int, gapH: Int, gapV: Int, stageMinPx: Int): Geometry {
        val band = (h - stripH - gapV).coerceAtLeast(0)
        val stageH = maxOf(stageMinPx, (band * STAGE_FRACTION).roundToInt()).coerceAtMost(band)
        val rows = (band - stageH - gapV).coerceAtLeast(0)
        val panelH = ((rows - gapV) / 2).coerceAtLeast(0)
        val panelW = ((w - gapH) / 2).coerceAtLeast(0)
        return Geometry(stageH, panelW, panelH, gapV)
    }
}
