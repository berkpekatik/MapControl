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
