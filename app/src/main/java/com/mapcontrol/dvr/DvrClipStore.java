package com.mapcontrol.dvr;

import android.os.StatFs;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kayit klasorundeki MP4'leri kliplere gruplar (ayni zaman damgali 4 aci tek klip)
 * ve saklama kurallarini uygular: bos alan rezervi korunamazsa en kucuk klip id'sinden
 * (en eski) silme. Klip adlari zaman damgasi + "_id_N" icerir; id her yeni klipte artar.
 * Yazilmakta olan klip (exclude) hicbir zaman listelenmez/silinmez.
 */
public final class DvrClipStore {
    private static final String TAG = "DvrClipStore";
    private static final String TIME = "(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})";
    /**
     * f_m_/r_m_/l_m_/b_m_ (ayri aci) veya s_ (tek dosya) + zaman + istege bagli _id_N.
     * Id'siz (eski/USB) dosyalar id 0 sayilir ve silmede ilk sirada gider.
     */
    private static final Pattern NAME = Pattern.compile(
            "^(?:([frlb])_m|s)_" + TIME + "(?:" + CameraRecorder.ID_TAG + "(\\d{1,18}))?\\.mp4$");
    /** Eski adlandirma: DVR_zaman[_ON|_SAG|_SOL|_ARKA]. */
    private static final Pattern LEGACY_NAME = Pattern.compile("^DVR_" + TIME + "(?:_([A-Z]+))?\\.mp4$");

    private DvrClipStore() {
    }

    /** Ayni anda kaydedilmis dosya grubu. camFiles indeksi CameraRecorder.CAM_SUFFIX sirasidir. */
    public static final class Clip {
        /** Ayni klibin dosyalarini birlestiren anahtar: zaman[_id_N]. */
        public final String base;
        public final long timeMs;
        /** Klip sirasi (0: id'siz eski klip). Silme en kucuk id'den baslar. */
        public final long id;
        public final File[] camFiles = new File[CameraRecorder.CAM_SUFFIX.length];
        public File mosaic;
        public long bytes;

        Clip(String base, long timeMs, long id) {
            this.base = base;
            this.timeMs = timeMs;
            this.id = id;
        }

        public List<File> files() {
            List<File> out = new ArrayList<>();
            if (mosaic != null) out.add(mosaic);
            for (File f : camFiles) {
                if (f != null) out.add(f);
            }
            return out;
        }

        /** Tek dosya (Performans modu / mozaik) kaydi mi. */
        public boolean isSingle() {
            return mosaic != null;
        }

        /** Oynatma icin ilk mevcut dosya. */
        public File primary() {
            if (mosaic != null) return mosaic;
            for (File f : camFiles) {
                if (f != null) return f;
            }
            return null;
        }
    }

    /** Ayni klibin dosyalarini birlestiren anahtar (zaman[_id_N]); taninmayan ad icin null. */
    public static String baseOf(File file) {
        if (file == null) return null;
        Matcher m = NAME.matcher(file.getName());
        if (m.matches()) return m.group(3) != null ? m.group(2) + CameraRecorder.ID_TAG + m.group(3) : m.group(2);
        m = LEGACY_NAME.matcher(file.getName());
        return m.matches() ? m.group(1) : null;
    }

    /** Dosya adindaki klip id'si; id'siz veya taninmayan ad icin 0. */
    private static long idOf(String name) {
        Matcher m = NAME.matcher(name);
        if (m.matches() && m.group(3) != null) {
            try {
                return Long.parseLong(m.group(3));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    /** Klasordeki en buyuk klip id'sinin bir fazlasi; hic id'li klip yoksa 1. */
    public static long nextId(File dir) {
        File[] files = dir != null ? dir.listFiles() : null;
        long max = 0;
        if (files != null) {
            for (File f : files) {
                max = Math.max(max, idOf(f.getName()));
            }
        }
        return max + 1;
    }

    /** -2: taninmayan ad, -1: tek dosya (mozaik), aksi halde kamera indeksi. */
    private static int camOf(String name) {
        Matcher m = NAME.matcher(name);
        if (m.matches()) {
            String tag = m.group(1);
            if (tag == null) return -1;
            for (int i = 0; i < CameraRecorder.CAM_FILE_TAG.length; i++) {
                if (CameraRecorder.CAM_FILE_TAG[i].equals(tag)) return i;
            }
            return -2;
        }
        m = LEGACY_NAME.matcher(name);
        if (!m.matches()) return -2;
        String suffix = m.group(2);
        if (suffix == null) return -1;
        for (int i = 0; i < CameraRecorder.CAM_SUFFIX.length; i++) {
            if (CameraRecorder.CAM_SUFFIX[i].equals(suffix)) return i;
        }
        return -2;
    }

    /** Klasordeki klipler, en buyuk id (en yeni) basta; esit id'de zamana gore. */
    public static List<Clip> list(File dir, Set<String> excludeBases) {
        File[] files = dir != null ? dir.listFiles() : null;
        if (files == null) {
            return new ArrayList<>();
        }
        SimpleDateFormat fmt = new SimpleDateFormat(CameraRecorder.FILE_TIME_PATTERN, Locale.US);
        Map<String, Clip> clips = new HashMap<>();
        for (File f : files) {
            String base = baseOf(f);
            int cam = base != null ? camOf(f.getName()) : -2;
            if (cam == -2 || !f.isFile()) continue;
            if (excludeBases != null && excludeBases.contains(base)) continue;
            Clip clip = clips.get(base);
            if (clip == null) {
                long t;
                try {
                    t = fmt.parse(base.substring(0, CameraRecorder.FILE_TIME_PATTERN.length())).getTime();
                } catch (ParseException | RuntimeException e) {
                    t = f.lastModified();
                }
                clip = new Clip(base, t, idOf(f.getName()));
                clips.put(base, clip);
            }
            if (cam == -1) {
                clip.mosaic = f;
            } else {
                clip.camFiles[cam] = f;
            }
            clip.bytes += f.length();
        }
        List<Clip> out = new ArrayList<>(clips.values());
        Collections.sort(out, (a, b) -> {
            int c = Long.compare(b.id, a.id);
            return c != 0 ? c : Long.compare(b.timeMs, a.timeMs);
        });
        return out;
    }

    public static long totalBytes(Collection<Clip> clips) {
        long total = 0;
        for (Clip c : clips) total += c.bytes;
        return total;
    }

    public static long freeBytes(File dir) {
        try {
            return new StatFs(dir.getAbsolutePath()).getAvailableBytes();
        } catch (Throwable t) {
            return -1;
        }
    }

    public static long totalSpaceBytes(File dir) {
        try {
            return new StatFs(dir.getAbsolutePath()).getTotalBytes();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Klasoru olusturur ve gercekten yazilabildigini bir deneme dosyasiyla dogrular. */
    public static boolean ensureWritable(File dir) {
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            File probe = new File(dir, ".probe");
            try (FileOutputStream out = new FileOutputStream(probe)) {
                out.write(1);
            }
            return probe.delete();
        } catch (Throwable t) {
            return false;
        }
    }

    public static int delete(Clip clip) {
        int n = 0;
        for (File f : clip.files()) {
            if (f.delete()) n++;
        }
        return n;
    }

    /**
     * Bos alan minFreeBytes'in altindaysa en kucuk id'li (en eski) klipten baslayarak
     * yeterli yer acilana kadar siler, sonra durur.
     * @return islem sonrasi bos alan
     */
    public static long freeUpTo(File dir, long minFreeBytes, Set<String> excludeBases) {
        long free = freeBytes(dir);
        if (free < 0 || free >= minFreeBytes) {
            return free;
        }
        List<Clip> clips = list(dir, excludeBases);
        int deleted = 0;
        for (int i = clips.size() - 1; i >= 0 && free < minFreeBytes; i--) {
            delete(clips.get(i));
            deleted++;
            free = freeBytes(dir);
        }
        if (deleted > 0) Log.i(TAG, deleted + " eski klip alan icin silindi, bos=" + free);
        return free;
    }
}
