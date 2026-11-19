package com.mapcontrol.vehicle;

import android.content.Context;
import com.mapcontrol.launcher.HomeScreenPrefs;

/** Yeni J7 işlevleri açık Araç modeli > Jaecoo 7 seçimiyle etkinleşir. */
public final class JaecooJ7Features {
    private JaecooJ7Features() {
    }

    public static boolean isEnabled(Context context) {
        return isJ7Selection(HomeScreenPrefs.carModelIndex(context));
    }

    // Otomatik dikey ekran tespiti J7 ve J8'i ayıramaz.
    static boolean isJ7Selection(int modelIndex) {
        return modelIndex == 2;
    }
}
