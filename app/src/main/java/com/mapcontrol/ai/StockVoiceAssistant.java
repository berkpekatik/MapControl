package com.mapcontrol.ai;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import com.mapcontrol.admin.MapControlDeviceAdminReceiver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Aracın kendi sesli asistanını (Chery / iFlytek "Hello Chery") kapatır ki direksiyon ses tuşu yalnızca MapControl AI'ı
 * açsın. Intent ile susturma (iFlytek {@code stopvr}/{@code EXIT}) T19C'de işe yaramadı; {@code pm disable-user}
 * persistent {@code com.desaysv.vrservice}'i durdurmadı (2026-11-23). Bunun yerine cihaz sahibi (device owner) yetkisiyle
 * paketler askıya alınır ve gizlenir ({@code setPackagesSuspended} + {@code setApplicationHidden}). Hiçbir şey silinmez, {@link #apply} {@code false}
 * ile geri açılır.
 *
 * <p>Yan etki: aracın kendi sesli komutları kapanır. iFlytek'in ana paketi (TTS) açık kalır ki MapControl AI cevapları
 * aracın sesiyle okunsun ({@link #KEEP_FOR_TTS}).
 * Asistan uygulaması kaldırılırsa açılışta ({@link #reapplyOnStart}) Chery geri açılır.
 */
public final class StockVoiceAssistant {
    private static final String TAG = "StockVoiceAssistant";
    private static final String PREFS = "MapControlPrefs";
    private static final String KEY_DISABLED = "assistantDisableStockVoice";

    /**
     * Gizlenmez: iFlytek'in ana paketi MapControl AI'ın cevaplarını aracın sesiyle okuyan TTS servisini taşır (T19C'de
     * sistem TTS motoru yok). Daha önce gizlendiyse kapatırken geri açılır. "Hello Chery" uyandırması bunun yerine
     * {@link #WAKE_SETTING} ile kapatılmaya çalışılır.
     */
    static final String[] KEEP_FOR_TTS = {"com.iflytek.cutefly.speechclient.hmi"};
    /** iFlytek uyandırma sözü ("Hello Chery") açık/kapalı. {@code Settings.Global}'dedir (iFlytek
     * {@code VoiceCoreSettingUtil}, varsayılan 1, ContentObserver ile canlı izlenir); PR #48 yanlışlıkla System'e yazıyordu.
     * Yazmak WRITE_SECURE_SETTINGS ister (install.sh verir). */
    static final String WAKE_SETTING = "com.iflytek.autofly.IFLY_SPEECH_WAKE";

    static final String[] PACKAGES = {
            "com.desaysv.voicezone",
            "com.iflytek.autofly.voicecoreservice",
            "com.iflytek.autofly.sceneengine",
            "com.iflytek.autofly.sceneengineeu",
            "com.iflytek.avatarService",
            "com.desaysv.vrcontrol",
            "com.desaysv.vrservice",
            "com.desaysv.ivi.vds.vr",
            "com.iflytek.autofly.icvp.pro",
            "com.iflytek.autofly.icvpservice.pro",
    };

    /** @param failed değiştirilemeyen kurulu paketler; {@code owner} false ise hiçbir şey yapılmadı. */
    public static final class Result {
        public final boolean owner;
        public final boolean disabled;
        public final List<String> changed;
        public final List<String> failed;

        Result(boolean owner, boolean disabled, List<String> changed, List<String> failed) {
            this.owner = owner;
            this.disabled = disabled;
            this.changed = changed;
            this.failed = failed;
        }
    }

    private StockVoiceAssistant() {
    }

    public static boolean isDisabledPref(Context context) {
        return prefs(context).getBoolean(KEY_DISABLED, false);
    }

    public static boolean isDeviceOwner(Context context) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        return dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());
    }

    /** Askıya al + gizle ({@code disable=true}) ya da geri aç. Tercihi kaydeder. Ana iş parçacığında çağrılabilir. */
    public static Result apply(Context context, boolean disable) {
        Context app = context.getApplicationContext();
        DevicePolicyManager dpm = app.getSystemService(DevicePolicyManager.class);
        List<String> changed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        if (dpm == null || !dpm.isDeviceOwnerApp(app.getPackageName())) {
            return new Result(false, isDisabledPref(app), changed, failed);
        }
        ComponentName admin = new ComponentName(app, MapControlDeviceAdminReceiver.class);
        List<String> installed = new ArrayList<>();
        for (String pkg : PACKAGES) {
            if (isInstalled(app, pkg)) installed.add(pkg);
        }
        String[] targets = installed.toArray(new String[0]);
        List<String> notSuspended = new ArrayList<>();
        try {
            notSuspended.addAll(Arrays.asList(dpm.setPackagesSuspended(admin, targets, disable)));
        } catch (RuntimeException e) {
            Log.w(TAG, "setPackagesSuspended: " + e);
            notSuspended.addAll(installed);
        }
        for (String pkg : installed) {
            boolean hiddenOk;
            try {
                hiddenOk = dpm.setApplicationHidden(admin, pkg, disable) || dpm.isApplicationHidden(admin, pkg) == disable;
            } catch (RuntimeException e) {
                Log.w(TAG, "setApplicationHidden " + pkg + ": " + e);
                hiddenOk = false;
            }
            if (hiddenOk || !notSuspended.contains(pkg)) changed.add(pkg);
            else failed.add(pkg);
        }
        if (disable) {
            for (String pkg : KEEP_FOR_TTS) {
                if (!isInstalled(app, pkg)) continue;
                try {
                    dpm.setPackagesSuspended(admin, new String[]{pkg}, false);
                    dpm.setApplicationHidden(admin, pkg, false);
                } catch (RuntimeException e) {
                    Log.w(TAG, "TTS paketi açılamadı " + pkg + ": " + e);
                }
            }
        }
        setWakeWord(app, !disable);
        prefs(app).edit().putBoolean(KEY_DISABLED, disable).apply();
        com.mapcontrol.util.DiagJournal.event("asistan", "Chery asistanı " + (disable ? "kapatıldı" : "açıldı")
                + " değişen=" + changed.size() + " başarısız=" + failed);
        return new Result(true, disable, changed, failed);
    }

    /**
     * Servis açılışında: tercih açıksa yeniden uygular (sistem güncellemesi sonrası); asistan uygulaması kaldırıldıysa
     * Chery'yi geri açar (ses tuşu sahipsiz kalmasın).
     */
    public static void reapplyOnStart(Context context) {
        if (!isDisabledPref(context)) return;
        apply(context, AssistantLauncher.isInstalled(context));
    }

    /** "Hello Chery" uyandırmasını aç/kapat. Android izin vermezse yalnızca kayda yazılır. */
    private static void setWakeWord(Context context, boolean enabled) {
        try {
            android.provider.Settings.Global.putInt(context.getContentResolver(), WAKE_SETTING, enabled ? 1 : 0);
        } catch (RuntimeException e) {
            Log.w(TAG, "uyandırma ayarı yazılamadı: " + e);
            com.mapcontrol.util.DiagJournal.event("asistan", "IFLY_SPEECH_WAKE yazılamadı: " + e.getClass().getSimpleName());
        }
    }

    private static boolean isInstalled(Context context, String pkg) {
        try {
            // Gizli paketler de bulunsun (geri açarken gerekir).
            context.getPackageManager().getPackageInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
