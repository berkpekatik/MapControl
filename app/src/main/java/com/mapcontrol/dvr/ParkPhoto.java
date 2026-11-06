package com.mapcontrol.dvr;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kontak kapanırken dört kameradan tek mozaik fotoğraf çeker ("aracı nereye park ettim"). Yalnızca son
 * fotoğraf tutulur; telefon {@code GET /parking} ve {@code GET /parking/photo} ile alır.
 *
 * <p><b>Kameralara dikkat:</b> güncelleme kurulurken kamera akışları bilerek kapatılır
 * ({@link CameraHub#suspendForInstall}); açık akış sürecin öldürülmesinde grafik sistemini düşürebilir. Bu yüzden
 * çekim kurulum beklemesi sürerken hiç başlamaz, beklerken bu başlarsa kare okunmadan vazgeçilir ve kamera
 * her durumda bırakılır. Fotoğraf hiçbir hatada kontak kapanışını ya da yolculuk kaydını etkilemez.
 */
public final class ParkPhoto {

    private static final String TAG = "ParkPhoto";
    private static final String HUB_TAG = "parkphoto";

    /** Ayar anahtarı ({@code MapControlPrefs}); varsayılan kapalı. */
    public static final String PREF_KEY = "parkPhotoEnabled";

    static final int WIDTH = 1280;
    static final int HEIGHT = 720;
    private static final int JPEG_QUALITY = 85;

    /** Kontak kapandıktan sonra kamera açılışı için en çok beklenen süre. */
    private static final long READY_TIMEOUT_MS = 4_000L;
    /** Canlı kameraların ilk karesini bekleme süresi. */
    private static final long FRAMES_TIMEOUT_MS = 3_000L;
    private static final long POLL_MS = 150L;
    /** Uyku kamerayı yarıda kesmesin diye tutulan uyanık kalma süresi (en çok). */
    private static final long WAKE_MS = 25_000L;

    private static final String META_PREFS = "park_photo";
    private static final String META_AT = "capturedAt";
    private static final String META_TRIP = "tripId";

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private ParkPhoto() {
    }

    /** logcat'e ve araçtaki Günlük sekmesine yazar; çekimin neden yapılmadığı oradan okunur. */
    private static void note(Context context, String message) {
        Log.i(TAG, message);
        context.sendBroadcast(new android.content.Intent("com.mapcontrol.LOG_MESSAGE")
                .putExtra("log_message", "Park fotoğrafı: " + message));
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE).getBoolean(PREF_KEY, false);
    }

    /** Son park fotoğrafının dosyası; yoksa dosya var olmayabilir. */
    public static File photoFile(Context context) {
        return new File(new File(context.getFilesDir(), "park"), "last.jpg");
    }

    /** {@code {capturedAt, tripId}}; fotoğraf yoksa null. */
    public static Meta meta(Context context) {
        File f = photoFile(context);
        if (!f.isFile() || f.length() == 0) {
            return null;
        }
        SharedPreferences p = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE);
        long at = p.getLong(META_AT, 0L);
        return at > 0 ? new Meta(at, p.getString(META_TRIP, "")) : null;
    }

    public static final class Meta {
        public final long capturedAt;
        public final String tripId;

        Meta(long capturedAt, String tripId) {
            this.capturedAt = capturedAt;
            this.tripId = tripId;
        }
    }

    /**
     * Ayar açıksa ve kamera donanımı varsa arka planda fotoğraf çeker. Hızlı döner; hata vermez.
     *
     * @param tripId fotoğrafın ait olduğu yolculuk
     */
    public static void captureAfterIgnitionOff(Context context, String tripId) {
        final Context app = context.getApplicationContext();
        if (!isEnabled(app)) {
            return; // ayar kapalı: sessiz
        }
        if (!new File("/dev/hab").exists() || !HabNative.isLoaded()) {
            note(app, "bu araçta kamera donanımı yok, atlandı");
            return;
        }
        if (CameraHub.get().isInstallHold()) {
            note(app, "güncelleme hazırlığı sürüyor, kameralar kapalı tutuluyor; atlandı");
            return;
        }
        if (!BUSY.compareAndSet(false, true)) {
            note(app, "önceki çekim sürüyor, atlandı");
            return;
        }
        note(app, "çekiliyor");
        EXEC.execute(() -> {
            PowerManager.WakeLock wake = null;
            try {
                PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mapcontrol:parkphoto");
                    wake.acquire(WAKE_MS);
                }
                capture(app, tripId);
                // Kamera bırakıldı; şimdi sunucuya yüklenir (yerel kayıt zaten tamam).
                String[] reason = {""};
                ParkPhotoUploader.Result r = ParkPhotoUploader.upload(app, reason);
                if (r == ParkPhotoUploader.Result.UPLOADED) {
                    note(app, "sunucuya yüklendi (" + reason[0] + ")");
                } else if (r == ParkPhotoUploader.Result.FAILED) {
                    note(app, "sunucuya yüklenemedi (" + reason[0] + "), telefon araçtan alır");
                } else if (reason[0] != null && !reason[0].equals("fotoğraf yok")) {
                    note(app, reason[0]);
                }
            } catch (Throwable t) {
                Log.w(TAG, "Park fotoğrafı alınamadı", t);
                note(app, "alınamadı: " + t);
            } finally {
                if (wake != null && wake.isHeld()) {
                    wake.release();
                }
                BUSY.set(false);
            }
        });
    }

    private static void capture(Context context, String tripId) throws Exception {
        final CameraHub hub = CameraHub.get();
        final int[] liveMask = {0};
        final CountDownLatch ready = new CountDownLatch(1);
        hub.acquire(HUB_TAG, mask -> {
            liveMask[0] = mask;
            ready.countDown();
        });
        try {
            if (!ready.await(READY_TIMEOUT_MS, TimeUnit.MILLISECONDS) || liveMask[0] == 0) {
                note(context, "kamera açılmadı (kontak kapanınca kameralar kapanmış olabilir), atlandı");
                return;
            }
            for (int cam = 0; cam < CameraHub.CAM_COUNT; cam++) {
                if ((liveMask[0] & (1 << cam)) != 0) {
                    hub.setDemand(HUB_TAG, cam, HabNative.COPY_HALF, false);
                }
            }
            final int want = Integer.bitCount(liveMask[0]);
            final ByteBuffer out = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 3 / 2);
            final String stamp = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(new Date());
            long deadline = SystemClock.elapsedRealtime() + FRAMES_TIMEOUT_MS;
            boolean composed = false;
            while (!composed) {
                // Güncelleme başladıysa kare okumayı bırak; akışlar zaten kapatılıyor.
                if (hub.isInstallHold()) {
                    note(context, "güncelleme hazırlığı başladı, fotoğraf bırakıldı");
                    return;
                }
                final boolean last = SystemClock.elapsedRealtime() >= deadline;
                final boolean[] done = {false};
                hub.readFrames((frames, widths, heights) -> {
                    int have = 0;
                    for (ByteBuffer f : frames) {
                        if (f != null) have++;
                    }
                    // Hepsi geldiyse hemen; süre dolduysa gelenlerle (en az bir kare).
                    if (have >= want || (last && have > 0)) {
                        HabNative.composeMosaic(frames, widths, heights, out, WIDTH, HEIGHT, stamp, null);
                        done[0] = true;
                    }
                });
                composed = done[0];
                if (!composed) {
                    if (last) {
                        note(context, "kamera karesi gelmedi, atlandı");
                        return;
                    }
                    Thread.sleep(POLL_MS);
                }
            }
            write(context, jpeg(out), tripId);
        } finally {
            hub.clearDemand(HUB_TAG);
            hub.release(HUB_TAG);
        }
    }

    private static byte[] jpeg(ByteBuffer i420) throws IOException {
        byte[] raw = new byte[WIDTH * HEIGHT * 3 / 2];
        ByteBuffer view = i420.duplicate();
        view.position(0);
        view.get(raw);
        byte[] nv21 = ParkPhotoImage.i420ToNv21(raw, WIDTH, HEIGHT);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(256 * 1024);
        if (!new YuvImage(nv21, ImageFormat.NV21, WIDTH, HEIGHT, null)
                .compressToJpeg(new Rect(0, 0, WIDTH, HEIGHT), JPEG_QUALITY, bytes)) {
            throw new IOException("JPEG sıkıştırılamadı");
        }
        return bytes.toByteArray();
    }

    /** Önce geçici dosyaya yazılır, sonra yer değiştirilir: telefon yarım dosya görmez. */
    private static void write(Context context, byte[] jpeg, String tripId) throws IOException {
        File target = photoFile(context);
        File dir = target.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Klasör açılamadı: " + dir);
        }
        File tmp = new File(dir, "last.jpg.tmp");
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            os.write(jpeg);
            os.getFD().sync();
        }
        if (!tmp.renameTo(target)) {
            throw new IOException("Fotoğraf yerine konamadı");
        }
        final long at = System.currentTimeMillis();
        context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE).edit()
                .putLong(META_AT, at).putString(META_TRIP, tripId).commit();
        note(context, "kaydedildi (" + (jpeg.length / 1024) + " KB, " + tripId + ")");
    }
}
