package com.mapcontrol.vehicle;

import android.content.Context;
import android.os.Bundle;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.extra.project.carinfo.proxy.Constants;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.mapcontrol.R;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SeatMemoryVdbusTest {
    public interface Logger {
        void log(String line);
    }

    private final Context appContext;
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<Integer, String> signals;
    private volatile boolean running;
    private volatile boolean registered;
    private boolean completionLogged;

    private final IVDBusNotify.Stub callback = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (!running || event == null || event.getPayload() == null
                    || event.getId() != VDEventCarInfo.MODULE_CAR_SETTING) {
                return;
            }
            Bundle payload = event.getPayload();
            int itemId = payload.getInt(Constants.CMD_ID, -1);
            int[] values = payload.getIntArray(Constants.VALUE);
            String name = signals.get(itemId);
            if (name != null) {
                logger.log(appContext.getString(R.string.cluster_test_seat_memory_event,
                        timestamp(), name, itemId, formatValues(values)));
            }
        }
    };

    public SeatMemoryVdbusTest(Context context, Logger logger) {
        this.appContext = context.getApplicationContext();
        this.logger = logger;
        this.signals = findSeatMemorySignals();
    }

    public synchronized boolean start() {
        if (running) {
            return false;
        }
        running = true;
        completionLogged = false;
        executor.execute(this::startCapture);
        return true;
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        executor.execute(this::finishCapture);
    }

    public void shutdown() {
        running = false;
        try {
            if (registered) {
                CarInfoProxy.getInstance().unRegCallBack(
                        VDEventCarInfo.MODULE_CAR_SETTING, callback);
                registered = false;
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void startCapture() {
        try {
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            if (!proxy.isServiceConnnected()) {
                logger.log(appContext.getString(R.string.cluster_test_seat_memory_connecting));
                proxy.init(appContext);
                for (int attempt = 0; attempt < 20 && running
                        && !proxy.isServiceConnnected(); attempt++) {
                    Thread.sleep(500);
                }
            }
            if (!running) {
                finishCapture();
                return;
            }
            if (!proxy.isServiceConnnected()) {
                logger.log(appContext.getString(R.string.cluster_test_seat_memory_no_connection));
                finishCapture();
                return;
            }
            if (signals.isEmpty()) {
                logger.log(appContext.getString(R.string.cluster_test_seat_memory_no_signals));
                finishCapture();
                return;
            }

            int[] ids = new int[signals.size()];
            int index = 0;
            StringBuilder names = new StringBuilder();
            for (Map.Entry<Integer, String> signal : signals.entrySet()) {
                ids[index++] = signal.getKey();
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(signal.getValue()).append('=').append(signal.getKey());
            }
            proxy.regCallBack(VDEventCarInfo.MODULE_CAR_SETTING, callback, ids);
            registered = true;
            logger.log(appContext.getString(R.string.cluster_test_seat_memory_header,
                    timestamp(), VDEventCarInfo.MODULE_CAR_SETTING, names));
            for (Map.Entry<Integer, String> signal : signals.entrySet()) {
                if (!running) {
                    break;
                }
                try {
                    int[] values = proxy.getItemValues(
                            VDEventCarInfo.MODULE_CAR_SETTING, signal.getKey());
                    logger.log(appContext.getString(R.string.cluster_test_seat_memory_initial,
                            signal.getValue(), signal.getKey(), formatValues(values)));
                } catch (RuntimeException e) {
                    logger.log(appContext.getString(R.string.cluster_test_seat_memory_read_error,
                            signal.getValue(), signal.getKey(),
                            e.getClass().getSimpleName(), String.valueOf(e.getMessage())));
                }
            }
            if (!running) {
                finishCapture();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
            finishCapture();
        } catch (RuntimeException e) {
            logger.log(appContext.getString(R.string.cluster_test_seat_memory_error,
                    e.getClass().getSimpleName(), String.valueOf(e.getMessage())));
            running = false;
            finishCapture();
        }
    }

    private synchronized void finishCapture() {
        if (completionLogged) {
            return;
        }
        completionLogged = true;
        running = false;
        if (registered) {
            CarInfoProxy.getInstance().unRegCallBack(
                    VDEventCarInfo.MODULE_CAR_SETTING, callback);
            registered = false;
        }
        logger.log(appContext.getString(R.string.cluster_test_seat_memory_done));
    }

    private Map<Integer, String> findSeatMemorySignals() {
        Map<Integer, String> result = new TreeMap<>();
        for (Field field : CarSettingID.class.getFields()) {
            String name = field.getName();
            if (!name.contains("SEAT") && !name.contains("MEMORY") && !name.contains("FACE")) {
                continue;
            }
            try {
                int id = field.getInt(null);
                String previous = result.get(id);
                result.put(id, previous == null ? name : previous + "/" + name);
            } catch (IllegalAccessException e) {
                logger.log(appContext.getString(R.string.cluster_test_seat_memory_field_error,
                        name, e.getMessage()));
            }
        }
        return result;
    }

    private String formatValues(int[] values) {
        return values == null || values.length == 0 ? "no-response" : Arrays.toString(values);
    }

    private String timestamp() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
                java.util.Locale.getDefault()).format(new java.util.Date());
    }
}
