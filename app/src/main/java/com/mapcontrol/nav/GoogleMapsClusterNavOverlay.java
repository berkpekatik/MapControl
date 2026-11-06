package com.mapcontrol.nav;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.mapcontrol.util.AppLaunchHelper;

/**
 * Cluster ekranında Google Maps navigasyon özet kartı (manevra + ETA tek kart) (bildirim verisi).
 * FLAG_NOT_TOUCHABLE — kadranlara dokunuşu engellemez.
 */
public final class GoogleMapsClusterNavOverlay {

    public static final String PREF_ENABLED = "google_maps_cluster_nav_overlay";
    private static final String PREFS = "MapControlPrefs";
    private static final int LAYOUT_VERSION = 6;
    private static final int TOP_MARGIN_DP =
            ClusterNavCardStyle.topMarginDp(ClusterNavOverlayOffsets.Provider.GOOGLE);

    private static GoogleMapsClusterNavOverlay instance;

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
    private LinearLayout navCard;
    private LinearLayout maneuverSection;
    private LinearLayout etaSection;
    private View divider;
    private FrameLayout.LayoutParams navCardLp;
    private int layoutTopMargin;
    private WindowManager.LayoutParams overlayLayoutParams;
    private int appliedLayoutVersion = -1;
    private ClusterNavOverlayOffsets.Style appliedStyle;
    private long lastZOrderRaiseUptimeMs = 0L;
    private static final long Z_ORDER_RAISE_MIN_INTERVAL_MS = 250L;

    @Nullable
    private GoogleMapsNavSnapshot lastApplied;

    private GoogleMapsClusterNavOverlay(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized GoogleMapsClusterNavOverlay getInstance(Context context) {
        if (instance == null) {
            instance = new GoogleMapsClusterNavOverlay(context);
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
        GoogleMapsClusterNavOverlay overlay = getInstance(context);
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

    private ClusterNavOverlayOffsets.Style currentStyle() {
        return ClusterNavOverlayOffsets.getStyle(
                appContext, ClusterNavOverlayOffsets.Provider.GOOGLE);
    }

    /** Kart görünümü (detay/ince çizgi) değişince: son veriyle yeniden çizer. */
    public void restyle() {
        mainHandler.post(() -> {
            if (overlayRoot != null && windowManager != null) {
                try {
                    windowManager.removeView(overlayRoot);
                } catch (Exception ignored) {
                }
                overlayRoot = null;
                clearViewRefs();
                appliedLayoutVersion = -1;
            }
            GoogleMapsNavSnapshot snap = lastApplied;
            lastApplied = null;
            if (snap != null) {
                applyOnMain(snap);
            }
        });
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

    public void apply(@Nullable GoogleMapsNavSnapshot snapshot) {
        mainHandler.post(() -> applyOnMain(snapshot));
    }

    private void applyOnMain(@Nullable GoogleMapsNavSnapshot snapshot) {
        if (!isEnabled(appContext)) {
            hideInternal();
            return;
        }
        if (snapshot == null || !snapshot.active) {
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
        setVisible(maneuverSection, hasManeuver);
        setVisible(etaSection, hasEta);
        setVisible(divider, hasManeuver && hasEta);
        if (appliedStyle == ClusterNavOverlayOffsets.Style.SLIM && navCard != null && navCardLp != null) {
            int slimWidth = (hasManeuver && hasEta)
                    ? dp(navCard.getContext(), ClusterNavCardStyle.widthDp(appliedStyle))
                    : FrameLayout.LayoutParams.WRAP_CONTENT;
            if (navCardLp.width != slimWidth) {
                navCardLp.width = slimWidth;
                navCard.setLayoutParams(navCardLp);
            }
        }
        if (!hasManeuver && !hasEta) {
            hideInternal();
            return;
        }
        lastApplied = snapshot;
    }

    /**
     * Cluster'da uygulama / pencere değişince harita veya launcher üst katmana geçebilir;
     * overlay aynı {@link WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY} tipinde
     * kalırsa eski ekleme sırasında altta kalır. remove + add ile en üste alınır.
     */
    public void raiseToTopIfShowing() {
        mainHandler.post(() -> raiseToTopOnMain(true));
    }

    private void raiseToTopOnMain(boolean throttle) {
        if (overlayRoot == null || windowManager == null || overlayLayoutParams == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (throttle && now - lastZOrderRaiseUptimeMs < Z_ORDER_RAISE_MIN_INTERVAL_MS) {
            return;
        }
        lastZOrderRaiseUptimeMs = now;
        try {
            windowManager.removeView(overlayRoot);
            windowManager.addView(overlayRoot, overlayLayoutParams);
        } catch (Exception ignored) {
        }
    }

    private boolean bindManeuverCard(GoogleMapsNavSnapshot snapshot) {
        boolean any = false;

        String distance = snapshot.formatManeuverDistanceLine();
        if (distance != null) {
            setTextIfChanged(maneuverDistanceView, distance);
            setVisible(maneuverDistanceView, true);
            any = true;
        } else {
            setVisible(maneuverDistanceView, false);
        }

        String direction = snapshot.formatManeuverDirectionLine();
        if (direction != null) {
            setTextIfChanged(nextStreetView, direction);
            setVisible(nextStreetView, true);
            any = true;
        } else {
            setVisible(nextStreetView, false);
        }

        if (snapshot.maneuverHint != null && !snapshot.maneuverHint.isEmpty()
                && (direction == null || !direction.equals(snapshot.maneuverHint))) {
            setTextIfChanged(maneuverStatusView, snapshot.maneuverHint);
            setVisible(maneuverStatusView, true);
            any = true;
        } else {
            setVisible(maneuverStatusView, false);
        }

        return any;
    }

    private boolean bindEtaCard(GoogleMapsNavSnapshot snapshot) {
        boolean any = false;

        any |= bindMetricRow(etaDistanceRow, etaDistanceValue, snapshot.etaDistanceLabel());
        any |= bindMetricRow(etaArrivalRow, etaArrivalValue, snapshot.etaArrivalLabel());
        any |= bindMetricRow(etaTimeRow, etaTimeValue, snapshot.etaDurationLabel());

        if (!any && snapshot.routeSummary != null && !snapshot.routeSummary.isEmpty()) {
            setTextIfChanged(etaStatusView, snapshot.routeSummary);
            setVisible(etaStatusView, true);
            any = true;
        } else {
            setVisible(etaStatusView, false);
        }

        return any;
    }

    private static boolean bindMetricRow(LinearLayout row, TextView valueView, @Nullable String value) {
        if (value == null || value.isEmpty()) {
            setVisible(row, false);
            return false;
        }
        setTextIfChanged(valueView, value);
        setVisible(row, true);
        return true;
    }

    private static void setTextIfChanged(TextView view, String text) {
        CharSequence current = view.getText();
        if (current == null || !current.toString().contentEquals(text)) {
            view.setText(text);
        }
    }

    private static void setVisible(View view, boolean visible) {
        int target = visible ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != target) {
            view.setVisibility(target);
        }
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
        overlayLayoutParams = null;
        lastZOrderRaiseUptimeMs = 0L;
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
        navCard = null;
        maneuverSection = null;
        etaSection = null;
        divider = null;
        navCardLp = null;
    }

    /** Yansıtma sekmesindeki X/Y kaydırma; overlay açıksa anında uygulanır. */
    public void applyStoredOffsets() {
        mainHandler.post(this::applyStoredOffsetsOnMain);
    }

    private void applyStoredOffsetsOnMain() {
        if (navCard == null || navCardLp == null) {
            return;
        }
        Context ctx = displayContext != null ? displayContext : appContext;
        navCardLp.topMargin = layoutTopMargin;
        navCard.setLayoutParams(navCardLp);
        ClusterNavOverlayOffsets.CardLayout layout =
                ClusterNavOverlayOffsets.getCardLayout(
                        appContext, ClusterNavOverlayOffsets.Provider.GOOGLE,
                        ClusterNavOverlayOffsets.Card.NAV);
        ClusterNavOverlayOffsets.applyTranslationAndScale(
                navCard, ctx, layout.offsetXDp, layout.offsetYDp, layout.scalePercent);
    }

    private void ensureOverlay() {
        if (overlayRoot != null && windowManager != null
                && appliedLayoutVersion == LAYOUT_VERSION
                && appliedStyle == currentStyle()) {
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

            appliedStyle = currentStyle();
            overlayRoot = buildOverlay(displayContext);
            appliedLayoutVersion = LAYOUT_VERSION;
            overlayLayoutParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            windowManager.addView(overlayRoot, overlayLayoutParams);
            lastZOrderRaiseUptimeMs = SystemClock.uptimeMillis();
        } catch (Exception e) {
            android.util.Log.e("ClusterNavOverlay", "overlay oluşturulamadı", e);
            hideInternal();
        }
    }

    private View buildOverlay(Context ctx) {
        FrameLayout root = new FrameLayout(ctx);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        ClusterNavCardStyle.NavCard c = ClusterNavCardStyle.createNavCard(ctx, appliedStyle);
        navCard = c.card;
        maneuverSection = c.maneuverSection;
        etaSection = c.etaSection;
        divider = c.divider;
        maneuverDistanceView = c.distance;
        nextStreetView = c.street;
        maneuverStatusView = c.status;
        etaDistanceRow = c.etaDistance.row;
        etaDistanceValue = c.etaDistance.value;
        etaArrivalRow = c.etaArrival.row;
        etaArrivalValue = c.etaArrival.value;
        etaTimeRow = c.etaTime.row;
        etaTimeValue = c.etaTime.value;
        etaStatusView = c.etaStatus;

        layoutTopMargin = dp(ctx, TOP_MARGIN_DP);
        navCardLp = new FrameLayout.LayoutParams(
                dp(ctx, ClusterNavCardStyle.widthDp(appliedStyle)),
                FrameLayout.LayoutParams.WRAP_CONTENT);
        navCardLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        navCardLp.topMargin = layoutTopMargin;
        root.addView(navCard, navCardLp);

        applyStoredOffsetsOnMain();
        return root;
    }

    private static int dp(Context ctx, int value) {
        float d = ctx.getResources().getDisplayMetrics().density;
        return Math.round(value * d);
    }
}
