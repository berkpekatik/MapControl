package com.desaysv.ivi.vdb.event.id.media.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/**
 * VDBus medya oynatma yükü (MEDIA_SOURCE_PLAY_STATUS). Paket adı ve Parcel sırası servisle aynı olmalı:
 * sunucu sınıfı adıyla çözer.
 */
public class VDMediaPlay implements Parcelable {
    public static final Parcelable.Creator<VDMediaPlay> CREATOR = new Parcelable.Creator<VDMediaPlay>() {
        @Override
        public VDMediaPlay createFromParcel(Parcel parcel) {
            return new VDMediaPlay(parcel);
        }

        @Override
        public VDMediaPlay[] newArray(int size) {
            return new VDMediaPlay[size];
        }
    };
    // Biçim bayrağı com.mapcontrol.launcher.MediaPlayWire'da: bu sınıf bazı araçlarda sistemdeki vdbus.jar ile
    // gölgelenir, buraya eklenen statik üye orada bulunmaz (NoSuchMethodError ile çökme). Buraya kendi üyemizi ekleme.
    private long actions;
    private VDMediaPlayActionInfo mediaPlayActionInfo;
    private int mediaType;
    private int playAction;
    private int playStatus;
    private int searchStatus;

    public VDMediaPlay() {
    }

    protected VDMediaPlay(Parcel parcel) {
        mediaType = parcel.readInt();
        playStatus = parcel.readInt();
        playAction = parcel.readInt();
        searchStatus = parcel.readInt();
        if (com.mapcontrol.launcher.MediaPlayWire.withActions) {
            actions = parcel.readLong();
        }
        mediaPlayActionInfo = parcel.readParcelable(VDMediaPlayActionInfo.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mediaType);
        parcel.writeInt(playStatus);
        parcel.writeInt(playAction);
        parcel.writeInt(searchStatus);
        if (com.mapcontrol.launcher.MediaPlayWire.withActions) {
            parcel.writeLong(actions);
        }
        parcel.writeParcelable(mediaPlayActionInfo, flags);
    }

    public int getMediaType() {
        return mediaType;
    }

    public void putMediaType(int value) {
        mediaType = value;
    }

    public int getPlayStatus() {
        return playStatus;
    }

    public void putPlayStatus(int value) {
        playStatus = value;
    }

    public int getPlayAction() {
        return playAction;
    }

    public void putPlayAction(int value) {
        playAction = value;
    }

    public VDMediaPlayActionInfo getMediaPlayActionInfo() {
        return mediaPlayActionInfo;
    }

    public void putMediaPlayActionInfo(VDMediaPlayActionInfo value) {
        mediaPlayActionInfo = value;
    }

    public static VDMediaPlay getValue(VDEvent event) {
        if (event == null || event.getPayload() == null) {
            return null;
        }
        event.getPayload().setClassLoader(VDMediaPlay.class.getClassLoader());
        return event.getPayload().getParcelable(VDKey.STATUS);
    }

    public static Bundle createPayload(VDMediaPlay value) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMediaPlay.class.getClassLoader());
        bundle.putParcelable(VDKey.STATUS, value);
        return bundle;
    }

    public static VDEvent createEvent(int id, VDMediaPlay value) {
        return new VDEvent(id, createPayload(value));
    }

    @Override
    public String toString() {
        return "VDMediaPlay{mediaType=" + mediaType + ", playStatus=" + playStatus + ", playAction=" + playAction
                + ", searchStatus=" + searchStatus + ", info=" + mediaPlayActionInfo + "}";
    }
}
