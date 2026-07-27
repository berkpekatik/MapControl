package com.mapcontrol.ui.builder;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

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
    private AppCompatImageButton launcherBackButton;
    private View topBarDivider;

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

        float density = context.getResources().getDisplayMetrics().density;
        int backBtnSize = (int) (48 * density);
        int iconPad = (int) (10 * density);

        launcherBackButton = new AppCompatImageButton(context);
        launcherBackButton.setVisibility(View.GONE);
        launcherBackButton.setContentDescription("Araç paneline dön");
        launcherBackButton.setMinimumWidth(0);
        launcherBackButton.setMinimumHeight(0);
        launcherBackButton.setPadding(iconPad, iconPad, iconPad, iconPad);
        launcherBackButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        launcherBackButton.setAdjustViewBounds(false);
        UiStyles.setGlassCardBackground(launcherBackButton);
        launcherBackButton.setImageResource(R.drawable.ic_mdi_chevron_double_left);
        ImageViewCompat.setImageTintList(launcherBackButton,
                ColorStateList.valueOf(UiStyles.color(context, R.color.textPrimary)));
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(backBtnSize, backBtnSize);
        backParams.setMargins(0, 0, (int) (12 * density), 0);
        row.addView(launcherBackButton, backParams);

        titleView = new TextView(context);
        titleView.setText("Wi-Fi Yönetimi");
        titleView.setTextSize(18);
        titleView.setTextColor(UiStyles.color(context, R.color.textPrimary));
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

        topBarDivider = new View(context);
        UiStyles.setBackgroundRes(topBarDivider, R.drawable.bg_topbar_divider);
        int oneDp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, context.getResources().getDisplayMetrics());
        topBar.addView(topBarDivider, new LinearLayout.LayoutParams(
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

    public void setLauncherBackButtonVisible(boolean visible) {
        if (launcherBackButton != null) {
            launcherBackButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void setTopBarVisible(boolean visible) {
        if (topBar != null) {
            topBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void setLauncherBackButtonListener(View.OnClickListener listener) {
        if (launcherBackButton != null) {
            launcherBackButton.setOnClickListener(listener);
        }
    }

    /** Light/dark değişiminde başlık / geri düğmesi renklerini yeniler. */
    public void reapplyTheme() {
        if (titleView != null) {
            titleView.setTextColor(UiStyles.color(context, R.color.textPrimary));
        }
        if (launcherBackButton != null) {
            UiStyles.setGlassCardBackground(launcherBackButton);
            ImageViewCompat.setImageTintList(launcherBackButton,
                    ColorStateList.valueOf(UiStyles.color(context, R.color.textPrimary)));
        }
        if (topBarDivider != null) {
            UiStyles.setBackgroundRes(topBarDivider, R.drawable.bg_topbar_divider);
        }
    }

    public TextView getTitleView() {
        return titleView;
    }

    public LinearLayout getButtonsContainer() {
        return buttonsContainer;
    }
}
