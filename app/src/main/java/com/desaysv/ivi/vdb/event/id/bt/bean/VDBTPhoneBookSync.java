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

public class VDBTPhoneBookSync implements Parcelable {
    public static final Parcelable.Creator<VDBTPhoneBookSync> CREATOR = new Parcelable.Creator<VDBTPhoneBookSync>() {
        @Override
        public VDBTPhoneBookSync createFromParcel(Parcel parcel) {
            return new VDBTPhoneBookSync(parcel);
        }

        @Override
        public VDBTPhoneBookSync[] newArray(int r1) {
            return new VDBTPhoneBookSync[r1];
        }
    };
    private String mSyncAddress = null;
    private int mSyncType = 7;
    private int mSyncStatus = 20;
    private int mSyncCtrl = 1;
    private String mSyncKey = null;

    @Override
    public int describeContents() {
        return 0;
    }

    public VDBTPhoneBookSync() {
    }

    public String getSyncAddress() {
        return this.mSyncAddress;
    }

    public void putSyncAddress(String str) {
        this.mSyncAddress = str;
    }

    public int getSyncType() {
        return this.mSyncType;
    }

    public void putSyncType(int r1) {
        this.mSyncType = r1;
    }

    public int getSyncStatus() {
        return this.mSyncStatus;
    }

    public void putSyncStatus(int r1) {
        this.mSyncStatus = r1;
    }

    public int getSyncCtrl() {
        return this.mSyncCtrl;
    }

    public void putSyncCtrl(int r1) {
        this.mSyncCtrl = r1;
    }

    public String getSyncKey() {
        return this.mSyncKey;
    }

    public void putSyncKey(String str) {
        this.mSyncKey = str;
    }

    protected VDBTPhoneBookSync(Parcel parcel) {
        readFromParcel(parcel);
    }

    @Override
    public void writeToParcel(Parcel parcel, int r2) {
        parcel.writeString(this.mSyncAddress);
        parcel.writeInt(this.mSyncType);
        parcel.writeInt(this.mSyncStatus);
        parcel.writeInt(this.mSyncCtrl);
        parcel.writeString(this.mSyncKey);
    }

    private void readFromParcel(Parcel parcel) {
        this.mSyncAddress = parcel.readString();
        this.mSyncType = parcel.readInt();
        this.mSyncStatus = parcel.readInt();
        this.mSyncCtrl = parcel.readInt();
        this.mSyncKey = parcel.readString();
    }

    @Override
    public String toString() {
        return "VDBTPhoneBookSync{syncType=" + this.mSyncType + ", syncStatus=" + this.mSyncStatus + ", syncCtrl=" + this.mSyncCtrl + "}";
    }

    public static VDBTPhoneBookSync getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDBTPhoneBookSync.class.getClassLoader());
        return (VDBTPhoneBookSync) vDEvent.getPayload().getParcelable(VDKey.STATUS);
    }

    public static Bundle createPayload(VDBTPhoneBookSync vDBTPhoneBookSync) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDBTPhoneBookSync.class.getClassLoader());
        bundle.putParcelable(VDKey.STATUS, vDBTPhoneBookSync);
        return bundle;
    }

    public static VDEvent createEvent(int r1, VDBTPhoneBookSync vDBTPhoneBookSync) {
        return new VDEvent(r1, createPayload(vDBTPhoneBookSync));
    }
}
