package com.mapcontrol.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;

import java.util.List;

/**
 * "Araç Launcher Modu": Kullanıcı bunu Ayarlar'dan açtığında, manifestte varsayılan olarak
 * kapalı (disabled) tanımlı {@code MainActivityHomeAlias} activity-alias'ı etkinleştirilir.
 * Bu alias, Android'e MainActivity'nin Ana Ekran (HOME) uygulaması olarak da seçilebileceğini
 * bildirir. Kapatıldığında alias yeniden devre dışı bırakılır ve uygulama yalnızca normal
 * LAUNCHER simgesinden açılabilir hale döner.
 *
 * <p>Bu yaklaşım {@link com.mapcontrol.ui.activity.MainActivity} veya mevcut manifest girdisini
 * değiştirmez; tamamen opsiyonel ve geri alınabilir bir ek katmandır.
 */
public final class LauncherModeManager {

    private static final String PREFS_NAME = "MapControlPrefs";
    public static final String KEY_LAUNCHER_MODE_ENABLED = "launcherModeEnabled";
    private static final String HOME_ALIAS_CLASS = "com.mapcontrol.ui.activity.MainActivityHomeAlias";

    /** Basit tanı loglaması için minimal callback; MainActivity/SettingsTabBuilder'daki log()'a bağlanır. */
    public interface LogSink {
        void log(String message);
    }

    private LauncherModeManager() {
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_LAUNCHER_MODE_ENABLED, false);
    }

    /** Launcher modu UI'sının aktif olup olmadığı ({@link #isEnabled}). */
    public static boolean isActive(Context context) {
        return isEnabled(context);
    }

    /**
     * Ana Ekran (HOME) intent'i ile açıldıysa tercihi kalıcı olarak etkinleştirir.
     * Uygulama yeniden başlatıldığında launcher deneyiminin sürmesi için kullanılır.
     */
    public static void ensurePersistedFromHomeLaunch(Context context) {
        if (!isEnabled(context)) {
            setEnabled(context, true);
        }
    }

    /** Tercihi kaydeder ve HOME activity-alias'ını buna göre etkinleştirir/devre dışı bırakır. */
    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_LAUNCHER_MODE_ENABLED, enabled).apply();
        setHomeAliasEnabled(context, enabled);
    }

    private static void setHomeAliasEnabled(Context context, boolean enabled) {
        try {
            ComponentName alias = new ComponentName(context.getPackageName(), HOME_ALIAS_CLASS);
            PackageManager pm = context.getPackageManager();
            int newState = enabled
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            pm.setComponentEnabledSetting(alias, newState, PackageManager.DONT_KILL_APP);
        } catch (Exception ignored) {
            // Alias bileşeni bulunamazsa (ör. eski build) sessizce yok say.
        }
    }

    /**
     * Ana Ekran uygulaması seçim ekranını açar. Cihazda daha önce bir uygulama "Her zaman" ile
     * Home olarak sabitlenmişse, düz {@code ACTION_MAIN+CATEGORY_HOME} intent'i hiçbir diyalog
     * göstermeden doğrudan o uygulamaya gider — bu yüzden önce sistemin resmi "Ana ekran
     * uygulaması" ayar ekranını, olmazsa zorla seçim diyaloğu gösteren {@link Intent#createChooser}
     * yolunu dener.
     */
    public static void openHomeChooser(Context context) {
        // 1) Sistemin resmi "Ana ekran uygulaması" ayar ekranı (OEM'lerde de genelde desteklenir,
        //    önceki "Her zaman" tercihini de bu ekrandan değiştirmek mümkündür).
        try {
            Intent settingsIntent = new Intent(Settings.ACTION_HOME_SETTINGS);
            settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (settingsIntent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(settingsIntent);
                return;
            }
        } catch (Exception ignored) {
        }
        // 2) Yedek yol: seçim diyaloğunu zorla göster — daha önce "Her zaman" seçilmiş olsa bile sorar.
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            Intent chooser = Intent.createChooser(homeIntent, "Ana Ekran uygulamasını seçin");
            chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception ignored) {
        }
    }

    /**
     * Tanı amaçlı: Home (Ana Ekran) rolüne aday tüm uygulamaları ve şu an hangi uygulamanın
     * çözüldüğünü loga yazar. "Ana Ekran Seçim Ekranını Aç" tıklamasına rağmen hiçbir şey
     * olmadığında, MapControl'ün aday listesinde olup olmadığını ve cihazda önceden sabitlenmiş
     * bir Home uygulaması olup olmadığını anlamak için kullanılır.
     */
    public static void logHomeResolutionState(Context context, LogSink log) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);

            List<ResolveInfo> candidates = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
            StringBuilder sb = new StringBuilder("Home adayları (" + candidates.size() + "): ");
            boolean found = false;
            for (ResolveInfo ri : candidates) {
                sb.append(ri.activityInfo.packageName).append("/").append(ri.activityInfo.name).append("; ");
                if (context.getPackageName().equals(ri.activityInfo.packageName)) {
                    found = true;
                }
            }
            log.log(sb.toString());
            log.log("MapControl aday listesinde: " + (found ? "VAR" : "YOK (alias etkin değil / PM henüz güncellemedi)"));

            ResolveInfo resolved = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved != null && resolved.activityInfo != null) {
                log.log("Şu an çözülen Home: " + resolved.activityInfo.packageName + "/" + resolved.activityInfo.name);
                if (candidates.size() > 1 && !"android".equals(resolved.activityInfo.packageName)
                        && resolved.activityInfo.name != null
                        && !resolved.activityInfo.name.contains("ResolverActivity")) {
                    log.log("Uyarı: Birden fazla aday olmasına rağmen doğrudan bir uygulama çözülüyor — "
                            + "cihazda muhtemelen \"Her zaman\" ile sabitlenmiş bir Home uygulaması var.");
                }
            }
        } catch (Exception e) {
            log.log("Home durum kontrolü hata: " + e.getMessage());
        }
    }

    /** Verilen intent, cihazın Ana Ekran (Home) tuşu/isteği ile mi geldi? */
    public static boolean isHomeIntent(Intent intent) {
        return intent != null && intent.getCategories() != null
                && intent.getCategories().contains(Intent.CATEGORY_HOME);
    }
}

