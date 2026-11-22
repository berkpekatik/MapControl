package com.mapcontrol.vehicle;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.mapcontrol.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Araç ışık (Lux) sensörü, yağmur durumu ve sürüş hızına göre cam tavanı
 * otomatik açıp kapatan akıllı otomasyon motoru.
 *
 * Kullanıcı dostu akıllı mod:
 * - Hava kavurucu güneşliyse güneşten korunmak için cam tavanı kapatır.
 * - Hava bulutluysa, gün batımına yakınsa veya tatlı/gözü yormayan aydınlıktaysa açar.
 * - Gece, tünel veya karanlıkta kapatır.
 */
public final class SunroofAutomationEngine implements VehicleLightSensorManager.Listener, VehicleMetricsRepository.Listener {

    public interface Listener {
        void onSunroofStatusChanged(Status status);
    }

    // Çalışma Modları (Kullanıcı Dostu)
    public static final int MODE_SMART_COMFORT = 0;   // Akıllı Konfor (Bulutlu/Gün Batımında Aç, Dik Güneşte & Gece Kapat) - VARSAYILAN
    public static final int MODE_SHADE_ONLY = 1;      // Sadece Gölgede Aç (Hassas Güneş Koruması)
    public static final int MODE_OPEN_AIR = 2;        // Açık Hava (Gece Hariç Çoğu Zaman Açık)
    public static final int MODE_ALWAYS_OPEN = 3;     // Daima Açık (Işıktan Bağımsız Her Sürüşte Aç)

    // Otomasyon Kapsamı / Hedef Eleman
    public static final int TARGET_SHADE_ONLY = 0;    // Yalnızca Perde
    public static final int TARGET_SUNROOF_AND_SHADE = 1; // Tavan + Perde
    public static final String KEY_TARGET_SCOPE = "sunroof_auto_target_scope";
    public static final String KEY_TILT_ONLY = "sunroof_auto_tilt_only";

    // Güneş Hassasiyeti Seviyeleri (Kullanıcı sayısal lux ile uğraşmasın diye)
    public static final int SENSITIVITY_SENSITIVE = 0; // Hassas (Güneşte Erken Kapatır)
    public static final int SENSITIVITY_BALANCED = 1;  // Dengeli (Önerilen)
    public static final int SENSITIVITY_BROAD = 2;     // Geniş (Geç Kapatır)

    public static final String PREFS_NAME = "sunroof_automation_prefs";
    public static final String KEY_ENABLED = "sunroof_auto_enabled";
    public static final String KEY_MODE = "sunroof_auto_mode";
    public static final String KEY_SENSITIVITY = "sunroof_auto_sensitivity";
    public static final String KEY_DEBOUNCE_SEC = "sunroof_auto_debounce_sec";
    public static final String KEY_COOLDOWN_SEC = "sunroof_auto_cooldown_sec";
    public static final String KEY_CONTROL_SHADE = "sunroof_auto_control_shade";
    public static final String KEY_RAIN_GUARD = "sunroof_auto_rain_guard";
    public static final String KEY_SPEED_GUARD = "sunroof_auto_speed_guard";
    public static final String KEY_SPEED_LIMIT = "sunroof_auto_speed_limit";
    public static final String KEY_CLOSE_ON_IGNITION_OFF = "sunroof_auto_close_on_ignition_off";
    public static final String KEY_CLOSE_AT_NIGHT = "sunroof_auto_close_at_night";
    public static final String KEY_SIMULATED_RAIN = "sunroof_auto_simulated_rain";

    private static final String TAG = "SunroofAutoEngine";
    private static final String ACTION_LOG = "com.mapcontrol.LOG_MESSAGE";
    private static final String EXTRA_LOG_MESSAGE = "log_message";

    public static final class Status {
        public final boolean connected;
        public final boolean masterEnabled;
        public final int mode;
        public final int sensitivity;
        public final float currentLux;
        public final float averageLux;
        public final String luxSource;
        public final boolean isSimulated;
        public final String conditionTitle;      // Örn: "Kavurucu Güneş", "Tatlı Gün Işığı", "Bulutlu", "Gece"
        public final String conditionAdvice;     // Örn: "Güneşten koruma amacıyla tavan kapalı tutuluyor"
        public final Boolean sunroofOpen;       // true=Açık, false=Kapalı, null=Hareketli/Bilinmiyor
        public final boolean sunshadeOpen;
        public final boolean isRaining;
        public final int vehicleSpeed;
        public final int powerMode;
        public final String lastAction;
        public final long lastActionTimeMs;
        public final int cooldownRemainingSec;
        public final String pendingAction;

        public Status(boolean connected, boolean masterEnabled, int mode, int sensitivity,
                      float currentLux, float averageLux, String luxSource, boolean isSimulated,
                      String conditionTitle, String conditionAdvice, Boolean sunroofOpen,
                      boolean sunshadeOpen, boolean isRaining, int vehicleSpeed,
                      int powerMode, String lastAction, long lastActionTimeMs,
                      int cooldownRemainingSec, String pendingAction) {
            this.connected = connected;
            this.masterEnabled = masterEnabled;
            this.mode = mode;
            this.sensitivity = sensitivity;
            this.currentLux = currentLux;
            this.averageLux = averageLux;
            this.luxSource = luxSource;
            this.isSimulated = isSimulated;
            this.conditionTitle = conditionTitle;
            this.conditionAdvice = conditionAdvice;
            this.sunroofOpen = sunroofOpen;
            this.sunshadeOpen = sunshadeOpen;
            this.isRaining = isRaining;
            this.vehicleSpeed = vehicleSpeed;
            this.powerMode = powerMode;
            this.lastAction = lastAction;
            this.lastActionTimeMs = lastActionTimeMs;
            this.cooldownRemainingSec = cooldownRemainingSec;
            this.pendingAction = pendingAction;
        }
    }

    private static volatile SunroofAutomationEngine instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollFuture;

    private volatile boolean started;
    private volatile long lastMovementTimestamp = 0L;
    private volatile long conditionMetSinceTimestamp = 0L;
    private volatile Boolean pendingTarget = null;
    private volatile String lastActionDescription;
    private volatile long lastActionTimestamp = 0L;
    private volatile boolean manualClosedByDriver = false;
    private volatile Boolean lastKnownSunroofOpen = null;
    private volatile int previousPowerMode = -1;
    private volatile long lastEvaluationTimestamp = 0L;
    private volatile boolean lastKnownRaining = false;
    private volatile long lastStatusNotifyTimestamp = 0L;
    private volatile boolean initialStartupPending = true; // İlk çalıştırmada güneş yoksa hız/gecikme beklemeksizin açmak için flag
    private static final long RAIN_HOLD_DURATION_MS = 25_000L; // Silecek duraksamalarında yağmurun kesilmemesi için 25 sn koruma süresi
    private volatile long lastRainDetectedTimestampMs = 0L;
    private volatile boolean rainCloseTriggered = false;

    // Son 1-2 dakikalık ışık ortalaması (Rolling Average Lux)
    // Kırmızı ışık ve köprü altı gibi geçici gölgelerde anlık düşüşlerin tavanı gereksiz açmasını engeller
    private final java.util.LinkedList<Float> luxHistory = new java.util.LinkedList<>();
    private static final int MAX_LUX_HISTORY = 40; // ~2 dakikalık periyodik örneklem (3sn * 40)
    private volatile float rollingAverageLux = -1f;

    private void updateRollingLux(float lux) {
        if (lux <= 0) return;
        synchronized (luxHistory) {
            luxHistory.add(lux);
            if (luxHistory.size() > MAX_LUX_HISTORY) {
                luxHistory.removeFirst();
            }
            float sum = 0f;
            for (float v : luxHistory) {
                sum += v;
            }
            rollingAverageLux = sum / luxHistory.size();
        }
    }

    private SunroofAutomationEngine(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Kullanıcı isteği doğrultusunda motor koruma süresi 10 saniyeye ayarlandı
        if (prefs.getInt(KEY_COOLDOWN_SEC, 10) != 10) {
            prefs.edit().putInt(KEY_COOLDOWN_SEC, 10).apply();
        }
    }

    public static SunroofAutomationEngine getInstance(Context context) {
        if (instance == null) {
            synchronized (SunroofAutomationEngine.class) {
                if (instance == null) {
                    instance = new SunroofAutomationEngine(context);
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
        scheduler = Executors.newSingleThreadScheduledExecutor();

        VehicleLightSensorManager.getInstance(appContext).addListener(this);
        VehicleMetricsRepository.getInstance(appContext).addListener(this);

        pollFuture = scheduler.scheduleWithFixedDelay(this::evaluateSafetyAndAutomation, 2, 3, TimeUnit.SECONDS);
        logEvent("Cam Tavan Akıllı Otomasyonu başlatıldı");
    }

    public synchronized void stop() {
        if (!started) {
            return;
        }
        started = false;
        VehicleLightSensorManager.getInstance(appContext).removeListener(this);
        VehicleMetricsRepository.getInstance(appContext).removeListener(this);

        if (pollFuture != null) {
            pollFuture.cancel(true);
            pollFuture = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
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
        Status current = buildStatus();
        mainHandler.post(() -> listener.onSunroofStatusChanged(current));
    }

    public void removeListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.remove(listener);
    }

    @Override
    public void onLightChanged(float lux, String source, boolean simulated) {
        updateRollingLux(lux);
        if (!started || !prefs.getBoolean(KEY_ENABLED, false)) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastEvaluationTimestamp >= 2000L) {
            triggerEvaluationAsync();
        }
    }

    private void triggerEvaluationAsync() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.execute(this::evaluateSafetyAndAutomation);
        }
    }

    @Override
    public void onMetricsUpdated(VehicleMetricsSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        Boolean currentOpen = snapshot.sunroofOpenOrNull();
        if (currentOpen != null && lastKnownSunroofOpen != null && !currentOpen.equals(lastKnownSunroofOpen)) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastMovementTimestamp > 5000L) {
                if (Boolean.FALSE.equals(currentOpen)) {
                    manualClosedByDriver = true;
                    logEvent("Sürücü cam tavanı manuel kapattı — Otomatik açılma bu sürüş boyunca kilitlendi.");
                } else if (Boolean.TRUE.equals(currentOpen)) {
                    manualClosedByDriver = false;
                    logEvent("Sürücü cam tavanı manuel açtı — Sürüş kilidi kaldırıldı.");
                }
            }
        }
        boolean sunroofChanged = (currentOpen != null && !currentOpen.equals(lastKnownSunroofOpen));
        lastKnownSunroofOpen = currentOpen;

        int currentPower = snapshot.powerMode;
        boolean powerChanged = (previousPowerMode != currentPower);
        if (previousPowerMode >= 2 && currentPower < 2) {
            initialStartupPending = true; // Tam kontak kapandı, sonraki tam kontak için hazırla
            manualClosedByDriver = false; // Yeni sürüş için kilidi sıfırla
        } else if (previousPowerMode < 2 && currentPower >= 2) {
            initialStartupPending = true; // Tam kontak açıldı, ilk çalıştırma aktif
            manualClosedByDriver = false;
        }
        previousPowerMode = currentPower;

        boolean isRaining = snapshot.isRaining();
        if (isRaining) {
            lastRainDetectedTimestampMs = SystemClock.elapsedRealtime();
        }
        boolean rainChanged = (isRaining != lastKnownRaining);
        lastKnownRaining = isRaining;

        // CAN-Bus paketleri saniyede 50-100 kez gelir (RPM, hız, voltaj vb.).
        // Her CAN mesajında değerlendirme yapmak ana döngüyü kilitler.
        // Sadece güç, yağmur veya tavan durumu değiştiğinde ya da en az 2 saniye geçmişse tetikle:
        long now = SystemClock.elapsedRealtime();
        if (powerChanged || rainChanged || sunroofChanged || (now - lastEvaluationTimestamp >= 2000L)) {
            triggerEvaluationAsync();
        }
    }

    /**
     * Güneş hassasiyetine göre üst (yakıcı güneş) eşiğini belirler.
     */
    public static float getSunThreshold(int sensitivity) {
        switch (sensitivity) {
            case SENSITIVITY_SENSITIVE:
                return 14000f; // 14.000 lx üzeri parlak güneşte erken kapat
            case SENSITIVITY_BROAD:
                return 22000f; // 22.000 lx üzeri aşırı yakıcı güneşte kapat
            case SENSITIVITY_BALANCED:
            default:
                return 17000f; // 17.000 lx (bulutlu 12.800 lx'de açık tutar; dik öğle güneşinde kapatır)
        }
    }

    /**
     * Güneş hassasiyetine göre alt (gece/karanlık/sokak lambalı) eşiğini belirler.
     * Kullanıcı ölçümü: Akşam 20:30'da sokak lambalarıyla tam karanlıkta ~7.600 lx okunmaktadır.
     */
    public static float getNightThreshold(int sensitivity) {
        switch (sensitivity) {
            case SENSITIVITY_SENSITIVE:
                return 8800f; // Hassas: 8.800 lx altı (alacakaranlıkta daha erken gece moduna geçer)
            case SENSITIVITY_BROAD:
                return 7800f; // Geniş: 7.800 lx altı
            case SENSITIVITY_BALANCED:
            default:
                return 8300f; // Dengeli: 8.300 lx altı (Gece karanlığı ve sokak lambaları aralığı)
        }
    }

    /**
     * Otomasyon karar ve güvenlik mantığı.
     */
    private synchronized void evaluateSafetyAndAutomation() {
        if (!started) {
            return;
        }
        lastEvaluationTimestamp = SystemClock.elapsedRealtime();

        boolean enabled = prefs.getBoolean(KEY_ENABLED, false);
        VehicleLightSensorManager lightManager = VehicleLightSensorManager.getInstance(appContext);
        VehicleMetricsSnapshot metrics = VehicleMetricsRepository.getInstance(appContext).getSnapshot();
        VehicleSunroofController controller = VehicleSunroofController.getInstance(appContext);

        float currentLux = lightManager.getEffectiveLux();
        long now = SystemClock.elapsedRealtime();

        // 1. Sürücü Manuel Kapattıysa Sürüş Boyunca Kilitle (Açılması engellenir)
        if (manualClosedByDriver) {
            pendingTarget = null;
            conditionMetSinceTimestamp = 0L;
            notifyStatusChanged(appContext.getString(R.string.sunroof_engine_driver_locked));
            return;
        }

        // 2. Yağmur Emniyeti (Tavanı derhal otomatik kapatır; silecek hareketleri arasında sıfırlanmaz)
        boolean rainGuard = prefs.getBoolean(KEY_RAIN_GUARD, true);
        boolean rainDetectedNow = metrics.isRaining() || prefs.getBoolean(KEY_SIMULATED_RAIN, false);
        if (rainDetectedNow) {
            lastRainDetectedTimestampMs = now;
        }
        boolean isRaining = (now - lastRainDetectedTimestampMs < RAIN_HOLD_DURATION_MS);

        if (rainGuard && isRaining) {
            pendingTarget = null;
            conditionMetSinceTimestamp = 0L;
            if (!rainCloseTriggered) {
                logEvent("⚠️ YAĞMUR ALGILANDI! Cam tavan güvenlik amacıyla derhal kapatılıyor.");
                controller.closeSunroof(false);
                rainCloseTriggered = true;
                lastMovementTimestamp = now;
                lastActionDescription = appContext.getString(R.string.sunroof_engine_rain_closed);
                lastActionTimestamp = System.currentTimeMillis();
            }
            notifyStatusChanged();
            return;
        } else if (!isRaining) {
            rainCloseTriggered = false;
        }

        // 3. Kontak / Güç Modu Kontrolü (Yarım kontak PowerMode=1 iken açma yapmaz, tam kontak PowerMode>=2 bekler)
        if (metrics.powerMode < 2) {
            pendingTarget = null;
            conditionMetSinceTimestamp = 0L;
            notifyStatusChanged();
            return;
        }

        // 4. Ana Açma/Kapatma Anahtarı Kontrolü
        if (!enabled) {
            pendingTarget = null;
            conditionMetSinceTimestamp = 0L;
            notifyStatusChanged();
            return;
        }

        // 5. Işık ve Mod Kararı
        int mode = prefs.getInt(KEY_MODE, MODE_SMART_COMFORT);
        int sensitivity = prefs.getInt(KEY_SENSITIVITY, SENSITIVITY_BALANCED);
        float sunThreshold = getSunThreshold(sensitivity);
        float nightThreshold = getNightThreshold(sensitivity);
        boolean closeAtNight = prefs.getBoolean(KEY_CLOSE_AT_NIGHT, false);

        // Son 1-2 dakikalık hareketli ışık ortalaması (Köprü altı / kırmızı ışık koruması)
        updateRollingLux(currentLux);
        float avgLux = (rollingAverageLux > 0) ? rollingAverageLux : currentLux;

        Boolean targetAction = null; // null: deadband (mevcut durumu koru), TRUE: Aç, FALSE: Kapat

        if (mode == MODE_SMART_COMFORT) {
            // Kullanıcı kuralı: SADECE GÜNEŞTE KAPANIR.
            // Kapatma: Anlık veya ortalama yakıcı güneş varsa derhal kapat (Acil koruma!)
            if (currentLux >= sunThreshold || avgLux >= sunThreshold) {
                targetAction = Boolean.FALSE; // Yakıcı güneşten koru -> Kapat!
            } else if (closeAtNight && (currentLux <= nightThreshold && avgLux <= nightThreshold)) {
                targetAction = Boolean.FALSE; // İsteğe bağlı gece modu -> Kapat
            } else if (currentLux <= (sunThreshold - 2000f) && avgLux <= (sunThreshold - 2000f)) {
                // Açma: Hem anlık hem de son dakikaların ortalaması güvenliyse -> Aç!
                targetAction = Boolean.TRUE;
            }
        } else if (mode == MODE_SHADE_ONLY) {
            float shadeLimit = sunThreshold * 0.65f;
            if (currentLux >= shadeLimit || avgLux >= shadeLimit) {
                targetAction = Boolean.FALSE;
            } else if (closeAtNight && currentLux <= nightThreshold) {
                targetAction = Boolean.FALSE;
            } else if (currentLux <= (shadeLimit - 1500f) && avgLux <= (shadeLimit - 1500f)) {
                targetAction = Boolean.TRUE;
            }
        } else if (mode == MODE_ALWAYS_OPEN) {
            // Daima Açık Modu: Işık seviyesi ne olursa olsun her zaman AÇ
            targetAction = Boolean.TRUE;
        } else {
            // Açık Hava Modu
            if (currentLux >= 35000f || avgLux >= 35000f) {
                targetAction = Boolean.FALSE;
            } else if (closeAtNight && currentLux <= nightThreshold) {
                targetAction = Boolean.FALSE;
            } else if (currentLux <= 32000f && avgLux <= 32000f) {
                targetAction = Boolean.TRUE;
            }
        }

        int targetScope = prefs.getInt(KEY_TARGET_SCOPE, TARGET_SUNROOF_AND_SHADE);
        int currentSpeed = metrics.preferredSpeed();
        boolean speedGuardActive = prefs.getBoolean(KEY_SPEED_GUARD, true);
        int speedLimit = prefs.getInt(KEY_SPEED_LIMIT, 100);
        boolean isHighSpeed = speedGuardActive && (currentSpeed > speedLimit);

        // Yüksek Hız Koruması: Hız belirlenen limiti (>100 km/s) aştığında cam tavanı kapatır ve açılmasını engeller
        if (isHighSpeed && targetScope != TARGET_SHADE_ONLY) {
            targetAction = Boolean.FALSE;
        }

        // Yalnızca Perde modunda olunsa dahi cam tavan açıksa ve yüksek hız limiti aşıldıysa güvenlik için tavanı kapat
        if (targetScope == TARGET_SHADE_ONLY && isHighSpeed && metrics.isSunroofOpen()) {
            if (now - lastMovementTimestamp > 10000L) {
                logEvent("\u26A0\uFE0F Y\u00dcKSEK HIZ G\u00dcVENL\u0130\u011e\u0130: H\u0131z " + currentSpeed + " km/s > " + speedLimit + " km/s! Cam tavan kapat\u0131l\u0131yor.");
                controller.closeSunroof(false);
                lastMovementTimestamp = now;
                lastActionDescription = appContext.getString(R.string.sunroof_engine_high_speed_closed,
                        currentSpeed, timeFormat.format(new Date()));
                lastActionTimestamp = System.currentTimeMillis();
                notifyStatusChanged();
            }
        }

        Boolean isCurrentlyOpen = (targetScope == TARGET_SHADE_ONLY)
                ? metrics.isSunshadeOpen()
                : metrics.sunroofOpenOrNull();

        // Eğer mevcut durum hedefle aynıysa veya deadband içindeyse, bekle
        if (targetAction == null || (isCurrentlyOpen != null && isCurrentlyOpen.equals(targetAction))) {
            pendingTarget = null;
            conditionMetSinceTimestamp = 0L;
            notifyStatusChanged();
            return;
        }

        // 6. Hız Korumaları
        boolean isInitialStartupOpen = initialStartupPending && Boolean.TRUE.equals(targetAction);

        // A. Minimum Hız Koruması (Kullanıcı Kuralı: Kırmızı ışık / dur-kalkta gölgede açılmayı engeller)
        // İlk çalıştırmada güneş yoksa hız şartı aranmaz (hemen açılır); seyir halindeyken en az 15 km/s şartı aranır.
        if (targetScope != TARGET_SHADE_ONLY && Boolean.TRUE.equals(targetAction) && !isInitialStartupOpen && currentSpeed < 15) {
            pendingTarget = null;
            conditionMetSinceTimestamp = 0L;
            notifyStatusChanged(appContext.getString(R.string.sunroof_engine_waiting_speed));
            return;
        }

        // 7. Kararlılık / Asimetrik Debounce Süresi Kontrolü
        // - İlk çalıştırmada güneş yoksa: 0 saniye (Kullanıcı Kuralı: Hız sıfır olsa bile derhal aç!)
        // - Seyir esnasında gölgede açma: 120 saniye / 2 dakika (Trafik ışığı ve köprü altı koruması)
        // - Yüksek hızda kapatma: 3 saniye (Hızlı güvenlik koruması)
        // - Güneşte kapatma: 5 saniye (Acil güneş koruması)
        long debounceMs;
        if (isInitialStartupOpen) {
            debounceMs = 0L;
        } else if (Boolean.TRUE.equals(targetAction)) {
            debounceMs = 120 * 1000L;
        } else if (isHighSpeed) {
            debounceMs = 3 * 1000L;
        } else {
            debounceMs = 5 * 1000L;
        }

        if (pendingTarget == null || !pendingTarget.equals(targetAction)) {
            pendingTarget = targetAction;
            conditionMetSinceTimestamp = now;
        }

        long conditionDurationMs = now - conditionMetSinceTimestamp;
        if (conditionDurationMs < debounceMs) {
            long remainingSec = Math.max(1, (debounceMs - conditionDurationMs) / 1000);
            String actionName = appContext.getString(Boolean.TRUE.equals(targetAction) ? R.string.sunroof_engine_opening : R.string.sunroof_engine_closing);
            String reasonText;
            if (isHighSpeed) {
                reasonText = appContext.getString(R.string.sunroof_engine_reason_speed, remainingSec);
            } else if (Boolean.TRUE.equals(targetAction)) {
                reasonText = appContext.getString(R.string.sunroof_engine_reason_shade, remainingSec);
            } else {
                reasonText = appContext.getString(R.string.sunroof_engine_reason_sun, remainingSec);
            }
            notifyStatusChanged(actionName + " " + reasonText);
            return;
        }

        // İlk çalıştırma açılışından sonra normal seyir kurallarına geçiş yap
        if (initialStartupPending) {
            initialStartupPending = false;
        }

        // 8. Motor Koruma / Cooldown Kontrolü (10 sn)
        int cooldownSec = Math.min(prefs.getInt(KEY_COOLDOWN_SEC, 10), 10);
        long cooldownMs = cooldownSec * 1000L;
        long timeSinceLastMovement = now - lastMovementTimestamp;

        if (timeSinceLastMovement < cooldownMs) {
            long remainingSec = Math.max(1, (cooldownMs - timeSinceLastMovement) / 1000);
            notifyStatusChanged(appContext.getString(R.string.sunroof_engine_motor_cooldown, remainingSec));
            return;
        }

        // 9. Komut İcrası (Güneşlik/perde kontrolü kaldırıldı, yalnızca cam tavan yönetilir)
        boolean tiltOnly = targetScope != TARGET_SHADE_ONLY && prefs.getBoolean(KEY_TILT_ONLY, false);
        boolean success;
        String elementLabel = appContext.getString((targetScope == TARGET_SHADE_ONLY)
                ? R.string.sunroof_engine_label_shade
                : (tiltOnly ? R.string.sunroof_engine_label_roof_tilt : R.string.sunroof_engine_label_roof));
        if (targetAction) {
            if (targetScope == TARGET_SHADE_ONLY) {
                success = controller.openSunshadeOnly();
            } else if (tiltOnly) {
                success = controller.tiltSunroof();
            } else {
                success = controller.openSunroof(false);
            }
            String prefix = appContext.getString((mode == MODE_ALWAYS_OPEN) ? R.string.sunroof_engine_prefix_always :
                    (isInitialStartupOpen ? R.string.sunroof_engine_prefix_startup : R.string.sunroof_engine_prefix_ideal));
            lastActionDescription = appContext.getString(R.string.sunroof_engine_opened_fmt,
                    prefix, elementLabel, timeFormat.format(new Date()));
            logEvent(elementLabel + " açıldı (" + prefix + ", Işık: " + (int) currentLux + " lx, Ort: " + (int) avgLux + " lx)");
        } else {
            if (targetScope == TARGET_SHADE_ONLY) {
                success = controller.closeSunshadeOnly();
            } else {
                success = controller.closeSunroof(false);
            }
            String reason;
            if (isHighSpeed) {
                reason = appContext.getString(R.string.sunroof_engine_reason_high_speed, currentSpeed, speedLimit);
            } else if (currentLux >= sunThreshold || avgLux >= sunThreshold) {
                reason = appContext.getString(R.string.sunroof_engine_reason_harsh_sun);
            } else {
                reason = appContext.getString(R.string.sunroof_engine_reason_dark);
            }
            lastActionDescription = appContext.getString(R.string.sunroof_engine_closed_fmt,
                    reason, elementLabel, timeFormat.format(new Date()));
            logEvent(elementLabel + " kapatıldı (" + reason + ", Işık: " + (int) currentLux + " lx)");
        }

        if (success) {
            lastMovementTimestamp = now;
            lastActionTimestamp = System.currentTimeMillis();
            pendingTarget = null;
            conditionMetSinceTimestamp = 0L;
        }

        notifyStatusChanged();
    }

    private void notifyStatusChanged() {
        notifyStatusChanged(null);
    }

    private void notifyStatusChanged(String pendingAction) {
        if (listeners.isEmpty()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        // UI thread'ini saniyede defalarca gereksiz güncelleme ile boğmayı engelle
        if (pendingAction == null && (now - lastStatusNotifyTimestamp < 800L)) {
            return;
        }
        lastStatusNotifyTimestamp = now;
        Status status = buildStatus(pendingAction);
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onSunroofStatusChanged(status);
            }
        });
    }

    public Status buildStatus() {
        return buildStatus(null);
    }

    private Status buildStatus(String pendingAction) {
        VehicleLightSensorManager lightManager = VehicleLightSensorManager.getInstance(appContext);
        VehicleMetricsSnapshot metrics = VehicleMetricsRepository.getInstance(appContext).getSnapshot();
        boolean connected = VehicleSunroofController.getInstance(appContext).isConnected();

        boolean enabled = prefs.getBoolean(KEY_ENABLED, false);
        int mode = prefs.getInt(KEY_MODE, MODE_SMART_COMFORT);
        int sensitivity = prefs.getInt(KEY_SENSITIVITY, SENSITIVITY_BALANCED);
        float lux = lightManager.getEffectiveLux();
        String luxSource = lightManager.getEffectiveSource();
        boolean simulated = lightManager.isSimulationEnabled();

        long now = SystemClock.elapsedRealtime();
        Boolean sunroofOpen = metrics.sunroofOpenOrNull();
        boolean sunshadeOpen = metrics.isSunshadeOpen();
        boolean rainDetectedNow = metrics.isRaining() || prefs.getBoolean(KEY_SIMULATED_RAIN, false);
        if (rainDetectedNow) {
            lastRainDetectedTimestampMs = now;
        }
        boolean isRaining = (now - lastRainDetectedTimestampMs < RAIN_HOLD_DURATION_MS);
        int speed = metrics.preferredSpeed();
        int power = metrics.powerMode;

        int cooldownSec = Math.min(prefs.getInt(KEY_COOLDOWN_SEC, 10), 10);
        long elapsedSinceMovement = now - lastMovementTimestamp;
        int cooldownRemaining = (int) Math.max(0, (cooldownSec * 1000L - elapsedSinceMovement) / 1000);

        boolean closeAtNight = prefs.getBoolean(KEY_CLOSE_AT_NIGHT, false);

        String conditionTitle;
        String conditionAdvice;

        if (isRaining) {
            conditionTitle = appContext.getString(R.string.sunroof_engine_cond_rain_title);
            conditionAdvice = appContext.getString(R.string.sunroof_engine_cond_rain_advice);
        } else if (lux < 0) {
            conditionTitle = appContext.getString(R.string.sunroof_engine_cond_wait_title);
            conditionAdvice = appContext.getString(R.string.sunroof_engine_cond_wait_advice);
        } else if (lux >= getSunThreshold(sensitivity)) {
            conditionTitle = appContext.getString(R.string.sunroof_engine_cond_sun_title);
            conditionAdvice = appContext.getString(R.string.sunroof_engine_cond_sun_advice);
        } else if (lux <= getNightThreshold(sensitivity)) {
            if (closeAtNight) {
                conditionTitle = appContext.getString(R.string.sunroof_engine_cond_night_closed_title);
                conditionAdvice = appContext.getString(R.string.sunroof_engine_cond_night_closed_advice);
            } else {
                conditionTitle = appContext.getString(R.string.sunroof_engine_cond_night_open_title);
                conditionAdvice = appContext.getString(R.string.sunroof_engine_cond_night_open_advice);
            }
        } else if (lux <= 10500f) {
            conditionTitle = appContext.getString(R.string.sunroof_engine_cond_dusk_title);
            conditionAdvice = appContext.getString(R.string.sunroof_engine_cond_night_open_advice);
        } else {
            conditionTitle = appContext.getString(R.string.sunroof_engine_cond_cloudy_title);
            conditionAdvice = appContext.getString(R.string.sunroof_advice_default);
        }

        if (manualClosedByDriver) {
            conditionAdvice = appContext.getString(R.string.sunroof_engine_cond_driver_locked_advice);
            if (pendingAction == null) {
                pendingAction = appContext.getString(R.string.sunroof_engine_pending_driver_locked);
            }
        }

        return new Status(
                connected,
                enabled,
                mode,
                sensitivity,
                lux,
                rollingAverageLux > 0 ? rollingAverageLux : lux,
                luxSource,
                simulated,
                conditionTitle,
                conditionAdvice,
                sunroofOpen,
                sunshadeOpen,
                isRaining,
                speed,
                power,
                lastActionDescription != null ? lastActionDescription : appContext.getString(R.string.sunroof_engine_ready),
                lastActionTimestamp,
                cooldownRemaining,
                pendingAction);
    }

    private void logEvent(String message) {
        Log.i(TAG, message);
        Intent intent = new Intent(ACTION_LOG);
        intent.putExtra(EXTRA_LOG_MESSAGE, "[" + TAG + "] " + message);
        appContext.sendBroadcast(intent);
    }

    public SharedPreferences getPrefs() {
        return prefs;
    }

    /**
     * Sürücü hızlı jest veya butonla manuel müdahale ettiğinde:
     * - Kapatma yapıldıysa: Otomatik açılmayı bu sürüş boyunca kilitler.
     * - Açma yapıldıysa: Sürüş kilidini kaldırır.
     */
    /**
     * Sürücü hızlı jest veya butonla manuel müdahale ettiğinde çağrılır.
     * SADECE cam tavanın kendisi (SPF) açılıp kapandığında sürüş kilidi güncellenir.
     * Perde / güneşlik hareketleri tavan kilidini ASLA etkilemez.
     */
    public void notifyManualOverride(String actionName, boolean isSunroof, boolean isClose) {
        lastActionDescription = actionName;
        lastActionTimestamp = System.currentTimeMillis();
        if (isSunroof) {
            if (isClose) {
                manualClosedByDriver = true;
                logEvent("Hızlı Kumanda: " + actionName + " — Otomatik açılma bu sürüş boyunca kilitlendi.");
            } else {
                manualClosedByDriver = false;
                logEvent("Hızlı Kumanda: " + actionName + " — Sürüş kilidi kaldırıldı.");
            }
        } else {
            logEvent("Hızlı Kumanda: " + actionName);
        }
        notifyStatusChanged();
    }

    public void notifyManualOverride(String actionName) {
        if (actionName == null) {
            return;
        }
        String low = actionName.toLowerCase(Locale.ROOT);
        boolean isSunshade = actionName.contains("Perde") || actionName.contains("Güneşlik") || low.contains("shade");
        boolean isSunroof = !isSunshade && (actionName.contains("Tavan") || actionName.contains("Cam") || low.contains("roof"));
        boolean isClose = low.contains("kapat") || low.contains("close");
        notifyManualOverride(actionName, isSunroof, isClose);
    }
}
