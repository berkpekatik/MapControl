/*
 * VDBus BT liste ilerleme verisi (araçla konuşmak için); Parcel alan
 * sırası değiştirilmemeli: address(String), index(int), total(int), key(String). OEM servisi DIAL_INFO_LIST ve
 * PHONE_BOOK_*_LIST* yayınlarına bunu "status" anahtarıyla koyar; sınıf yoksa Bundle açılırken (Android 11/12 ilk
 * erişimde tüm değerleri açar) "data" listesi de okunamaz.
 * toString() gizlilik için adres içermez.
 */
package com.desaysv.ivi.vdb.event.id.bt.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

public class VDBTListProgress implements Parcelable {
    public static final Parcelable.Creator<VDBTListProgress> CREATOR = new Parcelable.Creator<VDBTListProgress>() {
        @Override
        public VDBTListProgress createFromParcel(Parcel parcel) {
            return new VDBTListProgress(parcel);
        }

        @Override
        public VDBTListProgress[] newArray(int size) {
            return new VDBTListProgress[size];
        }
    };
    private int mIndex;
    private int mTotal;
    private String mAddress = null;
    private String mKey = null;

    public VDBTListProgress() {
    }

    protected VDBTListProgress(Parcel parcel) {
        readFromParcel(parcel);
    }

    public static VDEvent createEvent(int id, VDBTListProgress progress) {
        return new VDEvent(id, createPayload(progress));
    }

    public static Bundle createPayload(VDBTListProgress progress) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDBTListProgress.class.getClassLoader());
        bundle.putParcelable(VDKey.STATUS, progress);
        return bundle;
    }

    public static VDBTListProgress getValue(VDEvent event) {
        if (event == null || event.getPayload() == null) {
            return null;
        }
        event.getPayload().setClassLoader(VDBTListProgress.class.getClassLoader());
        return (VDBTListProgress) event.getPayload().getParcelable(VDKey.STATUS);
    }

    private void readFromParcel(Parcel parcel) {
        this.mAddress = parcel.readString();
        this.mIndex = parcel.readInt();
        this.mTotal = parcel.readInt();
        this.mKey = parcel.readString();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(this.mAddress);
        parcel.writeInt(this.mIndex);
        parcel.writeInt(this.mTotal);
        parcel.writeString(this.mKey);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public String getAddress() {
        return this.mAddress;
    }

    public void putAddress(String address) {
        this.mAddress = address;
    }

    public int getIndex() {
        return this.mIndex;
    }

    public void putIndex(int index) {
        this.mIndex = index;
    }

    public int getTotal() {
        return this.mTotal;
    }

    public void putTotal(int total) {
        this.mTotal = total;
    }

    public String getKey() {
        return this.mKey;
    }

    public void putKey(String key) {
        this.mKey = key;
    }

    @Override
    public String toString() {
        return "VDBTListProgress{index=" + this.mIndex + ", total=" + this.mTotal + "}";
    }
}
