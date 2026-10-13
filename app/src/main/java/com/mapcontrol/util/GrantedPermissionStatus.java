package com.mapcontrol.util;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;

import com.mapcontrol.service.GlobalBackService;

import java.util.ArrayList;
import java.util.List;

/**
 * Kurulumun MapControl'e vermeye çalıştığı yetkilerin anlık durumu.
 */
public final class GrantedPermissionStatus {

    private static final String NOTIFICATION_LISTENER =
            "com.mapcontrol/com.mapcontrol.media.LauncherMediaNotificationListener";

    private static final String[] APP_OPS = {
            "SYSTEM_ALERT_WINDOW",
            "GET_USAGE_STATS",
            "REQUEST_INSTALL_PACKAGES",
            "MANAGE_EXTERNAL_STORAGE",
            "WRITE_SETTINGS"
    };

    private static final String[] PM_PERMISSIONS = {
            "android.permission.READ_LOGS",
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.READ_SECURE_SETTINGS",
            "android.permission.READ_SETTINGS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.WRITE_SETTINGS",
            "android.permission.WRITE_SECURE_SETTINGS",
            "android.permission.RECORD_AUDIO",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.CAMERA",
            "android.permission.INTERNET",
            "android.permission.READ_PHONE_STATE",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.INTERACT_ACROSS_USERS_FULL",
            "android.permission.DELETE_PACKAGES",
            "android.permission.MEDIA_CONTENT_CONTROL",
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.RECEIVE_BOOT_COMPLETED"
    };

    private GrantedPermissionStatus() {
    }

    public static final class Row {
        public final String label;
        public final boolean granted;

        public Row(String label, boolean granted) {
            this.label = label;
            this.granted = granted;
        }
    }

    public static List<Row> read(Context context) {
        List<Row> rows = new ArrayList<Row>();
        for (String op : APP_OPS) {
            rows.add(new Row(op, isAppOpGranted(context, op)));
        }
        for (String perm : PM_PERMISSIONS) {
            rows.add(new Row(shortName(perm), isPmGranted(context, perm)));
        }
        rows.add(new Row("DEVICE_OWNER", isDeviceOwner(context)));
        rows.add(new Row("ACCESSIBILITY", GlobalBackService.isRegisteredInSystemAccessibilitySettings(context)));
        rows.add(new Row("NOTIFICATION_LISTENER", isNotificationListenerEnabled(context)));
        rows.add(new Row("DOZE_WHITELIST", isIgnoringBatteryOptimizations(context)));
        return rows;
    }

    private static String shortName(String permission) {
        int slash = permission.lastIndexOf('.');
        return slash >= 0 ? permission.substring(slash + 1) : permission;
    }

    private static boolean isPmGranted(Context context, String permission) {
        try {
            return context.getPackageManager().checkPermission(permission, context.getPackageName())
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isAppOpGranted(Context context, String op) {
        try {
            if ("SYSTEM_ALERT_WINDOW".equals(op)) {
                return Settings.canDrawOverlays(context);
            }
            if ("GET_USAGE_STATS".equals(op)) {
                return UsageAccessHelper.hasPackageUsageAccess(context);
            }
            if ("REQUEST_INSTALL_PACKAGES".equals(op)) {
                return context.getPackageManager().canRequestPackageInstalls();
            }
            if ("MANAGE_EXTERNAL_STORAGE".equals(op)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return Environment.isExternalStorageManager();
                }
                return isPmGranted(context, "android.permission.MANAGE_EXTERNAL_STORAGE");
            }
            if ("WRITE_SETTINGS".equals(op)) {
                return Settings.System.canWrite(context);
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isDeviceOwner(Context context) {
        try {
            DevicePolicyManager manager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            return manager != null && manager.isDeviceOwnerApp(context.getPackageName());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isNotificationListenerEnabled(Context context) {
        try {
            String enabled = Settings.Secure.getString(
                    context.getContentResolver(),
                    "enabled_notification_listeners");
            return enabled != null && (enabled.contains(NOTIFICATION_LISTENER)
                    || enabled.contains("LauncherMediaNotificationListener"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isIgnoringBatteryOptimizations(Context context) {
        try {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return power != null && power.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Exception ignored) {
            return false;
        }
    }
}
