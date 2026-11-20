package com.mapcontrol.nav

/**
 * Yerleşik HUD aktarım oturumu. Yalnız seri gönderim iş parçacığından çağrılır.
 * Kısmi gönderim veya bağlantı kaybından sonra önce üreticinin temizleme komutu uygulanır.
 */
internal class NativeHudSession(
    initiallyOwned: Boolean = false,
    private val persistOwnership: (Boolean) -> Boolean = { true },
    private val send: (IntArray) -> Boolean,
) {
    private var owned = initiallyOwned
    private var active = false
    private var dirty = initiallyOwned
    private var last: NativeHudFrame? = null
    var needsRepeatClear = false
        private set
    val hasWork: Boolean get() = active || dirty || needsRepeatClear

    fun connectionLost() {
        dirty = dirty || active
        active = false
        last = null
    }

    fun apply(next: NativeHudFrame?): Boolean {
        if (next == null) {
            if (!active && !dirty) return true
            val cleared = send(NativeHudProtocol.stop())
            active = false
            dirty = !cleared
            last = null
            needsRepeatClear = cleared
            return cleared
        }
        needsRepeatClear = false
        if (next == last && !dirty) return true
        val previous = last
        val changedFields = previous != null && (previous.sourcePackage != next.sourcePackage ||
            previous.hasTurn != next.hasTurn || previous.hasRemaining != next.hasRemaining)
        if (dirty || (active && changedFields)) {
            if (!send(NativeHudProtocol.stop())) {
                dirty = true
                return false
            }
            active = false
            dirty = false
            last = null
        }
        if (!active) {
            // Süreç gönderim sırasında sonlansa da bir sonraki açılışta temizlenebilsin.
            if (!owned && !persistOwnership(true)) return false
            owned = true
            if (!send(NativeHudProtocol.start())) {
                dirty = true
                return false
            }
            active = true
        }
        for (packet in next.packets) {
            if (!send(packet.toIntArray())) {
                dirty = true
                last = null
                return false
            }
        }
        last = next
        dirty = false
        return true
    }

    /** Üretici bitiş komutunu 1500 ms sonra tekrarlar; yeni rota gelince tekrar iptal olur. */
    fun repeatClear(): Boolean {
        if (!needsRepeatClear || active) return true
        val cleared = send(NativeHudProtocol.stop()) && (!owned || persistOwnership(false))
        if (cleared) owned = false
        needsRepeatClear = !cleared
        return cleared
    }
}
