package com.mapcontrol.ui.welcome;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import androidx.annotation.Nullable;

import com.mapcontrol.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Hoşgeldin ekranı tasarım listesi ve kayıtlı seçim.
 */
public final class WelcomeDesignCatalog {

    public static final String PREFS_NAME = "MapControlPrefs";
    public static final String PREF_KEY = "welcomeDesignId";
    public static final String PREF_LINE = "welcomeCustomText";
    public static final String ID_OFF = "off";
    public static final String ID_SIGNATURE = "signature";
    public static final String ID_WINTER = "winter";
    public static final String ID_HIKE = "hike";

    private static final WelcomeDesign OFF = new WelcomeDesign() {
        @Override
        public String id() {
            return ID_OFF;
        }

        @Override
        public int titleRes() {
            return R.string.welcome_design_off;
        }

        @Override
        public int helpRes() {
            return R.string.welcome_design_off_help;
        }

        @Nullable
        @Override
        public View createView(Context context) {
            return null;
        }
    };

    private static final List<WelcomeDesign> DESIGNS = Collections.unmodifiableList(Arrays.asList(
            OFF,
            new SignatureWelcomeDesign(),
            new WinterTouchWelcomeDesign(),
            new MountainHikeWelcomeDesign()
    ));

    private WelcomeDesignCatalog() {
    }

    public static List<WelcomeDesign> designs() {
        return DESIGNS;
    }

    public static String savedId(SharedPreferences prefs) {
        String id = prefs.getString(PREF_KEY, ID_OFF);
        if (find(id) == null) {
            return ID_OFF;
        }
        return id;
    }

    @Nullable
    public static WelcomeDesign find(String id) {
        if (id == null) {
            return null;
        }
        for (WelcomeDesign design : DESIGNS) {
            if (design.id().equals(id)) {
                return design;
            }
        }
        return null;
    }

    @Nullable
    public static View createView(Context context, String id) {
        WelcomeDesign design = find(id);
        if (design == null) {
            return null;
        }
        return design.createView(context);
    }
}
