package com.mapcontrol.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Yansıtma hedefi için başlatılabilir kullanıcı uygulamaları listesi (sistem/priv filtre ile).
 * {@link com.mapcontrol.ui.builder.ProjectionTabBuilder} ile aynı kurallar.
 */
public final class ProjectionTargetApps {

    private ProjectionTargetApps() {
    }

    public static final class Row {
        public final String label;
        public final String packageName;
        /** Launcher activity sınıf adı; uygulama ikonu boşken activity ikonunu yüklemek için. */
        @Nullable
        public final String activityName;

        public Row(String label, String packageName) {
            this(label, packageName, null);
        }

        public Row(String label, String packageName, @Nullable String activityName) {
            this.label = label != null ? label : "";
            this.packageName = packageName != null ? packageName : "";
            this.activityName = activityName != null && !activityName.isEmpty() ? activityName : null;
        }
    }

    /**
     * {@link com.mapcontrol.ui.activity.MainActivity} içindeki {@code isSystemOrPrivApp(ApplicationInfo)} ile aynı mantık.
     */
    public static boolean isSystemOrPrivApp(ApplicationInfo appInfo) {
        try {
            if (appInfo == null) {
                return true;
            }
            if ("com.mapcontrol".equals(appInfo.packageName)) {
                return false;
            }
            boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isUpdatedSystemApp = (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            if (isSystemApp || isUpdatedSystemApp) {
                return true;
            }
            String sourceDir = appInfo.sourceDir;
            String publicSourceDir = appInfo.publicSourceDir;
            if (sourceDir != null && (sourceDir.contains("/system/priv-app/") || sourceDir.contains("/system/app/"))) {
                return true;
            }
            if (publicSourceDir != null && (publicSourceDir.contains("/system/priv-app/")
                    || publicSourceDir.contains("/system/app/"))) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Launcher'da görünen, MapControl ve sistem/priv hariç uygulamalar; isim sıralı.
     */
    public static List<Row> loadSortedRows(Context context) {
        return loadLauncherRows(context, false);
    }

    /**
     * Launcher'da görünen sistem/priv uygulamalar; MapControl hariç, isim sıralı.
     */
    public static List<Row> loadSortedSystemRows(Context context) {
        return loadLauncherRows(context, true);
    }

    /**
     * Launcher'da görünen tüm uygulamalar (kullanıcı + sistem); MapControl hariç, isim sıralı.
     */
    public static List<Row> loadAllLaunchableRows(Context context) {
        List<Row> user = loadSortedRows(context);
        List<Row> system = loadSortedSystemRows(context);
        List<Row> all = new ArrayList<>(user.size() + system.size());
        all.addAll(user);
        all.addAll(system);
        Collections.sort(all, Comparator.comparing(r -> r.label, String.CASE_INSENSITIVE_ORDER));
        return all;
    }

    private static List<Row> loadLauncherRows(Context context, boolean systemAppsOnly) {
        PackageManager pm = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherApps = pm.queryIntentActivities(mainIntent, 0);
        if (launcherApps == null || launcherApps.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, ResolveInfo> bestByPackage = new HashMap<>();
        for (ResolveInfo info : launcherApps) {
            try {
                if (info.activityInfo == null || info.activityInfo.packageName == null) {
                    continue;
                }
                String pkg = info.activityInfo.packageName;
                if (pkg.isEmpty() || "com.mapcontrol".equals(pkg)) {
                    continue;
                }
                ResolveInfo existing = bestByPackage.get(pkg);
                if (existing == null || isBetterLauncherResolve(info, existing)) {
                    bestByPackage.put(pkg, info);
                }
            } catch (Exception ignored) {
            }
        }
        List<Row> out = new ArrayList<>();
        for (ResolveInfo info : bestByPackage.values()) {
            try {
                String pkg = info.activityInfo.packageName;
                ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                boolean isSystem = isSystemOrPrivApp(appInfo);
                if (systemAppsOnly != isSystem) {
                    continue;
                }
                CharSequence labelCs = info.loadLabel(pm);
                String appName = labelCs != null ? labelCs.toString() : null;
                if (appName == null || appName.trim().isEmpty()) {
                    appName = pm.getApplicationLabel(appInfo).toString();
                }
                if (appName == null || appName.trim().isEmpty()) {
                    appName = pkg;
                }
                String activityName = info.activityInfo.name;
                out.add(new Row(appName.trim(), pkg, activityName));
            } catch (Exception ignored) {
            }
        }
        Collections.sort(out, Comparator.comparing(r -> r.label, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /**
     * Aynı pakette birden fazla LAUNCHER activity varken (DesaySV) öncelik + activity ikon kaynağı
     * ile OEM launcher’ın gösterdiğine yakın olanı seç.
     */
    private static boolean isBetterLauncherResolve(ResolveInfo candidate, ResolveInfo current) {
        if (candidate.priority != current.priority) {
            return candidate.priority > current.priority;
        }
        int candidateIcon = candidate.activityInfo != null ? candidate.activityInfo.icon : 0;
        int currentIcon = current.activityInfo != null ? current.activityInfo.icon : 0;
        if (candidateIcon != 0 && currentIcon == 0) {
            return true;
        }
        if (candidateIcon == 0 && currentIcon != 0) {
            return false;
        }
        if (candidate.isDefault && !current.isDefault) {
            return true;
        }
        return false;
    }
}
