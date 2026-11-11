package com.mapcontrol;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.android.core.SentryAndroid;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;

/** Çökme raporlarını GlitchTip'e gönderir. Konum, token ve telefon bilgisini ayıklar. */
public final class GlitchTip {

    private static final Pattern QUERY_SECRET = Pattern.compile(
            "(?i)\\b(latitude|longitude|lat|lon|lng|token|phone|authorization|password|email|imei|coordinate|coordinates)=([^&\\s]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._\\-]+");

    private GlitchTip() {
    }

    /** Kurulum kimliği ayrı dosyada: AppBackup bu dosyayı taşımaz, kimlik başka araca geçmez. */
    private static final String ID_PREFS = "glitchtip_install";
    private static final String KEY_INSTALL_ID = "installId";
    private static final String VDBUS_JAR = "/system/framework/vdbus.jar";

    private static volatile Context app;
    /** Süreç boyunca değişmeyen etiketler (açılışta bir kez hesaplanır). */
    private static final Map<String, String> STATIC_TAGS = new HashMap<>();

    public static void start(Context context) {
        app = context.getApplicationContext();
        STATIC_TAGS.putAll(staticTags(app));
        final String installId = installId(app);
        SentryAndroid.init(context, options -> {
            options.setDsn(BuildConfig.GLITCHTIP_DSN);
            options.setEnableNdk(false);
            options.setSendDefaultPii(false);
            options.setAttachScreenshot(false);
            options.setAttachViewHierarchy(false);
            options.setTracesSampleRate(0.0);
            options.setDebug(BuildConfig.DEBUG);
            options.setEnvironment(BuildConfig.DEBUG ? "debug" : "release");
            // Aynı sürüm adıyla çıkan beta/release derlemeleri ayrılsın: 1.5.0+18, dist = commit.
            options.setRelease(BuildConfig.VERSION_NAME + "+" + BuildConfig.VERSION_CODE);
            options.setDist(BuildConfig.GIT_SHA);
            options.setBeforeBreadcrumb((breadcrumb, hint) -> scrubBreadcrumb(breadcrumb));
            options.setBeforeSend((event, hint) -> scrubEvent(enrich(event)));
        });
        // Anonim kurulum kimliği: rastgele UUID, araç/kişi bilgisinden türetilmez. Etkilenen araç sayısı için.
        User user = new User();
        user.setId(installId);
        Sentry.setUser(user);
        Sentry.setTag("install_id", installId);
        for (Map.Entry<String, String> tag : STATIC_TAGS.entrySet()) {
            Sentry.setTag(tag.getKey(), tag.getValue());
        }
    }

    static String installId(Context context) {
        SharedPreferences sp = context.getSharedPreferences(ID_PREFS, Context.MODE_PRIVATE);
        String id = sp.getString(KEY_INSTALL_ID, null);
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            sp.edit().putString(KEY_INSTALL_ID, id).apply();
        }
        return id;
    }

    /** Araç yazılımı, ekran, profil ve vdbus.jar: aynı hatanın hangi araç ailesinde olduğunu tek bakışta gösterir. */
    private static Map<String, String> staticTags(Context context) {
        Map<String, String> tags = new HashMap<>();
        tags.put("firmware", clip(Build.DISPLAY));
        tags.put("product", clip(Build.PRODUCT));
        tags.put("vdbus_system", String.valueOf(new File(VDBUS_JAR).exists()));
        try {
            DisplayManager dm = context.getSystemService(DisplayManager.class);
            Display d = dm != null ? dm.getDisplay(Display.DEFAULT_DISPLAY) : null;
            if (d != null) {
                DisplayMetrics m = new DisplayMetrics();
                d.getRealMetrics(m);
                tags.put("screen", m.widthPixels + "x" + m.heightPixels);
                // Doğal yönden tespit: dikey ekran (Jaecoo) "portrait" görünür, Tiggo 8 sanılmaz.
                tags.put("vehicle_profile", com.mapcontrol.launcher.VehicleProfile.Companion
                        .detectFromDisplay(context).getKey());
            }
        } catch (Throwable ignored) {
        }
        return tags;
    }

    /** Gönderim anındaki durum etiketleri + bağlantı hatalarında gruplama. */
    private static SentryEvent enrich(SentryEvent event) {
        if (event == null) {
            return null;
        }
        Context c = app;
        if (c != null) {
            try {
                event.setTag("launcher_mode", String.valueOf(
                        com.mapcontrol.util.LauncherModeManager.isEnabled(c)));
            } catch (Throwable ignored) {
            }
            try {
                DevicePolicyManager dpm = c.getSystemService(DevicePolicyManager.class);
                event.setTag("device_owner", String.valueOf(
                        dpm != null && dpm.isDeviceOwnerApp(c.getPackageName())));
            } catch (Throwable ignored) {
            }
            try {
                String a11y = Settings.Secure.getString(c.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                event.setTag("accessibility", String.valueOf(
                        a11y != null && a11y.contains(c.getPackageName() + "/")));
            } catch (Throwable ignored) {
            }
        }
        List<String> fp = fingerprint(event.getThrowable());
        if (fp != null) {
            event.setFingerprints(fp);
        }
        return event;
    }

    /**
     * Bağlantı hataları (NoSuchMethodError vb.) araç yazılımına göre farklı mesajla gelir ve GlitchTip'te ayrı başlıklara
     * bölünür. Hata tipi + ilk MapControl satırı aynıysa tek başlıkta toplanır. Diğer hatalarda varsayılan gruplama kalır.
     */
    static List<String> fingerprint(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (!(t instanceof LinkageError)) {
                continue;
            }
            String site = "?";
            for (StackTraceElement e : t.getStackTrace()) {
                if (e.getClassName().startsWith("com.mapcontrol.")) {
                    site = e.getClassName() + "." + e.getMethodName();
                    break;
                }
            }
            return Arrays.asList("linkage", t.getClass().getName(), site);
        }
        return null;
    }

    private static String clip(String value) {
        if (value == null) {
            return "?";
        }
        return value.length() > 120 ? value.substring(0, 120) : value;
    }

    /** Çökme olmayan teşhis kaydı; aynı [message] GlitchTip'te tek başlıkta toplanır, ayrıntı [extras] içindedir. */
    public static void reportWarning(String message, Map<String, String> extras) {
        Sentry.captureMessage(message, scope -> {
            scope.setLevel(SentryLevel.WARNING);
            for (Map.Entry<String, String> extra : extras.entrySet()) {
                scope.setExtra(extra.getKey(), extra.getValue());
            }
        });
    }

    private static Breadcrumb scrubBreadcrumb(Breadcrumb breadcrumb) {
        if (breadcrumb == null) {
            return null;
        }
        breadcrumb.setMessage(scrubText(breadcrumb.getMessage()));
        scrubObjectMap(breadcrumb.getData());
        return breadcrumb;
    }

    private static SentryEvent scrubEvent(SentryEvent event) {
        if (event == null) {
            return null;
        }
        User user = event.getUser();
        if (user != null) {
            user.setEmail(null);
            user.setIpAddress(null);
            user.setUsername(null);
            scrubStringMap(user.getData());
        }

        Request request = event.getRequest();
        if (request != null) {
            request.setCookies(null);
            request.setData(null);
            request.setQueryString(null);
            scrubStringMap(request.getHeaders());
        }

        Message message = event.getMessage();
        if (message != null) {
            message.setFormatted(scrubText(message.getFormatted()));
            message.setMessage(scrubText(message.getMessage()));
        }

        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            for (int i = 0; i < exceptions.size(); i++) {
                SentryException exception = exceptions.get(i);
                if (exception != null) {
                    exception.setValue(scrubText(exception.getValue()));
                }
            }
        }

        scrubObjectMap(event.getExtras());
        scrubStringMap(event.getTags());
        scrubContexts(event.getContexts());
        List<Breadcrumb> breadcrumbs = event.getBreadcrumbs();
        if (breadcrumbs != null) {
            for (int i = 0; i < breadcrumbs.size(); i++) {
                scrubBreadcrumb(breadcrumbs.get(i));
            }
        }
        return event;
    }

    private static void scrubStringMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        List<String> drop = new ArrayList<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (isSensitive(entry.getKey())) {
                drop.add(entry.getKey());
            } else {
                entry.setValue(scrubText(entry.getValue()));
            }
        }
        for (int i = 0; i < drop.size(); i++) {
            map.remove(drop.get(i));
        }
    }

    private static void scrubContexts(Contexts contexts) {
        if (contexts == null || contexts.size() == 0) {
            return;
        }
        List<String> drop = new ArrayList<>();
        List<String> textKeys = new ArrayList<>();
        List<String> textValues = new ArrayList<>();
        for (Map.Entry<String, Object> entry : contexts.entrySet()) {
            if (isSensitive(entry.getKey())) {
                drop.add(entry.getKey());
            } else if (entry.getValue() instanceof String) {
                textKeys.add(entry.getKey());
                textValues.add(scrubText((String) entry.getValue()));
            }
        }
        for (int i = 0; i < drop.size(); i++) {
            contexts.remove(drop.get(i));
        }
        for (int i = 0; i < textKeys.size(); i++) {
            contexts.put(textKeys.get(i), textValues.get(i));
        }
    }

    private static void scrubObjectMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        List<String> drop = new ArrayList<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (isSensitive(entry.getKey())) {
                drop.add(entry.getKey());
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof String) {
                entry.setValue(scrubText((String) value));
            }
        }
        for (int i = 0; i < drop.size(); i++) {
            map.remove(drop.get(i));
        }
    }

    private static boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.US);
        if (normalized.equals("lat") || normalized.equals("lon") || normalized.equals("lng")) {
            return true;
        }
        return normalized.contains("latitude")
                || normalized.contains("longitude")
                || normalized.contains("coordinate")
                || normalized.contains("phone")
                || normalized.contains("imei")
                || normalized.contains("token")
                || normalized.contains("authorization")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("email")
                || normalized.contains("msisdn")
                || normalized.contains("cookie");
    }

    private static String scrubText(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String withoutQuery = QUERY_SECRET.matcher(value).replaceAll("$1=[redacted]");
        return BEARER.matcher(withoutQuery).replaceAll("Bearer [redacted]");
    }
}
