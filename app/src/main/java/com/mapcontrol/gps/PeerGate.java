package com.mapcontrol.gps;

/**
 * Aynı anda tek telefon oturumundan, yalnızca artan sıra numarasıyla paket kabul eder;
 * oturum içindeki yinelenen ve tekrar oynatılan paketler düşer.
 */
final class PeerGate {

    private long client;
    private long lastSeq;
    private long lastSeenMs;

    boolean accept(long clientId, long seq, long nowMs) {
        if (clientId != client) {
            if (client != 0 && nowMs - lastSeenMs < Wire.STALE_MS) {
                return false;
            }
            client = clientId;
            lastSeq = 0;
        }
        if (seq <= lastSeq) {
            return false;
        }
        lastSeq = seq;
        lastSeenMs = nowMs;
        return true;
    }
}
