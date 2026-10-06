package com.mapcontrol;

import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.AppLocaleManager;
import com.mapcontrol.util.DisplayHelper;
import com.mapcontrol.util.LauncherLegacyPrefs;

public final class MapControlApplication extends Application {

    private int lastNightModeUiBits = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        AppLocaleManager.applyStoredLocale(this);
        LauncherLegacyPrefs.applyMigrations(this);
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
