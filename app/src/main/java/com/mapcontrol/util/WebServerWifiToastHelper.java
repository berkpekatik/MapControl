package com.mapcontrol.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;

/**
 * Sistem üstü ince Wi-Fi bantları: hazırlık (tazeleme) ve sonuç (başarı / hata) aynı kompakt düzende.
 */
public final class WebServerWifiToastHelper {

    private static final long SHOW_DURATION_MS = 4500L;
    /**
     * İçerik metne göre genişler; aşırı geniş cümlelerde üst sınır (ekran oranı).
     */
    private static final float BANNER_MAX_WIDTH_CAP_FRACTION = 0.98f;
    private static final float BANNER_Y_OFFSET_BELOW_CENTER_DP = 150f;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static View sOverlayView;
    private static Runnable sPendingDismiss;

    private static View sPreparingOverlayView;

    private enum StatusBanner {
        STABILIZING,
        SUCCESS,
        FAIL
    }

    private WebServerWifiToastHelper() {
    }

    /**
     * Wi-Fi stabilize zinciri başlarken: ufak, ekranda biraz aşağıda, bilgilendirme kartı.
     * Sonuç {@link #showSystemOverlay} veya {@link #dismissWifiStabilizePreparingOverlay} ile kapanır.
     */
    public static void showWifiStabilizePreparingOverlay(@NonNull Context context) {
        Context app = context.getApplicationContext();
        MAIN.post(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(app)) {
                Toast.makeText(app, R.string.wifi_global_stabilize_title, Toast.LENGTH_LONG).show();
                return;
            }
            removePreparingUnlockedInternal(app);
            sPreparingOverlayView = buildStatusBanner(app, StatusBanner.STABILIZING);
            try {
                addBannerToWindowUnlocked(app, sPreparingOverlayView);
            } catch (Exception e) {
                sPreparingOverlayView = null;
                Toast.makeText(app, R.string.wifi_global_stabilize_title, Toast.LENGTH_LONG).show();
                return;
            }
            sPreparingOverlayView.setAlpha(0f);
            sPreparingOverlayView.animate()
                    .alpha(1f)
                    .setDuration(200L)
                    .start();
        });
    }

    /**
     * "Hazırlanıyor" kartını kaldır (servis destroy / sonuç gelmeden iptal).
     */
    public static void dismissWifiStabilizePreparingOverlay(@NonNull Context context) {
        Context app = context.getApplicationContext();
        MAIN.post(() -> removePreparingUnlockedInternal(app));
    }

    public static void showSystemOverlay(@NonNull Context context, boolean wifiConnected) {
        Context app = context.getApplicationContext();

        MAIN.post(() -> {
            removePreparingUnlockedInternal(app);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(app)) {
                Toast.makeText(
                        app,
                        app.getString(wifiConnected
                                ? R.string.wifi_global_status_internet_ok
                                : R.string.wifi_global_status_wifi_failed),
                        Toast.LENGTH_LONG
                ).show();
                return;
            }
            removeOverlayUnlockedInternal(app);
            sOverlayView = buildStatusBanner(
                    app,
                    wifiConnected ? StatusBanner.SUCCESS : StatusBanner.FAIL);
            try {
                addBannerToWindowUnlocked(app, sOverlayView);
            } catch (Exception e) {
                sOverlayView = null;
                Toast.makeText(
                        app,
                        app.getString(wifiConnected
                                ? R.string.wifi_global_status_internet_ok
                                : R.string.wifi_global_status_wifi_failed),
                        Toast.LENGTH_LONG
                ).show();
                return;
            }
            sOverlayView.setAlpha(0f);
            sOverlayView.animate()
                    .alpha(1f)
                    .setDuration(200L)
                    .start();
            if (sPendingDismiss != null) {
                MAIN.removeCallbacks(sPendingDismiss);
            }
            sPendingDismiss = () -> dismissOverlayInternal(app);
            MAIN.postDelayed(sPendingDismiss, SHOW_DURATION_MS);
        });
    }

    private static void addBannerToWindowUnlocked(@NonNull Context app, @NonNull View banner)
            throws Exception {
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            throw new IllegalStateException("no WindowManager");
        }
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        int yOff = (int) (BANNER_Y_OFFSET_BELOW_CENTER_DP
                * app.getResources().getDisplayMetrics().density);
        int maxW = (int) (app.getResources().getDisplayMetrics().widthPixels
                * BANNER_MAX_WIDTH_CAP_FRACTION);
        int wms = View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST);
        int hms = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        banner.measure(wms, hms);
        int measuredW = banner.getMeasuredWidth();
        int measuredH = banner.getMeasuredHeight();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                measuredW,
                measuredH,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;
        params.y = yOff;
        wm.addView(banner, params);
    }

    @NonNull
    private static View buildStatusBanner(@NonNull Context app, @NonNull StatusBanner mode) {
        float d = app.getResources().getDisplayMetrics().density;
        int padH = Math.round(12f * d);
        int padV = Math.round(7f * d);

        int bg = R.drawable.bg_global_wifi_toast_error;
        int halo = R.drawable.bg_global_wifi_toast_halo_error;
        int icon = R.drawable.ic_mdi_close;
        int iconColor = R.color.wifi_toast_global_icon_tint_fail;
        int eyebrowId = 0;
        int mainId = 0;
        int eyebrowColor = 0;
        float mainSizeSp = 14f;
        float ele = 10f;

        switch (mode) {
            case STABILIZING:
                bg = R.drawable.bg_global_wifi_toast_preparing;
                halo = R.drawable.bg_global_wifi_toast_halo_preparing;
                icon = R.drawable.ic_mdi_timer_sand;
                iconColor = R.color.wifi_toast_preparing_icon;
                eyebrowId = R.string.wifi_global_stabilize_eyebrow;
                mainId = R.string.wifi_global_stabilize_title;
                eyebrowColor = R.color.wifi_toast_preparing_eyebrow;
                mainSizeSp = 15.5f;
                ele = 10f;
                break;
            case SUCCESS:
                bg = R.drawable.bg_global_wifi_toast_success;
                halo = R.drawable.bg_global_wifi_toast_halo_ok;
                icon = R.drawable.ic_mdi_check;
                iconColor = R.color.wifi_toast_global_icon_tint_ok;
                eyebrowId = R.string.wifi_global_toast_eyebrow_ok;
                mainId = R.string.wifi_global_status_internet_ok;
                eyebrowColor = R.color.wifi_toast_global_eyebrow_ok;
                mainSizeSp = 16.5f;
                ele = 10f;
                break;
            case FAIL:
                bg = R.drawable.bg_global_wifi_toast_error;
                halo = R.drawable.bg_global_wifi_toast_halo_error;
                icon = R.drawable.ic_mdi_close;
                iconColor = R.color.wifi_toast_global_icon_tint_fail;
                eyebrowId = R.string.wifi_global_toast_eyebrow_fail;
                mainId = R.string.wifi_global_status_wifi_failed;
                eyebrowColor = R.color.wifi_toast_global_eyebrow_fail;
                mainSizeSp = 16.5f;
                ele = 10f;
                break;
        }

        int haloSize = (int) (33 * d);
        int iconPx = (int) (21 * d);

        LinearLayout root = new LinearLayout(app);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(padH, padV, padH, padV);
        root.setBackgroundResource(bg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setClipToPadding(false);
            root.setElevation(ele * d);
        }
        root.setClickable(false);

        FrameLayout haloFl = new FrameLayout(app);
        haloFl.setBackgroundResource(halo);
        LinearLayout.LayoutParams haloLp = new LinearLayout.LayoutParams(haloSize, haloSize);
        root.addView(haloFl, haloLp);

        AppCompatImageView ico = new AppCompatImageView(app);
        FrameLayout.LayoutParams iconFl = new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER);
        ico.setImageResource(icon);
        ico.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(app, iconColor)));
        ico.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        haloFl.addView(ico, iconFl);

        LinearLayout textCol = new LinearLayout(app);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        colLp.setMarginStart((int) (8 * d));
        root.addView(textCol, colLp);

        TextView eyebrow = new TextView(app);
        eyebrow.setText(eyebrowId);
        eyebrow.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                mode == StatusBanner.STABILIZING ? 11.5f : 12f);
        eyebrow.setLetterSpacing(0.08f);
        eyebrow.setTextColor(ContextCompat.getColor(app, eyebrowColor));
        setMediumTypeface(eyebrow);
        textCol.addView(eyebrow);

        TextView main = new TextView(app);
        main.setText(mainId);
        main.setTextSize(TypedValue.COMPLEX_UNIT_SP, mainSizeSp);
        main.setTextColor(ContextCompat.getColor(app, R.color.wifi_toast_global_text_primary));
        setNormalTypeface(main);
        main.setLineSpacing(2.2f * d, 1f);
        LinearLayout.LayoutParams mainLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mainLp.topMargin = (int) (2.5f * d);
        textCol.addView(main, mainLp);

        return root;
    }

    private static void removePreparingUnlockedInternal(@NonNull Context app) {
        if (sPreparingOverlayView == null) {
            return;
        }
        View v = sPreparingOverlayView;
        sPreparingOverlayView = null;
        v.clearAnimation();
        v.animate().cancel();
        removeViewFromWm(v, app);
    }

    private static void removeViewFromWm(@NonNull View v, @NonNull Context app) {
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            try {
                wm.removeView(v);
            } catch (Exception ignored) {
            }
        }
    }

    private static void setMediumTypeface(TextView v) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            v.setTypeface(Typeface.create(Typeface.SANS_SERIF, 500, false));
        } else {
            v.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        }
    }

    private static void setNormalTypeface(TextView v) {
        v.setTypeface(Typeface.SANS_SERIF, Typeface.NORMAL);
    }

    private static void removeOverlayUnlockedInternal(@NonNull Context app) {
        if (sPendingDismiss != null) {
            MAIN.removeCallbacks(sPendingDismiss);
            sPendingDismiss = null;
        }
        if (sOverlayView == null) {
            return;
        }
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            try {
                wm.removeView(sOverlayView);
            } catch (Exception ignored) {
            }
        }
        sOverlayView = null;
    }

    private static void dismissOverlayInternal(@NonNull Context app) {
        sPendingDismiss = null;
        View v = sOverlayView;
        if (v == null) {
            return;
        }
        v.animate()
                .alpha(0f)
                .setDuration(240L)
                .withEndAction(() -> removeOverlayUnlockedInternal(app))
                .start();
    }
}
