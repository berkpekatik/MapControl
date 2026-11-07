package com.mapcontrol.vehicle;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.HvacID;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Konfor otomasyonunun araca yazdığı (modül, kimlik, değer) tabloları. SAF kod: VDBus'a, Android'e dokunmaz,
 * JVM'de sınanır; ClimateAutomationEngine yalnızca buradaki listeleri sırayla gönderir.
 */
final class ClimateAutomationWrites {

    static final int MODULE_HVAC = VDEventCarInfo.MODULE_HVAC;
    static final int MODULE_SETTING = VDEventCarInfo.MODULE_CAR_SETTING;

    /** Direksiyon ısıtma anahtar değeri: OEM açarken de kapatırken de CS 160 ← 1 yazar, 0 hiç yazmaz. */
    static final int STEER_WHEEL_TOGGLE = 1;

    private ClimateAutomationWrites() {
    }

    static final class Write {
        final int module;
        final int id;
        final int value;

        Write(int module, int id, int value) {
            this.module = module;
            this.id = id;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Write)) {
                return false;
            }
            Write w = (Write) o;
            return module == w.module && id == w.id && value == w.value;
        }

        @Override
        public int hashCode() {
            return (module * 31 + id) * 31 + value;
        }

        @Override
        public String toString() {
            return "module=" + module + " id=" + id + " value=" + value;
        }
    }

    private static Write hvac(int id, int value) {
        return new Write(MODULE_HVAC, id, value);
    }

    private static Write setting(int id, int value) {
        return new Write(MODULE_SETTING, id, value);
    }

    /**
     * Açma yazmaları (hemen gönderilir). STEER_HEAT için boş: anahtar, durum okunarak ayrıca gönderilir.
     * CLIMATE için yalnızca güç; A/C ve otomatik [climateFollowUpWrites] ile ~150 ms sonra. Bilinmeyen kural: null.
     */
    static List<Write> onWrites(String ruleId, int wireSeatLevel) {
        switch (ruleId) {
            case ClimateAutomationStore.DRIVER_HEAT:
                return Collections.singletonList(setting(CarSettingID.ID_CAR_DRIVER_SEAT_HEAT, wireSeatLevel));
            case ClimateAutomationStore.PASSENGER_HEAT:
                return Collections.singletonList(setting(CarSettingID.ID_CAR_COPILOT_SEAT_HEAT, wireSeatLevel));
            case ClimateAutomationStore.DRIVER_VENT:
                return Collections.singletonList(setting(CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION, wireSeatLevel));
            case ClimateAutomationStore.PASSENGER_VENT:
                return Collections.singletonList(setting(CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION, wireSeatLevel));
            case ClimateAutomationStore.STEER_HEAT:
                return Collections.emptyList();
            case ClimateAutomationStore.WINDSHIELD:
                return Arrays.asList(hvac(HvacID.ID_AC_FRONT_GLASS_HEAT, 1), hvac(HvacID.ID_AC_MAX_FRONT_DEFOREST, 1));
            case ClimateAutomationStore.REAR_DEFROST:
                // OEM arka buz çözmeyi yalnızca HVAC 80 ile yazar; CS 159'u hiçbir OEM uygulaması yazmıyor
                return Collections.singletonList(hvac(HvacID.ID_AC_REAR_DEFROST, 1));
            case ClimateAutomationStore.CLIMATE:
                // Önce güç 18 ← 1; A/C ve otomatik ~150 ms sonra (OEM sırası)
                return Collections.singletonList(hvac(HvacID.ID_AC_WORKING, 1));
            default:
                return null;
        }
    }

    /** Klima açma: güç yazmasından sonra gönderilen A/C 1 ← 1 ve otomatik 4 ← 1. */
    static List<Write> climateFollowUpWrites() {
        return Arrays.asList(hvac(HvacID.ID_AC_STATE, 1), hvac(HvacID.ID_AC_AUTO_STATE, 1));
    }

    /** Kapatma yazmaları. STEER_HEAT için boş (anahtar, ayrıca gönderilir). Bilinmeyen kural: null. */
    static List<Write> offWrites(String ruleId) {
        switch (ruleId) {
            case ClimateAutomationStore.DRIVER_HEAT:
                return Collections.singletonList(setting(CarSettingID.ID_CAR_DRIVER_SEAT_HEAT, 1));
            case ClimateAutomationStore.PASSENGER_HEAT:
                return Collections.singletonList(setting(CarSettingID.ID_CAR_COPILOT_SEAT_HEAT, 1));
            case ClimateAutomationStore.DRIVER_VENT:
                return Collections.singletonList(setting(CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION, 1));
            case ClimateAutomationStore.PASSENGER_VENT:
                return Collections.singletonList(setting(CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION, 1));
            case ClimateAutomationStore.STEER_HEAT:
                return Collections.emptyList();
            case ClimateAutomationStore.WINDSHIELD:
                // OEM: 65 ve 24 için 1 aç, 2 kapat (HvacModel.onFrontHeatingClick / onMaxDefrostingClick)
                return Arrays.asList(hvac(HvacID.ID_AC_FRONT_GLASS_HEAT, 2), hvac(HvacID.ID_AC_MAX_FRONT_DEFOREST, 2));
            case ClimateAutomationStore.REAR_DEFROST:
                // OEM her tıkta 80 ← 1 yazar (anahtar); araçta 0 ve tekrar 1 kapatmadı. Kapatma değeri
                // belirsiz olduğu için mevcut 0 korunuyor (kodlama doğrulanmadı)
                return Collections.singletonList(hvac(HvacID.ID_AC_REAR_DEFROST, 0));
            case ClimateAutomationStore.CLIMATE:
                // OEM: A/C 1 ve güç 18 için 1 aç, 2 kapat; 0 hiç yazılmaz. Otomatik 4: T1E24_INT dalında 2 ile
                // kapatılır; sesli komut da her modelde 4 ← 2
                return Arrays.asList(
                        hvac(HvacID.ID_AC_STATE, 2), hvac(HvacID.ID_AC_WORKING, 2), hvac(HvacID.ID_AC_AUTO_STATE, 2));
            default:
                return null;
        }
    }
}
