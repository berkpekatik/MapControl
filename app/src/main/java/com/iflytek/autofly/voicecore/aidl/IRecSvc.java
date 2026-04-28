package com.iflytek.autofly.voicecore.aidl;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * {@code RecService} AIDL arayüzü (HMI APK jadx — bire bir).
 */
public interface IRecSvc extends IInterface {

    abstract class Stub extends Binder implements IRecSvc {
        private static final String DESCRIPTOR = "com.iflytek.autofly.voicecore.aidl.IRecSvc";
        static final int TRANSACTION_createRecSession = 1;
        static final int TRANSACTION_sessionStart = 2;
        static final int TRANSACTION_sessionStop = 3;
        static final int TRANSACTION_destroyRecSession = 4;

        private static class Proxy implements IRecSvc {
            private final IBinder mRemote;

            Proxy(IBinder iBinder) {
                this.mRemote = iBinder;
            }

            @Override
            public IBinder asBinder() {
                return this.mRemote;
            }

            @Override
            public int createRecSession(VoiceServiceId voiceServiceId, IRecListener iRecListener) {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(DESCRIPTOR);
                    if (voiceServiceId != null) {
                        parcelObtain.writeInt(1);
                        voiceServiceId.writeToParcel(parcelObtain, 0);
                    } else {
                        parcelObtain.writeInt(0);
                    }
                    parcelObtain.writeStrongBinder(iRecListener != null ? iRecListener.asBinder() : null);
                    this.mRemote.transact(TRANSACTION_createRecSession, parcelObtain, parcelObtain2, 0);
                    parcelObtain2.readException();
                    return parcelObtain2.readInt();
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override
            public int destroyRecSession(VoiceServiceId voiceServiceId, IRecListener iRecListener) {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(DESCRIPTOR);
                    if (voiceServiceId != null) {
                        parcelObtain.writeInt(1);
                        voiceServiceId.writeToParcel(parcelObtain, 0);
                    } else {
                        parcelObtain.writeInt(0);
                    }
                    parcelObtain.writeStrongBinder(iRecListener != null ? iRecListener.asBinder() : null);
                    this.mRemote.transact(TRANSACTION_destroyRecSession, parcelObtain, parcelObtain2, 0);
                    parcelObtain2.readException();
                    return parcelObtain2.readInt();
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override
            public int sessionStart(VoiceServiceId voiceServiceId, int i) {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(DESCRIPTOR);
                    if (voiceServiceId != null) {
                        parcelObtain.writeInt(1);
                        voiceServiceId.writeToParcel(parcelObtain, 0);
                    } else {
                        parcelObtain.writeInt(0);
                    }
                    parcelObtain.writeInt(i);
                    this.mRemote.transact(TRANSACTION_sessionStart, parcelObtain, parcelObtain2, 0);
                    parcelObtain2.readException();
                    return parcelObtain2.readInt();
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override
            public int sessionStop(VoiceServiceId voiceServiceId) {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(DESCRIPTOR);
                    if (voiceServiceId != null) {
                        parcelObtain.writeInt(1);
                        voiceServiceId.writeToParcel(parcelObtain, 0);
                    } else {
                        parcelObtain.writeInt(0);
                    }
                    this.mRemote.transact(TRANSACTION_sessionStop, parcelObtain, parcelObtain2, 0);
                    parcelObtain2.readException();
                    return parcelObtain2.readInt();
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

        public static IRecSvc asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface iInterfaceQueryLocalInterface = iBinder.queryLocalInterface(DESCRIPTOR);
            return (iInterfaceQueryLocalInterface == null || !(iInterfaceQueryLocalInterface instanceof IRecSvc))
                    ? new Proxy(iBinder)
                    : (IRecSvc) iInterfaceQueryLocalInterface;
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel parcel, Parcel parcel2, int flags) {
            if (code == TRANSACTION_createRecSession) {
                parcel.enforceInterface(DESCRIPTOR);
                int iCreateRecSession = createRecSession(
                        parcel.readInt() != 0 ? VoiceServiceId.CREATOR.createFromParcel(parcel) : null,
                        IRecListener.Stub.asInterface(parcel.readStrongBinder()));
                parcel2.writeNoException();
                parcel2.writeInt(iCreateRecSession);
                return true;
            }
            if (code == TRANSACTION_sessionStart) {
                parcel.enforceInterface(DESCRIPTOR);
                int iSessionStart = sessionStart(
                        parcel.readInt() != 0 ? VoiceServiceId.CREATOR.createFromParcel(parcel) : null,
                        parcel.readInt());
                parcel2.writeNoException();
                parcel2.writeInt(iSessionStart);
                return true;
            }
            if (code == TRANSACTION_sessionStop) {
                parcel.enforceInterface(DESCRIPTOR);
                int iSessionStop = sessionStop(
                        parcel.readInt() != 0 ? VoiceServiceId.CREATOR.createFromParcel(parcel) : null);
                parcel2.writeNoException();
                parcel2.writeInt(iSessionStop);
                return true;
            }
            if (code == TRANSACTION_destroyRecSession) {
                parcel.enforceInterface(DESCRIPTOR);
                int iDestroyRecSession = destroyRecSession(
                        parcel.readInt() != 0 ? VoiceServiceId.CREATOR.createFromParcel(parcel) : null,
                        IRecListener.Stub.asInterface(parcel.readStrongBinder()));
                parcel2.writeNoException();
                parcel2.writeInt(iDestroyRecSession);
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

    int createRecSession(VoiceServiceId voiceServiceId, IRecListener iRecListener);

    int destroyRecSession(VoiceServiceId voiceServiceId, IRecListener iRecListener);

    int sessionStart(VoiceServiceId voiceServiceId, int dataType);

    int sessionStop(VoiceServiceId voiceServiceId);
}
