package com.mapcontrol.ui.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.VehicleMetricsFormatter;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;
import com.mapcontrol.weather.OpenMeteoWeatherClient;
import com.mapcontrol.weather.OpenMeteoWeatherLabels;
import com.mapcontrol.weather.OpenMeteoWeatherSnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Launcher Lite modu — kurumsal saat, hava durumu ve sürüş özeti.
 */
public final class LauncherLitePanelView extends LinearLayout implements OpenMeteoWeatherClient.Listener {

    private final VehicleMetricsFormatter formatter;
    private final OpenMeteoWeatherClient weatherClient;

    private TextClock clockView;
    private TextView dateView;
    private TextView weatherGlyphView;
    private TextView weatherTempView;
    private TextView weatherLabelView;
    private TextView statSpeedValueView;
    private TextView statSpeedUnitView;
    private TextView statGearValueView;
    private TextView statGearLabelView;
    private TextView statFuelValueView;
    private TextView statFuelLabelView;
    private View weatherDivider;
    private View statsDivider;

    private final Runnable dateRefreshRunnable = this::refreshDateLabel;

    public LauncherLitePanelView(@NonNull Context context) {
        super(context);
        formatter = new VehicleMetricsFormatter(context);
        weatherClient = new OpenMeteoWeatherClient(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        int pad = UiStyles.dimenPx(context, R.dimen.spacing_medium);
        setPadding(pad, pad, pad, pad);
        buildContent();
        weatherClient.setListener(this);
    }

    private void buildContent() {
        removeAllViews();

        clockView = new TextClock(getContext());
        clockView.setFormat24Hour("HH:mm");
        clockView.setFormat12Hour("HH:mm");
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.launcher_lite_clock));
        clockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clockView.setTextColor(UiStyles.color(getContext(), R.color.textPrimary));
        clockView.setGravity(Gravity.CENTER);
        clockView.setIncludeFontPadding(false);
        clockView.setLetterSpacing(0.04f);
        addView(clockView, matchWidthWrap());

        dateView = new TextView(getContext());
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.launcher_lite_date));
        dateView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        dateView.setTextColor(UiStyles.color(getContext(), R.color.textMuted));
        dateView.setGravity(Gravity.CENTER);
        dateView.setIncludeFontPadding(false);
        dateView.setLetterSpacing(0.02f);
        LinearLayout.LayoutParams dateLp = matchWidthWrap();
        dateLp.topMargin = UiStyles.dimenPx(getContext(), R.dimen.spacing_tiny);
        addView(dateView, dateLp);

        weatherDivider = createDivider();
        addView(weatherDivider, dividerLp());

        LinearLayout weatherRow = new LinearLayout(getContext());
        weatherRow.setOrientation(HORIZONTAL);
        weatherRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams weatherRowLp = matchWidthWrap();
        weatherRowLp.topMargin = UiStyles.dimenPx(getContext(), R.dimen.spacing_small);
        addView(weatherRow, weatherRowLp);

        weatherGlyphView = new TextView(getContext());
        weatherGlyphView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.launcher_lite_weather_glyph));
        weatherGlyphView.setTypeface(Typeface.DEFAULT);
        weatherGlyphView.setIncludeFontPadding(false);
        weatherGlyphView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams glyphLp = wrapContent();
        glyphLp.setMarginEnd(UiStyles.dimenPx(getContext(), R.dimen.spacing_small));
        weatherRow.addView(weatherGlyphView, glyphLp);

        weatherTempView = new TextView(getContext());
        weatherTempView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.launcher_lite_weather_temp));
        weatherTempView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        weatherTempView.setTextColor(UiStyles.color(getContext(), R.color.textPrimary));
        weatherTempView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams tempLp = wrapContent();
        tempLp.setMarginEnd(UiStyles.dimenPx(getContext(), R.dimen.spacing_small));
        weatherRow.addView(weatherTempView, tempLp);

        weatherLabelView = new TextView(getContext());
        weatherLabelView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.launcher_lite_weather_label));
        weatherLabelView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        weatherLabelView.setTextColor(UiStyles.color(getContext(), R.color.textSecondary));
        weatherLabelView.setIncludeFontPadding(false);
        weatherRow.addView(weatherLabelView, wrapContent());

        statsDivider = createDivider();
        addView(statsDivider, dividerLp());

        LinearLayout statsRow = new LinearLayout(getContext());
        statsRow.setOrientation(HORIZONTAL);
        statsRow.setGravity(Gravity.CENTER);
        statsRow.setWeightSum(3f);
        LinearLayout.LayoutParams statsRowLp = matchWidthWrap();
        statsRowLp.topMargin = UiStyles.dimenPx(getContext(), R.dimen.spacing_small);
        addView(statsRow, statsRowLp);

        LinearLayout speedCol = buildStatColumn(
                getContext().getString(R.string.launcher_lite_stat_speed_unit));
        statSpeedValueView = (TextView) speedCol.getChildAt(0);
        statSpeedUnitView = (TextView) speedCol.getChildAt(1);
        statsRow.addView(speedCol, weightedColLp());

        LinearLayout gearCol = buildStatColumn(
                getContext().getString(R.string.launcher_lite_stat_gear));
        statGearValueView = (TextView) gearCol.getChildAt(0);
        statGearLabelView = (TextView) gearCol.getChildAt(1);
        statsRow.addView(gearCol, weightedColLp());

        LinearLayout fuelCol = buildStatColumn(
                getContext().getString(R.string.launcher_lite_stat_fuel));
        statFuelValueView = (TextView) fuelCol.getChildAt(0);
        statFuelLabelView = (TextView) fuelCol.getChildAt(1);
        statsRow.addView(fuelCol, weightedColLp());

        refreshDateLabel();
        showWeatherPlaceholder();
    }

    private LinearLayout buildStatColumn(String label) {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(VERTICAL);
        col.setGravity(Gravity.CENTER);

        TextView value = new TextView(getContext());
        value.setText("—");
        value.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.launcher_lite_stat_value));
        value.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        value.setTextColor(UiStyles.color(getContext(), R.color.textPrimary));
        value.setGravity(Gravity.CENTER);
        value.setIncludeFontPadding(false);
        col.addView(value, matchWidthWrap());

        TextView caption = new TextView(getContext());
        caption.setText(label);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.launcher_lite_stat_label));
        caption.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        caption.setTextColor(UiStyles.color(getContext(), R.color.textMuted));
        caption.setGravity(Gravity.CENTER);
        caption.setIncludeFontPadding(false);
        caption.setAllCaps(true);
        caption.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams captionLp = matchWidthWrap();
        captionLp.topMargin = UiStyles.dimenPx(getContext(), R.dimen.spacing_tiny);
        col.addView(caption, captionLp);
        return col;
    }

    private View createDivider() {
        View divider = new View(getContext());
        divider.setBackgroundColor(UiStyles.color(getContext(), R.color.outlineSubtle));
        return divider;
    }

    private LinearLayout.LayoutParams dividerLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiStyles.dimenPx(getContext(), R.dimen.launcher_lite_divider_height));
        lp.topMargin = UiStyles.dimenPx(getContext(), R.dimen.spacing_medium);
        return lp;
    }

    private static LinearLayout.LayoutParams matchWidthWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams wrapContent() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightedColLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f);
        int gap = UiStyles.dimenPx(getContext(), R.dimen.spacing_tiny);
        lp.setMarginStart(gap);
        lp.setMarginEnd(gap);
        return lp;
    }

    public void start() {
        removeCallbacks(dateRefreshRunnable);
        postDelayed(dateRefreshRunnable, millisUntilNextMinute());
        weatherClient.start();
    }

    public void stop() {
        removeCallbacks(dateRefreshRunnable);
        weatherClient.stop();
    }

    public void destroy() {
        stop();
        weatherClient.destroy();
    }

    public void bindVehicleMetrics(@NonNull VehicleMetricsSnapshot snapshot) {
        if (statSpeedValueView == null) {
            return;
        }
        int speed = snapshot.preferredSpeed();
        statSpeedValueView.setText(speed > 0
                ? String.format(Locale.getDefault(), "%d", speed)
                : "0");
        statGearValueView.setText(formatter.formatDashboardGearShort(snapshot));
        statFuelValueView.setText(formatFuelPercent(snapshot));
    }

    public void reapplyTheme() {
        int primary = UiStyles.color(getContext(), R.color.textPrimary);
        int muted = UiStyles.color(getContext(), R.color.textMuted);
        int secondary = UiStyles.color(getContext(), R.color.textSecondary);
        if (clockView != null) {
            clockView.setTextColor(primary);
        }
        if (dateView != null) {
            dateView.setTextColor(muted);
        }
        if (weatherTempView != null) {
            weatherTempView.setTextColor(primary);
        }
        if (weatherLabelView != null) {
            weatherLabelView.setTextColor(secondary);
        }
        if (statSpeedValueView != null) {
            statSpeedValueView.setTextColor(primary);
        }
        if (statGearValueView != null) {
            statGearValueView.setTextColor(primary);
        }
        if (statFuelValueView != null) {
            statFuelValueView.setTextColor(primary);
        }
        applyCaptionColors(muted);
        if (weatherDivider != null) {
            weatherDivider.setBackgroundColor(UiStyles.color(getContext(), R.color.outlineSubtle));
        }
        if (statsDivider != null) {
            statsDivider.setBackgroundColor(UiStyles.color(getContext(), R.color.outlineSubtle));
        }
        OpenMeteoWeatherSnapshot cached = weatherClient.getLastSnapshot();
        if (cached != null) {
            applyWeather(cached);
        }
    }

    private void applyCaptionColors(int muted) {
        if (statSpeedUnitView != null) {
            statSpeedUnitView.setTextColor(muted);
        }
        if (statGearLabelView != null) {
            statGearLabelView.setTextColor(muted);
        }
        if (statFuelLabelView != null) {
            statFuelLabelView.setTextColor(muted);
        }
    }

    @Override
    public void onWeatherUpdated(@NonNull OpenMeteoWeatherSnapshot snapshot) {
        applyWeather(snapshot);
    }

    @Override
    public void onWeatherFailed() {
        showWeatherPlaceholder();
    }

    private void applyWeather(@NonNull OpenMeteoWeatherSnapshot snapshot) {
        if (weatherGlyphView == null) {
            return;
        }
        weatherGlyphView.setText(OpenMeteoWeatherLabels.glyphFor(snapshot.weatherCode, snapshot.day));
        weatherTempView.setText(String.format(
                Locale.getDefault(),
                "%.0f°C",
                snapshot.temperatureC));
        weatherLabelView.setText(OpenMeteoWeatherLabels.labelFor(
                getContext(),
                snapshot.weatherCode,
                snapshot.day));
    }

    private void showWeatherPlaceholder() {
        if (weatherGlyphView == null) {
            return;
        }
        weatherGlyphView.setText("\u2014");
        weatherTempView.setText(getContext().getString(R.string.launcher_lite_weather_loading));
        weatherLabelView.setText("");
    }

    private void refreshDateLabel() {
        if (dateView == null) {
            return;
        }
        Calendar calendar = Calendar.getInstance();
        Locale locale = Locale.getDefault();
        String pattern = DateFormat.getBestDateTimePattern(locale, "EEEE d MMMM");
        dateView.setText(new SimpleDateFormat(pattern, locale).format(calendar.getTime()));
        removeCallbacks(dateRefreshRunnable);
        postDelayed(dateRefreshRunnable, millisUntilNextMinute());
    }

    private static long millisUntilNextMinute() {
        long now = System.currentTimeMillis();
        return 60_000L - (now % 60_000L) + 250L;
    }

    @NonNull
    private String formatFuelPercent(@NonNull VehicleMetricsSnapshot snapshot) {
        int fuel = snapshot.fuelPercent;
        if (!VehicleMetricsSnapshot.isValid(fuel) || fuel < 0) {
            return "—";
        }
        if (fuel <= 100) {
            return String.format(Locale.getDefault(), "%d%%", fuel);
        }
        return String.format(Locale.getDefault(), "%.1f%%", fuel / 10.0);
    }
}
