package com.mapcontrol.ui.builder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import com.mapcontrol.manager.FloatingBackButtonManager;
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

    public SettingsTabBuilder(Context context, SharedPreferences prefs, SettingsCallback callback) {
        this.context = context;
        this.prefs = prefs;
        this.callback = callback;
        this.handler = new Handler(Looper.getMainLooper());
        build();
    }

    public ScrollView build() {
        scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(0xFF0A0F14);
        scrollView.setPadding(0, 0, 0, 0);
        scrollView.setFillViewport(true);

        settingsTabContent = new LinearLayout(context);
        settingsTabContent.setOrientation(LinearLayout.VERTICAL);
        settingsTabContent.setPadding(0, 0, 0, 0);
        settingsTabContent.setBackgroundColor(0xFF0A0F14);
        scrollView.addView(settingsTabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        createAppInfoSection(settingsTabContent);
        createBootAutostartSection(settingsTabContent);
        createFloatingBackButtonSection(settingsTabContent);
        return scrollView;
    }

    private void createBootAutostartSection(LinearLayout parentContainer) {
        TextView sectionTitle = new TextView(context);
        sectionTitle.setText("Sistem açılışı");
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(0xFFFFFFFF);
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText("Cihaz yeniden başladığında servis ve uygulama ekranının otomatik açılması.");
        sectionDesc.setTextSize(13);
        sectionDesc.setTextColor(0xAAFFFFFF);
        sectionDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(sectionDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView serviceTitle = new TextView(context);
        serviceTitle.setText("Açılışta arka plan servisi");
        serviceTitle.setTextSize(16);
        serviceTitle.setTextColor(0xFFFFFFFF);
        serviceTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        serviceTitle.setPadding(16, 8, 16, 8);
        parentContainer.addView(serviceTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        RadioGroup serviceGroup = new RadioGroup(context);
        serviceGroup.setOrientation(LinearLayout.VERTICAL);
        serviceGroup.setPadding(20, 0, 20, 0);
        addBootRadioRow(serviceGroup, 500, 501, "Açık", "BOOT sonrası MapControl servisini başlat",
                "Kapalı", "Servis yalnızca uygulamayı elle açınca başlar");
        parentContainer.addView(serviceGroup, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean serviceOn = prefs.getBoolean(BootReceiver.KEY_BOOT_AUTO_START, true);
        serviceGroup.check(serviceOn ? 500 : 501);
        serviceGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean on = (checkedId == 500);
            prefs.edit().putBoolean(BootReceiver.KEY_BOOT_AUTO_START, on).apply();
            callback.log("Açılışta servis: " + (on ? "Açık" : "Kapalı"));
        });

        TextView uiTitle = new TextView(context);
        uiTitle.setText("Açılışta uygulama ekranı");
        uiTitle.setTextSize(16);
        uiTitle.setTextColor(0xFFFFFFFF);
        uiTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        uiTitle.setPadding(16, 16, 16, 8);
        parentContainer.addView(uiTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        RadioGroup uiGroup = new RadioGroup(context);
        uiGroup.setOrientation(LinearLayout.VERTICAL);
        uiGroup.setPadding(20, 0, 20, 8);
        addBootRadioRow(uiGroup, 502, 503, "Açık", "Yaklaşık 4 sn sonra ana ekranı aç (cihaza bağlı)",
                "Kapalı", "Yalnızca bildirimden veya launcher'dan açın");
        parentContainer.addView(uiGroup, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean uiOn = prefs.getBoolean(BootReceiver.KEY_BOOT_AUTO_LAUNCH_UI, true);
        uiGroup.check(uiOn ? 502 : 503);
        uiGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean on = (checkedId == 502);
            prefs.edit().putBoolean(BootReceiver.KEY_BOOT_AUTO_LAUNCH_UI, on).apply();
            callback.log("Açılışta ekran: " + (on ? "Açık" : "Kapalı"));
        });
    }

    private void addBootRadioRow(RadioGroup group, int idOn, int idOff,
            String onTitle, String onDesc, String offTitle, String offDesc) {
        LinearLayout optionOn = new LinearLayout(context);
        optionOn.setOrientation(LinearLayout.HORIZONTAL);
        optionOn.setPadding(16, 16, 16, 16);
        optionOn.setGravity(Gravity.CENTER_VERTICAL);
        optionOn.setClickable(true);
        optionOn.setFocusable(true);
        optionOn.addView(createIconCircle("✅"), wrapIconParams());
        optionOn.addView(createOptionText(onTitle, onDesc), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        RadioButton radioOn = new RadioButton(context);
        radioOn.setId(idOn);
        radioOn.setClickable(false);
        radioOn.setFocusable(false);
        optionOn.addView(radioOn);
        optionOn.setOnClickListener(v -> group.check(idOn));
        group.addView(optionOn);

        View divider = new View(context);
        divider.setBackgroundColor(0x1FFFFFFF);
        group.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        LinearLayout optionOff = new LinearLayout(context);
        optionOff.setOrientation(LinearLayout.HORIZONTAL);
        optionOff.setPadding(16, 16, 16, 16);
        optionOff.setGravity(Gravity.CENTER_VERTICAL);
        optionOff.setClickable(true);
        optionOff.setFocusable(true);
        optionOff.addView(createIconCircle("❌"), wrapIconParams());
        optionOff.addView(createOptionText(offTitle, offDesc), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        RadioButton radioOff = new RadioButton(context);
        radioOff.setId(idOff);
        radioOff.setClickable(false);
        radioOff.setFocusable(false);
        optionOff.addView(radioOff);
        optionOff.setOnClickListener(v -> group.check(idOff));
        group.addView(optionOff);
    }

    private LinearLayout.LayoutParams wrapIconParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(48, 48);
        p.setMargins(0, 0, 12, 0);
        return p;
    }

    private void createFloatingBackButtonSection(LinearLayout parentContainer) {
        TextView floatingBackButtonTitle = new TextView(context);
        floatingBackButtonTitle.setText("Floating Back Button");
        floatingBackButtonTitle.setTextSize(18);
        floatingBackButtonTitle.setTextColor(0xFFFFFFFF);
        floatingBackButtonTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        floatingBackButtonTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(floatingBackButtonTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView floatingBackButtonDesc = new TextView(context);
        floatingBackButtonDesc.setText("Ekranda yüzen bir geri tuşu göster. Sağa sola kaydırabilirsiniz.");
        floatingBackButtonDesc.setTextSize(13);
        floatingBackButtonDesc.setTextColor(0xAAFFFFFF);
        floatingBackButtonDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(floatingBackButtonDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        RadioGroup floatingBackButtonRadioGroup = new RadioGroup(context);
        floatingBackButtonRadioGroup.setOrientation(LinearLayout.VERTICAL);
        floatingBackButtonRadioGroup.setPadding(20, 0, 20, 0);

        LinearLayout option1Container = new LinearLayout(context);
        option1Container.setOrientation(LinearLayout.HORIZONTAL);
        option1Container.setPadding(16, 16, 16, 16);
        option1Container.setGravity(Gravity.CENTER_VERTICAL);
        option1Container.setClickable(true);
        option1Container.setFocusable(true);

        LinearLayout iconCircle1 = createIconCircle("✅");
        LinearLayout.LayoutParams iconCircle1Params = new LinearLayout.LayoutParams(48, 48);
        iconCircle1Params.setMargins(0, 0, 12, 0);
        option1Container.addView(iconCircle1, iconCircle1Params);

        LinearLayout textColumn1 = createOptionText("Açık", "Yüzen geri tuşunu göster");
        LinearLayout.LayoutParams textParams1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        option1Container.addView(textColumn1, textParams1);

        RadioButton radio1 = new RadioButton(context);
        radio1.setId(400);
        radio1.setClickable(false);
        radio1.setFocusable(false);
        option1Container.addView(radio1);
        option1Container.setOnClickListener(v -> floatingBackButtonRadioGroup.check(400));
        floatingBackButtonRadioGroup.addView(option1Container);

        View divider = new View(context);
        divider.setBackgroundColor(0x1FFFFFFF);
        floatingBackButtonRadioGroup.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        LinearLayout option2Container = new LinearLayout(context);
        option2Container.setOrientation(LinearLayout.HORIZONTAL);
        option2Container.setPadding(16, 16, 16, 16);
        option2Container.setGravity(Gravity.CENTER_VERTICAL);
        option2Container.setClickable(true);
        option2Container.setFocusable(true);

        LinearLayout iconCircle2 = createIconCircle("❌");
        LinearLayout.LayoutParams iconCircle2Params = new LinearLayout.LayoutParams(48, 48);
        iconCircle2Params.setMargins(0, 0, 12, 0);
        option2Container.addView(iconCircle2, iconCircle2Params);

        LinearLayout textColumn2 = createOptionText("Kapalı", "Yüzen geri tuşunu gizle");
        LinearLayout.LayoutParams textParams2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        option2Container.addView(textColumn2, textParams2);

        RadioButton radio2 = new RadioButton(context);
        radio2.setId(401);
        radio2.setClickable(false);
        radio2.setFocusable(false);
        option2Container.addView(radio2);
        option2Container.setOnClickListener(v -> floatingBackButtonRadioGroup.check(401));
        floatingBackButtonRadioGroup.addView(option2Container);

        parentContainer.addView(floatingBackButtonRadioGroup, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        floatingBackButtonManager = FloatingBackButtonManager.getInstance(context);
        floatingBackButtonManager.setLogCallback(callback::log);

        final boolean savedEnabled = FloatingBackButtonManager.loadEnabledState(context);

        final LinearLayout[] iconCircles = {iconCircle1, iconCircle2};
        floatingBackButtonRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isEnabled = (checkedId == 400);
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
                            floatingBackButtonRadioGroup.check(401);
                            return;
                        } catch (Exception e) {
                            callback.log("İzin ayarlarına gidilemedi: " + e.getMessage());
                            floatingBackButtonRadioGroup.check(401);
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

            for (int i = 0; i < iconCircles.length; i++) {
                LinearLayout iconCircle = iconCircles[i];
                if (iconCircle != null) {
                    if ((isEnabled && i == 0) || (!isEnabled && i == 1)) {
                        iconCircle.setBackgroundColor(0xFF3DAEA8);
                    } else {
                        iconCircle.setBackgroundColor(0xFF1A2330);
                    }
                }
            }
        });
        // 500ms postDelayed yok: önceki Activity'den kalan gecikmeli iş aynı Looper kuyruğunda ikinci show() üretebiliyordu.
        // Listener + check: tek başlatma yolu. post() = RadioGroup hiyerarşide iken check güvenilir.
        floatingBackButtonRadioGroup.post(() -> {
            try {
                floatingBackButtonRadioGroup.check(savedEnabled ? 400 : 401);
            } catch (Exception e) {
                callback.log("Floating Back Button başlatma hatası: " + e.getMessage());
            }
        });
    }

    private LinearLayout createIconCircle(String iconText) {
        LinearLayout iconCircle = new LinearLayout(context);
        iconCircle.setOrientation(LinearLayout.VERTICAL);
        iconCircle.setBackgroundColor(0xFF1A2330);
        iconCircle.setGravity(Gravity.CENTER);
        iconCircle.setPadding(10, 10, 10, 10);
        TextView icon = new TextView(context);
        icon.setText(iconText);
        icon.setTextSize(20);
        icon.setTextColor(0xFFFFFFFF);
        iconCircle.addView(icon);
        return iconCircle;
    }

    private LinearLayout createOptionText(String title, String desc) {
        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTextSize(16);
        textColumn.addView(titleView);
        TextView descView = new TextView(context);
        descView.setText(desc);
        descView.setTextColor(0xAAFFFFFF);
        descView.setTextSize(13);
        descView.setPadding(0, 2, 0, 0);
        textColumn.addView(descView);
        return textColumn;
    }

    private void createAppInfoSection(LinearLayout parentContainer) {
        TextView appInfoTitle = new TextView(context);
        appInfoTitle.setText("Uygulama Hakkında");
        appInfoTitle.setTextSize(18);
        appInfoTitle.setTextColor(0xFFFFFFFF);
        appInfoTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        appInfoTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(appInfoTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView versionTitle = new TextView(context);
        versionTitle.setText("Versiyon");
        versionTitle.setTextSize(16);
        versionTitle.setTextColor(0xFFFFFFFF);
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
        versionText.setTextColor(0xAAFFFFFF);
        versionText.setPadding(16, 0, 16, 16);
        parentContainer.addView(versionText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView latestVersionText = new TextView(context);
        latestVersionText.setId(View.generateViewId());
        latestVersionText.setText("Güncel Versiyon: Yükleniyor...");
        latestVersionText.setTextSize(14);
        latestVersionText.setTextColor(0xAAFFFFFF);
        latestVersionText.setPadding(16, 0, 16, 16);
        parentContainer.addView(latestVersionText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView freeInstallTitle = new TextView(context);
        freeInstallTitle.setText("Kurulum");
        freeInstallTitle.setTextSize(16);
        freeInstallTitle.setTextColor(0xFFFFFFFF);
        freeInstallTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        freeInstallTitle.setPadding(16, 16, 16, 8);
        parentContainer.addView(freeInstallTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView freeInstallText = new TextView(context);
        freeInstallText.setText("Bu uygulama https://vnoisy.dev adresinden ücretsiz olarak kurulabilir.");
        freeInstallText.setTextSize(14);
        freeInstallText.setTextColor(0xAAFFFFFF);
        freeInstallText.setPadding(16, 0, 16, 16);
        parentContainer.addView(freeInstallText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView changelogTitle = new TextView(context);
        changelogTitle.setText("Güncelleme Notları");
        changelogTitle.setTextSize(16);
        changelogTitle.setTextColor(0xFFFFFFFF);
        changelogTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        changelogTitle.setPadding(16, 16, 16, 8);
        parentContainer.addView(changelogTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView changelogText = new TextView(context);
        changelogText.setId(View.generateViewId());
        changelogText.setText("Yükleniyor...");
        changelogText.setTextSize(14);
        changelogText.setTextColor(0xAAFFFFFF);
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

    public ScrollView getScrollView() {
        return scrollView != null ? scrollView : build();
    }
}
