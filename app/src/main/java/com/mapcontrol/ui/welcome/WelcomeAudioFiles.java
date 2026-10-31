package com.mapcontrol.ui.welcome;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Hoşgeldin sesi: USB taraması ve seçilen dosyanın uygulama klasörüne kopyası.
 */
public final class WelcomeAudioFiles {

    public static final int MAX_FILES = 100;

    private static final String[] EXTENSIONS = {
            ".mp3", ".wav", ".m4a", ".ogg", ".flac", ".aac", ".wma", ".amr"
    };

    private static final String[] MOUNT_POINTS = {
            "/storage/udisk",
            "/storage/udisk0",
            "/storage/udisk1",
            "/storage/usb",
            "/storage/usb0",
            "/storage/usb1",
            "/storage/usb2",
            "/mnt/usb_storage",
            "/mnt/usb"
    };

    private WelcomeAudioFiles() {
    }

    public static final class UsbScan {
        public final boolean present;
        public final List<File> files;

        UsbScan(boolean present, List<File> files) {
            this.present = present;
            this.files = files;
        }
    }

    public static UsbScan scanUsb(Context context) {
        Set<String> seen = new LinkedHashSet<>();
        List<File> roots = new ArrayList<>();
        addVolumeRoots(context, roots, seen);
        for (String path : MOUNT_POINTS) {
            addRoot(new File(path), roots, seen);
        }
        addMediaRwChildren(roots, seen);
        if (roots.isEmpty()) {
            return new UsbScan(false, Collections.emptyList());
        }
        List<File> audio = new ArrayList<>();
        Set<String> audioSeen = new LinkedHashSet<>();
        for (File root : roots) {
            collect(root, audio, audioSeen, 0);
            if (audio.size() >= MAX_FILES) {
                break;
            }
        }
        Collections.sort(audio, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return new UsbScan(true, audio);
    }

    public static List<File> scanDownloads() {
        List<File> audio = new ArrayList<>();
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir == null || !downloadDir.isDirectory()) {
            downloadDir = new File("/storage/emulated/0/Download");
            if (!downloadDir.isDirectory()) {
                downloadDir = new File("/sdcard/Download");
            }
        }
        collect(downloadDir, audio, new LinkedHashSet<>(), 1);
        Collections.sort(audio, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return audio;
    }

    public static File copyFile(Context context, File source) throws IOException {
        File store = storeDir(context);
        if (isInside(source, store)) {
            return source;
        }
        try (InputStream in = new FileInputStream(source)) {
            return copyStream(store, in, source.getName());
        }
    }

    /**
     * Üretilen mp3'ü welcome-audio altına yeni bir adla yazar. Önceki dosyalar durur.
     */
    public static File writeGenerated(Context context, InputStream in) throws IOException {
        File dir = storeDir(context);
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                .format(new java.util.Date());
        File dest = new File(dir, "mp3-" + stamp + ".mp3");
        int extra = 2;
        while (dest.exists()) {
            dest = new File(dir, "mp3-" + stamp + "-" + extra + ".mp3");
            extra++;
        }
        File tmp = new File(dir, dest.getName() + ".part");
        long total = 0;
        try (OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n == 0) {
                    continue;
                }
                out.write(buf, 0, n);
                total += n;
            }
        } catch (IOException e) {
            tmp.delete();
            throw e;
        }
        if (total <= 0) {
            tmp.delete();
            throw new IOException("empty");
        }
        if (!tmp.renameTo(dest)) {
            tmp.delete();
            throw new IOException("rename");
        }
        return dest;
    }

    public static File copyUri(Context context, Uri uri) throws IOException {
        String name = displayName(context, uri);
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("empty");
            }
            return copyStream(storeDir(context), in, name);
        }
    }

    private static void addVolumeRoots(Context context, List<File> roots, Set<String> seen) {
        StorageManager storage = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        if (storage == null) {
            return;
        }
        List<StorageVolume> volumes = storage.getStorageVolumes();
        if (volumes == null) {
            return;
        }
        for (StorageVolume volume : volumes) {
            if (volume == null || !volume.isRemovable()) {
                continue;
            }
            if (!Environment.MEDIA_MOUNTED.equals(volume.getState())
                    && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(volume.getState())) {
                continue;
            }
            addRoot(volumeDirectory(volume), roots, seen);
        }
    }

    private static File volumeDirectory(StorageVolume volume) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            File directory = volume.getDirectory();
            if (directory != null) {
                return directory;
            }
        }
        try {
            Method method = StorageVolume.class.getDeclaredMethod("getPath");
            method.setAccessible(true);
            Object path = method.invoke(volume);
            if (path instanceof String && !((String) path).isEmpty()) {
                return new File((String) path);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void addMediaRwChildren(List<File> roots, Set<String> seen) {
        File mediaRw = new File("/mnt/media_rw");
        File[] children = mediaRw.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory() && !child.getName().startsWith(".")) {
                addRoot(child, roots, seen);
            }
        }
    }

    private static void addRoot(File dir, List<File> roots, Set<String> seen) {
        if (dir == null || !dir.isDirectory() || isInternalStorage(dir)) {
            return;
        }
        String key;
        try {
            key = dir.getCanonicalPath();
        } catch (IOException e) {
            key = dir.getAbsolutePath();
        }
        if (seen.add(key)) {
            roots.add(dir);
        }
    }

    private static boolean isInternalStorage(File dir) {
        File primary = Environment.getExternalStorageDirectory();
        if (primary != null && isInside(dir, primary)) {
            return true;
        }
        String path = dir.getAbsolutePath();
        return path.startsWith("/storage/emulated") || path.startsWith("/storage/self");
    }

    private static void collect(File dir, List<File> out, Set<String> seen, int depth) {
        if (dir == null || !dir.isDirectory() || out.size() >= MAX_FILES) {
            return;
        }
        File[] children;
        try {
            children = dir.listFiles();
        } catch (Exception e) {
            return;
        }
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (out.size() >= MAX_FILES) {
                return;
            }
            String name = child.getName();
            if (name.startsWith(".")) {
                continue;
            }
            if (child.isDirectory()) {
                if (depth == 0 && !"android".equalsIgnoreCase(name)) {
                    collect(child, out, seen, 1);
                }
            } else if (isAudio(name) && seen.add(fileKey(child))) {
                out.add(child);
            }
        }
    }

    private static String fileKey(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    private static boolean isAudio(String name) {
        String lower = name.toLowerCase(Locale.US);
        for (String ext : EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static File storeDir(Context context) throws IOException {
        File dir = new File(context.getFilesDir(), "welcome-audio");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("store");
        }
        return dir;
    }

    private static File copyStream(File store, InputStream in, String displayName) throws IOException {
        String name = sanitize(displayName);
        File dest = new File(store, name);
        File tmp = new File(store, name + ".part");
        try (OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            tmp.delete();
            throw e;
        }
        File backup = new File(store, name + ".bak");
        if (dest.exists() && !dest.renameTo(backup)) {
            tmp.delete();
            throw new IOException("replace");
        }
        if (!tmp.renameTo(dest)) {
            tmp.delete();
            if (backup.exists()) {
                backup.renameTo(dest);
            }
            throw new IOException("rename");
        }
        if (backup.exists()) {
            backup.delete();
        }
        File[] existing = store.listFiles();
        if (existing != null) {
            for (File file : existing) {
                if (!file.equals(dest)) {
                    file.delete();
                }
            }
        }
        return dest;
    }

    private static String displayName(Context context, Uri uri) {
        String name = null;
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (Exception ignored) {
        }
        if (name == null || name.trim().isEmpty()) {
            return "welcome-sound";
        }
        return name;
    }

    private static String sanitize(String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.equals(".") || cleaned.equals("..")) {
            cleaned = "welcome-sound";
        }
        if (cleaned.length() > 80) {
            cleaned = cleaned.substring(cleaned.length() - 80);
        }
        if (cleaned.isEmpty()) {
            return "welcome-sound";
        }
        return cleaned;
    }

    private static boolean isSameFile(File a, File b) {
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (IOException e) {
            return a.getAbsolutePath().equals(b.getAbsolutePath());
        }
    }

    private static boolean isInside(File file, File dir) {
        try {
            String child = file.getCanonicalPath();
            String parent = dir.getCanonicalPath();
            return child.equals(parent) || child.startsWith(parent + File.separator);
        } catch (IOException e) {
            return false;
        }
    }
}
