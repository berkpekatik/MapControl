package com.mapcontrol.trip;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import com.mapcontrol.api.CloudHttp;
import com.mapcontrol.api.CloudApi;
import com.mapcontrol.api.CloudToken;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kontak kapanınca (ve yolculuk kesinleşince) yolculuğu ve o andaki sayaçları (odometre, yakıt, menzil), eşleşme
 * anahtarıyla şifreli olarak hesabın sunucusundaki posta kutusuna bırakır; telefon aracın ağında olmasa da yolculuğu
 * oradan alır, kaydedince sunucu siler. Ayarı yoktur: hesaba giriş yapılmış ve şase numarası okunabiliyorsa her
 * yolculuk gider (içerik şifrelidir, sunucu okuyamaz).
 *
 * <p>Zaman bütçesi dardır (ölçüm: kontak 0'dan sonra internet ~2 sn, kapı açılınca Wi-Fi kesilir). Bu yüzden:
 * paket önce diske ({@link TripBoxQueue}) yazılır, sonra sıcak bağlantıyla ({@link CloudHttp}) gönderilir; giderse
 * kuyruktan silinir. Gitmezse sonraki kontak açılışında ve yolculuk sürerken yeniden denenir ({@link #retryPending}).
 * Tüm gönderimler tek iş parçacığında sırayla gider: sunucu aynı yolculukta son yazılanı tutar, eski sürüm yeniyi
 * ezmesin. Park fotoğrafı bu sıranın arkasına girer ({@link #afterUploads}): önce yolculuk, sonra fotoğraf.
 *
 * <p>Yerel kayıt zaten yapılmıştır; bu adım yardımcıdır: oturum, şase ya da internet yoksa yolculuk araçta da durur
 * ve telefon HTTP ile alır. Hata fırlatmaz.
 */
public final class TripBoxUploader {

    private static final String TAG = "TripBox";
    /** Kapı açılınca araç uyur; takılan bağlantı uyanık kalma süresini yemesin. */
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final long WAKE_MS = 20_000L;
    /** Kuyruktaki paketler en sık bu aralıkla yeniden denenir (kontak açılışı hariç). */
    private static final long RETRY_EVERY_MS = 60_000L;
    /** Tek iş parçacığı: aynı yolculuğun yüklemeleri sırayla gider, sonuncusu kazanır. */
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    private static long lastVersion;
    private static volatile long lastRetryAt;
    private static volatile TripBoxQueue queue;

    enum Outcome { SENT, KEEP, DROP }

    private TripBoxUploader() {
    }

    /** Hızlı döner; paket diske yazılır, gönderim arka planda yapılır. */
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
        final byte[] plain = payload(trip, snapshot);
        final String tripId = trip.id;
        if (CloudToken.bearer(app, TripRecorder.get(app).vinOrEmpty()) == null) {
            note(app, "sunucu anahtarı yok (telefon araç ağındayken verir), sunucuya bırakılmadı; telefon araçtan alır");
            return;
        }
        // Ağdan önce diske: gönderim sırası beklerken araç uyusa da paket sonraki açılışta gider.
        final TripBoxQueue q = queue(app);
        q.put(tripId, version, plain);
        EXEC.execute(() -> {
            PowerManager.WakeLock wake = CloudHttp.acquireWake(app, "mapcontrol:tripbox", WAKE_MS);
            try {
                long began = SystemClock.elapsedRealtime();
                Outcome r = upload(app, tripId, version, plain);
                if (r != Outcome.KEEP) {
                    q.remove(tripId, version);
                }
                if (r == Outcome.SENT) {
                    note(app, "sunucuya bırakıldı (" + tripId + ", " + (plain.length / 1024 + 1) + " KB, "
                            + (SystemClock.elapsedRealtime() - began) + " ms)");
                }
            } catch (Throwable t) {
                Log.w(TAG, "Yolculuk kutuya bırakılamadı", t);
            } finally {
                CloudHttp.release(wake);
            }
        });
    }

    /**
     * Gönderilemeyen paketleri yeniden dener. {@code force} değilse en sık {@link #RETRY_EVERY_MS}. İnternet
     * doğrulanmamışsa hiç denemez: DNS takılırsa (10 sn) kapanış yüklemesini bekletmesin. Hızlı döner.
     */
    public static void retryPending(Context context, boolean force) {
        final Context app = context.getApplicationContext();
        long now = SystemClock.elapsedRealtime();
        if (!force && lastRetryAt != 0L && now - lastRetryAt < RETRY_EVERY_MS) {
            return;
        }
        lastRetryAt = now;
        EXEC.execute(() -> {
            TripBoxQueue q = queue(app);
            List<TripBoxQueue.Item> items = q.pending(System.currentTimeMillis());
            if (items.isEmpty()) {
                return;
            }
            if (!CloudHttp.hasInternet(app)) {
                lastRetryAt = 0L; // internet gelince sonraki çağrıda denensin
                return;
            }
            if (CloudToken.bearer(app, TripRecorder.get(app).vinOrEmpty()) == null) {
                return; // telefon sunucu anahtarını verince gider
            }
            PowerManager.WakeLock wake = CloudHttp.acquireWake(app, "mapcontrol:tripbox-retry", WAKE_MS);
            try {
                int sent = 0;
                for (TripBoxQueue.Item item : items) {
                    Outcome r = upload(app, item.tripId, item.version, item.payload);
                    if (r == Outcome.KEEP) {
                        break; // ağ yine gitti: kalanlar sonraki denemede
                    }
                    q.remove(item.tripId, item.version);
                    if (r == Outcome.SENT) {
                        sent++;
                    }
                }
                if (sent > 0) {
                    note(app, "bekleyen " + sent + " yolculuk sunucuya bırakıldı (kalan " + q.size() + ")");
                }
            } catch (Throwable t) {
                Log.w(TAG, "Bekleyen yolculuklar gönderilemedi", t);
            } finally {
                CloudHttp.release(wake);
            }
        });
    }

    /** Sıradaki yolculuk yüklemeleri bittikten sonra çalışır (park fotoğrafı yüklemesi için). */
    public static void afterUploads(Runnable task) {
        EXEC.execute(task);
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

    /** HTTP yanıtına göre paketin kaderi: gitti, sonra yeniden dene, ya da bir daha deneme. */
    static Outcome outcomeFor(int code) {
        if (code / 100 == 2) {
            return Outcome.SENT;
        }
        // Araç anahtarı geçersiz (401; telefon yenisini verir), sunucu yoğun (408/429) ya da sunucu hatası (5xx): sonra yeniden denenir.
        if (code == 401 || code == 408 || code == 429 || code >= 500) {
            return Outcome.KEEP;
        }
        // Araç hesapta yok (404), paket geçersiz/çok büyük (400/413) vb.: tekrar denemek düzeltmez.
        return Outcome.DROP;
    }

    private static synchronized TripBoxQueue queue(Context context) {
        if (queue == null) {
            queue = new TripBoxQueue(new File(context.getFilesDir(), "tripbox-pending"));
        }
        return queue;
    }

    private static Outcome upload(Context context, String tripId, long version, byte[] plain) {
        String vin = TripRecorder.get(context).vinOrEmpty();
        if (vin.isEmpty()) {
            note(context, "şase numarası okunamadı, sunucuya bırakılmadı (" + tripId + "), sonra denenecek");
            return Outcome.KEEP;
        }
        String bearer = CloudToken.bearer(context, vin);
        if (bearer == null) {
            note(context, "sunucu anahtarı yok (" + tripId + "), telefon verince gönderilecek");
            return Outcome.KEEP;
        }
        HttpURLConnection conn = null;
        try {
            byte[] sealed = TripBoxCrypto.encrypt(TripBoxCrypto.deriveKey(PhoneAccess.token(context)), plain);
            conn = (HttpURLConnection) new URL(CloudApi.API_ROOT + "/api/cars/" + vin + "/trip-box/" + tripId)
                    .openConnection();
            conn.setRequestMethod("PUT");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(sealed.length);
            conn.setRequestProperty("Authorization", "Bearer " + bearer);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("X-Updated-At", String.valueOf(version));
            try (OutputStream out = conn.getOutputStream()) {
                out.write(sealed);
            }
            int code = conn.getResponseCode();
            CloudHttp.finish(conn, code); // bağlantı havuza döner, sonraki istek (fotoğraf) DNS/TLS kurmaz
            if (code == 401) {
                CloudToken.clear(context); // token geçersiz: telefon yenisini verene kadar kuyrukta bekler
            }
            Outcome r = outcomeFor(code);
            if (r != Outcome.SENT) {
                note(context, "sunucuya bırakılamadı (" + tripId + ", sunucu " + code + ")"
                        + (r == Outcome.KEEP ? ", sonra yeniden denenecek" : ", bırakıldı; telefon araçtan alır"));
            }
            return r;
        } catch (IOException e) {
            if (conn != null) {
                conn.disconnect();
            }
            note(context, "sunucuya bırakılamadı (" + tripId + ", " + e.getClass().getSimpleName()
                    + "), sonra yeniden denenecek");
            return Outcome.KEEP;
        } catch (Exception e) {
            if (conn != null) {
                conn.disconnect();
            }
            note(context, "paket hazırlanamadı (" + tripId + ", " + e.getClass().getSimpleName() + ")");
            return Outcome.DROP;
        }
    }

    private static void note(Context context, String message) {
        Log.i(TAG, message);
        context.sendBroadcast(new android.content.Intent("com.mapcontrol.LOG_MESSAGE")
                .putExtra("log_message", "Yolculuk kutusu: " + message));
    }
}
