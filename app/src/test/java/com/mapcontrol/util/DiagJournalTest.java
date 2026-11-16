package com.mapcontrol.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiagJournalTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void admitLimitsPerMinuteAndReportsDropped() {
        Map<String, long[]> state = new HashMap<>();
        assertEquals(0, DiagJournal.admit(state, "a", 1, 2));
        assertEquals(0, DiagJournal.admit(state, "a", 1, 2));
        assertEquals(-1, DiagJournal.admit(state, "a", 1, 2));
        assertEquals(-1, DiagJournal.admit(state, "a", 1, 2));
        // Başka alan etkilenmez.
        assertEquals(0, DiagJournal.admit(state, "b", 1, 2));
        // Yeni dakikada ilk satır bastırılanları bildirir, sonra sıfırlanır.
        assertEquals(2, DiagJournal.admit(state, "a", 2, 2));
        assertEquals(0, DiagJournal.admit(state, "a", 2, 2));
    }

    @Test
    public void formatFoldsNewlinesAndClips() {
        String line = DiagJournal.format(0L, 42, "kurulum", "a\nb\r\nc");
        assertTrue(line.endsWith(" 42 [kurulum] a|b|c"));
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < DiagJournal.MAX_LINE_CHARS + 50; i++) {
            big.append('x');
        }
        String clipped = DiagJournal.format(0L, 1, "x", big.toString());
        assertTrue(clipped.endsWith("…"));
        assertFalse(clipped.contains(big.toString()));
    }

    @Test
    public void appendRotatesToPreviousFile() throws Exception {
        File dir = new File(tmp.getRoot(), "diag");
        DiagJournal.append(dir, "first-line-0123456789", 40);
        DiagJournal.append(dir, "second-line-0123456789", 40);
        List<File> files = DiagJournal.files(dir);
        assertEquals(2, files.size());
        assertEquals(DiagJournal.PREVIOUS, files.get(0).getName());
        assertEquals("first-line-0123456789\n", read(files.get(0)));
        assertEquals("second-line-0123456789\n", read(files.get(1)));
        // Üçüncü dönüşte en eski silinir.
        DiagJournal.append(dir, "third-line-0123456789", 40);
        files = DiagJournal.files(dir);
        assertEquals("second-line-0123456789\n", read(files.get(0)));
        assertEquals("third-line-0123456789\n", read(files.get(1)));
    }

    @Test
    public void areaLimits() {
        assertEquals(6, DiagJournal.limitFor("gps"));
        assertEquals(DiagJournal.AREA_LIMIT_PER_MIN, DiagJournal.limitFor("kurulum"));
    }

    private static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
