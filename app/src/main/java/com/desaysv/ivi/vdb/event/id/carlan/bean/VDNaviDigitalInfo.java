package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/* loaded from: classes.dex */
public class VDNaviDigitalInfo implements Parcelable {
    public static final Parcelable.Creator<VDNaviDigitalInfo> CREATOR = new Parcelable.Creator<VDNaviDigitalInfo>() { // from class: com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDigitalInfo.1
        @Override // android.os.Parcelable.Creator
        /* renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviDigitalInfo createFromParcel(Parcel parcel) {
            return new VDNaviDigitalInfo(parcel);
        }

        @Override // android.os.Parcelable.Creator
        /* renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviDigitalInfo[] newArray(int i) {
            return new VDNaviDigitalInfo[i];
        }
    };
    private int CameraType;
    private int NaviStatus;
    private int SpeedingInfo;
    private int WarningMessage;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public VDNaviDigitalInfo() {
    }

    protected VDNaviDigitalInfo(Parcel parcel) {
        this.NaviStatus = parcel.readInt();
        this.SpeedingInfo = parcel.readInt();
        this.CameraType = parcel.readInt();
        this.WarningMessage = parcel.readInt();
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.NaviStatus);
        parcel.writeInt(this.SpeedingInfo);
        parcel.writeInt(this.CameraType);
        parcel.writeInt(this.WarningMessage);
    }

    public int getNaviStatus() {
        return this.NaviStatus;
    }

    public void setNaviStatus(int i) {
        this.NaviStatus = i;
    }

    public int getSpeedingInfo() {
        return this.SpeedingInfo;
    }

    public void setSpeedingInfo(int i) {
        this.SpeedingInfo = i;
    }

    public int getCameraType() {
        return this.CameraType;
    }

    public void setCameraType(int i) {
        this.CameraType = i;
    }

    public int getWarningMessage() {
        return this.WarningMessage;
    }

    public void setWarningMessage(int i) {
        this.WarningMessage = i;
    }

    public String toString() {
        return "VDNaviDigitalInfo{NaviStatus=" + this.NaviStatus + ", SpeedingInfo=" + this.SpeedingInfo + ", CameraType=" + this.CameraType + ", WarningMessage=" + this.WarningMessage + '}';
    }

    public static VDNaviDigitalInfo getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDNaviDigitalInfo.class.getClassLoader());
        return (VDNaviDigitalInfo) vDEvent.getPayload().getParcelable(VDKey.DATA);
    }

    public static Bundle createPayload(VDNaviDigitalInfo vDNaviDigitalInfo) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDNaviDigitalInfo.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, vDNaviDigitalInfo);
        return bundle;
    }

    public static VDEvent createEvent(int i, VDNaviDigitalInfo vDNaviDigitalInfo) {
        return new VDEvent(i, createPayload(vDNaviDigitalInfo));
    }
}
