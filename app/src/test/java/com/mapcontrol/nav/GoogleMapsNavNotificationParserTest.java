package com.mapcontrol.nav;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GoogleMapsNavNotificationParserTest {

    @Test
    public void charSequenceExtrasAreKept() {
        assertNull(GoogleMapsNavNotificationParser.normalizeExtra(null));
        assertNull(GoogleMapsNavNotificationParser.normalizeExtra("   "));
        assertEquals("200 m", GoogleMapsNavNotificationParser.normalizeExtra(new StringBuilder(" 200 m ")));
    }

    @Test
    public void missingSubTextIsInactive() {
        GoogleMapsNavSnapshot snap = GoogleMapsNavNotificationParser.parseFields(
                "200 m", "Turn left - Main", null, false);
        assertFalse(snap.active);
        assertNull(snap.formatSummaryLine());
    }

    @Test
    public void convertsFeetAndMilesAndStripsClockLabels() {
        GoogleMapsNavSnapshot snap = GoogleMapsNavNotificationParser.parseFields(
                "500 ft",
                "Turn left TVS: - Oak",
                "1.2 mi · 12 min ETA",
                true);
        assertTrue(snap.active);
        assertTrue(snap.hadIcon);
        assertEquals("152 m", snap.distanceTitle);
        assertEquals("Turn left", snap.maneuverHint);
        assertEquals("1,9 km · 12 min", snap.routeSummary);
    }

    @Test
    public void ignoresPromptsWithoutDistanceOrDuration() {
        GoogleMapsNavSnapshot signIn = GoogleMapsNavNotificationParser.parseFields(
                "Sign in to Google", "Account - prompt", "Open Maps", false);
        assertFalse(signIn.active);

        GoogleMapsNavSnapshot store = GoogleMapsNavNotificationParser.parseFields(
                "Store", "Ready to install - Store", "Open the app", false);
        assertFalse(store.active);
    }

    @Test
    public void shortSubTextStillActive() {
        GoogleMapsNavSnapshot snap = GoogleMapsNavNotificationParser.parseFields(
                "80 m", null, "Arrive", false);
        assertTrue(snap.active);
        assertEquals("80 m", snap.distanceTitle);
        assertEquals("Arrive", snap.routeSummary);
        assertNull(snap.maneuverHint);
    }

    @Test
    public void snapshotExposesClusterCardFields() {
        GoogleMapsNavSnapshot snap = GoogleMapsNavNotificationParser.parseFields(
                "500 ft - Head south-west",
                "Vega AVM - 12:26 ETA",
                "1 hr 34 min · 89 mi · 12:26 ETA",
                true);
        assertTrue(snap.active);
        assertEquals("152 m", snap.formatManeuverDistanceLine());
        assertEquals("Head south-west", snap.formatManeuverDirectionLine());
        assertEquals("143,2 km", snap.etaDistanceLabel());
        assertEquals("1 hr 34 min", snap.etaDurationLabel());
        assertEquals("12:26", snap.etaArrivalLabel());
    }

    @Test
    public void metersAtEndOfSummaryAreReadAsRemainingDistance() {
        GoogleMapsNavSnapshot snap = GoogleMapsNavNotificationParser.parseFields(
                null, "Salkım Söğüt Sk. yönüne doğru", "1 dk. · 260 m · TVS: 23:01", false);
        assertTrue(snap.active);
        assertEquals("260 m", snap.etaDistanceLabel());
        assertEquals("1 dk.", snap.etaDurationLabel());
        assertEquals("23:01", snap.etaArrivalLabel());
    }

    @Test
    public void kilometersAndMinutesStillSeparated() {
        GoogleMapsNavSnapshot snap = GoogleMapsNavNotificationParser.parseFields(
                "0 m", null, "12 dk. · 2,3 km · TVS: 23:10", false);
        assertEquals("2,3 km", snap.etaDistanceLabel());
        assertEquals("12 dk.", snap.etaDurationLabel());
        GoogleMapsNavSnapshot minutes = GoogleMapsNavNotificationParser.parseFields(
                "0 m", null, "5 min · 800 m", false);
        assertEquals("800 m", minutes.etaDistanceLabel());
        assertEquals("5 min", minutes.etaDurationLabel());
    }

    @Test
    public void textWithoutSeparatorIsUsedAsInstruction() {
        GoogleMapsNavSnapshot snap = GoogleMapsNavNotificationParser.parseFields(
                null, "Salkım Söğüt Sk. yönüne doğru", "1 dk. · 240 m · TVS: 23:04", false);
        assertEquals("Salkım Söğüt Sk. yönüne doğru", snap.formatManeuverDirectionLine());
        assertNull(snap.formatManeuverDistanceLine());
    }
}
