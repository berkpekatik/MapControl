package com.mapcontrol.launcher

/**
 * Yandex aynı rehberlik bildirimini saniyede bir yeniden yayımlıyor (6 Kasım bugreport: 4,5 dk'da 210 bildirim).
 * Bu kapı, içerik değişmediyse ayrıştırmayı atlatır. İki aşama:
 *  1. Aynı anahtar + aynı postTime: aynı gönderi (yeniden eşitleme, bağlanma) → imza hesaplanmadan atlanır.
 *  2. Aynı anahtar + aynı ucuz içerik imzası: yeni gönderi ama metin/çizim aynı → atlanır.
 * Saf Kotlin; tek iş parçacığından (ayrıştırma iş parçacığı) çağrılır.
 */
internal class NavParseGate(private val maxKeys: Int = 8) {
    private class Seen(var postTime: Long, var signature: Int)

    private val seen = LinkedHashMap<String, Seen>()

    /** true: ayrıştır (ve sonucu [remember] ile kaydet). [signature] yalnız gerektiğinde, en fazla bir kez çağrılır. */
    fun shouldParse(key: String, postTime: Long, signature: () -> Int): Boolean {
        val last = seen[key] ?: return true
        if (last.postTime == postTime) return false
        val sig = signature()
        if (last.signature == sig) {
            last.postTime = postTime
            return false
        }
        return true
    }

    fun remember(key: String, postTime: Long, signature: Int) {
        val s = seen[key]
        if (s != null) {
            s.postTime = postTime
            s.signature = signature
            return
        }
        if (seen.size >= maxKeys) seen.remove(seen.keys.first())
        seen[key] = Seen(postTime, signature)
    }

    fun forget(key: String) {
        seen.remove(key)
    }

    fun clear() = seen.clear()

    val size: Int get() = seen.size
}
