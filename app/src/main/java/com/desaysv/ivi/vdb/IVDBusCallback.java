package com.desaysv.ivi.vdb;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.desaysv.ivi.vdb.event.VDEvent;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public interface IVDBusCallback extends IInterface {
    void onVDBusCallback(VDEvent vDEvent);

    void onVDBusNotify(VDEvent vDEvent);

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public static abstract class Stub extends Binder implements IVDBusCallback {
        private static final String DESCRIPTOR = "com.desaysv.ivi.vdb.IVDBusCallback";
        static final int TRANSACTION_onVDBusCallback = 1;
        static final int TRANSACTION_onVDBusNotify = 2;

        /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
        private static class Proxy implements IVDBusCallback {
            private IBinder mRemote;

            Proxy(IBinder iBinder) {
                this.mRemote = iBinder;
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            public void onVDBusCallback(VDEvent vDEEvent) {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (vDEEvent != null) {
                        obtain.writeInt(1);
                        vDEEvent.writeToParcel(obtain, 0);
                    } else {
                        obtain.writeInt(0);
                    }
                    try {
                        this.mRemote.transact(1, obtain, obtain2, 0);
                    } catch (RemoteException e) {
                        // convert to unchecked to satisfy callers
                        throw new RuntimeException(e);
                    }
                    obtain2.readException();
                    obtain2.recycle();
                    obtain.recycle();
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw new RuntimeException(th);
                }
            }

            public void onVDBusNotify(VDEvent vDEvent) {
                Parcel obtain = Parcel.obtain();
                obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                if (vDEvent != null) {
                    obtain.writeInt(1);
                    vDEvent.writeToParcel(obtain, 0);
                } else {
                    obtain.writeInt(0);
                }
                try {
                    this.mRemote.transact(2, obtain, (Parcel) null, 1);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                } finally {
                    obtain.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IVDBusCallback asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(DESCRIPTOR);
            if (queryLocalInterface == null || !(queryLocalInterface instanceof IVDBusCallback)) {
                return new Proxy(iBinder);
            }
            return (IVDBusCallback) queryLocalInterface;
        }

        public boolean onTransact(int i9, Parcel parcel, Parcel parcel2, int i10) throws RemoteException {
            VDEvent vDEvent = null;
            if (i9 == 1) {
                parcel.enforceInterface(DESCRIPTOR);
                if (parcel.readInt() != 0) {
                    vDEvent = VDEvent.CREATOR.createFromParcel(parcel);
                }
                onVDBusCallback(vDEvent);
                parcel2.writeNoException();
                return true;
            } else if (i9 == 2) {
                parcel.enforceInterface(DESCRIPTOR);
                if (parcel.readInt() != 0) {
                    vDEvent = VDEvent.CREATOR.createFromParcel(parcel);
                }
                onVDBusNotify(vDEvent);
                return true;
            } else if (i9 != 1598968902) {
                return super.onTransact(i9, parcel, parcel2, i10);
            } else {
                parcel2.writeString(DESCRIPTOR);
                return true;
            }
        }

        public IBinder asBinder() {
            return this;
        }
    }
}
