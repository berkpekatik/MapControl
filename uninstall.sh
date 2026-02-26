#!/bin/bash
set -e

# Uygulamayı kaldır
echo "pm uninstall com.mapcontrol" | adb shell

# /data/local/tmp içindeki yüklenen APK'ları temizle
echo "🧹 Geçici dosyalar temizleniyor..."
echo 'rm -f /data/local/tmp/mapcontrol.apk /data/local/tmp/app-debug.apk /data/local/tmp/*.apk' | adb shell

echo "✅ Uninstall ve tmp temizleme tamamlandı"
