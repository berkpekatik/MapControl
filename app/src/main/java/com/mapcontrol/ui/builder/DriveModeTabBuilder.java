package com.mapcontrol.ui.builder;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

public class DriveModeTabBuilder {
    public interface DriveModeCallback {
        void onModeSelected(int modeValue);
        void log(String message);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final DriveModeCallback callback;

    private ScrollView scrollView;
    private LinearLayout tabContent;

    public DriveModeTabBuilder(Context context, SharedPreferences prefs, DriveModeCallback callback) {
        this.context = context;
        this.prefs = prefs;
        this.callback = callback;
        build();
    }

    public ScrollView build() {
        scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(0xFF101922);
        scrollView.setPadding(0, 0, 0, 0);
        scrollView.setFillViewport(true);

        tabContent = new LinearLayout(context);
        tabContent.setOrientation(LinearLayout.VERTICAL);
        tabContent.setPadding(0, 0, 0, 0);
        tabContent.setBackgroundColor(0xFF101922);
        scrollView.addView(tabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView driveModeTitle = new TextView(context);
        driveModeTitle.setText("Sürüş Modları");
        driveModeTitle.setTextSize(20);
        driveModeTitle.setTextColor(0xFFFFFFFF);
        driveModeTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        driveModeTitle.setPadding(16, 24, 16, 12);
        tabContent.addView(driveModeTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout driveModeContainer = new LinearLayout(context);
        driveModeContainer.setOrientation(LinearLayout.HORIZONTAL);
        driveModeContainer.setBackgroundColor(0xFF1C2630);
        LinearLayout.LayoutParams driveModeContainerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        driveModeContainerParams.setMargins(16, 0, 16, 16);
        driveModeContainer.setPadding(2, 12, 2, 12);

        RadioGroup driveModeRadioGroup = new RadioGroup(context);
        driveModeRadioGroup.setOrientation(LinearLayout.HORIZONTAL);
        RadioGroup.LayoutParams radioGroupParams = new RadioGroup.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        driveModeRadioGroup.setLayoutParams(radioGroupParams);

        String[] driveModeNames = {"Hiçbiri", "Eco", "Normal", "Sport", "Snow", "Mud", "Offroad", "Sand"};
        int[] driveModeValues = {-1, 0, 1, 2, 3, 4, 5, 7};
        int[] driveModeIds = {9, 10, 11, 12, 13, 14, 15, 17};

        int savedMode = prefs.getInt("driveModeSetting", -1);

        final LinearLayout[] modeCards = new LinearLayout[driveModeNames.length];
        final TextView[] modeTitles = new TextView[driveModeNames.length];
        final android.view.View[] indicators = new android.view.View[driveModeNames.length];

        for (int i = 0; i < driveModeNames.length; i++) {
            final int modeValue = driveModeValues[i];
            final int radioId = driveModeIds[i];
            final String modeName = driveModeNames[i];

            LinearLayout modeCard = new LinearLayout(context);
            modeCard.setOrientation(LinearLayout.VERTICAL);
            modeCard.setGravity(android.view.Gravity.CENTER);
            modeCard.setPadding(4, 12, 4, 0);
            modeCard.setClickable(true);
            modeCard.setFocusable(true);
            modeCard.setMinimumHeight(70);

            boolean isSelected = (savedMode == modeValue);
            modeCard.setBackgroundColor(isSelected ? 0xFF2A3A47 : 0xFF1A1F26);

            RadioGroup.LayoutParams cardParams = new RadioGroup.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            cardParams.setMargins(1, 0, 1, 0);

            TextView modeTitle = new TextView(context);
            modeTitle.setText(modeName);
            modeTitle.setTextColor(isSelected ? 0xFFFFFFFF : 0xD9FFFFFF);
            modeTitle.setTextSize(13);
            modeTitle.setTypeface(null, isSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            modeTitle.setGravity(android.view.Gravity.CENTER);
            modeTitle.setMaxLines(2);
            modeTitle.setEllipsize(TextUtils.TruncateAt.END);
            modeTitle.setLineSpacing(2, 1.0f);
            modeCard.addView(modeTitle);

            android.view.View indicator = new android.view.View(context);
            indicator.setBackgroundColor(0xFF4CAF50);
            LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (int) (4 * context.getResources().getDisplayMetrics().density));
            indicatorParams.setMargins(0, 8, 0, 0);
            indicator.setVisibility(isSelected ? android.view.View.VISIBLE : android.view.View.GONE);
            modeCard.addView(indicator, indicatorParams);

            modeCards[i] = modeCard;
            modeTitles[i] = modeTitle;
            indicators[i] = indicator;

            RadioButton radioButton = new RadioButton(context);
            radioButton.setId(radioId);
            radioButton.setVisibility(android.view.View.GONE);
            modeCard.addView(radioButton);

            modeCard.setOnClickListener(v -> driveModeRadioGroup.check(radioId));
            driveModeRadioGroup.addView(modeCard, cardParams);

            if (i < driveModeNames.length - 1) {
                android.view.View divider = new android.view.View(context);
                divider.setBackgroundColor(0x1FFFFFFF);
                RadioGroup.LayoutParams dividerParams = new RadioGroup.LayoutParams(
                        1, LinearLayout.LayoutParams.MATCH_PARENT);
                dividerParams.setMargins(0, 12, 0, 12);
                driveModeRadioGroup.addView(divider, dividerParams);
            }
        }

        driveModeContainer.addView(driveModeRadioGroup);
        tabContent.addView(driveModeContainer, driveModeContainerParams);

        if (savedMode == -1) {
            driveModeRadioGroup.check(9);
        } else {
            int initialId = mapModeToRadioId(savedMode);
            if (initialId != -1) {
                driveModeRadioGroup.check(initialId);
            }
        }

        driveModeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int selectedMode = -1;
            int selectedIndex = -1;

            if (checkedId == 9) {
                selectedMode = -1;
                selectedIndex = 0;
            } else {
                for (int i = 0; i < driveModeIds.length; i++) {
                    if (driveModeIds[i] == checkedId) {
                        selectedMode = driveModeValues[i];
                        selectedIndex = i;
                        break;
                    }
                }
            }

            if (selectedIndex >= 0) {
                prefs.edit().putInt("driveModeSetting", selectedMode).apply();
                callback.onModeSelected(selectedMode);

                for (int i = 0; i < modeCards.length; i++) {
                    if (modeCards[i] != null) {
                        boolean isSelected = (i == selectedIndex);
                        modeCards[i].setBackgroundColor(isSelected ? 0xFF2A3A47 : 0xFF1A1F26);
                        if (modeTitles[i] != null) {
                            modeTitles[i].setTextColor(isSelected ? 0xFFFFFFFF : 0xD9FFFFFF);
                            modeTitles[i].setTypeface(null, isSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                        }
                        if (indicators[i] != null) {
                            indicators[i].setVisibility(isSelected ? android.view.View.VISIBLE : android.view.View.GONE);
                        }
                    }
                }

                if (selectedMode == -1) {
                    callback.log("Hafıza modu seçildi: Hiçbiri (Otomatik ayarlama yapılmayacak)");
                } else {
                    callback.log("Hafıza modu seçildi: " + driveModeNames[selectedIndex] + " (Mode: " + selectedMode + ")");
                    applyDriveMode(selectedMode);
                }
            }
        });

        return scrollView;
    }

    private int mapModeToRadioId(int mode) {
        switch (mode) {
            case -1: return 9;
            case 0: return 10;
            case 1: return 11;
            case 2: return 12;
            case 3: return 13;
            case 4: return 14;
            case 5: return 15;
            case 7: return 17;
            default: return -1;
        }
    }

    private void applyDriveMode(int mode) {
        try {
            CarInfoProxy carInfo = CarInfoProxy.getInstance();

            if (!carInfo.isServiceConnnected()) {
                carInfo.init(context);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> applyDriveMode(mode), 500);
                return;
            }

            int valueToSend = mode;
            if (mode == 6 || mode == 7) {
                valueToSend = 7; // SAND
            }

            carInfo.sendItemValue(VDEventCarInfo.MODULE_NEW_ENERGY, 4, valueToSend);
            callback.log("Hafıza modu gönderildi: " + valueToSend + " (Mode: " + mode + ")");
        } catch (Exception e) {
            callback.log("applyDriveMode hatası: " + e.getMessage());
        }
    }

    public ScrollView getScrollView() {
        return scrollView != null ? scrollView : build();
    }

    public LinearLayout getTabContent() {
        return tabContent;
    }
}
