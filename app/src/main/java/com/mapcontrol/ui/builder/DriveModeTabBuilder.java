package com.mapcontrol.ui.builder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.graphics.ColorUtils;

import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.DriveModeStyle;

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
    private FrameLayout driveModeComponent;
    private View bgBase;
    private View bgReveal;
    private Animator activeRevealAnimator;


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
        scrollView.setFillViewport(false);

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        outer.setPadding(margin, margin, margin, margin);

        tabContent = new LinearLayout(context);
        tabContent.setOrientation(LinearLayout.VERTICAL);

        outer.addView(tabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        float density = context.getResources().getDisplayMetrics().density;
        int savedMode = prefs.getInt("driveModeSetting", -1);

        // Dinamik temalı ve damla dalgalanma efektli sürüş modu bileşeni (Card Component)
        driveModeComponent = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                // Decorative MATCH_PARENT layers must not determine the card height.
                View content = getChildAt(getChildCount() - 1);
                measureChildWithMargins(content, widthMeasureSpec, 0,
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0);
                int contentHeight = content.getMeasuredHeight() + getPaddingTop() + getPaddingBottom();
                super.onMeasure(widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY));
            }
        };
        driveModeComponent.setTag("drive_mode_component");
        driveModeComponent.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 16f * density);
            }
        });
        driveModeComponent.setClipToOutline(true);

        bgBase = new View(context);
        bgBase.setBackground(createModeBackgroundDrawable(context, savedMode));
        driveModeComponent.addView(bgBase, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        bgReveal = new View(context);
        bgReveal.setVisibility(View.INVISIBLE);
        driveModeComponent.addView(bgReveal, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout driveModeContent = new LinearLayout(context);
        driveModeContent.setOrientation(LinearLayout.VERTICAL);
        int inner = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        driveModeContent.setPadding(inner, inner, inner, inner);
        driveModeComponent.addView(driveModeContent, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        tabContent.addView(driveModeComponent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout driveTitleRow = new LinearLayout(context);
        driveTitleRow.setOrientation(LinearLayout.HORIZONTAL);
        driveTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        driveTitleRow.setPadding(16, 24, 16, 12);

        AppCompatImageView driveTitleIcon = new AppCompatImageView(context);
        driveTitleIcon.setImageResource(R.drawable.ic_mdi_car);
        driveTitleIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int titleIconPx = (int) (28 * density);
        driveTitleIcon.setLayoutParams(new LinearLayout.LayoutParams(titleIconPx, titleIconPx));
        driveTitleIcon.setImageTintList(ColorStateList.valueOf(
                UiStyles.color(context, R.color.textPrimary)));
        driveTitleRow.addView(driveTitleIcon);

        TextView driveModeTitle = new TextView(context);
        driveModeTitle.setText(R.string.drive_modes_title);
        UiStyles.setTextSize(driveModeTitle, R.dimen.text_size_large);
        driveModeTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        driveModeTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleRowTextLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleRowTextLp.setMargins((int) (12 * density), 0, 0, 0);
        driveTitleRow.addView(driveModeTitle, titleRowTextLp);

        AppCompatCheckBox fourWheelModes = new AppCompatCheckBox(context);
        fourWheelModes.setTag("drive_mode_4x4");
        fourWheelModes.setText(R.string.drive_modes_4x4);
        UiStyles.setTextSize(fourWheelModes, R.dimen.text_size_small);
        fourWheelModes.setTextColor(UiStyles.color(context, R.color.textPrimary));
        fourWheelModes.setButtonDrawable(R.drawable.bg_choice_control);
        fourWheelModes.setButtonTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{UiStyles.color(context, R.color.oemAccent),
                        UiStyles.color(context, R.color.textSecondary)}));
        fourWheelModes.setMinHeight(UiStyles.dimenPx(context, R.dimen.button_height));
        fourWheelModes.setCompoundDrawablePadding(Math.round(8 * density));
        fourWheelModes.setPadding(Math.round(12 * density), 0, Math.round(8 * density), 0);
        fourWheelModes.setChecked(prefs.getBoolean("driveMode4x4Expanded", false));
        driveTitleRow.addView(fourWheelModes, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        driveModeContent.addView(driveTitleRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout driveModeTrack = new LinearLayout(context);
        driveModeTrack.setOrientation(LinearLayout.HORIZONTAL);
        driveModeTrack.setBackgroundColor(Color.TRANSPARENT);
        driveModeTrack.setPadding(0, 0, 0, 0);
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

        int pianoKeyH = Math.round(100f * density);
        int indicatorHpx = Math.max(1, Math.round(4f * density));

        String[] driveModeNames = {
                context.getString(R.string.drive_mode_none),
                "Eco", "Normal", "Sport", "Snow", "Mud", "Offroad", "Sand"
        };
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

        final LinearLayout[] modeCards = new LinearLayout[driveModeNames.length];
        final AppCompatImageView[] modeIcons = new AppCompatImageView[driveModeNames.length];
        final TextView[] modeTitles = new TextView[driveModeNames.length];
        final View[] indicators = new View[driveModeNames.length];

        for (int i = 0; i < driveModeNames.length; i++) {
            final int modeValue = driveModeValues[i];
            final int radioId = driveModeIds[i];
            final String modeName = driveModeNames[i];
            final int modeIconRes = driveModeIconRes[i];

            LinearLayout modeCard = new LinearLayout(context);
            modeCard.setTag("drive_mode_card_" + modeValue);
            modeCard.setOrientation(LinearLayout.VERTICAL);
            modeCard.setGravity(Gravity.CENTER_HORIZONTAL);
            int keyPadH = UiStyles.dimenPx(context, R.dimen.spacing_tiny);
            modeCard.setPadding(keyPadH, 10, keyPadH, 8);
            modeCard.setClickable(true);
            modeCard.setFocusable(true);

            boolean isSelected = (savedMode == modeValue);

            RadioGroup.LayoutParams cardParams = new RadioGroup.LayoutParams(
                    0, pianoKeyH, 1f);
            int cardMargin = Math.round(6 * density);
            cardParams.setMargins(cardMargin, 0, cardMargin, 0);

            LinearLayout titleBand = new LinearLayout(context);
            titleBand.setOrientation(LinearLayout.VERTICAL);
            titleBand.setGravity(Gravity.CENTER);
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
            modeIconLp.gravity = Gravity.CENTER_HORIZONTAL;
            modeIconLp.bottomMargin = (int) (4 * density);
            modeIcon.setLayoutParams(modeIconLp);
            modeIcon.setImageTintList(ColorStateList.valueOf(UiStyles.color(context,
                    isSelected ? R.color.textPrimary : R.color.textPrimary85)));
            modeIcons[i] = modeIcon;
            titleBand.addView(modeIcon);

            TextView modeTitle = new TextView(context);
            modeTitle.setText(modeName);
            modeTitle.setTextColor(UiStyles.color(context, isSelected ? R.color.textPrimary : R.color.textPrimary85));
            UiStyles.setTextSize(modeTitle, R.dimen.text_size_small);
            modeTitle.setTypeface(null, isSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            modeTitle.setGravity(Gravity.CENTER);
            modeTitle.setMaxLines(2);
            modeTitle.setEllipsize(TextUtils.TruncateAt.END);
            modeTitle.setLineSpacing(2, 1.0f);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            titleBand.addView(modeTitle, titleLp);

            View indicator = new View(context);
            LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(
                    Math.round(24 * density), Math.round(3 * density));
            indicatorParams.gravity = Gravity.CENTER_HORIZONTAL;
            indicatorParams.bottomMargin = Math.round(6 * density);
            indicator.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            modeCard.addView(indicator, indicatorParams);

            modeCards[i] = modeCard;
            modeTitles[i] = modeTitle;
            indicators[i] = indicator;
            styleModeCard(modeCard, modeIcon, modeTitle, indicator, modeValue, isSelected);

            RadioButton radioButton = new RadioButton(context);
            radioButton.setId(radioId);
            radioButton.setVisibility(View.GONE);
            modeCard.addView(radioButton);

            final int finalIndex = i;
            modeCard.setOnClickListener(v -> {
                transitionModeBackground(modeValue, modeCard);
                driveModeRadioGroup.check(radioId);
            });
            driveModeRadioGroup.addView(modeCard, cardParams);
            if (modeValue == -1 || (modeValue >= 3 && !fourWheelModes.isChecked())) {
                modeCard.setVisibility(View.GONE);
            }
        }

        driveModeTrack.addView(driveModeRadioGroup);
        driveModeContent.addView(driveModeTrack, driveModeTrackParams);

        fourWheelModes.setOnCheckedChangeListener((button, expanded) -> {
            prefs.edit().putBoolean("driveMode4x4Expanded", expanded).apply();
            for (int i = 0; i < driveModeValues.length; i++) {
                if (driveModeValues[i] >= 3) {
                    modeCards[i].setVisibility(expanded ? View.VISIBLE : View.GONE);
                }
            }
        });

        TextView clearSelection = new TextView(context);
        clearSelection.setTag("drive_mode_clear");
        clearSelection.setText(R.string.drive_mode_clear_selection);
        UiStyles.setTextSize(clearSelection, R.dimen.text_size_small);
        clearSelection.setTextColor(UiStyles.color(context, R.color.textSecondary));
        clearSelection.setGravity(Gravity.CENTER);
        clearSelection.setMinHeight(UiStyles.dimenPx(context, R.dimen.button_height));
        int clearPadding = Math.round(12 * density);
        clearSelection.setPadding(clearPadding, 0, clearPadding, 0);
        clearSelection.setClickable(true);
        clearSelection.setFocusable(true);
        android.util.TypedValue selectableBackground = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,
                selectableBackground, true);
        clearSelection.setBackgroundResource(selectableBackground.resourceId);
        clearSelection.setVisibility(savedMode == -1 ? View.GONE : View.VISIBLE);
        clearSelection.setOnClickListener(v -> {
            transitionModeBackground(-1, clearSelection);
            driveModeRadioGroup.check(9);
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clearParams.setMarginEnd(Math.round(16 * density));
        driveTitleRow.addView(clearSelection, driveTitleRow.getChildCount() - 1, clearParams);

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
                clearSelection.setVisibility(selectedMode == -1 ? View.GONE : View.VISIBLE);

                for (int i = 0; i < modeCards.length; i++) {
                    if (modeCards[i] != null) {
                        boolean isSelected = (i == selectedIndex);
                        styleModeCard(modeCards[i], modeIcons[i], modeTitles[i], indicators[i],
                                driveModeValues[i], isSelected);
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

    private Drawable createModeBackgroundDrawable(Context context, int mode) {
        float density = context.getResources().getDisplayMetrics().density;
        float cornerRadius = 16f * density;
        int baseSurface = UiStyles.color(context, R.color.surfaceCard);

        if (mode < 0 || !DriveModeStyle.isThemed(mode)) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(baseSurface);
            gd.setCornerRadius(cornerRadius);
            gd.setStroke(Math.max(1, Math.round(1f * density)),
                    UiStyles.color(context, R.color.outline));
            return gd;
        }

        int accent = DriveModeStyle.accentColor(context, mode);
        int startColor = ColorUtils.blendARGB(baseSurface, accent, 0.42f);
        int midColor = ColorUtils.blendARGB(baseSurface, accent, 0.24f);
        int endColor = ColorUtils.blendARGB(baseSurface, accent, 0.12f);

        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, midColor, endColor}
        );
        gd.setCornerRadius(cornerRadius);
        int strokeColor = ColorUtils.setAlphaComponent(accent, 160);
        gd.setStroke(Math.max(1, Math.round(1.5f * density)), strokeColor);
        return gd;
    }

    private void transitionModeBackground(int modeValue, View anchorView) {
        if (driveModeComponent == null || bgBase == null || bgReveal == null) {
            return;
        }
        Drawable newBg = createModeBackgroundDrawable(context, modeValue);
        if (!driveModeComponent.isAttachedToWindow() || driveModeComponent.getWidth() <= 0) {
            bgBase.setBackground(newBg);
            bgReveal.setVisibility(View.INVISIBLE);
            return;
        }

        int clickX = driveModeComponent.getWidth() / 2;
        int clickY = driveModeComponent.getHeight() / 2;

        if (anchorView != null) {
            int[] anchorLoc = new int[2];
            anchorView.getLocationInWindow(anchorLoc);
            int[] compLoc = new int[2];
            driveModeComponent.getLocationInWindow(compLoc);
            clickX = (anchorLoc[0] - compLoc[0]) + anchorView.getWidth() / 2;
            clickY = (anchorLoc[1] - compLoc[1]) + anchorView.getHeight() / 2;
        }

        float endRadius = (float) Math.hypot(
                Math.max(clickX, driveModeComponent.getWidth() - clickX),
                Math.max(clickY, driveModeComponent.getHeight() - clickY)
        );

        bgReveal.setBackground(newBg);
        bgReveal.setVisibility(View.VISIBLE);

        if (activeRevealAnimator != null && activeRevealAnimator.isRunning()) {
            activeRevealAnimator.cancel();
        }

        Animator anim = ViewAnimationUtils.createCircularReveal(bgReveal, clickX, clickY, 0f, endRadius);
        anim.setDuration(550);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                bgBase.setBackground(createModeBackgroundDrawable(context, modeValue));
                bgReveal.setVisibility(View.INVISIBLE);
            }
        });
        activeRevealAnimator = anim;
        anim.start();
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

    private void styleModeCard(LinearLayout card, ImageView icon, TextView title,
            View indicator, int mode, boolean selected) {
        boolean known = DriveModeStyle.isThemed(mode);
        int accent = known ? DriveModeStyle.accentColor(context, mode)
                : UiStyles.color(context, R.color.textSecondary);
        int surface = UiStyles.color(context, R.color.surfaceCardInner);
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(16 * density);
        if (selected) {
            background.setColor(ColorUtils.blendARGB(UiStyles.color(context, R.color.assistCardActive), accent, 0.22f));
            background.setStroke(Math.round(2 * density), accent);
        } else {
            background.setColor(UiStyles.color(context, R.color.assistCardIdle));
            background.setStroke(Math.round(1 * density), UiStyles.color(context, R.color.outline));
        }
        card.setBackground(background);
        card.setSelected(selected);
        icon.setImageTintList(ColorStateList.valueOf(accent));
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

        GradientDrawable indBg = new GradientDrawable();
        indBg.setCornerRadius(2 * density);
        indBg.setColor(accent);
        indicator.setBackground(indBg);
        indicator.setVisibility(selected ? View.VISIBLE : View.GONE);
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



    public void release() {
        if (activeRevealAnimator != null) {
            activeRevealAnimator.cancel();
            activeRevealAnimator = null;
        }
    }

    public ScrollView getScrollView() {
        return scrollView != null ? scrollView : build();
    }

    public LinearLayout getTabContent() {
        return tabContent;
    }
}
