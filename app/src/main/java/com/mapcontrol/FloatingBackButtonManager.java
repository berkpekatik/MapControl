package com.mapcontrol;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.Toast;

/**
 * Floating Back Button Manager
 * Yüzen geri tuşu oluşturur ve yönetir
 */
public class FloatingBackButtonManager {
    private static final String PREFS_NAME = "MapControlPrefs";
    private static final String KEY_FLOATING_BACK_BUTTON_ENABLED = "floatingBackButtonEnabled";
    
    private Context context;
    private WindowManager windowManager;
    private View floatingButton;
    private WindowManager.LayoutParams params;
    private boolean isShowing = false;
    
    // Sürükleme için değişkenler
    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;
    
    // Log callback interface
    public interface LogCallback {
        void log(String message);
    }
    
    private LogCallback logCallback;
    
    public FloatingBackButtonManager(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }
    
    /**
     * Log callback'i set et
     */
    public void setLogCallback(LogCallback callback) {
        this.logCallback = callback;
    }
    
    /**
     * Log mesajı gönder
     */
    private void log(String message) {
        if (logCallback != null) {
            logCallback.log(message);
        }
    }
    
    /**
     * Floating back button'ı göster
     */
    public void show() {
        // Her zaman önce mevcut view'ı temizle (güvenli başlangıç)
        cleanupExistingView();
        
        // Overlay permission kontrolü
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    // İzin isteği için Settings'e yönlendir
                    try {
                        android.content.Intent intent = new android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                        Toast.makeText(context, "Lütfen 'Diğer uygulamaların üzerinde görüntüleme' iznini açın", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(context, "İzin ayarlarına gidilemedi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
                return;
            }
        }
        
        // Floating button oluştur (oval, küçük, yumuşak köşeler)
        Button button = new Button(context);
        button.setText("◀");
        button.setTextSize(18); // Orta boyut
        button.setTextColor(0xFFFFFFFF);
        
        // Oval arka plan (GradientDrawable ile)
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF3DAEA8); // Accent rengi
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL); // Oval şekil
        button.setBackground(bg);
        
        button.setPadding(14, 14, 14, 14); // Daha küçük padding
        button.setAlpha(0.9f); // Biraz şeffaf
        
        floatingButton = button;
        
        // WindowManager parametreleri
        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        float density = context.getResources().getDisplayMetrics().density;
        final int buttonSize = (int)(56 * density); // 56dp - daha küçük ve oval
        
        params = new WindowManager.LayoutParams(
                buttonSize, // Sabit genişlik (56dp)
                buttonSize, // Sabit yükseklik (56dp) - oval için eşit
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        
        // Başlangıç pozisyonu (sağ alt köşe) - Gravity TOP|START kullan, x ve y ile pozisyon ver
        params.gravity = Gravity.TOP | Gravity.START;
        
        // Touch listener - sürükleme ve tıklama
        floatingButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // İlk dokunma pozisyonunu kaydet
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        // Sürükleme - pozisyonu güncelle
                        // RawX ve RawY ekran koordinatlarını kullan
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        
                        int newX = initialX + deltaX;
                        int newY = initialY + deltaY;
                        
                        // Ekran sınırlarını kontrol et
                        android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
                        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
                        int screenWidth = displayMetrics.widthPixels;
                        int screenHeight = displayMetrics.heightPixels;
                        
                        // X sınırları (soldan ve sağdan)
                        int margin = (int)(8 * context.getResources().getDisplayMetrics().density); // 8dp margin
                        if (newX < margin) {
                            newX = margin;
                        } else if (newX > screenWidth - buttonSize - margin) {
                            newX = screenWidth - buttonSize - margin;
                        }
                        
                        // Y sınırları (üstten ve alttan)
                        if (newY < margin) {
                            newY = margin;
                        } else if (newY > screenHeight - buttonSize - margin) {
                            newY = screenHeight - buttonSize - margin;
                        }
                        
                        params.x = newX;
                        params.y = newY;
                        
                        try {
                            windowManager.updateViewLayout(floatingButton, params);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                        // Tıklama - BACK tuşunu simüle et
                        // Eğer çok az hareket varsa, tıklama olarak kabul et
                        float moveDeltaX = Math.abs(event.getRawX() - initialTouchX);
                        float moveDeltaY = Math.abs(event.getRawY() - initialTouchY);
                        if (moveDeltaX < 10 && moveDeltaY < 10) {
                            // Tıklama animasyonu
                            animateButtonClick(v);
                            // BACK tuşunu simüle et
                            simulateBackButton();
                        }
                        return true;
                }
                return false;
            }
        });
        
        // Başlangıç pozisyonunu ayarla (sağ alt köşe)
        android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;
        params.x = screenWidth - buttonSize - (int)(16 * density); // Sağdan 16dp içeride
        params.y = screenHeight - buttonSize - (int)(100 * density); // Alttan 100dp yukarı
        
        // WindowManager'a ekle
        try {
            if (windowManager != null && floatingButton != null && params != null) {
                windowManager.addView(floatingButton, params);
                isShowing = true;
                log("[SUCCESS] Floating Back Button gösterildi");
            }
        } catch (Exception e) {
            log("[ERROR] Floating Back Button gösterilemedi: " + e.getMessage());
            // Hata durumunda state'i temizle
            floatingButton = null;
            isShowing = false;
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                Toast.makeText(context, "Yüzen buton gösterilemedi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    /**
     * Mevcut view'ı temizle (güvenli temizleme)
     */
    private void cleanupExistingView() {
        if (floatingButton != null) {
            try {
                // View'ın hala windowManager'da olup olmadığını kontrol et
                if (windowManager != null) {
                    windowManager.removeView(floatingButton);
                }
            } catch (Exception e) {
                // View zaten kaldırılmış olabilir, sorun değil
            }
            floatingButton = null;
        }
        isShowing = false;
    }
    
    /**
     * Floating back button'ı gizle
     */
    public void hide() {
        if (floatingButton != null || isShowing) {
            cleanupExistingView();
            log("[INFO] Floating Back Button gizlendi");
        }
    }
    
    /**
     * Butona tıklama animasyonu
     */
    private void animateButtonClick(View button) {
        // Scale animasyonu (büyüyüp küçülme)
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(button, "scaleX", 1.0f, 0.85f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(button, "scaleY", 1.0f, 0.85f, 1.0f);
        
        // Alpha animasyonu (parlaklık değişimi)
        ObjectAnimator alpha = ObjectAnimator.ofFloat(button, "alpha", 0.9f, 0.6f, 0.9f);
        
        // Animasyon seti
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY, alpha);
        animatorSet.setDuration(200); // 200ms animasyon
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.start();
    }
    
    /**
     * Android BACK tuşunu simüle et
     * AccessibilityService kullanarak güvenilir şekilde BACK tuşu gönderir
     */
    private void simulateBackButton() {
        // Foreground uygulamayı kontrol et
        String foregroundPackage = getForegroundPackage();
        
        // Eğer MapControl foreground'daysa, BACK tuşunu gönderme
        if (foregroundPackage != null && foregroundPackage.equals("com.mapcontrol")) {
            log("[DEBUG] Floating Back Button: MapControl aktifken BACK tuşu gönderilmedi");
            return;
        }
        
        // GlobalBackService kontrolü
        if (!GlobalBackService.isServiceEnabled()) {
            log("[WARN] Floating Back Button: GlobalBackService aktif değil, ayarlara yönlendiriliyor");
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                try {
                    // Accessibility ayarlarına yönlendir
                    android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    Toast.makeText(context, "Lütfen 'Global Back Service' erişilebilirlik servisini açın", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    log("[ERROR] Floating Back Button: Accessibility ayarlarına gidilemedi - " + e.getMessage());
                }
            });
            return;
        }
        
        // GlobalBackService ile BACK tuşunu gönder
        boolean success = GlobalBackService.performBackAction();
        if (success) {
            log("[SUCCESS] Floating Back Button: BACK tuşu gönderildi (AccessibilityService)");
        } else {
            log("[ERROR] Floating Back Button: BACK tuşu gönderilemedi");
        }
    }
    
    /**
     * Foreground'daki uygulamanın package adını al
     * Daha güvenilir yöntem: dumpsys kullan
     */
    private String getForegroundPackage() {
        try {
            // Yöntem 1: dumpsys activity komutu (en güvenilir)
            try {
                java.lang.Process process = Runtime.getRuntime().exec("dumpsys activity activities");
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("mResumedActivity") || line.contains("mFocusedActivity")) {
                        // Satırdan package adını çıkar
                        int startIndex = line.indexOf("com.");
                        if (startIndex != -1) {
                            int endIndex = line.indexOf("/", startIndex);
                            if (endIndex == -1) {
                                endIndex = line.indexOf(" ", startIndex);
                            }
                            if (endIndex == -1) {
                                endIndex = line.length();
                            }
                            String packageName = line.substring(startIndex, endIndex).trim();
                            reader.close();
                            return packageName;
                        }
                    }
                }
                reader.close();
            } catch (Exception e) {
                log("[DEBUG] Floating Back Button: dumpsys hatası - " + e.getMessage());
            }
            
            // Yöntem 2: getRunningAppProcesses (Android 5.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    java.util.List<android.app.ActivityManager.RunningAppProcessInfo> runningProcesses = am.getRunningAppProcesses();
                    if (runningProcesses != null) {
                        for (android.app.ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                            if (processInfo.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                                if (processInfo.pkgList != null && processInfo.pkgList.length > 0) {
                                    String packageName = processInfo.pkgList[0];
                                    return packageName;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Sessizce hata yok say
        }
        return null;
    }
    
    /**
     * Durumu kontrol et
     */
    public boolean isShowing() {
        return isShowing;
    }
    
    /**
     * Ayarları kaydet
     */
    public static void saveEnabledState(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_FLOATING_BACK_BUTTON_ENABLED, enabled).apply();
    }
    
    /**
     * Ayarları yükle
     * Default değer: false (kapalı)
     */
    public static boolean loadEnabledState(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Default değer false - ilk yüklemede kapalı
        return prefs.getBoolean(KEY_FLOATING_BACK_BUTTON_ENABLED, false);
    }
}

