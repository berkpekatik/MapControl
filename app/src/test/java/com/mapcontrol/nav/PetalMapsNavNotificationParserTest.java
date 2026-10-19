package com.mapcontrol.nav;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PetalMapsNavNotificationParserTest {

    @Test
    public void petalMapsNotificationWithoutSubTextIsActive() {
        GoogleMapsNavSnapshot snap = PetalMapsNavNotificationParser.parseFields(
                "200 m", "Turn left", null, false);
        assertTrue(snap.active);
        assertEquals("200 m", snap.distanceTitle);
        assertEquals("Turn left", snap.maneuverHint);
    }

    @Test
    public void promptWithoutDistanceStaysInactive() {
        GoogleMapsNavSnapshot snap = PetalMapsNavNotificationParser.parseFields(
                "Petal Maps", "Open the app", null, false);
        assertFalse(snap.active);
    }
}
