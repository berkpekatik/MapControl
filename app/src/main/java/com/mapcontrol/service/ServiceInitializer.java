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
        if (context.getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(BootReceiver.KEY_BOOT_AUTO_START, false)) {
            startForegroundService();
        } else {
            callback.log("Automatic background service is disabled");
        }
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
        androidx.core.content.ContextCompat.registerReceiver(
                context, logReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
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
