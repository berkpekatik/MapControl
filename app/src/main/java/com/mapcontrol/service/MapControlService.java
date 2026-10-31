package com.mapcontrol.service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID;
import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.Context;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.IOException;
import android.media.MediaPlayer;
import android.net.Uri;
import com.mapcontrol.api.ProfileApiService;
import com.mapcontrol.nav.GoogleMapsNavNotificationCoordinator;
import com.mapcontrol.audio.AudioEqualizerController;
import com.mapcontrol.ui.activity.MainActivity;
import com.mapcontrol.util.AppLaunchHelper;
import com.mapcontrol.util.BootSession;
import com.mapcontrol.util.StartupGate;
import com.mapcontrol.ui.welcome.WelcomeDesignCatalog;
import com.mapcontrol.ui.welcome.WelcomeScreenPresenter;
import com.mapcontrol.util.DisplayHelper;
import com.mapcontrol.util.ClusterNavigationState;
import com.mapcontrol.util.TargetPackageStore;
import com.mapcontrol.util.SplitScreenStore;
import com.mapcontrol.manager.SplitScreenGestureController;
import com.mapcontrol.manager.CarSystemGestureController;
import com.mapcontrol.manager.ClusterDisplayManager;
import com.mapcontrol.manager.FloatingBackButtonManager;
import com.mapcontrol.manager.MapControlVDBusKeyBridge;
import com.mapcontrol.R;
import com.mapcontrol.vehicle.ClimateAutomationEngine;
import com.mapcontrol.vehicle.SunroofAutomationEngine;
import com.mapcontrol.vehicle.SunroofGestureOverlayManager;
import android.content.pm.PackageManager;

public class MapControlService extends Service {
    private static final String CHANNEL_ID = "MapControlServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "MapControlService";
    private static final int UI_LAUNCH_ATTEMPTS = 8;
    private static final long UI_LAUNCH_RETRY_MS = 1000L;

    /** Bench: {@link com.mapcontrol.manager.ClusterVDBusBenchManager} — power modu olmadan cluster aç. */
    public static final String ACTION_BENCH_OPEN_CLUSTER = "com.mapcontrol.action.BENCH_OPEN_CLUSTER";
    /** Bench: cluster kapat (servis içi yol). */
    public static final String ACTION_BENCH_CLOSE_CLUSTER = "com.mapcontrol.action.BENCH_CLOSE_CLUSTER";
    /**
     * Google Maps navigasyon bildirimi. Cluster yalnızca güç modu hazır (1)
     * veya motor (2) iken açılır; kapalıyken VDBus çağrısı yapılmaz.
     */
    public static final String ACTION_OPEN_CLUSTER_FROM_MAPS_NOTIFICATION =
            "com.mapcontrol.action.OPEN_CLUSTER_FROM_MAPS_NOTIFICATION";
    /**
     * {@link #ACTION_BENCH_CLOSE_CLUSTER} ile: {@code false} = HOME yok, uygulama önde kalır (yüzen yansıtma çubuğu).
     * Varsayılan {@code true} (yansıtma sekmesi Durdur / bench / güç modu).
     */
    public static final String EXTRA_CLUSTER_CLOSE_SEND_BACKGROUND = "com.mapcontrol.extra.CLUSTER_CLOSE_SEND_BACKGROUND";
    private static final String ACTION_LOG = "com.mapcontrol.LOG_MESSAGE";
    private static final String EXTRA_LOG_MESSAGE = "log_message";
    private ScheduledExecutorService scheduler;
    private Handler handler;
    /** Yansıtma paneli / yüzen kontroller / power modu ile aynı cluster VDBus ve taşıma mantığı ({@link ClusterDisplayManager}). */
    private ClusterDisplayManager clusterDisplayHelper;
    private int lastPowerMode = -1;
    /** Hoşgeldin: ekran güç alınca bir kez. 1→2 geçişinde tekrar açılmaz. */
    private int lastWelcomePowerMode = -1;
    private int lastAppliedDriveMode = -1; // Son uygulanan sürüş modu (tekrar uygulamayı önlemek için)
    private int uiLaunchAttempts;
    private long nextUiLaunchAt;
    private CarSystemGestureController carSystemGestureController;
    private SharedPreferences.OnSharedPreferenceChangeListener gesturePrefListener;
    private SplitScreenGestureController splitScreenGestureController;
    private SharedPreferences.OnSharedPreferenceChangeListener splitGesturePrefListener;
    
    /**
     * Log mesajını MainActivity'ye gönder (sistem kayıtlarına)
     */
    private void log(String msg) {
        // Android Log'a da yaz (debug için)
        Log.d(TAG, msg);
        
        // MainActivity'ye broadcast gönder
        Intent intent = new Intent(ACTION_LOG);
        intent.putExtra(EXTRA_LOG_MESSAGE, msg);
        sendBroadcast(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        BootSession.noteServiceStart(this);
        handler = new Handler(Looper.getMainLooper());
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduleClusterBootSplash();
        scheduleEqualizerKeeper();

        // Power mode kontrolünü başlat
        com.mapcontrol.dvr.DvrController.get(this).start();
        startPowerModeMonitor();
        ClimateAutomationEngine.getInstance(this).start();
        SunroofAutomationEngine.getInstance(this).start();
        SunroofGestureOverlayManager.getInstance(this).startIfEnabled();
        if (DmsFaceRecognitionManager.isEnabled(this)
                && checkSelfPermission(android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                DmsFaceRecognitionManager.start(this);
            } catch (RuntimeException e) {
                log("Yuz tanima arka planda yeniden baslatilamadi: " + e.getMessage());
            }
        }
        MapControlVDBusKeyBridge.acquire(this);
        scheduler.scheduleAtFixedRate(
                () -> MapControlVDBusKeyBridge.refreshSubscription(false, false),
                30, 30, TimeUnit.SECONDS);
        carSystemGestureController = new CarSystemGestureController(this, new CarSystemGestureController.Host() {
            @Override
            public void openCluster() {
                openClusterDisplay();
            }

            @Override
            public void closeCluster() {
                closeClusterDisplay(true);
            }
        });
        gesturePrefListener = (sharedPreferences, key) -> {
            if (key == null || CarSystemGestureController.PREF_ENABLED.equals(key)) {
                if (carSystemGestureController != null) {
                    carSystemGestureController.syncRegistration();
                }
            }
        };
        TargetPackageStore.prefs(this).registerOnSharedPreferenceChangeListener(gesturePrefListener);
        carSystemGestureController.syncRegistration();

        splitScreenGestureController = new SplitScreenGestureController(this);
        splitGesturePrefListener = (sharedPreferences, key) -> {
            if (splitScreenGestureController != null) {
                splitScreenGestureController.syncRegistration();
            }
        };
        SplitScreenStore.prefs(this).registerOnSharedPreferenceChangeListener(splitGesturePrefListener);
        splitScreenGestureController.syncRegistration();
    }

    /**
     * Boot/servis başlangıcında cluster (QNX) ekranı siyah kalmasın diye animasyonlu welcome
     * overlay'i bastırır. Display henüz hazır değilse birkaç kez retry eder. Kullanıcı
     * uygulamadan harita yansıttığında {@link ClusterDisplayManager} bu splash'i kapatır.
     */
    private void scheduleClusterBootSplash() {
        if (handler == null) {
            return;
        }
        final Context appCtx = getApplicationContext();
        final int[] attempt = {0};
        final int maxAttempts = 8;
        final long retryDelayMs = 1500L;
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (DisplayHelper.isBootSplashShowing()) {
                    return;
                }
                int clusterDisplayId = AppLaunchHelper.getClusterDisplayId(appCtx);
                if (clusterDisplayId != 0 && clusterDisplayId != android.view.Display.DEFAULT_DISPLAY) {
                    DisplayHelper.showBootSplashOnDisplay(MapControlService.this, clusterDisplayId);
                    if (DisplayHelper.isBootSplashShowing()) {
                        return;
                    }
                }
                if (++attempt[0] < maxAttempts && handler != null) {
                    handler.postDelayed(this, retryDelayMs);
                }
            }
        };
        handler.postDelayed(r, 800L);
    }

    private void scheduleEqualizerKeeper() {
        if (scheduler == null) {
            return;
        }
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                AudioEqualizerController.ensureApplied(MapControlService.this);
            } catch (Throwable ignored) {
            }
        }, 20, 20, TimeUnit.SECONDS);
    }

    private void applySavedEqualizer() {
        try {
            AudioEqualizerController.applySaved(this);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Foreground service için notification oluştur
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MapControl")
                .setContentText("Arka planda çalışıyor")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
        applySavedEqualizer();
        noteBootLaunch(intent);

        // Yüzen geri tuşu + yan menü (birleşik hub)
        ensureFloatingBackButtonIfEnabled();

        if (intent != null && ACTION_BENCH_OPEN_CLUSTER.equals(intent.getAction())) {
            log("[Bench] ACTION_BENCH_OPEN_CLUSTER");
            openClusterDisplay();
            return START_STICKY;
        }
        if (intent != null && ACTION_BENCH_CLOSE_CLUSTER.equals(intent.getAction())) {
            boolean sendBackground = intent.getBooleanExtra(EXTRA_CLUSTER_CLOSE_SEND_BACKGROUND, true);
            log("[Bench] ACTION_BENCH_CLOSE_CLUSTER (sendBackground=" + sendBackground + ")");
            closeClusterDisplay(sendBackground);
            return START_STICKY;
        }
        if (intent != null && ACTION_OPEN_CLUSTER_FROM_MAPS_NOTIFICATION.equals(intent.getAction())) {
            openClusterFromMapsNotificationIfVehicleReady();
            return START_STICKY;
        }

        // Service'i restart etme (STICKY)
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        try {
            Intent restart = new Intent(getApplicationContext(), MapControlService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restart);
            } else {
                startService(restart);
            }
        } catch (Exception ignored) {
        }
        super.onTaskRemoved(rootIntent);
    }

    /**
     * Ayarlarda açık kayıtlıysa (varsayılan: açık) overlay'i ana iş parçacığında çizer.
     */
    private void ensureFloatingBackButtonIfEnabled() {
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            if (!FloatingBackButtonManager.loadEnabledState(this)) {
                return;
            }
            FloatingBackButtonManager.getInstance(this).show();
        });
    }

    @Override
    public void onDestroy() {
        if (gesturePrefListener != null) {
            TargetPackageStore.prefs(this).unregisterOnSharedPreferenceChangeListener(gesturePrefListener);
            gesturePrefListener = null;
        }
        if (carSystemGestureController != null) {
            carSystemGestureController.release();
            carSystemGestureController = null;
        }
        if (splitGesturePrefListener != null) {
            SplitScreenStore.prefs(this).unregisterOnSharedPreferenceChangeListener(splitGesturePrefListener);
            splitGesturePrefListener = null;
        }
        if (splitScreenGestureController != null) {
            splitScreenGestureController.release();
            splitScreenGestureController = null;
        }
        AudioEqualizerController.release();
        ClimateAutomationEngine.getInstance(this).stop();
        SunroofAutomationEngine.getInstance(this).stop();
        SunroofGestureOverlayManager.getInstance(this).stop();
        DmsFaceRecognitionManager.stop(this);
        super.onDestroy();
        if (scheduler != null) {
            scheduler.shutdown();
        }
        MapControlVDBusKeyBridge.release(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "MapControl Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("MapControl arka plan servisi");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Power mode değişikliklerini izle
     */
    private void startPowerModeMonitor() {
        if (scheduler == null) {
            return;
        }

        // CarInfoProxy'yi başlat
        try {
            CarInfoProxy.getInstance().init(getApplicationContext());
        } catch (Exception e) {
            log("❌ CarInfoProxy init hatası: " + e.getMessage());
        }

        // Her 2 saniyede bir power mode kontrol et
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!CarInfoProxy.getInstance().isServiceConnnected()) {
                    // Bağlantı yoksa yeniden bağlanmayı dene (Thread.sleep yerine bir sonraki iterasyonda kontrol edilir)
                    try {
                        CarInfoProxy.getInstance().init(getApplicationContext());
                        // Thread.sleep kaldırıldı - bir sonraki iterasyonda (2 saniye sonra) kontrol edilecek
                    } catch (Exception e) {
                        // Ignore
                    }
                    return;
                }

                int[] pwrItems = CarInfoProxy.getInstance().getItemValues(
                        VDEventCarInfo.MODULE_READONLY_INFO,
                        ReadOnlyID.ID_SYSTEM_POWER_MODE);

                if (pwrItems != null && pwrItems.length > 0) {
                    int currentPowerMode = pwrItems[0];
                    
                    // Power mode değişti mi kontrol et
                    if (lastPowerMode != currentPowerMode) {
                        int previousPowerMode = lastPowerMode;
                        lastPowerMode = currentPowerMode;
                        com.mapcontrol.dvr.DvrController.get(MapControlService.this)
                                .onPowerMode(currentPowerMode);
                        
                        // Power mode 2 = Araç çalışıyor (ON)
                        // Power mode 1 = Araç power aldı (STANDBY)
                        // Power mode 0 = Araç kapalı (OFF)

                        if (currentPowerMode == 1 || currentPowerMode == 2) {
                            MapControlVDBusKeyBridge.refreshSubscription(true, true);
                        }

                        int powerModeSetting = getPowerModeSetting();

                        boolean openedByPowerSetting = false;
                        if (currentPowerMode == 2) {
                            // Motor çalışınca - ayara göre kontrol et
                            if (powerModeSetting == 2) {
                                log("✅ Araç power aldı! PowerMode: " + currentPowerMode);
                                openClusterDisplay();
                                openedByPowerSetting = true;
                            } else {
                                log("⚠️ Araç power aldı ama ayar farklı, açılmıyor. PowerMode: " + currentPowerMode);
                            }
                            
                            // Hoşgeldin ekranı ve ses, ekran güç alır almaz ayrı izleyicide açılır.
                            
                            // Sürüş modu otomatik ayarlama (Spor, Eco, Normal hariç)
                            // Powermode 2 olduğunda her zaman kayıtlı modu uygula
                            // CarInfoProxy bağlantısını kontrol et
                            if (CarInfoProxy.getInstance().isServiceConnnected()) {
                                // Sıralı ayarlamaları Handler.postDelayed ile yap (Thread.sleep yerine)
                                applyDriveModeIfNeeded();
                                handler.postDelayed(() -> {
                                    applyIssIfNeeded();
                                    handler.postDelayed(() -> {
                                        applyLdwIfNeeded();
                                        handler.postDelayed(() -> {
                                            applyLdpIfNeeded();
                                            handler.postDelayed(() -> {
                                                applyFcwIfNeeded();
                                                handler.postDelayed(() -> {
                                                    applyAebIfNeeded();
                                                    handler.postDelayed(() -> {
                                                        applySpdLimitIfNeeded();
                                                        handler.postDelayed(() -> {
                                                            applyCpdIfNeeded();
                                                            handler.postDelayed(() -> {
                                                                applyChildWindowLockIfNeeded();
                                                            }, 100);
                                                        }, 100);
                                                    }, 100);
                                                }, 100);
                                            }, 100);
                                        }, 100);
                                    }, 100);
                                }, 100);
                            } else {
                                log("⚠️ CarInfoProxy bağlı değil, sürüş modu ayarlanamıyor");
                            }
                        } else if (currentPowerMode == 1) {
                            // Araç hazır durumdayken - ayara göre kontrol et
                            if (powerModeSetting == 1) {
                                log("✅ Araç hazır durumda! PowerMode: " + currentPowerMode);
                                openClusterDisplay();
                                openedByPowerSetting = true;
                            } else {
                                log("⚠️ Araç hazır durumda ama ayar farklı, açılmıyor. PowerMode: " + currentPowerMode);
                            }
                        } else if (currentPowerMode == 0) {
                            // Power mode 0 = OFF
                            log("Araç kapatıldı! PowerMode: " + currentPowerMode);
                            
                            // "Araç kapanınca navigasyonu otomatik ana ekrana al" ayarını kontrol et
                            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
                            boolean autoCloseOnPowerOff = prefs.getBoolean("autoCloseOnPowerOff", true); // Varsayılan: Evet
                            
                            if (autoCloseOnPowerOff) {
                                log("Araç kapanınca otomatik kapatma aktif, navigasyon kapatılıyor...");
                            closeClusterDisplay(true);
                            } else {
                                log("Araç kapanınca otomatik kapatma devre dışı, navigasyon kapatılmıyor");
                            }
                            
                            // Otomatik konum kaydetme kontrolü
                            boolean autoLocationSaveOnPowerOff = prefs.getBoolean("autoLocationSaveOnPowerOff", false);
                            if (autoLocationSaveOnPowerOff) {
                                // ProfileApiService ile giriş kontrolü yap
                                ProfileApiService profileApiService = new ProfileApiService(MapControlService.this);
                                if (profileApiService.isLoggedIn()) {
                                    log("Otomatik konum kaydetme aktif, konum kaydediliyor...");
                                    saveLocationAutomatically(profileApiService);
                                } else {
                                    log("Otomatik konum kaydetme aktif ama kullanıcı giriş yapmamış");
                                }
                            }
                            
                            // Sürüş modu sıfırla (bir sonraki açılışta tekrar uygulanabilir)
                            lastAppliedDriveMode = -1;
                        } else {
                            log("ℹ️ Araç power durumu: " + currentPowerMode);
                        }
                        if (!openedByPowerSetting
                                && (currentPowerMode == 1 || currentPowerMode == 2)) {
                            openClusterFromMapsNotificationIfVehicleReady();
                        }
                    }
                }
            } catch (Exception e) {
                log("❌ Power mode kontrol hatası: " + e.getMessage());
            }
        }, 0, 2, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::pollWelcomeScreen, 0, 250, TimeUnit.MILLISECONDS);
    }

    /**
     * Ekran güç alır almaz karşılama açılır. Araç kapanınca kayıt silinir, sonraki açılışta yeniden gelir.
     * Süreç araç açıkken yeniden doğarsa aynı açılışta ikinci karşılama basılmaz.
     * Ana ekran, açılış yayını bekleyen isteği güç 1 veya üzerindeyken açar.
     */
    private void pollWelcomeScreen() {
        try {
            if (!CarInfoProxy.getInstance().isServiceConnnected()) {
                try {
                    CarInfoProxy.getInstance().init(getApplicationContext());
                } catch (Exception ignored) {
                }
                return;
            }
            int[] pwrItems = CarInfoProxy.getInstance().getItemValues(
                    VDEventCarInfo.MODULE_READONLY_INFO,
                    ReadOnlyID.ID_SYSTEM_POWER_MODE);
            if (pwrItems == null || pwrItems.length == 0) {
                return;
            }
            int mode = pwrItems[0];
            int previous = lastWelcomePowerMode;
            if (previous == mode && mode < 1) {
                return;
            }
            if (previous != mode) {
                lastWelcomePowerMode = mode;
            }
            SharedPreferences prefs = getSharedPreferences(WelcomeDesignCatalog.PREFS_NAME, MODE_PRIVATE);
            boolean designEnabled = !WelcomeDesignCatalog.ID_OFF.equals(WelcomeDesignCatalog.savedId(prefs));
            StartupGate gate = StartupGate.decide(
                    previous,
                    mode,
                    BootSession.isWelcomeShown(this),
                    designEnabled,
                    BootSession.isUiPending(this));
            if (gate.clearWelcome) {
                BootSession.clearWelcomeShown(this);
                uiLaunchAttempts = 0;
            }
            if (gate.showWelcome) {
                playWelcomeAudio();
                WelcomeScreenPresenter.showSaved(this);
                BootSession.markWelcomeShown(this);
            }
            if (gate.launchUi) {
                maybeLaunchHome();
            }
        } catch (Exception e) {
            log("❌ Hoşgeldin ekranı kontrol hatası: " + e.getMessage());
        }
    }

    private void noteBootLaunch(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(BootReceiver.EXTRA_BOOT, false)) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences(BootReceiver.PREFS_NAME, MODE_PRIVATE);
        boolean ui = prefs.getBoolean(BootReceiver.KEY_BOOT_AUTO_LAUNCH_UI, true);
        BootSession.setUiPending(this, ui);
        if (!ui) {
            return;
        }
        uiLaunchAttempts = 0;
        nextUiLaunchAt = 0L;
        if (lastWelcomePowerMode >= 1) {
            maybeLaunchHome();
        }
    }

    private void maybeLaunchHome() {
        if (!BootSession.isUiPending(this) || uiLaunchAttempts >= UI_LAUNCH_ATTEMPTS) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now < nextUiLaunchAt) {
            return;
        }
        nextUiLaunchAt = now + UI_LAUNCH_RETRY_MS;
        if (handler != null) {
            handler.post(this::tryLaunchHome);
        } else {
            tryLaunchHome();
        }
    }

    private void tryLaunchHome() {
        if (!BootSession.isUiPending(this)) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences(BootReceiver.PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(BootReceiver.KEY_BOOT_AUTO_LAUNCH_UI, true)) {
            BootSession.setUiPending(this, false);
            return;
        }
        try {
            Intent activityIntent = new Intent(this, MainActivity.class);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(activityIntent);
            BootSession.setUiPending(this, false);
            uiLaunchAttempts = 0;
        } catch (Exception e) {
            uiLaunchAttempts++;
        }
    }

    /**
     * Powermode 2 olunca seçili sürüş modunu otomatik ayarla (Spor, Eco, Normal hariç)
     */
    private void applyDriveModeIfNeeded() {
        try {
            
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            int driveModeSetting = prefs.getInt("driveModeSetting", -1); // -1 = "Hiçbiri" seçili
            
            // "Hiçbiri" seçilirse direkt return at (otomatik ayarlama yapma)
            if (driveModeSetting < 0) {
                log("ℹ️ Hiçbiri seçili, otomatik ayarlama yapılmıyor");
                return;
            }
            
            // Spor (2), Eco (0), Normal (1) için otomatik ayarlama yapma
            if (driveModeSetting == 0 ) {
                log("ℹ️ Eco modu seçili, otomatik ayarlama yapılmıyor. Mode: " + driveModeSetting);
                return;
            }
            
            // Powermode 2 olduğunda her zaman kayıtlı modu uygula
            // lastAppliedDriveMode kontrolü kaldırıldı - her powermode 2'de tekrar uygula
            
            // CarInfoProxy bağlantısını kontrol et
            if (!CarInfoProxy.getInstance().isServiceConnnected()) {
                log("🔌 CarInfoProxy bağlı değil, bağlantı kuruluyor...");
                CarInfoProxy.getInstance().init(getApplicationContext());
                // Bağlantı için bekle
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (CarInfoProxy.getInstance().isServiceConnnected()) {
                        applyDriveModeInternal(driveModeSetting);
                    } else {
                        log("⚠️ CarInfoProxy bağlantısı kurulamadı");
                    }
                }, 500);
                return;
            }
            
            log("🔄 Sürüş modu uygulanıyor: " + driveModeSetting);
            applyDriveModeInternal(driveModeSetting);
            
        } catch (Exception e) {
            log("❌ applyDriveModeIfNeeded hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sürüş modunu ayarla (internal)
     */
    private void applyDriveModeInternal(int mode) {
        try {
            CarInfoProxy carInfo = CarInfoProxy.getInstance();
            
            int valueToSend = mode;
            if (mode == 7) {
                valueToSend = 7; // SAND
            }
            
            // MODULE_NEW_ENERGY = 327682
            // ID 4 = Drive Mode (NewEnergyID.ID_DRIVE_MODE = 4)
            carInfo.sendItemValue(VDEventCarInfo.MODULE_NEW_ENERGY, 4, valueToSend);
            
            lastAppliedDriveMode = mode; // Orijinal modu sakla (6 veya 7)
            
            String modeName = "Unknown";
            switch (mode) {
                case 3:
                    modeName = "SNOW";
                    break;
                case 4:
                    modeName = "MUD";
                    break;
                case 5:
                    modeName = "OFFROAD";
                    break;
                case 7:
                    modeName = "SAND";
                    break;
            }
            
            log("✅ Sürüş modu otomatik ayarlandı: " + modeName + " (Mode: " + mode + ", Sent: " + valueToSend + ")");
            
        } catch (Exception e) {
            log("❌ applyDriveModeInternal hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Powermode 2 olunca kayıtlı ISS ayarını otomatik uygula
     */
    private void applyIssIfNeeded() {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            int issSetting = prefs.getInt("issSetting", -1); // -1 = "Hiçbiri" seçili
            
            // "Hiçbiri" seçilirse direkt return at (otomatik ayarlama yapma)
            if (issSetting < 0) {
                log("ℹ️ ISS için Hiçbiri seçili, otomatik ayarlama yapılmıyor");
                return;
            }
            
            // CarInfoProxy bağlantısını kontrol et
            if (!CarInfoProxy.getInstance().isServiceConnnected()) {
                log("🔌 CarInfoProxy bağlı değil, ISS ayarlanamıyor");
                return;
            }
            
            log("🔄 ISS uygulanıyor: " + (issSetting == 1 ? "Açık" : "Kapalı"));
            applyIssInternal(issSetting);
            
        } catch (Exception e) {
            log("❌ applyIssIfNeeded hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ISS ayarını uygula (internal)
     */
    private void applyIssInternal(int issValue) {
        try {
            CarInfoProxy carInfo = CarInfoProxy.getInstance();
            
            // MODULE_CAR_SETTING = 327681
            // ID_CAR_ISS = 152
            carInfo.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_ISS, issValue);
            
            log("✅ ISS gönderildi!");
            log("   Module: MODULE_CAR_SETTING (" + VDEventCarInfo.MODULE_CAR_SETTING + ")");
            log("   ID: ID_CAR_ISS (" + CarSettingID.ID_CAR_ISS + ")");
            log("   Value: " + issValue + " (" + (issValue == 1 ? "Açık" : "Kapalı") + ")");
            
        } catch (Exception e) {
            log("❌ applyIssInternal hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Powermode 2 olunca kayıtlı LDW ayarını otomatik uygula
     */
    private void applyLdwIfNeeded() {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            int ldwSetting = prefs.getInt("ldwSetting", -1); // -1 = "Hiçbiri" seçili

            if (ldwSetting < 0) {
                log("ℹ️ LDW için Hiçbiri seçili, otomatik ayarlama yapılmıyor");
                return;
            }

            if (!CarInfoProxy.getInstance().isServiceConnnected()) {
                log("🔌 CarInfoProxy bağlı değil, LDW ayarlanamıyor");
                return;
            }

            log("🔄 LDW uygulanıyor: " + (ldwSetting == 1 ? "Açık" : "Kapalı"));
            applyLdwInternal(ldwSetting);

        } catch (Exception e) {
            log("❌ applyLdwIfNeeded hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Powermode 2 olunca kayıtlı LDP (Şeritten Kaçınma) ayarını otomatik uygula
     */
    private void applyLdpIfNeeded() {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            int ldpSetting = prefs.getInt("ldpSetting", -1); // -1 = "Hiçbiri" seçili

            // "Hiçbiri" veya 2 dışındaki değerlerde herhangi bir şey yapma
            if (ldpSetting != 2) {
                log("ℹ️ LDP için geçerli bir kapatma modu seçilmemiş (değer=" + ldpSetting + "), otomatik ayarlama yapılmıyor");
                return;
            }

            if (!CarInfoProxy.getInstance().isServiceConnnected()) {
                log("🔌 CarInfoProxy bağlı değil, LDP ayarlanamıyor");
                return;
            }

            log("🔄 LDP uygulanıyor: Kapalı (values=[2])");
            applyLdpInternal(ldpSetting);

        } catch (Exception e) {
            log("❌ applyLdpIfNeeded hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * LDP ayarını uygula (internal)
     * requestCmdId = 108, values = [2]  → 2 = Kapalı
     */
    private void applyLdpInternal(int ldpValue) {
        try {
            CarInfoProxy carInfo = CarInfoProxy.getInstance();

            // MODULE_CAR_SETTING = 327681
            // ID_CAR_FCM_INHIBIT = 108
            if (ldpValue != 2) {
                log("ℹ️ LDP değeri 2 değil (değer=" + ldpValue + "), gönderilmiyor");
                return;
            }

            int[] vals = new int[]{2};
            carInfo.sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_FCM_INHIBIT, vals);

            log("✅ LDP gönderildi!");
            log("   Module: MODULE_CAR_SETTING (" + VDEventCarInfo.MODULE_CAR_SETTING + ")");
            log("   ID: ID_CAR_FCM_INHIBIT (" + CarSettingID.ID_CAR_FCM_INHIBIT + ")");
            log("   Values: " + java.util.Arrays.toString(vals) + " (Kapalı)");

        } catch (Exception e) {
            log("❌ applyLdpInternal hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * LDW ayarını uygula (internal)
     */
    private void applyLdwInternal(int ldwValue) {
        try {
            CarInfoProxy carInfo = CarInfoProxy.getInstance();

            // MODULE_CAR_SETTING = 327681
            // ID_CAR_LDW = 56
            // Araç logu: 56 -> 1 (Açık), 2 (Kapalı)
            carInfo.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_LDW, ldwValue);

            log("✅ LDW gönderildi!");
            log("   Module: MODULE_CAR_SETTING (" + VDEventCarInfo.MODULE_CAR_SETTING + ")");
            log("   ID: ID_CAR_LDW (" + CarSettingID.ID_CAR_LDW + ")");
            log("   Value: " + ldwValue + " (" + (ldwValue == 1 ? "Açık" : "Kapalı") + ")");

        } catch (Exception e) {
            log("❌ applyLdwInternal hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Powermode 2 olunca kayıtlı FCW (Ön Çarpışma Uyarısı) ayarını otomatik uygula
     * moduleId=327681, requestCmdId=36, values=[2, 1]
     */
    private void applyFcwIfNeeded() {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            int fcwSetting = prefs.getInt("fcwSetting", -1); // -1 = "Hiçbiri" seçili

            if (fcwSetting != 2) {
                log("ℹ️ FCW için geçerli bir kapatma modu seçilmemiş (değer=" + fcwSetting + "), otomatik ayarlama yapılmıyor");
                return;
            }

            if (!CarInfoProxy.getInstance().isServiceConnnected()) {
                log("🔌 CarInfoProxy bağlı değil, FCW ayarlanamıyor");
                return;
            }

            log("🔄 FCW uygulanıyor: Kapalı (values=[2, 1])");
            applyFcwInternal();

        } catch (Exception e) {
            log("❌ applyFcwIfNeeded hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * FCW ayarını uygula (internal)
     * requestCmdId = 36, values = [2, 1]
     */
    private void applyFcwInternal() {
        try {
            CarInfoProxy carInfo = CarInfoProxy.getInstance();

            // MODULE_CAR_SETTING = 327681
            // requestCmdId = 36
            int[] vals = new int[]{2, 1};
            carInfo.sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, 36, vals);

            log("✅ FCW gönderildi!");
            log("   Module: MODULE_CAR_SETTING (" + VDEventCarInfo.MODULE_CAR_SETTING + ")");
            log("   ID: 36");
            log("   Values: " + java.util.Arrays.toString(vals) + " (Kapalı)");

        } catch (Exception e) {
            log("❌ applyFcwInternal hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Powermode 2 olunca kayıtlı AEB (Aktif Acil Fren) ayarını otomatik uygula
     * requestCmdId=37, values=[2], delayCount=10
     */
    private void applyAebIfNeeded() {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            int aebSetting = prefs.getInt("aebSetting", -1); // -1 = "Hiçbiri" seçili

            if (aebSetting != 2) {
                log("ℹ️ AEB için geçerli bir kapatma modu seçilmemiş (değer=" + aebSetting + "), otomatik ayarlama yapılmıyor");
                return;
            }

            if (!CarInfoProxy.getInstance().isServiceConnnected()) {
                log("🔌 CarInfoProxy bağlı değil, AEB ayarlanamıyor");
                return;
            }

            log("🔄 AEB uygulanıyor: Kapalı (values=[2])");
            applyAebInternal();

        } catch (Exception e) {
            log("❌ applyAebIfNeeded hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * AEB ayarını uygula (internal)
     * requestCmdId = 37, values = [2]
     */
    private void applyAebInternal() {
        try {
            CarInfoProxy carInfo = CarInfoProxy.getInstance();

            // MODULE_CAR_SETTING = 327681
            // requestCmdId = 37
            int[] vals = new int[]{2};
            carInfo.sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, 37, vals);

            log("✅ AEB gönderildi!");
            log("   Module: MODULE_CAR_SETTING (" + VDEventCarInfo.MODULE_CAR_SETTING + ")");
            log("   ID: 37");
            log("   Values: " + java.util.Arrays.toString(vals) + " (Kapalı)");

        } catch (Exception e) {
            log("❌ applyAebInternal hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Powermode 2 olunca kayıtlı CPD (çocuk algılama) ayarını otomatik uygula.
     * ID_CAR_CIR_ENA_STS (191): 1 = açık, 2 = kapalı — tüm araçlarda desteklenmez.
     */
    private void applyCpdIfNeeded() {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            int cpdSetting = prefs.getInt("cpdSetting", -1);

            if (cpdSetting != 2) {
                log("ℹ️ CPD için geçerli bir kapatma modu seçilmemiş (değer=" + cpdSetting + "), otomatik ayarlama yapılmıyor");
                return;
            }

            if (!CarInfoProxy.getInstance().isServiceConnnected()) {
                log("🔌 CarInfoProxy bağlı değil, CPD ayarlanamıyor");
                return;
            }

            log("🔄 CPD uygulanıyor: Kapalı (value=2)");
            applyCpdInternal(2);

        } catch (Exception e) {
            log("❌ applyCpdIfNeeded hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void applyCpdInternal(int cpdValue) {
        try {
            CarInfoProxy carInfo = CarInfoProxy.getInstance();
            carInfo.sendItemValue(
                    VDEventCarInfo.MODULE_CAR_SETTING,
                    CarSettingID.ID_CAR_CIR_ENA_STS,
                    cpdValue);

            log("✅ CPD gönderildi!");
            log("   Module: MODULE_CAR_SETTING (" + VDEventCarInfo.MODULE_CAR_SETTING + ")");
            log("   ID: ID_CAR_CIR_ENA_STS (" + CarSettingID.ID_CAR_CIR_ENA_STS + ")");
            log("   Value: " + cpdValue + " (" + (cpdValue == 1 ? "Açık" : "Kapalı") + ")");

        } catch (Exception e) {
            log("❌ applyCpdInternal hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Powermode 2 olunca kayıtlı hız limit uyarı ayarını otomatik uygula
     */
    private void applySpdLimitIfNeeded() {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            int setting = prefs.getInt("spdLimitSetting", -1); // -1 = "Hiçbiri"

            if (setting < 0) {
                log("ℹ️ Hız limit uyarısı için Hiçbiri seçili, otomatik ayarlama yapılmıyor");
                return;
            }

            if (!CarInfoProxy.getInstance().isServiceConnnected()) {
                log("🔌 CarInfoProxy bağlı değil, hız limit uyarısı ayarlanamıyor");
                return;
            }

            log("🔄 Hız limit uyarı modu uygulanıyor: " + (setting == 2 ? "Kapalı" : "Bilinmiyor"));
            applySpdLimitInternal(setting);

        } catch (Exception e) {
            log("❌ applySpdLimitIfNeeded hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Hız limit uyarı modunu ayarla (internal)
     */
    private void applySpdLimitInternal(int value) {
        try {
            CarInfoProxy carInfo = CarInfoProxy.getInstance();

            // MODULE_CAR_SETTING = 327681
            // ID_CAR_SPD_LIMIT_WARN_SET = 201 (log: Kapalı -> values [2,2])
            if (value != 2) {
                log("ℹ️ Hız limit uyarı modu Hiçbiri/diğer, gönderilmiyor");
                return;
            }
            int[] vals = new int[]{2, 2};
            carInfo.sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_SPD_LIMIT_WARN_SET, vals);

            log("✅ Hız limit uyarı modu gönderildi!");
            log("   Module: MODULE_CAR_SETTING (" + VDEventCarInfo.MODULE_CAR_SETTING + ")");
            log("   ID: ID_CAR_SPD_LIMIT_WARN_SET (" + CarSettingID.ID_CAR_SPD_LIMIT_WARN_SET + ")");
            log("   Values: " + java.util.Arrays.toString(vals) + " (Kapalı)");

        } catch (Exception e) {
            log("❌ applySpdLimitInternal hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ana ekrandaki Yansıtma sekmesiyle aynı uygulama: {@link ClusterDisplayManager#openClusterDisplay()}.
     */
    /**
     * Powermode 2 olunca kayıtlı çocuk cam kilidi ayarını araca otomatik uygula
     */
    private void applyChildWindowLockIfNeeded() {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            boolean enabled = prefs.getInt("childLockSetting", -1) == 1 || prefs.getBoolean("childWindowLockAutoEnable", false);
            if (!enabled) {
                return;
            }
            if (!CarInfoProxy.getInstance().isServiceConnnected()) {
                log("🔌 CarInfoProxy bağlı değil, çocuk cam kilidi uygulanamıyor");
                return;
            }
            log("🔒 Çocuk cam kilidi otomatik kilitleniyor (ID_CAR_WINDOW_PROHIBIT=1)...");
            CarInfoProxy.getInstance().sendItemValue(
                    VDEventCarInfo.MODULE_CAR_SETTING,
                    CarSettingID.ID_CAR_WINDOW_PROHIBIT,
                    1);
            log("✅ Çocuk cam kilidi otomatik aktif edildi.");
        } catch (Exception e) {
            log("❌ applyChildWindowLockIfNeeded hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private ClusterDisplayManager getClusterDisplayHelper() {
        if (clusterDisplayHelper == null) {
            clusterDisplayHelper = new ClusterDisplayManager(getApplicationContext(),
                    new ClusterDisplayManager.ClusterCallback() {
                        @Override
                        public void onNavigationStateChanged(boolean isOpen) {
                            ClusterNavigationState.publishFromService(
                                    MapControlService.this.getApplicationContext(), isOpen);
                        }

                        @Override
                        public String getTargetPackage() {
                            return MapControlService.this.getTargetPackage();
                        }

                        @Override
                        public void log(String message) {
                            MapControlService.this.log(message);
                        }
                    });
        }
        return clusterDisplayHelper;
    }

    /**
     * Maps bildirimi yansıtmayı yalnızca Başlatma bölümündeki Hazır (1) veya
     * motor (2) sinyali varken açar. Kapalı veya okunamayan güçte VDBus'a gitmez;
     * sinyal sonradan gelince güç izleyicisi aynı kapıdan açar.
     */
    private void openClusterFromMapsNotificationIfVehicleReady() {
        if (!mapsNotificationWantsCluster()) {
            return;
        }
        int powerMode = readSystemPowerMode();
        if (powerMode != 1 && powerMode != 2) {
            log("[Maps] Bildirim duruyor; yansıtma bekliyor. PowerMode: " + powerMode);
            return;
        }
        if (ClusterNavigationState.getLastKnownOpen()) {
            return;
        }
        log("[Maps] Navigasyon bildirimi — cluster açılıyor (PowerMode: " + powerMode + ")");
        openClusterDisplay();
    }

    private boolean mapsNotificationWantsCluster() {
        try {
            if (!GoogleMapsNavNotificationCoordinator.isAutoProjectionEnabled(this)) {
                return false;
            }
            if (!AppLaunchHelper.GOOGLE_MAPS_PACKAGE.equals(TargetPackageStore.read(this))) {
                return false;
            }
            return GoogleMapsNavNotificationCoordinator.getLastSnapshot().active;
        } catch (Exception e) {
            return false;
        }
    }

    /** 0 kapalı, 1 hazır, 2 motor. VDBus okunamazsa -1. */
    private int readSystemPowerMode() {
        try {
            if (!CarInfoProxy.getInstance().isServiceConnnected()) {
                return -1;
            }
            int[] pwrItems = CarInfoProxy.getInstance().getItemValues(
                    VDEventCarInfo.MODULE_READONLY_INFO,
                    ReadOnlyID.ID_SYSTEM_POWER_MODE);
            if (pwrItems != null && pwrItems.length > 0) {
                return pwrItems[0];
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private void openClusterDisplay() {
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            log("🔌 Cluster display açılıyor (ClusterDisplayManager)...");
            try {
                getClusterDisplayHelper().openClusterDisplay();
            } catch (Exception e) {
                log("❌ Cluster display açma hatası: " + e.getMessage());
            }
        });
    }

    /**
     * SharedPreferences'tan target package'ı al
     */
    private String getTargetPackage() {
        try {
            String v = TargetPackageStore.read(this);
            return v.isEmpty() ? null : v;
        } catch (Exception e) {
            log("❌ getTargetPackage hatası: " + e.getMessage());
            return null;
        }
    }

    /**
     * Power mode ayarını al (2 = Motor Çalışınca, 1 = Araç Hazır Durumdayken, 0 = Elle Çalıştır)
     */
    private int getPowerModeSetting() {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            return prefs.getInt("powerModeSetting", 2); // Varsayılan: 2 (Motor Çalışınca)
        } catch (Exception e) {
            log("❌ getPowerModeSetting hatası: " + e.getMessage());
            return 2; // Varsayılan
        }
    }

    /**
     * @param sendBackground {@code true}: sekmedeki Durdur gibi HOME ile arka plan;
     *                     {@code false}: cluster kapanır, hedef uygulama önde kalır (yüzen çubuk).
     */
    private void closeClusterDisplay(boolean sendBackground) {
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            log("🔴 Cluster display kapatılıyor (ClusterDisplayManager, sendBackground=" + sendBackground + ")...");
            try {
                getClusterDisplayHelper().closeClusterDisplay(sendBackground);
            } catch (Exception e) {
                log("❌ Cluster display kapatma hatası: " + e.getMessage());
            }
        });
    }

    /**
     * Otomatik konum kaydetme (araç kapanınca)
     */
    private void saveLocationAutomatically(ProfileApiService profileApiService) {
        new Thread(() -> {
            try {
                // LocationManager ile konum al
                android.location.LocationManager locationManager = (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (locationManager == null) {
                    log("❌ LocationManager bulunamadı");
                    return;
                }
                
                // Konum izni kontrolü
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                        checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        log("❌ Konum izni yok, otomatik konum kaydedilemedi");
                        return;
                    }
                }
                
                // Mevcut konumu al
                android.location.Location lastLocation = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                if (lastLocation == null) {
                    lastLocation = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                }
                
                if (lastLocation != null) {
                    double latitude = lastLocation.getLatitude();
                    double longitude = lastLocation.getLongitude();
                    
                    log("📍 Otomatik konum kaydediliyor: " + latitude + ", " + longitude);
                    
                    profileApiService.saveCoordinates(latitude, longitude, "Auto Location (Power Off)", "Araç kapanınca otomatik kaydedilen konum", new ProfileApiService.ApiCallback() {
                        @Override
                        public void onSuccess(String message, org.json.JSONObject data) {
                            handler.post(() -> {
                                log("✅ Otomatik konum başarıyla kaydedildi: " + message);
                            });
                        }
                        
                        @Override
                        public void onError(String error) {
                            handler.post(() -> {
                                log("❌ Otomatik konum kaydetme hatası: " + error);
                            });
                        }
                    });
                } else {
                    log("⚠️ Konum alınamadı, otomatik konum kaydedilemedi");
                }
            } catch (Exception e) {
                log("❌ Otomatik konum kaydetme hatası: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Hoşgeldin ses dosyasını çal. Ekran güç alınca (mod 1 veya 2) bir kez.
     */
    private void playWelcomeAudio() {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            boolean autoPlayEnabled = prefs.getBoolean("welcomeAudioAutoPlay", false);
            
            if (!autoPlayEnabled) {
                return; // Otomatik çal kapalı
            }
            
            String filePath = prefs.getString("welcomeAudioFilePath", null);
            if (filePath == null || filePath.isEmpty()) {
                return; // Dosya yolu yok
            }
            
            File audioFile = new File(filePath);
            if (!audioFile.exists()) {
                log("⚠️ Hoşgeldin ses dosyası bulunamadı: " + filePath);
                return;
            }
            
            // MediaPlayer'ı arka planda çalıştır
            new Thread(() -> {
                try {
                    MediaPlayer mediaPlayer = new MediaPlayer();
                    mediaPlayer.setDataSource(filePath);
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                    
                    // Çalma bitince temizle
                    mediaPlayer.setOnCompletionListener(mp -> {
                        mp.release();
                        log("✅ Hoşgeldin ses dosyası çalındı");
                    });
                    
                    // Hata durumunda
                    mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                        log("❌ Hoşgeldin ses çalma hatası: " + what);
                        mp.release();
                        return true;
                    });
                    
                    log("🔊 Hoşgeldin ses dosyası çalınıyor: " + audioFile.getName());
                } catch (IOException e) {
                    log("❌ Hoşgeldin ses dosyası açılamadı: " + e.getMessage());
                } catch (Exception e) {
                    log("❌ Hoşgeldin ses hatası: " + e.getMessage());
                }
            }).start();
            
        } catch (Exception e) {
            log("❌ Hoşgeldin ses kontrol hatası: " + e.getMessage());
        }
    }
    
}