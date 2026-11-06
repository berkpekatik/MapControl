package com.mapcontrol.manager;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import com.mapcontrol.trip.PhoneAccess;

/**
 * Web sunucusunu yerel ağda mDNS ile duyurur: {@code _frostnav._tcp}, TXT {@code id=<araç kimliği>}, {@code v=1}.
 * Telefon adresi değişen aracı bu kayıttan (kimliği eşleşenden) bulur; kayıt yetki vermez, bağlantı yine eşleşme
 * anahtarını ister. Sunucu çalıştığı sürece kayıtlıdır.
 */
final class NsdAdvertiser {

    static final String SERVICE_TYPE = "_frostnav._tcp";

    private final Context context;
    private NsdManager.RegistrationListener listener;

    NsdAdvertiser(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Kaydı açar; zaten kayıtlıysa önce kapatır. Hata sunucuyu etkilemez, yalnızca mDNS ile bulunamaz. */
    synchronized void register(int port) {
        unregister();
        NsdManager manager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (manager == null) {
            return;
        }
        String carId = PhoneAccess.carId(context);
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName("MapControl-" + carId.substring(0, 6));
        info.setServiceType(SERVICE_TYPE);
        info.setPort(port);
        info.setAttribute("id", carId);
        info.setAttribute("v", "1");
        NsdManager.RegistrationListener created = new NsdManager.RegistrationListener() {
            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            }

            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            }
        };
        try {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, created);
            listener = created;
        } catch (RuntimeException ignored) {
            // mDNS yok veya izin reddedildi: telefon son adresi, ağ geçidini ve taramayı dener
        }
    }

    synchronized void unregister() {
        NsdManager.RegistrationListener current = listener;
        listener = null;
        if (current == null) {
            return;
        }
        NsdManager manager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (manager == null) {
            return;
        }
        try {
            manager.unregisterService(current);
        } catch (RuntimeException ignored) {
            // zaten kapalı
        }
    }
}
