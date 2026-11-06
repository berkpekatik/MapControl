package com.mapcontrol.trip;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Bitmiş yolculuğu sunucudaki posta kutusuna şifreli bırakmak için. Anahtar eşleşme anahtarından (32 onaltılık
 * {@code phoneToken}) türetilir: {@code HMAC-SHA256(anahtar baytları, "frostnav-trip-v1")} ilk 16 bayt. GPS Link, park
 * fotoğrafı ve HTTP başlığı anahtarlarından farklıdır. Çıktı: {@code nonce(12) || şifreli metin || etiket(16)},
 * AES-128-GCM, AAD {@code "FrostNavTrip/1"}. Telefon (Dart) aynı biçimi çözer. Android'e bağlı değildir.
 */
public final class TripBoxCrypto {

    static final int KEY_BYTES = 16;
    static final int IV_LENGTH = 12;
    static final int TAG_BITS = 128;
    private static final byte[] LABEL = "frostnav-trip-v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] AAD = "FrostNavTrip/1".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();

    private TripBoxCrypto() {
    }

    public static byte[] deriveKey(String tokenHex) {
        if (tokenHex == null || !tokenHex.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("key");
        }
        byte[] token = new byte[KEY_BYTES];
        for (int i = 0; i < KEY_BYTES; i++) {
            token[i] = (byte) Integer.parseInt(tokenHex.substring(i * 2, i * 2 + 2), 16);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token, "HmacSHA256"));
            byte[] out = new byte[KEY_BYTES];
            System.arraycopy(mac.doFinal(LABEL), 0, out, 0, KEY_BYTES);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] encrypt(byte[] key, byte[] plain) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        return encrypt(key, plain, iv);
    }

    static byte[] encrypt(byte[] key, byte[] plain, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(AAD);
        byte[] body = cipher.doFinal(plain);
        byte[] out = new byte[IV_LENGTH + body.length];
        System.arraycopy(iv, 0, out, 0, IV_LENGTH);
        System.arraycopy(body, 0, out, IV_LENGTH, body.length);
        return out;
    }

    /** Yanlış anahtar ya da bozuk veri {@link GeneralSecurityException} verir. */
    public static byte[] decrypt(byte[] key, byte[] sealed) throws GeneralSecurityException {
        if (sealed == null || sealed.length < IV_LENGTH + TAG_BITS / 8) {
            throw new GeneralSecurityException("short");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, sealed, 0, IV_LENGTH));
        cipher.updateAAD(AAD);
        return cipher.doFinal(sealed, IV_LENGTH, sealed.length - IV_LENGTH);
    }
}
