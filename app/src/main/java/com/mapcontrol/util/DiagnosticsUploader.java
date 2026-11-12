package com.mapcontrol.util;

import android.content.Context;
import android.os.Build;

import com.mapcontrol.BuildConfig;
import com.mapcontrol.R;
import com.mapcontrol.api.CloudApi;
import com.mapcontrol.api.CloudToken;
import com.mapcontrol.trip.TripRecorder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Tanılama paketini eşleşmiş hesaba yükler ({@code PUT /api/cars/{vin}/diagnostics}, araç token'ı). Paket dosyadan
 * akış olarak gönderilir (belleğe alınmaz). Arka plan iş parçacığında çağrılır; hata fırlatmaz.
 */
public final class DiagnosticsUploader {

    /** Sunucu 5 MB'a kadar alır; pay bırakılır. */
    static final long MAX_UPLOAD = 4L * 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    private DiagnosticsUploader() {
    }

    /** Sonuç: [message] kullanıcıya gösterilecek metin kaynağı; başarıda [id] sunucudaki kayıt numarası. */
    public static final class Result {
        public final boolean ok;
        public final int message;
        public final String detail;

        Result(boolean ok, int message, String detail) {
            this.ok = ok;
            this.message = message;
            this.detail = detail;
        }
    }

    /** Hesap bağlantısı var mı (şase okunmuş ve telefon araç token'ını vermiş). */
    public static boolean ready(Context context) {
        String vin = TripRecorder.get(context).vinOrEmpty();
        return !vin.isEmpty() && CloudToken.bearer(context, vin) != null;
    }

    public static Result upload(Context context, File file) {
        String vin = TripRecorder.get(context).vinOrEmpty();
        if (vin.isEmpty()) {
            return new Result(false, R.string.diag_err_no_vin, null);
        }
        String bearer = CloudToken.bearer(context, vin);
        if (bearer == null) {
            return new Result(false, R.string.diag_err_no_account, null);
        }
        long length = file.length();
        if (length <= 0) {
            return new Result(false, R.string.diag_err_failed, "bos");
        }
        if (length > MAX_UPLOAD) {
            return new Result(false, R.string.diag_err_too_large, (length >> 10) + " KB");
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(CloudApi.API_ROOT + "/api/cars/" + vin + "/diagnostics")
                    .openConnection();
            conn.setRequestMethod("PUT");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(length);
            conn.setRequestProperty("Authorization", "Bearer " + bearer);
            conn.setRequestProperty("Content-Type", "application/gzip");
            conn.setRequestProperty("X-App-Version", BuildConfig.VERSION_NAME + "+" + BuildConfig.VERSION_CODE);
            conn.setRequestProperty("X-Firmware", ascii(Build.DISPLAY));
            try (InputStream in = new FileInputStream(file); OutputStream out = conn.getOutputStream()) {
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            int code = conn.getResponseCode();
            if (code / 100 == 2) {
                String id = parseId(read(conn.getInputStream()));
                return new Result(true, R.string.diag_sent, id);
            }
            if (code == 401) {
                CloudToken.clear(context); // telefon yenisini verene kadar denenmez
                return new Result(false, R.string.diag_err_no_account, "401");
            }
            if (code == 404) {
                return new Result(false, R.string.diag_err_car_not_found, null);
            }
            if (code == 413) {
                return new Result(false, R.string.diag_err_too_large, null);
            }
            if (code == 429) {
                return new Result(false, R.string.diag_err_too_many, null);
            }
            return new Result(false, R.string.diag_err_failed, "sunucu " + code);
        } catch (Exception e) {
            return new Result(false, R.string.diag_err_network, e.getClass().getSimpleName());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** HTTP başlığı yalnızca ASCII taşır. */
    static String ascii(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(Math.min(s.length(), 120));
        for (int i = 0; i < s.length() && b.length() < 120; i++) {
            char c = s.charAt(i);
            b.append(c >= 0x20 && c < 0x7f ? c : '?');
        }
        return b.toString();
    }

    /** {"success":true,"id":123} → "123" (bulunamazsa null). */
    static String parseId(String json) {
        if (json == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String read(InputStream in) {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = s.read(buf)) > 0 && out.size() < 8192) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return null;
        }
    }
}
