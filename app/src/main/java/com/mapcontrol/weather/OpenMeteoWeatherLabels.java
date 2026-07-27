package com.mapcontrol.weather;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.mapcontrol.R;

/**
 * WMO hava kodu → kullanıcı metni ve sembol.
 */
public final class OpenMeteoWeatherLabels {

    private OpenMeteoWeatherLabels() {
    }

    @NonNull
    public static String labelFor(@NonNull Context context, int weatherCode, boolean day) {
        return context.getString(labelResFor(weatherCode, day));
    }

    @NonNull
    public static String glyphFor(int weatherCode, boolean day) {
        if (weatherCode == 0) {
            return day ? "\u2600" : "\u263E";
        }
        if (weatherCode == 1 || weatherCode == 2) {
            return day ? "\u26C5" : "\u2601";
        }
        if (weatherCode == 3) {
            return "\u2601";
        }
        if (weatherCode == 45 || weatherCode == 48) {
            return "\u2591";
        }
        if (weatherCode >= 51 && weatherCode <= 67) {
            return "\u2614";
        }
        if (weatherCode >= 71 && weatherCode <= 77) {
            return "\u2744";
        }
        if (weatherCode >= 80 && weatherCode <= 82) {
            return "\u2614";
        }
        if (weatherCode >= 95) {
            return "\u26A1";
        }
        return day ? "\u2600" : "\u263E";
    }

    @StringRes
    private static int labelResFor(int weatherCode, boolean day) {
        if (weatherCode == 0) {
            return day ? R.string.weather_clear : R.string.weather_clear_night;
        }
        if (weatherCode == 1) {
            return R.string.weather_mainly_clear;
        }
        if (weatherCode == 2) {
            return R.string.weather_partly_cloudy;
        }
        if (weatherCode == 3) {
            return R.string.weather_overcast;
        }
        if (weatherCode == 45 || weatherCode == 48) {
            return R.string.weather_fog;
        }
        if (weatherCode >= 51 && weatherCode <= 55) {
            return R.string.weather_drizzle;
        }
        if (weatherCode >= 56 && weatherCode <= 67) {
            return R.string.weather_rain;
        }
        if (weatherCode >= 71 && weatherCode <= 77) {
            return R.string.weather_snow;
        }
        if (weatherCode >= 80 && weatherCode <= 82) {
            return R.string.weather_showers;
        }
        if (weatherCode >= 95) {
            return R.string.weather_thunder;
        }
        return R.string.weather_unknown;
    }
}
