package com.mapcontrol.trip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PhonePrefsTest {

    @Test
    public void reportsAllSixteenKeysInStableOrder() {
        assertEquals(16, PhonePrefs.DEFAULTS.size());
        String json = PhonePrefs.toJson(PhonePrefs.DEFAULTS);
        assertTrue(json.startsWith("{\"prefs\":{\"disclaimerAccepted\":false,\"appManagementDisclaimerAccepted\":false,"));
        assertTrue(json.endsWith("\"launcherModeEnabled\":false}}"));
    }

    @Test
    public void writableKeysAreSubsetOfKnownKeys() {
        assertTrue(PhonePrefs.DEFAULTS.keySet().containsAll(PhonePrefs.WRITABLE));
    }

    @Test
    public void consentPositionAndOverlayFlagsAreReadOnly() {
        for (String key : new String[]{"disclaimerAccepted", "appManagementDisclaimerAccepted",
                "floatingProjectionBarPosSaved", "floatingBackPosSaved", "floatingQuickBarPosSaved",
                "floatingBackButtonEnabled", "floatingQuickActionsEnabled", "floatingProjectionControlsEnabled",
                "launcherModeEnabled"}) {
            assertFalse(key, PhonePrefs.WRITABLE.contains(key));
            Map<String, Boolean> m = new HashMap<>();
            m.put(key, true);
            assertNull(key, PhonePrefs.acceptable(m));
        }
    }

    @Test
    public void acceptsWritableKeysAndRejectsAnyUnknownMixedIn() {
        Map<String, Boolean> ok = new HashMap<>();
        ok.put("bootAutoStart", false);
        ok.put("autoCloseOnPowerOff", true);
        assertNotNull(PhonePrefs.acceptable(ok));

        Map<String, Boolean> mixed = new HashMap<>(ok);
        mixed.put("carToken", true);
        assertNull(PhonePrefs.acceptable(mixed));
        assertNull(PhonePrefs.acceptable(Collections.emptyMap()));
        assertNull(PhonePrefs.acceptable(null));
    }
}
