package com.mapcontrol.util;

/**
 * Güç örneğinden karşılama ve ana ekran kararını üretir.
 * Mod 1'in altı karşılama ve ana ekran kaydını siler. Ana ekran, ayar açıkken ve bu güç
 * döngüsünde henüz görünmediyse mod 1 veya üzerindeyken açılır. Güç yükselişi (kenar) kaçsa
 * da, bekleyen istek ya da açılmamış ekran yüzünden tekrar denenir.
 */
public final class StartupGate {

    public final boolean clearWelcome;
    public final boolean showWelcome;
    public final boolean launchUi;

    private StartupGate(boolean clearWelcome, boolean showWelcome, boolean launchUi) {
        this.clearWelcome = clearWelcome;
        this.showWelcome = showWelcome;
        this.launchUi = launchUi;
    }

    public static StartupGate decide(int previousMode, int mode, boolean welcomeShown,
            boolean designEnabled, boolean uiPending, boolean uiEnabled, boolean uiLaunched) {
        if (mode < 1) {
            return new StartupGate(true, false, false);
        }
        boolean show = previousMode < 1 && designEnabled && !welcomeShown;
        boolean launch = uiEnabled && !uiLaunched && (uiPending || previousMode < 1);
        return new StartupGate(false, show, launch);
    }
}
