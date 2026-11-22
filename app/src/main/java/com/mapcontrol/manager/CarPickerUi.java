package com.mapcontrol.manager;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

/**
 * Yüzen menüden açılan seçim pencereleri (yansıtılacak uygulama, açık uygulamalar) için araç ölçüsü:
 * geniş kart, büyük yazı, parmakla kolay seçilen büyük kutucuklar.
 */
final class CarPickerUi {

    static final float TITLE_SP = 28f;
    static final float SUBTITLE_SP = 18f;
    static final float TILE_LABEL_SP = 21f;
    static final float TILE_SUB_SP = 16f;
    private static final float TILE_WIDTH_DP = 210f;
    private static final float TILE_ICON_DP = 76f;
    private static final float GRID_SPACING_DP = 14f;
    private static final float CLOSE_SIDE_DP = 64f;

    private CarPickerUi() {
    }

    private static float density(Context c) {
        return c.getResources().getDisplayMetrics().density;
    }

    private static int dp(Context c, float v) {
        return (int) (v * density(c) + 0.5f);
    }

    /** Ekranın çoğunu kaplayan kart genişliği (geniş araç ekranında 4-5 sütun). */
    static int cardWidth(Context c) {
        int w = c.getResources().getDisplayMetrics().widthPixels;
        return Math.min(w - dp(c, 48), dp(c, 1240));
    }

    static int cardHeight(Context c) {
        int h = c.getResources().getDisplayMetrics().heightPixels;
        return Math.min(h - dp(c, 40), (int) (h * 0.92f));
    }

    static int cardPadding(Context c) {
        return dp(c, 22);
    }

    static TextView title(Context c, CharSequence text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TITLE_SP);
        UiStyles.setFontStyle(t, Typeface.BOLD);
        t.setTextColor(UiStyles.color(c, R.color.textPrimary));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static TextView subtitle(Context c, CharSequence text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(SUBTITLE_SP);
        t.setTextColor(UiStyles.color(c, R.color.textHint));
        return t;
    }

    /** Büyük yuvarlak ✕ düğmesi. */
    static ImageView closeButton(Context c, View.OnClickListener onClick) {
        ImageView b = new ImageView(c);
        b.setImageResource(R.drawable.ic_mdi_close);
        b.setImageTintList(ColorStateList.valueOf(UiStyles.color(c, R.color.textPrimary)));
        int pad = dp(c, 16);
        b.setPadding(pad, pad, pad, pad);
        b.setBackground(ripple(c, UiStyles.color(c, R.color.surfaceCardInner), true, 0f));
        b.setOnClickListener(onClick);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, CLOSE_SIDE_DP), dp(c, CLOSE_SIDE_DP)));
        return b;
    }

    static GridView grid(Context c) {
        GridView g = new GridView(c);
        g.setColumnWidth(dp(c, TILE_WIDTH_DP));
        g.setNumColumns(GridView.AUTO_FIT);
        g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        int gap = dp(c, GRID_SPACING_DP);
        g.setHorizontalSpacing(gap);
        g.setVerticalSpacing(gap);
        g.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        g.setClipToPadding(false);
        g.setVerticalScrollBarEnabled(true);
        g.setFastScrollEnabled(false);
        return g;
    }

    /** Kutucuk görünümü (geri dönüştürülebilir). */
    static View tile(Context c, View convertView, Drawable icon, CharSequence label, CharSequence sub,
            boolean highlighted) {
        LinearLayout tile;
        ImageView iv;
        TextView t1;
        TextView t2;
        if (convertView instanceof LinearLayout && ((LinearLayout) convertView).getChildCount() == 3) {
            tile = (LinearLayout) convertView;
            iv = (ImageView) tile.getChildAt(0);
            t1 = (TextView) tile.getChildAt(1);
            t2 = (TextView) tile.getChildAt(2);
        } else {
            tile = new LinearLayout(c);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            int p = dp(c, 14);
            tile.setPadding(p, dp(c, 18), p, dp(c, 16));
            tile.setMinimumHeight(dp(c, 196));

            iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(iv, new LinearLayout.LayoutParams(dp(c, TILE_ICON_DP), dp(c, TILE_ICON_DP)));

            t1 = new TextView(c);
            t1.setTextSize(TILE_LABEL_SP);
            UiStyles.setFontStyle(t1, Typeface.BOLD);
            t1.setTextColor(UiStyles.color(c, R.color.textPrimary));
            t1.setGravity(Gravity.CENTER);
            t1.setMaxLines(2);
            t1.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            l1.topMargin = dp(c, 12);
            tile.addView(t1, l1);

            t2 = new TextView(c);
            t2.setTextSize(TILE_SUB_SP);
            t2.setTextColor(UiStyles.color(c, R.color.textHint));
            t2.setGravity(Gravity.CENTER);
            t2.setSingleLine(true);
            t2.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            l2.topMargin = dp(c, 4);
            tile.addView(t2, l2);
        }
        int fill = UiStyles.color(c, R.color.surfaceCardInner);
        tile.setBackground(ripple(c, fill, false, highlighted ? 3f : 0f));
        if (icon != null) {
            iv.setImageDrawable(icon);
        } else {
            iv.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        t1.setText(label);
        t2.setText(sub);
        t2.setVisibility(TextUtils.isEmpty(sub) ? View.GONE : View.VISIBLE);
        return tile;
    }

    /** Yuvarlak köşeli (ya da daire) dolgu + dokunma dalgası; strokeDp>0 ise vurgu çerçevesi. */
    static Drawable ripple(Context c, int fill, boolean oval, float strokeDp) {
        float radius = dp(c, 18);
        GradientDrawable content = new GradientDrawable();
        content.setShape(oval ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
        if (!oval) {
            content.setCornerRadius(radius);
        }
        content.setColor(fill);
        if (strokeDp > 0f) {
            content.setStroke(dp(c, strokeDp), UiStyles.color(c, R.color.accent));
        }
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(oval ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
        if (!oval) {
            mask.setCornerRadius(radius);
        }
        mask.setColor(Color.WHITE);
        int rippleColor = ColorUtils.setAlphaComponent(UiStyles.color(c, R.color.textPrimary), 0x44);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }
}
