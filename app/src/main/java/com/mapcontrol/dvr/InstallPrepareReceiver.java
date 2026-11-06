package com.mapcontrol.dvr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * adb ile kurulum/kaldirma oncesi kamerayi kapatir (install.sh, web yukleyici). Manifest'te
 * android.permission.DUMP ile korunur: adb shell gonderebilir, normal uygulamalar gonderemez.
 *
 * "am broadcast" bitene kadar bekler ve sonucu "result=N" olarak yazar. N=0 alinirsa yuklu surum
 * bu alici yoktur (veya uygulama durdurulmustur).
 */
public final class InstallPrepareReceiver extends BroadcastReceiver {
    private static final String TAG = "InstallPrepare";

    public static final String ACTION = "com.mapcontrol.action.PREPARE_SELF_INSTALL";
    private static final int RESULT_STOPPED = 1;
    private static final int RESULT_TIMEOUT = 2;
    private static final long AUTO_RESUME_MS = 60_000;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) {
            return;
        }
        final Context app = context.getApplicationContext();
        final PendingResult result = goAsync();
        new Thread(() -> {
            int code = RESULT_TIMEOUT;
            try {
                if (DvrController.get(app).prepareForSelfInstall(AUTO_RESUME_MS)) {
                    code = RESULT_STOPPED;
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "kurulum hazirligi basarisiz", e);
            } finally {
                result.setResultCode(code);
                result.finish();
            }
        }, "install-prepare").start();
    }
}
