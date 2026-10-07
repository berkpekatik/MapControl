package com.mapcontrol.util;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * APK kurulumu: PackageInstaller session.
 * Sonuç, {@link #ACTION_SESSION_RESULT} ile çağıran activity'ye döner.
 */
public final class ApkSessionInstaller {

    public static final String ACTION_SESSION_RESULT =
            "com.mapcontrol.action.PACKAGE_INSTALL_SESSION_RESULT";

    private static final int REQUEST_CODE = 0x4D43;
    private static final int COPY_BUFFER = 16384;

    public interface Listener {
        void onInstallStatus(int status, @Nullable String message);
    }

    private static final class QueuedStatus {
        final int status;
        final String message;

        QueuedStatus(int status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    @Nullable
    private static Listener listener;
    @Nullable
    private static QueuedStatus queued;

    private ApkSessionInstaller() {
    }

    public static void setListener(@Nullable Listener installListener) {
        listener = installListener;
        if (installListener != null && queued != null) {
            QueuedStatus pending = queued;
            queued = null;
            installListener.onInstallStatus(pending.status, pending.message);
        }
    }

    /** Yalnızca hâlâ kayıtlı olan dinleyiciyi kaldırır (yeniden oluşturulan activity'yi silmez). */
    public static void clearListener(@Nullable Listener installListener) {
        if (listener == installListener) {
            listener = null;
        }
    }

    /**
     * Session açar, APK'yı yazar ve commit eder. Çağıran thread'i bloklar; UI thread'den çağırma.
     */
    public static void install(@NonNull Activity activity, @NonNull File apkFile,
                                @Nullable Listener installListener) throws Exception {
        if (installListener != null) {
            listener = installListener;
        }
        if (!apkFile.isFile()) {
            throw new IllegalArgumentException("APK dosyası yok: " + apkFile.getAbsolutePath());
        }

        PackageInstaller packageInstaller = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.setInstallReason(PackageManager.INSTALL_REASON_DEVICE_SETUP);
        }

        int sessionId = packageInstaller.createSession(params);
        PackageInstaller.Session session = packageInstaller.openSession(sessionId);
        try {
            writeApk(apkFile, session);

            Intent callback = new Intent(activity, activity.getClass());
            callback.setAction(ACTION_SESSION_RESULT);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent pending = PendingIntent.getActivity(activity, REQUEST_CODE, callback, flags);
            session.commit(pending.getIntentSender());
        } catch (Exception e) {
            try {
                session.abandon();
            } catch (Exception ignored) {
            }
            if (e instanceof java.io.IOException) {
                throw new RuntimeException("IO exception", e);
            }
            throw e;
        } finally {
            try {
                session.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * @return true if this intent was the session result and was consumed
     */
    public static boolean handleSessionResultIntent(@NonNull Activity activity, @Nullable Intent intent) {
        if (intent == null || !ACTION_SESSION_RESULT.equals(intent.getAction())) {
            return false;
        }
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return true;
        }

        int status = extras.getInt(PackageInstaller.EXTRA_STATUS);
        String message = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = readConfirmIntent(extras);
            if (confirm != null) {
                confirm.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                activity.startActivity(confirm);
            }
        }
        dispatch(status, message);
        return true;
    }

    private static void writeApk(File apkFile, PackageInstaller.Session session) throws Exception {
        OutputStream outputStream = session.openWrite("package", 0L, -1L);
        try {
            InputStream inputStream = new FileInputStream(apkFile);
            try {
                byte[] buffer = new byte[COPY_BUFFER];
                int read;
                while ((read = inputStream.read(buffer)) >= 0) {
                    outputStream.write(buffer, 0, read);
                }
                session.fsync(outputStream);
            } finally {
                inputStream.close();
            }
        } finally {
            outputStream.close();
        }
    }

    @SuppressWarnings("deprecation")
    @Nullable
    private static Intent readConfirmIntent(Bundle extras) {
        if (Build.VERSION.SDK_INT >= 33) {
            return extras.getParcelable(Intent.EXTRA_INTENT, Intent.class);
        }
        return extras.getParcelable(Intent.EXTRA_INTENT);
    }

    private static void dispatch(int status, @Nullable String message) {
        Listener current = listener;
        if (current == null) {
            queued = new QueuedStatus(status, message);
            return;
        }
        current.onInstallStatus(status, message);
    }
}
