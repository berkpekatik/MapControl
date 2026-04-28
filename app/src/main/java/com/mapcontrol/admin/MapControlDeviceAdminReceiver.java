package com.mapcontrol.admin;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Cihaz sahibi (Device Owner) atandığında kaldırmayı engellemek için {@link android.app.admin.DevicePolicyManager} ile birlikte kullanılır.
 * Kurulum: {@code dpm set-device-owner com.mapcontrol/.admin.MapControlDeviceAdminReceiver} (cihazda kullanıcı ve kurulum kısıtları).
 */
public class MapControlDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        MapControlDpmHelper.tryBlockOwnUninstallIfDeviceOwner(context);
    }
}
