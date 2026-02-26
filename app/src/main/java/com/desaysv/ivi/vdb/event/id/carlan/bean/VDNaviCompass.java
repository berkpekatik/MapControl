package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/* loaded from: classes.dex */
public class VDNaviCompass implements Parcelable {
    public static final Parcelable.Creator<VDNaviCompass> CREATOR = new Parcelable.Creator<VDNaviCompass>() { // from class: com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviCompass.1
        @Override // android.os.Parcelable.Creator
        /* renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviCompass createFromParcel(Parcel parcel) {
            return new VDNaviCompass(parcel);
        }

        @Override // android.os.Parcelable.Creator
        /* renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviCompass[] newArray(int i) {
            return new VDNaviCompass[i];
        }
    };
    private int Angle;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public VDNaviCompass() {
    }

    protected VDNaviCompass(Parcel parcel) {
        this.Angle = parcel.readInt();
    }

    public int getAngle() {
        return this.Angle;
    }

    public void setAngle(int i) {
        this.Angle = i;
    }

    public String toString() {
        return "VDNaviCompass{Angle=" + this.Angle + '}';
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.Angle);
    }

    public static VDNaviCompass getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDNaviCompass.class.getClassLoader());
        return (VDNaviCompass) vDEvent.getPayload().getParcelable(VDKey.DATA);
    }

    public static Bundle createPayload(VDNaviCompass vDNaviCompass) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDNaviCompass.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, vDNaviCompass);
        return bundle;
    }

    public static VDEvent createEvent(int i, VDNaviCompass vDNaviCompass) {
        return new VDEvent(i, createPayload(vDNaviCompass));
    }
}
