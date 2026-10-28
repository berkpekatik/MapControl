package com.mapcontrol.ui.builder;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.text.TextUtils;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.ui.widget.StatusWidgetsView;
import com.mapcontrol.util.DialogHelper;

public class SideRailBuilder {
    public interface SideRailCallback {
        void onTabSelected(int tabIndex, String title);
        void onAppManagementRequested();
        void log(String message);
        default void onHomeRequested() {}
        default void onBackPressedRequested() {}
    }

    private static final int TAB_WIFI = 0;
    private static final int TAB_FILE = 1;
    private static final int TAB_PROFILE = 2;
    private static final int TAB_PROJECTION = 3;
    private static final int TAB_APPS = 5;
    private static final int TAB_DRIVE_MODE = 6;
    private static final int TAB_SETTINGS = 7;
    private static final int TAB_WELCOME_SOUND = 8;
    private static final int TAB_VEHICLE_INFO = 9;
    private static final int TAB_LAUNCHER = 10;
    private static final int TAB_EQUALIZER = 11;
    private static final int TAB_CLIMATE_AUTOMATION = 12;
    private static final int TAB_FACE_RECOGNITION = 13;
    private static final int TAB_CAMERA_360 = 14;
    private static final int TAB_SPLIT_SCREEN = 15;

    private final Context context;
    private final SharedPreferences prefs;
    private final SideRailCallback callback;

    private LinearLayout menuApps;
    private LinearLayout menuWifi;
    private LinearLayout menuFileUpload;
    private LinearLayout menuSettings;
    private LinearLayout menuProjection;
    private LinearLayout menuDriveMode;
    private LinearLayout menuTest;
    private LinearLayout menuCamera360;
    private LinearLayout menuWelcomeSound;
    private LinearLayout menuEqualizer;
    private LinearLayout menuClimateAutomation;
    private LinearLayout menuFaceRecognition;
    private LinearLayout menuProfile;
    private LinearLayout menuVehicleInfo;
    private LinearLayout menuSplitScreen;
    private LinearLayout sideRail;
    private View statusWidgets;
    private LinearLayout selectedMenuRow;
    private boolean launcherModeActive;
    private boolean contentWide;
    private View btnHome;
    private AppCompatImageView btnHomeIcon;
    private TextView btnHomeLabel;

    public SideRailBuilder(Context context, SharedPreferences prefs, SideRailCallback callback) {
        this.context = context;
        this.prefs = prefs;
        this.callback = callback;
    }

    public void setStatusWidgets(View widgets) { statusWidgets = widgets; }

    private void attachStatusWidgets() {
        if (statusWidgets == null) return;
        if (statusWidgets instanceof StatusWidgetsView) {
            ((StatusWidgetsView) statusWidgets).setSingleRow(true);
        }
        if (statusWidgets.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) statusWidgets.getParent()).removeView(statusWidgets);
        }
        int pad = Math.round(12 * context.getResources().getDisplayMetrics().density);
        statusWidgets.setPadding(pad, pad, pad, pad);
        sideRail.addView(statusWidgets, new LinearLayout.LayoutParams(-1, -2));
    }

    public LinearLayout build() {
        sideRail = new LinearLayout(context);
        sideRail.setOrientation(LinearLayout.VERTICAL);
        UiStyles.setRailPanelBackground(sideRail);
        sideRail.setPadding(0, 0, 0, 0);
        // Status widgets TopBar içine taşındı

        ScrollView menuScrollView = new ScrollView(context);
        menuScrollView.setBackgroundColor(Color.TRANSPARENT);
        menuScrollView.setFillViewport(false);

        LinearLayout menuContainer = new LinearLayout(context);
        menuContainer.setOrientation(LinearLayout.VERTICAL);
        menuContainer.setBackgroundColor(Color.TRANSPARENT);
        railMenuContainer = menuContainer;

        menuWifi = createRailMenuItemView(R.drawable.ic_mdi_wifi, t(R.string.side_rail_wifi));
        menuApps = createRailMenuItemView(R.drawable.ic_mdi_cellphone, t(R.string.side_rail_apps));
        menuFileUpload = createRailMenuItemView(R.drawable.ic_mdi_web, t(R.string.side_rail_web));
        // Temporarily hidden: Profile rail item (uncomment addView and onClick below to restore).
        menuProfile = createRailMenuItemView(R.drawable.ic_mdi_account, t(R.string.side_rail_profile));
        menuDriveMode = createRailMenuItemView(R.drawable.ic_mdi_car, t(R.string.side_rail_drive_mode));
        menuWelcomeSound = createRailMenuItemView(R.drawable.ic_mdi_volume_high,
                t(R.string.side_rail_welcome_sound));
        menuEqualizer = createRailMenuItemView(R.drawable.ic_mdi_equalizer,
                t(R.string.side_rail_equalizer));
        menuClimateAutomation = createRailMenuItemView(R.drawable.ic_mdi_thermometer,
                t(R.string.side_rail_climate_automation));
        menuFaceRecognition = createRailMenuItemView(R.drawable.ic_mdi_account,
                t(R.string.side_rail_face_recognition));
        menuTest = createRailMenuItemView(R.drawable.ic_mdi_camera, t(R.string.side_rail_camera));
        menuCamera360 = createRailMenuItemView(R.drawable.ic_mdi_camera, t(R.string.camera360_title));
        menuProjection = createRailMenuItemView(R.drawable.ic_mdi_map, t(R.string.side_rail_projection));
        menuVehicleInfo = createRailMenuItemView(R.drawable.ic_mdi_speedometer,
                t(R.string.side_rail_vehicle_info));
        menuSplitScreen = createRailMenuItemView(R.drawable.ic_mdi_view_split,
                t(R.string.side_rail_split_screen));
        menuSettings = createRailMenuItemView(R.drawable.ic_mdi_cog, t(R.string.side_rail_settings));

        menuContainer.addView(menuWifi);
        menuContainer.addView(menuApps);
        menuContainer.addView(menuFileUpload);
        // menuContainer.addView(menuProfile); // Temporarily hidden — see comment above menuProfile creation.
        menuContainer.addView(menuDriveMode);
        menuContainer.addView(menuWelcomeSound);
        menuContainer.addView(menuEqualizer);
        menuContainer.addView(menuClimateAutomation);
        menuContainer.addView(menuFaceRecognition);
        menuTest.setVisibility(View.GONE);
        menuContainer.addView(menuTest);
        menuContainer.addView(menuCamera360);
        menuContainer.addView(menuSplitScreen);
        menuContainer.addView(menuProjection);
        menuContainer.addView(menuVehicleInfo);
        menuContainer.addView(menuSettings);

        float density = context.getResources().getDisplayMetrics().density;
        updateRailBottomInset();
        menuScrollView.addView(menuContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams menuScrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f);
        sideRail.addView(menuScrollView, menuScrollParams);

        // Zone 1: Kalıcı Altın Dock (MainActivity root FrameLayout katmanında sabit tutulur)
        buildGoldenDock();

        updateMenuSelection(menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        bindMenuClicks();
        return sideRail;
    }

    /**
     * Dikey ekran: sol kolon yok. Aynı sekmeler üstte yatay kaydırılan ikon + kısa etiket şeridi.
     */
    public LinearLayout buildPortrait() {
        railMenuContainer = null;
        sideRail = new LinearLayout(context);
        sideRail.setOrientation(LinearLayout.VERTICAL);
        UiStyles.setRailPanelBackground(sideRail);
        // Status widgets TopBar içine taşındı

        HorizontalScrollView menuScroll = new HorizontalScrollView(context);
        menuScroll.setHorizontalScrollBarEnabled(false);
        menuScroll.setFillViewport(true);

        LinearLayout menuContainer = new LinearLayout(context);
        menuContainer.setOrientation(LinearLayout.HORIZONTAL);
        menuContainer.setGravity(Gravity.CENTER_VERTICAL);

        menuWifi = createPortraitRailItem(R.drawable.ic_mdi_wifi, t(R.string.side_rail_wifi));
        menuApps = createPortraitRailItem(R.drawable.ic_mdi_cellphone, t(R.string.side_rail_apps));
        menuFileUpload = createPortraitRailItem(R.drawable.ic_mdi_web, t(R.string.side_rail_web));
        menuProfile = createPortraitRailItem(R.drawable.ic_mdi_account, t(R.string.side_rail_profile));
        menuDriveMode = createPortraitRailItem(R.drawable.ic_mdi_car, t(R.string.side_rail_drive_mode));
        menuWelcomeSound = createPortraitRailItem(R.drawable.ic_mdi_volume_high,
                t(R.string.side_rail_welcome_sound));
        menuEqualizer = createPortraitRailItem(R.drawable.ic_mdi_equalizer,
                t(R.string.side_rail_equalizer));
        menuClimateAutomation = createPortraitRailItem(R.drawable.ic_mdi_thermometer,
                t(R.string.side_rail_climate_automation));
        menuFaceRecognition = createPortraitRailItem(R.drawable.ic_mdi_account,
                t(R.string.side_rail_face_recognition));
        menuTest = createPortraitRailItem(R.drawable.ic_mdi_camera, t(R.string.side_rail_camera));
        menuCamera360 = createPortraitRailItem(R.drawable.ic_mdi_camera, t(R.string.camera360_title));
        menuProjection = createPortraitRailItem(R.drawable.ic_mdi_map, t(R.string.side_rail_projection));
        menuVehicleInfo = createPortraitRailItem(R.drawable.ic_mdi_speedometer,
                t(R.string.side_rail_vehicle_info));
        menuSplitScreen = createPortraitRailItem(R.drawable.ic_mdi_view_split,
                t(R.string.side_rail_split_screen));
        menuSettings = createPortraitRailItem(R.drawable.ic_mdi_cog, t(R.string.side_rail_settings));

        menuContainer.addView(menuWifi);
        menuContainer.addView(menuApps);
        menuContainer.addView(menuFileUpload);
        menuContainer.addView(menuDriveMode);
        menuContainer.addView(menuWelcomeSound);
        menuContainer.addView(menuEqualizer);
        menuContainer.addView(menuClimateAutomation);
        menuContainer.addView(menuFaceRecognition);
        menuTest.setVisibility(View.GONE);
        menuContainer.addView(menuTest);
        menuContainer.addView(menuCamera360);
        menuContainer.addView(menuSplitScreen);
        menuContainer.addView(menuProjection);
        menuContainer.addView(menuVehicleInfo);
        menuContainer.addView(menuSettings);

        // Let the final destination travel all the way to the left edge.
        menuContainer.setPaddingRelative(0, 0,
                context.getResources().getDisplayMetrics().widthPixels, 0);
        menuScroll.setClipToPadding(false);
        menuScroll.addView(menuContainer, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        sideRail.addView(menuScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        buildGoldenDock();
        updateMenuSelection(menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        bindMenuClicks();
        return sideRail;
    }

    /** Launcher shortcuts use the same upstream disclaimer and navigation as the rail. */
    public boolean openCameraOrSplitScreen(int tabIndex) {
        if (tabIndex == TAB_CAMERA_360 && menuCamera360 != null) return menuCamera360.performClick();
        if (tabIndex == TAB_SPLIT_SCREEN && menuSplitScreen != null) return menuSplitScreen.performClick();
        return false;
    }

    private void bindMenuClicks() {
        menuWifi.setOnClickListener(v -> {
            callback.onTabSelected(TAB_WIFI, t(R.string.side_rail_wifi));
            updateMenuSelection(menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        });

        menuFileUpload.setOnClickListener(v -> {
            callback.onTabSelected(TAB_FILE, t(R.string.side_rail_web));
            updateMenuSelection(menuFileUpload, menuWifi, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        });

        menuProjection.setOnClickListener(v -> {
            callback.onTabSelected(TAB_PROJECTION, t(R.string.side_rail_projection));
            updateMenuSelection(menuProjection, menuWifi, menuFileUpload, menuProfile, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        });

        menuVehicleInfo.setOnClickListener(v -> {
            callback.onTabSelected(TAB_VEHICLE_INFO, t(R.string.side_rail_vehicle_info));
            updateMenuSelection(menuVehicleInfo, menuWifi, menuFileUpload, menuProfile, menuProjection, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        });

        menuSettings.setOnClickListener(v -> {
            callback.onTabSelected(TAB_SETTINGS, t(R.string.side_rail_settings));
            updateMenuSelection(menuSettings, menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        });

        menuApps.setOnClickListener(v -> {
            boolean accepted = prefs.getBoolean("appManagementDisclaimerAccepted", false);
            if (accepted) {
                callback.onTabSelected(TAB_APPS, t(R.string.side_rail_apps));
                updateMenuSelection(menuApps, menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuDriveMode, menuTest, menuWelcomeSound);
            } else {
                callback.onAppManagementRequested();
            }
        });

        menuDriveMode.setOnClickListener(v -> {
            callback.onTabSelected(TAB_DRIVE_MODE, t(R.string.side_rail_drive_mode));
            updateMenuSelection(menuDriveMode, menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuTest, menuWelcomeSound);
        });

        menuWelcomeSound.setOnClickListener(v -> {
            callback.onTabSelected(TAB_WELCOME_SOUND, t(R.string.side_rail_welcome_sound));
            updateMenuSelection(menuWelcomeSound, menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest);
        });

        menuEqualizer.setOnClickListener(v -> {
            callback.onTabSelected(TAB_EQUALIZER, t(R.string.side_rail_equalizer));
            updateMenuSelection(menuEqualizer, menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        });

        menuClimateAutomation.setOnClickListener(v -> openEarlyAccessMenu(
                "climateAutoBetaWarningSeen",
                t(R.string.side_rail_climate_automation),
                () -> {
                    callback.onTabSelected(TAB_CLIMATE_AUTOMATION, t(R.string.side_rail_climate_automation));
                    updateMenuSelection(menuClimateAutomation);
                }));

        menuFaceRecognition.setOnClickListener(v -> openEarlyAccessMenu(
                "dmsFaceBetaWarningSeen",
                t(R.string.dms_recognition_title),
                () -> {
                    callback.onTabSelected(TAB_FACE_RECOGNITION, t(R.string.dms_recognition_title));
                    updateMenuSelection(menuFaceRecognition);
                }));

        menuCamera360.setOnClickListener(v -> openEarlyAccessMenu(
                "camera360BetaWarningSeen",
                t(R.string.camera360_title),
                () -> {
                    callback.onTabSelected(TAB_CAMERA_360, t(R.string.camera360_title));
                    updateMenuSelection(menuCamera360);
                }));

        menuSplitScreen.setOnClickListener(v -> openEarlyAccessMenu(
                "splitScreenBetaWarningSeen",
                t(R.string.side_rail_split_screen),
                () -> {
                    callback.onTabSelected(TAB_SPLIT_SCREEN, t(R.string.side_rail_split_screen));
                    updateMenuSelection(menuSplitScreen);
                }));
    }

    /**
     * Launcher modu aktifken sol menüyü tamamen gizler; kapalıyken normal görünüme döner.
     */
    public void setLauncherModeActive(boolean active) {
        launcherModeActive = active;
        applyRailVisibility();
        updateDockHomeButton(active);
        applyDockPanelTheme();
        updateGoldenDockSelection(active ? TAB_LAUNCHER : currentDockSelection);
    }

    /** Icerik tam genislik istedi (or. Kamera 360 izleme ekrani); launcher modundan bagimsiz. */
    public void setContentWide(boolean wide) {
        contentWide = wide;
        applyRailVisibility();
    }

    public boolean isRailHidden() {
        return launcherModeActive || contentWide;
    }

    private void applyRailVisibility() {
        if (sideRail != null) {
            sideRail.setVisibility(isRailHidden() ? View.GONE : View.VISIBLE);
        }
        if (goldenDockLayout != null) {
            goldenDockLayout.setVisibility(contentWide ? View.GONE : View.VISIBLE);
        }
    }

    public boolean isLauncherModeActive() {
        return launcherModeActive;
    }

    /** Light/dark değişiminde rail arka planı ve seçim renklerini yeniler (Activity recreate yok). */
    public void reapplyTheme() {
        if (sideRail != null) {
            UiStyles.setRailPanelBackground(sideRail);
        }
        reapplyGoldenDockTheme();
        LinearLayout[] rows = menuRows();
        for (LinearLayout row : rows) {
            if (row != null) {
                applyRailRowSelectedState(row, row == selectedMenuRow);
            }
        }
    }

    private LinearLayout railMenuContainer;
    private LinearLayout goldenDockLayout;
    private View goldenDockDivider;
    private final java.util.List<View> goldenDockItems = new java.util.ArrayList<>();

    private void updateRailBottomInset() {
        if (railMenuContainer == null) return;
        int dockHeight = goldenDockLayout != null ? goldenDockLayout.getHeight() : 0;
        if (dockHeight <= 0) {
            dockHeight = Math.round(80 * context.getResources().getDisplayMetrics().density);
        }
        int bottom = dockHeight + UiStyles.dimenPx(context, R.dimen.spacing_medium);
        if (railMenuContainer.getPaddingBottom() != bottom) {
            railMenuContainer.setPadding(0, 0, 0, bottom);
        }
    }

    public View buildGoldenDock() {
        if (goldenDockLayout != null) {
            return goldenDockLayout;
        }
        float density = context.getResources().getDisplayMetrics().density;
        goldenDockLayout = new LinearLayout(context);
        goldenDockLayout.setOrientation(LinearLayout.VERTICAL);
        goldenDockLayout.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, oright, ob) -> updateRailBottomInset());
        applyDockPanelTheme();
        goldenDockItems.clear();

        goldenDockDivider = new View(context);
        goldenDockDivider.setBackgroundColor(UiStyles.color(context, R.color.outlineSubtle));
        goldenDockLayout.addView(goldenDockDivider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(1 * density))));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padH = UiStyles.dimenPx(context, R.dimen.spacing_small);
        int padV = UiStyles.dimenPx(context, R.dimen.spacing_small);
        row.setPadding(padH, padV, padH, padV);

        btnHome = createDockActionItem(
                launcherModeActive ? R.drawable.ic_mdi_view_grid : R.drawable.ic_mdi_home,
                launcherModeActive ? t(R.string.dock_menu) : t(R.string.dock_home),
                v -> {
                    if (callback != null) callback.onHomeRequested();
                });
        btnHomeIcon = (AppCompatImageView) btnHome.getTag(R.id.side_rail_icon);
        btnHomeLabel = (TextView) btnHome.getTag(R.id.side_rail_label);

        View btnBack = createDockActionItem(R.drawable.ic_vehicle_back, t(R.string.dock_back), v -> {
            if (callback != null) callback.onBackPressedRequested();
        });
        View btnDrive = createDockActionItem(R.drawable.ic_mdi_car, t(R.string.dock_drive_memory), v -> {
            if (callback != null) {
                callback.onTabSelected(TAB_DRIVE_MODE, t(R.string.side_rail_drive_mode));
            }
            updateMenuSelection(menuDriveMode, menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuTest, menuWelcomeSound);
        });
        View btnSettings = createDockActionItem(R.drawable.ic_mdi_cog, t(R.string.dock_settings), v -> {
            if (callback != null) {
                callback.onTabSelected(TAB_SETTINGS, t(R.string.side_rail_settings));
            }
            updateMenuSelection(menuSettings, menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        });

        goldenDockItems.add(btnHome);
        goldenDockItems.add(btnBack);
        goldenDockItems.add(btnDrive);
        goldenDockItems.add(btnSettings);

        row.addView(btnHome, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        row.addView(btnBack, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        row.addView(btnDrive, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        row.addView(btnSettings, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        goldenDockLayout.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        updateGoldenDockSelection(currentDockSelection >= 0 ? currentDockSelection : (launcherModeActive ? TAB_LAUNCHER : 0));
        return goldenDockLayout;
    }

    public View getGoldenDock() {
        if (goldenDockLayout == null) {
            buildGoldenDock();
        }
        return goldenDockLayout;
    }

    public void updateDockHomeButton(boolean isLauncher) {
        if (btnHomeIcon != null) {
            btnHomeIcon.setImageResource(isLauncher ? R.drawable.ic_mdi_view_grid : R.drawable.ic_mdi_home);
        }
        if (btnHomeLabel != null) {
            btnHomeLabel.setText(isLauncher ? t(R.string.dock_menu) : t(R.string.dock_home));
        }
    }

    private View createDockActionItem(int iconRes, String label, View.OnClickListener onClickListener) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        item.setMinimumHeight(Math.round(64 * density));
        int padH = Math.round(2 * density);
        int padV = Math.round(4 * density);
        item.setPadding(padH, padV, padH, padV);

        int iconSize = Math.round(40 * density);
        AppCompatImageView iconView = new AppCompatImageView(context);
        iconView.setImageResource(iconRes);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconPadding = Math.round(9 * density);
        iconView.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
        iconView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        iconView.setImageTintList(ColorStateList.valueOf(UiStyles.color(context, R.color.textSecondaryCool)));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        item.addView(iconView, iconLp);

        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        UiStyles.setTextSize(tv, R.dimen.text_size_tiny);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tvLp.topMargin = Math.round(2 * density);
        item.addView(tv, tvLp);

        item.setTag(R.id.side_rail_icon, iconView);
        item.setTag(R.id.side_rail_label, tv);
        item.setContentDescription(label);
        android.util.TypedValue feedback = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, feedback, true)) {
            item.setForeground(UiStyles.drawable(context, feedback.resourceId));
        }
        item.setOnClickListener(onClickListener);
        return item;
    }

    private int currentDockSelection = -1;

    public void updateGoldenDockSelection(int tabIndex) {
        currentDockSelection = tabIndex;
        if (goldenDockItems.size() < 4) return;
        int activeColor = UiStyles.color(context, R.color.accentHighlight);
        int idleColor = UiStyles.color(context, R.color.textSecondaryCool);
        int primaryColor = UiStyles.color(context, R.color.primaryColor);
        int activeBg = androidx.core.graphics.ColorUtils.setAlphaComponent(primaryColor, 45);

        for (int i = 0; i < goldenDockItems.size(); i++) {
            View item = goldenDockItems.get(i);
            boolean isActive = (i == 0 && tabIndex == TAB_LAUNCHER)
                    || (i == 2 && tabIndex == TAB_DRIVE_MODE)
                    || (i == 3 && tabIndex == TAB_SETTINGS);

            int mode = prefs.getInt("driveModeSetting", -1);
            boolean themedMemory = i == 2 && com.mapcontrol.vehicle.DriveModeStyle.isThemed(mode);
            int color = themedMemory
                    ? com.mapcontrol.vehicle.DriveModeStyle.accentColor(context, mode)
                    : (isActive ? activeColor : idleColor);
            View icon = (View) item.getTag(R.id.side_rail_icon);
            if (icon instanceof AppCompatImageView) {
                ((AppCompatImageView) icon).setImageTintList(ColorStateList.valueOf(color));
            }
            View label = (View) item.getTag(R.id.side_rail_label);
            if (label instanceof TextView) {
                ((TextView) label).setTextColor(color);
            }
            if (isActive || themedMemory) {
                int background = themedMemory
                        ? androidx.core.graphics.ColorUtils.setAlphaComponent(color, 45) : activeBg;
                if (icon != null) UiStyles.applySolidRoundedBackgroundDp(icon, background, 14);
                item.setBackgroundColor(Color.TRANSPARENT);
            } else {
                if (icon != null) UiStyles.setBackgroundRes(icon, R.drawable.bg_launcher_icon_slot);
                item.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    public void refreshDriveMemoryColor() {
        updateGoldenDockSelection(currentDockSelection);
    }

    private void applyDockPanelTheme() {
        if (goldenDockLayout == null) return;
        if (launcherModeActive) {
            UiStyles.setGlassCardBackground(goldenDockLayout);
        } else {
            UiStyles.setRailPanelBackground(goldenDockLayout);
        }
        if (goldenDockDivider != null) {
            goldenDockDivider.setVisibility(launcherModeActive ? View.GONE : View.VISIBLE);
        }
    }

    private void reapplyGoldenDockTheme() {
        applyDockPanelTheme();
        if (goldenDockDivider != null) {
            goldenDockDivider.setBackgroundColor(UiStyles.color(context, R.color.outlineSubtle));
        }
        updateGoldenDockSelection(currentDockSelection);
    }

    /**
     * Tıklama dışı (ör. Uygulama Yönetimi yasal diyalogu onayı) sonrası sol menü vurgusunu
     * {@code switchTab} ile aynı sekme indeksine hizalar.
     * <p>LOG (4) kenar çubuğunda olmadığından bu indekste işlem yapılmaz.
     */
    public void setSelectionForTabIndex(int tabIndex) {
        updateGoldenDockSelection(tabIndex);
        switch (tabIndex) {
            case 0:
                updateMenuSelection(menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
                break;
            case 1:
                updateMenuSelection(menuFileUpload, menuWifi, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
                break;
            case 2:
                updateMenuSelection(menuProfile, menuWifi, menuFileUpload, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
                break;
            case 3:
                updateMenuSelection(menuProjection, menuWifi, menuFileUpload, menuProfile, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
                break;
            case 4:
                break;
            case 5:
                updateMenuSelection(menuApps, menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuDriveMode, menuTest, menuWelcomeSound);
                break;
            case 6:
                updateMenuSelection(menuDriveMode, menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuTest, menuWelcomeSound);
                break;
            case 7:
                updateMenuSelection(menuSettings, menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
                break;
            case 8:
                updateMenuSelection(menuWelcomeSound, menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest);
                break;
            case 9:
                updateMenuSelection(menuVehicleInfo, menuWifi, menuFileUpload, menuProfile, menuProjection, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
                break;
            case 11:
                updateMenuSelection(menuEqualizer, menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
                break;
            case TAB_CLIMATE_AUTOMATION:
                updateMenuSelection(menuClimateAutomation);
                break;
            case TAB_FACE_RECOGNITION:
                updateMenuSelection(menuFaceRecognition);
                break;
            case TAB_CAMERA_360:
                updateMenuSelection(menuCamera360);
                break;
            case TAB_SPLIT_SCREEN:
                updateMenuSelection(menuSplitScreen);
                break;
            default:
                break;
        }
    }

    private String t(int resId) {
        return context.getString(resId);
    }

    private LinearLayout createRailMenuItemView(int iconResId, String text) {
        float density = context.getResources().getDisplayMetrics().density;
        int minTouchPx = Math.round(context.getResources().getDimension(R.dimen.rail_row_min_height));

        LinearLayout itemLayout = new LinearLayout(context);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        int padH = (int) (26 * density);
        int padV = (int) (20 * density);
        itemLayout.setPadding(padH, padV, padH, padV);
        itemLayout.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        itemLayout.setClickable(true);
        itemLayout.setFocusable(true);
        itemLayout.setMinimumHeight(minTouchPx);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int mH = (int) (12 * density);
        int mV = (int) (6 * density);
        params.setMargins(mH, mV, mH, mV);
        itemLayout.setLayoutParams(params);

        int iconSize = (int) (36 * density);
        AppCompatImageView iconView = new AppCompatImageView(context);
        iconView.setImageResource(iconResId);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconView.setImageTintList(ColorStateList.valueOf(UiStyles.color(context, R.color.textSecondary)));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.gravity = Gravity.CENTER_VERTICAL;
        itemLayout.addView(iconView, iconLp);

        TextView textView = new TextView(context);
        textView.setText(text);
        UiStyles.setTextSize(textView, R.dimen.text_size_large);
        textView.setTextColor(UiStyles.color(context, R.color.textSecondary));
        textView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f);
        textParams.setMargins((int) (16 * density), 0, 0, 0);
        itemLayout.addView(textView, textParams);

        itemLayout.setTag(R.id.side_rail_icon, iconView);
        itemLayout.setTag(R.id.side_rail_label, textView);

        return itemLayout;
    }

    private LinearLayout createPortraitRailItem(int iconResId, String text) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout itemLayout = new LinearLayout(context);
        itemLayout.setOrientation(LinearLayout.VERTICAL);
        int padH = (int) (14 * density);
        int padV = (int) (10 * density);
        itemLayout.setPadding(padH, padV, padH, padV);
        itemLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        itemLayout.setClickable(true);
        itemLayout.setFocusable(true);
        itemLayout.setMinimumWidth((int) (88 * density));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int m = (int) (4 * density);
        params.setMargins(m, m, m, m);
        itemLayout.setLayoutParams(params);

        int iconSize = (int) (28 * density);
        AppCompatImageView iconView = new AppCompatImageView(context);
        iconView.setImageResource(iconResId);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconView.setImageTintList(ColorStateList.valueOf(UiStyles.color(context, R.color.textSecondary)));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        itemLayout.addView(iconView, iconLp);

        TextView textView = new TextView(context);
        textView.setText(text);
        UiStyles.setTextSize(textView, R.dimen.text_size_small);
        textView.setMaxLines(1);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTextColor(UiStyles.color(context, R.color.textSecondary));
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = (int) (4 * density);
        itemLayout.addView(textView, textParams);

        itemLayout.setTag(R.id.side_rail_icon, iconView);
        itemLayout.setTag(R.id.side_rail_label, textView);
        return itemLayout;
    }

    private void applyRailRowSelectedState(LinearLayout row, boolean selected) {
        AppCompatImageView icon = (AppCompatImageView) row.getTag(R.id.side_rail_icon);
        TextView label = (TextView) row.getTag(R.id.side_rail_label);
        if (selected) {
            UiStyles.setBackgroundRes(row, R.drawable.bg_nav_pill_selected);
            if (icon != null) {
                icon.setImageTintList(ColorStateList.valueOf(UiStyles.color(context, R.color.oemAccent)));
            }
            if (label != null) {
                label.setTextColor(UiStyles.color(context, R.color.oemAccent));
                label.setTypeface(null, android.graphics.Typeface.BOLD);
            }
        } else {
            row.setBackgroundColor(Color.TRANSPARENT);
            if (icon != null) {
                icon.setImageTintList(ColorStateList.valueOf(UiStyles.color(context, R.color.textSecondary)));
            }
            if (label != null) {
                label.setTextColor(UiStyles.color(context, R.color.textSecondary));
                label.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }
    }

    private void openEarlyAccessMenu(String seenKey, String title, Runnable open) {
        if (prefs.getBoolean(seenKey, false)) {
            open.run();
            return;
        }
        DialogHelper.showEarlyAccessDisclaimer(context, title, () -> {
            prefs.edit().putBoolean(seenKey, true).apply();
            open.run();
        }, () -> {});
    }

    private LinearLayout[] menuRows() {
        return new LinearLayout[]{
                menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo,
                menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound, menuEqualizer, menuClimateAutomation, menuFaceRecognition, menuCamera360, menuSplitScreen
        };
    }

    private void updateMenuSelection(LinearLayout selected, LinearLayout... others) {
        selectedMenuRow = selected;
        applyRailRowSelectedState(selected, true);
        for (LinearLayout other : others) {
            if (other != null && other != selected) {
                applyRailRowSelectedState(other, false);
            }
        }
        for (LinearLayout row : menuRows()) {
            if (row != null && row != selected) {
                applyRailRowSelectedState(row, false);
            }
        }
    }
}
