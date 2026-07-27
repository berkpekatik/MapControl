package com.mapcontrol.nav;

import androidx.annotation.Nullable;

/**
 * Yandex Maps navigasyon UI scrap anlık durumu.
 */
public final class YandexNavSnapshot {

    public final boolean guidanceActive;
    @Nullable public final String maneuverDistance;
    @Nullable public final String maneuverUnit;
    @Nullable public final String nextStreet;
    @Nullable public final String etaDistance;
    @Nullable public final String etaArrival;
    @Nullable public final String etaTime;
    @Nullable public final String speed;
    @Nullable public final String speedLimit;
    @Nullable public final String statusPanel;

    public YandexNavSnapshot(
            boolean guidanceActive,
            @Nullable String maneuverDistance,
            @Nullable String maneuverUnit,
            @Nullable String nextStreet,
            @Nullable String etaDistance,
            @Nullable String etaArrival,
            @Nullable String etaTime,
            @Nullable String speed,
            @Nullable String speedLimit,
            @Nullable String statusPanel) {
        this.guidanceActive = guidanceActive;
        this.maneuverDistance = maneuverDistance;
        this.maneuverUnit = maneuverUnit;
        this.nextStreet = nextStreet;
        this.etaDistance = etaDistance;
        this.etaArrival = etaArrival;
        this.etaTime = etaTime;
        this.speed = speed;
        this.speedLimit = speedLimit;
        this.statusPanel = statusPanel;
    }

    public static YandexNavSnapshot inactive() {
        return new YandexNavSnapshot(
                false, null, null, null, null, null, null, null, null, null);
    }

    /** Manevra satırı: "100 m" */
    @Nullable
    public String formatManeuverLine() {
        if (maneuverDistance == null || maneuverDistance.isEmpty()) {
            return null;
        }
        if (maneuverUnit != null && !maneuverUnit.isEmpty()) {
            return maneuverDistance + " " + maneuverUnit;
        }
        return maneuverDistance;
    }

    /** ETA satırı: "2,9 km · 22:20 · 5 dk." */
    @Nullable
    public String formatEtaLine() {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, etaDistance);
        appendPart(sb, etaArrival);
        appendPart(sb, etaTime);
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void appendPart(StringBuilder sb, @Nullable String part) {
        if (part == null || part.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("  ·  ");
        }
        sb.append(part);
    }

    public boolean contentEquals(@Nullable YandexNavSnapshot other) {
        if (other == null) {
            return false;
        }
        return guidanceActive == other.guidanceActive
                && eq(maneuverDistance, other.maneuverDistance)
                && eq(maneuverUnit, other.maneuverUnit)
                && eq(nextStreet, other.nextStreet)
                && eq(etaDistance, other.etaDistance)
                && eq(etaArrival, other.etaArrival)
                && eq(etaTime, other.etaTime)
                && eq(speed, other.speed)
                && eq(speedLimit, other.speedLimit)
                && eq(statusPanel, other.statusPanel);
    }

    private static boolean eq(@Nullable String a, @Nullable String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }
}
