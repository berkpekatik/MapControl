package com.mapcontrol.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class DropBoxWatchTest {

    @Test
    public void parsesSystemAppCrash() {
        DropBoxWatch.Head h = DropBoxWatch.parse("Process: com.desaysv.ivi.vds.bt\nPID: 1234\nFlags: 0x1\n\n"
                + "java.lang.NullPointerException: Attempt to invoke on null caller +905321234567\n\tat x.y(Z.java:1)\n");
        assertEquals("com.desaysv.ivi.vds.bt", h.process);
        assertEquals("java.lang.NullPointerException: Attempt to invoke on null caller [tel...67]", h.error);
    }

    @Test
    public void parsesWatchdogAndTombstone() {
        assertEquals("Subject: Blocked in handler on main thread",
                DropBoxWatch.parse("Process: system_server\nSubject: Blocked in handler on main thread\n").error);
        DropBoxWatch.Head t = DropBoxWatch.parse("Cmd line: /vendor/bin/camera\nsignal 11 (SIGSEGV), code 1\n");
        assertEquals("/vendor/bin/camera", t.process);
        assertEquals("signal 11 (SIGSEGV), code 1", t.error);
    }

    @Test
    public void emptyTextIsSafe() {
        assertEquals("", DropBoxWatch.parse(null).process);
        assertEquals("", DropBoxWatch.parse("").error);
    }

    @Test
    public void ownCrashesAreLeftToSentry() {
        assertFalse(DropBoxWatch.TAGS.contains("data_app_crash"));
    }
}
