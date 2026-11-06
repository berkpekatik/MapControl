package com.mapcontrol.trip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.junit.Test;

public class TripBoxUploaderTest {

    @Test
    public void versionsAlwaysIncrease() {
        long a = TripBoxUploader.nextVersion();
        long b = TripBoxUploader.nextVersion();
        long c = TripBoxUploader.nextVersion();
        assertTrue(b > a);
        assertTrue(c > b);
    }

    @Test
    public void payloadIsTheSameShapeAsTheTripsEndpoint() {
        TripRecord t = new TripRecord("t1761800000000", 1761800000000L, 1761801800000L, 100, 112, 12, 80, 71, 900,
                500, 430, 40, 90, 1_500_000L, 41.0, 29.0, 41.1, 29.1, new ArrayList<TripRecord.Point>() {{
            add(new TripRecord.Point(41.0, 29.0, 30));
            add(new TripRecord.Point(41.1, 29.1, 0));
        }});
        String json = new String(TripBoxUploader.payload(t), StandardCharsets.UTF_8);
        assertEquals(PhoneApiJson.trips(java.util.Collections.singletonList(t)), json);
        assertTrue(json.startsWith("{\"trips\":[{\"id\":\"t1761800000000\""));
        assertTrue(json.contains("\"route\":[[41.000000,29.000000,30],[41.100000,29.100000,0]]"));
    }

    @Test
    public void payloadCarriesTheIgnitionOffCounters() {
        TripRecord t = new TripRecord("t1761800000000", 1761800000000L, 1761801800000L, 100, 112, 12, 80, 71, 900,
                500, 430, 40, 90, 1_500_000L, 41.0, 29.0, 41.1, 29.1, new ArrayList<TripRecord.Point>());
        String snapshot = PhoneApiJson.vehicleNow("VIN123", 1761801800500L, 54321, 42, 380);
        String json = new String(TripBoxUploader.payload(t, snapshot), StandardCharsets.UTF_8);
        assertTrue(json.startsWith("{\"trips\":[{\"id\":\"t1761800000000\""));
        assertTrue(json.endsWith(",\"snapshot\":" + snapshot + "}"));
        // sayaç yoksa (araç verisi okunmadı) paket eski biçimle aynıdır
        assertEquals(PhoneApiJson.trips(java.util.Collections.singletonList(t)),
                new String(TripBoxUploader.payload(t, null), StandardCharsets.UTF_8));
    }
}
