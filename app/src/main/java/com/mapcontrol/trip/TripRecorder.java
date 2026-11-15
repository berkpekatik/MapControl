package com.mapcontrol.trip;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Telefon yokken de çalışan kayıt: kontak kenarlarından yolculuk, kapı/kaput/tavan
 * geçişlerinden son açılma saatleri yazar, telefona {@code /trips} ve {@code /vehicle/now} verir.
 * Mantık {@link TripTracker} içindedir; burası Android bağlantısıdır.
 */
public final class TripRecorder implements TripTracker.Listener {

    private static final String TAG = "TripRecorder";

    /** Konum bundan eskiyse yolculuğa yazılmaz. */
    private static final long MAX_FIX_AGE_MS = 15_000L;
    /** Tek yanıtta en çok bu kadar yolculuk gider; telefon kaldığı yerden tekrar sorar. */
    static final int TRIPS_PAGE = 200;

    private static volatile TripRecorder sInstance;

    public static TripRecorder get(Context context) {
        if (sInstance == null) {
            synchronized (TripRecorder.class) {
                if (sInstance == null) {
                    sInstance = new TripRecorder(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final TripStore store;
    private final TripTracker tracker = new TripTracker(this);
    private volatile boolean started;
    private volatile String vin;
    /** Yalnızca io iş parçacığında: son işlenen güç örneğinin okuma anı ve açık olup olmadığı. */
    private long lastSampledAt = Long.MIN_VALUE;
    private boolean lastSampleOn;

    // Menzil kuralı ve son geçerli değer: com.mapcontrol.vehicle.VehicleRange (launcher ile ortak).

    private volatile Location lastFix;
    private boolean locating;
    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            lastFix = location;
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, android.os.Bundle extras) {
        }
    };

    private TripRecorder(Context context) {
        this.context = context;
        this.store = new TripStore(context);
    }

    /** Servis açılırken bir kez çağrılır. */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        // Araçta hesaba giriş kaldırıldı (sunucu anahtarını telefon verir); eski oturum kayıtları silinir.
        com.mapcontrol.api.CloudToken.dropLegacyLogin(context);
        VehicleMetricsRepository.getInstance(context).startMonitoring();
    }

    /** Okuma anı bilinmiyorsa: şimdi. */
    public void onPowerSample(int powerMode) {
        onPowerSample(powerMode, SystemClock.elapsedRealtime());
    }

    /**
     * Güç örneği. İki kaynaktan gelir: MapControlService'in 2 sn'lik döngüsü ve aracın güç değişim bildirimi
     * ({@link VehicleMetricsRepository}, ~100 ms). Okunamayan güç için -1 verilir.
     *
     * @param sampledAtElapsedMs okumanın başladığı an ({@link SystemClock#elapsedRealtime()}). Bildirimden sonra
     *                           sıraya giren ama ondan önce okunmuş eski bir döngü örneği atlanır; yoksa kapanış
     *                           anında "0 → 2 → 0" gibi sahte bir blip görülürdü.
     */
    public void onPowerSample(int powerMode, long sampledAtElapsedMs) {
        if (!started) {
            return;
        }
        io.execute(() -> {
            try {
                if (sampledAtElapsedMs < lastSampledAt) {
                    return;
                }
                lastSampledAt = sampledAtElapsedMs;
                boolean on = TripTracker.isPowerOn(powerMode);
                tracker.update(powerMode, System.currentTimeMillis(), readNow());
                setLocating(tracker.isActive());
                if (on) {
                    // Kapanışta zaman ~2 sn: bağlantı sıcak dursun, bekleyen paketler araç açıkken gitsin.
                    com.mapcontrol.api.CloudHttp.prewarm(context);
                    TripBoxUploader.retryPending(context, !lastSampleOn);
                }
                lastSampleOn = on;
            } catch (RuntimeException e) {
                Log.w(TAG, "Yolculuk örneği işlenemedi", e);
            }
        });
    }

    @Override
    public void onDiagnostic(String message) {
        // io iş parçacığında çağrılır. Araçtaki Günlük sekmesinde de görünsün.
        Log.i(TAG, message);
        context.sendBroadcast(new android.content.Intent("com.mapcontrol.LOG_MESSAGE").putExtra("log_message", message));
    }

    @Override
    public void onTripCheckpoint(TripRecord trip) {
        // io iş parçacığında çağrılır. Aynı id ile üzerine yazılır; sessizdir (her 15 sn'de bir gelir).
        try {
            store.insertTrip(trip);
        } catch (RuntimeException e) {
            Log.w(TAG, "Yolculuk denetim noktası yazılamadı", e);
        }
    }

    @Override
    public void onIgnitionOff(TripRecord trip) {
        // io iş parçacığında çağrılır. Kapanış zaman çizelgesinde (güç tanısı) bu anın görünmesi için.
        onDiagnostic("Kontak kapandı (PowerMode 0), kapanış işleri başlıyor: yolculuk " + trip.id);
        // Önce yolculuk: internet ~2 sn kalır, kapı açılınca kesilir. Telefon araçta değilse sunucu kutusundan alır.
        TripBoxUploader.enqueue(context, trip);
        // Sonra fotoğraf: çekim kendi iş parçacığında; yüklemesi yolculuk gönderiminin arkasına girer.
        com.mapcontrol.dvr.ParkPhoto.captureAfterIgnitionOff(context, trip.id);
    }

    @Override
    public void onTripClosed(TripRecord trip) {
        // io iş parçacığında çağrılır.
        try {
            store.insertTrip(trip);
            TripBoxUploader.enqueue(context, trip); // kapanıştaki son hâli (kontak kapandıktan sonraki blip dahil)
            Log.i(TAG, "Yolculuk kaydedildi: " + trip.id + " " + trip.distanceKm + " km, " + trip.fuelUsedMl + " ml");
            // Ölçeği doğrulamak için aracın kendi ortalamasıyla yan yana: ham sayılar birimi ele verir.
            VehicleMetricsRepository repo = VehicleMetricsRepository.getInstance(context);
            onDiagnostic("Yakıt karşılaştırma: kaydedilen " + trip.fuelUsedMl + " birim / " + trip.distanceKm
                    + " km; aracın bu tur ortalaması ham=" + repo.getCombined(ReadOnlyID.ID_AVERAGE_FUEL_CONS_AFTER_RUNNING)
                    + ", genel ortalama ham=" + repo.getCombined(ReadOnlyID.ID_AVG_FUEL_CONS));
        } catch (RuntimeException e) {
            Log.w(TAG, "Yolculuk yazılamadı", e);
        }
    }

    private TripReading readNow() {
        VehicleMetricsRepository repo = VehicleMetricsRepository.getInstance(context);
        VehicleMetricsSnapshot s = repo.currentSnapshot();
        Location fix = freshFix();
        return new TripReading(
                s.odoKm,
                repo.getCombined(ReadOnlyID.ID_SUM_FUEL),
                s.fuelPercent,
                stableRangeKm(s),
                s.preferredSpeed(),
                fix == null ? Double.NaN : fix.getLatitude(),
                fix == null ? Double.NaN : fix.getLongitude());
    }

    /**
     * Menzil yalnızca powerMode 2'de (kontak açık) yenilenir; 0/1'de araç geçersiz sayı (61440) verdiği için
     * son geçerli değer döner. Hiç geçerli değer yoksa -1.
     */
    private int stableRangeKm(VehicleMetricsSnapshot s) {
        return com.mapcontrol.vehicle.VehicleRange.stable(context, s);
    }

    /**
     * Rota noktalarının konum kaynağı: GPS Link'in yazdığı sahte konum "phone", aracın kendi GPS'i "car",
     * taze konum yoksa "none" (rota çizilemez).
     */
    @SuppressWarnings("deprecation")
    private String locationSource() {
        Location fix = freshFix();
        if (fix == null) {
            return PhoneApiJson.LOCATION_NONE;
        }
        return fix.isFromMockProvider() ? PhoneApiJson.LOCATION_PHONE : PhoneApiJson.LOCATION_CAR;
    }

    private Location freshFix() {
        Location fix = lastFix;
        if (fix == null) {
            return null;
        }
        long ageMs = (SystemClock.elapsedRealtimeNanos() - fix.getElapsedRealtimeNanos()) / 1_000_000L;
        return ageMs <= MAX_FIX_AGE_MS ? fix : null;
    }

    /** Konum güncellemeleri yalnızca yolculuk sürerken istenir. */
    private void setLocating(boolean want) {
        if (want == locating) {
            return;
        }
        locating = want;
        mainHandler.post(() -> {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                return;
            }
            try {
                if (want) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 0f, locationListener,
                            Looper.getMainLooper());
                } else {
                    lm.removeUpdates(locationListener);
                    lastFix = null;
                }
            } catch (SecurityException | IllegalArgumentException e) {
                // Konum izni verilmediyse yolculuk rotasız yazılır; km yine odometreden gelir.
                Log.w(TAG, "Konum güncellemesi açılamadı: " + e.getMessage());
            }
        });
    }

    // ---- telefon uçları --------------------------------------------------

    /** {@code GET /trips?since=} gövdesi. */
    public String tripsJson(long since) {
        // Sürmekte olan yolculuk telefona verilmez: yarım halini alırsa güncel halini görmez. Kontak kapandıktan sonra
        // (kapanış beklemesi) kayıt kesinleşmiş sayılır ve verilir; telefon park yerini hemen görsün. Güç bu sürede
        // dönerse telefon son yolculuğu her eşitlemede yeniden sorduğu için güncel halini yine alır.
        TripTracker.Status running = tracker.status();
        String open = running.active && !running.ending ? "t" + running.startedAt : null;
        return PhoneApiJson.trips(store.tripsSince(since, TRIPS_PAGE, open));
    }

    /**
     * {@code GET /vehicle/now} gövdesi; araç verisi henüz okunmadıysa {@code null}.
     */
    public String vehicleNowJson() {
        VehicleMetricsSnapshot s = VehicleMetricsRepository.getInstance(context).currentSnapshot();
        if (!s.connected || !TripReading.validOdo(s.odoKm)) {
            return null;
        }
        int fuel = TripReading.percent(s.fuelPercent);
        TripTracker.Status trip = tracker.status();
        String json = PhoneApiJson.vehicleNow(vin(), System.currentTimeMillis(), s.odoKm,
                fuel == TripReading.INVALID ? 0 : fuel, Math.max(0, stableRangeKm(s)),
                trip.active, trip.startedAt, trip.routePoints, trip.active ? locationSource() : null);
        return PhoneApiJson.withGps(json, com.mapcontrol.gps.LinkState.status().wireName(),
                com.mapcontrol.gps.LinkState.fixAgeMs());
    }

    /**
     * Kontak kapanırken sunucu kutusuna konan sayaçlar (odometre, yakıt, menzil); araç verisi okunmadıysa {@code null}.
     * {@code /vehicle/now}'un yolculuk ve GPS alanları olmadan hâli.
     */
    public String snapshotJson() {
        VehicleMetricsSnapshot s = VehicleMetricsRepository.getInstance(context).currentSnapshot();
        if (!s.connected || !TripReading.validOdo(s.odoKm)) {
            return null;
        }
        int fuel = TripReading.percent(s.fuelPercent);
        return PhoneApiJson.vehicleNow(vin(), System.currentTimeMillis(), s.odoKm,
                fuel == TripReading.INVALID ? 0 : fuel, Math.max(0, stableRangeKm(s)));
    }

    /** {@code GET /parking} gövdesi. */
    public String parkingJson() {
        com.mapcontrol.dvr.ParkPhoto.Meta m = com.mapcontrol.dvr.ParkPhoto.meta(context);
        return PhoneApiJson.parking(m == null ? 0L : m.capturedAt, m == null ? "" : m.tripId);
    }

    /** Şase numarası; araçtan okunamadıysa boş. QR eşleşmesi bunu telefona taşır. */
    public String vinOrEmpty() {
        return vin();
    }

    /** {@code persist.sys.vehicle.vin}, boşsa {@code persist.sys.tbox.vin}; ikisi de boşsa boş. */
    private String vin() {
        String cached = vin;
        if (cached == null) {
            cached = systemProperty("persist.sys.vehicle.vin");
            if (cached.isEmpty()) {
                cached = systemProperty("persist.sys.tbox.vin");
            }
            vin = cached;
        }
        return cached;
    }

    private static String systemProperty(String key) {
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            Object v = cls.getMethod("get", String.class, String.class).invoke(null, key, "");
            return v == null ? "" : v.toString().trim();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }
}
