package com.mapcontrol.ui.welcome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Base64;

import com.mapcontrol.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * HTML şablonundaki {@code {{ANAHTAR}}} alanlarını doldurur.
 *
 * <p>Alanlar: {@code TIME}, {@code DAY}, {@code DATE}, {@code GREETING}, {@code NAME},
 * {@code LANG}, {@code BODY_CLASS} (p-morning|p-day|p-evening|p-night ve varsa has-name),
 * {@code FONT_FACE} (Outfit için hazır @font-face kuralı).
 * Bilinmeyen alanlar olduğu gibi bırakılır.
 */
final class WelcomeHtmlRenderer {

    private static String fontFaceCache;

    private WelcomeHtmlRenderer() {
    }

    static String render(Context context, String template) {
        Context app = context.getApplicationContext();
        Locale locale = app.getResources().getConfiguration().getLocales().get(0);
        Date now = new Date();
        int period = periodOf(now);
        String name = customLine(app);

        Map<String, String> values = new HashMap<>();
        values.put("TIME", new java.text.SimpleDateFormat("HH:mm", locale).format(now));
        values.put("DAY", new java.text.SimpleDateFormat("EEEE", locale).format(now));
        values.put("DATE", new java.text.SimpleDateFormat(
                DateFormat.getBestDateTimePattern(locale, "dMMMM"), locale).format(now));
        values.put("GREETING", TextUtils.htmlEncode(app.getString(greetingRes(period))));
        values.put("NAME", TextUtils.htmlEncode(name));
        values.put("LANG", TextUtils.htmlEncode(locale.toLanguageTag()));
        values.put("BODY_CLASS", periodClass(period) + (name.isEmpty() ? "" : " has-name"));

        return fill(template, values, app);
    }

    private static String fill(String template, Map<String, String> values, Context app) {
        StringBuilder out = new StringBuilder(template.length() + 160 * 1024);
        int pos = 0;
        while (pos < template.length()) {
            int open = template.indexOf("{{", pos);
            if (open < 0) {
                break;
            }
            int close = template.indexOf("}}", open + 2);
            if (close < 0) {
                break;
            }
            out.append(template, pos, open);
            String key = template.substring(open + 2, close).trim();
            String value = "FONT_FACE".equals(key) ? fontFace(app) : values.get(key);
            if (value == null) {
                out.append(template, open, close + 2);
            } else {
                out.append(value);
            }
            pos = close + 2;
        }
        out.append(template, pos, template.length());
        return out.toString();
    }

    private static final int MORNING = 0;
    private static final int DAY = 1;
    private static final int EVENING = 2;
    private static final int NIGHT = 3;

    private static int periodOf(Date now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) {
            return MORNING;
        }
        if (hour >= 12 && hour < 18) {
            return DAY;
        }
        if (hour >= 18 && hour < 22) {
            return EVENING;
        }
        return NIGHT;
    }

    private static int greetingRes(int period) {
        switch (period) {
            case MORNING:
                return R.string.welcome_greeting_morning;
            case DAY:
                return R.string.welcome_greeting_day;
            case EVENING:
                return R.string.welcome_greeting_evening;
            default:
                return R.string.welcome_greeting_night;
        }
    }

    private static String periodClass(int period) {
        switch (period) {
            case MORNING:
                return "p-morning";
            case DAY:
                return "p-day";
            case EVENING:
                return "p-evening";
            default:
                return "p-night";
        }
    }

    private static String customLine(Context app) {
        SharedPreferences prefs = app.getSharedPreferences(
                WelcomeDesignCatalog.PREFS_NAME, Context.MODE_PRIVATE);
        String line = prefs.getString(WelcomeDesignCatalog.PREF_LINE, "");
        return line == null ? "" : line.trim();
    }

    /** Outfit fontunu data URI olarak gömer; WebView'ın dosya erişimine bağımlı olmaz. */
    private static synchronized String fontFace(Context app) {
        if (fontFaceCache != null) {
            return fontFaceCache;
        }
        try (InputStream in = app.getResources().openRawResource(R.font.outfit);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            String base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            fontFaceCache = "@font-face{font-family:'Outfit';font-weight:100 900;font-style:normal;"
                    + "src:url(data:font/ttf;base64," + base64 + ") format('truetype');}";
        } catch (IOException | RuntimeException e) {
            fontFaceCache = "";
        }
        return fontFaceCache;
    }
}
