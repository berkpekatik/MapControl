package com.mapcontrol.trip;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * MapControl'ün kullanıcı tercihlerinin yedeği ve geri yüklemesi ({@link AutomationBackup} belgesindeki {@code app}
 * bölümü). Yalnızca aşağıdaki izin listesindeki anahtarlar yedeklenir; gizli veri (oturum, eşleşme anahtarı, araç
 * kimliği, yüz profilleri), çalışma durumu ve yasal onaylar bilerek listede yoktur.
 *
 * <pre>
 * "app": {"screen":{"w":1920,"h":720},
 *   "power":{"mode":2,"bootAutoStart":true,...}, "projection":{...}, "floating":{...}, "clusterOverlay":{...},
 *   "launcher":{...}, "welcome":{...}, "look":{...}, "equalizer":{...}, "camera":{...}, "sunroofOverlay":{...},
 *   "split":{...}, "vehicleModel":{...}, "dms":{"enabled":true}}
 * </pre>
 *
 * <b>Dışa aktarma.</b> Yalnızca aracın tercih dosyasında gerçekten yazılı olan alanlar çıkar; hiç dokunulmamış bir
 * ayar belgeye girmez ve geri yüklemede varsayılanında kalır.
 *
 * <b>Geri yükleme.</b> Önce her alan türüne ve aralığına göre doğrulanır; tek alan geçersizse hiçbiri yazılmaz.
 * Doğrulanan ama bu araçta uygulanamayacak alanlar atlanır ve {@link Plan#skipped} içinde bildirilir:
 * <ul>
 *   <li>Ekrana bağlı alanlar (pencere konumları, kümedeki kart konumu/ölçeği): belgedeki ekran çözünürlüğü bu
 *       aracınkiyle aynı değilse.</li>
 *   <li>Uygulama paketi içeren alanlar: paket bu araçta yüklü değilse.</li>
 * </ul>
 * Belgede olmayan bölüm ya da alan olduğu gibi kalır.
 */
public final class AppBackup {

    public static final String FILE_MAIN = "MapControlPrefs";
    public static final String FILE_CAMERA = "camera360";
    public static final String FILE_SPLIT = "mapcontrol_split_screen";
    public static final String FILE_LOCALE = "mapcontrol_locale";
    public static final String FILE_SUNROOF = "sunroof_automation_prefs";

    /** Aracın ortamı: tercih dosyaları, yüklü paketler, ekran. */
    public interface Env {
        SharedPreferences prefs(String file);

        boolean isInstalled(String packageName);

        int screenWidth();

        int screenHeight();
    }

    private enum Kind { BOOL, INT, ENUM, INT_SET, PACKAGE, LAUNCHER_ACTION, TEXT, PACKAGE_LIST, INT_LIST }

    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    private static final Pattern ACTION = Pattern.compile("action:[A-Za-z0-9_:.\\-]{1,90}");
    private static final Pattern IDENT = Pattern.compile("[A-Za-z0-9_.]{0,100}");
    private static final Pattern BANDS = Pattern.compile("-?[0-9]{1,5}(,-?[0-9]{1,5}){0,31}");

    private static final int MAX_QUICK_SLOTS = 35;

    private static final class Field {
        final String section;
        final String name;
        final String file;
        final String key;
        final Kind kind;
        final int min;
        final int max;
        final String[] values;
        final int maxLen;
        final boolean screenBound;

        Field(String section, String name, String file, String key, Kind kind, int min, int max, String[] values,
              int maxLen, boolean screenBound) {
            this.section = section;
            this.name = name;
            this.file = file;
            this.key = key;
            this.kind = kind;
            this.min = min;
            this.max = max;
            this.values = values;
            this.maxLen = maxLen;
            this.screenBound = screenBound;
        }

        String path() {
            return section + "." + name;
        }
    }

    private static final List<Field> FIELDS = new ArrayList<>();
    private static final Map<String, Boolean> SECTIONS = new LinkedHashMap<>();

    private static void add(Field f) {
        FIELDS.add(f);
        SECTIONS.put(f.section, true);
    }

    private static void bool(String s, String n, String file, String key) {
        add(new Field(s, n, file, key, Kind.BOOL, 0, 0, null, 0, false));
    }

    private static void boolScreen(String s, String n, String file, String key) {
        add(new Field(s, n, file, key, Kind.BOOL, 0, 0, null, 0, true));
    }

    private static void integer(String s, String n, String file, String key, int min, int max) {
        add(new Field(s, n, file, key, Kind.INT, min, max, null, 0, false));
    }

    private static void integerScreen(String s, String n, String file, String key, int min, int max) {
        add(new Field(s, n, file, key, Kind.INT, min, max, null, 0, true));
    }

    private static void oneOf(String s, String n, String file, String key, String... values) {
        add(new Field(s, n, file, key, Kind.ENUM, 0, 0, values, 0, false));
    }

    private static void kind(String s, String n, String file, String key, Kind k, int maxLen) {
        add(new Field(s, n, file, key, k, 0, 0, null, maxLen, false));
    }

    static {
        integer("power", "mode", FILE_MAIN, "powerModeSetting", 0, 2);
        bool("power", "bootAutoStart", FILE_MAIN, "bootAutoStart");
        bool("power", "bootAutoLaunchUi", FILE_MAIN, "bootAutoLaunchUi");
        bool("power", "launcherStartEarliest", FILE_MAIN, "launcherStartEarliest");
        bool("power", "homeKeyMapControl", FILE_MAIN, "homeKeyMapControl");
        integer("power", "radioCommandMode", FILE_MAIN, "radioCommandMode", 0, 2);
        bool("power", "autoCloseOnPowerOff", FILE_MAIN, "autoCloseOnPowerOff");
        bool("power", "autoLocationSaveOnPowerOff", FILE_MAIN, "autoLocationSaveOnPowerOff");

        bool("projection", "mapControlKey", FILE_MAIN, "mapControlKeyEnabled");
        bool("projection", "googleAutoProjection", FILE_MAIN, "google_maps_notification_auto_projection");
        bool("projection", "googleClusterOverlay", FILE_MAIN, "google_maps_cluster_nav_overlay");
        bool("projection", "yandexClusterOverlay", FILE_MAIN, "yandex_cluster_nav_overlay");
        kind("projection", "targetPackage", FILE_MAIN, "targetPackage", Kind.PACKAGE, 255);

        bool("floating", "backButton", FILE_MAIN, "floatingBackButtonEnabled");
        bool("floating", "projectionControls", FILE_MAIN, "floatingProjectionControlsEnabled");
        bool("floating", "quickActions", FILE_MAIN, "floatingQuickActionsEnabled");
        boolScreen("floating", "backPosSaved", FILE_MAIN, "floatingBackPosSaved");
        integerScreen("floating", "backX", FILE_MAIN, "floatingBackPosX", -20000, 20000);
        integerScreen("floating", "backY", FILE_MAIN, "floatingBackPosY", -20000, 20000);
        boolScreen("floating", "projectionPosSaved", FILE_MAIN, "floatingProjectionBarPosSaved");
        integerScreen("floating", "projectionX", FILE_MAIN, "floatingProjectionBarPosX", -20000, 20000);
        integerScreen("floating", "projectionY", FILE_MAIN, "floatingProjectionBarPosY", -20000, 20000);
        boolScreen("floating", "quickPosSaved", FILE_MAIN, "floatingQuickBarPosSaved");
        integerScreen("floating", "quickX", FILE_MAIN, "floatingQuickBarPosX", -20000, 20000);
        integerScreen("floating", "quickY", FILE_MAIN, "floatingQuickBarPosY", -20000, 20000);

        // Kümedeki yol tarifi / varış kartı: konum dp, ölçek yüzde (50–150).
        for (String app : new String[]{"google", "yandex"}) {
            for (String card : new String[]{"maneuver", "eta"}) {
                String base = app + "_cluster_nav_" + card;
                String name = app + Character.toUpperCase(card.charAt(0)) + card.substring(1);
                integerScreen("clusterOverlay", name + "X", FILE_MAIN, base + "_offset_x_dp", -2000, 2000);
                integerScreen("clusterOverlay", name + "Y", FILE_MAIN, base + "_offset_y_dp", -2000, 2000);
                integerScreen("clusterOverlay", name + "Scale", FILE_MAIN, base + "_scale_pct", 50, 150);
            }
        }

        kind("launcher", "swipeUp", FILE_MAIN, "launcher_swipe_up", Kind.LAUNCHER_ACTION, 100);
        kind("launcher", "swipeDown", FILE_MAIN, "launcher_swipe_down", Kind.LAUNCHER_ACTION, 100);
        kind("launcher", "swipeLeft", FILE_MAIN, "launcher_swipe_left", Kind.LAUNCHER_ACTION, 100);
        kind("launcher", "swipeRight", FILE_MAIN, "launcher_swipe_right", Kind.LAUNCHER_ACTION, 100);
        kind("launcher", "quickApps", FILE_MAIN, "launcher_quick_apps", Kind.PACKAGE_LIST, MAX_QUICK_SLOTS * 256);
        bool("launcher", "quickAppsShowAll", FILE_MAIN, "launcher_quick_apps_show_all");
        integer("launcher", "quickAppsVisibleCount", FILE_MAIN, "launcher_quick_apps_visible_count", 0, MAX_QUICK_SLOTS);

        oneOf("welcome", "designId", FILE_MAIN, "welcomeDesignId", "off", "signature", "winter", "hike", "prestige");
        kind("welcome", "customText", FILE_MAIN, "welcomeCustomText", Kind.TEXT, 500);
        kind("welcome", "ttsText", FILE_MAIN, "welcomeTtsText", Kind.TEXT, 1000);
        kind("welcome", "ttsVoice", FILE_MAIN, "welcomeTtsVoice", Kind.TEXT, 100);
        bool("welcome", "autoPlay", FILE_MAIN, "welcomeAudioAutoPlay");

        // AppCompatDelegate: -1 sistemi izle, 1 aydınlık, 2 karanlık.
        add(new Field("look", "nightMode", FILE_MAIN, "nightModeSetting", Kind.INT_SET, 0, 0, new String[]{"-1", "1", "2"}, 0, false));
        oneOf("look", "locale", FILE_LOCALE, "locale_tag", "tr", "en");

        bool("equalizer", "enabled", FILE_MAIN, "equalizerEnabled");
        oneOf("equalizer", "preset", FILE_MAIN, "equalizerPreset", "flat", "bass", "vocal", "treble", "custom");
        integer("equalizer", "gainMb", FILE_MAIN, "equalizerGainMb", 0, 1500);
        kind("equalizer", "bands", FILE_MAIN, "equalizerBands", Kind.INT_LIST, 300);

        bool("camera", "autoInternal", FILE_CAMERA, "autoInternal");
        bool("camera", "autoUsb", FILE_CAMERA, "autoUsb");
        integer("camera", "resIndex", FILE_CAMERA, "resIndex", 0, 2);

        bool("sunroofOverlay", "enabled", FILE_SUNROOF, "sunroof_gesture_overlay_enabled");
        integer("sunroofOverlay", "mode", FILE_SUNROOF, "sunroof_gesture_overlay_mode", 0, 2);
        bool("sunroofOverlay", "controlShade", FILE_SUNROOF, "sunroof_auto_control_shade");

        kind("split", "pkg1", FILE_SPLIT, "split_pkg1", Kind.PACKAGE, 255);
        kind("split", "pkg2", FILE_SPLIT, "split_pkg2", Kind.PACKAGE, 255);
        oneOf("split", "ratio", FILE_SPLIT, "split_ratio", "left_third", "half", "right_third");
        oneOf("split", "axis", FILE_SPLIT, "split_axis", "auto", "side", "stack");

        bool("vehicleModel", "autoDetect", FILE_MAIN, "vehicleModelUseAutoDetection");
        kind("vehicleModel", "package", FILE_MAIN, "vehicleModelMaterialPackage", Kind.PACKAGE, 255);
        kind("vehicleModel", "drawable", FILE_MAIN, "vehicleModelMaterialDrawable", Kind.TEXT, 100);
        kind("vehicleModel", "label", FILE_MAIN, "vehicleModelMaterialLabel", Kind.TEXT, 100);

        bool("dms", "enabled", FILE_MAIN, "dmsFaceRecognitionEnabled");
    }

    private AppBackup() {
    }

    // ---- dışa aktarma ---------------------------------------------------

    /** Yazılı tercihleri {@code app} bölümü olarak verir; hiçbiri yoksa yalnızca {@code screen} içerir. */
    public static JSONObject export(Env env) throws JSONException {
        JSONObject app = new JSONObject();
        app.put("screen", new JSONObject().put("w", env.screenWidth()).put("h", env.screenHeight()));
        for (Field f : FIELDS) {
            SharedPreferences sp = env.prefs(f.file);
            if (sp == null || !sp.contains(f.key)) {
                continue;
            }
            Object value;
            try {
                value = read(sp, f);
            } catch (ClassCastException e) {
                continue; // beklenmeyen türde yazılmış: yedeğe alma
            }
            // Geri yüklemede reddedilecek bir değer (ör. aralık dışı) tüm yedeği geri yüklenemez yapardı: yedeğe alma.
            if (value == null || validate(f, value) == null) {
                continue;
            }
            JSONObject section = app.optJSONObject(f.section);
            if (section == null) {
                section = new JSONObject();
                app.put(f.section, section);
            }
            section.put(f.name, value);
        }
        return app;
    }

    private static Object read(SharedPreferences sp, Field f) {
        switch (f.kind) {
            case BOOL:
                return sp.getBoolean(f.key, false);
            case INT:
            case INT_SET:
                return sp.getInt(f.key, 0);
            default:
                String s = sp.getString(f.key, null);
                return s == null || s.length() > (f.maxLen > 0 ? f.maxLen : 255) ? null : s;
        }
    }

    /** Yedeğe girebilecek ve araçta yazılı tercih sayısı. 0: araç hiç yapılandırılmamış (fabrika durumu). */
    public static int configuredCount(Env env) {
        int n = 0;
        for (Field f : FIELDS) {
            SharedPreferences sp = env.prefs(f.file);
            if (sp != null && sp.contains(f.key)) {
                n++;
            }
        }
        return n;
    }

    // ---- geri yükleme ---------------------------------------------------

    /** Doğrulanmış geri yükleme: {@link #commit} yazar. {@code errors} boş değilse yazılmaz. */
    public static final class Plan {
        public final List<String> errors = new ArrayList<>();
        /** Bu araçta uygulanmayan alanlar ({@code launcher.swipeUp}, {@code launcher.quickApps[3]}, {@code screen}). */
        public final List<String> skipped = new ArrayList<>();
        private final List<Object[]> writes = new ArrayList<>(); // {Field, value}
        private Env env;

        public boolean ok() {
            return errors.isEmpty();
        }

        /** Doğrulanan alanları yazar. Tercih dosyası başına tek işlemde. */
        public void commit() {
            if (!errors.isEmpty()) {
                return;
            }
            Map<String, SharedPreferences.Editor> editors = new LinkedHashMap<>();
            for (Object[] w : writes) {
                Field f = (Field) w[0];
                SharedPreferences.Editor ed = editors.get(f.file);
                if (ed == null) {
                    ed = env.prefs(f.file).edit();
                    editors.put(f.file, ed);
                }
                Object v = w[1];
                if (v instanceof Boolean) {
                    ed.putBoolean(f.key, (Boolean) v);
                } else if (v instanceof Integer) {
                    ed.putInt(f.key, (Integer) v);
                } else {
                    ed.putString(f.key, (String) v);
                }
            }
            for (SharedPreferences.Editor ed : editors.values()) {
                ed.commit();
            }
        }
    }

    /** {@code app} bölümünü doğrular. Hatalar {@code app.bölüm.alan} biçiminde {@link Plan#errors}'a yazılır. */
    public static Plan plan(JSONObject app, Env env) {
        Plan plan = new Plan();
        plan.env = env;

        boolean sameScreen = false;
        Object screen = app.opt("screen");
        if (app.has("screen")) {
            if (screen instanceof JSONObject) {
                JSONObject s = (JSONObject) screen;
                int w = s.optInt("w", -1);
                int h = s.optInt("h", -1);
                if (w <= 0 || h <= 0 || !(s.opt("w") instanceof Integer) || !(s.opt("h") instanceof Integer)) {
                    plan.errors.add("app.screen");
                } else {
                    sameScreen = (w == env.screenWidth() && h == env.screenHeight())
                            || (w == env.screenHeight() && h == env.screenWidth());
                }
            } else {
                plan.errors.add("app.screen");
            }
        }

        boolean screenSkipped = false;
        List<String> packageSkipped = new ArrayList<>();
        Iterator<String> sections = app.keys();
        while (sections.hasNext()) {
            String sectionName = sections.next();
            if ("screen".equals(sectionName)) {
                continue;
            }
            if (!SECTIONS.containsKey(sectionName)) {
                plan.errors.add("app." + sectionName);
                continue;
            }
            JSONObject section = app.optJSONObject(sectionName);
            if (section == null) {
                plan.errors.add("app." + sectionName);
                continue;
            }
            Iterator<String> names = section.keys();
            while (names.hasNext()) {
                String name = names.next();
                Field f = find(sectionName, name);
                if (f == null) {
                    plan.errors.add("app." + sectionName + "." + name);
                    continue;
                }
                Object raw = section.opt(name);
                Object value = validate(f, raw);
                if (value == null) {
                    plan.errors.add("app." + f.path());
                    continue;
                }
                if (f.screenBound && !sameScreen) {
                    screenSkipped = true;
                    continue;
                }
                if (f.kind == Kind.PACKAGE) {
                    String pkg = (String) value;
                    if (!pkg.isEmpty() && !env.isInstalled(pkg)) {
                        plan.skipped.add(f.path());
                        packageSkipped.add(f.path());
                        continue;
                    }
                } else if (f.kind == Kind.LAUNCHER_ACTION) {
                    String v = (String) value;
                    if (!v.isEmpty() && !v.startsWith("action:") && !env.isInstalled(v)) {
                        plan.skipped.add(f.path());
                        continue;
                    }
                } else if (f.kind == Kind.PACKAGE_LIST) {
                    value = filterInstalled((String) value, f, env, plan.skipped);
                }
                plan.writes.add(new Object[]{f, value});
            }
        }
        // Araç modeli bir bütündür: paketi uygulanmadıysa görsel ve etiketi de uygulanmaz.
        if (packageSkipped.contains("vehicleModel.package")) {
            for (int i = plan.writes.size() - 1; i >= 0; i--) {
                Field f = (Field) plan.writes.get(i)[0];
                if ("vehicleModel".equals(f.section) && ("drawable".equals(f.name) || "label".equals(f.name))) {
                    plan.skipped.add(f.path());
                    plan.writes.remove(i);
                }
            }
        }
        if (screenSkipped) {
            plan.skipped.add("screen");
        }
        java.util.Collections.sort(plan.skipped); // JSON anahtar sırasından bağımsız, öngörülebilir çıktı
        return plan;
    }

    private static Field find(String section, String name) {
        for (Field f : FIELDS) {
            if (f.section.equals(section) && f.name.equals(name)) {
                return f;
            }
        }
        return null;
    }

    /** Geçerliyse yazılacak değer (Boolean, Integer veya String); değilse null. */
    private static Object validate(Field f, Object raw) {
        switch (f.kind) {
            case BOOL:
                return raw instanceof Boolean ? raw : null;
            case INT:
                if (raw instanceof Integer || raw instanceof Long) {
                    long n = ((Number) raw).longValue();
                    return n >= f.min && n <= f.max ? (Object) (int) n : null;
                }
                return null;
            case INT_SET:
                if (raw instanceof Integer || raw instanceof Long) {
                    String s = String.valueOf(((Number) raw).longValue());
                    for (String ok : f.values) {
                        if (ok.equals(s)) {
                            return Integer.parseInt(s);
                        }
                    }
                }
                return null;
            case ENUM:
                if (raw instanceof String) {
                    for (String ok : f.values) {
                        if (ok.equals(raw)) {
                            return raw;
                        }
                    }
                }
                return null;
            case PACKAGE:
                return raw instanceof String && ((String) raw).length() <= f.maxLen
                        && (((String) raw).isEmpty() || PACKAGE.matcher((String) raw).matches()) ? raw : null;
            case LAUNCHER_ACTION:
                return raw instanceof String && ((String) raw).length() <= f.maxLen
                        && (((String) raw).isEmpty() || ACTION.matcher((String) raw).matches()
                        || PACKAGE.matcher((String) raw).matches()) ? raw : null;
            case TEXT:
                return raw instanceof String && ((String) raw).length() <= f.maxLen && cleanText((String) raw)
                        && (!"drawable".equals(f.name) || IDENT.matcher((String) raw).matches())
                        ? raw : null;
            case PACKAGE_LIST:
                return raw instanceof String && ((String) raw).length() <= f.maxLen && validSlots((String) raw) ? raw : null;
            case INT_LIST:
                return raw instanceof String && (((String) raw).isEmpty() || BANDS.matcher((String) raw).matches())
                        && ((String) raw).length() <= f.maxLen ? raw : null;
            default:
                return null;
        }
    }

    /** Satır sonu ve sekme dışında denetim karakteri taşımayan metin. */
    private static boolean cleanText(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\t') {
                return false;
            }
        }
        return true;
    }

    private static boolean validSlots(String raw) {
        String[] slots = raw.split("\\|", -1);
        if (slots.length > MAX_QUICK_SLOTS) {
            return false;
        }
        for (String s : slots) {
            if (!s.isEmpty() && (s.length() > 255 || !PACKAGE.matcher(s).matches())) {
                return false;
            }
        }
        return true;
    }

    /** Yüklü olmayan paketin yuvasını boşaltır (yuva sırası bozulmaz); atlananları bildirir. */
    private static String filterInstalled(String raw, Field f, Env env, List<String> skipped) {
        String[] slots = raw.split("\\|", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < slots.length; i++) {
            String s = slots[i];
            if (!s.isEmpty() && !env.isInstalled(s)) {
                skipped.add(f.path() + "[" + i + "]");
                s = "";
            }
            if (i > 0) {
                out.append('|');
            }
            out.append(s);
        }
        return out.toString();
    }
}
