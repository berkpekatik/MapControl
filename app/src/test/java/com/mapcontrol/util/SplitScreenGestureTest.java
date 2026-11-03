package com.mapcontrol.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SplitScreenGestureTest {

    /** MyCar M6.onReceive tablosu: parmak sayısı * 100 + yön kodu. */
    @Test
    public void codesMatchVehicleBroadcastTable() {
        assertEquals(203, SplitScreenGesture.code(2, SplitScreenGesture.UP));
        assertEquals(202, SplitScreenGesture.code(2, SplitScreenGesture.DOWN));
        assertEquals(200, SplitScreenGesture.code(2, SplitScreenGesture.LEFT));
        assertEquals(201, SplitScreenGesture.code(2, SplitScreenGesture.RIGHT));
        assertEquals(303, SplitScreenGesture.code(3, SplitScreenGesture.UP));
        assertEquals(302, SplitScreenGesture.code(3, SplitScreenGesture.DOWN));
        assertEquals(300, SplitScreenGesture.code(3, SplitScreenGesture.LEFT));
        assertEquals(301, SplitScreenGesture.code(3, SplitScreenGesture.RIGHT));
        assertEquals(403, SplitScreenGesture.code(4, SplitScreenGesture.UP));
        assertEquals(402, SplitScreenGesture.code(4, SplitScreenGesture.DOWN));
        assertEquals(400, SplitScreenGesture.code(4, SplitScreenGesture.LEFT));
        assertEquals(401, SplitScreenGesture.code(4, SplitScreenGesture.RIGHT));
    }

    @Test
    public void codesRoundTrip() {
        for (int fingers : SplitScreenGesture.FINGER_COUNTS) {
            for (int direction : SplitScreenGesture.DIRECTIONS) {
                int code = SplitScreenGesture.code(fingers, direction);
                assertTrue(SplitScreenGesture.isValid(code));
                assertEquals(fingers, SplitScreenGesture.fingersOf(code));
                assertEquals(direction, SplitScreenGesture.directionOf(code));
            }
        }
    }

    @Test
    public void invalidCodesAreRejected() {
        assertFalse(SplitScreenGesture.isValid(SplitScreenStore.GESTURE_OFF));
        assertFalse(SplitScreenGesture.isValid(204));
        assertFalse(SplitScreenGesture.isValid(500));
        assertFalse(SplitScreenGesture.isValid(100));
    }

    @Test
    public void mapSwipeKeepsTwoFingerLeftAndRight() {
        assertTrue(SplitScreenGesture.isReservedByMapSwipe(200));
        assertTrue(SplitScreenGesture.isReservedByMapSwipe(201));
        assertFalse(SplitScreenGesture.isReservedByMapSwipe(203));
        assertFalse(SplitScreenGesture.isReservedByMapSwipe(300));
    }

    @Test
    public void ratioIdsMatchMyCarAndDefaultToHalf() {
        assertEquals(SplitScreenLauncher.Ratio.LEFT_THIRD, SplitScreenLauncher.Ratio.fromId("left_third"));
        assertEquals(SplitScreenLauncher.Ratio.HALF, SplitScreenLauncher.Ratio.fromId("half"));
        assertEquals(SplitScreenLauncher.Ratio.RIGHT_THIRD, SplitScreenLauncher.Ratio.fromId("right_third"));
        assertEquals(SplitScreenLauncher.Ratio.HALF, SplitScreenLauncher.Ratio.fromId(null));
        assertEquals(SplitScreenLauncher.Ratio.HALF, SplitScreenLauncher.Ratio.fromId("bogus"));
    }
}
