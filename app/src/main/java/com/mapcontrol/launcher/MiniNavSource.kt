package com.mapcontrol.launcher

import android.content.Context
import com.mapcontrol.R

/**
 * Mini göstergenin navigasyon satırının veri kaynağı. Gerçek kaynak [NavigationMiniSource] (Yandex rehberlik bildirimi,
 * [NavigationSource]); rota yokken satır gizli kalır. [MiniDashActivity.navSourceFactory] üzerinden verilir.
 *
 * Sözleşme: [start] görünür olunca (onStart), [stop] gizlenince (onStop) çağrılır; [stop] sonrası dinleyici çağrılmaz.
 * Dinleyici ana iş parçacığında çağrılır; null = gösterilecek ipucu yok (satır gizlenir).
 */
internal interface MiniNavSource {

    fun interface Listener {
        fun onNav(hint: NavHint?)
    }

    fun start(listener: Listener)

    fun stop()

    companion object {
        /** Kaynak yok: satır hep gizli. */
        val NONE: MiniNavSource = object : MiniNavSource {
            override fun start(listener: Listener) = listener.onNav(null)
            override fun stop() = Unit
        }

        /** Demo (ekran görüntüsü): sabit bir dönüş ve varış. Yalnızca [LauncherDemo] açıkken ve istenirse kullanılır. */
        val DEMO: MiniNavSource = object : MiniNavSource {
            override fun start(listener: Listener) = listener.onNav(NavHint("Sağa dönün · Atatürk Cd.", 350, 14 * 60 + 32))
            override fun stop() = Unit
        }
    }
}

/**
 * Gerçek kaynak: [NavigationSource] (olay güdümlü, yoklama yok) → [NavHint] ([MiniNav.hint]). Dinleyici [start]'ta eklenir,
 * [stop]'ta çıkarılır; ilk çağrı son durumla hemen gelir.
 */
internal class NavigationMiniSource(context: Context) : MiniNavSource {
    private val app = context.applicationContext
    private var bound: ((NavigationState) -> Unit)? = null

    override fun start(listener: MiniNavSource.Listener) {
        stop()
        val l: (NavigationState) -> Unit = { s -> listener.onNav(MiniNav.hint(s, ::label) { app.getString(R.string.launcher_nav_exit, it) }) }
        bound = l
        NavigationSource.addListener(l)
    }

    override fun stop() {
        bound?.let { NavigationSource.removeListener(it) }
        bound = null
    }

    private fun label(m: NavManeuver): String? = when (m) {
        NavManeuver.STRAIGHT -> R.string.launcher_mini_turn_straight
        NavManeuver.SLIGHT_LEFT -> R.string.launcher_mini_turn_slight_left
        NavManeuver.SLIGHT_RIGHT -> R.string.launcher_mini_turn_slight_right
        NavManeuver.LEFT -> R.string.launcher_mini_turn_left
        NavManeuver.RIGHT -> R.string.launcher_mini_turn_right
        NavManeuver.HARD_LEFT -> R.string.launcher_mini_turn_hard_left
        NavManeuver.HARD_RIGHT -> R.string.launcher_mini_turn_hard_right
        NavManeuver.UTURN_LEFT, NavManeuver.UTURN_RIGHT -> R.string.launcher_mini_turn_uturn
        NavManeuver.ROUNDABOUT_ENTER, NavManeuver.ROUNDABOUT_EXIT -> R.string.launcher_mini_turn_roundabout
        NavManeuver.EXIT_LEFT -> R.string.launcher_mini_turn_exit_left
        NavManeuver.EXIT_RIGHT -> R.string.launcher_mini_turn_exit_right
        NavManeuver.FERRY -> R.string.launcher_mini_turn_ferry
        NavManeuver.FINISH -> R.string.launcher_mini_turn_finish
        NavManeuver.WAYPOINT -> R.string.launcher_mini_turn_waypoint
        NavManeuver.UNKNOWN -> null
    }?.let { app.getString(it) }
}
