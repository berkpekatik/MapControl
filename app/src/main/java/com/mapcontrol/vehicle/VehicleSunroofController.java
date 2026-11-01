package com.mapcontrol.vehicle;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.VrID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

/**
 * Cam tavan ve güneşlik/perde mekanizmasını VDBus / CarInfoProxy üzerinden
 * kontrol eden yönetici sınıf.
 */
public final class VehicleSunroofController implements VehicleMetricsRepository.Listener {

    private static final String TAG = "SunroofController";
    private static final Object SUNSHADE_TOKEN = new Object();
    private static final Object SUNROOF_TOKEN = new Object();
    private static volatile VehicleSunroofController instance;

    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean awaitingSunshadeToOpenGlass = false;

    private VehicleSunroofController(Context context) {
        this.appContext = context.getApplicationContext();
    }

    private synchronized void registerMetricsListener() {
        try {
            VehicleMetricsRepository.getInstance(appContext).addListener(this);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to register metrics listener for sunroof controller", t);
        }
    }

    private synchronized void unregisterMetricsListener() {
        try {
            VehicleMetricsRepository.getInstance(appContext).removeListener(this);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to unregister metrics listener for sunroof controller", t);
        }
    }

    @Override
    public void onMetricsUpdated(VehicleMetricsSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        // Perde açıldıktan sonra camın açılmasını bekleyen işlem varsa:
        if (awaitingSunshadeToOpenGlass) {
            if (snapshot.isSunshadeOpen()) {
                Log.i(TAG, "VDBus ONAY: Tavan perdesi tamamen arkaya çekildi! Cam motoru anında devreye sokuluyor...");
                sendSunroofOpenDirect();
                handler.postDelayed(this::sendSunroofOpenDirect, 800);
                handler.postDelayed(this::sendSunroofOpenDirect, 1800);
                awaitingSunshadeToOpenGlass = false;
                unregisterMetricsListener();
            }
        }

        // Cam tavan açıldıysa veya harekete geçtiyse açma darbelerini sonlandır
        if (Boolean.TRUE.equals(snapshot.sunroofOpenOrNull())) {
            handler.removeCallbacksAndMessages(SUNROOF_TOKEN);
            awaitingSunshadeToOpenGlass = false;
            unregisterMetricsListener();
        }
    }

    public static VehicleSunroofController getInstance(Context context) {
        if (instance == null) {
            synchronized (VehicleSunroofController.class) {
                if (instance == null) {
                    instance = new VehicleSunroofController(context);
                }
            }
        }
        return instance;
    }

    private void ensureReady() {
        try {
            VDBus.getDefault().init(appContext);
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            if (!proxy.isServiceConnnected()) {
                proxy.init(appContext);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to init CarInfoProxy for sunroof", t);
        }
    }

    public boolean isConnected() {
        ensureReady();
        return CarInfoProxy.getInstance().isServiceConnnected();
    }

    /**
     * Cam tavanı açar. İsteğe bağlı olarak tavan perdesini de eşzamanlı açar.
     * Chery/Jaecoo araçlarında perde kapalıyken tavan açma komutu verildiğinde
     * araç güvenlik beyni ilk komutta yalnızca perdeyi açıp camı kapalı bıraktığından,
     * perdenin harekete geçmesiyle eşzamanlı olarak cam motorunu tek hamlede devreye sokan
     * çok aşamalı zamanlı açma darbeleri gönderilir (kullanıcı iki kere dokunmak zorunda kalmaz).
     */
    public boolean openSunroof(boolean controlSunshade) {
        ensureReady();
        if (!CarInfoProxy.getInstance().isServiceConnnected()) {
            return false;
        }
        try {
            // Varsa bekleyen kapatma darbelerini iptal et
            handler.removeCallbacksAndMessages(SUNSHADE_TOKEN);
            handler.removeCallbacksAndMessages(SUNROOF_TOKEN);

            VehicleMetricsSnapshot snapshot = VehicleMetricsRepository.getInstance(appContext).getSnapshot();
            boolean isShadeOpen = snapshot != null && snapshot.isSunshadeOpen();

            if (controlSunshade && !isShadeOpen) {
                // Perde kapalı: Perdeyi sonuna kadar açmaya başla ve sona ulaştığında camı aç
                awaitingSunshadeToOpenGlass = true;
                registerMetricsListener();
                openSunshadeOnly();
                // İlk tetik darbesi
                sendSunroofOpenDirect();
                // Perdenin tam açılma süresi 6-8.5 sn sürdüğü için darbeleri 12.5 saniye boyunca yay
                scheduleMultiStageSunroofOpen();
            } else {
                // Perde zaten açık: Cam doğrudan tek hamlede açılır
                awaitingSunshadeToOpenGlass = false;
                unregisterMetricsListener();
                sendSunroofOpenDirect();
                handler.postDelayed(this::sendSunroofOpenDirect, 800);
                handler.postDelayed(this::sendSunroofOpenDirect, 1800);
            }

            Log.d(TAG, "Sent command: Open Sunroof (isShadeOpen=" + isShadeOpen + ", controlSunshade=" + controlSunshade + ")");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Error executing openSunroof", t);
            return false;
        }
    }

    /**
     * Cam tavan açılırken perdenin ardından camın da tekte açılması için zamanlanmış takip darbeleri.
     * Perde kapalıyken araç perdenin baştan sona açılmasını (6-8.5 sn) bekler; bu esnada cam motorunu
     * bloke eder. Perde arkadaki limit anahtarına ulaştığı milisaniyede camı yakalamak için
     * darbeler 12.5 saniyeye kadar düzenli aralıklarla devam eder.
     */
    public void scheduleMultiStageSunroofOpen() {
        handler.removeCallbacksAndMessages(SUNROOF_TOKEN);
        handler.postAtTime(this::sendSunroofOpenDirect, SUNROOF_TOKEN, SystemClock.uptimeMillis() + 1500);
        handler.postAtTime(this::sendSunroofOpenDirect, SUNROOF_TOKEN, SystemClock.uptimeMillis() + 3500);
        handler.postAtTime(this::sendSunroofOpenDirect, SUNROOF_TOKEN, SystemClock.uptimeMillis() + 5500);
        handler.postAtTime(this::sendSunroofOpenDirect, SUNROOF_TOKEN, SystemClock.uptimeMillis() + 7000);
        handler.postAtTime(this::sendSunroofOpenDirect, SUNROOF_TOKEN, SystemClock.uptimeMillis() + 8200);
        handler.postAtTime(this::sendSunroofOpenDirect, SUNROOF_TOKEN, SystemClock.uptimeMillis() + 9500);
        handler.postAtTime(this::sendSunroofOpenDirect, SUNROOF_TOKEN, SystemClock.uptimeMillis() + 11000);
        handler.postAtTime(this::sendSunroofOpenDirect, SUNROOF_TOKEN, SystemClock.uptimeMillis() + 12500);
        handler.postAtTime(() -> {
            awaitingSunshadeToOpenGlass = false;
            unregisterMetricsListener();
        }, SUNROOF_TOKEN, SystemClock.uptimeMillis() + 13500);
    }

    /**
     * Cam tavan açma sinyalini hem CarSetting hem VR modülüne doğrudan yollar.
     */
    public void sendSunroofOpenDirect() {
        try {
            ensureReady();
            if (CarInfoProxy.getInstance().isServiceConnnected()) {
                // OEM SPF Operate: 3 = Aç (Open), 7 = Tavanı ve Perdeyi Tamamen Aç
                CarInfoProxy.getInstance().sendItemValue(
                        VDEventCarInfo.MODULE_CAR_SETTING,
                        CarSettingID.ID_CAR_SPF_OPERATE,
                        3);
                CarInfoProxy.getInstance().sendItemValue(
                        VDEventCarInfo.MODULE_CAR_SETTING,
                        CarSettingID.ID_CAR_SPF_OPERATE,
                        7);

                // Sesli komut / VR modülü üzerinden Aç komutu gönder
                CarInfoProxy.getInstance().sendItemValue(
                        VDEventCarInfo.MODULE_VR,
                        VrID.ID_VR_SRF,
                        1); // 1 = Aç
                Log.d(TAG, "Direct sunroof open pulse sent");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to send direct sunroof open pulse: " + t.getMessage());
        }
    }

    /**
     * Cam tavanı kapatır. İsteğe bağlı olarak tavan perdesini de eşzamanlı kapatır.
     * Chery/Jaecoo araçlarında cam hareket halindeyken tavan perdesi güvenlik nedeniyle
     * yarı açık pozisyonda durdurulduğundan, cam contaya oturduktan sonra perdenin %100
     * tam kapanmasını sağlayan çok aşamalı tamamlama darbeleri gönderilir.
     */
    public boolean closeSunroof(boolean controlSunshade) {
        ensureReady();
        if (!CarInfoProxy.getInstance().isServiceConnnected()) {
            return false;
        }
        try {
            awaitingSunshadeToOpenGlass = false;
            unregisterMetricsListener();
            // Varsa bekleyen açma darbelerini iptal et
            handler.removeCallbacksAndMessages(SUNROOF_TOKEN);
            handler.removeCallbacksAndMessages(SUNSHADE_TOKEN);

            // OEM SPF Operate: 2 = Kapat (Close)
            CarInfoProxy.getInstance().sendItemValue(
                    VDEventCarInfo.MODULE_CAR_SETTING,
                    CarSettingID.ID_CAR_SPF_OPERATE,
                    2);

            // Sesli komut / VR modülü üzerinden de destek komutu gönder
            CarInfoProxy.getInstance().sendItemValue(
                    VDEventCarInfo.MODULE_VR,
                    VrID.ID_VR_SRF,
                    2);

            if (controlSunshade) {
                // 1. İlk anlık kapatma komutu
                sendSunshadeCloseDirect();
                // 2. Cam kapanırken perdenin yarıda takılı kalmasını önleyen çok aşamalı darbeler
                scheduleMultiStageSunshadeClose();
            }
            Log.d(TAG, "Sent command: Close Sunroof (controlSunshade=" + controlSunshade + ")");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Error executing closeSunroof", t);
            return false;
        }
    }

    /**
     * Cam tavan kapanırken perdenin yarıda kalmaması için zamanlanmış takip darbeleri.
     * Chery BCM cam hareketini algıladığında perdeyi durdurur; cam tam kapandıktan (+4-5 sn)
     * sonra gelen darbeler perdeyi %100 tam kapalı konuma çeker.
     */
    public void scheduleMultiStageSunshadeClose() {
        handler.removeCallbacksAndMessages(SUNSHADE_TOKEN);
        // Aşama 1: Cam hareketinin başında perdeyi canlı tut (+1.8 sn)
        handler.postAtTime(this::sendSunshadeCloseDirect, SUNSHADE_TOKEN, SystemClock.uptimeMillis() + 1800);
        // Aşama 2: Cam yarıyı geçerken (+3.8 sn)
        handler.postAtTime(this::sendSunshadeCloseDirect, SUNSHADE_TOKEN, SystemClock.uptimeMillis() + 3800);
        // Aşama 3: Cam contaya oturduktan sonra perdenin kalan yarısını %100 kapat (+5.8 sn)
        handler.postAtTime(this::sendSunshadeCloseDirect, SUNSHADE_TOKEN, SystemClock.uptimeMillis() + 5800);
        // Aşama 4: Son kilitlenme ve sonlandırma darbesi (+8.0 sn)
        handler.postAtTime(this::sendSunshadeCloseDirect, SUNSHADE_TOKEN, SystemClock.uptimeMillis() + 8000);
    }

    /**
     * Perde kapatma sinyalini hem CarSetting hem VR modülüne doğrudan yollar.
     */
    public void sendSunshadeCloseDirect() {
        try {
            ensureReady();
            if (CarInfoProxy.getInstance().isServiceConnnected()) {
                CarInfoProxy.getInstance().sendItemValue(
                        VDEventCarInfo.MODULE_CAR_SETTING,
                        CarSettingID.ID_CAR_SUNSHADE,
                        1); // 1 = Kapalı
                CarInfoProxy.getInstance().sendItemValue(
                        VDEventCarInfo.MODULE_VR,
                        VrID.ID_VR_SUN_SHADE,
                        2); // VR 2 = Kapat
                Log.d(TAG, "Direct sunshade full close pulse sent");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to send direct sunshade close pulse: " + t.getMessage());
        }
    }

    /**
     * Cam tavan hareketini durdurur.
     */
    public boolean stopSunroof() {
        awaitingSunshadeToOpenGlass = false;
        handler.removeCallbacksAndMessages(SUNSHADE_TOKEN);
        handler.removeCallbacksAndMessages(SUNROOF_TOKEN);
        ensureReady();
        if (!CarInfoProxy.getInstance().isServiceConnnected()) {
            return false;
        }
        try {
            CarInfoProxy.getInstance().sendItemValue(
                    VDEventCarInfo.MODULE_VR,
                    VrID.ID_VR_SRF,
                    3); // 3 = Durdur (Stop)
            Log.d(TAG, "Sent command: Stop Sunroof");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Error executing stopSunroof", t);
            return false;
        }
    }

    /**
     * Cam tavanı havalandırma (Tilt / Havalanma) moduna alır.
     */
    public boolean tiltSunroof() {
        handler.removeCallbacksAndMessages(SUNSHADE_TOKEN);
        handler.removeCallbacksAndMessages(SUNROOF_TOKEN);
        ensureReady();
        if (!CarInfoProxy.getInstance().isServiceConnnected()) {
            return false;
        }
        try {
            CarInfoProxy.getInstance().sendItemValue(
                    VDEventCarInfo.MODULE_CAR_SETTING,
                    CarSettingID.ID_CAR_SPF_POSI,
                    12); // 12 = Tilt
            CarInfoProxy.getInstance().sendItemValue(
                    VDEventCarInfo.MODULE_VR,
                    VrID.ID_VR_SRF,
                    4); // 4 = Tilt
            Log.d(TAG, "Sent command: Tilt Sunroof");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Error executing tiltSunroof", t);
            return false;
        }
    }

    /**
     * Yalnızca cam tavan perdesini açar.
     */
    public boolean openSunshadeOnly() {
        handler.removeCallbacksAndMessages(SUNSHADE_TOKEN);
        ensureReady();
        if (!CarInfoProxy.getInstance().isServiceConnnected()) {
            return false;
        }
        try {
            CarInfoProxy.getInstance().sendItemValue(
                    VDEventCarInfo.MODULE_CAR_SETTING,
                    CarSettingID.ID_CAR_SUNSHADE,
                    2); // 2 = Açık
            CarInfoProxy.getInstance().sendItemValue(
                    VDEventCarInfo.MODULE_VR,
                    VrID.ID_VR_SUN_SHADE,
                    1);
            Log.d(TAG, "Sent command: Open Sunshade");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Error executing openSunshadeOnly", t);
            return false;
        }
    }

    /**
     * Yalnızca cam tavan perdesini kapatır.
     * Perdenin havalandırma veya yarı açık kademede takılmaması için takip teyit darbeleri uygular.
     */
    public boolean closeSunshadeOnly() {
        ensureReady();
        if (!CarInfoProxy.getInstance().isServiceConnnected()) {
            return false;
        }
        try {
            sendSunshadeCloseDirect();
            // Perdenin mekanik ara konumda (yarı açık) durmaması için teyit darbeleri
            handler.removeCallbacksAndMessages(SUNSHADE_TOKEN);
            handler.postAtTime(this::sendSunshadeCloseDirect, SUNSHADE_TOKEN, SystemClock.uptimeMillis() + 2500);
            handler.postAtTime(this::sendSunshadeCloseDirect, SUNSHADE_TOKEN, SystemClock.uptimeMillis() + 5000);
            Log.d(TAG, "Sent command: Close Sunshade (Full Close Multi-Stage)");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Error executing closeSunshadeOnly", t);
            return false;
        }
    }
}
