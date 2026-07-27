package com.mapcontrol.util;

import android.content.Context;
import android.content.Intent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Yansıtma/cluster açık durumu: {@link com.mapcontrol.service.MapControlService} ile
 * {@link com.mapcontrol.ui.activity.MainActivity} (tuş toggle için {@code isNavigationOpen}) senkronu.
 */
public final class ClusterNavigationState {

    public static final String ACTION_NAVIGATION_CLUSTER_STATE =
            "com.mapcontrol.action.NAVIGATION_CLUSTER_STATE";
    public static final String EXTRA_IS_OPEN = "navigationClusterOpen";

    private static final AtomicBoolean LAST_KNOWN_OPEN = new AtomicBoolean(false);

    private ClusterNavigationState() {
    }

    public static void setLastKnownOpen(boolean isOpen) {
        LAST_KNOWN_OPEN.set(isOpen);
    }

    public static boolean getLastKnownOpen() {
        return LAST_KNOWN_OPEN.get();
    }

    /** Servis üzerinden aç/kapa sonrası: son bilinen durum + paket içi yayın. */
    public static void publishFromService(Context context, boolean isOpen) {
        LAST_KNOWN_OPEN.set(isOpen);
        Intent br = new Intent(ACTION_NAVIGATION_CLUSTER_STATE);
        br.setPackage(context.getApplicationContext().getPackageName());
        br.putExtra(EXTRA_IS_OPEN, isOpen);
        context.getApplicationContext().sendBroadcast(br);
    }
}
