package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapControlWidgetMathTest {

    @Test fun webUrlMatchesMapControlFormat() {
        assertEquals("http://192.168.43.1:7462", MapControlWidgetMath.webUrl("192.168.43.1"))
        assertEquals("http://10.0.2.16:8080", MapControlWidgetMath.webUrl(" 10.0.2.16 ", 8080))
        assertNull(MapControlWidgetMath.webUrl(null))
        assertNull(MapControlWidgetMath.webUrl(""))
        assertNull(MapControlWidgetMath.webUrl("1.2.3.4", 0))
    }

    @Test fun loopbackIsNotReachableFromPhone() {
        assertTrue(MapControlWidgetMath.isLoopback("127.0.0.1"))
        assertTrue(MapControlWidgetMath.isLoopback(null))
        assertFalse(MapControlWidgetMath.isLoopback("192.168.43.1"))
    }

    @Test fun miniDashSplitOtherPrefersLastThenYandex() {
        assertEquals("com.spotify.music", MapControlWidgetMath.miniDashSplitOther("com.spotify.music") { true })
        assertEquals("a.b/a.b.Main", MapControlWidgetMath.miniDashSplitOther("a.b/a.b.Main") { it == "a.b" })
        // Son seçilen kaldırıldı: varsayılana düşülür, o da yoksa null.
        assertEquals("ru.yandex.yandexmaps", MapControlWidgetMath.miniDashSplitOther("com.spotify.music") { it == "ru.yandex.yandexmaps" })
        assertNull(MapControlWidgetMath.miniDashSplitOther("com.spotify.music") { false })
        assertEquals("ru.yandex.yandexmaps", MapControlWidgetMath.miniDashSplitOther(null) { it == "ru.yandex.yandexmaps" })
        assertEquals("ru.yandex.yandexmaps", MapControlWidgetMath.miniDashSplitOther(" ") { true })
        assertNull(MapControlWidgetMath.miniDashSplitOther(null) { false })
    }

    @Test fun automationCount() {
        val items = listOf(
            AutomationItem(AutomationId.CLIMATE, on = true, toggleable = true),
            AutomationItem(AutomationId.SUNROOF, on = false, toggleable = true),
            AutomationItem(AutomationId.FACE, on = true, toggleable = false),
        )
        assertEquals(2 to 3, MapControlWidgetMath.automationCount(items))
        assertEquals(0 to 0, MapControlWidgetMath.automationCount(emptyList()))
    }

    @Test fun splitNeedsTwoDifferentApps() {
        assertTrue(MapControlWidgetMath.splitReady(SplitPair("a.maps", "b.music")))
        assertFalse(MapControlWidgetMath.splitReady(SplitPair("a.maps", "a.maps")))
        assertFalse(MapControlWidgetMath.splitReady(SplitPair("a.maps", null)))
        assertFalse(MapControlWidgetMath.splitReady(SplitPair(" ", "b.music")))
    }

    @Test fun clusterPendingShownUntilConfirmedOrExpired() {
        // İstek bekliyor, servis henüz bildirmedi: istenen gösterilir.
        assertTrue(MapControlWidgetMath.clusterShown(read = false, pending = true, pendingAgeMs = 500))
        // Süre doldu: okunan durum.
        assertFalse(MapControlWidgetMath.clusterShown(read = false, pending = true, pendingAgeMs = MapControlWidgetMath.PENDING_MS))
        // Servis onayladı.
        assertTrue(MapControlWidgetMath.clusterShown(read = true, pending = true, pendingAgeMs = 500))
        assertTrue(MapControlWidgetMath.clusterShown(read = true, pending = null, pendingAgeMs = 0))
    }

    @Test fun mapcontrolWidgetsFitSahneAndHaveLargeForm() {
        listOf(WidgetType.WEB_PANEL, WidgetType.EQUALIZER, WidgetType.AUTOMATION, WidgetType.SPLIT_SCREEN, WidgetType.CLUSTER_MIRROR).forEach {
            assertFalse("$it panel", SahneForm.fitsPanel(it)) // büyük panel yalnız medya/yolculuk/tüketim (4 Kasım kararı)
            assertTrue("$it şerit", SahneForm.fitsStrip(it))
            assertTrue("$it büyük biçim", SahneForm.hasLargeForm(it))
            assertEquals(it, WidgetType.fromKey(it.key))
        }
    }

    @Test fun qrMatrixIndexing() {
        val m = QrMatrix(2, booleanArrayOf(true, false, false, true))
        assertTrue(m[0, 0])
        assertFalse(m[1, 0])
        assertTrue(m[1, 1])
    }
}
