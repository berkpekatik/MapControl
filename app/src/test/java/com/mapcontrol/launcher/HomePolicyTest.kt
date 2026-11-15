package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePolicyTest {

    private val oem = "com.desaysv.launcher"
    private val self = "com.mapcontrol"
    private val launcher = "com.mapcontrol.launcher.HomeLauncherActivity"
    private val settings = "com.mapcontrol.ui.activity.MainActivity"
    private val picker = "com.mapcontrol.ui.activity.DriveMapPickerActivity"
    private val miniDash = "com.mapcontrol.launcher.MiniDashActivity"
    private val maps = "com.google.android.apps.maps"
    private val music = "com.desaysv.music"

    private fun tracker() = HomeKeyTracker(self, launcher)

    // --- decide ---

    @Test fun launcherModeOffNeverActs() {
        for (before in FrontApp.values()) {
            assertEquals(HomeAction.NONE, HomePolicy.decide(false, before, Long.MAX_VALUE))
            assertEquals(HomeAction.NONE, HomePolicy.decide(false, before, Long.MAX_VALUE))
        }
    }

    @Test fun mapcontrolScreensAndAppsRedirect() {
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, HomePolicy.decide(true, FrontApp.OUR_SCREEN, Long.MAX_VALUE))
        assertEquals(HomeAction.STAY_ON_OEM, HomePolicy.decide(true, FrontApp.OUR_LAUNCHER, Long.MAX_VALUE))
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, HomePolicy.decide(true, FrontApp.OTHER_APP, Long.MAX_VALUE))
        assertEquals(HomeAction.NONE, HomePolicy.decide(true, FrontApp.CAR_HOME, Long.MAX_VALUE))
        assertEquals(HomeAction.NONE, HomePolicy.decide(true, FrontApp.UNKNOWN, Long.MAX_VALUE))
    }

    @Test fun driveScreenRedirectsThirdPartyMapButNotUnknown() {
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, HomePolicy.decide(true, FrontApp.OTHER_APP, Long.MAX_VALUE))
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, HomePolicy.decide(true, FrontApp.OUR_SCREEN, Long.MAX_VALUE))
        assertEquals(HomeAction.NONE, HomePolicy.decide(true, FrontApp.UNKNOWN, Long.MAX_VALUE))
        assertEquals(HomeAction.NONE, HomePolicy.decide(true, FrontApp.CAR_HOME, Long.MAX_VALUE))
    }

    @Test fun oemAppMenuIsNotCarHome() {
        val oem = "com.desaysv.launcher"
        val home = "com.desaysv.launcher.activity.LauncherActivity"
        assertEquals(FrontApp.CAR_HOME, HomePolicy.classify(oem, home, "com.mapcontrol", "L", oem, home))
        assertEquals(FrontApp.OTHER_APP, HomePolicy.classify(oem, "com.desaysv.appmenu.activity.AppMenuActivity", "com.mapcontrol", "L", oem, home))
        assertEquals(FrontApp.CAR_HOME, HomePolicy.classify(oem, "android.widget.FrameLayout", "com.mapcontrol", "L", oem, home))
        assertEquals(FrontApp.CAR_HOME, HomePolicy.classify(oem, "MenuActivity", "com.mapcontrol", "L", oem, null))
    }

    @Test fun debounce() {
        assertEquals(HomeAction.NONE, HomePolicy.decide(true, FrontApp.OUR_SCREEN, HomePolicy.REDIRECT_DEBOUNCE_MS - 1))
        assertEquals(
            HomeAction.REDIRECT_TO_LAUNCHER,
            HomePolicy.decide(true, FrontApp.OUR_SCREEN, HomePolicy.REDIRECT_DEBOUNCE_MS),
        )
    }

    // --- helpers ---

    @Test fun oemHomeDetection() {
        assertTrue(HomePolicy.isOemHome(oem, oem, self))
        assertFalse(HomePolicy.isOemHome(maps, oem, self))
        assertFalse(HomePolicy.isOemHome(self, self, self))
        assertFalse(HomePolicy.isOemHome(oem, null, self))
        assertFalse(HomePolicy.isOemHome(null, oem, self))
    }

    @Test fun transientWindows() {
        assertTrue(HomePolicy.isTransient("com.android.systemui", "android.widget.FrameLayout", self))
        assertTrue(HomePolicy.isTransient("com.desaysv.svhvac", "android.widget.FrameLayout", self))
        assertTrue(HomePolicy.isTransient(self, "android.widget.FrameLayout", self))
        assertTrue(HomePolicy.isTransient(self, null, self))
        assertFalse(HomePolicy.isTransient(self, settings, self))
        assertFalse(HomePolicy.isTransient(maps, "com.google.android.maps.MapsActivity", self))
    }

    @Test fun classify() {
        assertEquals(FrontApp.OUR_LAUNCHER, HomePolicy.classify(self, launcher, self, launcher, oem))
        assertEquals(FrontApp.OUR_SCREEN, HomePolicy.classify(self, settings, self, launcher, oem))
        assertEquals(FrontApp.OUR_SCREEN, HomePolicy.classify(self, picker, self, launcher, oem))
        assertEquals(FrontApp.CAR_HOME, HomePolicy.classify(oem, "Main", self, launcher, oem))
        assertEquals(FrontApp.OTHER_APP, HomePolicy.classify(maps, "MapsActivity", self, launcher, oem))
        // Varsayılan HOME çözülemediyse OEM ana ekranı sıradan uygulamadır (hiç yönlendirme tetiklenmez).
        assertEquals(FrontApp.OTHER_APP, HomePolicy.classify(oem, "Main", self, launcher, null))
    }

    // --- tracker: car scenarios ---

    @Test fun homeFromMapControlSettingsReturnsToLauncher() {
        val t = tracker()
        t.onLauncherTop(true)
        t.onLauncherTop(false)
        t.onWindow(self, settings, 10_000, oem, true)
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, t.onWindow(oem, "Main", 20_000, oem, true))
        // OEM'in art arda ikinci olayı tek açılış.
        assertEquals(HomeAction.NONE, t.onWindow(oem, "Main", 20_300, oem, true))
    }

    @Test fun homeFromOtherMapControlScreensReturnsToLauncher() {
        val t = tracker()
        t.onWindow(self, picker, 10_000, oem, true)
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, t.onWindow(oem, "Main", 11_000, oem, true))
        t.onLauncherTop(true)
        t.onLauncherTop(false)
        t.onWindow(self, "com.mapcontrol.ui.activity.CameraActivity", 30_000, oem, true)
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, t.onWindow(oem, "Main", 31_000, oem, true))
    }

    @Test fun homeOnOurLauncherGoesToCarHome() {
        val t = tracker()
        t.onLauncherTop(true)
        t.onWindow(self, launcher, 1_000, oem, true)
        // Ana ekran hâlâ en üstte görünürken OEM olayı.
        assertEquals(HomeAction.STAY_ON_OEM, t.onWindow(oem, "Main", 10_000, oem, true))
        // Ana ekran öndeki yerini önce bıraktıysa da aynı.
        t.onLauncherTop(true)
        t.onLauncherTop(false)
        assertEquals(HomeAction.STAY_ON_OEM, t.onWindow(oem, "Main", 20_000, oem, true))
    }

    @Test fun homeFromThirdPartyAppReturnsToLauncher() {
        val t = tracker()
        t.onLauncherTop(true)
        t.onLauncherTop(false)
        t.onWindow(maps, "MapsActivity", 10_000, oem, true)
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, t.onWindow(oem, "Main", 20_000, oem, true))
        // MapControl ayarlarından açılan müzik de üçüncü taraf: tuş yine launcher'a döner.
        t.onWindow(self, settings, 30_000, oem, true)
        t.onWindow(music, "MusicActivity", 31_000, oem, true)
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, t.onWindow(oem, "Main", 40_000, oem, true))
    }

    @Test fun transientWindowsDoNotChangeTheFrontApp() {
        val t = tracker()
        t.onWindow(self, settings, 10_000, oem, true)
        t.onWindow("com.android.systemui", "android.widget.FrameLayout", 11_000, oem, true)
        t.onWindow(self, "android.widget.FrameLayout", 11_500, oem, true)
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, t.onWindow(oem, "Main", 12_000, oem, true))
    }

    @Test fun keyboardOrForeignPopupOverMapControlSettingsStillReturnsToLauncher() {
        // MapControl ayarlarında alana yazarken klavye açıldı: klavye kendi paketinden olay gönderir.
        val t = tracker()
        t.onWindow(self, settings, 10_000, oem, true)
        t.onWindow("com.google.android.inputmethod.latin", "android.inputmethodservice.SoftInputWindow", 11_000, oem, true)
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, t.onWindow(oem, "Main", 12_000, oem, true))
        // Başka paketin uygulama penceresi olmayan açılır penceresi (ör. aracın ses çubuğu): öndeki uygulama değişmez.
        t.onWindow(self, settings, 20_000, oem, true)
        t.onWindow("com.desaysv.volume", "android.widget.FrameLayout", 21_000, oem, true, appWindow = false)
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, t.onWindow(oem, "Main", 22_000, oem, true))
        // OEM ana ekranı olayı uygulama penceresi görünmese de atlanmaz.
        t.onWindow(self, settings, 30_000, oem, true)
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, t.onWindow(oem, "Main", 31_000, oem, true, appWindow = false))
    }

    @Test fun driveScreenHomeFromMapReturnsToLauncher() {
        val t = tracker()
        t.onLauncherTop(true)
        t.onLauncherTop(false)
        t.onWindow(self, miniDash, 10_000, oem, true)
        t.onWindow(maps, "MapsActivity", 10_200, oem, true)
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, t.onWindow(oem, "Main", 30_000, oem, true))
        // Sürüş ekranı bittikten sonra da aynı: tuş launcher'a döner.
        t.onLauncherTop(true)
        t.onLauncherTop(false)
        t.onWindow(maps, "MapsActivity", 40_000, oem, true)
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, t.onWindow(oem, "Main", 50_000, oem, true))
    }

    @Test fun launcherModeOffTrackerNeverActs() {
        val t = tracker()
        t.onWindow(self, settings, 10_000, oem, false)
        assertEquals(HomeAction.NONE, t.onWindow(oem, "Main", 20_000, oem, false))
    }

    // --- tracker: accessibility service (re)connect ---

    @Test fun serviceConnectedWhileMapsInFrontDoesNotAssumeLauncher() {
        // 7 Kasım araç kaydı: ana ekranımız önceden öndeydi, hizmet Google Haritalar öndeyken bağlandı.
        val t = tracker()
        t.onLauncherTop(true)
        t.onLauncherTop(false)
        t.onServiceConnected(maps, oem)
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, t.onWindow(oem, "Main", 10_000, oem, true))
    }

    @Test fun serviceConnectedWithUnknownFrontDoesNothing() {
        val t = tracker()
        t.onWindow(self, settings, 1_000, oem, true)
        t.onServiceConnected(null, oem)
        assertEquals(HomeAction.NONE, t.onWindow(oem, "Main", 10_000, oem, true))
        val t2 = tracker()
        t2.onWindow(self, settings, 1_000, oem, true)
        t2.onServiceConnected("com.android.systemui", oem)
        assertEquals(HomeAction.NONE, t2.onWindow(oem, "Main", 10_000, oem, true))
    }

    @Test fun serviceConnectedOnMapControlScreenRedirects() {
        val t = tracker()
        t.onServiceConnected(self, oem)
        assertEquals(HomeAction.REDIRECT_TO_LAUNCHER, t.onWindow(oem, "Main", 10_000, oem, true))
        val t2 = tracker()
        t2.onLauncherTop(true)
        t2.onServiceConnected(self, oem)
        assertEquals(HomeAction.STAY_ON_OEM, t2.onWindow(oem, "Main", 10_000, oem, true))
    }

    @Test fun serviceDisconnectForgetsFrontApp() {
        val t = tracker()
        t.onWindow(self, settings, 1_000, oem, true)
        t.onServiceDisconnected()
        assertEquals(HomeAction.NONE, t.onWindow(oem, "Main", 10_000, oem, true))
    }

    // --- start screen ---

    @Test fun startScreens() {
        assertEquals(StartScreen.LAUNCHER, HomePolicy.bootScreen(true))
        assertEquals(StartScreen.SETTINGS, HomePolicy.bootScreen(false))
        assertEquals(StartScreen.LAUNCHER, HomePolicy.appStartScreen(true, iconLaunch = true, hasOwnRequest = false, restored = false))
        assertEquals(StartScreen.SETTINGS, HomePolicy.appStartScreen(true, iconLaunch = false, hasOwnRequest = true, restored = false))
        assertEquals(StartScreen.SETTINGS, HomePolicy.appStartScreen(true, iconLaunch = true, hasOwnRequest = true, restored = false))
        assertEquals(StartScreen.SETTINGS, HomePolicy.appStartScreen(true, iconLaunch = true, hasOwnRequest = false, restored = true))
        assertEquals(StartScreen.SETTINGS, HomePolicy.appStartScreen(false, iconLaunch = true, hasOwnRequest = false, restored = false))
    }
}
