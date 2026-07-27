package com.mapcontrol.nav;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.AppLaunchHelper;

/**
 * Cluster ekranında Yandex navigasyon özet kartları.
 * FLAG_NOT_TOUCHABLE — kadranlara dokunuşu engellemez.
 */
public final class YandexClusterNavOverlay {

    public static final String PREF_ENABLED = "yandex_cluster_nav_overlay";
    private static final String PREFS = "MapControlPrefs";
    private static final int LAYOUT_VERSION = 4;
    private static final int TOP_MARGIN_DP = 80;
    private static final int CENTER_GAP_DP = 300;

    private static YandexClusterNavOverlay instance;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WindowManager windowManager;
    private Context displayContext;
    private View overlayRoot;
    private TextView maneuverDistanceView;
    private TextView nextStreetView;
    private TextView maneuverStatusView;
    private TextView etaDistanceValue;
    private TextView etaArrivalValue;
    private TextView etaTimeValue;
    private TextView etaStatusView;
    private LinearLayout etaDistanceRow;
    private LinearLayout etaArrivalRow;
    private LinearLayout etaTimeRow;
    private LinearLayout maneuverCard;
    private LinearLayout etaCard;
    private int appliedLayoutVersion = -1;

    @Nullable
    private YandexNavSnapshot lastApplied;

    private YandexClusterNavOverlay(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized YandexClusterNavOverlay getInstance(Context context) {
        if (instance == null) {
            instance = new YandexClusterNavOverlay(context);
        }
        return instance;
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_ENABLED, enabled).apply();
        YandexClusterNavOverlay overlay = getInstance(context);
        if (!enabled) {
            overlay.hide();
        } else {
            overlay.invalidateCache();
        }
    }

    /** Aynı snapshot tekrar uygulanmasın diye önbelleği temizler (ayar açılınca). */
    public void invalidateCache() {
        mainHandler.post(() -> lastApplied = null);
    }

    /** Zorla yeniden çizim (layout sürümü değişince). */
    public void forceRebuild() {
        mainHandler.post(() -> {
            lastApplied = null;
            if (overlayRoot != null && windowManager != null) {
                try {
                    windowManager.removeView(overlayRoot);
                } catch (Exception ignored) {
                }
                overlayRoot = null;
                clearViewRefs();
                appliedLayoutVersion = -1;
            }
        });
    }

    public void apply(@Nullable YandexNavSnapshot snapshot) {
        mainHandler.post(() -> applyOnMain(snapshot));
    }

    private void applyOnMain(@Nullable YandexNavSnapshot snapshot) {
        if (!isEnabled(appContext)) {
            hideInternal();
            return;
        }
        if (snapshot == null || !snapshot.guidanceActive) {
            hideInternal();
            return;
        }
        if (lastApplied != null && lastApplied.contentEquals(snapshot)) {
            return;
        }
        ensureOverlay();
        if (overlayRoot == null) {
            return;
        }

        boolean hasManeuver = bindManeuverCard(snapshot);
        boolean hasEta = bindEtaCard(snapshot);
        maneuverCard.setVisibility(hasManeuver ? View.VISIBLE : View.GONE);
        etaCard.setVisibility(hasEta ? View.VISIBLE : View.GONE);
        if (!hasManeuver && !hasEta) {
            hideInternal();
            return;
        }
        lastApplied = snapshot;
    }

    private boolean bindManeuverCard(YandexNavSnapshot snapshot) {
        boolean any = false;

        String maneuver = snapshot.formatManeuverLine();
        if (maneuver != null) {
            maneuverDistanceView.setText(maneuver);
            maneuverDistanceView.setVisibility(View.VISIBLE);
            any = true;
        } else {
            maneuverDistanceView.setVisibility(View.GONE);
        }

        if (snapshot.nextStreet != null && !snapshot.nextStreet.isEmpty()) {
            nextStreetView.setText(snapshot.nextStreet);
            nextStreetView.setVisibility(View.VISIBLE);
            any = true;
        } else {
            nextStreetView.setVisibility(View.GONE);
        }

        if (snapshot.statusPanel != null && !snapshot.statusPanel.isEmpty()) {
            maneuverStatusView.setText(snapshot.statusPanel);
            maneuverStatusView.setVisibility(View.VISIBLE);
            any = true;
        } else {
            maneuverStatusView.setVisibility(View.GONE);
        }

        return any;
    }

    private boolean bindEtaCard(YandexNavSnapshot snapshot) {
        boolean any = false;

        any |= bindMetricRow(etaDistanceRow, etaDistanceValue, snapshot.etaDistance);
        any |= bindMetricRow(etaArrivalRow, etaArrivalValue, snapshot.etaArrival);
        any |= bindMetricRow(etaTimeRow, etaTimeValue, snapshot.etaTime);

        if (!any && snapshot.statusPanel != null && !snapshot.statusPanel.isEmpty()) {
            etaStatusView.setText(snapshot.statusPanel);
            etaStatusView.setVisibility(View.VISIBLE);
            any = true;
        } else {
            etaStatusView.setVisibility(View.GONE);
        }

        return any;
    }

    private static boolean bindMetricRow(LinearLayout row, TextView valueView, @Nullable String value) {
        if (value == null || value.isEmpty()) {
            row.setVisibility(View.GONE);
            return false;
        }
        valueView.setText(value);
        row.setVisibility(View.VISIBLE);
        return true;
    }

    public void hide() {
        mainHandler.post(this::hideInternal);
    }

    private void hideInternal() {
        lastApplied = null;
        appliedLayoutVersion = -1;
        if (overlayRoot != null && windowManager != null) {
            try {
                windowManager.removeView(overlayRoot);
            } catch (Exception ignored) {
            }
        }
        overlayRoot = null;
        windowManager = null;
        displayContext = null;
        clearViewRefs();
    }

    private void clearViewRefs() {
        maneuverDistanceView = null;
        nextStreetView = null;
        maneuverStatusView = null;
        etaDistanceValue = null;
        etaArrivalValue = null;
        etaTimeValue = null;
        etaStatusView = null;
        etaDistanceRow = null;
        etaArrivalRow = null;
        etaTimeRow = null;
        maneuverCard = null;
        etaCard = null;
    }

    private void ensureOverlay() {
        if (overlayRoot != null && windowManager != null
                && appliedLayoutVersion == LAYOUT_VERSION) {
            return;
        }
        if (overlayRoot != null) {
            try {
                windowManager.removeView(overlayRoot);
            } catch (Exception ignored) {
            }
            overlayRoot = null;
            clearViewRefs();
            appliedLayoutVersion = -1;
        }
        try {
            int displayId = AppLaunchHelper.getClusterDisplayId(appContext);
            DisplayManager dm =
                    (DisplayManager) appContext.getSystemService(Context.DISPLAY_SERVICE);
            if (dm == null) {
                return;
            }
            Display target = null;
            for (Display d : dm.getDisplays()) {
                if (d.getDisplayId() == displayId) {
                    target = d;
                    break;
                }
            }
            if (target == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                displayContext = appContext.createDisplayContext(target);
            } else {
                displayContext = appContext;
            }
            windowManager =
                    (WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) {
                return;
            }

            overlayRoot = buildOverlay(displayContext);
            appliedLayoutVersion = LAYOUT_VERSION;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            windowManager.addView(overlayRoot, lp);
        } catch (Exception e) {
            hideInternal();
        }
    }

    private View buildOverlay(Context ctx) {
        FrameLayout root = new FrameLayout(ctx);

        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_HORIZONTAL);

        maneuverCard = createManeuverCard(ctx);
        maneuverDistanceView = createManeuverText(ctx, 30, true);
        maneuverCard.addView(maneuverDistanceView, wrapLp());

        nextStreetView = createManeuverText(ctx, 14, false);
        nextStreetView.setMaxLines(2);
        LinearLayout.LayoutParams streetLp = wrapLp();
        streetLp.topMargin = dp(ctx, 4);
        maneuverCard.addView(nextStreetView, streetLp);

        maneuverStatusView = createManeuverText(ctx, 12, false);
        maneuverStatusView.setMaxLines(2);
        LinearLayout.LayoutParams statusLp = wrapLp();
        statusLp.topMargin = dp(ctx, 2);
        maneuverCard.addView(maneuverStatusView, statusLp);

        topRow.addView(maneuverCard, cardLp(ctx));

        View centerGap = new View(ctx);
        topRow.addView(centerGap, new LinearLayout.LayoutParams(
                dp(ctx, CENTER_GAP_DP),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        etaCard = createEtaCard(ctx);
        MetricRow distanceRow = addMetricRow(etaCard, ctx, "Kalan", 0);
        etaDistanceRow = distanceRow.row;
        etaDistanceValue = distanceRow.value;

        MetricRow arrivalRow = addMetricRow(etaCard, ctx, "Varış", dp(ctx, 6));
        etaArrivalRow = arrivalRow.row;
        etaArrivalValue = arrivalRow.value;

        MetricRow timeRow = addMetricRow(etaCard, ctx, "Süre", dp(ctx, 6));
        etaTimeRow = timeRow.row;
        etaTimeValue = timeRow.value;

        etaStatusView = createEtaText(ctx, 12, false);
        etaStatusView.setTextColor(UiStyles.color(ctx, R.color.textMuted));
        etaStatusView.setMaxLines(2);
        LinearLayout.LayoutParams etaStatusLp = wrapLp();
        etaStatusLp.topMargin = dp(ctx, 6);
        etaCard.addView(etaStatusView, etaStatusLp);

        topRow.addView(etaCard, cardLp(ctx));

        FrameLayout.LayoutParams rowLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        rowLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        rowLp.topMargin = dp(ctx, TOP_MARGIN_DP);
        root.addView(topRow, rowLp);

        return root;
    }

    private static final class MetricRow {
        final LinearLayout row;
        final TextView value;

        MetricRow(LinearLayout row, TextView value) {
            this.row = row;
            this.value = value;
        }
    }

    private static MetricRow addMetricRow(LinearLayout parent, Context ctx,
            String label, int topMargin) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView labelView = createEtaText(ctx, 13, false);
        labelView.setText(label);
        labelView.setTextColor(UiStyles.color(ctx, R.color.textMuted));
        row.addView(labelView, new LinearLayout.LayoutParams(
                dp(ctx, 52),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView valueView = createEtaText(ctx, 16, true);
        row.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams rowLp = wrapLp();
        rowLp.topMargin = topMargin;
        parent.addView(row, rowLp);
        return new MetricRow(row, valueView);
    }

    private static TextView createManeuverText(Context ctx, int sp, boolean bold) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) {
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(UiStyles.color(ctx, R.color.cluster_nav_maneuver_text));
        } else {
            tv.setTextColor(UiStyles.color(ctx, R.color.cluster_nav_maneuver_subtext));
        }
        return tv;
    }

    private static TextView createEtaText(Context ctx, int sp, boolean bold) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) {
            tv.setTypeface(null, Typeface.BOLD);
        }
        tv.setTextColor(UiStyles.color(ctx, R.color.textPrimary));
        return tv;
    }

    private static LinearLayout.LayoutParams wrapLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams cardLp(Context ctx) {
        return new LinearLayout.LayoutParams(
                dp(ctx, 136),
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout createManeuverCard(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_cluster_nav_maneuver));
        int padH = dp(ctx, 16);
        int padV = dp(ctx, 12);
        card.setPadding(padH, padV, padH, padV);
        return card;
    }

    private static LinearLayout createEtaCard(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_cluster_nav_eta));
        int pad = dp(ctx, 14);
        card.setPadding(pad, pad, pad, pad);
        return card;
    }

    private static int dp(Context ctx, int value) {
        float d = ctx.getResources().getDisplayMetrics().density;
        return Math.round(value * d);
    }
}
