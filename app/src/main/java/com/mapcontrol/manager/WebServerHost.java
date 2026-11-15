package com.mapcontrol.manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.mapcontrol.ui.activity.MainActivity;
import com.mapcontrol.ui.builder.FileUploadTabBuilder;
import com.mapcontrol.util.LinkOpener;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;

import java.util.ArrayDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Telefonla konuşan HTTP sunucusunun (7462) sahibi. Sunucu artık {@code MainActivity}'ye bağlı değildir: uygulama
 * genelinde tek örnek, {@code MapControlService} ve activity tarafından kullanılır; activity kapanınca sunucu
 * kapanmaz.
 *
 * <ul>
 *   <li><b>Kontak:</b> güç modu kapalıdan açığa (1/2) geçince, "otomatik başlat" açıksa sunucu açılır.
 *       Elle kapatılan sunucu aynı kontakta kendiliğinden açılmaz.</li>
 *   <li><b>Ağ:</b> Wi-Fi bağlanınca, hotspot açılıp kapanınca ya da adres değişince mDNS kaydı yenilenir, arayüz
 *       (QR adresi) güncellenir; açık olması istenen sunucu düşmüşse yeniden açılır.</li>
 *   <li><b>Bekçi:</b> 30 sn'de bir; açık olması istenen sunucu sağlıklı değilse üstel beklemeyle yeniden açar.
 *       Aynı turda Wi-Fi bağlantısı da yoklanır: "bağlı" görünüp ağ geçidine ulaşamayan bağlantı onarılır
 *       ({@link WifiLinkWatchdog}).</li>
 *   <li><b>Activity yokken gelen komut:</b> uygulama başlatma, klavye, link açma ve silme doğrudan
 *       yapılır (launcher üstüne MapControl açılmaz); APK kurma activity ister, kuyruğa alınır ve activity öne
 *       getirilir.</li>
 *   <li><b>Başka ekranlar (launcher):</b> {@link #addStateListener} ile durumu dinler, {@link #startServer} /
 *       {@link #stopServer} ile açıp kapatır; açma/kapama arka plan iş parçacığında yapılır (durdurma 1 sn'ye kadar
 *       bekleyebilir, ana iş parçacığı bloklanmaz).</li>
 * </ul>
 */
public final class WebServerHost {

    private static final String TAG = "WebServerHost";
    private static final String PREFS = "MapControlPrefs";
    private static final long WATCHDOG_MS = 30_000L;
    private static final long NETWORK_DEBOUNCE_MS = 1_500L;
    private static final long RETRY_INITIAL_MS = 2_000L;
    private static final long RETRY_MAX_MS = 60_000L;
    /** Activity yokken gelen komut bu süreden sonra uygulanmaz: geç kalmış kurulum/link sürpriz olmasın. */
    private static final long PENDING_TTL_MS = 2 * 60_000L;
    private static final int PENDING_MAX = 20;

    private static volatile WebServerHost instance;

    public static WebServerHost get(Context context) {
        WebServerHost local = instance;
        if (local == null) {
            synchronized (WebServerHost.class) {
                local = instance;
                if (local == null) {
                    local = new WebServerHost(context.getApplicationContext());
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Ana iş parçacığında çağrılır: sunucu açıldı, kapandı, hata verdi ya da adresi değişti. */
    public interface StateListener {
        void onWebServerStateChanged();
    }

    /** Açma sonucu (ana iş parçacığında): null = çalışıyor, değilse hata metni. */
    public interface StartCallback {
        void onResult(String error);
    }

    private interface UiCall {
        void run(WebServerManager.WebServerListener ui);
    }

    private static final class Pending {
        final long at = System.currentTimeMillis();
        final UiCall call;

        Pending(UiCall call) {
            this.call = call;
        }
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final WebServerManager manager;
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final CopyOnWriteArrayList<StateListener> stateListeners = new CopyOnWriteArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, TAG);
        t.setDaemon(true);
        return t;
    });
    private volatile String lastError;
    private final AtomicBoolean restarting = new AtomicBoolean();
    private final WifiLinkWatchdog wifiLink;

    private volatile WebServerManager.WebServerListener ui;
    private boolean monitoring;
    private boolean powerOn;
    private long retryDelayMs = RETRY_INITIAL_MS;
    private String advertisedHost;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback networkCallback;
    private BroadcastReceiver apReceiver;
    private VehicleMetricsRepository.Listener powerListener;

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            check("bekçi");
            wifiLink.tick();
            main.postDelayed(this, WATCHDOG_MS);
        }
    };
    private final Runnable networkChanged = () -> {
        refreshForNetwork();
        check("ağ");
    };
    private final Runnable retry = () -> check("yeniden deneme");

    private WebServerHost(Context app) {
        this.app = app;
        // Takılı kalan Wi-Fi onarılınca ağ değişti bildirimi gelmeyebilir: mDNS ve QR adresi burada da yenilensin.
        this.wifiLink = new WifiLinkWatchdog(app, () -> main.post(networkChanged));
        this.manager = new WebServerManager(app);
        this.manager.setListener(new Forwarder());
    }

    public WebServerManager manager() {
        return manager;
    }

    public boolean isRunning() {
        return manager.isRunning();
    }

    /** Telefonun bağlanacağı güncel adres (hotspot sunucudan sonra açılmış olabilir). */
    public String currentHost() {
        return manager.currentHost();
    }

    // ---- elle açma / kapama ---------------------------------------------

    /** Sunucuyu arka planda açar; çalışıyorsa yalnızca sonucu bildirir. [callback] null olabilir. */
    public void startServer(StartCallback callback) {
        io.execute(() -> {
            if (!manager.isRunning()) {
                lastError = null;
                manager.startServer();
            }
            String error = manager.isRunning() ? null : (lastError != null ? lastError : "Sunucu açılamadı");
            main.post(() -> {
                afterStartAttempt();
                if (callback != null) {
                    callback.onResult(error);
                }
            });
        });
    }

    /** Sunucuyu arka planda kapatır; elle kapatılan sunucu bekçi ya da kontakla kendiliğinden açılmaz. */
    public void stopServer() {
        io.execute(() -> {
            manager.stopServer();
            // Sürmekte olan bir istek (ör. APK yükleme) sunucu iş parçacığını tutarsa onServerStopped ancak istek
            // bitince gelir; isRunning() şimdiden false olduğu için arayüz hemen güncellenir (tekrarı zararsız).
            notifyState();
            main.post(() -> {
                WebServerManager.WebServerListener target = ui;
                if (target != null && !manager.isRunning()) {
                    target.onServerStopped();
                }
            });
        });
    }

    /** Aktar sekmesindeki aç/kapat düğmesi. */
    public void toggleServer() {
        if (manager.isRunning()) {
            stopServer();
        } else {
            startServer(null);
        }
    }

    /**
     * Ekler ve güncel durumu okuması için hemen bir kez bildirir; sonra durum değişince ana iş parçacığında bildirilir.
     * Durum {@link #isRunning()} ve {@link #currentHost()} ile okunur.
     */
    public void addStateListener(StateListener listener) {
        if (listener == null || !stateListeners.addIfAbsent(listener)) {
            return;
        }
        main.post(() -> {
            if (stateListeners.contains(listener)) {
                listener.onWebServerStateChanged();
            }
        });
    }

    public void removeStateListener(StateListener listener) {
        stateListeners.remove(listener);
    }

    private void notifyState() {
        if (stateListeners.isEmpty()) {
            return;
        }
        main.post(() -> {
            for (StateListener l : stateListeners) {
                if (stateListeners.contains(l)) {
                    l.onWebServerStateChanged();
                }
            }
        });
    }

    // ---- yaşam döngüsü --------------------------------------------------

    /**
     * Ağ, kontak ve bekçi izlemesini başlatır; tekrar çağrılması zararsızdır. {@code MapControlService} ve
     * {@code MainActivity} ikisi de çağırır: hangisi önce açılırsa izleme o zaman başlar.
     */
    public synchronized void start() {
        if (monitoring) {
            return;
        }
        monitoring = true;
        registerNetwork();
        registerPower();
        main.post(() -> {
            startIfPreferred();
            main.postDelayed(watchdog, WATCHDOG_MS);
        });
    }

    /** Kullanıcı "otomatik başlat"ı seçtiyse ve sunucu kapalıysa açar; varsayılan kapalı. */
    public void startIfPreferred() {
        if (manager.isRunning() || !autoStart()) {
            return;
        }
        // Açma/kapama kilidi (durdurma 1 sn'ye kadar bekleyebilir) ana iş parçacığını tutmasın.
        io.execute(() -> {
            if (!manager.isRunning()) {
                manager.startServer();
            }
            main.post(this::afterStartAttempt);
        });
    }

    private boolean autoStart() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(FileUploadTabBuilder.KEY_WEB_SERVER_AUTO_START, false);
    }

    // ---- arayüz bağlama -------------------------------------------------

    /**
     * Activity açılınca dinleyicisini bağlar. Sunucunun şu anki durumu hemen iletilir (QR, düğme metni) ve activity
     * yokken kuyruğa alınan komutlar uygulanır.
     */
    public void attachUi(WebServerManager.WebServerListener listener) {
        ui = listener;
        start();
        if (manager.isRunning()) {
            listener.onServerStarted(manager.getPort(), manager.currentHost());
        } else {
            listener.onServerStopped();
        }
        drainPending();
    }

    /** Activity kapanınca bağlantıyı bırakır; sunucu kapanmaz. Başka dinleyici bağlanmışsa dokunmaz. */
    public void detachUi(WebServerManager.WebServerListener listener) {
        if (ui == listener) {
            ui = null;
        }
    }

    private void drainPending() {
        final WebServerManager.WebServerListener target = ui;
        if (target == null) {
            return;
        }
        long now = System.currentTimeMillis();
        while (true) {
            Pending next;
            synchronized (pending) {
                next = pending.poll();
            }
            if (next == null) {
                return;
            }
            if (now - next.at > PENDING_TTL_MS) {
                Log.w(TAG, "Bekleyen komut zaman aşımına uğradı, atlandı");
                continue;
            }
            next.call.run(target);
        }
    }

    /** Activity bağlıysa komutu ona verir; değilse kuyruğa alır ve activity'yi öne getirir. */
    private void dispatchOrQueue(UiCall call) {
        WebServerManager.WebServerListener target = ui;
        if (target != null) {
            call.run(target);
            return;
        }
        synchronized (pending) {
            if (pending.size() >= PENDING_MAX) {
                pending.poll();
            }
            pending.add(new Pending(call));
        }
        // Kuyruğa eklerken attachUi araya girip kuyruğu boşaltmış olabilir: komut sonraki bağlanmaya kalmasın.
        if (ui != null) {
            main.post(this::drainPending);
            return;
        }
        try {
            Intent open = new Intent(app, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            app.startActivity(open);
        } catch (RuntimeException e) {
            Log.w(TAG, "Activity öne getirilemedi, komut kuyrukta bekliyor", e);
        }
    }

    // ---- sunucuyu ayakta tutma ------------------------------------------

    /** Açık olması istenen sunucu sağlıklı değilse üstel beklemeyle yeniden açar; adres değiştiyse yayını yeniler. */
    private void check(String reason) {
        if (manager.isDesired() && !manager.isHealthy()) {
            if (restarting.compareAndSet(false, true)) {
                Log.i(TAG, "Sunucu sağlıklı değil (" + reason + "), yeniden açılıyor");
                // restart() 1 sn'ye kadar bekleyebilir: arka planda yapılır.
                io.execute(() -> {
                    try {
                        if (manager.isDesired() && !manager.isHealthy()) {
                            manager.restart();
                        }
                    } finally {
                        restarting.set(false);
                        main.post(this::afterStartAttempt);
                    }
                });
            }
            return;
        }
        if (manager.isRunning()) {
            String host = manager.currentHost();
            if (advertisedHost != null && !advertisedHost.equals(host)) {
                refreshForNetwork();
            }
        }
    }

    /** Açılış denemesinden sonra: başarılıysa beklemeyi sıfırlar, değilse bir sonraki denemeyi planlar. */
    private void afterStartAttempt() {
        main.removeCallbacks(retry);
        if (manager.isRunning()) {
            retryDelayMs = RETRY_INITIAL_MS;
            return;
        }
        if (manager.isDesired()) {
            main.postDelayed(retry, retryDelayMs);
            retryDelayMs = Math.min(retryDelayMs * 2, RETRY_MAX_MS);
        }
    }

    /** Ağ değişti: mDNS kaydı yenilenir ve arayüze güncel adres iletilir (QR yeniden çizilir). */
    private void refreshForNetwork() {
        if (!manager.isRunning()) {
            return;
        }
        manager.refreshAdvertisement();
        String host = manager.currentHost();
        advertisedHost = host;
        WebServerManager.WebServerListener target = ui;
        if (target != null) {
            target.onServerStarted(manager.getPort(), host);
        }
        notifyState();
    }

    // ---- ağ ve kontak tetikleyicileri -----------------------------------

    private void scheduleNetworkChanged() {
        main.removeCallbacks(networkChanged);
        main.postDelayed(networkChanged, NETWORK_DEBOUNCE_MS);
    }

    private void registerNetwork() {
        try {
            connectivity = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity != null) {
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        scheduleNetworkChanged();
                    }

                    @Override
                    public void onLost(@NonNull Network network) {
                        scheduleNetworkChanged();
                    }

                    @Override
                    public void onLinkPropertiesChanged(@NonNull Network network,
                                                        @NonNull android.net.LinkProperties properties) {
                        scheduleNetworkChanged();
                    }
                };
                // Kısıt koymayan istek: internetsiz araç hotspot'u ve yerel ağlar da bildirilir.
                connectivity.registerNetworkCallback(new NetworkRequest.Builder().build(), networkCallback);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Ağ dinleyicisi kaydedilemedi; yalnızca bekçi çalışır", e);
        }
        // Araç kendi hotspot'unu açtığında (kendisi ağ sağlayıcı) ConnectivityManager çoğu zaman ağ bildirmez.
        try {
            apReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    scheduleNetworkChanged();
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
            filter.addAction("android.net.conn.TETHER_STATE_CHANGED");
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(apReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                app.registerReceiver(apReceiver, filter);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Hotspot dinleyicisi kaydedilemedi", e);
            apReceiver = null;
        }
    }

    private void registerPower() {
        try {
            // Elle kapatılan sunucu aynı kontakta tekrar açılmasın: yalnızca kapalıdan açığa geçişte başlar.
            powerListener = snapshot -> {
                boolean valid = VehicleMetricsSnapshot.isValid(snapshot.powerMode);
                boolean on = valid && (snapshot.powerMode == 1 || snapshot.powerMode == 2);
                if (on && !powerOn) {
                    main.post(this::startIfPreferred);
                }
                if (valid) {
                    powerOn = on;
                }
            };
            VehicleMetricsRepository repo = VehicleMetricsRepository.getInstance(app);
            repo.addListener(powerListener);
            repo.startMonitoring();
        } catch (RuntimeException e) {
            Log.w(TAG, "Kontak dinleyicisi kaydedilemedi; bekçi ve ağ tetikleyicisi yine çalışır", e);
        }
    }

    // ---- sunucudan gelen olaylar ----------------------------------------

    /** {@link WebServerManager} dinleyicisi: arayüze iletir; activity yoksa yapılabilenleri kendisi yapar. */
    private final class Forwarder implements WebServerManager.WebServerListener {

        @Override
        public void onServerStarted(int port, String localIp) {
            advertisedHost = localIp;
            lastError = null;
            WebServerManager.WebServerListener target = ui;
            if (target != null) {
                target.onServerStarted(port, localIp);
            }
            notifyState();
        }

        @Override
        public void onServerStopped() {
            WebServerManager.WebServerListener target = ui;
            if (target != null) {
                target.onServerStopped();
            }
            notifyState();
            // Beklenmedik düşüş (istenen sunucu kapandı): bekçiyi beklemeden bir an sonra yeniden dene.
            if (manager.isDesired()) {
                main.removeCallbacks(retry);
                main.postDelayed(retry, RETRY_INITIAL_MS);
            }
        }

        @Override
        public void onError(String error) {
            Log.w(TAG, "Sunucu hatası: " + error);
            lastError = error;
            WebServerManager.WebServerListener target = ui;
            if (target != null) {
                target.onError(error);
            }
            notifyState();
        }

        @Override
        public void onLog(String message) {
            WebServerManager.WebServerListener target = ui;
            if (target != null) {
                target.onLog(message);
            } else {
                Log.d(TAG, message);
            }
        }

        @Override
        public void onInstallApk(String fileName) {
            dispatchOrQueue(l -> l.onInstallApk(fileName));
        }

        @Override
        public void onDeleteApp(String packageName) {
            WebServerManager.WebServerListener target = ui;
            if (target != null) {
                target.onDeleteApp(packageName);
                return;
            }
            main.post(() -> deleteHeadless(packageName));
        }

        @Override
        public void onOpenMapUrl(String url) {
            WebServerManager.WebServerListener target = ui;
            if (target != null) {
                target.onOpenMapUrl(url);
                return;
            }
            // Launcher modunda MapControl ekranı öne getirilmez: Geri tuşu haritadan launcher'a dönsün.
            main.post(() -> LinkOpener.open(app, url, msg -> Log.d(TAG, msg)));
        }

        @Override
        public void onLaunchApp(String packageName) {
            WebServerManager.WebServerListener target = ui;
            if (target != null) {
                target.onLaunchApp(packageName);
                return;
            }
            main.post(() -> launchHeadless(packageName));
        }

        @Override
        public void onTypeKeyboardText(String text, boolean showDeviceFeedback) {
            WebServerManager.WebServerListener target = ui;
            if (target != null) {
                target.onTypeKeyboardText(text, showDeviceFeedback);
                return;
            }
            main.post(() -> typeHeadless(text, showDeviceFeedback));
        }
    }

    /** Activity yokken uygulama başlatma: activity'nin kullandığı niyetle aynı (bkz. AppsTabBuilder.launchApp). */
    private void launchHeadless(String packageName) {
        try {
            Intent launch = app.getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch == null) {
                Log.w(TAG, "Uygulama açılamadı (launch intent yok): " + packageName);
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            app.startActivity(launch);
        } catch (RuntimeException e) {
            Log.w(TAG, "Uygulama açma hatası: " + packageName, e);
        }
    }

    /** Activity yokken uygulama silme: sistem kaldırma ekranı doğrudan açılır (bkz. AppsTabBuilder.deleteApp). */
    private void deleteHeadless(String packageName) {
        try {
            if (packageName == null || packageName.isEmpty() || app.getPackageName().equals(packageName)) {
                Log.w(TAG, "Bu uygulama silinemez: " + packageName);
                return;
            }
            ApplicationInfo info = app.getPackageManager().getApplicationInfo(packageName, 0);
            if ((info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
                Log.w(TAG, "Sistem uygulaması silinemez: " + packageName);
                return;
            }
            Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(intent);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Silinecek uygulama bulunamadı: " + packageName);
        } catch (RuntimeException e) {
            Log.w(TAG, "Uygulama silme hatası: " + packageName, e);
        }
    }

    private void typeHeadless(String text, boolean showDeviceFeedback) {
        try {
            if (!com.mapcontrol.service.GlobalBackService.isRegisteredInSystemAccessibilitySettings(app)) {
                if (showDeviceFeedback) {
                    Toast.makeText(app, "Ayarlar > Erişilebilirlik: Global Back servisini açın.", Toast.LENGTH_LONG).show();
                }
                return;
            }
            boolean ok = com.mapcontrol.service.GlobalBackService.typeIntoFocusedField(app, text);
            if (!ok && showDeviceFeedback) {
                Toast.makeText(app, "Metin eklenemedi. Hedef uygulamada metin alanının odakta olduğundan emin olun.",
                        Toast.LENGTH_LONG).show();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Metin yazılamadı", e);
        }
    }
}
