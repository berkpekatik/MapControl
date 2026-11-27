package com.mapcontrol.vehicle;

import android.os.Bundle;

import com.desaysv.ivi.extra.project.carinfo.proxy.Constants;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

/**
 * CarInfo ham okuma. {@code CarInfoProxy.getItemValues} yanıt gelmeyince {@code [0]} döndürür; güç modu için bu
 * "kontak kapalı" ile karışır (güç geçiş tanısında görüldü). Burası yanıt yoksa {@code null} döndürür.
 */
public final class CarInfoRead {
    private CarInfoRead() {
    }

    /** Herhangi bir CarInfo modülünden ham okuma; yanıt yoksa null. */
    public static int[] read(int module, int itemId) {
        try {
            Bundle request = new Bundle();
            request.putInt(Constants.CMD_ID, itemId);
            VDEvent once = VDBus.getDefault().getOnce(new VDEvent(module, request));
            if (once == null || once.getPayload() == null) {
                return null;
            }
            int[] values = once.getPayload().getIntArray(Constants.VALUE);
            return values == null || values.length == 0 ? null : values;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** {@code MODULE_READONLY_INFO} içindeki bir ID; yanıt yoksa ya da okuma hata verirse null. */
    public static int[] readOnly(int itemId) {
        return read(VDEventCarInfo.MODULE_READONLY_INFO, itemId);
    }
}
