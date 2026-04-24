package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public class VDMusicPlayTime implements Parcelable {
    public static final Parcelable.Creator<VDMusicPlayTime> CREATOR = new Parcelable.Creator<VDMusicPlayTime>() {
        public VDMusicPlayTime createFromParcel(Parcel parcel) {
            return new VDMusicPlayTime(parcel);
        }

        public VDMusicPlayTime[] newArray(int i9) {
            return new VDMusicPlayTime[i9];
        }
    };
    public int CurrentPlayingTime;
    public int TotalPlayTime;

    public VDMusicPlayTime() {
    }

    public static VDEvent createEvent(int i9, VDMusicPlayTime vDMusicPlayTime) {
        return new VDEvent(i9, createPayload(vDMusicPlayTime));
    }

    public static Bundle createPayload(VDMusicPlayTime vDMusicPlayTime) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMusicPlayTime.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, vDMusicPlayTime);
        return bundle;
    }

    public static VDMusicPlayTime getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDMusicPlayTime.class.getClassLoader());
        return (VDMusicPlayTime) vDEvent.getPayload().getParcelable(VDKey.DATA);
    }

    public int describeContents() {
        return 0;
    }

    public int getCurrentPlayingTime() {
        return this.CurrentPlayingTime;
    }

    public int getTotalPlayTime() {
        return this.TotalPlayTime;
    }

    public void setCurrentPlayingTime(int i9) {
        this.CurrentPlayingTime = i9;
    }

    public void setTotalPlayTime(int i9) {
        this.TotalPlayTime = i9;
    }

    public String toString() {
        return "VDMusicPlayTime{CurrentPlayingTime=" + this.CurrentPlayingTime + ", TotalPlayTime=" + this.TotalPlayTime + '}';
    }

    public void writeToParcel(Parcel parcel, int i9) {
        parcel.writeInt(this.CurrentPlayingTime);
        parcel.writeInt(this.TotalPlayTime);
    }

    protected VDMusicPlayTime(Parcel parcel) {
        this.CurrentPlayingTime = parcel.readInt();
        this.TotalPlayTime = parcel.readInt();
    }
}
