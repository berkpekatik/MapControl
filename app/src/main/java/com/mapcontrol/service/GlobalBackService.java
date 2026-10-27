package com.mapcontrol.service;
import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.SparseArray;

import java.util.List;

import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.mapcontrol.nav.YandexClusterNavOverlay;
import com.mapcontrol.util.InstallerConfirmClicker;
import com.mapcontrol.nav.YandexNavScraper;
import com.mapcontrol.nav.YandexNavSnapshot;
import com.mapcontrol.nav.YandexNavStability;

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

    /** Rehberlik varken iki okuma arası en kısa süre. */
    private static final long YANDEX_SCRAPE_GAP_ACTIVE_MS = 200L;
    /** Rehberlik yokken olayla tetiklenen okuma aralığı. */
    private static final long YANDEX_SCRAPE_GAP_IDLE_MS = 700L;
    /** Rehberlik varken, olay gelmese de arka plan penceresini yoklama. */
    private static final long YANDEX_POLL_ACTIVE_MS = 250L;
    /** Nav başlamadan önce yoklama. Yalnızca pencere olayına bağlı kalmaz. */
    private static final long YANDEX_POLL_IDLE_MS = 1000L;
    // Pencere kokleri (getWindows/getRootInActiveWindow) senkron binder beklemesi
    // yapar; main thread'de ANR uretiyordu. Tarama arka thread'de, overlay UI'i
    // zaten kendi mainHandler'i ile main'e donuyor.
    private static final java.util.concurrent.ExecutorService SCRAPE_EXEC =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "yandex-scrape");
                t.setDaemon(true);
                return t;
            });
    private static final java.util.concurrent.atomic.AtomicBoolean SCRAPE_IN_FLIGHT =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean scrapePending =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private final Handler yandexHandler = new Handler(Looper.getMainLooper());
    private final YandexNavStability yandexStability = new YandexNavStability();
    private final Object yandexLock = new Object();
    private final Runnable delayedScrapeRunnable = this::requestScrape;
    private boolean pollQueued;
    private final Runnable pollRunnable = () -> {
        pollQueued = false;
        if (!YandexClusterNavOverlay.isEnabled(this)) {
            return;
        }
        requestScrape();
        ensureYandexPoll();
    };
    private long lastYandexScrapeMs = 0L;
    private volatile boolean lastGuidanceActive = false;
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        maybeScrapeYandexNav(event);
        InstallerConfirmClicker.onWindowEvent(this);
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
            ensureYandexPoll();
            requestScrape();
        }
    }
    
    @Override
    public void onDestroy() {
        InstallerConfirmClicker.disarm();
        stopYandexPoll();
        yandexStability.clear();
        try {
            YandexClusterNavOverlay.getInstance(this).hide();
        } catch (Exception ignored) {
        }
        lastGuidanceActive = false;
        instance = null;
        super.onDestroy();
    }

    /**
     * Yandex Maps navigasyon kartlarını okuyup cluster overlay'e yansıtır.
     * Olaylar hızlandırır; arka planda olay kesilse de yoklama sürer.
     */
    private void maybeScrapeYandexNav(AccessibilityEvent event) {
        if (!YandexClusterNavOverlay.isEnabled(this)) {
            stopYandexPoll();
            return;
        }
        ensureYandexPoll();
        if (event != null && isYandexOverlayZOrderEvent(event)) {
            YandexClusterNavOverlay.getInstance(this).raiseToTopIfShowing();
        }
        if (event != null && !shouldHandleYandexEvent(event)) {
            return;
        }
        requestScrape();
    }

    private void ensureYandexPoll() {
        if (pollQueued || !YandexClusterNavOverlay.isEnabled(this)) {
            return;
        }
        pollQueued = true;
        long delay = lastGuidanceActive ? YANDEX_POLL_ACTIVE_MS : YANDEX_POLL_IDLE_MS;
        yandexHandler.postDelayed(pollRunnable, delay);
    }

    private void stopYandexPoll() {
        pollQueued = false;
        yandexHandler.removeCallbacks(pollRunnable);
        yandexHandler.removeCallbacks(delayedScrapeRunnable);
    }

    private void kickPollRate() {
        yandexHandler.removeCallbacks(pollRunnable);
        pollQueued = false;
        ensureYandexPoll();
    }

    private void requestScrape() {
        scrapePending.set(true);
        if (!SCRAPE_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        SCRAPE_EXEC.execute(this::drainScrapes);
    }

    private void drainScrapes() {
        boolean handedOff = false;
        try {
            while (scrapePending.get()) {
                long now = SystemClock.uptimeMillis();
                long gap = lastGuidanceActive
                        ? YANDEX_SCRAPE_GAP_ACTIVE_MS
                        : YANDEX_SCRAPE_GAP_IDLE_MS;
                long wait = gap - (now - lastYandexScrapeMs);
                if (wait > 0) {
                    handedOff = true;
                    yandexHandler.postDelayed(delayedScrapeRunnable, wait);
                    return;
                }
                scrapePending.set(false);
                lastYandexScrapeMs = SystemClock.uptimeMillis();
                performYandexNavSync();
            }
        } finally {
            SCRAPE_IN_FLIGHT.set(false);
            if (!handedOff && scrapePending.get()) {
                requestScrape();
            }
        }
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
        inst.ensureYandexPoll();
        return inst.performYandexNavSync();
    }

    private YandexSyncResult performYandexNavSync() {
        synchronized (yandexLock) {
            if (!YandexClusterNavOverlay.isEnabled(this)) {
                yandexStability.clear();
                lastGuidanceActive = false;
                YandexClusterNavOverlay.getInstance(this).hide();
                return YandexSyncResult.DISABLED;
            }
            YandexRead read = readYandexSnapshot();
            long now = SystemClock.uptimeMillis();
            YandexNavSnapshot stable = yandexStability.accept(read.snapshot, now);
            boolean wasActive = lastGuidanceActive;
            lastGuidanceActive = stable.guidanceActive;
            if (wasActive != stable.guidanceActive) {
                yandexHandler.post(this::kickPollRate);
            }
            if (!stable.guidanceActive) {
                YandexClusterNavOverlay.getInstance(this).hide();
                return read.windowFound
                        ? YandexSyncResult.NAV_INACTIVE
                        : YandexSyncResult.YANDEX_NOT_OPEN;
            }
            YandexClusterNavOverlay.getInstance(this).apply(stable);
            return YandexSyncResult.OK;
        }
    }

    private static boolean isYandexOverlayZOrderEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        return type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED;
    }

    private boolean shouldHandleYandexEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return true;
        }
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !YandexNavScraper.PACKAGE_YANDEX_MAPS.contentEquals(pkg)) {
            return false;
        }
        return type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                || type == AccessibilityEvent.TYPE_VIEW_SCROLLED;
    }

    /**
     * Yandex pencerelerinin alanlarını birleştirir. ETA paneli tek başına yeterli sayılmaz;
     * manevra balonu başka penceredeyse "30 m" kartı düşmesin.
     */
    private YandexRead readYandexSnapshot() {
        YandexRead best = new YandexRead(false, null);
        AccessibilityNodeInfo active = null;
        try {
            active = getRootInActiveWindow();
        } catch (Exception ignored) {
        }
        if (active != null) {
            try {
                if (isYandexNode(active)) {
                    best = best.offer(true, scrapeFresh(active));
                    if (best.hasManeuverAndEta()) {
                        return best;
                    }
                }
            } finally {
                recycleNode(active);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                SparseArray<List<AccessibilityWindowInfo>> all = getWindowsOnAllDisplays();
                if (all != null) {
                    for (int i = 0; i < all.size(); i++) {
                        best = scanWindows(all.valueAt(i), best);
                    }
                }
            } catch (Exception ignored) {
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                best = scanWindows(getWindows(), best);
            } catch (Exception ignored) {
            }
        }
        return best;
    }

    private YandexRead scanWindows(@androidx.annotation.Nullable List<AccessibilityWindowInfo> wins,
            YandexRead best) {
        if (wins == null) {
            return best;
        }
        for (int i = 0; i < wins.size(); i++) {
            AccessibilityWindowInfo w = wins.get(i);
            if (w == null) {
                continue;
            }
            try {
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null) {
                    continue;
                }
                try {
                    if (isYandexNode(root)) {
                        best = best.offer(true, scrapeFresh(root));
                    }
                } finally {
                    recycleNode(root);
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    w.recycle();
                } catch (Exception ignored) {
                }
            }
        }
        return best;
    }

    private static boolean isYandexNode(AccessibilityNodeInfo node) {
        CharSequence pkg = node.getPackageName();
        return pkg != null && YandexNavScraper.PACKAGE_YANDEX_MAPS.contentEquals(pkg);
    }

    private static YandexNavSnapshot scrapeFresh(AccessibilityNodeInfo node) {
        return YandexNavScraper.scrape(node);
    }

    private static void recycleNode(AccessibilityNodeInfo node) {
        try {
            node.recycle();
        } catch (Exception ignored) {
        }
    }

    private static final class YandexRead {
        final boolean windowFound;
        @androidx.annotation.Nullable final YandexNavSnapshot snapshot;
        final int score;

        YandexRead(boolean windowFound, @androidx.annotation.Nullable YandexNavSnapshot snapshot) {
            this.windowFound = windowFound;
            this.snapshot = snapshot;
            this.score = snapshot == null ? -1 : snapshot.populatedFieldCount();
        }

        boolean hasManeuverAndEta() {
            return snapshot != null && snapshot.hasManeuverDistance() && snapshot.hasEta();
        }

        YandexRead offer(boolean found, YandexNavSnapshot snap) {
            if (!found) {
                return this;
            }
            if (snap == null) {
                return windowFound ? this : new YandexRead(true, null);
            }
            if (!windowFound || snapshot == null) {
                return new YandexRead(true, snap);
            }
            return new YandexRead(true, snapshot.withFilledGaps(snap));
        }
    }
    
    /**
     * Singleton instance'ı döndür
     */
    public static GlobalBackService getInstance() {
        return instance;
    }

    /**
     * Erişilebilirlik servisi üzerinden tek parmak dokunuşunu altındaki uygulamaya iletir.
     */
    public static boolean dispatchTap(float x, float y) {
        GlobalBackService inst = getInstance();
        if (inst != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                android.accessibilityservice.GestureDescription.Builder builder = new android.accessibilityservice.GestureDescription.Builder();
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(x, y);
                builder.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50));
                return inst.dispatchGesture(builder.build(), null, null);
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    /**
     * Erişilebilirlik servisi üzerinden tek parmak kaydırmayı altındaki uygulamaya iletir.
     */
    public static boolean dispatchStroke(float startX, float startY, float endX, float endY, long durationMs) {
        GlobalBackService inst = getInstance();
        if (inst != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                android.accessibilityservice.GestureDescription.Builder builder = new android.accessibilityservice.GestureDescription.Builder();
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(startX, startY);
                path.lineTo(endX, endY);
                long dur = Math.max(50, Math.min(durationMs, 500));
                builder.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, dur));
                return inst.dispatchGesture(builder.build(), null, null);
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
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

