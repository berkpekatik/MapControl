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
