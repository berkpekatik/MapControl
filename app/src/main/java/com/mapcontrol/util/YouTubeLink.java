package com.mapcontrol.util;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YouTube bağlantılarını tanır; araç bunları tarayıcı ya da seçici yerine doğrudan YouTube uygulamasına verir.
 * Android'e bağlı değildir.
 *
 * <p>Uygulama paketleri sırayla denenir: resmî YouTube, sonra ReVanced. Hangisinin kurulu olduğu araca göre
 * değişir ve ReVanced bağlantı açma izni verilmediyse bağlantıyı kabul etmeyebilir; ikisi de açmazsa çağıran
 * eski sistem yoluna (tarayıcı) döner.
 *
 * <p>YouTube Music ({@code music.youtube.com}) ve YouTube TV ayrı uygulamalardır; burada tanınmaz.
 */
public final class YouTubeLink {

    /** Denenme sırasıyla YouTube uygulama paketleri. */
    public static final String[] PACKAGES = {
            "com.google.android.youtube",
            "app.revanced.android.youtube",
    };

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern PATH_ID = Pattern.compile("^/(?:shorts|live|embed|v)/([A-Za-z0-9_-]{11})(?:[/?#].*)?$");

    private YouTubeLink() {
    }

    /** {@code youtube.com}, {@code www.}, {@code m.} ve {@code youtu.be} bağlantısı mı (yalnızca http/https). */
    public static boolean isYouTube(String url) {
        URI uri = parse(url);
        if (uri == null) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("youtube.com") || host.equals("www.youtube.com") || host.equals("m.youtube.com")
                || host.equals("youtu.be") || host.equals("www.youtu.be");
    }

    /**
     * Bağlantıdaki video kimliği (11 karakter); kanal, oynatma listesi ya da ana sayfa gibi tek bir videoyu
     * göstermeyen bağlantıda null.
     */
    public static String videoId(String url) {
        if (!isYouTube(url)) {
            return null;
        }
        URI uri = parse(url);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (host.endsWith("youtu.be")) {
            Matcher m = Pattern.compile("^/([A-Za-z0-9_-]{11})(?:[/?#].*)?$").matcher(path);
            return m.matches() ? m.group(1) : null;
        }
        if (path.equals("/watch")) {
            String v = queryParam(uri.getRawQuery(), "v");
            return v != null && ID.matcher(v).matches() ? v : null;
        }
        Matcher m = PATH_ID.matcher(path);
        return m.matches() ? m.group(1) : null;
    }

    private static URI parse(String url) {
        if (url == null) {
            return null;
        }
        try {
            return new URI(url.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String queryParam(String query, String name) {
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
