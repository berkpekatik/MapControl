package com.mapcontrol.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SplitSlotTest {

    @Test
    public void packageIsNotComponent() {
        assertFalse(SplitSlot.isComponent("com.foo"));
        assertFalse(SplitSlot.isComponent(null));
        assertFalse(SplitSlot.isComponent("com.foo/"));
        assertFalse(SplitSlot.isComponent("/x"));
        assertEquals("com.foo", SplitSlot.packageOf("com.foo"));
        assertNull(SplitSlot.classOf("com.foo"));
    }

    @Test
    public void shortFormExpandsClass() {
        assertTrue(SplitSlot.isComponent("com.a/.ui.Main"));
        assertEquals("com.a", SplitSlot.packageOf("com.a/.ui.Main"));
        assertEquals("com.a.ui.Main", SplitSlot.classOf("com.a/.ui.Main"));
        assertEquals("org.b.C", SplitSlot.classOf("com.a/org.b.C"));
    }

    @Test
    public void componentRoundTripsShortForm() {
        assertEquals("com.a/.ui.Main", SplitSlot.component("com.a", "com.a.ui.Main"));
        assertEquals("com.a/org.b.C", SplitSlot.component("com.a", "org.b.C"));
        assertEquals("com.a/com.ab.C", SplitSlot.component("com.a", "com.ab.C"));
    }

    @Test
    public void miniDashForBothPackages() {
        for (String self : new String[]{"com.mapcontrol", "com.mapcontrol.dev"}) {
            String slot = SplitSlot.miniDash(self);
            assertTrue(SplitSlot.isComponent(slot));
            assertTrue(SplitSlot.isMiniDash(slot, self));
            assertTrue(SplitSlot.isAllowed(slot, self));
            assertEquals(SplitSlot.MINI_DASH_CLASS, SplitSlot.classOf(slot));
        }
        assertEquals("com.mapcontrol/.launcher.MiniDashActivity", SplitSlot.miniDash("com.mapcontrol"));
        assertEquals("com.mapcontrol.dev/com.mapcontrol.launcher.MiniDashActivity",
                SplitSlot.miniDash("com.mapcontrol.dev"));
    }

    @Test
    public void ownPackageAllowedOnlyAsMiniDash() {
        String self = "com.mapcontrol";
        assertFalse(SplitSlot.isAllowed(self, self));
        assertFalse(SplitSlot.isAllowed("com.mapcontrol/.ui.activity.MainActivity", self));
        assertFalse(SplitSlot.isAllowed("com.other/.launcher.MiniDashActivity", self));
        assertFalse(SplitSlot.isMiniDash(SplitSlot.miniDash("com.mapcontrol.dev"), self));
        assertTrue(SplitSlot.isAllowed("com.foo", self));
        assertFalse(SplitSlot.isAllowed("", self));
        assertFalse(SplitSlot.isAllowed(null, self));
    }

    @Test
    public void mapPickerIsAnOwnAllowedSlot() {
        String self = "com.mapcontrol";
        String picker = SplitSlot.mapPicker(self);
        assertEquals("com.mapcontrol/.ui.activity.DriveMapPickerActivity", picker);
        assertTrue(SplitSlot.isMapPicker(picker, self));
        assertTrue(SplitSlot.isOwn(picker, self));
        assertTrue(SplitSlot.isOwn(SplitSlot.miniDash(self), self));
        assertTrue(SplitSlot.isAllowed(picker, self));
        assertFalse(SplitSlot.isMiniDash(picker, self));
        assertFalse(SplitSlot.isMapPicker(SplitSlot.mapPicker("com.other"), self));
        assertFalse(SplitSlot.isOwn("ru.yandex.yandexmaps", self));
    }
}
