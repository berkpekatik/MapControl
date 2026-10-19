package com.mapcontrol.util;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SecondaryDisplaySelectorTest {
    @Test public void noDisplayIsNotAssumedToBeTwo() {
        assertEquals(-1, SecondaryDisplaySelector.select(null));
        assertEquals(-1, SecondaryDisplaySelector.select(new int[]{}));
        assertEquals(-1, SecondaryDisplaySelector.select(new int[]{0, -1}));
    }
    @Test public void usesActualSecondaryId() {
        assertEquals(7, SecondaryDisplaySelector.select(new int[]{0, 7}));
    }
    @Test public void ambiguityBlocksSelection() {
        assertEquals(-1, SecondaryDisplaySelector.select(new int[]{0, 2, 7}));
    }
    @Test public void duplicateDoesNotCreateAmbiguity() {
        assertEquals(7, SecondaryDisplaySelector.select(new int[]{7, 0, 7}));
    }
}
