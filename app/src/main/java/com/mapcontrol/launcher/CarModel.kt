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
) {
    /** Açık parçalar base dışına taşar (kapılar yanlara, bagaj geriye); görünüm bu payları kapsar. Gölge bulanıklığı dahil. */
    val padLeft: Float = maxOf(0f, -parts.values.minOf { minOf(it.overlay.x, it.sprite.x + it.translateXEnd) })
    val padTop: Float = maxOf(0f, -parts.values.minOf { it.overlay.y }) + CarAnimSpec.SHADOW_BLUR * 2
    val padRight: Float = maxOf(0f, parts.values.maxOf { it.overlay.right } - CarAnimSpec.BASE_W)
    val padBottom: Float = maxOf(0f, parts.values.maxOf { it.overlay.bottom } - CarAnimSpec.BASE_H) + CarAnimSpec.SHADOW_BLUR * 2
}

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
    TIGGO("tiggo", "car_", R.drawable.car_roof_shade_closed, R.drawable.car_roof_shade_open, R.drawable.car_roof_open_hole, TIGGO_GEOMETRY);

    companion object {
        /** Etkin model: şimdilik tek model var (Tiggo). */
        @Volatile
        var active: CarModel = TIGGO
            internal set
    }
}

private fun door(sprite: Box, gap: Box, overlay: Box, px: Float, py: Float, rot: Float, sx: Float, sy: Float, shadow: Box, rim: Box) =
    Part(Kind.HINGE, sprite, gap, overlay, px, py, rot, sx, sy, 0f, 360, CarAnimSpec.EASE_OPEN, 360, CarAnimSpec.EASE_CLOSE, 250, 120, 120, shadow, rim)

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
