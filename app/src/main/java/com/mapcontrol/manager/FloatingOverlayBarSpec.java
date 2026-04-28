package com.mapcontrol.manager;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextPaint;
import android.util.TypedValue;
import android.widget.Button;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

/**
 * Yüzen hızlı işlem, yansıtma ve geri tuşu için aynı ölçek (yazı, ikon, kare segment).
 */
public final class FloatingOverlayBarSpec {

    public static final float ROW_TEXT_SIZE_SP = 12f;
    public static final float ROW_ICON_SIZE_DP = 20f;
    public static final float ROW_INNER_PAD_DP = 4f;
    public static final float BAR_CARD_PAD_H_DP = 5f;
    public static final float BAR_CARD_PAD_V_DP = 4f;
    public static final float BAR_CORNER_DP = 10f;
    public static final float BAR_COLUMN_GAP_DP = 2f;
    public static final float ELEVATION_BACK_DP = 2f;

    private FloatingOverlayBarSpec() {
    }

    public static int dpToPx(float dp, float density) {
        return (int) (dp * density + 0.5f);
    }

    public static int rowIconSizePx(Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;
        return dpToPx(ROW_ICON_SIZE_DP, d);
    }

    public static int rowInnerPadPx(Context ctx) {
        return dpToPx(ROW_INNER_PAD_DP, ctx.getResources().getDisplayMetrics().density);
    }

    public static int compoundDrawablePaddingPx(Context ctx) {
        int px = UiStyles.dimenPx(ctx, R.dimen.spacing_small);
        return Math.max(2, (int) Math.round(px * 0.5));
    }

    /**
     * {@link FloatingBackButtonManager} yüzen geri tuşu ile aynı yüzey: surfaceCard, köşe, hafif gölge,
     * opaklık, tema animasyonu kapalı. Metin/ikon/ padding çağırandan önce veya sonra verilir.
     */
    public static void applyLikeFloatingBackButton(Button b, int surfaceCardArgb) {
        Context ctx = b.getContext();
        float d = ctx.getResources().getDisplayMetrics().density;
        b.setMinWidth(0);
        b.setMinHeight(0);
        UiStyles.applySolidRoundedBackgroundDp(b, surfaceCardArgb, BAR_CORNER_DP);
        b.setAlpha(0.96f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            b.setStateListAnimator(null);
            b.setElevation(ELEVATION_BACK_DP * d);
        }
    }

    /**
     * İki satırlı hücre + ikon yüksekliğine eşit; bar ile aynı yükseklikte, geri tuşu kare penceresinde
     * kullanılır (hizalı çerçeve).
     */
    /**
     * Geri tuşu penceresi ve tüm segment düğmeleri için aynı kenar (kare) uzunluğu, piksel.
     */
    public static int uniformCellSidePx(Context ctx) {
        return uniformFloatingControlSizePx(ctx);
    }

    public static int uniformFloatingControlSizePx(Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;
        int cardV2 = 2 * dpToPx(BAR_CARD_PAD_V_DP, d);
        int inner2 = 2 * dpToPx(ROW_INNER_PAD_DP, d);
        int icon = rowIconSizePx(ctx);
        int comp = compoundDrawablePaddingPx(ctx);
        TextPaint p = new TextPaint();
        p.setAntiAlias(true);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, ROW_TEXT_SIZE_SP, ctx.getResources().getDisplayMetrics()));
        Paint.FontMetrics fm = p.getFontMetrics();
        int line = (int) Math.ceil(fm.descent - fm.ascent) + 1;
        int twoLine = 2 * line + (int) (2f * d);
        int cellBlock = inner2 + icon + comp + twoLine;
        int h = cardV2 + cellBlock;
        int minT = (int) (24 * d);
        return Math.max(Math.min(h, (int) (90 * d)), minT);
    }
}
