package com.mapcontrol.admin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Servis modu son çare kurtarma: USB kilit dosyası çalışmazsa adb ile kapatır. Cihaz sahibi uygulamada
 * {@code pm unhide} (MANAGE_USERS yok) ve {@code pm enable} (korunan paket) shell'den çalışmaz; bu alıcı olmadan tek
 * çıkış fabrika ayarları olurdu. Manifest'te android.permission.DUMP ile korunur: yalnızca adb shell gönderebilir.
 * <pre>
 * adb shell am broadcast -f 0x20 -n com.mapcontrol/.admin.ServiceModeOffReceiver -a com.mapcontrol.action.SERVICE_MODE_OFF
 * </pre>
 * Sonuç "result=N": 1 kapatıldı, 2 zaten kapalıydı, 3 hata. 0 gelirse yüklü sürümde bu alıcı yoktur.
 */
public final class ServiceModeOffReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.mapcontrol.action.SERVICE_MODE_OFF";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        if (!ServiceMode.isActive(context)) {
            setResultCode(2);
            return;
        }
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                ServiceMode.deactivate(context);
                com.mapcontrol.util.DiagJournal.event("servis", "servis modu adb ile kapatildi");
                pending.setResultCode(1);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    ServiceMode.resumeAfterUnlock(context);
                    ServiceModeUsbReceiver.showUnlockedNotice(context.getApplicationContext());
                });
            } catch (RuntimeException e) {
                pending.setResultCode(3);
            } finally {
                pending.finish();
            }
        }, "ServiceModeOff").start();
    }
}
