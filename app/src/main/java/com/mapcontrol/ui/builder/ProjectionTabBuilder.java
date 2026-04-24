package com.mapcontrol.ui.builder;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.Display;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.mapcontrol.util.AppLaunchHelper;
import com.mapcontrol.util.DialogHelper;

public class ProjectionTabBuilder {
    public interface ProjectionCallback {
        void onOpenCluster();
        void onCloseCluster();
        void onSavePowerMode(int mode);
        void onStartKeyEventListener();
        void onStopKeyEventListener();
        String getTargetPackage();
        void onTargetPackageSelected(String packageName);
        boolean isSystemOrPrivApp(ApplicationInfo appInfo);
        void log(String message);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final ProjectionCallback callback;
    private final Handler handler;

    private ScrollView scrollView;
    private LinearLayout projectionTabContent;
    private TextView targetAppLabel;
    private boolean isNavigationOpenLocal = false;

    private RadioGroup powerModeRadioGroup;
    private RadioButton radioMode2;
    private RadioButton radioMode1;
    private RadioButton radioManual;

    public ProjectionTabBuilder(Context context, SharedPreferences prefs, ProjectionCallback callback) {
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

        projectionTabContent = new LinearLayout(context);
        projectionTabContent.setOrientation(LinearLayout.VERTICAL);
        projectionTabContent.setPadding(0, 0, 0, 0);
        projectionTabContent.setBackgroundColor(0xFF0A0F14);
        scrollView.addView(projectionTabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView projectionTitle = new TextView(context);
        projectionTitle.setText("Yansıtma Kontrolü");
        projectionTitle.setTextSize(18);
        projectionTitle.setTextColor(0xFFFFFFFF);
        projectionTitle.setTypeface(null, Typeface.BOLD);
        projectionTitle.setPadding(16, 16, 16, 8);
        projectionTabContent.addView(projectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView projectionStatus = new TextView(context);
        projectionStatus.setText("Yansıtma kapalı");
        projectionStatus.setTextSize(13);
        projectionStatus.setTextColor(0xAAFFFFFF);
        projectionStatus.setPadding(16, 0, 16, 16);
        projectionStatus.setId(View.generateViewId());
        projectionTabContent.addView(projectionStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout controlButtonContainer = new LinearLayout(context);
        controlButtonContainer.setOrientation(LinearLayout.HORIZONTAL);
        controlButtonContainer.setPadding(16, 0, 16, 16);

        Button btnOpen = new Button(context);
        btnOpen.setText("Yansıt");
        btnOpen.setTextColor(0xFFFFFFFF);
        btnOpen.setTextSize(16);
        btnOpen.setTypeface(null, Typeface.BOLD);
        btnOpen.setBackgroundColor(0xFF3DAEA8);
        btnOpen.setPadding(16, 20, 16, 20);
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        openParams.setMargins(0, 0, 8, 0);
        btnOpen.setId(View.generateViewId());
        controlButtonContainer.addView(btnOpen, openParams);

        Button btnClose = new Button(context);
        btnClose.setText("Durdur");
        btnClose.setTextColor(0xFFFFFFFF);
        btnClose.setTextSize(16);
        btnClose.setTypeface(null, Typeface.BOLD);
        btnClose.setBackgroundColor(0xFFF44336);
        btnClose.setPadding(16, 20, 16, 20);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnClose.setId(View.generateViewId());
        controlButtonContainer.addView(btnClose, closeParams);

        projectionTabContent.addView(controlButtonContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        btnOpen.setOnClickListener(v -> {
            callback.onOpenCluster();
            isNavigationOpenLocal = true;
            updateProjectionUI(projectionStatus);
            handleButtonClickWithDelay(btnOpen, "Yansıt", "Yansıtılıyor...");
        });

        btnClose.setOnClickListener(v -> {
            callback.onCloseCluster();
            isNavigationOpenLocal = false;
            updateProjectionUI(projectionStatus);
            handleButtonClickWithDelay(btnClose, "Durdur", "Durduruluyor...");
        });

        handler.post(() -> updateProjectionUI(projectionStatus));

        TextView appTitle = new TextView(context);
        appTitle.setText("Uygulama");
        appTitle.setTextSize(18);
        appTitle.setTextColor(0xFFFFFFFF);
        appTitle.setTypeface(null, Typeface.BOLD);
        appTitle.setPadding(16, 16, 16, 8);
        projectionTabContent.addView(appTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout appCard = new LinearLayout(context);
        appCard.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable appCardBg = new android.graphics.drawable.GradientDrawable();
        appCardBg.setColor(0xFF151C24);
        appCardBg.setCornerRadius(12);
        appCard.setBackground(appCardBg);
        appCard.setPadding(20, 20, 20, 20);
        LinearLayout.LayoutParams appCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        appCardParams.setMargins(16, 0, 16, 16);

        LinearLayout appInfo = new LinearLayout(context);
        appInfo.setOrientation(LinearLayout.HORIZONTAL);
        appInfo.setPadding(0, 0, 0, 20);

        LinearLayout iconBox = new LinearLayout(context);
        iconBox.setOrientation(LinearLayout.VERTICAL);
        iconBox.setBackgroundColor(0xFF1A2330);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setPadding(16, 16, 16, 16);

        TextView mapIcon = new TextView(context);
        mapIcon.setText("🗺");
        mapIcon.setTextSize(28);
        iconBox.addView(mapIcon);

        LinearLayout.LayoutParams iconBoxParams = new LinearLayout.LayoutParams(80, 80);
        iconBoxParams.setMargins(0, 0, 16, 0);
        appInfo.addView(iconBox, iconBoxParams);

        LinearLayout textInfo = new LinearLayout(context);
        textInfo.setOrientation(LinearLayout.VERTICAL);
        textInfo.setPadding(0, 0, 0, 0);

        targetAppLabel = new TextView(context);
        targetAppLabel.setText("(seçilmedi)");
        targetAppLabel.setTextColor(0xFFFFFFFF);
        targetAppLabel.setTextSize(17);
        targetAppLabel.setTypeface(null, Typeface.NORMAL);
        LinearLayout.LayoutParams targetLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        targetLabelParams.setMargins(0, 0, 0, 4);
        textInfo.addView(targetAppLabel, targetLabelParams);

        TextView appDesc = new TextView(context);
        appDesc.setText("Seçili uygulamayı araç ekranına yansıt");
        appDesc.setTextColor(0xAAFFFFFF);
        appDesc.setTextSize(13);
        textInfo.addView(appDesc);

        appInfo.addView(textInfo, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        appCard.addView(appInfo);

        LinearLayout appButtons = new LinearLayout(context);
        appButtons.setOrientation(LinearLayout.HORIZONTAL);

        Button btnSelectApp = new Button(context);
        btnSelectApp.setText("Değiştir");
        btnSelectApp.setTextColor(0xCCFFFFFF);
        btnSelectApp.setTextSize(14);
        btnSelectApp.setTypeface(null, Typeface.NORMAL);
        btnSelectApp.setBackgroundColor(0xFF1A2330);
        btnSelectApp.setPadding(16, 14, 16, 14);
        LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        selectParams.setMargins(0, 0, 6, 0);
        appButtons.addView(btnSelectApp, selectParams);
        btnSelectApp.setOnClickListener(v -> selectTargetApp());

        Button btnLaunchOnCluster = new Button(context);
        btnLaunchOnCluster.setText("▶ Ana Ekrana Al");
        btnLaunchOnCluster.setTextColor(0xFFFFFFFF);
        btnLaunchOnCluster.setTextSize(14);
        btnLaunchOnCluster.setTypeface(null, Typeface.BOLD);
        btnLaunchOnCluster.setBackgroundColor(0xFF3DAEA8);
        btnLaunchOnCluster.setPadding(16, 14, 16, 14);
        LinearLayout.LayoutParams launchParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        launchParams.setMargins(6, 0, 0, 0);
        appButtons.addView(btnLaunchOnCluster, launchParams);
        btnLaunchOnCluster.setOnClickListener(v -> {
            String pkg = callback.getTargetPackage();
            if (pkg == null || pkg.trim().isEmpty()) {
                Toast.makeText(context, "Önce bir uygulama seçin!", Toast.LENGTH_SHORT).show();
                callback.log("Uygulama seçilmedi");
                return;
            }
            String trimmed = pkg.trim();
            if (context.getPackageManager().getLaunchIntentForPackage(trimmed) == null) {
                Toast.makeText(context, "Uygulama bulunamadı: " + pkg, Toast.LENGTH_SHORT).show();
                callback.log("Launch intent bulunamadı: " + pkg);
                return;
            }
            try {
                AppLaunchHelper.launchAppOnDisplay(context, trimmed, Display.DEFAULT_DISPLAY, Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                callback.log("Uygulama başlatıldı (displayId=" + Display.DEFAULT_DISPLAY + "): " + pkg);
            } catch (Exception e) {
                callback.log("launchSelectedAppOnDisplay hatası: " + e.getMessage());
                Toast.makeText(context, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        appCard.addView(appButtons);
        projectionTabContent.addView(appCard, appCardParams);

        TextView mainGroupTitle = new TextView(context);
        mainGroupTitle.setText("Navigasyon Davranışı");
        mainGroupTitle.setTextSize(18);
        mainGroupTitle.setTextColor(0xFFFFFFFF);
        mainGroupTitle.setTypeface(null, Typeface.BOLD);
        mainGroupTitle.setPadding(16, 24, 16, 8);
        projectionTabContent.addView(mainGroupTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout mainCardContainer = new LinearLayout(context);
        mainCardContainer.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable mainCardBg = new android.graphics.drawable.GradientDrawable();
        mainCardBg.setColor(0xFF151C24);
        mainCardBg.setCornerRadius(12);
        mainCardContainer.setBackground(mainCardBg);
        LinearLayout.LayoutParams mainCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        mainCardParams.setMargins(16, 0, 16, 32);

        TextView section1Title = new TextView(context);
        section1Title.setText("Başlatma");
        section1Title.setTextSize(17);
        section1Title.setTextColor(0xFFFFFFFF);
        section1Title.setTypeface(null, Typeface.BOLD);
        section1Title.setPadding(20, 20, 20, 8);
        mainCardContainer.addView(section1Title);

        TextView section1Desc = new TextView(context);
        section1Desc.setText("Ne zaman başlasın?");
        section1Desc.setTextSize(13);
        section1Desc.setTextColor(0xAAFFFFFF);
        section1Desc.setPadding(20, 0, 20, 12);
        mainCardContainer.addView(section1Desc);

        powerModeRadioGroup = new RadioGroup(context);
        powerModeRadioGroup.setOrientation(LinearLayout.VERTICAL);
        powerModeRadioGroup.setPadding(20, 0, 20, 0);

        LinearLayout option1Container = createPowerModeOption("🚗", "Motor çalışınca", "Direkt start verildiğinde", 2);
        LinearLayout option2Container = createPowerModeOption("📡", "Araç hazır olduğunda", "Engine Start 1 kere basınca (Frensiz)", 1);
        LinearLayout option3Container = createPowerModeOption("✋", "Elle çalıştır", "Kendiniz istediğinize zaman başlatın", 0);

        radioMode2 = (RadioButton) option1Container.getChildAt(option1Container.getChildCount() - 1);
        radioMode1 = (RadioButton) option2Container.getChildAt(option2Container.getChildCount() - 1);
        radioManual = (RadioButton) option3Container.getChildAt(option3Container.getChildCount() - 1);

        powerModeRadioGroup.addView(option1Container);
        View divider1 = new View(context);
        divider1.setBackgroundColor(0x1FFFFFFF);
        powerModeRadioGroup.addView(divider1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        powerModeRadioGroup.addView(option2Container);
        View divider2 = new View(context);
        divider2.setBackgroundColor(0x1FFFFFFF);
        powerModeRadioGroup.addView(divider2, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        powerModeRadioGroup.addView(option3Container);
        mainCardContainer.addView(powerModeRadioGroup);

        View sectionDivider1 = new View(context);
        sectionDivider1.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams dividerParams1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams1.setMargins(20, 24, 20, 24);
        mainCardContainer.addView(sectionDivider1, dividerParams1);

        TextView section2Title = new TextView(context);
        section2Title.setText("Kapanış");
        section2Title.setTextSize(15);
        section2Title.setTextColor(0xE6FFFFFF);
        section2Title.setTypeface(null, Typeface.NORMAL);
        section2Title.setPadding(20, 0, 20, 8);
        mainCardContainer.addView(section2Title);

        TextView section2Desc = new TextView(context);
        section2Desc.setText("Araç kapanınca ne olsun?");
        section2Desc.setTextSize(13);
        section2Desc.setTextColor(0xAAFFFFFF);
        section2Desc.setPadding(20, 0, 20, 12);
        mainCardContainer.addView(section2Desc);

        final LinearLayout[] iconCircles = {
                (LinearLayout) option1Container.getChildAt(0),
                (LinearLayout) option2Container.getChildAt(0),
                (LinearLayout) option3Container.getChildAt(0)
        };

        int savedMode = prefs.getInt("powerModeSetting", 2);
        powerModeRadioGroup.check(savedMode);
        powerModeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            callback.onSavePowerMode(checkedId);
            String modeName = checkedId == 2 ? "Motor Çalışınca" :
                    (checkedId == 1 ? "Araç Hazır Durumdayken" : "Elle Çalıştır");
            callback.log("Navigasyon açma modu: " + modeName);
            for (int i = 0; i < iconCircles.length; i++) {
                LinearLayout iconCircle = iconCircles[i];
                if (iconCircle != null) {
                    if ((checkedId == 2 && i == 0) || (checkedId == 1 && i == 1) || (checkedId == 0 && i == 2)) {
                        iconCircle.setBackgroundColor(0xFF3DAEA8);
                    } else {
                        iconCircle.setBackgroundColor(0xFF1A2330);
                    }
                }
            }
        });

        handler.post(() -> powerModeRadioGroup.check(prefs.getInt("powerModeSetting", 2)));

        RadioGroup autoCloseRadioGroup = new RadioGroup(context);
        autoCloseRadioGroup.setOrientation(LinearLayout.VERTICAL);
        autoCloseRadioGroup.setPadding(20, 0, 20, 0);
        LinearLayout autoCloseOption1 = createSimpleToggleOption(autoCloseRadioGroup, "✅", "Evet", "Araç kapanınca otomatik kapat", 200);
        View autoDivider = new View(context);
        autoDivider.setBackgroundColor(0x1FFFFFFF);
        autoCloseRadioGroup.addView(autoDivider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        LinearLayout autoCloseOption2 = createSimpleToggleOption(autoCloseRadioGroup, "❌", "Hayır", "Otomatik kapatma yapılmayacak", 201);
        mainCardContainer.addView(autoCloseRadioGroup);

        View sectionDivider2 = new View(context);
        sectionDivider2.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams dividerParams2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams2.setMargins(20, 24, 20, 24);
        mainCardContainer.addView(sectionDivider2, dividerParams2);

        RadioGroup mapControlRadioGroup = new RadioGroup(context);
        mapControlRadioGroup.setOrientation(LinearLayout.VERTICAL);
        mapControlRadioGroup.setPadding(20, 0, 20, 0);
        LinearLayout mapControlOption1 = createSimpleToggleOption(mapControlRadioGroup, "✅", "Açık", "Harita kontrol tuşu aktif", 100);
        View mapDivider = new View(context);
        mapDivider.setBackgroundColor(0x1FFFFFFF);
        mapControlRadioGroup.addView(mapDivider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        LinearLayout mapControlOption2 = createSimpleToggleOption(mapControlRadioGroup, "❌", "Kapalı", "Harita kontrol tuşu devre dışı", 101);
        mainCardContainer.addView(mapControlRadioGroup);

        projectionTabContent.addView(mainCardContainer, mainCardParams);

        autoCloseRadioGroup.check(prefs.getBoolean("autoCloseOnPowerOff", true) ? 200 : 201);
        mapControlRadioGroup.check(prefs.getBoolean("mapControlKeyEnabled", true) ? 100 : 101);

        final LinearLayout[] autoIcons = {(LinearLayout) autoCloseOption1.getChildAt(0), (LinearLayout) autoCloseOption2.getChildAt(0)};
        autoCloseRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isEnabled = (checkedId == 200);
            prefs.edit().putBoolean("autoCloseOnPowerOff", isEnabled).apply();
            callback.log(isEnabled ? "Araç kapanınca otomatik kapatma açıldı" : "Araç kapanınca otomatik kapatma kapatıldı");
            autoIcons[0].setBackgroundColor(isEnabled ? 0xFF3DAEA8 : 0xFF1A2330);
            autoIcons[1].setBackgroundColor(!isEnabled ? 0xFF3DAEA8 : 0xFF1A2330);
        });

        final LinearLayout[] mapIcons = {(LinearLayout) mapControlOption1.getChildAt(0), (LinearLayout) mapControlOption2.getChildAt(0)};
        mapControlRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isEnabled = (checkedId == 100);
            prefs.edit().putBoolean("mapControlKeyEnabled", isEnabled).apply();
            if (isEnabled) {
                callback.log("Harita kontrol tuşu açıldı");
                callback.onStartKeyEventListener();
            } else {
                callback.log("Harita kontrol tuşu kapatıldı");
                callback.onStopKeyEventListener();
            }
            mapIcons[0].setBackgroundColor(isEnabled ? 0xFF3DAEA8 : 0xFF1A2330);
            mapIcons[1].setBackgroundColor(!isEnabled ? 0xFF3DAEA8 : 0xFF1A2330);
        });

        return scrollView;
    }

    private void updateProjectionUI(TextView statusText) {
        if (statusText == null) return;
        statusText.setText(isNavigationOpenLocal ? "Yansıtma aktif" : "Yansıtma kapalı");
    }

    private void handleButtonClickWithDelay(Button button, String originalText, String loadingText) {
        if (button == null) return;
        button.setEnabled(false);
        button.setText(loadingText);
        button.setAlpha(0.6f);
        handler.postDelayed(() -> {
            button.setText(originalText);
            button.setAlpha(1.0f);
            button.setEnabled(true);
        }, 2500);
    }

    private void refreshTargetLabel() {
        if (targetAppLabel == null) return;
        String pkg = callback.getTargetPackage();
        if (pkg == null || pkg.trim().isEmpty()) {
            targetAppLabel.setText("(seçilmedi)");
        } else {
            targetAppLabel.setText(pkg.trim());
        }
    }

    private void selectTargetApp() {
        try {
            callback.log("Yüklü uygulamalar listeleniyor...");
            PackageManager pm = context.getPackageManager();

            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> launcherApps = pm.queryIntentActivities(mainIntent, 0);

            if (launcherApps == null || launcherApps.isEmpty()) {
                Toast.makeText(context, "Liste oluşturulamadı", Toast.LENGTH_SHORT).show();
                return;
            }

            Set<String> seen = new HashSet<>();
            java.util.List<java.util.Map.Entry<String, String>> appList = new java.util.ArrayList<>();
            for (ResolveInfo info : launcherApps) {
                try {
                    String pkg = info.activityInfo.packageName;
                    if (pkg == null || pkg.isEmpty() || seen.contains(pkg)) continue;
                    seen.add(pkg);
                    if ("com.mapcontrol".equals(pkg)) continue;

                    ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                    if (callback.isSystemOrPrivApp(appInfo)) continue;

                    String appName = pm.getApplicationLabel(appInfo).toString();
                    if (appName == null || appName.trim().isEmpty()) {
                        appName = pkg;
                    }
                    appList.add(new java.util.AbstractMap.SimpleEntry<>(appName, pkg));
                } catch (Exception ignored) {
                }
            }

            if (appList.isEmpty()) {
                Toast.makeText(context, "Liste oluşturulamadı", Toast.LENGTH_SHORT).show();
                return;
            }

            appList.sort((a, b) -> a.getKey().compareToIgnoreCase(b.getKey()));
            java.util.List<String> appNames = new java.util.ArrayList<>();
            java.util.List<String> sortedPackages = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, String> entry : appList) {
                appNames.add(entry.getKey() + " (" + entry.getValue() + ")");
                sortedPackages.add(entry.getValue());
            }

            String[] items = appNames.toArray(new String[0]);
            String titleText = "📱 Yüklü Uygulamalar (" + items.length + ")";

            DialogHelper.showAppSelectionDialog(
                    context,
                    titleText,
                    items,
                    sortedPackages,
                    null,
                    selectedPkg -> {
                        callback.onTargetPackageSelected(selectedPkg);
                        refreshTargetLabel();
                        Toast.makeText(context, "Seçildi: " + selectedPkg, Toast.LENGTH_SHORT).show();
                    },
                    null,
                    () -> {
                        callback.onTargetPackageSelected("");
                        refreshTargetLabel();
                        Toast.makeText(context, "✅ Hedef uygulama temizlendi", Toast.LENGTH_SHORT).show();
                    }
            );
        } catch (Exception e) {
            callback.log("selectTargetApp hatası: " + e.getMessage());
            Toast.makeText(context, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout createPowerModeOption(String emoji, String title, String desc, int id) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setPadding(16, 16, 16, 16);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setClickable(true);
        container.setFocusable(true);

        LinearLayout iconCircle = new LinearLayout(context);
        iconCircle.setOrientation(LinearLayout.VERTICAL);
        iconCircle.setBackgroundColor(0xFF1A2330);
        iconCircle.setGravity(Gravity.CENTER);
        iconCircle.setPadding(10, 10, 10, 10);
        iconCircle.setId(View.generateViewId());

        TextView icon = new TextView(context);
        icon.setText(emoji);
        icon.setTextSize(20);
        icon.setTextColor(0xFFFFFFFF);
        iconCircle.addView(icon);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(48, 48);
        iconParams.setMargins(0, 0, 12, 0);
        container.addView(iconCircle, iconParams);

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
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        container.addView(textColumn, textParams);

        RadioButton radioButton = new RadioButton(context);
        radioButton.setId(id);
        radioButton.setClickable(false);
        radioButton.setFocusable(false);
        container.addView(radioButton);
        container.setOnClickListener(v -> powerModeRadioGroup.check(id));
        return container;
    }

    private LinearLayout createSimpleToggleOption(RadioGroup group, String emoji, String title, String desc, int id) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setPadding(16, 16, 16, 16);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setClickable(true);
        container.setFocusable(true);

        LinearLayout iconCircle = new LinearLayout(context);
        iconCircle.setOrientation(LinearLayout.VERTICAL);
        iconCircle.setBackgroundColor(0xFF1A2330);
        iconCircle.setGravity(Gravity.CENTER);
        iconCircle.setPadding(10, 10, 10, 10);

        TextView icon = new TextView(context);
        icon.setText(emoji);
        icon.setTextSize(20);
        icon.setTextColor(0xFFFFFFFF);
        iconCircle.addView(icon);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(48, 48);
        iconParams.setMargins(0, 0, 12, 0);
        container.addView(iconCircle, iconParams);

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(context);
        t.setText(title);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(16);
        textCol.addView(t);
        TextView d = new TextView(context);
        d.setText(desc);
        d.setTextColor(0xAAFFFFFF);
        d.setTextSize(13);
        d.setPadding(0, 2, 0, 0);
        textCol.addView(d);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        container.addView(textCol, textParams);

        RadioButton rb = new RadioButton(context);
        rb.setId(id);
        rb.setClickable(false);
        rb.setFocusable(false);
        container.addView(rb);
        container.setOnClickListener(v -> group.check(id));
        group.addView(container);
        return container;
    }

    public ScrollView getScrollView() {
        return scrollView != null ? scrollView : build();
    }

    public LinearLayout getProjectionTabContent() {
        return projectionTabContent;
    }

    public TextView getTargetAppLabel() {
        refreshTargetLabel();
        return targetAppLabel;
    }
}
