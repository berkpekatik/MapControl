package com.mapcontrol.dvr;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * HAB erişim sondası (Adım 2, faz 1): sadece /dev/hab açma testi.
 * ioctl / kanal açma YOK — qcarcam tarafına hiç dokunulmaz.
 * Açma başarılıysa bu uid (SELinux dahil) native HAB katmanına geçebilir.
 */
public final class HabProbe {
    private static final String TAG = "HabProbe";
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hab-probe");
        t.setDaemon(true);
        return t;
    });

    private HabProbe() {
    }

    /** Arka planda çalışır; sonuç tek satır özet olarak callback'e (ve logcat'e) gider. */
    public static void probeAsync(Consumer<String> callback) {
        EXEC.execute(() -> {
            String result = probe();
            Log.i(TAG, result);
            if (callback != null) {
                callback.accept(result);
            }
        });
    }

    static String probe() {
        StringBuilder sb = new StringBuilder("HAB Probe: ");
        File dev = new File("/dev/hab");
        sb.append("exists=").append(dev.exists())
                .append(" r=").append(dev.canRead())
                .append(" w=").append(dev.canWrite()).append(" | ");

        try {
            FileInputStream in = new FileInputStream(dev);
            in.close();
            sb.append("open_read=OK | ");
        } catch (Exception e) {
            sb.append("open_read=FAIL(").append(shortMsg(e)).append(") | ");
        }

        try {
            FileOutputStream out = new FileOutputStream(dev);
            out.close();
            sb.append("open_write=OK | ");
        } catch (Exception e) {
            sb.append("open_write=FAIL(").append(shortMsg(e)).append(") | ");
        }

        File lib = new File("/vendor/lib64/libuhab.so");
        sb.append("libuhab exists=").append(lib.exists())
                .append(" r=").append(lib.canRead());
        return sb.toString();
    }

    private static String shortMsg(Exception e) {
        String m = e.getMessage();
        return e.getClass().getSimpleName() + (m != null ? ": " + m : "");
    }
}
