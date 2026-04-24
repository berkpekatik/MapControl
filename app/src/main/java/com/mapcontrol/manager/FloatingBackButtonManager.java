package com.mapcontrol.manager;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.Toast;
import com.mapcontrol.service.GlobalBackService;

import java.util.ArrayList;

/**
 * Floating Back Button Manager
 * Yüzen geri tuşu oluşturur ve yönetir
 */
public class FloatingBackButtonManager {
    private static final String PREFS_NAME = "MapControlPrefs";
    private static final String KEY_FLOATING_BACK_BUTTON_ENABLED = "floatingBackButtonEnabled";
    private static final String KEY_FLOATING_BACK_POS_SAVED = "floatingBackPosSaved";
    private static final String KEY_FLOATING_BACK_POS_X = "floatingBackPosX";
    private static final String KEY_FLOATING_BACK_POS_Y = "floatingBackPosY";

    /** MapControlService vb. süreci açık tutunca Activity ölse bile overlay referansı korunur; aksi halde her açılışta ikinci buton eklenirdi. */
    private static volatile FloatingBackButtonManager sInstance;
    /** setLogCallback öncesi üretilen satırlar; Log ekranına flush edilir */
    private static final int PENDING_LOG_MAX = 32;
    private static final ArrayList<String> sPendingForUi = new ArrayList<>();
    
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
    /** Sürüklerken log spam’ini sınırlamak için (ms) */
    private long lastDragLogUptimeMs;
    private static final int DRAG_LOG_MIN_INTERVAL_MS = 120;
    
    // Log callback interface
    public interface LogCallback {
        void log(String message);
    }
    
    private LogCallback logCallback;

    private FloatingBackButtonManager(Context appContext) {
        this.context = appContext.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    /**
     * Süreç başına tek manager; böylece Activity yenilense bile önceki yüzen pencere {@link #show()} içinde kaldırılabilir.
     */
    public static FloatingBackButtonManager getInstance(Context anyContext) {
        if (sInstance == null) {
            synchronized (FloatingBackButtonManager.class) {
                if (sInstance == null) {
                    sInstance = new FloatingBackButtonManager(anyContext);
                    sInstance.log("[INFO] tekil FloatingBackButtonManager oluşturuldu");
                }
            }
        }
        return sInstance;
    }
    
    /**
     * Log callback'i set et
     */
    public void setLogCallback(LogCallback callback) {
        this.logCallback = callback;
        if (callback != null) {
            synchronized (FloatingBackButtonManager.class) {
                for (int i = 0; i < sPendingForUi.size(); i++) {
                    callback.log(sPendingForUi.get(i));
                }
                sPendingForUi.clear();
            }
        }
    }
    
    /**
     * Uygulama Log sekmesi (callback); Logcat yok. Callback yoksa kuyruk, setLogCallback ile flush
     */
    private void log(String message) {
        if (logCallback != null) {
            logCallback.log(message);
        } else {
            synchronized (FloatingBackButtonManager.class) {
                if (sPendingForUi.size() < PENDING_LOG_MAX) {
                    sPendingForUi.add(message);
                }
            }
        }
    }

    private static int getEdgeMarginPx(Context ctx) {
        return (int) (8 * ctx.getResources().getDisplayMetrics().density);
    }

    /**
     * TOP|START penceresini ekran içinde tutar (sürükleme ile aynı kurallar).
     */
    private int[] clampButtonPosition(int x, int y, int screenWidth, int screenHeight, int buttonSize) {
        int m = getEdgeMarginPx(context);
        if (x < m) {
            x = m;
        } else if (x > screenWidth - buttonSize - m) {
            x = screenWidth - buttonSize - m;
        }
        if (y < m) {
            y = m;
        } else if (y > screenHeight - buttonSize - m) {
            y = screenHeight - buttonSize - m;
        }
        return new int[] {x, y};
    }

    private void saveButtonPosition() {
        if (params == null) {
            return;
        }
        // commit: radyo ile hemen kapatıp açınca apply() bitmeden show() okuyabiliyordu; konum yüklenmiyordu
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_FLOATING_BACK_POS_SAVED, true)
                .putInt(KEY_FLOATING_BACK_POS_X, params.x)
                .putInt(KEY_FLOATING_BACK_POS_Y, params.y)
                .commit();
    }

    private void applyInitialPosition(int screenWidth, int screenHeight, int buttonSize, float density) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_FLOATING_BACK_POS_SAVED, false)) {
            int x = prefs.getInt(KEY_FLOATING_BACK_POS_X, 0);
            int y = prefs.getInt(KEY_FLOATING_BACK_POS_Y, 0);
            int[] c = clampButtonPosition(x, y, screenWidth, screenHeight, buttonSize);
            params.x = c[0];
            params.y = c[1];
        } else {
            params.x = screenWidth - buttonSize - (int) (16 * density);
            params.y = screenHeight - buttonSize - (int) (100 * density);
        }
    }
    
    /**
     * Floating back button'ı göster
     */
    public synchronized void show() {
        log("[INFO] Floating Back show() çağrıldı");
        // Her zaman önce mevcut view'ı temizle (güvenli başlangıç)
        cleanupExistingView();
        
        // Overlay permission kontrolü
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                log("[WARN] Floating Back: 'Diğer uygulamaların üzerinde görüntüleme' izni yok; buton eklenmiyor. Ayarlarda MapControl için açın.");
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
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        // İlk dokunma pozisyonunu kaydet
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        log(String.format(
                                java.util.Locale.US,
                                "[DEBUG] FloatingBack dokun: pencere (x=%d, y=%d) raw=(%.1f, %.1f)",
                                initialX, initialY, initialTouchX, initialTouchY));
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        // Sürükleme - pozisyonu güncelle
                        // RawX ve RawY ekran koordinatlarını kullan
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        
                        int newX = initialX + deltaX;
                        int newY = initialY + deltaY;
                        android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
                        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
                        int screenWidth = displayMetrics.widthPixels;
                        int screenHeight = displayMetrics.heightPixels;
                        int[] c = clampButtonPosition(newX, newY, screenWidth, screenHeight, buttonSize);
                        params.x = c[0];
                        params.y = c[1];
                        
                        try {
                            windowManager.updateViewLayout(floatingButton, params);
                        } catch (Exception e) {
                            log("[ERROR] FloatingBack updateViewLayout: " + e.getMessage());
                        }
                        long now = SystemClock.uptimeMillis();
                        if (now - lastDragLogUptimeMs >= DRAG_LOG_MIN_INTERVAL_MS) {
                            lastDragLogUptimeMs = now;
                            log(String.format(
                                    java.util.Locale.US,
                                    "[DEBUG] FloatingBack sürükle: pencere (x=%d, y=%d) parmak ekran (rawX=%.1f, rawY=%.1f) ekran %dx%d",
                                    params.x,
                                    params.y,
                                    event.getRawX(),
                                    event.getRawY(),
                                    screenWidth,
                                    screenHeight));
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
                        saveButtonPosition();
                        return true;
                }
                return false;
            }
        });
        
        // Başlangıç: kayıtlı konum veya sağ alt varsayılan
        android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;
        applyInitialPosition(screenWidth, screenHeight, buttonSize, density);
        
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
    public synchronized void hide() {
        if (floatingButton != null || isShowing) {
            // Son görünen konum ACTION_UP dışında (ör. sadece radyo ile kapatma) diske gitsin; açılışta applyInitialPosition doğru okusun
            if (params != null) {
                saveButtonPosition();
            }
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
     * Default değer: true (açık) — {@link com.mapcontrol.service.MapControlService} uygulama açılışında overlay'i başlatır; kullanıcı ayrıca ayarlara girmek zorunda değil
     */
    public static boolean loadEnabledState(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_FLOATING_BACK_BUTTON_ENABLED, true);
    }
}

