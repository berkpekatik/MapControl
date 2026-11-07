package com.mapcontrol.launcher

/**
 * VDBus rehber/geçmiş parçalarını toplayan saf mantık (Android'siz; birim testli). Sunucu tarafı:
 * liste 10'arlık parçalara bölünür; her parçada index = o parçanın son sırası,
 * total = liste boyu, key = istekteki etiket ([VdListCollector.key]). Boş/null liste tek parça {index 0, total 0}.
 * Bitiş kuralı OEM telefon uygulamasıyla aynı:
 * total > 0 ise index >= total-1 bitti; total == 0 boş bitti; total < 0 veri hatası.
 *
 * Aynı liste iki kez yayımlanabilir (veritabanı yolunda adresli ve adressiz SP yükleme olayı ikisi de _DB yayar):
 * bitişten sonra gelen parçalar yok sayılır.
 */
internal class VdListCollector<T>(val key: String?) {
    enum class Offer { ACCEPTED, OTHER_KEY, AFTER_FINISH }

    private val items = ArrayList<T>()

    /** En az bir parça (bizim etiketle) geldi. */
    var any: Boolean = false
        private set
    var finished: Boolean = false
        private set
    var failed: Boolean = false
        private set
    var chunks: Int = 0
        private set
    var lastTotal: Int = -1
        private set

    @Synchronized
    fun offer(eventKey: String?, index: Int, total: Int, chunk: List<T>): Offer {
        if (eventKey != key) return Offer.OTHER_KEY
        if (finished || failed) return Offer.AFTER_FINISH
        any = true
        chunks++
        lastTotal = total
        when {
            total < 0 -> failed = true
            total == 0 -> finished = true
            else -> {
                items.addAll(chunk)
                if (index >= total - 1) finished = true
            }
        }
        return Offer.ACCEPTED
    }

    @Synchronized
    fun size(): Int = items.size

    /** Hiç parça gelmediyse ya da veri hatasıysa null (boş rehber gibi görünmesin). */
    @Synchronized
    fun result(): List<T>? = if (!any || failed) null else ArrayList(items)

    /** Günlük için yalnızca sayılar (isim/numara yok). */
    @Synchronized
    fun summary(): String = "parça=$chunks kayıt=${items.size} toplam=$lastTotal bitti=$finished hata=$failed"
}

/** Bekleme kararı (saf): ne kadar daha beklenecek; 0 ya da eksi → bekleme bitti. */
internal object VdListWait {
    /** OEM telefon uygulamasıyla aynı: her parçada sıfırlanan 60 × 50 ms = 3 sn sessizlik sınırı. */
    const val IDLE_MS = 3_000L
    /** OEM'de üst sınır yok; çok büyük rehberde paneli sonsuza kadar bekletmemek için. */
    const val HARD_MS = 20_000L

    fun remaining(now: Long, lastChunkAt: Long, startedAt: Long, idleMs: Long = IDLE_MS, hardMs: Long = HARD_MS): Long =
        minOf(lastChunkAt + idleMs, startedAt + hardMs) - now
}
