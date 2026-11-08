package com.mapcontrol.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.List;
import java.util.function.Consumer;

/**
 * Telefondan gelen bağlantıyı açar: Google Haritalar, Yandex ve YouTube bağlantıları doğrudan uygulamasına verilir,
 * gerisi sistemin seçtiği uygulamaya. Yalnızca bir {@link Context} ister; FrostNav ekranı açık olmasa da (launcher
 * modunda) çalışır, ekranı öne getirmez. Ana iş parçacığında çağrılır.
 */
public final class LinkOpener {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Context context;
    private final Consumer<String> log;

    private LinkOpener(Context context, Consumer<String> log) {
        this.context = context;
        this.log = log != null ? log : msg -> { };
    }

    /** [log] Günlük sekmesine ya da logcat'e yazar; null olabilir. */
    public static void open(Context context, String url, Consumer<String> log) {
        LinkOpener opener = new LinkOpener(context, log);
        try {
            String u = url != null ? url.trim() : "";
            if (u.isEmpty()) {
                return;
            }
            // Google Haritalar bağlantısı: tarayıcı seçicisine düşmeden doğrudan Google Haritalar'a ver.
            if (MapsLink.isGoogleMaps(u) && opener.openGoogleMapsLink(u)) {
                return;
            }
            if (MapsLink.isYandex(u) && opener.openYandexLink(u)) {
                return;
            }
            if (YouTubeLink.isYouTube(u) && opener.openYouTubeLink(u)) {
                return;
            }
            opener.openLinkViaSystem(u);
        } catch (Exception e) {
            opener.fail(e);
        }
    }

    private void fail(Exception e) {
        Toast.makeText(context, "Link açılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
        log.accept("Link açma hatası: " + e.getMessage());
    }

    /**
     * Bağlantıyı sistemin seçtiği uygulamayla açar. Aynı host'ta /q açılırken /maps gibi yollarda Yandex Haritalar
     * (veya App Link) intent'i alıp ekran göstermeyebiliyor; bu URL'lerde önce seçici gösterilir.
     */
    private void openLinkViaSystem(String u) {
        try {
            Uri uri = Uri.parse(u);
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            String scheme = uri.getScheme();
            if (scheme != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                i.addCategory(Intent.CATEGORY_BROWSABLE);
            }
            if (shouldShowChooserToPickBrowserOrMaps(uri)) {
                // Yandex /maps, goo.gl harita kısası: önce tek hedef uyg. sessizce çalışıp
                // ekran göstermeyebiliyor; listeden tarayıcı seçilebilir.
                Intent chooser = Intent.createChooser(i, "Linki aç (tarayıcı önerilir)");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooser);
            } else {
                try {
                    context.startActivity(i);
                } catch (ActivityNotFoundException noHandler) {
                    Intent chooser = Intent.createChooser(i, "Bağlantıyı aç");
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(chooser);
                }
            }
        } catch (Exception e) {
            fail(e);
        }
    }

    /**
     * Google Haritalar bağlantısını doğrudan Google Haritalar'a verir (açık paket adıyla, seçici yok).
     * Sırayla dener: bağlantının kendisi, koordinat varsa rota/konum, yer adı varsa arama. Kısa bağlantıda
     * (maps.app.goo.gl) koordinat bağlantıda yoktur; yönlendirme arka planda çözülür.
     *
     * @return Google Haritalar kurulu değilse false (çağıran sistemin yoluna döner); aksi halde true
     */
    private boolean openGoogleMapsLink(String u) {
        if (!isPackageInstalled(AppLaunchHelper.GOOGLE_MAPS_PACKAGE)) {
            log.accept("Google Haritalar kurulu değil; sistem yoluyla açılıyor");
            return false;
        }
        if (startGoogleMaps(Uri.parse(u), "bağlantı")) {
            return true;
        }
        if (openGoogleMapsTarget(u)) {
            return true;
        }
        if (MapsLink.isShortLink(u)) {
            log.accept("Kısa bağlantı çözülüyor: " + u);
            new Thread(() -> {
                String resolved = MapsLink.resolve(u);
                MAIN.post(() -> {
                    log.accept("Kısa bağlantı çözüldü: " + resolved);
                    if (!resolved.equals(u) && (startGoogleMaps(Uri.parse(resolved), "çözülen bağlantı")
                            || openGoogleMapsTarget(resolved))) {
                        return;
                    }
                    openLinkViaSystem(u);
                });
            }, "MapsLinkResolve").start();
            return true;
        }
        return false;
    }

    /**
     * Yandex bağlantısı (Navi/Haritalar ayrımı yok): kurulu Yandex uygulaması açar; hiçbiri yoksa hedef
     * koordinatı varsa Google Haritalar rotasına düşer.
     *
     * @return açıldıysa true; false ise çağıran sistemin yoluna döner
     */
    private boolean openYandexLink(String u) {
        for (String pkg : AppLaunchHelper.YANDEX_PACKAGES) {
            if (!isPackageInstalled(pkg)) {
                continue;
            }
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(u));
            i.setPackage(pkg);
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (context.getPackageManager().queryIntentActivities(i, 0).isEmpty()) {
                log.accept("Yandex (" + pkg + ") bu bağlantıyı açmıyor");
                continue;
            }
            try {
                context.startActivity(i);
                log.accept("Yandex'e verildi (" + pkg + "): " + u);
                return true;
            } catch (Exception e) {
                log.accept("Yandex açılamadı (" + pkg + "): " + e.getMessage());
            }
        }
        log.accept("Yandex yok; Google Haritalar deneniyor");
        double[] dest = MapsLink.yandexDestination(u);
        if (dest != null && isPackageInstalled(AppLaunchHelper.GOOGLE_MAPS_PACKAGE)) {
            return startGoogleMaps(Uri.parse(MapsLink.navigationUri(dest[0], dest[1])), "Yandex rotası")
                    || startGoogleMaps(Uri.parse(MapsLink.geoUri(dest[0], dest[1])), "Yandex konumu");
        }
        return false;
    }

    /** Bağlantıdan koordinat ya da yer adı çıkarıp Google Haritalar'ın anladığı bir adresle açar. */
    private boolean openGoogleMapsTarget(String url) {
        double[] c = MapsLink.coordinates(url);
        if (c != null) {
            return startGoogleMaps(Uri.parse(MapsLink.navigationUri(c[0], c[1])), "rota")
                    || startGoogleMaps(Uri.parse(MapsLink.geoUri(c[0], c[1])), "konum");
        }
        String name = MapsLink.placeName(url);
        return name != null && startGoogleMaps(Uri.parse(MapsLink.searchUri(name)), "yer araması");
    }

    private boolean startGoogleMaps(Uri uri, String what) {
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        i.setPackage(AppLaunchHelper.GOOGLE_MAPS_PACKAGE);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            i.addCategory(Intent.CATEGORY_BROWSABLE);
        }
        List<ResolveInfo> handlers = context.getPackageManager().queryIntentActivities(i, 0);
        if (handlers.isEmpty()) {
            // Hangi biçimleri anladığı araçta bilinmediği için tanı kaydı: Günlük sekmesinde görünür.
            log.accept("Google Haritalar bu biçimi açmıyor (" + what + "): " + uri.getScheme() + ":");
            return false;
        }
        try {
            context.startActivity(i);
            log.accept("Google Haritalar'a verildi (" + what + "): " + uri);
            return true;
        } catch (Exception e) {
            log.accept("Google Haritalar açılamadı (" + what + "): " + e.getMessage());
            return false;
        }
    }

    /**
     * YouTube bağlantısını doğrudan YouTube uygulamasına verir (açık paket adıyla, seçici ve tarayıcı yok).
     * {@link YouTubeLink#PACKAGES} sırasıyla denenir: resmî YouTube, sonra ReVanced. Paket kurulu ama bağlantıyı
     * kabul etmiyorsa (ReVanced'da bağlantı açma izni verilmemiş olabilir) sıradakine geçilir; her deneme Günlük
     * sekmesine yazılır.
     *
     * @return Hiçbiri açmadıysa false; çağıran sistemin yoluna (tarayıcı) döner
     */
    private boolean openYouTubeLink(String u) {
        Uri uri = Uri.parse(u);
        for (String pkg : YouTubeLink.PACKAGES) {
            if (!isPackageInstalled(pkg)) {
                continue;
            }
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.setPackage(pkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            if (context.getPackageManager().queryIntentActivities(i, 0).isEmpty()) {
                log.accept("YouTube bağlantısı bu uygulamada açılmıyor: " + pkg);
                continue;
            }
            try {
                context.startActivity(i);
                log.accept("YouTube'a verildi (" + pkg + "): " + uri);
                return true;
            } catch (Exception e) {
                log.accept("YouTube açılamadı (" + pkg + "): " + e.getMessage());
            }
        }
        log.accept("YouTube uygulaması bağlantıyı açmadı; sistem yoluyla açılıyor");
        return false;
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static boolean shouldShowChooserToPickBrowserOrMaps(Uri uri) {
        if (uri == null) {
            return false;
        }
        String h = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
        String p = uri.getPath() != null ? uri.getPath().toLowerCase() : "";
        if (h.contains("yandex") && p.contains("maps")) {
            return true;
        }
        return h.equals("maps.app.goo.gl");
    }
}
