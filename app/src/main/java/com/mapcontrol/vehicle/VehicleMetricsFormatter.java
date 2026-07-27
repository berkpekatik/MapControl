package com.mapcontrol.vehicle;

import android.content.Context;

import com.mapcontrol.R;

import java.util.Locale;

/**
 * Araç metriklerini kullanıcıya gösterilebilir metne çevirir.
 */
public final class VehicleMetricsFormatter {

    private final Context context;

    public VehicleMetricsFormatter(Context context) {
        this.context = context.getApplicationContext();
    }

    public String formatDashboardSpeed(VehicleMetricsSnapshot snapshot) {
        int speed = snapshot.preferredSpeed();
        if (speed > 0) {
            return String.format(Locale.getDefault(), "%d", speed);
        }
        return "—";
    }

    public String formatDashboardOdo(VehicleMetricsSnapshot snapshot) {
        int odo = snapshot.odoKm;
        if (!VehicleMetricsSnapshot.isValid(odo) || odo <= 0) {
            return dash();
        }
        return String.format(Locale.getDefault(), "%,d km", odo);
    }

    public String formatDashboardFuel(VehicleMetricsSnapshot snapshot) {
        int fuel = snapshot.fuelPercent;
        if (!VehicleMetricsSnapshot.isValid(fuel) || fuel < 0) {
            return dash();
        }
        // OEM: 0–100 tam yüzde; >100 ise onda bir (348 → 34.8)
        if (fuel <= 100) {
            return String.format(Locale.getDefault(), "%% %d", fuel);
        }
        return String.format(Locale.getDefault(), "%% %.1f", fuel / 10.0);
    }

    public String formatDashboardRange(VehicleMetricsSnapshot snapshot) {
        int range = snapshot.rangeKm;
        if (!VehicleMetricsSnapshot.isValid(range) || range <= 0) {
            return dash();
        }
        return String.format(Locale.getDefault(), "%,d km", range);
    }

    public String formatDashboardBattery(VehicleMetricsSnapshot snapshot) {
        int raw = snapshot.batteryRaw;
        if (!VehicleMetricsSnapshot.isValid(raw) || raw <= 0) {
            return dash();
        }
        return String.format(Locale.getDefault(), "%.1f V", raw / 1000.0);
    }

    public String formatDashboardGearShort(VehicleMetricsSnapshot snapshot) {
        int combined = snapshot.gearSignal;
        if (!VehicleMetricsSnapshot.isValid(combined)) {
            return "—";
        }
        switch (combined) {
            case 1:
                return "P";
            case 2:
                return "R";
            case 3:
                return "N";
            case 4:
                return "D";
            default:
                if (combined > 4) {
                    return "D" + (combined - 4);
                }
                return "—";
        }
    }

    public String formatDashboardPowerStatus(VehicleMetricsSnapshot snapshot) {
        int mode = snapshot.powerMode;
        if (!VehicleMetricsSnapshot.isValid(mode)) {
            return context.getString(R.string.launcher_dashboard_offline);
        }
        if (mode == 2) {
            return context.getString(R.string.vehicle_info_power_running);
        }
        return String.format(Locale.getDefault(),
                context.getString(R.string.vehicle_info_power_other), mode);
    }

    public String formatDashboardGearLabel(VehicleMetricsSnapshot snapshot) {
        return context.getString(R.string.launcher_dashboard_gear_label,
                formatDashboardGearShort(snapshot));
    }

    public String formatDashboardRpm(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined) || combined <= 0) {
            return dash();
        }
        int rpm = combined * 10;
        return String.format(Locale.getDefault(), "%,d", rpm);
    }

    public String formatDashboardTrip(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined) || combined <= 0) {
            return dash();
        }
        if (combined > 100_000) {
            return String.format(Locale.getDefault(), "%,d km", combined);
        }
        return String.format(Locale.getDefault(), "%.1f km", combined / 10.0);
    }

    public String formatDashboardConsumption(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined) || combined <= 0) {
            return dash();
        }
        return String.format(Locale.getDefault(), "%.1f L/100", combined / 10.0);
    }

    public String formatDashboardTemperature(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined) || combined <= 0) {
            return dash();
        }
        if (combined > 200) {
            return String.format(Locale.getDefault(), "%.0f°C", combined / 10.0);
        }
        return String.format(Locale.getDefault(), "%d°C", combined);
    }

    public String formatDashboardLowFuelShort(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined)) {
            return dash();
        }
        if (combined == 1) {
            return context.getString(R.string.launcher_dashboard_low_fuel_yes);
        }
        return context.getString(R.string.launcher_dashboard_low_fuel_no);
    }

    public int fuelPercentOrZero(VehicleMetricsSnapshot snapshot) {
        int fuel = snapshot.fuelPercent;
        if (!VehicleMetricsSnapshot.isValid(fuel) || fuel < 0) {
            return 0;
        }
        int percent = fuel <= 100 ? fuel : Math.round(fuel / 10f);
        return Math.max(0, Math.min(percent, 100));
    }

    public String formatMediaDuration(long millis) {
        if (millis <= 0L) {
            return "0:00";
        }
        long totalSeconds = millis / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes >= 60L) {
            long hours = minutes / 60L;
            minutes = minutes % 60L;
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    public String formatKm(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined) || combined <= 0) {
            return unknown();
        }
        return String.format(Locale.getDefault(), "%,d km", combined);
    }

    public String formatDeciKm(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined) || combined <= 0) {
            return unknown();
        }
        if (combined > 100_000) {
            return formatKm(combined);
        }
        return String.format(Locale.getDefault(), "%.1f km", combined / 10.0);
    }

    public String formatFuelLevel(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined) || combined < 0) {
            return unknown();
        }
        if (combined <= 100) {
            return String.format(Locale.getDefault(), "Depo yaklaşık %% %d dolu", combined);
        }
        // Onda bir yüzde (348 → 34.8)
        return String.format(Locale.getDefault(), "Depo yaklaşık %% %.1f dolu", combined / 10.0);
    }

    public String formatFuelUsedMl(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined) || combined <= 0) {
            return unknown();
        }
        if (combined >= 1000) {
            return String.format(Locale.getDefault(), "%.2f litre (ETM TotaUseFul)", combined / 1000.0);
        }
        return String.format(Locale.getDefault(), "%d ml", combined);
    }

    public String formatSpeed(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined) || combined <= 0) {
            return context.getString(R.string.vehicle_info_speed_parked);
        }
        return String.format(Locale.getDefault(), "%d km/saat", combined);
    }

    public String formatRpm(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined) || combined <= 0) {
            return context.getString(R.string.vehicle_info_engine_off);
        }
        int rpm = combined * 10;
        return String.format(Locale.getDefault(), "%,d devir/dakika", rpm);
    }

    public String formatConsumption(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined) || combined <= 0) {
            return unknown();
        }
        return String.format(Locale.getDefault(), "%.1f litre / 100 km", combined / 10.0);
    }

    public String formatTemperature(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined) || combined <= 0) {
            return unknown();
        }
        if (combined > 200) {
            return String.format(Locale.getDefault(), "%.1f °C", combined / 10.0);
        }
        return String.format(Locale.getDefault(), "%d °C", combined);
    }

    public String formatVoltage(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined) || combined <= 0) {
            return unknown();
        }
        return String.format(Locale.getDefault(), "%.1f volt", combined / 1000.0);
    }

    public String formatLowFuel(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined)) {
            return unknown();
        }
        if (combined == 0) {
            return context.getString(R.string.vehicle_info_low_fuel_no);
        }
        if (combined == 1) {
            return context.getString(R.string.vehicle_info_low_fuel_yes);
        }
        return context.getString(R.string.vehicle_info_low_fuel_active);
    }

    public String formatGear(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined)) {
            return unknown();
        }
        String label;
        switch (combined) {
            case 0:
                label = context.getString(R.string.vehicle_info_gear_unknown);
                break;
            case 1:
                label = "P";
                break;
            case 2:
                label = "R";
                break;
            case 3:
                label = "N";
                break;
            case 4:
                label = "D";
                break;
            default:
                label = "D" + (combined - 4);
                break;
        }
        return String.format(Locale.getDefault(), "%s (sinyal %d)", label, combined);
    }

    public String formatPowerMode(int combined) {
        if (!VehicleMetricsSnapshot.isValid(combined)) {
            return unknown();
        }
        if (combined == 2) {
            return context.getString(R.string.vehicle_info_power_running);
        }
        return String.format(Locale.getDefault(),
                context.getString(R.string.vehicle_info_power_other), combined);
    }

    private String unknown() {
        return context.getString(R.string.vehicle_info_value_unknown);
    }

    private String dash() {
        return "—";
    }
}
