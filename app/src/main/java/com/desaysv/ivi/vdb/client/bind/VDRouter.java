package com.desaysv.ivi.vdb.client.bind;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.client.bind.VDServiceDef;
import com.desaysv.ivi.vdb.client.listener.VDBindListener;
import com.desaysv.ivi.vdb.client.listener.VDCallbackListener;
import com.desaysv.ivi.vdb.client.listener.VDGetListListener;
import com.desaysv.ivi.vdb.client.listener.VDGetListener;
import com.desaysv.ivi.vdb.client.listener.VDNotifyListener;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public class VDRouter {
    private static final String TAG = "VDRouter";
    private final int CORE_POOL_SIZE;
    private final int CPU_COUNT;
    private final int KEEP_ALIVE;
    private final int MAXIMUM_POOL_SIZE;
    private final int MAX_DEBOUNCE_NUM = 10;
    private final int MSG_DISPATCH_GET_LIST_EVENT = 1000;
    private Context mContext = null;
    private VDConnector mCurVDConnector = null;
    private Handler mDebounceHandler;
    private ArrayList<VDEvent> mDebounceList = new ArrayList<>();
    private boolean mIsInited = false;
    private Map<GetListListenerInfo, ArrayList<VDEvent>> mListenerMap = new HashMap();
    private Map<GetListListenerInfo, ArrayList<VDEvent>> mListenerMapClone = new HashMap();
    private Handler mMainThreadHandler;
    private ThreadPoolExecutor mThreadPoolExecutor;
    private ArrayList<VDConnector> mVDConnectorList = new ArrayList<>();

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public class GetListListenerInfo {
        public VDGetListListener listener;
        public int threadType;

        public GetListListenerInfo(VDGetListListener vDGetListListener, int i9) {
            this.listener = vDGetListListener;
            this.threadType = i9;
        }
    }

    public VDRouter() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        this.CPU_COUNT = availableProcessors;
        this.CORE_POOL_SIZE = availableProcessors + 1;
        this.MAXIMUM_POOL_SIZE = (availableProcessors * 2) + 1;
        this.KEEP_ALIVE = 1;
        Log.d(TAG, "VDRouter created, CPU_COUNT=" + this.CPU_COUNT + " CORE_POOL_SIZE=" + this.CORE_POOL_SIZE + " MAXIMUM_POOL_SIZE=" + this.MAXIMUM_POOL_SIZE);
        this.mMainThreadHandler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(Message message) {
                super.handleMessage(message);
                if (message.what == 1000) {
                    Iterator it = ((Map) message.obj).entrySet().iterator();
                    if (it.hasNext()) {
                        Map.Entry entry = (Map.Entry) it.next();
                        ((GetListListenerInfo) entry.getKey()).listener.onVDGetList((ArrayList) entry.getValue(), VDThreadType.MAIN_THREAD);
                    }
                }
            }
        };
        this.mDebounceHandler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(Message message) {
                super.handleMessage(message);
                VDEvent vDEvent = (VDEvent) message.obj;
                int i9 = message.arg1;
                Log.d(TAG, "Debounce handler: eventId=" + vDEvent.getId() + " onceFlag=" + i9);
                VDConnector connector = VDRouter.this.getConnector(vDEvent);
                if (connector == null) {
                    return;
                }
                if (i9 == 0) {
                    connector.set(vDEvent);
                } else {
                    connector.setOnce(vDEvent);
                }
            }
        };
    }

    private boolean dealDebounce(com.desaysv.ivi.vdb.event.VDEvent event, int debounceMs, int onceFlag) {
        if (debounceMs <= 0) {
            Log.d(TAG, "dealDebounce: debounceMs<=0, will be sent directly. eventId=" + event.getId());
            return true;
        }

        synchronized (this.mDebounceList) {
            for (int i = 0; i < mDebounceList.size(); i++) {
                VDEvent last = mDebounceList.get(i);
                if (last.getId() == event.getId()) {
                    mDebounceHandler.removeMessages(event.getId());

                    long diff = Math.abs(event.getTimeMillis() - last.getTimeMillis());
                    if (diff < (long) debounceMs) {
                        Log.d(TAG, "dealDebounce: event repeated, debounce applied. eventId=" + event.getId() + " diff=" + diff + "ms debounceMs=" + debounceMs);
                        Message msg = mDebounceHandler.obtainMessage();
                        msg.what = event.getId();
                        msg.obj = event;
                        msg.arg1 = onceFlag; 
                        mDebounceHandler.sendMessageDelayed(msg, debounceMs);
                        return false;
                    } else {
                        mDebounceList.remove(i);
                        break;
                    }
                }
            }

            mDebounceList.add(0, event);
            Log.d(TAG, "dealDebounce: event added to list, eventId=" + event.getId() + " size=" + mDebounceList.size());
            if (mDebounceList.size() > MAX_DEBOUNCE_NUM) {
                mDebounceList.remove(MAX_DEBOUNCE_NUM);
            }
        }

        return true;
    }

    private boolean isMainThread() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            return true;
        }
        return false;
    }

    public void addSubscribe(VDEvent vDEvent, int i9) {
        if (!VDValue.isNullEvent(vDEvent)) {
            vDEvent.setThreadType(i9);
            Log.d(TAG, "addSubscribe: eventId=" + vDEvent.getId() + " threadType=" + i9);
            VDConnector connector = getConnector(vDEvent);
            if (connector != null) {
                connector.addSubscribe(vDEvent);
            }
        }
    }

    public boolean bindService(VDServiceDef.ServiceType serviceType) {
        VDConnector connector = getConnector(serviceType);
        Log.d(TAG, "bindService: serviceType=" + serviceType + " connector=" + connector);
        if (connector != null) {
            return connector.bindService();
        }
        return false;
    }

    public void dealGetListCallback(VDEvent vDEvent) {
        HashMap<GetListListenerInfo, ArrayList<VDEvent>> hashMap = new HashMap<>();
        synchronized (this.mListenerMap) {
            try {
                Iterator<Map.Entry<GetListListenerInfo, ArrayList<VDEvent>>> it = this.mListenerMap.entrySet().iterator();
                Iterator<Map.Entry<GetListListenerInfo, ArrayList<VDEvent>>> it2 = this.mListenerMapClone.entrySet().iterator();
                while (it2.hasNext() && it.hasNext()) {
                    Map.Entry<GetListListenerInfo, ArrayList<VDEvent>> next = it.next();
                    ArrayList<VDEvent> arrayList = next.getValue();
                    ArrayList<VDEvent> arrayList2 = it2.next().getValue();
                    int size = arrayList.size();
                    int i9 = 0;
                    int i10 = 0;
                    while (i10 < size) {
                        Object obj = arrayList.get(i10);
                        i10++;
                        VDEvent vDEvent2 = (VDEvent) obj;
                        if (vDEvent2.getId() == vDEvent.getId()) {
                            vDEvent2.setPayload(vDEvent.getPayload());
                        }
                    }
                    while (i9 < arrayList2.size()) {
                        if (arrayList2.get(i9).getId() == vDEvent.getId()) {
                            arrayList2.remove(i9);
                            i9--;
                        }
                        i9++;
                    }
                    if (arrayList2.size() <= 0) {
                        hashMap.put(next.getKey(), arrayList);
                        it.remove();
                        it2.remove();
                    }
                }
            } finally {
            }
        }
        for (Map.Entry<GetListListenerInfo, ArrayList<VDEvent>> entry : hashMap.entrySet()) {
            final GetListListenerInfo getListListenerInfo = entry.getKey();
            final ArrayList<VDEvent> arrayList3 = entry.getValue();
            int i11 = getListListenerInfo.threadType;
            if (i11 == VDThreadType.MAIN_THREAD || i11 == VDThreadType.MAIN_AND_CHILD_THREAD) {
                if (isMainThread()) {
                    getListListenerInfo.listener.onVDGetList(arrayList3, VDThreadType.MAIN_THREAD);
                } else {
                    HashMap<GetListListenerInfo, ArrayList<VDEvent>> hashMap2 = new HashMap<>();
                    hashMap2.put(getListListenerInfo, arrayList3);
                    Message obtainMessage = this.mMainThreadHandler.obtainMessage();
                    obtainMessage.what = 1000;
                    obtainMessage.obj = hashMap2;
                    this.mMainThreadHandler.sendMessage(obtainMessage);
                }
            }
            int i12 = getListListenerInfo.threadType;
            if (i12 == VDThreadType.CHILD_THREAD || i12 == VDThreadType.MAIN_AND_CHILD_THREAD) {
                this.mThreadPoolExecutor.execute(new Runnable() {
                    public void run() {
                        getListListenerInfo.listener.onVDGetList(arrayList3, VDThreadType.CHILD_THREAD);
                    }
                });
            }
        }
    }

    public void get(VDEvent vDEvent, int i9, VDGetListener vDGetListener) {
        VDConnector connector;
        if (!VDValue.isNullEvent(vDEvent) && vDGetListener != null && (connector = getConnector(vDEvent)) != null) {
            Log.d(TAG, "get: eventId=" + vDEvent.getId() + " threadType=" + i9 + " connectorService=" + connector.getServiceInfo().getServiceName());
            connector.get(vDEvent, i9, vDGetListener);
        }
    }

    public VDConnector getConnector(VDEvent vDEvent) {
        int id = vDEvent.getId() >> 16;
        VDConnector vDConnector = this.mCurVDConnector;
        if (vDConnector != null && vDConnector.getServiceInfo().getServiceType().getValue() == id) {
            return this.mCurVDConnector;
        }
        if (id >= this.mVDConnectorList.size()) {
            Log.w(TAG, "getConnector(VDEvent): id out of range, eventId=" + vDEvent.getId() + " index=" + id + " size=" + this.mVDConnectorList.size());
            return null;
        }
        VDConnector vDConnector2 = this.mVDConnectorList.get(id);
        this.mCurVDConnector = vDConnector2;
        Log.d(TAG, "getConnector(VDEvent): eventId=" + vDEvent.getId() + " index=" + id + " service=" + vDConnector2.getServiceInfo().getServiceName());
        return vDConnector2;
    }

    public VDEvent getOnce(VDEvent vDEvent) {
        VDConnector connector;
        if (!VDValue.isNullEvent(vDEvent) && (connector = getConnector(vDEvent)) != null) {
            Log.d(TAG, "getOnce: eventId=" + vDEvent.getId() + " connectorService=" + connector.getServiceInfo().getServiceName());
            return connector.getOnce(vDEvent);
        }
        Log.w(TAG, "getOnce: invalid event or connector null, eventId=" + (vDEvent != null ? vDEvent.getId() : -1));
        return null;
    }

    public void init(Context context, VDThreadConfig vDThreadConfig, ArrayList<String> arrayList) {
        if (context != null && !this.mIsInited) {
            this.mIsInited = true;
            this.mContext = context;
            Log.d(TAG, "init: context=" + context + " threadConfig=" + vDThreadConfig + " keepBindList=" + arrayList);
            if (vDThreadConfig == null) {
                this.mThreadPoolExecutor = new ThreadPoolExecutor(this.CORE_POOL_SIZE, this.MAXIMUM_POOL_SIZE, 1, TimeUnit.SECONDS, new LinkedBlockingQueue(128));
            } else {
                this.mThreadPoolExecutor = new ThreadPoolExecutor(vDThreadConfig.corePoolSize, vDThreadConfig.maximumPoolSize, vDThreadConfig.keepAliveTime, vDThreadConfig.timeUnit, vDThreadConfig.workQueue);
            }
            ArrayList<VDServiceDef.ServiceInfo> createServiceList = VDServiceDef.createServiceList(this.mContext);
            int size = createServiceList.size();
            int i9 = 0;
            while (i9 < size) {
                VDServiceDef.ServiceInfo serviceInfo = createServiceList.get(i9);
                i9++;
                VDServiceDef.ServiceInfo serviceInfo2 = serviceInfo;
                VDConnector vDConnector = new VDConnector(context, serviceInfo2, this.mThreadPoolExecutor);
                this.mVDConnectorList.add(vDConnector);
                Log.d(TAG, "init: VDConnector added service=" + serviceInfo2.getServiceName() + " type=" + serviceInfo2.getServiceType());
                if (arrayList != null) {
                    int i10 = 0;
                    while (true) {
                        if (i10 >= arrayList.size()) {
                            break;
                        } else if (arrayList.get(i10).equals(serviceInfo2.getServiceName())) {
                            vDConnector.keepBind(true);
                            arrayList.remove(i10);
                            break;
                        } else {
                            i10++;
                        }
                    }
                }
            }
        }
    }

    public boolean isServiceConnected(VDServiceDef.ServiceType serviceType) {
        VDConnector connector = getConnector(serviceType);
        if (connector != null) {
            boolean connected = connector.isServiceConnected();
            Log.d(TAG, "isServiceConnected: serviceType=" + serviceType + " connected=" + connected);
            return connected;
        }
        Log.w(TAG, "isServiceConnected: connector null, serviceType=" + serviceType);
        return false;
    }

    public void registerVDBindListener(VDBindListener vDBindListener) {
        ArrayList<VDConnector> arrayList = this.mVDConnectorList;
        int size = arrayList.size();
        int i9 = 0;
        while (i9 < size) {
            VDConnector vDConnector = arrayList.get(i9);
            i9++;
            vDConnector.registerVDBindListener(vDBindListener);
        }
    }

    public void registerVDCallbackListener(VDCallbackListener vDCallbackListener) {
        ArrayList<VDConnector> arrayList = this.mVDConnectorList;
        int size = arrayList.size();
        int i9 = 0;
        while (i9 < size) {
            VDConnector vDConnector = arrayList.get(i9);
            i9++;
            vDConnector.registerVDCallbackListener(vDCallbackListener);
        }
    }

    public void registerVDNotifyListener(VDNotifyListener vDNotifyListener) {
        ArrayList<VDConnector> arrayList = this.mVDConnectorList;
        int size = arrayList.size();
        int i9 = 0;
        while (i9 < size) {
            VDConnector vDConnector = arrayList.get(i9);
            i9++;
            vDConnector.registerVDNotifyListener(vDNotifyListener);
        }
    }

    public void release() {
        Log.d(TAG, "release: clearing all VDConnector list, size=" + this.mVDConnectorList.size());
        ArrayList<VDConnector> arrayList = this.mVDConnectorList;
        int size = arrayList.size();
        int i9 = 0;
        while (i9 < size) {
            VDConnector vDConnector = arrayList.get(i9);
            i9++;
            VDConnector vDConnector2 = vDConnector;
            if (vDConnector2.isServiceConnected()) {
                vDConnector2.unbindService();
            }
            vDConnector2.release();
        }
        this.mVDConnectorList.clear();
        this.mIsInited = false;
    }

    public void removeSubscribe(VDEvent vDEvent) {
        VDConnector connector;
        if (!VDValue.isNullEvent(vDEvent) && (connector = getConnector(vDEvent)) != null) {
            Log.d(TAG, "removeSubscribe: eventId=" + vDEvent.getId() + " connectorService=" + connector.getServiceInfo().getServiceName());
            connector.removeSubscribe(vDEvent);
        }
    }

    public void set(VDEvent vDEvent, int i9) {
        VDConnector connector;
        if (!VDValue.isNullEvent(vDEvent)) {
            vDEvent.createTimeMillis();
            if (dealDebounce(vDEvent, i9, 0) && (connector = getConnector(vDEvent)) != null) {
                connector.set(vDEvent);
            }
        }
    }

    public void setOnce(VDEvent vDEvent, int i9) {
        VDConnector connector;
        if (!VDValue.isNullEvent(vDEvent)) {
            vDEvent.createTimeMillis();
            Log.d(TAG, "setOnce: eventId=" + vDEvent.getId() + " debounceMs=" + i9);
            if (dealDebounce(vDEvent, i9, 1) && (connector = getConnector(vDEvent)) != null) {
                Log.d(TAG, "setOnce: debounce passed, connector.setOnce called. service=" + connector.getServiceInfo().getServiceName());
                connector.setOnce(vDEvent);
            }
        }
    }

    public void subscribe(VDEvent vDEvent, IVDBusNotify.Stub stub) {
        VDConnector connector;
        if (!VDValue.isNullEvent(vDEvent) && stub != null && (connector = getConnector(vDEvent)) != null) {
            connector.subscribe(vDEvent, stub);
        }
    }

    public void subscribeCommit() {
        ArrayList<VDConnector> arrayList = this.mVDConnectorList;
        int size = arrayList.size();
        int i9 = 0;
        Log.d(TAG, "subscribeCommit: sending commit to all connectors, size=" + size);
        while (i9 < size) {
            VDConnector vDConnector = arrayList.get(i9);
            i9++;
            vDConnector.subscribeCommit();
        }
    }

    public boolean unbindService(VDServiceDef.ServiceType serviceType) {
        VDConnector connector = getConnector(serviceType);
        if (connector != null) {
            boolean result = connector.unbindService();
            Log.d(TAG, "unbindService: serviceType=" + serviceType + " result=" + result);
            return result;
        }
        Log.w(TAG, "unbindService: connector null, serviceType=" + serviceType);
        return false;
    }

    public void unregisterVDBindListener(VDBindListener vDBindListener) {
        ArrayList<VDConnector> arrayList = this.mVDConnectorList;
        int size = arrayList.size();
        int i9 = 0;
        while (i9 < size) {
            VDConnector vDConnector = arrayList.get(i9);
            i9++;
            vDConnector.unregisterVDBindListener(vDBindListener);
        }
    }

    public void unregisterVDCallbackListener(VDCallbackListener vDCallbackListener) {
        ArrayList<VDConnector> arrayList = this.mVDConnectorList;
        int size = arrayList.size();
        int i9 = 0;
        while (i9 < size) {
            VDConnector vDConnector = arrayList.get(i9);
            i9++;
            vDConnector.unregisterVDCallbackListener(vDCallbackListener);
        }
    }

    public void unregisterVDNotifyListener(VDNotifyListener vDNotifyListener) {
        ArrayList<VDConnector> arrayList = this.mVDConnectorList;
        int size = arrayList.size();
        int i9 = 0;
        while (i9 < size) {
            VDConnector vDConnector = arrayList.get(i9);
            i9++;
            vDConnector.unregisterVDNotifyListener(vDNotifyListener);
        }
    }

    public void unsubscribe(VDEvent vDEvent, IVDBusNotify.Stub stub) {
        VDConnector connector;
        if (stub != null && (connector = getConnector(vDEvent)) != null) {
            Log.d(TAG, "unsubscribe: eventId=" + vDEvent.getId() + " connectorService=" + connector.getServiceInfo().getServiceName());
            connector.unsubscribe(vDEvent, stub);
        }
    }

    private VDConnector getConnector(VDServiceDef.ServiceType serviceType) {
        VDConnector vDConnector = this.mCurVDConnector;
        if (vDConnector != null && vDConnector.getServiceInfo().getServiceType() == serviceType) {
            return this.mCurVDConnector;
        }
        int value = serviceType.getValue();
        if (value >= this.mVDConnectorList.size()) {
            return null;
        }
        VDConnector vDConnector2 = this.mVDConnectorList.get(value);
        this.mCurVDConnector = vDConnector2;
        return vDConnector2;
    }
}
