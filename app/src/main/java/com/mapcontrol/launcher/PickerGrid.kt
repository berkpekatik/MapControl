package com.mapcontrol.launcher

/** Seçici ızgarasının yerleşimi (dp): sütun/satır sayısı, kutu ölçüsü ve hepsinin kaydırmadan sığıp sığmadığı. */
data class PickerFit(val cols: Int, val rows: Int, val tileW: Int, val tileH: Int, val fits: Boolean)

/**
 * Tam ekran yuva seçicisinin saf ızgara hesabı (7 Kasım kararı: kaydırma yok, tüm seçenekler tek ekranda, küçük kutular).
 * Önce genişliğe sığan en çok sütun ([MAX_COLS] ile sınırlı) alınır, satır sayısı bulunur, sonra sütunlar satırlara eşit
 * dağıtılır (14 öğe 8 sütunda 8+6 değil 7+7). Kutu yüksekliği kalan alana göre [maxTileH]..[minTileH] arasında; en küçükte
 * bile sığmıyorsa [PickerFit.fits] false (kutular en küçük ölçüde çizilir, ızgara kaydırılabilir olur).
 */
object PickerGrid {

    const val GAP_DP = 12
    const val MIN_TILE_W_DP = 220
    const val MAX_TILE_W_DP = 300
    const val MIN_TILE_H_DP = LauncherTheme.TOUCH_MIN_DP
    const val MAX_TILE_H_DP = 96
    const val MAX_COLS = 8

    fun fit(
        count: Int,
        widthDp: Int,
        heightDp: Int,
        gapDp: Int = GAP_DP,
        minTileW: Int = MIN_TILE_W_DP,
        maxTileW: Int = MAX_TILE_W_DP,
        minTileH: Int = MIN_TILE_H_DP,
        maxTileH: Int = MAX_TILE_H_DP,
        maxCols: Int = MAX_COLS,
    ): PickerFit {
        if (count <= 0) return PickerFit(0, 0, 0, 0, true)
        val widthCols = ((widthDp + gapDp) / (minTileW + gapDp)).coerceAtLeast(1)
        val capped = layout(count, minOf(count, widthCols, maxCols.coerceAtLeast(1)), widthDp, heightDp, gapDp, maxTileW, minTileH, maxTileH)
        // Sütun sınırıyla sığmıyorsa genişliğin el verdiği kadar sütun denenir (yine sığmazsa çağıran kaydırır).
        if (capped.fits || widthCols <= capped.cols) return capped
        return layout(count, minOf(count, widthCols), widthDp, heightDp, gapDp, maxTileW, minTileH, maxTileH)
    }

    private fun layout(count: Int, startCols: Int, widthDp: Int, heightDp: Int, gapDp: Int, maxTileW: Int, minTileH: Int, maxTileH: Int): PickerFit {
        var cols = startCols
        val rows = (count + cols - 1) / cols
        cols = (count + rows - 1) / rows
        val tileW = minOf(maxTileW, (widthDp - (cols - 1) * gapDp) / cols).coerceAtLeast(1)
        val freeH = (heightDp - (rows - 1) * gapDp) / rows
        val tileH = freeH.coerceIn(minTileH, maxOf(minTileH, maxTileH))
        val fits = freeH >= minTileH && cols * tileW + (cols - 1) * gapDp <= widthDp
        return PickerFit(cols, rows, tileW, tileH, fits)
    }
}
