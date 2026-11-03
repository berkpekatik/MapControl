package com.mapcontrol.ui.builder;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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
import com.mapcontrol.nav.ClusterNavOverlayOffsets;
import com.mapcontrol.nav.GoogleMapsClusterNavOverlay;
import com.mapcontrol.nav.GoogleMapsNavNotificationCoordinator;
import com.mapcontrol.nav.GoogleMapsNavSnapshot;
import com.mapcontrol.nav.YandexClusterNavCoordinator;
import com.mapcontrol.nav.YandexClusterNavOverlay;
import com.mapcontrol.service.GlobalBackService;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.ui.theme.FeatureGridLayout;

import com.mapcontrol.manager.CarSystemGestureController;
import com.mapcontrol.util.AppLaunchHelper;
import com.mapcontrol.util.ClusterNavigationState;
import com.mapcontrol.util.DialogHelper;
import com.mapcontrol.util.GestureConflicts;
import com.mapcontrol.util.ProjectionTargetApps;
import com.mapcontrol.util.UsageAccessHelper;

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

        outer.addView(projectionTabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        FeatureGridLayout grid = new FeatureGridLayout(context);
        projectionTabContent.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        createClusterControlCard(grid);
        createPowerModeCard(grid);
        createAutoCloseCard(grid);
        createMapKeyCard(grid);
        createGoogleMapsNavCard(grid);
        createMapSwipeCard(grid);
        createGoogleMapsClusterCard(grid);
        createYandexClusterCard(grid);

        handler.post(this::refreshProjectionStatusUi);

        return scrollView;
    }

    private LinearLayout createProjectionCard(FeatureGridLayout grid) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        card.setPadding(padding, padding, padding, padding);
        UiStyles.setGlassCardBackground(card);
        grid.addCard(card);
        return card;
    }

    private void addCardHeading(LinearLayout card, int titleRes, int descRes) {
        TextView title = new TextView(context);
        title.setText(titleRes);
        UiStyles.setTextSize(title, R.dimen.text_size_medium);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, UiStyles.dimenPx(context, R.dimen.spacing_tiny));
        card.addView(title, matchWrap());

        if (descRes > 0) {
            TextView desc = new TextView(context);
            desc.setText(descRes);
            UiStyles.setTextSize(desc, R.dimen.text_size_small);
            desc.setTextColor(UiStyles.color(context, R.color.textHint));
            desc.setPadding(0, 0, 0, UiStyles.dimenPx(context, R.dimen.spacing_small));
            card.addView(desc, matchWrap());
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void createClusterControlCard(FeatureGridLayout grid) {
        LinearLayout card = createProjectionCard(grid);
        addCardHeading(card, R.string.projection_title, -1);

        projectionStatusText = new TextView(context);
        projectionStatusText.setText(R.string.projection_status_off);
        UiStyles.setTextSize(projectionStatusText, R.dimen.text_size_small);
        projectionStatusText.setTextColor(UiStyles.color(context, R.color.textHint));
        projectionStatusText.setPadding(0, 0, 0, UiStyles.dimenPx(context, R.dimen.spacing_small));
        card.addView(projectionStatusText, matchWrap());

        LinearLayout controlButtonContainer = new LinearLayout(context);
        controlButtonContainer.setOrientation(LinearLayout.HORIZONTAL);
        controlButtonContainer.setPadding(0, 0, 0, UiStyles.dimenPx(context, R.dimen.spacing_medium));

        Button btnOpen = new Button(context);
        btnOpen.setText(R.string.projection_open);
        btnOpen.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.setTextSize(btnOpen, R.dimen.text_size_normal);
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
        UiStyles.setTextSize(btnClose, R.dimen.text_size_normal);
        btnClose.setTypeface(null, Typeface.BOLD);
        UiStyles.styleOemButton(btnClose, UiStyles.color(context, R.color.statusErrorBright));
        btnClose.setPadding(16, 20, 16, 20);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnClose.setId(View.generateViewId());
        controlButtonContainer.addView(btnClose, closeParams);

        card.addView(controlButtonContainer, matchWrap());

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

        LinearLayout appInfo = new LinearLayout(context);
        appInfo.setOrientation(LinearLayout.HORIZONTAL);
        appInfo.setGravity(Gravity.CENTER_VERTICAL);
        appInfo.setPadding(0, UiStyles.dimenPx(context, R.dimen.spacing_small), 0, UiStyles.dimenPx(context, R.dimen.spacing_small));

        LinearLayout iconBox = new LinearLayout(context);
        iconBox.setOrientation(LinearLayout.VERTICAL);
        UiStyles.applySolidRoundedBackgroundDp(iconBox,
                UiStyles.color(context, R.color.surfaceCardInner), 12f);
        iconBox.setGravity(Gravity.CENTER);
        int iconBoxPx = Math.round(52 * context.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams iconBoxParams = new LinearLayout.LayoutParams(iconBoxPx, iconBoxPx);
        iconBoxParams.setMargins(0, 0, UiStyles.dimenPx(context, R.dimen.spacing_small), 0);

        AppCompatImageView mapIcon = new AppCompatImageView(context);
        mapIcon.setImageResource(R.drawable.ic_mdi_map);
        mapIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int mapIconPx = Math.round(30 * context.getResources().getDisplayMetrics().density);
        mapIcon.setLayoutParams(new LinearLayout.LayoutParams(mapIconPx, mapIconPx));
        mapIcon.setImageTintList(ColorStateList.valueOf(UiStyles.color(context, R.color.textPrimary)));
        iconBox.addView(mapIcon);
        appInfo.addView(iconBox, iconBoxParams);

        LinearLayout textInfo = new LinearLayout(context);
        textInfo.setOrientation(LinearLayout.VERTICAL);

        targetAppLabel = new TextView(context);
        targetAppLabel.setText(R.string.common_not_selected);
        targetAppLabel.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.setTextSize(targetAppLabel, R.dimen.text_size_medium);
        targetAppLabel.setTypeface(null, Typeface.BOLD);
        textInfo.addView(targetAppLabel, matchWrap());

        TextView appDesc = new TextView(context);
        appDesc.setText(R.string.projection_app_desc);
        appDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        UiStyles.setTextSize(appDesc, R.dimen.text_size_small);
        textInfo.addView(appDesc, matchWrap());

        appInfo.addView(textInfo, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(appInfo, matchWrap());

        LinearLayout appButtons = new LinearLayout(context);
        appButtons.setOrientation(LinearLayout.HORIZONTAL);
        appButtons.setPadding(0, UiStyles.dimenPx(context, R.dimen.spacing_tiny), 0, 0);

        Button btnSelectApp = new Button(context);
        btnSelectApp.setText(R.string.projection_change_app);
        btnSelectApp.setTextColor(UiStyles.color(context, R.color.textPrimary80));
        UiStyles.setTextSize(btnSelectApp, R.dimen.text_size_small);
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
        UiStyles.setTextSize(btnLaunchOnCluster, R.dimen.text_size_small);
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

        card.addView(appButtons, matchWrap());
    }

    private void createPowerModeCard(FeatureGridLayout grid) {
        LinearLayout card = createProjectionCard(grid);
        addCardHeading(card, R.string.projection_start_section, R.string.projection_start_desc);
        final UiStyles.TernarySegmentHandle[] powerHandle = new UiStyles.TernarySegmentHandle[1];
        powerHandle[0] = UiStyles.addTernarySegmentedControl(context, card,
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
    }

    private void createAutoCloseCard(FeatureGridLayout grid) {
        UiStyles.addBinarySegmentedControl(context, grid,
                context.getString(R.string.projection_stop_section),
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
    }

    private void createMapKeyCard(FeatureGridLayout grid) {
        UiStyles.addBinarySegmentedControl(context, grid,
                context.getString(R.string.projection_map_key_title),
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
    }

    private void createGoogleMapsNavCard(FeatureGridLayout grid) {
        final UiStyles.BinarySegmentHandle[] mapsNavHandle = new UiStyles.BinarySegmentHandle[1];
        mapsNavHandle[0] = UiStyles.addBinarySegmentedControl(context, grid,
                context.getString(R.string.projection_google_maps_nav_title),
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
    }

    private void createMapSwipeCard(FeatureGridLayout grid) {
        final UiStyles.BinarySegmentHandle[] mapSwipeHandle = new UiStyles.BinarySegmentHandle[1];
        mapSwipeHandle[0] = UiStyles.addBinarySegmentedControl(context, grid,
                context.getString(R.string.projection_map_swipe_title),
                context.getString(R.string.common_on),
                context.getString(R.string.common_off),
                context.getString(R.string.projection_map_swipe_on_help),
                context.getString(R.string.projection_map_swipe_off_help),
                CarSystemGestureController.isEnabled(context),
                isEnabled -> {
                    if (isEnabled) {
                        int conflictOwner = GestureConflicts.findMapSwipeConflict(context);
                        if (conflictOwner != 0) {
                            mapSwipeHandle[0].syncVisualWithoutCommit(false);
                            prefs.edit().putBoolean(CarSystemGestureController.PREF_ENABLED, false).apply();
                            DialogHelper.showGestureConflict(context, conflictOwner);
                            callback.log("Harita kaydırma: hareket başka özellikte kullanılıyor");
                            return;
                        }
                    }
                    if (isEnabled && !UsageAccessHelper.hasPackageUsageAccess(context)) {
                        UsageAccessHelper.openUsageAccessSettings(context);
                        Toast.makeText(context,
                                R.string.projection_map_swipe_need_access,
                                Toast.LENGTH_LONG).show();
                        mapSwipeHandle[0].syncVisualWithoutCommit(false);
                        prefs.edit().putBoolean(CarSystemGestureController.PREF_ENABLED, false).apply();
                        callback.log("Harita kaydırma: kullanım erişimi kapalı");
                        return;
                    }
                    prefs.edit().putBoolean(CarSystemGestureController.PREF_ENABLED, isEnabled).apply();
                    callback.log(isEnabled
                            ? "Harita kaydırma açıldı"
                            : "Harita kaydırma kapatıldı");
                });
    }

    private void createGoogleMapsClusterCard(FeatureGridLayout grid) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        grid.addCard(card);
        UiStyles.addBinarySegmentedControl(context, card,
                context.getString(R.string.projection_google_maps_cluster_cards_title),
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

        addClusterOverlayLayoutSection(
                card,
                R.string.projection_google_cluster_position_title,
                ClusterNavOverlayOffsets.Provider.GOOGLE,
                () -> GoogleMapsClusterNavOverlay.getInstance(context).applyStoredOffsets());
    }

    private void createYandexClusterCard(FeatureGridLayout grid) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        grid.addCard(card);
        final UiStyles.BinarySegmentHandle[] yandexHandle = new UiStyles.BinarySegmentHandle[1];
        yandexHandle[0] = UiStyles.addBinarySegmentedControl(context, card,
                context.getString(R.string.yandex_cluster_nav_section_title),
                context.getString(R.string.common_on),
                context.getString(R.string.common_off),
                context.getString(R.string.yandex_cluster_nav_help_on),
                context.getString(R.string.yandex_cluster_nav_help_off),
                YandexClusterNavOverlay.isEnabled(context),
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
                        yandexHandle[0].setLeftSelected(false);
                        return;
                    }
                    if (!GlobalBackService.isRegisteredInSystemAccessibilitySettings(context)) {
                        YandexClusterNavCoordinator.openAccessibilitySettings(context);
                        Toast.makeText(context,
                                R.string.yandex_cluster_nav_toast_accessibility,
                                Toast.LENGTH_LONG).show();
                        yandexHandle[0].setLeftSelected(false);
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

        addClusterOverlayLayoutSection(
                card,
                R.string.projection_yandex_cluster_position_title,
                ClusterNavOverlayOffsets.Provider.YANDEX,
                () -> YandexClusterNavOverlay.getInstance(context).applyStoredOffsets());
    }

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

    private void addClusterOverlayLayoutSection(LinearLayout parent, int titleResId,
            ClusterNavOverlayOffsets.Provider provider, Runnable applyOverlay) {
        Button layoutToggle = new Button(context);
        layoutToggle.setText(context.getString(titleResId) + " ↓");
        UiStyles.styleOemButton(layoutToggle, UiStyles.color(context, R.color.surfaceCardInner));
        parent.addView(layoutToggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout expanded = new LinearLayout(context);
        expanded.setOrientation(LinearLayout.VERTICAL);
        expanded.setVisibility(View.GONE);
        parent.addView(expanded);
        layoutToggle.setOnClickListener(v -> {
            boolean show = expanded.getVisibility() != View.VISIBLE;
            expanded.setVisibility(show ? View.VISIBLE : View.GONE);
            layoutToggle.setText(context.getString(titleResId) + (show ? " ↑" : " ↓"));
        });
        parent = expanded;

        TextView desc = new TextView(context);
        desc.setText(R.string.projection_cluster_offset_desc);
        UiStyles.setTextSize(desc, R.dimen.text_size_small);
        desc.setTextColor(UiStyles.color(context, R.color.textHint));
        desc.setPadding(20, 0, 20, 8);
        parent.addView(desc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(20, 0, 20, 8);
        parent.addView(block, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final int offsetRangeXDp = ClusterNavOverlayOffsets.getOffsetRangeXDp(context);
        final int offsetRangeYDp = ClusterNavOverlayOffsets.getOffsetRangeYDp(context);
        final java.util.List<ClusterCardSliderUi> sliderUis = new java.util.ArrayList<>();

        sliderUis.add(addSingleClusterCardLayoutSliders(
                block, provider, ClusterNavOverlayOffsets.Card.MANEUVER,
                R.string.projection_cluster_maneuver_card, offsetRangeXDp, offsetRangeYDp, applyOverlay));
        sliderUis.add(addSingleClusterCardLayoutSliders(
                block, provider, ClusterNavOverlayOffsets.Card.ETA,
                R.string.projection_cluster_eta_card, offsetRangeXDp, offsetRangeYDp, applyOverlay));

        Button btnResetLayout = new Button(context);
        btnResetLayout.setText(R.string.projection_cluster_layout_reset);
        btnResetLayout.setTextColor(UiStyles.color(context, R.color.textPrimary80));
        UiStyles.setTextSize(btnResetLayout, R.dimen.text_size_small);
        UiStyles.styleOemButton(btnResetLayout, UiStyles.color(context, R.color.surfaceCardInner));
        btnResetLayout.setPadding(16, 14, 16, 14);
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        resetLp.topMargin = 8;
        block.addView(btnResetLayout, resetLp);
        btnResetLayout.setOnClickListener(v -> {
            ClusterNavOverlayOffsets.resetProvider(context, provider);
            for (ClusterCardSliderUi ui : sliderUis) {
                ui.syncFromPrefs();
            }
            applyOverlay.run();
            callback.log(provider == ClusterNavOverlayOffsets.Provider.GOOGLE
                    ? "Google Maps cluster kart düzeni sıfırlandı"
                    : "Yandex cluster kart düzeni sıfırlandı");
            Toast.makeText(context, R.string.projection_cluster_layout_reset_done, Toast.LENGTH_SHORT).show();
        });
    }

    private static final class ClusterCardSliderUi {
        final ClusterNavOverlayOffsets.Provider provider;
        final ClusterNavOverlayOffsets.Card card;
        final int offsetRangeXDp;
        final int offsetRangeYDp;
        final ClusterNavOverlayOffsets.CardLayout layout;
        final SeekBar xBar;
        final SeekBar yBar;
        final SeekBar scaleBar;
        final TextView xValue;
        final TextView yValue;
        final TextView scaleValue;
        final ProjectionTabBuilder owner;

        ClusterCardSliderUi(ProjectionTabBuilder owner,
                ClusterNavOverlayOffsets.Provider provider,
                ClusterNavOverlayOffsets.Card card,
                int offsetRangeXDp,
                int offsetRangeYDp,
                ClusterNavOverlayOffsets.CardLayout layout,
                SeekBar xBar, SeekBar yBar, SeekBar scaleBar,
                TextView xValue, TextView yValue, TextView scaleValue) {
            this.owner = owner;
            this.provider = provider;
            this.card = card;
            this.offsetRangeXDp = offsetRangeXDp;
            this.offsetRangeYDp = offsetRangeYDp;
            this.layout = layout;
            this.xBar = xBar;
            this.yBar = yBar;
            this.scaleBar = scaleBar;
            this.xValue = xValue;
            this.yValue = yValue;
            this.scaleValue = scaleValue;
        }

        void syncFromPrefs() {
            ClusterNavOverlayOffsets.CardLayout fresh =
                    ClusterNavOverlayOffsets.getCardLayout(owner.context, provider, card);
            layout.offsetXDp = fresh.offsetXDp;
            layout.offsetYDp = fresh.offsetYDp;
            layout.scalePercent = fresh.scalePercent;
            xBar.setProgress(ClusterNavOverlayOffsets.toOffsetSeekProgress(
                    layout.offsetXDp, offsetRangeXDp));
            yBar.setProgress(ClusterNavOverlayOffsets.toOffsetSeekProgress(
                    layout.offsetYDp, offsetRangeYDp));
            scaleBar.setProgress(ClusterNavOverlayOffsets.toScaleSeekProgress(layout.scalePercent));
            xValue.setText(owner.context.getString(
                    R.string.projection_cluster_offset_value, layout.offsetXDp));
            yValue.setText(owner.context.getString(
                    R.string.projection_cluster_offset_value, layout.offsetYDp));
            scaleValue.setText(owner.context.getString(
                    R.string.projection_cluster_offset_scale_value, layout.scalePercent));
        }
    }

    private ClusterCardSliderUi addSingleClusterCardLayoutSliders(LinearLayout parent,
            ClusterNavOverlayOffsets.Provider provider,
            ClusterNavOverlayOffsets.Card card,
            int cardTitleResId,
            int offsetRangeXDp,
            int offsetRangeYDp,
            Runnable applyOverlay) {
        TextView cardTitle = new TextView(context);
        cardTitle.setText(cardTitleResId);
        UiStyles.setTextSize(cardTitle, R.dimen.text_size_small);
        cardTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        cardTitle.setTypeface(null, Typeface.BOLD);
        cardTitle.setPadding(0, 12, 0, 4);
        parent.addView(cardTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final ClusterNavOverlayOffsets.CardLayout layout =
                ClusterNavOverlayOffsets.getCardLayout(context, provider, card);

        Runnable persistAndApply = () -> {
            ClusterNavOverlayOffsets.setCardLayout(context, provider, card, layout);
            applyOverlay.run();
        };

        ClusterOffsetSliderRow xRow = addClusterOffsetSliderRow(
                parent,
                R.string.projection_cluster_offset_x,
                layout.offsetXDp,
                offsetRangeXDp,
                value -> {
                    layout.offsetXDp = value;
                    persistAndApply.run();
                });
        ClusterOffsetSliderRow yRow = addClusterOffsetSliderRow(
                parent,
                R.string.projection_cluster_offset_y,
                layout.offsetYDp,
                offsetRangeYDp,
                value -> {
                    layout.offsetYDp = value;
                    persistAndApply.run();
                });
        ClusterOffsetSliderRow scaleRow = addClusterScaleSliderRow(
                parent,
                layout.scalePercent,
                value -> {
                    layout.scalePercent = value;
                    persistAndApply.run();
                });
        return new ClusterCardSliderUi(this, provider, card, offsetRangeXDp, offsetRangeYDp, layout,
                xRow.bar, yRow.bar, scaleRow.bar,
                xRow.value, yRow.value, scaleRow.value);
    }

    private static final class ClusterOffsetSliderRow {
        final SeekBar bar;
        final TextView value;

        ClusterOffsetSliderRow(SeekBar bar, TextView value) {
            this.bar = bar;
            this.value = value;
        }
    }

    private ClusterOffsetSliderRow addClusterOffsetSliderRow(LinearLayout parent, int labelResId,
            int initialOffsetDp, int offsetRangeDp, java.util.function.IntConsumer onChanged) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 8, 0, 0);

        TextView label = new TextView(context);
        label.setText(labelResId);
        UiStyles.setTextSize(label, R.dimen.text_size_small);
        label.setTextColor(UiStyles.color(context, R.color.textSecondary));
        row.addView(label, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(context);
        UiStyles.setTextSize(value, R.dimen.text_size_small);
        value.setTextColor(UiStyles.color(context, R.color.textHint));
        value.setGravity(Gravity.END);
        row.addView(value, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        value.setText(context.getString(R.string.projection_cluster_offset_value, initialOffsetDp));

        SeekBar bar = createClusterSeekBar(ClusterNavOverlayOffsets.offsetSeekMax(offsetRangeDp));
        bar.setProgress(ClusterNavOverlayOffsets.toOffsetSeekProgress(initialOffsetDp, offsetRangeDp));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int offset = ClusterNavOverlayOffsets.fromOffsetSeekProgress(progress, offsetRangeDp);
                value.setText(context.getString(R.string.projection_cluster_offset_value, offset));
                if (fromUser) {
                    onChanged.accept(offset);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        parent.addView(bar, clusterOffsetSeekBarLayoutParams());
        return new ClusterOffsetSliderRow(bar, value);
    }

    private ClusterOffsetSliderRow addClusterScaleSliderRow(LinearLayout parent, int initialScalePercent,
            java.util.function.IntConsumer onChanged) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 8, 0, 0);

        TextView label = new TextView(context);
        label.setText(R.string.projection_cluster_offset_scale);
        UiStyles.setTextSize(label, R.dimen.text_size_small);
        label.setTextColor(UiStyles.color(context, R.color.textSecondary));
        row.addView(label, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(context);
        UiStyles.setTextSize(value, R.dimen.text_size_small);
        value.setTextColor(UiStyles.color(context, R.color.textHint));
        value.setGravity(Gravity.END);
        value.setText(context.getString(
                R.string.projection_cluster_offset_scale_value, initialScalePercent));
        row.addView(value, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        SeekBar bar = createClusterSeekBar(ClusterNavOverlayOffsets.scaleSeekMax());
        bar.setProgress(ClusterNavOverlayOffsets.toScaleSeekProgress(initialScalePercent));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int scale = ClusterNavOverlayOffsets.fromScaleSeekProgress(progress);
                value.setText(context.getString(R.string.projection_cluster_offset_scale_value, scale));
                if (fromUser) {
                    onChanged.accept(scale);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        parent.addView(bar, clusterOffsetSeekBarLayoutParams());
        return new ClusterOffsetSliderRow(bar, value);
    }

    private SeekBar createClusterSeekBar(int max) {
        SeekBar bar = new SeekBar(context);
        bar.setMax(max);
        int accent = UiStyles.color(context, R.color.oemAccent);
        int thumbPx = UiStyles.dimenPx(context, R.dimen.equalizer_thumb_size);
        int trackPx = UiStyles.dimenPx(context, R.dimen.equalizer_track_height);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(accent);
        thumb.setSize(thumbPx, thumbPx);
        bar.setThumb(thumb);
        bar.setThumbOffset(thumbPx / 2);
        bar.setSplitTrack(false);
        bar.setMinHeight(trackPx);
        bar.setMaxHeight(trackPx);
        bar.setProgressTintList(ColorStateList.valueOf(accent));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(
                UiStyles.color(context, R.color.buttonInactive)));
        bar.setThumbTintList(ColorStateList.valueOf(accent));
        int side = thumbPx / 2;
        bar.setPadding(side, 0, side, 0);
        return bar;
    }

    private LinearLayout.LayoutParams clusterOffsetSeekBarLayoutParams() {
        int h = UiStyles.dimenPx(context, R.dimen.spacing_small);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiStyles.dimenPx(context, R.dimen.equalizer_slider_height));
        lp.setMargins(0, 0, 0, h);
        return lp;
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
