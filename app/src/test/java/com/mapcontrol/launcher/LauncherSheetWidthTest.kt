package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSheetWidthTest {

    @Test fun fitsOnWideScreenUnchanged() {
        // Tiggo 7: 1920 px ekranda 1440 px'lik sayfa aynen kalır.
        assertEquals(1440, sheetWidthPx(1440, 1920, 24))
    }

    @Test fun narrowsOnPortraitScreen() {
        // Dikey 1440 px ekranda 1440 px'lik araç detayı iki yanda 24 px payla daralır.
        assertEquals(1392, sheetWidthPx(1440, 1440, 24))
    }

    @Test fun unknownScreenKeepsWanted() {
        assertEquals(640, sheetWidthPx(640, 0, 24))
    }
}
