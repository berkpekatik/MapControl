package com.mapcontrol.nav;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

import com.mapcontrol.R;
import com.mapcontrol.service.GlobalBackService;

/**
 * Ayarlardan açılınca Yandex durumunu kontrol eder ve cluster overlay'i senkronize eder.
 */
public final class YandexClusterNavCoordinator {

    public enum ActivateResult {
        SHOWING,
        YANDEX_NOT_RUNNING,
        YANDEX_OPEN_NO_NAV,
        ACCESSIBILITY_REQUIRED,
        SERVICE_CONNECTING
    }

    private YandexClusterNavCoordinator() {
    }

    public static ActivateResult activate(Context context) {
        Context app = context.getApplicationContext();
        if (!GlobalBackService.isRegisteredInSystemAccessibilitySettings(app)) {
            return ActivateResult.ACCESSIBILITY_REQUIRED;
        }
        YandexClusterNavOverlay.setEnabled(app, true);
        YandexClusterNavOverlay.getInstance(app).invalidateCache();
        return syncNow(app);
    }

    public static void deactivate(Context context) {
        YandexClusterNavOverlay.setEnabled(context.getApplicationContext(), false);
    }

    public static ActivateResult syncNow(Context context) {
        Context app = context.getApplicationContext();
        if (!YandexClusterNavOverlay.isEnabled(app)) {
            return ActivateResult.YANDEX_OPEN_NO_NAV;
        }
        if (!YandexMapsPresence.isLikelyRunning(app)) {
            YandexClusterNavOverlay.getInstance(app).hide();
            return ActivateResult.YANDEX_NOT_RUNNING;
        }
        GlobalBackService.YandexSyncResult result = GlobalBackService.syncYandexClusterNav(app);
        switch (result) {
            case OK:
                return ActivateResult.SHOWING;
            case NAV_INACTIVE:
                return ActivateResult.YANDEX_OPEN_NO_NAV;
            case SERVICE_NOT_CONNECTED:
                return ActivateResult.SERVICE_CONNECTING;
            case YANDEX_NOT_OPEN:
            default:
                if (!YandexMapsPresence.isLikelyRunning(app)) {
                    return ActivateResult.YANDEX_NOT_RUNNING;
                }
                return ActivateResult.YANDEX_OPEN_NO_NAV;
        }
    }

    public static void openAccessibilitySettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    public static void showActivateToast(Context context, ActivateResult result) {
        int resId;
        switch (result) {
            case SHOWING:
                resId = R.string.yandex_cluster_nav_toast_showing;
                break;
            case YANDEX_NOT_RUNNING:
                resId = R.string.yandex_cluster_nav_toast_yandex_closed;
                break;
            case YANDEX_OPEN_NO_NAV:
                resId = R.string.yandex_cluster_nav_toast_no_nav;
                break;
            case ACCESSIBILITY_REQUIRED:
                resId = R.string.yandex_cluster_nav_toast_accessibility;
                break;
            case SERVICE_CONNECTING:
            default:
                resId = R.string.yandex_cluster_nav_toast_service_wait;
                break;
        }
        Toast.makeText(context, resId, Toast.LENGTH_LONG).show();
    }
}
