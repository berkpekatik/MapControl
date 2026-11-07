package com.mapcontrol.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NetworkWifiHelperTest {

    @Test
    public void unquotesSavedSsid() {
        assertEquals("Ev Ağı", NetworkWifiHelper.unquoteSsid("\"Ev Ağı\""));
    }

    @Test
    public void keepsHexOrUnquotedSsid() {
        assertEquals("a1b2c3", NetworkWifiHelper.unquoteSsid("a1b2c3"));
        assertEquals("\"", NetworkWifiHelper.unquoteSsid("\""));
    }

    @Test
    public void nullSsidIsEmpty() {
        assertEquals("", NetworkWifiHelper.unquoteSsid(null));
    }
}
