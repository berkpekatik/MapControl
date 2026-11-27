package com.mapcontrol.nav

/**
 * HUD donanım kararı (Android'e bağlı değil; JVM'de sınanır). Sonuç: true var, false yok, null bilinmiyor.
 *
 * 1. Araç config biti okunabiliyorsa karar odur.
 * 2. Okunamıyorsa (Jaecoo'da SELinux) HUD modülünün kendi yanıtı kanıttır; ama yalnız sıfırdan farklı bir değer.
 *    CarInfo yanıt vermeyen kimlik için varsayılan `[0]` döndürebilir; HUD'lu J7'de (2026-11-19..24, 15 binden fazla
 *    yoklama) state/display yalnız 0/1, 2/0, 3/0 görüldü, 0/0 hiç görülmedi.
 * 3. Bir kez kanıt geldiyse süreç boyunca kalıcıdır: HUD veri alırken ya da güç geçişinde kısa süre yanıt vermezse
 *    destek düşmez (oturum kapanıp son temizleme karesi kaybolmasın).
 * 4. Araç servisi bağlıyken [giveUpMs] boyunca kanıt gelmezse HUD yok sayılır: kart "kontrol ediliyor"da asılı
 *    kalmaz, yoklama durur. HUD'lu J7'de yanıtsız yoklama yalnız süreç açılışında görüldü.
 */
internal class HudSupportDecider(private val giveUpMs: Long = 60_000L) {
    private var probedOk = false
    private var unansweredSince: Long? = null

    fun decide(configBit: Boolean?, state: IntArray?, display: IntArray?, serviceConnected: Boolean, nowMs: Long): Boolean? {
        if (configBit != null) {
            unansweredSince = null
            return configBit
        }
        if (isEvidence(state) || isEvidence(display)) {
            probedOk = true
            unansweredSince = null
            return true
        }
        if (probedOk) return true
        if (!serviceConnected) {
            unansweredSince = null
            return null
        }
        val since = unansweredSince ?: nowMs.also { unansweredSince = it }
        return if (nowMs - since >= giveUpMs) false else null
    }

    private fun isEvidence(values: IntArray?): Boolean = values != null && values.any { it != 0 }
}
