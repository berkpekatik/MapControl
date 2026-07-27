package com.mapcontrol.ui.builder;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.VehicleMetricsFormatter;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;

/**
 * Araç bilgisi — {@link VehicleMetricsRepository} üzerinden canlı VDBus verisi.
 */
public class VehicleInfoTabBuilder implements VehicleMetricsRepository.Listener {

    public interface VehicleInfoCallback {
        void log(String message);
    }

    private static final String ENGMODE_PACKAGE = "com.desaysv.engmode";
    private static final String ENGMODE_MAIN_ACTIVITY = "com.desaysv.engmode.MainActivity";

    private final Context uiContext;
    private final Context appContext;
    private final VehicleMetricsRepository repository;
    private final VehicleMetricsFormatter formatter;
    private final VehicleInfoCallback callback;

    private ScrollView scrollView;
    private LinearLayout contentRoot;
    private TextView statusText;
    private LinearLayout rowsContainer;

    private volatile boolean tabVisible;

    public VehicleInfoTabBuilder(
            Context context,
            VehicleMetricsRepository repository,
            VehicleInfoCallback callback) {
        this.uiContext = context;
        this.appContext = context.getApplicationContext();
        this.repository = repository;
        this.formatter = new VehicleMetricsFormatter(context);
        this.callback = callback;
        build();
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    /** Light/dark geçişinde içeriği taze renklerle yeniden kurar. */
    public void rebuild() {
        build();
    }

    public void startListening() {
        tabVisible = true;
        statusText.setText(R.string.vehicle_info_loading);
        rowsContainer.removeAllViews();
        repository.addListener(this);
        repository.refresh();
    }

    public void stopListening() {
        tabVisible = false;
        repository.removeListener(this);
    }

    public void release() {
        stopListening();
    }

    public void refreshSnapshot() {
        statusText.setText(R.string.vehicle_info_loading);
        repository.refresh();
    }

    @Override
    public void onMetricsUpdated(VehicleMetricsSnapshot snapshot) {
        if (!tabVisible) {
            return;
        }
        repaintRows(snapshot);
        statusText.setText(snapshot.connected
                ? R.string.vehicle_info_listening
                : R.string.vehicle_info_unavailable);
    }

    private void build() {
        scrollView = new ScrollView(appContext);
        scrollView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        scrollView.setFillViewport(true);

        LinearLayout outer = new LinearLayout(appContext);
        outer.setOrientation(LinearLayout.VERTICAL);
        int margin = UiStyles.dimenPx(appContext, R.dimen.oem_card_margin);
        outer.setPadding(margin, margin, margin, margin);

        contentRoot = new LinearLayout(appContext);
        contentRoot.setOrientation(LinearLayout.VERTICAL);
        int inner = UiStyles.dimenPx(appContext, R.dimen.oem_card_inner_padding);
        contentRoot.setPadding(inner, inner, inner, inner);
        UiStyles.setGlassCardBackground(contentRoot);

        TextView title = new TextView(appContext);
        title.setText(R.string.vehicle_info_title);
        title.setTextSize(20);
        title.setTextColor(UiStyles.color(appContext, R.color.textPrimary));
        title.setTypeface(null, Typeface.BOLD);
        contentRoot.addView(title);

        TextView intro = new TextView(appContext);
        intro.setText(R.string.vehicle_info_intro);
        intro.setTextSize(14);
        intro.setTextColor(UiStyles.color(appContext, R.color.textHint));
        intro.setPadding(0, UiStyles.dimenPx(appContext, R.dimen.spacing_small), 0,
                UiStyles.dimenPx(appContext, R.dimen.spacing_medium));
        contentRoot.addView(intro);

        statusText = new TextView(appContext);
        statusText.setText(R.string.vehicle_info_tab_idle);
        statusText.setTextSize(14);
        statusText.setTextColor(UiStyles.color(appContext, R.color.textPrimary87));
        statusText.setPadding(0, 0, 0, UiStyles.dimenPx(appContext, R.dimen.spacing_small));
        contentRoot.addView(statusText);

        LinearLayout actionRow = new LinearLayout(appContext);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        int btnGap = UiStyles.dimenPx(appContext, R.dimen.spacing_small);

        Button refreshBtn = createActionButton(R.string.vehicle_info_refresh, R.color.buttonPrimary);
        refreshBtn.setOnClickListener(v -> refreshSnapshot());
        actionRow.addView(refreshBtn, weightedButtonLp(0, btnGap));

        Button engModeBtn = createActionButton(R.string.vehicle_info_open_engmode, R.color.buttonSecondaryMuted);
        engModeBtn.setOnClickListener(v -> launchEngModeMenu());
        actionRow.addView(engModeBtn, weightedButtonLp(btnGap, 0));

        LinearLayout.LayoutParams actionRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionRowLp.bottomMargin = UiStyles.dimenPx(appContext, R.dimen.spacing_medium);
        contentRoot.addView(actionRow, actionRowLp);

        rowsContainer = new LinearLayout(appContext);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        contentRoot.addView(rowsContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        outer.addView(contentRoot, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private Button createActionButton(int labelResId, int backgroundColorResId) {
        Button button = new Button(appContext);
        button.setText(labelResId);
        button.setTextSize(14);
        button.setTextColor(UiStyles.color(appContext, R.color.textPrimary));
        button.setTypeface(null, Typeface.BOLD);
        UiStyles.styleOemButton(button, UiStyles.color(appContext, backgroundColorResId));
        int padH = UiStyles.dimenPx(appContext, R.dimen.spacing_medium);
        button.setPadding(padH, 12, padH, 12);
        return button;
    }

    private static LinearLayout.LayoutParams weightedButtonLp(int marginStart, int marginEnd) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(marginStart);
        lp.setMarginEnd(marginEnd);
        return lp;
    }

    private void launchEngModeMenu() {
        try {
            PackageManager pm = uiContext.getPackageManager();
            pm.getPackageInfo(ENGMODE_PACKAGE, 0);
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(ENGMODE_PACKAGE, ENGMODE_MAIN_ACTIVITY));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            uiContext.startActivity(intent);
            log("EngMode menüsü açıldı");
        } catch (PackageManager.NameNotFoundException e) {
            log(appContext.getString(R.string.vehicle_info_engmode_launch_fail));
        } catch (SecurityException e) {
            log(appContext.getString(R.string.vehicle_info_engmode_launch_denied) + ": " + e.getMessage());
        } catch (Exception e) {
            log(appContext.getString(R.string.vehicle_info_engmode_launch_fail) + ": " + e.getMessage());
        }
    }

    private void log(String message) {
        if (callback != null) {
            callback.log(message);
        }
    }

    private void repaintRows(VehicleMetricsSnapshot snapshot) {
        rowsContainer.removeAllViews();

        if (!snapshot.connected) {
            addRow(appContext.getString(R.string.vehicle_info_hint_title),
                    appContext.getString(R.string.vehicle_info_hint_body));
            return;
        }

        addSection(appContext.getString(R.string.vehicle_info_section_distance));
        addRow(appContext.getString(R.string.vehicle_info_odo),
                formatter.formatKm(repository.getCombined(ReadOnlyID.ID_GRAND_TOTAL_KM)));
        addRow(appContext.getString(R.string.vehicle_info_trip),
                formatter.formatDeciKm(repository.getCombined(ReadOnlyID.ID_TRIP)));
        addRow(appContext.getString(R.string.vehicle_info_trip_after_reset),
                formatter.formatKm(repository.getCombined(ReadOnlyID.ID_GRAND_TOTAL_KM_AFTER_CLEAR)));
        addRow(appContext.getString(R.string.vehicle_info_trip_since_start),
                formatter.formatKm(repository.getCombined(ReadOnlyID.ID_GRAND_TOTAL_KM_AFTER_RUNNING)));

        addSection(appContext.getString(R.string.vehicle_info_section_fuel));
        addRow(appContext.getString(R.string.vehicle_info_fuel_level),
                formatter.formatFuelLevel(repository.getCombined(ReadOnlyID.ID_FUEL_PERCENT)));
        addRow(appContext.getString(R.string.vehicle_info_range_remaining),
                formatter.formatKm(repository.getCombined(ReadOnlyID.ID_ENDURANCE_KM)));
        addRow(appContext.getString(R.string.vehicle_info_range_total),
                formatter.formatKm(repository.getCombined(ReadOnlyID.ID_TOTAL_RANGE)));
        addRow(appContext.getString(R.string.vehicle_info_fuel_used),
                formatter.formatFuelUsedMl(repository.getCombined(ReadOnlyID.ID_SUM_FUEL)));
        addRow(appContext.getString(R.string.vehicle_info_low_fuel),
                formatter.formatLowFuel(repository.getCombined(ReadOnlyID.ID_LOW_FUEL_WARNING)));
        addRow(appContext.getString(R.string.vehicle_info_afc),
                formatter.formatConsumption(repository.getCombined(ReadOnlyID.ID_AVG_FUEL_CONS)));
        addRow(appContext.getString(R.string.vehicle_info_afc_trip),
                formatter.formatConsumption(repository.getCombined(ReadOnlyID.ID_AVERAGE_FUEL_CONS_AFTER_RUNNING)));

        addSection(appContext.getString(R.string.vehicle_info_section_motor));
        addRow(appContext.getString(R.string.vehicle_info_eng_spd),
                formatter.formatRpm(repository.getCombined(ReadOnlyID.ID_ENGINE_RPM)));
        addRow(appContext.getString(R.string.vehicle_info_col_tmp),
                formatter.formatTemperature(repository.getCombined(ReadOnlyID.ID_WATER_TEMPERATURE)));
        addRow(appContext.getString(R.string.vehicle_info_battery),
                formatter.formatVoltage(repository.getCombined(ReadOnlyID.ID_IBS_VOLTAGE)));
        addRow(appContext.getString(R.string.vehicle_info_act_speed),
                formatter.formatSpeed(repository.getCombined(ReadOnlyID.ID_CAR_SPEED)));
        addRow(appContext.getString(R.string.vehicle_info_dis_speed),
                formatter.formatSpeed(repository.getCombined(ReadOnlyID.ID_SPEED_GAUGE_DISPLAY)));
        addRow(appContext.getString(R.string.vehicle_info_gear),
                formatter.formatGear(repository.getCombined(ReadOnlyID.ID_GEARBOX_STATE)));
        addRow(appContext.getString(R.string.vehicle_info_power_mode),
                formatter.formatPowerMode(repository.getCombined(ReadOnlyID.ID_SYSTEM_POWER_MODE)));
        addRow(appContext.getString(R.string.vehicle_info_avg_speed),
                formatter.formatSpeed(repository.getCombined(ReadOnlyID.ID_AVG_SPEED)));

        addSection(appContext.getString(R.string.vehicle_info_section_etm_note));
        addRow(appContext.getString(R.string.vehicle_info_etm_only_title),
                appContext.getString(R.string.vehicle_info_etm_only_body));
    }

    private void addSection(String title) {
        TextView tv = new TextView(appContext);
        tv.setText(title);
        tv.setTextSize(17);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(UiStyles.color(appContext, R.color.textPrimary));
        int top = UiStyles.dimenPx(appContext, R.dimen.spacing_medium);
        tv.setPadding(0, top, 0, UiStyles.dimenPx(appContext, R.dimen.spacing_small));
        rowsContainer.addView(tv);
    }

    private void addRow(String label, String value) {
        LinearLayout row = new LinearLayout(appContext);
        row.setOrientation(LinearLayout.VERTICAL);
        int padV = UiStyles.dimenPx(appContext, R.dimen.spacing_small);
        row.setPadding(0, padV, 0, padV);

        TextView labelTv = new TextView(appContext);
        labelTv.setText(label);
        labelTv.setTextSize(14);
        labelTv.setTextColor(UiStyles.color(appContext, R.color.textHint));
        row.addView(labelTv);

        TextView valueTv = new TextView(appContext);
        valueTv.setText(value);
        valueTv.setTextSize(18);
        valueTv.setTextColor(UiStyles.color(appContext, R.color.textPrimary));
        valueTv.setGravity(Gravity.START);
        row.addView(valueTv);

        rowsContainer.addView(row);
    }
}
