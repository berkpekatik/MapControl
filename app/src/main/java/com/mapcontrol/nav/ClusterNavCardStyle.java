package com.mapcontrol.nav;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.launcher.SahneFonts;
import com.mapcontrol.ui.theme.UiStyles;

/**
 * Cluster navigasyon kartının (Google Maps + Yandex) ortak görsel stili.
 * Tek kart: üstte manevra bölümü, ince ayraç, altta üç sütunlu ETA (Kalan / Varış / Süre).
 */
final class ClusterNavCardStyle {

    static final int CARD_WIDTH_DP = 260;
    static final int SLIM_WIDTH_DP = 520;
    /** Konum sınırlarını hesaplamak için tahmini kart yükseklikleri (dp). */
    static final int DETAIL_HEIGHT_DP = 150;
    static final int SLIM_HEIGHT_DP = 46;
    static final int GLASS_HEIGHT_DP = 164;
    private static final int GLASS_RADIUS_DP = 24;
    private static final int YANDEX_TOP_MARGIN_DP = 80;
    private static final int GOOGLE_TOP_MARGIN_DP = 110;

    static int topMarginDp(ClusterNavOverlayOffsets.Provider provider) {
        return provider == ClusterNavOverlayOffsets.Provider.GOOGLE
                ? GOOGLE_TOP_MARGIN_DP : YANDEX_TOP_MARGIN_DP;
    }

    static int heightDp(ClusterNavOverlayOffsets.Style style) {
        switch (style) {
            case SLIM:
                return SLIM_HEIGHT_DP;
            case GLASS:
                return GLASS_HEIGHT_DP;
            default:
                return DETAIL_HEIGHT_DP;
        }
    }
    static final int CARD_MIN_HEIGHT_DP = 120;
    private static final int CARD_PADDING_H_DP = 16;
    private static final int CARD_PADDING_V_DP = 14;
    private static final int DIVIDER_MARGIN_DP = 10;

    private ClusterNavCardStyle() {
    }

    static final class MetricRow {
        final LinearLayout row;
        final TextView value;

        MetricRow(LinearLayout row, TextView value) {
            this.row = row;
            this.value = value;
        }
    }

    /** Birleşik kartın tüm görünümleri. */
    static final class NavCard {
        LinearLayout card;
        LinearLayout maneuverSection;
        TextView distance;
        TextView street;
        TextView status;
        View divider;
        LinearLayout etaSection;
        LinearLayout metricsRow;
        MetricRow etaDistance;
        MetricRow etaArrival;
        MetricRow etaTime;
        TextView etaStatus;
    }

    static int widthDp(ClusterNavOverlayOffsets.Style style) {
        return style == ClusterNavOverlayOffsets.Style.SLIM ? SLIM_WIDTH_DP : CARD_WIDTH_DP;
    }

    static NavCard createNavCard(Context ctx, ClusterNavOverlayOffsets.Style style) {
        switch (style) {
            case SLIM:
                return createSlimCard(ctx);
            case GLASS:
                return createGlassCard(ctx);
            default:
                return createDetailCard(ctx);
        }
    }

    /** Launcher camı: tema renkli zemin, ince büyük mesafe, altta değer üstte / etiket altta üç sütun. */
    private static NavCard createGlassCard(Context ctx) {
        NavCard c = new NavCard();

        c.card = new LinearLayout(ctx);
        c.card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
                UiStyles.color(ctx, R.color.cluster_nav_glass_top),
                UiStyles.color(ctx, R.color.cluster_nav_glass_bottom)});
        bg.setCornerRadius(dp(ctx, GLASS_RADIUS_DP));
        bg.setStroke(Math.max(1, dp(ctx, 1)), UiStyles.color(ctx, R.color.cluster_nav_glass_stroke));
        c.card.setBackground(bg);
        int pad = dp(ctx, 18);
        c.card.setPadding(pad, dp(ctx, 16), pad, dp(ctx, 16));
        c.card.setMinimumHeight(dp(ctx, CARD_MIN_HEIGHT_DP));

        c.maneuverSection = new LinearLayout(ctx);
        c.maneuverSection.setOrientation(LinearLayout.VERTICAL);
        c.distance = glassText(ctx, 36, 300, R.color.cluster_nav_glass_numeral);
        c.distance.setSingleLine(true);
        c.maneuverSection.addView(c.distance, wrapLp());

        c.street = glassText(ctx, 15, 500, R.color.cluster_nav_glass_text);
        c.street.setMaxLines(2);
        LinearLayout.LayoutParams streetLp = wrapLp();
        streetLp.topMargin = dp(ctx, 2);
        c.maneuverSection.addView(c.street, streetLp);

        c.status = glassText(ctx, 12, 400, R.color.cluster_nav_glass_subtext);
        c.status.setMaxLines(2);
        LinearLayout.LayoutParams statusLp = wrapLp();
        statusLp.topMargin = dp(ctx, 2);
        c.maneuverSection.addView(c.status, statusLp);
        c.card.addView(c.maneuverSection, matchWrapLp());

        c.divider = new View(ctx);
        c.divider.setBackgroundColor(UiStyles.color(ctx, R.color.cluster_nav_glass_hair));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(ctx, 1)));
        divLp.topMargin = dp(ctx, 12);
        divLp.bottomMargin = dp(ctx, 12);
        c.card.addView(c.divider, divLp);

        c.etaSection = new LinearLayout(ctx);
        c.etaSection.setOrientation(LinearLayout.VERTICAL);
        c.metricsRow = new LinearLayout(ctx);
        c.metricsRow.setOrientation(LinearLayout.HORIZONTAL);
        c.etaDistance = addGlassMetric(c.metricsRow, ctx, R.string.cluster_nav_eta_remaining);
        c.etaArrival = addGlassMetric(c.metricsRow, ctx, R.string.cluster_nav_eta_arrival);
        c.etaTime = addGlassMetric(c.metricsRow, ctx, R.string.cluster_nav_eta_duration);
        c.etaSection.addView(c.metricsRow, matchWrapLp());

        c.etaStatus = glassText(ctx, 12, 400, R.color.cluster_nav_glass_subtext);
        c.etaStatus.setMaxLines(2);
        c.etaSection.addView(c.etaStatus, wrapLp());
        c.card.addView(c.etaSection, matchWrapLp());
        return c;
    }

    private static MetricRow addGlassMetric(LinearLayout parent, Context ctx, int labelResId) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView valueView = glassText(ctx, 17, 600, R.color.cluster_nav_glass_text);
        valueView.setSingleLine(true);
        col.addView(valueView, wrapLp());

        TextView labelView = glassText(ctx, 11, 400, R.color.cluster_nav_glass_subtext);
        labelView.setText(labelResId);
        labelView.setSingleLine(true);
        col.addView(labelView, wrapLp());

        parent.addView(col, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return new MetricRow(col, valueView);
    }

    private static TextView glassText(Context ctx, int sp, int weight, int colorRes) {
        TextView tv = createText(ctx, sp, false, colorRes);
        tv.setTypeface(SahneFonts.INSTANCE.get(ctx, weight));
        tv.setFontFeatureSettings("tnum");
        return tv;
    }

    /** İnce çizgi: tek satır — mesafe, sokak | kalan, varış, süre. Durum metinleri gösterilmez. */
    private static NavCard createSlimCard(Context ctx) {
        NavCard c = new NavCard();
        c.card = new LinearLayout(ctx);
        c.card.setOrientation(LinearLayout.HORIZONTAL);
        c.card.setGravity(Gravity.CENTER_VERTICAL);
        c.card.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_cluster_nav_unified));
        c.card.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8));

        c.maneuverSection = new LinearLayout(ctx);
        c.maneuverSection.setOrientation(LinearLayout.HORIZONTAL);
        c.maneuverSection.setGravity(Gravity.CENTER_VERTICAL);
        c.distance = createText(ctx, 22, true, R.color.cluster_nav_maneuver_text);
        c.distance.setSingleLine(true);
        c.maneuverSection.addView(c.distance, wrapLp());
        c.street = createText(ctx, 14, false, R.color.cluster_nav_maneuver_subtext);
        c.street.setSingleLine(true);
        LinearLayout.LayoutParams streetLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        streetLp.setMarginStart(dp(ctx, 10));
        c.maneuverSection.addView(c.street, streetLp);
        // Slim modda gösterilmez; bağlama kodu null olmasın diye eklenmeden oluşturulur.
        c.status = createText(ctx, 12, false, R.color.cluster_nav_maneuver_subtext);
        c.card.addView(c.maneuverSection, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        c.divider = new View(ctx);
        c.divider.setBackgroundColor(UiStyles.color(ctx, R.color.cluster_nav_maneuver_stroke));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                Math.max(1, dp(ctx, 1)), dp(ctx, 22));
        divLp.setMarginStart(dp(ctx, 12));
        divLp.setMarginEnd(dp(ctx, 12));
        c.card.addView(c.divider, divLp);

        c.etaSection = new LinearLayout(ctx);
        c.etaSection.setOrientation(LinearLayout.HORIZONTAL);
        c.etaSection.setGravity(Gravity.CENTER_VERTICAL);
        c.metricsRow = new LinearLayout(ctx);
        c.metricsRow.setOrientation(LinearLayout.HORIZONTAL);
        c.metricsRow.setGravity(Gravity.CENTER_VERTICAL);
        c.etaDistance = addInlineMetric(c.metricsRow, ctx, true);
        c.etaArrival = addInlineMetric(c.metricsRow, ctx, false);
        c.etaTime = addInlineMetric(c.metricsRow, ctx, false);
        c.etaSection.addView(c.metricsRow, wrapLp());
        c.etaStatus = createText(ctx, 12, false, R.color.cluster_nav_maneuver_subtext);
        c.card.addView(c.etaSection, wrapLp());
        return c;
    }

    private static MetricRow addInlineMetric(LinearLayout parent, Context ctx, boolean first) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.HORIZONTAL);
        TextView valueView = createText(ctx, 16, true, R.color.cluster_nav_maneuver_text);
        valueView.setSingleLine(true);
        col.addView(valueView, wrapLp());
        LinearLayout.LayoutParams lp = wrapLp();
        if (!first) {
            lp.setMarginStart(dp(ctx, 14));
        }
        parent.addView(col, lp);
        return new MetricRow(col, valueView);
    }

    private static NavCard createDetailCard(Context ctx) {
        NavCard c = new NavCard();

        c.card = new LinearLayout(ctx);
        c.card.setOrientation(LinearLayout.VERTICAL);
        c.card.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        c.card.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_cluster_nav_unified));
        int padH = dp(ctx, CARD_PADDING_H_DP);
        int padV = dp(ctx, CARD_PADDING_V_DP);
        c.card.setPadding(padH, padV, padH, padV);
        c.card.setMinimumHeight(dp(ctx, CARD_MIN_HEIGHT_DP));

        // Manevra bölümü
        c.maneuverSection = new LinearLayout(ctx);
        c.maneuverSection.setOrientation(LinearLayout.VERTICAL);
        c.distance = createText(ctx, 30, true, R.color.cluster_nav_maneuver_text);
        c.distance.setSingleLine(true);
        c.maneuverSection.addView(c.distance, wrapLp());

        c.street = createText(ctx, 14, false, R.color.cluster_nav_maneuver_subtext);
        c.street.setMaxLines(2);
        LinearLayout.LayoutParams streetLp = wrapLp();
        streetLp.topMargin = dp(ctx, 4);
        c.maneuverSection.addView(c.street, streetLp);

        c.status = createText(ctx, 12, false, R.color.cluster_nav_maneuver_subtext);
        c.status.setMaxLines(2);
        LinearLayout.LayoutParams statusLp = wrapLp();
        statusLp.topMargin = dp(ctx, 2);
        c.maneuverSection.addView(c.status, statusLp);
        c.card.addView(c.maneuverSection, matchWrapLp());

        // Ayraç
        c.divider = new View(ctx);
        c.divider.setBackgroundColor(UiStyles.color(ctx, R.color.cluster_nav_maneuver_stroke));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(ctx, 1)));
        divLp.topMargin = dp(ctx, DIVIDER_MARGIN_DP);
        divLp.bottomMargin = dp(ctx, DIVIDER_MARGIN_DP);
        c.card.addView(c.divider, divLp);

        // ETA bölümü
        c.etaSection = new LinearLayout(ctx);
        c.etaSection.setOrientation(LinearLayout.VERTICAL);
        c.metricsRow = new LinearLayout(ctx);
        c.metricsRow.setOrientation(LinearLayout.HORIZONTAL);
        c.etaDistance = addMetricColumn(c.metricsRow, ctx, R.string.cluster_nav_eta_remaining);
        c.etaArrival = addMetricColumn(c.metricsRow, ctx, R.string.cluster_nav_eta_arrival);
        c.etaTime = addMetricColumn(c.metricsRow, ctx, R.string.cluster_nav_eta_duration);
        c.etaSection.addView(c.metricsRow, matchWrapLp());

        c.etaStatus = createText(ctx, 12, false, R.color.cluster_nav_maneuver_subtext);
        c.etaStatus.setMaxLines(2);
        c.etaSection.addView(c.etaStatus, wrapLp());
        c.card.addView(c.etaSection, matchWrapLp());
        return c;
    }

    /** Etiket üstte (küçük), değer altta (kalın); sütunlar eşit genişlikte. */
    private static MetricRow addMetricColumn(LinearLayout parent, Context ctx, int labelResId) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView labelView = createText(ctx, 11, false, R.color.cluster_nav_maneuver_subtext);
        labelView.setText(labelResId);
        labelView.setSingleLine(true);
        col.addView(labelView, wrapLp());

        TextView valueView = createText(ctx, 16, true, R.color.cluster_nav_maneuver_text);
        valueView.setSingleLine(true);
        col.addView(valueView, wrapLp());

        parent.addView(col, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return new MetricRow(col, valueView);
    }

    private static TextView createText(Context ctx, int sp, boolean bold, int colorRes) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setGravity(Gravity.START);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) {
            UiStyles.setFontStyle(tv, Typeface.BOLD);
        }
        tv.setTextColor(UiStyles.color(ctx, colorRes));
        return tv;
    }

    static LinearLayout.LayoutParams wrapLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams matchWrapLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    static int dp(Context ctx, int value) {
        float d = ctx.getResources().getDisplayMetrics().density;
        return Math.round(value * d);
    }
}
