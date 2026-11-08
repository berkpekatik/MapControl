package com.mapcontrol.media;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NotificationAccessSelfGrantTest {
    private static final String ME = "com.frostnav/com.frostnav.media.LauncherMediaNotificationListener";
    private static final String MAPS = "com.google.android.apps.maps/com.google.android.apps.maps.MusicNotificationListenerService";

    @Test public void emptyOrNullListBecomesJustUs() {
        assertEquals(ME, LauncherMediaController.withListener(null, ME));
        assertEquals(ME, LauncherMediaController.withListener("", ME));
        assertEquals(ME, LauncherMediaController.withListener("null", ME));
    }

    @Test public void othersAreKeptAndWeAreAppended() {
        assertEquals(MAPS + ":" + ME, LauncherMediaController.withListener(MAPS, ME));
    }

    @Test public void alreadyPresentIsUnchanged() {
        String both = ME + ":" + MAPS;
        assertEquals(both, LauncherMediaController.withListener(both, ME));
    }
}
