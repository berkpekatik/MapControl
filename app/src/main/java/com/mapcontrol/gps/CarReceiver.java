package com.mapcontrol.gps;

import android.content.Context;
import android.os.SystemClock;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;

/** Telefonun şifreli konumlarını dinler ve Android'in GPS sağlayıcısına iletir. */
final class CarReceiver implements LinkEngine {

    private static final int RECEIVE_TIMEOUT_MS = 500;

    private final MockGpsProvider provider;
    private final byte[] key;
    private final Host host;
    private final PeerGate gate = new PeerGate();

    private volatile boolean running;
    private volatile DatagramSocket socket;
    private Thread thread;
    private long lastFixAt;

    CarReceiver(Context context, byte[] key, Host host) {
        this.provider = new MockGpsProvider(context);
        this.key = key;
        this.host = host;
    }

    @Override
    public void start() {
        running = true;
        thread = new Thread(this::run, "MapControlGPS-recv");
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        DatagramSocket current = socket;
        if (current != null) {
            current.close();
        }
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        provider.release();
    }

    private void run() {
        try (DatagramSocket udp = new DatagramSocket((SocketAddress) null)) {
            udp.setReuseAddress(true);
            udp.bind(new InetSocketAddress(Wire.PORT));
            udp.setSoTimeout(RECEIVE_TIMEOUT_MS);
            socket = udp;
            listen(udp);
        } catch (IOException e) {
            if (running) {
                running = false;
                host.onFatal(LinkStatus.NETWORK_ERROR);
            }
        }
    }

    private void listen(DatagramSocket udp) throws IOException {
        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (running) {
            packet.setLength(buffer.length);
            try {
                udp.receive(packet);
                handle(udp, packet, buffer);
            } catch (SocketTimeoutException idle) {
                // nothing arrived; check for silence below
            }
            releaseWhenSilent();
        }
    }

    private void handle(DatagramSocket udp, DatagramPacket packet, byte[] buffer) {
        if (!packet.getAddress().isSiteLocalAddress()) {
            return;
        }
        Wire.Frame fix;
        try {
            fix = Wire.decode(buffer, packet.getLength(), key);
        } catch (GeneralSecurityException | IOException notOurs) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (!Wire.isValidFix(fix) || !gate.accept(fix.client, fix.seq, now)) {
            return;
        }
        try {
            provider.publish(fix);
        } catch (SecurityException notAllowed) {
            running = false;
            host.onFatal(LinkStatus.NEEDS_MOCK);
            return;
        } catch (RuntimeException rejected) {
            return;
        }
        lastFixAt = now;
        LinkState.update(LinkStatus.LIVE);
        LinkState.setFix(fix.lat, fix.lon, fix.accuracy);
        acknowledge(udp, packet, fix);
    }

    private void acknowledge(DatagramSocket udp, DatagramPacket from, Wire.Frame fix) {
        Wire.Frame ack = new Wire.Frame();
        ack.type = Wire.TYPE_ACK;
        ack.client = fix.client;
        ack.seq = fix.seq;
        try {
            byte[] reply = Wire.encode(ack, key);
            udp.send(new DatagramPacket(reply, reply.length, from.getAddress(), from.getPort()));
        } catch (GeneralSecurityException | IOException e) {
            // the phone retries with the next fix
        }
    }

    private void releaseWhenSilent() {
        if (lastFixAt != 0 && SystemClock.elapsedRealtime() - lastFixAt > Wire.STALE_MS) {
            provider.release();
            lastFixAt = 0;
            LinkState.update(LinkStatus.LOST);
        }
    }
}
