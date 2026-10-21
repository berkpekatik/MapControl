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

import java.util.HashSet;
import java.util.HashMap;
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

    public static final class Status {
        public final boolean connected;
        public final boolean masterEnabled;
        public final boolean powerOn;
        public final Double outsideTempC;
        public final String lastAction;

        Status(boolean connected, boolean masterEnabled, boolean powerOn,
               Double outsideTempC, String lastAction) {
            this.connected = connected;
            this.masterEnabled = masterEnabled;
            this.powerOn = powerOn;
            this.outsideTempC = outsideTempC;
            this.lastAction = lastAction;
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
    private final Set<String> awaitingActivationFeedback = new HashSet<>();
    private final Set<String> feedbackReadErrorsReported = new HashSet<>();
    private final Map<String, Integer> sendAttemptsThisPhase = new HashMap<>();
    private final Map<String, Long> lastSentElapsedMs = new HashMap<>();
    private final Map<String, String> lastRuleEvaluationStates = new HashMap<>();
    private final Map<String, String> lastCommandErrors = new HashMap<>();
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor();

    private volatile boolean started;
    private volatile boolean callbacksRegistered;
    private volatile Double outsideTempC;
    private volatile int lastRawOutsideTemp = Integer.MIN_VALUE;
    private volatile int lastRawOutsideTempF = Integer.MIN_VALUE;
    private volatile int lastPowerMode = -1;
    private volatile long ignitionOnAtMs;
    private volatile boolean awaitingRestartAfterPowerDown;
    private volatile String lastAction = "";
    private String lastEvaluationGate = "";
    private String lastPollError = "";
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
                        awaitingRestartAfterPowerDown = false;
                        firedThisDrive.clear();
                        appliedThisDrive.clear();
                        appliedWhileReady.clear();
                        sendAttemptsThisPhase.clear();
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
        awaitingRestartAfterPowerDown = false;
        firedThisDrive.clear();
        appliedThisDrive.clear();
        appliedWhileReady.clear();
        sendAttemptsThisPhase.clear();
        lastSuccessfulPollElapsedMs = SystemClock.elapsedRealtime();
        log("Konfor otomasyonu: 30 saniyeden uzun izleme kesintisi; "
                + "araç durumu ve otomasyon kuralları yeniden değerlendiriliyor");
    }

    public void notifyRulesChanged() {
        io.execute(() -> {
            firedThisDrive.clear();
            sendAttemptsThisPhase.clear();
            manualOverridesThisDrive.clear();
            refreshFromBus();
            evaluate("rules");
            notifyListeners();
        });
    }

    public void notifyRuleChanged(String id) {
        io.execute(() -> {
            manualOverridesThisDrive.remove(id);
            sendAttemptsThisPhase.remove(id);
            refreshFromBus();
            ClimateAutomationStore.Rule rule = ClimateAutomationStore.getRule(prefs, id);
            Double temp = outsideTempC;
            boolean eligible = rule.enabled
                    && ClimateAutomationStore.isMasterEnabled(prefs)
                    && isConnected()
                    && lastPowerMode >= 1
                    && temp != null
                    && ClimateAutomationStore.matchesTemperature(temp, rule.compare, rule.tempC);
            if (!eligible) {
                if (appliedThisDrive.contains(id)) {
                    deactivateRule(id);
                }
                firedThisDrive.remove(id);
            } else if (appliedThisDrive.contains(id)) {
                if (applyRule(id, rule)) {
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
            notifyListeners();
        });
    }

    public Status currentStatus() {
        return new Status(
                isConnected(),
                ClimateAutomationStore.isMasterEnabled(prefs),
                lastPowerMode >= 1,
                outsideTempC,
                lastAction);
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
        if (ClimateAutomationStore.usesHeatLevel(ruleId)) {
            // Koltuk ısıtma / soğutma: 0 veya 1 = kapalı
            isOffNotification = (raw == 0 || raw == 1);
        } else {
            isOffNotification = (raw == 0);
        }
        if (isOffNotification) {
            firedThisDrive.add(ruleId);
            awaitingActivationFeedback.remove(ruleId);
            if (manualOverridesThisDrive.add(ruleId)) {
                log("Manuel müdahale algılandı (" + ruleId
                        + "); otomasyon bu sürüşte yeniden açmayacak");
            }
        } else if (raw > 0) {
            // Araç özelliği aktif ettiğini bildirdi; artık tekrar göndermeye gerek yok
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
        int[] pwr = proxy.getItemValues(MODULE_READONLY, ReadOnlyID.ID_SYSTEM_POWER_MODE);
        if (pwr != null && pwr.length > 0) {
            applyPowerMode(pwr[0]);
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
            firedThisDrive.clear();
            sendAttemptsThisPhase.clear();
            if (previous == 0) {
                manualOverridesThisDrive.clear();
            }
            log("Konfor otomasyonu: araç hazır/kontak açık (PowerMode="
                    + mode + "), koşullar izleniyor");
            evaluate("power_mode");
        } else if (mode == 1 && previous >= 2) {
            ignitionOnAtMs = 0;
            awaitingRestartAfterPowerDown = true;
            firedThisDrive.clear();
            appliedWhileReady.clear();
            sendAttemptsThisPhase.clear();
            log("PowerMode 2→1; otomasyon PowerMode 1'de iki saniyelik aralıklarla koşulları değerlendirecek");
        } else if (mode >= 2 && previous == 1) {
            if (awaitingRestartAfterPowerDown) {
                ignitionOnAtMs = System.currentTimeMillis();
                awaitingRestartAfterPowerDown = false;
                log("Kontak yeniden açıldı (PowerMode 1→2); otomasyon kuralları tekrar değerlendiriliyor");
            }
            sendAttemptsThisPhase.clear();
            if (!appliedWhileReady.isEmpty()) {
                log("Araç PowerMode 1'den 2'ye geçti; PowerMode 1'de gönderilen "
                        + appliedWhileReady.size() + " otomasyon komutu tekrar deneniyor");
                firedThisDrive.removeAll(appliedWhileReady);
                appliedWhileReady.clear();
            }
            evaluate("power_mode_2");
        } else if (mode == 0) {
            ignitionOnAtMs = 0;
            awaitingRestartAfterPowerDown = false;
            firedThisDrive.clear();
            appliedWhileReady.clear();
            sendAttemptsThisPhase.clear();
            manualOverridesThisDrive.clear();
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
                    + "; kontak/araç hazır durumu bekleniyor");
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
        for (String id : ClimateAutomationStore.ALL_IDS) {
            ClimateAutomationStore.Rule rule = ClimateAutomationStore.getRule(prefs, id);
            boolean conditionMatches = ClimateAutomationStore.matchesTemperature(
                    temp, rule.compare, rule.tempC);
            boolean timedOut = (rule.delayMinutes > 0
                    && ignitionOnAtMs > 0
                    && (now - ignitionOnAtMs) >= (rule.delayMinutes * 60_000L));

            if ((!rule.enabled || !conditionMatches || timedOut) && appliedThisDrive.contains(id)) {
                if (timedOut) {
                    log("Konfor otomasyonu kapatma süresi doldu (" + id
                            + "); kontak sonrası " + rule.delayMinutes + " dk tamamlandığından kapatılıyor");
                }
                deactivateRule(id);
            }
            if (!rule.enabled) {
                awaitingActivationFeedback.remove(id);
                manualOverridesThisDrive.remove(id);
                sendAttemptsThisPhase.remove(id);
                logRuleEvaluationState(id, "kural kapalı");
                continue;
            }
            if (!conditionMatches) {
                awaitingActivationFeedback.remove(id);
                manualOverridesThisDrive.remove(id);
                sendAttemptsThisPhase.remove(id);
                logRuleEvaluationState(id, "sıcaklık koşulu eşleşmiyor");
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
                awaitingActivationFeedback.remove(id);
                feedbackReadErrorsReported.remove(id);
                lastCommandErrors.remove(id);
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
                if (Boolean.FALSE.equals(activationFeedback)
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
                if (awaitingActivationFeedback.add(id)) {
                    log("Konfor otomasyonu komutu gönderildi: " + id
                            + " (dış ortam " + String.format(java.util.Locale.getDefault(), "%.1f", temp)
                            + "°C, PowerMode=" + lastPowerMode + ", " + reason + ")");
                }
                // Başlangıçtaki 3 deneme (0s, 2s, 4s) tamamlandığında komutu bu sürüş için kilitle;
                // böylece araç ekranından manuel kapatıldığında otomasyon tekrar açmaz.
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
            int v = values[0];
            int wire = wireSeatLevel(uiLevel);
            // Araç geri bildirimde ya wire değerini (2..4) ya da doğrudan kademe değerini (2..3) döner
            if (v == wire || (uiLevel >= 2 && v == uiLevel)) {
                return true;
            }
            return false;
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
                case ClimateAutomationStore.STEER_HEAT:
                    proxy.sendItemValue(MODULE_SETTING, CarSettingID.ID_CAR_STEER_WHEEL_HEAT, 1);
                    break;
                case ClimateAutomationStore.WINDSHIELD:
                    proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_FRONT_GLASS_HEAT, 1);
                    proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_MAX_FRONT_DEFOREST, 1);
                    break;
                case ClimateAutomationStore.REAR_DEFROST:
                    proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_REAR_DEFROST, 1);
                    proxy.sendItemValue(MODULE_SETTING, CarSettingID.ID_CAR_REAR_WINDOW_HEAT, 1);
                    break;
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
                case ClimateAutomationStore.STEER_HEAT:
                    proxy.sendItemValue(MODULE_SETTING, CarSettingID.ID_CAR_STEER_WHEEL_HEAT, 0);
                    break;
                case ClimateAutomationStore.WINDSHIELD:
                    proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_FRONT_GLASS_HEAT, 0);
                    proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_MAX_FRONT_DEFOREST, 0);
                    break;
                case ClimateAutomationStore.REAR_DEFROST:
                    proxy.sendItemValue(MODULE_HVAC, HvacID.ID_AC_REAR_DEFROST, 0);
                    proxy.sendItemValue(MODULE_SETTING, CarSettingID.ID_CAR_REAR_WINDOW_HEAT, 0);
                    break;
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

    private void registerHvacCallback() {
        if (callbacksRegistered || !isConnected()) {
            return;
        }
        try {
            CarInfoProxy.getInstance().regCallBack(MODULE_HVAC, hvacNotify, HVAC_IDS);
            CarInfoProxy.getInstance().regCallBack(MODULE_SETTING, settingNotify, SETTING_IDS);
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
