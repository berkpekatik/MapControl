package com.mapcontrol.trip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import com.mapcontrol.vehicle.ClimateAutomationStore;

import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AutomationBackupTest {

    /** Bellekte çalışan SharedPreferences; commit/apply hemen yazar. */
    static final class FakePrefs implements SharedPreferences {
        final Map<String, Object> map = new HashMap<>();

        @Override public Map<String, ?> getAll() { return map; }
        @Override public String getString(String k, String d) { return map.containsKey(k) ? (String) map.get(k) : d; }
        @Override public Set<String> getStringSet(String k, Set<String> d) { return d; }
        @Override public int getInt(String k, int d) { return map.containsKey(k) ? (Integer) map.get(k) : d; }
        @Override public long getLong(String k, long d) { return d; }
        @Override public float getFloat(String k, float d) { return d; }
        @Override public boolean getBoolean(String k, boolean d) { return map.containsKey(k) ? (Boolean) map.get(k) : d; }
        @Override public boolean contains(String k) { return map.containsKey(k); }
        @Override public Editor edit() { return new Ed(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }

        final class Ed implements Editor {
            private final Map<String, Object> pending = new HashMap<>();
            @Override public Editor putString(String k, String v) { pending.put(k, v); return this; }
            @Override public Editor putStringSet(String k, Set<String> v) { pending.put(k, v); return this; }
            @Override public Editor putInt(String k, int v) { pending.put(k, v); return this; }
            @Override public Editor putLong(String k, long v) { pending.put(k, v); return this; }
            @Override public Editor putFloat(String k, float v) { pending.put(k, v); return this; }
            @Override public Editor putBoolean(String k, boolean v) { pending.put(k, v); return this; }
            @Override public Editor remove(String k) { pending.put(k, null); return this; }
            @Override public Editor clear() { return this; }
            @Override public boolean commit() { map.putAll(pending); return true; }
            @Override public void apply() { commit(); }
        }
    }

    private final FakePrefs main = new FakePrefs();
    private final FakePrefs sunroof = new FakePrefs();

    @Test
    public void exportOfFreshDeviceHasDefaultsAndNoHiddenRule() throws Exception {
        JSONObject doc = AutomationBackup.export(main, sunroof);
        assertEquals(1, doc.getInt("version"));
        JSONObject sun = doc.getJSONObject("sunroof");
        assertFalse(sun.getBoolean("enabled"));
        assertEquals(0, sun.getInt("mode"));
        assertEquals(1, sun.getInt("sensitivity"));
        assertTrue(sun.getBoolean("rainGuard"));
        assertTrue(sun.getBoolean("speedGuard"));
        assertFalse(sun.getBoolean("closeAtNight"));
        JSONObject rules = doc.getJSONObject("climate").getJSONObject("rules");
        assertEquals(7, rules.length());
        assertFalse(rules.has("climate")); // arayüzde gizli kural yedeğe girmez
        assertEquals(6, rules.getJSONObject("driver_heat").getInt("tempC"));
        assertFalse(sun.has("simulatedRain"));
    }

    @Test
    public void exportThenApplyOnAnotherCarRestoresEverything() throws Exception {
        sunroof.edit().putBoolean("sunroof_auto_enabled", true).putInt("sunroof_auto_mode", 2)
                .putInt("sunroof_auto_sensitivity", 0).putBoolean("sunroof_auto_close_at_night", true)
                .putBoolean("sunroof_auto_rain_guard", false).commit();
        ClimateAutomationStore.setMasterEnabled(main, true);
        ClimateAutomationStore.Rule r = ClimateAutomationStore.defaultsFor(ClimateAutomationStore.DRIVER_HEAT);
        r.enabled = true;
        r.tempC = 9;
        r.delayMinutes = 4;
        r.level = 3;
        ClimateAutomationStore.putRule(main, ClimateAutomationStore.DRIVER_HEAT, r);
        JSONObject backup = AutomationBackup.export(main, sunroof);

        FakePrefs newMain = new FakePrefs();
        FakePrefs newSunroof = new FakePrefs();
        AutomationBackup.Result result = AutomationBackup.apply(backup, newMain, newSunroof);
        assertTrue(result.errors.toString(), result.ok());

        assertEquals(backup.toString().length(), AutomationBackup.export(newMain, newSunroof).toString().length());
        assertTrue(newSunroof.getBoolean("sunroof_auto_enabled", false));
        assertEquals(2, newSunroof.getInt("sunroof_auto_mode", -1));
        assertEquals(0, newSunroof.getInt("sunroof_auto_sensitivity", -1));
        assertFalse(newSunroof.getBoolean("sunroof_auto_rain_guard", true));
        assertTrue(ClimateAutomationStore.isMasterEnabled(newMain));
        ClimateAutomationStore.Rule back = ClimateAutomationStore.getRule(newMain, ClimateAutomationStore.DRIVER_HEAT);
        assertTrue(back.enabled);
        assertEquals(9, back.tempC);
        assertEquals(4, back.delayMinutes);
        assertEquals(3, back.level);
    }

    @Test
    public void invalidFieldRejectsTheWholeDocumentAndWritesNothing() throws Exception {
        JSONObject doc = new JSONObject("{\"version\":1,"
                + "\"sunroof\":{\"enabled\":true,\"mode\":7},"
                + "\"climate\":{\"enabled\":true,\"rules\":{\"driver_heat\":{\"tempC\":99,\"level\":2}}}}");
        AutomationBackup.Result result = AutomationBackup.apply(doc, main, sunroof);
        assertFalse(result.ok());
        assertTrue(result.errors.contains("sunroof.mode"));
        assertTrue(result.errors.contains("climate.rules.driver_heat.tempC"));
        assertTrue(sunroof.map.isEmpty()); // geçerli alanlar bile yazılmadı
        assertTrue(main.map.isEmpty());
    }

    @Test
    public void wrongTypesAndUnknownRulesAreRejected() throws Exception {
        AutomationBackup.Result r1 = AutomationBackup.apply(
                new JSONObject("{\"version\":1,\"sunroof\":{\"enabled\":\"true\"}}"), main, sunroof);
        assertTrue(r1.errors.contains("sunroof.enabled"));
        AutomationBackup.Result r2 = AutomationBackup.apply(
                new JSONObject("{\"version\":1,\"climate\":{\"rules\":{\"made_up\":{}}}}"), main, sunroof);
        assertTrue(r2.errors.contains("climate.rules.made_up"));
        AutomationBackup.Result r3 = AutomationBackup.apply(
                new JSONObject("{\"version\":1,\"climate\":{\"rules\":{\"climate\":{\"enabled\":true}}}}"), main, sunroof);
        assertFalse(r3.ok()); // gizli kural geri yüklenmez
        AutomationBackup.Result r4 = AutomationBackup.apply(
                new JSONObject("{\"version\":1,\"climate\":{\"rules\":{\"driver_heat\":{\"tempC\":6.5}}}}"), main, sunroof);
        assertTrue(r4.errors.contains("climate.rules.driver_heat.tempC")); // tam sayı olmalı
        assertTrue(main.map.isEmpty());
    }

    @Test
    public void unsupportedOrMissingVersionIsRejected() throws Exception {
        assertFalse(AutomationBackup.apply(new JSONObject("{\"version\":2}"), main, sunroof).ok());
        assertFalse(AutomationBackup.apply(new JSONObject("{}"), main, sunroof).ok());
    }

    @Test
    public void missingSectionsAndFieldsKeepCurrentValues() throws Exception {
        sunroof.edit().putInt("sunroof_auto_mode", 1).commit();
        ClimateAutomationStore.Rule r = ClimateAutomationStore.defaultsFor(ClimateAutomationStore.WINDSHIELD);
        r.enabled = true;
        r.tempC = 3;
        ClimateAutomationStore.putRule(main, ClimateAutomationStore.WINDSHIELD, r);

        JSONObject doc = new JSONObject("{\"version\":1,\"climate\":{\"rules\":{\"windshield\":{\"tempC\":5}}}}");
        assertTrue(AutomationBackup.apply(doc, main, sunroof).ok());
        assertEquals(1, sunroof.getInt("sunroof_auto_mode", -1)); // sunroof bölümü yok: dokunulmadı
        ClimateAutomationStore.Rule after = ClimateAutomationStore.getRule(main, ClimateAutomationStore.WINDSHIELD);
        assertEquals(5, after.tempC);
        assertTrue(after.enabled); // belgede olmayan alan korundu
    }

    @Test
    public void heatAndVentThatFireTogetherAreRejected() throws Exception {
        // Sürücü ısıtma < 10 ve havalandırma > 5: 5–10 °C arasında ikisi birden tetiklenir.
        JSONObject doc = new JSONObject("{\"version\":1,\"climate\":{\"rules\":{"
                + "\"driver_heat\":{\"enabled\":true,\"compare\":0,\"tempC\":10},"
                + "\"driver_vent\":{\"enabled\":true,\"compare\":2,\"tempC\":5}}}}");
        AutomationBackup.Result result = AutomationBackup.apply(doc, main, sunroof);
        assertFalse(result.ok());
        assertTrue(result.errors.contains("climate.conflict.driver_heat+driver_vent"));
        assertTrue(main.map.isEmpty());

        // Aralıklar kesişmiyorsa kabul: ısıtma < 5, havalandırma > 15.
        JSONObject fine = new JSONObject("{\"version\":1,\"climate\":{\"rules\":{"
                + "\"driver_heat\":{\"enabled\":true,\"compare\":0,\"tempC\":5},"
                + "\"driver_vent\":{\"enabled\":true,\"compare\":2,\"tempC\":15}}}}");
        assertTrue(AutomationBackup.apply(fine, main, sunroof).ok());
    }

    @Test
    public void memoryExportOfFreshDeviceHasNothingSetAndNoAssistDisabled() throws Exception {
        JSONObject memory = AutomationBackup.export(main, sunroof).getJSONObject("memory");
        assertEquals(-1, memory.getInt("driveMode"));
        JSONObject off = memory.getJSONObject("assistsOff");
        assertEquals(7, off.length());
        for (String name : new String[]{"iss", "spdLimit", "ldw", "ldp", "fcw", "aeb", "cpd"}) {
            assertFalse(name, off.getBoolean(name));
        }
    }

    @Test
    public void memoryRoundTripKeepsDriveModeAndDisabledAssists() throws Exception {
        main.edit().putInt("driveModeSetting", 3).putInt("issSetting", 0).putInt("ldwSetting", 2)
                .putInt("cpdSetting", 2).commit();
        JSONObject backup = AutomationBackup.export(main, sunroof);
        JSONObject off = backup.getJSONObject("memory").getJSONObject("assistsOff");
        assertTrue(off.getBoolean("iss"));   // ISS "kapalı" değeri 0
        assertTrue(off.getBoolean("ldw"));
        assertTrue(off.getBoolean("cpd"));
        assertFalse(off.getBoolean("ldp"));

        FakePrefs other = new FakePrefs();
        assertTrue(AutomationBackup.apply(backup, other, new FakePrefs()).ok());
        assertEquals(3, other.getInt("driveModeSetting", -9));
        assertEquals(0, other.getInt("issSetting", -9));
        assertEquals(2, other.getInt("ldwSetting", -9));
        assertEquals(-1, other.getInt("ldpSetting", -9)); // kapalı değil: ayarlanmadı
    }

    @Test
    public void invalidDriveModeOrAssistIsRejected() throws Exception {
        assertTrue(AutomationBackup.apply(new JSONObject("{\"version\":1,\"memory\":{\"driveMode\":6}}"), main, sunroof)
                .errors.contains("memory.driveMode")); // 6 hiçbir kartın değeri değil
        assertTrue(AutomationBackup.apply(new JSONObject("{\"version\":1,\"memory\":{\"assistsOff\":{\"turbo\":true}}}"), main, sunroof)
                .errors.contains("memory.assistsOff.turbo"));
        assertTrue(AutomationBackup.apply(new JSONObject("{\"version\":1,\"memory\":{\"assistsOff\":{\"ldw\":1}}}"), main, sunroof)
                .errors.contains("memory.assistsOff.ldw"));
        assertTrue(main.map.isEmpty());
    }

    @Test
    public void disablingFcwOrAebNeedsTheSafetyAcknowledgement() throws Exception {
        JSONObject doc = new JSONObject("{\"version\":1,\"memory\":{\"driveMode\":1,\"assistsOff\":{\"fcw\":true,\"aeb\":true,\"ldw\":true}}}");
        AutomationBackup.Result refused = AutomationBackup.apply(doc, main, sunroof);
        assertFalse(refused.ok());
        assertTrue(refused.errors.contains("safety.ack_required.fcw"));
        assertTrue(refused.errors.contains("safety.ack_required.aeb"));
        assertFalse(refused.errors.contains("safety.ack_required.ldw")); // diğer yardımlar onay istemez
        assertTrue(main.map.isEmpty()); // hiçbir şey yazılmadı (sürüş modu bile)

        doc.put("safetyAck", true);
        assertTrue(AutomationBackup.apply(doc, main, sunroof).ok());
        assertEquals(2, main.getInt("fcwSetting", -1));
        assertEquals(2, main.getInt("aebSetting", -1));
        assertEquals(1, main.getInt("driveModeSetting", -9));
    }

    @Test
    public void safetyAcknowledgementIsNotNeededWhenAlreadyOffOrWhenTurningOn() throws Exception {
        main.edit().putInt("fcwSetting", 2).commit(); // araçta zaten kapalı
        assertTrue(AutomationBackup.apply(new JSONObject(
                "{\"version\":1,\"memory\":{\"assistsOff\":{\"fcw\":true}}}"), main, sunroof).ok());
        // Yeniden açmak (kapatmayı kaldırmak) her zaman serbest.
        assertTrue(AutomationBackup.apply(new JSONObject(
                "{\"version\":1,\"memory\":{\"assistsOff\":{\"fcw\":false,\"aeb\":false}}}"), main, sunroof).ok());
        assertEquals(-1, main.getInt("fcwSetting", -9));
    }

    @Test
    public void safetyAckIsNeverExportedOrStored() throws Exception {
        JSONObject doc = new JSONObject("{\"version\":1,\"safetyAck\":true,\"memory\":{\"assistsOff\":{\"aeb\":true}}}");
        assertTrue(AutomationBackup.apply(doc, main, sunroof).ok());
        assertFalse(AutomationBackup.export(main, sunroof).has("safetyAck"));
        assertFalse(main.map.containsKey("safetyAck"));
    }

    @Test
    public void alwaysOpenModeAndTargetScopeRoundTrip() throws Exception {
        sunroof.edit().putBoolean("sunroof_auto_enabled", true).putInt("sunroof_auto_mode", 3)
                .putInt("sunroof_auto_target_scope", 0).commit();
        JSONObject backup = AutomationBackup.export(main, sunroof);
        assertEquals(3, backup.getJSONObject("sunroof").getInt("mode"));
        assertEquals(0, backup.getJSONObject("sunroof").getInt("targetScope"));

        FakePrefs newSunroof = new FakePrefs();
        AutomationBackup.Result result = AutomationBackup.apply(backup, new FakePrefs(), newSunroof);
        assertTrue(result.errors.toString(), result.ok()); // "daima açık" artık geçerli mod
        assertEquals(3, newSunroof.getInt("sunroof_auto_mode", -1));
        assertEquals(0, newSunroof.getInt("sunroof_auto_target_scope", -1));
    }

    @Test
    public void targetScopeDefaultsToSunroofAndShadeAndRejectsOutOfRange() throws Exception {
        assertEquals(1, AutomationBackup.export(main, sunroof).getJSONObject("sunroof").getInt("targetScope"));
        assertTrue(AutomationBackup.apply(new JSONObject("{\"version\":1,\"sunroof\":{\"targetScope\":2}}"), main, sunroof)
                .errors.contains("sunroof.targetScope"));
        assertTrue(AutomationBackup.apply(new JSONObject("{\"version\":1,\"sunroof\":{\"mode\":4}}"), main, sunroof)
                .errors.contains("sunroof.mode"));
    }

    @Test
    public void childLockIsBackedUpSeparatelyFromTheDisabledAssists() throws Exception {
        assertFalse(AutomationBackup.export(main, sunroof).getJSONObject("memory").getBoolean("childLock"));

        main.edit().putInt("childLockSetting", 1).commit();
        JSONObject backup = AutomationBackup.export(main, sunroof);
        assertTrue(backup.getJSONObject("memory").getBoolean("childLock"));
        assertFalse(backup.getJSONObject("memory").getJSONObject("assistsOff").has("childLock"));

        FakePrefs other = new FakePrefs();
        assertTrue(AutomationBackup.apply(backup, other, new FakePrefs()).ok()); // onay istemez: kilit güvenlik kapatmaz
        assertEquals(1, other.getInt("childLockSetting", -9));
    }

    @Test
    public void legacyChildLockFlagCountsAndIsClearedWhenTurningTheLockOff() throws Exception {
        main.edit().putBoolean("childWindowLockAutoEnable", true).commit(); // eski sürümün bayrağı
        assertTrue(AutomationBackup.export(main, sunroof).getJSONObject("memory").getBoolean("childLock"));

        assertTrue(AutomationBackup.apply(new JSONObject("{\"version\":1,\"memory\":{\"childLock\":false}}"), main, sunroof).ok());
        assertEquals(-1, main.getInt("childLockSetting", -9));
        assertFalse(main.getBoolean("childWindowLockAutoEnable", true)); // motor ikisini de sayar: eski bayrak da kapanır
        assertFalse(AutomationBackup.export(main, sunroof).getJSONObject("memory").getBoolean("childLock"));

        assertFalse(AutomationBackup.apply(new JSONObject("{\"version\":1,\"memory\":{\"childLock\":\"yes\"}}"), main, sunroof).ok());
    }
}
