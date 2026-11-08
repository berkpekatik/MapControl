/*
 * VDBus BT veri sınıfı (araçla konuşmak için); Parcel alan sırası Parcel uyumu için sabit, değiştirilmemeli (servis sınıf adıyla açar).
 * toString() gizlilik için isim/numara/adres/portre içermez.
 */
package com.desaysv.ivi.vdb.event.id.bt.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

public class VDBTDial implements Parcelable {
    public static final Parcelable.Creator<VDBTDial> CREATOR = new Parcelable.Creator<VDBTDial>() {
        @Override
        public VDBTDial createFromParcel(Parcel parcel) {
            return new VDBTDial(parcel);
        }

        @Override
        public VDBTDial[] newArray(int r1) {
            return new VDBTDial[r1];
        }
    };
    private String mNumber = null;
    private String mAddress = null;
    private int mDialStatus = 0;
    private int mDialCtrl = 1;
    private int mAcceptCallType = 1;
    private int mRedialType = 0;
    private String mUuid = null;

    @Override
    public int describeContents() {
        return 0;
    }

    public VDBTDial() {
    }

    public String getNumber() {
        return this.mNumber;
    }

    public void putNumber(String str) {
        this.mNumber = str;
    }

    public String getAddress() {
        return this.mAddress;
    }

    public void putAddress(String str) {
        this.mAddress = str;
    }

    public int getDialStatus() {
        return this.mDialStatus;
    }

    public void putDialStatus(int r1) {
        this.mDialStatus = r1;
    }

    public int getDialCtrl() {
        return this.mDialCtrl;
    }

    public void putDialCtrl(int r1) {
        this.mDialCtrl = r1;
    }

    public int getAcceptCallType() {
        return this.mAcceptCallType;
    }

    public void putAcceptCallType(int r1) {
        this.mAcceptCallType = r1;
    }

    public int getRedialType() {
        return this.mRedialType;
    }

    public void putRedialType(int r1) {
        this.mRedialType = r1;
    }

    public String getUuid() {
        return this.mUuid;
    }

    public void putUuid(String str) {
        this.mUuid = str;
    }

    protected VDBTDial(Parcel parcel) {
        readFromParcel(parcel);
    }

    @Override
    public void writeToParcel(Parcel parcel, int r2) {
        parcel.writeString(this.mNumber);
        parcel.writeString(this.mAddress);
        parcel.writeInt(this.mDialStatus);
        parcel.writeInt(this.mDialCtrl);
        parcel.writeInt(this.mAcceptCallType);
        parcel.writeInt(this.mRedialType);
        parcel.writeString(this.mUuid);
    }

    private void readFromParcel(Parcel parcel) {
        this.mNumber = parcel.readString();
        this.mAddress = parcel.readString();
        this.mDialStatus = parcel.readInt();
        this.mDialCtrl = parcel.readInt();
        this.mAcceptCallType = parcel.readInt();
        this.mRedialType = parcel.readInt();
        this.mUuid = parcel.readString();
    }

    @Override
    public String toString() {
        return "VDBTDial{dialStatus=" + this.mDialStatus + ", dialCtrl=" + this.mDialCtrl + ", acceptCallType=" + this.mAcceptCallType + ", redialType=" + this.mRedialType + "}";
    }

    public static VDBTDial getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDBTDial.class.getClassLoader());
        return (VDBTDial) vDEvent.getPayload().getParcelable(VDKey.STATUS);
    }

    public static Bundle createPayload(VDBTDial vDBTDial) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDBTDial.class.getClassLoader());
        bundle.putParcelable(VDKey.STATUS, vDBTDial);
        return bundle;
    }

    public static VDEvent createEvent(int r1, VDBTDial vDBTDial) {
        return new VDEvent(r1, createPayload(vDBTDial));
    }
}
