package com.mapcontrol.vehicle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.extra.project.carinfo.proxy.Constants;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.desaysv.ivi.vdb.event.id.carstate.VDEventCarState;
import com.desaysv.ivi.vdb.event.id.vehicle.VDEventVehicleHal;
import com.mapcontrol.api.CloudApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Kontak kapanışının gerçek zaman çizelgesini çıkaran salt okunur tanı (VDBus bench ekranından başlatılır).
 *
 * <p>Aynı satır düzeninde, milisaniye damgasıyla yazılanlar:
 * <ul>
 *   <li>{@code poll}: 100 ms'de bir ham okuma: {@code ID_SYSTEM_POWER_MODE} (14), {@code ID_CAR_POWER_MODE} (13),
 *       {@code ID_DRIVE_DOOR_STATE} (15). Yanıt yoksa {@code yok} yazılır; {@link CarInfoProxy#getItemValues}
 *       bu durumda {@code [0]} döndürdüğü için ham {@code getOnce} kullanılır.</li>
 *   <li>{@code cb}: aynı ID'ler için CarInfo değişim bildirimi.</li>
 *   <li>{@code bus}: {@code VDEventCarState.POWER_STATUS} ve {@code VDEventVehicleHal.IGNITION_STATE} olayları.</li>
 *   <li>{@code net}: varsayılan ağın gelişi, gidişi, internet doğrulaması.</li>
 *   <li>{@code http}: güç 2'den düşünce 12 sn boyunca hesap sunucusuna {@code HEAD} denemesi (süre ve sonuç).</li>
 *   <li>{@code app}: uygulamanın kendi kapanış işleri (yolculuk kutusu, park fotoğrafı, güç döngüsü günlükleri).</li>
 * </ul>
 * {@code +Δ} sütunu, gücün 2'den son düştüğü andan (yoksa kaydın başından) geçen süredir.
 *
 * <p>Satırlar her yazımda diske işlenir ({@link #logFile}); araç uykuya geçse de kayıt kalır ve sonraki açılışta
 * "son kaydı göster" ile okunur. Bilerek wake lock tutulmaz: amaç aracın kendi kapanış davranışını görmek.
 * Araca komut göndermez.
 */
public final class PowerTransitionProbe {
    public interface Logger {
        void log(String line);
    }

    private static final String TAG = "PowerProbe";
    private static final long POLL_MS = 100L;
    private static final long HTTP_WINDOW_MS = 12_000L;
    private static final long HTTP_GAP_MS = 300L;
    private static final int HTTP_TIMEOUT_MS = 1_500;
    private static final long MAX_FILE_BYTES = 512L * 1024L;
    private static final int[] WATCHED_IDS = {
            ReadOnlyID.ID_SYSTEM_POWER_MODE, ReadOnlyID.ID_CAR_POWER_MODE, ReadOnlyID.ID_DRIVE_DOOR_STATE
    };

    private static PowerTransitionProbe instance;

    public static synchronized PowerTransitionProbe get(Context context) {
        if (instance == null) {
            instance = new PowerTransitionProbe(context.getApplicationContext());
        }
        return instance;
    }

    public static File logFile(Context context) {
        return new File(new File(context.getFilesDir(), "diag"), "power-probe.log");
    }

    private final Context app;
    private final Object fileLock = new Object();
    private volatile Logger logger;
    private volatile boolean running;
    private ScheduledExecutorService poller;
    private ExecutorService httpExec;

    private String lastPm14 = "";
    private String lastPm13 = "";
    private String lastDoor = "";
    private Boolean lastProxyConnected;
    private volatile long t0 = 0L;
    private volatile long httpUntil = 0L;
    private volatile boolean httpLoopActive;

    private BroadcastReceiver appLogReceiver;
    private ConnectivityManager.NetworkCallback netCallback;
    private boolean carInfoRegistered;
    private boolean busRegistered;
    private VDEvent powerStatusEvent;
    private VDEvent ignitionEvent;

    private final IVDBusNotify.Stub carInfoCallback = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (!running || event == null || event.getPayload() == null) {
                return;
            }
            Bundle p = event.getPayload();
            int id = p.getInt(Constants.CMD_ID, -1);
            if (!isWatched(id)) {
                return;
            }
            write("cb", label(id) + "=" + format(p.getIntArray(Constants.VALUE)));
        }
    };

    private final IVDBusNotify.Stub busCallback = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (!running || event == null) {
                return;
            }
            String name = event.getId() == VDEventCarState.POWER_STATUS ? "POWER_STATUS"
                    : event.getId() == VDEventVehicleHal.IGNITION_STATE ? "IGNITION_STATE"
                    : String.valueOf(event.getId());
            write("bus", name + " " + describe(event.getPayload()));
        }
    };

    private PowerTransitionProbe(Context app) {
        this.app = app;
    }

    public boolean isRunning() {
        return running;
    }

    /** Ekran açıkken satırları gösterir; null verilirse yalnızca dosyaya yazılır. */
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public synchronized boolean start() {
        if (running) {
            return false;
        }
        running = true;
        t0 = 0L;
        lastPm14 = "";
        lastPm13 = "";
        lastDoor = "";
        lastProxyConnected = null;
        write("start", "güç geçiş tanısı başladı; dosya " + logFile(app).getAbsolutePath());
        poller = Executors.newSingleThreadScheduledExecutor();
        httpExec = Executors.newSingleThreadExecutor();
        poller.execute(this::subscribe);
        poller.scheduleWithFixedDelay(this::poll, 0, POLL_MS, TimeUnit.MILLISECONDS);
        registerNetwork();
        registerAppLog();
        // Taban ölçüm: araç açıkken bir istek ne kadar sürüyor.
        httpExec.execute(() -> httpProbe(0));
        return true;
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        httpUntil = 0L;
        unregisterAppLog();
        unregisterNetwork();
        if (poller != null) {
            poller.execute(this::unsubscribe);
            poller.shutdown();
            poller = null;
        }
        if (httpExec != null) {
            httpExec.shutdownNow();
            httpExec = null;
        }
        writeAlways("stop", "güç geçiş tanısı durduruldu");
    }

    /** Dosyanın son kısmı (en çok {@code maxChars}); dosya yoksa null. */
    public String readTail(int maxChars) {
        File f = logFile(app);
        if (!f.isFile()) {
            return null;
        }
        synchronized (fileLock) {
            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                long len = raf.length();
                long from = Math.max(0L, len - (long) maxChars * 2L);
                raf.seek(from);
                byte[] buf = new byte[(int) (len - from)];
                raf.readFully(buf);
                String text = new String(buf, StandardCharsets.UTF_8);
                if (from > 0) {
                    int nl = text.indexOf('\n');
                    text = nl >= 0 ? text.substring(nl + 1) : text;
                }
                return text.length() > maxChars ? text.substring(text.length() - maxChars) : text;
            } catch (IOException e) {
                return "okunamadı: " + e;
            }
        }
    }

    // ---------- okuma ----------

    private void poll() {
        if (!running) {
            return;
        }
        try {
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            boolean connected = proxy.isServiceConnnected();
            if (lastProxyConnected == null || lastProxyConnected != connected) {
                lastProxyConnected = connected;
                write("poll", "CarInfo servisi " + (connected ? "bağlı" : "KOPUK"));
                if (!connected) {
                    try {
                        proxy.init(app);
                    } catch (RuntimeException ignored) {
                        // sonraki turda yeniden denenir
                    }
                }
            }
            if (!connected) {
                return;
            }
            String pm14 = read(ReadOnlyID.ID_SYSTEM_POWER_MODE);
            String pm13 = read(ReadOnlyID.ID_CAR_POWER_MODE);
            String door = read(ReadOnlyID.ID_DRIVE_DOOR_STATE);
            if (pm14.equals(lastPm14) && pm13.equals(lastPm13) && door.equals(lastDoor)) {
                return;
            }
            boolean pmLeftRunning = "[2]".equals(lastPm14) && !"[2]".equals(pm14);
            if (pmLeftRunning) {
                t0 = SystemClock.elapsedRealtime();
            }
            StringBuilder sb = new StringBuilder();
            sb.append("PM14=").append(change(lastPm14, pm14))
                    .append(" PM13=").append(change(lastPm13, pm13))
                    .append(" KAPI=").append(change(lastDoor, door));
            lastPm14 = pm14;
            lastPm13 = pm13;
            lastDoor = door;
            write("poll", sb.toString());
            if (pmLeftRunning) {
                startHttpWindow();
            }
        } catch (RuntimeException e) {
            write("poll", "okuma hatası " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Ham değer; yanıt yoksa "yok". */
    private String read(int id) {
        Bundle request = new Bundle();
        request.putInt(Constants.CMD_ID, id);
        VDEvent once = VDBus.getDefault().getOnce(new VDEvent(VDEventCarInfo.MODULE_READONLY_INFO, request));
        if (once == null || once.getPayload() == null) {
            return "yok";
        }
        int[] v = once.getPayload().getIntArray(Constants.VALUE);
        return v == null ? "yok" : Arrays.toString(v);
    }

    private static String change(String before, String after) {
        return before.equals(after) || before.isEmpty() ? after : before + "→" + after;
    }

    // ---------- abonelikler ----------

    private void subscribe() {
        try {
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            if (!proxy.isServiceConnnected()) {
                proxy.init(app);
            }
            proxy.regCallBack(VDEventCarInfo.MODULE_READONLY_INFO, carInfoCallback, WATCHED_IDS);
            carInfoRegistered = true;
            write("cb", "CarInfo bildirimlerine abone olundu (14, 13, 15)");
        } catch (Throwable t) {
            write("cb", "CarInfo aboneliği başarısız: " + t);
        }
        try {
            VDBus bus = VDBus.getDefault();
            bus.init(app);
            powerStatusEvent = new VDEvent(VDEventCarState.POWER_STATUS);
            ignitionEvent = new VDEvent(VDEventVehicleHal.IGNITION_STATE);
            bus.subscribe(powerStatusEvent, busCallback);
            bus.subscribe(ignitionEvent, busCallback);
            busRegistered = true;
            write("bus", "POWER_STATUS (196609) ve IGNITION_STATE (1033) olaylarına abone olundu");
        } catch (Throwable t) {
            write("bus", "VDBus olay aboneliği başarısız: " + t);
        }
    }

    private void unsubscribe() {
        if (carInfoRegistered) {
            try {
                CarInfoProxy.getInstance().unRegCallBack(VDEventCarInfo.MODULE_READONLY_INFO, carInfoCallback);
            } catch (Throwable ignored) {
            }
            carInfoRegistered = false;
        }
        if (busRegistered) {
            try {
                VDBus.getDefault().unsubscribe(powerStatusEvent, busCallback);
                VDBus.getDefault().unsubscribe(ignitionEvent, busCallback);
            } catch (Throwable ignored) {
            }
            busRegistered = false;
        }
    }

    private void registerNetwork() {
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            write("net", "ConnectivityManager yok");
            return;
        }
        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                write("net", "ağ geldi " + network);
            }

            @Override
            public void onLost(Network network) {
                write("net", "AĞ GİTTİ " + network);
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                String transport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "wifi"
                        : caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "hücresel"
                        : caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "ethernet" : "diğer";
                write("net", network + " " + transport + (validated ? " internet doğrulandı" : " internet doğrulanmadı"));
            }
        };
        try {
            cm.registerDefaultNetworkCallback(netCallback);
        } catch (RuntimeException e) {
            write("net", "ağ dinlenemedi: " + e);
            netCallback = null;
        }
    }

    private void unregisterNetwork() {
        if (netCallback == null) {
            return;
        }
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            if (cm != null) {
                cm.unregisterNetworkCallback(netCallback);
            }
        } catch (RuntimeException ignored) {
        }
        netCallback = null;
    }

    /** Uygulamanın Günlük iletilerinden kapanışla ilgili olanlar aynı zaman çizelgesine düşer. */
    private void registerAppLog() {
        appLogReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String m = intent.getStringExtra("log_message");
                if (m == null) {
                    return;
                }
                String lower = m.toLowerCase(Locale.ROOT);
                if (lower.contains("yolculuk") || lower.contains("park fotoğrafı") || lower.contains("powermode")
                        || lower.contains("power mode") || lower.contains("kontak")) {
                    write("app", m);
                }
            }
        };
        app.registerReceiver(appLogReceiver, new IntentFilter("com.mapcontrol.LOG_MESSAGE"));
    }

    private void unregisterAppLog() {
        if (appLogReceiver == null) {
            return;
        }
        try {
            app.unregisterReceiver(appLogReceiver);
        } catch (RuntimeException ignored) {
        }
        appLogReceiver = null;
    }

    // ---------- internet denemesi ----------

    private void startHttpWindow() {
        httpUntil = SystemClock.elapsedRealtime() + HTTP_WINDOW_MS;
        ExecutorService exec = httpExec;
        if (exec == null || httpLoopActive) {
            return; // süren döngü yeni bitiş zamanını görür
        }
        httpLoopActive = true;
        exec.execute(() -> {
            try {
                int n = 1;
                while (running && SystemClock.elapsedRealtime() < httpUntil) {
                    long began = SystemClock.elapsedRealtime();
                    httpProbe(n++);
                    long left = HTTP_GAP_MS - (SystemClock.elapsedRealtime() - began);
                    if (left > 0) {
                        Thread.sleep(left);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                httpLoopActive = false;
            }
        });
    }

    private void httpProbe(int n) {
        long began = SystemClock.elapsedRealtime();
        HttpURLConnection conn = null;
        String result;
        try {
            conn = (HttpURLConnection) new URL(CloudApi.API_ROOT + "/").openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(HTTP_TIMEOUT_MS);
            conn.setReadTimeout(HTTP_TIMEOUT_MS);
            conn.setUseCaches(false);
            result = "HTTP " + conn.getResponseCode();
        } catch (IOException | RuntimeException e) {
            result = "HATA " + e.getClass().getSimpleName();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        long took = SystemClock.elapsedRealtime() - began;
        write("http", (n == 0 ? "taban" : "#" + n) + " " + result + " " + took + " ms");
    }

    // ---------- yazım ----------

    private void write(String source, String message) {
        if (!running) {
            return;
        }
        writeAlways(source, message);
    }

    private void writeAlways(String source, String message) {
        long now = SystemClock.elapsedRealtime();
        long base = t0;
        String delta = base == 0L ? "" : String.format(Locale.ROOT, " +%dms", now - base);
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date())
                + delta + " [" + source + "] " + message;
        Log.i(TAG, line);
        append(line);
        Logger l = logger;
        if (l != null) {
            l.log(line);
        }
    }

    private void append(String line) {
        synchronized (fileLock) {
            File f = logFile(app);
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                return;
            }
            if (f.length() > MAX_FILE_BYTES) {
                File old = new File(dir, f.getName() + ".1");
                //noinspection ResultOfMethodCallIgnored
                old.delete();
                //noinspection ResultOfMethodCallIgnored
                f.renameTo(old);
            }
            try (FileOutputStream out = new FileOutputStream(f, true)) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                out.getFD().sync(); // araç bir sonraki satırdan önce uyuyabilir
            } catch (IOException e) {
                Log.w(TAG, "tanı dosyası yazılamadı", e);
            }
        }
    }

    // ---------- biçim ----------

    private static boolean isWatched(int id) {
        for (int w : WATCHED_IDS) {
            if (w == id) {
                return true;
            }
        }
        return false;
    }

    private static String label(int id) {
        if (id == ReadOnlyID.ID_SYSTEM_POWER_MODE) {
            return "PM14";
        }
        if (id == ReadOnlyID.ID_CAR_POWER_MODE) {
            return "PM13";
        }
        if (id == ReadOnlyID.ID_DRIVE_DOOR_STATE) {
            return "KAPI";
        }
        return "ID" + id;
    }

    private static String format(int[] v) {
        return v == null ? "yok" : Arrays.toString(v);
    }

    private static String describe(Bundle b) {
        if (b == null) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        for (String key : b.keySet()) {
            Object v = b.get(key);
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(key).append('=');
            if (v instanceof int[]) {
                sb.append(Arrays.toString((int[]) v));
            } else if (v instanceof long[]) {
                sb.append(Arrays.toString((long[]) v));
            } else if (v instanceof double[]) {
                sb.append(Arrays.toString((double[]) v));
            } else if (v instanceof float[]) {
                sb.append(Arrays.toString((float[]) v));
            } else if (v instanceof String[]) {
                sb.append(Arrays.toString((String[]) v));
            } else {
                sb.append(v);
            }
        }
        return sb.append('}').toString();
    }
}
