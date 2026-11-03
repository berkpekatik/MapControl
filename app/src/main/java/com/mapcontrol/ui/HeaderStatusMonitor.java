package com.mapcontrol.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import com.mapcontrol.ui.builder.TopBarBuilder;
import com.mapcontrol.util.ClusterNavigationState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Updates UI chips while visible; mode uses its own read-only OEM query. */
public final class HeaderStatusMonitor {
    private static final String MODE_KEY = "driveModeSetting";
    private final Context context;
    private final SharedPreferences preferences;
    private final List<HeaderStatusSink> sinks = new CopyOnWriteArrayList<>();
    private final ModeSource modeSource;
    private final BooleanSupplier serverRunning;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean started;
    private final Consumer<ModeReading> modeListener = this::bindMode;
    // Run after the current UI event (and its existing vehicle command) has completed.
    private final Runnable refreshModeTask;
    private final Runnable selectionChanged = () -> { if (started) refresh(); };
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (prefs, key) -> {
        if (started && (key == null || MODE_KEY.equals(key))) {
            handler.removeCallbacks(selectionChanged);
            handler.post(selectionChanged);
        }
    };

    static final class ModeReading {
        final boolean connected;
        final int driveMode;
        ModeReading(boolean connected, int driveMode) {
            this.connected = connected;
            this.driveMode = driveMode;
        }
    }

    interface ModeSource {
        ModeReading currentReading();
        void addListener(Consumer<ModeReading> listener);
        void removeListener(Consumer<ModeReading> listener);
        void refresh();
    }

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (!started) return;
            refresh();
            handler.postDelayed(this, 2000);
        }
    };

    public HeaderStatusMonitor(Context context, HeaderStatusSink initialSink, BooleanSupplier serverRunning) {
        this(context, initialSink, new WidgetDriveModeSource(context), serverRunning);
    }

    HeaderStatusMonitor(Context context, HeaderStatusSink initialSink, ModeSource modeSource,
            BooleanSupplier serverRunning) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
        if (initialSink != null) {
            this.sinks.add(initialSink);
        }
        this.modeSource = modeSource;
        this.serverRunning = serverRunning;
        refreshModeTask = () -> {
            if (started && !preferences.contains(MODE_KEY)) modeSource.refresh();
        };
    }

    public void addSink(HeaderStatusSink sink) {
        if (sink != null && !sinks.contains(sink)) {
            sinks.add(sink);
            sink.setWifiState(readWifiState());
            sink.setServerRunning(serverRunning.getAsBoolean());
            sink.setProjectionActive(ClusterNavigationState.getLastKnownOpen());
            ModeReading reading = modeSource.currentReading();
            displayMode(sink, reading);
            sink.setSoundBoost(readBoostActive(),
                    readGainMb());
        }
    }

    public void removeSink(HeaderStatusSink sink) {
        if (sink != null) {
            sinks.remove(sink);
        }
    }

    public void start() {
        if (started) return;
        started = true;
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        modeSource.addListener(modeListener);
        handler.post(refreshTask);
    }

    public void stop() {
        if (!started) return;
        started = false;
        handler.removeCallbacks(refreshTask);
        handler.removeCallbacks(refreshModeTask);
        handler.removeCallbacks(selectionChanged);
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        modeSource.removeListener(modeListener);
    }

    public void refresh() {
        TopBarBuilder.WifiState wifiState = readWifiState();
        boolean running = serverRunning.getAsBoolean();
        boolean projectionActive = ClusterNavigationState.getLastKnownOpen();
        ModeReading reading = modeSource.currentReading();
        boolean boostActive = readBoostActive();
        int gainMb = readGainMb();

        for (HeaderStatusSink sink : sinks) {
            sink.setWifiState(wifiState);
            sink.setServerRunning(running);
            sink.setProjectionActive(projectionActive);
            displayMode(sink, reading);
            sink.setSoundBoost(boostActive, gainMb);
        }
        if (started) {
            // Coalesce requests in the same UI event and let selection handlers finish first.
            handler.removeCallbacks(refreshModeTask);
            handler.post(refreshModeTask);
        }
    }

    private void bindMode(ModeReading reading) {
        if (started && reading != null) {
            for (HeaderStatusSink sink : sinks) displayMode(sink, reading);
        }
    }

    private void displayMode(HeaderStatusSink sink, ModeReading reading) {
        if (preferences.contains(MODE_KEY)) {
            sink.setSelectedDriveMode(preferences.getInt(MODE_KEY, -1));
        } else {
            sink.setDriveMode(reading != null && reading.connected,
                    reading != null ? reading.driveMode : Integer.MIN_VALUE);
        }
    }

    private boolean readBoostActive() {
        return context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE)
                .getBoolean("equalizerEnabled", false) && readGainMb() > 0;
    }

    private int readGainMb() {
        return context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE)
                .getInt("equalizerGainMb", 0);
    }

    private TopBarBuilder.WifiState readWifiState() {
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) return TopBarBuilder.WifiState.UNKNOWN;
            if (!wifi.isWifiEnabled()) return TopBarBuilder.WifiState.OFF;
            ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity == null) return TopBarBuilder.WifiState.UNKNOWN;
            for (Network network : connectivity.getAllNetworks()) {
                NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return TopBarBuilder.WifiState.CONNECTED;
                }
            }
            return TopBarBuilder.WifiState.DISCONNECTED;
        } catch (RuntimeException unavailable) {
            return TopBarBuilder.WifiState.UNKNOWN;
        }
    }
}
