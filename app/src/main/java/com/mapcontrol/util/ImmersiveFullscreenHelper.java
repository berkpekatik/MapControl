package com.mapcontrol.util;

import android.app.Activity;
import android.view.Window;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Durum ve gezinme çubuklarını gizleyerek tam ekran (immersive) deneyim sağlar.
 * Araç Launcher Modu açıkken kullanılır; sistem HOME seçimi OEM'de kilitli olsa bile
 * uygulama içi launcher deneyimi tam ekran sürdürülebilir.
 */
public final class ImmersiveFullscreenHelper {

    private ImmersiveFullscreenHelper() {
    }

    public static void setImmersiveFullscreen(Activity activity, boolean enabled) {
        if (activity == null) {
            return;
        }
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }

        WindowCompat.setDecorFitsSystemWindows(window, !enabled);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller == null) {
            return;
        }

        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    /** Odak veya yaşam döngüsü sonrası çubuklar geri gelmişse launcher modunda yeniden uygular. */
    public static void reapplyIfLauncherMode(Activity activity) {
        if (activity == null || !LauncherModeManager.isEnabled(activity)) {
            return;
        }
        setImmersiveFullscreen(activity, true);
    }
}
