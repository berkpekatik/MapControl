package com.mapcontrol.vehicle;

import android.content.Context;
import android.os.Bundle;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.HUDID;
import com.desaysv.ivi.extra.project.carinfo.HvacID;
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.extra.project.carinfo.proxy.Constants;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.mapcontrol.R;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VehicleVdbusDiscoveryTest {
    public interface Logger {
        void log(String line);
    }

    private static final int FIRST_ITEM_ID = 0;
    private static final int LAST_ITEM_ID = 255;

    private final Context appContext;
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Module> modules;
    private final Map<Integer, Map<Integer, int[]>> initialValues = new HashMap<>();
    private volatile boolean running;
    private volatile boolean subscriptionsRegistered;
    private boolean completionLogged;

    private final IVDBusNotify.Stub callback = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (!running || event == null || event.getPayload() == null) {
                return;
            }
            int moduleId = event.getId();
            Module module = findModule(moduleId);
            if (module == null) {
                return;
            }
            Bundle payload = event.getPayload();
            int itemId = payload.getInt(Constants.CMD_ID, -1);
            if (itemId < FIRST_ITEM_ID || itemId > LAST_ITEM_ID) {
                return;
            }
            int[] values = payload.getIntArray(Constants.VALUE);
            int[] previous = initialValues.getOrDefault(moduleId, Collections.emptyMap())
                    .get(itemId);
            logger.log(appContext.getString(R.string.cluster_test_vdbus_change,
                    timestamp(), module.name, moduleId, itemName(module, itemId),
                    itemId, formatValues(previous), formatValues(values)));
            Map<Integer, int[]> moduleValues = initialValues.computeIfAbsent(
                    moduleId, ignored -> new HashMap<>());
            if (values != null) {
                moduleValues.put(itemId, values.clone());
            }
        }
    };

    public VehicleVdbusDiscoveryTest(Context context, Logger logger) {
        this.appContext = context.getApplicationContext();
        this.logger = logger;
        this.modules = findModules();
    }

    public synchronized boolean start() {
        if (running) {
            return false;
        }
        running = true;
        completionLogged = false;
        initialValues.clear();
        executor.execute(this::scanAndListen);
        return true;
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        executor.execute(this::finish);
    }

    public void shutdown() {
        running = false;
        try {
            unsubscribe();
        } finally {
            executor.shutdownNow();
        }
    }

    private void scanAndListen() {
        try {
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            if (!proxy.isServiceConnnected()) {
                logger.log(appContext.getString(R.string.cluster_test_vdbus_connecting));
                proxy.init(appContext);
                for (int attempt = 0; attempt < 20 && running
                        && !proxy.isServiceConnnected(); attempt++) {
                    Thread.sleep(500);
                }
            }
            if (!running) {
                finish();
                return;
            }
            if (!proxy.isServiceConnnected()) {
                logger.log(appContext.getString(R.string.cluster_test_vdbus_no_connection));
                finish();
                return;
            }
            if (modules.isEmpty()) {
                logger.log(appContext.getString(R.string.cluster_test_vdbus_no_modules));
                finish();
                return;
            }

            logger.log(appContext.getString(R.string.cluster_test_vdbus_header,
                    timestamp(), modules.size(), FIRST_ITEM_ID, LAST_ITEM_ID));
            VDBus bus = VDBus.getDefault();
            subscriptionsRegistered = true;
            for (Module module : modules) {
                if (!running) {
                    finish();
                    return;
                }
                int[] ids = new int[LAST_ITEM_ID - FIRST_ITEM_ID + 1];
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = FIRST_ITEM_ID + i;
                }
                Map<Integer, int[]> moduleValues = new HashMap<>();
                initialValues.put(module.id, moduleValues);
                int responseCount = 0;
                int noResponseCount = 0;
                logger.log(appContext.getString(R.string.cluster_test_vdbus_module,
                        module.name, module.id));
                for (int itemId = FIRST_ITEM_ID; itemId <= LAST_ITEM_ID && running; itemId++) {
                    Bundle request = new Bundle();
                    request.putInt(Constants.CMD_ID, itemId);
                    try {
                        VDEvent response = bus.getOnce(new VDEvent(module.id, request));
                        int[] values = valuesFrom(response);
                        if (values == null) {
                            noResponseCount++;
                            continue;
                        }
                        responseCount++;
                        moduleValues.put(itemId, values.clone());
                        logger.log(appContext.getString(R.string.cluster_test_vdbus_value,
                                module.name, module.id, itemName(module, itemId), itemId,
                                formatValues(values)));
                    } catch (RuntimeException e) {
                        noResponseCount++;
                        logger.log(appContext.getString(R.string.cluster_test_vdbus_read_error,
                                module.name, module.id, itemName(module, itemId), itemId,
                                e.getClass().getSimpleName(), String.valueOf(e.getMessage())));
                    }
                }
                if (!running) {
                    finish();
                    return;
                }
                logger.log(appContext.getString(R.string.cluster_test_vdbus_module_done,
                        module.name, responseCount, noResponseCount));
            }

            if (!running) {
                finish();
                return;
            }
            for (Module module : modules) {
                if (!running) {
                    finish();
                    return;
                }
                int[] ids = new int[LAST_ITEM_ID - FIRST_ITEM_ID + 1];
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = FIRST_ITEM_ID + i;
                }
                proxy.regCallBack(module.id, callback, ids);
            }
            logger.log(appContext.getString(R.string.cluster_test_vdbus_listening));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
            finish();
        } catch (RuntimeException e) {
            logger.log(appContext.getString(R.string.cluster_test_vdbus_error,
                    e.getClass().getSimpleName(), String.valueOf(e.getMessage())));
            running = false;
            finish();
        }
    }

    private synchronized void finish() {
        if (completionLogged) {
            return;
        }
        completionLogged = true;
        running = false;
        unsubscribe();
        logger.log(appContext.getString(R.string.cluster_test_vdbus_done));
    }

    private void unsubscribe() {
        if (!subscriptionsRegistered) {
            return;
        }
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        for (Module module : modules) {
            proxy.unRegCallBack(module.id, callback);
        }
        subscriptionsRegistered = false;
    }

    private int[] valuesFrom(VDEvent response) {
        if (response == null || response.getPayload() == null) {
            return null;
        }
        return response.getPayload().getIntArray(Constants.VALUE);
    }

    private Module findModule(int id) {
        for (Module module : modules) {
            if (module.id == id) {
                return module;
            }
        }
        return null;
    }

    private String itemName(Module module, int id) {
        String name = module.itemNames.get(id);
        return name == null ? "ID" : name;
    }

    private List<Module> findModules() {
        List<Module> result = new ArrayList<>();
        for (Field field : VDEventCarInfo.class.getFields()) {
            if (!field.getName().startsWith("MODULE_")) {
                continue;
            }
            try {
                int id = field.getInt(null);
                result.add(new Module(field.getName(), id, itemNamesFor(id)));
            } catch (IllegalAccessException e) {
                logger.log(appContext.getString(R.string.cluster_test_vdbus_field_error,
                        field.getName(), e.getMessage()));
            }
        }
        Collections.sort(result, (first, second) -> Integer.compare(first.id, second.id));
        return result;
    }

    private Map<Integer, String> itemNamesFor(int moduleId) {
        Class<?> itemClass = null;
        if (moduleId == VDEventCarInfo.MODULE_CAR_SETTING) {
            itemClass = CarSettingID.class;
        } else if (moduleId == VDEventCarInfo.MODULE_HVAC) {
            itemClass = HvacID.class;
        } else if (moduleId == VDEventCarInfo.MODULE_READONLY_INFO) {
            itemClass = ReadOnlyID.class;
        } else if (moduleId == VDEventCarInfo.MODULE_NEW_ENERGY) {
            itemClass = NewEnergyID.class;
        } else if (moduleId == VDEventCarInfo.MODULE_HUD) {
            itemClass = HUDID.class;
        }
        Map<Integer, String> names = new HashMap<>();
        if (itemClass == null) {
            return names;
        }
        for (Field field : itemClass.getFields()) {
            if (!field.getName().startsWith("ID_")) {
                continue;
            }
            try {
                int id = field.getInt(null);
                String existing = names.get(id);
                names.put(id, existing == null ? field.getName()
                        : existing + "/" + field.getName());
            } catch (IllegalAccessException e) {
                logger.log(appContext.getString(R.string.cluster_test_vdbus_field_error,
                        field.getName(), e.getMessage()));
            }
        }
        return names;
    }

    private String formatValues(int[] values) {
        return values == null ? "no-response" : Arrays.toString(values);
    }

    private String timestamp() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
                java.util.Locale.getDefault()).format(new java.util.Date());
    }

    private static final class Module {
        final String name;
        final int id;
        final Map<Integer, String> itemNames;

        Module(String name, int id, Map<Integer, String> itemNames) {
            this.name = name;
            this.id = id;
            this.itemNames = itemNames;
        }
    }
}
