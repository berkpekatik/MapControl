package com.mapcontrol.nav;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Yandex Maps sürecinin çalışıp çalışmadığını kabaca doğrular.
 */
public final class YandexMapsPresence {

    private YandexMapsPresence() {
    }

    public static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(
                    YandexNavScraper.PACKAGE_YANDEX_MAPS, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Yandex Maps süreci çalışıyor mu (arka planda dahil).
     */
    public static boolean isLikelyRunning(Context context) {
        if (!isInstalled(context)) {
            return false;
        }
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return false;
        }
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes == null) {
            return false;
        }
        String pkg = YandexNavScraper.PACKAGE_YANDEX_MAPS;
        for (ActivityManager.RunningAppProcessInfo info : processes) {
            if (info == null) {
                continue;
            }
            if (pkg.equals(info.processName)) {
                return true;
            }
            if (info.pkgList != null) {
                for (String p : info.pkgList) {
                    if (pkg.equals(p)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    public static String foregroundPackage(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return null;
        }
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes == null) {
            return null;
        }
        for (ActivityManager.RunningAppProcessInfo info : processes) {
            if (info == null) {
                continue;
            }
            if (info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && info.pkgList != null
                    && info.pkgList.length > 0) {
                return info.pkgList[0];
            }
        }
        return null;
    }

    public static boolean isForeground(Context context) {
        return YandexNavScraper.PACKAGE_YANDEX_MAPS.equals(foregroundPackage(context));
    }
}
