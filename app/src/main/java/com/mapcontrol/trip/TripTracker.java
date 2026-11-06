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

        /**
         * Sürmekte olan yolculuğun o anki hâli; aynı {@code id} ile tekrar tekrar gelir ve her seferinde öncekinin
         * yerine yazılmalıdır. Kontak kapanır kapanmaz bir kez, yolculuk sürerken de {@link #CHECKPOINT_INTERVAL_MS}
         * aralığıyla gelir. Araç 60 sn'lik bitiş beklemesi dolmadan kapansa ya da süreç ölse de yolculuk
         * son denetim noktasıyla kayıtlı kalır.
         */
        default void onTripCheckpoint(TripRecord trip) {
        }

        /**
         * Kontak kapandı ({@code powerMode == 0}) ve yolculuk kayda değerdi. Okunamayan güçte ({@code -1}) gelmez:
         * o durumda araç sürüyor olabilir. Park fotoğrafı gibi "araç şimdi durdu" işleri için.
         */
        default void onIgnitionOff(TripRecord trip) {
        }

        /** Tanı iletisi (yolculuk başladı/bitti ve ham yakıt sayacı); Günlük sekmesine yazılır. */
        default void onDiagnostic(String message) {
        }
    }

    /** Güç bu kadar 0 kalırsa yolculuk biter. */
    public static final long END_DEBOUNCE_MS = 60_000L;

    /** Yolculuk sürerken durum en sık bu aralıkla {@link Listener#onTripCheckpoint} ile bildirilir. */
    public static final long CHECKPOINT_INTERVAL_MS = 15_000L;

    /** İki örnek arası bu süreden uzunsa fazlası hareket süresine eklenmez (veri kesintisi). */
    static final long MAX_SAMPLE_GAP_MS = 10_000L;

    /** Rota noktaları en sık bu aralıkla yazılır. */
    static final long ROUTE_INTERVAL_MS = 5_000L;

    private enum State { IDLE, ACTIVE, ENDING }

    /**
     * Telefona gösterilen anlık kayıt durumu ({@code /vehicle/now} içindeki {@code trip}). Kapanış beklemesi
     * ({@link State#ENDING}) dahil yolculuk kapanana kadar {@code active} true kalır.
     */
    public static final class Status {
        static final Status IDLE = new Status(false, 0L, 0, false);

        public final boolean active;
        public final long startedAt;
        public final int routePoints;

        /** Kontak kapandı, 60 sn'lik kapanış beklemesi sürüyor: kayıt diskte yazılı ve güç dönmedikçe kesinleşmiş sayılır. */
        public final boolean ending;

        Status(boolean active, long startedAt, int routePoints, boolean ending) {
            this.active = active;
            this.startedAt = startedAt;
            this.routePoints = routePoints;
            this.ending = ending;
        }
    }

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
    private long lastCheckpointAt;
    private final List<TripRecord.Point> route = new ArrayList<>();

    /** HTTP iş parçacığı okur, örnekleri işleyen iş parçacığı yazar; değişmez nesne yayımlanır. */
    private volatile Status status = Status.IDLE;

    public TripTracker(Listener listener) {
        this.listener = listener;
    }

    public static boolean isPowerOn(int powerMode) {
        return powerMode == 1 || powerMode == 2;
    }

    public boolean isActive() {
        return state != State.IDLE;
    }

    /** Son işlenen örnekteki kayıt durumu; her iş parçacığından okunabilir. */
    public Status status() {
        return status;
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
                    if (nowMs - lastCheckpointAt >= CHECKPOINT_INTERVAL_MS) {
                        checkpoint(nowMs);
                    }
                } else {
                    state = State.ENDING;
                    zeroSince = nowMs;
                    endReading = reading;
                    // Kimse 60 sn beklemez, araç hemen uykuya geçebilir: yolculuk şimdi kayda geçer.
                    TripRecord saved = checkpoint(nowMs);
                    if (powerMode == 0 && saved != null) {
                        listener.onIgnitionOff(saved);
                    }
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
        status = state == State.IDLE ? Status.IDLE : new Status(true, startedAt, route.size(), state == State.ENDING);
    }

    private void begin(long nowMs, TripReading reading) {
        state = State.ACTIVE;
        startedAt = nowMs;
        startReading = reading;
        lastReading = reading;
        lastSampleAt = nowMs;
        lastCheckpointAt = nowMs;
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

    /** Sürmekte olan yolculuğun anlık kaydı; kaydedilmeye değer değilse bildirilmez. */
    private TripRecord checkpoint(long nowMs) {
        lastCheckpointAt = nowMs;
        TripReading end = endReading != null ? endReading : lastReading;
        TripRecord record = build(end, state == State.ENDING ? zeroSince : nowMs);
        if (record != null) {
            listener.onTripCheckpoint(record);
        }
        return record;
    }

    private void close() {
        TripReading end = endReading != null ? endReading : lastReading;
        state = State.IDLE;
        endReading = null;

        trackFuel(end);
        int distance = distanceTo(end);
        int fuelMl = fuelUsedMl();
        listener.onDiagnostic("Yolculuk bitti: " + distance + " km, yakıt sayacı " + describe(fuelBaseline)
                + " → " + describe(fuelMax) + " (fark " + fuelMl + " birim)");
        TripRecord record = build(end, zeroSince);
        if (record != null) {
            listener.onTripClosed(record);
        }
    }

    /** Yol: odometre farkı. Kapanış okuması geçersizse son geçerli örnek kullanılır. */
    private int distanceTo(TripReading end) {
        int endOdo = TripReading.validOdo(end.odoKm) ? end.odoKm : lastReading.odoKm;
        if (TripReading.validOdo(startReading.odoKm) && TripReading.validOdo(endOdo)) {
            return Math.max(0, endOdo - startReading.odoKm);
        }
        return 0;
    }

    private int fuelUsedMl() {
        if (fuelBaseline != TripReading.INVALID && fuelMax != TripReading.INVALID) {
            return Math.max(0, fuelMax - fuelBaseline);
        }
        return 0;
    }

    /**
     * Yolculuğun o ana kadarki kaydı; durum değiştirmez. Yol almadan, hareket etmeden açılıp kapanan kontak
     * yolculuk sayılmaz (null).
     */
    private TripRecord build(TripReading end, long endedAt) {
        int distance = distanceTo(end);
        if (distance == 0 && movingMs == 0) {
            return null;
        }
        int fuelMl = 0;
        if (fuelBaseline != TripReading.INVALID) {
            int max = fuelMax;
            if (TripReading.validCounter(end.sumFuelMl)) {
                max = Math.max(max, end.sumFuelMl);
            }
            fuelMl = Math.max(0, max - fuelBaseline);
        }
        int endOdo = TripReading.validOdo(end.odoKm) ? end.odoKm : lastReading.odoKm;
        int avg = movingMs > 0 ? (int) Math.round(distance / (movingMs / 3_600_000.0)) : 0;

        return new TripRecord(
                "t" + startedAt,
                startedAt,
                endedAt,
                startReading.odoKm,
                endOdo,
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
                route);
    }
}
