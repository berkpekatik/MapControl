package com.mapcontrol.manager;

import static org.junit.Assert.assertEquals;

import com.mapcontrol.manager.WifiLinkHealth.Action;

import org.junit.Test;

public class WifiLinkHealthTest {

    private static final String KEY = "wlan0/172.20.10.1";

    @Test
    public void healthyLinkNeverActs() {
        WifiLinkHealth h = new WifiLinkHealth();
        for (int i = 0; i < 10; i++) {
            assertEquals(Action.NONE, h.onProbe(KEY, true, i * 30_000L));
        }
    }

    @Test
    public void singleFailureIsTolerated() {
        WifiLinkHealth h = new WifiLinkHealth();
        assertEquals(Action.NONE, h.onProbe(KEY, false, 0));
        assertEquals(Action.NONE, h.onProbe(KEY, true, 30_000));
        assertEquals(Action.NONE, h.onProbe(KEY, false, 60_000));
        assertEquals(Action.NONE, h.onProbe(KEY, true, 90_000));
    }

    @Test
    public void escalatesWithSettleTimeBetweenSteps() {
        WifiLinkHealth h = new WifiLinkHealth();
        assertEquals(Action.NONE, h.onProbe(KEY, false, 0));
        assertEquals(Action.REASSOCIATE, h.onProbe(KEY, false, 30_000));
        // 45 sn sakin bekleme
        assertEquals(Action.NONE, h.onProbe(KEY, false, 60_000));
        assertEquals(Action.RECONNECT, h.onProbe(KEY, false, 90_000));
        assertEquals(Action.NONE, h.onProbe(KEY, false, 120_000));
        assertEquals(Action.TOGGLE, h.onProbe(KEY, false, 150_000));
    }

    @Test
    public void toggleBacksOffUpToCap() {
        WifiLinkHealth h = new WifiLinkHealth();
        long t = 0;
        int toggles = 0;
        long lastToggle = -1;
        long maxGap = 0;
        for (int i = 0; i < 200; i++, t += 30_000) {
            if (h.onProbe(KEY, false, t) == Action.TOGGLE) {
                if (lastToggle >= 0) {
                    maxGap = Math.max(maxGap, t - lastToggle);
                }
                lastToggle = t;
                toggles++;
            }
        }
        // 100 dakikada sürekli kapat-aç yapılmaz; aralık tavana (10 dk) ulaşır ve onu (bir tur payıyla) aşmaz
        assertEquals(true, toggles < 20);
        assertEquals(true, maxGap >= WifiLinkHealth.TOGGLE_BACKOFF_MAX_MS);
        assertEquals(true, maxGap <= WifiLinkHealth.TOGGLE_BACKOFF_MAX_MS + 30_000);
    }

    @Test
    public void recoveryReportedOnceAndResets() {
        WifiLinkHealth h = new WifiLinkHealth();
        h.onProbe(KEY, false, 0);
        assertEquals(Action.REASSOCIATE, h.onProbe(KEY, false, 30_000));
        assertEquals(Action.RECOVERED, h.onProbe(KEY, true, 60_000));
        assertEquals(Action.NONE, h.onProbe(KEY, true, 90_000));
        // yeniden kopunca baştan (hafif adımdan) başlar
        h.onProbe(KEY, false, 120_000);
        assertEquals(Action.REASSOCIATE, h.onProbe(KEY, false, 150_000));
    }

    @Test
    public void newLinkResetsState() {
        WifiLinkHealth h = new WifiLinkHealth();
        h.onProbe(KEY, false, 0);
        assertEquals(Action.NONE, h.onProbe("wlan0/192.168.43.1", false, 30_000));
        assertEquals(Action.REASSOCIATE, h.onProbe("wlan0/192.168.43.1", false, 60_000));
    }

    @Test
    public void noWifiLinkResetsState() {
        WifiLinkHealth h = new WifiLinkHealth();
        h.onProbe(KEY, false, 0);
        assertEquals(Action.NONE, h.onProbe(null, false, 30_000));
        assertEquals(Action.NONE, h.onProbe(KEY, false, 60_000));
        assertEquals(Action.REASSOCIATE, h.onProbe(KEY, false, 90_000));
    }
}
