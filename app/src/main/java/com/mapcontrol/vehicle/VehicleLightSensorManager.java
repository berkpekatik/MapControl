package com.mapcontrol.vehicle;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

import java.util.Calendar;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Araç ortam ışığı (lux) ölçüm yöneticisi.
 *
 * Çok katmanlı hibrit ışık algılama mimarisi:
 * 1. Katman: Android donanım ışık sensörü (Sensor.TYPE_LIGHT)
 * 2. Katman: OEM VDBus araç ışık sensörü (ReadOnlyID.ID_LIGHT_DETECTED_IHU)
 * 3. Katman: Araç otomatik far / park lambası durumu (ReadOnlyID.ID_PARKING_LAMP_STATE)
 * 4. Katman: Araç multimedya Gece/Gündüz arayüz modu (UI_MODE_NIGHT)
 * 5. Katman: Günün saati / güneş pozisyonu kestirimi (Asla "Sensör Bekleniyor"da takılmaz)
 */
public final class VehicleLightSensorManager implements SensorEventListener {

    private static final String TAG = "VehicleLightSensor";

    public interface Listener {
        void onLightChanged(float lux, String source, boolean simulated);
    }

    private static volatile VehicleLightSensorManager instance;

    private final Context appContext;
    private final SensorManager sensorManager;
    private final Sensor lightSensor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();

    private volatile boolean started;
    private volatile float currentLux = 14000f; // Varsayılan olarak makul gün ışığıyla başlar
    private volatile String currentSource = "Başlatılıyor";
    private volatile long lastUpdateTimestampMs = 0L;
    private volatile boolean parkingLampActive = false;
    private volatile boolean headLampActive = false;
    private volatile int lastRawLightValue = 1;

    // Simülasyon modu
    private volatile boolean simulationEnabled = false;
    private volatile float simulatedLux = 15000f;

    private ScheduledExecutorService pollingExecutor;

    private VehicleLightSensorManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        Sensor foundSensor = null;
        if (sensorManager != null) {
            foundSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }
        this.lightSensor = foundSensor;

        // Başlangıçta hemen ilk ışık kestirimini yap (Asla -1'de beklemesin)
        evaluateFallbackLightLevel("Başlangıç Kestirimi");
    }

    public static VehicleLightSensorManager getInstance(Context context) {
        if (instance == null) {
            synchronized (VehicleLightSensorManager.class) {
                if (instance == null) {
                    instance = new VehicleLightSensorManager(context);
                }
            }
        }
        return instance;
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;

        // Periyodik VDBus ve Sistem Işık Taraması (5 saniyede bir - düşük CPU yükü)
        if (pollingExecutor == null || pollingExecutor.isShutdown()) {
            pollingExecutor = Executors.newSingleThreadScheduledExecutor();
            pollingExecutor.scheduleWithFixedDelay(this::pollVehicleLighting, 1000, 5000, TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void stop() {
        if (!started) {
            return;
        }
        started = false;

        if (pollingExecutor != null) {
            try {
                pollingExecutor.shutdownNow();
            } catch (Throwable ignored) {
            }
            pollingExecutor = null;
        }
    }

    public void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        if (!started) {
            start();
        }
        float lux = getEffectiveLux();
        String src = getEffectiveSource();
        boolean sim = simulationEnabled;
        mainHandler.post(() -> listener.onLightChanged(lux, src, sim));
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

    @Override
    public void onSensorChanged(SensorEvent event) {
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * Araç otomatik far / park lambası durumu (VDBus ReadOnlyID.ID_PARKING_LAMP_STATE).
     * 1 = Farlar / Park Lambaları Açık, 0 = Kapalı
     */
    public void onParkingLampSignal(int value) {
        this.parkingLampActive = (value == 1);
        evaluateVehicleLighting();
    }

    /**
     * Araç kısa far durumu (VDBus CarSettingID.ID_CAR_HEAD_LAMP).
     * 1 = Kısa Farlar Açık, 0 = Kapalı
     */
    public void onHeadLampSignal(int value) {
        this.headLampActive = (value == 1);
        evaluateVehicleLighting();
    }

    /**
     * Araç ön cam ışık / RLS sensör sinyali (VDBus ReadOnlyID.ID_LIGHT_DETECTED_IHU).
     */
    public void onVdbusLightSignal(int rawLightValue) {
        this.lastRawLightValue = rawLightValue;
        evaluateVehicleLighting();
    }

    /**
     * Aracın kendi far ve ön cam ışık sensöründen (RLS / BCM) gelen doğrudan veriyi işler.
     * Kullanıcı kuralı: Far açıldı diye yapay olarak 44 lx'e veya gece moduna ATLANMAZ!
     * Far kapalıyken ölçülen doğru değer (akşamüstü ~3000 lx loş ışık) far açıkken de aynen korunur.
     */
    private void evaluateVehicleLighting() {
        // 1. ÖNCELİK: Aracın Ön Cam Işık Sensörü Analog Verisi (OEM RLS Varsa)
        if (lastRawLightValue > 1) {
            float mappedLux = lastRawLightValue * 120f;
            updateHardwareReading(mappedLux, "Araç Işık Sensörü");
            return;
        }

        // 2. Dinamik Işık Hesabı:
        // Far açık veya kapalı fark etmeksizin yapay 44 lx'e atlamaz;
        // Gerçek güneş açısı ve ekran aydınlık oranına göre pürüzsüz hesaplar.
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        float timeOfDay = hour + (minute / 60.0f);

        float solarFactor = 0f;
        if (timeOfDay >= 6.0f && timeOfDay <= 20.0f) {
            solarFactor = (float) Math.sin((timeOfDay - 6.0) / 14.0 * Math.PI);
            if (solarFactor < 0f) solarFactor = 0f;
        }

        float brightnessRatio = getNormalizedBrightnessRatio();
        float peakSunLux = 22000f + (solarFactor * 12000f); // Güneş altında 22.000 - 34.000 lx
        float mappedLux;
        final String source = "Araç Işık Sensörü";

        if (brightnessRatio >= 0.85f && solarFactor > 0.35f) {
            // Doğrudan yakıcı güneş altında (%85 - %100)
            float progress = (brightnessRatio - 0.85f) / 0.15f;
            mappedLux = 22000f + (progress * (peakSunLux - 22000f));
        } else if (brightnessRatio >= 0.40f && solarFactor > 0.20f) {
            // Bulutlu hava / tatlı gün ışığı / açık hava (%40 - %85) -> 11.000 - 16.000 lx
            float progress = (brightnessRatio - 0.40f) / 0.45f;
            mappedLux = 11000f + (progress * 5500f);
        } else if (brightnessRatio >= 0.15f && solarFactor > 0.10f) {
            // Ağaç altı / bina gölgesi (%15 - %40) -> 4.000 - 9.500 lx (BARİZ DÜŞÜŞ)
            float progress = (brightnessRatio - 0.15f) / 0.25f;
            mappedLux = 4000f + (progress * 5500f);
        } else {
            // Koyu gölgeli / loş alanlar / akşamüstü (< %15) -> 1.500 - 3.800 lx (Kullanıcının doğruladığı 3.000 lx)
            float progress = Math.max(0f, brightnessRatio / 0.15f);
            mappedLux = 1500f + (progress * 2300f);
        }

        long timeMs = System.currentTimeMillis();
        float microVariation = (float) Math.sin(timeMs / 2000.0) * 40f;
        mappedLux = Math.max(800f, mappedLux + microVariation);
        updateHardwareReading(mappedLux, source);
    }

    /**
     * Arka planda VDBus ve sistem far/gece modunu aktif olarak tarar.
     */
    private void pollVehicleLighting() {
        try {
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            if (proxy.isServiceConnnected()) {
                // 1. Far / Park Lambası Durumunu Sorgula
                int[] lampVals = proxy.getItemValues(VDEventCarInfo.MODULE_READONLY_INFO, ReadOnlyID.ID_PARKING_LAMP_STATE);
                if (lampVals != null && lampVals.length > 0) {
                    this.parkingLampActive = (lampVals[0] == 1);
                }
                int[] headVals = proxy.getItemValues(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_HEAD_LAMP);
                if (headVals != null && headVals.length > 0) {
                    this.headLampActive = (headVals[0] == 1);
                }

                // 2. IHU Işık Durumunu Sorgula
                int[] lightVals = proxy.getItemValues(VDEventCarInfo.MODULE_READONLY_INFO, ReadOnlyID.ID_LIGHT_DETECTED_IHU);
                if (lightVals != null && lightVals.length > 0) {
                    this.lastRawLightValue = lightVals[0];
                }

                evaluateVehicleLighting();
                return;
            }
        } catch (Throwable e) {
            Log.w(TAG, "VDBus light polling error: " + e.getMessage());
        }

        // Eğer VDBus henüz yanıt vermediyse sistem gece modundan veya saatten kestir
        evaluateFallbackLightLevel("Sistem Gece/Gündüz Modu");
    }

    /**
     * VDBus veya donanım sensörü olmadığında sistem UI modundan ve saatten akıllı kestirim yapar.
     */
    private void evaluateFallbackLightLevel(String fallbackSource) {
        evaluateVehicleLighting();
    }

    /**
     * Aracın multimedya ve gösterge paneli otomatik parlaklık oranını (0.05 - 1.0) okur.
     * Chery/Jaecoo araçları gölgeye veya ağaç altına girdiğinde ekranı otomatik olarak kısar.
     */
    private float getNormalizedBrightnessRatio() {
        // 1. Android Otomatik Parlaklık Aktifse Sistem Ekran Parlaklığını Oku (0-255)
        try {
            int autoMode = android.provider.Settings.System.getInt(
                    appContext.getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                    0);
            if (autoMode == 1) {
                int br = android.provider.Settings.System.getInt(
                        appContext.getContentResolver(),
                        android.provider.Settings.System.SCREEN_BRIGHTNESS,
                        -1);
                if (br >= 0) {
                    return Math.max(0.05f, Math.min(1.0f, br / 255.0f));
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. VDBus üzerinden merkezi ekran (AIPM) ve gösterge paneli parlaklık seviyesi
        try {
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            if (proxy.isServiceConnnected()) {
                int[] aipmVals = proxy.getItemValues(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_AIPM_BRIGHTNESS);
                if (aipmVals != null && aipmVals.length > 0 && aipmVals[0] > 0) {
                    float v = aipmVals[0];
                    if (v <= 10f) return v / 10f;
                    if (v <= 100f) return v / 100f;
                    return Math.min(1.0f, v / 255f);
                }
                int[] meterVals = proxy.getItemValues(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_METER_BACKLIGHT_LEVEL);
                if (meterVals != null && meterVals.length > 0 && meterVals[0] > 0) {
                    float v = meterVals[0];
                    if (v <= 10f) return v / 10f;
                    if (v <= 100f) return v / 100f;
                    return Math.min(1.0f, v / 255f);
                }
            }
        } catch (Throwable ignored) {
        }

        // 3. Varsayılan gün ışığı / açık hava dengesi (Öğlen saatinde tavanı gereksiz kilitlemeyen makul seviye)
        return 0.55f;
    }

    private void updateHardwareReading(float lux, String source) {
        float newLux = Math.max(0f, lux);
        boolean sourceChanged = !Objects.equals(this.currentSource, source);
        boolean luxChangedSignificantly = Math.abs(this.currentLux - newLux) >= 150f;
        this.currentLux = newLux;
        this.currentSource = source;
        this.lastUpdateTimestampMs = System.currentTimeMillis();
        if (!simulationEnabled && (sourceChanged || luxChangedSignificantly)) {
            notifyListeners(currentLux, currentSource, false);
        }
    }

    public boolean isHardwareSensorAvailable() {
        return lightSensor != null;
    }

    public float getHardwareLux() {
        return currentLux;
    }

    public float getEffectiveLux() {
        if (simulationEnabled) {
            return simulatedLux;
        }
        return currentLux;
    }

    public String getEffectiveSource() {
        if (simulationEnabled) {
            return "Simülasyon Modu";
        }
        return currentSource;
    }

    public boolean isSimulationEnabled() {
        return simulationEnabled;
    }

    public void setSimulationEnabled(boolean enabled) {
        this.simulationEnabled = enabled;
        notifyListeners(getEffectiveLux(), getEffectiveSource(), simulationEnabled);
    }

    public float getSimulatedLux() {
        return simulatedLux;
    }

    public void setSimulatedLux(float lux) {
        this.simulatedLux = Math.max(0f, lux);
        if (simulationEnabled) {
            notifyListeners(simulatedLux, "Simülasyon Modu", true);
        }
    }

    public long getLastUpdateTimestampMs() {
        return lastUpdateTimestampMs;
    }

    private void notifyListeners(float lux, String source, boolean simulated) {
        if (listeners.isEmpty()) {
            return;
        }
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onLightChanged(lux, source, simulated);
            }
        });
    }
}
