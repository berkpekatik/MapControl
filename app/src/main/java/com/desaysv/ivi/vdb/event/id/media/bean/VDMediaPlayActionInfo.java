package com.desaysv.ivi.vdb.event.id.media.bean;

import android.os.Parcel;
import android.os.Parcelable;

/** [VDMediaPlay] eylem ayrıntısı; Parcel alan sırası araçla uyum için sabit. */
public class VDMediaPlayActionInfo implements Parcelable {
    public static final Parcelable.Creator<VDMediaPlayActionInfo> CREATOR = new Parcelable.Creator<VDMediaPlayActionInfo>() {
        @Override
        public VDMediaPlayActionInfo createFromParcel(Parcel parcel) {
            return new VDMediaPlayActionInfo(parcel);
        }

        @Override
        public VDMediaPlayActionInfo[] newArray(int size) {
            return new VDMediaPlayActionInfo[size];
        }
    };
    private int collectStatus;
    private int cyclicMode;
    private int mDisPlayView;
    private int mIntent;
    private int mReason;
    private int mSeekPosition = 0;
    private int mSeekStatus;

    public VDMediaPlayActionInfo() {
    }

    protected VDMediaPlayActionInfo(Parcel parcel) {
        mSeekPosition = parcel.readInt();
        mSeekStatus = parcel.readInt();
        mDisPlayView = parcel.readInt();
        mReason = parcel.readInt();
        mIntent = parcel.readInt();
        cyclicMode = parcel.readInt();
        collectStatus = parcel.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mSeekPosition);
        parcel.writeInt(mSeekStatus);
        parcel.writeInt(mDisPlayView);
        parcel.writeInt(mReason);
        parcel.writeInt(mIntent);
        parcel.writeInt(cyclicMode);
        parcel.writeInt(collectStatus);
    }

    public int getDisPlayView() {
        return mDisPlayView;
    }

    public void putDisPlayView(int value) {
        mDisPlayView = value;
    }

    public int getReason() {
        return mReason;
    }

    public void putReason(int value) {
        mReason = value;
    }

    public int getIntent() {
        return mIntent;
    }

    public void putIntent(int value) {
        mIntent = value;
    }

    @Override
    public String toString() {
        return "VDMediaPlayActionInfo{seekPosition=" + mSeekPosition + ", seekStatus=" + mSeekStatus + ", disPlayView="
                + mDisPlayView + ", reason=" + mReason + ", intent=" + mIntent + ", cyclicMode=" + cyclicMode
                + ", collectStatus=" + collectStatus + "}";
    }
}
