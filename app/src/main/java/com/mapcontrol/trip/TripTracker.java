package com.mapcontrol.trip;

import java.util.ArrayList;
import java.util.List;

/**
 * Kontak kenarlarından yolculuk çıkaran durum makinesi. Android'e bağlı değildir.
 *
 * <ul>
 *   <li>Başlangıç: {@code ID_SYSTEM_POWER_MODE} 1 (hazır) veya 2 (motor) olunca.</li>
 *   <li>Bitiş: sinyal 0 (veya okunamayan) olunca; {@link #END_DEBOUNCE_MS} boyunca 0 kalırsa
 *       yolculuk kapanır. Bu sürede güç geri gelirse aynı yolculuk sürer (kısa aksesuar blip'i).</li>
 *   <li>Yol = odometre farkı, yakıt = {@code ID_SUM_FUEL} farkı (ml). Kapanış değerleri
 *       0'a düştüğü andaki okumadır, debounce sonundaki değil.</li>
 *   <li>Yakıt sayacı kontakta henüz okunmamış olabilir (veri gelince dolar) ve kapanışta sıfırlanabilir:
 *       başlangıç, yolculuk içindeki ilk geçerli okuma; bitiş, görülen en büyük geçerli okumadır
 *       (birikmiş sayaç geri gitmez).</li>
 * </ul>
 */
public final class TripTracker {

    public interface Listener {
        void onTripClosed(TripRecord trip);

        /** Tanı iletisi (yolculuk başladı/bitti ve ham yakıt sayacı); Günlük sekmesine yazılır. */
        default void onDiagnostic(String message) {
        }
    }

    /** Güç bu kadar 0 kalırsa yolculuk biter. */
    public static final long END_DEBOUNCE_MS = 60_000L;

    /** İki örnek arası bu süreden uzunsa fazlası hareket süresine eklenmez (veri kesintisi). */
    static final long MAX_SAMPLE_GAP_MS = 10_000L;

    /** Rota noktaları en sık bu aralıkla yazılır. */
    static final long ROUTE_INTERVAL_MS = 5_000L;

    private enum State { IDLE, ACTIVE, ENDING }

    private final Listener listener;

    private State state = State.IDLE;
    private long startedAt;
    private TripReading startReading;
    private long zeroSince;
    private TripReading endReading;
    private TripReading lastReading;

    /** Yolculuktaki ilk geçerli yakıt sayacı ve görülen en büyük geçerli değer; geçersizse {@link TripReading#INVALID}. */
    private int fuelBaseline = TripReading.INVALID;
    private int fuelMax = TripReading.INVALID;

    private long lastSampleAt;
    private long movingMs;
    private int maxSpeed;
    private long lastPointAt;
    private final List<TripRecord.Point> route = new ArrayList<>();

    public TripTracker(Listener listener) {
        this.listener = listener;
    }

    public static boolean isPowerOn(int powerMode) {
        return powerMode == 1 || powerMode == 2;
    }

    public boolean isActive() {
        return state != State.IDLE;
    }

    /**
     * Her örnekte çağrılır (birkaç saniyede bir). [powerMode] okunamıyorsa -1 verilir.
     */
    public void update(int powerMode, long nowMs, TripReading reading) {
        boolean on = isPowerOn(powerMode);
        switch (state) {
            case IDLE:
                if (on) {
                    begin(nowMs, reading);
                }
                break;
            case ACTIVE:
                if (on) {
                    sample(nowMs, reading);
                } else {
                    state = State.ENDING;
                    zeroSince = nowMs;
                    endReading = reading;
                }
                break;
            case ENDING:
                if (on) {
                    // Aksesuar blip'i: aynı yolculuk sürüyor, boşluk hareket süresine girmez.
                    state = State.ACTIVE;
                    endReading = null;
                    lastSampleAt = nowMs;
                    sample(nowMs, reading);
                } else if (nowMs - zeroSince >= END_DEBOUNCE_MS) {
                    close();
                }
                break;
            default:
                break;
        }
    }

    private void begin(long nowMs, TripReading reading) {
        state = State.ACTIVE;
        startedAt = nowMs;
        startReading = reading;
        lastReading = reading;
        lastSampleAt = nowMs;
        movingMs = 0;
        maxSpeed = 0;
        lastPointAt = Long.MIN_VALUE / 2; // ilk nokta hemen yazılabilsin
        route.clear();
        fuelBaseline = TripReading.INVALID;
        fuelMax = TripReading.INVALID;
        sample(nowMs, reading);
        listener.onDiagnostic("Yolculuk başladı: odometre=" + describe(reading.odoKm) + " km, yakıt sayacı="
                + describe(reading.sumFuelMl) + (fuelBaseline == TripReading.INVALID ? " (henüz okunmadı)" : ""));
    }

    private void sample(long nowMs, TripReading reading) {
        lastReading = reading;
        trackFuel(reading);
        int speed = reading.speedKmh == TripReading.INVALID ? 0 : Math.max(0, reading.speedKmh);
        long dt = Math.min(Math.max(0, nowMs - lastSampleAt), MAX_SAMPLE_GAP_MS);
        if (speed > 0) {
            movingMs += dt;
            maxSpeed = Math.max(maxSpeed, speed);
            if (reading.hasLocation() && nowMs - lastPointAt >= ROUTE_INTERVAL_MS) {
                route.add(new TripRecord.Point(reading.lat, reading.lon, speed));
                lastPointAt = nowMs;
            }
        }
        lastSampleAt = nowMs;
    }

    private void trackFuel(TripReading reading) {
        if (!TripReading.validCounter(reading.sumFuelMl)) {
            return;
        }
        if (fuelBaseline == TripReading.INVALID) {
            fuelBaseline = reading.sumFuelMl;
        }
        fuelMax = fuelMax == TripReading.INVALID ? reading.sumFuelMl : Math.max(fuelMax, reading.sumFuelMl);
    }

    private static String describe(int value) {
        return value == TripReading.INVALID ? "okunamadı" : Integer.toString(value);
    }

    private void close() {
        TripReading end = endReading != null ? endReading : lastReading;
        state = State.IDLE;
        endReading = null;

        int distance = 0;
        if (TripReading.validOdo(startReading.odoKm) && TripReading.validOdo(end.odoKm)) {
            distance = Math.max(0, end.odoKm - startReading.odoKm);
        }
        trackFuel(end);
        int fuelMl = 0;
        if (fuelBaseline != TripReading.INVALID && fuelMax != TripReading.INVALID) {
            fuelMl = Math.max(0, fuelMax - fuelBaseline);
        }
        listener.onDiagnostic("Yolculuk bitti: " + distance + " km, yakıt sayacı " + describe(fuelBaseline)
                + " → " + describe(fuelMax) + " (fark " + fuelMl + " birim)");
        // Yol almadan, hareket etmeden açılıp kapanan kontak yolculuk sayılmaz.
        if (distance == 0 && movingMs == 0) {
            return;
        }
        int avg = movingMs > 0 ? (int) Math.round(distance / (movingMs / 3_600_000.0)) : 0;

        listener.onTripClosed(new TripRecord(
                "t" + startedAt,
                startedAt,
                zeroSince,
                startReading.odoKm,
                end.odoKm,
                distance,
                TripReading.percent(startReading.fuelPercent),
                TripReading.percent(end.fuelPercent),
                fuelMl,
                startReading.rangeKm,
                end.rangeKm,
                avg,
                maxSpeed,
                movingMs,
                startReading.hasLocation() ? startReading.lat : Double.NaN,
                startReading.hasLocation() ? startReading.lon : Double.NaN,
                end.hasLocation() ? end.lat : Double.NaN,
                end.hasLocation() ? end.lon : Double.NaN,
                route));
    }
}
