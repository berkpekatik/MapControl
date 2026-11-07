package com.mapcontrol.launcher

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import java.util.ArrayList

/**
 * Ana ekran açılırken ilk kare çizilene kadar ertelenebilir işler (hava durumu, yoklamalar).
 * İlk kare çizildikten sonraki ilk Choreographer karesinde çalışır; ondan sonra gelenler hemen (post ile) çalışır.
 */
object LauncherStartup {
    private val main = Handler(Looper.getMainLooper())
    private val pending = ArrayList<Deferred>()
    private var firstFrameDone = false

    class Deferred internal constructor(private val block: () -> Unit) : Runnable {
        @Volatile private var cancelled = false
        fun cancel() { cancelled = true }
        override fun run() { if (!cancelled) block() }
    }

    /** Etkinlik her kurulduğunda çağrılır; ilk çizimden sonra bekleyenleri çalıştırır. */
    fun begin(decor: View) {
        pending.clear()
        firstFrameDone = false
        decor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnPreDrawListener(this)
                Choreographer.getInstance().postFrameCallback {
                    firstFrameDone = true
                    val run = ArrayList(pending)
                    pending.clear()
                    run.forEach { it.run() }
                }
                return true
            }
        })
    }

    /**
     * Ana ekran dışındaki bir etkinlik (ör. [MiniDashActivity]) için: süreçte henüz hiç kare çizilmediyse bekleyenleri bu
     * pencerenin ilk karesinden sonra çalıştırır. [begin]'den farkı: bekleyenleri silmez ve durumu sıfırlamaz (ana ekranın
     * kendi açılışını bozmaz).
     */
    fun attach(decor: View) {
        if (firstFrameDone) return
        decor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnPreDrawListener(this)
                Choreographer.getInstance().postFrameCallback {
                    if (firstFrameDone) return@postFrameCallback
                    firstFrameDone = true
                    val run = ArrayList(pending)
                    pending.clear()
                    run.forEach { it.run() }
                }
                return true
            }
        })
    }

    /** İlk kareden sonra çalıştırır; dönen nesne ile (örn. onHidden'da) iptal edilebilir. */
    fun later(block: () -> Unit): Deferred {
        val d = Deferred(block)
        if (firstFrameDone) main.post(d) else pending.add(d)
        return d
    }
}

/**
 * Tema değişiminde `recreate()` ağır bir kare üretir (yeni görünüm ağacı). Eski ekranın görüntüsünü tam ekran
 * kaplama olarak tutup yeni etkinlikte yumuşakça söndürerek takılma yerine kısa bir geçiş gösterilir.
 */
object ThemeTransition {
    private const val FADE_MS = 200L
    private var snapshot: Bitmap? = null
    private var pending = false
    internal var tapAt = 0L

    /** Pencerenin görüntüsünü alıp [Activity.recreate] eder; yakalama başarısızsa doğrudan recreate. */
    fun recreate(activity: Activity) {
        if (pending || activity.isFinishing || activity.isDestroyed) return
        tapAt = SystemClock.uptimeMillis()
        val decor = activity.window.decorView
        val w = decor.width
        val h = decor.height
        if (w <= 0 || h <= 0 || !decor.isAttachedToWindow) {
            activity.recreate()
            return
        }
        val bmp = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            activity.recreate()
            return
        }
        pending = true
        try {
            PixelCopy.request(activity.window, bmp, { result ->
                pending = false
                if (activity.isFinishing || activity.isDestroyed) {
                    bmp.recycle()
                    return@request
                }
                if (result == PixelCopy.SUCCESS) {
                    snapshot?.recycle()
                    snapshot = bmp
                } else {
                    bmp.recycle()
                }
                activity.recreate()
            }, Handler(Looper.getMainLooper()))
        } catch (e: IllegalArgumentException) {
            pending = false
            bmp.recycle()
            activity.recreate()
        }
    }

    // --- Yerinde tema geçişi (etkinlik yeniden yaratılmaz) ---

    private var live: ImageView? = null
    private var inPlaceBusy = false
    private var inPlaceAgain = false

    /**
     * Tema değişimini etkinliği yeniden yaratmadan uygular: pencerenin (yarı çözünürlükte, ucuz) görüntüsü örtü olur,
     * altında [HomeLauncherActivity.applyThemeInPlace] widget'ları yeniler, ilk kare çizilince örtü söner.
     * Hızlı ardışık çağrılar birleşir (sonuncunun tema seçimi uygulanır). Etkinlik bu türden değilse recreate.
     */
    fun retheme(activity: Activity) {
        val home = activity as? HomeLauncherActivity
        if (home == null) { recreate(activity); return }
        if (activity.isFinishing || activity.isDestroyed) return
        if (inPlaceBusy) { inPlaceAgain = true; return }
        val t0 = SystemClock.uptimeMillis()
        val decor = activity.window.decorView
        val w = decor.width
        val h = decor.height
        val finish = {
            home.endThemeDefer()
            inPlaceBusy = false
            if (inPlaceAgain) { inPlaceAgain = false; decor.post { retheme(activity) } }
        }
        if (w <= 0 || h <= 0 || !decor.isAttachedToWindow) {
            home.applyThemeInPlace(); return
        }
        val bmp = try {
            Bitmap.createBitmap(w / SNAP_DIV, h / SNAP_DIV, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            home.applyThemeInPlace(); return
        }
        inPlaceBusy = true
        fun go(ok: Boolean) {
            if (activity.isFinishing || activity.isDestroyed) {
                bmp.recycle(); inPlaceBusy = false
            } else {
                val overlay = if (ok) addOverlay(decor as ViewGroup, activity, bmp) else { bmp.recycle(); null }
                val t1 = SystemClock.uptimeMillis()
                // Yeni widget'ların onVisible'ı örtü sönene kadar (finish) bekler; görsel geçiş aynı.
                home.beginThemeDefer()
                try { home.applyThemeInPlace() } catch (e: Throwable) {
                    Log.e("ThemeTiming", "yerinde tema hatası, yeniden kuruluyor", e)
                    overlay?.let { removeOverlay(decor, it) }
                    home.endThemeDefer()
                    inPlaceBusy = false
                    activity.recreate(); return
                }
                Log.i("ThemeTiming", "yerinde: anlık görüntü ${t1 - t0} ms, yenileme ${SystemClock.uptimeMillis() - t1} ms")
                decor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnPreDrawListener(this)
                        Log.i("ThemeTiming", "yerinde: ilk kare (onPreDraw) ${SystemClock.uptimeMillis() - t0} ms")
                        Choreographer.getInstance().postFrameCallback {
                            Log.i("ThemeTiming", "yerinde: ilk kare çizildi ${SystemClock.uptimeMillis() - t0} ms")
                            if (overlay == null) finish() else overlay.animate().alpha(0f).setDuration(FADE_MS)
                                .withEndAction { removeOverlay(decor, overlay); finish() }.start()
                        }
                        return true
                    }
                })
            }
        }
        try {
            PixelCopy.request(activity.window, bmp, { go(it == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper()))
        } catch (e: IllegalArgumentException) {
            bmp.recycle(); inPlaceBusy = false; home.applyThemeInPlace()
        }
    }

    private const val SNAP_DIV = 2

    private fun addOverlay(decor: ViewGroup, activity: Activity, bmp: Bitmap): ImageView {
        live?.let { removeOverlay(decor, it) } // yeni görüntü eskisini zaten içeriyor
        val overlay = object : ImageView(activity) {
            override fun hasOverlappingRendering() = false
        }.apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.FIT_XY
            isClickable = true
        }
        decor.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        live = overlay
        return overlay
    }

    private fun removeOverlay(decor: View, overlay: ImageView) {
        overlay.animate().cancel()
        (decor as ViewGroup).removeView(overlay)
        (overlay.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { overlay.setImageDrawable(null); it.recycle() }
        if (live === overlay) live = null
    }

    /** Yeni etkinlikte: varsa eski görüntüyü kaplama yapar, ilk kare çizilince söndürür. */
    fun playIn(activity: Activity) {
        val t0 = tapAt
        if (t0 != 0L) activity.window.decorView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                activity.window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                Log.i("ThemeTiming", "ilk kare (onPreDraw) ${SystemClock.uptimeMillis() - t0} ms")
                Choreographer.getInstance().postFrameCallback { Log.i("ThemeTiming", "ilk kare cizildi ${SystemClock.uptimeMillis() - t0} ms") }
                return true
            }
        })
        val bmp = snapshot ?: return
        snapshot = null
        val decor = activity.window.decorView as ViewGroup
        // Tek bitmap çizildiği için alfa doğrudan boyaya uygulanır; ara katman (saveLayer) gerekmez.
        val overlay = object : ImageView(activity) {
            override fun hasOverlappingRendering() = false
        }.apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.FIT_XY
            isClickable = true
        }
        decor.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        decor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnPreDrawListener(this)
                Choreographer.getInstance().postFrameCallback {
                    overlay.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
                        decor.removeView(overlay)
                        overlay.setImageDrawable(null)
                        bmp.recycle()
                    }.start()
                }
                return true
            }
        })
    }
}
