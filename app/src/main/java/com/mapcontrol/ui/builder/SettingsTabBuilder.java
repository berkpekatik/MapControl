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
import com.mapcontrol.util.AppLocaleManager;
import com.mapcontrol.util.LauncherModeManager;
import com.mapcontrol.vehicle.material.MaterialVehiclePreferences;
import com.mapcontrol.vehicle.material.MaterialVehicleResources;
import com.mapcontrol.vehicle.material.VehicleMaterialPickerDialog;

public class SettingsTabBuilder {
    public interface SettingsCallback {
        void log(String message);
        String getCarToken();
        void onLauncherModeChanged(boolean enabled);

        void onLocaleChanged();
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final SettingsCallback callback;
    private final Handler handler;
    private ScrollView scrollView;
    private LinearLayout settingsTabContent;
    private FloatingBackButtonManager floatingBackButtonManager;
    private UiStyles.BinarySegmentHandle launcherModeSegmentHandle;
    private UiStyles.BinarySegmentHandle languageSegmentHandle;
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

        createLanguageSection(settingsTabContent);
        createAppInfoSection(settingsTabContent);
        createBootAutostartSection(settingsTabContent);
        createFloatingBackButtonSection(settingsTabContent);
        createYandexClusterNavSection(settingsTabContent);
        createLauncherModeSection(settingsTabContent);
        createVehicleModelSection(settingsTabContent);
        return scrollView;
    }

    private void createLanguageSection(LinearLayout parentContainer) {
        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(R.string.settings_language_section_title);
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText(R.string.settings_language_section_desc);
        sectionDesc.setTextSize(13);
        sectionDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        sectionDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(sectionDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean english = AppLocaleManager.isEnglish(context);
        languageSegmentHandle = UiStyles.addBinarySegmentedControl(context, parentContainer,
                null,
                context.getString(R.string.settings_language_tr),
                context.getString(R.string.settings_language_en),
                context.getString(R.string.settings_language_tr),
                context.getString(R.string.settings_language_en),
                !english,
                turkishSelected -> {
                    String tag = turkishSelected
                            ? AppLocaleManager.LOCALE_TR
                            : AppLocaleManager.LOCALE_EN;
                    if (tag.equals(AppLocaleManager.getStoredLocaleTag(context))) {
                        return;
                    }
                    AppLocaleManager.setLocale(context, tag);
                    callback.log("Locale: " + tag);
                    callback.onLocaleChanged();
                });
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
                context.getString(R.string.common_on), context.getString(R.string.common_off),
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
                                    R.string.overlay_permission_toast,
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
        sectionTitle.setText(R.string.settings_boot_section_title);
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText(R.string.settings_boot_section_desc);
        sectionDesc.setTextSize(13);
        sectionDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        sectionDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(sectionDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean serviceOn = prefs.getBoolean(BootReceiver.KEY_BOOT_AUTO_START, true);
        UiStyles.addBinarySegmentedControl(context, parentContainer,
                context.getString(R.string.settings_boot_service_title),
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(R.string.settings_boot_service_help_on),
                context.getString(R.string.settings_boot_service_help_off),
                serviceOn,
                on -> {
                    prefs.edit().putBoolean(BootReceiver.KEY_BOOT_AUTO_START, on).apply();
                    callback.log("Açılışta servis: " + (on ? "Açık" : "Kapalı"));
                });

        boolean uiOn = prefs.getBoolean(BootReceiver.KEY_BOOT_AUTO_LAUNCH_UI, true);
        UiStyles.addBinarySegmentedControl(context, parentContainer,
                context.getString(R.string.settings_boot_ui_title),
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(R.string.settings_boot_ui_help_on),
                context.getString(R.string.settings_boot_ui_help_off),
                uiOn,
                on -> {
                    prefs.edit().putBoolean(BootReceiver.KEY_BOOT_AUTO_LAUNCH_UI, on).apply();
                    callback.log("Açılışta ekran: " + (on ? "Açık" : "Kapalı"));
                });
    }

    private void createFloatingBackButtonSection(LinearLayout parentContainer) {
        TextView floatingBackButtonTitle = new TextView(context);
        floatingBackButtonTitle.setText(R.string.settings_floating_back_title);
        floatingBackButtonTitle.setTextSize(18);
        floatingBackButtonTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        floatingBackButtonTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        floatingBackButtonTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(floatingBackButtonTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView floatingBackButtonDesc = new TextView(context);
        floatingBackButtonDesc.setText(R.string.settings_floating_back_desc);
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
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(R.string.settings_floating_back_help_on),
                context.getString(R.string.settings_floating_back_help_off),
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
                                    Toast.makeText(context, R.string.overlay_permission_toast, Toast.LENGTH_LONG).show();
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
                    Toast.makeText(context, R.string.overlay_permission_toast, Toast.LENGTH_LONG).show();
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
        appInfoTitle.setText(R.string.settings_app_about_title);
        appInfoTitle.setTextSize(18);
        appInfoTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        appInfoTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        appInfoTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(appInfoTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView versionTitle = new TextView(context);
        versionTitle.setText(R.string.settings_version_title);
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
            versionText.setText(context.getString(R.string.settings_version_current, versionName));
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText(R.string.settings_version_current_unknown);
        }
        versionText.setTextSize(14);
        versionText.setTextColor(UiStyles.color(context, R.color.textHint));
        versionText.setPadding(16, 0, 16, 16);
        parentContainer.addView(versionText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView latestVersionText = new TextView(context);
        latestVersionText.setId(View.generateViewId());
        latestVersionText.setText(R.string.settings_version_latest_loading);
        latestVersionText.setTextSize(14);
        latestVersionText.setTextColor(UiStyles.color(context, R.color.textHint));
        latestVersionText.setPadding(16, 0, 16, 16);
        parentContainer.addView(latestVersionText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView freeInstallTitle = new TextView(context);
        freeInstallTitle.setText(R.string.settings_install_title);
        freeInstallTitle.setTextSize(16);
        freeInstallTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        freeInstallTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        freeInstallTitle.setPadding(16, 16, 16, 8);
        parentContainer.addView(freeInstallTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView freeInstallText = new TextView(context);
        freeInstallText.setText(R.string.settings_install_body);
        freeInstallText.setTextSize(14);
        freeInstallText.setTextColor(UiStyles.color(context, R.color.textHint));
        freeInstallText.setPadding(16, 0, 16, 16);
        parentContainer.addView(freeInstallText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView changelogTitle = new TextView(context);
        changelogTitle.setText(R.string.settings_changelog_title);
        changelogTitle.setTextSize(16);
        changelogTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        changelogTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        changelogTitle.setPadding(16, 16, 16, 8);
        parentContainer.addView(changelogTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView changelogText = new TextView(context);
        changelogText.setId(View.generateViewId());
        changelogText.setText(R.string.common_loading);
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
                                    changelogBuilder.append(context.getString(R.string.settings_changelog_version_prefix)).append(version);
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
                                ? changelogBuilder.toString() : context.getString(R.string.settings_changelog_not_found);

                        handler.post(() -> {
                            latestVersionView.setText(context.getString(R.string.settings_version_latest, finalLatestVersion));
                            changelogView.setText(finalChangelog);
                        });
                    } else {
                        JSONObject json = new JSONObject(responseStr);
                        String latestVersion = json.optString("version", currentVersion);
                        String changelog = json.optString("changelog", json.optString("message",
                                context.getString(R.string.settings_changelog_not_found)));

                        handler.post(() -> {
                            latestVersionView.setText(context.getString(R.string.settings_version_latest, latestVersion));
                            CharSequence parsed = parseMarkdown(changelog);
                            changelogView.setText(parsed != null ? parsed : changelog);
                        });
                    }
                } else {
                    handler.post(() -> {
                        latestVersionView.setText(R.string.settings_version_latest_failed);
                        changelogView.setText(context.getString(R.string.settings_changelog_load_failed, responseCode));
                    });
                }
                connection.disconnect();
            } catch (Exception e) {
                handler.post(() -> {
                    latestVersionView.setText(R.string.settings_version_latest_error);
                    changelogView.setText(context.getString(R.string.settings_changelog_load_error, e.getMessage()));
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
        return com.mapcontrol.util.MarkdownUtil.parse(markdownText);
    }

    /**
     * Ara\u00e7 Launcher Modu: tam ekran ara\u00e7 paneli deneyimi ({@link LauncherModeManager}).
     * A\u00e7\u0131kken HOME activity-alias da etkinle\u015fir; bu iste\u011fe ba\u011fl\u0131 ikincil bir ad\u0131md\u0131r.
     */
    private void createLauncherModeSection(LinearLayout parentContainer) {
        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(R.string.settings_launcher_mode_title);
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText(R.string.settings_launcher_mode_desc);
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
                context.getString(R.string.common_yes), context.getString(R.string.common_no),
                context.getString(R.string.settings_launcher_mode_help_on),
                context.getString(R.string.settings_launcher_mode_help_off),
                launcherModeOn,
                enabled -> {
                    callback.log("Ara\u00e7 Launcher Modu: " + (enabled ? "Evet" : "Hay\u0131r"));
                    callback.onLauncherModeChanged(enabled);
                    updateLauncherHomeSettingsLinkVisibility(enabled);
                });

        launcherHomeSettingsLink = new TextView(context);
        launcherHomeSettingsLink.setText(R.string.settings_launcher_home_link);
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
        sectionTitle.setText(R.string.settings_vehicle_image_title);
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText(R.string.settings_vehicle_image_desc);
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
        pickButton.setText(R.string.settings_vehicle_image_pick);
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
            label.setText(context.getString(R.string.settings_vehicle_selected, manual.label, manual.packageName));
            return;
        }
        if (MaterialVehiclePreferences.isAutoDetectionEnabled(context)) {
            String pkg = resources.getPackageName();
            String source = resources.getSourceLabel();
            if (pkg != null) {
                label.setText(context.getString(R.string.settings_vehicle_auto_detect, source, pkg));
            } else {
                label.setText(R.string.settings_vehicle_auto_no_pkg);
            }
            return;
        }
        label.setText(R.string.settings_vehicle_no_selection);
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
