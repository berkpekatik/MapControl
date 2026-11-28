package com.mapcontrol.ai;

import android.content.Context;

import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.client.VDBus;

import org.json.JSONObject;

/**
 * Sesli asistanın (MapControl AI, ayrı uygulama {@code com.mapcontrol.ai}) istediği araç komutunu çalıştırır. Komut adları ve
 * parametreler sunucudaki araç listesiyle aynıdır ({@code adbauth.web} {@code assistant-tools.json}); eşleme MapControl
 * PR #48'deki {@code GeminiApiClient.executeTool}'dan taşındı. Ana iş parçacığında çağırma: bazı komutlar ağa çıkar
 * (hava durumu) ya da bekler (cam hareketi).
 */
public final class AssistantToolDispatcher {
    private final Context context;
    private final VehicleActionExecutor executor;

    public AssistantToolDispatcher(Context context) {
        this.context = context.getApplicationContext();
        this.executor = new VehicleActionExecutor(this.context);
    }

    public VehicleActionExecutor executor() {
        return executor;
    }

    /** Araç servislerine bağlanır (zaten bağlıysa hemen döner). */
    public void prepare() {
        try {
            VDBus.getDefault().init(context);
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            if (!proxy.isServiceConnnected()) {
                proxy.init(context);
            }
            for (int i = 0; i < 5 && !proxy.isServiceConnnected(); i++) {
                Thread.sleep(300);
                proxy.init(context);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable ignored) {
            // Araç servisi olmayan cihazda (emülatör) komutlar kendi hatasını döndürür.
        }
    }

    /** @return sürücüye/modele gidecek kısa sonuç metni */
    public String execute(String name, JSONObject args) {
        if (args == null) {
            args = new JSONObject();
        }
        switch (name) {
            case "control_sunroof":
                return executor.executeSunroof(args.optString("element", "both"), args.optString("action", "open"));
            case "set_child_lock":
                return executor.executeChildLock(args.optBoolean("enable", true));
            case "set_drive_mode":
                return executor.executeDriveMode(args.optString("mode", "normal"));
            case "set_seat_memory":
                return executor.executeSeatMemory(args.optInt("profile", 1), args.optString("action", "recall"));
            case "get_vehicle_metrics":
                return executor.executeGetVehicleTelemetry("all");
            case "get_vehicle_telemetry":
                return executor.executeGetVehicleTelemetry(args.optString("type", "all"));
            case "get_weather":
                return executor.getWeatherSummary(args.optString("city", "Ankara"));
            case "launch_split_screen":
                return executor.executeSplitScreen(optString(args, "left_app"), optString(args, "right_app"));
            case "launch_app":
                return executor.executeLaunchApp(args.optString("app_name", "harita"));
            case "control_window":
                return executor.executeWindow(args.optString("window", "all"), args.optString("action", "open"));
            case "control_climate": {
                Boolean power = has(args, "power") ? args.optBoolean("power") : null;
                Double temp = null;
                if (has(args, "temperature")) {
                    double parsed = args.optDouble("temperature", Double.NaN);
                    if (!Double.isNaN(parsed)) temp = parsed;
                }
                Integer fan = null;
                if (has(args, "fan_level")) {
                    int parsedFan = args.optInt("fan_level", -1);
                    if (parsedFan >= 0) fan = parsedFan;
                }
                return executor.executeClimate(power, temp, fan, optString(args, "circulation"), optString(args, "mode"));
            }
            case "control_seat_climate":
                return executor.executeSeatClimate(args.optString("seat", "driver"), args.optString("action", "heat"),
                        has(args, "level") ? args.optInt("level", 3) : 3);
            case "control_steering_wheel_heat":
                return executor.executeSteeringWheelHeat(!has(args, "power") || args.optBoolean("power", true));
            case "start_navigation":
                return executor.executeStartNavigation(args.optString("destination", ""), args.optString("nav_app", "auto"),
                        args.optBoolean("to_cluster", false));
            case "control_cluster":
                return executor.executeClusterControl(args.optString("action", "open"), optString(args, "app"));
            case "control_dvr":
                return executor.executeDvr(args.optString("action", "start"));
            case "control_ambient_light":
                return executor.executeAmbientLight(optString(args, "color"),
                        has(args, "brightness") ? args.optInt("brightness") : null,
                        has(args, "rhythm") ? args.optBoolean("rhythm") : null,
                        has(args, "power") ? args.optBoolean("power") : null);
            case "control_avm_camera":
                return executor.executeAvmCamera(args.optString("action", "open"));
            case "control_trunk":
                return executor.executeTrunk(args.optString("action", "open"));
            case "control_defrost":
                return executor.executeDefrost(args.optString("target", "front"),
                        has(args, "power") ? args.optBoolean("power") : Boolean.TRUE);
            case "control_special_climate":
                return executor.executeSpecialClimate(args.optString("mode", "clean_air"));
            case "play_media":
                return executor.executePlayMedia(args.optString("query", ""), args.optString("platform", "auto"));
            case "control_media":
                return executor.executeControlMedia(args.optString("action", "play_pause"),
                        has(args, "volume_level") ? args.optInt("volume_level") : null);
            case "search_nearby_places":
                return executor.executeSearchNearbyPlaces(args.optString("query", ""),
                        args.optBoolean("start_navigation", false), args.optString("nav_app", "auto"));
            default:
                return "Bilinmeyen işlem: " + name;
        }
    }

    private static boolean has(JSONObject o, String key) {
        return o.has(key) && !o.isNull(key);
    }

    private static String optString(JSONObject o, String key) {
        return has(o, key) ? o.optString(key, null) : null;
    }
}
