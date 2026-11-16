package com.mapcontrol.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LogBufferTrimTest {

    @Test
    public void staysUnderLimitAndCutsAtLineStart() {
        StringBuilder b = new StringBuilder();
        int n = 0;
        while (b.length() <= MainActivity.LOG_BUFFER_MAX_CHARS + 100) {
            b.append("[12:00:00] satir ").append(n++).append('\n');
        }
        String last = "[12:00:00] satir " + (n - 1) + "\n";
        MainActivity.trimLogBuffer(b);
        assertTrue(b.length() <= MainActivity.LOG_BUFFER_KEEP_CHARS);
        assertTrue(b.toString().startsWith("[12:00:00] satir "));
        assertTrue(b.toString().endsWith(last));
    }

    @Test
    public void smallBufferUntouched() {
        StringBuilder b = new StringBuilder("a\nb\n");
        MainActivity.trimLogBuffer(b);
        assertEquals("a\nb\n", b.toString());
    }
}
