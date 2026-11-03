package com.mapcontrol.trip;

import java.util.Locale;

/**
 * Telefon uygulamasının QR ile okuduğu eşleşme adresi:
 * {@code mapcontrol://pair?host=192.168.43.1&port=7462&key=<32 hex>&vin=<şase>}.
 * Şase araçtan okunamadıysa {@code vin} alanı yoktur. Android'e bağlı değildir.
 */
public final class PairingUri {

    private PairingUri() {
    }

    public static String build(String host, int port, String key, String vin) {
        StringBuilder sb = new StringBuilder("mapcontrol://pair?host=").append(host)
                .append("&port=").append(port)
                .append("&key=").append(key.toLowerCase(Locale.ROOT));
        if (vin != null && !vin.isEmpty()) {
            sb.append("&vin=").append(vin);
        }
        return sb.toString();
    }

    /** {@code http://192.168.43.1:7462} gibi sunucu adresinden ana makine ve port. */
    public static String[] hostAndPort(String serverUrl) {
        try {
            java.net.URI uri = new java.net.URI(serverUrl);
            if (uri.getHost() == null) {
                return null;
            }
            int port = uri.getPort() > 0 ? uri.getPort() : 80;
            return new String[]{uri.getHost(), Integer.toString(port)};
        } catch (java.net.URISyntaxException e) {
            return null;
        }
    }
}
