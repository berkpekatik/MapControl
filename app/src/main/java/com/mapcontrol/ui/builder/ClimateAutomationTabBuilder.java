package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.SwitchCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.ClimateAutomationEngine;
import com.mapcontrol.vehicle.ClimateAutomationStore;

public class ClimateAutomationTabBuilder implements ClimateAutomationEngine.Listener {

    public interface Callback {
        void log(String message);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final Callback callback;
    private final ClimateAutomationEngine engine;

    private TextView liveStatusView;
    private View liveStatusIndicator;
    private final java.util.Map<String, TextView> conflictNotes = new java.util.HashMap<>();
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    public ClimateAutomationTabBuilder(Context context, SharedPreferences prefs, Callback callback) {
        this.context = context;
        this.prefs = prefs;
        this.callback = callback;
        this.engine = ClimateAutomationEngine.getInstance(context);
    }

    public LinearLayout build() {
        engine.removeListener(this);
        conflictNotes.clear();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        float density = context.getResources().getDisplayMetrics().density;
        boolean compactLayout = context.getResources().getConfiguration().screenWidthDp < 600;
        int pagePadding = dp(compactLayout ? 12 : 20, density);
        root.setPadding(pagePadding, dp(8, density), pagePadding, dp(24, density));

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout titleIconBadge = new FrameLayout(context);
        GradientDrawable titleIconBackground = new GradientDrawable();
        titleIconBackground.setColor(UiStyles.color(context, R.color.surfaceCardInner));
        titleIconBackground.setCornerRadius(dp(16, density));
        titleIconBadge.setBackground(titleIconBackground);
        AppCompatImageView titleIcon = new AppCompatImageView(context);
        titleIcon.setImageResource(R.drawable.ic_mdi_thermometer);
        titleIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        titleIcon.setImageTintList(ColorStateList.valueOf(UiStyles.color(context, R.color.oemAccent)));
        int titleIconPx = dp(28, density);
        FrameLayout.LayoutParams titleIconLp = new FrameLayout.LayoutParams(titleIconPx, titleIconPx, Gravity.CENTER);
        titleIconBadge.addView(titleIcon, titleIconLp);
        titleRow.addView(titleIconBadge, new LinearLayout.LayoutParams(dp(52, density), dp(52, density)));

        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(14, density), 0, 0, 0);
        TextView title = new TextView(context);
        title.setText(R.string.climate_auto_title);
        UiStyles.setTextSize(title, R.dimen.text_size_large);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setTypeface(null, Typeface.BOLD);
        heading.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText(R.string.climate_auto_subtitle);
        UiStyles.setTextSize(subtitle, R.dimen.text_size_small);
        subtitle.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
        if (compactLayout) {
            LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subtitleLp.topMargin = dp(8, density);
            subtitleLp.leftMargin = dp(4, density);
            subtitleLp.rightMargin = dp(4, density);
            root.addView(titleRow);
            root.addView(subtitle, subtitleLp);
        } else {
            heading.addView(subtitle, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        titleRow.addView(heading, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (!compactLayout) {
            root.addView(titleRow);
        }

        // Live Sensor Status Card
        LinearLayout statusCard = new LinearLayout(context);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setPadding(dp(18, density), dp(16, density), dp(18, density), dp(16, density));
        statusCard.setBackground(createCardBackground(
                UiStyles.color(context, R.color.automationCardSurface),
                UiStyles.color(context, R.color.automationCardStroke), density, 20));
        statusCard.setElevation(dp(2, density));
        LinearLayout.LayoutParams statusCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusCardLp.topMargin = dp(20, density);
        root.addView(statusCard, statusCardLp);

        AppCompatImageView statusIcon = new AppCompatImageView(context);
        statusIcon.setImageResource(R.drawable.ic_mdi_thermometer);
        statusIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        statusIcon.setImageTintList(ColorStateList.valueOf(UiStyles.color(context, R.color.oemAccent)));
        statusCard.addView(statusIcon, new LinearLayout.LayoutParams(dp(30, density), dp(30, density)));

        LinearLayout statusTextColumn = new LinearLayout(context);
        statusTextColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams statusTextLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        statusTextLp.setMargins(dp(14, density), 0, dp(12, density), 0);
        statusCard.addView(statusTextColumn, statusTextLp);
        TextView sensorLabel = new TextView(context);
        sensorLabel.setText(R.string.climate_auto_sensor_label);
        UiStyles.setTextSize(sensorLabel, R.dimen.text_size_tiny);
        sensorLabel.setTypeface(null, Typeface.BOLD);
        sensorLabel.setLetterSpacing(0.08f);
        sensorLabel.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
        statusTextColumn.addView(sensorLabel);

        liveStatusView = new TextView(context);
        UiStyles.setTextSize(liveStatusView, R.dimen.text_size_medium);
        liveStatusView.setTypeface(null, Typeface.BOLD);
        liveStatusView.setTextColor(UiStyles.color(context, R.color.automationText));
        LinearLayout.LayoutParams liveStatusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        liveStatusLp.topMargin = dp(4, density);
        statusTextColumn.addView(liveStatusView, liveStatusLp);
        liveStatusIndicator = new View(context);
        GradientDrawable indicatorBackground = new GradientDrawable();
        indicatorBackground.setShape(GradientDrawable.OVAL);
        indicatorBackground.setColor(UiStyles.color(context, R.color.statusWarning));
        liveStatusIndicator.setBackground(indicatorBackground);
        statusCard.addView(liveStatusIndicator, new LinearLayout.LayoutParams(dp(10, density), dp(10, density)));

        // Master Automation Switch Card
        LinearLayout masterCard = new LinearLayout(context);
        masterCard.setOrientation(LinearLayout.VERTICAL);
        masterCard.setPadding(dp(18, density), dp(14, density), dp(18, density), dp(14, density));
        masterCard.setBackground(createCardBackground(
                UiStyles.color(context, R.color.automationCardSurface),
                UiStyles.color(context, R.color.automationCardStroke), density, 20));
        masterCard.setElevation(dp(2, density));
        LinearLayout.LayoutParams masterCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        masterCardLp.topMargin = dp(12, density);
        root.addView(masterCard, masterCardLp);

        LinearLayout masterRow = new LinearLayout(context);
        masterRow.setOrientation(LinearLayout.HORIZONTAL);
        masterRow.setGravity(Gravity.CENTER_VERTICAL);
        masterCard.addView(masterRow);

        LinearLayout masterText = new LinearLayout(context);
        masterText.setOrientation(LinearLayout.VERTICAL);
        masterRow.addView(masterText, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView masterTitle = new TextView(context);
        masterTitle.setText(R.string.climate_auto_master);
        UiStyles.setTextSize(masterTitle, R.dimen.text_size_normal);
        masterTitle.setTypeface(null, Typeface.BOLD);
        masterTitle.setTextColor(UiStyles.color(context, R.color.automationText));
        masterText.addView(masterTitle);
        SwitchCompat masterSwitch = new SwitchCompat(context);
        masterSwitch.setShowText(false);
        masterSwitch.setChecked(ClimateAutomationStore.isMasterEnabled(prefs));
        masterSwitch.setThumbTintList(createSwitchTint(
                UiStyles.color(context, R.color.oemAccent),
                UiStyles.color(context, R.color.outlineMuted)));
        masterSwitch.setTrackTintList(createSwitchTint(
                UiStyles.color(context, R.color.assistStrokeActive),
                UiStyles.color(context, R.color.outline)));
        masterRow.addView(masterSwitch, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        masterSwitch.setOnCheckedChangeListener((button, enabled) -> {
            ClimateAutomationStore.setMasterEnabled(prefs, enabled);
            if (enabled) {
                engine.notifyRulesChanged();
            } else {
                engine.disableAllRules();
            }
            callback.log(enabled
                    ? context.getString(R.string.climate_auto_log_on)
                    : context.getString(R.string.climate_auto_log_off));
        });

        root.addView(buildDualSeatCard(
                R.string.climate_auto_driver_section,
                ClimateAutomationStore.DRIVER_HEAT, R.string.climate_auto_driver_heat, R.drawable.ic_climate_seat_heat,
                ClimateAutomationStore.DRIVER_VENT, R.string.climate_auto_driver_vent, R.drawable.ic_climate_seat_cooling,
                density, compactLayout));

        root.addView(buildDualSeatCard(
                R.string.climate_auto_passenger_section,
                ClimateAutomationStore.PASSENGER_HEAT, R.string.climate_auto_passenger_heat, R.drawable.ic_climate_seat_heat,
                ClimateAutomationStore.PASSENGER_VENT, R.string.climate_auto_passenger_vent, R.drawable.ic_climate_seat_cooling,
                density, compactLayout));

        // SECTION 3: OTHER CONTROLS (CAM & DİREKSİYON) - Direksiyon, Ön Cam, Arka Cam
        root.addView(buildOtherControlsCard(density, compactLayout));

        engine.addListener(this);
        return root;
    }

    /**
     * Sürücü veya Yolcu tarafı için büyük modern ana kart oluşturur.
     * İçinde Isıtma ve Havalandırma kutucukları yan yana (2 sütun) konumlandırılır.
     */
    private LinearLayout buildDualSeatCard(int sectionTitleRes,
                                          String heatId, int heatTitleRes, int heatIconRes,
                                          String ventId, int ventTitleRes, int ventIconRes,
                                          float density, boolean compactLayout) {
        LinearLayout outerCard = new LinearLayout(context);
        outerCard.setOrientation(LinearLayout.VERTICAL);
        outerCard.setPadding(dp(16, density), dp(16, density), dp(16, density), dp(16, density));
        
        GradientDrawable outerBg = new GradientDrawable();
        outerBg.setColor(UiStyles.color(context, R.color.automationPanel));
        outerBg.setCornerRadius(dp(22, density));
        outerBg.setStroke(dp(1, density), UiStyles.color(context, R.color.automationCardStroke));
        outerCard.setBackground(outerBg);
        outerCard.setElevation(dp(2, density));

        LinearLayout.LayoutParams outerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        outerLp.topMargin = dp(16, density);
        outerCard.setLayoutParams(outerLp);

        // Bölüm Başlığı (Örn: Sürücü Tarafı)
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(4, density), 0, dp(4, density), dp(12, density));

        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(context.getString(sectionTitleRes));
        UiStyles.setTextSize(sectionTitle, R.dimen.text_size_normal);
        sectionTitle.setTypeface(null, Typeface.BOLD);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.automationText));
        headerRow.addView(sectionTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView seatBadge = new TextView(context);
        seatBadge.setText(heatId.contains("driver") ? "SÜRÜCÜ" : "YOLCU");
        UiStyles.setTextSize(seatBadge, R.dimen.text_size_tiny);
        seatBadge.setTypeface(null, Typeface.BOLD);
        seatBadge.setTextColor(UiStyles.color(context, R.color.oemAccent));
        seatBadge.setPadding(dp(8, density), dp(3, density), dp(8, density), dp(3, density));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor((UiStyles.color(context, R.color.oemAccent) & 0x00FFFFFF) | 0x22000000);
        badgeBg.setCornerRadius(dp(8, density));
        seatBadge.setBackground(badgeBg);
        headerRow.addView(seatBadge);

        outerCard.addView(headerRow);

        // Yan yana 2 alt kart içeren satır
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        FrameLayout heatCard = buildRuleCard(heatId, heatTitleRes, heatIconRes, density, compactLayout);
        FrameLayout ventCard = buildRuleCard(ventId, ventTitleRes, ventIconRes, density, compactLayout);

        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p1.rightMargin = dp(6, density);
        row.addView(heatCard, p1);

        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p2.leftMargin = dp(6, density);
        row.addView(ventCard, p2);

        outerCard.addView(row);
        return outerCard;
    }

    /**
     * Direksiyon ısıtma, ön cam ve arka cam gibi diğer yardımcı kuralları barındıran kart.
     */
    private LinearLayout buildOtherControlsCard(float density, boolean compactLayout) {
        LinearLayout outerCard = new LinearLayout(context);
        outerCard.setOrientation(LinearLayout.VERTICAL);
        outerCard.setPadding(dp(16, density), dp(16, density), dp(16, density), dp(16, density));

        GradientDrawable outerBg = new GradientDrawable();
        outerBg.setColor(UiStyles.color(context, R.color.automationPanel));
        outerBg.setCornerRadius(dp(22, density));
        outerBg.setStroke(dp(1, density), UiStyles.color(context, R.color.automationCardStroke));
        outerCard.setBackground(outerBg);
        outerCard.setElevation(dp(2, density));

        LinearLayout.LayoutParams outerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        outerLp.topMargin = dp(16, density);
        outerCard.setLayoutParams(outerLp);

        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(R.string.climate_auto_other_section);
        UiStyles.setTextSize(sectionTitle, R.dimen.text_size_normal);
        sectionTitle.setTypeface(null, Typeface.BOLD);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.automationText));
        sectionTitle.setPadding(dp(4, density), 0, dp(4, density), dp(12, density));
        outerCard.addView(sectionTitle);

        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(columnsForWidth(context.getResources().getDisplayMetrics().widthPixels, density));
        grid.addOnLayoutChangeListener((view, left, top, right, bottom,
                                        oldLeft, oldTop, oldRight, oldBottom) -> {
            int columns = columnsForWidth(right - left, density);
            if (columns != grid.getColumnCount()) {
                grid.setColumnCount(columns);
            }
        });
        grid.setUseDefaultMargins(false);

        Object[][] otherSpecs = {
                {ClimateAutomationStore.STEER_HEAT, R.string.climate_auto_steer_heat, R.drawable.ic_climate_steering_heat},
                {ClimateAutomationStore.WINDSHIELD, R.string.climate_auto_windshield, R.drawable.ic_climate_windshield_defrost},
                {ClimateAutomationStore.REAR_DEFROST, R.string.climate_auto_rear_defrost, R.drawable.ic_climate_rear_defrost}
        };

        for (Object[] spec : otherSpecs) {
            String id = (String) spec[0];
            int titleRes = (Integer) spec[1];
            int iconRes = (Integer) spec[2];
            FrameLayout card = buildRuleCard(id, titleRes, iconRes, density, compactLayout);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(6, density), dp(6, density), dp(6, density), dp(6, density));
            grid.addView(card, lp);
        }

        outerCard.addView(grid);
        return outerCard;
    }

    @Override
    public void onClimateStatus(ClimateAutomationEngine.Status status) {
        if (liveStatusView == null) {
            return;
        }
        if (!status.connected) {
            liveStatusView.setText(R.string.climate_auto_status_disconnected);
            setStatusIndicator(R.color.statusError);
            return;
        }
        if (status.outsideTempC == null) {
            liveStatusView.setText(R.string.climate_auto_status_no_temp);
            setStatusIndicator(R.color.statusWarning);
            return;
        }
        liveStatusView.setText(context.getString(R.string.climate_auto_status_temp, status.outsideTempC));
        setStatusIndicator(R.color.statusSuccess);
    }

    private FrameLayout buildRuleCard(String id, int titleRes, int iconRes, float density,
                                      boolean compactLayout) {
        ClimateAutomationStore.Rule rule = ClimateAutomationStore.getRule(prefs, id);
        FrameLayout frame = new FrameLayout(context);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int cardPadding = dp(compactLayout ? 10 : 14, density);
        card.setPadding(cardPadding, cardPadding, cardPadding, dp(compactLayout ? 8 : 12, density));
        applyCardBackground(card, rule.enabled, density);
        card.setElevation(dp(2, density));

        final int accentColor = accentFor(id);
        View accentStrip = new View(context);
        GradientDrawable stripBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{accentColor, (accentColor & 0x00FFFFFF) | 0x33000000});
        stripBg.setCornerRadius(dp(3, density));
        accentStrip.setBackground(stripBg);
        LinearLayout.LayoutParams stripLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4, density));
        stripLp.bottomMargin = dp(8, density);
        card.addView(accentStrip, stripLp);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);

        AppCompatImageView icon = new AppCompatImageView(context);
        icon.setImageResource(getRuleIconResource(id, rule.tempC, iconRes));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconPx = dp(compactLayout ? 36 : 40, density);
        int badgePx = dp(compactLayout ? 64 : 72, density);
        FrameLayout iconBadge = new FrameLayout(context);
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setColor(UiStyles.color(context, R.color.automationIconSurface));
        iconBackground.setCornerRadius(dp(16, density));
        iconBackground.setStroke(dp(1, density), UiStyles.color(context, R.color.automationCardStroke));
        iconBadge.setBackground(iconBackground);
        icon.setImageTintList(null);
        iconBadge.addView(icon, new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER));
        LinearLayout.LayoutParams iconBadgeLp = new LinearLayout.LayoutParams(badgePx, badgePx);
        iconBadgeLp.gravity = Gravity.CENTER_HORIZONTAL;
        iconBadgeLp.bottomMargin = dp(8, density);
        header.addView(iconBadge, iconBadgeLp);

        TextView name = new TextView(context);
        name.setText(titleRes);
        UiStyles.setTextSize(name, R.dimen.text_size_small);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(UiStyles.color(context, R.color.automationText));
        name.setGravity(Gravity.CENTER);
        name.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        name.setMaxLines(2);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        header.addView(name, nameLp);

        SwitchCompat ruleSwitch = new SwitchCompat(context);
        ruleSwitch.setShowText(false);
        ruleSwitch.setChecked(rule.enabled);
        ruleSwitch.setThumbTintList(createSwitchTint(
                UiStyles.color(context, R.color.oemAccent),
                UiStyles.color(context, R.color.outlineMuted)));
        ruleSwitch.setTrackTintList(createSwitchTint(
                UiStyles.color(context, R.color.assistStrokeActive),
                UiStyles.color(context, R.color.outline)));
        LinearLayout switchRow = new LinearLayout(context);
        switchRow.setGravity(Gravity.CENTER);
        switchRow.setPadding(0, dp(6, density), 0, 0);
        switchRow.addView(ruleSwitch, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        header.addView(switchRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(header);

        TextView delayLabel = new TextView(context);
        UiStyles.setTextSize(delayLabel, R.dimen.text_size_tiny);
        delayLabel.setTextColor(UiStyles.color(context, R.color.automationTextMuted));
        delayLabel.setGravity(Gravity.CENTER);
        delayLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        delayLabel.setPadding(0, dp(8, density), 0, 0);
        delayLabel.setText(formatDelay(rule.delayMinutes));
        card.addView(delayLabel);
        card.addView(buildSeekBar(0, ClimateAutomationStore.DELAY_MAX_MINUTES, rule.delayMinutes, progress -> {
            rule.delayMinutes = progress;
            delayLabel.setText(formatDelay(progress));
            saveRuleSilently(id, rule);
        }));

        LinearLayout compareRow = new LinearLayout(context);
        compareRow.setOrientation(LinearLayout.HORIZONTAL);
        compareRow.setGravity(Gravity.CENTER_VERTICAL);
        compareRow.setPadding(0, dp(8, density), 0, 0);

        TextView compareChip = new TextView(context);
        UiStyles.setTextSize(compareChip, R.dimen.text_size_small);
        compareChip.setTypeface(null, Typeface.BOLD);
        compareChip.setPadding(dp(14, density), dp(6, density), dp(14, density), dp(6, density));
        compareChip.setTextColor(accentColor);
        GradientDrawable compareBg = new GradientDrawable();
        compareBg.setColor((accentColor & 0x00FFFFFF) | 0x1A000000);
        compareBg.setCornerRadius(dp(10, density));
        compareBg.setStroke(dp(1, density), (accentColor & 0x00FFFFFF) | 0x66000000);
        compareChip.setBackground(compareBg);
        compareChip.setContentDescription(context.getString(R.string.climate_auto_compare_hint));
        refreshCompareChip(compareChip, rule);
        compareRow.addView(compareChip);

        TextView conditionDesc = new TextView(context);
        UiStyles.setTextSize(conditionDesc, R.dimen.text_size_tiny);
        conditionDesc.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
        conditionDesc.setPadding(dp(10, density), 0, 0, 0);
        conditionDesc.setText(formatConditionDesc(rule));
        compareRow.addView(conditionDesc);

        card.addView(compareRow);

        int tempProgress = rule.tempC - ClimateAutomationStore.TEMP_MIN;
        int tempMax = ClimateAutomationStore.TEMP_MAX - ClimateAutomationStore.TEMP_MIN;
        final SeekBar tempSeek = buildSeekBar(0, tempMax, tempProgress, null);
        card.addView(tempSeek);

        // Çakışma Bilgilendirme Uyarı Metni (Kullanıcı çakışan derece seçerse görünür)
        TextView conflictNote = new TextView(context);
        UiStyles.setTextSize(conflictNote, R.dimen.text_size_tiny);
        conflictNote.setTypeface(null, Typeface.BOLD);
        conflictNote.setTextColor(0xFFFF4D4F); // Belirgin kırmızı/turuncu uyarı
        conflictNote.setPadding(0, dp(6, density), 0, 0);
        conflictNote.setVisibility(View.GONE);
        card.addView(conflictNote);
        conflictNotes.put(id, conflictNote);

        // Karşılaştırma koşulu tıklandığında kontrol
        compareChip.setOnClickListener(v -> {
            int newCompare = (rule.compare + 1) % 4;
            ClimateAutomationStore.Rule testRule = new ClimateAutomationStore.Rule();
            testRule.enabled = rule.enabled;
            testRule.tempC = rule.tempC;
            testRule.compare = newCompare;
            testRule.delayMinutes = rule.delayMinutes;
            testRule.level = rule.level;

            if (rule.enabled && ClimateAutomationStore.willConflict(id, testRule, prefs)) {
                // Çakışma var: Uyarı göster ve ayarı uygulama!
                showConflictRejectedWarning(id);
                return;
            }
            rule.compare = newCompare;
            refreshCompareChip(compareChip, rule);
            conditionDesc.setText(formatConditionDesc(rule));
            saveRuleSilently(id, rule);
        });

        // SeekBar değiştiğinde kontrol:
        tempSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int originalTemp = rule.tempC;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int prospectiveTemp = ClimateAutomationStore.TEMP_MIN + progress;
                    refreshCompareChip(compareChip, prospectiveTemp, rule.compare);
                    conditionDesc.setText(formatConditionDesc(prospectiveTemp, rule.compare));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                originalTemp = rule.tempC;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int newTemp = ClimateAutomationStore.TEMP_MIN + seekBar.getProgress();
                ClimateAutomationStore.Rule testRule = new ClimateAutomationStore.Rule();
                testRule.enabled = rule.enabled;
                testRule.tempC = newTemp;
                testRule.compare = rule.compare;
                testRule.delayMinutes = rule.delayMinutes;
                testRule.level = rule.level;

                if (rule.enabled && ClimateAutomationStore.willConflict(id, testRule, prefs)) {
                    // Çakışma var: Değiştirilen ayar uygulanmaz, eski dereceye geri çekilir ve uyarı verilir!
                    seekBar.setProgress(originalTemp - ClimateAutomationStore.TEMP_MIN);
                    refreshCompareChip(compareChip, originalTemp, rule.compare);
                    conditionDesc.setText(formatConditionDesc(originalTemp, rule.compare));
                    showConflictRejectedWarning(id);
                    return;
                }

                rule.tempC = newTemp;
                icon.setImageResource(getRuleIconResource(id, rule.tempC, iconRes));
                refreshCompareChip(compareChip, rule);
                conditionDesc.setText(formatConditionDesc(rule));
                saveRuleSilently(id, rule);
            }
        });

        TextView levelLabel = null;
        SeekBar levelSeek = null;
        if (ClimateAutomationStore.usesHeatLevel(id)) {
            levelLabel = new TextView(context);
            UiStyles.setTextSize(levelLabel, R.dimen.text_size_tiny);
            levelLabel.setTextColor(UiStyles.color(context, R.color.automationTextMuted));
            levelLabel.setGravity(Gravity.CENTER);
            levelLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            levelLabel.setPadding(0, dp(8, density), 0, 0);
            levelLabel.setText(context.getString(R.string.climate_auto_level, rule.level));
            levelLabel.setVisibility(rule.enabled ? View.VISIBLE : View.GONE);
            card.addView(levelLabel);
            TextView levelLabelRef = levelLabel;
            levelSeek = buildSeekBar(ClimateAutomationStore.LEVEL_MIN, ClimateAutomationStore.LEVEL_MAX, rule.level, progress -> {
                rule.level = progress;
                levelLabelRef.setText(context.getString(R.string.climate_auto_level, progress));
                saveRuleSilently(id, rule);
            });
            levelSeek.setVisibility(rule.enabled ? View.VISIBLE : View.GONE);
            card.addView(levelSeek);
        }
        final TextView shownLevelLabel = levelLabel;
        final SeekBar shownLevelSeek = levelSeek;

        ruleSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (enabled) {
                // Açılmak istenen kural mevcut açık eşiyle çakışıyor mu?
                if (ClimateAutomationStore.willConflict(id, rule, prefs)) {
                    // Diğer kural zaten açık ve dereceler çakışıyor: Bu kural açılamaz!
                    ruleSwitch.setChecked(false);
                    showConflictRejectedWarning(id);
                    return;
                }
            }
            rule.enabled = enabled;
            applyCardBackground(card, rule.enabled, density);
            int levelVisibility = enabled ? View.VISIBLE : View.GONE;
            if (shownLevelLabel != null) {
                shownLevelLabel.setVisibility(levelVisibility);
            }
            if (shownLevelSeek != null) {
                shownLevelSeek.setVisibility(levelVisibility);
            }
            ClimateAutomationStore.putRule(prefs, id, rule);
            if (enabled) {
                engine.notifyRuleChanged(id);
            } else {
                engine.disableRule(id);
            }
            callback.log(context.getString(titleRes) + ": "
                    + (rule.enabled
                    ? context.getString(R.string.assist_status_active)
                    : context.getString(R.string.assist_status_not_set)));
        });

        frame.addView(card);
        return frame;
    }

    private SeekBar buildSeekBar(int min, int max, int value, ProgressConsumer consumer) {
        float density = context.getResources().getDisplayMetrics().density;
        SeekBar bar = new SeekBar(context);
        bar.setMin(min);
        bar.setMax(max);
        bar.setProgress(value);
        int accent = UiStyles.color(context, R.color.oemAccent);
        int track = dp(8, density);
        int thumbSize = dp(28, density);
        bar.setMinHeight(track);
        bar.setMaxHeight(track);
        bar.setSplitTrack(false);
        bar.setProgressTintList(ColorStateList.valueOf(accent));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(
                UiStyles.color(context, R.color.outline)));
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(accent);
        thumb.setSize(thumbSize, thumbSize);
        bar.setThumb(thumb);
        bar.setThumbOffset(thumbSize / 2);
        int side = thumbSize / 2;
        int vertical = dp(12, density);
        bar.setPadding(side, vertical, side, vertical);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52, density));
        bar.setLayoutParams(lp);
        if (consumer != null) {
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        consumer.accept(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }
        return bar;
    }

    private int getRuleIconResource(String id, int thresholdC, int defaultIconRes) {
        if (!ClimateAutomationStore.CLIMATE.equals(id)) {
            return defaultIconRes;
        }
        return thresholdC > 25 ? R.drawable.ic_climate_auto_hot : R.drawable.ic_climate_auto_cold;
    }

    private void applyCardBackground(LinearLayout card, boolean enabled, float density) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18, density));
        if (enabled) {
            bg.setColor(UiStyles.color(context, R.color.automationCardSurface));
            bg.setStroke(dp(2, density), UiStyles.color(context, R.color.oemAccent));
        } else {
            bg.setColor(UiStyles.color(context, R.color.automationCardSurface));
            bg.setStroke(dp(1, density), UiStyles.color(context, R.color.automationCardStroke));
        }
        card.setBackground(bg);
    }

    private GradientDrawable createCardBackground(int fill, int stroke, float density, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(radiusDp, density));
        background.setStroke(dp(1, density), stroke);
        return background;
    }

    private ColorStateList createSwitchTint(int checked, int unchecked) {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{checked, unchecked});
    }

    private void setStatusIndicator(int colorRes) {
        if (liveStatusIndicator != null) {
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(UiStyles.color(context, colorRes));
            liveStatusIndicator.setBackground(background);
        }
    }

    private int dp(int value, float density) {
        return Math.round(value * density);
    }

    private int columnsForWidth(int widthPx, float density) {
        int widthDp = Math.round(widthPx / density);
        if (widthDp < 600) {
            return 2;
        }
        if (widthDp < 900) {
            return 3;
        }
        return 3;
    }

    private void refreshCompareChip(TextView chip, ClimateAutomationStore.Rule rule) {
        chip.setText(compareSymbol(rule.compare) + " " + rule.tempC + "°");
    }

    private void refreshCompareChip(TextView chip, int tempC, int compare) {
        chip.setText(compareSymbol(compare) + " " + tempC + "°");
    }

    private String formatConditionDesc(ClimateAutomationStore.Rule rule) {
        return formatConditionDesc(rule.tempC, rule.compare);
    }

    private String formatConditionDesc(int tempC, int compare) {
        switch (compare) {
            case ClimateAutomationStore.CMP_LT:
                return tempC + "°C altındayken aç";
            case ClimateAutomationStore.CMP_LTE:
                return tempC + "°C ve altındayken aç";
            case ClimateAutomationStore.CMP_GT:
                return tempC + "°C üzerindeyken aç";
            case ClimateAutomationStore.CMP_GTE:
                return tempC + "°C ve üzerindeyken aç";
            default:
                return tempC + "°C";
        }
    }

    private String compareSymbol(int compare) {
        switch (compare) {
            case ClimateAutomationStore.CMP_LTE:
                return "≤";
            case ClimateAutomationStore.CMP_GT:
                return ">";
            case ClimateAutomationStore.CMP_GTE:
                return "≥";
            case ClimateAutomationStore.CMP_LT:
            default:
                return "<";
        }
    }

    private String formatDelay(int minutes) {
        if (minutes <= 0) {
            return context.getString(R.string.climate_auto_delay_off);
        }
        return context.getString(R.string.climate_auto_delay, minutes);
    }

    private String formatTemp(int tempC) {
        return context.getString(R.string.climate_auto_temp, tempC);
    }

    private void saveRuleSilently(String id, ClimateAutomationStore.Rule rule) {
        ClimateAutomationStore.putRule(prefs, id, rule);
        engine.notifyRuleChanged(id);
    }

    /**
     * Kullanıcı çakışan bir ayar yaptığında uyarı metni gösterir ve Toast basar.
     */
    private void showConflictRejectedWarning(String id) {
        String msg = context.getString(R.string.climate_auto_conflict_rejected);
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();

        final TextView note = conflictNotes.get(id);
        if (note == null) {
            return;
        }
        note.setText("⚠ " + msg);
        note.setVisibility(View.VISIBLE);
        uiHandler.removeCallbacksAndMessages(note);
        uiHandler.postAtTime(() -> note.setVisibility(View.GONE),
                note, android.os.SystemClock.uptimeMillis() + 4500L);
    }

    /** Isıtma kartları kırmızı, havalandırma kartları mavi tonda; diğerleri marka rengi. */
    private int accentFor(String id) {
        if (ClimateAutomationStore.DRIVER_HEAT.equals(id)
                || ClimateAutomationStore.PASSENGER_HEAT.equals(id)
                || ClimateAutomationStore.STEER_HEAT.equals(id)) {
            return 0xFFEF4444; // Modern canlı kırmızı
        }
        if (ClimateAutomationStore.DRIVER_VENT.equals(id)
                || ClimateAutomationStore.PASSENGER_VENT.equals(id)) {
            return 0xFF38BDF8; // Modern canlı açık mavi / cyan
        }
        return UiStyles.color(context, R.color.oemAccent);
    }

    private interface ProgressConsumer {
        void accept(int progress);
    }
}
