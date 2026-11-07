package com.mapcontrol.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Spor modu (kronograf dili) renkleri ve çizim yardımcıları. Kırmızı-turuncu yalnız ışık olarak (ibre, kırmızı bölge,
 * ilerleme halkası). Spor her temada koyudur; gündüz/gece ayrımı yok.
 *
 * Zemin (karbon, süet, gerçek kırmızı çift dikiş) kodda çizilmez: hazır tek görsel ([SportGroundImage], `sport_ground`),
 * bir kez çözülür. Kadran yüzleri, çerçeveler ve yazılar bir kez önbellek bitmap'ine ([SportArt.render]) çizilir; kare
 * başına bitmap üretimi, bulanıklık ya da gölge yok.
 */
internal object SportInk {
    const val RED = 0xFFEA580C.toInt()
    const val RED_HOT = 0xFFFF7A33.toInt()
    const val INK = 0xFFF1F3F6.toInt()
    const val INK_2 = 0xFFC9CED6.toInt()
    const val INK_3 = 0xFF858B94.toInt()
    const val FACE = 0xFF0B0C0E.toInt()
    const val METAL_HI = 0xFFE9ECEF.toInt()
    const val METAL_MID = 0xFF9298A1.toInt()
    const val METAL_LO = 0xFF3B3F45.toInt()
    const val METAL_DEEP = 0xFF1C1E22.toInt()
    const val ICON = 0xFFD9DDE3.toInt()
    const val ICON_ACTIVE = RED_HOT
}

internal object SportArt {

    // --- Kadran parçaları ---

    /** Fırçalanmış metal halka: [r] dış yarıçap, [bw] kalınlık; [bright] 0..1 genel parlaklık. */
    fun bezel(c: Canvas, cx: Float, cy: Float, r: Float, bw: Float, bright: Float = 1f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF040405.toInt()
        c.drawCircle(cx, cy, r, p)
        fun shade(col: Int): Int = scale(col, bright)
        val hi = shade(SportInk.METAL_HI)
        val mid = shade(SportInk.METAL_MID)
        val lo = shade(SportInk.METAL_LO)
        val deep = shade(SportInk.METAL_DEEP)
        p.style = Paint.Style.STROKE
        p.strokeWidth = bw
        p.shader = SweepGradient(cx, cy,
            intArrayOf(mid, hi, mid, lo, deep, lo, mid, hi, mid, lo, deep, lo, mid),
            floatArrayOf(0f, 0.07f, 0.15f, 0.24f, 0.32f, 0.41f, 0.5f, 0.57f, 0.65f, 0.74f, 0.82f, 0.91f, 1f))
        val rm = r - bw / 2f - 0.5f
        c.drawCircle(cx, cy, rm, p)
        p.shader = null
        // Fırça izleri: halka boyunca ince eşmerkezli çizgiler.
        p.strokeWidth = 0.7f
        for (i in 1..5) {
            p.color = if (i % 2 == 0) 0x1AFFFFFF else 0x1F000000
            c.drawCircle(cx, cy, r - 0.5f - bw * i / 6f, p)
        }
        // Dış kenar (koyu) ve iç pah (ince ışık + koyu çizgi).
        p.strokeWidth = 1.2f
        p.color = 0xFF020203.toInt()
        c.drawCircle(cx, cy, r - 0.6f, p)
        p.color = 0x59FFFFFF
        p.strokeWidth = 1f
        c.drawCircle(cx, cy, r - bw - 0.5f, p)
        p.color = 0xFF050506.toInt()
        p.strokeWidth = 1.6f
        c.drawCircle(cx, cy, r - bw - 1.8f, p)
        // Üstten düşen ışık: dış kenarın üst yarısında ince parıltı.
        p.strokeWidth = 1.2f
        p.shader = LinearGradient(0f, cy - r, 0f, cy, 0x99FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r - 1.4f, p)
        p.shader = null
    }

    /** Kadran yüzü: üstten hafif ışık, kenara doğru koyulaşan iç gölge (gradyan; bulanıklık yok). */
    fun face(c: Canvas, cx: Float, cy: Float, rf: Float, lift: Float = 0f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val top = blend(0xFF1A1C20.toInt(), 0xFF3A3E45.toInt(), lift)
        p.shader = RadialGradient(cx, cy - rf * 0.55f, rf * 1.45f, intArrayOf(top, SportInk.FACE, 0xFF050506.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, rf, p)
        p.shader = RadialGradient(cx, cy, rf, intArrayOf(0x00000000, 0x00000000, 0xA6000000.toInt()), floatArrayOf(0f, 0.82f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, rf, p)
        p.shader = null
    }

    /** Yüzün kenarında ince kronograf bölüntüsü ([n] çizgi; her 5'te biri uzun). [red] açı aralıklarında kırmızı. */
    fun tickRing(c: Canvas, cx: Float, cy: Float, rOut: Float, n: Int, len: Float, alpha: Int, red: List<ClosedFloatingPointRange<Float>> = emptyList()) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.strokeCap = Paint.Cap.BUTT
        for (i in 0 until n) {
            val deg = 360f * i / n
            val major = i % 5 == 0
            val isRed = red.any { deg in it }
            p.color = if (isRed) SportInk.RED else (alpha shl 24) or 0xFFFFFF
            p.strokeWidth = if (major) 2f else 1.1f
            val l = if (major) len * 1.6f else len
            line(c, cx, cy, deg, rOut - l, rOut, p)
        }
    }

    fun line(c: Canvas, cx: Float, cy: Float, deg: Float, r0: Float, r1: Float, p: Paint) {
        val a = Math.toRadians(deg.toDouble())
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        c.drawLine(cx + ca * r0, cy + sa * r0, cx + ca * r1, cy + sa * r1, p)
    }

    fun textPaint(context: Context, sizePx: Float, color: Int, weight: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textSize = sizePx
        this.color = color
        typeface = SahneFonts.get(context, weight)
        textAlign = Paint.Align.CENTER
    }

    /** Yazıyı (cx, cy) dikey ortasına yerleştirir. */
    fun centered(c: Canvas, text: String, cx: Float, cy: Float, p: Paint) {
        val fm = p.fontMetrics
        c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, p)
    }

    fun scale(col: Int, k: Float): Int {
        val a = col ushr 24
        val r = (((col shr 16) and 0xFF) * k).toInt().coerceIn(0, 255)
        val g = (((col shr 8) and 0xFF) * k).toInt().coerceIn(0, 255)
        val b = ((col and 0xFF) * k).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun blend(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(s: Int) = ((((a shr s) and 0xFF) * (1 - k)) + (((b shr s) and 0xFF) * k)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    // --- Sahne bitmap'i (orta alan) ---

    /**
     * Orta alanın bütün durağan çizimi (saydam zemin; karbon ve dikiş kök zemin görselinden gelir): beş/yedi kadranın
     * çerçeveleri, yüzleri, bölüntüleri ve sabit yazıları. Arka planda çağrılabilir (yazı tipleri önceden yüklü olmalı).
     */
    fun render(context: Context, g: SportGeometry): Bitmap {
        val bmp = Bitmap.createBitmap(g.w.coerceAtLeast(1), g.h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        mediaDial(c, g.media)
        carDial(c, g.car, g)
        g.subs.forEach { subDial(context, c, it, g) }
        speedDial(context, c, g)
        return bmp
    }

    private fun mediaDial(c: Canvas, d: SportGeometry.Dial) {
        val bw = d.r * 0.075f
        bezel(c, d.cx, d.cy, d.r, bw)
        val rf = d.r - bw - 2f
        face(c, d.cx, d.cy, rf)
        // Bölüntü yalnız doku: kırmızı yok (ilerleme halkası ikinci bir devir göstergesi gibi okunmasın).
        tickRing(c, d.cx, d.cy, rf - 4f, 120, rf * 0.035f, 0x38)
        // İlerleme halkasının izi (dolgu canlı çizilir).
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d.r * SportGeometry.PROGRESS_W; color = 0x1FFFFFFF }
        c.drawCircle(d.cx, d.cy, d.r * SportGeometry.PROGRESS_R, p)
    }

    private fun carDial(c: Canvas, d: SportGeometry.Dial, g: SportGeometry) {
        val bw = d.r * 0.075f
        bezel(c, d.cx, d.cy, d.r, bw, bright = 1.06f)
        val rf = d.r - bw - 2f
        face(c, d.cx, d.cy, rf, lift = 0.75f)
        tickRing(c, d.cx, d.cy, rf - 4f, 120, rf * 0.035f, 0x38)
        // Motor torku halkasının izi (medya ilerleme izinin eşi; alt boşluklu yay). Dolgu canlı çizilir ([TorqueRing]).
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = d.r * SportGeometry.PROGRESS_W; color = 0x1FFFFFFF
        }
        val tr = d.r * SportGeometry.PROGRESS_R
        c.drawArc(RectF(d.cx - tr, d.cy - tr, d.cx + tr, d.cy + tr), SportTorque.START_DEG, SportTorque.SWEEP_DEG, false, track)
        val carLen = g.carLength()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Üstten düşen ışık havuzu (aracın arkasında yüz biraz aydınlık; araç üstündeki ışık [CarLightView]).
        p.shader = RadialGradient(d.cx, d.cy - rf * 0.25f, rf * 0.85f, intArrayOf(0x33B8C4D4, 0x0FB8C4D4, 0x00B8C4D4), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(d.cx, d.cy, rf, p)
        // Zemin gölgesi (aracın altı) ve stop lambası parıltısı (burun yukarı, kuyruk aşağıda).
        p.shader = RadialGradient(d.cx, d.cy, carLen * 0.62f, intArrayOf(0x99000000.toInt(), 0x00000000), null, Shader.TileMode.CLAMP)
        c.drawCircle(d.cx, d.cy, carLen * 0.62f, p)
        // Işık havuzu kuyruğun hemen altında; yüz çemberinin içinde sıfıra iner (en alt nokta ~0,94 rf), ayrıca yüz
        // çemberine kırpılır: alt kenarda kesik görünmez.
        val ty = d.cy + rf * 0.70f
        val gr = rf * 0.5f
        p.shader = RadialGradient(d.cx, ty, gr, intArrayOf(0x80EA580C.toInt(), 0x3DEA580C, 0x12EA580C, 0x00EA580C), floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.save()
        val clip = android.graphics.Path().apply { addCircle(d.cx, d.cy, rf - 1f, android.graphics.Path.Direction.CW) }
        c.clipPath(clip)
        c.scale(1.15f, 0.48f, d.cx, ty)
        c.drawCircle(d.cx, ty, gr, p)
        c.restore()
        p.shader = null
    }

    private fun subDial(context: Context, c: Canvas, d: SportGeometry.Dial, g: SportGeometry) {
        val bw = d.r * 0.09f
        bezel(c, d.cx, d.cy, d.r, bw, bright = 0.82f)
        val rf = d.r - bw - 2f
        face(c, d.cx, d.cy, rf)
        tickRing(c, d.cx, d.cy, rf - 3f, 60, rf * 0.05f, 0x30)
        val label = textPaint(context, d.r * 0.17f, SportInk.INK_3, 500)
        when (d.kind) {
            SportDial.CLOCK -> clockFace(c, d.cx, d.cy, rf)
            SportDial.G -> {
                // Artı işareti yalnız 1 g çemberinin içinde: etiket, eksen yazıları ve büyük değer çizgilerin üstüne binmez.
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = 0x40FFFFFF }
                val gr = d.r * SportGeometry.G_RANGE
                c.drawCircle(d.cx, d.cy, gr, p)
                c.drawCircle(d.cx, d.cy, gr / 2f, p)
                c.drawLine(d.cx - gr, d.cy, d.cx + gr, d.cy, p)
                c.drawLine(d.cx, d.cy - gr, d.cx, d.cy + gr, p)
                centered(c, "G", d.cx, d.cy - d.r * 0.62f, label)
                val small = textPaint(context, d.r * 0.15f, SportInk.INK_3, 400)
                // Dış çember tam ölçek (GFilter.FULL_SCALE_G = 0,5 g), iç çember 0,25 g.
                centered(c, "0,5", d.cx + gr + d.r * 0.14f, d.cy, small)
                centered(c, "0,5", d.cx - gr - d.r * 0.14f, d.cy, small)
            }
            else -> {
                // Birim ve durum ("Hazır", "En iyi") canlı yazı; durağan çizimde yalnız etiket.
                val k = d.kind ?: return
                centered(c, SportGeometry.label(k), d.cx, d.cy - d.r * 0.44f, label)
            }
        }
    }

    /**
     * Analog saat yüzü: 12/3/6/9'da talaşlı metal çentikler, diğer saatlerde sönük noktalar, altta minik saniye alt
     * kadranı (ince halka ve 12 çentik). İbreler canlı ([SportClockHand]).
     */
    private fun clockFace(c: Canvas, cx: Float, cy: Float, rf: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r1 = rf * 0.86f
        val len = rf * 0.17f
        val hw = rf * 0.036f
        for (i in 0 until 12) {
            val deg = i * 30f - 90f
            if (i % 3 == 0) {
                c.save()
                c.rotate(deg + 90f, cx, cy)
                // Çentik: dikey metal çubuk (üstten ışık), koyu kenar çizgisi.
                val rect = RectF(cx - hw, cy - r1, cx + hw, cy - r1 + len)
                p.style = Paint.Style.FILL
                p.shader = LinearGradient(rect.left, 0f, rect.right, 0f, intArrayOf(SportInk.METAL_LO, SportInk.METAL_HI, SportInk.METAL_MID), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, hw * 0.4f, hw * 0.4f, p)
                p.shader = null
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1f
                p.color = 0xFF050506.toInt()
                c.drawRoundRect(rect, hw * 0.4f, hw * 0.4f, p)
                c.restore()
            } else {
                p.style = Paint.Style.FILL
                p.shader = null
                p.color = 0x8CFFFFFF.toInt()
                val a = Math.toRadians(deg.toDouble())
                c.drawCircle(cx + cos(a).toFloat() * (r1 - len * 0.3f), cy + sin(a).toFloat() * (r1 - len * 0.3f), rf * 0.018f, p)
            }
        }
        // Saniye alt kadranı.
        val sy = cy + rf * SportGeometry.CLOCK_SEC_Y
        val sr = rf * SportGeometry.CLOCK_SEC_R
        p.style = Paint.Style.FILL
        p.color = 0xFF060607.toInt()
        c.drawCircle(cx, sy, sr, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = 0x4DFFFFFF
        c.drawCircle(cx, sy, sr, p)
        for (i in 0 until 12) line(c, cx, sy, i * 30f, sr * (if (i % 3 == 0) 0.62f else 0.78f), sr * 0.92f, p.apply { color = if (i % 3 == 0) 0x99FFFFFF.toInt() else 0x40FFFFFF })
    }

    /**
     * Ana kadran: hız 0–[SportMath.SPEED_MAX] km/sa (kullanıcı kararı "önce hız", spor-modu-brief sonu). Bölüntü her 5 km/sa
     * ince, 10'da orta, 20'de uzun; rakamlar 20'şer. Kırmızı bölge yok (sakin); tek sıcak vurgu ibre ve tepe işareti.
     */
    private fun speedDial(context: Context, c: Canvas, g: SportGeometry) {
        val d = g.rpm
        val bw = d.r * 0.075f
        bezel(c, d.cx, d.cy, d.r, bw, bright = 1.08f)
        val rf = d.r - bw - 2f
        face(c, d.cx, d.cy, rf, lift = 0.15f)
        val rT = rf - rf * 0.045f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.FILL
        p.strokeCap = Paint.Cap.BUTT
        for (i in 0..SportMath.SPEED_MAX / 5) {
            val kmh = i * 5
            val deg = SportMath.speedDeg(kmh.toFloat())
            val major = kmh % SportMath.SPEED_MAJOR == 0
            val half = !major && kmh % 10 == 0
            p.color = when {
                major -> SportInk.INK
                half -> 0xD9FFFFFF.toInt()
                else -> 0x8CFFFFFF.toInt()
            }
            p.strokeWidth = when { major -> rf * 0.016f; half -> rf * 0.009f; else -> rf * 0.0055f }
            val len = when { major -> rf * 0.11f; half -> rf * 0.065f; else -> rf * 0.038f }
            line(c, d.cx, d.cy, deg, rT - len, rT, p)
        }
        // Rakamlar 0, 20 … 220 (ince; üç haneliler biraz küçük, yay boyunca sığsın).
        val num = textPaint(context, rf * 0.13f, SportInk.INK, 300)
        val rn = rf * 0.71f
        for (kmh in 0..SportMath.SPEED_MAX step SportMath.SPEED_MAJOR) {
            val a = Math.toRadians(SportMath.speedDeg(kmh.toFloat()).toDouble())
            centered(c, kmh.toString(), d.cx + cos(a).toFloat() * rn, d.cy + sin(a).toFloat() * rn, num)
        }
        hubAndGear(c, g, d, rf, p)
        // En alttaki küçük devir okuması canlı yazı ([SportStageView]); burada zemin yok (eskiyse yalnız boş yüz görünür).
    }

    /** Ana kadranın alt yarısı (iki düzende ortak): dijital hızın zemini ve vites penceresi. */
    private fun hubAndGear(c: Canvas, g: SportGeometry, d: SportGeometry.Dial, rf: Float, p: Paint) {
        // Dijital hızın zemini: göbeğin altında çok hafif koyu bir yuva (yüzden ayrışır, kutu gibi durmaz). Rakamlar
        // canlı yazı; burada yalnız zemin.
        p.style = Paint.Style.FILL
        val sy = d.cy + rf * SportGeometry.SPEED_Y
        val sr = rf * 0.36f
        p.shader = RadialGradient(d.cx, sy, sr, intArrayOf(0x8C000000.toInt(), 0x4D000000, 0x00000000), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(d.cx, sy, sr, p)
        p.shader = null
        // Vites penceresi: metal çerçeveli koyu pencere.
        val win = g.gearWindow()
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = 0xFF050506.toInt()
        val wr = win.height() * 0.18f
        c.drawRoundRect(win, wr, wr, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = rf * 0.012f
        p.shader = LinearGradient(0f, win.top, 0f, win.bottom, intArrayOf(SportInk.METAL_HI, SportInk.METAL_LO, SportInk.METAL_MID), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(win, wr, wr, p)
        p.shader = null
    }

    /** Geçişte kadran çerçevesi parıltısı (yalnızca halka; gradyanla yumuşak kenar). */
    fun glowRing(c: Canvas, cx: Float, cy: Float, r: Float, width: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val outer = r + width
        val inner = (r - width).coerceAtLeast(1f)
        val start = inner / outer
        val mid = r / outer
        // Işık, neon değil: ince ve yarı saydam.
        p.shader = RadialGradient(cx, cy, outer, intArrayOf(0x00EA580C, 0x00EA580C, 0x66FF7A33, 0x00EA580C),
            floatArrayOf(0f, start, mid, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, outer, p)
    }

    /** Tırtıllı (knurl) kenar: dış halkaya kısa radyal çizgiler. */
    fun knurl(c: Canvas, cx: Float, cy: Float, r: Float, depth: Float, n: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }
        for (i in 0 until n) {
            val deg = 360f * i / n
            p.color = if (i % 2 == 0) 0x66000000 else 0x40FFFFFF
            line(c, cx, cy, deg, r - depth, r, p)
        }
    }
}

/**
 * Spor orta alanının kadran yerleşimi (taslak 1872 × 488; ölçek ve ortalama gerçek boyuta göre). Uzun alanda (Tiggo 8,
 * [isTall]) ikinci taslak 1872 × 740: devir kadranı büyür, küçük kadranlar onun çaprazlarına (üst/alt) açılır, medya ve
 * araç kadranları yanlarda dikey ortada; böylece yükseklik kullanılır ve hiçbir kadran çakışmaz (SportLogicTest).
 */
internal class SportGeometry(
    val w: Int,
    val h: Int,
    val dials: List<SportDial>,
) {
    /** [slot] küçük kadran yuvası (0 sol üst, 1 sol alt, 2 sağ üst, 3 sağ alt); büyük kadranlarda -1. */
    class Dial(val cx: Float, val cy: Float, val r: Float, val kind: SportDial? = null, val slot: Int = -1)

    val tall: Boolean = isTall(w, h)
    private val dh = if (tall) DH_TALL else DH
    val s: Float = min(w / DW, h / dh).coerceAtLeast(0.1f)
    private val ox = (w - DW * s) / 2f
    private val oy = (h - dh * s) / 2f

    fun x(d: Float) = ox + d * s
    fun y(d: Float) = oy + d * s
    fun r(d: Float) = d * s

    val media = if (tall) Dial(x(225f), y(370f), r(215f)) else Dial(x(212f), y(244f), r(210f))
    val car = if (tall) Dial(x(1647f), y(370f), r(215f)) else Dial(x(1660f), y(244f), r(210f))
    val rpm = if (tall) Dial(x(936f), y(370f), r(318f)) else Dial(x(936f), y(244f), r(236f))
    val subs: List<Dial> = if (tall) listOf(
        Dial(x(565f), y(122f), r(112f), dials[0], 0),
        Dial(x(565f), y(618f), r(112f), dials[1], 1),
        Dial(x(1307f), y(122f), r(112f), dials[2], 2),
        Dial(x(1307f), y(618f), r(112f), dials[3], 3),
    ) else listOf(
        Dial(x(590f), y(128f), r(104f), dials[0], 0),
        Dial(x(590f), y(360f), r(104f), dials[1], 1),
        Dial(x(1282f), y(128f), r(104f), dials[2], 2),
        Dial(x(1282f), y(360f), r(104f), dials[3], 3),
    )

    /** Tüm kadranlar (çakışma denetimi için). */
    val all: List<Dial> get() = listOf(media, car, rpm) + subs

    /** Kadranlar soldan sağa (geçişteki parıltı sırası). */
    val inOrder: List<Dial> get() = (listOf(media, rpm, car) + subs).sortedBy { it.cx }

    fun sub(kind: SportDial): Dial? = subs.firstOrNull { it.kind == kind }

    /** Aynı boyut ve aynı seçim (durağan çizim yeniden kullanılabilir). */
    fun sameAs(o: SportGeometry?): Boolean = o != null && o.w == w && o.h == h && o.dials == dials

    /** Ana kadran (hız) yüz yarıçapı. */
    val rpmFace: Float get() = rpm.r - rpm.r * 0.075f - 2f

    /**
     * Vites penceresi: dijital hızın altında, kadranın alt boşluğunda (ibre 0–8 000 arası üst yarıyı süpürür, buraya
     * hiç inmez; kuyruğu göbekten ~0,14 yüz yarıçapı taşar).
     */
    fun gearWindow(): RectF {
        val rf = rpmFace
        val cx = rpm.cx
        val cy = rpm.cy + rf * GEAR_Y
        val hw = rf * GEAR_HW
        val hh = rf * GEAR_HH
        return RectF(cx - hw, cy - hh, cx + hw, cy + hh)
    }

    /** Dijital hız rakamlarının dikey ortası ve yazı boyu (px). */
    val speedY: Float get() = rpm.cy + rpmFace * SPEED_Y
    val speedSize: Float get() = rpmFace * SPEED_SIZE
    /** Küçük devir okumasının dikey ortası ve yazı boyu (px). */
    val rpmTextY: Float get() = rpm.cy + rpmFace * RPM_LABEL_Y
    val rpmTextSize: Float get() = rpmFace * RPM_TEXT_SIZE
    /** "km/sa" biriminin dikey ortası ve yazı boyu (px). */
    val speedUnitY: Float get() = rpm.cy + rpmFace * SPEED_UNIT_Y
    val speedUnitSize: Float get() = rpmFace * SPEED_UNIT_SIZE

    /** Araç kadranında aracın gövde boyu (burun yukarı). */
    fun carLength(): Float = (car.r - car.r * 0.075f - 2f) * 1.52f

    /** Küçük kadran yüzü yarıçapı (bezel içi). */
    fun subFace(d: Dial): Float = d.r - d.r * 0.09f - 2f

    companion object {
        /** Kadran üstündeki tek kelimelik etiket (saatte yok). */
        fun label(kind: SportDial): String = when (kind) {
            SportDial.ZERO100 -> "0–100"
            SportDial.G -> "G"
            SportDial.INSTANT -> "Anlık"
            SportDial.MAX -> "Maks"
            SportDial.AVG_SPEED -> "Ortalama"
            SportDial.TRIP_TIME -> "Süre"
            SportDial.RANGE -> "Menzil"
            SportDial.CLOCK -> ""
        }

        /** Birim (canlı yazı; süre "dk"/"sa" arasında değişir). */
        fun unit(kind: SportDial): String = when (kind) {
            SportDial.ZERO100 -> "sn"
            SportDial.INSTANT -> "L/100"
            SportDial.MAX, SportDial.AVG_SPEED -> "km/sa"
            SportDial.TRIP_TIME -> "dk"
            SportDial.RANGE -> "km"
            SportDial.G, SportDial.CLOCK -> ""
        }

        /** Seçicideki ad. */
        fun pickerName(kind: SportDial): String = when (kind) {
            SportDial.ZERO100 -> "0–100"
            SportDial.G -> "G-metre"
            SportDial.INSTANT -> "Anlık tüketim"
            SportDial.MAX -> "Maks hız"
            SportDial.AVG_SPEED -> "Ortalama hız"
            SportDial.TRIP_TIME -> "Yolculuk süresi"
            SportDial.RANGE -> "Menzil"
            SportDial.CLOCK -> "Analog saat"
        }

        // Hız kadranının alt yarısı (yüz yarıçapına oran, göbekten aşağı): dijital hız, birimi, vites penceresi, küçük
        // devir okuması. Hız ibre kuyruğunun (≈0,14) altında başlar; ibre bu bölgeye hiç girmez (SportLogicTest).
        const val SPEED_Y = 0.31f
        const val SPEED_SIZE = 0.34f
        const val SPEED_UNIT_Y = 0.485f
        const val SPEED_UNIT_TEXT = "km/sa"
        const val SPEED_UNIT_SIZE = 0.058f
        const val GEAR_Y = 0.655f
        const val GEAR_HW = 0.165f
        const val GEAR_HH = 0.1f
        const val RPM_LABEL_Y = 0.855f
        /** Küçük devir okumasının yazı boyu (yüz yarıçapına oran). */
        const val RPM_TEXT_SIZE = 0.058f
        /** Yazı tipinin büyük harf/rakam yüksekliği (yazı boyuna oran, Manrope ~0,72). */
        const val CAP = 0.72f

        /** Analog saat: saniye alt kadranının merkez yüksekliği ve yarıçapı (yüz yarıçapına oran). */
        const val CLOCK_SEC_Y = 0.42f
        const val CLOCK_SEC_R = 0.19f

        const val DW = 1872f
        const val DH = 488f
        /** Uzun alan taslağının yüksekliği ve uzun sayılma oranı (yükseklik / genişlik; Tiggo 7 ~0,28, Tiggo 8 ~0,45). */
        const val DH_TALL = 740f
        const val TALL_RATIO = 0.36f

        fun isTall(w: Int, h: Int): Boolean = w > 0 && h > w * TALL_RATIO

        /**
         * Medya kadranının içeriği hangi yarıçapta kurulsun: kadran tasarım ölçüsüne ([naturalPx], Tiggo 7) yakın ya da
         * büyükse kendi yarıçapı (ölçek 1, Tiggo 7 aynı); küçükse tasarım ölçüsünde kurulup küçültülür, böylece yazı ve
         * düğmeler aynı oranda kalır ve sanatçı satırı düğmelere binmez.
         */
        fun mediaContentRadius(rPx: Float, naturalPx: Float): Float = if (rPx >= naturalPx * 0.95f) rPx else naturalPx
        /** Medya kadranında ilerleme halkası yarıçapı ve kalınlığı (dış yarıçapa oran). */
        const val PROGRESS_R = 0.82f
        const val PROGRESS_W = 0.016f
        /** G-metre: tam ölçeğin (0,5 g) kadrandaki yarıçapı, büyük değerin boyu ve dikey yeri (dış yarıçapa oran). */
        const val G_RANGE = 0.42f
        const val G_VALUE = 0.30f
        const val G_VALUE_Y = 0.64f
    }
}

/**
 * Spor zemin görseli (`res/drawable-nodpi/sport_ground.jpg`, 1920 × 720: ortada ışık alan karbon, yanlarda süet ve
 * gerçek kırmızı çift dikiş, kenar kararması; kendi OpenAI çizimimiz). Bir kez çözülür (~5,5 MB), spor kapanınca
 * bırakılır. İlk çözüm arka planda ([SportStageView] durağan çizimiyle aynı iş parçacığında) yapılır.
 */
internal object SportGroundImage {
    @Volatile private var bitmap: Bitmap? = null

    fun load(context: Context): Bitmap? {
        bitmap?.let { return it }
        synchronized(this) {
            bitmap?.let { return it }
            val opts = android.graphics.BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = try {
                android.graphics.BitmapFactory.decodeResource(context.resources, com.mapcontrol.R.drawable.sport_ground, opts)
            } catch (t: Throwable) { null }
            bitmap = bmp
            return bmp
        }
    }

    /** Spor kapandı: başvuru bırakılır (geri dönüştürülmez; çizimde kalan bir kare güvende). */
    fun release() {
        bitmap = null
    }
}

/**
 * Spor modunda kök zemin: hazır zemin görseli ekranı kaplar (genişliğe ölçeklenir, üstten hizalı; yükseklik yetmezse
 * kaplayacak kadar büyür). Görsel yoksa (bellek) düz koyu zemin. Tek bitmap çizimi; gradyan ya da karo yok.
 */
internal class SportGround(context: Context) : Drawable() {
    private val image: Bitmap? = SportGroundImage.load(context)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()

    override fun onBoundsChange(b: Rect) {
        val bmp = image ?: return
        if (b.isEmpty) return
        // Kaplama: ölçek = max(genişlik, yükseklik oranı); kaynak dikdörtgeni yatayda ortalanır, dikeyde üstten.
        val k = maxOf(b.width().toFloat() / bmp.width, b.height().toFloat() / bmp.height)
        val sw = (b.width() / k).toInt().coerceIn(1, bmp.width)
        val sh = (b.height() / k).toInt().coerceIn(1, bmp.height)
        val sx = (bmp.width - sw) / 2
        src.set(sx, 0, sx + sw, sh)
    }

    override fun draw(canvas: Canvas) {
        val bmp = image
        if (bmp == null) { canvas.drawColor(0xFF07080A.toInt()); return }
        canvas.drawBitmap(bmp, src, bounds, paint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

/**
 * Talaşlı metal düğme çerçevesi (yuvarlak): fırçalanmış halka, koyu iç yüz; [knurled] ise dış kenarda tırtıl.
 * Sınırların ortasına, kısa kenara sığacak çapta çizilir. Gölgelendiriciler [onBoundsChange]'de bir kez kurulur.
 */
internal class SportKnobDrawable(private val context: Context, private val knurled: Boolean = false, private val insetDp: Float = 2f) : Drawable() {
    private var cache: Bitmap? = null

    override fun onBoundsChange(b: Rect) {
        cache = null
    }

    private fun build(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f - context.dpF(insetDp)
        val bw = r * if (knurled) 0.2f else 0.13f
        SportArt.bezel(c, cx, cy, r, bw, bright = 0.92f)
        if (knurled) SportArt.knurl(c, cx, cy, r - 1f, bw * 0.75f, 96)
        SportArt.face(c, cx, cy, r - bw - 1.5f, lift = 0.25f)
        return bmp
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val bmp = cache ?: build(b.width(), b.height()).also { cache = it }
        canvas.drawBitmap(bmp, b.left.toFloat(), b.top.toFloat(), null)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Metal çerçeveli hap / plaka (bağlantı ve hava kapsülü, saat plakası); iç yüz koyu, karbon görünmez. */
internal class SportPillDrawable(private val context: Context, private val radiusDp: Float = 999f, private val frameDp: Float = 2.5f) : Drawable() {
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = 0xFF040405.toInt() }
    private val rect = RectF()
    private val inner = RectF()
    private var radius = 0f

    override fun onBoundsChange(b: Rect) {
        val fw = context.dpF(frameDp)
        frame.strokeWidth = fw
        rect.set(b.left + fw / 2f, b.top + fw / 2f, b.right - fw / 2f, b.bottom - fw / 2f)
        inner.set(rect.left + fw / 2f + 0.5f, rect.top + fw / 2f + 0.5f, rect.right - fw / 2f - 0.5f, rect.bottom - fw / 2f - 0.5f)
        radius = min(context.dpF(radiusDp), b.height() / 2f)
        frame.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(),
            intArrayOf(SportInk.METAL_HI, SportInk.METAL_MID, SportInk.METAL_LO, SportInk.METAL_MID), floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        fill.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(), 0xFF1A1C20.toInt(), 0xFF08090A.toInt(), Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(rect, radius, radius, frame)
        val ir = (radius - frame.strokeWidth).coerceAtLeast(0f)
        canvas.drawRoundRect(inner, ir, ir, hair)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Talaşlı metal çerçeveli hap (başlıktaki hava ve bağlantı kapsülü): sol düğmelerdeki ([SportKnobDrawable]) fırçalanmış
 * halkanın hap biçimi; açısal metal bantlar, eşmerkezli fırça izleri, iç pah ve üstten düşen ışık. Boyut başına bir kez
 * önbellek bitmap'ine çizilir.
 */
internal class SportBezelPillDrawable(private val context: Context, private val bandDp: Float = 5f) : Drawable() {
    private var cache: Bitmap? = null

    override fun onBoundsChange(b: Rect) {
        cache = null
    }

    private fun build(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val inset = context.dpF(1f)
        val o = RectF(inset, inset, w - inset, h - inset)
        val r = o.height() / 2f
        val bw = context.dpF(bandDp)
        p.color = 0xFF040405.toInt()
        c.drawRoundRect(o, r, r, p)
        // Metal bant (açısal geçiş, kadran çerçevesiyle aynı tonlar).
        val cx = w / 2f
        val cy = h / 2f
        val hi = SportArt.scale(SportInk.METAL_HI, 0.92f)
        val mid = SportArt.scale(SportInk.METAL_MID, 0.92f)
        val lo = SportArt.scale(SportInk.METAL_LO, 0.92f)
        val deep = SportArt.scale(SportInk.METAL_DEEP, 0.92f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = bw
        p.shader = SweepGradient(cx, cy, intArrayOf(mid, hi, mid, lo, deep, lo, mid, hi, mid, lo, deep, lo, mid),
            floatArrayOf(0f, 0.07f, 0.15f, 0.24f, 0.32f, 0.41f, 0.5f, 0.57f, 0.65f, 0.74f, 0.82f, 0.91f, 1f))
        val m = RectF(o.left + bw / 2f, o.top + bw / 2f, o.right - bw / 2f, o.bottom - bw / 2f)
        c.drawRoundRect(m, r - bw / 2f, r - bw / 2f, p)
        p.shader = null
        // Fırça izleri.
        p.strokeWidth = 0.7f
        for (i in 1..4) {
            val k = bw * i / 5f
            p.color = if (i % 2 == 0) 0x1AFFFFFF else 0x1F000000
            c.drawRoundRect(RectF(o.left + k, o.top + k, o.right - k, o.bottom - k), r - k, r - k, p)
        }
        // Dış koyu kenar, iç pah (ince ışık + koyu çizgi).
        p.strokeWidth = 1.2f
        p.color = 0xFF020203.toInt()
        c.drawRoundRect(RectF(o.left + 0.6f, o.top + 0.6f, o.right - 0.6f, o.bottom - 0.6f), r - 0.6f, r - 0.6f, p)
        val ir = r - bw
        val inner = RectF(o.left + bw, o.top + bw, o.right - bw, o.bottom - bw)
        // İç yüz: üstten hafif ışık, koyu.
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, inner.top, 0f, inner.bottom, intArrayOf(0xFF202227.toInt(), 0xFF0D0E10.toInt(), 0xFF070708.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(inner, ir, ir, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = 0x59FFFFFF
        c.drawRoundRect(RectF(inner.left - 0.5f, inner.top - 0.5f, inner.right + 0.5f, inner.bottom + 0.5f), ir + 0.5f, ir + 0.5f, p)
        p.color = 0xFF050506.toInt()
        p.strokeWidth = 1.6f
        c.drawRoundRect(RectF(inner.left + 1.3f, inner.top + 1.3f, inner.right - 1.3f, inner.bottom - 1.3f), ir - 1.3f, ir - 1.3f, p)
        // Üstten düşen ışık: dış kenarın üst yarısında ince parıltı.
        p.strokeWidth = 1.2f
        p.shader = LinearGradient(0f, o.top, 0f, cy, 0x99FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(o.left + 1.4f, o.top + 1.4f, o.right - 1.4f, o.bottom - 1.4f), r - 1.4f, r - 1.4f, p)
        return bmp
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val bmp = cache ?: build(b.width(), b.height()).also { cache = it }
        canvas.drawBitmap(bmp, b.left.toFloat(), b.top.toFloat(), null)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Spor alt çubuğu: zemin görselinin koyu bir kesiti (yarı saydam koyu dolgu, altındaki görsel karartılmış görünür) ve
 * fırçalanmış metal çerçeve. Dikiş ve vida yok (dikiş zemin görselinde). Bir kez önbellek bitmap'ine çizilir.
 */
internal class SportPanelDrawable(
    private val context: Context,
    private val radiusDp: Float,
    /** Kadranların üstünde açılan panel (seçici, özet kartı): dolgu opak, alttaki çizim görünmez. */
    private val solid: Boolean = false,
) : Drawable() {
    private var cache: Bitmap? = null

    override fun onBoundsChange(b: Rect) {
        cache = null
    }

    private fun build(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fw = context.dpF(3f)
        val r = min(context.dpF(radiusDp), h / 2f)
        val rect = RectF(fw / 2f, fw / 2f, w - fw / 2f, h - fw / 2f)
        // Koyu kesit: üstte biraz daha saydam (görselin dokusu sezilir), altta koyu.
        c.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                if (solid) intArrayOf(0xFF17191C.toInt(), 0xFF0B0C0E.toInt(), 0xFF060607.toInt()) else intArrayOf(0xB3050607.toInt(), 0xC7050607.toInt(), 0xE6030304.toInt()),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        })
        // Fırçalanmış metal çerçeve: üstten ışık alan dikey geçiş (köşeler ve uçlar aynı tonda; parlak bant uçta taşmaz).
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = fw
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                intArrayOf(SportInk.METAL_LO, SportInk.METAL_LO, SportInk.METAL_DEEP, SportInk.METAL_LO), floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(rect, r, r, frame)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = 0xFF030304.toInt() }
        val ir = (r - fw).coerceAtLeast(0f)
        c.drawRoundRect(RectF(rect.left + fw / 2f + 0.5f, rect.top + fw / 2f + 0.5f, rect.right - fw / 2f - 0.5f, rect.bottom - fw / 2f - 0.5f), ir, ir, edge)
        // Üst kenardaki ince parlak çizgi yalnız düz kısmın içinde: iki uçta [HIGHLIGHT_END_DP] önce saydamlaşır (sağ uçtaki
        // FrostNav okunun altına uzanmaz; köşeye taşmaz), çubuğun içinde biter.
        val x0 = rect.left + r + context.dpF(HIGHLIGHT_END_DP)
        val x1 = rect.right - r - context.dpF(HIGHLIGHT_END_DP)
        if (x1 > x0) {
            val hl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = maxOf(1f, fw * 0.4f)
                strokeCap = Paint.Cap.BUTT
                shader = LinearGradient(x0, 0f, x1, 0f, intArrayOf(0x00FFFFFF, 0xB3FFFFFF.toInt(), 0x8CFFFFFF.toInt(), 0xB3FFFFFF.toInt(), 0x00FFFFFF),
                    floatArrayOf(0f, 0.12f, 0.5f, 0.88f, 1f), Shader.TileMode.CLAMP)
            }
            val y = rect.top - fw / 2f + hl.strokeWidth / 2f + 0.5f
            c.drawLine(x0, y, x1, y, hl)
        }
        return bmp
    }

    companion object {
        /** Üst parlak çizginin uçlarda düz kısmın bitişinden ne kadar önce sönmüş olduğu. */
        const val HIGHLIGHT_END_DP = 96f
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val bmp = cache ?: build(b.width(), b.height()).also { cache = it }
        canvas.drawBitmap(bmp, b.left.toFloat(), b.top.toFloat(), null)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Araç kadranında üstten düşen ışık: aracın ve yüzün üst yarısına yumuşak, soğuk beyaz bir parıltı (gradyan; bulanıklık
 * yok). Aracın üstünde ayrı, dokunulmaz bir görünüm; bir kez çizilir.
 */
internal class CarLightView(context: Context) : android.view.View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun hasOverlappingRendering(): Boolean = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val r = w / 2f
        paint.shader = RadialGradient(r, r * 0.62f, r * 0.62f, intArrayOf(0x2EDCE6F2, 0x12DCE6F2, 0x00DCE6F2), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val r = width / 2f
        canvas.drawCircle(r, r * 0.62f, r * 0.62f, paint)
    }
}
