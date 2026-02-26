package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public class VDNaviDisplayCluster implements Parcelable {
    public static final Parcelable.Creator<VDNaviDisplayCluster> CREATOR = new Parcelable.Creator<VDNaviDisplayCluster>() {
        public VDNaviDisplayCluster createFromParcel(Parcel parcel) {
            return new VDNaviDisplayCluster(parcel);
        }

        public VDNaviDisplayCluster[] newArray(int i9) {
            return new VDNaviDisplayCluster[i9];
        }
    };
    public String DisplayCluster;
    public String NaviFrontDeskStatus;
    public int Perspective = 0;
    public String PerspectiveResult;
    public String RequestDisplayNaviArea;

    public VDNaviDisplayCluster() {
    }

    public static VDEvent createEvent(int i9, VDNaviDisplayCluster vDNaviDisplayCluster) {
        return new VDEvent(i9, createPayload(vDNaviDisplayCluster));
    }

    public static Bundle createPayload(VDNaviDisplayCluster vDNaviDisplayCluster) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDNaviDisplayCluster.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, vDNaviDisplayCluster);
        return bundle;
    }

    public static VDNaviDisplayCluster getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDNaviDisplayCluster.class.getClassLoader());
        return (VDNaviDisplayCluster) vDEvent.getPayload().getParcelable(VDKey.DATA);
    }

    public int describeContents() {
        return 0;
    }

    public String getDisplayCluster() {
        return this.DisplayCluster;
    }

    public String getNaviFrontDeskStatus() {
        return this.NaviFrontDeskStatus;
    }

    public int getPerspective() {
        return this.Perspective;
    }

    public String getPerspectiveResult() {
        return this.PerspectiveResult;
    }

    public String getRequestDisplayNaviArea() {
        return this.RequestDisplayNaviArea;
    }

    public void setDisplayCluster(String str) {
        this.DisplayCluster = str;
    }

    public void setNaviFrontDeskStatus(String str) {
        this.NaviFrontDeskStatus = str;
    }

    public void setPerspective(int i9) {
        this.Perspective = i9;
    }

    public void setPerspectiveResult(String str) {
        this.PerspectiveResult = str;
    }

    public void setRequestDisplayNaviArea(String str) {
        this.RequestDisplayNaviArea = str;
    }

    public String toString() {
        return "VDNaviDisplayCluster{NaviFrontDeskStatus='" + this.NaviFrontDeskStatus + "', DisplayCluster='" + this.DisplayCluster + "', Perspective=" + this.Perspective + ", PerspectiveResult='" + this.PerspectiveResult + "', RequestDisplayNaviArea='" + this.RequestDisplayNaviArea + "'}";
    }

    public void writeToParcel(Parcel parcel, int i9) {
        parcel.writeString(this.NaviFrontDeskStatus);
        parcel.writeString(this.DisplayCluster);
        parcel.writeInt(this.Perspective);
        parcel.writeString(this.PerspectiveResult);
        parcel.writeString(this.RequestDisplayNaviArea);
    }

    public VDNaviDisplayCluster(Parcel parcel) {
        this.NaviFrontDeskStatus = parcel.readString();
        this.DisplayCluster = parcel.readString();
        this.Perspective = parcel.readInt();
        this.PerspectiveResult = parcel.readString();
        this.RequestDisplayNaviArea = parcel.readString();
    }
}
