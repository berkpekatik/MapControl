package com.mapcontrol.vehicle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mapcontrol.comfort.ComfortPlan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** Konfor otomasyonunun araca yazdığı değerler: araçta OEM klimanın yazdığı değerlerle aynı olmalı. */
public class ClimateAutomationWritesTest {

    private static final int HV = ClimateAutomationWrites.MODULE_HVAC;
    private static final int CS = ClimateAutomationWrites.MODULE_SETTING;

    private static ClimateAutomationWrites.Write w(int module, int id, int value) {
        return new ClimateAutomationWrites.Write(module, id, value);
    }

    private static List<ClimateAutomationWrites.Write> allWrites() {
        List<ClimateAutomationWrites.Write> all = new ArrayList<>();
        for (String id : ClimateAutomationStore.ALL_IDS) {
            for (int level = 1; level <= 4; level++) {
                all.addAll(ClimateAutomationWrites.onWrites(id, level));
            }
            all.addAll(ClimateAutomationWrites.offWrites(id));
        }
        all.addAll(ClimateAutomationWrites.climateFollowUpWrites());
        return all;
    }

    @Test
    public void everyRuleHasTables() {
        for (String id : ClimateAutomationStore.ALL_IDS) {
            assertNotNull(id, ClimateAutomationWrites.onWrites(id, 2));
            assertNotNull(id, ClimateAutomationWrites.offWrites(id));
        }
        assertNull(ClimateAutomationWrites.onWrites("yok", 2));
        assertNull(ClimateAutomationWrites.offWrites("yok"));
    }

    @Test
    public void neverWritesCs159OrCs160Zero() {
        for (ClimateAutomationWrites.Write x : allWrites()) {
            assertFalse("CS 159 yazılmamalı: " + x, x.module == CS && x.id == 159);
            // 160 anahtardır; yalnızca toggleSteerWheelHeat ile 160 ← 1 gider, tablolarda hiç yok
            assertFalse("CS 160 tabloda olmamalı: " + x, x.module == CS && x.id == 160);
        }
        assertTrue(ClimateAutomationWrites.onWrites(ClimateAutomationStore.STEER_HEAT, 2).isEmpty());
        assertTrue(ClimateAutomationWrites.offWrites(ClimateAutomationStore.STEER_HEAT).isEmpty());
        assertEquals(1, ClimateAutomationWrites.STEER_WHEEL_TOGGLE);
    }

    @Test
    public void climateOnIsPowerFirstThenAcAndAuto() {
        assertEquals(Arrays.asList(w(HV, 18, 1)),
                ClimateAutomationWrites.onWrites(ClimateAutomationStore.CLIMATE, 2));
        assertEquals(Arrays.asList(w(HV, 1, 1), w(HV, 4, 1)), ClimateAutomationWrites.climateFollowUpWrites());
    }

    @Test
    public void climateOffUsesTwoNeverZero() {
        assertEquals(Arrays.asList(w(HV, 1, 2), w(HV, 18, 2), w(HV, 4, 2)),
                ClimateAutomationWrites.offWrites(ClimateAutomationStore.CLIMATE));
    }

    @Test
    public void windshieldOnOneOffTwo() {
        assertEquals(Arrays.asList(w(HV, 65, 1), w(HV, 24, 1)),
                ClimateAutomationWrites.onWrites(ClimateAutomationStore.WINDSHIELD, 2));
        assertEquals(Arrays.asList(w(HV, 65, 2), w(HV, 24, 2)),
                ClimateAutomationWrites.offWrites(ClimateAutomationStore.WINDSHIELD));
    }

    @Test
    public void rearDefrostOnlyHvac80() {
        assertEquals(Arrays.asList(w(HV, 80, 1)),
                ClimateAutomationWrites.onWrites(ClimateAutomationStore.REAR_DEFROST, 2));
        assertEquals(Arrays.asList(w(HV, 80, 0)),
                ClimateAutomationWrites.offWrites(ClimateAutomationStore.REAR_DEFROST));
    }

    @Test
    public void seatsUseWireLevelAndOffIsOne() {
        assertEquals(Arrays.asList(w(CS, 95, 3)),
                ClimateAutomationWrites.onWrites(ClimateAutomationStore.DRIVER_HEAT, 3));
        assertEquals(Arrays.asList(w(CS, 96, 4)),
                ClimateAutomationWrites.onWrites(ClimateAutomationStore.PASSENGER_HEAT, 4));
        assertEquals(Arrays.asList(w(CS, 93, 2)),
                ClimateAutomationWrites.onWrites(ClimateAutomationStore.DRIVER_VENT, 2));
        assertEquals(Arrays.asList(w(CS, 94, 2)),
                ClimateAutomationWrites.onWrites(ClimateAutomationStore.PASSENGER_VENT, 2));
        assertEquals(Arrays.asList(w(CS, 95, 1)), ClimateAutomationWrites.offWrites(ClimateAutomationStore.DRIVER_HEAT));
        assertEquals(Arrays.asList(w(CS, 96, 1)), ClimateAutomationWrites.offWrites(ClimateAutomationStore.PASSENGER_HEAT));
        assertEquals(Arrays.asList(w(CS, 93, 1)), ClimateAutomationWrites.offWrites(ClimateAutomationStore.DRIVER_VENT));
        assertEquals(Arrays.asList(w(CS, 94, 1)), ClimateAutomationWrites.offWrites(ClimateAutomationStore.PASSENGER_VENT));
    }

    @Test
    public void steerToggleDecision() {
        // Motorun kullandığı karar: okunamadıysa gönderme (hata), hedefteyse atla, değilse 160 ← 1
        assertEquals(ComfortPlan.ToggleAction.REFUSE, ComfortPlan.toggleAction(null, 1));
        assertEquals(ComfortPlan.ToggleAction.REFUSE, ComfortPlan.toggleAction(null, 0));
        assertEquals(ComfortPlan.ToggleAction.SKIP, ComfortPlan.toggleAction(1, 1));
        assertEquals(ComfortPlan.ToggleAction.SKIP, ComfortPlan.toggleAction(0, 0));
        assertEquals(ComfortPlan.ToggleAction.SEND, ComfortPlan.toggleAction(0, 1));
        assertEquals(ComfortPlan.ToggleAction.SEND, ComfortPlan.toggleAction(1, 0));
    }
}
