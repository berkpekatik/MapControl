package com.mapcontrol.launcher

/**
 * Katmanlı yoklama saati (saf, JVM'de sınanır). Her katmanın kendi aralığı vardır; [due] verilen anda süresi
 * dolan katmanların bit maskesini döndürür ve onların bir sonraki zamanını ilerletir. İlk çağrıda hepsi doludur.
 * Katman 0'ın aralığı çağrı başına değiştirilebilir (ör. motor kapalı + hız 0 iken seyreltme).
 */
internal class PollTiers(private val periodsMs: LongArray) {
    private val nextAt = LongArray(periodsMs.size)
    private var first = true

    /** Süresi dolan katmanların maskesi (bit i = katman i). [tier0Ms] katman 0'ın bu turdaki aralığıdır. */
    fun due(now: Long, tier0Ms: Long = periodsMs[0]): Int {
        var mask = 0
        for (i in periodsMs.indices) {
            if (first || now >= nextAt[i]) {
                mask = mask or (1 shl i)
                nextAt[i] = now + if (i == 0) tier0Ms else periodsMs[i]
            }
        }
        first = false
        return mask
    }

    /** Hepsini şimdi okunmuş say (ör. bağlantı yeni kurulup tam tur yapıldığında). */
    fun markAll(now: Long, tier0Ms: Long = periodsMs[0]) {
        first = false
        for (i in periodsMs.indices) nextAt[i] = now + if (i == 0) tier0Ms else periodsMs[i]
    }

    /** Bir sonraki [due] her şeyi döndürsün. */
    fun reset() { first = true }

    fun all(): Int = (1 shl periodsMs.size) - 1
}
