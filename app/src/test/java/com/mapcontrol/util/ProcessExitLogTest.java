package com.mapcontrol.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.ApplicationExitInfo;

import org.junit.Test;

public class ProcessExitLogTest {

    @Test
    public void systemDeathsAreReported() {
        assertTrue(ProcessExitLog.shouldReport(ApplicationExitInfo.REASON_ANR));
        assertTrue(ProcessExitLog.shouldReport(ApplicationExitInfo.REASON_CRASH_NATIVE));
        assertTrue(ProcessExitLog.shouldReport(ApplicationExitInfo.REASON_LOW_MEMORY));
        assertTrue(ProcessExitLog.shouldReport(ApplicationExitInfo.REASON_SIGNALED));
        assertTrue(ProcessExitLog.shouldReport(ApplicationExitInfo.REASON_UNKNOWN));
    }

    @Test
    public void expectedExitsAndJavaCrashesAreNotReported() {
        assertFalse(ProcessExitLog.shouldReport(ApplicationExitInfo.REASON_CRASH)); // Sentry zaten raporlar
        assertFalse(ProcessExitLog.shouldReport(ApplicationExitInfo.REASON_USER_REQUESTED));
        assertFalse(ProcessExitLog.shouldReport(ApplicationExitInfo.REASON_EXIT_SELF));
        assertFalse(ProcessExitLog.shouldReport(ApplicationExitInfo.REASON_PACKAGE_UPDATED));
    }

    @Test
    public void reasonNamesAreReadable() {
        assertEquals("ANR", ProcessExitLog.reasonName(ApplicationExitInfo.REASON_ANR));
        assertEquals("CRASH_NATIVE", ProcessExitLog.reasonName(ApplicationExitInfo.REASON_CRASH_NATIVE));
        assertEquals("UNKNOWN_99", ProcessExitLog.reasonName(99));
    }

    @Test
    public void restartIsUnexplainedOnlyInSameBootAndVersion() {
        assertTrue(ProcessExitLog.isUnexplainedRestart("boot-5", "boot-5", 10, 10));
        assertFalse(ProcessExitLog.isUnexplainedRestart("boot-4", "boot-5", 10, 10)); // araç yeniden açıldı
        assertFalse(ProcessExitLog.isUnexplainedRestart("boot-5", "boot-5", 9, 10));  // güncelleme
        assertFalse(ProcessExitLog.isUnexplainedRestart(null, "boot-5", -1, 10));     // ilk çalıştırma
    }
}
