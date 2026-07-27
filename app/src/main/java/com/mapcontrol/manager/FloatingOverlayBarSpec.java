package com.mapcontrol.manager;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

/**
 * Yüzen hızlı işlem, yansıtma ve geri tuşu için aynı ölçek (yazı, ikon, kare segment).
 */
public final class FloatingOverlayBarSpec {

    public static final float ROW_TEXT_SIZE_SP = 12f;
    /** Yüzen bar düğümleri: {@link #uniformCellSidePx} ile küçültülmüş hücre + bu yazı boyutu. */
    public static final float OVERLAY_ROW_TEXT_SIZE_SP = ROW_TEXT_SIZE_SP * 2f;
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

    /**
     * Hub ikonları: hücre içinde mümkün olduğunca büyük, ortalanmış tek renk çizim için kenar (px).
     */
    public static int hubOverlayIconPx(Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;
        int cell = uniformCellSidePx(ctx);
        int inner = rowInnerPadPx(ctx);
        int maxByCell = Math.max(1, cell - 2 * inner);
        int preferred = dpToPx(30f, d);
        int floor = dpToPx(22f, d);
        return Math.max(floor, Math.min(maxByCell, preferred));
    }

    public static int rowInnerPadPx(Context ctx) {
        return dpToPx(ROW_INNER_PAD_DP, ctx.getResources().getDisplayMetrics().density);
    }

    public static int compoundDrawablePaddingPx(Context ctx) {
        int px = UiStyles.dimenPx(ctx, R.dimen.spacing_small);
        return Math.max(2, (int) Math.round(px * 0.5));
    }

    /**
     * Yuvarlak köşeli yüzen hub hücresi: dolgu + dokunma dalgalanması (ripple).
     */
    public static void applyHubCellRippleBackground(Context ctx, View v) {
        float d = ctx.getResources().getDisplayMetrics().density;
        int fill = UiStyles.color(ctx, R.color.surfaceCardInner);
        GradientDrawable content = new GradientDrawable();
        content.setShape(GradientDrawable.RECTANGLE);
        content.setColor(fill);
        content.setCornerRadius(BAR_CORNER_DP * d);

        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.RECTANGLE);
        mask.setCornerRadius(BAR_CORNER_DP * d);
        mask.setColor(Color.WHITE);

        int rippleRgb = UiStyles.color(ctx, R.color.textPrimary);
        int rippleAlpha = ColorUtils.setAlphaComponent(rippleRgb, 0x55);
        RippleDrawable ripple =
                new RippleDrawable(ColorStateList.valueOf(rippleAlpha), content, mask);
        v.setBackground(ripple);
        v.setAlpha(0.96f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            v.setStateListAnimator(null);
            v.setElevation(ELEVATION_BACK_DP * d);
            v.setClipToOutline(true);
            v.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        }
    }

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
     * Geri tuşu ve yan menü düğmeleri: önceki ölçeğin ~½’si; yazı {@link #OVERLAY_ROW_TEXT_SIZE_SP}.
     */
    public static int uniformCellSidePx(Context ctx) {
        int full = uniformFloatingControlSizePxForText(ctx, OVERLAY_ROW_TEXT_SIZE_SP);
        float d = ctx.getResources().getDisplayMetrics().density;
        int half = full / 2;
        int floor = (int) (18 * d);
        return Math.max(half, floor);
    }

    /**
     * Eski tek doğrusal ölçü (ikon + iki satır metin); başka yerden çağrılmıyorsa {@link #uniformCellSidePx} kullanın.
     */
    public static int uniformFloatingControlSizePx(Context ctx) {
        return uniformFloatingControlSizePxForText(ctx, ROW_TEXT_SIZE_SP);
    }

    private static int uniformFloatingControlSizePxForText(Context ctx, float textSizeSp) {
        float d = ctx.getResources().getDisplayMetrics().density;
        int cardV2 = 2 * dpToPx(BAR_CARD_PAD_V_DP, d);
        int inner2 = 2 * dpToPx(ROW_INNER_PAD_DP, d);
        int icon = rowIconSizePx(ctx);
        int comp = compoundDrawablePaddingPx(ctx);
        TextPaint p = new TextPaint();
        p.setAntiAlias(true);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, textSizeSp, ctx.getResources().getDisplayMetrics()));
        Paint.FontMetrics fm = p.getFontMetrics();
        int line = (int) Math.ceil(fm.descent - fm.ascent) + 1;
        int twoLine = 2 * line + (int) (2f * d);
        int cellBlock = inner2 + icon + comp + twoLine;
        int h = cardV2 + cellBlock;
        int minT = (int) (24 * d);
        return Math.max(Math.min(h, (int) (90 * d)), minT);
    }
}
