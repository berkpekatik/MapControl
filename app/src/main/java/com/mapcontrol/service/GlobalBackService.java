package com.mapcontrol.service;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Global Back Service
 * AccessibilityService kullanarak global BACK tuşu simülasyonu yapar
 * Singleton mantığıyla çalışır
 */
public class GlobalBackService extends AccessibilityService {
    private static final String TAG = "GlobalBackService";
    private static GlobalBackService instance = null;
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Event'leri dinlememize gerek yok, sadece BACK tuşu göndermek için kullanıyoruz
    }
    
    @Override
    public void onInterrupt() {
        // Service interrupted
    }
    
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }
    
    /**
     * Singleton instance'ı döndür
     */
    public static GlobalBackService getInstance() {
        return instance;
    }
    
    /**
     * Servisin aktif olup olmadığını kontrol et
     */
    public static boolean isServiceEnabled() {
        return instance != null;
    }
    
    /**
     * Global BACK tuşunu simüle et
     * @return true eğer başarılıysa, false aksi halde
     */
    public static boolean performBackAction() {
        if (instance == null) {
            return false;
        }
        
        try {
            return instance.performGlobalAction(GLOBAL_ACTION_BACK);
        } catch (Exception e) {
            return false;
        }
    }
}

