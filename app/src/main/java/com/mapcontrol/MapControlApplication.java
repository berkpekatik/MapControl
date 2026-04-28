package com.mapcontrol;

import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

import com.mapcontrol.util.DisplayHelper;

public final class MapControlApplication extends Application {

    private int lastNightModeUiBits = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        lastNightModeUiBits = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        registerComponentCallbacks(new ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                int night = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (night == lastNightModeUiBits) {
                    return;
                }
                lastNightModeUiBits = night;
                DisplayHelper.refreshBootSplashAfterConfigurationChange(MapControlApplication.this);
            }

            @Override
            public void onLowMemory() {
            }
        });
    }
}
