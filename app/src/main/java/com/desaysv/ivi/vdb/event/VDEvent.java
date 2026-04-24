package com.desaysv.ivi.vdb.event;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import com.desaysv.ivi.vdb.client.bind.VDThreadType;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public class VDEvent implements Parcelable {
    public static final Parcelable.Creator<VDEvent> CREATOR = new C04141();
    private int mId = 0;
    private Bundle mPayload;
    private int mThreadType = VDThreadType.MAIN_THREAD;
    public long mTimeMillis;

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    static class C04141 implements Parcelable.Creator<VDEvent> {
        C04141() {
        }

        public VDEvent m151a(Parcel parcel) {
            return new VDEvent(parcel);
        }

        public VDEvent[] m152a(int i9) {
            return new VDEvent[i9];
        }

        public VDEvent createFromParcel(Parcel parcel) {
            return m151a(parcel);
        }

        public VDEvent[] newArray(int i9) {
            return m152a(i9);
        }
    }

    public VDEvent(int i9) {
        this.mId = i9;
    }

    public void createTimeMillis() {
        this.mTimeMillis = SystemClock.elapsedRealtime();
    }

    public int describeContents() {
        return 0;
    }

    public int getId() {
        return this.mId;
    }

    public Bundle getPayload() {
        return this.mPayload;
    }

    public int getThreadType() {
        return this.mThreadType;
    }

    public long getTimeMillis() {
        return this.mTimeMillis;
    }

    public void setPayload(Bundle bundle) {
        this.mPayload = bundle;
    }

    public void setThreadType(int i9) {
        this.mThreadType = i9;
    }

    public String toString() {
        return "VDEvent{id=" + this.mId + ", payload=" + this.mPayload + ", threadType=" + this.mThreadType + ", timeMillis=" + this.mTimeMillis + "}";
    }

    public void writeToParcel(Parcel parcel, int i9) {
        parcel.writeInt(this.mId);
        parcel.writeBundle(this.mPayload);
        parcel.writeInt(this.mThreadType);
        parcel.writeLong(this.mTimeMillis);
    }

    public VDEvent(int i9, Bundle bundle) {
        this.mId = i9;
        this.mPayload = bundle;
    }

    protected VDEvent(Parcel parcel) {
        this.mId = parcel.readInt();
        this.mPayload = parcel.readBundle();
        this.mThreadType = parcel.readInt();
        this.mTimeMillis = parcel.readLong();
    }
}
