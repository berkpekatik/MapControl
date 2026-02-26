package com.desaysv.ivi.extra.project.carinfo.proxy;

import java.util.ArrayList;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public class CarInfoTimer {
    private static volatile CarInfoTimer mInstance;
    private final ArrayList<CarInfoHelper> mTimerCallBacks = new ArrayList<>();

    CarInfoTimer() {
    }

    public static CarInfoTimer getInstance() {
        if (mInstance == null) {
            synchronized (CarInfoTimer.class) {
                try {
                    if (mInstance == null) {
                        mInstance = new CarInfoTimer();
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return mInstance;
    }

    public void regTimerCallBack(CarInfoHelper carInfoHelper) {
        if (carInfoHelper != null) {
            synchronized (this.mTimerCallBacks) {
                this.mTimerCallBacks.add(carInfoHelper);
            }
        }
    }

    public void unRegTimerCallBack(CarInfoHelper carInfoHelper) {
        synchronized (this.mTimerCallBacks) {
            this.mTimerCallBacks.remove(carInfoHelper);
        }
    }
}
