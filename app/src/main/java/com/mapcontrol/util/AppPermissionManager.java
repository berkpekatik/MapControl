package com.mapcontrol.util;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ServiceInfo;
import android.provider.Settings;

import com.mapcontrol.admin.MapControlDeviceAdminReceiver;

import java.util.ArrayList;
import java.util.List;

/**
 * Başka bir yüklü uygulamaya, MapControl'ün sahip olduğu yetkilerle izin verir / geri alır.
 *
 * <p>Araçta ADB kablosuz açılamadığı için (Chery sistemi kapalı) bu sınıf yalnızca uygulamanın
 * kendi UID'siyle yapılabilenleri kapsar:
 * <ul>
 *   <li><b>Tehlikeli (runtime) izinler</b> — cihaz sahibi (device owner) olarak
 *       {@link DevicePolicyManager#setPermissionGrantState}. İmza gerektirmez.</li>
 *   <li><b>Erişilebilirlik hizmeti</b> ve <b>bildirim erişimi</b> — {@code WRITE_SECURE_SETTINGS}
 *       ile {@code enabled_accessibility_services} / {@code enabled_notification_listeners}
 *       listesine ekleme/çıkarma (mevcut girişler korunur).</li>
 * </ul>
 *
 * <p>appops (üstte gösterme, kullanım verisi, sahte konum...), Doze beyazlistesi ve imza/sistem
 * izinleri (READ_LOGS, WRITE_SECURE_SETTINGS...) bu yolla <b>verilemez</b>: shell/sistem imzası
 * ister. Onlar için FrostNav + USB gerekir. {@link Kind#ADB_ONLY} bu durumu işaretler.
 */
public final class AppPermissionManager {

    private static final String SECURE_ACCESSIBILITY = "enabled_accessibility_services";
    private static final String SECURE_ACCESSIBILITY_ENABLED = "accessibility_enabled";
    private static final String SECURE_NOTIFICATION_LISTENERS = "enabled_notification_listeners";

    private final Context context;

    public AppPermissionManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Bir iznin / girişin araç içinden verilip verilemeyeceği. */
    public enum Kind {
        /** Device owner ile verilebilen tehlikeli (runtime) izin. */
        RUNTIME,
        /** WRITE_SECURE_SETTINGS ile açılabilen erişilebilirlik hizmeti. */
        ACCESSIBILITY,
        /** WRITE_SECURE_SETTINGS ile açılabilen bildirim dinleyicisi. */
        NOTIFICATION_LISTENER,
        /** Yalnızca ADB/shell ile verilebilen izin (appops, doze, imza izinleri). */
        ADB_ONLY
    }

    /** Ekranda tek satır: bir izin ya da bir hizmet. */
    public static final class Entry {
        public final Kind kind;
        /** İzin adı (RUNTIME/ADB_ONLY) ya da hizmetin tam bileşen adı (ACCESSIBILITY/NOTIFICATION). */
        public final String id;
        public final String label;
        public boolean granted;

        Entry(Kind kind, String id, String label, boolean granted) {
            this.kind = kind;
            this.id = id;
            this.label = label;
            this.granted = granted;
        }

        /** Bu giriş araç içinden değiştirilebiliyor mu. */
        public boolean controllable() {
            return kind != Kind.ADB_ONLY;
        }
    }

    // ---------------------------------------------------------------- yetki durumu

    public boolean isDeviceOwner() {
        try {
            DevicePolicyManager dpm = dpm();
            return dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean canWriteSecureSettings() {
        try {
            return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception ignored) {
            return false;
        }
    }

    // ---------------------------------------------------------------- listeleme

    /**
     * Hedef uygulamanın beyan ettiği izinleri ve hizmetleri, araç içinden yönetilebilenler önce
     * gelecek şekilde döndürür. Boş liste, okunamadığını değil izin istemediğini de gösterebilir.
     */
    public List<Entry> read(String packageName) {
        List<Entry> runtime = new ArrayList<>();
        List<Entry> adbOnly = new ArrayList<>();
        List<Entry> services = new ArrayList<>();

        PackageManager pm = context.getPackageManager();
        PackageInfo info;
        try {
            info = pm.getPackageInfo(packageName,
                    PackageManager.GET_PERMISSIONS | PackageManager.GET_SERVICES);
        } catch (Exception e) {
            return runtime;
        }

        if (info.requestedPermissions != null) {
            for (String perm : info.requestedPermissions) {
                if (isDangerous(pm, perm)) {
                    runtime.add(new Entry(Kind.RUNTIME, perm, shortName(perm),
                            isRuntimeGranted(packageName, perm)));
                } else if (isAdbOnlyOfInterest(perm)) {
                    adbOnly.add(new Entry(Kind.ADB_ONLY, perm, shortName(perm), false));
                }
            }
        }

        if (info.services != null) {
            for (ServiceInfo svc : info.services) {
                String component = packageName + "/" + svc.name;
                if (android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE.equals(svc.permission)) {
                    services.add(new Entry(Kind.ACCESSIBILITY, component,
                            shortName(svc.name), isAccessibilityEnabled(component)));
                } else if (android.Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE.equals(svc.permission)) {
                    services.add(new Entry(Kind.NOTIFICATION_LISTENER, component,
                            shortName(svc.name), isNotificationListenerEnabled(component)));
                }
            }
        }

        List<Entry> all = new ArrayList<>(runtime.size() + services.size() + adbOnly.size());
        all.addAll(runtime);
        all.addAll(services);
        all.addAll(adbOnly);
        return all;
    }

    // ---------------------------------------------------------------- değiştirme

    /** Girişi verir/geri alır; başarılıysa true. ADB_ONLY girişleri için her zaman false. */
    public boolean apply(String packageName, Entry entry, boolean grant) {
        switch (entry.kind) {
            case RUNTIME:
                return setRuntimePermission(packageName, entry.id, grant);
            case ACCESSIBILITY:
                return setSecureListEntry(SECURE_ACCESSIBILITY, entry.id, grant, true);
            case NOTIFICATION_LISTENER:
                return setSecureListEntry(SECURE_NOTIFICATION_LISTENERS, entry.id, grant, false);
            default:
                return false;
        }
    }

    private boolean setRuntimePermission(String packageName, String permission, boolean grant) {
        try {
            DevicePolicyManager dpm = dpm();
            if (dpm == null || !isDeviceOwner()) {
                return false;
            }
            int state = grant
                    ? DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                    : DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;
            boolean ok = dpm.setPermissionGrantState(admin(), packageName, permission, state);
            return ok && isRuntimeGranted(packageName, permission) == grant;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * {@code enabled_accessibility_services} / {@code enabled_notification_listeners} gibi ':' ile
     * ayrılmış secure ayar listesine bileşeni ekler ya da çıkarır. Diğer girişler korunur.
     */
    private boolean setSecureListEntry(String key, String component, boolean enable, boolean alsoFlagEnabled) {
        if (!canWriteSecureSettings()) {
            return false;
        }
        try {
            String current = Settings.Secure.getString(context.getContentResolver(), key);
            String updated = enable ? withEntry(current, component) : withoutEntry(current, component);
            Settings.Secure.putString(context.getContentResolver(), key, updated);
            if (alsoFlagEnabled && enable) {
                Settings.Secure.putInt(context.getContentResolver(), SECURE_ACCESSIBILITY_ENABLED, 1);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- durum okuma

    private boolean isRuntimeGranted(String packageName, String permission) {
        try {
            return context.getPackageManager().checkPermission(permission, packageName)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean isAccessibilityEnabled(String component) {
        return listContains(Settings.Secure.getString(context.getContentResolver(), SECURE_ACCESSIBILITY), component);
    }

    public boolean isNotificationListenerEnabled(String component) {
        return listContains(Settings.Secure.getString(context.getContentResolver(), SECURE_NOTIFICATION_LISTENERS), component);
    }

    // ---------------------------------------------------------------- yardımcılar

    private DevicePolicyManager dpm() {
        return (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    private ComponentName admin() {
        return new ComponentName(context, MapControlDeviceAdminReceiver.class);
    }

    private static boolean isDangerous(PackageManager pm, String permission) {
        try {
            PermissionInfo pi = pm.getPermissionInfo(permission, 0);
            return pi.getProtection() == PermissionInfo.PROTECTION_DANGEROUS;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** appops/doze/imza izinlerinin en çok istenenleri; ekranda "USB gerekli" olarak gösterilir. */
    private static boolean isAdbOnlyOfInterest(String permission) {
        switch (permission) {
            case "android.permission.SYSTEM_ALERT_WINDOW":
            case "android.permission.PACKAGE_USAGE_STATS":
            case "android.permission.REQUEST_INSTALL_PACKAGES":
            case "android.permission.MANAGE_EXTERNAL_STORAGE":
            case "android.permission.WRITE_SETTINGS":
            case "android.permission.READ_LOGS":
            case "android.permission.WRITE_SECURE_SETTINGS":
            case "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS":
                return true;
            default:
                return false;
        }
    }

    static String shortName(String value) {
        if (value == null) {
            return "";
        }
        int dot = value.lastIndexOf('.');
        return dot >= 0 && dot < value.length() - 1 ? value.substring(dot + 1) : value;
    }

    static boolean listContains(String list, String component) {
        if ((list == null || list.isEmpty()) || "null".equals(list)) {
            return false;
        }
        for (String raw : list.split(":")) {
            if (raw.trim().equals(component)) {
                return true;
            }
        }
        return false;
    }

    static String withEntry(String list, String component) {
        if ((list == null || list.isEmpty()) || "null".equals(list.trim())) {
            return component;
        }
        if (listContains(list, component)) {
            return list;
        }
        return list + ":" + component;
    }

    static String withoutEntry(String list, String component) {
        if ((list == null || list.isEmpty()) || "null".equals(list.trim())) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String raw : list.split(":")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.equals(component)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(trimmed);
        }
        return sb.toString();
    }
}
