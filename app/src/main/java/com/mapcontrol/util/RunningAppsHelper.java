package com.mapcontrol.util;

import android.content.Context;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Şu an gerçekten çalışan (süreci olan) kullanıcı uygulamaları: {@code dumpsys activity lru} (DUMP izni,
 * install.sh verir). Kullanım geçmişi değil; kapatılınca listeden düşer.
 * Ana iş parçacığında çağırmayın (dumpsys süreci).
 */
public final class RunningAppsHelper {

    /** Kapatılabilirlik sırası: küçük olan daha "önde". */
    public enum State {
        /** Bir ekranda görünür (1. ekran ya da gösterge). Arka plan kapatması etkilemez. */
        SCREEN,
        /** Ön plan servisi / algılanabilir (müzik, navigasyon, klavye). Arka plan kapatması etkilemez. */
        SERVICE,
        /** Arka planda/önbellekte; {@code killBackgroundProcesses} kapatır. */
        CACHED
    }

    public static final class RunningApp {
        @NonNull public final String packageName;
        @NonNull public final State state;

        RunningApp(@NonNull String packageName, @NonNull State state) {
            this.packageName = packageName;
            this.state = state;
        }
    }

    /** Ham lru satırı. */
    @VisibleForTesting
    static final class LruEntry {
        final String packageName;
        final String adj;
        final String procState;
        final boolean appUid;

        LruEntry(String packageName, String adj, String procState, boolean appUid) {
            this.packageName = packageName;
            this.adj = adj;
            this.procState = procState;
            this.appUid = appUid;
        }
    }

    /**
     * Örnekler: {@code #53: vis+ 2 TOP  LCM 12552:ru.yandex.yandexmaps/u0a38 act:activities},
     * {@code #51: fg +50 FGS  L-- 22775:com.google.android.apps.maps/u0a43},
     * {@code #33: cch+55 CEM  --- 17603:com.qualcomm.timeservice/u0a11}.
     */
    private static final Pattern LRU_LINE = Pattern.compile(
            "^\\s*#\\d+:\\s+([a-z]+)(?:\\s*\\+\\s*\\d+)?\\s+(\\S+)\\s+\\S+\\s+\\d+:([^/\\s]+)/(\\S+)");

    private static final long DUMPSYS_TIMEOUT_MS = 2_000L;

    private RunningAppsHelper() {
    }

    @VisibleForTesting
    @NonNull
    static List<LruEntry> parseLru(@Nullable String dump) {
        if (dump == null || dump.isEmpty()) {
            return Collections.emptyList();
        }
        List<LruEntry> out = new ArrayList<>();
        for (String line : dump.split("\n")) {
            Matcher m = LRU_LINE.matcher(line);
            if (!m.find()) {
                continue;
            }
            String proc = m.group(3);
            int colon = proc.indexOf(':');
            String pkg = colon > 0 ? proc.substring(0, colon) : proc;
            // u0a38 = uygulama uid'si; 1000/1002 gibi düz sayı sistem süreçleridir.
            boolean appUid = m.group(4).matches("u\\d+a\\d+");
            out.add(new LruEntry(pkg, m.group(1), m.group(2), appUid));
        }
        return out;
    }

    /**
     * {@code killBackgroundProcesses} yalnızca SERVICE_ADJ (500) ve üstünü kapatır:
     * svc, home, prev, svcb, cch*. fg/vis/prcp/pers/bkup/hvy dokunulmaz.
     */
    @VisibleForTesting
    @NonNull
    static State stateOf(@NonNull String adj, @NonNull String procState) {
        if ("TOP".equals(procState) || "fg".equals(adj) || "vis".equals(adj)) {
            return State.SCREEN;
        }
        if ("svc".equals(adj) || "svcb".equals(adj) || "home".equals(adj)
                || "prev".equals(adj) || adj.startsWith("cch")) {
            return State.CACHED;
        }
        return State.SERVICE;
    }

    /** Paket başına en "önde" süreç durumu; lru (en yeni önce) sırası korunur. */
    @VisibleForTesting
    @NonNull
    static List<RunningApp> aggregate(@NonNull List<LruEntry> entries) {
        Map<String, State> byPkg = new LinkedHashMap<>();
        for (LruEntry e : entries) {
            if (!e.appUid) {
                continue;
            }
            State s = stateOf(e.adj, e.procState);
            State old = byPkg.get(e.packageName);
            if (old == null || s.ordinal() < old.ordinal()) {
                byPkg.put(e.packageName, s);
            }
        }
        List<RunningApp> out = new ArrayList<>(byPkg.size());
        for (Map.Entry<String, State> en : byPkg.entrySet()) {
            out.add(new RunningApp(en.getKey(), en.getValue()));
        }
        return out;
    }

    /**
     * Çalışan kullanıcı uygulamaları (sistem uygulamaları, MapControl ve etkin klavye hariç).
     *
     * @return dumpsys okunamazsa (izin yok) {@code null}.
     */
    @Nullable
    public static List<RunningApp> listRunningUserApps(@NonNull Context context) {
        String dump = readLruDump();
        if (dump == null || !dump.contains("#")) {
            return null;
        }
        Context app = context.getApplicationContext();
        String self = app.getPackageName();
        String ime = currentImePackage(app);
        List<RunningApp> out = new ArrayList<>();
        for (RunningApp ra : aggregate(parseLru(dump))) {
            if (ra.packageName.equals(self) || ra.packageName.equals(ime)) {
                continue;
            }
            if (ActivityDisplayTasksHelper.isSystemOrUnknownPackage(app, ra.packageName)) {
                continue;
            }
            out.add(ra);
        }
        return out;
    }

    /** Paketin hâlâ süreci var mı; dumpsys okunamazsa {@code null}. */
    @Nullable
    public static Boolean isRunning(@NonNull String packageName) {
        String dump = readLruDump();
        if (dump == null || !dump.contains("#")) {
            return null;
        }
        for (LruEntry e : parseLru(dump)) {
            if (packageName.equals(e.packageName)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String currentImePackage(Context c) {
        try {
            String id = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (id == null) {
                return null;
            }
            int slash = id.indexOf('/');
            return slash > 0 ? id.substring(0, slash) : id;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String readLruDump() {
        Process p = null;
        try {
            p = new ProcessBuilder("dumpsys", "activity", "lru").redirectErrorStream(true).start();
            final Process proc = p;
            // Takılırsa öldür; readLine akış kapanınca biter.
            Thread killer = new Thread(() -> {
                try {
                    if (!proc.waitFor(DUMPSYS_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                        proc.destroy();
                    }
                } catch (InterruptedException ignored) {
                    proc.destroy();
                }
            }, "lru-dump-timeout");
            killer.setDaemon(true);
            killer.start();
            StringBuilder sb = new StringBuilder(8192);
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }
}
