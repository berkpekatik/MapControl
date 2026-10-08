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
import com.mapcontrol.media.LauncherMediaController;
import com.mapcontrol.nav.GoogleMapsNavNotificationCoordinator;
import com.mapcontrol.nav.GoogleMapsNavSnapshot;
import com.mapcontrol.ui.theme.UiStyles;

import com.mapcontrol.util.AppLaunchHelper;
import com.mapcontrol.util.ClusterNavigationState;
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
    private TextView projectionStatusText;
    private TextView googleMapsNavSummaryText;

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
        projectionTitle.setText(R.string.projection_title);
        projectionTitle.setTextSize(18);
        projectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        projectionTitle.setTypeface(null, Typeface.BOLD);
        projectionTitle.setPadding(16, 16, 16, 8);
        projectionTabContent.addView(projectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView projectionStatus = new TextView(context);
        projectionStatus.setText(R.string.projection_status_off);
        projectionStatus.setTextSize(13);
        projectionStatus.setTextColor(UiStyles.color(context, R.color.textHint));
        projectionStatus.setPadding(16, 0, 16, 16);
        projectionStatus.setId(View.generateViewId());
        projectionTabContent.addView(projectionStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        projectionStatusText = projectionStatus;

        LinearLayout controlButtonContainer = new LinearLayout(context);
        controlButtonContainer.setOrientation(LinearLayout.HORIZONTAL);
        controlButtonContainer.setPadding(16, 0, 16, 16);

        Button btnOpen = new Button(context);
        btnOpen.setText(R.string.projection_open);
        btnOpen.setTextColor(UiStyles.color(context, R.color.textPrimary));
        btnOpen.setTextSize(16);
        btnOpen.setTypeface(null, Typeface.BOLD);
        UiStyles.styleOemButton(btnOpen, UiStyles.color(context, R.color.buttonPrimary));
        btnOpen.setPadding(16, 20, 16, 20);
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        openParams.setMargins(0, 0, 8, 0);
        btnOpen.setId(View.generateViewId());
        controlButtonContainer.addView(btnOpen, openParams);

        Button btnClose = new Button(context);
        btnClose.setText(R.string.projection_stop);
        btnClose.setTextColor(UiStyles.color(context, R.color.textPrimary));
        btnClose.setTextSize(16);
        btnClose.setTypeface(null, Typeface.BOLD);
        UiStyles.styleOemButton(btnClose, UiStyles.color(context, R.color.statusErrorBright));
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
            refreshProjectionStatusUi();
            handleButtonClickWithDelay(btnOpen,
                    context.getString(R.string.projection_open),
                    context.getString(R.string.projection_projecting));
        });

        btnClose.setOnClickListener(v -> {
            callback.onCloseCluster();
            refreshProjectionStatusUi();
            handleButtonClickWithDelay(btnClose,
                    context.getString(R.string.projection_stop),
                    context.getString(R.string.projection_stopping));
        });

        handler.post(this::refreshProjectionStatusUi);

        TextView appTitle = new TextView(context);
        appTitle.setText(R.string.projection_app_title);
        appTitle.setTextSize(18);
        appTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        appTitle.setTypeface(null, Typeface.BOLD);
        appTitle.setPadding(16, 16, 16, 8);
        projectionTabContent.addView(appTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout appCard = new LinearLayout(context);
        appCard.setOrientation(LinearLayout.VERTICAL);
        UiStyles.applySolidRoundedBackgroundDp(appCard,
                UiStyles.color(context, R.color.surfaceCard), 16f);
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
                UiStyles.color(context, R.color.surfaceCardInner), 12f);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setPadding(16, 16, 16, 16);

        AppCompatImageView mapIcon = new AppCompatImageView(context);
        mapIcon.setImageResource(R.drawable.ic_mdi_map);
        mapIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int mapIconPx = Math.round(36 * context.getResources().getDisplayMetrics().density);
        mapIcon.setLayoutParams(new LinearLayout.LayoutParams(mapIconPx, mapIconPx));
        mapIcon.setImageTintList(ColorStateList.valueOf(UiStyles.color(context, R.color.textPrimary)));
        iconBox.addView(mapIcon);

        LinearLayout.LayoutParams iconBoxParams = new LinearLayout.LayoutParams(80, 80);
        iconBoxParams.setMargins(0, 0, 16, 0);
        appInfo.addView(iconBox, iconBoxParams);

        LinearLayout textInfo = new LinearLayout(context);
        textInfo.setOrientation(LinearLayout.VERTICAL);
        textInfo.setPadding(0, 0, 0, 0);

        targetAppLabel = new TextView(context);
        targetAppLabel.setText(R.string.common_not_selected);
        targetAppLabel.setTextColor(UiStyles.color(context, R.color.textPrimary));
        targetAppLabel.setTextSize(17);
        targetAppLabel.setTypeface(null, Typeface.NORMAL);
        LinearLayout.LayoutParams targetLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        targetLabelParams.setMargins(0, 0, 0, 4);
        textInfo.addView(targetAppLabel, targetLabelParams);

        TextView appDesc = new TextView(context);
        appDesc.setText(R.string.projection_app_desc);
        appDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        appDesc.setTextSize(13);
        textInfo.addView(appDesc);

        appInfo.addView(textInfo, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        appCard.addView(appInfo);

        LinearLayout appButtons = new LinearLayout(context);
        appButtons.setOrientation(LinearLayout.HORIZONTAL);

        Button btnSelectApp = new Button(context);
        btnSelectApp.setText(R.string.projection_change_app);
        btnSelectApp.setTextColor(UiStyles.color(context, R.color.textPrimary80));
        btnSelectApp.setTextSize(14);
        btnSelectApp.setTypeface(null, Typeface.NORMAL);
        UiStyles.styleOemButton(btnSelectApp, UiStyles.color(context, R.color.surfaceCardInner));
        btnSelectApp.setPadding(16, 14, 16, 14);
        LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        selectParams.setMargins(0, 0, 6, 0);
        appButtons.addView(btnSelectApp, selectParams);
        btnSelectApp.setOnClickListener(v -> selectTargetApp());

        Button btnLaunchOnCluster = new Button(context);
        btnLaunchOnCluster.setText(R.string.projection_launch_cluster);
        btnLaunchOnCluster.setTextColor(UiStyles.color(context, R.color.textPrimary));
        btnLaunchOnCluster.setTextSize(14);
        btnLaunchOnCluster.setTypeface(null, Typeface.BOLD);
        UiStyles.styleOemButton(btnLaunchOnCluster, UiStyles.color(context, R.color.buttonPrimary));
        btnLaunchOnCluster.setPadding(16, 14, 16, 14);
        LinearLayout.LayoutParams launchParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        launchParams.setMargins(6, 0, 0, 0);
        appButtons.addView(btnLaunchOnCluster, launchParams);
        btnLaunchOnCluster.setOnClickListener(v -> {
            String pkg = callback.getTargetPackage();
            if (pkg == null || pkg.trim().isEmpty()) {
                Toast.makeText(context, R.string.projection_select_app_first, Toast.LENGTH_SHORT).show();
                callback.log("Uygulama seçilmedi");
                return;
            }
            String trimmed = pkg.trim();
            if (context.getPackageManager().getLaunchIntentForPackage(trimmed) == null) {
                Toast.makeText(context, context.getString(R.string.projection_app_not_found, pkg), Toast.LENGTH_SHORT).show();
                callback.log("Launch intent bulunamadı: " + pkg);
                return;
            }
            try {
                AppLaunchHelper.launchAppOnDisplay(context, trimmed, Display.DEFAULT_DISPLAY, Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                callback.log("Uygulama başlatıldı (displayId=" + Display.DEFAULT_DISPLAY + "): " + pkg);
                callback.onBringToMainDisplayCheckClusterSplash();
            } catch (Exception e) {
                callback.log("launchSelectedAppOnDisplay hatası: " + e.getMessage());
                Toast.makeText(context, context.getString(R.string.common_error_prefix, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });

        appCard.addView(appButtons);
        projectionTabContent.addView(appCard, appCardParams);

        TextView mainGroupTitle = new TextView(context);
        mainGroupTitle.setText(R.string.projection_nav_behavior);
        mainGroupTitle.setTextSize(18);
        mainGroupTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        mainGroupTitle.setTypeface(null, Typeface.BOLD);
        mainGroupTitle.setPadding(16, 24, 16, 8);
        projectionTabContent.addView(mainGroupTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout mainCardContainer = new LinearLayout(context);
        mainCardContainer.setOrientation(LinearLayout.VERTICAL);
        UiStyles.applySolidRoundedBackgroundDp(mainCardContainer,
                UiStyles.color(context, R.color.surfaceCard), 16f);
        LinearLayout.LayoutParams mainCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        mainCardParams.setMargins(16, 0, 16, 32);

        TextView section1Title = new TextView(context);
        section1Title.setText(R.string.projection_start_section);
        section1Title.setTextSize(17);
        section1Title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        section1Title.setTypeface(null, Typeface.BOLD);
        section1Title.setPadding(20, 20, 20, 8);
        mainCardContainer.addView(section1Title);

        TextView section1Desc = new TextView(context);
        section1Desc.setText(R.string.projection_start_desc);
        section1Desc.setTextSize(13);
        section1Desc.setTextColor(UiStyles.color(context, R.color.textHint));
        section1Desc.setPadding(20, 0, 20, 12);
        mainCardContainer.addView(section1Desc);

        final UiStyles.TernarySegmentHandle[] powerHandle = new UiStyles.TernarySegmentHandle[1];
        powerHandle[0] = UiStyles.addTernarySegmentedControl(context, mainCardContainer,
                null,
                new String[]{
                        context.getString(R.string.projection_power_engine),
                        context.getString(R.string.projection_power_ready),
                        context.getString(R.string.projection_power_manual)
                },
                new String[]{
                        context.getString(R.string.projection_power_engine_help),
                        context.getString(R.string.projection_power_ready_help),
                        context.getString(R.string.projection_power_manual_help)
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
        sectionDivider1.setBackgroundColor(UiStyles.color(context, R.color.dividerWhite12));
        LinearLayout.LayoutParams dividerParams1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams1.setMargins(20, 24, 20, 24);
        mainCardContainer.addView(sectionDivider1, dividerParams1);

        TextView section2Title = new TextView(context);
        section2Title.setText(R.string.projection_stop_section);
        section2Title.setTextSize(15);
        section2Title.setTextColor(UiStyles.color(context, R.color.textPrimary87));
        section2Title.setTypeface(null, Typeface.NORMAL);
        section2Title.setPadding(20, 0, 20, 8);
        mainCardContainer.addView(section2Title);

        TextView section2Desc = new TextView(context);
        section2Desc.setText(R.string.projection_stop_desc);
        section2Desc.setTextSize(13);
        section2Desc.setTextColor(UiStyles.color(context, R.color.textHint));
        section2Desc.setPadding(20, 0, 20, 12);
        mainCardContainer.addView(section2Desc);

        LinearLayout autoCloseBlock = new LinearLayout(context);
        autoCloseBlock.setOrientation(LinearLayout.VERTICAL);
        autoCloseBlock.setPadding(20, 0, 20, 0);
        mainCardContainer.addView(autoCloseBlock);

        UiStyles.addBinarySegmentedControl(context, autoCloseBlock,
                null,
                context.getString(R.string.common_yes),
                context.getString(R.string.common_no),
                context.getString(R.string.projection_auto_close_on_help),
                context.getString(R.string.projection_auto_close_off_help),
                prefs.getBoolean("autoCloseOnPowerOff", true),
                isEnabled -> {
                    prefs.edit().putBoolean("autoCloseOnPowerOff", isEnabled).apply();
                    callback.log(isEnabled
                            ? "Araç kapanınca otomatik kapatma açıldı"
                            : "Araç kapanınca otomatik kapatma kapatıldı");
                });

        View sectionDivider2 = new View(context);
        sectionDivider2.setBackgroundColor(UiStyles.color(context, R.color.dividerWhite12));
        LinearLayout.LayoutParams dividerParams2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams2.setMargins(20, 24, 20, 24);
        mainCardContainer.addView(sectionDivider2, dividerParams2);

        TextView mapKeyTitle = new TextView(context);
        mapKeyTitle.setText(R.string.projection_map_key_title);
        mapKeyTitle.setTextSize(15);
        mapKeyTitle.setTextColor(UiStyles.color(context, R.color.textPrimary87));
        mapKeyTitle.setTypeface(null, Typeface.NORMAL);
        mapKeyTitle.setPadding(20, 0, 20, 8);
        mainCardContainer.addView(mapKeyTitle);

        TextView mapKeyDesc = new TextView(context);
        mapKeyDesc.setText(R.string.projection_map_key_desc);
        mapKeyDesc.setTextSize(13);
        mapKeyDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        mapKeyDesc.setPadding(20, 0, 20, 12);
        mainCardContainer.addView(mapKeyDesc);

        LinearLayout mapControlBlock = new LinearLayout(context);
        mapControlBlock.setOrientation(LinearLayout.VERTICAL);
        mapControlBlock.setPadding(20, 0, 20, 0);
        mainCardContainer.addView(mapControlBlock);

        UiStyles.addBinarySegmentedControl(context, mapControlBlock,
                null,
                context.getString(R.string.common_on),
                context.getString(R.string.common_off),
                context.getString(R.string.projection_map_key_on_help),
                context.getString(R.string.projection_map_key_off_help),
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

        View sectionDividerMaps = new View(context);
        sectionDividerMaps.setBackgroundColor(UiStyles.color(context, R.color.dividerWhite12));
        LinearLayout.LayoutParams dividerParamsMaps = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParamsMaps.setMargins(20, 24, 20, 24);
        mainCardContainer.addView(sectionDividerMaps, dividerParamsMaps);

        TextView mapsNavTitle = new TextView(context);
        mapsNavTitle.setText(R.string.projection_google_maps_nav_title);
        mapsNavTitle.setTextSize(15);
        mapsNavTitle.setTextColor(UiStyles.color(context, R.color.textPrimary87));
        mapsNavTitle.setTypeface(null, Typeface.NORMAL);
        mapsNavTitle.setPadding(20, 0, 20, 8);
        mainCardContainer.addView(mapsNavTitle);

        TextView mapsNavDesc = new TextView(context);
        mapsNavDesc.setText(R.string.projection_google_maps_nav_desc);
        mapsNavDesc.setTextSize(13);
        mapsNavDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        mapsNavDesc.setPadding(20, 0, 20, 12);
        mapsNavDesc.setLineSpacing(3, 1.05f);
        mainCardContainer.addView(mapsNavDesc);

        LinearLayout mapsNavBlock = new LinearLayout(context);
        mapsNavBlock.setOrientation(LinearLayout.VERTICAL);
        mapsNavBlock.setPadding(20, 0, 20, 0);
        mainCardContainer.addView(mapsNavBlock);

        final UiStyles.BinarySegmentHandle[] mapsNavHandle = new UiStyles.BinarySegmentHandle[1];
        mapsNavHandle[0] = UiStyles.addBinarySegmentedControl(context, mapsNavBlock,
                null,
                context.getString(R.string.common_on),
                context.getString(R.string.common_off),
                context.getString(R.string.projection_google_maps_nav_on_help),
                context.getString(R.string.projection_google_maps_nav_off_help),
                GoogleMapsNavNotificationCoordinator.isAutoProjectionEnabled(context),
                isEnabled -> {
                    if (isEnabled && !LauncherMediaController.isNotificationAccessEnabled(context)) {
                        LauncherMediaController controller = new LauncherMediaController(context);
                        controller.openNotificationAccessSettings();
                        Toast.makeText(context,
                                R.string.projection_google_maps_nav_need_access,
                                Toast.LENGTH_LONG).show();
                        mapsNavHandle[0].setLeftSelected(false);
                        callback.log("Google Maps bildirimi: bildirim erişimi kapalı");
                        return;
                    }
                    GoogleMapsNavNotificationCoordinator.setAutoProjectionEnabled(context, isEnabled);
                    callback.log(isEnabled
                            ? "Google Maps bildirimiyle otomatik yansıtma açıldı"
                            : "Google Maps bildirimiyle otomatik yansıtma kapatıldı");
                });

        googleMapsNavSummaryText = new TextView(context);
        googleMapsNavSummaryText.setTextSize(13);
        googleMapsNavSummaryText.setTextColor(UiStyles.color(context, R.color.textHint));
        googleMapsNavSummaryText.setPadding(20, 12, 20, 8);
        mainCardContainer.addView(googleMapsNavSummaryText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        updateGoogleMapsNavSummary(GoogleMapsNavNotificationCoordinator.getLastSnapshot());

        TextView mapsClusterTitle = new TextView(context);
        mapsClusterTitle.setText(R.string.projection_google_maps_cluster_cards_title);
        mapsClusterTitle.setTextSize(15);
        mapsClusterTitle.setTextColor(UiStyles.color(context, R.color.textPrimary87));
        mapsClusterTitle.setTypeface(null, Typeface.NORMAL);
        mapsClusterTitle.setPadding(20, 20, 20, 8);
        mainCardContainer.addView(mapsClusterTitle);

        TextView mapsClusterDesc = new TextView(context);
        mapsClusterDesc.setText(R.string.projection_google_maps_cluster_cards_desc);
        mapsClusterDesc.setTextSize(13);
        mapsClusterDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        mapsClusterDesc.setPadding(20, 0, 20, 12);
        mapsClusterDesc.setLineSpacing(3, 1.05f);
        mainCardContainer.addView(mapsClusterDesc);

        LinearLayout mapsClusterBlock = new LinearLayout(context);
        mapsClusterBlock.setOrientation(LinearLayout.VERTICAL);
        mapsClusterBlock.setPadding(20, 0, 20, 0);
        mainCardContainer.addView(mapsClusterBlock);

        UiStyles.addBinarySegmentedControl(context, mapsClusterBlock,
                context.getString(R.string.projection_google_maps_cluster_overlay_label),
                context.getString(R.string.common_on),
                context.getString(R.string.common_off),
                context.getString(R.string.projection_google_maps_cluster_overlay_on_help),
                context.getString(R.string.projection_google_maps_cluster_overlay_off_help),
                GoogleMapsNavNotificationCoordinator.isClusterOverlayEnabled(context),
                isEnabled -> {
                    if (isEnabled && !LauncherMediaController.isNotificationAccessEnabled(context)) {
                        new LauncherMediaController(context).openNotificationAccessSettings();
                        Toast.makeText(context,
                                R.string.projection_google_maps_nav_need_access,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    GoogleMapsNavNotificationCoordinator.setClusterOverlayEnabled(context, isEnabled);
                    callback.log(isEnabled
                            ? "Google Maps cluster kartları açıldı"
                            : "Google Maps cluster kartları kapatıldı");
                });

        projectionTabContent.addView(mainCardContainer, mainCardParams);

        return scrollView;
    }

    /** {@link ClusterNavigationState} ile uyumlu durum metni (servis/tuş/sekme ortak). */
    public void refreshProjectionStatusUi() {
        if (projectionStatusText == null) {
            return;
        }
        projectionStatusText.setText(ClusterNavigationState.getLastKnownOpen()
                ? R.string.projection_status_on
                : R.string.projection_status_off);
    }

    public void updateGoogleMapsNavSummary(GoogleMapsNavSnapshot snapshot) {
        if (googleMapsNavSummaryText == null) {
            return;
        }
        CharSequence next;
        if (snapshot == null || !snapshot.active) {
            next = contextText(R.string.projection_google_maps_nav_summary_idle);
        } else {
            String line = snapshot.formatSummaryLine();
            if (line == null || line.isEmpty()) {
                next = contextText(R.string.projection_google_maps_nav_summary_idle);
            } else {
                next = line;
            }
        }
        CharSequence current = googleMapsNavSummaryText.getText();
        if (current == null || !current.toString().contentEquals(next)) {
            googleMapsNavSummaryText.setText(next);
        }
    }

    private CharSequence contextText(int resId) {
        return googleMapsNavSummaryText.getContext().getText(resId);
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
            targetAppLabel.setText(R.string.common_not_selected);
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
                Toast.makeText(context, R.string.projection_list_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            java.util.List<String> appNames = new java.util.ArrayList<>();
            java.util.List<String> sortedPackages = new java.util.ArrayList<>();
            for (ProjectionTargetApps.Row row : rows) {
                appNames.add(row.label + " (" + row.packageName + ")");
                sortedPackages.add(row.packageName);
            }

            String[] items = appNames.toArray(new String[0]);
            String titleText = context.getString(R.string.projection_installed_apps, items.length);

            DialogHelper.showAppSelectionDialog(
                    context,
                    titleText,
                    items,
                    sortedPackages,
                    null,
                    selectedPkg -> {
                        callback.onTargetPackageSelected(selectedPkg);
                        refreshTargetLabel();
                        Toast.makeText(context, context.getString(R.string.projection_selected, selectedPkg), Toast.LENGTH_SHORT).show();
                    },
                    null,
                    () -> {
                        callback.onTargetPackageSelected("");
                        refreshTargetLabel();
                        Toast.makeText(context, R.string.projection_target_cleared, Toast.LENGTH_SHORT).show();
                    }
            );
        } catch (Exception e) {
            callback.log("selectTargetApp hatası: " + e.getMessage());
            Toast.makeText(context, context.getString(R.string.common_error_prefix, e.getMessage()), Toast.LENGTH_SHORT).show();
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
