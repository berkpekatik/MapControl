package com.iflytek.autofly.voicecore.aidl;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * iFly {@code RecService} oturum kimliği (HMI APK jadx — {@code com.iflytek.cutefly.speechclient.hmi}).
 */
public class VoiceServiceId implements Parcelable {
    public static final Parcelable.Creator<VoiceServiceId> CREATOR = new Parcelable.Creator<VoiceServiceId>() {
        @Override
        public VoiceServiceId createFromParcel(Parcel parcel) {
            return new VoiceServiceId(parcel);
        }

        @Override
        public VoiceServiceId[] newArray(int i) {
            return new VoiceServiceId[i];
        }
    };

    private String id;

    public VoiceServiceId() {
        this.id = null;
    }

    public VoiceServiceId(String str) {
        this.id = str;
    }

    public VoiceServiceId(Parcel parcel) {
        readFromParcel(parcel);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        String str = this.id;
        String str2 = ((VoiceServiceId) obj).id;
        return str != null ? str.equals(str2) : str2 == null;
    }

    public String getId() {
        return this.id;
    }

    @Override
    public int hashCode() {
        String str = this.id;
        return str != null ? str.hashCode() : 0;
    }

    public void readFromParcel(Parcel parcel) {
        this.id = parcel.readString();
    }

    public void setId(String str) {
        this.id = str;
    }

    @Override
    public String toString() {
        return "VoiceServiceId{id='" + this.id + "'}";
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(this.id);
    }
}
