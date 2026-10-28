package com.mapcontrol.vehicle;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.Settings;
import android.util.Log;

import com.mapcontrol.R;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tam açılışta iFlytek uyandırmasını kısa süre kapatır.
 * Tanıma hazır olunca kullanıcının eski değerini geri yazar.
 */
public final class SpeechWakeHold {
    private static final String TAG = "SpeechWakeHold";
    private static final String PREFS = "MapControlPrefs";
    private static final String KEY_HELD = "speechWakeHoldActive";
    private static final String KEY_PREVIOUS = "speechWakeHoldPrevious";
    private static final String SPEECH_WAKE = "com.iflytek.autofly.IFLY_SPEECH_WAKE";
    private static final long AFTER_NETWORK_MS = TimeUnit.SECONDS.toMillis(25);
    private static final long ABSOLUTE_MS = TimeUnit.MINUTES.toMillis(3);

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "speech-wake-hold");
        thread.setDaemon(true);
        return thread;
    });

    public interface StatusListener {
        void onStatus(String message);
    }

    private SpeechWakeHold() {
    }

    public static void onServiceStart(Context context, StatusListener listener) {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        Context app = context.getApplicationContext();
        EXEC.execute(() -> holdUntilReady(app, listener));
    }

    private static void holdUntilReady(Context app, StatusListener listener) {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean alreadyHeld = prefs.getBoolean(KEY_HELD, false);
        if (!alreadyHeld) {
            Probe probe = recentLog();
            if (probe == Probe.UNAVAILABLE) {
                report(app, listener, R.string.speech_wake_hold_log_unavailable);
                return;
            }
            if (probe == Probe.READY) {
                report(app, listener, R.string.speech_wake_hold_already_ready);
                return;
            }
            int current = Settings.System.getInt(app.getContentResolver(), SPEECH_WAKE, 1);
            if (current != 1) {
                report(app, listener, R.string.speech_wake_hold_already_off);
                return;
            }
            try {
                if (!Settings.System.putInt(app.getContentResolver(), SPEECH_WAKE, 0)) {
                    report(app, listener, R.string.speech_wake_hold_write_failed);
                    return;
                }
            } catch (SecurityException e) {
                report(app, listener, R.string.speech_wake_hold_write_failed);
                return;
            }
            prefs.edit().putBoolean(KEY_HELD, true).putInt(KEY_PREVIOUS, current).apply();
            report(app, listener, R.string.speech_wake_hold_started);
        }

        long startedAt = System.currentTimeMillis();
        long networkReadyAt = 0L;
        boolean loggedNetwork = false;
        boolean loggedUnavailable = false;
        while (prefs.getBoolean(KEY_HELD, false)) {
            Probe probe = recentLog();
            if (probe == Probe.READY) {
                restore(app, prefs, listener);
                return;
            }
            if (probe == Probe.UNAVAILABLE && !loggedUnavailable) {
                loggedUnavailable = true;
                report(app, listener, R.string.speech_wake_hold_log_lost);
            }
            long now = System.currentTimeMillis();
            if (networkReadyAt == 0L && isNetworkValidated(app)) {
                networkReadyAt = now;
            }
            if (networkReadyAt != 0L && !loggedNetwork) {
                loggedNetwork = true;
                report(app, listener, R.string.speech_wake_hold_network);
            }
            boolean networkWindowElapsed = networkReadyAt != 0L
                    && now - networkReadyAt >= AFTER_NETWORK_MS;
            if (networkWindowElapsed || now - startedAt >= ABSOLUTE_MS) {
                report(app, listener, R.string.speech_wake_hold_left_off);
                return;
            }
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void restore(Context app, SharedPreferences prefs, StatusListener listener) {
        int previous = prefs.getInt(KEY_PREVIOUS, 1);
        try {
            Settings.System.putInt(app.getContentResolver(), SPEECH_WAKE, previous);
        } catch (SecurityException e) {
            report(app, listener, R.string.speech_wake_hold_write_failed);
            return;
        }
        prefs.edit().remove(KEY_HELD).remove(KEY_PREVIOUS).apply();
        report(app, listener, R.string.speech_wake_hold_restored);
    }

    private static void report(Context app, StatusListener listener, int resId) {
        String message = app.getString(resId);
        Log.i(TAG, message);
        if (listener != null) {
            listener.onStatus(message);
        }
    }

    private static boolean isNetworkValidated(Context app) {
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private enum Probe {
        READY,
        NOT_READY,
        UNAVAILABLE
    }

    private static Probe recentLog() {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "logcat", "-d", "-t", "400", "-s", "SrManager:I", "SrManager:D")
                    .redirectErrorStream(true)
                    .start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String line;
            boolean ready = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains("SR_INIT_OK") || line.contains("本地识别初始化完成")) {
                    ready = true;
                }
            }
            int code = process.waitFor();
            if (code != 0) {
                return Probe.UNAVAILABLE;
            }
            return ready ? Probe.READY : Probe.NOT_READY;
        } catch (Exception e) {
            Log.w(TAG, "logcat read failed: " + e.getMessage());
            return Probe.UNAVAILABLE;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
