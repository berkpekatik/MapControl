package com.mapcontrol.ui.builder;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
    private LinearLayout menuProfile;

    public SideRailBuilder(Context context, SharedPreferences prefs, SideRailCallback callback) {
        this.context = context;
        this.prefs = prefs;
        this.callback = callback;
    }

    public LinearLayout build() {
        LinearLayout sideRail = new LinearLayout(context);
        sideRail.setOrientation(LinearLayout.VERTICAL);
        sideRail.setBackgroundColor(0xFF1C2630);
        sideRail.setPadding(0, 0, 0, 0);

        LinearLayout sideRailTopBar = new LinearLayout(context);
        sideRailTopBar.setOrientation(LinearLayout.HORIZONTAL);
        sideRailTopBar.setBackgroundColor(0xFF1C2630);
        sideRailTopBar.setPadding(24, 16, 16, 16);
        sideRailTopBar.setGravity(Gravity.CENTER_VERTICAL);
        sideRailTopBar.setMinimumHeight((int) (48 * context.getResources().getDisplayMetrics().density));

        TextView appTitleText = new TextView(context);
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
                spannableText.setSpan(new RelativeSizeSpan(0.75f),
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
        appTitleText.setTextSize(20);
        appTitleText.setTextColor(0xFFFFFFFF);
        appTitleText.setTypeface(null, android.graphics.Typeface.BOLD);
        sideRailTopBar.addView(appTitleText);

        sideRail.addView(sideRailTopBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView menuScrollView = new ScrollView(context);
        menuScrollView.setBackgroundColor(0xFF1C2630);
        menuScrollView.setFillViewport(false);

        LinearLayout menuContainer = new LinearLayout(context);
        menuContainer.setOrientation(LinearLayout.VERTICAL);
        menuContainer.setBackgroundColor(0xFF1C2630);

        menuWifi = createRailMenuItemView("📶", "Wi-Fi Yönetimi", true);
        menuApps = createRailMenuItemView("📱", "Uygulama Yönetimi", false);
        menuFileUpload = createRailMenuItemView("📤", "Dosya Yükle", false);
        menuProfile = createRailMenuItemView("👤", "Profil", false);
        menuDriveMode = createRailMenuItemView("🚗", "Hafıza Modu", false);
        menuTest = createRailMenuItemView("📷", "Kamera Test", false);
        menuProjection = createRailMenuItemView("🗺️", "Yansıtma", false);
        menuSettings = createRailMenuItemView("⚙️", "Ayarlar", false);

        menuContainer.addView(menuWifi);
        menuContainer.addView(menuApps);
        menuContainer.addView(menuFileUpload);
        menuContainer.addView(menuProfile);
        menuContainer.addView(menuDriveMode);
        menuTest.setVisibility(View.GONE);
        menuContainer.addView(menuTest);
        menuContainer.addView(menuProjection);
        menuContainer.addView(menuSettings);

        menuScrollView.addView(menuContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams menuScrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f);
        sideRail.addView(menuScrollView, menuScrollParams);

        menuWifi.setOnClickListener(v -> {
            callback.onTabSelected(TAB_WIFI, "Wi-Fi Yönetimi");
            updateMenuSelection(menuWifi, menuFileUpload, menuProfile, menuProjection, menuSettings, menuApps, menuDriveMode, menuTest);
        });

        menuFileUpload.setOnClickListener(v -> {
            callback.onTabSelected(TAB_FILE, "Dosya Yükle");
            updateMenuSelection(menuFileUpload, menuWifi, menuProfile, menuProjection, menuSettings, menuApps, menuDriveMode, menuTest);
        });

        menuProfile.setOnClickListener(v -> {
            callback.onTabSelected(TAB_PROFILE, "Profil");
            updateMenuSelection(menuProfile, menuWifi, menuFileUpload, menuProjection, menuSettings, menuApps, menuDriveMode, menuTest);
        });

        menuProjection.setOnClickListener(v -> {
            callback.onTabSelected(TAB_PROJECTION, "Yansıtma");
            updateMenuSelection(menuProjection, menuWifi, menuFileUpload, menuProfile, menuSettings, menuApps, menuDriveMode, menuTest);
        });

        menuSettings.setOnClickListener(v -> {
            callback.onTabSelected(TAB_SETTINGS, "Ayarlar");
            updateMenuSelection(menuSettings, menuWifi, menuFileUpload, menuProfile, menuProjection, menuApps, menuDriveMode, menuTest);
        });

        menuApps.setOnClickListener(v -> {
            boolean accepted = prefs.getBoolean("appManagementDisclaimerAccepted", false);
            if (accepted) {
                callback.onTabSelected(TAB_APPS, "Uygulama Yönetimi");
                updateMenuSelection(menuApps, menuWifi, menuFileUpload, menuProfile, menuProjection, menuSettings, menuDriveMode, menuTest);
            } else {
                callback.onAppManagementRequested();
            }
        });

        menuDriveMode.setOnClickListener(v -> {
            callback.onTabSelected(TAB_DRIVE_MODE, "Hafıza Modu");
            updateMenuSelection(menuDriveMode, menuWifi, menuFileUpload, menuProfile, menuProjection, menuSettings, menuApps, menuTest);
        });

        return sideRail;
    }

    /**
     * Tıklama dışı (ör. Uygulama Yönetimi yasal diyalogu onayı) sonrası sol menü vurgusunu
     * {@code switchTab} ile aynı sekme indeksine hizalar.
     * <p>LOG (4) kenar çubuğunda olmadığından bu indekste işlem yapılmaz.
     */
    public void setSelectionForTabIndex(int tabIndex) {
        switch (tabIndex) {
            case 0:
                updateMenuSelection(menuWifi, menuFileUpload, menuProfile, menuProjection, menuSettings, menuApps, menuDriveMode, menuTest);
                break;
            case 1:
                updateMenuSelection(menuFileUpload, menuWifi, menuProfile, menuProjection, menuSettings, menuApps, menuDriveMode, menuTest);
                break;
            case 2:
                updateMenuSelection(menuProfile, menuWifi, menuFileUpload, menuProjection, menuSettings, menuApps, menuDriveMode, menuTest);
                break;
            case 3:
                updateMenuSelection(menuProjection, menuWifi, menuFileUpload, menuProfile, menuSettings, menuApps, menuDriveMode, menuTest);
                break;
            case 4:
                break;
            case 5:
                updateMenuSelection(menuApps, menuWifi, menuFileUpload, menuProfile, menuProjection, menuSettings, menuDriveMode, menuTest);
                break;
            case 6:
                updateMenuSelection(menuDriveMode, menuWifi, menuFileUpload, menuProfile, menuProjection, menuSettings, menuApps, menuTest);
                break;
            case 7:
                updateMenuSelection(menuSettings, menuWifi, menuFileUpload, menuProfile, menuProjection, menuApps, menuDriveMode, menuTest);
                break;
            default:
                break;
        }
    }

    private LinearLayout createRailMenuItemView(String icon, String text, boolean isSelected) {
        float density = context.getResources().getDisplayMetrics().density;
        int minTouchSizePx = 80;
        int minTouchSizeDp = (int) (minTouchSizePx / density);

        LinearLayout itemLayout = new LinearLayout(context);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setPadding(0, Math.max(32, minTouchSizeDp / 2), 24, Math.max(32, minTouchSizeDp / 2));
        itemLayout.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        itemLayout.setClickable(true);
        itemLayout.setFocusable(true);
        itemLayout.setMinimumHeight(minTouchSizePx);

        if (isSelected) {
            itemLayout.setBackgroundColor(0xFF1A4A6B);
        } else {
            itemLayout.setBackgroundColor(0x00000000);
        }

        LinearLayout leftPaddingContainer = new LinearLayout(context);
        leftPaddingContainer.setOrientation(LinearLayout.HORIZONTAL);
        leftPaddingContainer.setPadding(24, 0, 0, 0);
        leftPaddingContainer.setGravity(Gravity.CENTER_VERTICAL);

        if (isSelected) {
            View accentBar = new View(context);
            accentBar.setBackgroundColor(0xFF3DAEA8);
            LinearLayout.LayoutParams accentBarParams = new LinearLayout.LayoutParams(
                    5,
                    LinearLayout.LayoutParams.MATCH_PARENT);
            accentBarParams.setMargins(0, 12, 16, 12);
            leftPaddingContainer.addView(accentBar, accentBarParams);
        }

        LinearLayout iconContainer = new LinearLayout(context);
        iconContainer.setOrientation(LinearLayout.HORIZONTAL);
        iconContainer.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        LinearLayout.LayoutParams iconContainerParams = new LinearLayout.LayoutParams(
                64,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        leftPaddingContainer.addView(iconContainer, iconContainerParams);

        TextView iconView = new TextView(context);
        iconView.setText(icon);
        iconView.setTextSize(28);
        iconView.setTextColor(0xFFFFFFFF);
        iconView.setGravity(Gravity.START);
        iconContainer.addView(iconView);

        itemLayout.addView(leftPaddingContainer);

        TextView textView = new TextView(context);
        textView.setText(text);
        textView.setTextSize(18);
        textView.setTextColor(0xFFFFFFFF);
        textView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        textView.setTypeface(null, android.graphics.Typeface.NORMAL);
        if (isSelected) {
            textView.setTextColor(0xE6FFFFFF);
        }
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f);
        textParams.setMargins(16, 0, 0, 0);
        itemLayout.addView(textView, textParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        itemLayout.setLayoutParams(params);

        return itemLayout;
    }

    private void updateMenuSelection(LinearLayout selected, LinearLayout... others) {
        selected.setBackgroundColor(0xFF1A4A6B);

        if (selected.getChildCount() > 0 && selected.getChildAt(0) instanceof LinearLayout) {
            LinearLayout leftPaddingContainer = (LinearLayout) selected.getChildAt(0);
            if (leftPaddingContainer.getChildCount() > 0) {
                View firstChild = leftPaddingContainer.getChildAt(0);
                if (firstChild.getLayoutParams() != null && firstChild.getLayoutParams().width == 5) {
                    firstChild.setBackgroundColor(0xFF3DAEA8);
                } else {
                    View accentBar = new View(context);
                    accentBar.setBackgroundColor(0xFF3DAEA8);
                    LinearLayout.LayoutParams accentBarParams = new LinearLayout.LayoutParams(
                            5, LinearLayout.LayoutParams.MATCH_PARENT);
                    accentBarParams.setMargins(0, 8, 12, 8);
                    leftPaddingContainer.addView(accentBar, 0, accentBarParams);
                }
            }
        }

        TextView selectedText = (TextView) selected.getChildAt(selected.getChildCount() - 1);
        if (selectedText != null) {
            selectedText.setTypeface(null, android.graphics.Typeface.NORMAL);
            selectedText.setTextColor(0xE6FFFFFF);
        }

        for (LinearLayout other : others) {
            other.setBackgroundColor(0x00000000);
            if (other.getChildCount() > 0 && other.getChildAt(0) instanceof LinearLayout) {
                LinearLayout otherLeftPaddingContainer = (LinearLayout) other.getChildAt(0);
                if (otherLeftPaddingContainer.getChildCount() > 0) {
                    View firstChild = otherLeftPaddingContainer.getChildAt(0);
                    if (firstChild.getLayoutParams() != null && firstChild.getLayoutParams().width == 5) {
                        otherLeftPaddingContainer.removeViewAt(0);
                    }
                }
            }
            TextView otherText = (TextView) other.getChildAt(other.getChildCount() - 1);
            if (otherText != null) {
                otherText.setTypeface(null, android.graphics.Typeface.NORMAL);
                otherText.setTextColor(0xFFFFFFFF);
            }
        }
    }
}
