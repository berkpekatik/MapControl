package com.mapcontrol;

import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.net.wifi.WifiManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiConfiguration;
import android.text.InputType;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.PopupMenu;
import java.util.List;
import androidx.appcompat.app.AppCompatActivity;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.client.bind.VDServiceDef;
import com.desaysv.ivi.vdb.client.listener.VDBindListener;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;
import com.desaysv.ivi.vdb.event.id.carlan.VDEventCarLan;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayArea;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayCluster;
import com.desaysv.ivi.vdb.event.id.sms.VDEventSms;
import com.desaysv.ivi.vdb.IVDBusNotify;
import android.os.Build;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.pm.PackageInfo;
import android.content.SharedPreferences;
import androidx.core.content.FileProvider;
import android.app.ActivityManager;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoHelper;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID;
import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.bluetooth.BluetoothAdapter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.AudioAttributes;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private TextView tvLogs;
    private ScrollView scrollView;
    private final StringBuilder logBuffer = new StringBuilder();
    private Handler handler;
    private volatile boolean isNavigationOpen = false; // Navigasyon durumu
    private String targetPackage = ""; // Seçilen uygulama paketi
    private TextView targetAppLabel; // Seçilen uygulamayı gösteren TextView
    private LinearLayout tabContentArea; // Tab içerik alanı
    private LinearLayout settingsTabContent; // Ayarlar tab içeriği
    private ScrollView settingsScrollView; // Ayarlar tab ScrollView
    private LinearLayout projectionTabContent; // Yansıtma tab içeriği
    private ScrollView projectionScrollView; // Yansıtma tab ScrollView
    private LinearLayout wifiTabContent; // Wi-Fi tab içeriği
    private LinearLayout logTabContent; // LOG tab içeriği
    private LinearLayout appsTabContent; // Uygulamalar tab içeriği
    private LinearLayout driveModeTabContent; // Hafıza Modu tab içeriği
    private ScrollView driveModeScrollView; // Hafıza Modu tab ScrollView
    private LinearLayout fileUploadTabContent; // Dosya Yükle tab içeriği
    private ScrollView fileUploadScrollView; // Dosya Yükle tab ScrollView
    private int currentTab = 0; // 0 = Wi-Fi, 1 = Dosya Yükle, 2 = Profil, 3 = Yansıtma, 4 = LOG, 5 = Uygulamalar, 6 = Hafıza Modu, 7 = Ayarlar
    private WebServerManager webServerManager; // HTTP Server Manager
    private Button btnWebServerToggle; // Web Server aç/kapat butonu
    private TextView webServerStatusText; // Web Server durum metni
    private FloatingBackButtonManager floatingBackButtonManager; // Floating Back Button Manager
    private android.widget.ImageView qrCodeImageView; // QR kod görseli
    private ScrollView appsListScrollView; // Uygulamalar listesi için ScrollView
    private LinearLayout appsListContainer; // Uygulamalar listesi container
    private Button btnWifiToggle; // Wi-Fi aç/kapat butonu
    private WifiManager wifiManager; // Wi-Fi yöneticisi
    private ScrollView wifiListScrollView; // Wi-Fi listesi için ScrollView
    private LinearLayout wifiListContainer; // Wi-Fi listesi container
    private Button btnScanWifi; // Wi-Fi tarama butonu
    private TextView wifiStatusLine; // İnce durum satırı: "Bağlı: AndroidWifi • Güçlü sinyal" veya "Bağlı değil"
    private TextView wifiStatusIcon; // Sağ üstte Wi-Fi ikon + renkli nokta
    private android.content.BroadcastReceiver logReceiver; // MapControlService'den log mesajlarını almak için
    private LinearLayout topBarButtonsContainer; // Üst bar'daki buton container'ı (dinamik)
    private TextView topBarTitle; // Üst bar başlığı (dinamik)
    private Button btnRefreshApps; // Uygulama yenile butonu
    private Button btnDownloadedFiles; // İndirilen dosyalar butonu
    private Button btnReset; // Sıfırla butonu
    private Button btnModeToggle; // Yerel/Sunucu mod toggle butonu
    private boolean isLocalMode = false; // false = Sunucu, true = Yerel
    private LinearLayout menuApps; // Menü öğeleri (showAppManagementDisclaimer için)
    private LinearLayout menuWifi;
    private LinearLayout menuFileUpload; // Dosya Yükle menü öğesi
    private LinearLayout menuSettings;
    private LinearLayout menuProjection; // Yansıtma menü öğesi
    private LinearLayout menuDriveMode;
    private LinearLayout menuTest; // Test menü öğesi (Kamera Test)
    private LinearLayout menuProfile; // Profil menü öğesi
    private LinearLayout profileTabContent; // Profil tab içeriği
    private ScrollView profileScrollView; // Profil tab ScrollView
    private ProfileApiService profileApiService; // API servisi
    private LocationManager locationManager;
    private LocationListener locationListener;
    

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
        // Özel dialog tasarımı için koyu tema
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        // Ana container (koyu arka plan)
        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setBackgroundColor(0xFF0A0F14); // Koyu arka plan
        mainContainer.setPadding(0, 0, 0, 0);
        
        // Başlık container (resmi ama sıcak kanlı)
        LinearLayout titleContainer = new LinearLayout(this);
        titleContainer.setOrientation(LinearLayout.VERTICAL);
        titleContainer.setPadding(32, 32, 32, 24);
        titleContainer.setBackgroundColor(0xFF151C24); // Biraz daha açık kart rengi
        
        // Ana başlık
        TextView titleView = new TextView(this);
        titleView.setText("Hoş Geldiniz");
        titleView.setTextSize(24);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setGravity(android.view.Gravity.START);
        titleContainer.addView(titleView);
        
        // Alt başlık (sıcak kanlı ama resmi)
        TextView subtitleView = new TextView(this);
        subtitleView.setText("Yasal Uyarı ve Kullanım Koşulları");
        subtitleView.setTextSize(16);
        subtitleView.setTextColor(0xFF3DAEA8); // Accent rengi (sıcak ama profesyonel)
        subtitleView.setTypeface(null, android.graphics.Typeface.NORMAL);
        subtitleView.setGravity(android.view.Gravity.START);
        subtitleView.setPadding(0, 8, 0, 0);
        titleContainer.addView(subtitleView);
        
        mainContainer.addView(titleContainer);
        
        // İçerik alanı
        LinearLayout contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(32, 24, 32, 24);
        contentContainer.setBackgroundColor(0xFF0A0F14);
        
        // Uyarı metni (daha güzelleştirilmiş)
        String disclaimerText = "### Yasal Uyarı ve Sorumluluk Reddi\n" +
                        "1. **Ücretsiz Dağıtım:** Bu yazılım, herhangi bir ücret talep edilmeksizin tamamen ücretsiz olarak dağıtılmaktadır. Yazılım içinde belirtilen içerikler ayrı bir ücret karşılığında satılmaz.\n" +
                        "2. **Kullanıcı Onayı ve Risk Kabulü:** Kullanıcı, cihazın bellek (hafıza) ayarlarını veya araç konfigürasyonlarını kendi rızasıyla ve bilinciyle değiştirdiğini onaylar.\n" +
                        "3. **Sorumluluk Reddi:** Geliştirici, bu değişiklikler veya uygulamanın kullanımı sonucunda ortaya çıkabilecek hiçbir doğrudan veya dolaylı zarardan, veri kaybından veya arızadan **sorumlu değildir ve hiçbir yükümlülük kabul etmez.**\n" +
                        "**Onay:** Lütfen uygulamayı kullanmaya başlamadan önce yukarıdaki tüm bilgileri **okuduğunuzu, anladığınızu ve kabul ettiğinizi** onaylayın.";
                        
        TextView messageView = new TextView(this);
        messageView.setText(parseMarkdown(disclaimerText));
        messageView.setTextSize(15);
        messageView.setTextColor(0xFFE6E6E6); // Açık gri (beyazdan biraz daha yumuşak)
        messageView.setPadding(0, 0, 0, 0);
        messageView.setLineSpacing(12, 1.4f); // Daha rahat satır aralığı
        messageView.setGravity(android.view.Gravity.START);
        
        ScrollView scrollView = new ScrollView(this);
        scrollView.setPadding(0, 0, 0, 0);
        scrollView.setBackgroundColor(0xFF0A0F14);
        scrollView.addView(messageView);
        
        contentContainer.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f));
        
        mainContainer.addView(contentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f));
        
        builder.setView(mainContainer);
        
        builder.setPositiveButton("Kabul Ediyorum", (dialog, which) -> {
            // Kullanıcı kabul etti, kaydet ve uygulamayı başlat
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("disclaimerAccepted", true);
            editor.apply();
            initializeApp();
        });
        
        builder.setNegativeButton("Kabul Etmiyorum", (dialog, which) -> {
            // Kullanıcı kabul etmedi, uygulamayı kapat
            finish();
        });
        
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        // Dialog penceresini şeffaf yap ki koyu arka plan görünsün
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
        }
        
        // Dialog gösterildikten sonra butonları özelleştir
        dialog.setOnShowListener(dialogInterface -> {
            // Pozitif buton (Kabul Et)
            android.widget.Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setTextColor(0xFFFFFFFF); // Koyu accent üzerinde beyaz
                positiveButton.setTextSize(16);
                positiveButton.setTypeface(null, android.graphics.Typeface.BOLD);
                positiveButton.setPadding(32, 16, 32, 16);
                
                // Rounded corners için GradientDrawable
                android.graphics.drawable.GradientDrawable positiveBg = new android.graphics.drawable.GradientDrawable();
                positiveBg.setColor(0xFF3DAEA8); // Accent rengi
                positiveBg.setCornerRadius(12);
                positiveButton.setBackground(positiveBg);
            }
            
            // Negatif buton (Hayır)
            android.widget.Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                negativeButton.setTextColor(0xFFCCCCCC); // Koyu arka planda görünür gri
                negativeButton.setBackgroundColor(0x00000000);
                negativeButton.setPadding(32, 16, 32, 16);
                negativeButton.setTextSize(16);
                negativeButton.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        });
        
        dialog.show();
    }

    /**
     * Uygulama Yönetimi için yasal uyarı ve onay ekranını gösterir
     */
    private void showAppManagementDisclaimer() {
        // Özel dialog tasarımı için koyu tema
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        // Ana container (koyu arka plan)
        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setBackgroundColor(0xFF0A0F14); // Koyu arka plan
        mainContainer.setPadding(0, 0, 0, 0);
        
        // Başlık container (resmi ama sıcak kanlı)
        LinearLayout titleContainer = new LinearLayout(this);
        titleContainer.setOrientation(LinearLayout.VERTICAL);
        titleContainer.setPadding(32, 32, 32, 24);
        titleContainer.setBackgroundColor(0xFF151C24); // Biraz daha açık kart rengi
        
        // Ana başlık
        TextView titleView = new TextView(this);
        titleView.setText("Uygulama Yönetimi");
        titleView.setTextSize(24);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setGravity(android.view.Gravity.START);
        titleContainer.addView(titleView);
        
        // Alt başlık (sıcak kanlı ama resmi)
        TextView subtitleView = new TextView(this);
        subtitleView.setText("Yasal Uyarı ve Sorumluluk Reddi");
        subtitleView.setTextSize(16);
        subtitleView.setTextColor(0xFF3DAEA8); // Accent rengi (sıcak ama profesyonel)
        subtitleView.setTypeface(null, android.graphics.Typeface.NORMAL);
        subtitleView.setGravity(android.view.Gravity.START);
        subtitleView.setPadding(0, 8, 0, 0);
        titleContainer.addView(subtitleView);
        
        mainContainer.addView(titleContainer);
        
        // İçerik alanı
        LinearLayout contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(32, 24, 32, 24);
        contentContainer.setBackgroundColor(0xFF0A0F14);
        
        // Uyarı metni
        String disclaimerText = "Uygulama yükleme ve kaldırma işlemleri tamamen kullanıcının sorumluluğundadır. Geliştirici, kullanıcının yüklediği veya kaldırdığı uygulamalardan kaynaklanan hiçbir sorumluluğu kabul etmez.";
        
        TextView messageView = new TextView(this);
        messageView.setText(disclaimerText);
        messageView.setTextSize(15);
        messageView.setTextColor(0xFFE6E6E6); // Açık gri (beyazdan biraz daha yumuşak)
        messageView.setPadding(0, 0, 0, 0);
        messageView.setLineSpacing(12, 1.4f); // Daha rahat satır aralığı
        messageView.setGravity(android.view.Gravity.START);
        
        ScrollView scrollView = new ScrollView(this);
        scrollView.setPadding(0, 0, 0, 0);
        scrollView.setBackgroundColor(0xFF0A0F14);
        scrollView.addView(messageView);
        
        contentContainer.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f));
        
        mainContainer.addView(contentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f));
        
        builder.setView(mainContainer);
        
        builder.setPositiveButton("Kabul Ediyorum", (dialog, which) -> {
            // Kullanıcı kabul etti, kaydet ve sekme değiştir
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("appManagementDisclaimerAccepted", true);
            editor.apply();
            
            switchTab(3);
            if (topBarTitle != null) {
                topBarTitle.setText("Uygulama Yönetimi");
            }
            if (menuApps != null && menuWifi != null && menuSettings != null && menuDriveMode != null && menuTest != null) {
                updateMenuSelection(menuApps, menuWifi, menuSettings, menuDriveMode, menuTest);
            }
        });
        
        builder.setNegativeButton("Geri", (dialog, which) -> {
            // Kullanıcı geri döndü, dialog'u kapat
            dialog.dismiss();
        });
        
        builder.setCancelable(true);
        AlertDialog dialog = builder.create();
        // Dialog penceresini şeffaf yap ki koyu arka plan görünsün
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
        }
        
        // Dialog gösterildikten sonra butonları özelleştir
        dialog.setOnShowListener(dialogInterface -> {
            // Pozitif buton (Kabul Et)
            android.widget.Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setTextColor(0xFFFFFFFF); // Koyu accent üzerinde beyaz
                positiveButton.setTextSize(16);
                positiveButton.setTypeface(null, android.graphics.Typeface.BOLD);
                positiveButton.setPadding(32, 16, 32, 16);
                
                // Rounded corners için GradientDrawable
                android.graphics.drawable.GradientDrawable positiveBg = new android.graphics.drawable.GradientDrawable();
                positiveBg.setColor(0xFF3DAEA8); // Accent rengi
                positiveBg.setCornerRadius(12);
                positiveButton.setBackground(positiveBg);
            }
            
            // Negatif buton (Geri)
            android.widget.Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                negativeButton.setTextColor(0xFFCCCCCC); // Koyu arka planda görünür gri
                negativeButton.setBackgroundColor(0x00000000);
                negativeButton.setPadding(32, 16, 32, 16);
                negativeButton.setTextSize(16);
                negativeButton.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        });
        
        dialog.show();
    }

    /**
     * Uygulamayı başlatır (onCreate'in geri kalanı)
     */
    private void initializeApp() {
        // SharedPreferences (tüm bölümler için ortak)
        SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
        
        // ProfileApiService'i başlat
        profileApiService = new ProfileApiService(this);
        
        // SharedPreferences'tan targetPackage'ı yükle
        loadTargetPackage();
        
        // Foreground Service'i başlat (arka planda çalışması için)
        startForegroundService();
        
        // MapControlService'den gelen log mesajlarını dinle
        registerLogReceiver();
        

        // FrameLayout (Ana container - overlay için)
        FrameLayout rootContainer = new FrameLayout(this);
        rootContainer.setBackgroundColor(0xFF121212);

        // Ekran genişliğini al (%20/%80 bölme için)
        android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int sidebarWidth = (int)(screenWidth * 0.20f); // %20
        int mainContentWidth = (int)(screenWidth * 0.80f); // %80
        
        // Sol sabit kenar çubuğu (ekranın %20'si, tam yükseklik)
        LinearLayout sideRail = new LinearLayout(this);
        sideRail.setOrientation(LinearLayout.VERTICAL);
        sideRail.setBackgroundColor(0xFF1C2630);
        sideRail.setPadding(0, 0, 0, 0);
        
        // Sol panel üst bar (header ile aynı stil ve yükseklik)
        LinearLayout sideRailTopBar = new LinearLayout(this);
        sideRailTopBar.setOrientation(LinearLayout.HORIZONTAL);
        sideRailTopBar.setBackgroundColor(0xFF1C2630); // Header ile aynı renk
        sideRailTopBar.setPadding(24, 16, 16, 16); // Header ile aynı padding
        sideRailTopBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        sideRailTopBar.setMinimumHeight((int)(48 * getResources().getDisplayMetrics().density)); // Header ile aynı yükseklik
        
        // MapControl by vNoisy (sürüm numarası) - finalTopBarTitle ile aynı boyut ve fontta
        TextView appTitleText = new TextView(this);
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            
            // SpannableString ile farklı stiller uygula
            android.text.SpannableString spannableText = new android.text.SpannableString("MapControl by vNoisy (" + versionName + ")");
            
            // "vNoisy" kısmını italik yap
            int vNoisyStart = spannableText.toString().indexOf("vNoisy");
            int vNoisyEnd = vNoisyStart + "vNoisy".length();
            if (vNoisyStart >= 0) {
                spannableText.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), 
                    vNoisyStart, vNoisyEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            
            // Versiyon numarasını küçük yap (parantezler dahil)
            int versionStart = spannableText.toString().indexOf("(");
            int versionEnd = spannableText.length();
            if (versionStart >= 0) {
                spannableText.setSpan(new android.text.style.RelativeSizeSpan(0.75f), 
                    versionStart, versionEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            
            appTitleText.setText(spannableText);
        } catch (PackageManager.NameNotFoundException e) {
            // Hata durumunda sadece "vNoisy"yi italik yap
            android.text.SpannableString spannableText = new android.text.SpannableString("MapControl by vNoisy");
            int vNoisyStart = spannableText.toString().indexOf("vNoisy");
            int vNoisyEnd = vNoisyStart + "vNoisy".length();
            if (vNoisyStart >= 0) {
                spannableText.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), 
                    vNoisyStart, vNoisyEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            appTitleText.setText(spannableText);
        }
        appTitleText.setTextSize(20); // finalTopBarTitle ile aynı
        appTitleText.setTextColor(0xFFFFFFFF); // finalTopBarTitle ile aynı
        appTitleText.setTypeface(null, android.graphics.Typeface.BOLD); // finalTopBarTitle ile aynı
        sideRailTopBar.addView(appTitleText);
        
        sideRail.addView(sideRailTopBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // Menü öğeleri için ScrollView oluştur
        ScrollView menuScrollView = new ScrollView(this);
        menuScrollView.setBackgroundColor(0xFF1C2630);
        menuScrollView.setFillViewport(false);
        
        // Menü öğeleri container
        LinearLayout menuContainer = new LinearLayout(this);
        menuContainer.setOrientation(LinearLayout.VERTICAL);
        menuContainer.setBackgroundColor(0xFF1C2630);
        
        // Menü öğeleri oluştur (daha büyük ve ergonomik, minimum 80x80px dokunma alanı)
        menuWifi = createRailMenuItemView("📶", "Wi-Fi Yönetimi", true);
        menuApps = createRailMenuItemView("📱", "Uygulama Yönetimi", false);
        menuFileUpload = createRailMenuItemView("📤", "Dosya Yükle", false);
        menuProfile = createRailMenuItemView("👤", "Profil", false);
        menuDriveMode = createRailMenuItemView("🚗", "Hafıza Modu", false);
        menuTest = createRailMenuItemView("📷", "Kamera Test", false);
        menuProjection = createRailMenuItemView("🗺️", "Yansıtma", false);
        menuSettings = createRailMenuItemView("⚙️", "Ayarlar", false);
        
        menuContainer.addView(menuWifi);
        menuContainer.addView(menuApps);
        menuContainer.addView(menuFileUpload);
        menuContainer.addView(menuProfile);
        menuContainer.addView(menuDriveMode);
        // menuTest gizli - Log ekranından erişilebilir
        menuTest.setVisibility(android.view.View.GONE);
        menuContainer.addView(menuTest);
        menuContainer.addView(menuProjection);
        menuContainer.addView(menuSettings);
        
        menuScrollView.addView(menuContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // ScrollView'ı sideRail'e ekle (kalan alanı kaplasın)
        LinearLayout.LayoutParams menuScrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f); // Weight 1.0 - kalan alanı kaplasın
        sideRail.addView(menuScrollView, menuScrollParams);
        
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
        
        // Üst başlık bar (sadece sağ %80'lik alanda)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(0xFF1C2630);
        topBar.setPadding(24, 16, 16, 16);
        topBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        // Başlık TextView
        TextView topBarTitle = new TextView(this);
        topBarTitle.setText("Wi-Fi Yönetimi");
        topBarTitle.setTextSize(20);
        topBarTitle.setTextColor(0xFFFFFFFF);
        topBarTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        topBarTitle.setClickable(true);
        topBarTitle.setFocusable(true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        topBar.addView(topBarTitle, titleParams);
        
        // Sağ taraf buton container'ı (dinamik - sayfaya göre değişecek)
        topBarButtonsContainer = new LinearLayout(this);
        topBarButtonsContainer.setOrientation(LinearLayout.HORIZONTAL);
        topBarButtonsContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams buttonsContainerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        topBar.addView(topBarButtonsContainer, buttonsContainerParams);
        
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
        
        // Drawer menü öğelerine tıklandığında
        final TextView finalTopBarTitle = topBarTitle;
        
        // finalTopBarTitle'a 3 kere tıklama ile Sistem Kayıtları menüsünü açma/kapatma
        final int[] titleClickCount = {0};
        final Handler titleClickHandler = new Handler(Looper.getMainLooper());
        final Runnable titleClickReset = () -> titleClickCount[0] = 0;
        final boolean[] isLogTabVisible = {false}; // Sistem Kayıtları görünürlük durumu
        
        finalTopBarTitle.setOnClickListener(v -> {
            titleClickCount[0]++;
            titleClickHandler.removeCallbacks(titleClickReset);
            
            if (titleClickCount[0] >= 3) {
                // Sistem Kayıtları menüsü açıksa kapat, kapalıysa aç
                if (isLogTabVisible[0]) {
                    // Gizle - önceki tab'a geri dön
                    isLogTabVisible[0] = false;
                    if (logTabContent != null) {
                        logTabContent.setVisibility(android.view.View.GONE);
                    }
                    // LOG tab'ındaysak Wi-Fi tab'ına dön
                    if (currentTab == 4) {
                        switchTab(0);
                    }
            } else {
                    // Göster - LOG tab'ına geç
                    isLogTabVisible[0] = true;
                    if (logTabContent != null) {
                        logTabContent.setVisibility(android.view.View.VISIBLE);
                    }
                    switchTab(4);
                }
                titleClickCount[0] = 0;
            } else {
                // 1 saniye içinde tekrar tıklanmazsa sıfırla
                titleClickHandler.postDelayed(titleClickReset, 1000);
            }
        });
        
        menuWifi.setOnClickListener(v -> {
            switchTab(0);
            finalTopBarTitle.setText("Wi-Fi Yönetimi");
            updateMenuSelection(menuWifi, menuFileUpload, menuProfile, menuProjection, menuSettings, menuApps, menuDriveMode, menuTest);
        });
        
        menuFileUpload.setOnClickListener(v -> {
            switchTab(1);
            finalTopBarTitle.setText("Dosya Yükle");
            updateMenuSelection(menuFileUpload, menuWifi, menuProfile, menuProjection, menuSettings, menuApps, menuDriveMode, menuTest);
        });
        
        menuProfile.setOnClickListener(v -> {
            switchTab(2);
            finalTopBarTitle.setText("Profil");
            updateMenuSelection(menuProfile, menuWifi, menuFileUpload, menuProjection, menuSettings, menuApps, menuDriveMode, menuTest);
        });
        
        menuProjection.setOnClickListener(v -> {
            switchTab(3);
            finalTopBarTitle.setText("Yansıtma");
            updateMenuSelection(menuProjection, menuWifi, menuFileUpload, menuProfile, menuSettings, menuApps, menuDriveMode, menuTest);
        });
        
        menuSettings.setOnClickListener(v -> {
            switchTab(7);
            finalTopBarTitle.setText("Ayarlar");
            updateMenuSelection(menuSettings, menuWifi, menuFileUpload, menuProfile, menuProjection, menuApps, menuDriveMode, menuTest);
        });
        
        
        menuApps.setOnClickListener(v -> {
            // Uygulama Yönetimi uyarısını kontrol et
            SharedPreferences appPrefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            boolean appManagementDisclaimerAccepted = appPrefs.getBoolean("appManagementDisclaimerAccepted", false);
            
            if (appManagementDisclaimerAccepted) {
                // Daha önce kabul edilmiş, direkt sekme değiştir
                switchTab(5);
                if (topBarTitle != null) {
                    topBarTitle.setText("Uygulama Yönetimi");
                }
                updateMenuSelection(menuApps, menuWifi, menuFileUpload, menuProfile, menuProjection, menuSettings, menuDriveMode, menuTest);
            } else {
                // İlk kez açılıyor, uyarıyı göster
                showAppManagementDisclaimer();
            }
        });
        
        menuDriveMode.setOnClickListener(v -> {
            switchTab(6);
            finalTopBarTitle.setText("Hafıza Modu");
                updateMenuSelection(menuDriveMode, menuWifi, menuFileUpload, menuProfile, menuProjection, menuSettings, menuApps, menuTest);
        });
        
        // menuTest gizli olduğu için onClick listener'a gerek yok
        // Kamera Test butonu Log ekranından erişilebilir

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
                    generateQRCode(serverUrl);
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
                    installApkFile(fileName);
                });
            }

            @Override
            public void onDeleteApp(String packageName) {
                handler.post(() -> {
                    deleteApp(packageName);
                });
            }

            @Override
            public void onLaunchApp(String packageName) {
                handler.post(() -> {
                    launchApp(packageName);
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
        fileUploadScrollView = new ScrollView(this);
        fileUploadScrollView.setBackgroundColor(0xFF0A0F14);
        fileUploadScrollView.setPadding(0, 0, 0, 0);
        fileUploadScrollView.setFillViewport(true);

        fileUploadTabContent = new LinearLayout(this);
        fileUploadTabContent.setOrientation(LinearLayout.VERTICAL);
        fileUploadTabContent.setPadding(0, 0, 0, 0);
        fileUploadTabContent.setBackgroundColor(0xFF0A0F14);

        // Başlık
        TextView fileUploadTitle = new TextView(this);
        fileUploadTitle.setText("Dosya Yükle");
        fileUploadTitle.setTextSize(18);
        fileUploadTitle.setTextColor(0xFFFFFFFF);
        fileUploadTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        fileUploadTitle.setPadding(16, 16, 16, 8);
        fileUploadTabContent.addView(fileUploadTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Açıklama
        TextView fileUploadDesc = new TextView(this);
        fileUploadDesc.setText("Web Server'ı başlatarak aynı ağdaki cihazlardan dosya yükleyebilirsiniz.");
        fileUploadDesc.setTextSize(13);
        fileUploadDesc.setTextColor(0xAAFFFFFF);
        fileUploadDesc.setPadding(16, 0, 16, 16);
        fileUploadTabContent.addView(fileUploadDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Toggle butonu
        btnWebServerToggle = new Button(this);
        btnWebServerToggle.setText("▶ Web Server Başlat");
        btnWebServerToggle.setTextColor(0xFFFFFFFF);
        btnWebServerToggle.setTextSize(16);
        btnWebServerToggle.setTypeface(null, android.graphics.Typeface.BOLD);
        btnWebServerToggle.setBackgroundColor(0xFF3DAEA8);
        btnWebServerToggle.setPadding(16, 20, 16, 20);
        LinearLayout.LayoutParams webServerToggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        webServerToggleParams.setMargins(16, 0, 16, 16);
        fileUploadTabContent.addView(btnWebServerToggle, webServerToggleParams);

        // URL ve QR kod container
        LinearLayout urlQrContainer = new LinearLayout(this);
        urlQrContainer.setOrientation(LinearLayout.VERTICAL);
        urlQrContainer.setGravity(android.view.Gravity.CENTER);
        urlQrContainer.setPadding(16, 16, 16, 16);
        
        // Durum metni (büyük ve ortalanmış)
        webServerStatusText = new TextView(this);
        webServerStatusText.setText("Sunucu durduruldu");
        webServerStatusText.setTextSize(20);
        webServerStatusText.setTextColor(0xAAFFFFFF);
        webServerStatusText.setGravity(android.view.Gravity.CENTER);
        webServerStatusText.setPadding(16, 16, 16, 16);
        webServerStatusText.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        urlQrContainer.addView(webServerStatusText, urlParams);
        
        // QR kod ImageView
        qrCodeImageView = new android.widget.ImageView(this);
        qrCodeImageView.setVisibility(android.view.View.GONE);
        qrCodeImageView.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        qrCodeImageView.setPadding(16, 16, 16, 16);
        int qrSize = (int)(200 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(
                qrSize,
                qrSize);
        qrParams.gravity = android.view.Gravity.CENTER;
        urlQrContainer.addView(qrCodeImageView, qrParams);
        
        fileUploadTabContent.addView(urlQrContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Toggle butonu click listener
        btnWebServerToggle.setOnClickListener(v -> {
            if (webServerManager.isRunning()) {
                webServerManager.stopServer();
            } else {
                webServerManager.startServer();
            }
        });

        fileUploadScrollView.addView(fileUploadTabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Yansıtma tab içeriği (OEM Seviyesi Modern Tasarım)
        projectionScrollView = new ScrollView(this);
        projectionScrollView.setBackgroundColor(0xFF0A0F14); // Koyu, tek ton (antrasit)
        projectionScrollView.setPadding(0, 0, 0, 0);
        projectionScrollView.setFillViewport(true);
        
        projectionTabContent = new LinearLayout(this);
        projectionTabContent.setOrientation(LinearLayout.VERTICAL);
        projectionTabContent.setPadding(0, 0, 0, 0);
        projectionTabContent.setBackgroundColor(0xFF0A0F14); // Koyu, tek ton (antrasit)
        projectionScrollView.addView(projectionTabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // === YANSITMA KONTROLÜ BÖLÜMÜ ===
        TextView projectionTitle = new TextView(this);
        projectionTitle.setText("Yansıtma Kontrolü");
        projectionTitle.setTextSize(18);
        projectionTitle.setTextColor(0xFFFFFFFF);
        projectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        projectionTitle.setPadding(16, 16, 16, 8);
        projectionTabContent.addView(projectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Durum metni (küçük, düşük kontrast)
        TextView projectionStatus = new TextView(this);
        projectionStatus.setText("Yansıtma kapalı");
        projectionStatus.setTextSize(13);
        projectionStatus.setTextColor(0xAAFFFFFF); // %67 opaklık
        projectionStatus.setPadding(16, 0, 16, 16);
        projectionStatus.setId(android.view.View.generateViewId()); // ID ekle (dinamik güncelleme için)
        projectionTabContent.addView(projectionStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Yansıtma kontrol butonları (yan yana)
        LinearLayout controlButtonContainer = new LinearLayout(this);
        controlButtonContainer.setOrientation(LinearLayout.HORIZONTAL);
        controlButtonContainer.setPadding(16, 0, 16, 16);
        
        // Yansıt butonu
        Button btnOpen = new Button(this);
        btnOpen.setText("Yansıt");
        btnOpen.setTextColor(0xFFFFFFFF);
        btnOpen.setTextSize(16);
        btnOpen.setTypeface(null, android.graphics.Typeface.BOLD);
        btnOpen.setBackgroundColor(0xFF3DAEA8); // Accent rengi (teal/mavi) - önceki stil gibi
        btnOpen.setPadding(16, 20, 16, 20);
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); // Weight 1 ile yan yana eşit genişlik
        openParams.setMargins(0, 0, 8, 0); // Sağdan margin
        btnOpen.setId(android.view.View.generateViewId());
        controlButtonContainer.addView(btnOpen, openParams);

        // Durdur butonu (her zaman görünür)
        Button btnClose = new Button(this);
        btnClose.setText("Durdur");
        btnClose.setTextColor(0xFFFFFFFF);
        btnClose.setTextSize(16);
        btnClose.setTypeface(null, android.graphics.Typeface.BOLD);
        btnClose.setBackgroundColor(0xFFF44336); // Kırmızı renk - önceki stil gibi
        btnClose.setPadding(16, 20, 16, 20);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); // Weight 1 ile yan yana eşit genişlik
        btnClose.setId(android.view.View.generateViewId());
        controlButtonContainer.addView(btnClose, closeParams);
        
        projectionTabContent.addView(controlButtonContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

setContentView(rootContainer);

        // Buton click listener'ları
        btnOpen.setOnClickListener(v -> {
            // İşlemi hemen başlat
            openClusterDisplay();
            updateProjectionUI(projectionStatus, btnOpen, btnClose);
            
            // Butonu animasyonla disabled yap (2-3 saniye)
            handleButtonClickWithDelay(btnOpen, "Yansıt", "Yansıtılıyor...");
        });

        btnClose.setOnClickListener(v -> {
            // İşlemi hemen başlat
            closeClusterDisplay(true);
            updateProjectionUI(projectionStatus, btnOpen, btnClose);
            
            // Butonu animasyonla disabled yap (2-3 saniye)
            handleButtonClickWithDelay(btnClose, "Durdur", "Durduruluyor...");
        });
        
        // İlk yüklemede durumu güncelle
        handler.post(() -> {
            updateProjectionUI(projectionStatus, btnOpen, btnClose);
        });

        // === UYGULAMA BÖLÜMÜ ===
        TextView appTitle = new TextView(this);
        appTitle.setText("Uygulama");
        appTitle.setTextSize(18);
        appTitle.setTextColor(0xFFFFFFFF);
        appTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        appTitle.setPadding(16, 16, 16, 8);
        projectionTabContent.addView(appTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Uygulama Kartı (tool-like, düz tasarım)
        LinearLayout appCard = new LinearLayout(this);
        appCard.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable appCardBg = new android.graphics.drawable.GradientDrawable();
        appCardBg.setColor(0xFF151C24); // Kart rengi (ayarlardan tutarlı)
        appCardBg.setCornerRadius(12);
        appCard.setBackground(appCardBg);
        appCard.setPadding(20, 20, 20, 20);
        LinearLayout.LayoutParams appCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        appCardParams.setMargins(16, 0, 16, 16);
        
        // Üst kısım: Icon + Bilgi
        LinearLayout appInfo = new LinearLayout(this);
        appInfo.setOrientation(LinearLayout.HORIZONTAL);
        appInfo.setPadding(0, 0, 0, 20);
        
        // Icon container (daha küçük, daha sade)
        LinearLayout iconBox = new LinearLayout(this);
        iconBox.setOrientation(LinearLayout.VERTICAL);
        iconBox.setBackgroundColor(0xFF1A2330);
        iconBox.setGravity(android.view.Gravity.CENTER);
        iconBox.setPadding(16, 16, 16, 16);
        
        TextView mapIcon = new TextView(this);
        mapIcon.setText("🗺");
        mapIcon.setTextSize(28);
        iconBox.addView(mapIcon);
        
        LinearLayout.LayoutParams iconBoxParams = new LinearLayout.LayoutParams(
                80, 80);
        iconBoxParams.setMargins(0, 0, 16, 0);
        appInfo.addView(iconBox, iconBoxParams);
        
        // Bilgi kısmı
        LinearLayout textInfo = new LinearLayout(this);
        textInfo.setOrientation(LinearLayout.VERTICAL);
        textInfo.setPadding(0, 0, 0, 0);
        
        targetAppLabel = new TextView(this);
        targetAppLabel.setText("(seçilmedi)");
        targetAppLabel.setTextColor(0xFFFFFFFF);
        targetAppLabel.setTextSize(17);
        targetAppLabel.setTypeface(null, android.graphics.Typeface.NORMAL);
        LinearLayout.LayoutParams targetLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        targetLabelParams.setMargins(0, 0, 0, 4);
        textInfo.addView(targetAppLabel, targetLabelParams);
        
        TextView appDesc = new TextView(this);
        appDesc.setText("Seçili uygulamayı araç ekranına yansıt");
        appDesc.setTextColor(0xAAFFFFFF); // %67 opaklık (düşük kontrast)
        appDesc.setTextSize(13);
        textInfo.addView(appDesc);
        
        appInfo.addView(textInfo, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        
        appCard.addView(appInfo);
        
        // Alt kısım: Değiştir (secondary) ve Ana Ekrana Al (primary) butonları
        LinearLayout appButtons = new LinearLayout(this);
        appButtons.setOrientation(LinearLayout.HORIZONTAL);
        
        Button btnSelectApp = new Button(this);
        btnSelectApp.setText("Değiştir");
        btnSelectApp.setTextColor(0xCCFFFFFF); // %80 opaklık (secondary)
        btnSelectApp.setTextSize(14);
        btnSelectApp.setTypeface(null, android.graphics.Typeface.NORMAL);
        btnSelectApp.setBackgroundColor(0xFF1A2330); // Koyu gri (secondary)
        btnSelectApp.setPadding(16, 14, 16, 14);
        LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        selectParams.setMargins(0, 0, 6, 0);
        appButtons.addView(btnSelectApp, selectParams);

        btnSelectApp.setOnClickListener(v -> {
            autoSelectTargetApp();
        });

        Button btnLaunchOnCluster = new Button(this);
        btnLaunchOnCluster.setText("▶ Ana Ekrana Al");
        btnLaunchOnCluster.setTextColor(0xFFFFFFFF);
        btnLaunchOnCluster.setTextSize(14);
        btnLaunchOnCluster.setTypeface(null, android.graphics.Typeface.BOLD);
        btnLaunchOnCluster.setBackgroundColor(0xFF3DAEA8); // Accent rengi (primary)
        btnLaunchOnCluster.setPadding(16, 14, 16, 14);
        LinearLayout.LayoutParams launchParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        launchParams.setMargins(6, 0, 0, 0);
        appButtons.addView(btnLaunchOnCluster, launchParams);
        
        btnLaunchOnCluster.setOnClickListener(v -> {
            launchSelectedAppOnCluster(0);
        });
        
        appCard.addView(appButtons);
        projectionTabContent.addView(appCard, appCardParams);

        // ============================================
        // NAVİGASYON DAVRANIŞI - TEK KART İÇİNDE ÜÇ ALT BÖLÜM
        // ============================================
        
        // Ana grup başlığı
        TextView mainGroupTitle = new TextView(this);
        mainGroupTitle.setText("Navigasyon Davranışı");
        mainGroupTitle.setTextSize(18);
        mainGroupTitle.setTextColor(0xFFFFFFFF); // Yüksek kontrast beyaz
        mainGroupTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        mainGroupTitle.setPadding(16, 24, 16, 8);
        projectionTabContent.addView(mainGroupTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Tek ana kart (tüm alt bölümler bu kart içinde)
        LinearLayout mainCardContainer = new LinearLayout(this);
        mainCardContainer.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable mainCardBg = new android.graphics.drawable.GradientDrawable();
        mainCardBg.setColor(0xFF151C24); // Arka plandan %6-10 daha açık
        mainCardBg.setCornerRadius(12);
        mainCardContainer.setBackground(mainCardBg);
        LinearLayout.LayoutParams mainCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        mainCardParams.setMargins(16, 0, 16, 32); // Gruplar arası boşluk
        
        // ============================================
        // ALT BÖLÜM 1 - BAŞLATMA (En kontrastlı, en üstte)
        // ============================================
        
        // Alt bölüm başlığı (en kontrastlı)
        TextView section1Title = new TextView(this);
        section1Title.setText("Başlatma");
        section1Title.setTextSize(17);
        section1Title.setTextColor(0xFFFFFFFF); // Yüksek kontrast
        section1Title.setTypeface(null, android.graphics.Typeface.BOLD);
        section1Title.setPadding(20, 20, 20, 8);
        mainCardContainer.addView(section1Title);
        
        // Alt bölüm açıklaması
        TextView section1Desc = new TextView(this);
        section1Desc.setText("Ne zaman başlasın?");
        section1Desc.setTextSize(13);
        section1Desc.setTextColor(0xAAFFFFFF); // %67 opaklık
        section1Desc.setPadding(20, 0, 20, 12);
        mainCardContainer.addView(section1Desc);
        
        RadioGroup radioGroup = new RadioGroup(this);
        radioGroup.setOrientation(LinearLayout.VERTICAL);
        radioGroup.setPadding(20, 0, 20, 0);
        
        // RadioButton 1: Motor Çalışınca (Power mode 2)
        LinearLayout option1Container = new LinearLayout(this);
        option1Container.setOrientation(LinearLayout.HORIZONTAL);
        option1Container.setPadding(16, 16, 16, 16);
        option1Container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        option1Container.setClickable(true);
        option1Container.setFocusable(true);
        
        LinearLayout iconCircle1 = new LinearLayout(this);
        iconCircle1.setOrientation(LinearLayout.VERTICAL);
        iconCircle1.setBackgroundColor(0xFF1A2330); // Yumuşak, seçili olmayan durum
        iconCircle1.setGravity(android.view.Gravity.CENTER);
        iconCircle1.setPadding(10, 10, 10, 10);
        iconCircle1.setId(android.view.View.generateViewId()); // ID ekle (seçili durum için)
        
        TextView icon1 = new TextView(this);
        icon1.setText("🚗");
        icon1.setTextSize(20);
        icon1.setTextColor(0xFFFFFFFF);
        iconCircle1.addView(icon1);
        
        LinearLayout.LayoutParams iconCircle1Params = new LinearLayout.LayoutParams(
                48, 48);
        iconCircle1Params.setMargins(0, 0, 12, 0);
        option1Container.addView(iconCircle1, iconCircle1Params);
        
        LinearLayout textColumn1 = new LinearLayout(this);
        textColumn1.setOrientation(LinearLayout.VERTICAL);
        
        TextView title1 = new TextView(this);
        title1.setText("Motor çalışınca");
        title1.setTextColor(0xFFFFFFFF); // Yüksek kontrast
        title1.setTextSize(16);
        title1.setTypeface(null, android.graphics.Typeface.NORMAL); // Medium weight
        textColumn1.addView(title1);
        
        TextView desc1 = new TextView(this);
        desc1.setText("Direkt start verildiğinde");
        desc1.setTextColor(0xAAFFFFFF); // %67 opaklık (düşük kontrast açıklama)
        desc1.setTextSize(13);
        desc1.setPadding(0, 2, 0, 0);
        textColumn1.addView(desc1);
        
        LinearLayout.LayoutParams textParams1 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        option1Container.addView(textColumn1, textParams1);
        
        RadioButton radioMode2 = new RadioButton(this);
        radioMode2.setId(2);
        radioMode2.setClickable(false);
        radioMode2.setFocusable(false);
        option1Container.addView(radioMode2);
        
        option1Container.setOnClickListener(v -> radioGroup.check(2));
        
        radioGroup.addView(option1Container);
        
        // Ayırıcı çizgi (hafif - %12-18 opaklık)
        android.view.View divider1 = new android.view.View(this);
        divider1.setBackgroundColor(0x1FFFFFFF); // %12 opaklık (çok silik değil)
        radioGroup.addView(divider1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // RadioButton 2: Araç Hazır Durumdayken (Power mode 1)
        LinearLayout option2Container = new LinearLayout(this);
        option2Container.setOrientation(LinearLayout.HORIZONTAL);
        option2Container.setPadding(16, 16, 16, 16);
        option2Container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        option2Container.setClickable(true);
        option2Container.setFocusable(true);
        
        LinearLayout iconCircle2 = new LinearLayout(this);
        iconCircle2.setOrientation(LinearLayout.VERTICAL);
        iconCircle2.setBackgroundColor(0xFF1A2330); // Yumuşak, seçili olmayan durum
        iconCircle2.setGravity(android.view.Gravity.CENTER);
        iconCircle2.setPadding(10, 10, 10, 10);
        iconCircle2.setId(android.view.View.generateViewId()); // ID ekle
        
        TextView icon2 = new TextView(this);
        icon2.setText("📡");
        icon2.setTextSize(20);
        icon2.setTextColor(0xFFFFFFFF);
        iconCircle2.addView(icon2);
        
        LinearLayout.LayoutParams iconCircle2Params = new LinearLayout.LayoutParams(
                48, 48);
        iconCircle2Params.setMargins(0, 0, 12, 0);
        option2Container.addView(iconCircle2, iconCircle2Params);
        
        LinearLayout textColumn2 = new LinearLayout(this);
        textColumn2.setOrientation(LinearLayout.VERTICAL);
        
        TextView title2 = new TextView(this);
        title2.setText("Araç hazır olduğunda");
        title2.setTextColor(0xFFFFFFFF); // Yüksek kontrast
        title2.setTextSize(16);
        title2.setTypeface(null, android.graphics.Typeface.NORMAL); // Medium weight
        textColumn2.addView(title2);
        
        TextView desc2 = new TextView(this);
        desc2.setText("Engine Start 1 kere basınca (Frensiz)");
        desc2.setTextColor(0xAAFFFFFF); // %67 opaklık
        desc2.setTextSize(13);
        desc2.setPadding(0, 2, 0, 0);
        textColumn2.addView(desc2);
        
        LinearLayout.LayoutParams textParams2 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        option2Container.addView(textColumn2, textParams2);
        
        RadioButton radioMode1 = new RadioButton(this);
        radioMode1.setId(1);
        radioMode1.setClickable(false);
        radioMode1.setFocusable(false);
        option2Container.addView(radioMode1);
        
        option2Container.setOnClickListener(v -> radioGroup.check(1));
        
        radioGroup.addView(option2Container);
        
        // Ayırıcı çizgi (hafif)
        android.view.View divider2 = new android.view.View(this);
        divider2.setBackgroundColor(0x1FFFFFFF); // %12 opaklık
        radioGroup.addView(divider2, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // RadioButton 3: Elle Çalıştır (Power mode 0)
        LinearLayout option3Container = new LinearLayout(this);
        option3Container.setOrientation(LinearLayout.HORIZONTAL);
        option3Container.setPadding(16, 16, 16, 16);
        option3Container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        option3Container.setClickable(true);
        option3Container.setFocusable(true);
        
        LinearLayout iconCircle3 = new LinearLayout(this);
        iconCircle3.setOrientation(LinearLayout.VERTICAL);
        iconCircle3.setBackgroundColor(0xFF1A2330); // Yumuşak, seçili olmayan durum
        iconCircle3.setGravity(android.view.Gravity.CENTER);
        iconCircle3.setPadding(10, 10, 10, 10);
        iconCircle3.setId(android.view.View.generateViewId()); // ID ekle
        
        TextView icon3 = new TextView(this);
        icon3.setText("✋");
        icon3.setTextSize(20);
        icon3.setTextColor(0xFFFFFFFF);
        iconCircle3.addView(icon3);
        
        LinearLayout.LayoutParams iconCircle3Params = new LinearLayout.LayoutParams(
                48, 48);
        iconCircle3Params.setMargins(0, 0, 12, 0);
        option3Container.addView(iconCircle3, iconCircle3Params);
        
        LinearLayout textColumn3 = new LinearLayout(this);
        textColumn3.setOrientation(LinearLayout.VERTICAL);
        
        TextView title3 = new TextView(this);
        title3.setText("Elle çalıştır");
        title3.setTextColor(0xFFFFFFFF); // Yüksek kontrast
        title3.setTextSize(16);
        title3.setTypeface(null, android.graphics.Typeface.NORMAL); // Medium weight
        textColumn3.addView(title3);
        
        TextView desc3 = new TextView(this);
        desc3.setText("Kendiniz istediğinize zaman başlatın");
        desc3.setTextColor(0xAAFFFFFF); // %67 opaklık
        desc3.setTextSize(13);
        desc3.setPadding(0, 2, 0, 0);
        textColumn3.addView(desc3);
        
        LinearLayout.LayoutParams textParams3 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        option3Container.addView(textColumn3, textParams3);
        
        RadioButton radioManual = new RadioButton(this);
        radioManual.setId(0);
        radioManual.setClickable(false);
        radioManual.setFocusable(false);
        option3Container.addView(radioManual);
        
        option3Container.setOnClickListener(v -> radioGroup.check(0));
        
        radioGroup.addView(option3Container);
        
        mainCardContainer.addView(radioGroup);
        
        // Alt bölüm 1 ve 2 arası ayırıcı (hafif)
        android.view.View sectionDivider1 = new android.view.View(this);
        sectionDivider1.setBackgroundColor(0x1FFFFFFF); // %12 opaklık
        LinearLayout.LayoutParams dividerParams1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams1.setMargins(20, 24, 20, 24); // Üst ve alt boşluk
        mainCardContainer.addView(sectionDivider1, dividerParams1);
        
        // ============================================
        // ALT BÖLÜM 2 - KAPANIŞ (Orta kontrast, ikincil karar)
        // ============================================
        
        // Alt bölüm başlığı (orta kontrast)
        TextView section2Title = new TextView(this);
        section2Title.setText("Kapanış");
        section2Title.setTextSize(15);
        section2Title.setTextColor(0xE6FFFFFF); // %90 opaklık (orta kontrast)
        section2Title.setTypeface(null, android.graphics.Typeface.NORMAL);
        section2Title.setPadding(20, 0, 20, 8);
        mainCardContainer.addView(section2Title);
        
        // Alt bölüm açıklaması
        TextView section2Desc = new TextView(this);
        section2Desc.setText("Araç kapanınca ne olsun?");
        section2Desc.setTextSize(13);
        section2Desc.setTextColor(0xAAFFFFFF); // %67 opaklık
        section2Desc.setPadding(20, 0, 20, 12);
        mainCardContainer.addView(section2Desc);

        // Kaydedilmiş ayarı yükle
        loadPowerModeSetting(radioGroup);

        // RadioGroup değişiklik listener'ı (seçili durumda accent rengi)
        final LinearLayout[] iconCircles = {iconCircle1, iconCircle2, iconCircle3};
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int selectedMode = checkedId;
            savePowerModeSetting(selectedMode);
            String modeName = selectedMode == 2 ? "Motor Çalışınca" : 
                             (selectedMode == 1 ? "Araç Hazır Durumdayken" : "Elle Çalıştır");
            log("Navigasyon açma modu: " + modeName);
            
            // Seçili durumda accent rengi, seçili olmayanlarda sade
            for (int i = 0; i < iconCircles.length; i++) {
                LinearLayout iconCircle = iconCircles[i];
                if (iconCircle != null) {
                    if ((selectedMode == 2 && i == 0) || (selectedMode == 1 && i == 1) || (selectedMode == 0 && i == 2)) {
                        // Seçili: accent rengi (teal/mavi)
                        iconCircle.setBackgroundColor(0xFF3DAEA8); // Tek accent rengi
                    } else {
                        // Seçili değil: sade
                        iconCircle.setBackgroundColor(0xFF1A2330);
                    }
                }
            }
        });
        
        // İlk yüklemede seçili durumu göster (GRUP 1)
        handler.post(() -> {
            int savedMode = getSharedPreferences("MapControlPrefs", MODE_PRIVATE).getInt("powerModeSetting", 2);
            radioGroup.check(savedMode);
        });

        RadioGroup autoCloseRadioGroup = new RadioGroup(this);
        autoCloseRadioGroup.setOrientation(LinearLayout.VERTICAL);
        autoCloseRadioGroup.setPadding(20, 0, 20, 0);

        // RadioButton 1: Evet
        LinearLayout autoCloseOption1Container = new LinearLayout(this);
        autoCloseOption1Container.setOrientation(LinearLayout.HORIZONTAL);
        autoCloseOption1Container.setPadding(16, 16, 16, 16);
        autoCloseOption1Container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        autoCloseOption1Container.setClickable(true);
        autoCloseOption1Container.setFocusable(true);
        
        LinearLayout autoCloseIconCircle1 = new LinearLayout(this);
        autoCloseIconCircle1.setOrientation(LinearLayout.VERTICAL);
        autoCloseIconCircle1.setBackgroundColor(0xFF1A2330); // Yumuşak, seçili olmayan
        autoCloseIconCircle1.setGravity(android.view.Gravity.CENTER);
        autoCloseIconCircle1.setPadding(10, 10, 10, 10);
        autoCloseIconCircle1.setId(android.view.View.generateViewId());
        
        TextView autoCloseIcon1 = new TextView(this);
        autoCloseIcon1.setText("✅");
        autoCloseIcon1.setTextSize(20);
        autoCloseIcon1.setTextColor(0xFFFFFFFF);
        autoCloseIconCircle1.addView(autoCloseIcon1);
        
        LinearLayout.LayoutParams autoCloseIconCircle1Params = new LinearLayout.LayoutParams(48, 48);
        autoCloseIconCircle1Params.setMargins(0, 0, 12, 0);
        autoCloseOption1Container.addView(autoCloseIconCircle1, autoCloseIconCircle1Params);
        
        LinearLayout autoCloseTextColumn1 = new LinearLayout(this);
        autoCloseTextColumn1.setOrientation(LinearLayout.VERTICAL);
        
        TextView autoCloseTitle1 = new TextView(this);
        autoCloseTitle1.setText("Evet");
        autoCloseTitle1.setTextColor(0xFFFFFFFF); // Yüksek kontrast
        autoCloseTitle1.setTextSize(16);
        autoCloseTitle1.setTypeface(null, android.graphics.Typeface.NORMAL); // Medium weight
        autoCloseTextColumn1.addView(autoCloseTitle1);
        
        TextView autoCloseDesc1 = new TextView(this);
        autoCloseDesc1.setText("Araç kapanınca otomatik kapat");
        autoCloseDesc1.setTextColor(0xAAFFFFFF); // %67 opaklık
        autoCloseDesc1.setTextSize(13);
        autoCloseDesc1.setPadding(0, 2, 0, 0);
        autoCloseTextColumn1.addView(autoCloseDesc1);
        
        LinearLayout.LayoutParams autoCloseTextParams1 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        autoCloseOption1Container.addView(autoCloseTextColumn1, autoCloseTextParams1);
        
        RadioButton autoCloseRadioYes = new RadioButton(this);
        autoCloseRadioYes.setId(200);
        autoCloseRadioYes.setClickable(false);
        autoCloseRadioYes.setFocusable(false);
        autoCloseOption1Container.addView(autoCloseRadioYes);
        
        autoCloseOption1Container.setOnClickListener(v -> autoCloseRadioGroup.check(200));
        
        autoCloseRadioGroup.addView(autoCloseOption1Container);
        
        // Ayırıcı çizgi (hafif)
        android.view.View autoCloseDivider1 = new android.view.View(this);
        autoCloseDivider1.setBackgroundColor(0x1FFFFFFF); // %12 opaklık
        autoCloseRadioGroup.addView(autoCloseDivider1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // RadioButton 2: Hayır
        LinearLayout autoCloseOption2Container = new LinearLayout(this);
        autoCloseOption2Container.setOrientation(LinearLayout.HORIZONTAL);
        autoCloseOption2Container.setPadding(16, 16, 16, 16);
        autoCloseOption2Container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        autoCloseOption2Container.setClickable(true);
        autoCloseOption2Container.setFocusable(true);
        
        LinearLayout autoCloseIconCircle2 = new LinearLayout(this);
        autoCloseIconCircle2.setOrientation(LinearLayout.VERTICAL);
        autoCloseIconCircle2.setBackgroundColor(0xFF1A2330); // Yumuşak, seçili olmayan
        autoCloseIconCircle2.setGravity(android.view.Gravity.CENTER);
        autoCloseIconCircle2.setPadding(10, 10, 10, 10);
        autoCloseIconCircle2.setId(android.view.View.generateViewId());
        
        TextView autoCloseIcon2 = new TextView(this);
        autoCloseIcon2.setText("❌");
        autoCloseIcon2.setTextSize(20);
        autoCloseIcon2.setTextColor(0xFFFFFFFF);
        autoCloseIconCircle2.addView(autoCloseIcon2);
        
        LinearLayout.LayoutParams autoCloseIconCircle2Params = new LinearLayout.LayoutParams(48, 48);
        autoCloseIconCircle2Params.setMargins(0, 0, 12, 0);
        autoCloseOption2Container.addView(autoCloseIconCircle2, autoCloseIconCircle2Params);
        
        LinearLayout autoCloseTextColumn2 = new LinearLayout(this);
        autoCloseTextColumn2.setOrientation(LinearLayout.VERTICAL);
        
        TextView autoCloseTitle2 = new TextView(this);
        autoCloseTitle2.setText("Hayır");
        autoCloseTitle2.setTextColor(0xFFFFFFFF); // Yüksek kontrast
        autoCloseTitle2.setTextSize(16);
        autoCloseTitle2.setTypeface(null, android.graphics.Typeface.NORMAL); // Medium weight
        autoCloseTextColumn2.addView(autoCloseTitle2);
        
        TextView autoCloseDesc2 = new TextView(this);
        autoCloseDesc2.setText("Otomatik kapatma yapılmayacak");
        autoCloseDesc2.setTextColor(0xAAFFFFFF); // %67 opaklık
        autoCloseDesc2.setTextSize(13);
        autoCloseDesc2.setPadding(0, 2, 0, 0);
        autoCloseTextColumn2.addView(autoCloseDesc2);
        
        LinearLayout.LayoutParams autoCloseTextParams2 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        autoCloseOption2Container.addView(autoCloseTextColumn2, autoCloseTextParams2);
        
        RadioButton autoCloseRadioNo = new RadioButton(this);
        autoCloseRadioNo.setId(201);
        autoCloseRadioNo.setClickable(false);
        autoCloseRadioNo.setFocusable(false);
        autoCloseOption2Container.addView(autoCloseRadioNo);
        
        autoCloseOption2Container.setOnClickListener(v -> autoCloseRadioGroup.check(201));
        
        autoCloseRadioGroup.addView(autoCloseOption2Container);
        
        mainCardContainer.addView(autoCloseRadioGroup);
        
        // Alt bölüm 2 ve 3 arası ayırıcı (hafif)
        android.view.View sectionDivider2 = new android.view.View(this);
        sectionDivider2.setBackgroundColor(0x1FFFFFFF); // %12 opaklık
        LinearLayout.LayoutParams dividerParams2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams2.setMargins(20, 24, 20, 24); // Üst ve alt boşluk
        mainCardContainer.addView(sectionDivider2, dividerParams2);
        
        // ============================================
        // ALT BÖLÜM 3 - KONTROL (En sakin, küçük etiket)
        // ============================================
        
        // Alt bölüm başlığı (en sakin - küçük bilgi ikonu ile)
        LinearLayout section3Header = new LinearLayout(this);
        section3Header.setOrientation(LinearLayout.HORIZONTAL);
        section3Header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        section3Header.setPadding(20, 0, 20, 8);
        
        TextView section3Title = new TextView(this);
        section3Title.setText("Kontrol");
        section3Title.setTextSize(14);
        section3Title.setTextColor(0xCCFFFFFF); // %80 opaklık (en sakin)
        section3Title.setTypeface(null, android.graphics.Typeface.NORMAL);
        section3Header.addView(section3Title);
        
        // Küçük bilgi ikonu (gelişmiş ayar göstergesi)
        TextView infoIcon = new TextView(this);
        infoIcon.setText(" ℹ️");
        infoIcon.setTextSize(12);
        infoIcon.setTextColor(0xAAFFFFFF); // %67 opaklık
        section3Header.addView(infoIcon);
        
        mainCardContainer.addView(section3Header);
        
        // Alt bölüm açıklaması
        TextView section3Desc = new TextView(this);
        section3Desc.setText("Harita kontrol tuşunu devre dışı bırak");
        section3Desc.setTextSize(13);
        section3Desc.setTextColor(0xAAFFFFFF); // %67 opaklık
        section3Desc.setPadding(20, 0, 20, 12);
        mainCardContainer.addView(section3Desc);

        // Uyarı mesajı (expandable - varsayılan kapalı)
        final LinearLayout warningContainer = new LinearLayout(this);
        warningContainer.setOrientation(LinearLayout.VERTICAL);
        warningContainer.setVisibility(android.view.View.GONE); // Varsayılan kapalı
        
        TextView warningText = new TextView(this);
        warningText.setText("Navigasyon sürücü ekranına geldiğinde çıkmak için O tuşuyla çıkılıyor ancak bazen Lastık, Kilometre bilgisi geri gelmeyebilir. Bu özelliği kapatırsanız haritayı manuel açtıktan sonra O tuşuyla kapattığınızda sorun yaşayabilirsiniz. Aracı aç kapat yaparak çözebilirsiniz.");
        warningText.setTextSize(12);
        warningText.setTextColor(0xCCFFBD2E); // %80 opaklık (kontrollü sarı)
        warningText.setPadding(20, 8, 20, 12);
        warningText.setLineSpacing(2, 1.0f);
        warningContainer.addView(warningText);
        
        // Expandable buton (bilgi ikonu - kontrollü sarı)
        LinearLayout expandButton = new LinearLayout(this);
        expandButton.setOrientation(LinearLayout.HORIZONTAL);
        expandButton.setPadding(20, 8, 20, 8);
        expandButton.setClickable(true);
        expandButton.setFocusable(true);
        
        TextView expandIcon = new TextView(this);
        expandIcon.setText("ℹ️");
        expandIcon.setTextSize(14);
        expandIcon.setTextColor(0xCCFFBD2E); // %80 opaklık (kontrollü)
        expandButton.addView(expandIcon);
        
        TextView expandText = new TextView(this);
        expandText.setText(" Detaylı bilgi");
        expandText.setTextSize(12);
        expandText.setTextColor(0xCCFFBD2E); // %80 opaklık (kontrollü)
        expandButton.addView(expandText);
        
        expandButton.setOnClickListener(v -> {
            if (warningContainer.getVisibility() == android.view.View.GONE) {
                warningContainer.setVisibility(android.view.View.VISIBLE);
                expandIcon.setText("▼");
            } else {
                warningContainer.setVisibility(android.view.View.GONE);
                expandIcon.setText("ℹ️");
            }
        });
        
        mainCardContainer.addView(expandButton);
        mainCardContainer.addView(warningContainer);
        
        RadioGroup mapControlRadioGroup = new RadioGroup(this);
        mapControlRadioGroup.setOrientation(LinearLayout.VERTICAL);
        mapControlRadioGroup.setPadding(20, 0, 20, 0);

        // RadioButton 1: Açık
        LinearLayout mapControlOption1Container = new LinearLayout(this);
        mapControlOption1Container.setOrientation(LinearLayout.HORIZONTAL);
        mapControlOption1Container.setPadding(16, 16, 16, 16);
        mapControlOption1Container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        mapControlOption1Container.setClickable(true);
        mapControlOption1Container.setFocusable(true);
        
        LinearLayout mapControlIconCircle1 = new LinearLayout(this);
        mapControlIconCircle1.setOrientation(LinearLayout.VERTICAL);
        mapControlIconCircle1.setBackgroundColor(0xFF1A2330); // Yumuşak, seçili olmayan
        mapControlIconCircle1.setGravity(android.view.Gravity.CENTER);
        mapControlIconCircle1.setPadding(10, 10, 10, 10);
        mapControlIconCircle1.setId(android.view.View.generateViewId());
        
        TextView mapControlIcon1 = new TextView(this);
        mapControlIcon1.setText("✅");
        mapControlIcon1.setTextSize(20);
        mapControlIcon1.setTextColor(0xFFFFFFFF);
        mapControlIconCircle1.addView(mapControlIcon1);
        
        LinearLayout.LayoutParams mapControlIconCircle1Params = new LinearLayout.LayoutParams(48, 48);
        mapControlIconCircle1Params.setMargins(0, 0, 12, 0);
        mapControlOption1Container.addView(mapControlIconCircle1, mapControlIconCircle1Params);
        
        LinearLayout mapControlTextColumn1 = new LinearLayout(this);
        mapControlTextColumn1.setOrientation(LinearLayout.VERTICAL);
        
        TextView mapControlTitle1 = new TextView(this);
        mapControlTitle1.setText("Açık");
        mapControlTitle1.setTextColor(0xFFFFFFFF); // Yüksek kontrast
        mapControlTitle1.setTextSize(16);
        mapControlTitle1.setTypeface(null, android.graphics.Typeface.NORMAL); // Medium weight
        mapControlTextColumn1.addView(mapControlTitle1);
        
        TextView mapControlDesc1 = new TextView(this);
        mapControlDesc1.setText("Harita kontrol tuşu aktif");
        mapControlDesc1.setTextColor(0xAAFFFFFF); // %67 opaklık
        mapControlDesc1.setTextSize(13);
        mapControlDesc1.setPadding(0, 2, 0, 0);
        mapControlTextColumn1.addView(mapControlDesc1);
        
        LinearLayout.LayoutParams mapControlTextParams1 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        mapControlOption1Container.addView(mapControlTextColumn1, mapControlTextParams1);
        
        RadioButton mapControlRadioOn = new RadioButton(this);
        mapControlRadioOn.setId(100);
        mapControlRadioOn.setClickable(false);
        mapControlRadioOn.setFocusable(false);
        mapControlOption1Container.addView(mapControlRadioOn);
        
        mapControlOption1Container.setOnClickListener(v -> mapControlRadioGroup.check(100));
        
        mapControlRadioGroup.addView(mapControlOption1Container);
        
        // Ayırıcı çizgi (hafif)
        android.view.View mapControlDivider1 = new android.view.View(this);
        mapControlDivider1.setBackgroundColor(0x1FFFFFFF); // %12 opaklık
        mapControlRadioGroup.addView(mapControlDivider1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // RadioButton 2: Kapalı
        LinearLayout mapControlOption2Container = new LinearLayout(this);
        mapControlOption2Container.setOrientation(LinearLayout.HORIZONTAL);
        mapControlOption2Container.setPadding(16, 16, 16, 16);
        mapControlOption2Container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        mapControlOption2Container.setClickable(true);
        mapControlOption2Container.setFocusable(true);
        
        LinearLayout mapControlIconCircle2 = new LinearLayout(this);
        mapControlIconCircle2.setOrientation(LinearLayout.VERTICAL);
        mapControlIconCircle2.setBackgroundColor(0xFF1A2330); // Yumuşak, seçili olmayan
        mapControlIconCircle2.setGravity(android.view.Gravity.CENTER);
        mapControlIconCircle2.setPadding(10, 10, 10, 10);
        mapControlIconCircle2.setId(android.view.View.generateViewId());
        
        TextView mapControlIcon2 = new TextView(this);
        mapControlIcon2.setText("❌");
        mapControlIcon2.setTextSize(20);
        mapControlIcon2.setTextColor(0xFFFFFFFF);
        mapControlIconCircle2.addView(mapControlIcon2);
        
        LinearLayout.LayoutParams mapControlIconCircle2Params = new LinearLayout.LayoutParams(48, 48);
        mapControlIconCircle2Params.setMargins(0, 0, 12, 0);
        mapControlOption2Container.addView(mapControlIconCircle2, mapControlIconCircle2Params);
        
        LinearLayout mapControlTextColumn2 = new LinearLayout(this);
        mapControlTextColumn2.setOrientation(LinearLayout.VERTICAL);
        
        TextView mapControlTitle2 = new TextView(this);
        mapControlTitle2.setText("Kapalı");
        mapControlTitle2.setTextColor(0xFFFFFFFF); // Yüksek kontrast
        mapControlTitle2.setTextSize(16);
        mapControlTitle2.setTypeface(null, android.graphics.Typeface.NORMAL); // Medium weight
        mapControlTextColumn2.addView(mapControlTitle2);
        
        TextView mapControlDesc2 = new TextView(this);
        mapControlDesc2.setText("Harita kontrol tuşu devre dışı");
        mapControlDesc2.setTextColor(0xAAFFFFFF); // %67 opaklık
        mapControlDesc2.setTextSize(13);
        mapControlDesc2.setPadding(0, 2, 0, 0);
        mapControlTextColumn2.addView(mapControlDesc2);
        
        LinearLayout.LayoutParams mapControlTextParams2 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        mapControlOption2Container.addView(mapControlTextColumn2, mapControlTextParams2);
        
        RadioButton mapControlRadioOff = new RadioButton(this);
        mapControlRadioOff.setId(101);
        mapControlRadioOff.setClickable(false);
        mapControlRadioOff.setFocusable(false);
        mapControlOption2Container.addView(mapControlRadioOff);
        
        mapControlOption2Container.setOnClickListener(v -> mapControlRadioGroup.check(101));
        
        mapControlRadioGroup.addView(mapControlOption2Container);
        
        mainCardContainer.addView(mapControlRadioGroup);
        
        // Alt bölüm 3 ve 4 arası ayırıcı (hafif)
        android.view.View sectionDivider3 = new android.view.View(this);
        sectionDivider3.setBackgroundColor(0x1FFFFFFF); // %12 opaklık
        LinearLayout.LayoutParams dividerParams3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams3.setMargins(20, 24, 20, 24); // Üst ve alt boşluk
        mainCardContainer.addView(sectionDivider3, dividerParams3);
        
        // ============================================
        // ALT BÖLÜM 4 - PARMAK HAREKETLERİ (En sakin)
        // ============================================
        
        // Alt bölüm başlığı (en sakin - küçük bilgi ikonu ile)
        LinearLayout section4Header = new LinearLayout(this);
        section4Header.setOrientation(LinearLayout.HORIZONTAL);
        section4Header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        section4Header.setPadding(20, 0, 20, 8);
        
        // Ana kartı projectionTabContent'e ekle
        projectionTabContent.addView(mainCardContainer, mainCardParams);

        // Kaydedilmiş ayarları yükle
        loadMapControlKeySetting(mapControlRadioGroup);

        // RadioGroup değişiklik listener'ı (seçili durumda accent rengi)
        final LinearLayout[] mapControlIconCircles = {mapControlIconCircle1, mapControlIconCircle2};
        mapControlRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isEnabled = (checkedId == 100); // 100 = Açık, 101 = Kapalı
            saveMapControlKeySetting(isEnabled);
            
            if (isEnabled) {
                log("Harita kontrol tuşu açıldı");
                startKeyEventVdbusListener();
            } else {
                log("Harita kontrol tuşu kapatıldı");
                stopKeyEventVdbusListener();
            }
            
            // Seçili durumda accent rengi
            for (int i = 0; i < mapControlIconCircles.length; i++) {
                LinearLayout iconCircle = mapControlIconCircles[i];
                if (iconCircle != null) {
                    if ((isEnabled && i == 0) || (!isEnabled && i == 1)) {
                        // Seçili: accent rengi
                        iconCircle.setBackgroundColor(0xFF3DAEA8); // Tek accent rengi
                    } else {
                        // Seçili değil: sade
                        iconCircle.setBackgroundColor(0xFF1A2330);
                    }
                }
            }
        });
        
        // İlk yüklemede seçili durumu göster (GRUP 3)
        handler.post(() -> {
            boolean savedMapControl = getSharedPreferences("MapControlPrefs", MODE_PRIVATE).getBoolean("mapControlKeyDisabled", false);
            mapControlRadioGroup.check(savedMapControl ? 101 : 100);
            
        });
        
        // GRUP 2 için kaydedilmiş ayarı yükle
        loadAutoCloseOnPowerOffSetting(autoCloseRadioGroup);
        
        // GRUP 2 için listener (seçili durumda accent rengi)
        final LinearLayout[] autoCloseIconCircles = {autoCloseIconCircle1, autoCloseIconCircle2};
        autoCloseRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isEnabled = (checkedId == 200); // 200 = Evet, 201 = Hayır
            saveAutoCloseOnPowerOffSetting(isEnabled);
            
            if (isEnabled) {
                log("Araç kapanınca otomatik kapatma açıldı");
            } else {
                log("Araç kapanınca otomatik kapatma kapatıldı");
            }
            
            // Seçili durumda accent rengi
            for (int i = 0; i < autoCloseIconCircles.length; i++) {
                LinearLayout iconCircle = autoCloseIconCircles[i];
                if (iconCircle != null) {
                    if ((isEnabled && i == 0) || (!isEnabled && i == 1)) {
                        // Seçili: accent rengi
                        iconCircle.setBackgroundColor(0xFF3DAEA8); // Tek accent rengi
                    } else {
                        // Seçili değil: sade
                        iconCircle.setBackgroundColor(0xFF1A2330);
                    }
                }
            }
        });
        
        // İlk yüklemede seçili durumu göster (GRUP 2)
        handler.post(() -> {
            boolean savedAutoClose = getSharedPreferences("MapControlPrefs", MODE_PRIVATE).getBoolean("autoCloseOnPowerOff", true);
            autoCloseRadioGroup.check(savedAutoClose ? 200 : 201);
        });

        // === HAFIZA MODU TAB İÇERİĞİ ===
        driveModeScrollView = new ScrollView(this);
        driveModeScrollView.setBackgroundColor(0xFF101922);
        driveModeScrollView.setPadding(0, 0, 0, 0);
        driveModeScrollView.setFillViewport(true);
        
        driveModeTabContent = new LinearLayout(this);
        driveModeTabContent.setOrientation(LinearLayout.VERTICAL);
        driveModeTabContent.setPadding(0, 0, 0, 0);
        driveModeTabContent.setBackgroundColor(0xFF101922);
        driveModeScrollView.addView(driveModeTabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // Hafıza Modu başlığı
        TextView driveModeTitle = new TextView(this);
        driveModeTitle.setText("Sürüş Modları");
        driveModeTitle.setTextSize(20);
        driveModeTitle.setTextColor(0xFFFFFFFF);
        driveModeTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        driveModeTitle.setPadding(16, 24, 16, 12);
        driveModeTabContent.addView(driveModeTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Tek satır container (tüm modlar yan yana - responsive)
        LinearLayout driveModeContainer = new LinearLayout(this);
        driveModeContainer.setOrientation(LinearLayout.HORIZONTAL);
        driveModeContainer.setBackgroundColor(0xFF1C2630);
        LinearLayout.LayoutParams driveModeContainerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        driveModeContainerParams.setMargins(16, 0, 16, 16);
        driveModeContainer.setPadding(2, 12, 2, 12);
        
        RadioGroup driveModeRadioGroup = new RadioGroup(this);
        driveModeRadioGroup.setOrientation(LinearLayout.HORIZONTAL);
        RadioGroup.LayoutParams radioGroupParams = new RadioGroup.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        driveModeRadioGroup.setLayoutParams(radioGroupParams);
        
        // Sürüş modları: -1=Hiçbiri, 0=Eco, 1=Normal, 2=Sport, 3=Snow, 4=Mud, 5=Rock/Offroad, 7=Sand
        String[] driveModeNames = {"Hiçbiri", "Eco", "Normal", "Sport", "Snow", "Mud", "Offroad", "Sand"};
        int[] driveModeValues = {-1, 0, 1, 2, 3, 4, 5, 7}; // Mode değerleri
        int[] driveModeIds = {9, 10, 11, 12, 13, 14, 15, 17}; // Radio button ID'leri
        
        // Seçili modu kontrol et (varsayılan: -1 = Hiçbiri)
        int savedMode = prefs.getInt("driveModeSetting", -1);
        if (savedMode == -1) {
            // Varsayılan olarak "Hiçbiri" seçili
            savedMode = -1;
        }
        
        // Kart referanslarını saklamak için
        final LinearLayout[] modeCards = new LinearLayout[driveModeNames.length];
        final TextView[] modeTitles = new TextView[driveModeNames.length];
        final android.view.View[] indicators = new android.view.View[driveModeNames.length];
        
        for (int i = 0; i < driveModeNames.length; i++) {
            final int modeValue = driveModeValues[i];
            final int radioId = driveModeIds[i];
            final String modeName = driveModeNames[i];
            final int cardIndex = i;
            
            // Mod kartı
            LinearLayout modeCard = new LinearLayout(this);
            modeCard.setOrientation(LinearLayout.VERTICAL);
            modeCard.setGravity(android.view.Gravity.CENTER);
            modeCard.setPadding(4, 12, 4, 0); // Alt padding 0 (indicator için)
            modeCard.setClickable(true);
            modeCard.setFocusable(true);
            
            // Minimum yükseklik
            modeCard.setMinimumHeight(70);
            
            // Seçili mi kontrol et
            boolean isSelected = (savedMode == modeValue);
            
            // Arka plan renkleri
            int bgColor = isSelected ? 0xFF2A3A47 : 0xFF1A1F26; // Seçili: daha açık, Seçili değil: daha koyu
            modeCard.setBackgroundColor(bgColor);
            
            // Responsive: weight ile eşit genişlik, minimum margin
            RadioGroup.LayoutParams cardParams = new RadioGroup.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            cardParams.setMargins(1, 0, 1, 0);
            
            // Mod adı
            TextView modeTitle = new TextView(this);
            modeTitle.setText(modeName);
            // Metin rengi: Seçili = %100 beyaz, Seçili değil = %85 beyaz
            modeTitle.setTextColor(isSelected ? 0xFFFFFFFF : 0xD9FFFFFF); // %85 = 0xD9
            modeTitle.setTextSize(13);
            // Metin ağırlığı: Seçili = Semibold, Seçili değil = Regular
            modeTitle.setTypeface(null, isSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            modeTitle.setGravity(android.view.Gravity.CENTER);
            modeTitle.setMaxLines(2);
            modeTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            modeTitle.setLineSpacing(2, 1.0f);
            modeCard.addView(modeTitle);
            
            // Alt çizgi (indicator) - sadece seçili olan için
            android.view.View indicator = new android.view.View(this);
            indicator.setBackgroundColor(0xFF4CAF50); // Success rengi (yeşil)
            LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (int)(4 * getResources().getDisplayMetrics().density)); // 4dp
            indicatorParams.setMargins(0, 8, 0, 0);
            indicator.setVisibility(isSelected ? android.view.View.VISIBLE : android.view.View.GONE);
            modeCard.addView(indicator, indicatorParams);
            
            // Referansları sakla
            modeCards[i] = modeCard;
            modeTitles[i] = modeTitle;
            indicators[i] = indicator;
            
            // RadioButton (görünmez ama seçim için gerekli)
            RadioButton radioButton = new RadioButton(this);
            radioButton.setId(radioId);
            radioButton.setVisibility(android.view.View.GONE);
            modeCard.addView(radioButton);
            
            // Tıklama listener
            modeCard.setOnClickListener(v -> {
                driveModeRadioGroup.check(radioId);
            });
            
            // Tüm modları tek satıra ekle
            driveModeRadioGroup.addView(modeCard, cardParams);
            
            // Divider ekle (son eleman hariç) - kartların sağına
            if (i < driveModeNames.length - 1) {
                android.view.View divider = new android.view.View(this);
                divider.setBackgroundColor(0x1FFFFFFF); // %12 beyaz (0x1F = ~12%)
                RadioGroup.LayoutParams dividerParams = new RadioGroup.LayoutParams(
                        1, LinearLayout.LayoutParams.MATCH_PARENT);
                dividerParams.setMargins(0, 12, 0, 12);
                driveModeRadioGroup.addView(divider, dividerParams);
            }
        }
        
        driveModeContainer.addView(driveModeRadioGroup);
        driveModeTabContent.addView(driveModeContainer, driveModeContainerParams);

        // Kaydedilmiş hafıza modu ayarını yükle (varsayılan: Hiçbiri)
        if (savedMode == -1) {
            driveModeRadioGroup.check(9); // Hiçbiri
        } else {
            loadDriveModeSetting(driveModeRadioGroup);
        }

        // RadioGroup değişiklik listener'ı
        driveModeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int selectedMode = -1;
            int selectedIndex = -1;
            
            // "Hiçbiri" kontrolü
            if (checkedId == 9) {
                selectedMode = -1;
                selectedIndex = 0;
            } else {
                // Diğer modları bul
                for (int i = 0; i < driveModeIds.length; i++) {
                    if (driveModeIds[i] == checkedId) {
                        selectedMode = driveModeValues[i];
                        selectedIndex = i;
                        break;
                    }
                }
            }
            
            if (selectedIndex >= 0) {
                saveDriveModeSetting(selectedMode);
                
                // Tüm kartları güncelle
                for (int i = 0; i < modeCards.length; i++) {
                    if (modeCards[i] != null) {
                        boolean isSelected = (i == selectedIndex);
                        
                        // Arka plan rengi
                        modeCards[i].setBackgroundColor(isSelected ? 0xFF2A3A47 : 0xFF1A1F26);
                        
                        // Metin rengi ve ağırlığı
                        if (modeTitles[i] != null) {
                            modeTitles[i].setTextColor(isSelected ? 0xFFFFFFFF : 0xD9FFFFFF);
                            modeTitles[i].setTypeface(null, isSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                        }
                        
                        // Indicator görünürlüğü
                        if (indicators[i] != null) {
                            indicators[i].setVisibility(isSelected ? android.view.View.VISIBLE : android.view.View.GONE);
                        }
                    }
                }
                
                if (selectedMode == -1) {
                    log("Hafıza modu seçildi: Hiçbiri (Otomatik ayarlama yapılmayacak)");
                } else {
                    log("Hafıza modu seçildi: " + driveModeNames[selectedIndex] + " (Mode: " + selectedMode + ")");
                    // Seçilen modu direkt araca gönder (hemen uygula)
                    setDriveMode(selectedMode);
                }
            }
        });

        // === ARAÇ VE SÜRÜCÜ YARDIMLARI BÖLÜMÜ ===
        TextView assistTitle = new TextView(this);
        assistTitle.setText("Araç ve Sürücü Yardımları");
        assistTitle.setTextSize(20);
        assistTitle.setTextColor(0xFFFFFFFF);
        assistTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        assistTitle.setPadding(16, 24, 16, 8);
        driveModeTabContent.addView(assistTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Alt açıklama
        TextView assistSubtitle = new TextView(this);
        assistSubtitle.setText("Sürüş güvenliği ve konfor ayarları");
        assistSubtitle.setTextSize(14);
        assistSubtitle.setTextColor(0xFF9DABB9);
        assistSubtitle.setPadding(16, 0, 16, 16);
        driveModeTabContent.addView(assistSubtitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Grid container (2 sütun)
        GridLayout assistGrid = new GridLayout(this);
        assistGrid.setColumnCount(2);
        assistGrid.setPadding(16, 0, 16, 16);
        assistGrid.setBackgroundColor(0xFF101922);
        
        // GridLayout için LayoutParams
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        gridParams.setMargins(0, 0, 0, 16);
        
        // Kart verileri: {Başlık, İkon emoji, Aktif değer, Pasif değer, Setting key, Log prefix}
        Object[][] assistCards = {
            // 1. Satır: ISS + Hız Limitleyici
            {"ISS (Start-Stop)", "🔄", 0, -1, "issSetting", "ISS"},
            {"Hız Limitleyici", "⚡", 2, -1, "spdLimitSetting", "Hız Limitleyici"},
            // 2. Satır: Şerit Takip + Şeritten Kaçınma (LDP)
            {"Şerit Takip Uyarısı", "🛡️", 2, -1, "ldwSetting", "LDW"},
            {"Şeritten Kaçınma (LDP)", "🛡️", 2, -1, "ldpSetting", "LDP"},
            // 3. Satır: Ön Çarpışma Uyarısı + Aktif Acil Fren
            {"Ön Çarpışma Uyarısı", "⚠️", 2, -1, "fcwSetting", "FCW"},
            {"Aktif Acil Fren", "🛑", 2, -1, "aebSetting", "AEB"}
        };
        
        // Kaydedilmiş ayarları yükle (kart sırası ile aynı olmalı)
        int[] savedValues = {
            prefs.getInt("issSetting", -1),       // index 0 → ISS
            prefs.getInt("spdLimitSetting", -1),  // index 1 → Hız Limitleyici
            prefs.getInt("ldwSetting", -1),       // index 2 → Şerit Takip Uyarısı
            prefs.getInt("ldpSetting", -1),       // index 3 → Şeritten Kaçınma (LDP)
            prefs.getInt("fcwSetting", -1),       // index 4 → Ön Çarpışma Uyarısı
            prefs.getInt("aebSetting", -1)        // index 5 → Aktif Acil Fren
        };
        
        // Kart referanslarını sakla (güncelleme için)
        final LinearLayout[] cardContainers = new LinearLayout[assistCards.length];
        final TextView[] iconViews = new TextView[assistCards.length];
        final TextView[] titleViews = new TextView[assistCards.length];
        final TextView[] statusViews = new TextView[assistCards.length];
        final TextView[] onChips = new TextView[assistCards.length];
        final FrameLayout[] cardFrames = new FrameLayout[assistCards.length];
        
        float density = getResources().getDisplayMetrics().density;
        
        // Her kart için oluştur
        for (int i = 0; i < assistCards.length; i++) {
            final int cardIndex = i;
            final String cardTitle = (String) assistCards[i][0];
            final String cardIcon = (String) assistCards[i][1];
            final int activeValue = (Integer) assistCards[i][2];
            final int passiveValue = (Integer) assistCards[i][3];
            final String settingKey = (String) assistCards[i][4];
            final String logPrefix = (String) assistCards[i][5];
            
            // Mevcut ayarı kontrol et
            final boolean[] isActiveRef = {(savedValues[i] == activeValue)};
            
            // FrameLayout (ON chip için)
            FrameLayout cardFrame = new FrameLayout(this);
            cardFrames[cardIndex] = cardFrame;
            
            // Kart container
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(android.view.Gravity.CENTER);
            card.setPadding(16, 20, 16, 20);
            card.setClickable(true);
            card.setFocusable(true);
            card.setMinimumHeight((int)(135 * density)); // 135dp
            cardContainers[cardIndex] = card;
            
            // Kart arka plan ve border
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            if (isActiveRef[0]) {
                // Aktif: Hafif mavi tint, 2dp mavi border
                bg.setColor(0xFF1E3A5F);
                bg.setCornerRadius(16 * density);
                bg.setStroke((int)(2 * density), 0xFF2196F3);
            } else {
                // Pasif: Koyu kart rengi, 1dp düşük kontrast border
                bg.setColor(0xFF1C2630);
                bg.setCornerRadius(16 * density);
                bg.setStroke((int)(1 * density), 0xFF2A3A47);
            }
            card.setBackground(bg);
            
            // İkon (üstte)
            TextView iconView = new TextView(this);
            iconView.setText(cardIcon);
            iconView.setTextSize(32);
            iconView.setGravity(android.view.Gravity.CENTER);
            iconView.setTextColor(isActiveRef[0] ? 0xFFFFFFFF : 0xB3FFFFFF);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            iconParams.setMargins(0, 0, 0, (int)(8 * density));
            card.addView(iconView, iconParams);
            iconViews[cardIndex] = iconView;
            
            // Başlık (ortada)
            TextView titleView = new TextView(this);
            titleView.setText(cardTitle);
            titleView.setTextSize(16);
            titleView.setGravity(android.view.Gravity.CENTER);
            titleView.setTextColor(0xFFFFFFFF);
            titleView.setTypeface(null, isActiveRef[0] ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            LinearLayout.LayoutParams titleViewParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            titleViewParams.setMargins(0, 0, 0, (int)(4 * density));
            card.addView(titleView, titleViewParams);
            titleViews[cardIndex] = titleView;
            
            // Durum metni (altta)
            TextView statusView = new TextView(this);
            if (isActiveRef[0]) {
                if (settingKey.equals("issSetting")) {
                    statusView.setText("ISS Kapalı");
                } else if (settingKey.equals("ldwSetting")) {
                    statusView.setText("LDW Kapalı");
                } else if (settingKey.equals("spdLimitSetting")) {
                    statusView.setText("Uyarı Kapalı");
                } else if (settingKey.equals("ldpSetting")) {
                    statusView.setText("LDP Kapalı");
                } else if (settingKey.equals("fcwSetting")) {
                    statusView.setText("FCW Kapalı");
                } else if (settingKey.equals("aebSetting")) {
                    statusView.setText("AEB Kapalı");
                }
            } else {
                statusView.setText("Ayarlanmadı");
            }
            statusView.setTextSize(12);
            statusView.setGravity(android.view.Gravity.CENTER);
            statusView.setTextColor(0xB3FFFFFF);
            card.addView(statusView);
            statusViews[cardIndex] = statusView;
            
            // Sağ üstte "ON" chip (sadece aktif için görünür)
            TextView onChip = new TextView(this);
            onChip.setText("ON");
            onChip.setTextSize(10);
            onChip.setTextColor(0xFF2196F3);
            onChip.setPadding((int)(6 * density), (int)(2 * density), (int)(6 * density), (int)(2 * density));
            android.graphics.drawable.GradientDrawable chipBg = new android.graphics.drawable.GradientDrawable();
            chipBg.setColor(0x1A2196F3);
            chipBg.setCornerRadius(4 * density);
            onChip.setBackground(chipBg);
            onChip.setVisibility(isActiveRef[0] ? android.view.View.VISIBLE : android.view.View.GONE);
            FrameLayout.LayoutParams chipParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            chipParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
            chipParams.setMargins(0, (int)(8 * density), (int)(8 * density), 0);
            cardFrame.addView(onChip, chipParams);
            onChips[cardIndex] = onChip;
            
            cardFrame.addView(card);
            
            // GridLayout için LayoutParams
            GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams();
            cardParams.width = 0;
            cardParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
            cardParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            cardParams.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            cardParams.setMargins((int)(8 * density), (int)(8 * density), (int)(8 * density), (int)(8 * density));
            
            assistGrid.addView(cardFrame, cardParams);
            
            // Tıklama listener
            card.setOnClickListener(v -> {
                boolean newActive = !isActiveRef[0];
                int newValue = newActive ? activeValue : passiveValue;
                
                // FCW ve AEB için uyarı göster
                if ((settingKey.equals("fcwSetting") || settingKey.equals("aebSetting")) && newActive) {
                    showSafetyWarningDialog(settingKey, newValue, savedValues, cardIndex, isActiveRef, cardContainers, iconViews, titleViews, statusViews, onChips, density);
                    return; // Dialog'dan sonra devam edecek
                }
                
                // Ayarı kaydet
                if (settingKey.equals("issSetting")) {
                    saveIssSetting(newValue);
                    savedValues[0] = newValue;
                } else if (settingKey.equals("spdLimitSetting")) {
                    saveSpdLimitSetting(newValue);
                    savedValues[1] = newValue;
                } else if (settingKey.equals("ldwSetting")) {
                    saveLdwSetting(newValue);
                    savedValues[2] = newValue;
                } else if (settingKey.equals("ldpSetting")) {
                    saveLdpSetting(newValue);
                    savedValues[3] = newValue;
                } else if (settingKey.equals("fcwSetting")) {
                    saveFcwSetting(newValue);
                    savedValues[4] = newValue;
                } else if (settingKey.equals("aebSetting")) {
                    saveAebSetting(newValue);
                    savedValues[5] = newValue;
                }
                
                // Log
                if (newActive) {
                   
                    if (settingKey.equals("issSetting")) {
                        log("ISS: Aktif (ISS Kapalı)");
                    } else if (settingKey.equals("spdLimitSetting")) {
                        log("Hız Limitleyici: Aktif (Uyarı Kapalı)");
                    } else if (settingKey.equals("ldwSetting")) {
                        log("LDW: Aktif (LDW Kapalı)");
                    } else if (settingKey.equals("ldpSetting")) {
                        log("LDP: Aktif (LDP Kapalı)");
                    } else if (settingKey.equals("fcwSetting")) {
                        log("FCW: Aktif (Ön Çarpışma Uyarısı Kapalı)");
                    } else if (settingKey.equals("aebSetting")) {
                        log("AEB: Aktif (Aktif Acil Fren Kapalı)");
                    }
                    
                    // PowerMode == 2 ise bir kerelik kapatma değeri gönder
                    try {
                        SharedPreferences powerPrefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
                        int currentPowerMode = powerPrefs.getInt("lastPowerMode", -1);
                        int[] pwrItems = CarInfoProxy.getInstance().getItemValues(
                            VDEventCarInfo.MODULE_READONLY_INFO,
                            ReadOnlyID.ID_SYSTEM_POWER_MODE);
                        if (pwrItems != null && pwrItems.length > 0) {
                            currentPowerMode = pwrItems[0];
                        }
                        log("Kapatma Modu PowerMode: " + currentPowerMode);
                        if (currentPowerMode == 2 && CarInfoProxy.getInstance().isServiceConnnected()) {
                            log("🔄 PowerMode=2, " + logPrefix + " için kapatma değeri gönderiliyor");
                            
                            if (settingKey.equals("issSetting")) {
                                CarInfoProxy.getInstance().sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_ISS, 0);
                                log("✅ ISS kapatma değeri gönderildi: 0 (Kapalı)");
                            } else if (settingKey.equals("ldwSetting")) {
                                CarInfoProxy.getInstance().sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_LDW, 2);
                                log("✅ LDW kapatma değeri gönderildi: 2 (Kapalı)");
                            } else if (settingKey.equals("ldpSetting")) {
                                int[] vals = new int[]{2};
                                CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_FCM_INHIBIT, vals);
                                log("✅ LDP kapatma değeri gönderildi: [2] (Kapalı)");
                            } else if (settingKey.equals("fcwSetting")) {
                                int[] vals = new int[]{2, 1};
                                CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, 36, vals);
                                log("✅ FCW kapatma değeri gönderildi: [2, 1] (Kapalı)");
                            } else if (settingKey.equals("aebSetting")) {
                                int[] vals = new int[]{2};
                                CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, 37, vals);
                                log("✅ AEB kapatma değeri gönderildi: [2] (Kapalı)");
                            } else if (settingKey.equals("spdLimitSetting")) {
                                int[] vals = new int[]{2, 2};
                                CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_SPD_LIMIT_WARN_SET, vals);
                                log("✅ Hız Limitleyici kapatma değeri gönderildi: [2, 2] (Kapalı)");
                            }
                        }
                    } catch (Exception e) {
                        log("❌ Kapatma değeri gönderme hatası: " + e.getMessage());
                    }
                } else {
                    log(logPrefix + ": Pasif (Hiçbiri)");
                    
                    // PowerMode == 2 ise bir kerelik ters değer gönder (açık yap)
                    try {
                        SharedPreferences powerPrefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
                        int currentPowerMode = powerPrefs.getInt("lastPowerMode", -1);
                        int[] pwrItems = CarInfoProxy.getInstance().getItemValues(
                            VDEventCarInfo.MODULE_READONLY_INFO,
                            ReadOnlyID.ID_SYSTEM_POWER_MODE);
                        if (pwrItems != null && pwrItems.length > 0) {
                            currentPowerMode = pwrItems[0];
                        }
                        log("🔄 PowerMode: " + currentPowerMode);
                        if (currentPowerMode == 2 && CarInfoProxy.getInstance().isServiceConnnected()) {
                            log("🔄 PowerMode=2, " + logPrefix + " için ters değer gönderiliyor (Açık)");
                            
                            if (settingKey.equals("issSetting")) {
                                CarInfoProxy.getInstance().sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_ISS, 1);
                                log("✅ ISS ters değer gönderildi: 1 (Açık)");
                            } else if (settingKey.equals("ldwSetting")) {
                                CarInfoProxy.getInstance().sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_LDW, 1);
                                log("✅ LDW ters değer gönderildi: 1 (Açık)");
                            } else if (settingKey.equals("ldpSetting")) {
                                int[] vals = new int[]{1};
                                CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_FCM_INHIBIT, vals);
                                log("✅ LDP ters değer gönderildi: [1] (Açık)");
                            } else if (settingKey.equals("fcwSetting")) {
                                int[] vals = new int[]{1, 1};
                                CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, 36, vals);
                                log("✅ FCW ters değer gönderildi: [1, 1] (Açık)");
                            } else if (settingKey.equals("aebSetting")) {
                                int[] vals = new int[]{1};
                                CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, 37, vals);
                                log("✅ AEB ters değer gönderildi: [1] (Açık)");
                            } else if (settingKey.equals("spdLimitSetting")) {
                                int[] vals = new int[]{1, 1};
                                CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_SPD_LIMIT_WARN_SET, vals);
                                log("✅ Hız Limitleyici ters değer gönderildi: [1, 1] (Açık)");
                            }
                        }
                    } catch (Exception e) {
                        log("❌ Ters değer gönderme hatası: " + e.getMessage());
                    }
                }
                
                // UI'ı güncelle
                isActiveRef[0] = newActive;
                android.graphics.drawable.GradientDrawable newBg = new android.graphics.drawable.GradientDrawable();
                if (isActiveRef[0]) {
                    newBg.setColor(0xFF1E3A5F);
                    newBg.setCornerRadius(16 * density);
                    newBg.setStroke((int)(2 * density), 0xFF2196F3);
                } else {
                    newBg.setColor(0xFF1C2630);
                    newBg.setCornerRadius(16 * density);
                    newBg.setStroke((int)(1 * density), 0xFF2A3A47);
                }
                cardContainers[cardIndex].setBackground(newBg);
                
                iconViews[cardIndex].setTextColor(isActiveRef[0] ? 0xFFFFFFFF : 0xB3FFFFFF);
                titleViews[cardIndex].setTypeface(null, isActiveRef[0] ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                
                if (isActiveRef[0]) {
                    if (settingKey.equals("issSetting")) {
                        statusViews[cardIndex].setText("ISS Kapalı");
                    } else if (settingKey.equals("spdLimitSetting")) {
                        statusViews[cardIndex].setText("Uyarı Kapalı");
                    } else if (settingKey.equals("ldwSetting")) {
                        statusViews[cardIndex].setText("LDW Kapalı");
                    } else if (settingKey.equals("ldpSetting")) {
                        statusViews[cardIndex].setText("LDP Kapalı");
                    } else if (settingKey.equals("fcwSetting")) {
                        statusViews[cardIndex].setText("FCW Kapalı");
                    } else if (settingKey.equals("aebSetting")) {
                        statusViews[cardIndex].setText("AEB Kapalı");
                    }
                } else {
                    statusViews[cardIndex].setText("Ayarlanmadı");
                }
                
                onChips[cardIndex].setVisibility(isActiveRef[0] ? android.view.View.VISIBLE : android.view.View.GONE);
            });
        }
        
        driveModeTabContent.addView(assistGrid, gridParams);


        // Wi-Fi Manager'ı başlat
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        // Wi-Fi tab içeriği (Premium OEM UI Tasarım - Yumuşak Renk Paleti)
        wifiTabContent = new LinearLayout(this);
        wifiTabContent.setOrientation(LinearLayout.VERTICAL);
        wifiTabContent.setPadding(0, 0, 0, 0);
        wifiTabContent.setBackgroundColor(0xFF0F1419); // Yumuşak tek ton arka plan
        
        // Eski butonlar için referans (artık kullanılmıyor ama kod uyumluluğu için)
        btnWifiToggle = new Button(this);
        btnScanWifi = new Button(this);
        
        // İnce durum satırı (collapsing - scroll ile kaybolabilir)
        wifiStatusLine = new TextView(this);
        wifiStatusLine.setText("Bağlı değil");
        wifiStatusLine.setTextSize(12);
        wifiStatusLine.setTextColor(0xFF9DABB9);
        wifiStatusLine.setPadding(16, 8, 16, 8);
        wifiStatusLine.setBackgroundColor(0xFF0A0F14);
        LinearLayout.LayoutParams statusLineParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wifiTabContent.addView(wifiStatusLine, statusLineParams);
        
        // Aç/Kapat ve Yenile butonları (responsive, içeride)
        LinearLayout controlButtonsRow = new LinearLayout(this);
        controlButtonsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlButtonsRow.setPadding(16, 12, 16, 12);
        controlButtonsRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        controlButtonsRow.setBackgroundColor(0xFF0F1419);
        
        // Aç/Kapat butonu
        btnWifiToggle = new Button(this);
        btnWifiToggle.setText("Wi-Fi Aç");
        btnWifiToggle.setTextSize(14);
        btnWifiToggle.setTextColor(0xFFFFFFFF);
        btnWifiToggle.setTypeface(null, android.graphics.Typeface.BOLD);
        // GradientDrawable ile yumuşak teal/mavi accent
        android.graphics.drawable.GradientDrawable toggleBg = new android.graphics.drawable.GradientDrawable();
        toggleBg.setColor(0xFF3DAEA8); // Teal accent
        toggleBg.setCornerRadius(8);
        btnWifiToggle.setBackground(toggleBg);
        btnWifiToggle.setPadding(24, 12, 24, 12);
        btnWifiToggle.setOnClickListener(v -> {
            toggleWifi();
        });
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        toggleParams.setMargins(0, 0, 8, 0);
        controlButtonsRow.addView(btnWifiToggle, toggleParams);
        
        // Yenile butonu
        btnScanWifi = new Button(this);
        btnScanWifi.setText("Yenile");
        btnScanWifi.setTextSize(14);
        btnScanWifi.setTextColor(0xFFFFFFFF);
        btnScanWifi.setTypeface(null, android.graphics.Typeface.BOLD);
        // GradientDrawable ile yumuşak teal/mavi accent
        android.graphics.drawable.GradientDrawable scanBg = new android.graphics.drawable.GradientDrawable();
        scanBg.setColor(0xFF1976D2); // Mavi accent
        scanBg.setCornerRadius(8);
        btnScanWifi.setBackground(scanBg);
        btnScanWifi.setPadding(24, 12, 24, 12);
        btnScanWifi.setOnClickListener(v -> {
            scanWifiNetworks();
        });
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        controlButtonsRow.addView(btnScanWifi, scanParams);
        
        wifiTabContent.addView(controlButtonsRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // Wi-Fi listesi container (ScrollView - collapsing hissi için)
        wifiListScrollView = new ScrollView(this);
        wifiListScrollView.setBackgroundColor(0xFF0F1419);
        wifiListScrollView.setPadding(16, 8, 16, 16);
        
        wifiListContainer = new LinearLayout(this);
        wifiListContainer.setOrientation(LinearLayout.VERTICAL);
        wifiListScrollView.addView(wifiListContainer);
        
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        wifiTabContent.addView(wifiListScrollView, listParams);
        
        // İlk durumu güncelle
        updateWifiStatus();

        // LOG tab içeriği (Modern Terminal Tasarım)
        logTabContent = new LinearLayout(this);
        logTabContent.setOrientation(LinearLayout.VERTICAL);
        logTabContent.setPadding(12, 12, 12, 12);
        logTabContent.setBackgroundColor(0xFF1E1E1E);
        logTabContent.setVisibility(android.view.View.GONE); // Başlangıçta gizli
        
        // Üst Kontrol Paneli
        LinearLayout logControlPanel = new LinearLayout(this);
        logControlPanel.setOrientation(LinearLayout.HORIZONTAL);
        logControlPanel.setPadding(0, 0, 0, 8);
        logControlPanel.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        // Başlık (sol)
        TextView logTitle = new TextView(this);
        logTitle.setText("Sistem Kayıtları");
        logTitle.setTextSize(20);
        logTitle.setTextColor(0xFFFFFFFF);
        logTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams logTitleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        logControlPanel.addView(logTitle, logTitleParams);
        
        // Kamera Test butonu (sağ)
        Button btnCameraTest = new Button(this);
        btnCameraTest.setText("📷 Kamera Test");
        btnCameraTest.setTextColor(0xFFFFFFFF);
        btnCameraTest.setTextSize(14);
        btnCameraTest.setBackgroundColor(0xFF3DAEA8);
        btnCameraTest.setPadding(16, 8, 16, 8);
        btnCameraTest.setOnClickListener(v -> {
            Intent intent = new Intent(this, CameraActivity.class);
            startActivity(intent);
        });
        LinearLayout.LayoutParams cameraTestParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cameraTestParams.setMargins(0, 0, 8, 0);
        logControlPanel.addView(btnCameraTest, cameraTestParams);
        
        // Hoşgeldin Ses Test butonu (sağ)
        Button btnAudioTest = new Button(this);
        btnAudioTest.setText("🔊 Hoşgeldin Ses Test");
        btnAudioTest.setTextColor(0xFFFFFFFF);
        btnAudioTest.setTextSize(14);
        btnAudioTest.setBackgroundColor(0xFF3DAEA8);
        btnAudioTest.setPadding(16, 8, 16, 8);
        btnAudioTest.setOnClickListener(v -> {
            Intent intent = new Intent(this, AudioTestActivity.class);
            startActivity(intent);
        });
        LinearLayout.LayoutParams audioTestParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        audioTestParams.setMargins(0, 0, 8, 0);
        logControlPanel.addView(btnAudioTest, audioTestParams);
        
        // Temizle icon butonu (sağ)
        Button btnClearLogs = new Button(this);
        btnClearLogs.setText("🗑");
        btnClearLogs.setTextColor(0xFFFFFFFF);
        btnClearLogs.setTextSize(20);
        btnClearLogs.setBackgroundColor(0xFF3D1F1F);
        btnClearLogs.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams clearLogsParams = new LinearLayout.LayoutParams(
                56, 56);
        logControlPanel.addView(btnClearLogs, clearLogsParams);
        
        logTabContent.addView(logControlPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // Terminal Container
        LinearLayout terminalContainer = new LinearLayout(this);
        terminalContainer.setOrientation(LinearLayout.VERTICAL);
        terminalContainer.setBackgroundColor(0xFF0B1116);
        terminalContainer.setPadding(0, 0, 0, 0);
        
        // Terminal Header (macOS style)
        LinearLayout terminalHeader = new LinearLayout(this);
        terminalHeader.setOrientation(LinearLayout.HORIZONTAL);
        terminalHeader.setBackgroundColor(0xFF161B22);
        terminalHeader.setPadding(16, 12, 16, 12);
        terminalHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        // macOS dots
        LinearLayout dotsLayout = new LinearLayout(this);
        dotsLayout.setOrientation(LinearLayout.HORIZONTAL);
        
        // Red dot
        TextView redDot = new TextView(this);
        redDot.setText("●");
        redDot.setTextColor(0xFFFF5F56);
        redDot.setTextSize(16);
        redDot.setPadding(0, 0, 8, 0);
        dotsLayout.addView(redDot);
        
        // Yellow dot
        TextView yellowDot = new TextView(this);
        yellowDot.setText("●");
        yellowDot.setTextColor(0xFFFFBD2E);
        yellowDot.setTextSize(16);
        yellowDot.setPadding(0, 0, 8, 0);
        dotsLayout.addView(yellowDot);
        
        // Green dot
        TextView greenDot = new TextView(this);
        greenDot.setText("●");
        greenDot.setTextColor(0xFF27C93F);
        greenDot.setTextSize(16);
        dotsLayout.addView(greenDot);
        
        terminalHeader.addView(dotsLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // File path
        TextView terminalPath = new TextView(this);
        terminalPath.setText("/var/log/syslog/vehicle_core.log");
        terminalPath.setTextColor(0xFF9DABB9);
        terminalPath.setTextSize(10);
        terminalPath.setTypeface(android.graphics.Typeface.MONOSPACE);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        pathParams.gravity = android.view.Gravity.END;
        terminalHeader.addView(terminalPath, pathParams);
        
        terminalContainer.addView(terminalHeader, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // Log ScrollView
        scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFF0B1116);
        scrollView.setPadding(12, 12, 12, 12);
        
        tvLogs = new TextView(this);
        tvLogs.setTextColor(0xFFD1D5DB);
        tvLogs.setTextSize(12);
        tvLogs.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvLogs.setPadding(8, 8, 8, 8);
        tvLogs.setLineSpacing(4, 1.0f);
        scrollView.addView(tvLogs);
        
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        terminalContainer.addView(scrollView, scrollParams);
        
        LinearLayout.LayoutParams terminalParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        terminalParams.setMargins(0, 8, 0, 0);
        logTabContent.addView(terminalContainer, terminalParams);
        
        // Buton click listeners
        btnClearLogs.setOnClickListener(v -> {
            logBuffer.setLength(0);
            tvLogs.setText("");
            log("Loglar temizlendi");
        });

        // Uygulamalar tab içeriği (Modern Tasarım)
        appsTabContent = new LinearLayout(this);
        appsTabContent.setOrientation(LinearLayout.VERTICAL);
        appsTabContent.setPadding(0, 0, 0, 0);
        appsTabContent.setBackgroundColor(0xFF101922);
        
        // Uygulama yönetimi butonları (topBar'a eklenecek)
        // Bu butonlar switchTab() metodunda topBarButtonsContainer'a eklenecek
        
        // Uygulamalar listesi container (Kartlar)
        appsListScrollView = new ScrollView(this);
        appsListScrollView.setBackgroundColor(0xFF101922);
        appsListScrollView.setPadding(16, 8, 16, 16);
        
        appsListContainer = new LinearLayout(this);
        appsListContainer.setOrientation(LinearLayout.VERTICAL);
        appsListScrollView.addView(appsListContainer);
        
        LinearLayout.LayoutParams appsListParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        appsTabContent.addView(appsListScrollView, appsListParams);
        
        // İlk tab'ı göster (Wi-Fi)
        switchTab(0);
        
        // Uygulamaları yükle
        loadAppsFromServer();

        btnLaunchOnCluster.setOnClickListener(v -> {
            launchSelectedAppOnCluster(0);
        });

        try {
            VDBus.getDefault().init(getApplicationContext());
            log("VDBus başlatıldı");
            
            // Harita kontrol tuşu ayarını kontrol et
            boolean mapControlKeyEnabled = prefs.getBoolean("mapControlKeyEnabled", true); // Varsayılan: Açık
            
            if (mapControlKeyEnabled) {
                //startKeyEventLogcatMonitor(); // Key event'ler için logcat
                startKeyEventVdbusListener(); // VDBus üzerinden key listener
            } else {
                log("Harita kontrol tuşu devre dışı bırakılmış, KeyEventLogcatMonitor başlatılmadı");
            }
            
        } catch (Exception e) {
            log("VDBus init hatası: " + e.getMessage());
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
    private static final int[] VDBUS_KEY_SUBSCRIBE_CODES = new int[]{10, 14, 26};
    private final Object vdbusKeyLock = new Object();
    private VDBus vdbusKeyBus;
    private VDEvent vdbusSmsKeyEvent;
    private boolean vdbusKeySubscribed = false;
    
    private MediaPlayer alertMediaPlayer = null;
    private final Object alertMediaPlayerLock = new Object();
    private final IVDBusNotify.Stub vdbusKeyNotify = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (event == null || event.getId() != VDEventSms.ID_SMS_KEY_EVENT) {
                return;
            }
            Bundle payload = event.getPayload();
            if (payload == null) {
                return;
            }
            int keyCode = payload.getInt(VDKey.TYPE, -1);
            int action = payload.getInt(VDKey.ACTION, -1);
            log("VDBus KeyEvent: keyCode=" + keyCode + " action=" + action);
            if(keyCode == 26 && action == 1){
                playSoftAlert();
            }
            if (keyCode == 26 && action == 4) {
                log("VDBus NAV key tespit edildi (keyCode=26, action=4)");
                runOnUiThread(() -> {
                    if (!isNavigationOpen) {
                        log("Navigasyon kapalı, açılıyor...");
                        openClusterDisplay();
                    }else  {
                        log("Navigasyon açık, kapatılıyor...");
                        closeClusterDisplay(false);
                        }
                });
            }
        }
    };
    private final VDBindListener vdbusKeyBindListener = new VDBindListener() {
        @Override
        public void onVDConnected(VDServiceDef.ServiceType serviceType) {
            if (serviceType == VDServiceDef.ServiceType.SMS) {
                log("VDBus key listener: SMS service bağlandı, subscribe ediliyor");
                subscribeToSmsKeyEvents();
            }
        }

        @Override
        public void onVDDisconnected(VDServiceDef.ServiceType serviceType) {
            if (serviceType == VDServiceDef.ServiceType.SMS) {
                log("VDBus key listener: SMS service koptu, yeniden bağlanacak");
                synchronized (vdbusKeyLock) {
                    vdbusKeySubscribed = false;
                    vdbusSmsKeyEvent = null;
                }
                if (vdbusKeyBus != null) {
                    try {
                        vdbusKeyBus.bindService(VDServiceDef.ServiceType.SMS);
                    } catch (Throwable t) {
                        log("VDBus key listener: bindService hata: " + t.getMessage());
                    }
                }
            }
        }
    };
    /**
     * VDBus üzerinden SMS key event'lerine subscribe olur.
     */
    private void startKeyEventVdbusListener() {
        try {
            if (vdbusKeyBus == null) {
                vdbusKeyBus = VDBus.getDefault();
            }
            if (vdbusKeyBus == null) {
                log("VDBus key listener: VDBus null döndü");
                return;
            }
            vdbusKeyBus.init(getApplicationContext());
        } catch (Exception e) {
            log("VDBus key listener init hatası: " + e.getMessage());
        }

        try {
            vdbusKeyBus.registerVDBindListener(vdbusKeyBindListener);
        } catch (Exception e) {
            log("VDBus key listener bindListener kayıt hatası: " + e.getMessage());
        }

        if (vdbusKeyBus != null && vdbusKeyBus.isServiceConnected(VDServiceDef.ServiceType.SMS)) {
            subscribeToSmsKeyEvents();
        } else if (vdbusKeyBus != null) {
            try {
                vdbusKeyBus.bindService(VDServiceDef.ServiceType.SMS);
            } catch (Throwable t) {
                log("VDBus key listener bindService hatası: " + t.getMessage());
            }
        }
    }

    private void subscribeToSmsKeyEvents() {
        if (vdbusKeyBus == null) {
            return;
        }
        synchronized (vdbusKeyLock) {
            if (vdbusKeySubscribed) {
                return;
            }
            try {
                Bundle bundle = new Bundle();
                bundle.putIntArray(VDKey.TYPE, VDBUS_KEY_SUBSCRIBE_CODES);
                vdbusSmsKeyEvent = new VDEvent(VDEventSms.ID_SMS_KEY_EVENT, bundle);
                vdbusKeyBus.subscribe(vdbusSmsKeyEvent, vdbusKeyNotify);
                vdbusKeySubscribed = true;
                log("VDBus key listener: SMS key event subscribe edildi");
            } catch (Throwable t) {
                log("VDBus key listener subscribe hatası: " + t.getMessage());
            }
        }
    }
    
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

    private void stopKeyEventVdbusListener() {
        synchronized (vdbusKeyLock) {
            if (!vdbusKeySubscribed || vdbusSmsKeyEvent == null || vdbusKeyBus == null) {
                vdbusKeySubscribed = false;
                vdbusSmsKeyEvent = null;
                return;
            }
            try {
                vdbusKeyBus.unsubscribe(vdbusSmsKeyEvent, vdbusKeyNotify);
                log("VDBus key listener: unsubscribe edildi");
            } catch (Throwable t) {
                log("VDBus key listener unsubscribe hatası: " + t.getMessage());
            } finally {
                vdbusKeySubscribed = false;
                vdbusSmsKeyEvent = null;
            }
        }

        if (vdbusKeyBus != null) {
            try {
                vdbusKeyBus.unregisterVDBindListener(vdbusKeyBindListener);
            } catch (Throwable t) {
                // ignore
            }
        }
    }
    
    /**
     * Engine RPM listener'ını başlatır (CarInfoHelper kullanarak)
     */
    private void openClusterDisplay() {
        try {
            int targetDisplay =  getClusterDisplayId();
            if(targetDisplay != 0){
                // Önce "Uygulama Hazırlanıyor" mesajını göster
                showPreparingMessageOnDisplay(targetDisplay);
                Runnable timeoutRunnable = () -> {
                    log("⚠️ İşlem zaman aşımına uğradı, mesaj gizleniyor.");
                    hidePreparingMessage();
                };
                handler.postDelayed(timeoutRunnable, 7000);
            }

            // Display 2'de açık uygulamayı bul ve display 0'a taşı (com.mapcontrol hariç)
            String appOnDisplay2 = getAppOnDisplay2();
            if (appOnDisplay2 != null && !appOnDisplay2.isEmpty() && !appOnDisplay2.equals("com.mapcontrol")) {
                log("Display 2'de açık uygulama bulundu: " + appOnDisplay2 + " - Display 0'a taşınıyor ve kapatılıyor");
                moveAppToDefaultDisplay(appOnDisplay2);
                // Uygulamayı kapatmak için home'a gönder
                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(homeIntent);
            } else if (appOnDisplay2 != null && appOnDisplay2.equals("com.mapcontrol")) {
                log("Display 2'de com.mapcontrol bulundu - taşıma işlemi atlandı");
            }

            // Wake up event gönder
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
            VDNaviDisplayArea payloadDA = new VDNaviDisplayArea();
            payloadDA.setNaviDisplayArea(10);
            payloadDA.setNaviDisplayAreaResult("true");
            VDEvent eventDA = VDNaviDisplayArea.createEvent(VDEventCarLan.NAVIGATION_DISPLAY_AREA, payloadDA);
            VDBus.getDefault().set(eventDA);

            VDNaviDisplayCluster payload = new VDNaviDisplayCluster();
            payload.setNaviFrontDeskStatus("true");
            payload.setDisplayCluster("true");
            payload.setPerspective(0);
            payload.setPerspectiveResult("false");
            payload.setRequestDisplayNaviArea("false");
            VDEvent event = VDNaviDisplayCluster.createEvent(VDEventCarLan.NAVIGATION_DISPLAY_TO_CLUSTER, payload);
            VDBus.getDefault().set(event);

            isNavigationOpen = true;
                log("Navigasyon paneli açıldı");

            // Eğer bir uygulama seçilmişse, cluster'da başlat (600ms gecikme ile)
            if (targetPackage != null && !targetPackage.trim().isEmpty()) {
                handler.postDelayed(() -> {
                        log("Seçilen uygulama cluster'da başlatılıyor: " + targetPackage);
                        launchSelectedAppOnCluster(null);
                        handler.postDelayed(() -> {
                            hidePreparingMessage();
                        }, 1000);
                }, 600);
            }
            }, 1000);
        } catch (Exception e) {
            log("Açma hatası: " + e.getMessage());
        }
    }

    private void closeClusterDisplay(boolean sendBackground) {
        try {
            // 1. VDBus event ile display area'yı kapat
            VDNaviDisplayArea payloadDA = new VDNaviDisplayArea();
            payloadDA.setNaviDisplayArea(0);
            payloadDA.setNaviDisplayAreaResult("false");
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
            
            isNavigationOpen = false;       
            log("Navigasyon paneli kapatıldı");

            // 2. Eğer bir uygulama seçilmişse, onu default display'e (0) taşı ve arka plana gönder
            if (targetPackage != null && !targetPackage.trim().isEmpty()) {
                moveAppToDefaultDisplay(targetPackage);
                if (sendBackground) {
                    // Uygulamayı arka plana gönder (öne gelmesin)
                    handler.postDelayed(() -> {
                        moveAppToBackground(targetPackage);
                    }, 500); // 500ms gecikme ile uygulamanın taşınmasını bekle
                }
            }
        } catch (Exception e) {
            log("Kapatma hatası: " + e.getMessage());
        }
    }
    
    /**
     * Uygulamayı arka plana gönderir (öne gelmesin)
     * moveAppToDefaultDisplay çağrıldıktan sonra kullanılır
     */
    private void moveAppToBackground(String packageName) {
        try {
            // Uygulama zaten default display'e taşındı
            // Home tuşu göndererek tüm uygulamaları arka plana gönder
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
            
            // Kısa bir gecikme sonra kendimizi tekrar öne getir
            handler.postDelayed(() -> {
                try {
                    Intent mapControlIntent = getPackageManager().getLaunchIntentForPackage("com.mapcontrol");
                    if (mapControlIntent != null) {
                        mapControlIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(mapControlIntent);
                        log("MapControl tekrar öne getirildi");
                    }
                } catch (Exception e) {
                    log("MapControl öne getirme hatası: " + e.getMessage());
                }
            }, 300);
            
            log("Uygulama arka plana gönderildi: " + packageName);
        } catch (Exception e) {
            log("moveAppToBackground hatası: " + e.getMessage());
        }
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
    
    /**
     * Focuslanan (aktif) uygulamanın package adını döndürür
     * Sistem uygulamaları ve priv-app uygulamaları hariç tutulur
     */
    private String getFocusedAppPackageName() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            
            // Android 5.0+ için getRunningAppProcesses kullan
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                if (processes != null) {
                    for (ActivityManager.RunningAppProcessInfo processInfo : processes) {
                        if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                            if (processInfo.pkgList != null && processInfo.pkgList.length > 0) {
                                String packageName = processInfo.pkgList[0];
                                // Sistem uygulaması veya priv-app kontrolü
                                if (!isSystemOrPrivApp(packageName)) {
                                    log("Focuslanan uygulama bulundu: " + packageName);
                                    return packageName;
                                } else {
                                    log("Focuslanan uygulama sistem/priv-app, atlandı: " + packageName);
                                }
                            }
                        }
                    }
                }
            }
            
            // Alternatif: dumpsys komutu kullan
            try {
                Process process = Runtime.getRuntime().exec("dumpsys activity activities | grep mResumedActivity");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("mResumedActivity")) {
                        // Satırdan package adını çıkar
                        int startIndex = line.indexOf("com.");
                        if (startIndex != -1) {
                            int endIndex = line.indexOf("/", startIndex);
                            if (endIndex == -1) {
                                endIndex = line.indexOf(" ", startIndex);
                            }
                            if (endIndex == -1) {
                                endIndex = line.length();
                            }
                            String packageName = line.substring(startIndex, endIndex).trim();
                            // Sistem uygulaması veya priv-app kontrolü
                            if (!isSystemOrPrivApp(packageName)) {
                                log("Focuslanan uygulama (dumpsys): " + packageName);
                                reader.close();
                                return packageName;
                            } else {
                                log("Focuslanan uygulama (dumpsys) sistem/priv-app, atlandı: " + packageName);
                            }
                        }
                    }
                }
                reader.close();
            } catch (Exception e) {
                log("dumpsys komutu hatası: " + e.getMessage());
            }
            
        } catch (Exception e) {
            log("getFocusedAppPackageName hatası: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Display 2'de açık uygulamanın package adını döndürür
     * Sistem uygulamaları ve priv-app uygulamaları hariç tutulur
     */
    private String getAppOnDisplay2() {
        try {
            // dumpsys komutu ile display 2'deki uygulamayı bul
            Process process = Runtime.getRuntime().exec("dumpsys activity activities | grep -A 5 'displayId=2'");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String lastPackage = null;
            while ((line = reader.readLine()) != null) {
                // Package adını bul
                if (line.contains("com.")) {
                    int startIndex = line.indexOf("com.");
                    if (startIndex != -1) {
                        int endIndex = line.indexOf("/", startIndex);
                        if (endIndex == -1) {
                            endIndex = line.indexOf(" ", startIndex);
                        }
                        if (endIndex == -1) {
                            endIndex = line.length();
                        }
                        String packageName = line.substring(startIndex, endIndex).trim();
                        // com.mapcontrol ve sistem/priv-app uygulamalarını atla
                        if (!packageName.equals("com.mapcontrol") && !isSystemOrPrivApp(packageName)) {
                            lastPackage = packageName;
                        } else if (isSystemOrPrivApp(packageName)) {
                            log("Display 2'de sistem/priv-app uygulaması bulundu, atlandı: " + packageName);
                        }
                    }
                }
            }
            reader.close();
            
            if (lastPackage != null) {
                log("Display 2'de uygulama bulundu: " + lastPackage);
                return lastPackage;
            }
        } catch (Exception e) {
            log("getAppOnDisplay2 hatası: " + e.getMessage());
        }
        return null;
    }

    /**
     * Seçilen uygulamayı cluster display'den default display'e (0) taşır
     * com.mapcontrol, sistem uygulamaları ve priv-app uygulamaları taşınmaz
     */
    private void moveAppToDefaultDisplay(String packageName) {
        try {
            // com.mapcontrol paketini taşıma
            if (packageName == null || packageName.equals("com.mapcontrol")) {
                log("com.mapcontrol paketi taşınmayacak: " + packageName);
                return;
            }
            
            // Sistem uygulaması veya priv-app kontrolü
            if (isSystemOrPrivApp(packageName)) {
                log("Sistem/priv-app uygulaması taşınmayacak: " + packageName);
                return;
            }
            
            PackageManager pm = getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packageName);
            
            if (intent == null) {
                log("Uygulama intent bulunamadı: " + packageName);
                return;
            }

            // Default display'de (0) başlat - öne gelmeden (arka planda çalışsın)
            ActivityOptions opts = ActivityOptions.makeBasic();
            opts.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
            // FLAG_ACTIVITY_REORDER_TO_FRONT kaldırıldı - uygulama öne gelmeyecek
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent, opts.toBundle());
            log("Uygulama default display'e taşındı (arka planda): " + packageName);
        } catch (Exception e) {
            log("moveAppToDefaultDisplay hatası: " + e.getMessage());
        }
    }

    /**
     * Tüm yüklü uygulamaları listele ve seçim yap
     */
    private void autoSelectTargetApp() {
        log("Yüklü uygulamalar listeleniyor...");

        try {
            PackageManager pm = getPackageManager();
            // Tüm paketleri al (flag olmadan, ClusterControl gibi)
            java.util.List<android.content.pm.PackageInfo> allPackages = pm.getInstalledPackages(0);
            log("Toplam paket sayısı: " + allPackages.size());

            java.util.List<java.util.Map.Entry<String, String>> appList = new java.util.ArrayList<>();
            java.util.Set<String> seenPackages = new java.util.HashSet<>();

            // Yöntem 1: getInstalledPackages ile tüm paketleri kontrol et
            for (android.content.pm.PackageInfo pkgInfo : allPackages) {
                String pkg = null;
                try {
                    pkg = pkgInfo.packageName;
                    if (seenPackages.contains(pkg)) continue;
                    seenPackages.add(pkg);

                    ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                    
                    // MapControl uygulamasını filtrele (kendi uygulamamızı gösterme)
                    if (pkg != null && pkg.equals("com.mapcontrol")) {
                        continue;
                    }
                    
                    // Sistem ve priv uygulamalarını filtrele (WebServerManager'daki gibi)
                    if (isSystemOrPrivApp(appInfo)) {
                        continue;
                    }
                    
                    // Launch intent kontrolü - eğer launch intent yoksa atla
                    Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
                    if (launchIntent == null) {
                        continue;
                    }

                    String appName = pm.getApplicationLabel(appInfo).toString();
                    if (appName == null || appName.trim().isEmpty()) {
                        appName = pkg; // İsim yoksa paket adını kullan
                    }

                    appList.add(new java.util.AbstractMap.SimpleEntry<>(appName, pkg));
                } catch (Exception e) {
                    // Bazı uygulamalar için bilgi alınamayabilir, atla
                    log("Paket bilgisi alınamadı: " + (pkg != null ? pkg : "null") + " - " + e.getMessage());
                    continue;
                }
            }

            // Yöntem 2: queryIntentActivities ile de kontrol et (ek güvenlik)
            try {
                Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                java.util.List<ResolveInfo> launcherApps = pm.queryIntentActivities(mainIntent, 0);
                log("Launcher intent'i olan uygulama sayısı: " + (launcherApps != null ? launcherApps.size() : 0));

                // queryIntentActivities'den gelenleri de ekle (eğer eksikse)
                if (launcherApps != null) {
                    for (ResolveInfo info : launcherApps) {
                        try {
                            String pkg = info.activityInfo.packageName;
                            if (!seenPackages.contains(pkg)) {
                                // MapControl uygulamasını filtrele (kendi uygulamamızı gösterme)
                                if (pkg != null && pkg.equals("com.mapcontrol")) {
                                    continue;
                                }
                                
                                ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                                
                                // Sistem ve priv uygulamalarını filtrele (WebServerManager'daki gibi)
                                if (isSystemOrPrivApp(appInfo)) {
                                    continue;
                                }
                                
                                String appName = pm.getApplicationLabel(appInfo).toString();
                                if (appName == null || appName.trim().isEmpty()) {
                                    appName = pkg;
                                }
                                appList.add(new java.util.AbstractMap.SimpleEntry<>(appName, pkg));
                                seenPackages.add(pkg);
                            }
                        } catch (Exception e) {
                            continue;
                        }
                    }
                }
            } catch (Exception e) {
                log("queryIntentActivities hatası: " + e.getMessage());
            }

            if (appList.isEmpty()) {
                log("Liste oluşturulamadı!");
                Toast.makeText(this, "Liste oluşturulamadı", Toast.LENGTH_SHORT).show();
                return;
            }

            appList.sort((a, b) -> a.getKey().compareToIgnoreCase(b.getKey()));

            java.util.List<String> appNames = new java.util.ArrayList<>();
            java.util.List<String> sortedPackages = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, String> entry : appList) {
                appNames.add(entry.getKey() + " (" + entry.getValue() + ")");
                sortedPackages.add(entry.getValue());
            }

            log("" + appNames.size() + " uygulama bulundu");

            String[] preferred = new String[] {
                    "ru.yandex.yandexnavi",
                    "ru.yandex.yandexmaps",
                    "com.google.android.apps.maps",
                    "com.waze",
                    "com.sygic.aura"
            };

            String foundPreferred = null;
            for (String pkg : preferred) {
                if (sortedPackages.contains(pkg)) {
                    foundPreferred = pkg;
                    break;
                }
            }

            final String finalFoundPreferred = foundPreferred;
            String[] items = appNames.toArray(new String[0]);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            String titleText = "📱 Yüklü Uygulamalar (" + items.length + ")";
            if (finalFoundPreferred != null) {
                titleText += "\n💡 Önerilen: " + finalFoundPreferred;
            }
            builder.setTitle(titleText);

            // Custom adapter ile liste elemanlarının rengini ayarla
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                    this, android.R.layout.simple_list_item_1, items) {
                @Override
                public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                    android.view.View view = super.getView(position, convertView, parent);
                    android.widget.TextView textView = (android.widget.TextView) view.findViewById(android.R.id.text1);
                    if (textView != null) {
                        textView.setTextColor(0xFF1976D2); // Mavi renk
                        textView.setTextSize(16);
                    }
                    return view;
                }
            };
            
            builder.setAdapter(adapter, (dialog, which) -> {
                String selectedPkg = sortedPackages.get(which);
                targetPackage = selectedPkg;
                saveTargetPackage(selectedPkg);
                log("Seçilen uygulama: " + targetPackage);
                updateTargetLabel();
                Toast.makeText(this, "Seçildi: " + selectedPkg, Toast.LENGTH_SHORT).show();
            });

            if (finalFoundPreferred != null) {
                builder.setPositiveButton("✨ Otomatik Seç", (dialog, which) -> {
                    targetPackage = finalFoundPreferred;
                    saveTargetPackage(finalFoundPreferred);
                    log("Otomatik seçim: " + targetPackage);
                    updateTargetLabel();
                    Toast.makeText(this, "✅ Otomatik seçildi: " + finalFoundPreferred, Toast.LENGTH_SHORT).show();
                });
            }

            builder.setNegativeButton("❌ İptal", null);
            builder.setNeutralButton("🧹 Temizle", (dialog, which) -> {
                targetPackage = "";
                saveTargetPackage("");
                updateTargetLabel();
                Toast.makeText(this, "✅ Hedef uygulama temizlendi", Toast.LENGTH_SHORT).show();
            });

            AlertDialog dialog = builder.create();
            dialog.show();
            
            // Buton renklerini ayarla
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF4CAF50);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(17);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTypeface(null, android.graphics.Typeface.BOLD);
            }
            if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFFF44336);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(17);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTypeface(null, android.graphics.Typeface.BOLD);
            }
            if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(0xFFFF9800);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextSize(17);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTypeface(null, android.graphics.Typeface.BOLD);
            }
        } catch (Exception e) {
            log("autoSelectTargetApp hatası: " + e.getMessage());
            Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Seçilen uygulamayı cluster display'de başlat
     */
    private void launchSelectedAppOnCluster(Integer displayId) {
        if (targetPackage == null || targetPackage.trim().isEmpty()) {
            Toast.makeText(this, "Önce bir uygulama seçin!", Toast.LENGTH_SHORT).show();
            log("Uygulama seçilmedi");
            return;
        }

        try {
            String pkg = targetPackage.trim();
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            
            if (intent == null) {
                Toast.makeText(this, "Uygulama bulunamadı: " + pkg, Toast.LENGTH_SHORT).show();
                log("Launch intent bulunamadı: " + pkg);
                return;
            }

            launchOnCluster(intent,displayId);
        } catch (Exception e) {
            log("launchSelectedAppOnCluster hatası: " + e.getMessage());
            Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Belirtilen Intent'i cluster display'de başlat
     */
    private void launchOnCluster(Intent intent,Integer displayId) {
        try {
            int targetDisplay = displayId != null ? displayId : getClusterDisplayId();
            try {
            ActivityOptions opts = ActivityOptions.makeBasic();
            opts.setLaunchDisplayId(targetDisplay);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent, opts.toBundle());
                log("Intent cluster'da başlatıldı (displayId=" + targetDisplay + ")");
        } catch (Exception e) {
                log("launchOnCluster hata: " + e.getMessage());
            Toast.makeText(this, "launchOnCluster hata: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            log("launchOnCluster hata: " + e.getMessage());
            Toast.makeText(this, "launchOnCluster hata: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * İkinci ekranda "Uygulama Hazırlanıyor" mesajını göster
     * İkinci display için Context oluşturup WindowManager'ı o Context ile alır
     */
    private View preparingMessageView = null;
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
            LinearLayout messageContainer = new LinearLayout(displayContext);
            messageContainer.setOrientation(LinearLayout.VERTICAL);
            messageContainer.setGravity(android.view.Gravity.CENTER);
            messageContainer.setBackgroundColor(0xE6000000); // Yarı şeffaf siyah arka plan
            messageContainer.setPadding(40, 40, 40, 40);
            
            TextView messageText = new TextView(displayContext);
            messageText.setText("Uygulama Hazırlanıyor...");
            messageText.setTextSize(24);
            messageText.setTextColor(0xFFFFFFFF);
            messageText.setTypeface(null, android.graphics.Typeface.BOLD);
            messageText.setGravity(android.view.Gravity.CENTER);
            
            messageContainer.addView(messageText, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            
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

    /**
     * Menü öğesi view oluştur
     */
    private LinearLayout createMenuItemView(String icon, String text, boolean isSelected) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setPadding(0, 24, 24, 24); // Sol padding: 0 (accent bar ve ikon alanı padding'i içerecek)
        itemLayout.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START); // Dikey merkez, yatay sol
        itemLayout.setClickable(true);
        itemLayout.setFocusable(true);
        
        // Aktif menü öğesi için koyu/mat mavi arka plan
        if (isSelected) {
            itemLayout.setBackgroundColor(0xFF1A4A6B); // Daha koyu/mat mavi (0xFF1976D2 yerine)
        } else {
            itemLayout.setBackgroundColor(0x00000000);
        }
        
        // Sol padding (24dp) - accent bar bu padding içinde olacak
        LinearLayout leftPaddingContainer = new LinearLayout(this);
        leftPaddingContainer.setOrientation(LinearLayout.HORIZONTAL);
        leftPaddingContainer.setPadding(24, 0, 0, 0); // Sol padding: 24dp
        leftPaddingContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        // Sol tarafta ince accent bar (aktifse görünür)
        if (isSelected) {
            android.view.View accentBar = new android.view.View(this);
            accentBar.setBackgroundColor(0xFF3DAEA8); // Accent rengi
            LinearLayout.LayoutParams accentBarParams = new LinearLayout.LayoutParams(
                    5, // 5dp genişlik (ince)
                    LinearLayout.LayoutParams.MATCH_PARENT);
            accentBarParams.setMargins(0, 8, 12, 8); // Üst ve alt boşluk, sağ margin: 12dp
            leftPaddingContainer.addView(accentBar, accentBarParams);
        }
        
        // İkon container (sabit genişlik: 48dp)
        LinearLayout iconContainer = new LinearLayout(this);
        iconContainer.setOrientation(LinearLayout.HORIZONTAL);
        iconContainer.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        LinearLayout.LayoutParams iconContainerParams = new LinearLayout.LayoutParams(
                48, // Sabit genişlik: 48dp
                LinearLayout.LayoutParams.WRAP_CONTENT);
        leftPaddingContainer.addView(iconContainer, iconContainerParams);
        
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(18); // 20 -> 18 (daha küçük)
        iconView.setTextColor(0xFFFFFFFF);
        iconView.setGravity(android.view.Gravity.START); // Sol hizalı
        iconContainer.addView(iconView);
        
        itemLayout.addView(leftPaddingContainer);
        
        // Metin alanı (ikon container'ın sağında başlar)
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(17); // 16 -> 17 (metinler daha baskın)
        textView.setTextColor(0xFFFFFFFF);
        textView.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL); // Sol hizalı
        // Aktifse bold değil, sadece biraz daha parlak (normal weight)
        textView.setTypeface(null, android.graphics.Typeface.NORMAL);
        if (isSelected) {
            textView.setTextColor(0xE6FFFFFF); // %90 opaklık (biraz daha parlak ama bold değil)
        }
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, // Geri kalan alanı kapla
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f);
        textParams.setMargins(12, 0, 0, 0); // İkon container'dan sonra 12dp boşluk
        itemLayout.addView(textView, textParams);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        itemLayout.setLayoutParams(params);
        
        return itemLayout;
    }
    
    /**
     * Sabit sol kenar çubuğu için menü öğesi oluştur (daha büyük ikonlar ve aralıklar - ergonomik)
     * Minimum 80x80px dokunma alanı
     */
    private LinearLayout createRailMenuItemView(String icon, String text, boolean isSelected) {
        float density = getResources().getDisplayMetrics().density;
        int minTouchSizePx = 80; // Minimum 80px
        int minTouchSizeDp = (int)(minTouchSizePx / density); // DP'ye çevir
        
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        // Minimum 80px dokunma alanı için padding ayarla
        itemLayout.setPadding(0, Math.max(32, minTouchSizeDp / 2), 24, Math.max(32, minTouchSizeDp / 2));
        itemLayout.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        itemLayout.setClickable(true);
        itemLayout.setFocusable(true);
        // Minimum yükseklik garantisi
        itemLayout.setMinimumHeight(minTouchSizePx);
        
        // Aktif menü öğesi için koyu/mat mavi arka plan
        if (isSelected) {
            itemLayout.setBackgroundColor(0xFF1A4A6B);
        } else {
            itemLayout.setBackgroundColor(0x00000000);
        }
        
        // Sol padding (24dp) - accent bar bu padding içinde olacak
        LinearLayout leftPaddingContainer = new LinearLayout(this);
        leftPaddingContainer.setOrientation(LinearLayout.HORIZONTAL);
        leftPaddingContainer.setPadding(24, 0, 0, 0);
        leftPaddingContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        // Sol tarafta ince accent bar (aktifse görünür)
        if (isSelected) {
            android.view.View accentBar = new android.view.View(this);
            accentBar.setBackgroundColor(0xFF3DAEA8);
            LinearLayout.LayoutParams accentBarParams = new LinearLayout.LayoutParams(
                    5,
                    LinearLayout.LayoutParams.MATCH_PARENT);
            accentBarParams.setMargins(0, 12, 16, 12); // Üst ve alt boşluk artırıldı: 8 -> 12
            leftPaddingContainer.addView(accentBar, accentBarParams);
        }
        
        // İkon container (sabit genişlik: 64dp - daha büyük)
        LinearLayout iconContainer = new LinearLayout(this);
        iconContainer.setOrientation(LinearLayout.HORIZONTAL);
        iconContainer.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        LinearLayout.LayoutParams iconContainerParams = new LinearLayout.LayoutParams(
                64, // Sabit genişlik: 64dp (48 -> 64, daha büyük)
                LinearLayout.LayoutParams.WRAP_CONTENT);
        leftPaddingContainer.addView(iconContainer, iconContainerParams);
        
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(28); // 18 -> 28 (çok daha büyük ikonlar)
        iconView.setTextColor(0xFFFFFFFF);
        iconView.setGravity(android.view.Gravity.START);
        iconContainer.addView(iconView);
        
        itemLayout.addView(leftPaddingContainer);
        
        // Metin alanı (ikon container'ın sağında başlar)
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(18); // 17 -> 18 (daha büyük metin)
        textView.setTextColor(0xFFFFFFFF);
        textView.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        textView.setTypeface(null, android.graphics.Typeface.NORMAL);
        if (isSelected) {
            textView.setTextColor(0xE6FFFFFF); // %90 opaklık
        }
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f);
        textParams.setMargins(16, 0, 0, 0); // İkon container'dan sonra boşluk: 12 -> 16
        itemLayout.addView(textView, textParams);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        itemLayout.setLayoutParams(params);
        
        return itemLayout;
    }
    
    /**
     * Menü seçimini güncelle
     */
    private void updateMenuSelection(LinearLayout selected, LinearLayout... others) {
        // Seçili olanı vurgula (koyu/mat mavi, ince accent bar, metin bold değil)
        selected.setBackgroundColor(0xFF1A4A6B); // Koyu/mat mavi
        
        // Accent bar'ı kontrol et (leftPaddingContainer içinde)
        if (selected.getChildCount() > 0 && selected.getChildAt(0) instanceof LinearLayout) {
            LinearLayout leftPaddingContainer = (LinearLayout) selected.getChildAt(0);
            if (leftPaddingContainer.getChildCount() > 0) {
                android.view.View firstChild = (android.view.View) leftPaddingContainer.getChildAt(0);
                if (firstChild.getLayoutParams() != null && 
                    firstChild.getLayoutParams().width == 5) {
                    // Accent bar zaten var, rengini güncelle
                    firstChild.setBackgroundColor(0xFF3DAEA8);
                } else {
                    // Accent bar yok, ekle
                    android.view.View accentBar = new android.view.View(this);
                    accentBar.setBackgroundColor(0xFF3DAEA8);
                    LinearLayout.LayoutParams accentBarParams = new LinearLayout.LayoutParams(
                            5, LinearLayout.LayoutParams.MATCH_PARENT);
                    accentBarParams.setMargins(0, 8, 12, 8);
                    leftPaddingContainer.addView(accentBar, 0, accentBarParams);
                }
            }
        }
        
        // Metin rengini güncelle (bold değil, sadece biraz daha parlak)
        TextView selectedText = (TextView) selected.getChildAt(selected.getChildCount() - 1);
        if (selectedText != null) {
            selectedText.setTypeface(null, android.graphics.Typeface.NORMAL);
            selectedText.setTextColor(0xE6FFFFFF); // %90 opaklık
        }
        
        // Diğerlerini normal yap
        for (LinearLayout other : others) {
            other.setBackgroundColor(0x00000000);
            
            // Accent bar'ı kaldır (eğer varsa)
            if (other.getChildCount() > 0 && other.getChildAt(0) instanceof LinearLayout) {
                LinearLayout otherLeftPaddingContainer = (LinearLayout) other.getChildAt(0);
                if (otherLeftPaddingContainer.getChildCount() > 0) {
                    android.view.View firstChild = (android.view.View) otherLeftPaddingContainer.getChildAt(0);
                    if (firstChild.getLayoutParams() != null && 
                        firstChild.getLayoutParams().width == 5) {
                        otherLeftPaddingContainer.removeViewAt(0);
                    }
                }
            }
            
            TextView otherText = (TextView) other.getChildAt(other.getChildCount() - 1);
            if (otherText != null) {
            otherText.setTypeface(null, android.graphics.Typeface.NORMAL);
                otherText.setTextColor(0xFFFFFFFF); // Normal beyaz
            }
        }
    }

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
            
            // Üst bar başlığını güncelle: ☰ Wi-Fi Yönetimi
            if (topBarTitle != null) {
                topBarTitle.setText("☰ Wi-Fi Yönetimi");
            }
            
            // Sağ üstte Wi-Fi ikon + renkli nokta
            if (topBarButtonsContainer != null) {
                // Wi-Fi ikon + durum noktası container
                LinearLayout wifiIconContainer = new LinearLayout(this);
                wifiIconContainer.setOrientation(LinearLayout.HORIZONTAL);
                wifiIconContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
                wifiIconContainer.setPadding(8, 0, 0, 0);
                
                // Wi-Fi ikon
                TextView wifiIcon = new TextView(this);
                wifiIcon.setText("📶");
                wifiIcon.setTextSize(20);
                wifiIcon.setTextColor(0xFFFFFFFF);
                wifiIconContainer.addView(wifiIcon);
                
                // Renkli nokta (durum göstergesi)
                wifiStatusIcon = new TextView(this);
                wifiStatusIcon.setText("●");
                wifiStatusIcon.setTextSize(12);
                wifiStatusIcon.setTextColor(0xFF9DABB9); // Varsayılan: gri (bağlı değil)
                wifiStatusIcon.setPadding(4, 0, 0, 0);
                wifiIconContainer.addView(wifiStatusIcon);
                
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                topBarButtonsContainer.addView(wifiIconContainer, iconParams);
            }
            
            // Wi-Fi tab'ına geçildiğinde durumu güncelle
            updateWifiStatus();
        } else if (tabIndex == 1) {
            // Dosya Yükle tab'ı aktif
            tabContentArea.addView(fileUploadScrollView);
            // Dosya Yükle tab'ında buton yok
        } else if (tabIndex == 2) {
            // Profil tab'ı aktif
            if (profileScrollView == null) {
                createProfileTab();
            }
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
            // Uygulama yönetimi butonlarını topBar'a ekle (minimal tasarım)
            if (topBarButtonsContainer != null) {
                // Yenile butonu (icon, minimal)
                btnRefreshApps = new Button(this);
                btnRefreshApps.setText("🔄");
                btnRefreshApps.setTextSize(20);
                btnRefreshApps.setTextColor(0xFFFFFFFF);
                btnRefreshApps.setBackgroundColor(0x00000000); // Transparent
                btnRefreshApps.setPadding(12, 12, 12, 12);
                LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                refreshParams.setMargins(0, 0, 4, 0);
                topBarButtonsContainer.addView(btnRefreshApps, refreshParams);
                btnRefreshApps.setOnClickListener(v -> {
                    if (isLocalMode) {
                        loadLocalApps();
                    } else {
                        loadAppsFromServer();
                    }
                });
                
                // Klasörü Aç butonu (icon, minimal)
                btnDownloadedFiles = new Button(this);
                btnDownloadedFiles.setText("📁");
                btnDownloadedFiles.setTextSize(20);
                btnDownloadedFiles.setTextColor(0xFFFFFFFF);
                btnDownloadedFiles.setBackgroundColor(0x00000000); // Transparent
                btnDownloadedFiles.setPadding(12, 12, 12, 12);
                LinearLayout.LayoutParams filesParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                filesParams.setMargins(0, 0, 4, 0);
                topBarButtonsContainer.addView(btnDownloadedFiles, filesParams);
                btnDownloadedFiles.setOnClickListener(v -> showDownloadedFiles());
                
                // Yerel/Sunucu Toggle butonu (en sağda)
                btnModeToggle = new Button(this);
                updateModeToggleButton();
                btnModeToggle.setTextSize(16);
                btnModeToggle.setTextColor(0xFFFFFFFF);
                btnModeToggle.setBackgroundColor(0x00000000); // Transparent
                btnModeToggle.setPadding(12, 12, 12, 12);
                btnModeToggle.setOnClickListener(v -> {
                    isLocalMode = !isLocalMode;
                    updateModeToggleButton();
                    // Mod değişince listeyi yenile
                    if (isLocalMode) {
                        loadLocalApps();
                    } else {
                        loadAppsFromServer();
                    }
                });
                LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                toggleParams.setMargins(0, 0, 4, 0);
                topBarButtonsContainer.addView(btnModeToggle, toggleParams);
                
                // İlk açılışta varsayılan modda listeyi yükle (sunucu modu)
                if (appsListContainer != null && appsListContainer.getChildCount() == 0) {
                    loadAppsFromServer();
                }
                
                // Overflow menü (⋮) - Tümünü Sil içinde
                Button overflowMenu = new Button(this);
                overflowMenu.setText("⋮");
                overflowMenu.setTextSize(20);
                overflowMenu.setTextColor(0xFFFFFFFF);
                overflowMenu.setBackgroundColor(0x00000000); // Transparent
                overflowMenu.setPadding(12, 12, 12, 12);
                overflowMenu.setOnClickListener(v -> {
                    PopupMenu popupMenu = new PopupMenu(MainActivity.this, overflowMenu);
                    popupMenu.getMenu().add(0, 1, 0, "Tümünü Sil");
                    popupMenu.setOnMenuItemClickListener(item -> {
                        if (item.getItemId() == 1) {
                            // Tümünü Sil - confirm dialog ile
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Tümünü Sil")
                                .setMessage("Tüm indirilen dosyaları silmek istediğinize emin misiniz?")
                                .setPositiveButton("Sil", (d, which) -> {
                                    performReset();
                                })
                                .setNegativeButton("İptal", null)
                                .create()
                                .show();
                            return true;
                        }
                        return false;
                    });
                    popupMenu.show();
                });
                LinearLayout.LayoutParams overflowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                topBarButtonsContainer.addView(overflowMenu, overflowParams);
            }
        } else if (tabIndex == 6) {
            // Hafıza Modu tab'ı aktif
            tabContentArea.addView(driveModeScrollView);
            // Hafıza Modu tab'ında buton yok
        } else if (tabIndex == 7) {
            // Ayarlar tab'ı aktif
            if (settingsScrollView == null) {
                createSettingsTab();
            }
            tabContentArea.addView(settingsScrollView);
            // Ayarlar tab'ında buton yok
        }
    }

    /**
     * Wi-Fi durumunu güncelle ve göster
     */
    private void updateWifiStatus() {
        if (wifiManager == null || btnWifiToggle == null) {
            return;
        }

        try {
            boolean isWifiEnabled = wifiManager.isWifiEnabled();
            
            if (isWifiEnabled) {
                btnWifiToggle.setText("Wi-Fi Kapat");
                // GradientDrawable ile yumuşak teal/mavi accent
                android.graphics.drawable.GradientDrawable toggleBg = new android.graphics.drawable.GradientDrawable();
                toggleBg.setColor(0xFF3DAEA8); // Teal accent
                toggleBg.setCornerRadius(8);
                btnWifiToggle.setBackground(toggleBg);
            } else {
                btnWifiToggle.setText("Wi-Fi Aç");
                // GradientDrawable ile yumuşak teal/mavi accent
                android.graphics.drawable.GradientDrawable toggleBg = new android.graphics.drawable.GradientDrawable();
                toggleBg.setColor(0xFF1976D2); // Mavi accent
                toggleBg.setCornerRadius(8);
                btnWifiToggle.setBackground(toggleBg);
            }
            
            log("Wi-Fi durumu güncellendi: " + (isWifiEnabled ? "AÇIK" : "KAPALI"));
            
            // Wi-Fi açıksa otomatik tarama yap
            if (isWifiEnabled) {
                handler.postDelayed(() -> {
                    scanWifiNetworks();
                }, 500); // Kısa bir gecikme ile tarama başlat
            }
        } catch (Exception e) {
            log("Wi-Fi durumu kontrol hatası: " + e.getMessage());
            if (btnWifiToggle != null) {
                btnWifiToggle.setText("Hata");
                android.graphics.drawable.GradientDrawable errorBg = new android.graphics.drawable.GradientDrawable();
                errorBg.setColor(0xFFFF9800);
                errorBg.setCornerRadius(8);
                btnWifiToggle.setBackground(errorBg);
            }
        }
    }

    /**
     * Wi-Fi ağlarını tara
     */
    private void scanWifiNetworks() {
        if (wifiManager == null) {
            log("WifiManager bulunamadı");
            Toast.makeText(this, "Wi-Fi yöneticisi bulunamadı", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            log("Wi-Fi kapalı, önce Wi-Fi'yi açın");
            Toast.makeText(this, "Wi-Fi kapalı, önce Wi-Fi'yi açın", Toast.LENGTH_SHORT).show();
            // Wi-Fi kapalıysa listeyi sıfırla
            if (wifiListContainer != null) {
                wifiListContainer.removeAllViews();
                TextView emptyText = new TextView(this);
                emptyText.setText("Wi-Fi kapalı");
                emptyText.setTextColor(0xFFFF9800);
                emptyText.setTextSize(14);
                emptyText.setPadding(8, 8, 8, 8);
                wifiListContainer.addView(emptyText);
            }
            return;
        }

        try {
            // Önce listeyi yenile (mevcut sonuçları göster)
            displayWifiNetworks();
            
            log("Wi-Fi ağları taranıyor...");
            btnScanWifi.setEnabled(false);
            btnScanWifi.setText("🔍");
            
            boolean scanStarted = wifiManager.startScan();
            if (scanStarted) {
                // Tarama sonuçlarını almak için kısa bir gecikme
                handler.postDelayed(() -> {
                    displayWifiNetworks();
                    btnScanWifi.setEnabled(true);
                    btnScanWifi.setText("Yenile");
                }, 2000);
            } else {
                log("Wi-Fi taraması başlatılamadı");
                //Toast.makeText(this, "Wi-Fi taraması başlatılamadı", Toast.LENGTH_SHORT).show();
                btnScanWifi.setEnabled(true);
                btnScanWifi.setText("Yenile");
            }
        } catch (Exception e) {
            log("Wi-Fi tarama hatası: " + e.getMessage());
            Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            btnScanWifi.setEnabled(true);
            btnScanWifi.setText("🔍 Wi-Fi Ağlarını Tara");
        }
    }

    /**
     * Wi-Fi ağlarını listele ve göster
     */
    private void displayWifiNetworks() {
        if (wifiManager == null || wifiListContainer == null) {
            return;
        }

        try {
            List<ScanResult> scanResults = wifiManager.getScanResults();
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String connectedSSID = wifiInfo != null ? wifiInfo.getSSID().replace("\"", "") : null;

            wifiListContainer.removeAllViews();

            if (scanResults == null || scanResults.isEmpty()) {
                LinearLayout emptyCard = new LinearLayout(this);
                emptyCard.setOrientation(LinearLayout.VERTICAL);
                emptyCard.setGravity(android.view.Gravity.CENTER);
                emptyCard.setPadding(32, 48, 32, 48);
                
                TextView noNetworks = new TextView(this);
                noNetworks.setText("📡\n\nHiçbir Wi-Fi ağı bulunamadı\n\nLütfen tarama yapın");
                noNetworks.setTextColor(0xFF6B7280);
                noNetworks.setTextSize(15);
                noNetworks.setGravity(android.view.Gravity.CENTER);
                emptyCard.addView(noNetworks);
                
                wifiListContainer.addView(emptyCard);
                log("Wi-Fi ağı bulunamadı");
                return;
            }

            // Bağlı ağı en üste al
            for (ScanResult result : scanResults) {
                if (result.SSID != null && !result.SSID.isEmpty() && result.SSID.equals(connectedSSID)) {
                    addWifiNetworkItem(result, true);
                }
            }

            // Diğer ağları ekle
            for (ScanResult result : scanResults) {
                if (result.SSID != null && !result.SSID.isEmpty() && !result.SSID.equals(connectedSSID)) {
                    addWifiNetworkItem(result, false);
                }
            }

            log("" + scanResults.size() + " Wi-Fi ağı bulundu");
            //Toast.makeText(this, scanResults.size() + " ağ bulundu", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            log("Wi-Fi listesi hatası: " + e.getMessage());
            Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Wi-Fi ağ öğesini listeye ekle
     */
    private void addWifiNetworkItem(ScanResult result, boolean isConnected) {
        // Hero Card Tasarımı - Modern Araç UI
        LinearLayout wifiCard = new LinearLayout(this);
        wifiCard.setOrientation(LinearLayout.HORIZONTAL);
        wifiCard.setPadding(20, 20, 20, 20); // 16-20dp padding
        wifiCard.setGravity(android.view.Gravity.CENTER_VERTICAL);
        wifiCard.setClickable(true);
        wifiCard.setFocusable(true);
        
        // GradientDrawable ile yumuşak card arka planı (arka plandan %6-8 daha açık)
        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(0xFF1A2330); // Arka plandan %6-8 daha açık (0xFF0F1419 -> 0xFF1A2330)
        cardBg.setCornerRadius(16); // 16dp radius
        wifiCard.setBackground(cardBg);
        
        // Card height: 120-140dp (minimum height)
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 12);
        wifiCard.setMinimumHeight((int)(130 * getResources().getDisplayMetrics().density)); // ~130dp
        
        // Sol: Büyük Wi-Fi ikon (32-40dp)
        LinearLayout iconContainer = new LinearLayout(this);
        iconContainer.setOrientation(LinearLayout.VERTICAL);
        iconContainer.setGravity(android.view.Gravity.CENTER);
        iconContainer.setPadding(0, 0, 0, 0);
        
        TextView iconText = new TextView(this);
        iconText.setText("📶");
        iconText.setTextSize(36); // 32-40dp için ~36sp
        iconText.setTextColor(isConnected ? 0xFF3DAEA8 : 0xFF9DABB9); // Teal accent veya gri
        iconContainer.addView(iconText);
        
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        iconParams.setMargins(0, 0, 20, 0);
        wifiCard.addView(iconContainer, iconParams);
        
        // Orta kısım (SSID ve detaylar)
        LinearLayout infoContainer = new LinearLayout(this);
        infoContainer.setOrientation(LinearLayout.VERTICAL);
        infoContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        // SSID (18sp, Medium)
        TextView ssidText = new TextView(this);
        String ssid = result.SSID != null ? result.SSID : "(Gizli Ağ)";
        ssidText.setText(ssid);
        ssidText.setTextColor(0xFFFFFFFF);
        ssidText.setTextSize(18);
        ssidText.setTypeface(null, android.graphics.Typeface.NORMAL); // Medium weight
        infoContainer.addView(ssidText);
        
        // Alt satır: "Bağlı" • "Açık" / IP / Güçlü sinyal
        LinearLayout detailRow = new LinearLayout(this);
        detailRow.setOrientation(LinearLayout.HORIZONTAL);
        detailRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        detailRow.setPadding(0, 6, 0, 0);
        
        int level = result.level;
        String security = getSecurityType(result);
        String signalQuality = "";
        if (level > -50) signalQuality = "Güçlü sinyal";
        else if (level > -70) signalQuality = "İyi sinyal";
        else if (level > -85) signalQuality = "Orta sinyal";
        else signalQuality = "Zayıf sinyal";
        
        TextView detailText = new TextView(this);
        if (isConnected) {
            // Bağlı ise: "Bağlı" • "Açık" / IP
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String ipAddress = "";
            if (wifiInfo != null) {
                int ip = wifiInfo.getIpAddress();
                if (ip != 0) {
                    ipAddress = String.format("%d.%d.%d.%d",
                            (ip & 0xff),
                            (ip >> 8 & 0xff),
                            (ip >> 16 & 0xff),
                            (ip >> 24 & 0xff));
                }
            }
            String detailStr = "Bağlı";
            if (security.contains("Açık")) {
                detailStr += " • Açık";
            }
            if (!ipAddress.isEmpty()) {
                detailStr += " • " + ipAddress;
            } else {
                detailStr += " • " + signalQuality;
            }
            detailText.setText(detailStr);
            detailText.setTextColor(0xFF3DAEA8); // Teal accent
        } else {
            // Bağlı değil: Security • Sinyal
            detailText.setText(security + " • " + signalQuality);
            detailText.setTextColor(0xFF9DABB9); // Gri
        }
        detailText.setTextSize(13);
        detailRow.addView(detailText);
        
        infoContainer.addView(detailRow);
        
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        wifiCard.addView(infoContainer, infoParams);
        
        // Sağ: ✔︎ check icon veya küçük "ON" chip
        if (isConnected) {
            // Check icon
            TextView checkIcon = new TextView(this);
            checkIcon.setText("✔︎");
            checkIcon.setTextSize(20);
            checkIcon.setTextColor(0xFF27C93F); // Yeşil sadece connected için küçük kullanım
            checkIcon.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                    (int)(40 * getResources().getDisplayMetrics().density),
                    (int)(40 * getResources().getDisplayMetrics().density));
            checkParams.setMargins(8, 0, 0, 0);
            wifiCard.addView(checkIcon, checkParams);
        } else {
            // Küçük "ON" chip (opsiyonel - bağlan butonu yerine)
            // Boş bırakıyoruz, tıklanabilir kart olarak çalışacak
        }
        
        // Tıklama işlemleri
        wifiCard.setOnClickListener(v -> {
            if (isConnected) {
                disconnectFromWifi();
            } else {
                connectToWifi(result);
            }
        });
        
        wifiListContainer.addView(wifiCard, cardParams);
    }

    /**
     * Wi-Fi güvenlik tipini al
     */
    private String getSecurityType(ScanResult result) {
        String capabilities = result.capabilities;
        if (capabilities == null) return "Açık";
        
        if (capabilities.contains("WPA3")) {
            return "WPA3";
        } else if (capabilities.contains("WPA2")) {
            return "WPA2";
        } else if (capabilities.contains("WPA")) {
            return "WPA";
        } else if (capabilities.contains("WEP")) {
            return "WEP";
        } else {
            return "Açık";
        }
    }

    /**
     * Wi-Fi ağına bağlan
     */
    private void connectToWifi(ScanResult result) {
        if (wifiManager == null) {
            log("WifiManager bulunamadı");
            return;
        }

        String ssid = result.SSID;
        if (ssid == null || ssid.isEmpty()) {
            log("SSID boş");
            return;
        }

        String capabilities = result.capabilities;
        boolean isSecure = capabilities != null && 
                          (capabilities.contains("WPA") || capabilities.contains("WEP"));

        if (isSecure) {
            // Şifreli ağ için şifre dialog'u göster
            showPasswordDialog(result);
        } else {
            // Açık ağ için direkt bağlan
            connectToOpenNetwork(result);
        }
    }

    /**
     * Şifre dialog'u göster
     */
    private void showPasswordDialog(ScanResult result) {
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(40, 30, 40, 30);
        dialogLayout.setBackgroundColor(0xFF2C2C2C);
        
        TextView titleView = new TextView(this);
        titleView.setText("🔒 Wi-Fi Şifresi");
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTextSize(22);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setPadding(0, 0, 0, 20);
        dialogLayout.addView(titleView);
        
        TextView networkName = new TextView(this);
        networkName.setText("📡 " + result.SSID);
        networkName.setTextColor(0xFFFFA726);
        networkName.setTextSize(18);
        networkName.setTypeface(null, android.graphics.Typeface.BOLD);
        networkName.setPadding(0, 0, 0, 20);
        dialogLayout.addView(networkName);
        
        EditText passwordInput = new EditText(this);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setHint("🔑 Şifre giriniz");
        passwordInput.setTextColor(0xFFFFFFFF);
        passwordInput.setHintTextColor(0xFF808080);
        passwordInput.setTextSize(17);
        passwordInput.setPadding(40, 30, 40, 30);
        passwordInput.setBackgroundColor(0xFF1E1E1E);
        dialogLayout.addView(passwordInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogLayout)
                .setPositiveButton("✅ Bağlan", (d, which) -> {
                    String password = passwordInput.getText().toString();
                    if (password.isEmpty()) {
                        Toast.makeText(this, "⚠️ Şifre boş olamaz", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    connectToSecureNetwork(result, password);
                })
                .setNegativeButton("❌ İptal", null)
                .create();
        
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
        
        // Buton renklerini ayarla
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF4CAF50);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(17);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTypeface(null, android.graphics.Typeface.BOLD);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFFF44336);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(17);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTypeface(null, android.graphics.Typeface.BOLD);
    }

    /**
     * Açık ağa bağlan
     */
    private void connectToOpenNetwork(ScanResult result) {
        try {
            WifiConfiguration config = new WifiConfiguration();
            config.SSID = "\"" + result.SSID + "\"";
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            config.allowedAuthAlgorithms.clear();
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);

            int networkId = wifiManager.addNetwork(config);
            if (networkId == -1) {
                // Ağ zaten ekli olabilir, mevcut ağı bul
                List<WifiConfiguration> existingConfigs = wifiManager.getConfiguredNetworks();
                if (existingConfigs != null) {
                    for (WifiConfiguration existingConfig : existingConfigs) {
                        if (existingConfig.SSID != null && existingConfig.SSID.equals("\"" + result.SSID + "\"")) {
                            networkId = existingConfig.networkId;
                            break;
                        }
                    }
                }
            }

            if (networkId != -1) {
                boolean enabled = wifiManager.enableNetwork(networkId, true);
                if (enabled) {
                    log("Açık ağa bağlanılıyor: " + result.SSID);
                    Toast.makeText(this, "Bağlanılıyor: " + result.SSID, Toast.LENGTH_SHORT).show();
                    // Bağlantı durumunu kontrol et
                    handler.postDelayed(() -> {
                        scanWifiNetworks();
                    }, 2000);
                } else {
                    log("Ağ etkinleştirilemedi: " + result.SSID);
                    Toast.makeText(this, "Bağlantı hatası", Toast.LENGTH_SHORT).show();
                }
            } else {
                log("Ağ eklenemedi: " + result.SSID);
                Toast.makeText(this, "Ağ eklenemedi", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            log("Açık ağ bağlantı hatası: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Şifreli ağa bağlan
     */
    private void connectToSecureNetwork(ScanResult result, String password) {
        try {
            String capabilities = result.capabilities;
            WifiConfiguration config = new WifiConfiguration();
            config.SSID = "\"" + result.SSID + "\"";

            if (capabilities.contains("WPA3")) {
                // WPA3
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.SAE);
                config.preSharedKey = "\"" + password + "\"";
            } else if (capabilities.contains("WPA2")) {
                // WPA2
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                config.preSharedKey = "\"" + password + "\"";
            } else if (capabilities.contains("WPA")) {
                // WPA
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                config.preSharedKey = "\"" + password + "\"";
            } else if (capabilities.contains("WEP")) {
                // WEP
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.NONE);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
                if (password.length() == 10 || password.length() == 26 || password.length() == 58) {
                    config.wepKeys[0] = password;
                } else {
                    config.wepKeys[0] = "\"" + password + "\"";
                }
                config.wepTxKeyIndex = 0;
            }

            int networkId = wifiManager.addNetwork(config);
            if (networkId == -1) {
                // Ağ zaten ekli olabilir, mevcut ağı bul ve güncelle
                List<WifiConfiguration> existingConfigs = wifiManager.getConfiguredNetworks();
                if (existingConfigs != null) {
                    for (WifiConfiguration existingConfig : existingConfigs) {
                        if (existingConfig.SSID != null && existingConfig.SSID.equals("\"" + result.SSID + "\"")) {
                            networkId = existingConfig.networkId;
                            // Şifreyi güncelle
                            if (capabilities.contains("WEP")) {
                                if (password.length() == 10 || password.length() == 26 || password.length() == 58) {
                                    existingConfig.wepKeys[0] = password;
                                } else {
                                    existingConfig.wepKeys[0] = "\"" + password + "\"";
                                }
                            } else {
                                existingConfig.preSharedKey = "\"" + password + "\"";
                            }
                            wifiManager.updateNetwork(existingConfig);
                            break;
                        }
                    }
                }
            }

            if (networkId != -1) {
                boolean enabled = wifiManager.enableNetwork(networkId, true);
                if (enabled) {
                    log("Şifreli ağa bağlanılıyor: " + result.SSID);
                    Toast.makeText(this, "Bağlanılıyor: " + result.SSID, Toast.LENGTH_SHORT).show();
                    // Bağlantı durumunu kontrol et
                    handler.postDelayed(() -> {
                        scanWifiNetworks();
                    }, 2000);
                } else {
                    log("Ağ etkinleştirilemedi: " + result.SSID);
                    Toast.makeText(this, "Bağlantı hatası", Toast.LENGTH_SHORT).show();
                }
            } else {
                log("Ağ eklenemedi: " + result.SSID);
                Toast.makeText(this, "Ağ eklenemedi", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            log("Şifreli ağ bağlantı hatası: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Mevcut Wi-Fi bağlantısını kes
     */
    private void disconnectFromWifi() {
        if (wifiManager == null) {
            log("WifiManager bulunamadı");
            return;
        }

        try {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getNetworkId() != -1) {
                String ssid = wifiInfo.getSSID().replace("\"", "");
                boolean disconnected = wifiManager.disconnect();
                if (disconnected) {
                    log("Bağlantı kesildi: " + ssid);
                    Toast.makeText(this, "Bağlantı kesildi: " + ssid, Toast.LENGTH_SHORT).show();
                    // Listeyi güncelle
                    handler.postDelayed(() -> {
                        scanWifiNetworks();
                    }, 1000);
                } else {
                    log("Bağlantı kesilemedi");
                    Toast.makeText(this, "Bağlantı kesilemedi", Toast.LENGTH_SHORT).show();
                }
            } else {
                log("Aktif bağlantı yok");
                Toast.makeText(this, "Aktif bağlantı yok", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            log("Bağlantı kesme hatası: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Wi-Fi'yi aç/kapat (wifi.apk gibi sadece setWifiEnabled kullan)
     */
    private void toggleWifi() {
        if (wifiManager == null) {
            log("WifiManager bulunamadı");
            return;
        }

        try {
            boolean currentState = wifiManager.isWifiEnabled();
            boolean newState = !currentState;
            
            log("Mevcut durum: " + (currentState ? "AÇIK" : "KAPALI"));
            log("Hedef durum: " + (newState ? "AÇIK" : "KAPALI"));
            log("UID: " + android.os.Process.myUid());
            
            // wifi.apk gibi sadece setWifiEnabled kullan
            boolean result = wifiManager.setWifiEnabled(newState);
            log("setWifiEnabled(" + newState + ") sonucu: " + result);
            
            // Durumu güncelle (biraz gecikme ile, çünkü Wi-Fi açılması zaman alabilir)
            handler.postDelayed(() -> {
                updateWifiStatus();
                
                // Wi-Fi açıksa listeyi yenile, kapalıysa sıfırla
                if (newState) {
                    // Wi-Fi açıldı, listeyi yenile
                    scanWifiNetworks();
                } else {
                    // Wi-Fi kapandı, listeyi sıfırla
                    if (wifiListContainer != null) {
                        wifiListContainer.removeAllViews();
                        TextView emptyText = new TextView(this);
                        emptyText.setText("Wi-Fi kapalı");
                        emptyText.setTextColor(0xFFFF9800);
                        emptyText.setTextSize(14);
                        emptyText.setPadding(8, 8, 8, 8);
                        wifiListContainer.addView(emptyText);
                    }
                }
            }, 1000);
            
        } catch (Exception e) {
            log("Wi-Fi toggle hatası: " + e.getMessage());
            e.printStackTrace();
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

    /**
     * QR kod oluştur ve ImageView'da göster
     */
    private void generateQRCode(String url) {
        new Thread(() -> {
            try {
                QRCodeWriter qrCodeWriter = new QRCodeWriter();
                java.util.Map<EncodeHintType, Object> hints = new java.util.HashMap<>();
                hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
                hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
                hints.put(EncodeHintType.MARGIN, 1);
                
                int size = 512;
                BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, size, size, hints);
                
                Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
                for (int x = 0; x < size; x++) {
                    for (int y = 0; y < size; y++) {
                        bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                    }
                }
                
                handler.post(() -> {
                    if (qrCodeImageView != null) {
                        qrCodeImageView.setImageBitmap(bitmap);
                        qrCodeImageView.setVisibility(android.view.View.VISIBLE);
                    }
                });
            } catch (WriterException e) {
                handler.post(() -> {
                    log("QR kod oluşturma hatası: " + e.getMessage());
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Floating Back Button'ı temizle
        if (floatingBackButtonManager != null) {
            floatingBackButtonManager.hide();
        }
        // WebServerManager'ı durdur
        if (webServerManager != null) {
            webServerManager.stopServer();
        }
        // KeyEvent logcat thread'ini temizle
        keyEventLogcatRunning = false;
        if (keyEventLogcatThread != null) {
            keyEventLogcatThread.interrupt();
        }
        stopKeyEventVdbusListener();
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
        // Log receiver'ı temizle
        if (logReceiver != null) {
            try {
                unregisterReceiver(logReceiver);
            } catch (Exception e) {
                // Ignore
            }
            logReceiver = null;
        }
    }

    /**
     * MapControlService'den gelen log mesajlarını dinle
     */
    private void registerLogReceiver() {
        if (logReceiver != null) {
            return;
        }
        
        logReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.mapcontrol.LOG_MESSAGE".equals(intent.getAction())) {
                    String message = intent.getStringExtra("log_message");
                    if (message != null) {
                        log(message);
                    }
                }
            }
        };
        
        android.content.IntentFilter filter = new android.content.IntentFilter("com.mapcontrol.LOG_MESSAGE");
        registerReceiver(logReceiver, filter);
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

    /**
     * Toggle butonunun görünümünü güncelle
     */
    private void updateModeToggleButton() {
        if (btnModeToggle == null) {
            return;
        }
        if (isLocalMode) {
            btnModeToggle.setText("📱 Yerel");
        } else {
            btnModeToggle.setText("🌐 Sunucu");
        }
    }

    /**
     * Yerel kurulu uygulamaları yükle (com.mapcontrol hariç, sadece user 0)
     */
    private void loadLocalApps() {
        if (appsListContainer == null) {
            return;
        }

        new Thread(() -> {
            try {
                handler.post(() -> {
                    appsListContainer.removeAllViews();
                    TextView loadingText = new TextView(this);
                    loadingText.setText("Yükleniyor...");
                    loadingText.setTextColor(0xFFFF9800);
                    loadingText.setTextSize(14);
                    loadingText.setPadding(8, 8, 8, 8);
                    appsListContainer.addView(loadingText);
                });

                PackageManager pm = getPackageManager();
                java.util.List<android.content.pm.PackageInfo> allPackages = pm.getInstalledPackages(0);
                java.util.List<android.content.pm.PackageInfo> user0Apps = new java.util.ArrayList<>();

                // Sadece user 0 uygulamalarını filtrele (com.mapcontrol hariç)
                for (android.content.pm.PackageInfo pkgInfo : allPackages) {
                    try {
                        String packageName = pkgInfo.packageName;
                        
                        // com.mapcontrol'ü atla
                        if (packageName.equals("com.mapcontrol")) {
                            continue;
                        }

                        ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                        
                        // Sistem ve priv uygulamalarını filtrele (WebServerManager'daki gibi)
                        if (isSystemOrPrivApp(appInfo)) {
                            continue;
                        }
                        
                        // User 0 kontrolü: uid / 100000 == 0 (user 0)
                        // Android'de user 0 uygulamaları uid 10000-19999 arasındadır (genellikle)
                        // Daha güvenli kontrol: uid < 100000 (user 0) veya uid / 100000 == 0
                        int userId = appInfo.uid / 100000;
                        if (userId == 0) {
                            // Launch intent kontrolü - eğer launch intent yoksa atla
                            Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
                            if (launchIntent != null) {
                                user0Apps.add(pkgInfo);
                            }
                        }
                    } catch (Exception e) {
                        // Paket bilgisi alınamazsa atla
                        continue;
                    }
                }

                final java.util.List<android.content.pm.PackageInfo> finalUser0Apps = user0Apps;
                handler.post(() -> {
                    appsListContainer.removeAllViews();
                    displayLocalAppsList(finalUser0Apps);
                });

                log("" + finalUser0Apps.size() + " yerel uygulama yüklendi");
            } catch (Exception e) {
                handler.post(() -> {
                    appsListContainer.removeAllViews();
                    TextView errorText = new TextView(this);
                    errorText.setText("Hata: " + e.getMessage());
                    errorText.setTextColor(0xFFFF0000);
                    errorText.setTextSize(14);
                    errorText.setPadding(8, 8, 8, 8);
                    appsListContainer.addView(errorText);
                });
                log("Yerel uygulama listesi yükleme hatası: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Yerel kurulu uygulamaları göster
     */
    private void displayLocalAppsList(java.util.List<android.content.pm.PackageInfo> packages) {
        if (appsListContainer == null) {
            return;
        }

        try {
            PackageManager pm = getPackageManager();

            for (android.content.pm.PackageInfo pkgInfo : packages) {
                String packageName = pkgInfo.packageName;
                String currentVersion = pkgInfo.versionName != null ? pkgInfo.versionName : "0";
                
                // Uygulama adını al
                String displayName = packageName;
                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                    CharSequence label = pm.getApplicationLabel(appInfo);
                    if (label != null) {
                        displayName = label.toString();
                    }
                } catch (Exception e) {
                    // Label alınamazsa packageName kullan
                }

                // Lambda için final kopyalar
                final String finalPackageName = packageName;
                final String finalDisplayName = displayName;
                final String finalCurrentVersion = currentVersion;

                // Hero Card Tasarımı - OEM UI (displayAppsList ile aynı)
                LinearLayout appCard = new LinearLayout(this);
                appCard.setOrientation(LinearLayout.HORIZONTAL);
                appCard.setPadding(20, 20, 20, 20);
                appCard.setGravity(android.view.Gravity.CENTER_VERTICAL);
                appCard.setClickable(true);
                appCard.setFocusable(true);
                
                android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
                cardBg.setColor(0xFF1A2330);
                cardBg.setCornerRadius(16);
                appCard.setBackground(cardBg);
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    appCard.setElevation(2f);
                }
                
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                cardParams.setMargins(0, 0, 0, 12);
                appCard.setMinimumHeight((int)(120 * getResources().getDisplayMetrics().density));
                
                // Sol: Uygulama ikonu
                android.widget.ImageView appIcon = new android.widget.ImageView(this);
                try {
                    android.graphics.drawable.Drawable icon = pm.getApplicationIcon(finalPackageName);
                    appIcon.setImageDrawable(icon);
                } catch (Exception e) {
                    appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
                }
                appIcon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                        (int)(44 * getResources().getDisplayMetrics().density),
                        (int)(44 * getResources().getDisplayMetrics().density));
                iconParams.setMargins(0, 0, 16, 0);
                appCard.addView(appIcon, iconParams);
                
                // Orta kısım (uygulama bilgisi)
                LinearLayout infoContainer = new LinearLayout(this);
                infoContainer.setOrientation(LinearLayout.VERTICAL);
                infoContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
                
                TextView nameText = new TextView(this);
                nameText.setText(finalDisplayName);
                nameText.setTextColor(0xFFFFFFFF);
                nameText.setTextSize(17);
                nameText.setTypeface(null, android.graphics.Typeface.NORMAL);
                infoContainer.addView(nameText);
                
                TextView statusText = new TextView(this);
                statusText.setText("Kurulu • v" + finalCurrentVersion);
                statusText.setTextColor(0xFF9DABB9);
                statusText.setTextSize(13);
                LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                statusParams.setMargins(0, 4, 0, 0);
                infoContainer.addView(statusText, statusParams);
                
                LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                appCard.addView(infoContainer, infoParams);
                
                // Sağ: Ana aksiyon butonu + KALDIR butonu
                LinearLayout rightContainer = new LinearLayout(this);
                rightContainer.setOrientation(LinearLayout.HORIZONTAL);
                rightContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
                
                // AÇ butonu (mavi)
                Button actionButton = new Button(this);
                actionButton.setText("AÇ");
                actionButton.setTextSize(14);
                actionButton.setTypeface(null, android.graphics.Typeface.BOLD);
                actionButton.setPadding(24, 12, 24, 12);
                
                android.graphics.drawable.GradientDrawable buttonBg = new android.graphics.drawable.GradientDrawable();
                buttonBg.setColor(0xFF1976D2); // Mavi
                buttonBg.setCornerRadius(8);
                actionButton.setTextColor(0xFFFFFFFF);
                actionButton.setBackground(buttonBg);
                actionButton.setOnClickListener(v -> {
                    try {
                        Intent launchIntent = pm.getLaunchIntentForPackage(finalPackageName);
                        if (launchIntent != null) {
                            startActivity(launchIntent);
                            log(finalDisplayName + " açıldı");
                        } else {
                            Toast.makeText(this, "Uygulama açılamadı", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        log("Uygulama açma hatası: " + e.getMessage());
                        Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
                
                LinearLayout.LayoutParams actionButtonParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rightContainer.addView(actionButton, actionButtonParams);
                
                // KALDIR butonu
                Button removeButton = new Button(this);
                removeButton.setText("KALDIR");
                removeButton.setTextSize(14);
                removeButton.setTypeface(null, android.graphics.Typeface.BOLD);
                removeButton.setTextColor(0xFFFF5555);
                removeButton.setBackgroundColor(0x00000000);
                removeButton.setPadding(18, 12, 18, 12);
                removeButton.setOnClickListener(v -> uninstallApp(finalPackageName, finalDisplayName));
                LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                removeParams.setMargins(12, 0, 0, 0);
                rightContainer.addView(removeButton, removeParams);
                
                LinearLayout.LayoutParams rightContainerParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                appCard.addView(rightContainer, rightContainerParams);

                appsListContainer.addView(appCard, cardParams);
            }

            if (packages.isEmpty()) {
                LinearLayout emptyCard = new LinearLayout(this);
                emptyCard.setOrientation(LinearLayout.VERTICAL);
                emptyCard.setGravity(android.view.Gravity.CENTER);
                emptyCard.setPadding(32, 48, 32, 48);
                
                TextView emptyText = new TextView(this);
                emptyText.setText("📦\n\nYerel uygulama bulunamadı");
                emptyText.setTextColor(0xFF6B7280);
                emptyText.setTextSize(15);
                emptyText.setGravity(android.view.Gravity.CENTER);
                emptyCard.addView(emptyText);
                
                appsListContainer.addView(emptyCard);
            }
        } catch (Exception e) {
            log("Yerel uygulama listesi gösterim hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sunucudan uygulama listesini yükle
     */
    private void loadAppsFromServer() {
        if (appsListContainer == null) {
            return;
        }

        new Thread(() -> {
            try {
                handler.post(() -> {
                    appsListContainer.removeAllViews();
                    TextView loadingText = new TextView(this);
                    loadingText.setText("Yükleniyor...");
                    loadingText.setTextColor(0xFFFF9800);
                    loadingText.setTextSize(14);
                    loadingText.setPadding(8, 8, 8, 8);
                    appsListContainer.addView(loadingText);
                });

                URL url = new URL("https://vnoisy.dev/apk/list.json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    inputStream.close();

                    JSONObject jsonObject = new JSONObject(response.toString());
                    JSONArray listArray = jsonObject.getJSONArray("list");

                    handler.post(() -> {
                        appsListContainer.removeAllViews();
                        displayAppsList(listArray);
                    });

                    log("" + listArray.length() + " uygulama yüklendi");
                } else {
                    handler.post(() -> {
                        appsListContainer.removeAllViews();
                        TextView errorText = new TextView(this);
                        errorText.setText("Hata: " + responseCode);
                        errorText.setTextColor(0xFFFF0000);
                        errorText.setTextSize(14);
                        errorText.setPadding(8, 8, 8, 8);
                        appsListContainer.addView(errorText);
                    });
                    log("HTTP hatası: " + responseCode);
                }
                connection.disconnect();
            } catch (Exception e) {
                handler.post(() -> {
                    appsListContainer.removeAllViews();
                    TextView errorText = new TextView(this);
                    errorText.setText("Hata: " + e.getMessage());
                    errorText.setTextColor(0xFFFF0000);
                    errorText.setTextSize(14);
                    errorText.setPadding(8, 8, 8, 8);
                    appsListContainer.addView(errorText);
                });
                log("Uygulama listesi yükleme hatası: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Uygulama listesini göster
     */
    private void displayAppsList(JSONArray listArray) {
        if (appsListContainer == null) {
            return;
        }

        try {
            PackageManager pm = getPackageManager();

            for (int i = 0; i < listArray.length(); i++) {
                JSONObject appObj = listArray.getJSONObject(i);
                String packageName = appObj.getString("packageName");
                String displayName = appObj.getString("displayName");
                String downloadUrl = appObj.getString("downloadUrl");
                String version = appObj.getString("version");
                String currentVersion = "0";

                // Uygulama yüklü mü kontrol et
                boolean isInstalled = false;
                try {
                    PackageInfo info = pm.getPackageInfo(packageName, 0);
                    currentVersion = info.versionName;
                    log("currentVersion: " + currentVersion + " packageName: " + packageName);
                    isInstalled = true;
                } catch (PackageManager.NameNotFoundException e) {
                    isInstalled = false;
                }

                // İndirilmiş dosya var mı kontrol et
                File downloadDir = new File(getCacheDir(), "downloads");
                File downloadedFile = new File(downloadDir, packageName + ".apk");
                boolean isDownloaded = downloadedFile.exists() && downloadedFile.isFile();

                // Hero Card Tasarımı - OEM UI
                LinearLayout appCard = new LinearLayout(this);
                appCard.setOrientation(LinearLayout.HORIZONTAL);
                appCard.setPadding(20, 20, 20, 20); // 16-20dp padding
                appCard.setGravity(android.view.Gravity.CENTER_VERTICAL);
                appCard.setClickable(true);
                appCard.setFocusable(true);

                // GradientDrawable ile yumuşak card arka planı (arka plandan %6-8 daha açık)
                android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
                cardBg.setColor(0xFF1A2330); // Arka plandan %6-8 daha açık
                cardBg.setCornerRadius(16); // 14-16dp radius
                appCard.setBackground(cardBg);
                
                // Elevation (API 21+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    appCard.setElevation(2f); // Hafif elevation
                }
                
                // Card height: ~120dp
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                cardParams.setMargins(0, 0, 0, 12); // Kartlar arası boşluk: 12dp
                appCard.setMinimumHeight((int)(120 * getResources().getDisplayMetrics().density)); // ~120dp
                
                // Sol: Uygulama ikonu (40-48dp)
                android.widget.ImageView appIcon = new android.widget.ImageView(this);
                try {
                    if (isInstalled) {
                        // Gerçek uygulama ikonu
                        android.graphics.drawable.Drawable icon = pm.getApplicationIcon(packageName);
                        appIcon.setImageDrawable(icon);
                    } else {
                        // Varsayılan ikon
                        appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
                    }
                } catch (Exception e) {
                    appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
                }
                appIcon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                        (int)(44 * getResources().getDisplayMetrics().density), // 40-48dp
                        (int)(44 * getResources().getDisplayMetrics().density));
                iconParams.setMargins(0, 0, 16, 0);
                appCard.addView(appIcon, iconParams);
                
                // Orta kısım (uygulama bilgisi)
                LinearLayout infoContainer = new LinearLayout(this);
                infoContainer.setOrientation(LinearLayout.VERTICAL);
                infoContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
                
                // Güncelleme kontrolü (final for lambda)
                final boolean hasUpdate = isInstalled && !currentVersion.equals(version);
                final boolean isLocked = packageName.equals("com.mapcontrol");
                final boolean finalIsInstalled = isInstalled;
                
                // Uygulama adı (16-18sp, Medium)
                TextView nameText = new TextView(this);
                nameText.setText(displayName);
                nameText.setTextColor(0xFFFFFFFF);
                nameText.setTextSize(17); // 16-18sp
                nameText.setTypeface(null, android.graphics.Typeface.NORMAL); // Medium weight
                infoContainer.addView(nameText);
                
                // Alt satır: "Kurulu • v1.6.5" veya "Kurulu değil"
                TextView statusText = new TextView(this);
                if (isInstalled) {
                    statusText.setText("Kurulu • v" + currentVersion);
                    statusText.setTextColor(0xFF9DABB9); // Gri
                } else {
                    statusText.setText("Kurulu değil");
                    statusText.setTextColor(0xFF6B7280); // Daha koyu gri
                }
                statusText.setTextSize(13);
                LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                statusParams.setMargins(0, 4, 0, 0);
                infoContainer.addView(statusText, statusParams);
                
                LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                appCard.addView(infoContainer, infoParams);

                // Sağ: Ana aksiyon butonu + ⋮ menü
                LinearLayout rightContainer = new LinearLayout(this);
                rightContainer.setOrientation(LinearLayout.HORIZONTAL);
                rightContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
                
                // Ana aksiyon butonu
                Button actionButton = new Button(this);
                actionButton.setTextSize(14);
                actionButton.setTypeface(null, android.graphics.Typeface.BOLD);
                actionButton.setPadding(24, 12, 24, 12);
                
                android.graphics.drawable.GradientDrawable buttonBg = new android.graphics.drawable.GradientDrawable();
                buttonBg.setCornerRadius(8);
                
                if (hasUpdate) {
                    // Güncelleme varsa → GÜNCELLE (yeşil)
                    actionButton.setText("GÜNCELLE");
                    buttonBg.setColor(0xFF27C93F); // Yeşil
                    actionButton.setTextColor(0xFFFFFFFF);
                    actionButton.setBackground(buttonBg);
                    actionButton.setOnClickListener(v -> {
                        log(displayName + " güncelleniyor...");
                        // URL'yi butona tag olarak ekle (hata durumunda kullanılacak)
                        actionButton.setTag(downloadUrl);
                        downloadAndInstallApp(packageName, displayName, downloadUrl, actionButton);
                    });
                } else if (isInstalled && !isLocked) {
                    // Kurulu → AÇ (mavi)
                    actionButton.setText("AÇ");
                    buttonBg.setColor(0xFF1976D2); // Mavi
                    actionButton.setTextColor(0xFFFFFFFF);
                    actionButton.setBackground(buttonBg);
                    actionButton.setOnClickListener(v -> {
                        try {
                            Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
                            if (launchIntent != null) {
                                startActivity(launchIntent);
                                log(displayName + " açıldı");
                } else {
                                Toast.makeText(this, "Uygulama açılamadı", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            log("Uygulama açma hatası: " + e.getMessage());
                            Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                } else if (isLocked) {
                    // Kilitli → KİLİTLİ (disabled, gri)
                    actionButton.setText("KİLİTLİ");
                    buttonBg.setColor(0xFF6B7280); // Gri
                    actionButton.setTextColor(0xFF9DABB9);
                    actionButton.setBackground(buttonBg);
                    actionButton.setEnabled(false);
                } else if (isDownloaded) {
                    // İndirilmiş dosya varsa → KUR (yeşil)
                    actionButton.setText("KUR");
                    buttonBg.setColor(0xFF27C93F); // Yeşil
                    actionButton.setTextColor(0xFFFFFFFF);
                    actionButton.setBackground(buttonBg);
                    actionButton.setOnClickListener(v -> {
                        log("" + displayName + " kuruluyor (indirilmiş dosyadan)...");
                        installApkFile(downloadedFile);
                    });
                } else {
                    // Kurulu değil → KUR (yeşil)
                    actionButton.setText("KUR");
                    buttonBg.setColor(0xFF27C93F); // Yeşil
                    actionButton.setTextColor(0xFFFFFFFF);
                    actionButton.setBackground(buttonBg);
                    actionButton.setOnClickListener(v -> {
                        // URL'yi butona tag olarak ekle (hata durumunda kullanılacak)
                        actionButton.setTag(downloadUrl);
                        downloadAndInstallApp(packageName, displayName, downloadUrl, actionButton);
                    });
                }
                
                LinearLayout.LayoutParams actionButtonParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rightContainer.addView(actionButton, actionButtonParams);
                
                // Kaldır butonu (menü yerine tek aksiyon)
                if (finalIsInstalled && !isLocked) {
                    Button removeButton = new Button(this);
                    removeButton.setText("KALDIR");
                    removeButton.setTextSize(14);
                    removeButton.setTypeface(null, android.graphics.Typeface.BOLD);
                    removeButton.setTextColor(0xFFFF5555); // Kırmızı (destructive)
                    removeButton.setBackgroundColor(0x00000000); // Transparent
                    removeButton.setPadding(18, 12, 18, 12);
                    removeButton.setOnClickListener(v -> uninstallApp(packageName, displayName));
                    LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    removeParams.setMargins(12, 0, 0, 0);
                    rightContainer.addView(removeButton, removeParams);
                }
                
                LinearLayout.LayoutParams rightContainerParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                appCard.addView(rightContainer, rightContainerParams);

                appsListContainer.addView(appCard, cardParams);
            }

            if (listArray.length() == 0) {
                LinearLayout emptyCard = new LinearLayout(this);
                emptyCard.setOrientation(LinearLayout.VERTICAL);
                emptyCard.setGravity(android.view.Gravity.CENTER);
                emptyCard.setPadding(32, 48, 32, 48);
                
                TextView emptyText = new TextView(this);
                emptyText.setText("📦\n\nHenüz uygulama bulunamadı");
                emptyText.setTextColor(0xFF6B7280);
                emptyText.setTextSize(15);
                emptyText.setGravity(android.view.Gravity.CENTER);
                emptyCard.addView(emptyText);
                
                appsListContainer.addView(emptyCard);
            }
        } catch (Exception e) {
            log("Uygulama listesi gösterim hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Uygulamayı sil (WebServer'dan gelen istek için)
     */
    private void deleteApp(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            String displayName = pm.getApplicationLabel(appInfo).toString();
            
            // Sistem uygulaması kontrolü
            if (isSystemOrPrivApp(packageName)) {
                log("Sistem uygulaması silinemez: " + packageName);
                return;
            }
            
            // com.mapcontrol silinemez
            if ("com.mapcontrol".equals(packageName)) {
                log("Bu uygulama silinemez: " + packageName);
                return;
            }
            
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(android.net.Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            log("Uygulama silme başlatıldı: " + displayName + " (" + packageName + ")");
        } catch (Exception e) {
            log("Uygulama silme hatası: " + e.getMessage());
        }
    }

    /**
     * Uygulamayı aç (WebServer'dan gelen istek için)
     * Arka plandayken de çalışması için getApplicationContext() kullanılır
     */
    private void launchApp(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                // Arka plandayken de açılması için gerekli flag'ler
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                                     Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                                     Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED |
                                     Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                     Intent.FLAG_ACTIVITY_SINGLE_TOP);
                
                // Arka plandayken de çalışması için getApplicationContext() kullan
                // MainActivity arka plandayken startActivity() çalışmayabilir
                getApplicationContext().startActivity(launchIntent);
                log("Uygulama açıldı: " + packageName);
            } else {
                log("Uygulama açılamadı (launch intent bulunamadı): " + packageName);
            }
        } catch (Exception e) {
            log("Uygulama açma hatası: " + e.getMessage());
            // Hata durumunda tekrar dene (bazı durumlarda ilk deneme başarısız olabilir)
            try {
                PackageManager pm = getPackageManager();
                Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getApplicationContext().startActivity(launchIntent);
                    log("Uygulama açıldı (ikinci deneme): " + packageName);
                }
            } catch (Exception e2) {
                log("Uygulama açma hatası (ikinci deneme): " + e2.getMessage());
            }
        }
    }

    /**
     * Uygulamayı kaldır
     */
    private void uninstallApp(String packageName, String displayName) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("🗑️ Uygulamayı Kaldır")
                .setMessage("⚠️ " + displayName + " uygulamasını kaldırmak istediğinize emin misiniz?")
                .setPositiveButton("✅ Evet", (d, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_DELETE);
                        intent.setData(android.net.Uri.parse("package:" + packageName));
                        // Bazı headunit / OEM sistemlerde bile güvenli olsun diye NEW_TASK ile başlat
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        log("" + displayName + " kaldırılıyor...");
                        // Listeyi yenile (mod'a göre)
                        handler.postDelayed(() -> {
                            if (isLocalMode) {
                                loadLocalApps();
                            } else {
                            loadAppsFromServer();
                            }
                        }, 2000);
                    } catch (Exception e) {
                        log("Uygulama kaldırma hatası: " + e.getMessage());
                        Toast.makeText(this, "❌ Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("❌ Hayır", null)
                .create();
        
        dialog.show();
        
        // Buton renklerini ayarla
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFF44336);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(17);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTypeface(null, android.graphics.Typeface.BOLD);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF808080);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(17);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTypeface(null, android.graphics.Typeface.BOLD);
    }

    /**
     * APK indir ve yükle
     */
    private void downloadAndInstallApp(String packageName, String displayName, String downloadUrl, Button button) {
        new Thread(() -> {
            File apkFile = null;
            InputStream inputStream = null;
            FileOutputStream outputStream = null;
            HttpURLConnection connection = null;
            try {
                handler.post(() -> {
                    button.setEnabled(false);
                    button.setText("⏳ İndiriliyor...");
                });

                log("[INFO] APK indirme başlatılıyor: " + displayName);
                log("[INFO] URL: " + downloadUrl);

                URL url = new URL(downloadUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(60000); // 60 saniye
                connection.setReadTimeout(60000); // 60 saniye
                connection.setInstanceFollowRedirects(true);

                // User-Agent ekle (bazı sunucular bunu kontrol eder)
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
                connection.setRequestProperty("Accept", "*/*");
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                connection.setRequestProperty("Cache-Control", "no-cache");

                // Redirect'leri manuel takip et (bazı durumlarda daha güvenilir)
                int responseCode = connection.getResponseCode();
                int redirectCount = 0;
                while (responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                       responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                       responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    redirectCount++;
                    if (redirectCount > 5) {
                        throw new Exception("Çok fazla yönlendirme (redirect)");
                    }
                    String location = connection.getHeaderField("Location");
                    if (location == null) {
                        break;
                    }
                    log("[INFO] Redirect takip ediliyor: " + location);
                    connection.disconnect();
                    url = new URL(location);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(60000);
                    connection.setReadTimeout(60000);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
                    connection.setRequestProperty("Accept", "*/*");
                    responseCode = connection.getResponseCode();
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new Exception("HTTP hatası: " + responseCode);
                }

                // Content-Type kontrolü (log için)
                String contentType = connection.getContentType();
                log("[INFO] Content-Type: " + contentType);
                if (contentType != null && !contentType.contains("application/vnd.android.package-archive") && 
                    !contentType.contains("application/octet-stream") && !contentType.contains("application/zip")) {
                    log("[WARN] Beklenmeyen Content-Type: " + contentType + " (devam ediliyor)");
                }

                long fileLength = connection.getContentLengthLong();
                log("[INFO] Beklenen dosya boyutu: " + fileLength + " bytes");
                
                inputStream = connection.getInputStream();

                File downloadDir = new File(getCacheDir(), "downloads");
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs();
                }
                apkFile = new File(downloadDir, packageName + ".apk");
                
                // Eski dosyayı sil (varsa)
                if (apkFile.exists()) {
                    apkFile.delete();
                }

                outputStream = new FileOutputStream(apkFile);
                byte[] buffer = new byte[8192]; // Buffer boyutunu artırdık
                long total = 0;
                int count;
                long lastLogTime = System.currentTimeMillis();

                while ((count = inputStream.read(buffer)) != -1) {
                    total += count;
                    outputStream.write(buffer, 0, count);
                    
                    // Her 1 saniyede bir progress log'u
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastLogTime > 1000) {
                        if (fileLength > 0) {
                            int percent = (int) ((total * 100) / fileLength);
                            log("[INFO] İndirme ilerlemesi: " + percent + "% (" + total + "/" + fileLength + " bytes)");
                        } else {
                            log("[INFO] İndirilen: " + total + " bytes");
                        }
                        lastLogTime = currentTime;
                    }
                }

                outputStream.flush();
                outputStream.close();
                outputStream = null;
                inputStream.close();
                inputStream = null;
                connection.disconnect();
                connection = null;

                // Dosya boyutu kontrolü
                long actualFileSize = apkFile.length();
                log("[INFO] İndirilen dosya boyutu: " + actualFileSize + " bytes");
                
                if (!apkFile.exists() || actualFileSize == 0) {
                    throw new Exception("APK dosyası boş veya oluşturulamadı");
                }

                // Content-Length ile karşılaştır (eğer belirtilmişse)
                if (fileLength > 0 && actualFileSize != fileLength) {
                    log("[WARN] Dosya boyutu uyuşmuyor! Beklenen: " + fileLength + ", Gerçek: " + actualFileSize);
                    // Yine de devam et, bazı sunucular yanlış Content-Length gönderebilir
                }

                // APK dosyasının geçerli olup olmadığını kontrol et
                // Önce ZIP signature kontrolü (daha hızlı ve güvenilir)
                try (java.io.FileInputStream fis = new java.io.FileInputStream(apkFile)) {
                    byte[] header = new byte[2];
                    int read = fis.read(header);
                    if (read != 2 || header[0] != 0x50 || header[1] != 0x4B) {
                        // PK signature kontrolü (ZIP dosyaları PK ile başlar)
                        throw new Exception("APK dosyası geçersiz: ZIP signature bulunamadı (dosya bozuk olabilir)");
                    }
                    log("[INFO] ZIP signature doğrulandı (PK)");
                }

                // ZipFile ile daha detaylı kontrol (opsiyonel, bazı APK'lar için başarısız olabilir)
                try {
                    java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(apkFile);
                    int entryCount = zipFile.size();
                    zipFile.close();
                    log("[INFO] ZIP dosyası geçerli, " + entryCount + " entry bulundu");
                } catch (Exception zipEx) {
                    // ZipFile hatası olsa bile ZIP signature geçerliyse devam et
                    log("[WARN] ZipFile açılamadı ama ZIP signature geçerli: " + zipEx.getMessage());
                    // Yine de devam et, bazı APK'lar ZipFile ile açılamayabilir ama geçerli olabilir
                }

                handler.post(() -> {
                    button.setText("📦 Kuruluyor...");
                });

                // Shell ile APK'yı yükle
                boolean installSuccess = installApkViaShell(apkFile);
                
                if (!installSuccess) {
                    // Shell başarısız olursa Intent ile dene
                    android.net.Uri apkUri;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        apkUri = FileProvider.getUriForFile(
                                this,
                                getPackageName() + ".fileprovider",
                                apkFile
                        );
                    } else {
                        apkUri = android.net.Uri.fromFile(apkFile);
                    }

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    
                    try {
                        startActivity(intent);
                        log("APK kurulum intent başlatıldı");
                    } catch (Exception e) {
                        handler.post(() -> {
                            button.setEnabled(true);
                            button.setText("⬇️ İndir Kur");
                            Toast.makeText(this, "Kurulum başlatılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                        log("APK kurulum intent hatası: " + e.getMessage());
                        e.printStackTrace();
                        return;
                    }
                }

                log("" + displayName + " indirildi ve kuruluyor...");
                handler.post(() -> {
                    Toast.makeText(this, displayName + " kuruluyor...", Toast.LENGTH_SHORT).show();
                });

                // Listeyi yenile
                handler.postDelayed(() -> {
                    loadAppsFromServer();
                }, 5000);

            } catch (Exception e) {
                log("[ERROR] APK indirme/yükleme hatası: " + e.getMessage());
                e.printStackTrace();
                
                final String finalDownloadUrl = downloadUrl; // Final değişken olarak sakla
                
                handler.post(() -> {
                    button.setEnabled(true);
                    button.setText("🌐 Manuel İndir");
                    
                    // URL'yi butona tag olarak ekle
                    button.setTag(finalDownloadUrl);
                    
                    // Buton onClick listener'ını değiştir - Manuel indirme için
                    button.setOnClickListener(v -> {
                        String url = (String) button.getTag();
                        if (url == null || url.isEmpty()) {
                            Toast.makeText(this, "❌ URL bulunamadı", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        
                        // Uyarı göster
                        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                        builder.setTitle("🌐 Tarayıcıda Aç");
                        builder.setMessage("Bu URL tarayıcınızda açılacak ve APK dosyasını manuel olarak indirebileceksiniz.\n\n" +
                                "⚠️ Uyarı: Tarayıcınız yüklü değilse bu URL açıp indiremezsiniz.\n\n" +
                                "URL: " + url);
                        builder.setPositiveButton("Aç", (dialog, which) -> {
                            try {
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                
                                // Tarayıcı kontrolü
                                if (browserIntent.resolveActivity(getPackageManager()) != null) {
                                    startActivity(browserIntent);
                                    log("[INFO] URL tarayıcıda açıldı: " + url);
                                    Toast.makeText(this, "🌐 Tarayıcıda açılıyor...", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, "❌ Tarayıcı bulunamadı. Lütfen bir tarayıcı yükleyin.", Toast.LENGTH_LONG).show();
                                    log("[ERROR] Tarayıcı bulunamadı");
                                }
                            } catch (Exception ex) {
                                log("[ERROR] Tarayıcı açma hatası: " + ex.getMessage());
                                Toast.makeText(this, "❌ Hata: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                        builder.setNegativeButton("İptal", null);
                        
                        android.app.AlertDialog dialog = builder.create();
                        dialog.show();
                        
                        // Dialog stilini özelleştir
                        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF3DAEA8);
                        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextSize(16);
                        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTypeface(null, android.graphics.Typeface.BOLD);
                        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF808080);
                        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextSize(16);
                        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTypeface(null, android.graphics.Typeface.BOLD);
                    });
                    
                    // Daha açıklayıcı hata mesajı
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("ZIP signature")) {
                        Toast.makeText(this, "❌ APK dosyası bozuk. Manuel indirme için '🌐 Manuel İndir' butonunu kullanın.", Toast.LENGTH_LONG).show();
                    } else if (errorMsg != null && errorMsg.contains("HTTP")) {
                        Toast.makeText(this, "❌ İndirme hatası: " + errorMsg + "\nManuel indirme için '🌐 Manuel İndir' butonunu kullanın.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "❌ Hata: " + (errorMsg != null ? errorMsg : "Bilinmeyen hata") + "\nManuel indirme için '🌐 Manuel İndir' butonunu kullanın.", Toast.LENGTH_LONG).show();
                    }
                });
                
                // Hatalı dosyayı temizle
                if (apkFile != null && apkFile.exists()) {
                    try {
                        apkFile.delete();
                        log("[INFO] Hatalı APK dosyası silindi");
                    } catch (Exception deleteEx) {
                        log("[WARN] Hatalı APK dosyası silinemedi: " + deleteEx.getMessage());
                    }
                }
            } finally {
                // Stream'leri güvenli şekilde kapat
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (Exception e) {
                        // Ignore
                    }
                try {
                    if (outputStream != null) {
                        outputStream.close();
                    }
                } catch (Exception e) {
                    // Ignore
                }
                try {
                    if (connection != null) {
                        connection.disconnect();
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        }).start();
    }

    /**
     * İndirilen dosyaları göster ve silme özelliği ekle
     */
    private void showDownloadedFiles() {
        showDownloadedFilesDialog(null);
    }

    /**
     * İndirilen dosyalar dialog'unu göster (boşken de göster)
     */
    private void showDownloadedFilesDialog(AlertDialog previousDialog) {
        try {
            // Önceki dialog varsa kapat
            if (previousDialog != null && previousDialog.isShowing()) {
                previousDialog.dismiss();
            }

            // Mod'a göre klasör seç: Yerel modda download klasörü, sunucu modda cache klasörü
            File downloadDir;
            String folderName;
            if (isLocalMode) {
                // Yerel mod: Cihazın download klasörü
                downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                folderName = "Download klasörü";
            } else {
                // Sunucu mod: Cache klasörü
                downloadDir = new File(getCacheDir(), "downloads");
                folderName = "Cache klasörü";
            }
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }

            File[] files = downloadDir.listFiles();
            int fileCount = 0;
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".apk")) {
                        fileCount++;
                    }
                }
            }

            // Modern Dialog oluştur
            LinearLayout dialogLayout = new LinearLayout(this);
            dialogLayout.setOrientation(LinearLayout.VERTICAL);
            dialogLayout.setPadding(0, 0, 0, 0);
            dialogLayout.setBackgroundColor(0xFF101922);

            // Başlık alanı (başlık + alt bilgi + sağ tarafta Tümünü Sil)
            LinearLayout headerLayout = new LinearLayout(this);
            headerLayout.setOrientation(LinearLayout.HORIZONTAL);
            headerLayout.setBackgroundColor(0xFF1A2330);
            headerLayout.setPadding(24, 20, 24, 20);
            headerLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

            // Sol taraf: Başlık ve alt bilgi
            LinearLayout titleContainer = new LinearLayout(this);
            titleContainer.setOrientation(LinearLayout.VERTICAL);

            TextView titleText = new TextView(this);
            titleText.setText("İndirilen Dosyalar");
            titleText.setTextColor(0xFFFFFFFF);
            titleText.setTextSize(20);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleContainer.addView(titleText);

            TextView subtitleText = new TextView(this);
            subtitleText.setText(fileCount + " dosya • " + folderName);
            subtitleText.setTextColor(0xFF9DABB9);
            subtitleText.setTextSize(13);
            subtitleText.setPadding(0, 4, 0, 0);
            titleContainer.addView(subtitleText);
            
            LinearLayout.LayoutParams titleContainerParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            headerLayout.addView(titleContainer, titleContainerParams);

            // Sağ taraf: Tümünü Sil butonu (küçük ve sade)
            if (fileCount > 0) {
                Button deleteAllButton = new Button(this);
                deleteAllButton.setText("🗑 Tümünü Sil");
                deleteAllButton.setTextSize(13);
                deleteAllButton.setTextColor(0xFFFF5555); // Kırmızı
                deleteAllButton.setBackgroundColor(0x00000000); // Transparent
                deleteAllButton.setPadding(12, 8, 12, 8);
                final AlertDialog finalPreviousDialog = previousDialog;
                deleteAllButton.setOnClickListener(v -> {
                    // Confirm dialog
                    AlertDialog confirmDialog = new AlertDialog.Builder(this)
                        .setTitle("Tümünü Sil")
                        .setMessage("Tüm indirilen dosyaları silmek istediğinize emin misiniz?")
                        .setPositiveButton("Sil", (d, which) -> {
                            // İndirilen dosyaları sil (mod'a göre klasör)
                            File downloadDirToDelete;
                            if (isLocalMode) {
                                downloadDirToDelete = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                            } else {
                                downloadDirToDelete = new File(getCacheDir(), "downloads");
                            }
                            if (downloadDirToDelete.exists()) {
                                File[] filesToDelete = downloadDirToDelete.listFiles();
                                if (filesToDelete != null) {
                                    for (File fileToDelete : filesToDelete) {
                                        if (fileToDelete.isFile() && fileToDelete.getName().endsWith(".apk")) {
                                            fileToDelete.delete();
                                        }
                                    }
                                }
                            }
                            // Dialog'u yenile
                            if (finalPreviousDialog != null && finalPreviousDialog.isShowing()) {
                                finalPreviousDialog.dismiss();
                            }
                            showDownloadedFilesDialog(null);
                        })
                        .setNegativeButton("İptal", null)
                        .create();
                    confirmDialog.show();
                });
                LinearLayout.LayoutParams deleteAllParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                headerLayout.addView(deleteAllButton, deleteAllParams);
            }

            dialogLayout.addView(headerLayout);

            // Dosya listesi
            ScrollView scrollView = new ScrollView(this);
            scrollView.setBackgroundColor(0xFF101922);
            scrollView.setPadding(16, 16, 16, 16);
            scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    500));

            LinearLayout filesLayout = new LinearLayout(this);
            filesLayout.setOrientation(LinearLayout.VERTICAL);

            // Dosya yoksa mesaj göster
            if (fileCount == 0) {
                TextView emptyText = new TextView(this);
                emptyText.setText("📭 İndirilen dosya bulunmuyor");
                emptyText.setTextColor(0xFF9DABB9);
                emptyText.setTextSize(16);
                emptyText.setGravity(android.view.Gravity.CENTER);
                emptyText.setPadding(32, 64, 32, 64);
                filesLayout.addView(emptyText);
            } else {
                // Dosyaları göster
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".apk")) {
                    // Modern dosya kartı
                    LinearLayout fileCard = new LinearLayout(this);
                    fileCard.setOrientation(LinearLayout.HORIZONTAL);
                    fileCard.setPadding(12, 12, 12, 12);
                    fileCard.setBackgroundColor(0xFF1C2630);
                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    cardParams.setMargins(0, 0, 0, 8);

                    // Sol ikon
                    LinearLayout iconBox = new LinearLayout(this);
                    iconBox.setOrientation(LinearLayout.VERTICAL);
                    iconBox.setGravity(android.view.Gravity.CENTER);
                    iconBox.setBackgroundColor(0xFF1976D2);
                    iconBox.setPadding(16, 16, 16, 16);
                    
                    TextView iconText = new TextView(this);
                    iconText.setText("📦");
                    iconText.setTextSize(20);
                    iconBox.addView(iconText);
                    
                    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(60, 60);
                    iconParams.setMargins(0, 0, 12, 0);
                    fileCard.addView(iconBox, iconParams);

                    // Dosya bilgisi
                    LinearLayout infoLayout = new LinearLayout(this);
                    infoLayout.setOrientation(LinearLayout.VERTICAL);
                    infoLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

                    String fileName = file.getName();
                    long fileSize = file.length();
                    String sizeStr = formatFileSize(fileSize);

                    TextView nameText = new TextView(this);
                    nameText.setText(fileName.replace(".apk", ""));
                    nameText.setTextColor(0xFFFFFFFF);
                    nameText.setTextSize(14);
                    nameText.setTypeface(null, android.graphics.Typeface.BOLD);
                    nameText.setMaxLines(1);
                    nameText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    infoLayout.addView(nameText);

                    TextView sizeText = new TextView(this);
                    sizeText.setText("📏 " + sizeStr);
                    sizeText.setTextColor(0xFF9DABB9);
                    sizeText.setTextSize(12);
                    sizeText.setPadding(0, 2, 0, 0);
                    infoLayout.addView(sizeText);

                    LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    fileCard.addView(infoLayout, infoParams);

                    // Butonlar container (KUR ve Sil)
                    LinearLayout buttonsContainer = new LinearLayout(this);
                    buttonsContainer.setOrientation(LinearLayout.HORIZONTAL);
                    buttonsContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    
                    // KUR butonu
                    Button installButton = new Button(this);
                    installButton.setText("📦 KUR");
                    installButton.setTextColor(0xFFFFFFFF);
                    installButton.setBackgroundColor(0xFF1976D2);
                    installButton.setTextSize(13);
                    installButton.setTypeface(null, android.graphics.Typeface.BOLD);
                    installButton.setPadding(16, 12, 16, 12);
                    LinearLayout.LayoutParams installButtonParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            56);
                    installButtonParams.setMargins(0, 0, 8, 0);
                    buttonsContainer.addView(installButton, installButtonParams);

                    // Sil butonu
                    Button deleteButton = new Button(this);
                    deleteButton.setText("🗑");
                    deleteButton.setTextColor(0xFFFFFFFF);
                    deleteButton.setBackgroundColor(0xFF3D1F1F);
                    deleteButton.setTextSize(18);
                    deleteButton.setPadding(16, 12, 16, 12);
                    LinearLayout.LayoutParams deleteButtonParams = new LinearLayout.LayoutParams(
                            56, 56);
                    buttonsContainer.addView(deleteButton, deleteButtonParams);
                    
                    LinearLayout.LayoutParams buttonsContainerParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    buttonsContainerParams.gravity = android.view.Gravity.CENTER_VERTICAL;
                    buttonsContainerParams.setMargins(8, 0, 0, 0);
                    fileCard.addView(buttonsContainer, buttonsContainerParams);

                    filesLayout.addView(fileCard, cardParams);
                    }
                }
            }

            scrollView.addView(filesLayout);
            dialogLayout.addView(scrollView);

            final AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(dialogLayout)
                    .setPositiveButton("Kapat", null)
                    .create();
            
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.show();
            
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF1976D2);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(16);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTypeface(null, android.graphics.Typeface.BOLD);
            
            // Dialog oluşturulduktan sonra butonların listener'larını ayarla
            if (fileCount > 0 && files != null) {
                int buttonIndex = 0;
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".apk")) {
                        if (buttonIndex < filesLayout.getChildCount()) {
                            View fileCardView = filesLayout.getChildAt(buttonIndex);
                            if (fileCardView instanceof LinearLayout) {
                                LinearLayout fileCard = (LinearLayout) fileCardView;
                                // Butonlar container'ını bul (son child)
                                if (fileCard.getChildCount() > 0) {
                                    View lastChild = fileCard.getChildAt(fileCard.getChildCount() - 1);
                                    if (lastChild instanceof LinearLayout) {
                                        LinearLayout buttonsContainer = (LinearLayout) lastChild;
                                        // KUR butonunu bul (ilk child)
                                        if (buttonsContainer.getChildCount() > 0) {
                                            View installBtnView = buttonsContainer.getChildAt(0);
                                            if (installBtnView instanceof Button) {
                                                Button installBtn = (Button) installBtnView;
                    File finalFile = file;
                                                installBtn.setOnClickListener(v -> {
                                                    log("Kurulum başlatılıyor: " + finalFile.getName());
                                                    installApkFile(finalFile);
                                                });
                                            }
                                        }
                                        // Sil butonunu bul (ikinci child)
                                        if (buttonsContainer.getChildCount() > 1) {
                                            View deleteBtnView = buttonsContainer.getChildAt(1);
                                            if (deleteBtnView instanceof Button) {
                                                Button deleteBtn = (Button) deleteBtnView;
                                                File finalFile = file;
                                                String finalFileName = file.getName();
                                                // Listener'ı ayarla (dialog referansı ile)
                                                deleteBtn.setOnClickListener(v -> {
                        AlertDialog confirmDialog = new AlertDialog.Builder(this)
                                .setTitle("🗑️ Dosyayı Sil")
                                                            .setMessage("⚠️ " + finalFileName + " dosyasını silmek istediğinize emin misiniz?")
                                .setPositiveButton("✅ Evet", (d, which) -> {
                                    if (finalFile.delete()) {
                                        Toast.makeText(this, "✅ Dosya silindi", Toast.LENGTH_SHORT).show();
                                                                    log("Dosya silindi: " + finalFileName);
                                                                    // Dialog'u yeniden aç (içeriği güncelle)
                                                                    showDownloadedFilesDialog(dialog);
                                    } else {
                                        Toast.makeText(this, "❌ Dosya silinemedi", Toast.LENGTH_SHORT).show();
                                                                    log("Dosya silinemedi: " + finalFileName);
                                    }
                                })
                                .setNegativeButton("❌ Hayır", null)
                                .create();
                        confirmDialog.show();
                        confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFF44336);
                        confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(17);
                        confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTypeface(null, android.graphics.Typeface.BOLD);
                        confirmDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF808080);
                        confirmDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(17);
                        confirmDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTypeface(null, android.graphics.Typeface.BOLD);
                    });
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        buttonIndex++;
                    }
                }
            }

        } catch (Exception e) {
            log("İndirilen dosyalar gösterim hatası: " + e.getMessage());
            Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    /**
     * Dosya boyutunu formatla
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * APK dosyasını kur
     */
    /**
     * APK dosyasını kur (fileName ile)
     */
    private void installApkFile(String fileName) {
        try {
            File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            File apkFile = new File(downloadDir, fileName);
            installApkFile(apkFile);
        } catch (Exception e) {
            log("APK kurulum hatası: " + e.getMessage());
            handler.post(() -> {
                Toast.makeText(this, "❌ APK kurulum hatası: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    /**
     * APK dosyasını kur (File ile)
     */
    private void installApkFile(File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(this, "❌ APK dosyası bulunamadı", Toast.LENGTH_SHORT).show();
            log("APK dosyası bulunamadı");
            return;
        }

        new Thread(() -> {
            try {
                handler.post(() -> {
                    Toast.makeText(this, "📦 Kurulum başlatılıyor...", Toast.LENGTH_SHORT).show();
                });

                // Intent ile kurulum
                android.net.Uri apkUri;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    apkUri = FileProvider.getUriForFile(
                            this,
                            getPackageName() + ".fileprovider",
                            apkFile
                    );
            } else {
                    apkUri = android.net.Uri.fromFile(apkFile);
                }

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                handler.post(() -> {
                    try {
                        startActivity(intent);
                        log("APK kurulum intent başlatıldı: " + apkFile.getName());
                        Toast.makeText(this, "✅ Kurulum başlatıldı", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "❌ Kurulum başlatılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        log("[ERROR] APK kurulum intent hatası: " + e.getMessage());
                        e.printStackTrace();
                    }
                });

                // Listeyi yenile
                handler.postDelayed(() -> {
                    loadAppsFromServer();
                }, 2000);

            } catch (Exception e) {
                handler.post(() -> {
                    Toast.makeText(this, "❌ Kurulum hatası: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
                log("[ERROR] APK kurulum hatası: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Shell ile APK kurulumu yap
     */
    private boolean installApkViaShell(File apkFile) {
        try {
            // APK'yı /data/local/tmp'ye kopyala (erişilebilir olması için)
            File tmpApk = apkFile;

            // pm install komutu ile kur
            String installCmd = "pm install -r " + tmpApk.getAbsolutePath();
            log("Kurulum komutu: " + installCmd);
            
            Process installProcess = Runtime.getRuntime().exec(installCmd);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(installProcess.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(installProcess.getErrorStream()));
            
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            while ((line = errorReader.readLine()) != null) {
                output.append("ERR: ").append(line).append("\n");
            }
            
            int exitCode = installProcess.waitFor();
            
            String outputStr = output.toString().trim();
            log("pm install exit code: " + exitCode);
            log("pm install output: " + (outputStr.isEmpty() ? "(boş)" : outputStr));
            
            if (exitCode == 0 || outputStr.contains("Success")) {
                handler.post(() -> {
                    Toast.makeText(this, "✅ Uygulama kuruldu!", Toast.LENGTH_SHORT).show();
                });
                log("APK shell ile başarıyla kuruldu");
                return true;
            } else {
                log("Shell kurulum başarısız, intent deneniyor");
                return false;
            }
            
        } catch (Exception e) {
            log("Shell kurulum hatası: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Foreground Service'i başlat (arka planda çalışması için)
     */
    /**
     * Power mode ayarını kaydet
     */
    private void savePowerModeSetting(int powerMode) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("powerModeSetting", powerMode);
            editor.apply();
        } catch (Exception e) {
            log("savePowerModeSetting hatası: " + e.getMessage());
        }
    }

    /**
     * Power mode ayarını yükle (2 = Motor Çalışınca, 1 = Araç Hazır Durumdayken, 0 = Elle Çalıştır)
     */
    private void loadPowerModeSetting(RadioGroup radioGroup) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            int savedMode = prefs.getInt("powerModeSetting", 2); // Varsayılan: 2 (Motor Çalışınca)
            radioGroup.check(savedMode);
        } catch (Exception e) {
            log("loadPowerModeSetting hatası: " + e.getMessage());
        }
    }

    /**
     * Harita kontrol tuşu ayarını kaydet (true = Açık, false = Kapalı)
     */
    private void saveMapControlKeySetting(boolean isEnabled) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("mapControlKeyEnabled", isEnabled);
            editor.apply();
        } catch (Exception e) {
            log("saveMapControlKeySetting hatası: " + e.getMessage());
        }
    }

    /**
     * Harita kontrol tuşu ayarını yükle (varsayılan: true = Açık)
     */
    private void loadMapControlKeySetting(RadioGroup radioGroup) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            boolean isEnabled = prefs.getBoolean("mapControlKeyEnabled", true); // Varsayılan: Açık
            radioGroup.check(isEnabled ? 100 : 101); // 100 = Açık, 101 = Kapalı
        } catch (Exception e) {
            log("loadMapControlKeySetting hatası: " + e.getMessage());
        }
    }
    

    /**
     * Araç kapanınca otomatik kapatma ayarını kaydet (true = Evet, false = Hayır)
     */
    private void saveAutoCloseOnPowerOffSetting(boolean isEnabled) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("autoCloseOnPowerOff", isEnabled);
            editor.apply();
        } catch (Exception e) {
            log("saveAutoCloseOnPowerOffSetting hatası: " + e.getMessage());
        }
    }

    /**
     * Araç kapanınca otomatik kapatma ayarını yükle (varsayılan: true = Evet)
     */
    private void loadAutoCloseOnPowerOffSetting(RadioGroup radioGroup) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            boolean isEnabled = prefs.getBoolean("autoCloseOnPowerOff", true); // Varsayılan: Evet
            radioGroup.check(isEnabled ? 200 : 201); // 200 = Evet, 201 = Hayır
        } catch (Exception e) {
            log("loadAutoCloseOnPowerOffSetting hatası: " + e.getMessage());
        }
    }

    /**
     * Seçili hafıza modunu kaydet
     */
    private void saveDriveModeSetting(int driveMode) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("driveModeSetting", driveMode);
            editor.apply();
            log("Hafıza modu kaydedildi: " + driveMode);
        } catch (Exception e) {
            log("saveDriveModeSetting hatası: " + e.getMessage());
        }
    }

    /**
     * Kaydedilmiş hafıza modu ayarını yükle
     */
    private void loadDriveModeSetting(RadioGroup radioGroup) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            int savedMode = prefs.getInt("driveModeSetting", -1); // -1 = seçili değil veya "Hiçbiri"
            
            // Varsayılan: Hiçbiri (-1)
            if (savedMode == -1) {
                radioGroup.check(9); // Hiçbiri = 9
            } else {
                // Mode değerlerine göre ID'leri bul: 0=Eco(10), 1=Normal(11), 2=Sport(12), 3=Snow(13), 4=Mud(14), 5=Rock(15), 7=Sand(17)
                int[] driveModeIds = {10, 11, 12, 13, 14, 15, 17}; // 0,1,2,3,4,5,7 için ID'ler
                int[] driveModeValues = {0, 1, 2, 3, 4, 5, 7};
                
                for (int i = 0; i < driveModeValues.length; i++) {
                    if (driveModeValues[i] == savedMode) {
                        radioGroup.check(driveModeIds[i]);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log("loadDriveModeSetting hatası: " + e.getMessage());
        }
    }

    /**
     * Seçili ISS ayarını kaydet
     */
    private void saveIssSetting(int issValue) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("issSetting", issValue);
            editor.apply();
            log("ISS ayarı kaydedildi: " + issValue);
        } catch (Exception e) {
            log("saveIssSetting hatası: " + e.getMessage());
        }
    }

    private void saveLdwSetting(int ldwValue) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("ldwSetting", ldwValue);
            editor.apply();
            log("LDW ayarı kaydedildi: " + ldwValue);
        } catch (Exception e) {
            log("saveLdwSetting hatası: " + e.getMessage());
        }
    }

    private void saveSpdLimitSetting(int value) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("spdLimitSetting", value);
            editor.apply();
            log("Hız limit uyarı modu kaydedildi: " + value);
        } catch (Exception e) {
            log("saveSpdLimitSetting hatası: " + e.getMessage());
        }
    }

    private void saveLdpSetting(int value) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("ldpSetting", value);
            editor.apply();
            log("LDP ayarı kaydedildi: " + value);
        } catch (Exception e) {
            log("saveLdpSetting hatası: " + e.getMessage());
        }
    }

    private void saveFcwSetting(int value) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("fcwSetting", value);
            editor.apply();
            log("FCW ayarı kaydedildi: " + value);
        } catch (Exception e) {
            log("saveFcwSetting hatası: " + e.getMessage());
        }
    }

    private void saveAebSetting(int value) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("aebSetting", value);
            editor.apply();
            log("AEB ayarı kaydedildi: " + value);
        } catch (Exception e) {
            log("saveAebSetting hatası: " + e.getMessage());
        }
    }

    /**
     * FCW/AEB için güvenlik uyarı dialogu
     */
    private void showSafetyWarningDialog(String settingKey, int newValue, int[] savedValues, int cardIndex,
                                          boolean[] isActiveRef, LinearLayout[] cardContainers, TextView[] iconViews,
                                          TextView[] titleViews, TextView[] statusViews, TextView[] onChips, float density) {
        String title = settingKey.equals("fcwSetting") ? "⚠️ Ön Çarpışma Uyarısı" : "🛑 Aktif Acil Fren Sistemi";
        String message = "Bu güvenlik özelliğini devre dışı bırakmak tamamen sizin sorumluluğunuzdadır.\n\n" +
                "Bu ayar, aracın güvenlik sistemlerini etkiler. Devre dışı bırakıldığında olası risklerden geliştirici sorumlu değildir.\n\n" +
                "Devam etmek istiyor musunuz?";

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(48, 40, 48, 24);
        dialogLayout.setBackgroundColor(0xFF0A0F14);
        
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(20);
        titleView.setTextColor(0xFFFFCC00);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setGravity(android.view.Gravity.CENTER);
        dialogLayout.addView(titleView);
        
        TextView messageView = new TextView(this);
        messageView.setText(message);
        messageView.setTextSize(15);
        messageView.setTextColor(0xFFE6E6E6);
        messageView.setPadding(0, 32, 0, 32);
        messageView.setLineSpacing(6, 1);
        dialogLayout.addView(messageView);
        
        builder.setView(dialogLayout);
        
        builder.setPositiveButton("Kabul Ediyorum", (dialog, which) -> {
            // Ayarı kaydet
            if (settingKey.equals("fcwSetting")) {
                saveFcwSetting(newValue);
                savedValues[4] = newValue;
                log("FCW: Aktif (Ön Çarpışma Uyarısı Kapalı)");
            } else if (settingKey.equals("aebSetting")) {
                saveAebSetting(newValue);
                savedValues[5] = newValue;
                log("AEB: Aktif (Aktif Acil Fren Kapalı)");
            }
            
            // PowerMode == 2 ise bir kerelik kapatma değeri gönder
            try {
                SharedPreferences powerPrefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
                int currentPowerMode = powerPrefs.getInt("lastPowerMode", -1);
                int[] pwrItems = CarInfoProxy.getInstance().getItemValues(
                    VDEventCarInfo.MODULE_READONLY_INFO,
                    ReadOnlyID.ID_SYSTEM_POWER_MODE);
                if (pwrItems != null && pwrItems.length > 0) {
                    currentPowerMode = pwrItems[0];
                }
                log("Kapatma Modu PowerMode: " + currentPowerMode);
                if (currentPowerMode == 2 && CarInfoProxy.getInstance().isServiceConnnected()) {
                    if (settingKey.equals("fcwSetting")) {
                        int[] vals = new int[]{2, 1};
                        CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, 36, vals);
                        log("✅ FCW kapatma değeri gönderildi: [2, 1] (Kapalı)");
                    } else if (settingKey.equals("aebSetting")) {
                        int[] vals = new int[]{2};
                        CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, 37, vals);
                        log("✅ AEB kapatma değeri gönderildi: [2] (Kapalı)");
                    }
                }
            } catch (Exception e) {
                log("❌ Kapatma değeri gönderme hatası: " + e.getMessage());
            }
            
            // UI'ı güncelle
            isActiveRef[0] = true;
            android.graphics.drawable.GradientDrawable newBg = new android.graphics.drawable.GradientDrawable();
            newBg.setColor(0xFF1E3A5F);
            newBg.setCornerRadius(16 * density);
            newBg.setStroke((int)(2 * density), 0xFF2196F3);
            cardContainers[cardIndex].setBackground(newBg);
            
            iconViews[cardIndex].setTextColor(0xFFFFFFFF);
            titleViews[cardIndex].setTypeface(null, android.graphics.Typeface.BOLD);
            
            if (settingKey.equals("fcwSetting")) {
                statusViews[cardIndex].setText("FCW Kapalı");
            } else {
                statusViews[cardIndex].setText("AEB Kapalı");
            }
            
            onChips[cardIndex].setVisibility(android.view.View.VISIBLE);
        });
        
        builder.setNegativeButton("İptal", (dialog, which) -> {
            dialog.dismiss();
        });
        
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
        
        // Buton renklerini ayarla
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFFFCC00);
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(0xAAFFFFFF);
    }

    /**
     * Kaydedilmiş ISS ayarını yükle
     */
    private void loadIssSetting(RadioGroup radioGroup) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            int savedIss = prefs.getInt("issSetting", -1); // -1 = "Hiçbiri"
            
            int[] issIds = {20, 21, 22}; // Hiçbiri, Kapalı, Açık
            int[] issValues = {-1, 0, 1};
            
            for (int i = 0; i < issValues.length; i++) {
                if (issValues[i] == savedIss) {
                    radioGroup.check(issIds[i]);
                    break;
                }
            }
        } catch (Exception e) {
            log("loadIssSetting hatası: " + e.getMessage());
        }
    }

    private void loadLdwSetting(RadioGroup radioGroup) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            int saved = prefs.getInt("ldwSetting", -1); // -1 = "Hiçbiri"

            int[] ids = {30, 31, 32}; // Hiçbiri, Kapalı, Açık
            int[] vals = {-1, 2, 1};   // 2=Kapalı, 1=Açık (araç logundan)
            for (int i = 0; i < vals.length; i++) {
                if (vals[i] == saved) {
                    radioGroup.check(ids[i]);
                    break;
                }
            }
        } catch (Exception e) {
            log("loadLdwSetting hatası: " + e.getMessage());
        }
    }

    private void loadSpdLimitSetting(RadioGroup radioGroup) {
        try {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            int saved = prefs.getInt("spdLimitSetting", -1); // -1 = "Hiçbiri"

            int[] ids = {40, 41}; // Hiçbiri, Kapalı
            int[] vals = {-1, 2};
            if (saved == 2) {
                radioGroup.check(41);
            } else {
                radioGroup.check(40);
            }
        } catch (Exception e) {
            log("loadSpdLimitSetting hatası: " + e.getMessage());
        }
    }

    /**
     * Hafıza modunu ayarla (CarInfoProxy kullanarak)
     */
    private void setDriveMode(int mode) {
        try {
            log("Hafıza modu ayarlanıyor: " + mode);
            
            CarInfoProxy carInfo = CarInfoProxy.getInstance();
            
            if (!carInfo.isServiceConnnected()) {
                carInfo.init(this);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    setDriveModeInternal(mode);
                }, 500);
                return;
            }
            
            setDriveModeInternal(mode);
            
        } catch (Exception e) {
            log("setDriveMode hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Hafıza modunu ayarla (internal)
     */
    private void setDriveModeInternal(int mode) {
        try {
            CarInfoProxy carInfo = CarInfoProxy.getInstance();
            
            int valueToSend = mode;
            if (mode == 6 || mode == 7) {
                valueToSend = 7; // SAND
            }
            
            // MODULE_NEW_ENERGY = 327682
            // ID 4 = Drive Mode (NewEnergyID.ID_DRIVE_MODE = 4)
            // ClusterControl ve carayar'da direkt 4 kullanılıyor
            carInfo.sendItemValue(VDEventCarInfo.MODULE_NEW_ENERGY, 4, valueToSend);
            
            log("Hafıza modu gönderildi!");
            log("   Module: MODULE_NEW_ENERGY (" + VDEventCarInfo.MODULE_NEW_ENERGY + ")");
            log("   ID: 4 (Drive Mode)");
            log("   Value: " + valueToSend + " (Mode: " + mode + ")");
            
            String modeName = "Unknown";
            switch (mode) {
                case 0:
                    modeName = "ECO";
                    break;
                case 1:
                    modeName = "NORMAL";
                    break;
                case 2:
                    modeName = "SPORT";
                    break;
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
            
            //Toast.makeText(this, "✅ Hafıza Modu: " + modeName + " (" + valueToSend + ")", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            log("setDriveModeInternal hatası: " + e.getMessage());
            e.printStackTrace();
        }
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

    private void startForegroundService() {
        try {
            Intent serviceIntent = new Intent(this, MapControlService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            log("Foreground Service başlatıldı");
        } catch (Exception e) {
            log("Foreground Service başlatma hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reset işlemi: JSON'dan package listesini çek, eşleşenleri bul ve sil
     */
    private void performReset() {
        log("Reset işlemi başlatılıyor...");
        
        new Thread(() -> {
            try {
                // JSON'dan package listesini çek
                URL url = new URL("https://vnoisy.dev/apk/list.json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    handler.post(() -> {
                        Toast.makeText(this, "JSON yüklenemedi: " + responseCode, Toast.LENGTH_SHORT).show();
                        log("HTTP hatası: " + responseCode);
                    });
                    connection.disconnect();
                    return;
                }

                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                inputStream.close();
                connection.disconnect();

                JSONObject jsonObject = new JSONObject(response.toString());
                JSONArray listArray = jsonObject.getJSONArray("list");

                // Yüklü uygulamalarla eşleştir
                PackageManager pm = getPackageManager();
                ArrayList<String> matchingPackages = new ArrayList<>();
                
                for (int i = 0; i < listArray.length(); i++) {
                    JSONObject appObj = listArray.getJSONObject(i);
                    String packageName = appObj.getString("packageName");
                    
                    // Uygulama yüklü mü kontrol et
                    try {
                        pm.getPackageInfo(packageName, 0);
                        matchingPackages.add(packageName);
                        log("Eşleşen uygulama bulundu: " + packageName);
                    } catch (PackageManager.NameNotFoundException e) {
                        // Yüklü değil, atla
                    }
                }

                final int appCount = matchingPackages.size();
                final int totalCount = appCount + 1; // Kendisi dahil
                
                handler.post(() -> {
                    // İlk dialog: Bağlı uygulamaları mı yoksa sadece kendisini mi?
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("⚠️ Sıfırlama Seçeneği");
                    builder.setMessage("Ne silmek istiyorsunuz?");
                    
                    builder.setPositiveButton("Bağlı Uygulamaları Kaldır", (dialog, which) -> {
                        // Yüklediğimiz tüm uygulamalar + kendisi
                        showDeleteConfirmationDialog(matchingPackages, true, true);
                    });
                    
                    builder.setNeutralButton("Sadece Bu Uygulamayı Kaldır", (dialog, which) -> {
                        // Sadece kendisi (com.mapcontrol)
                        showDeleteConfirmationDialog(null, false, true); // deleteSelf = true olmalı
                    });
                    
                    builder.setNegativeButton("İptal", (dialog, which) -> {
                        dialog.dismiss();
                        log("Reset işlemi iptal edildi");
                    });
                    builder.setCancelable(false);
                    builder.show();
                });

            } catch (Exception e) {
                handler.post(() -> {
                    Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    log("Reset işlemi hatası: " + e.getMessage());
                });
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Silme onay dialog'unu göster
     */
    private void showDeleteConfirmationDialog(ArrayList<String> matchingPackages, boolean deleteRelatedFiles, boolean deleteSelf) {
        String message;
        if (matchingPackages != null && matchingPackages.size() > 0) {
            // Bağlı uygulamaları kaldır
            int appCount = matchingPackages.size();
            message = "Mevcut uygulama ve (" + appCount + ") yüklediğiniz tüm uygulamalar silinecek";
            if (deleteRelatedFiles) {
                message += " ve bağlı olanlar (/data/local/tmp altındaki APK'lar) da silinecek";
            }
            message += ", yüklü uygulama sayısı kadar onay vermeniz gerekebilir.";
        } else {
            // Sadece kendisi
            message = "Sadece mevcut uygulama (com.mapcontrol) silinecek.";
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚠️ Sıfırlama Onayı");
        builder.setMessage(message);
        builder.setPositiveButton("Evet", (dialog, which) -> {
            // Silme işlemlerini başlat
            deleteMatchingApps(matchingPackages, deleteRelatedFiles, deleteSelf);
        });
        builder.setNegativeButton("Hayır", (dialog, which) -> {
            dialog.dismiss();
            log("Reset işlemi iptal edildi");
        });
        builder.setCancelable(false);
        builder.show();
    }

    /**
     * Eşleşen uygulamaları sil ve son olarak uygulamanın kendisini sil
     */
    private void deleteMatchingApps(ArrayList<String> packages, boolean deleteRelatedFiles, boolean deleteSelf) {
        new Thread(() -> {
            try {
                log("Uygulama silme işlemi başlatılıyor...");
                
                // 1. Eşleşen package'ları sil (eğer varsa)
                if (packages != null && packages.size() > 0) {
                    // Package'ları sırayla sil (Thread.sleep yerine Handler.postDelayed kullanarak)
                    deletePackagesSequentially(packages, 0, deleteRelatedFiles, deleteSelf);
                } else {
                    // Eşleşen uygulama yok, sadece kendisini sil
                    if (deleteSelf) {
                        deleteSelfApp();
                    }
                }
            } catch (Exception e) {
                log("Uygulama silme işlemi hatası: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
    
    /**
     * Package'ları sırayla sil (Thread.sleep yerine Handler.postDelayed kullanır)
     */
    private void deletePackagesSequentially(ArrayList<String> packages, int index, boolean deleteRelatedFiles, boolean deleteSelf) {
        if (index >= packages.size()) {
            // Tüm package'lar silindi, şimdi bağlı dosyaları ve kendisini sil
            if (deleteRelatedFiles) {
                deleteRelatedFiles();
            }
            if (deleteSelf) {
                deleteSelfApp();
            }
            return;
        }
        
        String packageName = packages.get(index);
        log("Siliniyor: " + packageName);
        
        // Önce Intent ile silmeyi dene (daha güvenilir)
        try {
            Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
            uninstallIntent.setData(android.net.Uri.parse("package:" + packageName));
            uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            handler.post(() -> {
                try {
                    startActivity(uninstallIntent);
                    log("Silme Intent'i gönderildi: " + packageName);
                } catch (Exception e) {
                    log("Intent ile silme başarısız, pm komutu deneniyor: " + e.getMessage());
                    // Intent başarısız olursa pm komutunu dene
                    tryUninstallViaPm(packageName);
                }
            });
            
            // 500ms sonra bir sonraki package'a geç (Thread.sleep yerine)
            handler.postDelayed(() -> {
                deletePackagesSequentially(packages, index + 1, deleteRelatedFiles, deleteSelf);
            }, 500);
        } catch (Exception intentEx) {
            log("Intent hatası, pm komutu deneniyor: " + intentEx.getMessage());
            // Intent başarısız olursa pm komutunu dene
            tryUninstallViaPm(packageName);
            // Hata olsa bile devam et
            handler.postDelayed(() -> {
                deletePackagesSequentially(packages, index + 1, deleteRelatedFiles, deleteSelf);
            }, 500);
        }
    }
    
    /**
     * Bağlı dosyaları sil (/data/local/tmp altındaki APK'lar)
     */
    private void deleteRelatedFiles() {
        new Thread(() -> {
            try {
                log("/data/local/tmp altındaki APK'lar siliniyor...");
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "rm -f /data/local/tmp/*.apk"});
                
                // Output ve error stream'leri oku
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errorReader.readLine()) != null) {
                    output.append("ERR: ").append(line).append("\n");
                }
                
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    log("/data/local/tmp temizlendi");
                } else {
                    log("/data/local/tmp temizlenemedi (exit code: " + exitCode + ")");
                    String outputStr = output.toString().trim();
                    if (!outputStr.isEmpty()) {
                        log("   Output: " + outputStr);
                    }
                }
            } catch (Exception e) {
                log("/data/local/tmp temizleme hatası: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
    
    /**
     * Uygulamanın kendisini sil
     */
    private void deleteSelfApp() {
        handler.post(() -> {
            try {
                String selfPackage = getPackageName();
                log("Uygulama kendisini siliyor: " + selfPackage);
                
                // Toast göster
                Toast.makeText(this, "Uygulama kendisini siliyor...", Toast.LENGTH_SHORT).show();
                
                // 1000ms sonra silme işlemini başlat (Toast görünsün diye)
                handler.postDelayed(() -> {
                    try {
                        // Intent ile kendisini sil (daha güvenilir)
                        Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                        uninstallIntent.setData(android.net.Uri.parse("package:" + selfPackage));
                        uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(uninstallIntent);
                        log("Uygulama silme Intent'i gönderildi");
                        
                        // Intent başarısız olursa pm komutunu da dene (2000ms sonra, Thread.sleep yerine Handler.postDelayed)
                        handler.postDelayed(() -> {
                            new Thread(() -> {
                                try {
                                    Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "pm uninstall " + selfPackage});
                                    log("Uygulama silme pm komutu da gönderildi");
                                } catch (Exception e) {
                                    // Hata önemli değil, Intent zaten gönderildi
                                }
                            }).start();
                        }, 2000);
                    } catch (Exception e) {
                        log("Uygulama silme hatası: " + e.getMessage());
                        e.printStackTrace();
                    }
                }, 1000);
            } catch (Exception e) {
                log("Uygulama silme hatası: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    

    /**
     * pm komutu ile uygulama silmeyi dene
     */
    private void tryUninstallViaPm(String packageName) {
        new Thread(() -> {
            try {
                log("pm komutu ile silme deneniyor: " + packageName);
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "pm uninstall " + packageName});
                
                // Output ve error stream'leri oku
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errorReader.readLine()) != null) {
                    output.append("ERR: ").append(line).append("\n");
                }
                
                int exitCode = process.waitFor();
                String outputStr = output.toString().trim();
                
                if (exitCode == 0) {
                    log("pm komutu ile silindi: " + packageName);
                } else {
                    log("pm komutu ile silinemedi (exit code: " + exitCode + "): " + packageName);
                    if (!outputStr.isEmpty()) {
                        log("   Output: " + outputStr);
                    }
                }
            } catch (Exception e) {
                log("pm komutu silme hatası (" + packageName + "): " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
    
    /**
     * Yansıtma kontrolü UI'ını duruma göre günceller
     */
    private void updateProjectionUI(TextView statusText, Button btnOpen, Button btnClose) {
        if (isNavigationOpen) {
            // Aktif durum
            statusText.setText("Yansıtma aktif");
            //btnOpen.setEnabled(false); // Yansıt butonu pasif
            //btnClose.setEnabled(true); // Durdur butonu aktif
        } else {
            // Pasif durum
            statusText.setText("Yansıtma kapalı");
            //btnOpen.setEnabled(true); // Yansıt butonu aktif
            //btnClose.setEnabled(false); // Durdur butonu pasif
        }
    }
    
    // Profil tab için UI elementleri (güncelleme için)
    private TextView profileLoginStatusText;
    private TextView profileEmailLabel;
    private TextView profileCodeLabel;
    private EditText profileEmailInput;
    private EditText profileCodeInput;
    private LinearLayout profileButtonsContainer;
    private Button profileSendCodeButton;
    private Button profileLoginButton;
    private Button profileLogoutButton;
    private Button profilePlatformButton; // Platforma Gir butonu
    private Button profileSaveDataButton;
    private Button profileLoadDataButton; // Son Değişiklikleri Getir butonu
    private Button profileSaveLocationButton;
    private RadioGroup profileAutoLocationRadioGroup; // Otomatik konum kaydetme RadioGroup
    private LinearLayout profileAutoLocationIconCircle1; // Evet icon circle
    private LinearLayout profileAutoLocationIconCircle2; // Hayır icon circle
    
    /**
     * Profil tab içeriğini oluştur
     */
    private void createProfileTab() {
        profileScrollView = new ScrollView(this);
        profileScrollView.setBackgroundColor(0xFF1E1E1E);
        profileScrollView.setFillViewport(true);
        
        profileTabContent = new LinearLayout(this);
        profileTabContent.setOrientation(LinearLayout.VERTICAL);
        profileTabContent.setPadding(0, 0, 0, 0); // Ayarlardaki gibi padding yok, kartlar kendi margin'lerini kullanıyor
        profileTabContent.setBackgroundColor(0xFF1E1E1E);
        
        
        // Giriş kartı - Ayarlardaki tasarıma uygun
        LinearLayout loginCard = new LinearLayout(this);
        loginCard.setOrientation(LinearLayout.VERTICAL);
        loginCard.setPadding(20, 20, 20, 20);
        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(0xFF151C24); // Ayarlardaki kart rengi
        cardBg.setCornerRadius(12);
        cardBg.setStroke(1, 0xFF2A3A47); // Ayarlardaki border rengi
        loginCard.setBackground(cardBg);
        
        // Durum metni
        profileLoginStatusText = new TextView(this);
        profileLoginStatusText.setTextSize(15);
        profileLoginStatusText.setTextColor(0xE6FFFFFF); // %90 opaklık (orta kontrast)
        profileLoginStatusText.setPadding(0, 0, 0, 20);
        profileLoginStatusText.setLineSpacing(4, 1.2f);
        
        // E-posta input
        profileEmailLabel = new TextView(this);
        profileEmailLabel.setText("E-posta Adresi");
        profileEmailLabel.setTextSize(14);
        profileEmailLabel.setTextColor(0xFFB0B0B0);
        profileEmailLabel.setPadding(0, 0, 0, 8);
        loginCard.addView(profileEmailLabel);
        
        profileEmailInput = new EditText(this);
        profileEmailInput.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        profileEmailInput.setHint("ornek@email.com");
        profileEmailInput.setTextSize(16);
        profileEmailInput.setPadding(16, 16, 16, 16);
        android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
        inputBg.setColor(0xFF1E1E1E);
        inputBg.setCornerRadius(8);
        inputBg.setStroke(1, 0xFF404040);
        profileEmailInput.setBackground(inputBg);
        profileEmailInput.setTextColor(0xFFFFFFFF);
        profileEmailInput.setHintTextColor(0xAAFFFFFF);
        LinearLayout.LayoutParams emailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        emailParams.setMargins(0, 0, 0, 16);
        loginCard.addView(profileEmailInput, emailParams);
        
        // Kod input - Her zaman görünür
        profileCodeLabel = new TextView(this);
        profileCodeLabel.setText("Doğrulama Kodu");
        profileCodeLabel.setTextSize(14);
        profileCodeLabel.setTextColor(0xFFB0B0B0);
        profileCodeLabel.setPadding(0, 0, 0, 8);
        loginCard.addView(profileCodeLabel);
        
        profileCodeInput = new EditText(this);
        profileCodeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        profileCodeInput.setHint("123456");
        profileCodeInput.setTextSize(16);
        profileCodeInput.setPadding(16, 16, 16, 16);
        profileCodeInput.setBackground(inputBg);
        profileCodeInput.setTextColor(0xFFFFFFFF);
        profileCodeInput.setHintTextColor(0xAAFFFFFF);
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        codeParams.setMargins(0, 0, 0, 16);
        loginCard.addView(profileCodeInput, codeParams);
        
        // Butonlar container
        profileButtonsContainer = new LinearLayout(this);
        profileButtonsContainer.setOrientation(LinearLayout.HORIZONTAL);
        profileButtonsContainer.setPadding(0, 0, 0, 0);
        
        // Kod gönder butonu
        profileSendCodeButton = new Button(this);
        profileSendCodeButton.setText("Kod Gönder");
        profileSendCodeButton.setTextSize(14);
        profileSendCodeButton.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable sendBtnBg = new android.graphics.drawable.GradientDrawable();
        sendBtnBg.setColor(0xFF3DAEA8);
        sendBtnBg.setCornerRadius(8);
        profileSendCodeButton.setBackground(sendBtnBg);
        profileSendCodeButton.setPadding(20, 14, 20, 14);
        LinearLayout.LayoutParams sendBtnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        sendBtnParams.setMargins(0, 0, 8, 0);
        profileButtonsContainer.addView(profileSendCodeButton, sendBtnParams);
        
        // Giriş yap butonu
        profileLoginButton = new Button(this);
        profileLoginButton.setText("Giriş Yap");
        profileLoginButton.setTextSize(14);
        profileLoginButton.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable loginBtnBg = new android.graphics.drawable.GradientDrawable();
        loginBtnBg.setColor(0xFF4CAF50);
        loginBtnBg.setCornerRadius(8);
        profileLoginButton.setBackground(loginBtnBg);
        profileLoginButton.setPadding(20, 14, 20, 14);
        LinearLayout.LayoutParams loginBtnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        loginBtnParams.setMargins(8, 0, 0, 0);
        profileButtonsContainer.addView(profileLoginButton, loginBtnParams);
        
        loginCard.addView(profileLoginStatusText);
        loginCard.addView(profileButtonsContainer);
        
        // Çıkış yap butonu (ayrı container - giriş yapıldıktan sonra görünecek)
        profileLogoutButton = new Button(this);
        profileLogoutButton.setText("Çıkış Yap");
        profileLogoutButton.setTextSize(14);
        profileLogoutButton.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable logoutBtnBg = new android.graphics.drawable.GradientDrawable();
        logoutBtnBg.setColor(0xFFFF5722);
        logoutBtnBg.setCornerRadius(8);
        profileLogoutButton.setBackground(logoutBtnBg);
        profileLogoutButton.setPadding(20, 14, 20, 14);
        profileLogoutButton.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams logoutBtnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        logoutBtnParams.setMargins(0, 16, 0, 8);
        loginCard.addView(profileLogoutButton, logoutBtnParams);
        
        // Platforma Gir butonu
        profilePlatformButton = new Button(this);
        profilePlatformButton.setText("Platforma Gir");
        profilePlatformButton.setTextSize(14);
        profilePlatformButton.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable platformBtnBg = new android.graphics.drawable.GradientDrawable();
        platformBtnBg.setColor(0xFF3DAEA8);
        platformBtnBg.setCornerRadius(8);
        profilePlatformButton.setBackground(platformBtnBg);
        profilePlatformButton.setPadding(20, 14, 20, 14);
        profilePlatformButton.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams platformBtnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        platformBtnParams.setMargins(0, 0, 0, 0);
        loginCard.addView(profilePlatformButton, platformBtnParams);
        
        // Platforma Gir butonu tıklama
        profilePlatformButton.setOnClickListener(v -> {
            if (profileApiService == null || !profileApiService.isLoggedIn()) {
                Toast.makeText(this, "Önce giriş yapmalısınız", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String token = profileApiService.getCarToken();
            if (token == null || token.isEmpty()) {
                Toast.makeText(this, "Token bulunamadı", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String platformUrl = "https://user.vnoisy.dev/login?token=" + token;
            showPlatformQRDialog(platformUrl);
        });
        
        LinearLayout.LayoutParams loginCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        loginCardParams.setMargins(16, 0, 16, 32); // Ayarlardaki margin'ler
        profileTabContent.addView(loginCard, loginCardParams);
        
        // Giriş durumunu güncelle
        updateProfileLoginStatus();
        
        // Kod gönder butonu
        profileSendCodeButton.setOnClickListener(v -> {
            String email = profileEmailInput.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, "E-posta adresi gerekli", Toast.LENGTH_SHORT).show();
                return;
            }
            
            profileSendCodeButton.setEnabled(false);
            profileSendCodeButton.setText("Gönderiliyor...");
            
            if (profileApiService != null) {
                profileApiService.sendVerificationCode(email, new ProfileApiService.ApiCallback() {
                    @Override
                    public void onSuccess(String message, JSONObject data) {
                        handler.post(() -> {
                            profileSendCodeButton.setEnabled(true);
                            profileSendCodeButton.setText("Kod Gönderildi");
                            Toast.makeText(MainActivity.this, "✅ " + message, Toast.LENGTH_SHORT).show();
                            log("Doğrulama kodu gönderildi: " + email);
                            
                            // 3 saniye sonra buton metnini geri al
                            handler.postDelayed(() -> {
                                profileSendCodeButton.setText("📧 Kod Gönder");
                            }, 3000);
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        handler.post(() -> {
                            profileSendCodeButton.setEnabled(true);
                            profileSendCodeButton.setText("Kod Gönder");
                            Toast.makeText(MainActivity.this, "❌ " + error, Toast.LENGTH_SHORT).show();
                            log("Kod gönderme hatası: " + error);
                        });
                    }
                });
            }
        });
        
        // Giriş yap butonu
        profileLoginButton.setOnClickListener(v -> {
            String email = profileEmailInput.getText().toString().trim();
            String code = profileCodeInput.getText().toString().trim();
            
            if (email.isEmpty()) {
                Toast.makeText(this, "E-posta adresi gerekli", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (code.isEmpty()) {
                Toast.makeText(this, "Doğrulama kodu gerekli", Toast.LENGTH_SHORT).show();
                return;
            }
            
            profileLoginButton.setEnabled(false);
            profileLoginButton.setText("Doğrulanıyor...");
            
            if (profileApiService != null) {
                profileApiService.verifyCode(email, code, new ProfileApiService.ApiCallback() {
                    @Override
                    public void onSuccess(String message, JSONObject data) {
                        handler.post(() -> {
                            profileLoginButton.setEnabled(true);
                            profileLoginButton.setText("Giriş Yap");
                            updateProfileLoginStatus();
                            Toast.makeText(MainActivity.this, "✅ " + message, Toast.LENGTH_SHORT).show();
                            log("Giriş başarılı: " + email);
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        handler.post(() -> {
                            profileLoginButton.setEnabled(true);
                            profileLoginButton.setText("Giriş Yap");
                            Toast.makeText(MainActivity.this, "❌ " + error, Toast.LENGTH_SHORT).show();
                            log("Giriş hatası: " + error);
                        });
                    }
                });
            }
        });
        
        // Çıkış butonu
        profileLogoutButton.setOnClickListener(v -> {
            if (profileApiService != null) {
                profileApiService.clearToken();
                profileEmailInput.setText("");
                profileCodeInput.setText("");
                updateProfileLoginStatus();
                Toast.makeText(this, "Çıkış yapıldı", Toast.LENGTH_SHORT).show();
                log("Kullanıcı çıkış yaptı");
            }
        });
        
        // İşlemler kartı - Giriş yapıldıktan sonra görünür (Ayarlardaki tasarıma uygun)
        LinearLayout actionsCard = new LinearLayout(this);
        actionsCard.setOrientation(LinearLayout.VERTICAL);
        actionsCard.setPadding(20, 20, 20, 20);
        android.graphics.drawable.GradientDrawable actionsCardBg = new android.graphics.drawable.GradientDrawable();
        actionsCardBg.setColor(0xFF151C24); // Ayarlardaki kart rengi
        actionsCardBg.setCornerRadius(12);
        actionsCardBg.setStroke(1, 0xFF2A3A47); // Ayarlardaki border rengi
        actionsCard.setBackground(actionsCardBg);
        
        TextView actionsTitle = new TextView(this);
        actionsTitle.setText("Veri Yönetimi");
        actionsTitle.setTextSize(17);
        actionsTitle.setTextColor(0xFFFFFFFF);
        actionsTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
        actionsTitle.setPadding(0, 0, 0, 16);
        actionsCard.addView(actionsTitle);
        
        // Butonlar için horizontal container
        LinearLayout buttonsRow = new LinearLayout(this);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setPadding(0, 0, 0, 0);
        
        // Son değişiklikleri sakla butonu
        profileSaveDataButton = new Button(this);
        profileSaveDataButton.setText("Son Değişiklikleri Sakla");
        profileSaveDataButton.setTextSize(14);
        profileSaveDataButton.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable saveBtnBg = new android.graphics.drawable.GradientDrawable();
        saveBtnBg.setColor(0xFF3DAEA8);
        saveBtnBg.setCornerRadius(8);
        profileSaveDataButton.setBackground(saveBtnBg);
        profileSaveDataButton.setPadding(16, 14, 16, 14);
        profileSaveDataButton.setEnabled(profileApiService != null && profileApiService.isLoggedIn());
        profileSaveDataButton.setVisibility(android.view.View.VISIBLE);
        
        LinearLayout.LayoutParams saveBtnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        saveBtnParams.setMargins(0, 0, 8, 0);
        buttonsRow.addView(profileSaveDataButton, saveBtnParams);
        
        // Son değişiklikleri getir butonu
        profileLoadDataButton = new Button(this);
        profileLoadDataButton.setText("Son Değişiklikleri Getir");
        profileLoadDataButton.setTextSize(14);
        profileLoadDataButton.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable loadBtnBg = new android.graphics.drawable.GradientDrawable();
        loadBtnBg.setColor(0xFF4CAF50);
        loadBtnBg.setCornerRadius(8);
        profileLoadDataButton.setBackground(loadBtnBg);
        profileLoadDataButton.setPadding(16, 14, 16, 14);
        profileLoadDataButton.setEnabled(profileApiService != null && profileApiService.isLoggedIn());
        profileLoadDataButton.setVisibility(android.view.View.VISIBLE);
        
        LinearLayout.LayoutParams loadBtnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        loadBtnParams.setMargins(8, 0, 0, 0);
        buttonsRow.addView(profileLoadDataButton, loadBtnParams);
        
        // Butonlar container'ını actionsCard'a ekle
        LinearLayout.LayoutParams buttonsRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsRowParams.setMargins(0, 0, 0, 16);
        actionsCard.addView(buttonsRow, buttonsRowParams);
        
        profileSaveDataButton.setOnClickListener(v -> {
            if (profileApiService == null || !profileApiService.isLoggedIn()) {
                Toast.makeText(this, "Önce giriş yapmalısınız", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // İnternet bağlantısı kontrolü
            if (!isNetworkAvailable()) {
                Toast.makeText(this, "İnternet bağlantısı yok", Toast.LENGTH_SHORT).show();
                log("İnternet bağlantısı yok, veri kaydedilemedi");
                return;
            }
            
            profileSaveDataButton.setEnabled(false);
            profileSaveDataButton.setText("Kaydediliyor...");
            
            // SharedPreferences verilerini topla
            try {
                SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
                JSONObject data = new JSONObject();
                
                // Boolean değerler
                data.put("disclaimerAccepted", prefs.getBoolean("disclaimerAccepted", false));
                data.put("appManagementDisclaimerAccepted", prefs.getBoolean("appManagementDisclaimerAccepted", false));
                data.put("mapControlKeyEnabled", prefs.getBoolean("mapControlKeyEnabled", true));
                data.put("autoCloseOnPowerOff", prefs.getBoolean("autoCloseOnPowerOff", true));
                
                // Integer değerler
                data.put("powerModeSetting", prefs.getInt("powerModeSetting", 2));
                data.put("driveModeSetting", prefs.getInt("driveModeSetting", -1));
                data.put("issSetting", prefs.getInt("issSetting", -1));
                data.put("ldwSetting", prefs.getInt("ldwSetting", -1));
                data.put("spdLimitSetting", prefs.getInt("spdLimitSetting", -1));
                data.put("ldpSetting", prefs.getInt("ldpSetting", -1));
                data.put("fcwSetting", prefs.getInt("fcwSetting", -1));
                data.put("aebSetting", prefs.getInt("aebSetting", -1));
                
                // String değerler
                String targetPackage = prefs.getString("targetPackage", null);
                if (targetPackage != null) {
                    data.put("targetPackage", targetPackage);
                }
                
                // Bluetooth ismini al ve ekle
                String bluetoothName = getBluetoothName();
                if (bluetoothName != null && !bluetoothName.isEmpty()) {
                    data.put("bluetoothName", bluetoothName);
                }
                
                profileApiService.saveUserData(data, new ProfileApiService.ApiCallback() {
                    @Override
                    public void onSuccess(String message, JSONObject data) {
                        handler.post(() -> {
                            profileSaveDataButton.setEnabled(true);
                            profileSaveDataButton.setText("Son Değişiklikleri Sakla");
                            Toast.makeText(MainActivity.this, "✅ " + message, Toast.LENGTH_SHORT).show();
                            log("Veriler başarıyla kaydedildi: " + message);
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        handler.post(() -> {
                            profileSaveDataButton.setEnabled(true);
                            profileSaveDataButton.setText("Son Değişiklikleri Sakla");
                            Toast.makeText(MainActivity.this, "❌ " + error, Toast.LENGTH_SHORT).show();
                            log("Veri kaydetme hatası: " + error);
                        });
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    profileSaveDataButton.setEnabled(true);
                    profileSaveDataButton.setText(" on Değişiklikleri Sakla");
                    Toast.makeText(this, "❌ Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    log("Veri kaydetme hatası: " + e.getMessage());
                });
            }
        });
        
        // Son değişiklikleri getir butonu tıklama
        profileLoadDataButton.setOnClickListener(v -> {
            if (profileApiService == null || !profileApiService.isLoggedIn()) {
                Toast.makeText(this, "Önce giriş yapmalısınız", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // İnternet bağlantısı kontrolü
            if (!isNetworkAvailable()) {
                Toast.makeText(this, "İnternet bağlantısı yok", Toast.LENGTH_SHORT).show();
                log("İnternet bağlantısı yok, veri getirilemedi");
                return;
            }
            
            // Önce onay dialog'u göster
            AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(this);
            confirmBuilder.setTitle("Verileri Çek");
            confirmBuilder.setMessage("Sunucudan son kayıtlı verilerinizi çekmek istiyor musunuz?\n\nNot: Mevcut ayarlarınızın üzerine yazılacaktır.");
            confirmBuilder.setPositiveButton("Evet, Çek", (dialog, which) -> {
                // Kullanıcı onayladı, verileri çek
                profileLoadDataButton.setEnabled(false);
                profileLoadDataButton.setText("Yükleniyor...");
                
                profileApiService.getUserData(new ProfileApiService.ApiCallback() {
                @Override
                public void onSuccess(String message, JSONObject responseData) {
                    handler.post(() -> {
                        try {
                            // Sunucudan gelen data objesini al
                            JSONObject data = responseData;
                            if (data == null) {
                                Toast.makeText(MainActivity.this, "❌ Veri bulunamadı", Toast.LENGTH_SHORT).show();
                                log("Sunucudan veri alınamadı: data objesi yok");
                                profileLoadDataButton.setEnabled(true);
                                profileLoadDataButton.setText("Son Değişiklikleri Getir");
                                return;
                            }
                            
                            // SharedPreferences'a kaydet (Boolean değerler hariç)
                            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            
                            // Integer değerler
                            if (data.has("powerModeSetting")) {
                                editor.putInt("powerModeSetting", data.getInt("powerModeSetting"));
                            }
                            if (data.has("driveModeSetting")) {
                                editor.putInt("driveModeSetting", data.getInt("driveModeSetting"));
                            }
                            if (data.has("issSetting")) {
                                editor.putInt("issSetting", data.getInt("issSetting"));
                            }
                            if (data.has("ldwSetting")) {
                                editor.putInt("ldwSetting", data.getInt("ldwSetting"));
                            }
                            if (data.has("spdLimitSetting")) {
                                editor.putInt("spdLimitSetting", data.getInt("spdLimitSetting"));
                            }
                            if (data.has("ldpSetting")) {
                                editor.putInt("ldpSetting", data.getInt("ldpSetting"));
                            }
                            if (data.has("fcwSetting")) {
                                editor.putInt("fcwSetting", data.getInt("fcwSetting"));
                            }
                            if (data.has("aebSetting")) {
                                editor.putInt("aebSetting", data.getInt("aebSetting"));
                            }
                            
                            // String değerler
                            if (data.has("targetPackage")) {
                                String targetPackage = data.getString("targetPackage");
                                if (targetPackage != null && !targetPackage.isEmpty()) {
                                    editor.putString("targetPackage", targetPackage);
                                }
                            }
                            
                            // Boolean değerler hariç tutuldu (kullanıcı isteği)
                            
                            editor.apply();
                            
                            log("Sunucudan veriler başarıyla yüklendi ve SharedPreferences'a kaydedildi");
                            
                            // Yeniden başlatma dialog'unu göster
                            AlertDialog.Builder restartBuilder = new AlertDialog.Builder(MainActivity.this);
                            restartBuilder.setTitle("🔄 Uygulama Yeniden Başlatılacak");
                            restartBuilder.setMessage("Son kayıtlı verileriniz başarıyla yüklendi.\n\nAyarların etkin olması için uygulamanın yeniden başlatılması gerekiyor.\n\nYeniden başlatmak istiyor musunuz?");
                            restartBuilder.setPositiveButton("Yeniden Başlat", (dialog, which) -> {
                                // Uygulamayı yeniden başlat
                                Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                                if (intent != null) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(intent);
                                    finish();
                                    // Process'i sonlandır
                                    android.os.Process.killProcess(android.os.Process.myPid());
                                } else {
                                    Toast.makeText(MainActivity.this, "❌ Uygulama yeniden başlatılamadı", Toast.LENGTH_SHORT).show();
                                }
                            });
                            restartBuilder.setNegativeButton("İptal", (dialog, which) -> {
                                dialog.dismiss();
                                Toast.makeText(MainActivity.this, "ℹ️ Veriler yüklendi, uygulamayı manuel olarak yeniden başlatabilirsiniz", Toast.LENGTH_LONG).show();
                            });
                            restartBuilder.setCancelable(false);
                            restartBuilder.show();
                            
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "❌ Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            log("Veri yükleme hatası: " + e.getMessage());
                        } finally {
                            profileLoadDataButton.setEnabled(true);
                            profileLoadDataButton.setText("Son Değişiklikleri Getir");
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    handler.post(() -> {
                        profileLoadDataButton.setEnabled(true);
                        profileLoadDataButton.setText("Son Değişiklikleri Getir");
                        Toast.makeText(MainActivity.this, "❌ " + error, Toast.LENGTH_SHORT).show();
                        log("Veri getirme hatası: " + error);
                    });
                }
                });
            });
            confirmBuilder.setNegativeButton("İptal", (dialog, which) -> {
                dialog.dismiss();
            });
            confirmBuilder.setCancelable(true);
            confirmBuilder.show();
        });
        
        // Konum kaydet butonu
        profileSaveLocationButton = new Button(this);
        profileSaveLocationButton.setText("📍 Mevcut Konumu Kaydet");
        profileSaveLocationButton.setTextSize(16);
        profileSaveLocationButton.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable locationBtnBg = new android.graphics.drawable.GradientDrawable();
        locationBtnBg.setColor(0xFF3DAEA8);
        locationBtnBg.setCornerRadius(8);
        profileSaveLocationButton.setBackground(locationBtnBg);
        profileSaveLocationButton.setPadding(24, 18, 24, 18);
        profileSaveLocationButton.setEnabled(profileApiService != null && profileApiService.isLoggedIn());
        
        LinearLayout.LayoutParams locationBtnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        locationBtnParams.setMargins(0, 0, 0, 16);
        actionsCard.addView(profileSaveLocationButton, locationBtnParams);
        
        // Otomatik konum kaydetme RadioGroup (Ayarlardaki gibi)
        // Alt bölüm başlığı (orta kontrast)
        TextView autoLocationTitle = new TextView(this);
        autoLocationTitle.setText("Araç Kapanınca Otomatik Konum Kaydet");
        autoLocationTitle.setTextSize(15);
        autoLocationTitle.setTextColor(0xE6FFFFFF); // %90 opaklık (orta kontrast)
        autoLocationTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
        autoLocationTitle.setPadding(0, 0, 0, 8);
        actionsCard.addView(autoLocationTitle);
        
        // Alt bölüm açıklaması
        TextView autoLocationDesc = new TextView(this);
        autoLocationDesc.setText("Araç kapanınca ne olsun?");
        autoLocationDesc.setTextSize(13);
        autoLocationDesc.setTextColor(0xAAFFFFFF); // %67 opaklık
        autoLocationDesc.setPadding(0, 0, 0, 12);
        actionsCard.addView(autoLocationDesc);
        
        profileAutoLocationRadioGroup = new RadioGroup(this);
        profileAutoLocationRadioGroup.setOrientation(LinearLayout.VERTICAL);
        profileAutoLocationRadioGroup.setPadding(20, 0, 20, 0); // Ayarlardaki padding
        
        // RadioButton 1: Evet
        LinearLayout autoLocationOption1Container = new LinearLayout(this);
        autoLocationOption1Container.setOrientation(LinearLayout.HORIZONTAL);
        autoLocationOption1Container.setPadding(16, 16, 16, 16);
        autoLocationOption1Container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        autoLocationOption1Container.setClickable(true);
        autoLocationOption1Container.setFocusable(true);
        
        profileAutoLocationIconCircle1 = new LinearLayout(this);
        profileAutoLocationIconCircle1.setOrientation(LinearLayout.VERTICAL);
        profileAutoLocationIconCircle1.setBackgroundColor(0xFF1A2330);
        profileAutoLocationIconCircle1.setGravity(android.view.Gravity.CENTER);
        profileAutoLocationIconCircle1.setPadding(10, 10, 10, 10);
        profileAutoLocationIconCircle1.setId(android.view.View.generateViewId());
        
        TextView autoLocationIcon1 = new TextView(this);
        autoLocationIcon1.setText("✅");
        autoLocationIcon1.setTextSize(20);
        autoLocationIcon1.setTextColor(0xFFFFFFFF);
        profileAutoLocationIconCircle1.addView(autoLocationIcon1);
        
        LinearLayout.LayoutParams autoLocationIconCircle1Params = new LinearLayout.LayoutParams(48, 48);
        autoLocationIconCircle1Params.setMargins(0, 0, 12, 0);
        autoLocationOption1Container.addView(profileAutoLocationIconCircle1, autoLocationIconCircle1Params);
        
        LinearLayout autoLocationTextColumn1 = new LinearLayout(this);
        autoLocationTextColumn1.setOrientation(LinearLayout.VERTICAL);
        
        TextView autoLocationTitle1 = new TextView(this);
        autoLocationTitle1.setText("Açık");
        autoLocationTitle1.setTextColor(0xFFFFFFFF);
        autoLocationTitle1.setTextSize(16);
        autoLocationTitle1.setTypeface(null, android.graphics.Typeface.NORMAL);
        autoLocationTextColumn1.addView(autoLocationTitle1);
        
        TextView autoLocationDesc1 = new TextView(this);
        autoLocationDesc1.setText("Araç kapanınca otomatik konum kaydet");
        autoLocationDesc1.setTextColor(0xAAFFFFFF);
        autoLocationDesc1.setTextSize(13);
        autoLocationDesc1.setPadding(0, 2, 0, 0);
        autoLocationTextColumn1.addView(autoLocationDesc1);
        
        LinearLayout.LayoutParams autoLocationTextParams1 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        autoLocationOption1Container.addView(autoLocationTextColumn1, autoLocationTextParams1);
        
        RadioButton autoLocationRadio1 = new RadioButton(this);
        autoLocationRadio1.setId(300); // 300 = Açık
        autoLocationRadio1.setButtonDrawable(null);
        autoLocationRadio1.setBackground(null);
        autoLocationRadio1.setPadding(0, 0, 0, 0);
        autoLocationRadio1.setClickable(true);
        autoLocationRadio1.setEnabled(true);
        autoLocationOption1Container.addView(autoLocationRadio1);
        
        autoLocationOption1Container.setOnClickListener(v -> {
            if (profileAutoLocationRadioGroup.isEnabled()) {
                profileAutoLocationRadioGroup.check(300);
            }
        });
        profileAutoLocationRadioGroup.addView(autoLocationOption1Container);
        
        // RadioButton 2: Hayır
        LinearLayout autoLocationOption2Container = new LinearLayout(this);
        autoLocationOption2Container.setOrientation(LinearLayout.HORIZONTAL);
        autoLocationOption2Container.setPadding(16, 16, 16, 16);
        autoLocationOption2Container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        autoLocationOption2Container.setClickable(true);
        autoLocationOption2Container.setFocusable(true);
        
        profileAutoLocationIconCircle2 = new LinearLayout(this);
        profileAutoLocationIconCircle2.setOrientation(LinearLayout.VERTICAL);
        profileAutoLocationIconCircle2.setBackgroundColor(0xFF1A2330);
        profileAutoLocationIconCircle2.setGravity(android.view.Gravity.CENTER);
        profileAutoLocationIconCircle2.setPadding(10, 10, 10, 10);
        profileAutoLocationIconCircle2.setId(android.view.View.generateViewId());
        
        TextView autoLocationIcon2 = new TextView(this);
        autoLocationIcon2.setText("❌");
        autoLocationIcon2.setTextSize(20);
        autoLocationIcon2.setTextColor(0xFFFFFFFF);
        profileAutoLocationIconCircle2.addView(autoLocationIcon2);
        
        LinearLayout.LayoutParams autoLocationIconCircle2Params = new LinearLayout.LayoutParams(48, 48);
        autoLocationIconCircle2Params.setMargins(0, 0, 12, 0);
        autoLocationOption2Container.addView(profileAutoLocationIconCircle2, autoLocationIconCircle2Params);
        
        LinearLayout autoLocationTextColumn2 = new LinearLayout(this);
        autoLocationTextColumn2.setOrientation(LinearLayout.VERTICAL);
        
        TextView autoLocationTitle2 = new TextView(this);
        autoLocationTitle2.setText("Kapalı");
        autoLocationTitle2.setTextColor(0xFFFFFFFF);
        autoLocationTitle2.setTextSize(16);
        autoLocationTitle2.setTypeface(null, android.graphics.Typeface.NORMAL);
        autoLocationTextColumn2.addView(autoLocationTitle2);
        
        TextView autoLocationDesc2 = new TextView(this);
        autoLocationDesc2.setText("Otomatik konum kaydetme kapalı");
        autoLocationDesc2.setTextColor(0xAAFFFFFF);
        autoLocationDesc2.setTextSize(13);
        autoLocationDesc2.setPadding(0, 2, 0, 0);
        autoLocationTextColumn2.addView(autoLocationDesc2);
        
        LinearLayout.LayoutParams autoLocationTextParams2 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        autoLocationOption2Container.addView(autoLocationTextColumn2, autoLocationTextParams2);
        
        RadioButton autoLocationRadio2 = new RadioButton(this);
        autoLocationRadio2.setId(301); // 301 = Kapalı
        autoLocationRadio2.setButtonDrawable(null);
        autoLocationRadio2.setBackground(null);
        autoLocationRadio2.setPadding(0, 0, 0, 0);
        autoLocationOption2Container.addView(autoLocationRadio2);
        
        autoLocationOption2Container.setOnClickListener(v -> {
            if (profileAutoLocationRadioGroup.isEnabled()) {
                profileAutoLocationRadioGroup.check(301);
            }
        });
        profileAutoLocationRadioGroup.addView(autoLocationOption2Container);
        
        // RadioGroup listener
        final LinearLayout[] autoLocationIconCircles = {profileAutoLocationIconCircle1, profileAutoLocationIconCircle2};
        profileAutoLocationRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isEnabled = (checkedId == 300); // 300 = Açık, 301 = Kapalı
            SharedPreferences.Editor editor = getSharedPreferences("MapControlPrefs", MODE_PRIVATE).edit();
            editor.putBoolean("autoLocationSaveOnPowerOff", isEnabled);
            editor.apply();
            
            if (isEnabled) {
                log("Otomatik konum kaydetme açıldı");
            } else {
                log("Otomatik konum kaydetme kapatıldı");
            }
            
            // Seçili durumda accent rengi
            for (int i = 0; i < autoLocationIconCircles.length; i++) {
                LinearLayout iconCircle = autoLocationIconCircles[i];
                if (iconCircle != null) {
                    if ((isEnabled && i == 0) || (!isEnabled && i == 1)) {
                        // Seçili: accent rengi
                        iconCircle.setBackgroundColor(0xFF3DAEA8);
                    } else {
                        // Seçili değil: sade
                        iconCircle.setBackgroundColor(0xFF1A2330);
                    }
                }
            }
        });
        
        // Kaydedilmiş ayarı yükle
        SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
        boolean autoLocationEnabled = prefs.getBoolean("autoLocationSaveOnPowerOff", false);
        boolean isLoggedIn = profileApiService != null && profileApiService.isLoggedIn();
        profileAutoLocationRadioGroup.setEnabled(isLoggedIn);
        // Child'ları da aktif/pasif yap
        for (int i = 0; i < profileAutoLocationRadioGroup.getChildCount(); i++) {
            android.view.View child = profileAutoLocationRadioGroup.getChildAt(i);
            if (child instanceof LinearLayout) {
                child.setEnabled(isLoggedIn);
                child.setClickable(isLoggedIn);
                child.setFocusable(isLoggedIn);
                child.setAlpha(isLoggedIn ? 1.0f : 0.5f);
                // İçindeki RadioButton'ı da aktif/pasif yap
                for (int j = 0; j < ((LinearLayout) child).getChildCount(); j++) {
                    android.view.View innerChild = ((LinearLayout) child).getChildAt(j);
                    if (innerChild instanceof RadioButton) {
                        innerChild.setEnabled(isLoggedIn);
                        innerChild.setClickable(isLoggedIn);
                    }
                }
            }
        }
        handler.post(() -> {
            profileAutoLocationRadioGroup.check(autoLocationEnabled ? 300 : 301);
            // İlk yüklemede seçili durumu göster
            if (autoLocationEnabled) {
                profileAutoLocationIconCircle1.setBackgroundColor(0xFF3DAEA8);
                profileAutoLocationIconCircle2.setBackgroundColor(0xFF1A2330);
            } else {
                profileAutoLocationIconCircle1.setBackgroundColor(0xFF1A2330);
                profileAutoLocationIconCircle2.setBackgroundColor(0xFF3DAEA8);
            }
        });
        
        LinearLayout.LayoutParams autoLocationRadioGroupParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        autoLocationRadioGroupParams.setMargins(0, 0, 0, 0);
        actionsCard.addView(profileAutoLocationRadioGroup, autoLocationRadioGroupParams);
        
        LinearLayout.LayoutParams actionsCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsCardParams.setMargins(16, 0, 16, 32); // Ayarlardaki margin'ler
        profileTabContent.addView(actionsCard, actionsCardParams);
        
        // Giriş durumuna göre kartı göster/gizle
        if (profileApiService != null && profileApiService.isLoggedIn()) {
            actionsCard.setVisibility(android.view.View.VISIBLE);
        } else {
            actionsCard.setVisibility(android.view.View.GONE);
        }
        
        profileSaveLocationButton.setOnClickListener(v -> {
            if (profileApiService == null || !profileApiService.isLoggedIn()) {
                Toast.makeText(this, "Önce giriş yapmalısınız", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // İnternet bağlantısı kontrolü
            if (!isNetworkAvailable()) {
                Toast.makeText(this, "İnternet bağlantısı yok", Toast.LENGTH_SHORT).show();
                log("İnternet bağlantısı yok, konum kaydedilemedi");
                return;
            }
            
            profileSaveLocationButton.setEnabled(false);
            profileSaveLocationButton.setText("Konum alınıyor...");
            
            // Konum izni kontrolü
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
                profileSaveLocationButton.setEnabled(true);
                profileSaveLocationButton.setText("📍 Mevcut Konumu Kaydet");
                Toast.makeText(this, "Konum izni gerekli", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // LocationManager'ı başlat
            if (locationManager == null) {
                locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            }
            
            // Mevcut konumu al
            try {
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation == null) {
                    lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                
                if (lastLocation != null) {
                    double latitude = lastLocation.getLatitude();
                    double longitude = lastLocation.getLongitude();
                    
                    profileApiService.saveCoordinates(latitude, longitude, "Current Location", "Mevcut konum", new ProfileApiService.ApiCallback() {
                        @Override
                        public void onSuccess(String message, JSONObject data) {
                            handler.post(() -> {
                                profileSaveLocationButton.setEnabled(true);
                                profileSaveLocationButton.setText("Mevcut Konumu Kaydet");
                                Toast.makeText(MainActivity.this, "✅ " + message, Toast.LENGTH_SHORT).show();
                                log("Konum kaydedildi: " + latitude + ", " + longitude);
                            });
                        }
                        
                        @Override
                        public void onError(String error) {
                            handler.post(() -> {
                                profileSaveLocationButton.setEnabled(true);
                                profileSaveLocationButton.setText("Mevcut Konumu Kaydet");
                                Toast.makeText(MainActivity.this, "❌ " + error, Toast.LENGTH_SHORT).show();
                                log("Konum kaydetme hatası: " + error);
                            });
                        }
                    });
                } else {
                    // Konum alınamadı, GPS'ten almayı dene
                    profileSaveLocationButton.setText("Konum bekleniyor...");
                    locationListener = new LocationListener() {
                        @Override
                        public void onLocationChanged(Location location) {
                            if (location != null && profileApiService != null) {
                                double latitude = location.getLatitude();
                                double longitude = location.getLongitude();
                                
                                profileApiService.saveCoordinates(latitude, longitude, "Current Location", "Mevcut konum", new ProfileApiService.ApiCallback() {
                                    @Override
                                    public void onSuccess(String message, JSONObject data) {
                                        handler.post(() -> {
                                            profileSaveLocationButton.setEnabled(true);
                                            profileSaveLocationButton.setText("Mevcut Konumu Kaydet");
                                            Toast.makeText(MainActivity.this, "✅ " + message, Toast.LENGTH_SHORT).show();
                                            log("Konum kaydedildi: " + latitude + ", " + longitude);
                                        });
                                    }
                                    
                                    @Override
                                    public void onError(String error) {
                                        handler.post(() -> {
                                            profileSaveLocationButton.setEnabled(true);
                                            profileSaveLocationButton.setText("Mevcut Konumu Kaydet");
                                            Toast.makeText(MainActivity.this, "❌ " + error, Toast.LENGTH_SHORT).show();
                                            log("Konum kaydetme hatası: " + error);
                                        });
                                    }
                                });
                                
                                // Listener'ı kaldır
                                if (locationManager != null && locationListener != null) {
                                    locationManager.removeUpdates(locationListener);
                                }
                            }
                        }
                        
                        @Override
                        public void onStatusChanged(String provider, int status, Bundle extras) {}
                        
                        @Override
                        public void onProviderEnabled(String provider) {}
                        
                        @Override
                        public void onProviderDisabled(String provider) {}
                    };
                    
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                    
                    // 10 saniye sonra timeout
                    handler.postDelayed(() -> {
                        if (locationManager != null && locationListener != null) {
                            locationManager.removeUpdates(locationListener);
                        }
                        profileSaveLocationButton.setEnabled(true);
                        profileSaveLocationButton.setText("📍 Mevcut Konumu Kaydet");
                        Toast.makeText(this, "Konum alınamadı, lütfen GPS'in açık olduğundan emin olun", Toast.LENGTH_LONG).show();
                    }, 10000);
                }
            } catch (Exception e) {
                profileSaveLocationButton.setEnabled(true);
                profileSaveLocationButton.setText("📍 Mevcut Konumu Kaydet");
                Toast.makeText(this, "❌ Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                log("Konum alma hatası: " + e.getMessage());
            }
        });
        
        profileScrollView.addView(profileTabContent);
    }
    
    /**
     * Profil giriş durumunu güncelle
     */
    private void updateProfileLoginStatus() {
        if (profileLoginStatusText == null || profileApiService == null) {
            return;
        }
        
        boolean isLoggedIn = profileApiService.isLoggedIn();
        
        if (isLoggedIn) {
            String email = profileApiService.getUserEmail();
            profileLoginStatusText.setText("Giriş Mevcut\nE-posta: " + (email != null ? email : "Bilinmiyor"));
            
            // Giriş butonlarını ve input'ları gizle
            if (profileButtonsContainer != null) {
                profileButtonsContainer.setVisibility(android.view.View.GONE);
            }
            if (profileEmailLabel != null) {
                profileEmailLabel.setVisibility(android.view.View.GONE);
            }
            if (profileEmailInput != null) {
                profileEmailInput.setVisibility(android.view.View.GONE);
            }
            if (profileCodeLabel != null) {
                profileCodeLabel.setVisibility(android.view.View.GONE);
            }
            if (profileCodeInput != null) {
                profileCodeInput.setVisibility(android.view.View.GONE);
            }
            
            // Çıkış ve Platforma Gir butonlarını göster
            if (profileLogoutButton != null) {
                profileLogoutButton.setVisibility(android.view.View.VISIBLE);
            }
            if (profilePlatformButton != null) {
                profilePlatformButton.setVisibility(android.view.View.VISIBLE);
            }
            
            // Butonları aktif et
            if (profileSaveDataButton != null) {
                profileSaveDataButton.setEnabled(true);
            }
            if (profileLoadDataButton != null) {
                profileLoadDataButton.setEnabled(true);
            }
            if (profileSaveLocationButton != null) {
                profileSaveLocationButton.setEnabled(true);
            }
            if (profileAutoLocationRadioGroup != null) {
                profileAutoLocationRadioGroup.setEnabled(true);
                // RadioGroup içindeki tüm child'ları da aktif et
                for (int i = 0; i < profileAutoLocationRadioGroup.getChildCount(); i++) {
                    android.view.View child = profileAutoLocationRadioGroup.getChildAt(i);
                    if (child instanceof LinearLayout) {
                        child.setEnabled(true);
                        child.setClickable(true);
                        child.setFocusable(true);
                        child.setAlpha(1.0f);
                        // İçindeki RadioButton'ı da aktif et
                        for (int j = 0; j < ((LinearLayout) child).getChildCount(); j++) {
                            android.view.View innerChild = ((LinearLayout) child).getChildAt(j);
                            if (innerChild instanceof RadioButton) {
                                innerChild.setEnabled(true);
                                innerChild.setClickable(true);
                            }
                        }
                    }
                }
            }
            
            // İşlemler kartını göster
            if (profileTabContent != null) {
                for (int i = 0; i < profileTabContent.getChildCount(); i++) {
                    android.view.View child = profileTabContent.getChildAt(i);
                    if (child instanceof LinearLayout) {
                        LinearLayout card = (LinearLayout) child;
                        if (card.getChildCount() > 0 && card.getChildAt(0) instanceof TextView) {
                            TextView title = (TextView) card.getChildAt(0);
                            if (title.getText().toString().contains("Veri Yönetimi")) {
                                card.setVisibility(android.view.View.VISIBLE);
                                break;
                            }
                        }
                    }
                }
            }
        } else {
            profileLoginStatusText.setText("Ayarlarınızı kaydetmek için giriş yapın");
            
            // Giriş butonlarını ve input'ları göster
            if (profileButtonsContainer != null) {
                profileButtonsContainer.setVisibility(android.view.View.VISIBLE);
            }
            if (profileEmailLabel != null) {
                profileEmailLabel.setVisibility(android.view.View.VISIBLE);
            }
            if (profileEmailInput != null) {
                profileEmailInput.setVisibility(android.view.View.VISIBLE);
                profileEmailInput.setEnabled(true);
            }
            if (profileCodeLabel != null) {
                profileCodeLabel.setVisibility(android.view.View.VISIBLE);
            }
            if (profileCodeInput != null) {
                profileCodeInput.setVisibility(android.view.View.VISIBLE);
                profileCodeInput.setEnabled(true);
            }
            
            // Çıkış ve Platforma Gir butonlarını gizle
            profileLogoutButton.setVisibility(android.view.View.GONE);
            if (profilePlatformButton != null) {
                profilePlatformButton.setVisibility(android.view.View.GONE);
            }
            
            // Butonları pasif et
            if (profileSaveDataButton != null) {
                profileSaveDataButton.setEnabled(false);
            }
            if (profileLoadDataButton != null) {
                profileLoadDataButton.setEnabled(false);
            }
            if (profileSaveLocationButton != null) {
                profileSaveLocationButton.setEnabled(false);
            }
            if (profileAutoLocationRadioGroup != null) {
                profileAutoLocationRadioGroup.setEnabled(false);
                // RadioGroup içindeki tüm child'ları da pasif et
                for (int i = 0; i < profileAutoLocationRadioGroup.getChildCount(); i++) {
                    android.view.View child = profileAutoLocationRadioGroup.getChildAt(i);
                    if (child instanceof LinearLayout) {
                        child.setEnabled(false);
                        child.setAlpha(0.5f);
                    }
                }
            }
            
            // İşlemler kartını gizle
            if (profileTabContent != null) {
                for (int i = 0; i < profileTabContent.getChildCount(); i++) {
                    android.view.View child = profileTabContent.getChildAt(i);
                    if (child instanceof LinearLayout) {
                        LinearLayout card = (LinearLayout) child;
                        if (card.getChildCount() > 0 && card.getChildAt(0) instanceof TextView) {
                            TextView title = (TextView) card.getChildAt(0);
                            if (title.getText().toString().contains("Veri Yönetimi")) {
                                card.setVisibility(android.view.View.GONE);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Platforma Gir QR kod dialog'unu göster
     */
    private void showPlatformQRDialog(String url) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Platforma Gir");
        
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(32, 32, 32, 32);
        dialogLayout.setGravity(android.view.Gravity.CENTER);
        
        // QR kod ImageView
        android.widget.ImageView qrImageView = new android.widget.ImageView(this);
        qrImageView.setId(android.view.View.generateViewId());
        qrImageView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        qrImageView.setAdjustViewBounds(true);
        
        // QR kod boyutu
        int qrSize = (int)(300 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.gravity = android.view.Gravity.CENTER;
        qrParams.setMargins(0, 0, 0, 0);
        dialogLayout.addView(qrImageView, qrParams);
        
        builder.setView(dialogLayout);
        builder.setPositiveButton("Kapat", null);
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // QR kod oluştur
        new Thread(() -> {
            try {
                QRCodeWriter qrCodeWriter = new QRCodeWriter();
                java.util.Map<EncodeHintType, Object> hints = new java.util.HashMap<>();
                hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
                hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
                hints.put(EncodeHintType.MARGIN, 1);
                
                BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, qrSize, qrSize, hints);
                
                int width = bitMatrix.getWidth();
                int height = bitMatrix.getHeight();
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                    }
                }
                
                handler.post(() -> {
                    qrImageView.setImageBitmap(bitmap);
                });
            } catch (WriterException e) {
                handler.post(() -> {
                    Toast.makeText(this, "QR kod oluşturulamadı: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    log("QR kod oluşturma hatası: " + e.getMessage());
                });
            }
        }).start();
    }
    
    /**
     * Bluetooth cihaz ismini al
     */
    private String getBluetoothName() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ için izin kontrolü
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return null;
                }
            }
            
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                return bluetoothAdapter.getName();
            }
        } catch (Exception e) {
            log("Bluetooth ismi alma hatası: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * İnternet bağlantısı kontrolü
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }
    
    /**
     * Markdown metnini SpannableString'e dönüştürür (basit markdown desteği)
     * Desteklenen: ### başlıklar, **kalın** metinler
     */
    private android.text.SpannableString parseMarkdown(String markdownText) {
        android.text.SpannableStringBuilder builder = new android.text.SpannableStringBuilder();
        String[] lines = markdownText.split("\n", -1);
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // ### Başlık kontrolü
            if (line.trim().startsWith("### ")) {
                String titleText = line.substring(4).trim(); // "### " kısmını çıkar
                int start = builder.length();
                builder.append(titleText);
                int end = builder.length();
                
                // Başlık stilini uygula
                builder.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 
                    start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new android.text.style.RelativeSizeSpan(1.3f), 
                    start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                // Normal satır - **kalın** metinleri işle
                processBoldText(builder, line);
            }
            
            // Son satır değilse yeni satır ekle
            if (i < lines.length - 1) {
                builder.append("\n");
            }
        }
        
        return new android.text.SpannableString(builder);
    }
    
    /**
     * Satırdaki **kalın** metinleri işler ve SpannableStringBuilder'a ekler
     */
    private void processBoldText(android.text.SpannableStringBuilder builder, String line) {
        java.util.regex.Pattern boldPattern = java.util.regex.Pattern.compile("\\*\\*(.*?)\\*\\*");
        java.util.regex.Matcher matcher = boldPattern.matcher(line);
        
        int lastEnd = 0;
        while (matcher.find()) {
            // Bold öncesi normal metin
            if (matcher.start() > lastEnd) {
                builder.append(line.substring(lastEnd, matcher.start()));
            }
            
            // Bold metin
            String boldText = matcher.group(1);
            int boldStart = builder.length();
            builder.append(boldText);
            int boldEnd = builder.length();
            
            // Bold stilini uygula
            builder.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 
                boldStart, boldEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            lastEnd = matcher.end();
        }
        
        // Kalan metin
        if (lastEnd < line.length()) {
            builder.append(line.substring(lastEnd));
        }
    }
    
    /**
     * Butona tıklandığında animasyon gösterir ve 2-3 saniye sonra butonu tekrar aktif eder
     * İşlem zaten başlatılmış olmalı (simultaneously çalışır)
     */
    private void handleButtonClickWithDelay(Button button, String originalText, String loadingText) {
        // Butonu devre dışı bırak
        button.setEnabled(false);
        
        // Metni değiştir
        button.setText(loadingText);
        
        // Butonun alpha değerini düşür (görsel geri bildirim)
        button.setAlpha(0.6f);
        
        // Dönen animasyon için ProgressBar oluştur (butonun üzerine overlay)
        android.widget.ProgressBar progressBar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        progressBar.setIndeterminate(true);
        progressBar.setLayoutParams(new FrameLayout.LayoutParams(
                (int)(24 * getResources().getDisplayMetrics().density),
                (int)(24 * getResources().getDisplayMetrics().density)));
        
        // Butonun parent'ını FrameLayout'a dönüştür
        android.view.ViewParent parent = button.getParent();
        if (parent instanceof LinearLayout) {
            LinearLayout parentLayout = (LinearLayout) parent;
            int buttonIndex = parentLayout.indexOfChild(button);
            LinearLayout.LayoutParams buttonParams = (LinearLayout.LayoutParams) button.getLayoutParams();
            
            // FrameLayout container oluştur
            FrameLayout buttonContainer = new FrameLayout(this);
            buttonContainer.setLayoutParams(buttonParams);
            
            // Butonu container'a ekle
            parentLayout.removeView(button);
            button.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            buttonContainer.addView(button);
            
            // ProgressBar'ı sağ üst köşeye ekle
            FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                    (int)(24 * getResources().getDisplayMetrics().density),
                    (int)(24 * getResources().getDisplayMetrics().density));
            progressParams.gravity = android.view.Gravity.END | android.view.Gravity.TOP;
            progressParams.setMargins(0, (int)(8 * getResources().getDisplayMetrics().density), 
                    (int)(8 * getResources().getDisplayMetrics().density), 0);
            buttonContainer.addView(progressBar, progressParams);
            
            // Container'ı parent'a ekle
            parentLayout.addView(buttonContainer, buttonIndex);
            
            // 2.5 saniye sonra animasyonu kaldır ve butonu tekrar aktif et
            handler.postDelayed(() -> {
                // ProgressBar'ı kaldır
                buttonContainer.removeView(progressBar);
                
                // Butonu tekrar parent'a ekle
                buttonContainer.removeView(button);
                parentLayout.removeView(buttonContainer);
                button.setLayoutParams(buttonParams);
                parentLayout.addView(button, buttonIndex);
                
                // Metni ve alpha'yı geri al
                button.setText(originalText);
                button.setAlpha(1.0f);
                
                // Butonu tekrar aktif et
                button.setEnabled(true);
            }, 2500); // 2.5 saniye
        } else {
            // Basit yaklaşım: sadece metin değişikliği ve delay
            handler.postDelayed(() -> {
                button.setText(originalText);
                button.setAlpha(1.0f);
                button.setEnabled(true);
            }, 2500);
        }
    }
    
    /**
     * Ayarlar tab'ını oluştur
     */
    private void createSettingsTab() {
        settingsScrollView = new ScrollView(this);
        settingsScrollView.setBackgroundColor(0xFF0A0F14);
        settingsScrollView.setPadding(0, 0, 0, 0);
        settingsScrollView.setFillViewport(true);
        
        settingsTabContent = new LinearLayout(this);
        settingsTabContent.setOrientation(LinearLayout.VERTICAL);
        settingsTabContent.setPadding(0, 0, 0, 0);
        settingsTabContent.setBackgroundColor(0xFF0A0F14);
        settingsScrollView.addView(settingsTabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        
        // Uygulama Bilgileri bölümünü buraya ekle
        createAppInfoSection(settingsTabContent);
        
        // Floating Back Button bölümünü ekle (Uygulama Hakkında'nın altına)
        createFloatingBackButtonSection(settingsTabContent);
    }
    
    /**
     * Floating Back Button bölümünü oluştur (Ayarlar tab'ı içinde)
     */
    private void createFloatingBackButtonSection(LinearLayout parentContainer) {
        // Başlık
        TextView floatingBackButtonTitle = new TextView(this);
        floatingBackButtonTitle.setText("Floating Back Button");
        floatingBackButtonTitle.setTextSize(18);
        floatingBackButtonTitle.setTextColor(0xFFFFFFFF);
        floatingBackButtonTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        floatingBackButtonTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(floatingBackButtonTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // Açıklama
        TextView floatingBackButtonDesc = new TextView(this);
        floatingBackButtonDesc.setText("Ekranda yüzen bir geri tuşu göster. Sağa sola kaydırabilirsiniz.");
        floatingBackButtonDesc.setTextSize(13);
        floatingBackButtonDesc.setTextColor(0xAAFFFFFF);
        floatingBackButtonDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(floatingBackButtonDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // RadioGroup
        RadioGroup floatingBackButtonRadioGroup = new RadioGroup(this);
        floatingBackButtonRadioGroup.setOrientation(LinearLayout.VERTICAL);
        floatingBackButtonRadioGroup.setPadding(20, 0, 20, 0);
        
        // RadioButton 1: Açık
        LinearLayout option1Container = new LinearLayout(this);
        option1Container.setOrientation(LinearLayout.HORIZONTAL);
        option1Container.setPadding(16, 16, 16, 16);
        option1Container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        option1Container.setClickable(true);
        option1Container.setFocusable(true);
        
        LinearLayout iconCircle1 = new LinearLayout(this);
        iconCircle1.setOrientation(LinearLayout.VERTICAL);
        iconCircle1.setBackgroundColor(0xFF1A2330);
        iconCircle1.setGravity(android.view.Gravity.CENTER);
        iconCircle1.setPadding(10, 10, 10, 10);
        
        TextView icon1 = new TextView(this);
        icon1.setText("✅");
        icon1.setTextSize(20);
        icon1.setTextColor(0xFFFFFFFF);
        iconCircle1.addView(icon1);
        
        LinearLayout.LayoutParams iconCircle1Params = new LinearLayout.LayoutParams(48, 48);
        iconCircle1Params.setMargins(0, 0, 12, 0);
        option1Container.addView(iconCircle1, iconCircle1Params);
        
        LinearLayout textColumn1 = new LinearLayout(this);
        textColumn1.setOrientation(LinearLayout.VERTICAL);
        
        TextView title1 = new TextView(this);
        title1.setText("Açık");
        title1.setTextColor(0xFFFFFFFF);
        title1.setTextSize(16);
        textColumn1.addView(title1);
        
        TextView desc1 = new TextView(this);
        desc1.setText("Yüzen geri tuşunu göster");
        desc1.setTextColor(0xAAFFFFFF);
        desc1.setTextSize(13);
        desc1.setPadding(0, 2, 0, 0);
        textColumn1.addView(desc1);
        
        LinearLayout.LayoutParams textParams1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        option1Container.addView(textColumn1, textParams1);
        
        RadioButton radio1 = new RadioButton(this);
        radio1.setId(400);
        radio1.setClickable(false);
        radio1.setFocusable(false);
        option1Container.addView(radio1);
        
        option1Container.setOnClickListener(v -> floatingBackButtonRadioGroup.check(400));
        floatingBackButtonRadioGroup.addView(option1Container);
        
        // Ayırıcı çizgi
        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(0x1FFFFFFF);
        floatingBackButtonRadioGroup.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        
        // RadioButton 2: Kapalı
        LinearLayout option2Container = new LinearLayout(this);
        option2Container.setOrientation(LinearLayout.HORIZONTAL);
        option2Container.setPadding(16, 16, 16, 16);
        option2Container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        option2Container.setClickable(true);
        option2Container.setFocusable(true);
        
        LinearLayout iconCircle2 = new LinearLayout(this);
        iconCircle2.setOrientation(LinearLayout.VERTICAL);
        iconCircle2.setBackgroundColor(0xFF1A2330);
        iconCircle2.setGravity(android.view.Gravity.CENTER);
        iconCircle2.setPadding(10, 10, 10, 10);
        
        TextView icon2 = new TextView(this);
        icon2.setText("❌");
        icon2.setTextSize(20);
        icon2.setTextColor(0xFFFFFFFF);
        iconCircle2.addView(icon2);
        
        LinearLayout.LayoutParams iconCircle2Params = new LinearLayout.LayoutParams(48, 48);
        iconCircle2Params.setMargins(0, 0, 12, 0);
        option2Container.addView(iconCircle2, iconCircle2Params);
        
        LinearLayout textColumn2 = new LinearLayout(this);
        textColumn2.setOrientation(LinearLayout.VERTICAL);
        
        TextView title2 = new TextView(this);
        title2.setText("Kapalı");
        title2.setTextColor(0xFFFFFFFF);
        title2.setTextSize(16);
        textColumn2.addView(title2);
        
        TextView desc2 = new TextView(this);
        desc2.setText("Yüzen geri tuşunu gizle");
        desc2.setTextColor(0xAAFFFFFF);
        desc2.setTextSize(13);
        desc2.setPadding(0, 2, 0, 0);
        textColumn2.addView(desc2);
        
        LinearLayout.LayoutParams textParams2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        option2Container.addView(textColumn2, textParams2);
        
        RadioButton radio2 = new RadioButton(this);
        radio2.setId(401);
        radio2.setClickable(false);
        radio2.setFocusable(false);
        option2Container.addView(radio2);
        
        option2Container.setOnClickListener(v -> floatingBackButtonRadioGroup.check(401));
        floatingBackButtonRadioGroup.addView(option2Container);
        
        parentContainer.addView(floatingBackButtonRadioGroup, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // FloatingBackButtonManager instance (eğer yoksa oluştur)
        if (floatingBackButtonManager == null) {
            floatingBackButtonManager = new FloatingBackButtonManager(this);
            // Log callback'i set et
            floatingBackButtonManager.setLogCallback(new FloatingBackButtonManager.LogCallback() {
                @Override
                public void log(String message) {
                    MainActivity.this.log(message);
                }
            });
        }
        
        // Kaydedilmiş ayarı yükle
        boolean savedEnabled = FloatingBackButtonManager.loadEnabledState(this);
        floatingBackButtonRadioGroup.check(savedEnabled ? 400 : 401);
        
        // İlk yüklemede icon rengini ayarla ve butonu göster/gizle
        // Default: false (kapalı) - sadece kullanıcı açarsa göster
        handler.postDelayed(() -> {
            try {
                if (savedEnabled) {
                    iconCircle1.setBackgroundColor(0xFF3DAEA8);
                    iconCircle2.setBackgroundColor(0xFF1A2330);
                    if (floatingBackButtonManager != null) {
                        floatingBackButtonManager.show();
                    }
                } else {
                    // Default: kapalı
                    iconCircle1.setBackgroundColor(0xFF1A2330);
                    iconCircle2.setBackgroundColor(0xFF3DAEA8);
                    if (floatingBackButtonManager != null) {
                        floatingBackButtonManager.hide();
                    }
                }
            } catch (Exception e) {
                log("Floating Back Button başlatma hatası: " + e.getMessage());
            }
        }, 500); // 500ms gecikme ile güvenli başlatma
        
        // RadioGroup değişiklik listener'ı
        final LinearLayout[] iconCircles = {iconCircle1, iconCircle2};
        floatingBackButtonRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isEnabled = (checkedId == 400); // 400 = Açık, 401 = Kapalı
            FloatingBackButtonManager.saveEnabledState(this, isEnabled);
            
            if (isEnabled) {
                // İzin kontrolü yap
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!android.provider.Settings.canDrawOverlays(this)) {
                        // İzin yoksa ayarlara yönlendir
                        try {
                            android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                            Toast.makeText(this, "Lütfen 'Diğer uygulamaların üzerinde görüntüleme' iznini açın", Toast.LENGTH_LONG).show();
                            // RadioGroup'u geri al
                            floatingBackButtonRadioGroup.check(401);
                            return;
                        } catch (Exception e) {
                            log("İzin ayarlarına gidilemedi: " + e.getMessage());
                            floatingBackButtonRadioGroup.check(401);
                            return;
                        }
                    }
                }
                floatingBackButtonManager.show();
                log("Floating Back Button açıldı");
            } else {
                floatingBackButtonManager.hide();
                log("Floating Back Button kapatıldı");
            }
            
            // Seçili durumda accent rengi
            for (int i = 0; i < iconCircles.length; i++) {
                LinearLayout iconCircle = iconCircles[i];
                if (iconCircle != null) {
                    if ((isEnabled && i == 0) || (!isEnabled && i == 1)) {
                        // Seçili: accent rengi
                        iconCircle.setBackgroundColor(0xFF3DAEA8);
                    } else {
                        // Seçili değil: sade
                        iconCircle.setBackgroundColor(0xFF1A2330);
                    }
                }
            }
        });
    }
    
    /**
     * Uygulama Bilgileri bölümünü oluştur (Ayarlar tab'ı içinde)
     */
    private void createAppInfoSection(LinearLayout parentContainer) {
        // Uygulama Hakkında başlığı
        TextView appInfoTitle = new TextView(this);
        appInfoTitle.setText("Uygulama Hakkında");
        appInfoTitle.setTextSize(18);
        appInfoTitle.setTextColor(0xFFFFFFFF);
        appInfoTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        appInfoTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(appInfoTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // Versiyon bilgisi
        TextView versionTitle = new TextView(this);
        versionTitle.setText("Versiyon");
        versionTitle.setTextSize(16);
        versionTitle.setTextColor(0xFFFFFFFF);
        versionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        versionTitle.setPadding(16, 16, 16, 8);
        parentContainer.addView(versionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        TextView versionText = new TextView(this);
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionText.setText("Mevcut Versiyon: " + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("Mevcut Versiyon: Bilinmiyor");
        }
        versionText.setTextSize(14);
        versionText.setTextColor(0xAAFFFFFF);
        versionText.setPadding(16, 0, 16, 16);
        parentContainer.addView(versionText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // Güncel versiyon (API'den gelecek)
        TextView latestVersionText = new TextView(this);
        latestVersionText.setId(android.view.View.generateViewId());
        latestVersionText.setText("Güncel Versiyon: Yükleniyor...");
        latestVersionText.setTextSize(14);
        latestVersionText.setTextColor(0xAAFFFFFF);
        latestVersionText.setPadding(16, 0, 16, 16);
        parentContainer.addView(latestVersionText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // Ücretsiz kurulum bilgisi
        TextView freeInstallTitle = new TextView(this);
        freeInstallTitle.setText("Kurulum");
        freeInstallTitle.setTextSize(16);
        freeInstallTitle.setTextColor(0xFFFFFFFF);
        freeInstallTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        freeInstallTitle.setPadding(16, 16, 16, 8);
        parentContainer.addView(freeInstallTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        TextView freeInstallText = new TextView(this);
        freeInstallText.setText("Bu uygulama https://vnoisy.dev adresinden ücretsiz olarak kurulabilir.");
        freeInstallText.setTextSize(14);
        freeInstallText.setTextColor(0xAAFFFFFF);
        freeInstallText.setPadding(16, 0, 16, 16);
        parentContainer.addView(freeInstallText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // Güncelleme Notları
        TextView changelogTitle = new TextView(this);
        changelogTitle.setText("Güncelleme Notları");
        changelogTitle.setTextSize(16);
        changelogTitle.setTextColor(0xFFFFFFFF);
        changelogTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        changelogTitle.setPadding(16, 16, 16, 8);
        parentContainer.addView(changelogTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        TextView changelogText = new TextView(this);
        changelogText.setId(android.view.View.generateViewId());
        changelogText.setText("Yükleniyor...");
        changelogText.setTextSize(14);
        changelogText.setTextColor(0xAAFFFFFF);
        changelogText.setPadding(16, 0, 16, 16);
        changelogText.setLineSpacing(4, 1.0f); // Satır aralığı
        parentContainer.addView(changelogText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // API'den güncelleme notlarını çek
        fetchAnnouncement(latestVersionText, changelogText);
    }
    
    /**
     * API'den güncelleme notlarını çek
     * Endpoint: GET /api/announcement/get (tüm notları çek)
     * Headers: Authorization: Bearer {car_token}
     * Tüm notları çeker, güncel sürümü bulur ve tüm notları gösterir
     */
    private void fetchAnnouncement(TextView latestVersionView, TextView changelogView) {
        new Thread(() -> {
            try {
                // Mevcut versiyonu al
                String currentVersion = "1.0.0";
                try {
                    currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                } catch (PackageManager.NameNotFoundException e) {
                    // Varsayılan versiyon kullan
                }
                
                // Token'ı al
                SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
                String token = prefs.getString("carToken", null);
                
                // URL oluştur - version parametresi olmadan tüm notları çek
                String urlString = "https://api.vnoisy.dev/api/announcement/get";
                java.net.URL url = new java.net.URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                // Authorization header ekle (eğer token varsa)
                if (token != null && !token.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                }
                
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    // Response bir array olmalı
                    String responseStr = response.toString().trim();
                    if (responseStr.startsWith("[")) {
                        JSONArray announcements = new JSONArray(responseStr);
                        StringBuilder changelogBuilder = new StringBuilder();
                        String latestVersionFromApi = currentVersion; // Varsayılan olarak mevcut versiyon
                        
                        // Tüm notları işle ve en yüksek versiyonu bul
                        for (int i = 0; i < announcements.length(); i++) {
                            JSONObject announcement = announcements.getJSONObject(i);
                            String version = announcement.optString("version", "");
                            String title = announcement.optString("title", "");
                            String message = announcement.optString("message", "");
                            
                            // Güncel versiyonu bul (en yüksek versiyon numarası)
                            if (!version.isEmpty() && compareVersions(version, latestVersionFromApi) > 0) {
                                latestVersionFromApi = version;
                            }
                            
                            // Tüm notları birleştir
                            if (!title.isEmpty() || !message.isEmpty()) {
                                if (changelogBuilder.length() > 0) {
                                    changelogBuilder.append("\n\n");
                                }
                                
                                // Versiyon bilgisi ekle
                                if (!version.isEmpty()) {
                                    changelogBuilder.append("Versiyon ").append(version);
                                    if (!title.isEmpty()) {
                                        changelogBuilder.append(" - ");
                                    } else {
                                        changelogBuilder.append("\n");
                                    }
                                }
                                
                                if (!title.isEmpty()) {
                                    changelogBuilder.append(title).append("\n");
                                }
                                if (!message.isEmpty()) {
                                    changelogBuilder.append(message);
                                }
                            }
                        }
                        
                        final String finalLatestVersion = latestVersionFromApi;
                        final String finalChangelog = changelogBuilder.length() > 0 ? 
                            changelogBuilder.toString() : "Güncelleme notu bulunamadı.";
                        
                        handler.post(() -> {
                            latestVersionView.setText("Güncel Versiyon: " + finalLatestVersion);
                            changelogView.setText(finalChangelog);
                        });
                    } else {
                        // Tek bir JSON object (eski format - fallback)
                        JSONObject json = new JSONObject(responseStr);
                        String latestVersion = json.optString("version", currentVersion);
                        String changelog = json.optString("changelog", json.optString("message", "Güncelleme notu bulunamadı."));
                        
                        handler.post(() -> {
                            latestVersionView.setText("Güncel Versiyon: " + latestVersion);
                            changelogView.setText(parseMarkdown(changelog));
                        });
                    }
                } else {
                    handler.post(() -> {
                        latestVersionView.setText("Güncel Versiyon: Yüklenemedi");
                        changelogView.setText("Güncelleme notları yüklenemedi. (HTTP " + responseCode + ")");
                    });
                }
                connection.disconnect();
            } catch (Exception e) {
                handler.post(() -> {
                    latestVersionView.setText("Güncel Versiyon: Hata");
                    changelogView.setText("Güncelleme notları yüklenirken hata oluştu: " + e.getMessage());
                });
            }
        }).start();
    }
    
    /**
     * Versiyon numaralarını karşılaştır
     * @return v1 > v2 ise pozitif, v1 < v2 ise negatif, eşitse 0
     */
    private int compareVersions(String v1, String v2) {
        try {
            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");
            int maxLength = Math.max(parts1.length, parts2.length);
            
            for (int i = 0; i < maxLength; i++) {
                int num1 = (i < parts1.length) ? Integer.parseInt(parts1[i]) : 0;
                int num2 = (i < parts2.length) ? Integer.parseInt(parts2[i]) : 0;
                
                if (num1 > num2) return 1;
                if (num1 < num2) return -1;
            }
            return 0;
        } catch (Exception e) {
            // Hata durumunda string karşılaştırması yap
            return v1.compareTo(v2);
        }
    }
}
