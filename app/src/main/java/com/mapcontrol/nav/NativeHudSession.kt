package com.mapcontrol.nav

/**
 * Yerleşik HUD aktarım oturumu. Yalnız seri gönderim iş parçacığından çağrılır.
 *
 * Referans davranış (JCarTools e0.a0.f/g): her değişen aktif kare StartHUD + üç veri paketi gönderir.
 * Rota bitince 50 ms sonra StartHUD olmadan üç boş paket, 20 s sonra navigasyon bitiş yayını yapılır.
 * Kısmi gönderim veya bağlantı kaybı eski çerçeveyi geçersiz kılar; yeniden bağlanınca tam kare gönderilir.
 */
internal class NativeHudSession(
    initiallyOwned: Boolean = false,
    private val blankDelayMs: Long = 50L,
    private val cleanupDelayMs: Long = 20_000L,
    /**
     * Bitiş yayınından hemen sonra gönderilen kareyi J7 HUD yutuyor (araçta 2026-11-24: bitişten 1 s sonraki kare
     * görünmedi, 7 s sonraki göründü). Bu süre içinde gönderilen kare süre dolunca bir kez daha gönderilir.
     */
    private val settleAfterFinishMs: Long = 3_000L,
    private val now: () -> Long = { System.nanoTime() / 1_000_000L },
    private val persistOwnership: (Boolean) -> Boolean = { true },
    private val finish: () -> Boolean = { true },
    private val send: (IntArray) -> Boolean,
) {
    private var owned = initiallyOwned
    private var active = false
    private var dirty = initiallyOwned
    private var last: NativeHudFrame? = null

    private var pendingEnd = false
    private var blankSent = false
    private var stopSent = false
    private var blankAt = 0L
    private var stopAt = 0L
    /** Bitiş yayınından sonraki yutulma penceresinin sonu; 0 ise pencere yok. */
    private var settleUntil = 0L
    /** Pencere içinde kare gönderildi; pencere bitince yeniden gönderilecek. */
    private var refreshDue = false

    val hasWork: Boolean get() = active || dirty || pendingEnd

    /** Yeni kareleri bekleyen bir temizlik varsa sonraki işin gecikmesi; yoksa null. */
    fun nextActionDelayMs(): Long? = nextActionAtMs()?.let { (it - now()).coerceAtLeast(0L) }

    fun nextActionAtMs(): Long? {
        if (pendingEnd) return if (blankSent) stopAt else blankAt
        return if (refreshDue && active) settleUntil else null
    }

    fun connectionLost() {
        if (active || dirty) {
            active = false
            dirty = true
            last = null
        }
    }

    fun apply(next: NativeHudFrame?): Boolean {
        if (next == null) return scheduleEnd(false)
        if (pendingEnd) {
            cancelEnd()
            // Boşluk gönderilmediyse HUD eski kareyi hâlâ gösteriyor; aynı kareyi yeniden göndermeye gerek yok.
            if (next == last && active && !dirty) return true
        }
        if (next == last && active && !dirty) return true
        if (!owned && !persistOwnership(true)) return false
        owned = true
        if (!send(NativeHudProtocol.start())) {
            active = false
            dirty = true
            last = null
            return false
        }
        active = true
        dirty = false
        for (packet in next.packets) {
            if (!send(packet.toIntArray())) {
                active = false
                dirty = true
                last = null
                return false
            }
        }
        last = next
        if (now() < settleUntil) refreshDue = true
        return true
    }

    /** Kullanıcı kapatması / servis durması: beklemeden boşalt ve bitir; başarısızsa bağlantıda yeniden dene. */
    fun shutdown(): Boolean {
        if (!owned && !active && !dirty && !pendingEnd) return true
        if (!pendingEnd) {
            pendingEnd = true
            blankSent = false
            stopSent = false
            blankAt = now()
            stopAt = blankAt
        } else if (!stopSent) {
            // Doğal rota bitişi bekliyorsa kullanıcı kapatması 20 s'yi beklemez.
            blankAt = blankAt.coerceAtMost(now())
            stopAt = blankAt
        }
        return tick()
    }

    /** Zamanı gelen boşaltma ve bitiş adımlarını yürütür. */
    fun tick(): Boolean {
        if (!pendingEnd) return refreshIfSettled()
        val current = now()
        if (!blankSent && current >= blankAt) {
            if (!sendBlank()) return false
            blankSent = true
            active = false
            dirty = false
        }
        if (!blankSent || current < stopAt) return true
        if (!stopSent) {
            if (!finish()) return false
            stopSent = true
            settleUntil = now() + settleAfterFinishMs
        }
        if (owned && !persistOwnership(false)) return false
        owned = false
        pendingEnd = false
        blankSent = false
        stopSent = false
        blankAt = 0L
        stopAt = 0L
        last = null
        dirty = false
        return true
    }

    /** Bitişten hemen sonra gönderilmiş kareyi pencere dolunca bir kez yeniden gönderir. */
    private fun refreshIfSettled(): Boolean {
        val frame = last
        if (!refreshDue || now() < settleUntil) return true
        refreshDue = false
        settleUntil = 0L
        if (!active || frame == null) return true
        dirty = true
        return apply(frame)
    }

    private fun scheduleEnd(immediate: Boolean): Boolean {
        if (pendingEnd) return true
        if (!owned && !active && !dirty) return true
        pendingEnd = true
        blankSent = false
        stopSent = false
        blankAt = now() + if (immediate) 0L else blankDelayMs
        stopAt = now() + if (immediate) 0L else cleanupDelayMs
        return true
    }

    private fun cancelEnd() {
        pendingEnd = false
        blankSent = false
        stopSent = false
        blankAt = 0L
        stopAt = 0L
    }

    private fun sendBlank(): Boolean {
        // Başarısız bir temizleme de eski karenin bir kısmını değiştirmiş olabilir.
        // Aynı rota geri gelirse tam kareyi yeniden göndermek gerekir.
        active = false
        dirty = true
        last = null
        for (packet in NativeHudProtocol.blank().packets) {
            if (!send(packet.toIntArray())) return false
        }
        return true
    }
}
