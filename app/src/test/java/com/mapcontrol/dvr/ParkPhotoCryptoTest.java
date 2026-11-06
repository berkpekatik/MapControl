package com.mapcontrol.dvr;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.security.GeneralSecurityException;

import org.junit.Test;

public class ParkPhotoCryptoTest {

    private static final String TOKEN = "e4a1f893d20c4e12b79a834f8261ad90";

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    @Test
    public void keyIsDerivedNotTheToken() {
        byte[] key = ParkPhotoCrypto.deriveKey(TOKEN);
        assertEquals(16, key.length);
        assertFalse(hex(key).equals(TOKEN));
        // GPS Link anahtarından (8190d160…) farklı olmalı: farklı etiket.
        assertFalse(hex(key).startsWith("8190d160"));
    }

    @Test
    public void keyVectorMatchesThePhone() {
        // Telefon testi (park_photo_crypto_test.dart) aynı vektörü doğrular.
        assertEquals(ParkPhotoCryptoVector.KEY_HEX, hex(ParkPhotoCrypto.deriveKey(TOKEN)));
    }

    @Test
    public void roundTrips() throws Exception {
        byte[] key = ParkPhotoCrypto.deriveKey(TOKEN);
        byte[] plain = new byte[5000];
        for (int i = 0; i < plain.length; i++) plain[i] = (byte) (i * 7);
        byte[] sealed = ParkPhotoCrypto.encrypt(key, plain);
        assertEquals(plain.length + 12 + 16, sealed.length);
        assertArrayEquals(plain, ParkPhotoCrypto.decrypt(key, sealed));
    }

    @Test
    public void nonceIsFreshEachTime() throws Exception {
        byte[] key = ParkPhotoCrypto.deriveKey(TOKEN);
        byte[] a = ParkPhotoCrypto.encrypt(key, new byte[]{1, 2, 3});
        byte[] b = ParkPhotoCrypto.encrypt(key, new byte[]{1, 2, 3});
        assertFalse(hex(a).equals(hex(b)));
    }

    @Test
    public void knownVectorDecrypts() throws Exception {
        byte[] key = ParkPhotoCrypto.deriveKey(TOKEN);
        byte[] iv = new byte[12];
        for (int i = 0; i < 12; i++) iv[i] = (byte) i;
        byte[] sealed = ParkPhotoCrypto.encrypt(key, "park".getBytes("US-ASCII"), iv);
        assertEquals(ParkPhotoCryptoVector.SEALED_HEX, hex(sealed));
    }

    @Test
    public void wrongKeyTamperedAndShortFail() throws Exception {
        byte[] key = ParkPhotoCrypto.deriveKey(TOKEN);
        byte[] sealed = ParkPhotoCrypto.encrypt(key, new byte[]{9, 9, 9});
        try {
            ParkPhotoCrypto.decrypt(ParkPhotoCrypto.deriveKey("00000000000000000000000000000001"), sealed);
            fail();
        } catch (GeneralSecurityException expected) {
        }
        sealed[sealed.length - 1] ^= 1;
        try {
            ParkPhotoCrypto.decrypt(key, sealed);
            fail();
        } catch (GeneralSecurityException expected) {
        }
        try {
            ParkPhotoCrypto.decrypt(key, new byte[10]);
            fail();
        } catch (GeneralSecurityException expected) {
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedToken() {
        ParkPhotoCrypto.deriveKey("xyz");
    }
}
