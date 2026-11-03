package com.mapcontrol.nav;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

/**
 * Cluster navigasyon kartlarının (Google Maps + Yandex, manevra + ETA) ortak görsel stili.
 * Dört kart da aynı genişlik, minimum yükseklik, iç boşluk ve köşe yarıçapını paylaşır.
 */
final class ClusterNavCardStyle {

    /** Dört kart için ortak genişlik. */
    static final int CARD_WIDTH_DP = 176;
    /** Manevra ve ETA kartları yan yana eşit yükseklikte dursun diye. */
    static final int CARD_MIN_HEIGHT_DP = 120;
    private static final int CARD_PADDING_H_DP = 16;
    private static final int CARD_PADDING_V_DP = 14;
    private static final int METRIC_LABEL_WIDTH_DP = 50;
    private static final int METRIC_ROW_GAP_DP = 8;

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

    static LinearLayout createManeuverCard(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        card.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_cluster_nav_maneuver));
        applyCardMetrics(ctx, card);
        return card;
    }

    static LinearLayout createEtaCard(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        card.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_cluster_nav_eta));
        applyCardMetrics(ctx, card);
        return card;
    }

    private static void applyCardMetrics(Context ctx, LinearLayout card) {
        int padH = dp(ctx, CARD_PADDING_H_DP);
        int padV = dp(ctx, CARD_PADDING_V_DP);
        card.setPadding(padH, padV, padH, padV);
        card.setMinimumHeight(dp(ctx, CARD_MIN_HEIGHT_DP));
    }

    static TextView createManeuverText(Context ctx, int sp, boolean primary) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setGravity(Gravity.START);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        if (primary) {
            tv.setTypeface(null, Typeface.BOLD);
            tv.setSingleLine(true);
            tv.setTextColor(UiStyles.color(ctx, R.color.cluster_nav_maneuver_text));
        } else {
            tv.setTextColor(UiStyles.color(ctx, R.color.cluster_nav_maneuver_subtext));
        }
        return tv;
    }

    static TextView createEtaText(Context ctx, int sp, boolean bold) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setGravity(Gravity.START);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) {
            tv.setTypeface(null, Typeface.BOLD);
        }
        tv.setTextColor(UiStyles.color(ctx, R.color.textPrimary));
        return tv;
    }

    /** ETA kartı alt durum metni (satır yoksa). */
    static TextView createEtaStatusText(Context ctx) {
        TextView tv = createEtaText(ctx, 12, false);
        tv.setTextColor(UiStyles.color(ctx, R.color.cluster_nav_label));
        tv.setMaxLines(2);
        return tv;
    }

    /** Etiket solda, değer sağa hizalı; böylece iki ETA kartı da düzgün bir kolon oluşturur. */
    static MetricRow addMetricRow(LinearLayout parent, Context ctx, String label, boolean first) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView labelView = createEtaText(ctx, 13, false);
        labelView.setText(label);
        labelView.setSingleLine(true);
        labelView.setTextColor(UiStyles.color(ctx, R.color.cluster_nav_label));
        row.addView(labelView, new LinearLayout.LayoutParams(
                dp(ctx, METRIC_LABEL_WIDTH_DP),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView valueView = createEtaText(ctx, 17, true);
        valueView.setSingleLine(true);
        valueView.setGravity(Gravity.END);
        row.addView(valueView, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = first ? 0 : dp(ctx, METRIC_ROW_GAP_DP);
        parent.addView(row, rowLp);
        return new MetricRow(row, valueView);
    }

    static LinearLayout.LayoutParams wrapLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams cardLp(Context ctx) {
        return new LinearLayout.LayoutParams(
                dp(ctx, CARD_WIDTH_DP),
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    static int dp(Context ctx, int value) {
        float d = ctx.getResources().getDisplayMetrics().density;
        return Math.round(value * d);
    }
}
