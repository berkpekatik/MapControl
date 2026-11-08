/*
 * VDBus medya kaynağı ek bilgisi (araçla konuşmak için). Parcel alan sırası değiştirilmemeli: medya servisi MEDIA_SOURCE_EXT_INFO cevabını bu sınıf adıyla
 * yazar, alıcı aynı adlı ve aynı sıralı sınıfla açar. Kart yalnızca okur (USB bağlı mı, connectState).
 */
package com.desaysv.ivi.vdb.event.id.media.bean;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

public class VDMediaExtInfo implements Parcelable {
    public static final Parcelable.Creator<VDMediaExtInfo> CREATOR = new Parcelable.Creator<VDMediaExtInfo>() {
        @Override
        public VDMediaExtInfo createFromParcel(Parcel parcel) {
            return new VDMediaExtInfo(parcel);
        }

        @Override
        public VDMediaExtInfo[] newArray(int n) {
            return new VDMediaExtInfo[n];
        }
    };
    private int connectState;
    private int cyclicMode;
    private Uri mediaList;
    private int mediaType;

    @Override
    public int describeContents() {
        return 0;
    }

    public VDMediaExtInfo() {
    }

    public int getCyclicMode() {
        return this.cyclicMode;
    }

    public void putCyclicMode(int v) {
        this.cyclicMode = v;
    }

    public Uri getMediaList() {
        return this.mediaList;
    }

    public void putMediaList(Uri uri) {
        this.mediaList = uri;
    }

    public int getMediaType() {
        return this.mediaType;
    }

    public void putMediaType(int v) {
        this.mediaType = v;
    }

    public int getConnectState() {
        return this.connectState;
    }

    public void putConnectState(int v) {
        this.connectState = v;
    }

    protected VDMediaExtInfo(Parcel parcel) {
        this.mediaType = parcel.readInt();
        this.cyclicMode = parcel.readInt();
        this.connectState = parcel.readInt();
        this.mediaList = (Uri) parcel.readParcelable(Uri.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(this.mediaType);
        parcel.writeInt(this.cyclicMode);
        parcel.writeInt(this.connectState);
        parcel.writeParcelable(this.mediaList, flags);
    }

    public String toString() {
        return "VDMediaExtInfo{mediaType=" + this.mediaType + ", cyclicMode=" + this.cyclicMode + ", connectState=" + this.connectState + '}';
    }

    public static VDMediaExtInfo getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDMediaExtInfo.class.getClassLoader());
        return (VDMediaExtInfo) vDEvent.getPayload().getParcelable(VDKey.INFO);
    }

    public static Bundle createPayload(VDMediaExtInfo vDMediaExtInfo) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMediaExtInfo.class.getClassLoader());
        bundle.putParcelable(VDKey.INFO, vDMediaExtInfo);
        return bundle;
    }

    public static VDEvent createEvent(int id, VDMediaExtInfo vDMediaExtInfo) {
        return new VDEvent(id, createPayload(vDMediaExtInfo));
    }
}
