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
import android.util.Log;

import androidx.core.app.NotificationCompat;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID;
import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.desaysv.ivi.vdb.event.id.carlan.VDEventCarLan;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayArea;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayCluster;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.view.Display;
import android.content.Context;
import android.content.Intent;

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
import com.mapcontrol.ui.activity.MainActivity;
import com.mapcontrol.util.AppLaunchHelper;
import com.mapcontrol.util.DisplayHelper;
import com.mapcontrol.manager.FloatingBackButtonManager;
import com.mapcontrol.R;

public class MapControlService extends Service {
    private static final String CHANNEL_ID = "MapControlServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "MapControlService";
    private static final String ACTION_LOG = "com.mapcontrol.LOG_MESSAGE";
    private static final String EXTRA_LOG_MESSAGE = "log_message";
    private ScheduledExecutorService scheduler;
    private Handler handler;
    private int lastPowerMode = -1;
    private int lastAppliedDriveMode = -1; // Son uygulanan sürüş modu (tekrar uygulamayı önlemek için)
    
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
        handler = new Handler(Looper.getMainLooper());
        scheduler = Executors.newSingleThreadScheduledExecutor();
        
        // Power mode kontrolünü başlat
        startPowerModeMonitor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Foreground service için notification oluştur
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ClusterMap")
                .setContentText("Arka planda çalışıyor")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        // Yüzen geri tuşu: ayarlara girmek zorunda kalmadan, servis her çalıştığında (uygulama açılışı) göster
        ensureFloatingBackButtonIfEnabled();

        // Service'i restart etme (STICKY)
        return START_STICKY;
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
        super.onDestroy();
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "ClusterMap Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("ClusterMap arka plan servisi");
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
                        
                        // Power mode 2 = Araç çalışıyor (ON)
                        // Power mode 1 = Araç power aldı (STANDBY)
                        // Power mode 0 = Araç kapalı (OFF)

                        int powerModeSetting = getPowerModeSetting();

                        if (currentPowerMode == 2) {
                            // Motor çalışınca - ayara göre kontrol et
                            if (powerModeSetting == 2) {
                                log("✅ Araç power aldı! PowerMode: " + currentPowerMode);
                                openClusterDisplay();
                            } else {
                                log("⚠️ Araç power aldı ama ayar farklı, açılmıyor. PowerMode: " + currentPowerMode);
                            }
                            
                            // Hoşgeldin ses dosyasını çal (eğer ayar açıksa)
                            playWelcomeAudio();
                            
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
                            closeClusterDisplay();
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
                    }
                }
            } catch (Exception e) {
                log("❌ Power mode kontrol hatası: " + e.getMessage());
            }
        }, 0, 2, TimeUnit.SECONDS);
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
     * Cluster display'i aç (MainActivity'deki openClusterDisplay() ile aynı mantık)
     */
    private void openClusterDisplay() {
        new Thread(() -> {

                    // Önce "Uygulama Hazırlanıyor" mesajını göster
                    DisplayHelper.showPreparingMessageOnDisplay(getApplicationContext(), AppLaunchHelper.getClusterDisplayId(getApplicationContext()));
                    Runnable timeoutRunnable = () -> {
                        log("⚠️ İşlem zaman aşımına uğradı, mesaj gizleniyor.");
                        DisplayHelper.hidePreparingMessage();
                    };
                    handler.postDelayed(timeoutRunnable, 7000);
            try {
                log("🔌 Cluster display açılıyor...");

                // 1. Wake up event gönder
                    VDNaviDisplayCluster payloadwakeUp = new VDNaviDisplayCluster();
                    payloadwakeUp.setNaviFrontDeskStatus("true");
                    payloadwakeUp.setDisplayCluster("true");
                    payloadwakeUp.setPerspective(0);
                    payloadwakeUp.setPerspectiveResult("false");
                    payloadwakeUp.setRequestDisplayNaviArea("true");
                    VDEvent eventwakeUp = VDNaviDisplayCluster.createEvent(VDEventCarLan.NAVIGATION_DISPLAY_TO_CLUSTER, payloadwakeUp);
                    VDBus.getDefault().set(eventwakeUp);

                // 1000ms gecikme (Thread.sleep yerine Handler.postDelayed)
                handler.postDelayed(() -> {
                // 2. Display Area event
                VDNaviDisplayArea payloadDA = new VDNaviDisplayArea();
                payloadDA.setNaviDisplayArea(10);
                payloadDA.setNaviDisplayAreaResult("true");
                VDEvent eventDA = VDNaviDisplayArea.createEvent(VDEventCarLan.NAVIGATION_DISPLAY_AREA, payloadDA);
                VDBus.getDefault().set(eventDA);

                // 3. Display Cluster event
                VDNaviDisplayCluster payload = new VDNaviDisplayCluster();
                payload.setNaviFrontDeskStatus("true");
                payload.setDisplayCluster("true");
                payload.setPerspective(0);
                payload.setPerspectiveResult("false");
                payload.setRequestDisplayNaviArea("false");
                VDEvent event = VDNaviDisplayCluster.createEvent(VDEventCarLan.NAVIGATION_DISPLAY_TO_CLUSTER, payload);
                VDBus.getDefault().set(event);

                log("✅ Navigasyon paneli açıldı");

                // 4. Eğer bir uygulama seçilmişse, cluster'da başlat (600ms gecikme ile)
                handler.postDelayed(() -> {
                    String targetPackage = getTargetPackage();
                    if (targetPackage != null && !targetPackage.trim().isEmpty()) {
                            log("🚀 Seçilen uygulama cluster'da başlatılıyor: " + targetPackage);
                            try {
                                PackageManager pm = getPackageManager();
                                Intent launchIntent = pm.getLaunchIntentForPackage(targetPackage.trim());
                                if (launchIntent == null) {
                                    log("⚠️ Uygulama intent bulunamadı: " + targetPackage);
                                } else {
                                    int clusterDisplayId = AppLaunchHelper.getClusterDisplayId(MapControlService.this);
                                    AppLaunchHelper.launchAppOnDisplay(MapControlService.this, targetPackage.trim(), clusterDisplayId, Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                    log("✅ Uygulama cluster'da başlatıldı: " + targetPackage);
                                }
                            } catch (Exception e) {
                                log("❌ Uygulama başlatma hatası: " + e.getMessage());
                            }
                    }
                    handler.postDelayed(() -> {
                        DisplayHelper.hidePreparingMessage();
                    }, 1000);
                }, 600);
                }, 1000);

            } catch (Exception e) {
                log("❌ Cluster display açma hatası: " + e.getMessage());
            }
        }).start();
    }

    /**
     * SharedPreferences'tan target package'ı al
     */
    private String getTargetPackage() {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            return prefs.getString("targetPackage", null);
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
     * Cluster display'i kapat (MainActivity'deki closeClusterDisplay() ile aynı mantık)
     */
    private void closeClusterDisplay() {
        new Thread(() -> {
            try {
                log("🔴 Cluster display kapatılıyor...");

                // Eğer bir uygulama seçilmişse, önce display kontrolü yap
                String targetPackage = getTargetPackage();
                if (targetPackage != null && !targetPackage.trim().isEmpty()) {
                    int currentDisplay = getAppCurrentDisplay(targetPackage);
                    log("ℹ️ Uygulama mevcut display: " + currentDisplay);
                    
                    // Eğer uygulama display 0 veya 2'de değilse, işlem yapma
                    if (currentDisplay != Display.DEFAULT_DISPLAY && currentDisplay != 2) {
                        log("ℹ️ Uygulama display " + currentDisplay + "'de, işlem yapılmıyor");
                        return;
                    }
                }

                // VDBus event ile display area'yı kapat
                VDNaviDisplayArea payloadDA = new VDNaviDisplayArea();
                payloadDA.setNaviDisplayArea(0);
                payloadDA.setNaviDisplayAreaResult("true");
                VDEvent eventDA = VDNaviDisplayArea.createEvent(VDEventCarLan.NAVIGATION_DISPLAY_AREA, payloadDA);
                VDBus.getDefault().set(eventDA);


                VDNaviDisplayCluster payload = new VDNaviDisplayCluster();
                payload.setNaviFrontDeskStatus("false");
                payload.setDisplayCluster("false");
                payload.setPerspective(0);
                payload.setPerspectiveResult("false");
                payload.setRequestDisplayNaviArea("false");
                VDEvent event = VDNaviDisplayCluster.createEvent(VDEventCarLan.NAVIGATION_DISPLAY_TO_CLUSTER, payload);
                VDBus.getDefault().set(event);

                log("✅ Navigasyon paneli kapatıldı");

                // Eğer bir uygulama seçilmişse, onu default display'e (0) taşı
                if (targetPackage != null && !targetPackage.trim().isEmpty()) {
                    handler.postDelayed(() -> {
                        try {
                            if ("com.mapcontrol".equals(targetPackage)) {
                                return;
                            }
                            PackageManager pm = getPackageManager();
                            Intent intent = pm.getLaunchIntentForPackage(targetPackage);
                            if (intent == null) {
                                log("⚠️ Uygulama intent bulunamadı: " + targetPackage);
                            } else {
                                AppLaunchHelper.moveAppToDefaultDisplay(MapControlService.this, targetPackage);
                                log("✅ Uygulama default display'e taşındı: " + targetPackage);
                            }
                        } catch (Exception e) {
                            log("❌ moveAppToDefaultDisplay hatası: " + e.getMessage());
                        }
                    }, 300);
                }

            } catch (Exception e) {
                log("❌ Cluster display kapatma hatası: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Uygulamanın şu anda hangi display'de çalıştığını kontrol et
     */
    private int getAppCurrentDisplay(String packageName) {
        try {
            // dumpsys window komutu ile kontrol et
            Process process = Runtime.getRuntime().exec("dumpsys window windows");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            int currentDisplay = -1;
            
            while ((line = reader.readLine()) != null) {
                // Package name'i içeren satırları bul
                if (line.contains(packageName)) {
                    // Display ID'yi bul (mDisplayId=...)
                    int displayIndex = line.indexOf("mDisplayId=");
                    if (displayIndex != -1) {
                        int start = displayIndex + 11; // "mDisplayId=".length()
                        int end = line.indexOf(" ", start);
                        if (end == -1) end = line.length();
                        try {
                            currentDisplay = Integer.parseInt(line.substring(start, end));
                            break;
                        } catch (NumberFormatException e) {
                            // Ignore
                        }
                    }
                }
            }
            reader.close();
            process.destroy();
            
            return currentDisplay != -1 ? currentDisplay : Display.DEFAULT_DISPLAY;
        } catch (Exception e) {
            log("❌ getAppCurrentDisplay hatası: " + e.getMessage());
            return Display.DEFAULT_DISPLAY; // Varsayılan olarak 0 döndür
        }
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
     * Hoşgeldin ses dosyasını çal (powerMode == 2 olduğunda)
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