package com.mapcontrol.ui.activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.hardware.display.DisplayManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.View;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import java.util.List;
import androidx.appcompat.app.AppCompatActivity;
import com.desaysv.ivi.vdb.client.bind.VDServiceDef;
import com.desaysv.ivi.vdb.client.listener.VDBindListener;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;
import com.desaysv.ivi.vdb.event.id.carlan.VDEventCarLan;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayArea;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayCluster;
import android.os.Build;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import android.content.pm.PackageInfo;
import android.content.SharedPreferences;
import androidx.core.content.FileProvider;
import android.app.ActivityManager;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoHelper;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID;
import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.AudioAttributes;
import java.io.IOException;
import com.mapcontrol.api.ProfileApiService;
import com.mapcontrol.manager.ClusterDisplayManager;
import com.mapcontrol.manager.VDBusManager;
import com.mapcontrol.manager.WebServerManager;
import com.mapcontrol.service.ServiceInitializer;
import com.mapcontrol.ui.builder.AppsTabBuilder;
import com.mapcontrol.ui.builder.AssistTabBuilder;
import com.mapcontrol.ui.builder.DriveModeTabBuilder;
import com.mapcontrol.ui.builder.FileUploadTabBuilder;
import com.mapcontrol.ui.builder.LogTabBuilder;
import com.mapcontrol.ui.builder.ProfileTabBuilder;
import com.mapcontrol.ui.builder.ProjectionTabBuilder;
import com.mapcontrol.ui.builder.SettingsTabBuilder;
import com.mapcontrol.ui.builder.SideRailBuilder;
import com.mapcontrol.ui.builder.TopBarBuilder;
import com.mapcontrol.ui.builder.WifiTabBuilder;
import com.mapcontrol.util.DialogHelper;
import com.mapcontrol.util.DisplayHelper;

public class MainActivity extends AppCompatActivity {
    private TextView tvLogs;
    private ScrollView scrollView;
    private final StringBuilder logBuffer = new StringBuilder();
    private Handler handler;
    private volatile boolean isNavigationOpen = false; // Navigasyon durumu
    private String targetPackage = ""; // Seçilen uygulama paketi
    private ClusterDisplayManager clusterDisplayManager;
    private TextView targetAppLabel; // Seçilen uygulamayı gösteren TextView
    private LinearLayout tabContentArea; // Tab içerik alanı
    private LinearLayout settingsTabContent; // Ayarlar tab içeriği
    private ScrollView settingsScrollView; // Ayarlar tab ScrollView
    private LinearLayout projectionTabContent; // Yansıtma tab içeriği
    private ScrollView projectionScrollView; // Yansıtma tab ScrollView
    private LinearLayout wifiTabContent; // Wi-Fi tab içeriği
    private WifiTabBuilder wifiTabBuilder;
    private LinearLayout logTabContent; // LOG tab içeriği
    private LinearLayout appsTabContent; // Uygulamalar tab içeriği
    private AppsTabBuilder appsTabBuilder;
    private LinearLayout driveModeTabContent; // Hafıza Modu tab içeriği
    private ScrollView driveModeScrollView; // Hafıza Modu tab ScrollView
    private LinearLayout fileUploadTabContent; // Dosya Yükle tab içeriği
    private ScrollView fileUploadScrollView; // Dosya Yükle tab ScrollView
    private FileUploadTabBuilder fileUploadTabBuilder;
    private int currentTab = 0; // 0 = Wi-Fi, 1 = Dosya Yükle, 2 = Profil, 3 = Yansıtma, 4 = LOG, 5 = Uygulamalar, 6 = Hafıza Modu, 7 = Ayarlar
    private WebServerManager webServerManager; // HTTP Server Manager
    private Button btnWebServerToggle; // Web Server aç/kapat butonu
    private TextView webServerStatusText; // Web Server durum metni
    private android.widget.ImageView qrCodeImageView; // QR kod görseli
    private ServiceInitializer serviceInitializer;
    private SideRailBuilder sideRailBuilder;
    private VDBusManager vdbusManager;
    private LinearLayout topBarButtonsContainer; // Üst bar'daki buton container'ı (dinamik)
    private TextView topBarTitle; // Üst bar başlığı (dinamik)
    private ScrollView profileScrollView; // Profil tab ScrollView
    private ProfileApiService profileApiService; // API servisi

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler(Looper.getMainLooper());
        
        // Yasal uyarı ve onay ekranını göster (eğer daha önce kabul edilmediyse)
        SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
        boolean disclaimerAccepted = prefs.getBoolean("disclaimerAccepted", false);
        
        if (disclaimerAccepted) {
            // Daha önce kabul edilmiş, direkt uygulamayı başlat
            initializeApp();
        } else {
            // İlk kez açılıyor, yasal uyarıyı göster
            showLegalDisclaimer();
        }
    }

    /**
     * Yasal uyarı ve onay ekranını gösterir
     */
    private void showLegalDisclaimer() {
        DialogHelper.showLegalDisclaimer(this, () -> {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("disclaimerAccepted", true);
            editor.apply();
            initializeApp();
        }, this::finish);
    }

    /**
     * Uygulama Yönetimi için yasal uyarı ve onay ekranını gösterir
     */
    private void showAppManagementDisclaimer() {
        DialogHelper.showAppManagementDisclaimer(this, () -> {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            prefs.edit().putBoolean("appManagementDisclaimerAccepted", true).apply();
            switchTab(5);
            if (topBarTitle != null) topBarTitle.setText("Uygulama Yönetimi");
            if (sideRailBuilder != null) {
                sideRailBuilder.setSelectionForTabIndex(5);
            }
        }, () -> {});
    }

    /**
     * Uygulamayı başlatır (onCreate'in geri kalanı)
     */
    private void initializeApp() {
        // SharedPreferences (tüm bölümler için ortak)
        SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
        
        // ProfileApiService'i başlat
        profileApiService = new ProfileApiService(this);

        // Profil tab içeriği (Builder)
        ProfileTabBuilder profileTabBuilder = new ProfileTabBuilder(this, prefs, profileApiService,
                new ProfileTabBuilder.ProfileCallback() {
                    @Override
                    public void log(String msg) {
                        MainActivity.this.log(msg);
                    }
                });
        profileScrollView = profileTabBuilder.build();
        
        // SharedPreferences'tan targetPackage'ı yükle
        loadTargetPackage();

        clusterDisplayManager = new ClusterDisplayManager(this,
                new ClusterDisplayManager.ClusterCallback() {
                    @Override
                    public void onNavigationStateChanged(boolean isOpen) {
                        isNavigationOpen = isOpen;
                    }

                    @Override
                    public String getTargetPackage() {
                        return targetPackage;
                    }

                    @Override
                    public void log(String message) {
                        MainActivity.this.log(message);
                    }
                });
        
        // Servisleri başlat ve log alıcıyı kaydet
        serviceInitializer = new ServiceInitializer(this,
                new ServiceInitializer.ServiceCallback() {
                    @Override
                    public void onLogReceived(String message) {
                        MainActivity.this.log(message);
                    }

                    @Override
                    public void log(String msg) {
                        MainActivity.this.log(msg);
                    }
                });
        serviceInitializer.onCreate();
        

        // FrameLayout (Ana container - overlay için)
        FrameLayout rootContainer = new FrameLayout(this);
        rootContainer.setBackgroundColor(0xFF121212);

        // Ekran genişliğini al (%20/%80 bölme için)
        android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int sidebarWidth = (int)(screenWidth * 0.20f); // %20
        int mainContentWidth = (int)(screenWidth * 0.80f); // %80
        
        // Sol sabit kenar çubuğu (Builder)
        sideRailBuilder = new SideRailBuilder(this, prefs,
                new SideRailBuilder.SideRailCallback() {
                    @Override
                    public void onTabSelected(int tabIndex, String title) {
                        switchTab(tabIndex);
                        if (topBarTitle != null) {
                            topBarTitle.setText(title);
                        }
                    }

                    @Override
                    public void onAppManagementRequested() {
                        showAppManagementDisclaimer();
                    }

                    @Override
                    public void log(String msg) {
                        MainActivity.this.log(msg);
                    }
                });
        LinearLayout sideRail = sideRailBuilder.build();
        
        // Sol kenar çubuğunu ekle (%20 genişlik, tam yükseklik)
        FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(
                sidebarWidth,
                FrameLayout.LayoutParams.MATCH_PARENT);
        railParams.gravity = android.view.Gravity.START;
        rootContainer.addView(sideRail, railParams);

        // Ana içerik alanı (ekranın %80'i, header dahil)
        LinearLayout mainContent = new LinearLayout(this);
        mainContent.setOrientation(LinearLayout.VERTICAL);
        mainContent.setBackgroundColor(0xFF121212);
        
        // Üst başlık bar (Builder)
        TopBarBuilder topBarBuilder = new TopBarBuilder(this,
                new TopBarBuilder.TopBarCallback() {
                    @Override
                    public void onLogTabToggle(boolean show) {
                        if (logTabContent != null) {
                            logTabContent.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
                        }
                        if (show) {
                            switchTab(4);
                        } else if (currentTab == 4) {
                            switchTab(0);
                        }
                    }

                    @Override
                    public void log(String msg) {
                        MainActivity.this.log(msg);
                    }
                });
        LinearLayout topBar = topBarBuilder.build();
        topBarTitle = topBarBuilder.getTitleView();
        topBarButtonsContainer = topBarBuilder.getButtonsContainer();
        
        mainContent.addView(topBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Tab içerik alanı
        tabContentArea = new LinearLayout(this);
        tabContentArea.setOrientation(LinearLayout.VERTICAL);
        tabContentArea.setPadding(0, 0, 0, 0);
        tabContentArea.setBackgroundColor(0xFF1E1E1E);
        LinearLayout.LayoutParams tabContentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        mainContent.addView(tabContentArea, tabContentParams);
        
        // Ana içeriği ekle (sağ %80'lik alan)
        FrameLayout.LayoutParams mainContentParams = new FrameLayout.LayoutParams(
                mainContentWidth,
                FrameLayout.LayoutParams.MATCH_PARENT);
        mainContentParams.gravity = android.view.Gravity.END; // Sağa hizala
        rootContainer.addView(mainContent, mainContentParams);
        
        // WebServerManager'ı başlat
        webServerManager = new WebServerManager(this);
        webServerManager.setListener(new WebServerManager.WebServerListener() {
            @Override
            public void onServerStarted(int port, String localIp) {
                handler.post(() -> {
                    String serverUrl = "http://" + localIp + ":" + port;
                    if (webServerStatusText != null) {
                        webServerStatusText.setText(serverUrl);
                        webServerStatusText.setTextColor(0xFF3DAEA8);
                    }
                    if (btnWebServerToggle != null) {
                        btnWebServerToggle.setText("■ Web Server Durdur");
                    }
                    // QR kod oluştur
                    if (fileUploadTabBuilder != null) fileUploadTabBuilder.generateQRCode(serverUrl);
                    log("Web Server başlatıldı: " + serverUrl);
                });
            }

            @Override
            public void onServerStopped() {
                handler.post(() -> {
                    if (webServerStatusText != null) {
                        webServerStatusText.setText("Sunucu durduruldu");
                        webServerStatusText.setTextColor(0xAAFFFFFF);
                    }
                    if (qrCodeImageView != null) {
                        qrCodeImageView.setVisibility(android.view.View.GONE);
                    }
                    if (btnWebServerToggle != null) {
                        btnWebServerToggle.setText("▶ Web Server Başlat");
                    }
                    log("Web Server durduruldu");
                });
            }

            @Override
            public void onError(String error) {
                handler.post(() -> {
                    if (webServerStatusText != null) {
                        webServerStatusText.setText("Hata: " + error);
                        webServerStatusText.setTextColor(0xFFFF0000);
                    }
                    log("Web Server hatası: " + error);
                });
            }

            @Override
            public void onInstallApk(String fileName) {
                handler.post(() -> {
                    if (appsTabBuilder != null) appsTabBuilder.installApkFile(fileName);
                });
            }

            @Override
            public void onDeleteApp(String packageName) {
                handler.post(() -> {
                    if (appsTabBuilder != null) appsTabBuilder.deleteApp(packageName);
                });
            }

            @Override
            public void onLaunchApp(String packageName) {
                handler.post(() -> {
                    if (appsTabBuilder != null) appsTabBuilder.launchApp(packageName);
                });
            }

            @Override
            public void onLog(String message) {
                handler.post(() -> {
                    log(message);
                });
            }
        });

        // Dosya Yükle tab içeriği
        fileUploadTabBuilder = new FileUploadTabBuilder(this, webServerManager);
        fileUploadScrollView = fileUploadTabBuilder.getScrollView();
        btnWebServerToggle = fileUploadTabBuilder.getToggleButton();
        webServerStatusText = fileUploadTabBuilder.getStatusText();
        qrCodeImageView = fileUploadTabBuilder.getQrImageView();
        fileUploadTabContent = (LinearLayout) fileUploadScrollView.getChildAt(0);

        // Yansıtma tab içeriği (Builder)
        ProjectionTabBuilder projectionTabBuilder = new ProjectionTabBuilder(this, prefs,
                new ProjectionTabBuilder.ProjectionCallback() {
                    @Override
                    public void onOpenCluster() {
                        clusterDisplayManager.openClusterDisplay();
                    }

                    @Override
                    public void onCloseCluster() {
                        clusterDisplayManager.closeClusterDisplay(true);
                    }

                    @Override
                    public void onSavePowerMode(int mode) {
                        savePrefInt("powerModeSetting", mode);
                    }

                    @Override
                    public void onStartKeyEventListener() {
                        if (vdbusManager != null) {
                            vdbusManager.start();
                        }
                    }

                    @Override
                    public void onStopKeyEventListener() {
                        if (vdbusManager != null) {
                            vdbusManager.stop();
                        }
                    }

                    @Override
                    public String getTargetPackage() {
                        return targetPackage;
                    }

                    @Override
                    public void onTargetPackageSelected(String packageName) {
                        targetPackage = packageName != null ? packageName : "";
                        saveTargetPackage(targetPackage);
                        updateTargetLabel();
                        log("Seçilen uygulama: " + targetPackage);
                    }

                    @Override
                    public boolean isSystemOrPrivApp(ApplicationInfo appInfo) {
                        return MainActivity.this.isSystemOrPrivApp(appInfo);
                    }

                    @Override
                    public void log(String msg) {
                        MainActivity.this.log(msg);
                    }
                });
        projectionScrollView = projectionTabBuilder.getScrollView();
        projectionTabContent = projectionTabBuilder.getProjectionTabContent();
        targetAppLabel = projectionTabBuilder.getTargetAppLabel();

        // === HAFIZA MODU TAB İÇERİĞİ ===
        DriveModeTabBuilder driveModeTabBuilder = new DriveModeTabBuilder(this, prefs,
                new DriveModeTabBuilder.DriveModeCallback() {
                    @Override
                    public void onModeSelected(int modeValue) {
                        savePrefInt("driveModeSetting", modeValue);
                        log("Hafıza modu kaydedildi: " + modeValue);
                    }

                    @Override
                    public void log(String msg) {
                        MainActivity.this.log(msg);
                    }
                });
        driveModeScrollView = driveModeTabBuilder.getScrollView();
        driveModeTabContent = driveModeTabBuilder.getTabContent();

        AssistTabBuilder assistTabBuilder = new AssistTabBuilder(this, prefs,
                new AssistTabBuilder.AssistCallback() {
                    @Override
                    public void onSettingChanged(String key, int value) {
                        saveAssistSetting(key, value);
                    }

                    @Override
                    public void onSafetyWarningRequired(String key, int value, Runnable onUserConfirmed) {
                        DialogHelper.showSafetyWarningDialog(MainActivity.this, key, value, onUserConfirmed);
                    }

                    @Override
                    public void log(String message) {
                        MainActivity.this.log(message);
                    }
                });
        driveModeTabContent.addView(assistTabBuilder.build(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));


        wifiTabBuilder = new WifiTabBuilder(this, msg -> MainActivity.this.log(msg));
        wifiTabContent = wifiTabBuilder.getTabContent();

        final LogTabBuilder[] logTabBuilderHolder = new LogTabBuilder[1];
        logTabBuilderHolder[0] = new LogTabBuilder(this, new LogTabBuilder.LogCallback() {
            @Override
            public void onClearLogs() {
                logBuffer.setLength(0);
                if (logTabBuilderHolder[0] != null && logTabBuilderHolder[0].getLogsTextView() != null) {
                    logTabBuilderHolder[0].getLogsTextView().setText("");
                }
                log("Loglar temizlendi");
            }

            @Override
            public void log(String msg) {
                MainActivity.this.log(msg);
            }
        });
        logTabContent = logTabBuilderHolder[0].getTabContent();
        tvLogs = logTabBuilderHolder[0].getLogsTextView();
        scrollView = logTabBuilderHolder[0].getScrollView();

        appsTabBuilder = new AppsTabBuilder(this, new AppsTabBuilder.AppsCallback() {
            @Override
            public boolean isSystemOrPrivApp(ApplicationInfo appInfo) {
                return MainActivity.this.isSystemOrPrivApp(appInfo);
            }

            @Override
            public boolean isSystemOrPrivApp(String packageName) {
                return MainActivity.this.isSystemOrPrivApp(packageName);
            }

            @Override
            public void log(String msg) {
                MainActivity.this.log(msg);
            }
        });
        appsTabContent = MainActivity.this.appsTabBuilder.getTabContent();

        // Ayarlar tab içeriği (Builder) — initializeApp içinde bir kez oluşturulur
        SettingsTabBuilder settingsTabBuilder = new SettingsTabBuilder(this, prefs,
                new SettingsTabBuilder.SettingsCallback() {
                    @Override
                    public void log(String msg) {
                        MainActivity.this.log(msg);
                    }

                    @Override
                    public String getCarToken() {
                        return prefs.getString("carToken", null);
                    }
                });
        settingsScrollView = settingsTabBuilder.getScrollView();
        settingsTabContent = (LinearLayout) settingsScrollView.getChildAt(0);

        setContentView(rootContainer);
        
        // İlk tab'ı göster (Wi-Fi)
        switchTab(0);
        
        // Uygulamaları yükle
        if (appsTabBuilder != null) appsTabBuilder.loadAppsFromServer();

        vdbusManager = new VDBusManager(this, new VDBusManager.VDBusCallback() {
            @Override
            public void onNavKeyOpen() {
                if (!isNavigationOpen) {
                    clusterDisplayManager.openClusterDisplay();
                }
            }

            @Override
            public void onNavKeyClose() {
                if (isNavigationOpen) {
                    clusterDisplayManager.closeClusterDisplay(false);
                }
            }

            @Override
            public void onAlertTone() {
                playSoftAlert();
            }

            @Override
            public void log(String message) {
                MainActivity.this.log(message);
            }
        });

        boolean mapControlKeyEnabled = prefs.getBoolean("mapControlKeyEnabled", true);
        if (mapControlKeyEnabled) {
            vdbusManager.init();
        }

        // Otomatik seçim modu: Uygulama açıldığında önerilen uygulamayı otomatik seç
        autoSelectPreferredApp();
    }

    /**
     * logcat'i dinleyerek SMS servisinden gelen key event'leri yakalar.
     * VDS-SMS-SourceFile tag'ini ve keyCode pattern'ini dinler.
     */
    private Thread keyEventLogcatThread;
    private volatile boolean keyEventLogcatRunning = false;
    
    // VDBus üzerinden doğrudan key event dinleme (SMS key event subscribe)
    private MediaPlayer alertMediaPlayer = null;
    private final Object alertMediaPlayerLock = new Object();
    
    /**
     * Navigation Display Cluster ve Display Area event'lerine subscribe olur.
     */
    /**
     * soft_alert.mp3 dosyasını güvenli bir şekilde çalar.
     * Programın çökmesini veya loop'ta kalmasını önler.
     */
    private void playSoftAlert() {
        // Arka plan thread'inde çalıştır (UI thread'i bloklamamak için)
        new Thread(() -> {
            synchronized (alertMediaPlayerLock) {
                try {
                    // Eğer zaten çalıyorsa durdur ve temizle
                    if (alertMediaPlayer != null) {
                        try {
                            if (alertMediaPlayer.isPlaying()) {
                                alertMediaPlayer.stop();
                            }
                            alertMediaPlayer.release();
                        } catch (Exception e) {
                            log("Alert MediaPlayer temizleme hatası: " + e.getMessage());
                        }
                        alertMediaPlayer = null;
                    }

                    // Yeni MediaPlayer oluştur
                    alertMediaPlayer = new MediaPlayer();
                    
                    // AudioAttributes ayarla (ses çıkışı için)
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build();
                    alertMediaPlayer.setAudioAttributes(audioAttributes);
                    
                    // Assets'ten dosyayı yükle
                    android.content.res.AssetFileDescriptor afd = getAssets().openFd("soft_alert.mp3");
                    alertMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    afd.close();
                    
                    // Hazırla
                    alertMediaPlayer.prepare();
                    
                    // Çalma bitince temizle (loop'ta kalmaması için)
                    alertMediaPlayer.setOnCompletionListener(mp -> {
                        synchronized (alertMediaPlayerLock) {
                            try {
                                if (mp != null) {
                                    mp.release();
                                }
                            } catch (Exception e) {
                                log("Alert MediaPlayer completion release hatası: " + e.getMessage());
                            }
                            if (alertMediaPlayer == mp) {
                                alertMediaPlayer = null;
                            }
                        }
                        log("soft_alert.mp3 çalma tamamlandı");
                    });
                    
                    // Hata durumunda temizle
                    alertMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                        synchronized (alertMediaPlayerLock) {
                            try {
                                if (mp != null) {
                                    mp.release();
                                }
                            } catch (Exception e) {
                                log("Alert MediaPlayer error release hatası: " + e.getMessage());
                            }
                            if (alertMediaPlayer == mp) {
                                alertMediaPlayer = null;
                            }
                        }
                        log("soft_alert.mp3 çalma hatası: what=" + what + " extra=" + extra);
                        return true; // Hatayı handle ettik
                    });
                    
                    // Çal
                    alertMediaPlayer.start();
                    log("soft_alert.mp3 çalınıyor");
                    
                } catch (IOException e) {
                    log("soft_alert.mp3 dosyası açılamadı: " + e.getMessage());
                    // Hata durumunda MediaPlayer'ı temizle
                    if (alertMediaPlayer != null) {
                        try {
                            alertMediaPlayer.release();
                        } catch (Exception ex) {
                            // Ignore
                        }
                        alertMediaPlayer = null;
                    }
                } catch (Exception e) {
                    log("soft_alert.mp3 çalma hatası: " + e.getMessage());
                    // Hata durumunda MediaPlayer'ı temizle
                    if (alertMediaPlayer != null) {
                        try {
                            alertMediaPlayer.release();
                        } catch (Exception ex) {
                            // Ignore
                        }
                        alertMediaPlayer = null;
                    }
                }
            }
        }).start();
    }

    /**
     * Bir uygulamanın sistem uygulaması veya priv-app olup olmadığını kontrol eder
     */
    /**
     * ApplicationInfo ile sistem/priv uygulaması kontrolü (WebServerManager'daki gibi)
     */
    private boolean isSystemOrPrivApp(ApplicationInfo appInfo) {
        try {
            if (appInfo == null) {
                return true;
            }
            
            // com.mapcontrol hariç tut
            if ("com.mapcontrol".equals(appInfo.packageName)) {
                return false;
            }
            
            // Sistem uygulaması kontrolü
            boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isUpdatedSystemApp = (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            
            if (isSystemApp || isUpdatedSystemApp) {
                return true;
            }
            
            // Priv-app kontrolü (sourceDir ve publicSourceDir kontrolü)
            String sourceDir = appInfo.sourceDir;
            String publicSourceDir = appInfo.publicSourceDir;
            
            if (sourceDir != null && (sourceDir.contains("/system/priv-app/") || sourceDir.contains("/system/app/"))) {
                return true;
            }
            
            if (publicSourceDir != null && (publicSourceDir.contains("/system/priv-app/") || publicSourceDir.contains("/system/app/"))) {
                return true;
            }
            
            return false;
        } catch (Exception e) {
            // Hata durumunda güvenli tarafta kal (sistem uygulaması say)
            return true;
        }
    }
    
    /**
     * Package name ile sistem/priv uygulaması kontrolü
     */
    private boolean isSystemOrPrivApp(String packageName) {
        try {
            if (packageName == null || packageName.isEmpty()) {
                return true; // Null veya boş ise sistem uygulaması say
            }
            
            // com.mapcontrol hariç tut
            if (packageName.equals("com.mapcontrol")) {
                return false;
            }
            
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            
            // ApplicationInfo overload'unu kullan
            return isSystemOrPrivApp(appInfo);
        } catch (Exception e) {
            log("isSystemOrPrivApp kontrol hatası (" + packageName + "): " + e.getMessage());
            // Hata durumunda güvenli tarafta kal, sistem uygulaması say
            return true;
        }
    }
    
    private void showPreparingMessageOnDisplay(int displayId) {
        DisplayHelper.showPreparingMessageOnDisplay(this, displayId);
    }

    private void hidePreparingMessage() {
        DisplayHelper.hidePreparingMessage();
    }

    /**
     * Cluster display ID'sini al
     */
    private int getClusterDisplayId() {
        try {
            DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            Display[] displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
            if (displays.length == 0) {
                displays = dm.getDisplays();
            }
            for (Display d : displays) {
                if (d.getDisplayId() != Display.DEFAULT_DISPLAY) {
                    return d.getDisplayId();
                }
            }
        } catch (Exception e) {
            log("getClusterDisplayId hatası: " + e.getMessage());
        }
        return 2; // Varsayılan cluster display ID
    }

    /**
     * Otomatik seçim modu: Uygulama açıldığında önerilen uygulamayı otomatik seç
     */
    private void autoSelectPreferredApp() {
        try {
            PackageManager pm = getPackageManager();
            String[] preferred = new String[] {
                    "ru.yandex.yandexnavi",
                    "ru.yandex.yandexmaps",
                    "com.google.android.apps.maps",
                    "com.waze",
                    "com.sygic.aura"
            };

            for (String pkg : preferred) {
                try {
                    Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
                    if (launchIntent != null) {
                        targetPackage = pkg;
                        saveTargetPackage(pkg);
                        updateTargetLabel();
                        log("Otomatik seçim: " + pkg);
                        return;
                    }
                } catch (Exception e) {
                    continue;
                }
            }
            log("Önerilen uygulama bulunamadı, manuel seçim gerekli");
        } catch (Exception e) {
            log("autoSelectPreferredApp hatası: " + e.getMessage());
        }
    }

    // createMenuItemView / createRailMenuItemView SideRailBuilder'a taşındı.
    
    // updateMenuSelection artık SideRailBuilder içinde yönetiliyor.

    /**
     * Tab değiştirme metodu
     * 0 = Wi-Fi, 1 = Dosya Yükle, 2 = Profil, 3 = Yansıtma, 4 = LOG, 5 = Uygulamalar, 6 = Hafıza Modu, 7 = Ayarlar
     */
    private void switchTab(int tabIndex) {
        if (tabContentArea == null || projectionTabContent == null || wifiTabContent == null || logTabContent == null || appsTabContent == null || driveModeTabContent == null || fileUploadTabContent == null) {
            return;
        }

        currentTab = tabIndex;
        tabContentArea.removeAllViews();
        
        // TopBar buton container'ını temizle
        if (topBarButtonsContainer != null) {
            topBarButtonsContainer.removeAllViews();
        }

        if (tabIndex == 0) {
            // Wi-Fi tab'ı aktif
            tabContentArea.addView(wifiTabContent);
            if (topBarTitle != null) topBarTitle.setText("☰ Wi-Fi Yönetimi");
            if (topBarButtonsContainer != null && wifiTabBuilder != null) topBarButtonsContainer.addView(wifiTabBuilder.buildTopBarIcon());
            if (wifiTabBuilder != null) wifiTabBuilder.updateWifiStatus();
        } else if (tabIndex == 1) {
            // Dosya Yükle tab'ı aktif
            tabContentArea.addView(fileUploadScrollView);
            // Dosya Yükle tab'ında buton yok
        } else if (tabIndex == 2) {
            // Profil tab'ı aktif
            tabContentArea.addView(profileScrollView);
            // Profil tab'ında buton yok
        } else if (tabIndex == 3) {
            // Yansıtma tab'ı aktif
            tabContentArea.addView(projectionScrollView);
            // Yansıtma tab'ında buton yok
        } else if (tabIndex == 4) {
            // LOG tab'ı aktif (Sistem Kayıtları)
            tabContentArea.addView(logTabContent);
            // LOG tab'ında buton yok
        } else if (tabIndex == 5) {
            // Uygulamalar tab'ı aktif
            tabContentArea.addView(appsTabContent);
            if (topBarTitle != null) topBarTitle.setText("☰ Uygulamalar");
            if (topBarButtonsContainer != null && appsTabBuilder != null) topBarButtonsContainer.addView(appsTabBuilder.buildTopBarButtons(this));
        } else if (tabIndex == 6) {
            // Hafıza Modu tab'ı aktif
            tabContentArea.addView(driveModeScrollView);
            // Hafıza Modu tab'ında buton yok
        } else if (tabIndex == 7) {
            // Ayarlar tab'ı aktif
            tabContentArea.addView(settingsScrollView);
            // Ayarlar tab'ında buton yok
        }
    }


    /**
     * Seçilen uygulamayı TextView'de göster
     */
    private void updateTargetLabel() {
        if (targetAppLabel != null) {
            if (targetPackage == null || targetPackage.trim().isEmpty()) {
                targetAppLabel.setText("(seçilmedi)");
            } else {
                try {
                    PackageManager pm = getPackageManager();
                    ApplicationInfo appInfo = pm.getApplicationInfo(targetPackage, 0);
                    String appName = pm.getApplicationLabel(appInfo).toString();
                    targetAppLabel.setText(appName);
                } catch (Exception e) {
                    targetAppLabel.setText(targetPackage);
                }
            }
        }
    }

    private String now() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
    }

    // QR üretimi `FileUploadTabBuilder.generateQRCode()` içine taşındı.

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // WebServerManager'ı durdur
        if (webServerManager != null) {
            webServerManager.stopServer();
        }
        // KeyEvent logcat thread'ini temizle
        keyEventLogcatRunning = false;
        if (keyEventLogcatThread != null) {
            keyEventLogcatThread.interrupt();
        }
        if (vdbusManager != null) vdbusManager.destroy();
        // Alert MediaPlayer'ı temizle
        synchronized (alertMediaPlayerLock) {
            if (alertMediaPlayer != null) {
                try {
                    if (alertMediaPlayer.isPlaying()) {
                        alertMediaPlayer.stop();
                    }
                    alertMediaPlayer.release();
                } catch (Exception e) {
                    // Ignore
                }
                alertMediaPlayer = null;
            }
        }
        if (serviceInitializer != null) serviceInitializer.onDestroy();
    }

    private void log(String msg) {
        String timestamp = now();
        String line = "[" + timestamp + "] " + msg + "\n";
        
        // Renkli log seviyelerini tespit et ve ayarla
        String coloredLine = line;
        if (msg.contains("[INFO]") || msg.contains("ℹ️") || msg.contains("📡") || msg.contains("🔌")) {
            coloredLine = "[" + timestamp + "] 🔵 " + msg.replace("[INFO]", "") + "\n";
        } else if (msg.contains("[WARN]") || msg.contains("⚠️")) {
            coloredLine = "[" + timestamp + "] ⚠️ " + msg.replace("[WARN]", "") + "\n";
        } else if (msg.contains("[ERROR]") || msg.contains("❌") || msg.contains("ERR")) {
            coloredLine = "[" + timestamp + "] 🔴 " + msg.replace("[ERROR]", "") + "\n";
        } else if (msg.contains("[SUCCESS]") || msg.contains("✅") || msg.contains("✓")) {
            coloredLine = "[" + timestamp + "] ✅ " + msg.replace("[SUCCESS]", "") + "\n";
        } else if (msg.contains("[DEBUG]") || msg.contains("🐛") || msg.contains("DBG")) {
            coloredLine = "[" + timestamp + "] 🟣 " + msg.replace("[DEBUG]", "") + "\n";
        }
        
        logBuffer.append(coloredLine);
        handler.post(() -> {
            tvLogs.setText(logBuffer.toString());
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    // App operations moved to `AppsTabBuilder`.

    /**
     * Foreground Service'i başlat (arka planda çalışması için)
     */
    private void savePrefInt(String key, int value) {
        getSharedPreferences("MapControlPrefs", MODE_PRIVATE).edit().putInt(key, value).apply();
    }

    private void savePrefBool(String key, boolean value) {
        getSharedPreferences("MapControlPrefs", MODE_PRIVATE).edit().putBoolean(key, value).apply();
    }

    // saveDriveModeSetting / savePowerModeSetting / saveMapControlKeySetting / saveAutoCloseOnPowerOffSetting removed.

    /**
     * Seçili ISS ayarını kaydet
     */
    private void saveAssistSetting(String settingKey, int value) {
        savePrefInt(settingKey, value);
    }

    /**
     * SharedPreferences'a targetPackage'ı kaydet
     */
    private void saveTargetPackage(String packageName) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("targetPackage", packageName);
            editor.apply();
        } catch (Exception e) {
            log("saveTargetPackage hatası: " + e.getMessage());
        }
    }

    /**
     * SharedPreferences'tan targetPackage'ı yükle
     */
    private void loadTargetPackage() {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            String savedPackage = prefs.getString("targetPackage", null);
            if (savedPackage != null && !savedPackage.trim().isEmpty()) {
                targetPackage = savedPackage;
                log("Kaydedilmiş uygulama yüklendi: " + targetPackage);
            }
        } catch (Exception e) {
            log("loadTargetPackage hatası: " + e.getMessage());
        }
    }

    // App operations moved to `AppsTabBuilder`.
    
    // Profil sekmesi UI/logic `ProfileTabBuilder` içine taşındı.
    
    // Profil sekmesi helper/metodları `ProfileTabBuilder` içine taşındı.
    // parseMarkdown / processBoldText `SettingsTabBuilder` içine taşındı.
    
    // Settings tab builder is created in initializeApp.
}
