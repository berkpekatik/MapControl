package com.mapcontrol.util;

import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.Log;
import android.view.Display;

public final class AppLaunchHelper {
    private static final String TAG = "AppLaunchHelper";

    public static final String GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps";
    /**
     * Google Maps car/cluster surface.
     */
    private static final String GOOGLE_MAPS_CLUSTER_ACTIVITY =
            "com.google.android.apps.gmm.car.embedded.auxiliarymap.EmbeddedClusterActivity";

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
     * Google Maps on a non-default display tries {@code EmbeddedClusterActivity} first, then the normal launcher.
     */
    public static void launchAppOnDisplay(Context context, String packageName, int displayId, int additionalFlags) {
        Intent intent = buildLaunchIntent(context, packageName, displayId);
        if (intent == null) {
            return;
        }
        boolean embedded = isGoogleMapsClusterIntent(intent);
        try {
            startOnDisplay(context, intent, displayId, additionalFlags);
            if (embedded) {
                Log.i(TAG, "Google Maps EmbeddedClusterActivity started on display " + displayId);
            }
        } catch (ActivityNotFoundException | SecurityException e) {
            if (!embedded) {
                Log.w(TAG, "launchAppOnDisplay failed for " + packageName, e);
                return;
            }
            Log.w(TAG, "EmbeddedClusterActivity failed, falling back to launcher", e);
            Intent fallback = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (fallback == null) {
                return;
            }
            try {
                startOnDisplay(context, fallback, displayId, additionalFlags);
            } catch (ActivityNotFoundException | SecurityException e2) {
                Log.w(TAG, "Google Maps launcher fallback failed", e2);
            }
        }
    }

    /**
     * Cluster display + Google Maps: embedded cluster activity when the installed Maps build exports it.
     * Default display and every other package stay on the normal launcher intent.
     */
    private static Intent buildLaunchIntent(Context context, String packageName, int displayId) {
        PackageManager pm = context.getPackageManager();
        if (GOOGLE_MAPS_PACKAGE.equals(packageName) && displayId != Display.DEFAULT_DISPLAY) {
            Intent embedded = new Intent();
            embedded.setComponent(new ComponentName(GOOGLE_MAPS_PACKAGE, GOOGLE_MAPS_CLUSTER_ACTIVITY));
            if (pm.resolveActivity(embedded, 0) != null) {
                return embedded;
            }
            Log.w(TAG, "Google Maps EmbeddedClusterActivity not resolvable, using launcher");
        }
        return pm.getLaunchIntentForPackage(packageName);
    }

    private static boolean isGoogleMapsClusterIntent(Intent intent) {
        ComponentName component = intent.getComponent();
        return component != null && GOOGLE_MAPS_CLUSTER_ACTIVITY.equals(component.getClassName());
    }

    private static void startOnDisplay(Context context, Intent intent, int displayId, int additionalFlags) {
        ActivityOptions opts = ActivityOptions.makeBasic();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            opts.setLaunchDisplayId(displayId);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | additionalFlags);
        context.startActivity(intent, opts.toBundle());
    }
}
