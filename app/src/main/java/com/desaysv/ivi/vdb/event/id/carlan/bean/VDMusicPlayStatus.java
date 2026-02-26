package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public class VDMusicPlayStatus implements Parcelable {
    public static final Parcelable.Creator<VDMusicPlayStatus> CREATOR = new Parcelable.Creator<VDMusicPlayStatus>() {
        public VDMusicPlayStatus createFromParcel(Parcel parcel) {
            return new VDMusicPlayStatus(parcel);
        }

        public VDMusicPlayStatus[] newArray(int i9) {
            return new VDMusicPlayStatus[i9];
        }
    };
    public String MusicPlayStatus;

    public VDMusicPlayStatus() {
    }

    public static VDEvent createEvent(int i9, VDMusicPlayStatus vDMusicPlayStatus) {
        return new VDEvent(i9, createPayload(vDMusicPlayStatus));
    }

    public static Bundle createPayload(VDMusicPlayStatus vDMusicPlayStatus) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMusicPlayStatus.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, vDMusicPlayStatus);
        return bundle;
    }

    public static VDMusicPlayStatus getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDMusicPlayStatus.class.getClassLoader());
        return (VDMusicPlayStatus) vDEvent.getPayload().getParcelable(VDKey.DATA);
    }

    public int describeContents() {
        return 0;
    }

    public String getMusicPlayStatus() {
        return this.MusicPlayStatus;
    }

    public void setMusicPlayStatus(String str) {
        this.MusicPlayStatus = str;
    }

    public String toString() {
        return "VDMusicPlayStatus{MusicPlayStatus='" + this.MusicPlayStatus + "'}";
    }

    public void writeToParcel(Parcel parcel, int i9) {
        parcel.writeString(this.MusicPlayStatus);
    }

    protected VDMusicPlayStatus(Parcel parcel) {
        this.MusicPlayStatus = parcel.readString();
    }
}
