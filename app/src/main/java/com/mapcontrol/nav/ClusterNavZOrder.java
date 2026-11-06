package com.mapcontrol.nav;

import android.content.Context;

/** Cluster harita overlay'lerini z-sırası için tek noktadan öne alır. */
public final class ClusterNavZOrder {
    private ClusterNavZOrder() {
    }

    public static void raiseIfShowing(Context context) {
        GoogleMapsClusterNavOverlay.getInstance(context).raiseToTopIfShowing();
        YandexClusterNavOverlay.getInstance(context).raiseToTopIfShowing();
    }
}
