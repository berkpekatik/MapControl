package com.mapcontrol.launcher

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display

/** Alt çubuğun çizim biçimi: [CLASSIC] mevcut (Tiggo 7) çubuk, [FLAT] ince ve düz şerit (Tiggo 8). */
enum class BarStyle { CLASSIC, FLAT }

/**
 * Araç ekranı profili: Sahne biçimi (Tiggo 8'de dört panel), alt çubuk biçimi ve yüksekliği ekrana göre değişir.
 *  - [TIGGO7]: 1920×720 @160 dpi, iki panelli Sahne, mevcut alt çubuk (sabit [TIGGO7_BAR_DP] yükseklik).
 *  - [TIGGO8]: 15,6" 2560×1440 (16:9), dört panelli Sahne, düz ince şerit; yükseklik aracın kendi dock'una oranlı
 *    (1440 px içinde 128 px).
 * Etkin profil [active] ile okunur; [resolve] başlatmada bir kez (ve her yeniden kurulumda) çağrılır, böylece açık
 * ekran çalışırken profil değişmez. Düzenler araç ekranı başına ayrı saklanır ([SahneStore.prefsName]).
 */
enum class VehicleProfile(val key: String, val barStyle: BarStyle) {
    TIGGO7("tiggo7", BarStyle.CLASSIC),
    TIGGO8("tiggo8", BarStyle.FLAT);

    /** Alt çubuk yüksekliği (dp). [screenHeightDp] uygulamanın gördüğü ekran yüksekliğidir (dp). */
    fun barHeightDp(screenHeightDp: Float): Int = when (this) {
        TIGGO7 -> TIGGO7_BAR_DP
        TIGGO8 -> Math.round(screenHeightDp * TIGGO8_DOCK_FRACTION).coerceIn(TIGGO8_BAR_MIN_DP, TIGGO8_BAR_MAX_DP)
    }

    companion object {
        const val TIGGO7_BAR_DP = 104
        /** Tiggo 8'in kendi dock'u: 1440 px içinde 128 px. */
        const val TIGGO8_DOCK_FRACTION = 128f / 1440f
        const val TIGGO8_BAR_MIN_DP = 64
        const val TIGGO8_BAR_MAX_DP = 112

        /** Elle seçim: 0 = Otomatik, 1 = Tiggo 7, 2 = Tiggo 8 ([HomeScreenPrefs.vehicleProfileIndex]). */
        const val OVERRIDE_AUTO = 0

        /** Ekranın uzun kenarı bu pikselden uzunsa ya da en-boy oranı bundan küçükse Tiggo 8. */
        private const val T8_LONG_SIDE_PX = 2400
        private const val T8_MAX_ASPECT = 2.2f

        @Volatile
        private var current: VehicleProfile? = null

        /** Etkin profil. [resolve] hiç çağrılmadıysa (birim testleri) Tiggo 7. */
        val active: VehicleProfile get() = current ?: TIGGO7

        /** Demo/test: `--es launcher_profile tiggo7|tiggo8` (yalnızca debug ve demo açıkken); elle seçimi geçersiz kılar. */
        @Volatile
        var demoOverride: VehicleProfile? = null

        const val EXTRA_PROFILE = "launcher_profile"

        /**
         * Ham ekran ölçüsünden profil (saf; birim testlenir). Yalnızca ekran: model adları güvenilmez,
         * bizim Tiggo 7'nin `ro.product.name` değeri de `g7ph_t22_int`.
         */
        fun detect(widthPx: Int, heightPx: Int): VehicleProfile {
            if (widthPx <= 0 || heightPx <= 0) return TIGGO7
            val long = maxOf(widthPx, heightPx)
            val short = minOf(widthPx, heightPx)
            return if (long >= T8_LONG_SIDE_PX || long.toFloat() / short < T8_MAX_ASPECT) TIGGO8 else TIGGO7
        }

        /** Elle seçim ([OVERRIDE_AUTO] ya da 1/2) ile tespit edilen profil birleştirilir. */
        fun choose(override: Int, detected: VehicleProfile): VehicleProfile = when (override) {
            1 -> TIGGO7
            2 -> TIGGO8
            else -> detected
        }

        /** Verilen ad "tiggo7"/"tiggo8" ise profil; aksi halde null. */
        fun parse(name: String?): VehicleProfile? = values().firstOrNull { it.key.equals(name?.trim(), ignoreCase = true) }

        /** Gerçek ekrandan (pencere ya da yoğunluk uyarlamasından bağımsız) otomatik tespit. */
        fun detectFromDisplay(context: Context): VehicleProfile {
            val p = Point()
            try {
                val dm = context.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                @Suppress("DEPRECATION")
                dm.getDisplay(Display.DEFAULT_DISPLAY)?.getRealSize(p)
            } catch (_: RuntimeException) {
            }
            return detect(p.x, p.y)
        }

        /** Elle seçim ve demo isteği dahil, bu cihaz için profil (durum değiştirmez). */
        fun compute(context: Context): VehicleProfile =
            demoOverride ?: choose(HomeScreenPrefs.vehicleProfileIndex(context), detectFromDisplay(context))

        /** Etkin profili günceller; başlatmada ve her yeniden kurulumda çağrılır. */
        fun resolve(context: Context): VehicleProfile = compute(context).also { current = it }
    }
}
