package com.mapcontrol.vehicle;

import android.os.Bundle;

import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

/**
 * Ham VDBus / CarInfoProxy değerlerini birleştirip saklar.
 * Anahtar: {@code module + itemId} — ReadOnly ve CarSetting ID çakışmalarını önler.
 */
public final class VehicleMetricsCache {

    static final int INVALID = Integer.MIN_VALUE;
    static final int MODULE_READONLY = VDEventCarInfo.MODULE_READONLY_INFO;
    static final int MODULE_SETTING = VDEventCarInfo.MODULE_CAR_SETTING;
    static final int MODULE_NEW_ENERGY = VDEventCarInfo.MODULE_NEW_ENERGY;

    private final Bundle data = new Bundle();

    boolean update(int module, int itemId, int[] rawValues) {
        int combined = combineRaw(rawValues);
        int previous = data.getInt(key(module, itemId), INVALID);
        if (previous == combined) {
            return false;
        }
        data.putInt(key(module, itemId), combined);
        return true;
    }

    void put(int module, int itemId, int[] rawValues) {
        data.putInt(key(module, itemId), combineRaw(rawValues));
    }

    /** ReadOnly modülü için kısayol. */
    int getCombined(int itemId) {
        return getCombined(MODULE_READONLY, itemId);
    }

    int getCombined(int module, int itemId) {
        return data.getInt(key(module, itemId), INVALID);
    }

    void clear() {
        data.clear();
    }

    /** CarAyar ile aynı: çok baytlı ham diziyi tek sayıya birleştir. */
    static int combineRaw(int[] rawValues) {
        if (rawValues == null || rawValues.length == 0) {
            return INVALID;
        }
        if (rawValues.length == 1) {
            return rawValues[0];
        }
        int combined = 0;
        for (int value : rawValues) {
            combined = (combined << 8) | (value & 0xFF);
        }
        return combined;
    }

    private static String key(int module, int itemId) {
        return "m" + module + "_id_" + itemId;
    }
}
