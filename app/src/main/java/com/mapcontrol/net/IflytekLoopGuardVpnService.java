package com.mapcontrol.net;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import com.mapcontrol.util.DiagJournal;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * iFlytek SONSUZ DÖNGÜ KORUMASI — yalnızca DNS'e dokunan, araç içinde çalışan yerel bir "VPN".
 *
 * <p><b>Neden var:</b> Chery'nin iFlytek sesli asistanı ({@code com.iflytek.cutefly.speechclient.hmi}, persistent,
 * uid 1000) her açılışta internet varsa {@code https://flamesdev.iflytekauto.cn/.../privacyAgreement/isLatestVersion}
 * adresine soruyor. Sunucu 200 + JSON nesnesi olmayan bir gövde dönüyor; uygulama bunu Gson'a verip
 * {@code JsonSyntaxException} ile çöküyor, sistem persistent olduğu için anında yeniden başlatıyor → döngü.
 * İstek <i>başarısız</i> olursa uygulamanın {@code onFailure} dalı sorunsuz; bu servis o alan adına NXDOMAIN
 * döndürerek isteği başarısız kılar. Ayrıntı: {@link IflytekLoopGuard}.
 *
 * <p><b>Dışarıya hiçbir şey gitmez:</b> sunucu/abonelik yok. Tünelin tek rotası sahte DNS adresi
 * {@link #DNS_ADDR}/32; normal trafik tünele hiç girmez. Diğer DNS sorguları aracın kendi ağının (Wi-Fi/TBOX)
 * DNS sunucusuna aynen iletilir. Always-on, lockdown KAPALI.
 *
 * <p><b>Güvenli kapanma:</b> ilk denemede (2026-11-27, T19C) araçta tüm DNS çöktü. Bu yüzden servis kendini sürekli
 * sınar: aracın kendi DNS'i cevap veriyorken tünel üzerinden çözümleme art arda başarısızsa VPN'i kapatır, ayarı
 * kapalıya çeker ve nedenini kaydeder ({@link IflytekLoopGuard#failSafe}). Araç asla internetsiz kalmamalı.
 */
public class IflytekLoopGuardVpnService extends VpnService {

    static final String ACTION_STOP = "com.mapcontrol.net.IFLYTEK_LOOP_GUARD_STOP";

    private static final String TAG = "iflytek-dongu";
    private static final String LOG = "IflytekLoopGuard";
    private static final String TUN_ADDR = "10.111.222.2";
    private static final String DNS_ADDR = "10.111.222.1";
    private static final int UPSTREAM_TIMEOUT_MS = 4000;
    private static final int MAX_PACKET = 32767;

    /** Sağlık testi: Android'in kendi bağlantı denetimi adresi; her araçta çözülebilir olmalı. */
    private static final String PROBE_NAME = "connectivitycheck.gstatic.com";
    private static final long PROBE_FIRST_DELAY_MS = 5_000;
    private static final long PROBE_INTERVAL_MS = 10_000;
    private static final long PROBE_STEADY_INTERVAL_MS = 60_000;
    /** Üst DNS çalışırken tünel üzerinden bu kadar ardışık başarısız sınamada kapanılır. */
    private static final int PROBE_MAX_FAILURES = 3;

    private ParcelFileDescriptor tun;
    private Thread reader;
    private Thread prober;
    private ExecutorService forwarders;
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback upstreamCallback;
    /** VPN DIŞI ağlar (Wi-Fi, TBOX): üst DNS buradan seçilir. VPN'in kendisi asla üst ağ olmaz. */
    private final Map<Network, Upstream> upstreams = new ConcurrentHashMap<>();
    private volatile int blockedCount;

    private static final class Upstream {
        volatile NetworkCapabilities caps;
        volatile List<InetAddress> dns;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            DiagJournal.event(TAG, "durduruldu (ayar kapatildi)");
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!IflytekLoopGuard.shouldRun(this)) {
            DiagJournal.event(TAG, "kosul yok (kapali/iFlytek yok/device owner degil), baslatilmadi");
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (tun == null) {
            start();
        }
        return START_STICKY;
    }

    private synchronized void start() {
        cm = getSystemService(ConnectivityManager.class);
        trackUpstreamNetworks();
        try {
            // Kendi uygulamamız da tünelde kalır: sağlık testi (InetAddress) gerçek yoldan, tünel üzerinden geçsin.
            // Üst DNS soketleri protect() + bindSocket ile tünelin dışına çıkar.
            tun = new Builder()
                    .setSession("MapControl iFlytek döngü koruması")
                    .addAddress(TUN_ADDR, 32)
                    .addDnsServer(DNS_ADDR)
                    .addRoute(DNS_ADDR, 32)
                    .setBlocking(true)
                    .establish();
        } catch (Exception e) {
            DiagJournal.event(TAG, "tun kurulamadi: " + e);
            tun = null;
        }
        if (tun == null) {
            untrackUpstreamNetworks();
            stopSelf();
            return;
        }
        forwarders = Executors.newFixedThreadPool(4);
        final ParcelFileDescriptor fd = tun;
        reader = new Thread(() -> readLoop(fd), "iflytek-loop-guard");
        reader.setDaemon(true);
        reader.start();
        prober = new Thread(() -> probeLoop(fd), "iflytek-loop-probe");
        prober.setDaemon(true);
        prober.start();
        DiagJournal.event(TAG, "basladi: " + String.join(",", IflytekLoopGuard.BLOCKED_DOMAINS) + " engelleniyor");
        Log.i(LOG, "basladi");
    }

    private void readLoop(ParcelFileDescriptor fd) {
        byte[] buf = new byte[MAX_PACKET];
        try (FileInputStream in = new FileInputStream(fd.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(fd.getFileDescriptor())) {
            while (!Thread.currentThread().isInterrupted() && tun == fd) {
                int n = in.read(buf);
                if (n < 0) {
                    break;
                }
                if (n == 0) {
                    continue;
                }
                DnsTunPackets.Query q = DnsTunPackets.parseQuery(buf, n);
                if (q == null) {
                    continue;
                }
                String name = DnsTunPackets.questionName(q.dns);
                if (DnsTunPackets.isBlocked(name, IflytekLoopGuard.BLOCKED_DOMAINS)) {
                    byte[] nx = DnsTunPackets.nxdomain(q.dns);
                    if (nx != null) {
                        write(out, DnsTunPackets.buildResponse(q, nx));
                    }
                    if (blockedCount++ == 0) {
                        DiagJournal.event(TAG, "iFlytek istegi engellendi: " + name);
                        Log.i(LOG, "iFlytek istegi engellendi: " + name);
                    }
                    continue;
                }
                ExecutorService ex = forwarders;
                if (ex != null && !ex.isShutdown()) {
                    try {
                        ex.execute(() -> forward(out, q));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (IOException e) {
            if (tun != null) {
                DiagJournal.event(TAG, "tun okuma bitti: " + e);
            }
        }
    }

    /** Engelli olmayan sorguyu aracın asıl ağının DNS'ine iletir; ulaşılamazsa SERVFAIL döner. */
    private void forward(FileOutputStream out, DnsTunPackets.Query q) {
        byte[] answer = askUpstream(q.dns);
        if (answer == null) {
            answer = DnsTunPackets.servfail(q.dns);
        }
        if (answer != null) {
            write(out, DnsTunPackets.buildResponse(q, answer));
        }
    }

    /** Sorguyu en iyi VPN dışı ağın DNS sunucularına sorar (önce IPv4). Hiçbiri cevap vermezse null. */
    private byte[] askUpstream(byte[] dns) {
        Network net = bestUpstream();
        if (net == null) {
            return null;
        }
        Upstream u = upstreams.get(net);
        List<InetAddress> servers = u == null ? null : u.dns;
        if (servers == null || servers.isEmpty()) {
            return null;
        }
        // Wi-Fi listesinde ilk sırada link-local IPv6 olabiliyor: önce IPv4 sunucular.
        for (int pass = 0; pass < 2; pass++) {
            for (InetAddress server : servers) {
                if ((server.getAddress().length == 4) != (pass == 0)) {
                    continue;
                }
                byte[] r = ask(net, server, dns);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private byte[] ask(Network net, InetAddress server, byte[] dns) {
        try (DatagramSocket s = new DatagramSocket()) {
            protect(s);
            net.bindSocket(s);
            s.setSoTimeout(UPSTREAM_TIMEOUT_MS);
            s.send(new DatagramPacket(dns, dns.length, server, 53));
            byte[] rb = new byte[4096];
            DatagramPacket resp = new DatagramPacket(rb, rb.length);
            s.receive(resp);
            byte[] r = new byte[resp.getLength()];
            System.arraycopy(rb, 0, r, 0, r.length);
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Uçtan uca sağlık testi. Önce aracın kendi DNS'ine doğrudan sorar (internet var mı); varsa aynı adı sistem
     * çözücüsüyle, yani tünel üzerinden çözer. Doğrudan çalışıp tünel art arda başarısızsa güvenli kapanma.
     * İnternet hiç yoksa (TBOX'suz, Wi-Fi'siz) karar vermez.
     */
    private void probeLoop(ParcelFileDescriptor fd) {
        long startedAt = SystemClock.elapsedRealtime();
        int failures = 0;
        boolean everPassed = false;
        try {
            Thread.sleep(PROBE_FIRST_DELAY_MS);
            while (!Thread.currentThread().isInterrupted() && tun == fd) {
                byte[] direct = askUpstream(DnsTunPackets.buildQuery(
                        ThreadLocalRandom.current().nextInt(0x10000), PROBE_NAME));
                if (DnsTunPackets.hasAnswer(direct)) {
                    boolean viaTunnel;
                    try {
                        viaTunnel = InetAddress.getAllByName(PROBE_NAME).length > 0;
                    } catch (Exception e) {
                        viaTunnel = false;
                    }
                    if (viaTunnel) {
                        if (!everPassed) {
                            long ms = SystemClock.elapsedRealtime() - startedAt;
                            DiagJournal.event(TAG, "saglik testi gecti (" + ms + " ms)");
                            Log.i(LOG, "saglik testi gecti");
                        }
                        everPassed = true;
                        failures = 0;
                    } else {
                        failures++;
                        Log.w(LOG, "saglik testi basarisiz " + failures + "/" + PROBE_MAX_FAILURES
                                + " ust=" + describeUpstream());
                        if (failures >= PROBE_MAX_FAILURES) {
                            IflytekLoopGuard.failSafe(this, "tunel DNS cozemiyor, aracin DNS'i calisiyor ("
                                    + describeUpstream() + ")");
                            shutdown();
                            stopSelf();
                            return;
                        }
                    }
                }
                Thread.sleep(everPassed && failures == 0 ? PROBE_STEADY_INTERVAL_MS : PROBE_INTERVAL_MS);
            }
        } catch (InterruptedException ignored) {
        }
    }

    private void write(FileOutputStream out, byte[] pkt) {
        synchronized (out) {
            try {
                out.write(pkt);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * VPN dışı ağları izler. {@code registerDefaultNetworkCallback} kullanılmaz: VPN açıkken "varsayılan ağ" VPN'in
     * kendisi olabilir ve DNS'i kendimize iletip döngüye girer (ilk denemede tüm DNS'in çökmesinin olası nedeni).
     */
    private void trackUpstreamNetworks() {
        if (cm == null || upstreamCallback != null) {
            return;
        }
        upstreamCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return;
                }
                upstreams.computeIfAbsent(network, n -> new Upstream()).caps = caps;
            }

            @Override
            public void onLinkPropertiesChanged(Network network, LinkProperties lp) {
                Upstream u = upstreams.get(network);
                if (u == null) {
                    return;
                }
                u.dns = lp.getDnsServers();
                Log.i(LOG, "ust ag " + network + " " + lp.getInterfaceName() + " dns=" + u.dns);
            }

            @Override
            public void onLost(Network network) {
                upstreams.remove(network);
            }
        };
        try {
            NetworkRequest req = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build();
            cm.registerNetworkCallback(req, upstreamCallback);
        } catch (Exception e) {
            DiagJournal.event(TAG, "ag takibi kurulamadi: " + e);
            upstreamCallback = null;
        }
    }

    /** Doğrulanmış Wi-Fi > doğrulanmış diğer (TBOX) > DNS'i olan herhangi bir VPN dışı ağ. */
    private Network bestUpstream() {
        Network best = null;
        int bestScore = -1;
        for (Map.Entry<Network, Upstream> e : upstreams.entrySet()) {
            Upstream u = e.getValue();
            NetworkCapabilities c = u.caps;
            if (c == null || u.dns == null || u.dns.isEmpty()) {
                continue;
            }
            int score = 0;
            if (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                score += 2;
            }
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                score += 1;
            }
            if (score > bestScore) {
                bestScore = score;
                best = e.getKey();
            }
        }
        return best;
    }

    private String describeUpstream() {
        Network n = bestUpstream();
        if (n == null) {
            return "yok (" + upstreams.size() + " ag izleniyor)";
        }
        Upstream u = upstreams.get(n);
        return n + " dns=" + (u == null ? null : u.dns);
    }

    private void untrackUpstreamNetworks() {
        if (cm != null && upstreamCallback != null) {
            try {
                cm.unregisterNetworkCallback(upstreamCallback);
            } catch (Exception ignored) {
            }
        }
        upstreamCallback = null;
        upstreams.clear();
    }

    private synchronized void shutdown() {
        ParcelFileDescriptor fd = tun;
        tun = null;
        if (reader != null) {
            reader.interrupt();
            reader = null;
        }
        if (prober != null && prober != Thread.currentThread()) {
            prober.interrupt();
        }
        prober = null;
        if (forwarders != null) {
            forwarders.shutdownNow();
            forwarders = null;
        }
        if (fd != null) {
            try {
                fd.close();
            } catch (IOException ignored) {
            }
        }
        untrackUpstreamNetworks();
    }

    @Override
    public void onRevoke() {
        DiagJournal.event(TAG, "sistem VPN iznini geri aldi");
        shutdown();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }
}
