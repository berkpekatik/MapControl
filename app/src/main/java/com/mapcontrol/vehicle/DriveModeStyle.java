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
 * Eco=0, Normal=1, Sport=2 (DriveModeTabBuilder ile aynı).
 */
public final class DriveModeStyle {

    public static final int ECO = 0;
    public static final int NORMAL = 1;
    public static final int SPORT = 2;

    private DriveModeStyle() {
    }

    public static boolean isThemed(int driveMode) {
        return driveMode == ECO || driveMode == NORMAL || driveMode == SPORT;
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
            case 3:
                return "SNOW";
            case 4:
                return "MUD";
            case 5:
                return "OFFROAD";
            case 7:
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
            default:
                res = R.color.oemAccent;
                break;
        }
        return UiStyles.color(context, res);
    }

}
