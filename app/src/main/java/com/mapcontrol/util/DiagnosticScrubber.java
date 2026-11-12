package com.mapcontrol.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tanılama kaydı satırlarından kişisel veriyi maskeler (geliştiriciye gönderilmeden önce, araçta). Saf: JVM'de sınanır.
 *
 * Maskelenir: telefon numarası (+ ile başlayan, 00 ile başlayan ya da Türk cep 05xx / 5xx / 905xx), MAC adresi (son
 * bayt kalır: aynı cihaz satırlar arasında izlenebilsin), e-posta, koordinat çifti ve lat/lon anahtarları, token ve
 * parola değerleri. {@link #scrubDeviceNames} yalnızca BT dökümünde: cihaz/rehber adları.
 *
 * Bilerek maskelenmez: logcat'in PID/TID'i, epoch ms zaman damgaları (17…/18… ile başlar, cep numarası değildir).
 * Sınır: serbest metindeki kişi adları genel olarak bulunamaz.
 */
public final class DiagnosticScrubber {

    private static final Pattern MAC = Pattern.compile(
            "(?i)\\b(?:[0-9a-f]{2}[:-]){5}([0-9a-f]{2})\\b");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b");
    /** + ya da 00 ile uluslararası; ayrıca Türk cep (5xx xxx xx xx), başında 0 / 90 / +90 olabilir, boşluklu da. */
    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\w.])(?:\\+|00)\\d[\\d ]{8,16}\\d(?![\\w.])"
                    + "|(?<![\\w.])(?:90|0)?5\\d{2} ?\\d{3} ?\\d{2} ?\\d{2}(?![\\w.])");
    private static final Pattern COORD_PAIR = Pattern.compile(
            "-?\\d{1,3}\\.\\d{4,}\\s*,\\s*-?\\d{1,3}\\.\\d{4,}");
    private static final Pattern COORD_KEY = Pattern.compile(
            "(?i)\\b(lat|lon|lng|latitude|longitude)(\"?\\s*[:=]\\s*\"?)-?\\d+(?:\\.\\d+)?");
    private static final Pattern SECRET_KEY = Pattern.compile(
            "(?i)\\b(token|password|passwd|authorization|secret|cookie)(\"?\\s*[:=]\\s*\"?)(?!Bearer\\b)(?!\\[gizli\\])[^\\s\",;}&]+");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._\\-]+");
    /** BT dökümü: "Name: X", "name=X", "alias=X", "deviceName=X" (değer satır sonuna ya da ayraca kadar). */
    private static final Pattern DEVICE_NAME = Pattern.compile(
            "(?i)\\b(name|alias|devicename|remotename|friendlyname)(\\s*[:=]\\s*)[^,;}\\]\\r\\n]{1,80}");

    private DiagnosticScrubber() {
    }

    public static String scrub(String line) {
        if (line == null || line.isEmpty()) {
            return line;
        }
        String s = BEARER.matcher(line).replaceAll("Bearer [gizli]");
        s = SECRET_KEY.matcher(s).replaceAll("$1$2[gizli]");
        s = EMAIL.matcher(s).replaceAll("[e-posta]");
        s = maskMac(s);
        s = COORD_PAIR.matcher(s).replaceAll("[konum]");
        s = COORD_KEY.matcher(s).replaceAll("$1$2[konum]");
        s = maskPhones(s);
        return s;
    }

    /** {@link #scrub} + cihaz/rehber adları (BT dökümü için). */
    public static String scrubDeviceNames(String line) {
        String s = scrub(line);
        return s == null ? null : DEVICE_NAME.matcher(s).replaceAll("$1$2[ad]");
    }

    private static String maskMac(String s) {
        Matcher m = MAC.matcher(s);
        if (!m.find()) {
            return s;
        }
        StringBuffer sb = new StringBuffer();
        do {
            m.appendReplacement(sb, "XX:XX:XX:XX:XX:" + m.group(1).toUpperCase(java.util.Locale.ROOT));
        } while (m.find());
        m.appendTail(sb);
        return sb.toString();
    }

    /** Son iki rakam kalır (aynı numara satırlar arasında ayırt edilebilsin). */
    private static String maskPhones(String s) {
        Matcher m = PHONE.matcher(s);
        if (!m.find()) {
            return s;
        }
        StringBuffer sb = new StringBuffer();
        do {
            String digits = m.group().replaceAll("\\D", "");
            String tail = digits.length() >= 2 ? digits.substring(digits.length() - 2) : "";
            m.appendReplacement(sb, "[tel..." + tail + "]");
        } while (m.find());
        m.appendTail(sb);
        return sb.toString();
    }
}
