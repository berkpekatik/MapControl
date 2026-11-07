package com.mapcontrol;

import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

import com.mapcontrol.media.ClusterTrackRelay;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.AppLocaleManager;
import com.mapcontrol.util.DisplayHelper;

public final class MapControlApplication extends Application {

    private int lastNightModeUiBits = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        GlitchTip.start(this);
        guardLauncherCrashLoop();
        AppLocaleManager.applyStoredLocale(this);
        ClusterTrackRelay.install(this);
        try {
            com.mapcontrol.launcher.DriveScreen.INSTANCE.recoverStale(this);
        } catch (Throwable ignored) {
        }
        int nightPref = getSharedPreferences("MapControlPrefs", MODE_PRIVATE)
                .getInt("nightModeSetting", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(nightPref);
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

    /**
     * Launcher çökme döngüsüne girdiyse (bkz. LauncherCrashGuard) HOME sabitlemesini kaldırır, alias'ı
     * kapatır ve Launcher Modu'nu kapatır; böylece HOME aracın kendi ana ekranına döner. Bildirimi
     * MainActivity bir kez gösterir.
     */
    private void guardLauncherCrashLoop() {
        // Süreç çökerken hemen kapat (yeniden başlatma beklenmez); aşağıdaki kontrol yedektir.
        com.mapcontrol.launcher.LauncherCrashGuard.INSTANCE.setOnTripped(() ->
                com.mapcontrol.util.LauncherModeManager.setEnabled(this, false));
        try {
            android.content.SharedPreferences sp = getSharedPreferences(
                    com.mapcontrol.launcher.LauncherCrashGuard.PREFS, MODE_PRIVATE);
            if (sp.getBoolean(com.mapcontrol.launcher.LauncherCrashGuard.KEY_TRIPPED, false)) {
                com.mapcontrol.util.LauncherModeManager.setEnabled(this, false);
                sp.edit().remove(com.mapcontrol.launcher.LauncherCrashGuard.KEY_TRIPPED)
                        .remove("crashTimes").remove("startMs").apply();
            }
        } catch (Exception ignored) {
        }
    }
}
