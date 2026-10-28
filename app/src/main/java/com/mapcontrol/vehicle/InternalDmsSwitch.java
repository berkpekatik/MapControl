package com.mapcontrol.vehicle;

import android.content.Context;
import android.util.Log;

import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.client.bind.VDServiceDef;
import com.desaysv.ivi.vdb.event.id.cabin.VDEventCabinLan;
import com.desaysv.ivi.vdb.event.id.cabin.VDValueCabinLan;
import com.desaysv.ivi.vdb.event.id.cabin.bean.VDCLCommonMessage;

import org.json.JSONObject;

/**
 * Stok SVSetting iç DMS anahtarı. Kabin LAN:
 * {@code {"MsgID":"8001","MsgContent":{"DmsCamState":0|1}}}
 * 0 = açık (stok anahtar true), 1 = kapalı (stok anahtar false, CamStateSync 1).
 */
public final class InternalDmsSwitch {
    public static final String TAG = "InternalDmsSwitch";
    public static final int CAM_ON = 0;
    public static final int CAM_OFF = 1;

    private InternalDmsSwitch() {
    }

    public static boolean send(Context context, int camState) throws Exception {
        JSONObject content = new JSONObject();
        content.put("DmsCamState", camState);
        JSONObject root = new JSONObject();
        root.put("MsgID", "8001");
        root.put("MsgContent", content);
        String json = root.toString();

        VDBus bus = VDBus.getDefault();
        if (context != null) {
            bus.init(context.getApplicationContext());
        }
        if (!bus.isServiceConnected(VDServiceDef.ServiceType.CABIN_LAN)) {
            bus.bindService(VDServiceDef.ServiceType.CABIN_LAN);
        }
        boolean connected = bus.isServiceConnected(VDServiceDef.ServiceType.CABIN_LAN);

        VDCLCommonMessage message = new VDCLCommonMessage();
        message.setMsgType(VDValueCabinLan.Cabin_LAN_MSG_Type.COMMON_CHANNEL_TYPE_A2Q);
        message.setSubtype(0);
        message.setMessage(json);
        bus.set(VDCLCommonMessage.createEvent(VDEventCabinLan.CABIN_LAN_MSG_COMMON, message));

        Log.i(TAG, "send connected=" + connected + " json=" + json);
        return connected;
    }
}
