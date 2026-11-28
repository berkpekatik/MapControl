package com.desaysv.ivi.vdb.event.id.vr.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;

public class VDVRPipeLine implements Parcelable {
    private String pkgName;
    private String key;
    private String value;
    private int resultCode;
    private int voiceRole;
    private boolean isMulti;
    private boolean isFlowRecognize;
    private String recognizeReceipt;
    private String unique;
    private boolean isTtsForbid;

    public VDVRPipeLine() {
    }

    public VDVRPipeLine(Parcel in) {
        this.pkgName = in.readString();
        this.key = in.readString();
        this.value = in.readString();
        this.resultCode = in.readInt();
        this.voiceRole = in.readInt();
        this.isMulti = in.readBoolean();
        this.isFlowRecognize = in.readBoolean();
        this.recognizeReceipt = in.readString();
        this.unique = in.readString();
        this.isTtsForbid = in.readBoolean();
    }

    public static final Creator<VDVRPipeLine> CREATOR = new Creator<VDVRPipeLine>() {
        @Override
        public VDVRPipeLine createFromParcel(Parcel in) {
            return new VDVRPipeLine(in);
        }

        @Override
        public VDVRPipeLine[] newArray(int size) {
            return new VDVRPipeLine[size];
        }
    };

    public static VDEvent createEvent(int eventId, VDVRPipeLine pipeLine) {
        Bundle payload = createPayload(pipeLine);
        return new VDEvent(eventId, payload);
    }

    public static Bundle createPayload(VDVRPipeLine pipeLine) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDVRPipeLine.class.getClassLoader());
        bundle.putParcelable("data", pipeLine);
        return bundle;
    }

    public static VDVRPipeLine getValue(VDEvent event) {
        if (event != null && event.getPayload() != null) {
            event.getPayload().setClassLoader(VDVRPipeLine.class.getClassLoader());
            return event.getPayload().getParcelable("data");
        }
        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.pkgName);
        dest.writeString(this.key);
        dest.writeString(this.value);
        dest.writeInt(this.resultCode);
        dest.writeInt(this.voiceRole);
        dest.writeBoolean(this.isMulti);
        dest.writeBoolean(this.isFlowRecognize);
        dest.writeString(this.recognizeReceipt);
        dest.writeString(this.unique);
        dest.writeBoolean(this.isTtsForbid);
    }

    public String getPkgName() { return pkgName; }
    public void setPkgName(String pkgName) { this.pkgName = pkgName; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public int getResultCode() { return resultCode; }
    public void setResultCode(int resultCode) { this.resultCode = resultCode; }
    public int getVoiceRole() { return voiceRole; }
    public void setVoiceRole(int voiceRole) { this.voiceRole = voiceRole; }
    public boolean isMulti() { return isMulti; }
    public void setMulti(boolean multi) { isMulti = multi; }
    public boolean isFlowRecognize() { return isFlowRecognize; }
    public void setFlowRecognize(boolean flowRecognize) { isFlowRecognize = flowRecognize; }
    public String getRecognizeReceipt() { return recognizeReceipt; }
    public void setRecognizeReceipt(String recognizeReceipt) { this.recognizeReceipt = recognizeReceipt; }
    public String getUnique() { return unique; }
    public void setUnique(String unique) { this.unique = unique; }
    public boolean isTtsForbid() { return isTtsForbid; }
    public void setTtsForbid(boolean ttsForbid) { isTtsForbid = ttsForbid; }
}
