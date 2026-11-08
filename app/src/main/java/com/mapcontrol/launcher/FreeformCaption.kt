package com.mapcontrol.launcher

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import kotlin.math.abs

/**
 * Serbest pencere başlığı (Android 11 `DecorCaptionView`: küçült/kapat simgeli ~42 px şerit) için kendi etkinliklerimizde yedek.
 *
 * Bölünmüş ekran açılırken ([com.mapcontrol.util.SplitScreenLauncher]) üst yarılar başlık kadar yukarıdan başlatılır, başlık durum
 * çubuğunun altında kalır. Sistem bu sınırları kırpıp pencereyi yine durum çubuğunun hemen altına koyarsa başlık görünür kalır;
 * o durumda başlık bu etkinlikte gizlenir, içerik pencerenin tamamını alır. Pencere kenarından boyut değiştirme (sistem işi)
 * etkilenmez; yalnızca başlıktan sürükleyip taşıma ve iki simge gider (bölmeyi FrostNav/Mini gösterge düğmeleri yönetir).
 */
internal object FreeformCaption {

    private const val CAPTION_VIEW = "DecorCaptionView"
    /** Pencere üstünün durum çubuğu altına "denk" sayıldığı pay (px). */
    private const val TOLERANCE_PX = 2

    /**
     * Saf kural (birim testlenir): pencerenin üstü kullanılabilir alanın üstüne (durum çubuğunun altı) denk geliyorsa başlık ekranda
     * görünüyordur ve gizlenir. Daha yukarıdaysa başlık zaten durum çubuğunun altındadır (gizlenirse içerik oraya kayardı); daha
     * aşağıdaysa pencere elle konmuştur, taşıma tutamağı kalsın.
     */
    fun shouldHide(windowTop: Int, usableTop: Int): Boolean = usableTop >= 0 && abs(windowTop - usableTop) <= TOLERANCE_PX

    /**
     * Yerleşimden sonra ve yapılandırma değişince çağrılır; serbest pencere değilse (başlık görünümü yoksa) bir şey yapmaz. [force]:
     * pencere nerede olursa olsun gizlenir (sürüş ekranının yarıları; taşıma tutamağına gerek yok, bölmeyi düğmeler yönetir).
     */
    fun apply(activity: Activity, force: Boolean = false) {
        val decor = activity.window?.decorView as? ViewGroup ?: return
        val caption = captionOf(activity) ?: return
        if (caption.visibility == View.GONE) return
        val loc = IntArray(2)
        decor.getLocationOnScreen(loc)
        if (!force && !shouldHide(loc[1], usableTop(activity))) return
        caption.visibility = View.GONE
        // Öykünücü, 7 Kasım: başlık gizlense de içerik kökü başlık inset'i kadar (42 px) aşağıda kaldı (fitsSystemWindows dolgusu).
        // Gizlenen pencere durum çubuğunun altında, çubukların dışında: inset'ler içeriğe dolgu olarak uygulanmaz.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.window?.setDecorFitsSystemWindows(false)
    }

    /**
     * Saf kural (birim testlenir): kullanılabilir alanın üstü. Serbest pencerede en büyük pencere ölçüsünün üst inset'i 0 gelir
     * (öykünücü, 7 Kasım: pencere 24'te, inset 0; başlık bu yüzden hiç gizlenmiyordu); o zaman durum çubuğu yüksekliği kullanılır.
     */
    fun topEdge(metricsTop: Int, statusBarPx: Int): Int = if (metricsTop > 0) metricsTop else statusBarPx

    /**
     * Saf kural (birim testlenir): görünen başlığın içeriğin üstünü örttüğü pay (px). Başlık görünmüyorsa ya da içerik zaten
     * başlığın altından başlıyorsa 0.
     */
    fun overlap(captionShown: Boolean, captionBottom: Int, contentTop: Int): Int =
        if (!captionShown) 0 else maxOf(0, captionBottom - contentTop)

    /**
     * Başlık gizlenemediyse (ör. durum çubuğu başlıktan kısa, pencere yukarı itilemedi) [content]'in üstünde kalan pay (px):
     * içerik bu kadar aşağıdan başlamalı ki saat ve düğmeler başlığın altında kalmasın.
     */
    fun overlapPx(activity: Activity, content: View): Int {
        val caption = captionOf(activity) ?: return 0
        val c = IntArray(2)
        val r = IntArray(2)
        caption.getLocationOnScreen(c)
        content.getLocationOnScreen(r)
        return overlap(caption.isShown && caption.height > 0, c[1] + caption.height, r[1])
    }

    /**
     * DecorCaptionView'un başlık şeridi (`android:id/caption`); serbest pencere değilse null. Çocuk sırasına güvenilmez: Android
     * 11 öykünücüde ilk çocuk içerik kökü, başlık ikinci çocuk (6 Kasım; sıraya göre gizlemek içeriğin tamamını gizliyordu).
     * Kimlik bulunamazsa içerik (`android:id/content`) taşımayan çocuk alınır.
     */
    private fun captionOf(activity: Activity): View? {
        val decor = activity.window?.decorView as? ViewGroup ?: return null
        val holder = (0 until decor.childCount).map(decor::getChildAt)
            .firstOrNull { it.javaClass.name.endsWith(CAPTION_VIEW) } as? ViewGroup ?: return null
        val children = (0 until holder.childCount).map(holder::getChildAt)
        val id = activity.resources.getIdentifier("caption", "id", "android")
        return children.firstOrNull { id != 0 && it.id == id }
            ?: children.firstOrNull { it.findViewById<View>(android.R.id.content) == null }
    }

    /** Durum çubuğunun altı (px); bölünmüş ekranın kullandığı ölçüyle aynı (en büyük pencere ölçüsünün üst inset'i). */
    private fun usableTop(activity: Activity): Int {
        var metricsTop = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                metricsTop = activity.windowManager.maximumWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                ).top
            } catch (_: Throwable) {
            }
        }
        val id = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
        return topEdge(metricsTop, if (id != 0) activity.resources.getDimensionPixelSize(id) else -1)
    }
}
