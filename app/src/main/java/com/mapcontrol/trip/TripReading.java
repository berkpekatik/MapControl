package com.mapcontrol.trip;

import com.mapcontrol.vehicle.VehicleMetricsSnapshot;

/**
 * Bir andaki araç sayaçları. Okunamayan alan {@link #INVALID}, konum yoksa {@code NaN}.
 * Birimler: odometre km, birikmiş yakıt ml ({@code ID_SUM_FUEL}, 1 birim = 1 ml), hız km/s.
 */
public final class TripReading {

    public static final int INVALID = Integer.MIN_VALUE;

    public final int odoKm;
    public final int sumFuelMl;
    public final int fuelPercent;
    public final int rangeKm;
    public final int speedKmh;
    public final double lat;
    public final double lon;

    public TripReading(int odoKm, int sumFuelMl, int fuelPercent, int rangeKm, int speedKmh,
                       double lat, double lon) {
        this.odoKm = odoKm;
        this.sumFuelMl = sumFuelMl;
        this.fuelPercent = fuelPercent;
        this.rangeKm = rangeKm;
        this.speedKmh = speedKmh;
        this.lat = lat;
        this.lon = lon;
    }

    public boolean hasLocation() {
        return !Double.isNaN(lat) && !Double.isNaN(lon);
    }

    /** jconfig ile aynı: 9.999.999 üstü ve negatif (0xFFFFFFFF dahil) odometre geçersizdir. */
    static boolean validOdo(int km) {
        return km != INVALID && km >= 0 && km <= 9_999_999;
    }

    /** Birikmiş yakıt sayacı: negatif değer (0xFFFFFFFF dahil) geçersizdir. */
    static boolean validCounter(int value) {
        return value != INVALID && value >= 0;
    }

    /** Depo değeri her zaman onda bir yüzdedir (694 → %69); ekranla aynı yuvarlama ({@link VehicleMetricsSnapshot#fuelPercentRounded(int)}). */
    static int percent(int raw) {
        int rounded = VehicleMetricsSnapshot.fuelPercentRounded(raw);
        return rounded < 0 ? INVALID : rounded;
    }
}
