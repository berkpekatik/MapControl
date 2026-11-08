package com.mapcontrol.launcher

import java.util.Locale

/**
 * Rehberlik bildiriminin "navigasyonu bitir" eylemini bulur (Android'e bağlı değil; JVM'de sınanır).
 *
 * Eylem sırasına güvenilmez: başlıklar Türkçe / İngilizce / Rusça bilinen bitirme ifadeleriyle eşlenir
 * (Google "Çık" / "Exit", Yandex "Завершить" / "Bitir" / "Stop" / "Finish"). Başlık yazılarda ve günlükte gösterilmez
 * (adres içerebilir); burada yalnız eşleşme sonucu döner. Eşleşme yoksa null: düğme gizlenir, eylem uydurulmaz.
 */
object NavEndAction {

    /** Küçük harfe (Türkçe kurallı), noktalama ve fazla boşluk atılmış hâl. */
    internal fun normalize(title: CharSequence?): String {
        if (title == null) return ""
        val lower = title.toString().lowercase(Locale("tr"))
        return lower.map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").trim().replace(Regex("\\s+"), " ")
    }

    private val EXACT = setOf(
        "çık", "cik", "çıkış", "cikis", "bitir", "durdur", "sonlandır", "sonlandir", "kapat",
        "exit", "end", "stop", "finish", "quit", "close",
        "завершить", "остановить", "выйти", "закончить",
    )

    private val PHRASES = listOf(
        Regex("""(navigasyon|rehber|rota|yol tarifi)\p{L}*\s+(çık|cik|bitir|durdur|sonlandır|sonlandir|kapat)"""),
        Regex("""(navigasyon|rehber|rota)\p{L}*\s*(den|dan)\s+(çık|cik)"""),
        Regex("""(exit|end|stop|quit|finish|cancel)\s+(the\s+)?(navigation|directions|route|trip|guidance)"""),
        Regex("""(завершить|остановить|закончить|отменить)\s+(маршрут|навигацию|поездку)"""),
    )

    /** Bu eylem başlığı bitirme eylemi mi. */
    fun isEndTitle(title: CharSequence?): Boolean {
        val t = normalize(title)
        if (t.isEmpty()) return false
        return t in EXACT || PHRASES.any { it.containsMatchIn(t) }
    }

    /** Eylem başlıkları arasında ilk bitirme eyleminin sırası; yoksa -1 (sıra yalnız dönüş değeri, eşleme başlıkladır). */
    fun indexOf(titles: List<CharSequence?>): Int = titles.indexOfFirst { isEndTitle(it) }
}
