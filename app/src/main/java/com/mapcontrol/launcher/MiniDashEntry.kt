package com.mapcontrol.launcher

import android.content.pm.PackageManager

/**
 * "Mini gösterge" uygulama listesi simgesi ([ALIAS_CLASS], manifestte varsayılan kapalı activity-alias). Simge yalnız
 * FrostNav launcher modu açıkken görünür; mod kapanınca kalkar. [MiniDashActivity]'nin kendisi hep etkindir: bölünmüş
 * ekran ve sürüş ekranı onu bileşen adıyla açar, simgeye bağlı değildir. Uygulayan: `util/LauncherModeManager`.
 * Saf karar, JVM'de sınanır.
 */
object MiniDashEntry {

    /** Uygulama listesindeki "Mini gösterge" simgesi (MAIN + LAUNCHER filtresi yalnız bu alias'ta). */
    const val ALIAS_CLASS = "com.mapcontrol.launcher.MiniDashLauncherAlias"

    /** Launcher moduna göre simgenin olması gereken bileşen durumu. */
    @JvmStatic
    fun wantedState(launcherMode: Boolean): Int =
        if (launcherMode) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    /**
     * Yazılacak bileşen durumu; zaten istenen durumdaysa null (gereksiz paket yayını olmasın). Manifest varsayılanı
     * (DEFAULT) kapalı sayılır: mod kapalıyken DEFAULT'a dokunulmaz.
     */
    @JvmStatic
    fun stateToWrite(current: Int, launcherMode: Boolean): Int? {
        val wanted = wantedState(launcherMode)
        val effective = if (current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            current
        }
        return if (effective == wanted) null else wanted
    }
}
