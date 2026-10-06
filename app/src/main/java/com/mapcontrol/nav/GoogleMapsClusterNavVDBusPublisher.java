package com.mapcontrol.nav;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carlan.VDEventCarLan;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviRoadInfo;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviTotalInfo2;
import com.mapcontrol.util.TargetPackageStore;

/**
 * Google Maps bildirim özetini CarLan VDBus navigasyon alanlarına yansıtır.
 */
public final class GoogleMapsClusterNavVDBusPublisher {

    private static final String TAG = "GoogleMapsClusterVDBus";
    public static final String PREF_ENABLED = "google_maps_cluster_nav_vdbus";

    private GoogleMapsClusterNavVDBusPublisher() {
    }

    public static boolean isEnabled(Context context) {
        return TargetPackageStore.prefs(context).getBoolean(PREF_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        TargetPackageStore.prefs(context).edit().putBoolean(PREF_ENABLED, enabled).apply();
        if (!enabled) {
            publishInactive(context.getApplicationContext());
        }
    }

    public static void publish(Context context, @Nullable GoogleMapsNavSnapshot snapshot) {
        if (!isEnabled(context)) {
            return;
        }
        Context app = context.getApplicationContext();
        if (snapshot == null || !snapshot.active) {
            publishInactive(app);
            return;
        }
        try {
            VDNaviRoadInfo road = new VDNaviRoadInfo();
            road.setSegRemainDis(snapshot.segmentRemainMeters());
            road.setNextNaviActiion(GoogleMapsClusterNavManeuverMapper.mapFromNotificationText(
                    snapshot.distanceTitle, snapshot.maneuverHint));
            road.setNextRoadName(firstNonEmpty(
                    snapshot.formatManeuverDirectionLine(),
                    snapshot.maneuverHint,
                    ""));
            road.setRoadName("");
            road.setNextNaviActionProgbar(0);
            road.setIntersectionZoomStatus(0);
            VDEvent roadEvent = VDNaviRoadInfo.createEvent(
                    VDEventCarLan.NAVIGATION_ROAD_INFO, road);
            VDBus.getDefault().set(roadEvent);

            VDNaviTotalInfo2 total = new VDNaviTotalInfo2();
            total.setRemDistance(firstNonEmpty(snapshot.etaDistanceLabel(), ""));
            total.setRemDistanceUint("km");
            total.setArrivalTime(firstNonEmpty(snapshot.etaArrivalLabel(), ""));
            total.setTimeLeft(parseDurationMinutes(snapshot.etaDurationLabel()));
            VDEvent totalEvent = VDNaviTotalInfo2.createEvent(
                    VDEventCarLan.NAVIGATION_TOTAL_INFO2, total);
            VDBus.getDefault().set(totalEvent);
        } catch (Exception e) {
            Log.w(TAG, "VDBus publish failed", e);
        }
    }

    private static void publishInactive(Context app) {
        try {
            VDNaviRoadInfo road = new VDNaviRoadInfo();
            road.setSegRemainDis(0);
            road.setNextNaviActiion(GoogleMapsClusterNavManeuverMapper.ACTION_NONE);
            road.setNextRoadName("");
            road.setRoadName("");
            VDEvent roadEvent = VDNaviRoadInfo.createEvent(
                    VDEventCarLan.NAVIGATION_ROAD_INFO, road);
            VDBus.getDefault().set(roadEvent);
        } catch (Exception e) {
            Log.w(TAG, "VDBus clear failed", e);
        }
    }

    private static int parseDurationMinutes(@Nullable String durationLabel) {
        if (durationLabel == null) {
            return 0;
        }
        String lower = durationLabel.toLowerCase();
        int minutes = 0;
        try {
            if (lower.contains("hr") || lower.contains("sa")) {
                String[] tokens = lower.replace("dk", "min").split("\\s+");
                for (int i = 0; i < tokens.length; i++) {
                    if (tokens[i].contains("hr") || tokens[i].contains("sa")) {
                        minutes += (int) (Double.parseDouble(tokens[i - 1].replace(',', '.')) * 60);
                    }
                    if (tokens[i].contains("min") || tokens[i].equals("dk")) {
                        minutes += (int) Double.parseDouble(tokens[i - 1].replace(',', '.'));
                    }
                }
                return minutes;
            }
            if (lower.contains("min") || lower.contains("dk")) {
                String num = lower.split("(min|dk)", 2)[0].trim().replace(',', '.');
                return (int) Double.parseDouble(num);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static String firstNonEmpty(@Nullable String primary, String fallback) {
        if (primary != null && !primary.isEmpty()) {
            return primary;
        }
        return fallback;
    }

    private static String firstNonEmpty(@Nullable String a, @Nullable String b, String fallback) {
        if (a != null && !a.isEmpty()) {
            return a;
        }
        if (b != null && !b.isEmpty()) {
            return b;
        }
        return fallback;
    }
}
