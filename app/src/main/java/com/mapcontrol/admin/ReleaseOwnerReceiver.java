package com.mapcontrol.admin;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * adb ile kaldirmadan once cihaz sahipligini birakir. Cihaz sahibi uygulamayi {@code pm uninstall}
 * silemez (DELETE_FAILED_DEVICE_POLICY_MANAGER) ve {@code dpm remove-active-admin} yalnizca
 * testOnly uygulamalarda calisir; bu alici olmadan tek cikis fabrika ayarlari olurdu.
 *
 * Manifest'te android.permission.DUMP ile korunur: adb shell gonderebilir, normal uygulamalar
 * gonderemez. Kullanim (uninstall.sh, web yukleyici):
 * <pre>
 * am broadcast -f 0x20 -n com.mapcontrol/.admin.ReleaseOwnerReceiver -a com.mapcontrol.action.RELEASE_DEVICE_OWNER
 * </pre>
 * Sonuc "result=N": 1 birakildi, 2 zaten cihaz sahibi degil, 3 hata. 0 alinirsa yuklu surumde
 * bu alici yoktur. Silmeyi alici yapmaz; ardindan adb {@code pm uninstall} calistirir.
 */
public final class ReleaseOwnerReceiver extends BroadcastReceiver {
    private static final String TAG = "ReleaseOwner";

    public static final String ACTION = "com.mapcontrol.action.RELEASE_DEVICE_OWNER";
    static final int RESULT_RELEASED = 1;
    static final int RESULT_NOT_OWNER = 2;
    static final int RESULT_FAILED = 3;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) {
            return;
        }
        setResultCode(release(context));
    }

    @SuppressWarnings("deprecation")
    private static int release(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            String pkg = context.getPackageName();
            if (dpm == null || !dpm.isDeviceOwnerApp(pkg)) {
                return RESULT_NOT_OWNER;
            }
            // Cihaz sahipligini, admin kaydini ve ana ekran sabitlemesini birlikte kaldirir.
            dpm.clearDeviceOwnerApp(pkg);
            Log.i(TAG, "cihaz sahipligi birakildi");
            return RESULT_RELEASED;
        } catch (RuntimeException e) {
            Log.w(TAG, "cihaz sahipligi birakilamadi", e);
            return RESULT_FAILED;
        }
    }
}
