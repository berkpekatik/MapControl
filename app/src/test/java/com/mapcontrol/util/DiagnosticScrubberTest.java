package com.mapcontrol.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DiagnosticScrubberTest {

    private static String s(String in) {
        return DiagnosticScrubber.scrub(in);
    }

    @Test
    public void masksPhoneNumbers() {
        assertEquals("call from [tel...67]", s("call from +905321234567"));
        assertEquals("n=[tel...67] ok", s("n=05321234567 ok"));
        assertEquals("[tel...67]", s("532 123 45 67"));
        assertEquals("x [tel...89] y", s("x +44 20 7946 0789 y"));
        assertEquals("[tel...45]", s("004917012345"));
    }

    @Test
    public void keepsLogcatHeaderAndTimestamps() {
        String line = "11-12 18:39:45.123  1234  5678 I PhoneModel: arama: NONE -> INCOMING";
        assertEquals(line, s(line));
        assertEquals("timestamp=1793807958712", s("timestamp=1793807958712"));
        assertEquals("pss=265332 uid=1000", s("pss=265332 uid=1000"));
        assertEquals("v1.5.1a+25", s("v1.5.1a+25"));
    }

    @Test
    public void masksMacKeepingLastByte() {
        assertEquals("dev XX:XX:XX:XX:XX:EF connected", s("dev aa:bb:cc:dd:ee:ef connected"));
        assertEquals("XX:XX:XX:XX:XX:05", s("00-11-22-33-44-05"));
    }

    @Test
    public void masksEmailCoordinatesAndSecrets() {
        assertEquals("user [e-posta] in", s("user a.b@example.com in"));
        assertEquals("at [konum] now", s("at 41.008240, 28.978359 now"));
        assertEquals("lat=[konum] lon=[konum]", s("lat=41.0082 lon=28.9783"));
        assertEquals("{\"latitude\":[konum]}", s("{\"latitude\":41.0082}"));
        assertEquals("Authorization: Bearer [gizli]", s("Authorization: Bearer eyJhbGciOi.abc.def"));
        assertEquals("token=[gizli]&x=1", s("token=abc123&x=1"));
    }

    @Test
    public void leavesOrdinaryNumbersAlone() {
        String line = "speed=12.5 rpm=2100 temp=36.6 volume=0.75 size=1026915592";
        assertEquals(line, s(line));
    }

    @Test
    public void deviceNamesOnlyInBtDump() {
        String bt = "  Name: Berk iPhone";
        assertEquals(bt, s(bt));
        String masked = DiagnosticScrubber.scrubDeviceNames(bt);
        assertFalse(masked.contains("Berk"));
        assertTrue(masked.contains("[ad]"));
    }
}
