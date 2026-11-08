package com.mapcontrol.launcher

import android.content.Context
import android.content.pm.PackageManager

/** Sahne'nin varsayılan uygulama yuvalarını dolduran kurulu uygulamalar. */
object DefaultApps {
    /** Kurulu varsayılan uygulamalar (ilk [count] tanesi), [DEFAULT_CANDIDATES] sırasıyla. */
    fun installedDefaults(context: Context, count: Int): List<String> = DEFAULT_CANDIDATES.filter { pkg ->
        try {
            context.packageManager.getApplicationInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }.take(count)

    /**
     * Aday sırası. Sona aracın kendi uygulamaları eklendi: telefon uygulamaları yoksa yuvalar yine dolu görünür.
     */
    val DEFAULT_CANDIDATES = listOf(
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "com.google.android.apps.maps",
        "ru.yandex.yandexnavi",
        "com.waze",
        "com.desaysv.svaudioapp",
        "com.desaysv.bluetooth.phone",
        "com.desaysv.vehiclesetting",
        "com.desaysv.videoapp",
        "com.frostnav",
    )
}
