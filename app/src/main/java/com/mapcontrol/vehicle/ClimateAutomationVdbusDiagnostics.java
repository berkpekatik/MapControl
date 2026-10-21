package com.mapcontrol.vehicle;

import android.content.Context;
import android.content.SharedPreferences;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.HvacID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.mapcontrol.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ClimateAutomationVdbusDiagnostics {
    public interface Logger {
        void log(String line);
    }

    private final Context appContext;
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean liveCaptureRunning;
    private volatile int liveCaptureRunId;
    private int lastLoggedLivePower = Integer.MIN_VALUE;
    private int lastLoggedLiveCarPower = Integer.MIN_VALUE;
    private int lastLoggedLiveTemp = Integer.MIN_VALUE;
    private long lastLiveFeedbackAt;

    public ClimateAutomationVdbusDiagnostics(Context context, Logger logger) {
        this.appContext = context.getApplicationContext();
        this.logger = logger;
    }

    public void collect() {
        executor.execute(this::collectSnapshot);
    }

    public synchronized boolean startLiveCapture() {
        if (liveCaptureRunning) {
            return false;
        }
        liveCaptureRunning = true;
        int runId = ++liveCaptureRunId;
        executor.execute(() -> collectLiveSamples(runId));
        return true;
    }

    public synchronized void stopLiveCapture() {
        liveCaptureRunning = false;
    }

    public void stop() {
        liveCaptureRunning = false;
        executor.shutdownNow();
    }

    private void collectLiveSamples(int runId) {
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        if (!proxy.isServiceConnnected()) {
            logger.log(appContext.getString(R.string.cluster_test_automation_connecting));
            proxy.init(appContext);
            for (int attempt = 0; attempt < 20 && isLiveCaptureActive(runId)
                    && !proxy.isServiceConnnected(); attempt++) {
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.log(appContext.getString(R.string.cluster_test_automation_interrupted));
                    finishLiveCapture(runId);
                    return;
                }
            }
        }
        if (!proxy.isServiceConnnected()) {
            logger.log(appContext.getString(R.string.cluster_test_automation_no_connection));
            finishLiveCapture(runId);
            return;
        }

        logger.log(appContext.getString(R.string.cluster_test_live_header,
                timestamp(),
                VDEventCarInfo.MODULE_HVAC,
                VDEventCarInfo.MODULE_CAR_SETTING,
                VDEventCarInfo.MODULE_READONLY_INFO,
                ReadOnlyID.ID_SYSTEM_POWER_MODE,
                ReadOnlyID.ID_CAR_POWER_MODE,
                ReadOnlyID.ID_CAR_SPEED,
                HvacID.ID_AC_TEMPERATURE_CELSIUS_OUTSIDE,
                HvacID.ID_AC_TEMPERATURE_FAHRENHEIT_OUTSIDE));

        lastLoggedLivePower = Integer.MIN_VALUE;
        lastLoggedLiveCarPower = Integer.MIN_VALUE;
        lastLoggedLiveTemp = Integer.MIN_VALUE;
        lastLiveFeedbackAt = 0;
        int sample = 0;
        while (isLiveCaptureActive(runId) && sample < 120) {
            sample++;
            try {
                logLiveSample(proxy, sample);
            } catch (RuntimeException e) {
                logger.log(appContext.getString(R.string.cluster_test_automation_error,
                        e.getClass().getSimpleName(), String.valueOf(e.getMessage())));
            }
            if (sample < 120 && isLiveCaptureActive(runId)) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        finishLiveCapture(runId);
    }

    private void logLiveSample(CarInfoProxy proxy, int sample) {
        if (!proxy.isServiceConnnected()) {
            logger.log(appContext.getString(R.string.cluster_test_live_disconnected,
                    sample, timestamp()));
            return;
        }
        int[] celsius = proxy.getItemValues(VDEventCarInfo.MODULE_HVAC,
                HvacID.ID_AC_TEMPERATURE_CELSIUS_OUTSIDE);
        int[] fahrenheit = proxy.getItemValues(VDEventCarInfo.MODULE_HVAC,
                HvacID.ID_AC_TEMPERATURE_FAHRENHEIT_OUTSIDE);
        Integer systemPower = firstValue(proxy.getItemValues(VDEventCarInfo.MODULE_READONLY_INFO,
                ReadOnlyID.ID_SYSTEM_POWER_MODE));
        Integer carPower = firstValue(proxy.getItemValues(VDEventCarInfo.MODULE_READONLY_INFO,
                ReadOnlyID.ID_CAR_POWER_MODE));
        Integer speed = firstValue(proxy.getItemValues(VDEventCarInfo.MODULE_READONLY_INFO,
                ReadOnlyID.ID_CAR_SPEED));

        Integer rawTemp = firstValue(celsius);
        if (!same(rawTemp, lastLoggedLiveTemp)
                || !same(systemPower, lastLoggedLivePower)
                || !same(carPower, lastLoggedLiveCarPower)) {
            Double decoded = rawTemp == null ? null
                    : ClimateAutomationEngine.decodeOutsideCelsius(rawTemp);
            logger.log(appContext.getString(R.string.cluster_test_live_sample,
                    sample, timestamp(), formatValue(systemPower), formatValue(carPower),
                    formatValue(speed), formatValues(celsius), formatValues(fahrenheit),
                    decoded == null ? "n/a" : decimal(decoded)));
            lastLoggedLiveTemp = rawTemp == null ? Integer.MIN_VALUE : rawTemp;
            lastLoggedLivePower = systemPower == null ? Integer.MIN_VALUE : systemPower;
            lastLoggedLiveCarPower = carPower == null ? Integer.MIN_VALUE : carPower;
        }

        long now = System.currentTimeMillis();
        if (now - lastLiveFeedbackAt >= 5000) {
            logEnabledRuleFeedback(proxy);
            lastLiveFeedbackAt = now;
        }
    }

    private void logEnabledRuleFeedback(CarInfoProxy proxy) {
        SharedPreferences prefs = appContext.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
        boolean master = ClimateAutomationStore.isMasterEnabled(prefs);
        logger.log(appContext.getString(R.string.cluster_test_live_master,
                timestamp(), master));
        for (String id : ClimateAutomationStore.ALL_IDS) {
            ClimateAutomationStore.Rule rule = ClimateAutomationStore.getRule(prefs, id);
            logger.log(appContext.getString(R.string.cluster_test_live_rule,
                    id, rule.enabled, compareSymbol(rule.compare), rule.tempC,
                    rule.delayMinutes, rule.level));
            if (!master || !rule.enabled) {
                continue;
            }
            for (Signal signal : signalsFor(id)) {
                try {
                    int[] values = proxy.getItemValues(signal.module, signal.itemId);
                    logger.log(appContext.getString(R.string.cluster_test_automation_feedback,
                            id, signal.name, signal.module, signal.itemId, formatValues(values)));
                } catch (RuntimeException e) {
                    logger.log(appContext.getString(R.string.cluster_test_automation_feedback_error,
                            id, signal.name, signal.module, signal.itemId,
                            e.getClass().getSimpleName(), String.valueOf(e.getMessage())));
                }
            }
        }
    }

    private boolean isLiveCaptureActive(int runId) {
        return liveCaptureRunning && liveCaptureRunId == runId;
    }

    private synchronized void finishLiveCapture(int runId) {
        if (liveCaptureRunId != runId) {
            return;
        }
        liveCaptureRunning = false;
        logger.log(appContext.getString(R.string.cluster_test_live_done));
    }

    private boolean same(Integer value, int previous) {
        return value != null && value == previous;
    }

    private void collectSnapshot() {
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        if (!proxy.isServiceConnnected()) {
            logger.log(appContext.getString(R.string.cluster_test_automation_connecting));
            proxy.init(appContext);
            for (int attempt = 0; attempt < 20 && !proxy.isServiceConnnected(); attempt++) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.log(appContext.getString(R.string.cluster_test_automation_interrupted));
                    return;
                }
            }
        }

        boolean connected = proxy.isServiceConnnected();
        logger.log(appContext.getString(R.string.cluster_test_automation_header,
                timestamp(), connected,
                VDEventCarInfo.MODULE_HVAC, VDEventCarInfo.MODULE_CAR_SETTING,
                VDEventCarInfo.MODULE_READONLY_INFO));
        if (!connected) {
            logger.log(appContext.getString(R.string.cluster_test_automation_no_connection));
            return;
        }

        try {
            int[] rawCelsius = proxy.getItemValues(VDEventCarInfo.MODULE_HVAC,
                    HvacID.ID_AC_TEMPERATURE_CELSIUS_OUTSIDE);
            int[] rawFahrenheit = proxy.getItemValues(VDEventCarInfo.MODULE_HVAC,
                    HvacID.ID_AC_TEMPERATURE_FAHRENHEIT_OUTSIDE);
            Integer powerMode = firstValue(proxy.getItemValues(VDEventCarInfo.MODULE_READONLY_INFO,
                    ReadOnlyID.ID_SYSTEM_POWER_MODE));
            Integer carPowerMode = firstValue(proxy.getItemValues(VDEventCarInfo.MODULE_READONLY_INFO,
                    ReadOnlyID.ID_CAR_POWER_MODE));
            Integer speed = firstValue(proxy.getItemValues(VDEventCarInfo.MODULE_READONLY_INFO,
                    ReadOnlyID.ID_CAR_SPEED));
            logger.log(appContext.getString(R.string.cluster_test_automation_vehicle,
                    formatValues(rawCelsius),
                    formatValues(rawFahrenheit),
                    formatValue(powerMode),
                    formatValue(carPowerMode),
                    formatValue(speed)));

            SharedPreferences prefs = appContext.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
            boolean master = ClimateAutomationStore.isMasterEnabled(prefs);
            logger.log(appContext.getString(R.string.cluster_test_automation_master, master));
            Double temperature = rawCelsius == null || rawCelsius.length == 0
                    ? null : ClimateAutomationEngine.decodeOutsideCelsius(rawCelsius[0]);
            logger.log(appContext.getString(R.string.cluster_test_automation_decoded,
                    temperature == null ? "n/a" : decimal(temperature)));
            for (String id : ClimateAutomationStore.ALL_IDS) {
                logRuleAndFeedback(proxy, prefs, id, master, powerMode, temperature);
            }
        } catch (RuntimeException e) {
            logger.log(appContext.getString(R.string.cluster_test_automation_error,
                    e.getClass().getSimpleName(), String.valueOf(e.getMessage())));
        }
        logger.log(appContext.getString(R.string.cluster_test_automation_done));
    }

    private void logRuleAndFeedback(CarInfoProxy proxy, SharedPreferences prefs, String id,
                                    boolean master, Integer powerMode, Double temperature) {
        ClimateAutomationStore.Rule rule = ClimateAutomationStore.getRule(prefs, id);
        boolean matches = temperature != null
                && ClimateAutomationStore.matchesTemperature(temperature, rule.compare, rule.tempC);
        String expected = !master || !rule.enabled ? "OFF"
                : powerMode == null || powerMode < 1 ? "WAIT_POWER"
                : temperature == null ? "WAIT_TEMP"
                : matches ? "ON_CONDITION_MATCH" : "OFF_CONDITION_FALSE";
        logger.log(appContext.getString(R.string.cluster_test_automation_rule,
                id, rule.enabled, compareSymbol(rule.compare), rule.tempC,
                matches, rule.delayMinutes, rule.level, expected));

        for (Signal signal : signalsFor(id)) {
            try {
                int[] values = proxy.getItemValues(signal.module, signal.itemId);
                logger.log(appContext.getString(R.string.cluster_test_automation_feedback,
                        id, signal.name, signal.module, signal.itemId, formatValues(values)));
            } catch (RuntimeException e) {
                logger.log(appContext.getString(R.string.cluster_test_automation_feedback_error,
                        id, signal.name, signal.module, signal.itemId,
                        e.getClass().getSimpleName(), String.valueOf(e.getMessage())));
            }
        }
    }

    private Signal[] signalsFor(String id) {
        switch (id) {
            case ClimateAutomationStore.DRIVER_HEAT:
                return new Signal[]{setting("driver_seat_heat", CarSettingID.ID_CAR_DRIVER_SEAT_HEAT)};
            case ClimateAutomationStore.PASSENGER_HEAT:
                return new Signal[]{setting("passenger_seat_heat", CarSettingID.ID_CAR_COPILOT_SEAT_HEAT)};
            case ClimateAutomationStore.DRIVER_VENT:
                return new Signal[]{setting("driver_seat_vent", CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION)};
            case ClimateAutomationStore.PASSENGER_VENT:
                return new Signal[]{setting("passenger_seat_vent", CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION)};
            case ClimateAutomationStore.STEER_HEAT:
                return new Signal[]{setting("steering_heat", CarSettingID.ID_CAR_STEER_WHEEL_HEAT)};
            case ClimateAutomationStore.WINDSHIELD:
                return new Signal[]{hvac("front_glass_heat", HvacID.ID_AC_FRONT_GLASS_HEAT)};
            case ClimateAutomationStore.REAR_DEFROST:
                return new Signal[]{
                        hvac("rear_defrost", HvacID.ID_AC_REAR_DEFROST),
                        setting("rear_window_heat", CarSettingID.ID_CAR_REAR_WINDOW_HEAT)
                };
            case ClimateAutomationStore.CLIMATE:
                return new Signal[]{
                        hvac("ac_state", HvacID.ID_AC_STATE),
                        hvac("ac_working", HvacID.ID_AC_WORKING),
                        hvac("ac_auto_state", HvacID.ID_AC_AUTO_STATE)
                };
            default:
                return new Signal[0];
        }
    }

    private Signal setting(String name, int id) {
        return new Signal(name, VDEventCarInfo.MODULE_CAR_SETTING, id);
    }

    private Signal hvac(String name, int id) {
        return new Signal(name, VDEventCarInfo.MODULE_HVAC, id);
    }

    private Integer firstValue(int[] values) {
        return values == null || values.length == 0 ? null : values[0];
    }

    private String formatValues(int[] values) {
        if (values == null || values.length == 0) {
            return "no-response";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                result.append(',');
            }
            result.append(values[i]);
        }
        return result.toString();
    }

    private String formatValue(Integer value) {
        return value == null ? "no-response" : String.valueOf(value);
    }

    private String compareSymbol(int compare) {
        switch (compare) {
            case ClimateAutomationStore.CMP_LTE:
                return "<=";
            case ClimateAutomationStore.CMP_GT:
                return ">";
            case ClimateAutomationStore.CMP_GTE:
                return ">=";
            default:
                return "<";
        }
    }

    private String decimal(double value) {
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                .format(new Date());
    }

    private static final class Signal {
        final String name;
        final int module;
        final int itemId;

        Signal(String name, int module, int itemId) {
            this.name = name;
            this.module = module;
            this.itemId = itemId;
        }
    }
}
