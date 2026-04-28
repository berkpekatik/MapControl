package com.mapcontrol.util;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import androidx.annotation.NonNull;

/**
 * Uygulama kullanım erişimi. Kullanıcı, özel erişim ekranından açar; yetki
 * {@link android.Manifest.permission#PACKAGE_USAGE_STATS} ile aynı kapıdır.
 */
public final class UsageAccessHelper {

    private UsageAccessHelper() {
    }

    public static void openUsageAccessSettings(@NonNull Context anyContext) {
        Context c = anyContext.getApplicationContext();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                c.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else {
                c.startActivity(new Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean hasPackageUsageAccess(@NonNull Context anyContext) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return false;
        }
        if (anyContext.getSystemService(Context.USAGE_STATS_SERVICE) == null) {
            return false;
        }
        try {
            AppOpsManager aom = (AppOpsManager) anyContext.getSystemService(Context.APP_OPS_SERVICE);
            if (aom == null) {
                return false;
            }
            int mode = aom.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    anyContext.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }
}
