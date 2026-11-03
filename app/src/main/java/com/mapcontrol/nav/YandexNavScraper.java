package com.mapcontrol.nav;

import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Yandex Maps ({@code ru.yandex.yandexmaps}) navigasyon kartlarını view-id ile okur.
 */
public final class YandexNavScraper {

    public static final String PACKAGE_YANDEX_MAPS = "ru.yandex.yandexmaps";

    private static final String SUFFIX_MANEUVER_DISTANCE = "text_maneuverballoon_distance";
    private static final String SUFFIX_MANEUVER_UNIT = "text_maneuverballoon_metrics";
    private static final String SUFFIX_NEXT_STREET = "text_nextstreet";
    private static final String SUFFIX_ETA_DISTANCE = "textview_eta_distance";
    private static final String SUFFIX_ETA_ARRIVAL = "textview_eta_arrival";
    private static final String SUFFIX_ETA_TIME = "textview_eta_time";
    private static final String SUFFIX_SPEED = "text_speed_value";
    private static final String SUFFIX_SPEED_LIMIT = "text_speedlimit";
    private static final String SUFFIX_STATUS = "status_panel_text";

    private YandexNavScraper() {
    }

    public static YandexNavSnapshot scrape(@Nullable AccessibilityNodeInfo root) {
        if (root == null) {
            return YandexNavSnapshot.inactive();
        }
        try {
            root.refresh();
        } catch (Exception ignored) {
        }

        Map<String, String> tree = new HashMap<>();
        collectBySuffix(root, tree);

        String maneuverDistance = tree.get(SUFFIX_MANEUVER_DISTANCE);
        String maneuverUnit = tree.get(SUFFIX_MANEUVER_UNIT);
        String nextStreet = tree.get(SUFFIX_NEXT_STREET);
        String etaDistance = tree.get(SUFFIX_ETA_DISTANCE);
        String etaArrival = tree.get(SUFFIX_ETA_ARRIVAL);
        String etaTime = tree.get(SUFFIX_ETA_TIME);
        String speed = tree.get(SUFFIX_SPEED);
        String speedLimit = tree.get(SUFFIX_SPEED_LIMIT);
        String statusPanel = tree.get(SUFFIX_STATUS);

        boolean active = nonEmpty(maneuverDistance)
                || nonEmpty(etaDistance)
                || nonEmpty(etaArrival)
                || nonEmpty(etaTime)
                || nonEmpty(nextStreet)
                || nonEmpty(statusPanel)
                || nonEmpty(speed);

        if (!active) {
            return YandexNavSnapshot.inactive();
        }
        return new YandexNavSnapshot(
                true,
                maneuverDistance,
                maneuverUnit,
                nextStreet,
                etaDistance,
                etaArrival,
                etaTime,
                speed,
                speedLimit,
                statusPanel);
    }

    private static void collectBySuffix(AccessibilityNodeInfo node, Map<String, String> out) {
        if (node == null) {
            return;
        }
        try {
            String id = node.getViewIdResourceName();
            if (id != null) {
                putIfEmpty(out, suffixKey(id), nodeText(node));
            }
            int count = node.getChildCount();
            for (int i = 0; i < count; i++) {
                AccessibilityNodeInfo child = null;
                try {
                    child = node.getChild(i);
                    if (child != null) {
                        collectBySuffix(child, out);
                    }
                } catch (Exception ignored) {
                } finally {
                    if (child != null) {
                        try {
                            child.recycle();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private static String suffixKey(String viewId) {
        if (viewId.endsWith(SUFFIX_MANEUVER_DISTANCE)) {
            return SUFFIX_MANEUVER_DISTANCE;
        }
        if (viewId.endsWith(SUFFIX_MANEUVER_UNIT)) {
            return SUFFIX_MANEUVER_UNIT;
        }
        if (viewId.endsWith(SUFFIX_NEXT_STREET)) {
            return SUFFIX_NEXT_STREET;
        }
        if (viewId.endsWith(SUFFIX_ETA_DISTANCE)) {
            return SUFFIX_ETA_DISTANCE;
        }
        if (viewId.endsWith(SUFFIX_ETA_ARRIVAL)) {
            return SUFFIX_ETA_ARRIVAL;
        }
        if (viewId.endsWith(SUFFIX_ETA_TIME)) {
            return SUFFIX_ETA_TIME;
        }
        if (viewId.endsWith(SUFFIX_SPEED)) {
            return SUFFIX_SPEED;
        }
        if (viewId.endsWith(SUFFIX_SPEED_LIMIT)) {
            return SUFFIX_SPEED_LIMIT;
        }
        if (viewId.endsWith(SUFFIX_STATUS)) {
            return SUFFIX_STATUS;
        }
        return null;
    }

    private static void putIfEmpty(Map<String, String> out, @Nullable String key, @Nullable String value) {
        if (key == null || !nonEmpty(value) || out.containsKey(key)) {
            return;
        }
        out.put(key, value);
    }

    @Nullable
    private static String nodeText(AccessibilityNodeInfo node) {
        CharSequence text = node.getText();
        if (text == null || text.length() == 0) {
            text = node.getContentDescription();
        }
        if (text == null) {
            return null;
        }
        String s = text.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean nonEmpty(@Nullable String s) {
        return s != null && !s.isEmpty();
    }
}
