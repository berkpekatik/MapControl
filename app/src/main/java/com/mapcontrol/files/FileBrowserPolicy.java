package com.mapcontrol.files;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Dosya gezgininin güvenlik sınırı. Uygulama MANAGE_EXTERNAL_STORAGE ile tüm paylaşımlı depolamayı
 * görebildiği için "Android ne izin veriyorsa" yaklaşımı yetmez; her listeleme, açma, kopyalama ve
 * silme buradan geçer.
 *
 * <ul>
 *   <li>Her yol kanonik hale getirilir (symlink ve {@code ..} çözülür) ve seçili kökün içinde kalmalıdır.</li>
 *   <li>Sistem bölümleri ({@code /data}, {@code /system}, {@code /proc} ...) ve uygulamaların özel
 *       klasörleri ({@code Android/data}, {@code Android/obb}) serbest gezinmede bile kapalıdır.</li>
 *   <li>Kökün kendisi, {@code Android/} ağacı ve depolama kökündeki standart klasörler (DCIM, Download,
 *       launcher'ın duvar kağıdı klasörleri ...) silinemez.</li>
 *   <li>Silme symlink'leri takip etmez: bağlantının kendisi silinir, hedefi değil.</li>
 * </ul>
 *
 * Android'e bağımlı değildir; birim testlerle doğrulanır.
 */
public final class FileBrowserPolicy {

    /** Hiçbir modda gezilemeyen bölümler (kanonik yol öneki). */
    private static final List<String> SYSTEM_PREFIXES = Collections.unmodifiableList(Arrays.asList(
            "/data", "/system", "/system_ext", "/vendor", "/product", "/odm", "/apex", "/proc", "/sys",
            "/dev", "/cache", "/config", "/metadata", "/persist", "/mnt/secure", "/mnt/asec", "/mnt/obb",
            "/acct", "/sbin", "/bin", "/etc", "/linkerconfig", "/debug_ramdisk"));

    /** Depolama kökünde silinemeyen klasörler (küçük harf). Launcher duvar kağıdı klasörleri de dahil. */
    private static final Set<String> PROTECTED_TOP_LEVEL = new HashSet<>(Arrays.asList(
            "android", "dcim", "download", "downloads", "music", "pictures", "movies", "documents",
            "alarms", "notifications", "podcasts", "ringtones", "audiobooks", "recordings",
            "svwallpaper", "wallpapers"));

    @Nullable
    private final File storageRoot;
    private final List<File> blocked = new ArrayList<>();

    /**
     * @param storageRoot  birincil paylaşımlı depolama ({@code /storage/emulated/0}); bilinmiyorsa null
     * @param extraBlocked ayrıca kapatılacak klasörler (ör. uygulamanın kendi veri klasörü)
     */
    public FileBrowserPolicy(@Nullable File storageRoot, @NonNull Collection<File> extraBlocked) {
        this.storageRoot = storageRoot != null ? canonical(storageRoot) : null;
        if (this.storageRoot != null) {
            blocked.add(new File(this.storageRoot, "Android/data"));
            blocked.add(new File(this.storageRoot, "Android/obb"));
        }
        for (File f : extraBlocked) {
            File c = f != null ? canonical(f) : null;
            if (c != null) {
                blocked.add(c);
            }
        }
    }

    @Nullable
    public static File canonical(@Nullable File f) {
        if (f == null) {
            return null;
        }
        try {
            return f.getCanonicalFile();
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    /** İki yol da kanonik olmalı. */
    public static boolean isInsideOrSame(@NonNull File child, @NonNull File parent) {
        String c = child.getPath();
        String p = parent.getPath();
        if (c.equals(p)) {
            return true;
        }
        return c.startsWith(p.endsWith("/") ? p : p + "/");
    }

    /** Kanonik yol sistem bölümünde ya da kapalı bir klasörde mi? */
    public boolean isBlocked(@NonNull File canonicalPath) {
        String path = canonicalPath.getPath();
        for (String prefix : SYSTEM_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        for (File b : blocked) {
            if (isInsideOrSame(canonicalPath, b)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code candidate}'in kanonik halini döndürür; kökün dışına çıkıyorsa ya da kapalıysa null.
     *
     * @param rootCanonical seçili kökün kanonik hali
     */
    @Nullable
    public File resolve(@NonNull File rootCanonical, @Nullable File candidate) {
        File c = canonical(candidate);
        if (c == null || isBlocked(rootCanonical) || !isInsideOrSame(c, rootCanonical) || isBlocked(c)) {
            return null;
        }
        return c;
    }

    /** Listede gösterilsin mi? Güvenli modda gizli (nokta ile başlayan) öğeler saklanır. */
    public boolean isListable(@NonNull File rootCanonical, @NonNull File entry, boolean freeMode) {
        if (!freeMode && entry.getName().startsWith(".")) {
            return false;
        }
        return resolve(rootCanonical, entry) != null;
    }

    public boolean canDelete(@NonNull File rootCanonical, @NonNull File target) {
        File c = resolve(rootCanonical, target);
        if (c == null || c.equals(rootCanonical)) {
            return false;
        }
        if (storageRoot != null) {
            if (c.equals(storageRoot) || isInsideOrSame(c, new File(storageRoot, "Android"))) {
                return false;
            }
            File parent = c.getParentFile();
            if (parent != null && parent.equals(storageRoot)
                    && PROTECTED_TOP_LEVEL.contains(c.getName().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    /** Dosyayı ya da klasörü (içiyle) siler. Kurallara takılırsa hiçbir şeye dokunmaz. */
    public boolean delete(@NonNull File rootCanonical, @NonNull File target) {
        if (!canDelete(rootCanonical, target)) {
            return false;
        }
        File c = canonical(target);
        return c != null && deleteTree(c);
    }

    private static boolean deleteTree(@NonNull File f) {
        boolean ok = true;
        if (!Files.isSymbolicLink(f.toPath()) && f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    ok &= deleteTree(child);
                }
            }
        }
        return f.delete() && ok;
    }

    /** Dosya adındaki yol ayraçlarını ve kontrol karakterlerini temizler. */
    @NonNull
    public static String sanitizeName(@Nullable String name) {
        String n = name == null ? "" : name.replaceAll("[/\\\\\\x00-\\x1f]", "_").trim();
        if (n.isEmpty() || n.equals(".") || n.equals("..")) {
            return "file";
        }
        return n;
    }

    /** {@code dir} içinde kullanılmayan bir hedef: "ad.apk", "ad (1).apk", "ad (2).apk" ... */
    @NonNull
    public static File uniqueTarget(@NonNull File dir, @Nullable String name) {
        String clean = sanitizeName(name);
        File file = new File(dir, clean);
        int dot = clean.lastIndexOf('.');
        String base = dot > 0 ? clean.substring(0, dot) : clean;
        String ext = dot > 0 ? clean.substring(dot) : "";
        for (int i = 1; file.exists(); i++) {
            file = new File(dir, base + " (" + i + ")" + ext);
        }
        return file;
    }
}
