package com.mapcontrol.ui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.graphics.ColorUtils;
import com.mapcontrol.R;
import com.mapcontrol.ui.HeaderStatusSink;
import com.mapcontrol.ui.builder.TopBarBuilder;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.DriveModeStyle;
import java.util.function.IntConsumer;

/** Live status widgets independent of page chrome; used by the rail and launcher quick access. */
public final class StatusWidgetsView extends ViewGroup implements HeaderStatusSink {
    private final Context context;
    private final IntConsumer onSelected;
    private int columns, cellWidth, cellHeight;
    private boolean singleRow, compactCells;
    private int fixedColumnCount;
    private StatusChip wifiChip, modeChip, phoneChip, projectionChip, soundChip;
    private TopBarBuilder.WifiState wifiState = TopBarBuilder.WifiState.UNKNOWN;
    private boolean vehicleConnected, showingSelectedMode;
    private int driveMode = Integer.MIN_VALUE;
    private boolean serverRunning, projectionActive;
    private boolean soundBoostActive;
    private int soundGainMb;


    public StatusWidgetsView(Context context, String prefix, IntConsumer onSelected) {
        super(context);
        this.context = context;
        this.onSelected = onSelected;
        setTag(prefix + "_statuses");
        wifiChip = new StatusChip(prefix + "_wifi", R.string.header_wifi, R.drawable.ic_mdi_wifi, 0);
        modeChip = new StatusChip(prefix + "_mode", R.string.header_mode, R.drawable.ic_mdi_car, 6);
        phoneChip = new StatusChip(prefix + "_phone", R.string.header_phone, R.drawable.ic_mdi_cellphone, 1);
        projectionChip = new StatusChip(prefix + "_projection", R.string.header_projection,
                R.drawable.ic_hub_monitor, 3);
        soundChip = new StatusChip(prefix + "_sound", R.string.side_rail_equalizer,
                R.drawable.ic_mdi_volume_plus, 11);
        soundChip.setVisibility(View.GONE);
        addView(wifiChip);
        addView(modeChip);
        addView(phoneChip);
        addView(projectionChip);
        addView(soundChip);
        refreshStatuses();
    }

    public void setSingleRow(boolean enabled) {
        if (singleRow == enabled) return;
        singleRow = enabled;
        compactCells = enabled;
        for (int i = 0; i < getChildCount(); i++) {
            ((StatusChip) getChildAt(i)).arrange(enabled);
        }
        requestLayout();
    }

    /** Use a fixed number of columns, or zero for the adaptive layout. */
    public void setColumnCount(int count) {
        if (count < 0) throw new IllegalArgumentException("Column count must be non-negative");
        if (fixedColumnCount == count) return;
        fixedColumnCount = count;
        requestLayout();
    }

    public void reapplyTheme() { refreshStatuses(); }

    public void setWifiState(TopBarBuilder.WifiState state) {
        if (state == wifiState) return;
        wifiState = state;
        refreshWifi();
    }

    public void setDriveMode(boolean connected, int mode) {
        if (!showingSelectedMode && connected == vehicleConnected && mode == driveMode) return;
        showingSelectedMode = false;
        vehicleConnected = connected;
        driveMode = mode;
        refreshMode();
    }

    @Override public void setSelectedDriveMode(int mode) {
        if (showingSelectedMode && mode == driveMode) return;
        showingSelectedMode = true;
        driveMode = mode;
        refreshMode();
    }

    public void setServerRunning(boolean running) {
        if (serverRunning == running) return;
        serverRunning = running;
        refreshPhone();
    }

    public void setProjectionActive(boolean active) {
        if (projectionActive == active) return;
        projectionActive = active;
        refreshProjection();
    }

    @Override
    public void setSoundBoost(boolean active, int gainMb) {
        if (soundBoostActive == active && soundGainMb == gainMb) return;
        soundBoostActive = active;
        soundGainMb = gainMb;
        refreshSound();
    }

    private void refreshStatuses() {
        refreshWifi();
        refreshMode();
        refreshPhone();
        refreshProjection();
        refreshSound();
    }

    private void refreshWifi() {
        if (wifiChip == null) return;
        int label = R.string.header_unknown;
        if (wifiState == TopBarBuilder.WifiState.CONNECTED) label = R.string.header_connected;
        else if (wifiState == TopBarBuilder.WifiState.DISCONNECTED) label = R.string.header_disconnected;
        else if (wifiState == TopBarBuilder.WifiState.OFF) label = R.string.header_off;
        wifiChip.bind(context.getString(label), UiStyles.color(context,
                wifiState == TopBarBuilder.WifiState.CONNECTED ? R.color.statusSuccess
                : wifiState == TopBarBuilder.WifiState.DISCONNECTED ? R.color.statusWarning : R.color.textSecondary));
    }

    private void refreshMode() {
        if (modeChip == null) return;
        int mode = driveMode;
        boolean knownMode = (showingSelectedMode || vehicleConnected) && DriveModeStyle.isThemed(mode);
        modeChip.bind(knownMode ? DriveModeStyle.shortLabel(mode) : "—",
                knownMode ? DriveModeStyle.accentColor(context, mode)
                        : UiStyles.color(context, R.color.textSecondary));
        if (showingSelectedMode) {
            modeChip.setContentDescription(context.getString(R.string.header_selected_mode_description,
                    knownMode ? DriveModeStyle.shortLabel(mode) : "—"));
        }
    }

    private void refreshPhone() {
        if (phoneChip == null) return;
        phoneChip.bind(context.getString(serverRunning ? R.string.header_running : R.string.header_stopped),
                UiStyles.color(context, serverRunning ? R.color.statusSuccess : R.color.textSecondary));
    }

    private void refreshProjection() {
        if (projectionChip == null) return;
        projectionChip.bind(context.getString(projectionActive ? R.string.header_active : R.string.header_off),
                UiStyles.color(context, projectionActive ? R.color.statusSuccess : R.color.textSecondary));
    }

    private void refreshSound() {
        if (soundChip == null) return;
        if (soundBoostActive && soundGainMb > 0) {
            soundChip.setVisibility(View.VISIBLE);
            String val = String.format(java.util.Locale.US, "+%.1f dB", soundGainMb / 100.0f);
            soundChip.bind(val, UiStyles.color(context, R.color.oemAccent));
        } else {
            soundChip.setVisibility(View.GONE);
        }
    }


    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
    private int statusCellHeight() {
        return dp(68 * Math.max(1f, getResources().getConfiguration().fontScale));
    }

    private final class StatusChip extends LinearLayout {
        private final AppCompatImageView icon;
        private final TextView label, value;
        private final int labelRes;
        StatusChip(String tag, int labelRes, int iconRes, int tabIndex) {
            super(context);
            this.labelRes = labelRes;
            setTag(tag);
            setFocusable(true);
            setOnClickListener(v -> onSelected.accept(tabIndex));
            icon = new AppCompatImageView(context);
            icon.setImageResource(iconRes);
            label = new TextView(context);
            label.setText(labelRes);
            label.setSingleLine(true);
            label.setIncludeFontPadding(true);
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f);
            value = new TextView(context);
            value.setTag(tag + "_value");
            value.setTypeface(null, Typeface.BOLD);
            value.setIncludeFontPadding(true);
            value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16.5f);
            arrange(compactCells);
        }

        void arrange(boolean compact) {
            for (View child : new View[]{icon, label, value}) {
                if (child.getParent() instanceof ViewGroup) {
                    ((ViewGroup) child.getParent()).removeView(child);
                }
            }
            removeAllViews();
            setOrientation(compact ? VERTICAL : HORIZONTAL);
            setGravity(compact ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
            setPadding(dp(compact ? 4 : 12), dp(compact ? 6 : 8),
                    dp(compact ? 4 : 12), dp(compact ? 6 : 8));
            value.setSingleLine(!compact);
            value.setMaxLines(compact ? Integer.MAX_VALUE : 1);
            value.setGravity(compact ? Gravity.CENTER : Gravity.START);
            label.setVisibility(View.VISIBLE);
            LinearLayout texts = new LinearLayout(context);
            texts.setGravity(Gravity.CENTER_VERTICAL);
            if (compact) {
                texts.setGravity(Gravity.CENTER);
                texts.addView(icon, new LinearLayout.LayoutParams(dp(16), dp(16)));
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
                labelParams.setMarginStart(dp(4));
                texts.addView(label, labelParams);
                addView(texts, new LinearLayout.LayoutParams(-1, -2));
                addView(value, new LinearLayout.LayoutParams(-1, -2));
            } else {
                addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
                texts.setOrientation(VERTICAL);
                texts.addView(label);
                texts.addView(value);
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1);
                textParams.setMarginStart(dp(6));
                addView(texts, textParams);
            }
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            if (compactCells) {
                int available = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
                boolean showLabel = dp(20) + label.getPaint().measureText(label.getText().toString()) <= available;
                label.setVisibility(showLabel ? View.VISIBLE : View.GONE);
            }
            super.onMeasure(widthSpec, heightSpec);
        }

        void bind(String text, int accent) {
            label.setTextColor(UiStyles.color(context, R.color.textSecondary));
            value.setText(text);
            value.setTextColor(accent);
            icon.setImageTintList(ColorStateList.valueOf(accent));
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(dp(14));
            background.setColor(ColorUtils.blendARGB(UiStyles.color(context, R.color.surfaceCardInner), accent, .07f));
            setBackground(background);
            setContentDescription(labelRes == R.string.header_phone
                    ? context.getString(R.string.header_phone_description, text)
                    : context.getString(labelRes) + ": " + text);
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int measuredWidth = MeasureSpec.getSize(widthSpec);
        int width = Math.max(0, measuredWidth - getPaddingLeft() - getPaddingRight());
        int visibleCount = 0;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i).getVisibility() != View.GONE) visibleCount++;
        }
        if (visibleCount == 0) {
            setMeasuredDimension(measuredWidth, getPaddingTop() + getPaddingBottom());
            return;
        }
        int gap = dp(singleRow ? 6 : 12);
        int minimumCell = dp(160 * Math.max(1f, getResources().getConfiguration().fontScale));
        int maxPossibleCols = Math.max(1, (width + gap) / (minimumCell + gap));
        columns = singleRow ? visibleCount : Math.min(visibleCount,
                fixedColumnCount > 0 ? fixedColumnCount : maxPossibleCols);
        cellWidth = Math.max(0, (width - gap * (columns - 1)) / columns);
        boolean compact = singleRow || (fixedColumnCount > 0 && cellWidth < minimumCell);
        if (compactCells != compact) {
            compactCells = compact;
            for (int i = 0; i < getChildCount(); i++) {
                ((StatusChip) getChildAt(i)).arrange(compact);
            }
        }
        cellHeight = statusCellHeight();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            child.measure(
                    MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY),
                    compactCells ? MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                            : MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.EXACTLY));
            cellHeight = Math.max(cellHeight, child.getMeasuredHeight());
        }
        if (compactCells) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != View.GONE) child.measure(
                        MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.EXACTLY));
            }
        }
        int rows = (visibleCount + columns - 1) / columns;
        int height = rows * cellHeight + (rows - 1) * gap;
        setMeasuredDimension(measuredWidth, height + getPaddingTop() + getPaddingBottom());
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int visibleIdx = 0;
        int gap = dp(singleRow ? 6 : 12);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            int x = getPaddingLeft() + (visibleIdx % columns) * (cellWidth + gap);
            int y = getPaddingTop() + (visibleIdx / columns) * (cellHeight + gap);
            child.layout(x, y, x + cellWidth, y + cellHeight);
            visibleIdx++;
        }
    }
}
