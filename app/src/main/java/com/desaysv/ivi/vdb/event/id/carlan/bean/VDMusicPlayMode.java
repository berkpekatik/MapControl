package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public class VDMusicPlayMode implements Parcelable {
    public static final Parcelable.Creator<VDMusicPlayMode> CREATOR = new Parcelable.Creator<VDMusicPlayMode>() {
        public VDMusicPlayMode createFromParcel(Parcel parcel) {
            return new VDMusicPlayMode(parcel);
        }

        public VDMusicPlayMode[] newArray(int i9) {
            return new VDMusicPlayMode[i9];
        }
    };
    public String MusicPlayMode;

    public VDMusicPlayMode() {
    }

    public static VDEvent createEvent(int i9, VDMusicPlayMode vDMusicPlayMode) {
        return new VDEvent(i9, createPayload(vDMusicPlayMode));
    }

    public static Bundle createPayload(VDMusicPlayMode vDMusicPlayMode) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMusicPlayMode.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, vDMusicPlayMode);
        return bundle;
    }

    public static VDMusicPlayMode getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDMusicPlayMode.class.getClassLoader());
        return (VDMusicPlayMode) vDEvent.getPayload().getParcelable(VDKey.DATA);
    }

    public int describeContents() {
        return 0;
    }

    public String getMusicPlayMode() {
        return this.MusicPlayMode;
    }

    public void setMusicPlayMode(String str) {
        this.MusicPlayMode = str;
    }

    public String toString() {
        return "VDMusicPlayMode{MusicPlayMode='" + this.MusicPlayMode + "'}";
    }

    public void writeToParcel(Parcel parcel, int i9) {
        parcel.writeString(this.MusicPlayMode);
    }

    protected VDMusicPlayMode(Parcel parcel) {
        this.MusicPlayMode = parcel.readString();
    }
}
