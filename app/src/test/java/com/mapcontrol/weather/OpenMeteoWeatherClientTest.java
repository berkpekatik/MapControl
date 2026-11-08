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

    @Test
    public void fastFailureThenOnAvailableStillSchedulesRetry() {
        // start -> istek hızla başarısız (fetching=false) -> bağlantı geri geldi, 5 sn dolmadan tekrar çağrıldı
        assertEquals(OpenMeteoWeatherClient.FetchAction.SKIP_AND_SCHEDULE,
                OpenMeteoWeatherClient.decideFetch(false, 1_000L, 0L, false));
    }

    @Test
    public void runningFetchSchedulesItselfWhenFinished() {
        assertEquals(OpenMeteoWeatherClient.FetchAction.SKIP_FETCHING,
                OpenMeteoWeatherClient.decideFetch(true, 1_000L, 0L, false));
    }

    @Test
    public void fetchesAfterMinimumGap() {
        assertEquals(OpenMeteoWeatherClient.FetchAction.FETCH,
                OpenMeteoWeatherClient.decideFetch(false, 6_000L, 0L, false));
    }
}
