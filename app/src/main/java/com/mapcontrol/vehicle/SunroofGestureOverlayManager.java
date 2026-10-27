package com.mapcontrol.vehicle;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatImageView;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

/**
 * Arka planda ve diğer tüm uygulamaların (Yandex Navigasyon, Spotify, vb.) üzerindeyken
 * çalışan iki parmakla hızlı jest (Sunroof Quick Gestures) kontrol yöneticisi.
 *
 * Jest Tanımları:
 * - İki parmakla SAĞA kaydırma: Cam Tavanı Aç
 * - İki parmakla SOLA kaydırma: Cam Tavanı Kapat
 * - İki parmakla YUKARI kaydırma: Perdeyi Kapat
 * - İki parmakla AŞAĞI kaydırma: Perdeyi Aç
 */
public final class SunroofGestureOverlayManager {

    private static final String TAG = "SunroofGestureOverlay";

    public static final String KEY_GESTURE_OVERLAY_ENABLED = "sunroof_gesture_overlay_enabled";
    public static final String KEY_GESTURE_OVERLAY_MODE = "sunroof_gesture_overlay_mode";
    public static final String KEY_GESTURE_HUD_ENABLED = "sunroof_gesture_hud_enabled";

    public static final int MODE_TOP_STRIP = 0;     // Ekranın Üst Şeridi (Önerilen - Uygulamaları engellemez)
    public static final int MODE_FLOATING_PILL = 1; // Yüzen Hızlı Jest Rozeti
    public static final int MODE_FULL_SCREEN = 2;   // Tam Ekran Jest Katmanı

    public enum GestureAction {
        SWIPE_LEFT,   // Sola -> Tavanı Kapat
        SWIPE_RIGHT,  // Sağa -> Tavanı Aç
        SWIPE_UP,     // Yukarı -> Perdeyi Kapat
        SWIPE_DOWN    // Aşağı -> Perdeyi Aç
    }

    private static volatile SunroofGestureOverlayManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private View overlayView;
    private WindowManager.LayoutParams overlayParams;
    private View hudView;
    private WindowManager.LayoutParams hudParams;
    private Runnable hudDismissRunnable;

    private SunroofGestureOverlayManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(SunroofAutomationEngine.PREFS_NAME, Context.MODE_PRIVATE);
        this.prefs.edit().putBoolean(KEY_GESTURE_OVERLAY_ENABLED, false).apply();
        this.windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
    }

    public static SunroofGestureOverlayManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SunroofGestureOverlayManager.class) {
                if (instance == null) {
                    instance = new SunroofGestureOverlayManager(context);
                }
            }
        }
        return instance;
    }

    public SharedPreferences getPrefs() {
        return prefs;
    }

    public boolean isEnabled() {
        return false;
    }

    public int getMode() {
        return prefs.getInt(KEY_GESTURE_OVERLAY_MODE, MODE_TOP_STRIP);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_GESTURE_OVERLAY_ENABLED, false).apply();
        mainHandler.post(this::updateState);
    }

    public void setMode(int mode) {
        prefs.edit().putInt(KEY_GESTURE_OVERLAY_MODE, mode).apply();
        mainHandler.post(this::updateState);
    }

    private boolean activityInForeground = false;

    public void onActivityResumed() {
        activityInForeground = true;
        mainHandler.post(this::updateState);
    }

    public void onActivityPaused() {
        activityInForeground = false;
        mainHandler.post(this::updateState);
    }

    public void startIfEnabled() {
        mainHandler.post(this::updateState);
    }

    public void stop() {
        mainHandler.post(this::hideOverlay);
    }

    public void updateState() {
        hideOverlay();
    }

    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(appContext);
        }
        return true;
    }

    @SuppressLint("ClickableViewAccessibility")
    public synchronized void showOverlay() {
        hideOverlay();
    }

    public synchronized void hideOverlay() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Throwable ignored) {
            }
            overlayView = null;
        }
    }

    /**
     * İki parmakla kaydırma hareketini tüm ekran üzerinde algılayan dinleyiciyi View'a bağlar.
     * Tek parmak dokunuş ve sürüklemeleri alttaki uygulamaya (Yandex, Spotify vb.) şeffafça iletir.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void attachTwoFingerGestureDetector(View targetView, float density) {
        targetView.setOnTouchListener(new View.OnTouchListener() {
            private float downX0 = 0f;
            private float downY0 = 0f;
            private float downX1 = 0f;
            private float downY1 = 0f;
            private float midDownX = 0f;
            private float midDownY = 0f;
            private int maxPointerCount = 0;
            private boolean gestureTriggered = false;
            private long downTime = 0L;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
                int currentPointers = event.getPointerCount();

                if (currentPointers > maxPointerCount) {
                    maxPointerCount = currentPointers;
                }

                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        downTime = System.currentTimeMillis();
                        downX0 = event.getX(0);
                        downY0 = event.getY(0);
                        midDownX = downX0;
                        midDownY = downY0;
                        maxPointerCount = 1;
                        gestureTriggered = false;
                        return true;

                    case MotionEvent.ACTION_POINTER_DOWN:
                        if (currentPointers >= 2) {
                            downX0 = event.getX(0);
                            downY0 = event.getY(0);
                            downX1 = event.getX(1);
                            downY1 = event.getY(1);
                            midDownX = (downX0 + downX1) / 2f;
                            midDownY = (downY0 + downY1) / 2f;
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (gestureTriggered || maxPointerCount < 2) {
                            return true;
                        }

                        float curMidX;
                        float curMidY;
                        if (currentPointers >= 2) {
                            curMidX = (event.getX(0) + event.getX(1)) / 2f;
                            curMidY = (event.getY(0) + event.getY(1)) / 2f;
                        } else {
                            curMidX = event.getX(0);
                            curMidY = event.getY(0);
                        }

                        float dx = curMidX - midDownX;
                        float dy = curMidY - midDownY;
                        float absDx = Math.abs(dx);
                        float absDy = Math.abs(dy);

                        float thresholdX = dp(35, density);
                        float thresholdY = dp(22, density);

                        if (absDx > thresholdX || absDy > thresholdY) {
                            gestureTriggered = true;
                            // İKİ PARMAK JESTİ: CAM TAVAN VE PERDE KONTROLLERİ
                            if (absDx >= absDy) {
                                    // Yatay Kaydırma
                                    if (dx > 0) {
                                        handleGestureAction(GestureAction.SWIPE_RIGHT, "Tüm Ekran");
                                    } else {
                                        handleGestureAction(GestureAction.SWIPE_LEFT, "Tüm Ekran");
                                    }
                                } else {
                                    // Dikey Kaydırma
                                    if (dy < 0) {
                                        handleGestureAction(GestureAction.SWIPE_UP, "Tüm Ekran");
                                    } else {
                                        handleGestureAction(GestureAction.SWIPE_DOWN, "Tüm Ekran");
                                    }
                                }
                            }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_POINTER_UP:
                        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                            if (!gestureTriggered && maxPointerCount == 1 && action == MotionEvent.ACTION_UP) {
                                float upX = event.getX(0);
                                float upY = event.getY(0);
                                float dist = (float) Math.hypot(upX - downX0, upY - downY0);
                                long dur = System.currentTimeMillis() - downTime;
                                if (overlayView != null && overlayParams != null && windowManager != null) {
                                    overlayParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                                    try {
                                        windowManager.updateViewLayout(overlayView, overlayParams);
                                    } catch (Throwable ignored) {
                                    }
                                }
                                if (dist < dp(18, density)) {
                                    com.mapcontrol.service.GlobalBackService.dispatchTap(upX, upY);
                                    mainHandler.postDelayed(() -> {
                                        if (overlayView != null && overlayParams != null && windowManager != null) {
                                            overlayParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                                            try {
                                                windowManager.updateViewLayout(overlayView, overlayParams);
                                            } catch (Throwable ignored) {
                                            }
                                        }
                                    }, 120);
                                } else {
                                    com.mapcontrol.service.GlobalBackService.dispatchStroke(downX0, downY0, upX, upY, dur);
                                    mainHandler.postDelayed(() -> {
                                        if (overlayView != null && overlayParams != null && windowManager != null) {
                                            overlayParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                                            try {
                                                windowManager.updateViewLayout(overlayView, overlayParams);
                                            } catch (Throwable ignored) {
                                            }
                                        }
                                    }, Math.max(150, dur + 50));
                                }
                            }
                            gestureTriggered = false;
                            maxPointerCount = 0;
                        }
                        return true;
                }
                return false;
            }
        });
    }

    /**
     * Jest algılandığında doğrudan VDBus kontrolcüsünü çalıştırır,
     * ekranda görsel HUD rozeti gösterir ve motoru bilgilendirir.
     */
    public void handleGestureAction(GestureAction action, String source) {
        VehicleSunroofController controller = VehicleSunroofController.getInstance(appContext);
        SunroofAutomationEngine engine = SunroofAutomationEngine.getInstance(appContext);
        boolean controlShade = prefs.getBoolean(SunroofAutomationEngine.KEY_CONTROL_SHADE, true);

        String title;
        String desc;
        int iconRes;

        switch (action) {
            case SWIPE_RIGHT:
                // SAĞA: Cam Tavanı Aç
                controller.openSunroof(controlShade);
                VehicleMetricsSnapshot snap = VehicleMetricsRepository.getInstance(appContext).getSnapshot();
                if (snap != null && !snap.isSunshadeOpen()) {
                    title = "Cam Tavan & Perde Açılıyor";
                    desc = "➡️ Perde açılıyor, ardından cam otomatik açılacak";
                } else {
                    title = "Cam Tavan Açılıyor";
                    desc = "➡️ İki parmakla sağa kaydırıldı (" + source + ")";
                }
                iconRes = R.drawable.ic_mdi_car_sunroof;
                engine.notifyManualOverride("Hızlı Jest: Tavan Açıldı");
                break;

            case SWIPE_LEFT:
                // SOLA: Cam Tavanı Kapat
                controller.closeSunroof(controlShade);
                title = "Cam Tavan Kapatılıyor";
                desc = "⬅️ İki parmakla sola kaydırıldı (" + source + ")";
                iconRes = R.drawable.ic_mdi_car_sunroof;
                engine.notifyManualOverride("Hızlı Jest: Tavan Kapatıldı");
                break;

            case SWIPE_UP:
                // YUKARI: Perdeyi Kapat
                controller.closeSunshadeOnly();
                title = "Tavan Perdesi Kapatılıyor";
                desc = "⬆️ İki parmakla yukarı kaydırıldı (" + source + ")";
                iconRes = R.drawable.ic_mdi_car_sunroof;
                engine.notifyManualOverride("Hızlı Jest: Perde Kapatıldı");
                break;

            case SWIPE_DOWN:
                // AŞAĞI: Perdeyi Aç
                controller.openSunshadeOnly();
                title = "Tavan Perdesi Açılıyor";
                desc = "⬇️ İki parmakla aşağı kaydırıldı (" + source + ")";
                iconRes = R.drawable.ic_mdi_car_sunroof;
                engine.notifyManualOverride("Hızlı Jest: Perde Açıldı");
                break;

            default:
                return;
        }

        Log.i(TAG, "Executed Sunroof Gesture: " + title + " -> " + desc);

        // Görsel HUD Bildirimi (Varsayılan olarak her zaman gösterilir)
        showHudToast(title, desc, iconRes);
    }

    /**
     * Ekranın üstünde kaybolan zarif HUD (Head-Up Display) bilgi kapsülü gösterir.
     */
    public synchronized void showHudToast(String title, String subtitle, int iconRes) {
        mainHandler.post(() -> {
            if (windowManager == null || !canDrawOverlays()) {
                Toast.makeText(appContext, title + "\n" + subtitle, Toast.LENGTH_SHORT).show();
                return;
            }

            if (hudDismissRunnable != null) {
                mainHandler.removeCallbacks(hudDismissRunnable);
                hudDismissRunnable = null;
            }

            if (hudView != null) {
                try {
                    windowManager.removeView(hudView);
                } catch (Throwable ignored) {
                }
                hudView = null;
            }

            float density = appContext.getResources().getDisplayMetrics().density;

            int windowType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            hudParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            hudParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            hudParams.y = dp(28, density);

            LinearLayout root = new LinearLayout(appContext);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(dp(20, density), dp(12, density), dp(24, density), dp(12, density));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xE61E293B); // Koyu lacivert/füme cam efekt
            bg.setCornerRadius(dp(28, density));
            bg.setStroke(dp(2, density), 0xFF38BDF8); // Parlak cyan çerçeve
            root.setBackground(bg);
            root.setElevation(dp(8, density));

            AppCompatImageView iv = new AppCompatImageView(appContext);
            iv.setImageResource(iconRes);
            iv.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF38BDF8));
            root.addView(iv, new LinearLayout.LayoutParams(dp(32, density), dp(32, density)));

            LinearLayout textCol = new LinearLayout(appContext);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setPadding(dp(14, density), 0, 0, 0);

            TextView tvTitle = new TextView(appContext);
            tvTitle.setText(title);
            tvTitle.setTextSize(16);
            tvTitle.setTypeface(null, Typeface.BOLD);
            tvTitle.setTextColor(Color.WHITE);
            textCol.addView(tvTitle);

            TextView tvSub = new TextView(appContext);
            tvSub.setText(subtitle);
            tvSub.setTextSize(12);
            tvSub.setTextColor(0xFF94A3B8);
            textCol.addView(tvSub);

            root.addView(textCol);
            hudView = root;

            try {
                windowManager.addView(hudView, hudParams);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to show HUD", t);
                return;
            }

            hudDismissRunnable = () -> {
                if (hudView != null && windowManager != null) {
                    hudView.animate()
                            .alpha(0f)
                            .translationY(-dp(20, density))
                            .setDuration(250)
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    if (hudView != null && windowManager != null) {
                                        try {
                                            windowManager.removeView(hudView);
                                        } catch (Throwable ignored) {
                                        }
                                        hudView = null;
                                    }
                                }
                            })
                            .start();
                }
            };
            mainHandler.postDelayed(hudDismissRunnable, 1800);
        });
    }

    // --- Overlay Görünüm Fabrikaları ---

    private View createTopStripView(float density) {
        LinearLayout strip = new LinearLayout(appContext);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER);
        strip.setPadding(dp(16, density), dp(4, density), dp(16, density), dp(4, density));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x33000000); // Çok hafif yarı-saydam
        bg.setCornerRadius(dp(14, density));
        bg.setStroke(dp(1, density), 0x2238BDF8);
        strip.setBackground(bg);

        TextView label = new TextView(appContext);
        label.setText("👈 Tavan Kapat | 👉 Tavan Aç | 👆 Perde Kapat | 👇 Perde Aç");
        label.setTextSize(11);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(0x88FFFFFF);
        strip.addView(label);

        return strip;
    }

    private View createFloatingPillView(float density) {
        LinearLayout pill = new LinearLayout(appContext);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(12, density), dp(6, density), dp(12, density), dp(6, density));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC0F172A);
        bg.setCornerRadius(dp(28, density));
        bg.setStroke(dp(2, density), 0x9938BDF8);
        pill.setBackground(bg);

        AppCompatImageView icon = new AppCompatImageView(appContext);
        icon.setImageResource(R.drawable.ic_mdi_car_sunroof);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF38BDF8));
        pill.addView(icon, new LinearLayout.LayoutParams(dp(26, density), dp(26, density)));

        TextView label = new TextView(appContext);
        label.setText(" Hızlı Jest");
        label.setTextSize(12);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(Color.WHITE);
        pill.addView(label);

        return pill;
    }

    private View createFullScreenView(float density) {
        LinearLayout full = new LinearLayout(appContext);
        full.setOrientation(LinearLayout.VERTICAL);
        full.setBackgroundColor(0x05000000); // Neredeyse tamamen şeffaf
        return full;
    }

    private int dp(int val, float density) {
        return Math.round(val * density);
    }
}
