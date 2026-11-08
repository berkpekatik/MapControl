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
import com.mapcontrol.launcher.SahneFonts;
import com.mapcontrol.nav.ClusterNavOverlayOffsets;
import com.mapcontrol.nav.ClusterNavStylePreview;
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
    private static final int CLUSTER_STYLE_PREVIEW_HEIGHT_DP = 104;
    private static final int HERO_WIDE_MIN_DP = 1000;
    private static final int HERO_ACTIONS_WIDTH_DP = 440;

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
    private View projectionStatusDot;
    private Button heroAction;
    private boolean heroActionBusy;
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

        createClusterControlCard(projectionTabContent);

        FeatureGridLayout grid = new FeatureGridLayout(context);
        projectionTabContent.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        createPowerModeCard(grid);
        addStackedToggleCards(grid);
        createGoogleMapsNavCard(grid);
        createMapSwipeCard(grid);
        createGoogleMapsClusterCard(grid);
        createYandexClusterCard(grid);

        handler.post(this::refreshProjectionStatusUi);

        return scrollView;
    }

    /** Tek satırlık iki anahtar, yüksek "Başlatma" kartının yanında üst üste durur; böylece kartlar boş uzamaz. */
    private void addStackedToggleCards(FeatureGridLayout grid) {
        LinearLayout stack = new LinearLayout(context);
        stack.setOrientation(LinearLayout.VERTICAL);
        createAutoCloseCard(stack);
        createMapKeyCard(stack);
        int gap = Math.round(12 * context.getResources().getDisplayMetrics().density);
        for (int i = 0; i < stack.getChildCount(); i++) {
            LinearLayout.LayoutParams lp =
                    (LinearLayout.LayoutParams) stack.getChildAt(i).getLayoutParams();
            lp.height = 0;
            lp.weight = 1f;
            lp.topMargin = i == 0 ? 0 : gap;
            lp.bottomMargin = 0;
        }
        grid.addCard(stack);
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
        UiStyles.setFontStyle(title, Typeface.BOLD);
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

    /** Sayfanın odak noktası: kadran görseli, büyük yansıtma durumu, hedef uygulama ve tek ana eylem. */
    private void createClusterControlCard(LinearLayout parent) {
        float density = context.getResources().getDisplayMetrics().density;
        boolean wide = context.getResources().getConfiguration().screenWidthDp >= HERO_WIDE_MIN_DP;

        LinearLayout hero = new LinearLayout(context);
        hero.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        hero.setGravity(wide ? Gravity.CENTER_VERTICAL : Gravity.START);
        int padH = Math.round(28 * density);
        int padV = Math.round(24 * density);
        hero.setPadding(padH, padV, padH, padV);
        UiStyles.setGlassCardBackground(hero);
        LinearLayout.LayoutParams heroLp = matchWrap();
        // FeatureGridLayout kartlarının 6 dp dış boşluğuyla hizalı.
        int heroEdge = Math.round(6 * density);
        heroLp.setMargins(heroEdge, 0, heroEdge, heroEdge);
        parent.addView(hero, heroLp);

        if (wide) {
            ImageView art = new ImageView(context);
            art.setImageResource(R.drawable.launcher_art_cluster_mirror);
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(
                    Math.round(210 * density), Math.round(112 * density));
            artLp.setMarginEnd(Math.round(28 * density));
            hero.addView(art, artLp);
        }

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);

        LinearLayout statusRow = new LinearLayout(context);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        projectionStatusDot = new View(context);
        int dotPx = Math.round(12 * density);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotPx, dotPx);
        dotLp.setMarginEnd(Math.round(14 * density));
        statusRow.addView(projectionStatusDot, dotLp);

        projectionStatusText = new TextView(context);
        projectionStatusText.setText(R.string.projection_status_off);
        projectionStatusText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 40f);
        projectionStatusText.setTypeface(SahneFonts.INSTANCE.get(context, 300));
        projectionStatusText.setSingleLine(true);
        projectionStatusText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        projectionStatusText.setTextColor(UiStyles.color(context, R.color.textPrimary));
        statusRow.addView(projectionStatusText, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        info.addView(statusRow, matchWrap());

        LinearLayout targetRow = new LinearLayout(context);
        targetRow.setOrientation(LinearLayout.HORIZONTAL);
        targetRow.setGravity(Gravity.CENTER_VERTICAL);
        targetRow.setPadding(0, Math.round(8 * density), 0, 0);
        AppCompatImageView mapIcon = new AppCompatImageView(context);
        mapIcon.setImageResource(R.drawable.ic_mdi_map);
        mapIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mapIcon.setImageTintList(ColorStateList.valueOf(UiStyles.color(context, R.color.textHint)));
        int mapIconPx = Math.round(22 * density);
        LinearLayout.LayoutParams mapIconLp = new LinearLayout.LayoutParams(mapIconPx, mapIconPx);
        mapIconLp.setMarginEnd(Math.round(10 * density));
        targetRow.addView(mapIcon, mapIconLp);

        targetAppLabel = new TextView(context);
        targetAppLabel.setText(R.string.common_not_selected);
        targetAppLabel.setTextColor(UiStyles.color(context, R.color.textHint));
        UiStyles.setTextSize(targetAppLabel, R.dimen.text_size_small);
        targetAppLabel.setSingleLine(true);
        targetAppLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        targetRow.addView(targetAppLabel, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        info.addView(targetRow, matchWrap());

        hero.addView(info, wide
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                : matchWrap());

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams actionsLp = wide
                ? new LinearLayout.LayoutParams(Math.round(HERO_ACTIONS_WIDTH_DP * density),
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                : matchWrap();
        if (wide) {
            actionsLp.setMarginStart(Math.round(28 * density));
        } else {
            actionsLp.topMargin = Math.round(16 * density);
        }
        hero.addView(card, actionsLp);

        heroAction = new Button(context);
        card.addView(heroAction, matchWrap());
        applyHeroAction(ClusterNavigationState.getLastKnownOpen());
        heroAction.setOnClickListener(v -> {
            boolean open = ClusterNavigationState.getLastKnownOpen();
            heroActionBusy = true;
            heroAction.setEnabled(false);
            heroAction.setAlpha(0.6f);
            heroAction.setText(open ? R.string.projection_stopping : R.string.projection_projecting);
            if (open) {
                callback.onCloseCluster();
            } else {
                callback.onOpenCluster();
            }
            handler.postDelayed(() -> {
                heroActionBusy = false;
                heroAction.setEnabled(true);
                heroAction.setAlpha(1f);
                refreshProjectionStatusUi();
            }, 2500);
        });

        LinearLayout appButtons = new LinearLayout(context);
        appButtons.setOrientation(LinearLayout.HORIZONTAL);
        appButtons.setPadding(0, Math.round(10 * density), 0, 0);

        Button btnSelectApp = new Button(context);
        btnSelectApp.setText(R.string.projection_change_app);
        UiStyles.styleOemButton(btnSelectApp, UiStyles.color(context, R.color.surfaceCardInner));
        UiStyles.setTextSize(btnSelectApp, R.dimen.text_size_small);
        LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        selectParams.setMarginEnd(Math.round(5 * density));
        appButtons.addView(btnSelectApp, selectParams);
        btnSelectApp.setOnClickListener(v -> selectTargetApp());

        Button btnLaunchOnCluster = new Button(context);
        btnLaunchOnCluster.setText(R.string.projection_launch_cluster);
        UiStyles.styleOemButton(btnLaunchOnCluster, UiStyles.color(context, R.color.surfaceCardInner));
        UiStyles.setTextSize(btnLaunchOnCluster, R.dimen.text_size_small);
        LinearLayout.LayoutParams launchParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        launchParams.setMarginStart(Math.round(5 * density));
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

    private void createAutoCloseCard(android.view.ViewGroup grid) {
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

    private void createMapKeyCard(android.view.ViewGroup grid) {
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

    private void applyHeroAction(boolean open) {
        heroAction.setText(open ? R.string.projection_stop : R.string.projection_open);
        UiStyles.styleOemButton(heroAction,
                UiStyles.color(context, open ? R.color.buttonCta : R.color.buttonPrimary));
        heroAction.setMinHeight(UiStyles.dimenPx(context, R.dimen.button_height) + Math.round(
                16 * context.getResources().getDisplayMetrics().density));
    }

    public void refreshProjectionStatusUi() {
        if (projectionStatusText == null) {
            return;
        }
        boolean open = ClusterNavigationState.getLastKnownOpen();
        projectionStatusText.setText(open
                ? R.string.projection_status_on
                : R.string.projection_status_off);
        if (projectionStatusDot != null) {
            android.graphics.drawable.GradientDrawable dot =
                    new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(UiStyles.color(context, open ? R.color.statusSuccess : R.color.switchTrackOff));
            projectionStatusDot.setBackground(dot);
        }
        if (heroAction != null && !heroActionBusy) {
            applyHeroAction(open);
        }
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

    /** Kart tasarımı seçici: her seçenek gerçek kartın küçültülmüş önizlemesi ve adıyla bir kutu. */
    private void addClusterStylePicker(LinearLayout parent,
            ClusterNavOverlayOffsets.Provider provider, Runnable onChanged) {
        final ClusterNavOverlayOffsets.Style[] styles = {
                ClusterNavOverlayOffsets.Style.DETAIL,
                ClusterNavOverlayOffsets.Style.SLIM,
                ClusterNavOverlayOffsets.Style.GLASS};
        final int[] names = {
                R.string.projection_cluster_style_detail,
                R.string.projection_cluster_style_slim,
                R.string.projection_cluster_style_glass};
        final int[] helps = {
                R.string.projection_cluster_style_detail_help,
                R.string.projection_cluster_style_slim_help,
                R.string.projection_cluster_style_glass_help};
        float density = context.getResources().getDisplayMetrics().density;
        int gap = Math.round(8f * density);

        TextView heading = new TextView(context);
        heading.setText(R.string.projection_cluster_style_title);
        UiStyles.setTextSize(heading, R.dimen.text_size_medium);
        UiStyles.setFontStyle(heading, android.graphics.Typeface.BOLD);
        heading.setTextColor(UiStyles.color(context, R.color.textPrimary));
        heading.setPadding(0, gap, 0, gap);
        parent.addView(heading);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView help = new TextView(context);
        UiStyles.setTextSize(help, R.dimen.text_size_small);
        help.setTextColor(UiStyles.color(context, R.color.textHint));
        help.setPadding(0, gap, 0, gap);
        parent.addView(help);

        final LinearLayout[] tiles = new LinearLayout[styles.length];
        final TextView[] labels = new TextView[styles.length];
        final Runnable refresh = () -> {
            ClusterNavOverlayOffsets.Style current =
                    ClusterNavOverlayOffsets.getStyle(context, provider);
            for (int i = 0; i < styles.length; i++) {
                boolean selected = styles[i] == current;
                android.graphics.drawable.GradientDrawable bg =
                        new android.graphics.drawable.GradientDrawable();
                bg.setCornerRadius(16f * density);
                bg.setColor(UiStyles.color(context,
                        selected ? R.color.assistCardActive : R.color.assistCardIdle));
                bg.setStroke(Math.round((selected ? 2f : 1f) * density), UiStyles.color(context,
                        selected ? R.color.assistStrokeActive : R.color.outline));
                tiles[i].setBackground(bg);
                tiles[i].setSelected(selected);
                labels[i].setTextColor(UiStyles.color(context,
                        selected ? R.color.textPrimary : R.color.textSecondary));
                UiStyles.setFontStyle(labels[i], selected
                        ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                if (selected) {
                    help.setText(helps[i]);
                }
            }
        };

        int pad = Math.round(10f * density);
        for (int i = 0; i < styles.length; i++) {
            final ClusterNavOverlayOffsets.Style style = styles[i];
            LinearLayout tile = new LinearLayout(context);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setPadding(pad, pad, pad, pad);
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setContentDescription(context.getString(names[i]));

            tile.addView(new ClusterNavStylePreview(context, style),
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            Math.round(CLUSTER_STYLE_PREVIEW_HEIGHT_DP * density)));

            TextView label = new TextView(context);
            label.setText(names[i]);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            UiStyles.setTextSize(label, R.dimen.text_size_small);
            label.setPadding(0, gap, 0, 0);
            tile.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            tile.setOnClickListener(v -> {
                if (ClusterNavOverlayOffsets.getStyle(context, provider) == style) {
                    return;
                }
                ClusterNavOverlayOffsets.setStyle(context, provider, style);
                refresh.run();
                onChanged.run();
            });
            tiles[i] = tile;
            labels[i] = label;

            LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                tileLp.setMarginStart(gap);
            }
            row.addView(tile, tileLp);
        }
        refresh.run();
    }

    private void addClusterOverlayLayoutSection(LinearLayout parent, int titleResId,
            ClusterNavOverlayOffsets.Provider provider, Runnable applyOverlay) {
        final java.util.List<ClusterCardSliderUi> sliderUis = new java.util.ArrayList<>();
        addClusterStylePicker(parent, provider, () -> {
            if (provider == ClusterNavOverlayOffsets.Provider.GOOGLE) {
                GoogleMapsClusterNavOverlay.getInstance(context).restyle();
            } else {
                YandexClusterNavOverlay.getInstance(context).restyle();
            }
            // Kart boyutu değişti: kaydırıcı sınırlarını güncelle.
            for (ClusterCardSliderUi ui : sliderUis) {
                ui.syncFromPrefs();
            }
            applyOverlay.run();
        });
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

        sliderUis.add(addSingleClusterCardLayoutSliders(
                block, provider, ClusterNavOverlayOffsets.Card.NAV,
                R.string.projection_cluster_nav_card, applyOverlay, sliderUis));

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
        final ClusterNavOverlayOffsets.CardLayout layout;
        final ClusterOffsetSliderRow xRow;
        final ClusterOffsetSliderRow yRow;
        final SeekBar scaleBar;
        final TextView scaleValue;
        final ProjectionTabBuilder owner;

        ClusterCardSliderUi(ProjectionTabBuilder owner,
                ClusterNavOverlayOffsets.Provider provider,
                ClusterNavOverlayOffsets.Card card,
                ClusterNavOverlayOffsets.CardLayout layout,
                ClusterOffsetSliderRow xRow, ClusterOffsetSliderRow yRow,
                SeekBar scaleBar, TextView scaleValue) {
            this.owner = owner;
            this.provider = provider;
            this.card = card;
            this.layout = layout;
            this.xRow = xRow;
            this.yRow = yRow;
            this.scaleBar = scaleBar;
            this.scaleValue = scaleValue;
        }

        /** Kayıtlı değerleri okur; tasarım/boyut değiştiyse kaydırıcı sınırlarını yeniler. */
        void syncFromPrefs() {
            ClusterNavOverlayOffsets.CardLayout fresh =
                    ClusterNavOverlayOffsets.getCardLayout(owner.context, provider, card);
            layout.offsetXDp = fresh.offsetXDp;
            layout.offsetYDp = fresh.offsetYDp;
            layout.scalePercent = fresh.scalePercent;
            ClusterNavOverlayOffsets.Bounds b = ClusterNavOverlayOffsets.getBounds(
                    owner.context, provider, layout.scalePercent);
            xRow.setRange(b.minX, b.maxX, layout.offsetXDp, owner.context);
            yRow.setRange(b.minY, b.maxY, layout.offsetYDp, owner.context);
            scaleBar.setProgress(ClusterNavOverlayOffsets.toScaleSeekProgress(layout.scalePercent));
            scaleValue.setText(owner.context.getString(
                    R.string.projection_cluster_offset_scale_value, layout.scalePercent));
        }
    }

    private ClusterCardSliderUi addSingleClusterCardLayoutSliders(LinearLayout parent,
            ClusterNavOverlayOffsets.Provider provider,
            ClusterNavOverlayOffsets.Card card,
            int cardTitleResId,
            Runnable applyOverlay,
            java.util.List<ClusterCardSliderUi> allUis) {
        TextView cardTitle = new TextView(context);
        cardTitle.setText(cardTitleResId);
        UiStyles.setTextSize(cardTitle, R.dimen.text_size_small);
        cardTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.setFontStyle(cardTitle, Typeface.BOLD);
        cardTitle.setPadding(0, 12, 0, 4);
        parent.addView(cardTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final ClusterNavOverlayOffsets.CardLayout layout =
                ClusterNavOverlayOffsets.getCardLayout(context, provider, card);
        final ClusterNavOverlayOffsets.Bounds bounds =
                ClusterNavOverlayOffsets.getBounds(context, provider, layout.scalePercent);

        Runnable persistAndApply = () -> {
            ClusterNavOverlayOffsets.setCardLayout(context, provider, card, layout);
            applyOverlay.run();
        };

        ClusterOffsetSliderRow xRow = addClusterOffsetSliderRow(
                parent,
                R.string.projection_cluster_offset_x,
                layout.offsetXDp,
                bounds.minX, bounds.maxX,
                value -> {
                    layout.offsetXDp = value;
                    persistAndApply.run();
                });
        ClusterOffsetSliderRow yRow = addClusterOffsetSliderRow(
                parent,
                R.string.projection_cluster_offset_y,
                layout.offsetYDp,
                bounds.minY, bounds.maxY,
                value -> {
                    layout.offsetYDp = value;
                    persistAndApply.run();
                });
        final ClusterCardSliderUi[] holder = new ClusterCardSliderUi[1];
        ClusterOffsetSliderRow scaleRow = addClusterScaleSliderRow(
                parent,
                layout.scalePercent,
                value -> {
                    layout.scalePercent = value;
                    persistAndApply.run();
                    // Ölçek değişince ekranda kalabilecek aralık da değişir.
                    if (holder[0] != null) {
                        holder[0].syncFromPrefs();
                    }
                });
        holder[0] = new ClusterCardSliderUi(this, provider, card, layout,
                xRow, yRow, scaleRow.bar, scaleRow.value);
        return holder[0];
    }

    private static final class ClusterOffsetSliderRow {
        final SeekBar bar;
        final TextView value;
        /** {min, max} dp; kaydırıcı dinleyicisi güncel aralığı buradan okur. */
        final int[] range;

        ClusterOffsetSliderRow(SeekBar bar, TextView value) {
            this(bar, value, new int[]{0, 0});
        }

        ClusterOffsetSliderRow(SeekBar bar, TextView value, int[] range) {
            this.bar = bar;
            this.value = value;
            this.range = range;
        }

        void setRange(int min, int max, int offsetDp, Context context) {
            range[0] = min;
            range[1] = max;
            bar.setMax(ClusterNavOverlayOffsets.offsetSeekMax(min, max));
            int clamped = ClusterNavOverlayOffsets.clampOffset(offsetDp, min, max);
            bar.setProgress(ClusterNavOverlayOffsets.toOffsetSeekProgress(clamped, min, max));
            value.setText(context.getString(R.string.projection_cluster_offset_value, clamped));
        }
    }

    private ClusterOffsetSliderRow addClusterOffsetSliderRow(LinearLayout parent, int labelResId,
            int initialOffsetDp, int minDp, int maxDp, java.util.function.IntConsumer onChanged) {
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

        final int[] range = new int[]{minDp, maxDp};
        SeekBar bar = createClusterSeekBar(ClusterNavOverlayOffsets.offsetSeekMax(minDp, maxDp));
        bar.setProgress(ClusterNavOverlayOffsets.toOffsetSeekProgress(initialOffsetDp, minDp, maxDp));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int offset = ClusterNavOverlayOffsets.fromOffsetSeekProgress(progress, range[0], range[1]);
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
        return new ClusterOffsetSliderRow(bar, value, range);
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
