package com.mapcontrol.weather;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Open-Meteo hava durumu istemcisi — arka planda çeker, kısa süre önbelleğe alır.
 */
public final class OpenMeteoWeatherClient {

    private static final String TAG = "OpenMeteoWeather";
    private static final String PREFS_NAME = "MapControlPrefs";
    private static final String KEY_CACHED_JSON = "open_meteo_weather_cache";
    private static final String KEY_CACHED_AT = "open_meteo_weather_cached_at";

    private static final String API_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current_weather=true";

    /** Varsayılan: Sakarya bölgesi (örnek koordinatlar). */
    private static final double DEFAULT_LATITUDE = 40.77;
    private static final double DEFAULT_LONGITUDE = 30.39;

    private static final long CACHE_TTL_MS = 15L * 60L * 1000L;
    private static final long REFRESH_INTERVAL_MS = 30L * 60L * 1000L;

    public interface Listener {
        void onWeatherUpdated(@NonNull OpenMeteoWeatherSnapshot snapshot);

        void onWeatherFailed();
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Runnable refreshRunnable = this::fetchIfNeeded;

    @Nullable
    private Listener listener;
    private boolean active;
    @Nullable
    private OpenMeteoWeatherSnapshot lastSnapshot;

    public OpenMeteoWeatherClient(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        lastSnapshot = readCache();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
        if (listener != null && lastSnapshot != null) {
            listener.onWeatherUpdated(lastSnapshot);
        }
    }

    public void start() {
        active = true;
        mainHandler.removeCallbacks(refreshRunnable);
        fetchIfNeeded();
        mainHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    public void stop() {
        active = false;
        mainHandler.removeCallbacks(refreshRunnable);
    }

    public void destroy() {
        stop();
        executor.shutdownNow();
    }

    @Nullable
    public OpenMeteoWeatherSnapshot getLastSnapshot() {
        return lastSnapshot;
    }

    private void fetchIfNeeded() {
        if (!active) {
            return;
        }
        long cachedAt = prefs().getLong(KEY_CACHED_AT, 0L);
        if (lastSnapshot != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
            dispatchSuccess(lastSnapshot);
            scheduleNextRefresh();
            return;
        }
        executor.execute(this::fetchFromNetwork);
    }

    private void fetchFromNetwork() {
        HttpURLConnection connection = null;
        try {
            String urlText = String.format(
                    Locale.US,
                    API_URL,
                    DEFAULT_LATITUDE,
                    DEFAULT_LONGITUDE);
            connection = (HttpURLConnection) new URL(urlText).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestMethod("GET");

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + code);
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }

            OpenMeteoWeatherSnapshot snapshot = parse(body.toString());
            if (snapshot == null) {
                throw new IllegalStateException("Parse failed");
            }
            lastSnapshot = snapshot;
            prefs().edit()
                    .putString(KEY_CACHED_JSON, body.toString())
                    .putLong(KEY_CACHED_AT, System.currentTimeMillis())
                    .apply();
            dispatchSuccess(snapshot);
        } catch (Exception error) {
            Log.w(TAG, "Hava durumu alınamadı", error);
            if (lastSnapshot == null) {
                lastSnapshot = readCache();
            }
            if (lastSnapshot != null) {
                dispatchSuccess(lastSnapshot);
            } else {
                dispatchFailure();
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            scheduleNextRefresh();
        }
    }

    private void scheduleNextRefresh() {
        if (!active) {
            return;
        }
        mainHandler.removeCallbacks(refreshRunnable);
        mainHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Nullable
    private OpenMeteoWeatherSnapshot parse(@NonNull String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject current = root.optJSONObject("current_weather");
            if (current == null) {
                return null;
            }
            return new OpenMeteoWeatherSnapshot(
                    (float) current.optDouble("temperature", Double.NaN),
                    (float) current.optDouble("windspeed", 0d),
                    current.optInt("winddirection", 0),
                    current.optInt("weathercode", -1),
                    current.optInt("is_day", 1) == 1);
        } catch (Exception error) {
            Log.w(TAG, "JSON parse hatası", error);
            return null;
        }
    }

    @Nullable
    private OpenMeteoWeatherSnapshot readCache() {
        String raw = prefs().getString(KEY_CACHED_JSON, null);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return parse(raw);
    }

    private void dispatchSuccess(@NonNull OpenMeteoWeatherSnapshot snapshot) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onWeatherUpdated(snapshot);
            }
        });
    }

    private void dispatchFailure() {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onWeatherFailed();
            }
        });
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
