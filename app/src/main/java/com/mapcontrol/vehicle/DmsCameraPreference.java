package com.mapcontrol.vehicle;

import android.content.Context;

/** Yüz kaydı ekranı ve arka plan tanıması aynı Camera2 kimliğini kullanır. */
public final class DmsCameraPreference {
    private static final String PREFS = "MapControlPrefs";
    private static final String KEY = "dmsCameraId";

    private DmsCameraPreference() {
    }

    public static String read(Context context) {
        if (!JaecooJ7Features.isEnabled(context)) return "0";
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "0");
    }

    public static void save(Context context, String cameraId) {
        if (!JaecooJ7Features.isEnabled(context)) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, cameraId).apply();
    }
}
