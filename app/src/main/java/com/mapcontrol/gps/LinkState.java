package com.mapcontrol.gps;

import android.os.SystemClock;

/** Alıcı (yazan) ile ekran ve telefon uçları (okuyan) arasında, aynı süreçte paylaşılan canlı durum. */
public final class LinkState {

    private static volatile boolean running;
    private static volatile LinkStatus status = LinkStatus.READY;
    private static volatile double lat = Double.NaN;
    private static volatile double lon = Double.NaN;
    private static volatile float accuracy = Float.NaN;
    private static volatile long fixAt;

    private LinkState() {
    }

    public static boolean isRunning() {
        return running;
    }

    public static LinkStatus status() {
        return status;
    }

    public static float accuracy() {
        return accuracy;
    }

    /** Son konumun yaşı (ms); konum yoksa -1. */
    public static long fixAgeMs() {
        long at = fixAt;
        return at == 0 ? -1 : Math.max(0, SystemClock.elapsedRealtime() - at);
    }

    static void begin() {
        com.mapcontrol.util.DiagJournal.event("gps", "link basladi");
        status = LinkStatus.WAITING_PHONE;
        clearFix();
        running = true;
    }

    /** Bağlantı durduktan sonra yok sayılır; geç kalan iş parçacıkları son durumu ezmesin. */
    static void update(LinkStatus newStatus) {
        if (running) {
            if (status != newStatus) {
                com.mapcontrol.util.DiagJournal.event("gps", "durum " + status + " -> " + newStatus);
            }
            status = newStatus;
        }
    }

    static void setFix(double newLat, double newLon, float newAccuracy) {
        lat = newLat;
        lon = newLon;
        accuracy = newAccuracy;
        fixAt = SystemClock.elapsedRealtime();
    }

    static void fail(LinkStatus reason) {
        com.mapcontrol.util.DiagJournal.event("gps", "basarisiz: " + reason);
        status = reason;
    }

    static void end() {
        com.mapcontrol.util.DiagJournal.event("gps", "link durdu (son durum " + status + ")");
        running = false;
        if (!status.sticky) {
            status = LinkStatus.READY;
        }
        clearFix();
    }

    private static void clearFix() {
        lat = Double.NaN;
        lon = Double.NaN;
        accuracy = Float.NaN;
        fixAt = 0;
    }
}
