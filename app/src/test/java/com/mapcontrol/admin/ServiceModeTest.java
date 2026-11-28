package com.mapcontrol.admin;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class ServiceModeTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void systemAppsDetected() {
        int sys = android.content.pm.ApplicationInfo.FLAG_SYSTEM;
        int upd = android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        assertTrue(ServiceMode.isSystemApp(sys, "/system/app/Music/Music.apk"));
        assertTrue(ServiceMode.isSystemApp(upd, "/data/app/com.chery.music-1/base.apk"));
        assertTrue(ServiceMode.isSystemApp(0, "/vendor/app/Radio/Radio.apk"));
        assertTrue(ServiceMode.isSystemApp(0, null));
        assertFalse(ServiceMode.isSystemApp(0, "/data/app/com.example-1/base.apk"));
    }

    @Test
    public void imeListParsing() {
        String list = "com.iqqijni.car.keyboard/com.iqqijni.car.imeservice.IMEService:rkr.simplekeyboard.inputmethod/.latin.LatinIME;123;456";
        assertTrue(ServiceMode.imeListContains(list, "rkr.simplekeyboard.inputmethod/.latin.LatinIME"));
        assertTrue(ServiceMode.imeListContains(list, "com.iqqijni.car.keyboard/com.iqqijni.car.imeservice.IMEService"));
        assertFalse(ServiceMode.imeListContains(list, "rkr.simplekeyboard.inputmethod/.Other"));
        assertFalse(ServiceMode.imeListContains(null, "a/b"));
        assertEquals("rkr.simplekeyboard.inputmethod", ServiceMode.imePackage("rkr.simplekeyboard.inputmethod/.latin.LatinIME"));
        assertNull(ServiceMode.imePackage("bozuk"));
    }

    @Test
    public void hashDependsOnSaltAndPassword() {
        byte[] salt = {1, 2, 3, 4};
        String h = ServiceMode.hash(salt, "1234");
        assertEquals(64, h.length());
        assertEquals(h, ServiceMode.hash(salt, "1234"));
        assertNotEquals(h, ServiceMode.hash(salt, "12345"));
        assertNotEquals(h, ServiceMode.hash(new byte[]{9, 9, 9, 9}, "1234"));
    }

    @Test
    public void hexRoundTrip() {
        byte[] bytes = {0, 15, 16, (byte) 0xff, (byte) 0x80};
        assertArrayEquals(bytes, ServiceMode.fromHex(ServiceMode.toHex(bytes)));
        assertNull(ServiceMode.fromHex("abc"));
        assertNull(ServiceMode.fromHex("zz"));
        assertNull(ServiceMode.fromHex(null));
    }

    @Test
    public void unlockFileFoundCaseInsensitiveAndWithDoubleExtension() throws Exception {
        File root = tmp.newFolder("usb1");
        assertNull(ServiceMode.findUnlockFile(root));
        File upper = new File(root, ServiceMode.UNLOCK_FILE.toUpperCase(java.util.Locale.ROOT));
        assertTrue(upper.createNewFile());
        assertEquals(upper, ServiceMode.findUnlockFile(root));

        File root2 = tmp.newFolder("usb2");
        File doubled = new File(root2, ServiceMode.UNLOCK_FILE + ".txt");
        assertTrue(doubled.createNewFile());
        assertEquals(doubled, ServiceMode.findUnlockFile(root2));

        File root3 = tmp.newFolder("usb3");
        assertTrue(new File(root3, "other.txt").createNewFile());
        assertNull(ServiceMode.findUnlockFile(root3));
    }
}
