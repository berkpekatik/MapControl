package com.mapcontrol.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LauncherModeManagerTest {

    private static final String ENTRY = "com.mapcontrol.ui.activity.AppEntryActivity";
    private static final String HOME = "com.mapcontrol.launcher.HomeLauncherActivity";
    private static final String SETTINGS = "com.mapcontrol.ui.activity.MainActivity";

    @Test
    public void launcherTasksAreRecognised() {
        assertTrue(LauncherModeManager.isLauncherTask(ENTRY, HOME)); // araçta görülen: kök giriş, üstte launcher
        assertTrue(LauncherModeManager.isLauncherTask(HOME, HOME));
        assertTrue(LauncherModeManager.isLauncherTask(null, HOME));
    }

    @Test
    public void settingsTaskIsKept() {
        assertFalse(LauncherModeManager.isLauncherTask(SETTINGS, SETTINGS));
        assertFalse(LauncherModeManager.isLauncherTask(null, null));
    }
}
