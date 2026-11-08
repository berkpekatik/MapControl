package com.mapcontrol.nav;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import com.mapcontrol.R;

/**
 * Ayarlardaki kart seçicisi için önizleme: cluster'ı çizen fabrikanın ({@link ClusterNavCardStyle})
 * ürettiği gerçek kart, örnek veriyle doldurulup kutuya sığacak şekilde küçültülür ve ortalanır.
 */
public final class ClusterNavStylePreview extends FrameLayout {

    /** İnce çizgi gerçekte çok geniş; bu genişlikte çizilir ki küçültülünce yazısı okunabilsin. */
    private static final int MAX_PREVIEW_WIDTH_DP = 400;

    private final View card;
    private final int previewWidthPx;

    public ClusterNavStylePreview(Context context, ClusterNavOverlayOffsets.Style style) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        ClusterNavCardStyle.NavCard c = ClusterNavCardStyle.createNavCard(context, style);
        c.distance.setText("300 m");
        c.street.setText("Atatürk Cd.");
        c.status.setVisibility(GONE);
        c.etaStatus.setVisibility(GONE);
        c.etaDistance.value.setText("18 km");
        c.etaArrival.value.setText("14:52");
        c.etaTime.value.setText(context.getString(R.string.projection_cluster_preview_duration));
        card = c.card;
        previewWidthPx = ClusterNavCardStyle.dp(context,
                Math.min(ClusterNavCardStyle.widthDp(style), MAX_PREVIEW_WIDTH_DP));
        addView(card, new LayoutParams(previewWidthPx, LayoutParams.WRAP_CONTENT));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        // Kart kendi doğal yüksekliğinde ölçülür; kutuya sığdırma ölçekle yapılır (yoksa içerik kırpılır).
        card.measure(MeasureSpec.makeMeasureSpec(previewWidthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int w = right - left;
        int h = bottom - top;
        int cardW = card.getMeasuredWidth();
        int cardH = card.getMeasuredHeight();
        card.layout(0, 0, cardW, cardH);
        if (w <= 0 || h <= 0 || cardW <= 0 || cardH <= 0) {
            return;
        }
        float scale = Math.min(1f, Math.min((float) w / cardW, (float) h / cardH));
        card.setPivotX(0f);
        card.setPivotY(0f);
        card.setScaleX(scale);
        card.setScaleY(scale);
        card.setTranslationX((w - cardW * scale) / 2f);
        card.setTranslationY((h - cardH * scale) / 2f);
    }
}
