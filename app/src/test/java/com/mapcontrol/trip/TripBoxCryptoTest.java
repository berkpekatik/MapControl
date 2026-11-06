package com.mapcontrol.trip;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import java.security.GeneralSecurityException;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class TripBoxCryptoTest {

    private static final String TOKEN = "e4a1f893d20c4e12b79a834f8261ad90";

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    @Test
    public void keyAndSealedVectorMatchThePhone() throws Exception {
        byte[] key = TripBoxCrypto.deriveKey(TOKEN);
        assertEquals(TripBoxCryptoVector.KEY_HEX, hex(key));
        byte[] iv = new byte[12];
        for (int i = 0; i < 12; i++) iv[i] = (byte) i;
        assertEquals(TripBoxCryptoVector.SEALED_HEX, hex(TripBoxCrypto.encrypt(key, "yolculuk".getBytes(StandardCharsets.US_ASCII), iv)));
    }

    @Test
    public void keyDiffersFromPhotoAndGpsLinkKeys() {
        String key = hex(TripBoxCrypto.deriveKey(TOKEN));
        assertNotEquals("5756c7b748fae229b526f9f879df49bb", key); // park fotoğrafı
        assertNotEquals("8190d160a8a6fb8aec3a09f347bc3f3a", key); // GPS Link
    }

    @Test
    public void roundTripsAndRejectsTamperingWrongKeyAndShortData() throws Exception {
        byte[] key = TripBoxCrypto.deriveKey(TOKEN);
        byte[] plain = new byte[3000];
        for (int i = 0; i < plain.length; i++) plain[i] = (byte) (i * 13);
        byte[] sealed = TripBoxCrypto.encrypt(key, plain);
        assertArrayEquals(plain, TripBoxCrypto.decrypt(key, sealed));
        try {
            TripBoxCrypto.decrypt(TripBoxCrypto.deriveKey("00000000000000000000000000000001"), sealed);
            fail();
        } catch (GeneralSecurityException expected) {
        }
        sealed[20] ^= 1;
        try {
            TripBoxCrypto.decrypt(key, sealed);
            fail();
        } catch (GeneralSecurityException expected) {
        }
        try {
            TripBoxCrypto.decrypt(key, new byte[5]);
            fail();
        } catch (GeneralSecurityException expected) {
        }
    }

    @Test
    public void photoKeyCannotOpenATrip() throws Exception {
        // Aynı anahtar bile olsa AAD farklı: bir amaç için şifrelenen veri ötekinde açılmaz.
        byte[] tripKey = TripBoxCrypto.deriveKey(TOKEN);
        byte[] sealed = com.mapcontrol.dvr.ParkPhotoCrypto.encrypt(tripKey, new byte[]{1, 2, 3});
        try {
            TripBoxCrypto.decrypt(tripKey, sealed);
            fail();
        } catch (GeneralSecurityException expected) {
        }
    }
}
