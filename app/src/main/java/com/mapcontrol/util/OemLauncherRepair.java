package com.mapcontrol.util;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import com.mapcontrol.admin.MapControlDeviceAdminReceiver;
import com.mapcontrol.launcher.VehicleProfile;

/**
 * Jaecoo (dikey ekran) aracın kendi launcher'ı ({@link #PACKAGE}, Launcher3 tabanlı) uygulama güncellemelerinde ve
 * bileşen değişikliklerinde MapControl ikonlarını masaüstüne kopyalıyor. Launcher verisini silmek ({@code pm clear} ile
 * aynı) kopyaları kaldırıyor; launcher masaüstünü yeniden kurarken sistem ikonları ve konumları kalıyor (araçta teyitli).
 *
 * <p>Uygulama {@code pm clear} çalıştıramaz (CLEAR_APP_USER_DATA sistem izni); cihaz sahibiyken
 * {@link DevicePolicyManager#clearApplicationUserData} aynı işi yapar.
 */
public final class OemLauncherRepair {

    public static final String PACKAGE = "com.desaysv.launcher";

    public interface Callback {
        void onResult(boolean ok);
    }

    private OemLauncherRepair() {
    }

    /** Düğme yalnız dikey ekranda (Jaecoo), cihaz sahibiyken ve aracın launcher'ı kuruluyken gösterilir. */
    public static boolean isAvailable(Context context) {
        if (VehicleProfile.Companion.compute(context) != VehicleProfile.PORTRAIT) {
            return false;
        }
        if (deviceOwnerDpm(context) == null) {
            return false;
        }
        try {
            context.getPackageManager().getApplicationInfo(PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Aracın launcher verisini siler; sonuç ana iş parçacığında döner. */
    public static void clear(Context context, Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        DevicePolicyManager dpm = deviceOwnerDpm(context);
        if (dpm == null) {
            main.post(() -> callback.onResult(false));
            return;
        }
        try {
            ComponentName admin = new ComponentName(context, MapControlDeviceAdminReceiver.class);
            dpm.clearApplicationUserData(admin, PACKAGE, command -> main.post(command),
                    (packageName, succeeded) -> callback.onResult(succeeded));
        } catch (Exception e) {
            main.post(() -> callback.onResult(false));
        }
    }

    private static DevicePolicyManager deviceOwnerDpm(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                return dpm;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
