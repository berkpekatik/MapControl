package com.mapcontrol.trip;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TripBoxQueueTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final long NOW = 1_761_800_000_000L;

    private TripBoxQueue queue() {
        return new TripBoxQueue(new File(tmp.getRoot(), "q"));
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void storedPacketSurvivesAndIsRemovedOnlyByItsOwnVersion() {
        TripBoxQueue q = queue();
        assertTrue(q.put("t1", NOW, bytes("ilk")));
        // Uygulama yeniden başladı: aynı klasörden okunur.
        List<TripBoxQueue.Item> items = queue().pending(NOW + 1);
        assertEquals(1, items.size());
        assertEquals("t1", items.get(0).tripId);
        assertEquals(NOW, items.get(0).version);
        assertArrayEquals(bytes("ilk"), items.get(0).payload);

        q.remove("t1", NOW - 5); // başka sürüm: dokunmaz
        assertEquals(1, q.size());
        q.remove("t1", NOW);
        assertEquals(0, q.size());
    }

    @Test
    public void newerVersionReplacesOlderAndOlderNeverOverwritesNewer() {
        TripBoxQueue q = queue();
        q.put("t1", NOW, bytes("kapanış"));
        assertTrue(q.put("t1", NOW + 60_000, bytes("kesin")));
        assertFalse(q.put("t1", NOW + 10, bytes("eski")));

        List<TripBoxQueue.Item> items = q.pending(NOW + 60_001);
        assertEquals(1, items.size());
        assertArrayEquals(bytes("kesin"), items.get(0).payload);

        // Kapanıştaki ilk gönderim bitince yalnızca kendi sürümünü siler; yenisi kalır.
        q.remove("t1", NOW);
        assertEquals(1, q.size());
    }

    @Test
    public void pendingIsOldestFirstAndDropsExpired() {
        TripBoxQueue q = queue();
        q.put("t3", NOW + 3, bytes("c"));
        q.put("t1", NOW + 1, bytes("a"));
        q.put("t2", NOW + 2, bytes("b"));
        q.put("t0", NOW - TripBoxQueue.MAX_AGE_MS - 1, bytes("bayat"));

        List<TripBoxQueue.Item> items = q.pending(NOW + 4);
        assertEquals(3, items.size());
        assertEquals("t1", items.get(0).tripId);
        assertEquals("t2", items.get(1).tripId);
        assertEquals("t3", items.get(2).tripId);
        assertEquals(3, q.size()); // bayat paket silindi
    }

    @Test
    public void keepsAtMostMaxItemsDroppingTheOldest() {
        TripBoxQueue q = queue();
        for (int i = 0; i < TripBoxQueue.MAX_ITEMS + 5; i++) {
            q.put("t" + i, NOW + i, bytes("p" + i));
        }
        List<TripBoxQueue.Item> items = q.pending(NOW + 1_000);
        assertEquals(TripBoxQueue.MAX_ITEMS, items.size());
        assertEquals("t5", items.get(0).tripId);
    }

    @Test
    public void rejectsIdsThatAreNotSafeFileNames() {
        TripBoxQueue q = queue();
        assertFalse(q.put("../x", NOW, bytes("a")));
        assertFalse(q.put("", NOW, bytes("a")));
        assertFalse(q.put(null, NOW, bytes("a")));
        assertEquals(0, q.size());
    }

    @Test
    public void httpOutcomeKeepsOnlyWhatARetryCanFix() {
        assertEquals(TripBoxUploader.Outcome.SENT, TripBoxUploader.outcomeFor(200));
        assertEquals(TripBoxUploader.Outcome.SENT, TripBoxUploader.outcomeFor(204));
        assertEquals(TripBoxUploader.Outcome.KEEP, TripBoxUploader.outcomeFor(401));
        assertEquals(TripBoxUploader.Outcome.KEEP, TripBoxUploader.outcomeFor(429));
        assertEquals(TripBoxUploader.Outcome.KEEP, TripBoxUploader.outcomeFor(503));
        assertEquals(TripBoxUploader.Outcome.DROP, TripBoxUploader.outcomeFor(404)); // araç hesapta yok
        assertEquals(TripBoxUploader.Outcome.DROP, TripBoxUploader.outcomeFor(413)); // çok büyük
        assertEquals(TripBoxUploader.Outcome.DROP, TripBoxUploader.outcomeFor(400));
    }
}
