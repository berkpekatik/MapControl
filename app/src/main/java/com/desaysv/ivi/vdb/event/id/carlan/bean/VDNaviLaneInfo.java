package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;
import java.util.ArrayList;

/* loaded from: classes.dex */
public class VDNaviLaneInfo implements Parcelable {
    public static final Parcelable.Creator<VDNaviLaneInfo> CREATOR = new Parcelable.Creator<VDNaviLaneInfo>() { // from class: com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviLaneInfo.1
        @Override // android.os.Parcelable.Creator
        /* renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviLaneInfo createFromParcel(Parcel parcel) {
            return new VDNaviLaneInfo(parcel);
        }

        @Override // android.os.Parcelable.Creator
        /* renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviLaneInfo[] newArray(int i) {
            return new VDNaviLaneInfo[i];
        }
    };
    private ArrayList<LaneInfo> RoadInfo;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public VDNaviLaneInfo() {
    }

    protected VDNaviLaneInfo(Parcel parcel) {
        this.RoadInfo = parcel.createTypedArrayList(LaneInfo.CREATOR);
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeTypedList(this.RoadInfo);
    }

    public ArrayList<LaneInfo> getRoadInfo() {
        return this.RoadInfo;
    }

    public void setRoadInfo(ArrayList<LaneInfo> arrayList) {
        this.RoadInfo = arrayList;
    }

    public String toString() {
        return "VDNaviLaneInfo{RoadInfo=" + this.RoadInfo + '}';
    }

    public static class LaneInfo implements Parcelable {
        public static final Parcelable.Creator<LaneInfo> CREATOR = new Parcelable.Creator<LaneInfo>() { // from class: com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviLaneInfo.LaneInfo.1
            @Override // android.os.Parcelable.Creator
            /* renamed from: a, reason: merged with bridge method [inline-methods] */
            public LaneInfo createFromParcel(Parcel parcel) {
                return new LaneInfo(parcel);
            }

            @Override // android.os.Parcelable.Creator
            /* renamed from: a, reason: merged with bridge method [inline-methods] */
            public LaneInfo[] newArray(int i) {
                return new LaneInfo[i];
            }
        };
        private int LaneIconId;
        private int LaneId;
        private int LaneInfo;

        @Override // android.os.Parcelable
        public int describeContents() {
            return 0;
        }

        public LaneInfo() {
        }

        protected LaneInfo(Parcel parcel) {
            this.LaneInfo = parcel.readInt();
            this.LaneId = parcel.readInt();
            this.LaneIconId = parcel.readInt();
        }

        public int getLaneInfo() {
            return this.LaneInfo;
        }

        public void setLaneInfo(int i) {
            this.LaneInfo = i;
        }

        public int getLaneId() {
            return this.LaneId;
        }

        public void setLaneId(int i) {
            this.LaneId = i;
        }

        public int getLaneIconId() {
            return this.LaneIconId;
        }

        public void setLaneIconId(int i) {
            this.LaneIconId = i;
        }

        public String toString() {
            return "LaneInfo{LaneInfo=" + this.LaneInfo + ", LaneId=" + this.LaneId + ", LaneIconId=" + this.LaneIconId + '}';
        }

        @Override // android.os.Parcelable
        public void writeToParcel(Parcel parcel, int i) {
            parcel.writeInt(this.LaneInfo);
            parcel.writeInt(this.LaneId);
            parcel.writeInt(this.LaneIconId);
        }
    }

    public static VDNaviLaneInfo getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDNaviLaneInfo.class.getClassLoader());
        return (VDNaviLaneInfo) vDEvent.getPayload().getParcelable(VDKey.DATA);
    }

    public static Bundle createPayload(VDNaviLaneInfo vDNaviLaneInfo) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDNaviLaneInfo.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, vDNaviLaneInfo);
        return bundle;
    }

    public static VDEvent createEvent(int i, VDNaviLaneInfo vDNaviLaneInfo) {
        return new VDEvent(i, createPayload(vDNaviLaneInfo));
    }
}
