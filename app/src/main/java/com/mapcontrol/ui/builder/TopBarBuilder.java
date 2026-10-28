package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.graphics.ColorUtils;

import com.mapcontrol.BuildConfig;
import com.mapcontrol.R;
import com.mapcontrol.ui.HeaderStatusSink;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.ui.widget.StatusWidgetsView;
import com.mapcontrol.vehicle.DriveModeStyle;

/**
 * Üst başlık barı: Sayfa başlığı, geri butonu, bildirim tarzı kompakt durum çipleri ve marka rozeti.
 */
public class TopBarBuilder implements HeaderStatusSink {
    public enum WifiState { CONNECTED, DISCONNECTED, OFF, UNKNOWN }

    public interface TopBarCallback {
        void onLogTabToggle(boolean show);
        void log(String message);
        default void onStatusSelected(int tabIndex) {}
    }

    private final Context context;
    private final TopBarCallback callback;
    private final Handler titleClickHandler = new Handler(Looper.getMainLooper());
    private final Runnable titleClickReset = () -> titleClickCount = 0;
    private int titleClickCount;
    private boolean isLogTabVisible;
    private LinearLayout topBar;
    private TextView titleView;
    private TextView brandView;
    private AppCompatImageButton launcherBackButton;
    private View topBarDivider;
    private LinearLayout headerRow;
    private LinearLayout statusContainer;

    // Bildirim tarzı kompakt durum çipleri
    private View chipWifi;
    private View chipDriveMode;
    private View chipServer;
    private View chipProjection;

    private WifiState currentWifiState = WifiState.UNKNOWN;
    private boolean currentVehicleConnected = false;
    private int currentDriveMode = Integer.MIN_VALUE;
    private boolean showingSelectedMode = false;
    private boolean currentServerRunning = false;
    private boolean currentProjectionActive = false;

    private final StatusWidgetsView statusWidgets;

    public TopBarBuilder(Context context, TopBarCallback callback) {
        this.context = context;
        this.callback = callback;
        statusWidgets = new StatusWidgetsView(context, "header", callback::onStatusSelected);
    }

    public LinearLayout build() {
        topBar = new LinearLayout(context);
        topBar.setTag("shared_header");
        topBar.setOrientation(LinearLayout.VERTICAL);

        headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(20), dp(8), dp(20), dp(8));

        // 1. Sol: Title Area (Geri butonu + Sayfa Başlığı)
        LinearLayout titleArea = new LinearLayout(context);
        titleArea.setOrientation(LinearLayout.HORIZONTAL);
        titleArea.setGravity(Gravity.CENTER_VERTICAL);

        launcherBackButton = new AppCompatImageButton(context);
        launcherBackButton.setTag("header_back_button");
        launcherBackButton.setVisibility(View.GONE);
        launcherBackButton.setContentDescription(context.getString(R.string.header_back_home));
        launcherBackButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        launcherBackButton.setImageResource(R.drawable.ic_vehicle_back);
        launcherBackButton.setFocusable(true);
        launcherBackButton.setClickable(true);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        backParams.setMarginEnd(dp(8));
        // Header navigation is provided by the shared dock.

        titleView = new TextView(context);
        titleView.setTag("header_title");
        titleView.setText(R.string.side_rail_wifi);
        UiStyles.setTextSize(titleView, R.dimen.text_size_medium);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setSingleLine(true);
        titleView.setMinHeight(dp(44));
        titleView.setClickable(true);
        titleView.setFocusable(true);
        titleArea.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1.0f));

        headerRow.addView(titleArea, new LinearLayout.LayoutParams(0, -2, 1.0f));

        // 2. Orta/Sağ: Bildirim Tarzı Küçük Durum İkonları (Status Container)
        statusContainer = new LinearLayout(context);
        statusContainer.setOrientation(LinearLayout.HORIZONTAL);
        statusContainer.setGravity(Gravity.CENTER_VERTICAL);

        int chipMargin = dp(6);

        chipWifi = createHeaderNotificationChip(
                R.drawable.ic_mdi_wifi,
                context.getString(R.string.header_wifi),
                UiStyles.color(context, R.color.textSecondary),
                v -> { if (callback != null) callback.onStatusSelected(0); });
        LinearLayout.LayoutParams wifiLp = new LinearLayout.LayoutParams(-2, -2);
        wifiLp.setMarginEnd(chipMargin);
        statusContainer.addView(chipWifi, wifiLp);

        chipDriveMode = createHeaderNotificationChip(
                R.drawable.ic_mdi_car,
                "ECO",
                UiStyles.color(context, R.color.accentHighlight),
                v -> { if (callback != null) callback.onStatusSelected(6); });
        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(-2, -2);
        modeLp.setMarginEnd(chipMargin);
        statusContainer.addView(chipDriveMode, modeLp);

        chipServer = createHeaderNotificationChip(
                R.drawable.ic_mdi_cellphone,
                context.getString(R.string.header_phone),
                UiStyles.color(context, R.color.textSecondary),
                v -> { if (callback != null) callback.onStatusSelected(1); });
        LinearLayout.LayoutParams serverLp = new LinearLayout.LayoutParams(-2, -2);
        serverLp.setMarginEnd(chipMargin);
        statusContainer.addView(chipServer, serverLp);

        chipProjection = createHeaderNotificationChip(
                R.drawable.ic_hub_monitor,
                context.getString(R.string.header_projection),
                UiStyles.color(context, R.color.accentHighlight),
                v -> { if (callback != null) callback.onStatusSelected(3); });
        chipProjection.setVisibility(View.GONE);
        LinearLayout.LayoutParams projLp = new LinearLayout.LayoutParams(-2, -2);
        projLp.setMarginEnd(chipMargin);
        statusContainer.addView(chipProjection, projLp);

        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-2, -2);
        statusLp.setMarginEnd(dp(12));
        headerRow.addView(statusContainer, statusLp);

        // 3. Sağ: OEM Marka Rozeti
        brandView = new TextView(context);
        brandView.setTag("header_brand");
        SpannableString brand = new SpannableString("MapControl by vNoisy (" + BuildConfig.VERSION_NAME + ")");
        int author = brand.toString().indexOf("vNoisy");
        if (author >= 0) {
            brand.setSpan(new StyleSpan(Typeface.ITALIC), author, author + "vNoisy".length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int version = brand.toString().indexOf('(');
        if (version >= 0) {
            brand.setSpan(new RelativeSizeSpan(.82f), version, brand.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        brandView.setText(brand);
        UiStyles.setTextSize(brandView, R.dimen.text_size_small);
        brandView.setTypeface(null, Typeface.BOLD);
        brandView.setGravity(Gravity.CENTER);
        brandView.setTextDirection(View.TEXT_DIRECTION_LTR);
        brandView.setClickable(false);
        brandView.setFocusable(false);
        brandView.setPadding(dp(10), dp(5), dp(10), dp(5));
        UiStyles.applySolidRoundedBackgroundDp(brandView, UiStyles.color(context, R.color.surfaceCardInner), 10);
        headerRow.addView(brandView, new LinearLayout.LayoutParams(-2, -2));

        topBarDivider = new View(context);
        topBar.addView(headerRow, new LinearLayout.LayoutParams(-1, -2));
        topBar.addView(topBarDivider, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));

        titleView.setOnClickListener(v -> {
            titleClickCount++;
            titleClickHandler.removeCallbacks(titleClickReset);
            if (titleClickCount >= 3) {
                isLogTabVisible = !isLogTabVisible;
                callback.onLogTabToggle(isLogTabVisible);
                callback.log("Log sekmesi " + (isLogTabVisible ? "açıldı" : "kapatıldı"));
                titleClickCount = 0;
            } else titleClickHandler.postDelayed(titleClickReset, 1000);
        });

        refreshAllChips();
        reapplyTheme();
        return topBar;
    }

    private View createHeaderNotificationChip(int iconRes, String text, int accentColor, View.OnClickListener onClickListener) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setMinimumHeight(Math.round(30 * density));

        int padH = Math.round(8 * density);
        int padV = Math.round(4 * density);
        chip.setPadding(padH, padV, padH, padV);

        AppCompatImageView icon = new AppCompatImageView(context);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(accentColor));
        int iconSize = Math.round(15 * density);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.setMarginEnd(Math.round(5 * density));
        chip.addView(icon, iconLp);

        TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(accentColor);
        label.setSingleLine(true);
        chip.addView(label, new LinearLayout.LayoutParams(-2, -2));

        chip.setTag(R.id.side_rail_icon, icon);
        chip.setTag(R.id.side_rail_label, label);
        chip.setOnClickListener(onClickListener);

        applyChipBackground(chip, accentColor);
        return chip;
    }

    private void applyChipBackground(View chip, int accentColor) {
        int cardBg = UiStyles.color(context, R.color.surfaceCardInner);
        int blendedBg = ColorUtils.blendARGB(cardBg, accentColor, 0.14f);
        UiStyles.applySolidRoundedBackgroundDp(chip, blendedBg, 10);
    }

    private void refreshAllChips() {
        updateWifiChip();
        updateDriveModeChip();
        updateServerChip();
        updateProjectionChip();
    }

    private void updateWifiChip() {
        if (chipWifi == null) return;
        int accent;
        String text;
        if (currentWifiState == WifiState.CONNECTED) {
            accent = UiStyles.color(context, R.color.statusSuccess);
            text = context.getString(R.string.header_wifi);
        } else if (currentWifiState == WifiState.DISCONNECTED) {
            accent = UiStyles.color(context, R.color.statusWarning);
            text = context.getString(R.string.header_disconnected);
        } else if (currentWifiState == WifiState.OFF) {
            accent = UiStyles.color(context, R.color.textSecondary);
            text = context.getString(R.string.header_off);
        } else {
            accent = UiStyles.color(context, R.color.textSecondary);
            text = context.getString(R.string.header_wifi);
        }
        AppCompatImageView icon = (AppCompatImageView) chipWifi.getTag(R.id.side_rail_icon);
        if (icon != null) icon.setImageTintList(ColorStateList.valueOf(accent));
        TextView label = (TextView) chipWifi.getTag(R.id.side_rail_label);
        if (label != null) {
            label.setText(text);
            label.setTextColor(accent);
        }
        applyChipBackground(chipWifi, accent);
    }

    private void updateDriveModeChip() {
        if (chipDriveMode == null) return;
        int mode = currentDriveMode;
        boolean isKnown = (showingSelectedMode || currentVehicleConnected) && DriveModeStyle.isThemed(mode);
        int accent = isKnown ? DriveModeStyle.accentColor(context, mode) : UiStyles.color(context, R.color.textSecondary);
        String text = isKnown ? DriveModeStyle.shortLabel(mode) : "MOD";
        AppCompatImageView icon = (AppCompatImageView) chipDriveMode.getTag(R.id.side_rail_icon);
        if (icon != null) icon.setImageTintList(ColorStateList.valueOf(accent));
        TextView label = (TextView) chipDriveMode.getTag(R.id.side_rail_label);
        if (label != null) {
            label.setText(text);
            label.setTextColor(accent);
        }
        applyChipBackground(chipDriveMode, accent);
    }

    private void updateServerChip() {
        if (chipServer == null) return;
        int accent = currentServerRunning ? UiStyles.color(context, R.color.statusSuccess) : UiStyles.color(context, R.color.textSecondary);
        String text = currentServerRunning ? "Aktar (Açık)" : context.getString(R.string.header_phone);
        AppCompatImageView icon = (AppCompatImageView) chipServer.getTag(R.id.side_rail_icon);
        if (icon != null) icon.setImageTintList(ColorStateList.valueOf(accent));
        TextView label = (TextView) chipServer.getTag(R.id.side_rail_label);
        if (label != null) {
            label.setText(text);
            label.setTextColor(accent);
        }
        applyChipBackground(chipServer, accent);
    }

    private void updateProjectionChip() {
        if (chipProjection == null) return;
        if (currentProjectionActive) {
            chipProjection.setVisibility(View.VISIBLE);
            int accent = UiStyles.color(context, R.color.accentHighlight);
            AppCompatImageView icon = (AppCompatImageView) chipProjection.getTag(R.id.side_rail_icon);
            if (icon != null) icon.setImageTintList(ColorStateList.valueOf(accent));
            TextView label = (TextView) chipProjection.getTag(R.id.side_rail_label);
            if (label != null) {
                label.setText(R.string.header_projection);
                label.setTextColor(accent);
            }
            applyChipBackground(chipProjection, accent);
        } else {
            chipProjection.setVisibility(View.GONE);
        }
    }

    public StatusWidgetsView getStatusWidgets() { return statusWidgets; }

    @Override
    public void setWifiState(WifiState state) {
        currentWifiState = state;
        updateWifiChip();
        if (statusWidgets != null) statusWidgets.setWifiState(state);
    }

    @Override
    public void setDriveMode(boolean connected, int mode) {
        showingSelectedMode = false;
        currentVehicleConnected = connected;
        currentDriveMode = mode;
        updateDriveModeChip();
        if (statusWidgets != null) statusWidgets.setDriveMode(connected, mode);
    }

    @Override
    public void setSelectedDriveMode(int mode) {
        showingSelectedMode = true;
        currentDriveMode = mode;
        updateDriveModeChip();
        if (statusWidgets != null) statusWidgets.setSelectedDriveMode(mode);
    }

    @Override
    public void setServerRunning(boolean running) {
        currentServerRunning = running;
        updateServerChip();
        if (statusWidgets != null) statusWidgets.setServerRunning(running);
    }

    @Override
    public void setProjectionActive(boolean active) {
        currentProjectionActive = active;
        updateProjectionChip();
        if (statusWidgets != null) statusWidgets.setProjectionActive(active);
    }

    @Override
    public void setSoundBoost(boolean active, int gainMb) {
        if (statusWidgets != null) statusWidgets.setSoundBoost(active, gainMb);
    }

    public void setTitle(int titleRes) {
        if (titleView == null) return;
        String title = context.getString(titleRes);
        if (!android.text.TextUtils.equals(titleView.getText(), title)) titleView.setText(title);
    }

    public void setTitle(CharSequence title) {
        if (titleView == null || title == null) return;
        if (!android.text.TextUtils.equals(titleView.getText(), title)) titleView.setText(title);
    }

    public void setHeaderPosition(boolean isBottom) {
        // Header kalıcı olarak yukarıda sabitlenmiştir
    }

    public void setBackButtonVisible(boolean visible) {
        setLauncherBackButtonVisible(visible);
    }

    public void setBackButtonListener(View.OnClickListener listener) {
        setLauncherBackButtonListener(listener);
    }

    public void setLauncherBackButtonVisible(boolean visible) {
        if (launcherBackButton != null && (launcherBackButton.getVisibility() == View.VISIBLE) != visible)
            launcherBackButton.setVisibility(View.GONE);
    }

    public void setTopBarVisible(boolean visible) {
        if (topBar != null && (topBar.getVisibility() == View.VISIBLE) != visible)
            topBar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setLauncherBackButtonListener(View.OnClickListener listener) {
        if (launcherBackButton != null) launcherBackButton.setOnClickListener(listener);
    }

    public TextView getTitleView() { return titleView; }

    public void release() { titleClickHandler.removeCallbacksAndMessages(null); }

    public void reapplyTheme() {
        if (topBar != null) {
            float r = 16f * context.getResources().getDisplayMetrics().density;
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(UiStyles.color(context, R.color.surfaceBar));
            bg.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
            topBar.setBackground(bg);
        }
        if (titleView != null) titleView.setTextColor(UiStyles.color(context, R.color.textPrimary));
        if (brandView != null) {
            brandView.setTextColor(UiStyles.color(context, R.color.textSecondary));
            UiStyles.applySolidRoundedBackgroundDp(brandView, UiStyles.color(context, R.color.surfaceCardInner), 10);
        }
        if (launcherBackButton != null) {
            UiStyles.setGlassCardBackground(launcherBackButton);
            launcherBackButton.setImageTintList(ColorStateList.valueOf(UiStyles.color(context, R.color.textPrimary)));
        }
        if (topBarDivider != null) UiStyles.setBackgroundRes(topBarDivider, R.drawable.bg_topbar_divider);
        refreshAllChips();
        if (statusWidgets != null) statusWidgets.reapplyTheme();
    }

    private int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
