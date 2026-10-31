package com.mapcontrol.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google Haritalar bağlantılarını tanır, kısa bağlantıyı çözer ve koordinat/yer adını çıkarır; böylece
 * araç, tarayıcı seçicisine düşmeden doğrudan Google Haritalar'ı açabilir. Android'e bağlı değildir.
 */
public final class MapsLink {

    private static final String NUM = "(-?\\d{1,3}(?:\\.\\d+)?)";
    private static final Pattern PAIR = Pattern.compile("^\\s*" + NUM + "\\s*,\\s*" + NUM + "\\s*$");
    private static final Pattern PLACE_ID = Pattern.compile("!3d" + NUM + "!4d" + NUM);
    private static final Pattern VIEW = Pattern.compile("@" + NUM + "," + NUM);
    private static final Pattern PLACE_PATH = Pattern.compile("/maps/place/([^/?#]+)");
    private static final String[] COORD_KEYS = {"q", "query", "destination", "daddr", "ll", "center"};
    private static final int MAX_HOPS = 6;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    private MapsLink() {
    }

    /** Google Haritalar'ın açması gereken bağlantı mı (kısa bağlantı, maps.google.&lt;alan&gt;, google.&lt;alan&gt; altında /maps). */
    public static boolean isGoogleMaps(String url) {
        String host = host(url);
        String path = path(url);
        if (host == null) {
            return false;
        }
        if (host.equals("maps.app.goo.gl")) {
            return true;
        }
        if (host.equals("goo.gl")) {
            return path.startsWith("/maps");
        }
        if (host.startsWith("maps.google.")) {
            return true;
        }
        return (host.startsWith("www.google.") || host.startsWith("google.")) && path.startsWith("/maps");
    }

    /** Yönlendirmesi çözülmeden koordinat vermeyen kısa bağlantı. */
    public static boolean isShortLink(String url) {
        String host = host(url);
        return host != null && (host.equals("maps.app.goo.gl") || host.equals("goo.gl"));
    }

    /** {enlem, boylam} ya da bulunamazsa null. Kısa bağlantıda koordinat yoktur. */
    public static double[] coordinates(String url) {
        if (url == null) {
            return null;
        }
        String decoded = decode(url);
        String query = query(url);
        if (query != null) {
            for (String key : COORD_KEYS) {
                String value = param(query, key);
                if (value != null) {
                    Matcher m = PAIR.matcher(value);
                    if (m.matches()) {
                        double[] c = valid(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)));
                        if (c != null) {
                            return c;
                        }
                    }
                }
            }
        }
        Matcher place = PLACE_ID.matcher(decoded);
        if (place.find()) {
            double[] c = valid(Double.parseDouble(place.group(1)), Double.parseDouble(place.group(2)));
            if (c != null) {
                return c;
            }
        }
        Matcher view = VIEW.matcher(decoded);
        if (view.find()) {
            return valid(Double.parseDouble(view.group(1)), Double.parseDouble(view.group(2)));
        }
        return null;
    }

    /** Yer adı: {@code /maps/place/<ad>/} ya da koordinat olmayan {@code q=}; yoksa null. */
    public static String placeName(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = PLACE_PATH.matcher(path(url));
        if (m.find()) {
            String name = decode(m.group(1)).replace('+', ' ').trim();
            if (!name.isEmpty() && !PAIR.matcher(name).matches()) {
                return name;
            }
        }
        String query = query(url);
        for (String key : new String[]{"q", "query"}) {
            String value = query == null ? null : param(query, key);
            if (value != null && !value.trim().isEmpty() && !PAIR.matcher(value).matches()) {
                return value.trim();
            }
        }
        return null;
    }

    /** consent.google.com bir yönlendirmeyi {@code continue=} içinde taşır; asıl adres oradadır. */
    public static String consentContinue(String url) {
        String host = host(url);
        if (host == null || !host.equals("consent.google.com")) {
            return null;
        }
        String query = query(url);
        String value = query == null ? null : param(query, "continue");
        return value == null || value.isEmpty() ? null : value;
    }

    public static String navigationUri(double lat, double lon) {
        return String.format(Locale.US, "google.navigation:q=%.6f,%.6f", lat, lon);
    }

    public static String geoUri(double lat, double lon) {
        return String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f", lat, lon, lat, lon);
    }

    /** Yer adıyla arama: Google Haritalar sonuçlardan ilkini gösterir. */
    public static String searchUri(String name) {
        try {
            return "geo:0,0?q=" + URLEncoder.encode(name, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Kısa bağlantının yönlendirmelerini izleyip son adresi verir (en çok {@value #MAX_HOPS} adım).
     * Ağ gerektirir; ana iş parçacığında çağrılmaz. Hata olursa son bilinen adres döner.
     */
    public static String resolve(String shortUrl) {
        String current = shortUrl;
        for (int hop = 0; hop < MAX_HOPS; hop++) {
            String next;
            try {
                next = redirectOf(current);
            } catch (IOException e) {
                return current;
            }
            if (next == null) {
                return current;
            }
            String consent = consentContinue(next);
            current = consent != null ? consent : next;
        }
        return current;
    }

    private static String redirectOf(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setRequestProperty("User-Agent", USER_AGENT);
            c.setRequestProperty("Accept-Language", "tr,en;q=0.8");
            int code = c.getResponseCode();
            if (code < 300 || code >= 400) {
                return null;
            }
            String location = c.getHeaderField("Location");
            if (location == null || location.isEmpty()) {
                return null;
            }
            return URI.create(url).resolve(location).toString();
        } finally {
            c.disconnect();
        }
    }

    // ---- yardımcılar ------------------------------------------------------

    private static double[] valid(double lat, double lon) {
        if (Math.abs(lat) > 90 || Math.abs(lon) > 180 || (lat == 0 && lon == 0)) {
            return null;
        }
        return new double[]{lat, lon};
    }

    private static String host(String url) {
        try {
            String h = URI.create(url.trim()).getHost();
            return h == null ? null : h.toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String path(String url) {
        try {
            String p = URI.create(url.trim()).getRawPath();
            return p == null ? "" : p;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String query(String url) {
        try {
            return URI.create(url.trim()).getRawQuery();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String param(String rawQuery, String key) {
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return decode(pair.substring(eq + 1));
            }
        }
        return null;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }
}
