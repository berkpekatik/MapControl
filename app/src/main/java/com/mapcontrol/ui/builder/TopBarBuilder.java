package com.mapcontrol.ui.builder;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

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
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(0xFF1C2630);
        topBar.setPadding(24, 16, 16, 16);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        titleView = new TextView(context);
        titleView.setText("Wi-Fi Yönetimi");
        titleView.setTextSize(20);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setClickable(true);
        titleView.setFocusable(true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        topBar.addView(titleView, titleParams);

        buttonsContainer = new LinearLayout(context);
        buttonsContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonsContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams buttonsContainerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        topBar.addView(buttonsContainer, buttonsContainerParams);

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
