package com.mapcontrol.launcher

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.mapcontrol.BuildConfig
import java.io.File
import java.io.FileOutputStream

/**
 * Google Haritalar rehberlik bildiriminin büyük ikonundaki (manevra oku resmi) manevrayı tanır.
 *
 * Google talimat metninde çoğu zaman yalnız yol adı verir ("Ataşehir Blv."), dönüş yönü yalnız resimdedir. Şablonlar
 * cihazdaki Google Haritalar'ın kendi `maneuver_*` çizimlerinden üretilir (sürüm değişince yeniden), resim
 * [ManeuverMask] ile karşılaştırılır. Emin olunmazsa null döner; çağıran metne ya da "bilinmiyor"a düşer.
 */
internal object GoogleManeuverIcon {

    private const val PKG = "com.google.android.apps.maps"
    private const val TEMPLATE_PX = 96
    private const val MAX_ICON_PX = 512

    /** Haritalar 26.03'te bulunan çizimler; olmayan ad atlanır. */
    private val NAMES = listOf(
        "depart", "destination", "destination_left", "destination_right", "destination_straight",
        "fork_left", "fork_right", "keep_left", "keep_right", "merge", "merge_left", "merge_right", "name_change",
        "off_ramp_keep_left", "off_ramp_keep_right", "off_ramp_normal_left", "off_ramp_normal_right",
        "off_ramp_sharp_left", "off_ramp_sharp_right", "off_ramp_slight_left", "off_ramp_slight_right",
        "off_ramp_u_turn_left", "off_ramp_u_turn_right",
        "on_ramp_keep_left", "on_ramp_keep_right", "on_ramp_normal_left", "on_ramp_normal_right",
        "on_ramp_sharp_left", "on_ramp_sharp_right", "on_ramp_slight_left", "on_ramp_slight_right",
        "on_ramp_u_turn_left", "on_ramp_u_turn_right",
        "roundabout_enter_and_exit_ccw", "roundabout_enter_and_exit_ccw_normal_left",
        "roundabout_enter_and_exit_ccw_normal_right", "roundabout_enter_and_exit_ccw_sharp_left",
        "roundabout_enter_and_exit_ccw_sharp_right", "roundabout_enter_and_exit_ccw_slight_left",
        "roundabout_enter_and_exit_ccw_slight_right", "roundabout_enter_and_exit_ccw_straight",
        "roundabout_enter_and_exit_ccw_u_turn",
        "roundabout_enter_and_exit_cw", "roundabout_enter_and_exit_cw_normal_left",
        "roundabout_enter_and_exit_cw_normal_right", "roundabout_enter_and_exit_cw_sharp_left",
        "roundabout_enter_and_exit_cw_sharp_right", "roundabout_enter_and_exit_cw_slight_left",
        "roundabout_enter_and_exit_cw_slight_right", "roundabout_enter_and_exit_cw_straight",
        "roundabout_enter_and_exit_cw_u_turn",
        "roundabout_enter_ccw", "roundabout_enter_cw", "roundabout_exit_ccw", "roundabout_exit_cw",
        "straight", "turn_normal_left", "turn_normal_right", "turn_sharp_left", "turn_sharp_right",
        "turn_slight_left", "turn_slight_right", "u_turn_left", "u_turn_right",
    ).map { "maneuver_$it" }

    private class Loaded(val version: Long, val templates: List<ManeuverMask.Template>)

    @Volatile private var loaded: Loaded? = null

    /** Son resimler: Google aynı oku saniyede bir yeniden yayımlar, eşleştirme yeniden yapılmaz. */
    private val cache = object : LinkedHashMap<Int, Result>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Result>?) = size > 32
    }

    class Result(val hash: Int, val match: ManeuverMask.Match?)

    class Pixels(val px: IntArray, val w: Int, val h: Int, val hash: Int)

    /** Büyük ikonun pikselleri; ikon yoksa ya da okunamazsa null. */
    fun pixels(context: Context, n: Notification): Pixels? = try {
        n.getLargeIcon()?.loadDrawable(context)?.let { toPixels(it) }
    } catch (e: Exception) {
        null
    }

    /** Resmin manevrası (önbellekli). */
    @Synchronized
    fun classify(context: Context, p: Pixels): Result {
        cache[p.hash]?.let { return it }
        val match = try {
            val templates = templates(context)
            ManeuverMask.fromArgb(p.px, p.w, p.h)?.let { ManeuverMask.match(it, templates) }
        } catch (e: Exception) {
            null
        }
        val result = Result(p.hash, match)
        cache[p.hash] = result
        if (BuildConfig.DEBUG) debugDump(context, p, match)
        return result
    }

    private fun templates(context: Context): List<ManeuverMask.Template> {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val version = try { pm.getPackageInfo(PKG, 0).longVersionCode } catch (e: Exception) { -1L }
        loaded?.takeIf { it.version == version }?.let { return it.templates }
        val res = try { pm.getResourcesForApplication(PKG) } catch (e: Exception) { null }
        val list = mutableListOf<ManeuverMask.Template>()
        if (res != null) {
            for (name in NAMES) {
                val maneuver = ManeuverMask.maneuverFromGoogleName(name) ?: continue
                val id = res.getIdentifier(name, "drawable", PKG)
                if (id == 0) continue
                try {
                    @Suppress("DEPRECATION")
                    val d = res.getDrawable(id, null) ?: continue
                    val p = render(d, TEMPLATE_PX, TEMPLATE_PX)
                    ManeuverMask.fromArgb(p.px, p.w, p.h)?.let { list += ManeuverMask.Template(name, maneuver, it) }
                } catch (e: Exception) {
                }
            }
        }
        loaded = Loaded(version, list)
        cache.clear()
        return list
    }

    private fun toPixels(d: Drawable): Pixels? {
        if (d is BitmapDrawable) {
            val b = d.bitmap ?: return null
            if (b.width in 1..MAX_ICON_PX && b.height in 1..MAX_ICON_PX) return bitmapPixels(b)
        }
        val w = d.intrinsicWidth.takeIf { it in 1..MAX_ICON_PX } ?: TEMPLATE_PX
        val h = d.intrinsicHeight.takeIf { it in 1..MAX_ICON_PX } ?: TEMPLATE_PX
        return render(d, w, h)
    }

    private fun render(d: Drawable, w: Int, h: Int): Pixels {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(b))
        return bitmapPixels(b).also { b.recycle() }
    }

    private fun bitmapPixels(b: Bitmap): Pixels {
        val src = if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
        val px = IntArray(src.width * src.height)
        src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
        return Pixels(px, src.width, src.height, 31 * px.contentHashCode() + src.width)
    }

    /** Araç testi: her yeni ok resmini ve sonucunu kaydeder (en çok 40 resim). */
    private fun debugDump(context: Context, p: Pixels, match: ManeuverMask.Match?) {
        try {
            com.mapcontrol.util.DiagJournal.event(
                "hud", "google ok ikon=" + Integer.toHexString(p.hash) + " " + p.w + "x" + p.h + " -> " +
                    (match?.let { it.name + " " + it.maneuver + " skor=" + "%.2f".format(it.score) + " ikinci=" + "%.2f".format(it.runnerUp) } ?: "eslesmedi") +
                    " sablon=" + (loaded?.templates?.size ?: 0),
            )
            val dir = File(context.filesDir, "google-icons").apply { mkdirs() }
            if ((dir.list()?.size ?: 0) >= 40) return
            val f = File(dir, Integer.toHexString(p.hash) + ".png")
            if (f.exists()) return
            val b = Bitmap.createBitmap(p.px, p.w, p.h, Bitmap.Config.ARGB_8888)
            FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            b.recycle()
        } catch (e: Exception) {
        }
    }
}
