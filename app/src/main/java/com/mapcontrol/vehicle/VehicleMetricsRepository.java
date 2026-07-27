package com.mapcontrol.vehicle;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.extra.project.carinfo.proxy.Constants;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CarInfoProxy + VDBus üzerinden araç metriklerini paylaşımlı olarak sunar.
 * {@link com.mapcontrol.ui.builder.VehicleInfoTabBuilder} ve launcher dashboard aynı kaynağı kullanır.
 */
public final class VehicleMetricsRepository {

    public interface Listener {
        void onMetricsUpdated(VehicleMetricsSnapshot snapshot);
    }

    private static final int MODULE_READONLY = VDEventCarInfo.MODULE_READONLY_INFO;
    private static final int MODULE_SETTING = VDEventCarInfo.MODULE_CAR_SETTING;
    private static final int MODULE_NEW_ENERGY = VDEventCarInfo.MODULE_NEW_ENERGY;

    static final int[] SUBSCRIBED_READONLY_IDS = {
            ReadOnlyID.ID_GRAND_TOTAL_KM,
            ReadOnlyID.ID_TRIP,
            ReadOnlyID.ID_GRAND_TOTAL_KM_AFTER_CLEAR,
            ReadOnlyID.ID_GRAND_TOTAL_KM_AFTER_RUNNING,
            ReadOnlyID.ID_FUEL_PERCENT,
            ReadOnlyID.ID_ENDURANCE_KM,
            ReadOnlyID.ID_TOTAL_RANGE,
            ReadOnlyID.ID_SUM_FUEL,
            ReadOnlyID.ID_LOW_FUEL_WARNING,
            ReadOnlyID.ID_AVG_FUEL_CONS,
            ReadOnlyID.ID_AVERAGE_FUEL_CONS_AFTER_RUNNING,
            ReadOnlyID.ID_ENGINE_RPM,
            ReadOnlyID.ID_WATER_TEMPERATURE,
            ReadOnlyID.ID_IBS_VOLTAGE,
            ReadOnlyID.ID_CAR_SPEED,
            ReadOnlyID.ID_SPEED_GAUGE_DISPLAY,
            ReadOnlyID.ID_GEARBOX_STATE,
            ReadOnlyID.ID_SYSTEM_POWER_MODE,
            ReadOnlyID.ID_AVG_SPEED,
            // Kapı / bagaj (OEM: 1=açık, 0=kapalı)
            ReadOnlyID.ID_DRIVE_DOOR_STATE,
            ReadOnlyID.ID_PASSENGER_DOOR_STATE,
            ReadOnlyID.ID_LFFT_BEHIND_DOOR_STATE,
            ReadOnlyID.ID_RIGHT_BEHIND_DOOR_STATE,
            ReadOnlyID.ID_TRUNK_STATE,
    };

    /** Cam tavan — MODULE_CAR_SETTING (SPF = sunroof). */
    static final int[] SUBSCRIBED_SETTING_IDS = {
            CarSettingID.ID_CAR_SPF_OPERATE,
            CarSettingID.ID_CAR_SPF_POSI,
    };

    /** Sürüş modu — MODULE_NEW_ENERGY. */
    static final int[] SUBSCRIBED_NEW_ENERGY_IDS = {
            NewEnergyID.ID_DRIVE_MODE,
    };

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final VehicleMetricsCache cache = new VehicleMetricsCache();

    private volatile boolean callbacksRegistered;
    private volatile boolean started;

    private final IVDBusNotify.Stub carInfoNotifyStub = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (event == null || event.getPayload() == null) {
                return;
            }
            int module = event.getId();
            if (module != MODULE_READONLY
                    && module != MODULE_SETTING
                    && module != MODULE_NEW_ENERGY) {
                return;
            }
            int itemId = event.getPayload().getInt(Constants.CMD_ID, -1);
            int[] values = event.getPayload().getIntArray(Constants.VALUE);
            if (values == null || values.length == 0) {
                return;
            }
            if (cache.update(module, itemId, values)) {
                notifyListeners();
            }
        }
    };

    public VehicleMetricsRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        if (!started) {
            start();
        } else {
            mainHandler.post(() -> listener.onMetricsUpdated(currentSnapshot()));
        }
    }

    public void removeListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            stop();
        }
    }

    public void refresh() {
        if (!started) {
            return;
        }
        ioExecutor.execute(() -> {
            loadSnapshotFromBus();
            notifyListeners();
        });
    }

    public void release() {
        listeners.clear();
        stop();
        ioExecutor.shutdownNow();
    }

    public VehicleMetricsSnapshot currentSnapshot() {
        return VehicleMetricsSnapshot.from(cache, isCarInfoConnected());
    }

    public int getCombined(int readOnlyId) {
        return cache.getCombined(readOnlyId);
    }

    public boolean isConnected() {
        return isCarInfoConnected();
    }

    private void start() {
        if (started) {
            return;
        }
        started = true;
        ioExecutor.execute(() -> {
            try {
                ensureCarInfoReady();
                registerCallbacksIfNeeded();
                loadSnapshotFromBus();
            } catch (Throwable ignored) {
                cache.clear();
            }
            notifyListeners();
        });
    }

    private void stop() {
        started = false;
        unregisterCallbacks();
    }

    private void notifyListeners() {
        if (listeners.isEmpty()) {
            return;
        }
        VehicleMetricsSnapshot snapshot = currentSnapshot();
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onMetricsUpdated(snapshot);
            }
        });
    }

    private void ensureCarInfoReady() {
        VDBus.getDefault().init(appContext);
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        if (!proxy.isServiceConnnected()) {
            proxy.init(appContext);
        }
        for (int i = 0; i < 8 && !proxy.isServiceConnnected(); i++) {
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
            proxy.init(appContext);
        }
    }

    private boolean isCarInfoConnected() {
        return CarInfoProxy.getInstance().isServiceConnnected();
    }

    private void registerCallbacksIfNeeded() {
        if (callbacksRegistered || !isCarInfoConnected()) {
            return;
        }
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        proxy.regCallBack(MODULE_READONLY, carInfoNotifyStub, SUBSCRIBED_READONLY_IDS);
        proxy.regCallBack(MODULE_SETTING, carInfoNotifyStub, SUBSCRIBED_SETTING_IDS);
        proxy.regCallBack(MODULE_NEW_ENERGY, carInfoNotifyStub, SUBSCRIBED_NEW_ENERGY_IDS);
        callbacksRegistered = true;
    }

    private void unregisterCallbacks() {
        if (!callbacksRegistered) {
            return;
        }
        try {
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            proxy.unRegCallBack(MODULE_READONLY, carInfoNotifyStub);
            proxy.unRegCallBack(MODULE_SETTING, carInfoNotifyStub);
            proxy.unRegCallBack(MODULE_NEW_ENERGY, carInfoNotifyStub);
        } catch (Throwable ignored) {
        }
        callbacksRegistered = false;
    }

    private void loadSnapshotFromBus() {
        if (!isCarInfoConnected()) {
            cache.clear();
            return;
        }
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        for (int id : SUBSCRIBED_READONLY_IDS) {
            int[] values = proxy.getItemValues(MODULE_READONLY, id);
            if (values != null && values.length > 0) {
                cache.put(MODULE_READONLY, id, values);
            }
        }
        for (int id : SUBSCRIBED_SETTING_IDS) {
            int[] values = proxy.getItemValues(MODULE_SETTING, id);
            if (values != null && values.length > 0) {
                cache.put(MODULE_SETTING, id, values);
            }
        }
        for (int id : SUBSCRIBED_NEW_ENERGY_IDS) {
            int[] values = proxy.getItemValues(MODULE_NEW_ENERGY, id);
            if (values != null && values.length > 0) {
                cache.put(MODULE_NEW_ENERGY, id, values);
            }
        }
    }
}
