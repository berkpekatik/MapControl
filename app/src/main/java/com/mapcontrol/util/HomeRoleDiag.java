package com.mapcontrol.util;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;

/**
 * Ana ekran rol penceresi otomatik onaylanamazsa son denemenin teşhisini dosyaya yazar. Logcat araç
 * yeniden başlayınca kaybolduğu için kalıcıdır; web kurulum panelindeki hata raporu dosyayı adb ile okur
 * ve kullanıcı metni geliştiriciye gönderir. Yalnızca sistem izin penceresinin içeriği yazılır, diğer
 * pencerelerin yalnızca paket adı (kişisel içerik rapora girmesin). Arka plan iş parçacığından çağrılır.
 */
final class HomeRoleDiag {

    private static final String TAG = "InstallerConfirm";
    private static final String FILE_NAME = "home-role-diag.txt";
    private static final int MAX_NODES = 150;
    private static final int MAX_TEXT = 80;

    private HomeRoleDiag() {
    }

    /** @param service erişilebilirlik hizmeti bağlı değilse null; o zaman pencere yapısı yazılamaz */
    static void report(Context context, AccessibilityService service, String reason) {
        Log.w(TAG, "HOME otomatik onay başarısız: " + reason);
        StringBuilder out = new StringBuilder();
        out.append("time: ").append(new Date()).append('\n');
        out.append("reason: ").append(reason).append('\n');
        out.append("accessibility service: ").append(service != null ? "connected" : "not connected").append('\n');
        if (service != null) {
            appendWindows(service, out);
        }
        File file = file(context);
        if (file == null) {
            Log.w(TAG, "teşhis dosyası yazılamadı: depolama yok");
            return;
        }
        try {
            Files.write(file.toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "teşhis dosyası yazılamadı", e);
        }
    }

    /** Başarılı onaydan sonra eski teşhis raporda yanıltıcı olmasın. */
    static void clear(Context context) {
        File file = file(context);
        if (file != null) {
            file.delete();
        }
    }

    /** Son teşhis "onaya basıldı ama HOME değişmedi" mi (kilit işareti öncesi sürümlerin kaydı). */
    static boolean lastReportUnchanged(Context context) {
        File file = file(context);
        if (file == null || !file.isFile()) {
            return false;
        }
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).contains(UNCHANGED_REASON);
        } catch (Exception e) {
            return false;
        }
    }

    static final String UNCHANGED_REASON = "onaya basıldı ama HOME değişmedi";

    private static File file(Context context) {
        File dir = context.getExternalFilesDir(null);
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    private static void appendWindows(AccessibilityService service, StringBuilder out) {
        boolean dumped = false;
        try {
            List<AccessibilityWindowInfo> windows = service.getWindows();
            out.append("windows: ").append(windows == null ? 0 : windows.size()).append('\n');
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    AccessibilityNodeInfo root = window.getRoot();
                    out.append("window type=").append(window.getType())
                            .append(" active=").append(window.isActive());
                    dumped |= appendRoot(root, out);
                    if (root != null) {
                        root.recycle();
                    }
                    window.recycle();
                }
            }
        } catch (Exception e) {
            out.append("windows failed: ").append(e).append('\n');
        }
        if (dumped) {
            return;
        }
        // Bazı araç ünitelerinde pencere listesi boş döner.
        try {
            AccessibilityNodeInfo active = service.getRootInActiveWindow();
            out.append("active root");
            appendRoot(active, out);
            if (active != null) {
                active.recycle();
            }
        } catch (Exception e) {
            out.append("active root failed: ").append(e).append('\n');
        }
    }

    /** Satırı paket adıyla bitirir; sistem izin penceresiyse düğüm ağacını ekler. */
    private static boolean appendRoot(AccessibilityNodeInfo root, StringBuilder out) {
        CharSequence pkg = root == null ? null : root.getPackageName();
        out.append(" pkg=").append(pkg).append('\n');
        if (!InstallerConfirmClicker.isInstallerPackage(pkg)) {
            return false;
        }
        appendNode(root, 1, new int[]{MAX_NODES}, out);
        return true;
    }

    private static void appendNode(AccessibilityNodeInfo node, int depth, int[] left, StringBuilder out) {
        if (left[0]-- <= 0) {
            return;
        }
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
        CharSequence className = node.getClassName();
        String shortClass = className == null ? "?" : className.toString().substring(className.toString().lastIndexOf('.') + 1);
        out.append(shortClass)
                .append(" | ").append(node.getViewIdResourceName())
                .append(" | ").append(clip(node.getText()))
                .append(" | ").append(clip(node.getContentDescription()))
                .append(" | clickable=").append(node.isClickable())
                .append(" enabled=").append(node.isEnabled())
                .append(" checked=").append(node.isChecked())
                .append('\n');
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                appendNode(child, depth + 1, left, out);
                child.recycle();
            }
        }
    }

    private static String clip(CharSequence text) {
        if (text == null) {
            return "";
        }
        String value = text.toString().replace('\n', ' ');
        return value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT) : value;
    }
}
