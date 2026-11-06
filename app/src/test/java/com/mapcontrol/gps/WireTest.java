package com.mapcontrol.gps;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.junit.Test;

public class WireTest {

    private static final String KEY_HEX = "e4a1f893d20c4e12b79a834f8261ad90";
    private final byte[] key = Wire.hexToKey(KEY_HEX);

    private static Wire.Frame fix() {
        Wire.Frame f = new Wire.Frame();
        f.type = Wire.TYPE_FIX;
        f.client = 42;
        f.seq = 7;
        f.ageMs = 400;
        f.lat = 41.0082;
        f.lon = 28.9784;
        f.accuracy = 4.5f;
        f.altitude = 55.0;
        f.speed = 12.5f;
        f.bearing = 180f;
        return f;
    }

    @Test
    public void fixRoundTrip() throws Exception {
        byte[] packet = Wire.encode(fix(), key);
        Wire.Frame back = Wire.decode(packet, packet.length, key);
        assertEquals(Wire.TYPE_FIX, back.type);
        assertEquals(42, back.client);
        assertEquals(7, back.seq);
        assertEquals(400, back.ageMs);
        assertEquals(41.0082, back.lat, 0.0);
        assertEquals(28.9784, back.lon, 0.0);
        assertEquals(4.5f, back.accuracy, 0f);
        assertEquals(55.0, back.altitude, 0.0);
        assertEquals(12.5f, back.speed, 0f);
        assertEquals(180f, back.bearing, 0f);
    }

    @Test
    public void ackRoundTripKeepsMissingFieldsOut() throws Exception {
        Wire.Frame ack = new Wire.Frame();
        ack.type = Wire.TYPE_ACK;
        ack.client = 42;
        ack.seq = 9;
        byte[] packet = Wire.encode(ack, key);
        Wire.Frame back = Wire.decode(packet, packet.length, key);
        assertEquals(Wire.TYPE_ACK, back.type);
        assertEquals(9, back.seq);
    }

    @Test
    public void optionalFieldsSurviveAsNaN() throws Exception {
        Wire.Frame f = fix();
        f.altitude = Double.NaN;
        f.speed = Float.NaN;
        f.bearing = Float.NaN;
        byte[] packet = Wire.encode(f, key);
        Wire.Frame back = Wire.decode(packet, packet.length, key);
        assertTrue(Double.isNaN(back.altitude));
        assertTrue(Float.isNaN(back.speed));
        assertTrue(Float.isNaN(back.bearing));
        assertTrue(Wire.isValidFix(back));
    }

    @Test
    public void packetsAreRandomizedAndNotPlaintext() throws Exception {
        byte[] a = Wire.encode(fix(), key);
        byte[] b = Wire.encode(fix(), key);
        assertFalse(java.util.Arrays.equals(a, b));
        assertTrue(a.length >= 46 && a.length <= 256);
    }

    @Test
    public void tamperedPacketIsRejected() throws Exception {
        byte[] packet = Wire.encode(fix(), key);
        packet[packet.length - 1] ^= 0x01;
        try {
            Wire.decode(packet, packet.length, key);
            fail("expected rejection");
        } catch (GeneralSecurityException expected) {
            // authentication tag mismatch
        }
    }

    @Test
    public void wrongKeyIsRejected() throws Exception {
        byte[] packet = Wire.encode(fix(), key);
        try {
            Wire.decode(packet, packet.length, Wire.hexToKey("00000000000000000000000000000001"));
            fail("expected rejection");
        } catch (GeneralSecurityException expected) {
            // different key
        }
    }

    @Test
    public void badSizesAreRejected() {
        try {
            Wire.decode(new byte[10], 10, key);
            fail("expected rejection");
        } catch (IOException | GeneralSecurityException expected) {
            // too short
        }
        try {
            Wire.decode(new byte[300], 300, key);
            fail("expected rejection");
        } catch (IOException | GeneralSecurityException expected) {
            // too long
        }
    }

    @Test
    public void fixValidation() {
        assertTrue(Wire.isValidFix(fix()));

        Wire.Frame old = fix();
        old.ageMs = Wire.MAX_AGE_MS + 1;
        assertFalse(Wire.isValidFix(old));

        Wire.Frame inaccurate = fix();
        inaccurate.accuracy = 150f;
        assertFalse(Wire.isValidFix(inaccurate));

        Wire.Frame noSeq = fix();
        noSeq.seq = 0;
        assertFalse(Wire.isValidFix(noSeq));

        Wire.Frame badLat = fix();
        badLat.lat = 91;
        assertFalse(Wire.isValidFix(badLat));

        Wire.Frame nan = fix();
        nan.lon = Double.NaN;
        assertFalse(Wire.isValidFix(nan));

        Wire.Frame ack = new Wire.Frame();
        ack.type = Wire.TYPE_ACK;
        assertFalse(Wire.isValidFix(ack));
    }

    @Test
    public void privateAddressesOnly() {
        assertTrue(Wire.isPrivateV4("10.1.2.3"));
        assertTrue(Wire.isPrivateV4("172.16.0.1"));
        assertTrue(Wire.isPrivateV4("172.31.255.255"));
        assertTrue(Wire.isPrivateV4("192.168.43.12"));
        assertFalse(Wire.isPrivateV4("172.32.0.1"));
        assertFalse(Wire.isPrivateV4("8.8.8.8"));
        assertFalse(Wire.isPrivateV4("192.168.1.300"));
        assertFalse(Wire.isPrivateV4("example.com"));
        assertFalse(Wire.isPrivateV4(null));
    }

    /** Telefon (Dart) tarafı aynı vektörle sınanır: `test/gps_link_wire_test.dart`. */
    @Test
    public void derivedKeyMatchesSharedVector() {
        assertArrayEquals(Wire.hexToKey("8190d160a8a6fb8aec3a09f347bc3f3a"), Wire.deriveKey(KEY_HEX));
        assertArrayEquals(Wire.hexToKey("ff5a0bd629c643e1582210e8cdd29c64"),
                Wire.deriveKey("0123456789abcdef0123456789abcdef"));
    }

    @Test
    public void derivedKeyIsNotTheToken() {
        assertFalse(java.util.Arrays.equals(Wire.hexToKey(KEY_HEX), Wire.deriveKey(KEY_HEX)));
    }

    @Test
    public void derivedKeyRejectsMalformedToken() {
        try {
            Wire.deriveKey("short");
            fail("expected rejection");
        } catch (IllegalArgumentException expected) {
            // not 32 hex
        }
    }

    @Test
    public void gateAcceptsIncreasingSequenceOnly() {
        PeerGate gate = new PeerGate();
        assertTrue(gate.accept(1, 1, 1000));
        assertTrue(gate.accept(1, 2, 2000));
        assertFalse(gate.accept(1, 2, 2100));
        assertFalse(gate.accept(1, 1, 2200));
    }

    @Test
    public void gateSwitchesPhoneOnlyAfterSilence() {
        PeerGate gate = new PeerGate();
        assertTrue(gate.accept(1, 1, 1000));
        assertFalse(gate.accept(2, 1, 2000));
        assertTrue(gate.accept(2, 1, 1000 + Wire.STALE_MS + 1));
        assertFalse(gate.accept(1, 5, 1000 + Wire.STALE_MS + 2));
    }
}
