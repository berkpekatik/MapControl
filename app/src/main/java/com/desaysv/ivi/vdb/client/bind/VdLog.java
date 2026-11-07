package com.desaysv.ivi.vdb.client.bind;

import android.util.Log;

/**
 * VDBus istemcisinin çağrı/olay başına debug satırları (get, getConnector, dispatchEvent…). Veri tikinde saniyede
 * onlarca satır üretiyorlardı; artık varsayılan kapalı. Açmak için (yalnız debug derleme, süreç yeniden başlatılınca):
 * {@code adb shell setprop log.tag.VDBusTrace DEBUG}
 */
final class VdLog {
    static final boolean ON = com.mapcontrol.BuildConfig.DEBUG && Log.isLoggable("VDBusTrace", Log.DEBUG);

    private VdLog() {
    }

    static void d(String tag, String msg) {
        if (ON) Log.d(tag, msg);
    }
}
