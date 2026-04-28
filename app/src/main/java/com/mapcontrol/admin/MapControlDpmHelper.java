package com.mapcontrol.admin;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;

/**
 * Sadece cihaz sahibi uygulaması olarak ayarlandıysa bu paketin kaldırılmasını dener.
 */
public final class MapControlDpmHelper {
    private MapControlDpmHelper() {
    }

    public static void tryBlockOwnUninstallIfDeviceOwner(Context context) {
        if (context == null) {
            return;
        }
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context
                    .getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null) {
                return;
            }
            String pkg = context.getPackageName();
            if (!dpm.isDeviceOwnerApp(pkg)) {
                return;
            }
            ComponentName admin = new ComponentName(context, MapControlDeviceAdminReceiver.class);
            dpm.setUninstallBlocked(admin, pkg, true);
        } catch (SecurityException e) {
            // İzin veya cihaz politikası yok
        } catch (Exception e) {
            // Diğer OEM farklılıkları
        }
    }
}
