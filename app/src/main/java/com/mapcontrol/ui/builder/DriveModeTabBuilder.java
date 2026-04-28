package com.mapcontrol.ui.builder;
import android.graphics.Color;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
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
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        scrollView.setPadding(0, 0, 0, 0);
        scrollView.setFillViewport(true);

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        outer.setPadding(margin, margin, margin, margin);

        tabContent = new LinearLayout(context);
        tabContent.setOrientation(LinearLayout.VERTICAL);
        int inner = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        tabContent.setPadding(inner, inner, inner, inner);
        UiStyles.setGlassCardBackground(tabContent);

        outer.addView(tabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        float densityTitle = context.getResources().getDisplayMetrics().density;
        LinearLayout driveTitleRow = new LinearLayout(context);
        driveTitleRow.setOrientation(LinearLayout.HORIZONTAL);
        driveTitleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        driveTitleRow.setPadding(16, 24, 16, 12);

        AppCompatImageView driveTitleIcon = new AppCompatImageView(context);
        driveTitleIcon.setImageResource(R.drawable.ic_mdi_car);
        driveTitleIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int titleIconPx = (int) (28 * densityTitle);
        driveTitleIcon.setLayoutParams(new LinearLayout.LayoutParams(titleIconPx, titleIconPx));
        driveTitleIcon.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.textPrimary)));
        driveTitleRow.addView(driveTitleIcon);

        TextView driveModeTitle = new TextView(context);
        driveModeTitle.setText("Sürüş Modları");
        driveModeTitle.setTextSize(20);
        driveModeTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        driveModeTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleRowTextLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleRowTextLp.setMargins((int) (12 * densityTitle), 0, 0, 0);
        driveTitleRow.addView(driveModeTitle, titleRowTextLp);

        tabContent.addView(driveTitleRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout driveModeTrack = new LinearLayout(context);
        driveModeTrack.setOrientation(LinearLayout.HORIZONTAL);
        driveModeTrack.setBackgroundResource(R.drawable.bg_segment_track);
        int trackPad = UiStyles.dimenPx(context, R.dimen.spacing_tiny);
        driveModeTrack.setPadding(trackPad, trackPad, trackPad, trackPad);
        LinearLayout.LayoutParams driveModeTrackParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        driveModeTrackParams.setMargins(0, 0, 0, UiStyles.dimenPx(context, R.dimen.spacing_medium));

        RadioGroup driveModeRadioGroup = new RadioGroup(context);
        driveModeRadioGroup.setOrientation(LinearLayout.HORIZONTAL);
        RadioGroup.LayoutParams radioGroupLp = new RadioGroup.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        driveModeRadioGroup.setLayoutParams(radioGroupLp);

        float density = context.getResources().getDisplayMetrics().density;
        int pianoKeyH = Math.round(100f * density);
        int indicatorHpx = Math.max(1, Math.round(4f * density));

        String[] driveModeNames = {"Hiçbiri", "Eco", "Normal", "Sport", "Snow", "Mud", "Offroad", "Sand"};
        int[] driveModeValues = {-1, 0, 1, 2, 3, 4, 5, 7};
        int[] driveModeIds = {9, 10, 11, 12, 13, 14, 15, 17};
        int[] driveModeIconRes = {
                R.drawable.ic_mdi_radiobox_blank,
                R.drawable.ic_mdi_leaf,
                R.drawable.ic_mdi_car,
                R.drawable.ic_mdi_car_sports,
                R.drawable.ic_mdi_snowflake,
                R.drawable.ic_mdi_waves,
                R.drawable.ic_mdi_terrain,
                R.drawable.ic_mdi_beach
        };

        int savedMode = prefs.getInt("driveModeSetting", -1);

        final LinearLayout[] modeCards = new LinearLayout[driveModeNames.length];
        final AppCompatImageView[] modeIcons = new AppCompatImageView[driveModeNames.length];
        final TextView[] modeTitles = new TextView[driveModeNames.length];
        final android.view.View[] indicators = new android.view.View[driveModeNames.length];

        for (int i = 0; i < driveModeNames.length; i++) {
            final int modeValue = driveModeValues[i];
            final int radioId = driveModeIds[i];
            final String modeName = driveModeNames[i];
            final int modeIconRes = driveModeIconRes[i];

            LinearLayout modeCard = new LinearLayout(context);
            modeCard.setOrientation(LinearLayout.VERTICAL);
            modeCard.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            int keyPadH = UiStyles.dimenPx(context, R.dimen.spacing_tiny);
            modeCard.setPadding(keyPadH, 10, keyPadH, 8);
            modeCard.setClickable(true);
            modeCard.setFocusable(true);

            boolean isSelected = (savedMode == modeValue);
            if (isSelected) {
                modeCard.setBackgroundResource(R.drawable.bg_segment_thumb);
            } else {
                modeCard.setBackgroundColor(Color.TRANSPARENT);
            }

            RadioGroup.LayoutParams cardParams = new RadioGroup.LayoutParams(
                    0, pianoKeyH, 1f);
            cardParams.setMargins(1, 0, 1, 0);

            LinearLayout titleBand = new LinearLayout(context);
            titleBand.setOrientation(LinearLayout.VERTICAL);
            titleBand.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams titleBandLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f);
            modeCard.addView(titleBand, titleBandLp);

            int modeIconPx = (int) (22 * density);
            AppCompatImageView modeIcon = new AppCompatImageView(context);
            modeIcon.setImageResource(modeIconRes);
            modeIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams modeIconLp = new LinearLayout.LayoutParams(modeIconPx, modeIconPx);
            modeIconLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            modeIconLp.bottomMargin = (int) (4 * density);
            modeIcon.setLayoutParams(modeIconLp);
            modeIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context,
                    isSelected ? R.color.textPrimary : R.color.textPrimary85)));
            modeIcons[i] = modeIcon;
            titleBand.addView(modeIcon);

            TextView modeTitle = new TextView(context);
            modeTitle.setText(modeName);
            modeTitle.setTextColor(ContextCompat.getColor(context, isSelected ? R.color.textPrimary : R.color.textPrimary85));
            modeTitle.setTextSize(12);
            modeTitle.setTypeface(null, isSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            modeTitle.setGravity(android.view.Gravity.CENTER);
            modeTitle.setMaxLines(2);
            modeTitle.setEllipsize(TextUtils.TruncateAt.END);
            modeTitle.setLineSpacing(2, 1.0f);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            titleBand.addView(modeTitle, titleLp);

            android.view.View indicator = new android.view.View(context);
            indicator.setBackgroundColor(ContextCompat.getColor(context, R.color.oemAccent));
            LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    indicatorHpx);
            indicatorParams.setMargins(0, 0, 0, 0);
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
        }

        driveModeTrack.addView(driveModeRadioGroup);
        tabContent.addView(driveModeTrack, driveModeTrackParams);

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
                        if (isSelected) {
                            modeCards[i].setBackgroundResource(R.drawable.bg_segment_thumb);
                        } else {
                            modeCards[i].setBackgroundColor(Color.TRANSPARENT);
                        }
                        if (modeIcons[i] != null) {
                            modeIcons[i].setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context,
                                    isSelected ? R.color.textPrimary : R.color.textPrimary85)));
                        }
                        if (modeTitles[i] != null) {
                            modeTitles[i].setTextColor(ContextCompat.getColor(context, isSelected ? R.color.textPrimary : R.color.textPrimary85));
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
