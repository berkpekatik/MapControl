package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/* loaded from: classes.dex */
public class VDNaviTotalInfo implements Parcelable {
    public static final Parcelable.Creator<VDNaviTotalInfo> CREATOR = new Parcelable.Creator<VDNaviTotalInfo>() { // from class: com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviTotalInfo.1
        @Override // android.os.Parcelable.Creator
        /* renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviTotalInfo createFromParcel(Parcel parcel) {
            return new VDNaviTotalInfo(parcel);
        }

        @Override // android.os.Parcelable.Creator
        /* renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviTotalInfo[] newArray(int i) {
            return new VDNaviTotalInfo[i];
        }
    };
    private String ArrivalTime;
    private int DistanceUint;
    private int RemDistance;
    private int RemDistanceUint;
    private int TimeLeft;
    private int TotalDistance;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public VDNaviTotalInfo() {
        this.DistanceUint = 0;
    }

    protected VDNaviTotalInfo(Parcel parcel) {
        this.DistanceUint = 0;
        this.TotalDistance = parcel.readInt();
        this.DistanceUint = parcel.readInt();
        this.RemDistance = parcel.readInt();
        this.RemDistanceUint = parcel.readInt();
        this.TimeLeft = parcel.readInt();
        this.ArrivalTime = parcel.readString();
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.TotalDistance);
        parcel.writeInt(this.DistanceUint);
        parcel.writeInt(this.RemDistance);
        parcel.writeInt(this.RemDistanceUint);
        parcel.writeInt(this.TimeLeft);
        parcel.writeString(this.ArrivalTime);
    }

    public int getTotalDistance() {
        return this.TotalDistance;
    }

    public void setTotalDistance(int i) {
        this.TotalDistance = i;
    }

    public int getDistanceUint() {
        return this.DistanceUint;
    }

    public void setDistanceUint(int i) {
        this.DistanceUint = i;
    }

    public int getRemDistance() {
        return this.RemDistance;
    }

    public void setRemDistance(int i) {
        this.RemDistance = i;
    }

    public int getRemDistanceUint() {
        return this.RemDistanceUint;
    }

    public void setRemDistanceUint(int i) {
        this.RemDistanceUint = i;
    }

    public int getTimeLeft() {
        return this.TimeLeft;
    }

    public void setTimeLeft(int i) {
        this.TimeLeft = i;
    }

    public String getArrivalTime() {
        return this.ArrivalTime;
    }

    public void setArrivalTime(String str) {
        this.ArrivalTime = str;
    }

    public String toString() {
        return "VDNaviTotalInfo{TotalDistance=" + this.TotalDistance + ", DistanceUint=" + this.DistanceUint + ", RemDistance=" + this.RemDistance + ", RemDistanceUint=" + this.RemDistanceUint + ", TimeLeft=" + this.TimeLeft + ", ArrivalTime='" + this.ArrivalTime + "'}";
    }

    public static VDNaviTotalInfo getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDNaviTotalInfo.class.getClassLoader());
        return (VDNaviTotalInfo) vDEvent.getPayload().getParcelable(VDKey.DATA);
    }

    public static Bundle createPayload(VDNaviTotalInfo vDNaviTotalInfo) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDNaviTotalInfo.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, vDNaviTotalInfo);
        return bundle;
    }

    public static VDEvent createEvent(int i, VDNaviTotalInfo vDNaviTotalInfo) {
        return new VDEvent(i, createPayload(vDNaviTotalInfo));
    }
}
