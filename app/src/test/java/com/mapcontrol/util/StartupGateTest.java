package com.mapcontrol.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StartupGateTest {

    @Test
    public void powerOffClearsWelcomeAndDoesNotLaunch() {
        StartupGate gate = StartupGate.decide(-1, 0, false, true, true);
        assertTrue(gate.clearWelcome);
        assertFalse(gate.showWelcome);
        assertFalse(gate.launchUi);
    }

    @Test
    public void firstSampleWhileOnShowsWelcomeAndLaunches() {
        StartupGate gate = StartupGate.decide(-1, 2, false, true, true);
        assertFalse(gate.clearWelcome);
        assertTrue(gate.showWelcome);
        assertTrue(gate.launchUi);
    }

    @Test
    public void alreadyShownWelcomeDoesNotRepeatWhileLaunchStaysPending() {
        StartupGate gate = StartupGate.decide(-1, 2, true, true, true);
        assertFalse(gate.showWelcome);
        assertTrue(gate.launchUi);
    }

    @Test
    public void stayingOnDoesNotRepeatWelcomeButCanLaunch() {
        StartupGate gate = StartupGate.decide(2, 2, true, true, true);
        assertFalse(gate.showWelcome);
        assertTrue(gate.launchUi);
    }

    @Test
    public void motorOnAfterStandbyDoesNotRepeatWelcome() {
        StartupGate gate = StartupGate.decide(1, 2, true, true, false);
        assertFalse(gate.showWelcome);
        assertFalse(gate.launchUi);
    }

    @Test
    public void designOffDoesNotShow() {
        StartupGate gate = StartupGate.decide(0, 1, false, false, false);
        assertFalse(gate.showWelcome);
    }

    @Test
    public void ignitionRiseShowsAgainAfterLatchCleared() {
        StartupGate gate = StartupGate.decide(0, 1, false, true, true);
        assertTrue(gate.showWelcome);
        assertTrue(gate.launchUi);
    }
}
