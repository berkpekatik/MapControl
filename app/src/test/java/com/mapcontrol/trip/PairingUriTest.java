package com.mapcontrol.trip;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PairingUriTest {

    private static final String KEY = "0123456789ABCDEF0123456789abcdef";

    @Test
    public void includesVinWhenKnown() {
        assertEquals(
                "frostnav://pair?host=192.168.43.1&port=7462&key=0123456789abcdef0123456789abcdef&vin=LVVDB11B0NA004821",
                PairingUri.build("192.168.43.1", 7462, KEY, "LVVDB11B0NA004821"));
    }

    @Test
    public void omitsVinWhenUnknown() {
        assertEquals("frostnav://pair?host=10.0.0.5&port=7462&key=0123456789abcdef0123456789abcdef",
                PairingUri.build("10.0.0.5", 7462, KEY, ""));
        assertEquals("frostnav://pair?host=10.0.0.5&port=7462&key=0123456789abcdef0123456789abcdef",
                PairingUri.build("10.0.0.5", 7462, KEY, null));
    }

    @Test
    public void addsGpsPortAndCarId() {
        assertEquals(
                "frostnav://pair?host=192.168.43.1&port=7462&key=0123456789abcdef0123456789abcdef&vin=LVVDB11B0NA004821"
                        + "&gps=45921&cid=a1b2c3d4e5f60718",
                PairingUri.build("192.168.43.1", 7462, KEY, "LVVDB11B0NA004821", 45921, "a1b2c3d4e5f60718"));
    }

    @Test
    public void omitsGpsPortAndCarIdWhenAbsent() {
        assertEquals("frostnav://pair?host=10.0.0.5&port=7462&key=0123456789abcdef0123456789abcdef",
                PairingUri.build("10.0.0.5", 7462, KEY, "", 0, ""));
    }

    @Test
    public void splitsServerUrl() {
        assertArrayEquals(new String[]{"192.168.43.1", "7462"}, PairingUri.hostAndPort("http://192.168.43.1:7462"));
        assertNull(PairingUri.hostAndPort("not a url"));
    }
}
