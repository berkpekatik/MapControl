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

    data class Geometry(val stageH: Int, val panelW: Int, val panelH: Int, val gapV: Int, val columns: Int) {
        fun panelLeft(i: Int, w: Int): Int = (i % columns) * (w - panelW) / (columns - 1)

        /** [i] panelinin üst kenarı: ilk satır sahnenin altında, ikinci satır bir boşluk aşağıda. */
        fun panelTop(i: Int): Int = stageH + gapV + (i / columns) * (panelH + gapV)
    }

    /**
     * [w]×[h] orta alanda (piksel) geometri. [stripH] şerit, [gapH]/[gapV] yatay/dikey boşluk, [stageMinPx] sahnenin
     * en küçük yüksekliği (piksel).
     */
    fun geometry(w: Int, h: Int, stripH: Int, gapH: Int, gapV: Int, stageMinPx: Int,
                 stageFraction: Float = STAGE_FRACTION, singleRow: Boolean = false): Geometry {
        val band = (h - stripH - gapV).coerceAtLeast(0)
        val stageH = maxOf(stageMinPx, (band * stageFraction).roundToInt()).coerceAtMost(band)
        val columns = if (singleRow) 4 else 2
        val rows = 4 / columns
        val panelH = ((band - stageH - rows * gapV) / rows).coerceAtLeast(0)
        val panelW = ((w - (columns - 1) * gapH) / columns).coerceAtLeast(0)
        return Geometry(stageH, panelW, panelH, gapV, columns)
    }

    /**
     * Şerit yokken aracın sahnenin tam dikey ortasına inmesi için gereken kayma (piksel, >= 0). [boxTop]/[boxH] aracın şerit
     * payına göre yerleşmiş kutusu, [stageH] sahne yüksekliği; araç sahnenin altında en az [bottomGap] bırakır.
     */
    fun centerDrop(stageH: Float, boxTop: Float, boxH: Float, bottomGap: Float): Float {
        val room = stageH - bottomGap - (boxTop + boxH)
        return ((stageH - boxH) / 2f - boxTop).coerceIn(0f, maxOf(0f, room))
    }
}
