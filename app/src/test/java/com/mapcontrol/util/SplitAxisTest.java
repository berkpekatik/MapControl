package com.mapcontrol.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SplitAxisTest {

    @Test
    public void tiggo7SplitsSideBySide() {
        assertTrue(SplitScreenLauncher.sideBySideFor(1920, 720));
    }

    @Test
    public void tiggo8SplitsTopBottom() {
        assertFalse(SplitScreenLauncher.sideBySideFor(2560, 1440));
    }

    @Test
    public void portraitAndSmallLandscape() {
        assertFalse(SplitScreenLauncher.sideBySideFor(720, 1280));
        assertTrue(SplitScreenLauncher.sideBySideFor(1280, 720));
        assertTrue(SplitScreenLauncher.sideBySideFor(1920, 1080));
        assertTrue(SplitScreenLauncher.sideBySideFor(0, 0));
    }
}
