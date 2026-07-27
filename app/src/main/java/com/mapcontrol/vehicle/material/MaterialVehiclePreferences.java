package com.mapcontrol.vehicle.material;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * Kullanıcının manuel seçtiği materialvehicle paketi ve drawable adı.
 */
public final class MaterialVehiclePreferences {

    public static final String KEY_MANUAL_PACKAGE = "vehicleModelMaterialPackage";
    public static final String KEY_MANUAL_DRAWABLE = "vehicleModelMaterialDrawable";
    public static final String KEY_MANUAL_LABEL = "vehicleModelMaterialLabel";
    public static final String KEY_USE_AUTO_DETECTION = "vehicleModelUseAutoDetection";

    private static final String PREFS_NAME = "MapControlPrefs";

    public static final class Selection {
        public final String packageName;
        public final String drawableName;
        public final String label;

        public Selection(String packageName, String drawableName, String label) {
            this.packageName = packageName;
            this.drawableName = drawableName;
            this.label = label;
        }
    }

    private MaterialVehiclePreferences() {
    }

    @Nullable
    public static Selection getSelection(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String pkg = prefs.getString(KEY_MANUAL_PACKAGE, null);
        String drawable = prefs.getString(KEY_MANUAL_DRAWABLE, null);
        if (pkg == null || pkg.trim().isEmpty() || drawable == null || drawable.trim().isEmpty()) {
            return null;
        }
        return new Selection(pkg, drawable, prefs.getString(KEY_MANUAL_LABEL, friendlyLabel(pkg)));
    }

    public static void saveSelection(Context context, String packageName, String drawableName, String label) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MANUAL_PACKAGE, packageName)
                .putString(KEY_MANUAL_DRAWABLE, drawableName)
                .putString(KEY_MANUAL_LABEL, label)
                .putBoolean(KEY_USE_AUTO_DETECTION, false)
                .apply();
    }

    public static void enableAutoDetection(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_MANUAL_PACKAGE)
                .remove(KEY_MANUAL_DRAWABLE)
                .remove(KEY_MANUAL_LABEL)
                .putBoolean(KEY_USE_AUTO_DETECTION, true)
                .apply();
    }

    public static boolean isAutoDetectionEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_USE_AUTO_DETECTION, false);
    }

    public static void clearSelection(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_MANUAL_PACKAGE)
                .remove(KEY_MANUAL_DRAWABLE)
                .remove(KEY_MANUAL_LABEL)
                .putBoolean(KEY_USE_AUTO_DETECTION, false)
                .apply();
    }

    public static String friendlyLabel(String packageName) {
        if (packageName == null) {
            return "Bilinmiyor";
        }
        String suffix = packageName.replace("com.desaysv.materialvehicle", "")
                .replace("com.desaysv.", "");
        if (suffix.isEmpty() || suffix.equals("materialvehicle")) {
            return "Genel";
        }
        if (suffix.startsWith(".")) {
            suffix = suffix.substring(1);
        }
        return suffix.toUpperCase().replace('.', ' ');
    }
}
