package com.mapcontrol.vehicle;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

/**
 * OEM {@link com.desaysv.ivi.extra.project.carinfo.NewEnergyID#ID_DRIVE_MODE} değerleri
 * ve dashboard tema renkleri.
 * <p>
 * Eco=0, Normal=1, Sport=2, Snow=3, Mud=4, Offroad=5, Sand=7.
 */
public final class DriveModeStyle {

    public static final int ECO = 0;
    public static final int NORMAL = 1;
    public static final int SPORT = 2;
    public static final int SNOW = 3;
    public static final int MUD = 4;
    public static final int OFFROAD = 5;
    public static final int SAND = 7;

    private DriveModeStyle() {
    }

    public static boolean isThemed(int driveMode) {
        return shortLabel(driveMode) != null;
    }

    @Nullable
    public static String shortLabel(int driveMode) {
        switch (driveMode) {
            case ECO:
                return "ECO";
            case NORMAL:
                return "NORMAL";
            case SPORT:
                return "SPORT";
            case SNOW:
                return "SNOW";
            case MUD:
                return "MUD";
            case OFFROAD:
                return "OFFROAD";
            case SAND:
                return "SAND";
            default:
                return null;
        }
    }

    @ColorInt
    public static int accentColor(Context context, int driveMode) {
        int res;
        switch (driveMode) {
            case ECO:
                res = R.color.drive_mode_eco;
                break;
            case NORMAL:
                res = R.color.drive_mode_normal;
                break;
            case SPORT:
                res = R.color.drive_mode_sport;
                break;
            case SNOW:
                res = R.color.drive_mode_snow;
                break;
            case MUD:
                res = R.color.drive_mode_mud;
                break;
            case OFFROAD:
                res = R.color.drive_mode_offroad;
                break;
            case SAND:
                res = R.color.drive_mode_sand;
                break;
            default:
                res = R.color.oemAccent;
                break;
        }
        return UiStyles.color(context, res);
    }

}
