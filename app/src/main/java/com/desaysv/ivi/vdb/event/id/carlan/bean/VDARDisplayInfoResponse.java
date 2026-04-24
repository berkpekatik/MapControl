package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

public class VDARDisplayInfoResponse implements Parcelable {
    public static final Parcelable.Creator<VDARDisplayInfoResponse> CREATOR = new Parcelable.Creator<VDARDisplayInfoResponse>() {
        public VDARDisplayInfoResponse createFromParcel(Parcel parcel) {
            return new VDARDisplayInfoResponse(parcel);
        }

        public VDARDisplayInfoResponse[] newArray(int i) {
            return new VDARDisplayInfoResponse[i];
        }
    };
    private int AdasDisplaySwitchFeedback = 0;

    public VDARDisplayInfoResponse() {
    }

    public static VDEvent createEvent(int id, VDARDisplayInfoResponse info) {
        return new VDEvent(id, createPayload(info));
    }

    public static Bundle createPayload(VDARDisplayInfoResponse info) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDARDisplayInfoResponse.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, info);
        return bundle;
    }

    public static VDARDisplayInfoResponse getValue(VDEvent event) {
        if (event == null || event.getPayload() == null) {
            return null;
        }
        Object data = event.getPayload().getParcelable(VDKey.DATA);
        if (data instanceof VDARDisplayInfoResponse) {
            return (VDARDisplayInfoResponse) data;
        }
        return null;
    }

    public int describeContents() {
        return 0;
    }

    public int getAdasDisplaySwitchFeedback() {
        return this.AdasDisplaySwitchFeedback;
    }

    public void setAdasDisplaySwitchFeedback(int i) {
        this.AdasDisplaySwitchFeedback = i;
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.AdasDisplaySwitchFeedback);
    }

    public VDARDisplayInfoResponse(Parcel parcel) {
        this.AdasDisplaySwitchFeedback = parcel.readInt();
    }
}
