package com.mapcontrol.vehicle;

import com.desaysv.ivi.extra.project.carinfo.HvacID;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClimateAutomationEngineTest {

    @Test
    public void steeringWheelHeatTurnOnSendsToggleOneOnlyWhenOff() {
        assertEquals(Integer.valueOf(1), ClimateAutomationEngine.resolveSteeringWheelHeatCommand(true, new int[]{0}));
        assertEquals(Integer.valueOf(1), ClimateAutomationEngine.resolveSteeringWheelHeatCommand(true, new int[]{2}));
        assertEquals(Integer.valueOf(1), ClimateAutomationEngine.resolveSteeringWheelHeatCommand(true, null));
        assertEquals(Integer.valueOf(1), ClimateAutomationEngine.resolveSteeringWheelHeatCommand(true, new int[0]));

        // Already ON: must not re-send 1 or the toggle would flip it back OFF
        assertNull(ClimateAutomationEngine.resolveSteeringWheelHeatCommand(true, new int[]{1}));
    }

    @Test
    public void steeringWheelHeatTurnOffSendsToggleOneOnlyWhenOn() {
        // Active (1): Chery MCU requires toggle pulse 1 (sending 0 does not turn off steering heat)
        assertEquals(Integer.valueOf(1), ClimateAutomationEngine.resolveSteeringWheelHeatCommand(false, new int[]{1}));

        // Already OFF: must not send 1 or it would toggle steering heat back ON
        assertNull(ClimateAutomationEngine.resolveSteeringWheelHeatCommand(false, new int[]{0}));
        assertNull(ClimateAutomationEngine.resolveSteeringWheelHeatCommand(false, new int[]{2}));
        assertNull(ClimateAutomationEngine.resolveSteeringWheelHeatCommand(false, null));
        assertNull(ClimateAutomationEngine.resolveSteeringWheelHeatCommand(false, new int[0]));
    }

    @Test
    public void ruleTimeoutMatchesSeatAndSteeringDelaySchedule() {
        ClimateAutomationStore.Rule rule = new ClimateAutomationStore.Rule();
        rule.enabled = true;
        rule.delayMinutes = 5;

        long ignitionAt = 1_000_000L;
        assertFalse(ClimateAutomationEngine.isRuleTimedOut(rule, 0L, ignitionAt + 600_000L));
        assertFalse(ClimateAutomationEngine.isRuleTimedOut(rule, ignitionAt, ignitionAt + 299_999L));
        assertTrue(ClimateAutomationEngine.isRuleTimedOut(rule, ignitionAt, ignitionAt + 300_000L));
        assertTrue(ClimateAutomationEngine.isRuleTimedOut(rule, ignitionAt, ignitionAt + 420_000L));

        rule.delayMinutes = 0;
        assertFalse(ClimateAutomationEngine.isRuleTimedOut(rule, ignitionAt, ignitionAt + 3_600_000L));
    }

    @Test
    public void ruleTimeoutCountsFromMidDriveActivationNotIgnition() {
        ClimateAutomationStore.Rule rule = new ClimateAutomationStore.Rule();
        rule.enabled = true;
        rule.delayMinutes = 5;

        long ignitionAt = 1_000_000L;
        // 20 minutes after ignition, rule has NOT activated yet (activatedAtMs == 0): must not time out
        long midDriveMinute20 = ignitionAt + 1_200_000L;
        assertFalse(ClimateAutomationEngine.isRuleTimedOut(rule, 0L, midDriveMinute20));

        // Temperature crosses threshold at minute 20 -> rule activates then; runs full 5 min until minute 25
        long activatedAt = midDriveMinute20;
        assertFalse(ClimateAutomationEngine.isRuleTimedOut(rule, activatedAt, activatedAt + 299_999L));
        assertTrue(ClimateAutomationEngine.isRuleTimedOut(rule, activatedAt, activatedAt + 300_000L));
    }

    @Test
    public void drivePowerReadyBlocksEvaluationAfterEngineStopUntilRestart() {
        assertFalse(ClimateAutomationEngine.isDrivePowerReady(-1, false, false));
        assertFalse(ClimateAutomationEngine.isDrivePowerReady(0, false, false));
        // Tam kontakta (motor henüz çalışmadan) ısıtma/soğutma açılmaz -> aracın çalışması beklenir
        assertFalse(ClimateAutomationEngine.isDrivePowerReady(1, false, false));
        assertFalse(ClimateAutomationEngine.isDrivePowerReady(2, false, false));
        // Motor çalıştıktan sonra (RPM > 0) hem açılış süresi sayacı hem otomasyon devreye girer
        assertTrue(ClimateAutomationEngine.isDrivePowerReady(1, false, true));
        assertTrue(ClimateAutomationEngine.isDrivePowerReady(2, false, true));
        // After PowerMode 2->1 engine stop, must wait until engine restarts
        assertFalse(ClimateAutomationEngine.isDrivePowerReady(1, true, false));
    }

    @Test
    public void manualOverrideIgnoredUntilRuleConfirmedActive() {
        assertFalse(ClimateAutomationEngine.shouldDetectManualOverride(false, true));
        assertFalse(ClimateAutomationEngine.shouldDetectManualOverride(false, false));
        assertFalse(ClimateAutomationEngine.shouldDetectManualOverride(true, false));
        assertTrue(ClimateAutomationEngine.shouldDetectManualOverride(true, true));
    }

    @Test
    public void effectivePowerModeCombinesSystemAndCarPowerSignals() {
        assertEquals(0, ClimateAutomationEngine.resolveEffectivePowerMode(0, 0));
        assertEquals(1, ClimateAutomationEngine.resolveEffectivePowerMode(0, 1));
        assertEquals(1, ClimateAutomationEngine.resolveEffectivePowerMode(1, 0));
        assertEquals(2, ClimateAutomationEngine.resolveEffectivePowerMode(0, 2));
        assertEquals(1, ClimateAutomationEngine.resolveEffectivePowerMode(-1, 1));
        assertEquals(-1, ClimateAutomationEngine.resolveEffectivePowerMode(Integer.MIN_VALUE, -1));
        assertFalse(ClimateAutomationEngine.isCarPowerModeRunning(2));
        assertTrue(ClimateAutomationEngine.isCarPowerModeRunning(4));
    }

    @Test
    public void temperatureHysteresisPreventsChatteringWhenActive() {
        // CMP_LT < 10: activates strictly below 10; once active, stays active until >= 11.0
        assertFalse(ClimateAutomationStore.matchesTemperatureWithHysteresis(10.0, ClimateAutomationStore.CMP_LT, 10, false));
        assertTrue(ClimateAutomationStore.matchesTemperatureWithHysteresis(10.0, ClimateAutomationStore.CMP_LT, 10, true));
        assertTrue(ClimateAutomationStore.matchesTemperatureWithHysteresis(10.9, ClimateAutomationStore.CMP_LT, 10, true));
        assertFalse(ClimateAutomationStore.matchesTemperatureWithHysteresis(11.0, ClimateAutomationStore.CMP_LT, 10, true));

        // CMP_LTE <= 10: once active, stays active up to <= 11.0
        assertTrue(ClimateAutomationStore.matchesTemperatureWithHysteresis(11.0, ClimateAutomationStore.CMP_LTE, 10, true));
        assertFalse(ClimateAutomationStore.matchesTemperatureWithHysteresis(11.1, ClimateAutomationStore.CMP_LTE, 10, true));

        // CMP_GT > 15: activates above 15; once active, stays active until <= 14.0
        assertFalse(ClimateAutomationStore.matchesTemperatureWithHysteresis(15.0, ClimateAutomationStore.CMP_GT, 15, false));
        assertTrue(ClimateAutomationStore.matchesTemperatureWithHysteresis(15.0, ClimateAutomationStore.CMP_GT, 15, true));
        assertTrue(ClimateAutomationStore.matchesTemperatureWithHysteresis(14.1, ClimateAutomationStore.CMP_GT, 15, true));
        assertFalse(ClimateAutomationStore.matchesTemperatureWithHysteresis(14.0, ClimateAutomationStore.CMP_GT, 15, true));

        // CMP_GTE >= 15: once active, stays active down to >= 14.0
        assertTrue(ClimateAutomationStore.matchesTemperatureWithHysteresis(14.0, ClimateAutomationStore.CMP_GTE, 15, true));
        assertFalse(ClimateAutomationStore.matchesTemperatureWithHysteresis(13.9, ClimateAutomationStore.CMP_GTE, 15, true));
    }

    @Test
    public void seatFeedbackTreatsUserLevelChangeAsStillActiveNotManualOff() {
        // Off values (0/1) always count as FALSE (off / manual override)
        assertEquals(Boolean.FALSE, ClimateAutomationEngine.evaluateSeatFeedback(0, 3, true));
        assertEquals(Boolean.FALSE, ClimateAutomationEngine.evaluateSeatFeedback(1, 3, true));

        // Target wire level (level 3 -> wire 4, or raw level 3) confirms active
        assertEquals(Boolean.TRUE, ClimateAutomationEngine.evaluateSeatFeedback(4, 3, false));
        assertEquals(Boolean.TRUE, ClimateAutomationEngine.evaluateSeatFeedback(3, 3, false));

        // Driver lowers seat heat from 3 to 2 on head unit (wire 3 vs target wire 4 -> raw=2/3)
        // Before automation has applied this drive, mismatched level shouldn't falsely confirm active
        assertNull(ClimateAutomationEngine.evaluateSeatFeedback(2, 3, false));
        // Once automation already applied this drive, any active level (>=2) remains TRUE, not manual OFF
        assertEquals(Boolean.TRUE, ClimateAutomationEngine.evaluateSeatFeedback(2, 3, true));
        assertEquals(Boolean.TRUE, ClimateAutomationEngine.evaluateSeatFeedback(3, 3, true));
    }

    @Test
    public void carPowerMode4RearmsOncePerDrive() {
        // Non-running ID_CAR_POWER_MODE values (0, 1, 2, -1) must not trigger engine start
        assertFalse(ClimateAutomationEngine.isCarPowerModeRunning(-1));
        assertFalse(ClimateAutomationEngine.isCarPowerModeRunning(0));
        assertFalse(ClimateAutomationEngine.isCarPowerModeRunning(1));
        assertFalse(ClimateAutomationEngine.isCarPowerModeRunning(2));
        assertTrue(ClimateAutomationEngine.isCarPowerModeRunning(4));

        assertFalse(ClimateAutomationEngine.shouldRearmOnCarPowerRunning(2, false, 0));
        assertFalse(ClimateAutomationEngine.shouldRearmOnCarPowerRunning(2, false, 1));
        assertFalse(ClimateAutomationEngine.shouldRearmOnCarPowerRunning(2, false, 2));
        assertFalse(ClimateAutomationEngine.shouldRearmOnCarPowerRunning(0, false, 4));

        // First carPowerMode == 4 in a drive rearms once
        assertTrue(ClimateAutomationEngine.shouldRearmOnCarPowerRunning(1, false, 4));
        assertTrue(ClimateAutomationEngine.shouldRearmOnCarPowerRunning(4, false, 4));

        // Subsequent polls in the same drive must NOT rearm again
        assertFalse(ClimateAutomationEngine.shouldRearmOnCarPowerRunning(4, true, 4));
        assertFalse(ClimateAutomationEngine.shouldRearmOnCarPowerRunning(4, true, 2));
    }

    @Test
    public void startDelayWaitsBeforeActivationWithoutClashingWithAutoOff() {
        ClimateAutomationStore.Rule rule = new ClimateAutomationStore.Rule();
        rule.enabled = true;
        rule.startDelayMinutes = 2;
        rule.delayMinutes = 5;

        long driveReadyAt = 1_000_000L;
        assertEquals(2, ClimateAutomationEngine.remainingStartDelayMinutes(rule, driveReadyAt, driveReadyAt));
        assertEquals(1, ClimateAutomationEngine.remainingStartDelayMinutes(rule, driveReadyAt, driveReadyAt + 90_000L));
        assertEquals(0, ClimateAutomationEngine.remainingStartDelayMinutes(rule, driveReadyAt, driveReadyAt + 120_000L));

        // Immediate start (0 min) never blocks
        rule.startDelayMinutes = 0;
        assertEquals(0, ClimateAutomationEngine.remainingStartDelayMinutes(rule, driveReadyAt, driveReadyAt));

        // When rule/master switch is enabled mid-drive (15 min after engine start),
        // start delay counts from the later enable timestamp so it doesn't fire immediately
        rule.startDelayMinutes = 2;
        long enabledMidDriveAt = driveReadyAt + 900_000L;
        long base = Math.max(driveReadyAt, enabledMidDriveAt);
        assertEquals(2, ClimateAutomationEngine.remainingStartDelayMinutes(rule, base, enabledMidDriveAt));
        assertEquals(0, ClimateAutomationEngine.remainingStartDelayMinutes(rule, base, enabledMidDriveAt + 120_000L));
    }

    @Test
    public void engineRunningUsesCarPowerModeOrEngineState() {
        // Tiggo 8 / T1E24: ID_CAR_POWER_MODE (13) == 4
        assertTrue(ClimateAutomationEngine.isEngineRunning(4, 0, true, false, 2));
        // ID_ENGINE_STATE (74) == 1 tek başına yeterli
        assertTrue(ClimateAutomationEngine.isEngineRunning(0, 1, false, true, 2));
        // Sinyaller destekleniyor ama motor çalışmıyor: kontak açık olsa da bekle
        assertFalse(ClimateAutomationEngine.isEngineRunning(2, 0, true, false, 2));
        assertFalse(ClimateAutomationEngine.isEngineRunning(0, 0, false, true, 2));
    }

    @Test
    public void engineRunningFallsBackToPowerModeWhenSignalsMissing() {
        // T19C: 13 ve 74 hiç gelmedi (CarInfoProxy [0] döndürür) -> PowerMode>=2 eski davranış
        assertTrue(ClimateAutomationEngine.isEngineRunning(0, 0, false, false, 2));
        assertTrue(ClimateAutomationEngine.isEngineRunning(-1, -1, false, false, 3));
        assertFalse(ClimateAutomationEngine.isEngineRunning(0, 0, false, false, 1));
        assertFalse(ClimateAutomationEngine.isEngineRunning(0, 0, false, false, 0));

        assertTrue(ClimateAutomationEngine.shouldRearmOnEngineRunning(2, false, true));
        assertFalse(ClimateAutomationEngine.shouldRearmOnEngineRunning(2, true, true));
        assertFalse(ClimateAutomationEngine.shouldRearmOnEngineRunning(0, false, true));
    }

    @Test
    public void heatOnlyModeIgnoresMaxFrontDefrostSignalForWindshield() {
        int hvac = VDEventCarInfo.MODULE_HVAC;
        assertTrue(ClimateAutomationEngine.isIgnoredWindshieldSignal(
                ClimateAutomationStore.WINDSHIELD, hvac, HvacID.ID_AC_MAX_FRONT_DEFOREST, true));
        // Fan modunda 24 kuralın parçasıdır
        assertFalse(ClimateAutomationEngine.isIgnoredWindshieldSignal(
                ClimateAutomationStore.WINDSHIELD, hvac, HvacID.ID_AC_MAX_FRONT_DEFOREST, false));
        // Rezistans (65) her iki modda da izlenir
        assertFalse(ClimateAutomationEngine.isIgnoredWindshieldSignal(
                ClimateAutomationStore.WINDSHIELD, hvac, HvacID.ID_AC_FRONT_GLASS_HEAT, true));
        assertFalse(ClimateAutomationEngine.isIgnoredWindshieldSignal(
                ClimateAutomationStore.REAR_DEFROST, hvac, HvacID.ID_AC_REAR_DEFROST, true));
    }

    @Test
    public void rearDefrostToggleCommandAvoidsDoubleToggle() {
        assertEquals(Integer.valueOf(1),
                ClimateAutomationEngine.resolveRearDefrostCommand(true, new int[] {0}, new int[] {0}));
        assertNull(ClimateAutomationEngine.resolveRearDefrostCommand(true, new int[] {1}, new int[] {0}));
        assertNull(ClimateAutomationEngine.resolveRearDefrostCommand(true, new int[] {0}, new int[] {1}));

        assertEquals(Integer.valueOf(1),
                ClimateAutomationEngine.resolveRearDefrostCommand(false, new int[] {1}, new int[] {0}));
        assertEquals(Integer.valueOf(1),
                ClimateAutomationEngine.resolveRearDefrostCommand(false, new int[] {0}, new int[] {1}));
        assertNull(ClimateAutomationEngine.resolveRearDefrostCommand(false, new int[] {0}, new int[] {0}));
    }
}
