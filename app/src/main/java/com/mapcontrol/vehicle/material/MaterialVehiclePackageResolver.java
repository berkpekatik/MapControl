package com.mapcontrol.vehicle.material;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * OEM vehiclesetting {@code ResourceLoadManager#a()} ile aynı materialvehicle paket seçimi.
 */
final class MaterialVehiclePackageResolver {

    private static final String TAG = "MaterialVehiclePkg";

    static final class Resolution {
        @Nullable final String packageName;
        final String carDrawableBaseName;
        @Nullable final String platformLabel;

        Resolution(@Nullable String packageName, String carDrawableBaseName, @Nullable String platformLabel) {
            this.packageName = packageName;
            this.carDrawableBaseName = carDrawableBaseName;
            this.platformLabel = platformLabel;
        }
    }

    private MaterialVehiclePackageResolver() {
    }

    static Resolution resolve(Context context) {
        OemEolConfigReader eol = new OemEolConfigReader();
        eol.load(context);

        String pkg = null;
        String platform = null;

        if (eol.isT19CevInt()) {
            pkg = "com.desaysv.materialvehicle.t19c.ev";
            platform = "T19C_EV";
        } else if (eol.isT19cInt()) {
            pkg = "com.desaysv.materialvehicle.t19c";
            platform = "T19C";
        } else if (eol.isT18fl3Int()) {
            pkg = "com.desaysv.materialvehicle.t18fl3";
            platform = "T18FL3";
        } else if (eol.isT26Int()) {
            pkg = "com.desaysv.materialvehicle.t26";
            platform = "T26";
        } else if (eol.isM1ePhevInt()) {
            pkg = "com.desaysv.materialvehicle.m1e.phev";
            platform = "M1E_PHEV";
        } else if (eol.isT1eflInt()) {
            pkg = "com.desaysv.materialvehiclet1efl";
            platform = "T1EFL";
        } else if (eol.isT19flInt()) {
            pkg = "com.desaysv.materialvehiclet19fl";
            platform = "T19FL";
        } else if (eol.isM1eInt()) {
            pkg = "com.desaysv.materialvehicle.m1e";
            platform = "M1E";
        }

        String drawableBase = eol.resolveCarDrawableBaseName();

        if (pkg != null && isPackageInstalled(context, pkg)) {
            Log.i(TAG, "EOL paketi: " + pkg + " (" + platform + "), drawable=" + drawableBase);
            return new Resolution(pkg, drawableBase, platform);
        }

        if (pkg != null) {
            Log.w(TAG, "EOL paketi yüklü değil: " + pkg + " (" + platform + ")");
        } else {
            Log.w(TAG, "EOL eşleşmedi, modelCode=" + eol.getModelCode());
        }

        if (isPackageInstalled(context, "com.desaysv.materialvehicle")) {
            Log.i(TAG, "Yedek paket: com.desaysv.materialvehicle");
            return new Resolution("com.desaysv.materialvehicle", drawableBase, "GENERIC");
        }

        return new Resolution(null, drawableBase, platform);
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
