package com.mapcontrol.launcher

import android.content.Context
import androidx.annotation.DrawableRes
import com.mapcontrol.R
import com.mapcontrol.launcher.CarAnimSpec.Box
import com.mapcontrol.launcher.CarAnimSpec.Glass
import com.mapcontrol.launcher.CarAnimSpec.Kind
import com.mapcontrol.launcher.CarAnimSpec.Part

/**
 * Bir araç modelinin Sahne görsel takımına ait ölçüler (saf veri; birimler base pikseli, 766 × 356 tuval, burun sağda).
 * [CarAnimSpec] modele bağlı her değeri etkin modelin ([CarModel.active]) ölçülerinden okur. Kutu dizileri: sol, üst, sağ, alt.
 */
class CarGeometry internal constructor(
    val parts: Map<CarPart, Part>,
    internal val windows: Map<BodyPart, Glass>,
    val sunroof: Glass,
    val roofX0: Float,
    val roofX1: Float,
    val roofY0: Float,
    val roofY1: Float,
    val roofSplit: Float,
    val roofFrontX0: Float,
    val roofGlassTravel: Float,
    val tireX: FloatArray,
    val tireEdge: FloatArray,
    val tireL: Float,
    val tireW: Float,
    val tireOut: Float,
    val fuelCap: Box,
    val hoodCx: Float,
    val hoodCy: Float,
    val hoodRx: Float,
    val hoodRy: Float,
    /** Far huzmeleri ve arkadaki zemin ışıkları (gövdenin altında). */
    val beamTop: FloatArray,
    val beamBottom: FloatArray,
    val brakePool: FloatArray,
    val reverseFloor: FloatArray,
    /** Lambalar (gövdenin üstünde): far, geri vites, sinyal köşeleri (ön, arka). */
    val headTop: FloatArray,
    val headBottom: FloatArray,
    val reverseTop: FloatArray,
    val reverseBottom: FloatArray,
    val turnLeftFront: FloatArray,
    val turnLeftRear: FloatArray,
    val turnRightFront: FloatArray,
    val turnRightRear: FloatArray,
    /** Stop çubuğunun kutusu: arka kenar boyunca, köşelere kadar. */
    val tail: FloatArray,
    /** Gövdenin tuvaldeki alt kenarı (zemindeki yansıma buradan başlar). */
    val bodyBottom: Float,
    /** Şeritteki Bagaj görselinin kırpması ([TrunkArt]): açılınca geriye taşan kapak dahil, arka kapılara kadar. */
    val trunkCrop: FloatArray,
    /** Stop çubuğunun ortası bagaj kapağındaysa (Jaecoo 7) kapakla birlikte gider; null = çubuk tek parça, gövdede (Tiggo). */
    val tailGate: TailGate? = null,
    /** Fren zemin ışığı gövde tarafındaki kenarında sıfıra iner (Jaecoo 7: kutu yuvarlak arka köşelerden taşar). false = 1.5.1 maskesi (Tiggo). */
    val brakePoolSoftEdge: Boolean = false,
    /**
     * Cam tavan, tavan katmanı kutusunun ([roofY0]..[roofY1]) içinde üstten ve alttan bu kadar içeride başlar (Tiggo 8: çerçeve payı;
     * kayan panel ve kenar çizgileri camın dışına çizilmez). 0 = kutunun tamamı cam (Tiggo 7, Jaecoo 7).
     */
    val roofGlassInsetY: Float = 0f,
    /**
     * Gövdenin tuvaldeki yeri ve aracın gerçek boyu (yalnız Tiggo 8): araç, Tiggo 7'ye göre gerçek boy oranında çizilir
     * ([bodyScale], [stageScale]). null = ölçek tam 1, tuval çerçeveyi olduğu gibi doldurur (Tiggo 7; Jaecoo 7 onaylanan boyunda).
     */
    val bodySize: BodySize? = null,
    /**
     * Camın yuvarlak ön kenarı, tavan katmanı kutusunun ön ucundan ([roofX1]) bu kadar içeridedir (yalnız Tiggo 8): tavan açıkken
     * kenar ışığının ön çizgisi camın kenarına oturur. 0 = kutunun ucu (Tiggo 7, Jaecoo 7).
     */
    val roofGlassInsetFront: Float = 0f,
) {
    /** Camın üst ve alt satırı: perde kenarı ve kenar ışığı bunların içinde kalır; pay 0 iken tam [roofY0] / [roofY1]. */
    val roofGlassY0: Float = roofY0 + roofGlassInsetY
    val roofGlassY1: Float = roofY1 - roofGlassInsetY
    /** Camın ön kenarı (kenar ışığının ön çizgisi); pay 0 iken tam [roofX1]. */
    val roofGlassX1: Float = roofX1 - roofGlassInsetFront

    /** Gövde boyunu tuvali dolduran ilk takımınkine (Tiggo 7, [BodySize.REF_PX]) eşitleyen büyütme; [bodySize] yoksa tam 1. */
    val bodyScale: Float = bodySize?.let { BodySize.REF_PX / (it.x1 - it.x0) } ?: 1f
    /** [bodyScale] × gerçek boy oranı: Sahne'de uzun araç uzun çizilir (Tiggo 8, Tiggo 7'den ~%5 uzun); [bodySize] yoksa tam 1. */
    val stageScale: Float = bodySize?.let { bodyScale * it.lengthMm / BodySize.REF_LENGTH_MM } ?: 1f

    /** Açık parçalar base dışına taşar (kapılar yanlara, bagaj geriye); görünüm bu payları kapsar. Gölge bulanıklığı dahil. */
    val padLeft: Float = maxOf(0f, -parts.values.minOf { minOf(it.overlay.x, it.sprite.x + it.translateXEnd) })
    val padTop: Float = maxOf(0f, -parts.values.minOf { it.overlay.y }) + CarAnimSpec.SHADOW_BLUR * 2
    val padRight: Float = maxOf(0f, parts.values.maxOf { it.overlay.right } - CarAnimSpec.BASE_W)
    val padBottom: Float = maxOf(0f, parts.values.maxOf { it.overlay.bottom } - CarAnimSpec.BASE_H) + CarAnimSpec.SHADOW_BLUR * 2
}

/** Gövdenin tuvaldeki uçları ([x0] arka uç .. [x1] burun; aynasız, base px) ve aracın gerçek boyu ([lengthMm]). */
class BodySize(val x0: Float, val x1: Float, val lengthMm: Float) {
    companion object {
        /** Ölçek referansı: Tiggo 7 takımı (gövde 764 px, 4500 mm). */
        const val REF_PX = 764f
        const val REF_LENGTH_MM = 4500f
    }
}

/**
 * Bagaj kapağındaki ışık çubuğu: [cut0]..[cut1] ortayı gövdedeki uç parçalardan ayıran kesim (base y). Kapak kalkınca orta parça
 * kapakla söner, yerine [openBox] (base px: sol, üst, sağ, alt) içinde açık bagaj görselindeki LED çizgisinden okunan çubuk belirir.
 * [reverseDx]: bagaj açıkken geri vites zemin ışığının kapağın arkasına kayması (base px); geri lambaları söner.
 * [brakeDx]: bagaj açıkken fren zemin ışığının aynı yöne kayması (yalnız Tiggo 8: kalkık kapak ışığı örter); 0 = yerinde kalır.
 */
class TailGate(val cut0: Float, val cut1: Float, val openBox: FloatArray, val reverseDx: Float, val brakeDx: Float = 0f)

/**
 * Sahne'de çizilen araç modeli ([TIGGO] = Tiggo 7, ilk takım; adı ve `car_` ön eki değişmedi): [prefix] görsel dosya ön eki (`<prefix><renk>_base` ...), tavan katmanları (renkten
 * bağımsız) ve [geometry] o takımın ölçüleri. Etkin model [active] ile okunur; [resolve] başlatmada bir kez (ve her yeniden
 * kurulumda) çağrılır, böylece açık ekran çalışırken model değişmez ([VehicleProfile] ile aynı düzen).
 */
enum class CarModel(
    val key: String,
    val prefix: String,
    @DrawableRes val roofClosed: Int,
    @DrawableRes val roofOpen: Int,
    @DrawableRes val roofHole: Int,
    val geometry: CarGeometry,
) {
    TIGGO("tiggo", "car_", R.drawable.car_roof_shade_closed, R.drawable.car_roof_shade_open, R.drawable.car_roof_open_hole, TIGGO_GEOMETRY),
    JAECOO7("jaecoo7", "car_j7_", R.drawable.car_j7_roof_shade_closed, R.drawable.car_j7_roof_shade_open, R.drawable.car_j7_roof_open_hole, JAECOO7_GEOMETRY),
    TIGGO8("tiggo8", "car_t8_", R.drawable.car_t8_roof_shade_closed, R.drawable.car_t8_roof_shade_open, R.drawable.car_t8_roof_open_hole, TIGGO8_GEOMETRY);

    companion object {
        /** Elle seçim: 0 = Otomatik, 1 = Tiggo 7, 2 = Jaecoo 7, 3 = Tiggo 8 ([HomeScreenPrefs.carModelIndex]). */
        const val OVERRIDE_AUTO = 0
        const val OVERRIDE_MAX = 3

        /** Etkin model. [resolve] hiç çağrılmadıysa (birim testleri) Tiggo 7 ([TIGGO]). */
        @Volatile
        var active: CarModel = TIGGO
            internal set

        /** Demo/test: `--es launcher_demo_car tiggo|jaecoo7|tiggo8` (yalnızca debug ve demo açıkken); elle seçimi geçersiz kılar. */
        @Volatile
        var demoOverride: CarModel? = null

        const val EXTRA_MODEL = "launcher_demo_car"

        /**
         * Elle seçim ([OVERRIDE_AUTO] ya da 1/2/3) ile araç ekranı profilinden model (saf; birim testlenir). Otomatik: dikey ekran
         * ([VehicleProfile.PORTRAIT], Jaecoo J7/J8) Jaecoo 7, Tiggo 8 ekranı Tiggo 8, Tiggo 7 ekranı Tiggo 7 ([TIGGO]).
         */
        fun choose(override: Int, profile: VehicleProfile): CarModel = when (override) {
            1 -> TIGGO
            2 -> JAECOO7
            3 -> TIGGO8
            else -> when (profile) {
                VehicleProfile.PORTRAIT -> JAECOO7
                VehicleProfile.TIGGO8 -> TIGGO8
                VehicleProfile.TIGGO7 -> TIGGO
            }
        }

        /** Verilen ad "tiggo"/"jaecoo7"/"tiggo8" ise model; aksi halde null. */
        fun parse(name: String?): CarModel? = values().firstOrNull { it.key.equals(name?.trim(), ignoreCase = true) }

        /** Elle seçim ve demo isteği dahil, bu cihaz için model (durum değiştirmez). */
        fun compute(context: Context): CarModel =
            demoOverride ?: choose(HomeScreenPrefs.carModelIndex(context), VehicleProfile.compute(context))

        /** Etkin modeli günceller; başlatmada ve her yeniden kurulumda çağrılır. */
        fun resolve(context: Context): CarModel = compute(context).also { active = it }
    }
}

private fun door(sprite: Box, gap: Box, overlay: Box, px: Float, py: Float, rot: Float, sx: Float, sy: Float, shadow: Box, rim: Box, mirror: Boolean = false) =
    Part(Kind.HINGE, sprite, gap, overlay, px, py, rot, sx, sy, 0f, 360, CarAnimSpec.EASE_OPEN, 360, CarAnimSpec.EASE_CLOSE, 250, 120, 120, shadow, rim, mirror)

/** Tiggo: `.impeccable/mocks/premium/doors/android/anim.json` (sürüm 2) elle aktarıldı. */
private val TIGGO_GEOMETRY = CarGeometry(
    parts = mapOf(
        CarPart.DOOR_FL to door(Box(350f, 16f, 169f, 56f), Box(349f, 15f, 171f, 59f), Box(336f, -64f, 253f, 134f),
            522.73f, 20.25f, 37f, 0.825f, 0.85f, Box(354.1f, 14.6f, 160.9f, 48.0f), Box(354.1f, 16.7f, 160.9f, 2.1f)),
        CarPart.DOOR_RL to door(Box(169f, 22f, 186f, 50f), Box(167f, 22f, 190f, 52f), Box(124f, -76f, 239f, 146f),
            356.88f, 25.89f, 45f, 0.775f, 0.95f, Box(173.4f, 18.8f, 177.8f, 43.8f), Box(173.4f, 21.0f, 177.8f, 2.1f)),
        CarPart.DOOR_FR to door(Box(350f, 283f, 169f, 61f), Box(349f, 281f, 172f, 65f), Box(336f, 287f, 253f, 146f),
            522.73f, 333.59f, -45f, 0.775f, 1.15f, Box(354.1f, 291.9f, 160.9f, 53.6f), Box(354.1f, 341.3f, 160.9f, 2.1f)),
        CarPart.DOOR_RR to door(Box(169f, 283f, 187f, 57f), Box(167f, 281f, 190f, 59f), Box(124f, 287f, 239f, 146f),
            351.24f, 327.24f, -45f, 0.775f, 0.85f, Box(172.7f, 291.9f, 179.3f, 50.1f), Box(172.7f, 337.8f, 179.3f, 2.1f)),
        CarPart.HOOD to Part(Kind.LID, Box(546f, 41f, 220f, 275f), Box(544f, 39f, 222f, 278f), Box(541f, 31f, 220f, 302f),
            590.47f, 177.62f, 0f, 0.42f, 1.05f, -42.34f, 300, CarAnimSpec.EASE_OPEN, 320, CarAnimSpec.EASE_CLOSE, 220, 120, 120),
        CarPart.TRUNK to Part(Kind.SLIDE_FADE, Box(0f, 38f, 70f, 287f), Box(0f, 38f, 72f, 287f), Box(-79f, 12f, 209f, 325f),
            0f, 0f, 0f, 1f, 1f, -22.58f, 260, CarAnimSpec.EASE_STANDARD, 260, CarAnimSpec.EASE_STANDARD, 0, 0, 0),
    ),
    windows = mapOf(
        BodyPart.WINDOW_LF to Glass(Box(352f, 47f, 128f, 22f), CarPart.DOOR_FL),
        BodyPart.WINDOW_LR to Glass(Box(205f, 47f, 126f, 22f), CarPart.DOOR_RL),
        BodyPart.WINDOW_RF to Glass(Box(352f, 286f, 128f, 22f), CarPart.DOOR_FR),
        BodyPart.WINDOW_RR to Glass(Box(205f, 286f, 126f, 22f), CarPart.DOOR_RR),
    ),
    sunroof = Glass(Box(163f, 96f, 268f, 156f), null, strength = 0.6f),
    roofX0 = 162f,
    roofX1 = 438f,
    roofY0 = 95f,
    roofY1 = 254f,
    roofSplit = 291f,
    roofFrontX0 = 293f,
    roofGlassTravel = 118f,
    tireX = floatArrayOf(622f, 622f, 168f, 168f),
    tireEdge = floatArrayOf(26f, 337f, 23f, 338f),
    tireL = 92f,
    tireW = 26f,
    tireOut = 11f,
    fuelCap = Box(127f, 321f, 26f, 11f),
    hoodCx = 655f,
    hoodCy = 178f,
    hoodRx = 118f,
    hoodRy = 132f,
    beamTop = floatArrayOf(735f, -23f, 985f, 167f),
    beamBottom = floatArrayOf(735f, 189f, 985f, 379f),
    brakePool = floatArrayOf(-300f, -40f, 30f, 396f),
    reverseFloor = floatArrayOf(-160f, 88f, 40f, 268f),
    headTop = floatArrayOf(690f, 36f, 772f, 104f),
    headBottom = floatArrayOf(690f, 252f, 772f, 320f),
    reverseTop = floatArrayOf(0f, 95f, 56f, 145f),
    reverseBottom = floatArrayOf(0f, 211f, 56f, 261f),
    turnLeftFront = floatArrayOf(690f, 17f, 760f, 87f),
    turnLeftRear = floatArrayOf(2f, 17f, 72f, 87f),
    turnRightFront = floatArrayOf(690f, 269f, 760f, 339f),
    turnRightRear = floatArrayOf(2f, 269f, 72f, 339f),
    tail = floatArrayOf(-12f, 20f, 72f, 336f),
    bodyBottom = CarAnimSpec.BASE_H,
    trunkCrop = floatArrayOf(-60f, 6f, 200f, 350f),
)

/**
 * Jaecoo 7: aynı 766 × 356 tuvalde, gövde tuvalin ortasında (üstte 26, altta 27 px boşluk). Ön kapı sprite'ları dış aynayı
 * taşır ([Part.mirror]); ışık çubuğunun ortası bagaj kapağındadır ([TailGate]). Ölçüler emülatörde ayarlandı.
 */
private val JAECOO7_GEOMETRY = CarGeometry(
    parts = mapOf(
        CarPart.DOOR_FL to door(Box(366f, 26f, 148f, 73f), Box(365f, 51f, 150f, 47f), Box(364f, -1f, 152f, 100f),
            503.96f, 61.26f, 24.0f, 0.925f, 0.9f, Box(370.2f, 50.6f, 139.8f, 38.0f), Box(370.2f, 52.7f, 139.8f, 2.1f), mirror = true),
        CarPart.DOOR_RL to door(Box(234f, 52f, 133f, 45f), Box(233f, 51f, 135f, 47f), Box(232f, 1f, 137f, 98f),
            363.53f, 51.98f, 24.5f, 0.937f, 0.875f, Box(238.2f, 50.6f, 124.8f, 38.0f), Box(238.2f, 52.7f, 124.8f, 2.1f)),
        CarPart.DOOR_FR to door(Box(366f, 256f, 149f, 73f), Box(365f, 257f, 150f, 47f), Box(364f, 256f, 152f, 100f),
            505.81f, 293.74f, -23.5f, 0.925f, 0.9f, Box(370.2f, 266.8f, 139.8f, 38.0f), Box(370.2f, 300.2f, 139.8f, 2.1f), mirror = true),
        CarPart.DOOR_RR to door(Box(234f, 256f, 133f, 47f), Box(233f, 257f, 135f, 47f), Box(232f, 256f, 137f, 98f),
            361.68f, 305.8f, -25.0f, 0.925f, 0.875f, Box(238.2f, 266.8f, 124.8f, 38.0f), Box(238.2f, 300.2f, 124.8f, 2.1f)),
        CarPart.HOOD to Part(Kind.LID, Box(528f, 65f, 144f, 227f), Box(527f, 64f, 146f, 227f), Box(515f, 44f, 157f, 259f),
            // Açık görselde kalkık kaput gövdeden uzun ve yukarı kaçık (y 45..302): sprite o boya kadar uzar (ölçek merkezi y 216,5) ve
            // geçiş, kaput daha hareket ederken erken başlar (son anda boy sıçramasın). Kapanışta motor görseli 100 ms'de söner:
            // daha uzun sürerse genişleyen kaputun üstünde yarı saydam motor bölmesi görünür.
            529.0f, 216.5f, 0f, 0.22f, 1.132f, -6.0f, 300, CarAnimSpec.EASE_OPEN, 320, CarAnimSpec.EASE_CLOSE, 140, 160, 100),
        CarPart.TRUNK to Part(Kind.SLIDE_FADE, Box(85f, 86f, 63f, 184f), Box(84f, 85f, 65f, 186f), Box(-20f, 65f, 191f, 228f),
            0f, 0f, 0f, 1f, 1f, -40.0f, 260, CarAnimSpec.EASE_STANDARD, 260, CarAnimSpec.EASE_STANDARD, 0, 0, 0),
    ),
    windows = mapOf(
        BodyPart.WINDOW_LF to Glass(Box(375f, 71.5f, 93f, 15f), CarPart.DOOR_FL),
        BodyPart.WINDOW_LR to Glass(Box(250f, 71.5f, 100f, 15f), CarPart.DOOR_RL),
        BodyPart.WINDOW_RF to Glass(Box(375f, 268.5f, 93f, 15f), CarPart.DOOR_FR),
        BodyPart.WINDOW_RR to Glass(Box(250f, 268.5f, 100f, 15f), CarPart.DOOR_RR),
    ),
    sunroof = Glass(Box(220.5f, 114.3f, 177f, 126f), null, strength = 0.6f),
    roofX0 = 218.47f,
    roofX1 = 399.68f,
    roofY0 = 112.31f,
    roofY1 = 242.23f,
    roofSplit = 326.0f,
    roofFrontX0 = 329.70f,
    roofGlassTravel = 57.4f,
    tireX = floatArrayOf(568f, 568f, 206f, 206f),
    tireEdge = floatArrayOf(53f, 302f, 53f, 302f),
    tireL = 86f,
    tireW = 22f,
    tireOut = 8f,
    // Sol arkada (görünüşte üst kenar); taraf tahmin, bkz. [CarAnimSpec.FUEL_CAP] TODO.
    fuelCap = Box(171f, 58f, 26f, 11f),
    hoodCx = 597f,
    hoodCy = 177.5f,
    hoodRx = 68f,
    hoodRy = 100f,
    beamTop = floatArrayOf(649f, -9f, 899f, 181f),
    beamBottom = floatArrayOf(649f, 174f, 899f, 364f),
    brakePool = floatArrayOf(-215f, -14f, 115f, 370f),
    reverseFloor = floatArrayOf(-25f, 94f, 175f, 262f),
    headTop = floatArrayOf(604f, 52f, 686f, 120f),
    headBottom = floatArrayOf(604f, 235f, 686f, 303f),
    reverseTop = floatArrayOf(66f, 103f, 122f, 153f),
    reverseBottom = floatArrayOf(66f, 202f, 122f, 252f),
    turnLeftFront = floatArrayOf(604f, 40f, 674f, 100f),
    turnLeftRear = floatArrayOf(87f, 40f, 157f, 100f),
    turnRightFront = floatArrayOf(604f, 255f, 674f, 315f),
    turnRightRear = floatArrayOf(87f, 255f, 157f, 315f),
    tail = floatArrayOf(73f, 60f, 157f, 295f),
    bodyBottom = 329f,
    trunkCrop = floatArrayOf(-24f, 20f, 214f, 335f),
    tailGate = TailGate(cut0 = 89f, cut1 = 269f, openBox = floatArrayOf(-52f, 62f, 32f, 288f), reverseDx = -105f),
    brakePoolSoftEdge = true,
)

/**
 * Tiggo 8: aynı 766 × 356 tuvalde, gövde 600 px (83..683), orta çizgi y = 177,27. Jaecoo 7'nin seçenekleri burada da
 * açık: ön kapı sprite'ları dış aynayı taşır ([Part.mirror]), ışık çubuğunun ortası bagaj kapağındadır ([TailGate]), fren zemin
 * ışığı gövde tarafında söner ([CarGeometry.brakePoolSoftEdge]). Yalnız bu modelde: cam tavan payı ([CarGeometry.roofGlassInsetY])
 * ve gerçek boy oranı ([CarGeometry.bodySize]). Ölçüler görsel takımının dışa aktarımından gelir, emülatörde ayarlandı.
 */
private val TIGGO8_GEOMETRY = CarGeometry(
    parts = mapOf(
        CarPart.DOOR_FL to door(Box(383f, 37f, 133f, 58f), Box(382f, 59f, 135f, 36f), Box(382f, -25f, 144f, 120f),
            507.65f, 83.45f, 37.5f, 1.09f, 1.24f, Box(387.2f, 58.6f, 124.8f, 27f), Box(387.2f, 60.7f, 84f, 2.1f), mirror = true),
        CarPart.DOOR_RL to door(Box(257f, 55f, 129f, 39f), Box(258f, 55f, 129f, 40f), Box(231f, -25f, 155f, 121f),
            390.44f, 49.08f, 40f, 0.96f, 1.24f, Box(263.2f, 54.6f, 118.8f, 31f), Box(293f, 60.0f, 89f, 2.1f)),
        CarPart.DOOR_FR to door(Box(383f, 260f, 133f, 58f), Box(382f, 260f, 135f, 36f), Box(382f, 259f, 144f, 121f),
            507.65f, 271.09f, -37.5f, 1.09f, 1.24f, Box(387.2f, 269.8f, 124.8f, 27f), Box(387.2f, 292.2f, 84f, 2.1f), mirror = true),
        CarPart.DOOR_RR to door(Box(257f, 261f, 129f, 39f), Box(258f, 260f, 129f, 40f), Box(231f, 259f, 155f, 121f),
            390.44f, 305.46f, -40f, 0.96f, 1.24f, Box(263.2f, 269.8f, 118.8f, 31f), Box(293f, 292.9f, 89f, 2.1f)),
        CarPart.HOOD to Part(Kind.LID, Box(532f, 70f, 129f, 215f), Box(531f, 69f, 130f, 216f), Box(511f, 69f, 150f, 217f),
            553.7f, 177.27f, 0f, 0.24f, 1f, -12.09f, 300, CarAnimSpec.EASE_OPEN, 320, CarAnimSpec.EASE_CLOSE, 220, 120, 120),
        CarPart.TRUNK to Part(Kind.SLIDE_FADE, Box(83f, 94f, 103f, 165f), Box(83f, 95f, 104f, 165f), Box(12f, 85f, 174f, 185f),
            0f, 0f, 0f, 1f, 1f, -40f, 260, CarAnimSpec.EASE_STANDARD, 260, CarAnimSpec.EASE_STANDARD, 0, 0, 0),
    ),
    // Ön cam vurgusu A direğine doğru daralan kısma girmez (kısa kutu).
    windows = mapOf(
        BodyPart.WINDOW_LF to Glass(Box(393.7f, 79.0f, 62.0f, 10.4f), CarPart.DOOR_FL),
        BodyPart.WINDOW_LR to Glass(Box(302.0f, 80.5f, 72.16f, 12.23f), CarPart.DOOR_RL),
        BodyPart.WINDOW_RF to Glass(Box(393.7f, 265.13f, 62.0f, 10.4f), CarPart.DOOR_FR),
        BodyPart.WINDOW_RR to Glass(Box(302.0f, 261.8f, 72.16f, 12.23f), CarPart.DOOR_RR),
    ),
    // Yedek cam vurgusu (tavan konumu okunamazsa): yalnız iki cam; çerçeve payı ve aradaki kiriş boyanmaz.
    sunroof = Glass(Box(225.23f, 114.79f, 53.82f, 124.95f), null, strength = 0.6f, box2 = Box(288.12f, 114.79f, 132.71f, 124.95f)),
    roofX0 = 223.23f,
    roofX1 = 425.33f,
    roofY0 = 108.99f,
    roofY1 = 245.54f,
    roofSplit = 279.05f,
    roofFrontX0 = 288.12f,
    roofGlassTravel = 64.49f,
    tireX = floatArrayOf(565.33f, 565.33f, 221.6f, 221.6f),
    tireEdge = floatArrayOf(58.83f, 296.17f, 56.04f, 298.96f),
    tireL = 92f,
    tireW = 24f,
    tireOut = 7f,
    // Yakıt kapağı sol arka çamurlukta (görünüşte üst kenar), arka lastiğin gerisinde (lastik uyarısıyla tek blok olmasın; taraf tahmin).
    fuelCap = Box(145.81f, 68.94f, 26f, 11f),
    hoodCx = 599.28f,
    hoodCy = 177.27f,
    hoodRx = 61.4f,
    hoodRy = 95.68f,
    beamTop = floatArrayOf(650.53f, -2.26f, 900.53f, 187.74f),
    beamBottom = floatArrayOf(650.53f, 166.8f, 900.53f, 356.8f),
    brakePool = floatArrayOf(-214f, 27.27f, 116f, 327.27f),
    reverseFloor = floatArrayOf(-26.53f, 93.27f, 173.47f, 261.27f),
    headTop = floatArrayOf(605.53f, 58.74f, 687.53f, 126.74f),
    headBottom = floatArrayOf(605.53f, 227.8f, 687.53f, 295.8f),
    reverseTop = floatArrayOf(83.47f, 102.77f, 139.47f, 152.77f),
    reverseBottom = floatArrayOf(83.47f, 201.77f, 139.47f, 251.77f),
    turnLeftFront = floatArrayOf(596.84f, 51.12f, 666.84f, 111.12f),
    turnLeftRear = floatArrayOf(80.56f, 56.23f, 150.56f, 116.23f),
    turnRightFront = floatArrayOf(596.84f, 243.42f, 666.84f, 303.42f),
    turnRightRear = floatArrayOf(80.56f, 238.31f, 150.56f, 298.31f),
    // Kutu merceğin uçlarından (y ≈ 78 / 276) ~20 px taşar: uç sönümü ([CarAnimSpec.TAIL_TAPER]) kesimlerden önce biter, gövdedeki uç
    // parçalar orta çubukla aynı güçte yanar.
    tail = floatArrayOf(71.47f, 58f, 155.47f, 296.5f),
    bodyBottom = 318f,
    trunkCrop = floatArrayOf(8f, 20.27f, 238.81f, 334.27f),
    // Kalkık kapağın LED çizgisi açık bagaj görselinde x 16..21, y 97..258 (grafit takımdan ölçüldü); kutu çizgiyi ortalar.
    tailGate = TailGate(cut0 = 96.45f, cut1 = 258.08f, openBox = floatArrayOf(-24f, 90f, 60f, 265f), reverseDx = -70f, brakeDx = -70f),
    brakePoolSoftEdge = true,
    roofGlassInsetY = 5.8f,
    bodySize = BodySize(x0 = 83f, x1 = 683f, lengthMm = 4722f),
    roofGlassInsetFront = 4.5f,
)
