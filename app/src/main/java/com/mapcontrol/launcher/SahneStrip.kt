package com.mapcontrol.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.mapcontrol.R

/** Şerit öğesinin vurgu tonu: sıcaklık (turuncu), soğutma, açık (buz mavisi), uyarı, sessiz (veri yok). */
enum class StripTone { NORMAL, MUTED, ON, HEAT, COOL, ALERT }

/**
 * Değiştirilebilir illüstrasyonlar: bir şerit/panel öğesinin görseli res/drawable altındaki `launcher_art_<anahtar>` adlı bir
 * kaynaktır (ör. raster `launcher_art_seat_heat_driver.webp`); kaynak yoksa ince çizgili vektör ikon kullanılır. Anahtar
 * widget türünün kaydedilen anahtarıdır ([WidgetType.key]); duruma bağlı görseller için ayrı anahtar ("seat_heat_on"), panel
 * zemini için "panel_<anahtar>". Kod değişmeden görsel eklenip çıkarılabilir; arama bir kez yapılıp önbelleğe alınır.
 */
object SahneArt {
    private val ids = HashMap<String, Int>()

    /** `launcher_art_<key>` kimliği; yoksa 0. */
    fun resId(context: android.content.Context, key: String): Int = ids.getOrPut(key) {
        @Suppress("DiscouragedApi")
        context.resources.getIdentifier("launcher_art_$key", "drawable", context.packageName)
    }

    fun drawable(context: android.content.Context, key: String?): Drawable? {
        if (key == null) return null
        val id = resId(context, key)
        return if (id == 0) null else context.getDrawable(id)
    }

    /**
     * Duruma bağlı şerit görseli anahtarı (yoksa null): koltukta ısıtma açıkken "seat_heat_on", soğutma açıkken "seat_vent_on",
     * kapalıyken "seat_off"; diğer türlerde türün anahtarı. Kaynak yoksa null (vektör ikon kullanılır). Ayrılmış nesne üretmez.
     */
    fun stripKey(context: android.content.Context, type: WidgetType, s: StripState): String? {
        val key = when (type) {
            WidgetType.SEAT_DRIVER, WidgetType.SEAT_PASSENGER -> seatKey(s.tone, s.level)
            else -> type.key
        }
        return if (resId(context, key) == 0) null else key
    }

    /** Koltuk görseli: ısıtma (turuncu ton) / soğutma (buz tonu) seviyesi varsa kendi görseli, yoksa kapalı koltuk. */
    fun seatKey(tone: StripTone, level: Int): String = SeatChoice.artKey(when (tone) {
        StripTone.HEAT -> SeatMode.HEAT
        StripTone.COOL -> SeatMode.COOL
        else -> SeatMode.OFF
    }, level)

    /** Yolcu koltuğu yatay çevrilir (görsel sürücü koltuğuna bakar). */
    fun mirrored(type: WidgetType): Boolean = type == WidgetType.SEAT_PASSENGER

    /**
     * Koltuk görselleri kendi açık/kapalı halini taşır; diğer görseller [StripState.active] false iken (bagaj kapalı, arka
     * hoparlör kapalı, özellik kapalı) soluk çizilir. Durumu olmayan (null) türler hep tam renktir.
     */
    fun dimmed(type: WidgetType, s: StripState): Boolean =
        type != WidgetType.SEAT_DRIVER && type != WidgetType.SEAT_PASSENGER && s.active == false

    /** Kapalı/pasif görsel (buğu giderme kapalıyken): renksizleştirilmiş. Tek örnek, çizimde ayrılma yok. */
    val DESATURATE: android.graphics.ColorFilter = android.graphics.ColorMatrixColorFilter(
        android.graphics.ColorMatrix().apply { setSaturation(0.15f) })
    const val DIM_ALPHA = 140

    /** Gece: koyu grafit malzemeyi kaldırır (kontrast 1.3, +46 parlaklık); renk tonu korunur. Tek örnek. */
    private fun liftMatrix() = android.graphics.ColorMatrix(floatArrayOf(
        1.3f, 0f, 0f, 0f, 46f,
        0f, 1.3f, 0f, 0f, 46f,
        0f, 0f, 1.3f, 0f, 46f,
        0f, 0f, 0f, 1.25f, -45f))
    val NIGHT_LIFT: android.graphics.ColorFilter = android.graphics.ColorMatrixColorFilter(liftMatrix())
    /** Gece + kapalı: önce renksizleştir, sonra kaldır (kapalı öğeler yine açıklardan soluk kalır). */
    val NIGHT_LIFT_DESAT: android.graphics.ColorFilter = android.graphics.ColorMatrixColorFilter(
        android.graphics.ColorMatrix().apply { setSaturation(0.15f); postConcat(liftMatrix()) })

    /** Görsel filtresi: gece ve Sahne'de kaldırma, kapalıysa ayrıca renksiz; gündüz değişmez (null / DESATURATE). */
    fun filter(off: Boolean): android.graphics.ColorFilter? =
        if (LauncherTheme.night) (if (off) NIGHT_LIFT_DESAT else NIGHT_LIFT) else (if (off) DESATURATE else null)

    /** Gece: görselin arkasına yumuşak ışık lekesi (beyaz ~%9, kenarda sönümlenir); gündüz null. View başına bir kez üretilir. */
    fun spotlight(): Drawable? = if (!LauncherTheme.night) null else SpotDrawable()

    /** Sınırlar değişince bir kez RadialGradient kurar (çizimde ayrılma yok); merkez beyaz ~%9 → kenarda saydam. */
    private class SpotDrawable : Drawable() {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onBoundsChange(b: android.graphics.Rect) {
            // Kısa kenara göre: geniş kutuda da kenarlarda sert çizgi kalmaz.
            val r = minOf(b.width(), b.height()) / 2f
            paint.shader = if (r <= 0f) null else android.graphics.RadialGradient(
                b.exactCenterX(), b.exactCenterY(), r,
                intArrayOf(0x17FFFFFF, 0x0AFFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP)
        }
        override fun draw(canvas: android.graphics.Canvas) {
            if (paint.shader != null) canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), minOf(bounds.width(), bounds.height()) / 2f, paint)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** Panel zemini görseli (varsa); camın üstüne, içeriğin altına serilir. */
    fun panelBackground(context: android.content.Context, type: WidgetType): Drawable? = drawable(context, "panel_" + type.key)
}

/**
 * Bir widget'ın Sahne şeridindeki (ve sahnedeki notlardaki) özeti. Widget kendi verisinden üretir; şerit yalnızca çizer.
 * [level]/[levels] seviye dilimleri (koltuk), [iconDrawable] uygulama ikonu, [badge] değerin yanında küçük simge (ör. ay).
 */
data class StripState(
    val title: String,
    val value: String,
    @DrawableRes val icon: Int,
    val tone: StripTone = StripTone.NORMAL,
    val level: Int = 0,
    val levels: Int = 0,
    val detail: String? = null,
    val iconDrawable: Drawable? = null,
    /** Panel biçiminde değerin altında gösterilen ek satırlar (etiket, değer); [alertRows] uyarı renginde olanlar. */
    val rows: List<Pair<String, String>> = emptyList(),
    val alertRows: Set<Int> = emptySet(),
    /** Açık/etkin mi (bagaj, arka hoparlör); null = bilinmiyor/ilgisiz. Duruma bağlı görsel için. */
    val active: Boolean? = null,
    /** Değerin yanında küçük simge (ör. arka hoparlör kapalıyken ay); 0 = yok. */
    @DrawableRes val badge: Int = 0,
    /** Panel biçiminde değerin altında küçük çubuk geçmişi (ör. son tüketim ölçümleri, eskiden yeniye); boşsa çizilmez. */
    val history: List<Float> = emptyList(),
)

/**
 * Şerit biçimini destekleyen widget: durumunu [stripState] ile verir, değişince [onStripChanged]'i çağırır ve
 * dokunuşu kutucuktaki ile aynı yoldan ([stripTap]) işler; yazma kuralları (yazma politikası, doğrulama bayrakları,
 * onaylar) widget'ın kendi kodunda kalır. Desteklemeyen widget şeritte başlık + ikonla görünür ve
 * dokunuş kutucuğun kök görünümüne ([View.performClick]) gider.
 */
interface StripSource {
    fun stripState(): StripState?
    var onStripChanged: (() -> Unit)?
    /** [part]: öğe içindeki ayrı düğme (yoksa 0). */
    fun stripTap(part: Int = 0)
    /** Şerit öğesinden dokunuş: [anchor] öğenin kendisi (üstünde seçici açan widget'lar için, ör. koltuk). */
    fun stripTapFrom(anchor: View, part: Int = 0) = stripTap(part)
    /** Uzun basış (ör. uygulama seçici); işlendiyse true. */
    fun stripLongPress(): Boolean = false
}

/** Widget renginden şerit tonu: uyarı, ikincil (veri yok) ve vurgu renkleri tanınır. */
internal fun stripToneFor(color: Int): StripTone = when (color) {
    LauncherTheme.ALERT -> StripTone.ALERT
    LauncherTheme.TEXT_SECONDARY -> StripTone.MUTED
    LauncherTheme.ACCENT -> StripTone.ON
    LauncherTheme.HEAT -> StripTone.HEAT
    LauncherTheme.COOL -> StripTone.COOL
    else -> StripTone.NORMAL
}

/** Tonun Sahne rengi; [NORMAL] için [fallback]. */
internal fun stripToneColor(tone: StripTone, fallback: Int): Int = when (tone) {
    StripTone.HEAT -> Sahne.AMBER
    StripTone.COOL -> Sahne.ICE
    StripTone.ON -> Sahne.ICE
    StripTone.ALERT -> Sahne.WARN
    StripTone.MUTED -> Sahne.INK_3
    StripTone.NORMAL -> fallback
}

/**
 * Sahne şeridinin bir öğesi (taslaktaki ".seg"): görsel (illüstrasyon ya da ince ikon), küçük başlık, değer (tek satır, gerekirse
 * küçülür, asla kesilmez), değerin yanında isteğe bağlı küçük simge ve seviyesi olan widget'ta altta dilimler. Ölçüler tüm
 * öğelerde aynıdır ([Sahne] tip ölçeği: başlık [TITLE_SP], değer [VALUE_SP], görsel [ART_DP]). Yalnızca durum değişince yeniden bağlanır.
 */
@SuppressLint("ViewConstructor")
internal class StripItemView(
    context: Context,
    private val onTap: (Int) -> Unit,
    private val onLong: () -> Boolean,
    /** Widget türü: varsa duruma bağlı illüstrasyon ([SahneArt.stripKey]) vektör ikonun yerine renklendirilmeden çizilir. */
    private val type: WidgetType? = null,
) : LinearLayout(context) {

    private val iconView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    /** Görsel kutusu: tüm öğelerde aynı genişlik, böylece yazılar aynı hizadan başlar. */
    private val iconBox = android.widget.FrameLayout(context).apply {
        addView(iconView, android.widget.FrameLayout.LayoutParams(context.dp(ICON_DP), context.dp(ICON_DP), Gravity.CENTER))
    }
    private val title = text(context, TITLE_SP).apply {
        SahneFonts.apply(this, 400)
        maxLines = 1
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            this, TITLE_MIN_SP, TITLE_SP.toInt(), 1, TypedValue.COMPLEX_UNIT_SP)
    }
    private val value = text(context, VALUE_SP).apply {
        SahneFonts.apply(this, 500)
        maxLines = 1
        // Uzun değer şeritte kesilmez ve kaymaz: [VALUE_SP]'den küçülerek sığar.
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            this, VALUE_MIN_SP, VALUE_SP.toInt(), 1, TypedValue.COMPLEX_UNIT_SP)
    }
    private val badge = ImageView(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    private val valueRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(value, LayoutParams(0, context.dp(VALUE_H_DP), 1f))
        addView(badge, LayoutParams(context.dp(BADGE_DP), context.dp(BADGE_DP)).apply { marginStart = context.dp(6) })
    }
    private val pips = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    /** Başlık, değer ve altında seviye dilimleri. */
    private val texts = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(valueRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(pips, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(4) })
    }
    private var last: StripState? = null
    private var sizing = -1
    private var artKey: String? = null
    private var spot = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(PAD_DP), 0, context.dp(PAD_DP), 0)
        minimumHeight = context.dp(LauncherTheme.TOUCH_MIN_DP)
        addView(iconBox, LayoutParams(context.dp(ART_BOX_W_DP), context.dp(ART_DP)))
        addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(TEXT_GAP_DP) })
        isClickable = true
        isFocusable = true
        setOnClickListener { onTap(0) }
        setOnLongClickListener { onLong() }
        pressFeedback()
    }

    fun bind(s: StripState) {
        if (s == last) return
        val prev = last
        last = s
        val tint = stripToneColor(s.tone, Sahne.INK)
        val active = s.tone == StripTone.HEAT || s.tone == StripTone.COOL || s.tone == StripTone.ON || s.tone == StripTone.ALERT
        val key = if (s.iconDrawable == null && type != null) SahneArt.stripKey(context, type, s) else null
        // Bagaj: sahnedeki aracın kendisi (seçili boya, açık/kapalı), başka bir araç görseli değil.
        val trunkKey = if (type == WidgetType.TRUNK && s.iconDrawable == null) TrunkArt.key(context, s.active == true) else null
        if (trunkKey != null) {
            if (trunkKey != artKey) { artKey = trunkKey; iconView.setImageDrawable(TrunkArt.drawable(context, s.active == true)) }
            iconView.imageTintList = null
            applyArtLook(false, false, lift = false)
            resizeIcon(SIZE_ART)
        } else if (s.iconDrawable != null) {
            iconView.setImageDrawable(s.iconDrawable)
            iconView.imageTintList = null
            artKey = null
            applyArtLook(false, false)
            resizeIcon(SIZE_APP)
        } else if (key != null && type != null) {
            if (key != artKey) { artKey = key; iconView.setImageDrawable(SahneArt.drawable(context, key)) }
            iconView.imageTintList = null
            applyArtLook(SahneArt.mirrored(type), SahneArt.dimmed(type, s))
            resizeIcon(SIZE_ART)
        } else {
            if (artKey != null || prev?.icon != s.icon || prev.iconDrawable != null) iconView.setImageResource(sahneIconFor(s.icon))
            artKey = null
            applyArtLook(false, false)
            // Pasif çizgi ikon başlıktaki denetim ikonlarıyla aynı çelik mavisi; etkin tonlar kendi renginde.
            iconView.imageTintList = ColorStateList.valueOf(if (active) tint else Sahne.HEADER_ICON)
            resizeIcon(SIZE_ICON)
        }
        title.text = s.title
        title.setTextColor(Sahne.INK_3)
        title.visibility = if (s.title.isEmpty()) View.GONE else View.VISIBLE
        value.setTextSmooth(s.value)
        value.setTextColor(if (s.tone == StripTone.NORMAL) Sahne.INK else tint)
        valueRow.visibility = if (s.value.isEmpty()) View.GONE else View.VISIBLE
        if (s.badge != 0) {
            badge.setImageResource(s.badge)
            badge.imageTintList = ColorStateList.valueOf(Sahne.HEADER_ICON)
            badge.visibility = View.VISIBLE
        } else badge.visibility = View.GONE
        bindPips(s)
        contentDescription = listOf(s.title, s.value, s.detail).filter { !it.isNullOrEmpty() }.joinToString(", ")
    }

    /** [lift]: gece kaldırma filtresi (üretilmiş görseller); sahne aracının kendi fotoğrafında yok (sahnedekiyle aynı görünsün). */
    private fun applyArtLook(mirror: Boolean, dim: Boolean, lift: Boolean = true) {
        iconView.scaleX = if (mirror) -1f else 1f
        iconView.imageAlpha = if (dim) SahneArt.DIM_ALPHA else 255
        val art = artKey != null
        iconView.colorFilter = if (art && lift) SahneArt.filter(dim) else null
        val night = art && LauncherTheme.night
        if (night != spot) {
            spot = night
            iconBox.background = if (night) SahneArt.spotlight() else null
        }
    }

    /** Bagaj görseli araç rengini izler: renk ayarı değişince yeniden çizilir. */
    private val colorListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k == CarColorPrefs.KEY) last?.let { st -> last = null; artKey = null; bind(st) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (type == WidgetType.TRUNK) context.getSharedPreferences(CarColorPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(colorListener)
    }

    override fun onDetachedFromWindow() {
        if (type == WidgetType.TRUNK) context.getSharedPreferences(CarColorPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(colorListener)
        super.onDetachedFromWindow()
    }

    private fun resizeIcon(mode: Int) {
        if (mode == sizing) return
        sizing = mode
        val dp = when (mode) { SIZE_ART -> ART_DP; SIZE_APP -> APP_ICON_DP; else -> ICON_DP }
        iconView.layoutParams = (iconView.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
            width = context.dp(if (mode == SIZE_ART) ART_BOX_W_DP else dp)
            height = context.dp(dp)
        }
    }

    private fun bindPips(s: StripState) {
        if (s.levels <= 0) {
            pips.visibility = View.GONE
            return
        }
        pips.visibility = View.VISIBLE
        if (pips.childCount != s.levels) {
            pips.removeAllViews()
            repeat(s.levels) { i ->
                pips.addView(View(context), LayoutParams(context.dp(PIP_W_DP), context.dp(PIP_H_DP)).apply { if (i > 0) marginStart = context.dp(5) })
            }
        }
        val cool = s.tone == StripTone.COOL
        for (i in 0 until s.levels) {
            val on = i < s.level
            pips.getChildAt(i).background = if (!on) rounded(context, Sahne.TRACK, PIP_H_DP / 2f) else GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (cool) intArrayOf(Sahne.ICE, Sahne.ICE) else intArrayOf(Sahne.AMBER_HI, Sahne.AMBER),
            ).apply { cornerRadius = context.dpF(PIP_H_DP / 2f) }
        }
    }

    companion object {
        const val PAD_DP = 14
        const val ICON_DP = 36
        const val APP_ICON_DP = 48
        /** Illüstrasyon kutusu (şerit 92dp: 10dp pay): tüm öğelerde aynı, yazılar aynı hizadan başlar. */
        const val ART_DP = 72
        const val ART_BOX_W_DP = 80
        const val TEXT_GAP_DP = 10
        private const val SIZE_ICON = 0
        private const val SIZE_APP = 1
        private const val SIZE_ART = 2
        const val TITLE_SP = 20f
        const val TITLE_MIN_SP = 18
        const val VALUE_SP = 26f
        const val VALUE_MIN_SP = 16
        /** Değer satırı sabit yükseklikte: küçülen değer dikey ritmi bozmaz. */
        const val VALUE_H_DP = 34
        const val BADGE_DP = 20
        const val PIP_W_DP = 22
        const val PIP_H_DP = 6
    }
}

/**
 * Şeritte iki uygulama kısayolu yan yana (taslakta YouTube Music · Maps). Her yarı bir [AppShortcutTile]'dır; seçim ve
 * açma onun kodundadır (uzun basış uygulama seçici). Burada yalnızca ikon + ad yatay çizilir.
 */
@SuppressLint("ViewConstructor")
internal class AppPairStripView(context: Context, private val tiles: List<AppShortcutTile>) : LinearLayout(context) {

    private val buttons = tiles.map { tile ->
        AppButton(context, onClick = { tile.stripTap(0) }, onLong = { tile.stripLongPress() })
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(8), 0, context.dp(8), 0)
        // İki uygulama tek yuvada eşit paylı; her biri ikon üstte, ad altta tek satır (sığmazsa küçülür).
        buttons.forEachIndexed { i, b ->
            addView(b, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { if (i > 0) marginStart = context.dp(8) })
        }
        tiles.forEachIndexed { i, t ->
            t.onStripChanged = { t.stripState()?.let { buttons[i].bind(it) } }
            t.stripState()?.let { buttons[i].bind(it) }
        }
    }

    private class AppButton(context: Context, onClick: () -> Unit, onLong: () -> Boolean) : LinearLayout(context) {
        private val icon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private val label = text(context, LABEL_SP).apply {
            SahneFonts.apply(this, 500)
            maxLines = 1
            gravity = Gravity.CENTER
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 17, LABEL_SP.toInt(), 1, TypedValue.COMPLEX_UNIT_SP)
        }

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            addView(icon, LayoutParams(context.dp(ICON_DP), context.dp(ICON_DP)))
            addView(label, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(LABEL_BOX_DP)).apply { topMargin = context.dp(2) })
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            setOnLongClickListener { onLong() }
            pressFeedback()
        }

        private companion object {
            const val ICON_DP = 40
            const val LABEL_SP = 20f
            const val LABEL_BOX_DP = 26
        }

        fun bind(s: StripState) {
            if (s.iconDrawable != null) {
                icon.setImageDrawable(s.iconDrawable)
                icon.imageTintList = null
                icon.background = null
                icon.setPadding(0, 0, 0, 0)
            } else {
                // Boş yuva: sakin artı.
                icon.setImageResource(s.icon)
                icon.imageTintList = ColorStateList.valueOf(Sahne.INK_3)
                val p = context.dp(8)
                icon.setPadding(p, p, p, p)
                icon.background = rounded(context, Sahne.SOFT_FILL, 12f, Sahne.HAIR)
            }
            label.text = s.value
            label.setTextColor(if (s.tone == StripTone.MUTED) Sahne.INK_3 else Sahne.INK)
            contentDescription = s.value
        }
    }
}

/** Başlık + değer satırının taslak boyutlarıyla Sahne'ye uygun TextView'i. */
internal fun sahneText(context: Context, sizeSp: Float, color: Int, weight: Int, letterSpacingEm: Float = 0f, dayBump: Boolean = true): TextView =
    text(context, sizeSp, color).apply { SahneFonts.apply(this, weight, letterSpacingEm, dayBump) }

/** Kutucukların MDI ikonlarının Sahne'deki ince çizgili karşılıkları (yoksa aynısı). */
@DrawableRes
internal fun sahneIconFor(@DrawableRes res: Int): Int = when (res) {
    R.drawable.ic_mdi_car_seat_heater, R.drawable.ic_mdi_car_seat_vent -> R.drawable.ic_sahne_seat
    R.drawable.ic_mdi_car_trunk -> R.drawable.ic_sahne_trunk
    R.drawable.ic_mdi_music_note -> R.drawable.ic_sahne_note
    R.drawable.ic_mdi_road_variant -> R.drawable.ic_sahne_route
    R.drawable.ic_mdi_lock -> R.drawable.ic_sahne_lock
    R.drawable.ic_mdi_lock_open_variant -> R.drawable.ic_sahne_lock_open
    R.drawable.ic_mdi_car -> R.drawable.ic_sahne_car
    R.drawable.ic_mdi_air_filter -> R.drawable.ic_sahne_wind
    R.drawable.ic_mdi_fan -> R.drawable.ic_sahne_fan
    R.drawable.ic_mdi_web -> R.drawable.ic_sahne_qr
    R.drawable.ic_mdi_equalizer -> R.drawable.ic_sahne_eq
    R.drawable.ic_mdi_restart -> R.drawable.ic_sahne_auto
    R.drawable.ic_mdi_view_split -> R.drawable.ic_sahne_split
    R.drawable.ic_mdi_speedometer -> R.drawable.ic_sahne_cluster
    R.drawable.ic_mdi_view_grid -> R.drawable.ic_sahne_grid
    R.drawable.ic_mdi_volume_high -> R.drawable.ic_sahne_speaker
    R.drawable.ic_mdi_car_sports -> R.drawable.ic_sahne_dial
    R.drawable.ic_mdi_timer_sand -> R.drawable.ic_sahne_cup
    R.drawable.ic_mdi_weather_night -> R.drawable.ic_sahne_moon
    R.drawable.ic_mdi_cellphone -> R.drawable.ic_sahne_mobile
    R.drawable.ic_mdi_leaf -> R.drawable.ic_sahne_leaf
    R.drawable.ic_mdi_image -> R.drawable.ic_sahne_image
    R.drawable.ic_mdi_clock_outline -> R.drawable.ic_sahne_clock
    R.drawable.ic_mdi_car_cog -> R.drawable.ic_sahne_awd
    R.drawable.ic_mdi_lightning_bolt -> R.drawable.ic_sahne_health
    else -> res
}
