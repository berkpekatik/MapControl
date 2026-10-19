package com.mapcontrol.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.mapcontrol.util.BootSession;

/**
 * Sistem açılışında {@link MapControlService} başlatır.
 * Ana ekran, araç güç alınca servisin içinden açılır.
 * Tercihler: {@link #PREFS_NAME} içinde {@link #KEY_BOOT_AUTO_START}, {@link #KEY_BOOT_AUTO_LAUNCH_UI}.
 */
public class BootReceiver extends BroadcastReceiver {

    public static final String PREFS_NAME = "MapControlPrefs";
    public static final String KEY_BOOT_AUTO_START = "bootAutoStart";
    public static final String KEY_BOOT_AUTO_LAUNCH_UI = "bootAutoLaunchUi";
    public static final String EXTRA_BOOT = "com.mapcontrol.extra.BOOT";

    private static final String ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON";
    private static final String ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !ACTION_QUICKBOOT_POWERON.equals(action)
                && !ACTION_HTC_QUICKBOOT_POWERON.equals(action)) {
            return;
        }

        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_BOOT_AUTO_START, true)) {
            return;
        }

        BootSession.setUiPending(app, prefs.getBoolean(KEY_BOOT_AUTO_LAUNCH_UI, true));

        Intent serviceIntent = new Intent(app, MapControlService.class);
        serviceIntent.putExtra(EXTRA_BOOT, true);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(serviceIntent);
            } else {
                app.startService(serviceIntent);
            }
        } catch (Exception ignored) {
        }
    }
}
