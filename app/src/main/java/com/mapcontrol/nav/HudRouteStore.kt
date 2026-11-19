package com.mapcontrol.nav

import com.mapcontrol.launcher.NavigationSource
import com.mapcontrol.launcher.NavigationState
import com.mapcontrol.launcher.NavManeuver

/** Ön plan Yandex okuması, Google'ın etkin rehberliğini devralmaz. */
internal fun hudRoute(notification: NavigationState, foreground: NavigationState, provider: String): NavigationState {
    val yandexNotification = notification.sourcePackage == NavigationSource.YANDEX_MAPS ||
        notification.sourcePackage == NavigationSource.YANDEX_NAVI
    return if (provider != "google" && foreground.active &&
        (!notification.active || (yandexNotification && notification.maneuver == NavManeuver.UNKNOWN))) foreground
    else notification
}

/** HUD'un etkin bildirimleri. Yalnız ayrıştırma iş parçacığından erişilir. */
internal class HudRouteStore {
    private val routes = LinkedHashMap<String, NavigationState>()

    fun update(key: String, state: NavigationState?) {
        if (state?.active == true) routes[key] = state else routes.remove(key)
    }

    fun remove(key: String) {
        routes.remove(key)
    }

    fun clear() = routes.clear()

    /** Otomatikte en son güncellenen rehberlik; seçim değişince diğer uygulamanın rotası gösterilmez. */
    fun current(provider: String): NavigationState = routes.values
        .filter {
            when (provider) {
                "google" -> it.sourcePackage == NavigationSource.GOOGLE_MAPS
                "yandex" -> it.sourcePackage == NavigationSource.YANDEX_MAPS || it.sourcePackage == NavigationSource.YANDEX_NAVI
                else -> true
            }
        }
        .maxByOrNull { it.updatedAtMs } ?: NavigationState.NONE
}
