package com.desaysv.ivi.vdb.client.bind;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.desaysv.ivi.vdb.IVDBus;
import com.desaysv.ivi.vdb.IVDBusCallback;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.client.bind.VDServiceDef;
import com.desaysv.ivi.vdb.client.listener.VDBindListener;
import com.desaysv.ivi.vdb.client.listener.VDCallbackListener;
import com.desaysv.ivi.vdb.client.listener.VDGetListener;
import com.desaysv.ivi.vdb.client.listener.VDNotifyListener;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDValue;
import com.desaysv.ivi.vdb.utils.VDConfigUtil;
import com.desaysv.ivi.vdb.utils.VDServiceUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public class VDConnector {
    public static final int RECEIVER_EXPORTED = 2;
    private final int MSG_DISPATCH_EVENT = 2000;
    private final int MSG_DISPATCH_GET_EVENT = 3000;
    private final int MSG_RECONNECT_SERVICE = 1000;
    private final int RECONNECT_SPACE = 3000;
    private final int RECONNECT_TOTAL = 10;
    /* access modifiers changed from: private */
    public Handler mChildThreadHandler;
    private Context mContext;
    /* access modifiers changed from: private */
    public BinderDeathRecipient mDeathRecipient = new BinderDeathRecipient();
    private HandlerThread mDispatchEventThread;
    /* access modifiers changed from: private */
    public ArrayList<VDEvent> mGetBuffer = new ArrayList<>();
    private Map<Integer, ArrayList<GetListenerInfo>> mGetMap = new HashMap();
    private IVDBusCallback.Stub mICallback = null;
    /* access modifiers changed from: private */
    public IVDBus mIVDBus = null;
    private boolean mIsSubscribeModified = false;
    /* access modifiers changed from: private */
    public boolean mKeepBind = false;
    /* access modifiers changed from: private */
    public ArrayList<LocalCallback> mLocalCallbackList = new ArrayList<>();
    /* access modifiers changed from: private */
    public Handler mMainThreadHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message message) {
            super.handleMessage(message);
            int i9 = message.what;
            if (i9 == 1000) {
                Log.d(VDConnector.TAG, "MSG_RECONNECT_SERVICE: reconnectCount=" + VDConnector.this.mReconnectCount + " service=" + VDConnector.this.mServiceInfo.getServiceName());
                if (VDConnector.this.mReconnectCount >= 10) {
                    VDConnector.this.mReconnectCount = 0;
                    Log.w(VDConnector.TAG, "Reconnect deneme limiti aşıldı, tekrar denenmeyecek. service=" + VDConnector.this.mServiceInfo.getServiceName());
                    return;
                }
                VDConnector.access$108(VDConnector.this);
                Log.d(VDConnector.TAG, "Reconnect denemesi başlatılıyor, count=" + VDConnector.this.mReconnectCount + " service=" + VDConnector.this.mServiceInfo.getServiceName());
                VDConnector.this.bindService();
            } else if (i9 == 2000) {
                Log.d(VDConnector.TAG, "MSG_DISPATCH_EVENT: main thread notify, eventId=" + ((VDEvent) message.obj).getId());
                VDConnector.this.dispatchEvent((VDEvent) message.obj, VDThreadType.MAIN_THREAD);
            } else if (i9 == 3000) {
                Iterator it = ((Map) message.obj).entrySet().iterator();
                if (it.hasNext()) {
                    Map.Entry entry = (Map.Entry) it.next();
                    ((VDGetListener) entry.getKey()).onVDGet((VDEvent) entry.getValue(), VDThreadType.MAIN_THREAD);
                }
            }
        }
    };
    private int mPid = Process.myPid();
    private RebootReceiver mRebootReceiver = null;
    /* access modifiers changed from: private */
    public int mReconnectCount = 0;
    private ServiceConnection mServiceConn = new ServiceConnection() {
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(VDConnector.TAG, "onServiceConnected: component=" + componentName + " service=" + VDConnector.this.mServiceInfo.getServiceName());
            try {
                iBinder.linkToDeath(VDConnector.this.mDeathRecipient, 0);
                VDConnector.this.mIVDBus = IVDBus.Stub.asInterface(iBinder);
                Log.d(VDConnector.TAG, "onServiceConnected: IVDBus arayüzü alındı");
                VDConnector.this.stopReconnect();
                VDConnector.this.onVDBusConnected();
            } catch (Exception e10) {
                Log.e(VDConnector.TAG, "onServiceConnected: hata", e10);
            }
        }

        public void onServiceDisconnected(ComponentName componentName) {
            Log.w(VDConnector.TAG, "onServiceDisconnected: component=" + componentName + " service=" + VDConnector.this.mServiceInfo.getServiceName());
            VDConnector.this.mIVDBus = null;
        }
    };
    /* access modifiers changed from: private */
    public VDServiceDef.ServiceInfo mServiceInfo;
    /* access modifiers changed from: private */
    public ArrayList<VDEvent> mSetBuffer = new ArrayList<>();
    /* access modifiers changed from: private */
    public ArrayList<VDEvent> mSubscribeBuffer = new ArrayList<>();
    private ThreadPoolExecutor mThreadPoolExecutor;
    /* access modifiers changed from: private */
    public ArrayList<VDEvent> mThreadSubscribeBuffer = new ArrayList<>();
    private ArrayList<VDBindListener> mVDBindListenerList = new ArrayList<>();
    /* access modifiers changed from: private */
    public ArrayList<VDCallbackListener> mVDCallbackListenerList = new ArrayList<>();
    private ArrayList<VDNotifyListener> mVDNotifyListenerList = new ArrayList<>();

    private static final String TAG = "VDConnector";

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public class BinderDeathRecipient implements IBinder.DeathRecipient {
        public void binderDied() {
            Log.e(VDConnector.TAG, "binderDied: service=" + VDConnector.this.mServiceInfo.getServiceName());
            if (VDConnector.this.mIVDBus != null) {
                VDConnector.this.mIVDBus.asBinder().unlinkToDeath(VDConnector.this.mDeathRecipient, 0);
                VDConnector.this.mIVDBus = null;
            }
            VDConnector.this.onVDBusDisconnected();
            if (VDConnector.this.mServiceInfo.isSystemService()) {
                VDConnector.this.startReconnect();
            } else if (VDConnector.this.mKeepBind) {
                VDConnector.this.startReconnect();
            }
        }

        private BinderDeathRecipient() {
        }
    }

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public class GetListenerInfo {
        public VDGetListener listener;
        public int threadType;

        public GetListenerInfo(VDGetListener vDGetListener, int i9) {
            this.listener = vDGetListener;
            this.threadType = i9;
        }
    }

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public class LocalCallback {
        private IVDBusNotify.Stub mCallback;
        private VDEvent mEvent;

        public LocalCallback(VDEvent vDEvent, IVDBusNotify.Stub stub) {
            this.mEvent = vDEvent;
            this.mCallback = stub;
        }

        public IVDBusNotify.Stub getCallback() {
            return this.mCallback;
        }

        public VDEvent getEvent() {
            return this.mEvent;
        }
    }

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public class RebootReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            int size;
            int size2;
            int size3;
            int size4;
            if (VDConnector.this.mIVDBus == null) {
                synchronized (VDConnector.this.mSubscribeBuffer) {
                    size = VDConnector.this.mSubscribeBuffer.size();
                }
                synchronized (VDConnector.this.mLocalCallbackList) {
                    size2 = VDConnector.this.mLocalCallbackList.size();
                }
                synchronized (VDConnector.this.mSetBuffer) {
                    size3 = VDConnector.this.mSetBuffer.size();
                }
                synchronized (VDConnector.this.mGetBuffer) {
                    size4 = VDConnector.this.mGetBuffer.size();
                }
                if (size > 0 || size2 > 0 || size3 > 0 || size4 > 0) {
                    VDConnector.this.bindService();
                }
            }
        }

        private RebootReceiver() {
        }
    }

    public VDConnector(Context context, VDServiceDef.ServiceInfo serviceInfo, ThreadPoolExecutor threadPoolExecutor) {
        this.mContext = context;
        this.mServiceInfo = serviceInfo;
        this.mThreadPoolExecutor = threadPoolExecutor;
        Log.d(TAG, "VDConnector oluşturuldu service=" + serviceInfo.getServiceName() + " type=" + serviceInfo.getServiceType());
        this.mICallback = new IVDBusCallback.Stub() {
            public void onVDBusCallback(VDEvent vDEvent) {
                if (vDEvent.getPayload() != null) {
                    vDEvent.getPayload().setClassLoader(VDEvent.class.getClassLoader());
                }
                synchronized (VDConnector.this.mVDCallbackListenerList) {
                    int i9 = 0;
                    while (i9 < VDConnector.this.mVDCallbackListenerList.size()) {
                        try {
                            VDCallbackListener vDCallbackListener = (VDCallbackListener) VDConnector.this.mVDCallbackListenerList.get(i9);
                            if (vDCallbackListener != null) {
                                vDCallbackListener.onVDCallback(vDEvent);
                            }
                            i9++;
                        } finally {
                        }
                    }
                }
            }

            public void onVDBusNotify(VDEvent vDEvent) {
                if (vDEvent.getPayload() != null) {
                    vDEvent.getPayload().setClassLoader(VDEvent.class.getClassLoader());
                }
                if (VDConfigUtil.UNIT_TEST_MODE) {
                    VDConnector.this.dispatchEvent(vDEvent, VDThreadType.MAIN_THREAD);
                    return;
                }
                int i9 = VDThreadType.MAIN_THREAD;
                synchronized (VDConnector.this.mThreadSubscribeBuffer) {
                    ArrayList l9 = VDConnector.this.mThreadSubscribeBuffer;
                    int size = l9.size();
                    int i10 = 0;
                    while (true) {
                        if (i10 < size) {
                            Object obj = l9.get(i10);
                            i10++;
                            VDEvent vDEvent2 = (VDEvent) obj;
                            if (vDEvent.getId() == vDEvent2.getId()) {
                                i9 = vDEvent2.getThreadType();
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                }
                if (i9 == VDThreadType.MAIN_THREAD || i9 == VDThreadType.MAIN_AND_CHILD_THREAD) {
                    Message obtainMessage = VDConnector.this.mMainThreadHandler.obtainMessage();
                    obtainMessage.what = 2000;
                    obtainMessage.obj = vDEvent;
                    VDConnector.this.mMainThreadHandler.sendMessage(obtainMessage);
                }
                if (i9 == VDThreadType.CHILD_THREAD || i9 == VDThreadType.MAIN_AND_CHILD_THREAD) {
                    Message obtainMessage2 = VDConnector.this.mChildThreadHandler.obtainMessage();
                    obtainMessage2.what = 2000;
                    obtainMessage2.obj = vDEvent;
                    VDConnector.this.mChildThreadHandler.sendMessage(obtainMessage2);
                }
            }
        };
        registerReceiver();
    }

    static int access$108(VDConnector vDConnector) {
        int i9 = vDConnector.mReconnectCount;
        vDConnector.mReconnectCount = i9 + 1;
        return i9;
    }

    private void callbackGet(final VDEvent vDEvent, int i9, final VDGetListener vDGetListener) {
        if (i9 == -1) {
            vDGetListener.onVDGet(vDEvent, i9);
            return;
        }
        if (i9 == VDThreadType.MAIN_THREAD || i9 == VDThreadType.MAIN_AND_CHILD_THREAD) {
            if (isMainThread()) {
                vDGetListener.onVDGet(vDEvent, VDThreadType.MAIN_THREAD);
            } else {
                HashMap hashMap = new HashMap();
                hashMap.put(vDGetListener, vDEvent);
                if (VDConfigUtil.UNIT_TEST_MODE) {
                    vDGetListener.onVDGet(vDEvent, VDThreadType.MAIN_THREAD);
                } else {
                    Message obtainMessage = this.mMainThreadHandler.obtainMessage();
                    obtainMessage.what = 3000;
                    obtainMessage.obj = hashMap;
                    this.mMainThreadHandler.sendMessage(obtainMessage);
                }
            }
        }
        if (i9 == VDThreadType.CHILD_THREAD || i9 == VDThreadType.MAIN_AND_CHILD_THREAD) {
            this.mThreadPoolExecutor.execute(new Runnable() {
                public void run() {
                    vDGetListener.onVDGet(vDEvent, VDThreadType.CHILD_THREAD);
                }
            });
        }
    }

    private int[] changeSubscribeBuffer(ArrayList<VDEvent> arrayList) {
        int i9 = 0;
        if (arrayList == null || arrayList.size() <= 0) {
            return new int[0];
        }
        int[] iArr = new int[arrayList.size()];
        int size = arrayList.size();
        int i10 = 0;
        while (i10 < size) {
            VDEvent vDEvent = arrayList.get(i10);
            i10++;
            iArr[i9] = vDEvent.getId();
            i9++;
        }
        return iArr;
    }

    private void createDispatchEventThread() {
        if (this.mDispatchEventThread == null) {
            HandlerThread handlerThread = new HandlerThread("VDBus_DISPATCH");
            this.mDispatchEventThread = handlerThread;
            handlerThread.start();
            this.mChildThreadHandler = new Handler(this.mDispatchEventThread.getLooper()) {
                public void handleMessage(Message message) {
                    super.handleMessage(message);
                    if (message.what == 2000) {
                        VDConnector.this.dispatchEvent((VDEvent) message.obj, VDThreadType.CHILD_THREAD);
                    }
                }
            };
        }
    }

    private void dispatchGet(VDEvent vDEvent, VDEvent vDEvent2) {
        if (VDValue.isNullEvent(vDEvent2)) {
            vDEvent2 = null;
        } else if (vDEvent2.getPayload() != null) {
            vDEvent2.getPayload().setClassLoader(VDEvent.class.getClassLoader());
        }
        ArrayList arrayList = this.mGetMap.get(Integer.valueOf(vDEvent.getId()));
        if (arrayList != null) {
            int size = arrayList.size();
            int i9 = 0;
            while (i9 < size) {
                Object obj = arrayList.get(i9);
                i9++;
                GetListenerInfo getListenerInfo = (GetListenerInfo) obj;
                callbackGet(vDEvent2, getListenerInfo.threadType, getListenerInfo.listener);
            }
            this.mGetMap.remove(Integer.valueOf(vDEvent.getId()));
        }
    }

    private boolean isMainThread() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            return true;
        }
        return false;
    }

    private boolean isSystemApp() {
        try {
            if ((this.mContext.getPackageManager().getApplicationInfo(this.mContext.getPackageName(), 0).flags & 1) > 0) {
                return true;
            }
            return false;
        } catch (PackageManager.NameNotFoundException e10) {
            e10.printStackTrace();
            return false;
        }
    }

    private void registerReceiver() {
        if (!TextUtils.isEmpty(this.mServiceInfo.getServiceReboot()) && this.mRebootReceiver == null) {
            this.mRebootReceiver = new RebootReceiver();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(this.mServiceInfo.getServiceReboot());
            if (Build.VERSION.SDK_INT >= 34) {
                Intent unused = this.mContext.registerReceiver(this.mRebootReceiver, intentFilter, 2);
            } else {
                this.mContext.registerReceiver(this.mRebootReceiver, intentFilter);
            }
        }
    }

    private void sendCheckBootBroadcast() {
        String serviceAction = this.mServiceInfo.getServiceAction();
        if (!isSystemApp()) {
            serviceAction = serviceAction + VDServiceDef.SUFFIX_USER_APP_BROADCAST;
        }
        Intent intent = new Intent();
        intent.setAction(serviceAction);
        intent.setPackage(this.mServiceInfo.getPackageName());
        intent.putExtra("client", this.mContext.getPackageName());
        this.mContext.sendBroadcast(intent);
    }

    private void unregisterReceiver() {
        RebootReceiver rebootReceiver = this.mRebootReceiver;
        if (rebootReceiver != null) {
            this.mContext.unregisterReceiver(rebootReceiver);
            this.mRebootReceiver = null;
        }
    }

    public void addSubscribe(VDEvent vDEvent) {
        if (this.mServiceInfo.isExist()) {
            synchronized (this.mSubscribeBuffer) {
                try {
                    Iterator<VDEvent> it = this.mSubscribeBuffer.iterator();
                    while (it.hasNext()) {
                        if (vDEvent.getId() == it.next().getId()) {
                            Log.d(TAG, "addSubscribe: zaten buffer'da, eventId=" + vDEvent.getId() + " service=" + this.mServiceInfo.getServiceName());
                            return;
                        }
                    }
                    Log.d(TAG, "addSubscribe: eklendi, eventId=" + vDEvent.getId() + " threadType=" + vDEvent.getThreadType());
                    this.mSubscribeBuffer.add(vDEvent);
                    this.mIsSubscribeModified = true;
                    synchronized (this.mThreadSubscribeBuffer) {
                        if (vDEvent.getThreadType() != VDThreadType.CHILD_THREAD) {
                            if (vDEvent.getThreadType() == VDThreadType.MAIN_AND_CHILD_THREAD) {
                            }
                        }
                        this.mThreadSubscribeBuffer.add(vDEvent);
                    }
                } catch (Throwable th) {
                    throw th;
                } finally {
                    while (true) {
                    }
                }
            }
        }
    }

    public boolean bindService() {
        Log.d(TAG, "bindService: çağrıldı service=" + this.mServiceInfo.getServiceName() + " system=" + this.mServiceInfo.isSystemService());
        if (this.mIVDBus != null) {
            Log.d(TAG, "bindService: zaten bağlı, IVDBus null değil");
            return true;
        }
        startReconnect();
        if (this.mServiceInfo.isSystemService()) {
            IBinder serviceFromServiceManager = VDServiceUtil.getServiceFromServiceManager(this.mServiceInfo.getServiceName());
            if (serviceFromServiceManager != null) {
                try {
                    serviceFromServiceManager.linkToDeath(this.mDeathRecipient, 0);
                    try {
                        this.mIVDBus = IVDBus.Stub.asInterface(serviceFromServiceManager);
                        Log.d(TAG, "bindService: system service üzerinden IVDBus alındı");
                        stopReconnect();
                        onVDBusConnected();
                    } catch (Exception e10) {
                        Log.e(TAG, "bindService: system service bağlanma hatası", e10);
                        return false;
                    }
                } catch (Exception e11) {
                    Log.e(TAG, "bindService: linkToDeath hatası", e11);
                }
            }
            Log.w(TAG, "bindService: system service binder null döndü");
            return false;
        }
        Intent intent = new Intent();
        intent.setAction(this.mServiceInfo.getServiceAction());
        intent.setPackage(this.mServiceInfo.getPackageName());
        if (Build.VERSION.SDK_INT >= 26) {
            ComponentName unused = this.mContext.startForegroundService(intent);
        } else {
            this.mContext.startService(intent);
        }
        this.mContext.bindService(intent, this.mServiceConn, 1);
        return true;
    }

    public void dispatchEvent(VDEvent vDEvent, int i9) {
        Log.d(TAG, "dispatchEvent: id=" + vDEvent.getId() + " threadType=" + i9 + " listenerCount=" + this.mVDNotifyListenerList.size());
        synchronized (this.mVDNotifyListenerList) {
            int i10 = 0;
            while (i10 < this.mVDNotifyListenerList.size()) {
                try {
                    VDNotifyListener vDNotifyListener = this.mVDNotifyListenerList.get(i10);
                    if (vDNotifyListener != null) {
                        vDNotifyListener.onVDNotify(vDEvent, i9);
                    }
                    i10++;
                } finally {
                }
            }
        }
    }

    public void get(VDEvent vDEvent, int i9, VDGetListener vDGetListener) {
        ArrayList arrayList;
        VDEvent vDEvent2 = null;
        if (!this.mServiceInfo.isExist()) {
            Log.w(TAG, "get: service mevcut değil, callback null ile çağrılacak. eventId=" + vDEvent.getId());
            callbackGet((VDEvent) null, i9, vDGetListener);
            return;
        }
        if (this.mIVDBus == null) {
            Log.d(TAG, "get: IVDBus null, bindService çağrılıyor. eventId=" + vDEvent.getId());
            bindService();
        }
        try {
            IVDBus iVDBus = this.mIVDBus;
            if (iVDBus != null) {
                Log.d(TAG, "get: IVDBus.get çağrılıyor, eventId=" + vDEvent.getId());
                VDEvent vDEvent3 = iVDBus.get(vDEvent);
                if (!VDValue.isNullEvent(vDEvent3)) {
                    if (vDEvent3.getPayload() != null) {
                        vDEvent3.getPayload().setClassLoader(VDEvent.class.getClassLoader());
                    }
                    vDEvent2 = vDEvent3;
                }
                callbackGet(vDEvent2, i9, vDGetListener);
                return;
            }
        } catch (Exception e10) {
            e10.printStackTrace();
        }
        synchronized (this.mGetBuffer) {
            try {
                if (this.mGetMap.containsKey(Integer.valueOf(vDEvent.getId()))) {
                    arrayList = this.mGetMap.get(Integer.valueOf(vDEvent.getId()));
                } else {
                    arrayList = new ArrayList();
                    this.mGetMap.put(Integer.valueOf(vDEvent.getId()), arrayList);
                }
                arrayList.add(new GetListenerInfo(vDGetListener, i9));
                ArrayList<VDEvent> arrayList2 = this.mGetBuffer;
                int size = arrayList2.size();
                int i10 = 0;
                while (true) {
                    if (i10 < size) {
                        VDEvent vDEvent4 = arrayList2.get(i10);
                        i10++;
                        VDEvent vDEvent5 = vDEvent4;
                        if (vDEvent.getId() == vDEvent5.getId()) {
                            this.mGetBuffer.remove(vDEvent5);
                            break;
                        }
                    } else {
                        break;
                    }
                }
                this.mGetBuffer.add(vDEvent);
                Log.d(TAG, "get: IVDBus null, istek buffer'a eklendi. eventId=" + vDEvent.getId());
            } catch (Throwable th) {
                while (true) {
                    throw th;
                }
            }
        }
        bindService();
    }

    public VDEvent getOnce(VDEvent vDEvent) {
        VDEvent vDEvent2 = null;
        if (!this.mServiceInfo.isExist()) {
            Log.w(TAG, "getOnce: service mevcut değil, null dönecek. eventId=" + vDEvent.getId());
            return null;
        }
        if (this.mIVDBus == null) {
            Log.d(TAG, "getOnce: IVDBus null, bindService çağrılıyor. eventId=" + vDEvent.getId());
            bindService();
        }
        boolean z9 = true;
        try {
            IVDBus iVDBus = this.mIVDBus;
            if (iVDBus != null) {
                Log.d(TAG, "getOnce: IVDBus.get çağrılıyor, eventId=" + vDEvent.getId());
                VDEvent vDEvent3 = iVDBus.get(vDEvent);
                if (!VDValue.isNullEvent(vDEvent3)) {
                    if (vDEvent3.getPayload() != null) {
                        vDEvent3.getPayload().setClassLoader(VDEvent.class.getClassLoader());
                    }
                    z9 = false;
                    vDEvent2 = vDEvent3;
                }
            }
        } catch (Exception unused2) {
            Log.e(TAG, "getOnce: beklenmeyen hata", unused2);
        }
        if (z9) {
            Log.d(TAG, "getOnce: IVDBus null veya hata sonrası bindService tekrar çağrılıyor. eventId=" + vDEvent.getId());
            bindService();
        }
        return vDEvent2;
    }

    public VDServiceDef.ServiceInfo getServiceInfo() {
        return this.mServiceInfo;
    }

    public boolean isServiceConnected() {
        boolean connected = this.mIVDBus != null;
        Log.d(TAG, "isServiceConnected: " + connected + " service=" + this.mServiceInfo.getServiceName());
        if (connected) {
            return true;
        }
        return false;
    }

    public void keepBind(boolean z9) {
        this.mKeepBind = z9;
    }

    public void onVDBusConnected() {
        Log.d(TAG, "onVDBusConnected: service=" + this.mServiceInfo.getServiceName());
        int i9;
        synchronized (this.mSubscribeBuffer) {
            try {
                this.mIVDBus.subscribe(changeSubscribeBuffer(this.mSubscribeBuffer), this.mPid, this.mContext.getPackageName(), this.mICallback);
            } catch (Exception e10) {
                e10.printStackTrace();
            }
        }
        synchronized (this.mSetBuffer) {
            try {
                Iterator<VDEvent> it = this.mSetBuffer.iterator();
                while (it.hasNext()) {
                    this.mIVDBus.set(it.next());
                }
            } catch (Exception e11) {
                e11.printStackTrace();
            }
            this.mSetBuffer.clear();
        }
        synchronized (this.mGetBuffer) {
            i9 = 0;
            try {
                ArrayList<VDEvent> arrayList = this.mGetBuffer;
                int size = arrayList.size();
                int i10 = 0;
                while (i10 < size) {
                    VDEvent vDEvent = arrayList.get(i10);
                    i10++;
                    VDEvent vDEvent2 = vDEvent;
                    Log.d(TAG, "onVDBusConnected: pending get işleniyor, eventId=" + vDEvent2.getId());
                    dispatchGet(vDEvent2, this.mIVDBus.get(vDEvent2));
                }
            } catch (Exception e12) {
                e12.printStackTrace();
            }
            this.mGetBuffer.clear();
        }
        synchronized (this.mVDBindListenerList) {
            ArrayList<VDBindListener> arrayList2 = this.mVDBindListenerList;
            int size2 = arrayList2.size();
            int i11 = 0;
            while (i11 < size2) {
                VDBindListener vDBindListener = arrayList2.get(i11);
                i11++;
                VDBindListener vDBindListener2 = vDBindListener;
                if (vDBindListener2 != null) {
                    Log.d(TAG, "onVDBusConnected: VDBindListener'e onVDConnected bildiriliyor, serviceType=" + this.mServiceInfo.getServiceType());
                    vDBindListener2.onVDConnected(this.mServiceInfo.getServiceType());
                }
            }
        }
        synchronized (this.mLocalCallbackList) {
            try {
                ArrayList<LocalCallback> arrayList3 = this.mLocalCallbackList;
                int size3 = arrayList3.size();
                while (i9 < size3) {
                    LocalCallback localCallback = arrayList3.get(i9);
                    i9++;
                    LocalCallback localCallback2 = localCallback;
                    this.mIVDBus.subscribeCustomizedEvent(localCallback2.getEvent(), localCallback2.getCallback());
                }
            } catch (Exception e13) {
                e13.printStackTrace();
            }
            this.mLocalCallbackList.clear();
        }
    }

    public void onVDBusDisconnected() {
        Log.w(TAG, "onVDBusDisconnected: service=" + this.mServiceInfo.getServiceName());
        synchronized (this.mVDBindListenerList) {
            try {
                ArrayList<VDBindListener> arrayList = this.mVDBindListenerList;
                int size = arrayList.size();
                int i9 = 0;
                while (i9 < size) {
                    VDBindListener vDBindListener = arrayList.get(i9);
                    i9++;
                    VDBindListener vDBindListener2 = vDBindListener;
                    if (vDBindListener2 != null) {
                        vDBindListener2.onVDDisconnected(this.mServiceInfo.getServiceType());
                    }
                }
            } finally {
            }
        }
    }

    public void registerVDBindListener(VDBindListener vDBindListener) {
        synchronized (this.mVDBindListenerList) {
            try {
                if (!this.mVDBindListenerList.contains(vDBindListener)) {
                    this.mVDBindListenerList.add(vDBindListener);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public void registerVDCallbackListener(VDCallbackListener vDCallbackListener) {
        synchronized (this.mVDCallbackListenerList) {
            try {
                if (!this.mVDCallbackListenerList.contains(vDCallbackListener)) {
                    this.mVDCallbackListenerList.add(vDCallbackListener);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public void registerVDNotifyListener(VDNotifyListener vDNotifyListener) {
        synchronized (this.mVDNotifyListenerList) {
            try {
                if (!this.mVDNotifyListenerList.contains(vDNotifyListener)) {
                    this.mVDNotifyListenerList.add(vDNotifyListener);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public void release() {
        HandlerThread handlerThread = this.mDispatchEventThread;
        if (handlerThread != null) {
            handlerThread.quit();
        }
        unregisterReceiver();
    }

    public void removeSubscribe(VDEvent vDEvent) {
        int i9;
        if (this.mServiceInfo.isExist()) {
            synchronized (this.mSubscribeBuffer) {
                ArrayList<VDEvent> arrayList = this.mSubscribeBuffer;
                int size = arrayList.size();
                i9 = 0;
                int i10 = 0;
                while (true) {
                    if (i10 < size) {
                        VDEvent vDEvent2 = arrayList.get(i10);
                        i10++;
                        VDEvent vDEvent3 = vDEvent2;
                        if (vDEvent.getId() == vDEvent3.getId()) {
                            this.mSubscribeBuffer.remove(vDEvent3);
                            this.mIsSubscribeModified = true;
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }
            synchronized (this.mThreadSubscribeBuffer) {
                ArrayList<VDEvent> arrayList2 = this.mThreadSubscribeBuffer;
                int size2 = arrayList2.size();
                while (true) {
                    if (i9 < size2) {
                        VDEvent vDEvent4 = arrayList2.get(i9);
                        i9++;
                        VDEvent vDEvent5 = vDEvent4;
                        if (vDEvent.getId() == vDEvent5.getId()) {
                            this.mThreadSubscribeBuffer.remove(vDEvent5);
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }
        }
    }

    public void set(VDEvent vDEvent) {
        if (this.mServiceInfo.isExist()) {
            if (this.mIVDBus == null) {
                bindService();
            }
            try {
                IVDBus iVDBus = this.mIVDBus;
                if (iVDBus != null) {
                    iVDBus.set(vDEvent);
                    return;
                }
            } catch (Exception e10) {
                e10.printStackTrace();
            }
            synchronized (this.mSetBuffer) {
                ArrayList<VDEvent> arrayList = this.mSetBuffer;
                int size = arrayList.size();
                int i9 = 0;
                while (true) {
                    if (i9 < size) {
                        VDEvent vDEvent2 = arrayList.get(i9);
                        i9++;
                        VDEvent vDEvent3 = vDEvent2;
                        if (vDEvent.getId() == vDEvent3.getId()) {
                            this.mSetBuffer.remove(vDEvent3);
                            break;
                        }
                    } else {
                        break;
                    }
                }
                this.mSetBuffer.add(vDEvent);
            }
            bindService();
        }
    }

    public void setOnce(VDEvent vDEvent) {
        if (this.mServiceInfo.isExist()) {
            if (this.mIVDBus == null) {
                bindService();
            }
            try {
                IVDBus iVDBus = this.mIVDBus;
                if (iVDBus != null) {
                    iVDBus.set(vDEvent);
                    return;
                }
            } catch (Exception e10) {
                e10.printStackTrace();
            }
            bindService();
        }
    }

    public void startReconnect() {
        this.mMainThreadHandler.removeMessages(1000);
        this.mMainThreadHandler.sendEmptyMessageDelayed(1000, 3000);
    }

    public void stopReconnect() {
        this.mReconnectCount = 0;
        this.mMainThreadHandler.removeMessages(1000);
    }

    public void subscribe(VDEvent vDEvent, IVDBusNotify.Stub stub) {
        if (this.mServiceInfo.isExist()) {
            if (this.mIVDBus == null) {
                if (this.mServiceInfo.isSystemService()) {
                    bindService();
                } else {
                    sendCheckBootBroadcast();
                }
            }
            try {
                IVDBus iVDBus = this.mIVDBus;
                if (iVDBus != null) {
                    iVDBus.subscribeCustomizedEvent(vDEvent, stub);
                    return;
                }
            } catch (Exception e10) {
                e10.printStackTrace();
            }
            synchronized (this.mLocalCallbackList) {
                ArrayList<LocalCallback> arrayList = this.mLocalCallbackList;
                int size = arrayList.size();
                int i9 = 0;
                while (true) {
                    if (i9 < size) {
                        LocalCallback localCallback = arrayList.get(i9);
                        i9++;
                        LocalCallback localCallback2 = localCallback;
                        if (vDEvent == localCallback2.getEvent() && stub == localCallback2.getCallback()) {
                            this.mLocalCallbackList.remove(localCallback2);
                            break;
                        }
                    } else {
                        break;
                    }
                }
                this.mLocalCallbackList.add(new LocalCallback(vDEvent, stub));
            }
        }
    }

    /* JADX WARNING: No exception handlers in catch block: Catch:{  } */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void subscribeCommit() {
        /*
            r6 = this;
            com.desaysv.ivi.vdb.client.bind.VDServiceDef$ServiceInfo r0 = r6.mServiceInfo
            boolean r0 = r0.isExist()
            if (r0 != 0) goto L_0x0009
            goto L_0x0048
        L_0x0009:
            java.util.ArrayList<com.desaysv.ivi.vdb.event.VDEvent> r0 = r6.mSubscribeBuffer
            monitor-enter(r0)
            boolean r1 = r6.mIsSubscribeModified     // Catch:{ all -> 0x0026 }
            if (r1 == 0) goto L_0x0047
            r1 = 0
            r6.mIsSubscribeModified = r1     // Catch:{ all -> 0x0026 }
            r6.createDispatchEventThread()     // Catch:{ all -> 0x0026 }
            com.desaysv.ivi.vdb.IVDBus r1 = r6.mIVDBus     // Catch:{ all -> 0x0026 }
            if (r1 != 0) goto L_0x002b
            com.desaysv.ivi.vdb.client.bind.VDServiceDef$ServiceInfo r1 = r6.mServiceInfo     // Catch:{ all -> 0x0026 }
            boolean r1 = r1.isSystemService()     // Catch:{ all -> 0x0026 }
            if (r1 == 0) goto L_0x0028
            r6.bindService()     // Catch:{ all -> 0x0026 }
            goto L_0x002b
        L_0x0026:
            r1 = move-exception
            goto L_0x0049
        L_0x0028:
            r6.sendCheckBootBroadcast()     // Catch:{ all -> 0x0026 }
        L_0x002b:
            com.desaysv.ivi.vdb.IVDBus r1 = r6.mIVDBus     // Catch:{ RemoteException -> 0x0043 }
            if (r1 == 0) goto L_0x0047
            java.util.ArrayList<com.desaysv.ivi.vdb.event.VDEvent> r2 = r6.mSubscribeBuffer     // Catch:{ RemoteException -> 0x0043 }
            int[] r2 = r6.changeSubscribeBuffer(r2)     // Catch:{ RemoteException -> 0x0043 }
            int r3 = r6.mPid     // Catch:{ RemoteException -> 0x0043 }
            android.content.Context r4 = r6.mContext     // Catch:{ RemoteException -> 0x0043 }
            java.lang.String r4 = r4.getPackageName()     // Catch:{ RemoteException -> 0x0043 }
            com.desaysv.ivi.vdb.IVDBusCallback$Stub r5 = r6.mICallback     // Catch:{ RemoteException -> 0x0043 }
            r1.subscribe(r2, r3, r4, r5)     // Catch:{ RemoteException -> 0x0043 }
            goto L_0x0047
        L_0x0043:
            r1 = move-exception
            r1.printStackTrace()     // Catch:{ all -> 0x0026 }
        L_0x0047:
            monitor-exit(r0)     // Catch:{ all -> 0x0026 }
        L_0x0048:
            return
        L_0x0049:
            monitor-exit(r0)     // Catch:{ all -> 0x0026 }
            throw r1
        */
        throw new UnsupportedOperationException("Method not decompiled: com.desaysv.ivi.vdb.client.bind.VDConnector.subscribeCommit():void");
    }

    public boolean unbindService() {
        try {
            if (this.mIVDBus != null) {
                synchronized (this.mSubscribeBuffer) {
                    this.mSubscribeBuffer.clear();
                    this.mIVDBus.subscribe(changeSubscribeBuffer(this.mSubscribeBuffer), this.mPid, this.mContext.getPackageName(), this.mICallback);
                }
                synchronized (this.mThreadSubscribeBuffer) {
                    this.mThreadSubscribeBuffer.clear();
                }
                this.mIVDBus = null;
            }
            if (this.mServiceInfo.isSystemService()) {
                return true;
            }
            this.mContext.unbindService(this.mServiceConn);
            return true;
        } catch (Exception e10) {
            e10.printStackTrace();
            return false;
        }
    }

    public void unregisterVDBindListener(VDBindListener vDBindListener) {
        synchronized (this.mVDBindListenerList) {
            this.mVDBindListenerList.remove(vDBindListener);
        }
    }

    public void unregisterVDCallbackListener(VDCallbackListener vDCallbackListener) {
        synchronized (this.mVDCallbackListenerList) {
            this.mVDCallbackListenerList.remove(vDCallbackListener);
        }
    }

    public void unregisterVDNotifyListener(VDNotifyListener vDNotifyListener) {
        synchronized (this.mVDNotifyListenerList) {
            this.mVDNotifyListenerList.remove(vDNotifyListener);
        }
    }

    public void unsubscribe(VDEvent vDEvent, IVDBusNotify.Stub stub) {
        if (this.mServiceInfo.isExist()) {
            try {
                IVDBus iVDBus = this.mIVDBus;
                if (iVDBus != null) {
                    iVDBus.unsubscribeCustomizedEvent(vDEvent, stub);
                }
            } catch (Exception e10) {
                e10.printStackTrace();
            }
        }
    }
}
