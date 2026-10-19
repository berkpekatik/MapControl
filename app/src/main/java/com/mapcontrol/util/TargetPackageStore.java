package com.mapcontrol.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Yansıtma hedef paketi: tek {@link SharedPreferences} dosyası / anahtar ve tüm yüzeylerin aynı anda güncellenmesi için yayın.
 */
public final class TargetPackageStore {

    public static final String PREFS_NAME = "MapControlPrefs";
    public static final String KEY_TARGET_PACKAGE = "targetPackage";
    /** Hedef paket prefs'te değişti (sekme, overlay veya profil senkronu). */
    public static final String ACTION_TARGET_PACKAGE_UPDATED = "com.mapcontrol.action.TARGET_PACKAGE_UPDATED";

    private TargetPackageStore() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Boş veya null ise kayıt temizlenir (boş string). */
    public static String normalize(String packageName) {
        if (packageName == null) {
            return "";
        }
        String t = packageName.trim();
        return t;
    }

    public static String read(Context context) {
        String s = prefs(context).getString(KEY_TARGET_PACKAGE, "");
        return s != null ? s.trim() : "";
    }

    /**
     * Prefs yazar ve paket içi yayın gönderir (MainActivity alanını ve diğer dinleyicileri güncellemek için).
     */
    public static void writeAndBroadcast(Context context, String packageName) {
        String v = normalize(packageName);
        // commit: yayın alıcıları hemen okuduğunda disk ile uyumlu olsun (apply gecikmesi yok)
        prefs(context).edit().putString(KEY_TARGET_PACKAGE, v).commit();
        broadcast(context);
    }

    public static void broadcast(Context context) {
        Intent br = new Intent(ACTION_TARGET_PACKAGE_UPDATED);
        br.setPackage(context.getApplicationContext().getPackageName());
        context.getApplicationContext().sendBroadcast(br);
    }
}
