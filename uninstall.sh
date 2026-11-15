#!/bin/bash
set -e

# Kaldırma süreci öldürür: önce kamera ve kayıt kapatılır.
echo "📷 Kamera ve kayıt kapatılıyor..."
"$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/stop-cameras.sh"

# Cihaz sahibi uygulama pm uninstall ile silinemez; önce uygulamaya sahipliği bıraktırılır.
echo "🔓 Cihaz sahipliği bırakılıyor..."
OWNER_OUT=$(adb shell am broadcast -f 0x20 \
    -n com.mapcontrol/.admin.ReleaseOwnerReceiver \
    -a com.mapcontrol.action.RELEASE_DEVICE_OWNER 2>&1 | tr -d '\r')
case "$OWNER_OUT" in
    *"result=1"*) echo "  ✓ Cihaz sahipliği bırakıldı" ;;
    *"result=2"*) echo "  ✓ Cihaz sahibi değil" ;;
    *"result=3"*) echo "  ⚠ Uygulama cihaz sahipliğini bırakamadı" ;;
    *) echo "  ⚠ Yüklü sürüm cihaz sahipliğini bırakamıyor; önce güncelleyin" ;;
esac

# Uygulamayı kaldır
echo "📱 Uygulama kaldırılıyor..."
UNINSTALL_OUT=$(adb shell pm uninstall com.mapcontrol 2>&1 | tr -d '\r')
if ! echo "$UNINSTALL_OUT" | grep -q "Success"; then
    echo "  ✗ Kaldırılamadı: ${UNINSTALL_OUT:-yanıt yok}"
    if echo "$UNINSTALL_OUT" | grep -q "DELETE_FAILED_DEVICE_POLICY_MANAGER"; then
        echo "    Uygulama hâlâ cihaz sahibi. Son sürümü kurup tekrar deneyin."
    fi
    exit 1
fi
echo "  ✓ Uygulama kaldırıldı"

# /data/local/tmp içindeki yüklenen APK'ları temizle
# (wildcard'ı cihaz tarafında çalıştırmak için tek tırnak kullandık)
echo "🧹 Geçici dosyalar temizleniyor..."
echo 'rm -f /data/local/tmp/mapcontrol.apk /data/local/tmp/app-debug.apk /data/local/tmp/*.apk' | adb shell

echo "✅ Uninstall ve tmp temizleme tamamlandı"
