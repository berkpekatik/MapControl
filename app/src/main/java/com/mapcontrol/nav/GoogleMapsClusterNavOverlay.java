package com.mapcontrol.nav;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.AppLaunchHelper;

/**
 * Cluster ekranında Google Maps navigasyon özet kartları (bildirim verisi).
 * FLAG_NOT_TOUCHABLE — kadranlara dokunuşu engellemez.
 */
public final class GoogleMapsClusterNavOverlay {

    public static final String PREF_ENABLED = "google_maps_cluster_nav_overlay";
    public static final String PREF_STYLE = "google_cluster_nav_style";
    public static final String PREF_OPACITY = "google_cluster_nav_opacity";
    public static final String PREF_OPACITY_MANEUVER = "google_cluster_nav_opacity_maneuver";
    public static final String PREF_OPACITY_ETA = "google_cluster_nav_opacity_eta";
    public static final String STYLE_DUAL = "dual";
    public static final String STYLE_HUD = "hud";
    public static final String STYLE_RADIAL = "radial";
    public static final String STYLE_TRAFFIC = "traffic";
    // Eski kayıtlı değerler için uyumluluk.
    public static final String STYLE_CLASSIC = "classic";
    public static final String STYLE_TWO_LANE = "two_lane";
    public static final String STYLE_SINGLE_ROW = "single_row";
    public static final String STYLE_GLASS = "glass";
    private static final String PREFS = "MapControlPrefs";
    private static final int LAYOUT_VERSION = 4;
    private static final int CARD_WIDTH_DP = 188;
    private static final int TOP_MARGIN_DP = 110;
    private static final int CENTER_GAP_DP = 300;
    /** Kartları ekran genişliğine göre dışa kaydırma (sol/sağ). */
    private static final float CARD_OUTWARD_SHIFT_RATIO = 0.15f;

    private static GoogleMapsClusterNavOverlay instance;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WindowManager windowManager;
    private Context displayContext;
    private View overlayRoot;
    private TextView maneuverDistanceView;
    private ImageView maneuverIconView;
    private TextView nextStreetView;
    private TextView maneuverStatusView;
    private TextView etaDistanceValue;
    private TextView etaArrivalValue;
    private TextView etaTimeValue;
    private TextView etaStatusView;
    private TextView radialDurationView;
    private TextView radialDurationUnitView;
    private TextView radialProgressValue;
    private View trafficProgress;
    private View trafficTopPill;
    private View trafficBottomPill;
    private TextView hudEtaDetail;
    private boolean hudDistanceStyle;
    private LinearLayout etaDistanceRow;
    private LinearLayout etaArrivalRow;
    private LinearLayout etaTimeRow;
    private LinearLayout maneuverCard;
    private LinearLayout etaCard;
    private FrameLayout.LayoutParams maneuverCardLp;
    private FrameLayout.LayoutParams etaCardLp;
    private int layoutManeuverLeft;
    private int layoutEtaLeft;
    private int layoutTopMargin;
    private int layoutEtaTopMargin;
    private int layoutCardWidthPx;
    private int layoutScreenWidthPx;
    private WindowManager.LayoutParams overlayLayoutParams;
    private int appliedLayoutVersion = -1;
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

    public static String getStyle(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PREF_STYLE, STYLE_DUAL);
        if (STYLE_CLASSIC.equals(raw)) return STYLE_DUAL;
        if (STYLE_GLASS.equals(raw)) return STYLE_HUD;
        if (STYLE_TWO_LANE.equals(raw)) return STYLE_RADIAL;
        if (STYLE_SINGLE_ROW.equals(raw)) return STYLE_TRAFFIC;
        return raw == null ? STYLE_DUAL : raw;
    }

    public static void setStyle(Context context, String style) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(PREF_STYLE, style).apply();
        // Yeni tasarım seçilince eski kart ofsetleri yeni düzeni bozmasın.
        ClusterNavOverlayOffsets.resetProvider(
                context, ClusterNavOverlayOffsets.Provider.GOOGLE);
        getInstance(context).forceRebuild();
        getInstance(context).invalidateCache();
    }

    public static int getOpacity(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(PREF_OPACITY, 100);
    }

    public static void setOpacity(Context context, int opacity) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(PREF_OPACITY, Math.max(20, Math.min(100, opacity))).apply();
        getInstance(context).applyStoredOpacity();
    }

    public static int getCardOpacity(Context context, boolean eta) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(eta ? PREF_OPACITY_ETA : PREF_OPACITY_MANEUVER, 100);
    }

    public static void setCardOpacity(Context context, boolean eta, int opacity) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(eta ? PREF_OPACITY_ETA : PREF_OPACITY_MANEUVER,
                        Math.max(10, Math.min(100, opacity))).apply();
        getInstance(context).applyStoredOpacity();
    }

    private void applyStoredOpacity() {
        mainHandler.post(this::applyCardOpacityOnMain);
    }

    /** Opaklık yalnızca kart zeminine uygulanır; yazılar tam opak kalır. */
    private void applyCardOpacityOnMain() {
        if (overlayRoot == null) {
            return;
        }
        overlayRoot.setAlpha(1f);
        String style = getStyle(appContext);
        int maneuverAlpha = Math.round(255f * getCardOpacity(appContext, false) / 100f);
        int etaAlpha = Math.round(255f * getCardOpacity(appContext, true) / 100f);
        if (STYLE_TRAFFIC.equals(style)) {
            applyViewBackgroundAlpha(trafficTopPill, maneuverAlpha);
            applyViewBackgroundAlpha(trafficBottomPill, etaAlpha);
            return;
        }
        applyCardBackground(maneuverCard, style, maneuverAlpha, R.color.ck_accent);
        applyCardBackground(etaCard, style, etaAlpha, R.color.ck_nominal);
    }

    private static void applyViewBackgroundAlpha(@Nullable View view, int alpha) {
        if (view == null || view.getBackground() == null) return;
        view.getBackground().mutate().setAlpha(alpha);
        view.invalidate();
    }

    private void applyCardBackground(
            @Nullable View card, String style, int alpha, int accentColor) {
        if (card == null) {
            return;
        }
        Context ctx = displayContext != null ? displayContext : appContext;
        int fillColor = (alpha << 24) | 0x000B0F14;
        if (STYLE_DUAL.equals(style) || STYLE_HUD.equals(style)
                || STYLE_RADIAL.equals(style)) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(fillColor);
            bg.setCornerRadius(dp(ctx, STYLE_HUD.equals(style) ? 26 : 18));
            bg.setStroke(dp(ctx, STYLE_HUD.equals(style) ? 1 : 2),
                    UiStyles.color(ctx, accentColor));
            card.setBackground(bg);
        } else {
            android.graphics.drawable.Drawable bg = card.getBackground();
            if (bg != null) {
                bg.mutate().setAlpha(alpha);
                card.invalidate();
            }
        }
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
        setVisible(maneuverCard, hasManeuver);
        setVisible(etaCard, hasEta);
        if (!hasManeuver && !hasEta) {
            hideInternal();
            return;
        }
        lastApplied = snapshot;
        applyCardOpacityOnMain();
        raiseToTopOnMain(true);
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
        bindManeuverIcon(snapshot);

        String maneuverDistance = snapshot.maneuverDistanceFromTitle();
        String distance = maneuverDistance;
        if (distance != null) {
            if (hudDistanceStyle) {
                setHudDistanceText(distance);
            } else {
                setTextIfChanged(maneuverDistanceView, distance);
            }
            setVisible(maneuverDistanceView, true);
            any = true;
        } else if (hudDistanceStyle) {
            // Manevra mesafesi yoksa 0/850 uydurma; ama satır yüksekliğini koru.
            maneuverDistanceView.setText("");
            maneuverDistanceView.setVisibility(View.INVISIBLE);
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

    private void bindManeuverIcon(GoogleMapsNavSnapshot snapshot) {
        if (maneuverIconView == null) return;
        Bitmap bitmap = snapshot.maneuverIcon;
        if (bitmap != null && !bitmap.isRecycled()) {
            maneuverIconView.setImageBitmap(bitmap);
            maneuverIconView.setColorFilter(UiStyles.color(
                    maneuverIconView.getContext(), R.color.ck_accent));
            maneuverIconView.setRotation(0f);
            return;
        }
        String direction = snapshot.formatManeuverDirectionLine();
        String lower = direction == null ? "" : direction.toLowerCase(java.util.Locale.ROOT);
        int rotation = 0;
        if (lower.contains("sağ") || lower.contains("right")) {
            rotation = 180;
        } else if (lower.contains("düz") || lower.contains("straight")
                || lower.contains("devam")) {
            rotation = 90;
        } else if (lower.contains("u dön") || lower.contains("u-turn")
                || lower.contains("uturn")) {
            rotation = 135;
        }
        maneuverIconView.setImageResource(R.drawable.ic_ck_arrow_left);
        maneuverIconView.setColorFilter(UiStyles.color(
                maneuverIconView.getContext(), R.color.ck_accent));
        maneuverIconView.setRotation(rotation);
    }

    private boolean bindEtaCard(GoogleMapsNavSnapshot snapshot) {
        boolean any = false;
        if (radialDurationView != null) {
            String duration = snapshot.etaDurationLabel();
            String value = duration;
            String unit = "DAKİKA";
            if (duration != null) {
                int split = 0;
                while (split < duration.length() && Character.isDigit(duration.charAt(split))) split++;
                if (split > 0) value = duration.substring(0, split);
            }
            radialDurationView.setText(value == null || value.isEmpty() ? "—" : value);
            if (radialDurationUnitView != null) radialDurationUnitView.setText(unit);
        }

        any |= bindMetricRow(etaDistanceRow, etaDistanceValue, snapshot.etaDistanceLabel());
        any |= bindMetricRow(etaArrivalRow, etaArrivalValue, snapshot.etaArrivalLabel());
        any |= bindMetricRow(etaTimeRow, etaTimeValue, snapshot.etaDurationLabel());
        if (hudEtaDetail != null && etaTimeValue != null) {
            setHudDurationText(snapshot.etaDurationLabel());
        }
        if (hudEtaDetail != null) {
            String distance = snapshot.etaDistanceLabel();
            String maneuverDistance = snapshot.maneuverDistanceFromTitle();
            String arrival = snapshot.etaArrivalLabel();
            StringBuilder detail = new StringBuilder();
            if (distance != null && !distance.equals(maneuverDistance)) detail.append(distance);
            if (arrival != null) {
                if (detail.length() > 0) detail.append("  ·  ");
                detail.append(arrival);
            }
            hudEtaDetail.setText(detail);
        }

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
        if (valueView != null) {
            setTextIfChanged(valueView, value);
        }
        setVisible(row, true);
        return true;
    }

    private void setHudDistanceText(String line) {
        if (maneuverDistanceView == null || line == null) return;
        int split = line.length();
        while (split > 0 && !Character.isLetter(line.charAt(split - 1))) split--;
        String value = line.substring(0, split).trim();
        String unit = line.substring(split).trim().toUpperCase();
        if (value.isEmpty()) {
            setTextIfChanged(maneuverDistanceView, line);
            return;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(value);
        if (!unit.isEmpty()) {
            sb.append("  ");
            int start = sb.length();
            sb.append(unit);
            sb.setSpan(new ForegroundColorSpan(UiStyles.color(maneuverDistanceView.getContext(), R.color.ck_accent)),
                    start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new RelativeSizeSpan(0.55f), start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        maneuverDistanceView.setText(sb);
    }

    private void setHudDurationText(@Nullable String line) {
        if (etaTimeValue == null || line == null || line.isEmpty()) return;
        int split = 0;
        while (split < line.length() && Character.isDigit(line.charAt(split))) split++;
        String value = line.substring(0, split).trim();
        String unit = line.substring(split).trim();
        if (value.isEmpty()) {
            etaTimeValue.setText(line);
            return;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(value);
        if (!unit.isEmpty()) {
            sb.append(" ");
            int start = sb.length();
            sb.append(unit);
            sb.setSpan(new ForegroundColorSpan(UiStyles.color(etaTimeValue.getContext(), R.color.ck_text_high)),
                    start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new RelativeSizeSpan(0.6f), start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        etaTimeValue.setText(sb);
    }

    private static void setTextIfChanged(TextView view, String text) {
        if (view == null) return;
        CharSequence current = view.getText();
        if (current == null || !current.toString().contentEquals(text)) {
            view.setText(text);
        }
    }

    private static void setVisible(View view, boolean visible) {
        if (view == null) {
            return;
        }
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
        maneuverIconView = null;
        nextStreetView = null;
        maneuverStatusView = null;
        etaDistanceValue = null;
        etaArrivalValue = null;
        etaTimeValue = null;
        etaStatusView = null;
        radialDurationView = null;
        radialDurationUnitView = null;
        radialProgressValue = null;
        trafficProgress = null;
        trafficTopPill = null;
        trafficBottomPill = null;
        hudEtaDetail = null;
        hudDistanceStyle = false;
        etaDistanceRow = null;
        etaArrivalRow = null;
        etaTimeRow = null;
        maneuverCard = null;
        etaCard = null;
        maneuverCardLp = null;
        etaCardLp = null;
    }

    /** Yansıtma sekmesindeki X/Y kaydırma; overlay açıksa anında uygulanır. */
    public void applyStoredOffsets() {
        mainHandler.post(this::applyStoredOffsetsOnMain);
    }

    private void applyStoredOffsetsOnMain() {
        if (maneuverCard == null || maneuverCardLp == null) {
            return;
        }
        Context ctx = displayContext != null ? displayContext : appContext;
        ClusterNavOverlayOffsets.CardLayout maneuver =
                ClusterNavOverlayOffsets.getCardLayout(
                        appContext, ClusterNavOverlayOffsets.Provider.GOOGLE,
                        ClusterNavOverlayOffsets.Card.MANEUVER);
        ClusterNavOverlayOffsets.CardLayout eta =
                ClusterNavOverlayOffsets.getCardLayout(
                        appContext, ClusterNavOverlayOffsets.Provider.GOOGLE,
                        ClusterNavOverlayOffsets.Card.ETA);

        maneuverCardLp.leftMargin = layoutManeuverLeft;
        maneuverCardLp.topMargin = layoutTopMargin;
        maneuverCard.setLayoutParams(maneuverCardLp);
        if (STYLE_TRAFFIC.equals(getStyle(appContext))
                && trafficTopPill != null && trafficBottomPill != null) {
            maneuverCard.setTranslationX(0f);
            maneuverCard.setTranslationY(0f);
            maneuverCard.setScaleX(1f);
            maneuverCard.setScaleY(1f);
            ClusterNavOverlayOffsets.applyTranslationAndScale(
                    trafficTopPill, ctx,
                    maneuver.offsetXDp, maneuver.offsetYDp, maneuver.scalePercent);
            ClusterNavOverlayOffsets.applyTranslationAndScale(
                    trafficBottomPill, ctx,
                    eta.offsetXDp, eta.offsetYDp, eta.scalePercent);
            return;
        }
        ClusterNavOverlayOffsets.applyTranslationAndScale(
                maneuverCard, ctx,
                maneuver.offsetXDp, maneuver.offsetYDp, maneuver.scalePercent);

        if (etaCard != null && etaCardLp != null) {
            etaCardLp.leftMargin = layoutEtaLeft;
            etaCardLp.topMargin = layoutEtaTopMargin;
            etaCard.setLayoutParams(etaCardLp);
            ClusterNavOverlayOffsets.applyTranslationAndScale(
                    etaCard, ctx,
                    eta.offsetXDp, eta.offsetYDp, eta.scalePercent);
        }
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
            raiseToTopOnMain(true);
            applyCardOpacityOnMain();
            lastZOrderRaiseUptimeMs = SystemClock.uptimeMillis();
        } catch (Exception e) {
            hideInternal();
        }
    }

    private View buildOverlay(Context ctx) {
        FrameLayout root = new FrameLayout(ctx);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        String style = getStyle(appContext);
        boolean singleCard = !STYLE_DUAL.equals(style);
        int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
        int topMarginPx = dp(ctx, TOP_MARGIN_DP);
        boolean dualStyle = STYLE_DUAL.equals(style);
        int cardWidthPx = dp(ctx, dualStyle ? 300 : CARD_WIDTH_DP);
        int dualCardHeightPx = dp(ctx, 154);
        int gapPx = dp(ctx, dualStyle ? 20 : CENTER_GAP_DP);
        int outwardPx = dualStyle ? 0 : (int) (screenWidth * CARD_OUTWARD_SHIFT_RATIO);
        int pairWidth = cardWidthPx * 2 + gapPx;
        int pairLeft = (screenWidth - pairWidth) / 2;
        int maneuverLeft;
        int etaLeft;
        int etaTopMarginPx = topMarginPx;

        if (singleCard) {
            boolean wrapCard = STYLE_TRAFFIC.equals(style) || STYLE_RADIAL.equals(style);
            int width = Math.min(screenWidth - dp(ctx, 32), dp(ctx, 390));
            maneuverLeft = wrapCard ? 0 : (screenWidth - width) / 2;
            etaLeft = maneuverLeft;
            maneuverCard = createSingleCard(ctx, style);
            buildSingleCardContents(ctx, style);
            maneuverCardLp = wrapCard
                    ? new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT)
                    : new FrameLayout.LayoutParams(width,
                            FrameLayout.LayoutParams.WRAP_CONTENT);
            maneuverCardLp.gravity = wrapCard
                    ? (Gravity.TOP | Gravity.CENTER_HORIZONTAL)
                    : (Gravity.TOP | Gravity.START);
            maneuverCardLp.leftMargin = maneuverLeft;
            maneuverCardLp.topMargin = topMarginPx;
            root.addView(maneuverCard, maneuverCardLp);
            etaCard = new LinearLayout(ctx);
            etaCardLp = null;
        } else {
            // Referans: solda manevra, sağda varış.
            maneuverLeft = pairLeft - outwardPx;
            etaLeft = pairLeft + cardWidthPx + gapPx + outwardPx;
            maneuverCard = createDualManeuverCard(ctx);
            etaCard = createDualEtaCard(ctx);

            maneuverCardLp = new FrameLayout.LayoutParams(cardWidthPx,
                    dualCardHeightPx);
            maneuverCardLp.gravity = Gravity.TOP | Gravity.START;
            maneuverCardLp.leftMargin = Math.max(0, maneuverLeft);
            maneuverCardLp.topMargin = topMarginPx;
            root.addView(maneuverCard, maneuverCardLp);

            etaCardLp = new FrameLayout.LayoutParams(cardWidthPx,
                    dualCardHeightPx);
            etaCardLp.gravity = Gravity.TOP | Gravity.START;
            etaCardLp.leftMargin = Math.min(screenWidth - cardWidthPx, etaLeft);
            etaCardLp.topMargin = etaTopMarginPx;
            root.addView(etaCard, etaCardLp);
        }

        applyCardStyle(ctx, style);
        layoutScreenWidthPx = screenWidth;
        layoutCardWidthPx = cardWidthPx;
        layoutManeuverLeft = Math.max(0, maneuverLeft);
        layoutEtaLeft = Math.max(0, etaLeft);
        layoutTopMargin = topMarginPx;
        layoutEtaTopMargin = etaTopMarginPx;
        applyStoredOffsetsOnMain();
        return root;
    }

    private LinearLayout createSingleCard(Context ctx, String style) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(STYLE_TRAFFIC.equals(style)
                ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        if (STYLE_HUD.equals(style)) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF0B0F14);
            bg.setCornerRadius(dp(ctx, 26));
            bg.setStroke(dp(ctx, 1), UiStyles.color(ctx, R.color.ck_accent_stroke_30));
            card.setBackground(bg);
        } else {
            card.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_cluster_nav_maneuver));
        }
        int padH = dp(ctx, 14);
        int padV = dp(ctx, 10);
        card.setPadding(padH, padV, padH, padV);
        return card;
    }

    private void buildSingleCardContents(Context ctx, String style) {
        if (STYLE_HUD.equals(style)) {
            hudDistanceStyle = true;
            ImageView arrow = new ImageView(ctx);
            maneuverIconView = arrow;
            arrow.setImageResource(R.drawable.ic_ck_arrow_left);
            arrow.setColorFilter(UiStyles.color(ctx, R.color.ck_accent));
            GradientDrawable arrowBg = new GradientDrawable();
            arrowBg.setColor(UiStyles.color(ctx, R.color.ck_surface_2));
            arrowBg.setCornerRadius(dp(ctx, 10));
            arrowBg.setStroke(dp(ctx, 2), UiStyles.color(ctx, R.color.ck_accent_stroke_30));
            arrow.setBackground(arrowBg);
            arrow.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8));
            maneuverCard.addView(arrow, new LinearLayout.LayoutParams(dp(ctx, 48), dp(ctx, 48)));

            LinearLayout left = new LinearLayout(ctx);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setPadding(dp(ctx, 10), 0, dp(ctx, 8), 0);
            maneuverDistanceView = createManeuverText(ctx, 28, true);
            maneuverDistanceView.setMinHeight(dp(ctx, 34));
            left.addView(maneuverDistanceView);
            nextStreetView = createManeuverText(ctx, 14, false);
            left.addView(nextStreetView);
            maneuverCard.addView(left, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1.35f));

            TextView divider = new TextView(ctx);
            divider.setBackgroundColor(UiStyles.color(ctx, R.color.ck_stroke));
            maneuverCard.addView(divider, new LinearLayout.LayoutParams(1, dp(ctx, 48)));

            LinearLayout right = new LinearLayout(ctx);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            right.setPadding(dp(ctx, 8), 0, 0, 0);
            LinearLayout durationRow = new LinearLayout(ctx);
            durationRow.setOrientation(LinearLayout.HORIZONTAL);
            durationRow.setGravity(Gravity.CENTER_VERTICAL);
            etaTimeValue = createEtaText(ctx, 22, true);
            durationRow.addView(etaTimeValue);
            TextView dot = new TextView(ctx);
            dot.setText("●");
            dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            dot.setTextColor(UiStyles.color(ctx, R.color.ck_nominal));
            dot.setPadding(dp(ctx, 6), 0, 0, 0);
            durationRow.addView(dot);
            right.addView(durationRow, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            hudEtaDetail = createEtaText(ctx, 13, false);
            hudEtaDetail.setTextColor(UiStyles.color(ctx, R.color.ck_text_medium));
            hudEtaDetail.setGravity(Gravity.END);
            hudEtaDetail.setPadding(0, dp(ctx, 3), 0, 0);
            right.addView(hudEtaDetail, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            maneuverCard.addView(right, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            // bindEtaCard için boş alanlar; HUD'da görünür karşılıkları yukarıda.
            etaDistanceRow = new LinearLayout(ctx);
            etaDistanceValue = new TextView(ctx);
            etaArrivalRow = new LinearLayout(ctx);
            etaArrivalValue = new TextView(ctx);
            etaTimeRow = new LinearLayout(ctx);
            etaStatusView = new TextView(ctx);
        } else if (STYLE_RADIAL.equals(style)) {
            maneuverCard.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12));
            maneuverCard.setGravity(Gravity.CENTER_VERTICAL);
            maneuverCard.setBackground(radialPillBackground(ctx));

            maneuverIconView = new ImageView(ctx);
            maneuverIconView.setImageResource(R.drawable.ic_ck_arrow_left);
            maneuverIconView.setColorFilter(UiStyles.color(ctx, R.color.ck_accent));
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setShape(GradientDrawable.OVAL);
            iconBg.setColor(UiStyles.color(ctx, R.color.ck_surface_2));
            iconBg.setStroke(dp(ctx, 1), UiStyles.color(ctx, R.color.ck_accent_stroke_30));
            maneuverIconView.setBackground(iconBg);
            maneuverIconView.setPadding(dp(ctx, 9), dp(ctx, 9), dp(ctx, 9), dp(ctx, 9));
            maneuverCard.addView(maneuverIconView, new LinearLayout.LayoutParams(
                    dp(ctx, 46), dp(ctx, 46)));

            hudDistanceStyle = true;
            maneuverDistanceView = createManeuverText(ctx, 30, true);
            LinearLayout.LayoutParams distanceLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            distanceLp.leftMargin = dp(ctx, 12);
            maneuverCard.addView(maneuverDistanceView, distanceLp);

            TextView dot1 = createEtaText(ctx, 18, true);
            dot1.setText("·");
            dot1.setTextColor(UiStyles.color(ctx, R.color.ck_text_medium));
            dot1.setPadding(dp(ctx, 10), 0, dp(ctx, 10), 0);
            maneuverCard.addView(dot1);

            nextStreetView = createManeuverText(ctx, 22, true);
            nextStreetView.setSingleLine(true);
            nextStreetView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            maneuverCard.addView(nextStreetView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            View divider = new View(ctx);
            divider.setBackgroundColor(UiStyles.color(ctx, R.color.ck_stroke));
            LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                    dp(ctx, 1), dp(ctx, 34));
            dividerLp.leftMargin = dp(ctx, 14);
            dividerLp.rightMargin = dp(ctx, 14);
            maneuverCard.addView(divider, dividerLp);

            TextView statusDot = createEtaText(ctx, 12, true);
            statusDot.setText("●");
            statusDot.setTextColor(UiStyles.color(ctx, R.color.ck_nominal));
            maneuverCard.addView(statusDot);

            etaTimeValue = createEtaText(ctx, 21, true);
            etaTimeValue.setTextColor(UiStyles.color(ctx, R.color.ck_accent));
            LinearLayout.LayoutParams durationLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            durationLp.leftMargin = dp(ctx, 6);
            maneuverCard.addView(etaTimeValue, durationLp);

            TextView dot2 = createEtaText(ctx, 16, true);
            dot2.setText("·");
            dot2.setTextColor(UiStyles.color(ctx, R.color.ck_text_medium));
            dot2.setPadding(dp(ctx, 9), 0, dp(ctx, 9), 0);
            maneuverCard.addView(dot2);

            etaDistanceValue = createEtaText(ctx, 20, false);
            maneuverCard.addView(etaDistanceValue);

            TextView dot3 = createEtaText(ctx, 16, true);
            dot3.setText("·");
            dot3.setTextColor(UiStyles.color(ctx, R.color.ck_text_medium));
            dot3.setPadding(dp(ctx, 9), 0, dp(ctx, 9), 0);
            maneuverCard.addView(dot3);

            etaArrivalValue = createEtaText(ctx, 21, true);
            etaArrivalValue.setTextColor(UiStyles.color(ctx, R.color.ck_text_high));
            etaArrivalValue.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6));
            GradientDrawable arrivalBg = new GradientDrawable();
            arrivalBg.setColor(0xFF0F1A20);
            arrivalBg.setCornerRadius(dp(ctx, 9));
            arrivalBg.setStroke(dp(ctx, 1), UiStyles.color(ctx, R.color.ck_accent_stroke_30));
            etaArrivalValue.setBackground(arrivalBg);
            maneuverCard.addView(etaArrivalValue);
        } else {
            maneuverCard.setBackground(null);
            maneuverCard.setPadding(0, 0, 0, 0);
            maneuverCard.setOrientation(LinearLayout.VERTICAL);
            maneuverCard.setGravity(Gravity.CENTER_HORIZONTAL);

            LinearLayout top = createTrafficPill(ctx, true);
            trafficTopPill = top;
            maneuverIconView = new ImageView(ctx);
            maneuverIconView.setImageResource(R.drawable.ic_ck_arrow_left);
            maneuverIconView.setColorFilter(UiStyles.color(ctx, R.color.ck_accent));
            top.addView(maneuverIconView, new LinearLayout.LayoutParams(
                    dp(ctx, 30), dp(ctx, 30)));
            maneuverDistanceView = createManeuverText(ctx, 28, true);
            LinearLayout.LayoutParams distLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            distLp.leftMargin = dp(ctx, 10);
            top.addView(maneuverDistanceView, distLp);
            TextView dot = createEtaText(ctx, 16, true);
            dot.setText("·");
            dot.setTextColor(UiStyles.color(ctx, R.color.ck_text_medium));
            dot.setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0);
            top.addView(dot);
            nextStreetView = createManeuverText(ctx, 19, true);
            nextStreetView.setMaxLines(1);
            nextStreetView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            top.addView(nextStreetView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            View spacer = new View(ctx);
            top.addView(spacer, new LinearLayout.LayoutParams(dp(ctx, 14), 1));
            TextView statusDot = createEtaText(ctx, 12, true);
            statusDot.setText("●");
            statusDot.setTextColor(UiStyles.color(ctx, R.color.ck_nominal));
            top.addView(statusDot, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            maneuverCard.addView(top, wrapLp());

            LinearLayout bottom = createTrafficPill(ctx, false);
            trafficBottomPill = bottom;
            LinearLayout.LayoutParams bottomLp = wrapLp();
            bottomLp.topMargin = dp(ctx, 8);
            etaTimeValue = createEtaText(ctx, 18, true);
            bottom.addView(etaTimeValue);
            TextView dot2 = createEtaText(ctx, 14, true);
            dot2.setText("·");
            dot2.setTextColor(UiStyles.color(ctx, R.color.ck_text_medium));
            dot2.setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0);
            bottom.addView(dot2);
            etaDistanceValue = createEtaText(ctx, 16, false);
            bottom.addView(etaDistanceValue);
            TextView dot3 = createEtaText(ctx, 14, true);
            dot3.setText("·");
            dot3.setTextColor(UiStyles.color(ctx, R.color.ck_text_medium));
            dot3.setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0);
            bottom.addView(dot3);
            etaArrivalValue = createEtaText(ctx, 18, true);
            etaArrivalValue.setTextColor(UiStyles.color(ctx, R.color.ck_nominal));
            bottom.addView(etaArrivalValue);
            maneuverCard.addView(bottom, bottomLp);
        }
    }

    private void addEtaRows(Context ctx, LinearLayout parent) {
        MetricRow distanceRow = addMetricRow(parent, ctx, "Kalan", 0);
        etaDistanceRow = distanceRow.row;
        etaDistanceValue = distanceRow.value;
        MetricRow arrivalRow = addMetricRow(parent, ctx, "Varış", dp(ctx, 6));
        etaArrivalRow = arrivalRow.row;
        etaArrivalValue = arrivalRow.value;
        MetricRow timeRow = addMetricRow(parent, ctx, "Süre", dp(ctx, 6));
        etaTimeRow = timeRow.row;
        etaTimeValue = timeRow.value;
        etaStatusView = createEtaText(ctx, 12, false);
        etaStatusView.setTextColor(UiStyles.color(ctx, R.color.ck_text_medium));
        etaStatusView.setMaxLines(2);
        LinearLayout.LayoutParams statusLp = wrapLp();
        statusLp.topMargin = dp(ctx, 4);
        parent.addView(etaStatusView, statusLp);
    }

    private static LinearLayout.LayoutParams matchWrapLp() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
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
        labelView.setTextColor(UiStyles.color(ctx, R.color.ck_text_medium));
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
        tv.setTextColor(UiStyles.color(ctx, R.color.ck_text_high));
        return tv;
    }

    private static LinearLayout.LayoutParams wrapLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams cardLp(Context ctx) {
        return new LinearLayout.LayoutParams(
                dp(ctx, CARD_WIDTH_DP),
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void applyCardStyle(Context ctx, String style) {
        if (STYLE_HUD.equals(style)) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF0B0F14);
            bg.setCornerRadius(dp(ctx, 26));
            bg.setStroke(dp(ctx, 1), UiStyles.color(ctx, R.color.ck_accent_stroke_30));
            maneuverCard.setBackground(bg);
        } else if (STYLE_RADIAL.equals(style)) {
            maneuverCard.setBackground(radialPillBackground(ctx));
        } else if (STYLE_TRAFFIC.equals(style)) {
            maneuverCard.setBackground(null);
            maneuverCard.setPadding(0, 0, 0, 0);
        } else if (STYLE_DUAL.equals(style)) {
            // Kartlar kendi referans dolgularını kullanır.
        } else if (STYLE_SINGLE_ROW.equals(style)) {
            maneuverCard.setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9));
            etaCard.setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9));
        }
    }

    private static GradientDrawable glassBackground(Context ctx) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x99161B22);
        bg.setCornerRadius(dp(ctx, 16));
        bg.setStroke(Math.max(1, dp(ctx, 1)), 0x66FFFFFF);
        return bg;
    }




    private static GradientDrawable radialPillBackground(Context ctx) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0B0F14);
        bg.setCornerRadius(dp(ctx, 24));
        bg.setStroke(dp(ctx, 1), UiStyles.color(ctx, R.color.ck_accent_stroke_30));
        return bg;
    }

    private static LinearLayout createTrafficPill(Context ctx, boolean accent) {
        LinearLayout pill = new LinearLayout(ctx);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dp(ctx, 16);
        int padV = dp(ctx, 10);
        pill.setPadding(padH, padV, padH, padV);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0B0F14);
        bg.setCornerRadius(dp(ctx, 24));
        bg.setStroke(dp(ctx, 1), UiStyles.color(ctx,
                accent ? R.color.ck_accent : R.color.ck_stroke));
        pill.setBackground(bg);
        return pill;
    }

    private static void addTextShadow(TextView view) {
        view.setShadowLayer(5f, 0f, 2f, 0xCC000000);
    }

    private static LinearLayout createRadialMetricBox(Context ctx, String labelText) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(ctx, 13), dp(ctx, 11), dp(ctx, 13), dp(ctx, 11));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF18212A);
        bg.setCornerRadius(dp(ctx, 9));
        bg.setStroke(dp(ctx, 1), 0xFF2B3742);
        box.setBackground(bg);
        box.setAlpha(1f);
        TextView label = createEtaText(ctx, 14, true);
        label.setText(labelText);
        label.setTextColor(UiStyles.color(ctx, R.color.ck_text_high));
        label.setLetterSpacing(0.06f);
        box.addView(label);
        return box;
    }

    private static final class RadialGaugeView extends View {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private float progress = 0.68f;

        RadialGaugeView(Context context) {
            super(context);
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeWidth(dp(context, 9));
            trackPaint.setColor(0xFF252E38);
            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeWidth(dp(context, 9));
            arcPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        void setProgress(float value) {
            progress = Math.max(0f, Math.min(1f, value));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = dp(getContext(), 13);
            oval.set(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawArc(oval, -90f, 360f, false, trackPaint);
            int color = UiStyles.color(getContext(), R.color.ck_accent);
            int nominal = UiStyles.color(getContext(), R.color.ck_nominal);
            arcPaint.setShader(new SweepGradient(getWidth() / 2f, getHeight() / 2f,
                    new int[]{color, nominal, color}, new float[]{0f, 0.6f, 1f}));
            canvas.drawArc(oval, -90f, 270f * progress, false, arcPaint);
        }
    }

    private LinearLayout createDualManeuverCard(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        card.setMinimumHeight(dp(ctx, 148));
        card.setBackground(dualCardBackground(ctx, R.color.ck_accent));
        hudDistanceStyle = true;

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        maneuverIconView = new ImageView(ctx);
        maneuverIconView.setImageResource(R.drawable.ic_ck_arrow_left);
        maneuverIconView.setColorFilter(UiStyles.color(ctx, R.color.ck_accent));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(UiStyles.color(ctx, R.color.ck_surface_2));
        iconBg.setCornerRadius(dp(ctx, 10));
        iconBg.setStroke(dp(ctx, 1), UiStyles.color(ctx, R.color.ck_accent_stroke_30));
        maneuverIconView.setBackground(iconBg);
        maneuverIconView.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8));
        top.addView(maneuverIconView, new LinearLayout.LayoutParams(dp(ctx, 48), dp(ctx, 48)));

        TextView label = createEtaText(ctx, 12, false);
        label.setText("MANEVRA");
        label.setTextColor(UiStyles.color(ctx, R.color.ck_text_medium));
        label.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.leftMargin = dp(ctx, 10);
        top.addView(label, labelLp);

        LinearLayout lanes = new LinearLayout(ctx);
        lanes.setOrientation(LinearLayout.HORIZONTAL);
        lanes.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < 3; i++) {
            View dot = new View(ctx);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(UiStyles.color(ctx,
                    i == 0 ? R.color.ck_accent : R.color.ck_surface_2));
            dot.setBackground(dotBg);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(ctx, 7), dp(ctx, 13));
            dotLp.leftMargin = dp(ctx, 3);
            lanes.addView(dot, dotLp);
        }
        GradientDrawable laneBg = new GradientDrawable();
        laneBg.setColor(0xFF10151A);
        laneBg.setCornerRadius(dp(ctx, 7));
        lanes.setBackground(laneBg);
        lanes.setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4));
        top.addView(lanes, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(top, matchWrapLp());

        maneuverDistanceView = createManeuverText(ctx, 34, true);
        maneuverDistanceView.setMinHeight(dp(ctx, 40));
        LinearLayout.LayoutParams distanceLp = wrapLp();
        distanceLp.topMargin = dp(ctx, 8);
        card.addView(maneuverDistanceView, distanceLp);

        nextStreetView = createManeuverText(ctx, 16, false);
        nextStreetView.setMaxLines(2);
        LinearLayout.LayoutParams streetLp = wrapLp();
        streetLp.topMargin = dp(ctx, 4);
        card.addView(nextStreetView, streetLp);
        return card;
    }

    private LinearLayout createDualEtaCard(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        card.setMinimumHeight(dp(ctx, 148));
        card.setBackground(dualCardBackground(ctx, R.color.ck_nominal));

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = createEtaText(ctx, 12, false);
        label.setText("VARIŞ SAATİ");
        label.setTextColor(UiStyles.color(ctx, R.color.ck_text_medium));
        label.setLetterSpacing(0.08f);
        top.addView(label, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        card.addView(top, matchWrapLp());

        LinearLayout etaRow = new LinearLayout(ctx);
        etaRow.setOrientation(LinearLayout.HORIZONTAL);
        etaRow.setGravity(Gravity.CENTER_VERTICAL);
        etaArrivalValue = createEtaText(ctx, 34, true);
        etaRow.addView(etaArrivalValue, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams etaLp = matchWrapLp();
        etaLp.topMargin = dp(ctx, 4);
        card.addView(etaRow, etaLp);

        View divider = new View(ctx);
        divider.setBackgroundColor(UiStyles.color(ctx, R.color.ck_stroke));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1));
        dividerLp.topMargin = dp(ctx, 10);
        card.addView(divider, dividerLp);

        LinearLayout metrics = new LinearLayout(ctx);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setGravity(Gravity.CENTER_VERTICAL);
        metrics.setPadding(0, dp(ctx, 9), 0, 0);

        LinearLayout distanceCol = new LinearLayout(ctx);
        distanceCol.setOrientation(LinearLayout.VERTICAL);
        TextView distanceLabel = createEtaText(ctx, 10, false);
        distanceLabel.setText("KALAN YOL");
        distanceLabel.setTextColor(UiStyles.color(ctx, R.color.ck_text_medium));
        distanceLabel.setLetterSpacing(0.06f);
        distanceCol.addView(distanceLabel);
        etaDistanceValue = createEtaText(ctx, 20, true);
        distanceCol.addView(etaDistanceValue);
        metrics.addView(distanceCol, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout durationCol = new LinearLayout(ctx);
        durationCol.setOrientation(LinearLayout.VERTICAL);
        durationCol.setGravity(Gravity.END);
        TextView durationLabel = createEtaText(ctx, 10, false);
        durationLabel.setText("SÜRE");
        durationLabel.setTextColor(UiStyles.color(ctx, R.color.ck_text_medium));
        durationLabel.setLetterSpacing(0.06f);
        durationLabel.setGravity(Gravity.END);
        durationCol.addView(durationLabel);
        etaTimeValue = createEtaText(ctx, 20, true);
        etaTimeValue.setGravity(Gravity.END);
        durationCol.addView(etaTimeValue);
        metrics.addView(durationCol, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        card.addView(metrics, matchWrapLp());
        return card;
    }

    private static GradientDrawable dualCardBackground(Context ctx, int accentColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0B0F14);
        bg.setCornerRadius(dp(ctx, 18));
        bg.setStroke(dp(ctx, 2), UiStyles.color(ctx, accentColor));
        return bg;
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
