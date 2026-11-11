package com.mapcontrol.vehicle;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Menzil için tek kural. Kontak kapalıyken / araç dururken araç menzil yerine 0xF000 (61440) gibi geçersiz bir değer
 * yollar; bu değer gösterilmez. 0..{@link #MAX_PLAUSIBLE_KM} dışı geçersizdir. Geçerli değer yoksa son geçerli menzil
 * (kontak açıkken, powerMode 2'de okunmuş) döner; yolculuk kaydıyla ({@code TripRecorder}) aynı kayıt kullanılır.
 */
public final class VehicleRange {

    public static final int MAX_PLAUSIBLE_KM = 2000;
    /** Kontak açık: menzil yalnızca bu durumda güvenilir sayılıp hatırlanır. */
    public static final int POWER_ON = 2;
    static final String PREFS = "trip_recorder";
    static final String KEY = "lastRangeKm";
    /** Hiç geçerli değer yok. */
    public static final int NONE = -1;

    private static final Object LOCK = new Object();
    private static boolean loaded;
    private static int lastGood = NONE;

    private VehicleRange() {
    }

    /** Ham değer gerçek bir menzil mi (61440 gibi işaret değerleri değil). */
    public static boolean isPlausible(int km) {
        return VehicleMetricsSnapshot.isValid(km) && km >= 0 && km <= MAX_PLAUSIBLE_KM;
    }

    /** Saf kural (birim testlenir): geçerli ham değer önce, yoksa son geçerli, o da yoksa {@link #NONE}. */
    static int pick(int raw, int lastGoodKm) {
        if (isPlausible(raw)) {
            return raw;
        }
        return isPlausible(lastGoodKm) ? lastGoodKm : NONE;
    }

    /** Saf kural: hangi okumalar hatırlanır (kontak açık ve geçerli). */
    static boolean shouldRemember(int raw, int powerMode) {
        return powerMode == POWER_ON && isPlausible(raw);
    }

    /** Gösterilecek menzil (km) ya da {@link #NONE}; kontak açıkken geçerli değeri hatırlar. */
    public static int stable(Context context, VehicleMetricsSnapshot s) {
        synchronized (LOCK) {
            SharedPreferences sp = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (!loaded) {
                lastGood = sp.getInt(KEY, NONE);
                loaded = true;
            }
            if (shouldRemember(s.rangeKm, s.powerMode) && s.rangeKm != lastGood) {
                lastGood = s.rangeKm;
                sp.edit().putInt(KEY, lastGood).apply();
            }
            return pick(s.rangeKm, lastGood);
        }
    }

    /** Kotlin/arayüz için: gösterilecek menzil, yoksa null. */
    public static Integer stableOrNull(Context context, VehicleMetricsSnapshot s) {
        int km = stable(context, s);
        return km == NONE ? null : km;
    }
}
