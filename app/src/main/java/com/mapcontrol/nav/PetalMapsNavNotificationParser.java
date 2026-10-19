package com.mapcontrol.nav;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;

import com.mapcontrol.util.AppLaunchHelper;

/**
 * Petal Maps navigasyon bildirimi. Google Maps ayrıştırmasından ayrıdır:
 * alt metin yoksa mesafe başlıktan, manevra metinden okunur.
 */
public final class PetalMapsNavNotificationParser {

    public static final String PACKAGE_PETAL_MAPS = AppLaunchHelper.PETAL_MAPS_PACKAGE;

    private PetalMapsNavNotificationParser() {
    }

    public static boolean isNavigationNotification(@Nullable StatusBarNotification sbn) {
        return parse(sbn).active;
    }

    public static GoogleMapsNavSnapshot parse(@Nullable StatusBarNotification sbn) {
        if (sbn == null || !PACKAGE_PETAL_MAPS.equals(sbn.getPackageName())) {
            return GoogleMapsNavSnapshot.inactive();
        }
        Notification notification = sbn.getNotification();
        if (notification == null) {
            return GoogleMapsNavSnapshot.inactive();
        }
        Bundle extras = notification.extras;
        return parseFields(
                GoogleMapsNavNotificationParser.normalizeExtra(
                        extras == null ? null : extras.getCharSequence(Notification.EXTRA_TITLE)),
                GoogleMapsNavNotificationParser.normalizeExtra(
                        extras == null ? null : extras.getCharSequence(Notification.EXTRA_TEXT)),
                GoogleMapsNavNotificationParser.normalizeExtra(
                        extras == null ? null : extras.getCharSequence(Notification.EXTRA_SUB_TEXT)),
                notification.getLargeIcon() != null);
    }

    /**
     * Alt metin şart değil. Başlık, metin veya alt metinde mesafe ya da süre yoksa navigasyon sayılmaz.
     */
    public static GoogleMapsNavSnapshot parseFields(
            @Nullable String title,
            @Nullable String text,
            @Nullable String subText,
            boolean hadIcon) {
        if (!GoogleMapsNavNotificationParser.looksLikeNavigation(title, text, subText)) {
            return GoogleMapsNavSnapshot.inactive();
        }
        String maneuver = GoogleMapsNavNotificationParser.maneuverFrom(text);
        if (maneuver.isEmpty() && text != null
                && !GoogleMapsNavNotificationParser.looksLikeNavigation(null, text, null)) {
            maneuver = text.trim();
        }
        String summarySource = subText != null ? subText : text;
        return new GoogleMapsNavSnapshot(
                true,
                maneuver,
                GoogleMapsNavNotificationParser.toMetric(
                        GoogleMapsNavNotificationParser.stripClockLabels(title)),
                GoogleMapsNavNotificationParser.toMetric(
                        GoogleMapsNavNotificationParser.stripClockLabels(summarySource)),
                hadIcon);
    }
}
