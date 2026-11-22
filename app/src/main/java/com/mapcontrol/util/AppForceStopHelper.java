package com.mapcontrol.util;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Arka plandaki uygulamayı kapatır ({@link ActivityManager#killBackgroundProcesses}, normal izin).
 *
 * <p>Gerçek "zorla durdur" ({@code FORCE_STOP_PACKAGES}) yalnızca sistem imzalı uygulamalara ve adb'ye açık;
 * Device Owner da yapamaz. Eskiden kullanılan askıya al/geri aç süreci düşürmüyordu (T19C/Android 11'de
 * doğrulandı: PID aynı kaldı). Ekranda görünen ya da ön plan servisi olan süreçlere Android dokundurmaz;
 * onlar için {@link Result#STILL_RUNNING} döner, kullanıcı Uygulama bilgisi → Durmaya zorla'yı kullanır.
 * Kendisi, Ayarlar, Play Services ve SystemUI dokunulmaz.
 */
public final class AppForceStopHelper {

    private static final String TAG = "AppForceStop";

    private static final String PKG_GMS = "com.google.android.gms";
    private static final String PKG_SETTINGS = "com.android.settings";
    private static final String PKG_SYSTEMUI = "com.android.systemui";

    /** Kapatma sonrası süreç listesinin oturması için bekleme. */
    private static final long VERIFY_DELAY_MS = 300L;

    public enum Result {
        /** Süreç kalmadı (ya da doğrulanamadı ama çağrı başarılı). */
        CLOSED,
        /** Ekranda ya da ön plan servisiyle çalışıyor; Android arka plan kapatmasına izin vermedi. */
        STILL_RUNNING,
        FAILED
    }

    private AppForceStopHelper() {
    }

    public static boolean mayForceStop(@NonNull Context context, @NonNull String packageName) {
        if (packageName.isEmpty() || packageName.equals(context.getPackageName())) {
            return false;
        }
        if (isBlocked(packageName)) {
            return false;
        }
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Ana iş parçacığında çağırmayın (doğrulama dumpsys okur). */
    @NonNull
    public static Result stopPackage(@NonNull Context context, @NonNull String packageName) {
        if (!mayForceStop(context, packageName)) {
            return Result.FAILED;
        }
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return Result.FAILED;
        }
        try {
            am.killBackgroundProcesses(packageName);
        } catch (Exception e) {
            Log.w(TAG, "kill failed for " + packageName, e);
            return Result.FAILED;
        }
        try {
            Thread.sleep(VERIFY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Boolean running = RunningAppsHelper.isRunning(packageName);
        return Boolean.TRUE.equals(running) ? Result.STILL_RUNNING : Result.CLOSED;
    }

    private static boolean isBlocked(String packageName) {
        return PKG_GMS.equals(packageName)
                || PKG_SETTINGS.equals(packageName)
                || PKG_SYSTEMUI.equals(packageName);
    }
}
