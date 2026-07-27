package com.mapcontrol.nav;

import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Yandex Maps ({@code ru.yandex.yandexmaps}) navigasyon kartlarını view-id ile okur.
 */
public final class YandexNavScraper {

    public static final String PACKAGE_YANDEX_MAPS = "ru.yandex.yandexmaps";

    private static final String ID_MANEUVER_DISTANCE =
            PACKAGE_YANDEX_MAPS + ":id/text_maneuverballoon_distance";
    private static final String ID_MANEUVER_UNIT =
            PACKAGE_YANDEX_MAPS + ":id/text_maneuverballoon_metrics";
    private static final String ID_NEXT_STREET =
            PACKAGE_YANDEX_MAPS + ":id/text_nextstreet";
    private static final String ID_ETA_DISTANCE =
            PACKAGE_YANDEX_MAPS + ":id/textview_eta_distance";
    private static final String ID_ETA_ARRIVAL =
            PACKAGE_YANDEX_MAPS + ":id/textview_eta_arrival";
    private static final String ID_ETA_TIME =
            PACKAGE_YANDEX_MAPS + ":id/textview_eta_time";
    private static final String ID_SPEED =
            PACKAGE_YANDEX_MAPS + ":id/text_speed_value";
    private static final String ID_SPEED_LIMIT =
            PACKAGE_YANDEX_MAPS + ":id/text_speedlimit";
    private static final String ID_STATUS_PANEL =
            PACKAGE_YANDEX_MAPS + ":id/status_panel_text";

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

        Map<String, String> tree = new HashMap<>();
        collectBySuffix(root, tree);

        String maneuverDistance = firstNonEmpty(
                textByViewId(root, ID_MANEUVER_DISTANCE), tree.get(SUFFIX_MANEUVER_DISTANCE));
        String maneuverUnit = firstNonEmpty(
                textByViewId(root, ID_MANEUVER_UNIT), tree.get(SUFFIX_MANEUVER_UNIT));
        String nextStreet = firstNonEmpty(
                textByViewId(root, ID_NEXT_STREET), tree.get(SUFFIX_NEXT_STREET));
        String etaDistance = firstNonEmpty(
                textByViewId(root, ID_ETA_DISTANCE), tree.get(SUFFIX_ETA_DISTANCE));
        String etaArrival = firstNonEmpty(
                textByViewId(root, ID_ETA_ARRIVAL), tree.get(SUFFIX_ETA_ARRIVAL));
        String etaTime = firstNonEmpty(
                textByViewId(root, ID_ETA_TIME), tree.get(SUFFIX_ETA_TIME));
        String speed = firstNonEmpty(
                textByViewId(root, ID_SPEED), tree.get(SUFFIX_SPEED));
        String speedLimit = firstNonEmpty(
                textByViewId(root, ID_SPEED_LIMIT), tree.get(SUFFIX_SPEED_LIMIT));
        String statusPanel = firstNonEmpty(
                textByViewId(root, ID_STATUS_PANEL), tree.get(SUFFIX_STATUS));

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
                AccessibilityNodeInfo child = node.getChild(i);
                if (child == null) {
                    continue;
                }
                try {
                    collectBySuffix(child, out);
                } finally {
                    child.recycle();
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

    @Nullable
    private static String textByViewId(AccessibilityNodeInfo root, String viewId) {
        List<AccessibilityNodeInfo> nodes = null;
        try {
            nodes = root.findAccessibilityNodeInfosByViewId(viewId);
            if (nodes == null || nodes.isEmpty()) {
                return null;
            }
            for (AccessibilityNodeInfo node : nodes) {
                if (node == null) {
                    continue;
                }
                try {
                    String t = nodeText(node);
                    if (nonEmpty(t)) {
                        return t;
                    }
                } finally {
                    try {
                        node.recycle();
                    } catch (Exception ignored) {
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String a, @Nullable String b) {
        if (nonEmpty(a)) {
            return a;
        }
        return nonEmpty(b) ? b : null;
    }

    private static boolean nonEmpty(@Nullable String s) {
        return s != null && !s.isEmpty();
    }
}
