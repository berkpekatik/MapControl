package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherScaleTest {

    @Test fun tiggo7ScreenIsLeftAlone() {
        assertNull(LauncherScale.targetDensityDpi(1920, 720, 160))
    }

    @Test fun bigScreenAtComfortableDensityIsLeftAlone() {
        // 2560 px @240 dpi = 1707 dp: emülatörde iyi görünen hâl.
        assertNull(LauncherScale.targetDensityDpi(2560, 1440, 240))
    }

    @Test fun denseBigScreenIsWidenedToMinimum() {
        // 2560 px @320 dpi = 1280 dp: yazılar kesiliyordu; 1700 dp'ye getirilir.
        assertEquals(241, LauncherScale.targetDensityDpi(2560, 1440, 320))
    }

    @Test fun sparseBigScreenIsNarrowedToDesignWidth() {
        // 2560 px @160 dpi = 2560 dp: kartlar boş kalıyordu; 1920 dp'ye getirilir.
        assertEquals(213, LauncherScale.targetDensityDpi(2560, 1440, 160))
    }

    @Test fun portraitReportUsesLongSide() {
        assertNull(LauncherScale.targetDensityDpi(720, 1920, 160))
    }
}
