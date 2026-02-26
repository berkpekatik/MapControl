package com.desaysv.ivi.vdb.event.id.cabin.bean;

import android.os.Bundle;
import android.util.Log;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDBundleParcelable;
import com.desaysv.ivi.vdb.event.id.cabin.VDValueCabinLan;
import java.util.ArrayList;

/* loaded from: classes.dex */
public class VDCLCommonMessage extends VDBundleParcelable {
    private static final String BUNDLE_KEY_CONTENT = "CabinLanMessageData_Content";
    private static final String BUNDLE_KEY_SUB_TYPE = "CabinLanMessageData_SubType";
    private static final String PREFIX_CLASS_NAME = "CabinLanMessageData_";
    private static final String TAG = "VDCLCommonMessage";
    public static VDCLCommonMessage commonMessage = new VDCLCommonMessage();
    private String mContent;
    private ArrayList<VDCLCommonMsgKeyValue> mExtraData;
    private int mMsgResult;
    private int mMsgType;
    private int mSubtype;

    public VDCLCommonMessage() {
        this.mMsgResult = 0;
        this.mExtraData = new ArrayList<>();
    }

    public static VDEvent createEvent(int i2, VDCLCommonMessage vDCLCommonMessage) {
        return new VDEvent(i2, createPayload(vDCLCommonMessage));
    }

    public static VDEvent createGetEvent(int i2, VDCLCommonMessage vDCLCommonMessage) {
        return createEvent(i2, vDCLCommonMessage);
    }

    public static Bundle createPayload(VDCLCommonMessage vDCLCommonMessage) {
        Bundle bundle = new Bundle();
        vDCLCommonMessage.writeToBundle(bundle);
        return bundle;
    }

    public static VDCLCommonMessage getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        commonMessage.readFromBundle(vDEvent.getPayload());
        return commonMessage;
    }

    public String getContent() {
        return this.mContent;
    }

    public ArrayList<VDCLCommonMsgKeyValue> getExtraData() {
        return this.mExtraData;
    }

    public int getMsgResult() {
        return this.mMsgResult;
    }

    public int getMsgType() {
        return this.mMsgType;
    }

    public int getSubtype() {
        return this.mSubtype;
    }

    @Override // com.desaysv.ivi.vdb.event.base.VDBundleParcelable
    protected void readFromBundle(Bundle bundle) {
        setMsgResult(bundle.getInt(VDValueCabinLan.RESULT_TYPE));
        setSubtype(bundle.getInt(BUNDLE_KEY_SUB_TYPE));
        setMessage(bundle.getString(BUNDLE_KEY_CONTENT));
        for (String str : bundle.keySet()) {
            if (!str.equals(BUNDLE_KEY_SUB_TYPE) && !str.equals(BUNDLE_KEY_CONTENT) && !str.equals(VDValueCabinLan.RESULT_TYPE)) {
                if (str.equals(VDValueCabinLan.MSG_TYPE)) {
                    setMsgType(bundle.getInt(VDValueCabinLan.MSG_TYPE));
                } else {
                    Object obj = bundle.get(str);
                    if (obj != null) {
                        if (obj instanceof int[]) {
                            VDCLCommonMsgKeyValue vDCLCommonMsgKeyValue = new VDCLCommonMsgKeyValue();
                            vDCLCommonMsgKeyValue.key = str;
                            vDCLCommonMsgKeyValue.value = (int[]) obj;
                            this.mExtraData.add(vDCLCommonMsgKeyValue);
                        } else {
                            Log.d(TAG, " value type not support " + str);
                        }
                    }
                }
            }
        }
    }

    public void setExtraData(ArrayList<VDCLCommonMsgKeyValue> arrayList) {
        this.mExtraData = arrayList;
    }

    public void setMessage(String str) {
        if (str == null) {
            str = "";
        }
        this.mContent = str;
    }

    public void setMsgResult(int i2) {
        this.mMsgResult = i2;
    }

    public void setMsgType(int i2) {
        this.mMsgType = i2;
    }

    public void setSubtype(int i2) {
        this.mSubtype = i2;
    }

    public String toString() {
        return "VDCLCommonMessage{mMsgResult=" + this.mMsgResult + ", mMsgType=" + this.mMsgType + ", mSubtype=" + this.mSubtype + ", mContent='" + this.mContent + "', mExtraData=" + this.mExtraData + '}';
    }

    @Override // com.desaysv.ivi.vdb.event.base.VDBundleParcelable
    protected void writeToBundle(Bundle bundle) {
        bundle.putInt(VDValueCabinLan.MSG_TYPE, getMsgType());
        bundle.putInt(BUNDLE_KEY_SUB_TYPE, getSubtype());
        bundle.putString(BUNDLE_KEY_CONTENT, getContent());
        if (this.mExtraData != null) {
            ArrayList<VDCLCommonMsgKeyValue> extraData = getExtraData();
            for (int i2 = 0; i2 < extraData.size(); i2++) {
                bundle.putIntArray(extraData.get(i2).key, extraData.get(i2).value);
            }
        }
    }

    public VDCLCommonMessage(Bundle bundle) {
        this.mMsgResult = 0;
        this.mExtraData = new ArrayList<>();
        readFromBundle(bundle);
    }

    public VDCLCommonMessage(int i2, String str) {
        this.mMsgResult = 0;
        this.mExtraData = new ArrayList<>();
        this.mSubtype = i2;
        this.mContent = str == null ? "" : str;
        this.mExtraData = null;
    }
}
