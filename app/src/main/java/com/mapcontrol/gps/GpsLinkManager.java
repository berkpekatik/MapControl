package com.mapcontrol.gps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.PowerManager;

import com.mapcontrol.trip.PhoneAccess;

/**
 * GPS Link alıcısının yaşam döngüsü. {@code MapControlService} içinde araç yazılımı açıkken çalışır;
 * web sunucusundan bağımsızdır. Anahtar, telefon eşleme anahtarından türetilir ({@link Wire#deriveKey}), bu yüzden
 * "Yeni anahtar üret" sonrası {@link #restart()} çağrılmalıdır.
 */
public final class GpsLinkManager {

    private static GpsLinkManager instance;

    private final Context context;
    private LinkEngine engine;
    private PowerManager.WakeLock wakeLock;

    private GpsLinkManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized GpsLinkManager get(Context context) {
        if (instance == null) {
            instance = new GpsLinkManager(context);
        }
        return instance;
    }

    /**
     * Alıcıyı başlatır. Konum kapalıysa veya bu uygulama sahte konum uygulaması seçilmediyse başlamaz ve
     * {@link LinkState#status()} nedenini söyler; {@link #ensureRunning()} engel kalkınca yeniden dener.
     */
    @SuppressLint("WakelockTimeout")
    public synchronized void start() {
        if (engine != null) {
            return;
        }
        if (!DeviceChecks.locationEnabled(context)) {
            LinkState.fail(LinkStatus.LOCATION_OFF);
            return;
        }
        if (!DeviceChecks.mockAllowed(context)) {
            LinkState.fail(LinkStatus.NEEDS_MOCK);
            return;
        }
        LinkEngine created = createEngine();
        if (created == null) {
            LinkState.fail(LinkStatus.NEEDS_MOCK);
            return;
        }
        engine = created;
        // Ekran kapalıyken de telefonun paketleri işlenmeli; kilit alıcı çalıştığı sürece tutulur.
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MapControl:gpslink");
        wakeLock.acquire();
        LinkState.begin();
        engine.start();
    }

    public synchronized void stop() {
        if (engine != null) {
            engine.stop();
            engine = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
        LinkState.end();
    }

    /** Anahtar değiştiğinde: eski anahtarla çalışan alıcıyı kapatıp yenisini açar. */
    public synchronized void restart() {
        stop();
        start();
    }

    /** Servis düzenli çağırır: çalışmıyorsa ve engel kalktıysa (konum açıldı, sahte konum seçildi) başlatır. */
    public synchronized void ensureRunning() {
        if (engine == null) {
            start();
        }
    }

    private LinkEngine createEngine() {
        try {
            if (!MockGpsProvider.restoreIfOwned(context)) {
                return null;
            }
            byte[] key = Wire.deriveKey(PhoneAccess.token(context));
            return new CarReceiver(context, key, host);
        } catch (IllegalArgumentException invalidKey) {
            return null;
        }
    }

    /** Motor devam edemezse bağlantı tümüyle kapanır. Durdurma birkaç yüz ms bekleyebilir; ana iş parçacığında yapılmaz. */
    private final LinkEngine.Host host = reason -> new Thread(() -> {
        stop();
        LinkState.fail(reason);
    }, "MapControlGPS-fatal").start();
}
