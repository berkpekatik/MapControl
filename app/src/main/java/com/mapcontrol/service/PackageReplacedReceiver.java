package com.mapcontrol.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.mapcontrol.util.LauncherModeManager;

/**
 * Uygulama güncellendikten hemen sonra ({@code MY_PACKAGE_REPLACED}) Launcher Modu'nu geri kurar.
 * Kullanıcının FrostNav'ı elle açmasına gerek kalmadan HOME tuşu launcher'a gitsin diye. Arayüz
 * başlatmaz (Android 10+ arka plan kısıtı); yalnızca alias + cihaz sahibi HOME sabitlemesi.
 */
public class PackageReplacedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }
        LauncherModeManager.restoreIfEnabled(context.getApplicationContext());
    }
}
