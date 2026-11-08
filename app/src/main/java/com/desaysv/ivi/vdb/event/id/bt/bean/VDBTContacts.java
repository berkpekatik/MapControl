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

public class VDBTContacts implements Parcelable {
    public static final Parcelable.Creator<VDBTContacts> CREATOR = new Parcelable.Creator<VDBTContacts>() {
        @Override
        public VDBTContacts createFromParcel(Parcel parcel) {
            return new VDBTContacts(parcel);
        }

        @Override
        public VDBTContacts[] newArray(int r1) {
            return new VDBTContacts[r1];
        }
    };
    private ArrayList<VDBTContactsCard> mCardList;
    private String mId = null;
    private String mName = null;
    private String mNumber = null;
    private String mAddress = null;
    private String mFirstLetter = null;
    private String mQuanPin = null;
    private String mJianPin = null;
    private int mSortType = 0;
    private int mPhoneType = 0;
    private byte[] mPortrait = null;

    @Override
    public int describeContents() {
        return 0;
    }

    public VDBTContacts() {
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

    public int getSortType() {
        return this.mSortType;
    }

    public void putSortType(int r1) {
        this.mSortType = r1;
    }

    public int getPhoneType() {
        return this.mPhoneType;
    }

    public void putPhoneType(int r1) {
        this.mPhoneType = r1;
    }

    public byte[] getPortrait() {
        return this.mPortrait;
    }

    public void putPortrait(byte[] bArr) {
        this.mPortrait = bArr;
    }

    public ArrayList<VDBTContactsCard> getCardList() {
        return this.mCardList;
    }

    public void putCardList(ArrayList<VDBTContactsCard> arrayList) {
        this.mCardList = arrayList;
    }

    protected VDBTContacts(Parcel parcel) {
        readFromParcel(parcel);
    }

    @Override
    public void writeToParcel(Parcel parcel, int r2) {
        parcel.writeString(this.mId);
        parcel.writeString(this.mName);
        parcel.writeString(this.mNumber);
        parcel.writeString(this.mAddress);
        parcel.writeString(this.mFirstLetter);
        parcel.writeString(this.mQuanPin);
        parcel.writeString(this.mJianPin);
        parcel.writeInt(this.mSortType);
        parcel.writeInt(this.mPhoneType);
        parcel.writeByteArray(this.mPortrait);
        parcel.writeList(this.mCardList);
    }

    private void readFromParcel(Parcel parcel) {
        this.mId = parcel.readString();
        this.mName = parcel.readString();
        this.mNumber = parcel.readString();
        this.mAddress = parcel.readString();
        this.mFirstLetter = parcel.readString();
        this.mQuanPin = parcel.readString();
        this.mJianPin = parcel.readString();
        this.mSortType = parcel.readInt();
        this.mPhoneType = parcel.readInt();
        this.mPortrait = parcel.createByteArray();
        this.mCardList = parcel.readArrayList(VDBTContactsCard.class.getClassLoader());
    }

    @Override
    public String toString() {
        return "VDBTContacts{sortType=" + this.mSortType + ", phoneType=" + this.mPhoneType + ", cards=" + (this.mCardList == null ? 0 : this.mCardList.size()) + "}";
    }

    public static VDBTContacts getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDBTContacts.class.getClassLoader());
        return (VDBTContacts) vDEvent.getPayload().getParcelable(VDKey.DATA);
    }

    public static Bundle createPayload(VDBTContacts vDBTContacts) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDBTContacts.class.getClassLoader());
        bundle.putParcelable(VDKey.DATA, vDBTContacts);
        return bundle;
    }

    public static VDEvent createEvent(int r1, VDBTContacts vDBTContacts) {
        return new VDEvent(r1, createPayload(vDBTContacts));
    }

    public static ArrayList<VDBTContacts> getList(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDBTContacts.class.getClassLoader());
        return vDEvent.getPayload().getParcelableArrayList(VDKey.DATA);
    }

    public static Bundle createPayload(ArrayList<VDBTContacts> arrayList) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDBTContacts.class.getClassLoader());
        bundle.putParcelableArrayList(VDKey.DATA, arrayList);
        return bundle;
    }

    public static VDEvent createEvent(int r1, ArrayList<VDBTContacts> arrayList) {
        return new VDEvent(r1, createPayload(arrayList));
    }
}
