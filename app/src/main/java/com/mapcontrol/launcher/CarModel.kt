package com.mapcontrol.launcher

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
) {
    /** Açık parçalar base dışına taşar (kapılar yanlara, bagaj geriye); görünüm bu payları kapsar. Gölge bulanıklığı dahil. */
    val padLeft: Float = maxOf(0f, -parts.values.minOf { minOf(it.overlay.x, it.sprite.x + it.translateXEnd) })
    val padTop: Float = maxOf(0f, -parts.values.minOf { it.overlay.y }) + CarAnimSpec.SHADOW_BLUR * 2
    val padRight: Float = maxOf(0f, parts.values.maxOf { it.overlay.right } - CarAnimSpec.BASE_W)
    val padBottom: Float = maxOf(0f, parts.values.maxOf { it.overlay.bottom } - CarAnimSpec.BASE_H) + CarAnimSpec.SHADOW_BLUR * 2
}

/**
 * Bagaj kapağındaki ışık çubuğu: [cut0]..[cut1] ortayı gövdedeki uç parçalardan ayıran kesim (base y). Kapak kalkınca orta parça
 * kapakla söner, yerine [openBox] (base px: sol, üst, sağ, alt) içinde açık bagaj görselindeki LED çizgisinden okunan çubuk belirir.
 * [reverseDx]: bagaj açıkken geri vites zemin ışığının kapağın arkasına kayması (base px); geri lambaları söner.
 */
class TailGate(val cut0: Float, val cut1: Float, val openBox: FloatArray, val reverseDx: Float)

/**
 * Sahne'de çizilen araç modeli: [prefix] görsel dosya ön eki (`<prefix><renk>_base` ...), tavan katmanları (renkten
 * bağımsız) ve [geometry] o takımın ölçüleri. Etkin model [active] ile okunur.
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
    JAECOO7("jaecoo7", "car_j7_", R.drawable.car_j7_roof_shade_closed, R.drawable.car_j7_roof_shade_open, R.drawable.car_j7_roof_open_hole, JAECOO7_GEOMETRY);

    companion object {
        /** Etkin model: şimdilik hep Tiggo (Jaecoo 7 henüz seçilmiyor). */
        @Volatile
        var active: CarModel = TIGGO
            internal set
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
