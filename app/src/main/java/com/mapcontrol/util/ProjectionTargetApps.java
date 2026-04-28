package com.mapcontrol.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        public Row(String label, String packageName) {
            this.label = label != null ? label : "";
            this.packageName = packageName != null ? packageName : "";
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
        PackageManager pm = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherApps = pm.queryIntentActivities(mainIntent, 0);
        if (launcherApps == null || launcherApps.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> seen = new HashSet<>();
        List<Row> out = new ArrayList<>();
        for (ResolveInfo info : launcherApps) {
            try {
                String pkg = info.activityInfo.packageName;
                if (pkg == null || pkg.isEmpty() || seen.contains(pkg)) {
                    continue;
                }
                seen.add(pkg);
                if ("com.mapcontrol".equals(pkg)) {
                    continue;
                }
                ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                if (isSystemOrPrivApp(appInfo)) {
                    continue;
                }
                String appName = pm.getApplicationLabel(appInfo).toString();
                if (appName == null || appName.trim().isEmpty()) {
                    appName = pkg;
                }
                out.add(new Row(appName.trim(), pkg));
            } catch (Exception ignored) {
            }
        }
        Collections.sort(out, Comparator.comparing(r -> r.label, String.CASE_INSENSITIVE_ORDER));
        return out;
    }
}
