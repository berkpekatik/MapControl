package com.mapcontrol.ui.builder;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

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
    private LinearLayout menuWelcomeSound;
    private LinearLayout menuProfile;
    private LinearLayout menuVehicleInfo;
    private LinearLayout sideRail;
    private TextView appTitleText;
    private LinearLayout selectedMenuRow;
    private boolean launcherModeActive;

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

        appTitleText = new TextView(context);
        try {
            String versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            SpannableString spannableText = new SpannableString("MapControl by vNoisy (" + versionName + ")");
            int vNoisyStart = spannableText.toString().indexOf("vNoisy");
            int vNoisyEnd = vNoisyStart + "vNoisy".length();
            if (vNoisyStart >= 0) {
                spannableText.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC),
                        vNoisyStart, vNoisyEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int versionStart = spannableText.toString().indexOf("(");
            int versionEnd = spannableText.length();
            if (versionStart >= 0) {
                spannableText.setSpan(new RelativeSizeSpan(0.82f),
                        versionStart, versionEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            appTitleText.setText(spannableText);
        } catch (PackageManager.NameNotFoundException e) {
            SpannableString spannableText = new SpannableString("MapControl by vNoisy");
            int vNoisyStart = spannableText.toString().indexOf("vNoisy");
            int vNoisyEnd = vNoisyStart + "vNoisy".length();
            if (vNoisyStart >= 0) {
                spannableText.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC),
                        vNoisyStart, vNoisyEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            appTitleText.setText(spannableText);
        }
        appTitleText.setTextSize(28);
        appTitleText.setTextColor(UiStyles.color(context, R.color.textPrimary));
        appTitleText.setTypeface(null, android.graphics.Typeface.BOLD);
        sideRailTopBar.addView(appTitleText);

        sideRail.addView(sideRailTopBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView menuScrollView = new ScrollView(context);
        menuScrollView.setBackgroundColor(Color.TRANSPARENT);
        menuScrollView.setFillViewport(false);

        LinearLayout menuContainer = new LinearLayout(context);
        menuContainer.setOrientation(LinearLayout.VERTICAL);
        menuContainer.setBackgroundColor(Color.TRANSPARENT);

        menuWifi = createRailMenuItemView(R.drawable.ic_mdi_wifi, "Wi-Fi Yönetimi");
        menuApps = createRailMenuItemView(R.drawable.ic_mdi_cellphone, "Uygulama Yönetimi");
        menuFileUpload = createRailMenuItemView(R.drawable.ic_mdi_web, "Web Yönetimi");
        menuProfile = createRailMenuItemView(R.drawable.ic_mdi_account, "Profil");
        menuDriveMode = createRailMenuItemView(R.drawable.ic_mdi_car, "Hafıza Modu");
        menuWelcomeSound = createRailMenuItemView(R.drawable.ic_mdi_volume_high,
                context.getString(R.string.side_rail_welcome_sound));
        menuTest = createRailMenuItemView(R.drawable.ic_mdi_camera, "Kamera Test");
        menuProjection = createRailMenuItemView(R.drawable.ic_mdi_map, "Yansıtma");
        menuVehicleInfo = createRailMenuItemView(R.drawable.ic_mdi_speedometer,
                context.getString(R.string.side_rail_vehicle_info));
        menuSettings = createRailMenuItemView(R.drawable.ic_mdi_cog, "Ayarlar");

        menuContainer.addView(menuWifi);
        menuContainer.addView(menuApps);
        menuContainer.addView(menuFileUpload);
        menuContainer.addView(menuProfile);
        menuContainer.addView(menuDriveMode);
        menuContainer.addView(menuWelcomeSound);
        menuTest.setVisibility(View.GONE);
        menuContainer.addView(menuTest);
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

        menuWifi.setOnClickListener(v -> {
            callback.onTabSelected(TAB_WIFI, "Wi-Fi Yönetimi");
            updateMenuSelection(menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        });

        menuFileUpload.setOnClickListener(v -> {
            callback.onTabSelected(TAB_FILE, "Web Yönetimi");
            updateMenuSelection(menuFileUpload, menuWifi, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        });

        menuProfile.setOnClickListener(v -> {
            callback.onTabSelected(TAB_PROFILE, "Profil");
            updateMenuSelection(menuProfile, menuWifi, menuFileUpload, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        });

        menuProjection.setOnClickListener(v -> {
            callback.onTabSelected(TAB_PROJECTION, "Yansıtma");
            updateMenuSelection(menuProjection, menuWifi, menuFileUpload, menuProfile, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        });

        menuVehicleInfo.setOnClickListener(v -> {
            callback.onTabSelected(TAB_VEHICLE_INFO, context.getString(R.string.side_rail_vehicle_info));
            updateMenuSelection(menuVehicleInfo, menuWifi, menuFileUpload, menuProfile, menuProjection, menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        });

        menuSettings.setOnClickListener(v -> {
            callback.onTabSelected(TAB_SETTINGS, "Ayarlar");
            updateMenuSelection(menuSettings, menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuApps, menuDriveMode, menuTest, menuWelcomeSound);
        });

        menuApps.setOnClickListener(v -> {
            boolean accepted = prefs.getBoolean("appManagementDisclaimerAccepted", false);
            if (accepted) {
                callback.onTabSelected(TAB_APPS, "Uygulama Yönetimi");
                updateMenuSelection(menuApps, menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuDriveMode, menuTest, menuWelcomeSound);
            } else {
                callback.onAppManagementRequested();
            }
        });

        menuDriveMode.setOnClickListener(v -> {
            callback.onTabSelected(TAB_DRIVE_MODE, "Hafıza Modu");
            updateMenuSelection(menuDriveMode, menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuTest, menuWelcomeSound);
        });

        menuWelcomeSound.setOnClickListener(v -> {
            callback.onTabSelected(TAB_WELCOME_SOUND, context.getString(R.string.side_rail_welcome_sound));
            updateMenuSelection(menuWelcomeSound, menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo, menuSettings, menuApps, menuDriveMode, menuTest);
        });

        return sideRail;
    }

    /**
     * Launcher modu aktifken sol menüyü tamamen gizler; kapalıyken normal görünüme döner.
     */
    public void setLauncherModeActive(boolean active) {
        launcherModeActive = active;
        if (sideRail != null) {
            sideRail.setVisibility(active ? View.GONE : View.VISIBLE);
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
        if (appTitleText != null) {
            appTitleText.setTextColor(UiStyles.color(context, R.color.textPrimary));
        }
        LinearLayout[] rows = {
                menuWifi, menuFileUpload, menuProfile, menuProjection, menuVehicleInfo,
                menuSettings, menuApps, menuDriveMode, menuTest, menuWelcomeSound
        };
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
            default:
                break;
        }
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

    private void updateMenuSelection(LinearLayout selected, LinearLayout... others) {
        selectedMenuRow = selected;
        applyRailRowSelectedState(selected, true);
        for (LinearLayout other : others) {
            applyRailRowSelectedState(other, false);
        }
    }
}
