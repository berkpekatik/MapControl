package com.mapcontrol.nav;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GoogleMapsNavNotificationParserTest {

    @Test
    public void missingSubTextIsInactive() {
        GoogleMapsNavSnapshot snap = GoogleMapsNavNotificationParser.parseFields(
                "200 m", "Turn left - Main", null, false);
        assertFalse(snap.active);
        assertNull(snap.formatSummaryLine());
    }

    @Test
    public void convertsFeetAndMilesAndStripsTvs() {
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
    public void ignoresSignInAndUpdateTitles() {
        GoogleMapsNavSnapshot signIn = GoogleMapsNavNotificationParser.parseFields(
                "Sign in to Google", "Account - prompt", "Open Maps", false);
        assertFalse(signIn.active);

        GoogleMapsNavSnapshot update = GoogleMapsNavNotificationParser.parseFields(
                "200 m", "Güncelleme gerekli - Store", "3 km · 10 dk", false);
        assertFalse(update.active);
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
        assertEquals(152, snap.segmentRemainMeters());
    }
}
