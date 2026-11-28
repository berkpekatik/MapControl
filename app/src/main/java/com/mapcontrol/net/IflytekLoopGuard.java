package com.mapcontrol.net;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.mapcontrol.admin.MapControlDeviceAdminReceiver;
import com.mapcontrol.service.BootReceiver;
import com.mapcontrol.util.DiagJournal;

/**
 * iFlytek SONSUZ DÖNGÜ KORUMASI: aç/kapa ve always-on VPN yapılandırması.
 *
 * <p><b>Sorun (T19C'de araçta reprolandı, 2026-11-27):</b> {@code com.iflytek.cutefly.speechclient.hmi}
 * açılışta Wi-Fi'de internet varken {@code flamesdev.iflytekauto.cn} adresine gizlilik sözleşmesi sürümünü soruyor;
 * gelen gövde JSON nesnesi olmadığı için {@code PrivacyAgreementState.onSuccess} içinde Gson
 * {@code JsonSyntaxException} atıyor. Uygulama persistent olduğundan sistem onu 0 ms'de yeniden başlatıyor ve
 * her açılış çöküp-başlama döngüsüne giriyor. İstek başarısız olursa ({@code onFailure}) uygulama 2 kez daha deneyip
 * bırakıyor, çökme yok.
 *
 * <p><b>Çözüm:</b> root yok, {@code /system/etc/hosts} salt okunur, iFlytek uid 1000 (uygulama bazında ağ kesmek
 * tüm sistemi keser). Device owner olarak MapControl'ı always-on VPN yaparız; {@link IflytekLoopGuardVpnService}
 * yalnızca DNS'i görür ve sadece {@link #BLOCKED_DOMAINS} için NXDOMAIN döner. Lockdown kapalı.
 *
 * <p>Yalnızca iFlytek paketi kurulu araçlarda ve MapControl device owner iken çalışır; diğer araçlarda hiçbir şey yapmaz.
 */
public final class IflytekLoopGuard {

    public static final String IFLYTEK_PACKAGE = "com.iflytek.cutefly.speechclient.hmi";
    /** iFlytek'in çöken sözleşme isteğinin gittiği alan adı (firmware'e gömülü dev sunucusu). Başka bir şey eklemeyin. */
    static final String[] BLOCKED_DOMAINS = {"flamesdev.iflytekauto.cn"};

    public static final String KEY_ENABLED = "iflytekLoopGuard";
    /** Son güvenli kapanmanın zamanı ve nedeni (teşhis için). */
    static final String KEY_FAILSAFE_REASON = "iflytekLoopGuardFailSafe";
    /** Araçta doğrulanana kadar varsayılan kapalı. */
    static final boolean DEFAULT_ENABLED = false;

    private static final String TAG = "iflytek-dongu";

    private IflytekLoopGuard() {
    }

    /** Ayar ekranında gösterilsin mi: iFlytek kurulu ve MapControl device owner. */
    public static boolean isAvailable(Context context) {
        return isIflytekInstalled(context) && deviceOwnerDpm(context) != null;
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, DEFAULT_ENABLED);
    }

    static boolean shouldRun(Context context) {
        return isEnabled(context) && isAvailable(context);
    }

    public static void setEnabled(Context context, boolean on) {
        context.getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, on).apply();
        apply(context);
    }

    /**
     * Ayara göre always-on VPN'i kurar ya da kaldırır. Açılışta ve ayar değişince çağrılır.
     * Kurulunca sistem servisi kendisi başlatır (ve her açılışta yeniden başlatır).
     */
    public static void apply(Context context) {
        Context app = context.getApplicationContext();
        DevicePolicyManager dpm = deviceOwnerDpm(app);
        if (dpm == null) {
            return;
        }
        ComponentName admin = new ComponentName(app, MapControlDeviceAdminReceiver.class);
        String current = null;
        try {
            current = dpm.getAlwaysOnVpnPackage(admin);
        } catch (Exception ignored) {
        }
        boolean ours = app.getPackageName().equals(current);
        if (shouldRun(app)) {
            if (ours) {
                return;
            }
            if (current != null) {
                // Başka bir always-on VPN varsa ezme.
                DiagJournal.event(TAG, "baska always-on VPN var (" + current + "), kurulmadi");
                return;
            }
            try {
                dpm.setAlwaysOnVpnPackage(admin, app.getPackageName(), false);
                DiagJournal.event(TAG, "always-on VPN kuruldu (lockdown kapali)");
            } catch (Exception e) {
                DiagJournal.event(TAG, "always-on VPN kurulamadi: " + e);
            }
        } else if (ours) {
            try {
                dpm.setAlwaysOnVpnPackage(admin, null, false);
                DiagJournal.event(TAG, "always-on VPN kaldirildi");
            } catch (Exception e) {
                DiagJournal.event(TAG, "always-on VPN kaldirilamadi: " + e);
            }
            try {
                app.startService(new Intent(app, IflytekLoopGuardVpnService.class)
                        .setAction(IflytekLoopGuardVpnService.ACTION_STOP));
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * GÜVENLİ KAPANMA: servis tünel üzerinden DNS çözülemediğini (aracın kendi DNS'i çalışırken) tespit etti.
     * Ayarı kapatır, always-on VPN'i kaldırır, nedeni kaydeder. Kullanıcı ayarı yeniden açana kadar denenmez;
     * araç iFlytek döngüsüne geri döner ama internetsiz kalmaz.
     */
    static void failSafe(Context context, String reason) {
        Context app = context.getApplicationContext();
        app.getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, false)
                .putString(KEY_FAILSAFE_REASON, System.currentTimeMillis() + " " + reason)
                .apply();
        DiagJournal.eventNow(TAG, "GUVENLI KAPANMA: " + reason);
        android.util.Log.w("IflytekLoopGuard", "GUVENLI KAPANMA: " + reason);
        DevicePolicyManager dpm = deviceOwnerDpm(app);
        if (dpm == null) {
            return;
        }
        try {
            ComponentName admin = new ComponentName(app, MapControlDeviceAdminReceiver.class);
            if (app.getPackageName().equals(dpm.getAlwaysOnVpnPackage(admin))) {
                dpm.setAlwaysOnVpnPackage(admin, null, false);
            }
        } catch (Exception e) {
            DiagJournal.event(TAG, "always-on VPN kaldirilamadi: " + e);
        }
    }

    private static boolean isIflytekInstalled(Context context) {
        try {
            context.getPackageManager().getApplicationInfo(IFLYTEK_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
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
}
