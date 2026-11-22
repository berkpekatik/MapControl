package com.mapcontrol.wallpaper;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mapcontrol.BuildConfig;
import com.mapcontrol.api.CloudApi;
import com.mapcontrol.api.CloudToken;
import com.mapcontrol.trip.TripRecorder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Üyenin hesabındaki duvar kağıtları (adbauth.web): araç token'ıyla yalnızca okunur
 * ({@code GET /api/cars/{vin}/wallpapers}, {@code GET /api/cars/{vin}/wallpapers/{id}/data}). Yükleme ve silme
 * web'den ya da mobil uygulamadan yapılır. Arka plan iş parçacığında çağrılır; hata fırlatmaz.
 */
public final class CloudWallpapers {

    /** Sunucu görsel başına 5 MB tutar; yanıtta küçük pay bırakılır. */
    static final int MAX_DOWNLOAD = 5 * 1024 * 1024 + 1024;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    /** Araçtaki kopyanın öneki ({@code cloud_<id>_<ad>.jpg|png}); aynı görsel ikinci kez indirilmez. */
    public static final String FILE_PREFIX = "cloud_";

    public enum Status { OK, NO_ACCOUNT, OFFLINE, ERROR }

    public static final class Item {
        public final int id;
        @NonNull
        public final String name;
        public final int bytes;
        public final int width;
        public final int height;

        Item(int id, @NonNull String name, int bytes, int width, int height) {
            this.id = id;
            this.name = name;
            this.bytes = bytes;
            this.width = width;
            this.height = height;
        }

        @NonNull
        public String formatSize() {
            if (bytes >= 1024 * 1024) {
                return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
            }
            return Math.max(1, bytes / 1024) + " KB";
        }

        /**
         * SVWallpaper'daki kopyanın adı: {@code cloud_<id>_<ad>} (klasörde tanınsın diye ad da eklenir). İndirilen dosya
         * klasörde kalır; hesaptan silinse bile araçtaki kopya durur ve yerel galeride görünür.
         */
        @NonNull
        public String localBaseName() {
            StringBuilder safe = new StringBuilder();
            for (int i = 0; i < name.length() && safe.length() < 40; i++) {
                char c = name.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                    safe.append(c);
                } else if (c == ' ' && safe.length() > 0 && safe.charAt(safe.length() - 1) != '_') {
                    safe.append('_');
                }
            }
            return localPrefix() + safe;
        }

        /** Bu görselin araçtaki kopyasını adından bağımsız bulmak için önek: {@code cloud_<id>_}. */
        @NonNull
        public String localPrefix() {
            return FILE_PREFIX + id + "_";
        }
    }

    public static final class ListResult {
        @NonNull
        public final Status status;
        @NonNull
        public final List<Item> items;
        public final int maxCount;

        ListResult(@NonNull Status status, @NonNull List<Item> items, int maxCount) {
            this.status = status;
            this.items = items;
            this.maxCount = maxCount;
        }
    }

    private CloudWallpapers() {
    }

    /** Hesap bağlantısı var mı (şase okunmuş ve telefon araç token'ını vermiş). */
    public static boolean ready(@NonNull Context context) {
        String vin = TripRecorder.get(context).vinOrEmpty();
        return !vin.isEmpty() && CloudToken.bearer(context, vin) != null;
    }

    @NonNull
    public static ListResult list(@NonNull Context context) {
        String vin = TripRecorder.get(context).vinOrEmpty();
        String bearer = vin.isEmpty() ? null : CloudToken.bearer(context, vin);
        if (bearer == null) {
            return new ListResult(Status.NO_ACCOUNT, Collections.emptyList(), 0);
        }
        HttpURLConnection conn = null;
        try {
            conn = open(vin, "", bearer);
            int code = conn.getResponseCode();
            if (code == 401) {
                CloudToken.clear(context); // telefon yenisini verene kadar denenmez
                return new ListResult(Status.NO_ACCOUNT, Collections.emptyList(), 0);
            }
            if (code / 100 != 2) {
                return new ListResult(Status.ERROR, Collections.emptyList(), 0);
            }
            JSONObject root = new JSONObject(new String(read(conn.getInputStream(), 256 * 1024), StandardCharsets.UTF_8));
            JSONArray array = root.optJSONArray("items");
            List<Item> items = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.optJSONObject(i);
                    if (o == null || o.optInt("id", 0) <= 0) {
                        continue;
                    }
                    items.add(new Item(o.optInt("id"), o.optString("name", ""), o.optInt("bytes"),
                            o.optInt("width"), o.optInt("height")));
                }
            }
            return new ListResult(Status.OK, Collections.unmodifiableList(items), root.optInt("maxCount", 5));
        } catch (java.io.IOException e) {
            return new ListResult(Status.OFFLINE, Collections.emptyList(), 0);
        } catch (Exception e) {
            return new ListResult(Status.ERROR, Collections.emptyList(), 0);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** Görselin baytları; hesap yoksa, ağ yoksa ya da sunucu reddederse {@code null}. */
    @Nullable
    public static byte[] download(@NonNull Context context, int id) {
        String vin = TripRecorder.get(context).vinOrEmpty();
        String bearer = vin.isEmpty() ? null : CloudToken.bearer(context, vin);
        if (bearer == null) {
            return null;
        }
        HttpURLConnection conn = null;
        try {
            conn = open(vin, "/" + id + "/data", bearer);
            int code = conn.getResponseCode();
            if (code == 401) {
                CloudToken.clear(context);
                return null;
            }
            if (code / 100 != 2) {
                return null;
            }
            return read(conn.getInputStream(), MAX_DOWNLOAD);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static HttpURLConnection open(String vin, String suffix, String bearer) throws java.io.IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(
                CloudApi.API_ROOT + "/api/cars/" + vin + "/wallpapers" + suffix).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("Authorization", "Bearer " + bearer);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("X-App-Version", BuildConfig.VERSION_NAME + "+" + BuildConfig.VERSION_CODE);
        return conn;
    }

    private static byte[] read(InputStream in, int max) throws java.io.IOException {
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = stream.read(buf)) > 0) {
                if (out.size() + n > max) {
                    throw new java.io.IOException("too large");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
