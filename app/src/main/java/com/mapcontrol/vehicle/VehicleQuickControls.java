package com.mapcontrol.vehicle;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.extra.project.carinfo.proxy.Constants;
import com.desaysv.ivi.extra.project.carinfo.proxy.IServiceConnectListener;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OEM {@code SimpleQuickSettingFragment} merkezi kilit komutu ve durum takibi.
 * Fiziksel kumanda veya araç tarafı otomatik kilitleme VDBus üzerinden yansır.
 */
public final class VehicleQuickControls {

    public interface Listener {
        void onCentralLockChanged(boolean locked, boolean connected);
    }

    private static final int MODULE_SETTING = VDEventCarInfo.MODULE_CAR_SETTING;
    private static final int LOCK_CMD_ID = CarSettingID.ID_CAR_IVI_LOCK;
    private static final int[] SUBSCRIBED_IDS = {LOCK_CMD_ID};

    private static volatile VehicleQuickControls instance;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();

    private volatile boolean started;
    private volatile boolean callbacksRegistered;
    private volatile boolean connectListenerRegistered;
    private volatile boolean centralLocked;
    private volatile Boolean lastNotifiedLocked;
    private volatile boolean lastNotifiedConnected;

    private final IVDBusNotify.Stub carSettingNotifyStub = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (event == null || event.getPayload() == null || event.getId() != MODULE_SETTING) {
                return;
            }
            int itemId = event.getPayload().getInt(Constants.CMD_ID, -1);
            if (itemId != LOCK_CMD_ID) {
                return;
            }
            int[] values = event.getPayload().getIntArray(Constants.VALUE);
            if (values == null || values.length == 0) {
                return;
            }
            applyCentralLockValue(values[0], true);
        }
    };

    private final IServiceConnectListener serviceConnectListener = new IServiceConnectListener() {
        @Override
        public void onServiceConnectedChanged(int state) {
            if (state != 1 || !started) {
                return;
            }
            ioExecutor.execute(() -> {
                registerCallbacksIfNeeded();
                loadStateFromBus();
                notifyListeners();
            });
        }
    };

    private VehicleQuickControls(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static VehicleQuickControls getInstance(Context context) {
        if (instance == null) {
            synchronized (VehicleQuickControls.class) {
                if (instance == null) {
                    instance = new VehicleQuickControls(context);
                }
            }
        }
        return instance;
    }

    public void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        if (!started) {
            start();
        } else {
            mainHandler.post(() -> listener.onCentralLockChanged(centralLocked, isCarInfoConnected()));
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

    public void release() {
        listeners.clear();
        stop();
        ioExecutor.shutdownNow();
        instance = null;
    }

    public boolean isCarInfoConnected() {
        ensureReady();
        return CarInfoProxy.getInstance().isServiceConnnected();
    }

    /** OEM: değer 2 = kilitli */
    public boolean isCentralLocked() {
        return centralLocked;
    }

    public void setCentralLock(boolean locked) {
        ensureReady();
        if (!CarInfoProxy.getInstance().isServiceConnnected()) {
            return;
        }
        int value = locked ? 2 : 1;
        CarInfoProxy.getInstance().sendItemValue(MODULE_SETTING, LOCK_CMD_ID, value);
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
                registerConnectListenerIfNeeded();
                loadStateFromBus();
            } catch (Throwable ignored) {
                centralLocked = false;
            }
            notifyListeners();
        });
    }

    private void stop() {
        started = false;
        unregisterCallbacks();
        unregisterConnectListener();
        lastNotifiedLocked = null;
        lastNotifiedConnected = false;
    }

    private void applyCentralLockValue(int rawValue, boolean notify) {
        centralLocked = rawValue == 2;
        if (notify) {
            notifyListeners();
        }
    }

    private void loadStateFromBus() {
        if (!isCarInfoConnected()) {
            centralLocked = false;
            return;
        }
        int value = CarInfoProxy.getInstance().getItemValue(MODULE_SETTING, LOCK_CMD_ID);
        applyCentralLockValue(value, false);
    }

    private void notifyListeners() {
        if (listeners.isEmpty()) {
            return;
        }
        boolean connected = isCarInfoConnected();
        boolean locked = centralLocked;
        Boolean previousLocked = lastNotifiedLocked;
        if (previousLocked != null
                && previousLocked == locked
                && lastNotifiedConnected == connected) {
            return;
        }
        lastNotifiedLocked = locked;
        lastNotifiedConnected = connected;
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onCentralLockChanged(locked, connected);
            }
        });
    }

    private void ensureReady() {
        VDBus.getDefault().init(appContext);
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        if (!proxy.isServiceConnnected()) {
            proxy.init(appContext);
        }
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

    private void registerCallbacksIfNeeded() {
        if (callbacksRegistered || !isCarInfoConnected()) {
            return;
        }
        CarInfoProxy.getInstance().regCallBack(MODULE_SETTING, carSettingNotifyStub, SUBSCRIBED_IDS);
        callbacksRegistered = true;
    }

    private void unregisterCallbacks() {
        if (!callbacksRegistered) {
            return;
        }
        try {
            CarInfoProxy.getInstance().unRegCallBack(MODULE_SETTING, carSettingNotifyStub);
        } catch (Throwable ignored) {
        }
        callbacksRegistered = false;
    }

    private void registerConnectListenerIfNeeded() {
        if (connectListenerRegistered) {
            return;
        }
        CarInfoProxy.getInstance().regServiceConnectListener(serviceConnectListener);
        connectListenerRegistered = true;
    }

    private void unregisterConnectListener() {
        if (!connectListenerRegistered) {
            return;
        }
        try {
            CarInfoProxy.getInstance().unRegServiceConnectListener(serviceConnectListener);
        } catch (Throwable ignored) {
        }
        connectListenerRegistered = false;
    }
}
