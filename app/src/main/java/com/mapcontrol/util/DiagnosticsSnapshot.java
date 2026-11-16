package com.mapcontrol.util;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Process;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;

import com.mapcontrol.dvr.DvrController;
import com.mapcontrol.gps.LinkState;
import com.mapcontrol.manager.WebServerHost;
import com.mapcontrol.media.LauncherMediaNotificationListener;
import com.mapcontrol.service.GlobalBackService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tanılama paketinin "DURUM" bölümü: gönderim anındaki mantıksal durum. logcat'te iz bırakmayan ama sorunu
 * açıklayan bilgiler; örn. erişilebilirlik ayarda açık olduğu hâlde sisteme bağlı mı, araçtaki paket yükleyici
 * hangisi ve otomatik onaylayıcı onu tanıyor mu, hangi ekranlar var. Her parça ayrı korunur: biri hata verirse
 * diğerleri yazılır. Yan etkisizdir (hiçbir şey başlatmaz, tekil örnek oluşturmaz).
 */
final class DiagnosticsSnapshot {

    private interface Part {
        void write(DiagnosticsCollector.Sink s) throws Exception;
    }

    private DiagnosticsSnapshot() {
    }

    static void write(Context app, DiagnosticsCollector.Sink s) throws java.io.IOException {
        s.title("DURUM: ERISILEBILIRLIK VE BILDIRIM");
        part(s, x -> accessibility(app, x));
        s.title("DURUM: IZINLER");
        part(s, x -> permissions(app, x));
        s.title("DURUM: UYGULAMA KURULUMU");
        part(s, x -> installer(app, x));
        s.title("DURUM: EKRAN VE BOLGE");
        part(s, x -> displays(app, x));
        s.title("DURUM: UYGULAMA");
        part(s, x -> appState(app, x));
        part(s, x -> services(app, x));
        s.title("DURUM: AG VE DEPOLAMA");
        part(s, x -> network(app, x));
        part(s, x -> storage(app, x));
        File homeDiag = HomeRoleDiag.file(app);
        if (homeDiag != null && homeDiag.isFile()) {
            s.title("ANA EKRAN ROLU TESHISI (son)");
            part(s, x -> copyFile(homeDiag, x));
        }
    }

    private static void part(DiagnosticsCollector.Sink s, Part p) throws java.io.IOException {
        try {
            p.write(s);
        } catch (java.io.IOException e) {
            throw e;
        } catch (Throwable t) {
            s.line("okunamadi: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static void accessibility(Context app, DiagnosticsCollector.Sink s) throws Exception {
        boolean registered = GlobalBackService.isRegisteredInSystemAccessibilitySettings(app);
        boolean bound = GlobalBackService.isServiceEnabled();
        s.line("erisilebilirlik ayarda acik: " + registered);
        s.line("erisilebilirlik hizmeti bagli: " + bound);
        if (registered && !bound) {
            s.line("UYARI: ayarda acik ama sisteme bagli degil (cokmus/oldurulmus olabilir). Otomatik INSTALL,"
                    + " geri tusu ve Yandex okuma calismaz; hizmeti kapatip acmak gerekir.");
        }
        s.line("accessibility_enabled: " + secure(app, Settings.Secure.ACCESSIBILITY_ENABLED));
        s.line("enabled_accessibility_services: " + secure(app, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
        boolean listenerSetting = false;
        String listeners = secure(app, "enabled_notification_listeners");
        if (listeners != null) {
            listenerSetting = listeners.contains(app.getPackageName() + "/")
                    && listeners.contains("LauncherMediaNotificationListener");
        }
        s.line("bildirim dinleyici ayarda acik: " + listenerSetting);
        s.line("bildirim dinleyici bagli: " + LauncherMediaNotificationListener.isConnected());
        if (listenerSetting && !LauncherMediaNotificationListener.isConnected()) {
            s.line("UYARI: bildirim dinleyici ayarda acik ama bagli degil (navigasyon/medya bilgisi gelmez)");
        }
    }

    private static void permissions(Context app, DiagnosticsCollector.Sink s) throws Exception {
        StringBuilder yes = new StringBuilder();
        StringBuilder no = new StringBuilder();
        for (GrantedPermissionStatus.Row row : GrantedPermissionStatus.read(app)) {
            StringBuilder target = row.granted ? yes : no;
            if (target.length() > 0) {
                target.append(", ");
            }
            target.append(row.label);
        }
        s.line("VAR: " + yes);
        s.line("YOK: " + no);
    }

    private static void installer(Context app, DiagnosticsCollector.Sink s) throws Exception {
        PackageManager pm = app.getPackageManager();
        s.line("bilinmeyen kaynak izni (canRequestPackageInstalls): " + pm.canRequestPackageInstalls());
        DevicePolicyManager dpm = (DevicePolicyManager) app.getSystemService(Context.DEVICE_POLICY_SERVICE);
        s.line("cihaz sahibi (sessiz session kurulumu): "
                + (dpm != null && dpm.isDeviceOwnerApp(app.getPackageName())));
        s.line("sessiz dex araci (asset): " + assetExists(app, "mapcontrol-silent-installer.dex"));

        Intent view = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse("content://diag/x.apk"), "application/vnd.android.package-archive");
        resolvers(pm, s, "apk acan", view);
        resolvers(pm, s, "INSTALL_PACKAGE", new Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setData(Uri.parse("content://diag/x.apk")));
        resolvers(pm, s, "UNINSTALL_PACKAGE", new Intent(Intent.ACTION_UNINSTALL_PACKAGE)
                .setData(Uri.parse("package:diag.x")));

        List<PackageInstaller.SessionInfo> sessions = pm.getPackageInstaller().getMySessions();
        s.line("acik kurulum oturumlari: " + sessions.size());
        for (PackageInstaller.SessionInfo info : sessions) {
            s.line("  oturum id=" + info.getSessionId() + " paket=" + info.getAppPackageName()
                    + " aktif=" + info.isActive() + " ilerleme=" + info.getProgress());
        }
    }

    private static void resolvers(PackageManager pm, DiagnosticsCollector.Sink s, String label, Intent intent)
            throws java.io.IOException {
        List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (list.isEmpty()) {
            s.line(label + ": cozumleyen yok");
            return;
        }
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            s.line(label + ": " + pkg + "/" + ri.activityInfo.name
                    + " | otomatik onaylayici tanir: " + (InstallerConfirmClicker.isInstallerPackage(pkg) ? "evet" : "HAYIR"));
        }
    }

    private static boolean assetExists(Context app, String name) {
        try {
            app.getAssets().open(name).close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void displays(Context app, DiagnosticsCollector.Sink s) throws Exception {
        s.line("uygulama dili: " + Locale.getDefault().toLanguageTag()
                + " | sistem dili: " + Resources.getSystem().getConfiguration().getLocales().toLanguageTags());
        try {
            s.line("arac profili: " + com.mapcontrol.launcher.VehicleProfile.Companion.detectFromDisplay(app).getKey());
        } catch (Throwable t) {
            s.line("arac profili okunamadi: " + t.getClass().getSimpleName());
        }
        DisplayManager dm = (DisplayManager) app.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) {
            s.line("DisplayManager yok");
            return;
        }
        for (Display d : dm.getDisplays()) {
            DisplayMetrics m = new DisplayMetrics();
            d.getRealMetrics(m);
            s.line("ekran id=" + d.getDisplayId() + " ad=" + d.getName() + " " + m.widthPixels + "x" + m.heightPixels
                    + " dpi=" + m.densityDpi + " donus=" + d.getRotation() + " durum=" + d.getState()
                    + " bayrak=0x" + Integer.toHexString(d.getFlags()));
        }
    }

    private static void appState(Context app, DiagnosticsCollector.Sink s) throws Exception {
        s.line("surec yasi: " + ((SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / 1000L) + " sn");
        ActivityManager.RunningAppProcessInfo me = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(me);
        s.line("surec onemi: " + me.importance);
        s.line("launcher modu: acik=" + LauncherModeManager.isEnabled(app)
                + " varsayilan HOME=" + LauncherModeManager.isDefaultHome(app)
                + " rol kilitli=" + LauncherModeManager.isHomeRoleLocked(app));
        ResolveInfo home = app.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        s.line("HOME cozumu: " + (home == null || home.activityInfo == null ? "yok"
                : home.activityInfo.packageName + "/" + home.activityInfo.name));
        WebServerHost web = WebServerHost.peek();
        s.line("web sunucu: " + (web == null ? "olusturulmadi" : "calisiyor=" + web.isRunning()));
        s.line("GPS link: calisiyor=" + LinkState.isRunning() + " durum=" + LinkState.status()
                + " son konum yasi=" + LinkState.fixAgeMs() + " ms");
        DvrController dvr = DvrController.peek();
        if (dvr == null) {
            s.line("kamera/DVR: olusturulmadi");
        } else {
            s.line("kamera/DVR: destek=" + dvr.isCameraSupported() + " kontak=" + dvr.isIgnitionOn()
                    + " oto ic=" + dvr.isAutoInternalEnabled() + " oto usb=" + dvr.isAutoUsbEnabled());
        }
    }

    @SuppressWarnings("deprecation")
    private static void services(Context app, DiagnosticsCollector.Sink s) throws Exception {
        ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> list = am.getRunningServices(200);
        List<String> rows = new ArrayList<>();
        for (ActivityManager.RunningServiceInfo info : list) {
            if (info.service == null || !app.getPackageName().equals(info.service.getPackageName())) {
                continue;
            }
            rows.add(info.service.getShortClassName() + " basladi=" + info.started + " on plan=" + info.foreground
                    + " istemci=" + info.clientCount + " cokme=" + info.crashCount
                    + " son etkinlik=" + ((SystemClock.uptimeMillis() - info.lastActivityTime) / 1000L) + " sn once");
        }
        s.line("calisan hizmetler: " + rows.size());
        for (String row : rows) {
            s.line("  " + row);
        }
    }

    private static void network(Context app, DiagnosticsCollector.Sink s) throws Exception {
        WifiManager wifi = (WifiManager) app.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        s.line("wifi acik: " + (wifi != null && wifi.isWifiEnabled()));
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }
        Network active = cm.getActiveNetwork();
        NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
        if (caps == null) {
            s.line("aktif ag: yok");
            return;
        }
        s.line("aktif ag: wifi=" + caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                + " hucresel=" + caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                + " ethernet=" + caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                + " internet=" + caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                + " dogrulandi=" + caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
    }

    private static void storage(Context app, DiagnosticsCollector.Sink s) throws Exception {
        s.line("dahili: " + space(app.getFilesDir()));
        File ext = app.getExternalFilesDir(null);
        s.line("harici: " + (ext == null ? "yok" : space(ext)));
        s.line("onbellek (cache) klasoru: " + space(app.getCacheDir()));
    }

    private static String space(File dir) {
        StatFs st = new StatFs(dir.getAbsolutePath());
        return "bos " + (st.getAvailableBytes() >> 20) + " MB / toplam " + (st.getTotalBytes() >> 20) + " MB";
    }

    private static void copyFile(File file, DiagnosticsCollector.Sink s) throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!s.line(line)) {
                    return;
                }
            }
        }
    }

    static void journal(DiagnosticsCollector.Sink s) throws java.io.IOException {
        s.title("OLAY GUNLUGU (kalici, eskiden yeniye)");
        List<File> files = DiagJournal.files();
        if (files.isEmpty()) {
            s.line("bos");
            return;
        }
        for (File f : files) {
            part(s, x -> copyFile(f, x));
        }
    }

    private static String secure(Context app, String key) {
        try {
            return Settings.Secure.getString(app.getContentResolver(), key);
        } catch (Throwable t) {
            return "okunamadi";
        }
    }
}
