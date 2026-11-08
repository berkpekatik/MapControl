package com.mapcontrol.launcher

/**
 * MapControl uygulamasına geçiş kuralı. Launcher ayrı uygulama olarak da dağıtılabildiği için
 * kullanıcı her zaman MapControl'ın kendi ekranlarına ulaşabilmelidir.
 */
object MapControlLink {

    /** Araçtaki MapControl paketi. */
    const val PRIMARY_PACKAGE = "com.mapcontrol"

    /** Emülatör ve geliştirme derlemelerindeki yedek paket adı. */
    const val FALLBACK_PACKAGE = "com.mapcontrol"

    /**
     * Açılacak MapControl paketi: önce [PRIMARY_PACKAGE], sonra [FALLBACK_PACKAGE]; kurulu değilse ya da
     * launcher zaten MapControl'ın içinde çalışıyorsa null. Kendi paketimiz asla seçilmez.
     */
    fun choosePackage(ownPackage: String, installed: Set<String>): String? {
        if (ownPackage == PRIMARY_PACKAGE) return null
        return listOf(PRIMARY_PACKAGE, FALLBACK_PACKAGE).firstOrNull { it != ownPackage && it in installed }
    }
}
