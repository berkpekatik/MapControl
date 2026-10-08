package com.mapcontrol.util;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.mapcontrol.service.GlobalBackService;

import java.util.List;
import java.util.Locale;

/**
 * Paket yükleyici onay penceresindeki INSTALL / Yükle düğmesine basar.
 * MapControl kurulumu ve mağaza uygulamalarının açtığı sistem penceresi için geçerlidir.
 */
public final class InstallerConfirmClicker {

    private static final String TAG = "InstallerConfirm";
    private static final long WINDOW_MS = 8000L;
    private static final long RETRY_MS = 400L;
    private static final String[] INSTALL_LABELS = {
            "install", "yükle", "yukle", "kur", "update", "güncelle", "guncelle"
    };
    private static final String[] CANCEL_LABELS = {
            "cancel", "iptal", "vazgeç", "vazgec"
    };
    private static final String[] INSTALL_IDS = {
            "ok_button", "install_confirm", "install_button"
    };

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final long COOLDOWN_MS = 2500L;
    private static long armedUntil;
    private static long lastScanMs;
    private static long cooldownUntil;
    private static boolean sawInstaller;
    private static final Runnable RETRY = new Runnable() {
        @Override
        public void run() {
            if (!isArmed()) {
                if (armedUntil != 0L) {
                    armedUntil = 0L;
                    Log.i(TAG, "INSTALL onayı bulunamadı");
                }
                return;
            }
            GlobalBackService service = GlobalBackService.getInstance();
            if (service != null && tryClick(service)) {
                disarm();
                Log.i(TAG, "INSTALL onayına basıldı");
                return;
            }
            if (isArmed()) {
                HANDLER.postDelayed(this, RETRY_MS);
            }
        }
    };

    private InstallerConfirmClicker() {
    }

    public static void arm() {
        armedUntil = SystemClock.uptimeMillis() + WINDOW_MS;
        HANDLER.removeCallbacks(RETRY);
        HANDLER.post(RETRY);
    }

    public static void disarm() {
        armedUntil = 0L;
        HANDLER.removeCallbacks(RETRY);
    }

    public static void onWindowEvent(AccessibilityService service) {
        if (service == null || SystemClock.uptimeMillis() < cooldownUntil) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastScanMs < RETRY_MS) {
            return;
        }
        lastScanMs = now;
        if (tryClick(service)) {
            cooldownUntil = now + COOLDOWN_MS;
            disarm();
            Log.i(TAG, "INSTALL onayına basıldı");
            return;
        }
        if (sawInstaller) {
            arm();
        }
    }

    private static boolean isArmed() {
        return armedUntil != 0L && SystemClock.uptimeMillis() < armedUntil;
    }

    private static boolean tryClick(AccessibilityService service) {
        sawInstaller = false;
        boolean clicked = false;
        List<AccessibilityWindowInfo> windows = service.getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                if (window == null) {
                    continue;
                }
                AccessibilityNodeInfo root = window.getRoot();
                try {
                    if (!clicked && root != null && clickInInstallerRoot(service, root)) {
                        clicked = true;
                    }
                } finally {
                    if (root != null) {
                        root.recycle();
                    }
                    window.recycle();
                }
            }
        }
        if (clicked) {
            return true;
        }
        AccessibilityNodeInfo active = service.getRootInActiveWindow();
        if (active == null) {
            return false;
        }
        try {
            return clickInInstallerRoot(service, active);
        } finally {
            active.recycle();
        }
    }

    private static boolean clickInInstallerRoot(AccessibilityService service, AccessibilityNodeInfo root) {
        if (!isInstallerPackage(root.getPackageName())) {
            return false;
        }
        sawInstaller = true;
        for (String label : INSTALL_LABELS) {
            if (activateMatches(service, root.findAccessibilityNodeInfosByText(label), true)) {
                return true;
            }
        }
        if (findInstallById(service, root)) {
            return true;
        }
        scrollForward(root);
        return false;
    }

    private static void scrollForward(AccessibilityNodeInfo node) {
        if (node.isScrollable()) {
            for (int i = 0; i < 4; i++) {
                if (!node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                    break;
                }
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            scrollForward(child);
            child.recycle();
        }
    }

    private static boolean findInstallById(AccessibilityService service, AccessibilityNodeInfo node) {
        String viewId = node.getViewIdResourceName();
        if (viewId != null && node.isEnabled() && !isCancel(node)) {
            for (String suffix : INSTALL_IDS) {
                if (viewId.endsWith("/" + suffix)) {
                    return activate(node);
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            boolean found = findInstallById(service, child);
            child.recycle();
            if (found) {
                return true;
            }
        }
        return false;
    }

    private static boolean activateMatches(AccessibilityService service, List<AccessibilityNodeInfo> nodes,
                                           boolean requireInstallLabel) {
        if (nodes == null) {
            return false;
        }
        try {
            for (AccessibilityNodeInfo node : nodes) {
                if (node == null || !node.isEnabled() || isCancel(node)) {
                    continue;
                }
                if (requireInstallLabel && !isInstallLabel(node)) {
                    continue;
                }
                if (activate(node)) {
                    return true;
                }
            }
            return false;
        } finally {
            for (AccessibilityNodeInfo node : nodes) {
                if (node != null) {
                    node.recycle();
                }
            }
        }
    }

    private static boolean isInstallerPackage(CharSequence packageName) {
        if (packageName == null) {
            return false;
        }
        String name = packageName.toString().toLowerCase(Locale.ROOT);
        if (name.contains("com.mapcontrol")) {
            return false;
        }
        return name.contains("packageinstaller") || name.contains("permissioncontroller");
    }

    private static boolean isInstallLabel(AccessibilityNodeInfo node) {
        return matches(nodeLabel(node), INSTALL_LABELS);
    }

    private static boolean isCancel(AccessibilityNodeInfo node) {
        return matches(nodeLabel(node), CANCEL_LABELS);
    }

    private static boolean matches(String value, String[] labels) {
        if (value == null) {
            return false;
        }
        for (String label : labels) {
            if (label.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static String nodeLabel(AccessibilityNodeInfo node) {
        CharSequence text = node.getText();
        if (text == null || text.length() == 0) {
            text = node.getContentDescription();
        }
        if (text == null) {
            return null;
        }
        return text.toString().trim().toLowerCase(Locale.ROOT);
    }

    private static boolean activate(AccessibilityNodeInfo start) {
        Rect bounds = new Rect();
        start.getBoundsInScreen(bounds);
        boolean clicked = clickInstallNode(start, bounds);
        Log.i(TAG, "INSTALL denemesi click=" + clicked + " bounds=" + bounds);
        return clicked;
    }

    private static boolean clickInstallNode(AccessibilityNodeInfo start, Rect labelBounds) {
        AccessibilityNodeInfo current = start;
        for (int i = 0; i < 4 && current != null; i++) {
            if (isCancel(current)) {
                if (current != start) {
                    current.recycle();
                }
                return false;
            }
            Rect currentBounds = new Rect();
            current.getBoundsInScreen(currentBounds);
            boolean sameTarget = current == start
                    || currentBounds.width() <= labelBounds.width() * 2;
            if (sameTarget && current.isEnabled() && current.isClickable()
                    && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                if (current != start) {
                    current.recycle();
                }
                return true;
            }
            AccessibilityNodeInfo parent = current.getParent();
            if (current != start) {
                current.recycle();
            }
            current = parent;
        }
        if (current != null && current != start) {
            current.recycle();
        }
        return false;
    }
}
