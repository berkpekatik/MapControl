package com.mapcontrol.ui.activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
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
import java.util.ArrayList;
import java.util.List;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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
import com.mapcontrol.admin.MapControlDpmHelper;
import com.mapcontrol.R;
import com.mapcontrol.api.ProfileApiService;
import com.mapcontrol.manager.ClusterDisplayManager;
import com.mapcontrol.manager.ProjectionVDBusTargetPickerManager;
import com.mapcontrol.manager.VDBusManager;
import com.mapcontrol.manager.WebServerManager;
import com.mapcontrol.service.GlobalBackService;
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
import com.mapcontrol.util.IflyOemTtsHelper;
import com.mapcontrol.util.DialogHelper;
import com.mapcontrol.util.DisplayHelper;
import com.mapcontrol.util.TargetPackageStore;
import com.mapcontrol.ui.theme.UiStyles;

public class MainActivity extends AppCompatActivity {

    /** Yüzen yansıtma çubuğundan hedef uygulama seçiciyi açmak için {@link Intent} extra anahtarı. */
    public static final String EXTRA_OPEN_PROJECTION_TARGET_PICKER = "com.mapcontrol.extra.OPEN_PROJECTION_TARGET_PICKER";
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
    private LinearLayout fileUploadTabContent; // Web Yönetimi tab içeriği
    private ScrollView fileUploadScrollView; // Web Yönetimi tab ScrollView
    private FileUploadTabBuilder fileUploadTabBuilder;
    private int currentTab = 0; // 0 = Wi-Fi, 1 = Web Yönetimi, 2 = Profil, 3 = Yansıtma, 4 = LOG, 5 = Uygulamalar, 6 = Hafıza Modu, 7 = Ayarlar
    private WebServerManager webServerManager; // HTTP Server Manager
    private Button btnWebServerToggle; // Web Server aç/kapat butonu
    private TextView webServerStatusText; // Web Server durum metni
    private android.widget.ImageView qrCodeImageView; // QR kod görseli
    private ServiceInitializer serviceInitializer;
    private SideRailBuilder sideRailBuilder;
    private VDBusManager vdbusManager;
    /** {@link ClusterVDBusTestActivity} bench — yalnızca görünürken; {@link #onResume()} atanır. */
    private static volatile MainActivity sBenchHost;
    /** Oturumda LOG sekmesine ilk geçişte hoşgeldin TTS bir kez */
    private boolean logWelcomeTtsDone;
    private LinearLayout topBarButtonsContainer; // Üst bar'daki buton container'ı (dinamik)
    private TextView topBarTitle; // Üst bar başlığı (dinamik)
    private ScrollView profileScrollView; // Profil tab ScrollView
    private ProfileApiService profileApiService; // API servisi
    private FrameLayout mainRootContainer;
    private ProjectionTabBuilder projectionTabBuilder;
    /** Yüzen kontrolden veya tekilleştirilmiş intent ile hedef uygulama diyaloğu ertelenmiş. */
    private boolean deferredOpenProjectionTargetPicker;
    private boolean targetPackageBroadcastRegistered;
    private final BroadcastReceiver targetPackageUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (handler != null) {
                handler.post(() -> {
                    loadTargetPackage();
                    updateTargetLabel();
                });
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler(Looper.getMainLooper());
        if (savedInstanceState == null && getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_OPEN_PROJECTION_TARGET_PICKER, false)) {
            deferredOpenProjectionTargetPicker = true;
            getIntent().removeExtra(EXTRA_OPEN_PROJECTION_TARGET_PICKER);
        }

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
        MapControlDpmHelper.tryBlockOwnUninstallIfDeviceOwner(this);
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
        mainRootContainer = new FrameLayout(this);
        UiStyles.setRootBackground(mainRootContainer);

        // Sol şerit: geniş ekranda biraz daralt, dar ekranda biraz aç; min/max dp ile clamp
        android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int sidebarWidth = computeSidebarWidthPx(screenWidth, displayMetrics.density);
        int mainContentWidth = screenWidth - sidebarWidth;
        
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
        mainRootContainer.addView(sideRail, railParams);

        // Ana içerik alanı (ekranın %80'i, header dahil)
        LinearLayout mainContent = new LinearLayout(this);
        mainContent.setOrientation(LinearLayout.VERTICAL);
        mainContent.setBackgroundColor(ContextCompat.getColor(this, R.color.transparent));
        
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
        UiStyles.setTabContentBackdrop(tabContentArea);
        LinearLayout.LayoutParams tabContentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        mainContent.addView(tabContentArea, tabContentParams);
        
        // Ana içeriği ekle (sağ %80'lik alan)
        FrameLayout.LayoutParams mainContentParams = new FrameLayout.LayoutParams(
                mainContentWidth,
                FrameLayout.LayoutParams.MATCH_PARENT);
        mainContentParams.gravity = android.view.Gravity.END; // Sağa hizala
        mainRootContainer.addView(mainContent, mainContentParams);
        
        // WebServerManager'ı başlat
        webServerManager = new WebServerManager(this);
        webServerManager.setListener(new WebServerManager.WebServerListener() {
            @Override
            public void onServerStarted(int port, String localIp) {
                handler.post(() -> {
                    String serverUrl = "http://" + localIp + ":" + port;
                    if (webServerStatusText != null) {
                        webServerStatusText.setText(serverUrl);
                        webServerStatusText.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.accentHighlight));
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
                        webServerStatusText.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.textDialogButtonSecondary));
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
                        webServerStatusText.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.statusErrorBright));
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

            @Override
            public void onOpenMapUrl(String url) {
                handler.post(() -> {
                    try {
                        String u = url != null ? url.trim() : "";
                        if (u.isEmpty()) {
                            return;
                        }
                        Uri uri = Uri.parse(u);
                        Intent i = new Intent(Intent.ACTION_VIEW, uri);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        String scheme = uri.getScheme();
                        if (scheme != null
                                && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                            i.addCategory(Intent.CATEGORY_BROWSABLE);
                        }
                        if (shouldShowChooserToPickBrowserOrMaps(uri)) {
                            // Yandex /maps, goo.gl harita kısası: önce tek hedef uyg. sessizce çalışıp
                            // ekran göstermeyebiliyor; listeden tarayıcı seçilebilir.
                            Intent chooser = Intent.createChooser(i, "Linki aç (tarayıcı önerilir)");
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(chooser);
                        } else {
                            try {
                                startActivity(i);
                            } catch (android.content.ActivityNotFoundException noHandler) {
                                Intent chooser = Intent.createChooser(i, "Bağlantıyı aç");
                                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(chooser);
                            }
                        }
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this,
                                "Link açılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        log("Link açma hatası: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onTypeKeyboardText(String text, boolean showDeviceFeedback) {
                handler.post(() -> {
                    if (!GlobalBackService.isRegisteredInSystemAccessibilitySettings(MainActivity.this)) {
                        if (showDeviceFeedback) {
                            Toast.makeText(MainActivity.this,
                                    "Ayarlar > Erişilebilirlik: Global Back servisini açın; diğer uygulamada girdi alanının odaklı olması gerekir.",
                                    Toast.LENGTH_LONG).show();
                        }
                        return;
                    }
                    if (GlobalBackService.typeIntoFocusedField(MainActivity.this, text)) {
                        if (showDeviceFeedback) {
                            Toast.makeText(MainActivity.this, "Metin gönderildi", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        if (showDeviceFeedback) {
                            Toast.makeText(MainActivity.this,
                                    "Metin eklenemedi. Hedef uygulamada arama/Metin alanına bir kez dokunup odağın açık olduğundan emin olun (bazı uygulamalar ağaçta desteklemez).",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });

        // Web Yönetimi tab içeriği
        fileUploadTabBuilder = new FileUploadTabBuilder(this, webServerManager);
        fileUploadScrollView = fileUploadTabBuilder.getScrollView();
        btnWebServerToggle = fileUploadTabBuilder.getToggleButton();
        webServerStatusText = fileUploadTabBuilder.getStatusText();
        qrCodeImageView = fileUploadTabBuilder.getQrImageView();
        fileUploadTabContent = fileUploadTabBuilder.getFileUploadTabContent();

        // Yansıtma tab içeriği (Builder)
        projectionTabBuilder = new ProjectionTabBuilder(this, prefs,
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
                        saveTargetPackage(packageName);
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

                    @Override
                    public void onBringToMainDisplayCheckClusterSplash() {
                        if (clusterDisplayManager != null) {
                            clusterDisplayManager.showBootSplashOnClusterIfNoForegroundApp();
                        }
                    }
                });
        projectionScrollView = projectionTabBuilder.getScrollView();
        projectionTabContent = projectionTabBuilder.getProjectionTabContent();
        targetAppLabel = projectionTabBuilder.getTargetAppLabel();
        tryConsumeDeferredProjectionTargetPicker();

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

            @Override
            public void onReadAloud(String text) {
                speakTtsText(text);
            }

            @Override
            public void onWelcomeTts() {
                speakTtsText(getString(R.string.log_tts_welcome_phrase));
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
        settingsTabContent = settingsTabBuilder.getSettingsTabContent();

        setContentView(mainRootContainer);
        
        // İlk tab'ı göster (Wi-Fi)
        switchTab(0);
        
        // Uygulamaları yükle
        if (appsTabBuilder != null) appsTabBuilder.loadAppsFromServer();

        vdbusManager = new VDBusManager(this, new VDBusManager.VDBusCallback() {
            @Override
            public void onNavKeyToggle() {
                if (isNavigationOpen) {
                    clusterDisplayManager.closeClusterDisplay(false);
                } else {
                    clusterDisplayManager.openClusterDisplay();
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

            @Override
            public void onProjectionTargetPickerToggle() {
                ProjectionVDBusTargetPickerManager.openIfClosed(MainActivity.this,
                        message -> MainActivity.this.log(message));
            }

            @Override
            public void onProjectionTargetPickerKeyRight() {
                ProjectionVDBusTargetPickerManager.advanceSelectionIfOpen(MainActivity.this);
            }

            @Override
            public void onProjectionTargetPickerKeyLeft() {
                ProjectionVDBusTargetPickerManager.retreatSelectionIfOpen(MainActivity.this);
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
     * İlk kurulum / boş hedef: yüklü önerilen uygulamalardan birini otomatik seçer.
     * Daha önce kaydedilmiş {@code targetPackage} hâlâ başlatılabiliyorsa onu ezmek için çağrılmamalı;
     * aksi halde her açılışta liste sırasındaki ilk uygulama (ör. Yandex) ile kullanıcı seçiminin üstüne yazılırdı.
     */
    private void autoSelectPreferredApp() {
        try {
            PackageManager pm = getPackageManager();
            if (targetPackage != null && !targetPackage.trim().isEmpty()) {
                try {
                    if (pm.getLaunchIntentForPackage(targetPackage.trim()) != null) {
                        updateTargetLabel();
                        return;
                    }
                } catch (Exception ignored) {
                    // Kaldırıldı / geçersiz: aşağıda öneri listesinden yeniden dene
                }
            }
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
     * 0 = Wi-Fi, 1 = Web Yönetimi, 2 = Profil, 3 = Yansıtma, 4 = LOG, 5 = Uygulamalar, 6 = Hafıza Modu, 7 = Ayarlar
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
            if (topBarTitle != null) topBarTitle.setText("Wi-Fi Yönetimi");
            if (topBarButtonsContainer != null && wifiTabBuilder != null) topBarButtonsContainer.addView(wifiTabBuilder.buildTopBarIcon());
            if (wifiTabBuilder != null) wifiTabBuilder.updateWifiStatus();
        } else if (tabIndex == 1) {
            // Web Yönetimi tab'ı aktif
            tabContentArea.addView(fileUploadScrollView);
            // Web Yönetimi tab'ında buton yok
        } else if (tabIndex == 2) {
            // Profil tab'ı aktif
            tabContentArea.addView(profileScrollView);
            // Profil tab'ında buton yok
        } else if (tabIndex == 3) {
            // Yansıtma tab'ı aktif
            tabContentArea.addView(projectionScrollView);
            // Yansıtma tab'ında buton yok
        } else if (tabIndex == 4) {
            // LOG tab'ı aktif (Sistem Kayıtları) — kök yükseklik MATCH_PARENT olmalı; yoksa
            // içteki terminal ScrollView (weight) ölçüde 0 kalır ve kaydırma çalışmaz.
            tabContentArea.addView(logTabContent, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            if (topBarTitle != null) {
                topBarTitle.setText("Sistem Kayıtları");
            }
            if (!logWelcomeTtsDone) {
                logWelcomeTtsDone = true;
                handler.postDelayed(
                        () -> speakTtsText(getString(R.string.log_tts_welcome_phrase)), 450);
            }
        } else if (tabIndex == 5) {
            // Uygulamalar tab'ı aktif
            tabContentArea.addView(appsTabContent);
            if (topBarTitle != null) topBarTitle.setText("Uygulamalar");
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

    /**
     * iFly OEM xTTS ({@link IflyOemTtsHelper}); yalnız bu yol. Başarısızlık loglanır.
     */
    private void speakTtsText(String text) {
        if (text == null) {
            return;
        }
        String t = text.trim();
        if (t.isEmpty()) {
            log("TTS: metin boş");
            return;
        }
        IflyOemTtsHelper.trySpeak(this, t, this::log);
    }

    private String now() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
    }

    // QR üretimi `FileUploadTabBuilder.generateQRCode()` içine taşındı.

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_PROJECTION_TARGET_PICKER, false)) {
            intent.removeExtra(EXTRA_OPEN_PROJECTION_TARGET_PICKER);
            deferredOpenProjectionTargetPicker = true;
        }
    }

    private void tryConsumeDeferredProjectionTargetPicker() {
        if (!deferredOpenProjectionTargetPicker || projectionTabBuilder == null) {
            return;
        }
        deferredOpenProjectionTargetPicker = false;
        handler.post(() -> projectionTabBuilder.openTargetAppPicker());
    }

    @Override
    protected void onResume() {
        super.onResume();
        sBenchHost = this;
        tryConsumeDeferredProjectionTargetPicker();
        registerTargetPackageBroadcastReceiver();
    }

    @Override
    protected void onPause() {
        unregisterTargetPackageBroadcastReceiver();
        super.onPause();
    }

    private void registerTargetPackageBroadcastReceiver() {
        if (targetPackageBroadcastRegistered) {
            return;
        }
        try {
            IntentFilter filter = new IntentFilter(TargetPackageStore.ACTION_TARGET_PACKAGE_UPDATED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(targetPackageUpdatedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(targetPackageUpdatedReceiver, filter);
            }
            targetPackageBroadcastRegistered = true;
        } catch (Exception e) {
            log("Hedef paket yayını kaydı: " + e.getMessage());
        }
    }

    private void unregisterTargetPackageBroadcastReceiver() {
        if (!targetPackageBroadcastRegistered) {
            return;
        }
        try {
            unregisterReceiver(targetPackageUpdatedReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        targetPackageBroadcastRegistered = false;
    }

    @Override
    protected void onDestroy() {
        if (sBenchHost == this) {
            sBenchHost = null;
        }
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
            coloredLine = "[" + timestamp + "] INFO " + msg.replace("[INFO]", "").trim() + "\n";
        } else if (msg.contains("[WARN]") || msg.contains("⚠️")) {
            coloredLine = "[" + timestamp + "] WARN " + msg.replace("[WARN]", "").trim() + "\n";
        } else if (msg.contains("[ERROR]") || msg.contains("❌") || msg.contains("ERR")) {
            coloredLine = "[" + timestamp + "] ERR " + msg.replace("[ERROR]", "").trim() + "\n";
        } else if (msg.contains("[SUCCESS]") || msg.contains("✅") || msg.contains("✓")) {
            coloredLine = "[" + timestamp + "] OK " + msg.replace("[SUCCESS]", "").trim() + "\n";
        } else if (msg.contains("[DEBUG]") || msg.contains("🐛") || msg.contains("DBG")) {
            coloredLine = "[" + timestamp + "] DBG " + msg.replace("[DEBUG]", "").trim() + "\n";
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
     * Araç / tablet: şerit genişliği ekranın ~%30–36’sı + dp clamp ({@code rail_min_width}..{@code rail_max_width}).
     */
    private int computeSidebarWidthPx(int screenWidthPx, float density) {
        int screenWidthDp = Math.round(screenWidthPx / density);
        float ratio;
        if (screenWidthDp >= 960) {
            ratio = 0.30f;
        } else if (screenWidthDp >= 720) {
            ratio = 0.31f;
        } else if (screenWidthDp >= 600) {
            ratio = 0.32f;
        } else {
            ratio = 0.34f;
        }
        int w = Math.round(screenWidthPx * ratio);
        int minPx = Math.round(getResources().getDimension(R.dimen.rail_min_width));
        int maxPx = Math.round(getResources().getDimension(R.dimen.rail_max_width));
        return Math.max(minPx, Math.min(maxPx, w));
    }

    /**
     * SharedPreferences'a targetPackage'ı kaydet
     */
    private void saveTargetPackage(String packageName) {
        try {
            targetPackage = TargetPackageStore.normalize(packageName);
            TargetPackageStore.writeAndBroadcast(this, targetPackage);
        } catch (Exception e) {
            log("saveTargetPackage hatası: " + e.getMessage());
        }
    }

    /**
     * SharedPreferences'tan targetPackage'ı yükle (boş kayıtta bellek de temizlenir).
     */
    private void loadTargetPackage() {
        try {
            targetPackage = TargetPackageStore.read(this);
            if (targetPackage != null && !targetPackage.isEmpty()) {
                log("Kaydedilmiş uygulama yüklendi: " + targetPackage);
            }
        } catch (Exception e) {
            log("loadTargetPackage hatası: " + e.getMessage());
        }
    }

    /**
     * Aynı host’ta /q açılırken /maps gibi yollarda Yandex Haritalar (veya App Link) intent’i alıp
     * ekran göstermeyebiliyor; doğrudan tarayıcı hiç seçilmiyor. Bu URL’lerde önce seçici gösterilir.
     */
    private static boolean shouldShowChooserToPickBrowserOrMaps(Uri uri) {
        if (uri == null) {
            return false;
        }
        String h = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
        String p = uri.getPath() != null ? uri.getPath().toLowerCase() : "";
        if (h.contains("yandex") && p.contains("maps")) {
            return true;
        }
        if (h.equals("maps.app.goo.gl")) {
            return true;
        }
        return false;
    }

    // App operations moved to `AppsTabBuilder`.
    
    // Profil sekmesi UI/logic `ProfileTabBuilder` içine taşındı.
    
    // Profil sekmesi helper/metodları `ProfileTabBuilder` içine taşındı.
    // parseMarkdown / processBoldText `SettingsTabBuilder` içine taşındı.
    
    // Settings tab builder is created in initializeApp.

    /**
     * Bench ekranından ana loga satır düşer (MainActivity yaşıyorsa).
     */
    public static void appendBenchLogToMainIfActive(String msg) {
        MainActivity a = sBenchHost;
        if (a == null || msg == null) {
            return;
        }
        a.handler.post(() -> a.log(msg));
    }

    /** VDBus 26/4 ile aynı cluster toggle yolu. */
    public static void benchNavKeyToggle() {
        MainActivity a = sBenchHost;
        if (a == null) {
            Log.w("MainActivity", "[Bench] Ana ekran hazır değil (MainActivity yok)");
            return;
        }
        a.handler.post(() -> {
            if (a.clusterDisplayManager == null) {
                a.log("[Bench] clusterDisplayManager yok");
                return;
            }
            if (a.isNavigationOpen) {
                a.clusterDisplayManager.closeClusterDisplay(false);
            } else {
                a.clusterDisplayManager.openClusterDisplay();
            }
        });
    }

    public static void benchAlertTone() {
        MainActivity a = sBenchHost;
        if (a == null) {
            Log.w("MainActivity", "[Bench] Ana ekran hazır değil; uyarı sesi atlandı");
            return;
        }
        a.handler.post(a::playSoftAlert);
    }

    public static void benchClusterOpenDirect() {
        MainActivity a = sBenchHost;
        if (a == null) {
            Log.w("MainActivity", "[Bench] Ana ekran hazır değil; cluster aç atlandı");
            return;
        }
        a.handler.post(() -> {
            if (a.clusterDisplayManager == null) {
                a.log("[Bench] clusterDisplayManager yok");
                return;
            }
            a.clusterDisplayManager.openClusterDisplay();
        });
    }

    public static void benchClusterCloseDirect() {
        MainActivity a = sBenchHost;
        if (a == null) {
            Log.w("MainActivity", "[Bench] Ana ekran hazır değil; cluster kapat atlandı");
            return;
        }
        a.handler.post(() -> {
            if (a.clusterDisplayManager == null) {
                a.log("[Bench] clusterDisplayManager yok");
                return;
            }
            a.clusterDisplayManager.closeClusterDisplay(false);
        });
    }
}
