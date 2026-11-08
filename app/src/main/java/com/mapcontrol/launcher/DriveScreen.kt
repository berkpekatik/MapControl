package com.mapcontrol.launcher

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Sürüş ekranı giriş noktası. Bu parçada yalnız ana ekranı tam ekran öne getirme ([launchHome]) çalışır; sürüş ekranı
 * (ekranın harita ve Mini gösterge olarak ikiye bölünmesi) navigasyon paneliyle birlikte sonraki parçada gelir.
 *
 * O zamana kadar sürüş ekranı hiç açılmaz: [active] her zaman false, [openLeftover] false döner (çağıran uygulamayı
 * kendisi açar) ve yaşam döngüsü kancaları bir şey yapmaz.
 */
internal object DriveScreen {

    private const val TAG = "DriveScreen"

    private const val WINDOWING_MODE_FULLSCREEN = 1

    /** Sürüş ekranı açık ya da açılıyor. Bu parçada hiç açılmaz. */
    val active: Boolean get() = false

    /** Sürüş ekranından yarıda kalmış haritayı tam ekran açar; bu parçada böyle bir harita yoktur. */
    fun openLeftover(context: Context, pkg: String): Boolean = false

    fun recoverStale(context: Context) = Unit

    fun onLauncherResumed(context: Context) = Unit

    fun onLauncherTop(context: Context, top: Boolean) = Unit

    /**
     * Bizim ana ekranımız tam ekran (pencere modu 1) öne gelir; HOME rolü kapalıyken de açıkça adlandırılır. Ana ekran tuşu
     * yönlendirmesi ve açılış da bu yolu kullanır ([HomeKey]).
     */
    fun launchHome(context: Context) {
        try {
            val intent = Intent(context, HomeLauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            val opts = ActivityOptions.makeBasic().toBundle() ?: Bundle()
            // ActivityOptions.setLaunchWindowingMode (gizli API) ile aynı anahtarlar; 1 = WINDOWING_MODE_FULLSCREEN.
            opts.putInt("android.activity.windowingMode", WINDOWING_MODE_FULLSCREEN)
            opts.putInt("android:windowingMode", WINDOWING_MODE_FULLSCREEN)
            context.startActivity(intent, opts)
        } catch (e: RuntimeException) {
            Log.w(TAG, "launcher could not be opened: ${e.javaClass.simpleName}")
        }
    }
}
