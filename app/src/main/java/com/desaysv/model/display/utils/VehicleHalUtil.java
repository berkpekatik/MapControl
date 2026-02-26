package com.desaysv.model.display.utils;

import android.os.Bundle;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.vehicle.VDKeyVehicleHal;

/* loaded from: classes2.dex */
public class VehicleHalUtil {
    public static void sendItemValues(int i, int[] iArr, int... iArr2) {
        int[] iArr3;
        int i2 = 0;
        if (iArr2.length > 0) {
            iArr3 = new int[iArr2.length + 1 + iArr.length];
            for (int i3 = 0; i3 < iArr2.length; i3++) {
                iArr3[i3] = iArr2[i3];
            }
            iArr3[iArr2.length] = iArr.length;
            while (i2 < iArr.length) {
                iArr3[iArr2.length + 1 + i2] = iArr[i2];
                i2++;
            }
        } else {
            iArr3 = new int[iArr.length];
            while (i2 < iArr.length) {
                iArr3[i2] = iArr[i2];
                i2++;
            }
        }
        sendValues(i, iArr3);
    }

    public static void sendValues(int i, int[] iArr) {
        Bundle bundle = new Bundle();
        bundle.putIntArray(VDKeyVehicleHal.INT_VECTOR, iArr);
        VDBus.getDefault().set(new VDEvent(i, bundle));
    }
}
