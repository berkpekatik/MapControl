package com.mapcontrol.ui.builder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.manager.FloatingBackButtonManager;
import com.mapcontrol.manager.FloatingProjectionControlsManager;
import com.mapcontrol.manager.FloatingQuickActionsManager;
import com.mapcontrol.service.BootReceiver;

public class SettingsTabBuilder {
    public interface SettingsCallback {
        void log(String message);
        String getCarToken();
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final SettingsCallback callback;
    private final Handler handler;
    private ScrollView scrollView;
    private LinearLayout settingsTabContent;
    private FloatingBackButtonManager floatingBackButtonManager;
    private FloatingProjectionControlsManager floatingProjectionControlsManager;
    private FloatingQuickActionsManager floatingQuickActionsManager;

    public SettingsTabBuilder(Context context, SharedPreferences prefs, SettingsCallback callback) {
        this.context = context;
        this.prefs = prefs;
        this.callback = callback;
        this.handler = new Handler(Looper.getMainLooper());
        build();
    }

    public ScrollView build() {
        scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        scrollView.setPadding(0, 0, 0, 0);
        scrollView.setFillViewport(true);

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        outer.setPadding(margin, margin, margin, margin);

        settingsTabContent = new LinearLayout(context);
        settingsTabContent.setOrientation(LinearLayout.VERTICAL);
        int inner = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        settingsTabContent.setPadding(inner, inner, inner, inner);
        UiStyles.setGlassCardBackground(settingsTabContent);

        outer.addView(settingsTabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        createAppInfoSection(settingsTabContent);
        createBootAutostartSection(settingsTabContent);
        createFloatingBackButtonSection(settingsTabContent);
        createFloatingProjectionControlsSection(settingsTabContent);
        createFloatingQuickActionsSection(settingsTabContent);
        return scrollView;
    }

    private void createBootAutostartSection(LinearLayout parentContainer) {
        TextView sectionTitle = new TextView(context);
        sectionTitle.setText("Sistem açılışı");
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText("Cihaz yeniden başladığında servis ve uygulama ekranının otomatik açılması.");
        sectionDesc.setTextSize(13);
        sectionDesc.setTextColor(ContextCompat.getColor(context, R.color.textHint));
        sectionDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(sectionDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean serviceOn = prefs.getBoolean(BootReceiver.KEY_BOOT_AUTO_START, true);
        UiStyles.addBinarySegmentedControl(context, parentContainer,
                "Açılışta arka plan servisi",
                "Açık", "Kapalı",
                "BOOT sonrası MapControl servisini başlat.",
                "Servis yalnızca uygulamayı elle açınca başlar.",
                serviceOn,
                on -> {
                    prefs.edit().putBoolean(BootReceiver.KEY_BOOT_AUTO_START, on).apply();
                    callback.log("Açılışta servis: " + (on ? "Açık" : "Kapalı"));
                });

        boolean uiOn = prefs.getBoolean(BootReceiver.KEY_BOOT_AUTO_LAUNCH_UI, true);
        UiStyles.addBinarySegmentedControl(context, parentContainer,
                "Açılışta uygulama ekranı",
                "Açık", "Kapalı",
                "Yaklaşık 4 sn sonra ana ekranı aç (cihaza bağlı).",
                "Yalnızca bildirimden veya launcher'dan açın.",
                uiOn,
                on -> {
                    prefs.edit().putBoolean(BootReceiver.KEY_BOOT_AUTO_LAUNCH_UI, on).apply();
                    callback.log("Açılışta ekran: " + (on ? "Açık" : "Kapalı"));
                });
    }

    private void createFloatingBackButtonSection(LinearLayout parentContainer) {
        TextView floatingBackButtonTitle = new TextView(context);
        floatingBackButtonTitle.setText("Floating Back Button");
        floatingBackButtonTitle.setTextSize(18);
        floatingBackButtonTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        floatingBackButtonTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        floatingBackButtonTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(floatingBackButtonTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView floatingBackButtonDesc = new TextView(context);
        floatingBackButtonDesc.setText("Ekranda yüzen bir geri tuşu göster. Sağa sola kaydırabilirsiniz.");
        floatingBackButtonDesc.setTextSize(13);
        floatingBackButtonDesc.setTextColor(ContextCompat.getColor(context, R.color.textHint));
        floatingBackButtonDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(floatingBackButtonDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        floatingBackButtonManager = FloatingBackButtonManager.getInstance(context);
        floatingBackButtonManager.setLogCallback(callback::log);

        final boolean savedEnabled = FloatingBackButtonManager.loadEnabledState(context);

        final UiStyles.BinarySegmentHandle[] floatingHandleRef = new UiStyles.BinarySegmentHandle[1];
        floatingHandleRef[0] = UiStyles.addBinarySegmentedControl(context, parentContainer,
                null,
                "Açık", "Kapalı",
                "Yüzen geri tuşunu göster.",
                "Yüzen geri tuşunu gizle.",
                savedEnabled,
                isEnabled -> {
                    FloatingBackButtonManager.saveEnabledState(context, isEnabled);

                    if (isEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (!android.provider.Settings.canDrawOverlays(context)) {
                                try {
                                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                                    intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(intent);
                                    Toast.makeText(context, "Lütfen 'Diğer uygulamaların üzerinde görüntüleme' iznini açın", Toast.LENGTH_LONG).show();
                                    floatingHandleRef[0].setLeftSelected(false);
                                    return;
                                } catch (Exception e) {
                                    callback.log("İzin ayarlarına gidilemedi: " + e.getMessage());
                                    floatingHandleRef[0].setLeftSelected(false);
                                    return;
                                }
                            }
                        }
                        floatingBackButtonManager.show();
                        callback.log("Floating Back Button açıldı");
                    } else {
                        floatingBackButtonManager.hide();
                        callback.log("Floating Back Button kapatıldı");
                    }
                });

        // RadioGroup.post(check) ile aynı: ilk açılışta kayıtlı duruma göre manager senkronu
        handler.post(() -> {
            boolean enabled = FloatingBackButtonManager.loadEnabledState(context);
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !android.provider.Settings.canDrawOverlays(context)) {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    Toast.makeText(context, "Lütfen 'Diğer uygulamaların üzerinde görüntüleme' iznini açın", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    callback.log("İzin ayarlarına gidilemedi: " + e.getMessage());
                }
                floatingHandleRef[0].setLeftSelected(false);
                return;
            }
            if (enabled) {
                floatingBackButtonManager.show();
            } else {
                floatingBackButtonManager.hide();
            }
        });
    }

    private void createFloatingProjectionControlsSection(LinearLayout parentContainer) {
        TextView title = new TextView(context);
        title.setText("Yüzen yansıtma kontrolleri");
        title.setTextSize(18);
        title.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(16, 24, 16, 8);
        parentContainer.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView desc = new TextView(context);
        desc.setText("Değiştir, Yansıt ve Durdur için yüzen bir çubuk. Konumu sürükleyerek kaydırabilirsiniz. Diğer uygulamaların üzerinde görüntüleme izni gerektirir (geri tuşu ile aynı).");
        desc.setTextSize(13);
        desc.setTextColor(ContextCompat.getColor(context, R.color.textHint));
        desc.setPadding(16, 0, 16, 12);
        parentContainer.addView(desc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        floatingProjectionControlsManager = FloatingProjectionControlsManager.getInstance(context);
        floatingProjectionControlsManager.setLogCallback(callback::log);

        final boolean savedEnabled = FloatingProjectionControlsManager.loadEnabledState(context);
        final UiStyles.BinarySegmentHandle[] projHandleRef = new UiStyles.BinarySegmentHandle[1];
        projHandleRef[0] = UiStyles.addBinarySegmentedControl(context, parentContainer,
                null,
                "Açık", "Kapalı",
                "Yüzen yansıtma çubuğunu göster.",
                "Yüzen yansıtma çubuğunu gizle.",
                savedEnabled,
                isEnabled -> {
                    FloatingProjectionControlsManager.saveEnabledState(context, isEnabled);
                    if (isEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (!android.provider.Settings.canDrawOverlays(context)) {
                                try {
                                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                                    intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(intent);
                                    Toast.makeText(context, "Lütfen 'Diğer uygulamaların üzerinde görüntüleme' iznini açın", Toast.LENGTH_LONG).show();
                                    projHandleRef[0].setLeftSelected(false);
                                    return;
                                } catch (Exception e) {
                                    callback.log("İzin ayarlarına gidilemedi: " + e.getMessage());
                                    projHandleRef[0].setLeftSelected(false);
                                    return;
                                }
                            }
                        }
                        floatingProjectionControlsManager.show();
                        callback.log("Yüzen yansıtma kontrolleri açıldı");
                    } else {
                        floatingProjectionControlsManager.hide();
                        callback.log("Yüzen yansıtma kontrolleri kapatıldı");
                    }
                });

        handler.post(() -> {
            boolean enabled = FloatingProjectionControlsManager.loadEnabledState(context);
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !android.provider.Settings.canDrawOverlays(context)) {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    Toast.makeText(context, "Lütfen 'Diğer uygulamaların üzerinde görüntüleme' iznini açın", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    callback.log("İzin ayarlarına gidilemedi: " + e.getMessage());
                }
                projHandleRef[0].setLeftSelected(false);
                return;
            }
            if (enabled) {
                floatingProjectionControlsManager.show();
            } else {
                floatingProjectionControlsManager.hide();
            }
        });
    }

    private void createFloatingQuickActionsSection(LinearLayout parentContainer) {
        TextView title = new TextView(context);
        title.setText(R.string.floating_qa_settings_title);
        title.setTextSize(18);
        title.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(16, 24, 16, 8);
        parentContainer.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView desc = new TextView(context);
        desc.setText(R.string.floating_qa_settings_desc);
        desc.setTextSize(13);
        desc.setTextColor(ContextCompat.getColor(context, R.color.textHint));
        desc.setPadding(16, 0, 16, 12);
        parentContainer.addView(desc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        floatingQuickActionsManager = FloatingQuickActionsManager.getInstance(context);
        floatingQuickActionsManager.setLogCallback(msg -> callback.log(msg));

        final boolean savedQa = FloatingQuickActionsManager.loadEnabledState(context);
        final UiStyles.BinarySegmentHandle[] qaHandleRef = new UiStyles.BinarySegmentHandle[1];
        qaHandleRef[0] = UiStyles.addBinarySegmentedControl(context, parentContainer,
                null,
                "Açık", "Kapalı",
                "Hızlı işlemler yüzen çubuğunu göster.",
                "Gizle.",
                savedQa,
                isEnabled -> {
                    FloatingQuickActionsManager.saveEnabledState(context, isEnabled);
                    if (isEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (!android.provider.Settings.canDrawOverlays(context)) {
                                try {
                                    Intent intent = new Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                                    intent.setData(
                                            android.net.Uri.parse("package:" + context.getPackageName()));
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(intent);
                                    Toast.makeText(context,
                                            "Lütfen 'Diğer uygulamaların üzerinde görüntüleme' iznini açın",
                                            Toast.LENGTH_LONG).show();
                                    qaHandleRef[0].setLeftSelected(false);
                                    return;
                                } catch (Exception e) {
                                    callback.log("İzin ayarlarına gidilemedi: " + e.getMessage());
                                    qaHandleRef[0].setLeftSelected(false);
                                    return;
                                }
                            }
                        }
                        floatingQuickActionsManager.show();
                        callback.log("Yüzen hızlı işlemler açıldı");
                    } else {
                        floatingQuickActionsManager.hide();
                        callback.log("Yüzen hızlı işlemler kapatıldı");
                    }
                });

        handler.post(() -> {
            boolean enabled = FloatingQuickActionsManager.loadEnabledState(context);
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !android.provider.Settings.canDrawOverlays(context)) {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    Toast.makeText(context,
                            "Lütfen 'Diğer uygulamaların üzerinde görüntüleme' iznini açın", Toast.LENGTH_LONG)
                            .show();
                } catch (Exception e) {
                    callback.log("İzin ayarlarına gidilemedi: " + e.getMessage());
                }
                qaHandleRef[0].setLeftSelected(false);
                return;
            }
            if (enabled) {
                floatingQuickActionsManager.show();
            } else {
                floatingQuickActionsManager.hide();
            }
        });
    }

    private void createAppInfoSection(LinearLayout parentContainer) {
        TextView appInfoTitle = new TextView(context);
        appInfoTitle.setText("Uygulama Hakkında");
        appInfoTitle.setTextSize(18);
        appInfoTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        appInfoTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        appInfoTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(appInfoTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView versionTitle = new TextView(context);
        versionTitle.setText("Versiyon");
        versionTitle.setTextSize(16);
        versionTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        versionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        versionTitle.setPadding(16, 16, 16, 8);
        parentContainer.addView(versionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView versionText = new TextView(context);
        try {
            String versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            versionText.setText("Mevcut Versiyon: " + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("Mevcut Versiyon: Bilinmiyor");
        }
        versionText.setTextSize(14);
        versionText.setTextColor(ContextCompat.getColor(context, R.color.textHint));
        versionText.setPadding(16, 0, 16, 16);
        parentContainer.addView(versionText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView latestVersionText = new TextView(context);
        latestVersionText.setId(View.generateViewId());
        latestVersionText.setText("Güncel Versiyon: Yükleniyor...");
        latestVersionText.setTextSize(14);
        latestVersionText.setTextColor(ContextCompat.getColor(context, R.color.textHint));
        latestVersionText.setPadding(16, 0, 16, 16);
        parentContainer.addView(latestVersionText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView freeInstallTitle = new TextView(context);
        freeInstallTitle.setText("Kurulum");
        freeInstallTitle.setTextSize(16);
        freeInstallTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        freeInstallTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        freeInstallTitle.setPadding(16, 16, 16, 8);
        parentContainer.addView(freeInstallTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView freeInstallText = new TextView(context);
        freeInstallText.setText("Bu uygulama https://vnoisy.dev adresinden ücretsiz olarak kurulabilir.");
        freeInstallText.setTextSize(14);
        freeInstallText.setTextColor(ContextCompat.getColor(context, R.color.textHint));
        freeInstallText.setPadding(16, 0, 16, 16);
        parentContainer.addView(freeInstallText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView changelogTitle = new TextView(context);
        changelogTitle.setText("Güncelleme Notları");
        changelogTitle.setTextSize(16);
        changelogTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        changelogTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        changelogTitle.setPadding(16, 16, 16, 8);
        parentContainer.addView(changelogTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView changelogText = new TextView(context);
        changelogText.setId(View.generateViewId());
        changelogText.setText("Yükleniyor...");
        changelogText.setTextSize(14);
        changelogText.setTextColor(ContextCompat.getColor(context, R.color.textHint));
        changelogText.setPadding(16, 0, 16, 16);
        changelogText.setLineSpacing(4, 1.0f);
        parentContainer.addView(changelogText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        fetchAnnouncement(latestVersionText, changelogText);
    }

    private void fetchAnnouncement(TextView latestVersionView, TextView changelogView) {
        new Thread(() -> {
            try {
                String currentVersion = "1.0.0";
                try {
                    currentVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
                } catch (PackageManager.NameNotFoundException ignored) {
                }

                String token = callback.getCarToken();
                URL url = new URL("https://api.vnoisy.dev/api/announcement/get");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

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

                    String responseStr = response.toString().trim();
                    if (responseStr.startsWith("[")) {
                        JSONArray announcements = new JSONArray(responseStr);
                        StringBuilder changelogBuilder = new StringBuilder();
                        String latestVersionFromApi = currentVersion;

                        for (int i = 0; i < announcements.length(); i++) {
                            JSONObject announcement = announcements.getJSONObject(i);
                            String version = announcement.optString("version", "");
                            String title = announcement.optString("title", "");
                            String message = announcement.optString("message", "");

                            if (!version.isEmpty() && compareVersions(version, latestVersionFromApi) > 0) {
                                latestVersionFromApi = version;
                            }

                            if (!title.isEmpty() || !message.isEmpty()) {
                                if (changelogBuilder.length() > 0) {
                                    changelogBuilder.append("\n\n");
                                }
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
                        final String finalChangelog = changelogBuilder.length() > 0
                                ? changelogBuilder.toString() : "Güncelleme notu bulunamadı.";

                        handler.post(() -> {
                            latestVersionView.setText("Güncel Versiyon: " + finalLatestVersion);
                            changelogView.setText(finalChangelog);
                        });
                    } else {
                        JSONObject json = new JSONObject(responseStr);
                        String latestVersion = json.optString("version", currentVersion);
                        String changelog = json.optString("changelog", json.optString("message", "Güncelleme notu bulunamadı."));

                        handler.post(() -> {
                            latestVersionView.setText("Güncel Versiyon: " + latestVersion);
                            CharSequence parsed = parseMarkdown(changelog);
                            changelogView.setText(parsed != null ? parsed : changelog);
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
                callback.log("fetchAnnouncement hatası: " + e.getMessage());
            }
        }).start();
    }

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
            return v1.compareTo(v2);
        }
    }

    private android.text.SpannableString parseMarkdown(String markdownText) {
        android.text.SpannableStringBuilder builder = new android.text.SpannableStringBuilder();
        String[] lines = markdownText.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("### ")) {
                String titleText = line.substring(4).trim();
                int start = builder.length();
                builder.append(titleText);
                int end = builder.length();
                builder.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new android.text.style.RelativeSizeSpan(1.3f),
                        start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                processBoldText(builder, line);
            }
            if (i < lines.length - 1) {
                builder.append("\n");
            }
        }
        return new android.text.SpannableString(builder);
    }

    private void processBoldText(android.text.SpannableStringBuilder builder, String line) {
        java.util.regex.Pattern boldPattern = java.util.regex.Pattern.compile("\\*\\*(.*?)\\*\\*");
        java.util.regex.Matcher matcher = boldPattern.matcher(line);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                builder.append(line.substring(lastEnd, matcher.start()));
            }
            String boldText = matcher.group(1);
            int boldStart = builder.length();
            builder.append(boldText);
            int boldEnd = builder.length();
            builder.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    boldStart, boldEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            lastEnd = matcher.end();
        }
        if (lastEnd < line.length()) {
            builder.append(line.substring(lastEnd));
        }
    }

    public LinearLayout getSettingsTabContent() {
        return settingsTabContent;
    }

    public ScrollView getScrollView() {
        return scrollView != null ? scrollView : build();
    }
}
