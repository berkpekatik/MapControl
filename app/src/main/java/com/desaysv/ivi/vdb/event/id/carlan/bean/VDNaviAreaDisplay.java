package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/* loaded from: classes.dex */
public class VDNaviAreaDisplay implements Parcelable {
    public static final Parcelable.Creator<VDNaviAreaDisplay> CREATOR = new Parcelable.Creator<VDNaviAreaDisplay>() { // from class: com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviAreaDisplay.1
        @Override // android.os.Parcelable.Creator
        /* renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviAreaDisplay createFromParcel(Parcel parcel) {
            return new VDNaviAreaDisplay(parcel);
        }

        @Override // android.os.Parcelable.Creator
        /* renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviAreaDisplay[] newArray(int i) {
            return new VDNaviAreaDisplay[i];
        }
    };
    private int RequestNaviAreaDisplay;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public VDNaviAreaDisplay() {
        this.RequestNaviAreaDisplay = 0;
    }

    protected VDNaviAreaDisplay(Parcel parcel) {
        this.RequestNaviAreaDisplay = 0;
        this.RequestNaviAreaDisplay = parcel.readInt();
    }

    public int getRequestNaviAreaDisplay() {
        return this.RequestNaviAreaDisplay;
    }

    public void setRequestNaviAreaDisplay(int i) {
        this.RequestNaviAreaDisplay = i;
    }

    public String toString() {
        return "VDNaviAreaDisplay{RequestNaviAreaDisplay=" + this.RequestNaviAreaDisplay + '}';
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.RequestNaviAreaDisplay);
    }

    public static VDNaviAreaDisplay getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDNaviAreaDisplay.class.getClassLoader());
        return (VDNaviAreaDisplay) vDEvent.getPayload().getParcelable(VDKey.DATA);
    }

    public static Bundle createPayload(VDNaviAreaDisplay vDNaviAreaDisplay) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDNaviAreaDisplay.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, vDNaviAreaDisplay);
        return bundle;
    }

    public static VDEvent createEvent(int i, VDNaviAreaDisplay vDNaviAreaDisplay) {
        return new VDEvent(i, createPayload(vDNaviAreaDisplay));
    }
}
