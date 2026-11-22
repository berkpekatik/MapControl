package com.mapcontrol.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

public class FileBrowserPolicyTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File storage;
    private File downloads;
    private File outside;
    private FileBrowserPolicy policy;

    @Before
    public void setUp() throws IOException {
        storage = tmp.newFolder("emulated0").getCanonicalFile();
        downloads = mkdir(new File(storage, "Download"));
        mkdir(new File(storage, "Android/data/com.secret"));
        mkdir(new File(storage, "Android/media"));
        outside = mkdir(tmp.newFolder("outside").getCanonicalFile());
        policy = new FileBrowserPolicy(storage, Collections.emptyList());
    }

    @Test
    public void dotDotCannotEscapeRoot() throws IOException {
        File secret = touch(new File(outside, "secret.txt"));
        assertNull(policy.resolve(downloads, new File(downloads, "../../outside/secret.txt")));
        assertNull(policy.resolve(downloads, secret));
        assertNull(policy.resolve(downloads, storage));
    }

    @Test
    public void symlinkCannotEscapeRoot() throws IOException {
        touch(new File(outside, "secret.txt"));
        File link = new File(downloads, "link");
        Files.createSymbolicLink(link.toPath(), outside.toPath());
        assertNull(policy.resolve(downloads, link));
        assertNull(policy.resolve(downloads, new File(link, "secret.txt")));
        assertFalse(policy.isListable(downloads, link, true));
    }

    @Test
    public void privateAppFoldersBlockedEvenInFreeMode() {
        assertNull(policy.resolve(storage, new File(storage, "Android/data")));
        assertNull(policy.resolve(storage, new File(storage, "Android/data/com.secret")));
        assertNull(policy.resolve(storage, new File(storage, "Android/obb")));
        assertTrue(policy.isListable(storage, new File(storage, "Android/media"), true));
    }

    @Test
    public void systemPartitionsBlocked() {
        assertTrue(policy.isBlocked(new File("/data")));
        assertTrue(policy.isBlocked(new File("/data/data/com.mapcontrol/files")));
        assertTrue(policy.isBlocked(new File("/system/priv-app")));
        assertTrue(policy.isBlocked(new File("/proc/self")));
        assertFalse(policy.isBlocked(new File("/database")));
        assertNull(policy.resolve(new File("/data"), new File("/data/local/tmp")));
    }

    @Test
    public void extraBlockedFolder() throws IOException {
        File own = mkdir(new File(storage, "own"));
        FileBrowserPolicy p = new FileBrowserPolicy(storage, Collections.singletonList(own));
        assertNull(p.resolve(storage, new File(own, "x")));
    }

    @Test
    public void hiddenEntriesOnlyInFreeMode() throws IOException {
        File hidden = touch(new File(downloads, ".nomedia"));
        assertFalse(policy.isListable(downloads, hidden, false));
        assertTrue(policy.isListable(downloads, hidden, true));
    }

    @Test
    public void protectedItemsCannotBeDeleted() throws IOException {
        assertFalse(policy.canDelete(downloads, downloads));
        assertFalse(policy.canDelete(storage, storage));
        assertFalse(policy.canDelete(storage, downloads));
        assertFalse(policy.canDelete(storage, mkdir(new File(storage, "DCIM"))));
        assertFalse(policy.canDelete(storage, mkdir(new File(storage, "SVWallpaper"))));
        assertFalse(policy.canDelete(storage, new File(storage, "Android")));
        assertFalse(policy.canDelete(storage, new File(storage, "Android/media")));
        assertTrue(policy.canDelete(storage, mkdir(new File(storage, "MyStuff"))));
        assertTrue(policy.canDelete(downloads, touch(new File(downloads, "a.apk"))));
    }

    @Test
    public void deleteRemovesTreeButNotSymlinkTarget() throws IOException {
        File keep = touch(new File(outside, "keep.txt"));
        File folder = mkdir(new File(downloads, "folder"));
        touch(new File(folder, "inner/file.bin"));
        Files.createSymbolicLink(new File(folder, "link").toPath(), outside.toPath());

        assertTrue(policy.delete(downloads, folder));
        assertFalse(folder.exists());
        assertTrue(keep.exists());
        assertTrue(downloads.exists());
    }

    @Test
    public void deleteRefusesOutsideRoot() throws IOException {
        File keep = touch(new File(outside, "keep.txt"));
        assertFalse(policy.delete(downloads, keep));
        assertTrue(keep.exists());
    }

    @Test
    public void uniqueTargetAndSanitize() throws IOException {
        touch(new File(downloads, "app.apk"));
        touch(new File(downloads, "app (1).apk"));
        assertEquals("app (2).apk", FileBrowserPolicy.uniqueTarget(downloads, "app.apk").getName());
        assertEquals("_.._x", FileBrowserPolicy.sanitizeName("/../x"));
        assertEquals("file", FileBrowserPolicy.sanitizeName(".."));
        assertEquals(downloads, FileBrowserPolicy.uniqueTarget(downloads, "../evil").getParentFile());
    }

    @Test
    public void kinds() {
        assertEquals(FileKind.APK, FileKind.of("Waze.APK", false));
        assertEquals(FileKind.FOLDER, FileKind.of("x.apk", true));
        assertEquals(FileKind.IMAGE, FileKind.of("a.jpeg", false));
        assertEquals(FileKind.VIDEO, FileKind.of("clip.mp4", false));
        assertEquals(FileKind.AUDIO, FileKind.of("song.mp3", false));
        assertEquals(FileKind.ARCHIVE, FileKind.of("pack.xapk", false));
        assertEquals(FileKind.OTHER, FileKind.of("noext", false));
        assertEquals(FileKind.OTHER, FileKind.of("dot.", false));
    }

    private static File mkdir(File f) {
        f.mkdirs();
        return f;
    }

    private static File touch(File f) throws IOException {
        f.getParentFile().mkdirs();
        new FileOutputStream(f).close();
        return f;
    }
}
