package com.mapcontrol.vehicle.material;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.client.bind.VDServiceDef;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;
import com.desaysv.ivi.vdb.event.id.device.VDEventVehicleDevice;

import java.lang.reflect.Method;

/**
 * OEM {@code EolConfig} / {@code CarConfigUtil} verisinin MapControl için sadeleştirilmiş okuyucusu.
 */
final class OemEolConfigReader {

    private static final String TAG = "OemEolConfig";
    private static final int VDB_BIND_WAIT_MS = 2500;
    private static final int VDB_BIND_STEP_MS = 100;

    private int[] carConfig3;
    private int[] carConfig5;
    private int[] comboConfig;

    void load(Context context) {
        Context app = context.getApplicationContext();
        VDBus vdbus = VDBus.getDefault();
        vdbus.init(app);
        ensureVehicleDeviceConnected(vdbus);

        carConfig3 = readHexConfig(vdbus, ReserveKeys.CAR_CONFIG_3);
        carConfig5 = readHexConfig(vdbus, ReserveKeys.CAR_CONFIG_5);
        comboConfig = readHexConfig(vdbus, ReserveKeys.COMBO_CONFIG);

        if (comboConfig == null) {
            comboConfig = hexStringToBytes(readSystemProperty(ReserveKeys.COMBO_CONFIG));
        }
        if (carConfig5 == null) {
            carConfig5 = hexStringToBytes(readSystemProperty(ReserveKeys.CAR_CONFIG_5));
        }
        if (carConfig3 == null) {
            carConfig3 = hexStringToBytes(readSystemProperty(ReserveKeys.CAR_CONFIG_3));
        }

        Log.i(TAG, "EOL yüklendi — modelCode=" + getModelCode()
                + " power=" + getPowerType()
                + " region=" + getCountryOrRegion()
                + " net=" + getTboxNetworkType()
                + " combo=" + (comboConfig != null ? comboConfig.length : 0)
                + " cfg5=" + (carConfig5 != null ? carConfig5.length : 0));
    }

    private static void ensureVehicleDeviceConnected(VDBus vdbus) {
        VDServiceDef.ServiceType type = VDServiceDef.ServiceType.VEHICLE_DEVICE;
        if (vdbus.isServiceConnected(type)) {
            return;
        }
        vdbus.bindService(type);
        long deadline = System.currentTimeMillis() + VDB_BIND_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (vdbus.isServiceConnected(type)) {
                Log.i(TAG, "VDBus VEHICLE_DEVICE bağlandı");
                return;
            }
            try {
                Thread.sleep(VDB_BIND_STEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        Log.w(TAG, "VDBus VEHICLE_DEVICE bağlantısı zaman aşımı");
    }

    int getModelCode() {
        return comboConfig != null && comboConfig.length >= 2 ? comboConfig[1] : 0;
    }

    int getPowerType() {
        if (carConfig5 == null || carConfig5.length < 8) {
            return 0;
        }
        return (carConfig5[4] >> 4) & 3;
    }

    int getCountryOrRegion() {
        if (carConfig5 == null || carConfig5.length < 8) {
            return 0;
        }
        return carConfig5[6] & 255;
    }

    int getTboxNetworkType() {
        if (carConfig5 == null || carConfig5.length < 8) {
            return 0;
        }
        return (carConfig5[3] >> 2) & 3;
    }

    int getFaceStyleConfig() {
        if (carConfig3 == null || carConfig3.length < 8) {
            return 0;
        }
        return (carConfig3[4] >> 5) & 1;
    }

    int getBrandConfig() {
        if (comboConfig == null || comboConfig.length < 14) {
            return 0;
        }
        return comboConfig[13];
    }

    boolean isInternational() {
        return getCountryOrRegion() != 0;
    }

    boolean isNet() {
        return getTboxNetworkType() != 0;
    }

    boolean isEv() {
        return getPowerType() == 2;
    }

    boolean isPhev() {
        return getPowerType() == 1;
    }

    boolean isT19CevInt() {
        return getModelCode() == 7 && isInternational() && isEv();
    }

    boolean isT19cInt() {
        return getModelCode() == 7 && isInternational() && !isEv();
    }

    boolean isT18fl3Int() {
        return getModelCode() == 10 && isInternational();
    }

    boolean isT26Int() {
        return getModelCode() == 1 && isInternational();
    }

    boolean isM1ePhevInt() {
        return getModelCode() == 5 && isInternational() && isPhev();
    }

    boolean isT1eflInt() {
        return getModelCode() == 3 && isInternational();
    }

    boolean isT19flInt() {
        return getModelCode() == 12 && isInternational();
    }

    boolean isM1eInt() {
        return getModelCode() == 5 && isInternational() && !isPhev();
    }

    boolean hasSignal() {
        return getModelCode() != 0 || getCountryOrRegion() != 0;
    }

    String resolveCarDrawableBaseName() {
        final String base = "img_car_energy_conservation";
        if (isT19flInt()) {
            return base + "_face" + ((getBrandConfig() == 3 ? 1 : 0) + 1);
        }
        if (isT1eflInt()) {
            return base + "_face" + ((getFaceStyleConfig() == 1 ? 1 : 0) + 1);
        }
        return base;
    }

    private static int[] readHexConfig(VDBus vdbus, String key) {
        try {
            Bundle bundle = new Bundle();
            bundle.putString(VDKey.TYPE, key);
            VDEvent event = vdbus.getOnce(new VDEvent(VDEventVehicleDevice.PROJECT_RESERVE_CONFIGS, bundle));
            if (event == null || event.getPayload() == null) {
                return null;
            }
            String value = event.getPayload().getString("value");
            if (value == null || value.isEmpty()) {
                return null;
            }
            return hexStringToBytes(value);
        } catch (RuntimeException e) {
            Log.w(TAG, "EOL VDBus okunamadı: " + key, e);
            return null;
        }
    }

    private static String readSystemProperty(String key) {
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            Method get = cls.getMethod("get", String.class, String.class);
            Object value = get.invoke(null, key, "");
            return value != null ? value.toString() : "";
        } catch (ReflectiveOperationException e) {
            Log.w(TAG, "SystemProperties okunamadı: " + key, e);
            return "";
        }
    }

    private static int[] hexStringToBytes(String hex) {
        if (hex == null || hex.length() < 2) {
            return null;
        }
        int length = hex.length() / 2;
        int[] out = new int[length];
        for (int i = 0; i < hex.length() - 1; i += 2) {
            try {
                out[i / 2] = Integer.parseInt(hex.substring(i, i + 2), 16) & 255;
            } catch (NumberFormatException ignored) {
                out[i / 2] = 0;
            }
        }
        return out;
    }

    private static final class ReserveKeys {
        static final String CAR_CONFIG_3 = "vehicle.persist.project.ext.configs3";
        static final String CAR_CONFIG_5 = "vehicle.persist.project.ext.configs5";
        static final String COMBO_CONFIG = "vehicle.persist.combo.config";

        private ReserveKeys() {
        }
    }
}
