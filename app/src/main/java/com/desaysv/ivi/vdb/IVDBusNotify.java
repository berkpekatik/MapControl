package com.desaysv.ivi.vdb;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.util.Log;
import android.os.RemoteException;
import com.desaysv.ivi.vdb.event.VDEvent;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public interface IVDBusNotify extends IInterface {
    void onVDBusNotify(VDEvent vDEvent);

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public static abstract class Stub extends Binder implements IVDBusNotify {
        private static final String DESCRIPTOR = "com.desaysv.ivi.vdb.IVDBusNotify";
        static final int TRANSACTION_onVDBusNotify = 1;

        /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
        private static class Proxy implements IVDBusNotify {
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

            public void onVDBusNotify(VDEvent vDEvent) {
                Log.e("onVDBusNotify", "onVDBusNotify");
                Parcel obtain = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (vDEvent != null) {
                        obtain.writeInt(1);
                        vDEvent.writeToParcel(obtain, 0);
                    } else {
                        obtain.writeInt(0);
                    }
                    try {
                        this.mRemote.transact(1, obtain, (Parcel) null, 1);
                    } catch (RemoteException e) {
                        // Swallow or log; no checked exception leakage
                        Log.e("onVDBusNotify", "transact failed", e);
                    }
                } finally {
                    obtain.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IVDBusNotify asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(DESCRIPTOR);
            if (queryLocalInterface instanceof IVDBusNotify) {
                return (IVDBusNotify) queryLocalInterface;
            }
            return new Proxy(iBinder);
        }

        public boolean onTransact(int i9, Parcel parcel, Parcel parcel2, int i10) throws RemoteException {
            VDEvent vDEvent;
            Log.e("onTransact", "onTransact");
            if (i9 == 1) {
                parcel.enforceInterface(DESCRIPTOR);
                if (parcel.readInt() != 0) {
                    vDEvent = VDEvent.CREATOR.createFromParcel(parcel);
                } else {
                    vDEvent = null;
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
