package com.mapcontrol.launcher

import android.content.Context
import android.content.res.Configuration

/**
 * Ekran ölçeği uyarlaması. Launcher 1920x720 @160 dpi (1920 dp genişlik) için tasarlandı. Başka bir ünitede
 * (ör. yeni Tiggo 8, 2560x1440) yoğunluk ne olursa olsun yatay genişlik [MIN_WIDTH_DP]..[MAX_WIDTH_DP] aralığına
 * getirilir: dar gelirse yazılar kesilir, geniş gelirse kartlar boş kalır. Aralıktaysa (bizim araç) hiçbir şey değişmez.
 */
internal object LauncherScale {

    const val MIN_WIDTH_DP = 1700
    const val MAX_WIDTH_DP = 1920

    /** Değiştirilecek dpi; ölçek zaten uygunsa null. [widthPx] uzun kenar kabul edilir. */
    fun targetDensityDpi(widthPx: Int, heightPx: Int, densityDpi: Int): Int? {
        if (widthPx <= 0 || densityDpi <= 0) return null
        val longPx = maxOf(widthPx, heightPx)
        val widthDp = longPx * 160f / densityDpi
        if (widthDp >= MIN_WIDTH_DP && widthDp <= MAX_WIDTH_DP + 40) return null
        val targetDp = widthDp.coerceIn(MIN_WIDTH_DP.toFloat(), MAX_WIDTH_DP.toFloat())
        return Math.round(longPx * 160f / targetDp)
    }

    /** Etkinliğin bağlamını uyarlanmış yoğunlukla sarar; gerekmezse olduğu gibi döner. */
    fun wrap(base: Context): Context {
        val dm = base.resources.displayMetrics
        val dpi = targetDensityDpi(dm.widthPixels, dm.heightPixels, base.resources.configuration.densityDpi) ?: return base
        val cfg = Configuration(base.resources.configuration)
        val scale = cfg.densityDpi.toFloat() / dpi
        cfg.densityDpi = dpi
        // dp ölçüleri yeni yoğunluğa göre: kaynak niteleyicileri ve tanılama doğru değeri görsün.
        cfg.screenWidthDp = Math.round(cfg.screenWidthDp * scale)
        cfg.screenHeightDp = Math.round(cfg.screenHeightDp * scale)
        cfg.smallestScreenWidthDp = Math.round(cfg.smallestScreenWidthDp * scale)
        return base.createConfigurationContext(cfg)
    }
}
