package com.mapcontrol.trip;

import android.content.Context;
import android.util.Log;

import com.mapcontrol.api.ProfileApiService;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kontak kapanınca (ve yolculuk kesinleşince) yolculuğu ve o andaki sayaçları (odometre, yakıt, menzil), eşleşme
 * anahtarıyla şifreli olarak hesabın sunucusundaki posta kutusuna bırakır; telefon aracın ağında olmasa da yolculuğu oradan alır, kaydedince sunucu siler. Ayarı yoktur:
 * hesaba giriş yapılmış ve şase numarası okunabiliyorsa her yolculuk gider (içerik şifrelidir, sunucu okuyamaz).
 * Yerel kayıt zaten yapılmıştır; bu adım yardımcıdır: oturum, şase ya da internet yoksa yolculuk araçta kalır ve
 * telefon HTTP ile alır. Hata fırlatmaz.
 */
public final class TripBoxUploader {

    private static final String TAG = "TripBox";

    /** Kapı açılınca araç uyur; takılan bağlantı uyanık kalma süresini yemesin. */
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    /** Tek iş parçacığı: aynı yolculuğun yüklemeleri sırayla gider, sonuncusu kazanır. */
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static long lastVersion;

    private TripBoxUploader() {
    }

    /** Hızlı döner; yükleme arka planda yapılır. */
    public static void enqueue(Context context, TripRecord trip) {
        final Context app = context.getApplicationContext();
        if (trip == null) {
            return;
        }
        final long version = nextVersion();
        // Sayaçlar çağrı anında alınır (kontak kapanışı); yükleme sırada beklerse değer eskimesin.
        String snapshot = null;
        try {
            snapshot = TripRecorder.get(app).snapshotJson();
        } catch (RuntimeException e) {
            Log.w(TAG, "Sayaçlar okunamadı, yolculuk yine de bırakılacak", e);
        }
        final String snapshotJson = snapshot;
        EXEC.execute(() -> {
            try {
                upload(app, trip, snapshotJson, version);
            } catch (Throwable t) {
                Log.w(TAG, "Yolculuk kutuya bırakılamadı", t);
            }
        });
    }

    /** Her yükleme önceki sürümden büyük bir numara alır; telefon onayı yalnızca aldığı sürümü siler. */
    static synchronized long nextVersion() {
        long now = System.currentTimeMillis();
        lastVersion = Math.max(lastVersion + 1, now);
        return lastVersion;
    }

    static byte[] payload(TripRecord trip) {
        return payload(trip, null);
    }

    static byte[] payload(TripRecord trip, String snapshotJson) {
        return PhoneApiJson.tripBox(Collections.singletonList(trip), snapshotJson).getBytes(StandardCharsets.UTF_8);
    }

    private static void upload(Context context, TripRecord trip, String snapshotJson, long version) throws Exception {
        ProfileApiService profile = new ProfileApiService(context);
        if (!profile.isLoggedIn()) {
            note(context, "hesaba giriş yok, sunucuya bırakılmadı");
            return;
        }
        String vin = TripRecorder.get(context).vinOrEmpty();
        if (vin.isEmpty()) {
            note(context, "şase numarası okunamadı, sunucuya bırakılmadı");
            return;
        }
        byte[] sealed = TripBoxCrypto.encrypt(TripBoxCrypto.deriveKey(PhoneAccess.token(context)), payload(trip, snapshotJson));
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(ProfileApiService.API_ROOT + "/api/cars/" + vin + "/trip-box/" + trip.id)
                    .openConnection();
            conn.setRequestMethod("PUT");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(sealed.length);
            conn.setRequestProperty("Authorization", "Bearer " + profile.getToken());
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("X-Updated-At", String.valueOf(version));
            try (OutputStream out = conn.getOutputStream()) {
                out.write(sealed);
            }
            int code = conn.getResponseCode();
            note(context, code / 100 == 2
                    ? "sunucuya bırakıldı (" + trip.id + ", " + (sealed.length / 1024 + 1) + " KB)"
                    : "sunucuya bırakılamadı (" + trip.id + ", sunucu " + code + "), telefon araçtan alır");
        } catch (java.io.IOException e) {
            note(context, "sunucuya bırakılamadı (" + trip.id + ", " + e.getClass().getSimpleName() + "), telefon araçtan alır");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void note(Context context, String message) {
        Log.i(TAG, message);
        context.sendBroadcast(new android.content.Intent("com.mapcontrol.LOG_MESSAGE")
                .putExtra("log_message", "Yolculuk kutusu: " + message));
    }
}
