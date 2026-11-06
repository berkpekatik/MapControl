package com.mapcontrol.trip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TripTrackerTest {

    private final List<TripRecord> closed = new ArrayList<>();
    private TripTracker tracker;

    @Before
    public void setUp() {
        closed.clear();
        tracker = new TripTracker(closed::add);
    }

    private static TripReading reading(int odo, int fuelMl, int speed) {
        return new TripReading(odo, fuelMl, 62, 520, speed, Double.NaN, Double.NaN);
    }

    private static TripReading at(int odo, int fuelMl, int speed, double lat, double lon) {
        return new TripReading(odo, fuelMl, 62, 520, speed, lat, lon);
    }

    @Test
    public void ignitionToOffWithDebounceMakesOneTrip() {
        tracker.update(2, 0, reading(14_000, 100_000, 0));
        tracker.update(2, 2_000, reading(14_000, 100_000, 40));
        tracker.update(2, 4_000, reading(14_001, 100_050, 90));
        tracker.update(0, 6_000, reading(14_038, 102_900, 0));
        assertTrue(closed.isEmpty());
        tracker.update(0, 6_000 + TripTracker.END_DEBOUNCE_MS, reading(TripReading.INVALID, TripReading.INVALID, 0));

        assertEquals(1, closed.size());
        TripRecord t = closed.get(0);
        assertEquals(0L, t.startedAt);
        assertEquals(6_000L, t.endedAt);
        assertEquals(38, t.distanceKm);
        assertEquals(2_900, t.fuelUsedMl);
        assertEquals(90, t.maxSpeedKmh);
        assertEquals(14_000, t.odoStartKm);
        assertEquals(14_038, t.odoEndKm);
        assertFalse(tracker.isActive());
    }

    @Test
    public void statusReportsRunningTripAndRoutePoints() {
        assertFalse(tracker.status().active);

        tracker.update(2, 1_000, at(14_000, 100_000, 0, 41.0, 29.0)); // hız 0: nokta yok
        assertTrue(tracker.status().active);
        assertEquals(1_000L, tracker.status().startedAt);
        assertEquals(0, tracker.status().routePoints);

        tracker.update(2, 3_000, at(14_000, 100_000, 40, 41.0, 29.0));
        tracker.update(2, 4_000, at(14_000, 100_000, 40, 41.1, 29.1)); // 5 sn dolmadı: yeni nokta yok
        tracker.update(2, 9_000, at(14_001, 100_020, 40, 41.2, 29.2));
        assertEquals(2, tracker.status().routePoints);

        // Kontak kapandı ama kapanış beklemesinde kayıt hâlâ sürüyor sayılır.
        tracker.update(0, 12_000, reading(14_002, 100_040, 0));
        assertTrue(tracker.status().active);

        tracker.update(0, 12_000 + TripTracker.END_DEBOUNCE_MS, reading(TripReading.INVALID, TripReading.INVALID, 0));
        assertFalse(tracker.status().active);
    }

    @Test
    public void powerReturningWithinDebounceContinuesSameTrip() {
        tracker.update(2, 0, reading(1_000, 0, 50));
        tracker.update(0, 4_000, reading(1_002, 100, 0));
        tracker.update(2, 20_000, reading(1_002, 100, 30));
        tracker.update(0, 30_000, reading(1_010, 700, 0));
        tracker.update(0, 30_000 + TripTracker.END_DEBOUNCE_MS, reading(TripReading.INVALID, TripReading.INVALID, 0));

        assertEquals(1, closed.size());
        assertEquals(10, closed.get(0).distanceKm);
        assertEquals(30_000L, closed.get(0).endedAt);
    }

    @Test
    public void unreadablePowerCountsAsOff() {
        tracker.update(2, 0, reading(1_000, 0, 50));
        tracker.update(-1, 2_000, reading(1_005, 300, 0));
        tracker.update(-1, 2_000 + TripTracker.END_DEBOUNCE_MS, reading(TripReading.INVALID, TripReading.INVALID, 0));
        assertEquals(1, closed.size());
        assertEquals(5, closed.get(0).distanceKm);
    }

    @Test
    public void ignitionWithoutMovementIsNotATrip() {
        tracker.update(1, 0, reading(5_000, 10, 0));
        tracker.update(1, 2_000, reading(5_000, 10, 0));
        tracker.update(0, 4_000, reading(5_000, 10, 0));
        tracker.update(0, 4_000 + TripTracker.END_DEBOUNCE_MS, reading(5_000, 10, 0));
        assertTrue(closed.isEmpty());
    }

    @Test
    public void invalidCountersGiveZeroNotGarbage() {
        tracker.update(2, 0, reading(TripReading.INVALID, -1, 40));
        tracker.update(2, 2_000, reading(TripReading.INVALID, -1, 40));
        tracker.update(0, 4_000, reading(20_000_000, -1, 0));
        tracker.update(0, 4_000 + TripTracker.END_DEBOUNCE_MS, reading(0, 0, 0));
        assertEquals(1, closed.size());
        assertEquals(0, closed.get(0).distanceKm);
        assertEquals(0, closed.get(0).fuelUsedMl);
        assertTrue(closed.get(0).movingMs > 0);
    }

    @Test
    public void negativeFuelDifferenceIsZero() {
        tracker.update(2, 0, reading(100, 5_000, 30));
        tracker.update(2, 2_000, reading(101, 5_000, 30));
        tracker.update(0, 4_000, reading(105, 100, 0));
        tracker.update(0, 4_000 + TripTracker.END_DEBOUNCE_MS, reading(105, 100, 0));
        assertEquals(0, closed.get(0).fuelUsedMl);
        assertEquals(5, closed.get(0).distanceKm);
    }

    @Test
    public void movingTimeCountsOnlyWhileSpeedAboveZeroAndCapsGaps() {
        tracker.update(2, 0, reading(0, 0, 0));
        tracker.update(2, 2_000, reading(0, 0, 0));      // duruyor
        tracker.update(2, 4_000, reading(0, 0, 50));     // 2 sn
        tracker.update(2, 6_000, reading(1, 0, 50));     // 2 sn
        tracker.update(2, 106_000, reading(2, 0, 50));   // 100 sn boşluk: en çok 10 sn
        tracker.update(0, 108_000, reading(2, 0, 0));
        tracker.update(0, 108_000 + TripTracker.END_DEBOUNCE_MS, reading(2, 0, 0));
        assertEquals(14_000L, closed.get(0).movingMs);
    }

    @Test
    public void averageSpeedIsDistanceOverMovingTime() {
        tracker.update(2, 0, reading(0, 0, 60));
        for (int i = 1; i <= 360; i++) { // 360 × 10 sn = 1 saat hareket
            tracker.update(2, i * 10_000L, reading(i / 6, 0, 60));
        }
        tracker.update(0, 3_600_000L, reading(60, 3_000, 0));
        tracker.update(0, 3_600_000L + TripTracker.END_DEBOUNCE_MS, reading(60, 3_000, 0));
        assertEquals(60, closed.get(0).distanceKm);
        assertEquals(60, closed.get(0).avgSpeedKmh);
    }

    @Test
    public void routePointsAreThrottledAndOnlyWhileMovingWithFix() {
        tracker.update(2, 0, at(0, 0, 0, 40.76, 29.93));         // duruyor: nokta yok
        tracker.update(2, 2_000, at(0, 0, 30, 40.761, 29.931));  // ilk nokta
        tracker.update(2, 4_000, at(0, 0, 30, 40.762, 29.932));  // 5 sn dolmadı
        tracker.update(2, 8_000, at(0, 0, 40, 40.763, 29.933));  // ikinci nokta
        tracker.update(2, 10_000, reading(1, 0, 40));            // konum yok
        tracker.update(0, 12_000, at(2, 0, 0, 40.77, 29.94));
        tracker.update(0, 12_000 + TripTracker.END_DEBOUNCE_MS, reading(2, 0, 0));

        TripRecord t = closed.get(0);
        assertEquals(2, t.route.size());
        assertEquals(40.761, t.route.get(0).lat, 1e-9);
        assertEquals(40.763, t.route.get(1).lat, 1e-9);
        assertEquals(30, t.route.get(0).speedKmh);
        assertEquals(40.76, t.latStart, 1e-9);
        assertEquals(40.77, t.latEnd, 1e-9);
    }

    @Test
    public void missingLocationLeavesEndpointsNaN() {
        tracker.update(2, 0, reading(0, 0, 30));
        tracker.update(2, 2_000, reading(1, 0, 30));
        tracker.update(0, 4_000, reading(2, 0, 0));
        tracker.update(0, 4_000 + TripTracker.END_DEBOUNCE_MS, reading(2, 0, 0));
        assertTrue(Double.isNaN(closed.get(0).latStart));
        assertTrue(closed.get(0).route.isEmpty());
    }

    @Test
    public void fuelPercentTenthsAreNormalised() {
        assertEquals(35, TripReading.percent(348));
        assertEquals(62, TripReading.percent(62));
        assertEquals(100, TripReading.percent(100));
        assertEquals(TripReading.INVALID, TripReading.percent(-5));
        assertEquals(TripReading.INVALID, TripReading.percent(TripReading.INVALID));
    }

    @Test
    public void twoTripsGetDistinctIds() {
        for (int k = 0; k < 2; k++) {
            long base = k * 1_000_000L;
            tracker.update(2, base, reading(k * 100, 0, 30));
            tracker.update(2, base + 2_000, reading(k * 100 + 1, 0, 30));
            tracker.update(0, base + 4_000, reading(k * 100 + 3, 0, 0));
            tracker.update(0, base + 4_000 + TripTracker.END_DEBOUNCE_MS, reading(k * 100 + 3, 0, 0));
        }
        assertEquals(2, closed.size());
        assertFalse(closed.get(0).id.equals(closed.get(1).id));
    }

    @Test
    public void fuelCounterThatArrivesAfterIgnitionUsesTheFirstValidReadingAsBaseline() {
        tracker.update(2, 0, reading(14_000, TripReading.INVALID, 0)); // sayaç henüz gelmedi
        tracker.update(2, 2_000, reading(14_000, TripReading.INVALID, 40));
        tracker.update(2, 4_000, reading(14_010, 100_000, 60));        // ilk geçerli okuma: başlangıç
        tracker.update(2, 6_000, reading(14_030, 102_400, 80));
        tracker.update(0, 8_000, reading(14_038, 103_100, 0));
        tracker.update(0, 8_000 + TripTracker.END_DEBOUNCE_MS, reading(14_038, 103_100, 0));

        assertEquals(1, closed.size());
        assertEquals(3_100, closed.get(0).fuelUsedMl);
    }

    @Test
    public void fuelCounterThatResetsOrVanishesAtIgnitionOffStillCountsFromTheLastActiveSample() {
        tracker.update(2, 0, reading(14_000, 100_000, 0));
        tracker.update(2, 2_000, reading(14_020, 101_800, 70));
        tracker.update(0, 4_000, reading(14_038, 0, 0)); // kapanışta sayaç sıfırlandı
        tracker.update(0, 4_000 + TripTracker.END_DEBOUNCE_MS, reading(14_038, 0, 0));

        assertEquals(1, closed.size());
        assertEquals(1_800, closed.get(0).fuelUsedMl);
    }

    @Test
    public void tripWithoutAnyValidFuelCounterStillSavesWithZeroFuel() {
        tracker.update(2, 0, reading(14_000, TripReading.INVALID, 0));
        tracker.update(2, 2_000, reading(14_020, TripReading.INVALID, 70));
        tracker.update(0, 4_000, reading(14_038, TripReading.INVALID, 0));
        tracker.update(0, 4_000 + TripTracker.END_DEBOUNCE_MS, reading(14_038, TripReading.INVALID, 0));
        assertEquals(1, closed.size());
        assertEquals(0, closed.get(0).fuelUsedMl);
        assertEquals(38, closed.get(0).distanceKm);
    }

    @Test
    public void diagnosticsReportTheRawCounters() {
        List<String> messages = new ArrayList<>();
        TripTracker t = new TripTracker(new TripTracker.Listener() {
            @Override public void onTripClosed(TripRecord trip) { closed.add(trip); }
            @Override public void onDiagnostic(String message) { messages.add(message); }
        });
        t.update(2, 0, reading(14_000, 100_000, 0));
        t.update(2, 2_000, reading(14_020, 100_900, 70));
        t.update(0, 4_000, reading(14_038, 101_500, 0));
        t.update(0, 4_000 + TripTracker.END_DEBOUNCE_MS, reading(14_038, 101_500, 0));
        assertEquals(2, messages.size());
        assertTrue(messages.get(0), messages.get(0).contains("yakıt sayacı=100000"));
        assertTrue(messages.get(1), messages.get(1).contains("100000 → 101500 (fark 1500 birim)"));
    }

    // ---- denetim noktaları: kimse 60 sn beklemez --------------------------------

    private final List<TripRecord> checkpoints = new ArrayList<>();

    private TripTracker trackerWithCheckpoints() {
        checkpoints.clear();
        return new TripTracker(new TripTracker.Listener() {
            @Override public void onTripClosed(TripRecord trip) { closed.add(trip); }
            @Override public void onTripCheckpoint(TripRecord trip) { checkpoints.add(trip); }
        });
    }

    @Test
    public void tripIsCheckpointedTheMomentIgnitionDropsWithoutWaitingForDebounce() {
        TripTracker t = trackerWithCheckpoints();
        t.update(2, 0, reading(14_000, 100_000, 0));
        t.update(2, 2_000, reading(14_000, 100_000, 40));
        t.update(2, 4_000, reading(14_001, 100_050, 90));
        t.update(0, 6_000, reading(14_038, 102_900, 0));

        // Bitiş beklemesi dolmadı, ama yolculuk kayda geçti.
        assertTrue(closed.isEmpty());
        TripRecord last = checkpoints.get(checkpoints.size() - 1);
        assertEquals(6_000L, last.endedAt);
        assertEquals(38, last.distanceKm);
        assertEquals(2_900, last.fuelUsedMl);
        assertEquals(90, last.maxSpeedKmh);
    }

    @Test
    public void checkpointKeepsTheRouteDrivenSoFar() {
        TripTracker t = trackerWithCheckpoints();
        t.update(2, 0, at(0, 0, 0, 40.76, 29.93));
        t.update(2, 2_000, at(0, 0, 30, 40.761, 29.931));
        t.update(2, 8_000, at(1, 0, 40, 40.763, 29.933));
        t.update(0, 10_000, at(2, 0, 0, 40.77, 29.94));

        TripRecord last = checkpoints.get(checkpoints.size() - 1);
        assertEquals(2, last.route.size());
        assertEquals(40.763, last.route.get(1).lat, 1e-9);
        assertEquals(40.77, last.latEnd, 1e-9);
    }

    @Test
    public void runningTripIsCheckpointedPeriodicallyWithGrowingValues() {
        TripTracker t = trackerWithCheckpoints();
        t.update(2, 0, reading(1_000, 0, 50));
        for (long ms = 2_000; ms < TripTracker.CHECKPOINT_INTERVAL_MS; ms += 2_000) {
            t.update(2, ms, reading(1_000 + (int) (ms / 2_000), 0, 50));
        }
        assertTrue("ilk aralık dolmadan denetim noktası yok", checkpoints.isEmpty());

        t.update(2, 16_000, reading(1_010, 0, 50));
        assertEquals(1, checkpoints.size());
        t.update(2, 18_000, reading(1_011, 0, 50));
        assertEquals("aralık yeniden dolmadan ikincisi yok", 1, checkpoints.size());
        t.update(2, 32_000, reading(1_020, 0, 50));
        assertEquals(2, checkpoints.size());

        assertEquals(checkpoints.get(0).id, checkpoints.get(1).id);
        assertTrue(checkpoints.get(1).distanceKm > checkpoints.get(0).distanceKm);
        assertTrue(closed.isEmpty());
    }

    @Test
    public void checkpointAndFinalRecordShareTheIdSoTheFinalOneReplacesIt() {
        TripTracker t = trackerWithCheckpoints();
        t.update(2, 0, reading(14_000, 100_000, 50));
        t.update(2, 2_000, reading(14_010, 100_500, 50));
        t.update(0, 4_000, reading(14_038, 101_500, 0));
        t.update(0, 4_000 + TripTracker.END_DEBOUNCE_MS, reading(TripReading.INVALID, TripReading.INVALID, 0));

        assertEquals(1, closed.size());
        TripRecord checkpoint = checkpoints.get(checkpoints.size() - 1);
        assertEquals(checkpoint.id, closed.get(0).id);
        assertEquals(checkpoint.distanceKm, closed.get(0).distanceKm);
        assertEquals(checkpoint.endedAt, closed.get(0).endedAt);
    }

    @Test
    public void powerReturningAfterCheckpointKeepsOneTripAndLaterCheckpointsOverwriteIt() {
        TripTracker t = trackerWithCheckpoints();
        t.update(2, 0, reading(1_000, 0, 50));
        t.update(0, 4_000, reading(1_002, 100, 0));
        int afterDrop = checkpoints.size();
        t.update(2, 20_000, reading(1_002, 100, 30));
        t.update(2, 40_000, reading(1_030, 900, 30));
        t.update(0, 42_000, reading(1_031, 950, 0));

        assertTrue(checkpoints.size() > afterDrop);
        for (TripRecord c : checkpoints) {
            assertEquals(checkpoints.get(0).id, c.id);
        }
        assertEquals(31, checkpoints.get(checkpoints.size() - 1).distanceKm);
        assertEquals(42_000L, checkpoints.get(checkpoints.size() - 1).endedAt);
    }

    @Test
    public void ignitionWithoutMovementIsNeverCheckpointed() {
        TripTracker t = trackerWithCheckpoints();
        t.update(1, 0, reading(5_000, 10, 0));
        t.update(1, 20_000, reading(5_000, 10, 0));
        t.update(0, 24_000, reading(5_000, 10, 0));
        assertTrue(checkpoints.isEmpty());
    }

    @Test
    public void unreadableOdometerAtShutdownFallsBackToLastGoodSample() {
        TripTracker t = trackerWithCheckpoints();
        t.update(2, 0, reading(14_000, 100_000, 50));
        t.update(2, 2_000, reading(14_020, 100_900, 50));
        t.update(0, 4_000, reading(TripReading.INVALID, TripReading.INVALID, 0)); // kontak kapanınca sayaçlar okunamıyor

        TripRecord last = checkpoints.get(checkpoints.size() - 1);
        assertEquals(20, last.distanceKm);
        assertEquals(14_020, last.odoEndKm);
        assertEquals(900, last.fuelUsedMl);
    }

    // ---- park fotoğrafı tetiği -------------------------------------------------

    private final List<TripRecord> ignitionOff = new ArrayList<>();

    private TripTracker trackerWithIgnitionOff() {
        ignitionOff.clear();
        return new TripTracker(new TripTracker.Listener() {
            @Override public void onTripClosed(TripRecord trip) { closed.add(trip); }
            @Override public void onIgnitionOff(TripRecord trip) { ignitionOff.add(trip); }
        });
    }

    @Test
    public void ignitionOffAfterRealTripTriggersParkPhotoOnceWithTheTripId() {
        TripTracker t = trackerWithIgnitionOff();
        t.update(2, 0, reading(14_000, 100_000, 50));
        t.update(2, 2_000, reading(14_010, 100_500, 50));
        assertTrue(ignitionOff.isEmpty());
        t.update(0, 4_000, reading(14_038, 101_500, 0));
        t.update(0, 6_000, reading(14_038, 101_500, 0)); // beklemede tekrar tetiklenmez

        assertEquals(1, ignitionOff.size());
        assertEquals("t0", ignitionOff.get(0).id);
    }

    @Test
    public void unreadablePowerNeverTriggersParkPhotoBecauseTheCarMayStillBeDriving() {
        TripTracker t = trackerWithIgnitionOff();
        t.update(2, 0, reading(14_000, 100_000, 50));
        t.update(2, 2_000, reading(14_010, 100_500, 50));
        t.update(-1, 4_000, reading(14_012, 100_600, 50));
        assertTrue(ignitionOff.isEmpty());
    }

    @Test
    public void ignitionOffWithoutMovementDoesNotTriggerParkPhoto() {
        TripTracker t = trackerWithIgnitionOff();
        t.update(1, 0, reading(5_000, 10, 0));
        t.update(0, 4_000, reading(5_000, 10, 0));
        assertTrue(ignitionOff.isEmpty());
    }
}
