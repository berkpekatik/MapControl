package com.mapcontrol.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CloudTokenTest {
    private static final long NOW = 1_761_800_000_000L;
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJ2aW4iOiJYIn0.c2lnbmF0dXJl";
    private static final String VIN = "LVTDB21B6RD123456";

    @Test
    public void acceptsAFreshTokenForThisCar() {
        assertNull(CloudToken.reject(JWT, NOW + 30 * DAY, VIN, VIN, NOW));
        assertNull(CloudToken.reject(JWT, NOW + 30 * DAY, VIN.toLowerCase(), VIN, NOW));
        // Araç şasesini henüz okuyamadıysa token'daki şase kabul edilir.
        assertNull(CloudToken.reject(JWT, NOW + 30 * DAY, VIN, "", NOW));
    }

    @Test
    public void rejectsWhatCannotBeUsed() {
        assertEquals("token_invalid", CloudToken.reject("abc", NOW + DAY, VIN, VIN, NOW));
        assertEquals("token_invalid", CloudToken.reject(null, NOW + DAY, VIN, VIN, NOW));
        assertEquals("token_invalid", CloudToken.reject("a.b.c.d.eeeeeeeeeeeeeeeeeeeeeeeeeeee", NOW + DAY, VIN, VIN, NOW));
        assertEquals("token_expired", CloudToken.reject(JWT, NOW + 30_000, VIN, VIN, NOW));
        assertEquals("vin_required", CloudToken.reject(JWT, NOW + DAY, " ", VIN, NOW));
        assertEquals("vin_mismatch", CloudToken.reject(JWT, NOW + DAY, "LVTDB21B6RD999999", VIN, NOW));
    }
}
