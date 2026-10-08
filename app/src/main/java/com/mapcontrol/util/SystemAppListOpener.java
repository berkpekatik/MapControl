package com.mapcontrol.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Android'in kendi uygulama listesi (sistem launcher tepsi ekranı).
 * MapControl ızgarası değil; cihazda dışa açık app-list aktivitesi aranır.
 */
public final class SystemAppListOpener {

    private static final String ACTION_ALL_APPS = "android.intent.action.ALL_APPS";

    private static final String[][] KNOWN_COMPONENTS = {
            {"com.desaysv.launcher", "com.desaysv.launcher.ui.activity.AppListActivity"},
            {"com.desaysv.launcher", "com.desaysv.launcher.ui.activity.AllAppActivity"},
            {"com.desaysv.launcher", "com.desaysv.launcher.ui.activity.AllAppsActivity"},
            {"com.desaysv.launcher", "com.desaysv.launcher.ui.activity.MoreAppActivity"},
            {"com.desaysv.launcher", "com.desaysv.launcher.activity.AppListActivity"},
            {"com.desaysv.launcher", "com.desaysv.launcher.activity.AllAppsActivity"},
            {"com.desaysv.launcher", "com.desaysv.launcher.activity.AppsActivity"},
            {"com.android.launcher3", "com.android.launcher3.AllAppsActivity"},
            {"com.android.car.carlauncher", "com.android.car.carlauncher.AppGridActivity"},
    };

    @Nullable
    private static ComponentName cached;

    private SystemAppListOpener() {
    }

    public static boolean open(Context context) {
        Context app = context.getApplicationContext();
        PackageManager pm = app.getPackageManager();
        String self = app.getPackageName();

        if (cached != null && start(app, cached)) {
            return true;
        }
        cached = null;

        ComponentName fromAction = resolveAction(pm, self, ACTION_ALL_APPS);
        if (fromAction != null && start(app, fromAction)) {
            cached = fromAction;
            return true;
        }

        for (String[] pair : KNOWN_COMPONENTS) {
            ComponentName component = new ComponentName(pair[0], pair[1]);
            if (isExported(pm, component) && start(app, component)) {
                cached = component;
                return true;
            }
        }

        ComponentName scanned = scan(pm, self);
        if (scanned != null && start(app, scanned)) {
            cached = scanned;
            return true;
        }

        ComponentName stockHome = stockHome(pm, self);
        if (stockHome != null && start(app, stockHome)) {
            cached = stockHome;
            return true;
        }
        return false;
    }

    @Nullable
    private static ComponentName resolveAction(PackageManager pm, String self, String action) {
        Intent intent = new Intent(action);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        List<ResolveInfo> matches = pm.queryIntentActivities(intent, 0);
        ComponentName best = null;
        int bestScore = -1;
        for (ResolveInfo info : matches) {
            if (info.activityInfo == null || self.equals(info.activityInfo.packageName)) {
                continue;
            }
            int score = score(info.activityInfo.packageName, info.activityInfo.name);
            if (score > bestScore) {
                bestScore = score;
                best = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            }
        }
        return best;
    }

    @Nullable
    private static ComponentName scan(PackageManager pm, String self) {
        List<PackageInfo> packages;
        try {
            packages = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES);
        } catch (Exception ignored) {
            return null;
        }
        ComponentName best = null;
        int bestScore = 0;
        for (PackageInfo pkg : packages) {
            if (pkg.activities == null || self.equals(pkg.packageName)) {
                continue;
            }
            if (pkg.packageName.startsWith("com.android.settings")
                    || pkg.packageName.contains("permissioncontroller")) {
                continue;
            }
            for (ActivityInfo activity : pkg.activities) {
                if (activity == null || !activity.exported || !activity.enabled) {
                    continue;
                }
                int score = score(activity.packageName, activity.name);
                if (score > bestScore) {
                    bestScore = score;
                    best = new ComponentName(activity.packageName, activity.name);
                }
            }
        }
        return bestScore > 0 ? best : null;
    }

    private static int score(String packageName, String className) {
        String pkg = packageName.toLowerCase(Locale.US);
        String simple = className.substring(className.lastIndexOf('.') + 1).toLowerCase(Locale.US);
        int score = 0;
        if (simple.contains("allapp") || simple.contains("applist") || simple.contains("appgrid")
                || simple.contains("moreapp") || simple.contains("appcenter")
                || simple.contains("appsactivity")) {
            score += 50;
        }
        if (pkg.contains("desaysv") && pkg.contains("launcher")) {
            score += 40;
        } else if (pkg.contains("launcher")) {
            score += 20;
        }
        return score;
    }

    @Nullable
    private static ComponentName stockHome(PackageManager pm, String self) {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> homes = pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo info : homes) {
            if (info.activityInfo == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            if (self.equals(pkg) || "android".equals(pkg)) {
                continue;
            }
            return new ComponentName(pkg, info.activityInfo.name);
        }
        return null;
    }

    private static boolean isExported(PackageManager pm, ComponentName component) {
        try {
            ActivityInfo info = pm.getActivityInfo(component, 0);
            return info.exported && info.enabled;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean start(Context context, ComponentName component) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(component);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
