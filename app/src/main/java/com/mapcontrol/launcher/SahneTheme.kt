package com.mapcontrol.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.widget.TextView
import com.mapcontrol.R

/**
 * Sahne görünümünün renk jetonları: gece grafit cam, gündüz buzlu inci. Değerler onaylı tasarım taslağından
 * (premium-b) alındı; yarı saydam cam renkleri koyu/açık zemin üstünde önceden harmanlanmış opak tonlardır, böylece
 * gölge camın altından görünmez ve çizimde bulanıklık (blur) gerekmez. Gündüz/gece [LauncherTheme.night]'tan okunur.
 */
object Sahne {
    private val n get() = LauncherTheme.night

    /**
     * Zemin: düz renk ve dikey gradyanın üç durağı. Gece saf siyah: aracın kendi alt çubuğunun çevresi (yanlar, üstündeki
     * 610–628 bandı, altı) #000000; zemin de aynı olunca çubukla launcher arasında bant farkı kalmaz. Paneller tonla ayrılır.
     */
    val GROUND: Int get() = if (n) 0xFF000000.toInt() else 0xFFE4E2DD.toInt()
    val GROUND_TOP: Int get() = if (n) 0xFF000000.toInt() else 0xFFEFEEEA.toInt()
    val GROUND_MID: Int get() = if (n) 0xFF000000.toInt() else 0xFFE6E4DF.toInt()
    val GROUND_BOTTOM: Int get() = if (n) 0xFF000000.toInt() else 0xFFDAD8D2.toInt()
    /**
     * Ortadaki ve üstteki yumuşak ortam ışığı (radyal, merkezde bu renk, kenarda saydam). Gece yok (saydam): zemin her
     * yerde saf siyah kalsın; araç altındaki ışık havuzu sahnenin kendi zemin çiziminde (SahneStage.FloorView) durur.
     */
    val AMBIENT_CENTER: Int get() = if (n) 0x00000000 else 0x73FFFDF8
    val AMBIENT_TOP: Int get() = if (n) 0x00000000 else 0xE6FFFFFF.toInt()

    val INK: Int get() = if (n) 0xFFEEF2F7.toInt() else 0xFF111419.toInt()
    /** İkinci kademe mürekkep (birim, başlık): gece aracın alt çubuğundaki çelik mavisi ailesinden, [INK_3]'ten bir ton açık. */
    val INK_2: Int get() = if (n) 0xFFB9C3D4.toInt() else 0xBD111419.toInt()
    /**
     * İkincil yazı (etiket, alt satır): gece aracın alt çubuğundaki ikon/sıcaklık yazısının çelik mavisi (#ABB7CA; siyah
     * zeminde ~10:1, panel camında ~8:1), gündüz ~7:1 kontrast (opak). Mavi vurgu yalnızca etkin/birincil öğede kalır.
     */
    val INK_3: Int get() = if (n) 0xFFABB7CA.toInt() else 0xFF55575C.toInt()
    /** Büyük rakamlar (menzil, saat, sıcaklık): gece kâğıt beyazı yerine gri-beyaz (karanlık uyumu bozulmasın). */
    val NUMERAL: Int get() = if (n) 0xFFDCE1E8.toInt() else INK
    /** Açık kapı / bagaj / kaput uyarısı (sahne satırı, Bagaj "Açık"). */
    val WARN: Int get() = if (n) 0xFFF5A524.toInt() else 0xFFB45309.toInt()
    val HAIR: Int get() = if (n) 0x12FFFFFF else 0x17181E28
    val HAIR_2: Int get() = if (n) 0x1CFFFFFF else 0x21181E28

    val AMBER: Int get() = if (n) 0xFFFFA24A.toInt() else 0xFFB4500A.toInt()
    val AMBER_HI: Int get() = if (n) 0xFFFFC07E.toInt() else 0xFFE2852F.toInt()
    val AMBER_SOFT: Int get() = if (n) 0x24FFA24A else 0x24E2852F
    val AMBER_LINE: Int get() = if (n) 0x52FFA24A else 0x4DB4500A
    val ICE: Int get() = if (n) 0xFFA9D1FF.toInt() else 0xFF1D5FC4.toInt()
    val ICE_LO: Int get() = if (n) 0xFF6F9FD8.toInt() else 0xFF5F94DE.toInt()
    val COOL: Int get() = if (n) 0xFF7FE0F0.toInt() else 0xFF0E7C93.toInt()
    val OK: Int get() = if (n) 0xFF86E3B0.toInt() else 0xFF17935A.toInt()
    val ALERT: Int get() = if (n) 0xFFFF7A6E.toInt() else 0xFFC4352B.toInt()

    /** Cam: üst/alt (opak, zemine harmanlanmış), üst kenar parıltısı, kenar çizgisi. */
    val GLASS_TOP: Int get() = if (n) 0xFF1A1F27.toInt() else 0xFFFDFCFB.toInt()
    val GLASS_BOTTOM: Int get() = if (n) 0xFF0D1014.toInt() else 0xFFF3F2EF.toInt()
    val GLASS_HIGHLIGHT: Int get() = if (n) 0x12FFFFFF else 0xF2FFFFFF.toInt()
    val GLASS_BORDER: Int get() = if (n) 0x12FFFFFF else 0x14181E28
    /** Geniş yumuşak gölge ve sıkı temas gölgesi. */
    val SHADOW: Int get() = if (n) 0xB3000000.toInt() else 0x42262A34
    val SHADOW_TIGHT: Int get() = if (n) 0x73000000 else 0x1A262A34

    /**
     * Başlık ve alt çubuk denetimleri (sahibinin isteği): gecede panellerden bir ton açık cam, net 1dp üst parıltı,
     * belirgin kenar ve ~%85 beyaz ikonlar; gündüz panel camıyla aynı. Bu jetonlar alt çubuğumuzda da kullanılır.
     */
    val CONTROL_TOP: Int get() = if (n) 0xFF252C37.toInt() else GLASS_TOP
    val CONTROL_BOTTOM: Int get() = if (n) 0xFF161B23.toInt() else GLASS_BOTTOM
    val CONTROL_BORDER: Int get() = if (n) 0x29FFFFFF else GLASS_BORDER
    val CONTROL_HIGHLIGHT: Int get() = if (n) 0x40FFFFFF else GLASS_HIGHLIGHT
    val CONTROL_ICON: Int get() = if (n) 0xD9FFFFFF.toInt() else INK_2
    /**
     * Başlık ve şerit ikonları (yalnızca bunlar; alt çubuğumuz [CONTROL_ICON]'da kalır): gece aracın alt çubuk ikonlarıyla
     * aynı çelik mavisi (#AEB9CC); gündüz [CONTROL_ICON] ile aynı.
     */
    val HEADER_ICON: Int get() = if (n) 0xFFAEB9CC.toInt() else INK_2
    /** Başlıkta kapalı durumdaki ikon (Bluetooth kapalı): gece [HEADER_ICON]'un %65'i, açık/bağlı halinden belirgin sönük (~3,4:1). */
    val HEADER_ICON_OFF: Int get() = if (n) 0xA6AEB9CC.toInt() else INK_3
    /** Başlıktaki tarih ve hava etiketi: gece [INK_2] çelik mavisi (siyah zeminde ~11:1); gündüz [HEADER_DATE] ile aynı. */
    val HEADER_SUB: Int get() = if (n) INK_2 else HEADER_DATE
    /** Şerit kutuları arasındaki ince çizgi: gece çelik mavisi %25 (siyah zeminde kutular bir grup okunsun); gündüz [HAIR_2]. */
    val STRIP_HAIR: Int get() = if (n) 0x40ABB7CA else HAIR_2
    /** Gece başlıktaki saat/tarih: tam ve %85 mürekkep (ince saat koyu zeminde silik kalmasın). */
    val HEADER_DATE: Int get() = if (n) 0xD9EEF2F7.toInt() else INK_2

    /**
     * Arama renkleri: yalnızca arama düğmeleri (Cevapla yeşil, Reddet/Kapat kırmızı) ve cevapsız arama satırı. Dolgunun
     * üstünde ikon ve yazı [CALL_ON_INK] (beyaz).
     */
    val CALL_ACCEPT: Int get() = if (n) 0xFF2FBF71.toInt() else 0xFF17935A.toInt()
    val CALL_REJECT: Int get() = if (n) 0xFFE5484D.toInt() else 0xFFD13438.toInt()
    val CALL_MISSED: Int get() = if (n) 0xFFFF7A7A.toInt() else 0xFFC62828.toInt()
    val CALL_ON_INK: Int get() = 0xFFFFFFFF.toInt()

    val SOFT_FILL: Int get() = if (n) 0x0DFFFFFF else 0x0B141A24
    val TRACK: Int get() = if (n) 0x1FFFFFFF else 0x26141A24

    /**
     * Birincil düğme (oynat): gündüz grafit; gece parlamasın diye koyu grafit dolgu, 1dp halka ve gri-beyaz ikon
     * (gece en parlak piksel içerik olsun, düğme değil).
     */
    val CTA_TOP: Int get() = if (n) 0xFF262B33.toInt() else 0xFF2A2F37.toInt()
    val CTA_BOTTOM: Int get() = if (n) 0xFF262B33.toInt() else 0xFF14171C.toInt()
    val CTA_INK: Int get() = if (n) 0xFFF2F4F7.toInt() else 0xFFFFFFFF.toInt()
    /** Oynat düğmesinin gece halkası (gündüz yok). */
    val CTA_RING: Int get() = if (n) 0xFF343A44.toInt() else 0
    /** Etkin ana ekran tuşu: gece koyu dolgu + vurgu renkli ikon; gündüz grafit dolgu + beyaz ikon. */
    val HOME_TOP: Int get() = if (n) 0xFF232830.toInt() else CTA_TOP
    val HOME_BOTTOM: Int get() = if (n) 0xFF232830.toInt() else CTA_BOTTOM
    val HOME_INK: Int get() = if (n) 0xFF3B82F6.toInt() else CTA_INK
    val KNOB: Int get() = if (n) 0xFFF4F7FB.toInt() else 0xFFFFFFFF.toInt()

    /** Sahne zemini: ışık havuzu ve temas gölgesi. */
    val FLOOR_INNER: Int get() = if (n) 0x29BED0EC else 0xF2FFFFFF.toInt()
    val FLOOR_OUTER: Int get() = if (n) 0x1F6E84A8 else 0x8CFFFFFF.toInt()
    val CONE: Int get() = if (n) 0x14AAC3EB else 0x8CFFFFFF.toInt()
    val CAR_SHADOW: Int get() = if (n) 0x99000000.toInt() else 0x52282620
    val CAR_CONTACT: Int get() = if (n) 0xB3000000.toInt() else 0x611E1C18
    /** Gece far ve stop ışığının zemindeki yansıması; gündüz yok. */
    val HEAD_GLOW: Int get() = if (n) 0x24DCE8FF else 0
    val TAIL_GLOW: Int get() = if (n) 0x0DFF3B30 else 0

    /** Gündüz yazı ağırlıkları gece olandan bir kademe kalındır (açık zeminde ince yazı soluk durur). */
    fun weight(base: Int): Int = if (n) base else minOf(700, base + 100)

    /** Biçim dili: kaplar (panel, şerit, alt çubuk, sayfa) 24dp, içlerindeki denetimler 16dp, haplar tam yuvarlak. */
    const val PANEL_RADIUS_DP = 24f
    const val SQUARE_RADIUS_DP = 16f
    const val CONTROL_RADIUS_DP = 16f
    const val PILL_RADIUS_DP = 999f
}

/**
 * Manrope (OFL, res/font) yazı ailesi; her ağırlık bir kez yüklenir. Rakamlar sabit genişlikte ("tnum").
 * Yüklenemezse sistem yazısına düşülür.
 */
object SahneFonts {
    private val cache = HashMap<Int, Typeface>()

    fun get(context: Context, weight: Int): Typeface {
        val w = when {
            weight <= 300 -> 300
            weight <= 400 -> 400
            weight <= 500 -> 500
            weight <= 600 -> 600
            else -> 700
        }
        cache[w]?.let { return it }
        val res = when (w) {
            300 -> R.font.manrope_light
            400 -> R.font.manrope_regular
            500 -> R.font.manrope_medium
            600 -> R.font.manrope_semibold
            else -> R.font.manrope_bold
        }
        val tf = try {
            context.resources.getFont(res)
        } catch (e: RuntimeException) {
            Typeface.create("sans-serif", if (w >= 600) Typeface.BOLD else Typeface.NORMAL)
        }
        cache[w] = tf
        return tf
    }

    /** Yazıya Sahne ağırlığını verir (gündüz bir kademe kalın, bkz. [Sahne.weight]; [dayBump] false ise iki temada aynı). */
    fun apply(tv: TextView, baseWeight: Int, letterSpacingEm: Float = 0f, dayBump: Boolean = true) {
        tv.typeface = get(tv.context, if (dayBump) Sahne.weight(baseWeight) else baseWeight)
        tv.fontFeatureSettings = "tnum"
        tv.letterSpacing = letterSpacingEm
    }
}

/**
 * Sahne camı: opak dikey gradyan, 1dp kenar, üst kenarda ince parıltı ve dışa taşan yumuşak gölge. Gölge
 * [Paint.setShadowLayer] ile donanımda çizilir (blur shader yok); taşabilmesi için ebeveynin `clipChildren=false`
 * olması gerekir. Boyalar ve gradyan yalnızca boyut değişince kurulur; çizimde ayırma yok.
 */
class GlassDrawable(
    private val context: Context,
    private val radiusDp: Float = Sahne.PANEL_RADIUS_DP,
    /** Gölge gücü (0 = yok). Küçük düğmelerde düşük tutulur. */
    private val shadow: Float = 1f,
    /** Doluluk renkleri; null ise cam jetonları. */
    private val top: Int? = null,
    private val bottom: Int? = null,
    private val border: Int? = null,
    /** Denetim camı (başlık, alt çubuk): [Sahne.CONTROL_TOP] dolgu, net üst parıltı ve belirgin kenar. */
    private val control: Boolean = false,
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val inner = RectF()
    private var radius = 0f
    /** Gece gölge yok (zemin saf siyah, paneller tonla ayrılır); gündüz [shadow] gücünde. [refresh]'te güncellenir. */
    private var shadowOn = false

    init { refresh() }

    /** Tema değişti: renkler yeniden okunur. */
    fun refresh() {
        val d = context.resources.displayMetrics.density
        stroke.strokeWidth = d
        stroke.color = border ?: if (control) Sahne.CONTROL_BORDER else Sahne.GLASS_BORDER
        highlight.strokeWidth = d
        shadowPaint.color = Sahne.GLASS_BOTTOM
        tightPaint.color = Sahne.GLASS_BOTTOM
        shadowOn = shadow > 0f && !LauncherTheme.night
        if (shadowOn) {
            shadowPaint.setShadowLayer(22f * d * shadow, 0f, 14f * d * shadow, Sahne.SHADOW)
            tightPaint.setShadowLayer(4f * d * shadow, 0f, 2f * d * shadow, Sahne.SHADOW_TIGHT)
        } else {
            shadowPaint.clearShadowLayer()
            tightPaint.clearShadowLayer()
        }
        if (!bounds.isEmpty) onBoundsChange(bounds)
        invalidateSelf()
    }

    override fun onBoundsChange(b: Rect) {
        val d = context.resources.displayMetrics.density
        rect.set(b)
        radius = minOf(context.dpF(radiusDp), rect.height() / 2f, rect.width() / 2f)
        fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
            top ?: if (control) Sahne.CONTROL_TOP else Sahne.GLASS_TOP, bottom ?: if (control) Sahne.CONTROL_BOTTOM else Sahne.GLASS_BOTTOM, Shader.TileMode.CLAMP)
        inner.set(rect.left + d / 2, rect.top + d / 2, rect.right - d / 2, rect.bottom - d / 2)
        // Üst parıltı yalnızca üst kenarda: köşelerden aşağı doğru söner.
        val hl = if (control) Sahne.CONTROL_HIGHLIGHT else Sahne.GLASS_HIGHLIGHT
        // Denetimlerde parıltı net: üst kenarda tam renk, kısa mesafede söner.
        val fadeTo = rect.top + (if (control) minOf(radius * 0.45f, 10f * d) else radius * 0.9f) + d
        highlight.shader = LinearGradient(0f, rect.top, 0f, fadeTo, intArrayOf(hl, hl, hl and 0x00FFFFFF),
            floatArrayOf(0f, if (control) 0.35f else 0f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        if (shadowOn) {
            canvas.drawRoundRect(rect, radius, radius, shadowPaint)
            canvas.drawRoundRect(rect, radius, radius, tightPaint)
        }
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(inner, radius, radius, highlight)
        if (stroke.color != 0) canvas.drawRoundRect(inner, radius, radius, stroke)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Sahne zemini: dikey gradyan ve iki eliptik ortam ışığı (ortada sahne, üstte tavan). Gradyanlar yalnızca boyut
 * değişince kurulur; zemin statiktir, hiçbir kare yeniden hesaplanmaz.
 */
class SahneGround : Drawable() {
    /*
     * Titreştirme (dither) şart: gradyanlar önbellek bitmap'ine yazılımla çizilir ve Skia orada yalnız dither açıksa
     * titreştirir. Kapalıyken 8 bit basamaklar ortadaki elipsin (cy 380) etrafında eş merkezli halkalar bırakıyordu;
     * yarı çözünürlükten 2× büyütülünce halkaların yan uçları dikey çizgilere dönüp isim satırının iki yanında soluk
     * "hayalet yazı" gibi görünüyordu (6 Kasım öykünücü, 11-telefon-sayfa.png y≈380).
     */
    private val base = Paint(Paint.DITHER_FLAG)
    private val center = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val top = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    /** Ortam ışıkları saydamsa (gece) tam ekran radyal çizim atlanır. */
    private var drawCenter = false
    private var drawTop = false

    override fun onBoundsChange(b: Rect) {
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return
        base.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Sahne.GROUND_TOP, Sahne.GROUND_MID, Sahne.GROUND_BOTTOM),
            floatArrayOf(0f, if (LauncherTheme.night) 0.55f else 0.46f, 1f), Shader.TileMode.CLAMP)
        // Taslak 1920x720 içindir; ölçüler genişliğe oranlanır.
        val s = w / 1920f
        // CSS radial-gradient(900px 420px at ...): ölçüler yarıçaptır.
        center.shader = ellipse(w / 2f, (if (LauncherTheme.night) 360f else 380f) * s, 900f * s, (if (LauncherTheme.night) 420f else 400f) * s, Sahne.AMBIENT_CENTER, if (LauncherTheme.night) 0.70f else 0.72f)
        top.shader = ellipse(w / 2f, (if (LauncherTheme.night) -120f else -170f) * s, (if (LauncherTheme.night) 1400f else 1500f) * s, (if (LauncherTheme.night) 500f else 480f) * s, Sahne.AMBIENT_TOP, 0.70f)
        drawCenter = Sahne.AMBIENT_CENTER ushr 24 != 0
        drawTop = Sahne.AMBIENT_TOP ushr 24 != 0
    }

    /**
     * Üç tam ekran gradyan her karede GPU'da yeniden doldurulmasın (6 Kasım araç: slow issue draw commands 1.169):
     * zemin bir kez yarı çözünürlükte bitmap'e çizilir, sonra tek doku olarak (süzgeçli) basılır. Gradyanlar yumuşak
     * olduğundan yarı çözünürlük görünmez; bellek 960x360x4 ≈ 1,4 MB. Tema değişince yeni [SahneGround] kurulur.
     */
    private var cache: android.graphics.Bitmap? = null
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    private fun buildCache(): android.graphics.Bitmap? {
        val b = bounds
        val cw = (b.width() + 1) / CACHE_DIV
        val ch = (b.height() + 1) / CACHE_DIV
        if (cw <= 0 || ch <= 0) return null
        val bmp = try {
            android.graphics.Bitmap.createBitmap(cw, ch, android.graphics.Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            return null
        }
        val c = Canvas(bmp)
        c.scale(cw / b.width().toFloat(), ch / b.height().toFloat())
        c.translate(-b.left.toFloat(), -b.top.toFloat())
        drawDirect(c)
        return bmp
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val cached = cache?.takeIf { it.width == (b.width() + 1) / CACHE_DIV && it.height == (b.height() + 1) / CACHE_DIV }
            ?: buildCache().also { cache?.recycle(); cache = it }
        if (cached == null) { drawDirect(canvas); return }
        canvas.drawBitmap(cached, null, b, blit)
    }

    private fun drawDirect(canvas: Canvas) {
        val b = bounds
        canvas.drawRect(b, base)
        if (drawTop) canvas.drawRect(b, top)
        if (drawCenter) canvas.drawRect(b, center)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    companion object {
        private const val CACHE_DIV = 2

        /** (cx, cy) merkezli, rx × ry yarıçaplı eliptik radyal gradyan: merkezde [color], [stop] oranında saydam. */
        fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, color: Int, stop: Float): Shader =
            RadialGradient(0f, 0f, rx, intArrayOf(color, color and 0x00FFFFFF), floatArrayOf(0f, stop), Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply {
                    setScale(1f, ry / rx)
                    postTranslate(cx, cy)
                })
            }
    }
}
