package com.mapcontrol.net;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;

public class DnsTunPacketsTest {

    private static final String[] BLOCKED = IflytekLoopGuard.BLOCKED_DOMAINS;

    /** id=0x1234, RD=1, tek soru: name A IN. */
    private static byte[] dnsQuery(String name) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0x12); b.write(0x34); b.write(0x01); b.write(0x00);
        b.write(0); b.write(1); b.write(0); b.write(0); b.write(0); b.write(0); b.write(0); b.write(0);
        for (String label : name.split("\\.")) {
            b.write(label.length());
            for (char c : label.toCharArray()) {
                b.write(c);
            }
        }
        b.write(0);
        b.write(0); b.write(1); b.write(0); b.write(1);
        return b.toByteArray();
    }

    private static byte[] ipUdp(byte[] dns, int dstPort) {
        int total = 28 + dns.length;
        byte[] p = new byte[total];
        p[0] = 0x45;
        p[2] = (byte) (total >> 8); p[3] = (byte) total;
        p[8] = 64; p[9] = 17;
        p[12] = 10; p[13] = 111; p[14] = (byte) 222; p[15] = 2;
        p[16] = 10; p[17] = 111; p[18] = (byte) 222; p[19] = 1;
        p[20] = (byte) 0xA1; p[21] = 0x22;
        p[22] = (byte) (dstPort >> 8); p[23] = (byte) dstPort;
        p[24] = (byte) ((8 + dns.length) >> 8); p[25] = (byte) (8 + dns.length);
        System.arraycopy(dns, 0, p, 28, dns.length);
        return p;
    }

    @Test
    public void parsesQueryAndName() {
        byte[] dns = dnsQuery("FlamesDev.iflytekauto.cn");
        byte[] pkt = ipUdp(dns, 53);
        DnsTunPackets.Query q = DnsTunPackets.parseQuery(pkt, pkt.length);
        assertNotNull(q);
        assertEquals(0xA122, q.srcPort);
        assertArrayEquals(dns, q.dns);
        assertEquals("flamesdev.iflytekauto.cn", DnsTunPackets.questionName(q.dns));
    }

    @Test
    public void ignoresNonDnsAndJunk() {
        byte[] pkt = ipUdp(dnsQuery("example.com"), 443);
        assertNull(DnsTunPackets.parseQuery(pkt, pkt.length));
        pkt[0] = 0x60; // IPv6
        assertNull(DnsTunPackets.parseQuery(pkt, pkt.length));
        assertNull(DnsTunPackets.parseQuery(new byte[10], 10));
        byte[] tcp = ipUdp(dnsQuery("example.com"), 53);
        tcp[9] = 6;
        assertNull(DnsTunPackets.parseQuery(tcp, tcp.length));
    }

    @Test
    public void blocksOnlyIflytekDomainAndSubdomains() {
        assertTrue(DnsTunPackets.isBlocked("flamesdev.iflytekauto.cn", BLOCKED));
        assertTrue(DnsTunPackets.isBlocked("x.flamesdev.iflytekauto.cn", BLOCKED));
        assertFalse(DnsTunPackets.isBlocked("iflytekauto.cn", BLOCKED));
        assertFalse(DnsTunPackets.isBlocked("notflamesdev.iflytekauto.cn", BLOCKED));
        assertFalse(DnsTunPackets.isBlocked("google.com", BLOCKED));
        assertFalse(DnsTunPackets.isBlocked(null, BLOCKED));
    }

    @Test
    public void nxdomainKeepsIdAndQuestion() {
        byte[] q = dnsQuery("flamesdev.iflytekauto.cn");
        byte[] r = DnsTunPackets.nxdomain(q);
        assertNotNull(r);
        assertEquals(q.length, r.length);
        assertEquals(0x12, r[0] & 0xFF);
        assertEquals(0x34, r[1] & 0xFF);
        assertEquals(0x81, r[2] & 0xFF); // QR + RD
        assertEquals(0x83, r[3] & 0xFF); // RA + NXDOMAIN
        assertEquals(1, r[5]);
        for (int i = 6; i < 12; i++) {
            assertEquals(0, r[i]);
        }
        for (int i = 12; i < q.length; i++) {
            assertEquals(q[i], r[i]);
        }
        assertEquals(0x82, DnsTunPackets.servfail(q)[3] & 0xFF);
    }

    @Test
    public void responsePacketSwapsAddressesAndHasValidChecksum() {
        byte[] dns = dnsQuery("flamesdev.iflytekauto.cn");
        byte[] pkt = ipUdp(dns, 53);
        DnsTunPackets.Query q = DnsTunPackets.parseQuery(pkt, pkt.length);
        byte[] nx = DnsTunPackets.nxdomain(q.dns);
        byte[] r = DnsTunPackets.buildResponse(q, nx);
        assertEquals(28 + nx.length, r.length);
        assertEquals(1, r[15]);           // src = 10.111.222.1
        assertEquals(2, r[19]);           // dst = 10.111.222.2
        assertEquals(0, r[20]);           // src port 53
        assertEquals(53, r[21]);
        assertEquals((byte) 0xA1, r[22]); // dst port = sorgunun kaynak portu
        assertEquals(0x22, r[23]);
        assertEquals(0, DnsTunPackets.ipChecksum(r, 20)); // geçerli başlıkta toplam 0 olur
        DnsTunPackets.Query back = DnsTunPackets.parseQuery(r, r.length);
        assertNull(back); // cevap 53'e değil, sorgunun portuna gider
    }

    @Test
    public void buildQueryMatchesWireFormat() {
        assertArrayEquals(dnsQuery("connectivitycheck.gstatic.com"),
                DnsTunPackets.buildQuery(0x1234, "connectivitycheck.gstatic.com"));
    }

    @Test
    public void hasAnswerOnlyForSuccessfulResponses() {
        byte[] q = DnsTunPackets.buildQuery(1, "example.com");
        assertFalse(DnsTunPackets.hasAnswer(null));
        assertFalse(DnsTunPackets.hasAnswer(q));                          // QR=0
        assertFalse(DnsTunPackets.hasAnswer(DnsTunPackets.nxdomain(q)));  // RCODE=3
        assertFalse(DnsTunPackets.hasAnswer(DnsTunPackets.servfail(q)));  // RCODE=2
        byte[] ok = DnsTunPackets.nxdomain(q);
        ok[3] = (byte) 0x80;
        assertFalse(DnsTunPackets.hasAnswer(ok));                         // NOERROR ama cevap yok
        ok[7] = 1;
        assertTrue(DnsTunPackets.hasAnswer(ok));
    }

    @Test
    public void rejectsTruncatedName() {
        byte[] q = dnsQuery("flamesdev.iflytekauto.cn");
        byte[] cut = new byte[16];
        System.arraycopy(q, 0, cut, 0, cut.length);
        assertNull(DnsTunPackets.questionName(cut));
        assertNull(DnsTunPackets.nxdomain(cut));
    }
}
