package com.mapcontrol.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.mapcontrol.R

/**
 * Şeritteki Bagaj görseli: başka bir araç değil, sahnedeki aracın kendisi. Seçili boyanın (`car_<renk>_*`) üstten görünüşünün
 * arka kısmı kırpılır (burun yukarı döndürülür); bagaj açıkken sahne animasyonunun son karesi (boşluk + açık bagaj fotoğrafı) üstüne konur. Renk
 * ayarı değişince görsel de değişir. Görsel renk ve durum başına bir kez çizilir ve tutulur.
 */
internal object TrunkArt {
    private val cache = HashMap<String, Bitmap?>()

    /** Kırpma (base pikseli): bagaj açılınca araçtan geriye taşan kapak dahil, arka kapılara kadar. */
    private const val CROP_LEFT = -60f
    private const val CROP_RIGHT = 200f
    private const val CROP_TOP = 6f
    private const val CROP_BOTTOM = 350f

    fun key(context: Context, open: Boolean): String = "trunk_" + CarColorPrefs.get(context).key + if (open) "_open" else ""

    fun drawable(context: Context, open: Boolean): Drawable? {
        val k = key(context, open)
        val bmp = cache.getOrPut(k) { compose(context, CarColorPrefs.get(context), open) } ?: return null
        return BitmapDrawable(context.resources, bmp)
    }

    private fun compose(context: Context, color: CarColor, open: Boolean): Bitmap? = try {
        val res = context.resources
        val pkg = res.getResourcePackageName(R.drawable.car_grafit_base)
        fun load(name: String): Bitmap? {
            val id = res.getIdentifier("car_${color.key}_$name", "drawable", pkg)
            return if (id == 0) null else BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false })
        }
        val base = load("base")
        if (base == null) null else {
            // Döndürülmüş: burun yukarıda, arka (bagaj) aşağıda; dar şerit kutusunda araç daha büyük ve bagaj kapağı aşağı açılır.
            val out = Bitmap.createBitmap((CROP_BOTTOM - CROP_TOP).toInt(), (CROP_RIGHT - CROP_LEFT).toInt(), Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            c.translate(-CROP_TOP, CROP_RIGHT)
            c.rotate(-90f)
            c.drawBitmap(base, 0f, 0f, p)
            if (open) {
                val spec = CarAnimSpec.part(CarPart.TRUNK)
                load("gap_" + CarPart.TRUNK.asset)?.let { c.drawBitmap(it, spec.gap.x, spec.gap.y, p) }
                load("overlay_" + CarPart.TRUNK.asset)?.let { c.drawBitmap(it, spec.overlay.x, spec.overlay.y, p) }
            }
            out
        }
    } catch (e: OutOfMemoryError) {
        null
    }
}
