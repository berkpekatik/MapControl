package com.mapcontrol.launcher

import android.os.SystemClock
import android.util.Log
import com.mapcontrol.BuildConfig

/**
 * Launcher günlük politikası (6 Kasım bugreport: logd %5,8 CPU, tampon ~10 dk).
 * - [i]/[d]: yalnız debug derlemede yazılır; release sessizdir (W/E doğrudan [Log] ile kalır).
 * - [iEvery]: sık tekrarlanan (kare/veri tiki) satırlar; debug'da da anahtar başına en fazla [intervalMs]'de bir,
 *   arada bastırılan satır sayısıyla.
 */
object LLog {
    @JvmStatic
    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.i(tag, msg)
    }

    @JvmStatic
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    @PublishedApi internal class Slot(var at: Long, var dropped: Int)

    @PublishedApi internal val slots = HashMap<String, Slot>()

    /** Anahtar için şimdi yazılabilirse bastırılan satır sayısını (>=0), yazılamazsa -1 döner. */
    @PublishedApi
    internal fun admit(key: String, intervalMs: Long): Int = synchronized(slots) {
        val now = SystemClock.elapsedRealtime()
        val s = slots[key]
        when {
            s == null -> { slots[key] = Slot(now, 0); 0 }
            now - s.at >= intervalMs -> { val d = s.dropped; s.at = now; s.dropped = 0; d }
            else -> { s.dropped++; -1 }
        }
    }

    /** Debug-only, anahtar başına hız sınırlı INFO satırı; [msg] yalnız yazılacaksa kurulur. */
    inline fun iEvery(tag: String, key: String, intervalMs: Long, msg: () -> String) {
        if (!BuildConfig.DEBUG) return
        val dropped = admit("$tag/$key", intervalMs)
        if (dropped < 0) return
        Log.i(tag, if (dropped > 0) "${msg()} (+$dropped bastırıldı)" else msg())
    }
}
