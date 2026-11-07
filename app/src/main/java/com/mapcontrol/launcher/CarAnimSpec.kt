package com.mapcontrol.launcher

import android.content.Context
import androidx.annotation.StringRes
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID
import com.mapcontrol.R

/**
 * Sahne aracının açılan parçaları. Görselde burun sağda, üst kenar aracın SOL tarafı (sürücü, soldan direksiyon):
 * üstteki kapılar sol ön / sol arka, alttakiler sağ ön / sağ arka. [demoKey] `launcher_demo_open` biçimidir
 * (araç kartıyla aynı: lf, lr, rf, rr, trunk, hood); [asset] görsel dosya adındaki parça adı.
 */
enum class CarPart(val demoKey: String, val asset: String) {
    DOOR_FL("lf", "door_fl"),
    DOOR_RL("lr", "door_rl"),
    DOOR_FR("rf", "door_fr"),
    DOOR_RR("rr", "door_rr"),
    TRUNK("trunk", "trunk"),
    HOOD("hood", "hood");

    val isDoor: Boolean get() = asset.startsWith("door")

    companion object {
        /** Demo anahtarı ("lf") ya da görsel adı ("fl", "door_fl"); bilinmeyen null. */
        fun parse(key: String): CarPart? {
            val k = key.trim().lowercase(java.util.Locale.ROOT).removePrefix("door_")
            return values().firstOrNull { it.demoKey == k || it.asset.removePrefix("door_") == k }
        }

        /** Araç kartının kapı sinyalleri → açık parça kümesi (sol ön = sürücü kapısı = üstteki ön kapı). */
        fun fromSignals(lf: Boolean, rf: Boolean, lr: Boolean, rr: Boolean, trunk: Boolean, hood: Boolean): Set<CarPart> =
            buildSet {
                if (lf) add(DOOR_FL)
                if (rf) add(DOOR_FR)
                if (lr) add(DOOR_RL)
                if (rr) add(DOOR_RR)
                if (trunk) add(TRUNK)
                if (hood) add(HOOD)
            }
    }
}

/** Sahne aracının boyası; [key] tercih değeri ve görsel ön eki (`car_<key>_base`), [swatch] seçicideki renk. */
enum class CarColor(val key: String, @StringRes val label: Int, val swatch: Int) {
    GRAFIT("grafit", R.string.car_color_grafit, 0xFF4A4D52.toInt()),
    SIYAH("siyah", R.string.car_color_siyah, 0xFF1B1D21.toInt()),
    BEYAZ("beyaz", R.string.car_color_beyaz, 0xFFE9E7E1.toInt()),
    GUMUS("gumus", R.string.car_color_gumus, 0xFFAEB2B8.toInt()),
    KIRMIZI("kirmizi", R.string.car_color_kirmizi, 0xFFA9232A.toInt()),
    MAVI("mavi", R.string.car_color_mavi, 0xFF27479A.toInt());

    companion object {
        fun fromKey(key: String?): CarColor = values().firstOrNull { it.key == key } ?: GRAFIT
    }
}

/** "Araç rengi" tercihi (launcher ayarlarıyla aynı dosya); değişince sahne görselleri canlı değişir, yeniden kurulum yok. */
object CarColorPrefs {
    const val PREFS_NAME = "launcher_settings"
    const val KEY = "car_color"

    fun get(context: Context): CarColor =
        CarColor.fromKey(context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, null))

    fun set(context: Context, color: CarColor) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY, color.key).apply()
    }
}

/**
 * Kapı animasyonu ölçüleri: `.impeccable/mocks/premium/doors/android/anim.json` (sürüm 2) elle aktarıldı.
 * Birimler base.webp pikselidir (766 × 356); her katman base'in sol üst köşesine göredir. Dönüşüm sırası
 * View ile aynı: pivot etrafında öteleme · dönme (saat yönü +) · ölçek (kapının kendi eksenlerinde).
 */
object CarAnimSpec {

    const val BASE_W = 766f
    const val BASE_H = 356f

    class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
        val right get() = x + w
        val bottom get() = y + h
    }

    enum class Kind { HINGE, LID, SLIDE_FADE }

    class Part(
        val kind: Kind,
        val sprite: Box,
        val gap: Box,
        val overlay: Box,
        val pivotX: Float,
        val pivotY: Float,
        val rotationEnd: Float,
        val scaleXEnd: Float,
        val scaleYEnd: Float,
        val translateXEnd: Float,
        val openMs: Long,
        val openEasing: FloatArray,
        val closeMs: Long,
        val closeEasing: FloatArray,
        /** Kapı ve kaput: açılışta üst görselin geçişi bu ms'de başlar, [xfadeMs] sürer; kapanışta hemen başlar. */
        val xfadeStartMs: Long,
        val xfadeMs: Long,
        val closeXfadeMs: Long,
        /** Kapı gölgesi ve dış kenar ışığı (yalnızca kapılar). */
        val shadow: Box? = null,
        val rim: Box? = null,
    )

    val EASE_OPEN = floatArrayOf(0.2f, 0.9f, 0.25f, 1.05f)
    val EASE_CLOSE = floatArrayOf(0.4f, 0.0f, 0.2f, 1.0f)
    val EASE_STANDARD = floatArrayOf(0.4f, 0.0f, 0.2f, 1.0f)

    const val GAP_FADE_UNTIL = 0.18f
    const val SPRITE_FADE_FROM = 0.45f
    const val SHADOW_MAX_ALPHA = 0.7f
    const val SHADOW_BLUR = 7.1f
    const val SHADOW_CORNER = 12.7f
    const val RIM_MAX_ALPHA = 0.45f
    const val TRUNK_GAP_TO = 0.3f
    const val TRUNK_OVERLAY_FROM = 0.1f
    const val TRUNK_OVERLAY_TO = 0.85f
    const val TRUNK_SPRITE_FROM = 0.3f

    private fun door(sprite: Box, gap: Box, overlay: Box, px: Float, py: Float, rot: Float, sx: Float, sy: Float, shadow: Box, rim: Box) =
        Part(Kind.HINGE, sprite, gap, overlay, px, py, rot, sx, sy, 0f, 360, EASE_OPEN, 360, EASE_CLOSE, 250, 120, 120, shadow, rim)

    val parts: Map<CarPart, Part> = mapOf(
        CarPart.DOOR_FL to door(Box(350f, 16f, 169f, 56f), Box(349f, 15f, 171f, 59f), Box(336f, -64f, 253f, 134f),
            522.73f, 20.25f, 37f, 0.825f, 0.85f, Box(354.1f, 14.6f, 160.9f, 48.0f), Box(354.1f, 16.7f, 160.9f, 2.1f)),
        CarPart.DOOR_RL to door(Box(169f, 22f, 186f, 50f), Box(167f, 22f, 190f, 52f), Box(124f, -76f, 239f, 146f),
            356.88f, 25.89f, 45f, 0.775f, 0.95f, Box(173.4f, 18.8f, 177.8f, 43.8f), Box(173.4f, 21.0f, 177.8f, 2.1f)),
        CarPart.DOOR_FR to door(Box(350f, 283f, 169f, 61f), Box(349f, 281f, 172f, 65f), Box(336f, 287f, 253f, 146f),
            522.73f, 333.59f, -45f, 0.775f, 1.15f, Box(354.1f, 291.9f, 160.9f, 53.6f), Box(354.1f, 341.3f, 160.9f, 2.1f)),
        CarPart.DOOR_RR to door(Box(169f, 283f, 187f, 57f), Box(167f, 281f, 190f, 59f), Box(124f, 287f, 239f, 146f),
            351.24f, 327.24f, -45f, 0.775f, 0.85f, Box(172.7f, 291.9f, 179.3f, 50.1f), Box(172.7f, 337.8f, 179.3f, 2.1f)),
        CarPart.HOOD to Part(Kind.LID, Box(546f, 41f, 220f, 275f), Box(544f, 39f, 222f, 278f), Box(541f, 31f, 220f, 302f),
            590.47f, 177.62f, 0f, 0.42f, 1.05f, -42.34f, 300, EASE_OPEN, 320, EASE_CLOSE, 220, 120, 120),
        CarPart.TRUNK to Part(Kind.SLIDE_FADE, Box(0f, 38f, 70f, 287f), Box(0f, 38f, 72f, 287f), Box(-79f, 12f, 209f, 325f),
            0f, 0f, 0f, 1f, 1f, -22.58f, 260, EASE_STANDARD, 260, EASE_STANDARD, 0, 0, 0),
    )

    fun part(p: CarPart): Part = parts.getValue(p)

    /**
     * Cam vurgusu (4 Kasım §1, "camlara hafif renk"): açık camın üstüne hafif buz mavisi. Kutular base pikselinde, kapı
     * kapalıyken görünen yan camın yeri (üst kenar aracın solu); [door] açıldıkça vurgu söner (cam kapıyla birlikte döner).
     */
    class Glass(val box: Box, val door: CarPart?, val strength: Float = 1f)

    const val GLASS_MAX_ALPHA = 0.32f
    const val GLASS_CORNER = 8f
    const val GLASS_FADE_MS = 280L

    internal val windows: Map<BodyPart, Glass> = mapOf(
        BodyPart.WINDOW_LF to Glass(Box(352f, 47f, 128f, 22f), CarPart.DOOR_FL),
        BodyPart.WINDOW_LR to Glass(Box(205f, 47f, 126f, 22f), CarPart.DOOR_RL),
        BodyPart.WINDOW_RF to Glass(Box(352f, 286f, 128f, 22f), CarPart.DOOR_FR),
        BodyPart.WINDOW_RR to Glass(Box(205f, 286f, 126f, 22f), CarPart.DOOR_RR),
    )

    /** Cam tavanın camı (tavan açıkken aynı renk; geniş yüzey olduğundan daha hafif). */
    val sunroof = Glass(Box(163f, 96f, 268f, 156f), null, strength = 0.6f)

    // --- Panoramik tavan ([RoofState]; katmanlar car_roof_*.webp, base pikselinin 2 katı) ---

    /**
     * Tavan camının dış kutusu (base pikseli; car_*_base'deki ince açık kenar çizgisinin içi). Arka panel solda
     * ([ROOF_X0]..[ROOF_SPLIT]), ön panel sağda ([ROOF_FRONT_X0]..[ROOF_X1]); burun sağda olduğundan "ön" = sağ.
     */
    const val ROOF_X0 = 162f
    const val ROOF_X1 = 438f
    const val ROOF_Y0 = 95f
    const val ROOF_Y1 = 254f
    const val ROOF_SPLIT = 291f
    const val ROOF_FRONT_X0 = 293f
    /** Katman bitmap'lerinin ölçeği (base pikseli başına piksel). */
    const val ROOF_ART_SCALE = 2f
    /** Ön panelin tam açıkken arkaya kayma yolu (panel genişliğinin ~%82'si; geri kalanı çerçevenin altında kalır). */
    const val ROOF_GLASS_TRAVEL = 118f
    /** Duran tavanda yeni konum okununca kısa yumuşak geçiş ([RoofFollower]). */
    const val ROOF_EASE_MS = 420L
    /** Hareket durunca gösterilen konumun okunan değere yerleşme süresi. */
    const val ROOF_SETTLE_MS = 200L
    /** Hareket sürerken gösterilen konumun beklenen konuma yaklaşma zaman sabiti (adım bildirimi gelince yumuşak düzeltme). */
    const val ROOF_CORRECT_MS = 300L
    /** Kenar ışığının kutu payı ve yoğunluğu (sakin: en dolu yerinde ~%55 kehribar). */
    const val ROOF_EDGE_PAD = 9f
    const val ROOF_EDGE_ALPHA = 0.55f

    val roofW: Float get() = ROOF_X1 - ROOF_X0

    /** Perdenin açık/kapalı sınırı (base x): açıklık [shade] önden (sağ) arkaya doğru büyür. */
    fun shadeEdgeX(shade: Float): Float = ROOF_X1 - clamp01(shade) * roofW

    /** Ön panelin o anki yeri: (sol, sağ) base x; [glass] 0..1. Sağ kenar aynı zamanda açıklığın arka kenarıdır. */
    fun glassPanelX(glass: Float): FloatArray {
        val d = clamp01(glass) * ROOF_GLASS_TRAVEL
        return floatArrayOf(ROOF_FRONT_X0 - d, ROOF_X1 - d)
    }

    /** Açıklığın kutusu (sol, sağ) base x; kapalıyken genişlik 0. */
    fun roofHoleX(glass: Float): FloatArray = floatArrayOf(glassPanelX(glass)[1], ROOF_X1)

    /** Vurgunun saydamlığı: [fade] cam açılış geçişi (0 kapalı, 1 açık), [doorOpen] o camın kapısının açıklığı (0..1). */
    fun glassAlpha(fade: Float, doorOpen: Float): Float = GLASS_MAX_ALPHA * clamp01(fade) * (1f - clamp01(doorOpen))

    /** Açık parçalar base dışına taşar (kapılar yanlara, bagaj geriye); görünüm bu payları kapsar. Gölge bulanıklığı dahil. */
    val padLeft: Float = maxOf(0f, -parts.values.minOf { minOf(it.overlay.x, it.sprite.x + it.translateXEnd) })
    val padTop: Float = maxOf(0f, -parts.values.minOf { it.overlay.y }) + SHADOW_BLUR * 2
    val padRight: Float = maxOf(0f, parts.values.maxOf { it.overlay.right } - BASE_W)
    val padBottom: Float = maxOf(0f, parts.values.maxOf { it.overlay.bottom } - BASE_H) + SHADOW_BLUR * 2

    // --- Dijital ikiz ışıkları (§11) ---

    /** Sinyal ritmi: bir yanma + sönme süresi (~79 / dk, araç sinyaline yakın); döngü içindeki parlaklık [blinkLevel]. */
    const val BLINK_MS = 760L
    /** Döngüde yanışın bittiği ve sönüşün başladığı yer (0..1): ~270 ms yumuşak yanış, kısa tepe, ~380 ms yumuşak sönüş. */
    const val BLINK_RISE = 0.35f
    const val BLINK_FALL = 0.5f
    /** Sinyal kapanınca son yanığın sönme süresi. */
    const val TURN_OFF_MS = 220L
    /** Far / geri lambası açılıp kapanırken yumuşak geçiş. */
    const val LIGHT_FADE_MS = 180L
    /** Fren: basınca yumuşak yanış, bırakınca biraz daha yavaş sönüş. */
    const val BRAKE_IN_MS = 220L
    const val BRAKE_OUT_MS = 380L

    /** Sinyal döngüsünde [p] (0..1) anındaki parlaklık (0..1): yumuşak yükseliş, kısa tepe, yumuşak iniş; uçlarda 0. */
    fun blinkLevel(p: Float): Float = when {
        p <= 0f || p >= 1f -> 0f
        p < BLINK_RISE -> smooth(p, 0f, BLINK_RISE)
        p < BLINK_FALL -> 1f
        else -> 1f - smooth(p, BLINK_FALL, 1f)
    }
    /** Kilitlenince dörtlülerin tek yanışının yanık süresi. */
    const val HAZARD_FLASH_MS = 500L
    /** Boya parlaması: bu aralıkla bir kez, [GLINT_MS] sürer; arada hiçbir şey çizilmez. */
    const val GLINT_EVERY_MS = 60_000L
    const val GLINT_MS = 1_500L
    /**
     * Parlamanın adım aralığı (~12 kare/sn): bant çok yumuşak ve sönük olduğundan adımlar seçilmez; 60 kare/sn yerine beşte
     * bir iş (emülatörde ölçüldü, bkz. tasarim-kararlari §11 performans şartı).
     */
    const val GLINT_STEP_MS = 80L

    /**
     * Lastikler (base pikseli; üst kenar aracın solu, burun sağda), [TireDecode] sırasıyla: ön sol, ön sağ, arka sol, arka sağ.
     * Üstten görünüşte lastik gövdenin altındadır; çamurluk kemerinin en geniş yerinde (car_*_base silüetinden ölçüldü: arka
     * kemer x≈144..192, gövde kenarı y≈23 / 338; ön kemer x≈592..656, y≈26 / 337) gövde kenarından [TIRE_OUT] kadar taşar.
     * [TIRE_X] lastik merkezi, [TIRE_EDGE] o taraftaki gövde kenarı; lastik [TIRE_L] × [TIRE_W].
     */
    val TIRE_X = floatArrayOf(622f, 622f, 168f, 168f)
    val TIRE_EDGE = floatArrayOf(26f, 337f, 23f, 338f)
    const val TIRE_L = 92f
    const val TIRE_W = 26f
    const val TIRE_OUT = 11f

    /** Lastiğin base pikseli kutusu (sol, üst, sağ, alt); [i] [TireDecode] sırası. */
    fun tireBox(i: Int): FloatArray {
        val top = i % 2 == 0
        val y0 = if (top) TIRE_EDGE[i] - TIRE_OUT else TIRE_EDGE[i] + TIRE_OUT - TIRE_W
        return floatArrayOf(TIRE_X[i] - TIRE_L / 2f, y0, TIRE_X[i] + TIRE_L / 2f, y0 + TIRE_W)
    }

    /**
     * Yakıt kapağı: sağ arka çamurlukta (görünüşte alt kenar), arka kemerin arka yarısının üstünde, arka kapı dikişinin
     * (x≈172) gerisinde; gövde kenarına (y≈338) oturan küçük kapak. Taraf OEM kardeş model görsellerinden (sol yan görünüşte
     * arka çamurluk temiz) ve araç fotoğraflarından tahmin. TODO: göstergedeki yakıt pompası okunun yönüyle doğrula.
     */
    val FUEL_CAP = Box(127f, 321f, 26f, 11f)

    /** Kaput ışığı (motor suyu / akü uyarısı): kaputun ortası ve yarıçapları; gövde alfasıyla kırpılır. */
    const val HOOD_CX = 655f
    const val HOOD_CY = 178f
    const val HOOD_RX = 118f
    const val HOOD_RY = 132f

    // --- Eğri yardımcıları (anim.json formülleri) ---

    fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

    fun ramp(v: Float, a: Float, b: Float): Float = clamp01((v - a) / (b - a))

    fun smooth(v: Float, a: Float, b: Float): Float {
        val t = ramp(v, a, b)
        return t * t * (3f - 2f * t)
    }
}

/**
 * Tek parçanın hareketi (Android'den bağımsız, test edilebilir): [x] geometri ilerlemesi (0 kapalı .. 1 açık; açılışta
 * hafif taşar), [xf] üst görsele (açık hal fotoğrafı) geçiş. Yarıda tersine dönüş o anki değerlerden başlar; süre kalan
 * yola oranlanır (önizleme ile aynı: preview2.html `start`/`step`).
 */
class PartMotion(private val spec: CarAnimSpec.Part, private val openEase: (Float) -> Float, private val closeEase: (Float) -> Float) {
    var x = 0f
        private set
    var xf = 0f
        private set
    var target = 0
        private set
    private var x0 = 0f
    private var xf0 = 0f
    private var dur = 1L

    /** Animasyonsuz son hal (ekran açılışı). */
    fun snap(open: Boolean) {
        target = if (open) 1 else 0
        x = target.toFloat()
        xf = x
        x0 = x
        xf0 = xf
    }

    /** Yeni hedef; dönen değer bu geçişin toplam süresi (ms; ValueAnimator süresi). */
    fun start(open: Boolean): Long {
        target = if (open) 1 else 0
        x0 = x
        xf0 = xf
        val full = if (open) spec.openMs else spec.closeMs
        val left = Math.abs(target - x)
        dur = maxOf(100L, (full * (if (left > 0.001f) left else 1f)).toLong())
        return when {
            spec.kind == CarAnimSpec.Kind.SLIDE_FADE -> dur
            open -> maxOf(dur, xfadeStart() + spec.xfadeMs)
            else -> maxOf(dur, spec.closeXfadeMs)
        }
    }

    private fun xfadeStart(): Long = spec.xfadeStartMs * dur / spec.openMs

    /** Geçişin başından beri geçen süreyle değerleri günceller. */
    fun update(elapsedMs: Float) {
        val u = CarAnimSpec.clamp01(elapsedMs / dur)
        if (spec.kind == CarAnimSpec.Kind.SLIDE_FADE) {
            val f = if (target == 1) openEase(u) else closeEase(u)
            x = x0 + (target - x0) * f
            xf = x
            return
        }
        if (target == 1) {
            x = x0 + (1f - x0) * openEase(u)
            xf = xf0 + (1f - xf0) * CarAnimSpec.clamp01((elapsedMs - xfadeStart()) / spec.xfadeMs)
        } else {
            x = x0 * (1f - closeEase(u))
            xf = xf0 * (1f - CarAnimSpec.clamp01(elapsedMs / spec.closeXfadeMs))
        }
    }

    /** Gövdeden kesilmiş parça görselinin saydamlığı (açık hal fotoğrafı belirdikçe söner). */
    fun spriteAlpha(): Float = when (spec.kind) {
        CarAnimSpec.Kind.SLIDE_FADE -> 1f - CarAnimSpec.smooth(x, CarAnimSpec.TRUNK_SPRITE_FROM, 1f)
        else -> 1f - CarAnimSpec.smooth(xf, CarAnimSpec.SPRITE_FADE_FROM, 1f)
    }

    fun overlayAlpha(): Float = when (spec.kind) {
        CarAnimSpec.Kind.SLIDE_FADE -> CarAnimSpec.smooth(x, CarAnimSpec.TRUNK_OVERLAY_FROM, CarAnimSpec.TRUNK_OVERLAY_TO)
        else -> CarAnimSpec.clamp01(xf)
    }

    /** Parçanın gövdede bıraktığı boşluk (karanlık iç); parça görseli tamamen sönünce gizlenir. */
    fun gapAlpha(): Float = when (spec.kind) {
        CarAnimSpec.Kind.SLIDE_FADE -> CarAnimSpec.ramp(x, 0f, CarAnimSpec.TRUNK_GAP_TO)
        else -> if (spriteAlpha() < 0.001f) 0f else CarAnimSpec.clamp01(x / CarAnimSpec.GAP_FADE_UNTIL)
    }

    fun spriteVisible(): Boolean = x > 0.001f && spriteAlpha() > 0.001f
}

/** Kübik Bézier yumuşatma (CSS cubic-bezier / PathInterpolator ile aynı kontrol noktaları); testlerde ve JVM'de kullanılır. */
class CubicBezier(x1: Float, y1: Float, x2: Float, y2: Float) : (Float) -> Float {
    private val cx = 3f * x1
    private val bx = 3f * (x2 - x1) - cx
    private val ax = 1f - cx - bx
    private val cy = 3f * y1
    private val by = 3f * (y2 - y1) - cy
    private val ay = 1f - cy - by

    override fun invoke(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var t = x
        repeat(8) {
            val e = ((ax * t + bx) * t + cx) * t - x
            val d = (3f * ax * t + 2f * bx) * t + cx
            if (Math.abs(e) < 1e-5f || d == 0f) return@repeat
            t -= e / d
        }
        t = CarAnimSpec.clamp01(t)
        return ((ay * t + by) * t + cy) * t
    }

    companion object {
        fun of(p: FloatArray) = CubicBezier(p[0], p[1], p[2], p[3])
    }
}

/**
 * Dijital ikizin ışık durumu (§11): far, fren, geri vites, sol / sağ sinyal ve dörtlü anahtarı. Yalnızca okunan sinyaller
 * ([CarLightSignals]); okunamayan ya da doğrulanmamış olan false kalır (o ışık hiç yanmaz).
 */
data class CarLights(
    val head: Boolean = false,
    val brake: Boolean = false,
    val reverse: Boolean = false,
    val left: Boolean = false,
    val right: Boolean = false,
    /** Dörtlü anahtarı açık: iki taraf birlikte yanıp söner. */
    val hazard: Boolean = false,
) {
    val blinkLeft: Boolean get() = left || hazard
    val blinkRight: Boolean get() = right || hazard

    companion object {
        @JvmField val OFF = CarLights()

        /** Demo listesi (`--es launcher_demo_lights "head,brake,left,right,reverse,hazard"`); bilinmeyen ad yok sayılır. */
        fun parse(raw: String?): CarLights {
            val k = raw.orEmpty().split(',').map { it.trim().lowercase(java.util.Locale.ROOT) }.toSet()
            return CarLights("head" in k, "brake" in k, "reverse" in k, "left" in k, "right" in k, "hazard" in k)
        }
    }
}

/**
 * Işık sinyallerinin değerleri (ReadOnly modülü). null = değer araçta doğrulanmadı, sinyal okunmaz ve o ışık kapalı kalır
 * (karar §11: "fren / geri vites okunamazsa o kısım kapalı kalır; araçta doğrulanır"). Okuma yalnızca yolculuk
 * okuyucusunun canlı katmanında ([TripSource], 1 sn; motor kapalı ve hız 0 iken 3 sn) yapılır; yeni bir yoklama yoktur.
 */
internal object CarLightSignals {
    /**
     * ID_PARKING_LAMP_STATE (1): 1 = lambalar açık. araç far testinde 1 → 0 → 1 değişti; yönün
     * (1 açık mı) ve kısa farla aynı olup olmadığı ayrıca doğrulanacak.
     */
    val HEAD_ON_VALUE: Int? = 1

    /**
     * ID_DIRECTION_IND_LEFT / RIGHT (35 / 34): 1 = sinyal açık. Araçta doğrulandı (2026-10-29 ve 2026-11-06): sinyal açık olduğu
     * sürece hep 1 (kol durumu; yanıp sönme ritmi yok), dörtlülerde ikisi birden 1. Ekrandaki ritim bizim ([CarAnimSpec.BLINK_MS]).
     */
    val TURN_ON_VALUE: Int? = 1

    /** ID_GEARBOX_STATE (26): R = 2 (ölçüldü; P = 1, N = 3, D = 5). */
    val REVERSE_GEAR_VALUE: Int? = 2

    /** ID_BRAKE_PEDAL (80): 1 = basılı, 0 = bırakılmış (araçta doğrulandı 2026-11-06). */
    val BRAKE_ON_VALUE: Int? = 1

    /**
     * ID_HAZARD_LIGHT (79): 1 = dörtlüler açık (araçta doğrulandı 2026-11-06; o sırada 35 ve 34 de 1 okunur). Selektör / uzun far
     * için değişen bir sinyal görülmedi, okunmaz.
     */
    val HAZARD_ON_VALUE: Int? = 1

    /** Ham değerlerden ışık durumu (saf). Değeri null olan sinyalin ham okuması yok sayılır. */
    fun decode(head: Int?, left: Int?, right: Int?, gear: Int?, brake: Int?, hazard: Int?): CarLights = CarLights(
        head = HEAD_ON_VALUE != null && head == HEAD_ON_VALUE,
        brake = BRAKE_ON_VALUE != null && brake == BRAKE_ON_VALUE,
        reverse = REVERSE_GEAR_VALUE != null && gear == REVERSE_GEAR_VALUE,
        left = TURN_ON_VALUE != null && left == TURN_ON_VALUE,
        right = TURN_ON_VALUE != null && right == TURN_ON_VALUE,
        hazard = HAZARD_ON_VALUE != null && hazard == HAZARD_ON_VALUE,
    )

    /** Yalnızca değeri bilinen sinyalleri okur ([ro]: ReadOnly kimliği → ham değer). */
    fun read(ro: (Int) -> Int?): CarLights = decode(
        head = HEAD_ON_VALUE?.let { ro(ReadOnlyID.ID_PARKING_LAMP_STATE) },
        left = TURN_ON_VALUE?.let { ro(ReadOnlyID.ID_DIRECTION_IND_LEFT) },
        right = TURN_ON_VALUE?.let { ro(ReadOnlyID.ID_DIRECTION_IND_RIGHT) },
        gear = REVERSE_GEAR_VALUE?.let { ro(ReadOnlyID.ID_GEARBOX_STATE) },
        brake = BRAKE_ON_VALUE?.let { ro(ReadOnlyID.ID_BRAKE_PEDAL) },
        hazard = HAZARD_ON_VALUE?.let { ro(ReadOnlyID.ID_HAZARD_LIGHT) },
    )
}
