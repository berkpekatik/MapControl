package com.mapcontrol.gps;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * GPS Link şifreli UDP çerçeveleri ve anahtar türetme. Saf Java; birim testle sınanır.
 *
 * Paket düzeni: 12 bayt IV, sonra AES-GCM(version, type, client, seq[, konum alanları]).
 * Telefon uygulaması (`lib/data/gps_link_wire.dart`) aynı baytları üretir.
 */
public final class Wire {

    public static final int PORT = 45921;

    static final int TYPE_FIX = 1;
    static final int TYPE_ACK = 2;

    /** Bu kadar süre paket gelmezse bağlantı kopmuş sayılır. */
    static final long STALE_MS = 5000;
    /** Bundan eski konum ne gönderilir ne kabul edilir. */
    static final int MAX_AGE_MS = 3000;
    static final float MAX_ACCURACY_M = 100f;

    private static final int VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 16;
    private static final int MIN_PACKET = IV_LENGTH + 18 + 16;
    private static final int MAX_PACKET = 256;
    private static final byte[] AAD = "FrostNavGPS/1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] KDF_LABEL = "frostnav-gps-v1".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();

    private Wire() {
    }

    static final class Frame {
        int type;
        long client;
        long seq;
        int ageMs;
        double lat;
        double lon;
        float accuracy;
        double altitude = Double.NaN;
        float speed = Float.NaN;
        float bearing = Float.NaN;
    }

    /**
     * Eşleşme anahtarından (32 onaltılık, {@code PhoneAccess} token'ı) GPS Link şifreleme anahtarını türetir:
     * {@code HMAC-SHA256(anahtar baytları, "frostnav-gps-v1")} çıktısının ilk 16 baytı. HTTP başlığında giden
     * token ile paketleri şifreleyen anahtar böylece aynı değer olmaz.
     */
    static byte[] deriveKey(String tokenHex) {
        byte[] token = hexToKey(tokenHex);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token, "HmacSHA256"));
            byte[] out = new byte[KEY_BYTES];
            System.arraycopy(mac.doFinal(KDF_LABEL), 0, out, 0, KEY_BYTES);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean isPrivateV4(String host) {
        if (host == null || !host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return false;
        }
        String[] parts = host.split("\\.");
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] > 255) {
                return false;
            }
        }
        return octets[0] == 10
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168);
    }

    static byte[] hexToKey(String hex) {
        if (hex == null || !hex.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("key");
        }
        byte[] key = new byte[KEY_BYTES];
        for (int i = 0; i < KEY_BYTES; i++) {
            key[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return key;
    }

    static byte[] encode(Frame frame, byte[] key) throws GeneralSecurityException, IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(VERSION);
        out.writeByte(frame.type);
        out.writeLong(frame.client);
        out.writeLong(frame.seq);
        if (frame.type == TYPE_FIX) {
            out.writeInt(frame.ageMs);
            out.writeDouble(frame.lat);
            out.writeDouble(frame.lon);
            out.writeFloat(frame.accuracy);
            out.writeDouble(frame.altitude);
            out.writeFloat(frame.speed);
            out.writeFloat(frame.bearing);
        }
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(AAD);
        byte[] sealed = cipher.doFinal(bytes.toByteArray());
        byte[] packet = new byte[IV_LENGTH + sealed.length];
        System.arraycopy(iv, 0, packet, 0, IV_LENGTH);
        System.arraycopy(sealed, 0, packet, IV_LENGTH, sealed.length);
        return packet;
    }

    static Frame decode(byte[] buffer, int length, byte[] key) throws GeneralSecurityException, IOException {
        if (length < MIN_PACKET || length > MAX_PACKET || length > buffer.length) {
            throw new IOException("size");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, buffer, 0, IV_LENGTH));
        cipher.updateAAD(AAD);
        byte[] plain = cipher.doFinal(buffer, IV_LENGTH, length - IV_LENGTH);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(plain));
        if (in.readUnsignedByte() != VERSION) {
            throw new IOException("version");
        }
        Frame frame = new Frame();
        frame.type = in.readUnsignedByte();
        if (frame.type != TYPE_FIX && frame.type != TYPE_ACK) {
            throw new IOException("type");
        }
        frame.client = in.readLong();
        frame.seq = in.readLong();
        if (frame.type == TYPE_FIX) {
            frame.ageMs = in.readInt();
            frame.lat = in.readDouble();
            frame.lon = in.readDouble();
            frame.accuracy = in.readFloat();
            frame.altitude = in.readDouble();
            frame.speed = in.readFloat();
            frame.bearing = in.readFloat();
        }
        if (in.available() != 0 || frame.client == 0) {
            throw new IOException("format");
        }
        return frame;
    }

    static boolean isValidFix(Frame f) {
        return f.type == TYPE_FIX
                && f.seq > 0
                && f.ageMs >= 0 && f.ageMs <= MAX_AGE_MS
                && isFinite(f.lat) && Math.abs(f.lat) <= 90
                && isFinite(f.lon) && Math.abs(f.lon) <= 180
                && isFinite(f.accuracy) && f.accuracy > 0 && f.accuracy <= MAX_ACCURACY_M
                && (Double.isNaN(f.altitude) || (isFinite(f.altitude) && f.altitude >= -1000 && f.altitude <= 20000))
                && (Float.isNaN(f.speed) || (isFinite(f.speed) && f.speed >= 0 && f.speed <= 150))
                && (Float.isNaN(f.bearing) || (isFinite(f.bearing) && f.bearing >= 0 && f.bearing < 360));
    }

    private static boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }
}
