package com.mapcontrol.nav;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google Maps navigasyon bildiriminin sadeleştirilmiş hali.
 */
public final class GoogleMapsNavSnapshot {

    private static final Pattern ARRIVAL_TIME = Pattern.compile(
            "\\b([01]?\\d|2[0-3]):[0-5]\\d\\b");

    public final boolean active;
    @Nullable public final String maneuverHint;
    @Nullable public final String distanceTitle;
    @Nullable public final String routeSummary;
    public final boolean hadIcon;

    public GoogleMapsNavSnapshot(
            boolean active,
            @Nullable String maneuverHint,
            @Nullable String distanceTitle,
            @Nullable String routeSummary,
            boolean hadIcon) {
        this.active = active;
        this.maneuverHint = emptyToNull(maneuverHint);
        this.distanceTitle = emptyToNull(distanceTitle);
        this.routeSummary = emptyToNull(routeSummary);
        this.hadIcon = hadIcon;
    }

    public static GoogleMapsNavSnapshot inactive() {
        return new GoogleMapsNavSnapshot(false, null, null, null, false);
    }

    /** Yansıtma sekmesi özet satırı. */
    @Nullable
    public String formatSummaryLine() {
        if (!active) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        appendPart(sb, distanceTitle);
        appendPart(sb, maneuverHint);
        appendPart(sb, routeSummary);
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void appendPart(StringBuilder sb, @Nullable String part) {
        if (part == null || part.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(" · ");
        }
        sb.append(part);
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public boolean contentEquals(@Nullable GoogleMapsNavSnapshot other) {
        if (other == null) {
            return false;
        }
        return active == other.active
                && eq(maneuverHint, other.maneuverHint)
                && eq(distanceTitle, other.distanceTitle)
                && eq(routeSummary, other.routeSummary)
                && hadIcon == other.hadIcon;
    }

    private static boolean eq(@Nullable String a, @Nullable String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    /** Manevra kartı üst satırı: bildirim başlığındaki mesafe bölümü. */
    @Nullable
    public String formatManeuverDistanceLine() {
        return splitTitleDistancePart(distanceTitle);
    }

    /** Manevra yönü: başlıktaki talimat veya hedef satırı. */
    @Nullable
    public String formatManeuverDirectionLine() {
        String fromTitle = splitTitleDirectionPart(distanceTitle);
        if (fromTitle != null) {
            return fromTitle;
        }
        return maneuverHint;
    }

    @Nullable
    public String etaDistanceLabel() {
        return pickRouteSummaryPart(true, false, false);
    }

    @Nullable
    public String etaDurationLabel() {
        return pickRouteSummaryPart(false, true, false);
    }

    @Nullable
    public String etaArrivalLabel() {
        return pickRouteSummaryPart(false, false, true);
    }

    @Nullable
    private static String splitTitleDistancePart(@Nullable String title) {
        if (title == null) {
            return null;
        }
        int idx = title.indexOf(" - ");
        if (idx < 0) {
            return title.trim().isEmpty() ? null : title.trim();
        }
        String part = title.substring(0, idx).trim();
        return part.isEmpty() ? null : part;
    }

    @Nullable
    private static String splitTitleDirectionPart(@Nullable String title) {
        if (title == null) {
            return null;
        }
        int idx = title.indexOf(" - ");
        if (idx < 0) {
            return null;
        }
        String part = title.substring(idx + 3).trim();
        return part.isEmpty() ? null : part;
    }

    @Nullable
    private String pickRouteSummaryPart(boolean wantDistance, boolean wantDuration, boolean wantArrival) {
        if (routeSummary == null || routeSummary.isEmpty()) {
            return null;
        }
        String[] parts = routeSummary.split(" · ");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String lower = trimmed.toLowerCase(Locale.US);
            boolean isDistance = lower.contains(" km") || lower.contains(" mi") || lower.contains(" m ");
            boolean isDuration = lower.contains("min") || lower.contains("dk")
                    || lower.contains("hr") || lower.contains("sa");
            boolean isArrival = ARRIVAL_TIME.matcher(trimmed).find();
            if (wantDistance && isDistance && !isDuration) {
                return trimmed;
            }
            if (wantDuration && isDuration) {
                return trimmed;
            }
            if (wantArrival && isArrival) {
                return trimmed;
            }
        }
        if (wantArrival) {
            Matcher matcher = ARRIVAL_TIME.matcher(routeSummary);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return null;
    }
}
