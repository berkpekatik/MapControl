package com.mapcontrol.ui.builder;
import android.app.AlertDialog;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import com.mapcontrol.R;

public class WifiTabBuilder {
    public interface WifiCallback {
        void log(String message);
    }

    private final Context context;
    private final WifiCallback callback;

    private WifiManager wifiManager;
    private LinearLayout wifiTabContent;
    private LinearLayout wifiListContainer;
    private Button btnWifiToggle;
    private Button btnScanWifi;
    private TextView wifiStatusLine;
    private TextView wifiStatusIcon;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public WifiTabBuilder(Context context, WifiCallback callback) {
        this.context = context;
        this.callback = callback;
        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        build();
    }

    public LinearLayout build() {
        wifiTabContent = new LinearLayout(context);
        wifiTabContent.setOrientation(LinearLayout.VERTICAL);
        wifiTabContent.setBackgroundColor(0xFF0F1419);

        wifiStatusLine = new TextView(context);
        wifiStatusLine.setText("Bağlı değil");
        wifiStatusLine.setTextSize(12);
        wifiStatusLine.setTextColor(0xFF9DABB9);
        wifiStatusLine.setPadding(16, 8, 16, 8);
        wifiStatusLine.setBackgroundColor(0xFF0A0F14);
        LinearLayout.LayoutParams statusLineParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wifiTabContent.addView(wifiStatusLine, statusLineParams);

        LinearLayout controlButtonsRow = new LinearLayout(context);
        controlButtonsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlButtonsRow.setPadding(16, 12, 16, 12);
        controlButtonsRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        controlButtonsRow.setBackgroundColor(0xFF0F1419);

        btnWifiToggle = new Button(context);
        btnWifiToggle.setText("Wi-Fi Aç");
        btnWifiToggle.setTextSize(14);
        btnWifiToggle.setTextColor(0xFFFFFFFF);
        btnWifiToggle.setTypeface(null, android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable toggleBg = new android.graphics.drawable.GradientDrawable();
        toggleBg.setColor(0xFF3DAEA8);
        toggleBg.setCornerRadius(8);
        btnWifiToggle.setBackground(toggleBg);
        btnWifiToggle.setPadding(24, 12, 24, 12);
        btnWifiToggle.setOnClickListener(v -> toggleWifi());
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        toggleParams.setMargins(0, 0, 8, 0);
        controlButtonsRow.addView(btnWifiToggle, toggleParams);

        btnScanWifi = new Button(context);
        btnScanWifi.setText("Yenile");
        btnScanWifi.setTextSize(14);
        btnScanWifi.setTextColor(0xFFFFFFFF);
        btnScanWifi.setTypeface(null, android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable scanBg = new android.graphics.drawable.GradientDrawable();
        scanBg.setColor(0xFF1976D2);
        scanBg.setCornerRadius(8);
        btnScanWifi.setBackground(scanBg);
        btnScanWifi.setPadding(24, 12, 24, 12);
        btnScanWifi.setOnClickListener(v -> scanWifiNetworks());
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        controlButtonsRow.addView(btnScanWifi, scanParams);

        wifiTabContent.addView(controlButtonsRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView wifiListScrollView = new ScrollView(context);
        wifiListScrollView.setBackgroundColor(0xFF0F1419);
        wifiListScrollView.setPadding(16, 8, 16, 16);

        wifiListContainer = new LinearLayout(context);
        wifiListContainer.setOrientation(LinearLayout.VERTICAL);
        wifiListScrollView.addView(wifiListContainer);

        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        wifiTabContent.addView(wifiListScrollView, listParams);

        updateWifiStatus();
        return wifiTabContent;
    }

    public LinearLayout getTabContent() {
        return wifiTabContent;
    }

    public LinearLayout buildTopBarIcon() {
        LinearLayout wifiIconContainer = new LinearLayout(context);
        wifiIconContainer.setOrientation(LinearLayout.HORIZONTAL);
        wifiIconContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        wifiIconContainer.setPadding(8, 0, 0, 0);

        TextView wifiIcon = new TextView(context);
        wifiIcon.setText("📶");
        wifiIcon.setTextSize(20);
        wifiIcon.setTextColor(0xFFFFFFFF);
        wifiIconContainer.addView(wifiIcon);

        wifiStatusIcon = new TextView(context);
        wifiStatusIcon.setText("●");
        wifiStatusIcon.setTextSize(12);
        wifiStatusIcon.setTextColor(0xFF9DABB9);
        wifiStatusIcon.setPadding(4, 0, 0, 0);
        wifiIconContainer.addView(wifiStatusIcon);

        // İlk renk güncellemesi
        updateWifiStatusIcon();
        return wifiIconContainer;
    }

    public void updateWifiStatus() {
        if (wifiManager == null || btnWifiToggle == null) {
            return;
        }

        try {
            boolean isWifiEnabled = wifiManager.isWifiEnabled();

            if (isWifiEnabled) {
                btnWifiToggle.setText("■ Wi-Fi Kapat");
                android.graphics.drawable.GradientDrawable toggleBg = new android.graphics.drawable.GradientDrawable();
                toggleBg.setColor(0xFF3DAEA8);
                toggleBg.setCornerRadius(8);
                btnWifiToggle.setBackground(toggleBg);
            } else {
                btnWifiToggle.setText("Wi-Fi Aç");
                android.graphics.drawable.GradientDrawable toggleBg = new android.graphics.drawable.GradientDrawable();
                toggleBg.setColor(0xFF1976D2);
                toggleBg.setCornerRadius(8);
                btnWifiToggle.setBackground(toggleBg);
            }

            callback.log("Wi-Fi durumu güncellendi: " + (isWifiEnabled ? "AÇIK" : "KAPALI"));
            updateWifiStatusIcon();

            if (isWifiEnabled) {
                handler.postDelayed(this::scanWifiNetworks, 500);
            }
        } catch (Exception e) {
            callback.log("Wi-Fi durumu kontrol hatası: " + e.getMessage());
            if (btnWifiToggle != null) {
                btnWifiToggle.setText("Hata");
                android.graphics.drawable.GradientDrawable errorBg = new android.graphics.drawable.GradientDrawable();
                errorBg.setColor(0xFFFF9800);
                errorBg.setCornerRadius(8);
                btnWifiToggle.setBackground(errorBg);
            }
            if (wifiStatusIcon != null) {
                wifiStatusIcon.setTextColor(0xFFFF9800);
            }
        }
    }

    private void updateWifiStatusIcon() {
        if (wifiStatusIcon == null || wifiManager == null) return;
        try {
            if (!wifiManager.isWifiEnabled()) {
                wifiStatusIcon.setTextColor(0xFF9DABB9);
                return;
            }
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String ssid = wifiInfo != null ? wifiInfo.getSSID() : null;
            boolean connected = ssid != null && !ssid.equals("<unknown ssid>") && !ssid.equals("0x") && !ssid.equals("null") && !ssid.equals("\"\"");
            wifiStatusIcon.setTextColor(connected ? 0xFF3DAEA8 : 0xFF9DABB9);
        } catch (Exception ignore) {
            wifiStatusIcon.setTextColor(0xFF9DABB9);
        }
    }

    private void toggleWifi() {
        if (wifiManager == null) {
            callback.log("WifiManager bulunamadı");
            return;
        }

        try {
            boolean currentState = wifiManager.isWifiEnabled();
            boolean newState = !currentState;

            callback.log("Mevcut durum: " + (currentState ? "AÇIK" : "KAPALI"));
            callback.log("Hedef durum: " + (newState ? "AÇIK" : "KAPALI"));
            callback.log("UID: " + android.os.Process.myUid());

            boolean result = wifiManager.setWifiEnabled(newState);
            callback.log("setWifiEnabled(" + newState + ") sonucu: " + result);

            handler.postDelayed(() -> {
                updateWifiStatus();
                if (newState) {
                    scanWifiNetworks();
                } else {
                    if (wifiListContainer != null) {
                        wifiListContainer.removeAllViews();
                        TextView emptyText = new TextView(context);
                        emptyText.setText("Wi-Fi kapalı");
                        emptyText.setTextColor(0xFFFF9800);
                        emptyText.setTextSize(14);
                        emptyText.setPadding(8, 8, 8, 8);
                        wifiListContainer.addView(emptyText);
                    }
                }
            }, 1000);
        } catch (Exception e) {
            callback.log("Wi-Fi toggle hatası: " + e.getMessage());
        }
    }

    private void scanWifiNetworks() {
        if (wifiManager == null) {
            callback.log("WifiManager bulunamadı");
            Toast.makeText(context, "Wi-Fi yöneticisi bulunamadı", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            callback.log("Wi-Fi kapalı, önce Wi-Fi'yi açın");
            Toast.makeText(context, "Wi-Fi kapalı, önce Wi-Fi'yi açın", Toast.LENGTH_SHORT).show();
            if (wifiListContainer != null) {
                wifiListContainer.removeAllViews();
                TextView emptyText = new TextView(context);
                emptyText.setText("Wi-Fi kapalı");
                emptyText.setTextColor(0xFFFF9800);
                emptyText.setTextSize(14);
                emptyText.setPadding(8, 8, 8, 8);
                wifiListContainer.addView(emptyText);
            }
            return;
        }

        try {
            displayWifiNetworks();
            callback.log("Wi-Fi ağları taranıyor...");
            btnScanWifi.setEnabled(false);
            btnScanWifi.setText("🔍");

            boolean scanStarted = wifiManager.startScan();
            if (scanStarted) {
                handler.postDelayed(() -> {
                    displayWifiNetworks();
                    btnScanWifi.setEnabled(true);
                    btnScanWifi.setText("Yenile");
                }, 2000);
            } else {
                callback.log("Wi-Fi taraması başlatılamadı");
                btnScanWifi.setEnabled(true);
                btnScanWifi.setText("Yenile");
            }
        } catch (Exception e) {
            callback.log("Wi-Fi tarama hatası: " + e.getMessage());
            Toast.makeText(context, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            btnScanWifi.setEnabled(true);
            btnScanWifi.setText("🔍 Wi-Fi Ağlarını Tara");
        }
    }

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
                LinearLayout emptyCard = new LinearLayout(context);
                emptyCard.setOrientation(LinearLayout.VERTICAL);
                emptyCard.setGravity(android.view.Gravity.CENTER);
                emptyCard.setPadding(32, 48, 32, 48);

                TextView noNetworks = new TextView(context);
                noNetworks.setText("📡\n\nHiçbir Wi-Fi ağı bulunamadı\n\nLütfen tarama yapın");
                noNetworks.setTextColor(0xFF6B7280);
                noNetworks.setTextSize(15);
                noNetworks.setGravity(android.view.Gravity.CENTER);
                emptyCard.addView(noNetworks);

                wifiListContainer.addView(emptyCard);
                callback.log("Wi-Fi ağı bulunamadı");
                return;
            }

            for (ScanResult result : scanResults) {
                if (result.SSID != null && !result.SSID.isEmpty() && result.SSID.equals(connectedSSID)) {
                    addWifiNetworkItem(result, true);
                }
            }

            for (ScanResult result : scanResults) {
                if (result.SSID != null && !result.SSID.isEmpty() && !result.SSID.equals(connectedSSID)) {
                    addWifiNetworkItem(result, false);
                }
            }

            callback.log("" + scanResults.size() + " Wi-Fi ağı bulundu");
        } catch (Exception e) {
            callback.log("Wi-Fi listesi hatası: " + e.getMessage());
            Toast.makeText(context, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void addWifiNetworkItem(ScanResult result, boolean isConnected) {
        LinearLayout wifiCard = new LinearLayout(context);
        wifiCard.setOrientation(LinearLayout.HORIZONTAL);
        wifiCard.setPadding(20, 20, 20, 20);
        wifiCard.setGravity(android.view.Gravity.CENTER_VERTICAL);
        wifiCard.setClickable(true);
        wifiCard.setFocusable(true);

        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(0xFF1A2330);
        cardBg.setCornerRadius(16);
        wifiCard.setBackground(cardBg);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 12);
        wifiCard.setMinimumHeight((int) (130 * context.getResources().getDisplayMetrics().density));

        LinearLayout iconContainer = new LinearLayout(context);
        iconContainer.setOrientation(LinearLayout.VERTICAL);
        iconContainer.setGravity(android.view.Gravity.CENTER);

        TextView iconText = new TextView(context);
        iconText.setText("📶");
        iconText.setTextSize(36);
        iconText.setTextColor(isConnected ? 0xFF3DAEA8 : 0xFF9DABB9);
        iconContainer.addView(iconText);

        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        iconParams.setMargins(0, 0, 20, 0);
        wifiCard.addView(iconContainer, iconParams);

        LinearLayout infoContainer = new LinearLayout(context);
        infoContainer.setOrientation(LinearLayout.VERTICAL);
        infoContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView ssidText = new TextView(context);
        String ssid = result.SSID != null ? result.SSID : "(Gizli Ağ)";
        ssidText.setText(ssid);
        ssidText.setTextColor(0xFFFFFFFF);
        ssidText.setTextSize(18);
        ssidText.setTypeface(null, android.graphics.Typeface.NORMAL);
        infoContainer.addView(ssidText);

        LinearLayout detailRow = new LinearLayout(context);
        detailRow.setOrientation(LinearLayout.HORIZONTAL);
        detailRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        detailRow.setPadding(0, 6, 0, 0);

        int level = result.level;
        String security = getSecurityType(result);
        String signalQuality;
        if (level > -50) signalQuality = "Güçlü sinyal";
        else if (level > -70) signalQuality = "İyi sinyal";
        else if (level > -85) signalQuality = "Orta sinyal";
        else signalQuality = "Zayıf sinyal";

        TextView detailText = new TextView(context);
        if (isConnected) {
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
            detailText.setTextColor(0xFF3DAEA8);
        } else {
            detailText.setText(security + " • " + signalQuality);
            detailText.setTextColor(0xFF9DABB9);
        }
        detailText.setTextSize(13);
        detailRow.addView(detailText);

        infoContainer.addView(detailRow);

        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        wifiCard.addView(infoContainer, infoParams);

        if (isConnected) {
            TextView checkIcon = new TextView(context);
            checkIcon.setText("✔︎");
            checkIcon.setTextSize(20);
            checkIcon.setTextColor(0xFF3DAEA8);
            checkIcon.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                    (int) (40 * context.getResources().getDisplayMetrics().density),
                    (int) (40 * context.getResources().getDisplayMetrics().density));
            checkParams.setMargins(8, 0, 0, 0);
            wifiCard.addView(checkIcon, checkParams);
        }

        wifiCard.setOnClickListener(v -> {
            if (isConnected) {
                disconnectFromWifi();
            } else {
                connectToWifi(result);
            }
        });

        wifiListContainer.addView(wifiCard, cardParams);
    }

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

    private void connectToWifi(ScanResult result) {
        if (wifiManager == null) {
            callback.log("WifiManager bulunamadı");
            return;
        }

        String ssid = result.SSID;
        if (ssid == null || ssid.isEmpty()) {
            callback.log("SSID boş");
            return;
        }

        String capabilities = result.capabilities;
        boolean isSecure = capabilities != null &&
                (capabilities.contains("WPA") || capabilities.contains("WEP"));

        if (isSecure) {
            showPasswordDialog(result);
        } else {
            connectToOpenNetwork(result);
        }
    }

    private void showPasswordDialog(ScanResult result) {
        LinearLayout dialogLayout = new LinearLayout(context);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(40, 30, 40, 30);
        dialogLayout.setBackgroundColor(0xFF2C2C2C);

        TextView titleView = new TextView(context);
        titleView.setText("🔒 Wi-Fi Şifresi");
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTextSize(22);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setPadding(0, 0, 0, 20);
        dialogLayout.addView(titleView);

        TextView networkName = new TextView(context);
        networkName.setText("📡 " + result.SSID);
        networkName.setTextColor(0xFFFFA726);
        networkName.setTextSize(18);
        networkName.setTypeface(null, android.graphics.Typeface.BOLD);
        networkName.setPadding(0, 0, 0, 20);
        dialogLayout.addView(networkName);

        EditText passwordInput = new EditText(context);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setHint("🔑 Şifre giriniz");
        passwordInput.setTextColor(0xFFFFFFFF);
        passwordInput.setHintTextColor(0xFF808080);
        passwordInput.setTextSize(17);
        passwordInput.setPadding(40, 30, 40, 30);
        passwordInput.setBackgroundColor(0xFF1E1E1E);
        dialogLayout.addView(passwordInput);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogLayout)
                .setPositiveButton("✅ Bağlan", (d, which) -> {
                    String password = passwordInput.getText().toString();
                    if (password.isEmpty()) {
                        Toast.makeText(context, "⚠️ Şifre boş olamaz", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    connectToSecureNetwork(result, password);
                })
                .setNegativeButton("❌ İptal", null)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF4CAF50);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(17);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTypeface(null, android.graphics.Typeface.BOLD);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFFF44336);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(17);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTypeface(null, android.graphics.Typeface.BOLD);
    }

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
                    callback.log("Açık ağa bağlanılıyor: " + result.SSID);
                    Toast.makeText(context, "Bağlanılıyor: " + result.SSID, Toast.LENGTH_SHORT).show();
                    handler.postDelayed(this::scanWifiNetworks, 2000);
                } else {
                    callback.log("Ağ etkinleştirilemedi: " + result.SSID);
                    Toast.makeText(context, "Bağlantı hatası", Toast.LENGTH_SHORT).show();
                }
            } else {
                callback.log("Ağ eklenemedi: " + result.SSID);
                Toast.makeText(context, "Ağ eklenemedi", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            callback.log("Açık ağ bağlantı hatası: " + e.getMessage());
            Toast.makeText(context, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void connectToSecureNetwork(ScanResult result, String password) {
        try {
            String capabilities = result.capabilities;
            WifiConfiguration config = new WifiConfiguration();
            config.SSID = "\"" + result.SSID + "\"";

            if (capabilities.contains("WPA3")) {
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.SAE);
                config.preSharedKey = "\"" + password + "\"";
            } else if (capabilities.contains("WPA2")) {
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                config.preSharedKey = "\"" + password + "\"";
            } else if (capabilities.contains("WPA")) {
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                config.preSharedKey = "\"" + password + "\"";
            } else if (capabilities.contains("WEP")) {
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
                List<WifiConfiguration> existingConfigs = wifiManager.getConfiguredNetworks();
                if (existingConfigs != null) {
                    for (WifiConfiguration existingConfig : existingConfigs) {
                        if (existingConfig.SSID != null && existingConfig.SSID.equals("\"" + result.SSID + "\"")) {
                            networkId = existingConfig.networkId;
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
                    callback.log("Şifreli ağa bağlanılıyor: " + result.SSID);
                    Toast.makeText(context, "Bağlanılıyor: " + result.SSID, Toast.LENGTH_SHORT).show();
                    handler.postDelayed(this::scanWifiNetworks, 2000);
                } else {
                    callback.log("Ağ etkinleştirilemedi: " + result.SSID);
                    Toast.makeText(context, "Bağlantı hatası", Toast.LENGTH_SHORT).show();
                }
            } else {
                callback.log("Ağ eklenemedi: " + result.SSID);
                Toast.makeText(context, "Ağ eklenemedi", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            callback.log("Şifreli ağ bağlantı hatası: " + e.getMessage());
            Toast.makeText(context, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectFromWifi() {
        if (wifiManager == null) {
            callback.log("WifiManager bulunamadı");
            return;
        }

        try {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getNetworkId() != -1) {
                String ssid = wifiInfo.getSSID().replace("\"", "");
                boolean disconnected = wifiManager.disconnect();
                if (disconnected) {
                    callback.log("Bağlantı kesildi: " + ssid);
                    Toast.makeText(context, "Bağlantı kesildi: " + ssid, Toast.LENGTH_SHORT).show();
                    handler.postDelayed(this::scanWifiNetworks, 1000);
                } else {
                    callback.log("Bağlantı kesilemedi");
                    Toast.makeText(context, "Bağlantı kesilemedi", Toast.LENGTH_SHORT).show();
                }
            } else {
                callback.log("Aktif bağlantı yok");
                Toast.makeText(context, "Aktif bağlantı yok", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            callback.log("Bağlantı kesme hatası: " + e.getMessage());
            Toast.makeText(context, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}

