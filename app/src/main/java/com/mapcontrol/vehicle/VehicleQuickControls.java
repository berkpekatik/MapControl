package com.mapcontrol.vehicle;

import android.content.Context;
import android.os.Bundle;
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

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OEM {@code SimpleQuickSettingFragment} merkezi kilit komutu ve durum takibi.
 * Fiziksel kumanda veya araç tarafı otomatik kilitleme VDBus üzerinden yansır.
 * Araçtan gerçek bir cevap gelmediyse kilit durumu bilinmiyor ({@code null}) kalır.
 */
public final class VehicleQuickControls {

    public interface Listener {
        /** @param locked {@code null}: araçtan kilit verisi gelmedi (bilinmiyor). */
        void onCentralLockChanged(Boolean locked, boolean connected);
    }

    private static final int MODULE_SETTING = VDEventCarInfo.MODULE_CAR_SETTING;
    private static final int LOCK_CMD_ID = CarSettingID.ID_CAR_IVI_LOCK;
    private static final int[] SUBSCRIBED_IDS = {LOCK_CMD_ID};

    private static volatile VehicleQuickControls instance;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** Daemon ve boşta 30 sn sonra biten tek iş parçacığı; tekil örnek kapatılmadığı için uygulama kapanışını tutmaz. */
    private final ExecutorService ioExecutor = newDaemonSingleThread();
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();

    private volatile boolean started;
    private volatile boolean callbacksRegistered;
    private volatile boolean connectListenerRegistered;
    /** {@code null}: araçtan kilit verisi gelmedi (bilinmiyor). */
    private volatile Boolean centralLocked;
    private volatile boolean notifiedOnce;
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
            Boolean locked = lockedFromReply(event.getPayload().getIntArray(Constants.VALUE));
            if (locked == null) {
                return;
            }
            centralLocked = locked;
            notifyListeners();
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

    /**
     * Başka dinleyicileri etkilememek için dinleyicileri silmez ve tekil örneği kapatmaz;
     * yalnızca dinleyen kalmadıysa takibi durdurur. Her sahip kendi dinleyicisini removeListener ile çıkarır.
     */
    public void release() {
        if (listeners.isEmpty()) {
            stop();
        }
    }

    private static ExecutorService newDaemonSingleThread() {
        java.util.concurrent.ThreadPoolExecutor ex = new java.util.concurrent.ThreadPoolExecutor(
                1, 1, 30L, java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "quickcontrols-io");
                    t.setDaemon(true);
                    return t;
                });
        ex.allowCoreThreadTimeOut(true);
        return ex;
    }

    public boolean isCarInfoConnected() {
        ensureReady();
        return CarInfoProxy.getInstance().isServiceConnnected();
    }

    /** {@code true} kilitli, {@code false} açık, {@code null} bilinmiyor (bkz. {@link #lockedFromReply(int[])}). */
    public Boolean isCentralLocked() {
        return centralLocked;
    }

    /**
     * Kilit komutu değeri: kilitle 2, aç 1. Tiggo 7'de denendi; her yazmadan sonra aynı ID'nin geri okuması
     * değişti. Geri okuma kodlaması komuttan farklıdır, bkz. {@link #isLockedValue(int)}.
     */
    public static int centralLockValue(boolean locked) {
        return locked ? 2 : 1;
    }

    /** Geri okunan kilit değeri: 0 kilitli, 1 açık (Tiggo 7'de ölçüldü). */
    static boolean isLockedValue(int rawValue) {
        return rawValue == 0;
    }

    /**
     * Araç cevabındaki kilit durumu. Cevap yoksa ya da değer dizisi boşsa {@code null} (bilinmiyor):
     * {@code CarInfoProxy.getItemValues} cevap gelmeyince {@code {0}} döndürür ve 0 "kilitli" demek olduğundan
     * kilit durumu bu yardımcıyla değil, doğrudan VDBus cevabından okunur.
     */
    static Boolean lockedFromReply(int[] values) {
        if (values == null || values.length == 0) {
            return null;
        }
        return isLockedValue(values[0]);
    }

    public void setCentralLock(boolean locked) {
        ensureReady();
        if (!CarInfoProxy.getInstance().isServiceConnnected()) {
            return;
        }
        int value = centralLockValue(locked);
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
                centralLocked = null;
            }
            notifyListeners();
        });
    }

    private void stop() {
        started = false;
        unregisterCallbacks();
        unregisterConnectListener();
        notifiedOnce = false;
        lastNotifiedLocked = null;
        lastNotifiedConnected = false;
    }

    /**
     * Kilit durumu doğrudan VDBus cevabından okunur ({@code CarInfoProxy.getItemValue} cevap yokken 0 = "kilitli"
     * döndürdüğü için kullanılmaz). Cevap gelmediyse ya da araçta bu sinyal yoksa durum bilinmiyor kalır.
     */
    private void loadStateFromBus() {
        if (!isCarInfoConnected()) {
            centralLocked = null;
            return;
        }
        Bundle request = new Bundle();
        request.putInt(Constants.CMD_ID, LOCK_CMD_ID);
        VDEvent reply = VDBus.getDefault().getOnce(new VDEvent(MODULE_SETTING, request));
        Bundle payload = reply == null ? null : reply.getPayload();
        centralLocked = lockedFromReply(payload == null ? null : payload.getIntArray(Constants.VALUE));
    }

    private void notifyListeners() {
        if (listeners.isEmpty()) {
            return;
        }
        boolean connected = isCarInfoConnected();
        Boolean locked = centralLocked;
        if (notifiedOnce
                && Objects.equals(lastNotifiedLocked, locked)
                && lastNotifiedConnected == connected) {
            return;
        }
        notifiedOnce = true;
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
