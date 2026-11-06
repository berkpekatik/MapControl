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
 * Cluster ekranında Yandex navigasyon özet kartı (manevra + ETA tek kart).
 * FLAG_NOT_TOUCHABLE — kadranlara dokunuşu engellemez.
 */
public final class YandexClusterNavOverlay {

    public static final String PREF_ENABLED = "yandex_cluster_nav_overlay";
    private static final String PREFS = "MapControlPrefs";
    private static final int LAYOUT_VERSION = 6;
    private static final int TOP_MARGIN_DP =
            ClusterNavCardStyle.topMarginDp(ClusterNavOverlayOffsets.Provider.YANDEX);

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

    private ClusterNavOverlayOffsets.Style currentStyle() {
        return ClusterNavOverlayOffsets.getStyle(
                appContext, ClusterNavOverlayOffsets.Provider.YANDEX);
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
            YandexNavSnapshot snap = lastApplied;
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
        maneuverSection.setVisibility(hasManeuver ? View.VISIBLE : View.GONE);
        etaSection.setVisibility(hasEta ? View.VISIBLE : View.GONE);
        divider.setVisibility(hasManeuver && hasEta ? View.VISIBLE : View.GONE);
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
     * Cluster'da uygulama / pencere değişince Yandex veya launcher üst katmana geçebilir;
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
        View root = overlayRoot;
        WindowManager wm = windowManager;
        WindowManager.LayoutParams params = overlayLayoutParams;
        try {
            wm.removeView(root);
        } catch (Exception ignored) {
        }
        try {
            wm.addView(root, params);
            lastZOrderRaiseUptimeMs = now;
        } catch (Exception ignored) {
            overlayRoot = null;
            windowManager = null;
            displayContext = null;
            overlayLayoutParams = null;
            clearViewRefs();
            appliedLayoutVersion = -1;
            lastApplied = null;
        }
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
                        appContext, ClusterNavOverlayOffsets.Provider.YANDEX,
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
