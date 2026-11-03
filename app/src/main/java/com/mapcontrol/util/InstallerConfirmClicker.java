package com.mapcontrol.util;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.service.GlobalBackService;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Paket yükleyici onay penceresinde 5 sn bekler, sonra INSTALL düğmesine basar.
 * Yalnızca packageinstaller / permissioncontroller pencereleri.
 */
public final class InstallerConfirmClicker {

    private static final String TAG = "InstallerConfirm";
    private static final long RETRY_MS = 400L;
    private static final long COOLDOWN_MS = 2500L;
    private static final int COUNTDOWN_SECONDS = 5;
    private static final String QUESTION_SUFFIX = "install_confirm_question";
    private static final String[] INSTALL_LABELS = {
            "install", "yükle", "yukle", "kur", "update", "güncelle", "guncelle",
            "instalar", "actualizar", "установить", "обновить", "安装", "更新",
            "install anyway", "yine de yükle"
    };
    private static final String[] CANCEL_LABELS = {
            "cancel", "iptal", "vazgeç", "vazgec"
    };
    private static final String[] INSTALL_IDS = {
            "ok_button", "install_confirm", "install_button"
    };

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    // Window root sorgulari senkron binder cagrisidir; hedef uygulama mesgulse
    // saniyelerce bloklar. Main thread'de yapilirsa ANR olur -> arka thread sart.
    private static final ExecutorService SCAN_EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "installer-scan");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean SCAN_IN_FLIGHT = new AtomicBoolean(false);
    private static long lastScanMs;
    private static long cooldownUntil;
    private static boolean pendingArm;
    private static boolean stripShowing;
    private static boolean sawInstallerWhileStrip;
    private static boolean userDismissed;
    private static int secondsLeft;
    private static WindowManager windowManager;
    private static View stripView;
    private static TextView countdownView;

    private static final Runnable TICK = new Runnable() {
        @Override
        public void run() {
            if (!stripShowing) {
                return;
            }
            if (secondsLeft <= 1) {
                dismissStrip();
                AccessibilityService service = GlobalBackService.getInstance();
                if (service != null) {
                    SCAN_EXEC.execute(() -> {
                        boolean clicked = clickInstall(service);
                        if (clicked) {
                            cooldownUntil = SystemClock.uptimeMillis() + COOLDOWN_MS;
                        }
                        Log.i(TAG, "INSTALL click=" + clicked);
                    });
                }
                return;
            }
            secondsLeft--;
            updateCountdownText();
            HANDLER.postDelayed(this, 1000L);
        }
    };

    private InstallerConfirmClicker() {
    }

    public static void arm() {
        userDismissed = false;
        AccessibilityService service = GlobalBackService.getInstance();
        if (service == null) {
            pendingArm = true;
            return;
        }
        if (stripShowing) {
            return;
        }
        showStrip(service);
    }

    public static void disarm() {
        pendingArm = false;
        userDismissed = false;
        sawInstallerWhileStrip = false;
        dismissStrip();
    }

    public static void onWindowEvent(AccessibilityService service) {
        if (service == null || SystemClock.uptimeMillis() < cooldownUntil) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        // Throttle pendingArm dahil her durumda gecerli: armed iken her window
        // event'inde taramak sistemi kilitliyordu.
        if (now - lastScanMs < RETRY_MS) {
            return;
        }
        if (!SCAN_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        lastScanMs = now;
        SCAN_EXEC.execute(() -> {
            boolean confirm;
            try {
                confirm = hasInstallerConfirm(service);
            } catch (Exception ignored) {
                confirm = false;
            }
            SCAN_IN_FLIGHT.set(false);
            boolean found = confirm;
            HANDLER.post(() -> handleScanResult(service, found));
        });
    }

    private static void handleScanResult(AccessibilityService service, boolean confirm) {
        if (SystemClock.uptimeMillis() < cooldownUntil) {
            return;
        }
        if (userDismissed) {
            if (!confirm) {
                userDismissed = false;
            }
            return;
        }
        if (pendingArm && !stripShowing) {
            pendingArm = false;
            showStrip(service);
        }
        if (stripShowing) {
            if (confirm) {
                sawInstallerWhileStrip = true;
            } else if (sawInstallerWhileStrip) {
                dismissStrip();
            }
            return;
        }
        if (confirm) {
            showStrip(service);
        }
    }

    private static void showStrip(AccessibilityService service) {
        if (stripShowing) {
            return;
        }
        try {
            windowManager = (WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE);
            if (windowManager == null) {
                return;
            }
            LinearLayout bar = new LinearLayout(service);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            int pad = dp(service, 16);
            bar.setPadding(pad, dp(service, 12), pad, dp(service, 12));
            GradientDrawable background = new GradientDrawable();
            background.setColor(0xE61B1F27);
            background.setCornerRadius(dp(service, 18));
            bar.setBackground(background);

            TextView text = new TextView(service);
            text.setTextColor(Color.WHITE);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            countdownView = text;
            secondsLeft = COUNTDOWN_SECONDS;
            updateCountdownText();
            bar.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button cancel = new Button(service);
            cancel.setText(R.string.installer_confirm_cancel);
            cancel.setAllCaps(false);
            cancel.setTextColor(Color.WHITE);
            cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            GradientDrawable cancelBg = new GradientDrawable();
            cancelBg.setColor(0xFF5C6370);
            cancelBg.setCornerRadius(dp(service, 12));
            cancel.setBackground(cancelBg);
            cancel.setPadding(dp(service, 18), dp(service, 6), dp(service, 18), dp(service, 6));
            cancel.setMinWidth(0);
            cancel.setMinHeight(0);
            cancel.setMinimumWidth(0);
            cancel.setMinimumHeight(0);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cancelInstall();
                }
            });
            LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cancelLp.leftMargin = dp(service, 12);
            bar.addView(cancel, cancelLp);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.BOTTOM;
            params.y = dp(service, 24);
            windowManager.addView(bar, params);
            stripView = bar;
            stripShowing = true;
            sawInstallerWhileStrip = false; // arka plan taramasi gunceller
            HANDLER.removeCallbacks(TICK);
            HANDLER.postDelayed(TICK, 1000L);
        } catch (Exception e) {
            Log.w(TAG, "Şerit açılamadı", e);
            stripShowing = false;
            stripView = null;
            countdownView = null;
        }
    }

    private static void updateCountdownText() {
        TextView text = countdownView;
        AccessibilityService service = GlobalBackService.getInstance();
        if (text == null || service == null) {
            return;
        }
        text.setText(service.getString(R.string.installer_confirm_countdown, secondsLeft));
    }

    private static void cancelInstall() {
        AccessibilityService service = GlobalBackService.getInstance();
        userDismissed = true;
        dismissStrip();
        if (service != null) {
            SCAN_EXEC.execute(() -> clickViewId(service, "android:id/button2", false));
        }
    }

    private static void dismissStrip() {
        HANDLER.removeCallbacks(TICK);
        View view = stripView;
        WindowManager wm = windowManager;
        stripView = null;
        countdownView = null;
        stripShowing = false;
        sawInstallerWhileStrip = false;
        if (view != null && wm != null) {
            try {
                wm.removeView(view);
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean clickInstall(AccessibilityService service) {
        if (clickViewId(service, "android:id/button1", true)) {
            return true;
        }
        if (clickLabels(service)) {
            return true;
        }
        if (clickIdSuffixes(service)) {
            return true;
        }
        scrollInstaller(service);
        if (clickViewId(service, "android:id/button1", true)) {
            return true;
        }
        if (clickLabels(service)) {
            return true;
        }
        return clickIdSuffixes(service);
    }

    private static boolean hasInstallerConfirm(AccessibilityService service) {
        return anyInstallerRoot(service, new RootHandler() {
            @Override
            public boolean handle(AccessibilityNodeInfo root) {
                return hasQuestion(root) || hasInstallButton(root);
            }
        });
    }

    private static boolean hasQuestion(AccessibilityNodeInfo root) {
        return hasViewIdSuffix(root, QUESTION_SUFFIX);
    }

    private static boolean hasViewId(AccessibilityNodeInfo root, String id) {
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes == null) {
                return false;
            }
            boolean found = !nodes.isEmpty();
            recycle(nodes);
            return found;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasViewIdSuffix(AccessibilityNodeInfo node, String suffix) {
        String viewId = node.getViewIdResourceName();
        if (viewId != null && viewId.endsWith("/" + suffix)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            boolean found = hasViewIdSuffix(child, suffix);
            child.recycle();
            if (found) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasInstallButton(AccessibilityNodeInfo root) {
        for (String label : INSTALL_LABELS) {
            List<AccessibilityNodeInfo> nodes = findByText(root, label);
            if (nodes == null) {
                continue;
            }
            try {
                for (AccessibilityNodeInfo node : nodes) {
                    if (isEnabledInstallButton(node)) {
                        return true;
                    }
                }
            } finally {
                recycle(nodes);
            }
        }
        return false;
    }

    private static boolean clickViewId(AccessibilityService service, final String viewId, final boolean skipInstallOnCancel) {
        return anyInstallerRoot(service, new RootHandler() {
            @Override
            public boolean handle(AccessibilityNodeInfo root) {
                List<AccessibilityNodeInfo> nodes;
                try {
                    nodes = root.findAccessibilityNodeInfosByViewId(viewId);
                } catch (Exception ignored) {
                    return false;
                }
                if (nodes == null) {
                    return false;
                }
                try {
                    for (AccessibilityNodeInfo node : nodes) {
                        if (node == null || !node.isEnabled()) {
                            continue;
                        }
                        if (skipInstallOnCancel && isCancel(node)) {
                            continue;
                        }
                        if (!skipInstallOnCancel && isInstallLabel(node)) {
                            continue;
                        }
                        if (activate(node)) {
                            return true;
                        }
                    }
                    return false;
                } finally {
                    recycle(nodes);
                }
            }
        });
    }

    private static boolean clickLabels(AccessibilityService service) {
        return anyInstallerRoot(service, new RootHandler() {
            @Override
            public boolean handle(AccessibilityNodeInfo root) {
                for (String label : INSTALL_LABELS) {
                    List<AccessibilityNodeInfo> nodes = findByText(root, label);
                    if (nodes == null) {
                        continue;
                    }
                    try {
                        for (AccessibilityNodeInfo node : nodes) {
                            if (!isEnabledInstallButton(node)) {
                                continue;
                            }
                            if (activate(node)) {
                                return true;
                            }
                        }
                    } finally {
                        recycle(nodes);
                    }
                }
                return false;
            }
        });
    }

    private static boolean clickIdSuffixes(AccessibilityService service) {
        return anyInstallerRoot(service, new RootHandler() {
            @Override
            public boolean handle(AccessibilityNodeInfo root) {
                return activateIdSuffix(root);
            }
        });
    }

    private static boolean activateIdSuffix(AccessibilityNodeInfo node) {
        String viewId = node.getViewIdResourceName();
        if (viewId != null && node.isEnabled() && !isCancel(node)) {
            for (String suffix : INSTALL_IDS) {
                if (viewId.endsWith("/" + suffix) && activate(node)) {
                    return true;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            boolean found = activateIdSuffix(child);
            child.recycle();
            if (found) {
                return true;
            }
        }
        return false;
    }

    private static void scrollInstaller(AccessibilityService service) {
        anyInstallerRoot(service, new RootHandler() {
            @Override
            public boolean handle(AccessibilityNodeInfo root) {
                scrollForward(root);
                return true;
            }
        });
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

    private static boolean isEnabledInstallButton(AccessibilityNodeInfo node) {
        if (node == null || !node.isEnabled() || isCancel(node) || !isInstallLabel(node)) {
            return false;
        }
        if (node.isClickable()) {
            return true;
        }
        CharSequence className = node.getClassName();
        return className != null && className.toString().contains("Button");
    }

    private static boolean activate(AccessibilityNodeInfo start) {
        AccessibilityNodeInfo current = start;
        for (int i = 0; i < 4 && current != null; i++) {
            if (isCancel(current)) {
                if (current != start) {
                    current.recycle();
                }
                return false;
            }
            if (current.isEnabled() && current.isClickable()
                    && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.i(TAG, "INSTALL tıklandı id=" + current.getViewIdResourceName());
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

    private interface RootHandler {
        boolean handle(AccessibilityNodeInfo root);
    }

    private static boolean anyInstallerRoot(AccessibilityService service, RootHandler handler) {
        boolean handled = false;
        try {
            List<AccessibilityWindowInfo> windows = service.getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    if (window == null) {
                        continue;
                    }
                    AccessibilityNodeInfo root = window.getRoot();
                    try {
                        if (!handled && root != null && isInstallerPackage(root.getPackageName())) {
                            handled = handler.handle(root);
                        }
                    } finally {
                        if (root != null) {
                            root.recycle();
                        }
                        window.recycle();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (handled) {
            return true;
        }
        AccessibilityNodeInfo active = null;
        try {
            active = service.getRootInActiveWindow();
            if (active == null || !isInstallerPackage(active.getPackageName())) {
                return false;
            }
            return handler.handle(active);
        } catch (Exception ignored) {
            return false;
        } finally {
            if (active != null) {
                active.recycle();
            }
        }
    }

    private static List<AccessibilityNodeInfo> findByText(AccessibilityNodeInfo root, String label) {
        try {
            return root.findAccessibilityNodeInfosByText(label);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void recycle(List<AccessibilityNodeInfo> nodes) {
        if (nodes == null) {
            return;
        }
        for (AccessibilityNodeInfo node : nodes) {
            if (node != null) {
                node.recycle();
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

    private static int dp(AccessibilityService service, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                service.getResources().getDisplayMetrics());
    }
}
