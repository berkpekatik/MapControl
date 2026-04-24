package com.mapcontrol.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.mapcontrol.ui.activity.MainActivity;

/**
 * Sistem açılışında {@link MapControlService} ve isteğe bağlı olarak {@link MainActivity} başlatır.
 * Tercihler: {@link #PREFS_NAME} içinde {@link #KEY_BOOT_AUTO_START}, {@link #KEY_BOOT_AUTO_LAUNCH_UI}.
 */
public class BootReceiver extends BroadcastReceiver {

    public static final String PREFS_NAME = "MapControlPrefs";
    public static final String KEY_BOOT_AUTO_START = "bootAutoStart";
    public static final String KEY_BOOT_AUTO_LAUNCH_UI = "bootAutoLaunchUi";

    private static final long UI_LAUNCH_DELAY_MS = 4000L;

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

        Intent serviceIntent = new Intent(app, MapControlService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(serviceIntent);
            } else {
                app.startService(serviceIntent);
            }
        } catch (Exception ignored) {
            // Örn. arka planda başlatma kısıtı — kullanıcı bildirimden açabilir
        }

        if (!prefs.getBoolean(KEY_BOOT_AUTO_LAUNCH_UI, true)) {
            return;
        }

        Handler main = new Handler(Looper.getMainLooper());
        main.postDelayed(() -> {
            try {
                Intent activityIntent = new Intent(app, MainActivity.class);
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                app.startActivity(activityIntent);
            } catch (Exception ignored) {
            }
        }, UI_LAUNCH_DELAY_MS);
    }
}
