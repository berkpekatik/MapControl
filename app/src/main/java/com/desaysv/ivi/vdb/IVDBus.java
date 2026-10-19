package com.desaysv.ivi.vdb;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.desaysv.ivi.vdb.IVDBusCallback;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.event.VDEvent;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public interface IVDBus extends IInterface {
    VDEvent get(VDEvent vDEvent);

    void set(VDEvent vDEvent);

    void subscribe(int[] iArr, int i9, String str, IVDBusCallback iVDBusCallback);

    void subscribeCustomizedEvent(VDEvent vDEvent, IVDBusNotify iVDBusNotify);

    void unsubscribeCustomizedEvent(VDEvent vDEvent, IVDBusNotify iVDBusNotify);

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public static abstract class Stub extends Binder implements IVDBus {
        private static final String DESCRIPTOR = "com.desaysv.ivi.vdb.IVDBus";
        static final int TRANSACTION_get = 1;
        static final int TRANSACTION_set = 2;
        static final int TRANSACTION_subscribe = 3;
        static final int TRANSACTION_subscribeCustomizedEvent = 4;
        static final int TRANSACTION_unsubscribeCustomizedEvent = 5;

        /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
        private static class Proxy implements IVDBus {
            private IBinder mRemote;

            Proxy(IBinder iBinder) {
                this.mRemote = iBinder;
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public VDEvent get(VDEvent vDEvent) {
                VDEvent vDEvent2;
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (vDEvent != null) {
                        obtain.writeInt(1);
                        vDEvent.writeToParcel(obtain, 0);
                    } else {
                        obtain.writeInt(0);
                    }
                    this.mRemote.transact(1, obtain, obtain2, 0);
                    obtain2.readException();
                    if (obtain2.readInt() != 0) {
                        vDEvent2 = VDEvent.CREATOR.createFromParcel(obtain2);
                    } else {
                        vDEvent2 = null;
                    }
                    obtain2.recycle();
                    obtain.recycle();
                    return vDEvent2;
                } catch (RemoteException e10) {
                    throw new RuntimeException(e10);
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw new RuntimeException(th);
                }
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            public void set(VDEvent vDEvent) {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (vDEvent != null) {
                        obtain.writeInt(1);
                        vDEvent.writeToParcel(obtain, 0);
                    } else {
                        obtain.writeInt(0);
                    }
                    this.mRemote.transact(2, obtain, obtain2, 0);
                    obtain2.readException();
                    obtain2.recycle();
                    obtain.recycle();
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw new RuntimeException(th);
                }
            }

            public void subscribe(int[] iArr, int i9, String str, IVDBusCallback iVDBusCallback) {
                IBinder iBinder;
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeIntArray(iArr);
                    obtain.writeInt(i9);
                    obtain.writeString(str);
                    if (iVDBusCallback != null) {
                        iBinder = iVDBusCallback.asBinder();
                    } else {
                        iBinder = null;
                    }
                    obtain.writeStrongBinder(iBinder);
                    this.mRemote.transact(3, obtain, obtain2, 0);
                    obtain2.readException();
                    obtain2.recycle();
                    obtain.recycle();
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw new RuntimeException(th);
                }
            }

            public void subscribeCustomizedEvent(VDEvent vDEvent, IVDBusNotify iVDBusNotify) {
                IBinder iBinder;
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (vDEvent != null) {
                        obtain.writeInt(1);
                        vDEvent.writeToParcel(obtain, 0);
                    } else {
                        obtain.writeInt(0);
                    }
                    if (iVDBusNotify != null) {
                        iBinder = iVDBusNotify.asBinder();
                    } else {
                        iBinder = null;
                    }
                    obtain.writeStrongBinder(iBinder);
                    this.mRemote.transact(4, obtain, obtain2, 0);
                    obtain2.readException();
                    obtain2.recycle();
                    obtain.recycle();
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw new RuntimeException(th);
                }
            }

            public void unsubscribeCustomizedEvent(VDEvent vDEvent, IVDBusNotify iVDBusNotify) {
                IBinder iBinder;
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (vDEvent != null) {
                        obtain.writeInt(1);
                        vDEvent.writeToParcel(obtain, 0);
                    } else {
                        obtain.writeInt(0);
                    }
                    if (iVDBusNotify != null) {
                        iBinder = iVDBusNotify.asBinder();
                    } else {
                        iBinder = null;
                    }
                    obtain.writeStrongBinder(iBinder);
                    this.mRemote.transact(5, obtain, obtain2, 0);
                    obtain2.readException();
                } catch (RemoteException unused) {
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw new RuntimeException(th);
                }
                obtain2.recycle();
                obtain.recycle();
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IVDBus asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(DESCRIPTOR);
            if (queryLocalInterface instanceof IVDBus) {
                return (IVDBus) queryLocalInterface;
            }
            return new Proxy(iBinder);
        }

        public boolean onTransact(int i9, Parcel parcel, Parcel parcel2, int i10) throws RemoteException {
            VDEvent vDEvent = null;
            if (i9 == 1) {
                parcel.enforceInterface(DESCRIPTOR);
                if (parcel.readInt() != 0) {
                    vDEvent = VDEvent.CREATOR.createFromParcel(parcel);
                }
                VDEvent vDEvent2 = get(vDEvent);
                parcel2.writeNoException();
                if (vDEvent2 != null) {
                    parcel2.writeInt(1);
                    vDEvent2.writeToParcel(parcel2, 1);
                } else {
                    parcel2.writeInt(0);
                }
                return true;
            } else if (i9 == 2) {
                parcel.enforceInterface(DESCRIPTOR);
                if (parcel.readInt() != 0) {
                    vDEvent = VDEvent.CREATOR.createFromParcel(parcel);
                }
                set(vDEvent);
                parcel2.writeNoException();
                return true;
            } else if (i9 == 3) {
                parcel.enforceInterface(DESCRIPTOR);
                subscribe(parcel.createIntArray(), parcel.readInt(), parcel.readString(), IVDBusCallback.Stub.asInterface(parcel.readStrongBinder()));
                parcel2.writeNoException();
                return true;
            } else if (i9 == 4) {
                parcel.enforceInterface(DESCRIPTOR);
                if (parcel.readInt() != 0) {
                    vDEvent = VDEvent.CREATOR.createFromParcel(parcel);
                }
                subscribeCustomizedEvent(vDEvent, IVDBusNotify.Stub.asInterface(parcel.readStrongBinder()));
                parcel2.writeNoException();
                return true;
            } else if (i9 == 5) {
                parcel.enforceInterface(DESCRIPTOR);
                if (parcel.readInt() != 0) {
                    vDEvent = VDEvent.CREATOR.createFromParcel(parcel);
                }
                unsubscribeCustomizedEvent(vDEvent, IVDBusNotify.Stub.asInterface(parcel.readStrongBinder()));
                parcel2.writeNoException();
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
