package com.mapcontrol.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView

/**
 * Geliştirme aracı (yalnızca debug, elle açılır): boştayken ekranı kimin yeniden çizdirdiğini bulmak için. Her karede
 * kirli ([View.isDirty]) en alt görünümleri toplar, [WINDOW_MS]'de bir "DrawTrace" etiketiyle özet yazar: kare ve yerleşim
 * turu sayısı, en sık kirlenen görünümler (sınıf zinciri ve TextView'de metnin başı). Araçta:
 *   adb shell am start -n <paket>/com.mapcontrol.launcher.HomeLauncherActivity --ez launcher_draw_trace true
 *   adb logcat -s DrawTrace
 * Kapalıyken hiçbir dinleyici eklenmez (maliyet yok).
 */
internal object DrawTrace {
    const val EXTRA = "launcher_draw_trace"
    private const val TAG = "DrawTrace"
    private const val WINDOW_MS = 5_000L
    private const val TOP = 8

    private var attached = false

    fun applyFrom(activity: Activity, intent: Intent?) {
        if (attached || intent?.getBooleanExtra(EXTRA, false) != true) return
        if (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        attached = true
        attach(activity.window.decorView)
    }

    private fun attach(root: View) {
        val counts = HashMap<String, Int>()
        var frames = 0
        var layouts = 0
        var since = SystemClock.uptimeMillis()
        root.viewTreeObserver.addOnGlobalLayoutListener { layouts++ }
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                frames++
                val seen = HashSet<String>()
                walk(root, seen)
                seen.forEach { counts[it] = (counts[it] ?: 0) + 1 }
                val now = SystemClock.uptimeMillis()
                if (now - since >= WINDOW_MS) {
                    val top = counts.entries.sortedByDescending { it.value }.take(TOP).joinToString(" | ") { "${it.value}× ${it.key}" }
                    Log.i(TAG, "${(now - since) / 1000}s: kare=$frames yerlesim=$layouts  $top")
                    counts.clear()
                    frames = 0
                    layouts = 0
                    since = now
                }
                return true
            }
        })
    }

    private fun name(v: View): String {
        val sb = StringBuilder()
        var c: View? = v
        var depth = 0
        while (c != null && depth < 3) {
            var s = c.javaClass.name.substringAfterLast('.')
            if (c is TextView) s += "('" + c.text.toString().take(16) + "')"
            sb.insert(0, if (depth == 0) s else "$s/")
            c = c.parent as? View
            depth++
        }
        return sb.toString()
    }

    /** Görünür alt ağaçta kirli olup hiçbir çocuğu kirli olmayan görünümleri ekler; alt ağaçta kirli var mı döner. */
    private fun walk(v: View, out: MutableSet<String>): Boolean {
        if (v.visibility != View.VISIBLE) return false
        var childDirty = false
        if (v is ViewGroup) for (i in 0 until v.childCount) childDirty = walk(v.getChildAt(i), out) || childDirty
        val d = v.isDirty
        if (d && !childDirty) out.add(name(v))
        return d || childDirty
    }
}
