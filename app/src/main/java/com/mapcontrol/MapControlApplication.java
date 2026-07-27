package com.mapcontrol;

import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.DisplayHelper;

import com.google.android.filament.Filament;
import com.google.android.filament.utils.Utils;

public final class MapControlApplication extends Application {

    private int lastNightModeUiBits = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        Filament.init();
        Utils.INSTANCE.init();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        lastNightModeUiBits = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        UiStyles.setUiModeOverride(getResources().getConfiguration());
        registerComponentCallbacks(new ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                int night = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (night == lastNightModeUiBits) {
                    return;
                }
                lastNightModeUiBits = night;
                UiStyles.setUiModeOverride(newConfig);
                DisplayHelper.refreshBootSplashAfterConfigurationChange(MapControlApplication.this);
            }

            @Override
            public void onLowMemory() {
            }
        });
    }
}
