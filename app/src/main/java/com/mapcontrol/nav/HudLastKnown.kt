package com.mapcontrol.nav

import com.mapcontrol.launcher.NavDistance
import com.mapcontrol.launcher.NavManeuver
import com.mapcontrol.launcher.NavigationState

/**
 * Aktif rotada bildirim bir an eksik alan (ok, mesafe, yol adı) verirse HUD'a eski değeri tutar.
 * Ok süresiz tutulur: ok bilinmeyen kare
 * HUD'a "ok yok" kodu taşıyamaz (bkz. [NativeHudProtocol]), adım değişince ok ya yeni değerle gelir ya da eskisi kalır.
 * Mesafe, yol ve varış bilgisi sınırlı süre tutulur. Kaynak (Google/Yandex) paketine göre ayrı tutulur,
 * paket değişirse hafıza sıfırlanır. Birden çok iş parçacığından çağrılabilir.
 */
internal class HudLastKnown(
    private val now: () -> Long,
    private val holdMs: Long = 10_000L,
) {
    private class Held<T>(val value: T, val at: Long)

    private var source: String? = null
    private var maneuver: Held<Pair<NavManeuver, Int?>>? = null
    private var distance: Held<NavDistance>? = null
    private var street: Held<String>? = null
    private var remaining: Held<NavDistance>? = null
    private var minutes: Held<Int>? = null
    private var arrival: Held<String>? = null

    @Synchronized
    fun reset() {
        source = null
        maneuver = null; distance = null; street = null; remaining = null; minutes = null; arrival = null
    }

    /** Aktif olmayan durum olduğu gibi döner; hafıza zamanla kendiliğinden sona erer. */
    @Synchronized
    fun merge(state: NavigationState): NavigationState {
        if (!state.active) return state
        val at = now()
        if (state.sourcePackage != source) {
            reset()
            source = state.sourcePackage
        }
        if (state.maneuver != NavManeuver.UNKNOWN) maneuver = Held(state.maneuver to state.roundaboutExit, at)
        state.distance?.let { distance = Held(it, at) }
        state.street?.takeIf { it.isNotBlank() }?.let { street = Held(it, at) }
        state.remainingDistance?.let { remaining = Held(it, at) }
        state.remainingMinutes?.let { minutes = Held(it, at) }
        state.arrivalTime?.takeIf { it.isNotBlank() }?.let { arrival = Held(it, at) }

        fun <T> fresh(held: Held<T>?): T? = held?.takeIf { at - it.at <= holdMs }?.value
        val keptManeuver = if (state.maneuver == NavManeuver.UNKNOWN) maneuver?.value else null
        return state.copy(
            maneuver = keptManeuver?.first ?: state.maneuver,
            roundaboutExit = if (keptManeuver != null) keptManeuver.second else state.roundaboutExit,
            distance = state.distance ?: fresh(distance),
            street = state.street?.takeIf { it.isNotBlank() } ?: fresh(street) ?: state.street,
            remainingDistance = state.remainingDistance ?: fresh(remaining),
            remainingMinutes = state.remainingMinutes ?: fresh(minutes),
            arrivalTime = state.arrivalTime?.takeIf { it.isNotBlank() } ?: fresh(arrival) ?: state.arrivalTime,
        )
    }
}
