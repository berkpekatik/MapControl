package com.mapcontrol.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ServerGenerationTest {

    @Test
    public void newGenerationIsCurrent() {
        ServerGeneration g = new ServerGeneration();
        int a = g.next();
        assertTrue(g.isCurrent(a));
    }

    @Test
    public void restartMakesOldGenerationStale() {
        ServerGeneration g = new ServerGeneration();
        int old = g.next();
        int fresh = g.next();
        assertFalse("geç biten eski sunucu yeni durumu ezmemeli", g.isCurrent(old));
        assertTrue(g.isCurrent(fresh));
    }

    @Test
    public void numbersIncrease() {
        ServerGeneration g = new ServerGeneration();
        assertEquals(g.next() + 1, g.next());
    }
}
