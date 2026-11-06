package com.mapcontrol.gps;

import android.app.AppOpsManager;
import android.content.Context;
import android.location.LocationManager;
import android.os.Process;

/** Bağlantının dayandığı cihaz sorguları. */
final class DeviceChecks {

    private static final String OP_MOCK_LOCATION = "android:mock_location";

    private DeviceChecks() {
    }

    static boolean locationEnabled(Context context) {
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return manager != null && manager.isLocationEnabled();
    }

    /** Bu uygulama Geliştirici seçenekleri'nde "sahte konum uygulaması" olarak seçili mi. */
    static boolean mockAllowed(Context context) {
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) {
            return false;
        }
        try {
            int mode = ops.unsafeCheckOpNoThrow(OP_MOCK_LOCATION, Process.myUid(), context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
