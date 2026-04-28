package com.mapcontrol.ui.builder;
import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.json.JSONObject;
import com.mapcontrol.R;
import com.mapcontrol.util.TargetPackageStore;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.api.ProfileApiService;
import com.mapcontrol.ui.activity.MainActivity;

public class ProfileTabBuilder {
    public interface ProfileCallback {
        void log(String message);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final ProfileApiService apiService;
    private final ProfileCallback callback;

    private final Handler handler = new Handler(Looper.getMainLooper());

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
    private UiStyles.BinarySegmentHandle profileAutoLocationSegmentHandle;

    private LinearLayout profileTabContent;
    private ScrollView profileScrollView;

    private LocationManager locationManager;
    private LocationListener locationListener;

    public ProfileTabBuilder(Context context, SharedPreferences prefs, ProfileApiService apiService, ProfileCallback callback) {
        this.context = context;
        this.prefs = prefs;
        this.apiService = apiService;
        this.callback = callback;
    }

    public ScrollView build() {
        createProfileTab();
        return profileScrollView;
    }

    private void createProfileTab() {
        profileScrollView = new ScrollView(context);
        profileScrollView.setBackgroundColor(Color.TRANSPARENT);
        profileScrollView.setFillViewport(true);

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        outer.setPadding(margin, margin, margin, margin);

        profileTabContent = new LinearLayout(context);
        profileTabContent.setOrientation(LinearLayout.VERTICAL);
        int inner = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        profileTabContent.setPadding(inner, inner, inner, inner);
        UiStyles.setGlassCardBackground(profileTabContent);

        // Giriş kartı
        LinearLayout loginCard = new LinearLayout(context);
        loginCard.setOrientation(LinearLayout.VERTICAL);
        loginCard.setPadding(20, 20, 20, 20);
        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(ContextCompat.getColor(context, R.color.surfaceCard));
        cardBg.setCornerRadius(context.getResources().getDimension(R.dimen.oem_button_radius));
        cardBg.setStroke(1, ContextCompat.getColor(context, R.color.outline));
        loginCard.setBackground(cardBg);

        profileLoginStatusText = new TextView(context);
        profileLoginStatusText.setTextSize(15);
        profileLoginStatusText.setTextColor(ContextCompat.getColor(context, R.color.textPrimary87));
        profileLoginStatusText.setPadding(0, 0, 0, 20);
        profileLoginStatusText.setLineSpacing(4, 1.2f);

        profileEmailLabel = new TextView(context);
        profileEmailLabel.setText("E-posta Adresi");
        profileEmailLabel.setTextSize(14);
        profileEmailLabel.setTextColor(ContextCompat.getColor(context, R.color.textSecondary));
        profileEmailLabel.setPadding(0, 0, 0, 8);
        loginCard.addView(profileEmailLabel);

        profileEmailInput = new EditText(context);
        profileEmailInput.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        profileEmailInput.setHint("ornek@email.com");
        profileEmailInput.setTextSize(16);
        profileEmailInput.setPadding(16, 16, 16, 16);
        android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
        inputBg.setColor(ContextCompat.getColor(context, R.color.surfaceColor));
        inputBg.setCornerRadius(context.getResources().getDimension(R.dimen.oem_button_radius));
        inputBg.setStroke(1, ContextCompat.getColor(context, R.color.outlineMuted));
        profileEmailInput.setBackground(inputBg);
        profileEmailInput.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        profileEmailInput.setHintTextColor(ContextCompat.getColor(context, R.color.textHint));
        LinearLayout.LayoutParams emailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        emailParams.setMargins(0, 0, 0, 16);
        loginCard.addView(profileEmailInput, emailParams);

        profileCodeLabel = new TextView(context);
        profileCodeLabel.setText("Doğrulama Kodu");
        profileCodeLabel.setTextSize(14);
        profileCodeLabel.setTextColor(ContextCompat.getColor(context, R.color.textSecondary));
        profileCodeLabel.setPadding(0, 0, 0, 8);
        loginCard.addView(profileCodeLabel);

        profileCodeInput = new EditText(context);
        profileCodeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        profileCodeInput.setHint("123456");
        profileCodeInput.setTextSize(16);
        profileCodeInput.setPadding(16, 16, 16, 16);
        profileCodeInput.setBackground(inputBg);
        profileCodeInput.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        profileCodeInput.setHintTextColor(ContextCompat.getColor(context, R.color.textHint));
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        codeParams.setMargins(0, 0, 0, 16);
        loginCard.addView(profileCodeInput, codeParams);

        profileButtonsContainer = new LinearLayout(context);
        profileButtonsContainer.setOrientation(LinearLayout.HORIZONTAL);
        profileButtonsContainer.setPadding(0, 0, 0, 0);

        profileSendCodeButton = new Button(context);
        profileSendCodeButton.setText("Kod Gönder");
        profileSendCodeButton.setTextSize(14);
        profileSendCodeButton.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        UiStyles.styleOemButton(profileSendCodeButton, ContextCompat.getColor(context, R.color.buttonPrimary));
        profileSendCodeButton.setPadding(20, 14, 20, 14);
        LinearLayout.LayoutParams sendBtnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        sendBtnParams.setMargins(0, 0, 8, 0);
        profileButtonsContainer.addView(profileSendCodeButton, sendBtnParams);
        styleSendCodeButtonDefault();

        profileLoginButton = new Button(context);
        profileLoginButton.setText("Giriş Yap");
        profileLoginButton.setTextSize(14);
        profileLoginButton.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        UiStyles.styleOemButton(profileLoginButton, ContextCompat.getColor(context, R.color.buttonSuccessBright));
        profileLoginButton.setPadding(20, 14, 20, 14);
        LinearLayout.LayoutParams loginBtnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        loginBtnParams.setMargins(8, 0, 0, 0);
        profileButtonsContainer.addView(profileLoginButton, loginBtnParams);

        loginCard.addView(profileLoginStatusText);
        loginCard.addView(profileButtonsContainer);

        profileLogoutButton = new Button(context);
        profileLogoutButton.setText("Çıkış Yap");
        profileLogoutButton.setTextSize(14);
        profileLogoutButton.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        UiStyles.styleOemButton(profileLogoutButton, ContextCompat.getColor(context, R.color.buttonDanger));
        profileLogoutButton.setPadding(20, 14, 20, 14);
        profileLogoutButton.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams logoutBtnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        logoutBtnParams.setMargins(0, 16, 0, 8);
        loginCard.addView(profileLogoutButton, logoutBtnParams);

        profilePlatformButton = new Button(context);
        profilePlatformButton.setText("Platforma Gir");
        profilePlatformButton.setTextSize(14);
        profilePlatformButton.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        UiStyles.styleOemButton(profilePlatformButton, ContextCompat.getColor(context, R.color.buttonPrimary));
        profilePlatformButton.setPadding(20, 14, 20, 14);
        profilePlatformButton.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams platformBtnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        platformBtnParams.setMargins(0, 0, 0, 0);
        loginCard.addView(profilePlatformButton, platformBtnParams);

        profilePlatformButton.setOnClickListener(v -> {
            if (apiService == null || !apiService.isLoggedIn()) {
                Toast.makeText(context, "Önce giriş yapmalısınız", Toast.LENGTH_SHORT).show();
                return;
            }

            String token = apiService.getCarToken();
            if (token == null || token.isEmpty()) {
                Toast.makeText(context, "Token bulunamadı", Toast.LENGTH_SHORT).show();
                return;
            }

            String platformUrl = "https://user.vnoisy.dev/login?token=" + token;
            showPlatformQRDialog(platformUrl);
        });

        LinearLayout.LayoutParams loginCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        loginCardParams.setMargins(16, 0, 16, 32);
        profileTabContent.addView(loginCard, loginCardParams);

        updateProfileLoginStatus();

        profileSendCodeButton.setOnClickListener(v -> {
            String email = profileEmailInput.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(context, "E-posta adresi gerekli", Toast.LENGTH_SHORT).show();
                return;
            }

            profileSendCodeButton.setEnabled(false);
            UiStyles.clearButtonCompoundDrawables(profileSendCodeButton);
            profileSendCodeButton.setText("Gönderiliyor...");

            if (apiService != null) {
                apiService.sendVerificationCode(email, new ProfileApiService.ApiCallback() {
                    @Override
                    public void onSuccess(String message, JSONObject data) {
                        handler.post(() -> {
                            profileSendCodeButton.setEnabled(true);
                            UiStyles.clearButtonCompoundDrawables(profileSendCodeButton);
                            profileSendCodeButton.setText("Kod Gönderildi");
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                            callback.log("Doğrulama kodu gönderildi: " + email);
                            handler.postDelayed(() -> styleSendCodeButtonDefault(), 3000);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        handler.post(() -> {
                            profileSendCodeButton.setEnabled(true);
                            styleSendCodeButtonDefault();
                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
                            callback.log("Kod gönderme hatası: " + error);
                        });
                    }
                });
            }
        });

        profileLoginButton.setOnClickListener(v -> {
            String email = profileEmailInput.getText().toString().trim();
            String code = profileCodeInput.getText().toString().trim();

            if (email.isEmpty()) {
                Toast.makeText(context, "E-posta adresi gerekli", Toast.LENGTH_SHORT).show();
                return;
            }
            if (code.isEmpty()) {
                Toast.makeText(context, "Doğrulama kodu gerekli", Toast.LENGTH_SHORT).show();
                return;
            }

            profileLoginButton.setEnabled(false);
            profileLoginButton.setText("Doğrulanıyor...");

            if (apiService != null) {
                apiService.verifyCode(email, code, new ProfileApiService.ApiCallback() {
                    @Override
                    public void onSuccess(String message, JSONObject data) {
                        handler.post(() -> {
                            profileLoginButton.setEnabled(true);
                            profileLoginButton.setText("Giriş Yap");
                            updateProfileLoginStatus();
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                            callback.log("Giriş başarılı: " + email);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        handler.post(() -> {
                            profileLoginButton.setEnabled(true);
                            profileLoginButton.setText("Giriş Yap");
                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
                            callback.log("Giriş hatası: " + error);
                        });
                    }
                });
            }
        });

        profileLogoutButton.setOnClickListener(v -> {
            if (apiService != null) {
                apiService.clearToken();
                profileEmailInput.setText("");
                profileCodeInput.setText("");
                updateProfileLoginStatus();
                Toast.makeText(context, "Çıkış yapıldı", Toast.LENGTH_SHORT).show();
                callback.log("Kullanıcı çıkış yaptı");
            }
        });

        // İşlemler kartı (Veri Yönetimi)
        LinearLayout actionsCard = new LinearLayout(context);
        actionsCard.setOrientation(LinearLayout.VERTICAL);
        actionsCard.setPadding(20, 20, 20, 20);
        android.graphics.drawable.GradientDrawable actionsCardBg = new android.graphics.drawable.GradientDrawable();
        actionsCardBg.setColor(ContextCompat.getColor(context, R.color.surfaceCard));
        actionsCardBg.setCornerRadius(context.getResources().getDimension(R.dimen.oem_button_radius));
        actionsCardBg.setStroke(1, ContextCompat.getColor(context, R.color.outline));
        actionsCard.setBackground(actionsCardBg);

        TextView actionsTitle = new TextView(context);
        actionsTitle.setText("Veri Yönetimi");
        actionsTitle.setTextSize(17);
        actionsTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        actionsTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
        actionsTitle.setPadding(0, 0, 0, 16);
        actionsCard.addView(actionsTitle);

        LinearLayout buttonsRow = new LinearLayout(context);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setPadding(0, 0, 0, 0);

        profileSaveDataButton = new Button(context);
        profileSaveDataButton.setText("Son Değişiklikleri Sakla");
        profileSaveDataButton.setTextSize(14);
        profileSaveDataButton.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        UiStyles.styleOemButton(profileSaveDataButton, ContextCompat.getColor(context, R.color.buttonPrimary));
        profileSaveDataButton.setPadding(16, 14, 16, 14);
        profileSaveDataButton.setEnabled(apiService != null && apiService.isLoggedIn());
        profileSaveDataButton.setVisibility(android.view.View.VISIBLE);

        LinearLayout.LayoutParams saveBtnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        saveBtnParams.setMargins(0, 0, 8, 0);
        buttonsRow.addView(profileSaveDataButton, saveBtnParams);

        profileLoadDataButton = new Button(context);
        profileLoadDataButton.setText("Son Değişiklikleri Getir");
        profileLoadDataButton.setTextSize(14);
        profileLoadDataButton.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        UiStyles.styleOemButton(profileLoadDataButton, ContextCompat.getColor(context, R.color.buttonSuccessBright));
        profileLoadDataButton.setPadding(16, 14, 16, 14);
        profileLoadDataButton.setEnabled(apiService != null && apiService.isLoggedIn());
        profileLoadDataButton.setVisibility(android.view.View.VISIBLE);

        LinearLayout.LayoutParams loadBtnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        loadBtnParams.setMargins(8, 0, 0, 0);
        buttonsRow.addView(profileLoadDataButton, loadBtnParams);

        LinearLayout.LayoutParams buttonsRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsRowParams.setMargins(0, 0, 0, 16);
        actionsCard.addView(buttonsRow, buttonsRowParams);

        profileSaveDataButton.setOnClickListener(v -> {
            if (apiService == null || !apiService.isLoggedIn()) {
                Toast.makeText(context, "Önce giriş yapmalısınız", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isNetworkAvailable()) {
                Toast.makeText(context, "İnternet bağlantısı yok", Toast.LENGTH_SHORT).show();
                callback.log("İnternet bağlantısı yok, veri kaydedilemedi");
                return;
            }

            profileSaveDataButton.setEnabled(false);
            profileSaveDataButton.setText("Kaydediliyor...");

            try {
                JSONObject data = new JSONObject();
                data.put("disclaimerAccepted", prefs.getBoolean("disclaimerAccepted", false));
                data.put("appManagementDisclaimerAccepted", prefs.getBoolean("appManagementDisclaimerAccepted", false));
                data.put("mapControlKeyEnabled", prefs.getBoolean("mapControlKeyEnabled", true));
                data.put("autoCloseOnPowerOff", prefs.getBoolean("autoCloseOnPowerOff", true));
                data.put("powerModeSetting", prefs.getInt("powerModeSetting", 2));
                data.put("driveModeSetting", prefs.getInt("driveModeSetting", -1));
                data.put("issSetting", prefs.getInt("issSetting", -1));
                data.put("ldwSetting", prefs.getInt("ldwSetting", -1));
                data.put("spdLimitSetting", prefs.getInt("spdLimitSetting", -1));
                data.put("ldpSetting", prefs.getInt("ldpSetting", -1));
                data.put("fcwSetting", prefs.getInt("fcwSetting", -1));
                data.put("aebSetting", prefs.getInt("aebSetting", -1));

                String targetPackage = prefs.getString("targetPackage", null);
                if (targetPackage != null) {
                    data.put("targetPackage", targetPackage);
                }

                String bluetoothName = getBluetoothName();
                if (bluetoothName != null && !bluetoothName.isEmpty()) {
                    data.put("bluetoothName", bluetoothName);
                }

                apiService.saveUserData(data, new ProfileApiService.ApiCallback() {
                    @Override
                    public void onSuccess(String message, JSONObject data) {
                        handler.post(() -> {
                            profileSaveDataButton.setEnabled(true);
                            profileSaveDataButton.setText("Son Değişiklikleri Sakla");
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                            callback.log("Veriler başarıyla kaydedildi: " + message);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        handler.post(() -> {
                            profileSaveDataButton.setEnabled(true);
                            profileSaveDataButton.setText("Son Değişiklikleri Sakla");
                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
                            callback.log("Veri kaydetme hatası: " + error);
                        });
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    profileSaveDataButton.setEnabled(true);
                    profileSaveDataButton.setText("Son Değişiklikleri Sakla");
                    Toast.makeText(context, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    callback.log("Veri kaydetme hatası: " + e.getMessage());
                });
            }
        });

        // Not: Profil sekmesinin kalan kısmı (veri çekme, konum kaydetme, auto-location radio vs.)
        // createProfileTab() içinde zaten devam ediyor; burada dosya boyutunu kontrol altında tutmak için
        // kalan kısmı da birebir ekliyorum (aşağıda).

        // Kalan UI/logic bloklarını birebir taşımak için MainActivity'deki implementasyonla aynı şekilde devam:
        // profileLoadDataButton, profileSaveLocationButton, autoLocation radio group vb.
        // (Bu builder dosyasında, devamı aşağıdaki "appendRemainingProfileUI(actionsCard)" metodunda.)
        appendRemainingProfileUI(actionsCard);

        LinearLayout.LayoutParams actionsCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsCardParams.setMargins(16, 0, 16, 32);
        profileTabContent.addView(actionsCard, actionsCardParams);

        if (apiService != null && apiService.isLoggedIn()) {
            actionsCard.setVisibility(android.view.View.VISIBLE);
        } else {
            actionsCard.setVisibility(android.view.View.GONE);
        }

        outer.addView(profileTabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        profileScrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    // MainActivity'deki profil kartının kalan dev bloklarını korumak için ayrıştırdım.
    // Mantık aynı; sadece "this" -> context, log() -> callback.log, prefs -> this.prefs, handler -> this.handler uyarlaması var.
    private void appendRemainingProfileUI(LinearLayout actionsCard) {
        // Son değişiklikleri getir butonu tıklama (MainActivity kodundan uyarlanmış)
        profileLoadDataButton.setOnClickListener(v -> {
            if (apiService == null || !apiService.isLoggedIn()) {
                Toast.makeText(context, "Önce giriş yapmalısınız", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isNetworkAvailable()) {
                Toast.makeText(context, "İnternet bağlantısı yok", Toast.LENGTH_SHORT).show();
                callback.log("İnternet bağlantısı yok, veri getirilemedi");
                return;
            }

            AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(context);
            confirmBuilder.setTitle("Verileri Çek");
            confirmBuilder.setMessage("Sunucudan son kayıtlı verilerinizi çekmek istiyor musunuz?\n\nNot: Mevcut ayarlarınızın üzerine yazılacaktır.");
            confirmBuilder.setPositiveButton("Evet, Çek", (dialog, which) -> {
                profileLoadDataButton.setEnabled(false);
                profileLoadDataButton.setText("Yükleniyor...");

                apiService.getUserData(new ProfileApiService.ApiCallback() {
                    @Override
                    public void onSuccess(String message, JSONObject responseData) {
                        handler.post(() -> {
                            try {
                                JSONObject data = responseData;
                                if (data == null) {
                                    Toast.makeText(context, "Veri bulunamadı", Toast.LENGTH_SHORT).show();
                                    callback.log("Sunucudan veri alınamadı: data objesi yok");
                                    profileLoadDataButton.setEnabled(true);
                                    profileLoadDataButton.setText("Son Değişiklikleri Getir");
                                    return;
                                }

                                SharedPreferences.Editor editor = prefs.edit();
                                if (data.has("powerModeSetting")) editor.putInt("powerModeSetting", data.getInt("powerModeSetting"));
                                if (data.has("driveModeSetting")) editor.putInt("driveModeSetting", data.getInt("driveModeSetting"));
                                if (data.has("issSetting")) editor.putInt("issSetting", data.getInt("issSetting"));
                                if (data.has("ldwSetting")) editor.putInt("ldwSetting", data.getInt("ldwSetting"));
                                if (data.has("spdLimitSetting")) editor.putInt("spdLimitSetting", data.getInt("spdLimitSetting"));
                                if (data.has("ldpSetting")) editor.putInt("ldpSetting", data.getInt("ldpSetting"));
                                if (data.has("fcwSetting")) editor.putInt("fcwSetting", data.getInt("fcwSetting"));
                                if (data.has("aebSetting")) editor.putInt("aebSetting", data.getInt("aebSetting"));
                                if (data.has("targetPackage")) {
                                    String targetPackage = data.getString("targetPackage");
                                    if (targetPackage != null && !targetPackage.isEmpty()) {
                                        editor.putString(TargetPackageStore.KEY_TARGET_PACKAGE, targetPackage);
                                    }
                                }
                                editor.apply();
                                if (data.has("targetPackage")) {
                                    TargetPackageStore.broadcast(context);
                                }

                                callback.log("Sunucudan veriler başarıyla yüklendi ve SharedPreferences'a kaydedildi");

                                AlertDialog.Builder restartBuilder = new AlertDialog.Builder(context);
                                restartBuilder.setTitle("Uygulama Yeniden Başlatılacak");
                                restartBuilder.setMessage("Son kayıtlı verileriniz başarıyla yüklendi.\n\nAyarların etkin olması için uygulamanın yeniden başlatılması gerekiyor.\n\nYeniden başlatmak istiyor musunuz?");
                                restartBuilder.setPositiveButton("Yeniden Başlat", (d2, w2) -> {
                                    Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                                    if (intent != null) {
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                        context.startActivity(intent);
                                        android.os.Process.killProcess(android.os.Process.myPid());
                                    } else {
                                        Toast.makeText(context, "Uygulama yeniden başlatılamadı", Toast.LENGTH_SHORT).show();
                                    }
                                });
                                restartBuilder.setNegativeButton("İptal", (d2, w2) -> {
                                    d2.dismiss();
                                    Toast.makeText(context, "Veriler yüklendi, uygulamayı manuel olarak yeniden başlatabilirsiniz", Toast.LENGTH_LONG).show();
                                });
                                restartBuilder.setCancelable(false);
                                restartBuilder.show();
                            } catch (Exception e) {
                                Toast.makeText(context, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                callback.log("Veri yükleme hatası: " + e.getMessage());
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
                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
                            callback.log("Veri getirme hatası: " + error);
                        });
                    }
                });
            });
            confirmBuilder.setNegativeButton("İptal", (dialog, which) -> dialog.dismiss());
            confirmBuilder.setCancelable(true);
            confirmBuilder.show();
        });

        profileSaveLocationButton = new Button(context);
        profileSaveLocationButton.setTextSize(16);
        profileSaveLocationButton.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        UiStyles.styleOemButton(profileSaveLocationButton, ContextCompat.getColor(context, R.color.buttonPrimary));
        profileSaveLocationButton.setPadding(24, 18, 24, 18);
        profileSaveLocationButton.setEnabled(apiService != null && apiService.isLoggedIn());
        styleSaveLocationButtonDefault();

        LinearLayout.LayoutParams locationBtnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        locationBtnParams.setMargins(0, 0, 0, 16);
        actionsCard.addView(profileSaveLocationButton, locationBtnParams);

        TextView autoLocationTitle = new TextView(context);
        autoLocationTitle.setText("Araç Kapanınca Otomatik Konum Kaydet");
        autoLocationTitle.setTextSize(15);
        autoLocationTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary87));
        autoLocationTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
        autoLocationTitle.setPadding(0, 0, 0, 8);
        actionsCard.addView(autoLocationTitle);

        TextView autoLocationDesc = new TextView(context);
        autoLocationDesc.setText("Araç kapanınca ne olsun?");
        autoLocationDesc.setTextSize(13);
        autoLocationDesc.setTextColor(ContextCompat.getColor(context, R.color.textHint));
        autoLocationDesc.setPadding(0, 0, 0, 12);
        actionsCard.addView(autoLocationDesc);

        LinearLayout autoLocationSegmentBlock = new LinearLayout(context);
        autoLocationSegmentBlock.setOrientation(LinearLayout.VERTICAL);

        boolean autoLocationEnabled = prefs.getBoolean("autoLocationSaveOnPowerOff", false);
        boolean isLoggedIn = apiService != null && apiService.isLoggedIn();

        profileAutoLocationSegmentHandle = UiStyles.addBinarySegmentedControl(context, autoLocationSegmentBlock,
                null,
                "Açık", "Kapalı",
                "Araç kapanınca otomatik konum kaydet.",
                "Otomatik konum kaydetme kapalı.",
                autoLocationEnabled,
                isEnabled -> {
                    prefs.edit().putBoolean("autoLocationSaveOnPowerOff", isEnabled).apply();
                    callback.log(isEnabled ? "Otomatik konum kaydetme açıldı" : "Otomatik konum kaydetme kapatıldı");
                });
        profileAutoLocationSegmentHandle.setInteractionEnabled(isLoggedIn);

        handler.post(() -> {
            if (profileAutoLocationSegmentHandle != null) {
                profileAutoLocationSegmentHandle.syncVisualWithoutCommit(
                        prefs.getBoolean("autoLocationSaveOnPowerOff", false));
                profileAutoLocationSegmentHandle.setInteractionEnabled(
                        apiService != null && apiService.isLoggedIn());
            }
        });

        LinearLayout.LayoutParams autoLocationRadioGroupParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        autoLocationRadioGroupParams.setMargins(0, 0, 0, 0);
        actionsCard.addView(autoLocationSegmentBlock, autoLocationRadioGroupParams);

        profileSaveLocationButton.setOnClickListener(v -> {
            if (apiService == null || !apiService.isLoggedIn()) {
                Toast.makeText(context, "Önce giriş yapmalısınız", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isNetworkAvailable()) {
                Toast.makeText(context, "İnternet bağlantısı yok", Toast.LENGTH_SHORT).show();
                callback.log("İnternet bağlantısı yok, konum kaydedilemedi");
                return;
            }

            profileSaveLocationButton.setEnabled(false);
            UiStyles.clearButtonCompoundDrawables(profileSaveLocationButton);
            profileSaveLocationButton.setText("Konum alınıyor...");

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions((android.app.Activity) context,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
                profileSaveLocationButton.setEnabled(true);
                styleSaveLocationButtonDefault();
                Toast.makeText(context, "Konum izni gerekli", Toast.LENGTH_SHORT).show();
                return;
            }

            if (locationManager == null) {
                locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            }

            try {
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation == null) {
                    lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }

                if (lastLocation != null) {
                    double latitude = lastLocation.getLatitude();
                    double longitude = lastLocation.getLongitude();

                    apiService.saveCoordinates(latitude, longitude, "Current Location", "Mevcut konum", new ProfileApiService.ApiCallback() {
                        @Override
                        public void onSuccess(String message, JSONObject data) {
                            handler.post(() -> {
                                profileSaveLocationButton.setEnabled(true);
                                styleSaveLocationButtonDefault();
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                                callback.log("Konum kaydedildi: " + latitude + ", " + longitude);
                            });
                        }

                        @Override
                        public void onError(String error) {
                            handler.post(() -> {
                                profileSaveLocationButton.setEnabled(true);
                                styleSaveLocationButtonDefault();
                                Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
                                callback.log("Konum kaydetme hatası: " + error);
                            });
                        }
                    });
                } else {
                    UiStyles.clearButtonCompoundDrawables(profileSaveLocationButton);
                    profileSaveLocationButton.setText("Konum bekleniyor...");
                    locationListener = new LocationListener() {
                        @Override
                        public void onLocationChanged(Location location) {
                            if (location != null && apiService != null) {
                                double latitude = location.getLatitude();
                                double longitude = location.getLongitude();
                                apiService.saveCoordinates(latitude, longitude, "Current Location", "Mevcut konum", new ProfileApiService.ApiCallback() {
                                    @Override
                                    public void onSuccess(String message, JSONObject data) {
                                        handler.post(() -> {
                                            profileSaveLocationButton.setEnabled(true);
                                            styleSaveLocationButtonDefault();
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                                            callback.log("Konum kaydedildi: " + latitude + ", " + longitude);
                                        });
                                    }

                                    @Override
                                    public void onError(String error) {
                                        handler.post(() -> {
                                            profileSaveLocationButton.setEnabled(true);
                                            styleSaveLocationButtonDefault();
                                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
                                            callback.log("Konum kaydetme hatası: " + error);
                                        });
                                    }
                                });
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

                    handler.postDelayed(() -> {
                        if (locationManager != null && locationListener != null) {
                            locationManager.removeUpdates(locationListener);
                        }
                        profileSaveLocationButton.setEnabled(true);
                        styleSaveLocationButtonDefault();
                        Toast.makeText(context, "Konum alınamadı, lütfen GPS'in açık olduğundan emin olun", Toast.LENGTH_LONG).show();
                    }, 10000);
                }
            } catch (Exception e) {
                profileSaveLocationButton.setEnabled(true);
                styleSaveLocationButtonDefault();
                Toast.makeText(context, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                callback.log("Konum alma hatası: " + e.getMessage());
            }
        });
    }

    private void updateProfileLoginStatus() {
        if (profileLoginStatusText == null || apiService == null) {
            return;
        }

        boolean isLoggedIn = apiService.isLoggedIn();

        if (isLoggedIn) {
            String email = apiService.getUserEmail();
            profileLoginStatusText.setText("Giriş Mevcut\nE-posta: " + (email != null ? email : "Bilinmiyor"));

            if (profileButtonsContainer != null) profileButtonsContainer.setVisibility(android.view.View.GONE);
            if (profileEmailLabel != null) profileEmailLabel.setVisibility(android.view.View.GONE);
            if (profileEmailInput != null) profileEmailInput.setVisibility(android.view.View.GONE);
            if (profileCodeLabel != null) profileCodeLabel.setVisibility(android.view.View.GONE);
            if (profileCodeInput != null) profileCodeInput.setVisibility(android.view.View.GONE);

            if (profileLogoutButton != null) profileLogoutButton.setVisibility(android.view.View.VISIBLE);
            if (profilePlatformButton != null) profilePlatformButton.setVisibility(android.view.View.VISIBLE);

            if (profileSaveDataButton != null) profileSaveDataButton.setEnabled(true);
            if (profileLoadDataButton != null) profileLoadDataButton.setEnabled(true);
            if (profileSaveLocationButton != null) profileSaveLocationButton.setEnabled(true);
            if (profileAutoLocationSegmentHandle != null) {
                profileAutoLocationSegmentHandle.setInteractionEnabled(true);
            }

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

            if (profileButtonsContainer != null) profileButtonsContainer.setVisibility(android.view.View.VISIBLE);
            if (profileEmailLabel != null) profileEmailLabel.setVisibility(android.view.View.VISIBLE);
            if (profileEmailInput != null) {
                profileEmailInput.setVisibility(android.view.View.VISIBLE);
                profileEmailInput.setEnabled(true);
            }
            if (profileCodeLabel != null) profileCodeLabel.setVisibility(android.view.View.VISIBLE);
            if (profileCodeInput != null) {
                profileCodeInput.setVisibility(android.view.View.VISIBLE);
                profileCodeInput.setEnabled(true);
            }

            if (profileLogoutButton != null) profileLogoutButton.setVisibility(android.view.View.GONE);
            if (profilePlatformButton != null) profilePlatformButton.setVisibility(android.view.View.GONE);

            if (profileSaveDataButton != null) profileSaveDataButton.setEnabled(false);
            if (profileLoadDataButton != null) profileLoadDataButton.setEnabled(false);
            if (profileSaveLocationButton != null) profileSaveLocationButton.setEnabled(false);
            if (profileAutoLocationSegmentHandle != null) {
                profileAutoLocationSegmentHandle.setInteractionEnabled(false);
            }

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

    private void showPlatformQRDialog(String url) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Platforma Gir");

        LinearLayout dialogLayout = new LinearLayout(context);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(32, 32, 32, 32);
        dialogLayout.setGravity(android.view.Gravity.CENTER);

        android.widget.ImageView qrImageView = new android.widget.ImageView(context);
        qrImageView.setId(android.view.View.generateViewId());
        qrImageView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        qrImageView.setAdjustViewBounds(true);

        int qrSize = (int) (300 * context.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.gravity = android.view.Gravity.CENTER;
        qrParams.setMargins(0, 0, 0, 0);
        dialogLayout.addView(qrImageView, qrParams);

        builder.setView(dialogLayout);
        builder.setPositiveButton("Kapat", null);

        AlertDialog dialog = builder.create();
        dialog.show();

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

                handler.post(() -> qrImageView.setImageBitmap(bitmap));
            } catch (WriterException e) {
                handler.post(() -> {
                    Toast.makeText(context, "QR kod oluşturulamadı: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    callback.log("QR kod oluşturma hatası: " + e.getMessage());
                });
            }
        }).start();
    }

    private void styleSendCodeButtonDefault() {
        profileSendCodeButton.setText("Kod Gönder");
        UiStyles.setButtonStartIconTinted(profileSendCodeButton, R.drawable.ic_mdi_email_outline,
                ContextCompat.getColor(context, R.color.textPrimary),
                UiStyles.dimenPx(context, R.dimen.spacing_small));
    }

    private void styleSaveLocationButtonDefault() {
        profileSaveLocationButton.setText("Mevcut Konumu Kaydet");
        UiStyles.setButtonStartIconTinted(profileSaveLocationButton, R.drawable.ic_mdi_map_marker,
                ContextCompat.getColor(context, R.color.textPrimary),
                UiStyles.dimenPx(context, R.dimen.spacing_small));
    }

    private String getBluetoothName() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return null;
                }
            }

            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                return bluetoothAdapter.getName();
            }
        } catch (Exception e) {
            callback.log("Bluetooth ismi alma hatası: " + e.getMessage());
        }
        return null;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }
}

