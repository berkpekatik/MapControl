package com.mapcontrol.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.mapcontrol.R;

/**
 * Kamera 360 kaydi surerken sureci ayakta tutan on plan servisi.
 * Kayit yalnizca kullanici durdurunca (veya USB cikarilirsa) biter;
 * uygulama arka planda / baska menude olmasi kaydi etkilemez.
 */
public class RecordingService extends Service {
    private static final String CHANNEL_ID = "camera360_recording";
    private static final int NOTIF_ID = 3601;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "Kamera 360 Kayit", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mdi_camera)
                .setContentTitle(getString(R.string.camera360_notif_title))
                .setContentText(getString(R.string.camera360_notif_text))
                .setOngoing(true)
                .build();
        startForeground(NOTIF_ID, notif);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
