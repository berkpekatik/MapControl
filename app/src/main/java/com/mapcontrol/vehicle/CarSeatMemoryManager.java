package com.mapcontrol.vehicle;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.extra.project.carinfo.proxy.Constants;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

/**
 * Synchronizes with the vehicle's own seat memory (`CarSettings` / `MODULE_CAR_SETTING = 327681`)
 * and selects the driver's seat & mirror memory slot (`1..3`).
 */
public final class CarSeatMemoryManager {
    private static final String TAG = "CarSeatMemoryMgr";
    private static final String PREFS_NAME = "car_seat_memory_cache";

    private static final int MODULE = VDEventCarInfo.MODULE_CAR_SETTING;
    private static final int ID_SELECT_POSI = CarSettingID.ID_CAR_SELECT_SEAT_MIRROR_POSI; // 52
    private static final int ID_CUR_MIRROR = CarSettingID.ID_CAR_CUR_SEAT_MIRROR;          // 54
    private static final int ID_FR_POSI = CarSettingID.ID_CAR_SEAT_FR_POSI;                // 154
    private static final int ID_HEIGHT = CarSettingID.ID_CAR_SEAT_HEIGHT;                  // 155
    private static final int ID_BACK_REST = CarSettingID.ID_CAR_SEAT_BACK_REST_ANGLE;      // 156
    private static final int ID_CUSH_POSI = CarSettingID.ID_CAR_SEAT_CUSH_POSI;            // 170
    private static final int ID_HEAD_REST = CarSettingID.ID_CAR_SEAT_HEAD_REST_HEIGHT;     // 171
    private static final int ID_LEG_SUPPORT = CarSettingID.ID_CAR_SEAT_LEG_SUPPORT;        // 172

    private static final int[] MONITORED_IDS = new int[]{
            ID_SELECT_POSI,
            ID_CUR_MIRROR,
            ID_FR_POSI,
            ID_HEIGHT,
            ID_BACK_REST,
            ID_CUSH_POSI,
            ID_HEAD_REST,
            ID_LEG_SUPPORT
    };

    private static volatile CarSeatMemoryManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean registered = false;
    private volatile long suppressLearnUntilMs = 0L;

    private final Runnable syncFromCarRunnable = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() < suppressLearnUntilMs) {
                return;
            }
            syncCurrentFromCar();
        }
    };

    private final IVDBusNotify.Stub notifyCallback = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (event == null || event.getId() != MODULE || event.getPayload() == null) {
                return;
            }
            int cmdId = event.getPayload().getInt(Constants.CMD_ID, -1);
            if (cmdId == ID_SELECT_POSI
                    || cmdId == ID_CUR_MIRROR
                    || cmdId == ID_FR_POSI
                    || cmdId == ID_HEIGHT
                    || cmdId == ID_BACK_REST
                    || cmdId == ID_CUSH_POSI
                    || cmdId == ID_HEAD_REST
                    || cmdId == ID_LEG_SUPPORT) {
                if (System.currentTimeMillis() < suppressLearnUntilMs) {
                    return;
                }
                mainHandler.removeCallbacks(syncFromCarRunnable);
                mainHandler.postDelayed(syncFromCarRunnable, 180L);
            }
        }
    };

    public static CarSeatMemoryManager getInstance(Context context) {
        if (instance == null) {
            synchronized (CarSeatMemoryManager.class) {
                if (instance == null) {
                    instance = new CarSeatMemoryManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private CarSeatMemoryManager(Context context) {
        this.appContext = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        seedDefaultsIfNeeded();
    }

    /**
     * Seeds initial vehicle coordinates captured from the car's own VDBus (`20261019_195809.jpg` &
     * `20261019_195913.jpg`) so that even before the user taps a slot in the car UI, Slot 1 and Slot 2
     * already have the vehicle's known coordinates, which are then kept up-to-date automatically
     * whenever the car reports a slot/position change.
     */
    private void seedDefaultsIfNeeded() {
        if (prefs.getBoolean("seeded_v1", false)) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        // Vehicle log 20261019_195913.jpg: Slot 1 -> FR_POSI=7, HEIGHT=1, BACK_REST=8
        if (!prefs.contains(key(1, ID_FR_POSI))) {
            editor.putInt(key(1, ID_FR_POSI), 7);
            editor.putInt(key(1, ID_HEIGHT), 1);
            editor.putInt(key(1, ID_BACK_REST), 8);
        }
        // Vehicle log 20261019_195809.jpg: Slot 2 -> FR_POSI=8, HEIGHT=1, BACK_REST=7
        if (!prefs.contains(key(2, ID_FR_POSI))) {
            editor.putInt(key(2, ID_FR_POSI), 8);
            editor.putInt(key(2, ID_HEIGHT), 1);
            editor.putInt(key(2, ID_BACK_REST), 7);
        }
        editor.putBoolean("seeded_v1", true).apply();
    }

    private static String key(int slot, int cmdId) {
        return "slot_" + slot + "_cmd_" + cmdId;
    }

    public synchronized void ensureInitialized() {
        try {
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            if (!proxy.isServiceConnnected()) {
                proxy.init(appContext);
            }
            if (!registered && proxy.isServiceConnnected()) {
                proxy.regCallBack(MODULE, notifyCallback, MONITORED_IDS);
                registered = true;
            }
            mainHandler.postDelayed(syncFromCarRunnable, 350L);
            mainHandler.postDelayed(syncFromCarRunnable, 1500L);
        } catch (Throwable t) {
            Log.w(TAG, "ensureInitialized failed: " + t.getMessage());
        }
    }

    /**
     * Reads the active seat memory slot and current seat motor coordinates from `CarInfoProxy.getItemValues`
     * (which calls `VDBus.getDefault().getOnce(...)`) and caches them under that slot.
     */
    public synchronized int syncCurrentFromCar() {
        try {
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            if (!proxy.isServiceConnnected()) {
                return -1;
            }
            if (!registered) {
                try {
                    proxy.regCallBack(MODULE, notifyCallback, MONITORED_IDS);
                    registered = true;
                } catch (Throwable ignored) {
                }
            }
            int activeSlot = readActiveSlotFromCar();
            if (activeSlot < 1 || activeSlot > 3) {
                return -1;
            }
            int frPosi = readSingleInt(ID_FR_POSI);
            int height = readSingleInt(ID_HEIGHT);
            int backRest = readSingleInt(ID_BACK_REST);
            int cushPosi = readSingleInt(ID_CUSH_POSI);
            int headRest = readSingleInt(ID_HEAD_REST);
            int legSupport = readSingleInt(ID_LEG_SUPPORT);

            SharedPreferences.Editor editor = prefs.edit();
            boolean updated = false;
            if (frPosi > 0) {
                editor.putInt(key(activeSlot, ID_FR_POSI), frPosi);
                updated = true;
            }
            if (height > 0) {
                editor.putInt(key(activeSlot, ID_HEIGHT), height);
                updated = true;
            }
            if (backRest > 0) {
                editor.putInt(key(activeSlot, ID_BACK_REST), backRest);
                updated = true;
            }
            if (cushPosi > 0) {
                editor.putInt(key(activeSlot, ID_CUSH_POSI), cushPosi);
                updated = true;
            }
            if (headRest > 0) {
                editor.putInt(key(activeSlot, ID_HEAD_REST), headRest);
                updated = true;
            }
            if (legSupport > 0) {
                editor.putInt(key(activeSlot, ID_LEG_SUPPORT), legSupport);
                updated = true;
            }
            if (updated) {
                editor.apply();
                Log.i(TAG, "Synced car seat memory slot=" + activeSlot
                        + " -> FR=" + frPosi + ", H=" + height + ", BACK=" + backRest
                        + ", CUSH=" + cushPosi + ", HEAD=" + headRest + ", LEG=" + legSupport);
            }
            return activeSlot;
        } catch (Throwable t) {
            Log.w(TAG, "syncCurrentFromCar error: " + t.getMessage());
            return -1;
        }
    }

    /**
     * Returns the currently selected slot (`1`, `2`, or `3`) on the car's own screen, or `-1` if unknown.
     */
    public int readActiveSlotFromCar() {
        try {
            int[] selectVals = CarInfoProxy.getInstance().getItemValues(MODULE, ID_SELECT_POSI);
            if (selectVals != null) {
                if (selectVals.length >= 1 && selectVals[0] >= 1 && selectVals[0] <= 3) {
                    return selectVals[0];
                }
                if (selectVals.length >= 2 && selectVals[1] >= 1 && selectVals[1] <= 3) {
                    return selectVals[1];
                }
            }
            int curMirror = CarInfoProxy.getInstance().getItemValue(MODULE, ID_CUR_MIRROR);
            if (curMirror >= 1 && curMirror <= 3) {
                return curMirror;
            }
        } catch (Throwable t) {
            Log.w(TAG, "readActiveSlotFromCar failed: " + t.getMessage());
        }
        return -1;
    }

    private int readSingleInt(int cmdId) {
        try {
            int[] vals = CarInfoProxy.getInstance().getItemValues(MODULE, cmdId);
            if (vals != null && vals.length > 0) {
                return vals[0];
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    /**
     * Selects (recalls) the requested seat & mirror memory slot (`1..3`) on the car.
     *
     * `ID_CAR_SELECT_SEAT_MIRROR_POSI (52)` takes a 2-element array `[seatSlot, mirrorSlot]`:
     * - Slot 1 -> `[1, 1]`
     * - Slot 2 -> `[2, 2]`
     * - Slot 3 -> `[3, 3]`
     *
     * No save request (`[0, slot]`), no `ID_CAR_CUR_SEAT_MIRROR` write and no seat motor coordinate
     * writes (`154, 155, 156, 170, 171, 172`) are sent, because those were interpreted by the car
     * as memory save / memory setting requests and caused warnings on the driver display.
     */
    public synchronized boolean applySeatMemory(int slot) {
        if (slot < 1 || slot > 3) {
            return false;
        }
        ensureInitialized();

        // Ignore the seat position notifications caused by the recall movement
        suppressLearnUntilMs = System.currentTimeMillis() + 15000L;
        return sendSlotSelect(slot);
    }

    private boolean sendSlotSelect(int slot) {
        try {
            int[] payload = new int[]{slot, slot};
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            if (!proxy.isServiceConnnected()) {
                proxy.init(appContext);
            }
            if (proxy.isServiceConnnected()) {
                proxy.sendItemValues(MODULE, ID_SELECT_POSI, payload);
            } else {
                sendDirectVdbus(ID_SELECT_POSI, payload);
            }
            Log.i(TAG, "Seat memory slot selected (recall only): [" + slot + ", " + slot + "]");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "sendSlotSelect failed for slot=" + slot + ": " + t.getMessage());
            return false;
        }
    }

    private void sendDirectVdbus(int cmdId, int[] values) {
        try {
            Bundle bundle = new Bundle();
            bundle.putInt(Constants.CMD_ID, cmdId);
            bundle.putIntArray(Constants.VALUE, values);
            VDBus.getDefault().set(new VDEvent(MODULE, bundle));
        } catch (Throwable ignored) {
        }
    }

    public String getCachedSlotSummary(int slot) {
        int fr = prefs.getInt(key(slot, ID_FR_POSI), -1);
        int h = prefs.getInt(key(slot, ID_HEIGHT), -1);
        int back = prefs.getInt(key(slot, ID_BACK_REST), -1);
        return "Hafıza " + slot + " [İleri/Geri=" + fr + ", Yükseklik=" + h + ", Sırt=" + back + "]";
    }
}
