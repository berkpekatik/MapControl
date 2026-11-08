package com.mapcontrol.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** {@link SplitScreenLauncher#fullScreenResult}: harita tam ekran yapıldı mı (Mini gösterge ancak o zaman kapanır). */
public class SplitFullScreenTest {

    @Test
    public void movedTaskIsFullScreen() {
        assertEquals(SplitScreenLauncher.Result.STARTED, SplitScreenLauncher.fullScreenResult(1071, true, true));
        assertEquals(SplitScreenLauncher.Result.STARTED, SplitScreenLauncher.fullScreenResult(1071, true, false));
    }

    @Test
    public void noTaskInLogMeansTheLaunchOpenedANewFullScreenTask() {
        assertEquals(SplitScreenLauncher.Result.STARTED, SplitScreenLauncher.fullScreenResult(-1, false, true));
        assertEquals(SplitScreenLauncher.Result.LAUNCH_FAILED, SplitScreenLauncher.fullScreenResult(-1, false, false));
    }

    @Test
    public void unreadableLogKeepsTheCallerOpen() {
        // READ_LOGS yok: intent açık serbest pencere görevini taşımaz, harita yarıda kalmış olabilir.
        assertEquals(SplitScreenLauncher.Result.LAUNCH_FAILED, SplitScreenLauncher.fullScreenResult(-2, false, true));
    }

    @Test
    public void failedMoveIsNotFullScreen() {
        assertEquals(SplitScreenLauncher.Result.LAUNCH_FAILED, SplitScreenLauncher.fullScreenResult(1071, false, true));
    }
}
