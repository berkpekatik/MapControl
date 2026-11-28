package com.mapcontrol.admin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.mapcontrol.R;
import com.mapcontrol.ui.welcome.WelcomeAudioFiles;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Servis modundayken USB takılınca kilit dosyasını arar ({@link ServiceMode#tryUnlockFromUsb}). Manifest'te kayıtlıdır:
 * {@code MEDIA_MOUNTED} örtük yayın kısıtından muaftır ve açılışta takılı USB için de gelir. Mod kapalıysa hiçbir şey yapmaz.
 */
public class ServiceModeUsbReceiver extends BroadcastReceiver {
    /** Bağlama yayını geldiğinde birim henüz okunamıyor olabilir; birkaç kez tekrar denenir. */
    private static final long[] RETRY_DELAYS_MS = {0, 2000, 3000};

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MEDIA_MOUNTED.equals(intent.getAction())) return;
        Context app = context.getApplicationContext();
        if (!ServiceMode.isActive(app)) return;
        Uri data = intent.getData();
        String mountPath = data != null ? data.getPath() : null;
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                for (long delay : RETRY_DELAYS_MS) {
                    if (delay > 0) Thread.sleep(delay);
                    if (ServiceMode.tryUnlockFromUsb(app, roots(app, mountPath))) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            ServiceMode.resumeAfterUnlock(app);
                            showUnlockedNotice(app);
                        });
                        return;
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                com.mapcontrol.util.DiagJournal.event("servis", "USB kontrolu hata: " + t);
            } finally {
                pending.finish();
            }
        }, "ServiceModeUsb").start();
    }

    /**
     * Kilit açıldı bildirimi. Aracın launcher'ı ({@code com.desaysv.launcher}) yalnızca paket ekleme/kaldırma/güncelleme
     * yayınlarını dinliyor; MapControl ikonunu bileşen açarak geri getirdiğimiz için ikon araç yeniden başlayana kadar
     * görünmeyebilir. Bu yüzden MapControl ekranı açılır ve kullanıcıdan aracı yeniden başlatması istenir.
     */
    static void showUnlockedNotice(Context app) {
        openApp(app);
        boolean shown = com.mapcontrol.util.DialogHelper.showOverlayInfo(app,
                app.getString(R.string.service_mode_restart_title),
                app.getString(R.string.service_mode_restart_message));
        if (!shown) Toast.makeText(app, R.string.service_mode_unlocked_toast, Toast.LENGTH_LONG).show();
    }

    /**
     * İkon launcher'da hemen görünmediği için MapControl ekranı doğrudan açılır. Arka plandan etkinlik başlatma
     * kısıtından "üstte gösterme" izni sayesinde muafız; açılamazsa bildirim penceresi yine çıkar.
     */
    private static void openApp(Context app) {
        try {
            Intent launch = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
            if (launch == null) return;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            app.startActivity(launch);
            com.mapcontrol.util.DiagJournal.event("servis", "kilit acildi, uygulama ekrani acildi");
        } catch (RuntimeException e) {
            com.mapcontrol.util.DiagJournal.event("servis", "uygulama ekrani acilamadi: " + e);
        }
    }

    /** Herhangi bir anda takılı USB'leri kontrol eder (servis açılışı). Arka plan iş parçacığında çağrılmalı. */
    public static boolean checkNow(Context context) {
        Context app = context.getApplicationContext();
        return ServiceMode.isActive(app) && ServiceMode.tryUnlockFromUsb(app, roots(app, null));
    }

    private static List<File> roots(Context app, String mountPath) {
        List<File> roots = new ArrayList<>();
        if (mountPath != null) roots.add(new File(mountPath));
        for (File f : WelcomeAudioFiles.usbRoots(app)) {
            if (mountPath == null || !f.getAbsolutePath().equals(mountPath)) roots.add(f);
        }
        return roots;
    }
}
