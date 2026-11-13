package com.mapcontrol.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import com.mapcontrol.R

/**
 * Sahne'nin ortasındaki araç: kapalı gövde (base) ve her açılır parça için boşluk → parça → açık hal fotoğrafı katmanları
 * (`.impeccable/mocks/premium/doors`, B seçeneği). Kapılar ön menteşe etrafında döner ve kısalır (gölge + dış kenar
 * ışığıyla), sonra açık hal fotoğrafına geçer; kaput ön cama doğru basılır; bagaj geriye kayarak söner.
 *
 * Her parça kendi [ValueAnimator]'ıyla, anim.json'daki kübik eğrilerle ([PathInterpolator]) bağımsız oynar; yarıda
 * tersine dönen parça o anki halinden döner ([PartMotion]). Kare başına yalnızca matris + saydamlık değişir; bellek
 * ayrılmaz. Görseller renk başına bir kez çözülür (süreç boyunca tek renk takımı tutulur); renk değişince eskisi bırakılır.
 * Görünüm base'in taşan parçalarını ([CarAnimSpec.padLeft] …) da kapsar; araç gövdesi [carLeft]/[carTop]'tan başlar.
 *
 * Dijital ikiz (§11, [setLights]): far huzmesi, fren ışığı ve kırmızı zemin havuzu, geri lambası, sinyaller ve kilitlenince
 * dörtlülerin tek yanışı. Uyarıda ilgili parçanın kendisi yanar (halka / rozet yok): düşük lastikte o lastik gövde kenarının
 * altından kehribar parlar ([setTireAlert]), yakıt kapağı açıkken kapak yerinde, motor suyu / akü uyarısında kaput üstünde
 * hafif kehribar ([setPartAlerts]); arabanın altında çok hafif yansıma
 * ve ~60 sn'de bir 1,5 sn'lik boya parlaması. Hepsi önceden çizilmiş küçük maskelerdir (paylaşılan ALPHA_8 bitmap'ler) ve
 * yalnızca görünüm alfası / konumu canlanır: kare başına çizim kaydı ve bellek ayırma yoktur. Işık katmanları çocuk
 * görünümlerdir; zemindekiler gövdenin altında, lambalar üstünde çizilir. [setActive] false iken (ekran görünmezken)
 * yanıp sönme ve parlama durur.
 */
class CarStageView(context: Context) : ViewGroup(context) {

    /**
     * Bu görünümün modeli: kurulurken etkin olan. Ölçüler, görseller ve yerleşim hep bundan okunur; [CarModel.active] sonradan
     * (başka bir ekran kurulurken) değişse de görünüm kendi içinde tutarlı kalır.
     */
    val model: CarModel = CarModel.active
    private val geo = model.geometry

    private val parts = CarPart.values()
    private val specs = Array(parts.size) { geo.parts.getValue(parts[it]) }
    private val motions = Array(parts.size) { i ->
        val s = specs[i]
        val open = PathInterpolator(s.openEasing[0], s.openEasing[1], s.openEasing[2], s.openEasing[3])
        val close = PathInterpolator(s.closeEasing[0], s.closeEasing[1], s.closeEasing[2], s.closeEasing[3])
        PartMotion(s, open::getInterpolation, close::getInterpolation)
    }
    private val animators = arrayOfNulls<ValueAnimator>(parts.size)
    private var running = 0
        set(value) {
            val was = field > 0
            field = maxOf(0, value)
            if (was != field > 0) onAnimatingChanged?.invoke(field > 0)
        }

    /** Hareket başlayınca true, tüm parçalar durunca false (Sahne zeminini bu sürede donanım katmanında tutar). */
    var onAnimatingChanged: ((Boolean) -> Unit)? = null

    private var bitmaps: CarBitmaps? = null
    private val shadows = arrayOfNulls<Bitmap>(parts.size)
    private val rimPaints = arrayOfNulls<Paint>(parts.size)
    private val rimRect = RectF()

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    // --- Cam vurgusu: dört yan cam ve tavan camı ([CarAnimSpec.windows], [CarAnimSpec.sunroof]) ---
    private val glassKeys: List<BodyPart?> = geo.windows.keys.toList() + null
    private val glasses = glassKeys.map { if (it == null) geo.sunroof else geo.windows.getValue(it) }
    private val glassDoor = IntArray(glasses.size) { i -> glasses[i].door?.let { parts.indexOf(it) } ?: -1 }
    /** Kapısı aynalı ([CarAnimSpec.Part.mirror], Jaecoo 7 ön kapıları) camın vurgusu kapı sprite'ının üstüne çizilir; Tiggo'da hepsi false. */
    private val glassOverSprite = BooleanArray(glasses.size) { i -> glassDoor[i] >= 0 && specs[glassDoor[i]].mirror }
    private val hasMirrorDoor = glassOverSprite.any { it }
    private val glassFrom = FloatArray(glasses.size)
    private val glassNow = FloatArray(glasses.size)
    private val glassTarget = FloatArray(glasses.size)
    private var glassAnim: ValueAnimator? = null
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassRect = RectF()

    // --- Panoramik tavan ([setRoof]): perde ve ön cam panel, gerçek konumuyla ---
    private var roof: RoofState? = null
    private val shadeMotion = RoofFollower()
    private val glassMotion = RoofFollower()
    /**
     * Tavan kendi çocuk görünümünde (gövde görselinin hemen üstünde, cam ve parçaların altında çizilir): hareket sürerken her
     * karede yalnız bu katman yeniden kaydedilir, gövde / parçalar / ışıklar yeniden çizilmez.
     */
    private val roofLayer: View = object : View(context) {
        override fun hasOverlappingRendering(): Boolean = false
        override fun onDraw(canvas: Canvas) = drawRoofLayer(canvas, this)
    }.apply { visibility = GONE }
    private var roofArt: RoofArt? = null
    private val roofDst = RectF()
    private val roofPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val roofLine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val roofEdgePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = ROOF_AMBER }
    /** Kayan cam panel: yerel koordinatta (panel sol üstü 0,0) bir kez kurulan ton + çapraz parıltı. */
    private val roofPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val w = geo.roofX1 - geo.roofFrontX0
        val h = geo.roofY1 - geo.roofY0 - 2 * geo.roofGlassInsetY
        shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(0x47101826, 0x3D5A7896, 0x29101826, 0x38080C12),
            floatArrayOf(0f, 0.3f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    private val roofTick = Runnable { onRoofTick() }

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(CarColorPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == CarColorPrefs.KEY) setColor(CarColorPrefs.get(context))
    }

    // --- Işıklar (§11): zemindekiler gövdenin altında, lambalar üstünde ---
    private val beamTop = sprite(LightMasks.beam, HEAD_BEAM)
    private val beamBottom = sprite(LightMasks.beam, HEAD_BEAM)
    private val brakePool = sprite(if (geo.brakePoolSoftEdge) LightMasks.poolSoft else LightMasks.pool, BRAKE_POOL)
    private val reverseFloor = sprite(LightMasks.glow, REVERSE_FLOOR)
    private val reflection = LightSprite(context, null, 0xFFFFFFFF.toInt()).apply { alpha = REFLECTION_ALPHA }
    private val headTop = sprite(LightMasks.glow, HEAD_LAMP)
    private val headBottom = sprite(LightMasks.glow, HEAD_LAMP)
    /** Stop lambası: arka kenarın çizgisini izleyen ince ışık çubuğu (gövde silüetinden bir kez çizilir, [PartArt.tailBarOf]). */
    private val tail = sprite(null, 0xFFFFFFFF.toInt())
    /**
     * Yalnız ışık çubuğu bagaj kapağında olan modelde ([CarGeometry.tailGate], Jaecoo 7): çubuğun ortası kapakla gider, iki uç
     * parça gövdede kalır. O zaman [tail] orta, [tailEnds] uçlardır (aynı kutu, aynı alfa); diğer modellerde yoktur.
     */
    private val tailEnds: LightSprite? = if (geo.tailGate != null) sprite(null, 0xFFFFFFFF.toInt()) else null
    /** Aynı modelde kalkık kapağın üstündeki çubuk ([TailGate.openBox]): bagaj açıldıkça [tail] söner, bu belirir. */
    private val tailOpen: LightSprite? = if (geo.tailGate != null) sprite(null, 0xFFFFFFFF.toInt()) else null
    private val reverseTop = sprite(LightMasks.glow, REVERSE_LAMP)
    private val reverseBottom = sprite(LightMasks.glow, REVERSE_LAMP)
    /** Sinyaller yalnızca köşe lambalarında (ön, arka); aynada ışık yok. Küçük, yumuşak, düşük alfalı kehribar. */
    private val turnLeft = arrayOf(sprite(LightMasks.glow, TURN), sprite(LightMasks.glow, TURN))
    private val turnRight = arrayOf(sprite(LightMasks.glow, TURN), sprite(LightMasks.glow, TURN))
    /** Lastikler gövdenin altında (yalnızca kenardan taşan kısım ve ışıması görünür); [TireDecode] sırası. */
    private val tires = Array(4) { sprite(if (it % 2 == 0) PartArt.tire(model) else PartArt.tireFlipped(model), 0xFFFFFFFF.toInt()) }
    private val fuelCap = sprite(PartArt.fuelCap(model), 0xFFFFFFFF.toInt())
    private val hood = sprite(null, HOOD_GLOW)
    private val glint = GlintLayer(context)
    private val under: Array<View> = arrayOf(reflection, brakePool, reverseFloor, beamTop, beamBottom, *tires)
    private val over: Array<View> = arrayOf(tail, *listOfNotNull(tailEnds, tailOpen).toTypedArray(), headTop, headBottom, reverseTop, reverseBottom, *turnLeft, *turnRight, hood, fuelCap, glint)
    /** Çocukların base pikseli kutuları (gövdenin sol üstüne göre): sol, üst, sağ, alt. */
    private val boxes: Map<View, FloatArray> = mapOf(
        roofLayer to floatArrayOf(geo.roofX0 - ROOF_LAYER_PAD, geo.roofY0 - ROOF_LAYER_PAD,
            geo.roofX1 + ROOF_LAYER_PAD, geo.roofY1 + ROOF_LAYER_PAD),
        beamTop to geo.beamTop,
        beamBottom to geo.beamBottom,
        brakePool to geo.brakePool,
        reverseFloor to geo.reverseFloor,
        reflection to floatArrayOf(0f, geo.bodyBottom - 10f, CarAnimSpec.BASE_W, geo.bodyBottom + 86f),
        headTop to geo.headTop,
        headBottom to geo.headBottom,
        tail to geo.tail,
        reverseTop to geo.reverseTop,
        reverseBottom to geo.reverseBottom,
        turnLeft[0] to geo.turnLeftFront,
        turnLeft[1] to geo.turnLeftRear,
        turnRight[0] to geo.turnRightFront,
        turnRight[1] to geo.turnRightRear,
        tires[0] to PartArt.tireViewBox(geo, 0),
        tires[1] to PartArt.tireViewBox(geo, 1),
        tires[2] to PartArt.tireViewBox(geo, 2),
        tires[3] to PartArt.tireViewBox(geo, 3),
        fuelCap to PartArt.capViewBox(geo),
        hood to PartArt.hoodBox(geo),
        glint to floatArrayOf(0f, 0f, CarAnimSpec.BASE_W, CarAnimSpec.BASE_H),
    ) + listOfNotNull(tailEnds?.let { it to geo.tail }, tailOpen?.let { v -> geo.tailGate?.let { v to it.openBox } })
    private var lights = CarLights.OFF
    /** Ekran görünür ve pencereye bağlı: yanıp sönme ve parlama yalnızca bu sırada çalışır. */
    private var active = false
    private var wantActive = false
    /**
     * Sinyal ritmi: [CarAnimSpec.BLINK_MS]'lik döngüde yumuşak yanış ve sönüş ([CarAnimSpec.blinkLevel]); yalnızca iki köşe
     * lambasının görünüm alfası canlanır (kare başına çizim kaydı ve bellek ayırma yok). Sinyal açıkken döngü sürer, kapanınca
     * yumuşakça söner ve döngü durur. İki taraf aynı animatörden beslenir (dörtlü aynı ritimde). [flashing] kilitlenince tek döngü.
     */
    private var blinking = false
    private var flashing = false
    private val blinkAnim: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = CarAnimSpec.BLINK_MS
        interpolator = LinearInterpolator()
        addUpdateListener { setTurnLevel(CarAnimSpec.blinkLevel(it.animatedFraction)) }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (flashing && !blinking) {
                    flashing = false
                    hideTurns()
                }
            }
        })
    }

    private fun setTurnLevel(level: Float) {
        val l = lights
        val left = (blinking && l.left) || flashing
        val right = (blinking && l.right) || flashing
        for (t in turnLeft) t.alpha = if (left) level else 0f
        for (t in turnRight) t.alpha = if (right) level else 0f
    }

    private fun showTurns() {
        for (t in turnLeft) { t.animate().cancel(); t.visibility = VISIBLE }
        for (t in turnRight) { t.animate().cancel(); t.visibility = VISIBLE }
    }

    /** Sinyal kapandı: yanık köşe kısa sürede söner, sonra çizimden çıkar. */
    private fun hideTurns() {
        for (t in turnLeft + turnRight) {
            t.animate().cancel()
            if (t.visibility != VISIBLE || t.alpha == 0f) { t.alpha = 0f; t.visibility = GONE; continue }
            t.animate().alpha(0f).setDuration(CarAnimSpec.TURN_OFF_MS).withEndAction { t.visibility = GONE }.start()
        }
    }
    private val glintRun = Runnable { startGlint() }

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        for (v in under) addView(v)
        addView(roofLayer)
        for (v in over) addView(v)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        bitmaps = CarBitmaps.get(context, CarColorPrefs.get(context), model)
        bitmaps?.let { reflection.setMask(it.reflection); hood.setMask(it.hoodMask); tail.setMask(it.tailBar); tailEnds?.setMask(it.tailEnds); tailOpen?.setMask(it.tailOpen) }
        glint.mask = bitmaps?.base
        for (i in parts.indices) {
            val s = specs[i]
            val shadow = s.shadow ?: continue
            shadows[i] = shadowBitmap(shadow)
            val rim = s.rim ?: continue
            rimPaints[i] = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(rim.x - s.pivotX, 0f, rim.right - s.pivotX, 0f,
                    intArrayOf(0x00FFFFFF, 0xE6FFFFFF.toInt(), 0xE6FFFFFF.toInt(), 0x00FFFFFF),
                    floatArrayOf(0f, 0.25f, 0.8f, 1f), Shader.TileMode.CLAMP)
            }
        }
    }

    /** Ekrandaki ölçek: base pikselinden görünüm pikseline. */
    private val k: Float get() = width / (CarAnimSpec.BASE_W + geo.padLeft + geo.padRight)

    /** Verilen gövde genişliği için görünümün tam boyu ve gövdenin görünüm içindeki yeri (SahneStage yerleşimi); bu görünümün [model]ine göre. */
    fun widthFor(carW: Int): Int = (carW * (CarAnimSpec.BASE_W + geo.padLeft + geo.padRight) / CarAnimSpec.BASE_W).toInt()
    fun heightFor(carW: Int): Int = (carW * (CarAnimSpec.BASE_H + geo.padTop + geo.padBottom) / CarAnimSpec.BASE_W).toInt()
    fun carLeft(carW: Int): Int = (carW * geo.padLeft / CarAnimSpec.BASE_W).toInt()
    fun carTop(carW: Int): Int = (carW * geo.padTop / CarAnimSpec.BASE_W).toInt()

    /**
     * Açık parçalar. [animate] false ise (ekran açılışındaki ilk durum) doğrudan son hal çizilir; aksi halde değişen her
     * parça kendi hareketiyle açılır/kapanır, yarıda gelen ters istek o anki halinden döner.
     */
    fun setOpen(open: Set<CarPart>, animate: Boolean) {
        val anim = animate && isAttachedToWindow && isLaidOut
        for (i in parts.indices) {
            val want = parts[i] in open
            val m = motions[i]
            if ((m.target == 1) == want && (anim || animators[i] == null)) continue
            animators[i]?.let { it.removeAllListeners(); it.cancel(); animators[i] = null; running-- }
            if (!anim) {
                m.snap(want)
                continue
            }
            val total = m.start(want)
            animators[i] = ValueAnimator.ofFloat(0f, total.toFloat()).apply {
                duration = total
                interpolator = LinearInterpolator()
                addUpdateListener { a ->
                    m.update(a.animatedValue as Float)
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (animators[i] === animation) {
                            animators[i] = null
                            running--
                        }
                    }
                })
                running++
                start()
            }
        }
        invalidate()
    }

    /**
     * Açık camlar ve tavan: üstlerine hafif buz mavisi (4 Kasım §1). [animate] false ise doğrudan son hal; aksi halde kısa
     * bir geçişle belirir / söner.
     */
    internal fun setGlass(windows: Collection<BodyPart>, sunroof: Boolean, animate: Boolean) {
        var changed = false
        for (i in glasses.indices) {
            val want = if (glassKeys[i] == null) sunroof else glassKeys[i] in windows
            val t = if (want) 1f else 0f
            if (glassTarget[i] != t) changed = true
            glassTarget[i] = t
        }
        if (!changed && glassAnim == null) return
        glassAnim?.let { it.removeAllListeners(); it.cancel() }
        glassAnim = null
        if (!(animate && isAttachedToWindow && isLaidOut)) {
            glassTarget.copyInto(glassNow)
            invalidate()
            return
        }
        glassNow.copyInto(glassFrom)
        glassAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CarAnimSpec.GLASS_FADE_MS
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                for (i in glasses.indices) glassNow[i] = glassFrom[i] + (glassTarget[i] - glassFrom[i]) * f
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (glassAnim === animation) glassAnim = null
                }
            })
            start()
        }
    }

    /**
     * Panoramik tavan ([RoofState]; null = sinyal yok, taban görseli olduğu gibi). Perde kısmi konumda önden arkaya açılır,
     * ön panel açıklığı kadar arkaya kayar. Araç konumu adım adım bildirir (cam %10 / ≈ 0,9 sn, perde başladı / durdu);
     * çizim adımlara atlamaz, [RoofFollower] ile ölçülen hızda sürekli ilerler ve bildirim gelince yumuşak düzeltir. Kareler
     * yalnızca hareket sürerken istenir (Choreographer, [postOnAnimation]) ve yalnız tavan katmanı yeniden çizilir; durunca
     * hiçbir şey çizilmez. Aynı durum hiçbir şey yapmaz.
     */
    fun setRoof(state: RoofState?, animate: Boolean) {
        if (state == roof) return
        val now = android.os.SystemClock.uptimeMillis()
        val anim = animate && isAttachedToWindow && isLaidOut && ValueAnimator.areAnimatorsEnabled()
        val prev = roof
        roof = state
        if (state != null && roofArt == null) roofArt = RoofArt.get(context, model)
        RoofFeed.apply(shadeMotion, glassMotion, prev, state, anim, now)
        roofLayer.visibility = if (state != null) VISIBLE else GONE
        // Tavan var / yok değişince eski buz mavisi tavan camı da değişir (gövde katmanı); yoksa yalnız tavan katmanı.
        if ((prev == null) != (state == null)) invalidate() else roofLayer.invalidate()
        removeCallbacks(roofTick)
        if (anim) postOnAnimation(roofTick)
    }

    /** Tavan karesi: hareket sürdükçe bir sonraki kareyi ister; durunca biter (boşta kare yok). */
    private fun onRoofTick() {
        val now = android.os.SystemClock.uptimeMillis()
        shadeMotion.at(now)
        glassMotion.at(now)
        roofLayer.invalidate()
        if (shadeMotion.active(now) || glassMotion.active(now)) postOnAnimation(roofTick)
    }

    /** Renk takımını değiştirir (tercih dinleyicisi ve ayar panelinden); görseller hemen değişir. */
    fun setColor(color: CarColor) {
        if (bitmaps?.color == color && bitmaps?.model == model) return
        bitmaps = CarBitmaps.get(context, color, model)
        reflection.setMask(bitmaps?.reflection)
        hood.setMask(bitmaps?.hoodMask)
        tail.setMask(bitmaps?.tailBar)
        tailEnds?.setMask(bitmaps?.tailEnds)
        tailOpen?.setMask(bitmaps?.tailOpen)
        glint.mask = bitmaps?.base
        invalidate()
    }

    /**
     * Dijital ikiz ışıkları (§11). [head] far huzmesi ve far lambaları (stoplar hafif yanar); [brake] stoplar parlar ve
     * arkada kırmızı zemin havuzu; [reverse] geri lambası ve arkada beyaz zemin ışığı; [turnLeft] / [turnRight] o taraftaki
     * ön ve arka köşe lambasında yumuşak kehribar yanıp sönme (aynada yok; ikisi birlikte = dörtlü, aynı ritimde); [hazardFlash] dörtlülerin
     * tek yanışı (kilitlenince). Değişmeyen durum hiçbir şey yapmaz; ekran görünmezken yalnızca durum saklanır.
     */
    fun setLights(head: Boolean, brake: Boolean, reverse: Boolean, turnLeft: Boolean, turnRight: Boolean, hazardFlash: Boolean = false) {
        val next = CarLights(head = head, brake = brake, reverse = reverse, left = turnLeft, right = turnRight)
        if (next != lights) {
            lights = next
            applyLights(animate = isAttachedToWindow && isLaidOut)
        }
        if (hazardFlash) flashHazard()
    }

    fun setLights(l: CarLights) = setLights(l.head, l.brake, l.reverse, l.blinkLeft, l.blinkRight)

    /** Dörtlülerin tek yanışı (kilitlenince): bir sinyal döngüsü; görünmezken ya da sinyal zaten yanıp sönerken atlanır. */
    fun flashHazard() {
        if (!active || blinking) return
        flashing = true
        showTurns()
        blinkAnim.cancel()
        blinkAnim.repeatCount = 0
        blinkAnim.start()
    }

    /** Showroom zemini: arabanın altında çok hafif yansıma (Sahne; spor ekranında dönmüş araçta kapalı). Bir kez çizilir. */
    fun setFloorReflection(on: Boolean) {
        reflection.visibility = if (on) VISIBLE else GONE
    }

    private var tireWheel: Int? = null
    private val pulses = HashMap<View, Animator>(4)
    private var capOn = false
    private var hoodOn = false

    /**
     * Düşük lastik: o lastik gövde kenarının altından kehribar yanar ([wheel] [TireDecode] sırası; null ya da bilinmeyen =
     * hiçbiri). İlk çıkışta bir kez yavaş nabız, sonra sabit.
     */
    fun setTireAlert(wheel: Int?) {
        val next = wheel?.takeIf { it in 0..3 }
        if (next == tireWheel) return
        tireWheel = next
        for (i in tires.indices) showPart(tires[i], i == next, pulse = true)
    }

    /** Yakıt kapağı açık: kapak yerinde kehribar; [engine] motor suyu / akü uyarısı: kaput üstünde hafif kehribar (sabit). */
    fun setPartAlerts(fuelCapOpen: Boolean, engine: Boolean) {
        if (fuelCapOpen != capOn) {
            capOn = fuelCapOpen
            showPart(fuelCap, fuelCapOpen, pulse = true)
        }
        if (engine != hoodOn) {
            hoodOn = engine
            showPart(hood, engine, pulse = false)
        }
    }

    /** Parça ışığı yumuşakça belirir / söner (yalnızca görünüm alfası; donanım katmanı yalnız geçişte). */
    private fun showPart(v: View, on: Boolean, pulse: Boolean) {
        v.animate().cancel()
        pulses.remove(v)?.cancel()
        if (!(isAttachedToWindow && isLaidOut) || !ValueAnimator.areAnimatorsEnabled()) {
            v.alpha = if (on) 1f else 0f
            v.visibility = if (on) VISIBLE else GONE
            return
        }
        if (on) {
            v.visibility = VISIBLE
            if (pulse) {
                val a = android.animation.ObjectAnimator.ofFloat(v, View.ALPHA, v.alpha, 1f, PART_PULSE_LOW, 1f).apply {
                    duration = PART_PULSE_MS
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) { v.setLayerType(LAYER_TYPE_HARDWARE, null) }
                        override fun onAnimationEnd(animation: Animator) { v.setLayerType(LAYER_TYPE_NONE, null) }
                    })
                }
                pulses[v] = a
                a.start()
            } else {
                v.animate().alpha(1f).setDuration(PART_FADE_MS).withLayer().start()
            }
        } else {
            v.animate().alpha(0f).setDuration(PART_FADE_MS).withLayer().withEndAction { v.visibility = GONE }.start()
        }
    }

    /** Ekran görünür mü: false iken yanıp sönme, dörtlü ve parlama durur (durum korunur, görünür olunca sürer). */
    fun setActive(on: Boolean) {
        wantActive = on
        updateActive()
    }

    /** Görünüm (ya da bir atası, ör. spor ekranı açıkken Sahne) gizlenince hareket durur. */
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        shownNow = isVisible
        updateActive()
    }

    private var shownNow = true
    private var attached = false

    private fun updateActive() {
        val now = wantActive && attached && shownNow
        if (now == active) return
        active = now
        if (now) {
            applyLights(animate = false)
            scheduleGlint()
        } else {
            flashing = false
            stopBlink()
            removeCallbacks(glintRun)
            glint.stop()
        }
    }

    private fun applyLights(animate: Boolean) {
        val l = lights
        fade(beamTop, l.head, animate)
        fade(beamBottom, l.head, animate)
        fade(headTop, l.head, animate)
        fade(headBottom, l.head, animate)
        // Stop çubuğu: farla birlikte hafif (park lambası), frende tam; basınca yumuşak yanar, bırakınca daha yavaş söner.
        val brakeMs = if (l.brake) CarAnimSpec.BRAKE_IN_MS else CarAnimSpec.BRAKE_OUT_MS
        fade(tail, l.head || l.brake, animate, if (l.brake) 1f else TAIL_POSITION_ALPHA, brakeMs)
        tailEnds?.let { fade(it, l.head || l.brake, animate, if (l.brake) 1f else TAIL_POSITION_ALPHA, brakeMs) }
        tailOpen?.let { fade(it, l.head || l.brake, animate, if (l.brake) 1f else TAIL_POSITION_ALPHA, brakeMs) }
        fade(brakePool, l.brake, animate, 1f, brakeMs)
        fade(reverseTop, l.reverse, animate)
        fade(reverseBottom, l.reverse, animate)
        fade(reverseFloor, l.reverse, animate)
        if (!active) return
        val want = l.left || l.right
        if (want && !blinking) {
            blinking = true
            flashing = false
            showTurns()
            blinkAnim.cancel()
            blinkAnim.repeatCount = ValueAnimator.INFINITE
            blinkAnim.start()
        } else if (!want && blinking) {
            stopBlink()
        } else if (want) {
            // Taraf değişti (ör. sol → dörtlü): döngü sürer, yeni taraf aynı fazda yanar.
            setTurnLevel(CarAnimSpec.blinkLevel(blinkAnim.animatedFraction))
        }
    }

    private fun stopBlink() {
        blinking = false
        blinkAnim.cancel()
        hideTurns()
    }

    /** Lamba / huzme yumuşakça yanar ya da söner; söndüğünde çizimden çıkar (GONE). */
    private fun fade(v: View, on: Boolean, animate: Boolean, level: Float = 1f, ms: Long = CarAnimSpec.LIGHT_FADE_MS) {
        val target = if (on) level else 0f
        v.animate().cancel()
        if (!animate) {
            v.alpha = target
            v.visibility = if (on) VISIBLE else GONE
            return
        }
        if (on) v.visibility = VISIBLE
        if (v.visibility != VISIBLE || v.alpha == target) {
            if (!on) v.visibility = GONE
            return
        }
        v.animate().alpha(target).setDuration(ms).setInterpolator(if (on) FADE_IN_EASE else FADE_OUT_EASE)
            .withEndAction { if (!on) v.visibility = GONE }.start()
    }

    private fun scheduleGlint() {
        removeCallbacks(glintRun)
        if (active) postDelayed(glintRun, CarAnimSpec.GLINT_EVERY_MS)
    }

    private fun startGlint() {
        if (!active) return
        // Kapı hareketi sürerken atlanır (iki hareket üst üste binmesin).
        if (running == 0 && width > 0) glint.play()
        scheduleGlint()
    }

    private fun sprite(mask: Bitmap?, color: Int) = LightSprite(context, mask, color)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        val k = measuredWidth / (CarAnimSpec.BASE_W + geo.padLeft + geo.padRight)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val b = boxes[c] ?: continue
            c.measure(MeasureSpec.makeMeasureSpec(((b[2] - b[0]) * k).toInt().coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(((b[3] - b[1]) * k).toInt().coerceAtLeast(0), MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val k = k
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val bx = boxes[c] ?: continue
            val x = ((bx[0] + geo.padLeft) * k).toInt()
            val y = ((bx[1] + geo.padTop) * k).toInt()
            c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
        }
    }

    /** Çocuklar dokunuş almaz: araç dokunuşu (detay) görünümün kendisinde. */
    override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean = true

    /** Sıra: zemin ışıkları ve yansıma → gövde ve açılan parçalar → lambalar, halka ve parlama. */
    override fun dispatchDraw(canvas: Canvas) {
        val t = drawingTime
        for (v in under) if (v.visibility == VISIBLE) drawChild(canvas, v, t)
        drawCar(canvas)
        for (v in over) if (v.visibility == VISIBLE) drawChild(canvas, v, t)
    }

    /**
     * Karşılamada araç belirirken true: görünüm alfası ekran dışı katman açmadan her çizime ayrı uygulanır (katman görünüm
     * kutusuyla sınırlı olur, kutudan taşan far huzmesini ve fren havuzunu keserdi).
     */
    var flatAlpha = false

    override fun hasOverlappingRendering(): Boolean = !flatAlpha

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        setColor(CarColorPrefs.get(context))
        attached = true
        updateActive()
    }

    override fun onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        for (i in animators.indices) {
            animators[i]?.let { it.removeAllListeners(); it.end() }
            animators[i] = null
        }
        // Son hal korunur: kalan hareket bitmiş sayılır.
        motions.forEach { it.snap(it.target == 1) }
        glassAnim?.let { it.removeAllListeners(); it.cancel() }
        glassAnim = null
        glassTarget.copyInto(glassNow)
        removeCallbacks(roofTick)
        roof?.let { r -> r.shade?.let { shadeMotion.snap(it) }; glassMotion.snap(r.glass) }
        running = 0
        // isAttachedToWindow burada hâlâ true (mAttachInfo süper çağrıdan sonra boşalır): ayrı bayrakla kapatılır ki
        // göz kırpma/parlama geri çağrıları işleyiciden hâlâ kaldırılabilirken kaldırılsın.
        attached = false
        updateActive()
        super.onDetachedFromWindow()
    }

    private fun drawCar(canvas: Canvas) {
        val b = bitmaps ?: return
        val k = k
        if (k <= 0f) return
        val save = canvas.save()
        canvas.scale(k, k)
        canvas.translate(geo.padLeft, geo.padTop)
        paint.alpha = 255
        geo.tailGate?.let { g ->
            // Çubuğun ortası kapak görseline bağlıdır: kapalı görsel kayıp sönerken çubuk onunla kayar ve söner, açık görsel belirirken
            // kalkık kapağın çizgisindeki çubuk belirir; uçlar gövdede kalır. Geri lambaları açık kapağın altında söner, zemin ışığı kayar.
            val m = motions[CarPart.TRUNK.ordinal]
            val open = m.overlayAlpha()
            tail.translationX = specs[CarPart.TRUNK.ordinal].translateXEnd * k * m.x
            tail.gain = m.spriteAlpha() * (1f - open)
            tailOpen?.gain = open
            reverseTop.gain = 1f - open
            reverseBottom.gain = 1f - open
            reverseFloor.translationX = g.reverseDx * k * open
            if (g.brakeDx != 0f) brakePool.translationX = g.brakeDx * k * open
        }
        b.base?.let { canvas.drawBitmap(it, 0f, 0f, paint) }
        if (roofLayer.visibility == VISIBLE) {
            // Tavan kendi katmanında (hareket sırasında yalnız o yeniden çizilir); gövdenin hemen üstünde.
            canvas.restoreToCount(save)
            drawChild(canvas, roofLayer, drawingTime)
            canvas.save()
            canvas.scale(k, k)
            canvas.translate(geo.padLeft, geo.padTop)
        }
        drawGlass(canvas, overSprite = false)
        // 1) Boşluklar (tüm parçalar), 2) parça + gölge + kenar ışığı, 3) açık hal fotoğrafları (anim.json zOrder).
        for (i in parts.indices) {
            val a = motions[i].gapAlpha()
            if (a <= 0.001f) continue
            val g = specs[i].gap
            drawAt(canvas, b.gap[i], g.x, g.y, a)
        }
        for (i in parts.indices) drawSprite(canvas, i, b)
        // Aynalı kapının sprite'ı kapalıyken de çizilir ve camı örter: o kapıların cam vurgusu sprite'ın üstüne çizilir.
        if (hasMirrorDoor) drawGlass(canvas, overSprite = true)
        for (i in parts.indices) {
            val a = motions[i].overlayAlpha()
            if (a <= 0.001f) continue
            val o = specs[i].overlay
            drawAt(canvas, b.overlay[i], o.x, o.y, a)
        }
        canvas.restoreToCount(save)
    }

    /** Açık camların hafif rengi: gövdenin hemen üstünde (açılan kapının boşluğu ve parçası üstüne biner). */
    private fun drawGlass(canvas: Canvas, overSprite: Boolean) {
        glassPaint.color = Sahne.ICE
        for (i in glasses.indices) {
            if (glassOverSprite[i] != overSprite) continue
            // Tavanın konumu biliniyorsa cam tavan kendi katmanlarıyla çizilir (buz mavisi yalnız yedek).
            if (glassKeys[i] == null && roof != null) continue
            val d = glassDoor[i]
            val a = CarAnimSpec.glassAlpha(glassNow[i], if (d >= 0) motions[d].x else 0f) * glasses[i].strength
            if (a <= 0.001f) continue
            val g = glasses[i].box
            glassPaint.alpha = (255 * a).toInt().coerceIn(0, 255)
            glassRect.set(g.x, g.y, g.right, g.bottom)
            canvas.drawRoundRect(glassRect, CarAnimSpec.GLASS_CORNER, CarAnimSpec.GLASS_CORNER, glassPaint)
            glasses[i].box2?.let {
                glassRect.set(it.x, it.y, it.right, it.bottom)
                canvas.drawRoundRect(glassRect, CarAnimSpec.GLASS_CORNER, CarAnimSpec.GLASS_CORNER, glassPaint)
            }
        }
    }

    /** Tavan katmanının çizimi: gövdeyle aynı ölçek ve konum (katmanın tam sayıya yuvarlanmış yeri geri alınır). */
    private fun drawRoofLayer(canvas: Canvas, layer: View) {
        val k = k
        if (k <= 0f) return
        val save = canvas.save()
        canvas.translate(-layer.left.toFloat(), -layer.top.toFloat())
        canvas.scale(k, k)
        canvas.translate(geo.padLeft, geo.padTop)
        drawRoof(canvas)
        canvas.restoreToCount(save)
    }

    /**
     * Tavan: perde (kapalı kumaş | açık cam, sınır [CarAnimSpec.shadeEdgeX]; sınırda ince perde kenarı), sonra açıklık
     * (kabin, çerçevenin önünden), üstünde arkaya kaymış ön panel (ince cam levha: ton + parıltı, altı görünür) ve önündeki ince koyu
     * aralık; havalandırmada arka kenarda ince aralık. Tavan açık + hız / yağmur: açıklığın kenarı hafif kehribar.
     */
    private fun drawRoof(canvas: Canvas) {
        val r = roof ?: return
        val art = roofArt ?: return
        val now = android.os.SystemClock.uptimeMillis()
        val x0 = geo.roofX0
        val x1 = geo.roofX1
        val y0 = geo.roofY0
        val y1 = geo.roofY1
        // Camın kendisi: katman kutusunun içinde [CarGeometry.roofGlassInsetY] kadar içeride (0 ise kutunun kendisi); kayan panel ve
        // kenar çizgileri bunun dışına taşmaz.
        val gy0 = geo.roofGlassY0
        val gy1 = geo.roofGlassY1
        roofPaint.alpha = 255
        if (r.shade != null) {
            val xs = x1 - CarAnimSpec.clamp01(shadeMotion.at(now)) * (x1 - x0)
            roofDst.set(x0, y0, x1, y1)
            if (xs > x0 + 0.25f) art.closed?.let { clipped(canvas, it, x0, xs) }
            if (xs < x1 - 0.25f) art.open?.let { clipped(canvas, it, xs, x1) }
            if (xs > x0 + 0.5f && xs < x1 - 0.5f) {
                // Perdenin ön kenarı: ince koyu gölge + üstünde çok ince açık çizgi (kumaşın sert kenarı).
                roofLine.shader = null
                roofLine.color = 0x8C000000.toInt()
                canvas.drawRect(xs - 0.5f, gy0 + 2f, xs + 1.6f, gy1 - 2f, roofLine)
                roofLine.color = 0x33FFFFFF
                canvas.drawRect(xs - 1.4f, gy0 + 2f, xs - 0.5f, gy1 - 2f, roofLine)
            }
        }
        val g = glassMotion.at(now)
        if (g > 0.003f) {
            // [CarAnimSpec.glassPanelX] / [CarAnimSpec.roofHoleX] ile aynı (çizimde dizi ayrılmasın).
            val d = CarAnimSpec.clamp01(g) * geo.roofGlassTravel
            val pl = geo.roofFrontX0 - d
            val pr = x1 - d
            // Açıklık: kabin görünür (katman ön panelin tam yerini kaplar, açılan kısmı kırpılır).
            art.hole?.let {
                roofDst.set(geo.roofFrontX0, y0, x1, y1)
                val save = canvas.save()
                canvas.clipRect(pr, y0, x1, y1)
                canvas.drawBitmap(it, null, roofDst, roofPaint)
                canvas.restoreToCount(save)
            }
            // Kayan panel: arka panelin üstünde ince bir cam levha (altı görünür kalır): ton + çapraz gökyüzü parıltısı,
            // arkasında hafif gölge; çerçevenin dışına taşmaz.
            val save = canvas.save()
            canvas.clipRect(x0, gy0, x1, gy1)
            roofLine.shader = null
            roofLine.color = 0x59000000
            canvas.drawRect(pl - 4f, gy0 + 3f, pl, gy1 - 3f, roofLine)
            canvas.translate(pl, gy0)
            roofDst.set(0f, 0f, pr - pl, gy1 - gy0)
            canvas.drawRoundRect(roofDst, PANEL_CORNER, PANEL_CORNER, roofPanelPaint)
            canvas.restoreToCount(save)
            roofLine.color = 0xB3000000.toInt()
            canvas.drawRect(pr - 0.6f, gy0 + 3f, pr + 1.8f, gy1 - 3f, roofLine)
            roofLine.color = 0x2EFFFFFF
            canvas.drawRect(pr - 1.6f, gy0 + 4f, pr - 0.6f, gy1 - 4f, roofLine)
        }
        if (r.tilt) {
            // Havalandırma: ön panelin arka kenarı kalkık, ince koyu aralık.
            val gx = geo.roofFrontX0
            roofLine.color = 0xD9000000.toInt()
            canvas.drawRect(gx - 1f, gy0 + 3f, gx + 3.5f, gy1 - 3f, roofLine)
            roofLine.color = 0x47FFFFFF
            canvas.drawRect(gx + 3.5f, gy0 + 4f, gx + 4.6f, gy1 - 4f, roofLine)
        }
        if (r.edgeAlert && (g > 0.003f || r.tilt)) {
            val edge = art.edge ?: return
            val pad = CarAnimSpec.ROOF_EDGE_PAD
            val open = g > 0.003f
            val hl = if (open) x1 - CarAnimSpec.clamp01(g) * geo.roofGlassTravel else geo.roofFrontX0 - 3f
            val hr = if (open) geo.roofGlassX1 else geo.roofFrontX0 + 6f
            roofDst.set(hl - pad, gy0 - pad, hr + pad, gy1 + pad)
            roofEdgePaint.alpha = (255 * CarAnimSpec.ROOF_EDGE_ALPHA).toInt()
            canvas.drawBitmap(edge, null, roofDst, roofEdgePaint)
        }
    }

    /** Tavan katmanını tam kutusuna yerleştirip yalnız [left]..[right] arasını çizer. */
    private fun clipped(canvas: Canvas, bmp: Bitmap, left: Float, right: Float) {
        val save = canvas.save()
        canvas.clipRect(left, geo.roofY0, right, geo.roofY1)
        roofDst.set(geo.roofX0, geo.roofY0, geo.roofX1, geo.roofY1)
        canvas.drawBitmap(bmp, null, roofDst, roofPaint)
        canvas.restoreToCount(save)
    }

    private fun drawSprite(canvas: Canvas, i: Int, b: CarBitmaps) {
        val m = motions[i]
        if (!m.spriteVisible()) return
        val s = specs[i]
        val so = m.spriteAlpha()
        val x = m.x
        val c = CarAnimSpec.clamp01(x)
        val save = canvas.save()
        when (s.kind) {
            CarAnimSpec.Kind.HINGE -> {
                canvas.translate(s.pivotX, s.pivotY)
                canvas.rotate(s.rotationEnd * x)
                canvas.scale(1f + (s.scaleXEnd - 1f) * c, 1f + (s.scaleYEnd - 1f) * c)
                val shadow = s.shadow
                val sh = shadows[i]
                if (shadow != null && sh != null) {
                    drawAt(canvas, sh, shadow.x - s.pivotX - SHADOW_PAD, shadow.y - s.pivotY - SHADOW_PAD, CarAnimSpec.SHADOW_MAX_ALPHA * c * so)
                }
                drawAt(canvas, b.sprite[i], s.sprite.x - s.pivotX, s.sprite.y - s.pivotY, so)
                val rim = s.rim
                val rp = rimPaints[i]
                if (rim != null && rp != null) {
                    rp.alpha = (255 * CarAnimSpec.RIM_MAX_ALPHA * c * so).toInt().coerceIn(0, 255)
                    rimRect.set(rim.x - s.pivotX, rim.y - s.pivotY, rim.right - s.pivotX, rim.bottom - s.pivotY)
                    canvas.drawRoundRect(rimRect, 1f, 1f, rp)
                }
            }
            CarAnimSpec.Kind.LID -> {
                // Kaput: ön cama doğru basılıp kısalır (ölçek kaput kenarındaki pivot etrafında, taşma dahil).
                canvas.translate(s.pivotX + s.translateXEnd * x, s.pivotY)
                canvas.scale(1f + (s.scaleXEnd - 1f) * x, 1f + (s.scaleYEnd - 1f) * x)
                drawAt(canvas, b.sprite[i], s.sprite.x - s.pivotX, s.sprite.y - s.pivotY, so)
            }
            CarAnimSpec.Kind.SLIDE_FADE -> {
                canvas.translate(s.translateXEnd * x, 0f)
                drawAt(canvas, b.sprite[i], s.sprite.x, s.sprite.y, so)
            }
        }
        canvas.restoreToCount(save)
    }

    private fun drawAt(canvas: Canvas, bmp: Bitmap?, x: Float, y: Float, alpha: Float) {
        bmp ?: return
        paint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
        canvas.drawBitmap(bmp, x, y, paint)
    }

    /** Kapı gölgesi: bulanık yuvarlak dikdörtgen bir kez çizilir (bulanıklık asla canlandırılmaz). */
    private fun shadowBitmap(r: CarAnimSpec.Box): Bitmap? = try {
        val w = (r.w + 2 * SHADOW_PAD).toInt() + 1
        val h = (r.h + 2 * SHADOW_PAD).toInt() + 1
        Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8).also { bmp ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF000000.toInt()
                maskFilter = BlurMaskFilter(SHADOW_BLUR_RADIUS, BlurMaskFilter.Blur.NORMAL)
            }
            Canvas(bmp).drawRoundRect(RectF(SHADOW_PAD, SHADOW_PAD, SHADOW_PAD + r.w, SHADOW_PAD + r.h),
                CarAnimSpec.SHADOW_CORNER, CarAnimSpec.SHADOW_CORNER, p)
        }
    } catch (e: OutOfMemoryError) {
        null
    }

    /** Bir renk takımının çözülmüş görselleri (parça sırası [CarPart.values]). */
    private class CarBitmaps(
        val model: CarModel,
        val color: CarColor,
        val base: Bitmap?,
        val sprite: Array<Bitmap?>,
        val gap: Array<Bitmap?>,
        val overlay: Array<Bitmap?>,
        /** Modelin grafit takımından bir görsel (yalnız gerekince çözülür): LED çizgisinin yeri boyadan bağımsız okunsun diye. */
        private val grafit: (String) -> Bitmap?,
    ) {
        /** Zemindeki yansıma: gövdenin alt kenarının küçük, ters ve aşağı doğru sönen kopyası (renk başına bir kez). */
        val reflection: Bitmap? = base?.let { reflectionOf(it, model.geometry.bodyBottom) }
        /** Kaput ışığı maskesi: gövde alfası × yumuşak elips (renk başına bir kez, küçük ALPHA_8). */
        val hoodMask: Bitmap? by lazy { base?.let { PartArt.hoodMaskOf(it, model.geometry) } }
        /** Stop çubuğu: arka kenarın çizgisini izleyen ince kırmızı ışık (renk başına bir kez, küçük ARGB). */
        val tailBar: Bitmap? get() = if (model.geometry.tailGate == null) plainTailBar else gateBars[0]
        /** Çubuğun gövdede kalan uç parçaları (yalnız [CarGeometry.tailGate] olan modelde; o zaman [tailBar] yalnız ortadır). */
        val tailEnds: Bitmap? get() = if (model.geometry.tailGate == null) null else gateBars[1]
        /** Kalkık kapağın üstündeki çubuk ([TailGate.openBox]; yalnız o modelde). */
        val tailOpen: Bitmap? get() = if (model.geometry.tailGate == null) null else gateBars[2]

        private val plainTailBar: Bitmap? by lazy { base?.let { PartArt.tailBarOf(it, model.geometry) } }

        /**
         * [CarGeometry.tailGate] olan modelde çubukların yeri görsellerdeki kırmızı LED çizgisinden okunur. Kırmızı boyalı takımda boya
         * da kırmızı olduğundan okuma boyaya kayıyordu (çubuk iki yerde kopuyordu); çizginin yeri renkten bağımsızdır, o yüzden hep
         * grafit takımdan okunur (bir kez; grafit değilse okunan görseller sonra bırakılır). Sıra: orta, uçlar, kalkık kapak.
         */
        private val gateBars: Array<Bitmap?> by lazy {
            val g = model.geometry
            val gate = g.tailGate ?: return@lazy arrayOfNulls<Bitmap>(3)
            val own = color == CarColor.GRAFIT
            val ref = if (own) base else grafit("base")
            val trunk = g.parts.getValue(CarPart.TRUNK)
            val refOpen = if (own) overlay[CarPart.TRUNK.ordinal] else grafit("overlay_" + CarPart.TRUNK.asset)
            val bars = arrayOf(
                ref?.let { PartArt.tailBarOf(it, g) },
                ref?.let { PartArt.tailBarOf(it, g, centre = false) },
                refOpen?.let { PartArt.gateOpenBarOf(it, trunk.overlay, gate) },
            )
            if (!own) { ref?.recycle(); refOpen?.recycle() }
            bars
        }

        companion object {
            /** Süreç boyunca yalnızca son kullanılan model ve renk tutulur; tema değişimiyle yeniden kurulan ekran yeniden çözmez. */
            @Volatile private var cached: CarBitmaps? = null

            fun get(context: Context, color: CarColor, model: CarModel): CarBitmaps {
                cached?.takeIf { it.model == model && it.color == color }?.let { return it }
                val res = context.resources
                // Kaynak tablosunun paket adı (launcher test APK'sında uygulama kimliğinden farklı olabilir).
                val pkg = res.getResourcePackageName(R.drawable.car_grafit_base)
                fun load(name: String, key: String = color.key): Bitmap? = try {
                    val id = res.getIdentifier("${model.prefix}${key}_$name", "drawable", pkg)
                    if (id == 0) null else BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false })
                } catch (e: OutOfMemoryError) {
                    null
                }
                val all = CarPart.values()
                return CarBitmaps(
                    model,
                    color,
                    load("base"),
                    Array(all.size) { load("sprite_" + all[it].asset) },
                    Array(all.size) { load("gap_" + all[it].asset) },
                    Array(all.size) { load("overlay_" + all[it].asset) },
                    { name -> load(name, CarColor.GRAFIT.key) },
                ).also { cached = it }
            }
        }
    }
}

/**
 * Tavan katmanları (renkten bağımsız; yalnız cam alanı, kenarlar saydam): [closed] perde kapalı kumaş, [open] perde açık
 * cam + hafif gökyüzü yansıması, [hole] ön panel açıkken görünen kabin (ön panelin yeri). Kaynak: gpt-image ile mevcut
 * araba görselinin tavanı üzerinde düzenlendi. [edge] kenar ışığı maskesi (ALPHA_8).
 * Süreç başına (ve model başına) bir kez, yalnızca ilk tavan durumu gelince çözülür (~1,8 MB).
 */
private class RoofArt(val model: CarModel, val closed: Bitmap?, val open: Bitmap?, val hole: Bitmap?) {
    val edge: Bitmap? by lazy { edgeMask(model.geometry) }

    companion object {
        @Volatile private var cached: RoofArt? = null

        fun get(context: Context, model: CarModel): RoofArt {
            cached?.takeIf { it.model == model }?.let { return it }
            val res = context.resources
            fun load(id: Int): Bitmap? = try {
                BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false })
            } catch (e: OutOfMemoryError) {
                null
            }
            return RoofArt(model, load(model.roofClosed), load(model.roofOpen), load(model.roofHole))
                .also { cached = it }
        }

        /** Açıklığın çevresinde yumuşak ışıma: bulanık ince kontur, tam açık açıklık boyunda (kısmi açıklıkta yatay gerilir). */
        private fun edgeMask(geo: CarGeometry): Bitmap? = try {
            val pad = CarAnimSpec.ROOF_EDGE_PAD
            val w = geo.roofGlassTravel + 2 * pad
            val h = geo.roofGlassY1 - geo.roofGlassY0 + 2 * pad
            Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ALPHA_8).also { bmp ->
                val c = Canvas(bmp)
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2.4f
                    color = 0xFF000000.toInt()
                    maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
                }
                c.drawRoundRect(RectF(pad, pad, w - pad, h - pad), 10f, 10f, p)
                p.maskFilter = null
                p.strokeWidth = 1f
                p.color = 0xB3000000.toInt()
                c.drawRoundRect(RectF(pad, pad, w - pad, h - pad), 10f, 10f, p)
            }
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}

/** Tavan kenar ışığı: sakin kehribar (lastik / yakıt kapağıyla aynı aile). */
private const val ROOF_AMBER = 0xFFFFA040.toInt()
/** Kayan panelin köşe yarıçapı (base px; çerçevenin iç köşesiyle uyumlu, biraz daha küçük). */
private const val PANEL_CORNER = 8f
/** Tavan katmanının cam kutusundan taşma payı (base px; kenar ışığı [CarAnimSpec.ROOF_EDGE_PAD] içinde kalır). */
private const val ROOF_LAYER_PAD = 12f

/** Yansıma bitmap'i: base'in 1/4 genişliğinde, gövdenin alt kenarından ([bodyBottom]) [REFL_H] satır; üstte %40, aşağı doğru sıfıra söner. */
private fun reflectionOf(base: Bitmap, bodyBottom: Float): Bitmap? = try {
    val s = REFL_W / CarAnimSpec.BASE_W
    Bitmap.createBitmap(REFL_W.toInt(), REFL_H, Bitmap.Config.ARGB_8888).also { out ->
        val c = Canvas(out)
        c.save()
        c.scale(s, -s)
        c.translate(0f, -bodyBottom)
        c.drawBitmap(base, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        c.restore()
        c.drawRect(0f, 0f, REFL_W, REFL_H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, REFL_H.toFloat(), 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        })
    }
} catch (e: OutOfMemoryError) {
    null
}

private const val REFL_W = 192f
private const val REFL_H = 24

/**
 * Işık maskeleri (süreç başına bir kez, paylaşılan ALPHA_8): [glow] yumuşak yuvarlak (kutuya gerilince elips), [hot] daha
 * dolu çekirdekli yuvarlak (sinyal), [beam] far huzmesi (solda dar, sağa açılıp sönen koni). Renk çizim boyasından gelir;
 * toplam ~16 KB.
 */
private object LightMasks {
    val glow: Bitmap? by lazy { radial(intArrayOf(0xFF000000.toInt(), 0x8C000000.toInt(), 0x33000000, 0x0D000000, 0)) }

    /**
     * Fren zemin yansıması: arabanın arkasında geniş ve yumuşak; arka tampona yakın tarafta (sağ kenar) en dolu, geriye ve
     * yanlara doğru söner. ALPHA_8, 96 × 128.
     */
    val pool: Bitmap? by lazy {
        try {
            val w = 96
            val h = 128
            val px = ByteArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val u = x / (w - 1f)
                val v = (y - (h - 1) / 2f) / ((h - 1) / 2f)
                val along = Math.pow(u.toDouble(), 1.8).toFloat()
                val across = Math.exp(-(v * v) / (2.0 * 0.34 * 0.34)).toFloat()
                px[y * w + x] = (255 * along * across).toInt().coerceIn(0, 255).toByte()
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8).also { it.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(px)) }
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** [pool] ile aynı, ama gövde tarafındaki son %10'da (sağ kenar) sıfıra iner ([CarGeometry.brakePoolSoftEdge]; yalnız o modellerde kullanılır). */
    val poolSoft: Bitmap? by lazy {
        try {
            val w = 96
            val h = 128
            val px = ByteArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val u = x / (w - 1f)
                val v = (y - (h - 1) / 2f) / ((h - 1) / 2f)
                val along = Math.pow(u.toDouble(), 1.8).toFloat() * CarAnimSpec.smooth(1f - u, 0f, 0.1f)
                val across = Math.exp(-(v * v) / (2.0 * 0.34 * 0.34)).toFloat()
                px[y * w + x] = (255 * along * across).toInt().coerceIn(0, 255).toByte()
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8).also { it.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(px)) }
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun radial(colors: IntArray): Bitmap? = try {
        Bitmap.createBitmap(64, 64, Bitmap.Config.ALPHA_8).also {
            Canvas(it).drawCircle(32f, 32f, 32f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(32f, 32f, 32f, colors, floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f), Shader.TileMode.CLAMP)
            })
        }
    } catch (e: OutOfMemoryError) {
        null
    }

    val beam: Bitmap? by lazy {
        try {
            val w = 128
            val h = 64
            val px = ByteArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val u = x / (w - 1f)
                val v = (y - (h - 1) / 2f) / ((h - 1) / 2f)
                val spread = 0.16f + 0.84f * u
                val across = 1f - (v / spread) * (v / spread)
                if (across <= 0f) continue
                val along = (1f - u).let { it * it } * CarAnimSpec.smooth(u, 0f, 0.1f)
                px[y * w + x] = (255 * across * across * along).toInt().coerceIn(0, 255).toByte()
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8).also { it.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(px)) }
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}

/**
 * Tek ışık katmanı: [mask] (ALPHA_8 ise boyanın rengiyle, ARGB ise olduğu gibi) görünüm kutusuna gerilir. İçerik bir kez
 * kaydedilir; yalnızca görünüm alfası canlanır. Çakışan çizim yok ([hasOverlappingRendering] false): alfa ekran dışı katman
 * açmadan doğrudan boyaya uygulanır.
 */
private class LightSprite(context: Context, private var mask: Bitmap?, color: Int) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val dst = RectF()
    private val colorAlpha = color ushr 24

    /**
     * Görünüm alfasından ayrı ikinci bir çarpan (0..1; yalnız bagaj kapağına bağlı ışıklarda kullanılır, [CarGeometry.tailGate]):
     * görünüm alfası ışığın yanıp sönmesini canlandırırken bu, kapağın açıklığını izler. Hiç atanmazsa boya olduğu gibi kalır.
     */
    var gain = 1f
        set(value) {
            val v = if (value < 0f) 0f else if (value > 1f) 1f else value
            if (v == field) return
            field = v
            paint.alpha = (colorAlpha * v).toInt()
            invalidate()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        alpha = 0f
        visibility = GONE
    }

    fun setMask(m: Bitmap?) {
        if (m === mask) return
        mask = m
        invalidate()
    }

    override fun hasOverlappingRendering(): Boolean = false

    override fun onDraw(canvas: Canvas) {
        val m = mask ?: return
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(m, null, dst, paint)
    }
}

/**
 * Boya parlaması: gövde kutusunda çapraz, çok yumuşak beyaz bir bant [CarAnimSpec.GLINT_MS] boyunca soldan sağa adım adım
 * ([CarAnimSpec.GLINT_STEP_MS]) geçer;
 * gövde dışına taşmaz (base görselinin alfasıyla DST_IN maske). Yalnızca geçiş sırasında görünür ve donanım katmanındadır
 * (bant yalnızca konum değiştirir); arada GONE, katman bırakılır.
 */
private class GlintLayer(context: Context) : ViewGroup(context) {
    var mask: Bitmap? = null
    private val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val dst = RectF()
    private val band = object : View(context) {
        private val p = Paint()
        private var built = -1
        override fun hasOverlappingRendering(): Boolean = false
        override fun onDraw(canvas: Canvas) {
            if (built != width) {
                built = width
                val peak = if (LauncherTheme.night) 0x2EFFFFFF else 0x4DFFFFFF
                p.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, intArrayOf(0, peak, 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        }
    }
    private val ease = AccelerateDecelerateInterpolator()
    private var startedAt = 0L
    private var playing = false
    /** Adımlı geçiş ([CarAnimSpec.GLINT_STEP_MS]): her adımda yalnızca bandın konumu değişir. */
    private val step = object : Runnable {
        override fun run() {
            val f = (android.os.SystemClock.uptimeMillis() - startedAt) / CarAnimSpec.GLINT_MS.toFloat()
            if (f >= 1f) {
                stop()
                return
            }
            band.translationX = -band.width + ease.getInterpolation(f) * (width + band.width)
            postDelayed(this, CarAnimSpec.GLINT_STEP_MS)
        }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
        clipChildren = true
        addView(band)
    }

    fun play() {
        if (mask == null || playing) return
        playing = true
        band.translationX = -band.width.toFloat()
        visibility = VISIBLE
        setLayerType(LAYER_TYPE_HARDWARE, null)
        startedAt = android.os.SystemClock.uptimeMillis()
        post(step)
    }

    fun stop() {
        playing = false
        removeCallbacks(step)
        if (visibility == GONE) return
        visibility = GONE
        setLayerType(LAYER_TYPE_NONE, null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        // Bant gövde genişliğinin %40'ı, eğik durduğundan yüksekliği taşar.
        band.measure(MeasureSpec.makeMeasureSpec((w * 0.4f).toInt(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((h * 1.8f).toInt(), MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val top = -(band.measuredHeight - (b - t)) / 2
        band.layout(0, top, band.measuredWidth, top + band.measuredHeight)
        band.pivotX = band.measuredWidth / 2f
        band.pivotY = band.measuredHeight / 2f
        band.rotation = 18f
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val m = mask ?: return
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(m, null, dst, maskPaint)
    }
}

private val FADE_IN_EASE = android.view.animation.DecelerateInterpolator()
private val FADE_OUT_EASE = AccelerateDecelerateInterpolator()

// Işık renkleri: alfa kanalı ışığın tam parlaklığıdır, görünüm alfası 0..1 açar / kapatır.
private val HEAD_BEAM: Int get() = if (LauncherTheme.night) 0xB3DCE8FF.toInt() else 0x73DCE8FF
private const val HEAD_LAMP = 0xE6EAF2FF.toInt()
/** Zemindeki fren yansıması: geniş, yumuşak, düşük alfa (maskenin en dolu yerinde ~%30). */
private const val BRAKE_POOL = 0x4DFF2A20
private const val REVERSE_LAMP = 0xE6F2F6FF.toInt()
private const val REVERSE_FLOOR = 0x6BF2F6FF
/** Sinyal köşesi: yumuşak kehribar, en parlak yerinde ~%70 (premium, göz almayan). */
private const val TURN = 0xB3FFAA50.toInt()
/** Kaput ışığı (motor suyu / akü): maskenin en dolu yerinde ~%45 kehribar. */
private const val HOOD_GLOW = 0x73FFA040
private const val PART_FADE_MS = 360L
private const val PART_PULSE_MS = 1_800L
private const val PART_PULSE_LOW = 0.5f
/** Farla birlikte stopların park lambası parlaklığı (frende 1). */
private const val TAIL_POSITION_ALPHA = 0.35f
/** Yansımanın görünüm alfası (bitmap'te en çok %40; toplam ~%9). */
private const val REFLECTION_ALPHA = 0.22f

/** Gölge bitmap'inin bulanıklık payı (base px) ve BlurMaskFilter yarıçapı (CSS blur σ ≈ 7.1 px → yarıçap ≈ σ / 0.57735). */
private const val SHADOW_PAD = 24f
private const val SHADOW_BLUR_RADIUS = 12f

/**
 * Parça ışıkları (süreç başına bir kez, küçük bitmap'ler): [tire] üstten görülen lastik (yuvarlak köşeli dikdörtgen, sıcak
 * kehribar dolgu, çevresinde yumuşak ışıma; dört teker aynı bitmap'i paylaşır), [fuelCap] küçük yakıt kapağı ve ışıması.
 * Kutular base pikselinde; ışıma payı [GLOW_PAD].
 */
private object PartArt {
    const val GLOW_PAD = 22f
    private const val CAP_PAD = 12f
    private const val AMBER = 0xFFFFA040.toInt()

    fun tireViewBox(geo: CarGeometry, i: Int): FloatArray = CarAnimSpec.tireBox(i, geo).let { floatArrayOf(it[0] - GLOW_PAD, it[1] - GLOW_PAD, it[2] + GLOW_PAD, it[3] + GLOW_PAD) }

    fun capViewBox(geo: CarGeometry): FloatArray = geo.fuelCap.let { floatArrayOf(it.x - CAP_PAD, it.y - CAP_PAD, it.right + CAP_PAD, it.bottom + CAP_PAD) }

    fun hoodBox(geo: CarGeometry): FloatArray = floatArrayOf(
        geo.hoodCx - geo.hoodRx, geo.hoodCy - geo.hoodRy,
        geo.hoodCx + geo.hoodRx, geo.hoodCy + geo.hoodRy,
    )

    /** Lastik: dış kenar (gövdeden taşan) üstte; alttaki lastikler için [tireFlipped] dikey çevrilmiş hali. */
    fun tire(model: CarModel): Bitmap? = tires[model.ordinal].value

    fun tireFlipped(model: CarModel): Bitmap? = tiresFlipped[model.ordinal].value

    /** Lastik ve kapak ölçüleri modele göre değişir: model başına bir kez, o modelin ölçüleriyle çizilir. */
    private val tires = CarModel.values().map { m -> lazy { tireBitmap(m.geometry) } }
    private val tiresFlipped = CarModel.values().map { m ->
        lazy { tire(m)?.let { Bitmap.createBitmap(it, 0, 0, it.width, it.height, Matrix().apply { preScale(1f, -1f) }, false) } }
    }

    /**
     * Üstten görülen lastik: koyu lastik tabanı, enine 5 silik sırt çizgisi, dış kenardan (üst) içe doğru sönen sıcak kehribar
     * ışık (çekirdek #E8913A, alfa <= 0.85) ve çevrede yumuşak ışıma. Base pikseli ölçeğinde.
     */
    private fun tireBitmap(geo: CarGeometry): Bitmap? = try {
        val w = geo.tireL
        val h = geo.tireW
        val pad = GLOW_PAD
        Bitmap.createBitmap((w + 2 * pad).toInt(), (h + 2 * pad).toInt(), Bitmap.Config.ARGB_8888).also { bmp ->
            val c = Canvas(bmp)
            val r = RectF(pad, pad, pad + w, pad + h)
            val corner = 7f
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x66E8913A
                maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
            }
            c.drawRoundRect(RectF(r.left, r.top - 3f, r.right, r.bottom), corner, corner, glow)
            val clip = Path().apply { addRoundRect(r, corner, corner, Path.Direction.CW) }
            c.save()
            c.clipPath(clip)
            // Lastik tabanı: koyu kauçuk.
            c.drawRect(r, Paint().apply { color = 0xFF14110F.toInt() })
            // Sırt çizgileri (enine, silik).
            val tread = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66050403; strokeWidth = 2f }
            val n = 5
            for (k in 1..n) {
                val x = r.left + w * k / (n + 1)
                c.drawLine(x, r.top, x, r.bottom, tread)
            }
            // Kehribar ışık: dış kenarda en parlak, içe doğru söner.
            c.drawRect(r, Paint().apply {
                shader = LinearGradient(0f, r.top, 0f, r.bottom,
                    intArrayOf(0xD9E8913A.toInt(), 0x59E8913A.toInt(), 0x00E8913A), floatArrayOf(0f, 0.3f, 0.62f), Shader.TileMode.CLAMP)
            })
            // Sırt çizgileri ışığın üstünde de hafif okunsun.
            for (k in 1..n) {
                val x = r.left + w * k / (n + 1)
                c.drawLine(x, r.top, x, r.bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000; strokeWidth = 2f })
            }
            c.restore()
        }
    } catch (e: OutOfMemoryError) {
        null
    }

    fun fuelCap(model: CarModel): Bitmap? = fuelCaps[model.ordinal].value
    private val fuelCaps = CarModel.values().map { m -> lazy { lit(m.geometry.fuelCap.w, m.geometry.fuelCap.h, CAP_PAD, 4f, 8f) } }

    /**
     * Işıyan parça: iki kat bulanık kehribar ışıma, üstünde parça. Dolgu uzunlamasına tonlu (uçlar koyu, orta açık: lastik
     * sırtının yuvarlaklığı), üstte ince parlak çizgi. Base pikseli ölçeğinde.
     */
    private fun lit(w: Float, h: Float, pad: Float, corner: Float, blur: Float): Bitmap? = try {
        Bitmap.createBitmap((w + 2 * pad).toInt(), (h + 2 * pad).toInt(), Bitmap.Config.ARGB_8888).also { bmp ->
            val c = Canvas(bmp)
            val r = RectF(pad, pad, pad + w, pad + h)
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = (AMBER and 0x00FFFFFF) or 0xCC000000.toInt()
                maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            }
            c.drawRoundRect(r, corner, corner, glow)
            glow.maskFilter = BlurMaskFilter(blur * 0.5f, BlurMaskFilter.Blur.OUTER)
            c.drawRoundRect(r, corner, corner, glow)
            c.drawRoundRect(r, corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(r.left, 0f, r.right, 0f,
                    intArrayOf(0xFFD8701E.toInt(), 0xFFFFB45C.toInt(), AMBER, 0xFFFFB45C.toInt(), 0xFFD8701E.toInt()),
                    floatArrayOf(0f, 0.22f, 0.5f, 0.78f, 1f), Shader.TileMode.CLAMP)
            })
        }
    } catch (e: OutOfMemoryError) {
        null
    }

    /** Çubuğun gövde kenarından içeri uzaklığı (base px); uçlardaki sönüm [CarAnimSpec.tailTaper]. */
    private const val TAIL_INSET = 7f

    /**
     * Stop çubuğu: her satırda gövdenin arka kenarı (base alfası) bulunur, çubuk ondan [TAIL_INSET] içeride ince ve keskin bir
     * çekirdek (σ 1,3 px, ortası açık pembe-beyaz) + dar kırmızı ışıma (σ 5 px) olarak çizilir; kenar satırlar arasında
     * yumuşatılır, uçlar söner. ARGB, ~84 × 316 px, renk başına bir kez.
     *
     * [CarGeometry.tailGate] olan modelde çubuğun yeri tabandaki kırmızı LED çizgisidir ve çubuk ikiye ayrılır: [centre] true
     * bagaj kapağındaki orta, false gövdede kalan uçlar. Diğer modellerde [centre] kullanılmaz.
     */
    fun tailBarOf(base: Bitmap, geo: CarGeometry, centre: Boolean = true): Bitmap? = try {
        val gate = geo.tailGate
        // Stop çubuğunun kutusu ([CarGeometry.tail], base pikseli): arka kenar boyunca, köşelere kadar.
        val tailX0 = geo.tail[0]
        val tailX1 = geo.tail[2]
        val w = (tailX1 - tailX0).toInt()
        val h = (geo.tail[3] - geo.tail[1]).toInt()
        val scanW = minOf(if (gate == null) 90 else tailX1.toInt() + 40, base.width)
        val y0 = geo.tail[1].toInt()
        val edge = FloatArray(h) { Float.NaN }
        val row = IntArray(scanW)
        for (yy in 0 until h) {
            val by = y0 + yy
            if (by < 0 || by >= base.height) continue
            base.getPixels(row, 0, scanW, 0, by, scanW, 1)
            if (gate == null) {
                val x = row.indexOfFirst { (it ushr 24) > 140 }
                if (x >= 0) edge[yy] = x.toFloat()
                continue
            }
            edge[yy] = ledX(row, scanW) - TAIL_INSET
        }
        // Satırlar arası yumuşatma (9 satırlık ortalama): kenar pürüzü çubuğu titretmesin.
        val smooth = FloatArray(h) { i ->
            var sum = 0f
            var n = 0
            for (k in -4..4) { val e = edge.getOrNull(i + k) ?: continue; if (!e.isNaN()) { sum += e; n++ } }
            if (n == 0) Float.NaN else sum / n
        }
        val out = IntArray(w * h)
        for (yy in 0 until h) {
            val e = smooth[yy]
            if (e.isNaN()) continue
            var along = CarAnimSpec.tailTaper(yy, h)
            if (gate != null) {
                val by = (y0 + yy).toFloat()
                if ((by in gate.cut0..gate.cut1) != centre) continue
                // Orta parça kesim yerlerinde yumuşakça söner (base'deki iki küçük boşluk).
                if (centre) along *= CarAnimSpec.smooth(minOf(by - gate.cut0, gate.cut1 - by), 0f, 6f)
            }
            ledRow(out, yy * w, w, e + TAIL_INSET - tailX0, along)
        }
        Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    } catch (e: OutOfMemoryError) {
        null
    }

    /** LED çizgisinin satırdaki x'i (yoksa NaN): en kırmızı piksel satırdan satıra oynar (çubuk dalgalanır); çevresinin ağırlık merkezi alınır. */
    private fun ledX(row: IntArray, n: Int): Float {
        var best = -1
        var bestScore = 70
        for (xx in 0 until n) {
            val p = row[xx]
            if ((p ushr 24) < 140) continue
            val score = ((p shr 16) and 0xFF) - (((p shr 8) and 0xFF) + (p and 0xFF)) / 2
            if (score > bestScore) { bestScore = score; best = xx }
        }
        if (best < 0) return Float.NaN
        var sum = 0f
        var wsum = 0f
        for (xx in maxOf(0, best - 4)..minOf(n - 1, best + 4)) {
            val p = row[xx]
            if ((p ushr 24) < 140) continue
            val wgt = (((p shr 16) and 0xFF) - (((p shr 8) and 0xFF) + (p and 0xFF)) / 2 - 70).toFloat()
            if (wgt > 0f) { sum += wgt * xx; wsum += wgt }
        }
        return if (wsum > 0f) sum / wsum else best.toFloat()
    }

    /** Çubuğun bir satırı ([out]'ta [at]'tan başlar, [w] piksel): [c] merkezinde ince çekirdek + dar ışıma, [along] ile çarpılır. */
    private fun ledRow(out: IntArray, at: Int, w: Int, c: Float, along: Float) {
        for (x in 0 until w) {
            val d = x - c
            val core = Math.exp(-(d * d) / (2.0 * 1.3 * 1.3)).toFloat()
            val glow = 0.38f * Math.exp(-(d * d) / (2.0 * 5.0 * 5.0)).toFloat()
            val a = (core + glow).coerceAtMost(1f) * along
            if (a <= 0.004f) continue
            val hot = core * core * 0.55f
            val g = (42 + (176 - 42) * hot).toInt()
            val b = (32 + (160 - 32) * hot).toInt()
            out[at + x] = ((a * 255).toInt().coerceIn(0, 255) shl 24) or (255 shl 16) or (g shl 8) or b
        }
    }

    /**
     * Kalkık kapağın üstündeki stop çubuğu: açık bagaj görselinde ([overlay], yeri [at]) [TailGate.openBox] içindeki kırmızı LED
     * çizgisi satır satır okunur, çubuk [tailBarOf] ile aynı görünümde (ince çekirdek + dar ışıma) tam o çizginin üstüne çizilir;
     * uçlarda yumuşakça söner. ARGB, kutu boyunda.
     */
    fun gateOpenBarOf(overlay: Bitmap, at: CarAnimSpec.Box, gate: TailGate): Bitmap? = try {
        val box = gate.openBox
        val w = (box[2] - box[0]).toInt()
        val h = (box[3] - box[1]).toInt()
        // Okunan sütunlar: kutu ile görselin kesişimi (görsel pikseli).
        val sx0 = maxOf(0, (box[0] - at.x).toInt())
        val sx1 = minOf(overlay.width, (box[2] - at.x).toInt())
        val n = sx1 - sx0
        val edge = FloatArray(h) { Float.NaN }
        if (n > 0) {
            val row = IntArray(n)
            for (yy in 0 until h) {
                val oy = (box[1] - at.y).toInt() + yy
                if (oy < 0 || oy >= overlay.height) continue
                overlay.getPixels(row, 0, n, sx0, oy, n, 1)
                // Çizginin kutu içindeki yeri (base px, kutunun soluna göre).
                edge[yy] = ledX(row, n) + sx0 + at.x - box[0]
            }
        }
        val first = edge.indexOfFirst { !it.isNaN() }
        val last = edge.indexOfLast { !it.isNaN() }
        val out = IntArray(w * h)
        if (first >= 0) for (yy in first..last) {
            var sum = 0f
            var cnt = 0
            for (k in -4..4) { val e = edge.getOrNull(yy + k) ?: continue; if (!e.isNaN()) { sum += e; cnt++ } }
            if (cnt == 0) continue
            val c = sum / cnt
            ledRow(out, yy * w, w, c, CarAnimSpec.smooth(minOf(yy - first, last - yy).toFloat(), 0f, 6f))
        }
        Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    } catch (e: OutOfMemoryError) {
        null
    }

    /** Kaput maskesi: base alfası (kaput bölgesi) × elips içinde yumuşak sönüm; ALPHA_8, ~60 KB, renk başına bir kez. */
    fun hoodMaskOf(base: Bitmap, geo: CarGeometry): Bitmap? = try {
        val b = hoodBox(geo)
        val x0 = b[0].toInt().coerceIn(0, base.width - 1)
        val y0 = b[1].toInt().coerceIn(0, base.height - 1)
        val w = (b[2].toInt() - x0).coerceAtMost(base.width - x0)
        val h = (b[3].toInt() - y0).coerceAtMost(base.height - y0)
        val px = IntArray(w * h)
        base.getPixels(px, 0, w, x0, y0, w, h)
        val out = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val u = (x0 + x - geo.hoodCx) / geo.hoodRx
            val v = (y0 + y - geo.hoodCy) / geo.hoodRy
            val d = 1f - (u * u + v * v)
            if (d <= 0f) continue
            val a = (px[y * w + x] ushr 24) / 255f
            out[y * w + x] = (255f * a * d * d).toInt().coerceIn(0, 255).toByte()
        }
        // Görünüm kutusu tam elips kutusudur; kırpılan kenar varsa maske kutuya gerilir (kaput base içinde, kırpma yok).
        Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8).also { it.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(out)) }
    } catch (e: OutOfMemoryError) {
        null
    }
}
