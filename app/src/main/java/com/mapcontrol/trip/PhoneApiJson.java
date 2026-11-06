package com.mapcontrol.trip;

import java.util.List;
import java.util.Locale;

/**
 * Telefon uygulamasına giden {@code /trips} ve {@code /vehicle/now} gövdeleri.
 * Sözleşme {@code mapcontrol.mobil/MOBIL_UYGULAMA_OZELLIKLERI.md} içinde; zamanlar epoch milisaniye.
 * Android'e bağlı değildir (org.json yerine elle yazılır) ki JVM'de test edilebilsin.
 */
public final class PhoneApiJson {

    private PhoneApiJson() {
    }

    public static String trips(List<TripRecord> trips) {
        StringBuilder sb = new StringBuilder("{\"trips\":[");
        for (int i = 0; i < trips.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            trip(sb, trips.get(i));
        }
        return sb.append("]}").toString();
    }

    /**
     * Sunucu posta kutusuna giden paket: {@link #trips(List)} gövdesi ve kontak kapanırkenki sayaçlar
     * ({@code "snapshot"}, {@code /vehicle/now}'un ilk beş alanı). [snapshotJson] null ise alan yazılmaz.
     */
    public static String tripBox(List<TripRecord> trips, String snapshotJson) {
        String base = trips(trips);
        if (snapshotJson == null || snapshotJson.isEmpty()) {
            return base;
        }
        return base.substring(0, base.length() - 1) + ",\"snapshot\":" + snapshotJson + "}";
    }

    private static void trip(StringBuilder sb, TripRecord t) {
        sb.append("{\"id\":").append(quote(t.id))
                .append(",\"startedAt\":").append(t.startedAt)
                .append(",\"endedAt\":").append(t.endedAt)
                .append(",\"distanceKm\":").append(t.distanceKm)
                .append(",\"fuelUsedMl\":").append(t.fuelUsedMl)
                .append(",\"avgSpeedKmh\":").append(t.avgSpeedKmh)
                .append(",\"maxSpeedKmh\":").append(t.maxSpeedKmh)
                .append(",\"movingMinutes\":").append(t.movingMinutes())
                .append(",\"route\":[");
        for (int i = 0; i < t.route.size(); i++) {
            TripRecord.Point p = t.route.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append('[').append(number(p.lat)).append(',').append(number(p.lon))
                    .append(',').append(p.speedKmh).append(']');
        }
        sb.append("]}");
    }

    /**
     * @param vin şase numarası; sistem özelliğinden okunamadıysa boş
     */
    public static String vehicleNow(String vin, long updatedAt, int odometerKm, int fuelPercent,
                                    int rangeKm) {
        StringBuilder sb = new StringBuilder("{\"vin\":").append(quote(vin))
                .append(",\"updatedAt\":").append(updatedAt)
                .append(",\"odometerKm\":").append(odometerKm)
                .append(",\"fuelPercent\":").append(fuelPercent)
                .append(",\"rangeKm\":").append(rangeKm);
        return sb.append('}').toString();
    }

    /** Protokol sürümü; telefon uyumsuz araçları buradan anlar. */
    public static final int PROTOCOL_VERSION = 1;

    /**
     * {@code GET /ping} gövdesi (eşleşme anahtarı istemez, kişisel veri taşımaz):
     * {@code {"id":"<araç kimliği>","v":1,"gps":45921}}. {@code gps} GPS Link alıcısının UDP portu; alıcı yoksa 0.
     */
    public static String ping(String carId, int gpsPort) {
        return "{\"id\":" + quote(carId) + ",\"v\":" + PROTOCOL_VERSION + ",\"gps\":" + gpsPort + "}";
    }

    /**
     * {@code vehicleNow} gövdesine GPS Link alıcısının anlık durumunu ekler:
     * {@code "gps":{"state":"live","ageMs":900}}. {@code state}: off, waiting, live, lost, blocked.
     * [fixAgeMs] son konumun yaşı, konum yoksa negatif (alan yazılmaz).
     */
    public static String withGps(String json, String state, long fixAgeMs) {
        StringBuilder sb = new StringBuilder(json.substring(0, json.length() - 1))
                .append(",\"gps\":{\"state\":").append(quote(state));
        if (fixAgeMs >= 0) {
            sb.append(",\"ageMs\":").append(fixAgeMs);
        }
        return sb.append("}}").toString();
    }

    public static final String LOCATION_PHONE = "phone";
    public static final String LOCATION_CAR = "car";
    public static final String LOCATION_NONE = "none";

    /**
     * Sayaçlara ek olarak yolculuk kaydının anlık durumu:
     * {@code "trip":{"active":true,"startedAt":…,"points":14,"location":"phone"|"car"|"none"}}, kayıt yokken
     * {@code "trip":{"active":false}}. Eski telefonlar bilinmeyen alanı yok sayar.
     *
     * @param location yalnızca [tripActive] iken; {@link #LOCATION_PHONE}, {@link #LOCATION_CAR} veya {@link #LOCATION_NONE}
     */
    public static String vehicleNow(String vin, long updatedAt, int odometerKm, int fuelPercent, int rangeKm,
                                    boolean tripActive, long tripStartedAt, int tripPoints, String location) {
        String base = vehicleNow(vin, updatedAt, odometerKm, fuelPercent, rangeKm);
        StringBuilder sb = new StringBuilder(base.substring(0, base.length() - 1))
                .append(",\"trip\":{\"active\":").append(tripActive);
        if (tripActive) {
            sb.append(",\"startedAt\":").append(tripStartedAt)
                    .append(",\"points\":").append(tripPoints)
                    .append(",\"location\":").append(quote(location == null ? LOCATION_NONE : location));
        }
        return sb.append("}}").toString();
    }

    /**
     * {@code GET /parking}: son park fotoğrafı. Yoksa {@code {"photo":null}}; varsa
     * {@code {"photo":{"capturedAt":…,"tripId":"t…"}}}. Görüntü {@code GET /parking/photo} ile (JPEG) alınır.
     */
    public static String parking(long capturedAt, String tripId) {
        if (capturedAt <= 0) {
            return "{\"photo\":null}";
        }
        return "{\"photo\":{\"capturedAt\":" + capturedAt + ",\"tripId\":" + quote(tripId == null ? "" : tripId) + "}}";
    }

    /** Ondalık nokta her yerel ayarda nokta olsun, üstel gösterim çıkmasın. */
    static String number(double v) {
        return String.format(Locale.US, "%.6f", v);
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }
}
