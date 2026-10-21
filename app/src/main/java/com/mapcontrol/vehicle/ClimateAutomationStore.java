package com.mapcontrol.vehicle;

import android.content.SharedPreferences;

/**
 * Dış ortam sıcaklığına göre koltuk / direksiyon / klima otomasyonu tercihleri.
 */
public final class ClimateAutomationStore {

    public static final String DRIVER_HEAT = "driver_heat";
    public static final String PASSENGER_HEAT = "passenger_heat";
    public static final String DRIVER_VENT = "driver_vent";
    public static final String PASSENGER_VENT = "passenger_vent";
    public static final String STEER_HEAT = "steer_heat";
    public static final String WINDSHIELD = "windshield";
    public static final String REAR_DEFROST = "rear_defrost";
    public static final String CLIMATE = "climate";

    public static final String[] ALL_IDS = {
            DRIVER_HEAT, PASSENGER_HEAT, DRIVER_VENT, PASSENGER_VENT,
            STEER_HEAT, WINDSHIELD, REAR_DEFROST, CLIMATE
    };

    public static final int CMP_LT = 0;
    public static final int CMP_LTE = 1;
    public static final int CMP_GT = 2;
    public static final int CMP_GTE = 3;

    public static final int TEMP_MIN = -20;
    public static final int TEMP_MAX = 40;
    public static final int DELAY_MAX_MINUTES = 30;
    public static final int LEVEL_MIN = 1;
    public static final int LEVEL_MAX = 3;

    private static final String MASTER_KEY = "climateAutoEnabled";

    public static final class Rule {
        public boolean enabled;
        public int delayMinutes;
        public int compare;
        public int tempC;
        public int level;
    }

    private ClimateAutomationStore() {
    }

    public static boolean isMasterEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(MASTER_KEY, false);
    }

    public static void setMasterEnabled(SharedPreferences prefs, boolean enabled) {
        prefs.edit().putBoolean(MASTER_KEY, enabled).apply();
    }

    public static boolean usesHeatLevel(String id) {
        return DRIVER_HEAT.equals(id)
                || PASSENGER_HEAT.equals(id)
                || DRIVER_VENT.equals(id)
                || PASSENGER_VENT.equals(id);
    }

    /**
     * Arayüzde gizlenen kurallar. Kod ve kayıtlı tercihler durur, ancak gizli kural
     * görünmediği için çalıştırılmaz ({@link #getRule} gizli kuralı kapalı döndürür).
     */
    public static boolean isHidden(String id) {
        return CLIMATE.equals(id);
    }

    /** Aynı koltuğun ısıtma/havalandırma eşi; yoksa null. */
    public static String conflictPartnerOf(String id) {
        if (DRIVER_HEAT.equals(id)) {
            return DRIVER_VENT;
        }
        if (DRIVER_VENT.equals(id)) {
            return DRIVER_HEAT;
        }
        if (PASSENGER_HEAT.equals(id)) {
            return PASSENGER_VENT;
        }
        if (PASSENGER_VENT.equals(id)) {
            return PASSENGER_HEAT;
        }
        return null;
    }

    private static boolean isUpperBound(int compare) {
        return compare == CMP_LT || compare == CMP_LTE;
    }

    private static boolean isInclusive(int compare) {
        return compare == CMP_LTE || compare == CMP_GTE;
    }

    /** İki kuralın sıcaklık aralıkları kesişiyor mu (aynı anda ikisi de tetiklenebilir mi)? */
    public static boolean overlaps(Rule a, Rule b) {
        boolean aUpper = isUpperBound(a.compare);
        boolean bUpper = isUpperBound(b.compare);
        if (aUpper == bUpper) {
            return true; // ikisi de sonsuza uzanıyor
        }
        Rule upper = aUpper ? a : b;
        Rule lower = aUpper ? b : a;
        if (upper.tempC > lower.tempC) {
            return true;
        }
        return upper.tempC == lower.tempC
                && isInclusive(upper.compare) && isInclusive(lower.compare);
    }

    private static int oppositeCompare(int compare) {
        switch (compare) {
            case CMP_LT:
                return CMP_GT;
            case CMP_LTE:
                return CMP_GTE;
            case CMP_GT:
                return CMP_LT;
            case CMP_GTE:
            default:
                return CMP_LTE;
        }
    }

    /**
     * Kontrol: İki kural çakışıyor mu?
     * Hem bu kural hem de eş kural açık (enabled) ise ve sıcaklık aralıkları çakışıyorsa true döner.
     * Kullanıcı ikisini de aktif edebilir (örn. ısıtma < 16, soğutma > 17);
     * yalnızca dereceleri aynı anda tetiklenecek şekilde kesişiyorsa çakışma sayılır.
     */
    public static boolean willConflict(String id, Rule testRule, SharedPreferences prefs) {
        String partnerId = conflictPartnerOf(id);
        if (partnerId == null) {
            return false;
        }
        Rule partner = getRule(prefs, partnerId);
        // İkisi de açıkken (veya testRule açılmak isteniyorken ve partner açıkken) aralıkları kesişiyor mu?
        if (!testRule.enabled || !partner.enabled) {
            return false;
        }
        return overlaps(testRule, partner);
    }

    public static Rule getRule(SharedPreferences prefs, String id) {
        Rule defaults = defaultsFor(id);
        Rule rule = new Rule();
        rule.enabled = !isHidden(id) && prefs.getBoolean(key(id, "on"), defaults.enabled);
        rule.delayMinutes = clamp(prefs.getInt(key(id, "delay"), defaults.delayMinutes),
                0, DELAY_MAX_MINUTES);
        rule.compare = clamp(prefs.getInt(key(id, "cmp"), defaults.compare), CMP_LT, CMP_GTE);
        rule.tempC = clamp(prefs.getInt(key(id, "temp"), defaults.tempC), TEMP_MIN, TEMP_MAX);
        rule.level = clamp(prefs.getInt(key(id, "level"), defaults.level), LEVEL_MIN, LEVEL_MAX);
        return rule;
    }

    public static void putRule(SharedPreferences prefs, String id, Rule rule) {
        prefs.edit()
                .putBoolean(key(id, "on"), rule.enabled)
                .putInt(key(id, "delay"), clamp(rule.delayMinutes, 0, DELAY_MAX_MINUTES))
                .putInt(key(id, "cmp"), clamp(rule.compare, CMP_LT, CMP_GTE))
                .putInt(key(id, "temp"), clamp(rule.tempC, TEMP_MIN, TEMP_MAX))
                .putInt(key(id, "level"), clamp(rule.level, LEVEL_MIN, LEVEL_MAX))
                .apply();
    }

    public static boolean matchesTemperature(double outsideC, int compare, int thresholdC) {
        switch (compare) {
            case CMP_LT:
                return outsideC < thresholdC;
            case CMP_LTE:
                return outsideC <= thresholdC;
            case CMP_GT:
                return outsideC > thresholdC;
            case CMP_GTE:
                return outsideC >= thresholdC;
            default:
                return false;
        }
    }

    public static Rule defaultsFor(String id) {
        Rule rule = new Rule();
        rule.enabled = false;
        rule.level = 2;
        rule.delayMinutes = 0;
        rule.compare = CMP_LT;
        rule.tempC = 0;
        if (DRIVER_HEAT.equals(id)) {
            rule.delayMinutes = 0;
            rule.tempC = 6;
        } else if (PASSENGER_HEAT.equals(id)) {
            rule.tempC = 0;
        } else if (DRIVER_VENT.equals(id)) {
            rule.delayMinutes = 0;
            rule.compare = CMP_GT;
            rule.tempC = 15;
        } else if (PASSENGER_VENT.equals(id)) {
            rule.compare = CMP_GT;
            rule.tempC = 5;
        } else if (STEER_HEAT.equals(id) || WINDSHIELD.equals(id) || REAR_DEFROST.equals(id)) {
            rule.tempC = 0;
        } else if (CLIMATE.equals(id)) {
            rule.delayMinutes = 10;
            rule.tempC = 0;
        }
        return rule;
    }

    private static String key(String id, String field) {
        return "climateAuto_" + id + "_" + field;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
