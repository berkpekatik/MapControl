package com.mapcontrol.admin;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.mapcontrol.util.DiagJournal;
import com.mapcontrol.util.LauncherModeManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Servis modu: araç servise giderken uygulamaları silmek istemeyenler için. Cihaz sahibi yetkisiyle launcher ikonu olan
 * uygulamalar gizlenir ({@code setApplicationHidden}), MapControl'ın kendi ikonu kapatılır (süreç canlı kalır). USB
 * kökünde {@link #UNLOCK_FILE} içinde kullanıcı şifresi ya da araç seri numarası bulunursa her şey geri açılır.
 *
 * <p>{@code priv-app} altındaki paketlere dokunulmaz (aracın kritik bileşenleri). Tasarım: {@code SERVIS-MODU.md}.
 */
public final class ServiceMode {
    private static final String TAG = "ServiceMode";
    private static final String PREFS = "MapControlPrefs";

    /** USB kökünde aranan dosya. Markaya bağlıdır, public'te sync betiği yeniden adlandırır. */
    public static final String UNLOCK_FILE = "mapcontrol-unlock.txt";

    static final String KEY_ACTIVE = "serviceModeActive";
    static final String KEY_HIDDEN = "serviceModeHiddenPackages";
    static final String KEY_PW_HASH = "serviceModePasswordHash";
    static final String KEY_PW_SALT = "serviceModePasswordSalt";
    static final String KEY_RESTORE_LAUNCHER = "serviceModeRestoreLauncherMode";
    /** Gizlenen varsayılan klavyenin kimliği; kilit açılınca yeniden varsayılan yapılır. */
    static final String KEY_RESTORE_IME = "serviceModeRestoreIme";
    /** Uyku için kapatılan kendi bileşenlerimiz: {@code sınıf|önceki durum}. */
    static final String KEY_DISABLED_COMPONENTS = "serviceModeDisabledComponents";

    /**
     * Uykudayken açık kalan kendi bileşenlerimiz: USB ve adb ile çözme, cihaz sahipliği, adb kaldırma/kurma hazırlığı.
     * Geri kalan etkinlik, servis ve alıcılar kapatılır (sağlayıcılara dokunulmaz, kod çalıştırmazlar).
     */
    static final Set<String> KEEP_COMPONENTS = new HashSet<>(java.util.Arrays.asList(
            MapControlDeviceAdminReceiver.class.getName(),
            ServiceModeUsbReceiver.class.getName(),
            ServiceModeOffReceiver.class.getName(),
            ReleaseOwnerReceiver.class.getName(),
            "com.mapcontrol.dvr.InstallPrepareReceiver"));

    public static final int MIN_PASSWORD_LENGTH = 4;
    /** Seri bundan kısaysa (boş, "0", "unknown") kurtarma anahtarı sayılmaz. */
    private static final int MIN_SERIAL_LENGTH = 6;
    private static final String[] SERIAL_PROPS = {"sys.vehicle.hardware.serial.number", "ivi.sn"};
    private static final String APP_ENTRY_CLASS = "com.mapcontrol.ui.activity.AppEntryActivity";
    private static final int MAX_UNLOCK_FILE_BYTES = 4096;

    /** Gizlenecek listedeki bir uygulama. */
    public static final class AppEntry {
        public final String packageName;
        public final String label;
        public final Drawable icon;

        AppEntry(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private ServiceMode() {
    }

    public static boolean isActive(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    public static boolean isDeviceOwner(Context context) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        return dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());
    }

    /**
     * Gizlenecek uygulamalar: launcher ikonu olan, priv-app olmayan, MapControl / HOME uygulaması / klavye olmayan paketler.
     * Arka plan iş parçacığında çağrılmalı (etiket ve ikon yükler). Ekranda gösterilen liste {@link #activate}'e aynen verilir.
     */
    public static List<AppEntry> collectHideable(Context context) {
        Context app = context.getApplicationContext();
        PackageManager pm = app.getPackageManager();
        Set<String> keep = protectedPackages(app);
        List<AppEntry> out = new ArrayList<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            String pkg = ai.packageName;
            if (keep.contains(pkg) || !ai.enabled) continue;
            if (pm.getLaunchIntentForPackage(pkg) == null) continue;
            if (isSystemApp(ai.flags, ai.sourceDir)) continue;
            if (ai.uid / 100000 != 0) continue;
            String label;
            try {
                label = String.valueOf(ai.loadLabel(pm));
            } catch (RuntimeException e) {
                label = pkg;
            }
            Drawable icon = null;
            try {
                icon = ai.loadIcon(pm);
            } catch (RuntimeException ignored) {
            }
            out.add(new AppEntry(pkg, label, icon));
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    /**
     * Hiç dokunulmayacak paketler: MapControl, HOME çözen tüm uygulamalar. Varsayılan klavye ancak yerine geçecek bir sistem
     * klavyesi varsa ve ayarı değiştirebiliyorsak gizlenebilir; yoksa korunur (ekranda yazı yazılamaz hale gelmesin).
     */
    private static Set<String> protectedPackages(Context app) {
        Set<String> keep = new HashSet<>();
        keep.add(app.getPackageName());
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo ri : app.getPackageManager().queryIntentActivities(home, PackageManager.MATCH_ALL)) {
                if (ri.activityInfo != null) keep.add(ri.activityInfo.packageName);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "HOME listesi: " + e);
        }
        try {
            String ime = android.provider.Settings.Secure.getString(app.getContentResolver(),
                    android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null && ime.contains("/") && systemImeFallback(app) == null) {
                keep.add(ime.substring(0, ime.indexOf('/')));
            }
        } catch (RuntimeException ignored) {
        }
        return keep;
    }

    /**
     * Sistem uygulaması mı? Uygulamalar sekmesindeki "Yerel" listesiyle aynı ayrım (bkz.
     * {@code ProjectionTargetApps.isSystemOrPrivApp}): {@code FLAG_SYSTEM} / {@code FLAG_UPDATED_SYSTEM_APP} ya da kurulum
     * yolu {@code /data/} dışında ({@code /system}, {@code /product}, {@code /vendor}...). Yol okunamazsa sistem sayılır.
     */
    static boolean isSystemApp(int flags, String sourceDir) {
        if ((flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) return true;
        return sourceDir == null || !sourceDir.startsWith("/data/");
    }

    /**
     * Servis modunu açar: şifreyi (tuzlu SHA-256) ve listeyi kaydeder, listedeki paketleri gizler, Launcher Modu açıksa
     * kapatır (aracın kendi ana ekranı gelsin), MapControl ikonunu kapatır. Arka plan iş parçacığında çağrılmalı.
     *
     * @return gizlenen paket sayısı; cihaz sahibi değilsek -1
     */
    public static int activate(Context context, List<AppEntry> apps, String password) {
        Context app = context.getApplicationContext();
        DevicePolicyManager dpm = app.getSystemService(DevicePolicyManager.class);
        if (dpm == null || !dpm.isDeviceOwnerApp(app.getPackageName())) return -1;
        ComponentName admin = new ComponentName(app, MapControlDeviceAdminReceiver.class);

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        Set<String> planned = new HashSet<>();
        for (AppEntry e : apps) planned.add(e.packageName);
        boolean launcherWasOn = LauncherModeManager.isEnabled(app);
        String restoreIme = switchImeIfHiding(app, planned);
        // Önce kaydet: gizleme yarıda kesilirse de USB ile geri açılabilsin.
        prefs(app).edit()
                .putString(KEY_PW_SALT, toHex(salt))
                .putString(KEY_PW_HASH, hash(salt, password))
                .putStringSet(KEY_HIDDEN, planned)
                .putBoolean(KEY_RESTORE_LAUNCHER, launcherWasOn)
                .putString(KEY_RESTORE_IME, restoreIme)
                .putBoolean(KEY_ACTIVE, true)
                .commit();

        Set<String> hidden = new HashSet<>();
        for (String pkg : planned) {
            try {
                if (dpm.setApplicationHidden(admin, pkg, true) || dpm.isApplicationHidden(admin, pkg)) {
                    hidden.add(pkg);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "gizlenemedi " + pkg + ": " + e);
            }
        }
        prefs(app).edit().putStringSet(KEY_HIDDEN, hidden).commit();
        if (launcherWasOn) LauncherModeManager.setEnabled(app, false);
        setOwnIconEnabled(app, false);
        enterDormant(app);
        DiagJournal.event("servis", "servis modu acildi gizlenen=" + hidden.size() + "/" + planned.size()
                + " launcher=" + launcherWasOn + " klavye=" + restoreIme);
        return hidden.size();
    }

    /** Her şeyi geri açar ve servis modu kayıtlarını siler. Arka plan iş parçacığında çağrılabilir. */
    public static void deactivate(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = prefs(app);
        DevicePolicyManager dpm = app.getSystemService(DevicePolicyManager.class);
        Set<String> hidden = new HashSet<>(prefs.getStringSet(KEY_HIDDEN, Collections.emptySet()));
        if (dpm != null && dpm.isDeviceOwnerApp(app.getPackageName())) {
            ComponentName admin = new ComponentName(app, MapControlDeviceAdminReceiver.class);
            for (String pkg : hidden) {
                try {
                    dpm.setApplicationHidden(admin, pkg, false);
                } catch (RuntimeException e) {
                    Log.w(TAG, "acilamadi " + pkg + ": " + e);
                }
            }
        }
        restoreIme(app, prefs.getString(KEY_RESTORE_IME, null));
        restoreOwnComponents(app);
        setOwnIconEnabled(app, true);
        boolean restoreLauncher = prefs.getBoolean(KEY_RESTORE_LAUNCHER, false);
        prefs.edit()
                .remove(KEY_HIDDEN).remove(KEY_DISABLED_COMPONENTS).remove(KEY_PW_HASH).remove(KEY_PW_SALT).remove(KEY_RESTORE_LAUNCHER)
                .remove(KEY_RESTORE_IME)
                .putBoolean(KEY_ACTIVE, false)
                .commit();
        if (restoreLauncher) LauncherModeManager.setEnabled(app, true);
        DiagJournal.event("servis", "servis modu kapandi acilan=" + hidden.size());
    }

    // --- Klavye -----------------------------------------------------------------------------------------------------

    /** Kilit açılınca geri gelen klavyenin sistemce tanınmasını bekleme süresi. */
    private static final long IME_RESTORE_WAIT_MS = 5000;

    /**
     * Gizlenecek listede varsayılan klavye varsa varsayılanı sistem klavyesine alır.
     *
     * @return geri yüklenecek eski klavye kimliği; değişiklik yoksa null. Sistem klavyesine geçilemezse klavye paketi
     * listeden çıkarılır (gizlenmez).
     */
    private static String switchImeIfHiding(Context app, Set<String> planned) {
        String current = secureGet(app, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
        String pkg = imePackage(current);
        if (pkg == null || !planned.contains(pkg)) return null;
        String fallback = systemImeFallback(app);
        if (fallback == null || !setDefaultIme(app, fallback)) {
            planned.remove(pkg);
            DiagJournal.event("servis", "klavye gizlenmedi, sistem klavyesine gecilemedi: " + current);
            return null;
        }
        DiagJournal.event("servis", "klavye " + current + " -> " + fallback);
        return current;
    }

    /** Eski varsayılan klavyeyi geri seçer; paketi görünür olduktan sonra sistemin onu tanımasını kısa süre bekler. */
    private static void restoreIme(Context app, String imeId) {
        if (imeId == null) return;
        android.view.inputmethod.InputMethodManager imm =
                app.getSystemService(android.view.inputmethod.InputMethodManager.class);
        long deadline = android.os.SystemClock.uptimeMillis() + IME_RESTORE_WAIT_MS;
        while (imm != null && !imeInstalled(imm, imeId) && android.os.SystemClock.uptimeMillis() < deadline) {
            android.os.SystemClock.sleep(250);
        }
        boolean ok = setDefaultIme(app, imeId);
        DiagJournal.event("servis", "klavye geri: " + imeId + " ok=" + ok);
    }

    private static boolean imeInstalled(android.view.inputmethod.InputMethodManager imm, String imeId) {
        try {
            for (android.view.inputmethod.InputMethodInfo info : imm.getInputMethodList()) {
                if (imeId.equals(info.getId())) return true;
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    /**
     * Varsayılanın yerine geçebilecek sistem klavyesi (önce etkin olanlar). Ayarı yazma iznimiz yoksa ya da sistem klavyesi
     * yoksa null.
     */
    static String systemImeFallback(Context app) {
        if (app.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        android.view.inputmethod.InputMethodManager imm =
                app.getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (imm == null) return null;
        String enabled = secureGet(app, android.provider.Settings.Secure.ENABLED_INPUT_METHODS);
        String firstSystem = null;
        try {
            for (android.view.inputmethod.InputMethodInfo info : imm.getInputMethodList()) {
                ApplicationInfo ai = info.getServiceInfo() != null ? info.getServiceInfo().applicationInfo : null;
                if (ai == null || !isSystemApp(ai.flags, ai.sourceDir)) continue;
                if (imeListContains(enabled, info.getId())) return info.getId();
                if (firstSystem == null) firstSystem = info.getId();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "klavye listesi: " + e);
        }
        return firstSystem;
    }

    /** Klavyeyi etkin listeye ekler (yoksa) ve varsayılan yapar. */
    private static boolean setDefaultIme(Context app, String imeId) {
        try {
            android.content.ContentResolver cr = app.getContentResolver();
            String enabled = secureGet(app, android.provider.Settings.Secure.ENABLED_INPUT_METHODS);
            if (!imeListContains(enabled, imeId)) {
                String updated = enabled == null || enabled.isEmpty() ? imeId : enabled + ":" + imeId;
                android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.ENABLED_INPUT_METHODS,
                        updated);
            }
            android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE,
                    "-1");
            return android.provider.Settings.Secure.putString(cr,
                    android.provider.Settings.Secure.DEFAULT_INPUT_METHOD, imeId);
        } catch (RuntimeException e) {
            Log.w(TAG, "klavye ayarlanamadi " + imeId + ": " + e);
            return false;
        }
    }

    /** {@code enabled_input_methods} biçimi: {@code id;alt-tür;alt-tür:id2...}. */
    static boolean imeListContains(String list, String imeId) {
        if (list == null || imeId == null) return false;
        for (String entry : list.split(":")) {
            int semi = entry.indexOf(';');
            if ((semi < 0 ? entry : entry.substring(0, semi)).equals(imeId)) return true;
        }
        return false;
    }

    static String imePackage(String imeId) {
        if (imeId == null) return null;
        int slash = imeId.indexOf('/');
        return slash > 0 ? imeId.substring(0, slash) : null;
    }

    private static String secureGet(Context app, String key) {
        try {
            return android.provider.Settings.Secure.getString(app.getContentResolver(), key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * USB köklerinde kilit dosyası arar; içindeki bir satır şifreyle ya da araç seri numarasıyla eşleşirse modu kapatır.
     *
     * @return mod kapatıldıysa true
     */
    public static boolean tryUnlockFromUsb(Context context, List<File> roots) {
        if (!isActive(context)) return false;
        for (File root : roots) {
            File file = findUnlockFile(root);
            if (file == null) continue;
            String content = readSmall(file);
            if (content == null) continue;
            if (matches(context, content)) {
                DiagJournal.event("servis", "USB kilit dosyasi eslesti: " + root);
                deactivate(context);
                return true;
            }
            DiagJournal.event("servis", "USB kilit dosyasi eslesmedi: " + root);
        }
        return false;
    }

    /** Ad büyük/küçük harf duyarsız; Windows'un gizlediği uzantıyla oluşan {@code .txt.txt} de kabul edilir. */
    static File findUnlockFile(File root) {
        File[] children = root.listFiles();
        if (children == null) return null;
        String base = UNLOCK_FILE.substring(0, UNLOCK_FILE.length() - ".txt".length());
        for (File f : children) {
            if (!f.isFile()) continue;
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (name.equals(UNLOCK_FILE) || name.equals(UNLOCK_FILE + ".txt") || name.equals(base)) return f;
        }
        return null;
    }

    private static String readSmall(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[MAX_UNLOCK_FILE_BYTES];
            int total = 0;
            int n;
            while (total < buf.length && (n = in.read(buf, total, buf.length - total)) > 0) total += n;
            return new String(buf, 0, total, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "kilit dosyasi okunamadi: " + e);
            return null;
        }
    }

    /** Dosyanın herhangi bir satırı (BOM ve boşluklar temizlenmiş) şifre ya da seri numarasıyla eşleşiyor mu? */
    static boolean matches(Context context, String content) {
        SharedPreferences prefs = prefs(context);
        String hash = prefs.getString(KEY_PW_HASH, null);
        byte[] salt = fromHex(prefs.getString(KEY_PW_SALT, null));
        String serial = vehicleSerial();
        for (String line : content.replace("﻿", "").split("\\r?\\n|\\r")) {
            String candidate = line.trim();
            if (candidate.isEmpty()) continue;
            if (hash != null && salt != null && MessageDigest.isEqual(
                    hash.getBytes(StandardCharsets.US_ASCII), hash(salt, candidate).getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
            if (serial != null && candidate.equalsIgnoreCase(serial)) return true;
        }
        return false;
    }

    /** Araç donanım seri numarası (şifre unutulursa kurtarma anahtarı). Okunamazsa null. */
    public static String vehicleSerial() {
        for (String key : SERIAL_PROPS) {
            String value = systemProperty(key);
            if (value != null) {
                value = value.trim();
                if (value.length() >= MIN_SERIAL_LENGTH) return value;
            }
        }
        return null;
    }

    private static String systemProperty(String key) {
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            return (String) cls.getMethod("get", String.class).invoke(null, key);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * MapControl'ı uyutur: servisleri düzgünce durdurur, {@link #KEEP_COMPONENTS} dışındaki etkinlik/servis/alıcıları kapatır
     * (erişilebilirlik, bildirim dinleyici, açılış alıcısı dahil; sistem bunları bağlamaz ve başlatmaz). Önceki durumlar
     * kaydedilir, {@link #restoreOwnComponents} aynen geri koyar. Süreci kapatmak çağıranın işidir ({@link #killSelfSoon}).
     */
    private static void enterDormant(Context app) {
        try {
            app.stopService(new Intent(app, com.mapcontrol.service.MapControlService.class));
            app.stopService(new Intent(app, com.mapcontrol.service.RecordingService.class));
        } catch (RuntimeException e) {
            Log.w(TAG, "servis durdurulamadi: " + e);
        }
        // Erişilebilirlik hizmetinden önce temiz ayrıl; bileşen kapatılınca sistem onu "çöktü" sayıp bir daha bağlamaz.
        if (com.mapcontrol.util.AccessibilityKeeper.removeSelf(app)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        PackageManager pm = app.getPackageManager();
        Set<String> saved = new HashSet<>();
        for (String cls : ownComponents(app)) {
            if (KEEP_COMPONENTS.contains(cls) || APP_ENTRY_CLASS.equals(cls)) continue;
            ComponentName cn = new ComponentName(app.getPackageName(), cls);
            try {
                int prev = pm.getComponentEnabledSetting(cn);
                if (prev == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) continue;
                saved.add(cls + "|" + prev);
                // Kayıt her adımda: yarıda kalırsa çözerken yalnızca kapatılanlar geri açılır.
                prefs(app).edit().putStringSet(KEY_DISABLED_COMPONENTS, new HashSet<>(saved)).commit();
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            } catch (RuntimeException e) {
                Log.w(TAG, "bilesen kapatilamadi " + cls + ": " + e);
            }
        }
        DiagJournal.event("servis", "uyku: kapatilan bilesen=" + saved.size());
    }

    private static void restoreOwnComponents(Context app) {
        SharedPreferences prefs = prefs(app);
        PackageManager pm = app.getPackageManager();
        for (String entry : prefs.getStringSet(KEY_DISABLED_COMPONENTS, Collections.emptySet())) {
            int bar = entry.lastIndexOf('|');
            if (bar <= 0) continue;
            try {
                pm.setComponentEnabledSetting(new ComponentName(app.getPackageName(), entry.substring(0, bar)),
                        Integer.parseInt(entry.substring(bar + 1)), PackageManager.DONT_KILL_APP);
            } catch (RuntimeException e) {
                Log.w(TAG, "bilesen acilamadi " + entry + ": " + e);
            }
        }
        prefs.edit().remove(KEY_DISABLED_COMPONENTS).commit();
    }

    /** Manifest'teki etkinlik (alias dahil), servis ve alıcı sınıf adları; kapalı olanlar da. */
    private static List<String> ownComponents(Context app) {
        List<String> out = new ArrayList<>();
        try {
            PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(),
                    PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS
                            | PackageManager.MATCH_DISABLED_COMPONENTS);
            if (info.activities != null) for (android.content.pm.ActivityInfo a : info.activities) out.add(a.name);
            if (info.services != null) for (android.content.pm.ServiceInfo sv : info.services) out.add(sv.name);
            if (info.receivers != null) for (android.content.pm.ActivityInfo r : info.receivers) out.add(r.name);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "bilesen listesi: " + e);
        }
        return out;
    }

    /** Süreci {@code delayMs} sonra kapatır (uykuya geçiş ve çözme sonrası temiz başlangıç için). */
    public static void killSelfSoon(long delayMs) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                () -> android.os.Process.killProcess(android.os.Process.myPid()), delayMs);
    }

    /**
     * Çözme sonrası: süreç uyku modunda başlamıştı (uygulama başlangıcı atlandı). Atlanan başlangıcı bu süreçte çalıştırır ve
     * otomatik başlatma açıksa ana servisi açar. Ana iş parçacığında çağrılmalı.
     */
    public static void resumeAfterUnlock(Context context) {
        Context app = context.getApplicationContext();
        if (app instanceof com.mapcontrol.MapControlApplication) {
            ((com.mapcontrol.MapControlApplication) app).startFeatures();
        }
        // Süreç zaten normal başladıysa gözlemci kuruludur ama liste değişmedi; erişilebilirliği doğrudan geri ekle.
        com.mapcontrol.util.AccessibilityKeeper.ensure(app, "servis modu kapandi");
        com.mapcontrol.service.BootReceiver.startServiceIfAutoStartEnabled(app);
    }

    /** MapControl'ın uygulama listesi ikonu. Süreç ölmez ({@code DONT_KILL_APP}), USB dinlemeye devam eder. */
    private static void setOwnIconEnabled(Context app, boolean enabled) {
        try {
            app.getPackageManager().setComponentEnabledSetting(
                    new ComponentName(app.getPackageName(), APP_ENTRY_CLASS),
                    enabled ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (RuntimeException e) {
            Log.w(TAG, "ikon durumu degistirilemedi: " + e);
        }
    }

    static String hash(byte[] salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(password.getBytes(StandardCharsets.UTF_8));
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }

    static byte[] fromHex(String hex) {
        if (hex == null || (hex.length() & 1) != 0) return null;
        byte[] out = new byte[hex.length() / 2];
        try {
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
