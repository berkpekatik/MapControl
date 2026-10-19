package com.desaysv.ivi.extra.project.carinfo.proxy;

import android.content.Context;
import android.os.Bundle;
import com.desaysv.ivi.vdb.IVDBus;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.client.bind.VDServiceDef;
import com.desaysv.ivi.vdb.client.listener.VDBindListener;
import com.desaysv.ivi.vdb.event.VDEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public class CarInfoProxy implements VDBindListener {
    private static volatile CarInfoProxy mInstance;
    private IVDBus mAidl = null;
    private final List<IServiceConnectListener> mConnectListeners = new CopyOnWriteArrayList();
    private Context mContext = null;
    private boolean mIsServiceConnnected = false;

    public static CarInfoProxy getInstance() {
        if (mInstance == null) {
            synchronized (CarInfoProxy.class) {
                try {
                    if (mInstance == null) {
                        mInstance = new CarInfoProxy();
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return mInstance;
    }

    public int getItemValue(int i9, int i10) {
        return getItemValues(i9, i10)[0];
    }

    public int[] getItemValues(int i9, int i10) {
        Bundle payload;
        Bundle bundle = new Bundle();
        bundle.putInt(Constants.CMD_ID, i10);
        VDEvent once = VDBus.getDefault().getOnce(new VDEvent(i9, bundle));
        if (once != null && (payload = once.getPayload()) != null && payload.getIntArray(Constants.VALUE) != null) {
            return payload.getIntArray(Constants.VALUE);
        }
        return new int[]{0};
    }

    public void init(Context context) {
        this.mContext = context;
        VDBus.getDefault().init(context);
        VDBus.getDefault().registerVDBindListener(this);
        VDBus.getDefault().bindService(VDServiceDef.ServiceType.CAR_INFO);
    }

    public boolean isServiceConnnected() {
        return this.mIsServiceConnnected;
    }

    public void onVDConnected(VDServiceDef.ServiceType serviceType) {
        if (serviceType == VDServiceDef.ServiceType.CAR_INFO) {
            this.mIsServiceConnnected = true;
            synchronized (this.mConnectListeners) {
                int i9 = 0;
                while (i9 < this.mConnectListeners.size()) {
                    try {
                        this.mConnectListeners.get(i9).onServiceConnectedChanged(1);
                        i9++;
                    } finally {
                    }
                }
            }
        }
    }

    public void onVDDisconnected(VDServiceDef.ServiceType serviceType) {
        if (serviceType == VDServiceDef.ServiceType.CAR_INFO) {
            this.mIsServiceConnnected = false;
            synchronized (this.mConnectListeners) {
                int i9 = 0;
                while (i9 < this.mConnectListeners.size()) {
                    try {
                        this.mConnectListeners.get(i9).onServiceConnectedChanged(0);
                        i9++;
                    } finally {
                    }
                }
            }
        }
    }

    public void regCallBack(int i9, IVDBusNotify.Stub stub) {
        regCallBack(i9, stub, new int[0]);
    }

    public void regServiceConnectListener(IServiceConnectListener iServiceConnectListener) {
        if (iServiceConnectListener != null) {
            synchronized (this.mConnectListeners) {
                this.mConnectListeners.add(iServiceConnectListener);
            }
        }
    }

    public void sendItemValue(int i9, int i10, int i11) {
        sendItemValues(i9, i10, new int[]{i11});
    }

    public void sendItemValues(int i9, int i10, int[] iArr) {
        Bundle bundle = new Bundle();
        bundle.putInt(Constants.CMD_ID, i10);
        bundle.putIntArray(Constants.VALUE, iArr);
        VDBus.getDefault().set(new VDEvent(i9, bundle));
    }

    public void unRegCallBack(int i9, IVDBusNotify.Stub stub) {
        VDBus.getDefault().unsubscribe(new VDEvent(i9, new Bundle()), stub);
    }

    public void unRegServiceConnectListener(IServiceConnectListener iServiceConnectListener) {
        if (iServiceConnectListener != null) {
            synchronized (this.mConnectListeners) {
                this.mConnectListeners.remove(iServiceConnectListener);
            }
        }
    }

    public void regCallBack(int i9, IVDBusNotify.Stub stub, int[] iArr) {
        Bundle bundle = new Bundle();
        bundle.putIntArray(Constants.CMD_ID_ARRAY, iArr);
        VDBus.getDefault().subscribe(new VDEvent(i9, bundle), stub);
    }
}
