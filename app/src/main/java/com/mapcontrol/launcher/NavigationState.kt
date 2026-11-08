package com.mapcontrol.launcher

/**
 * Sonraki manevranın türü (Yandex MapKit `Action` listesinden sadeleştirilmiş). Çatal (fork) hafif dönüşle aynı oku
 * kullanır; feribot ayrı. [UNKNOWN]: rota var ama ok bilinmiyor (yalnız mesafe ve sokak gösterilir).
 */
enum class NavManeuver {
    STRAIGHT,
    SLIGHT_LEFT, SLIGHT_RIGHT,
    LEFT, RIGHT,
    HARD_LEFT, HARD_RIGHT,
    UTURN_LEFT, UTURN_RIGHT,
    ROUNDABOUT_ENTER, ROUNDABOUT_EXIT,
    EXIT_LEFT, EXIT_RIGHT,
    FERRY,
    FINISH, WAYPOINT,
    UNKNOWN,
}

/**
 * Navigasyon durumu: bildirimden ([NavigationSource]) okunan sonraki manevra ve varış bilgisi. Değişmez (immutable);
 * her bildirim güncellemesinde yeni bir örnek yayınlanır. Sahne Navigasyon paneli ve ileride mini gösterge (MiniDash)
 * bunu okur: `NavigationSource.addListener { state -> ... }` ana iş parçacığında çağrılır, `NavigationSource.state` son
 * değeri verir.
 *
 * Alanlar Yandex'in gösterdiği metinlerden gelir; Yandex bir alanı vermezse null kalır (ör. kalan süre).
 */
data class NavigationState(
    /** Rota sürüyor ve rehberlik bildirimi var. false iken diğer alanlar boştur ([NONE]). */
    val active: Boolean,
    val maneuver: NavManeuver = NavManeuver.UNKNOWN,
    /** Yuvarlak kavşak çıkış numarası (1, 2, 3 …); yoksa null. */
    val roundaboutExit: Int? = null,
    /** Manevraya kalan mesafe ("300 m", "1,2 km"). */
    val distance: NavDistance? = null,
    /** Manevradan sonraki yol adı ("Atatürk Cd."). */
    val street: String? = null,
    /** Tahmini varış saati "HH:mm". */
    val arrivalTime: String? = null,
    /** Hedefe kalan mesafe. */
    val remainingDistance: NavDistance? = null,
    /** Hedefe kalan süre (dakika). */
    val remainingMinutes: Int? = null,
    /** Bildirimi gönderen paket (ru.yandex.yandexmaps / ru.yandex.yandexnavi); demo için null. */
    val sourcePackage: String? = null,
    /** Son güncelleme (System.currentTimeMillis). */
    val updatedAtMs: Long = 0L,
    /** Bildirimin "navigasyonu bitir" eylemi ([NavEndAction] ile bulunur); yoksa null ve "Bitir" düğmesi gizlenir. */
    val endAction: android.app.PendingIntent? = null,
    /** Yalnız demo rota: "Bitir" hapı görünür, dokunuşu demo rotayı kapatır (bildirim eylemi yok). */
    val demoEnd: Boolean = false,
) {
    /** "Bitir" hapı gösterilir mi: bildirimin bitirme eylemi var ya da demo rota. */
    val hasEnd: Boolean get() = endAction != null || demoEnd

    companion object {
        /** Rota yok. */
        @JvmField val NONE = NavigationState(active = false)

        /** Demo rota: 300 m sonra sağa, Atatürk Cd., varış 14:52 · 18 km. */
        @JvmField val DEMO = NavigationState(
            active = true,
            maneuver = NavManeuver.RIGHT,
            distance = NavDistance("300", "m", 300),
            street = "Atatürk Cd.",
            arrivalTime = "14:52",
            remainingDistance = NavDistance("18", "km", 18_000),
            demoEnd = true,
        )
    }
}
