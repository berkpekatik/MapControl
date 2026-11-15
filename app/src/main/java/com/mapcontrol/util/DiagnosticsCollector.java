package com.mapcontrol.util;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.DropBoxManager;
import android.os.SystemClock;

import com.mapcontrol.BuildConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * "Log Kaydını Geliştiriciye Gönder" için tek seferlik tanılama paketi (gzip metin). Arka planda sürekli kayıt
 * YOKTUR: yalnızca kullanıcı onaylayınca, sistemin zaten tuttuğu kayıtlar okunur:
 * <ol>
 *   <li>Sürecin önceki ölümleri ve ANR thread dökümleri (ApplicationExitInfo).</li>
 *   <li>Sistem DropBox'ı: çökme/ANR/watchdog/yeniden başlatma kayıtları (araç resetlense de diskte kalır; BT
 *   servisi gibi sistem uygulamalarının çökmeleri de buradadır).</li>
 *   <li>BT durumu (dumpsys bluetooth_manager; DUMP izni yoksa hata satırı).</li>
 *   <li>logcat'in o anki tamponundan son satırlar (döküp çıkar).</li>
 * </ol>
 * Her satır {@link DiagnosticScrubber} ile maskelenip doğrudan gzip dosyasına yazılır (belleğe toplanmaz). Arka plan
 * iş parçacığında çağrılmalıdır; birkaç saniye sürebilir.
 */
public final class DiagnosticsCollector {

    /** Ham metin üst sınırı (gzip ~5-8 kat küçültür; sunucu 5 MB'a kadar alır). */
    static final long MAX_RAW = 10L * 1024 * 1024;
    private static final long EXITS_BUDGET = 1024 * 1024;
    private static final long DROPBOX_BUDGET = 2L * 1024 * 1024;
    private static final long BT_BUDGET = 512 * 1024;
    private static final long LOGCAT_BUDGET = 6L * 1024 * 1024;

    private static final long DROPBOX_WINDOW_MS = 72L * 60 * 60 * 1000;
    private static final int DROPBOX_MAX_ENTRIES = 40;
    private static final int DROPBOX_ENTRY_CHARS = 48 * 1024;
    private static final int LOGCAT_LINES = 40_000;
    private static final long COMMAND_TIMEOUT_MS = 15_000L;

    /** Çökme, ANR, watchdog ve yeniden başlatma etiketleri (Android DropBoxManager). */
    static final Set<String> DROPBOX_TAGS = new HashSet<>(Arrays.asList(
            "system_server_crash", "system_server_native_crash", "system_server_anr", "system_server_watchdog",
            "system_server_wtf", "system_server_lowmem",
            "system_app_crash", "system_app_native_crash", "system_app_anr", "system_app_wtf",
            "data_app_crash", "data_app_native_crash", "data_app_anr",
            "SYSTEM_RESTART", "SYSTEM_TOMBSTONE", "SYSTEM_LAST_KMSG", "SYSTEM_FSCK"));

    private DiagnosticsCollector() {
    }

    /** Paketi uygulamanın önbellek klasörüne yazar ve dosyayı döndürür. */
    public static File collect(Context context) throws IOException {
        Context app = context.getApplicationContext();
        File out = new File(app.getCacheDir(), "diagnostics.log.gz");
        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(new FileOutputStream(out), 64 * 1024), StandardCharsets.UTF_8), 64 * 1024)) {
            Sink total = new Sink(w, MAX_RAW, false);
            header(app, total);
            exits(app, total.section(EXITS_BUDGET, false));
            dropbox(app, total.section(DROPBOX_BUDGET, false));
            command(total.section(BT_BUDGET, true), "dumpsys bluetooth_manager",
                    new String[] {"dumpsys", "bluetooth_manager"});
            command(total.section(LOGCAT_BUDGET, false), "logcat",
                    new String[] {"logcat", "-d", "-v", "threadtime", "-b", "main,system,crash",
                            "-t", String.valueOf(LOGCAT_LINES)});
            total.title("SON");
        }
        return out;
    }

    private static void header(Context app, Sink s) throws IOException {
        s.title("MAPCONTROL TANILAMA");
        s.line("olusturma: " + fmt(System.currentTimeMillis()));
        s.line("surum: " + BuildConfig.VERSION_NAME + "+" + BuildConfig.VERSION_CODE + " (" + BuildConfig.GIT_SHA + ")");
        s.line("firmware: " + Build.DISPLAY);
        s.line("urun: " + Build.PRODUCT + " / " + Build.MODEL + " / sdk " + Build.VERSION.SDK_INT);
        s.line("vdbus.jar: " + new File("/system/framework/vdbus.jar").exists());
        s.line("acik kalma: " + (SystemClock.elapsedRealtime() / 60_000L) + " dk");
        try {
            ActivityManager am = app.getSystemService(ActivityManager.class);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            s.line("bellek: bos " + (mi.availMem >> 20) + " MB / toplam " + (mi.totalMem >> 20)
                    + " MB, dusuk=" + mi.lowMemory);
            Runtime rt = Runtime.getRuntime();
            s.line("uygulama heap: " + ((rt.totalMemory() - rt.freeMemory()) >> 20) + " / " + (rt.maxMemory() >> 20) + " MB");
        } catch (Throwable ignored) {
        }
        try {
            s.line("launcher modu: " + LauncherModeManager.isEnabled(app));
        } catch (Throwable ignored) {
        }
    }

    private static void exits(Context app, Sink s) throws IOException {
        s.title("SUREC CIKISLARI");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            s.line("Android 10: okunamiyor");
            return;
        }
        List<ApplicationExitInfo> list;
        try {
            list = app.getSystemService(ActivityManager.class)
                    .getHistoricalProcessExitReasons(app.getPackageName(), 0, 10);
        } catch (Throwable t) {
            s.line("okunamadi: " + t.getClass().getSimpleName());
            return;
        }
        int traces = 0;
        for (ApplicationExitInfo e : list) {
            s.line(fmt(e.getTimestamp()) + " " + ProcessExitLog.reasonName(e.getReason())
                    + " surec=" + e.getProcessName() + " onem=" + e.getImportance() + " pss=" + e.getPss()
                    + " kB durum=" + e.getStatus() + " aciklama=" + e.getDescription());
            // Son iki ANR'nin thread dökümü: neyi bekleyerek dondu.
            if (e.getReason() == ApplicationExitInfo.REASON_ANR && traces < 2) {
                try (InputStream in = e.getTraceInputStream()) {
                    if (in != null) {
                        traces++;
                        s.title("ANR DOKUMU " + fmt(e.getTimestamp()));
                        copyLines(in, s);
                    }
                } catch (Throwable t) {
                    s.line("anr dokumu okunamadi: " + t.getClass().getSimpleName());
                }
            }
            if (s.full()) {
                break;
            }
        }
    }

    private static void dropbox(Context app, Sink s) throws IOException {
        s.title("SISTEM KAYITLARI (DropBox, son 72 saat)");
        DropBoxManager dbm;
        try {
            dbm = app.getSystemService(DropBoxManager.class);
        } catch (Throwable t) {
            dbm = null;
        }
        if (dbm == null) {
            s.line("DropBox yok");
            return;
        }
        // 1. tur: yalnızca etiket + zaman (metin okunmaz), en yeni N seçilir.
        List<long[]> times = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        long since = System.currentTimeMillis() - DROPBOX_WINDOW_MS;
        try {
            DropBoxManager.Entry e;
            long t = since;
            while ((e = dbm.getNextEntry(null, t)) != null) {
                t = e.getTimeMillis();
                String tag = e.getTag();
                e.close();
                if (tag != null && DROPBOX_TAGS.contains(tag)) {
                    times.add(new long[] {t});
                    tags.add(tag);
                }
            }
        } catch (SecurityException se) {
            s.line("izin yok (READ_LOGS + PACKAGE_USAGE_STATS gerekli): " + se.getMessage());
            return;
        } catch (Throwable th) {
            s.line("okunamadi: " + th.getClass().getSimpleName());
            return;
        }
        s.line(times.size() + " kayit bulundu");
        int from = Math.max(0, times.size() - DROPBOX_MAX_ENTRIES);
        // 2. tur: seçilenlerin metni (en yeniden eskiye: bütçe biterse yeniler kalır).
        for (int i = times.size() - 1; i >= from && !s.full(); i--) {
            long t = times.get(i)[0];
            String tag = tags.get(i);
            try {
                DropBoxManager.Entry e = dbm.getNextEntry(tag, t - 1);
                if (e == null) {
                    continue;
                }
                String text;
                try {
                    text = e.getText(DROPBOX_ENTRY_CHARS);
                } finally {
                    e.close();
                }
                s.title(tag + " " + fmt(t));
                if (text == null) {
                    s.line("(metin degil)");
                    continue;
                }
                for (String line : text.split("\n")) {
                    if (!s.line(line)) {
                        break;
                    }
                }
            } catch (Throwable th) {
                s.line("okunamadi: " + th.getClass().getSimpleName());
            }
        }
    }

    /** Komutu çalıştırır, çıktıyı satır satır yazar; süre dolarsa süreç öldürülür. */
    private static void command(Sink s, String title, String[] cmd) throws IOException {
        s.title(title);
        Process p = null;
        Thread killer = null;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            final Process proc = p;
            killer = new Thread(() -> {
                try {
                    Thread.sleep(COMMAND_TIMEOUT_MS);
                    proc.destroy();
                } catch (InterruptedException ignored) {
                }
            }, "diag-timeout");
            killer.setDaemon(true);
            killer.start();
            copyLines(p.getInputStream(), s);
        } catch (IOException e) {
            s.line("calistirilamadi: " + e.getMessage());
        } finally {
            if (killer != null) {
                killer.interrupt();
            }
            if (p != null) {
                p.destroy();
            }
        }
    }

    private static void copyLines(InputStream in, Sink s) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 16 * 1024);
        String line;
        while ((line = r.readLine()) != null) {
            if (!s.line(line)) {
                return; // bütçe doldu: kalan okunmaz (süreç destroy ile biter)
            }
        }
    }

    private static String fmt(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date(ms));
    }

    /** Bütçeli yazıcı: maskeler, sınırı aşan satırı yazmaz. */
    static final class Sink {
        private final Writer w;
        private final Sink parent;
        private final long budget;
        private final boolean deviceNames;
        private long used;
        private boolean noted;

        Sink(Writer w, long budget, boolean deviceNames) {
            this(w, null, budget, deviceNames);
        }

        private Sink(Writer w, Sink parent, long budget, boolean deviceNames) {
            this.w = w;
            this.parent = parent;
            this.budget = budget;
            this.deviceNames = deviceNames;
        }

        Sink section(long sectionBudget, boolean names) {
            return new Sink(w, this, sectionBudget, names);
        }

        boolean full() {
            return used >= budget || (parent != null && parent.full());
        }

        void title(String t) throws IOException {
            raw("\n===== " + t + " =====");
        }

        /** @return false: bütçe doldu, bu bölüme artık yazılmaz */
        boolean line(String line) throws IOException {
            String clean = deviceNames ? DiagnosticScrubber.scrubDeviceNames(line) : DiagnosticScrubber.scrub(line);
            return raw(clean == null ? "" : clean);
        }

        private boolean raw(String text) throws IOException {
            long n = text.length() + 1L;
            if (full() || used + n > budget || (parent != null && !parent.reserve(n))) {
                if (!noted) {
                    noted = true;
                    w.write("[... sinir doldu, kalan atlandi]\n");
                }
                return false;
            }
            used += n;
            w.write(text);
            w.write('\n');
            return true;
        }

        private boolean reserve(long n) {
            if (used + n > budget || (parent != null && !parent.reserve(n))) {
                return false;
            }
            used += n;
            return true;
        }
    }
}
