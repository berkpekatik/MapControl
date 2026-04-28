#!/bin/bash

# Sistem property ayarı
echo "⚙️ Sistem property ayarlanıyor..."
echo "setprop persist.sys.sv.isl true" | adb shell || true

# APK'yı cihaza push et
echo "📦 APK'yı cihaza yüklüyorum..."
adb push /Users/berkpekatik/Downloads/r/MapControl/app/build/outputs/apk/release/app-release.apk /data/local/tmp/mapcontrol.apk

# APK'yı yükle
echo "📱 APK'yı yüklüyorum..."
echo "pm install -r /data/local/tmp/mapcontrol.apk" | adb shell

echo "🔑 İzinler veriliyor..."

# İzin verme fonksiyonu (hata durumunda kısa log)
grant_permission() {
    local cmd="$1"
    local name="$2"
    local result=$(echo "$cmd" | adb shell 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo "  ✓ $name"
    else
        echo "  ⚠ $name (hata)"
    fi
    #sleep 0.3
}

# Doze mode whitelist (arka planda çalışması için)
grant_permission "dumpsys deviceidle whitelist +com.mapcontrol" "Doze whitelist"

# AppOps izinleri
grant_permission "appops set com.mapcontrol SYSTEM_ALERT_WINDOW allow" "SYSTEM_ALERT_WINDOW"
grant_permission "appops set com.mapcontrol GET_USAGE_STATS allow" "GET_USAGE_STATS"
grant_permission "appops set com.mapcontrol REQUEST_INSTALL_PACKAGES allow" "REQUEST_INSTALL_PACKAGES"
grant_permission "appops set com.mapcontrol MANAGE_EXTERNAL_STORAGE allow" "MANAGE_EXTERNAL_STORAGE"
grant_permission "appops set com.mapcontrol ACTIVATE_VPN allow" "ACTIVATE_VPN"
grant_permission "appops set com.mapcontrol WRITE_SETTINGS allow" "WRITE_SETTINGS (AppOps)"

# Sistem geneli overlay (web kurulumu ile aynı)
grant_permission "settings put global overlay_show_system_window 1" "Global overlay_show_system_window"

# pm grant izinleri
grant_permission "pm grant com.mapcontrol android.permission.READ_LOGS" "READ_LOGS"
grant_permission "pm grant com.mapcontrol android.permission.QUERY_ALL_PACKAGES" "QUERY_ALL_PACKAGES"
grant_permission "pm grant com.mapcontrol android.permission.PACKAGE_USAGE_STATS" "PACKAGE_USAGE_STATS"
grant_permission "pm grant com.mapcontrol android.permission.ACCESS_FINE_LOCATION" "ACCESS_FINE_LOCATION"
grant_permission "pm grant com.mapcontrol android.permission.ACCESS_BACKGROUND_LOCATION" "ACCESS_BACKGROUND_LOCATION"
grant_permission "pm grant com.mapcontrol android.permission.WRITE_SETTINGS" "WRITE_SETTINGS"
grant_permission "pm grant com.mapcontrol android.permission.WRITE_SECURE_SETTINGS" "WRITE_SECURE_SETTINGS"
grant_permission "pm grant com.mapcontrol android.permission.READ_SECURE_SETTINGS" "READ_SECURE_SETTINGS"
grant_permission "pm grant com.mapcontrol android.permission.READ_SETTINGS" "READ_SETTINGS"
grant_permission "pm grant com.mapcontrol android.permission.FOREGROUND_SERVICE" "FOREGROUND_SERVICE"
grant_permission "pm grant com.mapcontrol android.permission.RECORD_AUDIO" "RECORD_AUDIO"
grant_permission "pm grant com.mapcontrol android.permission.BLUETOOTH_CONNECT" "BLUETOOTH_CONNECT"
grant_permission "pm grant com.mapcontrol android.permission.ACCESS_WIFI_STATE" "ACCESS_WIFI_STATE"
grant_permission "pm grant com.mapcontrol android.permission.CHANGE_WIFI_STATE" "CHANGE_WIFI_STATE"
grant_permission "pm grant com.mapcontrol android.permission.ACCESS_NETWORK_STATE" "ACCESS_NETWORK_STATE"
grant_permission "pm grant com.mapcontrol android.permission.CHANGE_NETWORK_STATE" "CHANGE_NETWORK_STATE"
grant_permission "pm grant com.mapcontrol android.permission.CAMERA" "CAMERA"
grant_permission "pm grant com.mapcontrol android.permission.INTERNET" "INTERNET"
grant_permission "pm grant com.mapcontrol android.permission.READ_PHONE_STATE" "READ_PHONE_STATE"
grant_permission "pm grant com.mapcontrol android.permission.ACCESS_COARSE_LOCATION" "ACCESS_COARSE_LOCATION"
grant_permission "pm grant com.mapcontrol android.permission.WRITE_EXTERNAL_STORAGE" "WRITE_EXTERNAL_STORAGE"
grant_permission "pm grant com.mapcontrol android.permission.READ_EXTERNAL_STORAGE" "READ_EXTERNAL_STORAGE"
grant_permission "pm grant com.mapcontrol android.permission.RECEIVE_BOOT_COMPLETED" "RECEIVE_BOOT_COMPLETED"
grant_permission "pm grant com.mapcontrol android.permission.INTERACT_ACROSS_USERS_FULL" "INTERACT_ACROSS_USERS_FULL"
grant_permission "pm grant com.mapcontrol android.permission.DELETE_PACKAGES" "DELETE_PACKAGES"
grant_permission "pm grant com.mapcontrol android.permission.BIND_ACCESSIBILITY_SERVICE" "BIND_ACCESSIBILITY_SERVICE"

# AccessibilityService otomatik etkinleştirme
echo "🔧 AccessibilityService etkinleştiriliyor..."
# Dikkat: Sınıf com.mapcontrol.service.GlobalBackService — .GlobalBackService (service olmadan) YANLIŞSIR.
grant_permission "settings put secure enabled_accessibility_services com.mapcontrol/com.mapcontrol.service.GlobalBackService" "AccessibilityService"
grant_permission "cmd accessibility enable-service com.mapcontrol/com.mapcontrol.service.GlobalBackService" "AccessibilityService (cmd)"

# Not: WAKE_LOCK, BROADCAST_STICKY, ACCESS_COARSE_UPDATES, READ_INTERNAL_STORAGE normal permissions, otomatik verilir
echo "✅ Yükleme ve izin verme tamamlandı!"

