/*
 * VDBus BT müzik denetim verisi (araçla konuşmak için).
 * Parcel alan sırası değiştirilmemeli: OEM BT servisi (com.desaysv.ivi.vds.bt) MUSIC_PLAY yükünü bu
 * sınıf adıyla açar ve null denetimi yapmadan kullanır; uymayan bir parcel o servisi çökertebilir.
 * toString() sorgu/medya kimliği/adres/ekleri içermez (gizlilik).
 */
package com.desaysv.ivi.vdb.event.id.bt.bean;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

public class VDBTMusicPlay implements Parcelable {
    public static final Parcelable.Creator<VDBTMusicPlay> CREATOR = new Parcelable.Creator<VDBTMusicPlay>() {
        @Override
        public VDBTMusicPlay createFromParcel(Parcel parcel) {
            return new VDBTMusicPlay(parcel);
        }

        @Override
        public VDBTMusicPlay[] newArray(int n) {
            return new VDBTMusicPlay[n];
        }
    };
    private int mPlayCtrl = 1;
    private int mPlayType = 0;
    private String mQuery = null;
    private String mMediaId = null;
    private Uri mUri = null;
    private Bundle mExtras = null;

    public VDBTMusicPlay() {
    }

    protected VDBTMusicPlay(Parcel parcel) {
        readFromParcel(parcel);
    }

    public static VDEvent createEvent(int id, VDBTMusicPlay vDBTMusicPlay) {
        return new VDEvent(id, createPayload(vDBTMusicPlay));
    }

    public static Bundle createPayload(VDBTMusicPlay vDBTMusicPlay) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDBTMusicPlay.class.getClassLoader());
        bundle.putParcelable(VDKey.STATUS, vDBTMusicPlay);
        return bundle;
    }

    public static VDBTMusicPlay getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDBTMusicPlay.class.getClassLoader());
        return (VDBTMusicPlay) vDEvent.getPayload().getParcelable(VDKey.STATUS);
    }

    private void readFromParcel(Parcel parcel) {
        this.mPlayCtrl = parcel.readInt();
        this.mPlayType = parcel.readInt();
        this.mQuery = parcel.readString();
        this.mMediaId = parcel.readString();
        this.mUri = Uri.parse(parcel.readString());
        this.mExtras = parcel.readBundle();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public Bundle getExtras() {
        return this.mExtras;
    }

    public String getMediaId() {
        return this.mMediaId;
    }

    public int getPlayCtrl() {
        return this.mPlayCtrl;
    }

    public int getPlayType() {
        return this.mPlayType;
    }

    public String getQuery() {
        return this.mQuery;
    }

    public Uri getUri() {
        return this.mUri;
    }

    public void putExtras(Bundle bundle) {
        this.mExtras = bundle;
    }

    public void putMediaId(String str) {
        this.mMediaId = str;
    }

    public void putPlayCtrl(int v) {
        this.mPlayCtrl = v;
    }

    public void putPlayType(int v) {
        this.mPlayType = v;
    }

    public void putQuery(String str) {
        this.mQuery = str;
    }

    public void putUri(Uri uri) {
        this.mUri = uri;
    }

    public String toString() {
        return "VDBTMusicPlay{playCtrl=" + this.mPlayCtrl + ", playType=" + this.mPlayType + "}";
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(this.mPlayCtrl);
        parcel.writeInt(this.mPlayType);
        parcel.writeString(this.mQuery);
        parcel.writeString(this.mMediaId);
        parcel.writeString(String.valueOf(this.mUri));
        parcel.writeBundle(this.mExtras);
    }
}
