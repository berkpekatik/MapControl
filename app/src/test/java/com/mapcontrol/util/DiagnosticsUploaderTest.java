package com.mapcontrol.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DiagnosticsUploaderTest {

    @Test
    public void parsesServerId() {
        assertEquals("42", DiagnosticsUploader.parseId("{\"success\":true,\"id\":42}"));
        assertNull(DiagnosticsUploader.parseId("{\"success\":true}"));
        assertNull(DiagnosticsUploader.parseId(null));
    }

    @Test
    public void headerIsAscii() {
        assertEquals("SOP7 ?zel", DiagnosticsUploader.ascii("SOP7 Özel"));
        assertEquals("ab", DiagnosticsUploader.ascii("a\nb").replace("?", ""));
    }
}
