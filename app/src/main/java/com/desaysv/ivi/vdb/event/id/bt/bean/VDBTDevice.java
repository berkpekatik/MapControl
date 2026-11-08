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
import java.util.ArrayList;

public class VDBTDevice implements Parcelable {
    public static final Parcelable.Creator<VDBTDevice> CREATOR = new Parcelable.Creator<VDBTDevice>() {
        @Override
        public VDBTDevice createFromParcel(Parcel parcel) {
            return new VDBTDevice(parcel);
        }

        @Override
        public VDBTDevice[] newArray(int r1) {
            return new VDBTDevice[r1];
        }
    };
    private String mMac = null;
    private String mName = null;
    private int mDeviceType = 0;
    private int mPairStatus = 1;
    private int mConnectStatus = 1;
    private int mHfpStatus = 1;
    private int mA2dpStatus = 1;
    private int mAvrcpStatus = 1;
    private int mPbapStatus = 1;
    private int mMapStatus = 1;
    private int mDeviceClass = 0;

    @Override
    public int describeContents() {
        return 0;
    }

    public VDBTDevice() {
    }

    public String getMac() {
        return this.mMac;
    }

    public void putMac(String str) {
        this.mMac = str;
    }

    public String getName() {
        return this.mName;
    }

    public void putName(String str) {
        this.mName = str;
    }

    public int getPairStatus() {
        return this.mPairStatus;
    }

    public void putPairStatus(int r1) {
        this.mPairStatus = r1;
    }

    public int getConnectStatus() {
        return this.mConnectStatus;
    }

    public void putConnectStatus(int r1) {
        this.mConnectStatus = r1;
    }

    public int getHfpStatus() {
        return this.mHfpStatus;
    }

    public void putHfpStatus(int r1) {
        this.mHfpStatus = r1;
    }

    public int getA2dpStatus() {
        return this.mA2dpStatus;
    }

    public void putA2dpStatus(int r1) {
        this.mA2dpStatus = r1;
    }

    public int getAvrcpStatus() {
        return this.mAvrcpStatus;
    }

    public void putAvrcpStatus(int r1) {
        this.mAvrcpStatus = r1;
    }

    public int getPbapStatus() {
        return this.mPbapStatus;
    }

    public void putPbapStatus(int r1) {
        this.mPbapStatus = r1;
    }

    public int getMapStatus() {
        return this.mMapStatus;
    }

    public int getDeviceType() {
        return this.mDeviceType;
    }

    public void putDeviceType(int r1) {
        this.mDeviceType = r1;
    }

    public void putMapStatus(int r1) {
        this.mMapStatus = r1;
    }

    protected VDBTDevice(Parcel parcel) {
        readFromParcel(parcel);
    }

    @Override
    public void writeToParcel(Parcel parcel, int r2) {
        parcel.writeString(this.mMac);
        parcel.writeString(this.mName);
        parcel.writeInt(this.mDeviceType);
        parcel.writeInt(this.mPairStatus);
        parcel.writeInt(this.mConnectStatus);
        parcel.writeInt(this.mHfpStatus);
        parcel.writeInt(this.mA2dpStatus);
        parcel.writeInt(this.mAvrcpStatus);
        parcel.writeInt(this.mPbapStatus);
        parcel.writeInt(this.mMapStatus);
        parcel.writeInt(this.mDeviceClass);
    }

    private void readFromParcel(Parcel parcel) {
        this.mMac = parcel.readString();
        this.mName = parcel.readString();
        this.mDeviceType = parcel.readInt();
        this.mPairStatus = parcel.readInt();
        this.mConnectStatus = parcel.readInt();
        this.mHfpStatus = parcel.readInt();
        this.mA2dpStatus = parcel.readInt();
        this.mAvrcpStatus = parcel.readInt();
        this.mPbapStatus = parcel.readInt();
        this.mMapStatus = parcel.readInt();
        this.mDeviceClass = parcel.readInt();
    }

    @Override
    public String toString() {
        return "VDBTDevice{deviceType=" + this.mDeviceType + ", pairStatus=" + this.mPairStatus + ", connectStatus=" + this.mConnectStatus + ", hfpStatus=" + this.mHfpStatus + ", a2dpStatus=" + this.mA2dpStatus + ", avrcpStatus=" + this.mAvrcpStatus + ", pbapStatus=" + this.mPbapStatus + ", mapStatus=" + this.mMapStatus + ", deviceClass=" + this.mDeviceClass + "}";
    }

    public static VDBTDevice getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDBTDevice.class.getClassLoader());
        return (VDBTDevice) vDEvent.getPayload().getParcelable(VDKey.DATA);
    }

    public static Bundle createPayload(VDBTDevice vDBTDevice) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDBTDevice.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, vDBTDevice);
        return bundle;
    }

    public static VDEvent createEvent(int r1, VDBTDevice vDBTDevice) {
        return new VDEvent(r1, createPayload(vDBTDevice));
    }

    public static ArrayList<VDBTDevice> getList(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDBTDevice.class.getClassLoader());
        return vDEvent.getPayload().getParcelableArrayList(VDKey.DATA);
    }

    public static Bundle createPayload(ArrayList<VDBTDevice> arrayList) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDBTDevice.class.getClassLoader());
        bundle.putParcelableArrayList(VDKey.DATA, arrayList);
        return bundle;
    }

    public static VDEvent createEvent(int r1, ArrayList<VDBTDevice> arrayList) {
        return new VDEvent(r1, createPayload(arrayList));
    }

    public int getDeviceClass() {
        return this.mDeviceClass;
    }

    public void setDeviceClass(int r1) {
        this.mDeviceClass = r1;
    }
}
