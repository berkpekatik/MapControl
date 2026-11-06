#!/bin/bash
set -e

# Kaldırma süreci öldürür: önce kamera ve kayıt kapatılır.
echo "📷 Kamera ve kayıt kapatılıyor..."
"$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/stop-cameras.sh"

# Uygulamayı kaldır
echo "📱 Uygulama kaldırılıyor..."
echo "pm uninstall com.mapcontrol" | adb shell

# /data/local/tmp içindeki yüklenen APK'ları temizle
# (wildcard'ı cihaz tarafında çalıştırmak için tek tırnak kullandık)
echo "🧹 Geçici dosyalar temizleniyor..."
echo 'rm -f /data/local/tmp/mapcontrol.apk /data/local/tmp/app-debug.apk /data/local/tmp/*.apk' | adb shell

echo "✅ Uninstall ve tmp temizleme tamamlandı"
