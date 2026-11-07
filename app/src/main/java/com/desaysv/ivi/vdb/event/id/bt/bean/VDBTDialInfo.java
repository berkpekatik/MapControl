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

public class VDBTDialInfo implements Parcelable {
    public static final Parcelable.Creator<VDBTDialInfo> CREATOR = new Parcelable.Creator<VDBTDialInfo>() {
        @Override
        public VDBTDialInfo createFromParcel(Parcel parcel) {
            return new VDBTDialInfo(parcel);
        }

        @Override
        public VDBTDialInfo[] newArray(int r1) {
            return new VDBTDialInfo[r1];
        }
    };
    private boolean mInBandRing;
    private boolean mMultiParty;
    private boolean mOutGoing;
    private String mName = null;
    private String mNumber = null;
    private String mAddress = null;
    private long mTime = 0;
    private String mUUID = null;
    private int mId = 0;
    private int mState = 0;
    private int mRecordType = 0;
    private int mNumberType = 1;
    private byte[] mPortrait = null;

    @Override
    public int describeContents() {
        return 0;
    }

    public VDBTDialInfo() {
    }

    public String getName() {
        return this.mName;
    }

    public void putName(String str) {
        this.mName = str;
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

    public long getTime() {
        return this.mTime;
    }

    public void putTime(long j) {
        this.mTime = j;
    }

    public String getUUID() {
        return this.mUUID;
    }

    public void putUUID(String str) {
        this.mUUID = str;
    }

    public int getId() {
        return this.mId;
    }

    public void putId(int r1) {
        this.mId = r1;
    }

    public int getState() {
        return this.mState;
    }

    public void putState(int r1) {
        this.mState = r1;
    }

    public int getRecordType() {
        return this.mRecordType;
    }

    public void putRecordType(int r1) {
        this.mRecordType = r1;
    }

    public int getNumberType() {
        return this.mNumberType;
    }

    public void putNumberType(int r1) {
        this.mNumberType = r1;
    }

    public byte[] getPortrait() {
        return this.mPortrait;
    }

    public void putPortrait(byte[] bArr) {
        this.mPortrait = bArr;
    }

    protected VDBTDialInfo(Parcel parcel) {
        readFromParcel(parcel);
    }

    @Override
    public void writeToParcel(Parcel parcel, int r4) {
        parcel.writeString(this.mName);
        parcel.writeString(this.mNumber);
        parcel.writeString(this.mAddress);
        parcel.writeLong(this.mTime);
        parcel.writeString(this.mUUID);
        parcel.writeInt(this.mId);
        parcel.writeInt(this.mState);
        parcel.writeInt(this.mRecordType);
        parcel.writeInt(this.mNumberType);
        parcel.writeByteArray(this.mPortrait);
        parcel.writeByte(this.mInBandRing ? (byte) 1 : (byte) 0);
        parcel.writeByte(this.mMultiParty ? (byte) 1 : (byte) 0);
        parcel.writeByte(this.mOutGoing ? (byte) 1 : (byte) 0);
    }

    private void readFromParcel(Parcel parcel) {
        this.mName = parcel.readString();
        this.mNumber = parcel.readString();
        this.mAddress = parcel.readString();
        this.mTime = parcel.readLong();
        this.mUUID = parcel.readString();
        this.mId = parcel.readInt();
        this.mState = parcel.readInt();
        this.mRecordType = parcel.readInt();
        this.mNumberType = parcel.readInt();
        this.mPortrait = parcel.createByteArray();
        this.mInBandRing = parcel.readByte() != 0;
        this.mMultiParty = parcel.readByte() != 0;
        this.mOutGoing = parcel.readByte() != 0;
    }

    @Override
    public String toString() {
        return "VDBTDialInfo{id=" + this.mId + ", state=" + this.mState + ", recordType=" + this.mRecordType + ", numberType=" + this.mNumberType + ", inBandRing=" + this.mInBandRing + ", multiParty=" + this.mMultiParty + ", outGoing=" + this.mOutGoing + "}";
    }

    public static VDBTDialInfo getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDBTDialInfo.class.getClassLoader());
        return (VDBTDialInfo) vDEvent.getPayload().getParcelable(VDKey.INFO);
    }

    public static Bundle createPayload(VDBTDialInfo vDBTDialInfo) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDBTDialInfo.class.getClassLoader());
        bundle.putParcelable(VDKey.INFO, vDBTDialInfo);
        return bundle;
    }

    public static VDEvent createEvent(int r1, VDBTDialInfo vDBTDialInfo) {
        return new VDEvent(r1, createPayload(vDBTDialInfo));
    }

    public static ArrayList<VDBTDialInfo> getList(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDBTDialInfo.class.getClassLoader());
        return vDEvent.getPayload().getParcelableArrayList(VDKey.DATA);
    }

    public static Bundle createPayload(ArrayList<VDBTDialInfo> arrayList) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDBTDialInfo.class.getClassLoader());
        bundle.putParcelableArrayList(VDKey.DATA, arrayList);
        return bundle;
    }

    public static VDEvent createEvent(int r1, ArrayList<VDBTDialInfo> arrayList) {
        return new VDEvent(r1, createPayload(arrayList));
    }

    public void setInBandRing(boolean z) {
        this.mInBandRing = z;
    }

    public boolean isInBandRing() {
        return this.mInBandRing;
    }

    public void setMultiParty(boolean z) {
        this.mMultiParty = z;
    }

    public boolean isMultiParty() {
        return this.mMultiParty;
    }

    public void setOutGoing(boolean z) {
        this.mOutGoing = z;
    }

    public boolean isOutGoing() {
        return this.mOutGoing;
    }
}
