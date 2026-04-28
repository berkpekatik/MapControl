package com.mapcontrol.ui.builder;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.Display;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

import com.mapcontrol.util.AppLaunchHelper;
import com.mapcontrol.util.DialogHelper;
import com.mapcontrol.util.ProjectionTargetApps;

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
        /** "Ana Ekrana Al" sonrası cluster boşsa boot splash (ClusterDisplayManager + getAppOnDisplay2). */
        void onBringToMainDisplayCheckClusterSplash();
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final ProjectionCallback callback;
    private final Handler handler;

    private ScrollView scrollView;
    private LinearLayout projectionTabContent;
    private TextView targetAppLabel;
    private boolean isNavigationOpenLocal = false;

    public ProjectionTabBuilder(Context context, SharedPreferences prefs, ProjectionCallback callback) {
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

        projectionTabContent = new LinearLayout(context);
        projectionTabContent.setOrientation(LinearLayout.VERTICAL);
        int inner = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        projectionTabContent.setPadding(inner, inner, inner, inner);
        UiStyles.setGlassCardBackground(projectionTabContent);

        outer.addView(projectionTabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView projectionTitle = new TextView(context);
        projectionTitle.setText("Yansıtma Kontrolü");
        projectionTitle.setTextSize(18);
        projectionTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        projectionTitle.setTypeface(null, Typeface.BOLD);
        projectionTitle.setPadding(16, 16, 16, 8);
        projectionTabContent.addView(projectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView projectionStatus = new TextView(context);
        projectionStatus.setText("Yansıtma kapalı");
        projectionStatus.setTextSize(13);
        projectionStatus.setTextColor(ContextCompat.getColor(context, R.color.textHint));
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
        btnOpen.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        btnOpen.setTextSize(16);
        btnOpen.setTypeface(null, Typeface.BOLD);
        UiStyles.styleOemButton(btnOpen, ContextCompat.getColor(context, R.color.buttonPrimary));
        btnOpen.setPadding(16, 20, 16, 20);
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        openParams.setMargins(0, 0, 8, 0);
        btnOpen.setId(View.generateViewId());
        controlButtonContainer.addView(btnOpen, openParams);

        Button btnClose = new Button(context);
        btnClose.setText("Durdur");
        btnClose.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        btnClose.setTextSize(16);
        btnClose.setTypeface(null, Typeface.BOLD);
        UiStyles.styleOemButton(btnClose, ContextCompat.getColor(context, R.color.statusErrorBright));
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
        appTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        appTitle.setTypeface(null, Typeface.BOLD);
        appTitle.setPadding(16, 16, 16, 8);
        projectionTabContent.addView(appTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout appCard = new LinearLayout(context);
        appCard.setOrientation(LinearLayout.VERTICAL);
        UiStyles.applySolidRoundedBackgroundDp(appCard,
                ContextCompat.getColor(context, R.color.surfaceCard), 16f);
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
        UiStyles.applySolidRoundedBackgroundDp(iconBox,
                ContextCompat.getColor(context, R.color.surfaceCardInner), 12f);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setPadding(16, 16, 16, 16);

        AppCompatImageView mapIcon = new AppCompatImageView(context);
        mapIcon.setImageResource(R.drawable.ic_mdi_map);
        mapIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int mapIconPx = Math.round(36 * context.getResources().getDisplayMetrics().density);
        mapIcon.setLayoutParams(new LinearLayout.LayoutParams(mapIconPx, mapIconPx));
        mapIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.textPrimary)));
        iconBox.addView(mapIcon);

        LinearLayout.LayoutParams iconBoxParams = new LinearLayout.LayoutParams(80, 80);
        iconBoxParams.setMargins(0, 0, 16, 0);
        appInfo.addView(iconBox, iconBoxParams);

        LinearLayout textInfo = new LinearLayout(context);
        textInfo.setOrientation(LinearLayout.VERTICAL);
        textInfo.setPadding(0, 0, 0, 0);

        targetAppLabel = new TextView(context);
        targetAppLabel.setText("(seçilmedi)");
        targetAppLabel.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        targetAppLabel.setTextSize(17);
        targetAppLabel.setTypeface(null, Typeface.NORMAL);
        LinearLayout.LayoutParams targetLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        targetLabelParams.setMargins(0, 0, 0, 4);
        textInfo.addView(targetAppLabel, targetLabelParams);

        TextView appDesc = new TextView(context);
        appDesc.setText("Seçili uygulamayı araç ekranına yansıt");
        appDesc.setTextColor(ContextCompat.getColor(context, R.color.textHint));
        appDesc.setTextSize(13);
        textInfo.addView(appDesc);

        appInfo.addView(textInfo, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        appCard.addView(appInfo);

        LinearLayout appButtons = new LinearLayout(context);
        appButtons.setOrientation(LinearLayout.HORIZONTAL);

        Button btnSelectApp = new Button(context);
        btnSelectApp.setText("Değiştir");
        btnSelectApp.setTextColor(ContextCompat.getColor(context, R.color.textPrimary80));
        btnSelectApp.setTextSize(14);
        btnSelectApp.setTypeface(null, Typeface.NORMAL);
        UiStyles.styleOemButton(btnSelectApp, ContextCompat.getColor(context, R.color.surfaceCardInner));
        btnSelectApp.setPadding(16, 14, 16, 14);
        LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        selectParams.setMargins(0, 0, 6, 0);
        appButtons.addView(btnSelectApp, selectParams);
        btnSelectApp.setOnClickListener(v -> selectTargetApp());

        Button btnLaunchOnCluster = new Button(context);
        btnLaunchOnCluster.setText("Ana Ekrana Al");
        btnLaunchOnCluster.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        btnLaunchOnCluster.setTextSize(14);
        btnLaunchOnCluster.setTypeface(null, Typeface.BOLD);
        UiStyles.styleOemButton(btnLaunchOnCluster, ContextCompat.getColor(context, R.color.buttonPrimary));
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
                callback.onBringToMainDisplayCheckClusterSplash();
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
        mainGroupTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        mainGroupTitle.setTypeface(null, Typeface.BOLD);
        mainGroupTitle.setPadding(16, 24, 16, 8);
        projectionTabContent.addView(mainGroupTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout mainCardContainer = new LinearLayout(context);
        mainCardContainer.setOrientation(LinearLayout.VERTICAL);
        UiStyles.applySolidRoundedBackgroundDp(mainCardContainer,
                ContextCompat.getColor(context, R.color.surfaceCard), 16f);
        LinearLayout.LayoutParams mainCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        mainCardParams.setMargins(16, 0, 16, 32);

        TextView section1Title = new TextView(context);
        section1Title.setText("Başlatma");
        section1Title.setTextSize(17);
        section1Title.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        section1Title.setTypeface(null, Typeface.BOLD);
        section1Title.setPadding(20, 20, 20, 8);
        mainCardContainer.addView(section1Title);

        TextView section1Desc = new TextView(context);
        section1Desc.setText("Ne zaman başlasın?");
        section1Desc.setTextSize(13);
        section1Desc.setTextColor(ContextCompat.getColor(context, R.color.textHint));
        section1Desc.setPadding(20, 0, 20, 12);
        mainCardContainer.addView(section1Desc);

        final UiStyles.TernarySegmentHandle[] powerHandle = new UiStyles.TernarySegmentHandle[1];
        powerHandle[0] = UiStyles.addTernarySegmentedControl(context, mainCardContainer,
                null,
                new String[]{"Motor", "Hazır", "Elle"},
                new String[]{
                        "Direkt start verildiğinde",
                        "Engine Start 1 kere basınca (Frensiz)",
                        "Kendiniz istediğinize zaman başlatın"
                },
                new int[]{2, 1, 0},
                prefs.getInt("powerModeSetting", 2),
                modeId -> {
                    callback.onSavePowerMode(modeId);
                    String modeName = modeId == 2 ? "Motor Çalışınca"
                            : (modeId == 1 ? "Araç Hazır Durumdayken" : "Elle Çalıştır");
                    callback.log("Navigasyon açma modu: " + modeName);
                });
        handler.post(() -> powerHandle[0].syncVisualFromModeId(prefs.getInt("powerModeSetting", 2)));

        View sectionDivider1 = new View(context);
        sectionDivider1.setBackgroundColor(ContextCompat.getColor(context, R.color.dividerWhite12));
        LinearLayout.LayoutParams dividerParams1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams1.setMargins(20, 24, 20, 24);
        mainCardContainer.addView(sectionDivider1, dividerParams1);

        TextView section2Title = new TextView(context);
        section2Title.setText("Kapanış");
        section2Title.setTextSize(15);
        section2Title.setTextColor(ContextCompat.getColor(context, R.color.textPrimary87));
        section2Title.setTypeface(null, Typeface.NORMAL);
        section2Title.setPadding(20, 0, 20, 8);
        mainCardContainer.addView(section2Title);

        TextView section2Desc = new TextView(context);
        section2Desc.setText("Araç kapanınca ne olsun?");
        section2Desc.setTextSize(13);
        section2Desc.setTextColor(ContextCompat.getColor(context, R.color.textHint));
        section2Desc.setPadding(20, 0, 20, 12);
        mainCardContainer.addView(section2Desc);

        LinearLayout autoCloseBlock = new LinearLayout(context);
        autoCloseBlock.setOrientation(LinearLayout.VERTICAL);
        autoCloseBlock.setPadding(20, 0, 20, 0);
        mainCardContainer.addView(autoCloseBlock);

        UiStyles.addBinarySegmentedControl(context, autoCloseBlock,
                null,
                "Evet", "Hayır",
                "Araç kapanınca otomatik kapat.",
                "Otomatik kapatma yapılmayacak.",
                prefs.getBoolean("autoCloseOnPowerOff", true),
                isEnabled -> {
                    prefs.edit().putBoolean("autoCloseOnPowerOff", isEnabled).apply();
                    callback.log(isEnabled
                            ? "Araç kapanınca otomatik kapatma açıldı"
                            : "Araç kapanınca otomatik kapatma kapatıldı");
                });

        View sectionDivider2 = new View(context);
        sectionDivider2.setBackgroundColor(ContextCompat.getColor(context, R.color.dividerWhite12));
        LinearLayout.LayoutParams dividerParams2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams2.setMargins(20, 24, 20, 24);
        mainCardContainer.addView(sectionDivider2, dividerParams2);

        TextView mapKeyTitle = new TextView(context);
        mapKeyTitle.setText("Harita kontrol tuşu");
        mapKeyTitle.setTextSize(15);
        mapKeyTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary87));
        mapKeyTitle.setTypeface(null, Typeface.NORMAL);
        mapKeyTitle.setPadding(20, 0, 20, 8);
        mainCardContainer.addView(mapKeyTitle);

        TextView mapKeyDesc = new TextView(context);
        mapKeyDesc.setText("Donanım tuşu ile harita kontrolü");
        mapKeyDesc.setTextSize(13);
        mapKeyDesc.setTextColor(ContextCompat.getColor(context, R.color.textHint));
        mapKeyDesc.setPadding(20, 0, 20, 12);
        mainCardContainer.addView(mapKeyDesc);

        LinearLayout mapControlBlock = new LinearLayout(context);
        mapControlBlock.setOrientation(LinearLayout.VERTICAL);
        mapControlBlock.setPadding(20, 0, 20, 0);
        mainCardContainer.addView(mapControlBlock);

        UiStyles.addBinarySegmentedControl(context, mapControlBlock,
                null,
                "Açık", "Kapalı",
                "Harita kontrol tuşu aktif.",
                "Harita kontrol tuşu devre dışı.",
                prefs.getBoolean("mapControlKeyEnabled", true),
                isEnabled -> {
                    prefs.edit().putBoolean("mapControlKeyEnabled", isEnabled).apply();
                    if (isEnabled) {
                        callback.log("Harita kontrol tuşu açıldı");
                        callback.onStartKeyEventListener();
                    } else {
                        callback.log("Harita kontrol tuşu kapatıldı");
                        callback.onStopKeyEventListener();
                    }
                });

        projectionTabContent.addView(mainCardContainer, mainCardParams);

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

    /** Yüzen kontrol veya MainActivity extra ile hedef uygulama listesi (sekmedeki Değiştir ile aynı). */
    public void openTargetAppPicker() {
        selectTargetApp();
    }

    private void selectTargetApp() {
        try {
            callback.log("Yüklü uygulamalar listeleniyor...");
            java.util.List<ProjectionTargetApps.Row> rows = ProjectionTargetApps.loadSortedRows(context);
            if (rows.isEmpty()) {
                Toast.makeText(context, "Liste oluşturulamadı", Toast.LENGTH_SHORT).show();
                return;
            }

            java.util.List<String> appNames = new java.util.ArrayList<>();
            java.util.List<String> sortedPackages = new java.util.ArrayList<>();
            for (ProjectionTargetApps.Row row : rows) {
                appNames.add(row.label + " (" + row.packageName + ")");
                sortedPackages.add(row.packageName);
            }

            String[] items = appNames.toArray(new String[0]);
            String titleText = "Yüklü Uygulamalar (" + items.length + ")";

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
                        Toast.makeText(context, "Hedef uygulama temizlendi", Toast.LENGTH_SHORT).show();
                    }
            );
        } catch (Exception e) {
            callback.log("selectTargetApp hatası: " + e.getMessage());
            Toast.makeText(context, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
