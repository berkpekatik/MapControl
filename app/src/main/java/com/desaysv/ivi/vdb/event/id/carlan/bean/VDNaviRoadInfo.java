package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/* loaded from: classes.dex */
public class VDNaviRoadInfo implements Parcelable {
    public static final Parcelable.Creator<VDNaviRoadInfo> CREATOR = new Parcelable.Creator<VDNaviRoadInfo>() { // from class: com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviRoadInfo.1
        @Override // android.os.Parcelable.Creator
        /* renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviRoadInfo createFromParcel(Parcel parcel) {
            return new VDNaviRoadInfo(parcel);
        }

        @Override // android.os.Parcelable.Creator
        /* renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviRoadInfo[] newArray(int i) {
            return new VDNaviRoadInfo[i];
        }
    };
    private int IntersectionZoomStatus;
    private int NextNaviActiion;
    private int NextNaviActionProgbar;
    private String NextRoadName;
    private int RoadIcon;
    private String RoadName;
    private int RoadType;
    private int SegRemainDis;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public VDNaviRoadInfo() {
    }

    protected VDNaviRoadInfo(Parcel parcel) {
        this.SegRemainDis = parcel.readInt();
        this.RoadType = parcel.readInt();
        this.RoadIcon = parcel.readInt();
        this.RoadName = parcel.readString();
        this.NextRoadName = parcel.readString();
        this.NextNaviActionProgbar = parcel.readInt();
        this.NextNaviActiion = parcel.readInt();
        this.IntersectionZoomStatus = parcel.readInt();
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.SegRemainDis);
        parcel.writeInt(this.RoadType);
        parcel.writeInt(this.RoadIcon);
        parcel.writeString(this.RoadName);
        parcel.writeString(this.NextRoadName);
        parcel.writeInt(this.NextNaviActionProgbar);
        parcel.writeInt(this.NextNaviActiion);
        parcel.writeInt(this.IntersectionZoomStatus);
    }

    public int getSegRemainDis() {
        return this.SegRemainDis;
    }

    public void setSegRemainDis(int i) {
        this.SegRemainDis = i;
    }

    public int getRoadType() {
        return this.RoadType;
    }

    public void setRoadType(int i) {
        this.RoadType = i;
    }

    public int getRoadIcon() {
        return this.RoadIcon;
    }

    public void setRoadIcon(int i) {
        this.RoadIcon = i;
    }

    public String getRoadName() {
        return this.RoadName;
    }

    public void setRoadName(String str) {
        this.RoadName = str;
    }

    public String getNextRoadName() {
        return this.NextRoadName;
    }

    public void setNextRoadName(String str) {
        this.NextRoadName = str;
    }

    public int getNextNaviActionProgbar() {
        return this.NextNaviActionProgbar;
    }

    public void setNextNaviActionProgbar(int i) {
        this.NextNaviActionProgbar = i;
    }

    public int getNextNaviActiion() {
        return this.NextNaviActiion;
    }

    public void setNextNaviActiion(int i) {
        this.NextNaviActiion = i;
    }

    public int getIntersectionZoomStatus() {
        return this.IntersectionZoomStatus;
    }

    public void setIntersectionZoomStatus(int i) {
        this.IntersectionZoomStatus = i;
    }

    public String toString() {
        return "VDNaviRoadInfo{SegRemainDis=" + this.SegRemainDis + ", RoadType=" + this.RoadType + ", RoadIcon=" + this.RoadIcon + ", RoadName='" + this.RoadName + "', NextRoadName='" + this.NextRoadName + "', NextNaviActionProgbar=" + this.NextNaviActionProgbar + ", NextNaviActiion=" + this.NextNaviActiion + ", IntersectionZoomStatus=" + this.IntersectionZoomStatus + '}';
    }

    public static VDNaviRoadInfo getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDNaviRoadInfo.class.getClassLoader());
        return (VDNaviRoadInfo) vDEvent.getPayload().getParcelable(VDKey.DATA);
    }

    public static Bundle createPayload(VDNaviRoadInfo vDNaviRoadInfo) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDNaviRoadInfo.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, vDNaviRoadInfo);
        return bundle;
    }

    public static VDEvent createEvent(int i, VDNaviRoadInfo vDNaviRoadInfo) {
        return new VDEvent(i, createPayload(vDNaviRoadInfo));
    }
}
