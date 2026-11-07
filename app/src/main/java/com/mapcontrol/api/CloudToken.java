package com.mapcontrol.api;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Araçtaki hesap yetkisi. Araçta kullanıcı adı/parola ile giriş yoktur: telefon (hesaba girişli) eşleşmede ve
 * süresi yarıya inince sunucudan bu araca özel kısıtlı bir token alır ({@code POST /api/cars/{vin}/car-token}) ve
 * eşleşme anahtarıyla korunan {@code PUT /cloud-token} ile buraya yazar. Token yalnızca bu şasenin yolculuk kutusuna
 * ve park fotoğrafına yükleme yapabilir; sunucu başka her isteği reddeder.
 */
public final class CloudToken {
    private static final String PREFS = "cloud_car_token";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_EXPIRES = "expiresAtMs";
    private static final String KEY_VIN = "vin";
    /** Süresine bu kadar kalmış token kullanılmaz (saat kayması, uzun yükleme). */
    private static final long EXPIRY_MARGIN_MS = 60_000L;

    private CloudToken() {
    }

    /** Doğrulama hatası kodu; geçerliyse null. Android'e bağlı değildir (birim testli). */
    static String reject(String token, long expiresAtMs, String tokenVin, String carVin, long nowMs) {
        if (token == null || token.length() < 20 || token.length() > 4096 || token.split("\\.", -1).length != 3) {
            return "token_invalid";
        }
        if (expiresAtMs <= nowMs + EXPIRY_MARGIN_MS) {
            return "token_expired";
        }
        if (tokenVin == null || tokenVin.trim().isEmpty()) {
            return "vin_required";
        }
        if (carVin != null && !carVin.isEmpty() && !carVin.equalsIgnoreCase(tokenVin.trim())) {
            return "vin_mismatch";
        }
        return null;
    }

    /** Telefondan gelen token'ı saklar. @return hata kodu ya da başarıda null */
    public static String store(Context context, String token, long expiresAtMs, String tokenVin, String carVin) {
        String error = reject(token, expiresAtMs, tokenVin, carVin, System.currentTimeMillis());
        if (error != null) {
            return error;
        }
        prefs(context).edit()
                .putString(KEY_TOKEN, token)
                .putLong(KEY_EXPIRES, expiresAtMs)
                .putString(KEY_VIN, tokenVin.trim().toUpperCase(java.util.Locale.ROOT))
                .commit();
        return null;
    }

    /** Kullanılabilir token; yoksa, süresi dolduysa ya da başka şaseye aitse null. */
    public static String bearer(Context context, String carVin) {
        SharedPreferences p = prefs(context);
        String token = p.getString(KEY_TOKEN, null);
        if (token == null || p.getLong(KEY_EXPIRES, 0L) <= System.currentTimeMillis() + EXPIRY_MARGIN_MS) {
            return null;
        }
        String vin = p.getString(KEY_VIN, "");
        if (carVin != null && !carVin.isEmpty() && !carVin.equalsIgnoreCase(vin)) {
            return null;
        }
        return token;
    }

    /** Sunucu token'ı reddetti (401): telefon yenisini verene kadar kullanılmaz. */
    public static void clear(Context context) {
        prefs(context).edit().clear().commit();
    }

    /** {@code GET /cloud-token}: telefonun yenileme kararı için durum (token'ın kendisi verilmez). */
    public static String statusJson(Context context) {
        SharedPreferences p = prefs(context);
        boolean has = p.getString(KEY_TOKEN, null) != null;
        try {
            JSONObject o = new JSONObject();
            o.put("hasToken", has);
            o.put("expiresAtMs", has ? p.getLong(KEY_EXPIRES, 0L) : 0L);
            o.put("vin", has ? p.getString(KEY_VIN, "") : "");
            return o.toString();
        } catch (JSONException e) {
            return "{\"hasToken\":false,\"expiresAtMs\":0,\"vin\":\"\"}";
        }
    }

    /** Eski sürümün araçta giriş kayıtlarını siler (kullanıcı adı, oturum anahtarı, premium bayrağı, TTS sesi). */
    public static void dropLegacyLogin(Context context) {
        SharedPreferences main = context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
        if (main.contains("adbauthToken") || main.contains("adbauthUsername") || main.contains("adbauthIsPremium")) {
            main.edit().remove("adbauthToken").remove("adbauthUsername").remove("adbauthIsPremium").apply();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
