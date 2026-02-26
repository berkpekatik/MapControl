package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/* loaded from: classes.dex */
public class VDNaviPerspective implements Parcelable {
    public static final Parcelable.Creator<VDNaviPerspective> CREATOR = new Parcelable.Creator<VDNaviPerspective>() { // from class: com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviPerspective.1
        @Override // android.os.Parcelable.Creator
        /* renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviPerspective createFromParcel(Parcel parcel) {
            return new VDNaviPerspective(parcel);
        }

        @Override // android.os.Parcelable.Creator
        /* renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviPerspective[] newArray(int i) {
            return new VDNaviPerspective[i];
        }
    };
    private int RequestPerspective;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public VDNaviPerspective() {
        this.RequestPerspective = 0;
    }

    protected VDNaviPerspective(Parcel parcel) {
        this.RequestPerspective = 0;
        this.RequestPerspective = parcel.readInt();
    }

    public int getRequestPerspective() {
        return this.RequestPerspective;
    }

    public void setRequestPerspective(int i) {
        this.RequestPerspective = i;
    }

    public String toString() {
        return "VDNaviPerspective{RequestPerspective=" + this.RequestPerspective + '}';
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.RequestPerspective);
    }

    public static VDNaviPerspective getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDNaviPerspective.class.getClassLoader());
        return (VDNaviPerspective) vDEvent.getPayload().getParcelable(VDKey.DATA);
    }

    public static Bundle createPayload(VDNaviPerspective vDNaviPerspective) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDNaviPerspective.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, vDNaviPerspective);
        return bundle;
    }

    public static VDEvent createEvent(int i, VDNaviPerspective vDNaviPerspective) {
        return new VDEvent(i, createPayload(vDNaviPerspective));
    }
}
