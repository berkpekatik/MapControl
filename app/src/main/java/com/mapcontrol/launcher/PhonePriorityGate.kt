package com.mapcontrol.launcher

/**
 * Telefon önceliğinin (Bluetooth'a otomatik geçiş) tek kapısı: yalnız launcher modu açıkken ve kullanıcı ayarı açıkken çalışır.
 * Launcher modunu hiç açmamış kullanıcıda ses kaynağı kendiliğinden değişmez.
 */
internal object PhonePriorityGate {
    fun allowed(launcherMode: Boolean, prefOn: Boolean): Boolean = launcherMode && prefOn
}
