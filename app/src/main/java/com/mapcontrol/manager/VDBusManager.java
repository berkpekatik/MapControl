package com.mapcontrol.manager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.client.bind.VDServiceDef;
import com.desaysv.ivi.vdb.client.listener.VDBindListener;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;
import com.desaysv.ivi.vdb.event.id.sms.VDEventSms;
import com.mapcontrol.ui.activity.MainActivity;

public class VDBusManager {
    public interface VDBusCallback {
        void onNavKeyOpen();
        void onNavKeyClose();
        void onAlertTone();
        void log(String message);
    }

    private static final int[] VDBUS_KEY_SUBSCRIBE_CODES = new int[]{10, 14, 26};

    private final Context context;
    private final VDBusCallback callback;

    private final Object vdbusKeyLock = new Object();
    private VDBus vdbusKeyBus;
    private VDEvent vdbusSmsKeyEvent;
    private boolean vdbusKeySubscribed = false;

    private final IVDBusNotify.Stub vdbusKeyNotify = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (event == null || event.getId() != VDEventSms.ID_SMS_KEY_EVENT) {
                return;
            }
            Bundle payload = event.getPayload();
            if (payload == null) {
                return;
            }
            int keyCode = payload.getInt(VDKey.TYPE, -1);
            int action = payload.getInt(VDKey.ACTION, -1);
            callback.log("VDBus KeyEvent: keyCode=" + keyCode + " action=" + action);

            if (keyCode == 26 && action == 1) {
                callback.onAlertTone();
            }

            if (keyCode == 26 && action == 3) {
                callback.log("VDBus NAV key tespit edildi (keyCode=26, action=3)");
                // isNavigationOpen durumu buraya enjekte edilmez.
                // MainActivity callback içinde kendi state'ine göre open/close kararını verir.
                callback.onNavKeyOpen();
                callback.onNavKeyClose();
            }
        }
    };

    private final VDBindListener vdbusKeyBindListener = new VDBindListener() {
        @Override
        public void onVDConnected(VDServiceDef.ServiceType serviceType) {
            if (serviceType == VDServiceDef.ServiceType.SMS) {
                callback.log("VDBus key listener: SMS service bağlandı, subscribe ediliyor");
                subscribeToSmsKeyEvents();
            }
        }

        @Override
        public void onVDDisconnected(VDServiceDef.ServiceType serviceType) {
            if (serviceType == VDServiceDef.ServiceType.SMS) {
                callback.log("VDBus key listener: SMS service koptu, yeniden bağlanacak");
                synchronized (vdbusKeyLock) {
                    vdbusKeySubscribed = false;
                    vdbusSmsKeyEvent = null;
                }
                if (vdbusKeyBus != null) {
                    try {
                        vdbusKeyBus.bindService(VDServiceDef.ServiceType.SMS);
                    } catch (Throwable t) {
                        callback.log("VDBus key listener: bindService hata: " + t.getMessage());
                    }
                }
            }
        }
    };

    public VDBusManager(Context context, VDBusCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public void init() {
        try {
            VDBus.getDefault().init(context.getApplicationContext());
            callback.log("VDBus başlatıldı");

            SharedPreferences prefs = context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
            boolean mapControlKeyEnabled = prefs.getBoolean("mapControlKeyEnabled", true);
            if (mapControlKeyEnabled) {
                start();
            } else {
                callback.log("Harita kontrol tuşu devre dışı bırakılmış, VDBus listener başlatılmadı");
            }
        } catch (Exception e) {
            callback.log("VDBus init hatası: " + e.getMessage());
        }
    }

    public void destroy() {
        stop();
    }

    public void start() {
        try {
            if (vdbusKeyBus == null) {
                vdbusKeyBus = VDBus.getDefault();
            }
            if (vdbusKeyBus == null) {
                callback.log("VDBus key listener: VDBus null döndü");
                return;
            }
            vdbusKeyBus.init(context.getApplicationContext());
        } catch (Exception e) {
            callback.log("VDBus key listener init hatası: " + e.getMessage());
        }

        try {
            vdbusKeyBus.registerVDBindListener(vdbusKeyBindListener);
        } catch (Exception e) {
            callback.log("VDBus key listener bindListener kayıt hatası: " + e.getMessage());
        }

        if (vdbusKeyBus != null && vdbusKeyBus.isServiceConnected(VDServiceDef.ServiceType.SMS)) {
            subscribeToSmsKeyEvents();
        } else if (vdbusKeyBus != null) {
            try {
                vdbusKeyBus.bindService(VDServiceDef.ServiceType.SMS);
            } catch (Throwable t) {
                callback.log("VDBus key listener bindService hatası: " + t.getMessage());
            }
        }
    }

    public void stop() {
        synchronized (vdbusKeyLock) {
            if (!vdbusKeySubscribed || vdbusSmsKeyEvent == null || vdbusKeyBus == null) {
                vdbusKeySubscribed = false;
                vdbusSmsKeyEvent = null;
                return;
            }
            try {
                vdbusKeyBus.unsubscribe(vdbusSmsKeyEvent, vdbusKeyNotify);
                callback.log("VDBus key listener: unsubscribe edildi");
            } catch (Throwable t) {
                callback.log("VDBus key listener unsubscribe hatası: " + t.getMessage());
            } finally {
                vdbusKeySubscribed = false;
                vdbusSmsKeyEvent = null;
            }
        }

        if (vdbusKeyBus != null) {
            try {
                vdbusKeyBus.unregisterVDBindListener(vdbusKeyBindListener);
            } catch (Throwable t) {
                // ignore
            }
        }
    }

    private void subscribeToSmsKeyEvents() {
        if (vdbusKeyBus == null) {
            return;
        }
        synchronized (vdbusKeyLock) {
            if (vdbusKeySubscribed) {
                return;
            }
            try {
                Bundle bundle = new Bundle();
                bundle.putIntArray(VDKey.TYPE, VDBUS_KEY_SUBSCRIBE_CODES);
                vdbusSmsKeyEvent = new VDEvent(VDEventSms.ID_SMS_KEY_EVENT, bundle);
                vdbusKeyBus.subscribe(vdbusSmsKeyEvent, vdbusKeyNotify);
                vdbusKeySubscribed = true;
                callback.log("VDBus key listener: SMS key event subscribe edildi");
            } catch (Throwable t) {
                callback.log("VDBus key listener subscribe hatası: " + t.getMessage());
            }
        }
    }
}
