package com.mapcontrol.service;
import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.List;

import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.mapcontrol.nav.YandexClusterNavOverlay;
import com.mapcontrol.nav.YandexNavScraper;
import com.mapcontrol.nav.YandexNavSnapshot;

/**
 * Global Back Service
 * AccessibilityService: global BACK, odak alanına metin, Yandex nav scrape → cluster overlay.
 * Singleton mantığıyla çalışır
 */
public class GlobalBackService extends AccessibilityService {
    private static GlobalBackService instance = null;
    /**
     * Ayarlarda / shell'de kullanılacak tam bileşen (manifest: .service.GlobalBackService)
     * Yanlış: com.mapcontrol/.GlobalBackService → sınıf com.mapcontrol.GlobalBackService (mevcut değil)
     */
    public static final String ACCESSIBILITY_FLAT_COMPONENT =
            "com.mapcontrol/com.mapcontrol.service.GlobalBackService";

    /** Navigasyon aktifken güncelleme aralığı. */
    private static final long YANDEX_SCRAPE_THROTTLE_ACTIVE_MS = 750L;
    /** Nav başlamadan önce pencere değişiminde kontrol aralığı. */
    private static final long YANDEX_SCRAPE_THROTTLE_IDLE_MS = 2000L;
    private long lastYandexScrapeMs = 0L;
    private boolean lastGuidanceActive = false;
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        maybeScrapeYandexNav(event);
    }
    
    @Override
    public void onInterrupt() {
        // Service interrupted
    }
    
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        if (YandexClusterNavOverlay.isEnabled(this)) {
            performYandexNavSync();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            YandexClusterNavOverlay.getInstance(this).hide();
        } catch (Exception ignored) {
        }
        lastGuidanceActive = false;
        instance = null;
    }

    /**
     * Yandex Maps navigasyon kartlarını okuyup cluster overlay'e yansıtır.
     * Nav aktif değilken yalnızca pencere değişimlerinde seyrek kontrol eder;
     * nav aktifken içerik değişimlerini throttle ile izler.
     */
    private void maybeScrapeYandexNav(AccessibilityEvent event) {
        if (!YandexClusterNavOverlay.isEnabled(this)) {
            return;
        }
        if (event != null && !shouldHandleYandexEvent(event)) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        long throttleMs = lastGuidanceActive
                ? YANDEX_SCRAPE_THROTTLE_ACTIVE_MS
                : YANDEX_SCRAPE_THROTTLE_IDLE_MS;
        if (now - lastYandexScrapeMs < throttleMs) {
            return;
        }
        lastYandexScrapeMs = now;

        performYandexNavSync();
    }

    public enum YandexSyncResult {
        OK,
        YANDEX_NOT_OPEN,
        NAV_INACTIVE,
        SERVICE_NOT_CONNECTED,
        DISABLED
    }

    /**
     * Ayar açıldığında veya manuel tetiklemede Yandex penceresini okuyup overlay'i günceller.
     */
    public static YandexSyncResult syncYandexClusterNav(Context context) {
        if (!YandexClusterNavOverlay.isEnabled(context)) {
            return YandexSyncResult.DISABLED;
        }
        GlobalBackService inst = getInstance();
        if (inst == null) {
            return YandexSyncResult.SERVICE_NOT_CONNECTED;
        }
        return inst.performYandexNavSync();
    }

    private YandexSyncResult performYandexNavSync() {
        AccessibilityNodeInfo root = findYandexRoot();
        try {
            if (root == null) {
                if (lastGuidanceActive) {
                    lastGuidanceActive = false;
                    YandexClusterNavOverlay.getInstance(this).hide();
                }
                return YandexSyncResult.YANDEX_NOT_OPEN;
            }
            YandexNavSnapshot snap = YandexNavScraper.scrape(root);
            lastGuidanceActive = snap.guidanceActive;
            if (!snap.guidanceActive) {
                YandexClusterNavOverlay.getInstance(this).hide();
                return YandexSyncResult.NAV_INACTIVE;
            }
            YandexClusterNavOverlay.getInstance(this).apply(snap);
            return YandexSyncResult.OK;
        } catch (Exception ignored) {
            return YandexSyncResult.YANDEX_NOT_OPEN;
        } finally {
            if (root != null) {
                try {
                    root.recycle();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean shouldHandleYandexEvent(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName();
        int type = event.getEventType();
        boolean yandexPkg = pkg != null
                && YandexNavScraper.PACKAGE_YANDEX_MAPS.contentEquals(pkg);
        boolean windowChange = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        if (windowChange) {
            return true;
        }
        if (!yandexPkg) {
            return false;
        }
        if (lastGuidanceActive && type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return true;
        }
        return type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
    }

    /**
     * Önce aktif pencere, yoksa tüm pencerelerde Yandex Maps kök düğümü.
     */
    private AccessibilityNodeInfo findYandexRoot() {
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null) {
            CharSequence p = active.getPackageName();
            if (p != null && YandexNavScraper.PACKAGE_YANDEX_MAPS.contentEquals(p)) {
                return active;
            }
            try {
                active.recycle();
            } catch (Exception ignored) {
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null;
        }
        try {
            List<AccessibilityWindowInfo> wins = getWindows();
            if (wins == null) {
                return null;
            }
            for (int i = 0; i < wins.size(); i++) {
                AccessibilityWindowInfo w = wins.get(i);
                if (w == null) {
                    continue;
                }
                try {
                    AccessibilityNodeInfo r = w.getRoot();
                    if (r == null) {
                        continue;
                    }
                    CharSequence p = r.getPackageName();
                    if (p != null && YandexNavScraper.PACKAGE_YANDEX_MAPS.contentEquals(p)) {
                        return r;
                    }
                    r.recycle();
                } finally {
                    w.recycle();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    
    /**
     * Singleton instance'ı döndür
     */
    public static GlobalBackService getInstance() {
        return instance;
    }
    
    /**
     * Sistem o anda servise bağlandı mı (performGlobalAction için gerekli)
     */
    public static boolean isServiceEnabled() {
        return instance != null;
    }

    /**
     * Kullanıcı MapControl / GlobalBackService'i erişilebilirlik ayarlarında açmış mı.
     * instance sadece onServiceConnected() sonrası dolu olur; ilk tıklamada yanlış "açın" üretilmesin diye
     * Settings üzerinden de doğrulama yapılır.
     */
    public static boolean isRegisteredInSystemAccessibilitySettings(Context context) {
        if (context == null) {
            return false;
        }
        try {
            if (Settings.Secure.getInt(
                    context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED) != 1) {
                return false;
            }
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
        String enabled = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null || enabled.isEmpty()) {
            return false;
        }
        for (String id : enabled.split(":")) {
            if (id != null) {
                String t = id.trim();
                if (t.equals(ACCESSIBILITY_FLAT_COMPONENT) || t.endsWith("service.GlobalBackService")) {
                    return true;
                }
            }
        }
        return false;
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

    /**
     * Odaklı girdi alanına metin: odak/FOCUS bilmek için pencere içeriğinin açık olması gerekir
     * (manifest: {@code canRetrieveWindowContent}).
     * Önce {@link #findFocus}, sonra aktif/etkileşimli tüm pencerelerde odaklı metin benzeri düğüm.
     * Sonra {@code SET_TEXT} veya pano + PASTE.
     */
    public static boolean typeIntoFocusedField(Context context, String text) {
        if (instance == null) {
            return false;
        }
        if (text == null) {
            text = "";
        }
        AccessibilityNodeInfo node = null;
        try {
            node = instance.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (node == null) {
                node = findFocusedTextInputInWindows();
            }
            if (node == null) {
                return false;
            }
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            if (trySetText(node, text)) {
                return true;
            }
            if (context == null) {
                return false;
            }
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) {
                return false;
            }
            cm.setPrimaryClip(ClipData.newPlainText("mapcontrol", text));
            return node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
        } catch (Exception e) {
            return false;
        } finally {
            if (node != null) {
                node.recycle();
            }
        }
    }

    private static AccessibilityNodeInfo findFocusedTextInputInWindows() {
        if (instance == null) {
            return null;
        }
        AccessibilityNodeInfo inActive = fromRootNode(instance.getRootInActiveWindow());
        if (inActive != null) {
            return inActive;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null;
        }
        try {
            List<AccessibilityWindowInfo> wins = instance.getWindows();
            if (wins == null) {
                return null;
            }
            for (int i = 0; i < wins.size(); i++) {
                AccessibilityWindowInfo w = wins.get(i);
                if (w == null) {
                    continue;
                }
                try {
                    if (w.getType() == AccessibilityWindowInfo.TYPE_SYSTEM) {
                        continue;
                    }
                    AccessibilityNodeInfo r = w.getRoot();
                    if (r == null) {
                        continue;
                    }
                    AccessibilityNodeInfo t = fromRootNode(r);
                    if (t != null) {
                        return t;
                    }
                } finally {
                    w.recycle();
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static AccessibilityNodeInfo fromRootNode(AccessibilityNodeInfo root) {
        if (root == null) {
            return null;
        }
        try {
            return findFocusedTextInputDeep(root);
        } finally {
            root.recycle();
        }
    }

    private static AccessibilityNodeInfo findFocusedTextInputDeep(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.isFocused() && isTextInputLike(node)) {
            return AccessibilityNodeInfo.obtain(node);
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo ch = node.getChild(i);
            if (ch == null) {
                continue;
            }
            AccessibilityNodeInfo f = findFocusedTextInputDeep(ch);
            ch.recycle();
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    /**
     * EditText, AutoCompleteTextView ve isEditable; bazı uygulamalarda odak sadece sınıf adıyla görünür.
     */
    private static boolean isTextInputLike(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 18 && node.isEditable()) {
            return true;
        }
        CharSequence cn = node.getClassName();
        if (cn == null) {
            return false;
        }
        String c = cn.toString();
        return c.contains("EditText")
                || c.contains("AutoCompleteTextView")
                || c.contains("AutoComplete");
    }

    private static boolean trySetText(AccessibilityNodeInfo node, String text) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
    }
}

