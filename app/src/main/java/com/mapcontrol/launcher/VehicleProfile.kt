package com.mapcontrol.launcher

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface

/** Alt çubuğun çizim biçimi: [CLASSIC] mevcut (Tiggo 7) çubuk, [FLAT] ince ve düz şerit (Tiggo 8). */
enum class BarStyle { CLASSIC, FLAT }

/**
 * Araç ekranı profili: Sahne biçimi (Tiggo 8'de dört panel), alt çubuk biçimi ve yüksekliği ekrana göre değişir.
 *  - [TIGGO7]: 1920×720 @160 dpi, iki panelli Sahne, mevcut alt çubuk (sabit [TIGGO7_BAR_DP] yükseklik).
 *  - [TIGGO8]: 15,6" 2560×1440 (16:9), dört panelli Sahne, düz ince şerit; yükseklik aracın kendi dock'una oranlı
 *    (1440 px içinde 128 px).
 *  - [PORTRAIT]: dikey tek parça ekran (Jaecoo J7/J8, 13,2" / 14,8"; hedef 1440×1920). Dört panelli Sahne 2×2 ızgarada,
 *    araç sahnesi üstte tam genişlik; düz alt çubuk sabit [PORTRAIT_BAR_DP]. Etkinlik dikey yönelimde tutulur.
 * Etkin profil [active] ile okunur; [resolve] başlatmada bir kez (ve her yeniden kurulumda) çağrılır, böylece açık
 * ekran çalışırken profil değişmez. Düzenler araç ekranı başına ayrı saklanır ([SahneStore.prefsName]).
 */
enum class VehicleProfile(val key: String, val barStyle: BarStyle) {
    TIGGO7("tiggo7", BarStyle.CLASSIC),
    TIGGO8("tiggo8", BarStyle.FLAT),
    PORTRAIT("portrait", BarStyle.FLAT);

    /** Dört panelli Sahne düzeni (Tiggo 8 ve dikey ekran); Tiggo 7 iki panellidir. */
    val quad: Boolean get() = this != TIGGO7

    /** Dikey ekran: etkinlik dikey yönelimde, Sahne üstte araç + 2×2 panel. */
    val portrait: Boolean get() = this == PORTRAIT

    /** Alt çubuk yüksekliği (dp). [screenHeightDp] uygulamanın gördüğü ekran yüksekliğidir (dp). */
    fun barHeightDp(screenHeightDp: Float): Int = when (this) {
        TIGGO7 -> TIGGO7_BAR_DP
        TIGGO8 -> Math.round(screenHeightDp * TIGGO8_DOCK_FRACTION).coerceIn(TIGGO8_BAR_MIN_DP, TIGGO8_BAR_MAX_DP)
        PORTRAIT -> PORTRAIT_BAR_DP
    }

    companion object {
        const val TIGGO7_BAR_DP = 104
        /** Tiggo 8'in kendi dock'u: 1440 px içinde 128 px. */
        const val TIGGO8_DOCK_FRACTION = 128f / 1440f
        const val TIGGO8_BAR_MIN_DP = 64
        const val TIGGO8_BAR_MAX_DP = 112
        /** Dikey ekranda alt çubuk: yükseklik oranı (Tiggo 8 dock'u) burada çubuğu gereksiz kalınlaştırır, sabit tutulur. */
        const val PORTRAIT_BAR_DP = 96

        /** Elle seçim: 0 = Otomatik, 1 = Tiggo 7, 2 = Tiggo 8, 3 = Dikey ([HomeScreenPrefs.vehicleProfileIndex]). */
        const val OVERRIDE_AUTO = 0
        const val OVERRIDE_MAX = 3

        /** Ekranın uzun kenarı bu pikselden uzunsa ya da en-boy oranı bundan küçükse Tiggo 8. */
        private const val T8_LONG_SIDE_PX = 2400
        private const val T8_MAX_ASPECT = 2.2f

        @Volatile
        private var current: VehicleProfile? = null

        /** Etkin profil. [resolve] hiç çağrılmadıysa (birim testleri) Tiggo 7. */
        val active: VehicleProfile get() = current ?: TIGGO7

        /** Demo/test: `--es launcher_profile tiggo7|tiggo8|portrait` (yalnızca debug ve demo açıkken); elle seçimi geçersiz kılar. */
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

        /**
         * Ekranın doğal (dönüşsüz, [Surface.ROTATION_0]) ölçüsünden profil (saf). Doğal hâli dikey olan ekran [PORTRAIT]'tır;
         * yalnız uzun kenarı Tiggo 8 sınırının ([T8_LONG_SIDE_PX]) altındaysa: dikey bildiren 2560×1440 Tiggo 8 ünitesi
         * Tiggo 8 kalır. Gerisi [detect].
         */
        fun detectNatural(naturalWidthPx: Int, naturalHeightPx: Int): VehicleProfile {
            if (naturalWidthPx > 0 && naturalHeightPx > naturalWidthPx && naturalHeightPx < T8_LONG_SIDE_PX) return PORTRAIT
            return detect(naturalWidthPx, naturalHeightPx)
        }

        /** Elle seçim ([OVERRIDE_AUTO] ya da 1/2/3) ile tespit edilen profil birleştirilir. */
        fun choose(override: Int, detected: VehicleProfile): VehicleProfile = when (override) {
            1 -> TIGGO7
            2 -> TIGGO8
            3 -> PORTRAIT
            else -> detected
        }

        /** Verilen ad "tiggo7"/"tiggo8"/"portrait" ise profil; aksi halde null. */
        fun parse(name: String?): VehicleProfile? = values().firstOrNull { it.key.equals(name?.trim(), ignoreCase = true) }

        /**
         * Gerçek ekrandan (pencere ya da yoğunluk uyarlamasından bağımsız) otomatik tespit. Ölçü ekranın doğal yönüne
         * çevrilir: etkinlik o an döndürülmüş olsa da (ör. eski yatay kilit) dikey ekran dikey görülür.
         */
        fun detectFromDisplay(context: Context): VehicleProfile {
            val p = Point()
            var rotation = Surface.ROTATION_0
            try {
                val dm = context.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val d = dm.getDisplay(Display.DEFAULT_DISPLAY)
                @Suppress("DEPRECATION")
                d?.getRealSize(p)
                rotation = d?.rotation ?: Surface.ROTATION_0
            } catch (_: RuntimeException) {
            }
            val turned = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
            return if (turned) detectNatural(p.y, p.x) else detectNatural(p.x, p.y)
        }

        /** Elle seçim ve demo isteği dahil, bu cihaz için profil (durum değiştirmez). */
        fun compute(context: Context): VehicleProfile =
            demoOverride ?: choose(HomeScreenPrefs.vehicleProfileIndex(context), detectFromDisplay(context))

        /** Etkin profili günceller; başlatmada ve her yeniden kurulumda çağrılır. */
        fun resolve(context: Context): VehicleProfile = compute(context).also { current = it }
    }
}
