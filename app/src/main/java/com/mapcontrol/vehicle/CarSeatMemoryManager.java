package com.mapcontrol.vehicle;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.extra.project.carinfo.proxy.Constants;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

/**
 * Manages Driver Seat Memory for Chery vehicles with dual-mode support:
 * 1) MODE_SCREEN (0): Tiggo 8 2026+ (On-screen multimedia memory recall via ID 52).
 * 2) MODE_DOOR (1): Tiggo 8 Pro Max (Door physical buttons with motor coordinates 154, 155, 156
 *    calibration and ceiling/roof jump protection).
 */
public final class CarSeatMemoryManager {
    private static final String TAG = "CarSeatMemoryMgr";
    private static final String PREFS_NAME = "car_seat_memory_cache";

    public static final String PREF_MEMORY_MODE = "seat_memory_hardware_mode";
    public static final int MODE_SCREEN = 0; // Tiggo 8 2026+
    public static final int MODE_DOOR = 1;   // Tiggo 8 Pro Max

    private static final int MODULE = VDEventCarInfo.MODULE_CAR_SETTING;
    public static final int ID_SELECT_POSI = CarSettingID.ID_CAR_SELECT_SEAT_MIRROR_POSI; // 52
    public static final int ID_CUR_MIRROR = CarSettingID.ID_CAR_CUR_SEAT_MIRROR;          // 54
    public static final int ID_FR_POSI = CarSettingID.ID_CAR_SEAT_FR_POSI;                // 154
    public static final int ID_HEIGHT = CarSettingID.ID_CAR_SEAT_HEIGHT;                  // 155
    public static final int ID_BACK_REST = CarSettingID.ID_CAR_SEAT_BACK_REST_ANGLE;      // 156
    public static final int ID_CUSH_POSI = CarSettingID.ID_CAR_SEAT_CUSH_POSI;            // 170
    public static final int ID_HEAD_REST = CarSettingID.ID_CAR_SEAT_HEAD_REST_HEIGHT;     // 171
    public static final int ID_LEG_SUPPORT = CarSettingID.ID_CAR_SEAT_LEG_SUPPORT;        // 172

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

    public interface SeatCalibrationListener {
        /**
         * Called when seat position changes while waiting or calibrating.
         * @param isMoving true if actively moving/changing values
         */
        void onSeatPositionUpdate(int slot, int frPosi, int height, int backRest, boolean isMoving);

        /**
         * Called when seat movement stops and coordinates are permanently saved.
         */
        void onCalibrationComplete(int slot, int frPosi, int height, int backRest);

        /**
         * Called if calibration fails or times out.
         */
        void onCalibrationFailed(String reason);
    }

    private static volatile CarSeatMemoryManager instance;
    private static volatile Context probeContext;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean registered = false;
    private volatile long suppressLearnUntilMs = 0L;

    // Active calibration state
    private volatile int calibratingSlot = -1;
    private volatile SeatCalibrationListener calibrationListener = null;

    private final Runnable calibrationDebounceRunnable = new Runnable() {
        @Override
        public void run() {
            finishCalibrationIfActive();
        }
    };

    private final Runnable calibrationTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            cancelCalibrationWithTimeout();
        }
    };

    private final Runnable syncFromCarRunnable = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() < suppressLearnUntilMs) {
                return;
            }
            if (calibratingSlot > 0) {
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

                final int targetSlot = calibratingSlot;
                final SeatCalibrationListener listener = calibrationListener;
                if (targetSlot > 0 && listener != null) {
                    mainHandler.post(() -> {
                        int fr = readSingleInt(ID_FR_POSI);
                        int h = readSingleInt(ID_HEIGHT);
                        int back = readSingleInt(ID_BACK_REST);
                        listener.onSeatPositionUpdate(targetSlot, fr > 0 ? fr : 0,
                                h > 0 ? h : 0, back > 0 ? back : 0, true);
                    });
                    mainHandler.removeCallbacks(calibrationDebounceRunnable);
                    mainHandler.postDelayed(calibrationDebounceRunnable, 1400L);
                    return;
                }

                if (System.currentTimeMillis() < suppressLearnUntilMs) {
                    return;
                }
                mainHandler.removeCallbacks(syncFromCarRunnable);
                mainHandler.postDelayed(syncFromCarRunnable, 180L);
            }
        }
    };

    public static boolean isSeatMemoryAvailable() {
        return true;
    }

    public static boolean isSeatMemoryAvailable(Context context) {
        if (context != null) {
            probeContext = context.getApplicationContext();
        }
        return true;
    }

    public static CarSeatMemoryManager getInstance(Context context) {
        probeContext = context.getApplicationContext();
        if (instance == null) {
            synchronized (CarSeatMemoryManager.class) {
                if (instance == null) {
                    instance = new CarSeatMemoryManager(probeContext);
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

    public int getMemoryMode() {
        return prefs.getInt(PREF_MEMORY_MODE, MODE_SCREEN);
    }

    public void setMemoryMode(int mode) {
        prefs.edit().putInt(PREF_MEMORY_MODE, mode).apply();
        Log.i(TAG, "Seat memory mode set to: " + (mode == MODE_DOOR ? "DOOR (Pro Max)" : "SCREEN (2026+)"));
    }

    public boolean isDoorMode() {
        return getMemoryMode() == MODE_DOOR;
    }

    public boolean hasExplicitModePreference() {
        return prefs.contains(PREF_MEMORY_MODE);
    }

    /**
     * Seeds initial safe vehicle coordinates captured from Chery VDBus logs so slots
     * never contain 0xFF / 255 (which triggers the ceiling/forward crash).
     */
    private void seedDefaultsIfNeeded() {
        if (prefs.getBoolean("seeded_v2", false)) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        if (!prefs.contains(key(1, ID_FR_POSI))) {
            editor.putInt(key(1, ID_FR_POSI), 7);
            editor.putInt(key(1, ID_HEIGHT), 1);
            editor.putInt(key(1, ID_BACK_REST), 8);
        }
        if (!prefs.contains(key(2, ID_FR_POSI))) {
            editor.putInt(key(2, ID_FR_POSI), 8);
            editor.putInt(key(2, ID_HEIGHT), 1);
            editor.putInt(key(2, ID_BACK_REST), 7);
        }
        if (!prefs.contains(key(3, ID_FR_POSI))) {
            editor.putInt(key(3, ID_FR_POSI), 6);
            editor.putInt(key(3, ID_HEIGHT), 1);
            editor.putInt(key(3, ID_BACK_REST), 8);
        }
        editor.putBoolean("seeded_v2", true).apply();
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

    public synchronized void startDoorCalibration(int slot, SeatCalibrationListener listener) {
        if (slot < 1 || slot > 3) {
            if (listener != null) {
                listener.onCalibrationFailed("Geçersiz hafıza yuvası: " + slot);
            }
            return;
        }
        ensureInitialized();
        this.calibratingSlot = slot;
        this.calibrationListener = listener;

        mainHandler.removeCallbacks(calibrationTimeoutRunnable);
        mainHandler.removeCallbacks(calibrationDebounceRunnable);

        // 35s timeout if user does not press the door button
        mainHandler.postDelayed(calibrationTimeoutRunnable, 35000L);

        int fr = readSingleInt(ID_FR_POSI);
        int h = readSingleInt(ID_HEIGHT);
        int back = readSingleInt(ID_BACK_REST);
        if (listener != null) {
            listener.onSeatPositionUpdate(slot, fr > 0 ? fr : 0, h > 0 ? h : 0, back > 0 ? back : 0, false);
        }
        Log.i(TAG, "Door calibration started for slot " + slot + " (initial FR=" + fr + ", H=" + h + ", B=" + back + ")");
    }

    public synchronized void stopDoorCalibration() {
        calibratingSlot = -1;
        calibrationListener = null;
        mainHandler.removeCallbacks(calibrationTimeoutRunnable);
        mainHandler.removeCallbacks(calibrationDebounceRunnable);
    }

    private synchronized void cancelCalibrationWithTimeout() {
        SeatCalibrationListener listener = this.calibrationListener;
        stopDoorCalibration();
        if (listener != null) {
            listener.onCalibrationFailed("Kapı tuşu zaman aşımı (35 saniye). Kapıdaki tuşa basılmadı.");
        }
    }

    public synchronized boolean saveCurrentPositionToSlot(int slot) {
        if (slot < 1 || slot > 3) {
            return false;
        }
        ensureInitialized();
        int fr = readSingleInt(ID_FR_POSI);
        int h = readSingleInt(ID_HEIGHT);
        int back = readSingleInt(ID_BACK_REST);
        int cush = readSingleInt(ID_CUSH_POSI);
        int head = readSingleInt(ID_HEAD_REST);
        int leg = readSingleInt(ID_LEG_SUPPORT);

        if (fr <= 0 || fr >= 255) {
            fr = prefs.getInt(key(slot, ID_FR_POSI), 7);
        }
        if (h <= 0 || h >= 255) {
            h = prefs.getInt(key(slot, ID_HEIGHT), 1);
        }
        if (back <= 0 || back >= 255) {
            back = prefs.getInt(key(slot, ID_BACK_REST), 8);
        }

        saveSlotCoordinates(slot, fr, h, back, cush, head, leg);

        SeatCalibrationListener listener = this.calibrationListener;
        stopDoorCalibration();
        if (listener != null) {
            listener.onCalibrationComplete(slot, fr, h, back);
        }
        return true;
    }

    public synchronized void saveSlotCoordinates(int slot, int fr, int h, int back, int cush, int head, int leg) {
        SharedPreferences.Editor editor = prefs.edit();
        if (fr > 0 && fr < 255) {
            editor.putInt(key(slot, ID_FR_POSI), fr);
        }
        if (h > 0 && h < 255) {
            editor.putInt(key(slot, ID_HEIGHT), h);
        }
        if (back > 0 && back < 255) {
            editor.putInt(key(slot, ID_BACK_REST), back);
        }
        if (cush > 0 && cush < 255) {
            editor.putInt(key(slot, ID_CUSH_POSI), cush);
        }
        if (head > 0 && head < 255) {
            editor.putInt(key(slot, ID_HEAD_REST), head);
        }
        if (leg > 0 && leg < 255) {
            editor.putInt(key(slot, ID_LEG_SUPPORT), leg);
        }
        editor.putBoolean("calibrated_slot_" + slot, true);
        editor.apply();
        Log.i(TAG, "Saved door coordinates for slot=" + slot + " -> FR=" + fr + ", H=" + h + ", BACK=" + back);
    }

    private synchronized void finishCalibrationIfActive() {
        final int slot = this.calibratingSlot;
        final SeatCalibrationListener listener = this.calibrationListener;
        if (slot < 1 || slot > 3 || listener == null) {
            return;
        }
        int fr = readSingleInt(ID_FR_POSI);
        int h = readSingleInt(ID_HEIGHT);
        int back = readSingleInt(ID_BACK_REST);
        int cush = readSingleInt(ID_CUSH_POSI);
        int head = readSingleInt(ID_HEAD_REST);
        int leg = readSingleInt(ID_LEG_SUPPORT);

        if (fr > 0 && fr < 255) {
            saveSlotCoordinates(slot, fr, h, back, cush, head, leg);
            stopDoorCalibration();
            listener.onCalibrationComplete(slot, fr, h, back);
        } else {
            mainHandler.postDelayed(calibrationDebounceRunnable, 600L);
        }
    }

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
            if (frPosi > 0 && frPosi < 255) {
                editor.putInt(key(activeSlot, ID_FR_POSI), frPosi);
                updated = true;
            }
            if (height > 0 && height < 255) {
                editor.putInt(key(activeSlot, ID_HEIGHT), height);
                updated = true;
            }
            if (backRest > 0 && backRest < 255) {
                editor.putInt(key(activeSlot, ID_BACK_REST), backRest);
                updated = true;
            }
            if (cushPosi > 0 && cushPosi < 255) {
                editor.putInt(key(activeSlot, ID_CUSH_POSI), cushPosi);
                updated = true;
            }
            if (headRest > 0 && headRest < 255) {
                editor.putInt(key(activeSlot, ID_HEAD_REST), headRest);
                updated = true;
            }
            if (legSupport > 0 && legSupport < 255) {
                editor.putInt(key(activeSlot, ID_LEG_SUPPORT), legSupport);
                updated = true;
            }
            if (updated) {
                editor.apply();
                Log.i(TAG, "Synced slot=" + activeSlot
                        + " -> FR=" + frPosi + ", H=" + height + ", BACK=" + backRest);
            }
            return activeSlot;
        } catch (Throwable t) {
            Log.w(TAG, "syncCurrentFromCar error: " + t.getMessage());
            return -1;
        }
    }

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

    public int readSingleInt(int cmdId) {
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
     * Applies seat memory slot.
     * In MODE_DOOR (Pro Max):
     * Directly commands verified physical motor coordinates (154, 155, 156), NEVER calling
     * the IVI profile recall that causes 0xFF (255) ceiling and forward slam.
     * In MODE_SCREEN (2026+):
     * Sends ID 52 [slot, slot] to recall on-screen memory profile.
     */
    public synchronized boolean applySeatMemory(int slot) {
        if (slot < 1 || slot > 3) {
            return false;
        }
        ensureInitialized();

        // Ignore seat position notifications caused by this recall movement for 15s
        suppressLearnUntilMs = System.currentTimeMillis() + 15000L;

        if (isDoorMode()) {
            int fr = prefs.getInt(key(slot, ID_FR_POSI), -1);
            int h = prefs.getInt(key(slot, ID_HEIGHT), -1);
            int back = prefs.getInt(key(slot, ID_BACK_REST), -1);
            int cush = prefs.getInt(key(slot, ID_CUSH_POSI), -1);
            int head = prefs.getInt(key(slot, ID_HEAD_REST), -1);
            int leg = prefs.getInt(key(slot, ID_LEG_SUPPORT), -1);

            Log.i(TAG, "MODE_DOOR applySeatMemory slot=" + slot
                    + " -> FR=" + fr + ", H=" + h + ", BACK=" + back);

            boolean sent = false;
            if (fr > 0 && fr < 255) {
                sendSeatParam(ID_FR_POSI, fr);
                sent = true;
            }
            if (h > 0 && h < 255) {
                sendSeatParam(ID_HEIGHT, h);
                sent = true;
            }
            if (back > 0 && back < 255) {
                sendSeatParam(ID_BACK_REST, back);
                sent = true;
            }
            if (cush > 0 && cush < 255) {
                sendSeatParam(ID_CUSH_POSI, cush);
            }
            if (head > 0 && head < 255) {
                sendSeatParam(ID_HEAD_REST, head);
            }
            if (leg > 0 && leg < 255) {
                sendSeatParam(ID_LEG_SUPPORT, leg);
            }

            if (!sent) {
                Log.w(TAG, "MODE_DOOR slot " + slot + " uncalibrated. Applying safe baseline.");
                sendSeatParam(ID_FR_POSI, 8);
                sendSeatParam(ID_HEIGHT, 1);
                sendSeatParam(ID_BACK_REST, 7);
                sent = true;
            }
            return sent;
        } else {
            // MODE_SCREEN (2026+ OEM behavior)
            return sendSlotSelect(slot);
        }
    }

    private void sendSeatParam(int cmdId, int value) {
        if (value <= 0 || value >= 255) {
            return;
        }
        try {
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            if (proxy.isServiceConnnected()) {
                proxy.sendItemValue(MODULE, cmdId, value);
            } else {
                sendDirectVdbus(cmdId, new int[]{value});
            }
            Log.d(TAG, "Sent seat motor cmd=" + cmdId + ", val=" + value);
        } catch (Throwable t) {
            sendDirectVdbus(cmdId, new int[]{value});
        }
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
            Log.i(TAG, "Seat memory slot selected (OEM recall): [" + slot + ", " + slot + "]");
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

    public boolean isSlotCalibrated(int slot) {
        int fr = prefs.getInt(key(slot, ID_FR_POSI), -1);
        return fr > 0 && fr < 255 && prefs.getBoolean("calibrated_slot_" + slot, false);
    }

    public int getSlotPosition(int slot, int cmdId) {
        return prefs.getInt(key(slot, cmdId), -1);
    }

    public String getCachedSlotSummary(int slot) {
        int fr = prefs.getInt(key(slot, ID_FR_POSI), -1);
        int h = prefs.getInt(key(slot, ID_HEIGHT), -1);
        int back = prefs.getInt(key(slot, ID_BACK_REST), -1);
        if (fr <= 0 || fr >= 255) {
            return "Hafıza " + slot + " (Kapıdan henüz kalibre edilmedi)";
        }
        return "Hafıza " + slot + " [İleri/Geri: " + fr + ", Yükseklik: " + h + ", Sırt: " + back + "]";
    }
}
