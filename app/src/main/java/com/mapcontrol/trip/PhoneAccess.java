package com.mapcontrol.trip;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Yolculuk ve sayaç uçlarını yalnızca eşleşmiş telefona açar. Anahtar araçta üretilir,
 * MapControl ekranında gösterilir; telefon her istekte {@value #HEADER} başlığıyla yollar.
 * Anahtar 32 onaltılık karakterdir (GPS Link anahtarıyla aynı biçim).
 */
public final class PhoneAccess {

    public static final String HEADER = "X-MapControl-Token";

    private static final String PREFS = "MapControlPrefs";
    private static final String KEY_TOKEN = "phoneToken";

    private PhoneAccess() {
    }

    /** Anahtar yoksa üretir ve saklar. */
    public static synchronized String token(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String token = prefs.getString(KEY_TOKEN, null);
        if (!isWellFormed(token)) {
            token = generate();
            prefs.edit().putString(KEY_TOKEN, token).commit();
        }
        return token;
    }

    /** Yeni anahtar üretir; eşleşmiş telefon yeniden eşleşmeli. */
    public static synchronized String regenerate(Context context) {
        String token = generate();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TOKEN, token).commit();
        return token;
    }

    /** Başlık satırlarından anahtarı okuyup saklı olanla sabit sürede karşılaştırır. */
    public static boolean isAuthorized(Context context, String requestHeader) {
        return matches(token(context), headerValue(requestHeader, HEADER));
    }

    static boolean matches(String expected, String presented) {
        if (expected == null || presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }

    static String headerValue(String requestHeader, String name) {
        if (requestHeader == null) {
            return null;
        }
        String prefix = name.toLowerCase(java.util.Locale.ROOT) + ":";
        for (String line : requestHeader.split("\r\n")) {
            if (line.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    static boolean isWellFormed(String token) {
        return token != null && token.matches("[0-9a-f]{32}");
    }

    private static String generate() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format(java.util.Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}
