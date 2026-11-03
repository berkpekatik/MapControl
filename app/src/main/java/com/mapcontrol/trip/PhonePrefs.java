package com.mapcontrol.trip;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Telefonun {@code GET/PUT /prefs} ile gördüğü ve yazdığı MapControl boolean ayarları.
 * Android'e bağlı değildir.
 *
 * <p>Hepsi okunur. Yazılabilenler yalnızca aracın olay anında (kontak, açılış) okuduğu ayarlardır;
 * yüzen çubuk ve launcher anahtarları açılıp kapanınca ekranda bir şey başlatır/durdurur, ham değer
 * yazmak yetmez. Onay bayrakları aracın kendi ekranında verilir. "Konum kaydedildi" bayrakları
 * konumun kendisiyle birlikte aracın içindedir.
 */
public final class PhonePrefs {

    private PhonePrefs() {
    }

    /** Anahtar → araçtaki varsayılan ({@code SHARED_PREFERENCES_KEYS.md}). */
    public static final Map<String, Boolean> DEFAULTS;

    public static final Set<String> WRITABLE;

    static {
        Map<String, Boolean> d = new LinkedHashMap<>();
        d.put("disclaimerAccepted", false);
        d.put("appManagementDisclaimerAccepted", false);
        d.put("mapControlKeyEnabled", true);
        d.put("google_maps_notification_auto_projection", true);
        d.put("autoCloseOnPowerOff", true);
        d.put("autoLocationSaveOnPowerOff", false);
        d.put("bootAutoStart", true);
        d.put("bootAutoLaunchUi", true);
        d.put("welcomeAudioAutoPlay", false);
        d.put("floatingProjectionControlsEnabled", false);
        d.put("floatingProjectionBarPosSaved", false);
        d.put("floatingBackButtonEnabled", true);
        d.put("floatingBackPosSaved", false);
        d.put("floatingQuickActionsEnabled", false);
        d.put("floatingQuickBarPosSaved", false);
        d.put("launcherModeEnabled", false);
        DEFAULTS = Collections.unmodifiableMap(d);

        Set<String> w = new LinkedHashSet<>();
        w.add("mapControlKeyEnabled");
        w.add("google_maps_notification_auto_projection");
        w.add("autoCloseOnPowerOff");
        w.add("autoLocationSaveOnPowerOff");
        w.add("bootAutoStart");
        w.add("bootAutoLaunchUi");
        w.add("welcomeAudioAutoPlay");
        WRITABLE = Collections.unmodifiableSet(w);
    }

    /** {@code {"prefs":{...}}}; anahtar sırası sabittir. */
    public static String toJson(Map<String, Boolean> values) {
        StringBuilder sb = new StringBuilder("{\"prefs\":{");
        boolean first = true;
        for (String key : DEFAULTS.keySet()) {
            Boolean v = values.get(key);
            if (v == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(key).append("\":").append(v);
        }
        return sb.append("}}").toString();
    }

    /**
     * @return yazılacak anahtarlar; bilinmeyen, salt okunur veya boş istek için {@code null}
     */
    public static Map<String, Boolean> acceptable(Map<String, Boolean> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return null;
        }
        for (String key : incoming.keySet()) {
            if (!WRITABLE.contains(key) || incoming.get(key) == null) {
                return null;
            }
        }
        return new LinkedHashMap<>(incoming);
    }
}
