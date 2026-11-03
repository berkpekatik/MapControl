package com.mapcontrol.trip;

import java.util.ArrayList;
import java.util.List;

/** Kapanmış bir yolculuk. */
public final class TripRecord {

    public static final class Point {
        public final double lat;
        public final double lon;
        public final int speedKmh;

        public Point(double lat, double lon, int speedKmh) {
            this.lat = lat;
            this.lon = lon;
            this.speedKmh = speedKmh;
        }
    }

    public final String id;
    public final long startedAt;
    public final long endedAt;
    public final int odoStartKm;
    public final int odoEndKm;
    public final int distanceKm;
    public final int fuelStartPercent;
    public final int fuelEndPercent;
    public final int fuelUsedMl;
    public final int rangeStartKm;
    public final int rangeEndKm;
    public final int avgSpeedKmh;
    public final int maxSpeedKmh;
    public final long movingMs;
    public final double latStart;
    public final double lonStart;
    public final double latEnd;
    public final double lonEnd;
    public final List<Point> route;

    public TripRecord(String id, long startedAt, long endedAt, int odoStartKm, int odoEndKm,
                      int distanceKm, int fuelStartPercent, int fuelEndPercent, int fuelUsedMl,
                      int rangeStartKm, int rangeEndKm, int avgSpeedKmh, int maxSpeedKmh,
                      long movingMs, double latStart, double lonStart, double latEnd, double lonEnd,
                      List<Point> route) {
        this.id = id;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.odoStartKm = odoStartKm;
        this.odoEndKm = odoEndKm;
        this.distanceKm = distanceKm;
        this.fuelStartPercent = fuelStartPercent;
        this.fuelEndPercent = fuelEndPercent;
        this.fuelUsedMl = fuelUsedMl;
        this.rangeStartKm = rangeStartKm;
        this.rangeEndKm = rangeEndKm;
        this.avgSpeedKmh = avgSpeedKmh;
        this.maxSpeedKmh = maxSpeedKmh;
        this.movingMs = movingMs;
        this.latStart = latStart;
        this.lonStart = lonStart;
        this.latEnd = latEnd;
        this.lonEnd = lonEnd;
        this.route = new ArrayList<>(route);
    }

    public int movingMinutes() {
        return (int) Math.round(movingMs / 60000.0);
    }
}
