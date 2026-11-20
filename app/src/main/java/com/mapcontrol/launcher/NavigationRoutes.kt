package com.mapcontrol.launcher

/** Ortak bildirim hattının paket bazındaki rotaları; yalnız ayrıştırma iş parçacığında kullanılır. */
internal class NavigationRoutes {
    private val routes = LinkedHashMap<String, NavigationState>()

    fun update(key: String, state: NavigationState?) {
        if (state?.active == true) routes[key] = state else routes.remove(key)
    }

    fun retainKeys(keys: Set<String>) { routes.keys.retainAll(keys) }
    fun clear() = routes.clear()

    fun byPackage(): Map<String, NavigationState> = routes.values.groupBy { it.sourcePackage }
        .mapNotNull { (pkg, values) -> pkg?.let { it to values.maxBy { state -> state.updatedAtMs } } }
        .toMap()
}

/** Launcher ve HUD kendi paket seçimini aynı çözümlenmiş veriden yapar. */
internal fun latestNavigation(states: Map<String, NavigationState>, packages: Set<String>): NavigationState =
    states.filterKeys { it in packages }.values.maxByOrNull { it.updatedAtMs } ?: NavigationState.NONE
