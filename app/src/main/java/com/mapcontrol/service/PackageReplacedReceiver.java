package com.mapcontrol.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.mapcontrol.util.LauncherModeManager;

/**
 * Uygulama güncellendikten hemen sonra ({@code MY_PACKAGE_REPLACED}) Launcher Modu'nu geri kurar ve
 * servisi başlatır (otomatik başlatma açıksa). Kullanıcının MapControl'ı elle açmasına gerek kalmadan HOME
 * tuşu launcher'a gitsin ve servise bağlı özellikler çalışsın diye. Arayüz başlatmaz (Android 10+ arka plan kısıtı).
 */
public class PackageReplacedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }
        LauncherModeManager.restoreIfEnabled(context.getApplicationContext());
        MapControlService.skipClusterSplashOnNextCreate();
        BootReceiver.startServiceIfAutoStartEnabled(context);
    }
}
