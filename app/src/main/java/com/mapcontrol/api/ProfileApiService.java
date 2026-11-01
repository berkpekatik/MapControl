package com.mapcontrol.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.mapcontrol.R;
import com.mapcontrol.ui.welcome.WelcomeAudioFiles;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * adbauth hesap, üyelik ve hoşgeldin sesi istekleri.
 * API kökü yalnızca {@link #API_ROOT} içindedir.
 */
public class ProfileApiService {

    public static final String API_ROOT = "https://api.vnoisy.dev";

    public static final String VOICE_FEMALE = "female";
    public static final String VOICE_MALE = "male";

    private static final String PREFS_NAME = "MapControlPrefs";
    private static final String KEY_TOKEN = "adbauthToken";
    private static final String KEY_USERNAME = "adbauthUsername";
    private static final String KEY_PREMIUM = "adbauthIsPremium";
    private static final String KEY_WELCOME_AUDIO = "welcomeAudioFilePath";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ApiCallback {
        void onSuccess(String message, JSONObject data);

        void onError(String error);
    }

    public interface AudioCallback {
        void onSuccess(File file);

        void onError(String error);

        default void onDownloading() {
        }
    }

    public ProfileApiService(Context context) {
        this.context = context.getApplicationContext();
    }

    public String getToken() {
        return prefs().getString(KEY_TOKEN, null);
    }

    public String getUsername() {
        return prefs().getString(KEY_USERNAME, "");
    }

    public boolean isPremium() {
        return prefs().getBoolean(KEY_PREMIUM, false);
    }

    public boolean isLoggedIn() {
        String token = getToken();
        return token != null && !token.isEmpty();
    }

    public void logout() {
        prefs().edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USERNAME)
                .remove(KEY_PREMIUM)
                .apply();
    }

    public void login(String username, String password, ApiCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject body = new JSONObject();
                body.put("username", username);
                body.put("password", password);
                conn = open("POST", "/api/auth/login", false);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                writeBody(conn, body);

                int code = conn.getResponseCode();
                JSONObject json = readJson(conn, code);
                boolean success = json.optBoolean("success", false);
                String token = json.optString("token", "");
                if (code >= 200 && code < 300 && success && !token.isEmpty()) {
                    String name = json.optString("username", username);
                    boolean premium = json.optBoolean("isPremium", false);
                    prefs().edit()
                            .putString(KEY_TOKEN, token)
                            .putString(KEY_USERNAME, name)
                            .putBoolean(KEY_PREMIUM, premium)
                            .apply();
                    mainHandler.post(() -> callback.onSuccess(name, json));
                } else {
                    String error = messageOf(json, context.getString(R.string.profile_login_failed));
                    mainHandler.post(() -> callback.onError(error));
                }
            } catch (Exception e) {
                postFailure(callback, e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }, "adbauth-login").start();
    }

    public void refreshMe(ApiCallback callback) {
        if (!isLoggedIn()) {
            mainHandler.post(() -> callback.onError(context.getString(R.string.profile_login_required)));
            return;
        }
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = open("GET", "/api/auth/me", true);
                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    logout();
                    mainHandler.post(() -> callback.onError(context.getString(R.string.profile_login_required)));
                    return;
                }
                JSONObject json = readJson(conn, code);
                if (code >= 200 && code < 300) {
                    String name = json.optString("username", getUsername());
                    boolean premium = json.optBoolean("isPremium", false);
                    prefs().edit()
                            .putString(KEY_USERNAME, name)
                            .putBoolean(KEY_PREMIUM, premium)
                            .apply();
                    mainHandler.post(() -> callback.onSuccess(name, json));
                } else {
                    String error = messageOf(json, context.getString(R.string.profile_login_failed));
                    mainHandler.post(() -> callback.onError(error));
                }
            } catch (Exception e) {
                postFailure(callback, e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }, "adbauth-me").start();
    }

    /**
     * Premium üye için mp3 üretir, {@code filesDir/welcome-audio} altına yazar,
     * önceki hoşgeldin dosyasını siler ve {@code welcomeAudioFilePath} değerini günceller.
     */
    public void generateWelcomeAudio(String text, String voice, AudioCallback callback) {
        if (!isLoggedIn()) {
            mainHandler.post(() -> callback.onError(context.getString(R.string.welcome_tts_need_login)));
            return;
        }
        if (!isPremium()) {
            mainHandler.post(() -> callback.onError(context.getString(R.string.welcome_tts_need_premium)));
            return;
        }
        if (!VOICE_FEMALE.equals(voice) && !VOICE_MALE.equals(voice)) {
            mainHandler.post(() -> callback.onError(context.getString(R.string.welcome_tts_failed)));
            return;
        }
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject body = new JSONObject();
                body.put("text", text);
                body.put("voice", voice);
                conn = open("POST", "/api/tts", true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "audio/mpeg");
                conn.setDoOutput(true);
                writeBody(conn, body);

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    logout();
                    mainHandler.post(() -> callback.onError(context.getString(R.string.welcome_tts_need_login)));
                    return;
                }
                if (code == HttpURLConnection.HTTP_FORBIDDEN) {
                    prefs().edit().putBoolean(KEY_PREMIUM, false).apply();
                    mainHandler.post(() -> callback.onError(context.getString(R.string.welcome_tts_need_premium)));
                    return;
                }
                String type = conn.getContentType() == null ? "" : conn.getContentType().toLowerCase(Locale.US);
                if (code < 200 || code >= 300 || type.contains("json") || type.startsWith("text/")) {
                    JSONObject json = readJson(conn, code);
                    String error = messageOf(json, context.getString(R.string.welcome_tts_failed));
                    mainHandler.post(() -> callback.onError(error));
                    return;
                }
                mainHandler.post(callback::onDownloading);
                File file;
                try (InputStream in = conn.getInputStream()) {
                    file = WelcomeAudioFiles.writeGenerated(context, in);
                }
                mainHandler.post(() -> callback.onSuccess(file));
            } catch (Exception e) {
                postAudioFailure(callback, e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }, "adbauth-tts").start();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private HttpURLConnection open(String method, String path, boolean authorize) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(API_ROOT + path).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(90000);
        if (authorize) {
            conn.setRequestProperty("Authorization", "Bearer " + getToken());
        }
        return conn;
    }

    private static void writeBody(HttpURLConnection conn, JSONObject body) throws Exception {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(bytes);
        }
    }

    private static JSONObject readJson(HttpURLConnection conn, int code) {
        InputStream stream;
        try {
            stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        } catch (Exception e) {
            stream = conn.getErrorStream();
        }
        String text = readText(stream);
        if (text.isEmpty()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(text);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static String readText(InputStream stream) {
        if (stream == null) {
            return "";
        }
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } catch (Exception ignored) {
            return "";
        }
        return response.toString();
    }

    private static String messageOf(JSONObject json, String fallback) {
        String message = json.optString("message", "");
        if (message.isEmpty()) {
            message = json.optString("error", "");
        }
        return message.isEmpty() ? fallback : message;
    }

    private void postFailure(ApiCallback callback, Exception error) {
        String message = error instanceof java.io.IOException
                ? context.getString(R.string.profile_no_internet)
                : context.getString(R.string.profile_login_failed);
        mainHandler.post(() -> callback.onError(message));
    }

    private void postAudioFailure(AudioCallback callback, Exception error) {
        String detail = error.getMessage() == null ? "" : error.getMessage();
        boolean fileError = "empty".equals(detail) || "replace".equals(detail)
                || "rename".equals(detail) || "store".equals(detail);
        String message = !fileError && error instanceof java.io.IOException
                ? context.getString(R.string.profile_no_internet)
                : context.getString(R.string.welcome_tts_failed);
        mainHandler.post(() -> callback.onError(message));
    }
}
