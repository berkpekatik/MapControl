package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/* loaded from: classes.dex */
public class VDNaviDisplayArea implements Parcelable {
    public static final Parcelable.Creator<VDNaviDisplayArea> CREATOR = new Parcelable.Creator<VDNaviDisplayArea>() { // from class: com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayArea.1
        @Override // android.os.Parcelable.Creator
        /* renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviDisplayArea createFromParcel(Parcel parcel) {
            return new VDNaviDisplayArea(parcel);
        }

        @Override // android.os.Parcelable.Creator
        /* renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviDisplayArea[] newArray(int i) {
            return new VDNaviDisplayArea[i];
        }
    };
    private int NaviDisplayArea;
    private String NaviDisplayAreaResult;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public VDNaviDisplayArea() {
    }

    protected VDNaviDisplayArea(Parcel parcel) {
        this.NaviDisplayArea = parcel.readInt();
        this.NaviDisplayAreaResult = parcel.readString();
    }

    public int getNaviDisplayArea() {
        return this.NaviDisplayArea;
    }

    public void setNaviDisplayArea(int i) {
        this.NaviDisplayArea = i;
    }

    public String getNaviDisplayAreaResult() {
        return this.NaviDisplayAreaResult;
    }

    public void setNaviDisplayAreaResult(String str) {
        this.NaviDisplayAreaResult = str;
    }

    public String toString() {
        return "VDNaviDisplayArea{NaviDisplayArea=" + this.NaviDisplayArea + ", NaviDisplayAreaResult='" + this.NaviDisplayAreaResult + "'}";
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.NaviDisplayArea);
        parcel.writeString(this.NaviDisplayAreaResult);
    }

    public static VDNaviDisplayArea getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDNaviDisplayArea.class.getClassLoader());
        return (VDNaviDisplayArea) vDEvent.getPayload().getParcelable(VDKey.DATA);
    }

    public static Bundle createPayload(VDNaviDisplayArea vDNaviDisplayArea) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDNaviDisplayArea.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, vDNaviDisplayArea);
        return bundle;
    }

    public static VDEvent createEvent(int i, VDNaviDisplayArea vDNaviDisplayArea) {
        return new VDEvent(i, createPayload(vDNaviDisplayArea));
    }
}
