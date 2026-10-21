package com.mapcontrol.vehicle;

import android.content.Context;

import com.mapcontrol.R;
import com.desaysv.ivi.extra.project.carinfo.HvacID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class OutsideTemperatureVdbusTest {
    public interface Logger {
        void log(String line);
    }

    private static final int SAMPLE_COUNT = 10;
    private final Context appContext;
    private final Logger logger;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running;

    public OutsideTemperatureVdbusTest(Context context, Logger logger) {
        this.appContext = context.getApplicationContext();
        this.logger = logger;
    }

    public synchronized boolean start() {
        if (running) {
            return false;
        }
        running = true;
        executor.execute(this::collectSamples);
        return true;
    }

    public synchronized void stop() {
        running = false;
        executor.shutdownNow();
    }

    private void collectSamples() {
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        if (!proxy.isServiceConnnected()) {
            logger.log(appContext.getString(R.string.cluster_test_temperature_connecting));
            proxy.init(appContext);
            for (int attempt = 0; attempt < 20 && running && !proxy.isServiceConnnected(); attempt++) {
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                    break;
                }
            }
        }

        logger.log(appContext.getString(R.string.cluster_test_temperature_header,
                VDEventCarInfo.MODULE_HVAC,
                HvacID.ID_AC_TEMPERATURE_CELSIUS_OUTSIDE,
                HvacID.ID_AC_TEMPERATURE_FAHRENHEIT_OUTSIDE));

        for (int sample = 1; sample <= SAMPLE_COUNT && running; sample++) {
            try {
                boolean connected = proxy.isServiceConnnected();
                int[] celsius = connected
                        ? proxy.getItemValues(VDEventCarInfo.MODULE_HVAC,
                                HvacID.ID_AC_TEMPERATURE_CELSIUS_OUTSIDE)
                        : new int[0];
                int[] fahrenheit = connected
                        ? proxy.getItemValues(VDEventCarInfo.MODULE_HVAC,
                                HvacID.ID_AC_TEMPERATURE_FAHRENHEIT_OUTSIDE)
                        : new int[0];
                logger.log(appContext.getString(R.string.cluster_test_temperature_sample,
                        sample,
                        timestamp(),
                        connected,
                        formatCelsius(celsius),
                        formatFahrenheit(fahrenheit)));
            } catch (RuntimeException e) {
                logger.log(appContext.getString(R.string.cluster_test_temperature_error,
                        sample, e.getClass().getSimpleName(), String.valueOf(e.getMessage())));
            }
            if (sample < SAMPLE_COUNT && running) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        running = false;
        logger.log(appContext.getString(R.string.cluster_test_temperature_done));
    }

    private String formatCelsius(int[] values) {
        if (values == null || values.length == 0) {
            return appContext.getString(R.string.cluster_test_temperature_no_value);
        }
        StringBuilder result = new StringBuilder("rawArray=");
        result.append(java.util.Arrays.toString(values));
        for (int raw : values) {
            result.append(" | raw=").append(raw)
                    .append(", rawHalfStep=(raw-110)/2=")
                    .append(decimal((raw - 110) / 2.0))
                    .append("°C")
                    .append(", raw/10=").append(decimal(raw / 10.0))
                    .append("°C, raw-direct=").append(raw)
                    .append("°C, raw-40=").append(raw - 40)
                    .append("°C, currentAppDecode=")
                    .append(decimal(ClimateAutomationEngine.decodeOutsideCelsius(raw)))
                    .append("°C");
        }
        return result.toString();
    }

    private String formatFahrenheit(int[] values) {
        if (values == null || values.length == 0) {
            return appContext.getString(R.string.cluster_test_temperature_no_value);
        }
        StringBuilder result = new StringBuilder("rawArray=");
        result.append(java.util.Arrays.toString(values));
        for (int raw : values) {
            double asWholeF = (raw - 32.0) * 5.0 / 9.0;
            double asTenthsF = (raw / 10.0 - 32.0) * 5.0 / 9.0;
            result.append(" | raw=").append(raw)
                    .append(", °C-if-whole-°F=").append(decimal(asWholeF))
                    .append(", °C-if-0.1°F=").append(decimal(asTenthsF));
        }
        return result.toString();
    }

    private String decimal(Double value) {
        return value == null ? "n/a" : decimal(value.doubleValue());
    }

    private String decimal(double value) {
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                .format(new Date());
    }
}
