package com.mapcontrol.nav;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;

import com.mapcontrol.util.AppLaunchHelper;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google Maps navigasyon bildirimini başlık, metin ve alt metinden okur.
 * Alt metin yoksa ya da alanlarda mesafe veya süre yoksa navigasyon sayılmaz.
 */
public final class GoogleMapsNavNotificationParser {

    public static final String PACKAGE_GOOGLE_MAPS = AppLaunchHelper.GOOGLE_MAPS_PACKAGE;

    private static final Pattern IMPERIAL = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(ft|mi)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAV_QUANTITY = Pattern.compile(
            "\\d+(?:[.,]\\d+)?\\s*(?:ft|mi|km|m|min|dk|hr|sa)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOCK_LABEL = Pattern.compile(
            "(?i)\\s*(?:eta|tvs)\\s*:?");

    private GoogleMapsNavNotificationParser() {
    }

    public static boolean isNavigationNotification(@Nullable StatusBarNotification sbn) {
        if (sbn == null || !PACKAGE_GOOGLE_MAPS.equals(sbn.getPackageName())) {
            return false;
        }
        Notification notification = sbn.getNotification();
        if (notification == null) {
            return false;
        }
        return extraText(notification.extras, Notification.EXTRA_SUB_TEXT) != null;
    }

    public static GoogleMapsNavSnapshot parse(@Nullable StatusBarNotification sbn) {
        if (sbn == null || !PACKAGE_GOOGLE_MAPS.equals(sbn.getPackageName())) {
            return GoogleMapsNavSnapshot.inactive();
        }
        Notification notification = sbn.getNotification();
        if (notification == null) {
            return GoogleMapsNavSnapshot.inactive();
        }
        Bundle extras = notification.extras;
        return parseFields(
                extraText(extras, Notification.EXTRA_TITLE),
                extraText(extras, Notification.EXTRA_TEXT),
                extraText(extras, Notification.EXTRA_SUB_TEXT),
                notification.getLargeIcon() != null);
    }

    /**
     * Bildirim extras çoğu zaman {@link String} değil {@link CharSequence} tutar.
     * {@link Bundle#getString} bu durumda null döner.
     */
    @Nullable
    static String normalizeExtra(@Nullable CharSequence value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.toString().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Nullable
    private static String extraText(@Nullable Bundle extras, String key) {
        if (extras == null) {
            return null;
        }
        return normalizeExtra(extras.getCharSequence(key));
    }

    /**
     * Ham bildirim alanları. {@code subText == null} navigasyon değildir.
     */
    public static GoogleMapsNavSnapshot parseFields(
            @Nullable String title,
            @Nullable String text,
            @Nullable String subText,
            boolean hadIcon) {
        if (subText == null || !looksLikeNavigation(title, text, subText)) {
            return GoogleMapsNavSnapshot.inactive();
        }
        return new GoogleMapsNavSnapshot(
                true,
                maneuverFrom(text),
                toMetric(stripClockLabels(title)),
                toMetric(stripClockLabels(subText)),
                hadIcon);
    }

    static boolean looksLikeNavigation(
            @Nullable String title, @Nullable String text, @Nullable String subText) {
        return hasQuantity(title) || hasQuantity(text) || hasQuantity(subText);
    }

    private static boolean hasQuantity(@Nullable String value) {
        return value != null && NAV_QUANTITY.matcher(value).find();
    }

    static String maneuverFrom(@Nullable String text) {
        if (text == null) {
            return "";
        }
        String cleaned = stripClockLabels(text);
        int split = cleaned.indexOf(" - ");
        if (split < 0) {
            return "";
        }
        return cleaned.substring(0, split).trim();
    }

    static String stripClockLabels(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String stripped = CLOCK_LABEL.matcher(value).replaceAll(" ");
        return stripped.replaceAll("\\s{2,}", " ").trim();
    }

    static String toMetric(@Nullable String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = IMPERIAL.matcher(value);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group();
            try {
                double amount = Double.parseDouble(matcher.group(1).replace(',', '.'));
                double meters = "ft".equalsIgnoreCase(matcher.group(2))
                        ? amount * 0.3048d
                        : amount * 1609.344d;
                replacement = formatMeters(meters);
            } catch (NumberFormatException ignored) {
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String formatMeters(double meters) {
        if (meters < 1000d) {
            return Math.round(meters) + " m";
        }
        return String.format(Locale.GERMANY, "%.1f km", meters / 1000d);
    }
}
