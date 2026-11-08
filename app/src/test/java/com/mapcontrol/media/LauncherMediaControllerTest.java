package com.mapcontrol.media;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/** Kapak kararlılığı: aynı parçada tekrar gelen üstveri kapağı sıfırlamamalı. */
public class LauncherMediaControllerTest {
    private static final LauncherMediaController.SameContent<String> EQ = String::equals;

    @Test
    public void sameTrackSameContentKeepsOldInstance() {
        String old = new String("kapak");
        String fresh = new String("kapak");
        String k = LauncherMediaController.trackKey("pkg", "A", "B", "C");
        assertSame(old, LauncherMediaController.stableArtwork(k, old, k, fresh, EQ));
    }

    @Test
    public void sameTrackMissingArtKeepsOld() {
        String old = "kapak";
        String k = LauncherMediaController.trackKey("pkg", "A", "B", "C");
        assertSame(old, LauncherMediaController.stableArtwork(k, old, k, null, EQ));
    }

    @Test
    public void sameTrackDifferentContentTakesNew() {
        String k = LauncherMediaController.trackKey("pkg", "A", "B", "C");
        String fresh = "yeni";
        assertSame(fresh, LauncherMediaController.stableArtwork(k, "eski", k, fresh, EQ));
    }

    @Test
    public void trackChangeTakesNewEvenIfIdentical() {
        String fresh = new String("kapak");
        String k1 = LauncherMediaController.trackKey("pkg", "A", "B", "C");
        String k2 = LauncherMediaController.trackKey("pkg", "X", "B", "C");
        assertSame(fresh, LauncherMediaController.stableArtwork(k1, "kapak", k2, fresh, EQ));
    }

    @Test
    public void trackChangeToNullClearsArt() {
        String k1 = LauncherMediaController.trackKey("pkg", "A", "B", "C");
        String k2 = LauncherMediaController.trackKey("pkg", "X", "B", "C");
        assertNull(LauncherMediaController.stableArtwork(k1, "kapak", k2, null, EQ));
    }

    @Test
    public void anonymousTracksDoNotKeepOldArt() {
        // Boş üstverili kaynak: parça tanınamaz, önceki kapak yeni (kapaksız) parçaya taşınmaz.
        String k = LauncherMediaController.artworkKey("bt", null, null, null, 0L, null);
        assertNull(k);
        assertNull(LauncherMediaController.stableArtwork(k, "kapak", k, null, EQ));
    }

    @Test
    public void sameTextDifferentDurationIsAnotherTrack() {
        String k1 = LauncherMediaController.artworkKey("bt", "Radyo", null, null, 180_000L, null);
        String k2 = LauncherMediaController.artworkKey("bt", "Radyo", null, null, 200_000L, null);
        assertNull(LauncherMediaController.stableArtwork(k1, "kapak", k2, null, EQ));
        // Aynı parça yeniden gönderildi (BT ~5 sn): kapak korunur.
        assertSame("kapak", LauncherMediaController.stableArtwork(k1, "kapak", k1, null, EQ));
    }
}
