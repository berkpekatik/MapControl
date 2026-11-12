package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SahnePortraitTest {

    // 1440×1920 @320 dpi, LauncherScale sonrası ~1273×1700 dp; başlık, alt çubuk ve boşluklar düşünce orta alan ~1225×1430 dp
    // (1 dp = 1 px alınır).
    private val g = SahnePortrait.geometry(w = 1225, h = 1430, stripH = 92, gapH = 16, gapV = 16, stageMinPx = 320)

    @Test fun panelsAreRoomierThanTiggo7Design() {
        assertTrue("panel genişliği ${g.panelW}", g.panelW >= 440)
        assertTrue("panel yüksekliği ${g.panelH}", g.panelH >= 378)
    }

    @Test fun columnsAndRowsDoNotOverlap() {
        assertEquals(0, g.panelLeft(0, 1225))
        assertEquals(0, g.panelLeft(2, 1225))
        assertEquals(1225 - g.panelW, g.panelLeft(1, 1225))
        assertTrue(g.panelLeft(1, 1225) - g.panelW >= 16)
        assertEquals(g.panelTop(0), g.panelTop(1))
        assertEquals(g.stageH + 16, g.panelTop(0))
        assertEquals(g.panelTop(0) + g.panelH + 16, g.panelTop(2))
    }

    @Test fun lowerRowStopsAboveStrip() {
        val bottom = g.panelTop(3) + g.panelH
        assertTrue("alt satır $bottom, şerit ${1430 - 92}", bottom <= 1430 - 92 - 16)
    }

    @Test fun stageKeepsMinimumOnShortScreen() {
        val small = SahnePortrait.geometry(w = 700, h = 900, stripH = 92, gapH = 16, gapV = 16, stageMinPx = 320)
        assertEquals(320, small.stageH)
        assertTrue(small.panelH >= 0)
    }

    @Test fun carDropsToTrueCentreWhenNoStripShows() {
        // 540 px sahne, araç şerit payı yüzünden yukarıda (üst 40, boy 300): ortalanınca üstü 120'ye iner.
        assertEquals(80f, SahnePortrait.centerDrop(stageH = 540f, boxTop = 40f, boxH = 300f, bottomGap = 8f), 0f)
        // Zaten ortada ya da ortanın altında: yukarı çekilmez.
        assertEquals(0f, SahnePortrait.centerDrop(stageH = 540f, boxTop = 120f, boxH = 300f, bottomGap = 8f), 0f)
        assertEquals(0f, SahnePortrait.centerDrop(stageH = 540f, boxTop = 200f, boxH = 300f, bottomGap = 8f), 0f)
        // Araç sahneden uzunsa ya da altta yer yoksa taşmaz.
        assertEquals(0f, SahnePortrait.centerDrop(stageH = 300f, boxTop = 0f, boxH = 300f, bottomGap = 8f), 0f)
        val drop = SahnePortrait.centerDrop(stageH = 320f, boxTop = 0f, boxH = 310f, bottomGap = 8f)
        assertEquals(2f, drop, 0f)
        assertTrue(0f + 310f + drop <= 320f - 8f)
    }
}
