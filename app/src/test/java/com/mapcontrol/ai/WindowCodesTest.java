package com.mapcontrol.ai;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/** Tiggo 7 cam kodları OEM com.desaysv.vrcontrol (handleWindowStatement) ile aynı olmalı. */
public class WindowCodesTest {
    @Test
    public void singleWindows() {
        assertArrayEquals(new int[]{1}, VehicleActionExecutor.t19cWindowCodes("fl", "open"));
        assertArrayEquals(new int[]{2}, VehicleActionExecutor.t19cWindowCodes("fl", "close"));
        assertArrayEquals(new int[]{3}, VehicleActionExecutor.t19cWindowCodes("fr", "open"));
        assertArrayEquals(new int[]{6}, VehicleActionExecutor.t19cWindowCodes("rl", "close"));
        assertArrayEquals(new int[]{7}, VehicleActionExecutor.t19cWindowCodes("rr", "open"));
    }

    @Test
    public void groups() {
        assertArrayEquals(new int[]{9}, VehicleActionExecutor.t19cWindowCodes("front", "open"));
        assertArrayEquals(new int[]{12}, VehicleActionExecutor.t19cWindowCodes("rear", "close"));
        assertArrayEquals(new int[]{13}, VehicleActionExecutor.t19cWindowCodes("all", "open"));
        assertArrayEquals(new int[]{14}, VehicleActionExecutor.t19cWindowCodes(null, "close"));
        assertArrayEquals(new int[]{1, 5}, VehicleActionExecutor.t19cWindowCodes("left", "open"));
        assertArrayEquals(new int[]{4, 8}, VehicleActionExecutor.t19cWindowCodes("right", "close"));
    }

    @Test
    public void ventIsComfortAll() {
        assertArrayEquals(new int[]{15}, VehicleActionExecutor.t19cWindowCodes("fl", "vent"));
        assertArrayEquals(new int[]{15}, VehicleActionExecutor.t19cWindowCodes("all", "vent"));
    }
}
