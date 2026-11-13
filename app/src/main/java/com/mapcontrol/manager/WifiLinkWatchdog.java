package com.mapcontrol.manager;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import com.mapcontrol.util.NetworkWifiHelper;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileReader;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wi-Fi istemci bağlantısının gerçekten çalıştığını denetler ve takılı kalmış bağlantıyı onarır (kararlar
 * {@link WifiLinkHealth}'te). {@link WebServerHost} bekçisi her turda {@link #tick()} çağırır; yoklama ve onarım
 * kendi arka plan iş parçacığında yapılır.
 *
 * <p>Yoklama, Wi-Fi ağına bağlanmış soketlerle ağ geçidine yapılır (varsayılan ağ aracın kendi modemi olsa bile):
 * TCP 53 ve 80 (bağlantı reddi de "canlı" sayılır: paket gidip geldi), ICMP echo. Hepsi başarısızsa ARP tablosuna
 * bakılır; ağ geçidinin MAC'i çözülmüşse bağlantı canlı sayılır (paketleri sessizce düşüren modemlerde yanlış alarm
 * olmasın).
 */
final class WifiLinkWatchdog {

    private static final String TAG = "WifiLinkWatchdog";
    private static final int TCP_TIMEOUT_MS = 2_500;
    private static final int ICMP_TIMEOUT_MS = 2_000;
    private static final int[] TCP_PORTS = {53, 80};
    private static final long TOGGLE_OFF_WAIT_MS = 5_000L;

    private final Context app;
    private final Runnable onRecovered;
    private final WifiLinkHealth health = new WifiLinkHealth();
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, TAG);
        t.setDaemon(true);
        return t;
    });

    /** @param onRecovered onarımdan sonra bağlantı geri gelince (arka plan iş parçacığında) çağrılır */
    WifiLinkWatchdog(Context app, Runnable onRecovered) {
        this.app = app.getApplicationContext();
        this.onRecovered = onRecovered;
    }

    /** Bir yoklama turu başlatır; önceki tur sürüyorsa atlanır. */
    void tick() {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        worker.execute(() -> {
            try {
                runOnce();
            } catch (RuntimeException e) {
                Log.w(TAG, "Wi-Fi sağlık denetimi hatası", e);
            } finally {
                busy.set(false);
            }
        });
    }

    private void runOnce() {
        Link link = findWifiLink();
        if (link == null) {
            health.onProbe(null, false, SystemClock.elapsedRealtime());
            return;
        }
        boolean alive = probe(link);
        Log.d(TAG, "Yoklama " + link.key() + ": " + (alive ? "canlı" : "cevap yok"));
        WifiLinkHealth.Action action = health.onProbe(link.key(), alive, SystemClock.elapsedRealtime());
        switch (action) {
            case NONE:
                return;
            case RECOVERED:
                Log.i(TAG, "Wi-Fi bağlantısı onarıldı (" + link.key() + ")");
                onRecovered.run();
                return;
            default:
                Log.w(TAG, "Ağ geçidine ulaşılamıyor (" + link.key() + "), onarım: " + action);
                repair(action);
        }
    }

    // ---- bağlantıyı bulma -----------------------------------------------

    private static final class Link {
        final Network network;
        final String iface;
        final Inet4Address gateway;

        Link(Network network, String iface, Inet4Address gateway) {
            this.network = network;
            this.iface = iface;
            this.gateway = gateway;
        }

        String key() {
            return iface + "/" + gateway.getHostAddress();
        }
    }

    /** IPv4 varsayılan rotası olan Wi-Fi istemci ağı; yoksa null. */
    private Link findWifiLink() {
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return null;
        }
        for (Network network : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                continue;
            }
            LinkProperties lp = cm.getLinkProperties(network);
            if (lp == null || lp.getInterfaceName() == null) {
                continue;
            }
            for (RouteInfo route : lp.getRoutes()) {
                InetAddress gw = route.getGateway();
                if (route.isDefaultRoute() && gw instanceof Inet4Address && !gw.isAnyLocalAddress()) {
                    return new Link(network, lp.getInterfaceName(), (Inet4Address) gw);
                }
            }
        }
        return null;
    }

    // ---- yoklama --------------------------------------------------------

    private boolean probe(Link link) {
        for (int port : TCP_PORTS) {
            if (tcpReachable(link, port)) {
                return true;
            }
        }
        if (icmpReachable(link)) {
            return true;
        }
        return arpResolved(link);
    }

    /** Bağlantı kuruldu ya da reddedildi: ağ geçidi cevap verdi. Zaman aşımı / ulaşılamaz: cevap yok. */
    private static boolean tcpReachable(Link link, int port) {
        Socket socket = null;
        try {
            socket = link.network.getSocketFactory().createSocket();
            socket.connect(new InetSocketAddress(link.gateway, port), TCP_TIMEOUT_MS);
            return true;
        } catch (ConnectException e) {
            String msg = String.valueOf(e.getMessage());
            return msg.contains("ECONNREFUSED") || msg.contains("refused");
        } catch (Exception e) {
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                    // kapalı
                }
            }
        }
    }

    /** ICMP echo (ayrıcalıksız "ping" soketi, Wi-Fi ağına bağlı). Soket açılamazsa false. */
    private static boolean icmpReachable(Link link) {
        FileDescriptor fd = null;
        try {
            fd = Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMP);
            link.network.bindSocket(fd);
            // type 8 (echo), code 0; sağlama toplamı ve kimlik çekirdekte doldurulur
            byte[] packet = new byte[16];
            packet[0] = 8;
            packet[7] = 1;
            Os.sendto(fd, packet, 0, packet.length, 0, link.gateway, 0);
            StructPollfd pfd = new StructPollfd();
            pfd.fd = fd;
            pfd.events = (short) OsConstants.POLLIN;
            long deadline = SystemClock.elapsedRealtime() + ICMP_TIMEOUT_MS;
            byte[] reply = new byte[64];
            while (true) {
                int left = (int) (deadline - SystemClock.elapsedRealtime());
                if (left <= 0 || Os.poll(new StructPollfd[]{pfd}, left) <= 0) {
                    return false;
                }
                int n = Os.recvfrom(fd, reply, 0, reply.length, 0, null);
                if (n > 0 && reply[0] == 0) { // echo reply
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (fd != null) {
                try {
                    Os.close(fd);
                } catch (Exception ignored) {
                    // kapalı
                }
            }
        }
    }

    /** /proc/net/arp'ta ağ geçidinin MAC'i bu arayüzde çözülmüş mü (flags 0x0 = çözülemedi). Okunamazsa false. */
    private static boolean arpResolved(Link link) {
        String ip = link.gateway.getHostAddress();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/net/arp"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.trim().split("\\s+");
                if (cols.length >= 6 && cols[0].equals(ip) && cols[5].equals(link.iface)) {
                    return !"0x0".equals(cols[2]) && !"00:00:00:00:00:00".equals(cols[3]);
                }
            }
        } catch (Exception ignored) {
            // izin yok ya da dosya yok
        }
        return false;
    }

    // ---- onarım ---------------------------------------------------------

    @SuppressWarnings("deprecation")
    private void repair(WifiLinkHealth.Action action) {
        WifiManager wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
        if (wm == null) {
            return;
        }
        try {
            switch (action) {
                case REASSOCIATE:
                    wm.reassociate();
                    break;
                case RECONNECT:
                    wm.disconnect();
                    SystemClock.sleep(1_000L);
                    wm.reconnect();
                    break;
                case TOGGLE:
                    // Araç kendi erişim noktasını da yayınlıyorsa (telefon projeksiyonu) radyoya dokunulmaz.
                    if (!Boolean.TRUE.equals(NetworkWifiHelper.isAccessPointOff(app))) {
                        Log.w(TAG, "Erişim noktası açık ya da durumu okunamadı; Wi-Fi kapat-aç atlandı");
                        wm.disconnect();
                        SystemClock.sleep(1_000L);
                        wm.reconnect();
                        break;
                    }
                    wm.setWifiEnabled(false);
                    long deadline = SystemClock.elapsedRealtime() + TOGGLE_OFF_WAIT_MS;
                    while (wm.getWifiState() != WifiManager.WIFI_STATE_DISABLED
                            && SystemClock.elapsedRealtime() < deadline) {
                        SystemClock.sleep(250L);
                    }
                    wm.setWifiEnabled(true);
                    break;
                default:
                    break;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Wi-Fi onarımı başarısız: " + action, e);
        }
    }
}
