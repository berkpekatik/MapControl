package com.mapcontrol.trip;

import android.content.SharedPreferences;

import com.mapcontrol.vehicle.ClimateAutomationStore;
import com.mapcontrol.vehicle.SunroofAutomationEngine;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Cam tavan, klima otomasyonu ve hafıza modu (sürüş modu + kapatılan sürücü yardımları) ayarlarının
 * telefona yedeği ve telefondan geri yüklemesi.
 * Telefon {@code GET/PUT /automation} ile bu belgeyi okur ve yazar; yedek hesapta saklanır.
 *
 * <pre>
 * {"version":1,
 *  "sunroof":{"enabled":false,"mode":0,"sensitivity":1,"targetScope":1,"rainGuard":true,"speedGuard":true,"closeAtNight":false},
 *  "climate":{"enabled":false,"rules":{"driver_heat":{"enabled":false,"delayMinutes":0,"compare":0,"tempC":6,"level":2}, ...}},
 *  "memory":{"driveMode":-1,"assistsOff":{"iss":false,"spdLimit":false,"ldw":false,"ldp":false,"fcw":false,"aeb":false,"cpd":false},"childLock":false}}
 * </pre>
 *
 * Belgeye isteğe bağlı bir {@code "app"} bölümü de girer (MapControl tercihleri, bkz. {@link AppBackup}); {@code version}
 * 1 kalır, bilmeyen eski araç bölümü yok sayar.
 *
 * {@code assistsOff.x = true}: o yardım her kontak açılışında kapatılır (Hafıza Modu ekranındaki "Kapalı" kartı).
 * FCW ve AEB gibi güvenlik özelliklerinin yeni kapatılması aracın kendi ekranında sorumluluk onayı ister;
 * burada karşılığı belgedeki {@code safetyAck: true}tur, yoksa reddedilir ({@code safety.ack_required}).
 * Yalnızca kullanıcının ekranlarda ayarladığı değerler yedeklenir. Sahte yağmur, bekleme süresi gibi
 * test/iç değerler ve arayüzde gizli {@code climate} kuralı dışarıda kalır. Geri yükleme önce her şeyi
 * doğrular; tek bir alan geçersizse hiçbir şey yazılmaz.
 */
public final class AutomationBackup {

    public static final int VERSION = 1;

    /** Hafıza Modu'nda seçilebilen sürüş modu değerleri; -1: hiçbiri. */
    private static final int[] DRIVE_MODES = {-1, 0, 1, 2, 3, 4, 5, 7};

    /** Yardım adı, kayıt anahtarı ve "kapalı" değeri (ISS 0, diğerleri 2); -1: ayarlanmadı. */
    private static final String[][] ASSISTS = {
            {"iss", "issSetting", "0"},
            {"spdLimit", "spdLimitSetting", "2"},
            {"ldw", "ldwSetting", "2"},
            {"ldp", "ldpSetting", "2"},
            {"fcw", "fcwSetting", "2"},
            {"aeb", "aebSetting", "2"},
            {"cpd", "cpdSetting", "2"},
    };

    /**
     * Çocuk cam kilidi: her kontak açılışında kilitlenir ({@code childLockSetting == 1}; eski sürümün
     * {@code childWindowLockAutoEnable} bayrağı da sayılır). Diğer yardımların tersine bir güvenlik
     * özelliğini kapatmaz, bir kilidi açar; bu yüzden {@code assistsOff} içinde değildir ve onay istemez.
     */
    private static boolean isChildLockOn(SharedPreferences main) {
        return main.getInt("childLockSetting", main.getBoolean("childWindowLockAutoEnable", false) ? 1 : -1) == 1;
    }

    /** Kapatılması araçta sorumluluk onayı isteyen güvenlik özellikleri. */
    private static boolean isSafetyCritical(String name) {
        return "fcw".equals(name) || "aeb".equals(name);
    }

    private AutomationBackup() {
    }

    /** Geri yükleme sonucu: boş liste = uygulandı. */
    public static final class Result {
        public final List<String> errors;

        /** Doğrulanan ama bu araçta uygulanmayan {@code app} alanları (yüklü olmayan uygulama, farklı ekran). */
        public final List<String> skipped;

        Result(List<String> errors) {
            this(errors, new ArrayList<>());
        }

        Result(List<String> errors, List<String> skipped) {
            this.errors = errors;
            this.skipped = skipped;
        }

        public boolean ok() {
            return errors.isEmpty();
        }
    }

    public static JSONObject export(SharedPreferences main, SharedPreferences sunroof) throws JSONException {
        return export(main, sunroof, null);
    }

    /**
     * [env] verilirse belgeye {@code app} bölümü (MapControl tercihleri, bkz. {@link AppBackup}) de girer.
     * Bölüm ekleyici olduğu için {@code version} 1 kalır: bilmeyen eski araç ve telefon onu yok sayar.
     */
    public static JSONObject export(SharedPreferences main, SharedPreferences sunroof, AppBackup.Env env)
            throws JSONException {
        JSONObject sun = new JSONObject()
                .put("enabled", sunroof.getBoolean(SunroofAutomationEngine.KEY_ENABLED, false))
                .put("mode", sunroof.getInt(SunroofAutomationEngine.KEY_MODE, SunroofAutomationEngine.MODE_SMART_COMFORT))
                .put("sensitivity", sunroof.getInt(SunroofAutomationEngine.KEY_SENSITIVITY,
                        SunroofAutomationEngine.SENSITIVITY_BALANCED))
                .put("targetScope", sunroof.getInt(SunroofAutomationEngine.KEY_TARGET_SCOPE,
                        SunroofAutomationEngine.TARGET_SUNROOF_AND_SHADE))
                .put("rainGuard", sunroof.getBoolean(SunroofAutomationEngine.KEY_RAIN_GUARD, true))
                .put("speedGuard", sunroof.getBoolean(SunroofAutomationEngine.KEY_SPEED_GUARD, true))
                .put("closeAtNight", sunroof.getBoolean(SunroofAutomationEngine.KEY_CLOSE_AT_NIGHT, false));

        JSONObject rules = new JSONObject();
        for (String id : ClimateAutomationStore.ALL_IDS) {
            if (ClimateAutomationStore.isHidden(id)) {
                continue;
            }
            ClimateAutomationStore.Rule r = ClimateAutomationStore.getRule(main, id);
            rules.put(id, new JSONObject()
                    .put("enabled", r.enabled)
                    .put("delayMinutes", r.delayMinutes)
                    .put("compare", r.compare)
                    .put("tempC", r.tempC)
                    .put("level", r.level));
        }
        JSONObject climate = new JSONObject()
                .put("enabled", ClimateAutomationStore.isMasterEnabled(main))
                .put("rules", rules);
        JSONObject off = new JSONObject();
        for (String[] a : ASSISTS) {
            off.put(a[0], main.getInt(a[1], -1) == Integer.parseInt(a[2]));
        }
        JSONObject memory = new JSONObject()
                .put("driveMode", main.getInt("driveModeSetting", -1))
                .put("assistsOff", off)
                .put("childLock", isChildLockOn(main));
        JSONObject doc = new JSONObject().put("version", VERSION).put("sunroof", sun).put("climate", climate)
                .put("memory", memory);
        if (env != null) {
            doc.put("app", AppBackup.export(env));
        }
        return doc;
    }

    /**
     * Araçta yedeğe girecek hiçbir ayar yazılı değil mi (fabrika durumu veya yeni kurulum)? Telefon bunu, hesapta
     * yedek varsa "geri yükleyeyim mi?" diye sormak için kullanır. Tek bir ayar bile yazılıysa false: yanlış
     * olumsuz, yanlış olumludan iyidir (sorulmaz, ama dolu bir aracın üzerine de yazılmaz).
     */
    public static boolean isPristine(SharedPreferences main, SharedPreferences sunroof, AppBackup.Env env) {
        for (String key : main.getAll().keySet()) {
            if (key.startsWith("climateAuto") || key.equals("driveModeSetting") || key.equals("childLockSetting")
                    || key.equals("childWindowLockAutoEnable")) {
                return false;
            }
        }
        for (String[] a : ASSISTS) {
            if (main.contains(a[1])) {
                return false;
            }
        }
        for (String key : sunroof.getAll().keySet()) {
            if (key.startsWith("sunroof_auto_") && !key.equals("sunroof_auto_simulated_rain")
                    && !key.equals("sunroof_auto_cooldown_sec") && !key.equals("sunroof_auto_speed_limit")) {
                return false;
            }
        }
        return env == null || AppBackup.configuredCount(env) == 0;
    }

    /**
     * Belgeyi doğrular ve geçerliyse yazar. Belgede olmayan bölüm/alan olduğu gibi kalır.
     * Isıtma ile havalandırma aynı koltukta aynı anda tetiklenecek biçimde çakışırsa reddedilir.
     */
    public static Result apply(JSONObject doc, SharedPreferences main, SharedPreferences sunroof) {
        return apply(doc, main, sunroof, null);
    }

    /** [env] verilirse belgedeki {@code app} bölümü de doğrulanır ve yazılır; verilmezse yok sayılır. */
    public static Result apply(JSONObject doc, SharedPreferences main, SharedPreferences sunroof, AppBackup.Env env) {
        List<String> errors = new ArrayList<>();
        if (doc.optInt("version", -1) != VERSION) {
            errors.add("version");
            return new Result(errors);
        }
        JSONObject sun = doc.optJSONObject("sunroof");
        JSONObject climate = doc.optJSONObject("climate");
        if (doc.has("sunroof") && sun == null) {
            errors.add("sunroof");
        }
        if (doc.has("climate") && climate == null) {
            errors.add("climate");
        }

        SharedPreferences.Editor sunEdit = sunroof.edit();
        if (sun != null) {
            readBool(sun, "enabled", SunroofAutomationEngine.KEY_ENABLED, "sunroof.enabled", sunEdit, errors);
            // 0 akıllı konfor, 1 yalnızca gölgede aç, 2 açık hava, 3 daima açık.
            readInt(sun, "mode", SunroofAutomationEngine.KEY_MODE, 0, SunroofAutomationEngine.MODE_ALWAYS_OPEN,
                    "sunroof.mode", sunEdit, errors);
            // 0 yalnızca perde, 1 tavan + perde.
            readInt(sun, "targetScope", SunroofAutomationEngine.KEY_TARGET_SCOPE, 0, 1, "sunroof.targetScope", sunEdit, errors);
            readInt(sun, "sensitivity", SunroofAutomationEngine.KEY_SENSITIVITY, 0, 2, "sunroof.sensitivity", sunEdit, errors);
            readBool(sun, "rainGuard", SunroofAutomationEngine.KEY_RAIN_GUARD, "sunroof.rainGuard", sunEdit, errors);
            readBool(sun, "speedGuard", SunroofAutomationEngine.KEY_SPEED_GUARD, "sunroof.speedGuard", sunEdit, errors);
            readBool(sun, "closeAtNight", SunroofAutomationEngine.KEY_CLOSE_AT_NIGHT, "sunroof.closeAtNight", sunEdit, errors);
        }

        SharedPreferences.Editor mainEdit = main.edit();
        Boolean masterValue = null;
        List<String> ruleIds = new ArrayList<>();
        List<ClimateAutomationStore.Rule> parsedRules = new ArrayList<>();
        if (climate != null) {
            if (climate.has("enabled")) {
                Object v = climate.opt("enabled");
                if (v instanceof Boolean) {
                    masterValue = (Boolean) v;
                } else {
                    errors.add("climate.enabled");
                }
            }
            JSONObject rules = climate.optJSONObject("rules");
            if (climate.has("rules") && rules == null) {
                errors.add("climate.rules");
            }
            if (rules != null) {
                Iterator<String> keys = rules.keys();
                while (keys.hasNext()) {
                    String id = keys.next();
                    if (!isKnownRule(id)) {
                        errors.add("climate.rules." + id);
                        continue;
                    }
                    ClimateAutomationStore.Rule rule = parseRule(rules.optJSONObject(id), id, main, errors);
                    if (rule != null) {
                        ruleIds.add(id);
                        parsedRules.add(rule);
                    }
                }
            }
        }
        checkConflicts(ruleIds, parsedRules, main, errors);

        JSONObject memory = doc.optJSONObject("memory");
        if (doc.has("memory") && memory == null) {
            errors.add("memory");
        }
        Integer driveMode = null;
        Boolean childLock = null;
        List<String[]> assistChanges = new ArrayList<>();
        List<Boolean> assistOff = new ArrayList<>();
        if (memory != null) {
            driveMode = driveModeIn(memory, errors);
            if (memory.has("childLock")) {
                Object v = memory.opt("childLock");
                if (v instanceof Boolean) {
                    childLock = (Boolean) v;
                } else {
                    errors.add("memory.childLock");
                }
            }
            JSONObject off = memory.optJSONObject("assistsOff");
            if (memory.has("assistsOff") && off == null) {
                errors.add("memory.assistsOff");
            }
            if (off != null) {
                Iterator<String> names = off.keys();
                while (names.hasNext()) {
                    String name = names.next();
                    String[] assist = assistNamed(name);
                    if (assist == null) {
                        errors.add("memory.assistsOff." + name);
                        continue;
                    }
                    Object v = off.opt(name);
                    if (!(v instanceof Boolean)) {
                        errors.add("memory.assistsOff." + name);
                        continue;
                    }
                    boolean disable = (Boolean) v;
                    boolean alreadyOff = main.getInt(assist[1], -1) == Integer.parseInt(assist[2]);
                    // Güvenlik özelliğini yeni kapatmak onay ister (aracın kendi ekranındaki uyarının karşılığı).
                    if (disable && !alreadyOff && isSafetyCritical(name) && !doc.optBoolean("safetyAck", false)) {
                        errors.add("safety.ack_required." + name);
                        continue;
                    }
                    assistChanges.add(assist);
                    assistOff.add(disable);
                }
            }
        }
        AppBackup.Plan appPlan = null;
        if (env != null && doc.has("app")) {
            JSONObject app = doc.optJSONObject("app");
            if (app == null) {
                errors.add("app");
            } else {
                appPlan = AppBackup.plan(app, env);
                errors.addAll(appPlan.errors);
            }
        }
        if (!errors.isEmpty()) {
            return new Result(errors);
        }

        sunEdit.commit();
        if (masterValue != null) {
            mainEdit.putBoolean("climateAutoEnabled", masterValue).commit();
        }
        for (int i = 0; i < ruleIds.size(); i++) {
            ClimateAutomationStore.putRule(main, ruleIds.get(i), parsedRules.get(i));
        }
        if (driveMode != null || childLock != null || !assistChanges.isEmpty()) {
            SharedPreferences.Editor memEdit = main.edit();
            if (childLock != null) {
                memEdit.putInt("childLockSetting", childLock ? 1 : -1);
                // Eski bayrak açık kalırsa kilit kapatılamaz (motor ikisini de sayar).
                memEdit.putBoolean("childWindowLockAutoEnable", false);
            }
            if (driveMode != null) {
                memEdit.putInt("driveModeSetting", driveMode);
            }
            for (int i = 0; i < assistChanges.size(); i++) {
                String[] a = assistChanges.get(i);
                memEdit.putInt(a[1], assistOff.get(i) ? Integer.parseInt(a[2]) : -1);
            }
            memEdit.commit();
        }
        if (appPlan != null) {
            appPlan.commit();
            return new Result(errors, appPlan.skipped);
        }
        return new Result(errors);
    }

    private static String[] assistNamed(String name) {
        for (String[] a : ASSISTS) {
            if (a[0].equals(name)) {
                return a;
            }
        }
        return null;
    }

    /** Alan yoksa null (değişmez); geçerli sürüş modu değilse hata ekler. */
    private static Integer driveModeIn(JSONObject memory, List<String> errors) {
        if (!memory.has("driveMode")) {
            return null;
        }
        Object v = memory.opt("driveMode");
        if (v instanceof Integer || v instanceof Long) {
            long n = ((Number) v).longValue();
            for (int mode : DRIVE_MODES) {
                if (mode == n) {
                    return mode;
                }
            }
        }
        errors.add("memory.driveMode");
        return null;
    }

    /** Gizli (arayüzde görünmeyen) kural yedeğe girmez; geri yüklemede de kabul edilmez. */
    private static boolean isKnownRule(String id) {
        for (String known : ClimateAutomationStore.ALL_IDS) {
            if (known.equals(id)) {
                return !ClimateAutomationStore.isHidden(id);
            }
        }
        return false;
    }

    private static ClimateAutomationStore.Rule parseRule(JSONObject o, String id, SharedPreferences main, List<String> errors) {
        String at = "climate.rules." + id;
        if (o == null) {
            errors.add(at);
            return null;
        }
        // Eksik alan şimdiki değerinde kalır.
        ClimateAutomationStore.Rule rule = ClimateAutomationStore.getRule(main, id);
        boolean ok = true;
        if (o.has("enabled")) {
            Object v = o.opt("enabled");
            if (v instanceof Boolean) {
                rule.enabled = (Boolean) v;
            } else {
                errors.add(at + ".enabled");
                ok = false;
            }
        }
        Integer delay = intIn(o, "delayMinutes", 0, ClimateAutomationStore.DELAY_MAX_MINUTES, at, errors);
        Integer cmp = intIn(o, "compare", ClimateAutomationStore.CMP_LT, ClimateAutomationStore.CMP_GTE, at, errors);
        Integer temp = intIn(o, "tempC", ClimateAutomationStore.TEMP_MIN, ClimateAutomationStore.TEMP_MAX, at, errors);
        Integer level = intIn(o, "level", ClimateAutomationStore.LEVEL_MIN, ClimateAutomationStore.LEVEL_MAX, at, errors);
        if (delay != null) {
            rule.delayMinutes = delay;
        }
        if (cmp != null) {
            rule.compare = cmp;
        }
        if (temp != null) {
            rule.tempC = temp;
        }
        if (level != null) {
            rule.level = level;
        }
        return ok ? rule : null;
    }

    /** Alan yoksa null (değişmez); varsa ve aralık dışı/tam sayı değilse hata ekler. */
    private static Integer intIn(JSONObject o, String field, int min, int max, String at, List<String> errors) {
        if (!o.has(field)) {
            return null;
        }
        Object v = o.opt(field);
        if (v instanceof Integer || v instanceof Long) {
            long n = ((Number) v).longValue();
            if (n >= min && n <= max) {
                return (int) n;
            }
        }
        errors.add(at + "." + field);
        return null;
    }

    private static void checkConflicts(List<String> ids, List<ClimateAutomationStore.Rule> incoming,
                                       SharedPreferences main, List<String> errors) {
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            String partner = ClimateAutomationStore.conflictPartnerOf(id);
            if (partner == null) {
                continue;
            }
            ClimateAutomationStore.Rule other;
            int j = ids.indexOf(partner);
            other = j >= 0 ? incoming.get(j) : ClimateAutomationStore.getRule(main, partner);
            ClimateAutomationStore.Rule mine = incoming.get(i);
            if (mine.enabled && other.enabled && ClimateAutomationStore.overlaps(mine, other)) {
                String key = "climate.conflict." + (id.compareTo(partner) < 0 ? id + "+" + partner : partner + "+" + id);
                if (!errors.contains(key)) {
                    errors.add(key);
                }
            }
        }
    }

    private static void readBool(JSONObject o, String field, String prefKey, String at,
                                 SharedPreferences.Editor edit, List<String> errors) {
        if (!o.has(field)) {
            return;
        }
        Object v = o.opt(field);
        if (v instanceof Boolean) {
            edit.putBoolean(prefKey, (Boolean) v);
        } else {
            errors.add(at);
        }
    }

    private static void readInt(JSONObject o, String field, String prefKey, int min, int max, String at,
                                SharedPreferences.Editor edit, List<String> errors) {
        Integer v = intIn(o, field, min, max, at.substring(0, at.lastIndexOf('.')), errors);
        if (v != null) {
            edit.putInt(prefKey, v);
        }
    }
}
