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
import android.widget.Button;
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
import com.mapcontrol.nav.YandexClusterNavCoordinator;
import com.mapcontrol.nav.YandexClusterNavOverlay;
import com.mapcontrol.service.BootReceiver;
import com.mapcontrol.service.GlobalBackService;
import com.mapcontrol.service.MapControlService;
import com.mapcontrol.util.LauncherModeManager;
import com.mapcontrol.vehicle.material.MaterialVehiclePreferences;
import com.mapcontrol.vehicle.material.MaterialVehicleResources;
import com.mapcontrol.vehicle.material.VehicleMaterialPickerDialog;

public class SettingsTabBuilder {
    public interface SettingsCallback {
        void log(String message);
        String getCarToken();
        void onLauncherModeChanged(boolean enabled);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final SettingsCallback callback;
    private final Handler handler;
    private ScrollView scrollView;
    private LinearLayout settingsTabContent;
    private FloatingBackButtonManager floatingBackButtonManager;
    private UiStyles.BinarySegmentHandle launcherModeSegmentHandle;
    private TextView launcherHomeSettingsLink;

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
        createWifiStabilizeOnScreenOnSection(settingsTabContent);
        createFloatingBackButtonSection(settingsTabContent);
        createYandexClusterNavSection(settingsTabContent);
        createLauncherModeSection(settingsTabContent);
        createVehicleModelSection(settingsTabContent);
        return scrollView;
    }

    private void createYandexClusterNavSection(LinearLayout parentContainer) {
        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(R.string.yandex_cluster_nav_section_title);
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText(R.string.yandex_cluster_nav_section_desc);
        sectionDesc.setTextSize(13);
        sectionDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        sectionDesc.setPadding(16, 0, 16, 12);
        sectionDesc.setLineSpacing(3, 1.05f);
        parentContainer.addView(sectionDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final boolean savedEnabled = YandexClusterNavOverlay.isEnabled(context);
        final UiStyles.BinarySegmentHandle[] handleRef = new UiStyles.BinarySegmentHandle[1];
        handleRef[0] = UiStyles.addBinarySegmentedControl(context, parentContainer,
                null,
                "Açık", "Kapalı",
                context.getString(R.string.yandex_cluster_nav_help_on),
                context.getString(R.string.yandex_cluster_nav_help_off),
                savedEnabled,
                isEnabled -> {
                    if (!isEnabled) {
                        YandexClusterNavCoordinator.deactivate(context);
                        callback.log("Yandex cluster nav: Kapalı");
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            && !android.provider.Settings.canDrawOverlays(context)) {
                        try {
                            Intent intent = new Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                            intent.setData(android.net.Uri.parse(
                                    "package:" + context.getPackageName()));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                            Toast.makeText(context,
                                    "Lütfen 'Diğer uygulamaların üzerinde görüntüleme' iznini açın",
                                    Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            callback.log("İzin ayarlarına gidilemedi: " + e.getMessage());
                        }
                        handleRef[0].setLeftSelected(false);
                        return;
                    }
                    if (!GlobalBackService.isRegisteredInSystemAccessibilitySettings(context)) {
                        YandexClusterNavCoordinator.openAccessibilitySettings(context);
                        Toast.makeText(context,
                                R.string.yandex_cluster_nav_toast_accessibility,
                                Toast.LENGTH_LONG).show();
                        handleRef[0].setLeftSelected(false);
                        return;
                    }
                    YandexClusterNavCoordinator.ActivateResult result =
                            YandexClusterNavCoordinator.activate(context);
                    callback.log("Yandex cluster nav: Açık — " + result.name());
                    YandexClusterNavCoordinator.showActivateToast(context, result);
                    if (result == YandexClusterNavCoordinator.ActivateResult.SERVICE_CONNECTING) {
                        handler.postDelayed(() -> {
                            YandexClusterNavCoordinator.ActivateResult retry =
                                    YandexClusterNavCoordinator.syncNow(context);
                            YandexClusterNavCoordinator.showActivateToast(context, retry);
                            callback.log("Yandex cluster nav yeniden deneme: " + retry.name());
                        }, 500);
                    }
                });
    }

    private void createBootAutostartSection(LinearLayout parentContainer) {
        TextView sectionTitle = new TextView(context);
        sectionTitle.setText("Sistem açılışı");
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText("Cihaz yeniden başladığında servis ve uygulama ekranının otomatik açılması.");
        sectionDesc.setTextSize(13);
        sectionDesc.setTextColor(UiStyles.color(context, R.color.textHint));
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

    private void createWifiStabilizeOnScreenOnSection(LinearLayout parentContainer) {
        int hPad = UiStyles.dimenPx(context, R.dimen.spacing_medium);

        TextView rowTitle = new TextView(context);
        rowTitle.setText(R.string.wifi_on_screen_on_toggle_title);
        rowTitle.setTextSize(16);
        rowTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        rowTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        rowTitle.setPadding(hPad, UiStyles.dimenPx(context, R.dimen.spacing_large),
                hPad, UiStyles.dimenPx(context, R.dimen.spacing_tiny));
        parentContainer.addView(rowTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(context);
        subtitle.setText(R.string.wifi_on_screen_on_toggle_subtitle);
        subtitle.setTextSize(13);
        subtitle.setTextColor(UiStyles.color(context, R.color.textHint));
        subtitle.setLineSpacing(3, 1.05f);
        subtitle.setPadding(hPad, 0, hPad, UiStyles.dimenPx(context, R.dimen.spacing_small));
        parentContainer.addView(subtitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean on = prefs.getBoolean(MapControlService.KEY_WIFI_STABILIZE_ON_SCREEN_ON, false);
        UiStyles.addBinarySegmentedControl(context, parentContainer,
                null,
                "Evet", "Hayır",
                context.getString(R.string.wifi_on_screen_on_toggle_yes_help),
                context.getString(R.string.wifi_on_screen_on_toggle_no_help),
                on,
                enabled -> {
                    prefs.edit().putBoolean(MapControlService.KEY_WIFI_STABILIZE_ON_SCREEN_ON, enabled).apply();
                    callback.log("Ekran açılınca Wi‑Fi tazele: " + (enabled ? "Evet" : "Hayır"));
                });
    }

    private void createFloatingBackButtonSection(LinearLayout parentContainer) {
        TextView floatingBackButtonTitle = new TextView(context);
        floatingBackButtonTitle.setText("Floating Back Button");
        floatingBackButtonTitle.setTextSize(18);
        floatingBackButtonTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        floatingBackButtonTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        floatingBackButtonTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(floatingBackButtonTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView floatingBackButtonDesc = new TextView(context);
        floatingBackButtonDesc.setText(
                "Kısa dokunuş (sol ok): geri. Uzun bas (sol ok): menüyü aç / menü açıkken kapat. Menüdeki düğmelere dokununca çubuk açık kalır. Çubuğu her yerinden sürükleyerek taşıyın.");
        floatingBackButtonDesc.setTextSize(13);
        floatingBackButtonDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        floatingBackButtonDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(floatingBackButtonDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView iconLegendTitle = new TextView(context);
        iconLegendTitle.setText(R.string.floating_hub_icon_legend_title);
        iconLegendTitle.setTextSize(15);
        iconLegendTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        iconLegendTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        iconLegendTitle.setPadding(16, 8, 16, 4);
        parentContainer.addView(iconLegendTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView iconLegendBody = new TextView(context);
        iconLegendBody.setText(R.string.floating_hub_icon_legend_body);
        iconLegendBody.setTextSize(13);
        iconLegendBody.setTextColor(UiStyles.color(context, R.color.textHint));
        iconLegendBody.setPadding(16, 0, 16, 12);
        iconLegendBody.setLineSpacing(3, 1.05f);
        parentContainer.addView(iconLegendBody, new LinearLayout.LayoutParams(
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

    private void createAppInfoSection(LinearLayout parentContainer) {
        TextView appInfoTitle = new TextView(context);
        appInfoTitle.setText("Uygulama Hakkında");
        appInfoTitle.setTextSize(18);
        appInfoTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        appInfoTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        appInfoTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(appInfoTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView versionTitle = new TextView(context);
        versionTitle.setText("Versiyon");
        versionTitle.setTextSize(16);
        versionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
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
        versionText.setTextColor(UiStyles.color(context, R.color.textHint));
        versionText.setPadding(16, 0, 16, 16);
        parentContainer.addView(versionText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView latestVersionText = new TextView(context);
        latestVersionText.setId(View.generateViewId());
        latestVersionText.setText("Güncel Versiyon: Yükleniyor...");
        latestVersionText.setTextSize(14);
        latestVersionText.setTextColor(UiStyles.color(context, R.color.textHint));
        latestVersionText.setPadding(16, 0, 16, 16);
        parentContainer.addView(latestVersionText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView freeInstallTitle = new TextView(context);
        freeInstallTitle.setText("Kurulum");
        freeInstallTitle.setTextSize(16);
        freeInstallTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        freeInstallTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        freeInstallTitle.setPadding(16, 16, 16, 8);
        parentContainer.addView(freeInstallTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView freeInstallText = new TextView(context);
        freeInstallText.setText("Bu uygulama https://vnoisy.dev adresinden ücretsiz olarak kurulabilir.");
        freeInstallText.setTextSize(14);
        freeInstallText.setTextColor(UiStyles.color(context, R.color.textHint));
        freeInstallText.setPadding(16, 0, 16, 16);
        parentContainer.addView(freeInstallText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView changelogTitle = new TextView(context);
        changelogTitle.setText("Güncelleme Notları");
        changelogTitle.setTextSize(16);
        changelogTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        changelogTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        changelogTitle.setPadding(16, 16, 16, 8);
        parentContainer.addView(changelogTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView changelogText = new TextView(context);
        changelogText.setId(View.generateViewId());
        changelogText.setText("Yükleniyor...");
        changelogText.setTextSize(14);
        changelogText.setTextColor(UiStyles.color(context, R.color.textHint));
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

    /**
     * Ara\u00e7 Launcher Modu: tam ekran ara\u00e7 paneli deneyimi ({@link LauncherModeManager}).
     * A\u00e7\u0131kken HOME activity-alias da etkinle\u015fir; bu iste\u011fe ba\u011fl\u0131 ikincil bir ad\u0131md\u0131r.
     */
    private void createLauncherModeSection(LinearLayout parentContainer) {
        TextView sectionTitle = new TextView(context);
        sectionTitle.setText("Ara\u00e7 Launcher Modu");
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText("Etkinle\u015ftirildi\u011finde MapControl tam ekran ara\u00e7 paneli olarak a\u00e7\u0131l\u0131r "
                + "(sol men\u00fc gizlenir, durum \u00e7ubu\u011fu saklan\u0131r, ara\u00e7 g\u00f6stergeleri ve uygulama k\u0131sayollar\u0131 g\u00f6sterilir). "
                + "Kapat\u0131ld\u0131\u011f\u0131nda uygulama normal sekme g\u00f6r\u00fcn\u00fcm\u00fcnde \u00e7al\u0131\u015f\u0131r.");
        sectionDesc.setTextSize(13);
        sectionDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        sectionDesc.setLineSpacing(3, 1.05f);
        sectionDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(sectionDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean launcherModeOn = LauncherModeManager.isEnabled(context);
        launcherModeSegmentHandle = UiStyles.addBinarySegmentedControl(context, parentContainer,
                null,
                "Evet", "Hay\u0131r",
                "Tam ekran ara\u00e7 paneli a\u00e7\u0131k.",
                "Normal sekme g\u00f6r\u00fcn\u00fcm\u00fc.",
                launcherModeOn,
                enabled -> {
                    callback.log("Ara\u00e7 Launcher Modu: " + (enabled ? "Evet" : "Hay\u0131r"));
                    callback.onLauncherModeChanged(enabled);
                    updateLauncherHomeSettingsLinkVisibility(enabled);
                });

        launcherHomeSettingsLink = new TextView(context);
        launcherHomeSettingsLink.setText("Varsay\u0131lan ana ekran uygulamas\u0131n\u0131 sistem ayarlar\u0131ndan de\u011fi\u015ftir");
        launcherHomeSettingsLink.setTextSize(13);
        launcherHomeSettingsLink.setTextColor(UiStyles.color(context, R.color.accentHighlight));
        launcherHomeSettingsLink.setPadding(16, 4, 16, 16);
        launcherHomeSettingsLink.setOnClickListener(v -> {
            LauncherModeManager.logHomeResolutionState(context, callback::log);
            LauncherModeManager.openHomeChooser(context);
        });
        parentContainer.addView(launcherHomeSettingsLink, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        updateLauncherHomeSettingsLinkVisibility(launcherModeOn);
    }

    private void createVehicleModelSection(LinearLayout parentContainer) {
        TextView sectionTitle = new TextView(context);
        sectionTitle.setText("Araç görseli");
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText("Launcher panelindeki araç görseline dokunarak listeden model seçin. "
                + "Listede otomatik algılama veya yüklü OEM paketleri bulunur.");
        sectionDesc.setTextSize(13);
        sectionDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        sectionDesc.setLineSpacing(3, 1.05f);
        sectionDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(sectionDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView currentLabel = new TextView(context);
        currentLabel.setTextSize(14);
        currentLabel.setTextColor(UiStyles.color(context, R.color.textSecondary));
        currentLabel.setPadding(16, 0, 16, 8);
        refreshVehicleModelStatusLabel(currentLabel);
        parentContainer.addView(currentLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button pickButton = new Button(context);
        pickButton.setText("Araç görseli seç");
        UiStyles.styleOemButton(pickButton, UiStyles.color(context, R.color.accentHighlight));
        LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pickLp.setMargins(16, 0, 16, 16);
        pickButton.setOnClickListener(v -> {
            if (context instanceof android.app.Activity) {
                VehicleMaterialPickerDialog.show((android.app.Activity) context,
                        new VehicleMaterialPickerDialog.OnSelectedListener() {
                            @Override
                            public void onManualSelected(
                                    com.mapcontrol.vehicle.material.MaterialVehicleCatalog.Entry entry) {
                                MaterialVehicleResources.getInstance().init(context);
                                refreshVehicleModelStatusLabel(currentLabel);
                                callback.log("Araç görseli seçildi: " + entry.label
                                        + " (" + entry.packageName + ")");
                            }

                            @Override
                            public void onAutoDetectionSelected() {
                                refreshVehicleModelStatusLabel(currentLabel);
                                callback.log("Araç görseli: otomatik algılama etkin");
                            }
                        });
            }
        });
        parentContainer.addView(pickButton, pickLp);
    }

    private void refreshVehicleModelStatusLabel(TextView label) {
        MaterialVehicleResources resources = MaterialVehicleResources.getInstance();
        resources.init(context);
        MaterialVehiclePreferences.Selection manual = MaterialVehiclePreferences.getSelection(context);
        if (manual != null) {
            label.setText("Seçili: " + manual.label + " (" + manual.packageName + ")");
            return;
        }
        if (MaterialVehiclePreferences.isAutoDetectionEnabled(context)) {
            String pkg = resources.getPackageName();
            String source = resources.getSourceLabel();
            if (pkg != null) {
                label.setText("Otomatik algılama: " + source + " — " + pkg);
            } else {
                label.setText("Otomatik algılama etkin; paket bulunamadı — listeden elle seçin.");
            }
            return;
        }
        label.setText("Seçim yok — launcher'daki araca dokunarak seçin.");
    }

    private void updateLauncherHomeSettingsLinkVisibility(boolean enabled) {
        if (launcherHomeSettingsLink != null) {
            launcherHomeSettingsLink.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }

    /** Tercih de\u011fi\u015fince (Ayarlar d\u0131\u015f\u0131ndan da) segment g\u00f6r\u00fcn\u00fcm\u00fcn\u00fc senkronlar. */
    public void syncLauncherModeFromPrefs() {
        boolean enabled = LauncherModeManager.isEnabled(context);
        if (launcherModeSegmentHandle != null) {
            launcherModeSegmentHandle.syncVisualWithoutCommit(enabled);
        }
        updateLauncherHomeSettingsLinkVisibility(enabled);
    }

    public LinearLayout getSettingsTabContent() {
        return settingsTabContent;
    }

    public ScrollView getScrollView() {
        return scrollView != null ? scrollView : build();
    }
}
