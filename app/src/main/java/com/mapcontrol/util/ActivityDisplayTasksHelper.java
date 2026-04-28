package com.mapcontrol.util;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Son kullanım / ön planda uygulama adaylarını {@link UsageStatsManager#queryEvents} ile toplar;
 * ekran ayrımı için (varsa) {@code UsageEvents.Event} içindeki {@code mDisplayId} yansıması kullanılır.
 */
public final class ActivityDisplayTasksHelper {

    private static final long QUERY_WINDOW_MS = 60L * 60L * 1000L; // 1 saat

    private ActivityDisplayTasksHelper() {
    }

    @NonNull
    public static List<String> listPackagesOnDisplay(
            @NonNull Context anyContext, int targetDisplay) {
        if (targetDisplay < 0) {
            return Collections.emptyList();
        }
        if (!UsageAccessHelper.hasPackageUsageAccess(anyContext)) {
            return Collections.emptyList();
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return Collections.emptyList();
        }
        Context app = anyContext.getApplicationContext();
        UsageStatsManager usm = (UsageStatsManager) app.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return Collections.emptyList();
        }
        long end = System.currentTimeMillis();
        long begin = end - QUERY_WINDOW_MS;
        UsageEvents ev = usm.queryEvents(begin, end);
        if (ev == null) {
            return Collections.emptyList();
        }
        UsageEvents.Event e = new UsageEvents.Event();
        /* paket -> en son eşleşen etkinlik zamanı */
        Map<String, Long> byPkg = new HashMap<>();
        while (ev.hasNextEvent()) {
            if (!ev.getNextEvent(e)) {
                break;
            }
            if (e.getEventType() != UsageEvents.Event.ACTIVITY_RESUMED) {
                continue;
            }
            String pkg = e.getPackageName();
            if (pkg == null || pkg.isEmpty() || "com.mapcontrol".equals(pkg)) {
                continue;
            }
            if (shouldSkipInstalledPackage(app, pkg)) {
                continue;
            }
            int d = getDisplayIdFromEvent(e);
            if (!eventMatchesTargetDisplay(d, targetDisplay)) {
                continue;
            }
            long t = e.getTimeStamp();
            Long old = byPkg.get(pkg);
            if (old == null || t >= old) {
                byPkg.put(pkg, t);
            }
        }
        if (byPkg.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map.Entry<String, Long>> list = new ArrayList<>(byPkg.entrySet());
        list.sort(Comparator.comparing((Map.Entry<String, Long> en) -> en.getValue()).reversed());
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Long> en : list) {
            out.add(en.getKey());
        }
        return out;
    }

    /**
     * Bilinmeyen ekran (yansıma yok): hem 1. hem 2. ekran aynı son uygulama setini gösterir; aksi halde kimliğe göre ayrışır.
     */
    private static boolean eventMatchesTargetDisplay(int d, int targetDisplay) {
        if (d >= 0) {
            return d == targetDisplay;
        }
        // bilinmeyen ekran — her iki ekran listesinde aynı veriyi göster
        return true;
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private static int getDisplayIdFromEvent(UsageEvents.Event e) {
        if (e == null) {
            return -1;
        }
        try {
            java.lang.reflect.Method m = e.getClass().getMethod("getDisplayId");
            Object o = m.invoke(e);
            if (o instanceof Integer) {
                return (Integer) o;
            }
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Field f = e.getClass().getDeclaredField("mDisplayId");
            f.setAccessible(true);
            return f.getInt(e);
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static boolean shouldSkipInstalledPackage(Context c, String pkg) {
        try {
            ApplicationInfo info = c.getPackageManager().getApplicationInfo(pkg, 0);
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    || (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                return true;
            }
            String sd = info.sourceDir;
            String psd = info.publicSourceDir;
            if (sd != null) {
                String s = sd.toLowerCase(Locale.US);
                if (s.contains("/system/priv-app/") || s.contains("/system/app/")) {
                    return true;
                }
            }
            if (psd != null) {
                String s = psd.toLowerCase(Locale.US);
                if (s.contains("/system/priv-app/") || s.contains("/system/app/")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return true;
        }
        return false;
    }
}
