package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class PickerGridTest {

    // Tiggo 7: 1920x720 dp, tam ekran seçici 24dp kenar, 72dp başlık + 16dp boşluk.
    private val t7W = 1920 - 48
    private val t7H = 720 - 48 - 72 - 16

    @Test fun tiggo7StripAllFitWithoutScroll() {
        val n = SahneForm.offered(SahneSlotKind.STRIP, quad = false).size
        val f = PickerGrid.fit(n, t7W, t7H)
        assertTrue(f.fits)
        assertTrue(f.cols * f.rows >= n)
        assertTrue(f.cols in 6..8)
        assertTrue(f.tileH >= LauncherTheme.TOUCH_MIN_DP)
        assertTrue(f.tileH <= PickerGrid.MAX_TILE_H_DP)
    }

    @Test fun fourteenItemsBalancedSevenBySeven() {
        val f = PickerGrid.fit(14, t7W, t7H)
        assertEquals(7, f.cols)
        assertEquals(2, f.rows)
        assertEquals(96, f.tileH)
        assertEquals(257, f.tileW)
    }

    @Test fun fifteenItemsTwoRowsOfEight() {
        val f = PickerGrid.fit(15, t7W, t7H)
        assertEquals(8, f.cols)
        assertEquals(2, f.rows)
        assertTrue(f.fits)
    }

    @Test fun tiggo8NarrowWidthStillFits() {
        val f = PickerGrid.fit(14, 1700 - 48, 956 - 48 - 72 - 16)
        assertTrue(f.fits)
        assertEquals(7, f.cols)
        assertEquals(2, f.rows)
        assertTrue(PickerGrid.fit(15, 1700 - 48, 956 - 48 - 72 - 16).fits)
        assertTrue(f.cols * f.tileW + (f.cols - 1) * PickerGrid.GAP_DP <= 1700 - 48)
    }

    @Test fun panelPickerSingleRowCappedWidth() {
        val f = PickerGrid.fit(SahneForm.offered(SahneSlotKind.PANEL, quad = true).size, t7W, t7H)
        assertEquals(1, f.rows)
        assertEquals(PickerGrid.MAX_TILE_W_DP, f.tileW)
        assertTrue(f.fits)
    }

    @Test fun moreColumnsThanCapUsedBeforeGivingUp() {
        // 8 sütunda 5 satır 330dp'ye sığmaz; genişlik 10 sütuna izin verir, 4 satırla sığar.
        val f = PickerGrid.fit(40, 2400, 330)
        assertTrue(f.cols > PickerGrid.MAX_COLS)
        assertTrue(f.fits)
    }

    @Test fun tooManyItemsReportsNoFit() {
        val f = PickerGrid.fit(80, t7W, t7H)
        assertFalse(f.fits)
        assertEquals(LauncherTheme.TOUCH_MIN_DP, f.tileH)
    }

    @Test fun emptyAndTinyInputs() {
        assertEquals(PickerFit(0, 0, 0, 0, true), PickerGrid.fit(0, t7W, t7H))
        val f = PickerGrid.fit(3, 100, 500)
        assertEquals(1, f.cols)
        assertEquals(3, f.rows)
    }
}
