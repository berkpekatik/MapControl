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

public class VDBTRecord implements Parcelable {
    public static final Parcelable.Creator<VDBTRecord> CREATOR = new Parcelable.Creator<VDBTRecord>() {
        @Override
        public VDBTRecord createFromParcel(Parcel parcel) {
            return new VDBTRecord(parcel);
        }

        @Override
        public VDBTRecord[] newArray(int r1) {
            return new VDBTRecord[r1];
        }
    };
    private int mRecordType = 1;
    private String mId = null;
    private String mName = null;
    private String mNumber = null;
    private String mDateTime = null;
    private String mDuration = null;
    private String mAddress = null;
    private String mFirstLetter = null;
    private String mQuanPin = null;
    private String mJianPin = null;

    @Override
    public int describeContents() {
        return 0;
    }

    public VDBTRecord() {
    }

    public int getRecordType() {
        return this.mRecordType;
    }

    public void putRecordType(int r1) {
        this.mRecordType = r1;
    }

    public String getId() {
        return this.mId;
    }

    public void putId(String str) {
        this.mId = str;
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

    public String getDateTime() {
        return this.mDateTime;
    }

    public void putDateTime(String str) {
        this.mDateTime = str;
    }

    public String getDuration() {
        return this.mDuration;
    }

    public void putDuration(String str) {
        this.mDuration = str;
    }

    public String getAddress() {
        return this.mAddress;
    }

    public void putAddress(String str) {
        this.mAddress = str;
    }

    public String getFirstLetter() {
        return this.mFirstLetter;
    }

    public void putFirstLetter(String str) {
        this.mFirstLetter = str;
    }

    public String getQuanPin() {
        return this.mQuanPin;
    }

    public void putQuanPin(String str) {
        this.mQuanPin = str;
    }

    public String getJianPin() {
        return this.mJianPin;
    }

    public void putJianPin(String str) {
        this.mJianPin = str;
    }

    protected VDBTRecord(Parcel parcel) {
        readFromParcel(parcel);
    }

    @Override
    public void writeToParcel(Parcel parcel, int r2) {
        parcel.writeInt(this.mRecordType);
        parcel.writeString(this.mId);
        parcel.writeString(this.mName);
        parcel.writeString(this.mNumber);
        parcel.writeString(this.mDateTime);
        parcel.writeString(this.mDuration);
        parcel.writeString(this.mAddress);
        parcel.writeString(this.mFirstLetter);
        parcel.writeString(this.mQuanPin);
        parcel.writeString(this.mJianPin);
    }

    private void readFromParcel(Parcel parcel) {
        this.mRecordType = parcel.readInt();
        this.mId = parcel.readString();
        this.mName = parcel.readString();
        this.mNumber = parcel.readString();
        this.mDateTime = parcel.readString();
        this.mDuration = parcel.readString();
        this.mAddress = parcel.readString();
        this.mFirstLetter = parcel.readString();
        this.mQuanPin = parcel.readString();
        this.mJianPin = parcel.readString();
    }

    @Override
    public String toString() {
        return "VDBTRecord{recordType=" + this.mRecordType + "}";
    }

    public static VDBTRecord getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDBTRecord.class.getClassLoader());
        return (VDBTRecord) vDEvent.getPayload().getParcelable(VDKey.DATA);
    }

    public static Bundle createPayload(VDBTRecord vDBTRecord) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDBTRecord.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, vDBTRecord);
        return bundle;
    }

    public static VDEvent createEvent(int r1, VDBTRecord vDBTRecord) {
        return new VDEvent(r1, createPayload(vDBTRecord));
    }

    public static ArrayList<VDBTRecord> getList(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDBTRecord.class.getClassLoader());
        return vDEvent.getPayload().getParcelableArrayList(VDKey.DATA);
    }

    public static Bundle createPayload(ArrayList<VDBTRecord> arrayList) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDBTRecord.class.getClassLoader());
        bundle.putParcelableArrayList(VDKey.DATA, arrayList);
        return bundle;
    }

    public static VDEvent createEvent(int r1, ArrayList<VDBTRecord> arrayList) {
        return new VDEvent(r1, createPayload(arrayList));
    }
}
