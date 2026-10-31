package com.mapcontrol.ui.builder;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
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
import com.mapcontrol.util.DialogHelper;

public class SideRailBuilder {
    public interface SideRailCallback {
        void onTabSelected(int tabIndex, String title);
        void onAppManagementRequested();
        void log(String message);
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
    private static final int TAB_EQUALIZER = 11;
    private static final int TAB_CLIMATE_AUTOMATION = 12;
    private static final int TAB_FACE_RECOGNITION = 13;
    private static final int TAB_CAMERA_360 = 14;
    private static final int TAB_SPLIT_SCREEN = 15;
    private static final int TAB_SUNROOF_AUTOMATION = 16;

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
    private LinearLayout menuSunroofAutomation;
    private LinearLayout menuProfile;
    private LinearLayout menuVehicleInfo;
    private LinearLayout menuSplitScreen;
    private LinearLayout sideRail;
    private TextView appTitleText;
    private LinearLayout selectedMenuRow;
    private boolean launcherModeActive;
    private boolean contentWide;
    private boolean premiumTitle;

    public SideRailBuilder(Context context, SharedPreferences prefs, SideRailCallback callback) {
        this.context = context;
        this.prefs = prefs;
        this.callback = callback;
    }

    public LinearLayout build() {
        sideRail = new LinearLayout(context);
        sideRail.setOrientation(LinearLayout.VERTICAL);
        UiStyles.setRailPanelBackground(sideRail);
        sideRail.setPadding(0, 0, 0, 0);

        LinearLayout sideRailTopBar = new LinearLayout(context);
        sideRailTopBar.setOrientation(LinearLayout.HORIZONTAL);
        sideRailTopBar.setBackgroundColor(Color.TRANSPARENT);
        sideRailTopBar.setPadding(36, 26, 28, 22);
        sideRailTopBar.setGravity(Gravity.CENTER_VERTICAL);
        sideRailTopBar.setMinimumHeight((int) (76 * context.getResources().getDisplayMetrics().density));

        bindAppTitle(sideRailTopBar, 28);

        sideRail.addView(sideRailTopBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView menuScrollView = new ScrollView(context);
        menuScrollView.setBackgroundColor(Color.TRANSPARENT);
        menuScrollView.setFillViewport(false);

        LinearLayout menuContainer = new LinearLayout(context);
        menuContainer.setOrientation(LinearLayout.VERTICAL);
        menuContainer.setBackgroundColor(Color.TRANSPARENT);

        menuWifi = createRailMenuItemView(R.drawable.ic_mdi_wifi, t(R.string.side_rail_wifi));
        menuApps = createRailMenuItemView(R.drawable.ic_mdi_cellphone, t(R.string.side_rail_apps));
        menuFileUpload = createRailMenuItemView(R.drawable.ic_mdi_web, t(R.string.side_rail_web));
        menuProfile = createRailMenuItemView(R.drawable.ic_mdi_account, t(R.string.side_rail_profile));
        menuDriveMode = createRailMenuItemView(R.drawable.ic_mdi_car, t(R.string.side_rail_drive_mode));
        menuWelcomeSound = createRailMenuItemView(R.drawable.ic_mdi_volume_high,
                t(R.string.side_rail_welcome_sound));
        menuEqualizer = createRailMenuItemView(R.drawable.ic_mdi_equalizer,
                t(R.string.side_rail_equalizer));
        menuClimateAutomation = createRailMenuItemView(R.drawable.ic_mdi_thermometer,
                t(R.string.side_rail_climate_automation));
        menuSunroofAutomation = createRailMenuItemView(R.drawable.ic_mdi_car_sunroof,
                t(R.string.side_rail_sunroof_automation));
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
        menuProfile.setVisibility(View.GONE);
        menuContainer.addView(menuProfile);
        menuContainer.addView(menuDriveMode);
        menuContainer.addView(menuWelcomeSound);
        menuContainer.addView(menuEqualizer);
        menuContainer.addView(menuClimateAutomation);
        menuContainer.addView(menuSunroofAutomation);
        menuContainer.addView(menuFaceRecognition);
        menuTest.setVisibility(View.GONE);
        menuContainer.addView(menuTest);
        menuContainer.addView(menuCamera360);
        menuContainer.addView(menuSplitScreen);
        menuContainer.addView(menuProjection);
        menuContainer.addView(menuVehicleInfo);
        menuContainer.addView(menuSettings);

        menuScrollView.addView(menuContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams menuScrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f);
        sideRail.addView(menuScrollView, menuScrollParams);

        updateMenuSelection(menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        bindMenuClicks();
        return sideRail;
    }

    /**
     * Dikey ekran: sol kolon yok. Aynı sekmeler üstte yatay kaydırılan ikon + kısa etiket şeridi.
     */
    public LinearLayout buildPortrait() {
        sideRail = new LinearLayout(context);
        sideRail.setOrientation(LinearLayout.VERTICAL);
        UiStyles.setRailPanelBackground(sideRail);

        LinearLayout titleBar = new LinearLayout(context);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(28, 16, 20, 8);
        bindAppTitle(titleBar, 22);
        sideRail.addView(titleBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

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
        menuSunroofAutomation = createPortraitRailItem(R.drawable.ic_mdi_car_sunroof,
                t(R.string.side_rail_sunroof_automation));
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
        menuProfile.setVisibility(View.GONE);
        menuContainer.addView(menuProfile);
        menuContainer.addView(menuDriveMode);
        menuContainer.addView(menuWelcomeSound);
        menuContainer.addView(menuEqualizer);
        menuContainer.addView(menuClimateAutomation);
        menuContainer.addView(menuSunroofAutomation);
        menuContainer.addView(menuFaceRecognition);
        menuTest.setVisibility(View.GONE);
        menuContainer.addView(menuTest);
        menuContainer.addView(menuCamera360);
        menuContainer.addView(menuSplitScreen);
        menuContainer.addView(menuProjection);
        menuContainer.addView(menuVehicleInfo);
        menuContainer.addView(menuSettings);

        menuScroll.addView(menuContainer, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        sideRail.addView(menuScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        updateMenuSelection(menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        bindMenuClicks();
        return sideRail;
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

        menuProfile.setOnClickListener(v -> {
            callback.onTabSelected(TAB_PROFILE, t(R.string.side_rail_profile));
            updateMenuSelection(menuProfile, menuWifi, menuFileUpload, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
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

        menuSunroofAutomation.setOnClickListener(v -> {
            callback.onTabSelected(TAB_SUNROOF_AUTOMATION, t(R.string.side_rail_sunroof_automation));
            updateMenuSelection(menuSunroofAutomation);
        });

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
    }

    public boolean isLauncherModeActive() {
        return launcherModeActive;
    }

    /** Premium üyelikte MapControl (sürüm) başlığını camgöbeğinden altına boyar. */
    public void setPremiumTitle(boolean premium) {
        premiumTitle = premium;
        paintAppTitle();
    }

    private void bindAppTitle(LinearLayout parent, float textSizeSp) {
        appTitleText = new TextView(context);
        try {
            String versionName = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            SpannableString spannableText = new SpannableString("MapControl (" + versionName + ")");
            int versionStart = spannableText.toString().indexOf("(");
            if (versionStart >= 0) {
                spannableText.setSpan(new RelativeSizeSpan(0.82f),
                        versionStart, spannableText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            appTitleText.setText(spannableText);
        } catch (PackageManager.NameNotFoundException e) {
            appTitleText.setText("MapControl");
        }
        appTitleText.setTextSize(textSizeSp);
        appTitleText.setTypeface(null, android.graphics.Typeface.BOLD);
        parent.addView(appTitleText);
        paintAppTitle();
    }

    private void paintAppTitle() {
        if (appTitleText == null) {
            return;
        }
        if (!premiumTitle) {
            appTitleText.getPaint().setShader(null);
            appTitleText.setTextColor(UiStyles.color(context, R.color.textPrimary));
            appTitleText.invalidate();
            return;
        }
        appTitleText.post(() -> {
            if (appTitleText == null) {
                return;
            }
            float width = appTitleText.getWidth();
            if (width <= 0) {
                width = appTitleText.getPaint().measureText(appTitleText.getText().toString());
            }
            appTitleText.getPaint().setShader(new LinearGradient(
                    0, 0, Math.max(width, 1f), 0,
                    new int[]{0xFF2EE6D6, 0xFFE6C15A},
                    null,
                    Shader.TileMode.CLAMP));
            appTitleText.invalidate();
        });
    }

    /** Light/dark değişiminde rail arka planı ve seçim renklerini yeniler (Activity recreate yok). */
    public void reapplyTheme() {
        if (sideRail != null) {
            UiStyles.setRailPanelBackground(sideRail);
        }
        paintAppTitle();
        LinearLayout[] rows = menuRows();
        for (LinearLayout row : rows) {
            if (row != null) {
                applyRailRowSelectedState(row, row == selectedMenuRow);
            }
        }
    }

    /**
     * Tıklama dışı (ör. Uygulama Yönetimi yasal diyalogu onayı) sonrası sol menü vurgusunu
     * {@code switchTab} ile aynı sekme indeksine hizalar.
     * <p>LOG (4) kenar çubuğunda olmadığından bu indekste işlem yapılmaz.
     */
    public void setSelectionForTabIndex(int tabIndex) {
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
            case TAB_SUNROOF_AUTOMATION:
                updateMenuSelection(menuSunroofAutomation);
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
        textView.setTextSize(22);
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
        textView.setTextSize(12);
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
                menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound, menuEqualizer, menuClimateAutomation, menuSunroofAutomation, menuFaceRecognition, menuCamera360, menuSplitScreen
        };
    }

    private void updateMenuSelection(LinearLayout selected, LinearLayout... others) {
        selectedMenuRow = selected;
        if (selected != null) {
            applyRailRowSelectedState(selected, true);
        }
        for (LinearLayout row : menuRows()) {
            if (row != null && row != selected) {
                applyRailRowSelectedState(row, false);
            }
        }
    }
}
