package com.mapcontrol.vehicle;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

/**
 * OEM {@link com.desaysv.ivi.extra.project.carinfo.NewEnergyID#ID_DRIVE_MODE} değerleri
 * ve dashboard / 3D tema renkleri.
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

    /** Filament ışık rengi (0–1). Temasız modda nötr beyaz. */
    public static float[] lightRgb(int driveMode) {
        switch (driveMode) {
            case ECO:
                return new float[]{0.42f, 0.88f, 0.55f};
            case NORMAL:
                return new float[]{0.52f, 0.72f, 1.00f};
            case SPORT:
                return new float[]{1.00f, 0.38f, 0.36f};
            default:
                return new float[]{1f, 1f, 1f};
        }
    }

    /** IBL irradiance — beyaza karışık hafif ton. */
    public static float[] irradianceRgb(int driveMode) {
        float[] rgb = lightRgb(driveMode);
        if (!isThemed(driveMode)) {
            return new float[]{1f, 1f, 1f};
        }
        return new float[]{
                0.72f + 0.28f * rgb[0],
                0.72f + 0.28f * rgb[1],
                0.72f + 0.28f * rgb[2]
        };
    }
}
