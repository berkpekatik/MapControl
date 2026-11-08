package com.mapcontrol.launcher

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemNightTest {
    @Test fun dayOnlyWhenSystemSaysDay() {
        assertFalse(LauncherTheme.isNight(Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO))
    }

    @Test fun nightWhenSystemSaysNight() {
        assertTrue(LauncherTheme.isNight(Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES))
    }

    @Test fun unknownCountsAsNight() {
        assertTrue(LauncherTheme.isNight(Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_UNDEFINED))
    }
}
