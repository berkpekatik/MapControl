package com.mapcontrol.weather;

import androidx.annotation.Nullable;

/**
 * Open-Meteo {@code current_weather} özeti.
 */
public final class OpenMeteoWeatherSnapshot {

    public final float temperatureC;
    public final float windSpeedKmh;
    public final int windDirectionDeg;
    public final int weatherCode;
    public final boolean day;

    public OpenMeteoWeatherSnapshot(
            float temperatureC,
            float windSpeedKmh,
            int windDirectionDeg,
            int weatherCode,
            boolean day) {
        this.temperatureC = temperatureC;
        this.windSpeedKmh = windSpeedKmh;
        this.windDirectionDeg = windDirectionDeg;
        this.weatherCode = weatherCode;
        this.day = day;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof OpenMeteoWeatherSnapshot)) {
            return false;
        }
        OpenMeteoWeatherSnapshot other = (OpenMeteoWeatherSnapshot) obj;
        return Float.compare(temperatureC, other.temperatureC) == 0
                && Float.compare(windSpeedKmh, other.windSpeedKmh) == 0
                && windDirectionDeg == other.windDirectionDeg
                && weatherCode == other.weatherCode
                && day == other.day;
    }
}
