package com.iflytek.autofly.voicecore.aidl;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * Kayıt verisi geri çağrısı — ham PCM / durum (HMI APK jadx ile bire bir).
 */
public interface IRecListener extends IInterface {

    abstract class Stub extends Binder implements IRecListener {
        private static final String DESCRIPTOR = "com.iflytek.autofly.voicecore.aidl.IRecListener";
        static final int TRANSACTION_onRecordData = 1;
        static final int TRANSACTION_onRecordState = 2;

        private static class Proxy implements IRecListener {
            private final IBinder mRemote;

            Proxy(IBinder iBinder) {
                this.mRemote = iBinder;
            }

            @Override
            public IBinder asBinder() {
                return this.mRemote;
            }

            @Override
            public void onRecordData(byte[] bArr, int i, int i2) {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(DESCRIPTOR);
                    parcelObtain.writeByteArray(bArr);
                    parcelObtain.writeInt(i);
                    parcelObtain.writeInt(i2);
                    this.mRemote.transact(TRANSACTION_onRecordData, parcelObtain, parcelObtain2, 0);
                    parcelObtain2.readException();
                    parcelObtain2.readByteArray(bArr);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override
            public void onRecordState(int i) {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(DESCRIPTOR);
                    parcelObtain.writeInt(i);
                    this.mRemote.transact(TRANSACTION_onRecordState, parcelObtain, parcelObtain2, 0);
                    parcelObtain2.readException();
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IRecListener asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface iInterfaceQueryLocalInterface = iBinder.queryLocalInterface(DESCRIPTOR);
            return (iInterfaceQueryLocalInterface == null || !(iInterfaceQueryLocalInterface instanceof IRecListener))
                    ? new Proxy(iBinder)
                    : (IRecListener) iInterfaceQueryLocalInterface;
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel parcel, Parcel parcel2, int flags) {
            if (code == TRANSACTION_onRecordData) {
                parcel.enforceInterface(DESCRIPTOR);
                byte[] bArrCreateByteArray = parcel.createByteArray();
                onRecordData(bArrCreateByteArray, parcel.readInt(), parcel.readInt());
                parcel2.writeNoException();
                parcel2.writeByteArray(bArrCreateByteArray);
                return true;
            }
            if (code == TRANSACTION_onRecordState) {
                parcel.enforceInterface(DESCRIPTOR);
                onRecordState(parcel.readInt());
                parcel2.writeNoException();
                return true;
            }
            if (code != IBinder.INTERFACE_TRANSACTION) {
                try {
                    return super.onTransact(code, parcel, parcel2, flags);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
            parcel2.writeString(DESCRIPTOR);
            return true;
        }
    }

    void onRecordData(byte[] data, int length, int type);

    void onRecordState(int state);
}
