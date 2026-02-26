package com.desaysv.ivi.extra.project.carinfo.proxy;

import android.os.Handler;
import android.os.Message;
import android.util.SparseArray;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.event.VDEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public class CarInfoHelper {
    private static final int NOCHECK_COUNTER_VALUE = -1;
    public static final int TIMER_DELAY_DEFAULT_COUNT = 10;
    private static final int UPDATE_COUNTER_VALUE = 0;
    private final ArrayList<CacheItem> mCacheList = new ArrayList<>();
    private final CarInfoCallBack mCarInfoCallBack = new CarInfoCallBack(this);
    private final IServiceConnectListener mConnectCallBack = new ServiceConnectListener(this);
    private final Handler mHandler = new CarHandler(this);
    private final SparseArray<SparseArray<Integer>> mListenIDs = new SparseArray<>();
    /* access modifiers changed from: private */
    public ISpiListener mSpiListener;

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public static class CacheItem {
        public int cmdId;
        public int[] data;
        public int moduleId;

        public CacheItem(int i9, int i10, int[] iArr) {
            this.moduleId = i9;
            this.cmdId = i10;
            this.data = iArr;
        }
    }

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    private static class CarHandler extends Handler {
        private final WeakReference<CarInfoHelper> mHelper;

        public void handleMessage(Message message) {
            if (this.mHelper.get() != null) {
                if (message.what == 0) {
                    this.mHelper.get().removeCacheItem(message.arg1, message.arg2);
                }
                if (this.mHelper.get().mSpiListener != null) {
                    this.mHelper.get().mSpiListener.onReceiveSpi(message.what, message.arg1, message.arg2);
                }
            }
            super.handleMessage(message);
        }

        private CarHandler(CarInfoHelper carInfoHelper) {
            this.mHelper = new WeakReference<>(carInfoHelper);
        }
    }

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public static class CarInfoCallBack extends IVDBusNotify.Stub {
        private final WeakReference<CarInfoHelper> mHelper;

        public void onVDBusNotify(VDEvent vDEvent) {
            vDEvent.getPayload();
            int id = vDEvent.getId();
            int i9 = vDEvent.getPayload().getInt(Constants.CMD_ID);
            WeakReference<CarInfoHelper> weakReference = this.mHelper;
            if (weakReference != null && weakReference.get() != null) {
                this.mHelper.get().onReceiveData(id, i9);
            }
        }

        private CarInfoCallBack(CarInfoHelper carInfoHelper) {
            this.mHelper = new WeakReference<>(carInfoHelper);
        }
    }

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public interface ISpiListener {
        void onReceiveSpi(int i9, int i10, int i11);
    }

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    private static class ServiceConnectListener implements IServiceConnectListener {
        private final WeakReference<CarInfoHelper> mHelper;

        public void onServiceConnectedChanged(int i9) {
            if (i9 == 1 && this.mHelper.get() != null) {
                this.mHelper.get().onConnectSuccess();
            }
        }

        private ServiceConnectListener(CarInfoHelper carInfoHelper) {
            this.mHelper = new WeakReference<>(carInfoHelper);
        }
    }

    private void regCallBack() {
        for (int i9 = 0; i9 < this.mListenIDs.size(); i9++) {
            SparseArray valueAt = this.mListenIDs.valueAt(i9);
            int[] iArr = new int[valueAt.size()];
            for (int i10 = 0; i10 < valueAt.size(); i10++) {
                iArr[i10] = valueAt.keyAt(i10);
            }
            CarInfoProxy.getInstance().regCallBack(this.mListenIDs.keyAt(i9), this.mCarInfoCallBack, iArr);
        }
    }

    public void end() {
        this.mSpiListener = null;
        synchronized (this.mListenIDs) {
            try {
                CarInfoTimer.getInstance().unRegTimerCallBack(this);
                CarInfoProxy.getInstance().unRegServiceConnectListener(this.mConnectCallBack);
                for (int i9 = 0; i9 < this.mListenIDs.size(); i9++) {
                    CarInfoProxy.getInstance().unRegCallBack(this.mListenIDs.keyAt(i9), this.mCarInfoCallBack);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public int getItemValue(int i9, int i10) {
        int[] itemValues = getItemValues(i9, i10);
        if (itemValues == null || itemValues.length < 1) {
            return 0;
        }
        return itemValues[0];
    }

    public int[] getItemValues(int i9, int i10) {
        for (int i11 = 0; i11 < this.mCacheList.size(); i11++) {
            if (i9 == this.mCacheList.get(i11).moduleId && i10 == this.mCacheList.get(i11).cmdId) {
                return this.mCacheList.get(i11).data;
            }
        }
        return CarInfoProxy.getInstance().getItemValues(i9, i10);
    }

    public void listen(int i9, int[] iArr) {
        SparseArray sparseArray = new SparseArray();
        for (int put : iArr) {
            sparseArray.put(put, -1);
        }
        this.mListenIDs.put(i9, sparseArray);
    }

    public void onConnectSuccess() {
        regCallBack();
        Message message = new Message();
        message.what = 3;
        this.mHandler.sendMessage(message);
    }

    public void onReceiveData(int i9, int i10) {
        synchronized (this.mListenIDs) {
            try {
                SparseArray sparseArray = this.mListenIDs.get(i9);
                if (sparseArray != null) {
                    if (sparseArray.get(i10) != null) {
                        int intValue = ((Integer) sparseArray.get(i10)).intValue();
                        Message message = new Message();
                        message.arg1 = i9;
                        message.arg2 = i10;
                        if (intValue <= 0) {
                            message.what = 1;
                        } else {
                            message.what = 2;
                        }
                        this.mHandler.sendMessage(message);
                    }
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public void onSVTimer() {
        for (int i9 = 0; i9 < this.mListenIDs.size(); i9++) {
            SparseArray valueAt = this.mListenIDs.valueAt(i9);
            for (int i10 = 0; i10 < valueAt.size(); i10++) {
                int keyAt = valueAt.keyAt(i10);
                int intValue = ((Integer) valueAt.valueAt(i10)).intValue() - 1;
                valueAt.put(keyAt, Integer.valueOf(intValue));
                if (intValue == 0) {
                    Message message = new Message();
                    message.what = 0;
                    message.arg1 = this.mListenIDs.keyAt(i9);
                    message.arg2 = keyAt;
                    this.mHandler.sendMessage(message);
                }
            }
        }
    }

    public void removeCacheItem(int i9, int i10) {
        for (int size = this.mCacheList.size() - 1; size >= 0; size--) {
            if (i9 == this.mCacheList.get(size).moduleId && i10 == this.mCacheList.get(size).cmdId) {
                this.mCacheList.remove(size);
            }
        }
    }

    public void setDelayResponse(int i9, int i10, int i11) {
        setDelayResponse(i9, i10, new int[]{i11});
    }

    public void setDelayTimerCount(int i9, int i10, int i11) {
        synchronized (this.mListenIDs) {
            try {
                if (this.mListenIDs.get(i9) != null) {
                    this.mListenIDs.get(i9).put(i10, Integer.valueOf(i11));
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public void start(ISpiListener iSpiListener) {
        this.mSpiListener = iSpiListener;
        regCallBack();
        CarInfoProxy.getInstance().regServiceConnectListener(this.mConnectCallBack);
        CarInfoTimer.getInstance().regTimerCallBack(this);
    }

    public void setDelayResponse(int i9, int i10, int[] iArr) {
        removeCacheItem(i9, i10);
        this.mCacheList.add(new CacheItem(i9, i10, iArr));
        setDelayTimerCount(i9, i10, 10);
    }
}
