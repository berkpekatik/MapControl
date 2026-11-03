package com.mapcontrol.trip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public class PhoneApiJsonTest {

    @Test
    public void tripJsonMatchesPhoneContract() {
        TripRecord t = new TripRecord("t1", 1000, 61_000, 14_000, 14_038, 38, 70, 62, 2_900, 600, 520,
                52, 98, 38 * 60_000L, 40.76, 29.93, 40.9, 29.4,
                Arrays.asList(new TripRecord.Point(40.76, 29.93, 12), new TripRecord.Point(40.77, 29.94, 55)));
        assertEquals("{\"trips\":[{\"id\":\"t1\",\"startedAt\":1000,\"endedAt\":61000,\"distanceKm\":38,"
                + "\"fuelUsedMl\":2900,\"avgSpeedKmh\":52,\"maxSpeedKmh\":98,\"movingMinutes\":38,"
                + "\"route\":[[40.760000,29.930000,12],[40.770000,29.940000,55]]}]}",
                PhoneApiJson.trips(Collections.singletonList(t)));
    }

    @Test
    public void emptyTripListIsValid() {
        assertEquals("{\"trips\":[]}", PhoneApiJson.trips(Collections.emptyList()));
    }

    @Test
    public void numbersUseDotRegardlessOfLocale() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals("40.760000", PhoneApiJson.number(40.76));
        } finally {
            Locale.setDefault(old);
        }
    }

    @Test
    public void vehicleNowJsonMatchesPhoneContract() {
        String json = PhoneApiJson.vehicleNow("LVV\"X", 5, 14_870, 62, 520);
        assertEquals("{\"vin\":\"LVV\\\"X\",\"updatedAt\":5,\"odometerKm\":14870,\"fuelPercent\":62,"
                + "\"rangeKm\":520}", json);
    }

    @Test
    public void quoteEscapesControlCharacters() {
        assertEquals("\"a\\n\\u0001\"", PhoneApiJson.quote("a\n\u0001"));
    }

    @Test
    public void tokenComparisonAndHeaderParsing() {
        String header = "GET /trips HTTP/1.1\r\nHost: x\r\nx-mapcontrol-token:  abc123 \r\n\r\n";
        assertEquals("abc123", PhoneAccess.headerValue(header, PhoneAccess.HEADER));
        assertTrue(PhoneAccess.matches("abc123", "abc123"));
        assertFalse(PhoneAccess.matches("abc123", "abc124"));
        assertFalse(PhoneAccess.matches("abc123", null));
        assertFalse(PhoneAccess.matches(null, "abc123"));
        assertTrue(PhoneAccess.headerValue("GET / HTTP/1.1\r\n\r\n", PhoneAccess.HEADER) == null);
        assertTrue(PhoneAccess.isWellFormed("0123456789abcdef0123456789abcdef"));
        assertFalse(PhoneAccess.isWellFormed("XYZ"));
    }
}
