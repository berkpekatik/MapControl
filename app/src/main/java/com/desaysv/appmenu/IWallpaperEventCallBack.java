package com.desaysv.appmenu;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import com.desaysv.launcher.svwallpaper.data.WallpaperInfo;

import java.util.List;

/**
 * Matches {@code com.desaysv.appmenu.IWallpaperEventCallBack} in SVLauncher.apk.
 */
public interface IWallpaperEventCallBack extends IInterface {

    abstract class Stub extends Binder implements IWallpaperEventCallBack {
        private static final String DESCRIPTOR = "com.desaysv.appmenu.IWallpaperEventCallBack";
        static final int TRANSACTION_onWallpaperSetResult = 1;
        static final int TRANSACTION_onWallpaperChanged = 2;
        static final int TRANSACTION_onWallpaperDeleteResult = 3;
        static final int TRANSACTION_onCustomWallpapersChange = 4;
        static final int TRANSACTION_onWallpaperWidthChange = 5;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IWallpaperEventCallBack asInterface(IBinder binder) {
            if (binder == null) {
                return null;
            }
            IInterface iin = binder.queryLocalInterface(DESCRIPTOR);
            if (iin instanceof IWallpaperEventCallBack) {
                return (IWallpaperEventCallBack) iin;
            }
            return new Proxy(binder);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DESCRIPTOR);
                return true;
            }
            switch (code) {
                case TRANSACTION_onWallpaperSetResult: {
                    data.enforceInterface(DESCRIPTOR);
                    boolean ok = data.readInt() != 0;
                    String id = data.readString();
                    WallpaperInfo info = data.readInt() != 0 ? WallpaperInfo.CREATOR.createFromParcel(data) : null;
                    onWallpaperSetResult(ok, id, info);
                    return true;
                }
                case TRANSACTION_onWallpaperChanged: {
                    data.enforceInterface(DESCRIPTOR);
                    WallpaperInfo info = data.readInt() != 0 ? WallpaperInfo.CREATOR.createFromParcel(data) : null;
                    onWallpaperChanged(info);
                    return true;
                }
                case TRANSACTION_onWallpaperDeleteResult: {
                    data.enforceInterface(DESCRIPTOR);
                    onWallpaperDeleteResult(data.readInt(), data.readString());
                    return true;
                }
                case TRANSACTION_onCustomWallpapersChange: {
                    data.enforceInterface(DESCRIPTOR);
                    onCustomWallpapersChange(data.createTypedArrayList(WallpaperInfo.CREATOR));
                    return true;
                }
                case TRANSACTION_onWallpaperWidthChange: {
                    data.enforceInterface(DESCRIPTOR);
                    WallpaperInfo a = data.readInt() != 0 ? WallpaperInfo.CREATOR.createFromParcel(data) : null;
                    WallpaperInfo b = data.readInt() != 0 ? WallpaperInfo.CREATOR.createFromParcel(data) : null;
                    onWallpaperWidthChange(a, b, data.readFloat());
                    return true;
                }
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static final class Proxy implements IWallpaperEventCallBack {
            private final IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return mRemote;
            }

            @Override
            public void onWallpaperSetResult(boolean ok, String id, WallpaperInfo info) throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(ok ? 1 : 0);
                    data.writeString(id);
                    if (info != null) {
                        data.writeInt(1);
                        info.writeToParcel(data, 0);
                    } else {
                        data.writeInt(0);
                    }
                    mRemote.transact(TRANSACTION_onWallpaperSetResult, data, null, FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }

            @Override
            public void onWallpaperChanged(WallpaperInfo info) throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (info != null) {
                        data.writeInt(1);
                        info.writeToParcel(data, 0);
                    } else {
                        data.writeInt(0);
                    }
                    mRemote.transact(TRANSACTION_onWallpaperChanged, data, null, FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }

            @Override
            public void onWallpaperDeleteResult(int code, String id) throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(code);
                    data.writeString(id);
                    mRemote.transact(TRANSACTION_onWallpaperDeleteResult, data, null, FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }

            @Override
            public void onCustomWallpapersChange(List<WallpaperInfo> list) throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeTypedList(list);
                    mRemote.transact(TRANSACTION_onCustomWallpapersChange, data, null, FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }

            @Override
            public void onWallpaperWidthChange(WallpaperInfo from, WallpaperInfo to, float width)
                    throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (from != null) {
                        data.writeInt(1);
                        from.writeToParcel(data, 0);
                    } else {
                        data.writeInt(0);
                    }
                    if (to != null) {
                        data.writeInt(1);
                        to.writeToParcel(data, 0);
                    } else {
                        data.writeInt(0);
                    }
                    data.writeFloat(width);
                    mRemote.transact(TRANSACTION_onWallpaperWidthChange, data, null, FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }
        }
    }

    void onCustomWallpapersChange(List<WallpaperInfo> list) throws RemoteException;

    void onWallpaperChanged(WallpaperInfo wallpaperInfo) throws RemoteException;

    void onWallpaperDeleteResult(int code, String id) throws RemoteException;

    void onWallpaperSetResult(boolean ok, String id, WallpaperInfo wallpaperInfo) throws RemoteException;

    void onWallpaperWidthChange(WallpaperInfo from, WallpaperInfo to, float width) throws RemoteException;
}
