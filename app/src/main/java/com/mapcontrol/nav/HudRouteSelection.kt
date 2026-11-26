package com.mapcontrol.nav

import com.mapcontrol.launcher.NavigationSource
import com.mapcontrol.launcher.NavigationState
import com.mapcontrol.launcher.NavManeuver

/** Aynı Yandex uygulamasının oku ve ekran ölçümleri tek yönlendirmede tamamlanır. */
internal fun hudRoute(notification: NavigationState, foreground: NavigationState, provider: String): NavigationState {
    if (provider == "google" || !foreground.active) return notification
    if (!notification.active) return foreground
    val sameYandex = notification.sourcePackage in setOf(NavigationSource.YANDEX_MAPS, NavigationSource.YANDEX_NAVI) &&
        notification.sourcePackage == foreground.sourcePackage
    if (!sameYandex) return notification
    return notification.copy(
        maneuver = if (notification.maneuver == NavManeuver.UNKNOWN) foreground.maneuver else notification.maneuver,
        distance = notification.distance ?: foreground.distance,
        street = notification.street?.takeIf { it.isNotBlank() } ?: foreground.street,
        remainingDistance = notification.remainingDistance ?: foreground.remainingDistance,
        remainingMinutes = notification.remainingMinutes ?: foreground.remainingMinutes,
        arrivalTime = notification.arrivalTime?.takeIf { it.isNotBlank() } ?: foreground.arrivalTime,
    )
}
