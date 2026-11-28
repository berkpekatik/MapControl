package com.mapcontrol.ai;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import com.mapcontrol.media.LauncherMediaController;

import com.desaysv.ivi.extra.project.carinfo.AvmID;
import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.HvacID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.mapcontrol.vehicle.DriveModeStyle;
import com.mapcontrol.vehicle.material.OemEolConfigReader;
import com.desaysv.ivi.extra.project.carinfo.VrID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.desaysv.ivi.vdb.event.id.vr.bean.VDVRPipeLine;
import com.mapcontrol.service.MapControlService;
import com.mapcontrol.util.AppLaunchHelper;
import com.mapcontrol.util.ClusterNavigationState;
import com.mapcontrol.nav.YandexClusterNavCoordinator;
import com.mapcontrol.util.SplitScreenLauncher;
import com.mapcontrol.util.SplitScreenStore;
import com.mapcontrol.util.TargetPackageStore;
import com.mapcontrol.vehicle.CarSeatMemoryManager;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;
import com.mapcontrol.vehicle.VehicleSunroofController;

import org.json.JSONObject;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * AI Asistanın Function Calling ile tetiklediği araç komutlarını
 * VDBus / CAN-Bus ve MapControl alt modülleri üzerinden icra eden sınıf.
 */
public final class VehicleActionExecutor {
    private static final String TAG = "VehicleActionExecutor";
    private static final String PREFS_NAME = "MapControlPrefs";
    private static final long APP_CACHE_TTL_MS = 15_000L;

    static final class AppCandidate {
        final String packageName;
        final String displayLabel;
        final String normLabel;
        final String normPkg;

        AppCandidate(String packageName, String displayLabel) {
            this.packageName = packageName;
            this.displayLabel = displayLabel;
            this.normLabel = normalizeForMatch(displayLabel);
            this.normPkg = normalizeForMatch(packageName);
        }
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile List<AppCandidate> cachedApps;
    private volatile long cachedAppsAtMs = 0L;

    public VehicleActionExecutor(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Cam tavan veya perde hareketi
     * @param element "sunroof", "sunshade", "both"
     * @param action "open", "close", "tilt"
     */
    public String executeSunroof(String element, String action) {
        VehicleSunroofController controller = VehicleSunroofController.getInstance(context);
        boolean isOpen = "open".equalsIgnoreCase(action);
        boolean isClose = "close".equalsIgnoreCase(action);
        boolean isTilt = "tilt".equalsIgnoreCase(action);

        Log.i(TAG, "executeSunroof: element=" + element + ", action=" + action);

        if ("sunshade".equalsIgnoreCase(element)) {
            if (isOpen) {
                controller.openSunshadeOnly();
                return "Güneş perdesi açılıyor.";
            } else {
                controller.closeSunshadeOnly();
                return "Güneş perdesi kapatılıyor.";
            }
        } else if ("sunroof".equalsIgnoreCase(element)) {
            if (isTilt) {
                controller.tiltSunroof();
                return "Cam tavan havalandırma (tilt) konumuna alınıyor.";
            } else if (isOpen) {
                controller.openSunroof(true);
                return "Cam tavan açılıyor.";
            } else {
                controller.closeSunroof(true);
                return "Cam tavan kapatılıyor.";
            }
        } else {
            // both
            if (isOpen) {
                controller.openSunroof(true);
                return "Cam tavan ve perde birlikte açılıyor.";
            } else {
                controller.closeSunroof(true);
                return "Cam tavan ve perde birlikte kapatılıyor.";
            }
        }
    }

    private void ensureCarInfoReady() {
        try {
            VDBus.getDefault().init(context);
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            if (!proxy.isServiceConnnected()) {
                proxy.init(context);
            }
        } catch (Throwable t) {
            Log.w(TAG, "CarInfoProxy başlatma uyarısı: " + t.getMessage());
        }
    }

    /**
     * Araç kapı camları kontrolü (Sol ön, Sağ ön, Sol arka, Sağ arka, Önler, Arkalar veya Hepsi)
     * @param window "fl" (sol ön), "fr" (sağ ön), "rl" (sol arka), "rr" (sağ arka), "all" (hepsi), "front" (önler), "rear" (arkalar), "left", "right"
     * @param action "open" (aç), "close" (kapat), "vent" (arala)
     */
    /** Tiggo 7 cam komutu: VDBus MODULE_VR öğesi (OEM SVVrControl {@code handleWindowStatement}). */
    static final int VR_WINDOW_ITEM = 120;

    /**
     * Tiggo 7 (T19C) cam kodları, OEM sesli komut uygulaması {@code com.desaysv.vrcontrol} ile birebir: sol ön 1/2,
     * sağ ön 3/4, sol arka 5/6, sağ arka 7/8, ön çift 9/10, arka çift 11/12, tümü 13/14 (aç/kapat), tümünü arala
     * ("COMFORT") 15. Tek camı aralama kodu yok: "vent" her zaman 15. Bu araçta {@code MODULE_CAR_SETTING}
     * {@code ID_CAR_*_WINDOW} yazmak işe yaramıyordu (yalnızca durum okunur: 3 açık, 2 kapalı).
     */
    static int[] t19cWindowCodes(String window, String action) {
        String a = action == null ? "open" : action.toLowerCase(Locale.ROOT).trim();
        if (a.equals("vent") || a.equals("tilt") || a.equals("arala")) {
            return new int[]{15};
        }
        boolean open = !a.equals("close");
        String w = window == null ? "all" : window.toLowerCase(Locale.ROOT).trim();
        switch (w) {
            case "fl": return new int[]{open ? 1 : 2};
            case "fr": return new int[]{open ? 3 : 4};
            case "rl": return new int[]{open ? 5 : 6};
            case "rr": return new int[]{open ? 7 : 8};
            case "front": return new int[]{open ? 9 : 10};
            case "rear": return new int[]{open ? 11 : 12};
            case "left": return open ? new int[]{1, 5} : new int[]{2, 6};
            case "right": return open ? new int[]{3, 7} : new int[]{4, 8};
            default: return new int[]{open ? 13 : 14};
        }
    }

    private String executeWindowTiggo7(String window, String action) {
        ensureCarInfoReady();
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        if (!proxy.isServiceConnnected()) {
            return "Araç bağlantısı hazır değil; cam komutu gönderilmedi.";
        }
        int[] codes = t19cWindowCodes(window, action);
        Log.i(TAG, "executeWindowTiggo7: window=" + window + " action=" + action + " codes=" + java.util.Arrays.toString(codes));
        new Thread(() -> {
            for (int code : codes) {
                try {
                    proxy.sendItemValue(VDEventCarInfo.MODULE_VR, VR_WINDOW_ITEM, code);
                    Thread.sleep(150);
                } catch (Throwable t) {
                    Log.w(TAG, "cam komutu gönderilemedi: " + code, t);
                }
            }
        }).start();
        String a = action == null ? "" : action.toLowerCase(Locale.ROOT);
        String verb = codes.length == 1 && codes[0] == 15 ? "aralanıyor" : (a.equals("close") ? "kapatılıyor" : "açılıyor");
        String which;
        switch (window == null ? "all" : window.toLowerCase(Locale.ROOT)) {
            case "fl": which = "Sol ön cam"; break;
            case "fr": which = "Sağ ön cam"; break;
            case "rl": which = "Sol arka cam"; break;
            case "rr": which = "Sağ arka cam"; break;
            case "front": which = "Ön camlar"; break;
            case "rear": which = "Arka camlar"; break;
            case "left": which = "Sol camlar"; break;
            case "right": which = "Sağ camlar"; break;
            default: which = "Tüm camlar";
        }
        if (codes.length == 1 && codes[0] == 15) which = "Tüm camlar";
        return which + " " + verb + ".";
    }

    public String executeWindow(String window, String action) {
        if (com.mapcontrol.launcher.VehicleProfile.Companion.detectFromDisplay(context)
                == com.mapcontrol.launcher.VehicleProfile.TIGGO7) {
            return executeWindowTiggo7(window, action);
        }
        ensureCarInfoReady();
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        boolean isOpen = !"close".equalsIgnoreCase(action);
        boolean isVent = "vent".equalsIgnoreCase(action) || "tilt".equalsIgnoreCase(action) || "arala".equalsIgnoreCase(action);
        int actionVal = isVent ? 3 : (isOpen ? 1 : 2);
        String actionDesc = isVent ? "havalandırma konumuna aralanıyor" : (isOpen ? "açılıyor" : "kapatılıyor");

        String w = (window != null) ? window.toLowerCase(Locale.ROOT).trim() : "all";
        Log.i(TAG, "executeWindow: target=" + w + ", action=" + action + " (val=" + actionVal + ")");

        try {
            if (w.contains("front") || w.contains("ön") || w.contains("on")) {
                if (w.contains("sol") || w.contains("left") || w.equals("fl")) {
                    proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_FL_WINDOW, actionVal);
                    return "Sol ön cam " + actionDesc + ".";
                } else if (w.contains("sağ") || w.contains("sag") || w.contains("right") || w.equals("fr")) {
                    proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_FR_WINDOW, actionVal);
                    return "Sağ ön cam " + actionDesc + ".";
                } else {
                    new Thread(() -> {
                        try {
                            proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_FL_WINDOW, actionVal);
                            Thread.sleep(100);
                            proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_FR_WINDOW, actionVal);
                        } catch (Throwable ignored) {}
                    }).start();
                    return "Ön camlar " + actionDesc + ".";
                }
            } else if (w.contains("rear") || w.contains("arka")) {
                if (w.contains("sol") || w.contains("left") || w.equals("rl")) {
                    proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_RL_WINDOW, actionVal);
                    return "Sol arka cam " + actionDesc + ".";
                } else if (w.contains("sağ") || w.contains("sag") || w.contains("right") || w.equals("rr")) {
                    proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_RR_WINDOW, actionVal);
                    return "Sağ arka cam " + actionDesc + ".";
                } else {
                    new Thread(() -> {
                        try {
                            proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_RL_WINDOW, actionVal);
                            Thread.sleep(100);
                            proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_RR_WINDOW, actionVal);
                        } catch (Throwable ignored) {}
                    }).start();
                    return "Arka camlar " + actionDesc + ".";
                }
            } else if (w.equals("fl") || w.equals("left") || w.equals("sol")) {
                proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_FL_WINDOW, actionVal);
                return "Sol cam " + actionDesc + ".";
            } else if (w.equals("fr") || w.equals("right") || w.equals("sağ") || w.equals("sag")) {
                proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_FR_WINDOW, actionVal);
                return "Sağ cam " + actionDesc + ".";
            } else if (w.equals("rl")) {
                proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_RL_WINDOW, actionVal);
                return "Sol arka cam " + actionDesc + ".";
            } else if (w.equals("rr")) {
                proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_RR_WINDOW, actionVal);
                return "Sağ arka cam " + actionDesc + ".";
            } else {
                // all / tüm camlar / hepsi
                new Thread(() -> {
                    int[] allWindows = {
                            CarSettingID.ID_CAR_FL_WINDOW,
                            CarSettingID.ID_CAR_FR_WINDOW,
                            CarSettingID.ID_CAR_RL_WINDOW,
                            CarSettingID.ID_CAR_RR_WINDOW
                    };
                    for (int winId : allWindows) {
                        try {
                            proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, winId, actionVal);
                            Thread.sleep(100);
                        } catch (Throwable ignored) {}
                    }
                }).start();
                return "Tüm camlar " + actionDesc + ".";
            }
        } catch (Throwable t) {
            Log.e(TAG, "executeWindow hatası", t);
            return "Cam işlemi gönderilirken hata oluştu: " + t.getMessage();
        }
    }

    static String normalizeSeatAction(String action, Integer level) {
        String a = (action != null) ? action.toLowerCase(Locale.ROOT).trim() : "heat";
        if ((level != null && level == 0)
                || a.contains("off")
                || a.contains("close")
                || a.contains("kapat")) {
            return "off";
        }
        if (a.contains("vent")
                || a.contains("cool")
                || a.contains("soğut")
                || a.contains("sogut")
                || a.contains("havaland")) {
            return "vent";
        }
        return "heat";
    }

    /**
     * MapControl Otomasyon sekmesiyle ({@code ClimateAutomationEngine}) birebir aynı VDBus kodlaması:
     * kapalı=1, Kademe 1=2, Kademe 2=3, Kademe 3=4.
     */
    static int encodeSeatClimateWireValue(boolean isOff, Integer level) {
        if (isOff) {
            return 1;
        }
        int clamped = (level != null && level >= 1 && level <= 3) ? level : 3;
        return clamped + 1;
    }

    private static void sendSeatSettings(
            CarInfoProxy proxy,
            boolean targetDriver,
            boolean targetPassenger,
            int driverItemId,
            int copilotItemId,
            int wireValue
    ) {
        if (targetDriver) {
            proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, driverItemId, wireValue);
        }
        if (targetPassenger) {
            proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, copilotItemId, wireValue);
        }
    }

    private static boolean isCarSettingActive(CarInfoProxy proxy, int itemId) {
        int[] values = proxy.getItemValues(VDEventCarInfo.MODULE_CAR_SETTING, itemId);
        return values != null && values.length > 0 && values[0] > 1;
    }

    /**
     * Sürücü ve Yolcu koltuk ısıtma / havalandırma (soğutma) kontrolü — Otomasyon sekmesiyle birebir aynı protokol.
     * @param seat "driver" (sürücü), "passenger" (yolcu / co-pilot), "both" (her ikisi)
     * @param action "heat" (ısıtma), "vent" (soğutma/havalandırma), "off" (kapat)
     * @param level kademe (1, 2, 3) - varsayılan 3 (maksimum) veya off ise 0
     */
    public String executeSeatClimate(String seat, String action, Integer level) {
        ensureCarInfoReady();
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        if (!proxy.isServiceConnnected()) {
            return "Araç bağlantısı hazır değil; koltuk komutu gönderilmedi.";
        }

        String s = (seat != null) ? seat.toLowerCase(Locale.ROOT).trim() : "driver";
        String normalizedAction = normalizeSeatAction(action, level);
        boolean isOff = "off".equals(normalizedAction);
        int targetLevel = isOff ? 0 : ((level != null && level >= 1 && level <= 3) ? level : 3);
        int wireLevel = encodeSeatClimateWireValue(isOff, targetLevel);

        boolean targetDriver = s.contains("driver") || s.contains("sürücü") || s.contains("surucu") || s.contains("sol") || s.contains("both") || s.contains("hepsi");
        boolean targetPassenger = s.contains("passenger") || s.contains("yolcu") || s.contains("sağ") || s.contains("sag") || s.contains("copilot") || s.contains("both") || s.contains("hepsi");
        if (!targetDriver && !targetPassenger) {
            targetDriver = true; // varsayılan sürücü
        }

        Log.i(TAG, "executeSeatClimate: seat=" + s + ", action=" + normalizedAction
                + ", level=" + targetLevel + " (wireLevel=" + wireLevel
                + ", driver=" + targetDriver + ", passenger=" + targetPassenger + ")");

        try {
            if (isOff) {
                sendSeatSettings(proxy, targetDriver, targetPassenger,
                        CarSettingID.ID_CAR_DRIVER_SEAT_HEAT,
                        CarSettingID.ID_CAR_COPILOT_SEAT_HEAT, 1);
                sendSeatSettings(proxy, targetDriver, targetPassenger,
                        CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION,
                        CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION, 1);
                return (targetDriver && targetPassenger
                        ? "Koltuk ısıtma ve havalandırmalar"
                        : (targetDriver ? "Sürücü koltuk ısıtma/havalandırması" : "Yolcu koltuk ısıtma/havalandırması"))
                        + " kapatıldı.";
            }

            boolean isHeat = "heat".equals(normalizedAction);
            int targetDriverId = isHeat ? CarSettingID.ID_CAR_DRIVER_SEAT_HEAT : CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION;
            int targetCopilotId = isHeat ? CarSettingID.ID_CAR_COPILOT_SEAT_HEAT : CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION;
            int oppositeDriverId = isHeat ? CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION : CarSettingID.ID_CAR_DRIVER_SEAT_HEAT;
            int oppositeCopilotId = isHeat ? CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION : CarSettingID.ID_CAR_COPILOT_SEAT_HEAT;

            if (targetDriver && isCarSettingActive(proxy, oppositeDriverId)) {
                proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, oppositeDriverId, 1);
            }
            if (targetPassenger && isCarSettingActive(proxy, oppositeCopilotId)) {
                proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, oppositeCopilotId, 1);
            }

            sendSeatSettings(proxy, targetDriver, targetPassenger, targetDriverId, targetCopilotId, wireLevel);

            String who;
            if (isHeat) {
                who = (targetDriver && targetPassenger) ? "Ön koltukların ısıtması" : (targetDriver ? "Sürücü koltuk ısıtması" : "Yolcu koltuk ısıtması");
            } else {
                who = (targetDriver && targetPassenger) ? "Ön koltukların havalandırması" : (targetDriver ? "Sürücü koltuk havalandırması" : "Yolcu koltuk havalandırması");
            }
            return who + " " + targetLevel + ". kademede açıldı.";
        } catch (Throwable t) {
            Log.e(TAG, "executeSeatClimate hatası", t);
            return "Koltuk işlemi yapılırken hata: " + t.getMessage();
        }
    }

    /**
     * Direksiyon ısıtmayı açar veya kapatır (Chery MCU toggle=1 protokolü).
     */
    public String executeSteeringWheelHeat(boolean power) {
        ensureCarInfoReady();
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        if (!proxy.isServiceConnnected()) {
            return "Araç bağlantısı hazır değil; direksiyon ısıtma komutu gönderilmedi.";
        }
        try {
            int[] current = proxy.getItemValues(
                    VDEventCarInfo.MODULE_CAR_SETTING,
                    CarSettingID.ID_CAR_STEER_WHEEL_HEAT);
            Integer cmd = resolveSteeringWheelHeatCommand(power, current);
            if (cmd == null) {
                return power
                        ? "Direksiyon ısıtma zaten açık."
                        : "Direksiyon ısıtma zaten kapalı.";
            }
            proxy.sendItemValue(
                    VDEventCarInfo.MODULE_CAR_SETTING,
                    CarSettingID.ID_CAR_STEER_WHEEL_HEAT,
                    cmd);
            Log.i(TAG, "executeSteeringWheelHeat: power=" + power + ", sent=" + cmd);
            return power
                    ? "Direksiyon ısıtma açıldı."
                    : "Direksiyon ısıtma kapatıldı.";
        } catch (Throwable t) {
            Log.e(TAG, "executeSteeringWheelHeat hatası", t);
            return "Direksiyon ısıtma işlemi yapılırken hata: " + t.getMessage();
        }
    }

    static boolean isHalfDegreeFromRawTelemetry(int[] driverVals, int[] copilotVals) {
        return hasHalfDegreeRawValue(driverVals) || hasHalfDegreeRawValue(copilotVals);
    }

    private static boolean hasHalfDegreeRawValue(int[] values) {
        return values != null && values.length > 0 && values[0] > 16 && values[0] <= 30;
    }

    /**
     * Chery / DesaySV OEM {@code SVVrControl} hedef sıcaklık kodlaması:
     * 1°C adımlı sistemlerde (varsayılan Tiggo 8) 15.0..31.0°C -> {@code round(temp - 15.0)} (0..16);
     * 0.5°C adımlı sistemlerde 15.5..30.5°C -> {@code round((temp - 15.5) * 2.0)} (0..30).
     */
    static int encodeHvacTemperatureWireValue(double tempC, boolean halfDegreeStep) {
        if (halfDegreeStep) {
            double clamped = Math.max(15.5, Math.min(tempC, 30.5));
            return Math.max(0, Math.min(30, (int) Math.round((clamped - 15.5) * 2.0)));
        }
        double clamped = Math.max(15.0, Math.min(tempC, 31.0));
        return Math.max(0, Math.min(16, (int) Math.round(clamped - 15.0)));
    }

    /**
     * Sıcaklığı araca uygun öğeyle gönderir ({@link OemEolConfigReader#usesElectricClimateTemperature}): normal klimada
     * 10/13, elektrikli klimada (Tiggo 7) 43 ve değer +1 (OEM svhvac {@code K0}). Yapılandırma okunamazsa ikisi de gider.
     */
    private static void sendClimateTemperature(CarInfoProxy proxy, int encodedTemp) {
        Boolean electric = OemEolConfigReader.usesElectricClimateTemperature();
        Log.i(TAG, "sendClimateTemperature encoded=" + encodedTemp + " electric=" + electric);
        if (!Boolean.TRUE.equals(electric)) {
            proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_TEMPERATURE_DRIVER, encodedTemp);
            proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_TEMPERATURE_COPILOT, encodedTemp);
        }
        if (!Boolean.FALSE.equals(electric)) {
            proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_ELEC_TEMPERATURE, Math.min(16, encodedTemp) + 1);
        }
    }

    /**
     * Klima ve havalandırma kontrolü (Sıcaklık, kademe, iç sirkülasyon, mod)
     * @param power true: aç, false: kapat, null: dokunma/otomatik aç
     * @param temperature hedef sıcaklık derecesi (örn: 26)
     * @param fanLevel fan kademesi / üfleme hızı (1..7)
     * @param circulation "inner" (iç sirkülasyon) / "outer" (dış hava)
     * @param mode "auto", "cool", "heat", "defrost"
     */
    public String executeClimate(Boolean power, Double temperature, Integer fanLevel, String circulation, String mode) {
        if (circulation != null && !"inner".equals(circulation) && !"outer".equals(circulation)) {
            return "Bu sirkülasyon modu desteklenmiyor; komut gönderilmedi.";
        }
        ensureCarInfoReady();
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        if (!proxy.isServiceConnnected()) {
            return "Araç bağlantısı hazır değil; klima komutu gönderilmedi.";
        }
        Log.i(TAG, "executeClimate: power=" + power + ", temp=" + temperature + ", fan=" + fanLevel + ", circ=" + circulation + ", mode=" + mode);

        try {
            if (Boolean.FALSE.equals(power)) {
                // SVVrControl Hvac presenter: WORKING close request is 2 (not status 0).
                proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_WORKING, 2);
                return "Klima kapatma komutu gönderildi.";
            }

            int[] workingVals = proxy.getItemValues(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_WORKING);
            boolean wasAcOn = workingVals != null && workingVals.length > 0 && workingVals[0] == 1;
            if (Boolean.TRUE.equals(power) || !wasAcOn) {
                proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_STATE, 1);
                proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_WORKING, 1);
            }

            StringBuilder feedback = new StringBuilder("Klima ");

            // 1. Sıcaklık (OEM SVVrControl: 10=ID_AC_TEMPERATURE_DRIVER, 13=ID_AC_TEMPERATURE_COPILOT)
            if (temperature != null && !Double.isNaN(temperature) && temperature > 0) {
                int[] driverVals = proxy.getItemValues(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_TEMPERATURE_DRIVER);
                int[] copilotVals = proxy.getItemValues(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_TEMPERATURE_COPILOT);
                boolean halfDegree = isHalfDegreeFromRawTelemetry(driverVals, copilotVals)
                        || OemEolConfigReader.isHalfDegreeClimateScale();
                int encodedTemp = encodeHvacTemperatureWireValue(temperature, halfDegree);
                double displayTemp = halfDegree
                        ? (15.5 + encodedTemp * 0.5)
                        : (15.0 + encodedTemp);

                Log.i(TAG, "executeClimate temp=" + temperature + " -> encodedTemp=" + encodedTemp
                        + " (halfDegree=" + halfDegree + ", wasAcOn=" + wasAcOn + ")");

                sendClimateTemperature(proxy, encodedTemp);
                if (!wasAcOn) {
                    final int delayedTemp = encodedTemp;
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            CarInfoProxy p = CarInfoProxy.getInstance();
                            if (p.isServiceConnnected()) {
                                sendClimateTemperature(p, delayedTemp);
                            }
                        } catch (Throwable ignored) {}
                    }, 250L);
                }

                if (Math.abs(displayTemp - Math.rint(displayTemp)) < 0.05) {
                    feedback.append((int) Math.round(displayTemp)).append("°C");
                } else {
                    feedback.append(String.format(Locale.US, "%.1f°C", displayTemp));
                }
            }

            // 2. Fan Kademesi (1-7)
            if (fanLevel != null && fanLevel > 0) {
                int speed = Math.max(1, Math.min(fanLevel, 7));
                proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_SET_BLOW_LEVEL, speed);
                proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_BLOW_SPEED_LEVEL, speed);
                if (feedback.length() > 6) feedback.append(", ");
                feedback.append("kademe ").append(speed);
            }

            // 3. İç / Dış Sirkülasyon
            if (circulation != null && !circulation.isEmpty()) {
                String c = circulation.toLowerCase(Locale.ROOT);
                boolean isInner = c.contains("in") || c.contains("iç") || c.contains("ic");
                int circVal = isInner ? 1 : 2;
                proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_CIRCULATION_MODE, circVal);
                if (feedback.length() > 6) feedback.append(", ");
                feedback.append(isInner ? "iç sirkülasyon aktif" : "dış hava modu aktif");
            }

            // 4. Mod (Auto, Defrost)
            if (mode != null && !mode.isEmpty()) {
                String m = mode.toLowerCase(Locale.ROOT);
                if (m.contains("auto") || m.contains("otomatik")) {
                    proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_AUTO_STATE, 1);
                    if (feedback.length() > 6) feedback.append(", ");
                    feedback.append("otomatik mod");
                } else if (m.contains("defrost") || m.contains("buğu") || m.contains("bugu")) {
                    proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_FRONT_GLASS_HEAT, 1);
                    proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_MAX_FRONT_DEFOREST, 1);
                    if (feedback.length() > 6) feedback.append(", ");
                    feedback.append("ön cam buğu çözme aktif");
                }
            }

            if (feedback.length() == 6) {
                return "Klima açıldı.";
            }

            feedback.append(" olarak ayarlandı.");
            return feedback.toString();
        } catch (Throwable t) {
            Log.e(TAG, "executeClimate hatası", t);
            return "Klima komutu iletilirken hata oluştu: " + t.getMessage();
        }
    }

    /**
     * Arka cam çocuk kilidi
     * @param enable true=kilitli, false=açık
     */
    public String executeChildLock(boolean enable) {
        Log.i(TAG, "executeChildLock: enable=" + enable);
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putInt("childLockSetting", enable ? 1 : -1).apply();

            CarInfoProxy proxy = CarInfoProxy.getInstance();
            if (!proxy.isServiceConnnected()) {
                proxy.init(context);
            }
            proxy.sendItemValue(
                    VDEventCarInfo.MODULE_CAR_SETTING,
                    CarSettingID.ID_CAR_WINDOW_PROHIBIT,
                    1);

            return enable ? "Arka cam çocuk kilidi kilitlendi." : "Arka cam çocuk kilidi açıldı.";
        } catch (Throwable t) {
            Log.e(TAG, "executeChildLock hatası", t);
            return "Çocuk kilidi işlemi gönderilirken hata oluştu: " + t.getMessage();
        }
    }

    public static int encodeDriveModeWireValue(String mode) {
        switch (mode != null ? mode.toLowerCase(Locale.ROOT).trim() : "normal") {
            case "eco":
            case "eko":
                return 0;
            case "sport":
            case "spor":
                return 2;
            case "snow":
            case "kar":
                return 3;
            case "mud":
            case "çamur":
            case "camur":
                return 4;
            case "offroad":
            case "arazi":
                return 5;
            case "sand":
            case "kum":
                return 7;
            case "normal":
            case "comfort":
            case "standart":
            default:
                return 1;
        }
    }

    public static String getDriveModeDisplayName(int modeValue) {
        switch (modeValue) {
            case 0: return "Eco";
            case 2: return "Sport";
            case 3: return "Kar (Snow)";
            case 4: return "Çamur (Mud)";
            case 5: return "Arazi (Offroad)";
            case 7: return "Kum (Sand)";
            case 1:
            default:
                return "Standart (Normal)";
        }
    }

    /**
     * Sürüş modu değiştirme (Chery Tiggo / Desay SV NewEnergyID.ID_DRIVE_MODE):
     * Eco=0, Normal=1, Sport=2, Snow=3, Mud=4, Offroad=5, Sand=7
     * @param mode "eco", "sport", "normal", "comfort", "snow", "mud", "sand", "offroad"
     */
    public String executeDriveMode(String mode) {
        Log.i(TAG, "executeDriveMode: mode=" + mode);
        int modeValue = encodeDriveModeWireValue(mode);
        String modeName = getDriveModeDisplayName(modeValue);

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putInt("driveModeSetting", modeValue).apply();

            CarInfoProxy proxy = CarInfoProxy.getInstance();
            if (!proxy.isServiceConnnected()) {
                proxy.init(context);
            }
            proxy.sendItemValue(VDEventCarInfo.MODULE_NEW_ENERGY, 4, modeValue);
            return "Sürüş modu " + modeName + " olarak ayarlandı.";
        } catch (Throwable t) {
            Log.e(TAG, "executeDriveMode hatası", t);
            return "Sürüş modu değiştirilirken hata oluştu: " + t.getMessage();
        }
    }

    /**
     * Koltuk hafızası konumunu çağırma veya kaydetme
     * @param profile 1, 2, 3
     */
    public String executeSeatMemory(int profile) {
        return executeSeatMemory(profile, "recall");
    }

    public String executeSeatMemory(int profile, String action) {
        Log.i(TAG, "executeSeatMemory: profile=" + profile + ", action=" + action);
        int slot = (profile >= 1 && profile <= 3) ? profile : 1;
        try {
            CarSeatMemoryManager manager = CarSeatMemoryManager.getInstance(context);
            boolean isSave = "save".equalsIgnoreCase(action)
                    || "kaydet".equalsIgnoreCase(action)
                    || "hafizaya_al".equalsIgnoreCase(action);
            if (isSave) {
                manager.syncCurrentFromCar();
                return "Mevcut koltuk ayarları " + slot + " numaralı hafızaya kaydedildi.";
            } else {
                manager.applySeatMemory(slot);
                return slot + " numaralı koltuk hafızası konumu çağrılıyor.";
            }
        } catch (Throwable t) {
            Log.e(TAG, "executeSeatMemory hatası", t);
            return "Koltuk hafızası işlemi sırasında hata oluştu: " + t.getMessage();
        }
    }

    /**
     * Canlı araç CAN-Bus telemetri ve teşhis sorgulama
     * @param type "range", "fuel", "battery", "tire", "doors", "all"
     */
    public String executeGetVehicleTelemetry(String type) {
        try {
            VehicleMetricsRepository repo = VehicleMetricsRepository.getInstance(context);
            VehicleMetricsSnapshot s = repo.getSnapshot();
            if (s == null) {
                return "Araç sensör verilerine şu an ulaşılamıyor.";
            }

            String q = (type != null) ? type.toLowerCase(Locale.ROOT).trim() : "all";

            // 1. Menzil & Yakıt sorgusu
            if (q.contains("range") || q.contains("menzil")) {
                if (VehicleMetricsSnapshot.isValid(s.rangeKm) && s.rangeKm > 0) {
                    String fuelInfo = "";
                    if (VehicleMetricsSnapshot.isValid(s.fuelPercent) && s.fuelPercent >= 0) {
                        double f = (s.fuelPercent <= 100) ? s.fuelPercent : (s.fuelPercent / 10.0);
                        fuelInfo = String.format(Locale.getDefault(), " Yakıt seviyeniz yüzde %.0f.", f);
                    }
                    return "Kalan menziliniz yaklaşık " + s.rangeKm + " kilometre." + fuelInfo;
                }
                return "Kalan menzil verisi şu an araçtan okunamıyor.";
            }

            if (q.contains("fuel") || q.contains("yakıt") || q.contains("yakit") || q.contains("benzin")) {
                if (VehicleMetricsSnapshot.isValid(s.fuelPercent) && s.fuelPercent >= 0) {
                    double f = (s.fuelPercent <= 100) ? s.fuelPercent : (s.fuelPercent / 10.0);
                    String rangeInfo = (VehicleMetricsSnapshot.isValid(s.rangeKm) && s.rangeKm > 0)
                            ? (" Bu yakıtla yaklaşık " + s.rangeKm + " km yol gidebilirsiniz.") : "";
                    return String.format(Locale.getDefault(), "Yakıt seviyeniz yüzde %.0f.%s", f, rangeInfo);
                }
                return "Yakıt seviyesi bilgisi şu an alınamıyor.";
            }

            // 2. Akü Voltajı sorgusu
            if (q.contains("battery") || q.contains("akü") || q.contains("aku") || q.contains("voltaj")) {
                if (VehicleMetricsSnapshot.isValid(s.batteryRaw) && s.batteryRaw > 0) {
                    double volt = s.batteryRaw / 1000.0;
                    String status = (volt >= 12.0) ? "Akü voltajınız normal durumda." : "Akü voltajı biraz düşük seviyede.";
                    return String.format(Locale.getDefault(), "Akü voltajı %.1f Volt. %s", volt, status);
                }
                return "Akü voltajı verisi şu an alınamıyor.";
            }

            // 3. Lastik Basınçları (TPMS) sorgusu
            if (q.contains("tire") || q.contains("lastik") || q.contains("tpms")) {
                int tpmsWarn = repo.getCombined(ReadOnlyID.ID_TIRE_PRESSURE_WARNING);
                if (VehicleMetricsSnapshot.isValid(tpmsWarn) && tpmsWarn > 0) {
                    return "Dikkat! Lastik basınç uyarısı tespit edildi. Lütfen lastik havalarınızı kontrol edin.";
                }
                return "Tüm lastik basınçlarınız normal, herhangi bir basınç uyarısı bulunmuyor.";
            }

            // 4. Kapılar, Bagaj ve Ön Kaput sorgusu
            if (q.contains("door") || q.contains("kapı") || q.contains("kapi") || q.contains("bagaj")) {
                List<String> openItems = new ArrayList<>();
                if (s.isDoorLfOpen()) openItems.add("Sol ön (sürücü) kapısı");
                if (s.isDoorRfOpen()) openItems.add("Sağ ön (yolcu) kapısı");
                if (s.isDoorLrOpen()) openItems.add("Sol arka kapı");
                if (s.isDoorRrOpen()) openItems.add("Sağ arka kapı");
                if (s.isTrunkOpen()) openItems.add("Bagaj kapağı");
                int hood = repo.getCombined(ReadOnlyID.ID_HOOD_STATE);
                if (VehicleMetricsSnapshot.isValid(hood) && hood == 1) openItems.add("Ön kaput");

                if (openItems.isEmpty()) {
                    return "Tüm kapılar, bagaj kapağı ve ön kaput tamamen kapalı.";
                }
                return "Açık olan bölümler: " + TextUtils.join(", ", openItems) + ".";
            }

            // 5. Genel Teşhis ve Durum Raporu ("all")
            StringBuilder rep = new StringBuilder();
            if (VehicleMetricsSnapshot.isValid(s.rangeKm) && s.rangeKm > 0) {
                rep.append("Kalan menzil ").append(s.rangeKm).append(" km");
                if (VehicleMetricsSnapshot.isValid(s.fuelPercent) && s.fuelPercent >= 0) {
                    double f = (s.fuelPercent <= 100) ? s.fuelPercent : (s.fuelPercent / 10.0);
                    rep.append(String.format(Locale.getDefault(), " (Yakıt: %%%.0f)", f));
                }
                rep.append(". ");
            }
            if (VehicleMetricsSnapshot.isValid(s.batteryRaw) && s.batteryRaw > 0) {
                rep.append(String.format(Locale.getDefault(), "Akü %.1fV. ", s.batteryRaw / 1000.0));
            }
            int tpms = repo.getCombined(ReadOnlyID.ID_TIRE_PRESSURE_WARNING);
            if (VehicleMetricsSnapshot.isValid(tpms) && tpms > 0) {
                rep.append("Lastik basınç uyarısı var! ");
            } else {
                rep.append("Lastikler normal. ");
            }
            if (s.isAnyDoorOpen() || s.isTrunkOpen()) {
                rep.append("Dikkat, açık kapı veya bagaj mevcut. ");
            } else {
                rep.append("Tüm kapılar kapalı. ");
            }
            if (s.hasDriveMode()) {
                String dName = DriveModeStyle.shortLabel(s.driveMode);
                if (dName != null) rep.append("Sürüş modu: ").append(dName).append(". ");
            }
            if (VehicleMetricsSnapshot.isValid(s.odoKm) && s.odoKm > 0) {
                rep.append(String.format(Locale.getDefault(), "Toplam: %,d km.", s.odoKm));
            }

            String result = rep.toString().trim();
            return !result.isEmpty() ? result : "Araç sensör verileri normal.";
        } catch (Throwable t) {
            Log.e(TAG, "executeGetVehicleTelemetry hatası", t);
            return "Araç telemetri bilgileri alınamadı: " + t.getMessage();
        }
    }

    /**
     * Anlık araç metrikleri durum özeti
     */
    public String getVehicleMetricsSummary() {
        return executeGetVehicleTelemetry("all");
    }

    /**
     * Canlı şehir hava durumu sorgulama (Open-Meteo Geocoding & Forecast)
     */
    public String getWeatherSummary(String city) {
        if (city == null || city.trim().isEmpty()) {
            city = "Ankara";
        }
        city = city.trim();

        try {
            // 1. Şehir koordinatlarını bul (Geocoding)
            String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + java.net.URLEncoder.encode(city, "UTF-8")
                    + "&count=1&language=tr&format=json";

            double lat = 39.92;
            double lon = 32.85;
            String resolvedName = city;

            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(geoUrl).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                if (conn.getResponseCode() == 200) {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        org.json.JSONObject geoJson = new org.json.JSONObject(sb.toString());
                        if (geoJson.has("results")) {
                            org.json.JSONArray results = geoJson.getJSONArray("results");
                            if (results.length() > 0) {
                                org.json.JSONObject first = results.getJSONObject(0);
                                lat = first.getDouble("latitude");
                                lon = first.getDouble("longitude");
                                resolvedName = first.optString("name", city);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Geocoding hatası: " + t.getMessage());
            }

            // 2. Canlı hava durumu çek
            String weatherUrl = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current_weather=true",
                    lat, lon);

            java.net.HttpURLConnection wConn = (java.net.HttpURLConnection) new java.net.URL(weatherUrl).openConnection();
            wConn.setConnectTimeout(6000);
            wConn.setReadTimeout(6000);
            if (wConn.getResponseCode() == 200) {
                try (java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(wConn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    StringBuilder wsb = new StringBuilder();
                    String wLine;
                    while ((wLine = r.readLine()) != null) {
                        wsb.append(wLine);
                    }
                    org.json.JSONObject wJson = new org.json.JSONObject(wsb.toString());
                    if (wJson.has("current_weather")) {
                        org.json.JSONObject cw = wJson.getJSONObject("current_weather");
                        double temp = cw.getDouble("temperature");
                        double wind = cw.getDouble("windspeed");
                        int code = cw.getInt("weathercode");
                        String condition = getWeatherDescription(code);

                        String result = String.format(Locale.getDefault(),
                                "%s için güncel hava: %.1f°C, %s, rüzgar %.1f km/s.",
                                resolvedName, temp, condition, wind);
                        Log.i(TAG, "getWeatherSummary başarılı: " + result);
                        return result;
                    }
                }
            }
            return resolvedName + " için hava durumu verisi alınamadı.";
        } catch (Throwable t) {
            Log.e(TAG, "getWeatherSummary hatası", t);
            return city + " hava durumu sorgulanırken hata: " + t.getMessage();
        }
    }

    private static String getWeatherDescription(int code) {
        if (code == 0) return "Açık ve Güneşli";
        if (code == 1) return "Genellikle Açık";
        if (code == 2) return "Parçalı Bulutlu";
        if (code == 3) return "Kapalı";
        if (code == 45 || code == 48) return "Sisli";
        if (code >= 51 && code <= 55) return "Çiseleyen Yağmurlu";
        if (code >= 56 && code <= 67) return "Yağmurlu";
        if (code >= 71 && code <= 77) return "Kar Yağışlı";
        if (code >= 80 && code <= 82) return "Sağanak Yağışlı";
        if (code >= 95) return "Gök Gürültülü Fırtına";
        return "Normal";
    }

    /**
     * MapControl bölünmüş ekran çiftini başlatır.
     * İsteğe bağlı olarak sol ve sağ uygulama adlarını dinamik olarak çözer.
     */
    public String executeSplitScreen(String leftAppName, String rightAppName) {
        try {
            String mainAppPkg = TargetPackageStore.read(context);
            boolean leftRequested = leftAppName != null && !leftAppName.trim().isEmpty();
            boolean rightRequested = rightAppName != null && !rightAppName.trim().isEmpty();

            String pkg1 = leftRequested ? findPackageByName(leftAppName) : null;
            String pkg2 = rightRequested ? findPackageByName(rightAppName) : null;

            boolean leftMissing = leftRequested && pkg1 == null;
            boolean rightMissing = rightRequested && pkg2 == null;
            if (leftMissing && rightMissing) {
                return leftAppName.trim() + " ve " + rightAppName.trim() + " uygulamalarını cihazda bulamadım.";
            }
            if (leftMissing) {
                return leftAppName.trim() + " uygulamasını cihazda bulamadım.";
            }
            if (rightMissing) {
                return rightAppName.trim() + " uygulamasını cihazda bulamadım.";
            }

            // Eğer sol belirtilmemiş ama sağ belirtilmişse: sol tarafı ana ekrandaki uygulama yap
            if (pkg1 == null && pkg2 != null) {
                if (mainAppPkg != null && !mainAppPkg.isEmpty() && !mainAppPkg.equals(pkg2)) {
                    pkg1 = mainAppPkg;
                } else {
                    String stored1 = SplitScreenStore.getPkg1(context);
                    pkg1 = (stored1 != null && !stored1.isEmpty() && !stored1.equals(pkg2))
                            ? stored1 : SplitScreenStore.getPkg2(context);
                }
            }

            // Eğer sağ belirtilmemiş ama sol belirtilmişse: sağ tarafı ana ekrandaki uygulama veya kayıtlı 2. uygulama yap
            if (pkg2 == null && pkg1 != null) {
                if (mainAppPkg != null && !mainAppPkg.isEmpty() && !mainAppPkg.equals(pkg1)) {
                    pkg2 = mainAppPkg;
                } else {
                    String stored2 = SplitScreenStore.getPkg2(context);
                    pkg2 = (stored2 != null && !stored2.isEmpty() && !stored2.equals(pkg1))
                            ? stored2 : SplitScreenStore.getPkg1(context);
                }
            }

            // İkisi de boşsa kayıtlı olanları al
            if (pkg1 == null) pkg1 = SplitScreenStore.getPkg1(context);
            if (pkg2 == null) pkg2 = SplitScreenStore.getPkg2(context);

            // Eğer hala aynıysa veya biri boşsa, ana ekrandaki uygulamayla eşle
            if (pkg1 != null && pkg1.equals(pkg2)) {
                if (mainAppPkg != null && !mainAppPkg.isEmpty() && !mainAppPkg.equals(pkg2)) {
                    pkg1 = mainAppPkg;
                } else {
                    // Varsayılan harita / navigasyon ara
                    String mapPkg = findPackageByName("harita");
                    if (mapPkg != null && !mapPkg.equals(pkg2)) {
                        pkg1 = mapPkg;
                    }
                }
            }

            if (pkg1 == null || pkg2 == null || pkg1.equals(pkg2)) {
                return "Bölünmüş ekran için solda ve sağda farklı iki uygulama seçilmelidir.";
            }

            // Seçilenleri kaydet
            SplitScreenStore.setPkg1(context, pkg1);
            SplitScreenStore.setPkg2(context, pkg2);

            PackageManager pm = context.getPackageManager();
            String name1 = pkg1;
            String name2 = pkg2;
            try {
                name1 = pm.getApplicationLabel(pm.getApplicationInfo(pkg1, 0)).toString();
            } catch (Throwable ignored) {}
            try {
                name2 = pm.getApplicationLabel(pm.getApplicationInfo(pkg2, 0)).toString();
            } catch (Throwable ignored) {}

            SplitScreenLauncher.Ratio ratio = SplitScreenStore.getRatio(context);
            final String finalN1 = name1;
            final String finalN2 = name2;
            SplitScreenLauncher.start(context, pkg1, pkg2, ratio, result -> {
                Log.i(TAG, "SplitScreen (" + finalN1 + " | " + finalN2 + ") sonucu: " + result);
            });

            return String.format(Locale.getDefault(), "Solda %s, sağda %s olacak şekilde ekran bölünüyor.", name1, name2);
        } catch (Throwable t) {
            Log.e(TAG, "executeSplitScreen hatası", t);
            return "Bölünmüş ekran başlatılırken hata: " + t.getMessage();
        }
    }

    /**
     * Uygulama adına göre cihazdaki paket adını (package name) tespit eder.
     */
    /**
     * Araç kamerasının (DVR / 360 kamera) video kaydını başlatır, durdurur veya durumunu bildirir.
     * @param action "start", "stop" veya "status"
     */
    public String executeDvr(String action) {
        final com.mapcontrol.dvr.DvrController dvr = com.mapcontrol.dvr.DvrController.get(context);
        String act = (action != null) ? action.toLowerCase(Locale.ROOT).trim() : "start";
        Log.i(TAG, "executeDvr: action=" + act);

        if (!dvr.isCameraSupported()) {
            return "Araç kamera donanımı (360 DVR) bulunamadı veya bu cihazda desteklenmiyor.";
        }

        if (act.contains("dur") || act.contains("stop") || act.contains("bitir") || act.contains("kapat")) {
            if (!dvr.isRecording()) {
                return "Araç kamerası şu anda kayıt yapmıyor.";
            }
            dvr.stopRecording(true);
            return "Araç kamerası kaydı durduruldu.";
        } else if (act.contains("durum") || act.contains("status")) {
            return dvr.isRecording()
                    ? "Araç kamerası şu anda aktif olarak kayıt yapıyor."
                    : "Araç kamerası şu anda kayıt yapmıyor.";
        } else {
            // "start", "başlat", "kaydet" vs.
            if (dvr.isRecording()) {
                return "Araç kamerası zaten kayıt yapıyor.";
            }
            if (dvr.isAutoInternalEnabled() || dvr.isAutoUsbEnabled()) {
                dvr.resumeAuto();
            } else {
                dvr.setAutoInternalEnabled(true);
            }
            return "Araç kamerası kaydı başlatılıyor.";
        }
    }

    private static Integer resolveSteeringWheelHeatCommand(boolean turnOn, int[] currentValues) {
        boolean isCurrentlyOn = currentValues != null && currentValues.length > 0 && currentValues[0] == 1;
        if (turnOn) {
            return isCurrentlyOn ? null : 1;
        }
        return isCurrentlyOn ? 1 : null;
    }

    private static String normalizeForMatch(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.forLanguageTag("tr-TR"))
                .replace("ı", "i")
                .replace("ğ", "g")
                .replace("ü", "u")
                .replace("ş", "s")
                .replace("ö", "o")
                .replace("ç", "c")
                .replaceAll("[^a-z0-9]", "");
    }

    static String cleanAppQuery(String appName) {
        if (appName == null) return "";
        String query = appName.toLowerCase(Locale.forLanguageTag("tr-TR")).trim();
        query = query.replaceAll("^(lütfen|bana)\\s+", "");
        query = query.replaceAll("\\s+(aç|ac|başlat|baslat|çalıştır|calistir)$", "");
        query = query.replaceAll("(ı|i|u|ü|yı|yi|yu|yü)'?(nı|ni|nu|nü)?\\s+(aç|ac|başlat|baslat)$", "");
        query = query.replaceAll("\\s+uygulaması(nı)?$", "");
        return query.trim();
    }

    private static int levenshteinDistance(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }

    private static double computeSimilarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int maxLen = Math.max(a.length(), b.length());
        int dist = levenshteinDistance(a, b);
        return 1.0 - ((double) dist / (double) maxLen);
    }

    private static String findInCandidates(List<AppCandidate> candidates, String... exactPackages) {
        for (String pkg : exactPackages) {
            for (AppCandidate c : candidates) {
                if (pkg.equals(c.packageName)) return c.packageName;
            }
        }
        return null;
    }

    private List<AppCandidate> collectLaunchableApps() {
        long now = SystemClock.elapsedRealtime();
        List<AppCandidate> snapshot = cachedApps;
        if (snapshot != null && (now - cachedAppsAtMs) < APP_CACHE_TTL_MS) {
            return snapshot;
        }

        PackageManager pm = context.getPackageManager();
        Map<String, AppCandidate> byPkg = new LinkedHashMap<>();

        try {
            Intent mainIntent = new Intent(Intent.ACTION_MAIN);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolved = pm.queryIntentActivities(mainIntent, 0);
            if (resolved != null) {
                for (ResolveInfo ri : resolved) {
                    if (ri.activityInfo == null || ri.activityInfo.packageName == null) continue;
                    String pkg = ri.activityInfo.packageName;
                    CharSequence actLabel = ri.loadLabel(pm);
                    String label = actLabel != null ? actLabel.toString().trim() : "";
                    if (label.isEmpty()) {
                        try {
                            label = pm.getApplicationLabel(ri.activityInfo.applicationInfo).toString().trim();
                        } catch (Throwable ignored) {}
                    }
                    if (!label.isEmpty() && !byPkg.containsKey(pkg)) {
                        byPkg.put(pkg, new AppCandidate(pkg, label));
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "queryIntentActivities hatası", t);
        }

        try {
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            if (apps != null) {
                for (ApplicationInfo ai : apps) {
                    if (ai == null || ai.packageName == null || byPkg.containsKey(ai.packageName)) continue;
                    if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                    String label = pm.getApplicationLabel(ai).toString().trim();
                    if (!label.isEmpty()) {
                        byPkg.put(ai.packageName, new AppCandidate(ai.packageName, label));
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "getInstalledApplications hatası", t);
        }

        List<AppCandidate> result = new ArrayList<>(byPkg.values());
        cachedApps = result;
        cachedAppsAtMs = now;
        return result;
    }

    /**
     * Gemini bağlamına vermek için cihazda yüklü açılabilir uygulamaların güncel ad listesini döndürür.
     */
    public String getInstalledAppLabelsSummary() {
        List<AppCandidate> candidates = collectLaunchableApps();
        if (candidates.isEmpty()) return "";
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (AppCandidate c : candidates) {
            if (c.displayLabel != null && !c.displayLabel.trim().isEmpty()) {
                names.add(c.displayLabel.trim());
            }
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String name : names) {
            if (count > 0) sb.append(", ");
            sb.append(name);
            count++;
            if (count >= 45) break;
        }
        return sb.toString();
    }

    public String findPackageByName(String appName) {
        if (appName == null || appName.trim().isEmpty()) {
            return null;
        }
        return resolvePackageFromCandidates(appName, collectLaunchableApps());
    }

    static String resolvePackageFromCandidates(String appName, List<AppCandidate> candidates) {
        if (appName == null || appName.trim().isEmpty() || candidates == null || candidates.isEmpty()) {
            return null;
        }

        String query = cleanAppQuery(appName);
        String normQuery = normalizeForMatch(query);
        if (normQuery.isEmpty()) {
            normQuery = normalizeForMatch(appName);
        }
        if (normQuery.isEmpty()) {
            return null;
        }

        // 1. Aşama: Tam Eşleşme (Normalized Label == Normalized Query)
        for (AppCandidate c : candidates) {
            if (c.normLabel.equals(normQuery)) {
                return c.packageName;
            }
        }

        // 2. Aşama: İçerik Eşleşmesi (En yakın uzunluktaki adayı seç)
        AppCandidate bestContains = null;
        int bestLenDiff = Integer.MAX_VALUE;
        for (AppCandidate c : candidates) {
            if (c.normLabel.isEmpty()) continue;
            boolean match = c.normLabel.contains(normQuery)
                    || (normQuery.length() >= 4 && c.normLabel.startsWith(normQuery))
                    || (c.normLabel.length() >= 3 && normQuery.contains(c.normLabel));
            if (match) {
                int diff = Math.abs(c.normLabel.length() - normQuery.length());
                if (diff < bestLenDiff) {
                    bestLenDiff = diff;
                    bestContains = c;
                }
            }
        }
        if (bestContains != null) {
            return bestContains.packageName;
        }

        // 3. Aşama: Paket Adında Eşleşme
        if (normQuery.length() >= 3) {
            for (AppCandidate c : candidates) {
                if (c.normPkg.contains(normQuery)) {
                    return c.packageName;
                }
            }
        }

        // 4. Aşama: Popüler Uygulama ve Konuşma Dili Alias Eşleştirmeleri
        if (normQuery.contains("inat")) {
            for (AppCandidate c : candidates) {
                if (c.normPkg.contains("inat") || c.normLabel.contains("inat")) return c.packageName;
            }
        }
        if (normQuery.contains("waze")) {
            String found = findInCandidates(candidates, "com.waze");
            if (found != null) return found;
        }
        if (normQuery.contains("harita") || normQuery.contains("maps") || normQuery.contains("navigasyon")) {
            String found = findInCandidates(candidates,
                    "com.google.android.apps.maps",
                    "ru.yandex.yandexmaps",
                    "ru.yandex.yandexnavi",
                    "com.waze");
            if (found != null) return found;
        }
        if (normQuery.contains("equalizer") || normQuery.contains("ekolayzer")) {
            String found = findInCandidates(candidates, "com.jazibkhan.equalizer");
            if (found != null) return found;
        }
        if (normQuery.contains("spotify") || normQuery.contains("spotifay")) {
            String found = findInCandidates(candidates, "com.spotify.music", "com.spotify.lite");
            if (found != null) return found;
        }
        if (normQuery.contains("youtube") || normQuery.contains("yutub")) {
            String found = findInCandidates(candidates, "com.google.android.youtube", "com.google.android.youtube.tv");
            if (found != null) return found;
        }
        if (normQuery.contains("yandex")) {
            String found = findInCandidates(candidates, "ru.yandex.yandexmaps", "ru.yandex.yandexnavi");
            if (found != null) return found;
        }
        if (normQuery.contains("netflix") || normQuery.contains("netfliks")) {
            String found = findInCandidates(candidates, "com.netflix.mediaclient");
            if (found != null) return found;
        }
        if (normQuery.contains("blutv") || normQuery.contains("bluetv")) {
            for (AppCandidate c : candidates) {
                if (c.normPkg.contains("blutv") || c.normLabel.contains("blutv")) return c.packageName;
            }
        }
        if (normQuery.contains("exxen") || normQuery.contains("eksen")) {
            for (AppCandidate c : candidates) {
                if (c.normPkg.contains("exxen") || c.normLabel.contains("exxen")) return c.packageName;
            }
        }
        if (normQuery.contains("ayarlar") || normQuery.contains("ayar")) {
            String found = findInCandidates(candidates, "com.android.settings");
            if (found != null) return found;
        }

        // 5. Aşama: Yazım / Telaffuz Benzerliği (Fuzzy Match - en az 4 karakter ve %72+ benzerlik)
        if (normQuery.length() >= 4) {
            AppCandidate bestFuzzy = null;
            double bestScore = 0.72;
            for (AppCandidate c : candidates) {
                if (c.normLabel.length() < 3) continue;
                double score = computeSimilarity(normQuery, c.normLabel);
                if (score >= bestScore) {
                    bestScore = score;
                    bestFuzzy = c;
                }
            }
            if (bestFuzzy != null) {
                return bestFuzzy.packageName;
            }
        }

        return null;
    }

    private boolean isPackageInstalled(String pkg) {
        try {
            return context.getPackageManager().getLaunchIntentForPackage(pkg) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * İstenen uygulamayı açar (örn: Harita, Spotify, YouTube, İnatBox, Radyo vb.)
     */
    public String executeLaunchApp(String appName) {
        if (appName == null || appName.trim().isEmpty()) {
            return "Açılacak uygulama adı belirtilmedi.";
        }
        String targetPkg = findPackageByName(appName);
        if (targetPkg != null) {
            PackageManager pm = context.getPackageManager();
            String label = targetPkg;
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(targetPkg, 0)).toString();
            } catch (Throwable ignored) {}
            final String finalPkg = targetPkg;
            mainHandler.post(() -> AppLaunchHelper.moveAppToDefaultDisplay(context, finalPkg));
            return label + " açılıyor.";
        }
        return appName.trim() + " uygulamasını cihazda bulamadım.";
    }

    /**
     * YouTube veya Spotify üzerinde belirtilen içerik, şarkı, video veya sanatçıyı arayıp doğrudan açar.
     *
     * @param query Aratılacak içerik (örn: "Kurtlar Vadisi", "Dönence", "90lar Pop")
     * @param platform "youtube", "spotify" veya "auto"
     */
    public String executePlayMedia(String query, String platform) {
        if (query == null || query.trim().isEmpty()) {
            return "Aratılacak medya veya içerik adı belirtilmedi.";
        }
        query = query.trim();
        String plat = (platform != null) ? platform.toLowerCase(Locale.ROOT).trim() : "auto";
        boolean isSpotify = plat.contains("spotify") || query.toLowerCase(Locale.ROOT).contains("spotify");

        // Sorgu içindeki fazlalık platform adlarını ve eylem fiillerini ayıkla
        String cleanQuery = query
                .replaceAll("(?i)\\b(youtube'dan|youtube'da|youtube|yutubdan|yutubda|yutub)\\b", "")
                .replaceAll("(?i)\\b(spotify'dan|spotify'da|spotify|spotifaydan|spotifayda|spotifay)\\b", "")
                .replaceAll("(?i)\\b(aç|çal|başlat|oynat|izle|bul|ara)\\b", "")
                .trim();
        if (cleanQuery.isEmpty()) {
            cleanQuery = query;
        }

        if (isSpotify && !plat.contains("youtube")) {
            // Spotify Arama & Otomatik Çalma (Multimedya ekranındaki yüklü Spotify)
            String spotifyPkg = null;
            if (isPackageInstalled("com.spotify.music")) {
                spotifyPkg = "com.spotify.music";
            } else if (isPackageInstalled("com.spotify.lite")) {
                spotifyPkg = "com.spotify.lite";
            }

            if (spotifyPkg == null) {
                // Spotify cihazda yüklü değilse kullanıcıyı bilgilendir
                return "Spotify uygulaması araç ekranında yüklü bulunamadı. Bluetooth üzerinden çalan medyayı 'müziği durdur/geç' komutlarıyla yönetebilirsiniz.";
            }

            try {
                // Öncelik 1: Standart Android Medya Arama & Doğrudan Çalma Intent'i (Auto-Play)
                android.content.Intent playIntent = new android.content.Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
                playIntent.putExtra(android.app.SearchManager.QUERY, cleanQuery);
                playIntent.putExtra(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*");
                playIntent.setPackage(spotifyPkg);
                playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(playIntent);
                return "Spotify üzerinde \"" + cleanQuery + "\" açılıyor ve çalınıyor.";
            } catch (Throwable t1) {
                Log.w(TAG, "Spotify MEDIA_PLAY_FROM_SEARCH başarısız, URI search deneniyor", t1);
                try {
                    // Öncelik 2: Spotify Doğrudan Arama URI'si
                    Intent spotifyIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + Uri.encode(cleanQuery)));
                    spotifyIntent.setPackage(spotifyPkg);
                    spotifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(spotifyIntent);
                    return "Spotify üzerinde \"" + cleanQuery + "\" aranıyor.";
                } catch (Throwable t2) {
                    Log.w(TAG, "Spotify URI intent başarısız, normal başlatılıyor", t2);
                    executeLaunchApp("spotify");
                    return "Spotify açıldı, \"" + cleanQuery + "\" aranıyor.";
                }
            }
        } else {
            // YouTube Arama & Oynatma (Kurtlar Vadisi vb.)
            String[] ytPackages = new String[]{
                    "com.google.android.youtube",
                    "com.google.android.youtube.tv",
                    "app.revanced.android.youtube",
                    "com.liskovsoft.videomanager",
                    "com.teamsmart.videomanager.tv",
                    "org.schabi.newpipe"
            };
            String targetPkg = null;
            for (String pkg : ytPackages) {
                if (isPackageInstalled(pkg)) {
                    targetPkg = pkg;
                    break;
                }
            }

            try {
                Uri ytUri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(cleanQuery));
                Intent ytIntent = new Intent(Intent.ACTION_VIEW, ytUri);
                if (targetPkg != null) {
                    ytIntent.setPackage(targetPkg);
                }
                ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(ytIntent);
                return "YouTube üzerinde \"" + cleanQuery + "\" aranıyor ve açılıyor.";
            } catch (Throwable t) {
                Log.e(TAG, "YouTube intent başlatılamadı", t);
                return "YouTube açılırken bir sorun oluştu: " + t.getMessage();
            }
        }
    }

    /**
     * Medya oynatma kontrolleri ve ses seviyesi yönetimi (Play, Pause, Sonraki, Önceki, Ses Seviyesi).
     *
     * @param action "play", "pause", "play_pause", "next", "previous", "volume_up", "volume_down", "mute", "unmute", "set_volume"
     * @param volumeLevel 0-100 arası istenen ses seviyesi (isteğe bağlı)
     */
    public String executeControlMedia(String action, Integer volumeLevel) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        String act = (action != null) ? action.toLowerCase(Locale.ROOT).trim() : "play_pause";

        LauncherMediaController mediaController = new LauncherMediaController(context);
        mediaController.start();

        switch (act) {
            case "next":
            case "skip_next":
                mediaController.skipToNext();
                dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT);
                mediaController.stop();
                return "Sonraki parçaya geçildi.";

            case "previous":
            case "skip_previous":
                mediaController.skipToPrevious();
                dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                mediaController.stop();
                return "Önceki parçaya geçildi.";

            case "pause":
            case "stop":
                mediaController.playPause();
                dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE);
                mediaController.stop();
                return "Medya duraklatıldı.";

            case "play":
            case "resume":
                mediaController.playPause();
                dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY);
                mediaController.stop();
                return "Medya oynatılıyor.";

            case "play_pause":
            case "toggle":
                mediaController.playPause();
                dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                mediaController.stop();
                return "Oynatma durumu değiştirildi.";

            case "volume_up":
                if (audioManager != null) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                }
                mediaController.stop();
                return "Medya sesi yükseltildi.";

            case "volume_down":
                if (audioManager != null) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                }
                mediaController.stop();
                return "Medya sesi kısıldı.";

            case "mute":
                if (audioManager != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
                    } else {
                        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
                    }
                }
                mediaController.stop();
                return "Medya sesi kapatıldı (sessize alındı).";

            case "unmute":
                if (audioManager != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
                    } else {
                        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
                    }
                }
                mediaController.stop();
                return "Medya sesi açıldı.";

            case "set_volume":
                if (audioManager != null && volumeLevel != null) {
                    int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int target;
                    if (volumeLevel <= 100 && volumeLevel > maxVol) {
                        target = (int) Math.round((volumeLevel / 100.0) * maxVol);
                    } else {
                        target = Math.min(maxVol, Math.max(0, volumeLevel));
                    }
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
                    mediaController.stop();
                    return "Medya sesi " + volumeLevel + " seviyesine ayarlandı.";
                }
                mediaController.stop();
                return "Ses seviyesi belirlenemedi.";

            default:
                mediaController.stop();
                return "Bilinmeyen medya işlemi: " + action;
        }
    }

    private void dispatchMediaKeyEvent(int keyCode) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            long eventTime = SystemClock.uptimeMillis();
            KeyEvent downEvent = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0);
            KeyEvent upEvent = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0);
            if (am != null) {
                am.dispatchMediaKeyEvent(downEvent);
                am.dispatchMediaKeyEvent(upEvent);
            }
        } catch (Throwable t) {
            Log.w(TAG, "dispatchMediaKeyEvent failed: " + keyCode, t);
        }
    }

    /**
     * Araç çevresindeki yerleri (en yakın benzinlik, nöbetçi eczane, hastane, oto yıkama vb.) haritada arar veya rota başlatır.
     *
     * @param query Aranacak yer/POI (örn: "en yakın benzinlik", "nöbetçi eczane", "oto yıkamacı")
     * @param startNav Doğrudan navigasyon rotası başlatılsın mı?
     * @param navApp "google", "yandex", "waze", "auto"
     */
    public String executeSearchNearbyPlaces(String query, boolean startNav, String navApp) {
        if (query == null || query.trim().isEmpty()) {
            return "Aranacak yer veya konum türü belirtilmedi.";
        }
        query = query.trim();

        if (startNav || query.toLowerCase(Locale.ROOT).contains("git") || query.toLowerCase(Locale.ROOT).contains("rota")) {
            return executeStartNavigation(query, navApp, false);
        }

        String requestedApp = (navApp != null) ? navApp.toLowerCase(Locale.ROOT).trim() : "";
        boolean hasYandexMaps = isPackageInstalled("ru.yandex.yandexmaps");
        boolean hasGoogleMaps = isPackageInstalled(AppLaunchHelper.GOOGLE_MAPS_PACKAGE);

        try {
            Intent searchIntent;
            String appName = "Haritalar";

            if (requestedApp.contains("yandex") && hasYandexMaps) {
                Uri yandexUri = Uri.parse("yandexmaps://maps.yandex.ru/?text=" + Uri.encode(query));
                searchIntent = new Intent(Intent.ACTION_VIEW, yandexUri);
                searchIntent.setPackage("ru.yandex.yandexmaps");
                appName = "Yandex Haritalar";
            } else if (hasGoogleMaps) {
                Uri gmmUri = Uri.parse("geo:0,0?q=" + Uri.encode(query));
                searchIntent = new Intent(Intent.ACTION_VIEW, gmmUri);
                searchIntent.setPackage(AppLaunchHelper.GOOGLE_MAPS_PACKAGE);
                appName = "Google Haritalar";
            } else if (hasYandexMaps) {
                Uri yandexUri = Uri.parse("yandexmaps://maps.yandex.ru/?text=" + Uri.encode(query));
                searchIntent = new Intent(Intent.ACTION_VIEW, yandexUri);
                searchIntent.setPackage("ru.yandex.yandexmaps");
                appName = "Yandex Haritalar";
            } else {
                Uri geoUri = Uri.parse("geo:0,0?q=" + Uri.encode(query));
                searchIntent = new Intent(Intent.ACTION_VIEW, geoUri);
            }

            searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(searchIntent);
            return appName + " üzerinde \"" + query + "\" aranıyor ve gösteriliyor.";
        } catch (Throwable t) {
            Log.e(TAG, "executeSearchNearbyPlaces başarısız", t);
            return query + " aranırken bir sorun oluştu: " + t.getMessage();
        }
    }

    /**
     * İstenen navigasyon uygulamasında belirtilen hedefe doğrudan rota ve navigasyon başlatır.
     * Cihazda hangi haritalar yüklüyse otomatik tespit eder, istenen harita yoksa yüklü olana yönlendirir.
     * İsteğe bağlı olarak haritayı doğrudan sürücü ekranına (Cluster) yansıtır.
     *
     * @param destination Hedef konum veya adres (örn: "AŞTİ", "Kadıköy", "Anıtkabir")
     * @param navApp Navigasyon uygulaması ("yandex", "google", "waze", "auto" veya null)
     * @param toCluster true ise rota başlatıldıktan sonra harita sürücü ekranına (cluster) taşınır
     */
    public String executeStartNavigation(String destination, String navApp, boolean toCluster) {
        if (destination == null || destination.trim().isEmpty()) {
            return "Gidilecek hedef konum belirtilmedi.";
        }
        destination = destination.trim();
        String requestedApp = (navApp != null) ? navApp.toLowerCase(Locale.ROOT).trim() : "";
        Log.i(TAG, "executeStartNavigation: destination=" + destination + ", navApp=" + navApp + ", toCluster=" + toCluster);

        boolean hasYandexMaps = isPackageInstalled("ru.yandex.yandexmaps");
        boolean hasYandexNavi = isPackageInstalled("ru.yandex.yandexnavi");
        boolean hasGoogleMaps = isPackageInstalled(AppLaunchHelper.GOOGLE_MAPS_PACKAGE);
        boolean hasWaze = isPackageInstalled("com.waze");

        String chosenPkg = null;
        String chosenLabel = null;
        String fallbackNotice = null;

        boolean reqYandex = requestedApp.contains("yandex");
        boolean reqGoogle = requestedApp.contains("google") || requestedApp.contains("maps") || requestedApp.contains("harita");
        boolean reqWaze = requestedApp.contains("waze");

        if (reqYandex) {
            if (hasYandexMaps) {
                chosenPkg = "ru.yandex.yandexmaps";
                chosenLabel = "Yandex Haritalar";
            } else if (hasYandexNavi) {
                chosenPkg = "ru.yandex.yandexnavi";
                chosenLabel = "Yandex Navigasyon";
            } else {
                fallbackNotice = "Yandex yüklü olmadığı için ";
            }
        } else if (reqGoogle) {
            if (hasGoogleMaps) {
                chosenPkg = AppLaunchHelper.GOOGLE_MAPS_PACKAGE;
                chosenLabel = "Google Haritalar";
            } else {
                fallbackNotice = "Google Haritalar yüklü olmadığı için ";
            }
        } else if (reqWaze) {
            if (hasWaze) {
                chosenPkg = "com.waze";
                chosenLabel = "Waze";
            } else {
                fallbackNotice = "Waze yüklü olmadığı için ";
            }
        }

        // Eğer kullanıcı belirli bir harita söylemediyse veya istenen uygulama yüklü değilse:
        if (chosenPkg == null) {
            String defaultPkg = TargetPackageStore.read(context);
            if (defaultPkg != null && !defaultPkg.isEmpty() && isPackageInstalled(defaultPkg)) {
                chosenPkg = defaultPkg;
                chosenLabel = getAppLabel(defaultPkg);
            } else if (hasYandexMaps) {
                chosenPkg = "ru.yandex.yandexmaps";
                chosenLabel = "Yandex Haritalar";
            } else if (hasYandexNavi) {
                chosenPkg = "ru.yandex.yandexnavi";
                chosenLabel = "Yandex Navigasyon";
            } else if (hasGoogleMaps) {
                chosenPkg = AppLaunchHelper.GOOGLE_MAPS_PACKAGE;
                chosenLabel = "Google Haritalar";
            } else if (hasWaze) {
                chosenPkg = "com.waze";
                chosenLabel = "Waze";
            } else {
                chosenPkg = AppLaunchHelper.GOOGLE_MAPS_PACKAGE;
                chosenLabel = "Haritalar";
            }
        }

        try {
            Intent navIntent = null;

            if ("ru.yandex.yandexmaps".equals(chosenPkg)) {
                Uri yandexUri = Uri.parse("yandexmaps://build_route_on_map?text=" + Uri.encode(destination));
                navIntent = new Intent(Intent.ACTION_VIEW, yandexUri);
                navIntent.setPackage("ru.yandex.yandexmaps");
            } else if ("ru.yandex.yandexnavi".equals(chosenPkg)) {
                Uri yandexUri = Uri.parse("yandexnavi://build_route_on_map?text=" + Uri.encode(destination));
                navIntent = new Intent(Intent.ACTION_VIEW, yandexUri);
                navIntent.setPackage("ru.yandex.yandexnavi");
            } else if (AppLaunchHelper.GOOGLE_MAPS_PACKAGE.equals(chosenPkg)) {
                Uri gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(destination) + "&mode=d");
                navIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                navIntent.setPackage(AppLaunchHelper.GOOGLE_MAPS_PACKAGE);
            } else if ("com.waze".equals(chosenPkg)) {
                Uri wazeUri = Uri.parse("waze://?q=" + Uri.encode(destination) + "&navigate=yes");
                navIntent = new Intent(Intent.ACTION_VIEW, wazeUri);
                navIntent.setPackage("com.waze");
            } else {
                Uri geoUri = Uri.parse("geo:0,0?q=" + Uri.encode(destination));
                navIntent = new Intent(Intent.ACTION_VIEW, geoUri);
            }

            navIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(navIntent);

            // Sürücü ekranına taşıma istendiyse
            if (toCluster) {
                TargetPackageStore.writeAndBroadcast(context, chosenPkg);
                if (chosenPkg.contains("yandex")) {
                    YandexClusterNavCoordinator.activate(context);
                }
                Intent clusterIntent = new Intent(context, MapControlService.class);
                clusterIntent.setAction(MapControlService.ACTION_BENCH_OPEN_CLUSTER);
                context.startService(clusterIntent);
                ClusterNavigationState.setLastKnownOpen(true);

                String prefix = (fallbackNotice != null) ? fallbackNotice : "";
                return prefix + chosenLabel + " ile " + destination + " hedefine rota başlatıldı ve sürücü ekranına taşındı.";
            }

            String prefix = (fallbackNotice != null) ? fallbackNotice : "";
            return prefix + chosenLabel + " ile " + destination + " hedefine rota başlatılıyor.";

        } catch (Throwable t) {
            Log.e(TAG, "executeStartNavigation hatası", t);
            return destination + " için navigasyon başlatılamadı: " + t.getMessage();
        }
    }

    /**
     * Haritayı veya uygulamayı sürücü ekranına (Cluster / Hayalet Ekran) taşır, açar veya kapatır.
     *
     * @param action "open", "close" veya "toggle"
     * @param appName Belirli bir uygulama adı (örn: "yandex", "google", "maps", "harita") veya null
     */
    public String executeClusterControl(String action, String appName) {
        String act = (action != null) ? action.toLowerCase(Locale.ROOT).trim() : "open";
        boolean isOpen = act.contains("open") || act.contains("aç") || act.contains("taşı") || act.contains("transfer");
        boolean isClose = act.contains("close") || act.contains("kapat");

        if (!isOpen && !isClose) {
            // Durumu tersine çevir (toggle)
            isOpen = !ClusterNavigationState.getLastKnownOpen();
        }

        try {
            if (isOpen) {
                String targetPkg = null;
                if (appName != null && !appName.isEmpty()) {
                    String a = appName.toLowerCase(Locale.ROOT);
                    if (a.contains("yandex")) {
                        if (isPackageInstalled("ru.yandex.yandexmaps")) targetPkg = "ru.yandex.yandexmaps";
                        else if (isPackageInstalled("ru.yandex.yandexnavi")) targetPkg = "ru.yandex.yandexnavi";
                    } else if (a.contains("google") || a.contains("maps") || a.contains("harita")) {
                        if (isPackageInstalled(AppLaunchHelper.GOOGLE_MAPS_PACKAGE)) targetPkg = AppLaunchHelper.GOOGLE_MAPS_PACKAGE;
                    } else if (a.contains("waze")) {
                        if (isPackageInstalled("com.waze")) targetPkg = "com.waze";
                    }
                }

                if (targetPkg == null) {
                    targetPkg = TargetPackageStore.read(context);
                }
                if (targetPkg == null || targetPkg.isEmpty() || !isPackageInstalled(targetPkg)) {
                    if (isPackageInstalled("ru.yandex.yandexmaps")) targetPkg = "ru.yandex.yandexmaps";
                    else if (isPackageInstalled("ru.yandex.yandexnavi")) targetPkg = "ru.yandex.yandexnavi";
                    else if (isPackageInstalled(AppLaunchHelper.GOOGLE_MAPS_PACKAGE)) targetPkg = AppLaunchHelper.GOOGLE_MAPS_PACKAGE;
                    else targetPkg = AppLaunchHelper.GOOGLE_MAPS_PACKAGE;
                }

                TargetPackageStore.writeAndBroadcast(context, targetPkg);
                if (targetPkg.contains("yandex")) {
                    YandexClusterNavCoordinator.activate(context);
                }

                Intent clusterIntent = new Intent(context, MapControlService.class);
                clusterIntent.setAction(MapControlService.ACTION_BENCH_OPEN_CLUSTER);
                context.startService(clusterIntent);
                ClusterNavigationState.setLastKnownOpen(true);

                String label = targetPkg.contains("yandex") ? "Yandex Haritalar"
                        : (targetPkg.contains("maps") ? "Google Haritalar" : "Harita");
                return label + " sürücü ekranına (Cluster) taşındı.";
            } else {
                Intent clusterIntent = new Intent(context, MapControlService.class);
                clusterIntent.setAction(MapControlService.ACTION_BENCH_CLOSE_CLUSTER);
                clusterIntent.putExtra(MapControlService.EXTRA_CLUSTER_CLOSE_SEND_BACKGROUND, true);
                context.startService(clusterIntent);
                ClusterNavigationState.setLastKnownOpen(false);

                return "Sürücü ekranı kapatıldı, uygulama ana ekrana alındı.";
            }
        } catch (Throwable t) {
            Log.e(TAG, "executeClusterControl hatası", t);
            return "Sürücü ekranı işlemi gerçekleştirilemedi: " + t.getMessage();
        }
    }

    private String getAppLabel(String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Throwable t) {
            if (packageName.contains("yandex")) return "Yandex Haritalar";
            if (packageName.contains("maps")) return "Google Haritalar";
            if (packageName.contains("waze")) return "Waze";
            return "Haritalar";
        }
    }

    /**
     * Chery OEM VR Service ve iFlytek yayın köprüsü.
     * Araç içi 'com.desaysv.vrservice' ve 'com.iflytek.cutefly.speechclient.hmi'
     * servislerine doğrudan yerel intent fırlatarak donanım seviyesinde icrayı garantiye alır.
     */
    public void sendNativeCheryVrBroadcast(String service, String operation, JSONObject slots) {
        try {
            JSONObject root = new JSONObject();
            root.put("service", service != null ? service : "carControl");
            root.put("operation", operation != null ? operation : "OPEN");
            JSONObject sem = new JSONObject();
            if (slots != null) {
                sem.put("slots", slots);
            }
            root.put("semantic", sem);

            String jsonPayload = root.toString();
            Log.d(TAG, "sendNativeCheryVrBroadcast payload: " + jsonPayload);

            // 1. Chery OEM VR Service intent
            Intent intent1 = new Intent("com.desaysv.vr.handleMessage");
            intent1.putExtra("nlp_out_handle_msg", jsonPayload);
            intent1.putExtra("value", jsonPayload);
            context.sendBroadcast(intent1);

            // 2. iFlytek Autofly handMessage intent
            Intent intent2 = new Intent("com.iflytek.autofly.handMessage");
            intent2.putExtra("nlp_out_handle_msg", jsonPayload);
            intent2.putExtra("value", jsonPayload);
            context.sendBroadcast(intent2);
        } catch (Throwable t) {
            Log.w(TAG, "sendNativeCheryVrBroadcast error: " + t.getMessage());
        }
    }

    /**
     * Ambiyans Aydınlatması Kontrolü
     * @param color Renk adı (kırmızı, mavi, yeşil, sarı, mor, turuncu, beyaz, buz mavisi vb.)
     * @param brightness Parlaklık yüzdesi (1-100)
     * @param rhythm Müzik ritmine göre dans modu (true/false)
     * @param power Açık/Kapalı (true/false)
     */
    /**
     * Ambiyans Aydınlatması Kontrolü (Hem 64-Renk Tek Bölgeli hem Çok Bölgeli Dinamik com.simba.atmospherelamp Uyumlu)
     *
     * Donanım protokolü (SVCarSetting.apk & SVVrControl.apk):
     * - ID_CAR_AMBIENT_LIGHT (7): 1 = AÇIK, 2 = KAPALI (0 değil!)
     * - ID_CAR_MUSIC_RHYTHM (8): 1 = AÇIK, 2 = KAPALI (Statik renk seçildiğinde mutlaka 2 gönderilmeli!)
     * - ID_CAR_AMBIENT_LIGHT_RELATE_DRIVE_MODE (55): 1 = AÇIK, 2 = KAPALI (Statik renk seçildiğinde 50ms gecikmeyle 2 gönderilmeli!)
     * - ID_CAR_AMBIENT_LIGHT_COLOR (11): 1..64 arası Chery 64 renk kodu
     * - ID_CAR_AMBIENT_LIGHT_BRIGHTNESS (12): 1..5 arası kademe
     * - VDBus 0x8019b ("controlLight"): Çok bölgeli dinamik ambiyans modülü (VrAmbientLightMgr) ve SVVrControl köprüsü
     */
    public String executeAmbientLight(String color, Integer brightness, Boolean rhythm, Boolean power) {
        ensureCarInfoReady();
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        Log.i(TAG, "executeAmbientLight: color=" + color + ", brightness=" + brightness + ", rhythm=" + rhythm + ", power=" + power);

        try {
            if (Boolean.FALSE.equals(power)) {
                // Chery MCU'da Ambiyans Kapatma değeri 2'dir (1=ON, 2=OFF)
                proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_AMBIENT_LIGHT, 2);
                sendVrControlLightEvent("AMBIENT_LIGHT", "CLOSE", "");
                return "Ambiyans aydınlatması kapatıldı.";
            }

            // Ambiyans zaten açık mı kontrol et; kapalıysa 1 (ON) gönder
            int curState = proxy.getItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_AMBIENT_LIGHT);
            boolean wasOff = (curState != 1);
            proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_AMBIENT_LIGHT, 1);
            if (wasOff) {
                sendVrControlLightEvent("AMBIENT_LIGHT", "OPEN", "");
            }

            StringBuilder sb = new StringBuilder("Ambiyans aydınlatması ");
            boolean hasColor = (color != null && !color.trim().isEmpty());

            // 1. Müzik Ritmi Modu AÇMA (Renk belirtilmeden sadece ritim istendiyse)
            if (Boolean.TRUE.equals(rhythm) && !hasColor) {
                // Önce Sürüş Modu İlişkisini kapat (55 -> 2 = OFF)
                proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_AMBIENT_LIGHT_RELATE_DRIVE_MODE, 2);
                proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_MUSIC_RHYTHM, 1);
                mainHandler.postDelayed(() -> {
                    try {
                        proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_MUSIC_RHYTHM, 1);
                        sendVrControlLightEvent("AMBIENT_LIGHT_BINDER_MUSIC", "OPEN", "");
                    } catch (Throwable ignored) {}
                }, wasOff ? 500L : 80L);
                sb.append("müzik ritmi moduna alındı");
            }
            // 2. Statik Renk Değiştirme VEYA Müzik Ritmini Kapatma
            else if (hasColor || Boolean.FALSE.equals(rhythm)) {
                // KRİTİK: Müzik ritmi (cmdId 8) veya Sürüş Modu (cmdId 55) açıkken MCU statik renk komutunu (cmdId 11) yoksayar!
                // SVCarSetting.apk (SettingAmbientLightViewModel$changeLampMode$1) protokolüne birebir uygun olarak:
                // Adım 1: Müzik ritmini kapat (8 -> 2 = OFF)
                proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_MUSIC_RHYTHM, 2);
                sendVrControlLightEvent("AMBIENT_LIGHT_BINDER_MUSIC", "CLOSE", "");

                // Adım 2: 50ms sonra Sürüş Modu ilişkisini kapat (55 -> 2 = OFF)
                mainHandler.postDelayed(() -> {
                    try {
                        proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_AMBIENT_LIGHT_RELATE_DRIVE_MODE, 2);
                        sendVrControlLightEvent("AMBIENT_LIGHT_BINDER_DRIVE_MODE", "CLOSE", "");
                    } catch (Throwable ignored) {}
                }, 50L);

                if (hasColor) {
                    final String cleanColor = color.trim();
                    final int colorCode = mapColorNameToCode(cleanColor);
                    final String vrColorValue = mapColorNameToVrValue(cleanColor);
                    Log.i(TAG, "executeAmbientLight: setting static color='" + cleanColor + "' -> code=" + colorCode + ", vrValue=" + vrColorValue);

                    // Hemen + 140ms sonra (Müzik ritmi ve sürüş modu kapandıktan hemen sonra) + 520ms sonra (SVVrControl 500ms donanım gecikmesi)
                    proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_AMBIENT_LIGHT_COLOR, colorCode);
                    mainHandler.postDelayed(() -> {
                        try {
                            proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_MUSIC_RHYTHM, 2);
                            proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_AMBIENT_LIGHT_COLOR, colorCode);
                            sendVrControlLightEvent("AMBIENT_LIGHT", "SWITCH", vrColorValue);
                        } catch (Throwable ignored) {}
                    }, 140L);

                    mainHandler.postDelayed(() -> {
                        try {
                            proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_AMBIENT_LIGHT_COLOR, colorCode);
                        } catch (Throwable ignored) {}
                    }, 520L);

                    sb.append(cleanColor).append(" renge ayarlandı");
                } else {
                    sb.append("müzik ritim modu kapatıldı");
                }
            }

            // 3. Parlaklık Ayarı (Chery donanım kademesi 1..5 arasındadır)
            if (brightness != null && brightness > 0) {
                final int level = (brightness <= 5)
                        ? brightness
                        : Math.max(1, Math.min(5, (int) Math.round(brightness / 20.0)));
                proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_AMBIENT_LIGHT_BRIGHTNESS, level);
                mainHandler.postDelayed(() -> {
                    try {
                        proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_AMBIENT_LIGHT_BRIGHTNESS, level);
                        sendVrControlLightEvent("AMBIENT_BRIGHTNESS", "ADJUST", String.valueOf(level));
                    } catch (Throwable ignored) {}
                }, 220L);
                if (sb.length() > 22) sb.append(", parlaklık kademe ").append(level).append(" yapıldı");
                else sb.append("parlaklığı kademe ").append(level).append(" olarak ayarlandı");
            }

            if (sb.length() == 22) {
                sb.append("açıldı.");
            } else {
                sb.append(".");
            }

            return sb.toString();
        } catch (Throwable t) {
            Log.e(TAG, "executeAmbientLight error", t);
            return "Ambiyans aydınlatması ayarlanırken hata: " + t.getMessage();
        }
    }

    /**
     * SVVrControl.apk ve SVCarSetting.apk (VrAmbientLightMgr) tarafından dinlenen
     * VDBus 0x8019b ("controlLight") olayını tetikler.
     */
    private void sendVrControlLightEvent(String type, String action, String value) {
        try {
            JSONObject sem = new JSONObject();
            sem.put("value", value != null ? value : "");
            sem.put("action", action != null ? action : "SWITCH");
            sem.put("type", type != null ? type : "AMBIENT_LIGHT");
            sem.put("position", "ALL");

            JSONObject root = new JSONObject();
            root.put("user", "");
            root.put("semantic", sem);

            VDVRPipeLine pipeLine = new VDVRPipeLine();
            pipeLine.setPkgName("com.iflytek.autofly.voicecoreservice");
            pipeLine.setKey("controlLight");
            pipeLine.setValue(root.toString());
            pipeLine.setResultCode(0);
            pipeLine.setTtsForbid(true);

            VDEvent event = VDVRPipeLine.createEvent(0x8019b, pipeLine);
            VDBus.getDefault().set(event);
            Log.d(TAG, "sendVrControlLightEvent dispatched 0x8019b: " + root);
        } catch (Throwable t) {
            Log.w(TAG, "sendVrControlLightEvent error: " + t.getMessage());
        }
    }

    /**
     * SVVrControl.apk (d$a.h) & SVCarSetting.apk (commandAmbientLights$2) 64-renk tablosu:
     * 1 = Yeşil (RGB 0,227,21)
     * 2 = Mavi / Lacivert (RGB 26,57,235)
     * 3 = Kırmızı (RGB 255,0,0)
     * 19 = Turkuaz / Camgöbeği / Buz Mavisi / Açık Mavi (RGB 0,255,248)
     * 20 = Beyaz / Soğuk Beyaz (RGB 0,255,216)
     * 36 = Sarı / Altın (RGB 237,201,0)
     * 41 = Turuncu / Kehribar (RGB 249,141,0)
     * 49 = Pembe / Gül / Fuşya (RGB 255,0,76)
     * 57 = Mor / Eflatun / Lila (RGB 255,0,202)
     */
    private int mapColorNameToCode(String colorName) {
        String c = colorName.toLowerCase(Locale.ROOT)
                .replace('ı', 'i')
                .replace('ş', 's')
                .replace('ğ', 'g')
                .replace('ü', 'u')
                .replace('ö', 'o')
                .replace('ç', 'c');

        if (c.contains("kirmiz") || c.contains("red") || c.contains("bordo") || c.equals("al")) return 3;
        if (c.contains("yesil") || c.contains("green") || c.contains("zumrut")) return 1;
        if (c.contains("turkuaz") || c.contains("camgobegi") || c.contains("buz") || c.contains("acik mavi") || c.contains("gok mavi") || c.contains("cyan") || c.contains("ice")) return 19;
        if (c.contains("mavi") || c.contains("lacivert") || c.contains("blue")) return 2;
        if (c.contains("sari") || c.contains("altin") || c.contains("yellow") || c.contains("gold") || c.contains("limon")) return 36;
        if (c.contains("turuncu") || c.contains("kehribar") || c.contains("orange") || c.contains("amber")) return 41;
        if (c.contains("pembe") || c.contains("gul") || c.contains("fusya") || c.contains("pink") || c.contains("rose")) return 49;
        if (c.contains("mor") || c.contains("eflatun") || c.contains("lila") || c.contains("menekse") || c.contains("purple") || c.contains("violet") || c.contains("magenta")) return 57;
        if (c.contains("beyaz") || c.contains("white")) return 20;
        if (c.contains("rastgele") || c.contains("random") || c.contains("karisik")) {
            return new java.util.Random().nextInt(64) + 1;
        }
        return 2; // Varsayılan Chery mavisi (2)
    }

    private String mapColorNameToVrValue(String colorName) {
        String c = colorName.toLowerCase(Locale.ROOT)
                .replace('ı', 'i')
                .replace('ş', 's')
                .replace('ğ', 'g')
                .replace('ü', 'u')
                .replace('ö', 'o')
                .replace('ç', 'c');

        if (c.contains("kirmiz") || c.contains("red") || c.contains("bordo") || c.equals("al")) return "RED";
        if (c.contains("yesil") || c.contains("green") || c.contains("zumrut")) return "GREEN";
        if (c.contains("turkuaz") || c.contains("camgobegi") || c.contains("buz") || c.contains("acik mavi") || c.contains("gok mavi") || c.contains("cyan") || c.contains("ice") || c.contains("beyaz") || c.contains("white")) return "CYAN";
        if (c.contains("mavi") || c.contains("lacivert") || c.contains("blue")) return "BLUE";
        if (c.contains("sari") || c.contains("altin") || c.contains("yellow") || c.contains("gold")) return "YELLOW";
        if (c.contains("turuncu") || c.contains("kehribar") || c.contains("orange") || c.contains("amber")) return "ORANGE";
        if (c.contains("pembe") || c.contains("pink") || c.contains("gul") || c.contains("fusya")) return "PURPLE";
        if (c.contains("mor") || c.contains("eflatun") || c.contains("lila") || c.contains("menekse") || c.contains("purple") || c.contains("violet")) return "PURPLE";
        if (c.contains("rastgele") || c.contains("random")) return "RANDOM";
        return "BLUE";
    }

    /**
     * 360 Derece Çevre Görüş Kamerası (AVM) Kontrolü
     * @param action "open" veya "close"
     */
    public String executeAvmCamera(String action) {
        ensureCarInfoReady();
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        boolean isOpen = (action == null || !"close".equalsIgnoreCase(action));

        Log.i(TAG, "executeAvmCamera: action=" + action + ", isOpen=" + isOpen);

        try {
            if (isOpen) {
                proxy.sendItemValue(VDEventCarInfo.MODULE_AVM, AvmID.ID_AVM_DISPLAY, 1);
                // iFlytek AVM intent
                Intent avmIntent = new Intent("com.iflytek.autofly.AVMService");
                avmIntent.setPackage("com.android.gl2jni");
                context.sendBroadcast(avmIntent);

                // Chery RVC kamera uygulamasını da başlat
                try {
                    Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage("com.desaysv.rvc");
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(launchIntent);
                    } else {
                        AppLaunchHelper.moveAppToDefaultDisplay(context, "com.desaysv.rvc");
                    }
                } catch (Throwable ignored) {}

                JSONObject slots = new JSONObject();
                slots.put("name", "全景");
                sendNativeCheryVrBroadcast("carControl", "OPEN", slots);

                return "360 derece çevre görüş kamerası açıldı.";
            } else {
                proxy.sendItemValue(VDEventCarInfo.MODULE_AVM, AvmID.ID_AVM_OFF, 1);
                JSONObject slots = new JSONObject();
                slots.put("name", "全景");
                sendNativeCheryVrBroadcast("carControl", "CLOSE", slots);

                return "360 derece çevre görüş kamerası kapatıldı.";
            }
        } catch (Throwable t) {
            Log.e(TAG, "executeAvmCamera error", t);
            return "360 kamera kontrolü sırasında hata: " + t.getMessage();
        }
    }

    /**
     * Elektrikli Bagaj Kapağı (Tailgate / Trunk) Kontrolü
     * @param action "open" veya "close"
     */
    public String executeTrunk(String action) {
        ensureCarInfoReady();
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        boolean isOpen = (action == null || !"close".equalsIgnoreCase(action));

        Log.i(TAG, "executeTrunk: action=" + action);

        try {
            int trunkVal = isOpen ? 1 : 2;
            proxy.sendItemValue(VDEventCarInfo.MODULE_VR, VrID.ID_VR_TRUNK, trunkVal);

            JSONObject slots = new JSONObject();
            slots.put("name", "后备箱");
            sendNativeCheryVrBroadcast("carControl", isOpen ? "OPEN" : "CLOSE", slots);

            return isOpen ? "Bagaj kapağı açılıyor." : "Bagaj kapağı kapatılıyor.";
        } catch (Throwable t) {
            Log.e(TAG, "executeTrunk error", t);
            return "Bagaj kapağı işlemi sırasında hata: " + t.getMessage();
        }
    }

    /**
     * Cam Buğu Çözme (Defrost) & Rezistans Kontrolü
     * @param target "front" (ön cam), "rear" (arka cam/aynalar), "both" (her ikisi)
     * @param power true: aç, false: kapat
     */
    public String executeDefrost(String target, Boolean power) {
        ensureCarInfoReady();
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        boolean isOn = !Boolean.FALSE.equals(power);
        String t = (target != null) ? target.toLowerCase(Locale.ROOT) : "front";

        Log.i(TAG, "executeDefrost: target=" + t + ", power=" + isOn);

        try {
            int val = isOn ? 1 : 0;
            boolean doFront = t.contains("front") || t.contains("ön") || t.contains("on") || t.contains("both") || t.contains("hepsi");
            boolean doRear = t.contains("rear") || t.contains("arka") || t.contains("both") || t.contains("hepsi");

            if (doFront) {
                proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_MAX_FRONT_DEFOREST, val);
                proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_FRONT_GLASS_HEAT, val);
            }
            if (doRear) {
                proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_REAR_DEFROST, val);
            }

            JSONObject slots = new JSONObject();
            slots.put("instype", "AIRCONTROL_DEFROST");
            sendNativeCheryVrBroadcast("airControl", isOn ? "OPEN" : "CLOSE", slots);

            String desc = (doFront && doRear) ? "Ön ve arka cam buğu çözücüler"
                    : (doFront ? "Ön cam buğu çözme" : "Arka cam ve ayna rezistansı");
            return desc + (isOn ? " açıldı." : " kapatıldı.");
        } catch (Throwable t2) {
            Log.e(TAG, "executeDefrost error", t2);
            return "Buğu çözme komutu uygulanırken hata: " + t2.getMessage();
        }
    }

    /**
     * Özel Araç & Klima Fonksiyonları (Hızlı Soğutma, Hızlı Isıtma, Hava Temizleme, İyonlaştırıcı)
     * @param mode "super_cool", "super_heat", "clean_air", "ionizer", "circulation_inner", "circulation_outer"
     */
    public String executeSpecialClimate(String mode) {
        ensureCarInfoReady();
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        String m = (mode != null) ? mode.toLowerCase(Locale.ROOT).trim() : "clean_air";

        Log.i(TAG, "executeSpecialClimate: mode=" + m);

        try {
            if (m.contains("cool") || m.contains("soğut") || m.contains("sogut")) {
                proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_CLM_SUP_COOL, 1);
                return "Hızlı soğutma modu (Super Cool) aktif edildi.";
            } else if (m.contains("heat") || m.contains("ısıt") || m.contains("isit")) {
                proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_CLM_SUP_HEAT, 1);
                return "Hızlı ısıtma modu (Super Heat) aktif edildi.";
            } else if (m.contains("clean") || m.contains("temiz")) {
                proxy.sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_CABIN_AUTO_CLEAN, 1);
                return "Kabin hava temizleme modu (Clean Air) başlatıldı.";
            } else if (m.contains("ion") || m.contains("iyon")) {
                proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_ION_FUNTION, 1);
                return "Negatif iyonlaştırıcı hava tazeleyici aktif edildi.";
            } else if (m.contains("inner") || m.contains("iç")) {
                proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_CIRCULATION_MODE, 1);
                return "İç hava sirkülasyonu aktif edildi.";
            } else if (m.contains("outer") || m.contains("dış") || m.contains("dis")) {
                proxy.sendItemValue(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_CIRCULATION_MODE, 0);
                return "Dış hava sirkülasyon modu aktif edildi.";
            }

            return "Özel iklimlendirme modu uygulandı.";
        } catch (Throwable t) {
            Log.e(TAG, "executeSpecialClimate error", t);
            return "İklimlendirme modu uygulanırken hata: " + t.getMessage();
        }
    }
}
