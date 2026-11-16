package com.mapcontrol.util;

import android.content.Context;
import android.os.Process;
import android.os.SystemClock;

import com.mapcontrol.BuildConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kalıcı olay günlüğü: "Log Kaydını Geliştiriciye Gönder" paketine girer.
 *
 * logcat aracın üzerinde ~10 dk'da döner ve release launcher logları sessizdir; bir sorun yaşandıktan saatler sonra
 * gönderilen kayıtta olayın izi kalmaz. Bu günlük yalnızca seyrek ve anlamlı olayları (süreç/hizmet yaşam döngüsü,
 * kurulum akışı, izin bağlanması, çökme özeti, uygulamanın kendi Log sekmesi) uygulamanın özel klasörüne yazar.
 * İki dosya dönüşümlü tutulur ({@link #MAX_FILE_BYTES} x 2). Kişisel veri maskelemesi gönderimde yapılır
 * ({@link DiagnosticScrubber}); dosya uygulama dışına çıkmaz.
 *
 * Sık tekrarlanan olay günlüğü doldurmasın diye her alan dakikada en fazla {@link #AREA_LIMIT_PER_MIN} satır yazar;
 * fazlası sayılıp bir sonraki satıra not düşülür.
 */
public final class DiagJournal {

    static final long MAX_FILE_BYTES = 192L * 1024;
    static final int AREA_LIMIT_PER_MIN = 40;
    static final int UI_LIMIT_PER_MIN = 8;
    static final int UI_KEY_CHARS = 16;
    /** Pencere dökümü gibi çok satırlı içerik tek satıra katlanır; bu sınır onu da taşır. */
    static final int MAX_LINE_CHARS = 6000;
    static final String CURRENT = "journal.txt";
    static final String PREVIOUS = "journal.1.txt";

    private static final Object FILE_LOCK = new Object();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "diag-journal");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String, long[]> RATE = new HashMap<>();
    private static volatile File dir;

    private DiagJournal() {
    }

    /** Application.onCreate başında bir kez. */
    public static void init(Context context) {
        dir = new File(context.getApplicationContext().getFilesDir(), "diag");
        event("surec", "basladi surum=" + BuildConfig.VERSION_NAME + "+" + BuildConfig.VERSION_CODE
                + " pid=" + Process.myPid());
    }

    /** Arka planda yazar; hata fırlatmaz. */
    public static void event(String area, String message) {
        event(area, area, limitFor(area), message);
    }

    /** Durum titreşebilen alanlar (GPS bağlantısı, araç güç modu) günlüğü doldurmasın. */
    static int limitFor(String area) {
        return "gps".equals(area) || "arac".equals(area) ? 6 : AREA_LIMIT_PER_MIN;
    }

    /**
     * Uygulamanın Log sekmesinden yansıtma: çok farklı kaynaktan satır gelir, hız sınırı mesajın önekine göre
     * uygulanır (tekrarlayan bir kaynak diğerlerini bastırmasın).
     */
    public static void uiLog(String message) {
        if (message == null) {
            return;
        }
        String key = message.length() > UI_KEY_CHARS ? message.substring(0, UI_KEY_CHARS) : message;
        event("ui", "ui:" + key, UI_LIMIT_PER_MIN, message);
    }

    private static void event(String area, String rateKey, int limit, String message) {
        File d = dir;
        if (d == null) {
            return;
        }
        final String line = admitAndFormat(area, rateKey, limit, message);
        if (line == null) {
            return;
        }
        try {
            EXEC.execute(() -> append(d, line, MAX_FILE_BYTES));
        } catch (Throwable ignored) {
        }
    }

    /** Çağıran iş parçacığında hemen yazar: süreç ölmek üzereyken (çökme) kullanılır. */
    public static void eventNow(String area, String message) {
        File d = dir;
        if (d == null) {
            return;
        }
        String line = admitAndFormat(area, area, AREA_LIMIT_PER_MIN, message);
        if (line != null) {
            append(d, line, MAX_FILE_BYTES);
        }
    }

    /** Günlük dosyaları, eskiden yeniye. */
    public static List<File> files() {
        File d = dir;
        return d == null ? new ArrayList<>() : files(d);
    }

    static List<File> files(File d) {
        List<File> out = new ArrayList<>(2);
        File previous = new File(d, PREVIOUS);
        File current = new File(d, CURRENT);
        if (previous.isFile()) {
            out.add(previous);
        }
        if (current.isFile()) {
            out.add(current);
        }
        return out;
    }

    private static String admitAndFormat(String area, String rateKey, int limit, String message) {
        long minute = SystemClock.elapsedRealtime() / 60_000L;
        int dropped;
        synchronized (RATE) {
            if (RATE.size() > 512) {
                RATE.clear(); // çok farklı önek: sınır durumu sıfırlanır, bellek büyümez
            }
            dropped = admit(RATE, rateKey, minute, limit);
        }
        if (dropped < 0) {
            return null;
        }
        String text = dropped > 0 ? message + " (+" + dropped + " satir bastirildi)" : message;
        return format(System.currentTimeMillis(), Process.myPid(), area, text);
    }

    /**
     * Saf hız sınırı (JVM'de sınanır). Durum: {dakika, bu dakikadaki sayı, bastırılan}.
     *
     * @return yazılabilirse önceki dakikalarda bastırılıp henüz bildirilmemiş satır sayısı (>=0); yazılamazsa -1
     */
    static int admit(Map<String, long[]> state, String area, long minute, int limit) {
        long[] s = state.get(area);
        if (s == null) {
            state.put(area, new long[] {minute, 1, 0});
            return 0;
        }
        if (s[0] != minute) {
            s[0] = minute;
            s[1] = 0;
        }
        if (s[1] >= limit) {
            s[2]++;
            return -1;
        }
        s[1]++;
        int dropped = (int) s[2];
        s[2] = 0;
        return dropped;
    }

    static String format(long wallMs, int pid, String area, String message) {
        String body = message == null ? "null" : message.replace("\r", "").replace('\n', '|');
        if (body.length() > MAX_LINE_CHARS) {
            body = body.substring(0, MAX_LINE_CHARS) + "…";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date(wallMs))
                + " " + pid + " [" + area + "] " + body;
    }

    /** Satırı ekler; dosya sınırı aşılacaksa önce mevcut dosyayı önceki dosyaya döndürür. */
    static void append(File d, String line, long maxBytes) {
        synchronized (FILE_LOCK) {
            try {
                if (!d.isDirectory() && !d.mkdirs()) {
                    return;
                }
                File current = new File(d, CURRENT);
                byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
                if (current.length() + bytes.length > maxBytes && current.length() > 0) {
                    File previous = new File(d, PREVIOUS);
                    //noinspection ResultOfMethodCallIgnored
                    previous.delete();
                    //noinspection ResultOfMethodCallIgnored
                    current.renameTo(previous);
                }
                try (FileOutputStream out = new FileOutputStream(current, true)) {
                    out.write(bytes);
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }
    }
}
