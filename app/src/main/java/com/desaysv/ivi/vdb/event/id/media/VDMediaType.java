package com.desaysv.ivi.vdb.event.id.media;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public class VDMediaType implements Parcelable {
    public static final Parcelable.Creator<VDMediaType> CREATOR = new Parcelable.Creator<VDMediaType>() {
        public VDMediaType createFromParcel(Parcel parcel) {
            return new VDMediaType(parcel);
        }

        public VDMediaType[] newArray(int i9) {
            return new VDMediaType[i9];
        }
    };
    private int mMediaType;

    public VDMediaType() {
    }

    public static VDEvent createEvent(int i9, VDMediaType vDMediaType) {
        return new VDEvent(i9, createPayload(vDMediaType));
    }

    public static Bundle createPayload(VDMediaType vDMediaType) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMediaType.class.getClassLoader());
        bundle.putParcelable(VDKey.TYPE, vDMediaType);
        return bundle;
    }

    public static VDMediaType getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDMediaType.class.getClassLoader());
        return (VDMediaType) vDEvent.getPayload().getParcelable(VDKey.TYPE);
    }

    private void readFromParcel(Parcel parcel) {
        this.mMediaType = parcel.readInt();
    }

    public int describeContents() {
        return 0;
    }

    public int getMediaType() {
        return this.mMediaType;
    }

    public void putMediaType(int i9) {
        this.mMediaType = i9;
    }

    public String toString() {
        return "VDMediaType{mediaType=" + this.mMediaType + "}";
    }

    public void writeToParcel(Parcel parcel, int i9) {
        parcel.writeInt(this.mMediaType);
    }

    protected VDMediaType(Parcel parcel) {
        readFromParcel(parcel);
    }
}
