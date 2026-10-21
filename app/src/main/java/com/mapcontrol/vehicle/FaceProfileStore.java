package com.mapcontrol.vehicle;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class FaceProfileStore {
    private static final String PREFS_NAME = "MapControlPrefs";
    private static final String KEY_PROFILES = "localDmsFaceProfiles";
    private static final String KEY_ALIAS = "MapControlDmsFaceProfiles";
    public static final int MAX_PROFILES = 3;

    public static final class Profile {
        public final String name;
        public final int seatMemory;
        public final List<byte[]> templates;

        public Profile(String name, int seatMemory, List<byte[]> templates) {
            this.name = name;
            this.seatMemory = seatMemory;
            this.templates = templates;
        }
    }

    private FaceProfileStore() {
    }

    public static List<Profile> load(Context context) {
        String stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PROFILES, null);
        if (stored == null) {
            return new ArrayList<>();
        }
        List<Profile> profiles = new ArrayList<>();
        try {
            String serialized = new String(decrypt(Base64.decode(stored, Base64.NO_WRAP)),
                    StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(serialized);
            for (int i = 0; i < array.length() && profiles.size() < MAX_PROFILES; i++) {
                JSONObject item = array.getJSONObject(i);
                JSONArray encodedTemplates = item.getJSONArray("templates");
                List<byte[]> templates = new ArrayList<>();
                for (int j = 0; j < encodedTemplates.length(); j++) {
                    byte[] template = Base64.decode(encodedTemplates.getString(j), Base64.NO_WRAP);
                    if (template.length > 0) {
                        templates.add(template);
                    }
                }
                if (!templates.isEmpty()) {
                    profiles.add(new Profile(item.getString("name"),
                            item.getInt("seatMemory"), templates));
                }
            }
        } catch (JSONException | IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("Yerel yüz profilleri okunamadı", e);
        }
        return profiles;
    }

    public static void save(Context context, List<Profile> profiles) {
        if (profiles.size() > MAX_PROFILES) {
            throw new IllegalArgumentException("En fazla üç yüz profili kaydedilebilir");
        }
        JSONArray array = new JSONArray();
        for (Profile profile : profiles) {
            JSONObject item = new JSONObject();
            JSONArray templates = new JSONArray();
            try {
                item.put("name", profile.name);
                item.put("seatMemory", profile.seatMemory);
                for (byte[] template : profile.templates) {
                    templates.put(Base64.encodeToString(template, Base64.NO_WRAP));
                }
                item.put("templates", templates);
                array.put(item);
            } catch (JSONException e) {
                throw new IllegalStateException("Yerel yüz profili hazırlanamadı", e);
            }
        }
        try {
            String encrypted = Base64.encodeToString(
                    encrypt(array.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (!prefs.edit().putString(KEY_PROFILES, encrypted).commit()) {
                throw new IllegalStateException("Yerel yüz profilleri kaydedilemedi");
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Yerel yüz profilleri şifrelenemedi", e);
        }
    }

    private static byte[] encrypt(byte[] plaintext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext);
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        return combined;
    }

    private static byte[] decrypt(byte[] encrypted) throws GeneralSecurityException {
        if (encrypted.length < 13) {
            throw new GeneralSecurityException("Şifreli yüz profili verisi eksik");
        }
        byte[] iv = new byte[12];
        System.arraycopy(encrypted, 0, iv, 0, iv.length);
        byte[] ciphertext = new byte[encrypted.length - iv.length];
        System.arraycopy(encrypted, iv.length, ciphertext, 0, ciphertext.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return cipher.doFinal(ciphertext);
    }

    private static SecretKey getOrCreateKey() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        try {
            keyStore.load(null);
            java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
            if (existing instanceof SecretKey) {
                return (SecretKey) existing;
            }
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            return generator.generateKey();
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException("Android keystore açılamadı", e);
        } catch (java.security.cert.CertificateException e) {
            throw new GeneralSecurityException("Android keystore sertifikası okunamadı", e);
        }
    }
}
