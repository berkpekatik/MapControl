package com.mapcontrol.ai;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

/**
 * Sesli asistanı ({@code com.mapcontrol.ai}) dışarıdan başlatır: direksiyon ses tuşu. Asistan, yüzen mikrofon servisinde
 * ({@code AssistantOverlayService}) ekranı kaplamadan dinlemeye başlar. Servis {@link AssistantBridgeService#PERMISSION}
 * ile korunur (aynı imza).
 */
public final class AssistantLauncher {
    public static final String PACKAGE = "com.mapcontrol.ai";
    public static final String OVERLAY_SERVICE = "com.mapcontrol.ai.overlay.AssistantOverlayService";
    public static final String ACTION_LISTEN = "com.mapcontrol.ai.action.LISTEN";
    private static final String TAG = "AssistantLauncher";

    private AssistantLauncher() {
    }

    /** Asistan kurulu mu (değilse ses tuşuna hiç dokunulmaz, aracın kendi asistanı çalışır). */
    public static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** @return başlatma isteği gönderildi mi */
    public static boolean listen(Context context) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(ACTION_LISTEN).setClassName(PACKAGE, OVERLAY_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent);
            } else {
                app.startService(intent);
            }
            dismissStockAssistant(app);
            return true;
        } catch (RuntimeException e) {
            // SecurityException: imza farklı / izin yok; IllegalStateException: arka plan kısıtı.
            Log.w(TAG, "asistan başlatılamadı: " + e);
            return false;
        }
    }

    /**
     * Aynı tuşa aracın iFlytek asistanı da tepki verir: açtığı diyaloğu kapatır. Yalnızca geçici "EXIT" gönderilir;
     * PR #48'deki {@code stopvr} (aracın asistanını kalıcı susturur) bilerek alınmadı.
     */
    private static void dismissStockAssistant(Context context) {
        String[] targets = {
                "com.iflytek.autofly.sceneengine",
                "com.iflytek.cutefly.speechclient.hmi",
                "com.iflytek.autofly.voicecoreservice",
                "com.iflytek.autofly",
                "com.desaysv.vrservice"
        };
        for (String pkg : targets) {
            try {
                Intent exit = new Intent("com.iflytek.autofly.AUTOFLY_SERVICE");
                exit.setPackage(pkg);
                exit.putExtra("com.iflytek.autofly.speechservice.operation", "EXIT");
                context.startService(exit);
            } catch (Throwable ignored) {
                // Paket yok ya da servis dışa kapalı.
            }
        }
    }
}
