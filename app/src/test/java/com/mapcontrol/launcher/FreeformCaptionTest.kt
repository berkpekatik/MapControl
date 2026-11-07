package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeformCaptionTest {
    @Test fun hidesWhenWindowSitsRightUnderStatusBar() {
        // Sistem bölme sınırlarını kırptı: pencere 70'te, başlık ekranda → gizle.
        assertTrue(FreeformCaption.shouldHide(windowTop = 70, usableTop = 70))
        assertTrue(FreeformCaption.shouldHide(windowTop = 71, usableTop = 70))
    }

    @Test fun keepsWhenCaptionAlreadyTuckedUnderStatusBar() {
        // Bölme başlığı durum çubuğunun altına itti (28): gizlersek içerik durum çubuğunun altına kayardı.
        assertFalse(FreeformCaption.shouldHide(windowTop = 28, usableTop = 70))
    }

    @Test fun keepsForManuallyPlacedWindowsOrUnknownStatusBar() {
        assertFalse(FreeformCaption.shouldHide(windowTop = 200, usableTop = 70))
        assertFalse(FreeformCaption.shouldHide(windowTop = 0, usableTop = -1))
    }

    @Test fun overlapIsWhatTheVisibleCaptionCovers() {
        // Emülatör: durum çubuğu 24 px, başlık 42 px; içerik pencere üstünden başlıyorsa 42 px örtülür.
        assertEquals(42, FreeformCaption.overlap(captionShown = true, captionBottom = 66, contentTop = 24))
        // İçerik başlığın altından başlıyorsa (ya da başlık gizliyse) pay yok.
        assertEquals(0, FreeformCaption.overlap(captionShown = true, captionBottom = 66, contentTop = 66))
        assertEquals(0, FreeformCaption.overlap(captionShown = true, captionBottom = 66, contentTop = 90))
        assertEquals(0, FreeformCaption.overlap(captionShown = false, captionBottom = 66, contentTop = 24))
    }

    @Test fun topEdgeFallsBackToStatusBarWhenFreeformMetricsReportZero() {
        // Öykünücü, 7 Kasım: serbest pencerede en büyük pencere ölçüsünün üst inset'i 0, durum çubuğu 24 px.
        assertEquals(24, FreeformCaption.topEdge(metricsTop = 0, statusBarPx = 24))
        assertTrue(FreeformCaption.shouldHide(windowTop = 24, usableTop = FreeformCaption.topEdge(0, 24)))
        assertEquals(70, FreeformCaption.topEdge(metricsTop = 70, statusBarPx = 24))
        assertEquals(-1, FreeformCaption.topEdge(metricsTop = 0, statusBarPx = -1))
    }
}
