package com.mapcontrol.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * Persists in-app locale (tr / en) and applies it via AppCompat per-app locales API.
 */
public final class AppLocaleManager {

    public static final String PREFS_NAME = "mapcontrol_locale";
    public static final String KEY_LOCALE_TAG = "locale_tag";
    public static final String LOCALE_TR = "tr";
    public static final String LOCALE_EN = "en";
    public static final String DEFAULT_LOCALE_TAG = LOCALE_TR;

    private AppLocaleManager() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getStoredLocaleTag(Context context) {
        String tag = prefs(context).getString(KEY_LOCALE_TAG, DEFAULT_LOCALE_TAG);
        if (LOCALE_EN.equals(tag)) {
            return LOCALE_EN;
        }
        return LOCALE_TR;
    }

    public static void applyStoredLocale(Context context) {
        applyLocaleTag(getStoredLocaleTag(context));
    }

    public static void applyLocaleTag(String tag) {
        String normalized = LOCALE_EN.equals(tag) ? LOCALE_EN : LOCALE_TR;
        AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(normalized));
    }

    public static void setLocale(Context context, String tag) {
        String normalized = LOCALE_EN.equals(tag) ? LOCALE_EN : LOCALE_TR;
        prefs(context).edit().putString(KEY_LOCALE_TAG, normalized).apply();
        applyLocaleTag(normalized);
    }

    public static boolean isEnglish(Context context) {
        return LOCALE_EN.equals(getStoredLocaleTag(context));
    }
}
