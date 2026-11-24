package com.mapcontrol.vehicle;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.HvacID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.extra.project.carinfo.proxy.Constants;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Araç dış ortam termometresini izler; kayıtlı sıcaklık / süre koşulları oluşunca
 * koltuk ısıtma, havalandırma, direksiyon ısıtma ve klimayı bir kez uygular.
 */
public final class ClimateAutomationEngine {

    public interface Listener {
        void onClimateStatus(Status status);
    }

    public enum RuleRunState {
        DISABLED,
        WAITING_VEHICLE,
        WAITING_ENGINE,
        ACTIVATING,
        WAITING_TEMP,
        WAITING_START_DELAY,
        ACTIVE,
        COMPLETED_TIMEOUT,
        MANUAL_OVERRIDE,
        UNSUPPORTED
    }

    public static final class RuleStatus {
        public final RuleRunState state;
        public final int remainingMinutes;

        public RuleStatus(RuleRunState state, int remainingMinutes) {
            this.state = state;
            this.remainingMinutes = remainingMinutes;
        }
    }

    public static final class Status {
        public final boolean connected;
        public final boolean masterEnabled;
        public final boolean powerOn;
        public final Double outsideTempC;
        public final String lastAction;
        public final Map<String, RuleStatus> ruleStatuses;

        Status(boolean connected, boolean masterEnabled, boolean powerOn,
               Double outsideTempC, String lastAction,
               Map<String, RuleStatus> ruleStatuses) {
            this.connected = connected;
            this.masterEnabled = masterEnabled;
            this.powerOn = powerOn;
            this.outsideTempC = outsideTempC;
            this.lastAction = lastAction;
            this.ruleStatuses = ruleStatuses != null
                    ? Collections.unmodifiableMap(new HashMap<>(ruleStatuses))
                    : Collections.emptyMap();
        }
    }

    private static final String TAG = "ClimateAuto";
    private static final String ACTION_LOG = "com.mapcontrol.LOG_MESSAGE";
    private static final String EXTRA_LOG_MESSAGE = "log_message";
    private static final long POLL_INTERVAL_SECONDS = 2;
    private static final long DRIVE_REARM_GAP_MS = TimeUnit.SECONDS.toMillis(30);
    /**
     * Araç ilk çalıştığında (PowerMode 1 veya 2 geçişinde) BCM/klima ünitesinin ilk paketi
     * kaçırmaması için en fazla 3 deneme (0s, 2s, 4s) yapılır; ardından komut kilitlenir ve
     * sürüş boyunca bir daha gönderilmez. Böylece sürücü araç ekranından manuel kapattığında
     * otomasyon tekrar açmaz.
     */
    private static final int MAX_STARTUP_SEND_ATTEMPTS = 3;
    private static final int MODULE_HVAC = VDEventCarInfo.MODULE_HVAC;
    private static final int MODULE_SETTING = VDEventCarInfo.MODULE_CAR_SETTING;
    private static final int MODULE_READONLY = VDEventCarInfo.MODULE_READONLY_INFO;
    private static final int[] HVAC_IDS = {
            HvacID.ID_AC_TEMPERATURE_CELSIUS_OUTSIDE,
            HvacID.ID_AC_TEMPERATURE_FAHRENHEIT_OUTSIDE,
            HvacID.ID_AC_FRONT_GLASS_HEAT,
            HvacID.ID_AC_MAX_FRONT_DEFOREST,
            HvacID.ID_AC_REAR_DEFROST,
            HvacID.ID_AC_STATE,
            HvacID.ID_AC_WORKING,
            HvacID.ID_AC_AUTO_STATE
    };
    private static final int[] SETTING_IDS = {
            CarSettingID.ID_CAR_DRIVER_SEAT_HEAT,
            CarSettingID.ID_CAR_COPILOT_SEAT_HEAT,
            CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION,
            CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION,
            CarSettingID.ID_CAR_STEER_WHEEL_HEAT,
            CarSettingID.ID_CAR_REAR_WINDOW_HEAT
    };
    private static final int[] READONLY_IDS = {
            ReadOnlyID.ID_SYSTEM_POWER_MODE,
            ReadOnlyID.ID_CAR_POWER_MODE,
            ReadOnlyID.ID_ENGINE_STATE
    };

    private static volatile ClimateAutomationEngine instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final Set<String> firedThisDrive = new HashSet<>();
    private final Set<String> appliedThisDrive = new HashSet<>();
    private final Set<String> appliedWhileReady = new HashSet<>();
    private final Set<String> manualOverridesThisDrive = new HashSet<>();
    private final Set<String> pendingDeactivation = new HashSet<>();
    /** Araç geri bildirimiyle gerçekten aktif olduğu görülen kurallar (bu sürüş). */
    private final Set<String> confirmedActive = new HashSet<>();
    private final Set<String> awaitingActivationFeedback = new HashSet<>();
    private final Set<String> feedbackReadErrorsReported = new HashSet<>();
    private final Map<String, Integer> sendAttemptsThisPhase = new HashMap<>();
    private final Map<String, Long> lastSentElapsedMs = new HashMap<>();
    private final Map<String, String> lastRuleEvaluationStates = new HashMap<>();
    private final Map<String, String> lastCommandErrors = new HashMap<>();
    private final Map<String, Long> ruleActivatedAtMs = new HashMap<>();
    private final Map<String, Long> ruleEnabledAtMs = new HashMap<>();
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor();

    private volatile boolean started;
    private volatile boolean callbacksRegistered;
    private volatile Double outsideTempC;
    private volatile int lastRawOutsideTemp = Integer.MIN_VALUE;
    private volatile int lastRawOutsideTempF = Integer.MIN_VALUE;
    private volatile int lastPowerMode = -1;
    private volatile long ignitionOnAtMs;
    private volatile long driveReadySinceMs;
    private volatile boolean awaitingRestartAfterPowerDown;
    private volatile boolean engineStartedThisDrive;
    /**
     * Bu bağlantıda ID_CAR_POWER_MODE (13) / ID_ENGINE_STATE (74) en az bir kez sıfırdan farklı geldi mi?
     * CarInfoProxy yanıt alamadığında [0] döndürdüğü için "hiç gelmedi" ile "0" ayırt edilemez;
     * ikisi de hiç gelmediyse (ör. T19C) motor çalışıyor kontrolü PowerMode>=2'ye düşer.
     */
    private volatile boolean carPowerSignalSeen;
    private volatile boolean engineStateSignalSeen;
    private volatile String lastAction = "";
    private String lastEvaluationGate = "";
    private String lastPollError = "";
    private String lastLoggedSignals = "";
    private long lastSuccessfulPollElapsedMs;
    private ScheduledFuture<?> pollFuture;

    private final IVDBusNotify.Stub hvacNotify = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (event == null || event.getPayload() == null || event.getId() != MODULE_HVAC) {
                return;
            }
            int itemId = event.getPayload().getInt(Constants.CMD_ID, -1);
            int[] values = event.getPayload().getIntArray(Constants.VALUE);
            if (values == null || values.length == 0) {
                return;
            }
            int raw = values[0];
            if (itemId == HvacID.ID_AC_TEMPERATURE_FAHRENHEIT_OUTSIDE) {
                logOutsideFahrenheit(raw, "hvac");
                return;
            }
            if (itemId == HvacID.ID_AC_TEMPERATURE_CELSIUS_OUTSIDE) {
                try {
                    io.execute(() -> updateOutsideTemperature(raw, "hvac"));
                } catch (java.util.concurrent.RejectedExecutionException e) {
                    Log.w(TAG, "Temperature update ignored after engine shutdown", e);
                }
                return;
            }
            try {
                io.execute(() -> onComfortSignalNotify(MODULE_HVAC, itemId, raw));
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
            }
        }
    };

    private final IVDBusNotify.Stub settingNotify = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (event == null || event.getPayload() == null || event.getId() != MODULE_SETTING) {
                return;
            }
            int itemId = event.getPayload().getInt(Constants.CMD_ID, -1);
            int[] values = event.getPayload().getIntArray(Constants.VALUE);
            if (values == null || values.length == 0) {
                return;
            }
            int raw = values[0];
            try {
                io.execute(() -> onComfortSignalNotify(MODULE_SETTING, itemId, raw));
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
            }
        }
    };

    private final IVDBusNotify.Stub readonlyNotify = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (event == null || event.getPayload() == null || event.getId() != MODULE_READONLY) {
                return;
            }
            int itemId = event.getPayload().getInt(Constants.CMD_ID, -1);
            int[] values = event.getPayload().getIntArray(Constants.VALUE);
            if (values == null || values.length == 0) {
                return;
            }
            try {
                io.execute(() -> {
                    if (itemId == ReadOnlyID.ID_SYSTEM_POWER_MODE
                            || itemId == ReadOnlyID.ID_CAR_POWER_MODE
                            || itemId == ReadOnlyID.ID_ENGINE_STATE) {
                        refreshPowerModeFromBus();
                    }
                    notifyListeners();
                });
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
            }
        }
    };

    private ClimateAutomationEngine(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
    }

    public static ClimateAutomationEngine getInstance(Context context) {
        if (instance == null) {
            synchronized (ClimateAutomationEngine.class) {
                if (instance == null) {
                    instance = new ClimateAutomationEngine(context);
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
        start();
        mainHandler.post(() -> listener.onClimateStatus(currentStatus()));
    }

    public void removeListener(Listener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public synchronized void stop() {
        started = false;
        if (pollFuture != null) {
            pollFuture.cancel(true);
            pollFuture = null;
        }
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        io.execute(() -> {
            try {
                CarInfoProxy.getInstance().init(appContext);
                registerHvacCallback();
                refreshFromBus();
                if (isConnected()) {
                    lastSuccessfulPollElapsedMs = SystemClock.elapsedRealtime();
                }
            } catch (Throwable t) {
                log("Klima otomasyonu başlatılamadı: " + t.getMessage());
            }
            evaluate("start");
            notifyListeners();
        });
        pollFuture = io.scheduleAtFixedRate(() -> {
            try {
                if (!CarInfoProxy.getInstance().isServiceConnnected()) {
                    if (lastPowerMode != -1) {
                        lastPowerMode = -1;
                        ignitionOnAtMs = 0;
                        driveReadySinceMs = 0;
                        awaitingRestartAfterPowerDown = false;
                        engineStartedThisDrive = false;
                        carPowerSignalSeen = false;
                        engineStateSignalSeen = false;
                        callbacksRegistered = false;
                        firedThisDrive.clear();
                        appliedThisDrive.clear();
                        appliedWhileReady.clear();
                        sendAttemptsThisPhase.clear();
                        ruleActivatedAtMs.clear();
                        lastSuccessfulPollElapsedMs = 0;
                        log("Araç veri bağlantısı kesildi; sonraki bağlantıda otomasyon yeniden başlatılacak");
                    }
                    CarInfoProxy.getInstance().init(appContext);
                    return;
                }
                long nowElapsedMs = SystemClock.elapsedRealtime();
                if (lastSuccessfulPollElapsedMs != 0
                        && nowElapsedMs - lastSuccessfulPollElapsedMs > DRIVE_REARM_GAP_MS) {
                    rearmAfterPollingGap();
                }
                registerHvacCallback();
                refreshFromBus();
                retryPendingDeactivations();
                evaluate("poll");
                notifyListeners();
                lastPollError = "";
                lastSuccessfulPollElapsedMs = SystemClock.elapsedRealtime();
            } catch (Throwable t) {
                String error = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                if (!error.equals(lastPollError)) {
                    log("Klima otomasyonu yenileme hatası: " + error);
                    lastPollError = error;
                }
            }
        }, POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void rearmAfterPollingGap() {
        lastPowerMode = -1;
        ignitionOnAtMs = 0;
        driveReadySinceMs = 0;
        awaitingRestartAfterPowerDown = false;
        engineStartedThisDrive = false;
        firedThisDrive.clear();
        appliedThisDrive.clear();
        appliedWhileReady.clear();
        sendAttemptsThisPhase.clear();
        ruleActivatedAtMs.clear();
        lastSuccessfulPollElapsedMs = SystemClock.elapsedRealtime();
        log("Konfor otomasyonu: 30 saniyeden uzun izleme kesintisi; "
                + "araç durumu ve otomasyon kuralları yeniden değerlendiriliyor");
    }

    public void notifyRulesChanged() {
        io.execute(() -> {
            long nowMs = System.currentTimeMillis();
            for (String id : ClimateAutomationStore.ALL_IDS) {
                if (!appliedThisDrive.contains(id)) {
                    ruleEnabledAtMs.putIfAbsent(id, nowMs);
                }
            }
            firedThisDrive.clear();
            sendAttemptsThisPhase.clear();
            manualOverridesThisDrive.clear();
            ruleActivatedAtMs.clear();
            refreshFromBus();
            evaluate("rules");
            notifyListeners();
        });
    }

    public void notifyRuleChanged(String id) {
        io.execute(() -> {
            manualOverridesThisDrive.remove(id);
            sendAttemptsThisPhase.remove(id);
            if (!appliedThisDrive.contains(id)) {
                ruleActivatedAtMs.remove(id);
                ruleEnabledAtMs.put(id, System.currentTimeMillis());
            }
            refreshFromBus();
            ClimateAutomationStore.Rule rule = ClimateAutomationStore.getRule(prefs, id);
            Double temp = outsideTempC;
            boolean tempMatches = temp != null
                    && ClimateAutomationStore.matchesTemperature(temp, rule.compare, rule.tempC);
            long nowMs = System.currentTimeMillis();
            boolean startDelayPending = remainingStartDelayMinutes(
                    rule, effectiveStartDelayBase(id, driveReadySinceMs), nowMs) > 0;
            boolean timedOut = isRuleTimedOut(id, rule, nowMs);
            boolean eligible = rule.enabled
                    && ClimateAutomationStore.isMasterEnabled(prefs)
                    && isConnected()
                    && isDrivePowerReady(lastPowerMode, awaitingRestartAfterPowerDown, engineStartedThisDrive)
                    && tempMatches
                    && !startDelayPending
                    && !timedOut;
            if (!eligible) {
                if (appliedThisDrive.contains(id)) {
                    deactivateRule(id);
                }
                if (!rule.enabled || !tempMatches || startDelayPending) {
                    ruleActivatedAtMs.remove(id);
                }
                firedThisDrive.remove(id);
            } else if (appliedThisDrive.contains(id)) {
                if (applyRule(id, rule)) {
                    ruleActivatedAtMs.putIfAbsent(id, System.currentTimeMillis());
                    firedThisDrive.add(id);
                    if (lastPowerMode == 1) {
                        appliedWhileReady.add(id);
                    }
                    log("Etkin konfor otomasyonu ayarı güncellendi: " + id);
                }
            } else {
                firedThisDrive.remove(id);
                evaluate("rule:" + id);
            }
            notifyListeners();
        });
    }

    public void disableRule(String id) {
        io.execute(() -> {
            ruleActivatedAtMs.remove(id);
            deactivateRule(id);
            refreshFromBus();
            evaluate("rule-disabled:" + id);
            notifyListeners();
        });
    }

    public void disableAllRules() {
        io.execute(() -> {
            for (String id : new HashSet<>(appliedThisDrive)) {
                deactivateRule(id);
            }
            firedThisDrive.clear();
            sendAttemptsThisPhase.clear();
            manualOverridesThisDrive.clear();
            ruleActivatedAtMs.clear();
            notifyListeners();
        });
    }

    public Status currentStatus() {
        return new Status(
                isConnected(),
                ClimateAutomationStore.isMasterEnabled(prefs),
                isDrivePowerReady(lastPowerMode, awaitingRestartAfterPowerDown, engineStartedThisDrive),
                outsideTempC,
                lastAction,
                buildRuleStatusesSnapshot(System.currentTimeMillis()));
    }

    private Map<String, RuleStatus> buildRuleStatusesSnapshot(long nowMs) {
        Map<String, RuleStatus> map = new HashMap<>();
        boolean master = ClimateAutomationStore.isMasterEnabled(prefs);
        boolean connected = isConnected();
        boolean hasIgnitionPower = lastPowerMode >= 1 && !awaitingRestartAfterPowerDown;
        boolean powerReady = isDrivePowerReady(lastPowerMode, awaitingRestartAfterPowerDown, engineStartedThisDrive);
        Double temp = outsideTempC;

        for (String id : ClimateAutomationStore.ALL_IDS) {
            ClimateAutomationStore.Rule rule = ClimateAutomationStore.getRule(prefs, id);
            if (!master || !rule.enabled) {
                map.put(id, new RuleStatus(RuleRunState.DISABLED, 0));
                continue;
            }
            if (confirmedActive.contains(id)) {
                int remMin = 0;
                if (rule.delayMinutes > 0) {
                    Long actAt = ruleActivatedAtMs.get(id);
                    if (actAt != null && actAt > 0) {
                        long remMs = Math.max(0L, (rule.delayMinutes * 60_000L) - Math.max(0L, nowMs - actAt));
                        remMin = Math.max(1, (int) Math.ceil(remMs / 60_000.0));
                    } else {
                        remMin = rule.delayMinutes;
                    }
                }
                map.put(id, new RuleStatus(RuleRunState.ACTIVE, remMin));
                continue;
            }
            if (manualOverridesThisDrive.contains(id)) {
                map.put(id, new RuleStatus(RuleRunState.MANUAL_OVERRIDE, 0));
                continue;
            }
            if (isRuleTimedOut(id, rule, nowMs)) {
                map.put(id, new RuleStatus(RuleRunState.COMPLETED_TIMEOUT, 0));
                continue;
            }
            if (!connected || !hasIgnitionPower) {
                map.put(id, new RuleStatus(RuleRunState.WAITING_VEHICLE, 0));
                continue;
            }
            if (!powerReady) {
                if (appliedThisDrive.contains(id)) {
                    map.put(id, new RuleStatus(RuleRunState.ACTIVATING, 0));
                } else {
                    map.put(id, new RuleStatus(RuleRunState.WAITING_ENGINE, 0));
                }
                continue;
            }
            if (temp == null || !ClimateAutomationStore.matchesTemperature(temp, rule.compare, rule.tempC)) {
                map.put(id, new RuleStatus(RuleRunState.WAITING_TEMP, 0));
                continue;
            }
            int startRem = remainingStartDelayMinutes(
                    rule, effectiveStartDelayBase(id, driveReadySinceMs), nowMs);
            if (startRem > 0) {
                map.put(id, new RuleStatus(RuleRunState.WAITING_START_DELAY, startRem));
                continue;
            }
            if (firedThisDrive.contains(id)
                    && !awaitingActivationFeedback.contains(id)
                    && sendAttemptsThisPhase.getOrDefault(id, 0) >= MAX_STARTUP_SEND_ATTEMPTS) {
                map.put(id, new RuleStatus(RuleRunState.UNSUPPORTED, 0));
                continue;
            }
            map.put(id, new RuleStatus(RuleRunState.ACTIVATING, 0));
        }
        return map;
    }

    private long effectiveStartDelayBase(String id, long driveReadyMs) {
        Long enabledAt = ruleEnabledAtMs.get(id);
        return enabledAt != null ? Math.max(driveReadyMs, enabledAt) : driveReadyMs;
    }

    static int remainingStartDelayMinutes(ClimateAutomationStore.Rule rule, long driveReadySinceMs, long nowMs) {
        if (rule == null || rule.startDelayMinutes <= 0 || driveReadySinceMs <= 0L) {
            return 0;
        }
        long targetMs = driveReadySinceMs + (rule.startDelayMinutes * 60_000L);
        if (nowMs >= targetMs) {
            return 0;
        }
        long remMs = targetMs - nowMs;
        return Math.max(1, (int) Math.ceil(remMs / 60_000.0));
    }

    public static Double decodeOutsideCelsius(int raw) {
        if (raw == Integer.MIN_VALUE) {
            return null;
        }
        double temperatureC = (raw - 110) / 2.0;
        if (temperatureC >= -40 && temperatureC <= 80) {
            return temperatureC;
        }
        return null;
    }

    static boolean isDrivePowerReady(
            int powerMode,
            boolean awaitingRestartAfterPowerDown,
            boolean engineStartedThisDrive) {
        return powerMode >= 1 && !awaitingRestartAfterPowerDown && engineStartedThisDrive;
    }

    static int resolveEffectivePowerMode(int systemPowerMode, int carPowerMode) {
        boolean sysValid = systemPowerMode >= 0 && systemPowerMode <= 10;
        boolean carValid = carPowerMode >= 0 && carPowerMode <= 10;
        if (sysValid && carValid) {
            return Math.max(systemPowerMode, carPowerMode);
        }
        if (sysValid) {
            return systemPowerMode;
        }
        if (carValid) {
            return carPowerMode;
        }
        return -1;
    }

    /**
     * Bir özelliğin kullanıcı tarafından manuel kapatıldığının kabul edilebilmesi için
     * öncelikle araç geri bildirimiyle gerçekten açıldığının teyit edilmiş olması gerekir.
     * Açılmadan önce gelen kapalı sinyalleri zamanlama/uyanma gecikmesi sayılır.
     */
    static boolean shouldDetectManualOverride(boolean confirmedActiveForRule, boolean isOffSignal) {
        return confirmedActiveForRule && isOffSignal;
    }

    static boolean isCarPowerModeRunning(int carPowerMode) {
        return carPowerMode == 4;
    }

    static boolean shouldRearmOnCarPowerRunning(int powerMode, boolean engineStartedThisDrive, int carPowerMode) {
        return shouldRearmOnEngineRunning(powerMode, engineStartedThisDrive, isCarPowerModeRunning(carPowerMode));
    }

    static boolean shouldRearmOnEngineRunning(int powerMode, boolean engineStartedThisDrive, boolean engineRunning) {
        return powerMode >= 1 && !engineStartedThisDrive && engineRunning;
    }

    static boolean isEngineStateRunning(int engineState) {
        return engineState == 1;
    }

    /**
     * Motor çalışıyor mu? Önce ID_CAR_POWER_MODE (13) == 4, sonra ID_ENGINE_STATE (74) == 1 bakılır.
     * T19C (Tiggo 7) firmware'inde 13 tanımlı değil; yalnızca T26/T1EJ 4x4 akışında okunuyor.
     * İki sinyal de bu bağlantıda hiç sıfırdan farklı gelmediyse PowerMode>=2 (eski davranış) kabul edilir,
     * böylece sinyalleri olmayan araçlarda konfor otomasyonu sessizce devre dışı kalmaz.
     */
    static boolean isEngineRunning(int carPowerMode, int engineState,
            boolean carPowerSignalSeen, boolean engineStateSignalSeen, int powerMode) {
        if (isCarPowerModeRunning(carPowerMode) || isEngineStateRunning(engineState)) {
            return true;
        }
        if (!carPowerSignalSeen && !engineStateSignalSeen) {
            return powerMode >= 2;
        }
        return false;
    }

    static Boolean evaluateSeatFeedback(int rawValue, int uiLevel, boolean alreadyAppliedThisDrive) {
        if (rawValue == 0 || rawValue == 1) {
            return Boolean.FALSE;
        }
        int wire = Math.max(ClimateAutomationStore.LEVEL_MIN,
                Math.min(ClimateAutomationStore.LEVEL_MAX, uiLevel)) + 1;
        if (rawValue == wire || (uiLevel >= 2 && rawValue == uiLevel)) {
            return Boolean.TRUE;
        }
        if (alreadyAppliedThisDrive && rawValue >= 2) {
            return Boolean.TRUE;
        }
        return null;
    }

    /**
     * Araç ekranından yapılan manuel açma/kapatma bildirimlerini yakalar:
     * Otomasyon komutu gönderildikten sonra sürücü araç ekranından ilgili özelliği kapatırsa
     * (`manualOverridesThisDrive`) bu sürüşte bir daha otomatik açılmaz.
     */
    private void onComfortSignalNotify(int module, int itemId, int raw) {
        String ruleId = ruleIdForSignal(module, itemId);
        if (ruleId == null || !appliedThisDrive.contains(ruleId)) {
            return;
        }
        Long sentAt = lastSentElapsedMs.get(ruleId);
        long nowElapsed = SystemClock.elapsedRealtime();
        // Kendi gönderdiğimiz komutun hemen ardından gelen ilk yansımayı (800ms) yoksay
        if (sentAt != null && (nowElapsed - sentAt) < 800L) {
            return;
        }
        boolean isOffNotification;
        boolean isOnNotification;
        if (ClimateAutomationStore.usesHeatLevel(ruleId)) {
            // Koltuk ısıtma / soğutma: 0 veya 1 = kapalı
            isOffNotification = (raw == 0 || raw == 1);
            isOnNotification = (raw > 1);
        } else if (ClimateAutomationStore.STEER_HEAT.equals(ruleId)
                || ClimateAutomationStore.WINDSHIELD.equals(ruleId)
                || ClimateAutomationStore.REAR_DEFROST.equals(ruleId)) {
            // Direksiyon ısıtma ve ön/arka cam buğu çözme: 1 = açık, 0 (veya 2) = kapalı
            isOffNotification = (raw == 0 || raw == 2);
            isOnNotification = (raw == 1);
        } else {
            isOffNotification = (raw == 0);
            isOnNotification = (raw > 0);
        }
        if (shouldDetectManualOverride(confirmedActive.contains(ruleId), isOffNotification)) {
            firedThisDrive.add(ruleId);
            awaitingActivationFeedback.remove(ruleId);
            if (manualOverridesThisDrive.add(ruleId)) {
                log("Manuel müdahale algılandı (" + ruleId
                        + "); otomasyon bu sürüşte yeniden açmayacak");
            }
        } else if (isOnNotification) {
            // Araç özelliği aktif ettiğini bildirdi; artık tekrar göndermeye gerek yok
            confirmedActive.add(ruleId);
            firedThisDrive.add(ruleId);
            awaitingActivationFeedback.remove(ruleId);
        }
    }

    private String ruleIdForSignal(int module, int itemId) {
        if (module == MODULE_SETTING) {
            if (itemId == CarSettingID.ID_CAR_DRIVER_SEAT_HEAT) {
                return ClimateAutomationStore.DRIVER_HEAT;
            }
            if (itemId == CarSettingID.ID_CAR_COPILOT_SEAT_HEAT) {
                return ClimateAutomationStore.PASSENGER_HEAT;
            }
            if (itemId == CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION) {
                return ClimateAutomationStore.DRIVER_VENT;
            }
            if (itemId == CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION) {
                return ClimateAutomationStore.PASSENGER_VENT;
            }
            if (itemId == CarSettingID.ID_CAR_STEER_WHEEL_HEAT) {
                return ClimateAutomationStore.STEER_HEAT;
            }
            if (itemId == CarSettingID.ID_CAR_REAR_WINDOW_HEAT) {
                return ClimateAutomationStore.REAR_DEFROST;
            }
        } else if (module == MODULE_HVAC) {
            if (itemId == HvacID.ID_AC_REAR_DEFROST) {
                return ClimateAutomationStore.REAR_DEFROST;
            }
            if (itemId == HvacID.ID_AC_FRONT_GLASS_HEAT
                    || itemId == HvacID.ID_AC_MAX_FRONT_DEFOREST) {
                return ClimateAutomationStore.WINDSHIELD;
            }
            if (itemId == HvacID.ID_AC_STATE || itemId == HvacID.ID_AC_WORKING) {
                return ClimateAutomationStore.CLIMATE;
            }
        }
        return null;
    }

    private void refreshFromBus() {
        if (!isConnected()) {
            return;
        }
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        int[] tempItems = proxy.getItemValues(
                MODULE_HVAC, HvacID.ID_AC_TEMPERATURE_CELSIUS_OUTSIDE);
        if (tempItems != null && tempItems.length > 0) {
            updateOutsideTemperature(tempItems[0], "poll");
        }
        int[] fahrenheitItems = proxy.getItemValues(
                MODULE_HVAC, HvacID.ID_AC_TEMPERATURE_FAHRENHEIT_OUTSIDE);
        if (fahrenheitItems != null && fahrenheitItems.length > 0) {
            logOutsideFahrenheit(fahrenheitItems[0], "poll");
        }
        refreshPowerModeFromBus(proxy);
    }

    private void refreshPowerModeFromBus() {
        if (!isConnected()) {
            return;
        }
        refreshPowerModeFromBus(CarInfoProxy.getInstance());
    }

    private void refreshPowerModeFromBus(CarInfoProxy proxy) {
        int[] sysPwr = proxy.getItemValues(MODULE_READONLY, ReadOnlyID.ID_SYSTEM_POWER_MODE);
        int[] carPwr = proxy.getItemValues(MODULE_READONLY, ReadOnlyID.ID_CAR_POWER_MODE);
        int[] engState = proxy.getItemValues(MODULE_READONLY, ReadOnlyID.ID_ENGINE_STATE);
        int sysVal = (sysPwr != null && sysPwr.length > 0) ? sysPwr[0] : -1;
        int carVal = (carPwr != null && carPwr.length > 0) ? carPwr[0] : -1;
        int engVal = (engState != null && engState.length > 0) ? engState[0] : -1;
        if (carVal > 0) {
            carPowerSignalSeen = true;
        }
        if (engVal > 0) {
            engineStateSignalSeen = true;
        }
        String sigKey = sysVal + "|" + carVal + "|" + engVal;
        if (!sigKey.equals(lastLoggedSignals)) {
            lastLoggedSignals = sigKey;
            log("Araç sinyalleri: sysPower=" + sysVal + ", carPower=" + carVal + ", engineState=" + engVal);
        }

        int effectivePwr = resolveEffectivePowerMode(sysVal, carVal);
        if (effectivePwr >= 0) {
            if (effectivePwr != lastPowerMode) {
                log("Güç sinyali okundu (sysPower=" + sysVal + ", carPower=" + carVal
                        + " -> etkin=" + effectivePwr + ")");
            }
            applyPowerMode(effectivePwr);
        }
        if (isCarPowerModeRunning(carVal) && lastPowerMode < 1) {
            applyPowerMode(carVal);
        }
        String engineSource = "carPowerMode=" + carVal + ", engineState=" + engVal
                + ((!carPowerSignalSeen && !engineStateSignalSeen) ? ", yedek: PowerMode=" + lastPowerMode : "");
        if (isEngineRunning(carVal, engVal, carPowerSignalSeen, engineStateSignalSeen, lastPowerMode)) {
            applyEngineRunningSignal(engineSource);
        } else if (engineStartedThisDrive) {
            engineStartedThisDrive = false;
            log("Motor durdu algılandı (" + engineSource + "); aracın çalışması bekleniyor");
        }
    }

    private void applyPowerMode(int mode) {
        if (lastPowerMode == mode) {
            return;
        }
        int previous = lastPowerMode;
        lastPowerMode = mode;
        if (mode >= 1 && previous < 1) {
            ignitionOnAtMs = System.currentTimeMillis();
            driveReadySinceMs = ignitionOnAtMs;
            engineStartedThisDrive = false;
            awaitingRestartAfterPowerDown = false;
            firedThisDrive.clear();
            confirmedActive.clear();
            sendAttemptsThisPhase.clear();
            ruleActivatedAtMs.clear();
            if (previous == 0) {
                manualOverridesThisDrive.clear();
            }
            log("Konfor otomasyonu: araç hazır/kontak açık (PowerMode="
                    + mode + "), koşullar izleniyor");
            evaluate("power_mode");
        } else if (mode == 1 && previous >= 2) {
            ignitionOnAtMs = 0;
            driveReadySinceMs = 0;
            awaitingRestartAfterPowerDown = true;
            engineStartedThisDrive = false;
            firedThisDrive.clear();
            appliedWhileReady.clear();
            sendAttemptsThisPhase.clear();
            ruleActivatedAtMs.clear();
            log("PowerMode 2→1; otomasyon PowerMode 1'de iki saniyelik aralıklarla koşulları değerlendirecek");
        } else if (mode >= 2 && previous == 1) {
            if (awaitingRestartAfterPowerDown || driveReadySinceMs == 0L) {
                long nowMs = System.currentTimeMillis();
                ignitionOnAtMs = nowMs;
                driveReadySinceMs = nowMs;
                awaitingRestartAfterPowerDown = false;
                ruleActivatedAtMs.clear();
                log("Kontak yeniden açıldı (PowerMode 1→2); otomasyon kuralları tekrar değerlendiriliyor");
            }
            sendAttemptsThisPhase.clear();
            if (!appliedWhileReady.isEmpty()) {
                // PowerMode 1'de gönderilip araç tarafından hiç onaylanmayan (BCM'in kaçırdığı) komutlar
                // "manuel müdahale" sayılmış olabilir; bunlar kullanıcı kapatması değildir, yeniden denenmeli.
                for (String id : new HashSet<>(appliedWhileReady)) {
                    if (!confirmedActive.contains(id)) {
                        ruleActivatedAtMs.remove(id);
                        if (manualOverridesThisDrive.remove(id)) {
                            log("PowerMode 1'de onaylanmayan komut manuel müdahale sayılmayacak (" + id + "); yeniden denenecek");
                        }
                    }
                    awaitingActivationFeedback.remove(id);
                }
                log("Araç PowerMode 1'den 2'ye geçti; PowerMode 1'de gönderilen "
                        + appliedWhileReady.size() + " otomasyon komutu tekrar deneniyor");
                firedThisDrive.removeAll(appliedWhileReady);
                appliedWhileReady.clear();
            }
            evaluate("power_mode_2");
        } else if (mode == 0) {
            ignitionOnAtMs = 0;
            driveReadySinceMs = 0;
            awaitingRestartAfterPowerDown = false;
            engineStartedThisDrive = false;
            firedThisDrive.clear();
            appliedWhileReady.clear();
            confirmedActive.clear();
            sendAttemptsThisPhase.clear();
            manualOverridesThisDrive.clear();
            ruleActivatedAtMs.clear();
            ruleEnabledAtMs.clear();
        }
    }

    private void applyEngineRunningSignal(String source) {
        if (shouldRearmOnEngineRunning(lastPowerMode, engineStartedThisDrive, true)) {
            engineStartedThisDrive = true;
            awaitingRestartAfterPowerDown = false;
            driveReadySinceMs = System.currentTimeMillis();
            firedThisDrive.clear();
            appliedWhileReady.clear();
            confirmedActive.clear();
            sendAttemptsThisPhase.clear();
            manualOverridesThisDrive.clear();
            awaitingActivationFeedback.clear();
            ruleActivatedAtMs.clear();
            log("Motor çalışıyor algılandı (" + source
                    + "); konfor otomasyonu kuralları değerlendiriliyor");
            evaluate("car_power_run");
        }
    }

    private void evaluate(String reason) {
        if (!ClimateAutomationStore.isMasterEnabled(prefs)) {
            logEvaluationGate("Ana otomasyon anahtarı kapalı; tetikleme yapılmadı");
            return;
        }
        if (!isConnected()) {
            logEvaluationGate("Araç VDBus bağlantısı yok; tetikleme bekliyor");
            return;
        }
        if (lastPowerMode < 1) {
            logEvaluationGate("Araç PowerMode=" + lastPowerMode
                    + "; araç gücü / kontak (PowerMode>=1) bekleniyor");
            return;
        }
        if (awaitingRestartAfterPowerDown) {
            logEvaluationGate("Araç stop edildi (PowerMode 2→1); yeni sürüş (PowerMode 2) bekleniyor");
            return;
        }
        if (!engineStartedThisDrive) {
            logEvaluationGate("Kontak açık (PowerMode=" + lastPowerMode
                    + "); aracın çalışması (ID_CAR_POWER_MODE=4 / ID_ENGINE_STATE=1) bekleniyor");
            return;
        }
        Double temp = outsideTempC;
        if (temp == null) {
            logEvaluationGate("Dış sıcaklık okunmadı; sıcaklık verisi bekleniyor");
            return;
        }
        logEvaluationGate("");
        long now = System.currentTimeMillis();
        if (ignitionOnAtMs == 0) {
            ignitionOnAtMs = now;
        }
        if (driveReadySinceMs == 0L) {
            driveReadySinceMs = now;
        }
        for (String id : ClimateAutomationStore.ALL_IDS) {
            ClimateAutomationStore.Rule rule = ClimateAutomationStore.getRule(prefs, id);
            boolean conditionMatches = ClimateAutomationStore.matchesTemperatureWithHysteresis(
                    temp, rule.compare, rule.tempC, appliedThisDrive.contains(id));
            int startDelayRem = remainingStartDelayMinutes(
                    rule, effectiveStartDelayBase(id, driveReadySinceMs), now);
            boolean startDelayPending = startDelayRem > 0;
            boolean timedOut = isRuleTimedOut(id, rule, now);

            if ((!rule.enabled || !conditionMatches || startDelayPending || timedOut) && appliedThisDrive.contains(id)) {
                if (timedOut) {
                    log("Konfor otomasyonu kapatma süresi doldu (" + id
                            + "); açıldıktan sonra " + rule.delayMinutes + " dk tamamlandığından kapatılıyor");
                }
                deactivateRule(id);
                if (!rule.enabled || !conditionMatches || startDelayPending) {
                    ruleActivatedAtMs.remove(id);
                }
            }
            if (!rule.enabled) {
                firedThisDrive.remove(id);
                awaitingActivationFeedback.remove(id);
                manualOverridesThisDrive.remove(id);
                sendAttemptsThisPhase.remove(id);
                ruleActivatedAtMs.remove(id);
                logRuleEvaluationState(id, "kural kapalı");
                continue;
            }
            if (!conditionMatches) {
                firedThisDrive.remove(id);
                awaitingActivationFeedback.remove(id);
                manualOverridesThisDrive.remove(id);
                sendAttemptsThisPhase.remove(id);
                ruleActivatedAtMs.remove(id);
                logRuleEvaluationState(id, "sıcaklık koşulu eşleşmiyor");
                continue;
            }
            if (startDelayPending) {
                firedThisDrive.remove(id);
                awaitingActivationFeedback.remove(id);
                sendAttemptsThisPhase.remove(id);
                ruleActivatedAtMs.remove(id);
                logRuleEvaluationState(id, "açılış gecikmesi bekleniyor (" + startDelayRem + " dk kaldı)");
                continue;
            }
            if (timedOut) {
                awaitingActivationFeedback.remove(id);
                logRuleEvaluationState(id, "kapatma süresi doldu (" + rule.delayMinutes + " dk)");
                continue;
            }
            if (manualOverridesThisDrive.contains(id)) {
                continue;
            }
            Boolean activationFeedback = readActivationFeedback(id, rule);
            if (Boolean.TRUE.equals(activationFeedback)) {
                confirmedActive.add(id);
                awaitingActivationFeedback.remove(id);
                feedbackReadErrorsReported.remove(id);
                lastCommandErrors.remove(id);
                ruleActivatedAtMs.putIfAbsent(id, now);
                if (!firedThisDrive.contains(id)) {
                    firedThisDrive.add(id);
                    appliedThisDrive.add(id);
                    if (lastPowerMode == 1) {
                        appliedWhileReady.add(id);
                    }
                    lastRuleEvaluationStates.remove(id);
                    lastAction = id;
                    log("Konfor otomasyonu araç geri bildirimiyle doğrulandı: " + id
                            + " (dış ortam " + String.format(java.util.Locale.getDefault(), "%.1f", temp)
                            + "°C, PowerMode=" + lastPowerMode + ", " + reason + ")");
                }
                continue;
            }
            if (firedThisDrive.contains(id)) {
                if (shouldDetectManualOverride(
                        confirmedActive.contains(id),
                        Boolean.FALSE.equals(activationFeedback))
                        && manualOverridesThisDrive.add(id)) {
                    awaitingActivationFeedback.remove(id);
                    log("Manuel müdahale algılandı (" + id
                            + "); otomasyon bu sürüşte yeniden açmayacak");
                }
                continue;
            }
            // Karşıt kural (ısıtma <-> havalandırma) önceden açılmışsa onu derhal kapat
            String partnerId = ClimateAutomationStore.conflictPartnerOf(id);
            if (partnerId != null && appliedThisDrive.contains(partnerId)) {
                log("Karşıt kural aktif (" + partnerId + "); yeni kural (" + id + ") için kapatılıyor");
                deactivateRule(partnerId);
                ruleActivatedAtMs.remove(partnerId);
            }
            if (applyRule(id, rule)) {
                lastCommandErrors.remove(id);
                appliedThisDrive.add(id);
                lastSentElapsedMs.put(id, SystemClock.elapsedRealtime());
                if (lastPowerMode == 1) {
                    appliedWhileReady.add(id);
                }
                int attempts = sendAttemptsThisPhase.getOrDefault(id, 0) + 1;
                sendAttemptsThisPhase.put(id, attempts);
                lastRuleEvaluationStates.remove(id);
                lastAction = id;
                if (awaitingActivationFeedback.add(id)) {
                    log("Konfor otomasyonu komutu gönderildi: " + id
                            + " (dış ortam " + String.format(java.util.Locale.getDefault(), "%.1f", temp)
                            + "°C, PowerMode=" + lastPowerMode + ", " + reason + ")");
                }
                // Başlangıçtaki 3 deneme (0s, 2s, 4s) tamamlandığında komutu bu sürüş için kilitle;
                // böylece araç ekranından manuel kapatıldığında veya donanım desteklemediğinde sonsuz tekrar yapılmaz.
                if (attempts >= MAX_STARTUP_SEND_ATTEMPTS) {
                    firedThisDrive.add(id);
                    awaitingActivationFeedback.remove(id);
                    lastRuleEvaluationStates.remove(id);
                    lastAction = id;
                }
            }
        }
    }

    private Boolean readActivationFeedback(String id, ClimateAutomationStore.Rule rule) {
        switch (id) {
            case ClimateAutomationStore.DRIVER_HEAT:
                return seatFeedbackMatches(id, CarSettingID.ID_CAR_DRIVER_SEAT_HEAT, rule.level);
            case ClimateAutomationStore.PASSENGER_HEAT:
                return seatFeedbackMatches(id, CarSettingID.ID_CAR_COPILOT_SEAT_HEAT, rule.level);
            case ClimateAutomationStore.DRIVER_VENT:
                return seatFeedbackMatches(id, CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION, rule.level);
            case ClimateAutomationStore.PASSENGER_VENT:
                return seatFeedbackMatches(id, CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION, rule.level);
            case ClimateAutomationStore.STEER_HEAT:
                return feedbackMatches(id, MODULE_SETTING, CarSettingID.ID_CAR_STEER_WHEEL_HEAT, 1);
            case ClimateAutomationStore.WINDSHIELD:
                if (ClimateAutomationStore.isWindshieldHeatOnly(prefs)) {
                    return feedbackMatches(id, MODULE_HVAC, HvacID.ID_AC_FRONT_GLASS_HEAT, 1);
                }
                return anyFeedbackMatches(
                        feedbackMatches(id, MODULE_HVAC, HvacID.ID_AC_FRONT_GLASS_HEAT, 1),
                        feedbackMatches(id, MODULE_HVAC, HvacID.ID_AC_MAX_FRONT_DEFOREST, 1));
            case ClimateAutomationStore.REAR_DEFROST:
                return anyFeedbackMatches(
                        feedbackMatches(id, MODULE_HVAC, HvacID.ID_AC_REAR_DEFROST, 1),
                        feedbackMatches(id, MODULE_SETTING, CarSettingID.ID_CAR_REAR_WINDOW_HEAT, 1));
            case ClimateAutomationStore.CLIMATE:
                return allFeedbackMatches(
                        feedbackMatches(id, MODULE_HVAC, HvacID.ID_AC_STATE, 1),
                        feedbackMatches(id, MODULE_HVAC, HvacID.ID_AC_WORKING, 1),
                        feedbackMatches(id, MODULE_HVAC, HvacID.ID_AC_AUTO_STATE, 1));
            default:
                return null;
        }
    }

    private Boolean seatFeedbackMatches(String ruleId, int itemId, int uiLevel) {
        try {
            int[] values = CarInfoProxy.getInstance().getItemValues(MODULE_SETTING, itemId);
            if (values == null || values.length == 0) {
                return null;
            }
            return evaluateSeatFeedback(values[0], uiLevel, appliedThisDrive.contains(ruleId));
        } catch (Throwable t) {
            if (feedbackReadErrorsReported.add(ruleId)) {
                log("Konfor otomasyonu geri bildirimi okunamadı (" + ruleId + "): "
                        + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
            }
            return null;
        }
    }

    private Boolean feedbackMatches(String ruleId, int module, int itemId, int expectedValue) {
        try {
            int[] values = CarInfoProxy.getInstance().getItemValues(module, itemId);
            return values == null || values.length == 0 ? null : values[0] == expectedValue;
        } catch (Throwable t) {
            if (feedbackReadErrorsReported.add(ruleId)) {
                log("Konfor otomasyonu geri bildirimi okunamadı (" + ruleId + "): "
                        + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
            }
            return null;
        }
    }

    private Boolean anyFeedbackMatches(Boolean... feedback) {
        boolean anyFalse = false;
        for (Boolean value : feedback) {
            if (Boolean.TRUE.equals(value)) {
                return true;
            }
            if (Boolean.FALSE.equals(value)) {
                anyFalse = true;
            }
        }
        return anyFalse ? false : null;
    }

    private Boolean allFeedbackMatches(Boolean... feedback) {
        boolean unknown = false;
        for (Boolean value : feedback) {
            if (Boolean.FALSE.equals(value)) {
                return false;
            }
            if (value == null) {
                unknown = true;
            }
        }
        return unknown ? null : true;
    }

    private void logEvaluationGate(String state) {
        if (state.equals(lastEvaluationGate)) {
            return;
        }
        lastEvaluationGate = state;
        if (!state.isEmpty()) {
            log("Konfor otomasyonu: " + state);
        }
    }

    private void logRuleEvaluationState(String id, String state) {
        if (state.equals(lastRuleEvaluationStates.put(id, state))) {
            return;
        }
        ClimateAutomationStore.Rule rule = ClimateAutomationStore.getRule(prefs, id);
        log("Konfor otomasyonu beklemede (" + id + "): " + state
                + ", dış sıcaklık=" + String.format(java.util.Locale.getDefault(), "%.1f", outsideTempC)
                + "°C, koşul=" + rule.tempC + "°C");
    }

    private boolean applyRule(String id, ClimateAutomationStore.Rule rule) {
        try {
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            switch (id) {
                case ClimateAutomationStore.DRIVER_HEAT:
                    proxy.sendItemValue(MODULE_SETTING, CarSettingID.ID_CAR_DRIVER_SEAT_HEAT, wireSeatLevel(rule.level));
                    break;
                case ClimateAutomationStore.PASSENGER_HEAT:
                    proxy.sendItemValue(MODULE_SETTING, CarSettingID.ID_CAR_COPILOT_SEAT_HEAT, wireSeatLevel(rule.level));
                    break;
                case ClimateAutomationStore.DRIVER_VENT:
                    proxy.sendItemValue(MODULE_SETTING, CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION, wireSeatLevel(rule.level));
                    break;
                case ClimateAutomationStore.PASSENGER_VENT:
                    proxy.sendItemValue(MODULE_SETTING, CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION, wireSeatLevel(rule.level));
                    break;
                case ClimateAutomationStore.STEER_HEAT: {
                    int[] current = proxy.getItemValues(MODULE_SETTING, CarSettingID.ID_CAR_STEER_WHEEL_HEAT);
                    Integer cmd = resolveSteeringWheelHeatCommand(true, current);
                    if (cmd != null) {
                        proxy.sendItemValue(MODULE_SETTING, CarSettingID.ID_CAR_STEER_WHEEL_HEAT, cmd);
                    }
                    break;
                }
                case ClimateAutomationStore.WINDSHIELD:
                    proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_FRONT_GLASS_HEAT, 1);
                    if (!ClimateAutomationStore.isWindshieldHeatOnly(prefs)) {
                        proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_MAX_FRONT_DEFOREST, 1);
                    }
                    break;
                case ClimateAutomationStore.REAR_DEFROST: {
                    int[] hvacCur = proxy.getItemValues(MODULE_HVAC, HvacID.ID_AC_REAR_DEFROST);
                    int[] setCur = proxy.getItemValues(MODULE_SETTING, CarSettingID.ID_CAR_REAR_WINDOW_HEAT);
                    boolean isOn = (hvacCur != null && hvacCur.length > 0 && hvacCur[0] == 1)
                            || (setCur != null && setCur.length > 0 && setCur[0] == 1);
                    if (!isOn) {
                        proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_REAR_DEFROST, 1);
                    }
                    break;
                }
                case ClimateAutomationStore.CLIMATE:
                    proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_STATE, 1);
                    proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_WORKING, 1);
                    proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_AUTO_STATE, 1);
                    break;
                default:
                    return false;
            }
            if (ClimateAutomationStore.usesHeatLevel(id)) {
                Log.d(TAG, "Koltuk kademe komutu: " + id + ", seçilen kademe="
                        + rule.level + ", araca gönderilen değer=" + wireSeatLevel(rule.level));
            }
            return true;
        } catch (Throwable t) {
            String error = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            if (!error.equals(lastCommandErrors.put(id, error))) {
                log("Konfor otomasyonu gönderilemedi (" + id + "): " + error);
            }
            return false;
        }
    }

    private void updateOutsideTemperature(int raw, String source) {
        Double decoded = decodeOutsideCelsius(raw);
        if (decoded == null) {
            log("Dış sıcaklık sinyali çözümlenemedi: MODULE_HVAC(" + MODULE_HVAC
                    + "), ID_AC_TEMPERATURE_CELSIUS_OUTSIDE("
                    + HvacID.ID_AC_TEMPERATURE_CELSIUS_OUTSIDE + "), raw=" + raw);
            return;
        }
        Double previous = outsideTempC;
        boolean rawChanged = lastRawOutsideTemp != raw;
        outsideTempC = decoded;
        lastRawOutsideTemp = raw;
        if (rawChanged) {
            log("Dış sıcaklık sinyali: MODULE_HVAC(" + MODULE_HVAC
                    + "), ID_AC_TEMPERATURE_CELSIUS_OUTSIDE("
                    + HvacID.ID_AC_TEMPERATURE_CELSIUS_OUTSIDE + "), raw=" + raw
                    + ", çözümlenen="
                    + String.format(java.util.Locale.getDefault(), "%.1f", decoded)
                    + "°C, kaynak=" + source);
        }
        if (!decoded.equals(previous)) {
            evaluate(source);
            notifyListeners();
        }
    }

    private void logOutsideFahrenheit(int raw, String source) {
        if (lastRawOutsideTempF == raw) {
            return;
        }
        lastRawOutsideTempF = raw;
        log("Dış sıcaklık Fahrenheit sinyali: MODULE_HVAC(" + MODULE_HVAC
                + "), ID_AC_TEMPERATURE_FAHRENHEIT_OUTSIDE("
                + HvacID.ID_AC_TEMPERATURE_FAHRENHEIT_OUTSIDE + "), raw=" + raw
                + ", kaynak=" + source);
    }

    private void deactivateRule(String id) {
        awaitingActivationFeedback.remove(id);
        manualOverridesThisDrive.remove(id);
        sendAttemptsThisPhase.remove(id);
        try {
            if (!isConnected()) {
                pendingDeactivation.add(id);
                return;
            }
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            lastSentElapsedMs.put(id, SystemClock.elapsedRealtime());
            switch (id) {
                case ClimateAutomationStore.DRIVER_HEAT:
                    proxy.sendItemValue(MODULE_SETTING, CarSettingID.ID_CAR_DRIVER_SEAT_HEAT, 1);
                    break;
                case ClimateAutomationStore.PASSENGER_HEAT:
                    proxy.sendItemValue(MODULE_SETTING, CarSettingID.ID_CAR_COPILOT_SEAT_HEAT, 1);
                    break;
                case ClimateAutomationStore.DRIVER_VENT:
                    proxy.sendItemValue(MODULE_SETTING, CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION, 1);
                    break;
                case ClimateAutomationStore.PASSENGER_VENT:
                    proxy.sendItemValue(MODULE_SETTING, CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION, 1);
                    break;
                case ClimateAutomationStore.STEER_HEAT: {
                    int[] current = proxy.getItemValues(MODULE_SETTING, CarSettingID.ID_CAR_STEER_WHEEL_HEAT);
                    Integer cmd = resolveSteeringWheelHeatCommand(false, current);
                    if (cmd != null) {
                        // Chery OEM ID_CAR_STEER_WHEEL_HEAT (160) toggle çalışır: açıkken (1) kapatmak için de 1 gönderilir; 0 etkisizdir.
                        proxy.sendItemValue(MODULE_SETTING, CarSettingID.ID_CAR_STEER_WHEEL_HEAT, cmd);
                    }
                    break;
                }
                case ClimateAutomationStore.WINDSHIELD:
                    proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_FRONT_GLASS_HEAT, 2);
                    if (!ClimateAutomationStore.isWindshieldHeatOnly(prefs)) {
                        proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_MAX_FRONT_DEFOREST, 2);
                    }
                    break;
                case ClimateAutomationStore.REAR_DEFROST: {
                    int[] hvacCur = proxy.getItemValues(MODULE_HVAC, HvacID.ID_AC_REAR_DEFROST);
                    int[] setCur = proxy.getItemValues(MODULE_SETTING, CarSettingID.ID_CAR_REAR_WINDOW_HEAT);
                    boolean isOn = (hvacCur != null && hvacCur.length > 0 && hvacCur[0] == 1)
                            || (setCur != null && setCur.length > 0 && setCur[0] == 1);
                    if (isOn) {
                        // 0 ve 2 ile kapanmadığı için direksiyon ısıtması gibi toggle (1) ile kapatılır
                        proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_REAR_DEFROST, 1);
                    }
                    break;
                }
                case ClimateAutomationStore.CLIMATE:
                    proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_STATE, 0);
                    proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_WORKING, 0);
                    proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_AUTO_STATE, 0);
                    break;
                default:
                    return;
            }
            firedThisDrive.remove(id);
            appliedThisDrive.remove(id);
            appliedWhileReady.remove(id);
            confirmedActive.remove(id);
            pendingDeactivation.remove(id);
            log("Konfor otomasyonu kapatma komutu gönderildi: " + id);
        } catch (Throwable t) {
            pendingDeactivation.add(id);
            log("Konfor otomasyonu kapatılamadı (" + id + "): " + t.getMessage());
        }
    }

    private void retryPendingDeactivations() {
        if (!isConnected() || pendingDeactivation.isEmpty()) {
            return;
        }
        for (String id : new HashSet<>(pendingDeactivation)) {
            deactivateRule(id);
        }
    }

    private int wireSeatLevel(int uiLevel) {
        return Math.max(ClimateAutomationStore.LEVEL_MIN,
                Math.min(ClimateAutomationStore.LEVEL_MAX, uiLevel)) + 1;
    }

    private boolean isRuleTimedOut(String id, ClimateAutomationStore.Rule rule, long nowMs) {
        Long activatedAt = ruleActivatedAtMs.get(id);
        return isRuleTimedOut(rule, activatedAt != null ? activatedAt : 0L, nowMs);
    }

    static boolean isRuleTimedOut(ClimateAutomationStore.Rule rule, long activatedAtMs, long nowMs) {
        return rule != null
                && rule.delayMinutes > 0
                && activatedAtMs > 0
                && (nowMs - activatedAtMs) >= (rule.delayMinutes * 60_000L);
    }

    /**
     * Chery MCU protokolünde ID_CAR_STEER_WHEEL_HEAT (160) toggle (1) ile çalışır:
     * durum 1 (açık) iken kapatmak için 1, 1 değilken (kapalı) açmak için 1 gönderilir;
     * 0 göndermek direksiyon ısıtmayı kapatmaz. Zaten hedef durumdaysa çift toggle
     * olmaması için null döner.
     */
    public static Integer resolveSteeringWheelHeatCommand(boolean turnOn, int[] currentValues) {
        boolean isCurrentlyOn = currentValues != null && currentValues.length > 0 && currentValues[0] == 1;
        if (turnOn) {
            return isCurrentlyOn ? null : 1;
        }
        return isCurrentlyOn ? 1 : null;
    }

    private void registerHvacCallback() {
        if (callbacksRegistered || !isConnected()) {
            return;
        }
        try {
            CarInfoProxy.getInstance().regCallBack(MODULE_HVAC, hvacNotify, HVAC_IDS);
            CarInfoProxy.getInstance().regCallBack(MODULE_SETTING, settingNotify, SETTING_IDS);
            CarInfoProxy.getInstance().regCallBack(MODULE_READONLY, readonlyNotify, READONLY_IDS);
            callbacksRegistered = true;
        } catch (Throwable t) {
            Log.w(TAG, "HVAC subscribe failed", t);
        }
    }

    private boolean isConnected() {
        try {
            return CarInfoProxy.getInstance().isServiceConnnected();
        } catch (Throwable t) {
            return false;
        }
    }

    private void notifyListeners() {
        if (listeners.isEmpty()) {
            return;
        }
        Status status = currentStatus();
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onClimateStatus(status);
            }
        });
    }

    private void log(String message) {
        Log.d(TAG, message);
        Intent intent = new Intent(ACTION_LOG);
        intent.putExtra(EXTRA_LOG_MESSAGE, message);
        appContext.sendBroadcast(intent);
    }
}
