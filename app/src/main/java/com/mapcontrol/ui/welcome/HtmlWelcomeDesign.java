package com.mapcontrol.ui.welcome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * HTML ile çizilen giriş tasarımı. Şablon {@link WelcomeHtmlSource} üzerinden gelir,
 * alanlar {@link WelcomeHtmlRenderer} ile doldurulur ve WebView'da gösterilir.
 *
 * <p>WebView açılamazsa (cihazda yoksa, bozuksa) varsa {@code fallback} tasarımı kullanılır.
 */
public final class HtmlWelcomeDesign implements WelcomeDesign {

    private static final String TAG = "HtmlWelcomeDesign";
    private static final int BACKGROUND = 0xFF10141B;
    private static final String BASE_URL = "https://welcome.local/";

    private final String id;
    @StringRes
    private final int titleRes;
    @StringRes
    private final int helpRes;
    private final WelcomeHtmlSource source;
    @Nullable
    private final WelcomeDesign fallback;

    public HtmlWelcomeDesign(String id, @StringRes int titleRes, @StringRes int helpRes,
            WelcomeHtmlSource source, @Nullable WelcomeDesign fallback) {
        this.id = id;
        this.titleRes = titleRes;
        this.helpRes = helpRes;
        this.source = source;
        this.fallback = fallback;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int titleRes() {
        return titleRes;
    }

    @Override
    public int helpRes() {
        return helpRes;
    }

    @Nullable
    @Override
    public View createView(Context context) {
        try {
            String html = WelcomeHtmlRenderer.render(context, source.load(context));
            return new HtmlHost(context, html);
        } catch (Exception e) {
            Log.w(TAG, "HTML tasarım açılamadı: " + id, e);
            return fallback != null ? fallback.createView(context) : null;
        }
    }

    /** WebView'ı saran, tüm dokunuşları kendine alan kap. Dokununca ekran kapanır. */
    private static final class HtmlHost extends FrameLayout {

        private final WebView web;

        @SuppressLint("SetJavaScriptEnabled")
        HtmlHost(Context context, String html) {
            super(context);
            setBackgroundColor(BACKGROUND);

            web = new WebView(context);
            web.setBackgroundColor(BACKGROUND);
            web.setVerticalScrollBarEnabled(false);
            web.setHorizontalScrollBarEnabled(false);
            web.setOverScrollMode(View.OVER_SCROLL_NEVER);

            WebSettings settings = web.getSettings();
            // Şablonlar yalnızca CSS ile hareket eder; ileride API'den gelen HTML için de kapalı.
            settings.setJavaScriptEnabled(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setSaveFormData(false);
            settings.setSupportZoom(false);
            // Uygulama gece temasında olsa bile tasarım renkleri bozulmasın.
            settings.setForceDark(WebSettings.FORCE_DARK_OFF);
            try {
                settings.setBlockNetworkLoads(true);
            } catch (SecurityException ignored) {
                // INTERNET izni yoksa zaten ağ yüklemesi olmaz.
            }

            addView(web, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            setClickable(true);
            web.loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", null);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return true;
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    web.stopLoading();
                    removeAllViews();
                    web.destroy();
                } catch (RuntimeException ignored) {
                }
            });
        }
    }
}
