package com.mapcontrol.dvr;

import android.content.Context;

import com.mapcontrol.api.ProfileApiService;
import com.mapcontrol.trip.PhoneAccess;
import com.mapcontrol.trip.TripRecorder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Yerel kayıttan sonra fotoğrafı (eşleşme anahtarıyla şifreli) hesabın sunucusuna bırakır; sunucu telefona
 * bildirim yollar, telefon alıp kaydedince siler. Bu adım yardımcıdır: oturum, şase ya da internet yoksa fotoğraf
 * araçta kalır ve telefon doğrudan HTTP ile alır. Hata fırlatmaz.
 */
final class ParkPhotoUploader {

    /** Kapı açılınca araç uyur; takılan bir bağlantı uyanık kalma süresini yemesin. */
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private ParkPhotoUploader() {
    }

    enum Result { UPLOADED, SKIPPED, FAILED }

    /** @return sonuç ve nedeni Günlük'e yazılacak kısa metin ({@code reason[0]}) */
    static Result upload(Context context, String[] reason) {
        ParkPhoto.Meta meta = ParkPhoto.meta(context);
        if (meta == null) {
            reason[0] = "fotoğraf yok";
            return Result.SKIPPED;
        }
        ProfileApiService profile = new ProfileApiService(context);
        if (!profile.isLoggedIn()) {
            reason[0] = "hesaba giriş yok, sunucuya yüklenmedi";
            return Result.SKIPPED;
        }
        String vin = TripRecorder.get(context).vinOrEmpty();
        if (vin.isEmpty()) {
            reason[0] = "şase numarası okunamadı, sunucuya yüklenmedi";
            return Result.SKIPPED;
        }
        HttpURLConnection conn = null;
        try {
            byte[] plain = readAll(ParkPhoto.photoFile(context));
            byte[] sealed = ParkPhotoCrypto.encrypt(ParkPhotoCrypto.deriveKey(PhoneAccess.token(context)), plain);
            conn = (HttpURLConnection) new URL(ProfileApiService.API_ROOT + "/api/cars/" + vin + "/park-photo")
                    .openConnection();
            conn.setRequestMethod("PUT");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(sealed.length);
            conn.setRequestProperty("Authorization", "Bearer " + profile.getToken());
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("X-Trip-Id", meta.tripId);
            conn.setRequestProperty("X-Captured-At", String.valueOf(meta.capturedAt));
            try (OutputStream out = conn.getOutputStream()) {
                out.write(sealed);
            }
            int code = conn.getResponseCode();
            if (code / 100 == 2) {
                reason[0] = (sealed.length / 1024) + " KB";
                return Result.UPLOADED;
            }
            // 404 car_not_found: araç bu hesapta kayıtlı değil. Diğer kodlar geçici olabilir.
            reason[0] = "sunucu " + code;
            return Result.FAILED;
        } catch (Exception e) {
            reason[0] = e.getClass().getSimpleName();
            return Result.FAILED;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static byte[] readAll(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] out = new byte[(int) f.length()];
            int off = 0;
            while (off < out.length) {
                int n = in.read(out, off, out.length - off);
                if (n < 0) {
                    throw new IOException("kısa dosya");
                }
                off += n;
            }
            return out;
        }
    }
}
