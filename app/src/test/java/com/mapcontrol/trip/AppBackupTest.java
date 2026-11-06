package com.mapcontrol.trip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AppBackupTest {

    /** Bellekte tercih dosyaları, yüklü paket listesi ve ekran. */
    static final class FakeEnv implements AppBackup.Env {
        final Map<String, AutomationBackupTest.FakePrefs> files = new HashMap<>();
        final Set<String> installed = new HashSet<>();
        int w = 1920;
        int h = 720;

        @Override public SharedPreferences prefs(String file) {
            return files.computeIfAbsent(file, k -> new AutomationBackupTest.FakePrefs());
        }
        @Override public boolean isInstalled(String p) { return installed.contains(p); }
        @Override public int screenWidth() { return w; }
        @Override public int screenHeight() { return h; }

        AutomationBackupTest.FakePrefs main() { return (AutomationBackupTest.FakePrefs) prefs(AppBackup.FILE_MAIN); }
    }

    private final FakeEnv car = new FakeEnv();
    private final FakeEnv other = new FakeEnv();

    private static JSONObject app(String json) throws Exception {
        return new JSONObject(json);
    }

    private AppBackup.Plan plan(String json) throws Exception {
        return AppBackup.plan(app(json), other);
    }

    @Test
    public void freshCarExportsOnlyScreenAndHasNothingConfigured() throws Exception {
        JSONObject doc = AppBackup.export(car);
        assertEquals(1, doc.length());
        assertEquals(1920, doc.getJSONObject("screen").getInt("w"));
        assertEquals(720, doc.getJSONObject("screen").getInt("h"));
        assertEquals(0, AppBackup.configuredCount(car));
    }

    @Test
    public void exportContainsOnlyWrittenFields() throws Exception {
        car.main().edit().putInt("powerModeSetting", 1).putBoolean("bootAutoStart", false)
                .putString("welcomeDesignId", "winter").commit();
        JSONObject doc = AppBackup.export(car);
        assertEquals(1, doc.getJSONObject("power").getInt("mode"));
        assertFalse(doc.getJSONObject("power").getBoolean("bootAutoStart"));
        assertEquals(2, doc.getJSONObject("power").length()); // dokunulmayan ayar belgeye girmez
        assertEquals("winter", doc.getJSONObject("welcome").getString("designId"));
        assertFalse(doc.has("launcher"));
        assertEquals(3, AppBackup.configuredCount(car));
    }

    @Test
    public void exportThenApplyOnAnotherCarRestoresEverything() throws Exception {
        car.installed.add("com.waze");
        car.installed.add("com.yandex.maps");
        other.installed.addAll(car.installed);
        AutomationBackupTest.FakePrefs m = car.main();
        m.edit().putInt("powerModeSetting", 0).putBoolean("bootAutoLaunchUi", false)
                .putBoolean("mapControlKeyEnabled", false).putString("targetPackage", "com.waze")
                .putBoolean("floatingBackButtonEnabled", false).putInt("floatingBackPosX", 40).putInt("floatingBackPosY", 90)
                .putBoolean("floatingBackPosSaved", true)
                .putInt("google_cluster_nav_eta_scale_pct", 130).putInt("google_cluster_nav_eta_offset_x_dp", -12)
                .putString("launcher_swipe_up", "com.yandex.maps").putString("launcher_swipe_left", "action:main_menu")
                .putString("launcher_quick_apps", "com.waze||com.yandex.maps")
                .putBoolean("launcher_quick_apps_show_all", true).putInt("launcher_quick_apps_visible_count", 12)
                .putString("welcomeDesignId", "hike").putString("welcomeCustomText", "Merhaba\nKış geldi")
                .putString("welcomeTtsText", "Hoş geldin").putString("welcomeTtsVoice", "tr-TR-x").putBoolean("welcomeAudioAutoPlay", true)
                .putInt("nightModeSetting", 2)
                .putBoolean("equalizerEnabled", true).putString("equalizerPreset", "custom")
                .putInt("equalizerGainMb", 600).putString("equalizerBands", "100,-200,0,300,500")
                .putBoolean("vehicleModelUseAutoDetection", false).putString("vehicleModelMaterialPackage", "com.waze")
                .putString("vehicleModelMaterialDrawable", "ic_tiggo_8").putString("vehicleModelMaterialLabel", "Tiggo 8")
                .putBoolean("dmsFaceRecognitionEnabled", true).commit();
        car.prefs(AppBackup.FILE_CAMERA).edit().putBoolean("autoUsb", true).putInt("resIndex", 2).commit();
        car.prefs(AppBackup.FILE_SPLIT).edit().putString("split_pkg1", "com.waze").putString("split_ratio", "half").commit();
        car.prefs(AppBackup.FILE_LOCALE).edit().putString("locale_tag", "en").commit();
        car.prefs(AppBackup.FILE_SUNROOF).edit().putBoolean("sunroof_gesture_overlay_enabled", true)
                .putInt("sunroof_gesture_overlay_mode", 1).commit();

        AppBackup.Plan p = AppBackup.plan(AppBackup.export(car), other);
        assertTrue(p.errors.toString(), p.ok());
        assertTrue(p.skipped.toString(), p.skipped.isEmpty());
        p.commit();

        assertEquals(car.main().map, other.main().map);
        assertEquals(car.prefs(AppBackup.FILE_CAMERA).getAll(), other.prefs(AppBackup.FILE_CAMERA).getAll());
        assertEquals(car.prefs(AppBackup.FILE_SPLIT).getAll(), other.prefs(AppBackup.FILE_SPLIT).getAll());
        assertEquals(car.prefs(AppBackup.FILE_LOCALE).getAll(), other.prefs(AppBackup.FILE_LOCALE).getAll());
        assertEquals(car.prefs(AppBackup.FILE_SUNROOF).getAll(), other.prefs(AppBackup.FILE_SUNROOF).getAll());
    }

    @Test
    public void oneInvalidFieldWritesNothing() throws Exception {
        AppBackup.Plan p = plan("{\"power\":{\"mode\":5,\"bootAutoStart\":false},\"welcome\":{\"designId\":\"hike\"}}");
        assertFalse(p.ok());
        assertEquals("[app.power.mode]", p.errors.toString());
        p.commit();
        assertEquals(0, AppBackup.configuredCount(other)); // geçerli alanlar da yazılmadı
    }

    @Test
    public void rejectsWrongTypesRangesAndUnknownNames() throws Exception {
        assertEquals("[app.power.bootAutoStart]", plan("{\"power\":{\"bootAutoStart\":\"evet\"}}").errors.toString());
        assertEquals("[app.equalizer.gainMb]", plan("{\"equalizer\":{\"gainMb\":1501}}").errors.toString());
        assertEquals("[app.look.nightMode]", plan("{\"look\":{\"nightMode\":0}}").errors.toString());
        assertEquals("[app.look.locale]", plan("{\"look\":{\"locale\":\"de\"}}").errors.toString());
        assertEquals("[app.welcome.designId]", plan("{\"welcome\":{\"designId\":\"disko\"}}").errors.toString());
        assertEquals("[app.equalizer.bands]", plan("{\"equalizer\":{\"bands\":\"1,a,3\"}}").errors.toString());
        assertEquals("[app.split.ratio]", plan("{\"split\":{\"ratio\":\"third\"}}").errors.toString());
        assertEquals("[app.clusterOverlay.googleEtaScale]", plan("{\"clusterOverlay\":{\"googleEtaScale\":200}}").errors.toString());
        assertEquals("[app.power.yok]", plan("{\"power\":{\"yok\":1}}").errors.toString());
        assertEquals("[app.yok]", plan("{\"yok\":{\"a\":1}}").errors.toString());
        assertEquals("[app.power]", plan("{\"power\":3}").errors.toString());
        assertEquals("[app.projection.targetPackage]", plan("{\"projection\":{\"targetPackage\":\"../etc\"}}").errors.toString());
        assertEquals("[app.screen]", plan("{\"screen\":{\"w\":\"x\",\"h\":720}}").errors.toString());
    }

    @Test
    public void textFieldsRejectControlCharactersAndLongValues() throws Exception {
        assertTrue(plan("{\"welcome\":{\"customText\":\"a\\nb\\tc\"}}").ok());
        assertEquals("[app.welcome.customText]", plan("{\"welcome\":{\"customText\":\"a\\u0007b\"}}").errors.toString());
        StringBuilder long501 = new StringBuilder();
        for (int i = 0; i < 501; i++) long501.append('a');
        assertEquals("[app.welcome.customText]",
                plan("{\"welcome\":{\"customText\":\"" + long501 + "\"}}").errors.toString());
        assertEquals("[app.vehicleModel.drawable]", plan("{\"vehicleModel\":{\"drawable\":\"../x\"}}").errors.toString());
    }

    @Test
    public void differentScreenSkipsPositionsButAppliesTheRest() throws Exception {
        other.w = 1280;
        other.h = 800;
        AppBackup.Plan p = plan("{\"screen\":{\"w\":1920,\"h\":720},"
                + "\"floating\":{\"backButton\":false,\"backX\":40,\"backPosSaved\":true},"
                + "\"clusterOverlay\":{\"googleEtaScale\":120},\"power\":{\"mode\":1}}");
        assertTrue(p.ok());
        assertEquals("[screen]", p.skipped.toString());
        p.commit();
        assertEquals(1, other.main().getInt("powerModeSetting", -1));
        assertFalse(other.main().getBoolean("floatingBackButtonEnabled", true));
        assertFalse(other.main().contains("floatingBackPosX"));
        assertFalse(other.main().contains("floatingBackPosSaved"));
        assertFalse(other.main().contains("google_cluster_nav_eta_scale_pct"));
    }

    @Test
    public void positionsWithoutScreenInfoAreSkippedAndSwappedOrientationMatches() throws Exception {
        assertEquals("[screen]", plan("{\"floating\":{\"backX\":4}}").skipped.toString());
        other.w = 720;
        other.h = 1920; // yatay/dikey fark etmez
        AppBackup.Plan p = plan("{\"screen\":{\"w\":1920,\"h\":720},\"floating\":{\"backX\":4}}");
        assertTrue(p.skipped.isEmpty());
        p.commit();
        assertEquals(4, other.main().getInt("floatingBackPosX", 0));
    }

    @Test
    public void uninstalledPackagesAreSkippedAndOthersApplied() throws Exception {
        other.installed.add("com.waze");
        AppBackup.Plan p = plan("{\"projection\":{\"targetPackage\":\"com.yok.app\"},"
                + "\"launcher\":{\"swipeUp\":\"com.yok.app\",\"swipeDown\":\"com.waze\",\"swipeLeft\":\"action:app_tray\","
                + "\"quickApps\":\"com.waze|com.yok.app||com.waze\"},"
                + "\"split\":{\"pkg1\":\"com.waze\",\"pkg2\":\"com.yok.app\"}}");
        assertTrue(p.errors.toString(), p.ok());
        assertEquals("[launcher.quickApps[1], launcher.swipeUp, projection.targetPackage, split.pkg2]",
                p.skipped.toString());
        p.commit();
        assertFalse(other.main().contains("targetPackage"));
        assertFalse(other.main().contains("launcher_swipe_up"));
        assertEquals("com.waze", other.main().getString("launcher_swipe_down", null));
        assertEquals("action:app_tray", other.main().getString("launcher_swipe_left", null)); // eylem paket değil
        assertEquals("com.waze|||com.waze", other.main().getString("launcher_quick_apps", null)); // yuva sırası korunur
        assertEquals("com.waze", other.prefs(AppBackup.FILE_SPLIT).getString("split_pkg1", null));
        assertFalse(other.prefs(AppBackup.FILE_SPLIT).contains("split_pkg2"));
    }

    @Test
    public void emptyPackageClearsTheSettingEvenWhenNothingIsInstalled() throws Exception {
        AppBackup.Plan p = plan("{\"projection\":{\"targetPackage\":\"\"},\"launcher\":{\"swipeUp\":\"\"}}");
        assertTrue(p.skipped.isEmpty());
        p.commit();
        assertEquals("", other.main().getString("targetPackage", null));
    }

    @Test
    public void vehicleModelIsAppliedAsAWhole() throws Exception {
        AppBackup.Plan p = plan("{\"vehicleModel\":{\"autoDetect\":false,\"package\":\"com.yok.model\","
                + "\"drawable\":\"ic_x\",\"label\":\"X\"}}");
        assertTrue(p.ok());
        assertEquals("[vehicleModel.drawable, vehicleModel.label, vehicleModel.package]", p.skipped.toString());
        assertTrue(p.skipped.contains("vehicleModel.package"));
        assertTrue(p.skipped.contains("vehicleModel.drawable"));
        assertTrue(p.skipped.contains("vehicleModel.label"));
        p.commit();
        assertFalse(other.main().getBoolean("vehicleModelUseAutoDetection", true)); // bağımsız alan uygulanır
        assertFalse(other.main().contains("vehicleModelMaterialDrawable"));

        other.installed.add("com.yok.model");
        AppBackup.Plan p2 = plan("{\"vehicleModel\":{\"package\":\"com.yok.model\",\"drawable\":\"ic_x\",\"label\":\"X\"}}");
        assertTrue(p2.skipped.isEmpty());
        p2.commit();
        assertEquals("X", other.main().getString("vehicleModelMaterialLabel", null));
    }

    @Test
    public void exportSkipsValuesThatRestoreWouldReject() throws Exception {
        car.main().edit().putInt("nightModeSetting", 7).putInt("powerModeSetting", 1)
                .putString("welcomeDesignId", "eski_tasarim").commit();
        car.prefs(AppBackup.FILE_LOCALE).edit().putString("locale_tag", "tr-TR").commit();
        car.main().edit().putString("floatingBackPosX", "yanlis-tur").commit(); // beklenmeyen tür
        JSONObject doc = AppBackup.export(car);
        assertFalse(doc.has("look"));
        assertFalse(doc.has("welcome"));
        assertFalse(doc.has("floating"));
        assertEquals(1, doc.getJSONObject("power").getInt("mode"));
        assertTrue(AppBackup.plan(doc, other).ok()); // üretilen belge her zaman geri yüklenebilir
    }

    @Test
    public void secretsIdentityStateAndConsentsNeverLeaveTheCar() throws Exception {
        car.main().edit().putString("adbauthToken", "GIZLI-TOKEN").putString("phoneToken", "GIZLI-ANAHTAR")
                .putString("carId", "0123456789abcdef").putString("carToken", "GIZLI-2").putString("userEmail", "a@b.c")
                .putString("localDmsFaceProfiles", "YUZ-VERISI").putString("dmsFaceRecognitionLastName", "Ali")
                .putBoolean("disclaimerAccepted", true).putBoolean("onboardingCompleted", true)
                .putBoolean("webServerAutoStart", true).putBoolean("launcherModeEnabled", true)
                .putString("welcomeAudioFilePath", "/sdcard/x.mp3").putString("bootGeneration", "g1")
                .putBoolean("gpsProviderOwned", true).putString("open_meteo_weather_cache", "{}").commit();
        car.prefs("car_seat_memory_cache").edit().putInt("seat_memory_hardware_mode", 1).commit();
        car.main().edit().putInt("powerModeSetting", 2).commit();
        String json = AppBackup.export(car).toString();
        for (String leaked : new String[]{"GIZLI", "0123456789abcdef", "YUZ-VERISI", "Ali", "a@b.c", "sdcard", "g1",
                "disclaimer", "onboarding", "webServer", "launcherMode"}) {
            assertFalse(leaked, json.contains(leaked));
        }
        assertEquals(1, AppBackup.configuredCount(car)); // yalnızca powerModeSetting izin listesinde
    }

    @Test
    public void largestPossibleDocumentStaysUnderTheAccountLimit() throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 1000; i++) text.append('ğ');
        StringBuilder slots = new StringBuilder();
        for (int i = 0; i < 35; i++) {
            if (i > 0) slots.append('|');
            slots.append("com.example.app").append(i).append("x");
        }
        car.main().edit().putString("welcomeCustomText", text.substring(0, 500)).putString("welcomeTtsText", text.toString())
                .putString("welcomeTtsVoice", text.substring(0, 100)).putString("launcher_quick_apps", slots.toString())
                .putString("equalizerBands", "-32768,-32768,-32768,-32768,-32768,-32768,-32768,-32768").commit();
        assertTrue(AppBackup.export(car).toString().getBytes("UTF-8").length < 8 * 1024);
    }

    // ---- AutomationBackup ile birlikte -----------------------------------

    @Test
    public void automationDocumentGainsAnAppSectionWithoutBumpingTheVersion() throws Exception {
        AutomationBackupTest.FakePrefs sunroof = new AutomationBackupTest.FakePrefs();
        car.main().edit().putInt("powerModeSetting", 1).commit();
        JSONObject doc = AutomationBackup.export(car.main(), sunroof, car);
        assertEquals(1, doc.getInt("version"));
        assertEquals(1, doc.getJSONObject("app").getJSONObject("power").getInt("mode"));
        assertFalse(AutomationBackup.export(car.main(), sunroof).has("app")); // eski çağrı aynen
    }

    @Test
    public void applyWritesAppSectionTogetherWithTheRestAndReportsSkips() throws Exception {
        AutomationBackupTest.FakePrefs sunroof = new AutomationBackupTest.FakePrefs();
        JSONObject doc = new JSONObject("{\"version\":1,\"sunroof\":{\"enabled\":true},"
                + "\"app\":{\"power\":{\"mode\":0},\"launcher\":{\"swipeUp\":\"com.yok.app\"}}}");
        AutomationBackup.Result r = AutomationBackup.apply(doc, other.main(), sunroof, other);
        assertTrue(r.errors.toString(), r.ok());
        assertEquals("[launcher.swipeUp]", r.skipped.toString());
        assertTrue(sunroof.getBoolean("sunroof_auto_enabled", false));
        assertEquals(0, other.main().getInt("powerModeSetting", -1));
    }

    @Test
    public void invalidAppSectionBlocksTheWholeRestore() throws Exception {
        AutomationBackupTest.FakePrefs sunroof = new AutomationBackupTest.FakePrefs();
        JSONObject doc = new JSONObject("{\"version\":1,\"sunroof\":{\"enabled\":true},\"app\":{\"power\":{\"mode\":9}}}");
        AutomationBackup.Result r = AutomationBackup.apply(doc, other.main(), sunroof, other);
        assertEquals("[app.power.mode]", r.errors.toString());
        assertFalse(sunroof.contains("sunroof_auto_enabled")); // cam tavan da yazılmadı
        assertFalse(AutomationBackup.apply(new JSONObject("{\"version\":1,\"app\":5}"), other.main(), sunroof, other).ok());
    }

    @Test
    public void withoutEnvTheAppSectionIsIgnoredAndOldDocumentsStillApply() throws Exception {
        AutomationBackupTest.FakePrefs sunroof = new AutomationBackupTest.FakePrefs();
        JSONObject doc = new JSONObject("{\"version\":1,\"sunroof\":{\"enabled\":true},\"app\":{\"power\":{\"mode\":9}}}");
        assertTrue(AutomationBackup.apply(doc, other.main(), sunroof).ok());
        assertNull(other.main().getAll().get("powerModeSetting"));
        assertTrue(AutomationBackup.apply(new JSONObject("{\"version\":1,\"sunroof\":{\"enabled\":false}}"),
                other.main(), sunroof, other).ok());
    }

    @Test
    public void pristineMeansNothingBackupWorthyIsWritten() throws Exception {
        AutomationBackupTest.FakePrefs sunroof = new AutomationBackupTest.FakePrefs();
        assertTrue(AutomationBackup.isPristine(car.main(), sunroof, car));

        // Yedeğe girmeyen anahtarlar fabrika durumunu bozmaz.
        car.main().edit().putString("phoneToken", "x").putString("carId", "y").putBoolean("onboardingCompleted", true).commit();
        sunroof.edit().putBoolean("sunroof_auto_simulated_rain", true).putInt("sunroof_auto_cooldown_sec", 30).commit();
        assertTrue(AutomationBackup.isPristine(car.main(), sunroof, car));

        AutomationBackupTest.FakePrefs sun2 = new AutomationBackupTest.FakePrefs();
        sun2.edit().putBoolean("sunroof_auto_enabled", true).commit();
        assertFalse(AutomationBackup.isPristine(car.main(), sun2, car));

        FakeEnv climate = new FakeEnv();
        climate.main().edit().putBoolean("climateAuto_driver_heat_on", true).commit();
        assertFalse(AutomationBackup.isPristine(climate.main(), new AutomationBackupTest.FakePrefs(), climate));

        FakeEnv assist = new FakeEnv();
        assist.main().edit().putInt("aebSetting", 2).commit();
        assertFalse(AutomationBackup.isPristine(assist.main(), new AutomationBackupTest.FakePrefs(), assist));

        FakeEnv appOnly = new FakeEnv();
        appOnly.main().edit().putInt("powerModeSetting", 1).commit();
        assertFalse(AutomationBackup.isPristine(appOnly.main(), new AutomationBackupTest.FakePrefs(), appOnly));
    }
}
