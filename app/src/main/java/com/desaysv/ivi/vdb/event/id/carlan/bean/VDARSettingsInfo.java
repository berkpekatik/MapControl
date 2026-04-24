package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

public class VDARSettingsInfo implements Parcelable {
    public static final Parcelable.Creator<VDARSettingsInfo> CREATOR = new Parcelable.Creator<VDARSettingsInfo>() {
        public VDARSettingsInfo createFromParcel(Parcel parcel) {
            return new VDARSettingsInfo(parcel);
        }

        public VDARSettingsInfo[] newArray(int i) {
            return new VDARSettingsInfo[i];
        }
    };
    private int AdasDisplaySwitch = 0;

    public VDARSettingsInfo() {
    }

    public static VDEvent createEvent(int id, VDARSettingsInfo info) {
        return new VDEvent(id, createPayload(info));
    }

    public static Bundle createPayload(VDARSettingsInfo info) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDARSettingsInfo.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, info);
        return bundle;
    }

    public static VDARSettingsInfo getValue(VDEvent event) {
        if (event == null || event.getPayload() == null) {
            return null;
        }
        Object data = event.getPayload().getParcelable(VDKey.DATA);
        if (data instanceof VDARSettingsInfo) {
            return (VDARSettingsInfo) data;
        }
        return null;
    }

    public int describeContents() {
        return 0;
    }

    public int getAdasDisplaySwitch() {
        return this.AdasDisplaySwitch;
    }

    public void setAdasDisplaySwitch(int i) {
        this.AdasDisplaySwitch = i;
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.AdasDisplaySwitch);
    }

    public VDARSettingsInfo(Parcel parcel) {
        this.AdasDisplaySwitch = parcel.readInt();
    }
}
