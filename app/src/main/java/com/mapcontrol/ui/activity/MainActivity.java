package com.mapcontrol.ui.activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.res.Configuration;
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
import com.mapcontrol.admin.MapControlDpmHelper;
import com.mapcontrol.R;
import com.mapcontrol.api.ProfileApiService;
import com.mapcontrol.manager.ClusterDisplayManager;
import com.mapcontrol.manager.MapControlVDBusKeyBridge;
import com.mapcontrol.util.AlertSoundHelper;
import com.mapcontrol.manager.WebServerManager;
import com.mapcontrol.service.GlobalBackService;
import com.mapcontrol.service.ServiceInitializer;
import com.mapcontrol.ui.builder.AppsTabBuilder;
import com.mapcontrol.ui.builder.AssistTabBuilder;
import com.mapcontrol.ui.builder.DriveModeTabBuilder;
import com.mapcontrol.ui.builder.FileUploadTabBuilder;
import com.mapcontrol.ui.builder.LauncherTabBuilder;
import com.mapcontrol.ui.builder.LogTabBuilder;
import com.mapcontrol.ui.builder.ProfileTabBuilder;
import com.mapcontrol.ui.builder.ProjectionTabBuilder;
import com.mapcontrol.ui.builder.SettingsTabBuilder;
import com.mapcontrol.ui.builder.SideRailBuilder;
import com.mapcontrol.ui.builder.TopBarBuilder;
import com.mapcontrol.ui.builder.VehicleInfoTabBuilder;
import com.mapcontrol.ui.builder.WelcomeSoundTabBuilder;
import com.mapcontrol.ui.builder.WifiTabBuilder;
import com.mapcontrol.util.IflyOemTtsHelper;
import com.mapcontrol.util.DialogHelper;
import com.mapcontrol.util.DisplayHelper;
import com.mapcontrol.util.ClusterNavigationState;
import com.mapcontrol.util.ImmersiveFullscreenHelper;
import com.mapcontrol.util.LauncherModeManager;
import com.mapcontrol.util.TargetPackageStore;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.vehicle.VehicleQuickControls;
import com.mapcontrol.ui.theme.UiStyles;

public class MainActivity extends AppCompatActivity {

    /** Yüzen yansıtma çubuğundan hedef uygulama seçiciyi açmak için {@link Intent} extra anahtarı. */
    public static final String EXTRA_OPEN_PROJECTION_TARGET_PICKER = "com.mapcontrol.extra.OPEN_PROJECTION_TARGET_PICKER";
    /** Araç Launcher Modu "Ana Ekran" karşılama sekmesinin {@code switchTab} indeksi. */
    private static final int TAB_LAUNCHER = 10;
    private TextView tvLogs;
    private ScrollView scrollView;
    private final StringBuilder logBuffer = new StringBuilder();
    private Handler handler;
    private volatile boolean isNavigationOpen = false; // Navigasyon durumu
    private String targetPackage = ""; // Seçilen uygulama paketi
    private ClusterDisplayManager clusterDisplayManager;
    private TextView targetAppLabel; // Seçilen uygulamayı gösteren TextView
    private FrameLayout tabContentArea; // Tab içerik alanı
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
    private WelcomeSoundTabBuilder welcomeSoundTabBuilder;
    private VehicleInfoTabBuilder vehicleInfoTabBuilder;
    private VehicleMetricsRepository vehicleMetricsRepository;
    private ScrollView vehicleInfoScrollView;
    private ScrollView launcherScrollView; // Araç Launcher Modu "Ana Ekran" karşılama sekmesi
    private LauncherTabBuilder launcherTabBuilder;
    private SettingsTabBuilder settingsTabBuilder;
    private ProfileTabBuilder profileTabBuilder;
    private DriveModeTabBuilder driveModeTabBuilder;
    private AssistTabBuilder assistTabBuilder;
    private LogTabBuilder logTabBuilder;
    /** Bu görev, Android'in Ana Ekran (HOME) isteğiyle mi başlatıldı? */
    private boolean launchedAsHome;
    private int currentTab = 0; // 0=Wi-Fi, 1=Web, 2=Profil, 3=Yansıtma, 4=LOG, 5=Uygulamalar, 6=Hafıza, 7=Ayarlar, 8=Açılış Sesi, 9=Araç Bilgi, 10=Ana Ekran (Launcher)
    private WebServerManager webServerManager; // HTTP Server Manager
    private Button btnWebServerToggle; // Web Server aç/kapat butonu
    private TextView webServerStatusText; // Web Server durum metni
    private android.widget.ImageView qrCodeImageView; // QR kod görseli
    private ServiceInitializer serviceInitializer;
    private SideRailBuilder sideRailBuilder;
    /** {@link ClusterVDBusTestActivity} bench — yalnızca görünürken; {@link #onResume()} atanır. */
    private static volatile MainActivity sBenchHost;
    /** Oturumda LOG sekmesine ilk geçişte hoşgeldin TTS bir kez */
    private boolean logWelcomeTtsDone;
    private LinearLayout topBarButtonsContainer; // Üst bar'daki buton container'ı (dinamik)
    private TextView topBarTitle; // Üst bar başlığı (dinamik)
    private ScrollView profileScrollView; // Profil tab ScrollView
    private ProfileApiService profileApiService; // API servisi
    private FrameLayout mainRootContainer;
    /** Gece/gündüz uiMode — Activity recreate etmeden soft tema yenilemek için. */
    private int lastNightModeUiBits = -1;
    private LinearLayout sideRail; // Sol kenar çubuğu kök view'ı (Launcher modunda gizlenebilir)
    private LinearLayout mainContent; // Ana içerik kök view'ı (Launcher modunda tam genişlik olur)
    private FrameLayout.LayoutParams mainContentParams;
    private int sidebarWidthPx;
    private int screenWidthPx;
    private TopBarBuilder topBarBuilder;
    private ProjectionTabBuilder projectionTabBuilder;
    /** Yüzen kontrolden veya tekilleştirilmiş intent ile hedef uygulama diyaloğu ertelenmiş. */
    private boolean deferredOpenProjectionTargetPicker;
    private boolean targetPackageBroadcastRegistered;
    private boolean navigationClusterBroadcastRegistered;
    private final BroadcastReceiver navigationClusterStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null
                    || !ClusterNavigationState.ACTION_NAVIGATION_CLUSTER_STATE.equals(intent.getAction())) {
                return;
            }
            boolean open = intent.getBooleanExtra(ClusterNavigationState.EXTRA_IS_OPEN, false);
            if (handler != null) {
                handler.post(() -> applyNavigationClusterOpenFromBus(open));
            } else {
                applyNavigationClusterOpenFromBus(open);
            }
        }
    };
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

    private View launchSplashRoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler(Looper.getMainLooper());
        launchedAsHome = LauncherModeManager.isHomeIntent(getIntent());
        if (savedInstanceState == null && getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_OPEN_PROJECTION_TARGET_PICKER, false)) {
            deferredOpenProjectionTargetPicker = true;
            getIntent().removeExtra(EXTRA_OPEN_PROJECTION_TARGET_PICKER);
        }

        // Cold start: önce splash boyansın — initializeApp uzun sürer, siyah frame olmasın
        launchSplashRoot = DisplayHelper.createAppLaunchSplashView(this);
        setContentView(launchSplashRoot);

        SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
        boolean disclaimerAccepted = prefs.getBoolean("disclaimerAccepted", false);

        if (disclaimerAccepted) {
            handler.post(this::initializeApp);
        } else {
            handler.post(this::showLegalDisclaimer);
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
        profileTabBuilder = new ProfileTabBuilder(this, prefs, profileApiService,
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
                        ClusterNavigationState.setLastKnownOpen(isOpen);
                        if (projectionTabBuilder != null) {
                            projectionTabBuilder.refreshProjectionStatusUi();
                        }
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
        isNavigationOpen = ClusterNavigationState.getLastKnownOpen();

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
        screenWidthPx = screenWidth;
        sidebarWidthPx = sidebarWidth;
        
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
        this.sideRail = sideRail;
        
        // Sol kenar çubuğunu ekle (%20 genişlik, tam yükseklik)
        FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(
                sidebarWidth,
                FrameLayout.LayoutParams.MATCH_PARENT);
        railParams.gravity = android.view.Gravity.START;
        mainRootContainer.addView(sideRail, railParams);

        // Ana içerik alanı (ekranın %80'i, header dahil)
        LinearLayout mainContent = new LinearLayout(this);
        mainContent.setOrientation(LinearLayout.VERTICAL);
        mainContent.setBackgroundColor(UiStyles.color(this, R.color.transparent));
        this.mainContent = mainContent;
        
        // Üst başlık bar (Builder)
        topBarBuilder = new TopBarBuilder(this,
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
        topBarBuilder.setLauncherBackButtonListener(v -> returnToLauncherHome());
        
        mainContent.addView(topBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Tab içerik alanı (FrameLayout: launcher park ederken SurfaceView GONE olmasın)
        tabContentArea = new FrameLayout(this);
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
        this.mainContentParams = mainContentParams;
        
        // WebServerManager'ı başlat
        webServerManager = new WebServerManager(this);
        webServerManager.setListener(new WebServerManager.WebServerListener() {
            @Override
            public void onServerStarted(int port, String localIp) {
                handler.post(() -> {
                    String serverUrl = "http://" + localIp + ":" + port;
                    if (webServerStatusText != null) {
                        webServerStatusText.setText(serverUrl);
                        webServerStatusText.setTextColor(UiStyles.color(MainActivity.this, R.color.accentHighlight));
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
                        webServerStatusText.setTextColor(UiStyles.color(MainActivity.this, R.color.textDialogButtonSecondary));
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
                        webServerStatusText.setTextColor(UiStyles.color(MainActivity.this, R.color.statusErrorBright));
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
                        MapControlVDBusKeyBridge.start();
                    }

                    @Override
                    public void onStopKeyEventListener() {
                        MapControlVDBusKeyBridge.stop();
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
        driveModeTabBuilder = new DriveModeTabBuilder(this, prefs,
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

        assistTabBuilder = new AssistTabBuilder(this, prefs,
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

        logTabBuilder = new LogTabBuilder(this, new LogTabBuilder.LogCallback() {
            @Override
            public void onClearLogs() {
                logBuffer.setLength(0);
                if (logTabBuilder != null && logTabBuilder.getLogsTextView() != null) {
                    logTabBuilder.getLogsTextView().setText("");
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
        logTabContent = logTabBuilder.getTabContent();
        tvLogs = logTabBuilder.getLogsTextView();
        scrollView = logTabBuilder.getScrollView();

        welcomeSoundTabBuilder = new WelcomeSoundTabBuilder(this, prefs);

        vehicleMetricsRepository = new VehicleMetricsRepository(getApplicationContext());

        vehicleInfoTabBuilder = new VehicleInfoTabBuilder(this, vehicleMetricsRepository,
                msg -> MainActivity.this.log(msg));
        vehicleInfoScrollView = vehicleInfoTabBuilder.getScrollView();

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
        settingsTabBuilder = new SettingsTabBuilder(this, prefs,
                new SettingsTabBuilder.SettingsCallback() {
                    @Override
                    public void log(String msg) {
                        MainActivity.this.log(msg);
                    }

                    @Override
                    public String getCarToken() {
                        return prefs.getString("carToken", null);
                    }

                    @Override
                    public void onLauncherModeChanged(boolean enabled) {
                        if (enabled) {
                            enterLauncherMode();
                        } else {
                            exitLauncherMode();
                        }
                    }
                });
        settingsScrollView = settingsTabBuilder.getScrollView();
        settingsTabContent = settingsTabBuilder.getSettingsTabContent();

        // Araç Launcher Modu "Ana Ekran" karşılama sekmesi (Builder) — mevcut switchTab akışını kullanır
        launcherTabBuilder = new LauncherTabBuilder(this, vehicleMetricsRepository,
                new LauncherTabBuilder.LauncherCallback() {
                    @Override
                    public void onShortcutSelected(int tabIndex, String title) {
                        switchTab(tabIndex);
                        if (topBarTitle != null) topBarTitle.setText(title);
                        if (sideRailBuilder != null) sideRailBuilder.setSelectionForTabIndex(tabIndex);
                    }

                    @Override
                    public void onExitLauncherRequested() {
                        exitLauncherMode();
                    }

                    @Override
                    public void onAppLaunchRequested(String packageName) {
                        if (appsTabBuilder != null) {
                            appsTabBuilder.launchApp(packageName);
                        }
                    }
                });
        launcherScrollView = launcherTabBuilder.build();

        presentMainUi(mainRootContainer);
        lastNightModeUiBits = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        UiStyles.setUiModeOverride(getResources().getConfiguration());

        if (LauncherModeManager.isHomeIntent(getIntent())) {
            LauncherModeManager.ensurePersistedFromHomeLaunch(this);
            launchedAsHome = true;
        }
        if (LauncherModeManager.isEnabled(this)) {
            applyLauncherChrome(true);
            switchTab(TAB_LAUNCHER);
        } else {
            applyLauncherChrome(false);
            switchTab(0);
        }
        
        // Uygulamaları yükle
        if (appsTabBuilder != null) appsTabBuilder.loadAppsFromServer();

        MapControlVDBusKeyBridge.acquire(this);

        // Otomatik seçim modu: Uygulama açıldığında önerilen uygulamayı otomatik seç
        autoSelectPreferredApp();
    }

    /** Splash → ana UI (splash initializeApp süresince zaten görünürdü). */
    private void presentMainUi(View mainRoot) {
        setTheme(R.style.Theme_MapControl);
        DisplayHelper.stopAppLaunchSplashAnimations();
        launchSplashRoot = null;
        setContentView(mainRoot);
    }

    /**
     * logcat'i dinleyerek SMS servisinden gelen key event'leri yakalar.
     * VDS-SMS-SourceFile tag'ini ve keyCode pattern'ini dinler.
     */
    private Thread keyEventLogcatThread;
    private volatile boolean keyEventLogcatRunning = false;
    
    private void playSoftAlert() {
        AlertSoundHelper.playSoftAlert(this, msg -> MainActivity.this.log(msg));
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
     * 0=Wi-Fi, 1=Web, 2=Profil, 3=Yansıtma, 4=LOG, 5=Uygulamalar, 6=Hafıza, 7=Ayarlar, 8=Açılış Sesi, 9=Araç Bilgi
     */
    private void switchTab(int tabIndex) {
        if (tabContentArea == null || projectionTabContent == null || wifiTabContent == null || logTabContent == null || appsTabContent == null || driveModeTabContent == null || fileUploadTabContent == null || welcomeSoundTabBuilder == null || vehicleInfoScrollView == null) {
            return;
        }

        welcomeSoundTabBuilder.onHostPause();

        if (currentTab == 9 && tabIndex != 9 && vehicleInfoTabBuilder != null) {
            vehicleInfoTabBuilder.stopListening();
        }
        if (currentTab == TAB_LAUNCHER && tabIndex != TAB_LAUNCHER && launcherTabBuilder != null) {
            launcherTabBuilder.onTabHidden();
        }

        currentTab = tabIndex;

        // TopBar buton container'ını temizle
        if (topBarButtonsContainer != null) {
            topBarButtonsContainer.removeAllViews();
        }

        // Launcher modu açıkken sol menü her zaman gizli; kapalıyken görünür.
        boolean launcherMode = LauncherModeManager.isEnabled(this);
        applyLauncherChrome(launcherMode);

        // Launcher modunda 3D modelin Engine'ini öldürmemek için Ana Ekran view'ını
        // hierarchy'de VISIBLE tut (üstüne opak overlay) — geri gelince kaldığı yerden devam.
        if (launcherMode && launcherScrollView != null) {
            showTabContentKeepingLauncherParked(tabIndex);
        } else {
            tabContentArea.removeAllViews();
            attachTabContent(tabIndex);
        }

        applyTabChrome(tabIndex);
    }

    /**
     * Launcher ScrollView'ı VISIBLE tutar; diğer sekmeyi üstte opak katmanda gösterir.
     * GONE kullanılmaz — SurfaceView deliği bozulunca dashboard arka planı değişmiş gibi görünür.
     */
    private void showTabContentKeepingLauncherParked(int tabIndex) {
        FrameLayout.LayoutParams matchParent = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);

        for (int i = tabContentArea.getChildCount() - 1; i >= 0; i--) {
            View child = tabContentArea.getChildAt(i);
            if (child != launcherScrollView) {
                tabContentArea.removeViewAt(i);
            }
        }

        if (launcherScrollView.getParent() != tabContentArea) {
            tabContentArea.addView(launcherScrollView, 0, matchParent);
        }
        launcherScrollView.setVisibility(View.VISIBLE);

        if (tabIndex == TAB_LAUNCHER) {
            launcherScrollView.bringToFront();
            if (launcherTabBuilder != null) {
                launcherTabBuilder.onTabVisible();
            }
            return;
        }

        // Opak host: altındaki canlı 3D / launcher gradient settings'ten sızmasın
        FrameLayout overlay = new FrameLayout(this);
        UiStyles.setRootBackground(overlay);
        attachTabContent(overlay, tabIndex);
        tabContentArea.addView(overlay, matchParent);
    }

    /** İçeriği verilen parent'a ekler; başlık / top-bar chrome {@link #applyTabChrome} ile. */
    private void attachTabContent(int tabIndex) {
        attachTabContent(tabContentArea, tabIndex);
    }

    private void attachTabContent(FrameLayout parent, int tabIndex) {
        FrameLayout.LayoutParams matchParent = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        if (tabIndex == 0) {
            addTabChild(parent, wifiTabContent, matchParent);
        } else if (tabIndex == 1) {
            addTabChild(parent, fileUploadScrollView, matchParent);
        } else if (tabIndex == 2) {
            addTabChild(parent, profileScrollView, matchParent);
        } else if (tabIndex == 3) {
            addTabChild(parent, projectionScrollView, matchParent);
        } else if (tabIndex == 4) {
            addTabChild(parent, logTabContent, matchParent);
        } else if (tabIndex == 5) {
            addTabChild(parent, appsTabContent, matchParent);
        } else if (tabIndex == 6) {
            addTabChild(parent, driveModeScrollView, matchParent);
        } else if (tabIndex == 7) {
            addTabChild(parent, settingsScrollView, matchParent);
        } else if (tabIndex == 8) {
            addTabChild(parent, welcomeSoundTabBuilder.getScrollView(), matchParent);
        } else if (tabIndex == 9) {
            addTabChild(parent, vehicleInfoScrollView, matchParent);
        } else if (tabIndex == TAB_LAUNCHER) {
            if (launcherScrollView != null) {
                addTabChild(parent, launcherScrollView, matchParent);
            }
            if (launcherTabBuilder != null) {
                launcherTabBuilder.onTabVisible();
            }
        }
    }

    private static void addTabChild(FrameLayout parent, View child, FrameLayout.LayoutParams lp) {
        if (child.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) child.getParent()).removeView(child);
        }
        parent.addView(child, lp);
    }

    private void applyTabChrome(int tabIndex) {
        if (tabIndex == 0) {
            if (topBarTitle != null) topBarTitle.setText("Wi-Fi Yönetimi");
            if (topBarButtonsContainer != null && wifiTabBuilder != null) {
                topBarButtonsContainer.addView(wifiTabBuilder.buildTopBarIcon());
            }
            if (wifiTabBuilder != null) wifiTabBuilder.updateWifiStatus();
        } else if (tabIndex == 4) {
            if (topBarTitle != null) {
                topBarTitle.setText("Sistem Kayıtları");
            }
            if (!logWelcomeTtsDone) {
                logWelcomeTtsDone = true;
                handler.postDelayed(
                        () -> speakTtsText(getString(R.string.log_tts_welcome_phrase)), 450);
            }
        } else if (tabIndex == 5) {
            if (topBarTitle != null) topBarTitle.setText("Uygulamalar");
            if (topBarButtonsContainer != null && appsTabBuilder != null) {
                topBarButtonsContainer.addView(appsTabBuilder.buildTopBarButtons(this));
            }
        } else if (tabIndex == 7) {
            if (settingsTabBuilder != null) {
                settingsTabBuilder.syncLauncherModeFromPrefs();
            }
        } else if (tabIndex == 8) {
            if (topBarTitle != null) {
                topBarTitle.setText(getString(R.string.welcome_sound_title));
            }
        } else if (tabIndex == 9) {
            if (topBarTitle != null) {
                topBarTitle.setText(getString(R.string.side_rail_vehicle_info));
            }
            if (vehicleInfoTabBuilder != null) {
                vehicleInfoTabBuilder.startListening();
            }
        } else if (tabIndex == TAB_LAUNCHER) {
            if (topBarTitle != null) {
                topBarTitle.setText("Araç Ana Ekranı");
            }
        }
    }

    /**
     * Launcher modu chrome'unu uygular: sol menü görünürlüğü, içerik genişliği ve TopBar geri düğmesi.
     */
    private void applyLauncherChrome(boolean active) {
        if (sideRailBuilder != null) {
            sideRailBuilder.setLauncherModeActive(active);
        }
        if (mainContent != null && mainContentParams != null) {
            mainContentParams.width = active ? screenWidthPx : (screenWidthPx - sidebarWidthPx);
            mainContent.setLayoutParams(mainContentParams);
        }
        if (topBarBuilder != null) {
            boolean onLauncherHome = active && currentTab == TAB_LAUNCHER;
            topBarBuilder.setTopBarVisible(!onLauncherHome);
            topBarBuilder.setLauncherBackButtonVisible(active && !onLauncherHome);
        }
        ImmersiveFullscreenHelper.setImmersiveFullscreen(this, active);
    }

    /** Launcher modunda alt sekmeden ana ekran ızgarasına döner; modu kapatmaz. */
    private void returnToLauncherHome() {
        if (!LauncherModeManager.isEnabled(this)) {
            return;
        }
        switchTab(TAB_LAUNCHER);
        if (topBarTitle != null) {
            topBarTitle.setText("Araç Ana Ekranı");
        }
    }

    private void enterLauncherMode() {
        LauncherModeManager.setEnabled(this, true);
        launchedAsHome = true;
        applyLauncherChrome(true);
        switchTab(TAB_LAUNCHER);
        syncLauncherModeSettingsUi();
    }

    /**
     * Launcher modunu kapatır. Yalnızca araç paneli sekmesindeyken Wi-Fi sekmesine döner;
     * Ayarlar gibi başka bir sekmeden kapatılırsa mevcut sekme korunur.
     */
    private void exitLauncherMode() {
        boolean wasOnLauncherTab = currentTab == TAB_LAUNCHER;
        LauncherModeManager.setEnabled(this, false);
        launchedAsHome = false;
        applyLauncherChrome(false);
        syncLauncherModeSettingsUi();
        // Park edilmiş Ana Ekran'ı hierarchy'den çıkar → 3D Engine serbest kalsın
        detachParkedLauncher();
        if (wasOnLauncherTab) {
            switchTab(0);
            if (topBarTitle != null) {
                topBarTitle.setText("Wi-Fi Yönetimi");
            }
            if (sideRailBuilder != null) {
                sideRailBuilder.setSelectionForTabIndex(0);
            }
        }
    }

    /** Launcher GONE park'tayken parent'tan koparır (ModelViewer detach destroy). */
    private void detachParkedLauncher() {
        if (launcherScrollView == null || tabContentArea == null) {
            return;
        }
        if (launcherScrollView.getParent() == tabContentArea) {
            tabContentArea.removeView(launcherScrollView);
        }
        launcherScrollView.setVisibility(View.VISIBLE);
    }

    private void syncLauncherModeSettingsUi() {
        if (settingsTabBuilder != null) {
            settingsTabBuilder.syncLauncherModeFromPrefs();
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
        // Cihaz Ana Ekran (Home) tuşuna basılıp uygulama zaten açıkken (singleTop) tekrar
        // çağrılırsa Araç Launcher karşılama ekranına dön.
        if (LauncherModeManager.isHomeIntent(intent)) {
            LauncherModeManager.ensurePersistedFromHomeLaunch(this);
            enterLauncherMode();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            ImmersiveFullscreenHelper.reapplyIfLauncherMode(this);
        }
    }

    @Override
    public void onBackPressed() {
        if (LauncherModeManager.isEnabled(this)) {
            // Ana Ekran (Home) olarak çalışırken geri tuşu uygulamayı kapatmamalı:
            // Launcher ekranındaysak hiçbir şey yapma, başka sekmedeysek Launcher'a dön.
            if (currentTab == TAB_LAUNCHER) {
                return;
            }
            switchTab(TAB_LAUNCHER);
            if (topBarTitle != null) {
                topBarTitle.setText("Araç Ana Ekranı");
            }
            return;
        }
        super.onBackPressed();
    }

    private void tryConsumeDeferredProjectionTargetPicker() {
        if (!deferredOpenProjectionTargetPicker || projectionTabBuilder == null) {
            return;
        }
        deferredOpenProjectionTargetPicker = false;
        handler.post(() -> projectionTabBuilder.openTargetAppPicker());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int night = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (night == lastNightModeUiBits) {
            return;
        }
        lastNightModeUiBits = night;
        // AppCompat Activity Resources yapışmasın diye çözümlemeyi newConfig'e kilitle.
        UiStyles.setUiModeOverride(newConfig);
        // uiMode configChanges ile Activity ayakta kalır → GLB Engine yok edilmez.
        reapplyUiModeTheme();
    }

    /**
     * Sistem light/dark değişince chrome + tüm sekmeleri yeniler.
     * Launcher / {@link com.mapcontrol.ui.widget.VehicleGlbView} yeniden kurulmaz.
     */
    private void reapplyUiModeTheme() {
        if (mainRootContainer != null) {
            UiStyles.setRootBackground(mainRootContainer);
        }
        if (tabContentArea != null) {
            UiStyles.setTabContentBackdrop(tabContentArea);
        }

        rebuildSideRailForUiMode();
        rebuildTopBarForUiMode();
        rebuildNonLauncherTabsForUiMode();

        if (launcherTabBuilder != null) {
            launcherTabBuilder.onUiModeChanged();
        }

        int tab = currentTab;
        boolean launcherMode = LauncherModeManager.isEnabled(this);
        applyLauncherChrome(launcherMode);
        if (sideRailBuilder != null && tab != TAB_LAUNCHER) {
            sideRailBuilder.setSelectionForTabIndex(tab);
        }
        if (tabContentArea == null) {
            return;
        }
        if (launcherMode && launcherScrollView != null) {
            showTabContentKeepingLauncherParked(tab);
        } else {
            tabContentArea.removeAllViews();
            attachTabContent(tab);
            applyTabChrome(tab);
        }
    }

    private void rebuildSideRailForUiMode() {
        if (sideRailBuilder == null || mainRootContainer == null) {
            return;
        }
        boolean launcherActive = LauncherModeManager.isEnabled(this);
        int selectTab = currentTab == TAB_LAUNCHER ? 0 : currentTab;
        if (sideRail != null) {
            mainRootContainer.removeView(sideRail);
        }
        sideRail = sideRailBuilder.build();
        FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(
                sidebarWidthPx > 0 ? sidebarWidthPx : FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        railParams.gravity = android.view.Gravity.START;
        mainRootContainer.addView(sideRail, 0, railParams);
        sideRailBuilder.setLauncherModeActive(launcherActive);
        sideRailBuilder.setSelectionForTabIndex(selectTab);
    }

    private void rebuildTopBarForUiMode() {
        if (topBarBuilder == null || mainContent == null) {
            return;
        }
        CharSequence title = topBarTitle != null ? topBarTitle.getText() : null;
        LinearLayout newTopBar = topBarBuilder.build();
        if (mainContent.getChildCount() > 0) {
            mainContent.removeViewAt(0);
        }
        mainContent.addView(newTopBar, 0, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        topBarTitle = topBarBuilder.getTitleView();
        topBarButtonsContainer = topBarBuilder.getButtonsContainer();
        topBarBuilder.setLauncherBackButtonListener(v -> returnToLauncherHome());
        if (title != null && topBarTitle != null) {
            topBarTitle.setText(title);
        }
    }

    /**
     * Launcher dışındaki sekmeleri taze renklerle yeniden kurar (GLB etkilenmez).
     */
    private void rebuildNonLauncherTabsForUiMode() {
        if (profileTabBuilder != null) {
            profileScrollView = profileTabBuilder.build();
        }
        if (wifiTabBuilder != null) {
            wifiTabContent = wifiTabBuilder.build();
        }
        if (fileUploadTabBuilder != null) {
            fileUploadScrollView = fileUploadTabBuilder.build();
            fileUploadTabContent = fileUploadTabBuilder.getFileUploadTabContent();
            btnWebServerToggle = fileUploadTabBuilder.getToggleButton();
            webServerStatusText = fileUploadTabBuilder.getStatusText();
            qrCodeImageView = fileUploadTabBuilder.getQrImageView();
            if (webServerManager != null && webServerManager.isRunning()) {
                if (btnWebServerToggle != null) {
                    btnWebServerToggle.setText("■ Web Server Durdur");
                }
                if (webServerStatusText != null) {
                    webServerStatusText.setTextColor(UiStyles.color(this, R.color.accentHighlight));
                }
            }
        }
        if (projectionTabBuilder != null) {
            projectionScrollView = projectionTabBuilder.build();
            projectionTabContent = projectionTabBuilder.getProjectionTabContent();
            targetAppLabel = projectionTabBuilder.getTargetAppLabel();
            updateTargetLabel();
            projectionTabBuilder.refreshProjectionStatusUi();
        }
        if (driveModeTabBuilder != null && assistTabBuilder != null) {
            driveModeScrollView = driveModeTabBuilder.build();
            driveModeTabContent = driveModeTabBuilder.getTabContent();
            driveModeTabContent.addView(assistTabBuilder.build(), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        if (logTabBuilder != null) {
            CharSequence logs = tvLogs != null ? tvLogs.getText() : logBuffer.toString();
            logTabContent = logTabBuilder.build();
            tvLogs = logTabBuilder.getLogsTextView();
            scrollView = logTabBuilder.getScrollView();
            if (tvLogs != null && logs != null) {
                tvLogs.setText(logs);
            }
        }
        if (appsTabBuilder != null) {
            appsTabContent = appsTabBuilder.build();
            appsTabBuilder.loadAppsFromServer();
        }
        if (settingsTabBuilder != null) {
            settingsScrollView = settingsTabBuilder.build();
            settingsTabContent = settingsTabBuilder.getSettingsTabContent();
            settingsTabBuilder.syncLauncherModeFromPrefs();
        }
        if (welcomeSoundTabBuilder != null) {
            welcomeSoundTabBuilder.rebuild();
        }
        if (vehicleInfoTabBuilder != null) {
            boolean listening = currentTab == 9;
            if (listening) {
                vehicleInfoTabBuilder.stopListening();
            }
            vehicleInfoTabBuilder.rebuild();
            vehicleInfoScrollView = vehicleInfoTabBuilder.getScrollView();
            if (listening) {
                vehicleInfoTabBuilder.startListening();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ImmersiveFullscreenHelper.reapplyIfLauncherMode(this);
        sBenchHost = this;
        // Arka plandayken Floating Back / overlay hedef seçimi prefs'e yazılır; yayın onPause'da
        // alıcı kapatıldığı için kaçırılabilir — her öne gelişte disk ile bellek senkronu.
        loadTargetPackage();
        updateTargetLabel();
        tryConsumeDeferredProjectionTargetPicker();
        registerTargetPackageBroadcastReceiver();
        registerNavigationClusterBroadcastReceiver();
        isNavigationOpen = ClusterNavigationState.getLastKnownOpen();
        if (projectionTabBuilder != null) {
            projectionTabBuilder.refreshProjectionStatusUi();
        }
    }

    @Override
    protected void onPause() {
        unregisterTargetPackageBroadcastReceiver();
        unregisterNavigationClusterBroadcastReceiver();
        if (welcomeSoundTabBuilder != null) {
            welcomeSoundTabBuilder.onHostPause();
        }
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

    private void registerNavigationClusterBroadcastReceiver() {
        if (navigationClusterBroadcastRegistered) {
            return;
        }
        try {
            IntentFilter filter = new IntentFilter(ClusterNavigationState.ACTION_NAVIGATION_CLUSTER_STATE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(navigationClusterStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(navigationClusterStateReceiver, filter);
            }
            navigationClusterBroadcastRegistered = true;
        } catch (Exception e) {
            log("Cluster durum yayını kaydı: " + e.getMessage());
        }
    }

    private void unregisterNavigationClusterBroadcastReceiver() {
        if (!navigationClusterBroadcastRegistered) {
            return;
        }
        try {
            unregisterReceiver(navigationClusterStateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        navigationClusterBroadcastRegistered = false;
    }

    private void applyNavigationClusterOpenFromBus(boolean open) {
        isNavigationOpen = open;
        ClusterNavigationState.setLastKnownOpen(open);
        if (projectionTabBuilder != null) {
            projectionTabBuilder.refreshProjectionStatusUi();
        }
    }

    @Override
    protected void onDestroy() {
        if (sBenchHost == this) {
            sBenchHost = null;
        }
        if (welcomeSoundTabBuilder != null) {
            welcomeSoundTabBuilder.releaseAudioFully();
        }
        if (vehicleInfoTabBuilder != null) {
            vehicleInfoTabBuilder.release();
        }
        if (vehicleMetricsRepository != null) {
            vehicleMetricsRepository.release();
        }
        VehicleQuickControls.getInstance(getApplicationContext()).release();
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
        MapControlVDBusKeyBridge.release(this);
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
