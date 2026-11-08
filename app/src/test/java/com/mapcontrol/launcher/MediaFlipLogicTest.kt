package com.mapcontrol.launcher

import com.mapcontrol.launcher.MediaFlipLogic.Swipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Medya/telefon kartı: sayfa kuralları, kalıcılık, kaydırma kararı, oturma ve uç direnci; telefon panelinin düzenden kalkışı ve bant yerleşimi. */
class MediaFlipLogicTest {

    @Test
    fun noPhoneMeansMediaFaceAndHiddenFlip() {
        assertEquals(CardFace.MEDIA, MediaFlipLogic.shown(CardFace.PHONE, connected = false))
        assertEquals(CardFace.MEDIA, MediaFlipLogic.shown(CardFace.MEDIA, connected = false))
        assertEquals(CardFace.PHONE, MediaFlipLogic.shown(CardFace.PHONE, connected = true))
        assertEquals(CardFace.MEDIA, MediaFlipLogic.shown(CardFace.MEDIA, connected = true))
        assertFalse(MediaFlipLogic.flipAvailable(false))
        assertTrue(MediaFlipLogic.flipAvailable(true))
    }

    @Test
    fun disconnectHoldsMediaInMemoryWithoutTouchingTheSavedFace() {
        // Ekran açıkken bağlı görülen telefon koptu: kart medyada tutulur (kayıtlı yüz parametre olarak hiç değişmez).
        assertTrue(MediaFlipLogic.holdAfter(false, wasConnected = true, connected = false))
        // Aynı görünürlükte geri bağlandı: bayrak kalır, kart kendiliğinden telefona dönmez.
        assertTrue(MediaFlipLogic.holdAfter(true, wasConnected = false, connected = true))
        assertEquals(CardFace.MEDIA, MediaFlipLogic.shown(CardFace.PHONE, connected = true, held = true))
        // İlk bildirim (durum bilinmiyor) ya da bağlanma bayrak koymaz: açılışta geç bağlanan telefonla son yüz gelir.
        assertFalse(MediaFlipLogic.holdAfter(false, wasConnected = null, connected = false))
        assertFalse(MediaFlipLogic.holdAfter(false, wasConnected = null, connected = true))
        assertFalse(MediaFlipLogic.holdAfter(false, wasConnected = false, connected = true))
        assertEquals(CardFace.PHONE, MediaFlipLogic.shown(CardFace.PHONE, connected = true, held = false))
        // Bağlı kalma (ör. arama bitti) bir şey değiştirmez.
        assertFalse(MediaFlipLogic.holdAfter(false, wasConnected = true, connected = true))
    }

    @Test
    fun gestureThatWentVerticalFirstNeverPagesLater() {
        val g = MediaFlipLogic.SwipeGesture(slopPx = 10f)
        g.down(startsOnDrag = false)
        assertEquals(Swipe.IGNORE, g.move(2f, 30f, enabled = true))
        // Sonradan yataya dönen sürükleme: yine bırakılır (alttaki kaydırma alanından dokunuş alınmaz).
        assertEquals(Swipe.IGNORE, g.move(150f, 30f, enabled = true))
        // Yeni basış kararı sıfırlar; yakalanan sürükleme bırakılana dek sürüklemedir (dikeye kaysa da).
        g.down(startsOnDrag = false)
        assertEquals(Swipe.WAIT, g.move(4f, 1f, enabled = true))
        assertEquals(Swipe.DRAG, g.move(20f, 2f, enabled = true))
        assertEquals(Swipe.DRAG, g.move(25f, 60f, enabled = true))
        // Dışarıdan kesilen (telefon koptu) sürükleme dokunuş bitene dek yok sayılır.
        g.cancel()
        assertEquals(Swipe.IGNORE, g.move(200f, 4f, enabled = true))
        // İlerleme çubuğunda başlayan dokunuş hiç kaydırmaz.
        g.down(startsOnDrag = true)
        assertEquals(Swipe.IGNORE, g.move(200f, 0f, enabled = true))
    }

    @Test
    fun faceRoundTripsThroughStorageAndUnknownIsMedia() {
        CardFace.values().forEach { assertEquals(it, MediaFlipLogic.decode(MediaFlipLogic.encode(it))) }
        assertEquals(CardFace.MEDIA, MediaFlipLogic.decode(null))
        assertEquals(CardFace.MEDIA, MediaFlipLogic.decode(""))
        assertEquals(CardFace.MEDIA, MediaFlipLogic.decode("PHONE?"))
        assertEquals(CardFace.PHONE, MediaFlipLogic.other(CardFace.MEDIA))
        assertEquals(CardFace.MEDIA, MediaFlipLogic.other(CardFace.PHONE))
    }

    @Test
    fun iconGlowsOnlyOnPhoneFaceWhilePlaying() {
        assertTrue(MediaFlipLogic.iconGlows(CardFace.PHONE, playing = true))
        assertFalse(MediaFlipLogic.iconGlows(CardFace.PHONE, playing = false))
        assertFalse(MediaFlipLogic.iconGlows(CardFace.MEDIA, playing = true))
        assertFalse(MediaFlipLogic.iconGlows(CardFace.MEDIA, playing = false))
    }

    private fun swipe(dx: Float, dy: Float, onDrag: Boolean = false, enabled: Boolean = true) =
        MediaFlipLogic.swipe(dx, dy, slopPx = 10f, startsOnDrag = onDrag, enabled = enabled)

    @Test
    fun horizontalSwipeDragsVerticalAndSeekBarDoNot() {
        assertEquals(Swipe.WAIT, swipe(4f, 1f))
        assertEquals(Swipe.DRAG, swipe(20f, 2f))
        assertEquals(Swipe.DRAG, swipe(-80f, -10f))
        // Dikey kayma: bırakılır (kaydırma alanları ve sayfa hareketi çalışır).
        assertEquals(Swipe.IGNORE, swipe(5f, 30f))
        // Çapraz (yatay dikeyin 1,5 katı değil): kaydırmaz.
        assertEquals(Swipe.IGNORE, swipe(80f, 70f))
        // İlerleme çubuğunda başlayan dokunuş hiçbir mesafede kaydırmaz.
        assertEquals(Swipe.IGNORE, swipe(200f, 0f, onDrag = true))
        // Bağlı telefon yok: kaydırma kapalı.
        assertEquals(Swipe.IGNORE, swipe(200f, 0f, enabled = false))
    }

    private val fling = 800f

    @Test
    fun releaseSettlesToNearestOrNextPastThirtyPercent() {
        // Medyadan sola (telefona doğru) sürükleme: %30'a kadar geri döner, üstü telefona.
        assertEquals(0, MediaFlipLogic.settle(0, 0.2f, 0f, fling, maxPage = 1))
        assertEquals(0, MediaFlipLogic.settle(0, 0.3f, 0f, fling, maxPage = 1))
        assertEquals(1, MediaFlipLogic.settle(0, 0.31f, 0f, fling, maxPage = 1))
        assertEquals(1, MediaFlipLogic.settle(0, 0.9f, 0f, fling, maxPage = 1))
        // Telefondan sağa (medyaya doğru).
        assertEquals(1, MediaFlipLogic.settle(1, 0.75f, 0f, fling, maxPage = 1))
        assertEquals(0, MediaFlipLogic.settle(1, 0.6f, 0f, fling, maxPage = 1))
        // Uçtaki taşma: yerine döner.
        assertEquals(0, MediaFlipLogic.settle(0, -0.1f, 0f, fling, maxPage = 1))
        assertEquals(1, MediaFlipLogic.settle(1, 1.1f, 0f, fling, maxPage = 1))
    }

    @Test
    fun flingPagesInVelocityDirectionAndStaysInRange() {
        // Kısa ama hızlı savurma: sola savurma sonraki sayfa, sağa savurma önceki.
        assertEquals(1, MediaFlipLogic.settle(0, 0.05f, -900f, fling, maxPage = 1))
        assertEquals(0, MediaFlipLogic.settle(1, 0.95f, 900f, fling, maxPage = 1))
        // Eşik altı hız: mesafe kuralı.
        assertEquals(0, MediaFlipLogic.settle(0, 0.05f, -700f, fling, maxPage = 1))
        // Geri savurma sürüklemeyi geri alır.
        assertEquals(0, MediaFlipLogic.settle(0, 0.6f, 1200f, fling, maxPage = 1))
        // Uçtan dışarı savurma ve tek sayfa (telefon yok): aralıkta kalır.
        assertEquals(0, MediaFlipLogic.settle(0, -0.05f, 2000f, fling, maxPage = 1))
        assertEquals(1, MediaFlipLogic.settle(1, 1.05f, -2000f, fling, maxPage = 1))
        assertEquals(0, MediaFlipLogic.settle(0, 0.6f, -2000f, fling, maxPage = 0))
    }

    @Test
    fun dragFollowsInsideAndResistsPastTheEnds() {
        // İçeride 1:1.
        assertEquals(0f, MediaFlipLogic.resist(0f, 1), 0f)
        assertEquals(0.42f, MediaFlipLogic.resist(0.42f, 1), 0f)
        assertEquals(1f, MediaFlipLogic.resist(1f, 1), 0f)
        // Uçlarda: taşma küçülür, yönü korunur, hiçbir mesafede sınırı geçmez, artan taşmada artar.
        val small = MediaFlipLogic.resist(-0.1f, 1)
        assertTrue(small < 0f && small > -0.1f)
        val big = MediaFlipLogic.resist(-5f, 1)
        assertTrue(big < small && big > -MediaFlipLogic.MAX_OVER)
        val right = MediaFlipLogic.resist(1.2f, 1)
        assertTrue(right > 1f && right < 1f + MediaFlipLogic.MAX_OVER)
        // Başlangıç eğimi RESIST.
        assertEquals(-0.001f * MediaFlipLogic.RESIST, MediaFlipLogic.resist(-0.001f, 1), 1e-5f)
        // Tek sayfa: 0'ın iki yanı dirençli.
        assertTrue(MediaFlipLogic.resist(0.5f, 0) < 0.5f * MediaFlipLogic.RESIST + 1e-6f)
    }

    @Test
    fun pagesMapToFaces() {
        assertEquals(0, MediaFlipLogic.page(CardFace.MEDIA))
        assertEquals(1, MediaFlipLogic.page(CardFace.PHONE))
        CardFace.values().forEach { assertEquals(it, MediaFlipLogic.faceAt(MediaFlipLogic.page(it))) }
    }

    @Test
    fun hitTestIsHalfOpenAndEmptyRectNeverHits() {
        assertTrue(MediaFlipLogic.hits(10f, 10f, 10, 10, 20, 20))
        assertTrue(MediaFlipLogic.hits(19.9f, 19.9f, 10, 10, 20, 20))
        assertFalse(MediaFlipLogic.hits(20f, 15f, 10, 10, 20, 20))
        assertFalse(MediaFlipLogic.hits(15f, 9f, 10, 10, 20, 20))
        assertFalse(MediaFlipLogic.hits(10f, 10f, 10, 10, 10, 20))
    }

    // --- Telefon panelinin düzenden kalkışı ---

    private fun layout(left: WidgetType?, right: WidgetType?) = SahneLayout(
        left = left?.let { SahneSlot(it.key, it) },
        right = right?.let { SahneSlot(it.key, it) },
        strip = SahneSlots.defaultPark().strip,
    )

    @Test
    fun rightPhonePanelIsRemovedWhenMediaIsOnTheLeft() {
        val out = SahneSlots.migratePhonePanel(layout(WidgetType.MEDIA, WidgetType.PHONE))
        assertEquals(WidgetType.MEDIA, out.left?.type)
        // Boşalan sağ yuva varsayılanla dolar (kullanıcı kararı 6 Kasım: sağda Yolculuk/Tüketim).
        assertEquals(WidgetType.TRIP, out.right?.type)
        val decoded = SahneSlots.decode(SahneSlots.encode(layout(WidgetType.MEDIA, WidgetType.PHONE)))!!
        assertEquals(WidgetType.MEDIA, decoded.left?.type)
        assertEquals(WidgetType.TRIP, decoded.right?.type)
        // Tiggo 7'de telefon paneli sunulmaz; Tiggo 8'in dört panelinde sunulur (7 Kasım).
        assertFalse(WidgetType.PHONE in SahneForm.offered(SahneSlotKind.PANEL))
        assertTrue(WidgetType.PHONE in SahneForm.offered(SahneSlotKind.PANEL, quad = true))
    }

    @Test
    fun phonePanelBecomesMediaWhenNoMediaPanelExists() {
        // İki yüzlü kart solda: medya sola, soldaki widget telefonun boşalttığı sağ yuvaya.
        val out = SahneSlots.migratePhonePanel(layout(WidgetType.NAVIGATION, WidgetType.PHONE))
        assertEquals(WidgetType.MEDIA, out.left?.type)
        assertEquals(WidgetType.NAVIGATION, out.right?.type)
        val emptyLeft = SahneSlots.migratePhonePanel(layout(null, WidgetType.PHONE))
        assertEquals(WidgetType.MEDIA, emptyLeft.left?.type)
        assertNull(emptyLeft.right)
        val left = SahneSlots.migratePhonePanel(layout(WidgetType.PHONE, WidgetType.TRIP))
        assertEquals(WidgetType.MEDIA, left.left?.type)
        assertEquals(WidgetType.TRIP, left.right?.type)
        val unchanged = layout(WidgetType.MEDIA, WidgetType.TRIP)
        assertEquals(unchanged, SahneSlots.migratePhonePanel(unchanged))
    }

    @Test
    fun emptySideCollapsesOutsideEditingOnTwoPanelLayoutOnly() {
        val l = layout(WidgetType.MEDIA, null)
        assertTrue(SahneBand.occupies(l, 0, editing = false))
        assertFalse(SahneBand.occupies(l, 1, editing = false))
        // Düzenlemede boş yuva "+" ile görünür.
        assertTrue(SahneBand.occupies(l, 1, editing = true))
        // Dört panelde her yuva yerinde.
        val q = SahneSlots.defaultsQuad(LayoutMode.PARK).copy(right = null)
        assertTrue(SahneBand.occupies(q, 1, editing = false))
        assertTrue(SahneBand.occupies(SahneSlots.defaultPark(), 1, editing = false))
    }
}
