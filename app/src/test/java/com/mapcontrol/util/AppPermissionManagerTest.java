package com.mapcontrol.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Saf liste/ad yardımcılarını doğrular (Android çalışma zamanı gerektirmez). */
public class AppPermissionManagerTest {

    private static final String A = "com.a/.SvcA";
    private static final String B = "com.b/.SvcB";

    @Test
    public void shortNameTakesLastSegment() {
        assertEquals("CAMERA", AppPermissionManager.shortName("android.permission.CAMERA"));
        assertEquals("SvcA", AppPermissionManager.shortName("com.a.SvcA"));
        assertEquals("plain", AppPermissionManager.shortName("plain"));
        assertEquals("", AppPermissionManager.shortName(null));
    }

    @Test
    public void listContainsMatchesWholeComponentOnly() {
        assertTrue(AppPermissionManager.listContains(A + ":" + B, B));
        assertFalse(AppPermissionManager.listContains(A, B));
        assertFalse(AppPermissionManager.listContains(null, A));
        assertFalse(AppPermissionManager.listContains("null", A));
        assertFalse(AppPermissionManager.listContains("", A));
    }

    @Test
    public void withEntryAppendsWithoutDuplicating() {
        assertEquals(A, AppPermissionManager.withEntry(null, A));
        assertEquals(A, AppPermissionManager.withEntry("", A));
        assertEquals(A, AppPermissionManager.withEntry("null", A));
        assertEquals(A + ":" + B, AppPermissionManager.withEntry(A, B));
        // Zaten varsa liste aynen kalır.
        assertEquals(A + ":" + B, AppPermissionManager.withEntry(A + ":" + B, B));
    }

    @Test
    public void withoutEntryRemovesAndKeepsOthers() {
        assertEquals(B, AppPermissionManager.withoutEntry(A + ":" + B, A));
        assertEquals(A, AppPermissionManager.withoutEntry(A + ":" + B, B));
        assertEquals("", AppPermissionManager.withoutEntry(A, A));
        assertEquals("", AppPermissionManager.withoutEntry(null, A));
        // Olmayanı çıkarmak listeyi korur.
        assertEquals(A, AppPermissionManager.withoutEntry(A, B));
    }
}
