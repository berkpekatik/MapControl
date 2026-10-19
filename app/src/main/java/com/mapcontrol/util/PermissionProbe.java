package com.mapcontrol.util;

import android.Manifest;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;

import com.mapcontrol.R;
import com.mapcontrol.nav.GoogleMapsNavNotificationCoordinator;
import com.mapcontrol.service.BootReceiver;
import com.mapcontrol.service.GlobalBackService;
import com.mapcontrol.service.MapControlService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Kurulumun vermeye çalıştığı izinleri, sistem ayarını değiştirmeden dener.
 * Yeşil sonuç gerçek bir API çağrısının geçtiği anlamına gelir.
 */
public final class PermissionProbe {

    public static final class Result {
        public enum Status { OK, FAIL, SKIP }

        public final String name;
        public final Status status;
        public final String detail;

        public Result(String name, Status status, String detail) {
            this.name = name;
            this.status = status;
            this.detail = detail;
        }
    }

    private PermissionProbe() {
    }

    public static List<Result> run(Context anyContext) {
        Context context = anyContext.getApplicationContext();
        List<Result> rows = new ArrayList<Result>();
        rows.add(probeOverlay(context));
        Result usage = probeUsage(context);
        rows.add(named("GET_USAGE_STATS", usage));
        rows.add(probeInstall(context));
        rows.add(probeAllFiles(context));
        rows.add(probeWriteSettings(context));
        rows.add(probeLogcat(context));
        rows.add(probePackages(context));
        rows.add(named("PACKAGE_USAGE_STATS", usage));
        rows.add(probeSecureRead(context));
        rows.add(probeSystemRead(context));
        LocationSample location = readLocation(context);
        rows.add(probeFineLocation(context, location));
        rows.add(probeBackgroundLocation(context));
        rows.add(probeWriteSecure(context));
        rows.add(probeMic(context));
        rows.add(probeBluetooth(context));
        rows.add(probeWifi(context));
        rows.add(probeFlagOnly(context, "CHANGE_WIFI_STATE", Manifest.permission.CHANGE_WIFI_STATE,
                R.string.settings_perm_change_wifi_granted, R.string.settings_perm_change_wifi_denied));
        rows.add(probeNetwork(context));
        rows.add(probeFlagOnly(context, "CHANGE_NETWORK_STATE", Manifest.permission.CHANGE_NETWORK_STATE,
                R.string.settings_perm_change_network_granted, R.string.settings_perm_change_network_denied));
        rows.add(probeCamera(context));
        rows.add(probeInternet(context));
        rows.add(probePhone(context));
        rows.add(probeCoarseLocation(context, location));
        Listing external = listExternal();
        rows.add(probeExternal(context, "WRITE_EXTERNAL_STORAGE",
                Manifest.permission.WRITE_EXTERNAL_STORAGE, external));
        rows.add(probeExternal(context, "READ_EXTERNAL_STORAGE",
                Manifest.permission.READ_EXTERNAL_STORAGE, external));
        rows.add(probePrivileged(context, "INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS_FULL", R.string.settings_perm_reason_users));
        rows.add(probePrivileged(context, "DELETE_PACKAGES",
                Manifest.permission.DELETE_PACKAGES, R.string.settings_perm_reason_delete));
        rows.add(probePrivileged(context, "MEDIA_CONTENT_CONTROL",
                Manifest.permission.MEDIA_CONTENT_CONTROL, R.string.settings_perm_reason_media));
        rows.add(probePrivileged(context, "BIND_ACCESSIBILITY_SERVICE",
                Manifest.permission.BIND_ACCESSIBILITY_SERVICE, R.string.settings_perm_reason_bind_a11y));
        rows.add(probeForeground(context));
        rows.add(probeBoot(context));
        rows.add(probeDeviceOwner(context));
        rows.add(probeAccessibility(context));
        rows.add(probeNotificationListener(context));
        rows.add(probeDoze(context));
        return rows;
    }

    private static Result named(String name, Result source) {
        return new Result(name, source.status, source.detail);
    }

    private static Result probeOverlay(Context context) {
        try {
            if (Settings.canDrawOverlays(context)) {
                return ok("SYSTEM_ALERT_WINDOW", context.getString(R.string.settings_perm_overlay_ok));
            }
            return fail("SYSTEM_ALERT_WINDOW", context.getString(R.string.settings_perm_overlay_fail));
        } catch (Exception e) {
            return fail("SYSTEM_ALERT_WINDOW", errorText(e));
        }
    }

    private static Result probeUsage(Context context) {
        try {
            UsageStatsManager stats = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (stats == null) {
                return fail("GET_USAGE_STATS", context.getString(R.string.settings_perm_usage_fail, "UsageStatsManager null"));
            }
            long now = System.currentTimeMillis();
            List<UsageStats> rows = stats.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, now - TimeUnit.HOURS.toMillis(1), now);
            int count = rows == null ? 0 : rows.size();
            return ok("GET_USAGE_STATS", context.getString(R.string.settings_perm_usage_ok, count));
        } catch (Exception e) {
            return fail("GET_USAGE_STATS", context.getString(R.string.settings_perm_usage_fail, errorText(e)));
        }
    }

    private static Result probeInstall(Context context) {
        boolean can;
        try {
            can = context.getPackageManager().canRequestPackageInstalls();
        } catch (Exception e) {
            return fail("REQUEST_INSTALL_PACKAGES", context.getString(R.string.settings_perm_install_fail, errorText(e)));
        }
        if (!can) {
            return fail("REQUEST_INSTALL_PACKAGES",
                    context.getString(R.string.settings_perm_install_fail, "canRequestPackageInstalls=false"));
        }
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        int sessionId = -1;
        try {
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(context.getPackageName());
            sessionId = installer.createSession(params);
            return ok("REQUEST_INSTALL_PACKAGES", context.getString(R.string.settings_perm_install_ok, sessionId));
        } catch (SecurityException e) {
            return fail("REQUEST_INSTALL_PACKAGES", context.getString(R.string.settings_perm_install_fail, errorText(e)));
        } catch (Exception e) {
            return skip("REQUEST_INSTALL_PACKAGES",
                    context.getString(R.string.settings_perm_install_flag_only, errorText(e)));
        } finally {
            if (sessionId >= 0) {
                try {
                    installer.abandonSession(sessionId);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static Result probeAllFiles(Context context) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return skip("MANAGE_EXTERNAL_STORAGE",
                        context.getString(R.string.settings_perm_storage_legacy, Build.VERSION.SDK_INT));
            }
            boolean manager = Environment.isExternalStorageManager();
            if (!manager) {
                return fail("MANAGE_EXTERNAL_STORAGE", context.getString(R.string.settings_perm_storage_fail));
            }
            String[] names = Environment.getExternalStorageDirectory().list();
            if (names == null) {
                return fail("MANAGE_EXTERNAL_STORAGE",
                        context.getString(R.string.settings_perm_storage_list_fail, yesNo(context, true)));
            }
            return ok("MANAGE_EXTERNAL_STORAGE", context.getString(R.string.settings_perm_storage_ok, names.length));
        } catch (Exception e) {
            return fail("MANAGE_EXTERNAL_STORAGE", errorText(e));
        }
    }

    private static Result probeWriteSettings(Context context) {
        try {
            boolean can = Settings.System.canWrite(context);
            String brightness;
            try {
                brightness = String.valueOf(Settings.System.getInt(
                        context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS));
            } catch (Exception e) {
                brightness = errorText(e);
            }
            if (can) {
                return ok("WRITE_SETTINGS", context.getString(R.string.settings_perm_write_settings_ok, brightness));
            }
            return fail("WRITE_SETTINGS", context.getString(R.string.settings_perm_write_settings_fail, brightness));
        } catch (Exception e) {
            return fail("WRITE_SETTINGS", errorText(e));
        }
    }

    private static Result probeLogcat(Context context) {
        java.lang.Process process = null;
        ExecutorService io = Executors.newFixedThreadPool(2);
        try {
            process = new ProcessBuilder("logcat", "-d", "-t", "5").start();
            final InputStream stdout = process.getInputStream();
            final InputStream stderr = process.getErrorStream();
            Future<String> outFuture = io.submit(() -> readText(stdout, 4000));
            Future<String> errFuture = io.submit(() -> readText(stderr, 800));
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                return fail("READ_LOGS", context.getString(R.string.settings_perm_logcat_fail, "timeout"));
            }
            String out = outFuture.get(1, TimeUnit.SECONDS);
            String err = errFuture.get(1, TimeUnit.SECONDS).trim();
            String errLower = err.toLowerCase();
            if (errLower.contains("permission") || errLower.contains("denied")) {
                return fail("READ_LOGS", context.getString(R.string.settings_perm_logcat_fail, clip(err)));
            }
            int lines = countLines(out);
            int exit = process.exitValue();
            if (exit != 0 && lines == 0) {
                String message = err.isEmpty() ? ("exit " + exit) : clip(err);
                return fail("READ_LOGS", context.getString(R.string.settings_perm_logcat_fail, message));
            }
            return ok("READ_LOGS", context.getString(R.string.settings_perm_logcat_ok, exit, lines));
        } catch (Exception e) {
            return fail("READ_LOGS", context.getString(R.string.settings_perm_logcat_fail, errorText(e)));
        } finally {
            if (process != null) {
                process.destroy();
            }
            io.shutdownNow();
        }
    }

    private static Result probePackages(Context context) {
        try {
            List<ApplicationInfo> apps = context.getPackageManager().getInstalledApplications(0);
            int count = apps == null ? 0 : apps.size();
            if (isGranted(context, Manifest.permission.QUERY_ALL_PACKAGES)) {
                return ok("QUERY_ALL_PACKAGES", context.getString(R.string.settings_perm_packages_ok, count));
            }
            return fail("QUERY_ALL_PACKAGES", context.getString(R.string.settings_perm_packages_filtered, count));
        } catch (Exception e) {
            return fail("QUERY_ALL_PACKAGES", context.getString(R.string.settings_perm_packages_fail, errorText(e)));
        }
    }

    private static Result probeSecureRead(Context context) {
        try {
            String value = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            int length = value == null ? 0 : value.length();
            if (isGranted(context, "android.permission.READ_SECURE_SETTINGS")) {
                return ok("READ_SECURE_SETTINGS", context.getString(R.string.settings_perm_secure_read_ok, length));
            }
            return skip("READ_SECURE_SETTINGS", context.getString(R.string.settings_perm_secure_read_public, length));
        } catch (Exception e) {
            return fail("READ_SECURE_SETTINGS", context.getString(R.string.settings_perm_secure_read_fail, errorText(e)));
        }
    }

    private static Result probeSystemRead(Context context) {
        try {
            Settings.System.getString(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            if (isGranted(context, "android.permission.READ_SETTINGS")) {
                return ok("READ_SETTINGS", context.getString(R.string.settings_perm_settings_read_ok));
            }
            return skip("READ_SETTINGS", context.getString(R.string.settings_perm_settings_read_public));
        } catch (Exception e) {
            return fail("READ_SETTINGS", context.getString(R.string.settings_perm_settings_read_fail, errorText(e)));
        }
    }

    private static Result probeFineLocation(Context context, LocationSample sample) {
        return locationRow(context, "ACCESS_FINE_LOCATION", Manifest.permission.ACCESS_FINE_LOCATION, sample);
    }

    private static Result probeCoarseLocation(Context context, LocationSample sample) {
        return locationRow(context, "ACCESS_COARSE_LOCATION", Manifest.permission.ACCESS_COARSE_LOCATION, sample);
    }

    private static Result locationRow(Context context, String name, String permission, LocationSample sample) {
        if (!isGranted(context, permission)) {
            return fail(name, context.getString(R.string.settings_perm_location_denied));
        }
        if (sample.error != null) {
            return fail(name, context.getString(R.string.settings_perm_location_fail, sample.error));
        }
        if (sample.location == null) {
            return ok(name, context.getString(R.string.settings_perm_location_ok_empty));
        }
        String provider = sample.location.getProvider() == null ? "?" : sample.location.getProvider();
        return ok(name, context.getString(R.string.settings_perm_location_ok_fix, provider));
    }

    private static LocationSample readLocation(Context context) {
        LocationSample sample = new LocationSample();
        if (!isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
                && !isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return sample;
        }
        try {
            LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (manager == null) {
                sample.error = "LocationManager null";
                return sample;
            }
            String[] providers = new String[] {
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
            };
            for (String provider : providers) {
                try {
                    Location location = manager.getLastKnownLocation(provider);
                    if (location != null) {
                        sample.location = location;
                        return sample;
                    }
                } catch (SecurityException e) {
                    sample.error = errorText(e);
                    return sample;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            sample.error = errorText(e);
        }
        return sample;
    }

    private static Result probeBackgroundLocation(Context context) {
        if (isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            return skip("ACCESS_BACKGROUND_LOCATION",
                    context.getString(R.string.settings_perm_background_location_granted));
        }
        return fail("ACCESS_BACKGROUND_LOCATION",
                context.getString(R.string.settings_perm_background_location_denied));
    }

    private static Result probeWriteSecure(Context context) {
        if (isGranted(context, Manifest.permission.WRITE_SECURE_SETTINGS)) {
            return skip("WRITE_SECURE_SETTINGS", context.getString(R.string.settings_perm_write_secure_granted));
        }
        return fail("WRITE_SECURE_SETTINGS", context.getString(R.string.settings_perm_write_secure_denied));
    }

    private static Result probeMic(Context context) {
        if (!isGranted(context, Manifest.permission.RECORD_AUDIO)) {
            return fail("RECORD_AUDIO", context.getString(R.string.settings_perm_mic_denied));
        }
        int min = AudioRecord.getMinBufferSize(
                16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) {
            return fail("RECORD_AUDIO", context.getString(R.string.settings_perm_mic_fail, "minBuffer=" + min));
        }
        AudioRecord record = null;
        try {
            record = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    min);
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                return fail("RECORD_AUDIO", context.getString(R.string.settings_perm_mic_fail, "STATE_UNINITIALIZED"));
            }
            record.startRecording();
            int state = record.getRecordingState();
            record.stop();
            if (state == AudioRecord.RECORDSTATE_RECORDING) {
                return ok("RECORD_AUDIO", context.getString(R.string.settings_perm_mic_ok));
            }
            return fail("RECORD_AUDIO", context.getString(R.string.settings_perm_mic_fail, "recordingState=" + state));
        } catch (Exception e) {
            return fail("RECORD_AUDIO", context.getString(R.string.settings_perm_mic_fail, errorText(e)));
        } finally {
            if (record != null) {
                record.release();
            }
        }
    }

    private static Result probeBluetooth(Context context) {
        try {
            BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null) {
                return skip("BLUETOOTH_CONNECT", context.getString(R.string.settings_perm_bt_missing));
            }
            int bonded = adapter.getBondedDevices() == null ? 0 : adapter.getBondedDevices().size();
            return ok("BLUETOOTH_CONNECT", context.getString(R.string.settings_perm_bt_ok, bonded));
        } catch (Exception e) {
            return fail("BLUETOOTH_CONNECT", context.getString(R.string.settings_perm_bt_fail, errorText(e)));
        }
    }

    @SuppressWarnings("deprecation")
    private static Result probeWifi(Context context) {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) {
                return fail("ACCESS_WIFI_STATE", context.getString(R.string.settings_perm_wifi_fail, "WifiManager null"));
            }
            WifiInfo info = wifi.getConnectionInfo();
            boolean hasId = info != null && info.getNetworkId() != -1;
            return ok("ACCESS_WIFI_STATE", context.getString(
                    R.string.settings_perm_wifi_ok, wifi.getWifiState(), yesNo(context, hasId)));
        } catch (Exception e) {
            return fail("ACCESS_WIFI_STATE", context.getString(R.string.settings_perm_wifi_fail, errorText(e)));
        }
    }

    private static Result probeNetwork(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                return fail("ACCESS_NETWORK_STATE",
                        context.getString(R.string.settings_perm_network_fail, "ConnectivityManager null"));
            }
            Network network = manager.getActiveNetwork();
            if (network == null) {
                return ok("ACCESS_NETWORK_STATE", context.getString(
                        R.string.settings_perm_network_ok, context.getString(R.string.settings_perm_network_none)));
            }
            NetworkCapabilities caps = manager.getNetworkCapabilities(network);
            String transport = context.getString(R.string.settings_perm_network_none);
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    transport = context.getString(R.string.settings_perm_network_transport, "WIFI");
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    transport = context.getString(R.string.settings_perm_network_transport, "CELLULAR");
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    transport = context.getString(R.string.settings_perm_network_transport, "ETHERNET");
                } else {
                    transport = context.getString(R.string.settings_perm_network_transport, "OTHER");
                }
            }
            return ok("ACCESS_NETWORK_STATE", context.getString(R.string.settings_perm_network_ok, transport));
        } catch (Exception e) {
            return fail("ACCESS_NETWORK_STATE", context.getString(R.string.settings_perm_network_fail, errorText(e)));
        }
    }

    private static Result probeCamera(Context context) {
        int cameras = 0;
        try {
            android.hardware.camera2.CameraManager camerasManager =
                    (android.hardware.camera2.CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (camerasManager != null) {
                String[] ids = camerasManager.getCameraIdList();
                cameras = ids == null ? 0 : ids.length;
            }
        } catch (Exception ignored) {
            cameras = -1;
        }
        if (isGranted(context, Manifest.permission.CAMERA)) {
            return skip("CAMERA", context.getString(R.string.settings_perm_camera_granted, cameras));
        }
        return fail("CAMERA", context.getString(R.string.settings_perm_camera_denied, cameras));
    }

    private static Result probeInternet(Context context) {
        if (isGranted(context, Manifest.permission.INTERNET)) {
            return skip("INTERNET", context.getString(R.string.settings_perm_internet_granted));
        }
        return fail("INTERNET", context.getString(R.string.settings_perm_internet_denied));
    }

    private static Result probePhone(Context context) {
        if (!isGranted(context, Manifest.permission.READ_PHONE_STATE)) {
            return fail("READ_PHONE_STATE", context.getString(R.string.settings_perm_phone_denied));
        }
        try {
            TelephonyManager telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephony == null) {
                return fail("READ_PHONE_STATE", context.getString(R.string.settings_perm_phone_fail, "TelephonyManager null"));
            }
            int type = telephony.getDataNetworkType();
            return ok("READ_PHONE_STATE", context.getString(R.string.settings_perm_phone_ok, type));
        } catch (Exception e) {
            return fail("READ_PHONE_STATE", context.getString(R.string.settings_perm_phone_fail, errorText(e)));
        }
    }

    private static Result probeExternal(Context context, String name, String permission, Listing listing) {
        if (!isGranted(context, permission) && listing.error != null) {
            return fail(name, context.getString(R.string.settings_perm_external_fail, name, listing.error));
        }
        if (listing.error != null) {
            return fail(name, context.getString(R.string.settings_perm_external_fail, name, listing.error));
        }
        if (!isGranted(context, permission)) {
            return fail(name, context.getString(
                    R.string.settings_perm_external_fail, name, "checkPermission denied, entries=" + listing.count));
        }
        return ok(name, context.getString(R.string.settings_perm_external_ok, name, listing.count));
    }

    private static Listing listExternal() {
        Listing listing = new Listing();
        try {
            String[] names = Environment.getExternalStorageDirectory().list();
            if (names == null) {
                listing.error = "list()=null";
            } else {
                listing.count = names.length;
            }
        } catch (Exception e) {
            listing.error = errorText(e);
        }
        return listing;
    }

    private static Result probeFlagOnly(Context context, String name, String permission, int grantedText, int deniedText) {
        if (isGranted(context, permission)) {
            return skip(name, context.getString(grantedText));
        }
        return fail(name, context.getString(deniedText));
    }

    private static Result probePrivileged(Context context, String name, String permission, int reason) {
        String note = context.getString(reason);
        if (isGranted(context, permission)) {
            return skip(name, context.getString(R.string.settings_perm_privileged_granted, note));
        }
        return fail(name, context.getString(R.string.settings_perm_privileged_denied, note));
    }

    @SuppressWarnings("deprecation")
    private static Result probeForeground(Context context) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) {
                return fail("FOREGROUND_SERVICE", context.getString(R.string.settings_perm_fg_fail, "ActivityManager null"));
            }
            List<ActivityManager.RunningServiceInfo> running = manager.getRunningServices(64);
            if (running != null) {
                String expected = MapControlService.class.getName();
                for (ActivityManager.RunningServiceInfo info : running) {
                    if (info.service != null && expected.equals(info.service.getClassName())) {
                        return ok("FOREGROUND_SERVICE", context.getString(
                                R.string.settings_perm_fg_ok, yesNo(context, info.foreground)));
                    }
                }
            }
            return fail("FOREGROUND_SERVICE", context.getString(R.string.settings_perm_fg_down));
        } catch (Exception e) {
            return fail("FOREGROUND_SERVICE", context.getString(R.string.settings_perm_fg_fail, errorText(e)));
        }
    }

    private static Result probeBoot(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE);
        boolean on = prefs.getBoolean(BootReceiver.KEY_BOOT_AUTO_START, true);
        String state = context.getString(on ? R.string.settings_perm_on : R.string.settings_perm_off);
        if (isGranted(context, Manifest.permission.RECEIVE_BOOT_COMPLETED)) {
            return skip("RECEIVE_BOOT_COMPLETED", context.getString(R.string.settings_perm_boot_granted, state));
        }
        return fail("RECEIVE_BOOT_COMPLETED", context.getString(R.string.settings_perm_boot_denied, state));
    }

    private static Result probeDeviceOwner(Context context) {
        try {
            DevicePolicyManager manager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (manager != null && manager.isDeviceOwnerApp(context.getPackageName())) {
                return ok("DEVICE_OWNER", context.getString(R.string.settings_perm_owner_ok));
            }
            return fail("DEVICE_OWNER", context.getString(R.string.settings_perm_owner_fail));
        } catch (Exception e) {
            return fail("DEVICE_OWNER", context.getString(R.string.settings_perm_owner_error, errorText(e)));
        }
    }

    private static Result probeAccessibility(Context context) {
        boolean registered = GlobalBackService.isRegisteredInSystemAccessibilitySettings(context);
        boolean connected = GlobalBackService.isServiceEnabled();
        if (registered && connected) {
            return ok("ACCESSIBILITY", context.getString(R.string.settings_perm_a11y_ok));
        }
        if (registered) {
            return fail("ACCESSIBILITY", context.getString(R.string.settings_perm_a11y_not_bound));
        }
        return fail("ACCESSIBILITY", context.getString(R.string.settings_perm_a11y_missing));
    }

    private static Result probeNotificationListener(Context context) {
        boolean enabled = false;
        try {
            String flat = Settings.Secure.getString(
                    context.getContentResolver(), "enabled_notification_listeners");
            enabled = flat != null && flat.contains("LauncherMediaNotificationListener");
        } catch (Exception e) {
            return fail("NOTIFICATION_LISTENER", errorText(e));
        }
        boolean bound = GoogleMapsNavNotificationCoordinator.isListenerBound();
        if (enabled && bound) {
            return ok("NOTIFICATION_LISTENER", context.getString(R.string.settings_perm_nls_ok));
        }
        if (enabled) {
            return fail("NOTIFICATION_LISTENER", context.getString(R.string.settings_perm_nls_not_bound));
        }
        return fail("NOTIFICATION_LISTENER", context.getString(R.string.settings_perm_nls_missing));
    }

    private static Result probeDoze(Context context) {
        try {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (power != null && power.isIgnoringBatteryOptimizations(context.getPackageName())) {
                return ok("DOZE_WHITELIST", context.getString(R.string.settings_perm_doze_ok));
            }
            return fail("DOZE_WHITELIST", context.getString(R.string.settings_perm_doze_fail));
        } catch (Exception e) {
            return fail("DOZE_WHITELIST", errorText(e));
        }
    }

    private static boolean isGranted(Context context, String permission) {
        try {
            return context.checkPermission(permission, android.os.Process.myPid(), android.os.Process.myUid())
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Result ok(String name, String detail) {
        return new Result(name, Result.Status.OK, detail);
    }

    private static Result fail(String name, String detail) {
        return new Result(name, Result.Status.FAIL, detail);
    }

    private static Result skip(String name, String detail) {
        return new Result(name, Result.Status.SKIP, detail);
    }

    private static String yesNo(Context context, boolean value) {
        return context.getString(value ? R.string.settings_perm_yes : R.string.settings_perm_no);
    }

    private static String errorText(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return error.getClass().getSimpleName() + ": " + clip(message.trim());
    }

    private static String clip(String value) {
        if (value.length() <= 180) {
            return value;
        }
        return value.substring(0, 180);
    }

    private static int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int lines = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        if (text.charAt(text.length() - 1) != '\n') {
            lines++;
        }
        return lines;
    }

    private static String readText(InputStream in, int max) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[512];
        int total = 0;
        while (total < max) {
            int n = in.read(buf, 0, Math.min(buf.length, max - total));
            if (n < 0) {
                break;
            }
            bos.write(buf, 0, n);
            total += n;
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static final class LocationSample {
        Location location;
        String error;
    }

    private static final class Listing {
        int count;
        String error;
    }
}
