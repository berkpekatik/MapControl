package com.mapcontrol.weather;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OpenMeteoWeatherClientTest {

    private static final long MINUTE = 60_000L;

    @Test
    public void afterSuccessRefreshesEveryThirtyMinutes() {
        assertEquals(30 * MINUTE, OpenMeteoWeatherClient.nextRefreshDelayMs(0));
    }

    @Test
    public void failuresBackOffStepByStep() {
        assertEquals(MINUTE, OpenMeteoWeatherClient.nextRefreshDelayMs(1));
        assertEquals(5 * MINUTE, OpenMeteoWeatherClient.nextRefreshDelayMs(2));
        assertEquals(15 * MINUTE, OpenMeteoWeatherClient.nextRefreshDelayMs(3));
        assertEquals(30 * MINUTE, OpenMeteoWeatherClient.nextRefreshDelayMs(4));
    }

    @Test
    public void backoffStopsAtThirtyMinutes() {
        assertEquals(30 * MINUTE, OpenMeteoWeatherClient.nextRefreshDelayMs(10));
    }
}
