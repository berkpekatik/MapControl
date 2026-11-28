package com.mapcontrol.net;

import java.util.Locale;

/**
 * {@link IflytekLoopGuardVpnService} için tun arayüzünden gelen IPv4/UDP DNS paketlerini çözer ve cevap paketi kurar.
 * Android'e bağımlı değildir (birim testli). Yalnızca iFlytek döngü korumasının ihtiyacı kadarını yapar:
 * IPv4 + UDP + tek soru; geri kalan her şey {@code null} döner ve sessizce düşürülür.
 */
public final class DnsTunPackets {

    private static final int IP_HEADER_MIN = 20;
    private static final int UDP_HEADER = 8;
    private static final int DNS_HEADER = 12;
    private static final int PROTO_UDP = 17;

    private DnsTunPackets() {
    }

    /** tun'dan okunan bir DNS sorgusu: adresler ve DNS yükü (payload). */
    public static final class Query {
        public final byte[] srcAddr;
        public final byte[] dstAddr;
        public final int srcPort;
        public final int dstPort;
        public final byte[] dns;

        Query(byte[] srcAddr, byte[] dstAddr, int srcPort, int dstPort, byte[] dns) {
            this.srcAddr = srcAddr;
            this.dstAddr = dstAddr;
            this.srcPort = srcPort;
            this.dstPort = dstPort;
            this.dns = dns;
        }
    }

    /** IPv4/UDP paketinden DNS sorgusunu çıkarır; {@code dstPort} 53 değilse ya da paket bozuksa {@code null}. */
    public static Query parseQuery(byte[] pkt, int len) {
        if (pkt == null || len < IP_HEADER_MIN + UDP_HEADER + DNS_HEADER || len > pkt.length) {
            return null;
        }
        if ((pkt[0] >> 4 & 0xF) != 4) {
            return null;
        }
        int ihl = (pkt[0] & 0xF) * 4;
        if (ihl < IP_HEADER_MIN || len < ihl + UDP_HEADER + DNS_HEADER) {
            return null;
        }
        if ((pkt[9] & 0xFF) != PROTO_UDP) {
            return null;
        }
        // Parçalanmış (fragment) paket: DNS sorgusu için beklenmez, düşür.
        if ((u16(pkt, 6) & 0x3FFF) != 0) {
            return null;
        }
        int totalLen = Math.min(u16(pkt, 2), len);
        int udpLen = u16(pkt, ihl + 4);
        int dnsLen = Math.min(udpLen, totalLen - ihl) - UDP_HEADER;
        if (dnsLen < DNS_HEADER) {
            return null;
        }
        int dstPort = u16(pkt, ihl + 2);
        if (dstPort != 53) {
            return null;
        }
        byte[] src = slice(pkt, 12, 4);
        byte[] dst = slice(pkt, 16, 4);
        byte[] dns = slice(pkt, ihl + UDP_HEADER, dnsLen);
        return new Query(src, dst, u16(pkt, ihl), dstPort, dns);
    }

    /**
     * DNS mesajındaki ilk sorunun adını küçük harfle döner (sondaki nokta olmadan).
     * Sıkıştırma işaretçisi (sorguda olmaz) ya da bozuk ad için {@code null}.
     */
    public static String questionName(byte[] dns) {
        if (dns == null || dns.length < DNS_HEADER || u16(dns, 4) < 1) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int i = DNS_HEADER;
        while (true) {
            if (i >= dns.length) {
                return null;
            }
            int l = dns[i] & 0xFF;
            if (l == 0) {
                break;
            }
            if ((l & 0xC0) != 0 || i + 1 + l > dns.length || sb.length() + l > 253) {
                return null;
            }
            if (sb.length() > 0) {
                sb.append('.');
            }
            for (int k = 0; k < l; k++) {
                sb.append((char) (dns[i + 1 + k] & 0xFF));
            }
            i += 1 + l;
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** İlk sorunun bittiği ofset (ad + QTYPE + QCLASS); bozuksa -1. */
    static int questionEnd(byte[] dns) {
        int i = DNS_HEADER;
        while (i < dns.length) {
            int l = dns[i] & 0xFF;
            if (l == 0) {
                int end = i + 1 + 4;
                return end <= dns.length ? end : -1;
            }
            if ((l & 0xC0) != 0) {
                return -1;
            }
            i += 1 + l;
        }
        return -1;
    }

    /** {@code name} engelli listedeki bir alan adı ya da onun alt alan adı mı. */
    public static boolean isBlocked(String name, String[] blocked) {
        if (name == null) {
            return false;
        }
        for (String b : blocked) {
            if (name.equals(b) || name.endsWith("." + b)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sorguya NXDOMAIN cevabı: aynı ID ve soru, QR=1, RA=1, RD kopyalanır, RCODE=3, cevap/yetki/ek kayıt yok.
     * Soru çözülemezse {@code null}.
     */
    public static byte[] nxdomain(byte[] query) {
        int qEnd = questionEnd(query);
        if (qEnd < 0) {
            return null;
        }
        byte[] r = new byte[qEnd];
        System.arraycopy(query, 0, r, 0, qEnd);
        r[2] = (byte) (0x80 | (query[2] & 0x79)); // QR=1, opcode ve RD korunur, AA/TC temiz
        r[3] = (byte) (0x80 | 0x03);               // RA=1, RCODE=NXDOMAIN
        r[4] = 0;
        r[5] = 1;                                  // QDCOUNT=1
        for (int i = 6; i < DNS_HEADER; i++) {
            r[i] = 0;                              // AN/NS/AR = 0
        }
        return r;
    }

    /** Sorguya SERVFAIL cevabı (üst DNS'e ulaşılamadığında çözücü beklemesin diye). */
    public static byte[] servfail(byte[] query) {
        byte[] r = nxdomain(query);
        if (r != null) {
            r[3] = (byte) (0x80 | 0x02);
        }
        return r;
    }

    /** Sağlık testi için tek sorulu, RD=1, A/IN DNS sorgusu. */
    public static byte[] buildQuery(int id, String name) {
        String[] labels = name.split("\\.");
        int len = DNS_HEADER + 1 + 4;
        for (String l : labels) {
            len += 1 + l.length();
        }
        byte[] q = new byte[len];
        put16(q, 0, id & 0xFFFF);
        q[2] = 0x01;          // RD
        put16(q, 4, 1);       // QDCOUNT
        int i = DNS_HEADER;
        for (String l : labels) {
            q[i++] = (byte) l.length();
            for (int k = 0; k < l.length(); k++) {
                q[i++] = (byte) l.charAt(k);
            }
        }
        q[i++] = 0;
        put16(q, i, 1);       // QTYPE A
        put16(q, i + 2, 1);   // QCLASS IN
        return q;
    }

    /** Cevap başarılı mı: QR=1, RCODE=0 ve en az bir cevap kaydı. */
    public static boolean hasAnswer(byte[] r) {
        return r != null && r.length >= DNS_HEADER
                && (r[2] & 0x80) != 0
                && (r[3] & 0x0F) == 0
                && u16(r, 6) > 0;
    }

    /** Sorgunun adreslerini ters çevirip {@code dns} yükünü taşıyan IPv4/UDP cevap paketi (UDP checksum 0 = yok). */
    public static byte[] buildResponse(Query q, byte[] dns) {
        int total = IP_HEADER_MIN + UDP_HEADER + dns.length;
        byte[] p = new byte[total];
        p[0] = 0x45;
        put16(p, 2, total);
        p[8] = 64;
        p[9] = PROTO_UDP;
        System.arraycopy(q.dstAddr, 0, p, 12, 4);
        System.arraycopy(q.srcAddr, 0, p, 16, 4);
        put16(p, 10, ipChecksum(p, IP_HEADER_MIN));
        put16(p, IP_HEADER_MIN, q.dstPort);
        put16(p, IP_HEADER_MIN + 2, q.srcPort);
        put16(p, IP_HEADER_MIN + 4, UDP_HEADER + dns.length);
        System.arraycopy(dns, 0, p, IP_HEADER_MIN + UDP_HEADER, dns.length);
        return p;
    }

    static int ipChecksum(byte[] p, int headerLen) {
        long sum = 0;
        for (int i = 0; i < headerLen; i += 2) {
            sum += u16(p, i);
        }
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (int) (~sum & 0xFFFF);
    }

    private static int u16(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    private static void put16(byte[] b, int off, int v) {
        b[off] = (byte) (v >> 8);
        b[off + 1] = (byte) v;
    }

    private static byte[] slice(byte[] b, int off, int len) {
        byte[] r = new byte[len];
        System.arraycopy(b, off, r, 0, len);
        return r;
    }
}
