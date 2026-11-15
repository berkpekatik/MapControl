package com.mapcontrol.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.StringWriter;

public class DiagnosticsSinkTest {

    @Test
    public void sectionStopsAtItsBudgetAndMasks() throws Exception {
        StringWriter w = new StringWriter();
        DiagnosticsCollector.Sink total = new DiagnosticsCollector.Sink(w, 1000, false);
        DiagnosticsCollector.Sink sec = total.section(30, false);
        assertTrue(sec.line("call +905321234567"));
        assertFalse(sec.line("this line does not fit in the budget"));
        assertTrue(w.toString().contains("[tel...67]"));
        assertFalse(w.toString().contains("5321234567"));
        assertTrue(w.toString().contains("sinir doldu"));
        // Ana bütçe hâlâ yazılabilir.
        assertTrue(total.section(100, false).line("next section"));
    }

    @Test
    public void parentBudgetLimitsSections() throws Exception {
        StringWriter w = new StringWriter();
        DiagnosticsCollector.Sink total = new DiagnosticsCollector.Sink(w, 20, false);
        assertTrue(total.section(1000, false).line("0123456789"));
        assertFalse(total.section(1000, false).line("0123456789abcdef"));
    }
}
