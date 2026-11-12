package com.mapcontrol.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class MainLoopShieldTest {

    private static final String DENIED =
            "Not allowed to start service Intent { act=action.desaysv.ivi.vds.bt.SERVICE pkg=com.desaysv.ivi.vds.bt }"
                    + " without permission not exported from uid 1000";

    private static Throwable withFrames(Throwable t, StackTraceElement... frames) {
        t.setStackTrace(frames);
        return t;
    }

    private static StackTraceElement frame(String cls, String method) {
        return new StackTraceElement(cls, method, "X.java", 1);
    }

    @Before
    public void reset() {
        MainLoopShield.resetForTest();
    }

    @Test
    public void swallowsOemBindDenial() {
        Throwable t = withFrames(new SecurityException(DENIED),
                frame("android.app.ContextImpl", "startServiceCommon"),
                frame("com.desaysv.ivi.vdb.client.bind.VDConnector", "bindService"),
                frame("com.desaysv.ivi.vdb.client.bind.VDConnector$1", "handleMessage"));
        assertTrue(MainLoopShield.isVdBindDenied(t));
    }

    @Test
    public void keepsSecurityExceptionFromOurCode() {
        Throwable t = withFrames(new SecurityException(DENIED),
                frame("android.app.ContextImpl", "startServiceCommon"),
                frame("com.mapcontrol.manager.Foo", "start"));
        assertFalse(MainLoopShield.isVdBindDenied(t));
    }

    @Test
    public void keepsOtherErrorsFromVdConnector() {
        Throwable npe = withFrames(new NullPointerException("x"),
                frame("com.desaysv.ivi.vdb.client.bind.VDConnector", "bindService"));
        Throwable otherSec = withFrames(new SecurityException("Permission Denial"),
                frame("com.desaysv.ivi.vdb.client.bind.VDConnector", "bindService"));
        assertFalse(MainLoopShield.isVdBindDenied(npe));
        assertFalse(MainLoopShield.isVdBindDenied(otherSec));
    }

    @Test
    public void rateLimitLetsLoopsCrash() {
        long now = 1_000_000L;
        for (int i = 0; i < MainLoopShield.MAX_PER_MINUTE; i++) {
            assertTrue(MainLoopShield.allow(now + i));
        }
        assertFalse(MainLoopShield.allow(now + 100));
        assertTrue(MainLoopShield.allow(now + 61_000L));
    }
}
