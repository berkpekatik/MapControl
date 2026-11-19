package com.mapcontrol.vehicle;

import org.junit.Test;
import static org.junit.Assert.*;

public class JaecooJ7FeaturesTest {
    @Test public void explicitJ7SelectionEnablesNewFeatures() {
        assertTrue(JaecooJ7Features.isJ7Selection(2));
    }

    @Test public void automaticAndOtherSelectionsDoNotEnableJ7Features() {
        for (int index : new int[] {-1, 0, 1, 3, 4, Integer.MAX_VALUE}) {
            assertFalse(JaecooJ7Features.isJ7Selection(index));
        }
    }
}
