package com.mapcontrol.dvr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnCamLayoutTest {

    @Test fun defaultMatchesPreviousCard() {
        // 1920x720: genişlik min(537.6, 529.9) = 529, yükseklik 330; yandan %8 = 153, dikeyde orta.
        val r = TurnCamLayout().rect(1920, 720, TurnSide.LEFT)
        assertEquals(529, r.w)
        assertEquals(330, r.h)
        assertEquals(153, r.x)
        assertEquals((720 - 330) / 2, r.y)
    }

    @Test fun rightMirrorsLeft() {
        val l = TurnCamLayout(edgePct = 12, posYPct = 30, sizePct = 120)
        val a = l.rect(1920, 1080, TurnSide.LEFT)
        val b = l.rect(1920, 1080, TurnSide.RIGHT)
        assertEquals(a.w, b.w)
        assertEquals(a.y, b.y)
        assertEquals(a.x, 1920 - b.x - b.w)
    }

    @Test fun verticalTopMiddleBottom() {
        val top = TurnCamLayout(posYPct = 0).rect(1920, 1080, TurnSide.LEFT)
        val bottom = TurnCamLayout(posYPct = 100).rect(1920, 1080, TurnSide.LEFT)
        assertEquals(0, top.y)
        assertEquals(1080 - bottom.h, bottom.y)
    }

    @Test fun sizeScalesAndIsCapped() {
        val small = TurnCamLayout(sizePct = 70).rect(1920, 1080, TurnSide.LEFT)
        val big = TurnCamLayout(sizePct = 150).rect(1920, 1080, TurnSide.LEFT)
        assertEquals((537.6f * 0.7f).toInt(), small.w)
        assertTrue(big.w <= (1920 * 0.45f).toInt())
        // Aralık dışı değer kırpılır.
        assertEquals(big, TurnCamLayout(sizePct = 400).rect(1920, 1080, TurnSide.LEFT))
    }

    @Test fun neverLeavesScreen() {
        val r = TurnCamLayout(edgePct = 30, posYPct = 100, sizePct = 150).rect(1920, 720, TurnSide.LEFT)
        assertTrue(r.x >= 0 && r.x + r.w <= 1920)
        assertTrue(r.y >= 0 && r.y + r.h <= 720)
        val q = TurnCamLayout(edgePct = 30, sizePct = 150).rect(1920, 720, TurnSide.RIGHT)
        assertTrue(q.x >= 0 && q.x + q.w <= 1920)
    }
}
