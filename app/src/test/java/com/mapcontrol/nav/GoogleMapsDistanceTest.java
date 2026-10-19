package com.mapcontrol.nav;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class GoogleMapsDistanceTest {
    private int meters(String title) {
        return new GoogleMapsNavSnapshot(true, null, title, null, false).segmentRemainMeters();
    }
    @Test public void metricAndManeuverTitle() {
        assertEquals(250, meters("250 m - Turn right"));
        assertEquals(1500, meters("1.5 km"));
        assertEquals(1500, meters("1,5 km"));
    }
    @Test public void imperialAndNonBreakingSpace() {
        assertEquals(152, meters("500 ft"));
        assertEquals(1609, meters("1 mi"));
        assertEquals(200, meters("200\u00a0m"));
        assertEquals(200, meters("200\u202fm"));
    }
    @Test public void unknownOrOverflowDoesNotFabricateDistance() {
        assertEquals(0, meters(null));
        assertEquals(0, meters("Turn right"));
        assertEquals(0, meters("-10 m"));
        assertEquals(0, meters("10 min"));
        assertEquals(0, meters("9999999999999999 km"));
        assertEquals(0, meters("200 m remaining"));
    }
    @Test public void inactiveRouteHasNoDistance() {
        assertEquals(0, new GoogleMapsNavSnapshot(false, null, "250 m", null, false)
                .segmentRemainMeters());
    }
}
