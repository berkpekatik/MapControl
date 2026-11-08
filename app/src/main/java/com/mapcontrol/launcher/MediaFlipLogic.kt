package com.mapcontrol.launcher

/** Medya kartının iki sayfası: ilk sayfa medya, yandaki sayfa telefon ([MediaPhoneFlip]). */
enum class CardFace { MEDIA, PHONE }

/**
 * Medya/telefon kartının saf kuralları (JVM'de sınanır): hangi sayfa gösterilir, sayfa simgesi ne zaman görünür ve ışır,
 * kayıtlı sayfa ne zaman değişir, yatay sürükleme ne zaman yakalanır, bırakınca hangi sayfaya oturulur.
 */
object MediaFlipLogic {

    /**
     * Gösterilecek yüz: bağlı telefon yokken hep medya; varken kayıtlı (son) yüz. [held]: bu görünürlükte telefon koptuğu için
     * kart medyada tutuluyor ([holdAfter]); kayıt değişmez.
     */
    fun shown(saved: CardFace, connected: Boolean, held: Boolean = false): CardFace =
        if (connected && !held) saved else CardFace.MEDIA

    /** Sayfa simgesi yalnız bağlı telefon varken görünür (telefon sayfasında gösterilecek bir şey yok). */
    fun flipAvailable(connected: Boolean): Boolean = connected

    /**
     * Bağlantı bildirimi sonrası "medyada tut" bayrağı (yalnız bellekte; kayıtlı yüz hiç değişmez): ekran açıkken daha önce
     * bağlı görülen telefon koparsa kart medyaya döner ve aynı görünürlükte telefon geri gelince kendiliğinden telefona
     * dönmez (kullanıcı o an medya kontrollerine dokunuyor olabilir). Bayrak ekran yeniden görünür olunca ya da kullanıcı
     * sayfa değiştirince kalkar; böylece Bluetooth kopması, kapsama dışı ya da kontak kapanması kullanıcının son seçimini silmez.
     * İlk bildirim ([wasConnected] null: durum henüz bilinmiyor) bayrağı koymaz; açılışta telefon bağlanınca son yüz gelir.
     */
    fun holdAfter(held: Boolean, wasConnected: Boolean?, connected: Boolean): Boolean =
        held || (wasConnected == true && !connected)

    /** Sayfa simgesi ışır mı: kart telefon sayfasındayken arkada müzik çalıyorsa (yalnız simge yanar; rozet/nokta yok). */
    fun iconGlows(face: CardFace, playing: Boolean): Boolean = face == CardFace.PHONE && playing

    /** Dokununca gidilecek yüz. */
    fun other(face: CardFace): CardFace = if (face == CardFace.MEDIA) CardFace.PHONE else CardFace.MEDIA

    fun encode(face: CardFace): String = if (face == CardFace.PHONE) PHONE_KEY else MEDIA_KEY

    /** Bilinmeyen ya da boş kayıt medya. */
    fun decode(raw: String?): CardFace = if (raw == PHONE_KEY) CardFace.PHONE else CardFace.MEDIA

    private const val MEDIA_KEY = "media"
    private const val PHONE_KEY = "phone"

    /** Yatay sürüklemenin kararı. */
    enum class Swipe {
        /** Bu dokunuş sayfa kaydırmaz (dikey, sürüklenen bir kontrolde başladı ya da kaydırma kapalı). */
        IGNORE,
        /** Henüz karar yok (eşik altında). */
        WAIT,
        /** Yatay niyet belli: dokunuş karttan alınır (alttaki düğme basılı kalmaz), içerik parmağı izler. */
        DRAG,
    }

    /**
     * Basıştan bu yana [dx]/[dy] (px) kaymış dokunuşun kararı. [startsOnDrag]: dokunuş ilerleme çubuğu gibi yatay sürüklenen bir
     * kontrolde başladı (sayfa hiç kaymaz). [enabled]: kaydırma açık (bağlı telefon var). Yatay niyet için yatay kayma
     * dikeyin [DOMINANCE] katından büyük olmalı; dikey kayma eşiği önce geçerse dokunuş bırakılır.
     */
    fun swipe(dx: Float, dy: Float, slopPx: Float, startsOnDrag: Boolean, enabled: Boolean): Swipe {
        if (!enabled || startsOnDrag) return Swipe.IGNORE
        val ax = kotlin.math.abs(dx)
        val ay = kotlin.math.abs(dy)
        val horizontal = ax > ay * DOMINANCE
        if (!horizontal && ay > slopPx) return Swipe.IGNORE
        if (!horizontal) return Swipe.WAIT
        return if (ax > slopPx) Swipe.DRAG else Swipe.WAIT
    }

    /**
     * Bir dokunuşun kaydırma izi: [swipe] kararını hareket boyunca hatırlar. IGNORE olan dokunuş (ör. önce dikey kaydı)
     * bırakılana dek bir daha yakalanmaz; önce dikey gidip sonra yataya dönen sürükleme sayfayı kaydırmaz. DRAG olan dokunuş
     * bırakılana dek sürüklemedir (parmak sonradan dikeye kaysa da sayfa izlemeyi sürdürür). [cancel]: telefon koptu gibi
     * dışarıdan kesilen sürükleme; dokunuş bitene dek yok sayılır.
     */
    class SwipeGesture(private val slopPx: Float) {
        private var blocked = false
        private var dragging = false

        /** Yeni basış; [startsOnDrag]: ilerleme çubuğu gibi yatay sürüklenen bir kontrolde. */
        fun down(startsOnDrag: Boolean) {
            blocked = startsOnDrag
            dragging = false
        }

        fun move(dx: Float, dy: Float, enabled: Boolean): Swipe {
            if (blocked) return Swipe.IGNORE
            if (dragging) return Swipe.DRAG
            val s = swipe(dx, dy, slopPx, false, enabled)
            if (s == Swipe.IGNORE) blocked = true
            if (s == Swipe.DRAG) dragging = true
            return s
        }

        fun cancel() {
            blocked = true
            dragging = false
        }
    }

    // --- Sayfalar: 0 medya, 1 telefon; konum sayfa genişliği biriminde (0..1, uçlarda biraz taşabilir) ---

    fun page(face: CardFace): Int = if (face == CardFace.PHONE) 1 else 0

    fun faceAt(page: Int): CardFace = if (page >= 1) CardFace.PHONE else CardFace.MEDIA

    /**
     * Uçlarda direnç: [raw] konum [0, maxPage] dışına çıkınca taşma yumuşakça sönümlenir (başta [RESIST] eğimli, en çok
     * [MAX_OVER] sayfa); içeride konum aynen (parmağı 1:1 izler).
     */
    fun resist(raw: Float, maxPage: Int): Float {
        val max = maxPage.coerceAtLeast(0).toFloat()
        return when {
            raw < 0f -> -damp(-raw)
            raw > max -> max + damp(raw - max)
            else -> raw
        }
    }

    private fun damp(over: Float): Float = over * RESIST / (1f + over * RESIST / MAX_OVER)

    /**
     * Bırakılınca oturulacak sayfa. [anchor]: sürüklemenin başladığı sayfa, [position]: şu anki konum, [velocityX]: parmak hızı
     * (px/s; artı = sağa, yani önceki sayfaya doğru), [flingPx]: savurma eşiği (px/s). Eşiği aşan savurma hız yönündeki
     * sayfaya; değilse [NEXT_FRACTION]'dan fazla sürüklendiyse o yöndeki sayfaya; yoksa başlangıç sayfasına döner.
     */
    fun settle(anchor: Int, position: Float, velocityX: Float, flingPx: Float, maxPage: Int): Int {
        val target = when {
            kotlin.math.abs(velocityX) >= flingPx -> if (velocityX < 0f) anchor + 1 else anchor - 1
            position - anchor > NEXT_FRACTION -> anchor + 1
            position - anchor < -NEXT_FRACTION -> anchor - 1
            else -> anchor
        }
        return target.coerceIn(0, maxPage.coerceAtLeast(0))
    }

    /** (x, y) noktası [l, r) × [t, b) dikdörtgeninde mi. */
    fun hits(x: Float, y: Float, l: Int, t: Int, r: Int, b: Int): Boolean = r > l && b > t && x >= l && x < r && y >= t && y < b

    const val DOMINANCE = 1.5f
    /** Sonraki sayfaya geçmek için sürükleme payı (sayfa genişliğinin oranı). */
    const val NEXT_FRACTION = 0.3f
    /** Uçta taşmanın başlangıç eğimi ve üst sınırı (sayfa). */
    const val RESIST = 0.35f
    const val MAX_OVER = 0.12f
}
