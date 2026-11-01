package com.mapcontrol.ui.theme;

import android.content.Context;
import android.view.View;
import android.widget.GridLayout;

/** Two features per row on the IVI; one column when the available width is narrow. */
public final class FeatureGridLayout extends GridLayout {
    private int arrangedColumns = -1;
    private final int twoColumnMinWidthDp;

    public FeatureGridLayout(Context context) { this(context, 840); }

    public FeatureGridLayout(Context context, int twoColumnMinWidthDp) {
        super(context);
        this.twoColumnMinWidthDp = twoColumnMinWidthDp;
        setOrientation(HORIZONTAL);
        setUseDefaultMargins(false);
        setAlignmentMode(ALIGN_BOUNDS);
    }

    public void addCard(View card) {
        LayoutParams lp = new LayoutParams();
        int gap = Math.round(6 * getResources().getDisplayMetrics().density);
        lp.setMargins(gap, gap, gap, gap);
        lp.height = LayoutParams.WRAP_CONTENT;
        addView(card, lp);
        arrangedColumns = -1;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        float density = getResources().getDisplayMetrics().density;
        int available = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int columns = available >= Math.round(twoColumnMinWidthDp * density) ? 2 : 1;

        int visibleIndex = 0;
        boolean rearrange = arrangedColumns != columns;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            boolean visible = child.getVisibility() != View.GONE;
            int column = visible ? visibleIndex % columns : 0;
            int row = visible ? visibleIndex / columns : 0;
            rearrange |= !lp.columnSpec.equals(spec(column, FILL))
                    || !lp.rowSpec.equals(spec(row, FILL));
            if (visible) visibleIndex++;
        }
        int totalRows = Math.max(1, (visibleIndex + columns - 1) / columns);
        rearrange |= getRowCount() != totalRows;
        if (rearrange) {
            // GridLayout caches the old maximum span. Invalidate it before shrinking either axis.
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                lp.columnSpec = spec(UNDEFINED);
                lp.rowSpec = spec(UNDEFINED);
                child.setLayoutParams(lp);
            }
            setColumnCount(columns);
            setRowCount(totalRows);
        }

        visibleIndex = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            boolean visible = child.getVisibility() != View.GONE;
            int childWidth = Math.max(0, available / columns - lp.leftMargin - lp.rightMargin);
            if (rearrange || lp.width != childWidth) {
                lp.columnSpec = spec(visible ? visibleIndex % columns : 0, FILL);
                lp.rowSpec = spec(visible ? visibleIndex / columns : 0, FILL);
                lp.width = childWidth;
                child.setLayoutParams(lp);
            }
            if (visible) visibleIndex++;
        }
        arrangedColumns = columns;

        super.onMeasure(widthSpec, heightSpec);
    }
}
