package com.mapcontrol.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.view.WindowInsets
import com.mapcontrol.vehicle.VehicleMetricsSnapshot

/**
 * Chery Tiggo 7 Desay ünitesinde OEM alt çubuğu (ana ekran/uygulamalar/araç/sıcaklık/fan/ayar/telefon)
 * yaklaşık 105 px yüksekliğindedir. Araç fotoğrafı (2026-10-30): launcher penceresi bu çubuğun ÜSTÜNDE
 * bitiyor; Android pencereyi kendisi kısaltıyor/inset bildiriyor, bizim alt çubuğumuz OEM çubuğunun hemen
 * üstüne oturuyor. Bu yüzden gerçek cihazda ek bir yedek boşluk UYGULANMAZ (yalnızca sistemin bildirdiği inset).
 *
 * Emülatörde aynı düzeni denemek için yalnızca debug yapıda `--ei launcher_oem_inset <px>` verilir: bu hem
 * alt bandı ayırır ve alt çubuğumuzu kaldırır.
 */
object LauncherSafeArea {

    private const val DESAY_PACKAGE = "com.desaysv.vehiclesetting"
    private const val EXTRA_OEM_INSET = "launcher_oem_inset"

    /** Yalnızca debug yapıda, emülatörde OEM çubuğunu taklit etmek için (`--ei launcher_oem_inset 120`). */
    private var debugOverridePx: Int? = null

    fun applyFrom(activity: Activity, intent: Intent?) {
        if (intent?.hasExtra(EXTRA_OEM_INSET) != true) return
        val debuggable = activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        debugOverridePx = if (debuggable) intent.getIntExtra(EXTRA_OEM_INSET, 0).coerceAtLeast(0) else null
    }

    /** Debug yapıda `launcher_oem_inset` ekstrasıyla taklit edilen çubuk mu (gerçek Desay tespitinde false). */
    fun isMocked(): Boolean = (debugOverridePx ?: 0) > 0

    /** Desay ünitesi mi: ucuz ve sağlam bir paket varlığı kontrolü (yalnızca bilgi; yerleşimi değiştirmez). */
    fun isDesayUnit(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(DESAY_PACKAGE, 0)
        true
    } catch (_: Exception) {
        false
    }

    /**
     * OEM alt çubuğu olan cihaz: gerçek Desay ünitesi ya da debug simülasyonu. Bizim alt çubuğumuz kaldırılır
     * (uygulamalar birleşik 1×1 kutucuk, koltuk ısıtma kutucuk) ve Desay varsayılan düzeni kullanılır.
     */
    fun hasOemBar(context: Context): Boolean = (debugOverridePx ?: 0) > 0 || isDesayUnit(context)

    /** Debug simülasyonunun ayırdığı OEM alt bandı (px); gerçek cihazda her zaman 0. */
    @Suppress("UNUSED_PARAMETER")
    fun oemInsetPx(context: Context): Int = if (LauncherBottomBar.hidesOemBar(context)) 0 else debugOverridePx ?: 0

    /**
     * Sistem OEM çubuğunu zorla gösteriyor mu (serbest pencere/bölünmüş ekran açıkken; bkz. [BottomBarMode.oemBarForced]).
     * Android 10'da inset görünürlüğü okunamaz: false.
     */
    fun oemBarForced(context: Context, insets: WindowInsets?): Boolean {
        if (insets == null || Build.VERSION.SDK_INT < 30) return false
        val nav = WindowInsets.Type.navigationBars()
        return BottomBarMode.oemBarForced(LauncherBottomBar.mode(context), insets.isVisible(nav), insets.getInsets(nav).bottom)
    }

    /**
     * Alt güvenli alan = max(sistem gezinme çubuğu inset'i, debug OEM simülasyonu). [oemBarForced] true ise (sistem OEM çubuğunu
     * zorla açtı) Bizimki/Gizli'de de çubuğun inset'i uygulanır; içerik onun altında kalmasın.
     */
    fun bottomInsetPx(context: Context, insets: WindowInsets?, oemBarForced: Boolean = false): Int {
        val system = when {
            // Bizimki/Gizli: gezinme çubuğu gizli; geçici gösterim içeriği itmesin, ana ekran 720 px'in tamamını alır.
            LauncherBottomBar.hidesOemBar(context) && !oemBarForced -> 0
            insets == null -> 0
            Build.VERSION.SDK_INT >= 30 -> insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            else -> @Suppress("DEPRECATION") insets.systemWindowInsetBottom
        }
        return maxOf(system, oemInsetPx(context))
    }

    /**
     * Sürüş kilidi: hız > 0 ise hareket var. Araç servisine bağlıyken hız henüz okunmadıysa da
     * kilitli sayılır; bağlı değilse (emülatör, servis yok) park kabul edilir. Demo her zaman park.
     */
    fun isMoving(snapshot: VehicleMetricsSnapshot, threshold: Int = 0): Boolean {
        if (LauncherDemo.enabled) return false
        if (snapshot.preferredSpeed() > threshold) return true
        val unread = !VehicleMetricsSnapshot.isValid(snapshot.displaySpeed) && !VehicleMetricsSnapshot.isValid(snapshot.actualSpeed)
        return snapshot.connected && unread && threshold == 0
    }
}
