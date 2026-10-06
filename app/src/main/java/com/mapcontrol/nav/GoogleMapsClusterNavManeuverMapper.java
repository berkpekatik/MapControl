package com.mapcontrol.nav;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Google Maps bildirim metninden gösterge manevra kodu tahmini.
 */
final class GoogleMapsClusterNavManeuverMapper {

    /** Bilinmeyen / genel düz devam. */
    static final int ACTION_CONTINUE = 14;
    /** Dönüş / rampa benzeri. */
    static final int ACTION_TURN = 16;
    /** Navigasyon kapalı. */
    static final int ACTION_NONE = -1;

    private GoogleMapsClusterNavManeuverMapper() {
    }

    static int mapFromNotificationText(@Nullable String distanceTitle, @Nullable String maneuverHint) {
        String combined = join(distanceTitle, maneuverHint);
        if (combined == null || combined.isEmpty()) {
            return ACTION_NONE;
        }
        String lower = combined.toLowerCase(Locale.US);
        if (containsAny(lower,
                "u-turn", "uturn", "roundabout", "rotary", "ramp", "exit",
                "turn left", "turn right", "bear left", "bear right",
                "slight left", "slight right", "sharp left", "sharp right",
                "keep left", "keep right", "fork", "merge")) {
            return ACTION_TURN;
        }
        if (containsAny(lower,
                "head ", "continue", "straight", "south", "north", "east", "west")) {
            return ACTION_CONTINUE;
        }
        return ACTION_CONTINUE;
    }

    @Nullable
    private static String join(@Nullable String a, @Nullable String b) {
        if (a == null || a.isEmpty()) {
            return b;
        }
        if (b == null || b.isEmpty()) {
            return a;
        }
        return a + " " + b;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
