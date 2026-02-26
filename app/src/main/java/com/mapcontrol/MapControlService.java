package com.mapcontrol;

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
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.app.ActivityOptions;
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

        // Service'i restart etme (STICKY)
        return START_STICKY;
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
                    showPreparingMessageOnDisplay(getClusterDisplayId());
                    Runnable timeoutRunnable = () -> {
                        log("⚠️ İşlem zaman aşımına uğradı, mesaj gizleniyor.");
                        hidePreparingMessage();
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
                        launchSelectedAppOnCluster(targetPackage);
                    }
                    handler.postDelayed(() -> {
                        hidePreparingMessage();
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
     * Seçilen uygulamayı cluster display'de başlat
     */
    private void launchSelectedAppOnCluster(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packageName);
            
            if (intent == null) {
                log("⚠️ Uygulama intent bulunamadı: " + packageName);
                return;
            }

            // Cluster display ID'yi bul
            int clusterDisplayId = getClusterDisplayId();
            if (clusterDisplayId < 0) {
                log("⚠️ Cluster display bulunamadı");
                return;
            }

            
            // Uygulamayı hemen aç
            ActivityOptions options = ActivityOptions.makeBasic();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                options.setLaunchDisplayId(clusterDisplayId);
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            startActivity(intent, options.toBundle());
            log("✅ Uygulama cluster'da başlatıldı: " + packageName);
        } catch (Exception e) {
            log("❌ Uygulama başlatma hatası: " + e.getMessage());
        }
    }

    /**
     * Cluster display ID'yi bul
     */
    private int getClusterDisplayId() {
        try {
            DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager == null) {
                return -1;
            }

            Display[] displays = displayManager.getDisplays();
            for (Display display : displays) {
                int displayId = display.getDisplayId();
                // Display 0 = default, diğerleri genellikle cluster
                if (displayId != Display.DEFAULT_DISPLAY) {
                    return displayId;
                }
            }
            return -1;
        } catch (Exception e) {
            log("❌ getClusterDisplayId hatası: " + e.getMessage());
            return -1;
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
                        moveAppToDefaultDisplay(targetPackage);
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
     * Seçilen uygulamayı cluster display'den default display'e (0) taşır
     */
    private void moveAppToDefaultDisplay(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packageName);
            
            if (intent == null) {
                log("⚠️ Uygulama intent bulunamadı: " + packageName);
                return;
            }

            // Default display'de (0) başlat
            ActivityOptions opts = ActivityOptions.makeBasic();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                opts.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent, opts.toBundle());
            log("✅ Uygulama default display'e taşındı: " + packageName);
        } catch (Exception e) {
            log("❌ moveAppToDefaultDisplay hatası: " + e.getMessage());
        }
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
    
    /**
     * İkinci ekranda "Uygulama Hazırlanıyor" mesajını göster
     * İkinci display için Context oluşturup WindowManager'ı o Context ile alır
     */
    private android.view.View preparingMessageView = null;
    private android.view.WindowManager preparingWindowManager = null;
    private android.view.WindowManager.LayoutParams preparingParams = null;
    private Context displayContext = null;
    
    private void showPreparingMessageOnDisplay(int displayId) {
        try {
            // Önce mevcut mesajı kaldır (varsa)
            hidePreparingMessage();
            
            // İkinci display için Context oluştur
            DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager == null) {
                log("DisplayManager bulunamadı");
                return;
            }
            
            Display targetDisplay = null;
            Display[] displays = displayManager.getDisplays();
            for (Display display : displays) {
                if (display.getDisplayId() == displayId) {
                    targetDisplay = display;
                    break;
                }
            }
            
            if (targetDisplay == null) {
                log("Display " + displayId + " bulunamadı");
                return;
            }
            
            // İkinci display için Context oluştur
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                displayContext = createDisplayContext(targetDisplay);
            } else {
                // Android 4.2 altı için default Context kullan
                displayContext = this;
            }
            
            // İkinci display için WindowManager al
            preparingWindowManager = (android.view.WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);
            
            // Mesaj view'ı oluştur (ikinci display Context'i ile)
            android.widget.LinearLayout messageContainer = new android.widget.LinearLayout(displayContext);
            messageContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
            messageContainer.setGravity(android.view.Gravity.CENTER);
            messageContainer.setBackgroundColor(0xE6000000); // Yarı şeffaf siyah arka plan
            messageContainer.setPadding(40, 40, 40, 40);
            
            android.widget.TextView messageText = new android.widget.TextView(displayContext);
            messageText.setText("Uygulama Hazırlanıyor...");
            messageText.setTextSize(24);
            messageText.setTextColor(0xFFFFFFFF);
            messageText.setTypeface(null, android.graphics.Typeface.BOLD);
            messageText.setGravity(android.view.Gravity.CENTER);
            
            messageContainer.addView(messageText, new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
            
            preparingMessageView = messageContainer;
            
            // WindowManager parametreleri
            int type;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                type = android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                type = android.view.WindowManager.LayoutParams.TYPE_PHONE;
            }
            
            preparingParams = new android.view.WindowManager.LayoutParams(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT);
            
            preparingParams.gravity = android.view.Gravity.CENTER;
            
            // WindowManager'a ekle (ikinci ekranda görünecek)
            preparingWindowManager.addView(preparingMessageView, preparingParams);
            
            // Smooth fade-in animasyonu (sadece alpha)
            preparingMessageView.setAlpha(0f);
            
            preparingMessageView.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            
            // Heartbeat efekti (yazıya kalp atışı animasyonu)
            android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator.ofFloat(messageText, "scaleX", 1.0f, 1.1f);
            android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator.ofFloat(messageText, "scaleY", 1.0f, 1.1f);
            scaleX.setDuration(600);
            scaleY.setDuration(600);
            scaleX.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            scaleY.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            scaleX.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            scaleY.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            scaleX.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            scaleY.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            android.animation.AnimatorSet heartbeatAnim = new android.animation.AnimatorSet();
            heartbeatAnim.playTogether(scaleX, scaleY);
            heartbeatAnim.start();
            
            log("Uygulama Hazırlanıyor mesajı gösterildi (displayId=" + displayId + ")");
        } catch (Exception e) {
            log("showPreparingMessageOnDisplay hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * "Uygulama Hazırlanıyor" mesajını kaldır (smooth fade-out animasyonu ile)
     */
    private void hidePreparingMessage() {
        if (preparingMessageView != null && preparingWindowManager != null) {
            try {
                // Smooth fade-out animasyonu (sadece alpha)
                preparingMessageView.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator())
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (preparingWindowManager != null && preparingMessageView != null) {
                                        preparingWindowManager.removeView(preparingMessageView);
                                    }
                                } catch (Exception e) {
                                    // View zaten kaldırılmış olabilir
                                }
                                preparingMessageView = null;
                                preparingWindowManager = null;
                                preparingParams = null;
                                displayContext = null;
                                log("Uygulama Hazırlanıyor mesajı kaldırıldı");
                            }
                        })
                        .start();
            } catch (Exception e) {
                // Animasyon başarısız olursa direkt kaldır
                try {
                    preparingWindowManager.removeView(preparingMessageView);
                } catch (Exception ex) {
                    // View zaten kaldırılmış olabilir
                }
                preparingMessageView = null;
                preparingWindowManager = null;
                preparingParams = null;
                displayContext = null;
            }
        }
    }
}

