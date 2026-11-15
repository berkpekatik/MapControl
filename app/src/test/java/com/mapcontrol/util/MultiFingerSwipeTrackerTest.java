package com.mapcontrol.util;

import static org.junit.Assert.assertEquals;

import android.view.MotionEvent;

import com.mapcontrol.util.MultiFingerSwipeTracker.Result;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MultiFingerSwipeTrackerTest {

    private final List<Integer> codes = new ArrayList<>();
    private boolean accept = true;
    private final MultiFingerSwipeTracker tracker = new MultiFingerSwipeTracker((fingers, direction) -> {
        codes.add(SplitScreenGesture.code(fingers, direction));
        return accept;
    });

    private Result event(int action, int pointers, float x, float y) {
        return tracker.onEvent(action, pointers, x, y, 1f);
    }

    private void threeFingersDownAt(float x, float y) {
        event(MotionEvent.ACTION_DOWN, 1, x, y);
        event(MotionEvent.ACTION_POINTER_DOWN, 2, x, y);
        event(MotionEvent.ACTION_POINTER_DOWN, 3, x, y);
    }

    @Test
    public void recognisedSwipeFiresOnceAndSwallowsTheRest() {
        threeFingersDownAt(100, 100);
        assertEquals(Result.CANCEL_CHILDREN, event(MotionEvent.ACTION_MOVE, 3, 140, 100));
        assertEquals(Result.CONSUME, event(MotionEvent.ACTION_MOVE, 3, 200, 100));
        assertEquals(Result.CONSUME, event(MotionEvent.ACTION_POINTER_UP, 3, 200, 100));
        assertEquals(Result.CONSUME, event(MotionEvent.ACTION_UP, 1, 200, 100));
        assertEquals(1, codes.size());
        assertEquals(SplitScreenGesture.code(3, SplitScreenGesture.RIGHT), (int) codes.get(0));
        assertEquals(Result.PASS, event(MotionEvent.ACTION_DOWN, 1, 0, 0));
    }

    @Test
    public void belowThresholdKeepsWaiting() {
        threeFingersDownAt(100, 100);
        assertEquals(Result.PASS, event(MotionEvent.ACTION_MOVE, 3, 135, 122));
        assertEquals(0, codes.size());
    }

    @Test
    public void directionFollowsDominantAxis() {
        threeFingersDownAt(100, 100);
        event(MotionEvent.ACTION_MOVE, 3, 110, 70);
        assertEquals(SplitScreenGesture.code(3, SplitScreenGesture.UP), (int) codes.get(0));
    }

    @Test
    public void declinedSwipeLeavesTouchAloneAndIsNotRetried() {
        accept = false;
        threeFingersDownAt(100, 100);
        assertEquals(Result.PASS, event(MotionEvent.ACTION_MOVE, 3, 150, 100));
        assertEquals(Result.PASS, event(MotionEvent.ACTION_MOVE, 3, 220, 100));
        assertEquals(Result.PASS, event(MotionEvent.ACTION_UP, 1, 220, 100));
        assertEquals(1, codes.size());
    }

    @Test
    public void liftedFingerStopsMeasurement() {
        threeFingersDownAt(100, 100);
        event(MotionEvent.ACTION_POINTER_UP, 3, 100, 100);
        assertEquals(Result.PASS, event(MotionEvent.ACTION_MOVE, 2, 200, 100));
        assertEquals(0, codes.size());
    }

    @Test
    public void singleFingerAndFiveFingersAreIgnored() {
        event(MotionEvent.ACTION_DOWN, 1, 100, 100);
        assertEquals(Result.PASS, event(MotionEvent.ACTION_MOVE, 1, 300, 100));
        threeFingersDownAt(100, 100);
        event(MotionEvent.ACTION_POINTER_DOWN, 4, 100, 100);
        event(MotionEvent.ACTION_POINTER_DOWN, 5, 100, 100);
        assertEquals(Result.PASS, event(MotionEvent.ACTION_MOVE, 5, 300, 100));
        assertEquals(0, codes.size());
    }
}
