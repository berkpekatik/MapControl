package com.mapcontrol.ui.builder;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;

public class TopBarBuilder {
    public interface TopBarCallback {
        void onLogTabToggle(boolean show);
        void log(String message);
    }

    private final Context context;
    private final TopBarCallback callback;
    private final Handler titleClickHandler;
    private final Runnable titleClickReset;
    private int titleClickCount = 0;
    private boolean isLogTabVisible = false;

    private LinearLayout topBar;
    private TextView titleView;
    private LinearLayout buttonsContainer;

    public TopBarBuilder(Context context, TopBarCallback callback) {
        this.context = context;
        this.callback = callback;
        this.titleClickHandler = new Handler(Looper.getMainLooper());
        this.titleClickReset = () -> titleClickCount = 0;
    }

    public LinearLayout build() {
        topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(Color.TRANSPARENT);
        row.setPadding(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, context.getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, context.getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, context.getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, context.getResources().getDisplayMetrics()));
        row.setGravity(Gravity.CENTER_VERTICAL);

        titleView = new TextView(context);
        titleView.setText("Wi-Fi Yönetimi");
        titleView.setTextSize(18);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setClickable(true);
        titleView.setFocusable(true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(titleView, titleParams);

        buttonsContainer = new LinearLayout(context);
        buttonsContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonsContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams buttonsContainerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        row.addView(buttonsContainer, buttonsContainerParams);

        topBar.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        View divider = new View(context);
        divider.setBackgroundResource(R.drawable.bg_topbar_divider);
        int oneDp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, context.getResources().getDisplayMetrics());
        topBar.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                oneDp));

        titleView.setOnClickListener(v -> {
            titleClickCount++;
            titleClickHandler.removeCallbacks(titleClickReset);

            if (titleClickCount >= 3) {
                isLogTabVisible = !isLogTabVisible;
                callback.onLogTabToggle(isLogTabVisible);
                callback.log("Log sekmesi " + (isLogTabVisible ? "açıldı" : "kapatıldı"));
                titleClickCount = 0;
            } else {
                titleClickHandler.postDelayed(titleClickReset, 1000);
            }
        });

        return topBar;
    }

    public TextView getTitleView() {
        return titleView;
    }

    public LinearLayout getButtonsContainer() {
        return buttonsContainer;
    }
}
