package com.mapcontrol.util;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;

public final class AppLaunchHelper {
    private AppLaunchHelper() {}

    /**
     * Cluster / secondary display id (same search strategy as ClusterDisplayManager).
     */
    public static int getClusterDisplayId(Context context) {
        try {
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (dm == null) {
                return 2;
            }
            Display[] displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
            if (displays.length == 0) {
                displays = dm.getDisplays();
            }
            for (Display d : displays) {
                if (d.getDisplayId() != Display.DEFAULT_DISPLAY) {
                    return d.getDisplayId();
                }
            }
        } catch (Exception ignored) {
        }
        return 2;
    }

    /**
     * Relaunch app on default display. Skips {@code com.mapcontrol}.
     */
    public static void moveAppToDefaultDisplay(Context context, String packageName) {
        if (packageName == null || "com.mapcontrol".equals(packageName)) {
            return;
        }
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(packageName);
        if (intent == null) {
            return;
        }
        ActivityOptions opts = ActivityOptions.makeBasic();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            opts.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        context.startActivity(intent, opts.toBundle());
    }

    /**
     * Launch package on a specific display. Uses {@link Intent#FLAG_ACTIVITY_NEW_TASK} OR'd with {@code additionalFlags}.
     */
    public static void launchAppOnDisplay(Context context, String packageName, int displayId, int additionalFlags) {
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(packageName);
        if (intent == null) {
            return;
        }
        ActivityOptions opts = ActivityOptions.makeBasic();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            opts.setLaunchDisplayId(displayId);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | additionalFlags);
        context.startActivity(intent, opts.toBundle());
    }
}
