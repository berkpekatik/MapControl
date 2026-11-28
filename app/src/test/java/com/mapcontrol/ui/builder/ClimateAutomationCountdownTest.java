package com.mapcontrol.ui.builder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ClimateAutomationCountdownTest {

    @Test
    public void formatsMinutesAndSeconds() {
        assertEquals("12:34", ClimateAutomationTabBuilder.formatCountdown(754_000L));
        assertEquals("30:00", ClimateAutomationTabBuilder.formatCountdown(30 * 60_000L));
        assertEquals("00:01", ClimateAutomationTabBuilder.formatCountdown(1L));
        assertEquals("00:00", ClimateAutomationTabBuilder.formatCountdown(0L));
        assertEquals("00:00", ClimateAutomationTabBuilder.formatCountdown(-500L));
    }

    @Test
    public void roundsPartialSecondsUp() {
        assertEquals("01:00", ClimateAutomationTabBuilder.formatCountdown(59_001L));
        assertEquals("00:59", ClimateAutomationTabBuilder.formatCountdown(59_000L));
    }

    @Test
    public void showsHoursWhenOverAnHour() {
        assertEquals("1:02:03", ClimateAutomationTabBuilder.formatCountdown(3_723_000L));
    }
}
