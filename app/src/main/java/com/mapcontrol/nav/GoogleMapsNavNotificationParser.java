package com.mapcontrol.nav;

import android.app.Notification;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;

import com.mapcontrol.util.AppLaunchHelper;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google Maps ongoing navigasyon bildirimi ({@code android.subText} dolu).
 */
public final class GoogleMapsNavNotificationParser {

    public static final String PACKAGE_GOOGLE_MAPS = AppLaunchHelper.GOOGLE_MAPS_PACKAGE;

    private static final Pattern FEET = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*ft");
    private static final Pattern MILES = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*mi\\b");
    private static final String[] IGNORE_FRAGMENTS = {
            "google services",
            "sign in",
            "google play",
            "güncelleme",
            "updat",
            "çevrimdışı",
            "update",
            "giriş yap",
            "oturum aç"
    };
    private static final String[] ICON_KEYS = {
            "android.ongoingActivityNoti.secondIcon",
            "android.ongoingActivityNoti.chipIcon",
            "android.largeIcon",
            "android.icon"
    };

    private GoogleMapsNavNotificationParser() {
    }

    public static boolean isNavigationNotification(@Nullable StatusBarNotification sbn) {
        if (sbn == null || !PACKAGE_GOOGLE_MAPS.equals(sbn.getPackageName())) {
            return false;
        }
        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) {
            return false;
        }
        return notification.extras.get(Notification.EXTRA_SUB_TEXT) != null;
    }

    public static GoogleMapsNavSnapshot parse(@Nullable StatusBarNotification sbn) {
        if (!isNavigationNotification(sbn)) {
            return GoogleMapsNavSnapshot.inactive();
        }
        Bundle extras = sbn.getNotification().extras;
        return parseFields(
                extras.getString(Notification.EXTRA_TITLE),
                extras.getString(Notification.EXTRA_TEXT),
                extras.getString(Notification.EXTRA_SUB_TEXT),
                hasIcon(extras));
    }

    /**
     * Bildirim extras'ından okunan ham alanlar. {@code subText == null} navigasyon değildir.
     */
    public static GoogleMapsNavSnapshot parseFields(
            @Nullable String title,
            @Nullable String text,
            @Nullable String subText,
            boolean hadIcon) {
        if (subText == null) {
            return GoogleMapsNavSnapshot.inactive();
        }
        String maneuver = extractManeuver(text);
        String distance = convertImperial(title);
        String summary = convertRouteSummary(subText);
        if (shouldIgnore(distance) || shouldIgnore(maneuver)) {
            return GoogleMapsNavSnapshot.inactive();
        }
        return new GoogleMapsNavSnapshot(true, maneuver, distance, summary, hadIcon);
    }

    static boolean shouldIgnore(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        String lower = value.toLowerCase();
        for (String fragment : IGNORE_FRAGMENTS) {
            if (lower.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    static String extractManeuver(@Nullable String text) {
        if (text == null || !text.contains(" - ")) {
            return "";
        }
        return text.split(" - ", 2)[0].replace(" TVS:", "").replace("TVS:", "").trim();
    }

    static String convertImperial(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String result = replaceFeet(value);
        return replaceMiles(result);
    }

    static String convertRouteSummary(@Nullable String subText) {
        if (subText == null) {
            return null;
        }
        String[] parts = subText.split(" · ");
        if (parts.length < 2) {
            return stripEtaLabels(convertImperial(subText));
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains(" mi") || part.contains(" ft")) {
                part = convertImperial(part);
            }
            sb.append(part);
            if (i < parts.length - 1) {
                sb.append(" · ");
            }
        }
        return stripEtaLabels(sb.toString());
    }

    private static String stripEtaLabels(String value) {
        if (value == null) {
            return null;
        }
        return value.replace(" ETA", "").replace(" TVS:", "").replace("TVS:", "").trim();
    }

    private static String replaceFeet(String value) {
        Matcher matcher = FEET.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group();
            try {
                double feet = Double.parseDouble(matcher.group(1).replace(',', '.'));
                replacement = ((int) (feet * 0.3048d)) + " m";
            } catch (NumberFormatException ignored) {
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String replaceMiles(String value) {
        Matcher matcher = MILES.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group();
            try {
                double km = Double.parseDouble(matcher.group(1).replace(',', '.')) * 1.609344d;
                if (km < 1.0d) {
                    replacement = ((int) (km * 1000.0d)) + " m";
                } else {
                    replacement = new DecimalFormat("#0.0", DecimalFormatSymbols.getInstance(Locale.US))
                        .format(km).replace('.', ',') + " km";
                }
            } catch (NumberFormatException ignored) {
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static boolean hasIcon(@Nullable Bundle extras) {
        if (extras == null) {
            return false;
        }
        for (String key : ICON_KEYS) {
            if (extras.get(key) instanceof Icon) {
                return true;
            }
        }
        return false;
    }
}
