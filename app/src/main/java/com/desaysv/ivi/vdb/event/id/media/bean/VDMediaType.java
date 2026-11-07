/*
 * VDBus medya kaynağı türü verisi (araçla konuşmak için).
 * Parcel alan sırası değiştirilmemeli: araç medya servisi CURRENT_SOURCE_TYPE cevabını bu sınıf adıyla yazar,
 * alıcı aynı adlı ve aynı sıralı sınıfla açar.
 */
package com.desaysv.ivi.vdb.event.id.media.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;

public class VDMediaType implements Parcelable {
    public static final Parcelable.Creator<VDMediaType> CREATOR = new Parcelable.Creator<VDMediaType>() {
        @Override
        public VDMediaType createFromParcel(Parcel parcel) {
            return new VDMediaType(parcel);
        }

        @Override
        public VDMediaType[] newArray(int n) {
            return new VDMediaType[n];
        }
    };
    private int mMediaType;
    private int mVdsLastSource;

    @Override
    public int describeContents() {
        return 0;
    }

    public VDMediaType() {
    }

    protected VDMediaType(Parcel parcel) {
        this.mMediaType = parcel.readInt();
        this.mVdsLastSource = parcel.readInt();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(this.mMediaType);
        parcel.writeInt(this.mVdsLastSource);
    }

    public int getVdsLastSource() {
        return this.mVdsLastSource;
    }

    public void putVdsLastSource(int v) {
        this.mVdsLastSource = v;
    }

    public int getMediaType() {
        return this.mMediaType;
    }

    public void putMediaType(int v) {
        this.mMediaType = v;
    }

    public String toString() {
        return "VDMediaType{mediaType=" + this.mMediaType + "mVdsLastSource=" + this.mVdsLastSource + "}";
    }

    public static VDMediaType getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDMediaType.class.getClassLoader());
        return (VDMediaType) vDEvent.getPayload().getParcelable("type");
    }

    public static Bundle createPayload(VDMediaType vDMediaType) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMediaType.class.getClassLoader());
        bundle.putParcelable("type", vDMediaType);
        return bundle;
    }

    public static VDEvent createEvent(int id, VDMediaType vDMediaType) {
        return new VDEvent(id, createPayload(vDMediaType));
    }
}
