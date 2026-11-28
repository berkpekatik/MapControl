package com.mapcontrol.manager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.client.bind.VDServiceDef;
import com.desaysv.ivi.vdb.client.listener.VDBindListener;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;
import com.desaysv.ivi.vdb.event.id.sms.VDEventSms;

public class VDBusManager {
    public interface VDBusCallback {
        /** keyCode 26 + action 4: tek olay — cluster açık/kapalı durumuna göre toggle. */
        void onNavKeyToggle();
        void onAlertTone();
        void log(String message);
        /**
         * keyCode 7 veya 8 + action 1: picker kapalıysa aç; zaten açıksa yok say (kapatmaz).
         */
        void onProjectionTargetPickerToggle();
        /** keyCode 7 + action 4: sağ — picker açıksa bir sonraki öğe. */
        void onProjectionTargetPickerKeyRight();
        /** keyCode 8 + action 4: sol — picker açıksa bir önceki öğe. */
        void onProjectionTargetPickerKeyLeft();
        /** keyCode 6 (direksiyon ses tuşu) kısa bas-bırak: sesli asistan (com.mapcontrol.ai) kuruluysa. */
        void onAssistantKey();
    }

    /** 6: direksiyon ses (VR) tuşu, 10/14: telefon, 26: nav/sessiz, 7/8: medya. */
    private static final int[] VDBUS_KEY_SUBSCRIBE_CODES = new int[]{6, 10, 14, 26, 7, 8};
    private static final int KEY_VOICE = 6;
    private final VoiceKeyGesture voiceKeyGesture = new VoiceKeyGesture();
    /** Harita/medya tuşları ayardan kapatılabilir; ses tuşu (asistan kuruluysa) yine dinlenir. */
    private volatile boolean mapKeysEnabled = true;

    private final Context context;
    private final VDBusCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String TAG = "VDBusManager";

    private final Object vdbusKeyLock = new Object();
    private VDBus vdbusKeyBus;
    private VDEvent vdbusSmsKeyEvent;
    private boolean vdbusKeySubscribed = false;
    private boolean listening = false;

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

            if (keyCode == KEY_VOICE) {
                if (com.mapcontrol.ai.AssistantLauncher.isInstalled(context) && voiceKeyGesture.accept(action)) {
                    mainHandler.post(callback::onAssistantKey);
                }
                return;
            }
            if (!mapKeysEnabled) {
                return;
            }
            if (keyCode == 26 && action == 1) {
                callback.onAlertTone();
            } else if (keyCode == 26 && action == 4) {
                // Ana thread'e taşımayın: openClusterDisplay() içinde bloklayan dumpsys okuması var.
                callback.onNavKeyToggle();
            } else if ((keyCode == 7 || keyCode == 8) && action == 4) {
                mainHandler.post(() -> callback.onProjectionTargetPickerToggle());
            } else if (keyCode == 7 && action == 1) {
                mainHandler.post(() -> callback.onProjectionTargetPickerKeyRight());
            } else if (keyCode == 8 && action == 1) {
                mainHandler.post(() -> callback.onProjectionTargetPickerKeyLeft());
            }
        }
    };

    private final VDBindListener vdbusKeyBindListener = new VDBindListener() {
        @Override
        public void onVDConnected(VDServiceDef.ServiceType serviceType) {
            if (serviceType == VDServiceDef.ServiceType.SMS) {
                callback.log("VDBus key listener: SMS service bağlandı, subscribe ediliyor");
                com.mapcontrol.util.DiagJournal.event("vdbus", "SMS servisi baglandi");
                subscribeToSmsKeyEvents(true);
            }
        }

        @Override
        public void onVDDisconnected(VDServiceDef.ServiceType serviceType) {
            if (serviceType == VDServiceDef.ServiceType.SMS) {
                callback.log("VDBus key listener: SMS service koptu, yeniden bağlanacak");
                com.mapcontrol.util.DiagJournal.event("vdbus", "SMS servisi koptu");
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
            mapKeysEnabled = prefs.getBoolean("mapControlKeyEnabled", true);
            boolean assistant = com.mapcontrol.ai.AssistantLauncher.isInstalled(context);
            if (mapKeysEnabled || assistant) {
                start();
                if (!mapKeysEnabled) {
                    callback.log("Harita kontrol tuşu kapalı; yalnızca sesli asistan tuşu dinleniyor");
                }
            } else {
                callback.log("Harita kontrol tuşu devre dışı bırakılmış, VDBus listener başlatılmadı");
            }
        } catch (Exception e) {
            callback.log("VDBus init hatası: " + e.getMessage());
            com.mapcontrol.util.DiagJournal.event("vdbus", "init hatasi: " + e);
        }
    }

    public void destroy() {
        stop();
    }

    /**
     * SMS eski callback'i düşürmüş olsa da aynı süreç yeni subscribe yapsın.
     * Araç uykusu binder'ı öldürmeden tuş listesini temizleyebiliyor.
     */
    public void refreshSubscription(boolean announce) {
        if (!listening || vdbusKeyBus == null) {
            return;
        }
        synchronized (vdbusKeyLock) {
            if (vdbusKeySubscribed && vdbusSmsKeyEvent != null) {
                try {
                    vdbusKeyBus.unsubscribe(vdbusSmsKeyEvent, vdbusKeyNotify);
                } catch (Throwable t) {
                    Log.d(TAG, "refresh unsubscribe: " + t.getMessage());
                }
            }
            vdbusKeySubscribed = false;
            vdbusSmsKeyEvent = null;
        }
        if (vdbusKeyBus.isServiceConnected(VDServiceDef.ServiceType.SMS)) {
            subscribeToSmsKeyEvents(announce);
        } else {
            try {
                vdbusKeyBus.bindService(VDServiceDef.ServiceType.SMS);
                if (announce) {
                    callback.log("VDBus key listener: SMS kopuk, yeniden bind");
                }
            } catch (Throwable t) {
                callback.log("VDBus key listener: bindService hata: " + t.getMessage());
            }
        }
    }

    public void start() {
        listening = true;
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
            subscribeToSmsKeyEvents(true);
        } else if (vdbusKeyBus != null) {
            try {
                vdbusKeyBus.bindService(VDServiceDef.ServiceType.SMS);
            } catch (Throwable t) {
                callback.log("VDBus key listener bindService hatası: " + t.getMessage());
            }
        }
    }

    public void stop() {
        listening = false;
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

    private void subscribeToSmsKeyEvents(boolean announce) {
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
                if (announce) {
                    callback.log("VDBus key listener: SMS key event subscribe edildi");
                } else {
                    Log.d(TAG, "SMS key event subscribe yenilendi");
                }
            } catch (Throwable t) {
                callback.log("VDBus key listener subscribe hatası: " + t.getMessage());
            }
        }
    }
}
