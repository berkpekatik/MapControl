package com.mapcontrol.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

/**
 * Araç / yüksek ayrıcalıklı cihazlarda shell benzeri ortamda {@code am force-stop} ile zorunlu durdurma.
 * Normal mağaza uygulamalarında çoğunlukla başarısız olur; o zaman kullanıcıya açıklanır.
 */
public final class AppForceStopHelper {

    private static final int DRAIN_MAX = 8192;

    private AppForceStopHelper() {
    }

    public static boolean mayForceStop(@NonNull Context context, @NonNull String packageName) {
        if (packageName.isEmpty()) {
            return false;
        }
        if (packageName.equals(context.getPackageName())) {
            return false;
        }
        return !isSystemOrPlatformPackage(context, packageName);
    }

    private static boolean isSystemOrPlatformPackage(Context context, String packageName) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    || (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                return true;
            }
            String sd = info.sourceDir;
            if (sd != null) {
                String s = sd.toLowerCase(Locale.US);
                if (s.contains("/system/priv-app/") || s.contains("/system/app/")) {
                    return true;
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            return true;
        }
        return false;
    }

    /**
     * @return true yalnızca süreç çıkış kodu 0 ise; stream’ler tüketilir.
     */
    public static boolean amForceStop(@NonNull String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        try {
            Process p = new ProcessBuilder("am", "force-stop", packageName).start();
            drainStream(p.getInputStream());
            drainStream(p.getErrorStream());
            int code = p.waitFor();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void drainStream(InputStream is) {
        if (is == null) {
            return;
        }
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream(256);
            byte[] b = new byte[512];
            int n;
            int total = 0;
            while ((n = is.read(b)) > 0 && total < DRAIN_MAX) {
                int take = Math.min(n, DRAIN_MAX - total);
                buf.write(b, 0, take);
                total += take;
            }
            is.close();
        } catch (Exception e) {
            try {
                is.close();
            } catch (Exception ignored) {
            }
        }
    }
}
