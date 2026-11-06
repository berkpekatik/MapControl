#!/bin/bash
# MapControl'a kurulumdan önce kamera ve kaydı kapatmasını söyler ve bitene kadar bekler.
# Kapanamazsa uyarır ama kurulumu engellemez (çıkış kodu her zaman 0).

if [[ -z "$(adb shell pidof com.mapcontrol 2>/dev/null | tr -d '\r')" ]]; then
    echo "  ✓ Uygulama çalışmıyor"
    exit 0
fi

OUT=$(adb shell am broadcast \
    -n com.mapcontrol/.dvr.InstallPrepareReceiver \
    -a com.mapcontrol.action.PREPARE_SELF_INSTALL 2>&1 | tr -d '\r')

case "$OUT" in
    *"result=1"*)
        echo "  ✓ Kamera ve kayıt kapalı"
        ;;
    *"result=2"*)
        echo "  ⚠ Kamera zamanında kapanmadı; kurulum ekranı karartabilir"
        ;;
    *)
        echo "  ⚠ Yüklü sürüm kamerayı kapatamıyor; Kamera 360 açıksa kurulum ekranı karartabilir"
        ;;
esac
exit 0
