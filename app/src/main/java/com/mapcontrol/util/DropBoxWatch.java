package com.mapcontrol.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.DropBoxManager;
import android.util.Log;

import com.mapcontrol.GlitchTip;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Açılışta (ProcessExitLog ile aynı arka plan iş parçacığında) sistem DropBox'ındaki yeni sistem çökmelerini,
 * donmalarını ve yeniden başlatmaları GlitchTip'e kısa uyarı olarak bildirir: hangi araçlarda BT servisi ya da
 * sistem çöküyor, kullanıcı log göndermeden görünsün. Kaydın metni GÖNDERİLMEZ; yalnızca etiket, süreç adı ve
 * maskelenmiş ilk hata satırı. Kendi Java çökmelerimizi Sentry zaten raporlar (data_app_* bilerek dışarıda).
 */
public final class DropBoxWatch {

    private static final String TAG = "DropBoxWatch";
    private static final String PREFS = "dropbox_watch";
    private static final String KEY_LAST_MS = "lastMs";
    private static final long FIRST_WINDOW_MS = 24L * 60 * 60 * 1000;
    private static final int MAX_REPORTS = 5;
    private static final int HEAD_CHARS = 4096;

    static final Set<String> TAGS = new HashSet<>(Arrays.asList(
            "system_server_crash", "system_server_native_crash", "system_server_anr", "system_server_watchdog",
            "system_app_crash", "system_app_native_crash", "system_app_anr",
            "SYSTEM_RESTART", "SYSTEM_TOMBSTONE"));

    private DropBoxWatch() {
    }

    /** Arka plan iş parçacığında çağrılır; hata fırlatmaz. */
    public static void reportNew(Context app) {
        DropBoxManager dbm;
        try {
            dbm = app.getSystemService(DropBoxManager.class);
        } catch (Throwable t) {
            return;
        }
        if (dbm == null) {
            return;
        }
        SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = sp.getLong(KEY_LAST_MS, 0L);
        long t = last > 0 && last <= now ? last : now - FIRST_WINDOW_MS;
        long newest = t;
        int reported = 0;
        try {
            DropBoxManager.Entry e;
            while ((e = dbm.getNextEntry(null, t)) != null) {
                t = e.getTimeMillis();
                newest = Math.max(newest, t);
                String tag = e.getTag();
                try {
                    if (tag == null || !TAGS.contains(tag) || reported >= MAX_REPORTS) {
                        continue;
                    }
                    Head head = parse(e.getText(HEAD_CHARS));
                    Map<String, String> extras = new LinkedHashMap<>();
                    extras.put("time", String.valueOf(t));
                    extras.put("process", head.process);
                    extras.put("first_error", head.error);
                    GlitchTip.reportWarning("dropbox:" + tag + (head.process.isEmpty() ? "" : ":" + head.process), extras);
                    reported++;
                } finally {
                    e.close();
                }
            }
        } catch (SecurityException se) {
            Log.w(TAG, "izin yok: " + se.getMessage());
            return;
        } catch (Throwable th) {
            Log.w(TAG, "okunamadi: " + th.getClass().getSimpleName());
        }
        sp.edit().putLong(KEY_LAST_MS, newest).apply();
    }

    static final class Head {
        final String process;
        final String error;

        Head(String process, String error) {
            this.process = process;
            this.error = error;
        }
    }

    /** Saf: "Process: x" satırı ve ilk istisna/sebep satırı (maskelenmiş, 200 karakter). */
    static Head parse(String text) {
        if (text == null) {
            return new Head("", "");
        }
        String process = "";
        String error = "";
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (process.isEmpty() && line.startsWith("Process: ")) {
                process = line.substring(9).trim();
            } else if (process.isEmpty() && line.startsWith("Cmd line: ")) {
                process = line.substring(10).trim();
            } else if (error.isEmpty() && (line.contains("Exception") || line.contains("Error")
                    || line.startsWith("Subject: ") || line.startsWith("Abort message: ")
                    || line.startsWith("signal "))) {
                error = line;
            }
            if (!process.isEmpty() && !error.isEmpty()) {
                break;
            }
        }
        if (process.length() > 80) {
            process = process.substring(0, 80);
        }
        error = DiagnosticScrubber.scrub(error);
        if (error.length() > 200) {
            error = error.substring(0, 200);
        }
        return new Head(process, error);
    }
}
