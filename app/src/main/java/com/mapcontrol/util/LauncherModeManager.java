package com.mapcontrol.util;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;

import com.mapcontrol.admin.MapControlDeviceAdminReceiver;

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
    /** Güncelleme başına en fazla bir kez (kullanıcıyı sürekli rahatsız etmemek için) rol isteği yapılır. */
    private static final String KEY_HOME_ROLE_ASKED_VERSION = "launcherHomeRoleAskedVersion";

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
        syncMiniDashEntry(context, enabled);
        if (enabled) {
            pinHomeAsDeviceOwner(context);
            // Bildirim dinleyicisi zaten bağlıysa telefon önceliği izlemesi hemen başlasın (bağlı değilse yoksayılır).
            com.mapcontrol.media.BtSessionWatcher.start(context);
        } else {
            clearDeviceOwnerHomePin(context);
            com.mapcontrol.media.BtSessionWatcher.stop();
        }
    }

    private static void setHomeAliasEnabled(Context context, boolean enabled) {
        try {
            ComponentName alias = new ComponentName(context.getPackageName(), HOME_ALIAS_CLASS);
            PackageManager pm = context.getPackageManager();
            int current = pm.getComponentEnabledSetting(alias);
            int newState = enabled
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            // Zaten istenen durumdaysa dokunma (gereksiz paket yayını ve HOME çözümü sıçraması olmasın).
            // Kapatırken manifest varsayılanı (DEFAULT = kapalı) da "zaten kapalı" sayılır.
            if (current == newState || (!enabled && current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)) {
                return;
            }
            pm.setComponentEnabledSetting(alias, newState, PackageManager.DONT_KILL_APP);
        } catch (Exception ignored) {
            // Alias bileşeni bulunamazsa (ör. eski build) sessizce yok say.
        }
    }

    /**
     * Kayıtlı tercihle alias durumunu eşitler (ör. APK güncellenince alias bileşen durumu
     * manifest varsayılanına, yani kapalıya döner; tercih ise açık kalır).
     */
    public static void syncAliasWithPreference(Context context) {
        boolean enabled = isEnabled(context);
        setHomeAliasEnabled(context, enabled);
        syncMiniDashEntry(context, enabled);
    }

    /**
     * "Mini gösterge" uygulama listesi simgesini launcher moduyla eşitler: yalnız mod açıkken görünür
     * ({@link com.mapcontrol.launcher.MiniDashEntry}). Etkinliğin kendisine dokunulmaz.
     */
    private static void syncMiniDashEntry(Context context, boolean launcherMode) {
        try {
            ComponentName alias = new ComponentName(context.getPackageName(),
                    com.mapcontrol.launcher.MiniDashEntry.ALIAS_CLASS);
            PackageManager pm = context.getPackageManager();
            Integer next = com.mapcontrol.launcher.MiniDashEntry.stateToWrite(
                    pm.getComponentEnabledSetting(alias), launcherMode);
            if (next != null) {
                pm.setComponentEnabledSetting(alias, next, PackageManager.DONT_KILL_APP);
            }
        } catch (Exception ignored) {
            // Alias bileşeni yoksa (eski derleme) sessizce geç.
        }
    }

    private static ComponentName homeAlias(Context context) {
        return new ComponentName(context.getPackageName(), HOME_ALIAS_CLASS);
    }

    private static DevicePolicyManager deviceOwnerDpm(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                return dpm;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Cihaz sahibiyse (araçta FrostNav böyle kurulu) HOME'u iletişim kutusu olmadan bu uygulamaya
     * sabitler: {@code addPersistentPreferredActivity} ile MAIN+HOME+DEFAULT filtresi alias'a bağlanır.
     * Bu kayıt RoleManager'dan bağımsızdır; uygulama güncellenince/yeniden kurulunca rol düşse bile
     * HOME bizim launcher'a çözülür. İşlem idempotenttir, arka plandan çağrılması güvenlidir.
     *
     * @return sabitleme yapıldıysa true (cihaz sahibi değilsek false)
     */
    public static boolean pinHomeAsDeviceOwner(Context context) {
        DevicePolicyManager dpm = deviceOwnerDpm(context);
        if (dpm == null) {
            return false;
        }
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
            filter.addCategory(Intent.CATEGORY_HOME);
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            ComponentName admin = new ComponentName(context, MapControlDeviceAdminReceiver.class);
            dpm.addPersistentPreferredActivity(admin, filter, homeAlias(context));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Launcher modu kapatılırken {@link #pinHomeAsDeviceOwner} ile eklenen sabitlemeyi kaldırır. */
    private static void clearDeviceOwnerHomePin(Context context) {
        DevicePolicyManager dpm = deviceOwnerDpm(context);
        if (dpm == null) {
            return;
        }
        try {
            ComponentName admin = new ComponentName(context, MapControlDeviceAdminReceiver.class);
            dpm.clearPackagePersistentPreferredActivities(admin, context.getPackageName());
        } catch (Exception ignored) {
        }
    }

    /**
     * Güncelleme / yeniden kurulum / açılış sonrası launcher modunu geri kurar (arka plandan güvenli,
     * hiçbir arayüz başlatmaz): tercih açıksa alias'ı etkinleştirir ve cihaz sahibiyse HOME'u sabitler.
     *
     * <p>Neden: paket güncellenince (özellikle araçtaki kurucu kaldır+kur yaparsa) sistem HOME
     * rolünü/tercihini düşürebilir; bunu yeniden kurmak için kullanıcının FrostNav'ı elle açmasını
     * beklemeyelim. Cihaz sahibi değilsek rol ancak kullanıcı onayıyla alınabilir; bu
     * {@link #requestHomeRoleOncePerVersion} ile ön plandayken yapılır.
     */
    public static void restoreIfEnabled(Context context) {
        boolean enabled = isEnabled(context);
        syncMiniDashEntry(context, enabled);
        if (!enabled) {
            return;
        }
        setHomeAliasEnabled(context, true);
        pinHomeAsDeviceOwner(context);
    }

    /**
     * Ön plandaki bir activity'den: launcher modu açık ama HOME bize çözülmüyorsa (ör. güncellemeden
     * sonra rol düştü) rol isteğini sürüm başına bir kez gösterir. Cihaz sahibiyse sabitleme zaten
     * yeterlidir, diyalog gösterilmez.
     *
     * @return diyalog/ekran gösterildiyse true
     */
    public static boolean requestHomeRoleOncePerVersion(Activity activity, int requestCode) {
        if (!isEnabled(activity) || isDefaultHome(activity)) {
            return false;
        }
        if (pinHomeAsDeviceOwner(activity) && isDefaultHome(activity)) {
            return false;
        }
        if (deviceOwnerDpm(activity) != null) {
            return false;
        }
        try {
            SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long version = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).getLongVersionCode()
                    : activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
            if (prefs.getLong(KEY_HOME_ROLE_ASKED_VERSION, -1L) == version) {
                return false;
            }
            prefs.edit().putLong(KEY_HOME_ROLE_ASKED_VERSION, version).apply();
        } catch (Exception e) {
            return false;
        }
        return requestHomeRoleIfNeeded(activity, requestCode);
    }

    /** HOME intent'i şu an bu uygulamaya mı çözülüyor? */
    public static boolean isDefaultHome(Context context) {
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolved = context.getPackageManager()
                    .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
            return resolved != null && resolved.activityInfo != null
                    && context.getPackageName().equals(resolved.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Launcher modu açıldıktan sonra HOME'u yeniden bu uygulamaya bağlamayı dener.
     *
     * <p>Neden gerekli: alias devre dışı bırakılınca Android rol yöneticisi uygulamayı HOME rolünden
     * düşürür ("no longer qualifies for the role"); alias yeniden etkinleştirilse de rol otomatik
     * geri gelmez ve HOME sistem launcher'ına çözülür. Bu yüzden, zaten varsayılan Home değilsek
     * (Android 10+) sistemin rol isteği diyaloğu ({@link RoleManager#createRequestRoleIntent})
     * gösterilir; kullanıcı onaylayınca HOME tuşu tekrar bizim launcher'a gider. Rol API'si
     * yoksa veya reddedilirse {@link #openHomeChooser} yedek yoludur. Araç ünitesi bu API'yi
     * desteklemeyebilir; bu yüzden her yol hata durumunda sessizce bir sonrakine geçer ve
     * zaten varsayılan Home isek hiçbir şey yapılmaz.
     *
     * @return diyalog/ekran gösterildiyse true
     */
    public static boolean requestHomeRoleIfNeeded(Activity activity, int requestCode) {
        if (isDefaultHome(activity)) {
            return false;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager roleManager = activity.getSystemService(RoleManager.class);
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
                        && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    activity.startActivityForResult(
                            roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), requestCode);
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Rol isteği başlatılamadı; aşağıdaki seçiciye düş.
        }
        openHomeChooser(activity);
        return true;
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

