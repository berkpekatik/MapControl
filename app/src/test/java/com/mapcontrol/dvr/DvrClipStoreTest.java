package com.mapcontrol.dvr;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DvrClipStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File write(File dir, String name, int bytes) throws IOException {
        File f = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(new byte[bytes]);
        }
        return f;
    }

    private static List<String> bases(List<DvrClipStore.Clip> clips) {
        List<String> out = new ArrayList<>();
        for (DvrClipStore.Clip c : clips) out.add(c.base);
        return out;
    }

    private File usbDir() throws IOException {
        File dir = tmp.newFolder("MapControl");
        write(dir, "s_2026-01-01_10-00-00.mp4", 100);        // eski, id'siz
        write(dir, "s_2026-01-02_10-00-00_id_2.mp4", 20);
        write(dir, "s_2026-01-02_10-03-00_id_3.mp4", 30);
        write(dir, "s_2026-01-02_09-57-00_id_1.mp4", 10);
        write(dir, "notlar.txt", 5);                         // kullanici dosyasi
        File export = new File(dir, "Klipler");
        export.mkdirs();
        write(export, "s_2025-12-31_10-00-00_id_9.mp4", 50); // alt klasor
        return dir;
    }

    @Test
    public void idOnlyDeletionSkipsIdlessAndForeignFilesOldestIdFirst() throws IOException {
        File dir = usbDir();
        List<DvrClipStore.Clip> order =
                DvrClipStore.deletionOrder(DvrClipStore.list(dir, null), true);
        assertEquals(java.util.Arrays.asList(
                "2026-01-02_09-57-00_id_1",
                "2026-01-02_10-00-00_id_2",
                "2026-01-02_10-03-00_id_3"), bases(order));
    }

    @Test
    public void internalDeletionStillPutsIdlessFirst() throws IOException {
        File dir = usbDir();
        List<DvrClipStore.Clip> order =
                DvrClipStore.deletionOrder(DvrClipStore.list(dir, null), false);
        assertEquals("2026-01-01_10-00-00", order.get(0).base);
        assertEquals(4, order.size());
    }

    @Test
    public void activeClipIsNeverCandidate() throws IOException {
        File dir = usbDir();
        Set<String> active = Collections.singleton("2026-01-02_09-57-00_id_1");
        List<DvrClipStore.Clip> order =
                DvrClipStore.deletionOrder(DvrClipStore.list(dir, active), true);
        assertEquals(java.util.Arrays.asList(
                "2026-01-02_10-00-00_id_2",
                "2026-01-02_10-03-00_id_3"), bases(order));
    }

    @Test
    public void reclaimableCountsOnlyIdClipsAndNextIdContinues() throws IOException {
        File dir = usbDir();
        assertEquals(60, DvrClipStore.reclaimableBytes(dir, null));
        assertEquals(4, DvrClipStore.nextId(dir));
    }
}
