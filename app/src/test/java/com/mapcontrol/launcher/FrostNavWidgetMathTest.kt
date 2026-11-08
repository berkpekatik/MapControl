package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrostNavWidgetMathTest {

    @Test fun webUrlMatchesFrostNavFormat() {
        assertEquals("http://192.168.43.1:7462", FrostNavWidgetMath.webUrl("192.168.43.1"))
        assertEquals("http://10.0.2.16:8080", FrostNavWidgetMath.webUrl(" 10.0.2.16 ", 8080))
        assertNull(FrostNavWidgetMath.webUrl(null))
        assertNull(FrostNavWidgetMath.webUrl(""))
        assertNull(FrostNavWidgetMath.webUrl("1.2.3.4", 0))
    }

    @Test fun loopbackIsNotReachableFromPhone() {
        assertTrue(FrostNavWidgetMath.isLoopback("127.0.0.1"))
        assertTrue(FrostNavWidgetMath.isLoopback(null))
        assertFalse(FrostNavWidgetMath.isLoopback("192.168.43.1"))
    }

    @Test fun automationCount() {
        val items = listOf(
            AutomationItem(AutomationId.CLIMATE, on = true, toggleable = true),
            AutomationItem(AutomationId.SUNROOF, on = false, toggleable = true),
            AutomationItem(AutomationId.FACE, on = true, toggleable = false),
        )
        assertEquals(2 to 3, FrostNavWidgetMath.automationCount(items))
        assertEquals(0 to 0, FrostNavWidgetMath.automationCount(emptyList()))
    }

    @Test fun clusterPendingShownUntilConfirmedOrExpired() {
        // İstek bekliyor, servis henüz bildirmedi: istenen gösterilir.
        assertTrue(FrostNavWidgetMath.clusterShown(read = false, pending = true, pendingAgeMs = 500))
        // Süre doldu: okunan durum.
        assertFalse(FrostNavWidgetMath.clusterShown(read = false, pending = true, pendingAgeMs = FrostNavWidgetMath.PENDING_MS))
        // Servis onayladı.
        assertTrue(FrostNavWidgetMath.clusterShown(read = true, pending = true, pendingAgeMs = 500))
        assertTrue(FrostNavWidgetMath.clusterShown(read = true, pending = null, pendingAgeMs = 0))
    }

    @Test fun frostNavWidgetsFitSahneAndHaveLargeForm() {
        listOf(WidgetType.WEB_PANEL, WidgetType.EQUALIZER, WidgetType.AUTOMATION, WidgetType.CLUSTER_MIRROR).forEach {
            assertFalse("$it panel", SahneForm.fitsPanel(it)) // büyük panel yalnız yolculuk/tüketim
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
