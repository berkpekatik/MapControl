package com.mapcontrol.util;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.mapcontrol.BuildConfig;
import com.mapcontrol.GlitchTip;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Açılışta sürecin önceki ölümünü kaydeder (`adb logcat -s ExitReason`); beklenmeyenleri GlitchTip'e de gönderir.
 * Android 11+ sistemin verdiği nedeni okur. Android 10'da neden okunamaz, yalnızca aynı araç açılışında
 * sürecin yeniden başladığı görülür.
 */
public final class ProcessExitLog {

    private static final String TAG = "ExitReason";
    private static final String PREFS = "process_exit_log";
    private static final String KEY_LAST_EXIT_MS = "lastExitMs";
    private static final String KEY_BOOT = "bootId";
    private static final String KEY_VERSION = "versionCode";
    private static final int MAX_EXITS = 8;

    private ProcessExitLog() {
    }

    public static void reportAsync(Context context) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                report(app);
            } catch (Throwable t) {
                Log.w(TAG, "okunamadi: " + t.getClass().getSimpleName());
            }
        }, "exit-reasons").start();
    }

    private static void report(Context app) {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String bootId = BootSession.currentId(app);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            reportExitReasons(app, prefs);
        } else if (isUnexplainedRestart(prefs.getString(KEY_BOOT, null), bootId,
                prefs.getInt(KEY_VERSION, -1), BuildConfig.VERSION_CODE)) {
            Log.w(TAG, "ayni arac acilisinda surec yeniden basladi (neden Android 10'da okunamiyor)");
            GlitchTip.reportWarning("process_exit:UNEXPLAINED_RESTART", Collections.emptyMap());
        }
        prefs.edit().putString(KEY_BOOT, bootId).putInt(KEY_VERSION, BuildConfig.VERSION_CODE).apply();
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static void reportExitReasons(Context app, SharedPreferences prefs) {
        ActivityManager am = app.getSystemService(ActivityManager.class);
        List<ApplicationExitInfo> exits = am.getHistoricalProcessExitReasons(app.getPackageName(), 0, MAX_EXITS);
        long lastSeen = prefs.getLong(KEY_LAST_EXIT_MS, 0L);
        long newest = lastSeen;
        for (ApplicationExitInfo exit : exits) {
            if (exit.getTimestamp() <= lastSeen) {
                continue;
            }
            newest = Math.max(newest, exit.getTimestamp());
            String reason = reasonName(exit.getReason());
            Log.w(TAG, reason + " at=" + exit.getTimestamp() + " importance=" + exit.getImportance()
                    + " status=" + exit.getStatus() + " desc=" + exit.getDescription());
            if (shouldReport(exit.getReason())) {
                Map<String, String> extras = new LinkedHashMap<>();
                extras.put("timestamp", String.valueOf(exit.getTimestamp()));
                extras.put("importance", String.valueOf(exit.getImportance()));
                extras.put("status", String.valueOf(exit.getStatus()));
                extras.put("pssKb", String.valueOf(exit.getPss()));
                extras.put("description", String.valueOf(exit.getDescription()));
                GlitchTip.reportWarning("process_exit:" + reason, extras);
            }
        }
        if (newest != lastSeen) {
            prefs.edit().putLong(KEY_LAST_EXIT_MS, newest).apply();
        }
    }

    /** Android 10: aynı açılış ve aynı sürümde yeni süreç, önceki sürecin beklenmedik öldüğünü gösterir. */
    static boolean isUnexplainedRestart(String previousBoot, String currentBoot, int previousVersion, int currentVersion) {
        return previousBoot != null && previousBoot.equals(currentBoot) && previousVersion == currentVersion;
    }

    /** Kullanıcı/kurulum kaynaklı çıkışlar gönderilmez; Java çökmesini Sentry zaten kendisi raporlar. */
    static boolean shouldReport(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_EXIT_SELF:
            case ApplicationExitInfo.REASON_CRASH:
            case ApplicationExitInfo.REASON_USER_REQUESTED:
            case ApplicationExitInfo.REASON_USER_STOPPED:
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE:
            case ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE:
            case ApplicationExitInfo.REASON_PACKAGE_UPDATED:
                return false;
            default:
                return true;
        }
    }

    static String reasonName(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_EXIT_SELF:
                return "EXIT_SELF";
            case ApplicationExitInfo.REASON_SIGNALED:
                return "SIGNALED";
            case ApplicationExitInfo.REASON_LOW_MEMORY:
                return "LOW_MEMORY";
            case ApplicationExitInfo.REASON_CRASH:
                return "CRASH";
            case ApplicationExitInfo.REASON_CRASH_NATIVE:
                return "CRASH_NATIVE";
            case ApplicationExitInfo.REASON_ANR:
                return "ANR";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
                return "INITIALIZATION_FAILURE";
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE:
                return "PERMISSION_CHANGE";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                return "EXCESSIVE_RESOURCE_USAGE";
            case ApplicationExitInfo.REASON_USER_REQUESTED:
                return "USER_REQUESTED";
            case ApplicationExitInfo.REASON_USER_STOPPED:
                return "USER_STOPPED";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED:
                return "DEPENDENCY_DIED";
            case ApplicationExitInfo.REASON_OTHER:
                return "OTHER";
            case ApplicationExitInfo.REASON_FREEZER:
                return "FREEZER";
            case ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE:
                return "PACKAGE_STATE_CHANGE";
            case ApplicationExitInfo.REASON_PACKAGE_UPDATED:
                return "PACKAGE_UPDATED";
            default:
                return "UNKNOWN_" + reason;
        }
    }
}
