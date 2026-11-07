package com.mapcontrol.launcher

/**
 * FrostNav uygulamasına geçiş kuralı. Launcher ayrı uygulama olarak da dağıtılabildiği için
 * kullanıcı her zaman FrostNav'ın kendi ekranlarına ulaşabilmelidir.
 */
object FrostNavLink {

    /** Araçtaki FrostNav paketi. */
    const val PRIMARY_PACKAGE = "com.frostnav"

    /** Emülatör ve geliştirme derlemelerindeki yedek paket adı. */
    const val FALLBACK_PACKAGE = "com.mapcontrol"

    /**
     * Açılacak FrostNav paketi: önce [PRIMARY_PACKAGE], sonra [FALLBACK_PACKAGE]; kurulu değilse ya da
     * launcher zaten FrostNav'ın içinde çalışıyorsa null. Kendi paketimiz asla seçilmez.
     */
    fun choosePackage(ownPackage: String, installed: Set<String>): String? {
        if (ownPackage == PRIMARY_PACKAGE) return null
        return listOf(PRIMARY_PACKAGE, FALLBACK_PACKAGE).firstOrNull { it != ownPackage && it in installed }
    }
}
