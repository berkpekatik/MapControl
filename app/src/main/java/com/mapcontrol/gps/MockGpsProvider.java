package com.mapcontrol.gps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;

/**
 * Alınan telefon konumlarını sahte konum mekanizmasıyla Android'in {@code gps} sağlayıcısına yazar,
 * sinyal kesilince sağlayıcıyı geri verir.
 */
final class MockGpsProvider {

    private static final String PROVIDER = LocationManager.GPS_PROVIDER;
    private static final String PREFS = "MapControlPrefs";
    /** Çökmeden sonra bir sonraki başlangıç test sağlayıcısını temizlesin diye hemen yazılır. */
    private static final String KEY_PROVIDER_OWNED = "gpsProviderOwned";

    private final LocationManager manager;
    private final SharedPreferences prefs;
    private boolean added;

    MockGpsProvider(Context context) {
        this.manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Önceki bir çökmeden kalan sağlayıcıyı kaldırır. Android reddederse false. */
    static boolean restoreIfOwned(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_PROVIDER_OWNED, false)) {
            return true;
        }
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return removeAndDisown(manager, prefs);
    }

    synchronized void publish(Wire.Frame fix) {
        if (!added) {
            install();
        }
        Location location = new Location(PROVIDER);
        location.setLatitude(fix.lat);
        location.setLongitude(fix.lon);
        location.setAccuracy(fix.accuracy);
        location.setTime(System.currentTimeMillis() - fix.ageMs);
        location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos() - fix.ageMs * 1_000_000L);
        if (!Double.isNaN(fix.altitude)) {
            location.setAltitude(fix.altitude);
        }
        if (!Float.isNaN(fix.speed)) {
            location.setSpeed(fix.speed);
        }
        if (!Float.isNaN(fix.bearing)) {
            location.setBearing(fix.bearing);
        }
        manager.setTestProviderLocation(PROVIDER, location);
    }

    synchronized void release() {
        if (!added) {
            return;
        }
        if (removeAndDisown(manager, prefs)) {
            added = false;
        }
    }

    @SuppressLint("ApplySharedPref")
    private void install() {
        prefs.edit().putBoolean(KEY_PROVIDER_OWNED, true).commit();
        try {
            addProvider();
        } catch (IllegalArgumentException alreadyExists) {
            manager.removeTestProvider(PROVIDER);
            addProvider();
        }
        manager.setTestProviderEnabled(PROVIDER, true);
        added = true;
    }

    /** Criteria sabitleri API 31 altında doğrudur; lint yalnızca API 31 karşılıklarını bilir. */
    @SuppressLint("WrongConstant")
    private void addProvider() {
        manager.addTestProvider(PROVIDER, false, true, false, false, true, true, true,
                Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
    }

    @SuppressLint("ApplySharedPref")
    private static boolean removeAndDisown(LocationManager manager, SharedPreferences prefs) {
        try {
            manager.removeTestProvider(PROVIDER);
        } catch (IllegalArgumentException alreadyGone) {
            // kaldırılacak bir şey yok
        } catch (SecurityException notAllowed) {
            return false;
        }
        prefs.edit().putBoolean(KEY_PROVIDER_OWNED, false).commit();
        return true;
    }
}
