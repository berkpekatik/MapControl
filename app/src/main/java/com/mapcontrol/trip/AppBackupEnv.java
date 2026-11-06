package com.mapcontrol.trip;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.DisplayMetrics;

/** {@link AppBackup.Env}'in araçtaki karşılığı: gerçek tercih dosyaları, yüklü paketler ve ekran. */
public final class AppBackupEnv implements AppBackup.Env {

    private final Context context;

    public AppBackupEnv(Context context) {
        this.context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
    }

    @Override
    public SharedPreferences prefs(String file) {
        return context.getSharedPreferences(file, Context.MODE_PRIVATE);
    }

    @Override
    public boolean isInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public int screenWidth() {
        DisplayMetrics m = context.getResources().getDisplayMetrics();
        return Math.max(m.widthPixels, m.heightPixels);
    }

    @Override
    public int screenHeight() {
        DisplayMetrics m = context.getResources().getDisplayMetrics();
        return Math.min(m.widthPixels, m.heightPixels);
    }
}
