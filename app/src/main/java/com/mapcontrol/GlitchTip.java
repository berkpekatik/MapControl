package com.mapcontrol;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import io.sentry.Breadcrumb;
import io.sentry.SentryEvent;
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

    public static void start(Context context) {
        SentryAndroid.init(context, options -> {
            options.setDsn(BuildConfig.GLITCHTIP_DSN);
            options.setEnableNdk(false);
            options.setSendDefaultPii(false);
            options.setAttachScreenshot(false);
            options.setAttachViewHierarchy(false);
            options.setTracesSampleRate(0.0);
            options.setDebug(BuildConfig.DEBUG);
            options.setEnvironment(BuildConfig.DEBUG ? "debug" : "release");
            options.setRelease(BuildConfig.VERSION_NAME);
            options.setBeforeBreadcrumb((breadcrumb, hint) -> scrubBreadcrumb(breadcrumb));
            options.setBeforeSend((event, hint) -> scrubEvent(event));
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
