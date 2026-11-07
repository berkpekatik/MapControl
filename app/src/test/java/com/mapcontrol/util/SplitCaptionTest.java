package com.mapcontrol.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SplitCaptionTest {

    @Test
    public void topHalvesTuckCaptionUnderStatusBar() {
        // Tiggo 7: durum çubuğu 0–70, başlık 42 px → pencere 28'den başlar, içerik 70'ten.
        assertEquals(28, SplitScreenLauncher.tuckedTop(70, 70, 42));
    }

    @Test
    public void lowerHalfOfTopBottomSplitIsUnchanged() {
        assertEquals(400, SplitScreenLauncher.tuckedTop(400, 70, 42));
    }

    @Test
    public void noRoomOrNoCaptionLeavesTopAlone() {
        assertEquals(20, SplitScreenLauncher.tuckedTop(20, 20, 42));
        assertEquals(0, SplitScreenLauncher.tuckedTop(0, 0, 42));
        assertEquals(70, SplitScreenLauncher.tuckedTop(70, 70, 0));
    }

    @Test
    public void exactFitIsAllowed() {
        assertEquals(0, SplitScreenLauncher.tuckedTop(42, 42, 42));
    }
}
