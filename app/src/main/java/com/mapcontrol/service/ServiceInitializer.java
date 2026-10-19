package com.mapcontrol.service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class ServiceInitializer {
    public interface ServiceCallback {
        void onLogReceived(String message);
        void log(String message);
    }

    private final Context context;
    private final ServiceCallback callback;
    private BroadcastReceiver logReceiver;

    public ServiceInitializer(Context context, ServiceCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public void onCreate() {
        startForegroundService();
        registerLogReceiver();
    }

    public void onDestroy() {
        unregisterLogReceiver();
    }

    public void startForegroundService() {
        try {
            Intent serviceIntent = new Intent(context, MapControlService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            callback.log("Foreground Service başlatıldı");
        } catch (Exception e) {
            callback.log("Foreground Service başlatma hatası: " + e.getMessage());
        }
    }

    public void registerLogReceiver() {
        if (logReceiver != null) {
            return;
        }

        logReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if ("com.mapcontrol.LOG_MESSAGE".equals(intent.getAction())) {
                    String message = intent.getStringExtra("log_message");
                    if (message != null) {
                        callback.onLogReceived(message);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter("com.mapcontrol.LOG_MESSAGE");
        context.registerReceiver(logReceiver, filter);
    }

    public void unregisterLogReceiver() {
        if (logReceiver != null) {
            try {
                context.unregisterReceiver(logReceiver);
            } catch (Exception ignored) {
            }
            logReceiver = null;
        }
    }
}
